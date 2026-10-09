package app.monolauncher.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** SharedPreferences-backed settings. Default routines come from assets/default_routines.json. */
class PrefsLauncherSettings internal constructor(
    private val prefs: SharedPreferences,
    private val loadDefaultRoutines: () -> String,
) : LauncherSettings {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultRoutinesFromAssets(context.applicationContext),
    )

    private val _registered = MutableStateFlow(
        prefs.getStringSet(KEY_REGISTERED, null)?.toSet() ?: Defaults.REGISTERED_PACKAGES.toSet()
    )
    override val registeredPackages: StateFlow<Set<String>> = _registered.asStateFlow()

    @Synchronized
    override fun setRegisteredPackages(packages: Set<String>) {
        val value = packages.toSet()
        // SharedPreferences keeps the set instance it is given, so hand it a private copy.
        prefs.edit().putStringSet(KEY_REGISTERED, HashSet(value)).apply()
        _registered.value = value
    }

    private val _aliases = MutableStateFlow(readJson(KEY_ALIASES, ALIASES_SERIALIZER))
    override val aliases: StateFlow<Map<String, List<String>>> = _aliases.asStateFlow()

    @Synchronized
    override fun setAliases(packageName: String, aliases: List<String>) {
        val cleaned = aliases.map(String::trim).filter(String::isNotEmpty).distinct()
        val next = if (cleaned.isEmpty()) _aliases.value - packageName else _aliases.value + (packageName to cleaned)
        writeJson(KEY_ALIASES, ALIASES_SERIALIZER, next)
        _aliases.value = next
    }

    private val _variables = MutableStateFlow(Defaults.VARIABLES + readJson(KEY_VARIABLES, VARIABLES_SERIALIZER))
    override val variables: StateFlow<Map<String, String>> = _variables.asStateFlow()

    @Synchronized
    override fun setVariable(name: String, value: String) {
        val next = _variables.value + (name to value)
        writeJson(KEY_VARIABLES, VARIABLES_SERIALIZER, next)
        _variables.value = next
    }

    private val _routines = MutableStateFlow(prefs.getString(KEY_ROUTINES, null) ?: loadDefaultRoutines())
    override val routinesJson: StateFlow<String> = _routines.asStateFlow()

    @Synchronized
    override fun setRoutinesJson(json: String) {
        prefs.edit().putString(KEY_ROUTINES, json).apply()
        _routines.value = json
    }

    @Synchronized
    override fun resetRoutinesToDefault() {
        prefs.edit().remove(KEY_ROUTINES).apply()
        _routines.value = loadDefaultRoutines()
    }

    private fun <K, V> readJson(key: String, serializer: KSerializer<Map<K, V>>): Map<K, V> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private fun <T> writeJson(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, Json.encodeToString(serializer, value)).apply()
    }

    internal companion object {
        const val PREFS_NAME = "settings"
        const val KEY_REGISTERED = "registered_packages"
        const val KEY_ALIASES = "aliases"
        const val KEY_VARIABLES = "variables"
        const val KEY_ROUTINES = "routines_json"
        const val DEFAULT_ROUTINES_ASSET = "default_routines.json"
        const val EMPTY_ROUTINES = """{"version":1,"routines":[]}"""

        private val ALIASES_SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
        private val VARIABLES_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

        fun defaultRoutinesFromAssets(context: Context): () -> String = {
            try {
                context.assets.open(DEFAULT_ROUTINES_ASSET).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                EMPTY_ROUTINES
            }
        }
    }
}
