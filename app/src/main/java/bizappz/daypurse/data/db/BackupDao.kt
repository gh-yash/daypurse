package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.RecurringSpend

/** Bulk read/replace of every table, for backup, restore and reset. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<Category>
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM budget_periods ORDER BY id") suspend fun periods(): List<BudgetPeriod>
    @Query("SELECT * FROM expenses ORDER BY date, createdAt") suspend fun expenses(): List<Expense>
    @Query("SELECT * FROM recurring_spends ORDER BY id") suspend fun recurring(): List<RecurringSpend>

    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM recurring_spends") suspend fun clearRecurring()
    @Query("DELETE FROM budget_periods") suspend fun clearPeriods()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertBudgets(items: List<Budget>)
    @Insert suspend fun insertPeriods(items: List<BudgetPeriod>)
    @Insert suspend fun insertExpenses(items: List<Expense>)
    @Insert suspend fun insertRecurring(items: List<RecurringSpend>)
}
