package dev.valentin.replaytv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val Scheme = darkColorScheme(
    primary = Color(0xFF7FB3FF),
    onPrimary = Color(0xFF00284B),
    secondary = Color(0xFFB8C8E8),
    background = Color(0xFF0E141F),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF16202F),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF243247),
    onSurfaceVariant = Color(0xFFC2CBDA),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ReplayTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
