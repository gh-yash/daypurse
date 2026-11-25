package bizappz.daypurse.ui.category

import androidx.compose.foundation.clickable
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import bizappz.daypurse.ui.theme.AppTheme
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import bizappz.daypurse.ui.components.groupedRow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarDuration
import bizappz.daypurse.ui.components.AppSnackbarHost
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.CategoryBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it.categoryRepository) }
    val all by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }
    val hiddenMsg = stringResource(R.string.category_hidden_msg)
    val deletedMsg = stringResource(R.string.category_deleted_msg)
    val undo = stringResource(R.string.action_undo)

    val categories = all ?: return
    val visible = categories.filterNot { it.isHidden }
    val hidden = categories.filter { it.isHidden }

    // Local copy so a drag moves rows instantly; re-synced from the database when not dragging.
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var ordered by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) { if (!dragging) ordered = visible }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = ordered.indexOfFirst { it.id == from.key }
        val toIdx = ordered.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            ordered = ordered.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    fun remove(c: Category) = vm.remove(c) { removal ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val msg = if (removal == CategoryRepository.Removal.HIDDEN) hiddenMsg else deletedMsg
            if (snackbar.showSnackbar(msg.format(c.name), undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoRemove(c, removal)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_restore_defaults)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_settings_backup_restore), null) },
                            onClick = { vm.restoreDefaults(); menuOpen = false },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingId = null; editorOpen = true },
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.categories_add)) },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            item(key = "reorder_hint") {
                Text(
                    stringResource(R.string.categories_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(ordered, key = { _, c -> c.id }) { i, c ->
                ReorderableItem(reorderState, key = c.id) { isDragging ->
                    val lifted = Modifier
                        .padding(horizontal = 16.dp)
                        .shadow(8.dp, MaterialTheme.shapes.medium)
                        .background(AppTheme.colors.card, MaterialTheme.shapes.medium)
                    ListItem(
                        leadingContent = { CategoryBadge(c) },
                        headlineContent = { Text(c.name) },
                        supportingContent = if (c.isDefault) ({ Text(stringResource(R.string.categories_default)) }) else null,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { remove(c) }) {
                                    Icon(
                                        painterResource(if (c.isDefault) R.drawable.ic_visibility_off else R.drawable.ic_delete),
                                        stringResource(if (c.isDefault) R.string.categories_hide else R.string.action_delete),
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ic_drag_indicator),
                                    stringResource(R.string.categories_drag),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = {
                                                dragging = true
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                dragging = false
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                vm.reorder(ordered.map { it.id })
                                            },
                                        )
                                        .size(40.dp)
                                        .padding(8.dp),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = (if (isDragging) lifted else Modifier.groupedRow(i, ordered.size))
                            .clickable { editingId = c.id; editorOpen = true },
                    )
                }
            }
            if (hidden.isNotEmpty()) {
                // Collapsed by default: hiding a category just removes it from the list above,
                // so the list never jumps to the bottom.
                item(key = "hidden_header") {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.categories_hidden_count, hidden.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                painterResource(if (hiddenExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().padding(top = 16.dp).clickable { hiddenExpanded = !hiddenExpanded },
                    )
                }
                if (hiddenExpanded) itemsIndexed(hidden, key = { _, c -> c.id }) { i, c ->
                    ListItem(
                        leadingContent = { CategoryBadge(c) },
                        headlineContent = { Text(c.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = { TextButton(onClick = { vm.unhide(c) }) { Text(stringResource(R.string.categories_show)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().groupedRow(i, hidden.size),
                    )
                }
            }
        }
    }

    if (editorOpen) {
        val existing = editingId?.let { id -> categories.firstOrNull { it.id == id } }
        CategoryEditorSheet(
            existing = existing,
            onSave = { name, icon, color, onResult -> vm.save(existing, name, icon, color, onResult) },
            onDismiss = { editorOpen = false; editingId = null },
        )
    }
}
