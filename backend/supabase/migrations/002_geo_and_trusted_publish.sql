-- 002: server-side geotagging + trusted-creator auto-publish.
--
-- Geo: clips get a public 5-char geohash cell (~4.9 km square) computed in
-- the database from the private coordinates. Clients never see lat/lng —
-- only the coarse cell, which feeds proximity ranking and honest "N km
-- away" labels. Precision 5 keeps a home recording inside a cell shared
-- with a whole neighborhood.
--
-- Moderation: profiles gain a 'trusted' flag (set only via dashboard/SQL —
-- the column grant below does NOT include it, so clients can't flip it).
-- Clips from trusted creators publish instantly on insert; everyone else
-- still lands in pending_review.

create extension if not exists postgis;

alter table public.clips
  add column if not exists geohash5 text
  generated always as (
    case
      when lat_private is null or lng_private is null then null
      else st_geohash(st_setsrid(st_makepoint(lng_private, lat_private), 4326), 5)
    end
  ) stored;

create index if not exists clips_feed_geohash_idx
  on public.clips (geohash5, published_at desc)
  where status = 'published';

-- Re-create the listener-facing view with the coarse cell appended (the
-- original column order is preserved; create-or-replace keeps the grants).
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
    geohash5
  from public.clips
  where status = 'published';

alter table public.profiles
  add column if not exists trusted boolean not null default false;

-- Trusted creators skip review. AFTER INSERT (not BEFORE) so the client's
-- RLS insert check still sees the allowed 'pending_review' status; this
-- definer function then flips it as the table owner.
create or replace function public.auto_publish_trusted()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'pending_review' and exists (
    select 1 from profiles where id = new.creator_id and trusted
  ) then
    update clips
      set status = 'published', published_at = now()
      where id = new.id;
  end if;
  return null;
end;
$$;

drop trigger if exists clips_auto_publish_trusted on public.clips;
create trigger clips_auto_publish_trusted
  after insert on public.clips
  for each row execute function public.auto_publish_trusted();
