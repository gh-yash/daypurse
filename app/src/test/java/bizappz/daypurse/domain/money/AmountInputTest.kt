package bizappz.daypurse.domain.money

import bizappz.daypurse.ui.format.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AmountInputTest {
    private fun type(keys: String) = keys.fold("") { acc, c -> AmountInput.append(acc, c) }

    @Test fun typingDigits() = assertEquals("135", type("135"))
    @Test fun leadingZeroIsReplaced() = assertEquals("5", type("05"))
    @Test fun dotOnEmptyBecomesZeroDot() = assertEquals("0.5", type(".5"))
    @Test fun onlyOneDot() = assertEquals("1.25", type("1..25"))
    @Test fun maxTwoDecimals() = assertEquals("1.25", type("1.259"))
    @Test fun maxNineIntegerDigits() = assertEquals("123456789", type("1234567890"))

    @Test fun backspace() = assertEquals("13", AmountInput.backspace("135"))

    @Test fun toMinor() {
        assertEquals(13500L, AmountInput.toMinor("135"))
        assertEquals(13550L, AmountInput.toMinor("135.5"))
        assertEquals(13505L, AmountInput.toMinor("135.05"))
        assertEquals(13500L, AmountInput.toMinor("135."))
        assertEquals(0L, AmountInput.toMinor(""))
    }

    @Test fun fromMinor() {
        assertEquals("135", AmountInput.fromMinor(13500))
        assertEquals("135.5", AmountInput.fromMinor(13550))
        assertEquals("0.05", AmountInput.fromMinor(5))
    }

    @Test fun formatterShowsDecimalsOnlyWhenNeeded() {
        val usd = MoneyFormatter("USD", Locale.US)
        assertEquals("$135", usd.format(13500))
        assertEquals("$135.50", usd.format(13550))
        assertEquals("-$12", usd.format(-1200))
        val plain = MoneyFormatter(null, Locale.US)
        assertEquals("1,234.50", plain.format(123450))
        assertEquals("1,000", plain.format(100000))
    }
}
