package app.monolauncher.apps

import kotlinx.coroutines.flow.StateFlow

/** Installed launchable apps (all profiles), kept fresh with LauncherApps callbacks. */
interface AppRepository {
    val apps: StateFlow<List<AppEntry>>
    fun refresh()
    /** Starts the app's main activity in a new task. Returns false if it could not be started. */
    fun launch(entry: AppEntry): Boolean
    /** Starts the first launchable activity of [packageName] for the main user. */
    fun launchPackage(packageName: String): Boolean
}
