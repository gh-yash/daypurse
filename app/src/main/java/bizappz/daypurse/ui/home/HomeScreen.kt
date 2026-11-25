package bizappz.daypurse.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.R
import bizappz.daypurse.data.model.InboxStatus
import bizappz.daypurse.surfaces.LaunchAction
import bizappz.daypurse.ui.components.AppSnackbarHost
import bizappz.daypurse.ui.spend.SpendPrefill
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.launch
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.ExpenseWithCategory
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.domain.budget.budgetOrNull
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.budget.BudgetHero
import bizappz.daypurse.ui.budget.BudgetMiniCard
import bizappz.daypurse.ui.budget.PeriodCard
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.components.SpendRow
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.format.currentLocale
import bizappz.daypurse.ui.navigation.AppActions
import bizappz.daypurse.ui.spend.SpendEditorSheet
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun HomeScreen(actions: AppActions, modifier: Modifier = Modifier) {
    val vm = appViewModel { HomeViewModel(it) }
    val container = (LocalContext.current.applicationContext as DayPurseApplication).container
    val state by vm.state.collectAsStateWithLifecycle()
    val chips by vm.quickChips.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val money = LocalMoneyFormatter.current
    val resources = LocalResources.current

    fun openEditor(prefill: SpendPrefill?) {
        editingId = null
        vm.prefill = prefill
        editorOpen = true
    }

    /** Shows "Added ₹20 · Chai" with Undo, or explains why nothing was added. */
    fun quickLog(amount: Long, categoryId: Long?, note: String?, label: String?) = scope.launch {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val saved = vm.quickLog(amount, categoryId, note)
        if (saved == null) {
            snackbar.showSnackbar(resources.getString(R.string.quick_no_budget))
            return@launch
        }
        // Name the budget when it isn't the main one (e.g. a trip that's running), so it's no surprise.
        val budget = container.budgetEngine.get(saved.budgetId)?.takeIf { !it.isPrimary }?.name
        val text = resources.getString(R.string.quick_added, money.format(amount), label ?: resources.getString(R.string.category_none)) +
            (budget?.let { " · $it" } ?: "")
        val result = snackbar.showSnackbar(text, resources.getString(R.string.action_undo), duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undoAdd(saved)
    }

    // Opened from a shortcut, the widget, the tile or a notification.
    val launch by container.launchActions.collectAsStateWithLifecycle()
    LaunchedEffect(launch, state is HomeUiState.Ready) {
        val action = launch ?: return@LaunchedEffect
        if (state !is HomeUiState.Ready) return@LaunchedEffect
        container.launchActions.value = null
        when (action) {
            LaunchAction.AddSpend -> openEditor(null)
            is LaunchAction.QuickSpend -> {
                val label = action.note ?: action.categoryId?.let { container.categoryRepository.get(it)?.name }
                quickLog(action.amount, action.categoryId, action.note, label)
            }
            is LaunchAction.ReviewPayment -> vm.inboxRow(action.inboxId)
                ?.takeIf { it.payment.status == InboxStatus.NEW }
                ?.let { openEditor(vm.prefillFor(it)) }
        }
    }

    val ready = state as? HomeUiState.Ready
    val canAdd = ready != null && (ready.primary is BudgetState.Active || ready.others.any { it is BudgetState.Active })
    val primaryBudget = ready?.primary?.budgetOrNull

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        floatingActionButton = {
            if (canAdd) {
                ExtendedFloatingActionButton(
                    onClick = { openEditor(null) },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text(stringResource(R.string.spend_add_title)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    ) { padding ->
        if (ready == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(ready.today, onEdit = { primaryBudget?.let { actions.editBudget(it.id) } }) }
            when (val p = ready.primary) {
                is BudgetState.Active -> {
                    item {
                        BudgetHero(
                            p,
                            onResume = { vm.resume(p.budget.id) },
                            modifier = Modifier.padding(horizontal = 16.dp).clickable { actions.openBudget(p.budget.id) },
                        )
                    }
                    if (inbox.isNotEmpty()) {
                        item(key = "inbox") {
                            PaymentInboxCard(
                                rows = inbox,
                                onOpen = { openEditor(vm.prefillFor(it)) },
                                onLog = { row ->
                                    scope.launch {
                                        val saved = vm.logPayment(row)
                                        if (saved == null) { openEditor(vm.prefillFor(row)); return@launch }
                                        val text = resources.getString(R.string.inbox_logged, money.format(saved.amount), row.suggested?.name ?: "")
                                        if (snackbar.showSnackbar(text, resources.getString(R.string.action_undo), duration = SnackbarDuration.Short) ==
                                            SnackbarResult.ActionPerformed
                                        ) vm.undoPaymentLog(row, saved)
                                    }
                                },
                                onIgnore = { row ->
                                    vm.ignore(row)
                                    scope.launch {
                                        val text = resources.getString(R.string.inbox_ignored, money.format(row.payment.amount))
                                        if (snackbar.showSnackbar(text, resources.getString(R.string.action_undo), duration = SnackbarDuration.Short) ==
                                            SnackbarResult.ActionPerformed
                                        ) vm.reopen(row)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    if (chips.isNotEmpty()) {
                        item(key = "quick") {
                            QuickSpendRow(
                                chips = chips,
                                onTap = { quickLog(it.spend.amount, it.spend.categoryId, it.spend.note, it.label) },
                                onEdit = { openEditor(SpendPrefill(it.spend.amount, it.spend.categoryId, it.spend.note)) },
                                onHide = { chip ->
                                    vm.hideChip(chip)
                                    scope.launch {
                                        if (snackbar.showSnackbar(
                                                resources.getString(R.string.quick_hidden),
                                                resources.getString(R.string.action_undo),
                                                duration = SnackbarDuration.Short,
                                            ) == SnackbarResult.ActionPerformed
                                        ) vm.unhideChip(chip)
                                    }
                                },
                            )
                        }
                    }
                    if (p.budget.dailySplit || p.paused) item { PeriodCard(p.budget, p.day, Modifier.padding(horizontal = 16.dp)) }
                }

                is BudgetState.NotStarted -> item {
                    MessageCard(
                        stringResource(R.string.home_not_started, DateFormats.long(p.budget.startDate)),
                        stringResource(R.string.home_edit_budget),
                    ) { actions.editBudget(p.budget.id) }
                }

                is BudgetState.Ended -> item {
                    MessageCard(stringResource(R.string.home_ended), stringResource(R.string.home_start_new)) {
                        actions.editBudget(p.budget.id)
                    }
                }

                BudgetState.NoBudget -> Unit
            }

            item { OtherBudgets(ready.others, actions) }

            if (ready.primary is BudgetState.Active) {
                item { TodaySpends(ready.todaySpends) { vm.prefill = null; editingId = it; editorOpen = true } }
            }
        }
    }

    if (editorOpen) {
        val editing: Expense? = editingId?.let { id -> ready?.todaySpends?.firstOrNull { it.expense.id == id }?.expense }
        SpendEditorSheet(
            editing = editing,
            prefill = vm.prefill.takeIf { editing == null },
            onDismiss = { editorOpen = false; editingId = null; vm.prefill = null },
        )
    }
}

@Composable
private fun Header(today: LocalDate, onEdit: () -> Unit) {
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                today.dayOfWeek.getDisplayName(TextStyle.FULL, currentLocale()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(DateFormats.dayMonth(today), style = MaterialTheme.typography.headlineMedium)
        }
        FilledTonalIconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.home_edit_budget))
        }
    }
}

@Composable
private fun OtherBudgets(others: List<BudgetState>, actions: AppActions) {
    Column {
        SectionTitle(stringResource(R.string.budget_other), Modifier.padding(start = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(others, key = { it.budgetOrNull?.id ?: 0L }) { state ->
                BudgetMiniCard(state, onClick = { state.budgetOrNull?.let { actions.openBudget(it.id) } })
            }
            item(key = "add") {
                AppCard(Modifier.width(if (others.isEmpty()) 260.dp else 140.dp).clickable(onClick = actions.newBudget)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp).height(96.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(R.drawable.ic_add), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (others.isEmpty()) R.string.budget_add_card else R.string.budget_new_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySpends(spends: List<ExpenseWithCategory>, onEdit: (Long) -> Unit) {
    val money = LocalMoneyFormatter.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle(stringResource(R.string.day_today), Modifier.weight(1f))
            if (spends.isNotEmpty()) {
                Text(
                    money.format(spends.sumOf { it.expense.amount }),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
            }
        }
        AppCard {
            if (spends.isEmpty()) {
                Text(
                    stringResource(R.string.home_empty_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            spends.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SpendRow(item, onClick = { onEdit(item.expense.id) })
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, action: String, onAction: () -> Unit) {
    AppCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAction) { Text(action) }
        }
    }
}
