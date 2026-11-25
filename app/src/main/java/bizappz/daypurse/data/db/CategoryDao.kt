package bizappz.daypurse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import bizappz.daypurse.data.model.Category
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isHidden = 0 ORDER BY sortOrder, name")
    fun observeVisible(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY isHidden, sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    /** Most-used visible categories since [since], most frequent first. */
    @Query(
        """
        SELECT c.* FROM categories c
        JOIN expenses e ON e.categoryId = c.id
        WHERE e.date >= :since AND c.isHidden = 0
        GROUP BY c.id
        ORDER BY COUNT(*) DESC, MAX(e.createdAt) DESC
        LIMIT :limit
        """,
    )
    fun observeMostUsed(since: LocalDate, limit: Int): Flow<List<Category>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :id")
    suspend fun expenseCount(id: Long): Int

    @Insert suspend fun insert(category: Category): Long
    @Insert suspend fun insertAll(categories: List<Category>)
    @Update suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE categories SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** Saves a new display order: position in [ids] becomes the sort order. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> setSortOrder(id, i) }
    }

    @Query("UPDATE categories SET isHidden = 0 WHERE isDefault = 1")
    suspend fun unhideDefaults()
}
