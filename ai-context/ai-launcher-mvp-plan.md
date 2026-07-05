# AI-Launcher MVP — Completion Track (Stage 1B)

> **Status: PLAN (active). Owner decisions 2026-07-05.** The launcher foundation (Phases 0–9 + Phase
> UX) is built and device-accepted, but today's routing is **rule-based only** — the local NLU/ONNX
> pipeline is inert (no model, OQ#1/#2), and the assistant is an isolated chat screen that cannot act.
> This track makes the launcher genuinely **AI-first**: one universal input that understands natural
> language and routes it to safe actions.
>
> **Owner decisions (2026-07-05):**
> - **AI core = BYOK cloud LLM routing.** The LLM understands intent and routes; the rule matcher stays
>   the fast offline fallback. Not blocked on model selection (unlike the ONNX track).
> - **Action rights = understand + route to safe actions.** The AI proposes/executes registered actions
>   (open app, web search, open site, Play Store, settings, assistant); **anything risky requires
>   explicit confirmation**; no autonomy, no multi-step chains yet (that is Stage 3).
>
> Reframed roadmap: [../docs/roadmap.md](../docs/roadmap.md). This plan owns Stage 1B (blocks AIL-0…6).

---

## 0. Block kickoff & closure protocol

Each AIL block runs from a fresh session. **This plan + `CLAUDE.md` + the block's ADR are the spec — do
not write a separate per-block prompt** (it would fork the source of truth and drift). Instead, open every
block with this kickoff, and close it with the checklist below.

**Kickoff (paste at the start of a block's session):**

```
Работаем над блоком AIL-<N> из ai-context/ai-launcher-mvp-plan.md.
Сначала прочитай CLAUDE.md (Current goal + Hard rules), §5 нужного блока и связанный ADR в decisions.md.
Соблюдай forks-before-code: если в блоке есть неразрешённый форк — спроси до кода.
Блок закрывается только зелёными testDebugUnitTest + assembleDebug, записью ADR и синхронизацией
CLAUDE.md/current-status.md.
Для AIL-4 обязателен guard-тест приватности и проверка router-off ⇒ rule-only паритет.
```

**Closure checklist (block is not done until all pass):**

- [ ] Scope matches this plan's §5 entry for the block; unresolved forks were surfaced (not silently
      decided).
- [ ] Hard rules intact: `domain` pure, no `feature→feature` edge, ops return `OperationResult`, launcher
      core still fully offline.
- [ ] Router-affecting blocks proven: **router-off / offline / no-key ⇒ byte-for-byte rule-only parity**
      (AIL-4/5); privacy guard green (no context beyond command + action schema leaves — AIL-4).
- [ ] `./gradlew --no-daemon testDebugUnitTest assembleDebug` green (add `:app:assembleRelease` only for
      AIL-6 / RC).
- [ ] New/changed behavior device-checked on SM-A325F where the block has a runtime surface (mandatory
      for AIL-6; opportunistic earlier).
- [ ] **ADR appended** to `ai-context/decisions.md` in the established format.
- [ ] **`CLAUDE.md` `Current goal` advanced to the next block**; `ai-context/current-status.md` synced;
      this plan's §5 entry marked ✅ with a one-paragraph result.

Blocks are sequential (each builds on the previous), so a new session only needs "which block is active" —
which `CLAUDE.md` `Current goal` always states. No sub-agent fan-out is needed for this track.

---

## 1. Why this track exists (the gap, concretely)

Grounded in the current code:

- **Routing intelligence = 7 verbs + a table.** `RuleBasedIntentMatcher` recognizes `open/launch/start`,
  `search/find/google`, a settings keyword, and 7 simple commands. Everything else → `UnknownIntent`.
  There is no natural-language understanding on the shipped path.
- **The LLM cannot act.** `GenerateReplyUseCase` sends only the user command + a static system prompt and
  streams text back to `AssistantScreen`. There is **no tool/function-calling path** anywhere — the
  assistant talks, it never routes or executes.
- **No web / URL / Play-Store routing.** `SearchIntent` fires only on a literal `search/find/google`
  prefix and hardcodes Google (`AndroidActionExecutor.openSearch` `TODO`). No URL detection, no
  "download X" → store, no direct site open.
- **Closed action taxonomy.** `ExecutableAction` (7 variants) + `IntentActionResolver`'s `when` + the
  executor's `when` must all be edited for every new capability — no registry, no risk model.

## 2. Goals / Non-goals

**Goals**
- One **universal input** that routes typed *and* spoken natural language to the safest correct target.
- A **BYOK cloud LLM router** that proposes **structured, registered actions** (not free text), used only
  when rules are low-confidence — with the offline rule path unchanged underneath.
- An extensible, testable, permission-aware **Action Registry** with per-action **risk levels**.
- **Web / URL / Play-Store routing** (no AI required) as first-class registered actions.
- **Confirmation gating**: risky/ambiguous actions ask before executing.

**UI approach (two parts).** Design tokens + visual identity land up front (**AIL-0**) because they are
cheap and compound; the *screen-level* redesign of the input, results, and confirmation surfaces is
deferred into the blocks that create those surfaces (**AIL-3 / AIL-5 / AIL-6**) so they are designed once
against real behavior rather than reworked.

**Non-goals (explicit)**
- No autonomy, no multi-step planning / tool chains, no automation (Stage 3).
- No accessibility automation (Stage 3).
- No local ONNX NLU model work (OQ#1/#2 — separate model track). The router is cloud BYOK.
- No inline AI answers rendered on Home — assistant questions route to the assistant screen with a
  prefilled prompt (existing X6-C mechanism).
- No new persistence of AI conversation, private context, or LLM prompts/replies.

## 3. Architecture — the new pieces

### 3.1 The third pipeline (`CommandPlanner`)

The project has two intentionally-separate pipelines: **`IntentMatcher`** (offline classification →
`IntentMatchResult`) and the assistant's **`GenerateReplyUseCase`** (conversation → `Flow<AiChunk>`).
This track adds a **third, distinct port** — a *structured router* that uses the LLM to map
natural language onto registered actions:

```
domain/ai/router/CommandPlanner        (port)
  suspend fun plan(command: String, registry: ActionCatalog): PlanResult

PlanResult =
  | RoutedAction(action: LauncherAction, confidence: Float, needsConfirmation: Boolean)
  | Clarify(question: String)
  | NoPlan            // planner declined / offline / failure → caller keeps the rule outcome
```

- **Not folded into `IntentMatcher`** (respects the hard rule "don't fold generative AI into
  `IntentMatcher`"). **Not the assistant's conversational path either.** It is structured
  routing-via-LLM. This bends the *spirit* of "matching ≠ generation" enough to require an explicit ADR
  (see §7) — but keeps "local matching runs before any LLM call": the planner is consulted **only** when
  the rule result is low-confidence or the input is natural language.
- **Impl (`data/ai-cloud` or `data/repository/ai`)** reuses the existing `OpenAiCompatibleGenerativeAiEngine`
  transport + `AiProviderConfigRepository` + Keystore key. It sends the user command **plus the Action
  Registry's tool schema** (action ids + human descriptions + argument shapes) and parses a **structured
  JSON tool call**, mapping it to a `LauncherAction`. Free-form model chatter → `NoPlan`.
- **Privacy:** outbound = user command + static action schema only. No calendar/location/usage/history/
  device context. Extends the existing `OutboundContextPolicy` allow-list; guard-tested.
- **Degradation:** offline / no key / provider error / unparseable → `NoPlan`; the caller falls back to
  the rule outcome. **With the router disabled, behavior is byte-for-byte the current rule-only launcher.**

### 3.2 Action Registry

```
domain/action/
  ActionId            value class
  ActionRiskLevel     enum { SAFE, CONFIRM, DANGEROUS }   // MVP uses SAFE + CONFIRM
  ActionDescriptor    (id, title, description, category, risk, argSchema, permissionGate?)
  LauncherAction      sealed — the concrete, ready-to-execute instances (wraps/extends ExecutableAction)
  ActionCatalog       port — the registered descriptors the router/UI can enumerate
```

- Registers existing capabilities (launch app, web search, open settings, open assistant, show apps) +
  the new ones (open URL, Play Store search).
- `ExecutableAction` stays the execution vocabulary; `LauncherAction`/`ActionDescriptor` add the
  **catalog + risk + schema** layer above it. Non-breaking: the existing rule→resolver→executor path is
  untouched; the registry is additive and is what the router emits into.
- Risk drives confirmation (§AIL-5). MVP: SAFE = execute directly; CONFIRM = ask first (e.g. open an
  arbitrary URL, Play Store, any LLM-proposed action). DANGEROUS reserved for Stage 3.

### 3.3 Universal Input

Additive `UniversalInputRouter` over one home field, producing a sealed `InputIntent` and dispatching:
app filter (live) · existing command pipeline (byte-for-byte) · web/site · Play Store · assistant
(prefilled) · voice (same path as typed). `CommandNormalizer` / `IntentMatcher` /
`HandleUserCommandUseCase` behavior unchanged.

## 4. Forks (decide before coding)

| # | Fork | Options | Recommendation |
|---|---|---|---|
| **R1** | Router trigger | (a) always call LLM, (b) **only on low rule-confidence / NL input** | **(b)** — keeps offline-first + cost/latency down; rule path still wins fast |
| **R2** | Router placement | (a) inside `HandleUserCommandUseCase`, (b) **a new `RouteCommandUseCase` that wraps rule-first then planner** | **(b)** — leaves the proven `HandleUserCommandUseCase` untouched; planner is composed above it |
| **R3** | LLM output contract | (a) parse free text, (b) **structured tool/JSON call, strict-parse, else NoPlan** | **(b)** — never execute on hallucinated free text; fail-closed |
| **R4** | Router auto-exec | (a) may auto-execute SAFE, (b) **always Suggest/Confirm for LLM-proposed actions** | **(b)** for MVP — LLM proposals never silently execute; matches the owner's "with confirmation" decision. (Revisit per-risk in Stage 2.) |
| **R5** | Web-search provider | (a) keep hardcoded Google, (b) **user-configurable, default Google** | **(b)** — retire the `TODO`; a pref key (denylist-clean) |
| **R6** | Direct site open | (a) always open typed domains, (b) **open only high-confidence URL-like input; ambiguous → browser search** | **(b)** — never open a guessed/malformed URL silently |
| **R7** | Registry scope (MVP) | (a) full Phase-11 registry now, (b) **minimal catalog covering the 6 shipped families** | **(b)** — MVP-sized; generalized in Stage 2 Framework-1 |
| **R8** | Voice routing | (a) voice stays home-only submit, (b) **voice transcript flows through UniversalInputRouter like typed** | **(b)** — one routing path for both modalities |

## 4A. Design intent — visual direction (AIL-0)

**Direction (owner, 2026-07-05).** *"Modern ultra-cyberpunk in the spirit of early computers."* The
launcher reads as an advanced **military AI terminal booted on a quantum machine**: retro-CRT phosphor
(monospace, near-black ground, luminous single accent, thin grid borders with glowing corner ticks) fused
with clean hi-tech restraint. One hero color does all the work. This deliberately replaces the Phase-UX
neutral-indigo Material-You look; the owner pinned the direction, so it is followed exactly rather than
softened toward a default.

**Resolved design forks (owner decisions, artifact-reviewed):**

| # | Fork | Decision |
|---|---|---|
| **D1** | Accent hue | **Green `#00FF66` = default brand;** amber `#FFB000` shipped as an alternative accent. Both defined; one hero accent at a time. |
| **D2** | Theme modes | **Dark-first identity** (near-black `#08090A`); a **restrained light "blueprint / paper terminal"** scheme also defined (muted ground, darkened accent, glow dropped). **Dynamic color (Material You) default OFF** — a bespoke brand and wallpaper-derived color are mutually exclusive. |
| **D3** | Typography | **Full monospace — JetBrains Mono bundled** (OFL). Launcher text is short (labels/commands), so the readability cost is low and the identity payoff high. |
| **D4** | Aesthetic intensity | **Restrained static tokens now;** CRT motion/overlays deferred (D4 → DF-5). Protects the LOW_END path and avoids reworking components when the real screens land. |
| **D5** | Accent switcher rollout | **Variant A:** define both accent schemes in `core/ui` now (default green); the live green/amber switch in Settings is a later half-step (DF-7). AIL-0 stays presentation-only. |

**In AIL-0 (this block, `core/ui` — presentation only):**
- **Palette:** 4 `ColorScheme`s (green/amber × dark/light) mapped onto semantic M3 roles; raw hex private to
  `Color.kt`, consumers read `MaterialTheme.colorScheme`.
- **Theme wiring:** `AccentColor { GREEN, AMBER }` enum + `SidrTheme(accent = GREEN, dynamicColor = false)`.
- **Typography:** JetBrains Mono `FontFamily` (Regular/Medium/SemiBold/Bold) across the existing type roles.
- **Shape:** brutalist sharpening — radius `0 / 2 / 4 / 8`dp (down from 4–28dp); elevation expressed as
  1px grid borders (soft M3 shadow / animated glow deferred).
- **Spacing/size tokens:** kept (4→32 rhythm already sound).
- **Base components:** token adoption + previews refreshed to the terminal identity (no structural/behavior
  change).
- **One `:app` touch:** window background set to the brand near-black so there is no white boot flash
  (cosmetic, no behavior change).

**Deferred design forks (NOT AIL-0 — designed against real behavior in the surface blocks):**

| # | Deferred fork | Lands in |
|---|---|---|
| **DF-1** | Home surface layout (wordmark header, command-field placement, section order/content) | AIL-3 |
| **DF-2** | Universal-input field visuals (`>` prompt, cursor, placeholder, mic states, focus treatment) | AIL-3 |
| **DF-3** | Results / suggestions presentation (bracketed `[ ]` terminal chips vs list vs cards) | AIL-3 |
| **DF-4** | Confirmation card (`EXECUTE?` / CONFIRM / CANCEL, risk-tag styling) | AIL-5 |
| **DF-5** | Motion & CRT FX (scanline overlay, screen flicker, per-character CRT typing, hover-invert, animated glow, boot sequence) — **LOW_END-gated** | AIL-6 |
| **DF-6** | Brand assets (adaptive launcher icon art, wordmark logo, splash) | AIL-6 / identity pass |
| **DF-7** | Accent switcher UI in Settings + persistence (tokens ready now — D5 Variant A) | AIL-3/6 |
| **DF-8** | Theme packs / custom wallpapers / widgets (architecture kept open by semantic tokens + parameterized theme) | Stage 2/3 |

## 5. Block breakdown (vertical slices; each ends green + ADR)

- **AIL-0 — Design tokens & visual identity (`core/ui`, no behavior change).** Refine the Phase-UX design
  system *before* the new surfaces land, so AIL-3/5/6 build on final tokens instead of reworking them:
  palette + visual identity (brand color, app name/logo, icon), typography scale, spacing/radius/elevation
  tokens, and any base-component tweaks. Presentation-only — existing screens keep working; no
  `feature→feature` / `domain→ui` edges; component previews updated. **Deliberately excludes** the
  input/results/confirmation *screen* redesign — those surfaces don't exist yet and are designed inside
  AIL-3 (universal input), AIL-5 (confirmation), and AIL-6 (final polish/motion). *(Cheap, compounds,
  nothing to redo. Do first.)*
  **✅ DONE (2026-07-05).** Replaced the Phase-UX neutral-indigo Material-You look with the
  "ultra-cyberpunk / early-computer terminal" identity (§4A). `core/ui` now ships **4 `ColorScheme`s**
  (green/amber × dark/light) with dynamic colour **off by default**; `AccentColor { GREEN, AMBER }` +
  `SidrTheme(accent = GREEN, dynamicColor = false)`; **JetBrains Mono** bundled (Regular/Medium/SemiBold/
  Bold, OFL) as the full-mono `SidrTypography`; **brutalist `SidrShapes`** (0/2/4/8dp); spacing/size tokens
  kept. Component previews refreshed to the dark terminal identity (+ an amber `AppTile` proof); one
  cosmetic `:app` touch — window background set to the brand ground (`#08090A`) to kill the white boot
  flash. Presentation-only: no component API/behaviour change, no `feature→feature`/`domain→ui` edge,
  `core/ui` still depends only on `core/common`. Forks D1–D5 resolved; DF-1…DF-8 deferred to the surface
  blocks. `./gradlew --no-daemon testDebugUnitTest assembleDebug` green. Device visual acceptance is
  opportunistic (mandatory only at AIL-6). ADR: decisions.md "ADR 2026-07-05 — AIL-0 complete".
- **AIL-1 — Action Registry (domain).** `ActionId`/`ActionRiskLevel`/`ActionDescriptor`/`LauncherAction`/
  `ActionCatalog`; register the 6 existing families. Pure `domain` + fakes in `core/testing`. No behavior
  change. *(Everything routes into this.)*
  **✅ DONE (2026-07-05).** Shipped the additive catalog+risk+schema layer in `domain/action/` (pure Kotlin,
  stdlib-only, vendor-neutral) **above** the untouched `ExecutableAction` → resolver → executor path:
  `ActionId` (`@JvmInline value class`) + `ActionIds` (canonical wire ids for all seven families —
  `launch_app`/`web_search`/`open_settings`/`open_assistant`/`show_apps` + AIL-2's `open_url`/
  `play_store_search`), `LauncherAction` (sealed; each variant carries **unresolved** semantic args + its
  `id`), `ActionDescriptor(id,title,description,category,risk,argSchema,permissionGate?)`,
  `ActionRiskLevel {SAFE,CONFIRM,DANGEROUS}`, `ActionCategory`, `ArgType {STRING}`/`ActionArg`, and the
  `ActionCatalog` port; `FakeActionCatalog` in `core/testing`. **Forks decided before code (R7 scope +
  three shape forks):** argSchema = `List<ActionArg>` string-only; `LauncherAction` is a **parallel**
  hierarchy (not a wrapper around `ExecutableAction`) so resolution/execution mapping defers to AIL-2/4/5;
  the **concrete descriptor catalog + `ActionCatalog` impl + `:app` binding are deferred to AIL-2** (so
  AIL-1 registers the families as *vocabulary* — `ActionIds` + `LauncherAction` — and AIL-2 registers the
  descriptor metadata alongside its executors). No behaviour change (nothing consumes the registry yet);
  `IntentMatcher`/`HandleUserCommandUseCase`/`GenerateReplyUseCase`/`ExecutableAction`/screens untouched;
  launcher still fully offline. 9 new domain tests; `./gradlew --no-daemon :domain:test testDebugUnitTest
  assembleDebug` green. ADR: decisions.md "ADR 2026-07-05 — AIL-1 complete".
- **AIL-2 — Web / URL / Play-Store routing (no AI).** New `OpenUrlAction` + `PlayStoreSearchAction` in the
  registry + `AndroidActionExecutor`; URL detection + safe normalization (R6); "download/install X" →
  `market://`; configurable search provider (R5). Rule matcher gains URL/install recognition. Fully
  offline, high-value.
  **✅ DONE (2026-07-05).** Shipped the concrete `DefaultActionCatalog` (all 7 descriptors; open_url /
  play_store_search = CONFIRM, rest SAFE; bound via `ActionBindsModule`) + offline URL/Play-Store routing
  through the untouched rule→resolver→executor path. New pure `domain/intent/UrlDetector`
  (`Url`/`SearchFallback`/`None`) enforces AIL-Q3: **scheme allow-list http/https only** (no silent
  `intent://`; user-typed `market://` never honored), **curated-TLD** bare-domain open (Q1), **punycode/IDN
  homograph guard** → search, **query-string → web search** (case-safe, Q4). `LauncherIntent` +=
  `OpenUrlIntent`/`PlayStoreSearchIntent`; `ExecutableAction` += `OpenUrlAction`/`PlayStoreSearchAction`;
  `RuleBasedIntentMatcher` gained launch-verb URL divert (Q2 — `open github.com`→site, `open telegram`→app),
  `install <app>`→Play Store (Q3, install-only), and bare-URL/ambiguous recognition (R6). Executor opens via
  `ACTION_VIEW` + `market://…` with web fallback; **R5** configurable provider read from
  `UserPreferences.webProviderTemplate` (default Google, denylist-clean key `web_provider_template`).
  URL/store queries redacted in history via existing Fork-3 SEARCH mapping (no new redaction surface).
  Confirmation of CONFIRM-risk actions is metadata for AIL-5 (user-typed executes directly in AIL-2, like
  search). Hard rules intact; `HandleUserCommandUseCase`/`IntentMatcher`/`GenerateReplyUseCase` contracts
  unchanged; launcher fully offline. New tests: UrlDetector 25, matcher 25→35, resolver +2, catalog 7,
  use-case +2; `PrivacyInventoryGuardTest` green. `./gradlew --no-daemon testDebugUnitTest assembleDebug`
  green. Device acceptance opportunistic (mandatory at AIL-6). ADR: decisions.md "ADR 2026-07-05 — AIL-2
  complete".
- **AIL-3 — Universal Input.** `UniversalInputRouter` + sealed `InputIntent`; unify the home field
  (app-filter + command + web/site + assistant + voice). Typed commands byte-for-byte; voice reuses the
  path (R8). `HandleUserCommandUseCase` untouched.
- **AIL-4 — LLM Action Router (BYOK cloud).** `CommandPlanner` port + cloud impl over the existing engine;
  `RouteCommandUseCase` (rule-first → planner on low confidence, R1/R2); strict structured parsing (R3);
  privacy allow-list extension + guard test; `NoPlan` → rule fallback. Feature-flag + settings toggle;
  off → exact rule-only parity.
- **AIL-5 — Confirmation & safety gating.** `ActionRiskLevel` → confirmation UI in `feature/launcher`;
  LLM-proposed actions always confirm (R4); permission-gated via existing education flow; safe fallback.
- **AIL-6 — Polish + device acceptance.** SM-A325F pass with a real BYOK provider: NL routing, web/URL/
  Play-Store, confirmation, **offline parity**. Docs + ADR + `current-status.md` sync.

## 6. Module & topology impact (hard-rules compliance)

- **`domain`** gains `action/` (registry) + `ai/router/CommandPlanner` port + `RouteCommandUseCase`.
  Stays stdlib + coroutines; vendor-neutral (no provider names).
- **`data/ai-cloud`** (or `data/repository/ai`) gains the `CommandPlanner` impl over the existing
  OpenAI-compatible transport. No `data → data` edge; ports live in `domain`.
- **`data/repository`** extends `AndroidActionExecutor` (URL / Play Store) + rule matcher.
- **`feature/launcher`** hosts the universal input + confirmation UI. No `feature → feature` edge; nav via
  `NavigationEvent`.
- **`:app`** wires the new DI (`@RouterEngine` reuse, `RouteCommandUseCase`, `ActionCatalog` binding) and
  the feature flag. Single `NavHost` unchanged.
- `HandleUserCommandUseCase`, `IntentMatcher`, `GenerateReplyUseCase` contracts **unchanged**. Launcher
  core stays fully offline.

## 7. Open questions / ADR needed

- **ADR (blocking, before AIL-4) — ✅ WRITTEN 2026-07-05:** decisions.md "ADR 2026-07-05 — AIL-4: LLM
  Action Router (`CommandPlanner`)". Pins the third-pipeline decision, the rule-first/low-confidence
  trigger, the portable structured-JSON strict-parse contract (non-streaming, separate from the
  assistant body), fail-closed `NoPlan`, confirmation gating, the privacy allow-list extension, and
  router-off ⇒ rule-only parity. CLAUDE.md hard-rules note updated.
- **AIL-Q1:** cost/latency budget for the router call (first-token target reuses the `<2000ms` cloud
  budget; add a hard timeout → `NoPlan`).
- **AIL-Q2:** which model families reliably emit structured tool calls via the OpenAI-compatible endpoint
  (document a recommended default for BYOK users; degrade to `NoPlan` on non-tool-capable models).
- **AIL-Q3:** URL-normalization/safety rules (scheme allow-list, no silent `intent://`, punycode/typo
  guard) — finalize in AIL-2.

## 8. Definition of done (Stage 1B)

- One field routes typed + spoken NL to app / web / site / Play Store / settings / assistant.
- LLM router proposes registered actions on low-confidence NL; risky ones confirm first; **router off →
  byte-for-byte rule-only parity**; offline/failure never breaks the launcher.
- Web/URL/Play-Store routing works without embedding a browser; no malformed URL opened silently.
- No private context sent to cloud; privacy guard green. Typed commands unchanged. Hard rules intact.
- JVM + `assembleDebug` green; SM-A325F device pass; ADR + `current-status.md` synced.
