# Open Air

Open Air is an Android-first MVP for hyperlocal audio discovery: creators upload geo-tagged clips, and listeners get a passive nearby feed that works like ambient local radio.

## Current Direction

- Native Android first: Kotlin, Jetpack Compose, and Media3.
- No web app scaffold.
- Backend should stay thin for MVP: Cloudflare Worker API, Cloudflare R2 for audio, Supabase Postgres for structured data, and one async FFmpeg worker for normalization/manual review.
- The app currently uses mock data so the listen loop can be designed before backend credentials exist.

## MVP App Shell

The first pass includes:

- Listen screen with nearby clip queue, category filters, playback controls, waveform placeholder, local heard-clip behavior, and MVP metric hooks.
- Product-shaped Record, History, and Profile surfaces ready to connect to auth, upload, and listening-history services.
- Media3 `MediaSessionService` scaffold for background playback and lock-screen controls.
- Supabase MVP schema and a small Hono/Cloudflare Worker API stub.
- Unit and Compose UI coverage for the mock discovery queue and primary navigation flows.

## Run

Open the folder in Android Studio, let Gradle sync, then run the `app` configuration on an emulator or Android device.

The local machine has Android SDK files at:

```text
C:\Users\smzab\AppData\Local\Android\Sdk
```

From this folder, run:

```powershell
.\gradlew.bat :app:assembleDebug
```

This repository does not include backend secrets or real Cloudflare/Supabase project IDs yet.

Backend/account setup notes live in [docs/backend_account_setup.md](docs/backend_account_setup.md).
Testing and emulator QA notes live in [docs/testing_qa.md](docs/testing_qa.md).
