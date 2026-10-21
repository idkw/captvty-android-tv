package dev.valentin.replaytv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.media3.session.MediaSession
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.drm.DrmStream
import dev.valentin.replaytv.ui.theme.ReplayTvTheme

/**
 * Lecteur plein écran (Media3 / ExoPlayer) pour un flux HLS distant, un flux DASH chiffré en Widevine
 * (TF1+, M6+) ou un MP4 local, avec une surcouche de contrôle pensée pour la télécommande :
 * - Retour masque d'abord la surcouche, et ne quitte la vidéo que si elle est déjà masquée ;
 * - gauche/droite déplacent la tête de lecture par pas de 10 s, accélérés si la touche reste enfoncée ;
 * - la position est mémorisée pour reprendre la lecture au retour sur la vidéo.
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var positions: PlaybackPositions
    private lateinit var prefs: PlayerPrefs
    private val ui = PlayerUiState()
    private var resumeKey = ""
    private var seekHoldStartedAt = 0L
    private var lastSeekStepAt = 0L
    private var resumeAfterScrub = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)
        if (uri == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        resumeKey = intent.getStringExtra(EXTRA_RESUME_KEY) ?: uri
        positions = PlaybackPositions(this)
        prefs = PlayerPrefs(this)
        ui.subtitleSizeIndex = prefs.subtitleSize

        val exoPlayer = buildPlayer(uri, title, drmFrom(intent))
        val saved = positions.get(resumeKey)
        if (saved > 0) {
            exoPlayer.seekTo(saved)
            ui.resumedFromMs = saved
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        player = exoPlayer
        // Session média : Google TV sait qu'une lecture est en cours (et les touches média du système arrivent ici).
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        // Retour (touche ou geste prédictif, Android 16 passe par ce dispatcher) : masque d'abord la
        // surcouche ; ne quitte la vidéo que si elle est déjà masquée, ou si la lecture est finie/en erreur.
        onBackPressedDispatcher.addCallback(this) {
            when {
                ui.confirmRestart -> ui.confirmRestart = false
                ui.menuOpen -> ui.menuOpen = false
                ui.controlsVisible && !ui.ended && ui.error == null -> ui.controlsVisible = false
                else -> finish()
            }
        }

        setContent {
            ReplayTvTheme {
                PlayerScreen(player = exoPlayer, ui = ui, title = title, onTick = ::savePosition)
            }
        }
    }

    private fun buildPlayer(uri: String, title: String, drm: DrmStream?): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ReplayTvApp.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .build()
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        if (drm != null) {
            // L'URL du manifeste TF1 ne finit pas par .mpd : le type doit être donné explicitement.
            item.setMimeType(MimeTypes.APPLICATION_MPD)
            item.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drm.licenseUrl)
                    .setLicenseRequestHeaders(drm.licenseHeaders)
                    .build(),
            )
        }
        exoPlayer.setMediaItem(item.build())
        // Sous-titres désactivés par défaut ; sinon la langue choisie la dernière fois.
        val wanted = prefs.subtitleLanguage
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, wanted == null)
            .setPreferredTextLanguage(wanted)
            .build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                ui.cues = cueGroup.cues.mapNotNull { it.text?.toString()?.trim()?.takeIf { t -> t.isNotEmpty() } }
            }

            override fun onTracksChanged(tracks: Tracks) {
                rebuildMenu(exoPlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.isPlaying = isPlaying
                updateKeepScreenOn(exoPlayer)
                if (!isPlaying) ui.showControls()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui.isBuffering = playbackState == Player.STATE_BUFFERING
                updateKeepScreenOn(exoPlayer)
                if (playbackState == Player.STATE_ENDED) {
                    ui.ended = true
                    positions.clear(resumeKey)
                    ui.showControls()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                ui.error = error.errorCodeName
                ui.showControls()
            }
        })
        return exoPlayer
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val exoPlayer = player ?: return super.dispatchKeyEvent(event)
        val isSeekKey = event.keyCode in SEEK_BACK_KEYS || event.keyCode in SEEK_FORWARD_KEYS

        if (event.action == KeyEvent.ACTION_UP && isSeekKey) {
            commitSeek(exoPlayer)
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        if (ui.confirmRestart) return handleConfirmKey(exoPlayer, event)
        if (ui.menuOpen) return handleMenuKey(exoPlayer, event)

        return when (event.keyCode) {
            in SEEK_BACK_KEYS -> { stepSeek(exoPlayer, -1, event); true }
            in SEEK_FORWARD_KEYS -> { stepSeek(exoPlayer, +1, event); true }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (ui.seekTargetMs != null) commitSeek(exoPlayer) else togglePlayPause(exoPlayer)
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> { exoPlayer.play(); ui.showControls(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { exoPlayer.pause(); ui.showControls(); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> { openMenu(exoPlayer); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { ui.showControls(); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun openMenu(exoPlayer: ExoPlayer) {
        rebuildMenu(exoPlayer)
        if (ui.menuItems.isEmpty()) return
        // Focus sur le choix en cours, sinon sur la première ligne utilisable (jamais sur une information).
        ui.menuIndex = ui.menuItems.indexOfFirst { it.selected }.takeIf { it >= 0 }
            ?: ui.menuItems.indexOfFirst { it.kind != MenuItem.Kind.INFO }.coerceAtLeast(0)
        ui.menuOpen = true
        ui.showControls()
    }

    /** Confirmation du retour au début : gauche/droite choisit, OK valide, Retour annule. */
    private fun handleConfirmKey(exoPlayer: ExoPlayer, event: KeyEvent): Boolean {
        ui.showControls()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> ui.confirmIndex = 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> ui.confirmIndex = 1
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (ui.confirmIndex == 1) {
                    restartFromBeginning(exoPlayer)
                    ui.menuOpen = false
                }
                ui.confirmRestart = false
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    private fun restartFromBeginning(exoPlayer: ExoPlayer) {
        stopScrubbing(exoPlayer)
        exoPlayer.seekTo(0)
        ui.seekTargetMs = null
        ui.resumedFromMs = 0
        ui.ended = false
        positions.clear(resumeKey)
        exoPlayer.play()
    }

    private fun handleMenuKey(exoPlayer: ExoPlayer, event: KeyEvent): Boolean {
        ui.showControls()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> ui.menuIndex = nextUsable(ui.menuIndex, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> ui.menuIndex = nextUsable(ui.menuIndex, +1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val item = ui.menuItems.getOrNull(ui.menuIndex)
                if (item != null && item.kind != MenuItem.Kind.INFO) item.action()
                if (!ui.confirmRestart) rebuildMenu(exoPlayer)
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> ui.menuOpen = false
            KeyEvent.KEYCODE_BACK -> return super.dispatchKeyEvent(event)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    /** Ligne suivante dans [direction] en sautant les lignes d'information ; reste en place au bord. */
    private fun nextUsable(from: Int, direction: Int): Int {
        var index = from + direction
        while (index in ui.menuItems.indices) {
            if (ui.menuItems[index].kind != MenuItem.Kind.INFO) return index
            index += direction
        }
        return from
    }

    /** Construit le menu : retour au début, puis sous-titres, taille et versions audio. */
    private fun rebuildMenu(exoPlayer: ExoPlayer) {
        val tracks = exoPlayer.currentTracks
        val params = exoPlayer.trackSelectionParameters
        // En tête, toujours visible ; une confirmation suit : un appui sur OK par mégarde ne fait pas
        // perdre la position.
        val items = mutableListOf(
            MenuItem(SECTION_PLAYBACK, "Revenir au début de l'épisode", selected = false, kind = MenuItem.Kind.ACTION) {
                ui.confirmIndex = 0
                ui.confirmRestart = true
            },
        )

        val textGroups = tracks.groupsOfType(C.TRACK_TYPE_TEXT)
        if (textGroups.isNotEmpty()) {
            val textDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            items += MenuItem(SECTION_SUBTITLES, "Désactivés", textDisabled || textGroups.none { it.isSelected }) {
                exoPlayer.trackSelectionParameters = params.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                prefs.subtitleLanguage = null
                ui.cues = emptyList()
            }
            textGroups.forEachIndexed { index, group ->
                val format = group.getTrackFormat(0)
                items += MenuItem(SECTION_SUBTITLES, trackLabel(format, "Piste ${index + 1}"), !textDisabled && group.isSelected) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                        .build()
                    prefs.subtitleLanguage = format.language ?: "und"
                }
            }
            PlayerPrefs.SUBTITLE_SIZES.forEachIndexed { index, label ->
                items += MenuItem(SECTION_SUBTITLE_SIZE, label, ui.subtitleSizeIndex == index) {
                    ui.subtitleSizeIndex = index
                    prefs.subtitleSize = index
                }
            }
        } else {
            items += MenuItem(SECTION_SUBTITLES, "Aucun sous-titre pour cette vidéo", selected = false, kind = MenuItem.Kind.INFO) {}
        }

        val audioGroups = tracks.groupsOfType(C.TRACK_TYPE_AUDIO)
        if (audioGroups.size > 1) {
            audioGroups.forEachIndexed { index, group ->
                val format = group.getTrackFormat(0)
                items += MenuItem(SECTION_AUDIO, trackLabel(format, "Version ${index + 1}"), group.isSelected) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                        .build()
                }
            }
        }

        ui.menuItems = items
        if (ui.menuIndex > items.lastIndex) ui.menuIndex = items.lastIndex.coerceAtLeast(0)
    }

    /**
     * Google TV passe en mode Ambiant après 10 minutes sans touche de la télécommande, sauf si la
     * fenêtre demande à garder l'écran allumé. On le demande pendant la lecture et le chargement
     * seulement : en pause ou à la fin, l'écran de veille doit pouvoir démarrer (recommandation Google).
     */
    private fun updateKeepScreenOn(exoPlayer: ExoPlayer) {
        val active = exoPlayer.playWhenReady &&
            (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING)
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun togglePlayPause(exoPlayer: ExoPlayer) {
        if (ui.ended) {
            exoPlayer.seekTo(0)
            ui.ended = false
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        ui.showControls()
    }

    /**
     * Un appui = 10 s. Touche maintenue : les événements répétés arrivent toutes les ~50 ms, on
     * n'en garde qu'un toutes les 150 ms et le pas grandit de 10 s par seconde d'appui, jusqu'à 60 s.
     */
    private fun stepSeek(exoPlayer: ExoPlayer, direction: Int, event: KeyEvent) {
        val now = SystemClock.uptimeMillis()
        if (event.repeatCount == 0) {
            seekHoldStartedAt = now
        } else if (now - lastSeekStepAt < SEEK_REPEAT_INTERVAL_MS) {
            return
        }
        lastSeekStepAt = now
        if (ui.seekTargetMs == null) startScrubbing(exoPlayer)
        val heldSeconds = ((now - seekHoldStartedAt) / 1000).coerceIn(0, 5)
        val step = SEEK_STEP_MS * (1 + heldSeconds)
        val base = ui.seekTargetMs ?: exoPlayer.currentPosition
        val max = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (base + direction * step).coerceIn(0L, max)
        ui.seekTargetMs = target
        // Comme sur Netflix : l'image à la position visée s'affiche pendant le déplacement.
        exoPlayer.seekTo(target)
        ui.showControls()
    }

    /**
     * Début d'un déplacement : la lecture se met en pause et le lecteur passe en mode « scrubbing »
     * (Media3), optimisé pour des sauts rapprochés qui n'affichent que l'image visée.
     */
    private fun startScrubbing(exoPlayer: ExoPlayer) {
        ui.seekFromMs = exoPlayer.currentPosition
        resumeAfterScrub = exoPlayer.playWhenReady
        exoPlayer.pause()
        exoPlayer.setScrubbingModeEnabled(true)
    }

    private fun stopScrubbing(exoPlayer: ExoPlayer) {
        if (!exoPlayer.isScrubbingModeEnabled) return
        exoPlayer.setScrubbingModeEnabled(false)
        ui.seekFromMs = null
        if (resumeAfterScrub) exoPlayer.play()
    }

    private fun commitSeek(exoPlayer: ExoPlayer) {
        val target = ui.seekTargetMs ?: return
        exoPlayer.seekTo(target)
        if (ui.ended) ui.ended = false
        ui.seekTargetMs = null
        stopScrubbing(exoPlayer)
        ui.showControls()
    }

    private fun savePosition() {
        val exoPlayer = player ?: return
        if (ui.ended) return
        positions.save(resumeKey, exoPlayer.currentPosition, exoPlayer.duration)
    }

    override fun onStop() {
        player?.let { exo ->
            ui.seekTargetMs = null
            if (exo.isScrubbingModeEnabled) {
                exo.setScrubbingModeEnabled(false)
                ui.seekFromMs = null
            }
        }
        savePosition()
        player?.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RESUME_KEY = "resumeKey"
        private const val EXTRA_LICENSE_URL = "licenseUrl"
        private const val EXTRA_LICENSE_HEADERS = "licenseHeaders"
        private const val SEEK_STEP_MS = 10_000L
        private const val SEEK_REPEAT_INTERVAL_MS = 150L
        private val SEEK_BACK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND)
        private val SEEK_FORWARD_KEYS = setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

        /** [resumeKey] identifie la vidéo pour la reprise : l'URL de sa page, commune au flux et au fichier. */
        fun intent(context: Context, uri: String, title: String, resumeKey: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_RESUME_KEY, resumeKey)

        fun intent(context: Context, stream: DrmStream, title: String, resumeKey: String): Intent =
            intent(context, stream.manifestUrl, title, resumeKey)
                .putExtra(EXTRA_LICENSE_URL, stream.licenseUrl)
                .putExtra(EXTRA_LICENSE_HEADERS, HashMap(stream.licenseHeaders))

        private fun drmFrom(intent: Intent): DrmStream? {
            val licenseUrl = intent.getStringExtra(EXTRA_LICENSE_URL) ?: return null
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val headers = intent.getSerializableExtra(EXTRA_LICENSE_HEADERS) as? HashMap<String, String>
            return DrmStream(intent.getStringExtra(EXTRA_URI).orEmpty(), licenseUrl, headers.orEmpty())
        }
    }
}
