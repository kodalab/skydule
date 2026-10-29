package com.example.gradule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One type-ahead suggestion: [title] is the place name, [subtitle] the address, [text] what gets stored on the event. */
data class PlaceSuggestion(val title: String, val subtitle: String) {
    val text: String get() = if (subtitle.isBlank()) title else "$title, $subtitle"
}

/** Live place look-ups while typing, via Photon (OpenStreetMap data, no API key). Returns an empty list on any failure. */
object PlaceSearch {
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://photon.komoot.io/api/?limit=5&q=" + URLEncoder.encode(query, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Gradule/0.3 (Android calendar)")
            }
            try {
                if (conn.responseCode != 200) return@withContext emptyList()
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val features = JSONObject(body).getJSONArray("features")
                val out = LinkedHashSet<PlaceSuggestion>()
                for (i in 0 until features.length()) {
                    val p = features.getJSONObject(i).getJSONObject("properties")
                    val street = listOf(p.optString("housenumber"), p.optString("street"))
                        .filter { it.isNotBlank() }.joinToString(" ")
                    val name = p.optString("name").ifBlank { street }
                    if (name.isBlank()) continue
                    val rest = listOf(
                        if (p.optString("name").isNotBlank()) street else "",
                        p.optString("city").ifBlank { p.optString("district") },
                        p.optString("state"),
                        p.optString("country"),
                    ).filter { it.isNotBlank() && it != name }.distinct()
                    out += PlaceSuggestion(name, rest.joinToString(", "))
                }
                out.toList()
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
