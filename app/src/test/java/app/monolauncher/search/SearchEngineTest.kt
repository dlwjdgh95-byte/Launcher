package app.monolauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun item(label: String, packageName: String = "test.${label.hashCode()}", aliases: List<String> = emptyList()) =
        SearchItem(key = "$packageName/$label", label = label, packageName = packageName, aliases = aliases)

    private val kakaoTalk = item("카카오톡", "com.kakao.talk")
    private val kakaoMap = item("카카오맵", "net.daum.android.map")
    private val phone = item("전화", "com.samsung.android.dialer")
    private val display = item("화면", "test.display")
    private val ytMusic = item("YouTube Music", "com.google.android.apps.youtube.music")
    private val millie = item("밀리의서재", "kr.co.millie.millieshelf")
    private val kyobo = item("교보eBook for 삼성", "com.kyobobook.ebook.samsung")
    private val home = item("Google Home", "com.google.android.apps.chromecast.app")
    private val camera = item("카메라", "com.sec.android.app.camera")

    private val all = listOf(kakaoTalk, kakaoMap, phone, display, ytMusic, millie, kyobo, home, camera)
    private val engine = SearchEngine(all)

    private fun top(query: String, engine: SearchEngine = this.engine) = engine.search(query).firstOrNull()
    private fun labels(query: String, engine: SearchEngine, limit: Int = 5) = engine.search(query, limit).map { it.label }

    private fun assertFinds(expected: SearchItem, query: String) =
        assertTrue("'$query' -> ${engine.search(query).map { it.label }}", expected in engine.search(query))

    @Test fun choseongQuery() {
        assertFinds(kakaoTalk, "ㅋㅋㅇ")
        assertEquals(kakaoTalk, top("ㅋㅋㅇㅌ"))
    }

    @Test fun skippedSyllables() {
        // A package without a built-in alias, so this exercises the jamo subsequence match.
        val talk = SearchEngine(listOf(item("카카오톡", "test.talk"), camera))
        assertEquals(listOf("카카오톡"), labels("카톡", talk))
    }

    @Test fun inProgressFinalConsonant() {
        assertFinds(kakaoTalk, "캌")
        assertEquals(kakaoTalk, top("카카옽"))
    }

    @Test fun inProgressCompoundVowel() = assertEquals(phone, top("전호"))

    @Test fun inProgressCompoundFinal() {
        val egg = SearchEngine(listOf(item("달걀 타이머"), item("달력")))
        assertEquals("달걀 타이머", top("닭", egg)?.label)
    }

    @Test fun imeCombinedConsonantsInChoseongQuery() {
        // Some keyboards render ㄱ followed by ㅅ as the single letter ㄳ.
        val singer = SearchEngine(listOf(item("가수"), item("가방")))
        assertEquals(listOf("가수"), labels("ㄳ", singer))
    }

    @Test fun qwertyTypedHangulWithCompoundVowel() = assertEquals(display, top("ghkaus"))

    @Test fun qwertyTypedHangul() {
        assertEquals(kakaoTalk, top("zkzkdhxhr"))
        assertEquals(kakaoTalk, top("Zkzkdhxhr"))
    }

    @Test fun qwertyShiftProducesDoubleConsonant() {
        val magpie = SearchEngine(listOf(item("까치"), item("가치")))
        assertEquals("까치", top("Rkcl", magpie)?.label)
        assertEquals("가치", top("rkcl", magpie)?.label)
    }

    @Test fun qwertyChoseong() {
        assertFinds(kakaoTalk, "zzd")
        assertEquals(kakaoTalk, top("zzdx"))
    }

    @Test fun latinWordStart() = assertEquals(ytMusic, top("music"))

    @Test fun latinIsCaseInsensitive() {
        assertEquals(ytMusic, top("YOUTUBE"))
        assertEquals(home, top("google h"))
    }

    @Test fun millie() {
        assertEquals(millie, top("밀리"))
        assertEquals(millie, top("ㅁㄹ"))
    }

    @Test fun kyobo() {
        assertEquals(kyobo, top("교보"))
        assertEquals(kyobo, top("ebook"))
    }

    @Test fun userAliasIsExactMatch() {
        val maps = SearchEngine(listOf(item("카맵 플러스"), item("카카오맵", aliases = listOf("카맵"))))
        assertEquals(listOf("카카오맵", "카맵 플러스"), labels("카맵", maps))
    }

    @Test fun builtInKoreanAliasesForEnglishLabels() {
        assertEquals(ytMusic, top("유튜브"))
        assertEquals(home, top("구글 홈"))
        assertEquals(kakaoMap, top("카카오지도"))
    }

    @Test fun packageNameSegment() {
        assertEquals(kakaoTalk, top("kakao"))
        assertEquals(kakaoTalk, top("talk"))
    }

    @Test fun packageTldAndGenericSegmentsDoNotMatch() {
        assertEquals(emptyList<SearchItem>(), engine.search("com"))
        assertEquals(emptyList<SearchItem>(), engine.search("android"))
    }

    @Test fun rankingAcrossTiers() {
        val notes = SearchEngine(
            listOf(item("Nova Teams"), item("Keynote"), item("Samsung Notes"), item("Notes"), item("Note")),
        )
        // exact > prefix > word start > substring > subsequence
        assertEquals(listOf("Note", "Notes", "Samsung Notes", "Keynote", "Nova Teams"), labels("note", notes))
    }

    @Test fun rankingExactBeforePrefixBeforeSubsequence() {
        val memos = SearchEngine(listOf(item("메일 모음"), item("메모장"), item("메모")))
        assertEquals(listOf("메모", "메모장", "메일 모음"), labels("메모", memos))
    }

    @Test fun rankingChoseongPrefixBeforeChoseongSubstringAndSubsequence() {
        val apps = SearchEngine(listOf(item("카오카"), item("오카카"), item("카카오톡")))
        assertEquals(listOf("카카오톡", "오카카", "카오카"), labels("ㅋㅋ", apps))
    }

    @Test fun prefixBeatsChoseongPrefix() {
        val apps = SearchEngine(listOf(item("카카오톡"), item("ㅋㅋ 유머")))
        assertEquals(listOf("ㅋㅋ 유머", "카카오톡"), labels("ㅋㅋ", apps))
    }

    @Test fun tiesBreakByShorterLabelThenLabelOrder() {
        val apps = SearchEngine(listOf(kakaoTalk, camera, kakaoMap))
        assertEquals(listOf("카메라", "카카오맵", "카카오톡"), labels("카", apps))
    }

    @Test fun subsequenceMustStartAtWordStart() {
        val apps = SearchEngine(listOf(item("밀리의서재")))
        assertEquals(emptyList<String>(), labels("리재", apps))
        assertEquals(listOf("밀리의서재"), labels("밀재", apps))
    }

    @Test fun limit() {
        val many = SearchEngine((1..20).map { item("앱 $it") })
        assertEquals(5, many.search("앱").size)
        assertEquals(3, many.search("앱", limit = 3).size)
        assertEquals(0, many.search("앱", limit = 0).size)
    }

    @Test fun blankQuery() {
        assertTrue(engine.search("").isEmpty())
        assertTrue(engine.search("   ").isEmpty())
        assertTrue(engine.search("!?").isEmpty())
    }

    @Test fun noMatch() = assertTrue(engine.search("넷플릭스").isEmpty())

    @Test fun returnsOriginalItems() {
        val custom = item("카카오맵", aliases = listOf("지도"))
        assertEquals(listOf(custom), SearchEngine(listOf(custom)).search("지도"))
    }

    @Test fun manyAppsStayFast() {
        val apps = (1..300).map { item("테스트 앱 $it", "com.example.app$it") } + all
        val start = System.nanoTime()
        val big = SearchEngine(apps)
        repeat(100) { big.search("ㅌㅅㅌ"); big.search("zkzk"); big.search("테스") }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $elapsedMs ms", elapsedMs < 2_000)
    }
}
