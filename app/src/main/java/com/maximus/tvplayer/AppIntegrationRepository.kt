package com.maximus.tvplayer

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val RENCIA_BASE_URL = "https://renciaapp.manus.space"

data class RemoteAppConfig(
    val registered: Boolean,
    val allowed: Boolean,
    val mac: String,
    val appId: String,
    val appName: String,
    val logoUrl: String,
    val bannerUrl: String,
    val backgroundUrl: String,
    val messageTitle: String,
    val messageText: String,
    val messageImageUrl: String,
    val iconLiveTv: String,
    val iconMovies: String,
    val iconSeries: String,
    val serverApiUrl: String,
    val playlistUrls: List<String>,
    val apkDownloadUrl: String,
    val apkVersion: String,
)

data class RemoteNotification(val id: Long, val title: String, val message: String)
data class RemoteCommand(val id: Long, val command: String, val payload: JSONObject)
data class UpdateInfo(val available: Boolean, val version: String, val url: String)

class AppIntegrationRepository {
    private val executor = Executors.newFixedThreadPool(3)
    private val heartbeat: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var syncFuture: ScheduledFuture<*>? = null
    private val main = Handler(Looper.getMainLooper())

    fun fetchConfig(mac: String, callback: (Result<RemoteAppConfig>) -> Unit) {
        getAsync("/api/v5/apps/evolux/config?mac=${encode(mac)}") { result ->
            callback(result.map { parseConfig(it, mac) })
        }
    }

    fun checkDevice(mac: String, callback: (Result<JSONObject>) -> Unit) {
        getAsync("/api/device/check?mac=${encode(mac)}", callback)
    }

    fun fetchNotifications(mac: String, callback: (Result<List<RemoteNotification>>) -> Unit) {
        getAsync("/api/v5/list-notifications?mac=${encode(mac)}") { result ->
            callback(result.map { json ->
                val array = json.optJSONArray("notifications") ?: json.optJSONArray("data") ?: JSONArray()
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(RemoteNotification(item.optLong("id"), item.optString("title"), item.optString("message")))
                    }
                }
            })
        }
    }

    fun fetchRemoteCommands(mac: String, callback: (Result<List<RemoteCommand>>) -> Unit) {
        getAsync("/api/v5/remote-commands?mac=${encode(mac)}") { result ->
            callback(result.map { json ->
                val array = json.optJSONArray("commands") ?: json.optJSONArray("data") ?: JSONArray()
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(RemoteCommand(item.optLong("id"), item.optString("command"), item.optJSONObject("payload") ?: JSONObject()))
                    }
                }
            })
        }
    }

    fun ackNotification(mac: String, alertId: Long) {
        postAsync("/api/v5/list-notifications/ack", JSONObject().apply {
            put("mac", mac)
            put("alert_id", alertId)
        }) { }
    }

    fun ackCommand(mac: String, commandId: Long, status: String, message: String = "") {
        postAsync("/api/v5/remote-commands/ack", JSONObject().apply {
            put("mac", mac)
            put("command_id", commandId)
            put("status", status)
            put("result_message", message)
        }) { }
    }

    fun reportPlaybackFailure(mac: String, activeListNumber: Int? = null) {
        postAsync("/api/v5/playback-failure", JSONObject().apply {
            put("mac", mac)
            activeListNumber?.let { put("active_list_number", it) }
        }) { }
    }

    fun sendHeartbeat(mac: String, currentContent: String? = null) {
        val path = buildString {
            append("/api/v5/heartbeat?mac=")
            append(encode(mac))
            if (!currentContent.isNullOrBlank()) {
                append("&current_content=")
                append(encode(currentContent))
            }
        }
        getAsync(path) { }
    }

    fun startHeartbeat(mac: String, currentContent: () -> String?) {
        stopHeartbeat()
        sendHeartbeat(mac, currentContent())
        syncFuture = heartbeat.scheduleAtFixedRate({ sendHeartbeat(mac, currentContent()) }, 60, 60, TimeUnit.SECONDS)
    }

    fun startBackgroundSync(
        mac: String,
        currentContent: () -> String?,
        onNotifications: (List<RemoteNotification>) -> Unit,
        onCommands: (List<RemoteCommand>) -> Unit,
    ) {
        stopHeartbeat()
        sendHeartbeat(mac, currentContent())
        fetchNotifications(mac) { it.onSuccess(onNotifications) }
        fetchRemoteCommands(mac) { it.onSuccess(onCommands) }
        syncFuture = heartbeat.scheduleAtFixedRate({
            sendHeartbeat(mac, currentContent())
            fetchNotifications(mac) { it.onSuccess(onNotifications) }
            fetchRemoteCommands(mac) { it.onSuccess(onCommands) }
        }, 60, 60, TimeUnit.SECONDS)
    }

    fun stopHeartbeat() {
        syncFuture?.cancel(true)
        syncFuture = null
    }

    fun checkUpdate(mac: String, callback: (Result<UpdateInfo>) -> Unit) {
        getAsync("/api/v5/apps/evolux/update?mac=${encode(mac)}") { result ->
            callback(result.map { json ->
                UpdateInfo(
                    available = json.optBoolean("update_available", false),
                    version = json.optString("latest_version", json.optString("version")),
                    url = json.optString("apk_download_url", json.optString("apk_link", json.optString("url"))),
                )
            })
        }
    }

    fun shutdown() {
        stopHeartbeat()
        heartbeat.shutdownNow()
        executor.shutdownNow()
    }

    private fun parseConfig(json: JSONObject, fallbackMac: String): RemoteAppConfig {
        val root = json.optJSONObject("data") ?: json
        val icons = root.optJSONObject("icons") ?: JSONObject()
        val playlistArray = root.optJSONArray("playlist_urls") ?: JSONArray()
        val playlists = buildList {
            for (index in 0 until playlistArray.length()) {
                val value = playlistArray.optString(index).trim()
                if (value.startsWith("http", true)) add(value)
            }
        }
        return RemoteAppConfig(
            registered = root.optBoolean("registered", true),
            allowed = root.optBoolean("allowed", true),
            mac = root.optString("mac", fallbackMac),
            appId = root.optString("app_id", "evolux"),
            appName = root.optString("app_name", "Maximus TV Player"),
            logoUrl = root.optString("logo_url"),
            bannerUrl = root.optString("banner_url"),
            backgroundUrl = root.optString("background_url"),
            messageTitle = root.optString("message_title"),
            messageText = root.optString("message_text"),
            messageImageUrl = root.optString("message_image_url"),
            iconLiveTv = icons.optString("live_tv"),
            iconMovies = icons.optString("movies"),
            iconSeries = icons.optString("series"),
            serverApiUrl = root.optString("server_api_url"),
            playlistUrls = playlists,
            apkDownloadUrl = root.optString("apk_download_url"),
            apkVersion = root.optString("apk_version"),
        )
    }

    private fun getAsync(path: String, callback: (Result<JSONObject>) -> Unit) {
        executor.execute {
            callback(runCatching { request("GET", path, null) })
        }
    }

    private fun postAsync(path: String, body: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        executor.execute {
            callback(runCatching { request("POST", path, body) })
        }
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(RENCIA_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MaximusTVPlayer/1.0 AndroidTV")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty().trim()
        connection.disconnect()
        if (text.startsWith("<html", true) || text.startsWith("<!doctype", true)) error("Resposta HTML inválida da integração")
        if (status !in 200..299) error("Integração HTTP $status")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
