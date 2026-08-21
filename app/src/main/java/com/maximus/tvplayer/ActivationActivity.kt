package com.maximus.tvplayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.util.Locale

class ActivationActivity : Activity() {
    private lateinit var mac: String
    private lateinit var status: TextView
    private lateinit var verifyButton: TextView
    private lateinit var connectButton: TextView
    private var checking = false
    private val integration = AppIntegrationRepository()
    private val handler = Handler(Looper.getMainLooper())
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
        macValue.setOnClickListener { copyMac() }
        macFormatted.setOnClickListener { copyMac() }
        findViewById<TextView>(R.id.copyMacButton).setOnClickListener { copyMac() }
        connectButton.setOnClickListener { verifyAccess(true) }
        verifyButton.setOnClickListener { verifyAccess(true) }
        connectButton.requestFocus()
        verifyAccess(false)
    }

    private fun copyMac() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MAC do dispositivo", mac))
        Toast.makeText(this, "MAC copiado", Toast.LENGTH_SHORT).show()
    }

    private fun verifyAccess(showProgress: Boolean) {
        if (checking) return
        checking = true
        if (showProgress) status.text = "Consultando o painel..."
        verifyButton.isEnabled = false
        connectButton.isEnabled = false
        integration.fetchConfig(mac) { result ->
            runOnUiThread {
                checking = false
                verifyButton.isEnabled = true
                connectButton.isEnabled = true
                result.onSuccess { config ->
                    if (!config.registered || !config.allowed) {
                        status.text = "Aguardando cadastro e liberação no painel..."
                        status.setTextColor(getColor(R.color.warning))
                        return@onSuccess
                    }
                    if (config.playlistUrls.isEmpty()) {
                        status.text = "MAC liberado, aguardando lista cadastrada..."
                        status.setTextColor(getColor(R.color.warning))
                        return@onSuccess
                    }
                    status.text = "Dispositivo liberado. Abrindo catálogo..."
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
                    status.text = "Painel indisponível. Toque em VERIFICAR para tentar novamente."
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
