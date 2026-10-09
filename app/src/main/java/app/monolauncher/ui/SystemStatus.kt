package app.monolauncher.ui

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

fun adbGrantCommand(packageName: String) =
    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

fun Context.isDefaultHome(): Boolean =
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

fun Context.hasDndAccess(): Boolean =
    getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", text))
}

fun Context.versionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

/** Starts a settings screen, ignoring devices that lack it. */
fun Context.openSystemSettings(action: String) {
    try {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/** A system status that can change while we are in the background (permissions, roles): re-read on every resume. */
@Composable
fun <T> rememberStatusOnResume(read: Context.() -> T): T {
    val context = LocalContext.current
    val latestRead by rememberUpdatedState(read)
    var value by remember(context) { mutableStateOf(context.read()) }
    LifecycleResumeEffect(context) {
        value = context.latestRead()
        onPauseOrDispose { }
    }
    return value
}

/** Returns an action that asks to become the default home app (falls back to the system home settings). */
@Composable
fun rememberRequestDefaultHome(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return remember(context, launcher) {
        {
            val roles = context.getSystemService(RoleManager::class.java)
            val launched = roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
                runCatching { launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.isSuccess
            if (!launched) context.openSystemSettings(Settings.ACTION_HOME_SETTINGS)
        }
    }
}
