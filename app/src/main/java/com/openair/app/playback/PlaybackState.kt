package com.openair.app.playback

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionSeconds: Int = 0
)
