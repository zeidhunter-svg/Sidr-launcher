# CLAUDE.md — Sidr Launcher

Session digest. Read this first. **Phase 4 is DONE (Blocks E → H, 2026-06-23).** Next per
[docs/roadmap.md](docs/roadmap.md) = **Phase 5 (cloud AI)** — not yet started, needs its own plan.

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
**Block G DONE (2026-06-23):** permission-education — new `:feature:permission_education` module
(Compose + Hilt) replaces the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus` +
`PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker`
in `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (key
`perm_dismissed_wallpaper`); education ≠ request (Fork 5); live `SET_WALLPAPER` trigger from a
launcher "Wallpaper" button; denial disables only that feature, core never blocked.
**`BIND_ACCESSIBILITY_SERVICE` not requested/educated (Ph8).** 2 fakes + 12 new JVM tests green.
**Block H DONE (2026-06-23):** hardening + docs-sync — `UiState.Error(retryable)` + `retry()`
(H2, no process restart); `commandInput` via `SavedStateHandle` (H3 process-death restore);
`refreshStatus()` made upgrade-only (H-b, never downgrades `PERMANENTLY_DENIED`); privacy guard
extended to Room **table names** (H-a); temporary home-screen "Wallpaper" button removed (H4);
nav safe-fallback + kill→reopen verified on device (H5); `docs/architecture.md` synced to the real
3-flow `LauncherViewModel` design + Forks 1/2/8/9 (H6). Content-restore half of Fork 6 deferred to
Ph7 (no Ph4 display surface). `assembleDebug` + `testDebugUnitTest --rerun-tasks` green.
**Phase 4 closed; next = Phase 5 (cloud AI).** Details in
[ai-context/decisions.md](ai-context/decisions.md) ("ADR Block H").

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

**All Phase-4 slices delivered:** DataStore (E), Room (F), permission-education (G), hardening (H).
**Frozen to Phase 5+:** secrets (Ph5), cloud AI (Ph5), ONNX (Ph6), voice + context-suggestions
(Ph7), accessibility (Ph8), WorkManager (Ph6/9), full Hilt→KSP migration (Ph9).

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
- **Phase 4 Block G ✅ (2026-06-23)** — permission-education: new `:feature:permission_education`
  module (Compose + Hilt) replacing the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus`
  + `PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker` in
  `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (`perm_dismissed_wallpaper`);
  education ≠ request (Fork 5) with a live `SET_WALLPAPER` trigger from a launcher "Wallpaper" button;
  denial disables only that feature, core never blocked; accessibility deferred (Ph8). 2 fakes + 12 JVM
  tests green.
- **Phase 4 Block H ✅ (2026-06-23)** — hardening + docs-sync (Fork 6 + Fork 9): `UiState.Error` gained
  `retryable: Boolean = false` + `LauncherViewModel.retry()` (no process restart, retry action not in the
  data class); `commandInput` backed by `SavedStateHandle` (process-death restore); `refreshStatus()`
  upgrade-only (never downgrades `PERMANENTLY_DENIED`, partial fix — revisit Ph7/`RECORD_AUDIO`); privacy
  guard extended to Room **table names** (hand-synced `TABLE_NAMES`); temporary home-screen wallpaper
  button removed (entry returns with `feature/settings`); nav safe-fallback latent (no bad route in Ph4)
  + kill→reopen verified on device (PID change, input restored, no crash); content-restore half of Fork 6
  deferred to Ph7 (no display surface yet). `docs/architecture.md` synced to the real 3-flow
  `LauncherViewModel`. `assembleDebug` + 105 JVM tests green. **Phase 4 CLOSED (Blocks E → H). Next =
  Phase 5 (cloud AI).**

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
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) *(Block G)* | `domain` |
| `AndroidPermissionChecker` impl *(Block G)* | `core/android` |
| `PermissionPrefsRepositoryImpl` (DataStore) *(Block G)* | `data/repository` |
| Permission-education UI (`PermissionEducationScreen`/`ViewModel`, rationale, request flow) *(Block G)* | `feature/permission_education` |
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
  **IN SYNC** as of Block H6 (2026-06-23) — real 3-flow `LauncherViewModel`, `UiState.Error(retryable)`,
  per-feature permission education + upgrade-only `refreshStatus()`, Forks 1/2/8/9 reflected.
  `EncryptedSharedPreferences` documented as deprecated/not used (Fork 1 defers secrets to Phase 5).
- Closed checklists: [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md) *(Phase 4 closed,
  Blocks E → H, 2026-06-23)* · [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md)
  *(Phase 3 closed 2026-06-21)*
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Roadmap: [docs/roadmap.md](docs/roadmap.md) *(Phase 5 = cloud AI, next — not yet planned)*

## Do not

- Don't start Phase 5 (cloud AI) ahead of its own approved plan. Phase 4 (E → H) is closed;
  extend the existing persistence/hardening, don't re-scaffold it.
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets land in Phase 5 via a
  `SecureSecretStore` port (Fork 1).
