package com.example.gradule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Current conditions from Open-Meteo: free, no API key, no account. */
class WeatherRepository {

    suspend fun current(lat: Double, lon: Double, fahrenheit: Boolean): Weather? = withContext(Dispatchers.IO) {
        try {
            // Locale.US so the decimal separator is always a dot, whatever the phone's language.
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${"%.3f".format(Locale.US, lat)}" +
                    "&longitude=${"%.3f".format(Locale.US, lon)}" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto" +
                    if (fahrenheit) "&temperature_unit=fahrenheit" else "",
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cur = JSONObject(body).getJSONObject("current")
                val (kind, label) = describeWeatherCode(cur.getInt("weather_code"))
                Weather(
                    temp = cur.getDouble("temperature_2m").roundToInt(),
                    kind = kind,
                    label = label,
                    isDay = cur.optInt("is_day", 1) == 1,
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
