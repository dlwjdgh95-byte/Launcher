package app.monolauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Gray tones used directly by the launcher UI. Everything stays monochrome, even in the EXITED (color) state. */
object Mono {
    val Text = Color.White
    val Muted = Color(0xFFA0A0A0)
    val Dim = Color(0xFF6E6E6E)
    val Line = Color(0xFF3A3A3A)
    val Panel = Color(0xFF141414)
}

private val Gray05 = Color(0xFF0A0A0A)
private val Gray10 = Color(0xFF141414)
private val Gray15 = Color(0xFF1E1E1E)
private val Gray20 = Color(0xFF2A2A2A)
private val Gray40 = Color(0xFF5E5E5E)
private val Gray80 = Color(0xFFCCCCCC)
private val Gray90 = Color(0xFFE6E6E6)

// Every slot is overridden: material3's defaults are tinted and would leak color into components.
private val MonoColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Gray20,
    onPrimaryContainer = Color.White,
    inversePrimary = Gray40,
    secondary = Gray80,
    onSecondary = Color.Black,
    secondaryContainer = Gray20,
    onSecondaryContainer = Color.White,
    tertiary = Gray80,
    onTertiary = Color.Black,
    tertiaryContainer = Gray20,
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Gray15,
    onSurfaceVariant = Mono.Muted,
    surfaceTint = Color.Black,
    inverseSurface = Gray90,
    inverseOnSurface = Color.Black,
    error = Gray90,
    onError = Color.Black,
    errorContainer = Gray20,
    onErrorContainer = Color.White,
    outline = Gray40,
    outlineVariant = Gray20,
    scrim = Color.Black,
    surfaceBright = Gray20,
    surfaceContainer = Gray10,
    surfaceContainerHigh = Gray15,
    surfaceContainerHighest = Gray20,
    surfaceContainerLow = Gray05,
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
    primaryFixed = Gray90,
    primaryFixedDim = Gray80,
    onPrimaryFixed = Color.Black,
    onPrimaryFixedVariant = Gray20,
    secondaryFixed = Gray90,
    secondaryFixedDim = Gray80,
    onSecondaryFixed = Color.Black,
    onSecondaryFixedVariant = Gray20,
    tertiaryFixed = Gray90,
    tertiaryFixedDim = Gray80,
    onTertiaryFixed = Color.Black,
    onTertiaryFixedVariant = Gray20,
)

@Composable
fun MonoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoColors, content = content)
}
