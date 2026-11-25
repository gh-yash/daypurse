package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import bizappz.daypurse.data.model.AmountUsage
import bizappz.daypurse.data.model.CategoryTotal
import bizappz.daypurse.data.model.DailyTotal
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.data.model.QuickSpend
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(expense: Expense): Long
    @Insert suspend fun insertAll(expenses: List<Expense>)
    @Update suspend fun update(expense: Expense)
    @Delete suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Transaction
    @Query(
        """
        SELECT * FROM expenses
        WHERE date BETWEEN :start AND :end
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:budgetId IS NULL OR budgetId = :budgetId)
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeBetween(
        start: LocalDate,
        end: LocalDate,
        categoryId: Long? = null,
        budgetId: Long? = null,
    ): Flow<List<ExpenseWithCategory>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE budgetId = :budgetId AND date BETWEEN :start AND :end")
    fun observeTotalBetween(budgetId: Long, start: LocalDate, end: LocalDate): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE budgetId = :budgetId AND date BETWEEN :start AND :end")
    suspend fun totalBetween(budgetId: Long, start: LocalDate, end: LocalDate): Long

    @Query("SELECT COUNT(*) FROM expenses WHERE budgetId = :budgetId")
    suspend fun countIn(budgetId: Long): Int

    @Query("UPDATE expenses SET budgetId = :to WHERE budgetId = :from")
    suspend fun moveAll(from: Long, to: Long)

    /**
     * Most frequent amounts since [since], optionally within one category.
     * Ties are broken by how recently the amount was used.
     */
    @Query(
        """
        SELECT amount, COUNT(*) AS uses FROM expenses
        WHERE date >= :since AND (:categoryId IS NULL OR categoryId = :categoryId)
        GROUP BY amount
        ORDER BY uses DESC, MAX(createdAt) DESC
        LIMIT :limit
        """,
    )
    fun observeFrequentAmounts(since: LocalDate, categoryId: Long?, limit: Int): Flow<List<AmountUsage>>

    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM expenses
        WHERE date BETWEEN :start AND :end AND (:budgetId IS NULL OR budgetId = :budgetId)
        GROUP BY categoryId ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(start: LocalDate, end: LocalDate, budgetId: Long? = null): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT date, SUM(amount) AS total FROM expenses
        WHERE date BETWEEN :start AND :end AND (:budgetId IS NULL OR budgetId = :budgetId)
        GROUP BY date ORDER BY date
        """,
    )
    fun observeDailyTotals(start: LocalDate, end: LocalDate, budgetId: Long? = null): Flow<List<DailyTotal>>

    /**
     * Spends the user repeats: same amount and category at least [minUses] times since [since]
     * (auto-added recurring spends don't count). Each carries its most common note, so a chip
     * can read "Chai" rather than just the category.
     */
    @Query(
        """
        SELECT e.amount AS amount, e.categoryId AS categoryId, COUNT(*) AS uses,
          (SELECT n.note FROM expenses n
             WHERE n.amount = e.amount AND n.categoryId IS e.categoryId AND n.note IS NOT NULL AND n.date >= :since
             GROUP BY n.note ORDER BY COUNT(*) DESC, MAX(n.createdAt) DESC LIMIT 1) AS note
        FROM expenses e
        WHERE e.date >= :since AND e.recurringId IS NULL
        GROUP BY e.amount, e.categoryId
        HAVING COUNT(*) >= :minUses
        ORDER BY uses DESC, MAX(e.createdAt) DESC
        LIMIT :limit
        """,
    )
    fun observeQuickSpends(since: LocalDate, minUses: Int, limit: Int): Flow<List<QuickSpend>>

    /** Category of the latest spend paid to [payee], to suggest it again. */
    @Query("SELECT categoryId FROM expenses WHERE payee = :payee COLLATE NOCASE AND categoryId IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastCategoryForPayee(payee: String): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE date = :date")
    suspend fun countOn(date: LocalDate): Int

    @Query("SELECT * FROM expenses ORDER BY date, createdAt")
    suspend fun all(): List<Expense>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
