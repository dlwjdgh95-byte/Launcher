package app.monolauncher.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.monolauncher.AppGraph

/**
 * Re-asserts the session after boot or an app update (grayscale persists, but the observer must be re-armed).
 * The grayscale watcher only runs while the launcher process is alive; these broadcasts start the process again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppGraph.session.refresh()
        }
    }
}
