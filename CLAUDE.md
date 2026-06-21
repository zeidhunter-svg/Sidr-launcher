# CLAUDE.md — Sidr Launcher

Session digest. Read this first, then `ai-context/phase-3-intent-system-plan.md`.

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

## Current goal (active work)

Execute the **reordered Phase 3 checklist** in
[ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md),
Blocks A → D:

- **A ✅ done.** `OperationResult` / `OperationError` moved to `domain`; `domain → core/common`
  edge removed; domain depends only on stdlib + coroutines.
- **B ✅ done.** `:data:repository` created; `InstalledAppsRepository` (PackageManager, offline);
  app grid + command input in `feature/launcher`; `:core:testing` bootstrapped.
- **C ✅ done.** `LauncherIntent` / `ExecutableAction` / `IntentCandidate` contracts;
  `IntentMatcher` port + `IntentMatchResult`; `CommandNormalizer`; `DefaultIntentConfidencePolicy`
  (class, overridable); `RuleBasedIntentMatcher` (`:data:repository`, Android-free);
  `IntentActionResolver` (domain use case); 65 table-driven JVM tests, all green.
- **D — MVP loop (active).** `HandleUserCommandUseCase` → type `open telegram` → app launches.
  Requires: `ActionExecutor` contract (domain) + Android impl (`:data:repository`), DI wiring,
  UI integration in `feature/launcher`.
- **Frozen (Phase 4+):** Room / DataStore, intent-match-history, permission-education module.

MVP done = `open telegram` resolves + launches offline; low-confidence never auto-executes;
`./gradlew assembleDebug` + unit tests pass.

## Status snapshot

- P0 ✅ docs/decisions · P1 ✅ compile-ready skeleton · P2 ⏭ reordered into Block B ✅ ·
  P3 foundation `3.0.1`–`3.1.5` ✅ (result types + navigation).
- Block A ✅ `OperationResult`/`OperationError` in `domain`; `domain → core/common` edge gone.
- Block B ✅ `:data:repository`, `InstalledAppsRepositoryImpl`, app grid, command input,
  `:core:testing` with `FakeInstalledAppsRepository`.
- Block C ✅ full intent domain — contracts, normalizer, rule-based matcher, resolver, 65 tests.
- **Block D — in progress.** Next: `ActionExecutor`, `HandleUserCommandUseCase`, DI, UI wiring.

## Hard rules

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`.
- Interfaces in `domain`; implementations in `data/*`. UI holds no business logic.
- No `feature -> feature` deps. Single `NavHost` in `app`. ViewModels emit
  `NavigationEvent`; they never touch `NavHostController`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- `IntentMatcher` (→ `IntentMatchResult`) is a **different port** from `GenerativeAiEngine`
  (→ `Flow<AiChunk>`). Matching ≠ generation.
- Launcher core works fully offline; optional permissions never block startup.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` *(Block D)* | `domain` |
| `DeviceCapability` model + AI routing policy | `domain` |
| Rule-based matcher impl, `InstalledAppsRepository` impl, Android `ActionExecutor` impl | `data/repository` |
| Room / DataStore *(Phase 4)* | `data/repository` |
| Cloud AI client (Ktor) | `data/ai-cloud` |
| ONNX NLU / embeddings | `data/ai-local` |
| `UiState`, dispatchers, logging contracts | `core/common` |
| `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| `DeviceProfile` detection, `PackageManager` access, `SpeechInputSource` Android impl | `core/android` |
| Design system / theme | `core/ui` |
| Test fakes / fixtures | `core/testing` |
| Single `NavHost`, composition root, Hilt graph | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |

## Source of truth

- Architecture & target module structure: [docs/architecture.md](docs/architecture.md)
- Active checklist: [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md)
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Roadmap: [docs/roadmap.md](docs/roadmap.md)

## Do not

- Don't add Room / DataStore / permission-education in this slice (frozen → Phase 4).
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
