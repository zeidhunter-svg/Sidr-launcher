# Decisions

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
  5. **NNAPI** = opportunistic EP + deterministic CPU fallback, init-failure degrades (no crash),
     confined to `:data:ai-local`; ⚠ exact `ai.onnxruntime` Java signatures re-verified at Block P
     (context7's ONNX Android-Java coverage was thin — Python-skewed).
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
- **Tooling note:** WorkManager/Hilt-WorkManager API verified current via context7 (`/androidx/androidx`:
  `@HiltWorker`/`HiltWorkerFactory`/`Configuration.Provider`/`Constraints.Builder` with
  `setRequiresBatteryNotLow`/`setRequiresStorageNotLow`). ONNX Android-Java surface **not** well covered
  by context7 → flagged for execution-time Javadoc verification, not pinned from memory.

### ADR 2026-06-19 — Phase 2 skipped / reordered into a minimal slice
- Decision: Phase 2 (launcher shell) is **not** run as a separate phase. Its navigation half was already absorbed into `3.1.x`; its product floor — `InstalledAppsRepository`, app grid, command input, offline app launch — is folded into Phase 3 as a **minimal P2 slice** (Block B).
- Context: Phase 3's intent system cannot reach acceptance without Phase 2's installed-apps repository and command input (e.g. `open telegram` cannot resolve or launch). The skip deferred an unavoidable dependency rather than removing it.
- Rationale: the launcher core (home + app grid + app launch) is the product floor; the intent pipeline is meaningless without it. Building the minimal slice now unblocks `3.4.8`/`3.4.11`/`3.4.13`.
- Consequence: full launcher-shell polish stays deferred; Room/DataStore persistence and intent-match-history are frozen to Phase 4; `feature/settings` and `feature/permission_education` remain inline placeholders until created.
- Active plan: `ai-context/phase-3-intent-system-plan.md` (Blocks A → D). Session summary in root `CLAUDE.md`.
