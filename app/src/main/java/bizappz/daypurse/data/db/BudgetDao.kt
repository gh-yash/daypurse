package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE active = 1 ORDER BY isPrimary DESC, sortOrder, id")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE active = 1 ORDER BY isPrimary DESC, sortOrder, id")
    suspend fun all(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    fun observe(id: Long): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: Long): Budget?

    @Query("SELECT * FROM budgets WHERE isPrimary = 1 LIMIT 1")
    fun observePrimary(): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE isPrimary = 1 LIMIT 1")
    suspend fun primary(): Budget?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM budgets")
    suspend fun maxSortOrder(): Int

    @Insert suspend fun insert(budget: Budget): Long
    @Update suspend fun update(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert suspend fun insertPeriod(period: BudgetPeriod): Long
    @Update suspend fun updatePeriod(period: BudgetPeriod)

    @Query("SELECT * FROM budget_periods WHERE budgetId = :budgetId AND :date BETWEEN startDate AND endDate LIMIT 1")
    fun observePeriodOn(budgetId: Long, date: LocalDate): Flow<BudgetPeriod?>

    @Query("SELECT * FROM budget_periods WHERE budgetId = :budgetId AND :date BETWEEN startDate AND endDate LIMIT 1")
    suspend fun periodOn(budgetId: Long, date: LocalDate): BudgetPeriod?

    @Query("SELECT * FROM budget_periods WHERE budgetId = :budgetId ORDER BY startDate")
    suspend fun periodsOf(budgetId: Long): List<BudgetPeriod>

    @Query("SELECT * FROM budget_periods WHERE budgetId = :budgetId ORDER BY startDate DESC LIMIT 1")
    suspend fun latestPeriod(budgetId: Long): BudgetPeriod?

    @Query("SELECT * FROM budget_periods WHERE budgetId = :budgetId ORDER BY startDate DESC")
    fun observePeriodsOf(budgetId: Long): Flow<List<BudgetPeriod>>

    @Query("DELETE FROM budget_periods WHERE budgetId = :budgetId AND startDate >= :from")
    suspend fun deletePeriodsFrom(budgetId: Long, from: LocalDate)
}
