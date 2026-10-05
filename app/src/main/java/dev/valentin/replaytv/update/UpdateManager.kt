package dev.valentin.replaytv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.valentin.replaytv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val manual: Boolean) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Verifying(val info: UpdateInfo) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Mise à jour depuis les releases GitHub : vérification au démarrage, téléchargement de l'APK
 * dans le cache, contrôles avant installation, puis installateur système.
 *
 * Avant de lancer l'installation, deux vérifications :
 * 1. le SHA-256 du fichier téléchargé est celui annoncé par l'API GitHub pour cet APK ;
 * 2. l'APK est signé par le même certificat que l'application installée. Le système refuserait de
 *    toute façon une signature différente, mais on préfère expliquer avant plutôt qu'échouer après.
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    // Le client partagé limite chaque appel à 30 s ; un APK de 200 Mo demande plus. Seule
    // l'inactivité est bornée ici.
    private val downloadClient = http.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var checkedOnce = false
    private var downloadJob: Job? = null

    /** Vérification automatique : une fois par lancement, silencieuse sauf si une version est disponible. */
    fun checkOnce() {
        if (checkedOnce) return
        checkedOnce = true
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        if (manual) _state.value = UpdateState.Checking
        scope.launch {
            val result = runCatching { fetchLatest() }
            val info = result.getOrNull()
            when {
                result.isFailure -> if (manual) _state.value = UpdateState.Failed("Impossible de joindre GitHub : ${result.exceptionOrNull()?.message}")
                info == null -> if (manual) _state.value = UpdateState.UpToDate(manual = true)
                !manual && prefs.getString(KEY_IGNORED, null) == info.tag -> Unit
                else -> _state.value = UpdateState.Available(info)
            }
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(UpdateChecker.LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            UpdateChecker.parseLatest(response.body.string(), currentVersion, Build.SUPPORTED_ABIS.toList())
        }
    }

    fun ignore(info: UpdateInfo) {
        prefs.edit().putString(KEY_IGNORED, info.tag).apply()
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Idle
    }

    fun download(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        _state.value = UpdateState.Downloading(info, 0f)
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val file = downloadApk(info)
                _state.value = UpdateState.Verifying(info)
                verify(info, file)
                _state.value = if (canInstall()) UpdateState.ReadyToInstall(info, file) else UpdateState.NeedsPermission(info, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message ?: e.toString())
            }
        }
    }

    private fun downloadApk(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, info.assetName)
        val request = Request.Builder().url(info.assetUrl).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Téléchargement refusé : HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: info.assetSize
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        return file
    }

    private fun verify(info: UpdateInfo, file: File) {
        info.sha256?.let { expected ->
            val actual = file.sha256()
            if (actual != expected) throw IOException("Le fichier téléchargé ne correspond pas à l'empreinte publiée sur GitHub.")
        }
        val installed = signingDigests(null)
        val downloaded = signingDigests(file.absolutePath)
        if (downloaded.isEmpty()) throw IOException("APK non signé ou illisible.")
        if (installed.isNotEmpty() && installed.intersect(downloaded).isEmpty()) {
            throw IOException(
                "Cet APK n'est pas signé avec la même clé que la version installée. " +
                    "Installez-le manuellement une fois (désinstaller puis installer) ; les mises à jour suivantes se feront ici.",
            )
        }
    }

    /** Empreintes SHA-256 des certificats de signature, de l'application installée ou d'un APK. */
    private fun signingDigests(apkPath: String?): Set<String> {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val info = if (apkPath == null) pm.getPackageInfo(context.packageName, flags) else pm.getPackageArchiveInfo(apkPath, flags)
                val signing = info?.signingInfo ?: return emptySet()
                val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
                signers.map { sha256Hex(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val flags = PackageManager.GET_SIGNATURES
                @Suppress("DEPRECATION")
                val info = if (apkPath == null) pm.getPackageInfo(context.packageName, flags) else pm.getPackageArchiveInfo(apkPath, flags)
                @Suppress("DEPRECATION")
                info?.signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
            }
        }.getOrDefault(emptySet())
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Ouvre l'installateur système ; l'utilisateur confirme, et le système remplace l'application. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Écran système où autoriser cette application à installer des APK. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Après le retour de l'écran d'autorisation : si elle est accordée, on peut installer. */
    fun refreshPermission() {
        val current = _state.value
        if (current is UpdateState.NeedsPermission && canInstall()) _state.value = UpdateState.ReadyToInstall(current.info, current.file)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_IGNORED = "ignored_tag"
    }
}
