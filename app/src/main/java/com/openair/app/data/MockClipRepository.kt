package com.openair.app.data

import com.openair.app.domain.AudioClip
import com.openair.app.domain.ClipCategory
import com.openair.app.domain.ClipUploadDraft
import com.openair.app.domain.GeoCell
import com.openair.app.domain.ListenEventDraft
import com.openair.app.domain.MvpMetric
import com.openair.app.domain.Station
import kotlinx.coroutines.delay

class MockClipRepository : ClipRepository {
    private val events = mutableListOf<ListenEventDraft>()
    private var uploadedCount = 0

    private val clips = listOf(
        AudioClip(
            id = "asbury-boardwalk-01",
            title = "The diner booth Springsteen used to haunt",
            creatorName = "Mara L.",
            creatorHandle = "@maraonshore",
            category = ClipCategory.History,
            locationLabel = "Asbury Park boardwalk",
            durationSeconds = 25,
            summary = "A quick local-history story about the corner where musicians traded rumors after late sets.",
            publishedAgo = "12 min ago",
            audioUrl = "android.resource://com.openair.app/raw/clip_asbury_boardwalk_01",
            completionRate = 0.68
        ),
        AudioClip(
            id = "belmar-tacos-02",
            title = "Order the fish tacos, skip the line",
            creatorName = "Jules Eats",
            creatorHandle = "@juleseats",
            category = ClipCategory.FoodDrink,
            locationLabel = "Belmar marina",
            durationSeconds = 19,
            summary = "A low-key food recommendation timed for drivers heading south before dinner.",
            publishedAgo = "28 min ago",
            audioUrl = "android.resource://com.openair.app/raw/clip_belmar_tacos_02",
            completionRate = 0.59
        ),
        AudioClip(
            id = "route35-demo-03",
            title = "A new chorus from a garage on Route 35",
            creatorName = "Northbound Static",
            creatorHandle = "@northboundstatic",
            category = ClipCategory.Music,
            locationLabel = "Route 35",
            durationSeconds = 38,
            summary = "Original music from a local band, rights-confirmed and normalized for a car-listening pass.",
            publishedAgo = "43 min ago",
            audioUrl = "android.resource://com.openair.app/raw/clip_route35_demo_03",
            completionRate = 0.72
        ),
        AudioClip(
            id = "neptune-porch-04",
            title = "The porch where everyone learned chess",
            creatorName = "Kenji R.",
            creatorHandle = "@kenjir",
            category = ClipCategory.StoriesPeople,
            locationLabel = "Neptune City",
            durationSeconds = 26,
            summary = "A personal neighborhood story with enough specificity to make the drive feel local.",
            publishedAgo = "1 hr ago",
            audioUrl = "android.resource://com.openair.app/raw/clip_neptune_porch_04",
            completionRate = 0.64
        ),
        AudioClip(
            id = "lake-como-05",
            title = "Why locals still call it South Belmar",
            creatorName = "Shore Notes",
            creatorHandle = "@shorenotes",
            category = ClipCategory.History,
            locationLabel = "Lake Como",
            durationSeconds = 26,
            summary = "A name-change footnote that becomes oddly charming when you are passing the lake.",
            publishedAgo = "2 hr ago",
            audioUrl = "android.resource://com.openair.app/raw/clip_lake_como_05",
            completionRate = 0.49
        )
    )

    private val seedStations = listOf(
        Station("st-asbury", "Asbury Park", "NJ · Shore corridor", listenersNow = 38, clipCount = 5, isNearby = true),
        Station("st-belmar", "Belmar", "NJ", listenersNow = 12, clipCount = 3, isNearby = true),
        Station("st-redbank", "Red Bank", "NJ", listenersNow = 9, clipCount = 4, isNearby = true),
        Station("st-neworleans", "New Orleans", "LA", listenersNow = 87, clipCount = 61, isNearby = false),
        Station("st-austin", "Austin", "TX", listenersNow = 64, clipCount = 48, isNearby = false),
        Station("st-dublin", "Dublin", "Ireland", listenersNow = 41, clipCount = 33, isNearby = false)
    )

    override fun activeCell(): GeoCell = GeoCell(
        h3Index = "882a1072b5fffff",
        label = "Asbury Park · Shore corridor",
        clipDensity = clips.size
    )

    override fun nearbyClips(category: ClipCategory, heardClipIds: Set<String>): List<AudioClip> {
        val filtered = clips
            .filter { category == ClipCategory.All || it.category == category }
            .filterNot { it.id in heardClipIds }

        return filtered.ifEmpty {
            clips.filter { category == ClipCategory.All || it.category == category }
        }
    }

    override fun stations(): List<Station> = seedStations

    override suspend fun forYouFeed(heardClipIds: Set<String>): List<AudioClip> =
        nearbyClips(ClipCategory.All, heardClipIds)

    // Station rotation is shared: every listener gets the same deterministic
    // order (oldest first), independent of personal heard history. All mock
    // stations serve the corridor seed clips until the backend exists.
    override suspend fun stationFeed(stationId: String): List<AudioClip> = clips.reversed()

    override suspend fun uploadClip(draft: ClipUploadDraft): Result<Unit> {
        delay(600)
        uploadedCount += 1
        return Result.success(Unit)
    }

    override suspend fun myClipCount(): Int = uploadedCount

    override fun mvpMetrics(): List<MvpMetric> = listOf(
        MvpMetric("Clip density", "${clips.size} in active cell", "4+ per H3 cell"),
        MvpMetric("Completion", "64% mock avg", "50%+"),
        MvpMetric("Early skips", "32% mock", "<40%"),
        MvpMetric("7-day return", "not measured", "30%+"),
        MvpMetric("Creator repeat", "not measured", "60%+")
    )

    override suspend fun recordListenEvent(event: ListenEventDraft) {
        events += event
    }
}
