package dev.valentin.replaytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.BuildConfig
import dev.valentin.replaytv.update.UpdateManager
import dev.valentin.replaytv.update.UpdateState

/** Boîte de dialogue de mise à jour, affichée par-dessus l'écran courant quand il y a quelque chose à dire. */
@Composable
fun UpdateDialog(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    if (state is UpdateState.Idle || state is UpdateState.Checking) return

    // Retour de l'écran d'autorisation d'installation : vérifier si elle a été accordée.
    LifecycleResumeEffect(Unit) {
        updates.refreshPermission()
        onPauseOrDispose { }
    }

    BackHandler { updates.dismiss() }

    val firstFocus = remember(state::class) { FocusRequester() }
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is UpdateState.Available -> {
                    Text("Mise à jour disponible : ${s.info.version}", style = MaterialTheme.typography.headlineSmall)
                    Text("Version installée : ${BuildConfig.VERSION_NAME} · fichier : ${s.info.assetName} (${s.info.assetSize / 1_048_576} Mo)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.info.notes.isNotBlank()) {
                        Text(s.info.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { updates.download(s.info) }, modifier = Modifier.focusRequester(firstFocus)) { Text("Télécharger et installer") }
                        Button(onClick = { updates.dismiss() }) { Text("Plus tard") }
                        Button(onClick = { updates.ignore(s.info) }) { Text("Ignorer cette version") }
                    }
                }

                is UpdateState.Downloading -> {
                    Text("Téléchargement de la version ${s.info.version}…", style = MaterialTheme.typography.headlineSmall)
                    ProgressBar(s.progress)
                    Text("${(s.progress * 100).toInt()} % de ${s.info.assetSize / 1_048_576} Mo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { updates.dismiss() }, modifier = Modifier.focusRequester(firstFocus)) { Text("Annuler") }
                }

                is UpdateState.Verifying -> {
                    Text("Vérification de l'APK…", style = MaterialTheme.typography.headlineSmall)
                    Text("Empreinte SHA-256 annoncée par GitHub et certificat de signature.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LoadingBar()
                }

                is UpdateState.ReadyToInstall -> {
                    Text("Version ${s.info.version} prête à installer", style = MaterialTheme.typography.headlineSmall)
                    Text("Empreinte et signature vérifiées. L'installateur Android va demander confirmation, puis remplacer l'application.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { updates.install(s.file) }, modifier = Modifier.focusRequester(firstFocus)) { Text("Installer") }
                        Button(onClick = { updates.dismiss() }) { Text("Plus tard") }
                    }
                }

                is UpdateState.NeedsPermission -> {
                    Text("Autorisation nécessaire", style = MaterialTheme.typography.headlineSmall)
                    Text("Android doit autoriser Replay TV à installer des applications. Le réglage s'ouvre dans les paramètres ; revenez ensuite ici pour installer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { updates.openInstallPermissionSettings() }, modifier = Modifier.focusRequester(firstFocus)) { Text("Ouvrir les paramètres") }
                        Button(onClick = { updates.dismiss() }) { Text("Plus tard") }
                    }
                }

                is UpdateState.UpToDate -> {
                    Text("Vous êtes à jour", style = MaterialTheme.typography.headlineSmall)
                    Text("Version installée : ${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { updates.dismiss() }, modifier = Modifier.focusRequester(firstFocus)) { Text("Fermer") }
                }

                is UpdateState.Failed -> {
                    Text("Mise à jour impossible", style = MaterialTheme.typography.headlineSmall)
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { updates.dismiss() }, modifier = Modifier.focusRequester(firstFocus)) { Text("Fermer") }
                }

                UpdateState.Idle, UpdateState.Checking -> Unit
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
    Spacer(Modifier.height(0.dp))
}
