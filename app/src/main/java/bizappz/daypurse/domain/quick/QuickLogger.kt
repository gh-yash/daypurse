package bizappz.daypurse.domain.quick

import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.budget.BudgetEngine
import java.time.LocalDate

/**
 * One-tap logging used by Home chips, launcher shortcuts, the widget and the reminder reply:
 * today, into the budget a new spend would go to by default.
 */
class QuickLogger(private val expenses: ExpenseRepository, private val engine: BudgetEngine) {
    /** Returns the saved spend, or null if no budget can take it today. */
    suspend fun log(amount: Long, categoryId: Long?, note: String?, today: LocalDate): Expense? {
        val budgetId = engine.defaultBudgetId(today) ?: return null
        val id = expenses.add(amount, categoryId, today, note, budgetId, action = ActivityAction.QUICK_ADDED)
        return expenses.get(id)
    }
}
