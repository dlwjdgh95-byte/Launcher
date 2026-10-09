package app.monolauncher.settings

import kotlinx.coroutines.flow.StateFlow

/** User settings. Edits are only offered in the EXITED state (enforced by the UI). */
interface LauncherSettings {
    /** Packages that may be searched and launched while the session is ACTIVE. */
    val registeredPackages: StateFlow<Set<String>>
    fun setRegisteredPackages(packages: Set<String>)

    /** Extra search keywords per package, e.g. "net.daum.android.map" -> ["카맵", "지도"]. */
    val aliases: StateFlow<Map<String, List<String>>>
    fun setAliases(packageName: String, aliases: List<String>)

    /** Routine placeholder values, e.g. home_lat, home_lng, home_name. */
    val variables: StateFlow<Map<String, String>>
    fun setVariable(name: String, value: String)

    /** Routine definitions as JSON (RoutineJson format). */
    val routinesJson: StateFlow<String>
    fun setRoutinesJson(json: String)
    fun resetRoutinesToDefault()
}
