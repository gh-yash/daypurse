package bizappz.daypurse.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import bizappz.daypurse.data.model.Budget
import bizappz.daypurse.data.model.BudgetPeriod
import bizappz.daypurse.data.model.Category
import bizappz.daypurse.data.model.ActivityEntry
import bizappz.daypurse.data.model.DetectedPayment
import bizappz.daypurse.data.model.Expense
import bizappz.daypurse.data.model.RecurringSpend

@Database(
    entities = [
        Category::class, Expense::class, Budget::class, BudgetPeriod::class,
        RecurringSpend::class, DetectedPayment::class, ActivityEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun backupDao(): BackupDao
    abstract fun recurringDao(): RecurringDao
    abstract fun paymentInboxDao(): PaymentInboxDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "daypurse.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()
    }

    /** Inserts default categories once, when the database file is first created. */
    internal object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DefaultCategories.all.forEach { c ->
                val values = ContentValues().apply {
                    put("name", c.name)
                    put("iconKey", c.iconKey)
                    put("color", c.color)
                    put("isDefault", 1)
                    put("isHidden", 0)
                    put("sortOrder", c.sortOrder)
                }
                db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }
}
