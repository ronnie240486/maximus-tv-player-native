package com.maximus.tvplayer

import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ImageLoader {
    private val executor = Executors.newFixedThreadPool(3)

    fun load(url: String, target: ImageView, fallback: Int) {
        target.setImageResource(fallback)
        if (url.isBlank()) return
        val key = url
        target.tag = key
        executor.execute {
            val bitmap = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "MaximusTVPlayer/1.0 AndroidTV")
                }
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) {
                target.post {
                    if (target.tag == key) target.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
