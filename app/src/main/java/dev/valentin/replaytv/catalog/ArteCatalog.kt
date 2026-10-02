package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
import dev.valentin.replaytv.ytdlp.YtDlp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * Arte expose sans authentification l'API EMAC qui alimente son site
 * (`/api/emac/v4/fr/web/pages/{CODE}`) et l'API « player » qui décrit une vidéo.
 * Les collections (RC-xxxxxx) exigent un jeton : on passe par yt-dlp pour les lister.
 */
class ArteCatalog(private val http: OkHttpClient, private val ytDlp: YtDlp) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun page(code: String): List<CatalogRow> {
        val root = json.parseToJsonElement(http.getString("$EMAC/pages/$code", accept = "application/json"))
        return root.arr("zones").mapNotNull { zone ->
            val title = zone.str("title") ?: return@mapNotNull null
            if (title.contains("(event teaser)")) return@mapNotNull null
            val items = zone.obj("content").arr("data").mapNotNull { it.asObjectOrNull()?.let(::teaser) }
            if (items.isEmpty()) null else CatalogRow(title, items)
        }
    }

    private fun teaser(teaser: JsonObject): CatalogItem? {
        val url = teaser.str("url") ?: return null
        val title = teaser.str("title")?.trim() ?: return null
        val kind = teaser.obj("kind")
        val image = teaser.obj("mainImage").str("url")?.replace("__SIZE__", "400x225")
        return when {
            kind.str("code") == "SHOW" ->
                CatalogItem.Video(Source.ARTE, title, teaser.str("subtitle"), image, url, teaser.int("duration"))
            kind.bool("isCollection") == true ->
                CatalogItem.Collection(Source.ARTE, title, teaser.str("subtitle"), image, url)
            else -> null
        }
    }

    suspend fun collection(url: String): List<CatalogRow> = coroutineScope {
        val gate = Semaphore(4)
        val episodes = ytDlp.listPlaylist(url)
            .map { episodeUrl -> async { gate.withPermit { episode(episodeUrl) } } }
            .awaitAll()
            .filterNotNull()
        if (episodes.isEmpty()) emptyList() else listOf(CatalogRow("Épisodes", episodes))
    }

    private suspend fun episode(url: String): CatalogItem.Video? {
        val id = VIDEO_ID.find(url)?.groupValues?.get(1) ?: return null
        val metadata = runCatching {
            json.parseToJsonElement(http.getString("$PLAYER/config/fr/$id", accept = "application/json"))
        }.getOrNull().obj("data").obj("attributes").obj("metadata")
        return CatalogItem.Video(
            source = Source.ARTE,
            title = metadata.str("title") ?: id,
            subtitle = metadata.str("subtitle"),
            imageUrl = metadata.arr("images").firstOrNull().str("url"),
            url = url,
            durationSeconds = metadata.obj("duration").int("seconds"),
        )
    }

    companion object {
        private const val EMAC = "https://api.arte.tv/api/emac/v4/fr/web"
        private const val PLAYER = "https://api.arte.tv/api/player/v2"
        private val VIDEO_ID = Regex("""/videos/(\d{6}-\d{3}-[A-Z])/""")
    }
}
