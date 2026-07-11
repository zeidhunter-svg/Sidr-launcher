# A1 - Tool and Capability Layer Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A1 is the natural next architectural slice after S2-1/S2-2. It must
> preserve the current launcher action path while introducing the deeper Tool/Capability seam.

**Goal:** Evolve Action Registry v1 into a typed Tool Registry + Tool Executor layer.

**Spec:** `docs/superpowers/specs/2026-07-11-a1-tool-capability-design.md`.

## Prerequisites

- Current AIL-4/AIL-5 router/confirmation/execution tests green.
- S2-2 Alias scope understood if alias-to-tool is included.
- DS-5 action/safety language available for risk/precondition UI.

## Global Constraints

- No commits unless the owner explicitly asks.
- No A4 runtime.
- No automation or accessibility tier implementation.
- No installed-app list or user context in LLM-visible tool schema.
- No replacement of the existing launcher route/execute path in one jump.

## Task 1: Baseline Inventory

- [ ] Use CodeGraph to inspect:
      - `ActionCatalog`;
      - `ActionDescriptor`;
      - `LauncherAction`;
      - `ActionExecutor`;
      - `ExecuteActionUseCase`;
      - `ProposalValidator`;
      - `DefaultActionCatalog`.
- [ ] List existing action families, risks, args, and execution paths.
- [ ] Run baseline:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest
```

Acceptance:

- no code edited;
- compatibility checklist captured.

## Task 2: Pure Domain Tool Contracts

- [ ] Add `ToolId`, `ToolDescriptor`, `ToolInvocation`, `ToolResult`.
- [ ] Add `ToolTier`, `ToolEffect`, `ToolCost`, `ToolPrecondition`.
- [ ] Add `ToolRegistry` and `ToolExecutor`.
- [ ] Keep interfaces small.

Acceptance:

- pure domain;
- JVM tests;
- no Android/data/UI imports.

## Task 3: Action-to-Tool Projection

- [ ] Add adapter/projection from `ActionDescriptor` to `ToolDescriptor`.
- [ ] Preserve current action ids or define stable id mapping.
- [ ] Preserve risk and permission gates.
- [ ] Add output schema placeholders where needed.

Acceptance:

- all current actions visible as tools;
- no behaviour change.

## Task 4: Validation

- [ ] Add tool invocation validator.
- [ ] Validate required args.
- [ ] Reject unknown args.
- [ ] Reject unregistered tools.
- [ ] Reject disallowed tiers for caller context.

Acceptance:

- validator tests cover every current action family.

## Task 5: Executor Compatibility Adapter

- [ ] Add `ToolExecutor` adapter over existing `ExecuteActionUseCase`/resolver/executor path.
- [ ] Preserve safe failure mapping.
- [ ] Preserve confirmation/risk decisions outside direct execution.
- [ ] Keep old call sites intact.

Acceptance:

- current launcher tests green;
- no duplicate execution path.

## Task 6: Router Schema Guard

- [ ] Add safe `ToolSchemaRenderer` or extend current `CatalogSchemaRenderer`.
- [ ] Ensure outbound schema contains capabilities only.
- [ ] Add privacy guard tests for forbidden context terms.

Acceptance:

- no installed apps, memory, calendar, location, history, or credentials in schema.

## Task 7: Alias-to-Tool Path

Start only if S2-2 scope includes it.

- [ ] Add alias target model for tool invocation.
- [ ] Validate alias target against `ToolRegistry`.
- [ ] Handle descriptor changes/stale aliases.
- [ ] Preserve current app alias behaviour during migration.

Acceptance:

- aliases cannot point to unregistered or disallowed tools.

## Full Verification Gate

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

## Stop Conditions

Stop and re-scope if:

- tool layer duplicates the entire action implementation instead of projecting/adapting it;
- risk/precondition metadata diverges from DS-5/A1;
- LLM schema needs private context;
- current route/execute parity breaks;
- accessibility tier work enters scope.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A1 Tool and Capability Layer from:
- docs/superpowers/specs/2026-07-11-a1-tool-capability-design.md
- docs/superpowers/plans/2026-07-11-a1-tool-capability.md

Preserve the current launcher route/confirm/execute path. Introduce a pure-domain ToolRegistry/ToolExecutor
seam by projecting/adapting current ActionCatalog/ExecuteActionUseCase. No A4 runtime, automation,
accessibility tier, or private context in cloud-visible schema.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
