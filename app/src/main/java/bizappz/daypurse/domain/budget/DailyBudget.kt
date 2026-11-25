package bizappz.daypurse.domain.budget

import bizappz.daypurse.data.model.BudgetPeriod
import java.time.LocalDate

/**
 * Everything the home screen needs for one day of an active period. All money in minor units.
 */
data class DayStatus(
    val period: BudgetPeriod,
    val today: LocalDate,
    /** 1-based day number within the period. */
    val dayNumber: Int,
    val periodDays: Int,
    /** Days left including today. */
    val daysLeft: Int,
    val totalBudget: Long,
    val spentBeforeToday: Long,
    val spentToday: Long,
    /** Today's allowance. Fixed for the whole day; only earlier days' spends affect it. */
    val dailyLimit: Long,
    /** The even split if nothing had been spent yet (for "usually X/day" context). */
    val baselineDailyLimit: Long,
    /** What tomorrow's limit will be given today's spends so far; null on the last day. */
    val nextDayLimit: Long?,
) {
    val spentInPeriod: Long get() = spentBeforeToday + spentToday
    /** Money left in the whole period (negative when over budget). */
    val remaining: Long get() = totalBudget - spentInPeriod
    /** Money left for today (negative when today's limit is exceeded). */
    val leftToday: Long get() = dailyLimit - spentToday
    val isOverDailyLimit: Boolean get() = spentToday > dailyLimit
    val isOverBudget: Boolean get() = remaining < 0
    /** 0..1 share of the period's budget already used. */
    val usedFraction: Float get() =
        if (totalBudget <= 0) 1f else (spentInPeriod.toFloat() / totalBudget).coerceIn(0f, 1f)
}

/**
 * The daily-limit rule: today's limit = money left before today ÷ days left (including today),
 * rounded down to a whole unit.
 *
 * Overspending today lowers every following day's limit; underspending raises them. If the
 * period's money is gone, the limit is 0 (never negative).
 */
object DailyBudgetCalculator {

    /**
     * @param isPaused days for which the budget is paused; they are skipped, so the money is
     *   spread over the remaining active days. [today] itself is always treated as active here
     *   (callers show a paused state instead of a limit when today is paused).
     */
    fun compute(
        period: BudgetPeriod,
        today: LocalDate,
        spentBeforeToday: Long,
        spentToday: Long,
        isPaused: (LocalDate) -> Boolean = { false },
    ): DayStatus {
        require(!today.isBefore(period.startDate) && !today.isAfter(period.endDate)) {
            "$today is outside period ${period.startDate}..${period.endDate}"
        }
        val active: (LocalDate) -> Boolean = { it == today || !isPaused(it) }
        val periodDays = activeDays(period.startDate, period.endDate, active).coerceAtLeast(1)
        val daysLeft = activeDays(today, period.endDate, active).coerceAtLeast(1)
        val total = period.totalAmount

        val dailyLimit = perDay(total - spentBeforeToday, daysLeft)
        val nextDayLimit = if (daysLeft > 1) perDay(total - spentBeforeToday - spentToday, daysLeft - 1) else null

        return DayStatus(
            period = period,
            today = today,
            dayNumber = activeDays(period.startDate, today, active).coerceAtLeast(1),
            periodDays = periodDays,
            daysLeft = daysLeft,
            totalBudget = total,
            spentBeforeToday = spentBeforeToday,
            spentToday = spentToday,
            dailyLimit = dailyLimit,
            baselineDailyLimit = perDay(total, periodDays),
            nextDayLimit = nextDayLimit,
        )
    }

    private fun activeDays(from: LocalDate, toInclusive: LocalDate, active: (LocalDate) -> Boolean): Int {
        var n = 0
        var d = from
        while (!d.isAfter(toInclusive)) {
            if (active(d)) n++
            d = d.plusDays(1)
        }
        return n
    }

    /**
     * Even share of [amount] over [days], rounded *down* to a whole currency unit so limits read
     * cleanly ("1,086" not "1,086.50") and rounding never lets you overspend; the few leftover
     * cents stay in the pool and flow into later days. Tiny budgets (under one unit a day) keep
     * their exact value instead of showing 0.
     */
    fun perDay(amount: Long, days: Int): Long {
        if (amount <= 0 || days <= 0) return 0
        val exact = amount / days
        val whole = exact / UNIT * UNIT
        return if (whole > 0) whole else exact
    }

    /** Minor units per whole currency unit (amounts are stored in hundredths). */
    private const val UNIT = 100L

}
