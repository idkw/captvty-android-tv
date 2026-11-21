package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.library.EpisodeNumber
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

    /**
     * Tous les programmes d'une chaîne du groupe (`m6replay`, `w9replay`, `6terreplay`), par pages
     * de 100 (maximum accepté par l'API), groupés par type (séries, téléfilms, émissions…).
     */
    suspend fun channel(service: String): List<CatalogRow> {
        val programs = mutableListOf<JsonElement>()
        var offset = 0
        while (true) {
            val page = (get("$SERVICES/$service/programs?limit=$PAGE_SIZE&offset=$offset&csa=6") as? JsonArray).orEmpty()
            programs += page
            if (page.size < PAGE_SIZE || offset >= 2000) break
            offset += PAGE_SIZE
        }
        val byType = linkedMapOf<String, MutableList<CatalogItem>>()
        programs
            .filter { (it.obj("count_by_type").obj("video").int("content") ?: 0) > 0 }
            .forEach { p ->
                val wording = p.obj("program_type_wording")
                // « épisodes » désigne en fait les séries dans la nomenclature de M6.
                val label = when (wording.str("code")) {
                    "episode" -> "Séries"
                    null -> "Autres programmes"
                    else -> wording.str("plural")?.replaceFirstChar { c -> c.uppercase() } ?: "Autres programmes"
                }
                program(p)?.let { byType.getOrPut(label) { mutableListOf() }.add(it) }
            }
        return byType.entries
            .sortedByDescending { it.value.size }
            .map { (label, items) -> CatalogRow(label, items.sortedBy { it.title.lowercase() }) }
    }

    suspend fun program(url: String): List<CatalogRow> {
        val programId = PROGRAM_ID.find(url)?.groupValues?.get(1) ?: return emptyList()
        val videos = get("$MIDDLEWARE/programs/$programId/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset=0")
        val items = withSeasons((videos as? JsonArray).orEmpty()
            .filter { it.arr("freemium_packs").isEmpty() }
            .mapNotNull { json -> video(json, url)?.let { M6Episode(it, json.arr("clips").firstOrNull().int("id") ?: 0) } })
        return if (items.isEmpty()) emptyList() else listOf(CatalogRow("Épisodes gratuits", items))
    }

    /** Une vidéo d'un programme et l'identifiant de son clip, attribué par M6 dans l'ordre de création. */
    internal class M6Episode(val video: CatalogItem.Video, val clipId: Int)

    /**
     * M6 garde plusieurs saisons d'un programme sans numéro de saison dans les titres : trois
     * « Épisode 14 - Partie 2 », de 2024 à 2026. L'année de publication ne suffit pas à les séparer :
     * M6 republie parfois une saison entière d'un coup, la saison de l'an dernier a ainsi été publiée
     * le 5 janvier 2026. Si des titres se répètent, chaque épisode reçoit sa saison :
     * « Épisode 14 - Partie 2 (Saison 21) ».
     */
    internal fun withSeasons(videos: List<M6Episode>): List<CatalogItem.Video> {
        val repeated = videos.groupingBy { it.video.title }.eachCount().any { it.value > 1 }
        if (!repeated) return videos.map { it.video }
        val labels = HashMap<Int, String>()
        videos.mapNotNull { episode -> EpisodeNumber.parse(episode.video.title)?.let { episode to it } }
            .groupBy { (_, number) -> number.series }
            .values
            .forEach { series -> seasons(series).forEach { (episodes, label) -> episodes.forEach { labels[it.clipId] = label } } }
        return videos.map { episode -> labels[episode.clipId]?.let { episode.video.copy(title = "${episode.video.title} ($it)") } ?: episode.video }
    }

    /**
     * Les saisons d'une série : dans l'ordre de création des clips, une saison commence quand un
     * numéro déjà vu revient, ou quand la numérotation repart nettement en arrière (de l'épisode 15
     * à l'épisode 2). Un léger désordre est toléré : la partie 1 de l'épisode 13 est parfois créée
     * après l'épisode 14. Le numéro de saison vient des
     * descriptions (« 21e saison », « saison 19 ») ; à défaut, les saisons sont numérotées dans l'ordre.
     */
    private fun seasons(series: List<Pair<M6Episode, EpisodeNumber>>): List<Pair<List<M6Episode>, String>> {
        val groups = mutableListOf<MutableList<M6Episode>>()
        val seen = HashSet<EpisodeNumber>()
        var maxEpisode = 0
        var lastClipId = 0
        series.sortedBy { (episode, _) -> episode.clipId }.forEach { (episode, number) ->
            val restarts = groups.isEmpty() ||
                number in seen ||
                number.episode < maxEpisode - 2 ||
                (number.episode < maxEpisode && episode.clipId - lastClipId > SEASON_GAP)
            if (restarts) {
                groups += mutableListOf<M6Episode>()
                seen.clear()
                maxEpisode = 0
            }
            seen += number
            groups.last() += episode
            maxEpisode = maxOf(maxEpisode, number.episode)
            lastClipId = episode.clipId
        }
        if (groups.size < 2) return emptyList()
        return groups.mapIndexed { index, group -> group to "Saison ${seasonNumber(group) ?: (index + 1)}" }
    }

    /** Numéro de saison le plus cité dans les descriptions des épisodes du groupe. */
    private fun seasonNumber(group: List<M6Episode>): Int? =
        group.flatMap { episode -> SEASON_IN_TEXT.findAll(episode.video.description.orEmpty()).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

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
        private const val SERVICES = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services"
        private const val MIDDLEWARE = "$SERVICES/6play"
        private const val PAGE_SIZE = 100
        private const val SITE = "https://www.6play.fr"
        private val PROGRAM_ID = Regex("""-p_(\d+)""")
        private val SEASON_IN_TEXT = Regex("""(\d{1,2})(?:e|ème)\s+saison|saison\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)

        /** Écart d'identifiants de clips d'environ 5 mois : au-delà, une numérotation qui recule marque une nouvelle saison. */
        private const val SEASON_GAP = 20_000
    }
}
