# Phase 9 — Hardening (pre-ship gate)

**Status: DONE (Y1 + Y2 + Y3 + Y4 + Y5 + Y6 + Y7 done 2026-07-04).** This is the
**pre-ship gate** — the last required phase before an MVP ship. It absorbs the residual startup-perf
work, the release-build hardening (R8/ProGuard + Baseline Profile), the contextual-suggestions rework,
test/privacy/logging hardening, and the multi-version / LOW_END validation. **No new product features.**

Hard rules unchanged: `domain` pure (stdlib+coroutines); interfaces in `domain`, impls in `data/*`; no
`feature→feature` edges; single `NavHost` in `:app`; repo/use-case ops return `OperationResult`; launcher
core works fully offline; optional permissions never block startup.

Each block ends with: touched-module `testDebugUnitTest` + `assembleDebug` (+ **release** build where
relevant) green, a device pass on SM-A325F where it's a runtime change, and a short ADR appended to
`ai-context/decisions.md` in the established format.

---

## 0. Priority / sequencing (ship-order within the phase)

1. **Block Y1 — Startup performance** ✅ 2026-07-04 (warm median ~102ms, cold median 766ms release,
   first home frame has no Loading spinner). Pairs with **Y2**.
2. **Block Y2 — Release build (R8/ProGuard + Baseline Profile)** ✅ 2026-07-04 (R8/resource shrink +
   shipped `app/src/main/baseline-prof.txt`; release smoke-clean on SM-A325F).
3. **Block Y3 — Contextual suggestions rework** ✅ 2026-07-04 (home correctness — no unlaunchable
   suggestion chips on SM-A325F).
4. **Block Y4 — Test-coverage hardening.** ✅ 2026-07-04 (VM-level regression coverage broadened;
   production code unchanged).
5. **Block Y5 — Privacy / logging / crash-report filtering / error handling.** ✅ 2026-07-04
   (payload-free logging guard + complete assistant retryability matrix; no crash-report SDK present).
6. **Block Y6 — Multi-version + LOW_END validation.** ✅ 2026-07-04 (Android 13 device pass +
   LOW_END/trim validation by tests and available-device profiling; Android 9/11/14 + real LOW_END
   hardware remain residual due unavailable matrix).
7. **Block Y7 — Residual cosmetic findings cleanup.** ✅ 2026-07-04

**Out of scope (separate model/data track, NOT Phase 9):** real NLU model (OQ#1/#2), embedding model
(OQ#3), on-device STT matrix (OQ#4). These are gated on model/data availability, not hardening.

---

## Block Y1 — Startup performance

**Goal (reframed — do NOT fixate on one number).** For a launcher the resident/warm path dominates; the
`<400ms` cold budget is aspirational, not a ship gate. Targets, in priority order:

1. **Warm/hot start** (HOME → home ready) ≤ **~200ms** and **no spinner**. This is the everyday path once
   Sidr is the default home. **Currently unmeasured — measure it first.**
2. **Remove the visible Loading spinner** on the first home frame (paint favorites/suggestions from cache
   immediately; load the full app list lazily / progressively).
3. **Cold start** from ~2000ms (debug) down to **~500–800ms** on **release**. Don't hand-micro-optimize the
   last few hundred ms (low ROI).

**Measurement protocol (RELEASE build only — debug is inflated).**
- Cold: `am force-stop` → `am start -S -W -n com.sidr.launcher/.LauncherActivity`, ×8, drop the first
  (page-cache warmup), report median.
- Warm: launch, background it, return via `am start -W` **without** `-S` (process alive), ×8, median.
- Spinner: Perfetto/log timestamp from first frame to home-rendered (favorites/list visible).

**Work (measure → then fix; confirm each step by re-measuring cold+warm).**
- Perfetto trace of `Application.onCreate → Activity.onCreate → first composition`. Confirm/refute the
  Round-3 suspects with data, not assumption:
  - `SidrLauncherApp.onCreate`: Hilt graph, WorkManager `Configuration.Provider`, `ensureModel()`
    fire-and-forget, boot warmup / suggestion scheduling.
  - First `DataStore<Preferences>` read; Room (`sidr_history.db`) open + migrations.
  - `InstalledAppsRepositoryImpl.getInstalledApps` (PackageManager enumeration of all packages) — heavy on
    a device with hundreds of packages; it feeds home favorites + drawer.
- Move off the first-frame critical path everything not needed for it: `ensureModel()`, WorkManager
  scheduling, suggestion precompute, and the full PackageManager enumeration (home only needs
  favorites+suggestions from cache; the drawer already loads lazily).
- Cache-first first paint so the spinner never shows on a repeat launch.

**Done 2026-07-04 (SM-A325F / Android 13):** release warm+cold measured, spinner gone on first home frame,
cold in the ~500-800ms band, offline-core unchanged. Final release: warm `TotalTime` median ~102ms
(`204,109,83,86,115,101,103,98`), cold median 766ms after dropping first
(`778,745,766,757,772,747,784,817`), early screenshots at ~150ms/~600ms show home shell with no Loading
spinner. ADR: decisions.md "2026-07-04 — Startup optimization + release build (SM-A325F)".

---

## Block Y2 — Release build: R8/ProGuard + Baseline Profile

**Overlaps Y1 (its first two steps are the honest baseline).**
- **R8:** enable `isMinifyEnabled = true` + `isShrinkResources = true` in the `release` build type
  (`app/build.gradle.kts`). Add/verify `-keep` rules for **Hilt, Room, kotlinx-serialization, ONNX
  Runtime (`ai.onnxruntime.**`), Ktor**. Confirm release **builds, installs, runs**; smoke every surface
  (home → drawer → settings → assistant → set-as-default → voice-education) with no crash.
- **Baseline Profile:** add the `androidx.benchmark` baseline-profile gradle plugin + a macrobenchmark
  module (`:baselineprofile`) with a `BaselineProfileGenerator` (scenario: cold start → home ready).
  Generate on-device, ship `app/src/main/baseline-prof.txt` in release. Re-measure (typically 20–40%).

**Done 2026-07-04:** release R8/resource shrink enabled, keep rules added for Hilt/Room/
kotlinx-serialization/ONNX/Ktor, `:baselineprofile` module added and generated a 18,862-line
`app/src/main/baseline-prof.txt`. Final release builds/installs/runs; smoke-clean on device
(home -> drawer -> settings -> assistant -> set-as-default -> voice education); `testDebugUnitTest`
+ `assembleDebug` + `:app:assembleRelease` green. ADR recorded in decisions.md.

---

## Block Y3 — Contextual suggestions rework

**Why:** suggestions are the home's AI-differentiator, but the offline `TimeOfDaySuggestionProvider` emits
a **hardcoded 6-app AOSP table** (`com.android.deskclock`, `com.android.camera`, `com.android.messaging`,
`com.android.settings`, `com.google.android.apps.maps`, `com.android.music`) that mostly doesn't exist on
OEM (Samsung) devices and is **not filtered against installed apps** → chips render but tapping ends in
"Couldn't open that app." Two levels:

**Y3-A — Never render an unlaunchable chip (tiny, do first).**
- Single choke-point: `LauncherViewModel.resolveSuggestionLabels(suggestions, apps)` already has both the
  candidate list and the installed-apps list. **Filter out** any suggestion whose `actionId` is neither an
  installed package nor a known route (`isKnownRoute`). ~5 lines + 1–2 `LauncherViewModelTest` cases.
- Effect: the "Couldn't open that app" class is gone on every device. Usage-based suggestions
  (`UsageSuggestionProvider`, unblocked by the 2026-07-04 "Personalize from usage" toggle) keep the row
  populated with real installed apps.

**Y3-B — Rework the TimeOfDay prior properly (b2 — chosen approach).**
- The hardcoded 6-app table is an arbitrary hand-picked prior. **Replace it**, don't just re-point packages:
  reduce it to **1–2 genuinely universal, category-resolved anchors** (e.g. Clock via
  `AlarmClock.ACTION_SHOW_ALARMS`, Camera via `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` /
  `PackageManager` category query) resolved to the **device's actual** default app — never a hardcoded
  package. Make **usage / calendar / location** the primary contextual sources; time-of-day becomes a thin,
  always-valid fallback rather than the main signal.
- `TimeOfDaySuggestionProvider` is currently pure (no Android). Category resolution needs `PackageManager`
  (via `@ApplicationContext` — a DI change) **or** a small injected resolver port (keeps the provider
  testable and the Android bit behind a seam — preferred, mirrors the project's port discipline). Decide in
  a fork at block start.
- Guarantee (invariant + test): the engine never emits a suggestion whose target can't be launched on the
  current device. Add a provider-level test that runs against a fake installed-set.

**Done 2026-07-04 (SM-A325F / Android 13).**
- `LauncherViewModel.resolveSuggestionLabels(...)` now filters suggestions to installed launchable
  packages or known routes; package-target cached suggestions are not rendered before the installed-app
  list is available, while route suggestions can still paint.
- New `SuggestionActionTargetResolver` domain port + Android `PackageManager` implementation. The engine
  filters unsupported actionIds before ranking/persisting, so precompute/cache do not keep stale
  unlaunchable targets.
- `TimeOfDaySuggestionProvider` no longer has the 6-app AOSP table. It resolves only two thin universal
  anchors (`AlarmClock.ACTION_SHOW_ALARMS`, `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`) to the
  device's actual launchable package, and emits nothing when no launchable handler is proven.
- JVM coverage added/updated for VM filtering, engine filtering/persistence, and TimeOfDay resolved-anchor
  fallback. `./gradlew testDebugUnitTest assembleDebug :app:assembleRelease` green.
- Device smoke: debug installed over the existing app, AI suggestions + usage personalization enabled,
  launched `A101` from the drawer to create a usage record, relaunched Sidr, and the home suggestions row
  rendered `A101` + resolved Samsung Clock (`Часы`) with no `Music`/missing-AOSP chip. `A101` suggestion
  launched `com.a101kapida.android`; Clock suggestion opened `com.sec.android.app.clockpackage`.
  `AndroidRuntime:E` logcat filter was empty.

---

## Block Y4 — Test-coverage hardening

- Fill the thin spots flagged by codegraph as "no covering tests found": `LauncherViewModel` suggestions
  first-paint/supersede + `deriveFavorites` + `onSuggestionClicked` routing; the Set-as-default and
  usage-history paths (VM-level).
- Broaden domain/intent/repository/permission/offline/device-capability coverage per the roadmap.
- Regression tests pinning the Phase-UX device findings (usage-history gate, suggestion filtering).

**Done 2026-07-04:** meaningful coverage added without production changes. `LauncherViewModelTest` now
pins suggestion first-paint/supersede, route/package tap routing (including stale-feedback clearing and
fallback package launch), usage-history gating for suggestion taps, and favorites edge cases
(`favoritesCount <= 0` and live preference changes without app-list reload). `SettingsViewModelTest` now
pins no-op writes for AI suggestions / usage history / user preferences plus `NavigateBack`. Set-as-default
remains a screen-level Android intent helper, so Y4 did not introduce a VM seam for it. Full
`testDebugUnitTest assembleDebug :app:assembleRelease` green.

---

## Block Y5 — Privacy / logging / crash-report filtering / error handling

- Audit all logging: no user command text, no transcripts, no secrets, no raw calendar/location values
  (extend the existing `PrivacyInventoryGuardTest` / `AiRequestGuardTest` discipline to any new log sites).
- Crash-report filtering (strip PII/secret-bearing state before any report surface).
- Error-handling sweep: every repo/use-case returns `OperationResult`; no throw reaches the UI; retryable
  vs non-retryable classification consistent.

**Done 2026-07-04:** logging audit found one real privacy gap in `AppNavHost`: the unknown-route
fallback logged the raw route and attached the navigation exception, while assistant routes can carry a
user prompt in the `prompt` query arg. The fallback log is now payload-free and no longer attaches the
exception; `AppNavHostLoggingGuardTest` pins that discipline. Existing log sites were re-audited and
remain payload-free (no command text/transcripts/secrets/raw calendar/location values; only class names,
status/counts, and static messages). No crash-report SDK/surface is wired, so there was no report payload
filter to install; the only exception-bearing surface found was the nav fallback log and it was stripped.
Assistant error handling gained full `AiError` retryable/provider-CTA coverage. `PrivacyInventoryGuardTest`,
`AiRequestGuardTest`, and `OutboundSecretLeakGuardTest` remain green. Full
`testDebugUnitTest assembleDebug :app:assembleRelease` green.

---

## Block Y6 — Multi-version + LOW_END validation

- Validate on **Android 9 / 11 / 13 / 14** (device matrix + emulators): launch, offline core, settings
  persistence, set-as-default (role vs `ACTION_HOME_SETTINGS` fallback path on older APIs), voice
  education.
- **LOW_END profiling** (`DeviceProfileClassifier` LOW_END path): memory, trim-memory teardown
  (`SessionLifecycle` release), cold/warm start on a low-RAM profile; confirm the launcher core stays
  responsive and the AI paths self-gate off.

**Done 2026-07-04:** validation-first pass completed with no production code changes. Available runtime
matrix was one real device only: SM-A325F (`RF8R705H38F`) on Android 13 / SDK 33. No AVDs were configured
(`avdmanager list avd` empty) and `emulator` was not available in PATH, so Android 9 / 11 / 14 remain an
explicit residual until the matrix exists.

Android 13 device pass:
- Installed the current debug APK via `adb install -r --no-streaming` (Gradle `installDebug` briefly lost
  the WSL USB device; direct no-streaming install succeeded).
- Launch/home: cold debug `TotalTime` samples during the run were 2258ms, 1901ms, and 1883ms; warm/HOT
  samples with the process alive were 166ms, 114ms, and 100ms. Home rendered search/mic, setup nudge,
  resolved suggestions (`Часы`, `A101`), Favorites, All apps, Settings, and Assistant; `AndroidRuntime:E`
  stayed empty. These are debug timings; Y1/Y2 release startup numbers remain the ship performance record.
- Offline core: temporarily set Wi-Fi disabled and `mobile_data=0`, force-stopped/relaunched Sidr, and
  confirmed the home surface still rendered. Tapping the resolved Clock suggestion launched
  `com.sec.android.app.clockpackage/.ClockPackage` while offline; no crash. Wi-Fi and mobile data were
  restored to their original enabled state.
- Settings persistence: switched theme to Light, force-stopped/relaunched, returned to Settings, and
  confirmed Light stayed selected while AI suggestions / usage personalization / voice input / favorites=8
  remained persisted. Restored the theme to System default after validation.
- Set-as-default: Settings -> Set as default launcher opened the Android 13 `ROLE_HOME`
  `RequestRoleActivity` chooser with One UI Home and Sidr Launcher; cancelled without changing the
  device default. The Android < 10 `ACTION_HOME_SETTINGS` fallback is code/JVM-covered but not device-run
  because no API 28/older target was available.
- Voice education: with `RECORD_AUDIO` denied (`appops RECORD_AUDIO: ignore`), tapping the home mic routed
  to the Voice commands education screen with the Enable microphone CTA; no recognizer start was attempted.

LOW_END / memory validation:
- The available device is not LOW_END by current thresholds: `/proc/meminfo` `MemTotal=5,791,280 kB` and
  `nproc=8`, so `DeviceProfileClassifier` classifies it HIGH_END. LOW_END gating remains covered by
  JVM tests (`DeviceProfileClassifierTest` / `LocalInferenceGateTest`) rather than hardware validation in
  this run.
- No local model files are present on device (`run-as com.sidr.launcher ls files/models` -> missing), so
  model-gated NLU/embedding paths remain inert and launcher core stayed responsive.
- `am send-trim-memory com.sidr.launcher BACKGROUND` and `COMPLETE` invoked the `SessionLifecycle` seams:
  `OnnxTextEmbedder: Embedding ONNX session released (trim)` and
  `OnnxIntentClassifier: ONNX session released (trim)` logged for both levels; the Sidr process stayed
  alive.

Verification:
- `./gradlew --no-daemon :domain:test :core:android:testDebugUnitTest :data:ai-local:testDebugUnitTest :app:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest :app:assembleDebug` ✅
- Device validation above on SM-A325F / Android 13 ✅

---

## Block Y7 — Residual cosmetic findings cleanup (low priority)

- Round-3 recorded findings: `keySet` indicator race (tick stays false though key saved/usable — cosmetic)
  and the `RateLimited` typo `retray → retry`.
- Phase-UX finding #3: relaunching `LauncherActivity` while alive resumes the last nav destination (drawer)
  instead of home — only visible while not the default launcher; decide whether to reset-to-home on
  `onNewIntent` when Sidr is the home app.

**Done:** cleared or explicitly deferred with rationale; ADR note.

**Done 2026-07-04 (SM-A325F / Android 13 smoke).**
- `AssistantViewModel.saveProvider(...)` now marks `form.keySet=true` immediately after a successful
  non-blank API-key write, so the provider form tick no longer waits on (or loses) the config-flow /
  secret-store ordering. The key value still never enters UI state.
- The `RateLimited` user-facing text is pinned as `Rate limited. Please wait and retry.`; `retray` is
  absent from production/test code. No production string edit was needed in this Y7 pass because the
  current working tree already had the corrected text; regression coverage now locks it.
- Relaunch/re-entry while `LauncherActivity` is alive now resets nested navigation back to launcher home:
  the HOME activity is `singleTop`, `onNewIntent` emits a Compose state signal, and `AppNavHost` clears
  back stack entries above `Routes.Launcher.ROUTE` via the same `popUpTo + launchSingleTop` home path.
  This is deliberately app-shell-only; feature ViewModels and in-app back behavior are unchanged.
- Coverage added: assistant VM regressions for immediate `keySet` and `RateLimited` text, plus an app
  source guard for the re-entry wiring.
- Verification: extended touched-module Gradle set + `:app:assembleDebug` green; debug APK installed on
  SM-A325F, drawer opened, `am start -W -n com.sidr.launcher/.LauncherActivity` delivered a new intent to
  the running top instance, and the UI returned to home (`Search or type a command...` / `Favorites` /
  `All apps`) with empty `AndroidRuntime:E`.

---

## Definition of done (phase / ship gate)

- Startup: warm ≤ ~200ms + no spinner; cold in the ~500–800ms band on release (`<400ms` = aspirational).
- Release build (R8 + Baseline Profile) green, installs, smoke-clean; ProGuard keeps correct.
- Suggestions: no unlaunchable chip on any device; time-of-day reworked to resolved universal anchors.
- Test/privacy/logging hardening done; multi-version + LOW_END validated.
- Hard rules intact; `testDebugUnitTest` + `assembleDebug` + release green; ADRs + `current-status.md`
  synced. Model-gated items (OQ#1–#4) remain a separate track, explicitly out of the ship gate.
