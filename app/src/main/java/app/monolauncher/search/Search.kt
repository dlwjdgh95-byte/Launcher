package app.monolauncher.search

/**
 * One searchable app. Pure Kotlin so the matcher can be unit-tested on the JVM.
 * [key] is a stable identity (e.g. "package/activity/userId").
 */
data class SearchItem(
    val key: String,
    val label: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
)

/** Korean-aware app search: 초성, jamo prefix, in-progress syllables, QWERTY-typed Hangul, aliases, package name. */
class SearchEngine(items: List<SearchItem>) {
    private val items = items

    /** Returns at most [limit] matches, best first. Blank query returns an empty list. */
    fun search(query: String, limit: Int = 5): List<SearchItem> {
        // TODO(search module): implement ranking described in docs/PLAN.md §6.
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return items.filter { it.label.lowercase().contains(q) }.take(limit)
    }
}
