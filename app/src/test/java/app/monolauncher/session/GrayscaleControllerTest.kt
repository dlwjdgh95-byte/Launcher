package app.monolauncher.session

import app.monolauncher.session.GrayscaleController.Companion.KEY_ENABLED
import app.monolauncher.session.GrayscaleController.Companion.KEY_MODE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscaleControllerTest {

    @Test
    fun `enable writes mode before enabled`() {
        val settings = FakeSecureSettings()

        assertTrue(GrayscaleController(settings).enable())

        assertEquals(listOf(KEY_MODE to 0, KEY_ENABLED to 1), settings.writes)
    }

    @Test
    fun `enable without permission writes nothing`() {
        val settings = FakeSecureSettings(canWrite = false)

        assertFalse(GrayscaleController(settings).enable())

        assertEquals(emptyList<Pair<String, Int>>(), settings.writes)
    }

    @Test
    fun `enable does not turn on the filter when the mode write fails`() {
        val settings = FakeSecureSettings().apply { failingKeys += KEY_MODE }

        assertFalse(GrayscaleController(settings).enable())

        assertEquals(emptyList<Pair<String, Int>>(), settings.writes)
    }

    @Test
    fun `enable reports a failed enabled write`() {
        val settings = FakeSecureSettings().apply { failingKeys += KEY_ENABLED }

        assertFalse(GrayscaleController(settings).enable())
    }

    @Test
    fun `grayscale is on only with enabled and monochromacy mode`() {
        assertTrue(GrayscaleController(FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)).isGrayscaleOn())
        assertFalse(GrayscaleController(FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 12)).isGrayscaleOn())
        assertFalse(GrayscaleController(FakeSecureSettings(KEY_ENABLED to 1)).isGrayscaleOn())
        assertFalse(GrayscaleController(FakeSecureSettings(KEY_ENABLED to 0, KEY_MODE to 0)).isGrayscaleOn())
    }

    @Test
    fun `snapshot reports absent keys as null`() {
        val snapshot = GrayscaleController(FakeSecureSettings(KEY_MODE to 12)).snapshot()

        assertEquals(DaltonizerSnapshot(enabled = null, mode = 12), snapshot)
    }

    @Test
    fun `restore of a missing snapshot turns the filter off`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)

        assertTrue(GrayscaleController(settings).restore(null))

        assertEquals(listOf(KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `restore of a snapshot without values turns the filter off`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)

        assertTrue(GrayscaleController(settings).restore(DaltonizerSnapshot(null, null)))

        assertEquals(listOf(KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `restore writes mode before enabled`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)

        assertTrue(GrayscaleController(settings).restore(DaltonizerSnapshot(enabled = 1, mode = 12)))

        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 1), settings.writes)
    }

    @Test
    fun `restore with only a mode turns the filter off after restoring the mode`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)

        assertTrue(GrayscaleController(settings).restore(DaltonizerSnapshot(enabled = null, mode = 12)))

        assertEquals(listOf(KEY_MODE to 12, KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `restore with only enabled leaves the mode alone`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0)

        assertTrue(GrayscaleController(settings).restore(DaltonizerSnapshot(enabled = 0, mode = null)))

        assertEquals(listOf(KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `restore still turns the filter off when the mode write fails`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0).apply { failingKeys += KEY_MODE }

        assertFalse(GrayscaleController(settings).restore(DaltonizerSnapshot(enabled = 0, mode = 12)))

        assertEquals(listOf(KEY_ENABLED to 0), settings.writes)
    }

    @Test
    fun `restore without permission writes nothing`() {
        val settings = FakeSecureSettings(KEY_ENABLED to 1, KEY_MODE to 0, canWrite = false)

        assertFalse(GrayscaleController(settings).restore(DaltonizerSnapshot(enabled = 0, mode = 12)))

        assertEquals(emptyList<Pair<String, Int>>(), settings.writes)
    }
}
