# CLAUDE.md — Sidr Launcher

Session digest. Read this first, then `ai-context/phase-4-plan.md` (active: Block G).

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

## Current goal (active work)

**Phase 3 is DONE (Blocks A → D, 2026-06-21).** The MVP loop works: type `open telegram` →
resolves + launches offline; tap a grid app → launches; unknown → fallback UI, no crash.
**Live launch verified on device (SM-A325F, Android 13).** `assembleDebug` +
`testDebugUnitTest` green. Details in
[ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md) and the
Block D ADR in [ai-context/decisions.md](ai-context/decisions.md).

**Phase 4 — persistence, state & navigation hardening** (Blocks E → H, see
[ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)). **Block E DONE (2026-06-22):** DataStore
Preferences foundation — `UserPreferences`/`FeatureFlags`/`DeviceProfileCacheEntry`/`CachedSuggestion`
+ 4 repos (`Flow` read / `OperationResult` write), impls over one `DataStore<Preferences>`,
`PersistenceProvidesModule`+`PersistenceBindsModule` in `:app`, 4 fakes, 14 JVM tests green
(round-trip survives simulated restart). Privacy inventory enforced by a key-name guard test.
**Block F DONE (2026-06-22):** Room persistence — 3 history domain models + repo interfaces
(`AppUsageRecord`/`SuggestionRankingRecord`/`IntentMatchRecord` + `IntentMatchType`) in `:domain`;
Room entities, DAOs, `SidrDatabase` (v1, `exportSchema=true`), `TypeConverters`, mappers with
**SEARCH/UNKNOWN redaction** (query content never stored), 3 `*RepositoryImpl` with retention-caps
(200/100/200), `DatabaseModule`+`HistoryBindsModule` in `:app`; intent-match write live in
`HandleUserCommandUseCase`; grid sorts by usage recency/frequency; 2 fakes in `:core:testing`;
17 new JVM tests (Robolectric DAO/repo + redaction + column-privacy guard) green. Golden schema
`schemas/1.json` committed; `MigrationTestHelper` runway in `androidTest`.
`assembleDebug` + `testDebugUnitTest` green. **Next: Block G — permission-education.** Details in
[ai-context/decisions.md](ai-context/decisions.md).

Phase 3 result, Blocks A → D:

- **A ✅** `OperationResult` / `OperationError` in `domain`; `domain → core/common` edge removed.
- **B ✅** `:data:repository`; `InstalledAppsRepository` (PackageManager, offline); app grid +
  command input in `feature/launcher`; `:core:testing` bootstrapped.
- **C ✅** `LauncherIntent` / `ExecutableAction` / `IntentCandidate`; `IntentMatcher` +
  `IntentMatchResult`; `CommandNormalizer`; `DefaultIntentConfidencePolicy`; `RuleBasedIntentMatcher`
  (`:data:repository`, Android-free); `IntentActionResolver`; 65 JVM tests.
- **D ✅** `ActionExecutor` port + truncated `ActionExecutionResult` + `CommandOutcome` (12
  variants) + `HandleUserCommandUseCase` (domain); `AndroidActionExecutor` (`:data:repository`);
  VM wiring + `CommandFeedback` fallback UI (`feature/launcher`); DI via `IntentBindsModule` +
  `IntentProvidesModule` (`:app`). Routing: low/medium never auto-executes; navigation + CLEAR +
  SHOW_APPS + ambiguity never go through the executor.

**Still frozen until their Phase-4 slice:** permission-education (G), hardening (H).
DataStore unfrozen in Block E; Room unfrozen in Block F.

## Status snapshot

- P0 ✅ docs/decisions · P1 ✅ compile-ready skeleton · P2 ⏭ reordered into Block B ✅ ·
  P3 foundation `3.0.1`–`3.1.5` ✅ (result types + navigation).
- Block A ✅ `OperationResult`/`OperationError` in `domain`; `domain → core/common` edge gone.
- Block B ✅ `:data:repository`, `InstalledAppsRepositoryImpl`, app grid, command input,
  `:core:testing` with `FakeInstalledAppsRepository`.
- Block C ✅ full intent domain — contracts, normalizer, rule-based matcher, resolver, 65 tests.
- Block D ✅ MVP loop — `ActionExecutor` + `CommandOutcome` + `HandleUserCommandUseCase`,
  `AndroidActionExecutor`, VM wiring + `CommandFeedback`, DI split modules. Live-verified on device.
- **Phase 3 CLOSED (2026-06-21).**
- **Phase 4 Block E ✅ (2026-06-22)** — DataStore Preferences: domain models + 4 repo interfaces,
  impls + mappers (`@Serializable` DTO stays in `:data:repository`), DI split modules, fakes, 14
  JVM tests. Privacy key-name guard green.
- **Phase 4 Block F ✅ (2026-06-22, fixes 2026-06-23)** — Room: 3 history domain models + interfaces
  in `:domain`; entities + DAOs + `SidrDatabase` (v1) + mappers with SEARCH/UNKNOWN redaction + 3
  `*RepositoryImpl` (retention 200/100/200) in `:data:repository`; `DatabaseModule`+`HistoryBindsModule`
  in `:app`; intent-match write live in use case; usage-sorted grid; 2 fakes + 17 new JVM tests
  (Robolectric + column-privacy guard) green; `MigrationTestHelper` v1 baseline verified on SM-A325F
  (Android 13); both history writes gated behind `FeatureFlags.usageHistoryEnabled` (4 flag-gate tests).
  Follow-up (2026-06-23): `recordMatch` moved off critical path via `recordingScope.launch {}` (injected
  `@ApplicationScope CoroutineScope`, required param, no lifecycle-less default); `CancellationException`
  re-thrown in `LauncherViewModel.recordUsage`; 2 more tests; 67 domain + 25 launcher JVM, 0 failures.
  **Active next: Block G (permission-education).**

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
| Pref domain models (`UserPreferences`, `FeatureFlags`, `DeviceProfileCacheEntry`, `CachedSuggestion`) + their repo interfaces *(Block E ✅)* | `domain` |
| DataStore Preferences impls + `PreferencesMapper` + `PreferencesKeys` *(Block E ✅)* | `data/repository` |
| History domain models (`AppUsageRecord`, `SuggestionRankingRecord`, `IntentMatchRecord`) + repo interfaces (`UsageHistoryRepository`, `SuggestionRankingRepository`, `IntentMatchHistoryRepository`) *(Block F)* | `domain` |
| Room entities, DAOs, `SidrDatabase`, `TypeConverters`, `migrations/`, mappers *(Block F)* | `data/repository` |
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
  **OUT OF SYNC** — reflects pre-Phase-4 layout; `EncryptedSharedPreferences` still mentioned
  (deprecated, Fork 1 defers secrets to Phase 5). Sync scheduled: Block H, step H6. Until then
  the authoritative sources are: this file + [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)
  + [ai-context/decisions.md](ai-context/decisions.md).
- Active checklist: [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md) *(Block G — active)*
- Closed checklist: [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md)
  *(Phase 3 closed 2026-06-21)*
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Roadmap: [docs/roadmap.md](docs/roadmap.md)

## Do not

- Don't start Room / permission-education / hardening ahead of their block (F/G/H). DataStore
  is done (Block E); extend it, don't re-scaffold it.
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
