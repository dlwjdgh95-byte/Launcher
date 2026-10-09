package app.monolauncher.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The exit confirmation: shows [seconds]..1, one per second, then calls [onFinished].
 * [cancel] at any point aborts without calling it. Starting while running is a no-op.
 */
class ExitCountdown(
    private val scope: CoroutineScope,
    private val seconds: Int = DEFAULT_SECONDS,
    private val onFinished: () -> Unit,
) {
    private val _remaining = MutableStateFlow<Int?>(null)

    /** Seconds left while counting down, null when idle. */
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            for (left in seconds downTo 1) {
                _remaining.value = left
                delay(1_000)
            }
            _remaining.value = null
            onFinished()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remaining.value = null
    }

    companion object {
        const val DEFAULT_SECONDS = 5
    }
}
