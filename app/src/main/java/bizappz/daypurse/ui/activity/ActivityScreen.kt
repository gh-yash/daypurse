package bizappz.daypurse.ui.activity

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityAction.*
import bizappz.daypurse.data.model.ActivityEntry
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.data.model.SettingKey
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.AppSnackbarHost
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.format.MoneyFormatter
import bizappz.daypurse.ui.spend.repeatLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything the user added, changed or deleted, newest first, grouped by day. Filter by type
 * and time, search, bring back a deleted spend, and choose how long history is kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val vm = appViewModel { ActivityViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val retention by vm.retentionDays.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rangeMenu by remember { mutableStateOf(false) }
    var retentionDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) vm.setQuery("") }) {
                        Icon(painterResource(if (searching) R.drawable.ic_close else R.drawable.ic_search), stringResource(R.string.activity_search))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.activity_keep))
                                        Text(retentionLabel(retention), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), null) },
                                onClick = { menu = false; retentionDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.activity_clear)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete_forever), null) },
                                onClick = { menu = false; clearDialog = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text(stringResource(R.string.activity_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Time range, then what kind of change.
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    AssistChip(
                        onClick = { rangeMenu = true },
                        label = { Text(stringResource(filter.range.label())) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_calendar_today), null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                        ActivityRange.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(stringResource(r.label())) }, onClick = { vm.setRange(r); rangeMenu = false })
                        }
                    }
                }
                FilterChip(selected = filter.kinds.isEmpty(), onClick = vm::clearKinds, label = { Text(stringResource(R.string.activity_filter_all)) })
                val paymentSpotting = booleanResource(R.bool.payment_spotting)
                ActivityKind.entries.filter { paymentSpotting || it != ActivityKind.PAYMENT }.forEach { kind ->
                    FilterChip(
                        selected = kind in filter.kinds,
                        onClick = { vm.toggleKind(kind) },
                        label = { Text(stringResource(kind.label())) },
                        leadingIcon = { Icon(painterResource(kind.icon()), null, Modifier.size(18.dp)) },
                    )
                }
            }

            val list = rows ?: return@Column
            if (list.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_history), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(if (filter.isFiltered) R.string.activity_empty_filtered else R.string.activity_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!filter.isFiltered) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.activity_empty_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                return@Column
            }

            val zone = ZoneId.systemDefault()
            val byDay = list.groupBy { Instant.ofEpochMilli(it.entry.at).atZone(zone).toLocalDate() }
            val today = LocalDate.now()
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                byDay.forEach { (day, entries) ->
                    item(key = "d$day") {
                        SectionTitle(
                            when (day) {
                                today -> stringResource(R.string.day_today)
                                today.minusDays(1) -> stringResource(R.string.day_yesterday)
                                else -> DateFormats.long(day)
                            },
                            Modifier.padding(start = 4.dp, top = 12.dp),
                        )
                    }
                    item(key = "c$day") {
                        AppCard {
                            entries.forEach { row ->
                                EntryRow(row, money, resources) {
                                    scope.launch {
                                        val ok = vm.restore(row.entry)
                                        snackbar.showSnackbar(resources.getString(if (ok) R.string.activity_restored else R.string.activity_restore_failed))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (retentionDialog) {
        AlertDialog(
            onDismissRequest = { retentionDialog = false },
            title = { Text(stringResource(R.string.activity_keep)) },
            text = {
                Column {
                    ActivityLog.RETENTION_CHOICES.forEach { days ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(retention == days, role = Role.RadioButton) { vm.setRetention(days); retentionDialog = false }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = retention == days, onClick = null)
                            Text(retentionLabel(days), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { retentionDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.activity_clear)) },
            text = { Text(stringResource(R.string.activity_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clear(); clearDialog = false
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.activity_cleared)) }
                }) { Text(stringResource(R.string.activity_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun retentionLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.activity_keep_forever)
    365 -> stringResource(R.string.activity_keep_year)
    else -> pluralStringResource(R.plurals.activity_keep_days, days, days)
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun EntryRow(row: ActivityRow, money: MoneyFormatter, resources: Resources, onRestore: () -> Unit) {
    val e = row.entry
    val (headline, supporting) = describe(e, money, resources)
    val time = Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).format(timeFormat)
    val negative = e.action == DELETED || e.action == IGNORED || e.action == RESET
    ListItem(
        leadingContent = {
            Box(
                Modifier.size(36.dp).background(
                    (if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(e.kind.icon()), null, Modifier.size(20.dp),
                    tint = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(supporting, time).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (row.restorable) {
            { TextButton(onClick = onRestore) { Text(stringResource(R.string.activity_restore)) } }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Words an entry for people: a headline and an optional second line. */
private fun describe(e: ActivityEntry, money: MoneyFormatter, r: Resources): Pair<String, String?> {
    val amount = e.amount?.let(money::format)
    val what = listOfNotNull(amount, e.subject).joinToString(" · ").ifEmpty { r.getString(R.string.category_none) }
    return when (e.kind) {
        ActivityKind.SPEND -> {
            val res = when (e.action) {
                QUICK_ADDED -> R.string.act_spend_quick
                AUTO_ADDED -> R.string.act_spend_auto
                EDITED -> R.string.act_spend_edited
                DELETED -> R.string.act_spend_deleted
                RESTORED -> R.string.act_spend_restored
                else -> R.string.act_spend_added
            }
            val was = if (e.action == EDITED) e.snapshot?.let(ActivityLog::expenseFrom)?.let { before ->
                r.getString(R.string.activity_was, money.format(before.amount) + if (before.date != null) " · " + DateFormats.short(before.date) else "")
            } else null
            r.getString(res, what) to (was ?: e.detail)
        }
        ActivityKind.BUDGET -> {
            val name = e.subject.orEmpty()
            val res = when (e.action) {
                ADDED -> R.string.act_budget_added
                PAUSED -> R.string.act_budget_paused
                RESUMED -> R.string.act_budget_resumed
                DELETED -> R.string.act_budget_deleted
                else -> R.string.act_budget_edited
            }
            val second = when {
                e.action == DELETED && e.detail == BudgetEngine.MOVED_TO_PRIMARY -> r.getString(R.string.act_budget_moved)
                e.action == PAUSED && e.detail != null -> runCatching { DateFormats.short(LocalDate.parse(e.detail)) }.getOrNull()
                else -> amount
            }
            r.getString(res, name) to second
        }
        ActivityKind.CATEGORY -> {
            val name = e.subject.orEmpty()
            when {
                e.action == REORDERED -> r.getString(R.string.act_category_reordered) to null
                e.action == SHOWN && e.detail == CategoryRepository.DEFAULTS -> r.getString(R.string.act_category_defaults) to null
                else -> r.getString(
                    when (e.action) {
                        ADDED -> R.string.act_category_added
                        HIDDEN -> R.string.act_category_hidden
                        DELETED -> R.string.act_category_deleted
                        RESTORED -> R.string.act_category_restored
                        SHOWN -> R.string.act_category_shown
                        else -> R.string.act_category_edited
                    },
                    name,
                ) to e.detail?.let { r.getString(R.string.activity_was, it) }
            }
        }
        ActivityKind.RECURRING -> {
            val res = when (e.action) {
                ADDED -> R.string.act_recurring_added
                PAUSED -> R.string.act_recurring_paused
                RESUMED -> R.string.act_recurring_resumed
                SKIPPED -> R.string.act_recurring_skipped
                DELETED -> R.string.act_recurring_deleted
                else -> R.string.act_recurring_edited
            }
            val second = when (e.action) {
                SKIPPED -> e.detail?.let { runCatching { DateFormats.short(LocalDate.parse(it)) }.getOrNull() }
                else -> e.detail?.let { runCatching { r.getString(repeatLabel(Repeat.valueOf(it))) }.getOrNull() }
            }
            r.getString(res, what) to second
        }
        ActivityKind.PAYMENT -> {
            val res = when (e.action) {
                LOGGED -> R.string.act_payment_logged
                IGNORED -> R.string.act_payment_ignored
                else -> R.string.act_payment_detected
            }
            r.getString(res, amount.orEmpty()) to listOfNotNull(e.subject, e.detail).joinToString(" · ").ifEmpty { null }
        }
        ActivityKind.SETTINGS -> {
            val key = runCatching { SettingKey.valueOf(e.subject.orEmpty()) }.getOrNull()
            r.getString(R.string.act_setting_changed, key?.let { r.getString(it.label()) } ?: e.subject.orEmpty(), settingValue(key, e.detail, r)) to null
        }
        ActivityKind.DATA -> when (e.action) {
            EXPORTED -> r.getString(if (e.detail == "csv") R.string.act_data_exported_csv else R.string.act_data_exported) to null
            IMPORTED -> r.getString(R.string.act_data_imported) to null
            else -> r.getString(R.string.act_data_reset) to null
        }
    }
}

private fun settingValue(key: SettingKey?, value: String?, r: Resources): String = when {
    value == null || value.isEmpty() -> r.getString(R.string.act_none)
    value == "true" -> r.getString(R.string.act_on)
    value == "false" -> r.getString(R.string.act_off)
    key == SettingKey.THEME -> r.getString(
        when (value) { "LIGHT" -> R.string.theme_light; "DARK" -> R.string.theme_dark; else -> R.string.theme_system },
    )
    key == SettingKey.ACTIVITY_RETENTION -> when (val d = value.toIntOrNull()) {
        0 -> r.getString(R.string.activity_keep_forever)
        365 -> r.getString(R.string.activity_keep_year)
        null -> value
        else -> r.getQuantityString(R.plurals.activity_keep_days, d, d)
    }
    key == SettingKey.ACCENT -> value.lowercase().replaceFirstChar(Char::uppercase)
    else -> value.replace(": true", ": " + r.getString(R.string.act_on)).replace(": false", ": " + r.getString(R.string.act_off))
}

private fun SettingKey.label(): Int = when (this) {
    SettingKey.THEME -> R.string.settings_theme
    SettingKey.ACCENT -> R.string.settings_accent
    SettingKey.PURE_BLACK -> R.string.settings_pure_black
    SettingKey.CURRENCY -> R.string.currency_title
    SettingKey.REMINDER -> R.string.settings_reminder
    SettingKey.REMINDER_TIME -> R.string.settings_reminder_time
    SettingKey.REMINDER_SKIP -> R.string.settings_reminder_skip
    SettingKey.APP_LOCK -> R.string.settings_app_lock
    SettingKey.PAYMENTS -> R.string.payments_title
    SettingKey.PAYMENTS_NOTIFY -> R.string.payments_notify
    SettingKey.PAYMENT_SOURCE -> R.string.payments_sources
    SettingKey.ACTIVITY_RETENTION -> R.string.activity_keep
}

private fun ActivityKind.label(): Int = when (this) {
    ActivityKind.SPEND -> R.string.activity_kind_spend
    ActivityKind.BUDGET -> R.string.activity_kind_budget
    ActivityKind.CATEGORY -> R.string.activity_kind_category
    ActivityKind.RECURRING -> R.string.activity_kind_recurring
    ActivityKind.PAYMENT -> R.string.activity_kind_payment
    ActivityKind.SETTINGS -> R.string.activity_kind_settings
    ActivityKind.DATA -> R.string.activity_kind_data
}

private fun ActivityKind.icon(): Int = when (this) {
    ActivityKind.SPEND -> R.drawable.ic_receipt_long
    ActivityKind.BUDGET -> R.drawable.ic_account_balance_wallet
    ActivityKind.CATEGORY -> R.drawable.ic_label
    ActivityKind.RECURRING -> R.drawable.ic_repeat
    ActivityKind.PAYMENT -> R.drawable.ic_notifications_active
    ActivityKind.SETTINGS -> R.drawable.ic_settings
    ActivityKind.DATA -> R.drawable.ic_settings_backup_restore
}

private fun ActivityRange.label(): Int = when (this) {
    ActivityRange.TODAY -> R.string.activity_range_today
    ActivityRange.WEEK -> R.string.activity_range_week
    ActivityRange.MONTH -> R.string.activity_range_month
    ActivityRange.ALL -> R.string.activity_range_all
}
