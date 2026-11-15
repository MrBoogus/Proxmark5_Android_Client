package com.proxmark5.client.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CapturedTag(
    val id: String = UUID.randomUUID().toString(),
    val uidHex: String,
    val technology: String,        // "EM4100", "HID Prox", "AWID", "IO Prox", "MIFARE Classic", etc.
    val frequency: String = "LF",  // "LF" (125 kHz) or "HF" (13.56 MHz)
    val cardId: String = "",
    val facilityCode: String? = null,
    val formatDescription: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("uidHex", uidHex)
            put("technology", technology)
            put("frequency", frequency)
            put("cardId", cardId)
            put("facilityCode", facilityCode ?: "")
            put("formatDescription", formatDescription ?: "")
            put("timestamp", timestamp)
            put("note", note)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CapturedTag {
            return CapturedTag(
                id = json.optString("id", UUID.randomUUID().toString()),
                uidHex = json.optString("uidHex", ""),
                technology = json.optString("technology", "Unknown"),
                frequency = json.optString("frequency", "LF"),
                cardId = json.optString("cardId", ""),
                facilityCode = json.optString("facilityCode").ifEmpty { null },
                formatDescription = json.optString("formatDescription").ifEmpty { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                note = json.optString("note", "")
            )
        }
    }
}

class TagStorageManager(private val context: Context) {
    private val storageFile: File by lazy {
        File(context.filesDir, "pm5_saved_tags.json")
    }

    @Synchronized
    fun loadAll(): List<CapturedTag> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val content = storageFile.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<CapturedTag>()
            for (i in 0 until jsonArray.length()) {
                list.add(CapturedTag.fromJson(jsonArray.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(tag: CapturedTag): List<CapturedTag> {
        val current = loadAll().toMutableList()
        // Deduplicate if same UID was captured within 3 seconds
        val existingIdx = current.indexOfFirst { it.uidHex.equals(tag.uidHex, ignoreCase = true) }
        if (existingIdx >= 0 && (System.currentTimeMillis() - current[existingIdx].timestamp) < 3000) {
            return current
        }
        current.add(0, tag)
        persist(current)
        return current
    }

    @Synchronized
    fun delete(id: String): List<CapturedTag> {
        val current = loadAll().filterNot { it.id == id }
        persist(current)
        return current
    }

    @Synchronized
    fun clear(): List<CapturedTag> {
        persist(emptyList())
        return emptyList()
    }

    fun exportJson(): String {
        val current = loadAll()
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    private fun persist(list: List<CapturedTag>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            storageFile.writeText(array.toString(2))
        } catch (_: Exception) {
        }
    }
}
