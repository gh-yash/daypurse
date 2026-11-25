package bizappz.daypurse.surfaces

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Something to do when the app opens from a shortcut, widget, tile or notification. */
sealed interface LaunchAction {
    /** Open the add-spend sheet. */
    data object AddSpend : LaunchAction

    /** Log this spend now (with Undo). */
    data class QuickSpend(val amount: Long, val categoryId: Long?, val note: String?) : LaunchAction

    /** Open a detected payment to log it. */
    data class ReviewPayment(val inboxId: Long) : LaunchAction
}

/**
 * Intents for [LaunchAction]s. They target `QuickActions`, a non-exported alias of the main
 * activity, so only this app (its shortcuts, widget, tile and notifications) can trigger them;
 * another app can't make it log spends.
 */
object LaunchActions {
    private const val ACTION_ADD = "in.bizappz.daypurse.action.ADD_SPEND"
    private const val ACTION_QUICK = "in.bizappz.daypurse.action.QUICK_SPEND"
    private const val ACTION_REVIEW = "in.bizappz.daypurse.action.REVIEW_PAYMENT"
    private const val EXTRA_AMOUNT = "amount"
    private const val EXTRA_CATEGORY = "categoryId"
    private const val EXTRA_NOTE = "note"
    private const val EXTRA_INBOX = "inboxId"

    fun alias(context: Context) = ComponentName(context.packageName, "bizappz.daypurse.QuickActions")

    fun intent(context: Context, action: LaunchAction): Intent {
        val i = Intent().setComponent(alias(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return when (action) {
            LaunchAction.AddSpend -> i.setAction(ACTION_ADD)
            is LaunchAction.QuickSpend -> i.setAction(ACTION_QUICK)
                .putExtra(EXTRA_AMOUNT, action.amount)
                .putExtra(EXTRA_CATEGORY, action.categoryId ?: -1L)
                .putExtra(EXTRA_NOTE, action.note)
            is LaunchAction.ReviewPayment -> i.setAction(ACTION_REVIEW).putExtra(EXTRA_INBOX, action.inboxId)
        }
    }

    /** Reads an action, only from an intent that came through the private alias. */
    fun from(context: Context, intent: Intent?): LaunchAction? {
        if (intent == null || intent.component != alias(context)) return null
        return when (intent.action) {
            ACTION_ADD -> LaunchAction.AddSpend
            ACTION_QUICK -> intent.getLongExtra(EXTRA_AMOUNT, 0).takeIf { it > 0 }?.let { amount ->
                LaunchAction.QuickSpend(amount, intent.getLongExtra(EXTRA_CATEGORY, -1).takeIf { it > 0 }, intent.getStringExtra(EXTRA_NOTE))
            }
            ACTION_REVIEW -> intent.getLongExtra(EXTRA_INBOX, 0).takeIf { it > 0 }?.let { LaunchAction.ReviewPayment(it) }
            else -> null
        }
    }
}
