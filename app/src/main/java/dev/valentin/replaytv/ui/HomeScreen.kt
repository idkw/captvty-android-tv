package dev.valentin.replaytv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.tv.material3.LocalContentColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.BuildConfig
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.model.Channel
import dev.valentin.replaytv.model.Channels
import dev.valentin.replaytv.ytdlp.YtDlp

@Composable
fun HomeScreen(
    app: ReplayTvApp,
    onChannel: (Channel) -> Unit,
    onThemes: () -> Unit,
    onDownloads: () -> Unit,
    onAccounts: () -> Unit,
    onCheckUpdates: () -> Unit,
    onFavorites: () -> Unit,
    onInProgress: () -> Unit,
    onSearch: () -> Unit,
) {
    val ytState by app.ytDlp.state.collectAsStateWithLifecycle()
    val downloads by app.downloads.entries.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 48.dp)) {
            Text("Replay TV", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            // Une loupe plutôt qu'un libellé : la rangée de boutons tient tout juste dans la largeur.
            Button(onClick = onSearch) { SearchIcon() }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onInProgress) { Text("En cours") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onFavorites) { Text("Favoris") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onThemes) { Text("Par thème") }
            Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.width(12.dp))
            Button(onClick = onAccounts) { Text("Comptes") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onCheckUpdates) { Text("v${BuildConfig.VERSION_NAME}") }
        }
        // Le moteur de téléchargement ne se signale qu'en cas de panne.
        (ytState as? YtDlp.State.Failed)?.message?.let {
            Text(
                "Moteur de téléchargement indisponible : $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }

        Spacer(Modifier.height(36.dp))
        Text("Chaînes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(12.dp))
        ChannelRow(Channels.main, onChannel, firstFocus)

        Spacer(Modifier.height(32.dp))
        Text("Autres chaînes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(12.dp))
        ChannelRow(Channels.others, onChannel, null)
    }
}

@Composable
private fun ChannelRow(channels: List<Channel>, onChannel: (Channel) -> Unit, firstFocus: FocusRequester?) {
    // Rangée non défilante : toutes les chaînes doivent rester visibles d'un coup d'œil.
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
    ) {
        channels.forEachIndexed { index, channel ->
            ChannelTile(
                channel,
                onChannel,
                Modifier.weight(1f).then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            )
        }
    }
}

@Composable
private fun ChannelTile(channel: Channel, onChannel: (Channel) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(onClick = { onChannel(channel) }, modifier = Modifier.fillMaxWidth().height(84.dp)) {
            ChannelLogoBox(channel, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            channel.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Loupe dessinée : les polices de la box n'ont pas toutes le symbole. Prend la couleur du texte du bouton. */
@Composable
private fun SearchIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = size.width * 0.12f
        drawCircle(color, radius = size.width * 0.3f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(stroke))
        drawLine(color, Offset(size.width * 0.64f, size.height * 0.64f), Offset(size.width * 0.9f, size.height * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
