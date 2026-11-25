package bizappz.daypurse.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.RecurringSpend
import bizappz.daypurse.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringRow(val item: RecurringSpend, val category: Category?, val budget: Budget?)

data class RecurringUiState(
    val rows: List<RecurringRow>,
    val categories: List<Category>,
    val budgets: List<Budget>,
)

class RecurringViewModel(private val c: AppContainer) : ViewModel() {
    private val recurring = c.recurringSpends

    val state: StateFlow<RecurringUiState?> =
        combine(recurring.all, c.categoryRepository.all, c.budgetEngine.budgets) { items, cats, budgets ->
            val catById = cats.associateBy { it.id }
            val budgetById = budgets.associateBy { it.id }
            RecurringUiState(
                rows = items.map { RecurringRow(it, it.categoryId?.let(catById::get), it.budgetId?.let(budgetById::get)) },
                categories = cats.filter { !it.isHidden },
                budgets = budgets,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(item: RecurringSpend) = viewModelScope.launch {
        recurring.update(item)
        // Moving the next date into the past adds what's due right away.
        recurring.catchUp(c.todayProvider.today.value)
    }

    fun setPaused(item: RecurringSpend, paused: Boolean) = viewModelScope.launch { recurring.setPaused(item, paused) }
    fun skipNext(item: RecurringSpend) = viewModelScope.launch { recurring.skipNext(item) }
    fun delete(item: RecurringSpend) = viewModelScope.launch { recurring.delete(item) }
    fun restore(item: RecurringSpend) = viewModelScope.launch { recurring.restore(item) }
}
