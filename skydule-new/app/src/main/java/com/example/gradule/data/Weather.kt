package com.example.gradule.data

enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class Weather(
    val temp: Int,
    val kind: WeatherKind,
    /** Short condition, e.g. "Partly cloudy". */
    val label: String,
    val isDay: Boolean,
)

sealed interface WeatherState {
    /** No location permission yet; the header offers to enable it. */
    data object NeedsPermission : WeatherState
    /** Permission is granted but the phone's Location switch is off, so there is nothing to read. The pill offers to open Location settings. */
    data object LocationOff : WeatherState
    data object Loading : WeatherState
    data object Unavailable : WeatherState
    data class Ready(val weather: Weather) : WeatherState
}

/** Maps WMO weather codes (as returned by Open-Meteo) to an icon kind and a short label. */
fun describeWeatherCode(code: Int): Pair<WeatherKind, String> = when (code) {
    0 -> WeatherKind.CLEAR to "Clear"
    1 -> WeatherKind.CLEAR to "Mostly clear"
    2 -> WeatherKind.PARTLY to "Partly cloudy"
    3 -> WeatherKind.CLOUDY to "Cloudy"
    45, 48 -> WeatherKind.FOG to "Fog"
    in 51..57 -> WeatherKind.DRIZZLE to "Drizzle"
    in 61..67 -> WeatherKind.RAIN to "Rain"
    in 71..77 -> WeatherKind.SNOW to "Snow"
    in 80..82 -> WeatherKind.RAIN to "Showers"
    85, 86 -> WeatherKind.SNOW to "Snow showers"
    in 95..99 -> WeatherKind.STORM to "Thunderstorm"
    else -> WeatherKind.CLOUDY to "Cloudy"
}
