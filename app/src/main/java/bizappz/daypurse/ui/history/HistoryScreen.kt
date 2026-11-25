package bizappz.daypurse.ui.history

import androidx.compose.foundation.background
import bizappz.daypurse.ui.components.BudgetBadge
import androidx.compose.runtime.key
import androidx.compose.material3.HorizontalDivider
import bizappz.daypurse.ui.theme.AppTheme
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.AppSnackbarHost
import bizappz.daypurse.ui.format.currentLocale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.CategoryBadge
import bizappz.daypurse.ui.components.SpendRow
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.spend.SpendEditorSheet
import bizappz.daypurse.ui.spend.relativeDay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(onOpenActivity: () -> Unit, modifier: Modifier = Modifier) {
    val vm = appViewModel { HistoryViewModel(it.expenseRepository, it.categoryRepository, it.budgetEngine, it.todayProvider) }
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deletedMsg = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val locale = currentLocale()
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val today = LocalDate.now()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Month switcher + total
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Balances the activity button on the right so the month stays centred.
                Spacer(Modifier.width(48.dp))
                IconButton(onClick = vm::previousMonth) {
                    Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.history_prev_month))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.month.format(monthFormat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.history_total, money.format(state.monthTotal)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::nextMonth, enabled = state.canGoNext) {
                    Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.history_next_month))
                }
                IconButton(onClick = onOpenActivity) {
                    Icon(painterResource(R.drawable.ic_history), stringResource(R.string.activity_open_cd))
                }
            }

            // Budget filter (only with more than one budget)
            if (state.budgets.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.budgetFilter == null,
                        onClick = { vm.setBudgetFilter(null) },
                        label = { Text(stringResource(R.string.budget_all)) },
                    )
                    state.budgets.forEach { b ->
                        FilterChip(
                            selected = state.budgetFilter == b.id,
                            onClick = { vm.setBudgetFilter(if (state.budgetFilter == b.id) null else b.id) },
                            label = { Text(b.name) },
                            leadingIcon = { BudgetBadge(b, size = 20.dp) },
                        )
                    }
                }
            }

            // Category filter
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.categoryFilter == null,
                    onClick = { vm.setFilter(null) },
                    label = { Text(stringResource(R.string.history_all)) },
                )
                state.categories.forEach { c ->
                    FilterChip(
                        selected = state.categoryFilter == c.id,
                        onClick = { vm.setFilter(if (state.categoryFilter == c.id) null else c.id) },
                        label = { Text(c.name) },
                        leadingIcon = { CategoryBadge(c, size = 20.dp) },
                    )
                }
            }

            if (state.loaded && state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.groups, key = { it.date.toEpochDay() }) { group ->
                        Column(Modifier.animateItem().padding(horizontal = 16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                                Text(
                                    relativeDay(group.date, today),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    money.format(group.total),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AppCard {
                                group.items.forEachIndexed { i, item ->
                                    if (i > 0) {
                                        HorizontalDivider(
                                            Modifier.padding(start = 72.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                    key(item.expense.id) {
                                        SwipeToDeleteRow(
                                            item = item,
                                            // In "All budgets", label spends from extra budgets.
                                            budgetName = state.budgets.firstOrNull { it.id == item.expense.budgetId }
                                                ?.takeIf { !it.isPrimary && state.budgetFilter == null }?.name,
                                            onClick = { editingId = item.expense.id },
                                            onDelete = {
                                                vm.delete(item.expense)
                                                scope.launch {
                                                    snackbar.currentSnackbarData?.dismiss()
                                                    val result = snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short)
                                                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(item.expense)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val editing = state.groups.firstNotNullOfOrNull { g -> g.items.firstOrNull { it.expense.id == id } }?.expense
        if (editing != null) {
            SpendEditorSheet(editing = editing, onDismiss = { editingId = null })
        } else {
            editingId = null
        }
    }
}

@Composable
private fun SwipeToDeleteRow(item: ExpenseWithCategory, budgetName: String?, onClick: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        Box(Modifier.background(AppTheme.colors.card)) {
            SpendRow(
                item,
                onClick = onClick,
                supporting = listOfNotNull(budgetName, item.expense.note ?: item.expense.payee).joinToString(" · ").ifEmpty { null },
            )
        }
    }
}
