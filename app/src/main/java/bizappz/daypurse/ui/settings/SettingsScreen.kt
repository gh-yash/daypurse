package bizappz.daypurse.ui.settings

import android.Manifest
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.theme.swatch
import bizappz.daypurse.data.model.Accent
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import bizappz.daypurse.ui.components.AppSnackbarHost
import android.os.Build
import androidx.compose.ui.platform.LocalResources
import bizappz.daypurse.ui.format.currentLocale
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.payments.PaymentListener
import bizappz.daypurse.ui.activity.retentionLabel
import bizappz.daypurse.ui.navigation.AppActions
import bizappz.daypurse.ui.payments.PaymentSettingsViewModel
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.model.ThemeMode
import bizappz.daypurse.security.AppLock
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.CurrencyPickerSheet
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class Dialog { NONE, LICENSES, THEME, TIME, IMPORT, RESET }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: AppActions, modifier: Modifier = Modifier) {
    val vm = appViewModel {
        SettingsViewModel(it.settingsRepository, it.budgetEngine, it.categoryRepository, it.backupManager, it.reminderScheduler,
            it.appLock, it.recurringSpends)
    }
    val onOpenBudgets = actions.openBudgets
    val onOpenCategories = actions.openCategories
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resolver = context.contentResolver
    val resources = LocalResources.current
    val locale = currentLocale()
    val money = LocalMoneyFormatter.current
    val snackbar = remember { SnackbarHostState() }
    var currencyOpen by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val text = resources.getString(m.res) + (m.detail?.let { ": $it" } ?: "")
            snackbar.showSnackbar(text)
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportJson(it, resolver) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it, resolver) }
    }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = uri; dialog = Dialog.IMPORT }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val s = state ?: return@rememberLauncherForActivityResult
        if (granted) vm.setReminder(true, s.settings.reminderMinutes)
    }

    val s = state ?: return
    val settings = s.settings
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val reminderTime = LocalTime.of(settings.reminderMinutes / 60, settings.reminderMinutes % 60).format(timeFormat)

    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0), snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp),
                )
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_budget)) {
                    SettingRow(
                        R.drawable.ic_account_balance_wallet,
                        stringResource(R.string.budgets_title),
                        s.budgets.joinToString(", ") { it.name }.ifEmpty { null },
                        onClick = onOpenBudgets,
                    )
                    SettingRow(R.drawable.ic_label, stringResource(R.string.categories_title),
                        pluralStringResource(R.plurals.categories_count, s.categoryCount, s.categoryCount), onClick = onOpenCategories)
                    SettingRow(
                        R.drawable.ic_event_repeat, stringResource(R.string.recurring_title),
                        if (s.recurringActive == 0) stringResource(R.string.recurring_none)
                        else pluralStringResource(R.plurals.recurring_count, s.recurringActive, s.recurringActive),
                        onClick = actions.openRecurring,
                    )
                    SettingRow(
                        R.drawable.ic_currency_exchange, stringResource(R.string.currency_title),
                        money.currency?.let { "${it.getDisplayName(locale)} (${money.symbol})" } ?: stringResource(R.string.currency_none),
                        onClick = { currencyOpen = true },
                    )
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_appearance)) {
                    SettingRow(R.drawable.ic_dark_mode, stringResource(R.string.settings_theme),
                        stringResource(settings.themeMode.label()), onClick = { dialog = Dialog.THEME })
                    AccentRow(settings.accent, vm::setAccent)
                    SwitchRow(R.drawable.ic_contrast, stringResource(R.string.settings_pure_black),
                        stringResource(R.string.settings_pure_black_hint), settings.pureBlack) { vm.setPureBlack(it) }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_payments)) {
                    // Re-checked on return from system settings, where access is granted or revoked.
                    var hasAccess by remember { mutableStateOf(PaymentListener.hasAccess(context)) }
                    LifecycleResumeEffect(Unit) { hasAccess = PaymentListener.hasAccess(context); onPauseOrDispose { } }
                    val sources = remember { PaymentSettingsViewModel.installedSources(context) }
                    val reading = sources.count { it.packageName !in settings.paymentSourcesOff }
                    SettingRow(
                        R.drawable.ic_notifications_active, stringResource(R.string.payments_title),
                        when {
                            !settings.paymentsEnabled -> stringResource(R.string.payments_hint_off)
                            !hasAccess -> stringResource(R.string.payments_hint_no_access)
                            else -> pluralStringResource(R.plurals.payments_hint_on, reading, reading)
                        },
                        onClick = actions.openPayments,
                    )
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_reminder)) {
                    SwitchRow(R.drawable.ic_notifications, stringResource(R.string.settings_reminder),
                        stringResource(R.string.settings_reminder_hint), settings.reminderEnabled) { on ->
                        // Runtime notification permission exists only on Android 13+.
                        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (on && needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.setReminder(on, settings.reminderMinutes)
                    }
                    AnimatedVisibility(settings.reminderEnabled) {
                        Column {
                            SettingRow(R.drawable.ic_schedule, stringResource(R.string.settings_reminder_time), reminderTime,
                                onClick = { dialog = Dialog.TIME })
                            SwitchRow(R.drawable.ic_done_all, stringResource(R.string.settings_reminder_skip),
                                stringResource(R.string.settings_reminder_skip_hint), settings.reminderSkipIfLogged) { vm.setReminderSkip(it) }
                        }
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_security)) {
                    val available = remember { AppLock.isAvailable(context) }
                    SwitchRow(
                        R.drawable.ic_fingerprint, stringResource(R.string.settings_app_lock),
                        stringResource(if (available) R.string.settings_app_lock_hint else R.string.settings_app_lock_unavailable),
                        settings.appLockEnabled, enabled = available || settings.appLockEnabled,
                    ) { on ->
                        // Require a successful unlock to change the setting either way.
                        activity?.let { vm.appLock.authenticate(it, onSuccess = { vm.setAppLock(on) }) }
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_data)) {
                    SettingRow(R.drawable.ic_history, stringResource(R.string.settings_activity),
                        stringResource(R.string.settings_activity_hint, retentionLabel(settings.activityRetentionDays)),
                        onClick = actions.openActivity)
                    SettingRow(R.drawable.ic_upload, stringResource(R.string.settings_export), stringResource(R.string.settings_export_hint),
                        onClick = { exportJson.launch("daypurse-backup-${LocalDate.now()}.json") })
                    SettingRow(R.drawable.ic_download, stringResource(R.string.settings_import), stringResource(R.string.settings_import_hint),
                        onClick = { importJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })
                    SettingRow(R.drawable.ic_table_view, stringResource(R.string.settings_export_csv), stringResource(R.string.settings_export_csv_hint),
                        onClick = { exportCsv.launch("daypurse-spends-${LocalDate.now()}.csv") })
                    SettingRow(R.drawable.ic_delete_forever, stringResource(R.string.settings_reset), stringResource(R.string.settings_reset_hint),
                        destructive = true, onClick = { dialog = Dialog.RESET })
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_about)) {
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                    }
                    SettingRow(R.drawable.ic_info, stringResource(R.string.app_name), stringResource(R.string.settings_about, version))
                    SettingRow(R.drawable.ic_code, stringResource(R.string.about_source), stringResource(R.string.about_source_hint),
                        onClick = { openLink(context, SOURCE_URL) })
                    SettingRow(R.drawable.ic_lock, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_hint),
                        onClick = { openLink(context, "$SOURCE_URL/blob/main/PRIVACY.md") })
                    SettingRow(R.drawable.ic_bug_report, stringResource(R.string.about_issue), stringResource(R.string.about_issue_hint),
                        onClick = { openLink(context, "$SOURCE_URL/issues") })
                    SettingRow(R.drawable.ic_description, stringResource(R.string.about_licenses), stringResource(R.string.about_licenses_hint),
                        onClick = { dialog = Dialog.LICENSES })
                }
            }
        }
    }

    if (currencyOpen) {
        CurrencyPickerSheet(settings.currencyCode, onSelect = { vm.setCurrency(it); currencyOpen = false }, onDismiss = { currencyOpen = false })
    }

    when (dialog) {
        Dialog.THEME -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(settings.themeMode == mode, role = Role.RadioButton) { vm.setThemeMode(mode); dialog = Dialog.NONE }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
                            Text(stringResource(mode.label()), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.action_cancel)) } },
        )

        Dialog.TIME -> {
            val picker = rememberTimePickerState(settings.reminderMinutes / 60, settings.reminderMinutes % 60)
            AlertDialog(
                onDismissRequest = { dialog = Dialog.NONE },
                text = { TimePicker(picker) },
                confirmButton = {
                    TextButton(onClick = { vm.setReminder(true, picker.hour * 60 + picker.minute); dialog = Dialog.NONE }) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        Dialog.IMPORT -> ConfirmDialog(
            title = stringResource(R.string.settings_import),
            text = stringResource(R.string.import_confirm),
            confirm = stringResource(R.string.import_confirm_action),
            onConfirm = { pendingImport?.let { vm.importJson(it, resolver) }; pendingImport = null; dialog = Dialog.NONE },
            onDismiss = { pendingImport = null; dialog = Dialog.NONE },
        )

        Dialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.settings_reset),
            text = stringResource(R.string.reset_confirm),
            confirm = stringResource(R.string.reset_confirm_action),
            onConfirm = { vm.resetAll(); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )

        Dialog.LICENSES -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.licenses_app), style = MaterialTheme.typography.bodyMedium)
                    OPEN_SOURCE.forEach { (name, license) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.action_ok)) } },
        )

        Dialog.NONE -> Unit
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title)
    AppCard(content = content)
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ListItem(
        leadingContent = { IconTile(icon, tint) },
        headlineContent = { Text(title, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
        supportingContent = subtitle?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun SwitchRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { IconTile(icon, MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(checked, enabled = enabled, role = Role.Switch) { onChange(!checked) },
    )
}

/** Small tinted rounded square behind a settings icon. */
@Composable
private fun IconTile(icon: Int, tint: Color) {
    Box(
        Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Row of accent swatches; the selected one gets a ring and a check. */
@Composable
private fun AccentRow(selected: Accent, onSelect: (Accent) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(R.drawable.ic_palette, MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 56.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Accent.entries.forEach { accent ->
                val isSelected = accent == selected
                Box(
                    Modifier
                        .size(36.dp)
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                        )
                        .padding(4.dp)
                        .background(accent.swatch(), CircleShape)
                        .clickable(role = Role.RadioButton, onClickLabel = accent.name) { onSelect(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private const val SOURCE_URL = "https://github.com/gh-yash/daypurse"

/** Libraries in the app, with their licences (shown in Settings → Open-source licences). */
private val OPEN_SOURCE = listOf(
    "AndroidX (Activity, Compose, Core, DataStore, Lifecycle, Navigation, Room)" to "Apache License 2.0",
    "Kotlin and kotlinx.coroutines" to "Apache License 2.0",
    "Material Symbols icons" to "Apache License 2.0",
    "Reorderable (Calvin Liang)" to "Apache License 2.0",
)

/** Opens a web page in the browser; the app itself never goes online. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
    }
}
