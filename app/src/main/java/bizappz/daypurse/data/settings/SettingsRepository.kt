package bizappz.daypurse.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bizappz.daypurse.data.model.Accent
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.AppSettings
import bizappz.daypurse.data.model.SettingKey
import bizappz.daypurse.data.model.ThemeMode
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, private val log: ActivityLog) {
    private val store = context.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val CURRENCY = stringPreferencesKey("currency_code")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
        val REMINDER = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MIN = intPreferencesKey("reminder_minutes")
        val REMINDER_SKIP = booleanPreferencesKey("reminder_skip_if_logged")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val HIDDEN_QUICK = stringSetPreferencesKey("hidden_quick_spends")
        val PAYMENTS = booleanPreferencesKey("payments_enabled")
        val PAYMENTS_NOTIFY = booleanPreferencesKey("payments_notify")
        val PAYMENT_SOURCES_OFF = stringSetPreferencesKey("payment_sources_off")
        val RETENTION = intPreferencesKey("activity_retention_days")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
                accent = p[Keys.ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: d.accent,
                pureBlack = p[Keys.PURE_BLACK] ?: d.pureBlack,
                currencyCode = p[Keys.CURRENCY]?.takeIf { it.isNotBlank() },
                onboardingCompleted = p[Keys.ONBOARDED] ?: d.onboardingCompleted,
                reminderEnabled = p[Keys.REMINDER] ?: d.reminderEnabled,
                reminderMinutes = p[Keys.REMINDER_MIN] ?: d.reminderMinutes,
                reminderSkipIfLogged = p[Keys.REMINDER_SKIP] ?: d.reminderSkipIfLogged,
                appLockEnabled = p[Keys.APP_LOCK] ?: d.appLockEnabled,
                hiddenQuickSpends = p[Keys.HIDDEN_QUICK] ?: d.hiddenQuickSpends,
                paymentsEnabled = p[Keys.PAYMENTS] ?: d.paymentsEnabled,
                paymentsNotify = p[Keys.PAYMENTS_NOTIFY] ?: d.paymentsNotify,
                paymentSourcesOff = p[Keys.PAYMENT_SOURCES_OFF] ?: d.paymentSourcesOff,
                activityRetentionDays = p[Keys.RETENTION] ?: d.activityRetentionDays,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = set(SettingKey.THEME, mode.name) { it[Keys.THEME] = mode.name }
    suspend fun setAccent(accent: Accent) = set(SettingKey.ACCENT, accent.name) { it[Keys.ACCENT] = accent.name }
    suspend fun setPureBlack(enabled: Boolean) = set(SettingKey.PURE_BLACK, enabled) { it[Keys.PURE_BLACK] = enabled }
    suspend fun setCurrency(code: String?) = set(SettingKey.CURRENCY, code ?: "") {
        if (code.isNullOrBlank()) it.remove(Keys.CURRENCY) else it[Keys.CURRENCY] = code
    }
    suspend fun setOnboardingCompleted(done: Boolean) = store.edit { it[Keys.ONBOARDED] = done }
    suspend fun setReminder(enabled: Boolean, minutes: Int) {
        store.edit {
            it[Keys.REMINDER] = enabled
            it[Keys.REMINDER_MIN] = minutes
        }
        log.record(ActivityKind.SETTINGS, ActivityAction.CHANGED, subject = SettingKey.REMINDER.name,
            detail = if (enabled) "%02d:%02d".format(minutes / 60, minutes % 60) else "false")
    }
    suspend fun setReminderSkipIfLogged(on: Boolean) = set(SettingKey.REMINDER_SKIP, on) { it[Keys.REMINDER_SKIP] = on }
    suspend fun setAppLock(enabled: Boolean) = set(SettingKey.APP_LOCK, enabled) { it[Keys.APP_LOCK] = enabled }
    suspend fun setPaymentsEnabled(on: Boolean) = set(SettingKey.PAYMENTS, on) { it[Keys.PAYMENTS] = on }
    suspend fun setPaymentsNotify(on: Boolean) = set(SettingKey.PAYMENTS_NOTIFY, on) { it[Keys.PAYMENTS_NOTIFY] = on }
    suspend fun setPaymentSource(packageName: String, label: String, on: Boolean) =
        set(SettingKey.PAYMENT_SOURCE, "$label: $on") {
            val off = it[Keys.PAYMENT_SOURCES_OFF].orEmpty()
            it[Keys.PAYMENT_SOURCES_OFF] = if (on) off - packageName else off + packageName
        }
    suspend fun setActivityRetention(days: Int) = set(SettingKey.ACTIVITY_RETENTION, days) { it[Keys.RETENTION] = days }

    /** Hides or shows a quick-spend suggestion (not worth a history line). */
    suspend fun setQuickSpendHidden(key: String, hidden: Boolean) = store.edit {
        val set = it[Keys.HIDDEN_QUICK].orEmpty()
        it[Keys.HIDDEN_QUICK] = if (hidden) set + key else set - key
    }.also { log.notifyChanged() }

    suspend fun clear() = store.edit { it.clear() }

    private suspend fun set(key: SettingKey, value: Any, edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val prefs = store.edit(edit)
        // Choices made during first-time setup aren't "changes" worth a history line.
        if (prefs[Keys.ONBOARDED] == true) {
            log.record(ActivityKind.SETTINGS, ActivityAction.CHANGED, subject = key.name, detail = value.toString())
        }
    }
}
