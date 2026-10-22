package com.iyad.deen.core

import android.Manifest
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.shared.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

object PlaceFinder {
    suspend fun hasPermission(): Boolean =
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION).asKotlinResult
            .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
            .getOrDefault(false)

    /**
     * Asks LightOS for a GPS fix (up to ~45 s), falling back to the default location the user
     * set on the Light dashboard. Returns null if neither is available.
     */
    suspend fun locate(): Pair<Double, Double>? {
        val fix = withTimeoutOrNull(45.seconds) {
            try {
                var tries = 0
                while (true) {
                    if (tries % 10 == 0) callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
                    val r = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit).getOrNull()
                    val lat = r?.latitude
                    val lon = r?.longitude
                    val fresh = r?.timestampMs?.let { System.currentTimeMillis() - it < 30 * 60_000 } ?: true
                    if (lat != null && lon != null && fresh) return@withTimeoutOrNull lat to lon
                    tries++
                    delay(2.seconds)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            } finally {
                withContext(NonCancellable) {
                    callRemoteServiceMethod(LightServiceMethod.ReleaseLocationUpdates, Unit)
                }
            }
        }
        if (fix != null) return fix
        val d = callRemoteServiceMethod(LightServiceMethod.GetDefaultLocation, Unit).getOrNull()
        val lat = d?.latitude
        val lon = d?.longitude
        return if (lat != null && lon != null) lat to lon else null
    }

    /** Names the coordinates using the bundled offline city list. */
    suspend fun describe(lat: Double, lon: Double, ctx: SealedLightContext): SavedPlace =
        withContext(Dispatchers.Default) {
            val city = Content.nearestCity(lat, lon, ctx::readAsset)
            if (city == null) {
                SavedPlace(lat, lon, formatCoords(lat, lon), "")
            } else {
                SavedPlace(lat, lon, "${city.name}, ${Content.countryName(city.countryCode, ctx::readAsset)}", city.countryCode)
            }
        }
}

fun formatCoords(lat: Double, lon: Double): String =
    "%.3f°%s, %.3f°%s".format(abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")
