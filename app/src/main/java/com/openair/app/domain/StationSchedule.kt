package com.openair.app.domain

/**
 * Deterministic shared clock for location stations.
 *
 * A station plays its rotation on a fixed schedule derived from wall-clock
 * time, so two listeners tuned to the same station at the same moment hear
 * the same audio — like terrestrial radio. The rotation loops; the "live
 * point" is wherever the schedule says the rotation is right now.
 *
 * The MVP computes this client-side from a shared epoch anchor. When the
 * backend exists, the anchor (and rotation) come from the server so schedule
 * changes don't desync listeners.
 */
object StationSchedule {
    /** 2026-01-01T00:00:00Z — every client derives the same rotation clock from it. */
    const val ANCHOR_EPOCH_SECONDS = 1_767_225_600L

    data class SchedulePoint(
        val index: Int,
        val offsetSeconds: Float,
        val globalSeconds: Double
    )

    fun cycleSeconds(rotation: List<AudioClip>): Int =
        rotation.sumOf { it.durationSeconds }

    /** Where the live broadcast is right now. */
    fun livePoint(
        rotation: List<AudioClip>,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): SchedulePoint {
        val cycle = cycleSeconds(rotation)
        if (rotation.isEmpty() || cycle <= 0) return SchedulePoint(0, 0f, 0.0)
        val elapsed = Math.floorMod(nowEpochSeconds - ANCHOR_EPOCH_SECONDS, cycle.toLong()).toDouble()
        return pointAt(rotation, elapsed)
    }

    /** Maps a position inside the rotation cycle to a clip index and offset. */
    fun pointAt(rotation: List<AudioClip>, globalSeconds: Double): SchedulePoint {
        var remaining = globalSeconds
        rotation.forEachIndexed { index, clip ->
            if (remaining < clip.durationSeconds) {
                return SchedulePoint(index, remaining.toFloat(), globalSeconds)
            }
            remaining -= clip.durationSeconds
        }
        return SchedulePoint(0, 0f, 0.0)
    }

    /** Global cycle position for a listener at [positionSeconds] inside clip [index]. */
    fun globalSeconds(rotation: List<AudioClip>, index: Int, positionSeconds: Float): Double {
        var total = 0.0
        rotation.take(index).forEach { total += it.durationSeconds }
        return total + positionSeconds
    }

    /** Seconds the listener trails the live broadcast (0 when at the live edge). */
    fun behindLiveSeconds(
        rotation: List<AudioClip>,
        index: Int,
        positionSeconds: Float,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): Float {
        val cycle = cycleSeconds(rotation)
        if (rotation.isEmpty() || cycle <= 0) return 0f
        val live = livePoint(rotation, nowEpochSeconds).globalSeconds
        val current = globalSeconds(rotation, index, positionSeconds)
        var diff = live - current
        if (diff < 0) diff += cycle
        return diff.toFloat()
    }
}
