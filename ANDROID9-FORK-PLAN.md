# Android 9 fork plan (leonardovin/femto-car-launcher, branch `android-9`)

## Context

Upstream Femto Car Launcher targets Android 13+. This fork runs it on the Haval H6 GT head unit
(Android 9, beantechs firmware) and folds in the features of the separate
[`shizuku-bottom-bar`](https://github.com/leonardovin/shizuku-bottom-bar) app, so one launcher replaces the stock home
screen *and* the overlay bottom bar. Constraints of the target unit:

- Android 9 (API 28), x86_64/arm64 unknown until checked on the car (both ABIs ship).
- `adb install` is blocked by beantechs; installs go through `pm install -i com.android.vending`
  (see `shizuku-bottom-bar/deploy.sh`).
- The WebView may be an old, non-updatable Chromium (the emulator's stock Pie WebView is 69).
- Vehicle functions (HVAC, seats, drive mode, volume) are only reachable through the system binder
  `com.beantechs.intelligentvehiclecontrol`, which needs Shizuku (ADB-level) privileges.
- No Google Maps Platform billing: Google Maps is used through the **installed Google Maps app**.

## Status

| Phase | What | State |
| --- | --- | --- |
| 1 | minSdk 33 → 28, every `NewApi` finding guarded with an Android 9 fallback | done |
| 2 | Runtime fixes found on an API 28 emulator (system-broadcast receivers, Bluetooth permission) | done |
| 3 | Live OSM map on Chromium 67–76 WebViews (syntax floor chrome67 + runtime polyfills) | done |
| 4 | Google Maps via the installed app (no API key, no billing) | done (underlay mode: later) |
| 5 | Shizuku integration layer inside the launcher | done |
| 6 | Port the bottom-bar vehicle controls into the dock | planned |
| 7 | Spotify / Apple Music first-class integration | planned |
| 8 | Car install path + on-car validation | planned |

Verified so far on an API 28 x86_64 emulator (stock WebView 69, GLES 3): dashboard, OSM map with
3D buildings, GPS chevron, platform reverse geocoding, weather, calendar, battery/Wi-Fi status.

## Phase 4 — Google Maps through the app (no billing)

The upstream "Google Maps" backend is the Maps JavaScript API with a user key (billed). The fork
adds a separate, key-less integration with the Google Maps app (`com.google.android.apps.maps`):

1. **Navigation handoff.** The dock's Navigation button and a map long-press open Google Maps with
   `google.navigation:q=<lat>,<lng>` (drive mode) or `geo:` for a place, targeting the Maps
   package when installed and falling back to the generic `geo:` chooser otherwise
   (`MainActivity` already owns the `geo:` handoff — extend it, do not duplicate it).
2. **Turn-by-turn card from the Maps notification.** Femto already runs a
   `NotificationListenerService` (`data/music/MusicSessionListenerService.kt`). Generalise it to
   also watch Google Maps' ongoing navigation notification and expose a `NavigationGuidance`
   flow (maneuver icon bitmap, distance, instruction, ETA) from a new `data/navigation/` repository.
   A `NavigationCard` on the dashboard shows it while guidance is active; tapping it brings Maps to
   the front. This is the same approach Android Auto–style dashboards use and needs no key.
3. **Settings.** Map section gains "Navigation app: Google Maps / Waze / system default", which
   decides the handoff target and which notification the guidance card follows.
4. (Stretch, Shizuku-only) **Maps underlay mode**: keep Google Maps running full screen and draw
   the Femto cards as overlay windows above it, the way the bottom bar reserves its strip today
   (`BottomBarService` overlay + `wm overscan`). Only after phases 5–6 prove the overlay plumbing.

## Phase 5 — Shizuku layer

New `data/privileged/` package, ported from `shizuku-bottom-bar`:

- Dependencies `dev.rikka.shizuku:api` + `:provider` (catalog entries, provider in the manifest).
- `ShizukuGateway`: binder-alive state as a `StateFlow`, permission request, `newProcess` shell
  (port of `utils/PrivilegedShell.kt`), `ShizukuBinderWrapper` for system services.
- Self-setup actions once Shizuku is granted: grant our own notification-listener access
  (`cmd notification allow_listener`), set Femto as the default HOME
  (`cmd package set-home-activity`), and grant runtime permissions — removing manual steps on a
  head unit whose settings UI is locked down.
- Settings → "Vehicle integration" section showing Shizuku state and a grant button. Everything
  degrades to today's behaviour when Shizuku is absent (phones, other head units).

## Phase 6 — Bottom-bar vehicle controls in the dock

Port, not re-implement, the proven pieces:

- AIDL: `com/beantechs/intelligentvehiclecontrol/*`, `com/beantechs/voice/adapter/*`, plus
  `CarConstants` / `CarValueMaps` and the `ServiceManager` read/write path
  (`getData`, `updateData`, data-changed listener).
- A `VehicleRepository` exposing climate (driver/passenger temperature, fan speed, A/C, auto,
  recirculation, defrost), seat heat/ventilation levels, media volume, drive/fuel/steer mode and
  regen level as flows, with command methods routed through the same keys the bottom bar writes
  (`CarCommandRegistry` documents which are proven on the car).
- Dock: a vehicle strip (driver temp · fan · passenger temp · seat heat/vent · volume) shown only
  when the vehicle service is reachable; drawers for drive/fuel/steer mode reuse the bottom bar's
  interaction model but Femto's design tokens (`FemtoDimens.MinTouchTarget` etc.).
- Out of scope for the first port: cluster/HUD projection, CarPlay/AA helpers, telnet/root tools,
  the Bluetooth remote-control server — they stay in `shizuku-bottom-bar`, which can keep running
  beside the launcher with its own dock disabled.

## Phase 7 — Spotify / Apple Music

Media already flows through `MediaSession` for any player; the gaps are start-up and resume:

- "Preferred player" setting (auto-detects installed `com.spotify.music`,
  `com.apple.android.music`, others with a `MediaBrowserService`).
- Idle music card shows one-tap launch tiles for the installed preferred players instead of only
  "grant notification access".
- Resume last player: launch the app, wait for its session, then send `KEYCODE_MEDIA_PLAY` to that
  session's `TransportControls`; with Shizuku, fall back to `cmd media_session dispatch play`.
- Browse (playlists) is not possible for Spotify/Apple Music without their allow-listed
  `MediaBrowser` clients, so it is explicitly out of scope.

## Phase 8 — Car install & validation

- Build: `./gradlew :app:assembleStableDebug` (or a release build signed with the user's key).
- Install on the car: `adb push` the APK, then `pm install -i com.android.vending -r <apk>`
  (reuse `shizuku-bottom-bar/deploy.sh` logic).
- Check on the car: WebView version (Diagnostics → WebView), ABI, Vulkan flyover fallback, GPS,
  vehicle strip read/write, notification-listener self-grant, HOME role.

## Verification per phase

`./gradlew :app:assembleStableDebug :app:lintStableDebug` must stay green (lint has no NewApi
findings at minSdk 28), then the API 28 emulator (`femto28` AVD: 1280×720, `hw.gps=yes`, launched
with `-feature GLESDynamicVersion`) for runtime checks; vehicle features can only be checked on
the car or with the bottom bar's `fakeheadunit` module.
