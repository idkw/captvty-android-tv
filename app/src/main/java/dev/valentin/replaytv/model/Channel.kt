package dev.valentin.replaytv.model

import java.text.Normalizer

/**
 * Une chaîne de télévision telle que présentée à l'accueil. [code] est la clé comprise par le
 * catalogue de la source : chemin france.tv, slug TF1, code de service M6, ou ignoré pour Arte.
 */
data class Channel(val id: String, val label: String, val source: Source, val code: String)

object Channels {
    /** Les chaînes principales, dans l'ordre historique de Captvty. */
    val main = listOf(
        Channel("tf1", "TF1", Source.TF1, "tf1"),
        Channel("france-2", "France 2", Source.FRANCE_TV, "/france-2/"),
        Channel("france-3", "France 3", Source.FRANCE_TV, "/france-3/"),
        Channel("france-4", "France 4", Source.FRANCE_TV, "/france-4/"),
        Channel("france-5", "France 5", Source.FRANCE_TV, "/france-5/"),
        Channel("m6", "M6", Source.M6, "m6replay"),
        Channel("arte", "Arte", Source.ARTE, "ARTE"),
    )

    val others = listOf(
        Channel("tmc", "TMC", Source.TF1, "tmc"),
        Channel("tfx", "TFX", Source.TF1, "tfx"),
        Channel("tf1-series-films", "TF1 Séries Films", Source.TF1, "tf1-series-films"),
        Channel("lci", "LCI", Source.TF1, "lci"),
        Channel("w9", "W9", Source.M6, "w9replay"),
        Channel("6ter", "6ter", Source.M6, "6terreplay"),
        Channel("franceinfo", "franceinfo", Source.FRANCE_TV, "/franceinfo/"),
    )
}

/** Forme comparable pour la recherche : minuscules, sans accents. */
fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

private val DIACRITICS = Regex("\\p{Mn}+")
