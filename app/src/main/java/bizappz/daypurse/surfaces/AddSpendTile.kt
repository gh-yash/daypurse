package bizappz.daypurse.surfaces

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.R
import bizappz.daypurse.domain.budget.BudgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings tile: "Add spend", with what's left today underneath. */
class AddSpendTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
        scope.launch {
            val data = withContext(Dispatchers.IO) { SurfaceData.load((application as DayPurseApplication).container) }
            val day = (data.state as? BudgetState.Active)?.takeIf { !it.paused && it.budget.dailySplit }?.day
            tile.subtitle = when {
                data.locked || day == null -> null
                day.isOverDailyLimit -> getString(R.string.tile_over, data.money.format(-day.leftToday))
                else -> getString(R.string.tile_left, data.money.format(day.leftToday))
            }
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = LaunchActions.intent(this, LaunchAction.AddSpend)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestRefresh(context: Context) =
            requestListeningState(context, ComponentName(context, AddSpendTile::class.java))
    }
}
