package dev.valentin.replaytv.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** État observable du lecteur, mis à jour par l'activité (touches, ExoPlayer) et lu par la surcouche Compose. */
class PlayerUiState {
    var controlsVisible by mutableStateOf(true)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    /** Position visée pendant un déplacement à la télécommande, avant validation. */
    var seekTargetMs by mutableStateOf<Long?>(null)
    var lastInteractionAt by mutableLongStateOf(SystemClock.uptimeMillis())
    var resumedFromMs by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)

    /** Sous-titres courants, rendus par la surcouche (et non par Media3, dont le positionnement sort de l'écran). */
    var cues by mutableStateOf<List<String>>(emptyList())
    var subtitleSizeIndex by mutableStateOf(1)

    var menuOpen by mutableStateOf(false)
    var menuIndex by mutableStateOf(0)
    var menuItems by mutableStateOf<List<MenuItem>>(emptyList())

    fun showControls() {
        controlsVisible = true
        lastInteractionAt = SystemClock.uptimeMillis()
    }
}
