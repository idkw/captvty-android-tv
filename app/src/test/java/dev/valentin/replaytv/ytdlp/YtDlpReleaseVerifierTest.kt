package dev.valentin.replaytv.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Fichiers réels de la release yt-dlp 2026.08.19 et clé publique du dépôt yt-dlp. */
class YtDlpReleaseVerifierTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/ytdlp/$name")) { name }.readBytes()

    private val sums = fixture("SHA2-256SUMS")
    private val signature = fixture("SHA2-256SUMS.sig")

    @Test
    fun `should accept the genuine signed checksums with the key embedded in the app`() {
        // GIVEN la clé embarquée dans l'APK et la release réelle
        // WHEN on vérifie la signature
        // THEN aucune exception n'est levée
        YtDlpReleaseVerifier.verifySums(sums, signature, YtDlpReleaseVerifier.embeddedPublicKey())
    }

    @Test
    fun `should reject tampered checksums`() {
        // GIVEN une empreinte modifiée dans le fichier signé
        val tampered = sums.decodeToString().replaceFirst("1fa6733c", "0fa6733c").encodeToByteArray()

        // WHEN / THEN la signature ne correspond plus
        assertThrows(YtDlpVerificationException::class.java) {
            YtDlpReleaseVerifier.verifySums(tampered, signature, YtDlpReleaseVerifier.embeddedPublicKey())
        }
    }

    @Test
    fun `should reject a corrupted signature`() {
        val corrupted = signature.copyOf().also { it[it.size - 10] = (it[it.size - 10].toInt() xor 0xFF).toByte() }
        assertThrows(YtDlpVerificationException::class.java) {
            YtDlpReleaseVerifier.verifySums(sums, corrupted, YtDlpReleaseVerifier.embeddedPublicKey())
        }
    }

    @Test
    fun `should reject a key whose fingerprint is not the pinned one`() {
        // GIVEN une empreinte épinglée différente de celle de la clé fournie
        // WHEN / THEN la clé n'est pas acceptée, même si la signature serait valide
        assertThrows(YtDlpVerificationException::class.java) {
            YtDlpReleaseVerifier.verifySums(sums, signature, YtDlpReleaseVerifier.embeddedPublicKey(), "0".repeat(40))
        }
    }

    @Test
    fun `should read the signed sha256 of the yt-dlp file`() {
        assertEquals("1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6", YtDlpReleaseVerifier.expectedSha256(sums))
        assertThrows(YtDlpVerificationException::class.java) { YtDlpReleaseVerifier.expectedSha256(sums, "absent.bin") }
    }

    @Test
    fun `should compare the downloaded file with the signed and the github digests`() {
        // GIVEN un contenu dont on connaît le SHA-256
        val content = "abc".encodeToByteArray()
        val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        // THEN il passe quand les deux empreintes concordent
        YtDlpReleaseVerifier.verifyBinary(content.inputStream(), sha, sha)
        YtDlpReleaseVerifier.verifyBinary(content.inputStream(), sha, null)

        // AND il est refusé si le fichier ou l'empreinte GitHub diffère
        assertThrows(YtDlpVerificationException::class.java) { YtDlpReleaseVerifier.verifyBinary("abd".encodeToByteArray().inputStream(), sha, null) }
        assertThrows(YtDlpVerificationException::class.java) { YtDlpReleaseVerifier.verifyBinary(content.inputStream(), sha, "0".repeat(64)) }
    }
}
