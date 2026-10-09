package app.monolauncher.ui

import app.monolauncher.routine.RoutineConfig
import app.monolauncher.routine.RoutineJson
import app.monolauncher.routine.StepResult
import app.monolauncher.session.SessionState

/** The one-line status shown under the routine buttons. Rendered to text by the UI. */
sealed interface HomeMessage {
    data class RoutineRunning(val label: String) : HomeMessage
    data object RoutineDone : HomeMessage
    data class RoutineProblem(val reason: String) : HomeMessage
    data object NotRegistered : HomeMessage
    data object LaunchFailed : HomeMessage
}

/** During a session only registered apps may be searched or launched; after exit, everything. */
fun isLaunchAllowed(packageName: String, state: SessionState, registered: Set<String>): Boolean =
    state == SessionState.EXITED || packageName in registered

/** "완료" unless a step was skipped or failed; then the first such reason. */
fun routineOutcome(results: List<StepResult>): HomeMessage {
    val reason = results.firstNotNullOfOrNull {
        when (it) {
            is StepResult.Skipped -> it.reason
            is StepResult.Failed -> it.reason
            StepResult.Success -> null
        }
    }
    return if (reason == null) HomeMessage.RoutineDone else HomeMessage.RoutineProblem(reason)
}

sealed interface RoutineCheck {
    data class Valid(val config: RoutineConfig) : RoutineCheck
    data class Invalid(val message: String) : RoutineCheck
}

/** Parses routine JSON; on error keeps only the useful head of the parser message. */
fun checkRoutinesJson(text: String): RoutineCheck = try {
    RoutineCheck.Valid(RoutineJson.parse(text))
} catch (e: IllegalArgumentException) { // includes SerializationException
    val message = (e.message ?: e::class.java.simpleName)
        .substringBefore("JSON input:")
        .trim()
        .take(MAX_ERROR_LENGTH)
    RoutineCheck.Invalid(message)
}

private const val MAX_ERROR_LENGTH = 300
