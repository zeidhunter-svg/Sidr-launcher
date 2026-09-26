# A1 - Tool and Capability Layer (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A1 evolves SIDR's current Action Registry into the Tool/Capability
> layer: the only world-effecting seam future planners and runtimes may cross.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, ADR "2026-07-10 - Agentic OS target
> architecture (A1-A6)", current `ActionCatalog` / `ActionDescriptor` / `LauncherAction` /
> `ActionExecutor` / `ExecuteActionUseCase`, S2-2 Alias work, and DS-5 action/safety.

## 1. Goal

Create a deep module for executable capabilities:

- enumerate what SIDR can do;
- validate tool arguments;
- expose risk, preconditions, tier, effect, and cost;
- execute only through a registered capability;
- provide the stable vocabulary A4 runtime can plan over later.

A1 is not an automation layer. It is the safety boundary that makes future automation possible.

## 2. Current Baseline

Existing seeds:

- `ActionCatalog` lists current action families.
- `ActionDescriptor` has id, title, description, category, risk, args, permission gate.
- `LauncherAction` represents unresolved routed action instances.
- `ActionExecutor` performs concrete `ExecutableAction`s.
- `ExecuteActionUseCase` maps confirmed `LauncherAction` to the existing resolver/executor path.
- `ProposalValidator` already validates LLM-proposed action ids/args against the catalog.

Current limitations:

- action metadata is not yet a full tool contract;
- no output schema;
- no tool tier/effect/cost;
- preconditions are only `permissionGate`;
- executor result vocabulary is still single-action launcher shaped;
- aliases still point to app targets rather than generic tool invocations.

## 3. Deep Module Shape

A1 should present a small external interface:

```kotlin
interface ToolRegistry {
    fun all(): List<ToolDescriptor>
    fun descriptor(id: ToolId): ToolDescriptor?
}

interface ToolExecutor {
    suspend fun invoke(invocation: ToolInvocation): ToolResult
}
```

Complexity stays behind these interfaces:

- argument schema validation;
- precondition evaluation;
- resolver bridging from old actions;
- permission/risk metadata;
- Android intent execution adapters;
- future accessibility tier adapters.

Do not expose a wide family of per-tool methods.

## 4. Core Concepts

Names may change during implementation, but A1 must model:

```text
ToolId
ToolDescriptor
ToolInputSchema
ToolOutputSchema
ToolArgument
ToolInvocation
ToolResult
ToolTier
ToolEffect
ToolCost
ToolPrecondition
ToolRegistry
ToolExecutor
ToolValidationError
```

Tool tiers:

```text
IN_APP
SYSTEM_INTENT
ACCESSIBILITY
```

Tool effects:

```text
LOCAL
EXTERNAL_APP
WEB
CLOUD
SYSTEM
```

Risk continues to use `ActionRiskLevel` or a compatible successor. Do not create a parallel risk vocabulary
unless A1 explicitly replaces the existing type.

## 5. Tool Descriptor Rules

Every registered tool must declare:

- stable id;
- human title and description;
- input schema;
- output schema;
- risk;
- preconditions;
- tier;
- effect;
- cost class;
- whether it can be called by A4 runtime;
- whether it is allowed in aliases;
- whether it is allowed in background/automation contexts.

Rules:

- descriptors are pure metadata;
- enumerating descriptors has no side effects;
- descriptors never include installed-app list, user memory, calendar, location, or prompt data;
- LLM-visible schema is a safe projection of descriptors, not raw implementation detail.

## 6. Invocation and Result

`ToolInvocation` carries:

- tool id;
- validated arguments;
- caller/source;
- optional consent/session id when A4 exists;
- provenance for why it was invoked.

`ToolResult` must distinguish:

```text
Success
Failed
Unsupported
Blocked
NeedsPermission
NeedsConfirmation
Cancelled
```

Rules:

- world effects happen only through `ToolExecutor`;
- blocked/precondition failures do not execute;
- unsafe failures return safe messages/categories;
- no stack traces or Android exceptions leak to UI.

## 7. Migration From Action Registry

A1 should grow from current actions without breaking Stage 1:

```text
ActionId -> ToolId
ActionDescriptor -> ToolDescriptor
LauncherAction -> ToolInvocation projection or compatibility wrapper
ActionCatalog -> ToolRegistry
ActionExecutor / ExecuteActionUseCase -> ToolExecutor adapter
```

The existing route/confirm/execute path must keep working during migration.

Recommended compatibility:

- keep `ActionCatalog` while introducing `ToolRegistry`;
- add an adapter/projection from action descriptors to tool descriptors;
- migrate S2-2 aliases to tool invocations only after A1 validation is proven;
- remove old action surfaces only after all consumers are moved.

## 8. S2-2 Alias Relationship

A1 immediately improves aliases:

Current:

```text
alias phrase -> app package target
```

Target:

```text
alias phrase -> registered tool invocation
```

Rules:

- alias target must reference a registered tool;
- alias args must validate against schema;
- alias cannot point to disallowed tiers;
- alias cannot silently become more dangerous after tool descriptor changes;
- stale alias must require reconfirmation or be disabled.

## 9. Privacy and Outbound Schema

Planner-visible tool schema may include:

- tool id;
- title;
- description;
- argument names/descriptions;
- risk/effect/tier if needed.

It must not include:

- installed apps;
- usage history;
- user memory values;
- location;
- calendar;
- contacts;
- prompt history;
- credentials.

Any widening of `OutboundContextPolicy.AllowedContext` requires explicit privacy review.

## 10. Verification

Required:

- all current actions projected to tools;
- schema validation accepts valid args;
- schema validation rejects missing/unknown/wrong args;
- unregistered tool rejected;
- risk/preconditions preserved;
- permission-gated tool blocks without permission;
- executor returns safe failure;
- current launcher route/execute tests remain green;
- router outbound schema guard remains green;
- alias to tool invocation validates.

## 11. Non-goals

- No A4 runtime.
- No automation.
- No accessibility tier implementation.
- No Agents UI.
- No broad replacement of current executor path in one step.
- No installed-app list in cloud schema.

## 12. Success Criteria

- SIDR has a small, deep ToolRegistry/ToolExecutor interface.
- Existing actions are represented as tools without behaviour regression.
- Future planners can reason over capabilities safely.
- S2-2 aliases can target tool invocations.
- Tools are the only future world-effecting seam.
