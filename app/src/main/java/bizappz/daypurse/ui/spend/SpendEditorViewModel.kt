package bizappz.daypurse.ui.spend

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.map
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.data.model.Budget
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.domain.payments.PaymentInbox
import bizappz.daypurse.domain.recurring.RecurringSpends
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Backs the add/edit spend sheet: categories, smart suggestions, and saving. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpendEditorViewModel(
    private val categories: CategoryRepository,
    private val expenses: ExpenseRepository,
    private val engine: BudgetEngine,
    private val todayProvider: TodayProvider,
    private val recurring: RecurringSpends,
    private val inbox: PaymentInbox,
) : ViewModel() {
    val today: StateFlow<LocalDate> = todayProvider.today

    /**
     * Budgets a spend can go to today: running and not paused. A paused budget only shows up
     * when nothing else can take the spend.
     */
    val budgetChoices: StateFlow<List<Budget>?> = today.flatMapLatest { engine.observeAllStates(it) }
        .map { states ->
            val running = states.filterIsInstance<BudgetState.Active>()
            running.filter { !it.paused }.map { it.budget }.ifEmpty { running.map { it.budget } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Default budget: an event budget running today (one-off), otherwise the primary one. */
    fun defaultBudget(choices: List<Budget>): Budget? =
        choices.firstOrNull { !it.isPrimary && !it.recurring } ?: choices.firstOrNull { it.isPrimary } ?: choices.firstOrNull()

    val allCategories: StateFlow<List<Category>> =
        categories.visible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickPicks: StateFlow<List<Category>> =
        today.flatMapLatest { categories.quickPicks(it, limit = QUICK_PICKS) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Usual amounts. With a category selected, that category's habits come first, topped up
     * with overall favourites.
     */
    fun amountSuggestions(categoryId: Long?): Flow<List<Long>> = today.flatMapLatest { t ->
        if (categoryId == null) {
            expenses.frequentAmounts(t, limit = AMOUNT_CHIPS)
        } else {
            combine(
                expenses.frequentAmounts(t, categoryId, AMOUNT_CHIPS),
                expenses.frequentAmounts(t, limit = AMOUNT_CHIPS),
            ) { inCategory, overall -> (inCategory + overall).distinct().take(AMOUNT_CHIPS) }
        }
    }

    /**
     * Saves the sheet. A new spend can start repeating ([repeat]); one from a detected payment
     * ([prefill] with an inbox id) is logged through the inbox so it leaves the "To review" list.
     */
    fun save(
        editing: Expense?,
        amount: Long,
        categoryId: Long?,
        date: LocalDate,
        note: String,
        budgetId: Long,
        repeat: Repeat? = null,
        prefill: SpendPrefill? = null,
    ) {
        viewModelScope.launch {
            val fromInbox = prefill?.inboxId?.let { inbox.get(it) }
            if (editing == null && fromInbox != null) {
                inbox.logIt(fromInbox, amount, categoryId, budgetId, note)
            } else if (editing == null) {
                val recurringId = repeat?.let {
                    val primary = engine.primary()?.id
                    recurring.create(amount, categoryId, note, budgetId.takeIf { b -> b != primary }, it, date)
                }
                expenses.add(amount, categoryId, date, note, budgetId, payee = prefill?.payee, recurringId = recurringId)
                // A repeat started in the past may already have later occurrences due.
                if (recurringId != null) recurring.catchUp(today.value)
            } else {
                expenses.update(
                    editing.copy(
                        amount = amount, categoryId = categoryId, date = date,
                        note = note.trim().ifEmpty { null }, budgetId = budgetId,
                    ),
                )
            }
            afterChange(date, budgetId)
            if (editing != null && (editing.budgetId != budgetId || editing.date != date)) afterChange(editing.date, editing.budgetId)
        }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch {
            expenses.delete(expense)
            afterChange(expense.date, expense.budgetId)
        }
    }

    /** Spends in past days can change an earlier period's leftover, so refresh carry-over. */
    private suspend fun afterChange(date: LocalDate, budgetId: Long) {
        if (date.isBefore(today.value)) engine.recalculateCarryOver(budgetId)
    }

    private companion object {
        const val QUICK_PICKS = 4
        const val AMOUNT_CHIPS = 5
    }
}

/** Values to start a new spend with (from a quick spend, a detected payment or a shortcut). */
data class SpendPrefill(
    val amount: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val payee: String? = null,
    /** A detected payment being logged. */
    val inboxId: Long? = null,
    val date: LocalDate? = null,
)
