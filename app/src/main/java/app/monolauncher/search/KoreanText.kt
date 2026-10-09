package app.monolauncher.search

import java.text.Normalizer

/**
 * Folds text into a matching key: lowercase letters and digits, Hangul spelled out as simple jamo.
 *
 * Syllables are decomposed with NFKD (which also turns IME compatibility jamo into conjoining jamo),
 * then every jamo is mapped back to a simple compatibility letter so keys stay readable:
 * finals become the matching initial, compound vowels/finals are split (ㅘ → ㅗㅏ, ㄺ → ㄹㄱ).
 * Doing this on both sides makes in-progress IME states match: '캌' ⊂ 카카오톡, '전호' ⊂ 전화.
 */
internal object KoreanText {
    private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNGSEONG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val JONGSEONG = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val SPLIT = mapOf(
        'ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ",
        'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ",
        'ㅘ' to "ㅗㅏ", 'ㅙ' to "ㅗㅐ", 'ㅚ' to "ㅗㅣ", 'ㅝ' to "ㅜㅓ", 'ㅞ' to "ㅜㅔ", 'ㅟ' to "ㅜㅣ", 'ㅢ' to "ㅡㅣ",
    )

    // 두벌식 layout for a..z; Shift only changes these keys.
    private const val QWERTY = "ㅁㅠㅊㅇㄷㄹㅎㅗㅑㅓㅏㅣㅡㅜㅐㅔㅂㄱㄴㅅㅕㅍㅈㅌㅛㅋ"
    private val QWERTY_SHIFT = mapOf('Q' to 'ㅃ', 'W' to 'ㅉ', 'E' to 'ㄸ', 'R' to 'ㄲ', 'T' to 'ㅆ', 'O' to 'ㅒ', 'P' to 'ㅖ')

    fun fold(text: String): String {
        val out = StringBuilder(text.length * 3)
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).codePoints().forEach { cp ->
            // Drops spaces, punctuation and the combining marks NFKD splits off accented Latin.
            if (!Character.isLetterOrDigit(cp)) return@forEach
            val jamo = when (cp) {
                in 0x1100..0x1112 -> CHOSEONG[cp - 0x1100]
                in 0x1161..0x1175 -> JUNGSEONG[cp - 0x1161]
                in 0x11A8..0x11C2 -> JONGSEONG[cp - 0x11A8]
                in 0x3131..0x3163 -> cp.toChar()
                else -> null
            }
            when (jamo) {
                null -> out.appendCodePoint(cp)
                else -> out.append(SPLIT[jamo] ?: jamo)
            }
        }
        return out.toString()
    }

    /** Initial consonant of a precomposed syllable, or null for anything else. */
    fun choseongOf(cp: Int): Char? =
        if (cp in 0xAC00..0xD7A3) CHOSEONG[(cp - 0xAC00) / (21 * 28)] else null

    fun isConsonant(c: Char): Boolean = c in CHOSEONG

    fun isAsciiLetters(text: String): Boolean = text.isNotEmpty() && text.all { it in 'a'..'z' || it in 'A'..'Z' }

    /** Jamo the 두벌식 keyboard would have produced for [text] typed with the IME in English mode. */
    fun fromQwerty(text: String): String = buildString(text.length) {
        for (c in text) append(QWERTY_SHIFT[c] ?: QWERTY.getOrElse(c.lowercaseChar() - 'a') { c })
    }
}
