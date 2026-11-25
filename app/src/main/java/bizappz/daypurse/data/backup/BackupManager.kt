package bizappz.daypurse.data.backup

import androidx.room.withTransaction
import bizappz.daypurse.data.db.AppDatabase
import bizappz.daypurse.data.db.DefaultCategories
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.RecurringSpend
import bizappz.daypurse.data.model.Repeat
import bizappz.daypurse.domain.activity.ActivityLog
import bizappz.daypurse.domain.money.AmountInput
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a backup restores besides the database. */
data class BackupMeta(val currencyCode: String?)

/**
 * Plain-JSON backup of everything (uses Android's built-in org.json — no extra library).
 * Import replaces all data in one transaction, so a bad file never leaves half the data.
 */
class BackupManager(private val db: AppDatabase, private val log: ActivityLog) {
    private val dao = db.backupDao()

    suspend fun exportJson(meta: BackupMeta): String {
        val root = JSONObject()
            .put("app", APP_ID)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put("settings", JSONObject().put("currencyCode", meta.currencyCode ?: JSONObject.NULL))
            .put("categories", JSONArray(dao.categories().map { it.toJson() }))
            .put("budgets", JSONArray(dao.budgets().map { it.toJson() }))
            .put("periods", JSONArray(dao.periods().map { it.toJson() }))
            .put("expenses", JSONArray(dao.expenses().map { it.toJson() }))
            .put("recurring", JSONArray(dao.recurring().map { it.toJson() }))
        log.record(ActivityKind.DATA, ActivityAction.EXPORTED, detail = "json")
        return root.toString(2)
    }

    /** Spends as CSV for spreadsheets: date, budget, category, amount, note. */
    suspend fun exportCsv(): String {
        log.record(ActivityKind.DATA, ActivityAction.EXPORTED, detail = "csv")
        val names = dao.categories().associate { it.id to it.name }
        val budgetNames = dao.budgets().associate { it.id to it.name }
        return buildString {
            append("date,budget,category,amount,note\n")
            dao.expenses().forEach { e ->
                append(e.date).append(',')
                append(csv(budgetNames[e.budgetId] ?: "")).append(',')
                append(csv(e.categoryId?.let(names::get) ?: "")).append(',')
                append(AmountInput.fromMinor(e.amount)).append(',')
                append(csv(e.note ?: "")).append('\n')
            }
        }
    }

    /** Parses and validates [json], then replaces all data. Returns the restored settings. */
    suspend fun importJson(json: String): BackupMeta {
        val parsed = try {
            parse(JSONObject(json))
        } catch (e: JSONException) {
            throw BackupException("Not a valid backup file", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("Backup file is damaged: ${e.message}", e)
        } catch (e: java.time.DateTimeException) {
            throw BackupException("Backup file has an invalid date", e)
        }
        db.withTransaction {
            clearLocked()
            dao.insertCategories(parsed.categories)
            dao.insertBudgets(parsed.budgets)
            dao.insertPeriods(parsed.periods)
            dao.insertRecurring(parsed.recurring)
            dao.insertExpenses(parsed.expenses)
        }
        log.record(ActivityKind.DATA, ActivityAction.IMPORTED, amount = null, detail = parsed.expenses.size.toString())
        return parsed.meta
    }

    /** Deletes everything (history and detected payments too) and re-seeds default categories. */
    suspend fun resetAll() {
        db.withTransaction {
            clearLocked()
            dao.insertCategories(DefaultCategories.all)
            db.paymentInboxDao().clear()
            log.clear()
        }
        log.record(ActivityKind.DATA, ActivityAction.RESET)
    }

    private suspend fun clearLocked() {
        dao.clearExpenses()
        dao.clearRecurring()
        dao.clearPeriods()
        dao.clearBudgets()
        dao.clearCategories()
    }

    private class Parsed(
        val meta: BackupMeta,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val periods: List<BudgetPeriod>,
        val expenses: List<Expense>,
        val recurring: List<RecurringSpend>,
    )

    private fun parse(root: JSONObject): Parsed {
        require(root.optString("app") == APP_ID) { "not a DayPurse backup" }
        val version = root.getInt("version")
        if (version > FORMAT_VERSION) throw BackupException("This backup was made by a newer version of the app")

        val categories = root.getJSONArray("categories").objects().map {
            Category(
                id = it.getLong("id"), name = it.getString("name"), iconKey = it.getString("iconKey"),
                color = it.getInt("color"), isDefault = it.getBoolean("isDefault"),
                isHidden = it.getBoolean("isHidden"), sortOrder = it.getInt("sortOrder"),
            )
        }
        val budgets = root.getJSONArray("budgets").objects().map {
            Budget(
                id = it.getLong("id"), type = BudgetType.valueOf(it.getString("type")), amount = it.getLong("amount"),
                startDate = LocalDate.parse(it.getString("startDate")), anchorDay = it.getInt("anchorDay"),
                customLengthDays = it.optIntOrNull("customLengthDays"), recurring = it.getBoolean("recurring"),
                carryOver = it.getBoolean("carryOver"), active = it.getBoolean("active"), createdAt = it.getLong("createdAt"),
                name = it.getString("name"), iconKey = it.getString("iconKey"), color = it.getInt("color"),
                isPrimary = it.getBoolean("isPrimary"), dailySplit = it.getBoolean("dailySplit"),
                pauseStart = it.optStringOrNull("pauseStart")?.let(LocalDate::parse),
                pauseEnd = it.optStringOrNull("pauseEnd")?.let(LocalDate::parse),
                sortOrder = it.getInt("sortOrder"),
            )
        }
        var periods = root.getJSONArray("periods").objects().map {
            BudgetPeriod(
                id = it.getLong("id"), budgetId = it.getLong("budgetId"),
                startDate = LocalDate.parse(it.getString("startDate")), endDate = LocalDate.parse(it.getString("endDate")),
                baseAmount = it.getLong("baseAmount"), carryIn = it.getLong("carryIn"),
            )
        }
        require(budgets.count { it.isPrimary } <= 1) { "more than one primary budget" }
        val primaryId = budgets.firstOrNull { it.isPrimary }?.id ?: budgets.firstOrNull()?.id
        val budgetIds = budgets.map { it.id }.toSet()
        periods = periods.filter { it.budgetId in budgetIds }
        val categoryIds = categories.map { it.id }.toSet()
        val recurring = root.optJSONArray("recurring")?.objects().orEmpty().map {
            val amount = it.getLong("amount")
            require(amount > 0) { "recurring spend with non-positive amount" }
            RecurringSpend(
                id = it.getLong("id"), amount = amount,
                categoryId = it.optLongOrNull("categoryId")?.takeIf(categoryIds::contains),
                note = it.optStringOrNull("note"),
                budgetId = it.optLongOrNull("budgetId")?.takeIf(budgetIds::contains),
                repeat = Repeat.valueOf(it.getString("repeat")), anchorDay = it.getInt("anchorDay"),
                nextDate = LocalDate.parse(it.getString("nextDate")), paused = it.getBoolean("paused"),
                createdAt = it.getLong("createdAt"),
            )
        }
        val recurringIds = recurring.map { it.id }.toSet()
        val expenses = root.getJSONArray("expenses").objects().mapNotNull {
            val amount = it.getLong("amount")
            require(amount > 0) { "expense with non-positive amount" }
            Expense(
                id = it.getLong("id"), amount = amount,
                // Drop dangling references rather than failing the whole import.
                categoryId = it.optLongOrNull("categoryId")?.takeIf(categoryIds::contains),
                note = it.optStringOrNull("note"), date = LocalDate.parse(it.getString("date")),
                createdAt = it.getLong("createdAt"),
                // Unknown budget → the primary one.
                budgetId = it.optLongOrNull("budgetId")?.takeIf(budgetIds::contains) ?: primaryId ?: return@mapNotNull null,
                payee = it.optStringOrNull("payee"),
                recurringId = it.optLongOrNull("recurringId")?.takeIf(recurringIds::contains),
            )
        }
        val currency = root.optJSONObject("settings")?.optStringOrNull("currencyCode")
        return Parsed(BackupMeta(currency), categories, budgets, periods, expenses, recurring)
    }

    private fun Category.toJson() = JSONObject()
        .put("id", id).put("name", name).put("iconKey", iconKey).put("color", color)
        .put("isDefault", isDefault).put("isHidden", isHidden).put("sortOrder", sortOrder)

    private fun Budget.toJson() = JSONObject()
        .put("id", id).put("type", type.name).put("amount", amount).put("startDate", startDate.toString())
        .put("anchorDay", anchorDay).put("customLengthDays", customLengthDays ?: JSONObject.NULL)
        .put("recurring", recurring).put("carryOver", carryOver).put("active", active).put("createdAt", createdAt)
        .put("name", name).put("iconKey", iconKey).put("color", color).put("isPrimary", isPrimary)
        .put("dailySplit", dailySplit).put("sortOrder", sortOrder)
        .put("pauseStart", pauseStart?.toString() ?: JSONObject.NULL)
        .put("pauseEnd", pauseEnd?.toString() ?: JSONObject.NULL)

    private fun BudgetPeriod.toJson() = JSONObject()
        .put("id", id).put("budgetId", budgetId).put("startDate", startDate.toString())
        .put("endDate", endDate.toString()).put("baseAmount", baseAmount).put("carryIn", carryIn)

    private fun Expense.toJson() = JSONObject()
        .put("id", id).put("amount", amount).put("categoryId", categoryId ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL).put("date", date.toString()).put("createdAt", createdAt)
        .put("budgetId", budgetId).put("payee", payee ?: JSONObject.NULL).put("recurringId", recurringId ?: JSONObject.NULL)

    private fun RecurringSpend.toJson() = JSONObject()
        .put("id", id).put("amount", amount).put("categoryId", categoryId ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL).put("budgetId", budgetId ?: JSONObject.NULL)
        .put("repeat", repeat.name).put("anchorDay", anchorDay).put("nextDate", nextDate.toString())
        .put("paused", paused).put("createdAt", createdAt)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optIntOrNull(key: String) = if (isNull(key) || !has(key)) null else getInt(key)
    private fun JSONObject.optLongOrNull(key: String) = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optStringOrNull(key: String) = if (isNull(key) || !has(key)) null else getString(key)

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    companion object {
        const val APP_ID = "daypurse"
        const val FORMAT_VERSION = 1
    }
}
