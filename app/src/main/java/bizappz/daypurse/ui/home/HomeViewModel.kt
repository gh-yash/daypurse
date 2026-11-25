package bizappz.daypurse.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.DetectedPayment
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.data.model.QuickSpend
import bizappz.daypurse.data.model.quickSpendKey
import bizappz.daypurse.di.AppContainer
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.domain.budget.budgetOrNull
import bizappz.daypurse.ui.spend.SpendPrefill
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val today: LocalDate,
        /** The primary ("Daily") budget. */
        val primary: BudgetState,
        /** Every other budget, in the user's order. */
        val others: List<BudgetState>,
        /** Today's spends in the primary budget. */
        val todaySpends: List<ExpenseWithCategory>,
    ) : HomeUiState
}

/** A one-tap chip: a spend the user repeats, with its category for the icon and label. */
data class QuickChip(val spend: QuickSpend, val category: Category?) {
    val key: String get() = quickSpendKey(spend.amount, spend.categoryId)
    /** "Chai" when the user usually adds a note, else the category name. */
    val label: String? get() = spend.note ?: category?.name
}

/** A detected payment, with the category last used for that payee (if any). */
data class InboxRow(val payment: DetectedPayment, val suggested: Category?)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val engine = c.budgetEngine
    private val expenses = c.expenseRepository

    val state: StateFlow<HomeUiState> = c.todayProvider.today
        .flatMapLatest { today ->
            engine.observeAllStates(today).flatMapLatest { states ->
                val primary = states.firstOrNull { it.budgetOrNull?.isPrimary == true } ?: BudgetState.NoBudget
                val others = states.filter { it !== primary }
                val primaryId = primary.budgetOrNull?.id
                val spends = if (primaryId == null) flowOf(emptyList()) else expenses.between(today, today, budgetId = primaryId)
                spends.map { HomeUiState.Ready(today, primary, others, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    val quickChips: StateFlow<List<QuickChip>> = c.todayProvider.today
        .flatMapLatest { today ->
            combine(expenses.quickSpends(today), c.categoryRepository.all, c.settingsRepository.settings) { spends, cats, s ->
                val byId = cats.associateBy { it.id }
                spends.map { QuickChip(it, it.categoryId?.let(byId::get)) }
                    .filter { it.key !in s.hiddenQuickSpends && it.category?.isHidden != true }
                    .take(MAX_CHIPS)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inbox: StateFlow<List<InboxRow>> =
        combine(c.paymentInbox.waiting, c.categoryRepository.all) { items, cats -> items to cats.associateBy { it.id } }
            .mapLatest { (items, cats) ->
                items.map { InboxRow(it, c.paymentInbox.suggestedCategory(it)?.let(cats::get)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Values for the add-spend sheet when it opens from a chip, a payment or a shortcut. */
    var prefill by mutableStateOf<SpendPrefill?>(null)

    fun resume(budgetId: Long) = viewModelScope.launch { engine.resume(budgetId, c.todayProvider.today.value) }

    /** Saves a quick spend now; returns it for Undo, or null if no budget can take it. */
    suspend fun quickLog(amount: Long, categoryId: Long?, note: String?): Expense? =
        c.quickLogger.log(amount, categoryId, note, c.todayProvider.today.value)

    fun undoAdd(expense: Expense) = viewModelScope.launch { expenses.delete(expense) }

    fun hideChip(chip: QuickChip) = viewModelScope.launch { c.settingsRepository.setQuickSpendHidden(chip.key, true) }
    fun unhideChip(chip: QuickChip) = viewModelScope.launch { c.settingsRepository.setQuickSpendHidden(chip.key, false) }

    /**
     * Logs a detected payment with its suggested category, into the default budget. Returns the
     * spend for Undo, or null when it needs the sheet (no category known yet, or no budget).
     */
    suspend fun logPayment(row: InboxRow): Expense? {
        val category = row.suggested ?: return null
        val budgetId = engine.defaultBudgetId(c.todayProvider.today.value) ?: return null
        val id = c.paymentInbox.logIt(row.payment, row.payment.amount, category.id, budgetId, null) ?: return null
        return expenses.get(id)
    }

    fun undoPaymentLog(row: InboxRow, expense: Expense) = viewModelScope.launch {
        expenses.delete(expense)
        c.paymentInbox.reopen(row.payment)
    }

    fun ignore(row: InboxRow) = viewModelScope.launch { c.paymentInbox.ignore(row.payment) }
    fun reopen(row: InboxRow) = viewModelScope.launch { c.paymentInbox.reopen(row.payment) }

    fun prefillFor(row: InboxRow) = SpendPrefill(
        amount = row.payment.amount,
        categoryId = row.suggested?.id,
        note = null,
        payee = row.payment.payee,
        inboxId = row.payment.id,
    )

    suspend fun inboxRow(id: Long): InboxRow? = c.paymentInbox.get(id)?.let { p ->
        InboxRow(p, c.paymentInbox.suggestedCategory(p)?.let { c.categoryRepository.get(it) })
    }

    private companion object {
        const val MAX_CHIPS = 8
    }
}
