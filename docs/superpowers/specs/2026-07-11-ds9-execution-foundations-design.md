# DS-9 - Execution Foundations (Design Spec)

> **Status: PROPOSED (2026-07-11).** DS-9 defines future multi-step execution presentation contracts.
>
> **Important:** No production execution UI is allowed until Stage 2/3 has a real execution model. Do not
> map a single network request or single AIL-5 action into a fake multi-step plan.
>
> **Governing sources:** `docs/design/SIDR Design Migration Plan v1.1.md` DS-9,
> `docs/design/SIDR Design System Master Plan.md` Conditional Future Blocks, current AIL-4/AIL-5 router and
> execution implementation.

## 1. Goal

Prepare visual contracts for future agentic execution:

- plan;
- step;
- stream/log;
- consent checkpoints;
- pause/cancel affordances;
- local/cloud/web provenance per meaningful step;
- completed/partial/failed states.

DS-9 is a **future-contract block**. It may produce components, previews, screenshots, and API review. It
must not create production runtime behaviour.

## 2. Current Production Baseline

Real execution today:

- AIL-4 `CommandPlanner` returns a single `PlanResult.RoutedAction`, `Clarify`, or `NoPlan`.
- AIL-5 `ExecuteActionUseCase` executes one confirmed `LauncherAction` through the existing resolver and
  executor path.
- `PendingRoutedAction` is a UI/runtime pending proposal, not a durable execution trace.
- There is no `ExecutionPlan`, no `PlanStep`, no `AgentSession`, no pause/resume runtime, and no execution
  trace store.

Therefore DS-9 production integration is blocked.

## 3. Required Future Runtime Concepts

Production integration requires real equivalents of:

```text
ExecutionPlan
PlanStep
ExecutionTrace
ExecutionState
ConsentCheckpoint
AgentSession
PauseCancelState
StepProvenance
```

The UI must be driven by these real concepts, not by decorative progress rows.

## 4. Public Component Family

`core/ui` may contain preview-only presentation components:

```kotlin
enum class SidrExecutionStepStatus {
    Pending,
    Running,
    WaitingForConsent,
    Completed,
    Partial,
    Failed,
    Cancelled,
    Skipped,
}

enum class SidrExecutionProvenance {
    Local,
    Device,
    Web,
    Cloud,
    ExternalApp,
}

data class SidrExecutionStepUi(
    val id: String,
    val title: String,
    val status: SidrExecutionStepStatus,
    val provenance: SidrExecutionProvenance,
    val detail: String? = null,
    val duration: String? = null,
)

@Composable
fun SidrExecutionPlan(
    title: String,
    steps: List<SidrExecutionStepUi>,
    modifier: Modifier = Modifier,
    summary: String? = null,
)

@Composable
fun SidrExecutionStep(
    step: SidrExecutionStepUi,
    modifier: Modifier = Modifier,
)

@Composable
fun SidrExecutionStream(
    steps: List<SidrExecutionStepUi>,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
)
```

Rules:

- status is label + marker, not colour alone;
- provenance is visible for meaningful boundaries;
- consent checkpoints use DS-5 gates;
- pause/cancel appear only when runtime supports them;
- no fake timing/duration;
- no animation that implies real progress without runtime updates.

## 5. Production Gate

DS-9 production integration is allowed only after A4 runtime exists with:

- real plan generation or plan representation;
- real step state machine;
- real consent checkpoint state;
- real cancellation semantics;
- real failure/partial result semantics;
- trace lifecycle;
- privacy/redaction rules.

A single routed action is not a plan.
A single network call is not a stream.
Streaming assistant text is not an execution trace.

## 6. Accessibility

Required:

- plan summary readable before step list;
- current/running step announced;
- consent-required state announced;
- pause/cancel labels are explicit;
- long step titles wrap;
- font-scale 2.0;
- no colour-only status;
- reduced-motion friendly.

## 7. Verification

Component-only gate:

- pending plan;
- running step;
- waiting for consent;
- completed;
- partial;
- failed;
- cancelled;
- local/cloud/web/external provenance;
- pause/cancel available;
- pause/cancel absent;
- dark/light;
- font-scale 2.0;
- RTL smoke;
- no `core/ui` domain/data/runtime imports.

Production gate after A4:

- runtime drives every step;
- cancellation is real;
- consent cannot be bypassed;
- partial result is honest;
- trace privacy/redaction tests pass;
- no fake steps for single actions.

## 8. Non-goals

- No production execution screen.
- No Activity feed.
- No Agents surface.
- No automation editor.
- No fake multi-step task flow.
- No new runtime semantics in UI.

## 9. Success Criteria

- Future execution components can be reviewed visually.
- Production integration remains blocked until runtime exists.
- DS-5 consent and result semantics are reused, not redefined.
