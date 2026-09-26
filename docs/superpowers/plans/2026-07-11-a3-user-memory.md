# A3 - User Memory Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A3 generalizes existing learned preferences and aliases without
> replacing their stores prematurely.

**Goal:** Create a typed User Memory seam over S2-1 and S2-2, with privacy-bounded planner projection.

**Spec:** `docs/superpowers/specs/2026-07-11-a3-user-memory-design.md`.

## Prerequisites

- S2-1 Learned Resolutions closed and tests green.
- S2-2 Alias scope implemented or ready.
- DS-7 Memory Surfaces drafted/available for presentation migration.
- A1 Tool layer if aliases are moving to tool invocations.

## Global Constraints

- No commits unless the owner explicitly asks.
- No cloud memory sync.
- No prompt history.
- No hidden memory from passive sensitive behaviour.
- No private memory in cloud planner prompts by default.
- No replacement of existing stores until adapters are proven.

## Task 1: Baseline Inventory

- [ ] Use CodeGraph to inspect:
      - `ResolutionPreferenceStore`;
      - learned preference policy/use-cases;
      - `AliasStore`;
      - alias use-cases;
      - `MemoryProvidesModule`;
      - DS-7 screen needs.
- [ ] List fields/provenance/retention for each memory type.
- [ ] Run baseline:

```text
./gradlew :domain:test :feature:settings:testDebugUnitTest
```

Acceptance:

- no code edited;
- migration table captured.

## Task 2: Domain Memory Model

- [ ] Add `MemoryItemId`, `MemoryItem`, `MemoryType`.
- [ ] Add provenance, retention, sensitivity, local-only fields.
- [ ] Add `MemoryQuery`.
- [ ] Add `UserMemoryStore` facade interface.

Acceptance:

- pure domain;
- JVM tests;
- no UI/data imports.

## Task 3: Store Adapters

- [ ] Add `PreferenceMemoryAdapter` over `ResolutionPreferenceStore`.
- [ ] Add `AliasMemoryAdapter` over `AliasStore`.
- [ ] Preserve delete-to-relearn.
- [ ] Preserve explicit alias semantics.
- [ ] Avoid unified persistence at this stage.

Acceptance:

- preferences and aliases observable as memory items;
- old tests remain green.

## Task 4: Memory Policy

- [ ] Add retention policy.
- [ ] Add sensitivity classification.
- [ ] Add edit/delete capability flags.
- [ ] Add stale/unavailable handling.

Acceptance:

- policy tests cover local-only, stale, retention, delete.

## Task 5: Planner Projection

- [ ] Add `MemoryProjectionPolicy`.
- [ ] Default to empty/minimal projection.
- [ ] Add allow-list for any planner-visible memory.
- [ ] Add sensitive planted-value tests.

Acceptance:

- no private memory leaves by default;
- cloud planner projection is explicit and tested.

## Task 6: DS-7 Bridge

- [ ] Provide feature-level mappers from memory items to DS-7 UI models.
- [ ] Keep `core/ui` free of memory domain imports.
- [ ] Do not add Memory hub unless product scope approves it.

Acceptance:

- DS-7 can render learned preferences and aliases from one semantic model.

## Full Verification Gate

```text
./gradlew :domain:test :feature:settings:testDebugUnitTest :data:repository:testDebugUnitTest testDebugUnitTest assembleDebug
```

## Stop Conditions

Stop and re-scope if:

- unified persistence migration becomes necessary;
- memory projection needs private data in cloud prompts;
- delete-to-relearn breaks;
- aliases become hidden/passive memory;
- usage history is treated as long-term memory without approval.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A3 User Memory from:
- docs/superpowers/specs/2026-07-11-a3-user-memory-design.md
- docs/superpowers/plans/2026-07-11-a3-user-memory.md

Generalize S2-1 learned preferences and S2-2 aliases through a pure-domain UserMemoryStore facade and
MemoryProjectionPolicy. Preserve existing stores and delete-to-relearn. No cloud memory, prompt history,
hidden passive sensitive memory, or private memory in cloud planner prompts by default.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
