package com.example.gradule.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Approximate (city-level) location, which is all weather needs. Requires ACCESS_COARSE_LOCATION. */
class LocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? = withTimeoutOrNull(10_000) {
        try {
            client.lastLocation.awaitOrNull() ?: run {
                val cts = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .awaitOrNull { cts.cancel() }
            }
        } catch (_: SecurityException) {
            null
        }
    }
}

private suspend fun <T> Task<T>.awaitOrNull(onCancel: () -> Unit = {}): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { onCancel() }
    }
