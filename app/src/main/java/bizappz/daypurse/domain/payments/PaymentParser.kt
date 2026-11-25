package bizappz.daypurse.domain.payments

import java.math.BigDecimal

/**
 * Reads a payment out of a notification's text, entirely on the device. Deliberately strict:
 * it is better to miss a payment (the user can still add it) than to suggest one that wasn't
 * made. Money received, OTPs, requests, reminders and failed payments are ignored.
 */
object PaymentParser {
    data class Parsed(val amount: Long, val payee: String?)

    /** Where a notification came from; message apps need stronger signs that it's a bank alert. */
    enum class SourceKind { UPI_APP, BANK_APP, SMS }

    private val AMOUNT = Regex("""(?:₹|\brs\.?|\binr)\s*([0-9]{1,3}(?:,[0-9]{2,3})+|[0-9]+)(\.[0-9]{1,2})?""", RegexOption.IGNORE_CASE)

    private val DEBIT = Regex(
        """\b(paid|sent|debited|spent|payment of|payment to|purchase|transferred to|withdrawn|debit(?:ed)? from|txn of)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val REJECT = Regex(
        """\b(otp|one time password|verification code|refund(?:ed)?|cashback|reversed|reversal|request(?:ed|s)?|collect|""" +
            """due|bill (?:is )?generated|statement|reminder|failed|declined|unsuccessful|not successful|pending|""" +
            """will be debited|scheduled|upcoming|mandate|autopay (?:set|created)|offer|reward|cashback offer|expire[sd]?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Signs a text message is from a bank (not a person saying "I paid ₹200"). */
    private val BANK_MARKERS = Regex("""\b(a/c|acct|account|card|upi|ref(?:erence)?|txn|vpa|debited)\b""", RegexOption.IGNORE_CASE)

    private const val STOP = """(?=\s+(?:on|via|using|from|for|ref|upi|txn|at|with|dated|by)\b|\s*[.,(\n|]|\s+-\s|$)"""
    private val TO_VPA = Regex("""\bto\s+vpa\s+([\w.\-]+@[\w.\-]+)(?:\s+([A-Za-z][A-Za-z .&'-]{1,40}?))?$STOP""", RegexOption.IGNORE_CASE)
    private val TO = Regex("""\bto\s+(?!(?:your|you|a/c|account|acct)\b)([^\n]{2,60}?)$STOP""", RegexOption.IGNORE_CASE)
    private val AT = Regex("""\bat\s+([^\n]{2,40}?)$STOP""", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?, source: SourceKind): Parsed? {
        val full = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }.joinToString(" | ")
        if (full.isEmpty()) return null
        if (REJECT.containsMatchIn(full) || !DEBIT.containsMatchIn(full)) return null
        if (source == SourceKind.SMS && !BANK_MARKERS.containsMatchIn(full)) return null
        val amount = AMOUNT.find(full)?.let { toMinor(it.groupValues[1] + it.groupValues[2]) }?.takeIf { it > 0 } ?: return null
        return Parsed(amount, payeeIn(full))
    }

    private fun payeeIn(text: String): String? {
        TO_VPA.find(text)?.let { m -> return clean(m.groupValues[2].ifBlank { m.groupValues[1] }) }
        return (TO.find(text) ?: AT.find(text))?.groupValues?.get(1)?.let(::clean)
    }

    private fun clean(raw: String): String? {
        val s = raw.trim().trimEnd('.', ',', ':', ';', '-', '!').trim()
        if (s.length < 2) return null
        // An account or card number is not a payee.
        if (s.count(Char::isDigit) > s.length / 2 && '@' !in s) return null
        // Bank alerts are often ALL CAPS; "RAVI STORES" reads better as "Ravi Stores".
        val words = if (s.any(Char::isLowerCase) || '@' in s) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
        return words.take(40)
    }

    private fun toMinor(number: String): Long? =
        number.replace(",", "").toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.DOWN)
            ?.takeIf { it < BigDecimal(100_000_000_00) }?.toLong()
}
