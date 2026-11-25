package bizappz.daypurse.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.settings.SettingsRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.budget.PeriodCalculator
import bizappz.daypurse.domain.money.AmountInput
import bizappz.daypurse.ui.format.MoneyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

data class SetupForm(
    val name: String = "Daily",
    val iconKey: String = "account_balance_wallet",
    val color: Int = 0,
    val type: BudgetType = BudgetType.MONTHLY,
    val amountText: String = "",
    val weekStartDay: Int = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value,
    val monthStartDay: Int = 1,
    val customStart: LocalDate = LocalDate.now(),
    /** Inclusive. */
    val customEnd: LocalDate = LocalDate.now().plusDays(13),
    val recurring: Boolean = true,
    /** Once the user flips the toggle we stop changing it when the type changes. */
    val recurringTouched: Boolean = false,
    val carryOver: Boolean = false,
    val dailySplit: Boolean = true,
    val currencyCode: String? = null,
) {
    val amount: Long get() = AmountInput.toMinor(amountText)
    val customDays: Int? get() =
        (ChronoUnit.DAYS.between(customStart, customEnd) + 1).toInt().takeIf { it in 1..MAX_CUSTOM_DAYS }
    val isValid: Boolean get() = amount > 0 && name.isNotBlank() && (type != BudgetType.CUSTOM || customDays != null)

    fun toBudget(today: LocalDate): Budget = Budget(
        type = type,
        amount = amount,
        startDate = if (type == BudgetType.CUSTOM) customStart else today,
        anchorDay = when (type) {
            BudgetType.WEEKLY -> weekStartDay
            BudgetType.MONTHLY -> monthStartDay
            BudgetType.CUSTOM -> 1
        },
        customLengthDays = if (type == BudgetType.CUSTOM) customDays else null,
        recurring = recurring,
        carryOver = carryOver,
        name = name.trim(),
        iconKey = iconKey,
        color = color,
        dailySplit = dailySplit,
    )

    companion object {
        const val MAX_CUSTOM_DAYS = 366
    }
}

/** Ready-made starting points for extra budgets. */
enum class BudgetTemplate(val iconKey: String, val color: Long, val days: Long, val dailySplit: Boolean) {
    FESTIVAL("celebration", 0xFFFFB74D, 7, false),
    VACATION("flight", 0xFF4FC3F7, 7, false),
    WEDDING("volunteer_activism", 0xFFF06292, 30, false),
    GIFTS("redeem", 0xFFBA68C8, 30, false),
    CUSTOM("savings", 0xFF4DB6AC, 14, false),
}

/** What the preview card describes. */
enum class PreviewKind { FIRST_PERIOD, THIS_PERIOD, NEW_PLAN }

enum class SetupMode { FIRST_RUN, NEW_BUDGET, EDIT }

/**
 * Backs the budget editor in three modes: first launch (creates the primary "Daily" budget),
 * adding an extra budget, and editing an existing one.
 */
class BudgetSetupViewModel(
    private val engine: BudgetEngine,
    private val settings: SettingsRepository,
    private val todayProvider: TodayProvider,
    private val budgetId: Long?,
    createNew: Boolean,
) : ViewModel() {
    var form by mutableStateOf(SetupForm())
        private set
    var existing by mutableStateOf<Budget?>(null)
        private set
    var mode by mutableStateOf(if (createNew) SetupMode.NEW_BUDGET else SetupMode.FIRST_RUN)
        private set
    var template by mutableStateOf<BudgetTemplate?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    /** Period of the edited budget running today. */
    private var currentPeriod: BudgetPeriod? = null

    val today: LocalDate get() = todayProvider.today.value

    init {
        viewModelScope.launch {
            val prefs = settings.settings.first()
            val budget = budgetId?.let { engine.get(it) }
                ?: if (!createNew) engine.primaryBudget.first() else null
            when {
                budget != null -> {
                    mode = SetupMode.EDIT
                    existing = budget
                    currentPeriod = engine.currentPeriod(budget.id, today)
                    form = SetupForm(
                        name = budget.name,
                        iconKey = budget.iconKey,
                        color = budget.color,
                        type = budget.type,
                        amountText = AmountInput.fromMinor(budget.amount),
                        weekStartDay = if (budget.type == BudgetType.WEEKLY) budget.anchorDay else form.weekStartDay,
                        monthStartDay = if (budget.type == BudgetType.MONTHLY) budget.anchorDay else 1,
                        customStart = if (budget.type == BudgetType.CUSTOM) budget.startDate else today,
                        customEnd = if (budget.type == BudgetType.CUSTOM) {
                            budget.startDate.plusDays((budget.customLengthDays ?: 14) - 1L)
                        } else today.plusDays(13),
                        recurring = budget.recurring,
                        recurringTouched = true,
                        carryOver = budget.carryOver,
                        dailySplit = budget.dailySplit,
                        currencyCode = prefs.currencyCode,
                    )
                }
                createNew -> applyTemplate(BudgetTemplate.FESTIVAL, prefs.currencyCode)
                // First run: suggest the phone's currency; the user can pick "none".
                else -> form = SetupForm(currencyCode = prefs.currencyCode ?: MoneyFormatter.localeCurrencyCode())
            }
            loaded = true
        }
    }

    fun update(transform: (SetupForm) -> SetupForm) {
        form = transform(form)
    }

    fun setType(type: BudgetType) = update {
        it.copy(type = type, recurring = if (it.recurringTouched) it.recurring else type != BudgetType.CUSTOM)
    }

    /** Fills name, look and a sensible length from a template (keeps any amount already typed). */
    fun applyTemplate(t: BudgetTemplate, currency: String? = form.currencyCode, name: String = "") {
        template = t
        form = form.copy(
            name = name,
            iconKey = t.iconKey,
            color = t.color.toInt(),
            type = BudgetType.CUSTOM,
            customStart = today,
            customEnd = today.plusDays(t.days - 1),
            recurring = false,
            recurringTouched = false,
            carryOver = false,
            dailySplit = t.dailySplit,
            currencyCode = currency,
        )
    }

    /**
     * True when saving keeps the edited budget's running (or upcoming) period and only changes
     * amount, look or toggles, rather than restarting the plan.
     */
    private fun isInPlaceEdit(f: SetupForm = form): Boolean {
        val old = existing ?: return false
        if (old.type != f.type) return false
        val sameShape = when (f.type) {
            BudgetType.WEEKLY, BudgetType.MONTHLY -> old.anchorDay == f.toBudget(today).anchorDay
            BudgetType.CUSTOM -> old.startDate == f.customStart && old.customLengthDays == f.customDays
        }
        val runningOrUpcoming = currentPeriod != null || old.startDate.isAfter(today)
        return sameShape && runningOrUpcoming
    }

    val previewKind: PreviewKind
        get() = when {
            mode != SetupMode.EDIT -> PreviewKind.FIRST_PERIOD
            isInPlaceEdit() -> PreviewKind.THIS_PERIOD
            else -> PreviewKind.NEW_PLAN
        }

    /** The period the preview card shows: what the user will actually get after saving. */
    fun previewPeriod(): BudgetPeriod? {
        val f = form
        if (!f.isValid) return null
        val old = existing
        val current = currentPeriod
        if (old != null && current != null && isInPlaceEdit(f)) {
            // Mirrors BudgetEngine.updateBudget.
            val updated = old.copy(amount = f.amount)
            val base = if (current.startDate == old.startDate) PeriodCalculator.firstPeriod(updated).baseAmount else f.amount
            return current.copy(baseAmount = base, carryIn = if (f.carryOver) current.carryIn else 0)
        }
        return PeriodCalculator.firstPeriod(f.toBudget(today))
    }

    fun save(onDone: (Long) -> Unit) {
        val f = form
        if (!f.isValid || saving) return
        saving = true
        viewModelScope.launch {
            val id = when (mode) {
                SetupMode.FIRST_RUN -> {
                    val id = engine.createBudget(f.toBudget(today).copy(isPrimary = true), today)
                    settings.setCurrency(f.currencyCode)
                    settings.setOnboardingCompleted(true)
                    id
                }
                SetupMode.NEW_BUDGET -> engine.createBudget(f.toBudget(today), today)
                SetupMode.EDIT -> {
                    val old = requireNotNull(existing)
                    if (isInPlaceEdit(f)) {
                        engine.updateBudget(old.id, today) {
                            it.copy(
                                amount = f.amount, recurring = f.recurring, carryOver = f.carryOver,
                                dailySplit = f.dailySplit, name = f.name.trim(), iconKey = f.iconKey, color = f.color,
                            )
                        }
                    } else {
                        engine.restructure(old.id, f.toBudget(today), today)
                    }
                    old.id
                }
            }
            saving = false
            onDone(id)
        }
    }
}
