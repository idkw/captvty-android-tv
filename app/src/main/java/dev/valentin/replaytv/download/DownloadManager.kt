package dev.valentin.replaytv.download

import android.content.Context
import android.content.Intent
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.ytdlp.MediaInfo
import dev.valentin.replaytv.ytdlp.YtDlp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class DownloadMeta(
    val id: String,
    val title: String,
    val subtitle: String?,
    val source: String,
    val sourceUrl: String,
    val imageUrl: String?,
    val durationSeconds: Int?,
    val createdAt: Long,
)

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Running(val progress: Float, val etaSeconds: Long) : DownloadStatus
    data object Done : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

data class DownloadEntry(val meta: DownloadMeta, val status: DownloadStatus, val file: File) {
    val isActive: Boolean get() = status is DownloadStatus.Queued || status is DownloadStatus.Running
}

/**
 * File d'attente de téléchargements, exécutés un par un. Les fichiers vont dans le stockage
 * privé externe de l'application (aucune permission nécessaire), avec un JSON de métadonnées
 * à côté de chaque MP4 pour reconstruire la liste au démarrage.
 */
class DownloadManager(
    private val context: Context,
    private val ytDlp: YtDlp,
    private val scope: CoroutineScope,
) {
    val directory: File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "replays").apply { mkdirs() }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val queue = Channel<DownloadMeta>(Channel.UNLIMITED)

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    val isBusy: StateFlow<Boolean> = entries
        .map { list -> list.any { it.isActive } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch(Dispatchers.IO) { loadCompleted() }
        scope.launch(Dispatchers.IO) { for (meta in queue) runOne(meta) }
    }

    fun entryFor(sourceUrl: String): DownloadEntry? = _entries.value.firstOrNull { it.meta.sourceUrl == sourceUrl }

    fun enqueue(video: CatalogItem.Video, info: MediaInfo?) {
        if (entryFor(video.url) != null) return
        val meta = DownloadMeta(
            id = UUID.randomUUID().toString().take(8),
            title = info?.title ?: video.title,
            subtitle = video.subtitle,
            source = video.source.label,
            sourceUrl = video.url,
            imageUrl = info?.thumbnailUrl ?: video.imageUrl,
            durationSeconds = info?.durationSeconds ?: video.durationSeconds,
            createdAt = System.currentTimeMillis(),
        )
        setStatus(meta, DownloadStatus.Queued)
        context.startForegroundService(Intent(context, DownloadService::class.java))
        queue.trySend(meta)
    }

    fun cancel(entry: DownloadEntry) {
        if (entry.status is DownloadStatus.Running) {
            ytDlp.cancel(entry.meta.id)
        } else {
            remove(entry)
        }
    }

    fun delete(entry: DownloadEntry) {
        entry.file.delete()
        sidecar(entry.meta.id).delete()
        deletePartialFiles(entry.meta.id)
        remove(entry)
    }

    private fun deletePartialFiles(id: String) {
        directory.listFiles { f -> f.name.startsWith("$id.") && f.extension != "json" }?.forEach { it.delete() }
    }

    private suspend fun runOne(meta: DownloadMeta) {
        if (entryFor(meta.sourceUrl) == null) return // annulé pendant l'attente
        setStatus(meta, DownloadStatus.Running(0f, -1))
        try {
            val template = File(directory, "${meta.id}.%(ext)s").absolutePath
            ytDlp.download(meta.sourceUrl, template, meta.id) { progress, eta ->
                setStatus(meta, DownloadStatus.Running(progress, eta))
            }
            val produced = fileFor(meta.id)
            if (produced == null || produced.length() == 0L) throw IllegalStateException("Aucun fichier produit")
            sidecar(meta.id).writeText(json.encodeToString(DownloadMeta.serializer(), meta))
            setStatus(meta, DownloadStatus.Done, produced)
        } catch (cancelled: YoutubeDL.CanceledException) {
            deletePartialFiles(meta.id)
            entryFor(meta.sourceUrl)?.let(::remove)
        } catch (e: Exception) {
            // Un .part de plusieurs Go ne doit pas rester bloquer le stockage de la box.
            deletePartialFiles(meta.id)
            val message = e.message?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: e.toString()
            setStatus(meta, DownloadStatus.Failed(message))
        }
    }

    private fun loadCompleted() {
        val completed = directory.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { sidecarFile ->
            val meta = runCatching { json.decodeFromString(DownloadMeta.serializer(), sidecarFile.readText()) }.getOrNull()
                ?: return@mapNotNull null
            val file = fileFor(meta.id) ?: return@mapNotNull null
            DownloadEntry(meta, DownloadStatus.Done, file)
        }.sortedByDescending { it.meta.createdAt }
        _entries.update { current -> completed + current.filter { c -> completed.none { it.meta.id == c.meta.id } } }
    }

    private fun fileFor(id: String): File? =
        directory.listFiles { f -> f.name.startsWith("$id.") && f.extension != "json" && !f.name.contains(".part") && !f.name.contains(".ytdl") }
            ?.maxByOrNull { it.length() }

    private fun sidecar(id: String) = File(directory, "$id.json")

    private fun setStatus(meta: DownloadMeta, status: DownloadStatus, file: File? = null) {
        _entries.update { list ->
            val existing = list.firstOrNull { it.meta.id == meta.id }
            val entry = DownloadEntry(meta, status, file ?: existing?.file ?: File(directory, "${meta.id}.mp4"))
            if (existing == null) listOf(entry) + list else list.map { if (it.meta.id == meta.id) entry else it }
        }
    }

    private fun remove(entry: DownloadEntry) {
        _entries.update { list -> list.filterNot { it.meta.id == entry.meta.id } }
    }
}
