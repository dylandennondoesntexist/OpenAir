package com.openair.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal geohash codec plus haversine, used for proximity ranking. The
 * database stores a public 5-char cell (~4.9 × 4.9 km) computed server-side
 * from the private coordinates; the client only decodes cell centers.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val EARTH_RADIUS_KM = 6371.0

    fun encode(latitude: Double, longitude: Double, precision: Int): String {
        var latLo = -90.0
        var latHi = 90.0
        var lngLo = -180.0
        var lngHi = 180.0
        val result = StringBuilder()
        var bits = 0
        var ch = 0
        var evenBit = true
        while (result.length < precision) {
            if (evenBit) {
                val mid = (lngLo + lngHi) / 2
                if (longitude >= mid) {
                    ch = (ch shl 1) or 1
                    lngLo = mid
                } else {
                    ch = ch shl 1
                    lngHi = mid
                }
            } else {
                val mid = (latLo + latHi) / 2
                if (latitude >= mid) {
                    ch = (ch shl 1) or 1
                    latLo = mid
                } else {
                    ch = ch shl 1
                    latHi = mid
                }
            }
            evenBit = !evenBit
            if (++bits == 5) {
                result.append(BASE32[ch])
                bits = 0
                ch = 0
            }
        }
        return result.toString()
    }

    /** Center of the cell as (latitude, longitude), or null for invalid input. */
    fun decodeCenter(geohash: String): Pair<Double, Double>? {
        if (geohash.isBlank()) return null
        var latLo = -90.0
        var latHi = 90.0
        var lngLo = -180.0
        var lngHi = 180.0
        var evenBit = true
        for (c in geohash.lowercase()) {
            val cd = BASE32.indexOf(c)
            if (cd == -1) return null
            for (shift in 4 downTo 0) {
                val bitSet = (cd shr shift) and 1 == 1
                if (evenBit) {
                    val mid = (lngLo + lngHi) / 2
                    if (bitSet) lngLo = mid else lngHi = mid
                } else {
                    val mid = (latLo + latHi) / 2
                    if (bitSet) latLo = mid else latHi = mid
                }
                evenBit = !evenBit
            }
        }
        return (latLo + latHi) / 2 to (lngLo + lngHi) / 2
    }

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }
}
