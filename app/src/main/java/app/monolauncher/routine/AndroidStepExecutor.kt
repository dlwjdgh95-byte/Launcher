package app.monolauncher.routine

import android.content.Context
import app.monolauncher.apps.AppRepository

/** Executes routine steps with Android APIs. Must be invoked from the launcher (HOME) process. */
class AndroidStepExecutor(
    private val context: Context,
    private val apps: AppRepository,
) : StepExecutor {
    // TODO(routine module)
    override suspend fun execute(step: Step): StepResult = StepResult.Skipped("not implemented")
}
