# DS-5 — Action and Safety Family Implementation Plan

> **STATUS: CODE-CLOSED (implemented 2026-07-12 in commit `5c8bc58`; reconciled against this checklist
> 2026-07-13 — ADR: decisions.md "2026-07-13 — DS-5 + auto-hide nav + accent reactivation closed +
> stabilization"). Device acceptance (Task 8) PENDING.** Honest deviations, recorded during
> reconciliation: **Task 1** ran implicitly (no parity-checklist notes were captured); **Task 2's
> `ActionSafetyGallery` + screenshot matrix was NOT delivered** — the family is covered by behavioural
> tests only (`SidrActionGateTest` 7 tests, `SidrActionSafetyTest` 7 tests), gallery goldens remain a
> follow-up; `ConfirmActionCard` production usage is zero but it is not yet marked `@Deprecated`.

**Goal:** Replace legacy routed confirmation and raw permission/error surfaces with SIDR's production
action/safety family while preserving routing, execution, permission, and offline parity.

**Spec:** `docs/superpowers/specs/2026-07-11-ds5-action-safety-design.md`.

## Prerequisites

- DS-3 controls implemented and green:
  - SIDR buttons/chips/rows available;
  - `SidrActionGate` available or ready to move from preview to production;
  - control gallery and Roborazzi matrix green.
- DS-4 may be drafted or implemented, but DS-5 must not require fake Home/agent/activity state.
- Existing launcher/routed action tests green before edits.

## Global Constraints

- No commits unless the owner explicitly asks.
- No edits to DS-3 implementation while a DS-3 agent is active.
- No new routing/execution/domain semantics.
- No new permission feature.
- No new persistence key.
- No fake agent/task/activity/execution state.
- No broad Assistant redesign.
- Keep domain enums out of `core/ui`; map them in feature modules.

## Task 1: Baseline and Surface Inventory

**Purpose:** Confirm the current production action/safety flows before changing presentation.

- [ ] Inspect `ConfirmActionCard`, `PendingActionArea`, `CommandFeedbackArea`, `PermissionEducationScreen`,
      `ErrorState`, and `EmptyState`.
- [ ] List current callbacks for SAFE proposal, CONFIRM proposal, Cancel, permission request, Not now,
      retry, and app settings.
- [ ] Run baseline tests:

```text
./gradlew :feature:launcher:testDebugUnitTest :feature:permission_education:testDebugUnitTest
```

Acceptance:

- no code changes yet;
- parity checklist captured in implementation notes.

## Task 2: Core UI Action/Safety Gallery

**Purpose:** Add screenshot coverage for the family before production migration.

Files:

- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ActionSafetyGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ActionSafetyScreenshotTest.kt`
- optional semantics tests beside existing DS-3 tests.

States:

- SAFE proposal;
- CONFIRM external handoff;
- permission notice;
- privacy notice;
- completed result;
- partial result;
- error;
- offline;
- blocked;
- destructive preview;
- font-scale 2.0;
- dark/light;
- RTL smoke.

Acceptance:

- screenshots make future variants reviewable without activating fake production states.

## Task 3: Action Proposal and Gate Hardening

**Purpose:** Move from DS-3 control availability to production-safe action semantics.

- [x] Add or harden `SidrActionProposal`.
- [x] Harden `SidrActionGate` if DS-3 introduced it:
      - no confirm on render/focus;
      - Cancel exactly once;
      - Confirm exactly once;
      - `confirming` disables controls;
      - long targets wrap;
      - consequence always visible.
- [x] Add tests for duplicate-click prevention where practical.
- [x] Add semantics tests for risk label + marker.

Acceptance:

- `:core:ui:testDebugUnitTest` green;
- no domain/data/feature imports in core UI.

## Task 4: Result, Error, Offline, and Blocked Surfaces

**Purpose:** Create production-ready surfaces for real completion/failure states.

- [x] Add `SidrResultSurface`, `SidrResultTone`, and `SidrSurfaceAction`.
- [x] Add `SidrErrorSurface`.
- [x] Add `SidrOfflineState`.
- [x] Add `SidrBlockedState`.
- [x] Keep future variants preview-only until real callers exist.

Acceptance:

- completed/partial/failed are visually distinct;
- partial success is not labelled completed;
- retry appears only when caller supplies a retry action;
- text wraps at 360dp and font-scale 2.0.

## Task 5: Permission and Privacy Notices

**Purpose:** Migrate Permission Education to the same safety language without changing request flow.

- [x] Add `SidrPermissionNotice`.
- [x] Add `SidrPrivacyNotice`.
- [x] Migrate `PermissionEducationScreen` presentation:
      - rationale remains before request;
      - system dialog only launches from explicit primary tap;
      - `Not now` / Back remains available;
      - permanently denied still opens app settings;
      - feature-specific post-grant action remains unchanged.
- [x] Run `:feature:permission_education:testDebugUnitTest`.

Forbidden:

- no new permission feature;
- no request on screen entry;
- no fake Android permission dialog styling;
- no pressure copy.

Acceptance:

- existing permission tests green;
- denial still affects only the feature;
- launcher core remains available without permission.

## Task 6: Migrate Routed SAFE and CONFIRM Proposals

**Purpose:** Replace `ConfirmActionCard` / legacy one-tap proposal presentation with DS-5 surfaces.

- [x] Map `PendingRoutedAction` to `SidrActionProposal` for SAFE.
- [x] Map CONFIRM/external handoff to `SidrActionGate`.
- [x] Preserve `onConfirm` and `onCancel` callbacks.
- [x] Preserve permission gate routing inside `LauncherScreen`.
- [x] Do not change `LauncherViewModel` execution semantics.
- [x] Add focused UI/semantics tests where practical.
- [x] Run `:feature:launcher:testDebugUnitTest`.

Acceptance:

- URL confirmation: no execution before Continue.
- Play Store confirmation: no execution before Continue.
- Cancel dismisses without action.
- Confirm executes exactly once.
- SAFE proposal still requires deliberate tap.
- router-off/offline parity unchanged.

## Task 7: ErrorState / EmptyState Migration Audit

**Purpose:** Replace generic old states only where DS-5 can improve truthfulness without behavioural churn.

- [x] Identify `ErrorState` callers.
- [x] Migrate callers with meaningful `what/why/next` data to `SidrErrorSurface` (`ErrorState` itself
      now delegates to `SidrErrorSurface`, retry preserved).
- [x] Leave simple empty states alone unless a DS-5 sibling (`SidrOfflineState`, `SidrBlockedState`) is
      semantically correct.
- [x] Avoid creating fake explanation text where no reliable reason exists.

Acceptance:

- no caller loses retry behaviour;
- no generic error is dressed up as a precise diagnosis.

## Task 8: Device Acceptance

Mandatory after code gates:

- [ ] URL confirmation.
- [ ] Play Store confirmation.
- [ ] SAFE action proposal.
- [ ] Cancel.
- [ ] external app handoff.
- [ ] permission education: continue/not now/permanently denied settings path where available.
- [ ] offline/router-off parity smoke.
- [ ] font-scale 2.0 button stacking.

## Task 9: Documentation and Status

- [x] Add DS-5 completion ADR only after implementation and device acceptance *(retro code-close ADR
      written 2026-07-13; a device-acceptance addendum is still required before DS-5 counts as DONE)*.
- [x] Update `ai-context/current-status.md`.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` only if implementation deviates from the artifact.
- [ ] Mark `ConfirmActionCard` deprecated only after production usage reaches zero *(usage IS zero as of
      2026-07-13 — deprecation is a small follow-up)*.

## Full Verification Gate

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:launcher:testDebugUnitTest :feature:permission_education:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-5 is closed.

## Stop Conditions

Stop and re-scope if:

- implementation requires changing `LauncherViewModel` action semantics;
- Permission Education needs a new permission feature;
- result/offline/blocked surfaces would need fake state;
- broad Assistant, Activity, Agent, or Execution Stream work starts creeping in;
- DS-3 active implementation files would need concurrent edits;
- Cancel/Confirm parity fails.
