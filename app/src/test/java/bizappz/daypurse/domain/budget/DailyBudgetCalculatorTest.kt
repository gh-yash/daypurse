package bizappz.daypurse.domain.budget

import bizappz.daypurse.data.model.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBudgetCalculatorTest {
    private val start = LocalDate.of(2026, 11, 16)
    private val week = BudgetPeriod(budgetId = 1, startDate = start, endDate = start.plusDays(6), baseAmount = 7000)

    @Test
    fun evenSplitOnFirstDay() {
        val s = DailyBudgetCalculator.compute(week, start, spentBeforeToday = 0, spentToday = 0)
        assertEquals(1000L, s.dailyLimit)
        assertEquals(1000L, s.baselineDailyLimit)
        assertEquals(7, s.daysLeft)
        assertEquals(1, s.dayNumber)
        assertEquals(1000L, s.leftToday)
        assertEquals(1100L, s.nextDayLimit) // 70.00 / 6 days = 11.66 -> rounded down to 11.00
    }

    @Test
    fun overspendingLowersFollowingDays() {
        val day1 = DailyBudgetCalculator.compute(week, start, 0, 2200)
        assertTrue(day1.isOverDailyLimit)
        assertEquals(-1200L, day1.leftToday)
        assertEquals(800L, day1.nextDayLimit) // (7000 - 2200) / 6

        val day2 = DailyBudgetCalculator.compute(week, start.plusDays(1), 2200, 0)
        assertEquals(800L, day2.dailyLimit)
    }

    @Test
    fun underspendingRaisesFollowingDays() {
        val day3 = DailyBudgetCalculator.compute(week, start.plusDays(2), spentBeforeToday = 1000, spentToday = 0)
        assertEquals(1200L, day3.dailyLimit) // (7000 - 1000) / 5
    }

    @Test
    fun limitIsFixedForTheDayRegardlessOfTodaysSpends() {
        val a = DailyBudgetCalculator.compute(week, start.plusDays(3), 3000, 0)
        val b = DailyBudgetCalculator.compute(week, start.plusDays(3), 3000, 900)
        assertEquals(a.dailyLimit, b.dailyLimit)
    }

    @Test
    fun exhaustedBudgetGivesZeroNotNegative() {
        val s = DailyBudgetCalculator.compute(week, start.plusDays(4), spentBeforeToday = 7500, spentToday = 100)
        assertEquals(0L, s.dailyLimit)
        assertEquals(0L, s.nextDayLimit)
        assertEquals(-600L, s.remaining)
        assertTrue(s.isOverBudget)
        assertEquals(1f, s.usedFraction)
    }

    @Test
    fun lastDayHasNoNextLimit() {
        val s = DailyBudgetCalculator.compute(week, week.endDate, 6000, 0)
        assertEquals(1, s.daysLeft)
        assertEquals(1000L, s.dailyLimit)
        assertNull(s.nextDayLimit)
        assertFalse(s.isOverBudget)
    }

    @Test
    fun carryInIsPartOfTheBudget() {
        val s = DailyBudgetCalculator.compute(week.copy(carryIn = 700), start, 0, 0)
        assertEquals(7700L, s.totalBudget)
        assertEquals(1100L, s.dailyLimit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDateOutsidePeriod() {
        DailyBudgetCalculator.compute(week, start.minusDays(1), 0, 0)
    }

    @Test
    fun perDayRoundsDownToWholeUnits() {
        assertEquals(108_600L, DailyBudgetCalculator.perDay(108_650, 1)) // 1,086.50 -> 1,086
        assertEquals(96_500L, DailyBudgetCalculator.perDay(965_000, 10))  // already whole
        assertEquals(50L, DailyBudgetCalculator.perDay(150, 3))           // under 1 unit/day: keep exact
        assertEquals(0L, DailyBudgetCalculator.perDay(-500, 3))
    }

    @Test
    fun roundedCentsAreNotLostTheyRollIntoLaterDays() {
        val p = week.copy(baseAmount = 700_050) // 7,000.50 for 7 days
        val day1 = DailyBudgetCalculator.compute(p, start, 0, 100_000)
        assertEquals(100_000L, day1.dailyLimit)
        val last = DailyBudgetCalculator.compute(p, p.endDate, 600_000, 0)
        assertEquals(100_000L, last.dailyLimit) // 1,000.50 left on the last day -> 1,000 shown
        assertEquals(100_050L, last.remaining)   // the 0.50 is still in the period total
    }
}
