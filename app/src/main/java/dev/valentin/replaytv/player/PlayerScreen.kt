package dev.valentin.replaytv.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private const val HIDE_DELAY_MS = 4_000L
private const val TICK_MS = 500L
private const val SAVE_EVERY_TICKS = 10

@Composable
fun PlayerScreen(player: ExoPlayer, ui: PlayerUiState, title: String, onTick: () -> Unit) {
    // Horloge : position/durée rafraîchies, sauvegarde périodique, masquage automatique.
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            ui.positionMs = player.currentPosition
            ui.durationMs = player.duration.coerceAtLeast(0)
            if (++ticks % SAVE_EVERY_TICKS == 0) onTick()
            val idleFor = SystemClock.uptimeMillis() - ui.lastInteractionAt
            if (ui.controlsVisible && ui.isPlaying && ui.seekTargetMs == null && !ui.ended && idleFor > HIDE_DELAY_MS) {
                ui.controlsVisible = false
            }
            delay(TICK_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(visible = ui.controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Overlay(ui, title)
        }
    }
}

@Composable
private fun Overlay(ui: PlayerUiState, title: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Icône centrale : pause, chargement ou fin.
        Box(modifier = Modifier.align(Alignment.Center)) {
            when {
                ui.error != null -> StatusBadge("Lecture impossible : ${ui.error}")
                ui.ended -> StatusBadge("Terminé · OK pour relire")
                ui.isBuffering -> StatusBadge("Chargement…")
                !ui.isPlaying -> PauseGlyph()
            }
        }

        if (ui.resumedFromMs > 0 && ui.positionMs < ui.resumedFromMs + 10_000) {
            Text(
                "Reprise à ${formatClock(ui.resumedFromMs)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(40.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(horizontal = 56.dp)
                .padding(top = 80.dp, bottom = 40.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            TimeBar(ui)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val target = ui.seekTargetMs
                val timeText = if (target != null) {
                    val delta = (target - ui.positionMs) / 1000
                    "${formatClock(target)}  (${if (delta >= 0) "+" else "−"}${formatClock(kotlin.math.abs(delta) * 1000)})"
                } else {
                    formatClock(ui.positionMs)
                }
                Text(
                    "$timeText / ${formatClock(ui.durationMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (target != null) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "OK : lecture / pause   ◀ ▶ : −10 s / +10 s (maintenir pour accélérer)   Retour : masquer",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TimeBar(ui: PlayerUiState) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val duration = ui.durationMs.coerceAtLeast(1)
    val playedFraction = (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val targetFraction = ui.seekTargetMs?.let { (it.toFloat() / duration).coerceIn(0f, 1f) }
    val trackWidth = with(density) { widthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .onSizeChanged { widthPx = it.width },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(playedFraction)
                .fillMaxHeight()
                .background(Color.White),
        )
        if (targetFraction != null) {
            // Portion entre la position courante et la cible, puis le curseur de la cible.
            val start = minOf(playedFraction, targetFraction)
            val end = maxOf(playedFraction, targetFraction)
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * start)
                    .width(trackWidth * (end - start))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            )
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * targetFraction - 8.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * playedFraction - 7.dp, y = (-3).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun PauseGlyph() {
    Canvas(
        modifier = Modifier
            .size(96.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
    ) {
        val barWidth = size.width * 0.12f
        val barHeight = size.height * 0.4f
        val top = (size.height - barHeight) / 2
        val gap = size.width * 0.08f
        val left1 = size.width / 2 - gap / 2 - barWidth
        val left2 = size.width / 2 + gap / 2
        drawLine(Color.White, Offset(left1 + barWidth / 2, top), Offset(left1 + barWidth / 2, top + barHeight), strokeWidth = barWidth, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(left2 + barWidth / 2, top), Offset(left2 + barWidth / 2, top + barHeight), strokeWidth = barWidth, cap = StrokeCap.Round)
    }
}

@Suppress("unused")
private fun playTriangle(size: Float): Path = Path().apply {
    moveTo(size * 0.3f, size * 0.2f)
    lineTo(size * 0.8f, size * 0.5f)
    lineTo(size * 0.3f, size * 0.8f)
    close()
}
