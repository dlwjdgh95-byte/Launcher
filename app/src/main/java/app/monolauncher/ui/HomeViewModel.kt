package app.monolauncher.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.monolauncher.AppGraph
import app.monolauncher.apps.AppEntry
import app.monolauncher.apps.AppRepository
import app.monolauncher.routine.Routine
import app.monolauncher.routine.RoutineRunner
import app.monolauncher.search.SearchEngine
import app.monolauncher.session.SessionController
import app.monolauncher.session.SessionState
import app.monolauncher.settings.LauncherSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settings: LauncherSettings,
    private val apps: AppRepository,
    private val session: SessionController,
    private val routineRunner: RoutineRunner,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state
    val canControlGrayscale: StateFlow<Boolean> = session.canControlGrayscale

    /** Search text. Kept here so it survives fold/unfold and can be cleared when HOME is pressed. */
    val query = TextFieldState()

    val routines: StateFlow<RoutineCheck> = settings.routinesJson
        .map(::checkRoutinesJson)
        .stateIn(viewModelScope, SharingStarted.Eagerly, checkRoutinesJson(settings.routinesJson.value))

    private var searchIndex by mutableStateOf(AppSearchIndex(emptyList(), emptyMap()))

    /** At most [MAX_RESULTS] apps, best first. Computed synchronously so the IME Go action sees what is on screen. */
    val results: List<AppEntry> by derivedStateOf { searchIndex.search(query.text.toString()) }

    private val _message = MutableStateFlow<HomeMessage?>(null)
    val message: StateFlow<HomeMessage?> = _message.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val exitCountdown = ExitCountdown(viewModelScope) { session.exit() }

    /** Seconds left in the exit countdown, null when it is not showing. */
    val exitRemaining: StateFlow<Int?> = exitCountdown.remaining

    private val _clearFocus = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the search field should lose focus and the keyboard should hide. */
    val clearFocus: SharedFlow<Unit> = _clearFocus.asSharedFlow()

    private var routineJob: Job? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            combine(apps.apps, settings.registeredPackages, settings.aliases, session.state) { all, registered, aliases, state ->
                AppSearchIndex(all.filter { isLaunchAllowed(it.packageName, state, registered) }, aliases)
            }.collect { searchIndex = it }
        }
        viewModelScope.launch {
            session.state.collect { state ->
                // Settings are an EXITED-only screen; the countdown only makes sense while ACTIVE.
                if (state == SessionState.ACTIVE) _settingsOpen.value = false else exitCountdown.cancel()
            }
        }
    }

    fun launch(entry: AppEntry) {
        if (!isLaunchAllowed(entry.packageName, session.state.value, settings.registeredPackages.value)) {
            show(HomeMessage.NotRegistered)
            return
        }
        if (apps.launch(entry)) {
            query.clearText()
            show(null)
            _clearFocus.tryEmit(Unit)
        } else {
            show(HomeMessage.LaunchFailed)
        }
    }

    fun launchFirstResult() {
        results.firstOrNull()?.let(::launch)
    }

    fun runRoutine(routine: Routine) {
        if (routineJob?.isActive == true) return
        routineJob = viewModelScope.launch {
            show(HomeMessage.RoutineRunning(routine.label), autoClear = false)
            val allowed = if (session.state.value == SessionState.ACTIVE) settings.registeredPackages.value else null
            val outcome = try {
                routineOutcome(routineRunner.run(routine, settings.variables.value, allowed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeMessage.RoutineProblem(e.message ?: e::class.java.simpleName)
            }
            show(outcome)
        }
    }

    fun requestExit() {
        if (session.state.value != SessionState.ACTIVE) return
        _clearFocus.tryEmit(Unit)
        exitCountdown.start()
    }

    fun cancelExit() = exitCountdown.cancel()

    fun start() {
        session.start()
        _settingsOpen.value = false
        query.clearText()
        _clearFocus.tryEmit(Unit)
    }

    fun openSettings() {
        if (session.state.value != SessionState.EXITED) return
        _clearFocus.tryEmit(Unit)
        _settingsOpen.value = true
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    /** Back never leaves the launcher; it only peels off one layer of UI. */
    fun onBack() {
        when {
            exitRemaining.value != null -> cancelExit()
            settingsOpen.value -> closeSettings()
            query.text.isNotEmpty() -> query.clearText()
        }
        _clearFocus.tryEmit(Unit)
    }

    /** HOME was pressed: return to a clean home screen. */
    fun resetUi() {
        cancelExit()
        _settingsOpen.value = false
        query.clearText()
        show(null)
        _clearFocus.tryEmit(Unit)
    }

    private fun show(message: HomeMessage?, autoClear: Boolean = true) {
        messageJob?.cancel()
        _message.value = message
        if (message != null && autoClear) {
            messageJob = viewModelScope.launch {
                delay(MESSAGE_TTL_MS)
                _message.value = null
            }
        }
    }

    companion object {
        const val MAX_RESULTS = 5
        private const val MESSAGE_TTL_MS = 6_000L

        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppGraph.settings, AppGraph.apps, AppGraph.session, AppGraph.routineRunner) }
        }
    }
}

/** Search over the apps allowed in the current session state, mapping hits back to [AppEntry]. */
private class AppSearchIndex(entries: List<AppEntry>, aliases: Map<String, List<String>>) {
    private val byKey = entries.associateBy { it.key }
    private val engine = SearchEngine(entries.map { it.toSearchItem(aliases[it.packageName].orEmpty()) })

    fun search(query: String): List<AppEntry> =
        engine.search(query, HomeViewModel.MAX_RESULTS).mapNotNull { byKey[it.key] }
}
