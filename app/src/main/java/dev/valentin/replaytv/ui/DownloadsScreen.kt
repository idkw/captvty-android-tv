package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.download.DownloadEntry
import dev.valentin.replaytv.download.DownloadStatus
import dev.valentin.replaytv.model.formatDuration

@Composable
fun DownloadsScreen(app: ReplayTvApp, onPlay: (uri: String, title: String, resumeKey: String) -> Unit) {
    val entries by app.downloads.entries.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(entries.isEmpty()) {
        if (entries.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Téléchargements", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Dossier : ${app.downloads.directory.absolutePath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (entries.isEmpty()) {
            Text("Aucun téléchargement pour l'instant.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                itemsIndexed(entries, key = { _, e -> e.meta.id }) { index, entry ->
                    DownloadRow(app, entry, onPlay, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(app: ReplayTvApp, entry: DownloadEntry, onPlay: (String, String, String) -> Unit, firstButton: Modifier = Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                entry.meta.source,
                formatDuration(entry.meta.durationSeconds),
                statusLabel(entry),
            ).joinToString(" · ")
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.status is DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (entry.status) {
            DownloadStatus.Done -> {
                Button(onClick = { onPlay(entry.file.toURI().toString(), entry.meta.title, entry.meta.sourceUrl) }, modifier = firstButton) { Text("Lire") }
                Button(onClick = { app.downloads.delete(entry) }) { Text("Supprimer") }
            }
            is DownloadStatus.Failed -> Button(onClick = { app.downloads.delete(entry) }, modifier = firstButton) { Text("Retirer") }
            else -> Button(onClick = { app.downloads.cancel(entry) }, modifier = firstButton) { Text("Annuler") }
        }
    }
}

private fun statusLabel(entry: DownloadEntry): String = when (val s = entry.status) {
    DownloadStatus.Queued -> "en attente"
    is DownloadStatus.Running -> buildString {
        append("${s.progress.toInt()} %")
        if (s.etaSeconds > 0) append(", reste ${formatDuration(s.etaSeconds.toInt()) ?: "${s.etaSeconds} s"}")
    }
    DownloadStatus.Done -> "${entry.file.length() / (1024 * 1024)} Mo"
    is DownloadStatus.Failed -> "échec : ${s.message}"
}
