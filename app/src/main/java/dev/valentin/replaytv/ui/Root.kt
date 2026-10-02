package dev.valentin.replaytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.drm.DrmStream
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Channel
import dev.valentin.replaytv.model.Section
import dev.valentin.replaytv.player.PlayerActivity

sealed interface Screen {
    data object Home : Screen
    data class ChannelReplays(val channel: Channel) : Screen
    data object Themes : Screen
    data class Browse(val section: Section) : Screen
    data class Collection(val item: CatalogItem.Collection) : Screen
    data class Detail(val video: CatalogItem.Video) : Screen
    data object Downloads : Screen
    data object Accounts : Screen
}

/** Navigation minimale par pile d'écrans ; le bouton Retour de la télécommande dépile. */
@Composable
fun ReplayTvRoot(app: ReplayTvApp) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val current = backStack.last()

    BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun open(item: CatalogItem) = when (item) {
        is CatalogItem.Video -> push(Screen.Detail(item))
        is CatalogItem.Collection -> push(Screen.Collection(item))
    }

    fun play(uri: String, title: String, resumeKey: String) {
        context.startActivity(PlayerActivity.intent(context, uri, title, resumeKey))
    }

    fun playDrm(stream: DrmStream, title: String, resumeKey: String) {
        context.startActivity(PlayerActivity.intent(context, stream, title, resumeKey))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    ) {
        when (current) {
            Screen.Home -> HomeScreen(
                app = app,
                onChannel = { push(Screen.ChannelReplays(it)) },
                onThemes = { push(Screen.Themes) },
                onDownloads = { push(Screen.Downloads) },
                onAccounts = { push(Screen.Accounts) },
            )

            is Screen.ChannelReplays -> ChannelScreen(
                channel = current.channel,
                load = { app.catalog.channel(current.channel) },
                onOpen = ::open,
            )

            Screen.Themes -> ThemesScreen(onSection = { push(Screen.Browse(it)) })

            is Screen.Browse -> BrowseScreen(
                title = "${current.section.source.label} · ${current.section.label}",
                key = current.section,
                load = { app.catalog.section(current.section) },
                onOpen = ::open,
            )

            is Screen.Collection -> BrowseScreen(
                title = current.item.title,
                key = current.item,
                load = { app.catalog.collection(current.item) },
                onOpen = ::open,
            )

            is Screen.Detail ->
                if (current.video.source.drm) {
                    DrmDetailScreen(app = app, video = current.video, onPlay = ::playDrm, onAccounts = { push(Screen.Accounts) })
                } else {
                    DetailScreen(app = app, video = current.video, onPlay = ::play)
                }

            Screen.Downloads -> DownloadsScreen(app = app, onPlay = ::play)

            Screen.Accounts -> AccountsScreen(app = app)
        }
    }
}
