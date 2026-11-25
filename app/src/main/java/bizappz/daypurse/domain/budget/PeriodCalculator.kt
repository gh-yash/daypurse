package bizappz.daypurse.domain.budget

import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.BudgetType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** An inclusive date range. */
data class DateWindow(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "end $end before start $start" } }

    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Pure period maths: where periods start and end and how much each one gets.
 * No I/O, so it is fully unit-tested.
 */
object PeriodCalculator {

    /** The full ("natural") weekly/monthly/custom window that contains [date]. */
    fun naturalWindow(budget: Budget, date: LocalDate): DateWindow = when (budget.type) {
        BudgetType.WEEKLY -> {
            val anchor = DayOfWeek.of(budget.anchorDay.coerceIn(1, 7))
            val back = (date.dayOfWeek.value - anchor.value + 7) % 7
            val start = date.minusDays(back.toLong())
            DateWindow(start, start.plusDays(6))
        }

        BudgetType.MONTHLY -> {
            val ym = YearMonth.from(date)
            var start = monthlyStart(ym, budget.anchorDay)
            if (date.isBefore(start)) start = monthlyStart(ym.minusMonths(1), budget.anchorDay)
            val nextStart = monthlyStart(YearMonth.from(start).plusMonths(1), budget.anchorDay)
            DateWindow(start, nextStart.minusDays(1))
        }

        BudgetType.CUSTOM -> {
            val length = customLength(budget)
            val offset = ChronoUnit.DAYS.between(budget.startDate, date).coerceAtLeast(0)
            val start = budget.startDate.plusDays(offset / length * length)
            DateWindow(start, start.plusDays(length - 1))
        }
    }

    /**
     * The first period of [budget]. Weekly/monthly plans that start mid-period get a partial
     * first period with the amount prorated by days, so starting on a Wednesday doesn't squeeze
     * a whole week's money into five days.
     */
    fun firstPeriod(budget: Budget): BudgetPeriod {
        val natural = naturalWindow(budget, budget.startDate)
        val window = DateWindow(budget.startDate, natural.end)
        val base = if (window.days == natural.days) {
            budget.amount
        } else {
            // Rounded to a whole currency unit: "$4,286", not "$4,285.71".
            (budget.amount.toDouble() * window.days / natural.days / 100).roundToLong() * 100
        }
        return BudgetPeriod(budgetId = budget.id, startDate = window.start, endDate = window.end, baseAmount = base)
    }

    /** The period right after [previous]. [carryIn] is leftover (+) or deficit (−) to bring along. */
    fun nextPeriod(budget: Budget, previous: BudgetPeriod, carryIn: Long): BudgetPeriod {
        val start = previous.endDate.plusDays(1)
        val window = when (budget.type) {
            BudgetType.CUSTOM -> DateWindow(start, start.plusDays(customLength(budget) - 1))
            else -> naturalWindow(budget, start)
        }
        return BudgetPeriod(
            budgetId = budget.id,
            startDate = window.start,
            endDate = window.end,
            baseAmount = budget.amount,
            carryIn = carryIn,
        )
    }

    private fun monthlyStart(ym: YearMonth, anchorDay: Int): LocalDate =
        ym.atDay(anchorDay.coerceIn(1, 31).coerceAtMost(ym.lengthOfMonth()))

    private fun customLength(budget: Budget): Long =
        requireNotNull(budget.customLengthDays) { "CUSTOM budget needs customLengthDays" }
            .also { require(it > 0) { "customLengthDays must be positive" } }
            .toLong()
}
