package com.openair.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.openair.app.data.ClipRepository
import com.openair.app.domain.AudioClip
import com.openair.app.domain.FeedMode
import com.openair.app.domain.ListenEventDraft
import com.openair.app.domain.Station
import com.openair.app.domain.StationSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single source of truth for the now-playing session. Hoisted above the tab
 * navigation so playback continues while the listener browses other tabs.
 *
 * When a Media3 [Player] (MediaController) is attached, it is the engine:
 * transport methods forward to it and a poll loop mirrors its position back
 * into Compose state. Before attach (previews), state falls back to a
 * simulated clock so the UI still moves.
 *
 * Feed semantics differ by mode:
 *  - For You: on-demand. Swipe, skip, seek, and speed are all allowed.
 *  - Station: live radio on a shared clock. Tuning in seeks to the live
 *    point. Rewinding builds a personal buffer (like live TV); forward
 *    seeking is clamped to the live edge and never passes it. No skipping,
 *    no speed control.
 */
@Stable
class NowPlayingState(
    private val repository: ClipRepository,
    private val scope: CoroutineScope
) {
    var feedMode by mutableStateOf(FeedMode.ForYou)
        private set
    var station by mutableStateOf(repository.stations().first())
        private set
    var queue by mutableStateOf<List<AudioClip>>(emptyList())
        private set
    var isLoadingQueue by mutableStateOf(true)
        private set
    var index by mutableIntStateOf(0)
        private set
    var heardClipIds by mutableStateOf(setOf<String>())
        private set

    var isPlaying by mutableStateOf(true)
        private set
    var positionSeconds by mutableFloatStateOf(0f)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** How far this listener trails the shared broadcast; 0 at the live edge. */
    var behindLiveSeconds by mutableFloatStateOf(0f)
        private set
    val isAtLiveEdge: Boolean
        get() = behindLiveSeconds <= LIVE_EDGE_TOLERANCE_SECONDS

    val currentClip: AudioClip?
        get() = queue.getOrNull(index)

    private var player: Player? = null
    private var pendingSeekContext: String? = null
    private var suppressEvents = false

    init {
        loadForYou(initialHeard = emptySet())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            val newIndex = p.currentMediaItemIndex
            if (suppressEvents || newIndex == index) return
            val previous = currentClip
            if (previous != null) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    positionSeconds = previous.durationSeconds.toFloat()
                    finishClip(previous, context = "autoplay")
                } else {
                    finishClip(previous, context = pendingSeekContext ?: "seek")
                }
            }
            pendingSeekContext = null
            index = newIndex
            positionSeconds = (p.currentPosition.coerceAtLeast(0L)) / 1000f
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                currentClip?.let { clip ->
                    positionSeconds = clip.durationSeconds.toFloat()
                    finishClip(clip, context = "autoplay")
                }
            }
        }
    }

    fun attachPlayer(target: Player) {
        if (player === target) return
        player = target
        target.addListener(playerListener)
        when (feedMode) {
            FeedMode.ForYou -> loadPlaylist(
                startIndex = index,
                startPositionMs = (positionSeconds * 1000).toLong(),
                loop = false
            )
            FeedMode.Station -> tuneToLive()
        }
    }

    fun detachPlayer() {
        player?.removeListener(playerListener)
        player = null
    }

    fun selectForYou() {
        if (feedMode == FeedMode.ForYou) return
        feedMode = FeedMode.ForYou
        loadForYou(initialHeard = heardClipIds)
    }

    fun selectStation(target: Station) {
        if (feedMode == FeedMode.Station && station.id == target.id) return
        feedMode = FeedMode.Station
        station = target
        setSpeedInternal(1f)
        isLoadingQueue = true
        scope.launch {
            queue = repository.stationFeed(target.id)
            isLoadingQueue = false
            isPlaying = true
            tuneToLive()
        }
    }

    private fun loadForYou(initialHeard: Set<String>) {
        isLoadingQueue = true
        scope.launch {
            queue = repository.forYouFeed(initialHeard)
            index = 0
            positionSeconds = 0f
            behindLiveSeconds = 0f
            isLoadingQueue = false
            isPlaying = true
            loadPlaylist(startIndex = 0, startPositionMs = 0L, loop = false)
        }
    }

    /** Jump (back) to the live broadcast point of the current station. */
    fun goLive() {
        if (feedMode != FeedMode.Station) return
        tuneToLive()
    }

    fun togglePlay() {
        val p = player ?: run {
            isPlaying = !isPlaying
            return
        }
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
            p.play()
        }
    }

    fun cycleSpeed() {
        if (feedMode == FeedMode.Station) return
        setSpeedInternal(
            when (speed) {
                1f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2f
                2f -> 0.75f
                else -> 1f
            }
        )
    }

    fun skipBack() {
        seekWithinClip((currentPositionSeconds() - 10f).coerceAtLeast(0f))
    }

    fun skipForward() {
        val clip = currentClip ?: return
        if (feedMode == FeedMode.Station) {
            // Live radio: +30 only catches you up toward the broadcast, never past it.
            if (isAtLiveEdge) return
            val target = StationSchedule.globalSeconds(queue, index, currentPositionSeconds()) +
                minOf(30f, behindLiveSeconds)
            val point = StationSchedule.pointAt(queue, target)
            seekToClip(point.index, point.offsetSeconds, context = "catchup")
        } else {
            seekWithinClip((currentPositionSeconds() + 30f).coerceAtMost(clip.durationSeconds.toFloat()))
        }
    }

    fun skipToNext() {
        if (feedMode == FeedMode.Station) return
        if (index < queue.lastIndex) seekToClip(index + 1, 0f, context = "skip")
    }

    /** Called when the listener swipes the pager to a different clip (For You only). */
    fun onSwipedTo(page: Int) {
        if (feedMode == FeedMode.Station) return
        if (page in queue.indices && page != index) seekToClip(page, 0f, context = "swipe")
    }

    /** Poll loop body: mirror the real player, or simulate when no player is attached. */
    fun onPollTick(elapsedSeconds: Float) {
        val p = player
        if (p != null) {
            if (p.mediaItemCount > 0 && p.currentMediaItemIndex != index) {
                index = p.currentMediaItemIndex
            }
            positionSeconds = (p.currentPosition.coerceAtLeast(0L)) / 1000f
        } else if (isPlaying) {
            simulateTick(elapsedSeconds)
        }
        if (feedMode == FeedMode.Station) {
            behindLiveSeconds = StationSchedule.behindLiveSeconds(queue, index, positionSeconds)
        }
    }

    private fun currentPositionSeconds(): Float {
        val p = player ?: return positionSeconds
        return (p.currentPosition.coerceAtLeast(0L)) / 1000f
    }

    private fun tuneToLive() {
        val live = StationSchedule.livePoint(queue)
        index = live.index
        positionSeconds = live.offsetSeconds
        behindLiveSeconds = 0f
        loadPlaylist(
            startIndex = live.index,
            startPositionMs = (live.offsetSeconds * 1000).toLong(),
            loop = true
        )
    }

    private fun loadPlaylist(startIndex: Int, startPositionMs: Long, loop: Boolean) {
        if (queue.isEmpty()) return
        val p = player ?: return
        suppressEvents = true
        p.setMediaItems(queue.map { it.toMediaItem() }, startIndex, startPositionMs)
        p.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        p.prepare()
        p.playWhenReady = true
        suppressEvents = false
    }

    private fun seekWithinClip(targetSeconds: Float) {
        positionSeconds = targetSeconds
        player?.seekTo((targetSeconds * 1000).toLong())
    }

    private fun seekToClip(targetIndex: Int, offsetSeconds: Float, context: String) {
        val p = player
        if (p != null && targetIndex != index) {
            pendingSeekContext = context
            p.seekTo(targetIndex, (offsetSeconds * 1000).toLong())
        } else {
            if (targetIndex != index) {
                currentClip?.let { finishClip(it, context) }
                index = targetIndex
            }
            positionSeconds = offsetSeconds
            p?.seekTo((offsetSeconds * 1000).toLong())
        }
    }

    private fun setSpeedInternal(value: Float) {
        speed = value
        player?.setPlaybackSpeed(value)
    }

    private fun simulateTick(elapsedSeconds: Float) {
        val clip = currentClip ?: return
        positionSeconds += elapsedSeconds * speed
        if (positionSeconds >= clip.durationSeconds) {
            positionSeconds = clip.durationSeconds.toFloat()
            finishClip(clip, context = "autoplay")
            if (index < queue.lastIndex) {
                index += 1
            } else if (feedMode == FeedMode.Station) {
                index = 0
            } else {
                isPlaying = false
            }
            positionSeconds = 0f
        }
    }

    private fun finishClip(clip: AudioClip, context: String) {
        val completion = (positionSeconds / clip.durationSeconds.coerceAtLeast(1))
            .coerceIn(0f, 1f)
        heardClipIds = heardClipIds + clip.id
        val event = ListenEventDraft(
            clipId = clip.id,
            listenDurationSeconds = positionSeconds.toInt(),
            completionPercent = completion * 100.0,
            skippedEarly = completion < 0.5f && context != "autoplay",
            context = context
        )
        scope.launch { repository.recordListenEvent(event) }
    }

    private fun AudioClip.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("$creatorHandle · $locationLabel")
                .setDescription(summary)
                .build()
        )
        .build()

    companion object {
        const val LIVE_EDGE_TOLERANCE_SECONDS = 4f
    }
}

@Composable
fun rememberNowPlayingState(repository: ClipRepository): NowPlayingState {
    val scope = rememberCoroutineScope()
    return remember(repository) { NowPlayingState(repository, scope) }
}
