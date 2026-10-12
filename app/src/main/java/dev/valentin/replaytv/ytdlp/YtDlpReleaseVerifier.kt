package dev.valentin.replaytv.ytdlp

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.InputStream
import java.security.MessageDigest

/** Une release de yt-dlp ne passe pas les contrôles : elle n'est pas installée et l'utilisateur est prévenu. */
class YtDlpVerificationException(message: String) : Exception(message)

/**
 * Contrôles d'une release yt-dlp avant installation, sans réseau ni Android pour rester testables :
 * 1. le fichier d'empreintes `SHA2-256SUMS` est signé (signature OpenPGP détachée `SHA2-256SUMS.sig`)
 *    par la clé de signature de yt-dlp embarquée dans l'APK, dont l'empreinte est épinglée ici ;
 * 2. le SHA-256 du fichier `yt-dlp` téléchargé est celui de ce fichier d'empreintes, et aussi celui
 *    que l'API GitHub annonce pour l'asset.
 */
object YtDlpReleaseVerifier {

    /** Clé « Simon Sawicki (yt-dlp signing key) », publiée dans le dépôt yt-dlp (`public.key`). */
    const val SIGNING_KEY_FINGERPRINT = "AC0CBBE6848D6A873464AF4E57CF65933B5A7581"
    const val BINARY_NAME = "yt-dlp"

    fun embeddedPublicKey(): InputStream =
        checkNotNull(YtDlpReleaseVerifier::class.java.getResourceAsStream("/ytdlp/public.key")) { "clé publique yt-dlp absente de l'APK" }

    /** Vérifie la signature du fichier d'empreintes ; lève [YtDlpVerificationException] sinon. */
    fun verifySums(sums: ByteArray, signature: ByteArray, publicKey: InputStream, expectedFingerprint: String = SIGNING_KEY_FINGERPRINT) {
        val keys = trustedKeys(publicKey, expectedFingerprint).associateBy { it.keyID }
        val signatures = runCatching {
            val factory = JcaPGPObjectFactory(PGPUtil.getDecoderStream(signature.inputStream()))
            generateSequence { factory.nextObject() }.filterIsInstance<PGPSignatureList>().firstOrNull()
        }.getOrNull() ?: throw YtDlpVerificationException("signature du fichier d'empreintes illisible")
        val valid = signatures.any { sig ->
            val key = keys[sig.keyID] ?: return@any false
            runCatching {
                sig.init(JcaPGPContentVerifierBuilderProvider(), key)
                sig.update(sums)
                sig.verify()
            }.getOrDefault(false)
        }
        if (!valid) throw YtDlpVerificationException("la signature du fichier d'empreintes ne correspond pas à la clé de yt-dlp")
    }

    /** Empreinte SHA-256 annoncée pour [fileName] dans un fichier `SHA2-256SUMS`. */
    fun expectedSha256(sums: ByteArray, fileName: String = BINARY_NAME): String {
        val line = sums.decodeToString().lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == fileName }
            ?: throw YtDlpVerificationException("$fileName absent du fichier d'empreintes")
        val hash = line[0].lowercase()
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw YtDlpVerificationException("empreinte de $fileName invalide")
        return hash
    }

    /** Compare le fichier téléchargé à l'empreinte signée, et à celle de l'API GitHub si elle est fournie. */
    fun verifyBinary(binary: InputStream, signedSha256: String, githubSha256: String?) {
        if (githubSha256 != null && githubSha256.lowercase() != signedSha256) {
            throw YtDlpVerificationException("l'empreinte annoncée par GitHub diffère de l'empreinte signée")
        }
        val actual = sha256(binary)
        if (actual != signedSha256) throw YtDlpVerificationException("le fichier téléchargé ne correspond pas à l'empreinte signée")
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use {
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Clés de l'anneau dont la clé principale a l'empreinte épinglée (clé principale et sous-clés :
     * yt-dlp peut signer avec une sous-clé). Toute autre clé du fichier est ignorée.
     */
    private fun trustedKeys(publicKey: InputStream, expectedFingerprint: String): List<PGPPublicKey> {
        val rings = runCatching { PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicKey), JcaKeyFingerprintCalculator()) }
            .getOrElse { throw YtDlpVerificationException("clé publique yt-dlp illisible") }
        val wanted = expectedFingerprint.uppercase()
        val ring = rings.keyRings.asSequence().firstOrNull { ring ->
            ring.publicKey.fingerprint.joinToString("") { "%02X".format(it) } == wanted
        } ?: throw YtDlpVerificationException("clé de signature yt-dlp attendue introuvable")
        return ring.publicKeys.asSequence().toList()
    }
}
