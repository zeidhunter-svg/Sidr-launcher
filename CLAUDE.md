# CLAUDE.md — Sidr Launcher

> **Read-first for every session.** This file is **current state + rules + pointers only**.
> History does not live here — it lives in `ai-context/decisions.md` (ADR log), one ADR per closed
> block. Compressed 2026-08-19 by Этап 0.4 of the agentic track (101 KB → ~15 KB); the
> pre-compression text is preserved verbatim in git at `9de23ab:CLAUDE.md` and every fact it carried
> is in an ADR (verified block-by-block before deletion).

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

Vision (owner reframe 2026-07-05, amended by ADR 3/4): **Stage 1 — AI Launcher** (closed) →
**portable agent core**, two consumers, Android + PC (Stage 2 as a *schedule* stage is abolished;
layers grow inside vertical slices) → **Agentic OS** (consented automation + the AI shell).

## Current goal (active work)

**The AGENTIC TRACK is the active track.** Governing document:
[docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md)
(owner-approved 2026-08-18). Session kickoff template + closing checklist are its §0; the closing
checklist is binding for every stage. `§HANDOFF` at the end of that file is written by the closing
session for the next one — read it.

Since Этап 3 the track also has a **Master Plan** — block sequence A0…A6, DoD, change-control,
milestones, and the `Agentic Shell v1 = DONE` definition:
[docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md).
Different layer, not a replacement: the restart plan keeps the owner's four decisions, the §0 session
protocol and `§HANDOFF`; the Master Plan governs how blocks are run from Этап 4 on. The eight Islamic
principles' **checkable** part now lives in
[docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md) as 28
`DOC-*` rules — cite a rule by ID, never paraphrase it; a rule's third column is a real test name or
an honest `<нет>`, and `DoctrineMatrixGuardTest` fails the build on a false claim.

**Owner decision 2026-08-21 — two consumers from the start.** ADR 3/4's "portable core, two consumers"
was backed only by a build flag: `:domain` builds for `androidTarget()` + `jvm()`, but `jvm()` has zero
consumers, the module has zero `expect`/`actual`, and `commonMain` carries eight `java.*` imports. The
owner resolved the fork in favour of a real second consumer. Consequences already in force: criterion 10
in the Master Plan's readiness definition; a new block **A0.5** between A0 and A1′
([brief](docs/superpowers/plans/2026-08-21-a05-second-consumer.md)); and fork **F6** in the A0 spec —
tools return values and steps bind to them, landing **before** the Room 3→4 migration. Full record: ADR
"2026-08-21 — Развилка агентного трека" in `decisions.md`. Everything else the revision found is parked
in `§HANDOFF`, not built.

| Stage | State |
|---|---|
| **1** — strategic ADR package | ✅ 2026-08-19 — four ADRs, docs only |
| **0.1** owner sign-off (release gate cleared — `:app:assembleRelease` green for the first time) · **0.2** FastPath `ru`/`tr` · **0.3** delete ONNX (APK 78 MB → 6.8 MB) · **0.4** compress this file · **0.5** honest statuses (`CODE-GREEN`/`DEVICE-ACCEPTED`/`CLOSED`) · **0.6** budgets rewritten on measured numbers (heap 55 MB PSS) · **0.7** I18N residue | ✅ 2026-08-19 |
| **2** toolchain (AGP 9.3.1/Kotlin 2.4.10/Gradle 9.5.0/compileSdk 37) + `:domain` → KMP | ✅ 2026-08-19 |
| **3** agentic Master Plan + doctrinal matrix (`docs/governing/`) | ✅ 2026-08-19 — docs + one guard test |
| **4.0** — invert the understanding flag (`llmRouterEnabled` → `localOnlyMode`, ADR 1/4) | ✅ 2026-08-20 — `CODE-GREEN`; first behavioural change of the track |
| **4** — A0 thin agentic spike | 🔄 **in flight** — work-order items 1–3 done (VM split; `domain/tool`+`agent`+`trace`+executor; planner + use cases). Items 4–9 open, **plus new item 2b (F6, step-to-step data flow) which must land before the Room 3→4 migration** |
| **4.5–7** — A0.5 second consumer, A1′ ToolRegistry, A4′ runtime, A2/A3/A5/A6 | each needs its own spec + plan (`brainstorm → spec → plan → build`) |

The four strategic ADRs (all 2026-08-19, in `decisions.md`):

1. **Deterministic-first redefined** — understanding belongs to the model, execution to the
   deterministic layer (see Hard rules). `FeatureFlags.llmRouterEnabled` was **inverted onto the new
   key** `localOnlyMode` / `flag_local_only`, default `false` (a plain default flip would have been
   inert — DS-11 `autoHideNavBar` precedent). **Implemented 2026-08-20 by Этап 4.0**, `CODE-GREEN`;
   the old key is orphaned and there is no migration.
2. **Platform re-baseline 2026** — ONNX NLU closed, OQ#1/#2/#3 closed; local-inference runtime is
   **LiteRT / LiteRT-LM** (alternative on record: ExecuTorch; separate path: AICore); `AppFunctions` /
   `MCP` are first-class tool sources; performance budgets become three tiers.
3. **Portable core boundary** — "Framework" = a portable agent core, two consumers (Android + PC);
   `:domain` → KMP in Этап 2.2; **`ActionIds`' seven values are frozen byte-for-byte** (`launch_app`
   is a Room PK in `resolution_preferences`; all seven are the outbound wire contract to the LLM).
4. **Assistant ⊕ Agent** — one conversational loop, two surfaces: one `AgentSession`/`Planner`
   contract; a 0-step plan *is* a spoken reply, an N-step plan is a task.

**Deliberately undecided:** the A1 fork (parallel tool vocabulary vs. evolve `ActionCatalog` in
place) belongs to Этап 5, on a spec rewritten after ADR 2/4. Do not pre-empt it.

## Shipped surface (2026-08-20)

Everything below is on `launcher--7` and `CODE-GREEN` (gate green: `testDebugUnitTest assembleDebug`,
plus `verifyRoborazziDebug` where `core/ui` is touched). Most of it is also `DEVICE-ACCEPTED` on
SM-A325F / Android 13 — the owner personally ran on-device verification and signed off — **except**
FastPath's `ru`/`tr` locale forms (0.2), Action & Safety (DS-5), the i18n surface (I18N-1), and the
flag inversion (Этап 4.0), which are `CODE-GREEN` only; see Known debt. `CLOSED` = both, with any
residual limitation named rather than implied absent (Этап 0.5 — status vocabulary).

- **Launcher core** — home, app drawer, settings, app launch; fully offline.
- **FastPath routing** — `RuleBasedIntentMatcher`, verb/keyword vocabulary in `en`/`ru`/`tr`
  (`FastPathLocaleGuardTest`). Since Этап 0.3 the unqualified `IntentMatcher` binds it **directly**.
- **BYOK cloud AI** — Assistant (SSE streaming, OpenAI-compatible, key in Keystore) + the LLM action
  router (`CommandPlanner` → risk-gated confirm card → `ExecuteActionUseCase`). Since Этап 4.0 a
  FastPath miss reaches the planner **by default**, gated only by `FeatureFlags.localOnlyMode`
  (default off), a configured provider and connectivity; each blocked state says which one it is
  instead of answering "Unknown command".
- **Memory** — learned resolutions (auto-resolve at streak ≥ 3) + explicit aliases; on-device,
  correctable from Settings.
- **Suggestions** — time-of-day / usage (offline) + calendar / location (permission-gated),
  heuristically ranked, precomputed by WorkManager.
- **Prayer** — offline `adhan2`, bundled GeoNames city index, one-shot device location rounded to 2dp
  before crossing the port boundary; coordinates never persisted, never leave the device.
- **Design system v1.1** — grey tokens, `core/ui` primitives + controls, Roborazzi goldens.
- **i18n** — `en`/`ru`/`tr` on every migrated screen through one `sidrString` seam; per-app language
  switch; four guard tests + an owner-sign-off release gate.
- **PREVIEW surfaces** — Tasks / Agents / Activity / Terminal: non-functional badged mock-ups, zero
  fabricated data (owner decision 2026-08-18: they stay).

## Known debt (honest list)

Not `CLOSED`. Status vocabulary (Этап 0.5): `CODE-GREEN` (gate green, no device claim) /
`DEVICE-ACCEPTED` (owner ran on-device verification and signed off — an agent-driven `adb`/`uiautomator`
pass does not count) / `CLOSED` (both, plus any residual limitation named, not implied absent). Each item
below is recorded in its own ADR.

- **`CODE-GREEN`, not `DEVICE-ACCEPTED`:** Этап 4.0's flag inversion has not run on the SM-A325F —
  its device check (`ru-RU`, no provider configured ⇒ the honest message, not `Unknown command`) is
  outstanding, and it is the first change of the track a user would actually *feel*.
  DS-5's own acceptance checklist has never been run (since 2026-07-13); I18N-1 was verified only by agent-driven `adb`/`uiautomator` — its offline path, live
  TalkBack, fontScale 2.0, and the system per-app-language picker are untested. DS-6B is `CLOSED` (owner
  ran full on-device acceptance 2026-08-08) but carries one named residual: its MWL times are
  cross-implementation-verified only (Istanbul/Makkah are authority-table-anchored) — `CLOSED` is not a
  zero-debt claim.
- **Performance:** cold start 766 ms (`< 400 ms` was never met — aspirational, not a ship gate);
  heap 55 MB PSS steady-state Home (SM-A325F, Android 13, release, measured 2026-08-19, Этап 0.6 —
  first-ever measurement, comfortably under the old unverified 80/150/250 MB ceilings); `baselineprofile/`
  still has no `StartupTimingMetric`/`MemoryUsageMetric` — optional hardening, not done.
- **Release gate:** closed by Этап 4.0 — `checkOwnerReviewedLocaleStrings` now checks a *signature
  over content* (`OWNER-REVIEWED <date> sha256:<16 hex>` covering that file's Class B keys), so an
  edited or added key invalidates the signature. All 10 locale files carry a digest.
- **Turkish morphology:** noun-case suffixes (accusative `-ı`) are not stripped from the extracted
  app name, so `telegramı aç` may not exact-match an installed label (Этап 0.2, documented).
- **Untested matrices:** Android 9 / 11 / 14, real LOW_END hardware, on-device STT states
  (`Ready`/`Partial`, OQ#4), boot warmup after a physical reboot.

## Hard rules

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`. It is now
  `kotlin.multiplatform` (`android` + `jvm` targets, Этап 2.2, ADR 3/4) — production code lives in
  `commonMain`, so introduce nothing JVM- or Android-specific there; it would fail to compile for the
  other target. Tests live in `jvmTest` (JUnit4 isn't `commonTest`-portable) — see `core:testing`.
- Interfaces in `domain`; implementations in `data/*`. UI holds no business logic.
- No `feature -> feature` deps. Single `NavHost` in `app`. ViewModels emit
  `NavigationEvent`; they never touch `NavHostController`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- **Understanding belongs to the model. Execution belongs to the deterministic layer.**
  *(ADR "2026-08-19 — ADR 1/4 (agentic restart)"; replaces the former "fast local intent matching runs
  before any LLM call".)*
  1. **FastPath** (deterministic, localized) answers frequent exact commands without a model. It is a
     **latency optimization, NOT a filter on understanding**.
  2. The **learned-plan cache** replays already-understood goal shapes deterministically and offline.
  3. Everything else goes to the **model planner**. A FastPath miss is **no longer** grounds to answer
     "Unknown command".
  4. Nothing the model proposes executes, gains rights, or leaves the device except through
     deterministic gates: `ToolRegistry` → argument validation → preconditions → risk gate / consent
     → loop bounds → egress allow-list → trace.
  5. `localOnlyMode` / no provider / offline ⇒ FastPath + plan cache + an honest statement of which
     of the three it is — never "Unknown command", which blames the command for the system's state.
     Parity stays test-checkable and means: the planner is not consulted, nothing leaves the device,
     and every outcome FastPath **decided** is returned byte-for-byte (`DOC-ADL-3`, amended 2026-08-20).
- **Understanding vs. execution, not matching vs. generation.** One contour may both speak and act
  (ADR 4/4 — one `AgentSession`, a 0-step plan *is* a spoken reply); what may never merge is
  **proposing** and **executing**. `GenerativeAiEngine` (→ `Flow<AiChunk>`, transport) stays a
  different port from `IntentMatcher` (→ `IntentMatchResult`) and from the structured `Planner` /
  `CommandPlanner` — those are different *shapes of answer*, and that separation is unaffected.
  LLM-proposed actions **never auto-execute a risky action** (confirmation-gated, Fork R4).
- **Boundaries are laid on the first slice, for two tools — not "when needed".** Registry as the only
  path to the world, argument validation, consent gate, loop limits, trace, egress allow-list,
  rollback. Functionality scales with need; boundaries do not (plan §«Правило роста»).
- Launcher core works fully offline; optional permissions never block startup.
- **User-facing text never originates in `domain` — and not in a ViewModel either.** Domain and
  ViewModels emit typed results (`CommandMessage`, `CommandFailure`, `CommandFeedback`); the feature
  layer chooses the string via `sidrString(R.string.…)`. Enforced by `HardcodedUiTextGuardTest` and
  `StringSeamGuardTest`.
- **Strings and all main-locale translations ship in the same commit as the feature.** A block is not
  gate-green until `en`/`ru`/`tr` are complete — enforced by `LocaleCompletenessGuardTest`.
- Outbound content is a **positive allow-list** (`OutboundContextPolicy`), widened only by an ADR and
  only with a guard test proving a planted sentinel never leaves the device.

## Build & verification gate

- **JDK 17.** The machine's default JDK is newer and Gradle cannot parse it; run with the Temurin 17
  toolchain (`-Porg.gradle.java.installations.paths`, `local.properties` is git-ignored).
- Gate: `./gradlew --no-daemon testDebugUnitTest assembleDebug` — plus
  `:core:ui:verifyRoborazziDebug` whenever `core/ui` is touched, and `:app:assembleRelease` for
  release-affecting work.
- **Never pipe `gradlew` through `tail`** — that masked a red gate as exit 0 on 2026-07-13. Check the
  exit code and read the real output.
- A stage/block is not closed until the gate is green, an ADR is written, `CLAUDE.md` +
  `ai-context/current-status.md` are synced, and a commit is **proposed to the owner**. The agent
  commits; the agent never pushes.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` | `domain` |
| `ActionId`/`ActionIds` (**frozen**), `LauncherAction`, `ActionDescriptor`, `ActionCatalog` | `domain` |
| `CommandPlanner` + `PlanResult`/`ActionProposal`/`ProposalValidator`/`CatalogSchemaRenderer`/`RouteCommandUseCase` | `domain` |
| `ExecuteActionUseCase` (confirmed `LauncherAction` → resolve → execute → `CommandOutcome`) | `domain` |
| `PromptContextBuilder` + `OutboundContextPolicy` (outbound allow-list/guards) | `domain` |
| Pref models (`UserPreferences`, `FeatureFlags`, `CachedSuggestion`) + history models + their repos | `domain` |
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) | `domain` |
| `DeviceProfile`/`DeviceCapability` + `DeviceProfileProvider` port | `domain` |
| Suggestion + voice contracts; prayer domain (`PrayerContext`, `GetPrayerContextUseCase`) | `domain` |
| `RuleBasedIntentMatcher`, `InstalledAppsRepository` impl, `AndroidActionExecutor` | `data/repository` |
| DataStore impls + `PreferencesMapper`/`PreferencesKeys`; Room entities/DAOs/`SidrDatabase`/migrations/mappers | `data/repository` |
| Suggestion providers + `SuggestionEngineImpl`; `SecureSecretStore` impl + `KeystoreSecretCipher` | `data/repository` |
| Cloud AI client (Ktor SSE engine, `LlmCommandPlanner`) | `data/ai-cloud` |
| Offline prayer calculation (`AdhanPrayerCalculator`), city index, schedule cache DTOs | `data/prayer` |
| `AndroidPermissionChecker`, `AndroidDeviceProfiler` + `DeviceProfileClassifier`, `AndroidSpeechInputSource`, `PackageManager` access | `core/android` |
| `UiState`, dispatchers, logging contracts; `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| Design system, theme, primitives/controls, `sidrString`/`SidrStringOverlay`, Roborazzi goldens | `core/ui` |
| Test fakes / fixtures | `core/testing` |
| Feature UI + ViewModels + feature-local presentation mappers | `feature/*` |
| Single `NavHost`, composition root, Hilt graph, DI modules, i18n guard tests, WorkManager wiring | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |

## Do not

- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets go through the
  `SecureSecretStore` port (Keystore-backed).
- Don't resurrect the deleted ONNX stack (`LayeredIntentMatcher`, `LocalInferenceGate`, `ModelStore`,
  `TextEmbedder`, `:data:ai-local`). A future local runtime is a **new design** on LiteRT/LiteRT-LM,
  not a restoration (ADR 2/4).
- Don't widen `ActionIds` or change its seven string values (ADR 3/4).
- Don't add autonomy without consent, and don't route around the risk/permission gates.
- Don't put history back into this file.

## Source of truth

| What | Where |
|---|---|
| Architecture, module structure, performance tiers | [docs/architecture.md](docs/architecture.md) |
| ADR log — **all history, one ADR per closed block** | [ai-context/decisions.md](ai-context/decisions.md) |
| Recent status snapshot | [ai-context/current-status.md](ai-context/current-status.md) |
| Active track plan (stages, §0 protocol, §HANDOFF) | [docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |
| Agentic target architecture A1–A6 | [docs/agentic-os-architecture.md](docs/agentic-os-architecture.md) |
| Doctrine — rules `DOC-*`, verification types, test per rule | [docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md) |
| Agentic track Master Plan — blocks A0…A6, DoD, change-control | [docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md) |
| Design system rulebook | [docs/governing/sidr-design-system-master-plan-v1.2.md](docs/governing/sidr-design-system-master-plan-v1.2.md) |
| Specs, task plans, closed phase checklists | [docs/superpowers/specs/](docs/superpowers/specs/) · [docs/superpowers/plans/](docs/superpowers/plans/) · `ai-context/phase-{3,4,5,6,7}-*.md` |

### History map — which ADR to open in `decisions.md`

- Phases 3–4 (foundation): Blocks **A → H** · Phase 5 (cloud AI): **I → N** + "Phase 5 close summary"
- Phase 6 (local NLU, stack since deleted): **O → R** + "OQ#1 amended" · Phase 7 (voice + suggestions): **S → W**
- UX + hardening: **X1 → X6**, **Y1 → Y7**, device-acceptance rounds 1–3
- Stage-1 AI Launcher: **AIL-0 → AIL-6** · Stage-2 memory: **S2-1**, **S2-2**
- Design track: **DS-0 → DS-11** + Vision MVP preview · Localization: **I18N-1**, **I18N-2**
- Agentic restart: **ADR 1/4 … 4/4**, **Этап 0.2 / 0.3 / 0.4 / 0.5 / 0.6 / 0.7 / 2 / 3** (2026-08-19) · **Этап 4.0** (2026-08-20)
