package app.monolauncher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.monolauncher.AppGraph
import app.monolauncher.apps.AppEntry
import app.monolauncher.apps.AppRepository
import app.monolauncher.session.SessionController
import app.monolauncher.session.SessionState
import app.monolauncher.settings.LauncherSettings
import app.monolauncher.ui.RoutineCheck
import app.monolauncher.ui.checkRoutinesJson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Settings edits. Every write is ignored unless the session is EXITED (editing mid-session would be a bypass). */
class SettingsViewModel(
    private val settings: LauncherSettings,
    appRepository: AppRepository,
    private val session: SessionController,
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

    fun validate(json: String): RoutineCheck = checkRoutinesJson(json)

    /** Saves only JSON that parses; returns the check either way. */
    fun save(json: String): RoutineCheck {
        val check = checkRoutinesJson(json)
        if (check is RoutineCheck.Valid && editable) settings.setRoutinesJson(json)
        return check
    }

    /** Restores the bundled routines and returns the resulting JSON for the editor. */
    fun resetRoutines(): String {
        if (editable) settings.resetRoutinesToDefault()
        return settings.routinesJson.value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(AppGraph.settings, AppGraph.apps, AppGraph.session) }
        }
    }
}
