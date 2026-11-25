package bizappz.daypurse.ui.spend

import androidx.compose.animation.AnimatedVisibility
import bizappz.daypurse.ui.components.BudgetBadge
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.RecurringSpend
import bizappz.daypurse.data.model.Repeat
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import bizappz.daypurse.domain.money.AmountInput
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.CategoryBadge
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Bottom sheet to add a spend, or edit/delete [editing]. Suggests the user's usual amounts and
 * most-used categories so a typical entry is two taps. The note is tucked behind a chip so the
 * normal flow never brings up the system keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpendEditorSheet(
    editing: Expense?,
    onDismiss: () -> Unit,
    preselectedBudgetId: Long? = null,
    prefill: SpendPrefill? = null,
    initialRepeat: Repeat? = null,
) {
    val vm = appViewModel {
        SpendEditorViewModel(it.categoryRepository, it.expenseRepository, it.budgetEngine, it.todayProvider, it.recurringSpends, it.paymentInbox)
    }
    val money = LocalMoneyFormatter.current
    val today by vm.today.collectAsStateWithLifecycle()
    val categories by vm.allCategories.collectAsStateWithLifecycle()
    val quickPicks by vm.quickPicks.collectAsStateWithLifecycle()
    val budgetChoices by vm.budgetChoices.collectAsStateWithLifecycle()
    var budgetId by rememberSaveable { mutableStateOf(editing?.budgetId ?: preselectedBudgetId) }
    LaunchedEffect(budgetChoices) {
        if (budgetId == null) budgetChoices?.let { budgetId = vm.defaultBudget(it)?.id }
    }

    var amountText by rememberSaveable {
        mutableStateOf((editing?.amount ?: prefill?.amount)?.let { AmountInput.fromMinor(it) } ?: "")
    }
    var categoryId by rememberSaveable { mutableStateOf(editing?.categoryId ?: prefill?.categoryId) }
    var dateEpochDay by rememberSaveable { mutableLongStateOf((editing?.date ?: prefill?.date ?: today).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: prefill?.note ?: "") }
    var repeat by rememberSaveable { mutableStateOf(initialRepeat) }
    var repeatMenu by remember { mutableStateOf(false) }
    // Repeating only makes sense for a new, typed-in spend (not one being edited or logged from a payment).
    val canRepeat = editing == null && prefill?.inboxId == null
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val amount = AmountInput.toMinor(amountText)

    // Freeze the first non-empty quick-pick list for the life of the sheet.
    var frozenPicks by remember { mutableStateOf<List<Category>?>(null) }
    if (frozenPicks == null && quickPicks.isNotEmpty()) frozenPicks = quickPicks
    val stablePicks = frozenPicks ?: quickPicks

    // Leave note mode whenever the keyboard goes away (back gesture, IME hide), so the keypad
    // always comes back.
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) editingNote = false
        imeWasVisible = imeVisible
    }

    val suggestions by remember(categoryId) { vm.amountSuggestions(categoryId) }
        .collectAsStateWithLifecycle(emptyList())

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() = scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: title, date, delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (editing == null) R.string.spend_add_title else R.string.spend_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    // Logging a detected payment: say who it went to.
                    (prefill?.payee ?: editing?.payee)?.let {
                        Text(stringResource(R.string.spend_paid_to, it), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(relativeDay(date, today)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_calendar_today), null, Modifier.size(18.dp)) },
                    shape = MaterialTheme.shapes.small,
                )
                if (editing != null) {
                    IconButton(onClick = { vm.delete(editing); close() }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Which budget (only when there is a choice)
            val choices = budgetChoices.orEmpty()
            // Show the choice when there is one, or when the spend won't go to the main budget.
            if (choices.size > 1 || choices.singleOrNull()?.isPrimary == false) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { b ->
                        FilterChip(
                            selected = b.id == budgetId,
                            onClick = { budgetId = b.id },
                            label = { Text(b.name, maxLines = 1) },
                            leadingIcon = { BudgetBadge(b, size = 24.dp) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }

            // Amount display
            Text(
                text = if (amountText.isEmpty()) money.format(0) else (money.symbol ?: "") + amountText,
                style = MaterialTheme.typography.displayLarge,
                color = if (amountText.isEmpty()) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = editingNote, interactionSource = null, indication = null) { editingNote = false }
                    .padding(vertical = 4.dp),
            )

            // Usual amounts
            if (suggestions.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.spend_usual), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    suggestions.forEach { value ->
                        SuggestionChip(
                            onClick = { amountText = AmountInput.fromMinor(value) },
                            label = { Text(money.format(value), style = MaterialTheme.typography.labelLarge) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }

            // Categories: quick picks in a fixed order (captured when the sheet opens, so chips
            // never jump around), plus the current selection at the end if it isn't one of them.
            val selected = categories.firstOrNull { it.id == categoryId }
            val chips = (stablePicks + listOfNotNull(selected)).distinctBy { it.id }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (showAllCategories) categories else chips).forEach { c ->
                    CategoryChip(c, selected = c.id == categoryId) { categoryId = if (categoryId == c.id) null else c.id }
                }
                FilterChip(
                    selected = false,
                    onClick = { showAllCategories = !showAllCategories },
                    label = { Text(stringResource(if (showAllCategories) R.string.spend_less else R.string.spend_more)) },
                    trailingIcon = {
                        Icon(
                            painterResource(if (showAllCategories) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                            null,
                            Modifier.size(18.dp),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                )
            }

            // Note: a chip until tapped; while typing, the keypad steps aside for the keyboard.
            // Next to it, whether the spend repeats (rent, subscriptions).
            if (editingNote) {
                val focus = remember { FocusRequester() }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    placeholder = { Text(stringResource(R.string.spend_note_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { editingNote = false }),
                    trailingIcon = {
                        IconButton(onClick = { editingNote = false }) {
                            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.action_done))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) editingNote = false },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { editingNote = true },
                        label = {
                            Text(note.ifBlank { stringResource(R.string.spend_add_note) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_edit_note), null, Modifier.size(18.dp)) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (canRepeat) {
                        Box {
                            FilterChip(
                                selected = repeat != null,
                                onClick = { repeatMenu = true },
                                label = { Text(stringResource(if (repeat == null) R.string.repeat_chip else repeatLabel(repeat)), maxLines = 1) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_repeat), null, Modifier.size(18.dp)) },
                                shape = MaterialTheme.shapes.small,
                            )
                            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                                (listOf<Repeat?>(null) + Repeat.entries).forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(repeatLabel(option))) },
                                        onClick = { repeat = option; repeatMenu = false },
                                        trailingIcon = if (option == repeat) {
                                            { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }
                if (repeat != null) {
                    Text(
                        stringResource(R.string.repeat_hint, DateFormats.short(nextOccurrence(repeat!!, date))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = !editingNote,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                NumberPad(
                    onKey = { amountText = AmountInput.append(amountText, it) },
                    onBackspace = { amountText = AmountInput.backspace(amountText) },
                    onClear = { amountText = "" },
                )
            }

            Button(
                onClick = { budgetId?.let { vm.save(editing, amount, categoryId, date, note, it, repeat, prefill) }; close() },
                enabled = amount > 0 && budgetId != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (editing == null) R.string.spend_save else R.string.action_save))
            }
        }
    }

    if (showDatePicker) {
        val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                // Spends can't be in the future.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { CategoryBadge(category, size = 24.dp) },
        shape = MaterialTheme.shapes.small,
    )
}

/** Label for a repeat choice (null = doesn't repeat). */
fun repeatLabel(repeat: Repeat?): Int = when (repeat) {
    null -> R.string.repeat_none
    Repeat.WEEKLY -> R.string.repeat_weekly
    Repeat.MONTHLY -> R.string.repeat_monthly
    Repeat.YEARLY -> R.string.repeat_yearly
}

private fun nextOccurrence(repeat: Repeat, from: LocalDate): LocalDate =
    RecurringSpend(amount = 1, categoryId = null, repeat = repeat, anchorDay = from.dayOfMonth, nextDate = from).after(from)

@Composable
fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    else -> DateFormats.short(date)
}
