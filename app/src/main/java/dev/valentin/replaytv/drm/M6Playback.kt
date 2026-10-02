package dev.valentin.replaytv.drm

import android.util.Base64
import dev.valentin.replaytv.catalog.M6Catalog
import dev.valentin.replaytv.catalog.arr
import dev.valentin.replaytv.catalog.getString
import dev.valentin.replaytv.catalog.int
import dev.valentin.replaytv.catalog.postForm
import dev.valentin.replaytv.catalog.str
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Lecture M6+ : connexion Gigya (`login-gigya.m6.fr`), échange contre un JWT 6cloud, puis un
 * jeton « upfront » par vidéo que la licence Widevine de DRMtoday attend dans `x-dt-auth-token`.
 * Le manifeste DASH lui-même est public, dans la fiche de la vidéo.
 */
class M6Playback(private val http: OkHttpClient, private val accounts: Accounts, private val catalog: M6Catalog) {

    private data class Session(val uid: String, val jwt: String, val expiresAtSeconds: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun stream(clipId: String): DrmStream {
        val manifest = manifest(clipId)
        val upfront = try {
            upfrontToken(session(forceLogin = false), clipId)
        } catch (e: IOException) {
            // JWT révoqué avant son expiration annoncée : une seule reconnexion avant d'abandonner.
            upfrontToken(session(forceLogin = true), clipId)
        }
        return DrmStream(manifest, LICENSE_URL, mapOf("x-dt-auth-token" to upfront))
    }

    /** Ouvre une session avec ces identifiants, sans les enregistrer : sert à les vérifier. */
    suspend fun check(credentials: Credentials) {
        login(credentials)
    }

    fun forget() {
        session = null
    }

    private suspend fun manifest(clipId: String): String {
        val assets = catalog.clip(clipId).arr("clips").firstOrNull().arr("assets")
            .filter { it.str("type") == "usp_dashcenc_h264" }
        val best = assets.firstOrNull { it.str("video_quality").equals("hd", ignoreCase = true) } ?: assets.firstOrNull()
        return best.str("full_physical_path") ?: throw IllegalStateException("M6+ ne propose pas de flux DASH pour cette vidéo.")
    }

    private suspend fun upfrontToken(session: Session, clipId: String): String {
        val response = json.parseToJsonElement(
            http.getString(
                "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/m6replay/users/${session.uid}/videos/$clipId/upfront-token",
                accept = "application/json",
                headers = mapOf(
                    "X-Customer-Name" to "m6web",
                    "X-Client-Release" to "5.103.3",
                    "Authorization" to "Bearer ${session.jwt}",
                ),
            ),
        )
        return response.str("token") ?: throw IllegalStateException("M6+ n'a pas délivré de jeton de licence.")
    }

    private suspend fun session(forceLogin: Boolean): Session = mutex.withLock {
        val cached = session
        val now = System.currentTimeMillis() / 1000
        if (!forceLogin && cached != null && now < cached.expiresAtSeconds - 60) return cached
        login(accounts.require(Source.M6)).also { session = it }
    }

    private suspend fun login(credentials: Credentials): Session {
        val gigya = json.parseToJsonElement(
            http.postForm(
                "https://login-gigya.m6.fr/accounts.login",
                mapOf(
                    "loginID" to credentials.login,
                    "password" to credentials.password,
                    "apiKey" to GIGYA_API_KEY,
                    "format" to "json",
                ),
            ),
        )
        // Gigya répond toujours en HTTP 200 : l'échec se lit dans errorCode.
        if (gigya.int("errorCode") != 0) {
            throw LoginFailedException(Source.M6, gigya.str("errorDetails") ?: gigya.str("errorMessage") ?: "erreur inconnue")
        }
        val uid = gigya.str("UID") ?: throw LoginFailedException(Source.M6, "identifiant absent de la réponse")
        val response = json.parseToJsonElement(
            http.getString(
                "https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt",
                accept = "application/json",
                headers = mapOf(
                    "x-auth-gigya-uid" to uid,
                    "x-auth-gigya-signature" to (gigya.str("UIDSignature") ?: ""),
                    "x-auth-gigya-signature-timestamp" to (gigya.str("signatureTimestamp") ?: ""),
                    "x-auth-device-id" to accounts.deviceId,
                    "x-customer-name" to "m6web",
                ),
            ),
        )
        val jwt = response.str("token") ?: throw LoginFailedException(Source.M6, "jeton absent de la réponse")
        return Session(uid, jwt, jwtExpiry(jwt) ?: (System.currentTimeMillis() / 1000 + 3600))
    }

    private fun jwtExpiry(jwt: String): Long? = runCatching {
        val payload = String(Base64.decode(jwt.split('.')[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        json.parseToJsonElement(payload).str("exp")?.toLongOrNull()
    }.getOrNull()

    companion object {
        // Clé publique du client web Gigya de m6.fr (visible dans le code du site), pas un secret.
        private const val GIGYA_API_KEY = "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK"

        // specConform=true : DRMtoday renvoie la licence brute, comme l'attend ExoPlayer.
        private const val LICENSE_URL = "https://lic.drmtoday.com/license-proxy-widevine/cenc/?specConform=true"
    }
}
