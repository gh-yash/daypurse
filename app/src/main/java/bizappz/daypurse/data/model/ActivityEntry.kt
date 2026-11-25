package bizappz.daypurse.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What an activity entry is about (used for filtering). */
enum class ActivityKind { SPEND, BUDGET, CATEGORY, RECURRING, PAYMENT, SETTINGS, DATA }

enum class ActivityAction {
    ADDED, QUICK_ADDED, AUTO_ADDED, EDITED, DELETED, RESTORED,
    PAUSED, RESUMED, HIDDEN, SHOWN, REORDERED, SKIPPED,
    DETECTED, LOGGED, IGNORED,
    CHANGED, EXPORTED, IMPORTED, RESET,
}

/**
 * One line of the activity history. Stored as structured fields (not finished sentences) so
 * the screen can word and format it, e.g. amounts in the current currency.
 *
 * - [subject]: what it's about: a category, budget or payee name, or a setting.
 * - [detail]: extra context: a note, a new setting value, a source app.
 * - [snapshot]: JSON of the record before the change (edits, deletes), for "Restore" and diffs.
 */
@Entity(tableName = "activity_log", indices = [Index("at"), Index("kind")])
data class ActivityEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: ActivityKind,
    val action: ActivityAction,
    val subject: String? = null,
    val amount: Long? = null,
    val detail: String? = null,
    val refId: Long? = null,
    val snapshot: String? = null,
)
