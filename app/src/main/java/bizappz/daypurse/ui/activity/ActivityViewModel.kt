package bizappz.daypurse.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityEntry
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.di.AppContainer
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class ActivityRange(val days: Long?) { TODAY(0), WEEK(6), MONTH(29), ALL(null) }

data class ActivityFilter(
    val kinds: Set<ActivityKind> = emptySet(),
    val range: ActivityRange = ActivityRange.ALL,
    val query: String = "",
) {
    val isFiltered get() = kinds.isNotEmpty() || range != ActivityRange.ALL || query.isNotBlank()
}

/** An entry plus whether its deleted spend can still be brought back. */
data class ActivityRow(val entry: ActivityEntry, val restorable: Boolean)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ActivityViewModel(private val c: AppContainer) : ViewModel() {
    private val log = c.activityLog
    val filter = MutableStateFlow(ActivityFilter())

    val retentionDays: StateFlow<Int> = c.settingsRepository.settings.map { it.activityRetentionDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityLog.DEFAULT_RETENTION_DAYS)

    val rows: StateFlow<List<ActivityRow>?> = combine(filter.debounce(150), c.todayProvider.today) { f, today -> f to today }
        .flatMapLatest { (f, today) ->
            val since = f.range.days?.let { today.minusDays(it).startMillis() } ?: 0L
            log.observe(since, f.kinds, f.query)
        }
        .mapLatest { entries ->
            entries.map { e ->
                val restorable = e.kind == ActivityKind.SPEND && e.action == ActivityAction.DELETED &&
                    e.snapshot != null && ActivityLog.expenseFrom(e.snapshot)?.let { c.expenseRepository.get(it.id) } == null
                ActivityRow(e, restorable)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleKind(kind: ActivityKind) = filter.value.let { f ->
        filter.value = f.copy(kinds = if (kind in f.kinds) f.kinds - kind else f.kinds + kind)
    }
    fun clearKinds() { filter.value = filter.value.copy(kinds = emptySet()) }
    fun setRange(range: ActivityRange) { filter.value = filter.value.copy(range = range) }
    fun setQuery(query: String) { filter.value = filter.value.copy(query = query) }

    /** Brings back a deleted spend. Returns false if there is no budget to put it in. */
    suspend fun restore(entry: ActivityEntry): Boolean {
        val snapshot = entry.snapshot?.let(ActivityLog::expenseFrom) ?: return false
        val primary = c.budgetEngine.primary()?.id
        val restored = c.expenseRepository.restoreDeleted(snapshot, primary) ?: return false
        if (restored.date.isBefore(c.todayProvider.today.value)) c.budgetEngine.recalculateCarryOver(restored.budgetId)
        return true
    }

    fun setRetention(days: Int) = viewModelScope.launch {
        c.settingsRepository.setActivityRetention(days)
        log.prune(days)
    }

    fun clear() = viewModelScope.launch { log.clear() }

    private fun LocalDate.startMillis() = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
