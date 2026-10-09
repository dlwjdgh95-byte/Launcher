package app.monolauncher.search

import org.junit.Assert.assertEquals
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
