package bizappz.daypurse.ui.insights

import androidx.compose.foundation.layout.Arrangement
import bizappz.daypurse.ui.components.BudgetBadge
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import bizappz.daypurse.ui.components.AppCard
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.CategoryBadge
import bizappz.daypurse.ui.format.DateFormats
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    val vm = appViewModel { InsightsViewModel(it.budgetEngine, it.expenseRepository, it.categoryRepository, it.todayProvider) }
    val state by vm.state.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val selectedBudgetId by vm.selectedBudgetId.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val s = state
    val budgetChips: @Composable () -> Unit = {
        if (budgets.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                budgets.forEach { b ->
                    FilterChip(
                        selected = b.id == selectedBudgetId,
                        onClick = { vm.selectBudget(b.id) },
                        label = { Text(b.name) },
                        leadingIcon = { BudgetBadge(b, size = 20.dp) },
                    )
                }
            }
        }
    }
    if (s == null) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            budgetChips()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.insights_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val neutral = MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { budgetChips() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::older, enabled = s.hasPrevious) {
                    Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.insights_older))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.nav_insights), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        DateFormats.range(s.period.startDate, s.period.endDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::newer, enabled = s.hasNext) {
                    Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.insights_newer))
                }
            }
        }

        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Stat(stringResource(R.string.home_spent), money.format(s.spent), Modifier.weight(1f))
                        Stat(stringResource(R.string.home_budget), money.format(s.budget), Modifier.weight(1f))
                        Stat(stringResource(R.string.insights_avg_day), money.format(s.avgPerDay), Modifier.weight(1f))
                    }
                    val over = s.spent > s.budget
                    LinearProgressIndicator(
                        progress = { if (s.budget <= 0) 1f else (s.spent.toFloat() / s.budget).coerceIn(0f, 1f) },
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insights_daily), style = MaterialTheme.typography.titleMedium)
                    DailyBarChart(
                        values = s.daily,
                        baseline = s.baselinePerDay,
                        barColor = MaterialTheme.colorScheme.primary,
                        overColor = MaterialTheme.colorScheme.error,
                        lineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        Text(DateFormats.dayMonth(s.period.startDate), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(DateFormats.dayMonth(s.period.endDate), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        stringResource(R.string.insights_daily_hint, money.format(s.baselinePerDay)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.insights_by_category), style = MaterialTheme.typography.titleMedium)
                    if (s.slices.isEmpty()) {
                        Text(stringResource(R.string.insights_no_spends), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DonutChart(
                                slices = s.slices.map { (it.category?.let { c -> Color(c.color) } ?: neutral) to it.fraction },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(180.dp),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(money.format(s.spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.home_spent), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        items(s.slices, key = { it.category?.id ?: -1L }) { slice ->
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(slice.category, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(slice.category?.name ?: stringResource(R.string.category_none), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(
                        progress = { slice.fraction },
                        color = slice.category?.let { Color(it.color) } ?: neutral,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(slice.total), style = MaterialTheme.typography.titleSmall)
                    Text("${(slice.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
