package bizappz.daypurse.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

/**
 * One spend. [amount] is stored in minor units (1/100 of the currency unit) to avoid
 * floating-point rounding. A spend belongs to one budget ([budgetId]) and to that budget's
 * period by [date], so changing a plan never orphans spends.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Budget::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecurringSpend::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("categoryId"), Index("amount"), Index("budgetId"), Index("recurringId"), Index("payee")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val categoryId: Long?,
    val note: String? = null,
    val date: LocalDate,
    /** Epoch millis when the entry was created; used for ordering and recency. */
    val createdAt: Long = System.currentTimeMillis(),
    /** The budget this spend counts towards. */
    val budgetId: Long,
    /** Who was paid (from a payment notification); used to remember their usual category. */
    val payee: String? = null,
    /** The recurring spend that added this one, if any. */
    val recurringId: Long? = null,
)

data class ExpenseWithCategory(
    @Embedded val expense: Expense,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: Category?,
)

/** A spend the user repeats often: same amount and category (shown as a one-tap chip). */
data class QuickSpend(val amount: Long, val categoryId: Long?, val note: String?, val uses: Int)

/** Result rows for aggregate queries. */
data class AmountUsage(val amount: Long, val uses: Int)

data class CategoryTotal(val categoryId: Long?, val total: Long)

data class DailyTotal(val date: LocalDate, val total: Long)
