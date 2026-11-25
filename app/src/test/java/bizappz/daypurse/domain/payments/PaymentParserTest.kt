package bizappz.daypurse.domain.payments

import bizappz.daypurse.domain.payments.PaymentParser.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {
    private fun upi(title: String?, text: String?) = PaymentParser.parse(title, text, SourceKind.UPI_APP)
    private fun sms(text: String) = PaymentParser.parse("AX-HDFCBK", text, SourceKind.SMS)

    @Test
    fun readsUpiAppPayments() {
        assertEquals(PaymentParser.Parsed(13500, "Ravi Stores"), upi("₹135 paid to Ravi Stores", "Payment successful"))
        assertEquals(PaymentParser.Parsed(50000, "John Doe"), upi("Sent ₹500 to John Doe", null))
        assertEquals(PaymentParser.Parsed(12050, "Ravi Stores"), upi("Payment successful", "Paid Rs.120.50 to Ravi Stores via UPI"))
        assertEquals(PaymentParser.Parsed(125000, null), upi("Paid ₹1,250", null))
    }

    @Test
    fun readsBankTextMessages() {
        assertEquals(
            PaymentParser.Parsed(13500, "Ravi Stores"),
            sms("Rs.135.00 debited from A/c XX1234 on 23-11-26 to VPA ravistores@okaxis RAVI STORES (UPI Ref 6331). Avl Bal Rs 10,000"),
        )
        assertEquals(
            PaymentParser.Parsed(49900, "Amazon"),
            sms("Spent Rs 499 on HDFC Bank Card x1234 at AMAZON on 2026-11-23. Avl Limit: Rs 49,501"),
        )
        assertEquals(
            PaymentParser.Parsed(150000, "ravi@okicici"),
            sms("INR 1,500.00 debited from a/c **1234 to VPA ravi@okicici on 23Nov26"),
        )
    }

    @Test
    fun ignoresWhatIsNotASpend() {
        assertNull(upi("₹500 received from Asha", null))
        assertNull(sms("Rs 500 credited to A/c XX1234 from VPA asha@ybl"))
        assertNull(sms("123456 is your OTP for a payment of Rs 500. Do not share."))
        assertNull(upi("Asha requested ₹200", "Pay now"))
        assertNull(upi("Payment of ₹135 to Ravi Stores failed", null))
        assertNull(upi("Refund of ₹135 processed", null))
        assertNull(sms("Your bill of Rs 799 is due on 25 Nov"))
        assertNull(upi("Get 10% cashback on your next payment", null))
        assertNull(upi("Paid", "No amount here"))
    }

    @Test
    fun textMessagesFromPeopleAreIgnored() {
        // A friend's message, not a bank alert.
        assertNull(PaymentParser.parse("Asha", "I paid ₹200 to the cab driver", SourceKind.SMS))
        // But a UPI app saying the same is trusted.
        assertEquals(20000L, upi("Paid ₹200 to Cab Driver", null)?.amount)
    }
}
