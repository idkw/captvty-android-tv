package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * Recherche dans toutes les chaînes : chaque source est interrogée en parallèle (moteur de recherche
 * de la chaîne, ou liste préchargée pour M6+) et ses résultats s'affichent dès qu'ils arrivent, une
 * rangée par source. La recherche et les résultats sont gardés dans [memory] pour le retour d'une
 * fiche.
 */
@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(app: ReplayTvApp, onOpen: (CatalogItem) -> Unit, memory: ScreenMemory) {
    var query by memory::query
    val fieldFocus = remember { FocusRequester() }
    // La touche « Terminé » du clavier lance la recherche sans attendre la fin du délai de saisie.
    val submitted = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    // À l'arrivée, le curseur va dans le champ ; au retour d'une fiche, il revient sur la carte ouverte.
    LaunchedEffect(Unit) {
        if (memory.focusedUrl == null) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        // Sur un clavier de télécommande, passer d'une lettre à la suivante prend plus d'une seconde :
        // la recherche attend une pause plus longue que cela, pour ne pas partir à chaque caractère.
        merge(snapshotFlow { query.trim() }.debounce(SEARCH_DEBOUNCE_MS), submitted)
            .distinctUntilChanged()
            .collectLatest { text ->
                if (text == memory.searchedQuery && !memory.needsReload) return@collectLatest
                memory.needsReload = false
                memory.searchedQuery = text
                if (text.length < MIN_QUERY_LENGTH) {
                    memory.searchResults = emptyMap()
                    return@collectLatest
                }
                memory.searchResults = SOURCES.associateWith { LoadState.Loading }
                coroutineScope {
                    SOURCES.forEach { source ->
                        launch {
                            // Hors du fil principal : réponses à analyser, et pour TF1+ parfois la
                            // découverte des requêtes du site, qui bloquait l'application.
                            val result = runCatching { withContext(Dispatchers.Default) { app.catalog.search(source, text) } }.fold(
                                onSuccess = { LoadState.Loaded(it) },
                                onFailure = { LoadState.Error(it.message ?: it.toString()) },
                            )
                            memory.searchResults = memory.searchResults + (source to result)
                        }
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 48.dp)) {
            Text("Rechercher", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(24.dp))
            TvTextField(
                value = query,
                onValueChange = {
                    query = it
                    // Nouvelle recherche : la carte retenue appartenait aux anciens résultats.
                    memory.focusedUrl = null
                },
                placeholder = "Programme, série, émission…",
                keyboardType = KeyboardType.Text,
                modifier = Modifier.focusRequester(fieldFocus),
                onDone = { submitted.tryEmit(query.trim()) },
            )
        }
        Spacer(Modifier.height(16.dp))

        val results = memory.searchResults
        val rows = SOURCES.mapNotNull { source ->
            val items = (results[source] as? LoadState.Loaded)?.value?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogRow("${source.label} · ${countLabel(items.size)}", items)
        }
        val status = statusLine(results)
        when {
            query.trim().length < MIN_QUERY_LENGTH -> Message("Tapez au moins $MIN_QUERY_LENGTH lettres : la recherche porte sur TF1+, France TV, M6+ et Arte.")
            status != null -> Message(status)
        }
        if (status != null && rows.isNotEmpty()) Spacer(Modifier.height(12.dp))
        if (rows.isNotEmpty()) {
            // Le focus ne quitte le champ que pour revenir sur la carte ouverte avant (retour d'une fiche) :
            // pendant la saisie, les résultats qui arrivent ne doivent pas le lui prendre.
            RowsList(rows, "search", onOpen, memory, requestFocus = memory.focusedUrl != null)
        }
    }
}

private val SOURCES = listOf(Source.TF1, Source.FRANCE_TV, Source.M6, Source.ARTE)
private const val SEARCH_DEBOUNCE_MS = 1_200L
private const val MIN_QUERY_LENGTH = 2

private fun countLabel(count: Int): String = if (count == 1) "1 résultat" else "$count résultats"

/** Sources encore en cours, en échec ou sans résultat ; null quand il n'y a rien à signaler. */
private fun statusLine(results: Map<Source, LoadState<List<CatalogItem>>>): String? {
    if (results.isEmpty()) return null
    val loading = SOURCES.filter { results[it] is LoadState.Loading }.map { it.label }
    val failed = SOURCES.filter { results[it] is LoadState.Error }.map { it.label }
    val empty = SOURCES.filter { (results[it] as? LoadState.Loaded)?.value?.isEmpty() == true }.map { it.label }
    val parts = buildList {
        if (loading.isNotEmpty()) add("Recherche en cours : ${loading.joinToString()}")
        if (failed.isNotEmpty()) add("Indisponible : ${failed.joinToString()}")
        if (empty.isNotEmpty()) add("Aucun résultat : ${empty.joinToString()}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
