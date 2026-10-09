package app.monolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monolauncher.R

/** Full-screen exit countdown. Cancelling is the big, obvious choice; letting it run out ends the session. */
@Composable
fun ExitOverlay(remaining: Int, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } } // keep taps away from the home screen underneath
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.exit_countdown_title), color = Mono.Muted, fontSize = 18.sp)
            Text("$remaining", color = Mono.Text, fontSize = 144.sp, fontWeight = FontWeight.Thin)
            Text(
                stringResource(R.string.exit_countdown_caption),
                color = Mono.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            MonoOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                fontSize = 28.sp,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(80.dp),
            )
        }
    }
}
