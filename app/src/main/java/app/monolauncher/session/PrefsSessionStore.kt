package app.monolauncher.session

import android.content.Context
import android.content.SharedPreferences

/** [SessionStore] in SharedPreferences("session"). Writes are synchronous: they are rare and must survive a kill. */
class PrefsSessionStore internal constructor(private val prefs: SharedPreferences) : SessionStore {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override var state: SessionState
        get() {
            val name = prefs.getString(KEY_STATE, null)
            return SessionState.entries.firstOrNull { it.name == name } ?: SessionState.ACTIVE
        }
        set(value) {
            prefs.edit().putString(KEY_STATE, value.name).commit()
        }

    override var snapshot: DaltonizerSnapshot?
        get() {
            if (!prefs.getBoolean(KEY_SNAPSHOT_SAVED, false)) return null
            return DaltonizerSnapshot(enabled = prefs.intOrNull(KEY_SNAPSHOT_ENABLED), mode = prefs.intOrNull(KEY_SNAPSHOT_MODE))
        }
        set(value) {
            prefs.edit().apply {
                remove(KEY_SNAPSHOT_ENABLED)
                remove(KEY_SNAPSHOT_MODE)
                putBoolean(KEY_SNAPSHOT_SAVED, value != null)
                value?.enabled?.let { putInt(KEY_SNAPSHOT_ENABLED, it) }
                value?.mode?.let { putInt(KEY_SNAPSHOT_MODE, it) }
            }.commit()
        }

    private fun SharedPreferences.intOrNull(key: String): Int? = if (contains(key)) getInt(key, 0) else null

    private companion object {
        const val PREFS_NAME = "session"
        const val KEY_STATE = "state"
        const val KEY_SNAPSHOT_SAVED = "snapshot_saved"
        const val KEY_SNAPSHOT_ENABLED = "snapshot_enabled"
        const val KEY_SNAPSHOT_MODE = "snapshot_mode"
    }
}
