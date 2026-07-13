# DS-6B - Prayer Correctness Capability Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax
> for tracking.

> **STATUS: APPROVED FOR IMPLEMENTATION (2026-07-13).** Tasks 1–2 (requirements + privacy) are COMPLETE —
> every open decision was made by the owner on 2026-07-13 and recorded in **spec §0**
> (`docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md`). Implementation tasks below are
> concretized to those decisions: **offline adhan-java calculation, bundled city index + optional one-shot
> device location, new `:data:prayer` + `:feature:prayer` modules, zero network in v1.**
> Originally PROPOSED 2026-07-11; architecture-first because prayer times are a religious-correctness
> surface, not a decorative widget.

**Goal:** Build the capability that lets SIDR Home show a truthful five-prayer summary: explicit
method/madhab/location chosen by the user, local offline calculation with provenance and freshness,
graceful failure states, and precise location structurally unable to reach AI/cloud.

**Architecture:** Pure `domain/prayer` contracts + `GetPrayerContextUseCase`; `:data:prayer` owns the
adhan-java adapter, bundled city index, and DataStore-backed prefs/schedule cache; `:feature:prayer` owns
setup/detail screens (pushed route); `core/ui` owns `SidrPrayerSummary` rendering only; `LauncherViewModel`
gains one injected use case (sanctioned production change — DS-6B is not presentation-only).

**Tech Stack:** Kotlin, Coroutines/Flow, Hilt, DataStore (existing shared `sidr_preferences`), Compose,
Roborazzi; new deps confined to `:data:prayer`: **adhan-java (MIT)** + bundled GeoNames-derived city asset
(CC-BY attribution).

**Spec:** `docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md` (§0 = approved decisions).

## Prerequisites — ALL MET (2026-07-13)

- [x] DS-3 controls implemented and green.
- [x] DS-4 Home shell exposes the anchor area (quiet English Shahada in `HomeAnchorSlot`).
- [x] DS-5 action/safety available for permission/privacy/blocked states (code-closed 2026-07-13).
- [x] DS-6A "stable enough to preserve Home hierarchy" — satisfied by the existing DS-4 Shahada line; the
      prayer strip renders below it. DS-6A itself stays a separate UI-only block.
- [x] Owner approval of religious/correctness requirements — spec §0, 2026-07-13.

## Global Constraints

- No commits unless the owner explicitly asks.
- **Zero network in v1**: no prayer HTTP client, no API, no outbound prayer traffic of any kind.
- No fake prayer times; no plausible-but-unverified static times; no mock schedule in production.
- No startup network/location/calculation wait: first frame renders cached/no-data only.
- No permission prompt on Home entry; device location is one-shot, opt-in, from prayer settings only.
- No precise location in `AiRequest`, cloud planner prompts, logs, or analytics; stored coordinates are
  rounded to 2 decimal places; exact coordinates are never persisted.
- No guessed defaults: method AND madhab are explicit first-run choices (`MethodRequired` is a real state).
- No casual hand-written prayer math: all times come from adhan-java through one adapter.
- No adhan audio, alarms, notifications, Qibla, mosque finder, or automation in this block.
- No prayer data mixed into ordinary suggestions.
- No production Home strip until provenance/freshness/failure states are implemented.
- adhan-java + city asset live ONLY in `:data:prayer`; `core/ui` imports no domain/data/location types.

## Task 1: Requirements and Authority Discovery — ✅ COMPLETE (2026-07-13, spec §0)

- [x] Identify target launch locality and first supported region(s) → **global, no regional default** (§0.1).
- [x] Decide default authority/method policy → **no default; explicit method + madhab at first setup** (§0.2).
- [x] Decide first implementation source → **maintained calculation library: adhan-java, fully offline**;
      official-authority adapters (e.g., Diyanet) deferred to a future block behind the `PrayerAuthority`
      seam (§0.3).
- [x] Capture primary source links/documents for authority/method parameters → method parameters are
      adhan-java's published standard sets; golden-test expected values come from published primary tables
      (Diyanet for Istanbul, Umm al-Qura for Makkah, MWL for London, Hanafi-madhab table for Kazan) —
      links recorded in Task 5 test files as comments.
- [x] Decide supported prayer names for Home and detail → **Fajr/Dhuhr/Asr/Maghrib/Isha everywhere** (§0.7).
- [x] Decide whether Sunrise appears in detail only → **yes, detail only, marked "not a prayer"** (§0.7).
- [x] Decide cache freshness threshold → **end of current day in the prayer-location timezone** (§0.6).
- [x] Decide stale schedule behaviour → **stale only reachable when recompute fails; shown with STALE
      label + CalculationFailed, never hidden** (§0.6).
- [x] Decide manual location UX and storage rules → **bundled offline city index (primary) + optional
      one-shot device location; store rounded coords + label + tzId + source** (§0.4–0.5).

## Task 2: Privacy Review — ✅ COMPLETE (2026-07-13, spec §0.5)

- [x] Location precision for schedule calculation → 2 decimal places (~1.1 km; ≤ ~1 min error).
- [x] Cache key shape → city ID for index picks; rounded coordinates for device picks.
- [x] What may be logged → nothing location-derived (no coords, no city name, no tz-derived hints).
- [x] What may be sent to a prayer authority/source → **nothing; v1 has no outbound path**.
- [x] Explicit rule that precise location never enters `AiRequest` → §0.5 + guard test in Task 10.
- [x] Manual location clear/change flow → prayer settings; clearing returns the capability to
      `LocationUnavailable`/setup state; Home strip disappears (opt-in semantics).
- [x] Permission-denied copy → DS-5 `SidrPermissionNotice` language; city path stays primary; denial
      changes nothing else.

## Task 3: Domain Architecture (pure, `:domain`)

**Purpose:** Prayer contracts + use case before any data/UI.

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerModels.kt`
  (`PrayerName` (5 values + `SUNRISE` marked non-prayer via docs), `CalculationMethodId` (value class,
  string key e.g. `"MWL"`, `"TURKEY"`), `Madhab {STANDARD, HANAFI}`, `PrayerAuthority` (v1: single
  `LOCAL_CALC` value — the seam for future official adapters), `PrayerLocation(label, lat2dp, lon2dp,
  tzId, source: PrayerLocationSource {CITY, DEVICE})`, `PrayerInstant(name, epochMillis)`,
  `PrayerDaySchedule(dateInLocationTz, instants, sunrise?)`, `PrayerScheduleProvenance(authority,
  methodId, madhab, locationLabel, computedAtMillis)`, `Freshness {VERIFIED_CURRENT, CACHED_FRESH,
  CACHED_STALE}`, `TimeZoneState {MATCHES_DEVICE, CONFLICT}`, `UnavailableReason {NOT_CONFIGURED,
  LOCATION_MISSING, CALCULATION_FAILED}`)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerContext.kt`
  (sealed: `Unavailable(reason)`, `Available(schedule, provenance, freshness, timeZoneState,
  nextPrayer: PrayerName?)`)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerCalculator.kt` (port:
  `calculate(location, methodId, madhab, dateInLocationTz): OperationResult<PrayerDaySchedule>`)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerPreferencesRepository.kt` (port:
  flow of `PrayerSetup?` = method+madhab+location; write ops return `OperationResult`)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerScheduleCache.kt` (port: last
  computed schedule + provenance; never unlabelled)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/CityIndex.kt` (port:
  `search(query, limit): List<PrayerLocation>` — impl in `:data:prayer`, Task 5)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerLocationProvider.kt` (port:
  one-shot `currentLocation(): OperationResult<PrayerLocation?>` returning ALREADY-ROUNDED 2dp coords,
  `source = DEVICE` — impl in `:core:android`, Task 8; Block-T `SpeechInputSource` precedent)
- Create: `domain/src/main/java/com/sidr/launcher/domain/prayer/GetPrayerContextUseCase.kt`
  (pure orchestration: no setup → `Unavailable(NOT_CONFIGURED)`; cache same-day+same-setup →
  `CACHED_FRESH` immediately, then recompute → `VERIFIED_CURRENT`; recompute failure with cache →
  `CACHED_STALE`; without cache → `Unavailable(CALCULATION_FAILED)`; next-prayer derivation; day
  boundary + `nextPrayer` in the **location** timezone)
- Test: `domain/src/test/java/com/sidr/launcher/domain/prayer/GetPrayerContextUseCaseTest.kt` +
  model invariant tests; fakes for all 5 ports in `:core:testing`.

**Steps:**
- [ ] Write failing JVM tests for every `PrayerContext` branch listed above (incl. "no schedule without
      provenance" — `Available` cannot be constructed with empty instants, enforce in `init`).
- [ ] Implement models/ports/use case; `:domain` stays stdlib+coroutines, no Android, no Adhan.
- [ ] `./gradlew :domain:test` green.

**Acceptance:** pure JVM tests; no Android imports; no UI strings in domain; vendor-neutral (grep
`adhan|geonames` over `domain/src/` empty).

## Task 4: `:data:prayer` Module + Adhan Adapter (license-gated)

**Purpose:** Prove the calculation source with golden tests before any UI exists.

**Files:**
- Create: `data/prayer/build.gradle.kts` (android-library, Hilt-free like `:data:ai-cloud`; deps:
  `:domain`, coroutines, adhan-java) + `settings.gradle.kts` include.
- Create: `data/prayer/src/main/java/com/sidr/launcher/data/prayer/AdhanPrayerCalculator.kt`
  (`PrayerCalculator` impl: maps `CalculationMethodId`→`CalculationMethod`/params, `Madhab`→Adhan madhab,
  builds date components in the **location tzId**, returns `OperationResult.Failure`-free API — failures
  map to `OperationResult` error, never throws; `SUNRISE` captured for detail).
- Test: `data/prayer/src/test/java/com/sidr/launcher/data/prayer/AdhanPrayerCalculatorGoldenTest.kt`,
  `AdhanPrayerCalculatorDstTest.kt`.

**Steps:**
- [ ] **License gate:** verify adhan-java license is MIT at the pinned version; record
      version+license+repo URL in the test-file header comment and later in the ADR. STOP if not MIT.
- [ ] Add the dependency to `libs.versions.toml` + `:data:prayer` only; grep proves no other module
      references `com.batoulapps`.
- [ ] Golden tests against published primary tables (tolerance ±2 min, sources linked in comments):
      Istanbul × `TURKEY` × a Diyanet-published date; Makkah × `UMM_AL_QURA`; London × `MWL`;
      Kazan × `MWL`+`HANAFI` Asr (madhab difference asserted > 30 min vs STANDARD that day).
- [ ] DST tests: Europe/London spring-forward + fall-back dates; no-DST zone (Asia/Riyadh); device-tz ≠
      location-tz (schedule follows location tz).
- [ ] Failure mapping test: absurd input (lat 90.0) → `OperationResult` failure, no throw.
- [ ] `./gradlew :data:prayer:testDebugUnitTest` green.

**Acceptance:** golden + DST tests green; adhan confined to this module; no network permission/client.

## Task 5: Offline City Index

**Purpose:** Manual location with zero permissions and zero network.

**Files:**
- Create: `tools/prayer/README.md` + `tools/prayer/build_city_index.py` (out of source sets; GeoNames
  `cities15000.txt` → filtered, pipe-separated, gzipped asset: asciiName|displayName|countryCode|lat2dp|
  lon2dp|tzId; target ≤ ~500 KB; CC-BY 4.0 attribution line in README and asset header row).
- Create: `data/prayer/src/main/assets/prayer/cities.gz` (generated).
- Create: `data/prayer/src/main/java/com/sidr/launcher/data/prayer/BundledCityIndex.kt`
  (lazy parse off-main via `Dispatchers.Default`, case/diacritic-insensitive prefix+contains search,
  returns `PrayerLocation` values with `source = CITY`).
- Test: `data/prayer/src/test/.../BundledCityIndexTest.kt` (finds "istanbul", "kazan", "makkah";
  diacritic query; miss returns empty; asset row-count sanity; every row has valid tzId parseable by
  `java.time.ZoneId`).

**Steps:**
- [ ] Generate the asset; record GeoNames dump date + attribution.
- [ ] Implement + tests green; measure asset size and record it in the README.

**Acceptance:** search works fully offline in JVM tests; attribution recorded; size within target.

## Task 6: Prefs + Schedule Cache (`:data:prayer` over shared DataStore)

**Purpose:** Persist setup + last schedule for instant first frame — labelled, never lying.

**Files:**
- Create: `data/prayer/src/main/java/com/sidr/launcher/data/prayer/PrayerPreferencesRepositoryImpl.kt`,
  `PrayerScheduleCacheImpl.kt` (constructor-injected `DataStore<Preferences>` like Block-E impls).
- Modify: `data/repository/.../preferences/PreferencesKeys.kt` — new denylist-clean keys, all in
  `ALL_KEY_NAMES`: `prayer_method`, `prayer_madhab`, `prayer_loc_label`, `prayer_loc_lat2dp`,
  `prayer_loc_lon2dp`, `prayer_loc_tz`, `prayer_loc_source`, `prayer_sched_date`, `prayer_sched_times`,
  `prayer_sched_provenance`. *(Keys live beside the existing inventory so `PrivacyInventoryGuardTest`
  covers them; impls stay in `:data:prayer` — keys are just names, no module edge.)*
- Test: round-trip + invalidation tests (`:data:prayer`), plus `PrivacyInventoryGuardTest` stays green.

**Steps:**
- [ ] Round-trip tests: setup survives reopen; schedule cache stores times WITH provenance atomically —
      a cache write without provenance is impossible by construction.
- [ ] Invalidation tests: changed method/madhab/location/tz or crossed day-boundary (location tz) ⇒
      cache reported stale/mismatched by `PrayerScheduleCache`.
- [ ] `./gradlew :data:prayer:testDebugUnitTest :data:repository:testDebugUnitTest` green.

**Acceptance:** cached fresh/stale distinguishable; no unlabelled times representable; privacy guard green.

## Task 7: `SidrPrayerSummary` (`core/ui`, presentation-only)

**Purpose:** The Home strip + status rendering, exactly per spec §8.

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrPrayerSummary.kt`
  (signature verbatim from spec §8: `SidrPrayerTimeUi`, `SidrPrayerSummaryStatus` (11 values),
  `SidrPrayerSummary(prayers, status, provenance, modifier, locationLabel, onOpenDetails)`).
- Test: `core/ui/src/test/.../component/PrayerSummaryGallery.kt` + `SidrPrayerSummaryScreenshotTest.kt`
  + semantics test.

**Steps:**
- [ ] One quiet mono row: five `NAME HH:MM` cells, next prayer inverted chip-style (label + marker, not
      colour-only); provenance `SidrProvenanceLine`-style line underneath; fontScale ≥ 1.7 ⇒ vertical list.
- [ ] `require(prayers.isEmpty() || provenance.isNotBlank())` — no non-empty schedule without provenance
      (unit-tested).
- [ ] Status states render as label+marker chips (`STALE`, `UPDATING`, `TZ CONFLICT`, …); cached-fresh is
      calm, only stale/failed use attention/danger status roles.
- [ ] Gallery goldens: all 11 states × dark/light + fontScale-2.0 + RTL smoke; record → verify.
- [ ] Dependency guard: no domain/data/feature import (existing `ControlsDependencyGuardTest` pattern
      covers `Sidr*.kt` automatically — confirm it picks the new file up).

**Acceptance:** `:core:ui:testDebugUnitTest` + `verifyRoborazziDebug` green; spec §8 rules hold.

## Task 8: `:feature:prayer` — Setup / Detail Screens

**Purpose:** Explicit method+madhab+location choice; detail view with all five + Sunrise.

**Files:**
- Create: `feature/prayer/build.gradle.kts` (mirror `:feature:permission_education` Hilt setup) +
  `settings.gradle.kts` include.
- Create: `feature/prayer/src/main/java/com/sidr/launcher/feature/prayer/PrayerSettingsViewModel.kt`
  (injects the 3 domain ports + `GetPrayerContextUseCase`; Android-free; exposes setup state, city
  search results, save/clear ops; `NavigationEvent` for education route).
- Create: `.../PrayerSettingsScreen.kt` (method selector, madhab selector, city search field over
  `CityIndex`, optional "Use device location" button → routes to the existing permission-education
  flow (`LOCATION` feature) exactly like Block-T/U screens; DS-5 `SidrPrivacyNotice` explaining
  local-only storage; clear-location row via `SidrActionGate` Destructive).
- Create: `.../PrayerDetailScreen.kt` (all five + Sunrise row labelled "SUNRISE · NOT A PRAYER",
  provenance, freshness, method/madhab/location rows linking to settings).
- Modify: `app/.../navigation/AppNavHost.kt` + `Routes` — two pushed destinations (no new tab, bar
  hides on push like the other 7 pushed screens).
- Modify: `feature/settings/.../SettingsScreen.kt` — one `SidrNavigationRow` "Prayer times" (emits
  existing-pattern `NavigationEvent`).
- Test: `PrayerSettingsViewModelTest` (JVM, fakes): save requires all three choices; clear returns
  NOT_CONFIGURED; device-location result is stored rounded; no permission logic in VM.

**Steps:**
- [ ] VM + tests first (JVM, `:domain` fakes), then screens, then nav wiring.
- [ ] One-shot device location read lives in `:core:android` behind a tiny
      `PrayerLocationProvider` port (rounds to 2dp BEFORE returning — exact value never crosses the
      port); permission check via existing `PermissionChecker`.
- [ ] `./gradlew :feature:prayer:testDebugUnitTest testDebugUnitTest assembleDebug` green.

**Acceptance:** no permission request on entry; manual path fully usable without permission; denial
leaves everything usable; no new permission feature beyond the existing LOCATION education flow.

## Task 9: Home Integration (`feature/launcher` + `LauncherViewModel`)

**Purpose:** The opt-in prayer strip below the Shahada — truthful or absent.

**Files:**
- Modify: `feature/launcher/.../LauncherViewModel.kt` — inject `GetPrayerContextUseCase`; new
  `prayerContext` state flow: first emission from cache only (no calculation on the critical startup
  path — collect lazily/`SharingStarted.WhileSubscribed`), recompute in `viewModelScope` off-main.
  **This is the sanctioned production change; every existing `LauncherViewModelTest` case must pass
  unchanged (constructor gains one param — update test constructions with the fake only).**
- Modify: `feature/launcher/.../LauncherScreen.kt` — render `SidrPrayerSummary` in the anchor column
  below the Shahada ONLY when context is `Available` (or `Unavailable(CALCULATION_FAILED)` with a
  previously-shown schedule → stale render); `NOT_CONFIGURED`/`LOCATION_MISSING` render nothing on Home;
  hidden while typing (same rule as the Shahada); tap (when `onOpenDetails` wired) pushes the detail
  route.
- Test: new `LauncherViewModelTest` cases (context mapping, no-eager-calculation) + screen test for
  "nothing rendered when not configured".

**Steps:**
- [ ] VM tests first; assert startup path performs zero `PrayerCalculator` calls before subscription.
- [ ] Screen wiring; Universal Input reachability untouched; no spinner (Updating state is a quiet label
      only inside the strip, never a placeholder strip).
- [ ] Full `:feature:launcher:testDebugUnitTest` green — pre-existing tests byte-compatible.

**Acceptance:** Home no-data state is calm/empty; cached state shows freshness; verified state shows
provenance; startup does no network (structurally true — module has none) and no synchronous calculation.

## Task 10: Privacy and AI Guards

**Purpose:** Prove prayer location cannot leak into AI/cloud.

- [ ] Extend the Block-U-pattern guard: plant a real city label + rounded coords in prayer prefs, run
      `PromptContextBuilder` + the real outbound body construction, assert absence (domain +
      outbound-body levels, like the AIL-4 three-level guard).
- [ ] Reflection sweep: `AiRequest` field inventory unchanged (existing `AiRequestGuardTest` already
      fails on new outbound fields — confirm it stays green, i.e., nothing prayer-shaped was added).
- [ ] Grep gates in a guard test or CI note: `com.batoulapps` only under `data/prayer/`;
      `android.location` only in `:core:android` (existing confinement) + no new usage outside the
      `PrayerLocationProvider` impl.
- [ ] Log audit: no `Log.*` call in `:data:prayer`/`:feature:prayer` includes location/city/coords
      (test scans source like the existing logging guard tests).

**Acceptance:** guard tests green; no AI/cloud path receives prayer location; grep gates clean.

## Task 11: Device Acceptance (SM-A325F) — mandatory before close

- [ ] First frame with no prayer data (fresh install): calm Home, no strip, no prompt.
- [ ] Setup: method + madhab + city (offline, airplane mode) → strip appears with provenance.
- [ ] Verified current schedule; times cross-checked against a published table for the chosen city/date.
- [ ] Airplane-mode relaunch: instant cached-fresh render (no wait).
- [ ] Stale path: simulate recompute failure if feasible; otherwise verify day-boundary recompute.
- [ ] Optional device location: education flow → grant → rounded location stored; deny → city path fine.
- [ ] Method/madhab/location change → immediate recompute + provenance update.
- [ ] Timezone conflict label when device tz ≠ city tz (set device tz manually).
- [ ] Font-scale 2.0 (vertical strip), dark/light, TalkBack pass on strip + setup + detail.
- [ ] Router-off/offline parity smoke unchanged (`open Salatuk`-style command still rule-routed).

## Task 12: Documentation and Status

- [x] Religious/correctness requirements note before coding → spec §0 (2026-07-13).
- [ ] Architecture ADR after Tasks 3–6 land (source/method/privacy decisions + license/attribution
      records for adhan-java and GeoNames).
- [ ] Completion ADR only after Task 11 device acceptance.
- [ ] Update `ai-context/current-status.md` + `CLAUDE.md` digest.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` if strip behaviour deviates from the artifact.
- [ ] Keep DS-6A explicitly UI-only (Shahada component remains a separate block).

## Full Verification Gate

```text
./gradlew :domain:test :data:prayer:testDebugUnitTest :data:repository:testDebugUnitTest \
  :feature:prayer:testDebugUnitTest :feature:settings:testDebugUnitTest \
  :feature:launcher:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug \
  testDebugUnitTest assembleDebug
```

(JDK-17 toolchain: `export JAVA_HOME=/home/Suleiman/jdks/jdk-17.0.19+10`.)
Device acceptance (Task 11) is required before DS-6B is closed.

## Stop Conditions

Stop and re-scope if:

- golden-test validation against primary source values fails beyond the documented ±2 min tolerance;
- adhan-java license is not MIT at the pinned version;
- timezone/DST correctness is uncertain;
- precise location would need to enter AI/cloud prompts;
- Home would need to show fake or unproven times;
- startup would block on location/calculation;
- the city asset cannot stay within a reasonable size (~≤ 500 KB) without dropping launch-relevant cities;
- implementation drifts into adhan audio, alarms, notifications, Qibla, or automation.

## Agent Start Prompt

Use this prompt for a new session:

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Implement DS-6B Prayer Correctness from:
- docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md (§0 = approved decisions)
- docs/superpowers/plans/2026-07-11-ds6b-prayer-correctness.md (this plan; Tasks 1-2 already complete)

Decisions are final: offline adhan-java calculation, explicit method+madhab at setup (no defaults),
bundled city index + optional one-shot device location, new :data:prayer and :feature:prayer modules,
zero network in v1.

Execute task-by-task with superpowers:subagent-driven-development (fresh implementer + fresh reviewer
per task). Subagent policy — custom agent types are defined in .claude/agents/:
- Tasks 5, 6, 7, 12: subagent_type "implementer" (sonnet, effort high).
- Tasks 3, 8: subagent_type "implementer" with model override "fable" (effort stays high).
- Tasks 4, 9, 10 (correctness-critical): subagent_type "implementer-critical" (fable, effort xhigh).
- Every per-task review: subagent_type "reviewer" (fable, effort high; cannot edit files).
- Never use haiku. Give each subagent ONLY its task text + the spec/plan excerpts it needs.

Hard rules:
- no fake prayer times; no schedule without provenance;
- no startup network/location/calculation wait;
- no permission request on Home entry;
- rounded (2dp) coordinates only; precise location never persisted, logged, or placed in AiRequest;
- adhan-java confined to :data:prayer; core/ui imports no domain/data/location types;
- no adhan audio, alarms, notifications, Qibla, or automation;
- no commits.

Use CodeGraph before reading/editing code. Build with
JAVA_HOME=/home/Suleiman/jdks/jdk-17.0.19+10 (system JDK 25 breaks Gradle); never pipe gradlew
through tail — capture to a log file and check the real exit code.
Before final, run the Full Verification Gate or report exactly why it could not run.
```
