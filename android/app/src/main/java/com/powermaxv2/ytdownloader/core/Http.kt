package com.powermaxv2.ytdownloader.core

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Minimal HTTPS client (GitHub API, yt-dlp updates, Instagram photos). Plain http is refused. */
object Http {
    const val USER_AGENT = "YTDownloader-Android"

    private fun open(url: String, headers: Map<String, String>, followRedirects: Boolean = true): HttpURLConnection {
        if (!url.startsWith("https://")) throw IOException("Seuls les liens https:// sont acceptés.")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = followRedirects
        connection.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection
    }

    data class Response(val code: Int, val body: String, val location: String? = null) {
        val isSuccess: Boolean get() = code in 200..299
    }

    /**
     * GET that also returns error responses and, with [followRedirects] off, redirections
     * (Instagram answers a refused session with a redirect to its login page).
     */
    fun get(url: String, headers: Map<String, String> = emptyMap(), followRedirects: Boolean = true): Response {
        val connection = open(url, headers, followRedirects)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, body, connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val response = get(url, headers)
        if (!response.isSuccess) throw IOException("HTTP ${response.code}")
        return response.body
    }

    /** Address reached after following the redirects of [url]. */
    fun finalUrl(url: String, headers: Map<String, String> = emptyMap()): String {
        val connection = open(url, headers)
        try {
            connection.responseCode
            return connection.url.toString()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads [url] into [dest] and returns its SHA-256 (hex).
     * [shouldStop] is polled between chunks so a download can be cancelled.
     */
    fun download(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
        shouldStop: () -> Boolean = { false },
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
    ): String {
        val connection = open(url, headers)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (shouldStop()) throw InterruptedException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                    }
                }
            }
            return digest.digest().toHex()
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
