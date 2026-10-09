package app.monolauncher.routine

/**
 * Runs a routine's steps in order.
 * - Resolves `{variable}` placeholders from [variables].
 * - When [allowedPackages] is non-null (session active), steps whose target package is not in it are skipped.
 */
class RoutineRunner(private val executor: StepExecutor) {
    suspend fun run(
        routine: Routine,
        variables: Map<String, String>,
        allowedPackages: Set<String>?,
        onStep: (index: Int, step: Step, result: StepResult) -> Unit = { _, _, _ -> },
    ): List<StepResult> {
        // TODO(routine module): placeholder resolution, allow-list enforcement, delays, error policy.
        return routine.steps.mapIndexed { i, step ->
            executor.execute(step).also { onStep(i, step, it) }
        }
    }
}
