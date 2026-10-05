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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Channel
import dev.valentin.replaytv.model.searchKey

/**
 * Les replays d'une chaîne : rangées par rubrique, et un champ de recherche qui filtre tous les
 * programmes et collections chargés (correspondance « contient », sans tenir compte des accents).
 */
@OptIn(FlowPreview::class)
@Composable
fun ChannelScreen(channel: Channel, load: suspend () -> List<CatalogRow>, onOpen: (CatalogItem) -> Unit) {
    var state by remember(channel) { mutableStateOf<LoadState<List<CatalogRow>>>(LoadState.Loading) }
    var query by remember(channel) { mutableStateOf("") }

    // Résultats du dernier filtrage terminé ; null tant que la requête est vide.
    var results by remember(channel) { mutableStateOf<List<CatalogItem>?>(null) }
    var searching by remember(channel) { mutableStateOf(false) }

    LaunchedEffect(channel) {
        state = runCatching { withContext(Dispatchers.Default) { load() } }.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { LoadState.Error(it.message ?: it.toString()) },
        )
    }

    // Index précalculé une fois : la normalisation (accents, casse) ne se refait pas à chaque frappe.
    val rows = (state as? LoadState.Loaded)?.value
    val index = remember(rows) {
        rows.orEmpty().flatMap { it.items }.distinctBy { it.url }.map { SearchEntry(it, "${it.title} ${it.subtitle.orEmpty()}".searchKey()) }
    }

    // La saisie met à jour `query` immédiatement ; le filtrage, débouncé et hors du thread
    // principal, publie ses résultats quand il a fini, sans jamais bloquer le clavier.
    LaunchedEffect(index) {
        snapshotFlow { query.trim().searchKey() }
            .distinctUntilChanged()
            .onEach { searching = it.isNotEmpty() }
            .debounce(SEARCH_DEBOUNCE_MS)
            .collectLatest { queryKey ->
                results = if (queryKey.isEmpty()) null else withContext(Dispatchers.Default) { index.filter { queryKey in it.key }.map { it.item } }
                searching = false
            }
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
                val current = results
                when {
                    query.isBlank() -> if (s.value.isEmpty()) Message("Aucun programme en replay.") else RowsList(s.value, channel, onOpen)
                    current == null -> Message("Recherche…")
                    else -> SearchResults(current, query.trim(), searching, onOpen)
                }
            }
        }
    }
}

private class SearchEntry(val item: CatalogItem, val key: String)

private const val SEARCH_DEBOUNCE_MS = 250L

@Composable
private fun SearchResults(matches: List<CatalogItem>, query: String, searching: Boolean, onOpen: (CatalogItem) -> Unit) {
    Message(
        when {
            matches.isEmpty() && searching -> "Recherche…"
            matches.isEmpty() -> "Aucun programme ne contient « $query »."
            matches.size == 1 -> "1 résultat"
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
        itemsIndexed(matches, key = { _, item -> item.url }) { _, item -> ItemCard(item, onOpen) }
    }
}
