package com.pingpaisa.app.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Keeps the scanned list across app restarts (private app storage). */
class ProductStore(context: Context) {
    private val file = File(context.filesDir, "scanned_products.json")

    fun load(): List<Product> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { productFromJson(a.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(items: List<Product>) {
        try {
            val a = JSONArray()
            items.forEach { a.put(it.toJson()) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(a.toString())
            if (!tmp.renameTo(file)) { file.writeText(a.toString()); tmp.delete() }
        } catch (_: Exception) {
            // best effort; the in-memory list stays authoritative
        }
    }
}
