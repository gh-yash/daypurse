package bizappz.daypurse.domain.payments

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.payments.PaymentParser.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class PaymentInboxTest {
    private lateinit var db: AppDatabase
    private lateinit var inbox: PaymentInbox
    private lateinit var expenses: ExpenseRepository
    private var now = 1_000_000_000L
    private val gpay = PaymentSource("gpay", "Google Pay", SourceKind.UPI_APP)
    private val bank = PaymentSource("bank", "Messages", SourceKind.SMS)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().addCallback(AppDatabase.SeedCallback).build()
        val log = ActivityLog(db)
        expenses = ExpenseRepository(db, log)
        inbox = PaymentInbox(db, expenses, log) { now }
    }

    @After fun tearDown() = db.close()

    @Test
    fun samePaymentFromTwoAppsCountsOnce() = runTest {
        assertNotNull(inbox.add(13500, null, bank, now))
        // The UPI app's notice a minute later adds the payee to the same entry.
        assertNull(inbox.add(13500, "Ravi Stores", gpay, now + 60_000))
        val waiting = inbox.waiting.first()
        assertEquals(1, waiting.size)
        assertEquals("Ravi Stores", waiting.single().payee)
        // Much later, the same amount is a new payment.
        assertNotNull(inbox.add(13500, "Ravi Stores", gpay, now + PaymentInbox.DUPLICATE_WINDOW_MS + 1))
    }

    @Test
    fun loggingRemembersThePayeesCategory() = runTest {
        val today = LocalDate.now()
        val budget = BudgetEngine(db, ActivityLog(db))
            .createBudget(Budget(type = BudgetType.MONTHLY, amount = 1_000_00, startDate = today, recurring = true), today)
        val first = inbox.add(13500, "Ravi Stores", gpay, now)!!
        assertNull(inbox.suggestedCategory(first))
        val spend = inbox.logIt(first, 13500, 2, budget, null)!!
        assertEquals("Ravi Stores", expenses.get(spend)!!.payee)
        assertTrue(inbox.waiting.first().isEmpty())
        // Logged twice (notification + app at once) only counts once.
        assertNull(inbox.logIt(first, 13500, 2, budget, null))

        val next = inbox.add(9900, "ravi stores", gpay, now + PaymentInbox.DUPLICATE_WINDOW_MS * 2)!!
        assertEquals(2L, inbox.suggestedCategory(next))
        inbox.ignore(next)
        assertTrue(inbox.waiting.first().isEmpty())
    }
}
