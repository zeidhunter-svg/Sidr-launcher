# Phase 9 — Hardening (pre-ship gate)

**Status: PLAN (owner sequencing 2026-07-02: Phase UX → Phase 9 → Phase 8-optional).** This is the
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

1. **Block Y1 — Startup performance** (biggest user-visible win; gates ship). Pairs with **Y2**.
2. **Block Y2 — Release build (R8/ProGuard + Baseline Profile)** (required for a real ship; overlaps Y1).
3. **Block Y3 — Contextual suggestions rework** (home correctness — the AI-differentiator screen must not
   show unlaunchable chips). Y3-A is tiny and can land immediately.
4. **Block Y4 — Test-coverage hardening.**
5. **Block Y5 — Privacy / logging / crash-report filtering / error handling.**
6. **Block Y6 — Multi-version + LOW_END validation.**
7. **Block Y7 — Residual cosmetic findings cleanup.**

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

**Done:** warm+cold measured on release, spinner gone on repeat launch, cold in the ~500–800ms band (or a
recorded residual plan), no offline-core regression, ADR with before/after numbers.

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

**Done:** release build green + smoke-clean on device; baseline profile shipped and measured; ADR.

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

**Done:** no unlaunchable chip on any device; time-of-day reduced to resolved universal anchors; usage/
calendar/location primary; JVM tests + device pass on SM-A325F; ADR.

---

## Block Y4 — Test-coverage hardening

- Fill the thin spots flagged by codegraph as "no covering tests found": `LauncherViewModel` suggestions
  first-paint/supersede + `deriveFavorites` + `onSuggestionClicked` routing; the Set-as-default and
  usage-history paths (VM-level).
- Broaden domain/intent/repository/permission/offline/device-capability coverage per the roadmap.
- Regression tests pinning the Phase-UX device findings (usage-history gate, suggestion filtering).

**Done:** meaningful coverage on the above; full `testDebugUnitTest` green; ADR (or a short note in the
block's ADR).

---

## Block Y5 — Privacy / logging / crash-report filtering / error handling

- Audit all logging: no user command text, no transcripts, no secrets, no raw calendar/location values
  (extend the existing `PrivacyInventoryGuardTest` / `AiRequestGuardTest` discipline to any new log sites).
- Crash-report filtering (strip PII/secret-bearing state before any report surface).
- Error-handling sweep: every repo/use-case returns `OperationResult`; no throw reaches the UI; retryable
  vs non-retryable classification consistent.

**Done:** guards green; no payload logging; ADR.

---

## Block Y6 — Multi-version + LOW_END validation

- Validate on **Android 9 / 11 / 13 / 14** (device matrix + emulators): launch, offline core, settings
  persistence, set-as-default (role vs `ACTION_HOME_SETTINGS` fallback path on older APIs), voice
  education.
- **LOW_END profiling** (`DeviceProfileClassifier` LOW_END path): memory, trim-memory teardown
  (`SessionLifecycle` release), cold/warm start on a low-RAM profile; confirm the launcher core stays
  responsive and the AI paths self-gate off.

**Done:** matrix pass recorded; LOW_END budget met or residual plan; ADR.

---

## Block Y7 — Residual cosmetic findings cleanup (low priority)

- Round-3 recorded findings: `keySet` indicator race (tick stays false though key saved/usable — cosmetic)
  and the `RateLimited` typo `retray → retry`.
- Phase-UX finding #3: relaunching `LauncherActivity` while alive resumes the last nav destination (drawer)
  instead of home — only visible while not the default launcher; decide whether to reset-to-home on
  `onNewIntent` when Sidr is the home app.

**Done:** cleared or explicitly deferred with rationale; ADR note.

---

## Definition of done (phase / ship gate)

- Startup: warm ≤ ~200ms + no spinner; cold in the ~500–800ms band on release (`<400ms` = aspirational).
- Release build (R8 + Baseline Profile) green, installs, smoke-clean; ProGuard keeps correct.
- Suggestions: no unlaunchable chip on any device; time-of-day reworked to resolved universal anchors.
- Test/privacy/logging hardening done; multi-version + LOW_END validated.
- Hard rules intact; `testDebugUnitTest` + `assembleDebug` + release green; ADRs + `current-status.md`
  synced. Model-gated items (OQ#1–#4) remain a separate track, explicitly out of the ship gate.
