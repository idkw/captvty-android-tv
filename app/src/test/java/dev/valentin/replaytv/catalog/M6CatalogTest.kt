package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class M6CatalogTest {

    private val catalog = M6Catalog(OkHttpClient())

    private fun video(title: String) = CatalogItem.Video(Source.M6, title, "L'amour est dans le pré", null, title, 3600)

    @Test
    fun `should add the year to every title when titles repeat`() {
        // GIVEN deux saisons qui réutilisent les mêmes titres
        val videos = listOf(
            video("Épisode 10 - Partie 2") to "2026-10-19 23:13:49",
            video("Les portraits - Épisode 3") to "2026-09-01 02:00:46",
            video("Épisode 10 - Partie 2") to "2025-10-20 23:10:00",
        )

        // WHEN on prépare la liste des épisodes
        val titles = catalog.withYears(videos).map { it.title }

        // THEN chaque titre porte son année
        assertEquals(listOf("Épisode 10 - Partie 2 (2026)", "Les portraits - Épisode 3 (2026)", "Épisode 10 - Partie 2 (2025)"), titles)
    }

    @Test
    fun `should keep titles unchanged when none repeats`() {
        // GIVEN une seule saison
        val videos = listOf(video("Épisode 2") to "2026-10-19 23:13:49", video("Épisode 1") to "2026-10-12 23:13:49")

        // WHEN on prépare la liste des épisodes
        val titles = catalog.withYears(videos).map { it.title }

        // THEN les titres ne changent pas
        assertEquals(listOf("Épisode 2", "Épisode 1"), titles)
    }
}
