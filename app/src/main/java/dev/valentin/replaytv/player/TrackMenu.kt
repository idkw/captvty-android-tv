package dev.valentin.replaytv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import java.util.Locale

/** Une ligne du menu sous-titres / audio. [section] sert d'en-tête, [action] applique le choix. */
class MenuItem(
    val section: String,
    val label: String,
    val selected: Boolean,
    val action: () -> Unit,
)

const val SECTION_SUBTITLES = "Sous-titres"
const val SECTION_SUBTITLE_SIZE = "Taille des sous-titres"
const val SECTION_AUDIO = "Audio"

fun Tracks.groupsOfType(type: Int): List<Tracks.Group> = groups.filter { it.type == type && it.isSupported }

/** Libellé lisible d'une piste : « Français », « Français · malentendants », « Audiodescription »… */
fun trackLabel(format: Format, fallback: String): String {
    // Un code inconnu de Java (ex. « qsm » chez France TV) ressort tel quel : on l'ignore alors
    // au profit du libellé fourni par le flux.
    val language = format.language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { tag ->
            Locale.forLanguageTag(tag).getDisplayLanguage(Locale.FRENCH)
                .takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
                ?.replaceFirstChar { c -> c.uppercase() }
        }
    val qualifiers = buildList {
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) add("malentendants")
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("audiodescription")
        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("forcés")
        if (language != null) format.label?.takeIf { it.isNotBlank() && it != language }?.let { add(it) }
    }
    val base = language ?: format.label ?: fallback
    return if (qualifiers.isEmpty()) base else "$base · ${qualifiers.joinToString(", ")}"
}
