package com.openair.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.net.toUri
import com.openair.app.domain.AudioClip
import com.openair.app.domain.ClipCategory
import com.openair.app.domain.FeedMode

/**
 * Full-screen now-playing feed. One clip per page, vertical swipe to move
 * through the queue, fixed transport controls, and a small For You / Station
 * switcher at the top. The clip is the screen; everything else stays minimal.
 */
@Composable
fun HomeScreen(state: NowPlayingState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen")
    ) {
        val queue = state.queue
        if (queue.isEmpty()) {
            if (state.isLoadingQueue) {
                Text(
                    text = "Tuning in…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                EmptyFeed(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            key(state.feedMode, state.station.id) {
                val pagerState = rememberPagerState(
                    initialPage = state.index.coerceIn(0, queue.lastIndex)
                ) { queue.size }

                LaunchedEffect(state.index) {
                    if (state.index in queue.indices && pagerState.currentPage != state.index) {
                        pagerState.animateScrollToPage(state.index)
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { page ->
                        if (page != state.index) state.onSwipedTo(page)
                    }
                }

                // Stations are live radio: the broadcast decides what plays
                // next, so the pager only moves when the rotation does.
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = state.feedMode == FeedMode.ForYou,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ClipPage(clip = queue[page])
                }
            }
            TransportControls(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        FeedSwitcher(state = state, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ClipPage(clip: AudioClip) {
    val accent = categoryAccent(clip.category)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.20f),
                    1f to MaterialTheme.colorScheme.background
                )
            )
    ) {
        ClipArtwork(
            clip = clip,
            accent = accent,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-48).dp)
        )

        ActionRail(
            clip = clip,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 168.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 76.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = clip.creatorHandle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = clip.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = clip.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${clip.locationLabel} · ${clip.publishedAgo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Discovery, not redistribution: ingested clips link back to the
            // publisher's full episode/track. The legal shield and the value
            // we return to the creator.
            clip.sourceLinkUrl?.let { link ->
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Full episode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipArtwork(clip: AudioClip, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(clip.category),
            contentDescription = null,
            tint = Color(0xE6111827),
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun ActionRail(clip: AudioClip, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = clip.creatorName.first().uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Like")
        }
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "Comments")
        }
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.Share, contentDescription = "Share")
        }
    }
}

@Composable
private fun TransportControls(state: NowPlayingState, modifier: Modifier = Modifier) {
    val clip = state.currentClip ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LinearProgressIndicator(
            progress = { (state.positionSeconds / clip.durationSeconds).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatClock(state.positionSeconds.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatClock(clip.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.feedMode == FeedMode.Station) {
                // Live radio: rewind builds a personal buffer; +30 only
                // catches back up toward the broadcast, never past it.
                LiveChip(
                    behindSeconds = state.behindLiveSeconds,
                    atLiveEdge = state.isAtLiveEdge,
                    onClick = state::goLive
                )
            } else {
                TextButton(onClick = state::cycleSpeed) {
                    Text(formatSpeed(state.speed), fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = state::skipBack) {
                Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds")
            }
            FilledIconButton(
                onClick = state::togglePlay,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(34.dp)
                )
            }
            if (state.feedMode == FeedMode.Station) {
                IconButton(onClick = state::skipForward, enabled = !state.isAtLiveEdge) {
                    Icon(Icons.Rounded.Forward30, contentDescription = "Catch up 30 seconds")
                }
            } else {
                IconButton(onClick = state::skipForward) {
                    Icon(Icons.Rounded.Forward30, contentDescription = "Forward 30 seconds")
                }
                IconButton(onClick = state::skipToNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next clip")
                }
            }
        }
    }
}

@Composable
private fun LiveChip(behindSeconds: Float, atLiveEdge: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !atLiveEdge) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (atLiveEdge) Color(0xFFFF5A4E) else MaterialTheme.colorScheme.onSurfaceVariant
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (atLiveEdge) "LIVE" else "−${formatClock(behindSeconds.toInt())}",
            fontWeight = FontWeight.Bold,
            color = if (atLiveEdge) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun FeedSwitcher(state: NowPlayingState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SwitcherLabel(
            text = "For You",
            selected = state.feedMode == FeedMode.ForYou,
            onClick = state::selectForYou
        )
        SwitcherLabel(
            text = state.station.name,
            selected = state.feedMode == FeedMode.Station,
            onClick = { state.selectStation(state.station) }
        )
    }
}

@Composable
private fun SwitcherLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        }
    )
}

@Composable
private fun EmptyFeed(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nothing nearby yet", fontWeight = FontWeight.Bold)
        Text(
            text = "Be the first to record something here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun categoryAccent(category: ClipCategory): Color = when (category) {
    ClipCategory.Music -> Color(0xFF8FD5C2)
    ClipCategory.FoodDrink -> Color(0xFFFFD166)
    ClipCategory.StoriesPeople -> Color(0xFFFF8A7A)
    ClipCategory.History -> Color(0xFFA5B4FC)
    ClipCategory.All -> Color(0xFF8FD5C2)
}

private fun categoryIcon(category: ClipCategory): ImageVector = when (category) {
    ClipCategory.Music -> Icons.Rounded.MusicNote
    ClipCategory.FoodDrink -> Icons.Rounded.Restaurant
    ClipCategory.StoriesPeople -> Icons.Rounded.RecordVoiceOver
    ClipCategory.History -> Icons.Rounded.HistoryEdu
    ClipCategory.All -> Icons.Rounded.GraphicEq
}

internal fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun formatSpeed(speed: Float): String {
    val label = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$label×"
}
