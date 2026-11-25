package bizappz.daypurse.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.budget.BudgetState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BudgetDetailUi(
    val today: LocalDate,
    val state: BudgetState,
    /** Spends of the running (or last) period, newest first. */
    val spends: List<ExpenseWithCategory>,
    val primaryName: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetDetailViewModel(
    private val engine: BudgetEngine,
    expenses: ExpenseRepository,
    private val todayProvider: TodayProvider,
    private val budgetId: Long,
) : ViewModel() {
    val ui: StateFlow<BudgetDetailUi?> = todayProvider.today.flatMapLatest { today ->
        engine.observeState(budgetId, today).flatMapLatest { state ->
            val range = when (state) {
                is BudgetState.Active -> state.day.period.startDate to state.day.period.endDate
                is BudgetState.Ended -> state.lastPeriod?.let { it.startDate to it.endDate }
                else -> null
            }
            val spends = range?.let { (s, e) -> expenses.between(s, e, budgetId = budgetId) } ?: flowOf(emptyList())
            spends.map { BudgetDetailUi(today, state, it, engine.primary()?.name ?: "") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val today: LocalDate get() = todayProvider.today.value

    fun pause(until: LocalDate?) = viewModelScope.launch { engine.pause(budgetId, today, until) }
    fun resume() = viewModelScope.launch { engine.resume(budgetId, today) }
    suspend fun spendCount(): Int = engine.spendCount(budgetId)
    fun delete(moveSpends: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        engine.deleteBudget(budgetId, moveSpends)
        onDone()
    }
}

class BudgetsViewModel(engine: BudgetEngine, todayProvider: TodayProvider) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val states: StateFlow<List<BudgetState>?> = todayProvider.today
        .flatMapLatest { engine.observeAllStates(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

internal val BudgetState.budget: Budget?
    get() = when (this) {
        is BudgetState.Active -> budget
        is BudgetState.NotStarted -> budget
        is BudgetState.Ended -> budget
        BudgetState.NoBudget -> null
    }
