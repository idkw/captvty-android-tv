package dev.valentin.replaytv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    /** [collection] : la collection d'où vient la vidéo, pour proposer les épisodes suivants en fin de lecture. */
    data class Detail(val video: CatalogItem.Video, val autoplay: Boolean = false, val collection: CatalogItem.Collection? = null) : Screen
    data object Favorites : Screen
    data object InProgress : Screen
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

    fun open(item: CatalogItem, from: CatalogItem.Collection?) = when (item) {
        is CatalogItem.Video -> push(Screen.Detail(item, collection = from))
        is CatalogItem.Collection -> push(Screen.Collection(item))
    }

    // Épisode choisi sur l'écran de fin du lecteur : sa fiche remplace celle de l'épisode terminé
    // et la lecture démarre seule.
    val choice by app.upNext.choice.collectAsStateWithLifecycle()
    LaunchedEffect(choice) {
        val next = choice ?: return@LaunchedEffect
        app.upNext.consumeChoice()
        if (backStack.last() is Screen.Detail) backStack.removeAt(backStack.lastIndex)
        push(Screen.Detail(next.video, autoplay = true, collection = next.collection))
    }

    val watched by app.watched.urls.collectAsStateWithLifecycle()

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
        CompositionLocalProvider(LocalWatchedUrls provides watched) {
            Screens(app, current, ::push, ::open, ::play, ::playDrm)
        }
        UpdateDialog(app.updates)
        YtDlpRejectedDialog(app.ytDlp)
    }
}

@Composable
private fun Screens(
    app: ReplayTvApp,
    current: Screen,
    push: (Screen) -> Unit,
    open: (CatalogItem, CatalogItem.Collection?) -> Unit,
    play: (String, String, String) -> Unit,
    playDrm: (DrmStream, String, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (current) {
            Screen.Home -> HomeScreen(
                app = app,
                onChannel = { push(Screen.ChannelReplays(it)) },
                onThemes = { push(Screen.Themes) },
                onDownloads = { push(Screen.Downloads) },
                onAccounts = { push(Screen.Accounts) },
                onFavorites = { push(Screen.Favorites) },
                onInProgress = { push(Screen.InProgress) },
                onCheckUpdates = { app.updates.check(manual = true) },
            )

            is Screen.ChannelReplays -> ChannelScreen(
                channel = current.channel,
                load = { app.catalog.channel(current.channel) },
                onOpen = { open(it, null) },
            )

            Screen.Themes -> ThemesScreen(onSection = { push(Screen.Browse(it)) })

            is Screen.Browse -> BrowseScreen(
                title = "${current.section.source.label} · ${current.section.label}",
                key = current.section,
                load = { app.catalog.section(current.section) },
                onOpen = { open(it, null) },
            )

            is Screen.Collection -> {
                val favorites by app.library.favorites.collectAsStateWithLifecycle()
                BrowseScreen(
                    title = current.item.title,
                    key = current.item,
                    load = { app.catalog.collection(current.item).also { app.upNext.rememberCollection(current.item, it) } },
                    onOpen = { open(it, current.item) },
                    favorite = favorites.any { it.url == current.item.url },
                    onToggleFavorite = { app.library.toggleFavorite(current.item) },
                )
            }

            Screen.Favorites -> FavoritesScreen(app = app, onOpen = { open(it, null) })

            Screen.InProgress -> InProgressScreen(app = app, onResume = { push(Screen.Detail(it.video, autoplay = true, collection = it.collection)) })

            is Screen.Detail ->
                if (current.video.source.drm) {
                    DrmDetailScreen(app = app, video = current.video, collection = current.collection, onPlay = playDrm, onAccounts = { push(Screen.Accounts) }, autoplay = current.autoplay)
                } else {
                    DetailScreen(app = app, video = current.video, collection = current.collection, onPlay = play, autoplay = current.autoplay)
                }

            Screen.Downloads -> DownloadsScreen(app = app, onPlay = play)

            Screen.Accounts -> AccountsScreen(app = app)
        }
    }
}
