package app.monolauncher.session

/** Thin seam over Settings.Secure so grayscale logic is unit-testable. */
interface SecureSettings {
    fun getInt(name: String): Int?
    fun putInt(name: String, value: Int): Boolean
    fun canWrite(): Boolean
}

/** Previously stored daltonizer values, restored on exit. Null means "key was absent". */
data class DaltonizerSnapshot(val enabled: Int?, val mode: Int?)

/**
 * System-wide grayscale via the AOSP daltonizer keys (see docs/PLAN.md §5).
 * Order matters: write mode=0 (monochromacy) before enabled=1.
 */
class GrayscaleController(private val settings: SecureSettings) {
    companion object {
        const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_MODE = "accessibility_display_daltonizer"
        const val MODE_MONOCHROMACY = 0
    }

    fun canControl(): Boolean = settings.canWrite()

    fun isGrayscaleOn(): Boolean =
        settings.getInt(KEY_ENABLED) == 1 && settings.getInt(KEY_MODE) == MODE_MONOCHROMACY

    fun snapshot(): DaltonizerSnapshot = DaltonizerSnapshot(settings.getInt(KEY_ENABLED), settings.getInt(KEY_MODE))

    /** Turns grayscale on. Returns false if the permission is missing or a write failed. */
    fun enable(): Boolean {
        // TODO(session module)
        return false
    }

    /** Restores [snapshot] (or turns grayscale off when the snapshot had no values). */
    fun restore(snapshot: DaltonizerSnapshot?): Boolean {
        // TODO(session module)
        return false
    }
}
