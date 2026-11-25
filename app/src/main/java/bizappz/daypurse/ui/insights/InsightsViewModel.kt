package bizappz.daypurse.ui.insights

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import bizappz.daypurse.data.model.Budget
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.budget.DailyBudgetCalculator
import bizappz.daypurse.domain.budget.DateWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CategorySlice(val category: Category?, val total: Long, val fraction: Float)

data class InsightsUiState(
    val period: BudgetPeriod,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val spent: Long,
    val budget: Long,
    val daysElapsed: Int,
    val avgPerDay: Long,
    val baselinePerDay: Long,
    val slices: List<CategorySlice>,
    /** One entry per day of the period (0 for future days). */
    val daily: List<Long>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    engine: BudgetEngine,
    expenses: ExpenseRepository,
    categories: CategoryRepository,
    todayProvider: TodayProvider,
) : ViewModel() {
    /** 0 = newest period that has started. */
    private val index = MutableStateFlow(0)

    init {
        viewModelScope.launch { engine.syncPeriods(todayProvider.today.value) }
    }

    private val selected = MutableStateFlow<Long?>(null)

    val budgets: StateFlow<List<Budget>> =
        engine.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Budget being viewed: the user's choice, else the primary budget. */
    val selectedBudgetId: StateFlow<Long?> = combine(selected, engine.budgets) { sel, list ->
        sel?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull { it.isPrimary }?.id ?: list.firstOrNull()?.id
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val startedPeriods = combine(
        selectedBudgetId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else engine.periodsOf(id) },
        todayProvider.today,
    ) { list, today -> list.filter { !it.startDate.isAfter(today) } }

    /** null while loading; Empty when there are no periods yet. */
    val state: StateFlow<InsightsUiState?> = combine(startedPeriods, index, todayProvider.today) { p, i, t -> Triple(p, i, t) }
        .flatMapLatest { (periods, i, today) ->
            val period = periods.getOrNull(i.coerceIn(0, (periods.size - 1).coerceAtLeast(0)))
                ?: return@flatMapLatest flowOf(null)
            combine(
                expenses.categoryTotals(period.startDate, period.endDate, period.budgetId),
                expenses.dailyTotals(period.startDate, period.endDate, period.budgetId),
                categories.all,
            ) { totals, daily, cats ->
                build(period, i > 0, i < periods.size - 1, totals.map { it.categoryId to it.total }, daily.associate { it.date to it.total }, cats, today)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectBudget(id: Long) {
        selected.value = id
        index.value = 0
    }

    fun newer() { if (index.value > 0) index.value -= 1 }
    fun older() { index.value += 1 }

    private fun build(
        period: BudgetPeriod,
        hasNext: Boolean,
        hasPrevious: Boolean,
        totals: List<Pair<Long?, Long>>,
        byDay: Map<LocalDate, Long>,
        cats: List<Category>,
        today: LocalDate,
    ): InsightsUiState {
        val window = DateWindow(period.startDate, period.endDate)
        val spent = totals.sumOf { it.second }
        val lastCounted = minOf(today, period.endDate)
        val elapsed = (ChronoUnit.DAYS.between(period.startDate, lastCounted) + 1).toInt().coerceIn(1, window.days)
        val catById = cats.associateBy { it.id }
        return InsightsUiState(
            period = period,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            spent = spent,
            budget = period.totalAmount,
            daysElapsed = elapsed,
            // Informational, so nearest whole unit rather than rounded down.
            avgPerDay = (spent / elapsed + 50) / 100 * 100,
            baselinePerDay = DailyBudgetCalculator.perDay(period.totalAmount, window.days),
            slices = totals.map { (id, total) ->
                CategorySlice(id?.let(catById::get), total, if (spent > 0) total.toFloat() / spent else 0f)
            },
            daily = (0 until window.days).map { byDay[period.startDate.plusDays(it.toLong())] ?: 0L },
        )
    }
}
