package bizappz.daypurse.surfaces

import bizappz.daypurse.data.model.quickSpendKey
import bizappz.daypurse.di.AppContainer
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.ui.format.MoneyFormatter
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** A quick-add spend for a shortcut or widget button. */
data class QuickItem(val amount: Long, val categoryId: Long?, val note: String?, val label: String)

/** What the widget, shortcuts and tile show, read once from the database. */
data class SurfaceData(
    val money: MoneyFormatter,
    /** App lock is on: show no amounts outside the app. */
    val locked: Boolean,
    val state: BudgetState,
    val quick: List<QuickItem>,
) {
    companion object {
        suspend fun load(c: AppContainer, today: LocalDate = LocalDate.now()): SurfaceData {
            // The app may not have run today: add recurring spends that fell due first.
            c.recurringSpends.catchUp(today)
            val settings = c.settingsRepository.settings.first()
            val categories = c.categoryRepository.all.first().associateBy { it.id }
            val quick = c.expenseRepository.quickSpends(today).first()
                .filter { quickSpendKey(it.amount, it.categoryId) !in settings.hiddenQuickSpends }
                .filter { it.categoryId?.let(categories::get)?.isHidden != true }
                .map { q -> QuickItem(q.amount, q.categoryId, q.note, q.note ?: q.categoryId?.let(categories::get)?.name ?: "") }
            return SurfaceData(
                money = MoneyFormatter(settings.currencyCode),
                locked = settings.appLockEnabled,
                state = c.budgetEngine.observePrimaryState(today).first(),
                quick = quick,
            )
        }
    }
}
