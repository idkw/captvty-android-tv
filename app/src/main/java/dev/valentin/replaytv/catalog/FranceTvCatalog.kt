package dev.valentin.replaytv.catalog

import android.text.Html
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * france.tv n'expose pas d'API publique de catalogue : on lit les cartes
 * (`<a data-card-link="true" href=...>`) des pages HTML de catégorie et de programme.
 * Les pages vidéo (`/1234567-slug.html`) sont ensuite résolues par yt-dlp.
 */
class FranceTvCatalog(private val http: OkHttpClient) {

    suspend fun page(path: String): List<CatalogRow> {
        val html = http.getString(BASE + path, accept = "text/html")
        val items = parseCards(html)
        val videos = items.filterIsInstance<CatalogItem.Video>()
        val collections = items.filterIsInstance<CatalogItem.Collection>()
        return buildList {
            if (videos.isNotEmpty()) add(CatalogRow("Vidéos", videos))
            if (collections.isNotEmpty()) add(CatalogRow("Programmes et collections", collections))
        }
    }

    /**
     * Page d'une chaîne (`/france-2/`) : une rangée par titre de section (`<h2>`), le direct exclu.
     * Si la page n'a pas de sections, on retombe sur le découpage vidéos / programmes.
     */
    suspend fun channelPage(path: String): List<CatalogRow> {
        val html = http.getString(BASE + path, accept = "text/html")
        val rows = HEADING.split(html).drop(1).mapNotNull { chunk ->
            val title = unescape(TAG.replace(chunk.substringBefore("</h2>"), "")).trim()
            if (title.isEmpty() || title.equals("En direct", ignoreCase = true)) return@mapNotNull null
            val items = parseCards(chunk.substringAfter("</h2>"))
            if (items.isEmpty()) null else CatalogRow(title, items)
        }
        return rows.ifEmpty { page(path) }
    }

    /**
     * Programmes trouvés par le moteur de recherche de l'application mobile france.tv. Les vidéos
     * isolées sont écartées : la réponse ne donne pas l'adresse de leur page, que yt-dlp attend.
     */
    suspend fun search(query: String): List<CatalogItem> {
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("platform", "apps")
            .build().toString()
        return parseSearch(http.getString(url, accept = "application/json"))
    }

    internal fun parseCards(html: String): List<CatalogItem> {
        val seen = HashSet<String>()
        return CARD.findAll(html).mapNotNull { match ->
            val href = match.groupValues[1]
            if (href.contains('?') || !seen.add(href)) return@mapNotNull null
            val body = match.groupValues[2]
            val texts = SPAN.findAll(body)
                .map { unescape(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() && it != "Sponsorisé" }
                .toList()
            val title = texts.firstOrNull() ?: slugToTitle(href)
            val subtitle = texts.getOrNull(1)
            val image = IMG.find(body)?.groupValues?.get(1)
            val url = if (href.startsWith("http")) href else BASE + href
            when {
                VIDEO_PATH.containsMatchIn(href) ->
                    CatalogItem.Video(Source.FRANCE_TV, title, subtitle, image, url, durationSeconds = null)
                href.endsWith("/") && href.count { it == '/' } >= 3 ->
                    CatalogItem.Collection(Source.FRANCE_TV, title, subtitle, image, url)
                else -> null
            }
        }.toList()
    }

    private fun unescape(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun slugToTitle(href: String): String =
        href.trimEnd('/').substringAfterLast('/').removeSuffix(".html")
            .replace(Regex("^\\d+-"), "").replace('-', ' ')
            .replaceFirstChar { it.uppercase() }

    companion object {
        const val BASE = "https://www.france.tv"
        private const val SEARCH = "https://api-mobile.yatta.francetv.fr/apps/search"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * `collections[].items[]` de type `program` ; `program_path` donne l'adresse de la page du
         * programme, les « _ » séparant ses segments : `france-2_la-terre-et-le-sang` →
         * `/france-2/la-terre-et-le-sang/`.
         */
        fun parseSearch(body: String): List<CatalogItem> =
            json.parseToJsonElement(body).arr("collections").flatMap { it.arr("items") }.mapNotNull { item ->
                if (item.str("type") != "program") return@mapNotNull null
                val path = item.str("program_path") ?: return@mapNotNull null
                val image = item.arr("images").firstOrNull().obj("urls")?.let { urls -> (urls["w:800"] ?: urls.values.firstOrNull()) }
                CatalogItem.Collection(
                    source = Source.FRANCE_TV,
                    title = item.str("label") ?: return@mapNotNull null,
                    subtitle = null,
                    imageUrl = (image as? JsonPrimitive)?.content,
                    url = "$BASE/${path.replace('_', '/')}/",
                )
            }.distinctBy { it.url }
        private val CARD = Regex("""<a data-card-link="true"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        private val SPAN = Regex("""<span[^>]*>([^<]*)</span>""")
        private val IMG = Regex("""<img[^>]*\ssrc="([^"]+)"""")
        private val VIDEO_PATH = Regex("""/\d+-[^/]+\.html$""")
        private val HEADING = Regex("<h2[^>]*>")
        private val TAG = Regex("<[^>]+>")
    }
}
