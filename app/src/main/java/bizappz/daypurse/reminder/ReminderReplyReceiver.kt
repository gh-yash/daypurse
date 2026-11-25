package bizappz.daypurse.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.R
import bizappz.daypurse.domain.quick.QuickEntryParser
import bizappz.daypurse.ui.format.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Logs a spend typed into the reminder notification ("120 food"), then replaces the
 * notification with the result so the reply spinner stops.
 */
class ReminderReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        val pending = goAsync()
        val c = (context.applicationContext as DayPurseApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val categories = c.categoryRepository.visible.first()
                val entry = QuickEntryParser.parse(text, categories)
                val message = if (entry == null) {
                    context.getString(R.string.reminder_reply_failed)
                } else {
                    val saved = c.quickLogger.log(entry.amount, entry.categoryId, entry.note, LocalDate.now())
                    if (saved == null) {
                        context.getString(R.string.reminder_no_budget)
                    } else {
                        val money = MoneyFormatter(c.settingsRepository.settings.first().currencyCode)
                        val label = entry.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
                            ?: entry.note ?: context.getString(R.string.category_none)
                        context.getString(R.string.reminder_logged, money.format(entry.amount), label)
                    }
                }
                ReminderScheduler(context).ensureChannel()
                val done = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_check)
                    .setContentTitle(message)
                    .setTimeoutAfter(if (entry == null) 10_000 else 4_000)
                    .setOnlyAlertOnce(true)
                    .build()
                context.getSystemService(NotificationManager::class.java).notify(ReminderReceiver.NOTIFICATION_ID, done)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "in.bizappz.daypurse.action.REMINDER_REPLY"
        const val KEY_TEXT = "text"
    }
}
