package app.monolauncher.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persists the session state and keeps grayscale asserted while ACTIVE (ContentObserver). */
class AndroidSessionController(
    private val context: Context,
    private val grayscale: GrayscaleController,
) : SessionController {
    private val _state = MutableStateFlow(SessionState.ACTIVE)
    override val state: StateFlow<SessionState> = _state
    private val _canControl = MutableStateFlow(false)
    override val canControlGrayscale: StateFlow<Boolean> = _canControl

    // TODO(session module)
    override fun start() { _state.value = SessionState.ACTIVE }
    override fun exit() { _state.value = SessionState.EXITED }
    override fun refresh() {}
}
