package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.model.Section
import dev.valentin.replaytv.model.Sections
import dev.valentin.replaytv.model.Source
import dev.valentin.replaytv.ytdlp.YtDlp

@Composable
fun HomeScreen(app: ReplayTvApp, onSection: (Section) -> Unit, onDownloads: () -> Unit) {
    val ytState by app.ytDlp.state.collectAsStateWithLifecycle()
    val downloads by app.downloads.entries.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Replay TV", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(24.dp))
            Text(
                text = when (val s = ytState) {
                    YtDlp.State.Initializing -> "Initialisation du moteur de téléchargement…"
                    is YtDlp.State.Ready -> "yt-dlp ${s.version ?: ""} prêt"
                    is YtDlp.State.Failed -> "Moteur indisponible : ${s.message}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ytState is YtDlp.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp), modifier = Modifier.fillMaxWidth()) {
            SectionColumn(Source.FRANCE_TV, Sections.franceTv, onSection, Modifier.weight(1f).focusRequester(firstFocus))
            SectionColumn(Source.ARTE, Sections.arte, onSection, Modifier.weight(1f))
            Column(modifier = Modifier.weight(1f)) {
                Text("Bibliothèque", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                val active = downloads.count { it.isActive }
                val done = downloads.size - active
                Button(onClick = onDownloads) {
                    Text(
                        buildString {
                            append("Téléchargements")
                            if (done > 0) append(" · $done")
                            if (active > 0) append(" · $active en cours")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionColumn(source: Source, sections: List<Section>, onSection: (Section) -> Unit, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(source.label, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sections, key = { it.code }) { section ->
                Button(onClick = { onSection(section) }, modifier = Modifier.fillMaxWidth()) {
                    Text(section.label)
                }
            }
        }
    }
}
