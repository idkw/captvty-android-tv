package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Catalogue TF1+ lu sans compte sur l'API GraphQL du site (`tf1.fr/graphql/web`). Les requêtes
 * sont des « persisted queries » désignées par un identifiant : il change quand TF1 met son site à jour.
 * Seules les vidéos gratuites (droit `BASIC`) sont proposées ; `MAX` exige l'abonnement payant.
 */
class Tf1Catalog(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Une rangée par chaîne : les programmes de la catégorie qui ont au moins un replay. */
    suspend fun page(categoryId: String): List<CatalogRow> = coroutineScope {
        CHANNELS.map { (slug, label) ->
            async {
                val programs = programs(slug).filter { program ->
                    program.arr("categories").any { it.str("id") == categoryId } &&
                        (program.obj("replay").int("total") ?: 0) > 0
                }
                CatalogRow(label, programs.mapNotNull(::program)).takeIf { it.items.isNotEmpty() }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun program(url: String): List<CatalogRow> {
        val slug = PROGRAM_SLUG.find(url)?.groupValues?.get(1) ?: return emptyList()
        val variables = buildJsonObject {
            put("programSlug", slug)
            put("offset", 0)
            put("limit", 50)
            putJsonObject("sort") { put("type", "DATE"); put("order", "DESC") }
            putJsonArray("types") { add("REPLAY") }
        }
        val videos = query(QUERY_PROGRAM_VIDEOS, variables).obj("data").obj("programBySlug").obj("videos").arr("items")
            .filter { video -> video.arr("rights").any { (it as? JsonPrimitive)?.content == "BASIC" } }
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::video)
        return if (videos.isEmpty()) emptyList() else listOf(CatalogRow("Replay gratuits", videos))
    }

    private suspend fun programs(channel: String): List<JsonElement> {
        val variables = buildJsonObject {
            putJsonObject("context") {
                put("persona", "PERSONA_2")
                put("application", "WEB")
                put("device", "DESKTOP")
                put("os", "WINDOWS")
            }
            putJsonObject("filter") { put("channel", channel) }
            put("offset", 0)
            put("limit", 500)
        }
        return query(QUERY_PROGRAMS, variables).obj("data").obj("programs").arr("items")
    }

    private fun program(program: JsonElement): CatalogItem.Collection? {
        val slug = program.str("slug") ?: return null
        val channel = program.obj("mainChannel").str("slug") ?: "tf1"
        return CatalogItem.Collection(
            source = Source.TF1,
            title = program.str("name") ?: return null,
            subtitle = null,
            imageUrl = program.obj("decoration").obj("thumbnail").arr("sources").pickSource(),
            url = "$BASE/$channel/$slug/videos",
        )
    }

    private fun video(video: JsonObject): CatalogItem.Video? {
        val decoration = video.obj("decoration")
        val thumbnail = decoration.arr("images").firstOrNull { it.str("type") == "THUMBNAIL_LARGE" }
            ?: decoration.arr("images").firstOrNull { it.str("type") == "THUMBNAIL" }
        return CatalogItem.Video(
            source = Source.TF1,
            title = decoration.str("label") ?: return null,
            subtitle = decoration.str("subLabel"),
            imageUrl = thumbnail.arr("sources").pickSource(),
            url = video.str("url") ?: return null,
            durationSeconds = video.obj("playingInfos").int("duration"),
            streamId = video.str("id") ?: return null,
            description = decoration.str("description")?.takeIf { it.isNotBlank() },
        )
    }

    // Les images existent en plusieurs tailles, la plus grande en premier : ~600 px suffit pour une carte.
    private fun List<JsonElement>.pickSource(): String? =
        (firstOrNull { (it.str("width")?.toIntOrNull() ?: 0) in 500..700 } ?: firstOrNull()).str("url")

    private suspend fun query(id: String, variables: JsonObject): JsonElement {
        val url = GRAPHQL.toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("variables", variables.toString())
            .build()
        return json.parseToJsonElement(
            http.getString(url.toString(), accept = "application/json", headers = mapOf("Content-Type" to "application/json")),
        )
    }

    companion object {
        private const val BASE = "https://www.tf1.fr"
        private const val GRAPHQL = "$BASE/graphql/web"
        private const val QUERY_PROGRAMS = "483ce0f"
        private const val QUERY_PROGRAM_VIDEOS = "a6f9cf0e"
        private val PROGRAM_SLUG = Regex("""tf1\.fr/[^/]+/([^/]+)/videos""")
        private val CHANNELS = listOf(
            "tf1" to "TF1",
            "tmc" to "TMC",
            "tfx" to "TFX",
            "tf1-series-films" to "TF1 Séries Films",
            "lci" to "LCI",
        )
    }
}
