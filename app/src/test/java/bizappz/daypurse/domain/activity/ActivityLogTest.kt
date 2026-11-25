package bizappz.daypurse.domain.activity

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.budget.BudgetEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ActivityLogTest {
    private lateinit var db: AppDatabase
    private var now = 100 * ActivityLog.DAY_MS
    private lateinit var log: ActivityLog
    private lateinit var expenses: ExpenseRepository
    private lateinit var engine: BudgetEngine
    private val today = LocalDate.of(2026, 11, 23)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().addCallback(AppDatabase.SeedCallback).build()
        log = ActivityLog(db) { now }
        expenses = ExpenseRepository(db, log)
        engine = BudgetEngine(db, log)
    }

    @After fun tearDown() = db.close()

    @Test
    fun recordsChangesWithFiltersAndSearch() = runTest {
        val budget = engine.createBudget(Budget(type = BudgetType.MONTHLY, amount = 1_000_00, startDate = today, recurring = true), today)
        val id = expenses.add(12000, 1, today, "lunch with Asha", budget)
        expenses.update(expenses.get(id)!!.copy(amount = 15000))
        expenses.delete(expenses.get(id)!!)

        val spends = log.observe(0, setOf(ActivityKind.SPEND), "").first()
        assertEquals(listOf(ActivityAction.DELETED, ActivityAction.EDITED, ActivityAction.ADDED), spends.map { it.action })
        assertEquals(12000L, ActivityLog.expenseFrom(spends[1].snapshot!!)!!.amount) // edit keeps the old value
        assertEquals(1, log.observe(0, setOf(ActivityKind.BUDGET), "").first().size)
        assertEquals(3, log.observe(0, emptySet(), "asha").first().size)
        assertEquals(0, log.observe(0, emptySet(), "nothing like this").first().size)
    }

    @Test
    fun deletedSpendCanBeRestored() = runTest {
        val budget = engine.createBudget(Budget(type = BudgetType.MONTHLY, amount = 1_000_00, startDate = today, recurring = true), today)
        val id = expenses.add(12000, 1, today, "lunch", budget)
        expenses.delete(expenses.get(id)!!)
        val deleted = log.observe(0, setOf(ActivityKind.SPEND), "").first().first()
        val restored = expenses.restoreDeleted(ActivityLog.expenseFrom(deleted.snapshot!!)!!, budget)
        assertNotNull(restored)
        assertEquals(id, restored!!.id)
        assertEquals("lunch", expenses.get(id)!!.note)
    }

    @Test
    fun pruneKeepsOnlyTheRetentionWindow() = runTest {
        log.record(ActivityKind.DATA, ActivityAction.EXPORTED)
        now += 40 * ActivityLog.DAY_MS
        log.record(ActivityKind.DATA, ActivityAction.EXPORTED)
        log.prune(30)
        assertEquals(1, log.observe(0, emptySet(), "").first().size)
        log.prune(0) // forever: nothing removed
        assertEquals(1, log.observe(0, emptySet(), "").first().size)
    }

    @Test
    fun quickSpendsAreRepeatedAmountAndCategoryPairs() = runTest {
        val budget = engine.createBudget(Budget(type = BudgetType.MONTHLY, amount = 1_000_00, startDate = today, recurring = true), today)
        repeat(3) { expenses.add(2000, 1, today, "Chai", budget) }
        expenses.add(2000, 2, today, null, budget)          // once in another category: not a habit
        repeat(2) { expenses.add(4500, 3, today, null, budget) }
        val quick = expenses.quickSpends(today).first()
        assertEquals(listOf(2000L to 1L, 4500L to 3L), quick.map { it.amount to it.categoryId })
        assertEquals("Chai", quick.first().note)
    }
}
