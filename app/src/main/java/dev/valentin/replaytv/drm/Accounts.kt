package dev.valentin.replaytv.drm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val login: String, val password: String)

class AccountMissingException(source: Source) :
    Exception("Aucun compte ${source.label} : renseignez-le dans « Comptes » depuis l'accueil.")

class LoginFailedException(source: Source, reason: String) :
    Exception("Connexion ${source.label} refusée : $reason")

/**
 * Identifiants TF1+ / M6+ saisis sur la box, chiffrés par une clé du Keystore Android.
 * Ils ne quittent l'appareil que vers le service de connexion de la chaîne.
 */
class Accounts(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    private val _configured = MutableStateFlow(configuredSources())

    /** Sources DRM pour lesquelles un compte est enregistré. */
    val configured: StateFlow<Set<Source>> = _configured.asStateFlow()

    fun get(source: Source): Credentials? {
        val login = read("${source.name}.login") ?: return null
        val password = read("${source.name}.password") ?: return null
        return Credentials(login, password)
    }

    fun require(source: Source): Credentials = get(source) ?: throw AccountMissingException(source)

    fun save(source: Source, credentials: Credentials) {
        prefs.edit {
            putString("${source.name}.login", encrypt(credentials.login))
            putString("${source.name}.password", encrypt(credentials.password))
        }
        _configured.value = configuredSources()
    }

    fun clear(source: Source) {
        prefs.edit {
            remove("${source.name}.login")
            remove("${source.name}.password")
        }
        _configured.value = configuredSources()
    }

    /** Identifiant d'appareil stable, exigé par l'authentification M6. */
    val deviceId: String by lazy {
        prefs.getString("deviceId", null) ?: "_luid_${UUID.randomUUID()}".also { prefs.edit { putString("deviceId", it) } }
    }

    private fun configuredSources(): Set<Source> = Source.entries.filter { it.drm && get(it) != null }.toSet()

    // Valeur illisible (clé du Keystore perdue après une restauration, par exemple) : traitée comme absente.
    private fun read(key: String): String? = prefs.getString(key, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    /** AES-GCM avec une clé non exportable du Keystore ; stocké en Base64 sous la forme IV + texte chiffré. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LENGTH))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "accounts"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
