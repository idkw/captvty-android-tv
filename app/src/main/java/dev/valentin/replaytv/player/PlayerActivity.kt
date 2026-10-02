package dev.valentin.replaytv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.valentin.replaytv.ReplayTvApp

/** Lecteur plein écran (Media3 / ExoPlayer) pour un flux HLS distant ou un MP4 local. */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)
        if (uri == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

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
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        player = exoPlayer

        val view = PlayerView(this).apply {
            this.player = exoPlayer
            useController = true
            controllerShowTimeoutMs = 4000
            setShowNextButton(false)
            setShowPreviousButton(false)
            keepScreenOn = true
        }
        playerView = view
        setContentView(view)
    }

    // Les touches de la télécommande (lecture/pause, flèches) doivent atteindre la barre de contrôle.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        playerView?.dispatchKeyEvent(event) == true || super.dispatchKeyEvent(event)

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, uri: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITLE, title)
    }
}
