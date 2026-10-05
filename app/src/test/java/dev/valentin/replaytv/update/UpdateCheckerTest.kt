package dev.valentin.replaytv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val release = UpdateCheckerTest::class.java.classLoader!!
        .getResourceAsStream("fixtures/github/latest_release.json")!!.bufferedReader().readText()

    @Test
    fun `should compare versions numerically`() {
        assertTrue(UpdateChecker.compareVersions("0.3.3", "0.3.2") > 0)
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("v1.0.0", "0.99.0") > 0)
        assertEquals(0, UpdateChecker.compareVersions("0.3.3", "v0.3.3"))
        assertEquals(0, UpdateChecker.compareVersions("0.3.3-debug", "0.3.3"))
        assertTrue(UpdateChecker.compareVersions("0.3", "0.3.1") < 0)
    }

    @Test
    fun `should pick the apk of the first supported abi, else the universal one`() {
        // GIVEN la release réelle (arm64-v8a, armeabi-v7a, universal, x86_64)
        val arm64 = UpdateChecker.parseLatest(release, "0.1.0", listOf("arm64-v8a", "armeabi-v7a"))
        val arm32 = UpdateChecker.parseLatest(release, "0.1.0", listOf("armeabi-v7a", "armeabi"))
        val unknown = UpdateChecker.parseLatest(release, "0.1.0", listOf("mips"))

        // THEN l'APK suit l'architecture, et l'universel sert de repli
        assertEquals("replaytv-v0.3.3-arm64-v8a.apk", arm64!!.assetName)
        assertEquals("replaytv-v0.3.3-armeabi-v7a.apk", arm32!!.assetName)
        assertEquals("replaytv-v0.3.3-universal.apk", unknown!!.assetName)
    }

    @Test
    fun `should describe a newer release with its digest and skip older or equal ones`() {
        // WHEN la version installée est plus ancienne
        val info = UpdateChecker.parseLatest(release, "0.3.2", listOf("arm64-v8a"))

        // THEN la mise à jour est décrite complètement
        assertNotNull(info)
        assertEquals("0.3.3", info!!.version)
        assertEquals("v0.3.3", info.tag)
        assertEquals("97e021f41218c6e13ab82ced620655c224da97352044579916610d609ef0ef10", info.sha256)
        assertEquals(65616226L, info.assetSize)
        assertTrue(info.assetUrl.startsWith("https://github.com/idkw/captvty-android-tv/releases/download/v0.3.3/"))
        assertTrue(info.notes.contains("Accueil par chaîne"))

        // AND rien n'est proposé quand la version installée est égale ou plus récente
        assertNull(UpdateChecker.parseLatest(release, "0.3.3", listOf("arm64-v8a")))
        assertNull(UpdateChecker.parseLatest(release, "0.4.0", listOf("arm64-v8a")))
    }

    @Test
    fun `should ignore drafts, prereleases and garbage`() {
        assertNull(UpdateChecker.parseLatest(release.replace("\"draft\": false", "\"draft\": true"), "0.1.0", listOf("arm64-v8a")))
        assertNull(UpdateChecker.parseLatest(release.replace("\"prerelease\": false", "\"prerelease\": true"), "0.1.0", listOf("arm64-v8a")))
        assertNull(UpdateChecker.parseLatest("{\"message\":\"API rate limit exceeded\"}", "0.1.0", listOf("arm64-v8a")))
        assertNull(UpdateChecker.parseLatest("not json", "0.1.0", listOf("arm64-v8a")))
    }
}
