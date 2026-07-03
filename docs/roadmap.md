# Roadmap

## Phase 0: Documentation and decisions

- Create project documentation and AI handoff context.
- Record hybrid AI decision in ADR.
- Define architecture boundaries, security principles, and domain contracts.
- Define performance budgets, permission strategy, and persistence boundaries.

## Phase 1: Compile-ready skeleton

- Create multi-module Gradle project.
- Configure Kotlin, Android, Compose, Hilt, Ktor, Serialization, Coroutines, ONNX, Navigation Compose, DataStore, Room, and WorkManager dependencies.
- Add Android launcher manifest and basic `LauncherActivity`.
- Add domain models, repository interfaces, `OperationResult`, use-case stubs, and DI placeholders.
- Add initial `DeviceProfile`, navigation route contracts, and permission contracts.
- Verify the project builds.

## Phase 2: Launcher shell

- Implement Compose home screen shell with `StateFlow<UiState>`.
- Add single `NavHost` in the `app` module and feature route wiring.
- Display installed apps through a repository abstraction.
- Add text command input.
- Add basic settings and permission education screens.
- Keep home screen, app grid, and app launch fully offline-capable.

## Phase 3: Intent system

- Implement local rule-based intent matcher.
- Define action execution contracts and `OperationResult`-based error handling.
- Add app launch, search, settings, and simple command intents.
- Add confidence thresholds and safe fallback behavior.
- Persist intent match history through repository-backed Room storage where allowed.
- Add table-driven matcher tests.

## Phase 4: Persistence, state, and navigation hardening

- Implement DataStore-backed user preferences, feature flags, device profile cache, and last known suggestions.
- Implement Room-backed usage history, suggestion ranking history, and intent match history.
- Ensure feature modules access persistence only through repositories.
- Harden `UiState`, navigation events, and recoverable error states.

## Phase 5: Cloud AI integration

- Implement Ktor cloud AI client.
- Add streaming adapter using `Flow<AiChunk>`.
- Add prompt/context builder with privacy constraints.
- Add API key storage via `SecureSecretStore` (Keystore AES-256-GCM, BYOK; backend proxy a later
  drop-in behind the same port). OpenAI-compatible adapter with configurable base URL + free-text model.
- Add error, retry, timeout, offline, and static fallback states.

## Phase 6: Local NLU and embeddings

**Status: code-complete (Blocks O → R, closed 2026-06-29); device acceptance + the real model
(OQ#1/#2) pending.** NLU rides the existing `IntentMatcher` port via the rule-first
`LayeredIntentMatcher`; the `TextEmbedder` is a port only (impl deferred to Phase 7).

- Integrate ONNX Runtime Mobile.
- Add intent classifier and embeddings interfaces.
- Use NNAPI opportunistically where available.
- Add model availability and `DeviceProfile` capability checks.
- Add WorkManager model download and verification with battery-aware constraints.

## Phase 7: Voice and contextual suggestions

**Status: user-facing close delivered (Blocks S + T + U + W, 2026-07-01).** Voice = a `SpeechInputSource` input *modality* feeding the existing intent
pipeline (on-device preferred, never a new generative path, never our-backend audio); suggestions = a
third `SuggestionEngine` port (offline time + usage always-on; calendar/location opt-in +
degrade-to-nothing). Surface single-owner = the host `LauncherViewModel`/`LauncherUiState.suggestions`,
now shipping with cache-first paint and background WorkManager pre-compute/cleanup. OQ#3 (embedding model +
host) keeps Block V as a separate model/runtime track; OQ#4 (on-device STT availability) gates Block T device
acceptance. Plan: [ai-context/phase-7-voice-suggestions-plan.md](../ai-context/phase-7-voice-suggestions-plan.md).

- Add `SpeechInputSource` abstraction over Android `SpeechRecognizer`.
- Add no-op/fake speech input implementation for tests and unsupported devices.
- Add context-aware suggestion pipeline.
- Keep suggestions useful without sensitive or unavailable data.
- Add user controls and permission education for calendar, location, audio, and boot warmup.
- Keep semantic re-rank optional; Block V must not block the shipped user surface.

## Phase UX: Home redesign & design system

**Status: PLAN (owner decision 2026-07-02) — MVP-critical, runs next.** The command-first "wall of all
app icons" home is too primitive for a shipping MVP. Declutter the home (no full icon grid), move all
apps into a searchable App Drawer, make Assistant + Settings discoverable via visible icons, fill the
reserved `core/ui` design system, and ship a real Settings surface. No AI paths change; synergistic
with the cold-start fix (smaller first frame). Plan:
[ai-context/phase-ux-plan.md](../ai-context/phase-ux-plan.md).

- Design system in `core/ui` (theme, typography, shape, spacing, core components).
- Minimal home: unified search/command field + suggestions + favorites (top-N used) + entry icons.
- Separate App Drawer for all apps (searchable, ordered, on demand); grid leaves the home surface.
- Unified search ⇄ command field (filters apps live; still submits commands unchanged).
- Real `:feature:settings` (theme, suggestions, voice, favorites, provider link, set-as-default helper).
- Basic a11y hygiene (content descriptions, touch targets), first-run nudge, device pass + cold-start
  re-measure.

> **MVP sequencing note (owner decision 2026-07-02).** The numeric order 8→9 is NOT the shipping
> order. For the MVP the sequence is **Phase UX → Phase 9 (Hardening, the pre-ship gate) → Phase 8
> (Optional, deferred)**. Phase 9 matters more for shipping than Phase 8; the residual cold-start
> perf work folds into Phase 9's "optimize startup" (only if Block X6's re-measure still misses the
> `<400ms` budget). Phase 8 is explicitly optional/advanced and is deferred unless accessibility
> automation becomes a committed product goal.

## Phase 8: Optional advanced automation

**Status: OPTIONAL / DEFERRED (post-MVP).** Not required to ship the MVP; start only if user-consented
automation becomes a product goal.

- Add optional Accessibility Service flow with explicit user-initiated consent.
- Implement only user-approved automation actions.
- Add clear disable path and audit-friendly UX.
- Ensure accessibility denial disables only advanced automation.

## Phase 9: Hardening

**Status: pre-ship gate — runs after Phase UX (MVP-required).** Full plan:
[ai-context/phase-9-plan.md](../ai-context/phase-9-plan.md) (Blocks Y1–Y7). No new features.

- **Y1 Startup performance** — reframed goals (a launcher's resident/warm path dominates): warm/hot start
  ≤ ~200ms + **no first-frame spinner**; cold ~500–800ms on release. `<400ms` is aspirational, **not** a
  ship gate. Measure warm **and** cold on release; move `ensureModel`/WorkManager/PackageManager-enum off
  the first-frame path; cache-first first paint.
- **Y2 Release build** — enable R8 (`isMinifyEnabled`/`isShrinkResources`) + `-keep` for
  Hilt/Room/kotlinx-serialization/ONNX/Ktor; add a **Baseline Profile** (macrobenchmark module).
- **Y3 Contextual suggestions rework** — (A) filter unlaunchable chips at
  `LauncherViewModel.resolveSuggestionLabels` so a chip that can't launch never renders (tiny, do first);
  (B/b2) **rework the hardcoded `TimeOfDaySuggestionProvider` 6-app AOSP table** down to 1–2 genuinely
  universal, **category-resolved** anchors (device's real default clock/camera via system intents), making
  usage/calendar/location the primary sources.
- **Y4** test-coverage hardening (domain, intent, repositories, permissions, offline, device-capability;
  fill VM suggestions/favorites gaps).
- **Y5** privacy, logging, crash-report filtering, error handling.
- **Y6** validate on Android 9/11/13/14 + LOW_END memory profiling.
- **Y7** residual cosmetic findings (keySet race, `retray→retry`, re-entry-resumes-drawer).

Model-gated items (OQ#1–#4: NLU/embedder models, STT matrix) are a **separate track, out of the ship gate**.

## Post-MVP Track: AI Operating Layer over Android

**Status: FUTURE / POST-MVP.** This track starts only after the MVP shipping gate is passed: Phase UX → Phase 9 hardening → release readiness. The goal is not to replace Android itself, but to evolve Sidr Launcher into an AI operating layer on top of Android: a user-facing control surface where text, voice, context, apps, web navigation, and assistant reasoning are routed through one safe intent system.

This track must preserve the existing architecture principles:

* Launcher core remains reliable without AI, network, microphone, or optional permissions.
* Local deterministic routing runs before any cloud or generative AI call.
* User actions are confidence-gated and permission-gated.
* No feature-to-feature dependency edges.
* Domain stays Android-free where possible.
* Accessibility-based automation remains optional and explicitly user-consented.
* Private context is never sent to cloud AI by default.
* Every phase ends with tests, `assembleDebug`, ADR sync, and device acceptance where applicable.

---

## Phase 10: Universal Input / Assistant Fusion

Unify the primary launcher input into one surface:

`Search field = app search + command input + web navigation + assistant entry + voice input`.

The user can type or speak natural requests such as:

* `telegram`
* `open telegram`
* `найди сайт openai`
* `открой сайт spacex`
* `что такое kotlin`
* `скачай telegram`
* `find flights`
* `ask assistant how to change wallpaper`

The launcher routes each input to the safest correct path:

* app filtering;
* app launch;
* existing command pipeline;
* web search;
* direct website opening when confidence is high;
* Assistant screen with prefilled prompt;
* Play Store search;
* clarification or safe fallback.

Implementation direction:

* Add an additive `UniversalInputRouter` / `LauncherInputRouter`.
* Introduce a sealed `InputIntent` model.
* Keep `CommandNormalizer`, `IntentMatcher`, and `HandleUserCommandUseCase` behavior unchanged.
* Voice recognition output must reuse the same routing path as typed input.
* For MVP of this phase, prefer routing Assistant questions to the existing Assistant screen with a prefilled prompt rather than rendering inline AI answers on Home.

Acceptance:

* One input field handles app search, commands, web/site requests, Assistant questions, and voice transcripts.
* Existing typed commands continue to work byte-for-byte.
* Offline app search, app launch, and local commands remain functional.
* Ambiguous or risky inputs produce clarification or safe fallback, not silent execution.

---

## Phase 11: Action Registry & Web/App Intent Router

Create a structured action layer for all executable launcher behaviors.

The goal is to move from scattered action handling toward a clear registry of safe, testable, permission-aware actions.

Core concepts:

* `ActionRegistry`
* `ActionDescriptor`
* `ActionRiskLevel`
* `ActionCapability`
* `ActionPrecondition`
* `PermissionGate`
* `ExecutionPlan`
* `ExecutionResult`

Supported action families:

* app launch;
* app search;
* web search;
* direct website opening;
* Play Store search;
* Android system intents;
* Assistant routing;
* settings routing;
* safe deep links where available.

Web and app routing rules:

* URL-like input opens via `ACTION_VIEW` after safe normalization.
* Ambiguous website requests open browser search instead of guessing.
* “Install/download app” requests open Play Store search.
* Direct website opening is allowed only when confidence is high.
* Suspicious or malformed URLs must never be opened silently.

Acceptance:

* All executable actions are registered and testable.
* Risky actions require confirmation or permission education.
* Web/app routing works without embedding a browser.
* No destructive/system action executes silently.
* Action execution remains compatible with existing `OperationResult` and error handling.

---

## Phase 12: Context Engine v2

Upgrade contextual understanding from simple suggestions into a privacy-preserving context engine.

The Context Engine should produce structured, minimal, permission-aware context snapshots for routing, suggestions, and Assistant handoff.

Possible context sources:

* time of day;
* usage history;
* recent launcher actions;
* current typed prefix;
* voice availability;
* network state;
* battery / thermal / device profile;
* optional calendar signal;
* optional location signal;
* optional notification signal if a future permission path allows it.

Privacy rules:

* Raw calendar titles, raw location coordinates, private messages, and sensitive text must not be stored or sent to cloud AI by default.
* Context should be reduced to safe signals whenever possible.
* Permission denial must degrade only the related feature, never the launcher core.
* Context collection must be explainable and user-controllable.

Acceptance:

* A `ContextSnapshot` or equivalent domain model exists.
* Context consumers do not read Android APIs directly.
* Suggestions, Assistant handoff, and Universal Input can use the same safe context abstraction.
* Privacy guard tests prove sensitive raw data does not leak to persistence, logs, or AI requests.

---

## Phase 13: User Memory & Personalization

Add explicit, user-controlled memory and personalization.

The goal is to let Sidr learn stable user preferences without becoming opaque, invasive, or dependent on cloud AI.

Memory categories:

* launcher preferences;
* favorite apps;
* preferred actions;
* preferred browser/search behavior;
* Assistant provider preferences;
* language preferences;
* safe personalization hints;
* dismissed suggestions;
* confirmed aliases, for example “work chat” → a specific app or action.

Hard rules:

* Memory must be editable and deletable.
* Sensitive memory requires explicit user action.
* No private memory is sent to cloud AI by default.
* Personalization must degrade gracefully when memory is disabled.
* Memory must be separated from transient usage history.

Acceptance:

* User can view, edit, and delete stored preferences.
* Router and suggestions can use memory through domain ports.
* Tests cover memory persistence, deletion, privacy guards, and fallback behavior.
* No hidden long-term profiling is introduced.

---

## Phase 14: Safe Automation Layer

Introduce optional, user-consented automation.

This phase must not be required for MVP and must not block core launcher behavior.

Automation scope:

* simple confirmed actions;
* repeatable user-approved workflows;
* Android intents where officially supported;
* optional Accessibility Service only after explicit education and consent;
* audit-friendly execution history;
* clear disable path.

Automation must not become unrestricted device control.

Safety model:

* Every automation action has a risk level.
* Risky actions require confirmation.
* Accessibility automation is opt-in only.
* Denial disables only automation, not launcher core.
* The user must always understand what will happen before it happens.

Acceptance:

* Automation is behind feature flags and permission gates.
* Accessibility Service is not required for normal launcher usage.
* User can disable automation completely.
* Action history is visible or explainable.
* Tests cover denied permission, revoked permission, failed execution, and safe fallback.

---

## Phase 15: AI OS Shell

Evolve Sidr into a coherent AI operating shell over Android.

This phase combines the previous layers into one user-facing system:

* minimal launcher home;
* universal input;
* Assistant;
* contextual suggestions;
* action registry;
* safe automation;
* user memory;
* privacy controls;
* device capability routing;
* offline-first local behavior;
* cloud AI only when appropriate.

The goal is for the user to interact primarily through intent:

`Say or type what you want → Sidr routes, executes, answers, or asks for clarification.`

This remains an Android launcher and operating layer, not a replacement for Android system internals.

Acceptance:

* The user can perform common phone tasks through one unified input model.
* App, web, Assistant, settings, suggestions, and automation flows feel like one coherent system.
* Offline behavior remains useful.
* AI failures never break launcher functionality.
* Permissions, privacy, and safety remain visible and controllable.
* Device performance remains within the project budgets or features degrade by `DeviceProfile`.

