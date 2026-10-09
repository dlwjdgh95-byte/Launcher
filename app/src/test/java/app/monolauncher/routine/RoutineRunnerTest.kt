package app.monolauncher.routine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineRunnerTest {
    private val success = StepResult.Success

    private fun routine(vararg steps: Step) = Routine("test", "테스트", steps.toList())

    @Test
    fun resolvesPlaceholdersInEveryStringField() = runTest {
        val executor = RecordingExecutor()
        val variables = mapOf(
            "home_lat" to "37.5",
            "home_lng" to "127.0",
            "map" to "net.daum.android.map",
            "chat" to "room_1",
            "food" to "라면",
        )

        val results = RoutineRunner(executor).run(
            routine(
                Step.DeepLink("kakaomap://route?ep={home_lat},{home_lng}&by=FOOT", "{map}"),
                Step.Launch("{map}"),
                Step.Shortcut("{map}", "{chat}"),
                Step.Timer(180, "{food} 완성"),
                Step.Alarm(7, 0, "{food}"),
            ),
            variables,
            allowedPackages = null,
        )

        assertEquals(List(5) { success }, results)
        assertEquals(
            listOf(
                Step.DeepLink("kakaomap://route?ep=37.5,127.0&by=FOOT", "net.daum.android.map"),
                Step.Launch("net.daum.android.map"),
                Step.Shortcut("net.daum.android.map", "room_1"),
                Step.Timer(180, "라면 완성"),
                Step.Alarm(7, 0, "라면"),
            ),
            executor.executed,
        )
    }

    @Test
    fun missingPlaceholderFailsThatStepAndContinues() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(
                Step.DeepLink("kakaomap://route?ep={home_lat},{home_lng}", "net.daum.android.map"),
                Step.Launch("a"),
            ),
            variables = mapOf("home_lng" to "127.0"),
            allowedPackages = null,
        )

        assertEquals(listOf(StepResult.Failed("설정에서 'home_lat' 값을 입력해 주세요"), success), results)
        assertEquals(listOf(Step.Launch("a")), executor.executed)
    }

    @Test
    fun blankPlaceholderCountsAsMissing() = runTest {
        val results = RoutineRunner(RecordingExecutor()).run(
            routine(Step.Timer(60, "{label}")),
            variables = mapOf("label" to "  "),
            allowedPackages = null,
        )

        assertEquals(listOf(StepResult.Failed("설정에서 'label' 값을 입력해 주세요")), results)
    }

    @Test
    fun sessionSkipsStepsThatOpenUnregisteredApps() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(
                Step.Launch("a"),
                Step.Launch("b"),
                Step.Shortcut("b", "s"),
                Step.DeepLink("x://y", "b"),
                Step.Launch("{pkg}"),
            ),
            variables = mapOf("pkg" to "c"),
            allowedPackages = setOf("a"),
        )

        assertEquals(
            listOf(
                success,
                StepResult.Skipped("등록되지 않은 앱: b"),
                StepResult.Skipped("등록되지 않은 앱: b"),
                StepResult.Skipped("등록되지 않은 앱: b"),
                StepResult.Skipped("등록되지 않은 앱: c"),
            ),
            results,
        )
        assertEquals(listOf(Step.Launch("a")), executor.executed)
    }

    @Test
    fun sessionNarrowsLaunchToRegisteredCandidatesInOrder() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(
                Step.Launch("a", listOf("b", "c", "d")),
                Step.Launch("b", listOf("c", "a")),
                Step.Launch("b", listOf("{alt}")),
            ),
            variables = mapOf("alt" to "a"),
            allowedPackages = setOf("a", "c"),
        )

        assertEquals(List(3) { success }, results)
        assertEquals(
            listOf(Step.Launch("a", listOf("c")), Step.Launch("c", listOf("a")), Step.Launch("a")),
            executor.executed,
        )
    }

    @Test
    fun sessionSkipsLaunchWhenNoCandidateIsRegistered() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(Step.Launch("b", listOf("c", "d"))),
            variables = emptyMap(),
            allowedPackages = setOf("a"),
        )

        assertEquals(listOf(StepResult.Skipped("등록되지 않은 앱: b")), results)
        assertTrue(executor.executed.isEmpty())
    }

    @Test
    fun launchAlternativesAreKeptWithoutSessionAndNeedTheirVariables() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(Step.Launch("b", listOf("{alt}")), Step.Launch("a", listOf("{missing}"))),
            variables = mapOf("alt" to "c"),
            allowedPackages = null,
        )

        assertEquals(listOf(success, StepResult.Failed("설정에서 'missing' 값을 입력해 주세요")), results)
        assertEquals(listOf(Step.Launch("b", listOf("c"))), executor.executed)
    }

    @Test
    fun sessionSkipsMediaAfterAnAppStepWasSkipped() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(Step.Launch("unregistered"), Step.Delay(1000), Step.Media(MediaAction.PLAY)),
            variables = emptyMap(),
            allowedPackages = setOf("a"),
        )

        val skippedMedia = StepResult.Skipped("앞 단계의 앱이 열리지 않아 재생을 건너뜁니다")
        assertEquals(listOf(StepResult.Skipped("등록되지 않은 앱: unregistered"), success, skippedMedia), results)
        assertTrue(executor.executed.isEmpty())
    }

    @Test
    fun sessionSkipsMediaAfterAnAppStepFailed() = runTest {
        val failed = StepResult.Failed("앱을 열 수 없습니다: a")
        val executor = RecordingExecutor { step -> if (step is Step.Launch) failed else success }

        val results = RoutineRunner(executor).run(
            routine(
                Step.Media(MediaAction.PAUSE),
                Step.Launch("a"),
                Step.Volume(30),
                Step.Media(MediaAction.PLAY),
                Step.DeepLink("x://y"),
            ),
            variables = emptyMap(),
            allowedPackages = setOf("a"),
        )

        assertEquals(
            listOf(
                success,
                failed,
                success,
                StepResult.Skipped("앞 단계의 앱이 열리지 않아 재생을 건너뜁니다"),
                StepResult.Skipped("세션 중에는 딥링크에 패키지를 지정해야 합니다"),
            ),
            results,
        )
        assertEquals(
            listOf(Step.Media(MediaAction.PAUSE), Step.Launch("a"), Step.Volume(30)),
            executor.executed,
        )
    }

    @Test
    fun sessionSkipsMediaAfterDeepLinkWithoutPackage() = runTest {
        val results = RoutineRunner(RecordingExecutor()).run(
            routine(Step.DeepLink("https://music.example"), Step.Media(MediaAction.PLAY)),
            variables = emptyMap(),
            allowedPackages = setOf("a"),
        )

        assertEquals(StepResult.Skipped("앞 단계의 앱이 열리지 않아 재생을 건너뜁니다"), results[1])
    }

    @Test
    fun mediaRunsAfterMissedAppStepWithoutSession() = runTest {
        val executor = RecordingExecutor { step -> if (step is Step.Launch) StepResult.Failed("x") else success }

        val results = RoutineRunner(executor).run(
            routine(Step.Launch("a"), Step.Media(MediaAction.PLAY)),
            variables = emptyMap(),
            allowedPackages = null,
        )

        assertEquals(listOf(StepResult.Failed("x"), success), results)
    }

    @Test
    fun sessionSkipsDeepLinksWithoutPackage() = runTest {
        val executor = RecordingExecutor()

        val results = RoutineRunner(executor).run(
            routine(Step.DeepLink("https://example.com"), Step.DeepLink("x://y", " "), Step.DeepLink("x://y", "a")),
            variables = emptyMap(),
            allowedPackages = setOf("a"),
        )

        val skipped = StepResult.Skipped("세션 중에는 딥링크에 패키지를 지정해야 합니다")
        assertEquals(listOf(skipped, skipped, success), results)
        assertEquals(listOf(Step.DeepLink("x://y", "a")), executor.executed)
    }

    @Test
    fun sessionRunsStepsThatOpenNoApp() = runTest {
        val executor = RecordingExecutor()
        val steps = arrayOf(Step.Media(MediaAction.PLAY), Step.Volume(30), Step.Dnd(true), Step.Timer(60), Step.GoHome)

        val results = RoutineRunner(executor).run(routine(*steps), emptyMap(), allowedPackages = emptySet())

        assertEquals(List(steps.size) { success }, results)
        assertEquals(steps.toList(), executor.executed)
    }

    @Test
    fun noEnforcementWithoutSession() = runTest {
        val executor = RecordingExecutor()
        val steps = arrayOf(Step.DeepLink("https://example.com"), Step.Launch("anything"), Step.Shortcut("b", "s"))

        val results = RoutineRunner(executor).run(routine(*steps), emptyMap(), allowedPackages = null)

        assertEquals(List(steps.size) { success }, results)
        assertEquals(steps.toList(), executor.executed)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // currentTime
    @Test
    fun delayIsHandledByRunnerInVirtualTime() = runTest {
        var launchedAt = -1L
        val executor = RecordingExecutor { launchedAt = currentTime; success }

        val results = RoutineRunner(executor).run(
            routine(Step.Delay(60_000), Step.Launch("a")),
            emptyMap(),
            allowedPackages = setOf("a"),
        )

        assertEquals(listOf(success, success), results)
        assertEquals(60_000L, launchedAt)
        assertEquals(listOf(Step.Launch("a")), executor.executed)
    }

    @Test
    fun executorExceptionBecomesFailedAndRoutineContinues() = runTest {
        val executor = RecordingExecutor { step ->
            when (step) {
                Step.Launch("boom") -> throw IllegalStateException("터짐")
                Step.Launch("silent") -> throw RuntimeException()
                else -> success
            }
        }

        val results = RoutineRunner(executor).run(
            routine(Step.Launch("boom"), Step.Launch("silent"), Step.Launch("ok")),
            emptyMap(),
            allowedPackages = null,
        )

        assertEquals(
            listOf(StepResult.Failed("터짐"), StepResult.Failed("알 수 없는 오류 (RuntimeException)"), success),
            results,
        )
    }

    @Test
    fun cancellationIsNotTurnedIntoFailure() = runTest {
        val executor = RecordingExecutor { throw CancellationException("stop") }

        val outcome = runCatching {
            RoutineRunner(executor).run(routine(Step.Launch("a"), Step.Launch("b")), emptyMap(), null)
        }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(listOf(Step.Launch("a")), executor.executed)
    }

    @Test
    fun reportsEveryStepAsWrittenAndBeginsEachRun() = runTest {
        val executor = RecordingExecutor()
        val runner = RoutineRunner(executor)
        val steps = listOf(Step.Launch("{missing}"), Step.Launch("b"), Step.Torch(true))
        val reported = mutableListOf<Triple<Int, Step, StepResult>>()

        runner.run(Routine("r", "R", steps), emptyMap(), setOf("a")) { i, step, result ->
            reported += Triple(i, step, result)
        }
        runner.run(Routine("r", "R", emptyList()), emptyMap(), null)

        assertEquals(
            listOf(
                Triple(0, steps[0], StepResult.Failed("설정에서 'missing' 값을 입력해 주세요")),
                Triple(1, steps[1], StepResult.Skipped("등록되지 않은 앱: b")),
                Triple(2, steps[2], success),
            ),
            reported,
        )
        assertEquals(2, executor.runsBegun)
    }
}
