package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Channel
import dev.valentin.replaytv.model.matches
import dev.valentin.replaytv.model.searchKey

/**
 * Les replays d'une chaîne : rangées par rubrique, et un champ de recherche qui filtre tous les
 * programmes et collections chargés (correspondance « contient », sans tenir compte des accents).
 */
@Composable
fun ChannelScreen(channel: Channel, load: suspend () -> List<CatalogRow>, onOpen: (CatalogItem) -> Unit) {
    var state by remember(channel) { mutableStateOf<LoadState<List<CatalogRow>>>(LoadState.Loading) }
    var query by remember(channel) { mutableStateOf("") }

    LaunchedEffect(channel) {
        state = runCatching { load() }.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { LoadState.Error(it.message ?: it.toString()) },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            ChannelLogo(channel, Modifier.size(width = 96.dp, height = 60.dp))
            Spacer(Modifier.width(20.dp))
            Text(channel.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Rechercher un programme…",
                keyboardType = KeyboardType.Text,
            )
        }
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            LoadState.Loading -> {
                Message("Chargement des programmes…")
                Spacer(Modifier.height(8.dp))
                LoadingBar(modifier = Modifier.padding(horizontal = 48.dp).width(480.dp))
            }

            is LoadState.Error -> Message("Erreur : ${s.message}", error = true)

            is LoadState.Loaded -> {
                val queryKey = query.trim().searchKey()
                if (queryKey.isEmpty()) {
                    if (s.value.isEmpty()) Message("Aucun programme en replay.") else RowsList(s.value, channel, onOpen)
                } else {
                    SearchResults(s.value, queryKey, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(rows: List<CatalogRow>, queryKey: String, onOpen: (CatalogItem) -> Unit) {
    val matches = remember(rows, queryKey) {
        rows.flatMap { it.items }.distinctBy { it.url }.filter { it.matches(queryKey) }
    }
    Message(
        when (matches.size) {
            0 -> "Aucun programme ne contient « $queryKey »."
            1 -> "1 résultat"
            else -> "${matches.size} résultats"
        },
    )
    Spacer(Modifier.height(12.dp))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
    ) {
        itemsIndexed(matches, key = { index, item -> "$index-${item.url}" }) { _, item -> ItemCard(item, onOpen) }
    }
}
