package bizappz.daypurse.data

import bizappz.daypurse.domain.activity.ActivityLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.backup.BackupException
import bizappz.daypurse.data.backup.BackupManager
import bizappz.daypurse.data.backup.BackupMeta
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.db.DefaultCategories
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.recurring.RecurringSpends
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.Repeat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private lateinit var db: AppDatabase
    private lateinit var backup: BackupManager
    private val today = LocalDate.of(2026, 11, 20)

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().addCallback(AppDatabase.SeedCallback).build()

    @Before fun setUp() { db = newDb(); backup = BackupManager(db, ActivityLog(db)) }
    @After fun tearDown() = db.close()

    private suspend fun seedData() {
        val cats = CategoryRepository(db.categoryDao(), ActivityLog(db))
        val pets = cats.add("Pets, \"furry\"", "pets", 123)
        cats.remove(cats.get(1)!!) // hide a default
        val engine = BudgetEngine(db, ActivityLog(db))
        val daily = engine.createBudget(
            Budget(type = BudgetType.WEEKLY, amount = 700000, startDate = today, anchorDay = 1, recurring = true, carryOver = true),
            today,
        )
        val trip = engine.createBudget(
            Budget(type = BudgetType.CUSTOM, amount = 300000, startDate = today, customLengthDays = 7, recurring = false,
                name = "Goa trip", iconKey = "flight", color = 123, dailySplit = false),
            today,
        )
        engine.pause(daily, today, today.plusDays(2))
        val expenses = ExpenseRepository(db, ActivityLog(db))
        expenses.add(13550, pets, today, "vet, checkup", daily, payee = "City Vet")
        expenses.add(2000, null, today, null, trip)
        val recurring = RecurringSpends(db, expenses, engine, ActivityLog(db))
        val rent = recurring.create(1500000, pets, "Rent", null, Repeat.MONTHLY, today)
        expenses.add(1500000, pets, today, "Rent", daily, recurringId = rent)
    }

    @Test
    fun exportThenImportIntoFreshDatabaseIsIdentical() = runTest {
        seedData()
        val json = backup.exportJson(BackupMeta("INR"))
        val src = db.backupDao()
        val expected = listOf(src.categories(), src.budgets(), src.periods(), src.expenses(), src.recurring())

        val other = newDb()
        val meta = BackupManager(other, ActivityLog(other)).importJson(json)
        val dst = other.backupDao()
        assertEquals("INR", meta.currencyCode)
        assertEquals(expected, listOf(dst.categories(), dst.budgets(), dst.periods(), dst.expenses(), dst.recurring()))
        assertEquals("City Vet", dst.expenses().first { it.amount == 13550L }.payee)
        assertEquals(1, dst.recurring().size)
        other.close()
    }

    @Test
    fun invalidFileIsRejectedAndDataIsKept() = runTest {
        seedData()
        val before = db.backupDao().expenses()
        for (bad in listOf("not json", """{"app":"other","version":1}""", """{"app":"daypurse","version":1}""")) {
            try {
                backup.importJson(bad)
                fail("Expected failure for $bad")
            } catch (_: BackupException) {
            } catch (_: org.json.JSONException) {
            }
        }
        assertEquals(before, db.backupDao().expenses())
    }

    @Test
    fun newerFormatIsRejected() = runTest {
        try {
            backup.importJson("""{"app":"daypurse","version":99}""")
            fail()
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun csvEscapesCommasAndQuotes() = runTest {
        seedData()
        val lines = backup.exportCsv().trim().lines()
        assertEquals("date,budget,category,amount,note", lines[0])
        assertTrue(lines.contains("2026-11-20,Daily,\"Pets, \"\"furry\"\"\",135.5,\"vet, checkup\""))
        assertTrue(lines.contains("2026-11-20,Goa trip,,20,"))
    }

    @Test
    fun resetLeavesOnlyDefaultCategories() = runTest {
        seedData()
        backup.resetAll()
        val dao = db.backupDao()
        assertTrue(dao.expenses().isEmpty() && dao.budgets().isEmpty() && dao.periods().isEmpty())
        assertEquals(DefaultCategories.all.map { it.name }, dao.categories().map { it.name })
        assertTrue(dao.categories().none { it.isHidden })
    }

    @Test
    fun resetClearsHistoryButRecordsTheReset() = runTest {
        seedData()
        backup.resetAll()
        val entries = ActivityLog(db).observe(0, emptySet(), "").first()
        assertEquals(listOf(ActivityAction.RESET), entries.map { it.action })
        assertTrue(db.backupDao().recurring().isEmpty())
    }
}
