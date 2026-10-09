package app.monolauncher.routine

/** Records executed steps; [respond] decides each result (and may throw). */
class RecordingExecutor(
    private val respond: (Step) -> StepResult = { StepResult.Success },
) : RunAwareStepExecutor {
    val executed = mutableListOf<Step>()
    var runsBegun = 0
        private set

    override fun beginRun() {
        runsBegun++
    }

    override suspend fun execute(step: Step): StepResult {
        executed += step
        return respond(step)
    }
}
