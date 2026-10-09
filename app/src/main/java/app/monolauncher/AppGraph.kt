package app.monolauncher

import app.monolauncher.apps.AppRepository
import app.monolauncher.routine.RoutineRunner
import app.monolauncher.session.SessionController
import app.monolauncher.settings.LauncherSettings

/** Minimal service locator, initialised in [LauncherApplication.onCreate]. */
object AppGraph {
    lateinit var settings: LauncherSettings
    lateinit var apps: AppRepository
    lateinit var session: SessionController
    lateinit var routineRunner: RoutineRunner
}
