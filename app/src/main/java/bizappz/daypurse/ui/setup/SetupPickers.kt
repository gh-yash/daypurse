package bizappz.daypurse.ui.setup

import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bizappz.daypurse.R
import bizappz.daypurse.ui.format.currentLocale
import bizappz.daypurse.ui.theme.AppTheme
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Seven equal toggles in one row (never wraps), starting at the locale's first weekday. */
@Composable
fun WeekdaySelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val first = WeekFields.of(locale).firstDayOfWeek.value
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.sortedBy { (it.value - first + 7) % 7 }.forEach { day ->
            val isSelected = day.value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else AppTheme.colors.card)
                    .clickable(role = Role.RadioButton) { onSelect(day.value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Bottom sheet with a 7-column grid of days 1–31. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDaySheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.setup_month_starts), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            (1..31).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { day ->
                        val isSelected = day == selected
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else AppTheme.colors.card)
                                .clickable(role = Role.RadioButton) { onSelect(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // Keep the last row's cells the same size as the others.
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                stringResource(R.string.setup_month_clamp_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Full-screen date-range picker (Material's layout for range selection): close on the left,
 * Save on the right, scrolling months below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    start: LocalDate,
    end: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    fun LocalDate.millis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start.millis(),
        initialSelectedEndDateMillis = end.millis(),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Full-screen page, so no dimming, and system bar icons that match the theme.
        val view = LocalView.current
        val dark = AppTheme.colors.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = state.selectedStartDateMillis != null,
                        onClick = {
                            val s = state.selectedStartDateMillis?.date() ?: return@TextButton
                            // A single tapped day means a one-day budget.
                            onConfirm(s, state.selectedEndDateMillis?.date() ?: s)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

/**
 * Shows the integer part of a typed amount with the locale's grouping ("30000.5" → "30,000.5",
 * or "1,00,000" in Indian English) while the underlying text stays plain digits.
 */
class AmountGroupingTransformation(locale: Locale) : VisualTransformation {
    private val format = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val dot = raw.indexOf('.').let { if (it < 0) raw.length else it }
        val intPart = raw.substring(0, dot)
        val grouped = intPart.toLongOrNull()?.let(format::format) ?: intPart
        val out = grouped + raw.substring(dot)

        // originalToTransformed[i] = position in `out` of the i-th original character.
        val o2t = IntArray(raw.length + 1)
        var t = 0
        for (i in intPart.indices) {
            while (t < grouped.length && !grouped[t].isDigit()) t++
            o2t[i] = t
            t++
        }
        val tail = grouped.length
        for (i in dot..raw.length) o2t[i] = tail + (i - dot)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int {
                var best = 0
                for (i in 0..raw.length) if (o2t[i] <= offset) best = i
                return best
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
