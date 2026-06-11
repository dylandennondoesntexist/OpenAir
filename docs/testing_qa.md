# Testing and QA

## Commands

Run local unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Run Compose instrumentation tests on a connected emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Build the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Current Coverage

Unit tests cover:

- Active corridor cell seed density.
- Category filtering.
- Recently heard clip exclusion.
- Repeat fallback when a filtered queue would otherwise be empty.
- Listen-event acceptance smoke test.

Compose UI tests cover:

- Listen screen renders the seeded nearby clip.
- Music category filter renders the music clip.
- Play button toggles to Pause.
- Bottom navigation reaches Record, History, and Profile surfaces.

## Emulator Screenshot QA

Screenshot QA was completed on the Pixel_10 Android 17 emulator after reclaiming Docker build cache and compacting Docker's WSL virtual disk.

Current verification:

- `:app:testDebugUnitTest` passes.
- `:app:connectedDebugAndroidTest` passes all 4 Compose UI tests.
- Listen, Record, History, and Profile were inspected for clipping, overlap, hierarchy, contrast, and bottom-navigation state.
- Final screenshots are stored in `qa/screenshots/final`.

Keep at least 10 GB free before emulator work; 15-20 GB gives Gradle and Android Studio more comfortable headroom.

To repeat the visual pass:

1. Cold boot the Pixel_10 emulator.
2. Run `.\gradlew.bat :app:connectedDebugAndroidTest`.
3. Install and launch the debug APK.
4. Capture the four primary tabs at a consistent emulator size.
5. Review typography, spacing, contrast, clipping, and navigation state.
