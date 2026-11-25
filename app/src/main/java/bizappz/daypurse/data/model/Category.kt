package bizappz.daypurse.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spend category. Default categories are never deleted, only hidden, so they can be restored.
 * [iconKey] maps to a drawable via `CategoryIcons`; [color] is an ARGB int.
 */
@Entity(tableName = "categories", indices = [Index("isHidden")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val color: Int,
    val isDefault: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)
