package bizappz.daypurse.domain.activity

import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityEntry
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Expense
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.time.LocalDate

/**
 * The user's activity history, and the app's single "data changed" signal.
 *
 * Every change to spends, budgets, categories, recurring spends, detected payments and settings
 * is recorded here (by the repositories, not the screens, so nothing is missed), and each record
 * pings [changes] so widgets, shortcuts and the Quick Settings tile can refresh.
 *
 * Kept on the device only, and pruned to the retention period the user picks.
 */
class ActivityLog(db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.activityDao()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits after any recorded change. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun record(
        kind: ActivityKind,
        action: ActivityAction,
        subject: String? = null,
        amount: Long? = null,
        detail: String? = null,
        refId: Long? = null,
        snapshot: String? = null,
    ) {
        dao.insert(ActivityEntry(0, clock(), kind, action, subject?.take(80), amount, detail?.take(120), refId, snapshot))
        _changes.tryEmit(Unit)
    }

    /** Signals a data change that isn't worth a history line (e.g. the day rolled over). */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    fun observe(since: Long, kinds: Set<ActivityKind>, query: String, limit: Int = 500): Flow<List<ActivityEntry>> =
        dao.observe(since, kinds.map { it.name }.ifEmpty { listOf("") }, if (kinds.isEmpty()) 1 else 0, query.trim(), limit)

    val count: Flow<Int> = dao.observeCount()

    suspend fun get(id: Long) = dao.get(id)

    /** Drops entries older than [days] (0 = keep forever). */
    suspend fun prune(days: Int) {
        if (days > 0) dao.deleteBefore(clock() - days * DAY_MS)
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val DAY_MS = 86_400_000L
        /** Choices offered for how long to keep history (days; 0 = forever). */
        val RETENTION_CHOICES = listOf(7, 30, 90, 365, 0)
        const val DEFAULT_RETENTION_DAYS = 90

        /** A spend as JSON, so a deleted one can be restored and an edit can show what changed. */
        fun snapshotOf(e: Expense): String = JSONObject()
            .put("id", e.id).put("amount", e.amount).put("categoryId", e.categoryId ?: JSONObject.NULL)
            .put("note", e.note ?: JSONObject.NULL).put("date", e.date.toString()).put("createdAt", e.createdAt)
            .put("budgetId", e.budgetId).put("payee", e.payee ?: JSONObject.NULL)
            .toString()

        fun expenseFrom(snapshot: String): Expense? = runCatching {
            val o = JSONObject(snapshot)
            Expense(
                id = o.getLong("id"),
                amount = o.getLong("amount"),
                categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId"),
                note = if (o.isNull("note")) null else o.getString("note"),
                date = LocalDate.parse(o.getString("date")),
                createdAt = o.getLong("createdAt"),
                budgetId = o.getLong("budgetId"),
                payee = if (o.isNull("payee")) null else o.optString("payee"),
            )
        }.getOrNull()
    }
}
