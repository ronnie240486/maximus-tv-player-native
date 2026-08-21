package com.maximus.tvplayer

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PlaylistRepository(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val cacheFile = File(context.filesDir, "catalog-cache.tsv.gz")

    fun load(url: String, callback: (Result<CatalogSnapshot>) -> Unit) = load(listOf(url), callback)

    fun load(urls: List<String>, callback: (Result<CatalogSnapshot>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val parsed = urls.filter { it.isNotBlank() }.flatMap { fetchAndParse(it.trim()) }
                    .distinctBy { it.key }
                if (parsed.isEmpty()) error("Nenhum item encontrado na playlist")
                writeCache(parsed)
                CatalogSnapshot(parsed)
            }.recoverCatching {
                val cached = readCache()
                if (cached.isEmpty()) throw it
                CatalogSnapshot(cached, loadedFromCache = true)
            }
            callback(result)
        }
    }

    fun loadRemoteOnly(urls: List<String>, callback: (Result<CatalogSnapshot>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val parsed = urls.filter { it.isNotBlank() }.flatMap { fetchAndParse(it.trim()) }
                    .distinctBy { it.key }
                if (parsed.isEmpty()) error("A lista do painel está vazia ou indisponível")
                writeCache(parsed)
                CatalogSnapshot(parsed)
            })
        }
    }

    fun loadCached(callback: (CatalogSnapshot?) -> Unit) {
        executor.execute {
            callback(runCatching { readCache() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { CatalogSnapshot(it, true) })
        }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun fetchAndParse(urlString: String): List<CatalogEntry> {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            setRequestProperty("User-Agent", "MaximusTVPlayer/1.0 AndroidTV")
        }
        connection.connect()
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val content = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("A lista do painel recusou a conexão (HTTP $status)")
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<html", true) || trimmed.startsWith("<!doctype", true)) {
            error("A lista do painel devolveu uma página HTML/bloqueio, não uma M3U válida")
        }
        return parseM3u(content)
    }

    private fun parseM3u(content: String): List<CatalogEntry> {
        val entries = ArrayList<CatalogEntry>()
        val normalized = content.replace("\r", "").replace(Regex("\\s+(?=#EXTINF:)"), "\n")
        var pendingInfo: String? = null
        normalized.lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF:")) {
                val sameLineUrl = Regex("\\s+(https?://\\S+)$").find(line)
                if (sameLineUrl != null) {
                    addEntry(entries, line.substring(0, sameLineUrl.range.first).trim(), sameLineUrl.groupValues[1])
                    pendingInfo = null
                } else {
                    pendingInfo = line
                }
            } else if (pendingInfo != null && line.startsWith("http")) {
                addEntry(entries, pendingInfo!!, line)
                pendingInfo = null
            }
        }
        return entries
    }

    private fun addEntry(target: MutableList<CatalogEntry>, info: String, streamUrl: String) {
        val attributes = Regex("([\\w-]+)=\"([^\"]*)\"").findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
        val displayName = info.substringAfter(',', attributes["tvg-name"].orEmpty()).trim()
        if (displayName.isBlank() || streamUrl.isBlank()) return
        val group = attributes["group-title"].orEmpty().ifBlank { "Sem categoria" }
        val kind = classify(displayName, group)
        val quality = Regex("\\b(4K|UHD|FHD|HD|SD)\\b", RegexOption.IGNORE_CASE).find(displayName)?.value?.uppercase().orEmpty()
        target += CatalogEntry(
            key = "${attributes["tvg-id"].orEmpty()}|$streamUrl",
            name = displayName,
            groupTitle = group,
            tvgId = attributes["tvg-id"].orEmpty(),
            logoUrl = attributes["tvg-logo"].orEmpty(),
            streamUrl = streamUrl,
            kind = kind,
            quality = quality,
            seriesGroup = if (kind == MediaKind.SERIES) displayName.split(Regex("\\sS\\d+|\\sTemporada")).first() else "",
        )
    }

    private fun classify(name: String, group: String): MediaKind {
        val normalizedGroup = group.lowercase().trim()
        val normalizedName = name.lowercase()
        if (normalizedGroup.startsWith("filmes |")) return MediaKind.MOVIE
        if (normalizedGroup.startsWith("series |")) return MediaKind.SERIES
        if (normalizedGroup.contains("24/7 filmes") || normalizedGroup.contains("24/7 seriados") || normalizedGroup.contains("24/7 doramas") || normalizedGroup.contains("24/7 animes") || normalizedGroup.contains("24/7 novelas")) return MediaKind.LIVE
        if (normalizedGroup == "filmes e séries" || normalizedGroup == "filmes e series") return MediaKind.LIVE
        if (normalizedName.contains("temporada") || Regex("\\bs\\d{1,2}\\b").containsMatchIn(normalizedName)) return MediaKind.SERIES
        if (normalizedName.contains("filme") || normalizedName.contains("movie")) return MediaKind.MOVIE
        return MediaKind.LIVE
    }

    private fun writeCache(entries: List<CatalogEntry>) {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).bufferedWriter(Charsets.UTF_8).use { writer ->
            entries.forEach { entry ->
                val fields = listOf(entry.key, entry.name, entry.groupTitle, entry.tvgId, entry.logoUrl, entry.streamUrl, entry.kind.name, entry.quality, entry.seriesGroup)
                writer.append(fields.joinToString("\t") { encode(it) }).append('\n')
            }
        }
        cacheFile.writeBytes(buffer.toByteArray())
    }

    private fun readCache(): List<CatalogEntry> {
        if (!cacheFile.exists()) return emptyList()
        val bytes = GZIPInputStream(ByteArrayInputStream(cacheFile.readBytes())).bufferedReader(Charsets.UTF_8).use { it.readLines() }
        return bytes.mapNotNull { line ->
            val f = line.split('\t').map(::decode)
            if (f.size < 9) return@mapNotNull null
            runCatching {
                CatalogEntry(f[0], f[1], f[2], f[3], f[4], f[5], MediaKind.valueOf(f[6]), f[7], f[8])
            }.getOrNull()
        }
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
}
