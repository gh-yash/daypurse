package bizappz.daypurse.data.repository

import bizappz.daypurse.data.db.CategoryDao
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class CategoryRepository(private val dao: CategoryDao, private val log: ActivityLog) {
    val visible: Flow<List<Category>> = dao.observeVisible()
    val all: Flow<List<Category>> = dao.observeAll()

    /**
     * Quick-pick categories: most used in the last [lookbackDays], topped up with the first
     * visible categories so new users still see [limit] chips.
     */
    fun quickPicks(today: LocalDate, limit: Int = 4, lookbackDays: Long = 60): Flow<List<Category>> =
        combine(dao.observeMostUsed(today.minusDays(lookbackDays), limit), visible) { used, visible ->
            (used + visible).distinctBy { it.id }.take(limit)
        }

    suspend fun get(id: Long) = dao.get(id)

    suspend fun add(name: String, iconKey: String, color: Int): Long =
        dao.insert(
            Category(name = name.trim(), iconKey = iconKey, color = color, sortOrder = dao.maxSortOrder() + 1),
        ).also { record(ActivityAction.ADDED, name.trim(), it) }

    suspend fun update(category: Category) {
        val before = dao.get(category.id)
        dao.update(category.copy(name = category.name.trim()))
        // Renames show the old name as detail.
        record(ActivityAction.EDITED, category.name.trim(), category.id, detail = before?.name?.takeIf { it != category.name.trim() })
    }

    enum class Removal { HIDDEN, DELETED }

    /**
     * Removes a category from the picker. Defaults and categories that already have spends are
     * only hidden (so history keeps its labels and they can be restored); unused custom
     * categories are deleted outright.
     */
    suspend fun remove(category: Category): Removal =
        if (category.isDefault || dao.expenseCount(category.id) > 0) {
            dao.setHidden(category.id, true)
            record(ActivityAction.HIDDEN, category.name, category.id)
            Removal.HIDDEN
        } else {
            dao.delete(category.id)
            record(ActivityAction.DELETED, category.name, category.id)
            Removal.DELETED
        }

    /** Undo for [remove]. */
    suspend fun undoRemove(category: Category, removal: Removal) {
        when (removal) {
            Removal.HIDDEN -> dao.setHidden(category.id, false)
            Removal.DELETED -> dao.insert(category)
        }
        record(ActivityAction.RESTORED, category.name, category.id)
    }

    /** True if another category (visible or hidden) already uses [name], ignoring case. */
    suspend fun isNameTaken(name: String, exceptId: Long? = null): Boolean =
        dao.countByName(name.trim(), exceptId ?: 0) > 0

    suspend fun unhide(id: Long) {
        dao.setHidden(id, false)
        record(ActivityAction.SHOWN, dao.get(id)?.name, id)
    }

    /** Persists the user's drag-and-drop order for visible categories (hidden ones go after). */
    suspend fun reorder(visibleIdsInOrder: List<Long>) {
        dao.reorder(visibleIdsInOrder)
        record(ActivityAction.REORDERED, null, null)
    }

    suspend fun restoreDefaults() {
        dao.unhideDefaults()
        record(ActivityAction.SHOWN, null, null, detail = DEFAULTS)
    }

    private suspend fun record(action: ActivityAction, name: String?, id: Long?, detail: String? = null) =
        log.record(ActivityKind.CATEGORY, action, subject = name, refId = id, detail = detail)

    companion object {
        /** Detail marker for "default categories restored". */
        const val DEFAULTS = "defaults"
    }
}
