# Content ingestion (cold-start seeding)

Open Air seeds the feed with **public, opt-in** Podcasting 2.0 audio so the app
isn't empty before locals start recording. This is a *complement* to UGC, not a
replacement — real local recordings are the product; ingested clips fill the map
while density builds.

## What we ingest (and what we never touch)

| Source | Status | Why |
| --- | --- | --- |
| `<podcast:soundbite>` highlights from geotagged shows | ✅ | The tag is an explicit invitation to play that slice of an episode. |
| `<podcast:medium>music` feeds with a location (Wavlake, Castopod, …) | ✅ | Independent artists publish these for open, third-party players. This is the on-brand "local bands" layer. |
| Bandcamp / SoundCloud scraping | ❌ | Their ToS forbid automated extraction; re-streaming is infringement. |
| Re-hosting anyone's audio in our bucket | ❌ | We hotlink only (see below). |

## The four rules that keep this legal

1. **Hotlink, never re-host.** Ingested audio streams from the publisher's
   `enclosureUrl`, stored in `clips.remote_audio_url`. We never download it.
   (`audio_path` stays null for ingested rows; `remote_audio_url` stays null for
   user uploads — they're mutually exclusive in code.)
2. **Attribute + link back.** Every ingested clip shows the source name and a
   **"Full episode"** button (`source_link_url`). The app is a discovery player.
3. **Don't strip ads.** Streaming from the publisher's URL means their host
   serves their dynamic ads and counts the play — we help them monetize.
4. **Honor opt-out.** The crawler skips any feed listed in `blocked_feeds`, and
   a publisher's URL there lets you purge their clips in one statement.

## Location honesty

`<podcast:location>` is **show-level and coarse** — where a podcast is based, not
where a clip was recorded. So ingested clips are tagged with the show's location
(stored in `lat_private`/`lng_private`, exposed only as the ~5 km `geohash5`
cell). Because the For You feed ranks by distance ring, precise local recordings
naturally outrank city-centroid podcast clips. Ingested clips are always labeled
with their source, so a national show never masquerades as a neighbor.

## Running the crawler

Ingestion runs **server-side** with the service-role key (clients can never
insert these rows). It lives in `qa/tools/ingest_podcastindex.py`.

```bash
pip install requests

# 1. Offline sanity check — no network, no credentials:
python qa/tools/ingest_podcastindex.py --self-test

# 2. Get free Podcast Index credentials at https://api.podcastindex.org and put
#    PODCAST_INDEX_API_KEY / PODCAST_INDEX_API_SECRET in backend/.env, along
#    with SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (dashboard → API).

# 3a. Measure how much geotagged content actually exists (writes nothing).
#     Use this to validate any "N geotagged feeds" claim with real data:
python qa/tools/ingest_podcastindex.py --medium music --max 1000 --scan
python qa/tools/ingest_podcastindex.py --medium podcast --max 1000 --scan

# 3b. Preview what a real pull would insert (writes nothing):
python qa/tools/ingest_podcastindex.py --medium music --max 50 --dry-run

# 4. Write. Rows land published and live immediately; re-runs dedupe.
python qa/tools/ingest_podcastindex.py --medium music --max 200
python qa/tools/ingest_podcastindex.py --medium podcast --max 200
```

`--medium music` is the recommended starting point: it's the most on-brand
content and the cleanest license. Validate the *real* volume empirically — the
numbers floating around online for "geotagged open feeds" look inflated.

## Do you even need the Podcast Index API?

The **app never uses it** — it only talks to Supabase and streams audio from
publisher URLs. The API is a build-time *discovery* tool, used only by the
crawler to answer "which feeds, out of ~4.7M, are geotagged and have
soundbites/music?" Two ways to populate the DB:

- **Curated launch (no API key):** hand-pick a handful of local feeds (NJ shows,
  specific Wavlake artists), list their RSS URLs, and parse those directly. Total
  control, nothing to sign up for — ideal for a focused Asbury Park start.
- **Automated discovery (free key):** "find everything geotagged anywhere." This
  is the only thing that needs the Podcast Index key.

## Schema

`backend/supabase/migrations/003_ingested_content.sql`:

- `clips.creator_id` is now nullable (ingested rows have no app author).
- New columns: `remote_audio_url`, `soundbite_start_seconds`,
  `soundbite_duration_seconds`, `source_feed_title`, `source_episode_title`,
  `source_link_url`, `source_feed_url`, `attribution`.
- `published_clips` exposes the safe additions (attribution, hotlink URL,
  soundbite timing) — never `lat_private`/`lng_private`.
- `blocked_feeds` (service-role only) is the takedown / opt-out list.

The app plays a soundbite by clipping the source file to
`[soundbite_start_seconds, +soundbite_duration_seconds]` via Media3
`ClippingConfiguration`; whole tracks and user clips play in full.

## Productionization (later)

The Python script is fine for periodic seeding (run it on a cron). When you want
ingestion to be continuous and self-serve, move this logic into a Supabase Edge
Function on a schedule — same transforms, same rules.
