package app.monolauncher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.monolauncher.AppGraph
import app.monolauncher.apps.AppEntry
import app.monolauncher.apps.AppRepository
import app.monolauncher.session.SessionController
import app.monolauncher.session.SessionState
import app.monolauncher.settings.LauncherSettings
import app.monolauncher.ui.LocationResult
import app.monolauncher.ui.RoutineCheck
import app.monolauncher.ui.checkRoutinesJson
import app.monolauncher.ui.requestCurrentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/** Outcome of "현재 위치를 집으로 저장", shown under the button. */
enum class LocationNotice { SAVED, SERVICES_OFF, FAILED, DENIED, PRECISE_NEEDED }

/**
 * Settings edits. Every write is ignored unless the session is EXITED (editing mid-session would be a bypass).
 * Activity-scoped, so the routine draft and a pending location fix outlive both a fold/unfold recreation
 * and a HOME press that closes settings.
 */
class SettingsViewModel(
    private val settings: LauncherSettings,
    appRepository: AppRepository,
    private val session: SessionController,
    /** Takes one location fix and reports it on the main thread. */
    private val locate: (onResult: (LocationResult) -> Unit) -> Unit,
) : ViewModel() {

    /** One row per package (registration is per package, not per activity). */
    val apps: StateFlow<List<AppEntry>> = appRepository.apps
        .map { list -> list.distinctBy { it.packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), appRepository.apps.value.distinctBy { it.packageName })

    val registered: StateFlow<Set<String>> = settings.registeredPackages
    val aliases: StateFlow<Map<String, List<String>>> = settings.aliases
    val variables: StateFlow<Map<String, String>> = settings.variables
    val routinesJson: StateFlow<String> = settings.routinesJson

    val variableNames: StateFlow<List<String>> = settings.routinesJson
        .map(::routineVariableNames)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), routineVariableNames(settings.routinesJson.value))

    private val _routineDraft = MutableStateFlow<String?>(null)

    /** Unsaved routine JSON in the editor; null means the editor shows [routinesJson]. */
    val routineDraft: StateFlow<String?> = _routineDraft.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _locationNotice = MutableStateFlow<LocationNotice?>(null)
    val locationNotice: StateFlow<LocationNotice?> = _locationNotice.asStateFlow()

    private val editable get() = session.state.value == SessionState.EXITED

    fun setRegistered(packageName: String, registered: Boolean) {
        if (!editable) return
        val current = settings.registeredPackages.value
        settings.setRegisteredPackages(if (registered) current + packageName else current - packageName)
    }

    fun setAliases(packageName: String, text: String) {
        if (editable) settings.setAliases(packageName, parseAliases(text))
    }

    fun setVariable(name: String, value: String) {
        if (editable) settings.setVariable(name, value.trim())
    }

    /** Result of requesting [app.monolauncher.ui.LocationPermissions]: a usable fix needs FINE; COARSE alone is not enough. */
    fun onLocationPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        when {
            fineGranted -> saveCurrentLocation()
            coarseGranted -> _locationNotice.value = LocationNotice.PRECISE_NEEDED
            else -> _locationNotice.value = LocationNotice.DENIED
        }
    }

    /** Takes one location fix and stores it as the 집 가기 destination. Needs FINE location permission. */
    fun saveCurrentLocation() {
        if (_locating.value || !editable) return
        _locating.value = true
        _locationNotice.value = null
        locate { result ->
            _locating.value = false
            _locationNotice.value = when (result) {
                is LocationResult.Found -> if (setHomeLocation(result.latitude, result.longitude)) LocationNotice.SAVED else null
                LocationResult.ServicesOff -> LocationNotice.SERVICES_OFF
                LocationResult.Unavailable -> LocationNotice.FAILED
            }
        }
    }

    /** Stores a location fix as the 집 가기 destination (6 decimals is about 10 cm). */
    private fun setHomeLocation(latitude: Double, longitude: Double): Boolean {
        if (!editable) return false
        settings.setVariable("home_lat", "%.6f".format(Locale.US, latitude))
        settings.setVariable("home_lng", "%.6f".format(Locale.US, longitude))
        return true
    }

    fun editRoutines(json: String) {
        _routineDraft.value = json
    }

    fun validate(json: String): RoutineCheck = checkRoutinesJson(json)

    /** Saves only JSON that parses (which also discards the draft); returns the check either way. */
    fun save(json: String): RoutineCheck {
        val check = checkRoutinesJson(json)
        if (check is RoutineCheck.Valid && editable) {
            settings.setRoutinesJson(json)
            _routineDraft.value = null
        }
        return check
    }

    /** Restores the bundled routines and discards the draft, so the editor shows them. */
    fun resetRoutines() {
        if (editable) settings.resetRoutinesToDefault()
        _routineDraft.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SettingsViewModel(AppGraph.settings, AppGraph.apps, AppGraph.session, locate = app::requestCurrentLocation)
            }
        }
    }
}
