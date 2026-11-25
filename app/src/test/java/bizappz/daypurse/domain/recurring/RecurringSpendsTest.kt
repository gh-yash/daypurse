package bizappz.daypurse.domain.recurring

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.model.RecurringSpend
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.budget.BudgetEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RecurringSpendsTest {
    private lateinit var db: AppDatabase
    private lateinit var log: ActivityLog
    private lateinit var engine: BudgetEngine
    private lateinit var expenses: ExpenseRepository
    private lateinit var recurring: RecurringSpends
    private val start = LocalDate.of(2026, 1, 1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().addCallback(AppDatabase.SeedCallback).build()
        log = ActivityLog(db)
        engine = BudgetEngine(db, log)
        expenses = ExpenseRepository(db, log)
        recurring = RecurringSpends(db, expenses, engine, log)
    }

    @After fun tearDown() = db.close()

    @Test
    fun monthlyKeepsTheDayOfMonth() {
        val r = RecurringSpend(amount = 1, categoryId = null, repeat = Repeat.MONTHLY, anchorDay = 31, nextDate = LocalDate.of(2026, 1, 31))
        val feb = r.after(LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), r.after(feb))
        assertEquals(LocalDate.of(2026, 1, 8), r.copy(repeat = Repeat.WEEKLY).after(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2027, 1, 31), r.copy(repeat = Repeat.YEARLY).after(LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun catchUpAddsEveryDueOccurrenceOnce() = runTest {
        val budget = engine.createBudget(Budget(type = BudgetType.MONTHLY, amount = 10_000_00, startDate = start, recurring = true), start)
        val rent = recurring.create(5_000_00, null, "Rent", null, Repeat.MONTHLY, start)
        expenses.add(5_000_00, null, start, "Rent", budget, recurringId = rent)

        val today = LocalDate.of(2026, 3, 15)
        assertEquals(2, recurring.catchUp(today)) // 1 Feb, 1 Mar
        assertEquals(0, recurring.catchUp(today)) // nothing twice
        val all = db.backupDao().expenses()
        assertEquals(listOf(start, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), all.map { it.date })
        assertEquals(LocalDate.of(2026, 4, 1), recurring.get(rent)!!.nextDate)
        val auto = log.observe(0, setOf(ActivityKind.SPEND), "").first().count { it.action == ActivityAction.AUTO_ADDED }
        assertEquals(2, auto)
    }

    @Test
    fun pausedAndSkippedAreRespected() = runTest {
        engine.createBudget(Budget(type = BudgetType.MONTHLY, amount = 10_000_00, startDate = start, recurring = true), start)
        val id = recurring.create(100, null, "Gym", null, Repeat.WEEKLY, start)
        recurring.setPaused(recurring.get(id)!!, true)
        assertEquals(0, recurring.catchUp(LocalDate.of(2026, 1, 20)))
        recurring.setPaused(recurring.get(id)!!, false)
        recurring.skipNext(recurring.get(id)!!) // skips 8 Jan
        assertEquals(LocalDate.of(2026, 1, 15), recurring.get(id)!!.nextDate)
        assertEquals(1, recurring.catchUp(LocalDate.of(2026, 1, 20)))
    }
}
