# A4 - Agent Runtime (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A4 is the missing runtime core that turns SIDR from a smart
> single-action router into a bounded, consent-woven agent loop.
>
> **Prerequisites:** A1 Tool/Capability layer, A2 Context Engine v2, and A3 User Memory must exist before
> production A4 runtime work. DS-9 execution UI remains preview-only until A4 is real.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, ADR "2026-07-10 — Agentic OS target
> architecture (A1-A6)", current AIL-4 `CommandPlanner`, AIL-5 `ExecuteActionUseCase`, DS-5 action/safety,
> and DS-9 execution foundations.

## 1. Goal

Introduce a pure-domain runtime for bounded multi-step work:

```text
goal -> plan -> step gate -> tool call -> observe -> continue/re-plan/stop -> result -> trace
```

This is not a UI block. A4 defines the engine that future Execution Stream, Activity, Agents, and
Automation surfaces must be driven by.

## 2. Current Baseline

Current SIDR is single-shot:

- `RouteCommandUseCase` runs rule-first routing.
- `CommandPlanner` can propose one registered `LauncherAction`.
- `ExecuteActionUseCase` executes one confirmed action.
- `PendingRoutedAction` is transient UI state.
- There is no multi-step plan, no runtime session, no trace store, no pause/resume/cancel loop.

A4 must compose over the existing router/executor path, not replace it.

## 3. Core Domain Concepts

Names may change during implementation, but A4 must model these distinctly:

```text
AgentGoal
Planner
PlanningResult
ExecutionPlan
PlanStep
StepDependency
AgentExecutor
AgentSession
ExecutionTrace
ExecutionState
ConsentCheckpoint
RuntimeLimit
RuntimeBudget
StepObservation
ReplanDecision
AgentResult
```

Required states:

```text
Planning
AwaitingConsent
Running
Paused
Completed
PartiallyCompleted
Failed
Cancelled
Blocked
```

## 4. Planner Contract

Planner is a port in domain:

```kotlin
interface Planner {
    suspend fun plan(request: PlanningRequest): PlanningResult
}
```

Planning request includes:

- user goal;
- reduced `ContextSnapshot`;
- `ToolRegistry`;
- allowed `UserMemory` projection;
- current runtime limits;
- previous trace/observations when re-planning.

Planner order is deterministic-first:

```text
TemplatePlanner -> LocalPlanner -> LlmPlanner
```

Rules:

- rule/template planner runs before model planner;
- cloud planner only receives allow-listed context/memory;
- invalid plan -> `NoPlan`;
- unclear goal -> `Clarify`;
- plan must reference registered tools only;
- plan must declare step risk/preconditions.

## 5. Execution Plan

An `ExecutionPlan` is ordered or DAG-shaped, but must be bounded:

- max step count;
- max duration;
- max cost class;
- allowed tool tiers;
- cancellation semantics;
- consent checkpoints.

Every `PlanStep` must contain:

- tool id;
- validated arguments;
- risk level;
- provenance of why this step exists;
- preconditions;
- expected output shape;
- whether it can be skipped/retried.

No UI may synthesize plan steps.

## 6. Agent Executor

`AgentExecutor` owns the loop:

```text
start session
for each runnable step:
  validate preconditions
  if risk/permission gate trips -> AwaitingConsent
  invoke tool
  record observation
  decide continue/re-plan/stop
finish with result
```

Rules:

- fail closed;
- cancellation is always honoured;
- pause is explicit runtime state;
- duplicate resume/confirm cannot execute a step twice;
- risky steps never auto-run;
- all tool invocations go through A1 `ToolExecutor`;
- every step writes trace events.

## 7. Consent Checkpoints

A4 reuses DS-5 semantics and A1 risk/precondition metadata.

Consent is required when:

- risk is `CONFIRM` or `DANGEROUS`;
- permission is missing;
- tool tier changes upward;
- data leaves device/cloud boundary changes;
- plan step modifies external state;
- runtime re-plans into a more consequential action.

Consent result is part of trace:

```text
granted
denied
cancelled
edited
expired
```

## 8. Execution Trace

`ExecutionTrace` is the source for DS-9 and A5. It must be inspectable and privacy-bounded.

Trace event categories:

- plan created;
- step queued;
- consent requested;
- consent resolved;
- tool invoked;
- tool result;
- replan requested;
- session paused/resumed;
- session cancelled;
- final result.

Trace rules:

- no raw sensitive payload by default;
- redaction happens before persistence;
- traces can be ephemeral;
- persistence is opt-in until A5 retention policy is approved;
- trace IDs are internal and not shown as user-facing meaning.

## 9. Runtime Limits

Every session must have:

- max steps;
- max wall-clock time;
- max consecutive failures;
- max cloud calls;
- max cost class;
- allowed tool tiers;
- cancellation token.

Limit hit maps to `Blocked` or `PartiallyCompleted`, not silent failure.

## 10. Architecture Boundary

Suggested placement:

```text
domain/agent/runtime    Planner, AgentExecutor, state machine contracts
domain/tool             A1 registry/tool contracts
domain/context          A2 snapshot contract
domain/memory           A3 memory projection contract
data/agent              planner adapters, trace persistence if approved
feature/agent_runtime   future session/detail UI, only after engine exists
```

Domain has no Android, network, Room, or UI imports. Data adapters own transport/persistence.

## 11. Verification

Required before production:

- deterministic planner wins before LLM;
- cloud planner receives only allow-listed context/memory;
- invalid plan rejected;
- unregistered tool rejected;
- risk gate pauses before execution;
- consent denial stops or re-plans safely;
- cancellation before/during step prevents further tool calls;
- duplicate resume does not double-execute;
- max steps/time/cost enforced;
- partial completion is honest;
- trace records every step;
- redaction tests prove sensitive payloads are not persisted by default.

## 12. Non-goals

- No Agents screen.
- No Automation editor.
- No accessibility tier execution.
- No background autonomous loops.
- No hidden persistent trace journal.
- No replacement of existing rule-only launcher path.

## 13. Success Criteria

- SIDR has a tested, bounded, fail-closed agent runtime.
- User consent is part of the loop, not a wrapper around it.
- Every step is traceable.
- DS-9 execution UI can be driven 1:1 by runtime state.
- A5 Activity can project from real traces.
