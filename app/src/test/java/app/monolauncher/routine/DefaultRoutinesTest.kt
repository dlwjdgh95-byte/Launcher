package app.monolauncher.routine

import app.monolauncher.settings.Defaults
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DefaultRoutinesTest {
    // Gradle runs unit tests from the module directory; IDEs may use the project root.
    private val config = RoutineJson.parse(
        listOf("src/main/assets/default_routines.json", "app/src/main/assets/default_routines.json")
            .map(::File)
            .first { it.exists() }
            .readText(),
    )

    private val home = mapOf("home_lat" to "37.5665", "home_lng" to "126.9780")

    @Test
    fun parsesWithUniqueIds() {
        assertEquals(listOf("집 가기", "음악", "집"), config.routines.map { it.label })
        assertEquals(config.routines.size, config.routines.map { it.id }.toSet().size)
    }

    @Test
    fun goHomeOpensKakaoMapRouteToHome() = runTest {
        val executor = RecordingExecutor()
        val goHome = config.routines.first { it.label == "집 가기" }

        RoutineRunner(executor).run(goHome, home, allowedPackages = null)

        assertEquals(
            listOf(
                Step.DeepLink(
                    "kakaomap://route?ep=37.5665,126.9780&by=PUBLICTRANSIT",
                    "net.daum.android.map",
                ),
            ),
            executor.executed,
        )
    }

    @Test
    fun everyDefaultRoutineRunsInSessionWithDefaultRegisteredApps() = runTest {
        val runner = RoutineRunner(RecordingExecutor())
        val allowed = Defaults.REGISTERED_PACKAGES.toSet()

        for (routine in config.routines) {
            val results = runner.run(routine, home, allowed)
            assertEquals(routine.label, List(routine.steps.size) { StepResult.Success }, results)
        }
    }
}
