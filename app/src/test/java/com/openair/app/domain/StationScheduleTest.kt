package com.openair.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StationScheduleTest {
    private fun clip(id: String, durationSeconds: Int) = AudioClip(
        id = id,
        title = id,
        creatorName = "Test",
        category = ClipCategory.All,
        locationLabel = "Test",
        durationSeconds = durationSeconds,
        summary = "",
        publishedAgo = "",
        audioUrl = "mock://$id"
    )

    // Cycle: 10s + 20s + 30s = 60s
    private val rotation = listOf(clip("a", 10), clip("b", 20), clip("c", 30))

    @Test
    fun livePoint_atAnchorIsStartOfRotation() {
        val point = StationSchedule.livePoint(rotation, StationSchedule.ANCHOR_EPOCH_SECONDS)

        assertEquals(0, point.index)
        assertEquals(0f, point.offsetSeconds, 0.001f)
    }

    @Test
    fun livePoint_landsInsideSecondClip() {
        val point = StationSchedule.livePoint(rotation, StationSchedule.ANCHOR_EPOCH_SECONDS + 15)

        assertEquals(1, point.index)
        assertEquals(5f, point.offsetSeconds, 0.001f)
    }

    @Test
    fun livePoint_wrapsAroundTheCycle() {
        val point = StationSchedule.livePoint(rotation, StationSchedule.ANCHOR_EPOCH_SECONDS + 75)

        assertEquals(1, point.index)
        assertEquals(5f, point.offsetSeconds, 0.001f)
    }

    @Test
    fun livePoint_sameMomentSamePointForEveryListener() {
        val now = StationSchedule.ANCHOR_EPOCH_SECONDS + 1_234_567
        val listener1 = StationSchedule.livePoint(rotation, now)
        val listener2 = StationSchedule.livePoint(rotation, now)

        assertEquals(listener1, listener2)
    }

    @Test
    fun pointAt_clipBoundaryBelongsToNextClip() {
        val point = StationSchedule.pointAt(rotation, 10.0)

        assertEquals(1, point.index)
        assertEquals(0f, point.offsetSeconds, 0.001f)
    }

    @Test
    fun behindLive_zeroWhenListenerIsAtLivePoint() {
        val now = StationSchedule.ANCHOR_EPOCH_SECONDS + 15
        val behind = StationSchedule.behindLiveSeconds(rotation, index = 1, positionSeconds = 5f, nowEpochSeconds = now)

        assertEquals(0f, behind, 0.001f)
    }

    @Test
    fun behindLive_growsWhenListenerRewinds() {
        val now = StationSchedule.ANCHOR_EPOCH_SECONDS + 15
        val behind = StationSchedule.behindLiveSeconds(rotation, index = 0, positionSeconds = 5f, nowEpochSeconds = now)

        assertEquals(10f, behind, 0.001f)
    }

    @Test
    fun behindLive_handlesLiveWrappingPastCycleEnd() {
        // Listener near the end of the cycle (global 55s); live wrapped to 5s.
        val now = StationSchedule.ANCHOR_EPOCH_SECONDS + 65
        val behind = StationSchedule.behindLiveSeconds(rotation, index = 2, positionSeconds = 25f, nowEpochSeconds = now)

        assertEquals(10f, behind, 0.001f)
    }
}
