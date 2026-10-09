package app.monolauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.monolauncher.ui.HomeViewModel
import app.monolauncher.ui.LauncherRoot

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light bar icons: the launcher is black regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { LauncherRoot(viewModel) }
    }

    // singleTask HOME: pressing HOME delivers a new intent here instead of a new activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only a HOME press resets; a routine's bring-to-front intent must not clear its status line.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.resetUi()
    }

    override fun onResume() {
        super.onResume()
        AppGraph.session.refresh()
        AppGraph.apps.refresh()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen cancels the exit countdown; a fold/unfold recreation does not.
        if (!isChangingConfigurations) viewModel.cancelExit()
    }
}
