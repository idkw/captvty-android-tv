package dev.valentin.replaytv.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

suspend fun OkHttpClient.getString(url: String, accept: String = "*/*"): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", accept).build()
        newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} sur $url")
            response.body.string()
        }
    }

// Navigation tolérante dans un JsonElement : null si la clé manque ou n'a pas le type attendu.
fun JsonElement?.obj(key: String): JsonObject? =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull } as? JsonObject

fun JsonElement?.arr(key: String): List<JsonElement> =
    ((this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull })?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()

fun JsonElement?.str(key: String): String? =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

fun JsonElement?.int(key: String): Int? =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

fun JsonElement?.bool(key: String): Boolean? =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

fun JsonElement.asObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
