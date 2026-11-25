package bizappz.daypurse.domain.recurring

import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.RecurringSpend
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.budget.BudgetEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Rent, subscriptions and other repeating spends. Due ones are added by [catchUp], which the
 * app calls on start, when the day changes, and from the widget and reminder, so nothing has
 * to run in the background.
 */
class RecurringSpends(
    db: AppDatabase,
    private val expenses: ExpenseRepository,
    private val engine: BudgetEngine,
    private val log: ActivityLog,
) {
    private val dao = db.recurringDao()
    private val categories = db.categoryDao()
    private val mutex = Mutex()

    val all: Flow<List<RecurringSpend>> = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    /**
     * Starts repeating a spend first made on [firstDate] (that one is added by the caller):
     * the next one is due a period later. Returns the new id.
     */
    suspend fun create(amount: Long, categoryId: Long?, note: String?, budgetId: Long?, repeat: Repeat, firstDate: LocalDate): Long {
        require(amount > 0) { "Amount must be positive" }
        val item = RecurringSpend(
            amount = amount, categoryId = categoryId, note = note?.trim()?.ifEmpty { null }, budgetId = budgetId,
            repeat = repeat, anchorDay = firstDate.dayOfMonth, nextDate = firstDate,
        )
        val id = dao.insert(item.copy(nextDate = item.after(firstDate)))
        record(ActivityAction.ADDED, item.copy(id = id))
        return id
    }

    suspend fun update(item: RecurringSpend) {
        require(item.amount > 0) { "Amount must be positive" }
        dao.update(item.copy(note = item.note?.trim()?.ifEmpty { null }))
        record(ActivityAction.EDITED, item)
    }

    suspend fun setPaused(item: RecurringSpend, paused: Boolean) {
        dao.update(item.copy(paused = paused))
        record(if (paused) ActivityAction.PAUSED else ActivityAction.RESUMED, item)
    }

    /** Skips the next occurrence (e.g. a month the rent is paid differently). */
    suspend fun skipNext(item: RecurringSpend) {
        dao.update(item.copy(nextDate = item.after(item.nextDate)))
        record(ActivityAction.SKIPPED, item, detail = item.nextDate.toString())
    }

    suspend fun delete(item: RecurringSpend) {
        dao.delete(item.id)
        record(ActivityAction.DELETED, item)
    }

    /** Undo for [delete] (spends it added earlier stay unlinked). */
    suspend fun restore(item: RecurringSpend) {
        dao.insert(item)
        record(ActivityAction.RESTORED, item)
    }

    /**
     * Adds every occurrence due up to [today] as a normal spend, in the item's budget (or the
     * main one). A phone that hasn't opened the app for a while catches up in one go, up to
     * [MAX_PER_RUN] occurrences per item. Returns how many spends were added.
     */
    suspend fun catchUp(today: LocalDate): Int = mutex.withLock {
        var added = 0
        val touched = mutableSetOf<Long>()
        for (item in dao.due(today)) {
            val budgetId = item.budgetId?.takeIf { engine.get(it) != null } ?: engine.primary()?.id ?: continue
            var next = item.nextDate
            var n = 0
            while (!next.isAfter(today) && n++ < MAX_PER_RUN) {
                expenses.add(item.amount, item.categoryId, next, item.note, budgetId, recurringId = item.id, action = ActivityAction.AUTO_ADDED)
                if (next.isBefore(today)) touched += budgetId
                next = item.after(next)
                added++
            }
            // Past the cap, jump ahead rather than flooding the history.
            while (!next.isAfter(today)) next = item.after(next)
            dao.update(item.copy(nextDate = next))
        }
        touched.forEach { engine.recalculateCarryOver(it) }
        added
    }

    private suspend fun record(action: ActivityAction, item: RecurringSpend, detail: String? = null) {
        val name = item.note ?: item.categoryId?.let { categories.get(it)?.name }
        log.record(ActivityKind.RECURRING, action, subject = name, amount = item.amount, refId = item.id,
            detail = detail ?: item.repeat.name)
    }

    companion object {
        const val MAX_PER_RUN = 12
    }
}
