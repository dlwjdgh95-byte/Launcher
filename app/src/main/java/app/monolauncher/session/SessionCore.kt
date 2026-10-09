package app.monolauncher.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted session data. */
interface SessionStore {
    var state: SessionState
    /** Daltonizer values from before the session turned grayscale on; null when none was saved. */
    var snapshot: DaltonizerSnapshot?
    /** True when exit could not restore [snapshot]; refresh() retries while EXITED. */
    var restorePending: Boolean
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
        if (store.restorePending) {
            // The last exit could not restore: the saved snapshot still holds the colors from before that session.
            store.restorePending = false
        } else if (!grayscale.isGrayscaleOn()) {
            // Grayscale already on (e.g. left over from an earlier session) means there are no colors to restore.
            store.snapshot = grayscale.snapshot()
        }
        setState(SessionState.ACTIVE)
        grayscale.enable()
        watch.arm()
    }

    fun exit() {
        if (_state.value == SessionState.EXITED) return
        _canControlGrayscale.value = grayscale.canControl()
        // Marked before EXITED is persisted so a kill before the restore still gets it retried by refresh().
        store.restorePending = true
        // State and watch go first so our own restore writes are not re-asserted.
        setState(SessionState.EXITED)
        watch.disarm()
        restoreColors()
    }

    fun refresh() {
        _canControlGrayscale.value = grayscale.canControl()
        if (_state.value != SessionState.ACTIVE) {
            if (store.restorePending) restoreColors()
            return
        }
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

    /** Keeps the snapshot (and marks the restore pending) until a restore succeeds. */
    private fun restoreColors() {
        if (grayscale.restore(store.snapshot)) {
            // Flag first: if the process dies in between, a stale snapshot only holds the user's own earlier colors,
            // while a stale flag would retry with no snapshot and switch off a color correction the user had on.
            store.restorePending = false
            store.snapshot = null
        } else {
            store.restorePending = true
        }
    }

    private fun setState(state: SessionState) {
        store.state = state
        _state.value = state
    }
}
