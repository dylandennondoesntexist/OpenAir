-- 003: ingested open audio (Podcasting 2.0) alongside user recordings.
--
-- Cold-start seeding from PUBLIC, OPT-IN sources only:
--   * <podcast:soundbite> highlights (the tag is an explicit invitation to
--     play a clip of an episode), and
--   * <podcast:medium>music feeds (published for open third-party players).
--
-- LEGAL POSTURE — enforced by how this data is shaped:
--   * We HOTLINK. Ingested audio lives at the publisher's enclosure URL in
--     `remote_audio_url`; we never copy it into our Storage bucket. (audio_path
--     stays null for ingested rows; remote_audio_url stays null for UGC.)
--   * We ATTRIBUTE and link back: source_feed_title + source_link_url drive a
--     "Full episode" CTA, so the app is a discovery player, not a re-distributor.
--   * Ads are preserved automatically because playback streams from the
--     publisher's own URL (their host inserts/serves the ads).
-- Ingestion runs server-side with the service-role key (see
-- qa/tools/ingest_podcastindex.py); clients can never insert these rows.

-- Ingested clips have no app user as author.
alter table public.clips alter column creator_id drop not null;

alter table public.clips
  add column if not exists remote_audio_url text,
  add column if not exists soundbite_start_seconds numeric(9,2),
  add column if not exists soundbite_duration_seconds numeric(9,2),
  add column if not exists source_feed_title text,
  add column if not exists source_episode_title text,
  add column if not exists source_link_url text,
  add column if not exists source_feed_url text,
  add column if not exists attribution text;

-- Every row is either user content (has a creator) or an admin import.
alter table public.clips drop constraint if exists clips_creator_or_ingested;
alter table public.clips add constraint clips_creator_or_ingested check (
  creator_id is not null
  or ingestion_type in ('admin_url_import', 'admin_rss_import')
);

-- Idempotent ingestion: a clip is one soundbite (or whole track) of one
-- remote file. Re-running the crawler skips rows it already has.
create unique index if not exists clips_remote_soundbite_key
  on public.clips (remote_audio_url, soundbite_start_seconds)
  where remote_audio_url is not null;

-- Listener-facing view: append the new safe columns (attribution + public
-- hotlink + soundbite timing). lat/lng_private remain excluded; ingested
-- rows expose only the coarse geohash5 cell, same as UGC.
create or replace view public.published_clips as
  select
    id,
    creator_id,
    title,
    description,
    category,
    duration_seconds,
    h3_index_r8,
    h3_index_r6,
    h3_centroid_lat,
    h3_centroid_lng,
    location_label,
    audio_path,
    normalized_audio_path,
    listen_count,
    completion_rate,
    published_at,
    geohash5,
    ingestion_type,
    remote_audio_url,
    soundbite_start_seconds,
    soundbite_duration_seconds,
    source_feed_title,
    source_link_url,
    attribution
  from public.clips
  where status = 'published';

grant select on public.published_clips to anon, authenticated;

-- Takedown / opt-out: feeds a publisher asks us to drop. The crawler checks
-- this list before inserting, and a row here lets you purge in one statement:
--   delete from clips where source_feed_url in (select feed_url from blocked_feeds);
create table if not exists public.blocked_feeds (
  feed_url text primary key,
  reason text,
  created_at timestamptz not null default now()
);

alter table public.blocked_feeds enable row level security;
-- No grants: service-role only.
