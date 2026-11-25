package bizappz.daypurse.di

import android.content.Context
import bizappz.daypurse.data.backup.BackupManager
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.repository.CategoryRepository
import bizappz.daypurse.data.repository.ExpenseRepository
import bizappz.daypurse.data.settings.SettingsRepository
import bizappz.daypurse.domain.TodayProvider
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.budget.BudgetEngine
import bizappz.daypurse.domain.payments.PaymentInbox
import bizappz.daypurse.domain.quick.QuickLogger
import bizappz.daypurse.domain.recurring.RecurringSpends
import bizappz.daypurse.reminder.ReminderScheduler
import bizappz.daypurse.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import bizappz.daypurse.surfaces.LaunchAction

/**
 * Manual dependency container (no Hilt/Dagger — smaller APK, faster builds).
 * Everything is created lazily so app start only pays for what the first screen uses.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For work that outlives a screen (catching up recurring spends, refreshing widgets). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val activityLog by lazy { ActivityLog(database) }

    val settingsRepository by lazy { SettingsRepository(appContext, activityLog) }
    val categoryRepository by lazy { CategoryRepository(database.categoryDao(), activityLog) }
    val expenseRepository by lazy { ExpenseRepository(database, activityLog) }
    val budgetEngine by lazy { BudgetEngine(database, activityLog) }
    val recurringSpends by lazy { RecurringSpends(database, expenseRepository, budgetEngine, activityLog) }
    val paymentInbox by lazy { PaymentInbox(database, expenseRepository, activityLog) }
    val quickLogger by lazy { QuickLogger(expenseRepository, budgetEngine) }
    val todayProvider = TodayProvider()
    val backupManager by lazy { BackupManager(database, activityLog) }
    val reminderScheduler by lazy { ReminderScheduler(appContext) }
    val appLock = AppLock()

    /** Set when the app is opened from a shortcut, widget, tile or notification; Home consumes it. */
    val launchActions = MutableStateFlow<LaunchAction?>(null)
}
