package dev.valentin.replaytv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.formatDuration

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

@Composable
fun BrowseScreen(
    title: String,
    key: Any,
    load: suspend () -> List<CatalogRow>,
    onOpen: (CatalogItem) -> Unit,
    memory: ScreenMemory,
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    // Au retour d'une fiche, les rangées déjà chargées sont réutilisées telles quelles.
    LaunchedEffect(key) {
        if (memory.rows is LoadState.Loaded) return@LaunchedEffect
        memory.rows = runCatching { withContext(Dispatchers.Default) { load() } }.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { LoadState.Error(it.message ?: it.toString()) },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
            if (favorite != null) {
                Spacer(Modifier.width(24.dp))
                androidx.tv.material3.Button(onClick = onToggleFavorite) {
                    Text(if (favorite) "★ Dans mes favoris" else "☆ Ajouter aux favoris")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (val s = memory.rows) {
            LoadState.Loading -> {
                Message("Chargement…")
                Spacer(Modifier.height(8.dp))
                LoadingBar(modifier = Modifier.padding(horizontal = 48.dp).width(480.dp))
            }
            is LoadState.Error -> Message("Erreur : ${s.message}", error = true)
            is LoadState.Loaded ->
                if (s.value.isEmpty()) Message("Rien à afficher ici.") else RowsList(s.value, key, onOpen, memory)
        }
    }
}

/**
 * Rangées de cartes, avec le focus posé à l'affichage sur la carte quittée en dernier (la première
 * à la première visite) : à l'arrivée sur l'écran rien n'a le focus et la télécommande serait
 * inerte sans cela. Le défilement et la carte sélectionnée sont gardés dans [memory].
 */
@Composable
fun RowsList(rows: List<CatalogRow>, key: Any, onOpen: (CatalogItem) -> Unit, memory: ScreenMemory, requestFocus: Boolean = true) {
    val targetFocus = remember(key) { FocusRequester() }
    val (targetRow, targetCard) = memory.focusedCard
        ?.takeIf { (row, card) -> row in rows.indices && card in rows[row].items.indices }
        ?: (0 to 0)

    LaunchedEffect(key, rows, requestFocus) {
        if (requestFocus && rows.isNotEmpty()) {
            withFrameNanos { }
            runCatching { targetFocus.requestFocus() }
        }
    }

    LazyColumn(
        state = memory.column,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        itemsIndexed(rows, key = { index, row -> "$index-${row.title}" }) { index, row ->
            CardRow(
                row = row,
                onOpen = onOpen,
                state = memory.row(index),
                targetCard = targetCard.takeIf { index == targetRow },
                targetFocus = targetFocus,
                onFocused = { card -> memory.focusedCard = index to card },
            )
        }
    }
}

@Composable
fun Message(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 48.dp),
    )
}

@Composable
private fun CardRow(
    row: CatalogRow,
    onOpen: (CatalogItem) -> Unit,
    state: LazyListState,
    targetCard: Int?,
    targetFocus: FocusRequester,
    onFocused: (Int) -> Unit,
) {
    Column {
        Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            itemsIndexed(row.items, key = { index, item -> "$index-${item.url}" }) { index, item ->
                val target = if (index == targetCard) Modifier.focusRequester(targetFocus) else Modifier
                ItemCard(item, onOpen, target.onFocusChanged { if (it.isFocused) onFocused(index) })
            }
        }
    }
}

@Composable
fun ItemCard(item: CatalogItem, onOpen: (CatalogItem) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onOpen(item) }, modifier = modifier.width(240.dp)) {
        Column {
            Box {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                if (item is CatalogItem.Video && item.url in LocalWatchedUrls.current) {
                    WatchedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val info = when (item) {
                    is CatalogItem.Video -> formatDuration(item.durationSeconds) ?: item.subtitle
                    is CatalogItem.Collection -> "Collection"
                }
                if (info != null) {
                    Text(
                        info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** URL des vidéos vues jusqu'au bout, fournies par l'écran racine : les cartes y lisent leur coche. */
val LocalWatchedUrls = compositionLocalOf<Set<String>> { emptySet() }

/** Coche verte posée sur la vignette d'une vidéo vue jusqu'au bout. */
@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        drawCircle(Color(0xFF2E9E4F))
        val check = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.36f)
        }
        drawPath(check, Color.White, style = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
