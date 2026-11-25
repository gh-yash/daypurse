package bizappz.daypurse.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.domain.budget.DayStatus
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.BudgetBadge
import bizappz.daypurse.ui.components.tint
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import bizappz.daypurse.ui.theme.AppTheme
import kotlin.math.abs

/** The main state card for a budget: daily allowance, total-only, or paused. */
@Composable
fun BudgetHero(state: BudgetState.Active, onResume: () -> Unit, modifier: Modifier = Modifier) {
    when {
        state.paused -> PausedCard(state.budget, onResume, modifier)
        state.budget.dailySplit -> DailyHeroCard(state.day, modifier)
        else -> TotalHeroCard(state.day, modifier)
    }
}

@Composable
private fun HeroBox(over: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val brush = Brush.linearGradient(
        if (over) listOf(colors.heroOverStart, colors.heroOverEnd) else listOf(colors.heroStart, colors.heroEnd),
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush)
            .padding(24.dp),
    ) { content() }
}

@Composable
private fun HeroProgress(progress: Float) {
    val onHero = AppTheme.colors.onHero
    LinearProgressIndicator(
        progress = { progress },
        color = onHero,
        trackColor = onHero.copy(alpha = 0.25f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(10.dp),
    )
}

@Composable
private fun HeroPill(icon: Int, text: String) {
    val onHero = AppTheme.colors.onHero
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(onHero.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(painterResource(icon), null, tint = onHero, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = onHero)
    }
}

/** Gradient card with today's allowance; turns red when today's limit is exceeded. */
@Composable
fun DailyHeroCard(day: DayStatus, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val onHero = AppTheme.colors.onHero
    val over = day.isOverDailyLimit
    HeroBox(over, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (over) R.string.home_over_today else R.string.home_left_today),
                style = MaterialTheme.typography.labelLarge,
                color = onHero.copy(alpha = 0.85f),
            )
            Text(money.format(if (over) -day.leftToday else day.leftToday), style = MaterialTheme.typography.displayMedium, color = onHero)
            Text(
                stringResource(R.string.home_daily_limit, money.format(day.dailyLimit), money.format(day.spentToday)),
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            HeroProgress(if (day.dailyLimit <= 0) 1f else (day.spentToday.toFloat() / day.dailyLimit).coerceIn(0f, 1f))
            day.nextDayLimit?.takeIf { day.spentToday > 0 }?.let { next ->
                HeroPill(
                    if (next < day.dailyLimit) R.drawable.ic_trending_down else R.drawable.ic_info,
                    stringResource(
                        if (next < day.dailyLimit) R.string.home_tomorrow_drops else R.string.home_tomorrow,
                        money.format(next),
                    ),
                )
            }
            if (day.dailyLimit == 0L && day.isOverBudget) {
                Text(stringResource(R.string.home_budget_exhausted), style = MaterialTheme.typography.bodyMedium, color = onHero)
            }
        }
    }
}

/** For budgets without a daily limit: what's left of the whole budget. */
@Composable
fun TotalHeroCard(day: DayStatus, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val onHero = AppTheme.colors.onHero
    HeroBox(day.isOverBudget, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (day.isOverBudget) R.string.budget_over_total else R.string.budget_left),
                style = MaterialTheme.typography.labelLarge,
                color = onHero.copy(alpha = 0.85f),
            )
            Text(money.format(abs(day.remaining)), style = MaterialTheme.typography.displayMedium, color = onHero)
            Text(
                stringResource(R.string.budget_spent_of, money.format(day.spentInPeriod), money.format(day.totalBudget)) +
                    " · " + stringResource(R.string.budget_days_left, pluralStringResource(R.plurals.days, day.daysLeft, day.daysLeft)),
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            HeroProgress(day.usedFraction)
            if (!day.isOverBudget && day.daysLeft > 1) {
                HeroPill(
                    R.drawable.ic_info,
                    stringResource(R.string.budget_soft_per_day, money.format(day.remaining / day.daysLeft / 100 * 100)),
                )
            }
        }
    }
}

@Composable
fun PausedCard(budget: Budget, onResume: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pause), null)
                Spacer(Modifier.width(8.dp))
                Text(
                    budget.pauseEnd?.let { stringResource(R.string.budget_paused_until, DateFormats.dayMonth(it)) }
                        ?: stringResource(R.string.budget_paused_indefinitely),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(stringResource(R.string.budget_paused_hint), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onResume, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.budget_resume))
            }
        }
    }
}

/** Remaining money in the period, progress and key numbers. */
@Composable
fun PeriodCard(budget: Budget, day: DayStatus, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when {
                            !budget.isPrimary -> R.string.budget_left
                            budget.type == BudgetType.WEEKLY -> R.string.home_left_week
                            budget.type == BudgetType.MONTHLY -> R.string.home_left_month
                            else -> R.string.home_left_period
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormats.range(day.period.startDate, day.period.endDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                money.format(day.remaining),
                style = MaterialTheme.typography.headlineMedium,
                color = if (day.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = { day.usedFraction },
                color = if (day.isOverBudget) MaterialTheme.colorScheme.error else budget.tint(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Row(Modifier.padding(top = 4.dp)) {
                Stat(stringResource(R.string.home_spent), money.format(day.spentInPeriod), Modifier.weight(1f))
                Stat(stringResource(R.string.home_budget), money.format(day.totalBudget), Modifier.weight(1f))
                Stat(
                    stringResource(R.string.home_days_left),
                    pluralStringResource(R.plurals.days, day.daysLeft, day.daysLeft),
                    Modifier.weight(1f),
                )
            }
            if (day.period.carryIn != 0L) {
                Text(
                    stringResource(
                        if (day.period.carryIn > 0) R.string.home_carry_in_plus else R.string.home_carry_in_minus,
                        money.format(abs(day.period.carryIn)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** One-line status for lists and small cards. */
@Composable
fun budgetStatus(state: BudgetState): String {
    val money = LocalMoneyFormatter.current
    return when (state) {
        BudgetState.NoBudget -> ""
        is BudgetState.NotStarted -> stringResource(R.string.budget_starts_on, DateFormats.dayMonth(state.budget.startDate))
        is BudgetState.Ended -> stringResource(
            R.string.budget_ended_summary,
            money.format(state.spent),
            money.format(state.lastPeriod?.totalAmount ?: state.budget.amount),
        )
        is BudgetState.Active -> when {
            state.paused -> stringResource(R.string.budget_paused)
            else -> stringResource(R.string.budget_left_of, money.format(state.day.remaining), money.format(state.day.totalBudget))
        }
    }
}

/** Compact card for the "Other budgets" row on Home. */
@Composable
fun BudgetMiniCard(state: BudgetState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val budget = when (state) {
        is BudgetState.Active -> state.budget
        is BudgetState.NotStarted -> state.budget
        is BudgetState.Ended -> state.budget
        BudgetState.NoBudget -> return
    }
    val money = LocalMoneyFormatter.current
    AppCard(modifier.width(200.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BudgetBadge(budget, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(budget.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state is BudgetState.Active && !state.paused) {
                Text(money.format(state.day.remaining), style = MaterialTheme.typography.titleLarge,
                    color = if (state.day.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { state.day.usedFraction },
                    color = if (state.day.isOverBudget) MaterialTheme.colorScheme.error else budget.tint(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Text(
                    stringResource(R.string.budget_days_left, pluralStringResource(R.plurals.days, state.day.daysLeft, state.day.daysLeft)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(budgetStatus(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
            }
        }
    }
}
