package bizappz.daypurse.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bizappz.daypurse.R
import bizappz.daypurse.ui.components.AppCard
import bizappz.daypurse.ui.components.CategoryBadge
import bizappz.daypurse.ui.components.IconBadge
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.format.LocalMoneyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Quick add": spends the user repeats, one tap each. Long-press for "Edit first" or to stop
 * suggesting it.
 */
@Composable
fun QuickSpendRow(
    chips: List<QuickChip>,
    onTap: (QuickChip) -> Unit,
    onEdit: (QuickChip) -> Unit,
    onHide: (QuickChip) -> Unit,
) {
    Column {
        Row(Modifier.padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.Bottom) {
            SectionTitle(stringResource(R.string.quick_title), Modifier.weight(1f))
            Text(
                stringResource(R.string.quick_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips, key = { it.key }) { chip -> QuickChipView(chip, onTap, onEdit, onHide) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChipView(chip: QuickChip, onTap: (QuickChip) -> Unit, onEdit: (QuickChip) -> Unit, onHide: (QuickChip) -> Unit) {
    val money = LocalMoneyFormatter.current
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val label = chip.label ?: stringResource(R.string.category_none)
    val amount = money.format(chip.spend.amount)
    val addDescription = stringResource(R.string.quick_add_cd, amount, label)
    val moreDescription = stringResource(R.string.quick_more_cd)
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .combinedClickable(
                    onClick = { onTap(chip) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .semantics {
                    contentDescription = addDescription
                    onLongClick(label = moreDescription) { menu = true; true }
                },
        ) {
            Row(Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (chip.category != null) CategoryBadge(chip.category, size = 32.dp)
                else IconBadge("payments", MaterialTheme.colorScheme.primary, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MaxLabelWidth))
                    Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_edit_first)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                onClick = { menu = false; onEdit(chip) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_stop_suggesting)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_visibility_off), null) },
                onClick = { menu = false; onHide(chip) },
            )
        }
    }
}

private val MaxLabelWidth = 120.dp

/**
 * Payments read from other apps' notifications, waiting for the user: Log (with the category
 * used for that payee before) or Ignore. Tapping a row opens it in the add sheet to adjust.
 */
@Composable
fun PaymentInboxCard(
    rows: List<InboxRow>,
    onOpen: (InboxRow) -> Unit,
    onLog: (InboxRow) -> Unit,
    onIgnore: (InboxRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoneyFormatter.current
    AppCard(modifier) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_inbox), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.inbox_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(rows.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.take(MAX_ROWS).forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.fillMaxWidth().clickable { onOpen(row) }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(money.format(row.payment.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.payment.payee ?: stringResource(R.string.inbox_unknown_payee),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    listOfNotNull(row.payment.sourceLabel, whenText(row.payment.detectedAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.suggested != null) {
                        CategoryBadge(row.suggested, size = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(row.suggested.name, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onIgnore(row) }) { Text(stringResource(R.string.inbox_ignore)) }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(onClick = { onLog(row) }, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text(stringResource(if (row.suggested != null) R.string.inbox_log else R.string.inbox_choose))
                    }
                }
            }
        }
        if (rows.size > MAX_ROWS) {
            Text(
                pluralStringResource(R.plurals.inbox_more, rows.size - MAX_ROWS, rows.size - MAX_ROWS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        } else {
            Spacer(Modifier.size(6.dp))
        }
    }
}

private const val MAX_ROWS = 3

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun whenText(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (at.toLocalDate() == LocalDate.now()) at.format(timeFormat) else at.format(dateFormat) + ", " + at.format(timeFormat)
}
