package app.monolauncher.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLogicTest {
    @Test
    fun `aliases are trimmed, deduplicated and blanks dropped`() {
        assertEquals(listOf("지도", "카맵"), parseAliases(" 지도, 카맵,, 지도 ,"))
        assertEquals(listOf("유튜브", "음악"), parseAliases("유튜브，음악"))
        assertEquals(emptyList<String>(), parseAliases("  , "))
    }

    @Test
    fun `variable names start with home coordinates then follow the json`() {
        val json = """
            {"routines":[
              {"id":"work","label":"출근","steps":[{"type":"deeplink","uri":"kakaomap://route?ep={work_lat},{work_lng}"}]},
              {"id":"home","label":"집 가기","steps":[{"type":"deeplink","uri":"kakaomap://route?ep={home_lat},{home_lng}"}]},
              {"id":"t","label":"타이머","steps":[{"type":"timer","seconds":60,"message":"{work_lat} {timer_note}"}]}
            ]}
        """.trimIndent()
        assertEquals(listOf("home_lat", "home_lng", "work_lat", "work_lng", "timer_note"), routineVariableNames(json))
    }

    @Test
    fun `json structure braces are not placeholders`() {
        assertEquals(BASE_VARIABLES, routineVariableNames("""{"version":1,"routines":[]}"""))
        assertEquals(BASE_VARIABLES, routineVariableNames("""{ } {1abc} {with space}"""))
    }
}
