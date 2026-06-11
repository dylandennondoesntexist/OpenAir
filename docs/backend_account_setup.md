# Backend Account Setup

Use this checklist while creating accounts. Keep real keys in local files or secret managers, not in git.

## Supabase (the only service needed for the solo/friends MVP)

1. Create a Supabase project (free tier). Region: any Americas region; East US (us-east-1 / North Virginia) is ideal for the NJ corridor.
2. Project creation options:
   - **Enable Data API: ON** — the Android app queries Postgres through it.
   - **Automatically expose new tables: OFF** — the migration does explicit grants.
   - **Enable automatic RLS: ON** — safety net; the migration also enables RLS explicitly.
3. SQL Editor → run `backend/supabase/migrations/001_mvp_schema.sql`. It creates the schema, RLS policies, grants, the `published_clips` listener view, the profile-on-signup trigger, and the private `audio` storage bucket with policies.
   Then run `backend/supabase/migrations/002_geo_and_trusted_publish.sql`: server-computed public geohash cell (`clips.geohash5`, ~4.9 km, derived from the private lat/lng) and a `profiles.trusted` flag — clips from trusted creators publish instantly instead of waiting in `pending_review`. Trust yourself with `update profiles set trusted = true where id = '<your-user-id>';` (Authentication → Users for the id).
4. Authentication → Sign In / Up: enable **Email** and **Anonymous sign-ins** (anonymous gets a real user id with zero UI; email/Google upgrade comes later).
5. Save these values locally:
   - Project URL -> `SUPABASE_URL`
   - Publishable anon key -> `SUPABASE_ANON_KEY`
   - Service role key: leave in the dashboard. The app never uses it; nothing local needs it yet.
6. Copy `openair.local.properties.example` -> `openair.local.properties` and fill in the two values. The Android app reads only this file.

Privacy model enforced by the migration:

- Listeners (anon or signed-in) can read only `published_clips`, a view without `lat_private`/`lng_private`.
- Creators read/update their own clips in full but cannot set `status = 'published'` — publishing happens in the dashboard (manual moderation) until an admin tool exists.
- `listen_events` is insert-only for clients; `manual_review_queue` is service-role only.
- Audio uploads go to the private `audio` bucket under the uploader's user-id folder; playback requires a signed-in session.

## Google OAuth (later, with email sign-in upgrade)

1. Create or use a Google Cloud project.
2. Configure OAuth consent screen.
3. Create OAuth client IDs required by Supabase Google sign-in.
4. Add the Supabase callback URL in Google Cloud and Supabase.

## Cloudflare (later, when egress economics or the edge API matter)

1. Create a Cloudflare account, enable R2, create `openair-raw-audio` / `openair-normalized-audio` buckets.
2. Create an API token scoped to the Open Air resources.
3. Deploy `backend/api` as a Worker (`wrangler deploy`); set secrets via `wrangler secret put`.
4. Add Queues/Containers when wiring FFmpeg loudness normalization (-16 LUFS).
5. Copy `backend/.env.example` -> `backend/.env` for local worker dev.
