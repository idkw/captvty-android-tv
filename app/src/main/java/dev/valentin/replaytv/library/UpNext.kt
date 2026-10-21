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

/** Épisodes proposés à la fin de la vidéo [forUrl]. */
data class UpNextSuggestions(val forUrl: String, val collection: CatalogItem.Collection, val videos: List<CatalogItem.Video>)

/** Épisode choisi sur l'écran de fin du lecteur, que l'écran principal doit ouvrir et lancer. */
data class UpNextChoice(val video: CatalogItem.Video, val collection: CatalogItem.Collection)

/**
 * Épisodes suivants, affichés par le lecteur quand la vidéo se termine. La collection est lue sur
 * l'API de la chaîne au début de la lecture, puis relue à l'approche de la fin ([refresh]) : un
 * épisode a pu paraître pendant la lecture, et charger une collection Arte prend plusieurs secondes.
 */
class UpNext(
    private val catalog: CatalogRepository,
    private val watched: WatchedVideos,
    private val scope: CoroutineScope,
) {
    private var playing: Pair<CatalogItem.Video, CatalogItem.Collection>? = null
    private var job: Job? = null

    private val _suggestions = MutableStateFlow<UpNextSuggestions?>(null)
    val suggestions: StateFlow<UpNextSuggestions?> = _suggestions.asStateFlow()

    private val _choice = MutableStateFlow<UpNextChoice?>(null)
    val choice: StateFlow<UpNextChoice?> = _choice.asStateFlow()

    /** À appeler au lancement d'une lecture ; sans collection connue, rien n'est proposé. */
    fun prepare(video: CatalogItem.Video, collection: CatalogItem.Collection?) {
        job?.cancel()
        _suggestions.value = null
        playing = collection?.let { video to it }
        refresh(video.url)
    }

    /**
     * Relit la collection de la vidéo [forUrl] sur l'API de la chaîne et recalcule les suggestions.
     * Si la chaîne ne répond pas, les suggestions précédentes restent.
     */
    fun refresh(forUrl: String) {
        val (video, collection) = playing?.takeIf { it.first.url == forUrl } ?: return
        job?.cancel()
        job = scope.launch {
            val episodes = runCatching { episodesOf(catalog.collection(collection)) }.getOrNull() ?: return@launch
            val videos = nextEpisodes(video, episodes, watched::isWatched)
            _suggestions.value = if (videos.isEmpty()) null else UpNextSuggestions(video.url, collection, videos)
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
