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
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

suspend fun OkHttpClient.getString(
    url: String,
    accept: String = "*/*",
    headers: Map<String, String> = emptyMap(),
): String = execute(Request.Builder().url(url).header("Accept", accept).headers(headers).build())

suspend fun OkHttpClient.postForm(url: String, fields: Map<String, String>): String =
    execute(Request.Builder().url(url).post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()).build())

suspend fun OkHttpClient.postJson(url: String, body: String): String =
    execute(Request.Builder().url(url).post(body.toRequestBody(JSON)).build())

private val JSON = "application/json".toMediaType()

/** Réponse brute : code HTTP et corps, pour les API qui signalent une erreur métier par un 4xx. */
class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

suspend fun OkHttpClient.getWithStatus(
    url: String,
    accept: String = "*/*",
    headers: Map<String, String> = emptyMap(),
): HttpResult = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).header("Accept", accept).headers(headers).build()
    newCall(request).execute().use { response -> HttpResult(response.code, response.body.string()) }
}

private fun Request.Builder.headers(headers: Map<String, String>): Request.Builder =
    apply { headers.forEach { (name, value) -> header(name, value) } }

private suspend fun OkHttpClient.execute(request: Request): String = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} sur ${request.url}")
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
