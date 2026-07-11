# A5 - Activity and Trace Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A5 starts only after A4 emits real `ExecutionTrace` events.

**Goal:** Build the privacy-bounded trace projection layer that can safely back DS-8 Activity UI.

**Spec:** `docs/superpowers/specs/2026-07-11-a5-activity-trace-design.md`.

## Prerequisites

- A4 `ExecutionTrace` exists.
- DS-8 Activity components exist or are planned.
- Owner-approved privacy/retention policy.

## Global Constraints

- No commits unless the owner explicitly asks.
- No fake Activity rows.
- No always-on persistent journal by default.
- No raw sensitive content.
- No Activity tab/IA change without approval.

## Task 1: Trace Inventory

- [ ] Inspect A4 trace event model.
- [ ] List event types safe to project.
- [ ] List sensitive payload fields requiring redaction.
- [ ] Compare with existing intent/usage history repositories.

Acceptance:

- source-by-source projection table exists.

## Task 2: Redaction and Projection Policy

- [ ] Add pure domain `ActivityProjectionPolicy`.
- [ ] Add `ActivityRedactionPolicy`.
- [ ] Test search/query/url/location/prompt redaction.
- [ ] Map trace states to Activity statuses.

Acceptance:

- redaction tests green;
- no raw sensitive payload in projected records.

## Task 3: Ephemeral Activity Projection

- [ ] Project current/recent A4 trace events to in-memory Activity records.
- [ ] Feed DS-8 UI models without persistence.
- [ ] Support completed/partial/failed/cancelled/denied.

Acceptance:

- no Room/DataStore changes;
- rows are real trace projections.

## Task 4: Opt-In Persistence

Start only after owner approval.

- [ ] Add persistence setting.
- [ ] Add store with observe/delete/clear/prune.
- [ ] Add max age and max rows.
- [ ] Add clear all.
- [ ] Add tests for opt-in disabled/enabled.

Acceptance:

- default is ephemeral;
- user can clear persisted records.

## Task 5: Conditional UI Surface

- [ ] Decide Settings sub-surface vs later destination.
- [ ] Wire DS-8 components to projected records.
- [ ] No Activity tab unless IA changes.
- [ ] Add empty state explaining ephemeral/persistence mode.

Acceptance:

- Activity shows real records only;
- privacy/provenance visible.

## Full Verification Gate

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:settings:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before closing persistent Activity.

## Stop Conditions

Stop and re-scope if:

- A4 traces do not exist;
- redaction cannot be proven;
- persistence would be on by default;
- Activity starts storing prompts/location/calendar/raw URLs;
- IA change is needed.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A5 Activity and Trace from:
- docs/superpowers/specs/2026-07-11-a5-activity-trace-design.md
- docs/superpowers/plans/2026-07-11-a5-activity-trace.md

Start only after A4 ExecutionTrace exists. Activity is real traces only, ephemeral by default, opt-in persist,
retention capped, redacted, and clearable. Do not add an Activity tab or fake rows.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
