package app.monolauncher.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

class AndroidSecureSettings(context: Context) : SecureSettings {
    private val context = context.applicationContext

    override fun getInt(name: String): Int? = try {
        Settings.Secure.getInt(context.contentResolver, name)
    } catch (e: Settings.SettingNotFoundException) {
        null
    } catch (e: SecurityException) {
        // Hidden keys that are not @Readable throw for apps targeting S+.
        null
    }

    override fun putInt(name: String, value: Int): Boolean = try {
        Settings.Secure.putInt(context.contentResolver, name, value)
    } catch (e: SecurityException) {
        false
    }

    override fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
}
