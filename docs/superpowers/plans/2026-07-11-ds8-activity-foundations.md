# DS-8 - Activity Foundations Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** DS-8 prepares Activity patterns. It may stay component-only until a
> real Activity domain/use-case plan exists.

**Goal:** Add honest Activity presentation foundations without inventing persistence or exposing sensitive
history.

**Spec:** `docs/superpowers/specs/2026-07-11-ds8-activity-foundations-design.md`.

## Prerequisites

- DS-3 controls and DS-5 safety components available.
- DS-7 memory semantics known for learned-memory events.
- Owner-approved Activity domain/use-case scope before production navigation or persistence.

## Global Constraints

- No commits unless the owner explicitly asks.
- No new Room table just to populate Activity.
- No fake activity feed.
- No raw sensitive command/prompt/location/calendar content.
- No Activity destination before real source mapping and privacy review.
- No Agents/Automation/Execution Stream work in DS-8.

## Task 1: Source Inventory

- [ ] Use CodeGraph to inspect existing history repositories:
      - `IntentMatchHistoryRepository`;
      - `UsageHistoryRepository`;
      - learned resolution stores/use-cases;
      - routed action runtime state.
- [ ] Decide which records are safe to show now.
- [ ] List redaction rules for each candidate source.
- [ ] List retention rules already implemented by each repository.

Acceptance:

- no code edited;
- notes identify which sources are real, conditional, or forbidden.

## Task 2: Component API and Gallery

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrActivityItem.kt`
- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrActivityTimeline.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ActivityGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrActivityScreenshotTest.kt`

States:

- command;
- app launch;
- memory learned;
- permission;
- failed;
- cancelled;
- blocked;
- local-only;
- external/cloud preview;
- empty timeline.

Acceptance:

- `core/ui` imports no domain/data types;
- screenshot matrix covers dark/light/font-scale 2.0/RTL smoke;
- status is not colour-only.

## Task 3: Privacy and Retention Decision

Required before production Activity:

- [ ] ephemeral vs persisted default;
- [ ] retention cap;
- [ ] clear-all semantics;
- [ ] per-row delete semantics, if any;
- [ ] export policy, if any;
- [ ] opt-in requirement for sources beyond current repositories.

Acceptance:

- owner-approved privacy notes or ADR.

## Task 4: Conditional Production Mapping

Start only after Task 3.

- [ ] Add feature-local `ActivityItemUiModel`.
- [ ] Map only approved real records.
- [ ] Preserve existing repository retention.
- [ ] Redact raw sensitive content.
- [ ] Avoid Assistant prompt/reply persistence.

Acceptance:

- every visible row has a real source;
- no raw sensitive text by default;
- tests cover redaction.

## Task 5: Conditional Surface

Start only after production mapping is approved.

- [ ] Decide Settings sub-surface vs future Activity destination.
- [ ] Add navigation only if approved.
- [ ] Add empty state that explains no persistent activity exists yet.
- [ ] Add clear/delete only if data source supports it.

Acceptance:

- no navigation to fake feed;
- no Activity tab unless IA is explicitly changed.

## Full Verification Gate

Component-only:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
```

Production, if scoped:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:settings:testDebugUnitTest testDebugUnitTest assembleDebug
```

## Stop Conditions

Stop and re-scope if:

- a new persistence table is needed only to fill the UI;
- raw sensitive content would be shown;
- Activity starts representing transient AIL-5 UI state as durable history;
- Agents/Automation/Execution traces enter the scope without A4/A5.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on DS-8 Activity Foundations from:
- docs/superpowers/specs/2026-07-11-ds8-activity-foundations-design.md
- docs/superpowers/plans/2026-07-11-ds8-activity-foundations.md

Start with source inventory. Do not create new persistence or production Activity navigation unless the
owner has approved the Activity domain/use-case and privacy scope.

Forbidden: fake feed, raw sensitive command/prompt/location/calendar content, Activity tab, Agents,
Automation, Execution Stream.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
