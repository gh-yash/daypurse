package bizappz.daypurse.ui.history

import androidx.lifecycle.ViewModel
import bizappz.daypurse.data.model.Budget
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.budget.BudgetEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val total: Long, val items: List<ExpenseWithCategory>)

data class HistoryUiState(
    val month: YearMonth,
    val canGoNext: Boolean,
    val categoryFilter: Long?,
    val categories: List<Category> = emptyList(),
    val budgetFilter: Long? = null,
    val budgets: List<Budget> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val monthTotal: Long = 0,
    val loaded: Boolean = false,
)

/** One month of spends at a time keeps queries small however long the history gets. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val expenses: ExpenseRepository,
    categories: CategoryRepository,
    private val engine: BudgetEngine,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(todayProvider.today.value))
    private val filter = MutableStateFlow<Long?>(null)
    private val budgetFilter = MutableStateFlow<Long?>(null)

    private val groups = combine(month, filter, budgetFilter) { m, c, b -> Triple(m, c, b) }
        .flatMapLatest { (m, c, b) -> expenses.between(m.atDay(1), m.atEndOfMonth(), c, b) }
        .map { list ->
            list.groupBy { it.expense.date }
                .map { (date, items) -> DayGroup(date, items.sumOf { it.expense.amount }, items) }
        }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<HistoryUiState> =
        combine(
            combine(month, filter, budgetFilter, ::Triple),
            categories.visible,
            engine.budgets,
            groups,
            todayProvider.today,
        ) { (m, f, bf), cats, budgets, g, today ->
            HistoryUiState(
                month = m,
                canGoNext = m < YearMonth.from(today),
                categoryFilter = f,
                categories = cats,
                budgetFilter = bf,
                budgets = budgets,
                groups = g,
                monthTotal = g.sumOf { it.total },
                loaded = true,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HistoryUiState(month.value, canGoNext = false, categoryFilter = null),
        )

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun setFilter(categoryId: Long?) { filter.value = categoryId }
    fun setBudgetFilter(budgetId: Long?) { budgetFilter.value = budgetId }

    fun delete(expense: Expense) = viewModelScope.launch {
        expenses.delete(expense)
        refreshCarryOver(expense)
    }

    fun undoDelete(expense: Expense) = viewModelScope.launch {
        expenses.restore(expense)
        refreshCarryOver(expense)
    }

    private suspend fun refreshCarryOver(expense: Expense) {
        if (expense.date.isBefore(todayProvider.today.value)) engine.recalculateCarryOver(expense.budgetId)
    }

    private inline fun <T> MutableStateFlow<T>.update(f: (T) -> T) { value = f(value) }
}
