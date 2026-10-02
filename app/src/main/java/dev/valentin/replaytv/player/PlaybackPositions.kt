package dev.valentin.replaytv.player

import android.content.Context

/**
 * Mémorise la position de lecture par vidéo (clé = URL de la page de la vidéo, commune au
 * streaming et au fichier téléchargé). Une vidéo regardée jusqu'à la fin est oubliée.
 */
class PlaybackPositions(context: Context) {

    private val prefs = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)

    fun get(key: String): Long = prefs.getLong(key, 0L)

    fun save(key: String, positionMs: Long, durationMs: Long) {
        val nearEnd = durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS
        if (positionMs < MIN_POSITION_MS || nearEnd) clear(key) else prefs.edit().putLong(key, positionMs).apply()
    }

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val MIN_POSITION_MS = 5_000L
        private const val END_MARGIN_MS = 15_000L
    }
}

fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
