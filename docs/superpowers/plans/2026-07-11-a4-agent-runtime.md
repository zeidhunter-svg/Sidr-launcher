# A4 - Agent Runtime Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A4 must not begin as production code until A1 tools, A2 context, and
> A3 memory projections exist. This plan is the architecture handoff for that future slice.

**Goal:** Build the bounded plan-execute-observe runtime that unblocks real Execution Stream and Activity.

**Spec:** `docs/superpowers/specs/2026-07-11-a4-agent-runtime-design.md`.

## Prerequisites

- A1 Tool/Capability registry and executor contracts exist.
- A2 ContextSnapshot exists with outbound allow-list.
- A3 UserMemory projection exists with local/cloud policy.
- DS-5 action/safety gates exist.
- DS-9 execution components remain preview-only until this runtime lands.

## Global Constraints

- No commits unless the owner explicitly asks.
- No production execution UI before runtime is real.
- No hidden autonomous/background loop.
- No accessibility tier tools.
- No raw sensitive context/memory in cloud planner requests.
- No replacement of current rule-only launcher path.

## Task 1: Baseline and Runtime Gap Audit

- [ ] Use CodeGraph to inspect `CommandPlanner`, `RouteCommandUseCase`, `ExecuteActionUseCase`,
      `ActionExecutor`, `PendingRoutedAction`, and `CommandOutcome`.
- [ ] Document single-action behaviour and parity invariants.
- [ ] List A1/A2/A3 contracts available at implementation time.

Acceptance:

- no code edited;
- gap list confirms A4 is not a UI refactor.

## Task 2: Domain State Machine

- [ ] Add pure domain runtime models.
- [ ] Add `AgentSession` state machine.
- [ ] Add runtime limits/budget model.
- [ ] Add cancellation semantics.
- [ ] Add unit tests for legal/illegal transitions.

Acceptance:

- JVM tests;
- no Android/data/UI imports;
- impossible transitions rejected.

## Task 3: Planner Port and Validators

- [ ] Add `Planner` port and `PlanningResult`.
- [ ] Add deterministic planner chain shell:
      - template;
      - local;
      - cloud.
- [ ] Add plan validator:
      - registered tools only;
      - args schema valid;
      - risk/preconditions present;
      - runtime limits respected.

Acceptance:

- invalid plans fail closed;
- planner order is deterministic-first;
- cloud request uses A2/A3 allow-lists.

## Task 4: AgentExecutor Loop

- [ ] Implement bounded step loop.
- [ ] Invoke A1 tools only through `ToolExecutor`.
- [ ] Pause on consent checkpoints.
- [ ] Support cancel/pause/resume.
- [ ] Record observations.
- [ ] Stop with completed/partial/failed/cancelled/blocked result.

Acceptance:

- risky step does not execute before consent;
- cancel prevents later tool calls;
- duplicate resume cannot double-run a step.

## Task 5: Trace Events

- [ ] Define `ExecutionTrace` and trace event model.
- [ ] Keep traces ephemeral by default unless A5 persistence scope exists.
- [ ] Redact payloads before any persistence boundary.
- [ ] Add projection hooks for DS-9/A5 UI.

Acceptance:

- every step produces trace events;
- sensitive payloads excluded by default.

## Task 6: Integration Shell

- [ ] Add feature/use-case entry point only after domain loop is tested.
- [ ] Keep current launcher route/execute path intact.
- [ ] Add feature flag for runtime experiment if needed.
- [ ] Do not wire Home/Execution UI yet unless explicitly scoped.

Acceptance:

- router-off/offline parity remains intact;
- AIL-5 single-action flow still works.

## Full Verification Gate

Expected after implementation:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before any production execution UI uses A4.

## Stop Conditions

Stop and re-scope if:

- A1/A2/A3 contracts are missing;
- a planner can call unregistered tools;
- risk gates can be bypassed;
- cancellation cannot be enforced;
- trace privacy/redaction is unclear;
- UI starts inventing runtime state.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A4 Agent Runtime from:
- docs/superpowers/specs/2026-07-11-a4-agent-runtime-design.md
- docs/superpowers/plans/2026-07-11-a4-agent-runtime.md

Do not start production code unless A1 tools, A2 context, and A3 memory projections exist. Preserve the
current rule-only/single-action launcher path. Build a pure-domain bounded runtime with consent checkpoints,
cancellation, and traces. Do not add production Execution/Agents UI.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
