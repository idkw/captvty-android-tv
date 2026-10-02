package dev.valentin.replaytv.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Tf1CatalogParsingTest {

    @Test
    fun `should parse programmes of the current catalogue query`() {
        // GIVEN une réponse réelle de ProgramCatalogDocument
        val response = Fixtures.json("program_catalog_response.json")

        // WHEN on l'analyse
        val programs = Tf1Catalog.parseCatalogPrograms(response)

        // THEN chaque programme a un slug, un nom, une chaîne, une vignette et un nombre de replays
        assertEquals(3, programs.size)
        programs.forEach { program ->
            assertTrue(program.slug.isNotBlank())
            assertTrue(program.name.isNotBlank())
            assertTrue(program.channelSlug.isNotBlank())
            assertNotNull(program.imageUrl)
            assertTrue(program.imageUrl!!.startsWith("https://photos.tf1.fr/"))
            assertTrue(program.replayCount > 0)
            assertTrue(program.toItem().url.startsWith("https://www.tf1.fr/${program.channelSlug}/${program.slug}/videos"))
        }
    }

    @Test
    fun `should accept partial GraphQL errors when data is usable`() {
        // GIVEN une réponse avec « errors » et des « data » exploitables
        val response = Fixtures.json("partial_errors_response.json")

        // WHEN on l'analyse
        val programs = Tf1Catalog.parseCatalogPrograms(response)

        // THEN les programmes sont tout de même lus
        assertEquals(3, programs.size)
    }

    @Test
    fun `should parse replays of the current videos query and keep only free ones`() {
        // GIVEN une réponse réelle de VideoContainer_RelatedVideosListDocument, dont une vidéo MAX
        val response = Fixtures.json("related_videos_response.json")

        // WHEN on l'analyse
        val videos = Tf1Catalog.parseProgramVideos(response)

        // THEN la vidéo réservée aux abonnés est écartée, les autres sont complètes
        assertEquals(2, videos.size)
        videos.forEach { video ->
            assertNotNull(video.streamId)
            assertTrue(video.title.isNotBlank())
            assertNotNull(video.imageUrl)
            assertNotNull(video.durationSeconds)
            assertTrue(video.url.matches(Regex("https://www\\.tf1\\.fr/[^/]+/koh-lanta/videos/.+\\.html")))
        }
    }

    @Test
    fun `should parse programmes of the legacy query and filter by category`() {
        // GIVEN une réponse réelle de l'ancienne requête 483ce0f
        val response = Fixtures.json("legacy_programs_response.json")

        // WHEN on l'analyse sans puis avec filtre de catégorie
        val all = Tf1Catalog.parseLegacyPrograms(response)
        val series = Tf1Catalog.parseLegacyPrograms(response, categoryId = "GT_SERIES_AND_FICTIONS")
        val none = Tf1Catalog.parseLegacyPrograms(response, categoryId = "GT_DOES_NOT_EXIST")

        // THEN le filtre ne garde que les programmes de la catégorie
        assertEquals(2, all.size)
        assertTrue(series.isNotEmpty() && series.size <= all.size)
        assertTrue(none.isEmpty())
        assertTrue(all.all { it.replayCount > 0 && it.group.isNotBlank() })
    }

    @Test
    fun `should parse replays of the legacy videos query`() {
        // GIVEN une réponse réelle de l'ancienne requête a6f9cf0e
        val response = Fixtures.json("legacy_videos_response.json")

        // WHEN on l'analyse
        val videos = Tf1Catalog.parseLegacyVideos(response)

        // THEN seules les vidéos BASIC restent, avec identifiant et URL
        assertTrue(videos.isNotEmpty())
        videos.forEach { video ->
            assertNotNull(video.streamId)
            assertTrue(video.url.startsWith("https://www.tf1.fr/"))
        }
    }

    @Test
    fun `should tolerate missing fields instead of failing`() {
        // GIVEN des réponses incomplètes : programme sans décoration ni chaîne, vidéo sans image ni durée
        val catalog = Json.parseToJsonElement(
            """{"data":{"sliderOfPrograms":{"items":[{"program":{"slug":"x","name":"X"}},{"program":{"name":"sans slug"}},{}]}}}""",
        )
        val videos = Json.parseToJsonElement(
            """{"data":{"programBySlug":{"sliderOfVideos":{"items":[{"video":{"id":"1","rights":["BASIC"],"program":{"slug":"p"},"decoration":{"label":"Titre"}}}]}}}}""",
        )

        // WHEN on les analyse
        val programs = Tf1Catalog.parseCatalogPrograms(catalog)
        val parsedVideos = Tf1Catalog.parseProgramVideos(videos)

        // THEN les éléments exploitables sont gardés avec des valeurs par défaut, les autres ignorés
        assertEquals(1, programs.size)
        assertEquals("tf1", programs[0].channelSlug)
        assertEquals("Autres programmes", programs[0].group)
        assertEquals(1, parsedVideos.size)
        assertEquals("https://www.tf1.fr/tf1/p/videos/1.html", parsedVideos[0].url)
    }

    @Test
    fun `should recognise an unknown query id answer`() {
        assertTrue(Tf1Catalog.isUnknownQuery(HttpResult(400, Fixtures.text("unknown_query_response.json"))))
        assertFalse(Tf1Catalog.isUnknownQuery(HttpResult(200, """{"data":{}}""")))
        assertFalse(Tf1Catalog.isUnknownQuery(HttpResult(500, "oops")))
    }
}
