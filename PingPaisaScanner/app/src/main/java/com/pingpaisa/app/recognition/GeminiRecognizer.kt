package com.pingpaisa.app.recognition

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

data class AiProduct(val name: String?, val brand: String?, val category: String?, val subcategory: String?)

/**
 * OPTIONAL online product identification via the Gemini REST API. Active only when GEMINI_API_KEY is set in
 * local.properties. It only suggests a name/brand and a (category, subcategory) chosen from YOUR dataset's
 * taxonomy; the price always stays an OCR/manual value. Any failure returns null and the on-device result is used.
 * Note: a key compiled into an APK can be extracted - for a Play Store release call this through your own backend.
 */
class GeminiRecognizer(private val apiKey: String, private val model: String, private val taxonomy: String) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    suspend fun identify(bitmap: Bitmap): AiProduct? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            try { request(bitmap) } catch (_: Exception) { null }
        }
    }

    private fun request(bitmap: Bitmap): AiProduct? {
        val longest = max(bitmap.width, bitmap.height)
        val small = if (longest > 768) {
            val s = 768f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
        } else bitmap
        val jpeg = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        val prompt = "You label shopping photos for an expense app. Identify the main product in the image. " +
            "Reply with JSON only: {\"name\": string or null, \"brand\": string or null, \"category\": string, \"subcategory\": string}. " +
            "Use null for name/brand if you cannot tell from the image - never guess a model number. " +
            "category and subcategory MUST be copied exactly from this list (use Miscellaneous / Uncategorized if nothing fits):\n$taxonomy"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val text = JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
            val o = JSONObject(text)
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
            return AiProduct(s("name"), s("brand"), s("category"), s("subcategory"))
        } finally {
            conn.disconnect()
        }
    }
}
