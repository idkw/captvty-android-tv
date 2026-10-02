package dev.valentin.replaytv.drm

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Source

/** Dispatch par source des vidéos protégées : résolution du flux et gestion des comptes. */
class DrmPlayback(private val accounts: Accounts, private val tf1: Tf1Playback, private val m6: M6Playback) {

    suspend fun stream(video: CatalogItem.Video): DrmStream {
        val id = video.streamId ?: throw IllegalArgumentException("Vidéo sans identifiant de flux : ${video.url}")
        return when (video.source) {
            Source.TF1 -> tf1.stream(id)
            Source.M6 -> m6.stream(id)
            else -> throw IllegalArgumentException("${video.source.label} ne passe pas par les DRM.")
        }
    }

    /** Vérifie les identifiants auprès de la chaîne puis les enregistre ; lève une exception s'ils sont refusés. */
    suspend fun saveAccount(source: Source, credentials: Credentials) {
        when (source) {
            Source.TF1 -> tf1.check(credentials)
            Source.M6 -> m6.check(credentials)
            else -> throw IllegalArgumentException("${source.label} n'a pas de compte.")
        }
        accounts.save(source, credentials)
        forget(source)
    }

    fun removeAccount(source: Source) {
        accounts.clear(source)
        forget(source)
    }

    private fun forget(source: Source) = when (source) {
        Source.TF1 -> tf1.forget()
        Source.M6 -> m6.forget()
        else -> Unit
    }
}
