package app.monolauncher.session

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.StateFlow

/** Persists the session state and keeps grayscale asserted while ACTIVE (ContentObserver). */
class AndroidSessionController(
    context: Context,
    grayscale: GrayscaleController,
) : SessionController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reassert: Runnable = Runnable { core.onGrayscaleSettingChanged() }

    private val observer: ContentObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            // Quick settings writes both keys in a burst; react once it settles.
            mainHandler.removeCallbacks(reassert)
            mainHandler.postDelayed(reassert, REASSERT_DELAY_MS)
        }
    }

    /** Only runs while the launcher process is alive; [BootReceiver] restarts the process after boot or an update. */
    private val watch: GrayscaleWatch = object : GrayscaleWatch {
        private var armed = false

        override fun arm() {
            if (armed) return
            val resolver = appContext.contentResolver
            for (key in listOf(GrayscaleController.KEY_ENABLED, GrayscaleController.KEY_MODE)) {
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
            armed = true
        }

        override fun disarm() {
            if (!armed) return
            appContext.contentResolver.unregisterContentObserver(observer)
            mainHandler.removeCallbacks(reassert)
            armed = false
        }
    }

    private val core: SessionCore = SessionCore(PrefsSessionStore(appContext), grayscale, watch)

    override val state: StateFlow<SessionState> = core.state
    override val canControlGrayscale: StateFlow<Boolean> = core.canControlGrayscale

    override fun start() = onMain { core.start() }
    override fun exit() = onMain { core.exit() }
    override fun refresh() = onMain { core.refresh() }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private companion object {
        const val REASSERT_DELAY_MS = 250L
    }
}
