package com.maximus.tvplayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.util.Locale

class ActivationActivity : Activity() {
    private lateinit var mac: String
    private lateinit var status: TextView
    private lateinit var verifyButton: TextView
    private lateinit var connectButton: TextView
    private lateinit var connectionProgress: ProgressBar
    private lateinit var connectionPercent: TextView
    private lateinit var connectionClock: TextView
    private lateinit var connectionMessage: TextView
    private var checking = false
    private var loadingStartedAt = 0L
    private val integration = AppIntegrationRepository()
    private val playlistRepository by lazy { PlaylistRepository(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var loadingPanelList = false
    private val clockTicker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1_000)
        }
    }

    private val periodicCheck = object : Runnable {
        override fun run() {
            verifyAccess(false)
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        setContentView(R.layout.activity_activation)

        mac = DeviceIdentifier.resolve(this)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_MAC_ADDRESS, mac).apply()
        val macValue = findViewById<TextView>(R.id.macValue)
        val macFormatted = findViewById<TextView>(R.id.macFormatted)
        macValue.text = mac
        macFormatted.text = "12 caracteres hexadecimais • toque para copiar"
        status = findViewById(R.id.activationStatus)
        verifyButton = findViewById(R.id.recheckButton)
        connectButton = findViewById(R.id.connectButton)
        connectionProgress = findViewById(R.id.connectionProgress)
        connectionPercent = findViewById(R.id.connectionPercent)
        connectionClock = findViewById(R.id.connectionClock)
        connectionMessage = findViewById(R.id.connectionMessage)
        loadingStartedAt = SystemClock.elapsedRealtime()
        handler.post(clockTicker)
        macValue.setOnClickListener { copyMac() }
        macFormatted.setOnClickListener { copyMac() }
        findViewById<TextView>(R.id.copyMacButton).setOnClickListener { copyMac() }
        connectButton.setOnClickListener { verifyAccess(true) }
        verifyButton.setOnClickListener { verifyAccess(true) }
        connectButton.requestFocus()
        setConnectionProgress(0, "Aguardando conexão com o painel...")
        verifyAccess(false)
    }

    private fun updateClock() {
        if (!::connectionClock.isInitialized || loadingStartedAt == 0L) return
        val elapsed = ((SystemClock.elapsedRealtime() - loadingStartedAt) / 1000L).coerceAtLeast(0L)
        connectionClock.text = "◷ %02d:%02d".format(Locale.US, elapsed / 60, elapsed % 60)
    }

    private fun setConnectionProgress(value: Int, message: String) {
        if (!::connectionProgress.isInitialized) return
        connectionProgress.progress = value.coerceIn(0, 100)
        connectionPercent.text = "${value.coerceIn(0, 100)}%"
        connectionMessage.text = message
    }

    private fun explainFailure(reason: String): String {
        val safeReason = reason
            .replace(Regex("([?&](username|password)=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1***")
            .replace(Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE), "servidor da lista")
        return when {
            reason.contains("403") -> "A lista foi recusada pelo servidor (HTTP 403). Verifique a URL/credenciais no painel."
            reason.contains("timeout", true) || reason.contains("timed out", true) -> "O servidor da lista demorou demais para responder."
            reason.contains("HTML", true) -> "O servidor devolveu uma página de bloqueio, não uma lista M3U."
            safeReason.isNotBlank() -> "Falha ao baixar a lista do painel: $safeReason"
            else -> "Falha ao baixar a lista do painel."
        }
    }

    private fun copyMac() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MAC do dispositivo", mac))
        Toast.makeText(this, "MAC copiado", Toast.LENGTH_SHORT).show()
    }

    private fun verifyAccess(showProgress: Boolean) {
        if (checking || loadingPanelList) return
        checking = true
        if (showProgress) status.text = "Consultando o painel..."
        setConnectionProgress(10, "Conectando sua lista de filmes, séries e canais...")
        verifyButton.isEnabled = false
        connectButton.isEnabled = false
        integration.fetchConfig(mac) { result ->
            runOnUiThread {
                checking = false
                verifyButton.isEnabled = true
                connectButton.isEnabled = true
                result.onSuccess { config ->
                    if (!config.registered || !config.allowed) {
                        setConnectionProgress(20, "Aguardando o cadastro deste MAC no painel...")
                        status.text = "Aguardando cadastro e liberação no painel..."
                        status.setTextColor(getColor(R.color.warning))
                        return@onSuccess
                    }
                    if (config.playlistUrls.isEmpty()) {
                        setConnectionProgress(30, "MAC liberado. Aguardando a lista cadastrada no painel...")
                        status.text = "MAC liberado, aguardando a lista cadastrada no painel..."
                        status.setTextColor(getColor(R.color.warning))
                        return@onSuccess
                    }
                    loadingPanelList = true
                    setConnectionProgress(60, "Lista encontrada. Conectando sua lista de filmes, séries e canais...")
                    verifyButton.isEnabled = false
                    connectButton.isEnabled = false
                    status.text = "Lista do painel encontrada. Carregando canais, filmes e séries..."
                    playlistRepository.loadRemoteOnly(config.playlistUrls) { playlistResult ->
                        runOnUiThread {
                            loadingPanelList = false
                            playlistResult.onSuccess {
                                setConnectionProgress(100, "Conectado. Em breve você terá em mãos o melhor conteúdo para assistir.")
                                status.text = "Conectado. Abrindo catálogo..."
                                status.setTextColor(getColor(R.color.success))
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                    .putBoolean(PREF_ACCESS_ALLOWED, true)
                                    .apply()
                                handler.removeCallbacks(periodicCheck)
                                startActivity(Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                finish()
                            }.onFailure {
                                verifyButton.isEnabled = true
                                connectButton.isEnabled = true
                                val reason = it.message.orEmpty()
                                val message = explainFailure(reason)
                                setConnectionProgress(40, message)
                                status.text = message
                                status.setTextColor(getColor(R.color.warning))
                            }
                        }
                    }
                }.onFailure {
                    setConnectionProgress(20, "O painel não respondeu. Tentando novamente automaticamente...")
                    status.text = "Não foi possível consultar o painel. Toque em CONECTAR para tentar novamente."
                    status.setTextColor(getColor(R.color.warning))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(periodicCheck)
        handler.postDelayed(periodicCheck, 5_000)
    }

    override fun onPause() {
        handler.removeCallbacks(periodicCheck)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(clockTicker)
        playlistRepository.shutdown()
        integration.shutdown()
        super.onDestroy()
    }

    companion object {
        const val PREFS_NAME = "maximus_device_preferences"
        const val PREF_MAC_ADDRESS = "mac_address"
        private const val PREF_ACCESS_ALLOWED = "access_allowed"
    }
}

object DeviceIdentifier {
    fun resolve(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray(Charsets.UTF_8))
        val compact = digest.take(6).joinToString("") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        return format(compact)
    }

    fun format(compact: String): String = compact.replace(":", "").chunked(2).joinToString(":")
}
