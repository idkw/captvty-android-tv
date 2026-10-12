package dev.valentin.replaytv.library

import android.content.Context
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Élément de catalogue tel qu'on le conserve sur la box (favori ou vidéo commencée). */
@Serializable
data class StoredItem(
    val source: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val url: String,
    val durationSeconds: Int? = null,
    val streamId: String? = null,
    val description: String? = null,
    val savedAt: Long = 0L,
) {
    fun toVideo(): CatalogItem.Video? = sourceOrNull()?.let {
        CatalogItem.Video(it, title, subtitle, imageUrl, url, durationSeconds, streamId, description)
    }

    fun toCollection(): CatalogItem.Collection? = sourceOrNull()?.let {
        CatalogItem.Collection(it, title, subtitle, imageUrl, url)
    }

    private fun sourceOrNull(): Source? = Source.entries.firstOrNull { it.name == source }

    companion object {
        fun of(item: CatalogItem, now: Long): StoredItem = when (item) {
            is CatalogItem.Video -> StoredItem(
                item.source.name, item.title, item.subtitle, item.imageUrl, item.url,
                item.durationSeconds, item.streamId, item.description, now,
            )
            is CatalogItem.Collection -> StoredItem(item.source.name, item.title, item.subtitle, item.imageUrl, item.url, savedAt = now)
        }
    }
}

/** Une vidéo commencée et pas terminée, avec sa position de reprise. */
data class InProgress(val video: CatalogItem.Video, val positionMs: Long, val lastPlayedAt: Long)

/**
 * Bibliothèque personnelle : collections favorites et vidéos lancées. Les positions de reprise
 * restent dans [dev.valentin.replaytv.player.PlaybackPositions] ; une vidéo est « en cours » tant
 * qu'elle y a une position (elle en sort quand elle est vue jusqu'au bout).
 */
class Library(context: Context, private val now: () -> Long = System::currentTimeMillis) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StoredItem.serializer())

    private val _favorites = MutableStateFlow(read(KEY_FAVORITES))
    val favorites: StateFlow<List<StoredItem>> = _favorites.asStateFlow()

    fun isFavorite(url: String): Boolean = _favorites.value.any { it.url == url }

    fun toggleFavorite(collection: CatalogItem.Collection) {
        val current = _favorites.value
        val updated = if (current.any { it.url == collection.url }) {
            current.filterNot { it.url == collection.url }
        } else {
            listOf(StoredItem.of(collection, now())) + current
        }
        write(KEY_FAVORITES, updated)
        _favorites.value = updated
    }

    /** À appeler au lancement d'une lecture : mémorise la fiche pour l'onglet « En cours ». */
    fun recordPlay(video: CatalogItem.Video) {
        val updated = listOf(StoredItem.of(video, now())) + read(KEY_PLAYED).filterNot { it.url == video.url }
        write(KEY_PLAYED, updated.take(MAX_PLAYED))
    }

    fun forgetPlayed(url: String) {
        write(KEY_PLAYED, read(KEY_PLAYED).filterNot { it.url == url })
    }

    fun played(): List<StoredItem> = read(KEY_PLAYED)

    private fun read(key: String): List<StoredItem> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun write(key: String, items: List<StoredItem>) {
        prefs.edit().putString(key, json.encodeToString(listSerializer, items)).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_PLAYED = "played"
        private const val MAX_PLAYED = 200

        /**
         * Vidéos commencées non terminées, de la plus récemment regardée à la plus ancienne.
         * [positionOf] donne la position de reprise (0 si aucune), [watchedAt] l'heure de la
         * dernière position enregistrée (0 si inconnue, on retombe alors sur l'heure de lancement).
         */
        fun inProgress(played: List<StoredItem>, positionOf: (String) -> Long, watchedAt: (String) -> Long): List<InProgress> =
            played.mapNotNull { item ->
                val position = positionOf(item.url)
                val video = item.toVideo()
                if (position <= 0 || video == null) null else InProgress(video, position, maxOf(watchedAt(item.url), item.savedAt))
            }.sortedByDescending { it.lastPlayedAt }
    }
}
