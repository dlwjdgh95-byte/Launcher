package app.monolauncher.routine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs a routine's steps in order and returns one result per step.
 * - Resolves `{variable}` placeholders from [variables]; a step with an unset variable fails.
 * - When `allowedPackages` is non-null (session active), steps that open an unregistered app,
 *   or deep links that do not name their package, are skipped.
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
        routine.steps.mapIndexed { i, step ->
            runStep(step, variables, allowedPackages).also { onStep(i, step, it) }
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
        val resolved = step.resolveVariables(variables)
        if (allowedPackages != null) {
            sessionSkipReason(resolved, allowedPackages)?.let { return StepResult.Skipped(it) }
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
        return if (pkg in allowedPackages) null else "등록되지 않은 앱: $pkg"
    }
}
