package bizappz.daypurse.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats minor-unit amounts for display. With no currency set, amounts are plain grouped
 * numbers. Decimals are shown only when the amount has a fractional part ("₹135" vs "₹135.50").
 * Not thread-safe: use from the UI thread.
 */
class MoneyFormatter(currencyCode: String?, private val locale: Locale = Locale.getDefault()) {
    val currency: Currency? = currencyCode?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
    val symbol: String? = currency?.getSymbol(locale)

    private val whole = newFormat(0)
    private val fractional = newFormat(2)

    fun format(minor: Long): String {
        val f = if (minor % 100 == 0L) whole else fractional
        return f.format(minor / 100.0)
    }

    private fun newFormat(digits: Int): NumberFormat =
        (if (currency != null) NumberFormat.getCurrencyInstance(locale).also { it.currency = currency }
        else NumberFormat.getNumberInstance(locale)).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }

    companion object {
        /** Real currencies only (skips metals, test and fund codes that have no fraction digits). */
        fun availableCurrencies(locale: Locale = Locale.getDefault()): List<Currency> =
            Currency.getAvailableCurrencies()
                .filter { it.defaultFractionDigits >= 0 }
                .sortedBy { it.getDisplayName(locale) }

        fun localeCurrencyCode(locale: Locale = Locale.getDefault()): String? =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
    }
}

val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter(null) }
