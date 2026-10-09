package app.monolauncher

import android.app.Application
import app.monolauncher.apps.LauncherAppsRepository
import app.monolauncher.routine.AndroidStepExecutor
import app.monolauncher.routine.RoutineRunner
import app.monolauncher.session.AndroidSecureSettings
import app.monolauncher.session.AndroidSessionController
import app.monolauncher.session.GrayscaleController
import app.monolauncher.settings.PrefsLauncherSettings

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.settings = PrefsLauncherSettings(this)
        AppGraph.apps = LauncherAppsRepository(this)
        AppGraph.session = AndroidSessionController(this, GrayscaleController(AndroidSecureSettings(this)))
        AppGraph.routineRunner = RoutineRunner(AndroidStepExecutor(this, AppGraph.apps))
        AppGraph.apps.refresh()
        AppGraph.session.refresh()
    }
}
