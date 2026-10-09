package app.monolauncher.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted session data. */
interface SessionStore {
    var state: SessionState
    /** Daltonizer values from before the session turned grayscale on; null when none was saved. */
    var snapshot: DaltonizerSnapshot?
}

/** Watches the daltonizer keys for changes made outside the launcher. Both calls must be idempotent. */
interface GrayscaleWatch {
    fun arm()
    fun disarm()
}

/**
 * Session decisions without Android types (docs/PLAN.md §5, §14).
 * Not thread-safe: the app calls it from the main thread only.
 */
class SessionCore(
    private val store: SessionStore,
    private val grayscale: GrayscaleController,
    private val watch: GrayscaleWatch,
) {
    private val _state = MutableStateFlow(store.state)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _canControlGrayscale = MutableStateFlow(grayscale.canControl())
    val canControlGrayscale: StateFlow<Boolean> = _canControlGrayscale.asStateFlow()

    fun start() {
        if (_state.value == SessionState.ACTIVE) return refresh()
        _canControlGrayscale.value = grayscale.canControl()
        // Grayscale already on (e.g. left over from an earlier session) means there are no colors to restore.
        if (!grayscale.isGrayscaleOn()) store.snapshot = grayscale.snapshot()
        setState(SessionState.ACTIVE)
        grayscale.enable()
        watch.arm()
    }

    fun exit() {
        if (_state.value == SessionState.EXITED) return
        _canControlGrayscale.value = grayscale.canControl()
        // State and watch go first so our own restore writes are not re-asserted.
        setState(SessionState.EXITED)
        watch.disarm()
        grayscale.restore(store.snapshot)
        store.snapshot = null
    }

    fun refresh() {
        _canControlGrayscale.value = grayscale.canControl()
        if (_state.value != SessionState.ACTIVE) return
        if (!grayscale.isGrayscaleOn()) {
            if (store.snapshot == null) store.snapshot = grayscale.snapshot()
            grayscale.enable()
        }
        watch.arm()
    }

    /** Called (debounced) when a daltonizer key changed, e.g. from quick settings. */
    fun onGrayscaleSettingChanged() {
        if (_state.value == SessionState.ACTIVE && !grayscale.isGrayscaleOn()) grayscale.enable()
    }

    private fun setState(state: SessionState) {
        store.state = state
        _state.value = state
    }
}
