package dev.valentin.replaytv.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vidéos regardées jusqu'au bout (clé = URL de la page, comme la position de reprise). Une vidéo
 * sort de la liste quand on la relance depuis le début.
 */
class WatchedVideos(context: Context) {

    private val prefs = context.getSharedPreferences("watched", Context.MODE_PRIVATE)
    private val _urls = MutableStateFlow(prefs.getStringSet(KEY, null).orEmpty().toSet())
    val urls: StateFlow<Set<String>> = _urls.asStateFlow()

    fun isWatched(url: String): Boolean = url in _urls.value

    fun markWatched(url: String) = update { it + url }

    fun clear(url: String) = update { it - url }

    private fun update(change: (Set<String>) -> Set<String>) {
        val current = _urls.value
        val updated = change(current)
        if (updated == current) return
        _urls.value = updated
        prefs.edit().putStringSet(KEY, updated).apply()
    }

    private companion object {
        const val KEY = "urls"
    }
}
