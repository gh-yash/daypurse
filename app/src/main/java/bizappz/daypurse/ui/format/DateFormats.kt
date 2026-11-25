package bizappz.daypurse.ui.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware date strings used across the UI. */
object DateFormats {
    private val mediumFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    // Formatters are cached per locale, so a language change while the app runs is picked up.
    private class Set(val locale: Locale) {
        val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
        val shortWeekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    }

    @Volatile private var cached = Set(Locale.getDefault())
    private val formats: Set
        get() = Locale.getDefault().let { l -> cached.takeIf { it.locale == l } ?: Set(l).also { cached = it } }

    fun medium(date: LocalDate): String = date.format(mediumFmt.withLocale(Locale.getDefault()))
    fun dayMonth(date: LocalDate): String = date.format(formats.dayMonth)
    fun long(date: LocalDate): String = date.format(formats.weekdayDayMonth)
    fun short(date: LocalDate): String = date.format(formats.shortWeekdayDayMonth)
    fun range(start: LocalDate, end: LocalDate): String =
        if (start == end) dayMonth(start) else "${dayMonth(start)} – ${dayMonth(end)}"
}

/** The UI locale, observed so the UI recomposes if the user changes the system language. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun currentLocale(): Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
