# Architecture

## Goal

Sidr Launcher is an AI-first Android launcher for Android 9+ (API 28+) that lets users start actions through text, voice, and contextual suggestions.

## Principles

- AI-first UX, but Android launcher behavior must remain reliable without AI.
- Launcher core features, including home screen, app grid, and app launch, must work fully offline.
- No AI feature should block launcher startup.
- Fast local intent matching runs before any LLM call.
- Cloud AI is the default generative path; local AI is optional and capability-gated.
- Local ONNX Runtime Mobile is used for NLU, intent classification, and embeddings, not full LLM generation.
- AI features degrade gracefully without connectivity.
- Streaming responses use one unified contract: `Flow<AiChunk>`.
- Accessibility features are optional and require explicit user consent.
- API keys must never be stored in source code or plain `SharedPreferences`.
- Secret/API-key storage is **deferred to Phase 5 (Fork 1)**: Phase 4 persists no secrets, so no secret
  store is introduced yet. `EncryptedSharedPreferences` is **not** the chosen mechanism — it is
  deprecated ("no longer recommended"). When a real key exists (cloud/ONNX), a `SecureSecretStore` port
  is added in `domain` and the impl (Tink AEAD + Android Keystore, or a backend proxy) chosen then.
  DataStore (Block E) holds **non-secret data only**.
- API keys must not appear in logs, analytics events, or crash reports.
- Keep business logic independent from Android framework APIs where possible.

## Target stack

- Kotlin
- Jetpack Compose
- Material 3
- MVVM + Clean Architecture
- Hilt dependency injection
- Coroutines and Flow
- Ktor client for cloud AI and streaming
- Kotlin Serialization
- ONNX Runtime Mobile with NNAPI where available
- Navigation Compose
- DataStore
- Room
- WorkManager

## Performance budgets

These are hard targets. Any feature that cannot meet them must degrade or be disabled on that device profile.

- Cold start, launcher visible: `< 400ms`
- First frame rendered from `Activity.onCreate`: `< 200ms`
- App grid visible and interactive from cold start: `< 600ms`
- Rule-based intent match: `< 10ms`
- ONNX inference on `MID_RANGE`: `< 150ms`
- Cloud AI first streaming token on good network: `< 2000ms`

Memory ceilings:

- `LOW_END`: `< 80MB` heap
- `MID_RANGE`: `< 150MB` heap
- `HIGH_END`: `< 250MB` heap

## Module layout

```text
app/                          Android launcher app, single NavHost, composition root, DI graph
core/                         Cross-cutting utilities and platform abstractions (kept thin)
core/ui/                      Design system, Compose components, theme
core/common/                  Presentation/state contracts: UiState, dispatchers, logging contracts,
                              navigation contracts (Routes, NavigationEvent)
core/android/                 Android helpers, DeviceProfile detection, PackageManager access,
                              SpeechInputSource Android implementation
core/testing/                 Shared fakes and test fixtures (built; bootstrapped Block B)
domain/                       Pure Kotlin: models, repository interfaces, use cases, OperationResult,
                              IntentMatcher / GenerativeAiEngine ports, DeviceCapability + routing policy
data/                         Repository implementations and data sources
data/repository/              Launcher repositories (InstalledApps, preferences, history), rule-based
                              matcher, Android ActionExecutor, Room (Block F) + DataStore (Block E)
data/ai-cloud/                Ktor cloud AI client and streaming adapter
data/ai-local/                ONNX NLU, embeddings, local inference (interface-gated, ONNX isolated)
feature/launcher/             Home screen, app grid, command input, launcher interactions
feature/assistant/            Text/voice AI command UI and SpeechInputSource abstraction
feature/suggestions/          Context-aware suggestions UI
feature/settings/             Settings UI (planned; inline placeholder until created)
feature/permission_education/ Permission education UI (built, Block G — real destination + request flow)
build-logic/                  Gradle convention plugins (planned)
```

Exact module names may be adjusted during setup, but dependencies must preserve Clean Architecture boundaries.

**Target-structure decisions (resolved):**

- `OperationResult` / `OperationError` are **domain** contracts (not `core/common`). The domain layer must not depend on `core/*`.
- `core/data` is dropped. Persistence (Room, DataStore, encrypted storage) lives in the `data/` layer, not in `core`, to keep `core` thin and avoid a god-storage module.
- `data/repository` is the single host for launcher repositories, the rule-based `IntentMatcher` implementation, and the Android `ActionExecutor`.
- Route contracts (`Routes`, `NavigationEvent`) stay in `core/common` for now. Extract a dedicated `core/navigation` module when destinations grow beyond the current set, when a non-`feature`/non-`app` module needs them, or when build profiling shows `core/common` as a recompilation hotspot.
- `core/testing` (shared fakes/fixtures) is **built** (bootstrapped Block B); `build-logic` (Gradle convention plugins) remains a planned addition, created on first need.
- `feature/permission_education` is **built** (Block G) — `AppNavHost` routes its real destination, not a placeholder. `feature/settings` is still a planned placeholder; its `AppNavHost` destination remains inline until the module is created (it is also the future home for the permission-education entry point — see the Block G/H ADRs).
- **Room** (Block F) uses **KSP** for its processor while Hilt stays on kapt (Fork 8 hybrid); schema is exported and versioned (`schemas/`, Fork 2), migration runway wired (`MigrationTestHelper`). **DataStore Preferences** (Block E) holds non-secret preferences/flags/profile-cache/last-known-suggestions only.

`SpeechInputSource` is a **domain** interface that abstracts Android `SpeechRecognizer`; its Android implementation lives in `core/android`. It must have a no-op or fake implementation (in `core/testing`) for tests and devices without available speech services.

`core/android` owns device capability detection:

```kotlin
enum class DeviceProfile {
    LOW_END,
    MID_RANGE,
    HIGH_END
}
```

`DeviceProfile` is decided from available RAM, CPU cores, NNAPI availability, GPU capability, thermal state, battery state, and connectivity. `LOW_END` devices should prefer rule matching and cloud routing when connected. `MID_RANGE` devices may use local ONNX classification when models are available. `HIGH_END` devices may enable more local AI features, still gated by model availability, thermals, and battery state.

## Dependency direction

```text
app        -> feature/*, data/*, core/*
feature/*  -> domain, core/ui, core/common
data/*     -> domain, core/common, core/android   (data/ai-local additionally -> ONNX)
core/android -> core/common
core/ui      -> core/common
domain     -> Kotlin stdlib / coroutines only
```

The domain layer must not depend on Android, Compose, Ktor, Hilt, Room, DataStore, WorkManager, ONNX, **or on `core/*`**. `OperationResult` / `OperationError` are domain contracts and live in `domain`.

> Resolved (Block A, Phase 3): `OperationResult` / `OperationError` now live in `domain`
> (`com.sidr.launcher.domain.result`); the former `domain -> core/common` edge is gone and
> `domain`'s compile classpath is stdlib + coroutines only (guard-verified each block).

Feature modules expose route constants and composable destinations, but must not directly depend on other feature modules.

## Navigation

Navigation uses Navigation Compose with a single `NavHost` in the `app` module.

Navigation events flow in one direction:

```text
ViewModel -> NavigationEvent -> NavHost in app
```

Deep link support must cover:

- Launcher shortcut intents routing to the correct destination.
- Assistant entry from notifications.
- Safe fallback to launcher home when a destination is unavailable.

## AI execution pipeline

1. User enters text or voice command.
2. Input is normalized and passed to a local fast matcher.
3. If confidence is sufficient, execute mapped intent.
4. If confidence is low, route to AI engine selection.
5. Cloud AI handles generative reasoning by default.
6. Optional local runtime can be used only when device capability and model availability allow it.
7. All engines stream output as `Flow<AiChunk>`.
8. Final action is confirmed or executed depending on risk level.

When the primary path fails, fallback order is:

```text
rule matcher
  -> local ONNX, if model is available and device is capable
  -> cloud AI, if connected and key is available
  -> static conversational fallback
```

Each step must fail gracefully without crashing or blocking launcher functionality.

## State management

Each feature `ViewModel` exposes a single sealed `UiState` through `StateFlow`.

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    // retryable (Block H, H2): whether re-running the failed op could plausibly succeed. It is a
    // property of the state in context (the ViewModel decides it), not of UiError. The retry action
    // is NOT carried here (no lambda in a data class) — the UI calls a ViewModel method.
    data class Error(val error: UiError, val retryable: Boolean = false) : UiState<Nothing>
}
```

Rules:

- Single source of truth per concern (see the `LauncherViewModel` note below).
- Use `StateFlow<UiState>`, not `LiveData`.
- Streaming AI responses use a separate `Flow<AiChunk>` collected in the UI layer.
- UI never holds business logic.
- Error states must be recoverable without app restart — `OperationError → UiError →
  UiState.Error(retryable)` + a `retry()` ViewModel method (Block H, H2).

`LauncherViewModel` state shape — **the single composite below is aspirational, not the current
code.** As of Phase 4 the ViewModel exposes **three independent `StateFlow`s** (single-source-per-concern,
not one source holding several states):

```kotlin
val uiState: StateFlow<UiState<LauncherUiState>>   // LauncherUiState(apps); Loading/Empty/Error/Success
val commandInput: StateFlow<String>                // backed by SavedStateHandle (H3 process-death restore)
val commandFeedback: StateFlow<CommandFeedback>    // transient last-command result (ephemeral, not restored)
// + navigationEvents: Flow<NavigationEvent> via a Channel

data class LauncherUiState(val apps: List<InstalledApp>)
```

`suggestions` (Ph7 context pipeline) and `aiState` (Ph5 generative) are **unbuilt** — `LauncherUiState`
grows (a new field/flow, decided then) when those phases land. The `SuggestionsCacheRepository` (Block E)
is the future cold-start repaint source; its content-restore activates with the Ph7 suggestions surface
(reconciliation rule: SavedStateHandle owns transient input/route, the DataStore cache owns content
first-paint, a fresh load supersedes the cached repaint — never merged).

## Launcher responsibilities

- Provide a compliant Android launcher activity.
- Show installed apps and common actions reliably.
- Surface AI command entry and contextual suggestions.
- Degrade gracefully when AI, network, microphone, or accessibility permissions are unavailable.

## Permissions strategy

Required permission:

- `QUERY_ALL_PACKAGES`: launcher core app discovery.

Optional permissions:

- `SET_WALLPAPER`: launcher wallpaper features.
- `RECORD_AUDIO`: voice input.
- `READ_CALENDAR`: contextual suggestions.
- `ACCESS_FINE_LOCATION`: contextual suggestions.
- `BIND_ACCESSIBILITY_SERVICE`: advanced automation.
- `RECEIVE_BOOT_COMPLETED`: suggestion warmup.

Rules:

- Never request optional permissions at startup.
- Request permissions only when the user triggers the related feature.
- Each denied permission disables exactly that feature.
- Never block launcher core on optional permissions.
- Never request `BIND_ACCESSIBILITY_SERVICE` without an explicit user-initiated consent flow.

Implemented in **Block G** (Fork 5 — "education ≠ request"): `:feature:permission_education` shows
rationale with **no** system dialog; the dialog is launched only on a feature trigger via
`ActivityResultContracts`. Status is checked through a `PermissionChecker` domain port
(`AndroidPermissionChecker` impl in `core/android`, over `checkSelfPermission`). The "dismissed /
don't ask again" flag is **per-feature** via `PermissionPrefsRepository` over DataStore (key
`perm_dismissed_<feature>`) — not a single global flag, so a denial disables exactly one feature.
Phase 4 wires a live `SET_WALLPAPER` trigger (normal permission); `RECORD_AUDIO`/`READ_CALENDAR`/
`ACCESS_FINE_LOCATION` have dormant education-only entries (request flow lands in their phase);
`BIND_ACCESSIBILITY_SERVICE` is **neither requested nor educated** (deferred to Phase 8). Note:
`PERMANENTLY_DENIED` is derivable only from the request callback (`shouldShowRequestPermissionRationale`),
not from `checkSelfPermission`; `refreshStatus()` is therefore upgrade-only (Block H, H-b) — a partial
fix revisited when the first dangerous permission (`RECORD_AUDIO`, Ph7) lands.

## Data boundaries

- App inventory, usage context, and user preferences stay behind repository interfaces.
- Sensitive user context must not be sent to cloud AI unless explicitly required and allowed.
- Cloud requests should use minimal context and avoid secrets.
- API keys and sensitive credentials must not be logged, serialized into crash reports, or stored in plain preferences.

## Data persistence

`DataStore` Preferences stores:

- User preferences.
- Feature flags.
- Device profile cache.
- Last known suggestions.

`Room` stores:

- App usage history.
- Suggestion ranking history.
- Intent match history for learning.

No persistence:

- AI conversation history.
- Location history.
- Raw voice input.

Rules:

- All persistence must be behind repository interfaces (domain interface ↔ `:data:repository` impl +
  mapping; Room/DataStore types never cross into `domain` — Fork 4).
- Feature modules must not access storage directly (no `feature → data` edge; repositories injected from `:app`).
- Sensitive data is **excluded from persistence entirely** in Phase 4. Secret storage is deferred to
  Phase 5 (Fork 1) — `EncryptedSharedPreferences` is deprecated and not used. Intent-match history
  redacts arbitrary-content match types (SEARCH/UNKNOWN store a placeholder, never the query — Block F).
- Retention is enforced in the repository on write (row-count caps; WorkManager cleanup is frozen to Ph6/9).

## Background processing

`WorkManager` is used for:

- ONNX model download and verification.
- Daily suggestion pre-computation when battery conditions allow it.
- Usage data cleanup.

Rules:

- No background work on `LOW_END` by default.
- Workers must respect battery saver mode.
- Workers must be idempotent and cancellable.
- Foreground services are not used unless strictly required.

## Error handling

Repository and use case operations return `OperationResult<T>` (owned by `domain`) and must not throw exceptions to UI.

Error categories (and their Block H, H2 retryability — a recoverable error becomes
`UiState.Error(retryable)` + a `retry()` ViewModel method, no process restart):

- `NetworkError`: show offline state, **retryable**.
- `AiUnavailable`: fall back to rule matcher (not button-retryable).
- `PermissionDenied`: show permission education UI (not retryable — granting is the fix).
- `DeviceNotCapable`: disable feature silently (not retryable).
- `UnknownError`: log non-sensitive context, show generic recovery UI, **retryable**.

`retry()` cancels any in-flight load (latest-wins) so a double-tap runs a single reload (Block H, H2).

Rules:

- Errors must never crash the launcher.
- Critical errors in AI paths must fall back gracefully.
- Crash reporting must exclude user content and keys.
- Logs must contain only non-sensitive diagnostic context.

## Testing strategy

- Domain use cases are covered by unit tests without Android dependencies.
- Intent matcher tests are table-driven and include confidence thresholds.
- Core screens have Compose preview tests.
- Data layer repositories have fake implementations for tests.
- Permission-denied, offline, and device-not-capable paths must be tested.

## Security model

API keys:

- Never stored in source code or version control.
- Never stored in plain `SharedPreferences`.
- **Phase 4 stores no secrets** (Fork 1). A `SecureSecretStore` (Tink AEAD + Keystore, or a backend
  proxy) is introduced in Phase 5 when a real key exists. `EncryptedSharedPreferences` is deprecated
  and is **not** the chosen mechanism.
- Never logged or included in crash reports.

User data:

- App inventory stays local.
- Usage history stays local.
- Calendar and location data are never sent to cloud AI.
- Cloud AI receives only the minimum required context.

Build security:

- R8 is enabled in release builds.
- ONNX Runtime requires explicit ProGuard rules.
- Release artifacts, including R8 mapping files, must not contain API keys.
- Network security config enforces HTTPS only.

## Build expectation

The first implementation milestone is a compile-ready skeleton with stubs and interfaces, not complete business logic.

R8/ProGuard rules are required for ONNX Runtime. Release readiness requires validation on Android 9, 11, 13, and 14, plus memory profiling on the `LOW_END` device profile.

