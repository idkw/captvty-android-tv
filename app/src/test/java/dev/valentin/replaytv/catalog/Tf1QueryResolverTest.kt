package dev.valentin.replaytv.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Tf1QueryResolverTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `should extract chunk paths from a page`() {
        // GIVEN un extrait de page HTML réel avec ses balises script
        val html = Fixtures.text("page_scripts_excerpt.html")

        // WHEN on en extrait les chemins de scripts
        val paths = Tf1QueryResolver.extractChunkPaths(html)

        // THEN chaque balise donne un chemin static/chunks/*.js
        assertEquals(4, paths.size)
        assertTrue(paths.all { it.startsWith("static/chunks/") && it.endsWith(".js") })
    }

    @Test
    fun `should parse the query table of a script`() {
        // GIVEN un extrait réel du script qui porte la table nom → empreinte
        val script = Fixtures.text("chunk_query_table_excerpt.js")

        // WHEN on l'analyse
        val table = Tf1QueryResolver.parseQueryTable(script)

        // THEN les requêtes utiles y sont, avec une empreinte sha256 complète
        assertTrue(table.size >= 5)
        val hash = table.getValue("ProgramCatalogDocument")
        assertTrue(hash.matches(Regex("sha256:[0-9a-f]{64}")))
        assertTrue("ProgramEditorialContent_EpisodeListByIdDocument" in table)
    }

    @Test
    fun `should return an empty table for a script without queries`() {
        assertTrue(Tf1QueryResolver.parseQueryTable("function a(){return 1}").isEmpty())
    }

    @Test
    fun `should discover then cache then reuse the table`() = runBlocking {
        // GIVEN un faux site : la page cite un script qui porte la table, et un compteur d'appels
        val fetched = mutableListOf<String>()
        val cacheFile = File(folder.root, "tf1_queries.json")
        var clock = 1_000_000L
        val resolver = Tf1QueryResolver(cacheFile, fetch = { url ->
            fetched += url
            when {
                url == Tf1QueryResolver.CATALOG_PAGE -> Fixtures.text("page_scripts_excerpt.html")
                url.endsWith(".js") -> Fixtures.text("chunk_query_table_excerpt.js")
                else -> error("URL inattendue $url")
            }
        }, now = { clock })

        // WHEN on résout deux requêtes
        val first = resolver.hash("ProgramCatalogDocument")
        val second = resolver.hash("SearchResultsDocument")

        // THEN une seule découverte a eu lieu, et le cache est écrit sur disque
        assertTrue(first!!.startsWith("sha256:"))
        assertTrue(second!!.startsWith("sha256:"))
        assertEquals(1, fetched.count { it == Tf1QueryResolver.CATALOG_PAGE })
        assertTrue(cacheFile.exists())

        // WHEN un nouveau résolveur relit le cache six jours plus tard
        clock += 6L * 24 * 3600 * 1000
        val reloaded = Tf1QueryResolver(cacheFile, fetch = { error("le cache aurait dû suffire") }, now = { clock })

        // THEN il répond sans réseau
        assertEquals(first, reloaded.hash("ProgramCatalogDocument"))
    }

    @Test
    fun `should rediscover when a query is reported unknown and back off after a failure`() = runBlocking {
        // GIVEN un résolveur dont la découverte échoue (page vide)
        val cacheFile = File(folder.root, "tf1_queries.json")
        var pages = 0
        var clock = 1_000_000L
        val resolver = Tf1QueryResolver(cacheFile, fetch = { url ->
            if (url == Tf1QueryResolver.CATALOG_PAGE) pages++
            ""
        }, now = { clock })

        // WHEN on résout, puis on signale l'inconnu et on résout encore dans l'heure
        assertNull(resolver.hash("ProgramCatalogDocument"))
        resolver.reportUnknown("ProgramCatalogDocument")
        assertNull(resolver.hash("ProgramCatalogDocument"))

        // THEN une seule découverte a été tentée : pas de nouvel essai avant une heure
        assertEquals(1, pages)

        // WHEN une heure est passée
        clock += 3_600_001L
        resolver.hash("ProgramCatalogDocument")

        // THEN la découverte est retentée
        assertEquals(2, pages)
    }
}
