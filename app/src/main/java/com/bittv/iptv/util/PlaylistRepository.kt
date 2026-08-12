package com.bittv.iptv.util

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class PlaylistRepository(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun load(url: String, headers: Map<String, String> = emptyMap(), onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, audio/mpegurl, text/plain, */*")
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw HttpStatusException(status, connection.responseMessage ?: "HTTP error")
                val content = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                connection.disconnect()
                onSuccess(content)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    fun loadFromUri(uriString: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            try {
                val uri = android.net.Uri.parse(uriString)
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected file")
                val content = input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                onSuccess(content)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    class HttpStatusException(val statusCode: Int, message: String) : Exception(message)
}