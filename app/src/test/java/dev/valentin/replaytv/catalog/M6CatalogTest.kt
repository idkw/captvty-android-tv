package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class M6CatalogTest {

    private val catalog = M6Catalog(OkHttpClient())

    private fun episode(title: String, clipId: Int, description: String? = null) =
        M6Catalog.M6Episode(CatalogItem.Video(Source.M6, title, "L'amour est dans le pré", null, "c_$clipId", 3600, description = description), clipId)

    @Test
    fun `should label each season of a program whose titles repeat`() {
        // GIVEN « L'amour est dans le pré » tel que M6 le liste le 21 novembre 2026 : trois saisons, la saison 20
        // republiée le 5 janvier 2026, la partie 1 de l'épisode 13 créée après l'épisode 14
        val videos = listOf(
            episode("Épisode 14 - Partie 2", 13205006, "L'amour est dans le pré revient avec une 21e saison"),
            episode("Épisode 1 - Partie 1", 13191778, "Une 21e saison"),
            episode("Épisode 15 - Partie 1", 13153055, "La 20e saison s'achève"),
            episode("Épisode 13 - Partie 1", 13152793, "Le bilan de la 20e saison"),
            episode("Épisode 14 - Partie 2", 13151803, "Karine Le Marchand accueille les agriculteurs de la saison"),
            episode("Épisode 1 - Partie 1", 13136829, "Les agriculteurs de la 20e saison"),
            episode("Épisode 13 - Partie 1", 13100076, "Découvrez les 12 agriculteurs de la saison 19"),
            episode("Épisode 14 - Partie 2", 13099341, "Découvrez les 12 agriculteurs de la saison 19"),
        )

        // WHEN on prépare la liste des épisodes
        val titles = catalog.withSeasons(videos).map { it.title }

        // THEN chaque épisode porte le numéro de sa saison, tiré des descriptions
        assertEquals(
            listOf(
                "Épisode 14 - Partie 2 (Saison 21)",
                "Épisode 1 - Partie 1 (Saison 21)",
                "Épisode 15 - Partie 1 (Saison 20)",
                "Épisode 13 - Partie 1 (Saison 20)",
                "Épisode 14 - Partie 2 (Saison 20)",
                "Épisode 1 - Partie 1 (Saison 20)",
                "Épisode 13 - Partie 1 (Saison 19)",
                "Épisode 14 - Partie 2 (Saison 19)",
            ),
            titles,
        )
    }

    @Test
    fun `should number seasons in order when descriptions do not give it`() {
        // GIVEN deux saisons sans numéro dans les descriptions
        val videos = listOf(episode("Épisode 2", 300), episode("Épisode 1", 200), episode("Épisode 2", 110), episode("Épisode 1", 100))

        // WHEN on prépare la liste des épisodes
        val titles = catalog.withSeasons(videos).map { it.title }

        // THEN les saisons sont numérotées de la plus ancienne à la plus récente
        assertEquals(listOf("Épisode 2 (Saison 2)", "Épisode 1 (Saison 2)", "Épisode 2 (Saison 1)", "Épisode 1 (Saison 1)"), titles)
    }

    @Test
    fun `should keep titles unchanged when none repeats`() {
        // GIVEN une seule saison
        val videos = listOf(episode("Épisode 2", 2), episode("Épisode 1", 1))

        // WHEN on prépare la liste des épisodes
        val titles = catalog.withSeasons(videos).map { it.title }

        // THEN les titres ne changent pas
        assertEquals(listOf("Épisode 2", "Épisode 1"), titles)
    }

    @Test
    fun `should not propose an older season at the end of the latest episode`() {
        // GIVEN la liste réelle des vidéos gratuites du programme, relevée le 21 novembre 2026 (titre, clip, description)
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/m6/amour_est_dans_le_pre_videos.json")).bufferedReader().use { it.readText() }
        val videos = kotlinx.serialization.json.Json.parseToJsonElement(raw).let { it as kotlinx.serialization.json.JsonArray }.map {
            episode(it.str("title")!!, it.int("clip_id")!!, it.str("description"))
        }

        // WHEN on prépare la liste, puis on termine la partie 2 de l'épisode 14 de la saison en cours
        val labelled = catalog.withSeasons(videos)
        val current = labelled.first { it.url == "c_13205006" }
        val next = dev.valentin.replaytv.library.nextEpisodes(current, labelled)

        // THEN l'épisode est bien de la saison 21, et rien de la saison 20 ni de la 19 n'est proposé
        assertEquals("Épisode 14 - Partie 2 (Saison 21)", current.title)
        assertEquals("Épisode 15 - Partie 1 (Saison 20)", labelled.first { it.url == "c_13153055" }.title)
        assertEquals(emptyList<String>(), next.map { it.title }.filter { "Saison 20" in it || "Saison 19" in it })
    }
}
