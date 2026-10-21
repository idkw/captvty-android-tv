package dev.valentin.replaytv.library

import dev.valentin.replaytv.model.CatalogItem
import java.text.Normalizer

/**
 * Place d'une vidéo dans sa série, lue dans son titre : « Épisode 10 - Partie 2 » (M6),
 * « Saison 2 · Épisode 3 » (TF1), « Que sont-ils devenus ? Émission 2 (1/2) », « Titre (3/6) ».
 * [series] est le texte qui précède le numéro (« 20 ans », « les portraits »…), normalisé : il
 * distingue plusieurs séries d'un même programme.
 */
data class EpisodeNumber(val series: String, val season: Int, val episode: Int, val part: Int) : Comparable<EpisodeNumber> {

    override fun compareTo(other: EpisodeNumber): Int =
        compareValuesBy(this, other, { it.season }, { it.episode }, { it.part })

    companion object {
        private val SEASON_EPISODE = Regex("""\bs(\d{1,3})\s*[-·.]?\s*e(\d{1,4})\b""", RegexOption.IGNORE_CASE)
        private val SEASON = Regex("""\bsaison\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
        private val EPISODE = Regex("""\b(?:episode|ep|emission)\.?\s*(\d{1,4})(?:\s*/\s*\d+)?""", RegexOption.IGNORE_CASE)
        private val PART = Regex("""\b(?:partie|part)\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
        private val FRACTION = Regex("""\((\d{1,3})\s*/\s*\d{1,3}\)""")

        /** Le sous-titre d'abord (TF1 y met « Saison · Épisode »), sinon le titre. */
        fun of(video: CatalogItem.Video): EpisodeNumber? =
            video.subtitle?.let(::parse) ?: parse(video.title)

        fun parse(text: String): EpisodeNumber? {
            // Accents retirés : « Épisode », « Émission » deviennent « episode », « emission ».
            val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val compact = SEASON_EPISODE.find(plain)
            val season = compact ?: SEASON.find(plain)
            val episode = EPISODE.find(plain)
            val part = PART.find(plain)
            val fraction = FRACTION.find(plain)

            val episodeNumber = when {
                compact != null -> compact.groupValues[2].toInt()
                episode != null -> episode.groupValues[1].toInt()
                // Sans numéro d'épisode, « (3/6) » est l'épisode ; avec, c'est une partie.
                fraction != null -> fraction.groupValues[1].toInt()
                else -> return null
            }
            val partNumber = part?.groupValues?.get(1)?.toInt()
                ?: fraction?.takeIf { compact != null || episode != null }?.groupValues?.get(1)?.toInt()
                ?: 0
            val start = listOfNotNull(season, episode, part, fraction).minOf { it.range.first }
            return EpisodeNumber(
                series = normalize(plain.substring(0, start)),
                season = season?.groupValues?.get(1)?.toInt() ?: 0,
                episode = episodeNumber,
                part = partNumber,
            )
        }

        private fun normalize(text: String): String =
            text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}

/**
 * Jusqu'à [count] vidéos à proposer après [current], prises dans [episodes] (la collection dont elle
 * fait partie) : d'abord les épisodes qui la suivent dans sa série (épisode 10 partie 1, puis
 * partie 2, puis épisode 11…), puis, pour compléter, les autres vidéos non vues de la collection
 * dans l'ordre où la chaîne les présente.
 */
fun nextEpisodes(
    current: CatalogItem.Video,
    episodes: List<CatalogItem.Video>,
    isWatched: (String) -> Boolean = { false },
    count: Int = 3,
): List<CatalogItem.Video> {
    val others = episodes.filter { it.url != current.url }.distinctBy { it.url }
    val currentNumber = EpisodeNumber.of(current)

    // Chez France TV, le titre est le nom du programme et une page mêle plusieurs programmes : si
    // d'autres vidéos portent le même titre, la série est aussi définie par le titre. Chez TF1, le
    // titre est le nom de chaque épisode et n'entre pas en compte.
    val titleIsProgram = others.any { it.title == current.title }
    val following = if (currentNumber == null) {
        emptyList()
    } else {
        others
            .filter { !titleIsProgram || it.title == current.title }
            .mapNotNull { video -> EpisodeNumber.of(video)?.let { video to it } }
            .filter { (_, number) -> number.series == currentNumber.series && number > currentNumber }
            .sortedBy { (_, number) -> number }
            .map { (video, _) -> video }
    }
    if (following.size >= count) return following.take(count)

    // Complément : les vidéos qui suivent la vidéo courante dans la liste, puis celles d'avant.
    val index = episodes.indexOfFirst { it.url == current.url }
    val listOrder = if (index < 0) others else episodes.drop(index + 1) + episodes.take(index)
    val chosen = following.map { it.url }.toMutableSet()
    val filler = listOrder.filter { it.url != current.url && !isWatched(it.url) && chosen.add(it.url) }
    return (following + filler).take(count)
}
