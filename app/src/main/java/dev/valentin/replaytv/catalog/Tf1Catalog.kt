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
import java.io.IOException

/** Le serveur TF1+ ne répond plus à aucune des requêtes connues : l'application doit être mise à jour. */
class Tf1CatalogChangedException :
    IOException("Le catalogue TF1+ a changé, une mise à jour de l'application est nécessaire.")

/**
 * Catalogue TF1+ lu sans compte sur l'API GraphQL du site (`tf1.fr/graphql/web`), qui n'accepte
 * que des requêtes persistées désignées par un identifiant.
 *
 * Chaque besoin suit une chaîne de repli : requête nommée du site actuel, résolue par
 * [Tf1QueryResolver] (et redécouverte si le serveur ne la reconnaît plus), puis identifiant de
 * l'ancienne version du site, encore accepté aujourd'hui. Chaque étape lit sa propre forme de
 * réponse et produit les mêmes éléments de catalogue. Seules les vidéos gratuites (droit `BASIC`)
 * sont proposées ; `MAX` exige l'abonnement payant.
 */
class Tf1Catalog(private val http: OkHttpClient, private val queries: Tf1QueryResolver) {

    /** Programme tel que les deux formes de réponse permettent de le décrire. */
    data class Program(
        val slug: String,
        val name: String,
        val channelSlug: String,
        val channelLabel: String,
        val imageUrl: String?,
        val replayCount: Int,
        /** Regroupement proposé : typologie (site actuel) ou catégorie principale (ancienne requête). */
        val group: String,
    ) {
        fun toItem(): CatalogItem.Collection =
            CatalogItem.Collection(Source.TF1, name, null, imageUrl, "$BASE/$channelSlug/$slug/videos")
    }

    /** Les programmes d'une catégorie (slug du catalogue), une rangée par chaîne. */
    suspend fun page(categorySlug: String): List<CatalogRow> {
        val programs = programsByCategory(categorySlug).filter { it.replayCount > 0 }
        return programs.groupBy { it.channelLabel }.entries
            .sortedByDescending { it.value.size }
            .map { (channel, items) -> CatalogRow(channel, items.sortedBy { it.name.lowercase() }.map { it.toItem() }) }
    }

    /** Tous les programmes d'une chaîne qui ont au moins un replay, groupés par typologie ou catégorie. */
    suspend fun channel(slug: String): List<CatalogRow> {
        val programs = programsByChannel(slug).filter { it.replayCount > 0 }
        return programs.groupBy { it.group }.entries
            .sortedByDescending { it.value.size }
            .map { (label, items) -> CatalogRow(label, items.sortedBy { it.name.lowercase() }.map { it.toItem() }) }
    }

    /** Les replays gratuits d'un programme, à partir de l'URL de sa page. */
    suspend fun program(url: String): List<CatalogRow> {
        val slug = PROGRAM_SLUG.find(url)?.groupValues?.get(1) ?: return emptyList()
        val videos = programVideos(slug)
        return if (videos.isEmpty()) emptyList() else listOf(CatalogRow("Replay gratuits", videos))
    }

    // ---- Programmes --------------------------------------------------------------------------

    private suspend fun programsByChannel(channel: String): List<Program> {
        val current = catalogPrograms(buildJsonObject { put("channel", channel) }, maxPages = CHANNEL_PAGES)
        if (current != null) return current
        val legacy = legacyPrograms(channel) ?: throw Tf1CatalogChangedException()
        return legacy
    }

    private suspend fun programsByCategory(categorySlug: String): List<Program> {
        val current = catalogPrograms(buildJsonObject { put("categorySlug", categorySlug) }, maxPages = 1)
        if (current != null) return current
        // L'ancienne requête ne filtre que par chaîne : on parcourt les chaînes et on garde la catégorie.
        val categoryId = LEGACY_CATEGORY_IDS[categorySlug] ?: throw Tf1CatalogChangedException()
        val perChannel = coroutineScope { LEGACY_CHANNELS.map { async { legacyPrograms(it, categoryId) } }.awaitAll() }
        if (perChannel.all { it == null }) throw Tf1CatalogChangedException()
        return perChannel.filterNotNull().flatten()
    }

    /**
     * `ProgramCatalogDocument` : `data.sliderOfPrograms.items[].program`. `offset` est un numéro de
     * page (comme sur le site), pas un nombre d'éléments. La première page donne le total ; les
     * suivantes sont chargées en parallèle, dans la limite de [maxPages] (3 Mo par page).
     */
    private suspend fun catalogPrograms(filter: JsonObject, maxPages: Int): List<Program>? = coroutineScope {
        val first = named(QUERY_PROGRAM_CATALOG, catalogVariables(filter, page = 0)) ?: return@coroutineScope null
        val slider = first.obj("data").obj("sliderOfPrograms") ?: return@coroutineScope null
        val programs = parseCatalogPrograms(first).toMutableList()
        val total = slider.int("total") ?: programs.size
        val pages = ((total + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtMost(maxPages)
        if (pages > 1 && programs.size >= PAGE_SIZE) {
            (1 until pages).map { page ->
                async { named(QUERY_PROGRAM_CATALOG, catalogVariables(filter, page))?.let(::parseCatalogPrograms).orEmpty() }
            }.awaitAll().forEach { programs += it }
        }
        programs
    }

    private fun catalogVariables(filter: JsonObject, page: Int): JsonObject = buildJsonObject {
        put("limit", PAGE_SIZE)
        put("offset", page)
        put("filter", filter)
    }

    /** Ancienne requête `483ce0f` : `data.programs.items[]`, filtrée sur une chaîne. */
    private suspend fun legacyPrograms(channel: String, categoryId: String? = null): List<Program>? {
        val variables = buildJsonObject {
            putJsonObject("context") {
                put("persona", "PERSONA_2")
                put("application", "WEB")
                put("device", "DESKTOP")
                put("os", "WINDOWS")
            }
            putJsonObject("filter") { put("channel", channel) }
            put("offset", 0)
            put("limit", PAGE_SIZE)
        }
        val response = legacy(LEGACY_QUERY_PROGRAMS, variables) ?: return null
        if (response.obj("data").obj("programs") == null) return null
        return parseLegacyPrograms(response, categoryId)
    }

    // ---- Vidéos ------------------------------------------------------------------------------

    private suspend fun programVideos(slug: String): List<CatalogItem.Video> {
        val variables = buildJsonObject {
            put("programSlug", slug)
            putJsonArray("types") { add("REPLAY") }
            put("limit", VIDEOS_LIMIT)
        }
        named(QUERY_PROGRAM_VIDEOS, variables)?.let { response ->
            if (response.obj("data").obj("programBySlug") != null) return parseProgramVideos(response)
        }
        val legacyVariables = buildJsonObject {
            put("programSlug", slug)
            put("offset", 0)
            put("limit", VIDEOS_LIMIT)
            putJsonObject("sort") { put("type", "DATE"); put("order", "DESC") }
            putJsonArray("types") { add("REPLAY") }
        }
        val response = legacy(LEGACY_QUERY_PROGRAM_VIDEOS, legacyVariables) ?: throw Tf1CatalogChangedException()
        if (response.obj("data").obj("programBySlug") == null) throw Tf1CatalogChangedException()
        return parseLegacyVideos(response)
    }

    // ---- Transport ---------------------------------------------------------------------------

    /**
     * Exécute une requête nommée. Si le serveur ne reconnaît plus l'identifiant, on demande une
     * redécouverte et on réessaie une fois avec le nouvel identifiant. Null quand rien n'aboutit.
     */
    private suspend fun named(name: String, variables: JsonObject): JsonElement? {
        var hash = queries.hash(name) ?: return null
        repeat(2) {
            val result = http.getWithStatus(url(hash, variables), accept = "application/json", headers = HEADERS)
            if (isUnknownQuery(result)) {
                queries.reportUnknown(name)
                val fresh = queries.hash(name)
                if (fresh == null || fresh == hash) return null
                hash = fresh
            } else {
                if (!result.isSuccessful) throw IOException("HTTP ${result.code} sur l'API TF1+")
                return parseJson(result.body)
            }
        }
        return null
    }

    private suspend fun legacy(id: String, variables: JsonObject): JsonElement? {
        val result = http.getWithStatus(url(id, variables), accept = "application/json", headers = HEADERS)
        if (isUnknownQuery(result)) return null
        if (!result.isSuccessful) throw IOException("HTTP ${result.code} sur l'API TF1+")
        return parseJson(result.body)
    }

    private fun url(id: String, variables: JsonObject): String =
        GRAPHQL.toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("variables", variables.toString())
            .build()
            .toString()

    private fun parseJson(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    companion object {
        const val BASE = "https://www.tf1.fr"
        private const val GRAPHQL = "$BASE/graphql/web"
        const val QUERY_PROGRAM_CATALOG = "ProgramCatalogDocument"
        const val QUERY_PROGRAM_VIDEOS = "VideoContainer_RelatedVideosListDocument"
        const val LEGACY_QUERY_PROGRAMS = "483ce0f"
        const val LEGACY_QUERY_PROGRAM_VIDEOS = "a6f9cf0e"
        private const val PAGE_SIZE = 500
        private const val CHANNEL_PAGES = 2
        private const val VIDEOS_LIMIT = 60
        private val HEADERS = mapOf("Content-Type" to "application/json")
        private val PROGRAM_SLUG = Regex("""tf1\.fr/[^/]+/([^/]+)/videos""")
        private val json = Json { ignoreUnknownKeys = true }

        private val LEGACY_CHANNELS = listOf("tf1", "tmc", "tfx", "tf1-series-films", "lci")
        private val LEGACY_CATEGORY_IDS = mapOf(
            "series" to "GT_SERIES_AND_FICTIONS",
            "telefilms" to "GT_SERIES_AND_FICTIONS",
            "films" to "GT_MOVIES",
            "divertissement" to "GT_ENTERTAINMENT",
            "info" to "GT_INFOS_MAG_AND_SPORT",
            "reportages" to "GT_INFOS_MAG_AND_SPORT",
            "sport" to "GT_INFOS_MAG_AND_SPORT",
            "jeunesse" to "GT_YOUTH",
        )

        /** Un identifiant inconnu du serveur : `400 {"error": "no queries to execute"}`. */
        fun isUnknownQuery(result: HttpResult): Boolean =
            result.code == 400 && result.body.contains("no queries to execute")

        // ---- Analyse des réponses : un champ absent ne fait jamais tout échouer ; les erreurs GraphQL
        // partielles (« errors » avec des « data » exploitables) sont acceptées.

        fun parseCatalogPrograms(response: JsonElement): List<Program> =
            response.obj("data").obj("sliderOfPrograms").arr("items").mapNotNull { item ->
                val program = item.obj("program") ?: return@mapNotNull null
                val slug = program.str("slug") ?: return@mapNotNull null
                val name = program.str("name") ?: return@mapNotNull null
                val channel = program.obj("mainChannel")
                Program(
                    slug = slug,
                    name = name,
                    channelSlug = channel.str("slug") ?: "tf1",
                    channelLabel = channel.str("label") ?: "TF1+",
                    imageUrl = pickSource(program.obj("decoration").obj("thumbnail").arr("sourcesWithScales"), preferredScale = 1),
                    replayCount = program.obj("sliderOfVideos").int("total") ?: 1,
                    group = typologyLabel(program.str("typology")),
                )
            }

        /** Les typologies arrivent au singulier (« Série ») : titres de rangées au pluriel. */
        private fun typologyLabel(typology: String?): String = when (typology) {
            null, "" -> "Autres programmes"
            "Série" -> "Séries"
            "Téléfilm" -> "Téléfilms"
            "Film" -> "Films"
            "Émission" -> "Émissions"
            "Documentaire" -> "Documentaires"
            "Spectacle" -> "Spectacles"
            "Information" -> "Info"
            else -> typology
        }

        fun parseLegacyPrograms(response: JsonElement, categoryId: String? = null): List<Program> =
            response.obj("data").obj("programs").arr("items").mapNotNull { program ->
                val categories = program.arr("categories")
                if (categoryId != null && categories.none { it.str("id") == categoryId }) return@mapNotNull null
                val slug = program.str("slug") ?: return@mapNotNull null
                val name = program.str("name") ?: return@mapNotNull null
                val channel = program.obj("mainChannel")
                Program(
                    slug = slug,
                    name = name,
                    channelSlug = channel.str("slug") ?: "tf1",
                    channelLabel = channel.str("label") ?: "TF1+",
                    imageUrl = pickSource(program.obj("decoration").obj("thumbnail").arr("sources"), preferredWidth = 500..700),
                    replayCount = program.obj("replay").int("total") ?: 0,
                    group = categories.firstOrNull { it.bool("main") == true }.str("label")
                        ?: categories.firstOrNull().str("label")
                        ?: "Autres programmes",
                )
            }

        /** `VideoContainer_RelatedVideosListDocument` : `data.programBySlug.sliderOfVideos.items[].video`. */
        fun parseProgramVideos(response: JsonElement): List<CatalogItem.Video> =
            response.obj("data").obj("programBySlug").obj("sliderOfVideos").arr("items").mapNotNull { item ->
                val video = item.obj("video") ?: return@mapNotNull null
                if (!isFree(video)) return@mapNotNull null
                val id = video.str("id") ?: return@mapNotNull null
                val program = video.obj("program")
                val programSlug = program.str("slug") ?: return@mapNotNull null
                val channel = video.obj("broadcastChannel").str("slug") ?: program.obj("mainChannel").str("slug") ?: "tf1"
                val videoSlug = video.str("slug") ?: id
                CatalogItem.Video(
                    source = Source.TF1,
                    title = video.obj("decoration").str("label") ?: program.str("name") ?: return@mapNotNull null,
                    subtitle = episodeLabel(video),
                    imageUrl = pickSource(item.obj("image").arr("sourcesWithScales"), preferredScale = 2),
                    url = "$BASE/$channel/$programSlug/videos/$videoSlug.html",
                    durationSeconds = video.obj("playingInfos").int("duration"),
                    streamId = id,
                    description = null,
                )
            }

        /** Ancienne requête `a6f9cf0e` : `data.programBySlug.videos.items[]`. */
        fun parseLegacyVideos(response: JsonElement): List<CatalogItem.Video> =
            response.obj("data").obj("programBySlug").obj("videos").arr("items").mapNotNull { video ->
                if (!isFree(video)) return@mapNotNull null
                val decoration = video.obj("decoration")
                val thumbnail = decoration.arr("images").firstOrNull { it.str("type") == "THUMBNAIL_LARGE" }
                    ?: decoration.arr("images").firstOrNull { it.str("type") == "THUMBNAIL" }
                CatalogItem.Video(
                    source = Source.TF1,
                    title = decoration.str("label") ?: return@mapNotNull null,
                    subtitle = decoration.str("subLabel"),
                    imageUrl = pickSource(thumbnail.arr("sources"), preferredWidth = 500..700),
                    url = video.str("url") ?: return@mapNotNull null,
                    durationSeconds = video.obj("playingInfos").int("duration"),
                    streamId = video.str("id") ?: return@mapNotNull null,
                    description = decoration.str("description")?.takeIf { it.isNotBlank() },
                )
            }

        private fun isFree(video: JsonElement): Boolean =
            video.arr("rights").any { (it as? JsonPrimitive)?.content == "BASIC" }

        private fun episodeLabel(video: JsonElement): String? {
            val season = video.str("season")?.toIntOrNull()?.takeIf { it in 1..999 }
            val episode = video.str("episode")?.toIntOrNull()?.takeIf { it > 0 }
            return when {
                season != null && episode != null -> "Saison $season · Épisode $episode"
                episode != null -> "Épisode $episode"
                else -> video.obj("decoration").str("shortLabel")?.takeIf { it != video.obj("decoration").str("label") }
            }
        }

        /** Les images existent en plusieurs tailles ; on préfère une échelle (site actuel) ou une largeur (ancien). */
        private fun pickSource(sources: List<JsonElement>, preferredScale: Int? = null, preferredWidth: IntRange? = null): String? {
            val jpg = sources.filter { it.str("type") == null || it.str("type") == "jpg" }.ifEmpty { sources }
            val preferred = jpg.firstOrNull { source ->
                (preferredScale == null || source.int("scale") == preferredScale) &&
                    (preferredWidth == null || (source.str("width")?.toIntOrNull() ?: 0) in preferredWidth)
            }
            return (preferred ?: jpg.firstOrNull()).str("url")
        }
    }
}
