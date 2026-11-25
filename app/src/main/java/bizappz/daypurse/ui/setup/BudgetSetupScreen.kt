package bizappz.daypurse.ui.setup

import androidx.compose.foundation.clickable
import bizappz.daypurse.ui.components.IconBadge
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.domain.budget.DailyBudgetCalculator
import bizappz.daypurse.domain.budget.DateWindow
import bizappz.daypurse.domain.money.AmountInput
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.CurrencyPickerSheet
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.MoneyFormatter
import bizappz.daypurse.ui.format.currentLocale
import bizappz.daypurse.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * Create or edit the budget plan. Shown full-screen on first launch (no back button) and from
 * Home/Settings when editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetSetupScreen(
    onDone: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    budgetId: Long? = null,
    createNew: Boolean = false,
) {
    val vm = appViewModel(key = "setup-$budgetId-$createNew") {
        BudgetSetupViewModel(it.budgetEngine, it.settingsRepository, it.todayProvider, budgetId, createNew)
    }
    if (!vm.loaded) return
    val form = vm.form
    val locale = currentLocale()
    val isFirstRun = vm.mode == SetupMode.FIRST_RUN
    val isNew = vm.mode == SetupMode.NEW_BUDGET
    val templateNames = BudgetTemplate.entries.associateWith { stringResource(it.label()) }
    // New budgets start from a template; give it the template's name until the user types one.
    LaunchedEffect(vm.template) {
        val t = vm.template ?: return@LaunchedEffect
        if (form.name.isBlank() || form.name in templateNames.values) vm.update { it.copy(name = templateNames.getValue(t)) }
    }
    var showLookPicker by rememberSaveable { mutableStateOf(false) }
    // Preview uses the currency being chosen during onboarding.
    val money = remember(form.currencyCode) { MoneyFormatter(form.currencyCode) }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var showMonthDays by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (vm.mode) {
                                SetupMode.FIRST_RUN -> R.string.setup_title_new
                                SetupMode.NEW_BUDGET -> R.string.budget_new_title
                                SetupMode.EDIT -> R.string.setup_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.save(onDone) },
                enabled = form.isValid && !vm.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(
                    stringResource(
                        when (vm.mode) {
                            SetupMode.FIRST_RUN -> R.string.setup_start
                            SetupMode.NEW_BUDGET -> R.string.budget_create
                            SetupMode.EDIT -> R.string.action_save
                        },
                    ),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (isFirstRun) {
                Text(
                    stringResource(R.string.setup_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_private), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                RestoreBackupButton()
            }

            if (isNew) {
                SectionTitle(stringResource(R.string.budget_template))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetTemplate.entries.forEach { t ->
                        FilterChip(
                            selected = vm.template == t,
                            onClick = { vm.applyTemplate(t, name = templateNames.getValue(t)) },
                            label = { Text(templateNames.getValue(t)) },
                            leadingIcon = { IconBadge(t.iconKey, Color(t.color.toInt()), size = 24.dp) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }

            if (!isFirstRun) {
                SectionTitle(stringResource(R.string.budget_name))
                AppCard {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            form.iconKey,
                            if (form.color == 0) MaterialTheme.colorScheme.primary else Color(form.color),
                            Modifier.clip(MaterialTheme.shapes.medium).clickable { showLookPicker = true },
                            size = 52.dp,
                        )
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { v -> vm.update { it.copy(name = v.take(30)) } },
                            placeholder = { Text(stringResource(R.string.budget_name_hint)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                            ),
                            textStyle = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.setup_period))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BudgetType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, BudgetType.entries.size),
                    ) { Text(stringResource(type.label())) }
                }
            }

            SectionTitle(stringResource(R.string.setup_amount))
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { text -> vm.update { it.copy(amountText = AmountInput.sanitize(text)) } },
                prefix = money.symbol?.let { { Text(it, style = MaterialTheme.typography.headlineMedium) } },
                placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                visualTransformation = remember(locale) { AmountGroupingTransformation(locale) },
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AppTheme.colors.card,
                    focusedContainerColor = AppTheme.colors.card,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            when (form.type) {
                BudgetType.WEEKLY -> {
                    SectionTitle(stringResource(R.string.setup_week_starts))
                    WeekdaySelector(form.weekStartDay, onSelect = { day -> vm.update { it.copy(weekStartDay = day) } })
                }

                BudgetType.MONTHLY -> {
                    SectionTitle(stringResource(R.string.setup_month_starts))
                    AppCard(Modifier.clickable { showMonthDays = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_calendar_today), null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.setup_day_of_month, form.monthStartDay),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                            Text(stringResource(R.string.action_change), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (form.monthStartDay > 28) Hint(stringResource(R.string.setup_month_clamp_hint))
                }

                BudgetType.CUSTOM -> {
                    SectionTitle(stringResource(R.string.setup_custom_range))
                    AppCard(Modifier.clickable { showRangePicker = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_calendar_today), null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(DateFormats.range(form.customStart, form.customEnd), style = MaterialTheme.typography.titleMedium)
                                form.customDays?.let { days ->
                                    Text(
                                        pluralStringResource(R.plurals.days, days, days),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(stringResource(R.string.action_change), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (form.customDays == null) Hint(pluralStringResource(R.plurals.setup_range_too_long, SetupForm.MAX_CUSTOM_DAYS, SetupForm.MAX_CUSTOM_DAYS))
                }
            }

            SectionTitle(stringResource(R.string.setup_options))
            AppCard {
                ToggleRow(
                    title = stringResource(R.string.budget_daily_split),
                    subtitle = stringResource(R.string.budget_daily_split_hint),
                    checked = form.dailySplit,
                    onChange = { v -> vm.update { it.copy(dailySplit = v) } },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ToggleRow(
                    title = stringResource(R.string.setup_repeat),
                    subtitle = stringResource(R.string.setup_repeat_hint),
                    checked = form.recurring,
                    onChange = { v -> vm.update { it.copy(recurring = v, recurringTouched = true) } },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ToggleRow(
                    title = stringResource(R.string.setup_carry_over),
                    subtitle = stringResource(R.string.setup_carry_over_hint),
                    checked = form.carryOver,
                    onChange = { v -> vm.update { it.copy(carryOver = v) } },
                )
                if (isFirstRun) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.currency_title)) },
                        supportingContent = {
                            Text(
                                money.currency?.let { "${it.getDisplayName(locale)} (${money.symbol})" }
                                    ?: stringResource(R.string.currency_none),
                            )
                        },
                        trailingContent = {
                            Text(stringResource(R.string.action_change), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showCurrencyPicker = true },
                    )
                }
            }

            vm.previewPeriod()?.let { period ->
                val window = DateWindow(period.startDate, period.endDate)
                Spacer(Modifier.height(20.dp))
                val kind = vm.previewKind
                val startsToday = period.startDate == vm.today
                PreviewCard(
                    title = when (kind) {
                        PreviewKind.FIRST_PERIOD -> stringResource(R.string.setup_preview_title)
                        PreviewKind.THIS_PERIOD -> stringResource(R.string.setup_preview_this_period)
                        PreviewKind.NEW_PLAN -> if (startsToday) {
                            stringResource(R.string.setup_preview_new_plan)
                        } else {
                            stringResource(R.string.setup_preview_new_plan_on, DateFormats.dayMonth(period.startDate))
                        }
                    },
                    amount = money.format(period.totalAmount),
                    perDay = money.format(DailyBudgetCalculator.perDay(period.totalAmount, window.days)),
                    dailySplit = form.dailySplit,
                    range = DateFormats.range(period.startDate, period.endDate),
                    days = window.days,
                    note = when {
                        kind == PreviewKind.THIS_PERIOD -> stringResource(R.string.setup_preview_this_period_hint)
                        kind == PreviewKind.NEW_PLAN && startsToday -> stringResource(R.string.setup_preview_new_plan_hint)
                        kind == PreviewKind.NEW_PLAN && period.startDate.isAfter(vm.today) ->
                            stringResource(R.string.setup_preview_new_plan_later_hint, DateFormats.dayMonth(period.startDate.minusDays(1)))
                        kind == PreviewKind.NEW_PLAN -> stringResource(R.string.setup_preview_new_plan_past_hint)
                        period.baseAmount != form.amount -> stringResource(R.string.setup_prorated_hint)
                        else -> null
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerSheet(
            selectedCode = form.currencyCode,
            onSelect = { code -> vm.update { it.copy(currencyCode = code) }; showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    if (showRangePicker) {
        DateRangeDialog(
            start = form.customStart,
            end = form.customEnd,
            onConfirm = { start, end -> vm.update { it.copy(customStart = start, customEnd = end) }; showRangePicker = false },
            onDismiss = { showRangePicker = false },
        )
    }

    if (showLookPicker) {
        IconColorSheet(
            iconKey = form.iconKey,
            color = form.color,
            onPick = { icon, color -> vm.update { it.copy(iconKey = icon, color = color) } },
            onDismiss = { showLookPicker = false },
        )
    }

    if (showMonthDays) {
        MonthDaySheet(
            selected = form.monthStartDay,
            onSelect = { day -> vm.update { it.copy(monthStartDay = day) }; showMonthDays = false },
            onDismiss = { showMonthDays = false },
        )
    }
}

private fun BudgetTemplate.label() = when (this) {
    BudgetTemplate.FESTIVAL -> R.string.template_festival
    BudgetTemplate.VACATION -> R.string.template_vacation
    BudgetTemplate.WEDDING -> R.string.template_wedding
    BudgetTemplate.GIFTS -> R.string.template_gifts
    BudgetTemplate.CUSTOM -> R.string.template_custom
}

private fun BudgetType.label() = when (this) {
    BudgetType.WEEKLY -> R.string.budget_weekly
    BudgetType.MONTHLY -> R.string.budget_monthly
    BudgetType.CUSTOM -> R.string.budget_custom
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun PreviewCard(title: String, amount: String, perDay: String, dailySplit: Boolean, range: String, days: Int, note: String?) {
    val colors = AppTheme.colors
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.heroStart,
        contentColor = colors.onHero,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (dailySplit) {
                Text(stringResource(R.string.setup_preview_per_day, perDay), style = MaterialTheme.typography.headlineMedium)
            } else {
                Text(amount, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.budget_soft_per_day, perDay), style = MaterialTheme.typography.bodyMedium)
            }
            Text("$range · " + pluralStringResource(R.plurals.days, days, days) + " · $amount", style = MaterialTheme.typography.bodyMedium)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
