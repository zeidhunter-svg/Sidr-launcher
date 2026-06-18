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
