package app.monolauncher.ui.settings

import app.monolauncher.apps.AppEntry
import app.monolauncher.apps.AppRepository
import app.monolauncher.session.SessionController
import app.monolauncher.session.SessionState
import app.monolauncher.settings.FakeSharedPreferences
import app.monolauncher.settings.PrefsLauncherSettings
import app.monolauncher.ui.LocationResult
import app.monolauncher.ui.RoutineCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val defaultRoutines = """{"version":1,"routines":[{"id":"home","label":"집 가기","steps":[]}]}"""
    private val editedRoutines = """{"version":1,"routines":[{"id":"t","label":"타이머","steps":[]}]}"""

    private val settings = PrefsLauncherSettings(FakeSharedPreferences()) { defaultRoutines }
    private val session = FakeSession()
    private val fixRequests = mutableListOf<(LocationResult) -> Unit>()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(settings, FakeApps(), session) { onResult -> fixRequests += onResult }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `routine draft is kept until a valid save`() {
        assertNull(viewModel.routineDraft.value)

        viewModel.editRoutines("{ broken")
        assertTrue(viewModel.save("{ broken") is RoutineCheck.Invalid)
        assertEquals("{ broken", viewModel.routineDraft.value)
        assertEquals(defaultRoutines, settings.routinesJson.value)

        viewModel.editRoutines(editedRoutines)
        assertTrue(viewModel.save(editedRoutines) is RoutineCheck.Valid)
        assertNull(viewModel.routineDraft.value)
        assertEquals(editedRoutines, viewModel.routinesJson.value)
    }

    @Test
    fun `reset restores the bundled routines and drops the draft`() {
        settings.setRoutinesJson(editedRoutines)
        viewModel.editRoutines("{ unsaved")

        viewModel.resetRoutines()

        assertNull(viewModel.routineDraft.value)
        assertEquals(defaultRoutines, viewModel.routinesJson.value)
    }

    @Test
    fun `a valid draft is not saved or dropped during a session`() {
        viewModel.editRoutines(editedRoutines)
        session.state.value = SessionState.ACTIVE

        viewModel.save(editedRoutines)

        assertEquals(editedRoutines, viewModel.routineDraft.value)
        assertEquals(defaultRoutines, settings.routinesJson.value)
    }

    @Test
    fun `a location fix is stored as home and reported`() {
        viewModel.saveCurrentLocation()
        assertTrue(viewModel.locating.value)
        viewModel.saveCurrentLocation() // ignored while a fix is pending
        assertEquals(1, fixRequests.size)

        fixRequests.single()(LocationResult.Found(37.5665, 126.978))

        assertFalse(viewModel.locating.value)
        assertEquals(LocationNotice.SAVED, viewModel.locationNotice.value)
        assertEquals("37.566500", settings.variables.value["home_lat"])
        assertEquals("126.978000", settings.variables.value["home_lng"])
    }

    @Test
    fun `location failures are reported without storing anything`() {
        val before = settings.variables.value

        viewModel.saveCurrentLocation()
        fixRequests.last()(LocationResult.ServicesOff)
        assertEquals(LocationNotice.SERVICES_OFF, viewModel.locationNotice.value)

        viewModel.saveCurrentLocation()
        assertNull(viewModel.locationNotice.value)
        fixRequests.last()(LocationResult.Unavailable)
        assertEquals(LocationNotice.FAILED, viewModel.locationNotice.value)

        assertEquals(before, settings.variables.value)
    }

    @Test
    fun `a fix that arrives after the session started is not stored`() {
        val before = settings.variables.value
        viewModel.saveCurrentLocation()
        session.state.value = SessionState.ACTIVE

        fixRequests.single()(LocationResult.Found(1.0, 2.0))

        assertFalse(viewModel.locating.value)
        assertNull(viewModel.locationNotice.value)
        assertEquals(before, settings.variables.value)
    }

    @Test
    fun `precise location permission is required`() {
        viewModel.onLocationPermissionResult(fineGranted = false, coarseGranted = true)
        assertEquals(LocationNotice.PRECISE_NEEDED, viewModel.locationNotice.value)

        viewModel.onLocationPermissionResult(fineGranted = false, coarseGranted = false)
        assertEquals(LocationNotice.DENIED, viewModel.locationNotice.value)
        assertTrue(fixRequests.isEmpty())

        viewModel.onLocationPermissionResult(fineGranted = true, coarseGranted = true)
        assertEquals(1, fixRequests.size)
        assertTrue(viewModel.locating.value)
    }

    private class FakeSession : SessionController {
        override val state = MutableStateFlow(SessionState.EXITED)
        override val canControlGrayscale: StateFlow<Boolean> = MutableStateFlow(true)
        override fun start() { state.value = SessionState.ACTIVE }
        override fun exit() { state.value = SessionState.EXITED }
        override fun refresh() = Unit
    }

    private class FakeApps : AppRepository {
        override val apps: StateFlow<List<AppEntry>> = MutableStateFlow(emptyList())
        override fun refresh() = Unit
        override fun launch(entry: AppEntry) = false
        override fun launchPackage(packageName: String) = false
    }
}
