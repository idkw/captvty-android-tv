package dev.valentin.replaytv.update

import dev.valentin.replaytv.catalog.arr
import dev.valentin.replaytv.catalog.asObjectOrNull
import dev.valentin.replaytv.catalog.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Une version publiée sur GitHub, avec l'APK qui convient à cet appareil. */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val pageUrl: String,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    /** SHA-256 hexadécimal annoncé par l'API GitHub, ou null si absent. */
    val sha256: String?,
)

/**
 * Lit la dernière release GitHub et décide si elle est plus récente que la version installée.
 * Fonctions pures, sans réseau, pour rester testables.
 */
object UpdateChecker {

    const val LATEST_RELEASE_URL = "https://api.github.com/repos/idkw/captvty-android-tv/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    /** `null` quand la release n'est pas plus récente ou n'a pas d'APK utilisable. */
    fun parseLatest(body: String, currentVersion: String, supportedAbis: List<String>): UpdateInfo? {
        val release = runCatching { json.parseToJsonElement(body) }.getOrNull()?.asObjectOrNull() ?: return null
        if (release.str("draft") == "true" || release.str("prerelease") == "true") return null
        val tag = release.str("tag_name") ?: return null
        val version = tag.removePrefix("v")
        if (compareVersions(version, currentVersion) <= 0) return null
        val asset = pickAsset(release.arr("assets"), supportedAbis) ?: return null
        return UpdateInfo(
            version = version,
            tag = tag,
            notes = release.str("body").orEmpty().trim(),
            pageUrl = release.str("html_url") ?: "https://github.com/idkw/captvty-android-tv/releases",
            assetName = asset.str("name") ?: return null,
            assetUrl = asset.str("browser_download_url") ?: return null,
            assetSize = (asset as? kotlinx.serialization.json.JsonObject)?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            sha256 = asset.str("digest")?.removePrefix("sha256:")?.lowercase()?.takeIf { it.length == 64 },
        )
    }

    /**
     * L'APK de la première architecture supportée par l'appareil qui a un fichier dans la release,
     * sinon l'APK universel, qui s'installe partout.
     */
    fun pickAsset(assets: List<JsonElement>, supportedAbis: List<String>): JsonElement? {
        val apks = assets.filter { it.str("name")?.endsWith(".apk") == true && it.str("state") != "starter" }
        supportedAbis.forEach { abi ->
            apks.firstOrNull { it.str("name")?.endsWith("-$abi.apk") == true }?.let { return it }
        }
        return apks.firstOrNull { it.str("name")?.endsWith("-universal.apk") == true }
    }

    /** Compare deux versions `X.Y.Z` numériquement ; un suffixe non numérique est ignoré. */
    fun compareVersions(a: String, b: String): Int {
        val left = a.numbers()
        val right = b.numbers()
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun String.numbers(): List<Int> =
        removePrefix("v").substringBefore('-').split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
