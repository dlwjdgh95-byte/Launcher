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
        /** AccessibilityManager.DALTONIZER_CORRECT_DEUTERANOMALY: what the platform uses when the mode key is absent. */
        const val MODE_PLATFORM_DEFAULT = 12
    }

    fun canControl(): Boolean = settings.canWrite()

    fun isGrayscaleOn(): Boolean =
        settings.getInt(KEY_ENABLED) == 1 && settings.getInt(KEY_MODE) == MODE_MONOCHROMACY

    fun snapshot(): DaltonizerSnapshot = DaltonizerSnapshot(settings.getInt(KEY_ENABLED), settings.getInt(KEY_MODE))

    /** Turns grayscale on. Returns false if the permission is missing or a write failed. */
    fun enable(): Boolean {
        if (!settings.canWrite()) return false
        // Without mode=0 the enabled flag would switch on color correction instead of grayscale.
        return settings.putInt(KEY_MODE, MODE_MONOCHROMACY) && settings.putInt(KEY_ENABLED, 1)
    }

    /**
     * Restores [snapshot] and turns grayscale off.
     *
     * A null snapshot means grayscale was already on before the session started (start() saves none then): the mode
     * was monochromacy before, so it is left as is and only the filter is switched off, because EXITED means color.
     * A mode that was absent is written back as [MODE_PLATFORM_DEFAULT] (a key cannot be deleted again), so our
     * monochromacy mode is not left behind. If the mode write fails, enabled=1 would keep the screen gray, so the
     * filter is switched off instead and false is returned.
     */
    fun restore(snapshot: DaltonizerSnapshot?): Boolean {
        if (!settings.canWrite()) return false
        if (snapshot == null) return settings.putInt(KEY_ENABLED, 0)
        if (!settings.putInt(KEY_MODE, snapshot.mode ?: MODE_PLATFORM_DEFAULT)) {
            settings.putInt(KEY_ENABLED, 0)
            return false
        }
        return settings.putInt(KEY_ENABLED, snapshot.enabled ?: 0)
    }
}
