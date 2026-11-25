package bizappz.daypurse.domain.payments

import bizappz.daypurse.domain.payments.PaymentParser.SourceKind

/** An app whose notifications may announce a payment. */
data class PaymentSource(val packageName: String, val label: String, val kind: SourceKind)

/**
 * Apps the payment reader listens to. Only installed ones are shown in Settings, and each can be
 * switched off. Chat apps are left out on purpose: people write "I paid ₹200" to each other.
 */
object PaymentSources {
    val known: List<PaymentSource> = listOf(
        // UPI apps
        PaymentSource("com.google.android.apps.nbu.paisa.user", "Google Pay", SourceKind.UPI_APP),
        PaymentSource("com.phonepe.app", "PhonePe", SourceKind.UPI_APP),
        PaymentSource("net.one97.paytm", "Paytm", SourceKind.UPI_APP),
        PaymentSource("in.org.npci.upiapp", "BHIM", SourceKind.UPI_APP),
        PaymentSource("in.amazon.mShop.android.shopping", "Amazon", SourceKind.UPI_APP),
        PaymentSource("com.dreamplug.androidapp", "CRED", SourceKind.UPI_APP),
        PaymentSource("com.mobikwik_new", "MobiKwik", SourceKind.UPI_APP),
        PaymentSource("com.freecharge.android", "Freecharge", SourceKind.UPI_APP),
        PaymentSource("com.naviapp", "Navi", SourceKind.UPI_APP),
        PaymentSource("money.super.payments", "super.money", SourceKind.UPI_APP),
        // Bank apps
        PaymentSource("com.sbi.lotusintouch", "YONO SBI", SourceKind.BANK_APP),
        PaymentSource("com.sbi.upi", "BHIM SBI Pay", SourceKind.BANK_APP),
        PaymentSource("com.snapwork.hdfc", "HDFC Bank", SourceKind.BANK_APP),
        PaymentSource("com.csam.icici.bank.imobile", "ICICI iMobile", SourceKind.BANK_APP),
        PaymentSource("com.axis.mobile", "Axis Mobile", SourceKind.BANK_APP),
        PaymentSource("com.msf.kbank.mobile", "Kotak", SourceKind.BANK_APP),
        PaymentSource("com.bankofbaroda.mconnect", "Bank of Baroda", SourceKind.BANK_APP),
        PaymentSource("com.idfcfirstbank.optimus", "IDFC FIRST", SourceKind.BANK_APP),
        // Text messages (bank SMS)
        PaymentSource("com.google.android.apps.messaging", "Messages", SourceKind.SMS),
        PaymentSource("com.samsung.android.messaging", "Samsung Messages", SourceKind.SMS),
        PaymentSource("com.android.mms", "Messaging", SourceKind.SMS),
        PaymentSource("com.truecaller", "Truecaller", SourceKind.SMS),
    )

    private val byPackage = known.associateBy { it.packageName }

    fun find(packageName: String): PaymentSource? = byPackage[packageName]
}
