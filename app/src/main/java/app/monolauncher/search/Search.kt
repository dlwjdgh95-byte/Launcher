package app.monolauncher.search

import java.text.Normalizer

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

/**
 * Korean-aware app search: 초성, jamo prefix, in-progress syllables, QWERTY-typed Hangul, aliases, package name.
 * Ranking is purely textual (docs/PLAN.md §6); usage frequency is deliberately never considered.
 */
class SearchEngine(items: List<SearchItem>) {
    private val entries = items.mapIndexed(::Entry)

    /** Returns at most [limit] matches, best first. Blank query returns an empty list. */
    fun search(query: String, limit: Int = 5): List<SearchItem> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val queries = Query.variantsOf(query)
        if (queries.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry -> entry.bestMatch(queries)?.let { Hit(entry, it) } }
            .sortedWith(HIT_ORDER)
            .take(limit)
            .map { it.entry.item }
    }

    private class Hit(val entry: Entry, val tier: Tier)

    private companion object {
        val HIT_ORDER: Comparator<Hit> = compareBy<Hit>({ it.tier }, { it.entry.item.label.length })
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.entry.item.label }
            .thenBy { it.entry.position }
    }
}

/** Best first. */
private enum class Tier { EXACT, PREFIX, WORD_START, CHOSEONG_PREFIX, SUBSTRING, SUBSEQUENCE }

/** A folded query; ASCII input also yields a second variant read as 두벌식 Hangul. */
private class Query(val text: String) {
    val isChoseong = text.all(KoreanText::isConsonant)

    companion object {
        fun variantsOf(raw: String): List<Query> {
            val compact = raw.filterNot(Char::isWhitespace)
            val texts = buildList {
                add(KoreanText.fold(compact))
                if (KoreanText.isAsciiLetters(compact)) add(KoreanText.fold(KoreanText.fromQwerty(compact)))
            }
            return texts.filter { it.isNotEmpty() }.distinct().map(::Query)
        }
    }
}

private class Entry(val position: Int, val item: SearchItem) {
    private val names = (listOf(item.label) + item.aliases + KnownAliases.forPackage(item.packageName))
        .distinct()
        .map(::NameIndex)

    // The first segment is a TLD ("com", "net") and the generic ones match nearly every app.
    private val packageSegments = item.packageName.lowercase()
        .split('.', '_')
        .drop(1)
        .filter { it.length > 1 && it !in GENERIC_SEGMENTS }

    fun bestMatch(queries: List<Query>): Tier? = queries.asSequence()
        .flatMap { q -> names.asSequence().map { it.match(q) } + packageMatch(q) }
        .filterNotNull()
        .minOrNull()

    private fun packageMatch(q: Query): Tier? {
        if (q.isChoseong || q.text.length < 2) return null
        return if (packageSegments.any { it.startsWith(q.text) }) Tier.SUBSTRING else null
    }

    private companion object {
        val GENERIC_SEGMENTS = setOf("android", "app", "apps")
    }
}

/** Precomputed matching data for one label or alias. */
private class NameIndex(name: String) {
    private val key: String
    private val choseong: String

    /** Offsets into [key] where a word begins: start, after a separator, Hangul/Latin switch, camelCase hump. */
    private val wordStarts: IntArray

    init {
        val key = StringBuilder()
        val choseong = StringBuilder()
        val starts = mutableListOf<Int>()
        var prev = 0 // last code point that contributed to the key; 0 right after a separator
        Normalizer.normalize(name, Normalizer.Form.NFC).codePoints().forEach { cp ->
            val folded = KoreanText.fold(String(Character.toChars(cp)))
            if (folded.isEmpty()) {
                prev = 0
                return@forEach
            }
            if (isWordStart(prev, cp)) starts += key.length
            key.append(folded)
            KoreanText.choseongOf(cp)?.let(choseong::append)
            prev = cp
        }
        this.key = key.toString()
        this.choseong = choseong.toString()
        this.wordStarts = starts.toIntArray()
    }

    fun match(q: Query): Tier? {
        val t = q.text
        return when {
            key == t -> Tier.EXACT
            key.startsWith(t) -> Tier.PREFIX
            wordStarts.any { key.startsWith(t, it) } -> Tier.WORD_START
            q.isChoseong -> when {
                choseong.startsWith(t) -> Tier.CHOSEONG_PREFIX
                t in choseong -> Tier.SUBSTRING
                isSubsequence(t, choseong, 0) -> Tier.SUBSEQUENCE
                else -> null
            }
            t in key -> Tier.SUBSTRING
            anchoredSubsequence(t) -> Tier.SUBSEQUENCE
            else -> null
        }
    }

    // The loosest match must still begin at a word start, otherwise short queries hit almost every label.
    private fun anchoredSubsequence(t: String): Boolean {
        val start = wordStarts.firstOrNull { key[it] == t[0] } ?: return false
        return isSubsequence(t, key, start)
    }

    private companion object {
        fun isWordStart(prev: Int, cp: Int): Boolean =
            prev == 0 || isHangul(prev) != isHangul(cp) || (Character.isUpperCase(cp) && Character.isLowerCase(prev))

        fun isHangul(cp: Int) = Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL

        fun isSubsequence(needle: String, haystack: String, from: Int): Boolean {
            var matched = 0
            for (i in from until haystack.length) {
                if (haystack[i] == needle[matched] && ++matched == needle.length) return true
            }
            return false
        }
    }
}
