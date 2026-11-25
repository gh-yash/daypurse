package bizappz.daypurse

import android.app.Application
import bizappz.daypurse.di.AppContainer
import bizappz.daypurse.surfaces.Surfaces
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DayPurseApplication : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val c = container
        c.appScope.launch {
            // Housekeeping once per process start: trim old history and handled payments.
            c.activityLog.prune(c.settingsRepository.settings.first().activityRetentionDays)
            c.paymentInbox.prune()
        }
        c.appScope.launch {
            // Add recurring spends that fell due, now and whenever the day rolls over.
            c.todayProvider.today.collect { today ->
                c.recurringSpends.catchUp(today)
                c.activityLog.notifyChanged()
            }
        }
        c.appScope.launch {
            // Keep the widget, launcher shortcuts and Quick Settings tile in step with the data.
            c.activityLog.changes.debounce(400).collect { Surfaces.refresh(this@DayPurseApplication) }
        }
    }
}
