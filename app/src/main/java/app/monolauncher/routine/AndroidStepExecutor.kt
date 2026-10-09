package app.monolauncher.routine

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.KeyEvent
import app.monolauncher.MainActivity
import app.monolauncher.apps.AppRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Executes routine steps with Android APIs. Must be invoked from the launcher (HOME) process:
 * holding the default-home role is what lets later steps start activities while another app
 * is in front.
 */
class AndroidStepExecutor(
    private val context: Context,
    private val apps: AppRepository,
) : RunAwareStepExecutor {
    /** Whether an earlier step of the current run may have put another app in front of us. */
    private var otherAppInFront = false

    override fun beginRun() {
        otherAppInFront = false
    }

    override suspend fun execute(step: Step): StepResult = when (step) {
        is Step.Launch -> opening { launch(step) }
        is Step.DeepLink -> opening { openDeepLink(step) }
        is Step.Shortcut -> opening { startShortcut(step) }
        is Step.Delay -> {
            delay(step.ms)
            StepResult.Success
        }
        is Step.Media -> sendMediaKey(step.action)
        is Step.Volume -> setMusicVolume(step.percent)
        is Step.Dnd -> setDnd(step.on)
        is Step.Torch -> setTorch(step.on)
        is Step.Timer -> setTimer(step)
        is Step.Alarm -> setAlarm(step)
        Step.GoHome -> goHome()
    }

    private inline fun opening(block: () -> StepResult): StepResult =
        block().also { if (it == StepResult.Success) otherAppInFront = true }

    private fun launch(step: Step.Launch): StepResult =
        if (apps.launchPackage(step.packageName)) StepResult.Success
        else StepResult.Failed("앱을 열 수 없습니다: ${step.packageName}")

    private fun openDeepLink(step: Step.DeepLink): StepResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(step.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        step.packageName?.takeIf { it.isNotBlank() }?.let(intent::setPackage)
        return start(intent, notFound = "앱이 이 링크를 열 수 없습니다")
    }

    private suspend fun startShortcut(step: Step.Shortcut): StepResult {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (!launcherApps.hasShortcutHostPermission()) {
            return StepResult.Failed("기본 홈 앱으로 설정해야 바로가기를 실행할 수 있습니다")
        }
        // Shortcuts start as the publishing app, so the home-app background-start exemption
        // does not cover them; they are blocked while another app is in front.
        if (otherAppInFront) bringLauncherToFront()
        return try {
            launcherApps.startShortcut(step.packageName, step.shortcutId, null, null, Process.myUserHandle())
            StepResult.Success
        } catch (e: ActivityNotFoundException) {
            StepResult.Failed("바로가기를 찾을 수 없습니다: ${step.shortcutId}")
        } catch (e: SecurityException) {
            StepResult.Failed("바로가기를 실행할 권한이 없습니다")
        } catch (e: IllegalStateException) {
            StepResult.Failed("지금은 바로가기를 실행할 수 없습니다")
        }
    }

    private suspend fun bringLauncherToFront() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
        delay(LAUNCHER_SETTLE_MS)
    }

    private fun sendMediaKey(action: MediaAction): StepResult {
        val audio = context.getSystemService(AudioManager::class.java)
        val code = action.keyCode()
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return StepResult.Success
    }

    private fun setMusicVolume(percent: Int): StepResult {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex(percent, max), 0)
        return StepResult.Success
    }

    private fun setDnd(on: Boolean): StepResult {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.isNotificationPolicyAccessGranted) {
            return StepResult.Skipped("방해금지 접근 권한이 필요합니다")
        }
        notifications.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return StepResult.Success
    }

    private fun setTorch(on: Boolean): StepResult {
        val camera = context.getSystemService(CameraManager::class.java)
        return try {
            val id = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return StepResult.Failed("손전등을 찾을 수 없습니다")
            camera.setTorchMode(id, on)
            StepResult.Success
        } catch (e: CameraAccessException) {
            StepResult.Failed("손전등을 제어할 수 없습니다")
        }
    }

    private fun setTimer(step: Step.Timer): StepResult {
        if (step.seconds !in 1..MAX_TIMER_SECONDS) {
            return StepResult.Failed("타이머는 1초에서 24시간 사이로 설정해 주세요")
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, step.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        step.message?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return start(intent, notFound = "타이머를 설정할 시계 앱이 없습니다")
    }

    private fun setAlarm(step: Step.Alarm): StepResult {
        if (step.hour !in 0..23 || step.minute !in 0..59) {
            return StepResult.Failed("알람 시각이 올바르지 않습니다: ${step.hour}:${step.minute}")
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, step.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, step.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        step.message?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return start(intent, notFound = "알람을 설정할 시계 앱이 없습니다")
    }

    private fun goHome(): StepResult {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent, notFound = "홈 화면을 열 수 없습니다")
            .also { if (it == StepResult.Success) otherAppInFront = false }
    }

    private fun start(intent: Intent, notFound: String): StepResult = try {
        context.startActivity(intent)
        StepResult.Success
    } catch (e: ActivityNotFoundException) {
        StepResult.Failed(notFound)
    }

    private companion object {
        const val LAUNCHER_SETTLE_MS = 400L
        const val MAX_TIMER_SECONDS = 24 * 60 * 60
    }
}

internal fun MediaAction.keyCode(): Int = when (this) {
    MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
    MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
    MediaAction.TOGGLE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
    MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
}

/** Stream volume index for [percent] (clamped to 0..100) of a stream whose maximum index is [max]. */
internal fun volumeIndex(percent: Int, max: Int): Int = (max * percent.coerceIn(0, 100) / 100f).roundToInt()
