# DS-6B - Prayer Correctness Capability (Design Spec)

> **Status: APPROVED (2026-07-13).** The requirements/authority/privacy decisions below (§0) were made by
> the owner in the 2026-07-13 brainstorming session, completing plan Tasks 1–2 (Requirements and Authority
> Discovery + Privacy Review). Implementation may start per the updated plan. Originally PROPOSED
> 2026-07-11 as a separate religious-correctness capability track for prayer data, provenance, privacy,
> and failure states.
>
> **Important:** DS-6B is not a normal presentation-only design-system block. A wrong prayer time is a real
> product and religious correctness failure. Do not implement prayer calculation casually inside Home,
> `LauncherScreen`, or a visual component.
>
> **Governing sources:** `docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md` section 7,
> `docs/design/SIDR Design System Master Plan.md` DS-6B, `docs/design/SIDR Design Migration Plan v1.1.md`
> DS-6B, `docs/design/SIDR Design and Architecture Audit.md` Prayer Times, and
> `docs/design/SIDR Component Library v1.1.md` `SidrPrayerSummary`.

## 0. Approved Owner Decisions (2026-07-13)

These resolve every open question this spec deferred to "a product decision". Sections below remain the
governing framework; where they said "must be decided", the decision is here.

1. **Locality: global launch, no regional default.** No authority/method is ever guessed from system
   language, SIM, or location. The `MethodRequired` state is a real first-run state.
2. **Method AND madhab are explicit user choices at first prayer setup.** Method list = the standard
   parameterized methods (MWL, ISNA, Egyptian, Umm al-Qura, Karachi, Tehran, Turkey/Diyanet-compatible,
   Singapore, …). Asr madhab (Standard/Hanafi) is a separate mandatory choice — also no default.
3. **Source: maintained calculation library — adhan-java (Batoul Apps, MIT), fully offline.** No network
   anywhere in DS-6B v1: no prayer API, no outbound traffic, so authority-outage handling collapses to
   `CalculationFailed`. The `PrayerAuthority` seam stays in the domain model so official-source adapters
   (e.g., Diyanet) can be added later as a separate block. Provenance source label: `LOCAL CALC`.
   **Amendment (owner, 2026-08-07):** the concrete library is the Kotlin port
   **adhan2 (`com.batoulapps.adhan:adhan2`, MIT declared in POM)**, not the Java port originally named
   "adhan-java". Reason: the Java port `com.batoulapps.adhan:adhan:1.2.1` has no `TURKEY`/Diyanet method
   in its `CalculationMethod` enum (verified against the decompiled jar + source at v1.2.1 and master),
   which would make the §0.2 Turkey/Diyanet requirement impossible without hand-transcribing method
   parameters onto a religious-correctness surface. adhan2 (same Batoul Apps author, same MIT license,
   Kotlin-native) ships every method including TURKEY. The §0.3 "maintained, offline, MIT calculation
   library" intent is unchanged.
4. **Location v1: bundled offline city index + optional one-shot device location.**
   - Primary path: a bundled GeoNames-derived city index (~10–20k cities; name, country, rounded
     lat/lon, tzId; one compressed asset, target ≤ ~500 KB) searched fully offline — works with zero
     permissions.
   - Optional path: a "use device location" button inside prayer settings (never Home) → the existing
     DS-5 permission-education flow → one-shot read → coordinates immediately rounded; no continuous
     tracking. Denial changes nothing: the city path remains primary.
5. **Stored location precision: 2 decimal places (~1.1 km; ≤ ~1 min schedule error).** Cache/settings key
   shape: city ID for index picks, rounded coordinates for device picks; plus display label, tzId, and
   source (`CITY` | `DEVICE`). Exact coordinates are never persisted, never logged, never leave the
   device (v1 has no outbound path at all). Preference keys are `prayer_*`, denylist-clean, inventoried
   in `ALL_KEY_NAMES`; a Block-U-style guard test plants a coordinate/city and proves it cannot enter
   `AiRequest`.
6. **Freshness threshold: end of the current day in the prayer-location timezone.** The persisted last
   schedule exists only for instant first-frame render. Same day + same settings = `CachedFresh`; any
   change of day/method/madhab/location/timezone triggers immediate local recompute (`Updating` →
   `VerifiedCurrent`). `CachedStale` is reachable only when recompute fails: stale times stay visible
   with an explicit STALE label plus `CalculationFailed` — never silently hidden.
7. **Home strip: all five prayers in one quiet mono row, next prayer highlighted (label + marker, never
   colour alone), provenance line underneath** (`LOCAL CALC · <METHOD> · <MADHAB> · <LOCATION LABEL>`).
   The strip renders only after setup is complete — prayer context is opt-in; before setup Home shows
   nothing and no nudge. Sunrise appears in the detail surface only, explicitly marked as not one of the
   five prayers.
8. **Module split (per §10):** new `:data:prayer` (the ONLY module with the adhan-java dependency +
   city-index asset) and new `:feature:prayer` (setup/detail screens, pushed route — no new tab);
   `domain/prayer/` stays pure (no Adhan import); `core/ui` gets `SidrPrayerSummary` only;
   `LauncherViewModel` injecting `GetPrayerContextUseCase` is a sanctioned production change (DS-6B is
   not presentation-only). DS-6A note: the quiet English Shahada in `HomeAnchorSlot` (DS-4) satisfies
   the "stable enough to preserve Home hierarchy" prerequisite; the strip renders below it.
9. **New dependencies (explicit, both confined to `:data:prayer`):** adhan-java (MIT) and the bundled
   city index (GeoNames, CC-BY attribution recorded in the ADR). License review is a plan task gate.

## 1. Goal

Build the capability that lets SIDR show prayer context only when it can be shown truthfully:

- locality is resolved or manually selected;
- authority and calculation method are explicit and user-selectable;
- timezone and DST are correct;
- offline cache is available with freshness;
- stale and unavailable states are visible;
- provenance is always displayed with computed or cached times;
- precise location remains local and never enters `AiRequest`;
- denial of location permission does not break the launcher;
- first frame never waits on network or prayer calculation.

DS-6B enables the prayer strip/summary from the visual direction. It does not automatically add adhan,
alarms, Qibla, prayer notifications, calendar features, or a full religious app surface.

## 2. Non-Negotiable Invariants

- Never show a computed prayer time without provenance.
- Never show plausible-but-unverified static times.
- Never use fake preview data in production Home.
- Never block startup on network, location, or calculation.
- Never send precise location to an LLM, cloud planner, analytics payload, or `AiRequest`.
- Location permission denial must leave Home, Universal Input, Settings, and launcher behaviour usable.
- Manual location must be available.
- Authority/method must be visible and user-selectable.
- Cached data must show freshness.
- Stale cached data must be labelled stale.
- Failure must be visible rather than silently hidden if the user expects prayer context.

## 3. Required Domain Concepts

Names may change during architecture, but the capability must represent these ideas distinctly:

```text
PrayerAuthority
CalculationMethod
PrayerLocation
PrayerLocationSource
TimeZoneState
PrayerName
PrayerInstant
PrayerDaySchedule
PrayerSchedule
PrayerScheduleProvenance
Freshness
UnavailableReason
```

Required prayer names for the first schedule:

```text
Fajr
Dhuhr
Asr
Maghrib
Isha
```

Sunrise may be supported in a detail surface if the selected authority/source provides it, but Home must
not imply it is one of the five daily prayers.

## 4. Authority and Method

The user must be able to see and change:

- authority/source;
- calculation method;
- location;
- timezone basis when relevant.

Default authority/method is a product decision, not a hidden constant. **Decided (§0.1–0.3): global
launch, no default — method and madhab are explicit first-run choices; source is local calculation
(adhan-java), so no authority is contacted in v1.** For Turkiye, a locally accepted source such as
Diyanet may be an appropriate future adapter, but DS-6B does not hardcode that globally or assume the
user's location from system language.

If an official source/API is used:

- document its terms and availability;
- cache responses for offline use;
- handle outage and schema change;
- record provenance and freshness.

If a calculation library is used:

- prefer a maintained, audited library over hand-rolled math;
- verify method parameters against primary/authority sources;
- test timezone and DST transitions;
- record method provenance.

## 5. Location and Privacy

Allowed location sources:

- manual city/region selection;
- coarse/manual stored place;
- device location only after explicit permission and explanation;
- last known device location only if permission is granted and privacy rules are satisfied.

Rules:

- precise coordinates stay on device;
- precise coordinates are not logged;
- precise coordinates are not included in AI prompts;
- precise coordinates are not sent to prayer authority unless that authority/source explicitly requires it
  and the user has approved that data flow;
- manual location must work without location permission;
- location can be changed or cleared;
- stale location state is visible.

DS-6B must define whether cached schedule keys use precise coordinates, rounded coordinates, city IDs, or
authority-specific location IDs before implementation. **Decided (§0.5): city ID for index picks, 2-decimal
rounded coordinates for device picks; exact coordinates are never persisted or logged.**

## 6. Data Freshness and Cache

Required schedule states:

```text
VerifiedCurrent
CachedFresh
CachedStale
NoData
Updating
Failed
```

Rules:

- first frame may render cached fresh/stale/no-data only;
- background refresh must not block Home;
- cache freshness threshold must be explicit;
- schedule day boundaries must use the prayer location timezone, not blindly the device timezone;
- DST changes must invalidate or refresh affected schedules;
- timezone conflict must be visible.

## 7. Required UI States

Production UI must support:

```text
Verified current
Cached fresh
Cached stale
Manual location
Location unavailable
Method required
Authority unavailable
Timezone conflict
Calculation failed
No data yet
Updating
```

Home prayer strip must be quiet and secondary. It may highlight the next prayer, but it must not compete
with the Shahada or Universal Input.

## 8. Public UI Components

`core/ui` may own presentation-only components. It must not own prayer calculation, location access,
cache policy, authority selection, or domain models.

```kotlin
data class SidrPrayerTimeUi(
    val name: String,
    val time: String,
    val isNext: Boolean = false,
)

enum class SidrPrayerSummaryStatus {
    VerifiedCurrent,
    CachedFresh,
    CachedStale,
    ManualLocation,
    LocationUnavailable,
    MethodRequired,
    AuthorityUnavailable,
    TimezoneConflict,
    CalculationFailed,
    NoData,
    Updating,
}

@Composable
fun SidrPrayerSummary(
    prayers: List<SidrPrayerTimeUi>,
    status: SidrPrayerSummaryStatus,
    provenance: String,
    modifier: Modifier = Modifier,
    locationLabel: String? = null,
    onOpenDetails: (() -> Unit)? = null,
)
```

Rules:

- `prayers` may be empty for no-data/failure states;
- `provenance` is required for any non-empty `prayers`;
- status uses label + marker, never colour alone;
- compact Home variant can show a subset only if detail view gives access to all prayers;
- all five prayers must be available somewhere when a schedule exists;
- cached times are not styled as alarming errors unless they are stale/invalid.

## 9. Home Integration

DS-6B integrates through DS-4/DS-6A Home slots after the capability is proven.

Target hierarchy:

```text
Home top row
SidrShahadaHeader
Prayer summary / prayer status
Universal Input
...
```

Rules:

- no remote request before first frame;
- Universal Input remains accessible;
- no hidden loading spinner;
- no fake prayer strip when no data exists;
- no permission prompt on startup;
- no prayer context inside ordinary suggestions;
- prayer detail opens only from a deliberate tap if `onOpenDetails` is supplied.

## 10. Architecture Boundary

Suggested module split may be adjusted, but responsibilities must remain separate:

```text
domain/prayer      pure contracts, schedule model, authority/method model, correctness policy
data/prayer        authority adapters, calculation adapter, cache, timezone/location mappers
feature/prayer     settings/detail UI, presentation mappers
feature/launcher   consumes presentation state only for Home strip
core/ui            SidrPrayerSummary rendering only
```

`core/ui` must not import:

- prayer domain models;
- Android location APIs;
- cache entities;
- authority adapters;
- repositories;
- AI request types.

## 11. Verification

Required:

- authority/method visible;
- provenance visible;
- no schedule without provenance;
- manual location path;
- location permission denied path;
- offline cached fresh path;
- offline cached stale path;
- authority unavailable path;
- timezone conflict path;
- DST transition tests;
- prayer location timezone vs device timezone tests;
- cache freshness tests;
- privacy guard proving precise location does not enter AI requests/logged outbound prompts;
- first frame does not perform network;
- Home no-data state shows no fake times;
- font-scale 2.0;
- TalkBack;
- dark/light;
- RTL smoke.

Suggested technical gate after implementation:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-6B is closed.

## 12. Non-goals

- No adhan.
- No alarms.
- No notifications.
- No Qibla.
- No Islamic calendar feature beyond whatever provenance/detail requires.
- No mosque finder.
- No agentic prayer automation.
- No prayer suggestions mixed into normal app suggestions.
- No remote font/download dependency.
- No production schedule from static mock data.

## 13. Success Criteria

- Prayer times appear only when backed by verified or clearly labelled cached data.
- User can see authority, method, location, and freshness.
- Manual location works without location permission.
- Offline cache works and stale state is clear.
- Precise location remains local and outside AI/cloud prompts.
- Home remains calm, fast, and usable.
- DS-6A remains UI-only; DS-6B owns all prayer correctness.
