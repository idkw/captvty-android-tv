package dev.valentin.replaytv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.valentin.replaytv.model.CatalogRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * État d'un écran de navigation, gardé tant que l'écran reste dans la pile : quand on ouvre une
 * fiche puis qu'on revient, le curseur revient sur la carte qu'on avait quittée, au même défilement.
 * Les vignettes ne se rechargent pas, sauf si une lecture a été lancée entre-temps ([needsReload]) :
 * la chaîne a pu publier un épisode pendant ce temps.
 */
class ScreenMemory {
    var rows by mutableStateOf<LoadState<List<CatalogRow>>>(LoadState.Loading)
    var query by mutableStateOf("")
    var needsReload = false

    /** Carte sélectionnée, par URL : un rechargement peut insérer un épisode et décaler les positions. */
    var focusedUrl: String? = null

    val column = LazyListState()
    val grid = LazyGridState()
    private val rowStates = mutableMapOf<Int, LazyListState>()

    fun row(index: Int): LazyListState = rowStates.getOrPut(index) { LazyListState() }

    /**
     * Charge les rangées à la première visite, ou les recharge si une lecture a eu lieu depuis. Pendant
     * le rechargement, l'ancienne liste reste affichée ; si la chaîne ne répond pas, elle est gardée.
     */
    suspend fun loadRows(load: suspend () -> List<CatalogRow>) {
        val loaded = rows is LoadState.Loaded
        if (loaded && !needsReload) return
        needsReload = false
        val result = runCatching { withContext(Dispatchers.Default) { load() } }
        rows = result.fold(
            onSuccess = { LoadState.Loaded(it) },
            onFailure = { if (loaded) rows else LoadState.Error(it.message ?: it.toString()) },
        )
    }
}
