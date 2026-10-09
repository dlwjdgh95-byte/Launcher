package app.monolauncher.session

import kotlinx.coroutines.flow.StateFlow

enum class SessionState { ACTIVE, EXITED }

/**
 * Owns the focus-session state: ACTIVE = grayscale + registered apps only; EXITED = color + all apps.
 * Implementations persist state, keep grayscale asserted while ACTIVE, and restore colors on exit.
 */
interface SessionController {
    val state: StateFlow<SessionState>
    /** True when WRITE_SECURE_SETTINGS is granted (grayscale controllable). */
    val canControlGrayscale: StateFlow<Boolean>
    fun start()
    fun exit()
    /** Re-checks permissions and re-asserts grayscale if ACTIVE (call from onResume / boot). */
    fun refresh()
}
