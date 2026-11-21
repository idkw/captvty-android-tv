package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FranceTvSearchTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/francetv/$name")) { "fixture $name introuvable" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `should keep programmes and build their page address`() {
        // GIVEN un extrait réel de la réponse du moteur de recherche pour « terre » : 3 programmes, 2 vidéos
        val body = fixture("search_response.json")

        // WHEN on l'analyse
        val items = FranceTvCatalog.parseSearch(body)

        // THEN seuls les programmes restent, chacun avec l'adresse de sa page
        assertEquals(3, items.size)
        assertTrue(items.all { it is CatalogItem.Collection })
        val terre = items.first { it.title == "La terre et le sang" }
        assertEquals("https://www.france.tv/france-2/la-terre-et-le-sang/", terre.url)
        assertEquals(
            "https://www.france.tv/la1ere/guadeloupe/terre-de-blues-2026-marie-galante/",
            items.first { it.title.startsWith("Terre de Blues") }.url,
        )
    }
}
