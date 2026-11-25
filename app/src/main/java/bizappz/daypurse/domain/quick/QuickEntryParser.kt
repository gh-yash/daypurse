package bizappz.daypurse.domain.quick

import bizappz.daypurse.data.model.Category

/**
 * Reads a typed one-liner such as "120 food", "chai 20" or "450 groceries milk and eggs":
 * the first number is the amount, a word matching a category picks it, and the rest is the note.
 */
object QuickEntryParser {
    data class Entry(val amount: Long, val categoryId: Long?, val note: String?)

    private val NUMBER = Regex("""(?:₹|rs\.?)?\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    fun parse(text: String, categories: List<Category>): Entry? {
        val match = NUMBER.find(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
            ?.movePointRight(2)?.toLong()?.takeIf { it > 0 } ?: return null
        val words = text.removeRange(match.range).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // A word that starts a category name ("groc" → Groceries), or a whole multi-word name.
        val rest = words.joinToString(" ")
        val byName = categories.firstOrNull { rest.startsWith(it.name, ignoreCase = true) }
        val byWord = byName ?: words.firstNotNullOfOrNull { w ->
            if (w.length < 3) null else categories.firstOrNull { c ->
                c.name.split(' ', '&', '/').any { part -> part.trim().startsWith(w, ignoreCase = true) }
            }
        }
        val note = when {
            byName != null -> rest.drop(byName.name.length)
            byWord != null -> words.filterNot { w -> w.length >= 3 && byWord.name.split(' ', '&', '/').any { it.trim().startsWith(w, true) } }
                .joinToString(" ")
            else -> rest
        }.trim().ifEmpty { null }
        return Entry(amount, byWord?.id, note)
    }
}
