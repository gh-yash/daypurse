package bizappz.daypurse.data

import bizappz.daypurse.domain.activity.ActivityLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.db.DefaultCategories
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var expenses: ExpenseRepository
    private val today = LocalDate.of(2026, 11, 20)
    /** Spends need a budget (foreign key). */
    private var bid = 0L

    @Before
    fun setUp() {
        // Same seed callback as production, but an in-memory database.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.SeedCallback)
            .build()
        categories = CategoryRepository(db.categoryDao(), ActivityLog(db))
        expenses = ExpenseRepository(db, ActivityLog(db))
        bid = runBlocking {
            db.budgetDao().insert(Budget(type = BudgetType.MONTHLY, amount = 100_000, startDate = today, recurring = true, isPrimary = true))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun defaultCategoriesAreSeeded() = runTest {
        val visible = categories.visible.first()
        assertEquals(DefaultCategories.all.map { it.name }, visible.map { it.name })
        assertTrue(visible.all { it.isDefault })
    }

    @Test
    fun removingDefaultHidesItAndRestoreBringsItBack() = runTest {
        val food = categories.visible.first().first()
        categories.remove(food)
        assertTrue(categories.visible.first().none { it.id == food.id })
        assertTrue(categories.all.first().first { it.id == food.id }.isHidden)

        categories.restoreDefaults()
        assertTrue(categories.visible.first().any { it.id == food.id })
    }

    @Test
    fun unusedCustomCategoryIsDeletedButUsedOneIsHidden() = runTest {
        val unused = categories.add("Pets", "pets", 0)
        val used = categories.add("Gym", "fitness_center", 0)
        expenses.add(500, used, today, null, bid)

        categories.remove(categories.get(unused)!!)
        categories.remove(categories.get(used)!!)

        assertNull(categories.get(unused))
        assertTrue(categories.get(used)!!.isHidden)
    }

    @Test
    fun frequentAmountsRankByUseAndIgnoreOneOffs() = runTest {
        val food = categories.visible.first()[0].id
        val fuel = categories.visible.first()[3].id
        repeat(3) { expenses.add(13500, food, today.minusDays(it.toLong()), null, bid) }
        repeat(2) { expenses.add(5000, fuel, today, null, bid) }
        expenses.add(99900, food, today, null, bid) // one-off
        expenses.add(13500, food, today.minusDays(90), null, bid) // outside lookback

        assertEquals(listOf(13500L, 5000L), expenses.frequentAmounts(today).first())
        assertEquals(listOf(5000L), expenses.frequentAmounts(today, categoryId = fuel).first())
    }

    @Test
    fun quickPicksPreferMostUsedThenFillWithDefaults() = runTest {
        val visible = categories.visible.first()
        val groceries = visible[1]
        repeat(3) { expenses.add(100, groceries.id, today, null, bid) }

        val picks = categories.quickPicks(today, limit = 4).first()
        assertEquals(4, picks.size)
        assertEquals(groceries.id, picks.first().id)
        assertEquals(picks.size, picks.distinctBy { it.id }.size)
    }

    @Test
    fun totalsAndCategoryBreakdown() = runTest {
        val food = categories.visible.first()[0].id
        expenses.add(1000, food, today, null, bid)
        expenses.add(2500, null, today.minusDays(1), null, bid)
        expenses.add(700, food, today.minusDays(10), null, bid)

        assertEquals(3500L, expenses.totalBetween(bid, today.minusDays(6), today).first())
        val totals = expenses.categoryTotals(today.minusDays(6), today).first()
        assertEquals(listOf(null to 2500L, food to 1000L), totals.map { it.categoryId to it.total })
    }

    @Test
    fun spendsAreSeparatedByBudget() = runTest {
        val trip = db.budgetDao().insert(Budget(type = BudgetType.CUSTOM, amount = 50_000, startDate = today, customLengthDays = 5, recurring = false, name = "Trip"))
        expenses.add(1_000, null, today, null, bid)
        expenses.add(7_000, null, today, null, trip)
        assertEquals(1_000L, expenses.totalBetween(bid, today, today).first())
        assertEquals(7_000L, expenses.totalBetween(trip, today, today).first())
        assertEquals(2, expenses.between(today, today).first().size)
        assertEquals(listOf(7_000L), expenses.between(today, today, budgetId = trip).first().map { it.expense.amount })
    }

    @Test
    fun undoRemoveRestoresHiddenAndDeletedCategories() = runTest {
        val default = categories.visible.first().first()
        val r1 = categories.remove(default)
        assertEquals(CategoryRepository.Removal.HIDDEN, r1)
        categories.undoRemove(default, r1)
        assertTrue(categories.visible.first().any { it.id == default.id })

        val custom = categories.get(categories.add("Pets", "pets", 0))!!
        val r2 = categories.remove(custom)
        assertEquals(CategoryRepository.Removal.DELETED, r2)
        categories.undoRemove(custom, r2)
        assertEquals("Pets", categories.get(custom.id)!!.name)
    }

    @Test
    fun categoryNamesAreUniqueIgnoringCase() = runTest {
        assertTrue(categories.isNameTaken("groceries"))
        assertTrue(categories.isNameTaken("  Groceries "))
        val groceries = categories.visible.first().first { it.name == "Groceries" }
        assertTrue(!categories.isNameTaken("Groceries", exceptId = groceries.id))
        assertTrue(!categories.isNameTaken("Pets"))
    }

    @Test
    fun deletedExpenseCanBeRestoredWithSameId() = runTest {
        val id = expenses.add(500, null, today, "tea", bid)
        val e = expenses.get(id)!!
        expenses.delete(e)
        assertNull(expenses.get(id))
        expenses.restore(e)
        assertEquals(e, expenses.get(id))
    }

    @Test
    fun betweenFiltersByCategory() = runTest {
        val food = categories.visible.first()[0].id
        expenses.add(100, food, today, null, bid)
        expenses.add(200, null, today, null, bid)
        assertEquals(2, expenses.between(today, today).first().size)
        assertEquals(listOf(100L), expenses.between(today, today, food).first().map { it.expense.amount })
    }

    @Test
    fun reorderPersistsAndDrivesListAndQuickPickFallback() = runTest {
        val ids = categories.visible.first().map { it.id }
        val reversed = ids.reversed()
        categories.reorder(reversed)
        assertEquals(reversed, categories.visible.first().map { it.id })
        // With no spending history, quick picks follow the user's order.
        assertEquals(reversed.take(4), categories.quickPicks(today, limit = 4).first().map { it.id })
    }
}
