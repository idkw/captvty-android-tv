package dev.valentin.replaytv.library

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun video(url: String, source: Source = Source.FRANCE_TV) =
        CatalogItem.Video(source, "Titre $url", "Sous-titre", "https://img/$url", url, 3600, streamId = "id-$url", description = "desc")

    @Test
    fun `should keep only started videos, most recently watched first`() {
        // GIVEN trois vidéos lancées : une terminée (sans position), deux en cours
        val played = listOf(
            StoredItem.of(video("a"), now = 1_000),
            StoredItem.of(video("b"), now = 2_000),
            StoredItem.of(video("c"), now = 3_000),
        )
        val positions = mapOf("a" to 60_000L, "c" to 120_000L)
        val watchedAt = mapOf("a" to 9_000L)

        // WHEN on calcule la liste « En cours »
        val result = Library.inProgress(played, { positions[it] ?: 0L }, { watchedAt[it] ?: 0L })

        // THEN la vidéo terminée disparaît et la plus récemment regardée vient en premier
        assertEquals(listOf("a", "c"), result.map { it.video.url })
        assertEquals(60_000L, result[0].positionMs)
        assertEquals(9_000L, result[0].lastPlayedAt)
        assertEquals(3_000L, result[1].lastPlayedAt)
    }

    @Test
    fun `should round-trip videos and collections through storage`() {
        // GIVEN une vidéo DRM et une collection
        val drmVideo = video("https://www.tf1.fr/x", Source.TF1)
        val collection = CatalogItem.Collection(Source.M6, "NCIS", null, "https://img", "https://www.6play.fr/ncis-p_843")

        // WHEN on les sérialise puis relit
        val json = Json.encodeToString(ListSerializer(StoredItem.serializer()), listOf(StoredItem.of(drmVideo, 1), StoredItem.of(collection, 2)))
        val back = Json.decodeFromString(ListSerializer(StoredItem.serializer()), json)

        // THEN on retrouve les mêmes éléments, identifiant de flux compris
        assertEquals(drmVideo, back[0].toVideo())
        assertEquals(collection, back[1].toCollection())
    }

    @Test
    fun `should ignore entries whose source no longer exists`() {
        val unknown = StoredItem(source = "CANAL", title = "x", url = "u", savedAt = 1)
        assertTrue(Library.inProgress(listOf(unknown), { 10_000L }, { 0L }).isEmpty())
    }
}
