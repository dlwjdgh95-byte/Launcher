package app.monolauncher.routine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs a routine's steps in order and returns one result per step.
 * - Resolves `{variable}` placeholders from [variables]; a step with an unset variable fails.
 * - When `allowedPackages` is non-null (session active), steps that open an unregistered app,
 *   or deep links that do not name their package, are skipped. A launch step with alternatives
 *   runs with only its registered candidates and is skipped when none is registered.
 * - During a session, once a step that opens an app has been skipped or has failed, later media
 *   steps are skipped too, so they cannot resume whatever (possibly unregistered) app played last.
 * - A failing or skipped step never stops the routine.
 *
 * Runs do not overlap: a second call waits until the current routine has finished.
 */
class RoutineRunner(private val executor: StepExecutor) {
    private val mutex = Mutex()

    /** [onStep] receives the step as written in [routine] (before placeholder resolution). */
    suspend fun run(
        routine: Routine,
        variables: Map<String, String>,
        allowedPackages: Set<String>?,
        onStep: (index: Int, step: Step, result: StepResult) -> Unit = { _, _, _ -> },
    ): List<StepResult> = mutex.withLock {
        (executor as? RunAwareStepExecutor)?.beginRun()
        var appStepMissed = false
        routine.steps.mapIndexed { i, step ->
            val result = if (allowedPackages != null && appStepMissed && step is Step.Media) {
                StepResult.Skipped("앞 단계의 앱이 열리지 않아 재생을 건너뜁니다")
            } else {
                runStep(step, variables, allowedPackages)
            }
            if (step.opensApp && result != StepResult.Success) appStepMissed = true
            onStep(i, step, result)
            result
        }
    }

    private suspend fun runStep(
        step: Step,
        variables: Map<String, String>,
        allowedPackages: Set<String>?,
    ): StepResult {
        step.missingVariables(variables).firstOrNull()?.let {
            return StepResult.Failed("설정에서 '$it' 값을 입력해 주세요")
        }
        var resolved = step.resolveVariables(variables)
        if (allowedPackages != null) {
            sessionSkipReason(resolved, allowedPackages)?.let { return StepResult.Skipped(it) }
            resolved = resolved.onlyAllowed(allowedPackages)
        }
        if (resolved is Step.Delay) {
            delay(resolved.ms)
            return StepResult.Success
        }
        return try {
            executor.execute(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StepResult.Failed(e.message ?: "알 수 없는 오류 (${e::class.java.simpleName})")
        }
    }

    private fun sessionSkipReason(step: Step, allowedPackages: Set<String>): String? {
        if (step is Step.DeepLink && step.packageName.isNullOrBlank()) {
            return "세션 중에는 딥링크에 패키지를 지정해야 합니다"
        }
        val pkg = step.targetPackage ?: return null
        val candidates = if (step is Step.Launch) step.candidates else listOf(pkg)
        return if (candidates.any { it in allowedPackages }) null else "등록되지 않은 앱: $pkg"
    }

    /** A launch step narrowed to its registered candidates, in order; any other step unchanged. */
    private fun Step.onlyAllowed(allowedPackages: Set<String>): Step {
        if (this !is Step.Launch) return this
        val allowed = candidates.filter { it in allowedPackages }
        return allowed.firstOrNull()?.let { Step.Launch(it, allowed.drop(1)) } ?: this
    }

    /** Whether the step opens another app (including a deep link that does not name its package). */
    private val Step.opensApp: Boolean
        get() = this is Step.Launch || this is Step.DeepLink || this is Step.Shortcut
}
