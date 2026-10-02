package dev.valentin.replaytv.model

/** [drm] : flux protégés par Widevine, lus via un compte de la chaîne et jamais téléchargés. */
enum class Source(val label: String, val drm: Boolean = false) {
    FRANCE_TV("France TV"),
    ARTE("Arte"),
    TF1("TF1+", drm = true),
    M6("M6+", drm = true),
}

/** Une rubrique d'accueil : une page France TV, une page EMAC Arte, une catégorie TF1+ ou un dossier M6+. */
data class Section(val source: Source, val code: String, val label: String)

sealed interface CatalogItem {
    val source: Source
    val title: String
    val subtitle: String?
    val imageUrl: String?

    /** URL de la page web : consommée par yt-dlp, et clé de reprise de lecture pour toutes les sources. */
    val url: String

    data class Video(
        override val source: Source,
        override val title: String,
        override val subtitle: String?,
        override val imageUrl: String?,
        override val url: String,
        val durationSeconds: Int?,
        /** Identifiant de la vidéo dans l'API de la chaîne, pour les sources DRM. */
        val streamId: String? = null,
        val description: String? = null,
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

    val tf1 = listOf(
        Section(Source.TF1, "GT_SERIES_AND_FICTIONS", "Séries et fictions"),
        Section(Source.TF1, "GT_ENTERTAINMENT", "Divertissements"),
        Section(Source.TF1, "GT_MOVIES", "Cinéma"),
        Section(Source.TF1, "GT_INFOS_MAG_AND_SPORT", "Infos, magazines et sport"),
        Section(Source.TF1, "GT_YOUTH", "Jeunesse"),
    )

    // Identifiants des dossiers du service « 6play » de l'API middleware M6.
    val m6 = listOf(
        Section(Source.M6, "8", "Séries"),
        Section(Source.M6, "10", "Divertissement"),
        Section(Source.M6, "232", "Séries-réalité"),
        Section(Source.M6, "907", "Cinéma"),
        Section(Source.M6, "70", "Téléfilms"),
        Section(Source.M6, "12", "Info et société"),
        Section(Source.M6, "58", "Sport"),
        Section(Source.M6, "6388", "Jeunesse"),
    )
}

fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
}
