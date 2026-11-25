package bizappz.daypurse.domain.budget

import androidx.room.withTransaction
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.domain.activity.ActivityLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

sealed interface BudgetState {
    /** No budget set up yet (show onboarding). */
    data object NoBudget : BudgetState

    /** Starts in the future. */
    data class NotStarted(val budget: Budget) : BudgetState

    /** A one-off (non-recurring) budget whose last period is over. */
    data class Ended(val budget: Budget, val lastPeriod: BudgetPeriod?, val spent: Long) : BudgetState

    /**
     * Running today. When [paused] is true there is no daily limit today; [day] still carries
     * the period totals.
     */
    data class Active(val budget: Budget, val day: DayStatus, val paused: Boolean) : BudgetState
}

val BudgetState.budgetOrNull: Budget?
    get() = when (this) {
        BudgetState.NoBudget -> null
        is BudgetState.NotStarted -> budget
        is BudgetState.Ended -> budget
        is BudgetState.Active -> budget
    }

/**
 * Owns every budget's lifecycle: creating periods as time passes (recurrence), carrying
 * leftovers over, plan edits, pausing, deleting, and the live [BudgetState] for a given day.
 *
 * Nothing here runs in the background: periods are created lazily when the app asks for
 * today's state, so a phone that hasn't opened the app for a month catches up in one go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEngine(private val db: AppDatabase, private val log: ActivityLog) {
    private val budgetDao = db.budgetDao()
    private val expenseDao = db.expenseDao()
    private val mutex = Mutex()

    /** All budgets: primary first, then the user's order. */
    val budgets: Flow<List<Budget>> = budgetDao.observeAll()
    val primaryBudget: Flow<Budget?> = budgetDao.observePrimary()

    fun observeBudget(id: Long): Flow<Budget?> = budgetDao.observe(id)
    fun periodsOf(id: Long): Flow<List<BudgetPeriod>> = budgetDao.observePeriodsOf(id)

    /** Live state of one budget for [today]. Re-collect with a new date when the day changes. */
    fun observeState(budgetId: Long, today: LocalDate): Flow<BudgetState> =
        budgetDao.observe(budgetId).distinctUntilChanged().flatMapLatest { budget ->
            when {
                budget == null -> flowOf(BudgetState.NoBudget)
                today.isBefore(budget.startDate) -> flowOf(BudgetState.NotStarted(budget))
                else -> flow {
                    syncPeriods(today)
                    emitAll(
                        budgetDao.observePeriodOn(budget.id, today).distinctUntilChanged().flatMapLatest { period ->
                            if (period == null) endedState(budget) else activeState(budget, period, today)
                        },
                    )
                }
            }
        }

    fun observePrimaryState(today: LocalDate): Flow<BudgetState> =
        budgetDao.observePrimary().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(BudgetState.NoBudget) else observeState(id, today)
        }

    /** States of all budgets, in display order. */
    fun observeAllStates(today: LocalDate): Flow<List<BudgetState>> =
        budgetDao.observeAll().map { list -> list.map { it.id } }.distinctUntilChanged().flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList()) else combine(ids.map { observeState(it, today) }) { it.toList() }
        }

    private fun activeState(budget: Budget, period: BudgetPeriod, today: LocalDate): Flow<BudgetState> =
        combine(
            // Empty range (start > end) on the first day sums to 0.
            expenseDao.observeTotalBetween(budget.id, period.startDate, today.minusDays(1)),
            expenseDao.observeTotalBetween(budget.id, today, today),
        ) { before, todaySpent ->
            val pausedToday = budget.isPausedOn(today)
            // While paused there is no limit to compute; plain calendar days read better.
            val isPaused: (LocalDate) -> Boolean = if (pausedToday) { _ -> false } else budget::isPausedOn
            BudgetState.Active(
                budget,
                DailyBudgetCalculator.compute(period, today, before, todaySpent, isPaused),
                paused = pausedToday,
            )
        }

    private suspend fun endedState(budget: Budget): Flow<BudgetState> {
        val last = budgetDao.latestPeriod(budget.id) ?: return flowOf(BudgetState.Ended(budget, null, 0))
        return expenseDao.observeTotalBetween(budget.id, last.startDate, last.endDate)
            .map { BudgetState.Ended(budget, last, it) }
    }

    /**
     * Where a new spend goes by default today: a running one-off budget (a trip, a festival),
     * else the main budget, else any running one. Paused budgets only if nothing else runs.
     */
    suspend fun defaultBudgetId(today: LocalDate): Long? {
        val running = observeAllStates(today).first().filterIsInstance<BudgetState.Active>()
        val choices = running.filter { !it.paused }.map { it.budget }.ifEmpty { running.map { it.budget } }
        return (choices.firstOrNull { !it.isPrimary && !it.recurring } ?: choices.firstOrNull { it.isPrimary } ?: choices.firstOrNull())?.id
    }

    suspend fun get(id: Long): Budget? = budgetDao.get(id)
    suspend fun primary(): Budget? = budgetDao.primary()

    /** The period of [budgetId] running on [today], if any. */
    suspend fun currentPeriod(budgetId: Long, today: LocalDate): BudgetPeriod? = budgetDao.periodOn(budgetId, today)

    suspend fun spendCount(budgetId: Long): Int = expenseDao.countIn(budgetId)

    /** Creates any periods missing up to [today] for every budget. Cheap and idempotent. */
    suspend fun syncPeriods(today: LocalDate) = mutex.withLock {
        db.withTransaction { budgetDao.all().forEach { syncLocked(it, today) } }
    }

    private suspend fun syncLocked(budget: Budget, today: LocalDate) {
        if (today.isBefore(budget.startDate)) return
        var latest = budgetDao.latestPeriod(budget.id)
            ?: PeriodCalculator.firstPeriod(budget).let { it.copy(id = budgetDao.insertPeriod(it)) }

        var guard = 0
        while (budget.recurring && latest.endDate.isBefore(today) && guard++ < MAX_CATCH_UP) {
            val next = PeriodCalculator.nextPeriod(budget, latest, carryFrom(budget, latest))
            latest = next.copy(id = budgetDao.insertPeriod(next))
        }
    }

    /** Adds a new budget (the first one becomes the primary "Daily" budget). */
    suspend fun createBudget(budget: Budget, today: LocalDate): Long = mutex.withLock {
        val id = db.withTransaction {
            require(budget.amount > 0) { "Budget amount must be positive" }
            val isPrimary = budget.isPrimary || budgetDao.primary() == null
            val id = budgetDao.insert(
                budget.copy(id = 0, isPrimary = isPrimary, active = true, sortOrder = budgetDao.maxSortOrder() + 1),
            )
            budgetDao.get(id)?.let { syncLocked(it, today) }
            id
        }
        record(ActivityAction.ADDED, budget.name, id, budget.amount)
        id
    }

    /**
     * Structural change to an existing budget (type, start day, dates): its current period is cut
     * short the day before [plan]'s start (amount prorated) so old and new never overlap, and the
     * budget continues with the new plan. Its leftover/deficit moves over when carry-over is on.
     */
    suspend fun restructure(budgetId: Long, plan: Budget, today: LocalDate) = mutex.withLock {
        db.withTransaction {
            require(plan.amount > 0) { "Budget amount must be positive" }
            val old = budgetDao.get(budgetId) ?: return@withTransaction
            val cut = plan.startDate
            var carry = 0L
            budgetDao.deletePeriodsFrom(budgetId, cut)
            budgetDao.periodOn(budgetId, cut.minusDays(1))?.let { current ->
                val truncated = truncate(current, cut.minusDays(1))
                budgetDao.updatePeriod(truncated)
                if (plan.carryOver) {
                    carry = truncated.totalAmount - expenseDao.totalBetween(budgetId, truncated.startDate, truncated.endDate)
                }
            }
            val updated = plan.copy(
                id = budgetId,
                isPrimary = old.isPrimary,
                sortOrder = old.sortOrder,
                createdAt = old.createdAt,
                active = true,
                pauseStart = old.pauseStart,
                pauseEnd = old.pauseEnd,
            )
            budgetDao.update(updated)
            if (!today.isBefore(cut)) {
                budgetDao.insertPeriod(PeriodCalculator.firstPeriod(updated).copy(carryIn = carry))
                syncLocked(updated, today)
            }
            record(ActivityAction.EDITED, updated.name, budgetId, updated.amount)
        }
    }

    /**
     * Non-structural edit (name, icon, colour, amount, repeat, carry-over, daily split). If a
     * period is running, its amount changes immediately; past periods are untouched.
     */
    suspend fun updateBudget(budgetId: Long, today: LocalDate, edit: (Budget) -> Budget) = mutex.withLock {
        db.withTransaction {
            val budget = budgetDao.get(budgetId) ?: return@withTransaction
            val updated = edit(budget).copy(id = budget.id, isPrimary = budget.isPrimary)
            require(updated.amount > 0) { "Budget amount must be positive" }
            budgetDao.update(updated)

            val current = budgetDao.periodOn(budgetId, today) ?: return@withTransaction
            val isFirst = current.startDate == budget.startDate
            val base = if (isFirst) PeriodCalculator.firstPeriod(updated).baseAmount else updated.amount
            val carryIn = if (updated.carryOver) current.carryIn else 0
            budgetDao.updatePeriod(current.copy(baseAmount = base, carryIn = carryIn))
            // Turning recurrence off ends the budget after this period; drop pre-created future ones.
            if (!updated.recurring) budgetDao.deletePeriodsFrom(budgetId, current.endDate.plusDays(1))
        }
        budgetDao.get(budgetId)?.let { record(ActivityAction.EDITED, it.name, it.id, it.amount) }
    }

    /** Pauses from [today] until [until] (inclusive), or until resumed when null. */
    suspend fun pause(budgetId: Long, today: LocalDate, until: LocalDate?) = mutex.withLock {
        val budget = budgetDao.get(budgetId) ?: return@withLock
        require(until == null || !until.isBefore(today)) { "Pause must end today or later" }
        budgetDao.update(budget.copy(pauseStart = today, pauseEnd = until))
        record(ActivityAction.PAUSED, budget.name, budgetId, detail = until?.toString())
    }

    /** Ends a pause: past paused days stay paused, today is active again. */
    suspend fun resume(budgetId: Long, today: LocalDate) = mutex.withLock {
        val budget = budgetDao.get(budgetId) ?: return@withLock
        val start = budget.pauseStart ?: return@withLock
        budgetDao.update(
            if (!start.isBefore(today)) budget.copy(pauseStart = null, pauseEnd = null)
            else budget.copy(pauseEnd = today.minusDays(1)),
        )
        record(ActivityAction.RESUMED, budget.name, budgetId)
    }

    /**
     * Deletes a non-primary budget. Its spends are either moved to the primary budget or
     * deleted with it.
     */
    suspend fun deleteBudget(budgetId: Long, moveSpendsToPrimary: Boolean) {
        val name = budgetDao.get(budgetId)?.name
        val primaryId = mutex.withLock {
            db.withTransaction {
                val budget = budgetDao.get(budgetId) ?: return@withTransaction null
                require(!budget.isPrimary) { "The primary budget can't be deleted" }
                val primary = budgetDao.primary()
                if (moveSpendsToPrimary && primary != null) expenseDao.moveAll(budgetId, primary.id)
                budgetDao.delete(budgetId) // periods and any remaining spends cascade
                primary?.id.takeIf { moveSpendsToPrimary }
            }
        }
        record(ActivityAction.DELETED, name, budgetId, detail = if (moveSpendsToPrimary) MOVED_TO_PRIMARY else null)
        primaryId?.let { recalculateCarryOver(it) }
    }

    private suspend fun record(action: ActivityAction, name: String?, id: Long, amount: Long? = null, detail: String? = null) =
        log.record(ActivityKind.BUDGET, action, subject = name, amount = amount, refId = id, detail = detail)

    /**
     * Recomputes carry-over amounts along a budget's periods. Call after adding, editing or
     * deleting a spend dated in a past period, since that changes what was left over.
     */
    suspend fun recalculateCarryOver(budgetId: Long) = mutex.withLock {
        db.withTransaction {
            val budget = budgetDao.get(budgetId) ?: return@withTransaction
            if (!budget.carryOver) return@withTransaction
            val periods = budgetDao.periodsOf(budget.id).toMutableList()
            for (i in 1 until periods.size) {
                // Uses the already-corrected previous period, so changes ripple forward.
                val carry = carryFrom(budget, periods[i - 1])
                if (periods[i].carryIn != carry) {
                    periods[i] = periods[i].copy(carryIn = carry)
                    budgetDao.updatePeriod(periods[i])
                }
            }
        }
    }

    private suspend fun carryFrom(budget: Budget, period: BudgetPeriod): Long =
        if (budget.carryOver) period.totalAmount - expenseDao.totalBetween(budget.id, period.startDate, period.endDate) else 0

    private fun truncate(period: BudgetPeriod, newEnd: LocalDate): BudgetPeriod {
        val oldDays = DateWindow(period.startDate, period.endDate).days
        val newDays = DateWindow(period.startDate, newEnd).days
        return period.copy(endDate = newEnd, baseAmount = period.baseAmount * newDays / oldDays)
    }

    companion object {
        /** Activity detail: the deleted budget's spends moved to the main budget. */
        const val MOVED_TO_PRIMARY = "moved"

        /** Upper bound on periods created in one sync (≈ 27 years of weekly periods). */
        private const val MAX_CATCH_UP = 1400
    }
}
