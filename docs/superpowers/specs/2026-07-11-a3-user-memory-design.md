# A3 - User Memory (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A3 generalizes S2-1 Learned Resolutions and S2-2 Aliases into a
> typed, user-controlled memory layer for future planning and personalization.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, ADR "2026-07-10 - Agentic OS target
> architecture (A1-A6)", S2-1 Learned Resolutions, S2-2 Explicit Aliases, DS-7 Memory Surfaces, and current
> `ResolutionPreferenceStore` / `AliasStore`.

## 1. Goal

Create a deep User Memory module:

- represent stored user-affecting knowledge as typed memory items;
- preserve provenance, retention, local-only policy, and edit/delete controls;
- provide planner-safe memory projections;
- keep memory separate from transient usage history;
- ensure private memory never auto-enters cloud.

A3 is not a hidden personalization profile. It is user-owned continuity.

## 2. Current Baseline

Existing memory-like systems:

- S2-1 `ResolutionPreferenceStore` stores learned app resolution preferences.
- S2-1 Learned Choices UI lets the user delete learned choices.
- S2-2 `AliasStore` and alias use-cases exist.
- `ResolveCommandWithPreferenceUseCase` decorates routing with learned preferences.
- `ResolveCommandWithAliasUseCase` decorates unknown commands with explicit aliases.

Current limitations:

- learned preferences and aliases are separate stores/contracts;
- no common `MemoryItem` type;
- no common retention/provenance policy;
- no planner-safe memory projection;
- no unified edit/delete/export model;
- some display models still leak domain directly into feature UI until DS-7 migration.

## 3. Deep Module Shape

Small external interfaces:

```kotlin
interface UserMemoryStore {
    fun observe(query: MemoryQuery = MemoryQuery.All): Flow<List<MemoryItem>>
    suspend fun upsert(item: MemoryItem): OperationResult<Unit>
    suspend fun delete(id: MemoryItemId): OperationResult<Unit>
}

interface MemoryProjectionPolicy {
    fun projectForPlanner(items: List<MemoryItem>, destination: PlannerDestination): MemoryProjection
}
```

The module hides:

- store-specific keys;
- migration from old stores;
- retention rules;
- provenance rendering;
- sensitive memory filtering;
- cloud allow-list projection.

## 4. Core Concepts

```text
MemoryItemId
MemoryItem
MemoryType
MemoryProvenance
MemoryRetention
MemorySensitivity
MemoryPolicy
MemoryQuery
MemoryProjection
MemoryMutation
MemoryConflict
```

Initial memory types:

```text
Preference
Alias
Fact
Dismissed
Policy
```

Only `Preference` and `Alias` are production-backed at first.

## 5. Memory Item Rules

Every memory item must answer:

- what is stored;
- type;
- provenance;
- local/cloud policy;
- retention;
- sensitivity;
- when it was created/updated/used;
- how to edit/delete;
- whether it may feed planning;
- whether it may ever leave device.

Do not expose:

- raw database keys;
- candidate fingerprints;
- implementation confidence;
- hidden profiling categories;
- sensitive facts without explicit user action.

## 6. Store Migration

A3 should not rewrite S2-1/S2-2 in one jump.

Migration path:

```text
ResolutionPreferenceStore -> PreferenceMemoryAdapter
AliasStore -> AliasMemoryAdapter
UserMemoryStore facade -> optional unified persistence later
```

Rules:

- old stores remain authoritative until replacement is proven;
- adapters preserve delete-to-relearn;
- aliases remain explicit user-declared memory;
- no hidden conversion to cloud memory;
- unified persistence requires separate migration plan.

## 7. Planner Projection

Memory can feed A4 only through policy:

- default projection is empty or minimal;
- local-only memory stays local;
- cloud planner receives only explicit allow-listed memory;
- sensitive memory requires explicit user consent/action;
- projection includes provenance/freshness.

Never auto-send:

- learned app preferences;
- aliases;
- private facts;
- dismissed suggestions;
- usage history;
- exact app list.

Any widening of outbound memory requires privacy review and guard tests.

## 8. Relationship to DS-7

DS-7 owns presentation. A3 owns semantics.

DS-7 surfaces must map from A3/old memory to feature-local UI models. `core/ui` still must not import memory
domain models.

## 9. Verification

Required:

- observe preferences through memory facade;
- observe aliases through memory facade;
- delete preference preserves delete-to-relearn;
- delete alias removes explicit alias;
- retention policy applies;
- local-only flag preserved;
- planner projection excludes private memory by default;
- outbound guard tests plant sensitive memory and prove it does not leave;
- old S2-1/S2-2 tests remain green.

## 10. Non-goals

- No cloud memory sync.
- No prompt history.
- No hidden facts from passive behaviour.
- No unified Memory hub unless DS-7/product scope approves it.
- No replacement of usage history with memory.
- No A4 planner integration before projection policy is proven.

## 11. Success Criteria

- SIDR has a common typed memory model.
- Learned preferences and aliases are visible through one memory seam.
- Users can inspect/edit/delete production memory.
- Planner-safe projection exists and is privacy bounded.
- Memory remains user-owned and local by default.
