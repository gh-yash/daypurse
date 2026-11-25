package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bizappz.daypurse.data.model.RecurringSpend
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_spends ORDER BY paused, nextDate, id")
    fun observeAll(): Flow<List<RecurringSpend>>

    @Query("SELECT * FROM recurring_spends WHERE id = :id")
    suspend fun get(id: Long): RecurringSpend?

    /** Running ones that are due on or before [today]. */
    @Query("SELECT * FROM recurring_spends WHERE paused = 0 AND nextDate <= :today ORDER BY nextDate, id")
    suspend fun due(today: LocalDate): List<RecurringSpend>

    @Insert suspend fun insert(item: RecurringSpend): Long
    @Update suspend fun update(item: RecurringSpend)

    @Query("DELETE FROM recurring_spends WHERE id = :id")
    suspend fun delete(id: Long)
}
