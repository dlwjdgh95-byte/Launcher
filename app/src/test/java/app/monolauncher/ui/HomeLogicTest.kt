package app.monolauncher.ui

import app.monolauncher.routine.Step
import app.monolauncher.routine.StepResult
import app.monolauncher.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val registered = setOf("net.daum.android.map")

    @Test
    fun `active session allows only registered packages`() {
        assertTrue(isLaunchAllowed("net.daum.android.map", SessionState.ACTIVE, registered))
        assertFalse(isLaunchAllowed("com.kakao.talk", SessionState.ACTIVE, registered))
    }

    @Test
    fun `exited session allows everything`() {
        assertTrue(isLaunchAllowed("com.kakao.talk", SessionState.EXITED, registered))
        assertTrue(isLaunchAllowed("com.kakao.talk", SessionState.EXITED, emptySet()))
    }

    @Test
    fun `routine outcome is done when every step succeeded`() {
        assertEquals(HomeMessage.RoutineDone, routineOutcome(listOf(StepResult.Success, StepResult.Success)))
        assertEquals(HomeMessage.RoutineDone, routineOutcome(emptyList()))
    }

    @Test
    fun `routine outcome reports the first skipped or failed reason`() {
        val results = listOf(
            StepResult.Success,
            StepResult.Skipped("등록되지 않은 앱: com.kakao.talk"),
            StepResult.Failed("설정에서 'home_lat' 값을 입력해 주세요"),
        )
        assertEquals(HomeMessage.RoutineProblem("등록되지 않은 앱: com.kakao.talk"), routineOutcome(results))
        assertEquals(
            HomeMessage.RoutineProblem("실패"),
            routineOutcome(listOf(StepResult.Failed("실패"), StepResult.Skipped("건너뜀"))),
        )
    }

    @Test
    fun `valid routine json parses`() {
        val json = """
            {"version":1,"routines":[
              {"id":"go_home","label":"집 가기","steps":[
                {"type":"deeplink","uri":"kakaomap://route?ep={home_lat},{home_lng}&by=PUBLICTRANSIT","packageName":"net.daum.android.map"}
              ]},
              {"id":"music","label":"음악","steps":[{"type":"launch","packageName":"com.google.android.apps.youtube.music"},{"type":"delay","ms":2500}]}
            ]}
        """.trimIndent()
        val check = checkRoutinesJson(json)
        assertTrue(check is RoutineCheck.Valid)
        val routines = (check as RoutineCheck.Valid).config.routines
        assertEquals(listOf("집 가기", "음악"), routines.map { it.label })
        assertEquals(Step.Delay(2500), routines[1].steps[1])
    }

    @Test
    fun `broken json is invalid with a short message`() {
        val check = checkRoutinesJson("""{"version":1,"routines":[{"id":"a","label":"A","steps":[}]}""")
        assertTrue(check is RoutineCheck.Invalid)
        val message = (check as RoutineCheck.Invalid).message
        assertTrue(message.isNotBlank())
        assertFalse(message.contains("JSON input"))
        assertTrue(message.length <= 300)
    }

    @Test
    fun `unknown step type is invalid`() {
        val check = checkRoutinesJson("""{"routines":[{"id":"a","label":"A","steps":[{"type":"teleport"}]}]}""")
        assertTrue(check is RoutineCheck.Invalid)
    }

    @Test
    fun `empty text is invalid`() {
        assertTrue(checkRoutinesJson("") is RoutineCheck.Invalid)
    }
}
