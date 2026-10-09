package app.monolauncher.session

import android.content.Context

class AndroidSecureSettings(private val context: Context) : SecureSettings {
    // TODO(session module): Settings.Secure get/put + checkSelfPermission(WRITE_SECURE_SETTINGS).
    override fun getInt(name: String): Int? = null
    override fun putInt(name: String, value: Int): Boolean = false
    override fun canWrite(): Boolean = false
}
