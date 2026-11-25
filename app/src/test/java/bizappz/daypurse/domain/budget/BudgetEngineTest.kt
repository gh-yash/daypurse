package bizappz.daypurse.domain.budget

import bizappz.daypurse.domain.activity.ActivityLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BudgetEngineTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: BudgetEngine
    private lateinit var expenses: ExpenseRepository
    private val monday = LocalDate.of(2026, 11, 16)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = BudgetEngine(db, ActivityLog(db))
        expenses = ExpenseRepository(db, ActivityLog(db))
    }

    @After
    fun tearDown() = db.close()

    private fun weekly(amount: Long = 7000, start: LocalDate = monday, recurring: Boolean = true, carryOver: Boolean = false) =
        Budget(type = BudgetType.WEEKLY, amount = amount, startDate = start, anchorDay = 1, recurring = recurring, carryOver = carryOver)

    private fun trip(start: LocalDate = monday, days: Int = 5, amount: Long = 50_000) = Budget(
        type = BudgetType.CUSTOM, amount = amount, startDate = start, customLengthDays = days,
        recurring = false, name = "Trip", dailySplit = false,
    )

    private suspend fun active(id: Long, today: LocalDate) = engine.observeState(id, today).first() as BudgetState.Active

    @Test
    fun noBudgetState() = runTest {
        assertEquals(BudgetState.NoBudget, engine.observePrimaryState(monday).first())
    }

    @Test
    fun firstBudgetBecomesPrimary() = runTest {
        val id = engine.createBudget(weekly(), monday)
        assertTrue(engine.get(id)!!.isPrimary)
        val second = engine.createBudget(trip(), monday)
        assertFalse(engine.get(second)!!.isPrimary)
        assertEquals(id, (engine.observePrimaryState(monday).first() as BudgetState.Active).budget.id)
    }

    @Test
    fun dailyLimitReactsToSpends() = runTest {
        val id = engine.createBudget(weekly(), monday)
        assertEquals(1000L, active(id, monday).day.dailyLimit)
        expenses.add(2200, null, monday, null, id)
        val day = active(id, monday).day
        assertEquals(2200L, day.spentToday)
        assertEquals(800L, day.nextDayLimit)
        assertEquals(800L, active(id, monday.plusDays(1)).day.dailyLimit)
    }

    @Test
    fun budgetsDoNotAffectEachOther() = runTest {
        val daily = engine.createBudget(weekly(), monday)
        val t = engine.createBudget(trip(), monday)
        expenses.add(30_000, null, monday, null, t)
        assertEquals(1000L, active(daily, monday).day.dailyLimit)
        assertEquals(20_000L, active(t, monday).day.remaining)
        assertEquals(2, engine.observeAllStates(monday).first().size)
    }

    @Test
    fun futureStartIsNotStarted() = runTest {
        val id = engine.createBudget(trip(start = monday.plusDays(7)), monday)
        assertTrue(engine.observeState(id, monday).first() is BudgetState.NotStarted)
        assertTrue(engine.observeState(id, monday.plusDays(7)).first() is BudgetState.Active)
    }

    @Test
    fun recurringBudgetCatchesUpMissedPeriods() = runTest {
        val id = engine.createBudget(weekly(), monday)
        val state = active(id, monday.plusDays(20))
        assertEquals(monday.plusDays(14), state.day.period.startDate)
        assertEquals(3, db.budgetDao().periodsOf(id).size)
    }

    @Test
    fun oneOffBudgetEndsWithSummary() = runTest {
        val id = engine.createBudget(trip(), monday)
        expenses.add(12_000, null, monday.plusDays(1), null, id)
        val state = engine.observeState(id, monday.plusDays(10)).first() as BudgetState.Ended
        assertEquals(12_000L, state.spent)
        assertEquals(50_000L, state.lastPeriod!!.totalAmount)
    }

    @Test
    fun carryOverMovesLeftoverAndDeficit() = runTest {
        val id = engine.createBudget(weekly(carryOver = true), monday)
        expenses.add(6000, null, monday.plusDays(2), null, id)
        assertEquals(8000L, active(id, monday.plusDays(7)).day.totalBudget)
        expenses.add(9500, null, monday.plusDays(8), null, id)
        assertEquals(5500L, active(id, monday.plusDays(14)).day.totalBudget)
    }

    @Test
    fun backdatedSpendRecalculatesCarryOver() = runTest {
        val id = engine.createBudget(weekly(carryOver = true), monday)
        assertEquals(14000L, active(id, monday.plusDays(7)).day.totalBudget)
        expenses.add(3000, null, monday.plusDays(1), null, id)
        engine.recalculateCarryOver(id)
        assertEquals(11000L, active(id, monday.plusDays(7)).day.totalBudget)
    }

    @Test
    fun inPlaceEditChangesCurrentPeriodAndLook() = runTest {
        val id = engine.createBudget(weekly(), monday)
        engine.updateBudget(id, monday.plusDays(2)) { it.copy(amount = 14000, name = "Everyday", dailySplit = false) }
        val s = active(id, monday.plusDays(2))
        assertEquals(14000L, s.day.totalBudget)
        assertEquals(2800L, s.day.dailyLimit)
        assertEquals("Everyday", s.budget.name)
        assertFalse(s.budget.dailySplit)
        assertTrue(s.budget.isPrimary)
    }

    @Test
    fun restructureKeepsIdentityAndSpends() = runTest {
        val id = engine.createBudget(weekly(), monday)
        expenses.add(500, null, monday, null, id)
        val wednesday = monday.plusDays(2)
        val monthly = Budget(type = BudgetType.MONTHLY, amount = 30000, startDate = wednesday, anchorDay = 1, recurring = true)
        engine.restructure(id, monthly, wednesday)

        val periods = db.budgetDao().periodsOf(id)
        assertEquals(monday.plusDays(1), periods.first().endDate)
        assertEquals(2000L, periods.first().baseAmount) // 7000 * 2 / 7
        val state = active(id, wednesday)
        assertEquals(id, state.budget.id)
        assertTrue(state.budget.isPrimary)
        assertEquals(BudgetType.MONTHLY, state.budget.type)
        assertEquals(LocalDate.of(2026, 11, 30), state.day.period.endDate)
        assertEquals(13000L, state.day.totalBudget)
        assertEquals(1, db.expenseDao().countIn(id))
    }

    @Test
    fun pausedDaysAreSkippedInTheDailySplit() = runTest {
        val id = engine.createBudget(weekly(), monday) // 7000 over Mon-Sun
        engine.pause(id, monday, monday.plusDays(1)) // Mon-Tue paused
        val mon = active(id, monday)
        assertTrue(mon.paused)
        val wed = active(id, monday.plusDays(2))
        assertFalse(wed.paused)
        assertEquals(5, wed.day.daysLeft) // Wed..Sun
        assertEquals(1400L, wed.day.dailyLimit) // 7000 / 5
    }

    @Test
    fun indefinitePauseAndResume() = runTest {
        val id = engine.createBudget(weekly(), monday)
        engine.pause(id, monday, until = null)
        assertTrue(active(id, monday.plusDays(3)).paused)
        engine.resume(id, monday.plusDays(3))
        val b = engine.get(id)!!
        assertEquals(monday.plusDays(2), b.pauseEnd)
        assertFalse(active(id, monday.plusDays(3)).paused)
        assertEquals(1700L, active(id, monday.plusDays(3)).day.dailyLimit) // 70.00 / Thu-Sun = 17.50 -> 17.00

        // Resuming a pause that starts today clears it entirely.
        engine.pause(id, monday.plusDays(4), null)
        engine.resume(id, monday.plusDays(4))
        assertNull(engine.get(id)!!.pauseStart)
    }

    @Test
    fun deleteBudgetMovingSpendsToPrimary() = runTest {
        val daily = engine.createBudget(weekly(), monday)
        val t = engine.createBudget(trip(), monday)
        expenses.add(2500, null, monday, null, t)
        engine.deleteBudget(t, moveSpendsToPrimary = true)
        assertNull(engine.get(t))
        assertEquals(2500L, active(daily, monday).day.spentToday)
    }

    @Test
    fun deleteBudgetWithItsSpends() = runTest {
        val daily = engine.createBudget(weekly(), monday)
        val t = engine.createBudget(trip(), monday)
        expenses.add(2500, null, monday, null, t)
        engine.deleteBudget(t, moveSpendsToPrimary = false)
        assertEquals(0, engine.spendCount(t))
        assertEquals(0L, active(daily, monday).day.spentToday)
        assertTrue(db.budgetDao().periodsOf(t).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun primaryCannotBeDeleted() = runTest {
        val daily = engine.createBudget(weekly(), monday)
        engine.deleteBudget(daily, moveSpendsToPrimary = true)
    }
}
