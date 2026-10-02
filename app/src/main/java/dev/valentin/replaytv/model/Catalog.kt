package dev.valentin.replaytv.model

enum class Source(val label: String) {
    FRANCE_TV("France TV"),
    ARTE("Arte"),
}

/** Une rubrique d'accueil : une page de catégorie France TV ou une page EMAC Arte. */
data class Section(val source: Source, val code: String, val label: String)

sealed interface CatalogItem {
    val source: Source
    val title: String
    val subtitle: String?
    val imageUrl: String?

    /** URL de la page web, telle que yt-dlp sait la consommer. */
    val url: String

    data class Video(
        override val source: Source,
        override val title: String,
        override val subtitle: String?,
        override val imageUrl: String?,
        override val url: String,
        val durationSeconds: Int?,
    ) : CatalogItem

    data class Collection(
        override val source: Source,
        override val title: String,
        override val subtitle: String?,
        override val imageUrl: String?,
        override val url: String,
    ) : CatalogItem
}

data class CatalogRow(val title: String, val items: List<CatalogItem>)

object Sections {
    val franceTv = listOf(
        Section(Source.FRANCE_TV, "/documentaires/", "Documentaires"),
        Section(Source.FRANCE_TV, "/series-et-fictions/", "Séries et fictions"),
        Section(Source.FRANCE_TV, "/films/", "Films"),
        Section(Source.FRANCE_TV, "/info/", "Info"),
        Section(Source.FRANCE_TV, "/societe/", "Société"),
        Section(Source.FRANCE_TV, "/spectacles-et-culture/", "Spectacles et culture"),
        Section(Source.FRANCE_TV, "/jeux-et-divertissements/", "Jeux et divertissements"),
        Section(Source.FRANCE_TV, "/enfants/", "Enfants"),
        Section(Source.FRANCE_TV, "/sport/", "Sport"),
    )

    val arte = listOf(
        Section(Source.ARTE, "HOME", "À la une"),
        Section(Source.ARTE, "DOR", "Documentaires et reportages"),
        Section(Source.ARTE, "SER", "Séries"),
        Section(Source.ARTE, "CIN", "Films"),
        Section(Source.ARTE, "ACT", "Info et société"),
        Section(Source.ARTE, "SCI", "Sciences"),
        Section(Source.ARTE, "HIS", "Histoire"),
        Section(Source.ARTE, "CPO", "Culture et pop"),
        Section(Source.ARTE, "DEC", "Voyages et découvertes"),
        Section(Source.ARTE, "EMI", "Les émissions"),
    )
}

fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
}
