package bizappz.daypurse.payments

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.R
import bizappz.daypurse.data.model.DetectedPayment
import bizappz.daypurse.data.model.InboxStatus
import bizappz.daypurse.surfaces.LaunchAction
import bizappz.daypurse.surfaces.LaunchActions
import bizappz.daypurse.ui.format.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** "₹135 to Ravi Stores — Log it as Food?" with Log / Ignore, for a detected payment. */
object PaymentNotifier {
    private const val CHANNEL_ID = "payments"
    private const val ID_BASE = 10_000

    fun notificationId(inboxId: Long) = ID_BASE + (inboxId % 1_000_000).toInt()

    suspend fun ask(context: Context, item: DetectedPayment) {
        if (!canPost(context)) return
        val container = (context.applicationContext as DayPurseApplication).container
        val settings = container.settingsRepository.settings.first()
        val money = MoneyFormatter(settings.currencyCode)
        val suggested = container.paymentInbox.suggestedCategory(item)?.let { container.categoryRepository.get(it) }
        ensureChannel(context)

        val amount = money.format(item.amount)
        val title = item.payee?.let { context.getString(R.string.payments_notif_title, amount, it) }
            ?: context.getString(R.string.payments_notif_title_no_payee, amount)
        val text = suggested?.let { context.getString(R.string.payments_notif_text, it.name, item.sourceLabel) }
            ?: context.getString(R.string.payments_notif_text_choose, item.sourceLabel)
        val review = PendingIntent.getActivity(
            context, requestCode(item.id, 0), LaunchActions.intent(context, LaunchAction.ReviewPayment(item.id)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val log = if (suggested != null) {
            Notification.Action.Builder(null, context.getString(R.string.payments_notif_log), broadcast(context, item.id, PaymentActionReceiver.ACTION_LOG, 1)).build()
        } else {
            Notification.Action.Builder(null, context.getString(R.string.payments_notif_choose), review).build()
        }
        val ignore = Notification.Action.Builder(null, context.getString(R.string.payments_notif_ignore),
            broadcast(context, item.id, PaymentActionReceiver.ACTION_IGNORE, 2)).build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_account_balance_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(review)
            .addAction(log)
            .addAction(ignore)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(item.detectedAt)
            .setShowWhen(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(item.id), notification)
    }

    /** Briefly confirms a payment logged from the notification. */
    fun confirmLogged(context: Context, inboxId: Long, text: String) {
        if (!canPost(context)) return
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(text)
            .setTimeoutAfter(4_000)
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(inboxId), n)
    }

    fun cancel(context: Context, inboxId: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(inboxId))

    /** Removes "ask" notifications for payments already handled in the app. */
    suspend fun cancelHandled(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val inbox = (context.applicationContext as DayPurseApplication).container.paymentInbox
        nm.activeNotifications.filter { it.id >= ID_BASE && it.notification.channelId == CHANNEL_ID && it.notification.actions != null }
            .forEach { sbn ->
                val waiting = inbox.waiting.first().any { notificationId(it.id) == sbn.id }
                if (!waiting) nm.cancel(sbn.id)
            }
    }

    /** The runtime notification permission exists only on Android 13+. */
    private fun canPost(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.payments_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun requestCode(inboxId: Long, slot: Int) = ((inboxId % 100_000_000).toInt() * 4) + slot

    private fun broadcast(context: Context, inboxId: Long, action: String, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(inboxId, slot),
        Intent(context, PaymentActionReceiver::class.java).setAction(action).putExtra(PaymentActionReceiver.EXTRA_ID, inboxId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Log / Ignore buttons on the payment notification (private: explicit intents only). */
class PaymentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0).takeIf { it > 0 } ?: return
        val pending = goAsync()
        val c = (context.applicationContext as DayPurseApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = c.paymentInbox.get(id)?.takeIf { it.status == InboxStatus.NEW }
                if (item == null) { PaymentNotifier.cancel(context, id); return@launch }
                when (intent.action) {
                    ACTION_IGNORE -> { c.paymentInbox.ignore(item); PaymentNotifier.cancel(context, id) }
                    ACTION_LOG -> {
                        val categoryId = c.paymentInbox.suggestedCategory(item)
                        val budgetId = c.budgetEngine.defaultBudgetId(LocalDate.now())
                        if (categoryId == null || budgetId == null) { PaymentNotifier.cancel(context, id); return@launch }
                        c.paymentInbox.logIt(item, item.amount, categoryId, budgetId, null)
                        val money = MoneyFormatter(c.settingsRepository.settings.first().currencyCode)
                        val name = c.categoryRepository.get(categoryId)?.name.orEmpty()
                        PaymentNotifier.confirmLogged(context, id, context.getString(R.string.payments_notif_logged, money.format(item.amount), name))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "in.bizappz.daypurse.action.PAYMENT_LOG"
        const val ACTION_IGNORE = "in.bizappz.daypurse.action.PAYMENT_IGNORE"
        const val EXTRA_ID = "inboxId"
    }
}
