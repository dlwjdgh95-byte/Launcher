package app.monolauncher.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LauncherAppsRepository(private val context: Context) : AppRepository {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    override val apps: StateFlow<List<AppEntry>> = _apps

    // TODO(session module): LauncherApps.getProfiles() x getActivityList(), Callback, sorting.
    override fun refresh() {}
    override fun launch(entry: AppEntry): Boolean = false
    override fun launchPackage(packageName: String): Boolean = false
}
