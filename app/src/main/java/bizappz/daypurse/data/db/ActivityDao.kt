package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import bizappz.daypurse.data.model.ActivityEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert suspend fun insert(entry: ActivityEntry): Long

    /**
     * Newest first. [kinds] empty = every kind (Room can't bind an empty list, so the caller
     * passes [allKinds] = 1 instead). [query] matches subject or detail.
     */
    @Query(
        """
        SELECT * FROM activity_log
        WHERE at >= :since
          AND (:allKinds = 1 OR kind IN (:kinds))
          AND (:query = '' OR subject LIKE '%' || :query || '%' OR detail LIKE '%' || :query || '%')
        ORDER BY at DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observe(since: Long, kinds: List<String>, allKinds: Int, query: String, limit: Int): Flow<List<ActivityEntry>>

    @Query("SELECT * FROM activity_log WHERE id = :id")
    suspend fun get(id: Long): ActivityEntry?

    @Query("DELETE FROM activity_log WHERE at < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM activity_log")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM activity_log")
    fun observeCount(): Flow<Int>
}
