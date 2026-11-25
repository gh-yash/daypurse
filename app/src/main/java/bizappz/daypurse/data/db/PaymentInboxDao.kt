package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import bizappz.daypurse.data.model.DetectedPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentInboxDao {
    @Query("SELECT * FROM payment_inbox WHERE status = 'NEW' ORDER BY detectedAt DESC")
    fun observeNew(): Flow<List<DetectedPayment>>

    @Query("SELECT * FROM payment_inbox WHERE id = :id")
    suspend fun get(id: Long): DetectedPayment?

    /** Any payment of [amount] seen since [since] (another app may report the same one). */
    @Query("SELECT * FROM payment_inbox WHERE amount = :amount AND detectedAt >= :since ORDER BY detectedAt DESC LIMIT 1")
    suspend fun recentWithAmount(amount: Long, since: Long): DetectedPayment?

    @Insert suspend fun insert(item: DetectedPayment): Long
    @Update suspend fun update(item: DetectedPayment)

    /** Handled entries are only kept for a while, to recognise repeats. */
    @Query("DELETE FROM payment_inbox WHERE status != 'NEW' AND detectedAt < :before")
    suspend fun pruneHandled(before: Long)

    @Query("DELETE FROM payment_inbox")
    suspend fun clear()
}
