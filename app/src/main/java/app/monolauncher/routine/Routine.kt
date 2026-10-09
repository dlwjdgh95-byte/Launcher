package app.monolauncher.routine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoutineConfig(
    val version: Int = 1,
    val routines: List<Routine> = emptyList(),
)

@Serializable
data class Routine(
    val id: String,
    val label: String,
    val steps: List<Step>,
)

/**
 * One routine step. String fields may contain `{variable}` placeholders (e.g. `{home_lat}`)
 * that are resolved from user settings before execution.
 */
@Serializable
sealed interface Step {
    /** Package the step opens, if any. Used to enforce "only registered apps" during a session. */
    val targetPackage: String? get() = null

    @Serializable @SerialName("launch")
    data class Launch(val packageName: String) : Step {
        override val targetPackage get() = packageName
    }

    @Serializable @SerialName("deeplink")
    data class DeepLink(val uri: String, val packageName: String? = null) : Step {
        override val targetPackage get() = packageName
    }

    @Serializable @SerialName("shortcut")
    data class Shortcut(val packageName: String, val shortcutId: String) : Step {
        override val targetPackage get() = packageName
    }

    @Serializable @SerialName("delay")
    data class Delay(val ms: Long) : Step

    @Serializable @SerialName("media")
    data class Media(val action: MediaAction) : Step

    @Serializable @SerialName("volume")
    data class Volume(val percent: Int) : Step

    @Serializable @SerialName("dnd")
    data class Dnd(val on: Boolean) : Step

    @Serializable @SerialName("torch")
    data class Torch(val on: Boolean) : Step

    @Serializable @SerialName("timer")
    data class Timer(val seconds: Int, val message: String? = null) : Step

    @Serializable @SerialName("alarm")
    data class Alarm(val hour: Int, val minute: Int, val message: String? = null) : Step

    @Serializable @SerialName("home")
    data object GoHome : Step
}

@Serializable
enum class MediaAction {
    @SerialName("play") PLAY,
    @SerialName("pause") PAUSE,
    @SerialName("toggle") TOGGLE,
    @SerialName("next") NEXT,
    @SerialName("previous") PREVIOUS,
}

sealed interface StepResult {
    data object Success : StepResult
    data class Skipped(val reason: String) : StepResult
    data class Failed(val reason: String) : StepResult
}

/** Platform side of step execution (Android implementation lives in AndroidStepExecutor). */
interface StepExecutor {
    suspend fun execute(step: Step): StepResult
}
