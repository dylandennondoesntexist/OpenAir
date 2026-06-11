package com.openair.app.data

import com.openair.app.domain.AudioClip
import com.openair.app.domain.ClipCategory
import com.openair.app.domain.ClipUploadDraft
import com.openair.app.domain.GeoCell
import com.openair.app.domain.ListenEventDraft
import com.openair.app.domain.MvpMetric
import com.openair.app.domain.Station

interface ClipRepository {
    /** Proximity hint for feed ranking; implementations may ignore it. */
    fun updateListenerLocation(latitude: Double, longitude: Double) {}
    fun activeCell(): GeoCell
    fun stations(): List<Station>
    fun nearbyClips(category: ClipCategory, heardClipIds: Set<String>): List<AudioClip>
    suspend fun forYouFeed(heardClipIds: Set<String>): List<AudioClip>
    suspend fun stationFeed(stationId: String): List<AudioClip>
    suspend fun recordListenEvent(event: ListenEventDraft)
    suspend fun uploadClip(draft: ClipUploadDraft): Result<Unit>
    suspend fun myClipCount(): Int
    fun mvpMetrics(): List<MvpMetric>
}
