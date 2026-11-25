package bizappz.daypurse.domain.quick

import bizappz.daypurse.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryParserTest {
    private val food = Category(1, "Food & Dining", "restaurant", 0)
    private val groceries = Category(2, "Groceries", "shopping_cart", 0)
    private val transport = Category(3, "Transport", "directions_bus", 0)
    private val cats = listOf(food, groceries, transport)

    @Test
    fun amountCategoryAndNote() {
        assertEquals(QuickEntryParser.Entry(12000, 1, null), QuickEntryParser.parse("120 food", cats))
        assertEquals(QuickEntryParser.Entry(2000, null, "chai"), QuickEntryParser.parse("chai 20", cats))
        assertEquals(QuickEntryParser.Entry(45050, 2, "milk and eggs"), QuickEntryParser.parse("450.50 groc milk and eggs", cats))
        assertEquals(QuickEntryParser.Entry(9900, 3, null), QuickEntryParser.parse("₹99 transport", cats))
        assertEquals(QuickEntryParser.Entry(30000, null, null), QuickEntryParser.parse("300", cats))
    }

    @Test
    fun noAmountIsRejected() {
        assertNull(QuickEntryParser.parse("food", cats))
        assertNull(QuickEntryParser.parse("", cats))
        assertNull(QuickEntryParser.parse("0 food", cats))
    }
}
