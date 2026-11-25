package bizappz.daypurse.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: CategoryRepository) : ViewModel() {
    /** Visible first, then hidden (ordered by the DAO). */
    val categories: StateFlow<List<Category>?> =
        repo.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Adds or updates a category. Calls [onResult] with false when the name is empty or taken.
     */
    fun save(existing: Category?, name: String, iconKey: String, color: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isEmpty() || repo.isNameTaken(clean, existing?.id)) {
                onResult(false)
                return@launch
            }
            if (existing == null) {
                repo.add(clean, iconKey, color)
            } else {
                repo.update(existing.copy(name = clean, iconKey = iconKey, color = color))
            }
            onResult(true)
        }
    }

    fun remove(category: Category, onRemoved: (CategoryRepository.Removal) -> Unit) {
        viewModelScope.launch { onRemoved(repo.remove(category)) }
    }

    fun undoRemove(category: Category, removal: CategoryRepository.Removal) {
        viewModelScope.launch { repo.undoRemove(category, removal) }
    }

    fun unhide(category: Category) = viewModelScope.launch { repo.unhide(category.id) }
    fun restoreDefaults() = viewModelScope.launch { repo.restoreDefaults() }
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorder(ids) }
}
