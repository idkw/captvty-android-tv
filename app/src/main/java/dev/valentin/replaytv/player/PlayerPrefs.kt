package dev.valentin.replaytv.player

import android.content.Context

/** Préférences du lecteur : piste de sous-titres choisie (langue ou désactivée) et taille du texte. */
class PlayerPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    /** Code langue des sous-titres voulus, ou null pour désactivés (valeur par défaut). */
    var subtitleLanguage: String?
        get() = prefs.getString("subtitle_language", null)
        set(value) = prefs.edit().putString("subtitle_language", value).apply()

    /** 0 = petit, 1 = normal, 2 = grand. */
    var subtitleSize: Int
        get() = prefs.getInt("subtitle_size", 1).coerceIn(0, SUBTITLE_SIZES.lastIndex)
        set(value) = prefs.edit().putInt("subtitle_size", value.coerceIn(0, SUBTITLE_SIZES.lastIndex)).apply()

    companion object {
        val SUBTITLE_SIZES = listOf("Petits", "Normaux", "Grands")
        val SUBTITLE_SIZE_SP = listOf(26, 34, 44)
    }
}
