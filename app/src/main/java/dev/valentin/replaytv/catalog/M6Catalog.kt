package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

/**
 * Catalogue M6+ lu sans compte sur l'API « middleware » de l'application Android de M6.
 * Les vidéos rattachées à une offre payante (`freemium_packs` non vide) sont écartées.
 */
class M6Catalog(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun page(folderId: String): List<CatalogRow> {
        val programs = get("$MIDDLEWARE/folders/$folderId/programs?limit=999&offset=0&csa=6&with=parentcontext")
        val items = (programs as? JsonArray).orEmpty().mapNotNull(::program)
        return if (items.isEmpty()) emptyList() else listOf(CatalogRow("Programmes", items))
    }

    suspend fun program(url: String): List<CatalogRow> {
        val programId = PROGRAM_ID.find(url)?.groupValues?.get(1) ?: return emptyList()
        val videos = get("$MIDDLEWARE/programs/$programId/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset=0")
        val items = (videos as? JsonArray).orEmpty()
            .filter { it.arr("freemium_packs").isEmpty() }
            .mapNotNull { video(it, url) }
        return if (items.isEmpty()) emptyList() else listOf(CatalogRow("Épisodes gratuits", items))
    }

    /** Fiche d'une vidéo, avec ses manifestes (`clips[0].assets`), dont l'URL signée expire après quelques heures. */
    suspend fun clip(clipId: String): JsonElement = get("$MIDDLEWARE/videos/$clipId?csa=6&with=clips,freemiumpacks")

    private fun program(program: JsonElement): CatalogItem.Collection? {
        val id = program.int("id") ?: return null
        return CatalogItem.Collection(
            source = Source.M6,
            title = program.str("title") ?: return null,
            subtitle = null,
            imageUrl = image(program, "mea", "vignette"),
            url = "$SITE/${program.str("code") ?: "programme"}-p_$id",
        )
    }

    private fun video(video: JsonElement, programUrl: String): CatalogItem.Video? {
        val clip = video.arr("clips").firstOrNull() ?: return null
        return CatalogItem.Video(
            source = Source.M6,
            title = video.str("title") ?: return null,
            subtitle = video.obj("program").str("title"),
            imageUrl = image(video, "vignette", "mea"),
            url = "$programUrl/${video.str("code") ?: "video"}-c_${clip.int("id")}",
            durationSeconds = video.int("duration"),
            streamId = clip.str("video_id") ?: return null,
            description = video.str("description")?.takeIf { it.isNotBlank() },
        )
    }

    private fun image(element: JsonElement, vararg roles: String): String? {
        val images = element.arr("images")
        val key = roles.firstNotNullOfOrNull { role -> images.firstOrNull { it.str("role") == role }?.str("external_key") }
            ?: return null
        // Le service d'images refuse les paramètres de redimensionnement : seule la version brute est servie.
        return "https://images.6play.fr/v1/images/$key/raw"
    }

    private suspend fun get(url: String): JsonElement =
        json.parseToJsonElement(http.getString(url, accept = "application/json", headers = mapOf("x-customer-name" to "m6web")))

    companion object {
        private const val MIDDLEWARE =
            "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/6play"
        private const val SITE = "https://www.6play.fr"
        private val PROGRAM_ID = Regex("""-p_(\d+)""")
    }
}
