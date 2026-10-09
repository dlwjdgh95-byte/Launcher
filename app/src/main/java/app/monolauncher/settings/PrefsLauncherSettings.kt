package app.monolauncher.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** SharedPreferences-backed settings. Default routines come from assets/default_routines.json. */
class PrefsLauncherSettings(private val context: Context) : LauncherSettings {
    // TODO(session module): persist everything in SharedPreferences.
    private val _registered = MutableStateFlow(Defaults.REGISTERED_PACKAGES.toSet())
    override val registeredPackages: StateFlow<Set<String>> = _registered
    override fun setRegisteredPackages(packages: Set<String>) { _registered.value = packages }

    private val _aliases = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    override val aliases: StateFlow<Map<String, List<String>>> = _aliases
    override fun setAliases(packageName: String, aliases: List<String>) {
        _aliases.value = _aliases.value + (packageName to aliases)
    }

    private val _variables = MutableStateFlow<Map<String, String>>(emptyMap())
    override val variables: StateFlow<Map<String, String>> = _variables
    override fun setVariable(name: String, value: String) { _variables.value = _variables.value + (name to value) }

    private val _routines = MutableStateFlow("{\"version\":1,\"routines\":[]}")
    override val routinesJson: StateFlow<String> = _routines
    override fun setRoutinesJson(json: String) { _routines.value = json }
    override fun resetRoutinesToDefault() {}
}
