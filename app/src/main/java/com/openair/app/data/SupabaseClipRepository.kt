package com.openair.app.data

import android.util.Log
import com.openair.app.domain.AudioClip
import com.openair.app.domain.ClipCategory
import com.openair.app.domain.ClipUploadDraft
import com.openair.app.domain.GeoCell
import com.openair.app.domain.Geohash
import com.openair.app.domain.ListenEventDraft
import com.openair.app.domain.MvpMetric
import com.openair.app.domain.Station
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * Live repository against Supabase: published_clips view for the feed,
 * Storage signed URLs for audio, listen_events for telemetry, and clip
 * uploads into the manual-review pipeline.
 *
 * Falls back to bundled seed clips whenever the backend is unreachable or
 * empty, so Home always plays something.
 */
class SupabaseClipRepository(
    private val fallback: MockClipRepository = MockClipRepository()
) : ClipRepository {

    @Volatile
    private var listenerLat: Double? = null

    @Volatile
    private var listenerLng: Double? = null

    override fun updateListenerLocation(latitude: Double, longitude: Double) {
        listenerLat = latitude
        listenerLng = longitude
    }

    override fun activeCell(): GeoCell = fallback.activeCell()
    override fun stations(): List<Station> = fallback.stations()
    override fun nearbyClips(category: ClipCategory, heardClipIds: Set<String>): List<AudioClip> =
        fallback.nearbyClips(category, heardClipIds)

    override fun mvpMetrics(): List<MvpMetric> = fallback.mvpMetrics()

    override suspend fun forYouFeed(heardClipIds: Set<String>): List<AudioClip> {
        val published = fetchPublished(newestFirst = true)
        val fresh = published.filterNot { it.id in heardClipIds }.ifEmpty { published }
        return rankByProximity(fresh).ifEmpty { fallback.forYouFeed(heardClipIds) }
    }

    // Local-first with recency inside each ring: the query returns newest
    // first, and the stable sort by distance ring keeps that order within a
    // ring. When nothing is nearby the feed widens on its own — the close
    // rings are simply empty and farther clips surface.
    private fun rankByProximity(clips: List<AudioClip>): List<AudioClip> {
        if (listenerLat == null || listenerLng == null) return clips
        return clips.sortedBy { distanceRing(it.distanceKm) }
    }

    private fun distanceRing(distanceKm: Double?): Int = when {
        distanceKm == null -> 4 // untagged: below anything verifiably local
        distanceKm < 2 -> 0
        distanceKm < 8 -> 1
        distanceKm < 25 -> 2
        distanceKm < 80 -> 3
        distanceKm < 250 -> 4
        else -> 5
    }

    override suspend fun stationFeed(stationId: String): List<AudioClip> =
        fetchPublished(newestFirst = false).ifEmpty { fallback.stationFeed(stationId) }

    override suspend fun recordListenEvent(event: ListenEventDraft) {
        fallback.recordListenEvent(event)
        if (!event.clipId.looksLikeUuid()) return // seed clips are not in the DB
        val userId = OpenAirSupabase.ensureSignedIn() ?: return
        try {
            OpenAirSupabase.client.from("listen_events").insert(
                buildJsonObject {
                    put("clip_id", event.clipId)
                    put("listener_id", userId)
                    put("listen_duration_seconds", event.listenDurationSeconds)
                    put("completion_pct", event.completionPercent)
                    put("skipped_early", event.skippedEarly)
                    put("context", event.context)
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "listen event insert failed", t)
        }
    }

    override suspend fun uploadClip(draft: ClipUploadDraft): Result<Unit> {
        val userId = OpenAirSupabase.ensureSignedIn()
            ?: return Result.failure(IllegalStateException("Could not sign in — check Supabase config and network."))
        return withContext(Dispatchers.IO) {
            try {
                val file = File(draft.filePath)
                val objectPath = "$userId/${System.currentTimeMillis()}_${file.name}"
                OpenAirSupabase.client.storage.from(AUDIO_BUCKET).upload(objectPath, file.readBytes())
                OpenAirSupabase.client.from("clips").insert(
                    buildJsonObject {
                        put("creator_id", userId)
                        put("status", "pending_review")
                        put("ingestion_type", "recorded")
                        put("audio_path", objectPath)
                        put("title", draft.title)
                        draft.description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                        put("category", "other")
                        put("duration_seconds", draft.durationSeconds.coerceAtLeast(1))
                        draft.latitude?.let { put("lat_private", it) }
                        draft.longitude?.let { put("lng_private", it) }
                        put("location_label", draft.locationLabel)
                    }
                )
                file.delete()
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.w(TAG, "upload failed", t)
                Result.failure(t)
            }
        }
    }

    override suspend fun myClipCount(): Int {
        val userId = OpenAirSupabase.ensureSignedIn() ?: return 0
        return withContext(Dispatchers.IO) {
            try {
                OpenAirSupabase.client.from("clips")
                    .select(columns = Columns.list("id")) {
                        filter { eq("creator_id", userId) }
                    }
                    .decodeList<JsonObject>()
                    .size
            } catch (t: Throwable) {
                Log.w(TAG, "clip count failed", t)
                0
            }
        }
    }

    private suspend fun fetchPublished(newestFirst: Boolean): List<AudioClip> = withContext(Dispatchers.IO) {
        OpenAirSupabase.ensureSignedIn() ?: return@withContext emptyList()
        try {
            val rows = OpenAirSupabase.client.from("published_clips")
                .select {
                    order("published_at", if (newestFirst) Order.DESCENDING else Order.ASCENDING)
                    limit(50)
                }
                .decodeList<JsonObject>()
            if (rows.isEmpty()) return@withContext emptyList()

            val creatorIds = rows.mapNotNull { it.str("creator_id") }.distinct()
            val creators = if (creatorIds.isEmpty()) emptyMap() else {
                OpenAirSupabase.client.from("profiles")
                    .select {
                        filter { isIn("id", creatorIds) }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { p -> p.str("id")?.let { it to p } }
                    .toMap()
            }
            rows.mapNotNull { row -> toAudioClip(row, creators) }
        } catch (t: Throwable) {
            Log.w(TAG, "feed fetch failed", t)
            emptyList()
        }
    }

    private suspend fun toAudioClip(row: JsonObject, creators: Map<String, JsonObject>): AudioClip? {
        val id = row.str("id") ?: return null
        val path = row.str("normalized_audio_path") ?: row.str("audio_path") ?: return null
        val signedUrl = try {
            OpenAirSupabase.client.storage.from(AUDIO_BUCKET).createSignedUrl(path, 60.minutes)
        } catch (t: Throwable) {
            Log.w(TAG, "sign url failed for $path", t)
            return null
        }
        val creator = creators[row.str("creator_id")]
        val displayName = creator?.str("display_name") ?: "Local creator"
        val handle = creator?.str("handle")?.let { "@$it" }
            ?: "@${displayName.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "local" }}"
        val distanceKm = distanceToCell(row.str("geohash5"))
        val baseLabel = row.str("location_label") ?: "Nearby"
        return AudioClip(
            id = id,
            title = row.str("title") ?: "Untitled clip",
            creatorName = displayName,
            creatorHandle = handle,
            category = when (row.str("category")) {
                "food_drink" -> ClipCategory.FoodDrink
                "stories_people" -> ClipCategory.StoriesPeople
                "music" -> ClipCategory.Music
                "history" -> ClipCategory.History
                else -> ClipCategory.All
            },
            locationLabel = if (distanceKm != null && distanceKm >= 10) {
                "$baseLabel · ${distanceKm.roundToInt()} km away"
            } else {
                baseLabel
            },
            durationSeconds = (row["duration_seconds"]?.jsonPrimitive?.intOrNull ?: 60).coerceAtLeast(1),
            summary = row.str("description") ?: "",
            publishedAgo = agoLabel(row.str("published_at")),
            audioUrl = signedUrl,
            completionRate = (row["completion_rate"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 100.0,
            distanceKm = distanceKm
        )
    }

    /** Listener position to the clip's public geohash cell center, in km. */
    private fun distanceToCell(geohash5: String?): Double? {
        val lat = listenerLat ?: return null
        val lng = listenerLng ?: return null
        val cell = geohash5 ?: return null
        val (cellLat, cellLng) = Geohash.decodeCenter(cell) ?: return null
        return Geohash.distanceKm(lat, lng, cellLat, cellLng)
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun String.looksLikeUuid(): Boolean =
        matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))

    private fun agoLabel(publishedAt: String?): String {
        if (publishedAt == null) return "just now"
        return try {
            val elapsed = Duration.between(OffsetDateTime.parse(publishedAt).toInstant(), java.time.Instant.now())
            when {
                elapsed.toMinutes() < 1 -> "just now"
                elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} min ago"
                elapsed.toHours() < 24 -> "${elapsed.toHours()} hr ago"
                else -> "${elapsed.toDays()} days ago"
            }
        } catch (_: Throwable) {
            "recently"
        }
    }

    companion object {
        private const val TAG = "SupabaseClips"
        private const val AUDIO_BUCKET = "audio"
    }
}
