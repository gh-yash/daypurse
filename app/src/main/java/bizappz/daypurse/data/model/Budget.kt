package bizappz.daypurse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class BudgetType { WEEKLY, MONTHLY, CUSTOM }

/**
 * A budget: the always-present primary one ("Daily") plus any number of extra ones (a festival,
 * a trip). The row is the budget's identity: spends point at it, and plan changes update it in
 * place while past periods keep their own stored amounts.
 *
 * - WEEKLY: periods start on weekday [anchorDay] (1 = Monday … 7 = Sunday).
 * - MONTHLY: periods start on day-of-month [anchorDay] (clamped for short months, e.g. 31 → Feb 28).
 * - CUSTOM: periods are [customLengthDays] long, the first one starting at [startDate].
 *
 * [startDate] is when tracking begins. For weekly/monthly plans it may fall mid-period; that
 * first partial period gets a prorated amount.
 */
@Entity(tableName = "budgets", indices = [Index("active")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BudgetType,
    /** Amount per period, in minor units. */
    val amount: Long,
    val startDate: LocalDate,
    val anchorDay: Int = 1,
    val customLengthDays: Int? = null,
    val recurring: Boolean,
    val carryOver: Boolean = false,
    /** Reserved for archiving; all current budgets are active. */
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val name: String = "Daily",
    val iconKey: String = "account_balance_wallet",
    /** ARGB; 0 means "use the app accent". */
    val color: Int = 0,
    /** The main day-to-day budget: always exists, can't be deleted, drives Home and reminders. */
    val isPrimary: Boolean = false,
    /** Split into a daily limit (day-to-day) or just track the total (trips, festivals). */
    val dailySplit: Boolean = true,
    /** Paused from [pauseStart] to [pauseEnd] inclusive; null end = until resumed. */
    val pauseStart: LocalDate? = null,
    val pauseEnd: LocalDate? = null,
    val sortOrder: Int = 0,
) {
    fun isPausedOn(date: LocalDate): Boolean =
        pauseStart != null && !date.isBefore(pauseStart) && (pauseEnd == null || !date.isAfter(pauseEnd))

    /** Paused now or later (a pause that hasn't fully ended). */
    fun hasPendingPause(today: LocalDate): Boolean = pauseStart != null && (pauseEnd == null || !pauseEnd.isBefore(today))
}

/**
 * A concrete budget window. Periods are materialised (stored) so history stays accurate even
 * after the plan changes. [carryIn] is leftover (+) or deficit (-) brought from the previous period.
 */
@Entity(
    tableName = "budget_periods",
    foreignKeys = [
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("budgetId"), Index("startDate"), Index("endDate")],
)
data class BudgetPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetId: Long,
    val startDate: LocalDate,
    /** Inclusive. */
    val endDate: LocalDate,
    val baseAmount: Long,
    val carryIn: Long = 0,
) {
    val totalAmount: Long get() = baseAmount + carryIn
}
