package bizappz.daypurse.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bizappz.daypurse.R
import bizappz.daypurse.data.backup.BackupException
import bizappz.daypurse.data.backup.BackupManager
import bizappz.daypurse.data.backup.BackupMeta
import bizappz.daypurse.data.model.Accent
import bizappz.daypurse.data.model.AppSettings
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.ThemeMode
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.settings.SettingsRepository
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.recurring.RecurringSpends
import bizappz.daypurse.reminder.ReminderScheduler
import bizappz.daypurse.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val settings: AppSettings,
    val budgets: List<Budget>,
    val categoryCount: Int,
    val recurringActive: Int,
)

/** A one-off message for the snackbar: a string resource plus an optional detail. */
data class SettingsMessage(val res: Int, val detail: String? = null)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    engine: BudgetEngine,
    categories: CategoryRepository,
    private val backup: BackupManager,
    private val reminders: ReminderScheduler,
    val appLock: AppLock,
    recurring: RecurringSpends,
) : ViewModel() {
    val state: StateFlow<SettingsUiState?> =
        combine(settingsRepo.settings, engine.budgets, categories.visible, recurring.all) { s, b, c, r ->
            SettingsUiState(s, b, c.size, r.count { !it.paused })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setReminderSkip(on: Boolean) = viewModelScope.launch { settingsRepo.setReminderSkipIfLogged(on) }

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setCurrency(code: String?) = viewModelScope.launch { settingsRepo.setCurrency(code) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setAccent(accent: Accent) = viewModelScope.launch { settingsRepo.setAccent(accent) }
    fun setPureBlack(on: Boolean) = viewModelScope.launch { settingsRepo.setPureBlack(on) }

    fun setReminder(enabled: Boolean, minutes: Int) = viewModelScope.launch {
        settingsRepo.setReminder(enabled, minutes)
        if (enabled) {
            reminders.ensureChannel()
            reminders.schedule(minutes)
        } else {
            reminders.cancel()
        }
    }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        if (enabled) appLock.markUnlocked()
        settingsRepo.setAppLock(enabled)
    }

    fun exportJson(uri: Uri, resolver: ContentResolver) = io(R.string.backup_exported) {
        val json = backup.exportJson(BackupMeta(settingsRepo.settings.first().currencyCode))
        write(resolver, uri, json)
    }

    fun exportCsv(uri: Uri, resolver: ContentResolver) = io(R.string.backup_csv_exported) {
        write(resolver, uri, backup.exportCsv())
    }

    fun importJson(uri: Uri, resolver: ContentResolver) = io(R.string.backup_imported) {
        val text = resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IOException("Can't open file")
        val meta = backup.importJson(text)
        settingsRepo.setCurrency(meta.currencyCode)
        settingsRepo.setOnboardingCompleted(true)
    }

    fun resetAll() = io(R.string.reset_done) {
        backup.resetAll()
        settingsRepo.setOnboardingCompleted(false)
    }

    private fun write(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException("Can't open file")
    }

    private fun io(successRes: Int, block: suspend () -> Unit) = viewModelScope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { block() } }
        _messages.send(
            result.fold(
                onSuccess = { SettingsMessage(successRes) },
                onFailure = { SettingsMessage(R.string.backup_failed, (it as? BackupException)?.message ?: it.message) },
            ),
        )
    }
}
