package bizappz.daypurse.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The current date as a flow. MainActivity refreshes it on resume and at midnight while the app
 * is visible; nothing ticks in the background.
 */
class TodayProvider {
    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refresh() {
        _today.value = LocalDate.now()
    }

    fun millisUntilTomorrow(): Long {
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, next).toMillis().coerceAtLeast(0) + 500
    }
}
