package bizappz.daypurse.surfaces

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.MainActivity
import bizappz.daypurse.R
import bizappz.daypurse.domain.budget.BudgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 2×1: what's left today and a + button. */
class SmallBudgetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.updateAsync(this, context)
}

/** 4×2: today's budget, progress and quick-add buttons. */
class BudgetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.updateAsync(this, context)
}

/**
 * Draws both widgets with plain RemoteViews (no extra library). Updated when data changes
 * while the app runs, and every 30 minutes by the system otherwise (also catching up recurring
 * spends and the new day).
 */
object Widgets {
    fun updateAsync(receiver: AppWidgetProvider, context: Context) {
        val pending = receiver.goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { updateAll(context) } finally { pending.finish() }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val small = manager.getAppWidgetIds(ComponentName(context, SmallBudgetWidget::class.java))
        val large = manager.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java))
        if (small.isEmpty() && large.isEmpty()) return
        val data = SurfaceData.load((context.applicationContext as DayPurseApplication).container)
        if (small.isNotEmpty()) manager.updateAppWidget(small, render(context, data, large = false))
        if (large.isNotEmpty()) manager.updateAppWidget(large, render(context, data, large = true))
    }

    private fun render(context: Context, data: SurfaceData, large: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, if (large) R.layout.widget_large else R.layout.widget_small)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val add = PendingIntent.getActivity(
            context, 1, LaunchActions.intent(context, LaunchAction.AddSpend),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_body, open)
        views.setOnClickPendingIntent(R.id.widget_add, add)

        val money = data.money
        var label = context.getString(R.string.widget_left_today)
        var amount = ""
        var detail = ""
        var progress = 0
        var negative = false
        when {
            data.locked -> label = context.getString(R.string.widget_locked)
            else -> when (val s = data.state) {
                is BudgetState.Active -> {
                    val day = s.day
                    when {
                        s.paused -> { label = context.getString(R.string.widget_paused); amount = s.budget.name }
                        s.budget.dailySplit -> {
                            negative = day.isOverDailyLimit
                            label = context.getString(if (negative) R.string.widget_over_today else R.string.widget_left_today)
                            amount = money.format(if (negative) -day.leftToday else day.leftToday)
                            detail = context.getString(R.string.widget_spent_of, money.format(day.spentToday), money.format(day.dailyLimit))
                            progress = if (day.dailyLimit <= 0) 1000 else (day.spentToday * 1000 / day.dailyLimit).toInt().coerceIn(0, 1000)
                        }
                        else -> {
                            negative = day.isOverBudget
                            label = context.getString(R.string.widget_left_period)
                            amount = money.format(day.remaining)
                            detail = context.getString(R.string.widget_spent_of, money.format(day.spentInPeriod), money.format(day.totalBudget))
                            progress = (day.usedFraction * 1000).toInt()
                        }
                    }
                }
                else -> label = context.getString(R.string.widget_no_budget)
            }
        }
        views.setTextViewText(R.id.widget_label, label)
        views.setTextViewText(R.id.widget_amount, amount)
        views.setViewVisibility(R.id.widget_amount, if (amount.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextColor(R.id.widget_amount, context.getColor(if (negative) R.color.widget_negative else R.color.widget_text))

        if (large) {
            val showStats = !data.locked && detail.isNotEmpty()
            views.setViewVisibility(R.id.widget_progress, if (showStats) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_detail, if (showStats) View.VISIBLE else View.GONE)
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            views.setTextViewText(R.id.widget_detail, detail)
            val buttons = listOf(R.id.widget_quick_1, R.id.widget_quick_2, R.id.widget_quick_3)
            val quick = if (data.locked) emptyList() else data.quick.take(buttons.size)
            buttons.forEachIndexed { i, id ->
                val q = quick.getOrNull(i)
                views.setViewVisibility(id, if (q == null) View.GONE else View.VISIBLE)
                if (q != null) {
                    views.setTextViewText(id, q.label.ifEmpty { "+" } + "\n" + money.format(q.amount))
                    views.setOnClickPendingIntent(id, PendingIntent.getActivity(
                        context, 10 + i, LaunchActions.intent(context, LaunchAction.QuickSpend(q.amount, q.categoryId, q.note)),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ))
                }
            }
        }
        return views
    }
}
