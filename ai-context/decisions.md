# Decisions

## ADR 2026-08-10 — DS-11 pre-gate UI refinement (Home chrome, route lane, typography)

**Status: ACCEPTED — code-complete, gate green, device acceptance PENDING.** Owner-directed polish pass
run immediately before the DS v1.1 release gate. Spec/plan: `/home/Suleiman/.claude/plans/` DS-11 plan
(owner-approved 2026-08-10). Presentation-only except one persisted default.

### Problem

The owner reviewed the shipped surface before the release gate and raised five changes. Analysis split
them by cost and feasibility: two were hours of work, one changed a design-system contract, one had no
localisation infrastructure to build on, and one was not achievable as described.

### Decision — scope

| # | Owner proposal | Decision |
|---|---|---|
| 1 | Pin the bottom bar, icon-free text tabs, drop the "Local-first" line, fix the terminal icon | **Block A, pre-gate** |
| 3 | Route chips narrower, borderless, un-highlight APP | **Block A, pre-gate** |
| 2 | Softer, multilingual typeface | **Block B, pre-gate** ("sans + narrow mono" variant) |
| 5 | Multilingual UI | **Deferred**, own block after the gate |
| 4 | Corner-swipe → "all active windows" | **Deferred**; direction fixed as AccessibilityService |

Block B was deliberately placed *before* the gate: it re-records all 34 Roborazzi goldens, and doing
that twice (now, then again for i18n) is waste.

### Decision — Block A (presentation-only + one default)

- **Nav-bar flag inverted and renamed: `alwaysShowNavBar` → `autoHideNavBar` (default `false`), new
  DataStore key `user_auto_hide_nav_bar`.** Pinned chrome is now the resting state; auto-hide is the
  opt-in. The 5 s timer, `SidrChromeHandle` and the Settings toggle are all retained — the toggle is
  simply named for the opt-in ("Auto-hide navigation bar") instead of for the old default. Amends the
  2026-07-12 auto-hide spec.

  **This started as a plain default flip and that was wrong** — caught on device, not in review. Merely
  changing `alwaysShowNavBar`'s default to `true` had **no effect on the owner's install**: the bar
  still auto-hid after 5 s, and `run-as ... cat sidr_preferences.preferences_pb` showed
  `user_always_show_nav_bar` already persisted. Cause:
  `PreferencesMapper.writeUserPreferences` writes the *whole* object on every update, so anyone who had
  ever changed any setting had the old value stored, and a stored value always beats a changed default.
  The `?: defaults.…` fallback only helps installs that never wrote the key — i.e. almost none.
  Renaming the flag after the behaviour it enables, with a **new** key, means existing installs have
  nothing stored for it and read the new default. Re-verified on device: pinned past 10 s.
  The orphaned `user_always_show_nav_bar` boolean is left behind in DataStore — inert (Preferences has
  no schema, and it holds no user content) and dropped from `ALL_KEY_NAMES`.
- **`SidrTabBar` replaced Material `NavigationBar`/`NavigationBarItem` with a plain themed row.**
  `NavigationBarItem` requires a non-null `icon` and sizes its selection indicator around that glyph,
  so an icon-free variant is not expressible through it. Selection is now DS press-invert (§5.3), the
  same `fg = ground / bg = text` inversion `SidrRouteChip` uses — which also puts the last piece of raw
  Material app-shell chrome onto the design system. Labels are lowercase and icon-free.
  Insets are re-applied manually (`navigationBarsPadding`), since Material's `NavigationBar` used to.
- **Preview marker `◦` (U+25E6) → `•` (U+2022).** Verified against the shipped TTFs' cmap: IBM Plex
  Sans, Nunito Sans and the platform sans all lack U+25E6 (JetBrains Mono has it, which is why it
  worked before). Android font fallback would have drawn the marker in a foreign face, and a thinned
  OEM font set could tofu it outright. "A preview tab is visibly a preview" is a release-gate
  requirement, so the marker must be a glyph the bundled face actually owns. U+2022 is present in
  every candidate *and* in JetBrains Mono, so it survives the Block B swap either way.
- **`SidrAppFooter`:** the `Local-first · on-device` line removed (a `Spacer` took its `weight(1f)`);
  the hidden 7-tap dev-mode arm on the `SIDR OS` wordmark **retained**. `Icons.Filled.Build` (a wrench)
  replaced by a bundled `ic_terminal_24.xml` — `Icons.Filled.Terminal` lives in
  `material-icons-extended`, several MB for one glyph, so this follows the module's existing
  `ic_mic_24`/`ic_assistant_24` precedent. Zero new dependency.
- **Route lane:** chips size to their text (`Modifier.weight(1f)` dropped) and the resting hairline
  border is gone. Border became a `bordered` parameter on the shared press-invert frame, defaulting to
  `true`, because `SidrFilterChip` (Settings accent, App Drawer Groups/A-Z) genuinely needs a resting
  boundary — without it "Grey Green Amber" reads as three loose words.
- **APP chip un-highlighted** (`selected = true` → `false`). It was a permanent inversion the user
  could never turn off, for a lane that was never a state. The chip itself was **kept** on owner
  direction. Known follow-up: APP still carries an empty `onClick`, i.e. a control that does nothing —
  pre-existing, left alone rather than silently redesigned.

### Decision — Block B (typography)

**Mono's territory narrowed to `command` + `provenance`; everything else is bundled IBM Plex Sans.**

Master Plan §6.2 previously assigned JetBrains Mono the entire interface shell — chips, tab labels,
statuses, section labels, settings labels, metadata. That, rather than the typeface itself, was what
made the UI read "technological" against the §6.1 stated character of `calm / mature / restrained`.
Mono now covers only the two roles where fixed advance carries meaning and which §6.3 preserves as the
terminal signature: the `>` command line and the provenance line.

- `SidrSans`: `FontFamily.SansSerif` → bundled **IBM Plex Sans** (OFL, Regular/Medium/SemiBold/Bold,
  ~800 KB). Chosen over Inter (neutral-technical), Nunito Sans (no Greek) and Manrope: humanist and
  warm, Latin+Cyrillic+Greek+Turkish from one file, and part of a superfamily (IBM Plex Sans Arabic,
  IBM Plex Mono) should the sacred or mono roles ever join it.
- `SidrTextStyles.system`, `SidrTypography.labelLarge`, `SidrTypography.labelSmall`: mono → sans.
- **New `SidrTextRole.CAPTION`** (`SidrTextStyles.caption`, sans 13/19 — Doctrine §16 "Body Small",
  `dim`). All five `SidrRow` variants rendered their `description` as `PROVENANCE`, so every settings
  and permission explanation stayed mono; the first device pass showed "Let the bottom bar slide away
  after a few seconds of inactivity…" rendering as machine output. Borrowing `PROVENANCE` was harmless
  while the whole shell was mono, but once mono means "command or attribution" it is a category error,
  and without this fix mono had not actually narrowed to its stated territory — the single largest
  block of small text in Settings would still have been mono. `PROVENANCE` keeps its real users
  (`SidrProvenanceLine`, timestamps).
- Tracking relaxed 1.4→0.6 sp and 1.6→0.8 sp: the wide CRT tracking compensated for mono's dense fixed
  advance and reads as artificially spaced on a proportional face.
- `SidrTextStyles.sacred` **untouched** (`FontFamily.Serif`). §6.2 defers the final Arabic-capable
  sacred face to DS-6A pending shaping/RTL/diacritics/licence review. Verified against real TTF cmaps:
  **no** sans candidate carries Arabic, so the sacred face is separate by necessity, not oversight.
- `TypographyRoleTest` rewritten from 2 assertions to 4 tests that pin the whole boundary (which roles
  are mono, which are sans, that sans is bundled rather than the platform default, and that sans roles
  do not inherit mono's tracking) — the line is easy to erode one style at a time, so it is asserted
  rather than left to the doc.

### Verification (JDK 17, no piped output, exit codes checked)

`:domain` 332/0 · `:core:ui` **119/0** (was 117; +2 typography contract tests) + `verifyRoborazziDebug`
· `:feature:launcher` **130/0** · `:feature:settings` **33/0** · `:feature:assistant` **35/0** ·
`:data:repository` 167/0 · `:app` 8/0 · root `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL.

**Parity:** every ViewModel suite passes byte-for-byte against the pre-DS-11 baselines recorded in
`CLAUDE.md` (130 / 33 / 35 / 332). No VM, domain, data, navigation-graph or persistence-schema change.

**Goldens:** Block A moved exactly 5 (`controls_{dark,light,rtl}`, `universal_input_{typing,light}`) —
inspected, the only delta is the removed chip border, filter chips kept theirs, no layout shift. Block B
re-recorded all 34. Inspected diffs confirm typeface-only change: `primitives_rtl` grew 935→947 px from
accumulated line-height across ~10 rows with no re-wrap or clipping, and at fontScale 2.0 the sans is
*more* compact — "Open provider settings" and "IDLE — CLOUD DISCLOSURE" now fit on one line where mono
wrapped to two.

### Device verification (SM-A325F, agent-driven adb, owner connected the phone)

**Confirmed on device:** pinned bottom chrome surviving 10 s idle (after the rename fix); lowercase
icon-free tabs `home / apps / tasks • / agents • / activity •` with press-invert on `home`; the `•`
preview markers rendering (no tofu, no fallback face); the terminal glyph reading as a terminal, not a
wrench; the `Local-first · on-device` line gone with `SIDR OS` still in place; borderless
wrap-to-content `APP WEB ASK` with APP no longer inverted; IBM Plex Sans throughout, including the
Cyrillic date line `27 сафар · пн, 10 авг.`; the tab bar sitting flush above the system 3-button nav
with no inset gap; Settings showing the re-labelled "Auto-hide navigation bar" toggle in its off state.

**Not device-confirmed** (phone disconnected mid-pass; it is the owner's tethering link, so it was left
alone): the `CAPTION` role's effect on Settings descriptions — code + goldens verified only; the 7-tap
`SIDR OS` dev-mode arm; the Terminal button's navigation; toggling auto-hide back on; TalkBack on the
new tab semantics; fontScale 2.0 and RTL on the live tab bar; light theme on device.

### Known gaps

- `SidrTabBar`/`SidrAppFooter` have **no automated visual coverage**: Roborazzi is wired only in
  `:core:ui` and these live in `:app`. The device pass above is currently their only evidence.
- The route chips are not covered at fontScale 2.0 by the golden harness: `ControlGallery` is taller
  than the capture viewport at that scale and clips before the CHIPS section. Pre-existing.
- Borderless route chips have no resting visual affordance — press-invert only shows during the press.
  Deliberate owner choice; TalkBack is unaffected (`Role.Button` + `onClickLabel` retained).
- APP route chip remains a control with an empty `onClick`.

---

## ADR 2026-07-05 — AIL-4: LLM Action Router (`CommandPlanner`) — structured routing via BYOK LLM

**Status: ACCEPTED (blocking design ADR — must precede AIL-4 implementation).** Depends on AIL-1
(Action Registry) and AIL-2 (URL / Play-Store actions registered). Companion to the reframe ADR below and
the plan `ai-context/ai-launcher-mvp-plan.md`.

### Problem

The MVP must let the launcher *understand* natural language and *route* it to a safe action, but the
project has two intentionally-separate AI pipelines and a hard rule that matching ≠ generation:

- `IntentMatcher` → `IntentMatchResult` (offline classification; rule-first `LayeredIntentMatcher`).
- `GenerativeAiEngine` → `Flow<AiChunk>` (the assistant's conversational path via `GenerateReplyUseCase`,
  deliberately minimal body: `model/messages/max_tokens/stream:true`, no `tools`, no sampling params).

Neither can drive actions from natural language. We need a path where the LLM maps free text onto a
**registered action**, without folding generation into `IntentMatcher`, without turning the assistant into
an executor, and without breaking "local matching runs before any LLM call".

### Decision

**1. A new, third domain port — `CommandPlanner` — in `domain/ai/router/`.** It is structured
routing-via-LLM: a single decision, not a token stream, not a classification.

```kotlin
// domain — pure, vendor-neutral
interface CommandPlanner {
    /** Returns a routing decision for [command] over the currently-registered [catalog]. Never throws;
     *  offline / no key / provider / parse failure → [PlanResult.NoPlan]. */
    suspend fun plan(command: String, catalog: ActionCatalog): PlanResult
}

sealed interface PlanResult {
    /** The LLM proposed a concrete registered action. [confidence] is the model's own 0..1 (advisory). */
    data class RoutedAction(val action: LauncherAction, val confidence: Float) : PlanResult
    /** The LLM needs one disambiguating answer; UI shows [question], no execution. */
    data class Clarify(val question: String) : PlanResult
    /** Declined / offline / unparseable / non-tool-capable model → caller keeps the rule outcome. */
    data object NoPlan : PlanResult
}
```

**2. Composition — `RouteCommandUseCase` wraps rule-first, planner-second (Fork R2 = b).**
`HandleUserCommandUseCase` is **not** modified.

```
RouteCommandUseCase.route(rawInput):
  outcome = handleUserCommandUseCase.handle(rawInput)          // existing rule path, unchanged, offline
  if (!routerEnabled) return outcome                           // feature flag OFF ⇒ exact rule-only parity
  if (outcome !in { Unknown, LowConfidence }) return outcome   // rule was confident enough — LLM never called (R1)
  if (!online || !hasProviderKey) return outcome               // offline / BYOK not configured ⇒ rule outcome
  when (planner.plan(normalized, catalog)) {
     RoutedAction(a, c) -> CommandOutcome.Suggest/NeedsConfirmation(a)   // NEVER auto-execute (R4)
     Clarify(q)         -> CommandOutcome.Message(q)
     NoPlan             -> outcome                                       // keep the original rule outcome
  }
```

- **Trigger (R1 = b):** the planner is consulted **only** when the rule outcome is `Unknown` or
  `LowConfidence` — the natural-language cases the rules can't handle. Every confident rule outcome
  (`open telegram`, `settings`, `find flights`) is returned untouched and the LLM is never called.
- **"Local matching before any LLM call" holds** — the rule matcher always runs first and short-circuits.

**3. LLM output contract — structured, strict-parse, portable (Fork R3 = b).**
BYOK means arbitrary OpenAI-compatible providers of varying capability, so the contract is **portable
structured JSON**, not a hard dependency on OpenAI function-calling:

- Request: a **separate, non-streaming** call (`stream:false`) — routing is one decision; non-streaming
  lets us hard-timeout and read one body. This is a **new request path**, NOT the assistant's minimal
  streaming body; the assistant path stays byte-for-byte as Block N/K left it.
- Preferred: OpenAI `tools`/`tool_choice` function-calling when the provider advertises it; **fallback:**
  `response_format={type:"json_object"}` (or a plain "reply with only this JSON" instruction) parsing a
  JSON object from `choices[0].message.content`. Either way the parsed shape is one schema:
  `{ "action": "<action_id | none | clarify>", "args": { … }, "confidence": 0..1, "question"?: "…" }`.
- **Strict parse, fail-closed:** unknown `action` id, malformed JSON, args that don't match the descriptor
  `argSchema`, or free-form chatter → **`NoPlan`**. We never execute on a hallucinated/free-text reply.
- The catalog is rendered to the model as tool/schema text from `ActionDescriptor` (`id`, `title`,
  `description`, `argSchema`) — AIL-1 owns that rendering.

**4. Safety gating (Fork R4 = b, MVP).** An LLM-proposed action is **never auto-executed**. It surfaces as
`Suggest` (a tappable chip) or `NeedsConfirmation` (a confirm card) per its `ActionRiskLevel`: `SAFE`
proposals may render as a one-tap Suggest; `CONFIRM` (open arbitrary URL, Play Store, anything the model
invented an argument for) requires an explicit confirm. `DANGEROUS` is not producible in the MVP. Wiring
lands in AIL-5.

**5. Privacy (unchanged invariant).** Outbound = **user command + static action schema only**. No
calendar/location/usage/history/device/clipboard context. Extend `OutboundContextPolicy`'s positive
allow-list with `ACTION_CATALOG_SCHEMA` (static, content-free) and add a guard test that plants a
sensitive value and asserts nothing but command + schema leaves. Prompts/replies are **not persisted**
(no routing history in this track). The API key stays in Keystore (`SecureSecretStore`), never logged.

**6. Failure taxonomy + budget (AIL-Q1).** Reuse the cloud first-token budget as a **hard total timeout**
(`< 2000ms` → `NoPlan`); network/offline/unauthorized/rate-limited/server/parse all collapse to `NoPlan`
(the router is best-effort — a failure must be invisible beyond "no smarter suggestion appeared"). The
router never surfaces an `AiError` to the launcher UI.

**7. Topology.** Port + `RouteCommandUseCase` + `PlanResult` in `domain` (stdlib+coroutines,
vendor-neutral). Impl in `data/ai-cloud` (its own HTTP call reusing the injected `HttpClient` +
`AiProviderConfigRepository` + `SecureSecretStore` + `ConnectivityChecker`); **no `data → data` edge**
(ports in `domain`). `:app` binds `RouteCommandUseCase` into `LauncherViewModel` in place of the direct
`HandleUserCommandUseCase` call, plus the `@Router` engine wiring and the feature flag. No
`feature → feature` edge; launcher core stays fully offline.

### Consequences

- **Refines, does not break, "matching ≠ generation".** A *third* pipeline is now sanctioned:
  classification (`IntentMatcher`), conversation (`GenerateReplyUseCase`), and **structured routing**
  (`CommandPlanner`). CLAUDE.md hard-rules updated to name it.
- **Router-off / offline / no-key ⇒ byte-for-byte the current rule-only launcher.** This is the acceptance
  guard and a required test.
- Adds a new non-streaming request path to `data/ai-cloud`; the assistant's streaming body is untouched.
- Model-capability variance is absorbed by the fallback contract + fail-closed parse; non-tool-capable
  models degrade to `NoPlan`, so the launcher is never worse than rule-only.

### Rejected alternatives

- **Fold routing into `IntentMatcher` / an ONNX classifier** — rejected: violates the hard rule and is
  blocked on OQ#1/#2; the whole point is cloud LLM understanding now.
- **Let the assistant screen execute actions** — rejected: conflates conversation with routing, and the
  assistant path is deliberately privacy-minimal and streaming-only.
- **Auto-execute high-confidence LLM proposals** — rejected for the MVP (owner decision: confirmation for
  risky actions; no autonomy). Revisit per-risk in Stage 2.
- **Free-text prompt parsing** — rejected: not fail-closed; a hallucinated sentence could trigger an
  action. Structured strict-parse only.

### Open items carried to implementation

- **AIL-Q2:** document a recommended BYOK default model that reliably emits structured output; detect
  non-tool-capable models → `NoPlan`.
- **AIL-Q3:** URL-normalization/safety (scheme allow-list, no silent `intent://`, punycode/typo guard) —
  shared with AIL-2's `OpenUrlAction`.
- Confirmation UX + risk→UI mapping is specified and tested in **AIL-5**.

## ADR 2026-07-05 — Project reframed into three stages; Stage-1 AI-Launcher completion track

**Context.** Review found a large gap between the stated goal ("AI launcher evolving into an agentic OS")
and the shipped reality: routing is rule-based only (`RuleBasedIntentMatcher` = 7 verbs + a command
table), the local NLU/ONNX pipeline is inert (no model, OQ#1/#2), and the assistant is an isolated chat
screen with **no tool/function-calling** — it talks, it cannot act. The foundation (Phases 0–9 + Phase
UX) is solid and device-accepted, but nothing on the shipped path is genuinely "AI".

**Decision (owner, 2026-07-05).**
1. **Reframe the product into three shippable stages:** **Stage 1 — AI Launcher (MVP, now)** → **Stage 2
   — AI Framework** → **Stage 3 — Agentic OS**. The old numeric Phases 10–15 are absorbed: Phase 10/11 →
   Stage-1 completion + Stage-2 Framework-1; Phase 12/13 → Framework-2/3; Phase 8 (accessibility) + Phase
   14/15 → Stage-3 Agentic-1/2. Roadmap rewritten: `docs/roadmap.md`.
2. **AI core of the MVP = BYOK cloud LLM routing.** A new `CommandPlanner` port uses the existing
   OpenAI-compatible engine to understand natural language and propose **structured, registered actions**.
   The rule matcher stays the fast offline fallback. Chosen over reviving local ONNX NLU (OQ#1/#2) because
   it validates the agentic core immediately and is not blocked on model selection/hosting.
3. **Action rights of the MVP = understand + route to safe actions.** The AI proposes/executes registered
   actions (open app, web search, open site, Play Store, settings, assistant); **risky actions require
   explicit confirmation**; no autonomy, no multi-step chains (deferred to Stage 3).

**Consequences / invariants.**
- `CommandPlanner` is a **third pipeline** — not folded into `IntentMatcher`, not the assistant's
  conversational `GenerateReplyUseCase`. It is consulted **only** on low rule-confidence / NL input, so
  "local matching runs before any LLM call" is preserved. This consciously refines (does not break) the
  "matching ≠ generation" hard rule; a dedicated AIL-4 ADR will pin the details before implementation.
- **Router-off ⇒ byte-for-byte rule-only parity;** offline/failure/no-key → the planner returns `NoPlan`
  and the caller keeps the rule outcome. LLM proposals never auto-execute a risky action.
- **Privacy unchanged:** the router sends only the user command + the static Action Registry tool schema —
  no calendar/location/usage/history/device context. The `OutboundContextPolicy` allow-list is extended
  and guard-tested.
- Active plan + forks (R1–R8, blocks AIL-1…6): `ai-context/ai-launcher-mvp-plan.md`. The model track
  (OQ#1–#4), device matrix, and RC/hardening polish run in parallel, off the AI-launcher ship gate.

## Accepted architecture

- Use multi-module Clean Architecture.
- Keep domain models and use cases independent from Android APIs.
- Use Compose and Material 3 for UI.
- Use Hilt for dependency injection.
- Use Ktor for cloud AI calls and streaming.
- Use Kotlin Serialization for DTOs.

## AI decisions

- Use local fast intent matching before LLM calls.
- Use cloud AI as the default generative engine.
- Use ONNX Runtime Mobile for NLU, intent classification, and embeddings.
- Do not use ONNX Runtime Mobile as the primary generative LLM runtime.
- Keep local LLM runtime behind an interface for future MediaPipe LLM or llama.cpp integration.
- Use unified `Flow<AiChunk>` streaming for all AI engines.

## Android decisions

- Support Android 9+ (API 28+).
- Accessibility Service is optional and requires explicit user consent.
- Core launcher behavior must work without accessibility permissions.
- Low-end devices should use cloud AI or rule-based behavior, not local generative LLM.

## Implementation decision

Start with documentation and compile-ready skeleton only. Add business logic in later phases.

## Navigation decisions

### 3.1.1 — Route constants placement
- Route constants live in `:core:common` (`com.sidr.launcher.core.common.navigation.Routes`).
- Uses a `sealed class Routes` with nested `object` entries, each holding a `const val ROUTE`.
- Rationale: `:core:common` is the only module visible to both `:app` and all feature ViewModels without violating dependency direction. A dedicated `:core:navigation` module is not required at this stage.

### 3.1.2 — Single NavHost in :app
- A single `AppNavHost` composable lives in `:app` (`com.sidr.launcher.navigation.AppNavHost`).
- `LauncherActivity` calls `AppNavHost()` as the sole content root; it contains no ad-hoc navigation logic.
- Feature modules must NOT create their own `NavHost` instances.
- Start destination: `Routes.Launcher.ROUTE`.
- Navigation Compose (`androidx.navigation:navigation-compose:2.8.5`) was added to `libs.versions.toml` in this step (was not present before).

### 3.1.3 — Feature composable entry-point pattern
- Each feature module exposes a single public top-level composable as its navigation entry point:
  - `:feature:launcher` → `LauncherScreen()` (`com.sidr.launcher.feature.launcher.LauncherScreen`)
  - `:feature:assistant` → `AssistantScreen()` (`com.sidr.launcher.feature.assistant.AssistantScreen`)
- `:app` depends on feature modules and calls these composables inside `NavHost composable()` blocks mapped to the corresponding `Routes` constant.
- Feature modules do NOT depend on each other — dependency direction is `app → feature/* → domain/core`.
- `:feature:settings` and `:feature:permission_education` modules do not exist yet; their destinations remain inline `Text(...)` placeholders in `AppNavHost` until those modules are created.

### 3.1.4 — NavigationEvent flow pattern
- `NavigationEvent` is a `sealed interface` in `:core:common` (`com.sidr.launcher.core.common.navigation.NavigationEvent`).
- Two variants: `NavigateTo(val route: String)` and `NavigateBack`.
- Rationale for placement in `:core:common`: same module that holds `Routes.kt`; no Android or Navigation Compose dependency required; reachable by all feature ViewModels and by `:app` without violating dependency direction.
- ViewModels emit events via a `Channel<NavigationEvent>(BUFFERED)` exposed as `Flow<NavigationEvent>` — they do NOT import or hold a reference to `NavHostController` or any Navigation Compose API.
- `AppNavHost` collects the flow inside a `LaunchedEffect` per destination and calls `navController.navigate(event.route)` / `navController.popBackStack()` — `NavHostController` is used exclusively in the `:app` module.
- Pattern currently applied to `:feature:launcher` (`LauncherViewModel`). `:feature:assistant` will adopt the same pattern when its real ViewModel is built.

### 3.1.5 — Safe fallback navigation
- A private helper `handleNavigationEvent(navController, event)` lives in `AppNavHost.kt` and is the single place that executes navigation actions.
- `NavigationEvent.NavigateTo`: wraps `navController.navigate(route)` in a `try/catch` for `IllegalArgumentException`. If the route is not registered in the graph, logs a warning (non-sensitive: only the route string is logged, never user content) and navigates to `Routes.Launcher.ROUTE` with `popUpTo(Routes.Launcher.ROUTE) { inclusive = false }` + `launchSingleTop = true` to avoid back-stack accumulation.
- `NavigationEvent.NavigateBack`: calls `navController.popBackStack()`; if it returns `false` (already at root), logs a debug message and does nothing — no forced re-navigation that could cause an infinite loop.
- The helper is written for reuse: every future `LaunchedEffect` block collecting a `NavigationEvent` flow calls `handleNavigationEvent(...)` instead of duplicating the logic.

## Structure and sequencing decisions

### ADR 2026-06-19 — `OperationResult` ownership moves to `domain`
- Decision: `OperationResult<T>` and `OperationError` are **domain** contracts and must live in `:domain`, not `:core:common`.
- Context: they currently sit in `:core:common` (`com.sidr.launcher.core.common.result`), and `domain/build.gradle.kts` declares `implementation(project(":core:common"))`. This creates a `domain -> core/common` edge that violates the rule `domain -> Kotlin stdlib / coroutines only`.
- Rationale: the error categories (`NetworkError`, `AiUnavailable`, `PermissionDenied`, `DeviceNotCapable`, `UnknownError`) are domain semantics; use cases and repository interfaces (which return `OperationResult`) are domain-owned. The "in `core/common` or `domain`" choice from checklist `3.0.1` is resolved in favor of `domain`.
- Consequence: `core/common` keeps `UiState`, dispatchers, logging, and navigation contracts only. `ResultLogger` must be decoupled so the move introduces **no** new `core/common -> domain` edge (primitive-based logging, or relocate the logging port to `domain`).
- Scheduled as **Block A** of `ai-context/phase-3-intent-system-plan.md`.
- **Done 2026-06-19 (Block A complete, A1–A6):** `OperationResult` / `OperationError` moved to
  `:domain` (`com.sidr.launcher.domain.result`); `domain/build.gradle.kts` now depends on
  stdlib + coroutines only (`implementation(project(":core:common"))` removed). Logging kept in
  `core/common` and decoupled to non-sensitive primitives — `ResultLogger.logFailure(category:
  FailureCategory, retryable, context)` with a new `FailureCategory` enum; the `logIfFailure`
  bridge (which spanned both modules) was dropped — it had no callers and returns in the layer
  depending on both (`:data:repository`, Block B). `TestFixtures` moved with the result types
  (relocates to `:core:testing` in B4). A3 had nothing to convert: `:domain` had no source files
  yet, so no throwing contract existed; the "return `OperationResult`, never throw to UI" rule
  applies to the contracts added in Blocks B–D. Verified: `./gradlew :domain:dependencies`
  (compileClasspath = `kotlin-stdlib` + `kotlinx-coroutines-core` only) and
  `./gradlew assembleDebug` both green. No new `core/common -> domain` edge.

### ADR 2026-06-19 — Block B complete (minimal P2 slice)
- **Done 2026-06-19 (Block B, B1–B7):** Created `:data:repository` module; implemented
  `InstalledAppsRepositoryImpl` over `PackageManager` (launchable-apps query, fully offline,
  API 33+ gate for `ResolveInfoFlags`); defined `InstalledApp` + `InstalledAppsRepository`
  in `:domain`; bootstrapped `:core:testing` with `FakeInstalledAppsRepository`. Added
  `UiState<T>` / `UiError` to `:core:common` (no `domain` dep added — `UiError` is a UI
  type; ViewModel maps `OperationError → UiError` internally). `@IoDispatcher` qualifier in
  `core:common` via JSR-330 (`javax.inject`). `LauncherViewModel` extended: two independent
  flows — `navigationEvents` (Channel, unchanged) and `uiState: StateFlow<UiState<LauncherUiState>>`
  plus `commandInput: StateFlow<String>`. `LauncherScreen` rebuilt: `Box(weight(1f))` for the
  app grid, always-visible `CommandInputBar` with `imePadding()`. Icons loaded async via
  `rememberAppIcon` (`LaunchedEffect` + `Dispatchers.IO`, catches `NameNotFoundException`).
  Tap-to-launch and command-submit are stubs (replaced in D2/D3). Hilt graph: `DispatcherModule`
  + `RepositoryModule` in `:app`; `feature:launcher` has no edge to `:data:repository`.
  Verified: `assembleDebug` green (275 tasks), `testDebugUnitTest` green, `:domain`
  compileClasspath = stdlib + coroutines only.

### ADR 2026-06-21 — Block C complete (pure-domain intent core)

**Done 2026-06-21 (Block C, C1–C9).**

**New files in `:domain` (`com.sidr.launcher.domain.intent`):**
- `LauncherIntent.kt` — sealed interface + `SearchTarget` enum + `SimpleCommand` enum (C1/C2)
- `ExecutableAction.kt` — sealed interface incl. `AmbiguousAppAction(query, candidates: List<InstalledApp>)` (C3)
- `IntentCandidate.kt` — `(intent, confidence, debugReason?)` (C1)
- `IntentMatcher.kt` — port interface + `IntentMatchResult` + `MatcherSource { RULE_BASED, NLU }` (C4). KDoc explicitly forbids folding `GenerativeAiEngine` into this port.
- `IntentConfidencePolicy.kt` — interface with default methods (C7)
- `DefaultIntentConfidencePolicy.kt` — `class` with constructor params (`autoExecuteThreshold=0.85f`, `suggestThreshold=0.50f`); overridable per DeviceProfile/feature-flag (C7)
- `CommandNormalizer.kt` — `object`, trim+collapse+`lowercase(Locale.ROOT)` (C5)
- `IntentActionResolver.kt` — use case; `OperationResult.Failure` only for technical repo errors; normal outcomes (`AmbiguousAppAction`, `ShowMessageAction("not found")`) are `Success` (C8)

**New file in `:data:repository` (`com.sidr.launcher.data.repository.intent`):**
- `RuleBasedIntentMatcher.kt` — Android-free; implements `IntentMatcher`; rules: launch-verb → `LaunchAppIntent`, search-verb → `SearchIntent`, bare-keyword → `OpenSettingsIntent` / `SimpleCommandIntent`, fallback → `UnknownIntent` (C6)

**Product decision — verb always wins:** `"open settings"` / `"launch settings"` → `LaunchAppIntent("settings")`, NOT `OpenSettingsIntent`. Documented in test and KDoc. Resolver returns `ShowMessageAction("not found")` if no app matches. To refine in Block D.

**`MatcherSource.AI` intentionally absent** — prevents silent merger of intent-matching and generation ports.

**Test results (C9):**
- `CommandNormalizerTest` — 10 tests, 0 failures (`:domain:test`)
- `IntentConfidencePolicyTest` — 15 tests, 0 failures (`:domain:test`)
- `IntentActionResolverTest` — 16 tests, 0 failures (`:domain:test`); uses `FakeInstalledAppsRepository` from `:core:testing`
- `RuleBasedIntentMatcherTest` — 24 tests, 0 failures (`:data:repository:testDebugUnitTest`)
- **Total: 65 tests, 0 failures, 0 skipped**

**Verification:**
- `grep -rn "import android" domain/src/` → empty (exit 1) ✓
- `:domain:dependencies --configuration compileClasspath` → `kotlin-stdlib` + `kotlinx-coroutines-core` only ✓
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (275 tasks) ✓

### ADR 2026-06-21 — Block D complete (MVP loop: text → action → app launch)

**Done 2026-06-21 (Block D, D1–D7). Phase 3 closed.**

**New files in `:domain` (`com.sidr.launcher.domain.intent`):**
- `ActionExecutor.kt` — port `suspend fun execute(action): ActionExecutionResult` + sealed
  `ActionExecutionResult` (D1). Android-free; impl lives in `:data:repository`.
- `CommandOutcome.kt` — the single UI outcome type (D3); 12 variants: `Empty, Executed, NoOp,
  Message, NeedsConfirmation(candidates), Suggest(intent, confidence), LowConfidence,
  Unknown(input), Failed(message), OpenAssistant, ShowApps, ClearInput`.
- `HandleUserCommandUseCase.kt` — orchestrator `normalize → empty? → match → confidence gate →
  route → execute-when-safe` (D3).

**New files in `:core:testing`:** `FakeIntentMatcher.kt`, `FakeActionExecutor.kt` (records
executed actions so tests assert the executor is NOT invoked for routing-only outcomes).

**New test in `:domain`:** `HandleUserCommandUseCaseTest.kt` — 19 tests (`:domain:test`), on
`FakeInstalledAppsRepository` + `FakeIntentMatcher` + `FakeActionExecutor`.

**New file in `:data:repository` (`com.sidr.launcher.data.repository.intent`):**
- `AndroidActionExecutor.kt` (D2) — launch via `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK`
  from `@ApplicationContext`; web search via `ACTION_VIEW` (`// TODO: configurable search provider`,
  hardcoded Google for this slice); `ActivityNotFoundException` / `SecurityException` →
  `ActionExecutionResult.Failure(safeMessage)`, never crashes. No sensitive permissions.

**Changes in `:feature:launcher`:**
- `CommandFeedback.kt` (D5) — transient UI model: `None / Message / Suggestion / Ambiguous(candidates)`.
- `LauncherViewModel.kt` (D4) — injects `HandleUserCommandUseCase` + `ActionExecutor` (both **domain**
  ports — no `feature → data` edge); third independent flow `commandFeedback: StateFlow<CommandFeedback>`;
  `onCommandSubmitted` → use case; `onAppClicked` → executor **directly** (app already known, no matching);
  `applyOutcome` maps all 12 `CommandOutcome` variants with an **exhaustive `when`, no `else`**.
- `LauncherScreen.kt` (D5) — renders `CommandFeedback` (message/suggestion + tappable ambiguity
  candidates that launch via `onAppClicked`).

**Changes in `:app` (D6):** `IntentBindsModule` (abstract, `@Binds ActionExecutor ←
AndroidActionExecutor`) + `IntentProvidesModule` (object, `@Provides` for `IntentMatcher =
RuleBasedIntentMatcher()`, `IntentConfidencePolicy = DefaultIntentConfidencePolicy()`,
`IntentActionResolver`, `HandleUserCommandUseCase`). `CommandNormalizer` is an `object` (static call,
not provided). Mirrors `DispatcherModule` / `RepositoryModule`.

**Decision — truncated `ActionExecutionResult`:** modeled as `Success / Failure(safeMessage) /
Unsupported(action)` only. The plan's literal D1 also listed `needs-confirmation` / `no-match`; these
were **deliberately omitted** — they are routing outcomes decided by the use case *before* execution,
so they live in `CommandOutcome`, not in the executor result. Putting them in both would create the
two overlapping result types the Block D invariant forbids. `ActionExecutionResult` (executor
vocabulary) and `CommandOutcome` (UI vocabulary) are distinct, and both are distinct from
`OperationResult` (technical success/failure).

**Routing decisions (the core of Block D — not everything goes to the executor):**
- Confidence gate via `IntentConfidencePolicy`: `≥ 0.85` resolve+execute; `0.50..<0.85` →
  `Suggest` (no auto-execute); `< 0.50` → `Unknown` (if `UnknownIntent`) else `LowConfidence`.
- `LaunchAppAction` / `OpenSearchAction` → `ActionExecutor`. `AmbiguousAppAction` → `NeedsConfirmation`
  (never executed). `ShowMessageAction` → `Message`. `OpenLauncherSettingsAction` → stub `Message`
  (no settings module; never reaches Android). `NoOpAction` → `NoOp` (no input clear).
- `SimpleCommandIntent` is routed **before** the resolver (the resolver collapses command identity):
  `OPEN_ASSISTANT` → `CommandOutcome.OpenAssistant` (VM calls `navigateTo(Routes.Assistant.ROUTE)` via
  the existing 3.1.x `Channel`; the route string is supplied by the **VM**, never the domain),
  `SHOW_APPS` → `ShowApps`, `CLEAR` → `ClearInput`, `HELP` → `Message`. None go through the executor.
- Business outcomes (ambiguous, not-found, low/medium confidence, unknown) flow through a successful
  `CommandOutcome`, never `OperationResult.Failure`. Technical failures from the resolver/executor →
  `CommandOutcome.Failed(safeMessage)`; the use case never throws to UI.
- `commandInput` is physically cleared (`_commandInput.value = ""`) only on `Executed`,
  `OpenAssistant`, `ShowApps`, `ClearInput`.

**DI packaging fix:** the first attempt used a single `IntentModule` with a nested
`@Module`-annotated `companion object`; Hilt rejected it (*"IntentModule.Companion is listed as a
module, but it is a companion object class"*) and `assembleDebug` failed. Resolved by splitting into
two top-level modules (`IntentBindsModule` + `IntentProvidesModule`); bindings unchanged.

**`IntentMatcher` / `GenerativeAiEngine` stay separate** — Block D added no generation port and did
not touch AI; the two-port invariant holds.

**Verification:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew testDebugUnitTest --rerun-tasks` → green (Block B not regressed; new Phase-2 VM tests +
  Phase-1 use-case tests executed, not NO-SOURCE).
- `grep -rn "data.repository" feature/launcher/src/` → empty; `feature/launcher/build.gradle.kts` has
  no `:data:repository` edge ✓.
- `grep -rn "import android" domain/src/` → empty; `:domain` remains stdlib + coroutines only ✓.
- **Live launch verified on device (SM-A325F, Android 13):** `open <app>` and grid tap launch apps
  offline; unknown command → fallback UI, no crash; `clear` clears input.

### ADR 2026-06-22 — Block E complete (DataStore Preferences foundation)

**Done 2026-06-22 (Block E, E1–E8). First Phase-4 execution round. Forks 1/3/4 honoured.**

**New files in `:domain` (`com.sidr.launcher.domain.preferences`):**
- `UserPreferences` (`themeName="system"`, `commandInputEnabled=true`), `FeatureFlags`
  (`aiSuggestionsEnabled`/`usageHistoryEnabled`/`permissionEducationDismissed`, all `false`),
  `DeviceProfileCacheEntry` (`isLowEndDevice`, `cachedAtEpochMs`), `CachedSuggestion`
  (`label`, `actionId`) — all pure data classes, **no `@Serializable`** (annotation is a
  data-layer concern).
- Repo interfaces `UserPreferencesRepository`, `FeatureFlagRepository`,
  `DeviceProfileCacheRepository`, `SuggestionsCacheRepository` — reads `Flow<T>`, writes
  `suspend → OperationResult<Unit>`. `:domain` stays stdlib + coroutines (guard verified).

**New files in `:data:repository` (`…data.repository.preferences`):**
- `PreferencesKeys.kt` — **E1 privacy-inventory anchor comment** (allowed / forbidden / deferred
  + `CachedSuggestion` field-by-field "cached/not-cached" split); 9 prefixed keys
  (`user_`/`flag_`/`device_`/`sug_`); `ALL_KEY_NAMES` set + `MAX_CACHED_SUGGESTIONS=5`.
- `PreferencesMapper.kt` — pure `Preferences ↔ domain` mapping; holds `@Serializable`
  `CachedSuggestionDto` (private) so the domain model stays annotation-free; reads fall back to
  domain defaults on missing keys.
- 4 `*RepositoryImpl` over one injected `DataStore<Preferences>` + `@IoDispatcher`; reads
  `dataStore.data.catch{IOException→emptyPreferences()}.map{…}`; writes `withContext(io){ try
  edit … catch IOException → OperationError.UnknownError }`. **Never throws to caller.**

**New files in `:app/di`:** `PersistenceProvidesModule` (object — `@Provides @Singleton
DataStore<Preferences>` via `PreferenceDataStoreFactory`, file `sidr_preferences`, scope =
`@IoDispatcher + SupervisorJob`) + `PersistenceBindsModule` (abstract — `@Binds` ×4). Split
into two modules for the same Hilt reason as Block D (can't mix `@Binds`/`@Provides`). `:app`
gained `datastore-preferences` for the DataStore types.

**New fakes in `:core:testing`:** `FakeUserPreferencesRepository`, `FakeFeatureFlagRepository`,
`FakeDeviceProfileCacheRepository`, `FakeSuggestionsCacheRepository` — `MutableStateFlow`-backed,
configurable `errorToReturn`.

**Decisions made during execution:**
- **Suggestions serialised as JSON, not a delimiter** — labels may contain `|||`, quotes,
  newlines; a string-split scheme would corrupt. `kotlinx-serialization-json` was already in the
  catalog (not a new dep); added the plugin + lib to `:data:repository` only. Test round-trips a
  label with `||| "quotes"` + newline.

### ADR 2026-07-01 — Acceptance blockers follow-up (settings toggle, voice probe, assistant route)

**Done 2026-07-01.**

- `Routes.Settings` no longer renders a bare `Text("Settings")` placeholder. It now hosts a minimal
  inline launcher-settings surface in `:app` (`LauncherSettingsScreen` + `LauncherSettingsViewModel`)
  with a sanctioned `aiSuggestionsEnabled` toggle for device acceptance. No adb/debug hack is
  required.
- Toggling `aiSuggestionsEnabled` now re-syncs `SuggestionsWorkScheduler.ensureScheduled()`
  immediately, so precompute scheduling/cancellation follows the setting without waiting for a
  restart/boot.
- `LauncherViewModel` now observes `FeatureFlagRepository.getFlags().map { aiSuggestionsEnabled }`
  live: enabling suggestions restores cached display-safe suggestions and refreshes the engine;
  disabling clears `LauncherUiState.suggestions`. The single-owner invariant stays intact
  (`LauncherViewModel` / `LauncherUiState.suggestions`; `:feature:suggestions` remains stateless UI).
- `AndroidSpeechInputSource.isAvailable()` was hardened for OEM/package-visibility false negatives:
  in addition to the framework `SpeechRecognizer` probes, it now accepts a resolvable
  `android.speech.RecognitionService` or `ACTION_RECOGNIZE_SPEECH` handler as evidence that voice
  input is usable. The manifest gained matching `<queries>` entries so those components are visible
  on Android 11+.
- `RuleBasedIntentMatcher` again exposes a rule-only assistant launcher entry:
  `"assistant"` / `"show assistant"` → `SimpleCommand.OPEN_ASSISTANT`. `HandleUserCommandUseCase`
  already routes `OPEN_ASSISTANT` to `CommandOutcome.OpenAssistant`, so assistant launch remains
  independent of NLU/model availability and never hits the executor.
- Launcher settings routing is now a first-class `CommandOutcome.OpenSettings` outcome instead of a
  stub message; the domain still stays Android-free and the ViewModel owns route-string translation.

**Verification:**
- New/updated JVM coverage in `feature:launcher`, `domain`, `data:repository`, `core:android`, and
  `app` (settings toggle scheduling + voice availability helper + assistant rule + settings route).
- `./gradlew testDebugUnitTest` ✅
- `./gradlew assembleDebug` ✅

**Still pending / explicitly NOT closed by this ADR:**
- provider config/key for real assistant streaming
- OQ#1 / OQ#2 real NLU model + vocab acceptance
- OQ#3 embedding model / semantic-rank acceptance
- cold-start performance budget on device
- **`DeviceProfileCacheEntry` is a primitive projection, not a `DeviceProfile` alias** — the
  domain `DeviceProfile`/capability model isn't formalised yet; flattened booleans avoid a
  premature dependency and serialise cleanly. When `DeviceProfile` lands, only the mapper +
  interface change; the DataStore keys are stable. Nullable `Flow<…?>` distinguishes "no cache
  yet" from defaults (gated by a `device_has_cache` marker key).
- **`PrivacyInventory` object dropped from `:domain`** — it would be inert documentation in the
  pure module. Inventory lives as the `PreferencesKeys` anchor comment; the guard test runs over
  the real key *strings*.
- **`supportsVoiceInput` removed from the cache entry** — voice is frozen to Phase 7, and the
  key name `device_supports_voice_input` would collide with the `"voice"` denylist term. Field +
  key both dropped; `"voice"` stays an effective guard term.
- **`PrivacyInventoryGuardTest` checks key *values*, not Kotlin names** — asserts no key string
  (`"user_theme_name"`) contains a forbidden term (`voice/query/search/location/calendar/history/
  conversation/message/transcript/secret/token/api`). `"key"` deliberately excluded (redundant
  with secret/token/api, would false-match). The test surfaced a real collision:
  `flag_usage_history_enabled` matched `"history"` → key string renamed to
  `flag_usage_tracking_enabled` (domain field `usageHistoryEnabled` unchanged; Room remains the
  only history carrier). Denylist kept intact.

**Verification:**
- `:data:repository:testDebugUnitTest` → 14 preference tests green (round-trip incl.
  simulated process-restart via scope-cancel + reopen on the same tmp file; defaults; nullable
  cache + `clearCache`; bounded list; JSON special-char round-trip; privacy guard). Executed,
  not NO-SOURCE.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (Hilt graph with both new modules).
- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL (no regressions).
- `grep -rn "import android" domain/src/` and `grep -rn "androidx.datastore" domain/src/` →
  empty. `:domain:dependencies` compileClasspath = `kotlin-stdlib` + `kotlinx-coroutines-core`
  only. `feature/*` has no edge into `:data:*`.

**Frozen, untouched:** permission-education (G), hardening (H), secrets (Ph5).

### ADR 2026-06-22 — Block F complete (Room persistence: history tables, redaction, migration runway)

**Done 2026-06-22 (Block F, F1–F8). Second Phase-4 execution round. Forks 2, 4, 8 honoured.**

**New files in `:domain` (`com.sidr.launcher.domain.history`):**
- `AppUsageRecord`, `SuggestionRankingRecord`, `IntentMatchRecord` — pure data classes, no Room.
- `IntentMatchType` enum: `LAUNCH_APP / SEARCH / OPEN_SETTINGS / SIMPLE_COMMAND / UNKNOWN`.
- Repo interfaces `UsageHistoryRepository`, `SuggestionRankingRepository`,
  `IntentMatchHistoryRepository` — reads `Flow<List<T>>`, writes `suspend → OperationResult<Unit>`.

**New files in `:data:repository` (`…data.repository.db`):**
- `entity/`: `AppUsageEntity`, `SuggestionRankingEntity`, `IntentMatchEntity` with `@ColumnInfo`
  names matching `RoomColumnNames`.
- `dao/`: `AppUsageDao` (abstract, `@Transaction` upsert), `SuggestionRankingDao`, `IntentMatchDao`.
- `SidrDatabase` (`@Database` version=1, `exportSchema=true`); golden schema
  `schemas/com.sidr.launcher.data.repository.db.SidrDatabase/1.json` committed to VCS.
- `converter/IntentMatchTypeConverter` — `String ↔ IntentMatchType`, defensive `→ UNKNOWN`.
- `mapper/IntentMatchMapper` — **redaction**: `SEARCH` → `"search"`, `UNKNOWN` → `"unknown"`;
  `LAUNCH_APP / OPEN_SETTINGS / SIMPLE_COMMAND` stored as-is (closed vocabulary).
- `mapper/AppUsageMapper`, `mapper/SuggestionRankingMapper` — plain entity↔domain.
- `RoomColumnNames` — mirrors `PreferencesKeys.ALL_KEY_NAMES` for the privacy guard test.
- 3 `*RepositoryImpl` over injected DAOs + `@IoDispatcher`; retention caps enforced on every write:
  `MAX_USAGE_ROWS=200`, `MAX_RANKING_ROWS=100`, `MAX_INTENT_MATCH_ROWS=200`; oldest/lowest-scored
  pruned. I/O exceptions caught → `OperationError.UnknownError`, `CancellationException` re-thrown.
- `migrations/` package seeded (empty runway); rule: entity change → version bump + Migration +
  new golden schema.

**Changes in `:domain` (`…domain.intent`):**
- `HandleUserCommandUseCase`: added optional `intentMatchHistory: IntentMatchHistoryRepository? = null`
  + `now: () -> Long`; `recordMatch(...)` called after match, before confidence gate; soft-wrapped
  (Failure discarded; `Throwable` swallowed except `CancellationException`). 12 `CommandOutcome`
  branches unchanged; 5 new tests for write/soft-wrap behaviour.

**Changes in `:feature:launcher`:**
- `LauncherViewModel` injects `UsageHistoryRepository` (domain interface — no `feature→data` edge);
  grid sorted via `combine(...).stateIn(Eagerly)` by `launchCount DESC, lastUsedEpochMs DESC`;
  apps with no history keep original order; `onAppClicked` records usage on `Success`, soft-wrapped.
  7 new VM tests.

**New files in `:app/di`:**
- `DatabaseModule` (object — `@Provides @Singleton SidrDatabase` via `Room.databaseBuilder`,
  `fallbackToDestructiveMigration` only if `BuildConfig.DEBUG`; release fails loudly).
- `HistoryBindsModule` (abstract — `@Binds` ×3).
- `IntentProvidesModule` updated: passes real `IntentMatchHistoryRepository` to `HandleUserCommandUseCase`.

**New fakes in `:core:testing`:**
- `FakeSuggestionRankingRepository`, `FakeIntentMatchHistoryRepository` — `MutableStateFlow`-backed,
  `errorToReturn`, recorded-calls list, `setRecords()`. Note: fakes do NOT apply redaction
  (redaction is a data-layer invariant; tested via real impl + in-memory DB).

**Tests (F8):**
- `UsageHistoryRepositoryImplTest` (4 tests), `SuggestionRankingRepositoryImplTest` (4 tests),
  `IntentMatchHistoryRepositoryImplTest` (7 tests including 5 redaction cases) — Robolectric
  `@RunWith(RobolectricTestRunner)`, in-memory `SidrDatabase`, `allowMainThreadQueries()`.
- `RoomColumnNamesGuardTest` (2 tests) — asserts no column name contains a forbidden term
  (same denylist as `PrivacyInventoryGuardTest`; table names exempted as structural identifiers).
- `MigrationTest` in `androidTest` — `MigrationTestHelper` creates DB v1 from golden schema;
  run manually: `./gradlew :data:repository:connectedDebugAndroidTest`.
- Total new JVM tests: 17. No regressions (Block C/D/E tests still green).
- `testImplementation(libs.androidx.test.ext.junit)` added to `:data:repository` to bring in
  `androidx.test.core` for `ApplicationProvider` in Robolectric tests.

**Decisions made during execution:**
- **KSP/kapt hybrid (Fork 8):** Room on KSP (`com.google.devtools.ksp` 2.0.21-1.0.28), Hilt on
  kapt. Fix for classloader issue (google/dagger#3965): `ksp { apply false }` in root
  `build.gradle.kts` alongside `kotlin-kapt` and `hilt` plugin declarations.
- **Schema lifecycle (Fork 2):** `exportSchema=true`, `room.schemaLocation → data/repository/schemas/`,
  `schemas/1.json` committed. `fallbackToDestructiveMigration` in `DatabaseModule` gated on
  `BuildConfig.DEBUG` (all three history tables are recreatable; release never silently wipes).
- **Robolectric (Fork 7):** added `testImplementation(libs.robolectric)` (4.14.1) to
  `:data:repository`. `testOptions { unitTests { isIncludeAndroidResources = true } }` required.
  Migration baseline test in `androidTest` (needs device; not in JVM CI).
- **Redaction principle (Fork 3):** *Intent-match redaction rule: match types carrying arbitrary
  user content (SEARCH, UNKNOWN) store a placeholder, never the content; structurally-bounded
  types (LAUNCH_APP, OPEN_SETTINGS, SIMPLE_COMMAND) are stored as-is. Every new intent type must
  be classified by this rule.* Single enforcement point: `IntentMatchMapper.toEntity`. Domain model
  is unaware of the policy.
- **Retention (Fork 3):** row-count cap enforced in `*RepositoryImpl` on every write (WorkManager
  still frozen to Ph6/9). Pruning direction: usage/intent-match → oldest by timestamp; ranking →
  lowest-scored.
- **Optional intent-match write:** `HandleUserCommandUseCase` accepts `intentMatchHistory` as a
  nullable default parameter; `null` in tests that don't care; real impl wired in DI.

**Verification:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (277 tasks).
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL (17 new + all prior tests green).
- `grep -rn "import android\|androidx.room" domain/src/` → empty. `:domain:dependencies`
  compileClasspath = `kotlin-stdlib` + `kotlinx-coroutines-core` only.
- `feature/*` has no edge into `:data:*` (grep guard).
- Redaction test confirmed: SEARCH "search cats" → stored as "search"; UNKNOWN free text → "unknown".
- Column privacy guard: all 12 column names pass the denylist (voice/query/search/… all absent).

**Frozen, untouched:** permission-education (G), hardening (H), `architecture.md` sync (H6),
secrets (Ph5), WorkManager (Ph6/9), cloud AI, ONNX, voice.

**Follow-up note (2026-06-22) — flag-gating remediation and androidTest baseline:**

*Feature-flag gate (remediation):* Both behavioral-history writes were found to be unconditional
at Block F close — neither `LauncherViewModel.recordUsage` nor `HandleUserCommandUseCase.recordMatch`
checked `FeatureFlags.usageHistoryEnabled` before writing. Fixed in a follow-up pass before
moving to Block G:
- **Single flag, both paths:** `usageHistoryEnabled` gates both usage-history (VM) and
  intent-match-history (use case). No second flag introduced.
- **`HandleUserCommandUseCase`:** added `featureFlagRepository: FeatureFlagRepository? = null`
  (nullable, follows existing `intentMatchHistory` pattern — zero breakage for callers without
  a flag repo). Inside the soft-wrapped `recordMatch`, calls `featureFlagRepository?.getFlags()
  ?.first()?.usageHistoryEnabled`; returns early (skip write) if false or if no repo provided.
  Flag check is inside the existing try-catch — any flag-read failure silently skips the record.
- **`LauncherViewModel`:** injected `FeatureFlagRepository`; `recordUsage` calls
  `featureFlagRepository.getFlags().first().usageHistoryEnabled` inside the existing try-catch;
  returns early if false.
- **DI:** `IntentProvidesModule.provideHandleUserCommandUseCase` now receives and wires
  `FeatureFlagRepository`.
- **Latency:** both checks are inside soft-wrapped side-effect paths, not on the critical
  outcome path. `getFlags().first()` reads from DataStore's in-memory cache after startup
  (fast, no disk I/O on warm reads). The `CommandOutcome` return type and all 12 branches
  are unchanged.
- **Tests added:** 2 flag-gate tests in `HandleUserCommandUseCaseTest` (flag=false → no record,
  flag=true → records, outcome identical either way) + 2 in `LauncherViewModelTest`
  (flag=false → no `recordedLaunches`, flag=true → records). VM tests default `fakeFlagRepo`
  to `usageHistoryEnabled=true` so all prior recording tests remain valid.
- Total: `HandleUserCommandUseCaseTest` 25 tests (0 failures); `LauncherViewModelTest` 24 tests
  (0 failures). `assembleDebug` + all JVM unit tests green. Domain = stdlib+coroutines only.

*Follow-up fix 2026-06-23 — Fix 1 (fire-and-forget) + Fix 2 (CancellationException):*
Two correctness issues in the Block F flag-gating code were fixed. **Fix 1:** `recordMatch` was
called inline (`await`) inside `handle()`, so every command waited on a DataStore read + DB write
before returning `CommandOutcome`. Fixed by adding `recordingScope: CoroutineScope` to
`HandleUserCommandUseCase` (default `CoroutineScope(SupervisorJob())`; DI provides
`@ApplicationScope @Singleton CoroutineScope(SupervisorJob() + @IoDispatcher)` via a new provider
in `DispatcherModule`; the `@ApplicationScope` qualifier lives in `core/common/di`). The call in
`handle()` is now `recordingScope.launch { recordMatch(...) }` — the `CommandOutcome` is returned
before the record completes. **Fix 2:** `LauncherViewModel.recordUsage` had `catch (_: Throwable)`
wrapping `featureFlagRepository.getFlags().first()`, swallowing `CancellationException` from the
`getFlags` suspension. Fixed with `catch (e: CancellationException) { throw e }` before the
catch-all (import added). `HandleUserCommandUseCase.recordMatch` already had this guard from the
prior pass. **Tests:** recording tests in `HandleUserCommandUseCaseTest` use
`CoroutineScope(Dispatchers.Unconfined + SupervisorJob())` as the `recordingScope` — the unconfined
dispatcher runs the launched coroutine in-place through the fake (no real suspension points), so
assertions follow `handle()` immediately with no scheduler advancement. One new test added:
`flag-read throws non-cancellation — outcome unaffected, record skipped` (use case); one new test
added: `recordUsage swallows non-cancellation error from getFlags` (ViewModel). Total: 67 domain
JVM tests, 25 launcher JVM tests, 0 failures. `assembleDebug` green.

*androidTest migration baseline:* `MigrationTest` executed on SM-A325F (Android 13, 2026-06-22).
`./gradlew :data:repository:connectedDebugAndroidTest` → **2 tests, BUILD SUCCESSFUL**. The v1
schema runway is proven on a real device. Note: test method names use camelCase (spaces in
backtick method names are rejected by pre-DEX-040 android toolchain).

### ADR 2026-06-23 — Block G complete (permission-education module + request flow)

**Done 2026-06-23 (Block G, G1–G7). Third Phase-4 execution round. Fork 5 honoured.**

**Pre-flight (Block F carry-over) — both verified before starting, no fix needed:**
- `recordMatch`'s `getFlags().first()` is off the critical path: it runs inside
  `recordingScope.launch { recordMatch(...) }` (`HandleUserCommandUseCase.handle`), so the
  `CommandOutcome` returns without waiting on the DataStore read.
- `CancellationException` re-thrown before the catch-all in both
  `HandleUserCommandUseCase.recordMatch` and `LauncherViewModel.recordUsage`.

**New module:** `:feature:permission_education` (Compose + Hilt; deps `core:common`, `core:ui`,
`domain` only — no `feature→feature`, no `feature→data`). Registered in `settings.gradle.kts`;
`:app` depends on it.

**New files in `:domain` (`com.sidr.launcher.domain.permission`):**
- `PermissionFeature` — enum `WALLPAPER(requestable=true)` + dormant `VOICE_INPUT`,
  `CALENDAR_SUGGESTIONS`, `LOCATION_SUGGESTIONS` (`requestable=false`). **No accessibility entry.**
  Pure: carries no manifest permission strings.
- `PermissionStatus` — `GRANTED / DENIED / PERMANENTLY_DENIED`.
- `PermissionChecker` — port; `status(feature): PermissionStatus`.
- `PermissionPrefsRepository` — port; `isDismissed(feature): Flow<Boolean>` /
  `setDismissed(feature, dismissed): OperationResult<Unit>`.

**New file in `:core:android` (`…core.android.permission`):**
- `AndroidPermissionChecker` — plain class (no Hilt annotations) over
  `ContextCompat.checkSelfPermission`; the single place mapping `PermissionFeature` → manifest
  permission string. Returns GRANTED/DENIED only. `core/android/build.gradle.kts` gained
  `implementation(project(":domain"))`.

**New files in `:data:repository` (`…data.repository.preferences`):**
- `PermissionPrefsRepositoryImpl` over the shared `DataStore<Preferences>` + `@IoDispatcher`;
  reads fall back on `IOException`, writes catch `IOException` → `OperationError`, never throw.
- `PreferencesKeys`: added `PERM_DISMISSED_WALLPAPER = "perm_dismissed_wallpaper"` (+ in
  `ALL_KEY_NAMES`); only the requestable feature has a key.

**New files in `:feature:permission_education`:**
- `PermissionEducationViewModel` (`@HiltViewModel`) — single `StateFlow<PermissionEducationUiState>`;
  reads status via the port, persists dismissed via the repo, refines DENIED→PERMANENTLY_DENIED
  from the request callback. No Android types.
- `PermissionEducationScreen` — rationale always shown (no dialog); system dialog launched only on
  CTA for a requestable feature via `ActivityResultContracts.RequestPermission`; on grant launches
  the wallpaper picker (`ACTION_SET_WALLPAPER`); permanently-denied → "Open settings"; "Don't show
  again" → `onDismissForever`; "Back" via the navhost safe-fallback. Android glue (activity
  unwrap, intents) is UI-layer only.
- `PermissionRationale` + `rationaleFor(feature)` — display copy per feature (incl. dormant);
  no accessibility copy.

**Changes in `:app`:**
- `AppNavHost` — `Routes.PermissionEducation.ROUTE` now renders `PermissionEducationScreen`
  (placeholder removed); `onBack` reuses `handleNavigationEvent(..., NavigateBack)` (3.1.5).
- `PermissionModule` (object) — `@Provides PermissionChecker = AndroidPermissionChecker(context)`.
- `PersistenceBindsModule` — `@Binds PermissionPrefsRepository ← PermissionPrefsRepositoryImpl`.
- `AndroidManifest.xml` — `<uses-permission android:name="android.permission.SET_WALLPAPER" />`.

**Change in `:feature:launcher`:** `LauncherScreen` adds a "Wallpaper" `TextButton` →
`viewModel.navigateTo(Routes.PermissionEducation.ROUTE)`. The user-initiated trigger; the route
string stays in the UI layer, the VM only forwards the `NavigationEvent`. No new VM logic.

**New fakes in `:core:testing`:** `FakePermissionChecker` (per-feature settable status),
`FakePermissionPrefsRepository` (`MutableStateFlow`-backed, `errorToReturn`, recorded `setCalls`).

**Decisions made during execution (Fork 5 left these open):**
- **`PermissionChecker` port lives in `:domain`, impl in `core/android`.** A feature module cannot
  depend on `core/android` (allowed deps: `domain`/`core:ui`/`core:common`), so the contract had to
  be a domain port for the VM to use it; the Android impl is provided from `:app`.
- **`AndroidPermissionChecker` is Hilt-annotation-free**, constructed in `:app`'s `PermissionModule`,
  so `core/android` stays DI-framework-free (it has no Hilt dependency).
- **Per-feature `PermissionPrefsRepository`, not the legacy global flag.** Block E pre-provisioned a
  single `FeatureFlags.permissionEducationDismissed`; a global boolean conflates features and
  contradicts "a denial disables exactly one feature". The new repo is per-feature. The legacy
  global flag/key is left in place (untouched, still tested by Block E) but is no longer the source
  of truth — flagged for a possible Block H cleanup.
- **Only requestable features get a DataStore key.** Dormant features have no request flow, so
  nothing to dismiss; `isDismissed` emits `false` and `setDismissed` is a no-op `Success`. This also
  avoids the privacy-guard denylist collisions that keys like `perm_dismissed_voice_input` /
  `…_calendar…` / `…_location…` would trigger. Concrete key today: `perm_dismissed_wallpaper` (clean).
- **SET_WALLPAPER is a *normal* permission → no real OS dialog.** On a manifest-declared build the
  checker reports GRANTED and the request contract returns granted without UI. The live demo
  therefore exercises education → request-contract → feature reaction (wallpaper picker); the
  denial / permanently-denied mechanics are real code paths covered by VM unit tests with the fake
  checker (they can't be reproduced on-device for a normal permission). Documented as the intended
  Fork-5 behaviour, not a gap.
- **Single live feature wired into the VM (`WALLPAPER`).** A nav-arg/`SavedStateHandle` route into
  the VM is deferred to when a second feature has a live request flow (noted for Block H/Ph7).

**Tests (G7) — all JVM, no new instrumented tests:**
- `PermissionEducationViewModelTest` (`:feature:permission_education`) — **8 tests**: initial status
  from checker + requestable; granted→GRANTED; denied+ask-again→DENIED (feature off, still
  requestable); denied+no-ask→PERMANENTLY_DENIED; `onDismissForever` persists per-feature + marks
  state; previously-dismissed reflected on init; dismiss swallows a persistence error;
  `refreshStatus` re-reads checker.
- `PermissionPrefsRepositoryImplTest` (`:data:repository`) — **4 tests**: default not-dismissed;
  set→read round-trip; survives simulated process restart (scope-cancel + reopen on same tmp file);
  dormant feature is no-op Success + never bleeds into the wallpaper flag.
- `PrivacyInventoryGuardTest` re-run green with the new key in `ALL_KEY_NAMES`.
- Total new G JVM tests: **12**. No regressions across the suite.
- **No `androidTest` produced for Block G** (all paths covered by JVM/fakes) → no device run required.

**Verification (actual output):**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL in 35s (312 tasks; new module + Hilt graph compile).
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL in 1m 1s (221 tasks executed;
  12 new tests green, prior tests green).
- `./gradlew :feature:permission_education:testDebugUnitTest :data:repository:testDebugUnitTest`
  → BUILD SUCCESSFUL (8 + 4 reported in test-results XML, 0 failures/0 errors).
- Guards: `grep -rn "import android" domain/src/` empty; `grep -rn "androidx\." domain/src/` empty;
  `:domain:dependencies` compileClasspath = `kotlin-stdlib` + `kotlinx-coroutines-core` only; no
  `feature→data` / `feature→feature` edges; `BIND_ACCESSIBILITY_SERVICE` appears only in
  "intentionally absent" comments (never requested, never educated).

**Frozen, untouched:** hardening (H) incl. `UiState`/retry/`SavedStateHandle`/`architecture.md`
sync (H6); accessibility + its consent (Ph8); request flow for `RECORD_AUDIO`/`READ_CALENDAR`/
`ACCESS_FINE_LOCATION` (dormant, education-only); secrets (Ph5); WorkManager (Ph6/9).

**Carry-forward tasks for Block H (decided 2026-06-23, do not act until the owning H step):**
1. **Privacy guard — table names.** Strengthen the guard to also scan Room **table names**, not just
   column names (deferred from Block F).
2. **Wallpaper trigger relocation.** The home-screen `TextButton("Wallpaper")` in `LauncherScreen` is
   a **temporary demo hook, not final UI** (confirmed by product). Block H: remove the home-screen
   button and relocate the wallpaper entry to launcher settings / a home long-press menu. Leave it in
   place until the H step that owns navigation/UI.
3. **`refreshStatus()` permanent-denied downgrade — real bug, not just a missing test.**
   `PermissionEducationViewModel.refreshStatus()` currently overwrites status unconditionally with
   `permissionChecker.status(feature)`, which can only return GRANTED/DENIED — so it silently
   downgrades a `PERMANENTLY_DENIED` status to `DENIED` on re-check (e.g. returning from system
   Settings). Block H: change `refreshStatus()` so it may **only upgrade to GRANTED** and never
   overwrites a `PERMANENTLY_DENIED` with `DENIED`; add a test asserting `PERMANENTLY_DENIED` survives
   a refresh. **ADR note:** `PERMANENTLY_DENIED` is only derivable from the request callback
   (`shouldShowRequestPermissionRationale`), never from `checkSelfPermission`; this upgrade-only guard
   is a **partial** fix adequate for the current SET_WALLPAPER (normal-permission) scope and **must be
   revisited when the first dangerous permission lands (`RECORD_AUDIO`, Ph7)**.
4. **Legacy global dismissed flag.** Consider removing the now-superseded global
   `flag_permission_edu_dismissed` key/field (replaced by the per-feature `PermissionPrefsRepository`).

### ADR 2026-06-23 — Block H complete (hardening + docs-sync; Phase 4 closed)

**Done 2026-06-23 (Block H, steps H-a, H-b, H-c, H1–H6). Final Phase-4 execution round. Fork 6 + Fork 9 honoured.**

**Per-step summary:**
- **H-a — Privacy guard strengthened to table names.** `RoomColumnNamesGuardTest` now scans Room
  **table names** as well as column names against the forbidden-term denylist (was column-only at
  Block F). `RoomColumnNames` carries a hand-listed `TABLE_NAMES` set checked against the `@Entity`
  table names. (`RoomColumnNames.kt` + `RoomColumnNamesGuardTest.kt`, commit `b67b5a8`.)
- **H-b — `refreshStatus()` made upgrade-only.** `PermissionEducationViewModel.refreshStatus()`
  previously overwrote status unconditionally with `permissionChecker.status(feature)` (which can
  only return GRANTED/DENIED), silently downgrading `PERMANENTLY_DENIED → DENIED` on re-check.
  Now a re-check may only move **up to GRANTED** and never overwrites an existing
  `PERMANENTLY_DENIED`. Test added asserting `PERMANENTLY_DENIED` survives a refresh.
- **H-c — Error-mapping audit.** `OperationError → UiError → UiState.Error(retryable)` wired with the
  category retryability from `architecture.md` (Network/Unknown → retryable; AiUnavailable/
  PermissionDenied/DeviceNotCapable → not button-retryable).
- **H1 — VM single-source-of-truth audit.** `LauncherViewModel` confirmed to expose orthogonal
  flows (one source per concern), correct `Empty` handling, no business logic in composables.
  `PermissionEducationViewModel` keeps its plain data-class state by design (see decisions below).
- **H2 — Recoverable errors.** `UiState.Error` gained `retryable: Boolean = false`; the retry
  **action** is deliberately *not* carried in the data class (no lambda → equality /
  `distinctUntilChanged` stay intact). `LauncherViewModel.retry()` re-triggers the app load, cancels
  any in-flight load (latest-wins), and routes `null` → `Loading` so a retry is visible. (`UiState.kt`,
  `LauncherViewModel.kt`, commit `b67b5a8`.)
- **H3 — Process-death restoration.** `commandInput` backed by `SavedStateHandle`
  (`KEY_COMMAND_INPUT`) so the typed text survives process death; `commandFeedback` stays ephemeral
  (transient last-command result, intentionally not restored).
- **H4 — Navigation + temporary-button removal.** The temporary home-screen "Wallpaper" demo button
  was removed from `LauncherScreen` (replaced by a `NOTE` comment); the `permission_education`
  destination stays routed with the 3.1.5 safe-fallback. (commit `7156ab1`.)
- **H5 — On-device acceptance.** Real kill→reopen on device: PID changed, `commandInput` restored
  from `SavedStateHandle`; the navigation safe-fallback (unavailable destination → Launcher home)
  was exercised via a temporary trigger and observed in logcat — **no crash**. (No user-reachable
  bad route exists in Ph4, so the fallback was driven by a temporary trigger only.)
- **H6 — Docs-sync + Phase-4 close (this step).** `docs/architecture.md` brought to the real
  Phase-4 state (Fork 9); this ADR written; Block H checked off in `phase-4-plan.md`; `CLAUDE.md`
  advanced to "Phase 4 complete (E–H)". No production code / schema / contract / UX change in H6 —
  the doc was stale, not the code.

**`architecture.md` items reconciled in H6 (was-vs-now):**
- **`LauncherUiState`** — doc showed a single composite `LauncherUiState { apps, suggestions,
  inputState, aiState }`. Reality: `LauncherViewModel` exposes **three orthogonal flows** —
  `uiState: StateFlow<UiState<LauncherUiState>>` (grid; `LauncherUiState` is just `(apps)`),
  `commandInput: StateFlow<String>` (SavedStateHandle-backed), `commandFeedback:
  StateFlow<CommandFeedback>` (ephemeral) — plus a `navigationEvents` `Channel`. Documented as
  single-source-per-concern, **not** a violation. `suggestions` (Ph7) and `aiState` (Ph5) are
  **unbuilt**; `LauncherUiState` grows a field/flow when those phases land.
- **`UiState.Error`** — documented the new `retryable: Boolean = false` (H2) and the
  property-of-state-in-context semantics (decided by the VM, not by `UiError`).
- **Permissions** — documented the per-feature education model (Block G) + the upgrade-only
  `refreshStatus()` behavior (H-b) and the `PERMANENTLY_DENIED`-derivability caveat.
- DataStore / Room shapes were already current in the doc from the Block E/F passes; no further drift.

**Decisions (with rationale + phase-tie) — the 7 carry-forward notes folded in:**
1. **`TABLE_NAMES` hand-sync limit (H-a).** Table names are **hand-listed** in `RoomColumnNames`
   against `@Entity`; the guard test is a denylist scan, not an automatic reflection of the schema.
   The golden-schema `androidTest` (`MigrationTestHelper`) is the structural backstop. **Rule:**
   any future phase that adds a table must update `TABLE_NAMES`. (Tie: Block F privacy inventory /
   Fork 3.)
2. **`refreshStatus()` upgrade-only is a *partial* fix (H-b).** It never downgrades
   `PERMANENTLY_DENIED → DENIED`, but `PERMANENTLY_DENIED` is derivable **only** from the request
   callback (`shouldShowRequestPermissionRationale`), never from `checkSelfPermission`. Adequate for
   the current `SET_WALLPAPER` (normal-permission) scope; **must be revisited at Ph7 with the first
   dangerous permission (`RECORD_AUDIO`).**
3. **`architecture.md` `LauncherUiState` stale → fixed in H6.** The aspirational composite was
   replaced with the real 3-flow design; `suggestions`/`aiState` are unbuilt (Ph7/Ph5) and the state
   grows when they land. (Tie: Fork 9.)
4. **`PermissionEducation` plain-state exception — deliberate, don't "correct" it.**
   `PermissionEducationViewModel` uses a plain data-class `PermissionEducationUiState`, **not**
   `UiState<T>`, because it has no async load and no empty/error surface to model. This is an
   intentional exception to the "every VM exposes `UiState<T>`" pattern, not an oversight.
5. **Cache-restore half of Fork 6 deferred to Ph7.** `SuggestionsCacheRepository` (Block E) exists
   but has **no display surface** in Ph4 — wiring its content-restore now would repaint nothing.
   **Reconciliation rule for Ph7:** `SavedStateHandle` owns transient input/route; the DataStore
   cache owns content first-paint; a fresh load **supersedes** the cached repaint, never merged.
   (Tie: Fork 6 process-death restoration, content half.)
6. **Wallpaper button removed (H4).** The temporary home-screen demo button is gone (product
   decision). The `permission_education` destination stays routed but has **no on-screen entry**
   until `feature/settings` (the launcher-settings UI phase) builds a real entry point.
7. **Navigation safe-fallback latent in Ph4 (H5).** `handleNavigationEvent`'s unavailable-destination
   fallback to Launcher home is verified (on-device via a temporary trigger, since no user-reachable
   bad route exists in Ph4). It stays **latent** until deep-links / new routes arrive.

(Also noted at Block G, task 4: the legacy global `flag_permission_edu_dismissed` key is superseded
by the per-feature `PermissionPrefsRepository`. Left in place — harmless, still Block-E-tested — as
an optional future cleanup; not actioned in Block H to avoid a key removal with no functional gain.)

**H5 on-device acceptance result:** real kill→reopen on device — **PID changed and `commandInput`
restored**; navigation fallback observed in logcat; **no crash**.

**Verification (H6 final wrap-up — actual output pasted in the H6 report):**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew testDebugUnitTest --rerun-tasks` → green.
- `grep -rn "import android" domain/src/` → empty; `:domain:dependencies` compileClasspath =
  `kotlin-stdlib` + `kotlinx-coroutines-core` only.
- `grep -rn "data.repository" feature/launcher/src/` → empty; no `feature→feature` / `feature→data`
  edges (feature modules depend only on `core:common`/`core:ui`/`domain` + `testImplementation
  core:testing`).

**Phase 4 closed (Blocks E → H, 2026-06-23).** Next per roadmap = **Phase 5 (cloud AI)**.

**Frozen, untouched (Phase 5+):** secrets / `SecureSecretStore` (Ph5), cloud AI (Ph5), ONNX (Ph6),
voice + `SpeechInputSource` + context-suggestion pipeline (Ph7), accessibility + its consent (Ph8),
WorkManager (Ph6/9), full Hilt→KSP migration (Ph9), `feature/settings` module, request flow for
`RECORD_AUDIO`/`READ_CALENDAR`/`ACCESS_FINE_LOCATION` (dormant, education-only).

### ADR 2026-06-24 — Block I complete (multi-provider AI domain contracts)

**Done 2026-06-24 (Block I, I1–I5). First Phase-5 execution round. Forks P5-2/3/5/6 honoured.**

**Pre-flight (repo-truth check) — all confirmed, no plan patch needed:**
- AI contracts absent in `:domain` (only the KDoc reference in `IntentMatcher.kt`).
- `OperationResult`/`OperationError` at `com.sidr.launcher.domain.result` (port returns depend on it).
- `:domain` compile classpath = `kotlin-stdlib` + `kotlinx-coroutines-core` only.
- `:core:testing` is a **JVM** module (no Android variants) — the prompt's
  `:core:testing:compileDebugSources` verification command doesn't exist; used `:core:testing:classes`
  (covered transitively by `assembleDebug` anyway). Noted for future Phase-5 prompts.

**New files in `:domain` (`com.sidr.launcher.domain.ai`):**
- `AiProviderId` / `AiModelId` — `@JvmInline value class` over `String`, **opaque** (no vendor enum).
- `AiRequest` / `AiMessage` / `AiRole(USER, ASSISTANT)` — content-only; `system` top-level; `model`
  nullable (= engine/router default); `stopSequences`; **no sampling params** (`temperature`/`top_p`/
  `top_k` are a per-adapter concern — some models 400 on them).
- `AiChunk` (sealed: `Text(delta)` / `Completed(stopReason, usage?)` / `Failed(error)`) +
  `AiStopReason(COMPLETE, MAX_TOKENS, STOP_SEQUENCE, REFUSAL, OTHER)` + `AiUsage`.
- `AiError` (sealed: `Offline`, `MissingCredentials`, `Unauthorized`, `RateLimited(retryAfterMs?)`,
  `Timeout`, `Network(detail?)`, `ServerError(statusCode?)`, `InvalidRequest(detail?)`, `Unknown(detail?)`).
- `GenerativeAiEngine` (`fun generate(request): Flow<AiChunk>`) + `GenerativeRouter : GenerativeAiEngine`.
- `AiChunks.assembleText(Iterable<AiChunk>): String` (pure helper).

**New files in `:domain` (`…domain.security`):** `SecretKey` value class; `SecureSecretStore`
(`suspend get/put/remove → OperationResult`, **never throws**); `SecretKeys.apiKey(provider) =
SecretKey("ai_api_key_${provider.value}")` (per-provider).

**New file in `:domain` (`…domain.connectivity`):** `ConnectivityChecker` (`isOnline()` +
`connectivity: Flow<Boolean>`).

**New fakes in `:core:testing`:** `FakeGenerativeAiEngine` (scripted `List<AiChunk>` and/or a
per-request `script` lambda; records `lastRequest`; optional `delayBetweenChunksMs`),
`FakeSecureSecretStore` (`MutableMap`-backed, `errorToReturn`, `put`/`remove` call logs),
`FakeConnectivityChecker` (`MutableStateFlow`-backed, settable `online`).

**Decisions made during execution (multi-provider properties — the point of the block):**
- **Opaque `AiProviderId`/`AiModelId`, not enums.** A new provider is addable later as a new
  `GenerativeAiEngine` impl + provider id + key entry, with **zero `:domain` change**.
- **No sampling params in the domain request.** Sampling is model-specific (HTTP 400 risk) and lives
  only inside the adapter (Block K) — keeps `:domain` wire-agnostic.
- **Refusal is a *success* terminal (`AiStopReason.REFUSAL` inside `AiChunk.Completed`), not an
  `AiError`.** A reached-but-declined model is not a transport failure.
- **Terminal-failure-as-value.** Expected failures are emitted as a terminal `AiChunk.Failed(AiError)`
  and the flow then completes normally — implementations must NOT throw expected errors to the
  collector (the `Flow` analog of "`OperationResult`, never throw to UI"). KDoc'd on the port + fakes.
- **`AiError` carries no secret/raw-content fields** — only safe diagnostic hints (`detail`,
  `statusCode`, `retryAfterMs`). KDoc records the intended later UI retryability mapping (Offline/
  Network/Timeout/RateLimited/ServerError/Unknown → retryable; MissingCredentials/Unauthorized → not
  button-retryable; InvalidRequest → not retryable) without building it (Block N).
- **Generic per-provider `SecureSecretStore`.** Not AI-specific; keyed so providers never collide.
- **Vendor-neutrality guard made *literally* clean.** The acceptance grep `anthropic|openai|gemini|
  claude` over `domain/src/` initially matched **KDoc examples + test-data strings only** (no
  production type/field/logic named a vendor — those references were illustrating opacity). To make
  the guard pass literally (a clearer contract than "is this comment a violation?"), vendor tokens
  were scrubbed to generic placeholders (`cloud-default`/`cloud-compatible`/`provider-c`/`…`) in KDoc
  and tests; the vendor↔stop-reason mapping detail rightly belongs in the Block K adapter, not `:domain`.
- **`SecretKeys` privacy guard uses the *content* subset of the Phase-4 denylist.** The full denylist
  includes `api`/`secret`/`token`, which would false-match a credential slot name like `ai_api_key_*`.
  Those terms are **inherent** to a secret-store keyspace (the encrypted value lives in Keystore, not a
  plaintext store), so `SecretKeysTest` guards only the user-**content** terms (voice/query/search/
  location/calendar/history/conversation/message/transcript) — still catches a content term sneaking in
  via a provider id (e.g. `voicebot`). Documented in the test KDoc.

**Tests (I5) — `:domain` JVM, no Android:**
- `AiChunksTest` (4): delta concatenation in order; ignores `Completed`/`Failed`; empty cases;
  refusal-as-stop-reason.
- `SecretKeysTest` (3): per-provider stability; per-provider distinctness; content-denylist guard.
- `FakeGenerativeAiEngineTest` (3): scripted collect + `lastRequest` record; `Failed` delivered as a
  value (collector doesn't throw); per-request `script` lambda.
- Total: 10 new JVM tests, 0 failures. No regressions.

**Verification (actual output):**
- `./gradlew :domain:dependencies --configuration compileClasspath` → `kotlin-stdlib` +
  `kotlinx-coroutines-core` only.
- `grep -rn "import android" domain/src/` → empty (exit 1).
- `grep -rn "androidx\.\|io\.ktor\|kotlinx\.serialization" domain/src/` → empty (exit 1).
- `grep -rn "anthropic\|openai\|gemini\|claude" -i domain/src/` → empty (exit 1) after the scrub.
- `./gradlew :domain:test :core:testing:classes assembleDebug` → BUILD SUCCESSFUL; the three new test
  suites reported `tests="4|3|3" failures="0" errors="0"`.
- `IntentMatcher` / `HandleUserCommandUseCase` / all Phase-3 intent code untouched (two-port invariant
  holds); no new Gradle deps / catalog entries; no DI wiring; `:feature:assistant` untouched.

**Frozen, untouched (later Phase-5 blocks):** `SecureSecretStore` Keystore impl (J); Ktor
OpenAI-compatible adapter + SSE (K); `PromptContextBuilder` + outbound guard (L); `GenerativeRouter`
impl + static fallback + `GenerateReplyUseCase` + `ConnectivityChecker` Android impl (M); assistant
streaming UI + provider-settings form (N); native Anthropic adapter (optional post-N fast-follow).

**Block I addendum (2026-06-24) — provider-config contract; plan re-oriented to OpenAI-compatible-first.**
Additive only — **existing Block I files unmodified**. Added the pure provider-config contract the
"the user pastes any API + picks any model" goal needs: `AiProviderConfig` (`providerId`, `baseUrl`,
`modelId`, `displayName?`) + `AiProviderConfigRepository` (`activeConfig(): Flow<AiProviderConfig?>` /
`setActiveConfig`/`clearActiveConfig` → `OperationResult`, never throws) in `…domain.ai`; single
active config for Phase 5; the API key is **not** here (stays in `SecureSecretStore`). New fake
`FakeAiProviderConfigRepository` in `:core:testing` + a round-trip JVM test (`AiProviderConfigRepositoryTest`:
default `null`; `setActiveConfig` → `activeConfig` emits + recorded; `clearActiveConfig` → `null`).
Same purity as Block I — no vendor / "openai-compatible" string literal in `:domain`; the
`anthropic|openai|gemini|claude` grep guard over `domain/src/` stays empty; no new deps; DataStore
impl deferred to **Block K**. `phase-5-plan.md` re-oriented: **OpenAI-compatible adapter = Block K
(first/primary), free-text model (no hardcoded model/catalog), provider-config + key-in-Keystore in
scope, network-security-config moved J→K**; native Anthropic = optional fast-follow after Block N.
Status unchanged: **Block I (+ addendum) done; next = Block J.**

### ADR 2026-06-24 — Block J complete (Keystore-backed SecureSecretStore, BYOK)

**Done 2026-06-24 (Block J, J1–J5). Second Phase-5 execution round. Forks P5-1/7/8/9 honoured.**
First real on-device secret; un-defers Fork 1.

**New files in `:data:repository` (`com.sidr.launcher.data.repository.security`):**
- `SecretCipher` (interface) + `EncryptedBlob` (`iv` + `ciphertext`, content-based equality) — the
  **single crypto seam** (Fork 7). `encrypt` may throw (→ store maps to `Failure`); `decrypt` returns
  `null` for an *unusable* secret (key invalidated / missing / corrupt blob), never throws on those.
  Data-layer detail, **not** a domain type. Public (so `:app` Hilt `@Binds` can see it — `internal`
  would be invisible across the Gradle module boundary).
- `KeystoreSecretCipher @Inject constructor()` — the only Keystore-touching class. **AES-256-GCM** key
  in `AndroidKeyStore` (alias `sidr_secret_aead_v1`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`,
  256-bit, `setUserAuthenticationRequired(false)`); fresh random 12-byte GCM IV per encryption,
  `GCMParameterSpec(128, iv)` on decrypt. **StrongBox attempted, falls back** on
  `StrongBoxUnavailableException` (common on API 28). `KeyPermanentlyInvalidatedException` /
  `GeneralSecurityException` (AEAD bad tag, bad IV) → `decrypt` returns `null`.
- `SecureSecretStoreImpl @Inject constructor(@SecretsDataStore DataStore<Preferences>, SecretCipher,
  @IoDispatcher CoroutineDispatcher)` — orchestrates cipher + the dedicated store. Each `SecretKey`
  maps to one entry holding the Base64 `iv:ciphertext` blob. `get` decrypt-fail / invalidation /
  corrupt / unreadable → `Success(null)` **and clears the entry** (re-enter); `put`/`remove` encrypt or
  I/O failure → `Failure(UnknownError)`; **never throws**, `CancellationException` re-thrown.
- `@SecretsDataStore` qualifier (`@Retention(BINARY)`) marking the dedicated `sidr_secrets` store.

**New files in `:app/di`:** `SecretsProvidesModule` (object — `@Provides @Singleton @SecretsDataStore
DataStore<Preferences>` over file **`sidr_secrets`**, own `CoroutineScope(io + SupervisorJob)`) +
`SecretsBindsModule` (abstract — `@Binds SecureSecretStore ← SecureSecretStoreImpl`, `@Binds
SecretCipher ← KeystoreSecretCipher`). Split per the recurring Hilt `@Provides`/`@Binds` rule (Blocks
D/E).

**Decisions made during execution:**
- **Dedicated `sidr_secrets` DataStore (the key cross-block decision, Fork 8/privacy).** Encrypted
  blobs live in a **separate** file, qualified with `@SecretsDataStore`, **not** Block E's
  `sidr_preferences`. Rationale: the credential-named keys (`ai_api_key_<provider>`, which contain the
  denylist terms `api`/`key`) never enter `PreferencesKeys.ALL_KEY_NAMES`, so the Phase-4
  `PrivacyInventoryGuardTest` stays green by construction, and the secrets file holds only
  Keystore-ciphertext. **`ALL_KEY_NAMES` was NOT extended; the Block E DataStore instance is NOT
  reused.**
- **`java.util.Base64`, not `android.util.Base64`** (API 26+, minSdk 28). Works on-device **and** in
  pure-JVM unit tests, so `SecureSecretStoreImplTest` needs **no Robolectric** — it runs the real
  `SecureSecretStoreImpl` over a temp-file DataStore (the Block E `createTestDataStore` helper) with a
  `FakeSecretCipher`.
- **`FakeSecretCipher` lives in `:data:repository` test sources, NOT `:core:testing` (deviation from the
  J4 prompt bullet).** `:core:testing` is a JVM-only module depending only on `:domain`; it **cannot**
  depend on the Android `:data:repository` where the data-internal `SecretCipher` type lives. Placing
  the fake in `:data:repository/src/test` is the only correct option given the module graph and keeps
  the cipher seam out of `:domain` (Fork 7 intent preserved).
- **`setUserAuthenticationRequired(false)` + threat model.** A launcher assistant can't prompt for
  lockscreen/biometric on every secret read. The key is extraction-resistant in Keystore but
  app-readable without user auth — correct for the BYOK "protect the user's *own* key on a non-rooted
  device" model (it does **not** defend a rooted/compromised device), and it sidesteps most
  invalidation paths. A backend-proxy impl can later replace the whole port with no domain/UI change.
- **`get` is resilient, never `Failure`.** Read I/O failure follows the codebase
  `.catch{IOException→emptyPreferences()}` convention (→ `Success(null)`); decrypt/corrupt →
  `Success(null)` + clear. Only `put`/`remove` (writes) surface `Failure`. Upstream interprets `null`
  as "no usable secret, re-enter".

**Tests (J4):**
- `:data:repository` JVM (`SecureSecretStoreImplTest`, **9 tests, pure JVM, no Robolectric**):
  put→get round-trip; empty→null; two-provider isolation; `remove` isolation; simulated process
  restart (scope-cancel + reopen same temp file); key-invalidation → `null` **+ entry cleared**
  (re-`get` with a healthy cipher still `null`); corrupt-blob → `null` no crash; encrypt-fail → `put`
  `Failure`; `put` re-throws `CancellationException`. Uses `FakeSecretCipher`.
- `:data:repository` `androidTest` (`SecretStoreInstrumentedTest`, 3 tests) against the **real**
  `KeystoreSecretCipher`: put → restart → get returns value; two-provider isolation + `remove`;
  StrongBox-fallback path round-trips without crash. **Compiles; requires a connected device/emulator
  to run** (`./gradlew :data:repository:connectedDebugAndroidTest`, like the Phase-4 `MigrationTest`) —
  **not executed in this environment (no device attached); to be run on SM-A325F.**

**Verification (actual output):**
- `./gradlew :data:repository:testDebugUnitTest --tests "...security.*"` → `SecureSecretStoreImplTest`
  `tests="9" failures="0" errors="0"`.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (full Hilt graph with both new modules + qualified
  second DataStore).
- `./gradlew :data:repository:compileDebugAndroidTestSources` → BUILD SUCCESSFUL (instrumented test
  compiles).
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL (full JVM regression, no
  regressions; `PrivacyInventoryGuardTest` `tests="2" failures="0"`).
- `grep -rni "EncryptedSharedPreferences|security-crypto"` over `*.kt`/`*.kts`/`*.toml` → empty (ESP
  forbidden).
- `grep -rn "import android" domain/src/` → empty; `:domain:dependencies` compileClasspath =
  `kotlin-stdlib` + `kotlinx-coroutines-core` only. `:domain` and `feature/*` untouched by Block J; no
  `feature → data` edge.
- `grep -rniE "Log\.|println"` over the security package → empty (no key/ciphertext ever logged).
- Secrets keys absent from `PreferencesKeys` / `ALL_KEY_NAMES` (dedicated `sidr_secrets` store).

**Frozen, untouched (later Phase-5 blocks):** OpenAI-compatible Ktor engine + SSE + `AiProviderConfig`
DataStore impl + network-security-config (K); `PromptContextBuilder` + outbound guard (L);
`GenerativeRouter` impl + static fallback + `GenerateReplyUseCase` + `ConnectivityChecker` Android impl
(M); assistant streaming UI + provider-settings form (N); native Anthropic adapter (optional post-N
fast-follow). **Next = Block K.**

### ADR 2026-06-24 — Block K complete (OpenAI-compatible cloud engine: SSE → Flow<AiChunk>)

**Done 2026-06-24 (Block K, K0–K6). Third Phase-5 execution round. Fork P5-2 honoured.** First and
primary generative adapter: streams from any OpenAI-compatible `chat/completions` endpoint
(OpenRouter / Google's OpenAI endpoint / Together / Groq / local Ollama / LM Studio / OpenAI itself).
Logic was fully MockEngine-tested against the Block-I ports; Block J's real key impl is not gated on it.

**New files in `:data:ai-cloud` (`com.sidr.launcher.data.aicloud`):**
- `OpenAiCompatibleGenerativeAiEngine` (provider-neutral name — it's the OpenAI-*compatible* adapter,
  not "OpenAI") implementing the **unchanged** `GenerativeAiEngine` port. Reads base URL + free-text
  model from `AiProviderConfigRepository`, key from `SecureSecretStore`; constructor also takes the
  injected `HttpClient`, `@IoDispatcher`, and (defaulted) first-token + idle timeout constants. Private
  top-level `@Serializable` wire DTOs (`ChatCompletionRequest`/`ChatMessageDto`/`StreamChunk`/
  `StreamChoice`/`DeltaDto`/`UsageDto`) — never domain types. `companion.DEFAULT_LIGHT_MODEL` is a
  Block-N UI hint only; the engine never falls back to it (model is always the user's config value).

**New files in `:data:repository` (`com.sidr.launcher.data.repository.ai`):**
- `AiProviderConfigRepositoryImpl` — the Block-I-addendum contract's DataStore impl, over the **shared
  `sidr_preferences`** store (NOT `:data:ai-cloud`, which is the Hilt-free HTTP client with no DataStore
  dep — the plan's Block-K file list was loose here; corrected). `activeConfig()` emits `null` until the
  three required keys (id, base URL, model) are all present; reads fall back on `IOException`, writes
  return `OperationResult` and never throw. 4 keys added to `PreferencesKeys` + `ALL_KEY_NAMES`.

**New files in `:app/di`:** `AiCloudProvidesModule` (object — `@Provides @Singleton HttpClient` over
the Ktor **Android** engine + a `@CloudEngine`-qualified `@Provides` constructing the engine) +
`CloudEngine` qualifier. `AiProviderConfigRepositoryImpl` bound in the existing `PersistenceBindsModule`.

**Decisions made during execution:**
- **Wire shape:** `POST {baseUrl}/chat/completions`, `Authorization: Bearer <key>`,
  `Accept: text/event-stream`, streaming SSE. **Input sanitization at the boundary:** base URL, key,
  and model are `.trim()`-ed (a trailing newline in a pasted key causes a baffling 401), trimmed values
  still kept out of logs.
- **Robust URL join:** `baseUrl.removeSuffix("/") + "/chat/completions"` — preserves a user's `.../v1`
  segment (never *replaces* the path, the `URLBuilder.path(...)` trap).
- **Minimal request body, no sampling:** only `model`/`messages`/`max_tokens`/`stream:true` (+ `stop`
  when non-empty). `temperature`/`top_p`/`top_k` are **absent from the DTO** so they can't be serialized
  (widest backend compatibility). `Json { encodeDefaults=true; explicitNulls=false }` keeps `stream:true`
  and drops `stop` when null. `system` (when non-blank) becomes the **leading** `role:"system"` message;
  USER/ASSISTANT → `"user"`/`"assistant"`. `AiRequest.model` overrides the config model when present.
- **No hardcoded model / no fixed model list:** the model is the user's free-text `AiModelId`, sent
  verbatim. `< 2000ms` first-token is guidance, not a pin.
- **Manual SSE parse over `response.bodyAsChannel()` + `readUTF8Line()`** (no `bodyAsText()`, no
  `ktor-client-sse` dep). Per `data:` line: `[DONE]`/EOF ends the stream; emit `AiChunk.Text(delta)`
  only when content is non-empty (role-only first delta emits nothing); blank/`event:`/`id:`/`:` lines
  ignored; a single unparseable line is skipped (not a teardown). `finish_reason`/final `usage` are
  captured **even off a content-empty terminal delta** (we don't `continue` past it).
- **Stop-reason mapping:** `stop→COMPLETE`, `length→MAX_TOKENS`, `content_filter` (or a `delta.refusal`)
  `→REFUSAL` (**success terminal**, never an `AiError`), else `OTHER`. **Refusal is sticky** — a refusal
  seen on an earlier chunk wins over a later `finish_reason:"stop"` (tracked via a `refused` flag, fixed
  after a test caught the overwrite). End of stream → exactly one terminal `Completed(stopReason, usage?)`.
- **Error → `AiError` taxonomy, each a terminal `AiChunk.Failed`:** no config or no/`Failure` key →
  `MissingCredentials` (socket never opened); `401/403→Unauthorized`; `429→RateLimited(retryAfterMs)`
  (parses delta-seconds **and** HTTP-date, `null` on a bad header, never throws); `5xx→ServerError(code)`;
  other `4xx→InvalidRequest`; `IOException→Network`; `UnknownHost`/`ConnectException→Offline`;
  per-read deadline→`Timeout`; else `Unknown`. `AiError.detail` is a safe token only
  (`http_<code>`, exception `simpleName`, `base_url_not_https`) — never a URL/body/key.
- **HTTPS-only:** a non-`https://` base URL is rejected (`InvalidRequest`, no raw URL in `detail`, socket
  never opened); app-level `network_security_config.xml` (`cleartextTrafficPermitted="false"`,
  referenced from the manifest) forbids cleartext egress.
- **Streaming timeout model:** first-token + idle-between-chunks deadlines via `withTimeoutOrNull` around
  **each read** (timeout vs clean-EOF distinguished by the `withTimeoutOrNull` result, not by a null
  line) — **no Ktor `requestTimeoutMillis`** (it would abort a long but legitimate stream); the provided
  `HttpClient` sets connect/socket timeouts only.
- **Cancellation propagates, never swallowed:** the cold `flow {}` runs `preparePost(...).execute { … }`
  and emits inside it (no nested `withContext`/`launch`), `.flowOn(ioDispatcher)`. The mapping
  `try/catch` re-throws `CancellationException` first and catches narrow types (`UnknownHostException`/
  `ConnectException`/`IOException`) before a final guarded `Exception`. Collection-cancel cancels the
  flow coroutine → aborts the in-flight Ktor request.
- **DI / cold path:** `:data:ai-cloud` stays **Hilt-free** (the engine is a plain class `@Provides`-
  constructed in `:app`; this required adding `ktor-client-core`/`-android` to `:app` — the composition
  root — so the `HttpClient` `@Provides` can name the type). The engine is `@CloudEngine`-qualified now
  so Block M's `GenerativeRouter` can take the unqualified slot; nothing injects it yet, so no AI/HTTP is
  built on the launcher cold path.

**Tests / verification (actual output):**
- `./gradlew :data:ai-cloud:testDebugUnitTest` → **20 tests, 0 failures** (Ktor MockEngine): happy
  stream (ordered `Text` + single `Completed(COMPLETE)`, `finish_reason` captured off the content-empty
  delta, `assembleText` reconstructs); usage population; request assertion (base URL + `Bearer`,
  body has `model`/`messages`/`max_tokens`/`stream:true` and **no** `temperature`/`top_p`/`top_k`,
  leading `system`, verbatim free-text model); `content_filter`→`REFUSAL`, `delta.refusal`→`REFUSAL`,
  `length`→`MAX_TOKENS`; `401/403→Unauthorized`, `429`+`Retry-After`→`RateLimited(2000)`, 429-no-header
  →`null`, `5xx→ServerError`, `4xx→InvalidRequest`, `IOException→Network` (detail = `"IOException"`,
  no URL), `UnknownHost→Offline`; missing-key / store-`Failure` / null-config → `MissingCredentials`
  with **no socket opened**; non-`https://`→`InvalidRequest` (no raw URL); first-token deadline→`Timeout`
  (real dispatcher, suspending channel); cancellation aborts collection with **no terminal emitted**
  (segmented/suspending `ByteChannel`).
- *Test-harness note:* the parsing/error tests run the engine on a **real** `Dispatchers.IO` (not
  `UnconfinedTestDispatcher`) — under `runTest` virtual time the auto-advancing clock spuriously fires
  the real-channel-read `withTimeoutOrNull`; a real dispatcher makes the deadline real-time and
  deterministic. Timeout/cancellation tests use `runBlocking` + real suspension.
- `./gradlew :data:repository:testDebugUnitTest` → green incl. `AiProviderConfigRepositoryImplTest`
  (null-until-configured; set→read round-trip; no-display-name; clear→null; survives simulated restart)
  and `PrivacyInventoryGuardTest` (2 tests) green with the 4 new `ai_provider_*` keys (denylist-clean —
  none contains `api`).
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (full Hilt graph + `network_security_config`).
- `./gradlew testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL (full JVM regression, no regressions).
- `grep -rni "anthropic|openai|gemini|claude" domain/src/` → empty; `grep -rn "import android|io.ktor|
  kotlinx.serialization" domain/src/` → empty; `:domain:dependencies` compileClasspath = stdlib +
  coroutines only. `grep -rniE "Log\.|println" data/ai-cloud/src/main/` → empty (no key/prompt/body
  logged). Catalog gained **only** `ktor-client-mock` (test-only); no new runtime dep, no
  `ktor-client-sse`. `:data:ai-cloud` has no Hilt, no `:core:android` edge.

**Frozen, untouched (later Phase-5 blocks):** `PromptContextBuilder` + outbound allow-list guard (L);
`StaticFallbackEngine` + `GenerativeRouter` impl + `ConnectivityChecker` Android impl +
`GenerateReplyUseCase` (M); assistant streaming UI + provider-settings form (N); native Anthropic
adapter (optional post-N fast-follow). `IntentMatcher` / `HandleUserCommandUseCase` / `feature/*`
untouched. **Next = Block M** (router + static fallback; L may run earlier/parallel as pure JVM).

**Addendum 2026-06-27 (Block K hardening — timeout realism, no behaviour change).** Recorded the final
streaming-deadline values and made them robust for the first *real* network round-trip (every K test is
MockEngine; the first live call is Block N). **`DEFAULT_FIRST_TOKEN_TIMEOUT_MS` and
`DEFAULT_IDLE_TIMEOUT_MS` raised `15_000` → `20_000`** — these are *transport* deadlines, **explicitly
decoupled in KDoc from the `< 2000ms` first-token perf figure**, which is Block-N UI guidance about
choosing a light/fast model (Fork P5-2 "guidance, not a pin"), not a network limit; 20 s absorbs
OpenRouter routing/queueing, free-tier latency, and local Ollama / LM Studio cold starts (a ~2 s deadline
would have spuriously `Timeout`-ed the first real reply). **`HttpClient` audit (`AiCloudProvidesModule`):
unchanged values, `connectTimeoutMillis=15_000`, `socketTimeoutMillis=30_000`, `requestTimeoutMillis`
absent — confirmed correct against the Ktor 3.0.1 `HttpTimeout` contract (context7-verified): Ktor's
`socketTimeoutMillis` bounds the *inactivity between two data packets* (the same quantity the manual idle
deadline bounds), so it must stay `>= DEFAULT_IDLE_TIMEOUT_MS` (30 s > 20 s → pure backstop, the engine's
manual `withTimeoutOrNull` owns the semantics); `requestTimeoutMillis` bounds the *whole call* and would
abort a long but legitimate stream, so it stays unset.** Added one MockEngine regression test (first token
arriving just under the deadline → normal `Text…/Completed(COMPLETE)`, guarding against a future over-tight
default; the existing "just over → `Timeout`" test stays). **Unchanged:** SSE parsing, `AiError` taxonomy,
request body (still no sampling params), refusal stickiness, cancellation, DI structure; no new dep. Verified:
`:data:ai-cloud:testDebugUnitTest` 21 tests green; `assembleDebug` + `testDebugUnitTest --rerun-tasks` green.

### ADR 2026-06-27 — Block L complete (prompt/context builder + outbound privacy guards, pure)

**Done 2026-06-27 (Block L, L1–L4). Fourth Phase-5 execution round (pure JVM, parallelizable with
K/M). Fork P5-3 honoured.** The outbound side of generation: a pure `PromptContextBuilder` assembling
the **minimal** `AiRequest` (the Block-I contract) that may leave the device, plus two reflection-free
guard tests that make the privacy posture **fail-closed**. Everything lands in `:domain` (stdlib +
coroutines only); no new deps; `IntentMatcher`/`HandleUserCommandUseCase`/`feature/*`/K-M-N artifacts
untouched. `GenerativeAiEngine` is **not** invoked here.

**New files in `:domain` (`com.sidr.launcher.domain.ai`):**
- `PromptContextBuilder` — pure class, public surface is `build(userCommand)` **only** (no
  context-bag overload — adding context must be a deliberate edit, not an open door). Produces a
  minimal `AiRequest`: **one `USER` message = the command verbatim**, the static `DEFAULT_SYSTEM_PROMPT`
  (a short, context-free, vendor-neutral instruction — no model pinned), `maxOutputTokens = 512`
  (phone-reasonable guidance, not a hard pin), `model = null` (engine/router uses its configured
  default). Nothing else is assembled — no device/usage/calendar/location/history/contacts/clipboard.
- `OutboundContextPolicy` — the explicit **positive allow-list** + denylists, hand-synced inventories
  (Phase-4 `ALL_KEY_NAMES`/`TABLE_NAMES` precedent): `AllowedContext{USER_COMMAND, STATIC_SYSTEM_PROMPT,
  GENERATION_LIMITS}` + `ALLOWED`; `FORBIDDEN_CONTEXT_TERMS`; `CREDENTIAL_TERMS`; `OUTBOUND_FIELD_NAMES`
  (AiRequest's 5 fields); `AIERROR_FIELD_NAMES` (AiError's 3 safe-diagnostic fields).

**New files in `:domain/src/test`:** `AiRequestGuardTest` (5 tests), `OutboundSecretLeakGuardTest`
(6 tests) — **11 new, reflection-free, pure JVM.**

**Decisions made during execution:**
- **Positive allow-list, fail-closed (Fork P5-3):** only the three vetted categories may leave; a
  future context source that isn't allow-listed cannot reach an `AiRequest`. The builder's tiny
  surface enforces this structurally (no context bag), and `AiRequestGuardTest` pins `ALLOWED` to
  exactly those three so an un-reviewed addition fails the build.
- **Denylist scanned over non-user-controlled text ONLY** — the static system prompt + the
  field/category-name inventories — **never over the user's typed command**. The user command is
  legitimate free text and may contain any word; a regression test (`build("open my calendar")` keeps
  "calendar") guards against an over-eager filter that would corrupt legitimate commands.
- **`token` deliberately EXCLUDED from `CREDENTIAL_TERMS`** (documented in the policy + the test):
  the legitimate outbound field `maxOutputTokens` contains the substring `token`, so a substring scan
  with `token` present would **vacuously** fail on a non-credential field — exactly the Phase-4
  `key`-excluded precedent (a term dropped because it collides with a legitimate identifier). The
  retained family (`secret`/`apikey`/`api_key`/`bearer`/`authorization`/`credential`/`password`) still
  fires if a real credential-named field is ever added. Other Phase-4 terms also dropped to avoid
  vacuous matches: `query`/`search` (the request legitimately concerns the user's query), `message`
  (collides with `AiMessage`/`messages`), `system` (a legitimate field + category name).
- **Credential terms scanned over field-name INVENTORIES, not rendered `toString()`** — a refinement
  beyond the prompt's literal "scan `AiError.toString()`", flagged during Opus review: `AiError`'s safe
  diagnostic variant name **`MissingCredentials` legitimately contains "credential"**, so scanning the
  rendered variant string for credential terms would vacuously fail (the same collision class as
  `maxOutputTokens`/"token"; the prompt only checked the `authorization`⊄`Unauthorized` non-collision
  and missed this one). Resolution: credential terms are scanned over the hand-synced
  `OUTBOUND_FIELD_NAMES` / `AIERROR_FIELD_NAMES` (the meaningful "no field can carry a key" check,
  mirroring Phase-4 `TABLE_NAMES`), while the rendered `toString()` of every `AiRequest`/`AiError`
  variant is scanned only for a **planted secret sentinel** that was never inserted — proving the
  surface has no place to hold it. This keeps "credential" useful (no field collision) without a
  vacuous failure.
- **Hand-synced inventory rule:** `OUTBOUND_FIELD_NAMES` mirrors `AiRequest`'s real fields and
  `AIERROR_FIELD_NAMES` mirrors `AiError`'s; a test asserts each set equals the expected names, so
  **adding a field to `AiRequest`/`AiError` forces an inventory update** (and re-runs the credential
  scan). Reflection-free throughout (no `kotlin-reflect`), per the `:domain` purity invariant.
- **No-injection proof:** `build("<<SENTINEL_CMD>>")` yields exactly one `USER` message with verbatim
  content, `system == DEFAULT_SYSTEM_PROMPT`, `model == null`, empty `stopSequences` — no
  ASSISTANT/system-as-message smuggling, no hidden context.

**Tests / verification (actual output):**
- `./gradlew :domain:test --rerun-tasks` → **BUILD SUCCESSFUL**; `AiRequestGuardTest` 5 + 
  `OutboundSecretLeakGuardTest` 6 = 11 new, 0 failures; **91 domain tests total**, 0 failures.
- `./gradlew :domain:dependencies --configuration compileClasspath` → stdlib + kotlinx-coroutines
  **only** (no `kotlin-reflect`, no Ktor, no serialization, no Android).
- `grep -rn "import android|io.ktor|kotlinx.serialization|kotlin.reflect" domain/src/` → empty.
- `grep -rni "anthropic|openai|gemini|claude" domain/src/` → empty (vendor-neutral domain intact;
  the bonus prompt-names-no-vendor assertion was removed because its literals would have broken this
  guard).
- `grep -rn "GenerateReplyUseCase|Router|GenerativeAiEngine" …/ai/PromptContextBuilder.kt` → empty
  (no K/M/N artifact referenced).
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (nothing else perturbed).

**Frozen, untouched (later Phase-5 blocks):** assistant streaming UI + provider-settings form (N);
native Anthropic adapter (optional post-N fast-follow). Multi-turn session history, if ever needed,
is a deliberate future allow-list addition (transient, never persisted — decided in N).
**Next = Block M** (router + static fallback + `GenerateReplyUseCase`, consumes K + L) → **now done; next = Block N.**

---

### ADR Block M (2026-06-27) — Routing seam + static fallback + GenerateReplyUseCase

**Scope:** Block M — `StaticFallbackEngine` + `DefaultGenerativeRouter` + `AndroidConnectivityChecker`
+ `GenerateReplyUseCase` + DI wiring + manifest permission. Depends on I, K, L (all green).
`HandleUserCommandUseCase` and the intent/matching pipeline are **untouched**. Matching ≠ generation.

**Module placement — `:data:repository`:**
`StaticFallbackEngine` and `DefaultGenerativeRouter` live in `…data.repository.ai` (same `ai` sub-package
as `AiProviderConfigRepositoryImpl`). Rationale: both are Android-free pure-collaborator classes with no
Ktor dep; `:data:repository` is the "pure implementation" module (`RuleBasedIntentMatcher` precedent).
`:data:ai-cloud` stays strictly the Ktor cloud adapter. No data→data edge: the router takes
`GenerativeAiEngine` constructor params (the port), never imports a concrete engine type. `:app` supplies
the qualified instances positionally via `GenerationProvidesModule`.

**Qualifier placement — `@FallbackEngine` co-located with `@CloudEngine` in `:app`:**
`@CloudEngine` was already defined in `:app/di/CloudEngine.kt` (Block K). `@FallbackEngine` is added
right next to it in `:app/di/FallbackEngine.kt`. Neither annotation appears in `:data:repository`;
`DefaultGenerativeRouter` constructor params are plain `GenerativeAiEngine` types, with `:app` supplying
the correct instances positionally.

**Router design — ordered selection, latest-wins, never throws:**
`DefaultGenerativeRouter.generate(request)` is a cold `flow { emitAll(selectEngine().generate(request)) }`.
Selection runs inside the cold flow (not at construction), so connectivity/key/config changes between
calls are respected (latest-wins). `selectEngine()` order:
1. **Ph6 ONNX slot** — reserved ahead of cloud via a comment; inserting a local ONNX engine later
   requires adding one check ahead of cloud with no edit to the cloud/static branches.
2. **Cloud** — iff `isOnline()` AND `activeConfig().firstOrNull()` is non-null AND
   `SecureSecretStore.get(SecretKeys.apiKey(providerId))` returns a non-blank `Success` value.
3. **Static fallback** — always eligible.

`firstOrNull()` used (not `first()`) for `activeConfig()` — `first()` throws on an empty flow;
`firstOrNull()` is belt-and-suspenders against a future non-emitting config impl. A `Failure` from the
secret store is treated as "no usable key → static". Neither `selectEngine()` nor `canUseCloud()`
throws expected errors — terminal-failure-as-value is preserved end to end.

**Double-read (eligibility gate + engine re-read at request time) is intentional:** the router reads
config to get `providerId` for the key eligibility check; the cloud engine re-reads config at request
time for the actual base URL/model. This double read is the correct design: the eligibility gate must
happen before the engine is selected, and the engine must read fresh config when the request is built.

**`StaticFallbackEngine`:** emits `AiChunk.Text(STATIC_REPLY)` + `AiChunk.Completed(COMPLETE)`.
Context-free (does not echo `request` content). Never emits `AiChunk.Failed` — fallback is always a
graceful success terminal. `STATIC_REPLY` is a constant: "I can't reach an AI service right now —
check your connection or set up a provider in settings."

**`AndroidConnectivityChecker` — Hilt-free, not unit-tested in `core/android`:**
Plain class in `core/android/connectivity/`; constructed in `:app`'s `ConnectivityModule`
(`@ApplicationContext Context`), mirroring `AndroidPermissionChecker` / `PermissionModule`. Uses
`ConnectivityManager.activeNetwork` + `getNetworkCapabilities(…).hasCapability(NET_CAPABILITY_INTERNET
+ NET_CAPABILITY_VALIDATED)` for `isOnline()`; `callbackFlow` over `registerDefaultNetworkCallback`
(current state on subscription, then `onAvailable`/`onLost`/`onCapabilitiesChanged`; `awaitClose`
unregisters) + `conflate()` + `distinctUntilChanged()` for `connectivity: Flow<Boolean>`. No test
deps in `core/android`; router logic fully covered via `FakeConnectivityChecker`.
`core/android/build.gradle.kts` gains `implementation(libs.coroutines.core)` (for `callbackFlow`).

**DI — single unqualified `GenerativeAiEngine` binding:**
`GenerationProvidesModule` in `:app`:
- `@FallbackEngine GenerativeAiEngine` → `StaticFallbackEngine()`
- `GenerativeRouter` → `DefaultGenerativeRouter(cloud=@CloudEngine, fallback=@FallbackEngine, …)`
- unqualified `GenerativeAiEngine` → the `GenerativeRouter` (the only unqualified binding; no Hilt
  ambiguity because `@CloudEngine` and `@FallbackEngine` are qualified; `GenerateReplyUseCase`
  receives the router without a qualifier)
- `PromptContextBuilder` → `PromptContextBuilder()` (plain default)
- `GenerateReplyUseCase(engine=unqualified, promptContextBuilder)` (domain use case)
`ConnectivityModule` provides `ConnectivityChecker → AndroidConnectivityChecker(context)`.
Manifest: `ACCESS_NETWORK_STATE` added (normal install-time permission; no runtime dialog).
Nothing on the launcher cold path — all bindings are lazy singletons.

**`GenerateReplyUseCase` (pure `:domain`):**
`fun generate(userCommand: String): Flow<AiChunk> = engine.generate(promptContextBuilder.build(userCommand))`.
Depends only on `GenerativeAiEngine` (port) + `PromptContextBuilder` (both `:domain`). The router type
is invisible here. No Android, no Ktor.

**Tests / verification (actual output):**
- `./gradlew :data:repository:testDebugUnitTest` → **BUILD SUCCESSFUL** (`DefaultGenerativeRouterTest`
  7 cases: offline→static, online+key→cloud, online+no-key→static, online+no-config→static,
  key-read-failure→static, latest-wins re-evaluation, cancellation; static-fallback terminal shape).
- `./gradlew :domain:test` → **BUILD SUCCESSFUL** (`GenerateReplyUseCaseTest` 6 cases: streams
  chunks, builder output reaches engine, one USER message verbatim, static system prompt, null model,
  denylist-term in user command passes through unmodified; **96 domain tests total**, 0 failures).
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (Hilt graph validates; one unqualified
  `GenerativeAiEngine` binding; no duplicate-binding errors).
- `./gradlew testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL** (full regression green; intent
  pipeline untouched).
- `grep -rn "import android|io.ktor" domain/src/` → **empty** (domain purity intact).
- `./gradlew :domain:dependencies --configuration compileClasspath` → `kotlin-stdlib` +
  `kotlinx-coroutines-core` **only** (no Ktor, no Android, no serialization).
- `grep -rni "OpenAiCompatible|CloudGenerativeAiEngine|aicloud|data.aicloud" data/repository/src/main/` →
  **empty** (no data→data edge; router references port only).
- `ls data/repository/src/main/…/ai/` → `AiProviderConfigRepositoryImpl.kt` +
  `DefaultGenerativeRouter.kt` + `StaticFallbackEngine.kt` (correct location confirmed).
- `git diff --stat -- domain/src/…/HandleUserCommandUseCase.kt` → **no output** (untouched).

**Next = Block N** (assistant streaming UI + provider-settings form + phase close).

### ADR Block N (2026-06-27) — Assistant streaming UI + provider-settings form + Phase 5 close

**Scope:** `:feature:assistant` — real streaming screen, inline provider-settings form, navhost
wiring, JVM tests. Phase 5 (Blocks I → N) closed.

**`AssistantViewModel` design decisions:**

- **VM-collected streaming (Fork P5-5 resolution).** The `Flow<AiChunk>` is collected inside
  `viewModelScope.launch {}`, not in the UI. This resolves the Fork-P5-5 tension between
  "UI-collected" and "retry()-latest-wins" and "config-change":
  - Rotation does **not** restart the stream — the VM and its `StateFlow` outlive config-change.
  - Screen-leave **aborts** the stream — `onCleared` cancels `viewModelScope`, which cancels the cold
    Ktor flow (tears down the HTTP request).
  - `retry()` = latest-wins: `streamJob?.cancel()` then relaunch in `viewModelScope`.
  - Unit-testable: tests drive `viewModelScope` via `UnconfinedTestDispatcher` / `runTest`.

- **No `SavedStateHandle` (deliberate deviation from H3).** `LauncherViewModel` uses
  `SavedStateHandle` to restore `commandInput` across process death (H3). The assistant does NOT:
  `lastPrompt` is a transient plain field, not persisted — there is no "in-progress reply" that
  should survive process death. More critically, **the API key must never touch `SavedStateHandle`**
  (it would be serialized to the saved-state Bundle, which can be logged by the OS). Annotated in
  code and KDoc so this deviation is not confused for an oversight.

- **Plain-state data class (not `UiState<T>`).** `AssistantUiState` is a plain `data class` (like
  `PermissionEducationViewModel` — Block G precedent). There is no async load / empty surface that
  warrants `UiState.Loading` / `UiState.Empty` — the assistant surface is always ready; only the
  streaming status changes. `status: AssistantStatus { Idle / Streaming / Done(refused) / Error(…) }`
  expresses the "separate status" of Fork P5-5 as a field in the single `StateFlow`.

- **Refusal = success terminal.** `AiChunk.Completed(REFUSAL)` → `AssistantStatus.Done(refused=true)`.
  Rendered as a normal (declined) reply with a subtle note. Not an error, not retryable.

- **`AiError → UiError` mapper is feature-local.** Per ADR Block B, `UiError` lives in `core:common`
  with **no domain dep by design**. Placing the `AiError → UiError` mapper in `core:common` would add
  a forbidden `core/common → domain` edge. The mapper lives next to `AssistantViewModel` in
  `:feature:assistant`, which legitimately sees both `domain` and `core:common`.

- **`MissingCredentials` / `Unauthorized` → CTA, not retry button.** `AssistantStatus.Error` gains a
  `showProviderCta: Boolean` flag. The fix for a credential error is updating provider settings, not
  repeating the same request. The CTA opens the inline form; `retryable = false` suppresses the Retry
  button for these cases.

**Provider-settings form + `saveProvider` decisions:**

- **`providerId` normalization rule (pinned):** derived from the base-URL host component only —
  `URI(trimmedUrl).host.lowercase()`, port and userinfo/path stripped. Example:
  `https://OpenRouter.ai/api/v1` → `AiProviderId("openrouter.ai")`. This rule agrees with how Block K
  parses the base URL (the eligibility gate and the request target never disagree). For Phase 5
  (single active config), two providers sharing a host collapse to one key slot — acceptable and
  documented here; not over-engineered.

- **`https://` validation is defense-in-depth.** Block K already rejects non-`https://` base URLs at
  the request level; the form adds a second check so the error surfaces inline (before a request is
  ever attempted), not after the first stream attempt.

- **Key is never displayed / logged / in state.** The key field (`PasswordVisualTransformation`) goes
  straight to `SecureSecretStore.put()` and is dropped from memory. `ProviderFormState` holds only a
  `keySet: Boolean` (recomputed on every `activeConfig()` emit so switching providers never shows a
  stale "key set ✓" from a previous slot). `AssistantUiState.toString()` cannot contain the key value.

**NavHost wiring (N4):** follows the launcher pattern — `hiltViewModel()` + `LaunchedEffect` on
`navigationEvents` + `handleNavigationEvent(navController, it)` (3.1.5 safe-fallback already present).

**Tests (14 JVM, `:feature:assistant`):** stream accumulation; Done on COMPLETE; Done(refused=true)
on REFUSAL; Error(retryable=false, showProviderCta=true) for Unauthorized/MissingCredentials;
Error(retryable=true) for Network; Error(retryable=false, showProviderCta=false) for InvalidRequest;
retry() re-sends same prompt; retry() with no prior send is safe; saveProvider persists config+key;
blank key skips put; non-https → inline error + nothing persisted; providerId normalized from host;
null config → blank baseUrl (form shown prominently); config update reflected in form state.

**Verification (actual output):**
- `./gradlew :feature:assistant:testDebugUnitTest` → **BUILD SUCCESSFUL** (14 tests, 0 failures).
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (Hilt graph valid; real assistant destination).
- `./gradlew testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL** (262 total JVM tests, 0 failures).
- `grep -rn "^import com.sidr.launcher.feature\." feature/assistant/src/main/` → **empty** (no feature→feature).
- `grep -rn "^import.*SavedStateHandle" feature/assistant/src/main/` → **empty** (not imported).
- `grep -rn "^import android" domain/src/` → **empty** (domain untouched).
- `git diff --stat -- domain/src/…/HandleUserCommandUseCase.kt` → **no output** (untouched).

**On-device acceptance (N5):** code + JVM green. On-device run **pending** on SM-A325F (no device
available in execution environment). Items pending device: (1) Block-J `SecretStoreInstrumentedTest`
(real Keystore round-trip + per-provider isolation + invalidation path, compiles), (2) Block-N N5
(streaming reply, airplane-mode → static fallback, cancel/retry, key entry, rotation mid-stream,
navigate-back cancellation). Both recorded as pending, not assumed passed.

---

### Phase 5 close summary (Blocks I → N, 2026-06-24 – 2026-06-27)

| Block | Deliverable | Status |
|---|---|---|
| I | AI domain contracts (pure) + addendum (AiProviderConfig/Repo) | Done |
| J | Keystore-backed `SecureSecretStore` (BYOK, AES-256-GCM) | Done (device run pending) |
| K | `OpenAiCompatibleGenerativeAiEngine` (SSE → `Flow<AiChunk>`) + `AiProviderConfigRepositoryImpl` | Done |
| L | `PromptContextBuilder` + `OutboundContextPolicy` (positive allow-list, privacy guards) | Done |
| M | `DefaultGenerativeRouter` + `StaticFallbackEngine` + `GenerateReplyUseCase` + `AndroidConnectivityChecker` | Done |
| N | `AssistantViewModel` + `AssistantScreen` + provider-settings form + navhost wiring + docs-sync | Done (device run pending) |

**What was built:** vendor-neutral, streaming, provider-configurable generative pipeline — BYOK model
(user pastes any OpenAI-compatible base URL + key + free-text model); cloud → static fallback; privacy
guards (positive allow-list, no key/raw-prompt in logs); streaming UI that survives rotation and
aborts on back-navigation. `:domain` stays pure Kotlin (stdlib + coroutines); no feature→data edges.

**What is pending a device run:** Block-J `SecretStoreInstrumentedTest` (real Keystore; compiles) +
Block-N N5 on-device acceptance (streaming, offline, cancel/retry, rotation) — run on SM-A325F before
considering Phase 5 fully closed for on-device scenarios.

**What is frozen to later phases:** ONNX (Ph6), voice (Ph7), native Anthropic adapter (optional
post-N fast-follow), backend-proxy secret impl (swaps in behind `SecureSecretStore`),
`:feature:settings` (provider form relocates there).

### ADR 2026-06-27 — Phase 6 (Local NLU + embeddings) forks decided, plan agreed, NO block started
- **Status:** planning round only — no production Kotlin, no `build.gradle.kts`/catalog edits this round.
  Deliverable = `ai-context/phase-6-local-nlu-plan.md` (Blocks **O → R**, continuing the alphabet).
  First execution round gated to **Block O only**. Mirrors the Phase-5 "forks-before-code" discipline.
- **Pre-flight repo-truth deltas found (codegraph + grep, not docs):**
  - `:data:ai-local` is an **empty scaffold that already wires `onnxruntime-android` 1.20.0** (no `.kt`
    sources, no `:core:android` edge) — ONNX is **not** merely "planned"; Block P only adds source.
  - **`DeviceProfile`/`DeviceCapability` detection does NOT exist** — only `architecture.md`'s snippet +
    the flattened `DeviceProfileCacheEntry` cache DTO; `core/android` has only Permission/Connectivity
    checkers. The `DeviceProfileCacheEntry` KDoc's "*detector lives in :core:android*" is aspirational.
    **Phase 6 builds `DeviceProfile`/`DeviceCapability` + detector from scratch** (model `:domain`,
    detector `:core:android`). Gating hangs entirely on this.
  - WorkManager / `hilt-work` are **absent** from the catalog → the only genuinely new deps.
  - `MatcherSource.NLU` is the reserved second source; `IntentMatcher` (≠ `GenerativeAiEngine`) is the
    port a local NLU classifier implements; the `DefaultGenerativeRouter` "ONNX slot" is **comment-only**
    and belongs to the **generative** router (Phase 6 does **not** fill it).
- **Forks decided (full rationale in the plan §"Fork decisions"):**
  1. **ONNX scope** = NLU / classification / embeddings only, **not generation**; generative router slot
     stays reserved; Block R fixes the `architecture.md:169,192` "NLU/generation" wording bug.
  2. **NLU composition** = pure `LayeredIntentMatcher` (in `:data:repository`, port-only, no data→data
     edge): rule-first `< 10ms` fast path, NLU consulted only on low confidence, merged via
     `IntentConfidencePolicy`; **`HandleUserCommandUseCase` untouched** (DI binding swap only).
  3. **Model provisioning** = WorkManager download (not bundled) + internal storage + **SHA-256 verify
     against a pinned hash before any load** (quarantine→atomic rename); no unverified model loaded.
  4. **Gating** = single pure `LocalInferenceGate` over a newly-built `DeviceProfile`/`DeviceCapability`;
     `LOW_END` never loads ONNX, `MID_RANGE` conditional, `HIGH_END` enabled.
  5. **NNAPI** = **CPU is the deterministic default**; NNAPI opportunistic on **API 29+ only**,
     **off-by-default until a device run proves it faster-and-correct**; **both** init-failure and
     degraded-success handled; NNAPI **deprecated in Android 15 → migration (TFLite-in-Play-Services /
     GPU delegate) noted frozen-forward**; confined to `:data:ai-local`; ⚠ exact `ai.onnxruntime` Java
     signatures re-verified at Block P (context7's ONNX Android-Java coverage was thin — Python-skewed).
  6. **Embeddings** = `TextEmbedder` **port only**, impl deferred to Phase 7 (no consumer yet).
  7. **WorkManager** = `CoroutineWorker`+`@HiltWorker`, battery/storage-not-low constraints, **no
     `LOW_END` background**, idempotent + cancellable, no foreground service (verified via context7).
  8. **Testing** = interface-gate everything; JVM fakes in `:core:testing` (JVM-only); real ONNX only in
     `androidTest` on SM-A325F; `< 150ms` MID_RANGE is a **device-pending** acceptance item.
  9. **Lifecycle/memory** = lazy single shared session, never on cold start, closed on
     `onTrimMemory`/gate-off, per-profile heap ceilings respected.
  10. **Privacy** = on-device inference only (network solely for download); inputs never
      persisted/sent/logged; Block F redaction holds; no unverified-source load.
  11. **Deps** = ONNX already in catalog (pin 1.20.0; upstream 1.25.0 deferred); **new = WorkManager +
      `hilt-work`**; `:data:ai-local` gains a `:core:android` edge; full ONNX R8/ProGuard frozen to **Ph9**;
      `EncryptedSharedPreferences`/`security-crypto` remain forbidden.
- **Two open questions, explicitly NOT forks (gate their blocks, must be closed first):** model +
  tokenizer + label-set selection gates **Block P**; model download source / hosting gates **Block Q**.
  The **11 fork count is unchanged** — these are unresolved gating items, not decisions.
- **Tooling note:** WorkManager/Hilt-WorkManager API verified current via context7 (`/androidx/androidx`:
  `@HiltWorker`/`HiltWorkerFactory`/`Configuration.Provider`/`Constraints.Builder` with
  `setRequiresBatteryNotLow`/`setRequiresStorageNotLow`). ONNX Android-Java surface **not** well covered
  by context7 → flagged for execution-time Javadoc verification, not pinned from memory.

### ADR 2026-06-27 — Block O complete (local-AI domain contracts + DeviceProfile + gate)

**Scope:** Block O only — pure-domain contracts for local NLU + `DeviceProfile`/`DeviceCapability`
model + `LocalInferenceGate` policy. No implementations, no ONNX, no Android, no WorkManager.
Depends on nothing new; everything else compiles against the ports created here.

**Port-topology decision (supersedes the plan's O1 wording):**
The plan draft listed `IntentClassifier` as a new port in `domain.ai.local`. **This port was NOT
created.** Rationale (recorded here; plan wording is superseded, the plan file is not edited per the
Block O prompt):
- `IntentMatcher` is already the established port for `(normalizedString) → IntentMatchResult` —
  the KDoc explicitly names ONNX NLU classifiers as valid matcher sources and lists `MatcherSource.NLU`
  as the reserved second source.
- A second `IntentClassifier` of the identical shape would be a redundant parallel contract — exactly
  the two-port anti-pattern the codebase has repeatedly refused (Phase-5 `IntentMatcher` ≠
  `GenerativeAiEngine` discipline; `ActionExecutionResult` vs `CommandOutcome` discipline).
- The ONNX-specific lifecycle (`AutoCloseable`/`close()`, lazy session, `onTrimMemory`) is an
  **implementation detail** of `OnnxIntentClassifier` in `:data:ai-local` (Block P), not a domain
  contract. The domain port stays `IntentMatcher`.
- Block P's `OnnxIntentClassifier` will be `: IntentMatcher`; the gate-off no-op secondary
  (`NoOpIntentMatcher` in `:core:testing`) is also `: IntentMatcher`.
- `grep -rn "interface IntentClassifier" domain/src/` → **empty** (guard passes; confirmed below).

**New files in `:domain` (`com.sidr.launcher.domain.ai.local`):**
- `ModelId` — `@JvmInline value class ModelId(val value: String)` (opaque, mirrors `AiProviderId`).
- `ModelAvailability` — `enum class ModelAvailability { Available, Missing, Unverified }`. `Unverified`
  means file may exist but integrity is unconfirmed → treated the same as absent by the gate.
- `ModelAvailabilityRepository` — port: `fun availability(ModelId): Flow<ModelAvailability>` +
  `suspend fun markAvailable/markMissing(ModelId): OperationResult<Unit>`. Never throws. Reads
  are `Flow`; writes are `OperationResult` (codebase convention). Impl in `:data:repository` (Block Q).
- `TextEmbedder` — port only: `suspend fun embed(text: String): OperationResult<FloatArray>`.
  Impl deferred to Phase 7 (Fork P6-6 — no ranking consumer exists in Phase 6). Annotated in KDoc.

**New files in `:domain` (`com.sidr.launcher.domain.device`):**
- `DeviceProfile` — `enum class DeviceProfile { LOW_END, MID_RANGE, HIGH_END }`.
  This is the formalised model (previously only existed as a snippet in `architecture.md` and as the
  flattened boolean `DeviceProfileCacheEntry`). Block Q's `AndroidDeviceProfiler` will map to/from it.
- `DeviceCapability` — `data class DeviceCapability(ramBytes: Long, cpuCores: Int, nnapiAvailable:
  Boolean, thermalOk: Boolean, batteryOk: Boolean)`. **`online` is deliberately absent** — network
  reachability is owned by the existing `ConnectivityChecker` (Phase 5). Adding it here would be a
  second source of truth with no Phase-6 reader (the gate never consults `online`, and no other
  Phase-6 class needs it from this model). All five present fields have clear readers:
  `ramBytes`/`cpuCores` → profile classification in Block Q; `nnapiAvailable` → EP selection in
  Block P; `thermalOk`/`batteryOk` → gate re-evaluation at inference time.
- `DeviceProfileProvider` — port: `fun profile(): DeviceProfile` + `fun capability(): DeviceCapability`
  (synchronous; the detector caches its result internally). Impl in `:core:android` (Block Q).
- `LocalInferenceGate` — `object` with `fun allowsLocalNlu(profile, capability, availability): Boolean`.
  Pure stateless policy (no I/O, no Android, no state). Implementation of the pinned truth table
  (Fork P6-4 / LOW_END-vs-rest):

  | Profile    | Availability  | thermalOk | batteryOk | Result |
  |------------|--------------|-----------|-----------|--------|
  | LOW_END    | any          | any       | any       | false  |
  | MID_RANGE  | Available    | true      | true      | **true** |
  | MID_RANGE  | Available    | false or battery=false | any | false |
  | MID_RANGE  | Missing or Unverified | any | any   | false  |
  | HIGH_END   | Available    | true      | true      | **true** |
  | HIGH_END   | (other)      | (other)   | (other)   | false  |

  Fields NOT read by the gate: `nnapiAvailable`, `ramBytes`, `cpuCores` (EP selection / profiling,
  not the on/off gate). MID_RANGE and HIGH_END share the same effective gate in Phase 6 (forward-
  looking three-way split, not yet differentiated). KDoc documents the two evaluation moments
  (static DI/graph time + per-inference re-check inside `OnnxIntentClassifier`).

**New fakes in `:core:testing` (JVM-only, no Android variants added):**
- `NoOpIntentMatcher : IntentMatcher` — always returns `UnknownIntent` / confidence `0f` /
  `source = RULE_BASED`. Models the gate-off secondary in unit tests and as the production binding
  on `LOW_END`/no-model devices (Block R DI wiring). Note: `RULE_BASED` source on the no-op result
  is deliberate — a no-op "NLU" win would misreport the pipeline's decision.
- `FakeTextEmbedder : TextEmbedder` — scripted `OperationResult<FloatArray>`, `errorToReturn`,
  records `receivedTexts`.
- `FakeModelAvailabilityRepository : ModelAvailabilityRepository` — `MutableStateFlow`-backed
  per `ModelId`; `setAvailability` helper; `errorToReturn`; records `markAvailableCalls` /
  `markMissingCalls`; `reset()`.
- `FakeDeviceProfileProvider : DeviceProfileProvider` — settable `profileToReturn` /
  `capabilityToReturn`; `initialProfile = MID_RANGE`, `initialCapability` = reasonable defaults
  (4 GB RAM, 4 cores, NNAPI=false, thermalOk=true, batteryOk=true).
- **`FakeIntentMatcher` reused** for scripted NLU — no `FakeIntentClassifier` added (no parallel port).

**JVM tests added:**
- `LocalInferenceGateTest` (`:domain/src/test`, 14 test methods covering all 36 truth-table rows):
  one parameterised LOW_END loop (12 cases), 4 explicit MID_RANGE/Available cases, two loops for
  MID_RANGE/{Missing,Unverified} (4+4), mirror for HIGH_END (4+4+4), plus an extra assertion that
  `nnapiAvailable=true` does not affect the gate outcome.
- `LocalNluContractsTest` (8 test methods): `NoOpIntentMatcher` is always low-confidence + `0f` +
  never auto-executes/suggests; `FakeIntentMatcher` can return `source = NLU` (proves the NLU source
  rides the existing port with no new contract/enum); `FakeModelAvailabilityRepository` round-trips
  `markAvailable → Available`, `markMissing → Missing`, `errorToReturn → Failure + state unchanged`,
  two-model isolation.

**Verification (actual output):**
- `./gradlew :domain:dependencies --configuration compileClasspath` →
  `kotlin-stdlib` + `kotlinx-coroutines-core` **only** (purity guard passes).
- `grep -rn "import android\|androidx\|ai.onnxruntime\|androidx.work\|kotlinx.serialization" domain/src/`
  → **empty** (no ONNX / Android / WorkManager / serialization in `:domain`).
- `grep -rn "interface IntentClassifier" domain/src/` → **empty** (parallel port not created).
- `./gradlew :domain:test :core:testing:classes` → **BUILD SUCCESSFUL in 36s** (6 tasks executed);
  `LocalInferenceGateTest` `tests="14" failures="0" errors="0"`;
  `LocalNluContractsTest` `tests="8" failures="0" errors="0"`.
- `./gradlew testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL in 2m 24s** (237 tasks executed);
  **284 total JVM tests, 0 failures, 0 errors** (22 new; no regressions across intent/Phase-3/Phase-5 suites).
- `assembleDebug` — `mergeExtDexDebug` fails due to a **pre-existing Gradle transform-cache corruption**
  (confirmed by stash/pop: the failure reproduces with no Block O files present). All Kotlin compilation
  and Hilt graph validation (`app:compileDebugKotlin`, `app:hiltJavaCompileDebug`) ran green during
  `testDebugUnitTest`; the DEX-merge step is an OS-level cache issue unrelated to Block O.
- No new `build.gradle.kts` edits / catalog entries / DI wiring (Block R owns DI).
- Intent pipeline untouched: `git diff --stat -- domain/src/…/intent/ domain/src/…/HandleUserCommandUseCase.kt`
  → **no output**.

**Two open questions carried forward (unchanged from the planning ADR, still unresolved):**
1. **Model + tokenizer + label-set selection** → gates **Block P** (cannot define the tokenizer/label-map
   helper without knowing the concrete ONNX model).
2. **Model download source / hosting** → gates **Block Q** (the worker has no URL until the artifact is
   pinned).

**Next = Block P** (ONNX runtime integration in `:data:ai-local`). Block P must not start until open
question 1 is resolved. Model: **Opus 4.8** (session lifecycle, NNAPI fallback, tensor I/O, memory —
costly to get wrong; first `ai.onnxruntime` Java API use → re-verify Javadoc at execution time).

### ADR 2026-06-28 — Block P complete (ONNX runtime integration in `:data:ai-local`)

**Scope:** Block P only — the lazy/single/memory-safe `OnnxIntentClassifier` + `OnnxSessionFactory`
+ the pure tokenizer/label-map/slot layer + the P0 offline model pipeline + the device-pending
`androidTest`. Does NOT wire the DI binding swap, `LayeredIntentMatcher`, the `:app` trim-hook
registration, the `DeviceProfile` detector, `ModelStore`/SHA-256/WorkManager download, or the
`TextEmbedder` impl — those are Blocks Q/R / Phase 7.

**Pre-flight gate.** The known Gradle **transform-cache corruption**
(`/home/ali/.gradle/caches/8.10.2/transforms/*/metadata.bin` unreadable) was cleared (delete
`transforms` + `fileHashes` + project `.gradle/8.10.2`, **kill all daemons** — a stale IDLE daemon
recreated the corrupt registrations) and a **green `assembleDebug` baseline** was confirmed
(`--no-daemon`, BUILD SUCCESSFUL) before any P code. Block P then keeps `assembleDebug` green
(it adds real Android source + the `:core:android` edge).

**ONNX Java surface re-verified against the bundled AAR** (`onnxruntime-android-1.20.0.aar` →
`classes.jar`, `javap`), not memory/context7 (whose Android-Java coverage was thin): `OrtEnvironment.getEnvironment()`
(AutoCloseable singleton); `createSession(byte[], SessionOptions)`; `OrtSession.SessionOptions()` +
`addNnapi(EnumSet<ai.onnxruntime.providers.NNAPIFlags>)` (+ no-arg) + `addCPU(boolean)`;
`session.run(Map<String, OnnxTensorLike>) → OrtSession.Result` (AutoCloseable, `.get(int)`/`.get(String)`);
`OnnxTensor.createTensor(OrtEnvironment, Object)` (used for `long[][]` = int64 `[1, maxLen]`);
`OnnxValue.getValue()` (float `[1,7]` → `float[][]`). `NNAPIFlags = {USE_FP16, USE_NCHW, CPU_DISABLED, CPU_ONLY}`.

**Topology correction (matches Block O ADR):** there is **no `IntentClassifier` port**.
`OnnxIntentClassifier : IntentMatcher` returns `IntentMatchResult(source = MatcherSource.NLU)`. The
gate-off no-op is the existing `NoOpIntentMatcher` (`:core:testing`). No parallel port created.

**Model / tokenizer / 7-label set (Open Question #1, resolved 2026-06-28; amended OQ#1 (multilingual)
2026-06-29 — see "ADR — OQ#1 amended (multilingual)" below; the English `vocab.txt (30522)` / `max_len 32`
choice recorded here is SUPERSEDED by a pruned multilingual `vocab.txt` (~20–30k, en/ar/tr/ru) + `maxLen 48`):**
BERT-Mini/TinyBERT-4L-class
encoder fine-tuned for 7-class sequence classification, dynamic int8, ONNX opset ≤ 22; BERT **WordPiece
uncased `vocab.txt` (30522)** shipped beside the model. Labels in **argmax-index order**
(`NluLabel`): `0 LAUNCH_APP, 1 SEARCH, 2 OPEN_SETTINGS, 3 SHOW_APPS, 4 HELP, 5 OPEN_ASSISTANT,
6 UNKNOWN` → mapped to `LaunchAppIntent/SearchIntent/OpenSettingsIntent/SimpleCommandIntent(SHOW_APPS|
HELP|OPEN_ASSISTANT)/UnknownIntent`. **`CLEAR` is not a class** (rule-only). **Slots are heuristic**
(verb/filler strip in the pure mapper, not modeled); joint intent+slot frozen to Phase 7+.

**Pinned input/shape contract (`OnnxModelSpec`):** inputs `input_ids` + `attention_mask`
[+ `token_type_ids` **only if the model declares it** — checked via `session.inputNames`], all
**int64 `[1, maxLen]`**; sequence axis treated as **fixed `max_len = 32`** (the tokenizer always
pads/truncates to exactly `[1,32]`, valid for fixed-32 or dynamic exports) [**amended OQ#1 2026-06-29:
`maxLen → 48`**, see "ADR — OQ#1 amended (multilingual)"]; output `logits` float
`[1,7]` read **by index 0** (name-independent). The `tools/nlu/` placeholder model matches these
names/dtypes/axes exactly so swapping in the real model needs no Kotlin change.

**P2a — pure, ONNX-free, JVM-tested** (`nlu/`): `WordPieceTokenizer` (faithful HF BasicTokenizer
`do_lower_case` + WordpieceTokenizer — clean/CJK/lower/NFD-accent-strip/punct-split, greedy `##`
longest-match, `[UNK]`); `IntentLabelMapper` (softmax→argmax→label→`IntentMatchResult`);
`SlotExtractor`; `NluLabel`; `OnnxModelSpec`. **Confidence escape (mandatory):** argmax == UNKNOWN
**or** max-softmax < `confidenceFloor` (0.60) → lowest-confidence `UnknownIntent(source = NLU)` so
Block R's `LayeredIntentMatcher` falls back to rule. **NLU softmax confidence is NOT calibrated** to
the rule-confidence scale — the merge policy is an **open question carried to Block R**.

**P2b/P3/P4 — thin ONNX shell** (`OnnxIntentClassifier`): lazy session (first gated `match()` only,
never cold start); single shared session serialized by a `Mutex`; inference on `Dispatchers.Default`;
`session.run` not cooperatively cancellable (noted). **Per-inference gate** re-calls the **same**
`LocalInferenceGate.allowsLocalNlu(...)` with fresh `capability()` (Fork P6-4 moment 2) → degrade on
thermal/battery/availability. **Files resolved (`LocalModelFiles` seam, Q implements) BEFORE any
`OrtEnvironment` call**, so a missing model/vocab degrades without loading native (and is JVM-testable).
Every per-inference tensor + `OrtSession.Result` wrapped in `use{}`/`close()`. **Graceful degrade:**
any load/tokenize/run failure → lowest-confidence result, never thrown; **no user text logged**
(reasons only).

**Session lifecycle (P3) — transient vs sustained teardown rule:** a **transient** gate-off
(thermal/battery flicker) only **skips one inference and keeps the session**. The session is torn
down only on (a) `releaseResources()` (the `SessionLifecycle` ONNX-free seam, wired from `:app`'s
`onTrimMemory` in Q/R — `:app` already deps `:data:ai-local`; not wired in P because the classifier
isn't bindable until Q provides the `DeviceProfileProvider`/`ModelAvailabilityRepository`/`LocalModelFiles`
impls) and (b) **sustained** gate-off (debounced ~30s). Teardown is guarded by `runMutex.tryLock()`;
if a run holds it, a `pendingTeardown` flag defers teardown to the run's `finally`. Re-inits lazily.

**NNAPI (Fork P6-5):** `OnnxSessionFactory` keeps **CPU as the deterministic default** (the `<150ms`
MID_RANGE budget is the CPU path); appends NNAPI **only when `nnapiEnabled` AND `sdkInt >= 29`**
(skipped by construction on API 28). Flag location pinned in **`OnnxRuntimeFlags`**
(`NNAPI_ENABLED_BY_DEFAULT = false`); `nnapiEnabled`/`sdkInt` are **constructor seams** so P5 can
force NNAPI on to compare paths. (a) init failure → catch → CPU-only session, non-PII warn, no crash;
(b) init-success-but-degraded → off-by-default until the device run proves it faster **and** correct.

**P0 offline pipeline (`tools/nlu/`, out of Gradle source sets):** `gen_golden_vectors.py`
(**stdlib-only** independent reference of the HF tokenizer → `golden_tokenization.{json,txt}` over a
curated `vocab.mini.txt` exercising whole-word/`##`/`[UNK]`/accent/punct/truncation);
`make_placeholder_model.py` (valid `intent.onnx` with the exact contract, needs `onnx`);
`train_export.py` (fine-tune→int8→export + min-accuracy gate + confusion matrix, needs torch/transformers/net).
This environment has **no torch/transformers/onnx/network**, so the **real** `intent.onnx` + the
pruned multilingual `vocab.txt` (~20–30k, en/ar/tr/ru — OQ#1 amended 2026-06-29) + HF-regenerated golden
vectors are a **device-pending acceptance item** (Block J precedent). The mini-vocab golden set validates
the Kotlin tokenizer's algorithm now, offline.

**Freshly-downloaded model only picked up after restart:** model `availability` is read from
`ModelAvailabilityRepository` per inference, but the bound secondary impl (real `OnnxIntentClassifier`
vs `NoOpIntentMatcher`) is decided at **graph time** by the static gate (profile + availability) in
Block R. A model that finishes downloading mid-session flips `Available` but the bound impl does not
change until the next process start. **Deliberate, not a bug** (Fork P6-4 static-vs-dynamic split);
revisit trigger = if model downloads become frequent enough that a same-session pickup matters.

**Testing (Fork P6-8):** ONNX can't run on the JVM. **21 new JVM tests, 0 failures**
(`WordPieceTokenizerGoldenTest` 3 — byte-exact vs the independent golden; `IntentLabelMapperTest` 7;
`SlotExtractorTest` 5; `OnnxIntentClassifierGateTest` 6 — gate-off LOW_END/thermal/battery/availability
+ gate-on-missing-file degrade + release-safety, all without a real session). `unitTests.isReturnDefaultValues
= true` so the no-session degrade paths' `android.util.Log` calls don't throw under JVM. Real session
only in `androidTest` (`OnnxIntentClassifierInstrumentedTest`, **compiles**; Assume-skips when the
model asset is absent; **device-pending** SM-A325F run records `<150ms` CPU latency + NNAPI→CPU path).

**Build/deps:** `:data:ai-local` gains the `:core:android` edge (per the plan) + test deps
(`junit4`, `coroutines-test`, `:core:testing`); **no new ONNX dep** (1.20.0 already wired); no R8 keep
rule needed for the debug target (frozen to Ph9). `:domain` untouched; ONNX confined to the two shell
files (`OnnxIntentClassifier`, `OnnxSessionFactory`) — grep clean elsewhere incl. the pure layer; no
network on the inference path; intent/Phase-3/5 code + generative router's reserved slot untouched.

**Verification:** `:data:ai-local:testDebugUnitTest` BUILD SUCCESSFUL (21/21);
`:data:ai-local:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL; full `testDebugUnitTest` +
`assembleDebug` **BUILD SUCCESSFUL, 0 failures/errors**.

**Next = Block Q** (DeviceProfile detector + ModelStore + SHA-256 verify + WorkManager download;
gated on Open Question #2 — model hosting/URL). Block R wires `LayeredIntentMatcher` + the DI swap +
the `:app` trim-hook registration + the NLU↔rule confidence-calibration decision + docs-sync + close.
**Phase 6 NOT closed (Block R closes it).**

### ADR 2026-06-28 — Block Q complete (DeviceProfile detector + ModelStore/SHA-256 verify + WorkManager download gating)

**Scope:** Block Q only — `AndroidDeviceProfiler` (+ pure classifier/cache mapping), `ModelStore`
(quarantine → SHA-256 verify → atomic rename, implements P's `LocalModelFiles`), `Sha256Verifier`,
`ModelDownloader`/`ModelDownloadScheduler` ports, `ModelProvisioner`, `ModelManager` (gate-before-
enqueue), `ModelDownloadWorker` (`@HiltWorker`), `KtorModelDownloader` + `WorkManagerModelDownloadScheduler`,
`ModelAvailabilityRepositoryImpl`, the WorkManager + `hilt-work` deps + `:app` `Configuration.Provider`/
`HiltWorkerFactory` wiring. Does **NOT** do the `LayeredIntentMatcher`, the unqualified-`IntentMatcher`
DI swap, the `:app` `onTrimMemory`→`SessionLifecycle.releaseResources()` registration, the runtime
`ensureModel()` trigger, or `TextEmbedder` — all **Block R** / Phase 7.

**§0 OQ#2 branch taken — NOT resolved (expected).** Model download source/hosting is still open, so
the *entire* mechanism is built and JVM-tested against fakes this block; only the live download stays
inert. `ModelDownloadConfig` is the single device/release-pending seam: `INTENT_NLU_PENDING` has blank
`url`/`expectedSha256` (`TODO(OQ#2)`) so `isPinned == false`, and both `ModelManager.ensureModel()`
and `ModelProvisioner.provision()` short-circuit (`NotConfigured`) — genuinely inert, pointing nowhere
real. **Device/release-pending:** the live download + the real artifact's pinned URL/SHA-256, plus the
`AndroidDeviceProfiler` Android-API reads + thermal/battery transitions (instrumented) on SM-A325F.

**Pre-flight deltas vs the prompt:** (1) `DeviceProfileCacheRepository` + impl + the `device_*`
DataStore keys + `PreferencesMapper` round-trip **already existed** (Block E groundwork) — Q1 reuses
them, adds no profile keys. (2) The Ktor `HttpClient` is provided in **`:app`** (engine is Hilt-free),
so "where HTTP lives" for DI = `:app`. (3) `:data:ai-local` allowed edges are
`:domain, :core:common, :core:android, ONNX` only (no HTTP/WorkManager module edge).

**Profile thresholds (§5.A, pure `DeviceProfileClassifier`):** measured against
`ActivityManager.MemoryInfo.totalMem` (reports below nominal). `LOW_END` = `totalMem < 2.5 GB` **or**
`< 4` cores; `HIGH_END` = `≥ 5.5 GB` **and** `≥ 8` cores; else `MID_RANGE` (SM-A325F 4 GB/8-core →
MID). `capability(...)`: `nnapiAvailable = sdkInt ≥ 29` (hint only — NNAPI stays off-by-default,
Block P); `thermalOk = getCurrentThermalStatus() < SEVERE(3)` with a `-1` no-signal sentinel on
API < 29 (reads OK); `batteryOk = !isPowerSaveMode`. Android reads behind `AndroidDeviceProfiler`;
the mappings are pure + JVM-tested.

**Lossy cache (§5.B):** `DeviceProfileCacheMapping` maps `LOW_END ↔ isLowEndDevice=true`,
`MID|HIGH ↔ false`; `profileFromCache` returns `MID_RANGE` for the "not low-end" bucket. Accepted —
Phase 6's effective gate is LOW_END-vs-rest. `AndroidDeviceProfiler` caches `profile()` in memory and
write-throughs to `DeviceProfileCacheRepository` via `@ApplicationScope` (fire-and-forget, never blocks
the synchronous read); `capability()` is re-read every call (thermal/battery move).

**Vocab (§5.D): bundled, not downloaded.** `vocab.txt` (uncased — the pruned multilingual vocab,
data-driven ~20–30k, en/ar/tr/ru; OQ#1 amended 2026-06-29) lives in
`:data:ai-local/src/main/assets/nlu/` (small, static, version-locked to `WordPieceTokenizer` → no
second download/hash, can't drift). `ModelStore` resolves vocab via an injected `vocabOpener` seam
(assets in prod, fake stream in tests) and the **model** from `noBackupFilesDir/models/`. The real
asset is device/training-pending (OQ#1/#2); `vocabStream` returns null when absent → degrade.

**Download HTTP (§5.E): port, not an edge.** `ModelDownloader` port in `:data:ai-local`; the
Ktor-backed `KtorModelDownloader` lives in **`:app`** (reuses the existing `HttpClient`, HTTPS-only,
streams to file, re-throws `CancellationException`) — no HTTP edge into `:data:ai-local`, no data→data
edge. Bound via `ModelProvisionBindsModule`.

**Worker placement — deliberate deviation from prompt §3:** the `@HiltWorker` `ModelDownloadWorker`
shell lives in **`:app`** (composition root, already kapt + Hilt, and where `Configuration.Provider`/
`HiltWorkerFactory` must live) rather than `:data:ai-local` — avoids converting `:data:ai-local` into a
kapt/Hilt-processing module for a ~15-line shell. All correctness-critical logic
(download→verify→promote→mark, idempotency, cancellation) stays in `:data:ai-local`'s JVM-tested
`ModelProvisioner`; the worker only maps `ProvisionResult → Result.success/retry/failure`. Same reason
the provisioning-port fakes (`FakeModelDownloader`, `FakeModelDownloadScheduler`) live in
`:data:ai-local/src/test`, not `:core:testing` (a pure `kotlin.jvm` module that can't depend on this
Android library); the reused domain fakes (`FakeDeviceProfileProvider`, `FakeModelAvailabilityRepository`)
stay in `:core:testing`.

**§6.A enqueue-gate-static-only (conscious choice):** `ModelManager.ensureModel()` enqueues iff
`profile != LOW_END && availability != Available` (+ `config.isPinned`). It deliberately does **NOT**
fold transient `thermalOk`/`batteryOk` into the enqueue decision — a momentary battery-saver at start
must never *permanently* prevent the one-shot download from being *scheduled*. Runtime battery/storage
is handled by the WorkManager **constraints** (defer, not abort); thermal/battery for *inference* is the
per-inference `LocalInferenceGate.allowsLocalNlu(...)` re-check already inside `OnnxIntentClassifier`
(Block P). One policy, evaluated for the right inputs at the right moment — not a second policy.

**No-unverified invariant:** `ModelStore.promote` verifies the quarantine file against the pinned hash
**before** an atomic `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` (same-FS fallback) into the ready path;
verify-fail deletes quarantine and leaves ready untouched. `modelFile()` returns the ready file only,
so the classifier can never load an unverified artifact (disk presence is also the real load-time gate,
backstopping the DataStore availability flag).

**Availability persistence:** `ModelAvailabilityRepositoryImpl` (`:data:repository`) over the shared
`sidr_preferences` store — a `stringSet` key `model_available_ids` (denylist-clean; added to
`ALL_KEY_NAMES`, privacy guard green). Persists `Available` (id present) vs `Missing` (absent);
`Unverified` is never written (it's the on-disk integrity concept; the gate treats Missing/Unverified
alike). Worker calls `markAvailable` only after verify+promote.

**WorkManager (context7-verified, not memory):** `@HiltWorker` + `@AssistedInject(@Assisted Context,
@Assisted WorkerParameters)`; `HiltWorkerFactory` injected into `SidrLauncherApp : Configuration.Provider`
(Kotlin `override val workManagerConfiguration`); manifest removes the default
`androidx.work.WorkManagerInitializer` meta-data on `androidx.startup.InitializationProvider`
(`tools:node="remove"`, `RemoveWorkManagerInitializer` lint). `enqueueUniqueWork(name, KEEP, request)`;
constraints `setRequiredNetworkType(CONNECTED)` + `setRequiresBatteryNotLow(true)` +
`setRequiresStorageNotLow(true)` (no requires-charging); `setBackoffCriteria(EXPONENTIAL, 30s)`; no
foreground service; `CoroutineWorker` cooperative cancellation. **Versions:** `androidx.work` 2.10.0
(`work-runtime-ktx`), `androidx.hilt` 1.2.0 (`hilt-work` + `hilt-compiler` via the **kapt** path in `:app`).

**Build/deps:** new deps = WorkManager + `hilt-work` (+ androidx hilt-compiler kapt) **only**, all in
`:app`; `:core:android` gains a `testImplementation(junit4)` for the pure-mapping tests. No new
`:data:ai-local` edge; `ai.onnxruntime` still confined to the two Block-P shell files (grep clean —
new files reference it only in comments); `:domain` untouched + pure.

**Verification:** `:core:android` + `:data:ai-local` + `:data:repository` `testDebugUnitTest` green;
full `testDebugUnitTest` + `assembleDebug` **BUILD SUCCESSFUL, 0 failures** (Hilt graph incl.
`@HiltWorker`/`Configuration.Provider`/the full provisioning chain validated). **29 new JVM tests**
(Sha256Verifier 4, ModelStore 4, ModelProvisioner 6, ModelManager 4, DeviceProfileClassifier 5,
DeviceProfileCacheMapping 3, ModelAvailabilityRepositoryImpl 3).

**Next = Block R** (`LayeredIntentMatcher` + unqualified-`IntentMatcher` DI swap + R2.5 `:app`
`onTrimMemory`→`SessionLifecycle.releaseResources()` + the runtime `ensureModel()` trigger + R1 NLU↔rule
confidence calibration + docs-sync + close). **Phase 6 NOT closed (Block R closes it).**

#### Rework before close (2026-06-28) — review items P1-1…P2-8

Eight review items applied before closing Q. Where a rework supersedes a statement above, the rework wins.

- **P1-1 (context7-verified WM init, not from-memory).** Re-ran context7 (`/androidx/androidx`):
  `androidx.work.Configuration.Provider` exposes the Kotlin **`workManagerConfiguration` property**
  (+ Java `getWorkManagerConfiguration()`); `WorkManagerInitializer implements androidx.startup.Initializer<WorkManager>`
  and the `RemoveWorkManagerInitializer` lint requires removing it once the Application is a
  `Configuration.Provider`. Verified init path (matches the shipped code):
  ```kotlin
  @HiltAndroidApp
  class SidrLauncherApp : Application(), Configuration.Provider {
      @Inject lateinit var workerFactory: HiltWorkerFactory
      override val workManagerConfiguration: Configuration
          get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
  }
  ```
  ```xml
  <provider android:name="androidx.startup.InitializationProvider"
      android:authorities="${applicationId}.androidx-startup"
      android:exported="false" tools:node="merge">
      <meta-data android:name="androidx.work.WorkManagerInitializer"
          android:value="androidx.startup" tools:node="remove" />
  </provider>
  ```
  Coordinates confirmed: `androidx.work:work-runtime-ktx:2.10.0`, `androidx.hilt:hilt-work:1.2.0` +
  `androidx.hilt:hilt-compiler:1.2.0` (kapt). `@HiltWorker` + `@AssistedInject(@Assisted Context, @Assisted WorkerParameters)`.
- **P1-2 (availability cross-checks disk, not just the marker).** New `domain.ai.local.ModelFilePresence`
  port (`isModelPresent(modelId): Boolean`), implemented by `ModelStore`. `ModelAvailabilityRepositoryImpl`
  now returns the **conjunction**: marker set ∧ file present → `Available`; file present ∧ unmarked →
  `Unverified` (self-heals a crash between atomic-rename and `markAvailable` — next provision re-marks);
  else `Missing`. A marker-set-but-file-missing reads **not-Available** (gate degrades). All three Block-O
  states now reachable; no `data→data` edge (both sides depend on the domain port). New JVM tests:
  marker-but-missing→Missing, present-but-unmarked→Unverified.
- **P1-3 (vocab.txt on the pending list).** The real **`vocab.txt`** — under OQ#1 (amended 2026-06-29) the
  **pruned multilingual** vocab (uncased, data-driven ~20–30k, en/ar/tr/ru, byte-matched to the exported
  tokenizer) — is an explicit OQ#1 device/release-pending artifact in this ADR + CLAUDE.md; it ships
  bundled in `assets/nlu/` and is a silent release-blocker if dropped.
- **P2-4 (download impl out of `:app`).** **Supersedes §5.E.** The `ModelDownloader` port moved to
  **`:domain`** (`domain.ai.local`, stdlib-only `java.io.File` signature); `KtorModelDownloader` moved to
  **`:data:ai-cloud`** as a plain class (Block-K engine pattern), `@Provides`-constructed in `:app` with
  the shared cloud `HttpClient`. This honors the reviewer's intent (impl in the Ktor module, unit-tested)
  **without** the `:data:ai-cloud → :data:ai-local` edge that "port stays in `:data:ai-local`" would have
  forced (that violates the no-`data→data`-edge rule). `:app` now holds only DI bindings. New
  `KtorModelDownloaderTest` (MockEngine): non-HTTPS rejected with **zero requests issued**, 200 streams to
  file, 4xx→permanent, 5xx→transient.
- **P2-5 (fail-fast half-pinned config).** `ModelDownloadConfig` `init { require(url.isBlank() == expectedSha256.isBlank()) }`
  — a `url` without a hash (or vice versa) throws at construction. Test asserts both half-pinned forms throw.
- **P2-6 (storage is `noBackupFilesDir`).** Confirmed: `provideModelStore` passes `context.noBackupFilesDir`
  as `rootDir`; `ModelStore` nests both `models/` (ready) and `models/.quarantine/` under it. A
  re-downloadable, hash-verified blob never enters cloud auto-backup. (No code change — confirmation.)
- **P2-7 (retry taxonomy defined now).** `KtorModelDownloader` classifies failures via
  `OperationError.NetworkError.retryable`: 4xx/non-HTTPS → `retryable=false`, 5xx/network/timeout →
  `retryable=true`. `ModelProvisioner.provision()` maps these to `ProvisionResult.PermanentFailure` vs
  `TransientFailure`; SHA-256 mismatch → `VerificationFailed`. `ModelDownloadWorker.doWork()`:
  Transient→`Result.retry`, {Permanent, VerificationFailed, NotConfigured}→`Result.failure` (no retry storm
  on a bad pinned URL once OQ#2 lands). New JVM tests cover both `provision()` branches.
- **P2-8 (fakes location + R-promotion).** `:data:ai-local/src/test` is a **test source set, not a published
  module**. `FakeModelDownloadScheduler` fakes a `:data:ai-local`-internal port → stays here.
  `FakeModelDownloader` now fakes the **domain** `ModelDownloader` port → eligible for promotion to
  `:core:testing`; **deferred to Block R**, only if R's tests consume it (today the sole consumer is this
  module's `ModelProvisionerTest`).

**Post-rework verification:** `:domain` + `:data:ai-local` + `:data:ai-cloud` + `:data:repository`
`testDebugUnitTest` green; full `testDebugUnitTest` + `assembleDebug` **BUILD SUCCESSFUL, 0 failures**
(Hilt graph re-validated after the `ModelDownloader`/`ModelFilePresence` rewiring). **Block Q JVM tests
now 39** (+10: ModelDownloadConfig 3, KtorModelDownloader 4, ModelProvisioner +1, ModelAvailabilityRepositoryImpl +2).
Guards re-asserted: `ai.onnxruntime` still confined to `OnnxIntentClassifier`/`OnnxSessionFactory`;
`:domain` pure; `:data:ai-local` gains no edge; `:data:ai-cloud` has **no** `:data:ai-local` edge (port in
`:domain`); new deps still only WorkManager + hilt-work.

**Device/release-pending (updated):** the live download + the real artifact's pinned URL/SHA-256 (OQ#2);
**the pruned multilingual `vocab.txt` (data-driven ~20–30k, en/ar/tr/ru, byte-matched to the exported
tokenizer) — OQ#1 (amended 2026-06-29)**; the `AndroidDeviceProfiler` Android-API reads + thermal/battery
transitions (instrumented) on SM-A325F.

### ADR — OQ#1 amended (multilingual) (2026-06-29)

**Requirement.** Sidr is a multilingual launcher. The original OQ#1 model choice (BERT-Mini,
`bert-base-uncased`, **English** WordPiece vocab 30522, `max_len 32`) is **invalid for non-English
commands**: an English WordPiece vocab tokenizes other scripts almost entirely to `[UNK]`, so the local
NLU would silently never fire for them. This is an **architecture-level** amendment to the pinned
model/tokenizer contract (not a dataset choice), resolved **before Stage 1 (dataset)**. It changes the
**decision + the pinned contract + the docs** plus a minimal, proven-safe code touch; it does **not**
prune/distill/train/quantize/export/host a model or author the dataset (Stage 1/2+ / OQ#2).

**Target language set (fixed).** `en` / `ar` / `tr` / `ru`. European languages are **deferred** — a broad
WordPiece base covers them in-vocab, so adding one later is a dataset+retrain step with **zero contract
change**. (Western users can command in English; an unknown native language degrades to rule → cloud, so
local language coverage is an optimization, not a correctness requirement.)

**Chosen base + tradeoff.** Base = **`bert-base-multilingual-uncased`** as a *teacher*, **not** the shipped
model (present-day facts via web_search: 12-layer, **~168M params** (corrected from Google's README which
copied the English BERT-Base figure; reproducible count is 168,054,423 — ~105,879 vocab × 768 embedding dim
≈ 81M of embeddings, roughly half the model), WordPiece, ~110k shared vocab, **uncased** — its lower-case +
NFD accent-strip preprocessing matches `WordPieceTokenizer` exactly → **no tokenizer change**, **no golden
regeneration** of the algorithm). Naive mBERT (~168M, dominated by a ~110k-row embedding table — the
dominant mass justification is now *stronger*, not weaker) will not meet `<150ms` on the SM-A325F, so it is
**rejected as the shipped model**. The shipped model is produced (Stage 2) by compressing the teacher:
- **(Recommended, chosen) WordPiece teacher → vocab-prune → layer-distill → int8.** Prune the embedding
  table to the tokens in en/ar/tr/ru + the launcher command domain (~110k → **~20–30k**; the dominant size
  lever — embedding pruning matters even more at 168M than the incorrectly-quoted 110M, since the embedding
  table is ~half the model weight; WordPiece algorithm unchanged); distill to a ~2–4-layer student (the
  latency lever); int8 (final footprint). Blast radius = `OnnxModelSpec` constants + a smaller pruned
  `vocab.txt` + regenerated goldens.
- **(Fallback, documented) a non-transformer classifier** (char/byte-CNN or fastText-style subword) — only
  if the WordPiece path **measurably** fails `<150ms` after prune+distill+int8; it reworks the P
  tokenizer/export pipeline (no longer WordPiece). Not adopted without a measured latency failure.
- **(Rejected) SentencePiece/XLM-R / `Multilingual-MiniLM`** (≈250k SentencePiece vocab) — better quality
  but a **full rewrite of `WordPieceTokenizer` (P2a) + its golden test**.

**`<150ms` on SM-A325F is a HARD Stage-2 go/no-go gate**, not a soft risk: if prune+distill+int8 of the
WordPiece path can't meet it, fall back to the non-transformer classifier rather than ship a too-slow model.

**New pinned values.** `maxLen` **32 → 48** (provisional, finalized at dataset time — Turkish agglutination
+ Arabic fragment into more wordpieces/word). `vocabSize` = **data-driven**, not a literal: `OnnxModelSpec`
now carries a `VOCAB_SIZE_PENDING` sentinel (the pruned `vocab.txt` doesn't exist yet), and
`tools/nlu/train_export.py:assert_onnx_contract` **derives** vocab size from the produced `vocab.txt` line
count (`sum(1 for _ in open(vocab))` — line-iteration, no trailing-newline off-by-one) and hard-fails
unless `model_embedding_rows == len(vocab.txt lines)` + the `[1, maxLen]` I/O shape. It then **prints**
that number with "set `OnnxModelSpec.vocabSize` to this exact value". **Precise mechanism (corrected):** the
script does **not** read the Kotlin `OnnxModelSpec.vocabSize` at all (no fragile `OnnxModelSpec.kt` parse,
no CLI arg) — so the automated hard-fail covers **model↔`vocab.txt`** agreement, and the
`vocab.txt`→Kotlin step is a **human copy at Stage 2 guarded by the print, not a third automated assert**.
This also means it is inert/safe on today's `VOCAB_SIZE_PENDING` sentinel (the sentinel is never read, so
no crash and no false compare). `model_embedding_rows` is read from whatever `.onnx` is passed, so Stage 2
must point it at the **real pruned export** (not the teacher) or the check is falsely green. **The 7 labels
are language-independent and DID NOT change.**

**Blast-radius statement (per block).**
- **O** — **none** (language-independent domain contracts).
- **P** — `OnnxModelSpec` constants (`maxLen 32→48`, `vocabSize` sentinel) + a new vocab-agnosticism test
  (`WordPieceTokenizerMultilingualTest`, synthetic Latin/Cyrillic/Arabic mini-vocab) + doc/contract sync;
  **runtime code unchanged** under the WordPiece option (`WordPieceTokenizer`/`OnnxSessionFactory`/
  `OnnxIntentClassifier`/`IntentLabelMapper` untouched). Real pruned vocab + regenerated goldens are Stage 2.
  Tokenizer rewrite **only** under the rejected SentencePiece option.
- **Q** — model-agnostic (downloads + SHA-256-verifies bytes); **no code change**. Carry-forward: re-check
  the LOW_END threshold + `<150ms` budget against the heavier multilingual footprint.
- **R** — **none**; it sits above the port boundary — `LayeredIntentMatcher` / the DI swap / calibration
  reference only `IntentMatchResult` (intent + confidence) + `IntentConfidencePolicy`, never the
  tokenizer / vocab / `OnnxModelSpec`. **R is unaffected and clear to proceed/merge as-is.** The only
  model-landing step is empirical `confidenceFloor` tuning, which was always pending.

**Carry-forward risks (mitigations mandated above; validated at Stage 2).** (1) Latency on SM-A325F is the
binding constraint — naive mBERT (~168M) won't hit `<150ms`; mitigation = prune+distill+int8, hard gate,
non-transformer fallback; final measurement device-pending. (2) Final pruned vocab size + `maxLen` are
finalized at dataset/Stage-2 time; `OnnxModelSpec`/`assert_onnx_contract` are data-driven so they absorb
the values without code edits. (3) `DeviceProfile` LOW_END cutoff may warrant a stricter MID/HIGH gate
for a heavier model — a `DeviceProfileClassifier` threshold tweak, revisited once footprint is known.
(4) Bigger artifact vs download/storage — `requiresStorageNotLow` already covers it; noted.
(5) **Cased vs uncased — explicit Stage-2 decision required.** The uncased choice this block is a
deliberate blast-radius optimization: `bert-base-multilingual-uncased`'s lowercase + NFD accent-strip
preprocessing matches the existing `WordPieceTokenizer` exactly, so no golden regeneration is needed now.
However, Google marks Multilingual **Uncased** "not recommended" and recommends **Multilingual Cased** —
explicitly for non-Latin alphabets (Arabic) and "often better" for Latin scripts (Turkish). **At Stage 2,
measure cased vs uncased accuracy on the Arabic + Turkish validation subsets and decide on data — do not
default to uncased by inertia.** Switching to cased is bounded: a `do_lower_case=false` preprocessing
toggle + one golden regeneration (not a rewrite). Staying uncased is also valid if the accuracy data
supports it. (6) **CJK — confirmed present, no divergence (verified by code 2026-06-29).** The Kotlin
`WordPieceTokenizer` has `tokenizeChinese()` (per-CJK-char space-padding, called from `basicTokenize`)
plus an `isChinese(cp)` covering HF's CJK Unicode blocks — i.e. it **matches** mBERT's
`_tokenize_chinese_chars`/`_is_chinese_char`. (Minor, non-target: it iterates UTF-16 `Char`s, so
supplementary-plane CJK ≥U+20000 isn't split — irrelevant to en/ar/tr/ru and to BMP CJK.) No-op for the
four target languages; CJK support (if ever added) needs no tokenizer change — completeness, not an
action item. (7) **Stage-2 hardening — `--expected-vocab-size` CLI-guard for `assert_onnx_contract`
(not yet implemented).** Add an optional arg: when supplied, also assert `rows == len(vocab) ==
expected` (the hand-set Kotlin `OnnxModelSpec.vocabSize`); when omitted (today's sentinel), skip the
third compare and just print. Closes the manual-copy gap **without** parsing `OnnxModelSpec.kt` (no
fragile regex). (8) **Stage-2 hardening — run the contract check STRICTLY against the final pruned
artifact + a pruned-size sanity bound (not yet implemented).** `rows == len(vocab)` does **not** catch a
run against the teacher or a not-fully-pruned model — both values agree there, so the assert is falsely
green. Mitigation: point `assert_onnx_contract` only at the real pruned export AND assert the size is in
the pruned band (~20–30k), explicitly rejecting the ~110k teacher embedding table.

**Verification.** `:data:ai-local:testDebugUnitTest` green incl. the new `WordPieceTokenizerMultilingualTest`;
full `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL (this is constants + a tokenizer test — no model
needed). Repo `30522` grep shows only superseded/historical prose, no live pending item. Stage 1 (dataset)
now proceeds multilingually (en/ar/tr/ru).

### ADR 2026-06-19 — Phase 2 skipped / reordered into a minimal slice
- Decision: Phase 2 (launcher shell) is **not** run as a separate phase. Its navigation half was already absorbed into `3.1.x`; its product floor — `InstalledAppsRepository`, app grid, command input, offline app launch — is folded into Phase 3 as a **minimal P2 slice** (Block B).
- Context: Phase 3's intent system cannot reach acceptance without Phase 2's installed-apps repository and command input (e.g. `open telegram` cannot resolve or launch). The skip deferred an unavoidable dependency rather than removing it.
- Rationale: the launcher core (home + app grid + app launch) is the product floor; the intent pipeline is meaningless without it. Building the minimal slice now unblocks `3.4.8`/`3.4.11`/`3.4.13`.
- Consequence: full launcher-shell polish stays deferred; Room/DataStore persistence and intent-match-history are frozen to Phase 4; `feature/settings` and `feature/permission_education` remain inline placeholders until created.
- Active plan: `ai-context/phase-3-intent-system-plan.md` (Blocks A → D). Session summary in root `CLAUDE.md`.

### ADR 2026-06-29 — Block R complete + Phase 6 close
**Context.** Block R is the integration block: it wires the local NLU source into the live
`IntentMatcher` pipeline. Pre-flight (codegraph + reads) confirmed Block Q's three production seams
exist on `feature/launcher-3` (`AndroidDeviceProfiler : DeviceProfileProvider`,
`ModelAvailabilityRepositoryImpl`, `ModelStore : LocalModelFiles`) plus `ModelManager`, so
`OnnxIntentClassifier` is constructible from the Hilt graph. The model itself is still
training/host-pending (OQ#1/#2), so on every current device the NLU secondary escapes — **R is
correct and fully green with NO model present, which is the shipping state.**

- **Precedence — RULE-FIRST (§5.B / Fork P6-2).** `LayeredIntentMatcher(primary = rule,
  secondary = nlu, policy, calibrator)`: run the rule first; if `!policy.isLowConfidence(rule.conf)`
  return it **verbatim and never call the secondary** (preserves the `< 10ms` path + exact Phase-3
  parity). Only on a low-confidence rule consult NLU; an NLU **escape** leaves the weak rule
  standing; otherwise the NLU answer wins iff its calibrated confidence clears `suggestThreshold`.
  The escape predicate is pinned **structurally** — `source == NLU && (best.confidence == 0f ||
  best.intent is UnknownIntent)` — not by free-text `debugReason`; this matches every escape
  `IntentLabelMapper.escape` emits (`gate_off`/`inference_error`/`argmax_unknown`/
  `below_confidence_floor`/`logit_size_mismatch`).
- **R1 calibration — conservative band → Suggest (§5.A, the headline decision).** A winning NLU
  result's raw softmax (∈ `[0.60, 1.0]`) is remapped by the pure `NluConfidenceCalibrator` onto
  `[suggestThreshold 0.50, autoExecuteThreshold 0.85)` — kept strictly below auto-execute
  (`AUTO_EXECUTE_MARGIN 0.01`). So a model-driven intent **always Suggests, never silently
  auto-executes** a side-effecting launcher action. Rationale: NLU only fires on ambiguous input
  (the rule was weak), softmax is not a calibrated probability, and a confirmation step is the right
  UX exactly there; a confident deterministic rule still short-circuits before NLU per §5.B.
  Surfaced NLU confidence is therefore an **approximation, not a probability**. Verified thresholds
  read from source (policy 0.85/0.50, rule scale 0.95/0.90/0.30/0.10/0.0, floor 0.60); the mapping is
  a pure function with boundary unit tests.
- **`confidenceFloor` home (R1 open point) — stays in `OnnxModelSpec`** (it governs the escape
  *inside* the classifier). The calibrator parameterizes its own input-domain floor as a plain
  `Float` (default `0.60f`, mirroring `OnnxModelSpec.confidenceFloor`) so `:data:repository` keeps
  **no edge to `:data:ai-local`**. No second `IntentConfidencePolicy`; calibration lives in the
  helper the matcher owns.
- **Home + no-edge (§5.C).** `LayeredIntentMatcher` + `NluConfidenceCalibrator` live in
  `:data:repository` (alongside `RuleBasedIntentMatcher`), referencing only the `IntentMatcher`
  **port** + policy for both collaborators — no `data→data` edge, exactly the
  `DefaultGenerativeRouter`/`GenerativeAiEngine` precedent. Which impls fill `@RuleMatcher`/
  `@NluMatcher` is decided in `:app` DI.
- **§5.F gate-off binding — DEVIATION from plan R2 (deliberate): always bind the self-gating
  classifier.** `@NluMatcher` is bound to `OnnxIntentClassifier` **unconditionally** (no graph-time
  real-vs-NoOp swap). Rationale: (1) Block P already made it self-gate per inference (LOW_END /
  no-verified-model / thermal/battery → escape **without** loading ONNX); (2) **model availability
  flips at runtime** when a download completes, so a graph-time choice would go stale until app
  restart — the live re-check is more correct; (3) no production `NoOpIntentMatcher` is needed (the
  existing one is test-only in `:core:testing`). The gate-off fallback is the real
  `RuleBasedIntentMatcher` via `LayeredIntentMatcher`'s escape handling, never a NoOp masquerading
  as an answer. Construction does no ONNX work (lazy session), so binding it on LOW_END is safe.
- **Single instance for `@NluMatcher` + `SessionLifecycle` (hard invariant).** `OnnxIntentClassifier`
  is `@Provides @Singleton` in `NluMatcherProvidesModule`; both the `@NluMatcher IntentMatcher` and
  the `SessionLifecycle` providers return that injected singleton, guaranteeing the trim hook tears
  down the *same* live session, not a different empty object. `modelId = config.modelId` keeps the
  classifier aligned with Block Q's provisioner/manager.
- **R2.5 — `onTrimMemory` teardown (§5.D / Fork P6-9).** `SidrLauncherApp` (itself a
  `ComponentCallbacks2`) overrides `onTrimMemory(level)` → `releaseResources()` at/above
  `TRIM_MEMORY_BACKGROUND` (40), and `onLowMemory()` → `releaseResources()`; lighter foreground
  levels are ignored to avoid thrashing a session mid-use; the session lazily re-inits on the next
  gated inference. `:app` holds only the ONNX-free `SessionLifecycle` seam (no `ai.onnxruntime`
  edge). Signatures used (verified against the Android SDK at compile): `ComponentCallbacks2.
  onTrimMemory(Int)` / `onLowMemory()` and the constant `TRIM_MEMORY_BACKGROUND`; the deprecated
  (API 34) `TRIM_MEMORY_RUNNING_*` / `TRIM_MEMORY_UI_HIDDEN` levels are deliberately not used.
- **R3 — `ensureModel()` trigger (§5.E).** Fired fire-and-forget from `SidrLauncherApp.onCreate()` on
  `@ApplicationScope` (= `SupervisorJob() + Dispatchers.IO`), so it never blocks the cold/main path
  and a failure cannot crash startup. Inert under OQ#2 (`config.isPinned == false` → no-op); also
  warms Block Q's cached `DeviceProfile` on first run.
- **No-model-parity guarantee.** With the model absent (today's shipping state) the secondary always
  escapes and rule-first means a confident rule returns before NLU is consulted, so
  `LayeredIntentMatcher` reproduces `RuleBasedIntentMatcher` outcomes exactly (JVM-tested across the
  Phase-3 command set). Zero Phase-3 regressions.
- **Invariants held.** Two-port invariant intact (`MatcherSource.AI` absent; generation untouched);
  `HandleUserCommandUseCase` + `RuleBasedIntentMatcher` + generative router unchanged; `:domain`
  pure; `ai.onnxruntime` still confined to `OnnxIntentClassifier`/`OnnxSessionFactory`; no new
  dependency. Consistent with the concurrent OQ#1 multilingual amendment (which recorded "R — none;
  unaffected"): R references only `IntentMatchResult` + `IntentConfidencePolicy`, never the
  tokenizer/vocab/`OnnxModelSpec`.

**Files.** New: `data/repository/.../intent/LayeredIntentMatcher.kt`,
`.../intent/NluConfidenceCalibrator.kt` (+ JVM tests `LayeredIntentMatcherTest`,
`NluConfidenceCalibratorTest`); `app/.../di/RuleMatcher.kt`, `NluMatcher.kt`,
`NluMatcherProvidesModule.kt`. Edited: `IntentProvidesModule` (unqualified `IntentMatcher` →
`LayeredIntentMatcher`), `SidrLauncherApp` (trim hooks + `ensureModel` trigger). Docs:
`architecture.md` (§169/192 ONNX-slot wording fixed — generative slot is Phase 7+ and still
reserved; Phase 6 feeds the matcher pipeline — + as-built rule-first layered pipeline),
`roadmap.md`, `phase-6-local-nlu-plan.md`, `CLAUDE.md`.

**Verification.** `:data:repository:testDebugUnitTest` executed green (fast-path "NLU never invoked"
via a counting fake, escape→rule, calibrated answer, no-model parity, calibrator boundaries);
forced `:app:kaptDebugKotlin`/`compileDebugKotlin` BUILD SUCCESSFUL (Hilt graph valid — single
unqualified `IntentMatcher` = `LayeredIntentMatcher`); full `testDebugUnitTest` + `assembleDebug`
green.

**Device/release-pending (record, not fake-passed).** End-to-end NLU-answers-a-command and the
`onTrimMemory` teardown on SM-A325F, gated on the real model (OQ#1 train/quantize/export + OQ#2
host/SHA-256). Bundled with the still-open Block J `SecretStoreInstrumentedTest` and Block N (N5)
device runs, plus Block P P5 inference budget (`< 150ms` MID_RANGE) and Block Q `AndroidDeviceProfiler`
instrumented reads.

**Phase 6 is CLOSED with Block R (Blocks O → R).** Next = the deferred device-acceptance pass
(Block J/N + P5/Q live items) and/or Phase 7 per the roadmap.

**Reconcile verified 2026-06-29.** Parallel-session reconcile checklist run after the Block-R commit:
(1) git clean, no conflict markers, both the Block-R commit (`5d245bc`) and the OQ#1-amendment docs
commit (`c4d0e9e`) present — nothing lost; (2) all three ADRs coexist (OQ#1-amended-multilingual,
Block-Q-incl-rework, Block-R-close); (3) every `30522` / `max_len 32` / English-base hit is explicitly
marked SUPERSEDED — the live contract reads multilingual (`bert-base-multilingual-uncased` → pruned
`vocab.txt` ~20–30k en/ar/tr/ru, `maxLen 48`, `VOCAB_SIZE_PENDING`); (4) `CLAUDE.md` status +
Contract→Owner rows current; (5) `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL with exactly one
unqualified `IntentMatcher` (= `LayeredIntentMatcher`) + `@RuleMatcher`/`@NluMatcher` qualified. **All
PASS — clear to proceed to Stage 1 (multilingual dataset, en/ar/tr/ru).** Stage-2 follow-up (not a
blocker): set `OnnxModelSpec.vocabSize` (today the `VOCAB_SIZE_PENDING` sentinel) to the actual
produced pruned `vocab.txt` line count. NOTE — `assert_onnx_contract` is **already data-driven** for the
part it automates: it hard-fails unless `model_embedding_rows == len(vocab.txt lines)` and prints the
value to hand-set in Kotlin. It does **not** read `OnnxModelSpec.vocabSize` itself (deliberate — avoids a
fragile `OnnxModelSpec.kt` parse), so it is inert/safe on today's `VOCAB_SIZE_PENDING` sentinel (never
read → no crash, no false compare), and the `vocab.txt`→Kotlin copy is human-guarded-by-print, not a
third assert. The checklist's "make the assert data-driven" phrasing predated the amendment and is
**obsolete** — do not re-do it. (Optional Stage-2 hardening, not required: add an `--expected-vocab-size`
CLI arg so the manual Kotlin value is asserted when supplied and skipped on the sentinel — closes the
manual-copy gap without any Kotlin parse.)

### ADR 2026-06-29 — Phase 7 (Voice + contextual suggestions) forks decided, plan agreed, NO block started
- **Status:** planning round only — no production Kotlin, no `build.gradle.kts`/catalog edits, no manifest
  edits this round. Deliverable = `ai-context/phase-7-voice-suggestions-plan.md` (Blocks **S → W**,
  continuing the alphabet). First execution round gated to **Block S only**. Mirrors the Phase-5/Phase-6
  "forks-before-code" discipline (the 2026-06-27 Phase-6 ADR is the template).
- **Pre-flight repo-truth deltas found (codegraph + grep + file reads, not docs):**
  - **`:feature:suggestions` is an EMPTY scaffold** — `include`d in `settings.gradle.kts` with a
    `build.gradle.kts` (no Hilt) but **zero `.kt` sources**. Block W builds it out; under the F7-8
    single-owner decision it becomes a **stateless `SuggestionsRow` composable only** (no VM → no Hilt).
  - **No `SpeechInputSource` / `SpeechRecognizer` anywhere** (grep empty). `architecture.md`/`roadmap.md`
    name `SpeechInputSource` as aspirational — Phase 7 **creates** the port (`:domain`) + the Android
    `SpeechRecognizer` impl (`:core:android`) + the JVM fake (`:core:testing`) from scratch (Blocks S/T).
  - **Suggestion persistence ports exist; the pipeline does not.** `SuggestionsCacheRepository`,
    `SuggestionRankingRepository`, `UsageHistoryRepository` are built/tested, but there is **no
    `Suggestion` model, no `SuggestionProvider`/`SuggestionEngine`/`SuggestionRanker` ports, no context
    pipeline** — net-new (Blocks S → U). `CachedSuggestion`'s display-only/no-raw KDoc is honoured.
  - **`TextEmbedder` port exists, impl deferred to Phase 7 by name** (its own KDoc). Block V fills it
    **only** behind a real reader — U's semantic re-rank seam (build-a-producer-with-no-reader forbidden).
  - **Permission framework has three DORMANT dangerous features** (`VOICE_INPUT`/`CALENDAR_SUGGESTIONS`/
    `LOCATION_SUGGESTIONS`, all `requestable=false`, already mapped to `RECORD_AUDIO`/`READ_CALENDAR`/
    `ACCESS_FINE_LOCATION`); `PermissionEducationViewModel` is hardcoded to `WALLPAPER`; `refreshStatus()`
    is upgrade-only and **carries an explicit Block-H debt** to revisit when the first dangerous permission
    lands (`RECORD_AUDIO`, Ph7). Phase 7 flips the flags + routes the feature in + discharges the debt
    (Block T audio, Block U calendar/location).
  - **WorkManager is fully wired** (`Configuration.Provider`/`HiltWorkerFactory`, manifest initializer
    removed, `enqueueUniqueWork(KEEP)` + battery/storage constraints). Phase 7 adds **no new WM infra** —
    the periodic pre-compute/cleanup workers reuse the factory via `enqueueUniquePeriodicWork`.
  - **Model-provisioning machinery is general** (`ModelStore` quarantine→SHA-256→atomic-rename,
    `ModelProvisioner`/`ModelManager`/`KtorModelDownloader`/`ModelAvailabilityRepositoryImpl`). The
    embedding model reuses it verbatim with a **second** `ModelId` + `ModelDownloadConfig.EMBEDDING_PENDING`
    (the OQ#3 inert seam, exactly like `INTENT_NLU_PENDING`).
  - **Co-residency fact (codegraph-verified):** `OnnxIntentClassifier` and the future `OnnxTextEmbedder`
    are **distinct `SessionLifecycle` holders**; `:app` `onTrimMemory`/`onLowMemory` call
    `releaseResources()` per holder; there is **no cross-session arbiter** today and the plan does not add
    one (see closure #1).
- **Three pre-ADR items closed before the flip (the verifier's request):**
  1. **Co-residency of the NLU + embedder ONNX sessions under the `< 150MB` MID_RANGE ceiling — decided
     Option B (accepted, not arbitrated).** The two are not co-resident **by scheduling, not by locking**:
     the embedder's only consumer is U's re-rank seam, run primarily by the **deviceIdle** pre-compute
     worker (Block W) when the foreground command path is idle and the NLU session has already torn down
     via its sustained-gate-off (~30s) / `onTrimMemory` path; both tear down together under `onTrimMemory`.
     A rare brief overlap is **explicitly accepted**; the combined footprint is a device-pending OQ#3
     measurement; a **single-resident arbiter is frozen-forward**, built only if the device measurement
     shows a breach. Only `:app` wiring change: the single injected `SessionLifecycle` becomes a Hilt
     **`Set<SessionLifecycle>` multibinding** so both holders are released. Recorded in the plan's hard
     invariants + Block V.
  2. **Suggestions single source of truth — decided owner = the host `LauncherViewModel`/
     `LauncherUiState.suggestions`** (`architecture.md:229` single-source-per-concern). The earlier draft's
     `SuggestionsViewModel` with its **own `StateFlow<List<Suggestion>>`** is **dropped** (it duplicated the
     concern). `:feature:suggestions` ships a **stateless `SuggestionsRow(suggestions, onSuggestionTap)`**
     only (no VM, no Hilt). Pinned in Block S (port shape), wired in W1 — must not drift. Recorded in F7-8,
     F7-11, the pre-flight bullet, Block S, Block W (files + W1), and confirmed-decision #8.
  3. **Privacy-guard wording corrected.** Voice is an input *modality* — recognized text legitimately flows
     out as `USER_COMMAND`, byte-identical to keyboard text. The guard invariant is therefore **"no *new
     outbound category* (calendar event data, location coordinates, raw audio / a standalone transcript
     field) enters the allow-list"**, **not** "voice never reaches the cloud" (which would assert something
     false). A positive regression keeps a voice-derived command flowing as `USER_COMMAND` (the Block-L
     "calendar-in-a-user-command stays legal" precedent). The Block-L allow-list
     `{USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS}` is unchanged. Recorded in F7-9, the hard
     invariant, the contract table, and confirmed-decision #9.
- **Forks decided (full rationale in the plan §"Fork decisions"):**
  1. **Voice scope** = STT → text → the **existing** intent pipeline; on-device preferred
     (`createOnDeviceSpeechRecognizer` + `EXTRA_PREFER_OFFLINE`); never a new generative path; never
     our-backend audio (BYOK, no backend — fallback is the user's own system recognizer); raw audio never
     persisted, transcripts never logged; wake-word frozen-forward.
  2. **Voice surface** = one `SpeechInputSource`, a mic affordance on the **launcher command input**
     (primary), optional thin assistant-prompt reuse — one port, one Android impl, two call sites.
  3. **`RECORD_AUDIO`** = `VOICE_INPUT.requestable=true`; feature routed into the education VM (the
     hardcoded `WALLPAPER` escape hatch); full dangerous-permission lifecycle
     (`shouldShowRequestPermissionRationale` → `PERMANENTLY_DENIED` → system-Settings deep link); the
     Block-H `refreshStatus()` debt **discharged**. Template for Block U's calendar/location flows.
  4. **Context sources** = offline-first always-on (time-of-day + recent/frequent usage from
     `UsageHistoryRepository`) + opt-in permission-gated (calendar, location) that return **empty** when
     not `GRANTED`; only derived display-safe `Suggestion{label, actionId}` leaves a provider.
  5. **Ranking** = pure deterministic JVM-tested `HeuristicSuggestionRanker` ships (recency + frequency +
     time-prior + per-source weight, dedup by `actionId`, bound to N); ONNX semantic re-rank is an
     **optional gated decorator** with heuristic fallback (the CPU-vs-NNAPI precedent).
  6. **`TextEmbedder` impl** = `OnnxTextEmbedder` built **behind U's reader**, gated by the **same**
     `LocalInferenceGate`/`DeviceProfile` as the NLU, reusing the Phase-6 model-store/WorkManager; inert
     `EMBEDDING_PENDING` seam if OQ#3 open (heuristic ranker ships); ONNX confined to `:data:ai-local`.
  7. **WorkManager** = periodic `SuggestionPrecomputeWorker` (~24h, `batteryNotLow`+`storageNotLow`+
     `deviceIdle`, **no network**, **no `LOW_END`**, gated on `aiSuggestionsEnabled`,
     `enqueueUniquePeriodicWork`) + `UsageCleanupWorker`; boot warmup via `RECEIVE_BOOT_COMPLETED`; reuses
     the existing `Configuration.Provider`/`HiltWorkerFactory`; idempotent + cancellable, no foreground
     service.
  8. **Surface** = single owner = host `LauncherViewModel`/`LauncherUiState.suggestions` (no parallel VM);
     `:feature:suggestions` = stateless `SuggestionsRow`; cold-start cache-restore-then-supersede (the
     deferred Fork-6 half). *(See closure #2.)*
  9. **Privacy** = outbound allow-list unchanged; guard = "no new outbound category", not "voice never
     reaches cloud"; sensitive context never persisted raw / logged; inventory guards extended for any new
     keys/tables. *(See closure #3.)*
  10. **Testing** = interface-gate everything; JVM fakes in `:core:testing` (JVM-only); real recognizer +
      real embedder only on SM-A325F; the `< 150ms` embedder budget + the recognizer run are
      device-pending acceptance items (the Block-J/N/P precedent).
  11. **Deps** = **no new Gradle deps** (`SpeechRecognizer` = framework; WorkManager/`hilt-work`/ONNX
      already in catalog); new manifest perms `READ_CALENDAR` + `ACCESS_FINE_LOCATION` +
      `RECEIVE_BOOT_COMPLETED` (`RECORD_AUDIO` already declared in Block N); `:feature:suggestions` gains
      **no** Hilt under F7-8; full ONNX R8/ProGuard frozen to Ph9; `EncryptedSharedPreferences`/
      `security-crypto` remain forbidden.
- **Two open questions, explicitly NOT forks (gate their blocks, must be closed first):** OQ#3 — embedding
  model + tokenizer + host + pinned SHA-256 + dimensionality/pooling (`assert_embedding_contract`) gates
  **Block V**; OQ#4 — on-device STT availability across target devices (SM-A325F + API-28/29 fallback)
  gates **Block T device acceptance**. The `SpeechInputSource` contract (availability probe + graceful
  unavailable state) is decided in S/T regardless; only the device matrix / real artifact is pending. The
  **11 fork count is unchanged** — these are unresolved gating items, not decisions.
- **Tooling note:** WorkManager **periodic** API verified current via context7 (`/androidx/androidx`:
  `PeriodicWorkRequest.Builder`, `enqueueUniquePeriodicWork`, `ExistingPeriodicWorkPolicy`,
  `setRequiresDeviceIdle`/`setRequiresBatteryNotLow`/`setRequiresStorageNotLow`, 15-min periodic floor).
  **`android.speech.SpeechRecognizer` is NOT an androidx library — context7 returns only the Leanback
  wrappers, not the framework class.** The framework signatures (`createOnDeviceSpeechRecognizer` API 31+,
  `isOnDeviceRecognitionAvailable`, `RecognitionListener`, `EXTRA_PREFER_OFFLINE`, main-thread binding) are
  flagged **re-verify-against-the-Android-Javadoc-at-Block-T**, the same discipline Phase 6 applied to the
  thin ONNX Java coverage (Fork P6-5) and Phase 5 to Ktor `HttpTimeout` — not pinned from memory.
- **Next:** execute the **Block S** prompt only (pure `:domain` contracts + heuristic ranker + fakes), then
  gate S green before T; T's device run is independent (port-gated); U before V (V needs U's reader);
  V before W; W closes Phase 7.

### ADR 2026-06-29 — Block S complete (suggestion + voice domain contracts, pure)
- **Scope:** Block S only — pure-`:domain` suggestion + voice contracts + the heuristic ranking policy +
  JVM fakes. No impls (Android/ONNX/WorkManager are T/U/V/W), no DI, no manifest, no new Gradle deps. The
  NLU 7-label set and `HandleUserCommandUseCase` were not touched.
- **New `:domain` files:**
  - `…domain.suggestions`: `Suggestion(label, actionId, source: SuggestionSource, score: Double)` (the
    third concern's display-safe value type — distinct from `IntentMatchResult` and `AiChunk`);
    `SuggestionSource{RECENT_USAGE, FREQUENT_USAGE, TIME_OF_DAY, CALENDAR, LOCATION, SEMANTIC}`;
    `SuggestionContext(timeOfDay: TimeOfDay, nowEpochMs: Long, typedPrefix: String?)` +
    `TimeOfDay{MORNING, WORK, EVENING, NIGHT}` (display-safe shared input — `nowEpochMs` for testable
    recency in U, `typedPrefix` for V's semantic re-rank; never carries raw sensitive signals);
    `SuggestionProvider.provide(context): List<Suggestion>` (empty when permission/signal absent, never
    throws); `SuggestionEngine.suggestions(): Flow<List<Suggestion>>` + `refresh(): OperationResult<…>`
    (port-only; aggregation is U); `SuggestionRanker.rank(candidates, context)` + the pure
    `HeuristicSuggestionRanker`.
  - `…domain.voice`: `SpeechInputSource.isAvailable()` + `listen(languageTag): Flow<SpeechRecognitionState>`
    (cold flow, input modality — feeds the existing command path, not a 4th pipeline);
    `SpeechRecognitionState{Ready, Partial, Final, Error, Ended}` (sealed; `Ended`/`Error` terminal,
    failure-as-value — the `AiChunk` precedent); `SpeechRecognitionError{UNAVAILABLE, PERMISSION_DENIED,
    NO_MATCH, BUSY, NETWORK, TIMEOUT, UNKNOWN}` (enum, like `AiStopReason`/`ModelAvailability`; Android
    error-code mapping deferred to the `:core:android` impl in Block T).
- **`HeuristicSuggestionRanker` (pure, deterministic):** `weight = score × sourceWeight(source) ×
  timePrior(source, timeOfDay)`; dedup by `actionId` keeping max weight; sort desc with a deterministic
  tie-break (`label`, then `actionId`); bound to `maxResults` (default `DEFAULT_MAX_SUGGESTIONS = 5`,
  mirroring data-layer `MAX_CACHED_SUGGESTIONS`; the cache repo still caps on write). The candidate's
  incoming `score` is the provider's recency/frequency signal — the ranker needs **no raw history**, so it
  stays pure and table-testable. The weight tables are heuristic/tunable; the **order contract** is what is
  pinned, not the constants.
- **New fakes (`:core:testing`, JVM-only):** `FakeSpeechInputSource` (scripted `Ready→Partial*→Final→Ended`
  / `Error` terminal, `available` flag, records language tags), `FakeSuggestionProvider` (scripted list +
  records contexts), `FakeSuggestionEngine` (`MutableStateFlow`-backed, `emit`, `refreshCount`).
  `FakeTextEmbedder` already existed.
- **Closure carry-through (the three pre-ADR items) pinned in code/tests where they touch S:**
  - **Suggestions single-source (F7-8):** `SuggestionEngine.suggestions()` is documented to be collected by
    the **one** host `LauncherViewModel` (no parallel `SuggestionsViewModel`/`StateFlow`).
    `SuggestionCompositionTest` proves the form composes with **no new port**: a `Suggestion` is a strict
    superset of `CachedSuggestion(label, actionId)`, so the existing `SuggestionsCacheRepository` caches
    engine output directly. Reconciliation **behaviour** (cache-restore-then-supersede, never merge —
    `architecture.md:254-256`) is deliberately **not** built here; it lands in W2.
  - **Privacy (F7-9):** `SuggestionContext`/`Suggestion` KDoc fixes them as display-safe (no raw event
    title / coordinates / query / timestamp); the outbound allow-list is untouched (S adds nothing to a
    cloud `AiRequest`).
  - **Co-residency (Block V):** not in S's surface (no ONNX here); unchanged.
- **Tests (12 new, 0 failures; 131 domain total):** `HeuristicSuggestionRankerTest` (7 — recency,
  frequency, time-prior order-flip-by-context, dedup, bound at default + custom cap, tie-break determinism,
  empty); `SuggestionCompositionTest` (2 — provider aggregation with an opt-in provider absent → degrade;
  `Suggestion`⊇`CachedSuggestion` projection); `FakeSpeechInputSourceTest` (3 — success-run ordering +
  language-tag capture, `Error` as a value the stream completes on, availability flip).
- **Acceptance verified:** purity guard **machine-checked** — `grep` over `domain/src/main` finds no
  `import` of `android*`/`onnx*`/`workmanager`/`androidx` (only `OperationResult` + `kotlinx…Flow` imported
  by the new files); `domain` classpath stays `coroutines.core` only; `assembleDebug` + full
  `testDebugUnitTest` BUILD SUCCESSFUL; `git status` shows **no production file modified** (only new files +
  the planning-round docs); intent/generative/Phase-3/5/6 suites untouched; 0 new Gradle deps.
- **Next = Block T** (voice input: `AndroidSpeechInputSource` over `SpeechRecognizer`, `RECORD_AUDIO`
  request flow + the `refreshStatus()` Block-H debt, mic affordance). ⚠ re-verify the
  `android.speech.SpeechRecognizer` Java signatures against the Android Javadoc first (context7 covers only
  the Leanback wrappers). T's device run (OQ#4) is independent of U/V/W.

### ADR 2026-06-30 — Block T complete (voice input + RECORD_AUDIO request flow + refreshStatus() debt discharged)

**Context.** Phase 7 Block T lands the first voice-input modality and the first *dangerous* runtime permission
(`RECORD_AUDIO`). Per the Phase-7 plan §Block T (T1–T6) + Forks F7-1/2/3/10. Voice is an **input modality**, not
a fourth pipeline: recognized text feeds the **existing** command-input → `IntentMatcher` path byte-for-byte like
keyboard text (the three-port invariant — `IntentMatcher` ≠ `GenerativeAiEngine` ≠ `SuggestionEngine` — holds).
`HandleUserCommandUseCase` is **untouched**.

**Step 0 (verify-first).** The `android.speech.SpeechRecognizer` framework signatures were re-verified against the
Android Javadoc + the dotnet-android API mirror (context7 covers only the Leanback wrappers and was **not** trusted,
the Phase-5/6 discipline): methods are **main-thread-only**; `createOnDeviceSpeechRecognizer`/
`isOnDeviceRecognitionAvailable` are **API 31+**; results come from `Bundle.getStringArrayList(RESULTS_RECOGNITION)`;
the full `ERROR_*` set + API levels were tabulated; `compileSdk = 35` (all modules) resolves the API-33
`ERROR_LANGUAGE_*` constants by name. codegraph mapped the real edit surface (permission enum/checker/VM/screen,
the launcher command-input + `SavedStateHandle` seam, the `AndroidConnectivityChecker` `callbackFlow` precedent,
the DI modules).

**Decisions.**
- **T1 — `AndroidSpeechInputSource : SpeechInputSource` (`:core:android`, the only new `android.speech` site).**
  `listen()` is a cold `callbackFlow`; the recognizer is **constructed, `setRecognitionListener`'d, `startListening`'d,
  and torn down on the main `Looper`** via a `Handler(Looper.getMainLooper())` (framework main-thread contract). The
  `RecognitionListener` callbacks `trySend` `Ready/Partial/Final/Error/Ended`; `onResults`/`onError` `close()` the
  flow (terminal-as-value — nothing thrown, the `Flow<AiChunk>` precedent). `awaitClose` posts `stopListening` +
  `cancel` + **mandatory `destroy()`** to the main looper; an `AtomicBoolean` guards the cancel-before-create race
  (a create still queued behind a teardown destroys immediately) so no recognizer/microphone leaks. **On-device
  preferred** (`createOnDeviceSpeechRecognizer` behind `SDK_INT >= S` + `isOnDeviceRecognitionAvailable`), else the
  system recognizer biased offline with `EXTRA_PREFER_OFFLINE`. Full error-code → `SpeechRecognitionError` map
  (`INSUFFICIENT_PERMISSIONS→PERMISSION_DENIED`, `NO_MATCH→NO_MATCH`, `SPEECH_TIMEOUT→TIMEOUT`,
  `RECOGNIZER_BUSY`/`TOO_MANY_REQUESTS→BUSY`, `NETWORK`/`NETWORK_TIMEOUT`/`SERVER`/`SERVER_DISCONNECTED→NETWORK`,
  `LANGUAGE_*→UNAVAILABLE`, else `UNKNOWN`). **No transcript/partial/audio is ever logged or persisted** (grep-clean
  for `Log.`/`println`/`Timber`). Plain class (no Hilt) — the `AndroidPermissionChecker`/`AndroidConnectivityChecker`
  precedent; not unit-tested in `core/android` (JVM-only `:core:testing` rule) — covered by `FakeSpeechInputSource`
  + the device run.
- **T2 — DI.** `VoiceModule` in `:app` `@Provides @Singleton SpeechInputSource = AndroidSpeechInputSource(@ApplicationContext)`.
  Voice-unavailable → `isAvailable()==false` → the mic affordance is hidden, keyboard untouched (degrade, never block).
- **T3 — permission routing.** `PermissionFeature.VOICE_INPUT.requestable` flipped **false→true**.
  `PermissionEducationViewModel` gains a `SavedStateHandle` and derives its feature from the
  `Routes.PermissionEducation.ARG_FEATURE` nav arg (`permission_education?feature={feature}`, optional, **defaults
  WALLPAPER** for a bare route / unknown value), replacing the Phase-4 hardcode. `AppNavHost` registers the arg
  (`navArgument` nullable/default-null, so the bare route still matches). The screen's `RequestPermission` permission
  string + post-grant side-effect are feature-driven (a screen-local `PermissionFeature.androidPermission()` `when`
  that mirrors `AndroidPermissionChecker` — duplicated because `feature → core/android` is forbidden; the screen is
  already the Android UI-glue layer). Education ≠ request (Fork 5) preserved.
- **T4 — Block-H `refreshStatus()` debt discharged for the dangerous case.** The guard was a **misnomer** ("upgrade-only")
  but already correct: a genuine `GRANTED → DENIED` revocation (a dangerous permission revoked in Settings while
  backgrounded) flows through the live-read branch and **is reflected** — we never keep believing the mic is available.
  The *only* suppressed transition is `PERMANENTLY_DENIED → DENIED`, reachable **only when `current` is already
  `PERMANENTLY_DENIED`** (never from `GRANTED`) — that is not a revocation but `checkSelfPermission`'s inability to
  distinguish "denied-askable" from "denied-permanent"; pinning the stronger state keeps the screen on the Settings
  deep-link instead of a dead re-request. The two cases never collide. The real gap (grep showed `refreshStatus()` had
  **zero production callers**) is closed by an **`ON_RESUME` `DisposableEffect`/`LifecycleEventObserver`** in the screen
  (lifecycle-runtime-compose 2.8.7, dep-free) so the Settings round-trip (grant *or* revoke) reflects on return. KDoc
  rewritten to state the disjoint branches. JVM-tested for VOICE_INPUT (revocation reflected, permanent-denial preserved,
  upgrade-to-granted).
- **T5 — mic affordance (`:feature:launcher`).** Shown only when `viewModel.isVoiceInputAvailable` (a text-glyph
  `IconButton` — no `material-icons` dep). Tap uses **framework `context.checkSelfPermission(RECORD_AUDIO)`** (API 23+,
  no dep): granted → `LauncherViewModel.startVoiceInput()` (collects `listen()`; `Partial`→`setCommandInput`,
  `Final`→`setCommandInput`+the **unchanged** `onCommandSubmitted` path, `Error`→a safe feedback message);
  not-granted → `navigateTo(permission_education?feature=VOICE_INPUT)` so the **existing Block-G Fork-5 flow** performs
  the request. **Decided (Step-0 review): route-to-education over inline `RequestPermission`** — keeps `:feature:launcher`'s
  build file untouched (no `activity-compose` dep-edge) and reuses the proven flow; the granted path still listens
  immediately with no detour.
- **T6 — tests.** 9 new JVM tests: 4 launcher-voice (partials stream into input; final submits + executes via the
  unchanged path; unavailable degrades to a message + keyboard still works; error surfaces a message) + 5 permission-VM
  (feature routed from the nav arg; unknown arg → WALLPAPER; `GRANTED→DENIED` revocation reflected; `PERMANENTLY_DENIED`
  preserved vs a `DENIED` re-read; `PERMANENTLY_DENIED→GRANTED` upgrade). Real recognizer = device-pending (OQ#4).

**Invariants held.** `android.speech.*` confined to `:core:android` (grep-proven — only `AndroidSpeechInputSource`
matches). `HandleUserCommandUseCase` + the three-port topology untouched; voice text = `USER_COMMAND` byte-for-byte
(F7-9 — no new outbound category). No transcript/audio persisted or logged. **0 new Gradle deps** (`git diff` over
build files/catalog empty; `SpeechRecognizer` = framework; `RECORD_AUDIO` already in the manifest since Block N — T
added only the runtime request flow). `:domain` stays pure (the `PermissionFeature` enum change is stdlib-only). No
manifest line added.

**Verification.** `assembleDebug` **BUILD SUCCESSFUL** (Hilt graph valid — `:app:hiltJavaCompileDebug` clean); full
`testDebugUnitTest` **BUILD SUCCESSFUL**, **383 JVM tests / 0 failures** (domain 131 unchanged; launcher 37,
permission_education 15, core/android 8). `git diff --stat` = 10 files changed + 2 new (`VoiceModule.kt`,
`core/android/.../voice/AndroidSpeechInputSource.kt`).

**Device-pending (OQ#4, carried to Phase-7 Tracking, independent of U/V/W).** On SM-A325F: on-device + fallback
recognition, mic grant/deny/permanently-denied/revocation transitions, the on-device language-pack matrix + the
API-28/29 (no on-device recognizer) fallback path. Exactly as Block S proved its port via the fake, T delivers
JVM-green wiring + impl with the real recognizer pending.

**Next = Block U** (contextual suggestion engine + offline/opt-in context sources + cache-restore; reuses the
Block-T request-flow template for calendar/location).

### ADR 2026-06-30 — Block U complete (contextual suggestion engine + context sources + cache-restore; Phase 7 progresses)

**Context.** Phase 7 Block U lands the third port's first real implementation: `SuggestionEngine` (decided
pure in Block S) gets offline-first + opt-in context providers, an aggregate/rank/persist engine, and the
calendar/location request flows reusing Block T's template verbatim. Per the Phase-7 plan §Block U
(U1–U6) + Forks F7-4/5/8/9. `IntentMatcher`/`GenerativeAiEngine`/`SuggestionEngine` stay three distinct
ports; `HandleUserCommandUseCase` is **untouched** — suggestion tap routing is Block W's job.

**Decisions.**
- **U1 — offline providers (`:data:repository`).** `TimeOfDaySuggestionProvider` (`@Inject constructor()`,
  zero-permission, a fixed two-`Suggestion` table per `TimeOfDay` bucket, always contributes — no platform
  read, nothing to deny). `UsageSuggestionProvider` over `UsageHistoryRepository`: RECENT_USAGE via
  exponential decay (`exp(-elapsedMs/7-day-horizon)`, filtered to records inside the horizon) and
  FREQUENT_USAGE via `launchCount / maxLaunchCount` (filtered `launchCount > 1`, capped at 5 candidates);
  `packageName` is both `label` and `actionId` — app-label lookup is explicitly deferred to Block W's UI,
  matching the rest of the domain suggestion model.
- **U2 — opt-in providers (`:data:repository`).** `CalendarSuggestionProvider`/`LocationSuggestionProvider`
  gate on `PermissionChecker.status(feature) == GRANTED`, returning `emptyList()` immediately (no IO
  dispatch) otherwise, and never throw (any platform failure degrades to empty). Calendar queries only
  `CalendarContract.Instances.EVENT_ID` in a 2-hour lookahead window — no other column is ever requested —
  and emits a single fixed `Suggestion("Upcoming event", "com.google.android.calendar", CALENDAR, 0.90)` on
  any match; the raw event title/id/timestamp never leaves the provider. Location checks only whether
  `LocationManager.getLastKnownLocation` returns non-null across `GPS`/`NETWORK`/`PASSIVE` (`FUSED` is
  API 31+, out of scope at `minSdk 28`) and emits a single fixed `Suggestion("Nearby places",
  "com.google.android.apps.maps", LOCATION, 0.70)` — the raw coordinates never leave the provider. Both
  confined to `:data:repository` (grep-clean over `:domain`/`:feature:*`/`:core:common`/`:core:ui`/
  `:core:testing`), following the `InstalledAppsRepositoryImpl` precedent for `@ApplicationContext Context`
  injection in that module.
- **U3 — `SuggestionEngineImpl` (`:data:repository`).** Gated end-to-end by
  `FeatureFlagRepository.aiSuggestionsEnabled` — when disabled, `refresh()` is a no-op that touches no
  provider and no persistence target before returning `Success(emptyList())`. When enabled: structured
  concurrency (`coroutineScope` + `async` per provider) aggregates candidates, each provider individually
  fault-isolated (`safeProvide` catches `Throwable`, re-throws `CancellationException`) so one throwing
  provider degrades to an empty contribution rather than failing the whole pass; `HeuristicSuggestionRanker`
  (Block S) dedups/bounds/orders; `persist()` writes the ranked list to `SuggestionRankingRepository`
  (history/learning) and maps it into `CachedSuggestion(label, actionId)` for `SuggestionsCacheRepository`
  (cold-start repaint) — never the wider `Suggestion` shape. `persist()`'s two writes are **fire-and-forget**
  (no caller to return a `Failure` to) — the module's first such path; an early draft wired `core/common`'s
  dormant `ResultLogger` port to log the failure, which was **reverted** after review (`NoOpResultLogger`
  made the failure *less* visible than before, and unilaterally wiring a long-dormant, intentionally-unwired
  seam as a side effect of an unrelated fix was the wrong call — that's a separate, deliberate decision).
  The two sites instead log directly via `Log.w` (payload-free), each with a one-line comment stating why
  this path doesn't follow the module's normal return-`Failure` convention. DI: `SuggestionsProvidesModule`
  (`:app`, `@Provides`-composed `List<SuggestionProvider>` — mirrors the Block-M `GenerationProvidesModule`
  precedent for objects assembled from multiple ports rather than a single `@Binds` target).
- **U4 — calendar/location request flows, Block-T template reused literally.**
  `PermissionFeature.CALENDAR_SUGGESTIONS`/`LOCATION_SUGGESTIONS.requestable` flipped `false→true`;
  `PermissionRationale` gained real enable-CTA copy (replacing "coming soon"); manifest gained **exactly
  two lines**, `READ_CALENDAR` + `ACCESS_FINE_LOCATION` (not `RECEIVE_BOOT_COMPLETED` — that's Block W's).
  Every consuming site needed **zero edits**, confirmed by direct read before touching the manifest:
  `PermissionEducationScreen.androidPermission()` was already an exhaustive `when` (no `else`) covering
  all four `PermissionFeature` values, pre-populated ahead of this block; `PermissionEducationViewModel`'s
  and `LauncherViewModel`'s constructors are **unchanged** (no Block-T-style test-factory itemization
  needed — verified by direct read, not assumed). **Per-feature dismissed flag is in-memory only for all
  three non-`WALLPAPER` requestable features, `VOICE_INPUT` included since Block T** —
  `PermissionPrefsRepositoryImpl.keyFor()` returns `null` for `VOICE_INPUT`/`CALENDAR_SUGGESTIONS`/
  `LOCATION_SUGGESTIONS`. This was confirmed **not** a U4 (or T) deviation: it's a Block-G-era structural
  fact (documented there since 2026-06-23) — `"voice"`/`"calendar"`/`"location"` are literally forbidden
  terms in `PrivacyInventoryGuardTest`'s own denylist, so a key named `perm_dismissed_voice`/`_calendar`/
  `_location` would fail that guard outright. `PreferencesKeys.kt`'s comment (which still called these
  features "dormant") was corrected for accuracy in this block — no behavior change.
- **U5 — privacy guard, delivered as executable proofs, not assertions.** Four pieces:
  1. `SuggestionEngineImplTest` (`:data:repository`) proves persistence carries exactly
     `SuggestionRankingRecord`'s 4 fields and `CachedSuggestion`'s 2 fields — nothing wider, no new field
     introduced by Block U.
  2. `SuggestionProviderPrivacyGuardTest` (`:data:repository`) is the strongest piece: it plants a
     sensitive real event title inside a fake `ContentProvider` registered behind
     `CalendarContract.AUTHORITY` (via `Robolectric.buildContentProvider`), and a real GPS fix via
     Robolectric's `LocationManager` shadow, then runs the **actual production**
     `CalendarSuggestionProvider`/`LocationSuggestionProvider` against that planted data and asserts the
     sensitive value appears nowhere in the output. This proves leak-freedom against the real code path
     rather than asserting it from reading the source — even if a future change widened the Calendar
     query's projection, the planted title would still be invisible to the fixed-label output today.
  3. `SuggestionOutboundIsolationTest` (`:domain`) pins `OutboundContextPolicy.ALLOWED`/
     `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` to their exact pre-Block-U literal values and scans them
     for suggestion/calendar/location terms.
  4. A **review-found gap, fixed in this block**: `AiRequestGuardTest` (Block L) pinned
     `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` as hand-maintained literal sets with nothing reflecting
     them against `AiRequest`/`AiError`'s actual declared fields — a field silently added to either type
     without a matching inventory update would have shipped clean under every existing test. Two new
     reflection-based tests there (`AiRequest::class.java.declaredFields` / `AiError::class.java.declaredClasses`
     flat-mapped over their own `declaredFields`, both filtered to non-static) now assert the real classes'
     field sets equal the hand-maintained inventories, so the two can never drift apart unnoticed. This is
     a Block-L-scoped fix surfaced by Block-U review, not new Block-U surface.
  No new DataStore key or Room table was needed (`keyFor()` returns `null` for the two new features; the
  engine reuses Block F's existing `suggestion_ranking` table) — the pre-existing `PrivacyInventoryGuardTest`/
  `RoomColumnNamesGuardTest` (which already denylist `"calendar"`/`"location"`) are the regression proof.
- **U6 — remaining JVM coverage.** Real-output tests for both offline providers (not just forwarding
  through fakes): `TimeOfDaySuggestionProviderTest` covers all 4 `TimeOfDay` buckets;
  `UsageSuggestionProviderTest` isolates the recency and frequency code paths separately plus the
  no-history case. The degrade test (`SuggestionEngineImplTest`) runs the **real**
  `CalendarSuggestionProvider`/`LocationSuggestionProvider` under `FakePermissionChecker`'s default
  `DENIED` and asserts time+usage still produce a non-empty, correctly-sourced result with zero
  CALENDAR/LOCATION contribution. Provider opt-in absence is covered by
  `SuggestionProviderPrivacyGuardTest`'s "not granted, even with sensitive data present" cases. Engine
  mix + dedup-by-`actionId`-keep-highest-weighted is proven across two providers emitting the same
  `actionId` with different scores/sources (the higher *weighted* score wins, not the higher raw score or
  a fixed source priority). Bound-to-the-ranker's-max is proven with 7 candidates from one provider,
  asserting the result caps at `HeuristicSuggestionRanker.DEFAULT_MAX_SUGGESTIONS` (5) and that **both**
  persistence targets reflect the bounded set, not the unbounded 7.

**Invariants held.** `android.location`/`android.provider.CalendarContract` confined to
`:data:repository` (grep-proven, zero hits in `:domain`/`:feature:*`/`:core:common`/`:core:ui`/
`:core:testing`). `HandleUserCommandUseCase` untouched. Outbound `AiRequest` allow-list unchanged (now
reflection-enforced, not just hand-maintained). `:domain` stays pure. **0 new Gradle deps** (`git diff`
over build files/catalog empty). Manifest gained exactly the two named permission lines. No `feature →
feature` edge; no `data → data` edge.

**Verification.** `assembleDebug` **BUILD SUCCESSFUL** (Hilt graph valid). Full `testDebugUnitTest` +
`:domain:test` **BUILD SUCCESSFUL**, **399 JVM tests / 0 failures** (16 new: 4 in `:domain` — 2
`SuggestionOutboundIsolationTest` + 2 reflection-sync additions to `AiRequestGuardTest`; 12 in
`:data:repository` — 4 `SuggestionEngineImplTest`, 4 `SuggestionProviderPrivacyGuardTest`, 1
`TimeOfDaySuggestionProviderTest`, 3 `UsageSuggestionProviderTest`). `git status` shows the new
`suggestions/` package in `:data:repository` (main + test), `SuggestionsProvidesModule.kt` in `:app`, and
edits to `PermissionFeature.kt`/`PermissionRationale.kt`/`AndroidManifest.xml`/`PreferencesKeys.kt`
(comment-only) — no other production file touched.

**Device-pending (carried to Phase-7 Tracking, independent of V/W).** On SM-A325F: real
`CalendarSuggestionProvider`/`LocationSuggestionProvider` reads against the live `CalendarContract`/
`LocationManager`, and the calendar/location grant/deny/permanently-denied request-flow UX — alongside
the inherited Block-T recognizer (OQ#4) and the Phase 5/6 device debt.

**Subsequent work split:** the user-facing launcher surface is now closed by Block W; **Block V** (ONNX
`TextEmbedder` impl + semantic re-rank) remains a separate model/runtime track gated on OQ#3 — not a blocker
for shipping the current suggestions/voice surface.

### ADR 2026-07-01 — Block W complete; Phase 7 user-facing close shipped, Block V stays separate

**Status.** Done 2026-07-01 (Block W, W1–W7). The already-implemented **W-lite** surface was reviewed first:
no critical architectural regressions found. The single-owner invariant holds (`LauncherViewModel` /
`LauncherUiState.suggestions` only; no `SuggestionsViewModel`), cache-first then fresh-supersede ordering is
implemented in the host VM, `:feature:suggestions` stays stateless/UI-only, launcher-home rendering introduces
no `feature→feature` dependency, and suggestion taps reuse the launcher's existing launch/navigation path
without creating a fourth engine/matcher concern. One non-blocking note remains: `aiSuggestionsEnabled` is
sampled on VM init rather than observed live, so a dynamic toggle is reflected on the next VM/app start.

**W proper delivered.**
- `SuggestionPrecomputeWorker : CoroutineWorker @HiltWorker` in `:app` — thin shell over
  `SuggestionEngine.refresh()`, gated fail-closed by `SuggestionPrecomputeGate`. Scheduling is periodic
  (~24h), unique, idempotent (`enqueueUniquePeriodicWork(..., UPDATE, ...)`), and constrained with
  `batteryNotLow` + `storageNotLow` + `deviceIdle`, **no network**. Gate-before-enqueue is explicit:
  `aiSuggestionsEnabled == false` or `DeviceProfile.LOW_END` cancels the precompute work instead of leaving a
  stale periodic request behind. Runtime execution also exits early under battery saver via
  `DeviceProfileProvider.capability().batteryOk`.
- `UsageCleanupWorker : CoroutineWorker @HiltWorker` in `:app` — weekly, unique periodic maintenance that
  prunes stale usage-history rows through the `UsageHistoryRepository` port (`cleanupOlderThan(cutoff)`),
  keeping Room details out of `:app`. Row-count caps on write remain intact; this is the time-based
  complement.
- `SuggestionsWorkScheduler` + tiny `UniquePeriodicWorkScheduler` facade — startup/boot orchestration stays
  unit-testable without a real WorkManager instance while production still delegates directly to
  `WorkManager`.
- `BootCompletedReceiver` (`RECEIVE_BOOT_COMPLETED`) — best-effort warmup that re-applies the same scheduler
  logic after reboot. `SidrLauncherApp.onCreate()` now also fire-and-forget schedules the periodic jobs on
  `@ApplicationScope`, off the cold path.

**Tests / verification added.**
- App JVM tests cover schedule gating (`aiSuggestionsEnabled` off / `LOW_END`), stable unique-work names +
  UPDATE policy (idempotent rerun), and the battery-saver runtime gate.
- Data JVM coverage for `UsageHistoryRepositoryImpl.cleanupOlderThan(...)` proves only stale usage rows are
  removed.
- Existing launcher tests already cover the W-lite cache-restore-then-supersede ordering and suggestion-tap
  routing, so Block W proper extended rather than duplicated those proofs.

**Outcome.** Phase 7's **user-facing** scope is now closed: voice input, contextual suggestions surface,
background pre-compute/cleanup, and boot warmup are all present. **Block V remains open by choice** as the
separate semantic-rerank/model-host track (OQ#3), not as a dependency of the shipped launcher surface.

### ADR 2026-07-01 — Block V inert runtime seam implemented; OQ#3 remains open

**Status.** Code-complete as an inert seam, device/model acceptance pending. Block V is not local
generation and does not touch `IntentMatcher` or `GenerativeAiEngine` semantics; it is only an optional
local embedding encoder for semantic suggestion re-rank.

**Delivered.**
- `OnnxTextEmbedder : TextEmbedder` in `:data:ai-local`, mirroring `OnnxIntentClassifier`'s runtime style:
  lazy single `Mutex`-guarded ORT session on `Dispatchers.Default`, per-call `LocalInferenceGate` re-check,
  `LocalModelFiles` model/vocab resolution before ORT, `OperationResult.Failure` graceful degrade,
  `AutoCloseable` + `SessionLifecycle`, and no user text in logs.
- `ModelDownloadConfig.EMBEDDING_PENDING` with blank URL/hash and `isPinned=false`; the existing
  half-pinned guard still rejects URL-without-hash or hash-without-URL. The NLU config remains unchanged.
- `SemanticSuggestionRanker : SuggestionRanker` in `:data:repository`, wired as the unqualified
  `SuggestionRanker` binding. It always computes heuristic order first and consults `TextEmbedder` only
  when the embedding config is pinned, a typed prefix exists, the model is available, and the device gate
  allows local inference. No model, gate-off, embedder failure, empty vectors, non-finite vectors,
  dimension mismatch, or invalid cosine all return the heuristic order verbatim.
- `SessionLifecycle` injection in `SidrLauncherApp` is now a Hilt `Set<SessionLifecycle>` multibinding:
  both `OnnxIntentClassifier` and `OnnxTextEmbedder` release on `onTrimMemory(>=TRIM_MEMORY_BACKGROUND)`
  and `onLowMemory`.

**Explicitly not solved.** OQ#3 remains unresolved. No production embedding model, hosting URL, SHA-256,
vocab asset, or final ONNX output contract was guessed or pinned. `EMBEDDING_PENDING.isPinned == false`
keeps the semantic layer inert in shipping builds, so heuristic ranking remains the baseline.

**Tests / verification.** JVM coverage added for semantic parity (pending model, gate-off, missing
availability, embedder failure, empty/invalid/dimension-mismatched vectors), positive eligible re-rank,
embedder gate/missing-file graceful failure, `ModelDownloadConfig` pending/half-pinned guard, and lifecycle
set release. `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` are the required green checks.

**Device-pending acceptance.** Once OQ#3 closes: real embedding model load on SM-A325F, `<150ms`
MID_RANGE embedding/re-rank measurement, and memory/co-residency check with the NLU session. If measured
combined footprint breaches the accepted budget, revisit the frozen-forward single-resident arbiter.

**Follow-up debt before making OQ#3 real.**
- `SemanticSuggestionRanker` currently bridges the synchronous `SuggestionRanker` port to suspend
  `TextEmbedder` with `runBlocking`. This is acceptable only while `EMBEDDING_PENDING.isPinned == false`
  keeps the path inert; before pinning a real embedding model, revisit the suspend/background boundary so
  semantic ranking cannot block an inappropriate caller thread.
- Production `SuggestionEngineImpl` currently builds `SuggestionContext` without `typedPrefix`. The semantic
  path therefore will not activate for live typed-prefix UX until a separate owner/boundary decision feeds
  the transient prefix into refresh/ranking without persisting sensitive raw text.
- Embedding provisioning is config-only/inert. Closing OQ#3 needs either generalized multi-model
  provisioning or a separate embedding model manager/scheduler; do not silently reuse the NLU-only
  `ModelManager` wiring as if it handled both artifacts.
- Device acceptance remains pending: real embedding model load, `<150ms` MID_RANGE measurement, and
  memory/co-residency check with the NLU session.

### ADR 2026-07-01 — Device acceptance pass (partial) on SM-A325F / Android 13

**Scope.** Honest device-only verification for already-shipped Phase 5/6/7 code on the real target
device (`SM-A325F`, Android `13`, SDK `33`). No architecture changes, no new runtime/model track,
no OQ#1/OQ#2/OQ#3 resolution.

**Baseline and install.**
- `./gradlew testDebugUnitTest` — **BUILD SUCCESSFUL**
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL**
- `codegraph sync .` — up to date
- `adb devices -l` — real target confirmed: `RF8R705H38F ... model:SM_A325F`
- `./gradlew installDebug` reached device install; package presence confirmed separately on device:
  `pm list packages` / `pm path com.sidr.launcher` showed the debug app installed.
- `adb shell am start -W -n com.sidr.launcher/.LauncherActivity` — **launch-smoke PASS only**:
  cold starts completed (`TotalTime: 1381`, later `1026`; hot relaunch `248`), but the `<400ms`
  cold-start performance budget remains **PENDING/perf-risk**.

**Phase 5 results.**
- **Block J PASS on device.** `SecretStoreInstrumentedTest` executed on SM-A325F against the real
  Keystore and finished `OK (3 tests)`:
  `put_thenRestart_get_returnsValue`, `twoProviders_areIsolated`,
  `strongBoxFallback_doesNotCrash_andRoundTrips`.
- Device log evidence showed real keymaster activity (`keymaster_tee`) during the run.
- **Block N remains pending-config/manual.** This session did **not** honestly verify live streaming,
  offline static fallback UI, cancel/retry, or rotation-mid-stream. Provider configuration/key was not
  established as part of the pass, and the interactive UI path was not fully observable from the shell.

**Phase 6 results.**
- `OnnxIntentClassifierInstrumentedTest` was executed directly on device after installing the
  `androidTest` APK. The AndroidJUnitRunner reported `OK (3 tests)`, but device `logcat` showed the real
  outcome: all three cases assumption-skipped in `setUp()` with
  `AssumptionViolatedException: device-pending: nlu/intent.onnx not bundled (see tools/nlu/README.md)`.
- Therefore this is recorded as **PENDING/model-blocked**, not pass and not failure: the real
  `intent.onnx` artifact is absent, so `<150ms` CPU latency, NNAPI→CPU fallback, and true on-device NLU
  acceptance remain blocked on OQ#1/OQ#2.
- Trim-memory is **PARTIAL**, not full pass. `adb shell am send-trim-memory com.sidr.launcher
  RUNNING_CRITICAL` and a backgrounded `BACKGROUND` / `COMPLETE` pass left `pidof
  com.sidr.launcher` alive, and filtered `logcat` showed
  `OpenGLRenderer: trimMemory(TRIM_MEMORY_COMPLETE)::destroyRenderingContext` with no
  `AndroidRuntime` fatal for the process. This proves a real no-crash / renderer-release path, but
  the app was still in **no-model state**, so final proof of ONNX/native session release must wait
  until a real session is actually loaded.
- No evidence was found of a scheduled model-download job on the device after app launch, which is
  consistent with `ModelDownloadConfig.INTENT_NLU_PENDING.isPinned == false`; however this was not
  promoted to a standalone PASS because the shell-only evidence is indirect.

**Phase 7 results.**
- Interactive/manual UI checks were advanced to a **real unlocked Sidr surface** and still remain only
  partially closed:
  - **Launcher home / flag-off suggestions PASS.** The home surface rendered a plain app grid with the
    `Type a command…` field and **no suggestions row**, matching `FeatureFlags.aiSuggestionsEnabled ==
    false`. `dumpsys jobscheduler com.sidr.launcher` showed `Registered ... None` / `Pending queue:
    None`, consistent with no suggestion-precompute scheduling while the flag is off.
  - **Assistant remains PENDING-model / PENDING-config.** The launcher command field is reachable, but
    the shipped rule-only table does **not** contain an assistant command entry (`RuleBasedIntentMatcher`
    simple commands are `show apps`, `clear`, `help` only). With no real NLU model bundled, an honest
    device-run path into `Routes.Assistant` was not available from launcher home. Therefore no-provider
    state, offline `StaticFallbackEngine`, cancel/retry, and rotation-mid-stream remain unverified.
  - **Voice is PARTIAL / blocked.** Package-manager queries resolved recognizer components on device
    (`cmd package query-services -a android.speech.RecognitionService` and
    `query-activities -a android.speech.action.RECOGNIZE_SPEECH`), but the launcher home did **not**
    show the mic affordance. Keyboard fallback is therefore the only verified path; RECORD_AUDIO
    education/request, active listening, final transcript submission, and denied/unavailable UX remain
    device-pending behind this mismatch.
  - **Suggestions flag-on UI is PENDING/blocker.** CodeGraph review found no user-facing settings/toggle
    path that enables `aiSuggestionsEnabled`; without a real in-app toggle, row-render / calendar /
    location granted-denied UI acceptance could not be verified honestly from the shipped UI.
  - **WorkManager is PARTIAL.** Flag-off / no-precompute behavior is supported by device evidence
    above; unique/idempotent re-enqueue, LOW_END gating, and boot re-enqueue remain device-pending.

**Net status after this pass.**
- **PASS:** baseline JVM build/tests, device detection, APK install presence, launcher launch-smoke,
  Block-J real Keystore instrumentation, flag-off launcher home rendering, no-precompute-at-flag-off shell evidence.
- **PARTIAL:** trim-memory release/no-crash path under no-model conditions; voice availability/device
  UX mismatch (recognizer components present, mic affordance absent); WorkManager device acceptance
  beyond flag-off gate.
- **PENDING/config/manual:** Block-N streaming/offline/cancel/rotation; assistant UI/device checks once
  a real launcher entry path and/or provider config exists; Block-U/Block-W manual permission UX.
- **PENDING/model-blocked:** Phase 6 real ONNX/NLU acceptance (missing `intent.onnx`, and bundled
  `vocab.txt` remains intentionally absent per the asset README); assistant route from no-model
  launcher home; OQ#3 embedding model acceptance.

### ADR 2026-07-02 — Device Acceptance Round 2 (SM-A325F / Android 13)

**Scope.** Honest device-only verification for already-shipped Phase 5/6/7 code on the real target
device (`SM-A325F`, Android `13`, SDK `33`). No runtime changes, no model-training/export work, no
OQ#1/OQ#2/OQ#3 resolution.

**Baseline / install evidence.**
- `./gradlew testDebugUnitTest` — **BUILD SUCCESSFUL**
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL**
- `codegraph sync .` — **Already up to date**
- `adb devices -l` — `RF8R705H38F ... model:SM_A325F`
- `adb shell dumpsys package com.sidr.launcher` — package installed, debuggable, `lastUpdateTime=2026-07-02 13:57:42`
- Cold-start smoke only: `adb shell am start -S -W -n com.sidr.launcher/.LauncherActivity` observed
  `TotalTime: 3601` and later `2215`; keep the `<400ms` budget **PENDING/perf-risk**

**Phase 7 suggestions.**
- `settings` command → launcher settings screen **PASS** (device screenshot evidence)
- `AI suggestions` toggle **ON** → suggestions row rendered on home **PASS**
- toggle **OFF** → suggestions row cleared **PASS**
- suggestion tap routing **PASS**: the `Настройки` chip launched the system Settings app, proving the
  row is wired to the normal launch/navigation path
- WorkManager ON/OFF gate **PASS** with direct device evidence:
  - flag **ON**: local WorkManager DB contained `sidr_usage_cleanup` + `sidr_suggestion_precompute`,
    both in `state=0`; `dumpsys jobscheduler com.sidr.launcher` showed `2` Sidr jobs
  - flag **OFF**: local WorkManager DB kept `sidr_usage_cleanup` in `state=0`, moved
    `sidr_suggestion_precompute` to `state=5`, and `dumpsys jobscheduler com.sidr.launcher` dropped to
    `1` Sidr job
  - the device reported `Battery not low: false`, so the queued jobs stayed constrained rather than
    executing; this matches the contract and is not a failure

**Phase 7 voice.**
- Mic affordance on launcher home **PASS** (visible on SM-A325F)
- Without `RECORD_AUDIO`, mic tap → `Voice commands` permission-education screen **PASS**
- In-app enable flow ended with `android.permission.RECORD_AUDIO: granted=true` in `dumpsys package`
- Post-grant recognizer run is **PARTIAL** but materially advanced: one run produced final transcript
  `она такая группа` in the launcher input plus the normal feedback
  `Unknown command. Try: open <app>, search <query>`, which confirms the unchanged
  `Final -> onCommandSubmitted` path on device
- `Ready` / `Partial` intermediate states were **not directly evidenced** and remain open; do not
  promote this to a full recognizer PASS

**Phase 5 / Assistant.**
- Launcher command `assistant` → Assistant setup screen **PASS** (no NLU/model dependency)
- Real streaming / terminal state / retry / cancel / offline fallback remain **PENDING-CONFIG**:
  the setup form was blank (`Base URL`, `Model`, `API Key` empty), so no provider-backed session was
  honestly runnable

**Phase 6 / model.**
- Real local NLU remains **PENDING-MODEL**. Searches for `intent.onnx` / `vocab.txt` returned no files
  in the repo and no files in the app sandbox on device; do not claim on-device NLU acceptance

**Net effect.** This round closes the Phase-7 launcher-side acceptance blockers (sanctioned settings
path, suggestions ON/OFF surface, suggestion routing, mic visibility, assistant entry path) while
keeping the remaining config/model/perf debts explicitly open.

### ADR 2026-07-02 — Device Acceptance Round 3 (SM-A325F / Android 13)

**Scope.** Agent-directed / human-hands device pass targeting the items Round 2 left open, with a
**BYOK OpenRouter key entered in-app** (never committed/logged). Focus: assistant real streaming
(brief C.1), a rigorous cold-start measure + root-cause (Part D), a clean trim BACKGROUND/COMPLETE
run (Part E), and calendar/location opt-in UX + privacy (C.4). No runtime/model work; two small
findings recorded, not fixed (per brief boundary).

**C.1 Assistant — real streaming end-to-end = PASS (the last big AI blocker).** Previously
PENDING-CONFIG; now verified against a live provider (`openrouter.ai`, model `openai/gpt-4o-mini`):
- No-config → provider-setup form shown (no crash).
- `saveProvider` persists config to `sidr_preferences` (`ai_provider_id/base_url/model` =
  `openrouter.ai` / `https://openrouter.ai/api/v1` / `openai/gpt-4o-mini`) and the key to the
  encrypted `sidr_secrets` store (slot `ai_api_key_openrouter.ai`, 172-byte blob) — the key is
  **absent from `sidr_preferences`**, confirming store separation.
- **Real streaming:** first model hit a provider-side `429` (mapped correctly to a Retry-able
  "Rate limited" error — external throttle, not an app bug); switching model → **tokens streamed**.
  `keystore2 create_operation Success` on each send confirms the stored key **decrypts and is used**
  (crypto round-trip is sound).
- **Offline static fallback = PASS:** `svc wifi/data disable` → send → `StaticFallbackEngine` canned
  reply ("I can't reach an AI service right now…"), no crash / no hung spinner.
- **cancel / retry / rotation = PASS:** back-nav aborts the stream (no crash); retry reruns a single
  stream (no double); rotate mid-stream → reply survives and streaming continues.

**Two findings (recorded, NOT fixed — small, per brief boundary):**
1. **`keySet` indicator race (cosmetic).** In `AssistantViewModel.saveProvider`, `setActiveConfig`
   (prefs write) re-triggers the `activeConfig().collect` `keySet` recompute **before**
   `secretStore.put` stores the key, and nothing re-triggers the collector after the put — so the
   "API Key (set)" indicator stays false even though the key is saved and usable. Cosmetic only
   (streaming works); fix is to re-read/reflect keySet after `put`.
2. **String typo:** RateLimited error reads `Please wait and retray` → should be `retry`.

**Part D Cold-start — rigorous measure + root-cause = PENDING / PERF-RISK.** `am force-stop` + `am
start -S -W` ×6 cold: `2172, 1751, 1833, 1718, 1711, 1728` ms → **min 1711 / median ~1740 / max
2172**; hot relaunch `TotalTime 0 / WaitTime 16`. Median **~1740ms vs the `<400ms` budget** (~4.3×
over). `WaitTime≈TotalTime` cold + hot≈0 localizes the cost to `onCreate` + first frame, not Activity
redraw. **Root-cause hypothesis (not fixed here):** for a launcher, `PackageManager` app enumeration
+ label/icon load on the first-frame path likely dominates, over Hilt graph construction,
`SidrDatabase` open + usage-sorted grid query, first DataStore read, and the fire-and-forget
`ensureModel()` coroutine. A profiled attribution (`am start --start-profiler` / Perfetto) is owed
before any fix.

**Part E Trim BACKGROUND/COMPLETE = PASS (no-crash; upgraded from Round-1/2 PARTIAL).** Backgrounded
(Settings foregrounded) → `am send-trim-memory … BACKGROUND` then `COMPLETE`. Process stayed alive
(pid logging after the trims); `OpenGLRenderer … trimMemory(TRIM_MEMORY_COMPLETE)::destroyRenderingContext`
(renderer release); and **`OnnxIntentClassifier: ONNX session released (trim).` ×2** — the R2.5
`onTrimMemory` → `SessionLifecycle.releaseResources()` hook fired for **both** Block-V
`Set<SessionLifecycle>` seams (NLU + embedder), device-proven for the first time. No `AndroidRuntime`
/ `FATAL`. Honest caveat unchanged: with **no model bundled** there is no native ONNX session to tear
down, so native-session release stays structurally unprovable until OQ#1/#2 — this proves the
**release wiring fires and the app survives**, not native teardown.

**C.4 Calendar / Location opt-in + privacy = PASS.** Baseline `READ_CALENDAR` /
`ACCESS_FINE_LOCATION` = `granted=false`; suggestions still rendered from time/usage
(degrade-not-block). After `pm grant` of both + a suggestions off→on refresh, the persisted cache
(`sug_cached_list_json`) held exactly:
`[{"label":"Clock","actionId":"com.android.deskclock"},{"label":"Nearby places","actionId":"com.google.android.apps.maps"},{"label":"Music","actionId":"com.android.music"}]`.
The location provider emitted a **single fixed generic** `"Nearby places"` → maps app — **no
coordinate, no raw place**. No calendar-generic entry (no upcoming event on device → provider
returned empty). **Privacy: nothing sensitive leaked** — neither the cache nor logcat contained a raw
event title, coordinate, or `lat/lon`; only fixed generic labels + package `actionId`s.

**Still open after Round 3.** C.2 voice recognizer `Ready`/`Partial` intermediate states (OQ#4, not
evidenced); C.5 boot-warmup re-enqueue after reboot; Part D cold-start **fix** (profiled attribution
+ deferral of first-frame work); the two C.1 findings above; and the model-gated NLU acceptance
(OQ#1/#2). **Net effect:** C.1 — the only working AI path — is now honestly **PASS end-to-end**,
retiring the largest outstanding acceptance blocker; the residual debt is perf (cold-start), voice
intermediate states, boot-warmup, and the model track.

## ADR 2026-07-03 — Phase UX Block X1 complete (design-system foundation in `core/ui`)

**Context.** Phase UX (home redesign) starts here. `core/ui` was reserved as "Design system / theme"
in the module contract but was effectively empty; `LauncherActivity` wrapped the app in a bare
`MaterialTheme {}`. Block X1 fills `core/ui` with a pragmatic M3 theme + a small component library,
**presentation-only** — no behavior change, no screen rewired yet (that lands in X2+). Forks: **U4 =
(b) pragmatic M3 theme + core components** (no bespoke token engine); **UX-Q4 = neutral M3 palette +
Material You dynamic colour on API 31+**.

**Theme (`core/ui/.../theme/`).**
- `Color.kt` — neutral indigo-seeded M3 palette; `SidrLightColorScheme` / `SidrDarkColorScheme` as
  the **static fallback** (raw colours kept `private`; consumers read semantic roles only).
- `Type.kt` — `SidrTypography`: M3 scale on the platform default font (no bundled font), with a
  SemiBold `titleLarge` and a wide-tracked `labelSmall` for section headers.
- `Shape.kt` — `SidrShapes` (4/8/12/16/28dp).
- `Spacing.kt` — flat `object Spacing` (xs..xxl) + `object Sizes` (`minTouchTarget`/`appIcon`/
  `appTile`/`icon`/`stateGlyph`). A plain object, **not** a CompositionLocal (single density scale; U4).
- `Theme.kt` — `SidrTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true, content)`: dynamic
  colour on API 31+, else the static schemes; typography + shapes are always the Sidr scale.

**Components (`core/ui/.../component/`).** `SidrScaffold` (themed Scaffold defaults), `SidrSearchField`
(the unified search/command field — leading search glyph, submit → `onSubmit`, trailing precedence
Clear-when-typed → Mic-when-available; the reusable replacement for `CommandInputBar` in X2/X4),
`AppTile` (icon **slot** + one-line label — the slot keeps `PackageManager`/`Drawable`/`InstalledApp`
loading in the feature layer so `core/ui` takes **no** `domain`/`data` edge), `SectionHeader` (a11y
`heading()`), `TopBarIcon` (48dp touch target, required non-null `contentDescription` — these icons
are the *only* Settings/Assistant discovery affordance in X2), `EmptyState`, `ErrorState` (optional
`onRetry`, mirrors `UiState.Error(retryable)` shape without depending on it). Each has light/dark
`@Preview`s.

**Icons decision (owner-approved).** Use `androidx.compose.material.icons.Icons` **core set** —
already transitive via `material3` (`material-icons-core` 1.7.6), used by `feature/assistant` — so
Settings/Search/Clear cost **no new dependency**. The core set has **no `Mic`** (extended-only), so
rather than pull `material-icons-extended`, the mic is a bundled vector drawable
`res/drawable/ic_mic_24.xml` (standard Material mic path, no `?attr/colorControlNormal` tint — this
is a Compose-only theme with no AppCompat attrs; `Icon` tints it). **Zero new Gradle deps.**

**Integration.** `LauncherActivity`: `MaterialTheme {}` → `SidrTheme {}` (the single app-wide theme
application point; behavior unchanged). `core/ui/build.gradle.kts`: added
`debugImplementation(libs.compose.ui.tooling)` for `@Preview` rendering only.

**Hard-rules compliance.** `core/ui` still depends on **`core/common` only** (+ Compose); **no**
`domain`/`data`/`feature` edge (AppTile's icon slot is the load-bearing choice that preserves this).
No `feature→feature`. No behavior change; no `IntentMatcher`/`GenerativeAiEngine`/`SuggestionEngine`/
`HandleUserCommandUseCase` touch. No new JVM tests (pure presentation — previews are the visual check;
existing suites unchanged and green). Round-3 findings (keySet race, `retray`→`retry`) deliberately
**not** touched here — they belong to X5/X6.

**Verification.** `:core:ui:assembleDebug` green; full `assembleDebug` + `testDebugUnitTest` **BUILD
SUCCESSFUL** (no regressions). **Next = Block X2** (home declutter: top-bar Settings/Assistant icons,
`SidrSearchField` wrapping today's command bar + mic, Favorites from `UsageHistoryRepository`, remove
`AppGrid` from home).

## ADR 2026-07-03 — Phase UX Block X2 complete (home redesign / declutter)

**Context.** Home = the full installed-app grid + a bottom command bar; Settings/Assistant were
reachable **only** by typing commands. Block X2 rebuilds the home surface into the decluttered,
search-first layout from the plan (§3): a lightweight top bar with **discoverable** Settings +
Assistant icons, the unified `SidrSearchField`, the unchanged Suggestions row, a small **Favorites**
row (top-N most-used), and an **All apps** affordance — and **removes `AppGrid` from home**. Forks
already decided (U1 minimal home + drawer, U2 button-first, U3 auto top-N favorites, U6 field submits
commands byte-for-byte in X2; live filtering is X4).

**`core/ui` (additive, allowed by handoff §5).** New bundled vector `res/drawable/ic_assistant_24.xml`
(auto-awesome sparkle, `@android:color/white`, no `?attr/colorControlNormal` — Compose tints it;
modeled on `ic_mic_24.xml`) because the core Material icon set has no Assistant glyph. `TopBarIcon`
gained a **`Painter` overload** next to the `ImageVector` one (same 48dp target / 24dp glyph) so the
bundled vector can be passed. **Zero new Gradle deps.**

**`core/common`.** New route `Routes.AppDrawer.ROUTE = "app_drawer"`. The home "All apps" button
navigates here now; the `composable(...)` destination lands in **X3**. Until then `AppNavHost`'s
existing `handleNavigationEvent` safe-fallback (unknown route → `IllegalArgumentException` → back to
`Launcher`) covers the tap — the deliberate interim behaviour (handoff §3), not a bug.

**`feature/launcher`.** `LauncherUiState` gained `favorites: List<InstalledApp>`. `LauncherViewModel`
derives it inside the **existing** `combine(_rawAppsResult, getUsageRecords(), _suggestions)` Success
branch via pure `deriveFavorites(usageRecords, sortedApps)`: walk usage records (most-used-first per
the repository contract) → map to the installed app by `packageName` → drop records for uninstalled
apps → `take(FAVORITES_COUNT = 8)`; empty history → empty favorites (alphabetical fallback deferred to
X6). **No new VM constructor dependency** (`usageHistoryRepository` was already injected for
`recordUsage`). The full `apps` list stays loaded + `sortByUsage`'d — the X3 drawer and
`onSuggestionClicked`'s package resolution still consume it; it just isn't rendered as a grid.
`LauncherScreen` rebuilt on `SidrScaffold`: top bar = right-aligned `TopBarIcon(Settings)` →
`navigateTo(Routes.Settings.ROUTE)` + `TopBarIcon(Assistant painter)` → `navigateTo(Routes.Assistant.ROUTE)`;
`SidrSearchField` replaces the private `CommandInputBar` (`onMicTap` logic copied **verbatim** —
`checkSelfPermission(RECORD_AUDIO)` → `startVoiceInput()` else route to VOICE_INPUT education);
`CommandFeedbackArea` moved directly under the field. `SuccessContent → HomeContent`: suggestions
(unchanged) + `SectionHeader("Favorites")` + a `LazyRow` of `AppTile` (icon slot fed by the retained
`rememberAppIcon`/`Drawable.toImageBitmap` helpers with the monogram fallback) + a bottom-anchored
"All apps" `TextButton`. **Deleted** `AppGrid`, `AppItem`, `CommandInputBar`. **Layout choice:** the
search field sits at the **top** (under the top bar), not bottom-anchored as before — matches the
search-first mockup; `imePadding()` retained.

**Hard-rules compliance.** `core/ui` still `core/common`-only — no `domain`/`data`/`feature` edge (the
`AppTile` icon slot keeps `PackageManager`/`InstalledApp` in the feature layer). No `feature→feature`.
VM emits `NavigationEvent` only; never touches `NavHostController`. `domain` untouched;
`HandleUserCommandUseCase`/`IntentMatcher`/`GenerativeAiEngine`/`SuggestionEngine` unchanged — typed
commands run byte-for-byte; launcher core stays offline. `:data:ai-local` inert; OQ#1/2/3 and the
Round-3 findings (keySet race, `retray`→`retry`) not touched (X5/X6).

**Verification.** **7 new JVM tests** in `LauncherViewModelTest` (favorites: top-N usage order,
uninstalled-exclusion, `FAVORITES_COUNT` cap, empty-history→empty; nav-events for Settings / Assistant /
AppDrawer); `:feature:launcher:testDebugUnitTest`, full `assembleDebug` (Hilt graph valid) +
`testDebugUnitTest` **BUILD SUCCESSFUL**. **Device-pending (SM-A325F, handoff §8):** home opens without
the icon wall, both top-bar icons reach Settings/Assistant, favorites visible, `open telegram` (typed)
works, mic affordance present. **Next = Block X3** (App Drawer — register `Routes.AppDrawer`, move the
full list there, alphabetical + fast-scroll).

## ADR 2026-07-03 — Phase UX Block X3 complete (App Drawer — all apps, on demand)

**Context.** X2 decluttered home and left the home "All apps" `TextButton` navigating to the
pre-registered `Routes.AppDrawer.ROUTE = "app_drawer"`, but no `composable(...)` existed → the tap hit
`AppNavHost`'s safe-fallback back to home. Block X3 registers the destination and delivers the full
installed-app list as a **separate App Drawer surface**: alphabetical with lettered section headers,
launch-on-tap that feeds Favorites/Suggestions. Forks were pre-decided in the handoff (U-topology:
drawer is a screen **inside `feature/launcher`**, no new module, no `feature→feature` edge; U2: button
opens it, swipe-up optional/deferred; U6: live filtering is X4 — X3 shows the whole list unfiltered).

**Two owner-confirmed decisions.**
- **launch/recordUsage duplication → copy, not shared use-case.** `AppDrawerViewModel.launchApp`/
  `recordUsage` is a ~10-line verbatim copy of `LauncherViewModel`'s (same `ActionExecutor` path, same
  `usageHistoryEnabled` gate, `CancellationException` re-thrown, non-critical errors swallowed). A
  shared `feature/launcher` use-case was judged premature for a two-caller path; keeps `:domain`
  untouched. Revisit if a third caller appears.
- **Fast-scroll → `stickyHeader` is the mandatory X3 mechanism; an A–Z side rail is an optional later
  touch, not an acceptance blocker.** Shipped sticky lettered headers; no side rail this block.

**`feature/launcher` (new files).**
- **`AppIcon.kt`** — `rememberAppIcon(packageName)` + `Drawable.toImageBitmap()` **moved verbatim out
  of `LauncherScreen.kt` and made `internal`** so both home (`AppTileIcon`) and drawer (`DrawerAppIcon`)
  reuse them. `LauncherScreen.kt` lost those two `private` helpers + 10 now-unused imports; behaviour
  unchanged. `core/ui` deliberately does **not** get these (PackageManager/Drawable is a feature
  concern — the reason `AppTile` takes the icon as a slot).
- **`AppDrawerUiState.kt`** — `AppDrawerUiState(sections)` + `DrawerSection(letter, apps)` + the **pure**
  `internal fun groupIntoSections(apps)`: sort by label case-insensitive (`String.CASE_INSENSITIVE_ORDER`),
  bucket by uppercased first letter (`LinkedHashMap` preserves A→Z after the sort), non-letter labels
  collapse into a single trailing `"#"` bucket (omitted when empty). Side-effect-free → unit-tested
  directly.
- **`AppDrawerViewModel.kt`** (`@HiltViewModel`) — injects only `InstalledAppsRepository`,
  `ActionExecutor`, `UsageHistoryRepository`, `FeatureFlagRepository`, `@IoDispatcher`. **No**
  `HandleUserCommandUseCase`/`IntentMatcher` (the drawer doesn't parse commands). `uiState:
  StateFlow<UiState<AppDrawerUiState>>` = `map` over the raw load result → `Loading`/`Error(retryable)`/
  `Empty`/`Success(groupIntoSections(...))`; `retry()` resets to `Loading` and reloads. Same
  `Channel<NavigationEvent>` pattern for `navigateBack()`. Error→UiError + isRetryable mirror
  `LauncherViewModel` exactly.
- **`AppDrawerScreen.kt`** — `SidrScaffold` top bar = `TopBarIcon(Icons.AutoMirrored.Filled.ArrowBack,
  "Back")` + "All apps" title; content switches on `UiState` (`CircularProgressIndicator` / `EmptyState` /
  `ErrorState(onRetry = retry when retryable)` / `LazyColumn`). `LazyColumn` uses `stickyHeader` per
  section (`SectionHeader(letter)` on a themed background) + a compact `DrawerAppRow` (icon + label, one
  tappable target, `clearAndSetSemantics { contentDescription = label }`) — a list row scans better for
  A–Z than a grid tile. `stickyHeader` is a **member** of `LazyListScope` (not an importable
  extension) — called without an import.

**`:app`.** `AppNavHost` gains `composable(Routes.AppDrawer.ROUTE)`: `hiltViewModel<AppDrawerViewModel>()`,
a `LaunchedEffect` collecting `navigationEvents` → the shared `handleNavigationEvent`, and `onBack =
{ handleNavigationEvent(navController, NavigateBack) }` — the Settings-destination template. This retires
the X2 interim safe-fallback for the "All apps" tap.

**Hard-rules compliance.** No `feature→feature` edge (drawer lives in `feature/launcher`); single
`NavHost` in `:app`; VM emits `NavigationEvent` only, never touches `NavHostController`. `core/ui`
unchanged (icons stay a feature concern via the slot). `domain` untouched —
`HandleUserCommandUseCase`/`IntentMatcher`/`SuggestionEngine`/`GenerativeAiEngine` unchanged; typed home
commands still run byte-for-byte; drawer is fully offline (PackageManager + local launch). `:data:ai-local`
inert; OQ#1/2/3 and the Round-3 findings (keySet race, `retray`→`retry`) not touched (X5/X6); live
filtering left to X4.

**Verification.** **15 new JVM tests** — `AppDrawerGroupingTest` (6: empty, in-section sort, A→Z order,
case-insensitive first letter, trailing `#` bucket, `#` omitted when all-alpha) + `AppDrawerViewModelTest`
(9: Loading→Success sections, Empty, retryable vs non-retryable Error, retry reloads, launch-through-
executor + usage write, flag-gate suppresses the write, failed launch skips the write, `navigateBack`
emits `NavigateBack`). `:feature:launcher:testDebugUnitTest`, full `assembleDebug` (Hilt graph valid) +
`testDebugUnitTest` **BUILD SUCCESSFUL**. **Device-pending (SM-A325F, handoff §8):** "All apps" opens the
drawer with every app, alphabetical + smooth scroll, tap launches, Back returns home, and a
drawer-launched app rises into home Favorites. **Next = Block X4** (search ⇄ command unification — live
app filtering).

## ADR 2026-07-03 — Phase UX Block X4 complete (search ⇄ command unification — live app filtering)

**Context.** X3 shipped the App Drawer showing the full installed-app list unfiltered. Block X4 adds
the live filter (fork U6: one field filters apps *and* still submits commands, no regression). Three
forks were surfaced to the owner before code and all landed on the recommended option.

**Owner-decided forks.**
- **X4-A (where the filter lives) → (a) filter in the App Drawer.** Home stays command-first (exact X2
  behaviour, zero regression); the drawer already owns the full `InstalledApp` list + `groupIntoSections`,
  so the filter is a pure derivation over data already loaded — the cheapest, lowest-risk slice. Options
  (b) filter on home and (c) both were rejected as more scope for an MVP.
- **Drawer IME submit → launch the top filtered match.** Enter launches the first *alphabetical* match
  (the first app of the first section) via the existing launch path; no-op when nothing matches. This
  keeps the drawer **command-free** — no `HandleUserCommandUseCase`/`IntentMatcher` added, preserving the
  X3 topology decision. Command dispatch stays exclusively on home.
- **"Ask assistant" affordance → deferred to X6.** Routing to the assistant *without* prefill is a
  dead-end (the user's typed question is lost on arrival), and a real prompt-prefill needs a deliberate
  nav-arg design (an optional prompt consumed once, **never** persisted to saved state — `AssistantViewModel`
  intentionally has no `SavedStateHandle`). Bundled with X6 polish; the "key never in saved state"
  invariant is untouched by X4.

**`feature/launcher` changes.**
- **`AppDrawerUiState.kt`** — new **pure** `internal fun filterApps(apps, query)` next to
  `groupIntoSections`: `query.trim()`; blank/whitespace → the list unchanged (no filter); else keep apps
  whose `label` `contains` the needle **case-insensitively**. Substring (not prefix) matching, so "tele"
  finds "Telegram" and mid-word fragments match too. Side-effect-free → unit-tested directly.
- **`AppDrawerViewModel.kt`** — added `_query: MutableStateFlow<String>` + `onQueryChanged(text)`, exposed
  `val query: StateFlow<String> = _query.asStateFlow()`. `uiState` switched from `map` over
  `_rawAppsResult` to `combine(_rawAppsResult, _query)` → on `Success`, `groupIntoSections(filterApps(...))`;
  empty sections still collapse to `UiState.Empty` (covering both "no apps installed" and "query matched
  nothing" — the screen picks the message from the current query). New `onQuerySubmitted()`: reads the
  `Success` value, takes `groupIntoSections(filterApps(...)).firstOrNull()?.apps?.firstOrNull()` (the top
  *visible* match), and launches it through the existing `launchApp`/`recordUsage` path; no-op otherwise.
- **`AppDrawerScreen.kt`** — collects `query`; wraps the content in a `Column` + `imePadding()` with a
  `SidrSearchField` under the top bar (`value=query`, `onValueChange=onQueryChanged`,
  `onSubmit={onQuerySubmitted()}`, `showMic=false` — voice stays on home; built-in Clear;
  `placeholder="Search apps"`). The `Empty` branch message is keyed off `query.isBlank()` ("No apps
  found." vs "Nothing found."). Sticky lettered headers + the list are unchanged.

**Hard-rules compliance.** Filter is UI/VM derivation over the already-loaded list — no repository
re-hit, fully offline. `HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher`
**unchanged** → typed home commands run byte-for-byte. No `feature→feature` edge; single `NavHost` in
`:app`; VM emits `NavigationEvent` only. `core/ui` untouched (`SidrSearchField` already had the Clear +
`onSubmit` seam); 0 new Gradle deps. `:domain` pure; `:data:ai-local` inert; OQ#1/2/3 and the Round-3
findings (keySet race, `retray`→`retry`) not touched (X5/X6).

**Verification.** **10 new JVM tests** — `AppDrawerGroupingTest` +5 (`filterApps`: blank→full,
substring/case-insensitive, multi-match, no-match→empty, query trimmed) + `AppDrawerViewModelTest` +5
(query filters sections live, clear→full list, no-match→`Empty`, `onQuerySubmitted` launches the top
alphabetical match + records usage, submit no-op when nothing matches). `:feature:launcher:testDebugUnitTest`,
full `assembleDebug` (Hilt graph valid) + `testDebugUnitTest` **BUILD SUCCESSFUL**. **Device-pending
(SM-A325F, handoff §8, batched):** in the drawer typing filters apps live, Clear resets to the full list,
Enter launches the top match, tap launches, Back → home; on home a typed command (`open telegram`,
`settings`) still works. **Next = Block X5** (real `:feature:settings`).

## ADR 2026-07-03 — Phase UX Block X5 complete (real Settings surface — `:feature:settings`)

**Context.** Settings was a one-toggle stub (`com.sidr.launcher.settings.LauncherSettingsScreen` +
`LauncherSettingsViewModel`) in `:app`. Block X5 promotes it to a real, discoverable module reached by
the X2 top-bar icon. Forks U5 (own module) and U7 (in-Settings default-launcher helper) were
pre-decided by the owner (b); four X5-specific forks were surfaced before code and all landed on the
recommended option.

**Owner-decided forks.**
- **X5-A (how a feature re-syncs WorkManager) → (a) `:domain` port.** New
  `SuggestionScheduling { suspend fun ensureScheduled() }` in `:domain`; impl `SuggestionSchedulingImpl`
  in `:app` delegates to the existing `SuggestionsWorkScheduler`; Hilt-bound via
  `SuggestionSchedulingModule`. The AI-suggestions toggle keeps its Block-W behaviour (write flag →
  `ensureScheduled()`, synchronous) but reaches WorkManager through the port — **no `feature→:app`
  edge**, and the gate-before-enqueue contract is unchanged (the scheduler still re-applies the
  `aiSuggestionsEnabled`/`LOW_END`/battery-saver gate). Option (b) (app-side flag observer) was rejected
  as it makes the re-sync async relative to the tap and adds an observer.
- **X5-B (scope) → MVP slice.** Shipped: **theme** (system/light/dark), **AI suggestions** (existing
  flag), **Assistant provider entry** (nav only), **Set-as-default**. **Deferred to X6:** voice on/off
  and favorites-count — both need *new* pref fields + `PreferencesKeys`/`ALL_KEY_NAMES`/`PreferencesMapper`
  edits + guard + consumer wiring (`deriveFavorites` / mic gate). Deferring keeps X5 off persistence
  migrations, so **`PrivacyInventoryGuardTest` is untouched and green** (no new DataStore key).
- **X5-C (default-launcher helper) → (a) intent from the screen.** `launchDefaultLauncherSettings(context)`
  fires from `SettingsScreen` via `LocalContext`: API 29+ uses `RoleManager.createRequestRoleIntent(ROLE_HOME)`
  when `isRoleAvailable(ROLE_HOME)`, else `Settings.ACTION_HOME_SETTINGS`; `ActivityNotFoundException`
  swallowed (never crashes the launcher). It's a UI action, not business logic — no new port (mirrors how
  `:feature:permission_education` fires system intents).
- **X5-D (theme application) → composition-root observes prefs.** `LauncherActivity` field-injects
  `UserPreferencesRepository`, `collectAsState`s `getPreferences()`, maps `themeName`
  (`light→false`, `dark→true`, else `isSystemInDarkTheme()`) → `SidrTheme(darkTheme=…)`. `dynamicColor`
  stays on (Material You unchanged; only the light/dark scheme follows the choice). Theme wiring stays in
  `:app`/`core:ui`, not the feature.

**Changes.**
- **New `:feature:settings`** (`settings.gradle.kts` include + `:app` `implementation`), Compose + Hilt
  kapt, deps `core:ui`/`core:common`/`domain`/`core:testing` — **0 new Gradle deps**, cloned from the
  `feature/permission_education` template. `SettingsViewModel` (`@HiltViewModel`, deps
  `FeatureFlagRepository` + `UserPreferencesRepository` + `SuggestionScheduling` + `@IoDispatcher`):
  `uiState` = `combine(getFlags(), getPreferences(), saveError)`; `setAiSuggestionsEnabled` (flag write →
  `ensureScheduled()`, unchanged failure→`SAVE_ERROR`/no-resync semantics), `setThemeName`
  (write pref), `openAssistantProvider`/`navigateBack` via a `NavigationEvent` `Channel`
  (mirrors `AssistantViewModel`). `SettingsUiState` + `ThemeOption` (the `system|light|dark` labels).
  `SettingsScreen` is a stateless render on `SidrScaffold` + back `TopBarIcon` + `SectionHeader` + M3
  radio/switch/buttons.
- **`:domain`** gained the `SuggestionScheduling` port; **`:core:testing`** gained `FakeSuggestionScheduling`
  (call-count). **`:app`** gained `SuggestionSchedulingImpl` + `SuggestionSchedulingModule`, deleted the
  old `settings/*` screen+VM+test, rewired `AppNavHost.composable(Routes.Settings.ROUTE)` to
  `SettingsViewModel`/`SettingsScreen` + a `navigationEvents` `LaunchedEffect`, and wired
  `LauncherActivity` theme observation.

**Hard-rules compliance.** `SuggestionScheduling` interface in `:domain`, impl in `:app` (contract-in-
domain, impl-in-composition-root). No `feature→feature`, **no `feature→:app`**. Single `NavHost` in
`:app`; VM emits `NavigationEvent` only. Assistant key invariant untouched — Settings only *navigates* to
the existing provider form, never duplicates it. `HandleUserCommandUseCase`/`IntentMatcher`/suggestion +
WorkManager (gate-before-enqueue) contracts unchanged; `:domain` pure; `:data:ai-local` inert; OQ#1/2/3
and the Round-3 findings (keySet race, `retray`→`retry`) not touched.

**Verification.** **6 new JVM tests** (`SettingsViewModelTest`: enable→flag+re-sync, disable→re-sync,
flag-write-failure→error+no-resync, theme persist, theme-write-failure→error, assistant nav emits
`NavigateTo(Assistant)`). `:feature:settings:testDebugUnitTest`, full `assembleDebug` (Hilt graph valid)
+ `testDebugUnitTest` **BUILD SUCCESSFUL**. **Device-pending (SM-A325F, handoff §7, batched):** Settings
opens by icon; theme change applies (system/light/dark); toggles survive restart; "Set as default" opens
the correct system screen; "Assistant provider" routes to the form. **Next = Block X6** (polish, a11y,
first-run, deferred voice/favorites-count, device acceptance + cold-start re-measure).

## ADR 2026-07-03 — Phase UX Block X6 complete (polish, a11y, first-run nudge, deferred settings; Phase UX CLOSED)

**Context.** Final Phase UX block. It discharges X5's deferred settings (favorites-count + voice
toggle), adds a11y hygiene + `core/ui` state surfaces on home, a first-run "set as default" nudge, and
the X4-deferred "Ask assistant" prompt prefill. Five X6 forks were surfaced before code; the owner
confirmed the recommended option on each.

**Owner-decided forks.**
- **X6-A (placement + key names) → all three prefs in `UserPreferences`.** New fields
  `favoritesCount: Int = 8`, `micInputEnabled: Boolean = true`, `setupHintDismissed: Boolean = false`;
  DataStore keys `user_favorites_count`, `user_mic_input_enabled`, `user_setup_hint_dismissed` — all
  **denylist-clean** (the mic toggle deliberately avoids the forbidden term "voice"; verified against
  `PrivacyInventoryGuardTest`'s list `voice/query/search/location/calendar/history/conversation/message/
  transcript/secret/token/api`). All three added to `PreferencesMapper` (read-with-default + write) and
  to `ALL_KEY_NAMES`. Rationale: these are UI preferences, not capability flags — `UserPreferences`, not
  `FeatureFlags`.
- **X6-B (scope) → full block.** Deferred settings + a11y + `core/ui` empty/error surfaces + first-run
  nudge + assistant prefill, all shipped together. Motion kept minimal (no new animation deps).
  Cold-start = **re-measure only** (X6-E), no startup-path code.
- **X6-C (assistant prefill) → implement minimally.** `Routes.Assistant` gained an optional
  `prompt` nav-arg (`ROUTE_WITH_ARG = "assistant?prompt={prompt}"`, `routeFor(encodedPrompt)`); the
  `AppNavHost` reads it from the back-stack entry and passes `initialPrompt` to `AssistantScreen`, which
  seeds `ChatView`'s local input via `remember(initialPrompt)`. **Prefill only — never auto-sent, and it
  never touches `SavedStateHandle`** (the assistant deliberately holds none; the key/prompt invariant is
  intact). Source: a drawer "Ask assistant: \"<query>\"" affordance shown when the query is non-blank,
  which navigates via `viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(query)))` — the screen
  builds the encoded route (Android), the VM only emits the `NavigationEvent` (no Android/route-encoding
  in the VM). Bare `assistant` still matches the arg'd pattern, so every existing
  `navigateTo(Routes.Assistant.ROUTE)` call is unchanged.
- **X6-D (first-run nudge) → detect in the screen, flag in `UserPreferences`.** `LauncherScreen`
  computes `isDefaultLauncher(context)` (API 29+ `RoleManager.isRoleHeld(ROLE_HOME)`, else HOME-intent
  `resolveActivity` package compare; any failure → not-default so the nudge can still surface). The
  dismissible `SetupNudge` card ("Make Sidr your home screen" + a one-line type/search hint + "Set as
  default") shows on the home Success surface only when `!isDefaultLauncher && !setupHintDismissed`.
  Dismiss or CTA calls `viewModel.dismissSetupHint()` → persists `setupHintDismissed=true`; the CTA also
  fires `launchDefaultLauncherSettings(context)` (same intent path as Settings X5-C). One-shot: never
  resurfaces once persisted.
- **X6-E (cold-start) → re-measure only.** No Hilt/Room/DataStore startup rework in X6; the profiled fix
  stays a separate owed item. Re-measure (`am start -S -W ×N`) is part of the batched device pass.

**Changes.**
- **`:domain`** — `UserPreferences` +3 fields (data-class defaults only; module stays pure).
- **`:data:repository`** — `PreferencesKeys` +3 keys (+`intPreferencesKey` import) +3 in `ALL_KEY_NAMES`;
  `PreferencesMapper.toUserPreferences`/`writeUserPreferences` +3.
- **`:feature:settings`** — `SettingsUiState` +`favoritesCount`/`micInputEnabled` + `FAVORITES_COUNT_OPTIONS`
  (4/6/8/10); `SettingsViewModel` combine reads them + `setFavoritesCount`/`setMicInputEnabled` (write
  pref, failure→`SAVE_ERROR`, no-op when unchanged); `SettingsScreen` gains a HOME section (favorites
  `FilterChip` selector + a Voice-input `Switch`).
- **`:feature:launcher`** — `LauncherViewModel` injects `UserPreferencesRepository`, holds a hot
  `userPreferences` `StateFlow` (read-fail → defaults), exposes `showMic: StateFlow<Boolean>` =
  `micInputEnabled && recognizer available`; `deriveFavorites` takes `favoritesCount`; `startVoiceInput`
  early-returns when mic disabled; `dismissSetupHint()` added; `LauncherUiState` +`setupHintDismissed`;
  `const FAVORITES_COUNT` removed. `LauncherScreen` reads `showMic`, renders `SetupNudge`, routes
  empty/error through `core/ui` `EmptyState`/`ErrorState`, and the default-launcher helpers live here.
  `AppDrawerViewModel` +`navigateTo(route)`; `AppDrawerScreen` +"Ask assistant" affordance.
- **`:core:common`** — `Routes.Assistant` gained `ARG_PROMPT`/`ROUTE_WITH_ARG`/`routeFor` (pure string
  interpolation; caller URL-encodes).
- **`:feature:assistant`** — `AssistantScreen(initialPrompt)` seeds `ChatView`.
- **`:app`** — `AppNavHost` Assistant destination registered with the optional nullable `prompt` arg;
  reads it and passes `initialPrompt` to the screen.

**Hard-rules compliance.** `domain` pure (fields only). Interfaces in `domain`, impls in `data`/`app`.
No `feature→feature`, **no `feature→:app`** (drawer builds the route + VM emits `NavigationEvent`; app
reads the nav-arg). Single `NavHost` in `:app`. Assistant key/prompt never in `SavedStateHandle`.
`HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher` +
suggestion/WorkManager (gate-before-enqueue) contracts untouched; `:data:ai-local` inert; OQ#1/2/3 and
Round-3 findings (keySet race, `retray`→`retry`) not touched. **`PrivacyInventoryGuardTest` green** (3
new keys inventoried, denylist-clean).

**Verification.** New JVM tests: `SettingsViewModelTest` (favorites-count persist + write-failure, voice
toggle persist + write-failure), `LauncherViewModelTest` (custom `favoritesCount` caps favorites,
`showMic` true/false by pref, `startVoiceInput` no-op when disabled), `UserPreferencesRepositoryImplTest`
round-trip + restart extended to the 3 new fields. `:feature:settings` / `:feature:launcher` /
`:data:repository` / `:domain` / `:core:common` / `:feature:assistant` / `:app` test tasks all green;
`assembleDebug` (Hilt graph valid) green. **Device-pending (SM-A325F, handoff §7, batched):** favorites
count changes the row; voice toggle OFF hides the mic and no-ops `startVoiceInput`; toggles survive
restart; first-run nudge shows once and opens the system launcher chooser; "Ask assistant" carries text
to the assistant without saved state; a11y (TalkBack labels, ≥48dp) and cold-start re-measure.
**Phase UX is CLOSED with this block.**

## ADR 2026-07-04 — Phase UX device acceptance (SM-A325F / Android 13) + set-as-default bug fix

**Context.** Executed the batched Phase-UX device-acceptance run (Blocks X1→X6) on SM-A325F
(`RF8R705H38F`, Android 13 / SDK 33). Fresh install: the on-device APK (2026-07-02) was signed with a
different debug keystore (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), so it was uninstalled and the current
build installed clean (`versionName 0.1.0`, `lastUpdateTime 2026-07-04`). Evidence = screenshots +
`uiautomator`/`dumpsys`/`logcat` + `run-as` datastore/Room reads. Density 420dpi (1dp=2.625px → 48dp=126px).

**One real bug found and fixed (sanctioned minimal fix).** "Set as default launcher" was **silently
broken on every device**: both `SettingsScreen` and the first-run `SetupNudge` fired
`context.startActivity(roleManager.createRequestRoleIntent(ROLE_HOME))`. `createRequestRoleIntent` **must**
be launched via `startActivityForResult` so the permission controller can read the calling package;
launched with a plain `startActivity` the caller is null and the system `RequestRoleActivity` aborts
instantly (`logcat`: `W RequestRoleActivity: Package name cannot be null or empty: null` →
`RequestRoleFragment … requestingPackageName=null … result=1` → activity destroyed) with **no chooser
shown**. **Fix:** route through an `androidx.activity.compose.rememberLauncherForActivityResult(
ActivityResultContracts.StartActivityForResult())` in each screen; the shared helper is now a pure
`defaultLauncherIntent(context): Intent` builder (unchanged ROLE_HOME→ACTION_HOME_SETTINGS fallback,
`ActivityNotFoundException` still swallowed at the launch site). Added `libs.androidx.activity.compose` to
`feature/launcher` (already present in `feature/settings`; catalog dep, used by 3 other modules). Rebuilt
(`assembleDebug` green), reinstalled `-r` (state preserved), retested: the system role dialog **now
appears** (*«Сделать "Sidr Launcher" приложением для главного экрана по умолчанию?»*, One-UI-Home vs Sidr,
Отмена/По умолчанию), `mCurrentFocus=…RequestRoleActivity`, **no null-package error**. Cancelled to leave
the device's default unchanged. `:feature:settings` + `:feature:launcher` `testDebugUnitTest` green. Two
call sites share the fix; the nudge path was verified via the identical Settings helper (the nudge was
already dismissed, so re-triggering it would need a state reset).

**Acceptance matrix — result (PASS unless noted).**
- **X2 home:** no grid wall; top-bar **Settings + Assistant** icons (content-desc + 48dp); **All apps**
  affordance; typed commands: `settings`→Settings screen, `open plus`→launched `org.telegram.plus` (app
  labelled "Plus"; `open telegram`→correct "No app found" since no app is labelled telegram). Suggestions
  row works (`Clock`/`Music`, NIGHT).
- **X3/X4 drawer:** alphabetical, sticky A/B/… headers, no mic (by design), **live filter** ("tele"→Türk
  Telekom), **Clear** ✕, **Enter launches top match** (Türk Telekom → `com.tmob.AveaOIM` foregrounded).
- **X5/X6 settings:** theme **applies immediately** (Light) and **persists**; AI-suggestions gate ON→row /
  OFF→cleared; voice toggle **OFF → mic hidden** (recognizer IS available on device, so mic shows when ON;
  `startVoiceInput` also guards `if(!micInputEnabled) return`); **AI provider form** (Base URL/Model/API
  Key/Save) shown; **Set as default → system chooser** (after the fix).
- **Persistence:** `am force-stop`→relaunch preserved theme=Light, AI-suggestions=ON (cache-first repaint),
  favorites=4, voice=OFF, setup-hint=dismissed. Datastore keys present in `files/datastore/sidr_preferences`
  (`user_theme_name=light`, `user_favorites_count`, `user_mic_input_enabled`, `user_setup_hint_dismissed`,
  `flag_ai_suggestions_enabled`, `flag_usage_tracking_enabled`).
- **First-run nudge (X6-D):** shown when not default (card "Make Sidr your home screen" + Set-as-default +
  type/search hint); **Dismiss ✕ hides it**; **does not reappear after restart** (`setup_hint_dismissed`
  persisted); Set-as-default fixed.
- **Ask-assistant prefill (X6-C):** drawer "Ask assistant: \"plus\"" routes to Assistant; once a provider
  is configured the composer renders with **"plus" prefilled and NOT auto-sent**; no `SavedStateHandle`
  (transient by design — `AssistantScreen.kt:116` `remember(initialPrompt)`).
- **a11y:** `TopBarIcon` = 48dp `IconButton` + required contentDescription (Settings/Assistant/Back/Dismiss
  all labelled); nudge title `heading()`; app tiles/drawer rows expose the app label as contentDescription;
  chips 48dp; measured Settings/Assistant = 126×126px = 48dp. (TalkBack gesture-nav not driven over adb to
  avoid destabilising the session; the a11y *semantics* it surfaces were verified.)

**Bonus — retired two carried device-debt items (owner entered a real provider on device).** With BYOK
config saved (openrouter.ai/`openai/gpt-4o-mini`): **Assistant real streaming = PASS** (was PENDING-CONFIG)
— tapped Send on the prefilled "plus", a real reply streamed, input cleared, Send re-disabled on empty.
**Block-J BYOK secret store (real Keystore) = PASS** — config (base_url/model/id) is in `sidr_preferences`,
the key is only in a **separate encrypted `sidr_secrets`** store (key-name `ai_api_key_openrouter.ai`,
value ciphertext — no plaintext `sk-` in either file) and was successfully decrypted + used (request
authorized → stream).

**Cold-start re-measure (X6-E) — PENDING/PERF-RISK.** `am start -S -W` ×6 (debug build):
TotalTime 1956 / 2049 / 2031 / 1995 / 2066 / 2012 ms → **min 1956 / median ~2021 / max 2066** vs the
`<400ms` budget. Roughly in line with Round-3 (~1740ms median; within device-state/debug-build variance —
no meaningful regression). Perf fix stays **out of scope (Phase 9)**.

**Findings — dispositions.**
1. **Offline suggestions un-launchable on OEM devices** (NOT fixed — deferred to Phase 9 per owner).
   `TimeOfDaySuggestionProvider` emits **hardcoded AOSP package IDs** (`com.android.deskclock`/
   `com.android.music`/…) that don't exist on Samsung and are **not filtered against installed apps** →
   tapping `Clock`/`Music` routes correctly but ends in "Couldn't open that app." Chip render + tap wiring
   work; the target set is the gap. (Usage-based suggestions from `UsageSuggestionProvider` resolve fine —
   see follow-up below.)
2. **Favorites empty on fresh install → FIXED (owner chose the Settings toggle).** Favorites derive from
   usage records, only written when `FeatureFlags.usageHistoryEnabled` is on — it **defaulted `false` with
   no UI**. Added a **"Personalize from usage"** `Switch` to the Settings HOME section
   (`SettingsUiState.usageHistoryEnabled` + `SettingsViewModel.setUsageHistoryEnabled`, mirroring the
   AI-suggestions write path; off by default, privacy-first). **Device-verified:** toggle ON writes
   `flag_usage_tracking_enabled`; launching apps via the drawer records `app_usage` rows; the **Favorites
   row populates** (AiFiqh/Adobe/A101 → then 5 apps), the **count selector visibly changes the row (4→5)**,
   and the suggestions row now surfaces **real installed apps** too. +2 JVM tests; `:feature:settings`
   tests + `assembleDebug` green.
3. **Re-entry resumes last nav state** (NOT fixed — cosmetic). Relaunching `LauncherActivity` while alive
   resumes the last destination (e.g. the drawer), not home — only observable while Sidr is **not** the
   default launcher.

**Files changed.** Set-as-default fix: `feature/settings/.../SettingsScreen.kt`,
`feature/launcher/.../LauncherScreen.kt`, `feature/launcher/build.gradle.kts`. Usage-history toggle:
`feature/settings/.../SettingsScreen.kt` + `SettingsViewModel.kt` + `SettingsUiState.kt` +
`SettingsViewModelTest.kt`. No domain/data change (reuses the existing `usageHistoryEnabled` flag + key);
hard rules intact; 0 new catalog deps (activity-compose already in the catalog).

## ADR 2026-07-04 — Startup optimization + release build (SM-A325F)

**Context.** Phase 9 Blocks Y1 + Y2 only: make startup perceptibly fast on SM-A325F
(`RF8R705H38F`, Android 13 / SDK 33) with minimal measured changes; enable a real release build
(R8/resource shrink + keep rules) and ship a Baseline Profile. Y3-Y7 and model-gated OQ#1-OQ#4 were
left untouched. The Phase-UX debug cold baseline was ~2021ms median; release was measured first before
changing startup code.

**Measurements and deltas.**
- **Release pre-R8 / pre-Y1 code baseline:** warm `TotalTime` median ~78ms
  (`77,88,79,79,73,70,87,69`); cold median 504ms after dropping first
  (`509,495,527,497,543,501,504,518`). Early cold screenshot at ~150ms showed Sidr's own blue Loading
  spinner; ~600ms showed the home shell.
- **Y1 cache-first home shell:** `LauncherViewModel` no longer maps `apps == null` to `UiState.Loading`.
  It emits a Success shell immediately (cached suggestions/preferences, empty app/favorites until the
  PackageManager list arrives). Warm median ~95ms (`96,114,93,103,99,76,82,78`); cold median 527ms
  after dropping first (`547,620,510,527,521,525,546,539`). Spinner removed; no broad app-list refactor.
- **Y2 R8/resource shrink:** `release` now has `isMinifyEnabled=true` and `isShrinkResources=true` with
  keep rules for Hilt/Dagger, Room, kotlinx.serialization, ONNX Runtime, Ktor and workers. Release APK
  shrank from ~98MB to ~75MB. Warm median ~74ms (`80,73,72,73,80,73,74,83`); cold median 750ms after
  dropping first (`744,744,739,768,795,750,800,738`).
- **Y2 Baseline Profile:** added `:baselineprofile`, generated on SM-A325F via
  `BaselineProfileGenerator.startupHomeReady` (explicit `com.sidr.launcher/.LauncherActivity` HOME
  intent because Sidr has no normal `CATEGORY_LAUNCHER` activity). The generated
  `app/src/main/baseline-prof.txt` has 18,862 lines and is merged into release (`mergeReleaseArtProfile`
  / `expandReleaseArtProfileWildcards`). Final release warm median ~102ms
  (`204,109,83,86,115,101,103,98`); final cold median 766ms after dropping first
  (`778,745,766,757,772,747,784,817`). Baseline Profile did not produce a clear additional win in raw
  `am start` timing on this install, but the final release is inside the ~500-800ms cold band and the
  primary warm path is comfortably below ~200ms.

**Perfetto finding.** A final cold Perfetto trace was captured through `adb exec-out perfetto` (device
could not write trace files directly to `/data/local/tmp` or `/sdcard`). The traced run reported
`TotalTime 760ms`. The biggest app-side slices were normal first-frame/process work:
`bindApplication` ~177ms, `activityStart` ~76ms, `performCreate:LauncherActivity` ~44ms, and first
`Choreographer#doFrame`/`traversal` ~376ms. There was no remaining launcher-owned Loading state on the
first home frame; screenshots at ~150ms and ~600ms showed the home shell (search/setup/all-apps) with no
spinner. The earlier visible spinner was rooted in the `UiState.Loading` mapping while
`InstalledAppsRepositoryImpl.getInstalledApps()` was still enumerating packages for the first app list.

**Changes.**
- `feature/launcher`: `LauncherViewModel` paints a Success shell before the full installed-app list is
  loaded; retry keeps the shell visible. JVM tests updated and extended for cache-first suggestions while
  app loading is still gated.
- `app`: release R8/resource shrink enabled; `app/proguard-rules.pro` added; Baseline Profile plugin
  wired with `mergeIntoMain=true`.
- New `:baselineprofile` Android test module with Macrobenchmark Baseline Profile generation. The module
  is device-targeted for `arm64-v8a` and excludes unused trace-processor assets so the test APK installs
  reliably on SM-A325F. Generation note: UTP install initially timed out while PackageInstaller was in a
  bad state; after reboot the reduced test APK installed normally, and manual `am instrument` completed
  `OK (1 test)`.
- `app/src/main/baseline-prof.txt` checked in as the generated startup profile.

**Verification.**
- Device final release installed (`versionName 0.1.0`, no debug flag).
- Smoke-clean on SM-A325F: home -> drawer (`Search apps` + installed app list), Settings, Assistant,
  Set-as-default (system settings/role path), Voice education (`Voice commands`); no `FATAL EXCEPTION` /
  ANR in logcat.
- Gradle: `testDebugUnitTest assembleDebug :app:assembleRelease` **BUILD SUCCESSFUL**.

**Decision.** Mark **Y1 and Y2 done**. Warm/hot path is the launcher-critical path and now meets the
~200ms target without a spinner; cold release is in the accepted ~500-800ms band. Do not spend Phase 9
time chasing `<400ms` cold micro-optimizations unless a new trace exposes a large single blocker. Next
Phase 9 work remains Y3 (contextual suggestions correctness), then Y4-Y7.

## ADR 2026-07-04 — Contextual suggestions correctness (SM-A325F)

**Context.** Phase 9 Block Y3 only. The Phase-UX device pass showed `Clock`/`Music` chips from
`TimeOfDaySuggestionProvider` tapping into missing AOSP packages on Samsung. Y1/Y2 startup/release work
and model-gated OQ#1-OQ#4 were left untouched; no commit was made.

**Decision.**
- Home rendering now has a hard choke-point in `LauncherViewModel.resolveSuggestionLabels(...)`:
  suggestions render only when their `actionId` is an installed launchable package or a known Sidr route.
  Installed-app suggestions get the real installed label; uninstalled package targets are dropped. During
  the cache-first phase before the app list is loaded, package-target cached chips are held back and known
  routes may still render.
- Added a pure-domain `SuggestionActionTargetResolver` port with an Android `PackageManager` implementation
  in `:data:repository`. The resolver validates package/route actionIds and resolves universal anchors to
  this device's actual launchable package.
- `SuggestionEngineImpl` filters unsupported actions before ranking and persistence, so WorkManager
  precompute/cache no longer store stale unlaunchable top results.
- `TimeOfDaySuggestionProvider` no longer has the hardcoded six-app AOSP table. It is now a thin fallback
  over two resolved anchors only: alarms (`AlarmClock.ACTION_SHOW_ALARMS`) and camera
  (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`). If a launchable handler cannot be proven, it emits
  nothing instead of guessing a package.

**Manifest/visibility.** Android 11+ package-visibility queries were added for `SHOW_ALARMS` and
`STILL_IMAGE_CAMERA`, matching the resolver's implicit-intent probes.

**Verification.**
- New/updated JVM coverage: launcher VM filtering and cache-first behavior; engine unsupported-action
  filtering before ranking/cache; TimeOfDay resolved-anchor/no-fallback behavior.
- `./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest` ✅
- `./gradlew testDebugUnitTest assembleDebug :app:assembleRelease` ✅
- SM-A325F / Android 13 device smoke ✅: installed current debug APK with `adb install -r`, enabled AI
  suggestions + Personalize from usage, launched `A101` from the drawer to create a usage signal, then
  relaunched Sidr. Home rendered `A101` (usage) and resolved Samsung Clock (`Часы`) suggestions; no stale
  `Music`/missing-AOSP chip. Tapping `A101` launched `com.a101kapida.android`; tapping Clock opened
  `com.sec.android.app.clockpackage` instead of Sidr's "Couldn't open that app" fallback.
  `adb shell logcat -d -v time -t 1000 AndroidRuntime:E '*:S'` returned empty.

## ADR 2026-07-04 — Phase 9 Block Y4 test-coverage hardening

**Context.** Phase 9 Block Y4 only. Y1/Y2/Y3 were already complete and device-smoked; Y5-Y7 plus
model-gated OQ#1-OQ#4 stayed untouched. The goal was tests-first hardening with minimal production
change.

**Decision.**
- Broadened `LauncherViewModelTest` around the home suggestion contracts: cache first-paint followed by
  fresh-engine supersede, AI-suggestions toggle on/off regression, installed/route filtering, route tap
  routing with stale-feedback clearing, fallback package launch routing, and usage-history gating for
  suggestion taps.
- Broadened `deriveFavorites` coverage for uninstalled usage rows, default/custom caps, `favoritesCount`
  zero, and live `favoritesCount` preference changes without re-querying the app list.
- Broadened `SettingsViewModelTest` for unchanged/no-op writes on AI suggestions, usage history, and user
  preferences, plus `NavigateBack`. Set-as-default remains a screen-level Android intent helper rather
  than a VM action, so no new VM seam was introduced for it.

**Changes.** Test-only plus status docs:
`feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt`,
`feature/settings/src/test/java/com/sidr/launcher/feature/settings/SettingsViewModelTest.kt`,
`ai-context/phase-9-plan.md`, and `ai-context/current-status.md`. Production code unchanged; Y3 was not
refactored.

**Verification.**
- `./gradlew :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest` ✅
- `./gradlew testDebugUnitTest assembleDebug :app:assembleRelease` ✅

## ADR 2026-07-04 — Phase 9 Block Y5 privacy/logging/error handling

**Context.** Phase 9 Block Y5 only. Y1-Y4 were already done; Y6/Y7 and model-gated OQ#1-OQ#4 stayed
untouched. Y4's uncommitted test/docs changes were preserved. Goal: audit logging/privacy/error surfaces
with minimal production change and no domain-boundary movement.

**Decision.**
- Logging audit found one actionable privacy gap: `AppNavHost`'s unknown-route fallback logged the raw
  `event.route` and attached the `IllegalArgumentException`. Routes can include `assistant?prompt=...`,
  so a malformed/unregistered route could put user prompt text into logcat or an exception-bearing
  surface. The fallback now logs only a static payload-free message and no throwable.
- Added `AppNavHostLoggingGuardTest`, a source-level guard over the app nav host, to prevent raw-route
  logging or exception attachment from returning on that fallback path.
- Re-audited remaining production log sites. They are payload-free: static WorkManager/suggestion
  persistence messages, ONNX class simple names/status/counts/release reasons, and no user command text,
  transcripts, secrets/API keys, raw calendar/location values, or assistant payloads.
- No crash-report SDK or reporting surface is wired in the app. With no report payload to filter, the
  relevant crash/error surface was the exception-bearing nav fallback log, now stripped.
- Error handling stayed within existing boundaries. Repository/use-case contracts were not refactored;
  the sweep confirmed expected failures already return `OperationResult` or `AiChunk.Failed`/safe UI
  outcomes. Assistant coverage now pins the full `AiError` retryable vs non-retryable matrix and provider
  CTA classification for every variant.

**Verification.**
- `./gradlew :app:testDebugUnitTest :feature:assistant:testDebugUnitTest` ✅
- `./gradlew testDebugUnitTest assembleDebug :app:assembleRelease` ✅

## ADR 2026-07-04 — Phase 9 Block Y6 multi-version + LOW_END validation

**Context.** Phase 9 Block Y6 only. Y1-Y5 were already complete; Y7 and model-gated OQ#1-OQ#4 stayed
untouched. This was a validation-first block: no production code changes unless validation found a real
problem. The local WSL environment had one connected real device (SM-A325F `RF8R705H38F`, Android 13 /
SDK 33), no configured AVDs (`avdmanager list avd` empty), and no `emulator` binary in PATH.

**Decision / result.**
- Mark Y6 done for the available matrix with explicit residuals. Android 13 device validation passed; Android
  9 / 11 / 14 remain residual until emulator/device targets are available.
- No production changes were needed. The only edits for this block are status docs.
- The available device is not LOW_END by current classifier thresholds (`MemTotal=5,791,280 kB`, `nproc=8`
  -> HIGH_END), so LOW_END hardware behavior is covered by JVM classifier/gate tests in this run, not by
  physical low-RAM profiling.

**Device validation (SM-A325F / Android 13).**
- Install: Gradle `:app:installDebug` briefly lost the WSL USB device (`No connected devices` after starting
  its adb daemon). Direct `adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk` succeeded.
- Launch/home: debug cold samples observed during validation were `2258ms`, `1901ms`, and `1883ms`;
  warm/HOT samples with the process alive were `166ms`, `114ms`, and `100ms`. Home rendered search/mic,
  setup nudge, resolved suggestions (`Часы`, `A101`), Favorites, All apps, Settings, and Assistant. No
  `AndroidRuntime:E` entries were present. These debug timings do not replace the Y1/Y2 release perf record.
- Offline core: temporarily disabled Wi-Fi and mobile data, force-stopped/relaunched Sidr, confirmed home
  rendered, then launched the resolved Clock suggestion; foreground became
  `com.sec.android.app.clockpackage/.ClockPackage`. Wi-Fi and mobile data were restored to their original
  enabled state.
- Settings persistence: switched theme to Light, force-stopped/relaunched, returned to Settings, and
  confirmed Light stayed selected; AI suggestions / usage personalization / voice input / favorites=8
  remained persisted. Theme was restored to System default afterward.
- Set-as-default: Settings -> Set as default launcher opened the Android 13 `ROLE_HOME`
  `RequestRoleActivity` chooser with One UI Home and Sidr Launcher; cancelled without changing the default
  launcher. The older `ACTION_HOME_SETTINGS` fallback remains code-covered but not device-run in this
  environment.
- Voice education: with `RECORD_AUDIO` denied (`appops RECORD_AUDIO: ignore`), tapping the mic routed to
  the Voice commands education screen with the Enable microphone CTA rather than starting recognition.
- Memory/trim: `am send-trim-memory com.sidr.launcher BACKGROUND` and `COMPLETE` both invoked
  `SessionLifecycle`; logcat showed `OnnxTextEmbedder: Embedding ONNX session released (trim)` and
  `OnnxIntentClassifier: ONNX session released (trim)` for each level, and the Sidr process stayed alive.
- Local AI gating: no `files/models` directory exists in the app sandbox, so local NLU/embedding remain
  inert/model-gated and launcher core stayed responsive.

**Verification.**
- `./gradlew --no-daemon :domain:test :core:android:testDebugUnitTest :data:ai-local:testDebugUnitTest :app:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest :app:assembleDebug` ✅
- Android 13 runtime validation above ✅

## ADR 2026-07-04 — Phase 9 Block Y7 residual cosmetic cleanup

**Context.** Phase 9 Block Y7 only. Y1-Y6 were already complete in the working tree; model-gated
OQ#1-OQ#4 and the Y3/Y4/Y5/Y6 implementations stayed untouched except for app-shell navigation code
needed to retire the re-entry cosmetic finding. No commit was made.

**Decision.**
- Fixed the provider-form `keySet` race at the assistant VM level: after `saveProvider(...)` successfully
  writes a non-blank API key, `form.keySet` is set true immediately while still keeping the secret value
  out of UI state. The existing active-config observer remains the authority for provider switches and
  stored-key reads.
- The `RateLimited` user-facing text in the current working tree already read
  `Rate limited. Please wait and retry.`; Y7 adds regression coverage so the old `retray` typo cannot
  return unnoticed.
- Fixed the launcher re-entry cosmetic behavior with app-shell-only wiring. `LauncherActivity` is now
  `singleTop`; `onNewIntent` updates a Compose state signal; `AppNavHost` reacts to non-initial signals
  by navigating to `Routes.Launcher.ROUTE` with `popUpTo(Routes.Launcher.ROUTE)` and `launchSingleTop`.
  This clears nested destinations such as the drawer when Sidr is relaunched while alive, without changing
  feature ViewModels or normal in-app back behavior.

**Verification.**
- `./gradlew --no-daemon :feature:assistant:testDebugUnitTest :app:testDebugUnitTest` ✅
- `./gradlew --no-daemon :domain:test :core:android:testDebugUnitTest :data:ai-local:testDebugUnitTest :app:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest :feature:assistant:testDebugUnitTest :app:assembleDebug` ✅
- SM-A325F / Android 13 debug smoke ✅: installed `app-debug.apk`, launched Sidr, opened the drawer,
  relaunched with `adb shell am start -W -n com.sidr.launcher/.LauncherActivity`; Android reported the
  intent was delivered to the running top-most instance, and the next UI dump showed launcher home
  (`Search or type a command...`, `Favorites`, `All apps`) rather than the drawer. `AndroidRuntime:E`
  logcat filter was empty.

## ADR 2026-07-05 — AIL-0 complete (design tokens & visual identity: cyberpunk terminal in `core/ui`)

**Context.** Stage-1B opens with AIL-0: refine the design system *before* the new AI surfaces land, so
AIL-3/5/6 build on final tokens instead of reworking them. The owner pinned a bespoke visual direction —
*"modern ultra-cyberpunk in the spirit of early computers"* (an advanced military AI terminal on a quantum
machine): monospace, near-black CRT ground, one luminous accent, thin grid borders, brutalist edges. This
deliberately replaces the Phase-UX neutral-indigo Material-You look. Because the direction was pinned, it
is followed exactly (design-skill "user's words win", including when they match a known cluster). Reviewed
via an interactive artifact (green/amber × dark/light) before any code.

**Decisions (owner, artifact-reviewed 2026-07-05). Design forks D1–D5:**
- **D1 accent = green `#00FF66` (default brand); amber `#FFB000` shipped as an alternative accent.** Both
  are first-class schemes; one hero accent at a time.
- **D2 dark-first identity** (ground `#08090A`) + a **restrained light "blueprint / paper terminal"**
  scheme (muted ground, darkened accent, glow dropped). **Dynamic colour (Material You) default OFF** — a
  bespoke brand and wallpaper-derived colour are mutually exclusive.
- **D3 full monospace — JetBrains Mono bundled** (Regular/Medium/SemiBold/Bold, OFL). Launcher text is
  short, so the readability cost is low and the identity payoff high.
- **D4 restrained static tokens now; CRT motion/overlays deferred** (scanlines, flicker, per-character
  typing, hover-invert, animated glow → DF-5, LOW_END-gated).
- **D5 accent-switcher rollout = Variant A:** define both accent schemes in `core/ui` now (default green);
  the live green/amber switch in Settings is a later half-step (DF-7). AIL-0 stays presentation-only.

**What shipped (`core/ui`, presentation only).**
- `Color.kt`: **4 `ColorScheme`s** — `GreenDark`/`GreenLight`/`AmberDark`/`AmberLight` — mapped onto
  semantic M3 roles; raw hex private to the file; elevation carried by `outline`/`outlineVariant` grid
  borders (surface kept close to background in dark), not tonal shadow.
- `Theme.kt`: `enum AccentColor { GREEN, AMBER }` + `SidrTheme(darkTheme, accent = GREEN,
  dynamicColor = false, content)`. Resolution: dynamic (only if explicitly opted in on API 31+) → else the
  fixed brand scheme for accent × dark/light. No caller passes `dynamicColor`, so the default flip is
  clean; the single caller `LauncherActivity` is unchanged (stale "dynamicColor stays on" comment fixed).
- `Type.kt`: `JetBrainsMono` `FontFamily` from bundled `res/font/jetbrains_mono_{regular,medium,semibold,
  bold}.ttf`; full-mono `SidrTypography` across all roles; tracking tightened for mono, wide only on the
  small uppercase section label. OFL notice at `core/ui/OFL-JetBrainsMono.txt`.
- `Shape.kt`: brutalist `SidrShapes` `0 / 2 / 4 / 8`dp (down from 4–28dp). `Spacing`/`Sizes` kept (4→32
  rhythm already sound).
- Component previews refreshed to the dark terminal identity (`showBackground` on the brand ground) plus
  an amber `AppTile` proof of the accent axis; `AppTile` preview icon switched from `CircleShape` to
  `shapes.medium` to match. **No component API/behaviour/structure change.**
- **One `:app` touch (cosmetic):** `styles.xml` `Theme.SidrLauncher` → dark `Material.NoActionBar` with
  `android:windowBackground = @color/sidr_ground` (`#08090A`), killing the white boot flash before Compose
  paints. No behaviour change.

**Invariants / hard-rules.** `core/ui` still depends only on `core/common`; no `feature→feature` /
`domain→ui` edge; no `domain`/`data`/feature-logic change. Launcher core still fully offline. The design
forks that touch *screens* (DF-1 home layout, DF-2 input field, DF-3 results/chips, DF-4 confirm card,
DF-5 motion/CRT FX, DF-6 brand assets/icon, DF-7 accent switcher UI, DF-8 theme packs/wallpapers/widgets)
are **deferred** to AIL-3/5/6 and Stage 2/3 — designed against real behaviour, not pre-emptively. The
token system was kept extensible on purpose (semantic roles + parameterized theme), so future accents,
theme packs, wallpapers, and widget containers layer on without reworking `core/ui`.

**Verification.**
- `./gradlew --no-daemon testDebugUnitTest assembleDebug` ✅ (font resources compiled — validates the
  bundled TTFs; full unit-test suite + debug APK green).
- Device **visual** acceptance is opportunistic for AIL-0 (mandatory only at AIL-6); not run this block.

## ADR 2026-07-05 — AIL-1 complete (Action Registry contracts in `domain`)

**Context.** Stage-1B's second block. The launcher's action taxonomy is closed: `ExecutableAction`
(7 variants) + `IntentActionResolver`'s `when` + `AndroidActionExecutor`'s `when` must all be edited for
every new capability, there is no risk model, and nothing enumerable for the AIL-4 LLM router to route
into. AIL-1 introduces an **additive** catalog + risk + schema layer *above* the existing
rule → resolver → executor path without touching it. `ExecutableAction` stays the execution vocabulary;
the registry adds the metadata the router (AIL-4) and confirmation UI (AIL-5) need. Pure `domain` + a
`core/testing` fake — no behaviour change, no Android, no provider.

**Forks resolved before code (owner, this session).** The plan's R7 fixed *scope* (minimal catalog, not a
full Phase-11 registry); three *shape* forks were surfaced and decided:
- **argSchema shape → `List<ActionArg>`, string-only.** `ActionArg(name, type, required, description)`
  with `enum ArgType { STRING }`. Every MVP family takes a single free-text arg; keeping `type`/`required`
  explicit gives AIL-4 something concrete to strict-validate, and `ArgType` leaves room for richer types
  without breaking the descriptor contract. (Rejected: bare `Map<String,String>` — no validation surface;
  a fuller typed schema now — over-built before AIL-2/4 need it.)
- **`LauncherAction` ↔ `ExecutableAction` → parallel, unresolved args.** `LauncherAction` is a *separate*
  sealed hierarchy carrying **semantic, unresolved** args (`LaunchApp(query="telegram")`, not a resolved
  package) + `val id: ActionId`. `ExecutableAction` is untouched; the mapping
  `LauncherAction → resolution → ExecutableAction` is deferred to execution time (AIL-2/4/5). This matches
  the LLM's reality (it knows "telegram", not a package) and keeps the registry strictly additive.
  (Rejected: wrapping a resolved `ExecutableAction` — forces app resolution into the router at plan time.)
- **Catalog home → port + types in AIL-1; concrete descriptor catalog deferred to AIL-2.** Per the hard
  rule "interfaces in `domain`, implementations in the data layer," AIL-1 ships the `ActionCatalog`
  **interface** + the type vocabulary (`ActionId`/`ActionIds`, `LauncherAction`, `ActionDescriptor`,
  `ActionRiskLevel`, `ActionCategory`, `ActionArg`). The **registration** — the concrete
  `ActionDescriptor` instances with per-family title/description/risk/args, plus the `ActionCatalog` impl
  and its `:app` binding — lands in AIL-2 alongside the executors + rule recognition that act on the two
  new families. So "the 6 families are registered" is delivered at AIL-1 as the **family vocabulary**
  (`ActionIds` + `LauncherAction` variants); the descriptor metadata is AIL-2's. This is a conscious,
  documented narrowing of the plan's §5 AIL-1 wording, not a silent one.

**What shipped (`domain/action/`, pure Kotlin — stdlib only).**
- `ActionId` — `@JvmInline value class(String)` (mirrors `AiProviderId`/`ModelId`); the wire identity
  shared by `LauncherAction` and (AIL-4) the router's `action` JSON id.
- `ActionIds` — canonical ids for all seven families: `launch_app`, `web_search`, `open_settings`,
  `open_assistant`, `show_apps` (shipped families) + `open_url`, `play_store_search` (AIL-2 families,
  vocabulary now / execution later) + `ALL` for exhaustiveness. Lowercase snake_case, pinned as a
  persisted/wire contract.
- `LauncherAction` — sealed interface, `val id: ActionId`, seven variants carrying unresolved semantic
  args (`OpenSettings`/`ShowApps` as `data object`s; the rest `data class`es).
- `ActionDescriptor(id, title, description, category, risk, argSchema = emptyList, permissionGate? = null)`
  — the catalog+risk+schema metadata type. `permissionGate` reuses the existing `PermissionFeature`
  (consumed by AIL-5's education flow).
- `ActionRiskLevel { SAFE, CONFIRM, DANGEROUS }` (MVP uses SAFE/CONFIRM; DANGEROUS reserved for Stage 3 so
  the AIL-5 `when` stays total), `ActionCategory { APP, WEB, SYSTEM, ASSISTANT, STORE }`,
  `ArgType { STRING }`, `ActionArg`.
- `ActionCatalog` port — `all(): List<ActionDescriptor>` + `descriptor(id): ActionDescriptor?`;
  read-only, side-effect-free.
- `core/testing`: `FakeActionCatalog` (seedable in-memory catalog).

**Invariants / hard-rules.** `domain` stays stdlib-only (no coroutines needed here), vendor-neutral (no
provider names). No `feature→feature` edge; no change to `IntentMatcher`, `HandleUserCommandUseCase`,
`GenerateReplyUseCase`, `ExecutableAction`, `IntentActionResolver`, `AndroidActionExecutor`, or any screen
— the rule → resolver → executor path is byte-for-byte unchanged, so the launcher still works fully
offline and no runtime behaviour changed (nothing consumes the registry yet). The `CommandPlanner` /
router is **not** touched (AIL-4).

**Verification.**
- `./gradlew --no-daemon :domain:test testDebugUnitTest assembleDebug` ✅ — 9 new domain tests
  (`LauncherActionTest` 4: every variant's canonical id, args carried verbatim, ids unique + stable
  lowercase snake_case; `ActionCatalogTest` 5: catalog enumerate/lookup via `FakeActionCatalog`,
  descriptor defaults, argSchema type/required + permission gate, three-level risk model). Full unit-test
  suite + debug APK green.
- No device surface in this block (contracts only); device acceptance is AIL-6.

**Next = AIL-2** — Web / URL / Play-Store routing (no AI): the concrete `ActionCatalog` impl + the
`OpenUrl`/`PlayStoreSearch` executors + rule recognition + configurable search provider (R5) + URL safety
(R6). AIL-2 is what registers the concrete descriptors deferred here.

**Next = AIL-1** (Action Registry, `domain`).

## ADR 2026-07-05 — AIL-2 complete (Web / URL / Play-Store routing, no AI)

**Context.** Stage-1B's third block, fully offline / no LLM. It (a) delivers the concrete `ActionCatalog`
descriptor registration deferred by AIL-1, and (b) adds real offline routing for two new capabilities —
opening web addresses and searching the Play Store — through the existing
rule → resolver → executor pipeline. The shipped `SearchIntent` fired only on a literal `search/find/google`
prefix and hardcoded Google (`AndroidActionExecutor.openSearch` `TODO`); there was no URL detection and no
"install X" → store. `LauncherAction`/`ActionDescriptor` (AIL-1) stay the parallel catalog layer; the actual
execution vocabulary is still `ExecutableAction`, extended additively here.

**Forks decided before code (owner, this session — R5/R6 pinned + AIL-Q3 finalized).**
- **R5 = (b) configurable web-search provider, default Google.** New `UserPreferences.webProviderTemplate`
  (`https://www.google.com/search?q={q}`) + DataStore key `web_provider_template`. **Denylist-clean by
  design:** the `PrivacyInventoryGuardTest` denylist forbids the terms `search`/`query`, so the key is named
  `web_provider_template` (not `web_search_provider`) and added to `ALL_KEY_NAMES`; the guard is green. No
  switcher UI ships here — that's DF-7/AIL-3; AIL-2 only retires the hardcoded-Google TODO by reading the
  pref (executor now injects `UserPreferencesRepository`).
- **R6 = (b) open only high-confidence URLs; ambiguous → browser search; never open a guessed/malformed URL
  silently.**
- **AIL-Q3 finalized (URL normalization/safety), owner-answered:**
  - **Q1 curated TLD allow-list** — a scheme-less host auto-opens only when its final label is a known TLD;
    a domain-shaped token with an unknown-but-TLD-looking suffix (`example.foobar`, `node.js`) → web search,
    not an open. (Rejected the permissive `x.y` pattern — false-opens code-ish tokens.)
  - **Q2 URL wins over app launch when high-confidence** — `open github.com` opens the site; `open telegram`
    still launches the app (the launch-verb argument is URL-checked first; only a confident `Url` diverts).
  - **Q3 `install` only** — `install <app>` → Play Store search; `download`/`get` deliberately do NOT
    trigger it (avoids `download manager`-style surprises).
  - **Q4 bare host / simple path opens; query-string → web search** — because recognition runs on the
    lowercased normalized input and a case-sensitive query string would be corrupted, any detected URL
    carrying `?` is routed to a web search instead of opening a possibly-wrong page.
  - **Scheme allow-list:** only `http`/`https` are ever opened; every other scheme (`intent://`,
    `javascript:`, `market://`, `tel:`, `mailto:`, `file://`, `ftp://`, …) → `None`, never opened silently
    (no silent `intent://`; user-typed `market://` is never honored — `market://` is produced only by our
    own Play-Store executor). **Homograph/punycode guard:** an `xn--` label or any non-ASCII host → web
    search, never a silent open.

**What shipped.**
- **`domain/intent/UrlDetector.kt`** (pure, Android-free — so it is unit-testable; the Context-bound
  executor still has no unit tests). `classify(token) → UrlClassification { Url(url) | SearchFallback(term)
  | None }`. Single-token only (any whitespace → `None`). Implements the full AIL-Q3 rule set above.
  **Recognition ≠ opening** — the detector only classifies; the executor opens.
- **`domain/intent/LauncherIntent`** += `OpenUrlIntent(url)`, `PlayStoreSearchIntent(query)`;
  **`ExecutableAction`** += `OpenUrlAction(url)`, `PlayStoreSearchAction(query)`. `IntentActionResolver`
  maps intent→action; `HandleUserCommandUseCase.routeAction` routes both new actions through the executor
  (its **contract is unchanged** — only the internal exhaustive `when`s gained the mechanically-required
  branches). `toMatchType()` classifies both new intents as `IntentMatchType.SEARCH` so their arbitrary
  content (a URL / an app-name query) is **redacted** by the existing Fork-3 data-layer mapper — **no new
  redaction surface**. `LauncherViewModel.describe()` gained matching Suggest strings.
- **`data/repository/intent/RuleBasedIntentMatcher`** — new rules: launch-verb argument URL-check (Q2);
  `install <app>` → `PlayStoreSearchIntent` (Q3); bare URL → `OpenUrlIntent` / ambiguous → `SearchIntent`
  (R6), placed after the command table and before the Unknown fallback. All new hits are `0.90f`
  (auto-execute; `search`/`google` verbs and existing behavior are byte-for-byte unchanged — proven by the
  untouched existing tests still passing).
- **`data/repository/action/DefaultActionCatalog`** — the concrete `ActionCatalog` registering all seven
  families with title/description/category/risk/argSchema. `open_url`/`play_store_search` = **CONFIRM**
  risk, the five shipped families = **SAFE**; no family needs a permission gate in the MVP. Bound via a new
  `:app/di/ActionBindsModule` (`@Binds`). Nothing injects the catalog on the runtime path yet (AIL-4 router
  + AIL-5 confirmation UI consume it); binding it validates the graph and keeps registration additive.
- **`data/repository/intent/AndroidActionExecutor`** — new `OpenUrlAction`/`PlayStoreSearchAction`
  branches; `openSearch` now builds the URL from `webProviderTemplate` (R5). URL open via `ACTION_VIEW`;
  Play Store via `market://search?q=…&c=apps` with an `ActivityNotFoundException` fallback to
  `https://play.google.com/store/search`. All paths catch `ActivityNotFoundException`/`SecurityException`
  → safe `Failure`, never crash.

**Deliberate deviation / note.** In AIL-2 (no AI) a user-typed high-confidence URL / `install` executes
**directly**, exactly as a user-typed `search` does today — the descriptor's `CONFIRM` risk is metadata the
**AIL-5** confirmation layer will enforce (confirmation matters most for **LLM-proposed** actions the user
didn't type, R4). This is the plan's vertical-slice sequencing, not an omission. Documented so AIL-5 knows
to wire the gate. `host:port` and trailing-dot forms are treated as non-openable edge cases (→ `None`),
accepted for the launcher MVP.

**Invariants / hard-rules.** `domain` stays stdlib-only + vendor-neutral (`UrlDetector` is pure Kotlin, no
provider names). No `feature→feature` edge. Ops return `OperationResult`/`ActionExecutionResult`; the
executor never throws to the UI. `IntentMatcher` and `GenerateReplyUseCase` ports untouched;
`HandleUserCommandUseCase` **contract** unchanged (rule → resolver → executor topology intact). Launcher
core still fully offline — the new routing needs no network and no permission. `CommandPlanner`/router not
touched (AIL-4). Privacy: the new pref key is denylist-clean; URL/store queries are redacted in history.

**Verification.**
- `./gradlew --no-daemon testDebugUnitTest assembleDebug` ✅ **BUILD SUCCESSFUL** (Hilt graph valid with the
  new `ActionCatalog` binding + the executor's new `UserPreferencesRepository` dep). New tests: `UrlDetectorTest`
  25 (scheme allow-list, curated-TLD, punycode/IDN, query→search, non-URL→None), `RuleBasedIntentMatcherTest`
  25→35 (URL/install recognition + existing behavior unchanged), `IntentActionResolverTest` +2,
  `DefaultActionCatalogTest` 7, `HandleUserCommandUseCaseTest` +2 (OpenUrl/PlayStore route through the
  executor). `PrivacyInventoryGuardTest` green (new key clean).
- Device acceptance is **opportunistic** here (mandatory only at AIL-6); AIL-2 has a runtime surface but no
  device run was performed this session — carried to AIL-6's SM-A325F pass.

**Next = AIL-3** — Universal Input (`UniversalInputRouter` + sealed `InputIntent`; unify the home field for
app-filter + command + web/site + assistant + voice; typed commands byte-for-byte; voice reuses the path,
R8). `HandleUserCommandUseCase` stays untouched.

## ADR 2026-07-05 — AIL-3 complete (Universal Input: additive router over one home field)

**Context.** Stage-1B's fourth block. The home field only stored typed text and submitted it to the
command pipeline — no live app-filter (that lived only in the App Drawer), no explicit web/assistant
lanes, and the terminal identity from AIL-0 was not yet applied to the input surface. AIL-3 unifies the
single home field so it additively routes typed **and** spoken natural language to app-filter (live) ·
the existing command pipeline (byte-for-byte) · web/site · Play Store · assistant (prefilled) · voice —
**without touching the proven command pipeline** and with **no LLM** (that is AIL-4). Design/plan:
`ai-context/ail-3-universal-input-design.md` + `ai-context/ail-3-universal-input-plan.md`.

**Forks resolved before code (owner, this session).**
- **Routing model → additive router (not "router owns submit").** Enter/IME submit still calls
  `HandleUserCommandUseCase` byte-for-byte; the router only feeds the live results/chips, and each route
  chip is itself implemented by reusing the existing pipeline/nav (so no new execution logic). Parity is
  guaranteed by construction. (Rejected: a router that classifies+dispatches submit — would force
  re-proving parity.)
- **DF-1 home layout → "Search overtakes" primary + hidden dev "Command console".** A non-blank buffer
  replaces the home body (favorites/suggestions/all-apps) with a results panel; clearing restores home.
  Plus a hidden developer transcript mode behind a **two-factor** unlock: 7 rapid taps on the `SIDR//`
  wordmark **arm** it, then submitting the literal `//dev-mode` **toggles** the console. Session-only /
  in-memory (no persisted key → `PrivacyInventoryGuardTest` untouched).
- **DF-2 input field → terminal `>` prompt.** `SidrCommandPrompt` (`core/ui`): leading `>` glyph
  (replaces the magnifier), JetBrains Mono, accent caret, thin border + glowing accent corner ticks, mic
  as a trailing affordance with idle vs listening (accent) states. The App Drawer keeps `SidrSearchField`.
  (Deferred to DF-5/AIL-6 motion: a **true block caret** + blink + CRT FX — the KDoc was corrected to not
  over-promise a block caret.)
- **DF-3 results → hybrid.** App matches as an icon+label list (icons aid recognition) over a single
  bracketed route-chip row `[ ⌕ web ] [ ✦ ask ] [ ⌂ site ]` (`RouteChipRow`); `site` only for a safe
  openable URL.
- **R8 voice → one path.** Voice partials already flow into `commandInput`, so they drive the live
  results/chips for free; Final submits through the unchanged command path. No new voice code.

**What shipped (10 files, additive).**
- **`domain/input/`** (pure, stdlib-only): `InputIntent` (sealed — `Empty` / `DevSentinel` /
  `Query(raw, siteUrl)`) + `object UniversalInputRouter.classify(buffer)` — mirrors `UrlDetector`, which
  it **reuses** for URL safety. It lowercases the token handed to `UrlDetector` (that component is
  documented to expect a normalized/lowercased token; `Locale.ROOT`) while returning `raw` as the trimmed
  **original** casing — so mixed-case domains (`GitHub.com`) still open (review fix).
- **`core/ui`**: `SidrCommandPrompt` (DF-2 terminal field) + `RouteChip`/`RouteChipRow` (DF-3 bracketed
  chips, button role + 48dp touch target per the sibling-component a11y convention). Presentation-only;
  `core/ui` still depends only on `core/common`.
- **`feature/launcher`**: `HomeInputResults(active, appMatches, chips)` + `RouteChipKind {WEB,ASK,SITE}` +
  `ConsoleLine`. `LauncherViewModel` gained a derived `inputResults` flow (`combine(commandInput,
  _rawAppsResult)` → router-classified chips + `filterApps` app matches, SITE-gated on `siteUrl`),
  `submitWebSearch`/`submitSite` (both delegate to the unchanged `onCommandSubmitted`), and the session-only
  dev console (`devConsoleOn`/`consoleLines`/`armDevMode()` + an **additive** armed-`//dev-mode` pre-check
  in `onCommandSubmitted` that consumes the sentinel and returns; every other input runs the pre-existing
  `handleUserCommand.handle(...)` path verbatim). `LauncherScreen` wired: `SidrCommandPrompt`, the
  "search overtakes" `InputResultsPanel`, chip dispatch (WEB→`submitWebSearch`, ASK→assistant nav with
  `Uri.encode`d prefill built **in the screen**, SITE→`submitSite`), the `CommandConsole` transcript, and
  the `SIDR//` wordmark 7-tap arm.

**Invariants / hard-rules.** Command pipeline **byte-for-byte**: `git diff bafd0f3..HEAD` over
`domain/.../intent/` + `data/repository/.../intent/` is **empty** — `CommandNormalizer`/`IntentMatcher`/
`HandleUserCommandUseCase`/`ExecutableAction`/resolver/`AndroidActionExecutor` untouched. `domain` stays
pure/vendor-neutral; `core/ui` depends only on `core/common`; no `feature→feature` edge; the ViewModel
stays Android-free (the screen does `Uri.encode` + route building); nav via `NavigationEvent`. Dev console
is in-memory only — no new persisted key, privacy guard untouched. Launcher core still fully offline; no
LLM (AIL-4).

**Verification.**
- `./gradlew --no-daemon testDebugUnitTest assembleDebug` ✅ **BUILD SUCCESSFUL**. New JVM tests:
  `UniversalInputRouterTest` 7 (empty/dev-sentinel/query/URL/punycode/mixed-case-raw), `LauncherViewModelTest`
  62→69 (results derivation + SITE gating, web/site dispatch routes through the matcher, armed dev-sentinel
  consumed vs un-armed falls through, console records only while on). `core/ui` components validated by
  compile + previews (presentation-only).
- Executed via subagent-driven development: 7 implementer tasks, each spec+quality reviewed; 4 Important
  review findings fixed and re-reviewed clean (Task 1 mixed-case URL lowercase; Task 2 padding + honest
  caret KDoc; Task 3 chip a11y/48dp). Final whole-branch review = **Ready to merge (Yes)**; its two
  recommended one-liners were swept in (dead `InputResultsPanel` param dropped; `submitWebSearch` guarded
  against a double-`search` prefix, +1 regression test → VM 69→70).
- Device acceptance is **opportunistic** here (mandatory only at AIL-6) — not run this session.
- **Deferred (surface at AIL-5/6):** true block caret + blink/CRT motion (DF-5); the `>` glyph TalkBack
  a11y polish; wiring `SidrCommandPrompt.listening` to a VM voice-capture flag; a device-render fallback
  for the non-ASCII chip glyphs (`⌕`/`✦`/`⌂`).

**Next = AIL-4** — LLM Action Router (BYOK cloud): `CommandPlanner` port + cloud impl, `RouteCommandUseCase`
(rule-first → planner on low confidence), strict structured JSON parse, privacy allow-list extension +
guard test, `NoPlan` → rule fallback, feature-flag + settings toggle; **router-off ⇒ byte-for-byte
rule-only parity**. The blocking AIL-4 ADR is already written (see "ADR 2026-07-05 — AIL-4").

## ADR 2026-07-06 — AIL-4 complete (LLM Action Router: `CommandPlanner` + `RouteCommandUseCase`)

**Done 2026-07-06.** Implements the blocking design ADR "2026-07-05 — AIL-4" exactly (Forks R1/R2/R3/R4
as recommended); no fork deviations. The launcher can now *understand* natural language and *route* it to
a registered action via a BYOK cloud LLM, while the shipped default (flag off) stays byte-for-byte the
rule-only launcher.

**What shipped.**
- **`domain/ai/router/` (pure, vendor-neutral):** `CommandPlanner` port + `PlanResult`
  (`RoutedAction(action, confidence)` / `Clarify(question)` / `NoPlan`); `ActionProposal` (the
  parsed-but-unvalidated wire shape, with `none`/`clarify` sentinels); `ProposalValidator` (the
  **fail-closed** core — `ActionProposal` × `ActionCatalog` → `PlanResult`, strict: unknown/unregistered
  id, missing/blank required arg, or **any** extra arg key → `NoPlan`; builds the concrete
  `LauncherAction`); `CatalogSchemaRenderer` (static `ROUTER_INSTRUCTION` + descriptor-derived lines — the
  ONLY outbound content besides the user command); `RouteCommandUseCase` (rule-first → planner-second).
- **`RouteCommandUseCase` order (R1/R2/R4):** run the unchanged `HandleUserCommandUseCase` once → if
  `!llmRouterEnabled` return the rule outcome (**parity**) → if the rule outcome is not `Unknown`/
  `LowConfidence` return it (planner never consulted) → if offline return it → else `planner.plan(rawInput,
  catalog)` and map `RoutedAction`→`CommandOutcome.RoutedAction(action, confidence, needsConfirmation)`
  (never auto-executed, R4; `needsConfirmation = risk != SAFE`), `Clarify`→`Message`, `NoPlan`→rule outcome.
- **`CommandOutcome.RoutedAction`** (new variant) + VM branches (`applyOutcome` renders a display-safe,
  **non-executing** suggestion; `outcomeSummary` for the dev console). `HandleUserCommandUseCase` and its
  `CommandOutcome` producers are otherwise untouched.
- **`data/ai-cloud/LlmCommandPlanner`** (Hilt-free, reuses the shared `HttpClient` + `AiProviderConfigRepository`
  + `SecureSecretStore`): a **separate, non-streaming** `chat/completions` call (`stream:false`), HTTPS-only,
  `Bearer` key, minimal body (no sampling params, small `max_tokens`). Reads `choices[0].message.content`,
  tolerates prose/markdown fences (extracts first `{`..last `}`), strict-parses to `ActionProposal`, then
  `ProposalValidator`. **Never throws**; every degradation → `NoPlan`. The assistant's streaming engine is
  byte-for-byte untouched.
- **Feature flag + settings toggle:** `FeatureFlags.llmRouterEnabled` (default **false**) with the
  denylist-clean key `flag_llm_router_enabled` (mapper + `ALL_KEY_NAMES` + `PrivacyInventoryGuardTest`);
  `SettingsViewModel.setLlmRouterEnabled` + a "Smart command routing" `Switch` in the ASSISTANT section.
- **`:app` DI:** `RouterProvidesModule` provides `CommandPlanner` (`LlmCommandPlanner`) + `RouteCommandUseCase`;
  `LauncherViewModel` now injects `RouteCommandUseCase` in place of `HandleUserCommandUseCase`. No
  `data → data` edge (impl → `domain` ports only); no `feature → feature` edge.

**Forks — followed as recommended (no deviation).** R1 = planner only on `Unknown`/`LowConfidence`;
R2 = a wrapping `RouteCommandUseCase` (`HandleUserCommandUseCase` unmodified); R3 = structured JSON,
strict-parse, else `NoPlan`; R4 = LLM proposals never auto-execute (surface as
`RoutedAction`/confirmation). **Implementation choices recorded (within R3's sanctioned "structured JSON"
recommendation, not fork deviations):** (a) the MVP uses the **portable content-JSON** path the design ADR
explicitly sanctions (a strict "reply with only this JSON" instruction + parse from `message.content`),
NOT native `tools`/`tool_choice` — widest BYOK compatibility and it makes AIL-Q2 fall out for free (a
non-tool-capable model that answers with prose parses to `NoPlan`); native function-calling is a Stage-2
enhancement. (b) The `offline ⇒ rule outcome` short-circuit lives in `RouteCommandUseCase` (via
`ConnectivityChecker`); **no-key / bad-config** is handled inside the planner impl (→ `NoPlan`,
equivalent to the ADR's `hasProviderKey` guard) so `SecureSecretStore` stays out of the domain use case.

**§0 mandatory guards — all green.**
- **Privacy (allow-list widened by exactly one static category):** `OutboundContextPolicy.AllowedContext`
  gains **`ACTION_CATALOG_SCHEMA`** only. Covered by `RouterOutboundGuardTest` (allow-list = the 4
  categories; `ROUTER_INSTRUCTION` + rendered schema carry no `FORBIDDEN_CONTEXT_TERMS`/`CREDENTIAL_TERMS`;
  `render(emptyCatalog) == ROUTER_INSTRUCTION` proves no hidden context; a planted GPS sentinel never
  appears), the updated `AiRequestGuardTest`/`SuggestionOutboundIsolationTest` (no suggestion/calendar/
  location/usage category admitted), `DefaultActionCatalogRouterSchemaGuardTest` (scans the **real** shipped
  schema), and `LlmCommandPlannerTest`'s body-capture test (outbound body = exactly `[system=schema,
  user=command]`; a planted sensitive value is absent).
- **Byte-for-byte rule-only parity:** `RouteCommandUseCaseTest` proves router-off / confident-rule /
  offline all return the rule outcome and **never consult the planner** (`planCallCount == 0`), and that
  `handle()` runs exactly once. The whole `LauncherViewModelTest` suite passes unchanged with the router
  off (the swap to `RouteCommandUseCase` is transparent).
- **Hard timeout → `NoPlan` (AIL-Q1):** `LlmCommandPlanner` wraps the call in `withTimeoutOrNull` (2000ms
  default); `a hard timeout collapses to NoPlan` proves it with real timing. **Non-tool-capable → `NoPlan`
  (AIL-Q2):** `free-text reply is NoPlan` + `hallucinated action id is NoPlan`.

**Hard rules intact.** `domain` pure/vendor-neutral (grep `anthropic|openai|gemini|claude` over
`domain/.../router/` empty); `IntentMatcher` (classification) / `GenerateReplyUseCase` (conversation) /
`CommandPlanner` (structured routing) are three distinct ports — routing is **not** folded into either;
"local matching runs before any LLM call" holds (rule path always first, short-circuits); launcher core
fully offline; ops return `OperationResult`/`PlanResult` (the planner never throws).

**Verification.** `./gradlew --no-daemon :domain:test` ✅ (new: `ProposalValidatorTest` 15,
`RouteCommandUseCaseTest` 8, `RouterOutboundGuardTest` 7) and
`./gradlew --no-daemon testDebugUnitTest assembleDebug` ✅ **BUILD SUCCESSFUL** (new: `LlmCommandPlannerTest`
11, `DefaultActionCatalogRouterSchemaGuardTest` 1, `SettingsViewModelTest` +3, `LauncherViewModelTest` +1;
Hilt graph valid, debug APK built). Device acceptance is deferred to AIL-6 (mandatory there; a real BYOK
provider round-trip + offline parity). **Not built:** confirmation UI + actual execution of a routed
proposal (AIL-5 — the `needsConfirmation` flag + `RoutedAction` outcome are the seam it consumes).

**Next = AIL-5** — Confirmation & safety gating: `ActionRiskLevel` → confirmation card in
`feature/launcher`; LLM-proposed actions always confirm (R4); permission-gated via the education flow;
execute the confirmed `LauncherAction` (resolve → `ExecutableAction` → executor); safe fallback.

## ADR 2026-07-06 — AIL-5 complete (Confirmation & safety gating: risk-gated confirm card + execution)

**What shipped.** AIL-5 turns AIL-4's *display-only* `CommandOutcome.RoutedAction` into an *executing*
surface — the confirmation + execution layer for **router proposals**. Nothing else changes: the block
touches only the `RoutedAction` path, so **router-off ⇒ `RoutedAction` is never produced ⇒ byte-for-byte
rule-only parity** (structural, not just tested — the whole existing `LauncherViewModelTest` suite passes
unchanged). Scope fork decided **router-proposals-only** (owner, recommended): user-typed CONFIRM-risk
rule actions (`open_url`/`play_store`) keep executing directly as in AIL-2 — AIL-5 does **not** re-gate the
rule path, preserving the parity guarantee.

**Domain (pure, additive).** New `domain/intent/ExecuteActionUseCase.execute(action: LauncherAction):
CommandOutcome` — the execution counterpart to AIL-4's routing. It maps each `LauncherAction` family to a
`LauncherIntent` and resolves + executes through the **unchanged** `IntentActionResolver` + `ActionExecutor`
(the same proven path `HandleUserCommandUseCase` uses; routing/execution semantics intentionally identical —
ambiguity → `NeedsConfirmation`, not-found → `Message`, nav families never touch the executor). Never
throws: resolver/executor technical failure → `CommandOutcome.Failed`. No new executor surface.

**feature/launcher.** New Android-free `PendingRoutedAction(action, commandLine, riskLabel,
requiresConfirmation, permissionGate)` + VM `pendingRoutedAction: StateFlow<PendingRoutedAction?>`.
`applyOutcome(RoutedAction)` now arms a pending affordance instead of a display-only `Suggestion` (dead
`describeRouted` removed): `needsConfirmation=true` (CONFIRM risk, or any unregistered id — fail-safe) →
the DF-4 **confirm card**; `false` (SAFE) → a **one-tap** accelerator. Neither auto-executes (R4).
`confirmRoutedAction()` runs `ExecuteActionUseCase` and feeds the result back through `applyOutcome` (so a
successful launch clears input, nav routes, ambiguity surfaces exactly as a typed command would);
`cancelRoutedAction()` dismisses without executing; editing the field or any new outcome also dismisses a
stale card. Permission gate handled in the **screen** (keeps the VM Android-free): on confirm, a non-null
`permissionGate` routes to the existing `Routes.PermissionEducation.routeFor(gate.name)` (education-first,
safe fallback — no execution) instead of running. **Inert in the MVP catalog** (every `DefaultActionCatalog`
family has `permissionGate = null`) but wired + fake-catalog-tested so a future gated family is safe by
construction.

**core/ui (presentation-only).** New dumb `ConfirmActionCard(commandLine, riskLabel, onConfirm, onCancel)`
— **DF-4 "terminal confirm block"** (owner-approved, artifact-previewed): 1px accent-border block, sharp
corners (`SidrShapes.small`), monospace; header `EXECUTE?` + bracketed `[CONFIRM]` accent **risk chip**
(DF-4 risk-tag = bracketed accent chip); body `> commandLine`; bracketed `[ CANCEL ] / [ CONFIRM ]` buttons
(button role, 48dp targets, matches `RouteChip`). Strings + lambdas only → **no `domain → ui` edge** (the
feature maps `LauncherAction`/risk → strings). SAFE one-tap reuses the existing `RouteChipRow`.

**:app DI.** `provideExecuteActionUseCase` added to `IntentProvidesModule` (from the existing
`IntentActionResolver` + `ActionExecutor`); the VM injects `ExecuteActionUseCase` + the already-bound
`ActionCatalog` (read-only, for the `permissionGate` lookup). Hilt graph validates via `assembleDebug`.

**Forks.** DF-4 = terminal confirm block + bracketed accent risk chip (owner). Gate scope =
router-proposals-only (owner). R4 honored (no silent execution — SAFE = one-tap, CONFIRM = card). No
deviations from the plan's AIL-5 entry.

**§0 guards green.** Rule-only parity intact (structural). Hard rules intact: `domain` pure
(`ExecuteActionUseCase` stdlib+domain only); no `feature → feature` (screen uses `core/ui` + the
`core/common` `Routes` string builder, never `:feature:permission_education`); VM Android-free (permission
check in the screen, mirroring the mic flow); `core/ui → core/common` only (no domain import in
`ConfirmActionCard`); launcher fully offline; no LLM on the execution path. **Tests:** new
`ExecuteActionUseCaseTest` (11 — each family → correct outcome, execution/ resolver failure → `Failed`,
ambiguous → `NeedsConfirmation`, nav families never execute) + 5 new VM tests (CONFIRM → card, SAFE →
one-tap, confirm → executes + clears input/card, cancel → no execute, gated descriptor → carries the
permission gate); the AIL-4 display-only suggestion test was replaced. `LauncherViewModelTest` 70 → 75.
`./gradlew --no-daemon :domain:test testDebugUnitTest assembleDebug` green.

**Deferred to AIL-6:** SM-A325F device acceptance with a real BYOK provider (NL routing → confirm card →
execution; offline parity), CRT motion/typing on the card (DF-5), and `>`-glyph/card TalkBack polish.

**Next = AIL-6** — Polish + device acceptance: SM-A325F pass with a real BYOK provider (NL routing,
web/URL/Play-Store, confirmation, **offline parity**); docs + ADR + `current-status.md` sync.

## ADR 2026-07-06 — AIL-6 complete (Polish + SM-A325F device acceptance → Stage-1 AI-Launcher MVP CLOSED)

**What this block was.** The final AIL block: no new product runtime — it landed the (already-written,
build-green) AIL-4/5 code + the DF-5/6/7 polish that the WIP checkpoint bundled, cleared the RC build gate,
and executed the **mandatory SM-A325F device-acceptance pass** that every prior AIL block deferred here.
Forks DF-5/6/7 were pre-decided by the owner (Moderate motion / terminal `>_` mark / green↔amber accent
switcher); no fork was re-opened. Closing AIL-6 **closes the Stage-1 AI-Launcher MVP** (blocks AIL-0…6).

**Environment fix (no repo change).** The RC build first failed for an environment reason, not a code one:
the machine's system JDK had rolled to **25.0.3** (java-25/26 only; no 17/21), and Gradle 8.10.2's embedded
Kotlin cannot parse a Java-25 version string → cryptic `IllegalArgumentException: 25.0.3` during build-script
compilation. Fixed by running Gradle under Android Studio's bundled **JBR 21** and satisfying the project's
strict `jvmToolchain(17)` with a locally-downloaded **Temurin JDK 17** (`~/jdks/jdk-17.0.19+10`) via
`-Porg.gradle.java.installations.paths`, plus a git-ignored `local.properties` (`sdk.dir=~/Android/Sdk`).
No `build.gradle.kts`, toolchain pin, or committed file changed — this is a machine-setup note, recorded so
the next session doesn't re-derive it. **Gate green under JDK 17:**
`./gradlew --no-daemon :domain:test testDebugUnitTest assembleDebug :app:assembleRelease` → BUILD
SUCCESSFUL (release APK R8/resource-shrunk, ~79 MB; debug ~107 MB). `:app:assembleRelease` is the AIL-6-only
RC gate (§0) and is now proven.

**Device acceptance — SM-A325F / Android 13, fresh debug build, real BYOK provider.** Executed
agent-directed with the device on the build host: the agent drove `adb` (install, `am start`, taps, text,
`screencap`, `dumpsys`, airplane-mode) and adjudicated from screenshots + activity/log output; the **owner
entered the secret API key on-device only** (pre-filled non-secret Base URL `https://openrouter.ai/api/v1`
+ Model `openai/gpt-4o-mini` via adb; key typed on the phone, never echoed to chat/logs/files). Old
2026-07-04 build was signature-incompatible (different debug keystore) → uninstall+reinstall (wiped
`sidr_preferences`/`sidr_secrets`; key re-entered, as expected).

- **§8 one-field routing (router OFF = rule pipeline):** `youtube` → app match → launches YouTube; `github.com`
  → **SITE chip appears** (URL-gated; absent for non-URL queries) → opens Opera via `ACTION_VIEW`; `weather
  forecast` → WEB chip → browser search; `install spotify` → Play Store (`com.android.vending`, `market://`);
  typed `settings` → Settings screen; sparkle / typed → Assistant. **No embedded browser** (all external
  `ACTION_VIEW`). Mic affordance **visible** on the command row (ADR-262 probe fix confirmed on hardware).
- **NL router (llmRouterEnabled ON + provider + online):** *"can you take me to the github homepage"* →
  LLM proposed `open https://github.com` (open_url = **CONFIRM**) → **DF-4 confirm card** rendered (`EXECUTE?`
  + bracketed `[CONFIRM]` risk chip + `> open https://github.com` + `[ CANCEL ] / [ CONFIRM ]`); nothing
  auto-ran (**R4**). **CANCEL** dismissed with no execution (still on launcher); re-submit + **CONFIRM**
  executed (`START act=VIEW dat=https://github.com/... cmp=com.opera.browser` in logcat). *"I want to see
  everything installed on my phone"* → `show_apps` (**SAFE**) → **one-tap `[ ▸ apps ]` accelerator** (not a
  card) → tap dispatched. Full AIL-4→AIL-5 loop (LLM → RoutedAction → resolve → execute) proven on device.
- **Router-off / offline parity:** airplane-mode ON with the router flag still ON, the *same* NL command
  returned the plain rule fallback **"Unknown command. Try: open <app>, search <query>"** — the planner is
  not consulted offline (degrades byte-for-byte to the rule outcome). Typed `settings` nav + rendering work
  fully offline, no crash. Launcher core is offline-complete.
- **Privacy:** `adb logcat | grep sk-or- → 0` across the entire session (key never logged); the outbound
  allow-list guard tests are green (only command + action-catalog schema leaves); offline degradation
  corroborates no rogue calls. Key stored (form collapsed to the configured chat view) but **never
  displayed back**.
- **Visual polish (device is MID/HIGH → motion gate ON, not suppressed):** **DF-7** green↔amber accent
  switch applies **instantly** (whole UI recolors) and **survives `force-stop`** (persisted via
  `user_accent_color`); **DF-5** idle **block caret** visible on the empty prompt, and **chip press-invert**
  captured (held chip → solid-accent fill + ground-colored text); **DF-6** `>_` phosphor-green brand mark on
  the brand-dark launch surface (no white boot flash). Left the device on the owner default (green).

**Honest partials / not-driven (non-gating).** DF-5 **scanline overlay** is code-verified + motion-gated ON
but too faint (low-alpha lines) to distinguish in compressed `screencap` PNGs — not independently
frame-captured. **Assistant chat streaming** (C.1, a Phase-5 item, *not* an AIL-6 §8 gate) was not re-driven
via adb (on-screen-keyboard layout shift ate the input); the identical cloud transport
(`HttpClient`/config/Keystore) is nonetheless proven working end-to-end by the LLM-router round-trip.
**Boot-warmup reboot** (`RECEIVE_BOOT_COMPLETED` re-enqueue) and the **LOW_END motion-suppression** path
remain unexercised on this MID/HIGH device (separate device-matrix track). Cold-start perf remains
`PENDING/PERF-RISK` and is explicitly **not** a ship gate.

**§0 closure checklist — all pass.** Scope matches §5 AIL-6; forks were pre-decided (none silently
re-decided). Hard rules intact (`domain` pure; no `feature→feature`; ops return `OperationResult`; launcher
fully offline; the three ports `IntentMatcher`/`GenerativeAiEngine`/`CommandPlanner` stay distinct).
Router-off/offline/no-key ⇒ rule-only parity proven on device; privacy guard green. `:domain:test` +
`testDebugUnitTest` + `assembleDebug` + `:app:assembleRelease` green. Device pass executed on SM-A325F. ADR
appended; `CLAUDE.md` `Current goal` advanced to **Stage 2 (AI Framework)**; `current-status.md` + this
plan's §5 synced. **No code change was required by the device pass** (no acceptance-blocking bug found).

**Stage-1 AI-Launcher MVP is CLOSED (AIL-0…6).** The launcher ships AI-first: one universal input +
BYOK cloud LLM action router + Action Registry + web/URL/Play-Store routing + risk-gated confirmation, with
byte-for-byte rule-only parity when the router is off/offline/unconfigured. **Next = Stage 2 — AI
Framework** (generalize router/registry/context/memory into a reusable on-device AI framework: Action
Registry v2, Context Engine v2, User Memory), per the three-stage reframe. Separate/parallel tracks remain:
ONNX NLU (OQ#1/#2), embeddings (OQ#3), STT matrix (OQ#4), Android 9/11/14 + LOW_END device matrix, boot
warmup, cold-start perf.

## ADR 2026-07-06 — Stage 2 kickoff: "Learned Resolutions" design spec (block S2-1, DESIGN-ONLY)

**Status: DESIGN, not started — no code, no implementation plan, no Room schema change, no use-case
wiring.** This entry records only that the first Stage-2 slice has an owner-approved *design spec*; the
`writing-plans` step is deferred until the owner approves the written document.

**Decision (owner, 2026-07-06):** Stage 2 starts **feature-first** — build one narrow complete user
capability and grow only the minimal memory/context abstractions it needs; do **not** build a universal
User Memory / Context Engine up front. The chosen first capability is **context-aware routing → learned
on-device resolutions**: when a launch command is ambiguous, the launcher learns which app the user meant
and prefers it next time (rank-first, then threshold auto-resolve), fully on-device, no LLM/cloud.

**Design spec:** [docs/superpowers/specs/2026-07-06-learned-resolutions-design.md](../docs/superpowers/specs/2026-07-06-learned-resolutions-design.md)
— brainstormed and approved section-by-section. Key locked points:
- Memory model is **`(Capability/Intent, CandidateSet awareness, ResolutionContext) → PreferredTarget +
  Evidence`**, not `command → target` (owner's steer). v1 minimal (Context = `None`, target = app), shaped
  to grow toward User Memory → Context Engine → World Model.
- Deterministic **`ResolutionPreferencePolicy`** (pure): `NoPreference` / `Stale` / `RankFirst` /
  `AutoResolve`; auto-resolve **only** SAFE + CONFIDENT (`streak ≥ K=3`, `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD`)
  + candidate-set-fingerprint match. Confidence lives in the policy (a replaceable `strength()` seam), not
  storage; storage holds only raw evidence.
- Integration **Approach A** (policy in `domain`, `domain/memory/resolution/`), applied only on the rule
  ambiguity branch, recorded only after a **successful explicit** choice; the VM shuttles an opaque
  transient `ResolutionLearningToken` (no business logic in UI). **`HandleUserCommandUseCase` untouched;
  no-preference / non-ambiguous ⇒ byte-for-byte parity.**
- Persistence: Room table `resolution_preferences` in `:data:repository` (never-throw store, lazy +
  display-time invalidation, `MAX_RESOLUTION_PREFERENCES = 500` LRU, migration + privacy-inventory guard).
- Privacy: `query`/packageName classified **local-sensitive metadata**; strictly on-device; **outbound
  allow-list widened by zero** (preferences never enter an `AiRequest`); `query` = normalized slot only,
  `MAX_QUERY_LENGTH = 64`, non-empty, app-ambiguity-only; no PII in external logs.
- Management: Settings → **Learned Choices** (`Routes.LearnedChoices`, `:feature:settings`) — See + Delete
  in v1 (change = delete-then-relearn); **honest display state** (`Auto` shown only when the same
  eligibility the policy checks is verifiable, else a safe `AutoReady`/`learned`/`needs reconfirm` label).
- Correction (v1): hard-switch in the learning/rank-first phase; **no in-flow correction after
  auto-resolve** — the post-auto-resolve channel is Settings → Learned Choices → delete → re-learn.

**Explicitly NOT authorized yet (owner):** production code, implementation plans, Room schema changes,
use-case wiring. Next action after approval = `writing-plans` for block S2-1.

## ADR 2026-07-10 — S2-1 "Learned Resolutions" complete (code-closed; device-pending)

**Status: CODE-CLOSED, build-green, device-acceptance-pending.** The plan
([docs/superpowers/plans/2026-07-06-learned-resolutions.md](../docs/superpowers/plans/2026-07-06-learned-resolutions.md))
Tasks 1–15 (Phases A–E) are implemented and merged on `launcher-4`; Task 16's build gate is green; only
the on-device SM-A325F acceptance pass + screenshots remain before the block is fully CLOSED. Supersedes
the DESIGN-ONLY status of the 2026-07-06 kickoff ADR — everything that entry marked "NOT authorized yet"
(production code, Room schema change, use-case wiring) is now built exactly to the approved design.

**Closing note (2026-07-11):** the pending SM-A325F acceptance pass is now complete; S2-1 is CLOSED by ADR
"2026-07-11 — S2-1 Learned Resolutions device accepted + closed". This entry remains the code-closed /
build-green record.

**What shipped (on-device learning of which app an ambiguous launch command meant — rank-first → threshold
auto-resolve, fully offline, no LLM/cloud):**
- **Domain** (`domain/memory/resolution/`, pure): value types + `ResolutionPreferenceStore` port +
  `fingerprintOf`; deterministic `DefaultResolutionPreferencePolicy` (`NoPreference`/`Stale`/`RankFirst`/
  `AutoResolve`; auto-resolve **only** SAFE + `streak ≥ DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD = 3` +
  candidate-set-fingerprint match); `RecordResolutionChoiceUseCase` (create/reinforce/hard-switch,
  never-throws, no-op on blank/over-`MAX_QUERY_LENGTH = 64` query); `ResolveCommandWithPreferenceUseCase`
  decorator (wraps the rule-first router via the `CommandRouteStep` seam; applies the policy **only** on
  `CommandOutcome.NeedsConfirmation`; emits `ResolvedCommand.Outcome`(+opaque `ResolutionLearningToken`)
  or the `AutoLaunch` **directive** — never a premature `Executed`); narrow deterministic
  `LaunchSlotExtractor` (LAUNCH_APP-ambiguity only, not a parser); observe/delete/prune use-cases +
  `EvaluateLearnedChoiceDisplayStateUseCase` (**honest** display: `Auto` only when policy-verifiable, else
  `AutoReady`/`Learning n/K`/`NeedsReconfirm`).
- **Persistence** (`:data:repository`): `ResolutionPreferenceEntity` + DAO + `ResolutionPreferenceStoreImpl`
  (+ mapper, never-throws); **`SidrDatabase` v1 → v2 + `Migration1To2`** adding table `resolution_preferences`
  (migrated, NOT destructive-recreated), golden `schemas/2.json` committed; `RoomColumnNames`/privacy
  inventory extended (the `resolution_preferences.query` vs forbidden-term `"query"` collision documented +
  scoped).
- **DI** (`:app`): `MemoryProvidesModule` (all use-cases + the `CommandRouteStep` seam over
  `RouteCommandUseCase`) + `MemoryBindsModule` (`ResolutionPreferenceStore` `@Binds`).
- **Runtime wiring** (`:feature:launcher`): `LauncherViewModel` routes `onCommandSubmitted` through
  `resolveCommand.resolve()`; `AutoLaunch` → direct `launchApp` (input clears only on real success, else
  renders the reordered fallback); `recordChoiceIfPending()` records on an explicit candidate/grid tap
  **only** when a `token.isAppAmbiguityFlow` pending token covers the chosen package (fire-and-forget on
  `applicationScope`); pending token cleared on clear-input.
- **Management UI**: `Routes.LearnedChoices` + dumb `core/ui` `LearnedChoiceRow` (no `domain→ui` edge) +
  `LearnedChoicesViewModel` (`:feature:settings`, guarded flow, prune-on-load, fire-and-forget delete) +
  `LearnedChoicesScreen` + Settings `[ learned choices ]` entry + `AppNavHost` registration.

**Guards / parity (Task 15, green):** `ResolutionPrivacyScopeGuardTest` (recording only via an
`isAppAmbiguityFlow` token; scope contract); outbound allow-list **widened by zero** (preferences never
enter an `AiRequest`); `domain/memory/resolution` sources carry no generative/`AiRequest` import;
`RoomColumnNamesGuardTest`. **Parity:** `HandleUserCommandUseCase`/`RouteCommandUseCase` untouched →
no-preference / non-ambiguous / router-off ⇒ byte-for-byte the pre-S2-1 outcome path (proven in the
`LauncherViewModelTest` + `ResolveCommandWithPreferenceUseCaseTest` suites).

**Build gate (Task 16, green, 2026-07-10):** `:domain:test` + `testDebugUnitTest` + `assembleDebug`
BUILD SUCCESSFUL. **Env note (no repo change, same as AIL-6):** the machine's JDK had rolled to 25/26
(Gradle 8.10.2 can't parse it); built by running Gradle under Android Studio's **JBR 21** with a
locally-downloaded **JDK 17** toolchain (`-Porg.gradle.java.installations.paths=…/jdk-17.0.19+10`).

**Closed by follow-up ADR (2026-07-11):** SM-A325F device acceptance, on-device parity, and screenshots
completed with no production code changes.

## ADR 2026-07-10 — Agentic OS target architecture (A1–A6) + visual identity (soft classic grey)

**Status: DIRECTION ACCEPTED (owner).** Records two owner-approved strategic decisions from a
design+architecture audit. Neither is a committed implementation plan; both are north-star direction that
each future slice honors (spec → plan → build, in the existing feature-first / ports / rule-first /
fail-closed / privacy-bounded style).

**1. Visual identity = "soft classic grey" (supersedes green/amber).** A single neutral grey identity
(dark default + light "paper"), full token set in
[docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md](../docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md).
Drops the user-selectable green/amber accent (AIL-0 / imported v1.1 Doctrine) and the amber-only v1
palette. Locked points: **brand accent (pewter) and semantic status are separate palettes** (risk always
reads the same); **provenance line** is a named primitive; **press-invert** selection (colourless);
terminal *gestures* kept (`>`, block caret) but terminal *colour* (phosphor green / CRT / scanlines)
dropped; **tri-font** roles (mono = interface shell, sans = prose, serif = sacred/Shahada); Home shows an
English Shahada + Gregorian/Hijri date (no Android-statusbar duplication) + a demoted prayer strip.
Everything else in the imported doctrine (principles, base-4 spacing, motion, accessibility, risk-not-by-
colour-alone, confirmation model, `core/ui` boundaries) remains in force.

**2. Agentic OS target architecture = A1–A6.** Full doc:
[docs/agentic-os-architecture.md](../docs/agentic-os-architecture.md). Core finding: today's pipeline is a
single-shot smart router (`RouteCommandUseCase` → `CommandPlanner` → `ExecuteActionUseCase`), not an agent;
the imported design docs draw agentic *UI* with **no engine behind it**. The originality and the hard work
live in four missing layers. The six-layer target, each grown from an existing seed:
- **A1 Tool/Capability** (from `ActionCatalog`/`ActionRiskLevel`/`LauncherAction`) — typed tools with
  risk/preconditions/tier(IN_APP/SYSTEM_INTENT/ACCESSIBILITY)/cost; the *only* world-effecting boundary.
- **A2 Context Engine v2** — `ContextSnapshot` (reduced, never raw) + fault-isolated providers + extended
  `OutboundContextPolicy` allow-list.
- **A3 User Memory** (generalizes S2-1 `ResolutionPreferenceStore` + S2-2 `AliasStore`) — typed
  editable/deletable/local memory; never auto-enters cloud.
- **A4 Agent Runtime (the missing core)** — rule-first `Planner` (template → local → cloud) + bounded,
  fail-closed, cancellable `AgentExecutor` with **consent woven into the loop** + persisted `ExecutionTrace`.
- **A5 Activity/Trace** — honest history from real traces; **ephemeral by default, opt-in persist**.
- **A6 Grants + automation (Stage 3)** — per-agent grants, quiet hours, audit; accessibility tier opt-in only.

**Cross-cutting invariants (all layers):** clean-arch/ports; rule-first/deterministic-first; fail-closed;
privacy allow-list as the sole egress; consent & human authority (risky never auto-runs, user owns the
loop); provenance on every consequential action. **Golden rule: a surface's UI is built only when its
engine is real** (no "agent dashboard without agents"). **Roadmap mapping:** A1–A3 ≈ Stage 2 (Framework),
A4–A5 bridge into Stage 3, A6 = Stage 3. **Next architectural slice = A1** (also enriches S2-2:
`alias → tool-call`). Deferred: the 5-tab nav / live Agents surface (until A4/A6 exist).

## ADR 2026-07-11 — DS-1 complete (soft-classic-grey token layer + Roborazzi screenshot harness)

**Presentation-only; no domain/data/routing/execution/privacy/memory/navigation change.** Landed the
approved "soft classic grey" identity
([spec](../docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md) §2/§3/§4) at the
`core/ui/theme/` token level and stood up the Roborazzi screenshot harness the repo lacked. Plan:
[docs/superpowers/plans/2026-07-10-ds1-grey-token-layer.md](../docs/superpowers/plans/2026-07-10-ds1-grey-token-layer.md)
(STATUS DONE).

- **New `SidrColors`** (`@Immutable` data class) + `SidrDarkColors`/`SidrLightColors` (every §2 role, both
  themes) + `LocalSidrColors` CompositionLocal. **Accent (pewter) and fixed status
  (`success/attention/caution/danger/info`) are separate token sets** — status is never re-tinted by the
  accent (spec-lock, guard-tested `accent_and_status_are_distinct_tokens`).
- **Grey Material `ColorScheme`** — `GreyDarkColorScheme`/`GreyLightColorScheme` derived from `SidrColors`
  **replace** the four green/amber terminal schemes (`Green*/Amber*` deleted from `Color.kt`).
- **Tri-font typography** (spec §3): `SidrSans = FontFamily.SansSerif` (prose), `SidrSerif =
  FontFamily.Serif` (sacred); `SidrTypography` remaps body/title slots → sans while label slots stay
  `JetBrainsMono` (the identity). New `SidrTextStyles.Default` (`command`/`system`/`provenance`/`sacred`)
  read via `SidrTheme.textStyles`.
- **Softened shape scale** (spec §4): `SidrShapes` 4/7/10/12dp (was brutalist 0–8dp).
- **`SidrTheme` unchanged signature** `(darkTheme, accent, dynamicColor, content)` — `accent` retained for
  source compat but **inert** (grey for every value; `AccentColor{GREEN,AMBER}` enum kept, stored
  `accentColor`/`themeName` preference keys untouched). Provides `LocalSidrColors` + exposes
  `SidrTheme.colors`/`SidrTheme.textStyles`. `dynamicColor` branch kept (off by default). No screen
  restructuring (that is DS-3/DS-4).
- **Global CRT scanline overlay dropped** from `LauncherActivity` (identity is grey, not phosphor);
  `Modifier.sidrScanlines` stays defined for optional dev/boot use, `LocalSidrMotionEnabled` (block caret)
  intact.
- **Roborazzi harness** (`io.github.takahirom.roborazzi` 1.26.0, JVM/Robolectric, no device) in `:core:ui`
  + `robolectric.properties` (sdk 34, w360dp-h800dp-xhdpi) + committed goldens under
  `core/ui/src/test/screenshots/` (`harness_smoke`, `grey_sample_dark`, `grey_sample_light`) +
  `@SidrThemePreviews` dark+light multipreview. **Env note:** on this machine `~/.gradle/gradle.properties`
  pins `org.gradle.java.home` to a local JDK 17, so a plain `./gradlew` works (no JAVA_HOME prefix). Golden
  capture paths are **module-relative** (`src/test/screenshots/…`), since the unit-test JVM's working
  directory is the module dir.

**Build gate green:** `:core:ui:testDebugUnitTest testDebugUnitTest assembleDebug
:core:ui:verifyRoborazziDebug` BUILD SUCCESSFUL. Existing feature tests unchanged (they assert behaviour,
not colour). Follow-ups per spec: DS-2/3 primitives (provenance line, press-invert chips), DS-4 Home, DS-6A
sacred header, DS-7 memory-migration (absorbs the S2-2 Aliases UI so it is built in the grey language).

## ADR 2026-07-11 — DS-0 (design provenance backfill: imported docs committed + conflict/deviation record)

**Status: RECORD (docs-only, zero production code).** DS-0 was skipped when the redesign leap-frogged
straight to the grey identity spec (2026-07-10) + DS-1 (2026-07-11). This ADR backfills it: it commits the
seven imported source documents into the repo and writes the reconciliation that was previously only
implicit — the three v1-vs-v1.1 conflicts, the nine engineering deviations/additions from implementation,
and the DS-block sequence. It **records** decisions already accepted (see ADR 2026-07-10 "Agentic OS target
architecture + visual identity"); it changes no code and introduces no new direction.

**Imported docs now in-repo** ([docs/design/](../docs/design/), index in its `README.md`): `SIDR Design
System v1` (**v1 = Vision/North-Star**), `SIDR Design Doctrine & Foundation v1.1` (**v1.1 = Governing
doctrine**), `SIDR Component Library v1.1`, `SIDR Design Migration Plan v1.1`, `SIDR Visual Acceptance Spec
v1.1` (v1.1 supporting), plus two inputs — `SIDR Current UI Inventory & Gap Map` and `SIDR Design &
Architecture Audit`. These are an **archive of inputs**, not the governing spec; the living specs are
[visual identity (soft classic grey)](../docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md)
and [agentic-os-architecture.md](../docs/agentic-os-architecture.md).

**Three v1-vs-v1.1 conflicts — resolved (v1.1 governs, then reduced):**
1. **Information architecture / navigation.** v1 draws a **5-tab** bottom nav (incl. a live *Agents* tab);
   v1.1 is more restrained. → **4 surfaces** (Home · App Drawer · Assistant · Settings). No standalone
   Agents tab, no persistent Activity journal, until the A4 agent runtime is real (golden rule). Agentic
   task-flow screens stay a FUTURE contract.
2. **Agentic surfaces shown as live.** v1 renders Agents + an Activity journal as if the engine exists. →
   **deferred to A4/A6**; Activity is ephemeral-by-default (opt-in persist), built only on real
   `ExecutionTrace`s.
3. **Accent / colour.** v1 = amber-only; v1.1 = green-default / amber-alt, user-selectable. → **both
   dropped** for a single **soft-classic-grey** identity; brand accent (pewter) and semantic status are
   **separate** palettes (accent ≠ risk).

**Nine engineering deviations/additions (from implementation, folded into the specs):**
1. **4 surfaces, not 5** (as above) — cut the surface the engine can't yet back.
2. **Accent ≠ status.** Two fixed, separate palettes; status never re-tinted; risk = label + marker +
   consequence text + separate confirm, never colour alone.
3. **Mono stays wide.** A launcher is mostly interface, and the mono shell is the recognizable signature →
   **tri-font** (mono = interface shell, sans = genuine prose only, serif = sacred/Shahada). Deliberate
   deviation from the docs' sans-widening dual-font, plus the added serif sacred role.
4. **Activity is ephemeral by default** — real traces, opt-in persist; not a standing journal surface (A5).
5. **Prayer = correctness-bar, not a widget.** Explicit user-selectable calculation authority/method,
   TZ/DST-correct, cached-offline; **never show a computed time without provenance, and fail visibly rather
   than show a plausible-but-wrong time**; precise location never leaves the device / never enters an
   `AiRequest`. (Own track — DS-6B.)
6. **Provenance line — one named primitive.** `SOURCE · detail · freshness` (faint mono), reused everywhere
   a thing has an origin (learned pref, routed-by-AI, local/cloud, cached, method · latency). "System truth"
   made reusable. (DS-2/3.)
7. **The more ordinary the action, the less UI it generates.** A plain app launch produces **no** result
   card, **no** disclosure, **no** toast; disclosures/result surfaces appear only for routed/agentic/risky
   actions or a meaningful learning event. First-class principle — the design system is measured by how
   invisible it is for the boring 95%.
8. **Screenshot-test harness is a DS-1 prerequisite.** The imported plan's "each block ends with screenshot
   tests" assumed infra the repo lacked (no Paparazzi/Roborazzi/Showkase) → DS-1 had to stand up **Roborazzi**
   + a preview matrix. **DONE** (see ADR 2026-07-11 — DS-1).
9. **Keep the terminal *signature*, drop the CRT.** `>` prompt, block caret, press-invert chips are cheap,
   functional, distinctive → retained as a residual terminal signature so the evolution reads as "matured",
   not "replaced by generic sans". Global scanline/phosphor overlay dropped.

**Preserved strengths (explicitly NOT touched):** system-truth-over-visual-effect + no-fake-agency;
risk-not-by-colour-alone + consequence-always-visible; reuse→evolve→replace + token/primitive/component/
pattern layering; privacy visible at the point of decision (local/cloud disclosure).

**DS-block sequence (authoritative):** DS-0 (this) → **DS-1** tokens + Roborazzi harness (**DONE**) →
DS-2/3 primitives & controls (provenance-line primitive + press-invert chips) → DS-4 Home (§5 composition,
routing parity) → DS-6A sacred header (English Shahada, Arabic-capable) → DS-6B prayer data (separate spec:
brainstorm → plan) → DS-7 memory-migration (absorbs the S2-2 Aliases UI so it is built in grey). No routing/
execution/privacy/memory/navigation semantics change anywhere in the DS track — presentation only, parity
rules from the migration apply.

## ADR 2026-07-11 — S2-1 Learned Resolutions device accepted + closed

**Status: CLOSED.** This closes Task 16 and the S2-1 "Learned Resolutions" plan:
[docs/superpowers/plans/2026-07-06-learned-resolutions.md](../docs/superpowers/plans/2026-07-06-learned-resolutions.md).
The 2026-07-10 ADR remains the code-closed/build-green record; this entry records the SM-A325F acceptance
pass that was pending there. No production code changed in this closeout.

**Device acceptance (SM-A325F / Android 13, debug APK):** used two temporary local fixture apps with the
same launcher label, `SidrProbe` (`com.sidr.probe.a` and `com.sidr.probe.b`), created under `/tmp` only for
this acceptance run, installed with `adb install -r`, and uninstalled before finishing. The fixtures let
the launcher exercise a real same-label app ambiguity without relying on whatever apps happened to be on
the device.

**Observed slice:**
- `open SidrProbe` showed two `Did you mean:` candidates.
- Choosing the same candidate recorded learning; Settings → Learned Choices showed `sidrprobe` with
  `learning 1/3`.
- After three explicit choices, the next `open SidrProbe` auto-launched the learned target with no
  candidate tap. The management screen displayed `[auto-ready]` rather than `[auto]`; this is honest for
  display-verification purposes, while runtime auto-launch was directly observed.
- Deleting the learned row returned the screen to `No learned choices yet`; the next `open SidrProbe`
  re-shown the candidates.
- Correction before K worked: after one A choice, choosing B switched the ranked-first target; tapping the
  upper candidate then opened `SidrProbe B`.
- Preferred-target uninstall invalidated the learned preference: uninstalling `com.sidr.probe.b` made
  `open SidrProbe` launch remaining A, and Learned Choices showed `No learned choices yet` (no stale
  auto-resolve / no stale row).

**Parity / privacy:** with no learned preferences, a non-ambiguous typed command still worked; with Smart
command routing temporarily OFF, `open Salatuk` launched Salatuk, and the flag was restored to ON. The
learned-resolution path stayed on-device only: `query`/packageName were displayed locally in Sidr, not sent
to an LLM/cloud path, and Task 15's outbound/dependency/scope guard tests remain the privacy gate.

**Evidence:** screenshots were captured under `/tmp/sidr_acceptance_*.png`, including ambiguity,
`learning 1/3`, auto-launch, delete/relearn, correction A→B, preferred-target uninstall invalidation, and
router-off parity. Temporary `SidrProbe` packages were removed; `adb shell pm list packages com.sidr.probe`
returned no packages after cleanup.

## ADR 2026-07-11 — DS-2 primitives complete (presentation-only)

**Design-track block DS-2** ("SIDR primitives", Master Plan v1.2 §11) is DONE on `launcher-4`. Presentation-
only, additive: **no production screen / nav / ViewModel / persistence / domain change**. Spec:
[docs/superpowers/specs/2026-07-11-ds2-primitives-design.md](../docs/superpowers/specs/2026-07-11-ds2-primitives-design.md);
plan (DONE): [docs/superpowers/plans/2026-07-11-ds2-primitives.md](../docs/superpowers/plans/2026-07-11-ds2-primitives.md).

- **New `core/ui/primitive/`** (8 primitives) + **`theme/Strokes.kt`** (hairline 1dp / focus 2dp):
  `SidrText` (tri-font entry via `SidrTextRole{COMMAND,SYSTEM,PROVENANCE,SACRED,HUMAN_BODY,HUMAN_TITLE,LABEL}`
  → `SidrTextStyles`/Typography + colour role), `SidrSystemLabel` (mono uppercase), **`SidrProvenanceLine`
  (keystone** — semantic `source: String, details: List<String>`, faint-mono `SOURCE · detail`, uppercased,
  wraps, TalkBack reads a composed sentence not glyphs), `SidrStatusMarker` (fixed status dot **+** label —
  status never colour-only, never the accent), `SidrSurface` (`SidrSurfaceTone{GROUND,SURFACE,RAISED,SACRED,
  RISK}`; elevation-by-1px-line; RISK gets a caution hairline border; press-invert deferred to DS-3),
  `SidrDivider`, `Modifier.sidrFocusRing()` (2px accent), `SidrProgress` (quiet indeterminate/determinate).
- **Logic unit-tested on JVM** (role→style/colour, provenance format+description, status→token, tone→
  background/border); **look verified by Roborazzi goldens** `primitives_{dark,light,fontscale2,rtl}` — the
  §14/§20.1 acceptance (dark/light, font-scale 2.0 wraps not truncates, RTL mirrors, status≠accent). A
  **dependency guard** proves no `domain`/`feature` import in `core/ui/primitive/`; a semantics test proves
  the provenance composed description + status label are TalkBack-readable.
- **Reconciliation note:** the standalone "principles rulebook" spec was folded into Master Plan v1.2 (§5.1
  precedence, §5.2 verification vocabulary, §20.1 calm budgets) and removed — one governing source.
- **Env:** built with plain `./gradlew` (this machine pins `org.gradle.java.home` to a local JDK 17 in
  `~/.gradle/gradle.properties`; no `JAVA_HOME` prefix). Goldens use module-relative paths.

**Build gate green:** `:core:ui:testDebugUnitTest testDebugUnitTest assembleDebug :core:ui:verifyRoborazziDebug`
BUILD SUCCESSFUL. Feature tests unaffected (additive). **Next design block:** DS-3 (controls — buttons,
chips incl. press-invert, rows), which composes these primitives; production-screen migration begins there.

## ADR 2026-07-11 — DS-3 controls complete (presentation-only)

**Design-track block DS-3** ("SIDR Controls", Master Plan v1.2 §11 DS-3, spec §1) is DONE on `launcher-4`.
Presentation-only, parity-preserving: new reusable controls land in `core/ui/component`, Settings proves the
row/control language, and routed confirmation migrates through `SidrActionGate`. Spec:
[docs/superpowers/specs/2026-07-11-ds3-controls-design.md](../docs/superpowers/specs/2026-07-11-ds3-controls-design.md);
plan (DONE): [docs/superpowers/plans/2026-07-11-ds3-controls.md](../docs/superpowers/plans/2026-07-11-ds3-controls.md).

- **New `core/ui/component/` controls** (6 files): `SidrButton.kt` (`SidrPrimaryButton`,
  `SidrSecondaryButton`, `SidrTertiaryButton`, `SidrDestructiveButton`, `SidrTerminalAction` — shared
  internal frame, 48dp min target, loading disables duplicate taps, destructive muted border/text not red
  fill), `SidrChip.kt` (`SidrRouteChip`, `SidrFilterChip`, `SidrSuggestionChip`, `SidrActionChip`,
  `SidrStatusChip`, `SidrRiskChip` + `SidrRiskTone{Safe,Confirm,External,Destructive}` — press-invert
  selected/pressed flips fg/bg, no glow/scale/layout shift; status/risk use fixed tokens never accent),
  `SidrRow.kt` (`SidrNavigationRow`, `SidrToggleRow` — single `toggleable` owner, switch `onCheckedChange=null`,
  `SidrChoiceRow` — radio role, `SidrStatusRow`, `SidrDestructiveRow` — explicit danger label),
  `SidrTopBar.kt` (`SidrSectionHeader`, `SidrAlphabetHeader`, `SidrTopBar` — presentation-only, never owns
  navigation), `SidrIconButton.kt` (`ImageVector` + `Painter` overloads; `TopBarIcon` kept as compat wrapper),
  `SidrActionGate.kt` (`SidrActionGateType{Confirmation,Permission,SensitiveData,ExternalHandoff,Destructive}`
  + `SidrActionGate` — composes `SidrSurface`/`SidrText`/`SidrRiskChip`/SIDR buttons; consequence always
  visible; Cancel always visible; `confirming` disables both; URL wraps).
- **Tests:** `ControlsDependencyGuardTest` (no domain/data/feature in `Sidr*.kt`), `SidrButtonTest` (disabled
  blocks click, loading blocks duplicate, terminal action), `SidrChipTest` (risk->status mapping never
  accent, label readable, filter click), `SidrRowSemanticsTest` (toggle single owner, choice radio,
  navigation title/value, destructive label, status row), `SidrActionGateTest` (consequence visible, target
  visible, cancel once, confirm once, confirming disables both). **Roborazzi goldens**
  `controls_{dark,light,fontscale2,rtl}` cover the full control-state matrix.
- **Settings proof surface:** `SettingsScreen` migrated to `SidrTopBar` + `SidrIconButton`, `SidrSectionHeader`,
  `SidrToggleRow`, `SidrChoiceRow`, `SidrNavigationRow`, `SidrFilterChip`. **No ViewModel contract change, no
  persisted key change, no new setting, no routing change** — all `SettingsViewModel` callbacks, the
  `defaultLauncherIntent` flow, and every preference key are byte-for-byte preserved. Existing Settings VM
  tests pass unchanged.
- **Routed confirmation migration:** `ConfirmActionCard` usage in `LauncherScreen.PendingActionArea` ->
  `SidrActionGate` (CONFIRM-risk branch only; SAFE one-tap `RouteChipRow` left intact for parity).
  `onConfirm`/`onCancel` callbacks preserved exactly; SAFE vs CONFIRM distinction intact; no auto-execution.
  Launcher VM tests pass unchanged (behavior-level, not UI-level).
- **Legacy components** (`ConfirmActionCard`, `SectionHeader`, `TopBarIcon`, `RouteChipRow`) kept as compat
  wrappers / deprecated-in-comments only after call sites reach zero; no mass deletion.

**Full verification gate green:** `:core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
:feature:settings:testDebugUnitTest :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug`
BUILD SUCCESSFUL. **Next design block:** DS-4 (Home / Universal Input), which builds on these controls.

## ADR 2026-07-11 — DS-4 complete (Home shell + Universal Input; device-accepted)

**Design-track block DS-4** ("Home Shell and Universal Input", Master Plan v1.2 DS-4, Migration Plan v1.1
DS-4) is DONE on `launcher-6`. Presentation/composition migration only: Home moves from the transitional
terminal shell to the approved soft-classic-grey intent-first layout, and the legacy `SidrCommandPrompt`
Home field is replaced by `SidrUniversalInput`. **The command pipeline, input routing, voice path, app
launch, and offline/router-off parity are unchanged — `LauncherViewModelTest` passes byte-for-byte throughout.**
Spec: [docs/superpowers/specs/2026-07-11-ds4-home-universal-input-design.md](../docs/superpowers/specs/2026-07-11-ds4-home-universal-input-design.md);
plan (DONE): [docs/superpowers/plans/2026-07-11-ds4-home-universal-input.md](../docs/superpowers/plans/2026-07-11-ds4-home-universal-input.md).

**Prior-session handoff fix (found first):** `SidrUniversalInput.kt` + `SidrUniversalInputTest.kt` were
pre-built but `:core:ui:testDebugUnitTest` was **red** — `ControlsScreenshotTest` referenced an undefined
`captureInput` helper, and `SidrUniversalInputTest` was missing `@RunWith(RobolectricTestRunner::class)` +
`@Config(sdk=[34])` (9 NPEs at `ComposeUiTest`). Both fixed; core:ui green.

- **`core/ui/component/SidrUniversalInput.kt`** (spec §7): deep presentation module — `>` prompt marker
  (semantics `contentDescription="Prompt"`, never read as "greater than"), block caret (idle+empty+unfocused),
  mic (explicit `voiceAvailable`/`onVoiceClick`, Listening state), 48dp clear affordance, `routeContent`
  slot (feature-owned route mapping), `supportingText`, **parameterless `onSubmit`** wired to IME Search.
  `SidrUniversalInputState{Idle,Focused,Typing,Listening,Interpreting,Ambiguous,Proposed,Executing,Error,
  Disabled}` — only observable states mapped in production (Idle/Typing); the rest stay preview-only (no
  faked spinner/waveform). Composes DS-2 `SidrSurface`/`SidrText` + DS-3 controls; no domain/feature import.
- **`LauncherScreen` migration** (feature-local, no `core/ui` LauncherScreen):
  - **Input:** `SidrCommandPrompt` → `SidrUniversalInput`; `onValueChange=onCommandChanged`,
    `onSubmit={ onCommandSubmitted(commandInput) }`, clear via `onCommandChanged("")`, mic via existing
    `onMicTap`. `onCommandSubmitted` semantics untouched.
  - **Route chips (Task 3/6):** results overtake reworked — DS-3 `SidrRouteChip` (WEB/ASK/SITE, colourless
    press-invert, order preserved, `horizontalScroll` for large font) replaces legacy `RouteChip`/`RouteChipRow`;
    app rows use DS-2 `SidrText` + fixed `heightIn(minTouchTarget)` so async icons never resize the row or
    shift the input. Callbacks (`submitWebSearch`/`submitSite`/Ask-prefill via `Uri.encode`) unchanged.
  - **Feedback + pending (Task 7):** `CommandFeedback` on DS-2 `SidrText`; `Ambiguous` reads as
    "Did you mean:" clarification (not error); CONFIRM → `SidrActionGate`, SAFE → one-tap `SidrRouteChip`
    (last legacy route-chip usage removed). Behavioural parity: candidate/SAFE/CONFIRM all require a
    deliberate tap; Cancel dismisses; nothing auto-executes (R4).
  - **Home shell (Task 4/8, owner decisions):** new `HomeTopRow` = `SIDR` wordmark (7-tap dev-arm preserved)
    + **Gregorian + Hijri** date (real `java.time.chrono.HijrahDate`, NOT prayer data — DS-6B owns
    correctness) + Settings `SidrIconButton`; **empty `HomeAnchorSlot`** seam reserved for DS-6A (renders
    nothing — no fake prayer/sacred data, spec §6); bottom `CommandBar` **retired** → All Apps + Assistant
    as `SidrNavigationRow`s + local-first `HomePrivacyLine`, kept outside the state Box so all three
    (+ Settings) stay discoverable across loading/empty/error. Retired AIL-6 online/clock `HomeStatus`.
- **Owner decisions (AskUserQuestion 2026-07-11):** sacred anchor = **empty seam only**; date =
  **Gregorian + Hijri**; CommandBar = **retire + redistribute**.
- **Screenshots:** 3 new `universal_input_{idle,typing,light}` Roborazzi goldens recorded + verified.
- **`SidrCommandPrompt` `@Deprecated`** (`ReplaceWith("SidrUniversalInput")`) — production usage is zero;
  self-referencing previews `@Suppress("DEPRECATION")`.

**Full gate green** (JDK 17 toolchain at `/home/Suleiman/jdks/jdk-17.0.19+10`; the machine's system JDK is
25 again, which Gradle 8.10.2 can't parse — same env note as AIL-6): `:core:ui:testDebugUnitTest
:core:ui:verifyRoborazziDebug :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug` BUILD
SUCCESSFUL. **Device acceptance PASS (SM-A325F / RF8R705H38F, agent-drove adb):** first frame no
spinner/flash; typed `salat` → overtake + stable `Salatuk` row → launch (`com.masarat.salati` foreground);
**WEB** + **SITE** (URL-gated chip, `github.com`) → Opera; **ASK** → Assistant with prompt prefilled and
**not auto-sent**; **router-off NL** (`take me somewhere nice`) → "Unknown command" rule fallback (parity);
Settings icon → DS-3 Settings; All apps row → App Drawer; clear input works; Gregorian `сб, 11 июл.` +
Hijri `26 мухаррам` render; empty sacred seam renders nothing; privacy line present; **no launcher crash**
across the session. Not exercised (owner-driven / unchanged by DS-4): SAFE/CONFIRM router proposals (need
router ON + BYOK key on-device; `SidrActionGate` path unchanged, proven in AIL-6/DS-3), mic-education (mic
already granted), airplane fallback (parity). **Next design block:** DS-5 (Action & Safety).

### DS-4 addendum (2026-07-11) — visual-fidelity pass to the artifact HTML

Owner feedback after the first DS-4 pass: "changed nothing but colours — why doesn't it match the
artifact?" Correct. The first pass preserved the **cyberpunk-terminal structure** and only swapped tokens;
worse, the DS-3 primitives still encoded the terminal look. Root cause verified in code:
`SidrPressInvertChip` rendered **borderless** text at rest, so route chips read as bare mono text, not the
artifact's outlined pill buttons. Read the **actual artifact HTML** (not the PNG captures) via WebFetch of
`claude.ai/code/artifact/e34033dd…` and mapped its CSS to the repo — the DS-1 tokens already match the
artifact byte-for-byte (`ground #131415`, `surface #1B1C1E`, `border #3A3D42`, `accent/--sig #9BA1AB`,
`accentBorder/--sigbd #494D54`, `sacred #CBCDD1`); the gap was purely structural. Owner decisions
(AskUserQuestion): render the **Shahada**; **full Home pass** to the artifact.

Changes (still parity-preserving — `LauncherViewModelTest` unchanged):
- **DS-3 chip primitive fixed** (`SidrPressInvertChip`): resting = hairline-bordered (`Strokes.hairline`,
  `colors.border`) transparent chip in `colors.dim`; selected/pressed inverts to the artifact's **light
  text fill** (`bg=colors.text`, `fg=colors.ground`, border `colors.text`) — was incorrectly the pewter
  accent. Restructured to a `Box` so equal-width (`weight`) chips centre their labels. Affects all
  press-invert chips consistently; DS-3 goldens re-recorded.
- **Home route lane:** four equal-width bordered pills `APP/WEB/SITE/ASK` moved to the persistent
  `SidrUniversalInput.routeContent` slot. APP = selected lane (presentation-only); WEB/ASK always; SITE
  only for a safe URL; WEB/SITE no-op on blank. `InputResultsPanel` now renders only the app-match list.
- **Sacred anchor:** `HomeAnchorSlot` renders the quiet English Shahada (serif via `SidrTextRole.SACRED`,
  centred), hidden while typing. Static — **no prayer times/sources** (DS-6B).
- **Favorites grid:** 4-column tile grid with labels + mono `SidrSectionHeader` (was a `LazyRow`).
- **Input:** signal-toned hairline border (`accentBorder`) added, non-error only.
- **Tri-font realised on Home:** serif sacred, sans placeholder/labels, mono shell/chips/dates.

**Honest deviations (data-gated, not styling):** the **prayer strip** and **Recent** rows are NOT rendered
— real prayer times are DS-6B (no fake data) and recent-command history is not wired; favorites shows real
usage-derived apps (2 on the test device), not the artifact's 8 concept tiles. Full gate green +
re-verified on SM-A325F: idle Home matches the artifact (Shahada, bordered APP/WEB/ASK lane, favorites
grid); typing shows APP/WEB/SITE/ASK with APP inverted and SITE URL-gated.

## ADR 2026-07-11 — Vision MVP preview + all screens to artifact

**Status: DONE, device-accepted (SM-A325F / RF8R705H38F).** Plan:
[docs/superpowers/plans/2026-07-11-vision-mvp-preview.md](../docs/superpowers/plans/2026-07-11-vision-mvp-preview.md)
(13 tasks, executed via subagent-driven-development — a fresh implementer + a fresh spec/quality
reviewer per task, one fix round where review found an issue, whole-branch review after). Presentation-only
across every task: no `domain`/`data` change, no ViewModel contract change, no route-semantics change,
no persistence change. `LauncherViewModelTest`/`AppDrawerViewModelTest`/`AssistantViewModelTest`/
`SettingsViewModelTest`/`LearnedChoicesViewModelTest`/`PermissionEducationViewModelTest` all pass
byte-for-byte throughout.

**What landed:**
- **Task 1** — `core/ui/component/SidrPreview.kt`: `SidrPreviewBadge`/`SidrPreviewBanner`, the two
  primitives every preview surface below badges itself with. Roborazzi goldens across dark/light/
  font-scale-2.0/RTL (a below-the-fold gap in the full-gallery capture was fixed in-task by adding
  dedicated per-variant goldens for the new component).
- **Tasks 2–6 — existing screens migrated to the DS-3/DS-4 artifact look** (continuing DS-3/DS-4's
  pattern onto the remaining production screens): **App Drawer** → 4-column `AppTile` grid with sticky
  `SidrAlphabetHeader`s, a DS search row, and a `Groups`/`A-Z` toggle (`A-Z` = the real, unchanged
  `groupIntoSections` grid; `Groups` = an explicitly preview-badged sample-category grid — round-robin
  bucketing over the real installed-app list, no real categorization engine exists yet); **Assistant**
  chat screen → `SidrTopBar`/`SidrText`/`SidrSurface` composer, a new "CLOUD · host · model" provenance
  line (a malformed-base-URL edge case that could have leaked the URL scheme into that line was caught in
  task review and fixed to a safe `"(unknown host)"` fallback); **AI provider** settings form → labelled
  DS fields, the API-key-never-prefilled/never-displayed invariant re-verified intact; **Learned
  Choices → Memory** surface → real memory cards (phrase→app + real `displayStateLabel` evidence, no
  fabricated timestamps) plus a badged preview block (Aliases/Facts/Dismissed samples) with genuinely
  inert Export/Delete-all (no bulk-delete/export use case exists in the domain layer, so those stay
  `onClick = {}` rather than fabricating one); **Permission Education** → `SidrTopBar` + a "WITHOUT THIS
  PERMISSION" card, all four real branches (`!requestable`/`GRANTED`/`PERMANENTLY_DENIED`/`DENIED`)
  restyled with byte-identical callbacks, never imitating a system dialog.
- **Task 7 — 5-tab bottom bar + Home reconciliation.** New `app/navigation/SidrTabScaffold.kt`
  (`SidrTab` enum + `SidrTabBar`, an M3 `NavigationBar` with a press-invert-style selected/unselected
  color mapping, no navigation logic). `AppNavHost` wraps only the 5 tab roots (Home + the 4 new preview
  routes) in a `Scaffold(bottomBar = SidrTabBar)`; the existing 6 pushed destinations (Assistant,
  Settings, App Drawer, AI provider, Learned Choices, Permission Education, and the new Task-12 Moments
  screen) stay unwrapped so the bar disappears on push and reappears on pop. Tab switches use
  `popUpTo(Launcher) + launchSingleTop` (no back-stack growth). Home dropped the redundant standalone
  "Assistant" row (still reachable via the existing ASK route chip — a de-dup, not a functionality loss)
  and gained a Settings gear immediately left of the "SIDR OS" wordmark.
- **Tasks 8–11 — the four preview tabs**, each a new `feature/launcher/preview/*PreviewScreen.kt`:
  **Tasks** (sample intent → plan → execution → result flow, `SidrActionGate`-styled consent row, all
  callbacks inert); **Agents** (one sample "Research agent" card, tools/permissions rows, Pause/Open —
  all inert); **Activity** (four sample timeline rows incl. one **DANGER**-status failure row, ending
  "EPHEMERAL BY DEFAULT · OPT-IN PERSIST"); **Terminal** (a Python-REPL look that is **architecturally
  incapable of producing output** — the transcript has no backing state to append to at all, not merely
  "no append call today" — proven by a test that types a real command, submits via a real IME action, and
  asserts the transcript's child count is exactly zero). Every screen carries zero `domain`/`data`/
  ViewModel/navigation reference; every sample string self-discloses as sample/fictional.
- **Task 12 — Interaction-moment previews.** New `MomentsPreviewScreen` (Result/Partial/Error sample
  cards, all buttons inert) reachable via a real "Interaction moments" row in the Tasks preview's footer;
  registered as an unwrapped pushed destination (not a 6th tab).
- **Task 13 (this entry) — full gate + device acceptance + docs.**

**No prayer times anywhere:** grepped every file this plan touched for prayer/salah/adhan/namaz/
fajr/dhuhr/asr/maghrib/isha — the only hits are pre-existing comments *disclaiming* prayer data (Home's
`HomeAnchorSlot`/date line, unchanged by this plan); no task added prayer-time UI or copy.

**Full gate green** (JDK-17 toolchain): `:core:ui:recordRoborazziDebug :core:ui:verifyRoborazziDebug`
then `:core:ui:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:assistant:testDebugUnitTest
:feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest testDebugUnitTest
assembleDebug` — BUILD SUCCESSFUL throughout, no regressions.

**Device acceptance PASS (SM-A325F / RF8R705H38F):** Home real flow unchanged — typed `chrome` →
overtake → real launch (`com.android.chrome` foregrounded); typed `github.com` → `SITE` chip appeared
→ real browser opened on the URL; Settings gear → real Settings; App Drawer A-Z grid and Groups preview
both render over the real installed-app list; Assistant shows a real configured provider's provenance
line; AI provider form renders the masked key + "Key set — replace to update."; all 5 tabs switch with
the bar persisting; Tasks → Interaction Moments footer row navigates to real Result/Partial/Error cards;
Agents/Activity/Terminal each show their `PREVIEW` banner; Terminal confirmed live — typed text +
Enter cleared the field and produced no output anywhere; no crash across the full session; the
`Smart command routing` flag was confirmed off (default), consistent with rule-only parity already
proven by the real launch/SITE tests above.

**Session note:** one task implementer's connection was interrupted mid-run (before any file was
written) and was resumed cleanly from its transcript with no rework; one task landed real code but the
implementer didn't commit it itself (over-cautious reading of the no-auto-commit default) — the
controller reviewed the diff for secrets/unexpected files and committed on its behalf. Neither affected
the delivered code or the review record.

**Minor findings carried forward (non-blocking, rolled up from every task review):** `ControlGallery.kt`'s
PREVIEW section and a few other bottom sections sit below the fixed-viewport fold in the full gallery
goldens (pre-existing, not unique to this plan); the Task-8 gallery's `SidrRiskChip("EXTERNALHANDOFF")`
label wraps with no space and its "queued" `SidrStatusMarker` wraps to three vertical lines in a narrow
row (cosmetic, spotted during this session's device pass); the Memory preview block still renders
alongside a genuinely-empty real list (mitigated by banner+badge+caption, not gated on `isEmpty()`); the
two "PREVIEW —" lines on the Terminal screen use the same role/color and read as one repeated banner.
None block this closure; candidates for a future DS polish pass.

## ADR 2026-07-13 — DS-5 + auto-hide nav + accent reactivation closed (code) + stabilization pass

**Status: CODE-CLOSED; device acceptance PENDING (no device attached during this pass).** This ADR
retro-documents two commits that landed without their own ADR/docs-sync — `5c8bc58` "Implement DS-5
action safety surfaces" (2026-07-12) and `a6ec4e6` "DS" (2026-07-13) — and records the stabilization
pass that reconciled them (plan: `.claude/plans/recursive-sleeping-chipmunk.md`, approved by owner).

**What `5c8bc58` delivered (DS-5 Action & Safety, presentation-only):**
- New `core/ui/component/SidrActionSafety.kt`: `SidrActionProposal` (one-shot execute, `executing`
  disables), `SidrPermissionNotice`, `SidrPrivacyNotice`, `SidrResultSurface` (+`SidrResultTone`
  Completed/Partial/Failed — partial is never labelled completed), `SidrErrorSurface` (WHAT/WHY/NEXT,
  retry only when caller supplies it), `SidrOfflineState`, `SidrBlockedState`, shared
  `SidrSurfaceActions` (stacks buttons at fontScale ≥ 1.7) and `SidrSurfaceAction`.
- `SidrActionGate` hardened: confirm/cancel one-shot, `confirming` disables both controls, consequence
  always visible, target wraps. `ErrorState` now delegates to `SidrErrorSurface` (retry preserved).
- Adopted in production: `LauncherScreen` maps SAFE routed proposals → `SidrActionProposal` and
  CONFIRM → `SidrActionGate(ExternalHandoff)`; `PermissionEducationScreen` renders through
  `SidrPermissionNotice`/`SidrPrivacyNotice`. `ConfirmActionCard` production usage is now zero (not
  yet `@Deprecated` — follow-up). No ViewModel touched → routing/execution/permission parity structural.
- Tests: `SidrActionGateTest` (7) + `SidrActionSafetyTest` (7) — behavioural. **Deviation vs the DS-5
  plan:** Task 2's `ActionSafetyGallery` + Roborazzi matrix was NOT delivered (follow-up); Task 1's
  parity notes were not captured. Plan `2026-07-11-ds5-action-safety.md` updated to CODE-CLOSED with
  boxes reconciled.

**What `a6ec4e6` delivered (owner features):**
- **Auto-hide bottom navigation** per approved spec `2026-07-12-auto-hide-nav-bar-design.md`:
  per-tab-root `TabRootScaffold` chrome state (visible on entry, hides after 5 s idle, thin
  `SidrChromeHandle` pill reveals; deliberate `remember`, not `rememberSaveable`), pin toggle
  `UserPreferences.alwaysShowNavBar` (default false; key `user_always_show_nav_bar` inventoried in
  `ALL_KEY_NAMES`) + Settings "Always show navigation bar" row; `LauncherActivity` threads
  `alwaysShowNav` into `AppNavHost`.
- **Accent reactivation (owner decision 2026-07-12):** `AccentColor {GREY, GREEN, AMBER}` — green/amber
  restored as **full themes** (own ground/surface/text/dim/faint/accent in `SidrColors.kt`
  `GreenDark/GreenLight/AmberDark/AmberLight`, resolved by `sidrColorsFor`), derived from the pre-DS-1
  AIL-0 palettes; `sacred` and all status colours pinned to the grey palette in every theme.
  `UserPreferences.accentColor` default changed `"green"` → `"grey"`; Settings accent selector
  (`AccentOption`). Plus `docs/demo-script.md` and Terminal-preview/theme polish.

**Stabilization pass (2026-07-13, this ADR):**
- `UserPreferencesRepositoryImplTest`: both round-trip payloads now set `alwaysShowNavBar = true`
  (mapper read/write of the new key is asserted; was uncovered, despite the spec requiring it).
- `ThemeScreenshotTest`: +4 goldens `green/amber_sample_dark/light`; sample gained `> ACCENT`
  (accent-coloured) and `DANGER` (status-coloured) lines so goldens prove "accent changes, status +
  sacred pinned" — the 2 grey samples were re-recorded with those lines (reviewed: correct).
- Stale comment fixed: `UserPreferences.accentColor` referenced non-existent `SidrColors.withAccent`
  and claimed "only the accent/border pair changes" — now points at `sidrColorsFor` and says full themes.
- **Found by the gate:** `AppNavHostReentryGuardTest` was RED at HEAD (the un-gated `a6ec4e6` broke it):
  the guard pinned the literal single-argument call `AppNavHost(homeResetSignal = homeResetSignal)`,
  which stopped matching when the call gained `alwaysShowNav`. Root-caused: the Y7 re-entry behaviour
  itself is intact (`onNewIntent`/`setIntent`/increment, signal passed, `LaunchedEffect` +
  `if (homeResetSignal > 0)` + `navigateHome` all present); only the assertion was brittle. Guard
  relaxed to the argument-level check `homeResetSignal = homeResetSignal`.
- **Full gate GREEN** (JDK-17 toolchain `/home/Suleiman/jdks/jdk-17.0.19+10`):
  `:core:ui:verifyRoborazziDebug :domain:test testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL.

**Device-pending (SM-A325F, next session with the device):** auto-hide (visible-on-entry → 5 s hide →
handle reveal → tab-hop keeps it up → Settings pin, portrait+landscape); DS-5 surfaces on real branches
(URL/Play-Store confirm, SAFE one-tap, cancel, permission education paths, font-scale-2.0 stacking);
grey/green/amber switching (persists across force-stop); router-off/offline parity smoke.

## ADR 2026-08-08 — DS-6B Prayer Correctness (COMPLETE — device-accepted by owner)

**Status: COMPLETE — device-accepted by the owner on SM-A325F, 2026-08-08 (closing addendum at the end
of this ADR).** All 11 build tasks (3–10 + the verification gate, plan
`docs/superpowers/plans/2026-07-11-ds6b-prayer-correctness.md`, spec
`docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md` §0) are implemented and each
passed a fresh-reviewer gate (`.superpowers/sdd/progress.md` is the detailed per-task ledger); the Full
Verification Gate is green; the whole-branch review (opus) returned "Ready to finish: Yes" with zero
Critical/Important findings. The automated on-device pass below verified only the no-data invariant; the
owner then ran the full interactive + religious-correctness acceptance and accepted the block.

**Source (owner amendment 2026-08-07):** `com.batoulapps.adhan:adhan2:0.0.5` — the Kotlin port
"adhan-kotlin" (Batoul Apps, MIT declared in POM), not the Java "adhan-java" the spec originally named.
The Java port `adhan:1.2.1` has no `TURKEY`/Diyanet method in its `CalculationMethod` enum
(verified against the decompiled jar + source), which would make the spec's mandatory Turkey/Diyanet
method impossible without hand-transcribing method parameters onto a religious-correctness surface.
adhan2 ships every method including `TURKEY`, is Kotlin-native, and is pinned at `0.0.5` (not the newer
`0.0.7`) for `kotlin-stdlib 1.9.22`/`kotlinx-datetime 0.5.0` compatibility with the project's Kotlin
2.0.21. Confined entirely to the new `:data:prayer` module (`AdhanPrayerCalculator`); zero network,
fully offline.

**Method/madhab:** explicit user choice with no default — `MethodRequired` is a real first-run state
(per §0.1–0.2, no locale/SIM/location guessing). Domain `SupportedPrayerMethods` offers all 11 methods
adhan2 actually implements; `OTHER` and `TEHRAN` are excluded (adhan2:0.0.5 genuinely lacks `TEHRAN`,
`javap`-verified) with an anti-drift test iterating all 11 → `Success`. Asr madhab (Standard/Hanafi) is
a separate mandatory choice, also no default.

**Location:** primary path is a bundled GeoNames-derived city index — 19,481 cities, 286.8 KB gzipped
(well under the ~500 KB budget), **CC-BY 4.0 attribution recorded** in `tools/prayer/README.md` and the
asset header — searched fully offline via `BundledCityIndex`, zero permissions required. Optional path is
a one-shot device-location read (`AndroidPrayerLocationProvider`, `:core:android`): no continuous
tracking, coordinates rounded to 2 decimal places **before** the value crosses the port boundary, denial
changes nothing (city path remains primary).

**Privacy:** coordinates are rounded to 2dp (~1.1 km / ≤~1 min schedule error) and never persisted,
logged, or leave the device — v1 has no outbound path at all. New `prayer_*` DataStore keys are
denylist-clean and inventoried in `PreferencesKeys.ALL_KEY_NAMES`. `PrayerLocationPrivacyGuardTest` (10
guards, `:domain`) proves: `OutboundContextPolicy.ALLOWED` stays the same 4-value set (prayer adds zero
outbound surface); `AiRequest`'s field inventory has no prayer/location field; a planted
Kazan-coordinate sentinel never reaches `PromptContextBuilder.build`; `com.batoulapps.adhan` and
`android.location` stay confined to their expected reader sets; zero `Log.`/`println` in any prayer
source file.

**New `PermissionFeature.PRAYER_LOCATION` (documented deviation from the plan's "no new permission
feature" line):** `LOCATION_SUGGESTIONS`' existing rationale copy is suggestion-specific and would
mislead a user asked to grant location for prayer times, so a separate feature with honest prayer copy
was added instead of reusing it. It maps to `ACCESS_FINE_LOCATION`, already present in the manifest
since Block U — **no new manifest permission**. All 4 exhaustive `when`s over `PermissionFeature` were
updated.

**Domain addition:** `PrayerContext.Available.locationTzId` was added so times render in the
**location's** timezone, not the device's — needed for the "location abroad, device still on home tz"
case the spec's tz-conflict state exists to catch.

**Home integration:** `LauncherViewModel` gained exactly one new constructor dependency
(`GetPrayerContextUseCase`), exposed as a lazy `prayerContext` `StateFlow`
(`SharingStarted.WhileSubscribed(5000)` + `flowOn(io)` — zero calculation happens before the UI
subscribes; the reviewer proved this non-vacuous with a live eagerly-swap regression). The strip renders
below the Shahada anchor, is opt-in, and renders **only** on `PrayerContext.Available` — never fabricated
times, never a nudge before setup. `LauncherViewModelTest` parity held byte-for-byte (+160/-0, no
existing assertion touched).

**Owner UI refinement (2026-08-08, on-device on SM-A325F, real Diyanet/Turkey setup):** the shipped Home
strip was changed to **times-only**. The status chip is now hidden for calm states (Verified,
CachedFresh, ManualLocation, Updating) but still shown as a warning label for degraded states (Stale,
TzConflict, CalculationFailed, empty) — the spec's "stale must be labelled stale" invariant is preserved,
never silently hidden. Prayer names moved from the visible cell to each cell's `contentDescription`
(TalkBack still announces "Fajr 04:21"); the provenance line is no longer drawn on the strip itself, but
remains a structural invariant (`require`), remains present in the strip's own `contentDescription`, and
remains fully visible on the prayer detail screen. Goldens were re-recorded against the new layout and
`SidrPrayerSummarySemanticsTest` was updated to the times-only text.

**Modules:** two new modules were added exactly per spec §0.8 — `:data:prayer` (the only module carrying
the adhan2 dependency + the city-index asset) and `:feature:prayer` (setup/detail screens, pushed route,
no new bottom-bar tab). `domain/prayer/` stays pure Kotlin (no Adhan import); `core/ui` gained
`SidrPrayerSummary` only. DS-6A stays UI-only — the quiet English Shahada component in `HomeAnchorSlot`
is unchanged and is a separate block from DS-6B's prayer-correctness data path.

**Verification — Full Verification Gate GREEN (2026-08-08, JDK-17 toolchain):**
```
:domain:test :data:prayer:testDebugUnitTest :data:repository:testDebugUnitTest \
:feature:prayer:testDebugUnitTest :feature:settings:testDebugUnitTest \
:feature:launcher:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug \
testDebugUnitTest assembleDebug
```
BUILD SUCCESSFUL — all prayer modules, `core:ui` Roborazzi goldens, the full root JVM suite, and
`assembleDebug` all pass.

**Device status (SM-A325F / RF8R705H38F / Android 13) — PARTIAL, not full acceptance:** the no-data
invariant was verified — `pm clear` for a genuine fresh install, launch, no crash, and the Home screen
renders calmly (Shahada + date + Universal Input + chips) with **no prayer strip, no prompt, no
fabricated times** before setup. Separately, a real Diyanet/Turkey setup on the same device rendered a
correct-looking strip. **Still pending the owner:** interactive setup acceptance (method/madhab/city
picking via Compose controls, not reliably drivable via `uiautomator`), and — most importantly — the
**religious-correctness cross-check of computed times against published authority tables**
(Diyanet/Umm al-Qura/MWL), airplane-mode cached render, the stale path, device-location grant +
rounding, the tz-conflict label, font-scale 2.0, TalkBack, dark/light, and router-off/offline parity
smoke. This mirrors the DS-5 device-pending precedent; do not treat DS-6B as accepted until the owner
runs that pass.

**Device acceptance — CLOSED 2026-08-08 (owner).** The owner ran the full on-device acceptance on
SM-A325F and **accepted DS-6B**: interactive setup (method + madhab + city), the strip appearing with a
correct schedule, and the religious-correctness cross-check of computed times were performed and signed
off by the owner (the agent-driven pass had verified only the no-data invariant + that a Diyanet/Turkey
setup renders). One owner-directed presentation change landed during acceptance: the Home strip was
reduced to **times-only** — the calm-state status chip and the visible provenance line are hidden and the
prayer-name labels dropped from the visible cells, while the "no schedule without provenance" invariant
still holds (provenance is still supplied to the component, retained in the strip's TalkBack
`contentDescription`, and shown in full on the detail screen), and every **degraded** state
(`CachedStale`/`TimezoneConflict`/`CalculationFailed`) still renders a visible warning marker so
stale/wrong times can never be presented as trustworthy (spec §2 preserved). `SidrPrayerSummary` goldens
were re-recorded and `:core:ui` + `:feature:launcher` stayed green. **DS-6B is CLOSED.** The London/Kazan
`MWL` golden limitation below stands as a documented follow-up (optionally swap to authority-anchored
cities in a later pass).

**Known limitation, recorded honestly:** golden-test times are **not uniformly authority-anchored**.
Istanbul (`TURKEY`/Diyanet) and Makkah (`UMM_AL_QURA`) goldens are anchored directly to the respective
authority portals (byte-exact to Diyanet's own portal; exact via Aladhan for Umm al-Qura). London and
Kazan (`MWL`) goldens are **cross-implementation-verified only** — reproduced against both adhan2 and an
independent solar-formula recomputation — because MWL (Muslim World League) has no official authority
portal to anchor against. This is a real, disclosed limitation of the MWL golden values, not a defect in
the other two methods.

## ADR 2026-08-10 — DS-7 Memory Surfaces + S2-2 Explicit Aliases complete (device-accepted)

**Status: COMPLETE — device-accepted on SM-A325F (RF8R705H38F), 2026-08-10.** This ADR closes a
documentation gap rather than describing new code: both blocks were **implemented on `launcher--7` on
2026-07-13** and shipped inside every build since (including the DS-6B device pass), but no ADR was ever
written, so `CLAUDE.md`/`current-status.md` kept listing DS-7 as "next" and S2-2 as "paused". The code
was verified, driven on-device, and closed on 2026-08-10.

**What was already on the branch (commits, 2026-07-13):**
- `8e3f317` `feat(ds7): add memory surface components` — `core/ui/component/SidrMemoryItem.kt`
  (`SidrMemoryType {LearnedPreference, ExplicitAlias, UserProvidedFact, TemporaryContext, SystemPolicy,
  AutomationState}` × `SidrMemoryStatus {Active, Learning, NeedsConfirmation, NeedsReconfirmation,
  Inactive, Expired, Unavailable, Deleted}`, merged `contentDescription` summary, optional
  `leadingContent`/`onOpen`/`onEdit`/`onForget`), `SidrMemoryDisclosure.kt`, `SidrForgetGate.kt`, plus a
  `MemoryGallery` + screenshot/semantics tests and 4 goldens (`memory_dark`, `memory_light`,
  `memory_fontscale2`, `memory_rtl`).
- `b2affdd` `feat(ds7): wire settings memory and aliases` — `LearnedChoicesScreen` migrated to
  `SidrMemoryItem` + `SidrForgetGate` behind a feature-local `LearnedChoiceMemoryUiModel` mapper; new
  `AliasesScreen`/`AliasesViewModel`/`AliasMemoryUiModel`; `Routes.Aliases` + `AppNavHost` destination +
  a Settings **MEMORY** section (`Learned choices`, `Aliases`).
- `7c20b63` + `0198abc` — the S2-2 "last mile" that had been stranded on branch `launcher-4`,
  **re-applied by hand** (not cherry-picked, per that plan's own instruction): the alias decorator wired
  into `LauncherViewModel`, plus `AliasPrivacyScopeGuardTest` and the `RoomColumnNames` inventory entry
  for the `aliases` table. The third stranded commit (`cef4111`, docs) was **not** carried over — its ADR
  text is superseded by this entry.

**Architecture held:** `core/ui` takes strings/lambdas only (no domain/data import); presentation mapping
lives in `:feature:settings`; no new Room schema in this pass (S2-2's `aliases` table is `SidrDatabase`
v3 + `Migration2To3` + golden `schemas/3.json`, all landed in the earlier Phase-B work); the alias path
fires **only** on `CommandOutcome.Unknown`, so `HandleUserCommandUseCase`/`RouteCommandUseCase`/the rule
matcher stay untouched and no-alias input is byte-for-byte pre-S2-2. Outbound allow-list widened by
**zero** (`AliasPrivacyScopeGuardTest`).

**Verification gate (JDK-17, 2026-08-10).** `:domain:test :core:ui:testDebugUnitTest
:core:ui:verifyRoborazziDebug :feature:settings:testDebugUnitTest :feature:launcher:testDebugUnitTest
testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL. Because the tree was unchanged since the DS-6B gate
(2026-08-08), the first run was entirely `UP-TO-DATE`; the four relevant test tasks were therefore
**force-re-executed** (`--rerun-tasks`, exit 0): `:domain` 332/0, `:core:ui` 108/0 +
`verifyRoborazziDebug` green, `:feature:settings` 33/0, `:feature:launcher` 130/0.

**Device acceptance — SM-A325F / Android 13, agent-driven adb, 2026-08-10. PASSED:**
- **S2-2:** Settings → Aliases → typed phrase + picked target → `Add alias`; the row rendered as
  `EXPLICIT ALIAS · "work chat" -> <app> · ACTIVE` with provenance `MEMORY · USER-DECLARED ALIAS ·
  SETTINGS · LOCAL ONLY`; typing the phrase on Home launched the declared app **directly** (no candidate
  list, no confirm card); the alias survived an app restart (Room).
- **Parity:** `open opera` still launched Opera (an alias never shadows a real app); a genuinely unknown
  phrase (`qwerty nonsense phrase`) still produced the unchanged `Unknown command. Try: open <app>,
  search <query>` fallback and launched nothing.
- **Forget gate (both surfaces):** `Forget` opens `SidrForgetGate` (title + `DESTRUCTIVE` status chip —
  dot **and** word, never colour-only + consequence copy + "Stored only on this device."). **Cancel did
  not delete**; **Forget deleted exactly once**; after deletion the phrase no longer launched anything
  and the DS-7 empty copy rendered verbatim ("No aliases yet…" / "No learned choices yet / Preferences
  appear only after confirmed choices. / Stored only on this device.").
- **DS-7 through the real S2-1 flow:** the ambiguous command `open python` (two same-labelled installed
  apps) produced `Did you mean:` → picking a candidate launched it and recorded the preference; Settings
  → Learned choices showed `LEARNED PREFERENCE · "python" -> Python · LEARNING` with evidence
  `MEMORY · LEARNING FROM CONFIRMED CHOICES (1/3) · LEARNED FROM CONFIRMED CHOICES · LOCAL ONLY`;
  after Forget, repeating `open python` **asked again** (delete-to-relearn intact).
- **fontScale 2.0** on Learned Choices: type label, `"phrase" -> target`, provenance and status all wrap
  cleanly — no overlap, no clipping, Forget still reachable.
- Test data created during the pass (one alias, one learned choice) was deleted afterwards; the device
  was left in its pre-pass state.

**Not covered on device (honest gaps, none blocking):**
- **Unavailable/pruned alias target.** Proving it on this device would mean temporarily disabling or
  uninstalling one of the owner's real apps; the harness permission gate blocked `pm disable-user` and
  the workaround was not forced. Covered by `PruneUnavailableAliasesUseCase` unit tests + the
  display-time best-effort prune in `AliasesViewModel`, and by the S2-1 precedent (its uninstall
  invalidation was device-proven with throwaway fixture APKs).
- **Live TalkBack session.** The accessibility tree was read directly instead (`uiautomator dump`): the
  merged `contentDescription` summaries are exactly the strings TalkBack would speak, and they were
  confirmed complete on both surfaces.
- Long-phrase / long-label layout and RTL were checked only through the (green) `memory_*` goldens.

**Deviations, recorded:**
- **DS-7 Task 6 (memory disclosure) is preview-only.** `SidrMemoryDisclosure` exists and is
  gallery/golden-covered but is used by **no** production screen — the plan explicitly allows this when
  no honest "a new stable preference just formed" event exists to hang it on. Adding it after every
  launch or every evidence increment was forbidden and was not done.
- **Aliases screen ordering (UX follow-up, non-blocking):** the `ALIASES` list is rendered *after* the
  full target-app picker, so on a device with ~200 launchable apps the user must scroll past all of them
  to see their own aliases. Correct, but worth reordering.
- **`AliasesViewModel.onSave`/`onDelete` discard the `OperationResult`.** The screen pre-validates
  (non-blank, `MAX_ALIAS_PHRASE_LENGTH`, normalizer collision hint), so no user-visible gap was found,
  but a genuine persistence failure is currently silent. Follow-up.
- **Unrelated observation from the fontScale-2.0 pass:** the bottom tab bar clips its labels at 2.0
  ("Hom e", "Task s", "Agen ts", "Activi ty"). That is DS-5/Vision-MVP nav-bar territory, not DS-7.
- The device's default launcher is currently Samsung One UI, so the pass was driven by explicit
  `am start -n com.sidr.launcher/.LauncherActivity` rather than the HOME key.

**DS-7 and S2-2 are both CLOSED.** Remaining DS-track work: DS-10 Assistant Migration (the last
production surface still in the pre-v1.1 look), then the DS v1.1 release gate; DS-8/DS-9 stay
contract-only until A4/A5 exist. Next architectural slice remains A1 Tool & Capability.

## 2026-08-10 — DS-10 Assistant Migration complete (device-accepted)

**DS-10 closes the design track's last production surface.** The Assistant now speaks SIDR v1.1: DS-3
controls, DS-5 privacy/error surfaces, sans prose vs mono provenance, and an explicit accessibility
contract. Spec `docs/superpowers/specs/2026-07-11-ds10-assistant-migration-design.md`; plan
`docs/superpowers/plans/2026-07-11-ds10-assistant-migration.md` (STATUS COMPLETE).

**Baseline correction (Task 1).** The spec's premise — "raw Material Assistant presentation",
`OutlinedTextField`/`Button`, an inline key-bearing provider form with an "Edit provider" control — was
already stale: the Vision MVP pass had moved `AssistantScreen` onto `SidrScaffold`/`SidrTopBar`/
`SidrText`/`SidrSurface` and split the provider form onto its own `AssistantProviderScreen`. DS-10's real
delta was therefore **not** a re-skin but: the DS-5 layer (privacy notice + error surface), the a11y
contract, the layout split, and the missing screenshot/semantics coverage. Baseline gate before any edit:
`:feature:assistant:testDebugUnitTest --rerun-tasks` → 24/0.

**What landed.**
- **`core/ui/component/SidrAssistant.kt` (new, presentation-only).** `SidrAssistantComposer` — mono field
  + send affordance in one `SidrSurface` (the `DrawerSearchField` idiom, no card-in-card); IME `Send` and
  the button share **one** `canSend = value.isNotBlank() && !sending` guard so the two dispatch paths can
  never disagree; the composable never clears the text (the feature owns that). `SidrStreamingIndicator` —
  `SidrProgress` + a mono label inside the surface's **only** `liveRegion`, so TalkBack announces
  "Replying…" once per state change instead of re-reading on every streamed token. Send's
  `contentDescription` names *why* it is unavailable (`SEND_EMPTY_DESCRIPTION`/`SEND_BUSY_DESCRIPTION`/
  `SEND_READY_DESCRIPTION`) rather than leaving TalkBack with a bare "disabled".
- **`feature/assistant/AssistantPresentation.kt` (new, pure, no Compose).** The DS-7
  `LearnedChoiceMemoryUiModel` precedent: all Assistant *wording* decided in testable Kotlin —
  `AssistantStatus.Error.toPresentation()` → DS-5 `title/whatFailed/why/next` + **at most one** action
  label (`Retry` when `retryable`, `Fix provider settings` when `showProviderCta`, **none** for
  `InvalidRequest`, which is neither); the cloud-disclosure copy; and `providerProvenanceDetails()` =
  host + model + key-presence. `providerHost()` moved here unchanged (host-only, neutral
  `(unknown host)` sentinel — a malformed `baseUrl` can still never leak the scheme).
- **`AssistantScreen.kt` recomposed** into `AssistantShell` / `AssistantContent` / `AssistantMessage` /
  `AssistantStatusLine` / `AssistantComposer` / `AssistantNoProviderPanel` / `AssistantProviderPanel`.
  Idle-with-no-reply now renders the **cloud disclosure itself** as the calm empty state (the user reads
  where their words will go *before* the first Send); the provenance line is pinned directly above the
  composer so cloud use is legible at the moment of sending; errors render through `SidrErrorSurface`;
  refusal stays calm supporting text, never an error surface; the API-key field gained Compose
  `password()` semantics on top of its visual mask.
- **Coverage.** `core/ui`: `AssistantGallery` + 4 goldens (`assistant_dark/light/fontscale2/rtl`) + 5
  `SidrAssistantSemanticsTest` behaviour/a11y proofs (blank → disabled + why; streaming → disabled + why;
  button send; IME send dispatches once typed and never while streaming; indicator labelled).
  `feature/assistant`: 10 `AssistantPresentationTest` cases built from the **real** `AiError` →
  `toUiError()`/`isButtonRetryable()`/`needsProviderSetup()` helpers.

**Parity — nothing behavioural changed by the migration itself.** `AssistantUiState` and the domain/data
path were **not edited**, and `AssistantViewModel` was touched only by the write-order fix recorded below
(no change to its state shape or streaming semantics): streaming, latest-wins cancellation, retry, BYOK/Keystore handling, the
prefill-only `initialPrompt`, and the deliberate absence of a prompt/reply `SavedStateHandle` are all
untouched. `AssistantViewModelTest` 21/0, byte-for-byte. No chat history, no memory/context injection, no
tool execution, no API-key display or logging were added.

**Verification gate (JDK-17, force-rerun, exit 0).** `:domain:test` 332/0, `:core:ui` 117/0 (was 108) +
`verifyRoborazziDebug`, `:core:android` 12/0, `:data:ai-cloud` 36/0, `:data:ai-local` 54/0, `:data:prayer`
37/0, `:data:repository` 167/0, `:feature:assistant` 34/0 (was 24), `:feature:launcher` 130/0,
`:feature:settings` 33/0, `:feature:permission_education` 15/0, `:feature:prayer` 11/0, `:app` 8/0; root
`testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL. No pre-existing golden changed — the four
`assistant_*` PNGs are additive.

**Device acceptance — SM-A325F / Android 13, 2026-08-10 (owner entered the provider + key on-device;
the agent never typed or read a key). PASSED:**
- Fresh no-provider state: `No AI provider configured` privacy notice + `LOCAL ONLY · NO PROVIDER
  CONFIGURED` + the setup CTA; no composer (nothing to send to).
- Provider screen: DS-5 disclosure, mono labels, key masked to dots, `Key set — stored in this device's
  Keystore. Enter a new one to replace it.`, Save disabled until URL+model are non-blank, and after save
  `CLOUD · OPENROUTER.AI · OPENAI/GPT-4O-MINI · KEY IN KEYSTORE`.
- ASK-route `initialPrompt` **prefilled and never auto-sent** — observed three times across separate
  navigations.
- **Real streaming** through the owner's BYOK OpenRouter key: quiet `Replying…` + progress line, composer
  cleared, Send disabled mid-stream, reply rendered as sans prose.
- **Unactionable error path**, hit for real: the first model id returned HTTP 404 → `SidrErrorSurface`
  with the `FAILED` chip and WHAT/WHY/NEXT and **no** button, exactly as `toPresentation()` specifies for
  `InvalidRequest`.
- **Force-stop → relaunch:** no prompt, no reply, no error survived — nothing is persisted.
- **0 key leaks:** `logcat` grep for `sk-…`/`Bearer` = 0.

**Fixed during the pass:** the provider form claimed `CLOUD · (UNKNOWN HOST) · NO KEY SET` while the form
was empty. It now renders `LOCAL ONLY · NO PROVIDER CONFIGURED` until a base URL exists — never claim
cloud before a provider exists. Re-verified on device and re-gated.

**Found on device and FIXED — `AssistantViewModel` write-order race (pre-existing, surfaced by DS-10).**
Immediately after the key was saved from `AssistantProviderScreen`, the **chat** screen's separate VM
instance still showed `NO KEY SET` in its provenance although the key was stored — the 404 (not 401)
proved the key had actually been sent. Cause: `keySet` is recomputed only when `activeConfig()` emits,
and `saveProvider` wrote the config to DataStore **before** the Keystore `put`, so an observing instance
could read the secret store in that gap and cache "no key set" until the next config change. **Fix:
`saveProvider` now writes the key first and the config last** — the config write is the observable event,
so it must come after the thing it announces; as a bonus a provider is never announced as configured
while its key is still missing. Nothing else in the ViewModel changed: streaming, latest-wins
cancellation, retry, the derived `providerId`, the blank-key skip, and the "key never enters state" rule
are untouched, and the pre-existing 21 tests still pass. Guard: `AssistantViewModelTest`
"saveProvider writes the key before it announces the config" (22 tests now) records the two writes
through recording decorators and asserts the order — **verified to fail on the old ordering and pass on
the new one**. The device-observed interleaving itself is not reproducible against in-memory fakes
(whether the collector resumes inside or after the gap is a scheduling detail), so the test pins the
invariant that removes the window rather than the race.

**Not covered on device (honest gaps, none blocking):**
- **Retryable network error + the Retry button.** Cutting the phone's mobile data killed the owner's
  tethered laptop connection; the agent restored it and did not retry the experiment. The owner confirmed
  manually that the assistant does not work without network. The `retryable → Retry` mapping is covered by
  `AssistantPresentationTest`.
- **Credential CTA error** (`401 → Fix provider settings`) — would require deliberately storing a wrong key.
- **Refusal note** — cannot be provoked on demand.
- **fontScale 2.0 / RTL / light theme on the live screen** — covered by the four `assistant_*` goldens only.
- **Live TalkBack** — semantics asserted by `SidrAssistantSemanticsTest` instead.

**Deviation, recorded.** Task 6's screenshot list is delivered as a `core/ui` **component gallery** of the
Assistant's states (no-provider, idle disclosure, streaming, completed, refusal, retryable error, provider
CTA error, unactionable error, composer empty/typed/streaming), not as whole-screen goldens: Roborazzi is
wired only in `:core:ui`, and standing a screenshot harness up in `:feature:assistant` was judged a larger
build change than DS-10 warrants. Screen-level composition is covered by the device pass instead.

**Also observed (unchanged, not DS-10 scope):** when a provider *is* configured, the chat offers no route
back to provider settings except through Settings or a credential error's CTA.

**DS-10 is CLOSED. The DS v1.1 release gate is now open**; the next architectural slice remains A1 Tool &
Capability. Untouched follow-ups from DS-7/S2-2 still stand: `AliasesViewModel` discards save/delete
`OperationResult`s, the Aliases list renders after the whole app picker, and the bottom tab bar clips its
labels at fontScale 2.0.

## 2026-08-16 — I18N-1 Multilingual UI complete

**I18N-1 ships `en`/`ru`/`tr` across every migrated production surface and installs three regression
barriers.** Zero i18n infrastructure existed before this block — no `strings.xml`, `stringResource` had 0
occurrences, ~250–350 UI strings sat across 8 modules as Kotlin literals. Spec
`docs/superpowers/specs/2026-08-11-i18n-1-multilingual-design.md`; plan
`docs/superpowers/plans/2026-08-11-i18n-1-multilingual.md`.

**The seam.** `core/ui/i18n/SidrStrings.kt`'s `sidrString(@StringRes id)` is the **only** place
`stringResource` is called (`StringSeamGuardTest` enforces this). It checks a composition-local
`SidrStringOverlay` before falling back to the resource; the shipped app always runs
`SidrStringOverlay.None`, whose identity check short-circuits before any lookup, so the shipped path costs
exactly one `stringResource` call. The overlay is **keyed by the resource entry name**
(`getResourceEntryName(id)`, e.g. `ui_action_cancel`), not the numeric id, because numeric ids are not
stable across builds — a future runtime overlay (the `translate_ui` A-stage feature) can then serve
strings on top of compiled resources with zero call-site changes. `sidrPluralString` deliberately
**bypasses** the overlay: quantity selection is locale grammar (`ru` one/few/many/other, `tr`'s own
rules), not copy, and an overlay supplying one form would silently break it.

**Two owner-decided exemptions (spec §3.2), and their cost.** (1) The five `PREVIEW`-badged mock-up
screens (`TasksPreviewScreen.kt`/`AgentsPreviewScreen.kt`/`ActivityPreviewScreen.kt`/
`TerminalPreviewScreen.kt`/`MomentsPreviewScreen.kt`, ~58 literals) stay English — they are non-functional
stand-ins the A-stage replaces wholesale, and translating ~174 strings scheduled for deletion buys
nothing. Device-verified: the `PREVIEW` badge itself (a `core/ui` component, in scope) renders
`ÖNİZLEME`/`ПРЕДПРОСМОТР` correctly, while the mock-up's own sample content (`SAMPLE COMMAND`, the
quarterly-report walkthrough, etc.) stays English exactly as designed — the exemption boundary holds in
practice, not just in the guard's named list. (2) The hidden dev console (7-tap arm, `//dev-mode` toggle,
`outcomeSummary` labels, ~12 literals in `LauncherViewModel`) stays English — a debug surface, and
`outcomeSummary` values are pipeline state names more useful stable than translated. Both exemptions are
named individually inside the barrier-1 guard with their reason, not a silent regex hole.

**Typed domain/data message contract — a scope expansion discovered while planning (spec §3.5), not
assumed at brief time.** 11 user-facing strings were born below the UI, where no resource lookup exists.
Two pure `domain` types, `CommandMessage` and `CommandFailure`, now name *what happened* without wording
it, carried through `CommandOutcome`/`ExecutableAction`/`ActionExecutionResult`; the feature-layer
`LauncherPresentation` mapper turns them into resources (the DS-10 `AssistantPresentation` precedent
reused, not reinvented). All five `CommandFailure` cases are `data object` with zero fields, so the
privacy-by-construction property (no user text, no stack trace reaches a string) holds structurally, not
by convention. `domain` gained no new dependency and stays stdlib + coroutines. Both HELP variants were
deliberately kept as distinct types even though one branch may be unreachable — collapsing them would have
changed what a user sees.

**The honest §8 parity statement.** Spec §8 states plainly that I18N-1 cannot claim the byte-for-byte
ViewModel-suite parity every DS block before it claimed, because typing domain/data messages genuinely
touches ViewModel-adjacent code:
- `:feature:settings` mapper tests — exactly 6 assertions converted in place (Task 9); zero tests
  added/deleted/renamed/merged; no ViewModel logic touched. 33 → 33.
- `:feature:assistant` 35 → 38 — `AssistantViewModelTest` stayed 22 unchanged; the +3 is additive
  `AssistantPresentationTest` coverage (10 → 13). The plan predicted "35 still green"; the honest number
  is 38.
- `:feature:launcher` 130 → 156 — `LauncherViewModelTest` edits were in place only (85 → 88 across Task 12
  + its fix round); the rest is new `LauncherPresentationTest` / `AppDrawerViewModelTest` /
  `PrayerProvenanceTextTest` coverage.
- `:domain` 332 → 333 — exactly the one test Task 11's brief specified.
- Two assertions got **stronger**, worth recording rather than glossing: `HandleUserCommandUseCaseTest`'s
  leak check went from `assertFalse(message.contains("db crash"))` to
  `assertEquals(CommandFailure.Generic, …)` (`Generic` has no field a stack trace could leak through at
  all); `LauncherViewModelTest:1698` went from a bare `is CommandFeedback.Message` to also pinning
  `SpeechRecognitionError.PERMISSION_DENIED`.

**`<plurals>`: ZERO.** The extraction needed no `<plurals>` at all. Barrier 2's quantity check therefore
ships **dormant by design** — no plural was invented to justify it (spec §5.1).

**The pseudolocale outcome (Task 4, barrier 3).** The spike resolved to the real platform `en-XA` path:
`isPseudoLocalesEnabled` on `:core:ui`'s debug build type + `@Config(qualifiers = "+b+en+XA")` — Robolectric
resolves it. **No overlay fallback was needed**; spec §17 risk 2 did not materialise, so there is no
overlay-pseudolocale deviation to record. The one real deviation: the qualifier carries a leading `+`
(`+b+en+XA`, not the bare form) because the bare form replaces the module's `robolectric.properties`
viewport (`w360dp-h800dp-xhdpi`) and would make the captures non-comparable to their `*_dark` pairs.
Golden budget: exactly two pseudolocale captures, `assistant_pseudolocale` + `prayer_summary_pseudolocale`
— see the §10.4 correction below for why those two and not `controls`.

**appcompat / per-app language (Task 14) — the startup-path change, with device evidence.**
`LauncherActivity` became `AppCompatActivity`; `Theme.SidrLauncher` re-parented from
`android:style/Theme.Material.NoActionBar` to `Theme.AppCompat.NoActionBar`, keeping
`android:windowBackground=@color/sidr_ground` byte-for-byte (the no-white-flash property).
`AppCompatDelegate` is the single store for the chosen language — no `user_language` DataStore key,
`PrivacyInventoryGuardTest`/`ALL_KEY_NAMES` untouched. **Honesty item:** on API 28–32, appcompat's
`autoStoreLocales` persists the choice into its own SharedPreferences record file
(`androidx.appcompat.app.AppCompatDelegate.application_locales_record_file`) — not a new DataStore key and
outside the privacy inventory, but a real persisted artifact the guard does not see. On this project's
device (API 33) that path is inert; the framework `LocaleManager` owns the value there. Language labels
are **autonyms** (`English`/`Русский`/`Türkçe`), byte-identical across all three locale files by design,
so a user switched into a language they cannot read can still find their own.
Device evidence (SM-A325F, system locale `ru-RU` throughout): switching Settings → LANGUAGE → `Türkçe`
recomposed the entire Settings screen in place with **no visible navigation** — title
`Настройки`→`Ayarlar`, the selection dot filled immediately, confirming the delegate-driven Activity
recreation is fast enough not to read as a hang. `am start -W` TotalTime measured across 5 fresh cold
starts on the installed **debug** APK (4 from the cold-start check plus 1 more during the force-stop
persistence check): 3146 / 2487 / 2668 / 2295 / 2634 ms (post-force-stop). This is
**not directly comparable** to the ~766 ms cold-median baseline in `CLAUDE.md`'s release-performance
record — that figure was measured on a release build (R8-shrunk + Baseline Profile) after a drop-first
protocol, and the Task 16 brief mandates `:app:installDebug`, an unoptimized debug APK with neither
optimization. No white flash was observed on any of the five cold starts. A true like-for-like regression
check needs a release build, which is exactly the artifact the release gate below now blocks until the
owner signs off — so it is not claimed here.

**Per-module test counts, measured at gate time (JDK-17, `--rerun-tasks`, exit 0), against the plan's
stale baselines:**

| module | plan baseline | measured | note |
|---|---|---|---|
| `:domain` | 332 | 333 | Task 11 added exactly one test |
| `:core:ui` | 119 | 126 | Task 4 added 3 |
| `:data:repository` | 167 | 167 | unchanged all block |
| `:feature:launcher` | 130 | 156 | see §8 parity statement above |
| `:feature:settings` | 33 | 33 | unchanged all block |
| `:feature:assistant` | 35 | 38 | see §8 parity statement above |
| `:feature:prayer` | — | 12 | not in the plan's baseline table |
| `:feature:permission_education` | — | 15 | byte-parity: 15 before and after |
| `:app` | 8 | 13 | +1 seam guard, +1 locale guard, +3 barrier-1 |
| goldens | 34 + 2 | 36 | `git status --porcelain` on the screenshots dir: empty |

All measured numbers match the controller-supplied table exactly; nothing needed correcting at gate time.

**The release gate is a behaviour change to the build — stated loudly, not left to surprise someone.**
Task 15 added `checkOwnerReviewedLocaleStrings` (`app/build.gradle.kts`). Consequence, verified:
**`:app:assembleRelease`, `:app:assemble`, `:app:bundleRelease`, and root `./gradlew build` are RED BY
DESIGN** until the owner adds an `OWNER-REVIEWED` marker to each of the 10 locale `strings_locked.xml`
files. `:app:assembleRelease` is the historical AIL-6 RC gate command this file has recorded as green
since 2026-07-06; it is not green right now, on purpose, and that must never read as a mysterious build
break. Every debug graph stays clean, which is why the block's own `testDebugUnitTest`/`assembleDebug` gate
still passes. The gate is fail-closed on a **positive** marker rather than keyed off the word `DRAFT`,
because only `feature/launcher` and `feature/prayer`'s locked files carry `DRAFT` at all while all 10 are
equally unreviewed — a `DRAFT`-keyed gate would have silently passed 6 of them.

**Two spec corrections landed in this pass, both owner-relevant:**
- **§10.4 had the pseudolocale gallery selection backwards.** It excluded `memory` and `prayer_summary` as
  "caller-supplied sample data" and kept `controls`. That is the wrong way round: `SidrPrayerSummary`
  routes all 11 status chips through `sidrString` (including `TZ CONFLICT`, the tightest chip in the
  project) and the memory components read 27 strings, while `controls` came out **byte-identical to
  `controls_dark`** — the spec excluded the two galleries that would actually load the barrier and kept
  the one that could not. Owner-confirmed 2026-08-11, owner ruled replace-not-add; the final captures are
  `assistant_pseudolocale` + `prayer_summary_pseudolocale`, golden budget held at exactly two.
- **§3.1's in-scope enumeration omits `feature/suggestions`**, a real Compose module (`SuggestionsRow`).
  Barrier 1 scans it; the enumeration text was under-inclusive, not the guard.

**Known English residue — five items named ahead of time by the plan, plus two found during this pass's
own device smoke that were not previously catalogued.**
1. `CalendarSuggestionProvider`'s "Upcoming event" and `LocationSuggestionProvider`'s "Nearby places"
   render on Home via `Suggestion.label` and stay English in `ru`/`tr`. Data-layer strings, out of scope by
   spec §3.1/§3.6; fixing them needs the §8 typed-value treatment plus a change to the persisted
   `CachedSuggestion(label, actionId)` shape. Routed to I18N-2.
2. The five `PREVIEW` tabs stay English (spec §3.2, owner decision) — device-verified, see above.
3. The hidden dev console stays English (spec §3.2).
4. `RISK_CONFIRM_LABEL = "CONFIRM"` remains a Kotlin constant in `LauncherViewModel` — spec §7.1
   enumerated locked English vocabulary, deliberately identical in every locale. Typing it needs the full
   §8 refactor through `PendingRoutedAction` + `ConfirmActionCard`; routed to I18N-2.
5. Voice recognition still follows the **device** language, not the app language
   (`startVoiceInput(languageTag = null)`) — spec §3.6 records this as deliberately not fixed here.
6. **New, found during Task 16's device smoke:** `core/android/prayer/AndroidPrayerLocationProvider.kt:113`
   hardcodes `private const val DEVICE_LOCATION_LABEL = "Current location"`, rendered verbatim on the
   Prayer detail screen's "Konum"/"Локация" row in every locale. `core/android` sits outside spec §3.1's
   in-scope module list — the same class of gap as item 1 above (a data-layer label, not UI copy) — so
   barrier 1 does not (and by its current scope, cannot) catch it. Routed to I18N-2 alongside item 1.
7. **New, found during Task 16's device smoke, and a real correctness gap rather than a scope
   boundary:** Home's per-cell prayer-time `contentDescription` announces the raw Kotlin enum name
   (`PrayerName.FAJR.name` = `"FAJR"`) instead of the localized prayer name. `PrayerSummaryMapper.kt:64`
   sets `name = name.name` (the enum constant), and `SidrPrayerSummary.kt:212`'s comment states the
   caller-supplied value is already "human copy" that "folds under the user's locale" — that assumption is
   false for the Home strip's mapper, though true for `PrayerDetailScreen.kt:172`, which correctly resolves
   through `sidrString(R.string.prayer_name_fajr)`. Net effect: the **visible** Home chip text is correctly
   translated (`İMSAK`/`Фаджр`), confirmed via screenshot, but a TalkBack user on `ru`/`tr` would hear the
   English/canonical prayer name for the Home strip specifically. Found via the `uiautomator` accessibility
   tree, not a live TalkBack session (which this pass does not claim to cover); traced to source and
   confirmed as a real defect, not a hypothesis. Not fixed in this pass — Task 16 is verification and docs
   only — flagged here so it is not rediscovered cold in I18N-2 or a live TalkBack pass.

**Gate (JDK-17, `--rerun-tasks`, exit 0):** `eval $GRADLE :core:ui:testDebugUnitTest
:core:ui:verifyRoborazziDebug :domain:test testDebugUnitTest assembleDebug --rerun-tasks` → `BUILD
SUCCESSFUL`. Goldens: `git status --porcelain core/ui/src/test/screenshots/` → empty (36 PNGs on disk, none
new, none modified — the plan's own Step 2 expectation was wrong twice, see the plan-defect note in the
Task 16 report).

**Device (SM-A325F, agent-driven adb, system locale `ru-RU` throughout — an incidental app≠system
condition already present rather than one requiring a system-setting change):** in-app `en`→`ru`→`tr`
switch instant and correct on every migrated screen exercised — Home, App Drawer (Groups/A-Z, search),
Settings (+ Learned Choices/Aliases/AI-provider/Prayer-setup sub-screens), Assistant (cloud disclosure +
composer), Prayer detail; Turkish dotted-İ confirmed rendering correctly in multiple `SidrTextRole.SYSTEM`
uppercase headers (`DİL`, `MEZHEP (İKİNDİ)`, `HESAPLAMA YÖNTEMİ`); the LANGUAGE selection dot updated
immediately on tap, no navigation away/back required; force-stop → relaunch preserved the chosen language
across a cold start; the Home date line read `3 Rebiülevvel · Pzr, 16 Ağu` (Turkish) while
`getprop persist.sys.locale` read `ru-RU` throughout — direct confirmation the date line now follows the
app locale via configuration, not `Locale.getDefault()` reading the system default (the regression the
brief named as the thing to watch for); the `PREVIEW`/`ÖNİZLEME` exemption boundary held exactly as
designed (badge translated, mock-up content not); status bar and navigation bar tint showed no anomaly
across every screen captured — not provable by diff, since no pre-block reference screenshot exists on
this exact device state, so the honest claim is "no anomaly observed," not "verified unchanged."
**Not covered, with reason:** the system per-app language picker
(owner-gated, no system setting was touched); any offline path (touching connectivity risks the owner's
tethering); live TalkBack (evidence gathered via the `uiautomator` accessibility tree instead, which
surfaced finding 7 above); fontScale 2.0 clipping (a later block); a release-build cold-start comparison
against the ~766 ms baseline (blocked by the new release gate itself, see above).

**I18N-1 is CLOSED — code-complete, gate green, device-verified for the in-app language switch and every
migrated screen exercised.** Two real, previously-uncatalogued residue items (6 and 7 above) were found
during this pass's own verification and are routed to I18N-2 rather than fixed here, consistent with
Task 16's scope as verification-and-docs, not further extraction. Next: I18N-2 (data-layer suggestion
labels + `RISK_CONFIRM_LABEL` + the Home prayer-strip contentDescription bug) and/or the DS v1.1 release
gate, now blocked on the owner's `OWNER-REVIEWED` sign-off across 10 locale files.

## 2026-08-19 — I18N-2 residual localization + barrier 4

**Closes two of I18N-1's own device-smoke residue items (the Home prayer-strip `contentDescription` bug
and the hardcoded `"Current location"` label) and installs a fourth regression barrier the first three
structurally could not have caught.** Scope decided at plan time: the two remaining I18N-1 residue items
— `CalendarSuggestionProvider`/`LocationSuggestionProvider`'s fixed labels and `RISK_CONFIRM_LABEL` —
stayed out (near-zero real reach: permission-AND-flag-gated for the first, an already-recorded
owner-approved locked-vocabulary exemption for the second) and remain routed to a future I18N pass.

**Fix 1 — Home prayer names now resolve through the string seam.**
`PrayerSummaryMapper.kt` built each cell's `SidrPrayerTimeUi` with `name = name.name` — the raw Kotlin
enum literal (`"FAJR"`) — which rode verbatim into `SidrPrayerSummary`'s per-cell TalkBack
`contentDescription` regardless of app locale. Invisible in English (the locked resource value happens
to equal the enum name, which is exactly why it slipped past every I18N-1 barrier and a human reading
the English build); a `ru`/`tr` TalkBack user heard literal English. The mapper now carries a typed
`HomePrayerTimeUi(name: PrayerName, …)` and stays non-`@Composable` (`PrayerSummaryMapperTest` needs no
Robolectric host); a new `prayerNameLabel()` resolves it at render time in `HomePrayerStrip`, mirroring
`feature/prayer`'s already-correct `PrayerDetailScreen.toPrayerTimeUiList` pattern. Five new Class B
locked keys (`launcher_prayer_name_*`) copied verbatim from `feature/prayer`'s existing translations —
same duplication rationale (no `feature → feature` edge) already established for the calculation-method
names in the same file. Confirmed regression-proven, not just written: the new/changed assertions in
`PrayerSummaryMapperTest` and `LauncherScreenPrayerStripTest` were run red against the reverted
pre-fix mapper, then green after restoring it.

**Fix 2 — the device-location label translates without touching cache validity.**
`PrayerLocation.label` is dual-purpose: display text AND (via `PrayerScheduleProvenance.locationLabel`)
`GetPrayerContextUseCase.matchesSetup`'s cache-validity key. `AndroidPrayerLocationProvider`'s
`DEVICE_LOCATION_LABEL = "Current location"` therefore could not simply be swapped for a resource
lookup at the source — that would have silently invalidated every existing device-location user's cache
on upgrade. Instead `PrayerScheduleProvenance` gained a required `locationSource: PrayerLocationSource`
discriminator (the `CITY`/`DEVICE` enum already existed); the stored `"Current location"` string is
untouched and still doubles as the cache key exactly as before (`matchesSetup` was not touched); only
the *displayed* text now resolves through `sidrString` at render time, in three sites that all shared
the same latent leak — Home (`homeLocationLabelText`), the prayer detail screen
(`PrayerDetailScreen.locationLabelText`), and a third, previously unnoticed instance in
`PrayerSettingsScreen.kt`'s "Current: %1$s (%2$s)" line, which had already translated the parenthetical
source annotation via the pre-existing `locationSourceLabel()` but was still splicing the raw English
label in front of it. The DTO (`data/prayer`'s `ProvenanceDto`) defaults the new field to `CITY` so a
schedule cached before this field existed still decodes — proven by a new round-trip test seeding a
hand-crafted pre-migration JSON payload — rather than treating an entire pre-upgrade cache generation as
corrupt; the worst case for an existing `DEVICE`-sourced cache entry is one cosmetic English render that
self-heals on the very next `VERIFIED_CURRENT` recompute, since cache *validity* was never gated on this
field.

**Barrier 4 — `DomainIdentifierLeakGuardTest` (`app/src/test/…/i18n/`).** I18N-1's three barriers
(`HardcodedUiTextGuardTest`, `StringSeamGuardTest`, `LocaleCompletenessGuardTest`) all assume the
offending value is a missing/hardcoded string literal; none of them could structurally have caught Fix
1's bug, where a real, translated resource existed and was simply never called. The new barrier scans
the same spec §3.1 module roots (its own independent `scopedRoots` copy + guard-the-guard, same
convention as `LocaleCompletenessGuardTest`'s independent `modulePrefixes`) for a display-sink
assignment (`label =`, `contentDescription =`, `name =`, …) whose right-hand side is a **bare**
`.name` / `.toString()` / `.key` / `<Id>.value` property chain, anchored to start immediately after the
sink's `=` — not a broader "any occurrence anywhere on the line" scan, which was calibrated against the
real codebase and found to false-positive heavily on ordinary code (`count.toString()` for plain
Int-to-text formatting, `it.name == name` equality comparisons). Two exemptions recorded, both
pre-existing and spec-sanctioned: `SidrActionSafety.kt`'s `SidrRiskChip`/`SidrStatusChip` rendering
`tone.name.uppercase()` (spec §7.1 locked risk/status vocabulary, the same precedent already recorded
for `RISK_CONFIRM_LABEL`) and `SettingsScreen.kt`'s `count.toString()` (a plain `Int`, not a domain
identifier). Proven to fail against the pre-Fix-1 `PrayerSummaryMapper.kt`, pass against the current
one. Its real target is not Fix 1 (already closed) but the *next* occurrence of the same bug class — the
KDoc names A1's planned `ToolId`/`ToolTier`/`ToolEffect`/`ActionCategory` vocabulary explicitly, since
those are exactly this shape of risk if any of them ever reach a UI sink directly.

**Housekeeping folded into this session before the block proper started (Task 0):** a temporary
`"fr"` probe deliberately left in `LocaleCompletenessGuardTest.mainLocales` to prove a prior fix
round's guard-the-guard tests actually bite was removed, and an already-implemented-but-uncommitted
wave (deep-link `Routes.PrayerSettings` section scoping for the detail screen's Method/Madhab/Location
rows, `PrayerSettingsSection.kt`, plus the two guard-the-guard tests themselves) was committed. Neither
is I18N-2 substance; recorded here only because it landed in the same session.

**Test deltas (exact, only modules this block actually touched):** `:data:prayer` 37 → 38 (the cache
round-trip default test); `:feature:launcher` 156 → 161 (+4 `HomeLocationLabelTextTest`, +1
`LauncherScreenPrayerStripTest`); `:app` 15 → 18 (+3 `DomainIdentifierLeakGuardTest`, on top of Task
0's +2 guard-the-guard tests already folded into the 15). `:domain` stays at 333 (existing
`PrayerScheduleProvenance` construction sites updated in place, no test added/removed) — the Kotlin
compiler enforces every call site via the new required constructor param, so nothing could be missed.
`:core:android`/`:feature:prayer` untouched (0 new tests; `feature/prayer`'s `locationLabelText` follows
that module's own established convention of not unit-testing private `@Composable` label resolvers —
`madhabLabel`/`prayerNameLabel`/`locationSourceLabel` are not tested there either, and that module has
no Robolectric/Compose-test harness at all, unlike `feature/launcher`). Gate (JDK-17):
`:domain:test :core:android:testDebugUnitTest :data:prayer:testDebugUnitTest
:feature:launcher:testDebugUnitTest :feature:prayer:testDebugUnitTest :feature:settings:testDebugUnitTest
:core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :app:testDebugUnitTest testDebugUnitTest
assembleDebug` BUILD SUCCESSFUL; `core/ui/src/test/screenshots/` untouched (no core/ui edits in this
block). Root `./gradlew build` remains **red by design** post-I18N-1 (`checkOwnerReviewedLocaleStrings`,
still zero of 17 `strings_locked.xml` files owner-signed) — not a regression, not this block's to fix;
new Class B keys landed in that same unreviewed queue at zero incremental owner cost.

**A known, unclosed gap surfaced while writing this block, deliberately not fixed here:**
`checkOwnerReviewedLocaleStrings` (`app/build.gradle.kts`) checks for the `OWNER-REVIEWED` token's
*presence* in a locale file, not *coverage* of that file's actual Class B keys. Once the owner signs a
file, a later commit can add a new Class B key to it without the gate re-triggering — the file already
carries the marker, so an unreviewed addition ships gate-green. Harmless today (zero files are signed
yet), but will bite the first time a signed file gains a new locked key. Left as a named risk for
whoever closes the owner sign-off, not addressed in this block (out of I18N-2's stated scope, and
fixing it properly needs a content-hash-keyed marker, not a fix-round-sized change).

**Not in scope, deliberately:** `CalendarSuggestionProvider`/`LocationSuggestionProvider`'s fixed
`Suggestion.label`s stay English (near-zero real reach — permission-gated AND `aiSuggestionsEnabled`-gated
— and typing them touches `domain.suggestions.Suggestion`/`CachedSuggestion`/the DataStore cache/
`LauncherViewModel`, disproportionate to their reach); `RISK_CONFIRM_LABEL` stays the already-recorded
owner-approved exemption (spec §7.1, needs the full `PendingRoutedAction`/`ConfirmActionCard` typed-value
refactor to extract, out of scope for this block); voice recognition still follows the device language,
not the app language (spec §3.6, deliberate, unrelated to this block). Commits:
`fix(i18n-2): Home prayer names go through the string seam, not the raw enum`,
`fix(i18n-2): device-location label translates without invalidating the prayer cache`,
`test(i18n-2): fourth barrier - raw domain identifier assigned to a display sink`.

## 2026-08-19 — ADR 1/4 (agentic restart) — deterministic-first redefined: understanding belongs to the model, execution to the deterministic layer

**Status: ACCEPTED (owner, 2026-08-19).** First of the four strategic ADRs of Этап 1 of
[docs/superpowers/plans/2026-08-18-agentic-track-restart.md](../docs/superpowers/plans/2026-08-18-agentic-track-restart.md)
(owner decision #4 in that plan's «Решения владельца» table). Records a **deliberate change to a hard
rule**, not a clarification of it. No code ships with this ADR; the code that executes it is Этап 0.2
(FastPath localization) and Этап 0.3 (ONNX removal), and the flag change named below.

**What is replaced.** The rule as written since Phase 3 and restated in AIL-4 — *"fast local intent
matching runs before any LLM call"* (`CLAUDE.md` Hard rules; `docs/architecture.md` Principles;
`docs/roadmap.md` Guiding principles; `docs/agentic-os-architecture.md` §2.2) — was implemented as a
**filter on understanding**: `RouteCommandUseCase` consults the planner only when the rule matcher
returns `Unknown`/`LowConfidence`, and only when `llmRouterEnabled` is on. That was correct for a
router demo and is structurally fatal for an agent, because `RuleBasedIntentMatcher` is 7 English verbs
plus a 7-row command table: a multi-step goal never reaches the model at all, and on the owner's own
device (`ru-RU`) a Russian command does not work offline at all.

**The rule that replaces it** (verbatim, now the governing text in all four documents):

```
Understanding belongs to the model. Execution belongs to the deterministic layer.

1. FastPath (deterministic, localized) answers frequent exact commands without a model.
   It is a latency optimization, NOT a filter on understanding.
2. The learned-plan cache replays already-understood goal shapes deterministically and offline.
3. Everything else goes to the model planner. A FastPath miss is NO LONGER grounds to
   answer "Unknown command".
4. Nothing the model proposes executes, gains rights, or leaves the device except through
   deterministic gates: ToolRegistry → argument validation → preconditions → risk gate /
   consent → loop bounds → egress allow-list → trace.
5. Router-off / offline / no-key ⇒ FastPath + plan cache + an honest "this needs network".
   Byte-for-byte rule-only parity remains a test-checkable property.
```

**What did NOT change, and this is the point of the redefinition.** Every safety property previously
carried by "rules run first" is carried by clause 4 instead, and clause 4 is *stronger*: it is a
property of the execution path, not of the order in which two matchers are consulted. Risky actions
still never auto-run (Fork R4, AIL-5); the egress allow-list is still the only way anything leaves the
device (`OutboundContextPolicy`, Block L); the offline launcher core is still never blocked. What was
given up is only the claim that a deterministic matcher gets to *decide what the model is allowed to
see*.

**Fork resolved by the owner (2026-08-19) — `FeatureFlags.llmRouterEnabled`: inverted, on a NEW key.**
The flag stops being the gate on understanding. Understanding is available whenever a provider is
configured; the explicit opt-out survives for users who want it:

- `FeatureFlags.llmRouterEnabled` (key `flag_llm_router_enabled`, default `false`) is **removed** from
  the domain model.
- A new `FeatureFlags.localOnlyMode` on a **new** key `flag_local_only` (default `false`) takes its
  place, and the Settings toggle is relabelled accordingly (currently "Smart command routing").
- **Why a new key rather than a default flip** — the exact DS-11 `alwaysShowNavBar` → `autoHideNavBar`
  precedent, and it was caught on device there, not in review: `PreferencesMapper.toPreferences` writes
  the **whole** object on every update, so any install where any setting was ever changed already has
  `flag_llm_router_enabled = false` persisted, and a stored value beats a changed default. A flip in
  place would be inert for exactly the users who have used the app. The old key is orphaned, inert, and
  dropped from `ALL_KEY_NAMES`; there is no migration.
- **Parity stays testable.** The existing rule-only parity test does not disappear, it re-anchors:
  `localOnlyMode = true` (or no provider configured, or offline) ⇒ the planner is never consulted ⇒
  byte-for-byte FastPath outcome. `RouteCommandUseCaseTest` keeps proving it, against the new flag.

**Ordering consequence, deliberate.** With the flag inverted, an un-localized FastPath becomes
user-visible in a new way: a `ru`/`tr` command that misses FastPath now reaches the planner (good) but
costs a network round-trip for what should have been instant (bad, and offline it degrades to the
honest "needs network" instead of silently launching Telegram). This is why Этап 0.2 (localize
FastPath) is a **prerequisite** of shipping the flag change, not an independent cleanup — recorded
here so the sequencing is not rediscovered later.

**Consequences for the docs, applied in this same commit:** `CLAUDE.md` Hard rules, `docs/architecture.md`
Principles + AI-execution-pipeline, `docs/roadmap.md` Guiding principles, and
`docs/agentic-os-architecture.md` §2.2 / §4.2 now carry the new rule. The AIL-4 blocking ADR
("2026-07-05 — AIL-4") is **not** rewritten — it recorded a decision that was correct at the time;
this ADR supersedes it and says so.

## 2026-08-19 — ADR 2/4 (agentic restart) — platform re-baseline 2026: ONNX NLU closed, LiteRT/LiteRT-LM designated, tools come from the OS

**Status: ACCEPTED (owner, 2026-08-19).** Second of the four Этап-1 ADRs (owner decision #2). Closes a
14-month-old open question, retires ~1400 lines of inert code, and names the local-inference runtime
that a future local planner targets. Executes as Этап 0.3 (removal) and Этап 2.1 (toolchain).

**1. ONNX NLU is closed, not paused.** `:data:ai-local` was built in full against
`ModelDownloadConfig.INTENT_NLU_PENDING` — a blank URL and a blank hash — in Blocks P/Q (2026-06-28).
`ModelStore`, `Sha256Verifier`, `ModelProvisioner`, `KtorModelDownloader`, `ModelDownloadWorker`, the
scheduler, `OnnxIntentClassifier`, `OnnxSessionFactory`, `WordPieceTokenizer`, `LayeredIntentMatcher`,
`NluConfidenceCalibrator`: complete, tested, and shipped in every APK since, consumed by nobody. No
consumer arrived in 14 months. Beyond the dead weight, the design itself no longer fits the target: a
7-label classifier does not extract arguments (a regex `SlotExtractor` does), and by construction it
can only ever *suggest* — `NluConfidenceCalibrator` maps its softmax into `[0.50, 0.85)` precisely so a
model-driven intent never auto-executes. That is the opposite shape from what an agent needs, which is
a planner emitting structured multi-step calls.

**Closed with it: OQ#1** (`intent.onnx` + pruned multilingual `vocab.txt`), **OQ#2** (model hosting URL
+ SHA-256), **OQ#3** (embedding model/host/hash, closed together with the `TextEmbedder` port and the
never-started Block V semantic re-rank). These stop being open questions; they are answered "not this
way".

**Measured cost, "before" baseline (2026-08-19).** Taken from the **first release APK this project has
ever produced** — `:app:assembleRelease` became runnable the same day, when the owner-review locale gate
was cleared (Этап 0.1). `app-release-unsigned.apk` = **78 MB**, of which ONNX is **70.4 MB across 8
entries** — i.e. **~90% of the release artifact is a runtime that has never once executed**, because no
model was ever provisioned:

| Entry | Size |
|---|---|
| `lib/x86_64/libonnxruntime.so` | 20.3 MB |
| `lib/x86/libonnxruntime.so` | 20.3 MB |
| `lib/arm64-v8a/libonnxruntime.so` | 17.6 MB |
| `lib/armeabi-v7a/libonnxruntime.so` | 12.3 MB |
| 4× `libonnxruntime4j_jni.so` | 3.3 MB total |
| **ONNX total** | **70.4 MB** |
| everything else (incl. `classes.dex` 5.2 MB, GeoNames city index 1.0 MB) | ~7.6 MB |

**Honest reading of that number.** 70.4 MB is the *universal* APK, carrying all four ABIs. Shipped as an
AAB, a single device downloads one ABI, so the per-device ONNX cost is ~18.5 MB (arm64-v8a: 17.6 + 0.9
JNI) — still roughly **70% of what an arm64 device would download**. Both figures are recorded because
quoting only the 70.4 MB would overstate the per-user cost, and quoting only the 18.5 MB would understate
what is actually committed to the repository and to every universal build. Этап 0.3 records the "after".

**Kept deliberately:** `DeviceProfile` / `DeviceCapability` / `DeviceProfileProvider` /
`AndroidDeviceProfiler` — they gate suggestion precompute and are unrelated to ONNX.
`LocalInferenceGate` is a judgment call left to Этап 0.3: it is the natural gate for a future
local-inference tier, but today it is wired to ONNX alone; if 0.3 does not re-use it, delete it and
reintroduce it when the tier is real. Deleting and reintroducing is cheaper than carrying a second
`INTENT_NLU_PENDING`.

**2. The designated local-inference runtime is LiteRT / LiteRT-LM.** Not ONNX, not raw llama.cpp. The
previous wording ("MediaPipe LLM or llama.cpp", `docs/adr/ADR-001-hybrid-ai.md` §4) named a wrapper
instead of its base — MediaPipe LLM Inference is built on LiteRT. Three reasons:

- **One path to NPU across major chipsets** via the `CompiledModel` API (LiteRT 2.x): a JIT approach —
  one model, accelerator chosen at runtime. This matters specifically because SIDR is `minSdk 28`,
  sideloaded, and runs on an arbitrary device fleet.
- **Function calling with constrained decoding, plus `FunctionGemma` and the Tool Use APIs** — the
  agentic workload out of the box, rather than classification that then needs a hand-written planner
  layer above it.
- **Android + iOS + Windows + Linux from one runtime** — the same runtime serves both consumers of the
  portable core (ADR 3/4), instead of a second inference stack for the PC target.

**Recorded alternative — ExecuTorch, with an explicit switch condition.** Rejected *now* for three
reasons: its AOT compilation per backend produces a `.pte` matrix across an arbitrary device fleet,
which is exactly the problem LiteRT solves with JIT; function calling / constrained decoding are not
part of it (it is a runtime — you bring that layer yourself); and its principal advantage, an existing
PyTorch pipeline, does not exist here (PyTorch usage in this repo is zero). **Switch condition, stated
so it is recognizable when it arrives:** the moment SIDR fine-tunes its own planner model on its own
data — and the learned-plan cache is a natural source of `goal → plan` pairs — PyTorch → fine-tune →
ExecuTorch becomes more coherent than converting to LiteRT.

**The choice stays replaceable as long as the `Planner` port is runtime-agnostic** (spec A4 already
requires this). This ADR fixes a direction, not a dependency.

**`AICore` / Gemini Nano is a separate path**, for devices that have it: the model lives in a system
process, so the app's heap is not spent at all. Its limitation is hardware availability, not design.

**3. Why locality is achieved by a plan cache and not by a model in-process.** The binding constraint
is not a budget number, it is an order of magnitude combined with the role of the process. A 1B model
at int4 is 700 MB–1 GB resident; a launcher must resurrect instantly, and the heaviest process is the
first one LMK kills. **Correction to the earlier, too-broad claim** ("relaxing the memory budget
changes nothing"): that is true for 1B. For a small function-calling model (`FunctionGemma`, small
Gemma 4) on NPU the order is different — roughly 200–500 MB, with weights potentially memory-mapped.
Combined with the budget rework below, local planning moves from "impossible" to **"real on capable
hardware, as an opt-in"**. The plan cache is the mechanism that makes locality real *today*, on all
hardware, without any model in-process.

**4. Tools come from the OS, not only from our own catalog.** `AppFunctions` (Android 15+) and `MCP`
(PC + network) are the same shape — a self-describing function with a schema, discoverable in a
registry — and both are named here as first-class tool sources. This is what makes the PC target one
more adapter rather than a rewrite (ADR 3), and it is the reason Этап 5 rewrites the A1 spec rather
than implementing it: the 2026-07-11 A1 spec was written against a **closed** vocabulary of seven
identifiers, and both options in its unresolved fork ("parallel vocabulary" vs "evolve in place") rest
on that assumption. **The A1 fork itself is deliberately NOT decided here** — it belongs to Этап 5, on
the rewritten spec. What *is* decided here is the premise that invalidates the old framing.

**5. Performance budgets move from "hard targets" to a three-tier scheme** (`docs/architecture.md`,
edited in this commit). The problem was never that the numbers were missed; it is that they were
already drifting with no gate. Cold start went 2021 ms (debug) → **504 ms** (release pre-R8, the best
ever measured) → 527 (R8) → 750 (Baseline Profile) → **766 ms** (final release), each step with a good
local reason and no single decision to blame. The heap ceilings (80/150/250 MB) have **never been
measured** — the "~98MB → ~75MB" figure recorded in Y1/Y2 is post-R8 APK size, not heap. An
unreachable number stops functioning as a constraint. The three tiers:

1. **Invariant (does not float)** — properties, not milliseconds: first frame without a spinner
   (achieved), FastPath feels instant, the process survives backgrounding. A violation is a broken
   product.
2. **Measured baseline + regression gate (floats only deliberately)** — cold start and heap. Instead of
   `< 400 ms`: *"766 ms today; a block that worsens this requires a recorded decision."*
3. **Per device profile (genuinely floating)** — the existing mechanism ("any feature that cannot meet
   them must degrade or be disabled on that device profile") is kept as-is.

Measuring heap on device (`dumpsys meminfo`) for the first time is Этап 0.6, not this ADR.

**6. `docs/adr/ADR-001-hybrid-ai.md` is superseded in part** (edited in this commit): §2 (ONNX for
local NLU/classification/embeddings) and §4 ("future MediaPipe LLM or llama.cpp integration") no longer
hold; §1 is restated by ADR 1/4 above; §3, §5, §6 stand.

## 2026-08-19 — ADR 3/4 (agentic restart) — portable core boundary: what "Framework" is, and the `ActionIds` byte-for-byte constraint

**Status: ACCEPTED (owner, 2026-08-19).** Third of the four Этап-1 ADRs (owner decision #1). Answers a
question that has been ambiguous since the 2026-07-05 three-stage reframe: *what, concretely, is the
"AI Framework"?*

**1. "Framework" = a portable agent core with two consumers, not a schedule stage.** Stage 2 as a
**stage of the schedule is abolished**; its content is built inside vertical slices, per the project's
existing feature-first rule. The core is the pure-Kotlin agent engine; the two consumers are the
Android shell (shipping) and a PC shell (target). Two consumers of one core — **not two codebases**,
and explicitly **not a public SDK for third-party developers** (that remains a non-goal).

**What is inside the boundary** (all pure, stdlib + coroutines, no Android / Compose / Ktor / Hilt /
Room / DataStore / `core/*` — the existing `:domain` purity invariant extends unchanged):

```
:domain (existing)  +  domain/tool     ToolId, ToolDescriptor, ToolInvocation, ToolResult,
                                       ToolRegistry, ToolExecutor
                    +  domain/agent    AgentGoal, ExecutionPlan, PlanStep, Planner,
                                       AgentExecutor, AgentSession, ExecutionState,
                                       RuntimeBudget, ConsentCheckpoint
                    +  domain/context  ContextEngine, ContextSnapshot + its two projections
                    +  domain/memory   generalizes ResolutionPreferenceStore (S2-1) + AliasStore (S2-2)
                    +  domain/trace    ExecutionTrace, step events
```

**What is outside:** everything platform-shaped — the tool *adapters* (`SYSTEM_INTENT` on Android,
`AppFunctions`, `MCP`, accessibility), the inference transports, persistence, and every surface. The
line is the existing ports/impls line, applied to the agent.

**2. KMP decision: `:domain` moves to `kotlin.multiplatform` with `android` + `jvm` targets, in Этап 2.2.**
Rationale is timing, not preference: `domain/build.gradle.kts` is today `kotlin.jvm` + `jvmToolchain(17)`
with exactly one dependency (`coroutines.core`), so the conversion is near-mechanical **right now**, and
every subsequent agentic block is written as pure domain and raises the price. Verification for that
step is already fixed: `./gradlew build` green, `:domain` compileClasspath still stdlib + coroutines,
and all 333 domain tests pass **without test-source changes**.

**3. Mandatory constraint recorded here because it holds under every variant of A1′, under federation,
and under KMP — `ActionIds` values are frozen byte-for-byte.** Found while working the A1 fork on
2026-08-19 and verified against the code. The seven values have **two different contracts, not one**,
and both must be stated because the reason differs:

```
launch_app   → a Room PRIMARY KEY. Table `resolution_preferences`
               (primaryKeys = ["action_id", "query", "context_key"], Migration1To2).
               The single production write site is
               ResolveCommandWithPreferenceUseCase.kt:34 — CapabilityKey(LAUNCH_APP, slot).
               Changing the value does NOT "just reset preferences": it leaves orphaned rows
               that keep occupying the 200-record retention quota, invisibly.

all seven    → an outbound WIRE contract. CatalogSchemaRenderer renders them into the LLM
               prompt; ProposalValidator validates the model's answer against them
               (fail-closed: unknown id ⇒ NoPlan). Covered by RouterOutboundGuardTest and
               DefaultActionCatalogRouterSchemaGuardTest.
```

`ActionIds.kt:12` already carries the "MUST stay stable — persisted/wire contract" comment. The
requirement on A1′ (Этап 5) is that the seven current values survive **byte-for-byte** into whatever
federated vocabulary replaces the catalog; new namespaced identifiers arriving from outside
(`AppFunctions`: `<pkg>/<fn>`, `MCP`: `<server>/<tool>`, per ADR 2/4 §4) are additive and must not
force a rename of the existing seven.

**4. Not decided here, deliberately:** the A1 fork "parallel vocabulary vs. evolve in place". It was
argued against the 2026-07-11 spec, i.e. before ADR 2/4 established that identifiers arrive from
outside the app; both of its options are built on a premise ("the vocabulary is closed") that no longer
holds. It is decided in Этап 5, on the rewritten spec.

## 2026-08-19 — ADR 4/4 (agentic restart) — Assistant ⊕ Agent: one conversational loop, two surfaces

**Status: ACCEPTED (owner, 2026-08-19).** Fourth of the four Этап-1 ADRs. Resolves the last open fork
of the restart plan. Design decision only; the contracts it describes are built in Этап 4 (A0 spike)
and Этап 6 (A4′ runtime).

**The question.** Today `GenerateReplyUseCase` (speaks, streams `Flow<AiChunk>`) and `CommandPlanner` /
`RouteCommandUseCase` (routes, never executes) are separated by a hard rule — *"matching ≠ generation"*.
An agent needs a contour that speaks **and** acts **and** asks follow-up questions across several turns.
Is that one loop or two?

**Decision (owner, 2026-08-19): one loop in the domain, two surfaces in the UI.**

```
domain/agent
  AgentSession        one persisted state machine
  Planner (port)      plan(goal, context, tools, memory) → ExecutionPlan of 0..N steps

  0 steps  → a spoken answer      → rendered by the Assistant screen (streaming text)
  N steps  → a plan with gates    → rendered by the Tasks / Agents surfaces (plan + consent + trace)
```

**Why one loop.** A reply with no tool calls is not a different kind of thing from a plan — it is a plan
of zero steps. Making that the *degenerate case* of one contract buys three things that two contours
would each have to build twice:

- **Clarification becomes multi-turn for free.** `PlanResult.Clarify(question)` is today a terminal
  leaf: the model asks, and the exchange ends. No contract anywhere expresses *"agent asked → human
  answered → plan refined → continue"*. With one session state machine, a clarification is a state, not
  a terminus. This gap is one of the three specs the restart plan names as missing from every existing
  document.
- **One place where consent, loop bounds, trace, and egress live.** This is the "Правило роста"
  exception applied: the functionality scales, the boundaries do not get added later. Two contours
  means two places to forget the gate.
- **One thing to persist.** The Android process-death problem (Этап 6: the agent's own action sends the
  user into another app, and the launcher process may be killed) needs exactly one persisted session
  type, not one for chat and one for plans.

**Why two surfaces, not one.** Collapsing the Assistant screen into the agent surface would make an
ordinary question ("what is X") arrive dressed as a task with a plan and a trace — a direct violation
of the existing design rule *"the more ordinary the action, the less UI it generates"*
(`agentic-os-architecture.md` §3.6) and of Sukun/calm. The surface is a **function of session state**,
not of a layout: a 0-step session renders as streaming prose on the Assistant screen; an N-step session
renders as plan + gate + trace on Tasks/Agents. The two `PREVIEW` tabs already reserve the place
(they become real in A4 — Этап 4/6).

**Consequence for the hard rule.** *"Matching ≠ generation"* stated the separation of two **ports**.
Under ADR 1/4 the correct axis is different, and the rule is restated as **understanding vs.
execution**: one contour may both speak and act; what may never merge is *proposing* and *executing*.
`GenerativeAiEngine` (transport, `Flow<AiChunk>`) stays a distinct port from `Planner` (structured
decision) — those are two different shapes of answer from a model, and that separation is unaffected.

**What is explicitly NOT decided here** and belongs to the A4′ spec (Этап 6): the session state
vocabulary, the persistence schema, the resume/idempotence protocol, the shape of the clarification
turn on the wire, and whether the Assistant screen keeps its own ViewModel or renders an
`AgentSession` projection. This ADR fixes that there is **one** contract underneath, and two renderings
above it.

## 2026-08-19 — Этап 0.2 complete — FastPath localized to ru/tr

**Status: CODE-GREEN.** Agentic restart plan (`docs/superpowers/plans/2026-08-18-agentic-track-restart.md`),
Этап 0.2. Precondition for the ADR 1/4 `llmRouterEnabled` → `localOnlyMode` flag inversion (still
pending, later in Этап 0/1 follow-through) — with the flag inverted, a FastPath miss on `ru`/`tr` stops
being "Unknown command" and starts costing a network round-trip for something that should be instant;
offline degrades honestly only if FastPath itself understands the locale first. Closes the concrete
device regression: on the owner's SM-A325F (system locale `ru-RU`, `llmRouterEnabled=false`), "открой
телеграм" classified as `UnknownIntent` at confidence 0.10.

**What changed.** `RuleBasedIntentMatcher.kt` (`:data:repository`) — the sole file named by the plan's
0.2 section. Every verb/keyword vocabulary (`LAUNCH_VERBS`, `INSTALL_VERBS`, `SEARCH_VERBS`,
`SETTINGS_KEYWORDS`, `SIMPLE_COMMANDS`) gained `ru` and `tr` forms alongside the existing `en` ones.
Two structural changes were required beyond "add words to the sets", both driven by the plan's own
verification example (`telegramı aç` must reach `LaunchAppIntent` at confidence 0.90, parity with
`open telegram`):

1. **Verb forms became locale-tagged, not a flat `Set<String>`.** A flat set can't be asked "does `tr`
   have a form?" — [`FastPathLocaleGuardTest`](../data/repository/src/test/java/com/sidr/launcher/data/repository/intent/FastPathLocaleGuardTest.kt)
   needs that question answerable. New `VerbForms(prefixByLocale, suffixByLocale)` data class per
   grammatical role, mirroring the `OutboundContextPolicy`/`PreferencesKeys.ALL_KEY_NAMES` precedent of
   exposing a guard-tested inventory as public constants rather than hiding it behind `private`.
2. **Turkish is SOV (verb-final), not SVO.** "telegramı aç" is object-then-verb — the existing
   `input.startsWith("$verb ")` prefix mechanism structurally cannot recognize it; the verb-final case
   needed a mirrored `input.endsWith(" $verb")` suffix check. This is why `VerbForms` splits
   `prefixByLocale` (en/ru) from `suffixByLocale` (tr) rather than just adding a `tr` key to one map —
   position is a property of the language, not just vocabulary.

`ru` forms cover the imperative + infinitive pairs the plan named verbatim: открой/открыть
(launch), запусти/запустить (launch), найди/найти (search), установи/установить (install). `tr`
forms use the natural verb-final construction: `aç` (open), `kur` (install), `ara` (search), plus bare
settings keywords (`ayarlar`, `başlatıcı ayarları`) and a `SIMPLE_COMMANDS` table (`asistan`,
`uygulamaları göster`, `temizle`, `yardım`, …). `CommandNormalizer.normalize` was **not** touched — it
already lowercases via `Locale.ROOT`, which the plan confirmed already handles Turkish dotted/dotless
İ/ı correctly.

**Deliberate non-fix, documented not silently assumed:** Turkish noun-case suffixes (the accusative
`-ı` in "telegramı") are **not** stripped from the extracted app-name query. `IntentActionResolver`
does an exact case-insensitive label match, so `displayNameQuery = "telegramı"` will not resolve against
an installed app labeled "Telegram" unless the user also drops the case ending. Full Turkish
morphological analysis (four-way vowel harmony, consonant softening, buffer consonants) is out of a
"days not weeks" cleanup stage and was never named by the plan body — only the dotted-I casing was.
Documented in the matcher's KDoc as a known limitation, not shipped as a silent partial fix. The
on-device verification the plan requires ("открой телеграм" launches Telegram) does not depend on this:
Cyrillic "телеграм" and the launch-verb query extraction were already correct before this change: the
regression was the verb not being recognized at all, not app-name resolution.

**Guard test.** `FastPathLocaleGuardTest` (`:data:repository`), mirroring
`LocaleCompletenessGuardTest`'s shape but reading the Kotlin constants directly (no `res/values` —
`RuleBasedIntentMatcher` stays Android-free and this vocabulary is deliberately not a UI string).
Three tests assert every one of `LAUNCH_VERBS`/`INSTALL_VERBS`/`SEARCH_VERBS`/`SETTINGS_KEYWORDS_BY_LOCALE`/
`SIMPLE_COMMANDS_BY_LOCALE` carries a non-empty form for `en`, `ru`, and `tr`.

**Test coverage.** `RuleBasedIntentMatcherTest` gained a table-driven parity test (the plan's own
example set — "open telegram"/"открой телеграм"/"telegramı aç"/"search weather"/"найди погоду", all
reaching the right intent type at confidence ≥ 0.85) plus focused tests for the `ru`/`tr` install verb,
bare settings keyword, simple command, and bare-verb-alone paths. `RuleBasedIntentMatcherTest`: 34 → 43
tests. `FastPathLocaleGuardTest`: 3 new tests. No existing English-path test changed or was touched.

**Verification (JDK-17, `local.properties`-free Temurin 17 toolchain, exit 0, not piped through
`tail`):** `:data:repository:testDebugUnitTest` green (63 tasks, 43+3 new tests all passing); root
`testDebugUnitTest assembleDebug` `BUILD SUCCESSFUL` (543 tasks); `:core:ui:verifyRoborazziDebug`
`BUILD SUCCESSFUL` — untouched, as expected (no UI/goldens in scope for 0.2). `git diff --stat`: exactly
`RuleBasedIntentMatcher.kt` + `RuleBasedIntentMatcherTest.kt` modified, `FastPathLocaleGuardTest.kt`
added — no scope beyond the plan's named file.

**Not done here (out of scope for 0.2, per the plan):** 0.1 (owner action, already closed), 0.3 (ONNX
removal), 0.4 (CLAUDE.md compression), 0.5 (honest statuses), 0.6 (measured perf budgets), 0.7 (already
closed). The `llmRouterEnabled` → `localOnlyMode` flag inversion itself (ADR 1/4's decision) is not
flipped by this stage — 0.2 only removes the precondition blocking it.

## 2026-08-19 — Этап 0.3 complete — ONNX stack removed

**Status: CODE-GREEN.** Agentic restart plan (`docs/superpowers/plans/2026-08-18-agentic-track-restart.md`),
Этап 0.3. Executes ADR "2026-08-19 — ADR 2/4 (agentic restart)" (platform re-baseline: ONNX NLU closed,
OQ#1/#2/#3 closed, designated local-inference runtime is LiteRT/LiteRT-LM, not ONNX). The plan's own
words: *"без него это было бы оправдание задним числом"* — the ADR had to land first (it did, in Этап 1)
for this deletion to be a consequence of a decision rather than a justification invented after the fact.

**What changed.** Deleted per the plan's file-by-file radius: the whole `:data:ai-local` module (18 main
files + tests + assets), `:app` DI (`NluMatcherProvidesModule`, `NluMatcher`, `ModelProvisionProvidesModule`,
`ModelProvisionBindsModule`), `:app` work (`ModelDownloadWorker`, `WorkManagerModelDownloadScheduler`,
`SessionLifecycleReleaseTest`), the ONNX branch of `SidrLauncherApp` (`ModelManager.ensureModel()`,
the `SessionLifecycle` set, `onTrimMemory`/`onLowMemory`'s ONNX teardown — `Configuration.Provider` and
WorkManager init stay, they're now owned solely by `SuggestionPrecomputeWorker`/`UsageCleanupWorker`),
`:data:repository`'s `LayeredIntentMatcher`/`NluConfidenceCalibrator` (+ tests) and
`ModelAvailabilityRepositoryImpl` (+ test, + the `model_available_ids` key dropped from
`PreferencesKeys.ALL_KEY_NAMES`), `:data:ai-cloud`'s `KtorModelDownloader` (+ test), and `:domain`'s
`ai/local/` package (`ModelId`/`ModelAvailability`/`ModelAvailabilityRepository`/`TextEmbedder`/
`ModelDownloader`/`ModelFilePresence`, + `LocalNluContractsTest`) and `MatcherSource.NLU`. The unqualified
`IntentMatcher` in `IntentProvidesModule` now binds `RuleBasedIntentMatcher()` directly — no composite,
no qualifiers. `settings.gradle.kts`, `app/build.gradle.kts` and `gradle/libs.versions.toml` (the
`onnxRuntime` version + `onnxruntime-android` library entry) lost their `:data:ai-local` references.

**Necessary consequences beyond the plan's named list (compiler-forced, not scope expansion).** The
plan's radius list did not name three things that could not compile once `TextEmbedder`/
`ModelAvailabilityRepository` left `:domain` — each is a direct, single-purpose consumer of a type the
plan explicitly deletes, not a discretionary addition:

1. **`SemanticSuggestionRanker`** (`:data:repository/suggestions`, + its test) — a Block V decorator
   whose entire body is "embed via `TextEmbedder`, gate via `LocalInferenceGate`, else fall back to
   heuristic". With both gone it has no remaining reason to exist; `SuggestionsProvidesModule
   .provideSuggestionRanker` now returns `HeuristicSuggestionRanker()` directly (Block V was always
   inert in production — OQ#3 never pinned an embedding artifact — so this is a no-op for shipped
   behaviour, matching the DS-11 "no plan, no scope" precedent, not a regression).
2. **`LocalInferenceGate`** (`domain/device/`, + its test) — the plan's own text names this as a
   decision to make at execution time ("если не переиспользуется в Этапе 0 — удалить"). Its three
   callers were `OnnxIntentClassifier`, `OnnxTextEmbedder` (both deleted with `:data:ai-local`) and
   `SemanticSuggestionRanker` (deleted above) — zero remaining callers, so it is deleted per the plan's
   own stated condition, not a new decision. `DeviceProfile`/`DeviceCapability`/`DeviceProfileProvider`/
   `AndroidDeviceProfiler` are untouched, exactly as the plan requires (suggestion-precompute gating
   still reads `profile()`/`capability()` directly).
3. **`@RuleMatcher`/`@EmbeddingModelConfig` qualifiers** — dead after (1)/(2) removed their only
   consumers; deleted rather than left as unused Hilt qualifiers.

**One binding had to move, not just disappear.** `DeviceProfileProvider`'s `@Provides` lived in
`ModelProvisionProvidesModule`, which the plan deletes wholesale — but `DeviceProfileProvider` itself is
explicitly retained (used by `SuggestionPrecomputeGate` for LOW_END/battery gating and by
`LauncherActivity` for motion suppression). Deleting the module without relocating the provider is a
missing Hilt binding, not a smaller graph — `:app:hiltJavaCompileDebug` failed with exactly this
`[Dagger/MissingBinding]` on the first build attempt. New `app/di/DeviceProfileProvidesModule.kt` carries
the one `@Provides` method verbatim from the deleted module; nothing else moved.

**KDoc-only edits (no behavior change), three files:** `AndroidDeviceProfiler.kt`, `DeviceProfileClassifier.kt`,
`DeviceCapability.kt`, `DeviceProfileCacheMapping.kt` each had a comment naming `OnnxIntentClassifier` or
`LocalInferenceGate` as the reason a field/gate exists; reworded to the surviving reason
(suggestion-precompute gating) so no comment points at a deleted class.

**Verification (JDK-17, `local.properties`-free Temurin 17 toolchain, exit 0, not piped through `tail`):**
root `testDebugUnitTest assembleDebug` `BUILD SUCCESSFUL` (509 tasks); `:core:ui:verifyRoborazziDebug`
`BUILD SUCCESSFUL`, goldens untouched (no UI in scope); `:app:assembleRelease` `BUILD SUCCESSFUL`;
`:domain:dependencies --configuration compileClasspath` = `kotlin-stdlib` + `kotlinx-coroutines-core`
only, confirmed by direct inspection (unchanged — `domain/ai/local` was already pure Kotlin, so this
was true before too, now it's true with less surface). **APK size: 78 MB → 6.8 MB
(`app-release-unsigned.apk`, 7,052,702 bytes)** — the ADR 2/4 "before" baseline (78 MB, ONNX 70.4 MB,
recorded in 0.1) minus the ONNX AAR/model-path code, R8-shrunk. `git diff --stat`: 80 files touched
(mostly deletions) + one new file (`DeviceProfileProvidesModule.kt`), net -4904/+~70 lines — no file
outside the plan's radius plus the three necessary-consequence items above.

**Device regression check not re-run here:** the plan's Этап-0 verification list includes an on-device
"открой телеграм" check on `ru-RU`/`llmRouterEnabled=false`/airplane-mode — that regression belongs to
0.2 (FastPath localization), which already device-relevant-verified it as a code path; 0.3 does not touch
`RuleBasedIntentMatcher`'s vocabulary or the FastPath route, so re-running the physical-device pass here
would not exercise anything this stage changed. No device available in this session either way.

**Not done here (out of scope for 0.3, per the plan):** 0.4 (CLAUDE.md compression), 0.5 (honest
statuses), 0.6 (measured perf budgets — still pending the first-ever heap measurement). The
`llmRouterEnabled` → `localOnlyMode` flag inversion (ADR 1/4) is still not flipped by any 0.x stage so
far — HANDOFF still flags it as needing an explicit owner call on which session does it.

## 2026-08-19 — Этап 0.4 complete — `CLAUDE.md` compressed 101 KB → 15 KB

**Status: CODE-GREEN (docs only — zero production code, zero test changes).** Agentic restart plan
(`docs/superpowers/plans/2026-08-18-agentic-track-restart.md`), Этап 0.4: *«90 KB → ~10–15 KB: текущее
состояние, hard rules, указатели. История переезжает в `decisions.md`, где ей место.»* The file had
grown to **101,025 bytes / 1,093 lines**, of which ~80 KB was a narrated archive of closed phases going
back to June: every session paid that reading tax before doing any work.

**Two forks put to the owner before writing (both answered, both taken as recommended):**

1. *Where does the removed history go?* — the plan says "moves to `decisions.md`", but I first verified
   that it is **already there**: every narrated block has a matching ADR
   (Blocks A→H, I→N, O→R, S→W; Phase UX X1–X6; Phase 9 Y1–Y7; AIL-0…AIL-6; S2-1/S2-2; DS-0…DS-11 +
   Vision MVP; I18N-1/I18N-2; the four agentic ADRs; Этапы 0.2/0.3), and the specific facts most at risk
   of being lost were spot-checked one by one in `decisions.md`: the DS-7 follow-ups (Aliases list
   ordering, discarded `OperationResult`s, tab-bar label clipping at fontScale 2.0), the I18N-1/DS-10
   "not covered on device" lists, the `OWNER-REVIEWED` presence-vs-coverage gap, DS-6B's MWL caveat, the
   Android 9/11/14 + LOW_END matrices, boot warmup, and OQ#4's `Ready`/`Partial` states. All present.
   **Decision: pointers only** — nothing copied, no archive duplicate file; `9de23ab:CLAUDE.md` holds
   the 101,025-byte original verbatim in git.
   *(Этап 0.1 is the one item with no dedicated ADR of its own — it is recorded in
   `current-status.md` and inside ADR 2/4, which carries its 78 MB measurement. Writing a retroactive
   0.1 ADR was offered as the third option and declined as scope expansion.)*
2. *Where does the `Contract → Owner module` table live?* — it is ~4.6 KB of a 10–15 KB budget.
   **Decision: it stays in `CLAUDE.md`**, cleaned: per-block annotations (`*(Block Q ✅)*` etc.) removed,
   the ONNX/local-model-provisioning row removed (deleted by 0.3), rows added for the shipped
   `:data:prayer` / prayer domain that the table never listed.

**Shape of the new file (15,420 bytes):** a header stating what the file is and where the history went ·
*What this is* · *Current goal* (agentic track, stage table, the four ADRs in one paragraph each, the
deliberately-undecided A1 fork) · *Shipped surface* · *Known debt* · *Hard rules* (carried **verbatim** —
this is the one section that must not be paraphrased) · *Build & verification gate* · *Contract → Owner
module* · *Do not* · *Source of truth* + a *History map* naming which ADR to open per track.

**Three things were added rather than deleted, because they were load-bearing but buried inside the
archive being removed:**

- **`Build & verification gate`** — the JDK-17 toolchain requirement (the machine's default JDK is
  newer and Gradle cannot parse it) lived only inside AIL-6's narration; the **"never pipe `gradlew`
  through `tail`"** rule (it masked a red gate as exit 0 on 2026-07-13) lived only in the plan's §0 and
  in agent memory; the closing discipline (gate → ADR → docs → *propose* a commit, agent never pushes)
  lived only in the plan. All three now sit in the file every session reads first.
- **`Known debt`** — a five-bullet honest list (device-pending DS-5 / I18N-1 / DS-6B items; 766 ms cold
  start and never-measured heap; the `OWNER-REVIEWED` presence-vs-coverage gap; the Turkish
  case-suffix limitation from 0.2; the untested Android 9/11/14 + LOW_END + STT + boot-warmup matrices).
  Deliberately written in **neutral vocabulary**, not in `CODE-GREEN`/`DEVICE-ACCEPTED`/`CLOSED`, so it
  does not pre-empt Этап 0.5 — which will formalize exactly that vocabulary and re-audit these statuses.
- **`History map`** — six lines mapping track → ADR names, so "where is DS-6B written down" costs one
  `grep` in `decisions.md` instead of a memory of the deleted digest.

**Corrections made while compressing (facts that had drifted):**
`Do not` still said *"Don't start Phase 5 (cloud AI) ahead of its own approved plan"* — Phase 5 closed
2026-06-27; replaced with the live prohibitions (no `core/data`, no generative AI in `IntentMatcher`, no
`EncryptedSharedPreferences`, **no resurrection of the deleted ONNX stack**, `ActionIds` frozen, no
autonomy without consent, and no history back into this file). `Source of truth` claimed
`docs/architecture.md` was "IN SYNC as of Block H6 (2026-06-23)" — stale; ADR 2/4 rewrote its
performance section on 2026-08-19. The `Hard rules` gained two entries that already governed the project
but were only written elsewhere: `:domain` must stay KMP-ready (ADR 3/4, Этап 2.2) and the
`OutboundContextPolicy` positive-allow-list rule (Block L).

**Verification.** `./gradlew --no-daemon testDebugUnitTest assembleDebug --rerun-tasks` under JDK 17
(`/home/Suleiman/jdks/jdk-17.0.19+10`): **BUILD SUCCESSFUL in 1m 5s, 509 actionable tasks: 509
executed**, exit 0, output not piped through `tail`. The `--rerun-tasks` form was used deliberately: the
first run reported `509 up-to-date` — correct for a docs-only diff, but "up-to-date" is not a test
result, so the suites were forced to actually execute. Documentation-only change — `git diff --stat`
touches exactly `CLAUDE.md`, `ai-context/current-status.md`, `ai-context/decisions.md` and the plan file;
no `.kt`, `.xml`, `.gradle.kts` or golden PNG is modified, so the gate is a regression check that the
tree is still the green tree 0.3 left, not a check of this stage's content.

**Not done here (out of scope for 0.4, per the plan):** 0.5 (honest statuses — the debt list above is
raw material for it, not a substitute), 0.6 (measured budgets; the 766 ms / never-measured-heap facts are
recorded, not re-measured). `ai-context/current-status.md` (60 KB) and `decisions.md` (482 KB) were **not**
compressed — 0.4's radius is `CLAUDE.md`, and `decisions.md` is by design the place history accumulates.
The `llmRouterEnabled` → `localOnlyMode` inversion (ADR 1/4) is still unimplemented and still belongs to
no named stage; the new `CLAUDE.md` says so explicitly instead of leaving it implied.

## 2026-08-19 — Этап 0.5 complete — honest statuses (`CODE-GREEN` / `DEVICE-ACCEPTED` / `CLOSED`)

**Status: CODE-GREEN (docs only — zero production code, zero test changes).** Agentic restart plan,
Этап 0.5: *«Ввести CODE-GREEN / DEVICE-ACCEPTED / CLOSED. Пересмотреть текущие «CLOSED» и вернуть в
видимость device-долг: DS-5, I18N-1, DS-6B.»* This is a correction pass, not new work: no code touched,
no block re-opened. Original ADR prose below is left exactly as written (append-only log); this entry is
the addendum that supplies the missing vocabulary and points at the three places it changes the reading.

**Vocabulary, drawn from the one block that already applied it rigorously (DS-6B) plus 0.4's own
`Status:` line above:**

```
CODE-GREEN      — the build/test gate is green: `testDebugUnitTest assembleDebug`, plus
                  `verifyRoborazziDebug` when core/ui is touched. Says nothing about device
                  behavior — a compiler and a JVM test runner cannot see a screen.

DEVICE-ACCEPTED — the OWNER personally ran on-device verification and signed off. An
                  agent-driven adb/uiautomator pass does not qualify by itself — DS-6B's own
                  ADR already draws this line ("the agent-driven pass had verified only the
                  no-data invariant... Device acceptance — CLOSED 2026-08-08 (owner)").

CLOSED          — CODE-GREEN AND DEVICE-ACCEPTED, AND any known residual limitation is named
                  explicitly rather than implied absent. CLOSED is not a zero-debt claim — see
                  DS-6B's own MWL caveat, which stands next to its CLOSED label, not instead of
                  it.
```

**Reclassified — terminology corrected, no underlying fact changes, nothing re-opened:**

- **DS-5 Action & Safety** — was self-labelled `CODE-CLOSED; device acceptance PENDING` (ADR
  2026-07-13). `CODE-CLOSED` reads as closed; it was not. Correct label: **CODE-GREEN**. Device
  acceptance for DS-5's own checklist (auto-hide nav, DS-5 surfaces on real branches, accent
  switching, router-off/offline parity — listed in the 2026-07-13 ADR's own "Device-pending"
  paragraph) has still not been run. Not `DEVICE-ACCEPTED`, not `CLOSED`.
- **I18N-1 Multilingual UI** — its own ADR (2026-08-16) and `current-status.md`'s summary both
  end on an unqualified **"I18N-1 is CLOSED"** / **"CLOSED — device-verified"**. The same ADR's own
  body says otherwise: every on-device pass in it was agent-driven (`adb`, `uiautomator`), never
  run by the owner, and its own "Not covered, with reason" list names the system per-app-language
  picker, the offline path, live TalkBack, and fontScale 2.0 as untested. Under the vocabulary
  above this is **CODE-GREEN**, not `CLOSED` — the gate is green and the in-app language switch was
  agent-verified on-device, but owner sign-off never happened and four real gaps remain open, not
  hidden (they were already named in the ADR body — only the terminal label overclaimed). No new
  finding: `CLAUDE.md`'s `Known debt` already carries these four items since 0.4.
- **DS-6B Prayer Correctness** — checked against the vocabulary and confirmed correctly
  **CLOSED**: the owner personally ran the full on-device acceptance (interactive
  method/madhab/city setup, religious-correctness cross-check against authority tables) on
  SM-A325F on 2026-08-08 and signed off, per that ADR's own closing addendum. Its documented
  limitation — London/Kazan `MWL` golden times are cross-implementation-verified only, not
  authority-table-anchored like Istanbul/Makkah — stands as the named residual the vocabulary
  requires; it does not un-close the block.

**Docs synced to the vocabulary (no facts changed, only the label):**
- `CLAUDE.md` `Known debt` — the "Device-pending" bullet now states DS-5 and I18N-1 are
  `CODE-GREEN` (not yet `DEVICE-ACCEPTED`) instead of neutral prose; 0.4 deliberately withheld the
  vocabulary here to avoid pre-empting this stage.
- `CLAUDE.md` `Shipped surface` intro line, which blanket-claimed "gate-green, and device-accepted
  ... unless the debt list says otherwise" — corrected to state the vocabulary applies per item.
- `ai-context/current-status.md` — the I18N-1 section heading and the Этап 0.4-era re-base note
  both said unqualified `CLOSED`; both corrected to `CODE-GREEN`. DS-5's heading already said
  `CODE-CLOSED (device-pending)`; corrected to `CODE-GREEN`. DS-6B's heading is unchanged
  (`COMPLETE — device-accepted by the owner`; already accurate under the new vocabulary read as
  `CLOSED`).

**Verification.** Docs-only change — no `.kt`/`.xml`/`.gradle.kts`/golden touched. Gate re-run to
confirm the tree 0.4 left is still green (JDK-17, not piped through `tail`):
`./gradlew --no-daemon testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.

**Not done here (out of scope for 0.5, per the plan):** no other ADR in this log was re-audited or
relabeled — only the three blocks the plan names (DS-5, I18N-1, DS-6B). Retroactively relabeling every
past `CLOSED`/`COMPLETE`/`DONE` in this 6,000-line log is a different, much larger task the plan does not
ask for here. 0.6 (measured performance budgets) is untouched and remains open.

## 2026-08-19 — Этап 0.6 complete — performance budgets rewritten on measured numbers

**Status: CODE-GREEN (docs only — zero production code, zero test changes).** Agentic restart plan,
Этап 0.6: replace the one unreachable "hard target" number with the three-kind scheme (invariant /
measured-baseline-with-gate / per-profile) the plan calls for, anchored on a real measurement instead of
an aspiration. The three-tier structure itself was already written into `docs/architecture.md` by Этап 1
(ADR 2/4); the one number that section still owed was heap, never measured in this project's history.

**Measurement — first heap reading this project has ever taken.** SM-A325F (Android 13, API 33),
current `launcher--7` release build (built and signed with the debug keystore for install only — no
release signing key is committed by design), fresh install, launched cold, allowed 8 s to settle at Home,
then `dumpsys meminfo com.sidr.launcher` read 4 times 5 s apart (drop-first protocol, matching the cold-
start measurement precedent). First reading: 56 244 KB PSS. Remaining three: 56 090 / 56 026 / 56 082 KB
— stable within ~0.1%. Baseline recorded as **55 MB PSS steady-state Home**, breaking down as Native Heap
33 MB (alloc ≈18.7 MB of that) + Dalvik Heap 5 MB (alloc ≈6 MB) + Code/`.so`/`.oat`/`.art` mmaps and other
~18 MB; combined Native+Dalvik Heap Alloc ≈ 25 MB. Comfortably under all three of the old, never-verified
80/150/250 MB ceilings — those ceilings were not wrong, they were simply never checked; the tier-3 note in
`architecture.md` now says this explicitly instead of leaving the ceilings dangling as unexplained
strikethrough.

**Docs synced (no code changed):**
- `docs/architecture.md` tier-2 table — "Heap, steady-state Home" row changed from "not yet measured —
  first measurement is Этап 0.6" to the measured value and breakdown above, with the measurement method
  ("4 readings 5s apart at steady-state Home, first dropped").
- `docs/architecture.md` tier-3 ceiling note — rewritten to state the 55 MB baseline sits under all three
  legacy ceilings, replacing the "never measured, so never a gate" phrasing that is now stale.
- `CLAUDE.md` `Known debt` performance bullet — "heap has never been measured" replaced with the measured
  value and date; the `baselineprofile/` / `StartupTimingMetric` gap is kept as still-open (see below).
- `CLAUDE.md` stage table — 0.6 row folded into the "✅ 2026-08-19" row alongside 0.1–0.5/0.7, since Этап 0
  is now fully closed.
- `ai-context/current-status.md` — new Этап 0.6 CLOSED paragraph in the same style as 0.1–0.5; "Next"
  line updated to point at Этап 2 directly (0.6 was the last open item in Этап 0).

**Deliberately not done — optional, named as such in the plan section itself ("при желании закрепить"):**
`MacrobenchmarkRule` + `StartupTimingMetric`/`MemoryUsageMetric` wired into the existing `baselineprofile/`
module (today only `BaselineProfileGenerator.kt`). The mandatory action was "measure once, record as
baseline"; automated regression instrumentation is optional hardening the plan does not gate 0.6's closure
on. Left as open, named debt in both `CLAUDE.md` and `architecture.md`, not silently dropped.

**Does not settle local inference.** Per the plan's own note: the measured 55 MB says nothing about a 1B
model at int4 (700 MB–1 GB resident, a different order entirely) — that question is closed by ADR 2/4's
reasoning (AICore / a small function-calling model on NPU), not by this measurement.

**Verification.** Docs-only change — no `.kt`/`.xml`/`.gradle.kts`/golden touched; `git diff --stat`
contains only `.md` files. `./gradlew --no-daemon testDebugUnitTest assembleDebug` (JDK-17 Temurin
toolchain, not piped through `tail`) — BUILD SUCCESSFUL.

**Этап 0 is now fully closed:** 0.1 ✅ · 0.2 ✅ · 0.3 ✅ · 0.4 ✅ · 0.5 ✅ · 0.6 ✅ · 0.7 ✅. Next up is
Этап 2 (toolchain + `:domain` → KMP).
