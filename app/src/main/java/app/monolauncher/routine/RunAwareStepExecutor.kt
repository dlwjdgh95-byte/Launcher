package app.monolauncher.routine

/**
 * A [StepExecutor] that keeps state across the steps of one routine run
 * (e.g. whether another app has been opened yet). [RoutineRunner] calls [beginRun]
 * before the first step of every run.
 */
interface RunAwareStepExecutor : StepExecutor {
    fun beginRun()
}
