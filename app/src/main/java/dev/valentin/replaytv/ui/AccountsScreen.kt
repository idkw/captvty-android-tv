package dev.valentin.replaytv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.drm.Credentials
import dev.valentin.replaytv.model.Source
import kotlinx.coroutines.launch

/** Saisie des comptes TF1+ / M6+ à la télécommande. Les identifiants sont vérifiés auprès de la chaîne avant d'être enregistrés. */
@Composable
fun AccountsScreen(app: ReplayTvApp) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Comptes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Les comptes gratuits suffisent pour les replays gratuits. Ils sont chiffrés sur la box et servent uniquement à obtenir les licences de lecture.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp), modifier = Modifier.fillMaxWidth()) {
            AccountForm(app, Source.TF1, Modifier.weight(1f), firstFocus)
            AccountForm(app, Source.M6, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccountForm(app: ReplayTvApp, source: Source, modifier: Modifier, firstFocus: FocusRequester? = null) {
    val configured by app.accounts.configured.collectAsStateWithLifecycle()
    val saved = remember(configured) { app.accounts.get(source) }
    var login by remember(saved) { mutableStateOf(saved?.login.orEmpty()) }
    var password by remember(saved) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(source.label, style = MaterialTheme.typography.titleLarge)
        Text(
            saved?.let { "Connecté : ${it.login}" } ?: "Aucun compte enregistré",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvTextField(
            value = login,
            onValueChange = { login = it },
            placeholder = "Adresse e-mail",
            keyboardType = KeyboardType.Email,
            modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
        )
        TvTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = if (saved != null) "Nouveau mot de passe" else "Mot de passe",
            keyboardType = KeyboardType.Password,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy && login.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        message = runCatching { app.drm.saveAccount(source, Credentials(login.trim(), password)) }.fold(
                            onSuccess = { "Connexion réussie, compte enregistré." to false },
                            onFailure = { (it.message ?: it.toString()) to true },
                        )
                        busy = false
                    }
                },
            ) { Text(if (busy) "Vérification…" else "Vérifier et enregistrer") }
            if (saved != null) {
                Button(enabled = !busy, onClick = { app.drm.removeAccount(source); message = null }) { Text("Supprimer") }
            }
        }
        message?.let { (text, isError) ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
