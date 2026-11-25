package bizappz.daypurse.surfaces

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import bizappz.daypurse.R

/**
 * Long-press the app icon: "Add spend" plus the top quick-add spends (one tap logs them, with
 * Undo in the app). Dynamic shortcuts, rebuilt whenever the data changes.
 */
object Shortcuts {
    fun update(context: Context, data: SurfaceData) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val add = ShortcutInfo.Builder(context, "add_spend")
            .setShortLabel(context.getString(R.string.shortcut_add))
            .setLongLabel(context.getString(R.string.shortcut_add_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_add))
            .setIntent(LaunchActions.intent(context, LaunchAction.AddSpend))
            .setRank(0)
            .build()
        val room = (manager.maxShortcutCountPerActivity - 1).coerceIn(0, MAX_QUICK)
        val quick = data.quick.take(room).mapIndexed { i, q ->
            // With app lock on, don't show amounts on the home screen.
            val label = if (data.locked) q.label.ifEmpty { context.getString(R.string.shortcut_add) }
            else listOf(q.label, data.money.format(q.amount)).filter { it.isNotEmpty() }.joinToString(" ")
            ShortcutInfo.Builder(context, "quick_${q.amount}_${q.categoryId ?: 0}")
                .setShortLabel(label.take(24))
                .setLongLabel(label)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_bolt))
                .setIntent(LaunchActions.intent(context, LaunchAction.QuickSpend(q.amount, q.categoryId, q.note)))
                .setRank(i + 1)
                .build()
        }
        runCatching { manager.dynamicShortcuts = listOf(add) + quick }
    }

    private const val MAX_QUICK = 3
}
