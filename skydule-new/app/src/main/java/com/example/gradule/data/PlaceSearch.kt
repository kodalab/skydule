package com.example.gradule.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.gradule.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * One type-ahead suggestion: [title] is the place name, [subtitle] the address, [text] what gets stored on the event.
 * [placeId] is set for Google results and is used to close the billing session when the user picks one.
 */
data class PlaceSuggestion(val title: String, val subtitle: String, val placeId: String? = null) {
    val text: String get() = if (subtitle.isBlank()) title else "$title, $subtitle"
    val fromGoogle: Boolean get() = placeId != null
}

/**
 * Live place look-ups while typing.
 *
 * Google Places (New) is used when a key was built in: it knows businesses and home addresses that OpenStreetMap
 * often lacks. Photon (OpenStreetMap, no key) is the fallback when there is no key, the call fails, Google has
 * nothing, or one of the limits below is reached, so suggestions never simply stop.
 *
 * Limits, to protect the free allowance (a server-side quota in Google Cloud is the real backstop, since a key
 * built into an app can be extracted):
 *  - an in-memory cache, so retyping or backspacing never repeats a request;
 *  - at most [SESSION_CAP] Google requests per typing session (a session ends when a place is picked or goes stale);
 *  - at most [DAILY_CAP] Google requests per day on this phone.
 *
 * Billing: requests in one session share a token; picking a suggestion sends one Place Details call for just the
 * place id (see [finish]), which closes the session so the typing requests are not billed one by one.
 */
object PlaceSearch {
    private const val SESSION_CAP = 25
    private const val DAILY_CAP = 300
    private const val SESSION_MAX_MS = 2 * 60_000L
    private const val BIAS_MAX_AGE_MS = 10 * 60_000L
    private const val TAG = "PlaceSearch"

    private val cache = object : LinkedHashMap<String, List<PlaceSuggestion>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlaceSuggestion>>?) = size > 40
    }
    private var token: String? = null
    private var tokenStart = 0L
    private var sessionCalls = 0
    private var bias: Triple<Double, Double, Long>? = null
    private var certSha1: String? = null

    suspend fun search(context: Context, query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val key = query.trim().lowercase(Locale.ROOT)
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val where = location(app)
        var out: List<PlaceSuggestion> = emptyList()
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && allowGoogle(app)) {
            out = google(app, query, where)
        }
        if (out.isEmpty()) out = photon(query, where)
        if (out.isNotEmpty()) synchronized(cache) { cache[key] = out }
        out
    }

    /** Call when the user picks [picked]: closes the Google session (one cheap Place Details call) and resets it. */
    suspend fun finish(context: Context, picked: PlaceSuggestion) = withContext(Dispatchers.IO) {
        val t = token
        token = null
        sessionCalls = 0
        val id = picked.placeId
        if (t == null || id == null || BuildConfig.PLACES_API_KEY.isBlank()) return@withContext
        try {
            val url = URL("https://places.googleapis.com/v1/places/" + URLEncoder.encode(id, "UTF-8") +
                "?sessionToken=" + t)
            val conn = googleConn(context.applicationContext, url, "GET")
            conn.setRequestProperty("X-Goog-FieldMask", "id")
            try { conn.responseCode } finally { conn.disconnect() }
        } catch (_: Exception) {
            // Closing the session is best effort.
        }
    }

    // --- Google Places (New) ---

    private fun allowGoogle(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (token == null || now - tokenStart > SESSION_MAX_MS) {
            token = UUID.randomUUID().toString()
            tokenStart = now
            sessionCalls = 0
        }
        if (sessionCalls >= SESSION_CAP) return false
        val prefs = context.getSharedPreferences("gradule_places", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        val used = if (prefs.getLong("day", -1) == today) prefs.getInt("count", 0) else 0
        if (used >= DAILY_CAP) return false
        prefs.edit().putLong("day", today).putInt("count", used + 1).apply()
        sessionCalls++
        return true
    }

    private fun google(context: Context, query: String, where: Pair<Double, Double>?): List<PlaceSuggestion> {
        return try {
            val body = JSONObject().put("input", query).put("sessionToken", token)
                .put("languageCode", Locale.getDefault().language)
            Locale.getDefault().country.takeIf { it.length == 2 }?.let { body.put("regionCode", it) }
            where?.let { (lat, lon) ->
                body.put(
                    "locationBias",
                    JSONObject().put(
                        "circle",
                        JSONObject()
                            .put("center", JSONObject().put("latitude", lat).put("longitude", lon))
                            .put("radius", 50_000.0),
                    ),
                )
            }
            val conn = googleConn(context, URL("https://places.googleapis.com/v1/places:autocomplete"), "POST")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200) {
                    Log.w(TAG, "Google autocomplete HTTP ${conn.responseCode}")
                    Diag.record("PlaceSearch", "Google autocomplete HTTP ${conn.responseCode}")
                    return emptyList()
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val items: JSONArray = json.optJSONArray("suggestions") ?: return emptyList()
                val out = ArrayList<PlaceSuggestion>()
                for (i in 0 until items.length()) {
                    val p = items.getJSONObject(i).optJSONObject("placePrediction") ?: continue
                    val id = p.optString("placeId").takeIf { it.isNotBlank() } ?: continue
                    val fmt = p.optJSONObject("structuredFormat")
                    val main = fmt?.optJSONObject("mainText")?.optString("text").orEmpty()
                        .ifBlank { p.optJSONObject("text")?.optString("text").orEmpty() }
                    if (main.isBlank()) continue
                    val rest = fmt?.optJSONObject("secondaryText")?.optString("text").orEmpty()
                    out += PlaceSuggestion(main, rest, id)
                }
                out
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google autocomplete failed: ${e.javaClass.simpleName}")
            Diag.record("PlaceSearch", e)
            emptyList()
        }
    }

    /** The key is restricted to this app, so Google wants the package name and signing certificate with every request. */
    private fun googleConn(context: Context, url: URL, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("X-Goog-Api-Key", BuildConfig.PLACES_API_KEY)
            setRequestProperty("X-Android-Package", context.packageName)
            certSha1(context)?.let { setRequestProperty("X-Android-Cert", it) }
        }

    @Suppress("DEPRECATION")
    private fun certSha1(context: Context): String? {
        certSha1?.let { return it }
        return try {
            val pm = context.packageManager
            val sig = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            sig?.let {
                MessageDigest.getInstance("SHA-1").digest(it.toByteArray())
                    .joinToString("") { b -> "%02X".format(b) }
            }?.also { certSha1 = it }
        } catch (_: Exception) {
            null
        }
    }

    // --- Approximate location, to rank nearby places first ---

    private suspend fun location(context: Context): Pair<Double, Double>? {
        bias?.let { (lat, lon, at) -> if (System.currentTimeMillis() - at < BIAS_MAX_AGE_MS) return lat to lon }
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val loc = withTimeoutOrNull(1500) { LocationProvider(context).current() } ?: return null
        bias = Triple(loc.latitude, loc.longitude, System.currentTimeMillis())
        return loc.latitude to loc.longitude
    }

    // --- Photon fallback (OpenStreetMap, no key) ---

    private fun photon(query: String, where: Pair<Double, Double>?): List<PlaceSuggestion> {
        return try {
            val near = where?.let { "&lat=${it.first}&lon=${it.second}" }.orEmpty()
            val url = URL("https://photon.komoot.io/api/?limit=5$near&q=" + URLEncoder.encode(query, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Skydule/1.0 (Android calendar)")
            }
            try {
                if (conn.responseCode != 200) return emptyList()
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
