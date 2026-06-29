package com.openair.app.domain

enum class ClipCategory(val label: String) {
    All("All"),
    FoodDrink("Food & Drink"),
    StoriesPeople("Stories & People"),
    Music("Music"),
    History("History")
}

/**
 * The two home feeds: a personalized local-first algorithmic feed, and a
 * shared location station where every listener hears the same rotation.
 */
enum class FeedMode {
    ForYou,
    Station
}

data class Station(
    val id: String,
    val name: String,
    val region: String,
    val listenersNow: Int,
    val clipCount: Int,
    val isNearby: Boolean
)

data class GeoCell(
    val h3Index: String,
    val label: String,
    val clipDensity: Int
)

data class AudioClip(
    val id: String,
    val title: String,
    val creatorName: String,
    val creatorHandle: String = "@local",
    val category: ClipCategory,
    val locationLabel: String,
    val durationSeconds: Int,
    val summary: String,
    val publishedAgo: String,
    val audioUrl: String,
    val completionRate: Double = 0.0,
    val distanceKm: Double? = null,
    /**
     * For ingested Podcasting 2.0 soundbites: seconds into the source file
     * where this highlight begins. Null for whole tracks and user clips,
     * which play from the start. [durationSeconds] is the clip's own length.
     */
    val clipStartSeconds: Float? = null,
    /** Deep link to the full episode/track — drives the "Full episode" CTA. */
    val sourceLinkUrl: String? = null,
    /** True when audio is hotlinked from a publisher URL rather than our bucket. */
    val isExternal: Boolean = false
)

data class ClipUploadDraft(
    val filePath: String,
    val title: String,
    val description: String?,
    val locationLabel: String,
    val durationSeconds: Int,
    val latitude: Double?,
    val longitude: Double?
)

data class ListenEventDraft(
    val clipId: String,
    val listenDurationSeconds: Int,
    val completionPercent: Double,
    val skippedEarly: Boolean,
    val context: String
)

data class MvpMetric(
    val label: String,
    val value: String,
    val target: String
)
