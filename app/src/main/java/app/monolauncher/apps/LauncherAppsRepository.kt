package app.monolauncher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Launchable activities of every profile, reloaded off the main thread whenever packages change. */
class LauncherAppsRepository(context: Context) : AppRepository {
    private val context = context.applicationContext
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val collator = Collator.getInstance(Locale.KOREAN)
    private val callbackRegistered = AtomicBoolean(false)

    // Conflated so a burst of package events causes at most one extra reload.
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    override val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (request in refreshRequests) {
                try {
                    _apps.value = loadApps()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not load apps", e)
                }
            }
        }
    }

    override fun refresh() {
        if (callbackRegistered.compareAndSet(false, true)) {
            launcherApps.registerCallback(PackageCallback(), Handler(Looper.getMainLooper()))
        }
        refreshRequests.trySend(Unit)
    }

    override fun launch(entry: AppEntry): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
        true
    } catch (e: RuntimeException) {
        // ActivityNotFoundException, SecurityException, or a profile that went away.
        Log.w(TAG, "Could not launch ${entry.component}", e)
        false
    }

    override fun launchPackage(packageName: String): Boolean {
        val me = Process.myUserHandle()
        val entry = _apps.value.firstOrNull { it.packageName == packageName && it.user == me }
        if (entry != null && launch(entry)) return true

        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not launch $packageName", e)
            false
        }
    }

    private fun loadApps(): List<AppEntry> {
        val ownPackage = context.packageName
        return launcherApps.profiles
            .flatMap { user ->
                launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != ownPackage }
                    .map { AppEntry(label = it.label.toString(), component = it.componentName, user = user) }
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    private inner class PackageCallback : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) = refresh()
        override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) = refresh()
    }

    private companion object {
        const val TAG = "LauncherAppsRepository"
    }
}
