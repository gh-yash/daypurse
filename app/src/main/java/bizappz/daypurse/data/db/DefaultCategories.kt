package bizappz.daypurse.data.db

import bizappz.daypurse.data.model.Category

/** Colour palette offered for categories (ARGB). Chosen to read well on light and dark. */
object CategoryPalette {
    val colors: List<Int> = listOf(
        0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6,
        0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFFFD54F, 0xFFFFB74D,
        0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE,
    ).map { it.toInt() }
}

object DefaultCategories {
    /** Seeded on first launch. Order here is the initial display order. */
    val all: List<Category> = listOf(
        def("Food & Dining", "restaurant", 0xFFFF8A65),
        def("Groceries", "shopping_cart", 0xFF81C784),
        def("Transport", "directions_bus", 0xFF64B5F6),
        def("Fuel", "local_gas_station", 0xFFFFB74D),
        def("Shopping", "shopping_bag", 0xFFF06292),
        def("Bills & Utilities", "receipt", 0xFF7986CB),
        def("Entertainment", "movie", 0xFFBA68C8),
        def("Health", "medical_services", 0xFFE57373),
        def("Coffee & Snacks", "local_cafe", 0xFFA1887F),
        def("Personal Care", "spa", 0xFF4DB6AC),
        def("Education", "school", 0xFF4FC3F7),
        def("Gifts", "redeem", 0xFFFFD54F),
        def("Other", "category", 0xFF90A4AE),
    ).mapIndexed { i, c -> c.copy(sortOrder = i) }

    private fun def(name: String, icon: String, color: Long) =
        Category(name = name, iconKey = icon, color = color.toInt(), isDefault = true)
}
