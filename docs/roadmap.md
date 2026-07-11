# Roadmap

> **Reframed 2026-07-05 (owner decision).** The project has one product goal delivered in **three
> evolutionary stages**, each shippable on its own:
>
> 1. **Stage 1 — AI Launcher (the MVP).** A real AI-first launcher: one universal input that
>    *understands* natural language and *routes* it to safe actions (open app, web, site, Play Store,
>    settings, assistant), with a fast offline rule path underneath. **This is what we ship first and
>    are refining now.**
> 2. **Stage 2 — AI Framework.** Generalize the launcher's routing/action/context/memory machinery into
>    a reusable, testable on-device AI framework (Action Registry, Context Engine, User Memory) that any
>    surface can consume.
> 3. **Stage 3 — Agentic OS.** An AI operating layer over Android: multi-step planning, user-consented
>    safe automation, and one coherent intent-driven shell.
>
> **The AI-first launcher only becomes real in Stage 1's completion track (below).** Today's shipped
> routing is rule-based (7 launch/search verbs + a command table); the local NLU/ONNX pipeline is built
> but **inert** (no model — OQ#1/#2). Stage 1 makes the launcher genuinely "AI" via a **BYOK cloud LLM
> router** that understands intent and proposes registered actions, gated by confidence, permissions,
> and explicit confirmation for anything risky. See
> [ai-context/ai-launcher-mvp-plan.md](../ai-context/ai-launcher-mvp-plan.md).

---

## Guiding principles (invariant across all three stages)

- Launcher core stays fully usable without AI, network, microphone, or optional permissions.
- **Local deterministic routing runs before any cloud/LLM call.** The LLM is consulted only on low
  rule-confidence or natural-language input; its failure/offline degrades to the rule outcome.
- Every AI-proposed action is **confidence-gated, permission-gated, and confirmation-gated** for risk.
  The AI never silently executes a risky or destructive action.
- No `feature → feature` dependency edges; single `NavHost` in `:app`; ViewModels emit
  `NavigationEvent`.
- `domain` stays Android-free (stdlib + coroutines). Interfaces in `domain`, impls in `data/*`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- Private context (raw calendar titles, coordinates, messages, clipboard) is **never** sent to cloud AI
  by default. Outbound content stays on a fail-closed allow-list.
- Every block ends with tests + `assembleDebug`, an ADR entry, and device acceptance where applicable.

---

# Stage 1 — AI Launcher (MVP)

## 1A. Foundation — DONE (Phases 0 → 9 + Phase UX)

The launcher shell, offline core, persistence, security, and UX are built and device-accepted on
SM-A325F / Android 13. Compressed history (full detail in `CLAUDE.md` and the per-phase plans):

- **Phase 0–2 — skeleton + shell.** Multi-module Clean Architecture; single `NavHost`; installed-apps
  repository; text command input; offline home/grid/launch.
- **Phase 3 — intent system.** Rule-based `IntentMatcher`, `ActionExecutor`, confidence gating, the MVP
  command loop. Live on device.
- **Phase 4 — persistence/state/hardening.** DataStore preferences/flags, Room usage/ranking/intent
  history (with SEARCH/UNKNOWN redaction), permission-education module, recoverable `UiState.Error`.
- **Phase 5 — cloud AI (BYOK).** OpenAI-compatible SSE engine (`Flow<AiChunk>`), `SecureSecretStore`
  (Keystore AES-256-GCM), prompt/outbound privacy guards, `DefaultGenerativeRouter` + static fallback,
  assistant streaming UI. **Device-proven** (openrouter / `gpt-4o-mini` streamed end-to-end; key stayed
  encrypted).
- **Phase 6 — local NLU (code only, INERT).** `OnnxIntentClassifier` (self-gating), rule-first
  `LayeredIntentMatcher`, model provisioning (`ModelStore`/SHA-256/WorkManager). **No model bundled →
  always escapes to rule-only.** Gated on OQ#1/#2 — deferred to the model track, not an MVP blocker.
- **Phase 7 — voice + contextual suggestions.** `AndroidSpeechInputSource` + `RECORD_AUDIO` flow,
  offline + opt-in suggestion providers, single-owner `LauncherUiState.suggestions`, periodic
  precompute/cleanup. Block V (semantic re-rank) inert — OQ#3.
- **Phase UX — home redesign + design system.** Minimal home + App Drawer, discoverable
  Settings/Assistant icons, `core/ui` design system, real `:feature:settings`. Device-accepted.
- **Phase 9 — hardening.** Startup perf (warm ~102ms, cold ~766ms, no first-frame spinner), R8 +
  Baseline Profile, suggestion correctness, test/privacy/logging hardening, Android-13 + LOW_END-path
  validation.

## 1B. AI-Launcher completion track — ACTIVE (NOW)

**This is the work that makes the launcher an *AI* launcher.** Detailed plan + forks:
[ai-context/ai-launcher-mvp-plan.md](../ai-context/ai-launcher-mvp-plan.md). Blocks are vertical
slices; each ends green (`testDebugUnitTest` + `assembleDebug`) and preserves every guiding principle.

- **AIL-0 — Design tokens & visual identity (`core/ui`).** Refine the design system up front (palette +
  visual identity, typography, spacing/radius/elevation, base-component tweaks) so the new surfaces build
  on final tokens. Presentation-only; the input/results/confirmation *screen* redesign is deferred into
  AIL-3 / AIL-5 / AIL-6 where those surfaces are created.
- **AIL-1 — Action Registry (domain).** Introduce an extensible `LauncherAction` / `ActionDescriptor` /
  `ActionRiskLevel` / `ActionId` registry (`domain`), registering existing + new capabilities behind
  one testable, permission-aware vocabulary. The registry is the set of targets the router routes into.
- **AIL-2 — Web / URL / Play-Store routing (no AI).** Detect URL-like input → `ACTION_VIEW` after safe
  normalization; "download/install X" → Play Store (`market://`); configurable web-search provider
  (retire the hardcoded Google `TODO`). Fully offline, high-value, populates the registry.
- **AIL-3 — Universal Input.** One home field = app filter + command + web/site + assistant entry +
  voice. Additive `UniversalInputRouter` + sealed `InputIntent`. Typed commands keep working
  byte-for-byte; voice transcripts reuse the same routing path.
- **AIL-4 — LLM Action Router (the AI core, BYOK cloud).** A **new third port** (`CommandPlanner`) —
  distinct from `IntentMatcher` (classification) and the assistant's `GenerateReplyUseCase`
  (conversation). Consulted only on low rule-confidence or natural-language input: it sends the user
  command + the Action Registry's tool schema to the BYOK cloud engine, parses a **structured** action
  proposal (never free text), and returns it as a `Suggest`/`NeedsConfirmation` outcome. Offline/failure
  → rule outcome (identical to today). Privacy: schema + command only, no private context.
- **AIL-5 — Confirmation & safety gating.** Wire `ActionRiskLevel` into execution: risky/ambiguous
  actions require explicit confirmation UI; permission-gated; safe fallback. This is what "route to safe
  actions with confirmation" means concretely.
- **AIL-6 — Polish + device acceptance.** SM-A325F pass: universal input, LLM routing with a real BYOK
  provider, web/URL/Play-Store, confirmation flows; **offline parity** (LLM off → identical to
  rule-only). Docs + ADR sync.

**Stage 1 Definition of Done:** the user can type or speak a natural request into one field and the
launcher routes it correctly to app / web / site / Play Store / settings / assistant; risky actions ask
first; typed commands are unchanged; offline behavior is identical to rule-only; device-accepted.

## 1C. Separate model track (out of the MVP ship gate)

Not blockers for Stage 1. Resolve or explicitly de-scope; stop carrying inert code as "done".

- **OQ#1 / OQ#2** — real NLU model (`intent.onnx`, pruned multilingual `vocab.txt`) + host + SHA-256.
- **OQ#3** — embedding model + tokenizer + host/hash (gates Block V semantic re-rank).
- **OQ#4** — on-device STT availability across the target device matrix (gates voice acceptance).
- Android 9/11/14 + real LOW_END hardware validation; boot-warmup after reboot.

---

# Stage 2 — AI Framework

> **Engineering target for Stage 2 → Stage 3:** the six-layer agentic architecture **A1–A6** in
> [agentic-os-architecture.md](agentic-os-architecture.md) (ADR 2026-07-10). A1–A3 flesh out this Framework
> stage (tools · context · memory); A4–A5 bridge into Stage 3; A6 is Stage 3. Visual identity is governed by
> [superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md](superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md).
>
> **Design system track.** The seven imported source docs are archived in [design/](design/) (v1 = Vision,
> v1.1 = Governing; index in its `README.md`); the reconciliation of record (three v1-vs-v1.1 conflicts + nine
> engineering deviations + DS sequence) is ADR "2026-07-11 — DS-0" in
> [ai-context/decisions.md](../ai-context/decisions.md). Target **IA = 4 surfaces** (Home · App Drawer ·
> Assistant · Settings) — the imported 5-tab nav / live Agents surface is deferred until the A4 runtime
> exists. DS blocks: DS-0 (provenance) → **DS-1 tokens + Roborazzi harness (DONE)** → DS-2/3 primitives →
> DS-4 Home → DS-6A sacred header → DS-6B prayer data → DS-7 memory-migration.

**Status: FUTURE (starts after Stage 1 ships).** Generalize the launcher's routing machinery into a
reusable on-device AI framework. Every layer must keep the Stage-1 principles (offline core, local
routing before LLM, confidence/permission/confirmation gating, no feature→feature edges, domain purity,
privacy allow-list).

## Framework-1: Action Registry & Web/App Intent Router (generalized)

Promote AIL-1/AIL-2 into a full, reusable action layer any surface can drive.

- Core concepts: `ActionRegistry`, `ActionDescriptor`, `ActionRiskLevel`, `ActionCapability`,
  `ActionPrecondition`, `PermissionGate`, `ExecutionPlan`, `ExecutionResult`.
- Families: app launch/search, web search, direct site open, Play Store search, Android system intents,
  assistant routing, settings routing, safe deep links.
- Rules: URL-like input opens via `ACTION_VIEW` after normalization; ambiguous site → browser search;
  install requests → Play Store; direct site open only on high confidence; malformed URLs never opened
  silently.
- Acceptance: all executable actions registered and testable; risky actions require
  confirmation/education; web/app routing without embedding a browser; no destructive/system action runs
  silently; execution stays compatible with `OperationResult`.

## Framework-2: Context Engine v2

A privacy-preserving context layer producing structured, minimal, permission-aware snapshots for
routing, suggestions, and assistant handoff.

- Possible sources: time, usage, recent launcher actions, typed prefix, voice availability, network,
  battery/thermal/`DeviceProfile`, optional calendar/location/notification signals.
- Privacy: raw titles/coordinates/messages never stored or sent to cloud by default; reduce to safe
  signals; permission denial degrades only the related feature; collection is explainable + controllable.
- Acceptance: a `ContextSnapshot` domain model exists; consumers don't read Android APIs directly;
  suggestions/assistant/universal-input share one safe context abstraction; privacy-guard tests prove no
  sensitive raw data leaks to persistence, logs, or AI requests.

## Framework-3: User Memory & Personalization

Explicit, user-controlled memory and personalization — learn stable preferences without becoming opaque
or cloud-dependent.

- Categories: launcher prefs, favorite apps, preferred actions, preferred browser/search, assistant
  provider prefs, language, safe personalization hints, dismissed suggestions, confirmed aliases
  ("work chat" → a specific app/action).
- Hard rules: memory is editable/deletable; sensitive memory requires explicit user action; no private
  memory to cloud by default; personalization degrades gracefully when disabled; memory separated from
  transient usage history.
- Acceptance: user can view/edit/delete stored preferences; router + suggestions consume memory through
  domain ports; tests cover persistence, deletion, privacy guards, fallback; no hidden long-term
  profiling.

---

# Stage 3 — Agentic OS

**Status: FUTURE / POST-FRAMEWORK.** An AI operating layer over Android — not a replacement for Android
internals. Starts only after Stages 1–2 are shipped and stable.

## Agentic-1: Safe Automation Layer

Optional, user-consented automation. Must never be required for MVP or block core launcher behavior.

- Scope: simple confirmed actions, repeatable user-approved workflows, officially-supported Android
  intents, optional Accessibility Service **only after explicit education + consent**, audit-friendly
  execution history, a clear disable path.
- Safety model: every action has a risk level; risky actions require confirmation; accessibility is
  opt-in only; denial disables only automation; the user always understands what will happen first.
- Acceptance: automation behind feature flags + permission gates; Accessibility not required for normal
  use; user can disable automation completely; action history visible/explainable; tests cover denied /
  revoked permission, failed execution, and safe fallback.
- *(Absorbs the former "Phase 8 — optional advanced automation" / accessibility track.)*

## Agentic-2: AI OS Shell

Combine the previous layers into one coherent user-facing system — minimal home, universal input,
assistant, contextual suggestions, action registry, safe automation, user memory, privacy controls,
device-capability routing, offline-first local behavior, cloud AI only when appropriate.

The user interacts primarily through intent:
`Say or type what you want → Sidr routes, executes, answers, or asks for clarification.`

- Acceptance: common phone tasks done through one unified input model; app/web/assistant/settings/
  suggestions/automation feel like one system; offline behavior stays useful; AI failures never break
  launcher functionality; permissions/privacy/safety stay visible and controllable; performance stays
  within budgets or features degrade by `DeviceProfile`.

---

## Sequencing summary

```
Stage 1 (MVP, NOW):  Foundation ✅  →  AIL-0 → AIL-1 → AIL-2 → AIL-3 → AIL-4 → AIL-5 → AIL-6  →  SHIP
Stage 2 (Framework): Framework-1 → Framework-2 → Framework-3
Stage 3 (Agentic):   Agentic-1 (safe automation / accessibility) → Agentic-2 (AI OS shell)
Model track (parallel, off the ship gate): OQ#1–#4, device matrix, boot warmup
```
