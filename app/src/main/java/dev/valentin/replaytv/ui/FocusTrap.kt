package dev.valentin.replaytv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Garde le focus de la télécommande dans une fenêtre affichée par-dessus l'écran : sans cela, les
 * flèches le font passer aux cartes de l'écran derrière, et OK ouvre une chaîne au lieu du bouton.
 */
fun Modifier.trapFocus(): Modifier = focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
