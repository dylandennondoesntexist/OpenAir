package com.openair.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import java.util.Locale

/**
 * Best-effort coarse location for tagging recordings. Per the MVP location
 * policy there is no background location and no active fix request — just
 * the last known position, or null.
 */
object LastKnownLocation {
    fun coarse(context: Context): Location? {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        for (provider in providers) {
            try {
                manager.getLastKnownLocation(provider)?.let { return it }
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return null
    }

    /**
     * Reverse-geocodes the last known position to a short place name
     * ("Asbury Park"). Blocking network call — invoke from Dispatchers.IO.
     */
    fun localityLabel(context: Context): String? {
        val location = coarse(context) ?: return null
        if (!Geocoder.isPresent()) return null
        return try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subLocality ?: it.subAdminArea ?: it.adminArea }
        } catch (_: Exception) {
            null
        }
    }
}
