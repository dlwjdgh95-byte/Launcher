package app.monolauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monolauncher.session.SessionState
import app.monolauncher.ui.settings.SettingsScreen

/** Below this width (the Fold cover screen, or a narrow window) the compact, thumb-reach layout is used. */
private val CompactMaxWidth = 600.dp

@Composable
fun LauncherRoot(viewModel: HomeViewModel) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val canControlGrayscale by viewModel.canControlGrayscale.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val exitRemaining by viewModel.exitRemaining.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(viewModel) {
        viewModel.clearFocus.collect {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    // Always enabled: back must never finish the home activity.
    BackHandler(onBack = viewModel::onBack)

    MonoTheme {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < CompactMaxWidth
                if (settingsOpen && session == SessionState.EXITED) {
                    SettingsScreen(compact, canControlGrayscale, onClose = viewModel::closeSettings)
                } else {
                    HomeScreen(viewModel, compact)
                }
                val remaining = exitRemaining
                if (remaining != null && session == SessionState.ACTIVE) {
                    ExitOverlay(remaining, onCancel = viewModel::cancelExit)
                }
            }
        }
    }
}
