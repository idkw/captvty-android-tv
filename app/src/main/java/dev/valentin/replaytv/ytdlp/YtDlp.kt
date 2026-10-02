package dev.valentin.replaytv.ytdlp

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.valentin.replaytv.catalog.arr
import dev.valentin.replaytv.catalog.asObjectOrNull
import dev.valentin.replaytv.catalog.bool
import dev.valentin.replaytv.catalog.int
import dev.valentin.replaytv.catalog.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Ce que l'on retient de la sortie `yt-dlp -J` pour une vidéo. */
data class MediaInfo(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    /** Manifest HLS lisible directement par ExoPlayer, s'il existe. */
    val streamUrl: String?,
    val hasDrm: Boolean,
)

/**
 * Façade au-dessus de youtubedl-android (Python + yt-dlp + ffmpeg embarqués dans l'APK).
 * L'initialisation extrait l'environnement Python sur disque : elle prend quelques secondes
 * au premier lancement, puis est quasi instantanée.
 */
class YtDlp(private val context: Context, private val scope: CoroutineScope) {

    sealed interface State {
        data object Initializing : State
        data class Ready(val version: String?) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val ready = CompletableDeferred<Unit>()
    private val json = Json { ignoreUnknownKeys = true }

    fun initAsync() {
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                _state.value = State.Ready(YoutubeDL.getInstance().version(context))
                ready.complete(Unit)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.toString())
                ready.completeExceptionally(e)
            }
        }
    }

    suspend fun probe(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ready.await()
        val request = YoutubeDLRequest(url)
            .addOption("-J")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
        parseMediaInfo(json.parseToJsonElement(run(request)).asObjectOrNull(), url)
    }

    suspend fun listPlaylist(url: String): List<String> = withContext(Dispatchers.IO) {
        ready.await()
        val request = YoutubeDLRequest(url)
            .addOption("-J")
            .addOption("--flat-playlist")
            .addOption("--no-warnings")
        json.parseToJsonElement(run(request)).arr("entries").mapNotNull { it.str("url") }
    }

    /**
     * Télécharge la meilleure qualité ≤ 1080p dans un MP4. `outputTemplate` est un template
     * yt-dlp (ex. `/sdcard/.../abcd1234.%(ext)s`) ; `processId` sert à l'annulation.
     */
    suspend fun download(
        url: String,
        outputTemplate: String,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        ready.await()
        val request = YoutubeDLRequest(url)
            .addOption("-o", outputTemplate)
            .addOption("-f", FORMAT)
            .addOption("--merge-output-format", "mp4")
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--no-warnings")
            .addOption("--newline")
        YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ -> onProgress(progress, eta) }
    }

    fun cancel(processId: String): Boolean = YoutubeDL.getInstance().destroyProcessById(processId)

    private fun run(request: YoutubeDLRequest): String {
        val response = YoutubeDL.getInstance().execute(request)
        if (response.exitCode != 0) throw YoutubeDLException(response.err.ifBlank { "yt-dlp a échoué (code ${response.exitCode})" })
        return response.out
    }

    private fun parseMediaInfo(root: JsonObject?, url: String): MediaInfo {
        val formats = root.arr("formats").mapNotNull { it.asObjectOrNull() }
        val hls = formats.filter { it.str("protocol").orEmpty().startsWith("m3u8") }
        val bestHlsVideo = hls
            .filter { it.str("vcodec") != "none" }
            .maxByOrNull { it.int("height") ?: 0 }
        val streamUrl = bestHlsVideo?.str("manifest_url")
            ?: bestHlsVideo?.str("url")
            ?: root.str("manifest_url")
            ?: root.str("url")?.takeIf { root.str("protocol").orEmpty().startsWith("m3u8") }
        val hasDrm = root.bool("_has_drm") == true || formats.any { it.bool("has_drm") == true }
        return MediaInfo(
            id = root.str("id") ?: url.hashCode().toString(),
            title = root.str("title") ?: url,
            description = root.str("description")?.takeIf { it.isNotBlank() },
            thumbnailUrl = root.str("thumbnail"),
            durationSeconds = root.int("duration"),
            streamUrl = streamUrl,
            hasDrm = hasDrm,
        )
    }

    companion object {
        private const val FORMAT =
            "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
    }
}
