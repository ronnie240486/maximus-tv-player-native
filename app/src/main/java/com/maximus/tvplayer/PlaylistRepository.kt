package com.maximus.tvplayer

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PlaylistRepository(private val context: Context) {
    companion object {
        private const val MAX_FRAGMENT_CHARS = 1_048_576
        private val SAME_LINE_URL_PATTERN = Regex("\\s+(https?://\\S+)$")
        private val ATTRIBUTE_PATTERN = Regex("([\\w-]+)=\\\"([^\\\"]*)\\\"")
        private val QUALITY_PATTERN = Regex("\\b(4K|UHD|FHD|HD|SD)\\b", RegexOption.IGNORE_CASE)
        private val SERIES_PATTERN = Regex("\\sS\\d+|\\sTemporada", RegexOption.IGNORE_CASE)
        private val SEASON_NAME_PATTERN = Regex("\\bs\\d{1,2}\\b", RegexOption.IGNORE_CASE)
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val cacheFile = File(context.filesDir, "catalog-cache.tsv.gz")
    private val metadata = context.getSharedPreferences("playlist_cache_metadata", Context.MODE_PRIVATE)

    fun load(url: String, callback: (Result<CatalogSnapshot>) -> Unit) = load(listOf(url), callback)

    fun load(urls: List<String>, callback: (Result<CatalogSnapshot>) -> Unit) {
        executor.execute {
            val result = runCatching {
                downloadAndCache(urls)
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
            callback(runCatching { downloadAndCache(urls) })
        }
    }

    fun loadIfChanged(urls: List<String>, callback: (Result<CatalogSnapshot>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val normalized = normalizeUrls(urls)
                val cached = readCache()
                if (cached.isNotEmpty() && !sourceChanged(normalized)) {
                    CatalogSnapshot(cached, loadedFromCache = true)
                } else {
                    downloadAndCache(normalized)
                }
            })
        }
    }

    private fun fetchUrls(urls: List<String>): List<CatalogEntry> {
        val unique = LinkedHashMap<String, CatalogEntry>()
        urls.filter { it.isNotBlank() }.forEach { url ->
            fetchAndParse(url.trim()).forEach { entry -> unique.putIfAbsent(entry.key, entry) }
        }
        return unique.values.toList()
    }

    fun loadCached(callback: (CatalogSnapshot?) -> Unit) {
        executor.execute {
            callback(runCatching { readCache() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { CatalogSnapshot(it, true) })
        }
    }

    fun clearCache() {
        cacheFile.delete()
        metadata.edit().clear().apply()
    }

    private fun downloadAndCache(urls: List<String>): CatalogSnapshot {
        val normalized = normalizeUrls(urls)
        val parsed = fetchUrls(normalized)
        if (parsed.isEmpty()) error("A lista do painel está vazia ou indisponível")
        writeCache(parsed)
        saveSourceMetadata(normalized)
        return CatalogSnapshot(parsed)
    }

    private fun normalizeUrls(urls: List<String>): List<String> = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun sourceChanged(urls: List<String>): Boolean {
        if (metadata.getInt("format_version", 0) != 2) return true
        val savedUrls = metadata.getString("urls", "").orEmpty().split('\n').filter { it.isNotBlank() }
        if (savedUrls != urls) return true
        return urls.any { url ->
            val savedSignature = metadata.getString("signature_${url.hashCode()}", "").orEmpty()
            val remoteSignature = headSignature(url)
            savedSignature.isNotBlank() && remoteSignature != null && remoteSignature != savedSignature
        }
    }

    private fun saveSourceMetadata(urls: List<String>) {
        val editor = metadata.edit().putInt("format_version", 2).putString("urls", urls.joinToString("\n"))
        urls.forEach { url -> headSignature(url)?.let { editor.putString("signature_${url.hashCode()}", it) } }
        editor.apply()
    }

    private fun headSignature(urlString: String): String? = runCatching {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", "MaximusTVPlayer/1.0 AndroidTV")
        }
        val status = connection.responseCode
        val signature = if (status in 200..299) {
            listOf(connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"), connection.getHeaderField("Content-Length"))
                .joinToString("|")
                .takeUnless { it == "||" }
        } else null
        connection.disconnect()
        signature
    }.getOrNull()

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
        if (status !in 200..299) {
            stream?.close()
            connection.disconnect()
            error("A lista do painel recusou a conexão (HTTP $status)")
        }
        val input = stream ?: run {
            connection.disconnect()
            error("A lista do painel não retornou conteúdo")
        }
        val entries = ArrayList<CatalogEntry>()
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            parseM3uStream(reader, entries)
        }
        connection.disconnect()
        if (entries.isEmpty()) error("A lista do painel não contém entradas M3U válidas")
        return entries
    }

    private fun parseM3uStream(reader: java.io.Reader, entries: MutableList<CatalogEntry>) {
        val buffer = StringBuilder()
        val chunk = CharArray(32 * 1024)
        var pendingInfo: String? = null
        while (true) {
            val count = reader.read(chunk)
            if (count < 0) break
            buffer.append(chunk, 0, count)
            while (true) {
                val token = buffer.indexOf("#EXTINF:")
                val newline = buffer.indexOf('\n')
                if (token < 0) {
                    if (newline >= 0) {
                        val line = buffer.substring(0, newline)
                        buffer.delete(0, newline + 1)
                        pendingInfo = processM3uLine(line, pendingInfo, entries)
                        continue
                    }
                    if (buffer.length > MAX_FRAGMENT_CHARS) error("A lista M3U contém uma linha inválida muito grande")
                    break
                }
                if (token > 0) {
                    if (newline >= 0 && newline < token) {
                        val line = buffer.substring(0, newline)
                        buffer.delete(0, newline + 1)
                        pendingInfo = processM3uLine(line, pendingInfo, entries)
                    } else {
                        buffer.delete(0, token)
                    }
                    continue
                }
                val nextToken = buffer.indexOf("#EXTINF:", 8)
                if (nextToken >= 0) {
                    val block = buffer.substring(0, nextToken)
                    buffer.delete(0, nextToken)
                    pendingInfo = processM3uBlock(block, pendingInfo, entries)
                    continue
                }
                if (newline >= 0) {
                    val line = buffer.substring(0, newline)
                    buffer.delete(0, newline + 1)
                    pendingInfo = processM3uLine(line, pendingInfo, entries)
                    continue
                }
                if (buffer.length > MAX_FRAGMENT_CHARS) error("A entrada M3U ultrapassou o limite seguro")
                break
            }
        }
        if (buffer.isNotBlank()) processM3uBlock(buffer.toString(), pendingInfo, entries)
    }

    private fun processM3uBlock(block: String, pendingInfo: String?, entries: MutableList<CatalogEntry>): String? {
        var nextPending = pendingInfo
        block.split('\n').forEach { line -> nextPending = processM3uLine(line, nextPending, entries) }
        return nextPending
    }

    private fun processM3uLine(raw: String, pendingInfo: String?, entries: MutableList<CatalogEntry>): String? {
        val line = raw.replace("\r", "").trim()
        if (line.isBlank()) return pendingInfo
        if (line.startsWith("#EXTINF:")) {
            val sameLineUrl = SAME_LINE_URL_PATTERN.find(line)
            if (sameLineUrl != null) {
                addEntry(entries, line.substring(0, sameLineUrl.range.first).trim(), sameLineUrl.groupValues[1])
                return null
            }
            return line
        }
        if (pendingInfo != null && line.startsWith("http")) {
            addEntry(entries, pendingInfo, line)
            return null
        }
        return pendingInfo
    }

    private fun addEntry(target: MutableList<CatalogEntry>, info: String, streamUrl: String) {
        val attributes = ATTRIBUTE_PATTERN.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
        val displayName = info.substringAfter(',', attributes["tvg-name"].orEmpty()).trim()
        if (displayName.isBlank() || streamUrl.isBlank()) return
        val group = attributes["group-title"].orEmpty().ifBlank { "Sem categoria" }
        val kind = classify(displayName, group)
        val quality = QUALITY_PATTERN.find(displayName)?.value?.uppercase().orEmpty()
        val synopsis = firstAttribute(attributes, "description", "tvg-desc", "tvg-description", "plot", "synopsis", "summary", "overview")
        val cast = firstAttribute(attributes, "cast", "actors", "actor", "elenco", "tvg-cast")
        val year = firstAttribute(attributes, "year", "release-year", "release_year", "date")
        val backdrop = firstAttribute(attributes, "backdrop", "backdrop-url", "backdrop_url", "fanart", "fanart-url", "background")
        val trailer = firstAttribute(attributes, "trailer", "trailer-url", "trailer_url", "youtube-trailer", "youtube_trailer")
        val runtime = firstAttribute(attributes, "duration", "runtime", "length")
        target += CatalogEntry(
            key = "${attributes["tvg-id"].orEmpty()}|$streamUrl",
            name = displayName,
            groupTitle = group,
            tvgId = attributes["tvg-id"].orEmpty(),
            logoUrl = attributes["tvg-logo"].orEmpty(),
            streamUrl = streamUrl,
            kind = kind,
            quality = quality,
            seriesGroup = if (kind == MediaKind.SERIES) displayName.split(SERIES_PATTERN).first() else "",
            year = year,
            synopsis = synopsis,
            cast = cast,
            backdropUrl = backdrop,
            trailerUrl = trailer,
            runtime = runtime,
        )
    }

    private fun firstAttribute(attributes: Map<String, String>, vararg keys: String): String {
        keys.forEach { key -> attributes[key]?.trim()?.takeIf { it.isNotBlank() }?.let { return it } }
        return ""
    }

    private fun classify(name: String, group: String): MediaKind {
        val normalizedGroup = group.lowercase().trim()
        val normalizedName = name.lowercase()
        if (normalizedGroup.startsWith("filmes |")) return MediaKind.MOVIE
        if (normalizedGroup.startsWith("series |")) return MediaKind.SERIES
        if (normalizedGroup.contains("24/7 filmes") || normalizedGroup.contains("24/7 seriados") || normalizedGroup.contains("24/7 doramas") || normalizedGroup.contains("24/7 animes") || normalizedGroup.contains("24/7 novelas")) return MediaKind.LIVE
        if (normalizedGroup == "filmes e séries" || normalizedGroup == "filmes e series") return MediaKind.LIVE
        if (normalizedName.contains("temporada") || SEASON_NAME_PATTERN.containsMatchIn(normalizedName)) return MediaKind.SERIES
        if (normalizedName.contains("filme") || normalizedName.contains("movie")) return MediaKind.MOVIE
        return MediaKind.LIVE
    }

    private fun writeCache(entries: List<CatalogEntry>) {
        GZIPOutputStream(cacheFile.outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use { writer ->
            entries.forEach { entry ->
                val fields = listOf(entry.key, entry.name, entry.groupTitle, entry.tvgId, entry.logoUrl, entry.streamUrl, entry.kind.name, entry.quality, entry.seriesGroup, entry.season, entry.episode, entry.year, entry.synopsis, entry.cast, entry.backdropUrl, entry.trailerUrl, entry.runtime)
                writer.append(fields.joinToString("\t") { encode(it) }).append('\n')
            }
        }
    }

    private fun readCache(): List<CatalogEntry> {
        if (!cacheFile.exists()) return emptyList()
        val entries = ArrayList<CatalogEntry>()
        GZIPInputStream(cacheFile.inputStream().buffered()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val f = line.split('\t').map(::decode)
                if (f.size >= 9) {
                    runCatching {
                        entries += CatalogEntry(
                            key = f[0], name = f[1], groupTitle = f[2], tvgId = f[3], logoUrl = f[4], streamUrl = f[5],
                            kind = MediaKind.valueOf(f[6]), quality = f[7], seriesGroup = f[8],
                            season = f.getOrElse(9) { "" }, episode = f.getOrElse(10) { "" }, year = f.getOrElse(11) { "" },
                            synopsis = f.getOrElse(12) { "" }, cast = f.getOrElse(13) { "" }, backdropUrl = f.getOrElse(14) { "" },
                            trailerUrl = f.getOrElse(15) { "" }, runtime = f.getOrElse(16) { "" },
                        )
                    }
                }
            }
        }
        return entries
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
}
