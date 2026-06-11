-- Open Air MVP schema (Supabase-direct revision).
--
-- The Android app talks to Supabase directly with the anon/publishable key,
-- so this migration carries its own security model:
--   * RLS enabled on every table; no table is exposed by default.
--   * Explicit grants only (create the project with "automatically expose
--     new tables" DISABLED and "automatic RLS" ENABLED).
--   * Exact creator coordinates (lat_private/lng_private) are never readable
--     by clients: listeners read the published_clips view, which exposes
--     only safe columns. Creators may read their own rows in full.
--   * Moderation stays service-role only (Supabase dashboard for now).

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Profiles: one row per auth user (anonymous or email), created by trigger.
-- ---------------------------------------------------------------------------

create table if not exists profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  handle text unique,
  display_name text not null default 'Local listener',
  creator_bio text,
  created_at timestamptz not null default now()
);

alter table profiles enable row level security;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

grant select on profiles to anon, authenticated;
grant update (handle, display_name, creator_bio) on profiles to authenticated;

drop policy if exists "profiles are public" on profiles;
create policy "profiles are public"
  on profiles for select
  to anon, authenticated
  using (true);

drop policy if exists "users update own profile" on profiles;
create policy "users update own profile"
  on profiles for update
  to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Clips. Audio lives in the private 'audio' storage bucket; *_path columns
-- are object paths within it. Cell columns are nullable until the app ships
-- H3 tagging; lat/lng stay private to the creator and the review process.
-- ---------------------------------------------------------------------------

create table if not exists clips (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references profiles(id),
  status text not null default 'pending_review'
    check (status in ('pending_upload', 'pending_review', 'published', 'rejected', 'manual_review')),
  rejection_reason text,
  ingestion_type text not null
    check (ingestion_type in ('recorded', 'device_upload', 'admin_url_import', 'admin_rss_import')),
  source_url text,
  audio_path text,
  normalized_audio_path text,
  title text,
  description text,
  category text not null default 'other'
    check (category in ('food_drink', 'stories_people', 'music', 'history', 'other')),
  duration_seconds integer check (duration_seconds between 1 and 3600),
  lat_private double precision,
  lng_private double precision,
  h3_index_r8 text,
  h3_index_r6 text,
  h3_centroid_lat double precision,
  h3_centroid_lng double precision,
  location_label text,
  transcript text,
  transcript_confidence numeric(4,3),
  ai_summary text,
  listen_count integer not null default 0,
  skip_count integer not null default 0,
  completion_rate numeric(5,2) not null default 0,
  created_at timestamptz not null default now(),
  published_at timestamptz
);

alter table clips enable row level security;

create index if not exists clips_feed_r8_idx
  on clips (h3_index_r8, category, published_at desc)
  where status = 'published';

create index if not exists clips_creator_status_idx
  on clips (creator_id, status, created_at desc);

-- Creators: full access to their own rows; cannot self-publish.
grant select, insert on clips to authenticated;
grant update (title, description, category, location_label, duration_seconds, audio_path)
  on clips to authenticated;

drop policy if exists "creators read own clips" on clips;
create policy "creators read own clips"
  on clips for select
  to authenticated
  using (creator_id = (select auth.uid()));

drop policy if exists "creators insert unpublished clips" on clips;
create policy "creators insert unpublished clips"
  on clips for insert
  to authenticated
  with check (
    creator_id = (select auth.uid())
    and status in ('pending_upload', 'pending_review')
  );

drop policy if exists "creators update own unpublished clips" on clips;
create policy "creators update own unpublished clips"
  on clips for update
  to authenticated
  using (creator_id = (select auth.uid()) and status <> 'published')
  with check (creator_id = (select auth.uid()));

-- Listeners: published clips only, safe columns only. The view runs as its
-- owner (postgres) on purpose — it is the column-privacy boundary that keeps
-- lat_private/lng_private out of client reach.
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
    published_at
  from public.clips
  where status = 'published';

grant select on public.published_clips to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Listen events: write-only telemetry from clients, partitioned by month.
-- Context values match what the app records (NowPlayingState).
-- ---------------------------------------------------------------------------

create table if not exists listen_events (
  id uuid not null default gen_random_uuid(),
  clip_id uuid not null references clips(id),
  listener_id uuid references profiles(id),
  listener_device_id text,
  listen_duration_seconds integer not null check (listen_duration_seconds >= 0),
  completion_pct numeric(5,2) not null check (completion_pct between 0 and 100),
  skipped_early boolean not null,
  context text not null
    check (context in ('autoplay', 'swipe', 'skip', 'seek', 'catchup', 'manual_replay', 'exploration_slot')),
  h3_at_listen text,
  listened_at timestamptz not null default now(),
  primary key (id, listened_at)
) partition by range (listened_at);

alter table listen_events enable row level security;

create table if not exists listen_events_2026_06
  partition of listen_events
  for values from ('2026-06-01') to ('2026-07-01');

create table if not exists listen_events_2026_07
  partition of listen_events
  for values from ('2026-07-01') to ('2026-08-01');

alter table listen_events_2026_06 enable row level security;
alter table listen_events_2026_07 enable row level security;

create index if not exists listen_events_clip_time_idx
  on listen_events (clip_id, listened_at desc);

grant insert on listen_events to authenticated;

drop policy if exists "listeners insert own events" on listen_events;
create policy "listeners insert own events"
  on listen_events for insert
  to authenticated
  with check (listener_id is null or listener_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Manual review queue: service-role only (no grants, no policies).
-- ---------------------------------------------------------------------------

create table if not exists manual_review_queue (
  id uuid primary key default gen_random_uuid(),
  clip_id uuid not null references clips(id),
  reason text not null,
  assigned_to text,
  resolved_at timestamptz,
  created_at timestamptz not null default now()
);

alter table manual_review_queue enable row level security;

-- ---------------------------------------------------------------------------
-- Storage: private 'audio' bucket. Creators upload under their own user-id
-- folder ("<auth.uid()>/<clip-id>.m4a"); any signed-in listener can read
-- (playback uses signed URLs created client-side, which require select).
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('audio', 'audio', false)
on conflict (id) do nothing;

drop policy if exists "creators upload own audio" on storage.objects;
create policy "creators upload own audio"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'audio'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

drop policy if exists "signed-in listeners read audio" on storage.objects;
create policy "signed-in listeners read audio"
  on storage.objects for select
  to authenticated
  using (bucket_id = 'audio');
