package dev.valentin.replaytv.catalog

import android.text.Html
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Source
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
        private val CARD = Regex("""<a data-card-link="true"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        private val SPAN = Regex("""<span[^>]*>([^<]*)</span>""")
        private val IMG = Regex("""<img[^>]*\ssrc="([^"]+)"""")
        private val VIDEO_PATH = Regex("""/\d+-[^/]+\.html$""")
        private val HEADING = Regex("<h2[^>]*>")
        private val TAG = Regex("<[^>]+>")
    }
}
