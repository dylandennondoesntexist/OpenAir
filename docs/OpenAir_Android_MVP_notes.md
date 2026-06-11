# Open Air Android MVP Notes

## v0.6 Product/UI Direction (June 2026)

The product framing shifted from "nearby audio dashboard" to "TikTok for audio, locally ranked, not length-limited, works with the screen off." The UI was rebuilt around five tabs:

- **Home** — full-screen now-playing feed. Auto-plays on open, vertical swipe for next/previous, transport controls (-10s, +30s, speed, skip), and a top switcher between two feeds: **For You** (personalized, local-first algorithmic) and a **location station** (shared rotation — every listener tuned to a place hears the same thing, like terrestrial radio; any location's station is tunable from anywhere).
- **Explore** — find location stations (nearby first, then worldwide). Creator search and follow/friend graphs come later.
- **Create** — one big record button plus device-file import. Audio-first on purpose; no video.
- **Inbox** — placeholder shell until a social graph exists.
- **Profile** — account, listening history (no longer a top-level tab), home station, playback settings.

Removed from the listener UI: category filter chips, H3 cell/density labels, queue explainers. Categories remain clip metadata that feeds ranking; they are no longer user-facing homework. Likes/comments/share appear as affordances on the Home action rail but are not wired in the MVP.

Playback is real Media3 audio: `OpenAirApp` connects a `MediaController` to `PlaybackService` and attaches it to `NowPlayingState`, which mirrors player position/state into Compose and forwards transport commands. Seed clips are bundled WAVs in `res/raw` (generated speech + a synth melody; see `qa/tools/`). Background/screen-off playback and lock-screen/system media controls are verified. Listen events (completion, early skip, context) are captured on swipe/skip/autoplay.

**Station live semantics:** a location station behaves like terrestrial radio on a shared clock. `StationSchedule` derives the rotation position from wall-clock time against a fixed epoch anchor, so everyone tuned to the same station hears the same audio at the same moment. Tuning in seeks to the live point. Rewind (-10s) builds a personal buffer like live TV; +30s only catches back up toward the live edge and is disabled at it; there is no skip, no speed control, and no swipe in station mode. A LIVE chip shows the edge state and how far behind you are, and tapping it jumps back to live. When the backend exists, the anchor/rotation should come from the server so schedule changes don't desync listeners.

## Recommendation

Use Kotlin + Jetpack Compose for the Android MVP. React Native/Expo made sense only if the primary goal was a shared Android/iOS codebase from day one. For an Android-first native app with background audio, location policy, lock-screen controls, Android Auto readiness, upload reliability, and no web app, Kotlin is the lower-risk path.

## MVP Includes

- Guest listener mode.
- Nearby auto-play feed based on the active H3 cell.
- Category filters: All, Food & Drink, Stories & People, Music, History.
- Media3 playback service for lock-screen/system controls.
- Local 48-hour heard clip cache.
- Record/import from device file.
- Rights confirmation for uploads.
- Manual moderation before publishing.
- Listen event capture from day one.

## MVP Defers

- Public URL import and RSS import. Keep these as internal/admin seeding tools until legal review.
- AI transcription, moderation, classification, and ranking.
- Redis/feed cache.
- Full Android Auto UI.
- Push notifications.
- Creator analytics.
- Monetization and promoted clips.
- Web client.

## Backend Shape

Start with a thin API:

- `GET /clips/feed`
- `POST /clips/upload-url`
- `POST /clips`
- `POST /clips/:id/events`
- `GET /users/:id/clips`

Use Supabase Postgres for `clips`, `profiles`, `listen_events`, and review queue data. Use Cloudflare R2 for raw and normalized audio, with signed upload/playback URLs. Use one async worker for FFmpeg normalization to -16 LUFS, mono, 64kbps AAC.

## Open Questions

- Which launch corridor should be used for the first 50-80 seed clips?
- Should creator accounts be email-only for MVP, or also Google sign-in?
- Will the founder team handle manual moderation in Supabase, a small admin script, or a lightweight internal tool?
- What exact music policy should be shown in the upload UI: original-only, spoken-word only, or original music allowed with rights checkbox?
- Is the app name Open Air final enough to put in Play Console assets, or should trademark clearance happen first?
