package dev.valentin.replaytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.valentin.replaytv.model.Channel

private class LogoStyle(val background: Color, val foreground: Color, val text: String, val dot: Color? = null)

/** Logos dessinés plutôt qu'importés : pas d'image à embarquer, lisibles à toute taille. */
private fun styleFor(channel: Channel): LogoStyle = when (channel.id) {
    "tf1" -> LogoStyle(Color(0xFF0E2E8A), Color.White, "TF1")
    "france-2" -> LogoStyle(Color.White, Color(0xFF141414), "2", dot = Color(0xFFE2001A))
    "france-3" -> LogoStyle(Color.White, Color(0xFF141414), "3", dot = Color(0xFF0068B4))
    "france-4" -> LogoStyle(Color.White, Color(0xFF141414), "4", dot = Color(0xFF8B2BB4))
    "france-5" -> LogoStyle(Color.White, Color(0xFF141414), "5", dot = Color(0xFF00A75D))
    "m6" -> LogoStyle(Color.White, Color(0xFF4E3FD6), "M6")
    "arte" -> LogoStyle(Color.White, Color(0xFFFF5B00), "arte")
    "tmc" -> LogoStyle(Color(0xFF14233F), Color.White, "TMC")
    "tfx" -> LogoStyle(Color(0xFF1A1A1A), Color(0xFFE63C2F), "TFX")
    "tf1-series-films" -> LogoStyle(Color(0xFF0E2E8A), Color.White, "TF1 Séries Films")
    "lci" -> LogoStyle(Color(0xFF0B1F5E), Color.White, "LCI")
    "w9" -> LogoStyle(Color.White, Color(0xFFE2001A), "W9")
    "6ter" -> LogoStyle(Color.White, Color(0xFF009FE3), "6ter")
    "franceinfo" -> LogoStyle(Color(0xFF1A1A1A), Color(0xFFF7D733), "franceinfo")
    else -> LogoStyle(Color(0xFF243247), Color.White, channel.label)
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    val style = styleFor(channel)
    val fontSize = when {
        style.text.length <= 2 -> 40.sp
        style.text.length <= 4 -> 32.sp
        style.text.length <= 8 -> 24.sp
        else -> 17.sp
    }
    Box(
        modifier = modifier.background(style.background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            style.dot?.let {
                Box(modifier = Modifier.size(14.dp).background(it, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                style.text,
                color = style.foreground,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = fontSize,
            )
        }
    }
}

@Composable
fun ChannelLogoBox(channel: Channel, modifier: Modifier = Modifier) {
    Box(modifier = modifier) { ChannelLogo(channel, Modifier.fillMaxSize()) }
}
