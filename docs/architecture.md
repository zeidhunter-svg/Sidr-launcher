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
- API keys must be stored with `EncryptedSharedPreferences` or accessed through a backend proxy.
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
app/                         Android launcher app, single NavHost, composition root
core/                        Shared utilities and platform abstractions
core/ui/                     Design system, Compose components, theme
core/common/                 Result types, dispatchers, logging contracts
core/android/                Android-specific helpers, capability checks, DeviceProfile
core/data/                   DataStore, Room, encrypted storage abstractions
core/navigation/             Route contracts and navigation events
domain/                      Pure domain models, repositories, use cases
data/                        Repository implementations and data sources
data/ai-cloud/               Ktor cloud AI client and streaming adapter
data/ai-local/               ONNX NLU, embeddings, local AI interfaces
feature/launcher/            Home screen and launcher interactions
feature/assistant/           Text/voice AI command UI and SpeechInputSource abstraction
feature/suggestions/         Context-aware suggestions UI
feature/settings/            Settings and permission education UI
```

Exact module names may be adjusted during setup, but dependencies must preserve Clean Architecture boundaries.

`SpeechInputSource` abstracts Android `SpeechRecognizer` behind a testable interface in `feature/assistant` or `core/android`. It must have a no-op or fake implementation for tests and devices without available speech services.

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
app -> feature/* -> domain
app -> data/* -> domain
data/* -> core/*
feature/* -> core/ui, core/common, core/navigation
domain -> Kotlin stdlib / coroutines only
```

The domain layer must not depend on Android, Compose, Ktor, Hilt, Room, DataStore, WorkManager, or ONNX.

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
    data class Error(val reason: UiError) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}
```

Rules:

- Single source of truth per `ViewModel`.
- Use `StateFlow<UiState>`, not `LiveData`.
- Streaming AI responses use a separate `Flow<AiChunk>` collected in the UI layer.
- UI never holds business logic.
- Error states must be recoverable without app restart.

`LauncherViewModel` state shape:

```kotlin
data class LauncherUiState(
    val apps: List<InstalledApp>,
    val suggestions: List<Suggestion>,
    val inputState: InputState,
    val aiState: AiResponseState
)
```

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

- All persistence must be behind repository interfaces.
- Feature modules must not access storage directly.
- Sensitive data must use `EncryptedSharedPreferences` or be excluded from persistence entirely.

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

Repository and use case operations return `OperationResult<T>` and must not throw exceptions to UI.

Error categories:

- `NetworkError`: show offline state, retry available.
- `AiUnavailable`: fall back to rule matcher.
- `PermissionDenied`: show permission education UI.
- `DeviceNotCapable`: disable feature silently.
- `UnknownError`: log non-sensitive context, show generic recovery UI.

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
- Stored with `EncryptedSharedPreferences` or accessed through a backend proxy.
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

