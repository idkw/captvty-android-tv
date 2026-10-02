package dev.valentin.replaytv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.valentin.replaytv.ReplayTvApp
import dev.valentin.replaytv.ui.theme.ReplayTvTheme

/**
 * Lecteur plein écran (Media3 / ExoPlayer) pour un flux HLS distant ou un MP4 local, avec une
 * surcouche de contrôle pensée pour la télécommande :
 * - Retour masque d'abord la surcouche, et ne quitte la vidéo que si elle est déjà masquée ;
 * - gauche/droite déplacent la tête de lecture par pas de 10 s, accélérés si la touche reste enfoncée ;
 * - la position est mémorisée pour reprendre la lecture au retour sur la vidéo.
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var positions: PlaybackPositions
    private val ui = PlayerUiState()
    private var resumeKey = ""
    private var seekHoldStartedAt = 0L
    private var lastSeekStepAt = 0L

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

        val exoPlayer = buildPlayer(uri, title)
        val saved = positions.get(resumeKey)
        if (saved > 0) {
            exoPlayer.seekTo(saved)
            ui.resumedFromMs = saved
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        player = exoPlayer

        // Retour (touche ou geste prédictif, Android 16 passe par ce dispatcher) : masque d'abord la
        // surcouche ; ne quitte la vidéo que si elle est déjà masquée, ou si la lecture est finie/en erreur.
        onBackPressedDispatcher.addCallback(this) {
            if (ui.controlsVisible && !ui.ended && ui.error == null) ui.controlsVisible = false else finish()
        }

        setContent {
            ReplayTvTheme {
                PlayerScreen(player = exoPlayer, ui = ui, title = title, onTick = ::savePosition)
            }
        }
    }

    private fun buildPlayer(uri: String, title: String): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ReplayTvApp.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .build()
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build(),
        )
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.isPlaying = isPlaying
                if (!isPlaying) ui.showControls()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui.isBuffering = playbackState == Player.STATE_BUFFERING
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

        return when (event.keyCode) {
            in SEEK_BACK_KEYS -> { stepSeek(exoPlayer, -1, event); true }
            in SEEK_FORWARD_KEYS -> { stepSeek(exoPlayer, +1, event); true }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (ui.seekTargetMs != null) commitSeek(exoPlayer) else togglePlayPause(exoPlayer)
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> { exoPlayer.play(); ui.showControls(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { exoPlayer.pause(); ui.showControls(); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { ui.showControls(); true }
            else -> super.dispatchKeyEvent(event)
        }
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
        val heldSeconds = ((now - seekHoldStartedAt) / 1000).coerceIn(0, 5)
        val step = SEEK_STEP_MS * (1 + heldSeconds)
        val base = ui.seekTargetMs ?: exoPlayer.currentPosition
        val max = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        ui.seekTargetMs = (base + direction * step).coerceIn(0L, max)
        ui.showControls()
    }

    private fun commitSeek(exoPlayer: ExoPlayer) {
        val target = ui.seekTargetMs ?: return
        exoPlayer.seekTo(target)
        if (ui.ended) ui.ended = false
        ui.seekTargetMs = null
        ui.showControls()
    }

    private fun savePosition() {
        val exoPlayer = player ?: return
        if (ui.ended) return
        positions.save(resumeKey, exoPlayer.currentPosition, exoPlayer.duration)
    }

    override fun onStop() {
        savePosition()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RESUME_KEY = "resumeKey"
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
    }
}
