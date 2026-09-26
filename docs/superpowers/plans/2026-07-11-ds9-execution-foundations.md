# DS-9 - Execution Foundations Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** DS-9 is a future-contract component block. Production integration is
> blocked until A4 runtime exists.

**Goal:** Create reviewable execution presentation contracts without faking a runtime.

**Spec:** `docs/superpowers/specs/2026-07-11-ds9-execution-foundations-design.md`.

## Prerequisites

- DS-3 controls and DS-5 action/safety components available.
- A4 runtime before production use.
- Owner approval before adding any navigation or runtime-like surface.

## Global Constraints

- No commits unless the owner explicitly asks.
- No production execution screen.
- No fake multi-step plan from a single routed action.
- No mapping Assistant streaming to execution trace.
- No pause/cancel controls unless runtime supports them.
- No new domain runtime semantics inside UI.

## Task 1: Runtime Gap Inventory

- [ ] Use CodeGraph to inspect current AIL-4/AIL-5:
      - `CommandPlanner`;
      - `PlanResult`;
      - `ExecuteActionUseCase`;
      - `PendingRoutedAction`;
      - `ActionExecutor`;
      - `CommandOutcome`.
- [ ] Document that current execution is single-action.
- [ ] List missing runtime concepts needed for production execution UI.

Acceptance:

- no code edited;
- notes explicitly block production integration.

## Task 2: Component API Review

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrExecutionPlan.kt`
- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrExecutionStep.kt`
- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrExecutionStream.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ExecutionGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrExecutionScreenshotTest.kt`

States:

- pending;
- running;
- waiting for consent;
- completed;
- partial;
- failed;
- cancelled;
- skipped;
- local/cloud/web/external provenance.

Acceptance:

- preview-only models;
- no domain/data/runtime imports;
- no production caller.

## Task 3: DS-5 Integration Check

- [ ] Confirm consent checkpoint uses DS-5 gate vocabulary.
- [ ] Confirm result/partial/failure uses DS-5 result vocabulary.
- [ ] Confirm risk labels are not colour-only.

Acceptance:

- DS-9 does not invent a parallel consent/result language.

## Task 4: Accessibility and Motion

- [ ] Add semantics for running/waiting/completed states.
- [ ] Add explicit pause/cancel labels in previews.
- [ ] Verify font-scale 2.0 and RTL.
- [ ] Avoid continuous progress animation in static preview states.

Acceptance:

- screenshots and semantics tests green.

## Task 5: Production Integration Placeholder

Do not implement now. When A4 runtime exists, a future plan must cover:

- runtime -> UI presentation mapper;
- trace lifecycle;
- cancellation semantics;
- consent checkpoint transitions;
- privacy/redaction;
- Activity bridge;
- device acceptance.

## Full Verification Gate

Component-only:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
```

Production gate is intentionally absent until A4 runtime exists.

## Stop Conditions

Stop and re-scope if:

- a single action is represented as multiple fake steps;
- UI code needs to invent runtime state;
- navigation is added;
- Agents/Automation work enters the scope;
- pause/cancel cannot be backed by real runtime semantics.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on DS-9 Execution Foundations from:
- docs/superpowers/specs/2026-07-11-ds9-execution-foundations-design.md
- docs/superpowers/plans/2026-07-11-ds9-execution-foundations.md

This is preview/component-only unless A4 runtime exists. Do not add production navigation or map a single
AIL-5 action into a fake multi-step execution plan.

Use CodeGraph before reading/editing code. Run relevant component gates or report why they could not run.
```
