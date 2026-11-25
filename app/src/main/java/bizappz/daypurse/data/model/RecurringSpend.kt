package bizappz.daypurse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class Repeat { WEEKLY, MONTHLY, YEARLY }

/**
 * A spend that comes back on a schedule (rent, a subscription). On or after [nextDate] the app
 * adds it as a normal spend and moves [nextDate] on; nothing runs in the background for it.
 *
 * Monthly and yearly repeats keep [anchorDay] (day of month), so the 31st falls on the last day
 * of shorter months and returns to the 31st afterwards.
 */
@Entity(
    tableName = "recurring_spends",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Budget::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("budgetId"), Index("nextDate")],
)
data class RecurringSpend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val categoryId: Long?,
    val note: String? = null,
    /** Budget to add it to; null means the main budget. */
    val budgetId: Long? = null,
    val repeat: Repeat,
    val anchorDay: Int,
    val nextDate: LocalDate,
    val paused: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** The occurrence after [date]. */
    fun after(date: LocalDate): LocalDate = when (repeat) {
        Repeat.WEEKLY -> date.plusWeeks(1)
        Repeat.MONTHLY -> date.plusMonths(1).let { it.withDayOfMonth(minOf(anchorDay, it.lengthOfMonth())) }
        Repeat.YEARLY -> date.plusYears(1).let { it.withDayOfMonth(minOf(anchorDay, it.lengthOfMonth())) }
    }
}
