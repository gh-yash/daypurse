package bizappz.daypurse.domain.budget

import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PeriodCalculatorTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val monday = d(2026, 11, 16)

    @Test
    fun weeklyWindowStartsOnAnchorWeekday() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        val budget = Budget(type = BudgetType.WEEKLY, amount = 7000, startDate = monday, anchorDay = 1, recurring = true)
        assertEquals(DateWindow(monday, d(2026, 11, 22)), PeriodCalculator.naturalWindow(budget, d(2026, 11, 19)))
        assertEquals(DateWindow(monday, d(2026, 11, 22)), PeriodCalculator.naturalWindow(budget, d(2026, 11, 22)))
        assertEquals(DateWindow(d(2026, 11, 23), d(2026, 11, 29)), PeriodCalculator.naturalWindow(budget, d(2026, 11, 23)))
    }

    @Test
    fun weeklyWithSundayAnchor() {
        val budget = Budget(type = BudgetType.WEEKLY, amount = 7000, startDate = monday, anchorDay = 7, recurring = true)
        assertEquals(DateWindow(d(2026, 11, 15), d(2026, 11, 21)), PeriodCalculator.naturalWindow(budget, monday))
    }

    @Test
    fun monthlyWindowSpansAnchorToDayBeforeNextAnchor() {
        val budget = Budget(type = BudgetType.MONTHLY, amount = 30000, startDate = d(2026, 1, 1), anchorDay = 25, recurring = true)
        assertEquals(DateWindow(d(2026, 10, 25), d(2026, 11, 24)), PeriodCalculator.naturalWindow(budget, d(2026, 11, 10)))
        assertEquals(DateWindow(d(2026, 11, 25), d(2026, 12, 24)), PeriodCalculator.naturalWindow(budget, d(2026, 11, 25)))
        // Year boundary
        assertEquals(DateWindow(d(2026, 12, 25), d(2027, 1, 24)), PeriodCalculator.naturalWindow(budget, d(2027, 1, 3)))
    }

    @Test
    fun monthlyAnchor31ClampsInShortMonths() {
        val budget = Budget(type = BudgetType.MONTHLY, amount = 30000, startDate = d(2027, 1, 31), anchorDay = 31, recurring = true)
        val first = PeriodCalculator.firstPeriod(budget)
        assertEquals(d(2027, 1, 31) to d(2027, 2, 27), first.startDate to first.endDate)
        val second = PeriodCalculator.nextPeriod(budget, first, 0)
        assertEquals(d(2027, 2, 28) to d(2027, 3, 30), second.startDate to second.endDate)
        val third = PeriodCalculator.nextPeriod(budget, second, 0)
        assertEquals(d(2027, 3, 31) to d(2027, 4, 29), third.startDate to third.endDate)
    }

    @Test
    fun leapYearFebruary() {
        val budget = Budget(type = BudgetType.MONTHLY, amount = 1, startDate = d(2028, 1, 30), anchorDay = 30, recurring = true)
        val first = PeriodCalculator.firstPeriod(budget)
        assertEquals(d(2028, 2, 28), first.endDate) // next start = Feb 29 (leap year)
    }

    @Test
    fun firstPeriodIsProratedWhenStartingMidWeek() {
        val wednesday = d(2026, 11, 18)
        val budget = Budget(type = BudgetType.WEEKLY, amount = 7000, startDate = wednesday, anchorDay = 1, recurring = true)
        val first = PeriodCalculator.firstPeriod(budget)
        assertEquals(wednesday to d(2026, 11, 22), first.startDate to first.endDate)
        assertEquals(5000L, first.baseAmount)
        val next = PeriodCalculator.nextPeriod(budget, first, carryIn = 250)
        assertEquals(d(2026, 11, 23) to d(2026, 11, 29), next.startDate to next.endDate)
        assertEquals(7000L, next.baseAmount)
        assertEquals(7250L, next.totalAmount)
    }

    @Test
    fun firstPeriodIsFullWhenStartingOnAnchor() {
        val budget = Budget(type = BudgetType.WEEKLY, amount = 7000, startDate = monday, anchorDay = 1, recurring = true)
        assertEquals(7000L, PeriodCalculator.firstPeriod(budget).baseAmount)
    }

    @Test
    fun customPeriodsRepeatWithSameLength() {
        val start = d(2026, 12, 1)
        val budget = Budget(type = BudgetType.CUSTOM, amount = 10000, startDate = start, customLengthDays = 10, recurring = true)
        val first = PeriodCalculator.firstPeriod(budget)
        assertEquals(start to d(2026, 12, 10), first.startDate to first.endDate)
        assertEquals(10000L, first.baseAmount)
        val second = PeriodCalculator.nextPeriod(budget, first, 0)
        assertEquals(d(2026, 12, 11) to d(2026, 12, 20), second.startDate to second.endDate)
        assertEquals(DateWindow(d(2026, 12, 21), d(2026, 12, 30)), PeriodCalculator.naturalWindow(budget, d(2026, 12, 25)))
    }
}
