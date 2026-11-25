package bizappz.daypurse.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class InboxStatus { NEW, LOGGED, IGNORED }

/**
 * A payment spotted in another app's notification, waiting for the user to log or ignore it.
 * Only these parsed fields are kept, never the notification text.
 */
@Entity(tableName = "payment_inbox", indices = [Index("status"), Index("detectedAt")])
data class DetectedPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val payee: String?,
    val sourcePackage: String,
    val sourceLabel: String,
    val detectedAt: Long,
    val status: InboxStatus = InboxStatus.NEW,
    /** The spend created when it was logged. */
    val expenseId: Long? = null,
)
