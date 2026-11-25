package bizappz.daypurse.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.domain.money.AmountInput
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.AppSnackbarHost
import bizappz.daypurse.ui.components.BudgetBadge
import bizappz.daypurse.ui.components.CategoryBadge
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.spend.SpendEditorSheet
import bizappz.daypurse.ui.spend.repeatLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Every repeating spend: what, how often, when next. Tap one to change, pause, skip or stop it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(onBack: () -> Unit) {
    val vm = appViewModel { RecurringViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.shortcut_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        if (s.rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(R.drawable.ic_event_repeat), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.recurring_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.recurring_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(s.rows, key = { it.item.id }) { row ->
                val item = row.item
                AppCard(Modifier.clickable { editingId = item.id }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(row.category)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(
                                item.note ?: row.category?.name ?: stringResource(R.string.category_none),
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (item.paused) stringResource(R.string.recurring_paused)
                                else stringResource(repeatLabel(item.repeat)) + " · " +
                                    stringResource(R.string.recurring_next, DateFormats.short(item.nextDate)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            row.budget?.takeIf { !it.isPrimary }?.let {
                                Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(money.format(item.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    val editing = state?.rows?.firstOrNull { it.item.id == editingId }
    if (editing != null) {
        RecurringEditSheet(
            row = editing,
            state = state!!,
            onDismiss = { editingId = null },
            onSave = { vm.save(it); editingId = null },
            onSkip = {
                vm.skipNext(editing.item)
                editingId = null
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.recurring_skipped, DateFormats.short(editing.item.nextDate))) }
            },
            onPause = { vm.setPaused(editing.item, !editing.item.paused); editingId = null },
            onDelete = {
                val item = editing.item
                vm.delete(item)
                editingId = null
                scope.launch {
                    if (snackbar.showSnackbar(
                            resources.getString(R.string.recurring_deleted),
                            resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Long,
                        ) == SnackbarResult.ActionPerformed
                    ) vm.restore(item)
                }
            },
        )
    }

    if (adding) SpendEditorSheet(editing = null, onDismiss = { adding = false }, initialRepeat = Repeat.MONTHLY)
}

private val AMOUNT_TEXT = Regex("""^\d{0,9}(\.\d{0,2})?$""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditSheet(
    row: RecurringRow,
    state: RecurringUiState,
    onDismiss: () -> Unit,
    onSave: (bizappz.daypurse.data.model.RecurringSpend) -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = row.item
    val money = LocalMoneyFormatter.current
    var amountText by rememberSaveable { mutableStateOf(AmountInput.fromMinor(item.amount)) }
    var categoryId by rememberSaveable { mutableStateOf(item.categoryId) }
    var note by rememberSaveable { mutableStateOf(item.note ?: "") }
    var repeat by rememberSaveable { mutableStateOf(item.repeat) }
    var nextDay by rememberSaveable { mutableLongStateOf(item.nextDate.toEpochDay()) }
    var budgetId by rememberSaveable { mutableStateOf(item.budgetId) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val amount = AmountInput.toMinor(amountText)
    val next = LocalDate.ofEpochDay(nextDay)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recurring_edit_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.recurring_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> if (AMOUNT_TEXT.matches(v)) amountText = v },
                label = { Text(stringResource(R.string.recurring_amount)) },
                prefix = money.symbol?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Repeat.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = repeat == r,
                        onClick = { repeat = r },
                        shape = SegmentedButtonDefaults.itemShape(i, Repeat.entries.size),
                    ) { Text(stringResource(repeatLabel(r)), maxLines = 1) }
                }
            }
            AssistChip(
                onClick = { pickDate = true },
                label = { Text(stringResource(R.string.recurring_next, DateFormats.long(next))) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_calendar_today), null, Modifier.size(18.dp)) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    FilterChip(
                        selected = c.id == categoryId,
                        onClick = { categoryId = if (categoryId == c.id) null else c.id },
                        label = { Text(c.name, maxLines = 1) },
                        leadingIcon = { CategoryBadge(c, size = 24.dp) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
            if (state.budgets.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.budgets.forEach { b ->
                        val selected = if (b.isPrimary) budgetId == null || budgetId == b.id else budgetId == b.id
                        FilterChip(
                            selected = selected,
                            onClick = { budgetId = if (b.isPrimary) null else b.id },
                            label = { Text(if (b.isPrimary) stringResource(R.string.recurring_budget_main) else b.name) },
                            leadingIcon = { BudgetBadge(b, size = 24.dp) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                placeholder = { Text(stringResource(R.string.spend_note_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip, enabled = !item.paused, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_skip_next), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.recurring_skip), maxLines = 1)
                }
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(if (item.paused) R.drawable.ic_play_arrow else R.drawable.ic_pause), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (item.paused) R.string.recurring_resume else R.string.recurring_pause), maxLines = 1)
                }
            }
            Button(
                onClick = {
                    onSave(item.copy(amount = amount, categoryId = categoryId, note = note, repeat = repeat,
                        nextDate = next, anchorDay = if (repeat == item.repeat && next == item.nextDate) item.anchorDay else next.dayOfMonth,
                        budgetId = budgetId))
                },
                enabled = amount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    if (pickDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = next.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { nextDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(dateState) }
    }
}
