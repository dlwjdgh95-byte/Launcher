package app.monolauncher.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal

sealed interface LocationResult {
    data class Found(val location: Location) : LocationResult
    data object ServicesOff : LocationResult
    data object Unavailable : LocationResult
}

fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** One fresh location fix on the main thread; the platform times out on its own if there is no fix. */
fun Context.requestCurrentLocation(onResult: (LocationResult) -> Unit) {
    val manager = getSystemService(LocationManager::class.java)
    if (!manager.isLocationEnabled) return onResult(LocationResult.ServicesOff)
    val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { manager.hasProvider(it) && manager.isProviderEnabled(it) }
        ?: return onResult(LocationResult.Unavailable)
    try {
        manager.getCurrentLocation(provider, CancellationSignal(), mainExecutor) { location ->
            onResult(if (location != null) LocationResult.Found(location) else LocationResult.Unavailable)
        }
    } catch (_: SecurityException) {
        onResult(LocationResult.Unavailable)
    }
}
