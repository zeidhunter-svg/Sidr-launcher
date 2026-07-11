# DS-6B - Prayer Correctness Capability Plan

> **STATUS: PROPOSED (2026-07-11).** This plan fills the missing DS-6B block. It is deliberately
> architecture-first because prayer times are a religious-correctness surface, not a decorative widget.

**Goal:** Define and implement the prayer data capability required before SIDR can show a production prayer
summary in Home.

**Spec:** `docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md`.

## Prerequisites

- DS-3 controls implemented and green.
- DS-4 Home shell implemented or ready to expose a prayer-summary slot.
- DS-5 action/safety available for permission/privacy/blocked states.
- DS-6A Sacred Header implemented or at least stable enough to preserve Home hierarchy.
- Owner approval of religious/correctness requirements before coding.

DS-6B may be planned now, but production code should not start until the requirements and architecture gates
below are complete.

## Global Constraints

- No commits unless the owner explicitly asks.
- Do not edit active DS-3 implementation files while another agent is working there.
- No fake prayer times.
- No startup network dependency.
- No permission prompt on Home entry.
- No precise location in `AiRequest`, cloud planner prompts, logs, or analytics.
- No casual hand-written prayer math inside UI.
- No adhan, alarms, notifications, Qibla, mosque finder, or automation in this block.
- No prayer data mixed into ordinary suggestions.
- No production Home strip until provenance/freshness/failure states are implemented.

## Task 1: Requirements and Authority Discovery

**Purpose:** Decide what "correct" means before writing code.

- [ ] Identify target launch locality and first supported region(s).
- [ ] Decide default authority/method policy.
- [ ] Decide whether first implementation uses:
      - official authority/API;
      - local bundled table;
      - maintained calculation library;
      - hybrid.
- [ ] Capture primary source links/documents for authority/method parameters.
- [ ] Decide supported prayer names for Home and detail.
- [ ] Decide whether Sunrise appears in detail only.
- [ ] Decide cache freshness threshold.
- [ ] Decide stale schedule behaviour.
- [ ] Decide manual location UX and storage rules.

Acceptance:

- requirements notes are written before code;
- owner approves first authority/method/locality decision;
- no implementation yet.

## Task 2: Privacy Review

**Purpose:** Lock down location boundaries before any adapter exists.

- [ ] Define location precision used for schedule calculation.
- [ ] Define cache key shape:
      - city ID;
      - authority location ID;
      - rounded coordinates;
      - exact coordinates, only if unavoidable.
- [ ] Define what may be logged.
- [ ] Define what may be sent to a prayer authority/source.
- [ ] Add explicit rule that precise location never enters `AiRequest`.
- [ ] Decide manual location clear/change flow.
- [ ] Decide permission-denied copy.

Acceptance:

- privacy notes exist;
- no prompt/cloud/outbound path can include precise location;
- manual location works without permission.

## Task 3: Domain Architecture

**Purpose:** Build pure prayer contracts before data/UI.

Candidate files:

- `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerModels.kt`
- `domain/src/main/java/com/sidr/launcher/domain/prayer/PrayerScheduleRepository.kt`
- `domain/src/main/java/com/sidr/launcher/domain/prayer/GetPrayerContextUseCase.kt`
- tests under `domain/src/test/java/com/sidr/launcher/domain/prayer/`.

Model concepts:

- `PrayerAuthority`;
- `CalculationMethod`;
- `PrayerLocation`;
- `PrayerLocationSource`;
- `TimeZoneState`;
- `PrayerName`;
- `PrayerInstant`;
- `PrayerDaySchedule`;
- `PrayerScheduleProvenance`;
- `Freshness`;
- `UnavailableReason`.

Acceptance:

- pure JVM/domain tests;
- no Android imports;
- no data/cache/location implementation in domain;
- no UI strings in domain.

## Task 4: Calculation or Authority Adapter Spike

**Purpose:** Prove the data source before committing to production integration.

- [ ] If using a library, add it only after dependency/license review.
- [ ] If using an API, create a small adapter spike and cache contract.
- [ ] If using official tables, define update/source process.
- [ ] Validate known dates/locations against primary source expected values.
- [ ] Validate timezone and DST transitions.
- [ ] Validate offline/no-network behaviour.

Acceptance:

- known-location golden tests exist;
- DST tests exist;
- failures map to explicit unavailable reasons;
- no UI integration yet.

## Task 5: Cache and Freshness

**Purpose:** Support first-frame offline rendering without lying.

- [ ] Add storage model only after domain and privacy decisions are approved.
- [ ] Store schedule with authority/method/location/timezone/provenance/freshness metadata.
- [ ] Never cache unlabelled times.
- [ ] Expose cached fresh vs cached stale.
- [ ] Invalidate on authority/method/location/timezone changes.
- [ ] Background refresh must not block Home.

Acceptance:

- cached fresh test;
- cached stale test;
- timezone change invalidation test;
- first-frame path uses local cache/no-data only.

## Task 6: Settings and Permission Flow

**Purpose:** Let users choose method/location without surprise permission requests.

- [ ] Add prayer settings/detail surface only after route/product scope is approved.
- [ ] Manual location is available without permission.
- [ ] Device location request uses DS-5 permission/privacy language.
- [ ] Denial keeps launcher usable.
- [ ] Authority/method visible and changeable.
- [ ] Location can be changed/cleared.

Acceptance:

- no permission request on Home entry;
- denial path tested;
- settings persistence tested if a new preference is introduced.

## Task 7: Core UI Prayer Summary

**Purpose:** Add presentation components after data states are defined.

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrPrayerSummary.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/PrayerSummaryGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrPrayerSummaryScreenshotTest.kt`
- semantics tests as needed.

States:

- verified current;
- cached fresh;
- cached stale;
- manual location;
- location unavailable;
- method required;
- authority unavailable;
- timezone conflict;
- calculation failed;
- no data;
- updating;
- font-scale 2.0;
- dark/light;
- RTL smoke.

Acceptance:

- `core/ui` has no domain/data/location imports;
- no non-empty schedule can render without provenance;
- all labels are text/marker based, not colour-only.

## Task 8: Home Integration

**Purpose:** Add prayer context only after capability is truthful.

Prerequisites:

- DS-4 Home slot exists.
- DS-6A hierarchy is stable.
- Prayer context use-case exposes verified/cached/failure state.

Steps:

- [ ] Map domain prayer context to feature-local UI model.
- [ ] Render `SidrPrayerSummary` below `SidrShahadaHeader`.
- [ ] Do not request permission or network on first frame.
- [ ] Show no fake times when data is missing.
- [ ] Keep Universal Input reachable.
- [ ] Open detail/settings only via explicit tap if scoped.

Acceptance:

- Home no-data state remains calm;
- cached state shows freshness;
- verified state shows provenance;
- startup tests show no network wait.

## Task 9: Privacy and AI Guards

**Purpose:** Prove prayer location cannot leak into AI/cloud.

- [ ] Add tests around outbound AI prompt/body allow-list if necessary.
- [ ] Plant a fake coordinate/city detail and prove it does not leave through `AiRequest`.
- [ ] Ensure logs redact or omit precise location.
- [ ] Ensure prayer source outbound requests, if any, are scoped to the prayer adapter only.

Acceptance:

- privacy guard tests green;
- no AI/cloud path receives precise prayer location.

## Task 10: Device Acceptance

Mandatory before DS-6B is closed:

- [ ] first frame with no prayer data;
- [ ] manual location setup;
- [ ] verified current schedule with provenance;
- [ ] airplane/offline cached fresh;
- [ ] stale cached state;
- [ ] permission denied path;
- [ ] method/authority change;
- [ ] timezone/DST sanity check where feasible;
- [ ] font-scale 2.0;
- [ ] dark/light;
- [ ] TalkBack.

## Task 11: Documentation and Status

- [ ] Add religious/correctness requirements note or ADR before coding.
- [ ] Add architecture ADR after source/method/privacy decisions.
- [ ] Add completion ADR only after device acceptance.
- [ ] Update `ai-context/current-status.md`.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` if Home prayer-strip behaviour intentionally
      differs from the artifact.
- [ ] Keep DS-6A explicitly UI-only.

## Full Verification Gate

Expected after implementation:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:launcher:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-6B is closed.

## Stop Conditions

Stop and re-scope if:

- no approved authority/method decision exists;
- validation against primary source values fails;
- timezone/DST correctness is uncertain;
- precise location would need to enter AI/cloud prompts;
- Home would need to show fake or unproven times;
- startup would block on network/location/calculation;
- implementation drifts into adhan, alarms, notifications, Qibla, or automation.

## Agent Start Prompt

Use this prompt for a new session:

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on DS-6B Prayer Correctness Capability from:
- docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md
- docs/superpowers/plans/2026-07-11-ds6b-prayer-correctness.md

This is not a presentation-only task. Start with requirements/authority/privacy discovery. Do not implement
prayer times in Home until authority/method/locality/cache/privacy decisions are approved.

Hard rules:
- no fake prayer times;
- no startup network dependency;
- no permission request on Home entry;
- no precise location in AiRequest, cloud planner prompts, logs, or analytics;
- no casual hand-written prayer math inside UI;
- no adhan, alarms, notifications, Qibla, or automation;
- no commits.

Use CodeGraph before reading/editing code. If DS-3 implementation is still active, do not touch those files.
Before final, run relevant gates or report exactly why they could not run.
```
