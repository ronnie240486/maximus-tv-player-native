package com.maximus.tvplayer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {
    private enum class ScaleMode(val label: String) { NORMAL("MODO: NORMAL"), STRETCH("MODO: ESTICAR"), ZOOM("MODO: ZOOM") }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var errorView: TextView
    private lateinit var scaleButton: TextView
    private var scaleMode = ScaleMode.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        errorView = findViewById(R.id.playerError)
        scaleButton = findViewById(R.id.playerScaleButton)
        scaleButton.background = rounded(0xCC101827.toInt(), 10f)
        scaleButton.setOnClickListener {
            scaleMode = when (scaleMode) {
                ScaleMode.NORMAL -> ScaleMode.STRETCH
                ScaleMode.STRETCH -> ScaleMode.ZOOM
                ScaleMode.ZOOM -> ScaleMode.NORMAL
            }
            applyScale()
        }
        scaleButton.setOnFocusChangeListener { view, hasFocus ->
            view.background = rounded(if (hasFocus) 0xFF4CE8F0.toInt() else 0xCC101827.toInt(), 10f)
            (view as TextView).setTextColor(if (hasFocus) Color.rgb(5, 6, 10) else Color.WHITE)
        }
        applyScale()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val streamUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val mac = intent.getStringExtra(EXTRA_MAC).orEmpty()
        findViewById<TextView>(R.id.playerTitle).text = "EXCELLENCE  •  $title"
        if (streamUrl.isBlank()) {
            showError("URL de reprodução não disponível")
            return
        }
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (mac.isNotBlank()) {
                        val integration = AppIntegrationRepository()
                        integration.reportPlaybackFailure(mac) { integration.shutdown() }
                    }
                    showError("Não foi possível reproduzir este conteúdo")
                }
            })
            exo.setMediaItem(MediaItem.fromUri(streamUrl))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun applyScale() {
        scaleButton.text = scaleMode.label
        playerView.resizeMode = when (scaleMode) {
            ScaleMode.NORMAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            ScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            ScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_TITLE = "player_title"
        const val EXTRA_URL = "player_url"
        const val EXTRA_MAC = "player_mac"
    }
}
