package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.drm.DrmStream
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.formatDuration
import dev.valentin.replaytv.player.PlaybackPositions
import dev.valentin.replaytv.player.formatClock
import kotlinx.coroutines.launch

/**
 * Fiche d'une vidéo TF1+ / M6+. Les métadonnées viennent du catalogue (pas d'analyse yt-dlp) ;
 * le flux et la licence sont demandés à la chaîne au moment de lire, avec le compte enregistré.
 */
@Composable
fun DrmDetailScreen(
    app: ReplayTvApp,
    video: CatalogItem.Video,
    onPlay: (stream: DrmStream, title: String, resumeKey: String) -> Unit,
    onAccounts: () -> Unit,
) {
    val configured by app.accounts.configured.collectAsStateWithLifecycle()
    val hasAccount = video.source in configured
    var resolving by remember(video.url) { mutableStateOf(false) }
    var error by remember(video.url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val positions = remember { PlaybackPositions(context) }
    var resumeAtMs by remember(video.url) { mutableLongStateOf(positions.get(video.url)) }
    LifecycleResumeEffect(video.url) {
        resumeAtMs = positions.get(video.url)
        onPauseOrDispose { }
    }

    val primaryFocus = remember(video.url) { FocusRequester() }
    LaunchedEffect(video.url, hasAccount) {
        withFrameNanos { }
        runCatching { primaryFocus.requestFocus() }
    }

    fun play() {
        if (resolving) return
        resolving = true
        error = null
        scope.launch {
            runCatching { app.drm.stream(video) }.fold(
                onSuccess = { onPlay(it, video.title, video.url) },
                onFailure = { error = it.message ?: it.toString() },
            )
            resolving = false
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        AsyncImage(
            model = video.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(520.dp).aspectRatio(16f / 9f),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(video.source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(video.title, style = MaterialTheme.typography.headlineMedium)
            video.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            formatDuration(video.durationSeconds)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasAccount) {
                    "Vidéo protégée par DRM : lecture en streaming avec votre compte ${video.source.label}, sans téléchargement."
                } else {
                    "Cette vidéo exige un compte ${video.source.label} (gratuit). Renseignez-le pour la lire."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (resolving) {
                Spacer(Modifier.height(8.dp))
                LoadingBar(modifier = Modifier.width(360.dp))
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasAccount) {
                    Button(onClick = ::play, enabled = !resolving, modifier = Modifier.focusRequester(primaryFocus)) {
                        Text(if (resumeAtMs > 0) "Reprendre à ${formatClock(resumeAtMs)}" else "Lire")
                    }
                    Button(onClick = onAccounts) { Text("Compte ${video.source.label}") }
                } else {
                    Button(onClick = onAccounts, modifier = Modifier.focusRequester(primaryFocus)) {
                        Text("Configurer le compte ${video.source.label}")
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }

            video.description?.let {
                Spacer(Modifier.height(24.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
