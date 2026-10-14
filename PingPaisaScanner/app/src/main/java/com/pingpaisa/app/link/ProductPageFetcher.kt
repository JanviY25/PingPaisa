package com.pingpaisa.app.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class FetchFailure { OFFLINE, TIMEOUT, BLOCKED, HTTP_ERROR, NOT_HTML, INVALID }

sealed interface FetchResult {
    class Success(val info: PageInfo, val finalUrl: String, val imageBytes: ByteArray?) : FetchResult
    class Failure(val reason: FetchFailure, val message: String) : FetchResult
}

/**
 * Downloads a shared product page politely (normal GET, redirects followed, size-limited) and hands the HTML to
 * [ProductPageParser]. It never logs in, solves CAPTCHAs or retries around a block - those pages come back as
 * Failure(BLOCKED) and the user types the details instead.
 */
class ProductPageFetcher(private val context: Context) {

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext FetchResult.Failure(FetchFailure.OFFLINE, "You're offline - connect to read the product page.")
        try {
            doFetch(url)
        } catch (e: UnknownHostException) {
            FetchResult.Failure(FetchFailure.OFFLINE, "Couldn't reach the shop - check your connection.")
        } catch (e: SocketTimeoutException) {
            FetchResult.Failure(FetchFailure.TIMEOUT, "The shop took too long to answer.")
        } catch (e: Exception) {
            FetchResult.Failure(FetchFailure.HTTP_ERROR, "Couldn't read the page.")
        }
    }

    private fun doFetch(url: String): FetchResult {
        var current = url
        var redirects = 0
        while (true) {
            val u = URL(current)
            if (u.protocol != "https" && u.protocol != "http") return FetchResult.Failure(FetchFailure.INVALID, "That isn't a web link.")
            val secure = if (u.protocol == "http") URL("https", u.host, u.port, u.file) else u
            val conn = secure.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.setRequestProperty("Accept-Language", "en-IN,en;q=0.8")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return FetchResult.Failure(FetchFailure.HTTP_ERROR, "The link redirects nowhere.")
                    if (++redirects > 6) return FetchResult.Failure(FetchFailure.HTTP_ERROR, "Too many redirects.")
                    current = URL(secure, loc).toString()
                    continue
                }
                if (code == 401 || code == 403 || code == 429 || code == 503) {
                    return FetchResult.Failure(FetchFailure.BLOCKED, "The shop didn't allow its page to be read automatically.")
                }
                if (code !in 200..299) return FetchResult.Failure(FetchFailure.HTTP_ERROR, "The page returned an error ($code).")
                val type = conn.contentType.orEmpty().lowercase()
                if (type.isNotEmpty() && !type.contains("html")) return FetchResult.Failure(FetchFailure.NOT_HTML, "That link isn't a product page.")
                val html = String(readLimited(conn.inputStream, MAX_HTML_BYTES), charsetOf(type))
                val info = ProductPageParser.parse(html)
                if (info.blocked) return FetchResult.Failure(FetchFailure.BLOCKED, "The shop showed a bot check instead of the product.")
                val image = info.imageUrl?.let { downloadImage(it) }
                return FetchResult.Success(info, current, image)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun downloadImage(url: String): ByteArray? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000; conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode in 200..299) readLimited(conn.inputStream, MAX_IMAGE_BYTES) else null
        } finally { conn.disconnect() }
    } catch (_: Exception) { null }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        input.use {
            while (out.size() < limit) {
                val n = it.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun charsetOf(contentType: String) = runCatching {
        Regex("charset=([\\w-]+)").find(contentType)?.groupValues?.get(1)?.let { java.nio.charset.Charset.forName(it) }
    }.getOrNull() ?: Charsets.UTF_8

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36 PingPaisa/1.0"
        private const val MAX_HTML_BYTES = 1_500_000
        private const val MAX_IMAGE_BYTES = 3_000_000
    }
}
