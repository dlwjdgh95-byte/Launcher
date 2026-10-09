package app.monolauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.monolauncher.R
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Time, Korean date and battery. Compact: stacked (cover screen). Expanded: time with battery/weekday on the right. */
@Composable
fun ClockHeader(compact: Boolean, modifier: Modifier = Modifier) {
    val now = rememberMinuteClock()
    val date = now.toLocalDate()
    val time = KoreanDateFormat.time(now.toLocalTime())
    val battery = rememberBatteryPercent()?.let { stringResource(R.string.battery_percent, it) }

    if (compact) {
        Column(modifier) {
            Text(time, fontSize = 64.sp, fontWeight = FontWeight.Light, color = Mono.Text)
            Text(
                listOfNotNull(KoreanDateFormat.short(date), battery).joinToString(" · "),
                fontSize = 18.sp,
                color = Mono.Muted,
            )
        }
    } else {
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 80.sp, fontWeight = FontWeight.Light, color = Mono.Text)
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(battery, KoreanDateFormat.weekday(date)).joinToString(" · "),
                    fontSize = 18.sp,
                    color = Mono.Muted,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            Text(KoreanDateFormat.long(date), fontSize = 18.sp, color = Mono.Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Current time, refreshed at each minute boundary while the screen is started. */
@Composable
private fun rememberMinuteClock(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = LocalDateTime.now()
                value = current
                delay(KoreanDateFormat.millisUntilNextMinute(current.toLocalTime()))
            }
        }
    }
    return now
}

/** Battery percent from the sticky ACTION_BATTERY_CHANGED broadcast, listened to while started. */
@Composable
private fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember(context) {
        val capacity = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        mutableStateOf(capacity?.takeIf { it in 0..100 })
    }
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                batteryPercent(intent)?.let { percent = it }
            }
        }
        val sticky = context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let(::batteryPercent)?.let { percent = it }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

private fun batteryPercent(intent: Intent): Int? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else null
}
