#!/usr/bin/env python3
"""
Open Air content ingestion — Podcast Index → Supabase (cold-start seeding).

LEGAL SCOPE (only opt-in, openly-licensed sources):
  * medium=music feeds (Wavlake/Castopod et al.) that declare a location —
    independent artists who publish for open, third-party players.
  * Podcast episodes that declare <podcast:soundbite> highlights — the tag is
    an explicit invitation to play that slice of the episode.

We HOTLINK. The enclosure URL is stored and streamed directly; we never
download or re-host audio. Each clip carries attribution and a link back to
the source, so Open Air is a discovery player, not a redistributor. We never
touch Bandcamp/SoundCloud or any feed that asks to be excluded.

Usage:
  # offline check of the transform logic — no network, no credentials:
  python ingest_podcastindex.py --self-test

  # see what a real pull would insert, without writing:
  python ingest_podcastindex.py --medium music --max 50 --dry-run

  # actually write (needs PODCAST_INDEX_* and SUPABASE_* in backend/.env):
  python ingest_podcastindex.py --medium music --max 200
  python ingest_podcastindex.py --medium podcast --max 200

Credentials (backend/.env, gitignored):
  PODCAST_INDEX_API_KEY, PODCAST_INDEX_API_SECRET   (free: api.podcastindex.org)
  SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY           (service role — server only)

Requires: requests  (pip install requests)
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys
import time
import dataclasses
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Iterable, Optional

PI_BASE = "https://api.podcastindex.org/api/1.0"
USER_AGENT = "OpenAir-Ingest/1.0 (+https://github.com/dylandennondoesntexist/OpenAir)"

# A soundbite shorter/longer than this is probably mistagged; skip it.
MIN_SOUNDBITE_SECONDS = 5.0
MAX_SOUNDBITE_SECONDS = 600.0


@dataclass
class IngestClip:
    """One row destined for public.clips. creator_id stays null (no app user)."""
    status: str
    ingestion_type: str
    category: str
    title: str
    remote_audio_url: str
    source_feed_title: str
    source_feed_url: str
    source_link_url: Optional[str]
    source_episode_title: Optional[str]
    attribution: str
    duration_seconds: int
    soundbite_start_seconds: Optional[float]
    soundbite_duration_seconds: Optional[float]
    lat_private: Optional[float]
    lng_private: Optional[float]
    location_label: Optional[str]


# --------------------------------------------------------------------------
# Pure transforms (covered by --self-test; no network).
# --------------------------------------------------------------------------

_GEO_RE = re.compile(r"geo:\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)")


def parse_geo(location: Any) -> tuple[Optional[float], Optional[float], Optional[str]]:
    """
    Pull (lat, lng, label) out of a Podcast Index `location` value. The API
    returns either a string ("geo:30.26,-97.74") or an object with `geo`/`name`.
    Returns (None, None, label?) when there are no usable coordinates.
    """
    if not location:
        return None, None, None
    geo_str = None
    label = None
    if isinstance(location, dict):
        geo_str = location.get("geo") or location.get("osm")
        label = location.get("name") or location.get("locality")
    elif isinstance(location, str):
        geo_str = location
        label = location
    if not geo_str:
        return None, None, label
    m = _GEO_RE.search(geo_str)
    if not m:
        return None, None, label
    lat, lng = float(m.group(1)), float(m.group(2))
    if not (-90 <= lat <= 90 and -180 <= lng <= 180):
        return None, None, label
    return lat, lng, label


def soundbite_clips_from_episode(
    episode: dict, feed_title: str, feed_url: str, location: Any
) -> list[IngestClip]:
    """Build one clip per valid <podcast:soundbite> on an episode."""
    enclosure = episode.get("enclosureUrl")
    if not enclosure:
        return []
    bites = episode.get("soundbites") or episode.get("soundbite") or []
    if isinstance(bites, dict):
        bites = [bites]
    lat, lng, label = parse_geo(location)
    out: list[IngestClip] = []
    for bite in bites:
        try:
            start = float(bite.get("startTime"))
            dur = float(bite.get("duration"))
        except (TypeError, ValueError):
            continue
        if not (MIN_SOUNDBITE_SECONDS <= dur <= MAX_SOUNDBITE_SECONDS):
            continue
        out.append(
            IngestClip(
                status="published",
                ingestion_type="admin_rss_import",
                category="stories_people",
                title=(bite.get("title") or episode.get("title") or feed_title)[:200],
                remote_audio_url=enclosure,
                source_feed_title=feed_title,
                source_feed_url=feed_url,
                source_link_url=episode.get("link") or feed_url,
                source_episode_title=episode.get("title"),
                attribution=f"Soundbite from “{feed_title}”",
                duration_seconds=int(round(dur)),
                soundbite_start_seconds=round(start, 2),
                soundbite_duration_seconds=round(dur, 2),
                lat_private=lat,
                lng_private=lng,
                location_label=label,
            )
        )
    return out


def music_clip_from_track(
    track: dict, feed_title: str, feed_url: str, location: Any
) -> Optional[IngestClip]:
    """Whole-track clip from a medium=music feed item (no soundbite window)."""
    enclosure = track.get("enclosureUrl")
    if not enclosure:
        return None
    lat, lng, label = parse_geo(location)
    dur = track.get("duration")
    try:
        dur = int(round(float(dur))) if dur else 0
    except (TypeError, ValueError):
        dur = 0
    return IngestClip(
        status="published",
        ingestion_type="admin_rss_import",
        category="music",
        title=(track.get("title") or feed_title)[:200],
        remote_audio_url=enclosure,
        source_feed_title=feed_title,
        source_feed_url=feed_url,
        source_link_url=track.get("link") or feed_url,
        source_episode_title=track.get("title"),
        attribution=f"Independent music from “{feed_title}”",
        duration_seconds=dur or 1,
        soundbite_start_seconds=None,
        soundbite_duration_seconds=None,
        lat_private=lat,
        lng_private=lng,
        location_label=label,
    )


# --------------------------------------------------------------------------
# Podcast Index client.
# --------------------------------------------------------------------------

def _pi_headers(key: str, secret: str) -> dict:
    epoch = str(int(time.time()))
    sig = hashlib.sha1((key + secret + epoch).encode("utf-8")).hexdigest()
    return {
        "User-Agent": USER_AGENT,
        "X-Auth-Key": key,
        "X-Auth-Date": epoch,
        "Authorization": sig,
    }


def _pi_get(path: str, key: str, secret: str, **params) -> dict:
    import requests

    r = requests.get(f"{PI_BASE}{path}", headers=_pi_headers(key, secret), params=params, timeout=30)
    if r.status_code == 401:
        raise SystemExit("[error] Podcast Index 401 — check PODCAST_INDEX_API_KEY/SECRET.")
    r.raise_for_status()
    return r.json()


def fetch_music_clips(key: str, secret: str, max_feeds: int, blocked: set[str]) -> Iterable[IngestClip]:
    data = _pi_get("/podcasts/bymedium", key, secret, medium="music", max=max_feeds)
    for feed in data.get("feeds", []):
        feed_url = feed.get("url") or ""
        if feed_url in blocked:
            continue
        location = feed.get("location")
        if not location:  # location is the whole point — skip untagged feeds
            continue
        feed_id = feed.get("id")
        feed_title = feed.get("title") or "Independent artist"
        eps = _pi_get("/episodes/byfeedid", key, secret, id=feed_id, max=100)
        for track in eps.get("items", []):
            clip = music_clip_from_track(track, feed_title, feed_url, location)
            if clip:
                yield clip


def fetch_soundbite_clips(key: str, secret: str, max_feeds: int, blocked: set[str]) -> Iterable[IngestClip]:
    data = _pi_get("/recent/feeds", key, secret, max=max_feeds)
    for feed in data.get("feeds", []):
        feed_url = feed.get("url") or ""
        if feed_url in blocked:
            continue
        location = feed.get("location")
        if not location:
            continue
        feed_id = feed.get("id")
        feed_title = feed.get("title") or "Local podcast"
        eps = _pi_get("/episodes/byfeedid", key, secret, id=feed_id, max=50)
        for ep in eps.get("items", []):
            yield from soundbite_clips_from_episode(ep, feed_title, feed_url, location)


def scan_location_adoption(key: str, secret: str, medium: str, max_feeds: int) -> dict:
    """
    Cheap, real measurement to sanity-check the "how much geotagged content
    exists" claims: of the feeds returned for this medium, how many declare a
    <podcast:location>, and how many of those carry usable coordinates? One
    API page, no per-episode calls.

    This is a sample-based RATE from a live query, not a full-index census —
    for an exact global count, analyze the Podcast Index database dump
    (https://public.podcastindex.org/). If `with_location` comes back 0, the
    list endpoint may omit the location field on list responses; say so and
    we'll switch to per-feed lookups.
    """
    if medium == "music":
        data = _pi_get("/podcasts/bymedium", key, secret, medium="music", max=max_feeds)
    else:
        data = _pi_get("/recent/feeds", key, secret, max=max_feeds)
    feeds = data.get("feeds", [])
    with_loc = sum(1 for f in feeds if f.get("location"))
    with_coords = sum(1 for f in feeds if parse_geo(f.get("location"))[0] is not None)
    return {"scanned": len(feeds), "with_location": with_loc, "with_coordinates": with_coords}


# --------------------------------------------------------------------------
# Supabase write (service role, server-side only).
# --------------------------------------------------------------------------

def insert_clips(clips: list[IngestClip], supabase_url: str, service_key: str) -> int:
    import requests

    if not clips:
        return 0
    endpoint = supabase_url.rstrip("/").removesuffix("/rest/v1") + "/rest/v1/clips"
    headers = {
        "apikey": service_key,
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "application/json",
        # Idempotent: the clips_remote_soundbite_key unique index dedupes.
        "Prefer": "resolution=ignore-duplicates,return=minimal",
    }
    written = 0
    for i in range(0, len(clips), 100):
        batch = [{k: v for k, v in asdict(c).items() if v is not None} for c in clips[i : i + 100]]
        r = requests.post(endpoint, headers=headers, json=batch, timeout=60)
        r.raise_for_status()
        written += len(batch)
    return written


def load_blocked_feeds(supabase_url: str, service_key: str) -> set[str]:
    import requests

    endpoint = supabase_url.rstrip("/").removesuffix("/rest/v1") + "/rest/v1/blocked_feeds"
    headers = {"apikey": service_key, "Authorization": f"Bearer {service_key}"}
    try:
        r = requests.get(endpoint, headers=headers, params={"select": "feed_url"}, timeout=30)
        r.raise_for_status()
        return {row["feed_url"] for row in r.json()}
    except Exception as e:  # noqa: BLE001 — opt-out list is best-effort
        print(f"[warn] could not load blocked_feeds: {e}", file=sys.stderr)
        return set()


def load_env() -> dict:
    env = dict(os.environ)
    dotenv = Path(__file__).resolve().parents[2] / "backend" / ".env"
    if dotenv.exists():
        for line in dotenv.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env.setdefault(k.strip(), v.strip())
    return env


# --------------------------------------------------------------------------
# Offline self-test — validates the transforms with zero network/credentials.
# --------------------------------------------------------------------------

def self_test() -> int:
    lat, lng, label = parse_geo("geo:30.2672,-97.7431")
    assert lat == 30.2672 and lng == -97.7431, (lat, lng)
    assert parse_geo("Austin, TX") == (None, None, "Austin, TX")
    assert parse_geo({"geo": "geo:51.5,-0.12", "name": "London"}) == (51.5, -0.12, "London")
    assert parse_geo(None) == (None, None, None)
    assert parse_geo("geo:999,0")[0] is None  # out of range

    episode = {
        "title": "Downtown is growing",
        "link": "https://example.com/ep1",
        "enclosureUrl": "https://cdn.example.com/ep1.mp3",
        "soundbites": [
            {"startTime": "125.0", "duration": "30.0", "title": "Why Downtown Austin is Growing"},
            {"startTime": "10", "duration": "2"},  # too short → dropped
        ],
    }
    clips = soundbite_clips_from_episode(episode, "Keep Austin Weird", "https://feed", "geo:30.26,-97.74")
    assert len(clips) == 1, clips
    c = clips[0]
    assert c.remote_audio_url == "https://cdn.example.com/ep1.mp3"
    assert c.soundbite_start_seconds == 125.0 and c.soundbite_duration_seconds == 30.0
    assert c.source_link_url == "https://example.com/ep1"
    assert c.lat_private == 30.26

    music = music_clip_from_track(
        {"title": "Neon Rain on 6th Street", "enclosureUrl": "https://wavlake.com/t.mp3", "duration": "180"},
        "The Austin Synth Syndicate", "https://feed", "geo:30.2672,-97.7431",
    )
    assert music and music.category == "music" and music.soundbite_start_seconds is None
    assert music.duration_seconds == 180

    # creator_id is intentionally NOT a field — ingested rows stay author-less.
    field_names = {f.name for f in dataclasses.fields(IngestClip)}
    assert "creator_id" not in field_names

    print("self-test OK: geo parsing + soundbite/music transforms behave")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Ingest open Podcasting 2.0 audio into Open Air.")
    ap.add_argument("--medium", choices=["music", "podcast"], default="music")
    ap.add_argument("--max", type=int, default=100, help="max feeds to scan")
    ap.add_argument("--dry-run", action="store_true", help="print, do not write")
    ap.add_argument("--scan", action="store_true",
                    help="measure location-tag adoption for the chosen medium (no writes)")
    ap.add_argument("--self-test", action="store_true", help="offline transform check")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    env = load_env()
    pi_key = env.get("PODCAST_INDEX_API_KEY")
    pi_secret = env.get("PODCAST_INDEX_API_SECRET")
    if not pi_key or not pi_secret:
        raise SystemExit("[error] set PODCAST_INDEX_API_KEY/SECRET in backend/.env")

    if args.scan:
        s = scan_location_adoption(pi_key, pi_secret, args.medium, args.max)
        scanned = s["scanned"]
        pct = (100 * s["with_location"] / scanned) if scanned else 0
        print(f"{args.medium}: scanned {scanned} feeds; "
              f"{s['with_location']} declare a location ({pct:.1f}%); "
              f"{s['with_coordinates']} have usable coordinates.")
        print("(Sample rate from a live query — for an exact global count, "
              "analyze the Podcast Index DB dump.)")
        return 0

    sb_url = env.get("SUPABASE_URL", "")
    sb_key = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    blocked = load_blocked_feeds(sb_url, sb_key) if (sb_url and sb_key) else set()

    source = fetch_music_clips if args.medium == "music" else fetch_soundbite_clips
    clips = list(source(pi_key, pi_secret, args.max, blocked))
    print(f"built {len(clips)} {args.medium} clips from up to {args.max} feeds")

    if args.dry_run:
        for c in clips[:15]:
            where = c.location_label or "??"
            print(f"  · [{where}] {c.source_feed_title} — {c.title}  <{c.remote_audio_url}>")
        if len(clips) > 15:
            print(f"  … and {len(clips) - 15} more")
        return 0

    if not sb_url or not sb_key:
        raise SystemExit("[error] set SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY to write (or use --dry-run)")
    written = insert_clips(clips, sb_url, sb_key)
    print(f"wrote {written} clips (duplicates ignored). Published and live immediately.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
