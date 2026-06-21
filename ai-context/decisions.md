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

### ADR 2026-06-19 — Phase 2 skipped / reordered into a minimal slice
- Decision: Phase 2 (launcher shell) is **not** run as a separate phase. Its navigation half was already absorbed into `3.1.x`; its product floor — `InstalledAppsRepository`, app grid, command input, offline app launch — is folded into Phase 3 as a **minimal P2 slice** (Block B).
- Context: Phase 3's intent system cannot reach acceptance without Phase 2's installed-apps repository and command input (e.g. `open telegram` cannot resolve or launch). The skip deferred an unavoidable dependency rather than removing it.
- Rationale: the launcher core (home + app grid + app launch) is the product floor; the intent pipeline is meaningless without it. Building the minimal slice now unblocks `3.4.8`/`3.4.11`/`3.4.13`.
- Consequence: full launcher-shell polish stays deferred; Room/DataStore persistence and intent-match-history are frozen to Phase 4; `feature/settings` and `feature/permission_education` remain inline placeholders until created.
- Active plan: `ai-context/phase-3-intent-system-plan.md` (Blocks A → D). Session summary in root `CLAUDE.md`.
