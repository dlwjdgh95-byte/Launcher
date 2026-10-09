package app.monolauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanTextTest {

    @Test fun syllablesBecomeSimpleJamo() = assertEquals("ㅋㅏㅋㅏㅇㅗㅌㅗㄱ", KoreanText.fold("카카오톡"))

    @Test fun compoundVowelsAndFinalsAreSplit() {
        assertEquals("ㅎㅗㅏㅁㅕㄴ", KoreanText.fold("화면"))
        assertEquals("ㄷㅏㄹㄱ", KoreanText.fold("닭"))
        assertEquals("ㅇㅡㅣ", KoreanText.fold("의"))
        assertEquals("ㄱㅏㅂㅅ", KoreanText.fold("값"))
    }

    @Test fun compatibilityJamoMatchSyllableJamo() {
        assertEquals("ㄱㅅ", KoreanText.fold("ㄳ"))
        assertEquals("ㅗㅏ", KoreanText.fold("ㅘ"))
        assertEquals(KoreanText.fold("카"), KoreanText.fold("ㅋㅏ"))
    }

    @Test fun everyCompoundConsonantLetterSplits() {
        // NFKD sends most of these to finals but ㅀ and ㅄ to archaic initials; all must end up split.
        val expected = mapOf(
            "ㄳ" to "ㄱㅅ", "ㄵ" to "ㄴㅈ", "ㄶ" to "ㄴㅎ", "ㄺ" to "ㄹㄱ", "ㄻ" to "ㄹㅁ", "ㄼ" to "ㄹㅂ",
            "ㄽ" to "ㄹㅅ", "ㄾ" to "ㄹㅌ", "ㄿ" to "ㄹㅍ", "ㅀ" to "ㄹㅎ", "ㅄ" to "ㅂㅅ",
        )
        for ((letter, split) in expected) assertEquals(letter, split, KoreanText.fold(letter))
        assertEquals("ㅂㅅㅇㅎ", KoreanText.fold("ㅄㅇㅎ"))
    }

    @Test fun everyCompatibilityJamoFoldsToCompatibilityJamo() {
        for (cp in 0x3131..0x3163) {
            val folded = KoreanText.fold(String(Character.toChars(cp)))
            assertTrue("U+${cp.toString(16)} -> $folded", folded.isNotEmpty() && folded.all { it.code in 0x3131..0x3163 })
        }
    }

    @Test fun latinIsLowercasedAndSeparatorsDropped() {
        assertEquals("youtubemusic", KoreanText.fold("YouTube Music"))
        assertEquals("pokemongo", KoreanText.fold("Pokémon GO!"))
    }

    @Test fun qwertyToJamo() {
        assertEquals("ㅋㅏㅋㅏㅇㅗㅌㅗㄱ", KoreanText.fromQwerty("zkzkdhxhr"))
        assertEquals("ㄲㅏㅃㅉㄸㅆㅒㅖ", KoreanText.fromQwerty("RkQWETOP"))
        assertEquals("ㅋ", KoreanText.fromQwerty("Z"))
    }

    @Test fun choseong() {
        assertEquals('ㄲ', KoreanText.choseongOf('까'.code))
        assertEquals(null, KoreanText.choseongOf('a'.code))
    }
}
