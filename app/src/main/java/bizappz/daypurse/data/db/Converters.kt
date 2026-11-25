package bizappz.daypurse.data.db

import androidx.room.TypeConverter
import bizappz.daypurse.data.model.ActivityAction
import bizappz.daypurse.data.model.ActivityKind
import bizappz.daypurse.data.model.BudgetType
import bizappz.daypurse.data.model.InboxStatus
import bizappz.daypurse.data.model.Repeat
import java.time.LocalDate

class Converters {
    // Dates stored as epoch day: compact, sortable, and range queries stay index-friendly.
    @TypeConverter fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    // Enums stored by name, so reordering them never changes stored data.
    @TypeConverter fun fromBudgetType(value: BudgetType): String = value.name
    @TypeConverter fun toBudgetType(value: String): BudgetType = BudgetType.valueOf(value)
    @TypeConverter fun fromRepeat(value: Repeat): String = value.name
    @TypeConverter fun toRepeat(value: String): Repeat = Repeat.valueOf(value)
    @TypeConverter fun fromInboxStatus(value: InboxStatus): String = value.name
    @TypeConverter fun toInboxStatus(value: String): InboxStatus = InboxStatus.valueOf(value)
    @TypeConverter fun fromActivityKind(value: ActivityKind): String = value.name
    @TypeConverter fun toActivityKind(value: String): ActivityKind = ActivityKind.valueOf(value)
    @TypeConverter fun fromActivityAction(value: ActivityAction): String = value.name
    @TypeConverter fun toActivityAction(value: String): ActivityAction = ActivityAction.valueOf(value)
}
