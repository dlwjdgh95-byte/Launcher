package app.monolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monolauncher.R
import kotlinx.coroutines.delay

/** Setup problems shown on the home screen in any state: missing grayscale permission, not the default home. */
@Composable
fun SetupBanners(canControlGrayscale: Boolean, modifier: Modifier = Modifier) {
    val isDefaultHome = rememberStatusOnResume { isDefaultHome() }
    if (canControlGrayscale && isDefaultHome) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!canControlGrayscale) {
            Banner {
                Text(stringResource(R.string.banner_grayscale), color = Mono.Text)
                AdbCommand()
            }
        }
        if (!isDefaultHome) {
            val requestDefaultHome = rememberRequestDefaultHome()
            Banner {
                Text(stringResource(R.string.banner_default_home), color = Mono.Text)
                MonoTextButton(stringResource(R.string.set_default_home), onClick = requestDefaultHome)
            }
        }
    }
}

@Composable
private fun Banner(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Mono.Panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** The adb grant command, selectable, with a copy button. */
@Composable
fun AdbCommand(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val command = remember(context) { adbGrantCommand(context.packageName) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Column(modifier) {
        SelectionContainer {
            Text(command, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Mono.Muted)
        }
        MonoTextButton(
            text = stringResource(if (copied) R.string.copied else R.string.copy),
            onClick = {
                context.copyToClipboard(command)
                copied = true
            },
        )
    }
}
