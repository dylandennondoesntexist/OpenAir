package com.openair.app.data

import com.openair.app.domain.ClipCategory
import com.openair.app.domain.ListenEventDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockClipRepositoryTest {
    private val repository = MockClipRepository()

    @Test
    fun activeCell_hasEnoughSeedClipsForMvpSmokeTest() {
        val cell = repository.activeCell()

        assertEquals("Asbury Park · Shore corridor", cell.label)
        assertTrue("MVP corridor cell should start at or above the density target", cell.clipDensity >= 4)
    }

    @Test
    fun nearbyClips_allCategoryReturnsRecencyOrderedSeedClips() {
        val clips = repository.nearbyClips(ClipCategory.All, heardClipIds = emptySet())

        assertEquals(5, clips.size)
        assertEquals("The diner booth Springsteen used to haunt", clips.first().title)
    }

    @Test
    fun nearbyClips_filtersByCategory() {
        val clips = repository.nearbyClips(ClipCategory.Music, heardClipIds = emptySet())

        assertEquals(1, clips.size)
        assertEquals(ClipCategory.Music, clips.single().category)
        assertEquals("Northbound Static", clips.single().creatorName)
    }

    @Test
    fun nearbyClips_excludesRecentlyHeardClipsWhenAlternativesExist() {
        val heardClipId = "asbury-boardwalk-01"
        val clips = repository.nearbyClips(ClipCategory.All, heardClipIds = setOf(heardClipId))

        assertFalse(clips.any { it.id == heardClipId })
        assertEquals(4, clips.size)
    }

    @Test
    fun nearbyClips_allowsRepeatsWhenFilteredQueueWouldBeEmpty() {
        val musicClipId = "route35-demo-03"
        val clips = repository.nearbyClips(ClipCategory.Music, heardClipIds = setOf(musicClipId))

        assertEquals(1, clips.size)
        assertEquals(musicClipId, clips.single().id)
    }

    @Test
    fun stations_listNearbyBeforeWorldwide() {
        val stations = repository.stations()

        assertEquals("Asbury Park", stations.first().name)
        assertTrue("Explore needs nearby stations", stations.any { it.isNearby })
        assertTrue("Explore needs worldwide stations", stations.any { !it.isNearby })
    }

    @Test
    fun forYouFeed_excludesHeardClips() = runBlocking {
        val heardClipId = "asbury-boardwalk-01"
        val clips = repository.forYouFeed(heardClipIds = setOf(heardClipId))

        assertFalse(clips.any { it.id == heardClipId })
        assertEquals(4, clips.size)
    }

    @Test
    fun stationFeed_returnsSharedOldestFirstRotation() = runBlocking {
        val clips = repository.stationFeed("st-asbury")

        assertEquals(5, clips.size)
        assertEquals("Why locals still call it South Belmar", clips.first().title)
    }

    @Test
    fun recordListenEvent_acceptsAutoplayCompletionEvent() = runBlocking {
        repository.recordListenEvent(
            ListenEventDraft(
                clipId = "asbury-boardwalk-01",
                listenDurationSeconds = 74,
                completionPercent = 100.0,
                skippedEarly = false,
                context = "autoplay"
            )
        )

        assertTrue(true)
    }
}
