package bizappz.daypurse.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import bizappz.daypurse.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * One inexact alarm per day (no exact-alarm permission, no background service, no WorkManager).
 * The receiver posts the notification and schedules the next day.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(minutesOfDay: Int) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(minutesOfDay), pendingIntent())
    }

    fun cancel() = alarms.cancel(pendingIntent())

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "daily_reminder"

        /** Next occurrence of [minutesOfDay] strictly after now, in epoch millis. */
        fun nextTrigger(minutesOfDay: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
            val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
            var at = now.with(time).withSecond(0).withNano(0)
            if (!at.isAfter(now)) at = LocalDate.from(now).plusDays(1).atTime(time).atZone(now.zone)
            return at.toInstant().toEpochMilli()
        }
    }
}
