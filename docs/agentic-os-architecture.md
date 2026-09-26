# SIDR — Agentic OS Target Architecture (A1–A6)

> **Status: TARGET ARCHITECTURE (owner-approved direction, 2026-07-10).** This is the north-star
> engineering architecture for evolving SIDR from a smart command-router launcher into an original,
> local-first **AI agentic OS**. It is a *direction*, not a committed plan — each layer becomes a real
> slice (spec → plan → build) in the project's existing style (feature-first, ports, rule-first,
> fail-closed, privacy-bounded). It refines the three-stage roadmap ([roadmap.md](roadmap.md)): A1–A3 flesh
> out **Stage 2 (AI Framework)**, A4–A5 bridge into **Stage 3 (Agentic OS)**, A6 is Stage 3 proper.
>
> Visual identity for these surfaces is governed separately by
> [docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md](superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md).
>
> **Amended 2026-08-19 by the four strategic ADRs of Этап 1 of**
> [docs/superpowers/plans/2026-08-18-agentic-track-restart.md](superpowers/plans/2026-08-18-agentic-track-restart.md)
> — the layers A1–A6 stand; four things around them changed:
> 1. **Deterministic-first is redefined** (ADR 1/4) — §2.2 and §4.2 below are rewritten in place.
> 2. **Tools come from the OS as well as from us** (ADR 2/4) — `AppFunctions` and `MCP` are first-class
>    tool sources, so A1 becomes **A1′, a federation of adapters over one vocabulary** (Этап 5 rewrites
>    the A1 spec). ONNX is closed; the designated local-inference runtime is LiteRT / LiteRT-LM.
> 3. **The "Framework" is a portable core with two consumers** — Android and PC (ADR 3/4). The layers
>    below are its contents; `ActionIds`' seven values are frozen byte-for-byte.
> 4. **Assistant and Agent share one loop, with two surfaces** (ADR 4/4) — a 0-step plan is a spoken
>    reply; an N-step plan is a task.
>
> **Build order is superseded** by §5 below.

## 0. Framing — what "agentic" requires that SIDR does not yet have

Today the pipeline is **single-shot**: `command → one proposed action → confirm → execute`
(`RouteCommandUseCase` → `CommandPlanner` → `ExecuteActionUseCase`). That is a *smart router*, not an agent.

Agentic means a **bounded plan-execute loop**:

```
goal → PLAN (multi-step) → per-step: gate → tool call → observe → (re-plan) → … → RESULT → trace
```

with consent woven *into* the loop and the user owning it (cancel/pause mid-flight). None of that engine
exists — the imported design docs draw its *UI* (Execution Stream, Agent Card, Activity) with no domain
behind it. **The originality and the hard work of an agentic OS live in four missing layers: tools,
context, memory, and the runtime.** The visual identity polishes the shell; this document is the engine.

### 0.1 What already suits it (assets — grow, don't replace)

- **Pure domain on ports + no `feature→feature` + single NavHost + fail-closed `OperationResult`** — a safe
  agent runtime can be a *pure-domain* layer with tool ports. This discipline is the biggest asset.
- **`ActionCatalog` / `ActionDescriptor` / `ActionRiskLevel` (SAFE/CONFIRM/DANGEROUS) / `LauncherAction`** —
  the seed of the tool / capability / consent model.
- **`OutboundContextPolicy` allow-list + BYOK Keystore** — the seed of the agent's outbound boundary.
- **Three separate pipelines (`IntentMatcher` / `GenerativeAiEngine` / `CommandPlanner`)** — the seed of
  tier-aware intelligence (rule → local model → cloud).
- **`ResolutionPreferenceStore` (S2-1) + planned `AliasStore` (S2-2)** — two concrete memory types to
  generalize.

## 1. The six layers

Each layer: **purpose · grows-from · domain shape · invariants · the surface it eventually enables**
(built only when the engine is real).

### A1 — Tool / Capability layer  *(the agent's hands; the safety boundary)*

- **Grows from:** `ActionCatalog` / `ActionDescriptor` / `ActionRiskLevel` / `LauncherAction` /
  `ActionExecutor` / `ExecuteActionUseCase`.
- **Domain (pure):** a `Tool` = `{ id, inputSchema, outputSchema, risk: ActionRiskLevel,
  preconditions: List<Precondition> (permissions/state), tier: ToolTier, effect: LOCAL | EXTERNAL,
  cost: CostClass }`; `ToolTier { IN_APP, SYSTEM_INTENT, ACCESSIBILITY }`; `ToolRegistry` port (evolves
  `ActionCatalog`); `ToolInvocation` / `ToolResult`; `ToolExecutor` port (generalizes `ActionExecutor`).
- **Invariants:** tools are the **only** world-effecting boundary; every tool carries its own
  risk/permission/tier; nothing acts without going through the registry; Tier 2 (accessibility) is opt-in
  and Stage-3-gated.
- **Enables:** Action Registry v2. Immediately enriches **S2-2** (`alias → tool invocation`), so A1 is the
  natural next architectural step.

### A2 — Context Engine v2  *(the agent's senses)*

- **Grows from:** the scattered suggestion providers (`TimeOfDay`/`Usage`/`Calendar`/`Location`),
  `DeviceProfileProvider`, `ConnectivityChecker`.
- **Domain (pure):** `ContextSnapshot` — immutable, **reduced** (`timeOfDay`, `placeClass`,
  `activityClass`, `network`, `power`, `recentActions`, `prayerWindow`, …) — **never raw titles/coords**;
  `ContextProvider` ports (fault-isolated, permission-gated, degrade independently); `ContextEngine`
  aggregator. `OutboundContextPolicy` **extended**: an explicit allow-list of which snapshot fields may
  enter a cloud plan request; everything else stays on-device.
- **Invariants:** no raw sensitive data anywhere (persistence, logs, `AiRequest`); a denied permission
  degrades only its own signal; outbound-bounded.
- **Enables:** relevant planning + suggestions; the home "current context" slot.

### A3 — User Memory  *(the agent's continuity)*

- **Grows from:** `ResolutionPreferenceStore` (S2-1, implicit/learned) + `AliasStore` (S2-2, explicit) —
  the two real examples to generalize from.
- **Domain (pure):** `MemoryItem` sealed = `Preference | Fact | Alias | Dismissed | Policy`, each with
  provenance, retention, and a `localOnly` flag; `UserMemoryStore` port; `MemoryPolicy` (retention limits,
  what may feed the planner, what may **never** leave the device). Becomes its own `feature/memory` module
  when it grows past a Settings sub-page.
- **Invariants:** every item viewable / editable / deletable / local; memory **never auto-enters cloud**;
  kept separate from transient usage history.
- **Enables:** the Memory surface; planner personalization.

### A4 — Agent Runtime  *(the missing core)*

- **Grows from:** `CommandPlanner` (single-shot) + `RouteCommandUseCase` (rule-first composition) +
  `ExecuteActionUseCase`.
- **Domain (pure):**
  - **`Planner`** port: `plan(goal, ContextSnapshot, ToolRegistry, UserMemory) → ExecutionPlan | Clarify |
    NoPlan`. **Rule-first here too:** a deterministic `TemplatePlanner` → on-device model → `LlmPlanner`
    (cloud), latest-capable-wins. `ExecutionPlan` = ordered/DAG of `PlanStep(tool, args, riskGate)`.
  - **`AgentExecutor`:** a **bounded** loop (`maxSteps` / `maxTime` / `maxCost`); per step —
    check risk/permission gate → **pause for consent if the gate trips** → invoke tool → observe
    `ToolResult` → continue | re-plan | stop. Fail-closed, cancellable.
  - **`AgentSession` / `ExecutionTrace`:** a persisted, inspectable state machine —
    `Planning · AwaitingConsent · Running · Paused · Completed · PartiallyCompleted · Failed · Cancelled ·
    Blocked`.
  - **`ConsentGate`:** any step crossing `SAFE→CONFIRM/DANGEROUS` or a permission boundary pauses the loop.
- **Invariants:** rule-first before any LLM; bounded + fail-closed; **the user owns the loop** (cancel /
  pause / edit-step); risky steps never auto-run; every step is traced.
- **Enables:** Execution Plan / Execution Stream / Result surfaces — driven **1:1 by real runtime state,
  never faked**.

### A5 — Activity / Trace surface  *(honest history)*

- **Grows from:** A4's `ExecutionTrace` + the existing `IntentMatchHistoryRepository`.
- **Domain (pure):** `ActivityRecord` projected from traces, with redaction (no raw sensitive command by
  default). **Ephemeral by default; persistence is opt-in with retention caps** (a full always-on journal
  is a honeypot — consistent with the existing "usage history off by default" stance).
- **Invariants:** reflects real events only (no fake agent history); sensitive content redacted; user can
  clear.
- **Enables:** the Activity surface (its own module when it grows past a diagnostic list).

### A6 — Capability grants + automation  *(Stage 3)*

- **Grows from:** A1 tiers + A4 consent + the permission-education module.
- **Domain (pure):** `Grant` = `{ agent/automation → allowed tools + limits + quiet-hours }`;
  `AutomationPolicy`; `AuditLog`. The **accessibility tier (Tier 2)** is opt-in only, heavily gated, and
  never required for normal use (absorbs the former "Phase 8 / accessibility" track).
- **Invariants:** behind feature flags + gates; accessibility opt-in only; fully disable-able; every
  consequential action audit-visible and revocable.
- **Enables:** safe automation; the Agents / Automation surfaces.

### A-drawer — AI app categorization  *(a capability spanning A2/A3, shipping-adjacent)*

The App Drawer groups apps into **thematic categories via an AI model**, with **user-creatable/editable
categories** — not the static coarse Play class. Same rule-first / local-first / privacy discipline:

- **Rule-first base:** `ApplicationInfo.category` (offline, deterministic; `UNDEFINED` → "Other").
- **AI refinement:** an **on-device** classifier (small model / embeddings over app name + package +
  base category) produces richer thematic groups (Banking, Ride-hailing, Health…) and may **propose new
  categories** when a cluster doesn't fit an existing one.
- **Privacy (important):** the **installed-app list is sensitive** (it profiles the user). Categorization is
  therefore **local-first**; any cloud classification is **opt-in and disclosed** and goes through the
  `OutboundContextPolicy` allow-list — the app list never leaves the device silently.
- **User Memory (A3):** categorization results + user overrides (move an app, rename/create/merge a
  category) are stored as editable local memory; the AI suggests, the user disposes.
- **Presentation:** the drawer is presentation-only (icon grids by category, spec §5.1); it renders what the
  categorization layer produces and holds no grouping logic.

## 2. Cross-cutting invariants (every layer honors these)

1. **Clean architecture** — pure domain, ports in `domain`, impls in `data/*`; no `feature→feature`; single
   `NavHost`; ops return `OperationResult`, never throw to UI.
2. **Understanding vs. execution** *(rewritten 2026-08-19, ADR 1/4 — replaces "rule-first /
   deterministic-first: templates and local matching run before any model")* — **understanding belongs
   to the model, execution to the deterministic layer**. FastPath and the learned-plan cache are a
   latency optimization, not a filter: a FastPath miss reaches the planner instead of answering
   "Unknown command". What stays absolute is the execution path — nothing proposed executes, gains
   rights or leaves the device except through `ToolRegistry` → argument validation → preconditions →
   risk gate / consent → loop bounds → egress allow-list → trace. The offline launcher core is still
   never blocked by the agent stack.
3. **Fail-closed** — any failure (no config, offline, bad plan, tool error, unparsable model output)
   degrades to the safe / rule-only path.
4. **Privacy is the egress boundary** — the `OutboundContextPolicy` allow-list is the *only* way anything
   leaves the device; raw context/memory stay local; BYOK key never logged/stored in plaintext.
5. **Consent & human authority** — risky actions never auto-run; the user owns and can stop the loop; the
   AI proposes, the user disposes.
6. **Provenance** — every consequential action carries `source · why · tier · local/cloud · model`,
   surfaced in the UI (the design expression of "system truth").

## 3. Design under agency (design rules specific to the agentic surfaces)

1. **Execution Stream is 1:1 with runtime** — no faked multi-step; a step that genuinely waits shows honest
   waiting, not animation.
2. **Consent is a repeated inline pattern**, not a one-time card — one consistent "gate" shape every time
   (Adl: identical risk → identical UI, whoever proposed it).
3. **Provenance everywhere** — in an agentic OS trust *is* the product.
4. **Aftermath / undo surface** — what happened, what changed, what's reversible, what went to cloud.
5. **Calm home ↔ focused agent via hand-off** — the calm Home hands control to a focused execution surface
   and returns; agent drama never leaks into Home.
6. **The more ordinary the action, the less UI it generates** — a plain app launch produces no card, no
   disclosure, no toast.

## 4. What makes it *original* (positioning, enforced by architecture)

1. **Local-first agency** — deterministic tasks run offline; cloud only when needed and always disclosed.
2. **Deterministic execution with a learned local memory** *(restated 2026-08-19, ADR 1/4 + 2/4)* — the
   model understands; FastPath and the **learned-plan cache** make already-understood goals replay
   deterministically, offline, on any hardware. Locality is bought by the cache, not by a model in the
   launcher process.
3. **Consent-woven loop** — the human owns the loop, not the model.
4. **Provenance-as-trust** — you always see source / why / local-cloud / tool / model.
5. **An ethical spine as product constraints** — Amanah (data as a trust), Adl (consistent risk), Haya (no
   dark patterns), Sukun (calm), Ilm (explainability). As *constraints* (not decoration) these genuinely
   differentiate an agent in a market of opaque, manipulative AI.

## 5. Build order & the golden rule

**Superseded 2026-08-19.** The original order was `A1 tools → A2 context → A3 memory → A4 runtime →
A5 activity → A6 grants/automation` — layer by layer, bottom-up. It produced zero agentic blocks in
5.5 weeks while the design track produced six, and it has the failure mode `:data:ai-local` already
demonstrated: a layer built in full against a consumer that never arrives. The order is now
**vertical-slice first**:

```
Этап 4  A0 thin agentic spike — ONE real 2-step goal through
        goal → plan → gate → tool → observe → tool → result → trace, device-accepted.
        Minimal pieces of A1/A2/A4; no A3/A5/A6, no DAG, no re-planning.
Этап 5  A1′ federated ToolRegistry (InApp · SystemIntent · AppFunctions · MCP · Accessibility)
Этап 6  A4′ runtime in full — session persistence across process death, rollback/compensation,
        the clarification protocol, the learned-plan cache
Этап 7  A2 / A3 built by consumer (contracts whole, implementation only what the first planner
        reads — the AIL-1 pattern), then A5 / A6
```

**Golden rule stands, unchanged: a surface's UI is built only once its engine is real.** The five
existing `PREVIEW` tabs stay (owner decision, 2026-08-18) — the badge *is* the measure the audit
prescribed — but they are excluded by policy from cross-cutting work (i18n, a11y, goldens, themes), and
each carries an expiry: `tasks`/`agents` → real in A4, `activity` → A5.

**The boundaries do not wait for a later slice.** Consent, argument validation, loop bounds, trace,
egress allow-list and rollback are laid down on the **first** slice, for two tools — not "when needed".
If the runtime first learns to call 20 tools and consent is added afterwards, that is 20 places to
forget it; if consent lives inside `ToolExecutor` from day one, tool #21 gets it for free. Same logic by
which this project's privacy guard-tests were written before there was anything to leak.

Current state (2026-08-19): Stage 1 closed; the design track closed (DS-1…DS-11 + I18N-1/2); the four
strategic ADRs of Этап 1 accepted → **Этап 0 (cleanup) and Этап 2 (toolchain + KMP) next**, then the A0
spike.

## 6. Non-goals / explicitly out of scope

- Building agent/execution/activity UI ahead of its engine.
- Adopting the 5-tab nav (Home/Agents/Memory/Activity/System) or a live "Agents" surface now — deferred
  until A4/A6 are real (see the design audit / DS decisions).
- Autonomy without consent; any action that bypasses the risk/permission gates.
- Replacing the existing rule/router/executor path — A1–A6 *compose over* it; router-off / offline parity
  is preserved throughout.
