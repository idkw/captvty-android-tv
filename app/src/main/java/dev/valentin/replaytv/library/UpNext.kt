package dev.valentin.replaytv.library

import dev.valentin.replaytv.catalog.CatalogRepository
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Épisodes proposés à la fin de la vidéo [forUrl]. */
data class UpNextSuggestions(val forUrl: String, val collection: CatalogItem.Collection, val videos: List<CatalogItem.Video>)

/** Épisode choisi sur l'écran de fin du lecteur, que l'écran principal doit ouvrir et lancer. */
data class UpNextChoice(val video: CatalogItem.Video, val collection: CatalogItem.Collection)

/**
 * Épisodes suivants : calculés dès le début de la lecture, pendant que la vidéo tourne (charger une
 * collection Arte prend plusieurs secondes), puis affichés par le lecteur quand la vidéo se termine.
 */
class UpNext(
    private val catalog: CatalogRepository,
    private val watched: WatchedVideos,
    private val scope: CoroutineScope,
) {
    // Épisodes des collections déjà affichées : évite de recharger celle qu'on vient de parcourir.
    private val episodesByCollection = ConcurrentHashMap<String, List<CatalogItem.Video>>()
    private var job: Job? = null

    private val _suggestions = MutableStateFlow<UpNextSuggestions?>(null)
    val suggestions: StateFlow<UpNextSuggestions?> = _suggestions.asStateFlow()

    private val _choice = MutableStateFlow<UpNextChoice?>(null)
    val choice: StateFlow<UpNextChoice?> = _choice.asStateFlow()

    fun rememberCollection(collection: CatalogItem.Collection, rows: List<CatalogRow>) {
        episodesByCollection[collection.url] = episodesOf(rows)
    }

    /** À appeler au lancement d'une lecture ; sans collection connue, rien n'est proposé. */
    fun prepare(video: CatalogItem.Video, collection: CatalogItem.Collection?) {
        job?.cancel()
        _suggestions.value = null
        if (collection == null) return
        job = scope.launch {
            val episodes = episodesByCollection[collection.url]
                ?: runCatching { episodesOf(catalog.collection(collection)) }.getOrNull()?.also { episodesByCollection[collection.url] = it }
                ?: return@launch
            val videos = nextEpisodes(video, episodes, watched::isWatched)
            if (videos.isNotEmpty()) _suggestions.value = UpNextSuggestions(video.url, collection, videos)
        }
    }

    fun choose(video: CatalogItem.Video) {
        val current = _suggestions.value ?: return
        _choice.value = UpNextChoice(video, current.collection)
    }

    fun consumeChoice() {
        _choice.value = null
    }

    private fun episodesOf(rows: List<CatalogRow>): List<CatalogItem.Video> =
        rows.flatMap { it.items }.filterIsInstance<CatalogItem.Video>().distinctBy { it.url }
}
