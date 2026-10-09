package app.monolauncher.session

import app.monolauncher.settings.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsSessionStoreTest {

    @Test
    fun `first run is active without a snapshot`() {
        val store = PrefsSessionStore(FakeSharedPreferences())

        assertEquals(SessionState.ACTIVE, store.state)
        assertNull(store.snapshot)
        assertFalse(store.restorePending)
    }

    @Test
    fun `restore pending flag is persisted`() {
        val prefs = FakeSharedPreferences()

        PrefsSessionStore(prefs).restorePending = true
        assertTrue(PrefsSessionStore(prefs).restorePending)

        PrefsSessionStore(prefs).restorePending = false
        assertFalse(PrefsSessionStore(prefs).restorePending)
    }

    @Test
    fun `state is persisted`() {
        val prefs = FakeSharedPreferences()
        PrefsSessionStore(prefs).state = SessionState.EXITED

        assertEquals(SessionState.EXITED, PrefsSessionStore(prefs).state)
    }

    @Test
    fun `unknown stored state falls back to active`() {
        val prefs = FakeSharedPreferences().apply { values["state"] = "PAUSED" }

        assertEquals(SessionState.ACTIVE, PrefsSessionStore(prefs).state)
    }

    @Test
    fun `snapshot keeps absent values distinct from no snapshot`() {
        val prefs = FakeSharedPreferences()
        val store = PrefsSessionStore(prefs)

        store.snapshot = DaltonizerSnapshot(enabled = null, mode = null)
        assertEquals(DaltonizerSnapshot(null, null), PrefsSessionStore(prefs).snapshot)

        store.snapshot = DaltonizerSnapshot(enabled = 0, mode = 12)
        assertEquals(DaltonizerSnapshot(0, 12), PrefsSessionStore(prefs).snapshot)

        store.snapshot = DaltonizerSnapshot(enabled = 1, mode = null)
        assertEquals(DaltonizerSnapshot(1, null), PrefsSessionStore(prefs).snapshot)

        store.snapshot = null
        assertNull(PrefsSessionStore(prefs).snapshot)
    }
}
