package dev.valentin.replaytv.ytdlp

import android.content.Context
import dev.valentin.replaytv.catalog.arr
import dev.valentin.replaytv.catalog.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Met yt-dlp à jour depuis ses releases GitHub, à la place de la fonction de youtubedl-android qui
 * ne vérifie rien. Une release n'est installée que si :
 * - son fichier `SHA2-256SUMS` porte une signature valide de la clé yt-dlp embarquée dans l'APK ;
 * - le fichier `yt-dlp` téléchargé a le SHA-256 signé (et celui annoncé par l'API GitHub).
 * Elle remplace alors le fichier que la bibliothèque utilise, et son numéro de version.
 */
class YtDlpUpdater(private val context: Context, http: OkHttpClient) {

    private val client = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Résultat lisible pour le journal ; lève [YtDlpVerificationException] si la release est refusée. */
    fun update(currentVersion: String?): String {
        val release = json.parseToJsonElement(getString(LATEST_RELEASE))
        val tag = release.str("tag_name") ?: throw IOException("release yt-dlp sans numéro de version")

        val assets = release.arr("assets")
        val binary = assets.asset(YtDlpReleaseVerifier.BINARY_NAME) ?: throw IOException("release $tag sans fichier ${YtDlpReleaseVerifier.BINARY_NAME}")
        val sumsAsset = assets.asset(SUMS) ?: throw YtDlpVerificationException("la release $tag ne publie pas de fichier d'empreintes")
        val sigAsset = assets.asset("$SUMS.sig") ?: throw YtDlpVerificationException("la release $tag n'est pas signée")

        val sums = getBytes(sumsAsset.url())
        YtDlpReleaseVerifier.verifySums(sums, getBytes(sigAsset.url()), YtDlpReleaseVerifier.embeddedPublicKey())
        val signedSha256 = YtDlpReleaseVerifier.expectedSha256(sums)

        // Même version déjà en place : on contrôle tout de même le fichier installé (il a pu être
        // posé sans vérification par une version précédente de l'application) et on ne le
        // retélécharge que s'il ne correspond pas à l'empreinte signée.
        val installed = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/${YtDlpReleaseVerifier.BINARY_NAME}")
        if (tag == currentVersion && installed.exists() && YtDlpReleaseVerifier.sha256(installed.inputStream()) == signedSha256) {
            return "déjà à jour ($tag, fichier en place vérifié)"
        }

        val download = File(context.cacheDir, "yt-dlp-$tag.download")
        try {
            downloadTo(binary.url(), download)
            YtDlpReleaseVerifier.verifyBinary(download.inputStream(), signedSha256, binary.str("digest")?.removePrefix("sha256:"))
            install(download, tag, release.str("name") ?: tag)
        } finally {
            download.delete()
        }
        return "installé $tag (signature et SHA-256 vérifiés)"
    }

    /** Remplace le fichier utilisé par youtubedl-android ; le renommage final est atomique. */
    private fun install(verified: File, tag: String, name: String) {
        val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp").apply { mkdirs() }
        val staging = File(dir, "yt-dlp.new")
        verified.copyTo(staging, overwrite = true)
        if (!staging.renameTo(File(dir, YtDlpReleaseVerifier.BINARY_NAME))) {
            staging.delete()
            throw IOException("installation de yt-dlp impossible")
        }
        // Clés lues par YoutubeDL.version() ; même fichier de préférences que la bibliothèque.
        context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit()
            .putString("dlpVersion", tag)
            .putString("dlpVersionName", name)
            .apply()
    }

    private fun List<JsonElement>.asset(name: String): JsonElement? = firstOrNull { it.str("name") == name }

    private fun JsonElement.url(): String = str("browser_download_url") ?: throw IOException("asset ${str("name")} sans URL")

    private fun getString(url: String): String = execute(url).decodeToString()

    private fun getBytes(url: String): ByteArray = execute(url)

    private fun execute(url: String): ByteArray {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json, */*").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} sur $url")
            response.body.bytes()
        }
    }

    private fun downloadTo(url: String, target: File) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} sur $url")
            response.body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    companion object {
        private const val LATEST_RELEASE = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val SUMS = "SHA2-256SUMS"
    }
}
