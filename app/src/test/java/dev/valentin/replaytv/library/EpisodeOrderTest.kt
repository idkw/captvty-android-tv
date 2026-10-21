package dev.valentin.replaytv.library

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeOrderTest {

    private fun video(title: String, subtitle: String? = "L'amour est dans le pré", url: String = title) =
        CatalogItem.Video(Source.M6, title, subtitle, null, url, 3600)

    @Test
    fun `should read episode, part, season and series from titles`() {
        // GIVEN les formats rencontrés chez M6, TF1 et Arte
        val cases = mapOf(
            "Épisode 10 - Partie 2" to EpisodeNumber("", 0, 10, 2),
            "20 ans - Épisode 2 - Partie 1" to EpisodeNumber("20 ans", 0, 2, 1),
            "Que sont-ils devenus ? Émission 2 (1/2)" to EpisodeNumber("que sont ils devenus", 0, 2, 1),
            "Saison 2 · Épisode 3" to EpisodeNumber("", 2, 3, 0),
            "Le Bureau S3 E7" to EpisodeNumber("le bureau", 3, 7, 0),
            "S1 E2 - Épisode 2" to EpisodeNumber("", 1, 2, 0),
            "Les Aventures de Tintin (3/6)" to EpisodeNumber("les aventures de tintin", 0, 3, 0),
        )

        cases.forEach { (title, expected) ->
            // WHEN on lit le titre
            val number = EpisodeNumber.parse(title)

            // THEN on retrouve sa place dans la série
            assertEquals(title, expected, number)
        }
        assertNull(EpisodeNumber.parse("Le documentaire du dimanche"))
    }

    @Test
    fun `should propose the next part, then the next episode`() {
        // GIVEN une collection M6, présentée de la plus récente à la plus ancienne, avec une autre série mêlée
        val episodes = listOf(
            video("Épisode 11 - Partie 1"),
            video("Épisode 10 - Partie 2"),
            video("Épisode 10 - Partie 1"),
            video("20 ans - Épisode 2 - Partie 1"),
            video("Épisode 9 - Partie 2"),
            video("Épisode 11 - Partie 2"),
        )

        // WHEN on termine l'épisode 10 partie 1
        val next = nextEpisodes(video("Épisode 10 - Partie 1"), episodes)

        // THEN la suite de la même série vient dans l'ordre, sans l'autre série
        assertEquals(listOf("Épisode 10 - Partie 2", "Épisode 11 - Partie 1", "Épisode 11 - Partie 2"), next.map { it.title })
    }

    @Test
    fun `should pick the part of the same season when several seasons share the numbers`() {
        // GIVEN le programme M6, de la plus récente à la plus ancienne vidéo, avec trois saisons sans numéro de saison
        val episodes = listOf(
            video("Épisode 10 - Partie 1", url = "2026-10-1"),
            video("Épisode 10 - Partie 2", url = "2026-10-2"),
            video("Épisode 11 - Partie 2", url = "2025-11-2"),
            video("Épisode 11 - Partie 1", url = "2025-11-1"),
            video("Épisode 10 - Partie 2", url = "2025-10-2"),
            video("Épisode 10 - Partie 1", url = "2025-10-1"),
            video("Épisode 10 - Partie 2", url = "2024-10-2"),
        )

        // WHEN on termine la partie 1 de l'épisode 10 de cette année
        val next = nextEpisodes(episodes[0], episodes)

        // THEN la partie 2 est celle de la même année, et aucun numéro n'est proposé deux fois
        assertEquals(listOf("2026-10-2", "2025-11-1", "2025-11-2"), next.map { it.url })
    }

    @Test
    fun `should use the season and episode of the subtitle`() {
        // GIVEN des épisodes TF1 : le titre est le nom de l'épisode, le sous-titre son numéro
        val episodes = listOf(
            video("Le retour", "Saison 2 · Épisode 1"),
            video("Le départ", "Saison 1 · Épisode 2"),
            video("Le début", "Saison 1 · Épisode 1"),
        )

        // WHEN on termine le premier épisode
        val next = nextEpisodes(episodes[2], episodes, count = 2)

        // THEN l'épisode 2 puis la saison suivante sont proposés
        assertEquals(listOf("Le départ", "Le retour"), next.map { it.title })
    }

    @Test
    fun `should keep to the same program when the title is the program name`() {
        // GIVEN une page France TV : le titre est le programme, et un autre programme s'y mêle
        val episodes = listOf(
            video("La terre et le sang", "S1 E2 - Épisode 2"),
            video("Nus & culottés", "S1 E4 - Objectif Corse"),
            video("La terre et le sang", "S1 E4 - Épisode 4", url = "e4"),
            video("La terre et le sang", "S1 E3 - Épisode 3", url = "e3"),
        )

        // WHEN on termine l'épisode 2
        val next = nextEpisodes(episodes[0], episodes, count = 2)

        // THEN seuls les épisodes du même programme sont proposés, dans l'ordre
        assertEquals(listOf("e3", "e4"), next.map { it.url })
    }

    @Test
    fun `should complete with unwatched videos of the collection`() {
        // GIVEN le dernier épisode d'une série, et d'autres vidéos dont une déjà vue
        val episodes = listOf(
            video("Bonus : les coulisses"),
            video("Épisode 3"),
            video("Épisode 2"),
            video("Interview"),
            video("Making-of"),
        )

        // WHEN on termine l'épisode 3
        val next = nextEpisodes(episodes[1], episodes, isWatched = { it == "Interview" })

        // THEN les vidéos qui suivent dans la liste viennent en complément, sans la vidéo déjà vue
        assertEquals(listOf("Épisode 2", "Making-of", "Bonus : les coulisses"), next.map { it.title })
    }
}
