package bizappz.daypurse.data.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Curated accent colours; the UI maps each to a full colour scheme. Stored by name. */
enum class Accent { EMERALD, TEAL, OCEAN, VIOLET, ROSE, AMBER }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.EMERALD,
    val pureBlack: Boolean = false,
    /** ISO 4217 code, or null to show plain numbers. */
    val currencyCode: String? = null,
    val onboardingCompleted: Boolean = false,
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight, e.g. 21:00 -> 1260. */
    val reminderMinutes: Int = 21 * 60,
    /** Skip the reminder on days something was already logged. */
    val reminderSkipIfLogged: Boolean = true,
    val appLockEnabled: Boolean = false,
    /** Quick spends the user removed from suggestions, as "amount:categoryId" keys. */
    val hiddenQuickSpends: Set<String> = emptySet(),
    /** Read payment notifications (needs notification access, granted in system settings). */
    val paymentsEnabled: Boolean = false,
    /** Also ask with our own notification, not just the list on Home. */
    val paymentsNotify: Boolean = true,
    /** Packages the user switched off. */
    val paymentSourcesOff: Set<String> = emptySet(),
    /** Days of activity history to keep; 0 = forever. */
    val activityRetentionDays: Int = 90,
)

/** Setting names used in the activity history. */
enum class SettingKey { THEME, ACCENT, PURE_BLACK, CURRENCY, REMINDER, REMINDER_TIME, REMINDER_SKIP, APP_LOCK, PAYMENTS, PAYMENTS_NOTIFY, PAYMENT_SOURCE, ACTIVITY_RETENTION }

fun quickSpendKey(amount: Long, categoryId: Long?) = "$amount:${categoryId ?: ""}"
