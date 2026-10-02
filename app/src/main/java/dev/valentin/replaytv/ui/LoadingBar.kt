package dev.valentin.replaytv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme

/**
 * Barre de chargement indéterminée : yt-dlp ne publie aucune progression pendant l'analyse
 * d'une vidéo (`-J`), on ne peut donc qu'indiquer que l'attente est en cours.
 */
@Composable
fun LoadingBar(modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val transition = rememberInfiniteTransition(label = "loading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "position",
    )
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val segment = 0.3f
    val trackWidth = with(density) { widthPx.toDp() }
    val offset = trackWidth * (progress * (1f + segment) - segment)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { widthPx = it.width },
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .fillMaxWidth(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
