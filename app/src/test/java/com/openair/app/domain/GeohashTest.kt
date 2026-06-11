package com.openair.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {

    @Test
    fun encode_matchesPublicReferenceVectors() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        assertEquals("ezs42", Geohash.encode(42.605, -5.603, 5))
    }

    @Test
    fun decodeCenter_matchesReferenceCell() {
        val (lat, lng) = Geohash.decodeCenter("ezs42")!!
        assertTrue(kotlin.math.abs(lat - 42.605) < 0.03)
        assertTrue(kotlin.math.abs(lng - (-5.603)) < 0.03)
    }

    @Test
    fun decodeCenter_roundTripsWithinOneCell() {
        // Asbury Park boardwalk; a precision-5 cell is ~4.9 km square, so the
        // decoded center must be within half a diagonal of the input.
        val hash = Geohash.encode(40.2204, -74.0121, 5)
        assertEquals(5, hash.length)
        val (lat, lng) = Geohash.decodeCenter(hash)!!
        assertTrue(Geohash.distanceKm(40.2204, -74.0121, lat, lng) < 3.6)
    }

    @Test
    fun decodeCenter_rejectsInvalidInput() {
        assertNull(Geohash.decodeCenter(""))
        // 'a', 'i', 'l', 'o' are not in the geohash alphabet.
        assertNull(Geohash.decodeCenter("aaaaa"))
    }

    @Test
    fun distanceKm_isSaneForKnownPairs() {
        // New York City to Los Angeles ≈ 3,936 km.
        val nycToLa = Geohash.distanceKm(40.7128, -74.0060, 34.0522, -118.2437)
        assertTrue("was $nycToLa", nycToLa in 3900.0..3975.0)
        // One degree of longitude at the equator ≈ 111.19 km.
        val oneDegree = Geohash.distanceKm(0.0, 0.0, 0.0, 1.0)
        assertTrue("was $oneDegree", oneDegree in 111.0..111.4)
        assertEquals(0.0, Geohash.distanceKm(40.2204, -74.0121, 40.2204, -74.0121), 1e-9)
    }
}
