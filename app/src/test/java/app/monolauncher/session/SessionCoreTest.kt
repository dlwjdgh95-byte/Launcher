package app.monolauncher.session

import app.monolauncher.session.GrayscaleController.Companion.KEY_ENABLED
import app.monolauncher.session.GrayscaleController.Companion.KEY_MODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoreTest {

    private class MemoryStore(
        override var state: SessionState = SessionState.ACTIVE,
        override var snapshot: DaltonizerSnapshot? = null,
        override var restorePending: Boolean = false,
    ) : SessionStore

    private class RecordingWatch : GrayscaleWatch {
        var armed = false
        override fun arm() { armed = true }
        override fun disarm() { armed = false }
    }

    /** Color correction (mode 12) switched off: a typical value set before the session. */
    private val colorSettings = arrayOf(KEY_ENABLED to 0, KEY_MODE to 12)

    private val noWrites = emptyList<Pair<String, Int>>()

    private fun core(store: SessionStore, settings: FakeSecureSettings, watch: GrayscaleWatch = RecordingWatch()) =
        SessionCore(store, GrayscaleController(settings), watch)

    private fun FakeSecureSettings.grayscaleOn() = values[KEY_ENABLED] == 1 && values[KEY_MODE] == 0

    @Test
    fun `first run refresh starts the session with grayscale`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val watch = RecordingWatch()
        val core = core(store, settings, watch)

        core.refresh()

        assertEquals(SessionState.ACTIVE, core.state.value)
        assertTrue(core.canControlGrayscale.value)
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertEquals(listOf(KEY_MODE to 0, KEY_ENABLED to 1), settings.writes)
        assertTrue(watch.armed)
    }

    @Test
    fun `exit restores the snapshot and clears it`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val watch = RecordingWatch()
        val core = core(store, settings, watch)
        core.refresh()
        settings.writes.clear()

        core.exit()

        assertEquals(SessionState.EXITED, core.state.value)
        assertEquals(SessionState.EXITED, store.state)
        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
        assertNull(store.snapshot)
        assertFalse(store.restorePending)
        assertFalse(watch.armed)
    }

    @Test
    fun `failed restore on exit keeps the snapshot and marks the restore pending`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val core = core(store, settings)
        core.refresh()
        settings.canWrite = false
        settings.writes.clear()

        core.exit()

        assertEquals(SessionState.EXITED, core.state.value)
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertTrue(store.restorePending)
        assertTrue(settings.grayscaleOn())
    }

    @Test
    fun `refresh while exited retries a pending restore and clears it on success`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val watch = RecordingWatch()
        val core = core(store, settings, watch)
        core.refresh()
        settings.canWrite = false
        core.exit()

        settings.canWrite = true
        settings.writes.clear()
        core.refresh()

        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
        assertNull(store.snapshot)
        assertFalse(store.restorePending)
        assertEquals(SessionState.EXITED, core.state.value)
        assertFalse(watch.armed)

        settings.writes.clear()
        core.refresh()
        assertEquals(noWrites, settings.writes)
    }

    @Test
    fun `refresh while exited keeps a pending restore that fails again`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0, canWrite = false)
        val snapshot = DaltonizerSnapshot(enabled = 0, mode = 12)
        val store = MemoryStore(state = SessionState.EXITED, snapshot = snapshot, restorePending = true)

        core(store, settings).refresh()

        assertEquals(noWrites, settings.writes)
        assertEquals(snapshot, store.snapshot)
        assertTrue(store.restorePending)
    }

    @Test
    fun `a kill during exit leaves the restore pending for the next refresh`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        core(store, settings).refresh()
        // The process dies right after EXITED is persisted, before the restore runs.
        val dying = object : SessionStore by store {
            override var state: SessionState
                get() = store.state
                set(value) {
                    store.state = value
                    if (value == SessionState.EXITED) throw IllegalStateException("killed")
                }
        }
        runCatching { core(dying, settings).exit() }
        settings.writes.clear()

        core(store, settings).refresh()

        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
        assertFalse(store.restorePending)
        assertNull(store.snapshot)
    }

    @Test
    fun `start after a failed restore keeps the original snapshot for the next exit`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val core = core(store, settings)
        core.refresh()
        settings.canWrite = false
        core.exit()

        settings.canWrite = true
        core.start()
        assertFalse(store.restorePending)
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertTrue(settings.grayscaleOn())

        settings.writes.clear()
        core.exit()
        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
        assertFalse(store.restorePending)
    }

    @Test
    fun `start saves the current values and turns grayscale on`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore(state = SessionState.EXITED)
        val watch = RecordingWatch()
        val core = core(store, settings, watch)

        core.start()

        assertEquals(SessionState.ACTIVE, core.state.value)
        assertEquals(SessionState.ACTIVE, store.state)
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertEquals(listOf(KEY_MODE to 0, KEY_ENABLED to 1), settings.writes)
        assertTrue(watch.armed)
    }

    @Test
    fun `start keeps no snapshot when grayscale is already on, so exit turns it off`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)
        val store = MemoryStore(state = SessionState.EXITED)
        val core = core(store, settings)

        core.start()
        assertNull(store.snapshot)
        assertEquals(listOf(KEY_MODE to 0, KEY_ENABLED to 1), settings.writes)
        settings.writes.clear()

        core.exit()
        assertEquals(listOf(KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `start while active does not overwrite the snapshot`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val core = core(store, settings)
        core.refresh()
        settings.externalSet(KEY_ENABLED, 0)

        core.start()

        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertTrue(settings.grayscaleOn())
    }

    @Test
    fun `refresh while active re-enables grayscale without overwriting the snapshot`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val core = core(store, settings)
        core.refresh()
        settings.externalSet(KEY_ENABLED, 0)
        settings.writes.clear()

        core.refresh()

        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
        assertEquals(listOf(KEY_MODE to 0, KEY_ENABLED to 1), settings.writes)
    }

    @Test
    fun `refresh while active and already gray writes nothing`() {
        val settings = FakeSecureSettings(*colorSettings)
        val core = core(MemoryStore(), settings)
        core.refresh()
        settings.writes.clear()

        core.refresh()

        assertEquals(noWrites, settings.writes)
    }

    @Test
    fun `refresh while exited leaves the display alone`() {
        val settings = FakeSecureSettings(*colorSettings)
        val watch = RecordingWatch()
        val core = core(MemoryStore(state = SessionState.EXITED), settings, watch)

        core.refresh()

        assertEquals(SessionState.EXITED, core.state.value)
        assertEquals(noWrites, settings.writes)
        assertFalse(watch.armed)
    }

    @Test
    fun `external change while active turns grayscale back on`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        val core = core(store, settings)
        core.refresh()
        settings.externalSet(KEY_ENABLED, 0)

        core.onGrayscaleSettingChanged()

        assertTrue(settings.grayscaleOn())
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
    }

    @Test
    fun `external mode change while active switches back to monochromacy`() {
        val settings = FakeSecureSettings(*colorSettings)
        val core = core(MemoryStore(), settings)
        core.refresh()
        settings.externalSet(KEY_MODE, 12)

        core.onGrayscaleSettingChanged()

        assertTrue(settings.grayscaleOn())
    }

    @Test
    fun `our own write notification does not write again`() {
        val settings = FakeSecureSettings(*colorSettings)
        val core = core(MemoryStore(), settings)
        core.refresh()
        settings.writes.clear()

        core.onGrayscaleSettingChanged()

        assertEquals(noWrites, settings.writes)
    }

    @Test
    fun `external change while exited is ignored`() {
        val settings = FakeSecureSettings(*colorSettings)
        val core = core(MemoryStore(state = SessionState.EXITED), settings)

        core.onGrayscaleSettingChanged()

        assertEquals(noWrites, settings.writes)
    }

    @Test
    fun `exit while exited writes nothing`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)
        val core = core(MemoryStore(state = SessionState.EXITED), settings)

        core.exit()

        assertEquals(noWrites, settings.writes)
    }

    @Test
    fun `session without permission still becomes active and catches up once granted`() {
        val settings = FakeSecureSettings(*colorSettings, canWrite = false)
        val store = MemoryStore(state = SessionState.EXITED)
        val core = core(store, settings)

        core.start()
        assertEquals(SessionState.ACTIVE, core.state.value)
        assertFalse(core.canControlGrayscale.value)
        assertEquals(noWrites, settings.writes)

        settings.canWrite = true
        core.refresh()
        assertTrue(core.canControlGrayscale.value)
        assertTrue(settings.grayscaleOn())
        assertEquals(DaltonizerSnapshot(enabled = 0, mode = 12), store.snapshot)
    }

    @Test
    fun `state survives a restart`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore()
        core(store, settings).exit()

        assertEquals(SessionState.EXITED, core(store, settings).state.value)
    }

    @Test
    fun `snapshot survives a restart and is restored on exit`() {
        val settings = FakeSecureSettings(*colorSettings)
        val store = MemoryStore(state = SessionState.EXITED)
        core(store, settings).start()
        settings.writes.clear()

        val restarted = core(store, settings)
        restarted.refresh()
        restarted.exit()

        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
    }
}
