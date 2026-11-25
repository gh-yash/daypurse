package bizappz.daypurse.domain.money

import java.math.BigDecimal

/**
 * Text-entry rules for amounts typed on the in-app keypad. Amounts are kept as plain strings
 * while typing ("135", "12.5") and converted to minor units (×100) on save.
 */
object AmountInput {
    private const val MAX_INTEGER_DIGITS = 9
    private const val MAX_DECIMALS = 2

    fun append(current: String, key: Char): String {
        if (key == '.') {
            return when {
                current.contains('.') -> current
                current.isEmpty() -> "0."
                else -> "$current."
            }
        }
        require(key.isDigit()) { "Unsupported key $key" }
        val dot = current.indexOf('.')
        if (dot >= 0) {
            return if (current.length - dot - 1 >= MAX_DECIMALS) current else current + key
        }
        if (current == "0") return key.toString()
        if (current.length >= MAX_INTEGER_DIGITS) return current
        return current + key
    }

    fun backspace(current: String): String = current.dropLast(1)

    /** Cleans free text from the system keyboard by replaying it through [append]. */
    fun sanitize(text: String): String =
        text.filter { it.isDigit() || it == '.' }.fold("") { acc, c -> append(acc, c) }

    /** "135.5" → 13550. Empty or invalid → 0. */
    fun toMinor(text: String): Long =
        text.trimEnd('.').toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L

    /** 13550 → "135.5", 13500 → "135". */
    fun fromMinor(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString()
}
