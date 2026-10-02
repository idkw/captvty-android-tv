package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
) {
    var state by remember(key) { mutableStateOf<LoadState<List<CatalogRow>>>(LoadState.Loading) }
    val firstCardFocus = remember(key) { FocusRequester() }

    LaunchedEffect(key) {
        state = runCatching { load() }.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { LoadState.Error(it.message ?: it.toString()) },
        )
    }

    // À l'arrivée sur l'écran rien n'a le focus : la télécommande serait inerte sans ceci.
    LaunchedEffect(state) {
        if (state is LoadState.Loaded) {
            withFrameNanos { }
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            LoadState.Loading -> Message("Chargement…")
            is LoadState.Error -> Message("Erreur : ${s.message}", error = true)
            is LoadState.Loaded ->
                if (s.value.isEmpty()) {
                    Message("Rien à afficher ici.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(bottom = 48.dp),
                    ) {
                        itemsIndexed(s.value, key = { index, row -> "$index-${row.title}" }) { index, row ->
                            CardRow(row, onOpen, firstCardFocus.takeIf { index == 0 })
                        }
                    }
                }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 48.dp),
    )
}

@Composable
private fun CardRow(row: CatalogRow, onOpen: (CatalogItem) -> Unit, firstCardFocus: FocusRequester? = null) {
    Column {
        Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            itemsIndexed(row.items, key = { index, item -> "$index-${item.url}" }) { index, item ->
                ItemCard(item, onOpen, if (index == 0 && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier)
            }
        }
    }
}

@Composable
fun ItemCard(item: CatalogItem, onOpen: (CatalogItem) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onOpen(item) }, modifier = modifier.width(240.dp)) {
        Column {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
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
