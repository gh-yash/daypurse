package bizappz.daypurse.domain.payments

import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.DetectedPayment
import bizappz.daypurse.data.model.InboxStatus
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Payments spotted in notifications, waiting for the user. Nothing becomes a spend until the
 * user taps Log; Ignore just drops it.
 */
class PaymentInbox(
    db: AppDatabase,
    private val expenses: ExpenseRepository,
    private val log: ActivityLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.paymentInboxDao()
    private val mutex = Mutex()

    val waiting: Flow<List<DetectedPayment>> = dao.observeNew()

    suspend fun get(id: Long) = dao.get(id)

    /**
     * Adds a detected payment unless it's a repeat: a UPI app and the bank often both announce
     * the same payment, so the same amount within [DUPLICATE_WINDOW_MS] counts once (keeping a
     * payee name if only the second one had it). Returns the new entry, or null for a repeat.
     */
    suspend fun add(amount: Long, payee: String?, source: PaymentSource, at: Long = clock()): DetectedPayment? = mutex.withLock {
        val existing = dao.recentWithAmount(amount, at - DUPLICATE_WINDOW_MS)
        if (existing != null) {
            if (existing.payee == null && payee != null && existing.status == InboxStatus.NEW) dao.update(existing.copy(payee = payee))
            return@withLock null
        }
        val item = DetectedPayment(amount = amount, payee = payee, sourcePackage = source.packageName,
            sourceLabel = source.label, detectedAt = at)
        val id = dao.insert(item)
        log.record(ActivityKind.PAYMENT, ActivityAction.DETECTED, subject = payee, amount = amount, refId = id, detail = source.label)
        item.copy(id = id)
    }

    /** Category the user picked last time for this payee. */
    suspend fun suggestedCategory(item: DetectedPayment): Long? = expenses.categoryForPayee(item.payee)

    /**
     * Logs [item] as a spend on the day it was paid. Returns the spend id, or null if it was
     * already handled (e.g. from the notification and the app at once).
     */
    suspend fun logIt(item: DetectedPayment, amount: Long, categoryId: Long?, budgetId: Long, note: String?): Long? = mutex.withLock {
        val current = dao.get(item.id)?.takeIf { it.status == InboxStatus.NEW } ?: return@withLock null
        val date = Instant.ofEpochMilli(current.detectedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            .let { if (it.isAfter(LocalDate.now())) LocalDate.now() else it }
        val id = expenses.add(amount, categoryId, date, note, budgetId, payee = current.payee)
        dao.update(current.copy(status = InboxStatus.LOGGED, expenseId = id))
        log.record(ActivityKind.PAYMENT, ActivityAction.LOGGED, subject = current.payee, amount = amount, refId = current.id,
            detail = current.sourceLabel)
        id
    }

    suspend fun ignore(item: DetectedPayment) = mutex.withLock {
        val current = dao.get(item.id)?.takeIf { it.status == InboxStatus.NEW } ?: return@withLock
        dao.update(current.copy(status = InboxStatus.IGNORED))
        log.record(ActivityKind.PAYMENT, ActivityAction.IGNORED, subject = current.payee, amount = current.amount,
            refId = current.id, detail = current.sourceLabel)
    }

    /** Puts an ignored or logged payment back in the inbox (Undo). */
    suspend fun reopen(item: DetectedPayment) = mutex.withLock {
        dao.get(item.id)?.let { dao.update(it.copy(status = InboxStatus.NEW, expenseId = null)) }
        log.notifyChanged()
    }

    /** Handled entries are kept a week (to recognise repeats), then removed. */
    suspend fun prune() = dao.pruneHandled(clock() - 7 * ActivityLog.DAY_MS)

    suspend fun clear() = dao.clear()

    companion object {
        const val DUPLICATE_WINDOW_MS = 10 * 60_000L
    }
}
