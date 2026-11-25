package bizappz.daypurse.data.repository

import androidx.room.withTransaction
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.QuickSpend
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ExpenseRepository(private val db: AppDatabase, private val log: ActivityLog) {
    private val dao = db.expenseDao()
    private val categories = db.categoryDao()

    fun between(start: LocalDate, end: LocalDate, categoryId: Long? = null, budgetId: Long? = null) =
        dao.observeBetween(start, end, categoryId, budgetId)
    fun totalBetween(budgetId: Long, start: LocalDate, end: LocalDate) = dao.observeTotalBetween(budgetId, start, end)
    fun categoryTotals(start: LocalDate, end: LocalDate, budgetId: Long? = null) = dao.observeCategoryTotals(start, end, budgetId)
    fun dailyTotals(start: LocalDate, end: LocalDate, budgetId: Long? = null) = dao.observeDailyTotals(start, end, budgetId)

    suspend fun get(id: Long) = dao.get(id)

    /** Top amounts the user usually enters (optionally for one category), in minor units. */
    fun frequentAmounts(
        today: LocalDate,
        categoryId: Long? = null,
        limit: Int = 5,
        lookbackDays: Long = 60,
    ): Flow<List<Long>> =
        dao.observeFrequentAmounts(today.minusDays(lookbackDays), categoryId, limit)
            // Only suggest amounts used more than once; a one-off isn't a habit.
            .map { rows -> rows.filter { it.uses > 1 }.map { it.amount } }

    /** Amount + category pairs used at least twice lately, most used first (one-tap chips). */
    fun quickSpends(today: LocalDate, limit: Int = 8, lookbackDays: Long = 60): Flow<List<QuickSpend>> =
        dao.observeQuickSpends(today.minusDays(lookbackDays), minUses = 2, limit = limit)

    /** The category last used for [payee], if any. */
    suspend fun categoryForPayee(payee: String?): Long? =
        payee?.takeIf { it.isNotBlank() }?.let { dao.lastCategoryForPayee(it.trim()) }

    /** Whether anything was logged on [date] (any budget). */
    suspend fun anyOn(date: LocalDate): Boolean = dao.countOn(date) > 0

    suspend fun add(
        amount: Long,
        categoryId: Long?,
        date: LocalDate,
        note: String?,
        budgetId: Long,
        payee: String? = null,
        recurringId: Long? = null,
        action: ActivityAction = ActivityAction.ADDED,
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        val expense = Expense(
            amount = amount, categoryId = categoryId, date = date, note = note?.trim()?.ifEmpty { null },
            budgetId = budgetId, payee = payee?.trim()?.ifEmpty { null }, recurringId = recurringId,
        )
        val id = dao.insert(expense)
        record(action, expense.copy(id = id))
        return id
    }

    suspend fun update(expense: Expense) {
        require(expense.amount > 0) { "Amount must be positive" }
        val before = dao.get(expense.id)
        dao.update(expense)
        if (before != null && before != expense) record(ActivityAction.EDITED, expense, snapshot = ActivityLog.snapshotOf(before))
    }

    suspend fun delete(expense: Expense) {
        dao.delete(expense)
        record(ActivityAction.DELETED, expense, snapshot = ActivityLog.snapshotOf(expense))
    }

    /** Re-inserts a just-deleted expense with its original id (for "Undo"). */
    suspend fun restore(expense: Expense) {
        dao.insert(expense)
        record(ActivityAction.RESTORED, expense)
    }

    /**
     * Brings back a spend deleted earlier (from the activity history). Its budget or category
     * may be gone by now: it then goes to [fallbackBudgetId] / no category. Returns the spend,
     * or null if there's no budget to put it in.
     */
    suspend fun restoreDeleted(snapshot: Expense, fallbackBudgetId: Long?): Expense? = db.withTransaction {
        val budgetId = snapshot.budgetId.takeIf { db.budgetDao().get(it) != null } ?: fallbackBudgetId
            ?: return@withTransaction null
        val categoryId = snapshot.categoryId?.takeIf { categories.get(it) != null }
        val keepId = dao.get(snapshot.id) == null
        val e = snapshot.copy(id = if (keepId) snapshot.id else 0, budgetId = budgetId, categoryId = categoryId, recurringId = null)
        val id = dao.insert(e)
        e.copy(id = id).also { record(ActivityAction.RESTORED, it) }
    }

    private suspend fun record(action: ActivityAction, e: Expense, snapshot: String? = null) {
        val category = e.categoryId?.let { categories.get(it)?.name }
        log.record(ActivityKind.SPEND, action, subject = category, amount = e.amount,
            detail = e.note ?: e.payee, refId = e.id, snapshot = snapshot)
    }
}
