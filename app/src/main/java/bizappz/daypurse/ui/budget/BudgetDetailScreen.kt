package bizappz.daypurse.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.BudgetBadge
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.components.SpendRow
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.spend.SpendEditorSheet
import bizappz.daypurse.ui.spend.relativeDay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class DetailDialog { NONE, PAUSE, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(budgetId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel(key = "budget-$budgetId") {
        BudgetDetailViewModel(it.budgetEngine, it.expenseRepository, it.todayProvider, budgetId)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(DetailDialog.NONE) }
    var spendCount by remember { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val money = LocalMoneyFormatter.current

    val data = ui ?: return
    val budget = data.state.budget ?: run { onBack(); return }
    val paused = budget.hasPendingPause(data.today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BudgetBadge(budget, size = 32.dp)
                        Text(budget.name, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { onEdit(budget.id) }) {
                        Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.home_edit_budget))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (paused) R.string.budget_resume else R.string.budget_pause)) },
                            leadingIcon = { Icon(painterResource(if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause), null) },
                            onClick = {
                                menuOpen = false
                                if (paused) vm.resume() else dialog = DetailDialog.PAUSE
                            },
                        )
                        if (!budget.isPrimary) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { spendCount = vm.spendCount(); dialog = DetailDialog.DELETE }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (data.state is BudgetState.Active && !(data.state as BudgetState.Active).paused) {
                ExtendedFloatingActionButton(
                    onClick = { editingId = null; editorOpen = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text(stringResource(R.string.spend_add_title)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = data.state) {
                is BudgetState.Active -> {
                    item { BudgetHero(s, onResume = vm::resume) }
                    // Without a daily limit the hero already shows the totals.
                    if (s.budget.dailySplit || s.paused) item { PeriodCard(s.budget, s.day) }
                }
                is BudgetState.NotStarted, is BudgetState.Ended -> item {
                    AppCard {
                        Text(
                            budgetStatus(s),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                BudgetState.NoBudget -> Unit
            }
            if (data.state is BudgetState.Active || data.state is BudgetState.Ended) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionTitle(stringResource(R.string.budget_spends_this_period), Modifier.weight(1f))
                        if (data.spends.isNotEmpty()) {
                            Text(
                                money.format(data.spends.sumOf { it.expense.amount }),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
                item {
                    AppCard {
                        if (data.spends.isEmpty()) {
                            Text(
                                stringResource(R.string.budget_no_spends),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                        data.spends.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            SpendRow(
                                item,
                                onClick = { editingId = item.expense.id; editorOpen = true },
                                supporting = listOfNotNull(relativeDay(item.expense.date, data.today), item.expense.note).joinToString(" · "),
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        val editing = editingId?.let { id -> data.spends.firstOrNull { it.expense.id == id }?.expense }
        SpendEditorSheet(editing = editing, preselectedBudgetId = budget.id, onDismiss = { editorOpen = false; editingId = null })
    }

    when (dialog) {
        DetailDialog.PAUSE -> PauseDialog(
            budgetName = budget.name,
            today = data.today,
            onPause = { until -> vm.pause(until); dialog = DetailDialog.NONE },
            onDismiss = { dialog = DetailDialog.NONE },
        )
        DetailDialog.DELETE -> DeleteBudgetDialog(
            budgetName = budget.name,
            primaryName = data.primaryName,
            spendCount = spendCount,
            onDelete = { move -> dialog = DetailDialog.NONE; vm.delete(move, onBack) },
            onDismiss = { dialog = DetailDialog.NONE },
        )
        DetailDialog.NONE -> Unit
    }
}

/** "Until I resume" or "Until a date" (date picked from today onwards). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseDialog(budgetName: String, today: LocalDate, onPause: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var untilDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val chosen = untilDate?.let(LocalDate::ofEpochDay)

    if (pickingDate) {
        val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (chosen ?: today.plusDays(6)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { untilDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_pause_title, budgetName)) },
        text = {
            Column {
                Text(stringResource(R.string.budget_paused_hint), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
                ChoiceRow(stringResource(R.string.budget_pause_until_resume), selected = untilDate == null) { untilDate = null }
                ChoiceRow(
                    chosen?.let { stringResource(R.string.budget_pause_pick_date, DateFormats.dayMonth(it)) }
                        ?: stringResource(R.string.budget_pause_until_date),
                    selected = untilDate != null,
                ) { pickingDate = true }
            }
        },
        confirmButton = { TextButton(onClick = { onPause(chosen) }) { Text(stringResource(R.string.budget_pause)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Asks what to do with the budget's spends: move them to the primary budget or delete them. */
@Composable
fun DeleteBudgetDialog(
    budgetName: String,
    primaryName: String,
    spendCount: Int,
    onDelete: (moveSpends: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_delete_title, budgetName)) },
        text = {
            Text(
                if (spendCount > 0) pluralStringResource(R.plurals.budget_delete_has_spends, spendCount, spendCount, primaryName)
                else stringResource(R.string.budget_delete_empty),
            )
        },
        confirmButton = {
            if (spendCount > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onDelete(true) }) { Text(stringResource(R.string.budget_delete_move, primaryName)) }
                    TextButton(onClick = { onDelete(false) }) {
                        Text(stringResource(R.string.budget_delete_all), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            } else {
                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onDelete(false) }) {
                        Text(stringResource(R.string.budget_delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}
