package dev.valentin.replaytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.valentin.replaytv.model.Section
import dev.valentin.replaytv.model.Sections
import dev.valentin.replaytv.model.Source

/** Navigation par thème (documentaires, séries…) pour chaque source, en complément des chaînes. */
@Composable
fun ThemesScreen(onSection: (Section) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Par thème", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.fillMaxWidth()) {
            SectionColumn(Source.FRANCE_TV, Sections.franceTv, onSection, Modifier.weight(1f).focusRequester(firstFocus))
            SectionColumn(Source.ARTE, Sections.arte, onSection, Modifier.weight(1f))
            SectionColumn(Source.TF1, Sections.tf1, onSection, Modifier.weight(1f))
            SectionColumn(Source.M6, Sections.m6, onSection, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionColumn(source: Source, sections: List<Section>, onSection: (Section) -> Unit, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(source.label, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sections, key = { it.code }) { section ->
                Button(onClick = { onSection(section) }, modifier = Modifier.fillMaxWidth()) {
                    Text(section.label)
                }
            }
        }
    }
}
