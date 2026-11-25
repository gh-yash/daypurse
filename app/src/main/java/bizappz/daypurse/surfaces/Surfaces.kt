package bizappz.daypurse.surfaces

import android.content.Context
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.payments.PaymentNotifier

/** Places outside the app that show budget data: widgets, launcher shortcuts, the Quick Settings tile. */
object Surfaces {
    /** Called (debounced) after any data change while the app process runs. */
    suspend fun refresh(context: Context) {
        val data = SurfaceData.load((context.applicationContext as DayPurseApplication).container)
        Shortcuts.update(context, data)
        Widgets.updateAll(context)
        runCatching { AddSpendTile.requestRefresh(context) }
        // A payment logged or ignored in the app no longer needs its notification.
        runCatching { PaymentNotifier.cancelHandled(context) }
    }
}
