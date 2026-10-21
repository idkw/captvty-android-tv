package dev.valentin.replaytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.library.InProgress
import dev.valentin.replaytv.library.Library
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.player.PlaybackPositions
import dev.valentin.replaytv.player.formatClock

/** Collections marquées d'une étoile, de la plus récemment ajoutée à la plus ancienne. */
@Composable
fun FavoritesScreen(app: ReplayTvApp, onOpen: (CatalogItem) -> Unit, memory: ScreenMemory) {
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    val items = remember(favorites) { favorites.mapNotNull { it.toCollection() } }
    LibraryGrid(
        title = "Favoris",
        empty = "Aucun favori. Dans une collection d'épisodes, « ☆ Ajouter aux favoris » l'ajoute ici.",
        items = items,
        onClick = onOpen,
        memory = memory,
    )
}

/**
 * Vidéos commencées et pas terminées, de la plus récemment regardée à la plus ancienne. Un clic
 * propose de reprendre la lecture ou de retirer la vidéo de la liste.
 */
@Composable
fun InProgressScreen(app: ReplayTvApp, onResume: (InProgress) -> Unit, memory: ScreenMemory) {
    val context = LocalContext.current
    val positions = remember { PlaybackPositions(context) }
    var entries by remember { mutableStateOf(emptyList<InProgress>()) }
    var selected by remember { mutableStateOf<InProgress?>(null) }

    fun reload() {
        entries = Library.inProgress(app.library.played(), positions::get, positions::updatedAt)
    }

    // Relu à chaque retour à l'écran : la lecture a pu avancer ou se terminer entre-temps.
    LifecycleResumeEffect(Unit) {
        reload()
        onPauseOrDispose { }
    }

    val cards = remember(entries) {
        entries.map { entry ->
            val total = entry.video.durationSeconds?.let { " / " + formatClock(it * 1000L) }.orEmpty()
            entry.video.copy(subtitle = "${entry.video.source.label} · reprise à ${formatClock(entry.positionMs)}$total", durationSeconds = null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryGrid(
            title = "En cours",
            empty = "Aucune vidéo en cours. Une vidéo commencée et pas terminée apparaît ici.",
            items = cards,
            onClick = { card -> selected = entries.firstOrNull { it.video.url == card.url } },
            memory = memory,
            requestFocus = selected == null,
        )
        selected?.let { entry ->
            InProgressDialog(
                entry = entry,
                onResume = {
                    selected = null
                    onResume(entry)
                },
                onRestart = {
                    positions.clear(entry.video.url)
                    selected = null
                    onResume(entry)
                },
                onRemove = {
                    positions.clear(entry.video.url)
                    app.library.forgetPlayed(entry.video.url)
                    selected = null
                    reload()
                },
                onDismiss = { selected = null },
            )
        }
    }
}

@Composable
private fun InProgressDialog(entry: InProgress, onResume: () -> Unit, onRestart: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .trapFocus()
                .width(760.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.video.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${entry.video.source.label} · arrêté à ${formatClock(entry.positionMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onResume, modifier = Modifier.focusRequester(focus)) { Text("Reprendre la lecture") }
                Button(onClick = onRestart) { Text("Recommencer") }
                Button(onClick = onRemove) { Text("Retirer de « En cours »") }
                Button(onClick = onDismiss) { Text("Annuler") }
            }
        }
    }
}

@Composable
private fun <T : CatalogItem> LibraryGrid(
    title: String,
    empty: String,
    items: List<T>,
    onClick: (T) -> Unit,
    memory: ScreenMemory,
    requestFocus: Boolean = true,
) {
    // Focus sur la carte quittée en dernier, retrouvée par son URL (la liste « En cours » se
    // réordonne entre deux visites), sinon sur la première.
    val targetFocus = remember { FocusRequester() }
    val targetIndex = items.indexOfFirst { it.url == memory.focusedUrl }.coerceAtLeast(0)
    LaunchedEffect(items.isEmpty(), requestFocus) {
        if (items.isNotEmpty() && requestFocus) {
            withFrameNanos { }
            runCatching { targetFocus.requestFocus() }
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 48.dp))
        // La vignette sélectionnée est agrandie par l'effet de focus : la grille garde une marge
        // haute pour ne pas la rogner sous le titre.
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) {
            Message(empty)
        } else {
            LazyVerticalGrid(
                state = memory.grid,
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 48.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.url }) { index, item ->
                    val target = if (index == targetIndex) Modifier.focusRequester(targetFocus) else Modifier
                    @Suppress("UNCHECKED_CAST")
                    ItemCard(
                        item,
                        { onClick(it as T) },
                        target.onFocusChanged { if (it.isFocused) memory.focusedUrl = item.url },
                    )
                }
            }
        }
    }
}
