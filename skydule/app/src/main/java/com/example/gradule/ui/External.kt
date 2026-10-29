package com.example.gradule.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

private const val MAPS_PACKAGE = "com.google.android.apps.maps"
const val GRADIENT_WEATHER_PACKAGE = "com.subtlesignals.gradientweather"

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Opens [place] in Google Maps, or any other maps app / the browser if Google Maps isn't installed. */
fun Context.openInMaps(place: String) {
    val q = Uri.encode(place)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).setPackage(MAPS_PACKAGE)
    if (tryStart(geo)) return
    if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))) return
    tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
}

/** Launches Gradient Weather; falls back to its Play Store page, then its website. */
fun Context.openGradientWeather() {
    val launch = packageManager.getLaunchIntentForPackage(GRADIENT_WEATHER_PACKAGE)
    if (launch != null && tryStart(launch)) return
    if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GRADIENT_WEATHER_PACKAGE")))) return
    tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://gradientweather.app/")))
}
