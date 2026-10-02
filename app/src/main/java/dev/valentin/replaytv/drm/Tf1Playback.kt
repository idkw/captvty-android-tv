package dev.valentin.replaytv.drm

import android.os.SystemClock
import dev.valentin.replaytv.catalog.arr
import dev.valentin.replaytv.catalog.getString
import dev.valentin.replaytv.catalog.int
import dev.valentin.replaytv.catalog.obj
import dev.valentin.replaytv.catalog.postForm
import dev.valentin.replaytv.catalog.postJson
import dev.valentin.replaytv.catalog.str
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Lecture TF1+ : connexion Gigya (`compte.tf1.fr`), échange contre un jeton TF1, puis
 * `mediainfo.tf1.fr` renvoie le manifeste DASH et l'URL de licence Widevine de la vidéo.
 */
class Tf1Playback(private val http: OkHttpClient, private val accounts: Accounts) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var token: String? = null
    private var tokenExpiresAt = 0L

    suspend fun stream(videoId: String): DrmStream {
        // Jeton expiré ou révoqué côté TF1 : une seule reconnexion avant d'abandonner.
        val first = mediainfo(videoId, token(forceLogin = false))
        val delivery = if ((first.int("code") ?: 0) in AUTH_ERRORS) mediainfo(videoId, token(forceLogin = true)) else first
        val code = delivery.int("code") ?: 0
        if (code >= 400) throw IllegalStateException("TF1+ refuse la lecture (code $code) : ${delivery.str("error") ?: "raison inconnue"}")
        val manifest = delivery.str("url") ?: throw IllegalStateException("TF1+ n'a pas renvoyé de flux.")
        val license = delivery.arr("drms").firstOrNull { it.str("name") == "widevine" }.str("url")
            ?: throw IllegalStateException("TF1+ n'a pas renvoyé de licence Widevine.")
        return DrmStream(manifest, license)
    }

    /** Ouvre une session avec ces identifiants, sans les enregistrer : sert à les vérifier. */
    suspend fun check(credentials: Credentials) {
        login(credentials)
    }

    fun forget() {
        token = null
    }

    private suspend fun token(forceLogin: Boolean): String = mutex.withLock {
        val cached = token
        if (!forceLogin && cached != null && SystemClock.elapsedRealtime() < tokenExpiresAt) return cached
        val (fresh, ttlSeconds) = login(accounts.require(Source.TF1))
        token = fresh
        // Marge d'une minute pour ne pas présenter un jeton qui expire pendant la requête.
        tokenExpiresAt = SystemClock.elapsedRealtime() + (ttlSeconds - 60).coerceAtLeast(60) * 1000L
        fresh
    }

    private suspend fun login(credentials: Credentials): Pair<String, Long> {
        val session = json.parseToJsonElement(
            http.postForm(
                "https://compte.tf1.fr/accounts.login",
                mapOf(
                    "loginID" to credentials.login,
                    "password" to credentials.password,
                    "APIKey" to GIGYA_API_KEY,
                    "targetEnv" to "jssdk",
                    "includeUserInfo" to "true",
                    "sessionExpiration" to "31536000",
                    "format" to "json",
                ),
            ),
        )
        // Gigya répond toujours en HTTP 200 : l'échec se lit dans errorCode.
        if (session.int("errorCode") != 0) {
            throw LoginFailedException(Source.TF1, session.str("errorDetails") ?: session.str("errorMessage") ?: "erreur inconnue")
        }
        val user = session.obj("userInfo")
        val body = buildJsonObject {
            put("uid", user.str("UID"))
            put("signature", user.str("UIDSignature"))
            put("timestamp", user.str("signatureTimestamp")?.toLongOrNull())
            putJsonArray("consent_ids") { }
        }
        val response = json.parseToJsonElement(http.postJson("https://www.tf1.fr/token/gigya/web", body.toString()))
        val token = response.str("token") ?: throw LoginFailedException(Source.TF1, "jeton absent de la réponse")
        return token to (response.int("ttl")?.toLong() ?: 3600L)
    }

    private suspend fun mediainfo(videoId: String, token: String) = json.parseToJsonElement(
        http.getString(
            "https://mediainfo.tf1.fr/mediainfocombo/$videoId".toHttpUrl().newBuilder()
                .addQueryParameter("context", "MYTF1")
                .addQueryParameter("pver", "5029000")
                .addQueryParameter("platform", "web")
                .addQueryParameter("device", "desktop")
                .addQueryParameter("os", "linux")
                .addQueryParameter("osVersion", "unknown")
                .addQueryParameter("topDomain", "www.tf1.fr")
                .addQueryParameter("playerVersion", "5.29.0")
                .addQueryParameter("productName", "mytf1")
                .addQueryParameter("productVersion", "3.37.0")
                .addQueryParameter("format", "dash")
                .build().toString(),
            accept = "application/json",
            headers = mapOf("Authorization" to "Bearer $token"),
        ),
    ).obj("delivery")

    companion object {
        // Clé publique du client web Gigya de tf1.fr (visible dans le code du site), pas un secret.
        private const val GIGYA_API_KEY = "3_hWgJdARhz_7l1oOp3a8BDLoR9cuWZpUaKG4aqF7gum9_iK3uTZ2VlDBl8ANf8FVk"
        private val AUTH_ERRORS = setOf(401, 403)
    }
}
