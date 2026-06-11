package com.openair.app.location

data class LocationPolicy(
    val requestBackgroundLocation: Boolean = false,
    val coarseUpdateMeters: Int = 500,
    val coarseUpdateSeconds: Int = 30,
    val stopWhenNotPlaying: Boolean = true
)

object MvpLocationPolicy {
    val default = LocationPolicy()
}
