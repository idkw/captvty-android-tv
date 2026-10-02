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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.download.DownloadStatus
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.formatDuration
import dev.valentin.replaytv.ytdlp.MediaInfo

@Composable
fun DetailScreen(app: ReplayTvApp, video: CatalogItem.Video, onPlay: (uri: String, title: String) -> Unit) {
    var info by remember(video.url) { mutableStateOf<LoadState<MediaInfo>>(LoadState.Loading) }
    val downloads by app.downloads.entries.collectAsStateWithLifecycle()
    val entry = downloads.firstOrNull { it.meta.sourceUrl == video.url }

    LaunchedEffect(video.url) {
        info = runCatching { app.ytDlp.probe(video.url) }.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { LoadState.Error(it.message?.lines()?.lastOrNull { l -> l.isNotBlank() } ?: it.toString()) },
        )
    }

    val media = (info as? LoadState.Loaded)?.value
    val primaryFocus = remember(video.url) { FocusRequester() }

    LaunchedEffect(media) {
        if (media != null) {
            withFrameNanos { }
            runCatching { primaryFocus.requestFocus() }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        AsyncImage(
            model = media?.thumbnailUrl ?: video.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(520.dp).aspectRatio(16f / 9f),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(video.source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(media?.title ?: video.title, style = MaterialTheme.typography.headlineMedium)
            video.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            formatDuration(media?.durationSeconds ?: video.durationSeconds)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            val statusText = when (val s = info) {
                LoadState.Loading -> "Analyse de la vidéo par yt-dlp…"
                is LoadState.Error -> "Impossible d'analyser la vidéo : ${s.message}"
                is LoadState.Loaded -> when {
                    s.value.hasDrm -> "Cette vidéo est protégée par DRM : ni lecture ni téléchargement possibles."
                    s.value.streamUrl == null -> "Pas de flux HLS détecté : lecture directe indisponible, téléchargement possible."
                    else -> "Flux détecté."
                }
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (info is LoadState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info is LoadState.Loading) {
                Spacer(Modifier.height(8.dp))
                LoadingBar(modifier = Modifier.width(360.dp))
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { media?.streamUrl?.let { onPlay(it, media.title) } },
                    enabled = media?.streamUrl != null && media.hasDrm.not(),
                    modifier = Modifier.focusRequester(primaryFocus),
                ) { Text("Lire en direct") }

                if (entry == null) {
                    Button(
                        onClick = { app.downloads.enqueue(video, media) },
                        enabled = media != null && !media.hasDrm,
                    ) { Text("Télécharger") }
                } else {
                    when (val status = entry.status) {
                        DownloadStatus.Queued -> Button(onClick = { app.downloads.cancel(entry) }) { Text("En attente · annuler") }

                        is DownloadStatus.Running -> Button(onClick = { app.downloads.cancel(entry) }) {
                            Text("${status.progress.toInt()} % · annuler")
                        }

                        DownloadStatus.Done -> Button(onClick = { onPlay(entry.file.toURI().toString(), entry.meta.title) }) {
                            Text("Lire le fichier téléchargé")
                        }

                        is DownloadStatus.Failed -> Button(onClick = {
                            app.downloads.delete(entry)
                            app.downloads.enqueue(video, media)
                        }) { Text("Échec · réessayer") }
                    }
                }
            }
            (entry?.status as? DownloadStatus.Failed)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }

            media?.description?.let {
                Spacer(Modifier.height(24.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
