package app.monolauncher.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.monolauncher.AppGraph

/** Re-asserts the session after boot (grayscale persists, but the observer must be re-armed). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            AppGraph.session.refresh()
        }
    }
}
