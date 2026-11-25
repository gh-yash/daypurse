package bizappz.daypurse.reminder

import android.Manifest
import android.os.Build
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.RemoteInput
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.MainActivity
import bizappz.daypurse.R
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.ui.format.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires the daily reminder (private, explicit intent only). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val pending = goAsync()
        val container = (context.applicationContext as DayPurseApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = container.settingsRepository.settings.first()
                if (!settings.reminderEnabled) return@launch
                val today = LocalDate.now()
                container.recurringSpends.catchUp(today)
                val state = container.budgetEngine.observePrimaryState(today).first()
                val paused = state is BudgetState.Active && state.paused
                // No nudge while the main budget is paused, or (if chosen) once something was logged today.
                val loggedToday = settings.reminderSkipIfLogged && container.expenseRepository.anyOn(today)
                if (!paused && !loggedToday) post(context, state, MoneyFormatter(settings.currencyCode))
                ReminderScheduler(context).schedule(settings.reminderMinutes)
            } finally {
                pending.finish()
            }
        }
    }

    private fun post(context: Context, state: BudgetState, money: MoneyFormatter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = when (state) {
            is BudgetState.Active -> if (!state.budget.dailySplit) {
                context.getString(R.string.reminder_text_plain)
            } else {
                val day = state.day
                if (day.isOverDailyLimit) context.getString(R.string.reminder_text_over, money.format(-day.leftToday))
                else context.getString(R.string.reminder_text_left, money.format(day.leftToday))
            }
            else -> context.getString(R.string.reminder_text_plain)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        ReminderScheduler(context).ensureChannel()
        // Reply right from the notification: "120 food".
        val reply = RemoteInput.Builder(ReminderReplyReceiver.KEY_TEXT)
            .setLabel(context.getString(R.string.reminder_reply_hint))
            .build()
        val replyIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ReminderReplyReceiver::class.java).setAction(ReminderReplyReceiver.ACTION_REPLY),
            // Mutable so Android can attach the typed text; explicit intent to our own receiver.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val logAction = Notification.Action.Builder(null, context.getString(R.string.reminder_action_log), replyIntent)
            .addRemoteInput(reply)
            .setAllowGeneratedReplies(false)
            .build()
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_account_balance_wallet)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(logAction)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "in.bizappz.daypurse.action.REMIND"
        const val NOTIFICATION_ID = 1
    }
}

/**
 * Re-arms the reminder after reboot, app update or clock/time-zone changes (alarms don't
 * survive those). Exported because these are system broadcasts; only protected actions handled.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val container = (context.applicationContext as DayPurseApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = container.settingsRepository.settings.first()
                if (s.reminderEnabled) ReminderScheduler(context).schedule(s.reminderMinutes)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
