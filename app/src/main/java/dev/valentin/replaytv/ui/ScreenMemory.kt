package dev.valentin.replaytv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.valentin.replaytv.model.CatalogRow

/**
 * État d'un écran de navigation, gardé tant que l'écran reste dans la pile : quand on ouvre une
 * fiche puis qu'on revient, les vignettes ne se rechargent pas et le curseur revient sur la carte
 * qu'on avait quittée, au même défilement.
 */
class ScreenMemory {
    var rows by mutableStateOf<LoadState<List<CatalogRow>>>(LoadState.Loading)
    var query by mutableStateOf("")

    /** Carte sélectionnée dans des rangées : (rangée, carte). */
    var focusedCard: Pair<Int, Int>? = null

    /** Carte sélectionnée dans une grille, par URL : l'ordre d'une grille peut changer entre deux visites. */
    var focusedUrl: String? = null

    val column = LazyListState()
    val grid = LazyGridState()
    private val rowStates = mutableMapOf<Int, LazyListState>()

    fun row(index: Int): LazyListState = rowStates.getOrPut(index) { LazyListState() }
}
