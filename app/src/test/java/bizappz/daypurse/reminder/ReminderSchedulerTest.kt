package bizappz.daypurse.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 20, h, m, 30, 0, zone)
    private fun millis(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun laterToday() = assertEquals(millis(20, 21, 0), ReminderScheduler.nextTrigger(21 * 60, at(9, 0)))
    @Test fun alreadyPassedGoesToTomorrow() = assertEquals(millis(21, 21, 0), ReminderScheduler.nextTrigger(21 * 60, at(22, 0)))
    @Test fun sameMinuteGoesToTomorrow() = assertEquals(millis(21, 21, 0), ReminderScheduler.nextTrigger(21 * 60, at(21, 0)))
}
