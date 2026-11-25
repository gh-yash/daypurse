package bizappz.daypurse.payments

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import bizappz.daypurse.domain.payments.PaymentSource
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.domain.payments.PaymentParser
import bizappz.daypurse.domain.payments.PaymentSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reads notifications from the supported payment apps (and nothing else) to spot payments.
 * Runs only while the user has granted notification access; everything stays on the phone and
 * only the parsed amount, payee, app and time are stored.
 */
class PaymentListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        val container = (application as DayPurseApplication).container
        scope.launch {
            // Switched off in the app: let Android stop delivering notifications to us.
            if (!container.settingsRepository.settings.first().paymentsEnabled) requestUnbind()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Cheap filter first: almost every notification ends here.
        val source = PaymentSources.find(sbn.packageName) ?: testSource(sbn.packageName) ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = lastMessage(extras)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val parsed = PaymentParser.parse(title, text, source.kind) ?: return
        val container = (application as DayPurseApplication).container
        scope.launch {
            val settings = container.settingsRepository.settings.first()
            if (!settings.paymentsEnabled || source.packageName in settings.paymentSourcesOff) return@launch
            val item = container.paymentInbox.add(parsed.amount, parsed.payee, source, sbn.postTime) ?: return@launch
            if (settings.paymentsNotify) PaymentNotifier.ask(this@PaymentListener, item)
        }
    }

    /**
     * Debug builds only: notifications posted from `adb shell cmd notification post` count as a
     * UPI app, so payment spotting can be tried on an emulator. Never applies to release builds.
     */
    private fun testSource(packageName: String): PaymentSource? =
        if (packageName == "com.android.shell" && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            PaymentSource(packageName, "UPI app", PaymentParser.SourceKind.UPI_APP)
        } else null

    /** Messaging-style notifications (text messages) keep the newest message in a list. */
    private fun lastMessage(extras: Bundle): String? {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        return (messages.lastOrNull() as? Bundle)?.getCharSequence("text")?.toString()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, PaymentListener::class.java)

        fun hasAccess(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))

        /** Ask Android to (re)connect after the user turns reading on. */
        fun sync(context: Context, enabled: Boolean) {
            if (enabled && hasAccess(context)) requestRebind(component(context))
        }
    }
}
