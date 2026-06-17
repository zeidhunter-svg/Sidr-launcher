# Sidr Launcher Handoff

## Purpose

This document is the single handoff context for continuing implementation of Sidr Launcher. The next AI model should be able to continue work from this file without re-reading the previous conversation or all other documentation files.

## Project goal

Sidr Launcher is an AI-first Android launcher for Android 9+ (API 28+) that lets users start actions through text, voice, and contextual suggestions.

The product must remain a reliable Android launcher even when AI, network, microphone, model files, optional permissions, or accessibility features are unavailable.

## Current repository state

Done:

- Architecture direction agreed and documented.
- Hybrid AI strategy agreed.
- Android limitations identified.
- Documentation structure created.
- Multi-module Gradle Android skeleton created.
- Android app module, launcher manifest, package directories, and placeholder activity created.
- Gradle wrapper created with Gradle `8.10.2`.
- Local Android SDK path recorded in `local.properties`.
- `./gradlew assembleDebug` completed successfully earlier.
- `docs/architecture.md` updated to production architecture.
- `docs/roadmap.md` updated with production phases.
- `ai-context/phase-3-intent-system-plan.md` updated and current step set to `3.0.1`.

Not done:

- Domain interfaces and stubs are not implemented yet.
- `OperationResult<T>` is not implemented yet.
- Room and DataStore are not set up yet.
- App-level Navigation Compose `NavHost` is not implemented yet.
- Permission education UI is not implemented yet.
- Data-layer AI stubs are not implemented yet.
- Feature UI modules contain package structure only.
- Real launcher, AI, intent, and suggestion business logic is not implemented yet.

Current implementation step:

```text
3.0.1 Add OperationResult<T> and shared error categories in core/common or domain.
```

## Existing Gradle structure

Current modules in `settings.gradle.kts`:

```text
:app
:core:common
:core:ui
:core:android
:domain
:data:ai-cloud
:data:ai-local
:feature:launcher
:feature:assistant
:feature:suggestions
```

Architecture now also expects these modules eventually, but they may not exist yet:

```text
:core:data
:core:navigation
:feature:settings
```

Do not assume missing modules exist. Add them only when needed for the current implementation step and keep the project compile-ready.

Current Kotlin source files found:

```text
app/src/main/java/com/sidr/launcher/SidrLauncherApp.kt
app/src/main/java/com/sidr/launcher/LauncherActivity.kt
```

Current version catalog includes:

- AGP `8.7.3`
- Kotlin `2.0.21`
- Compose BOM `2024.12.01`
- Hilt `2.52`
- Ktor `3.0.1`
- ONNX Runtime Android `1.20.0`

Navigation Compose, DataStore, Room, and WorkManager are part of the target architecture but may not yet be present in `gradle/libs.versions.toml`.

## Core architecture rules

Follow these rules strictly:

- AI-first UX, but Android launcher behavior must remain reliable without AI.
- Launcher core features, including home screen, app grid, and app launch, must work fully offline.
- No AI feature should block launcher startup.
- Fast local intent matching runs before any LLM call.
- Cloud AI is the default generative path; local AI is optional and capability-gated.
- ONNX Runtime Mobile is for NLU, intent classification, and embeddings, not full LLM generation.
- AI features degrade gracefully without connectivity.
- Streaming responses use one unified contract: `Flow<AiChunk>`.
- Accessibility features are optional and require explicit user consent.
- API keys must never be stored in source code or plain `SharedPreferences`.
- API keys must be stored with `EncryptedSharedPreferences` or accessed through a backend proxy.
- API keys must not appear in logs, analytics events, crash reports, or release artifacts.
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

## Intended module layout

```text
app/                         Android launcher app, single NavHost, composition root
core/                        Shared utilities and platform abstractions
core/ui/                     Design system, Compose components, theme
core/common/                 Result types, dispatchers, logging contracts
core/android/                Android-specific helpers, capability checks, DeviceProfile
core/data/                   DataStore, Room, encrypted storage abstractions
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

## Dependency direction

```text
app -> feature/* -> domain
app -> data/* -> domain
data/* -> core/*
feature/* -> core/ui, core/common, core/navigation when available
domain -> Kotlin stdlib / coroutines only
```

The domain layer must not depend on Android, Compose, Ktor, Hilt, Room, DataStore, WorkManager, or ONNX.

Feature modules may expose route constants and composable destinations, but must not directly depend on other feature modules.

## DeviceProfile system

`core/android` owns device capability detection:

```kotlin
enum class DeviceProfile {
    LOW_END,
    MID_RANGE,
    HIGH_END
}
```

`DeviceProfile` is decided from:

- available RAM
- CPU cores
- NNAPI availability
- GPU capability
- thermal state
- battery state
- connectivity

Routing meaning:

- `LOW_END`: prefer rule matching and cloud routing when connected; no background work by default.
- `MID_RANGE`: may use local ONNX classification when models are available.
- `HIGH_END`: may enable more local AI features, still gated by model availability, thermals, and battery.

## Navigation architecture

Use Navigation Compose with a single `NavHost` in the `app` module.

Navigation events flow in one direction:

```text
ViewModel -> NavigationEvent -> NavHost in app
```

Deep link support must eventually cover:

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

Fallback order when the primary path fails:

```text
rule matcher
  -> local ONNX, if model is available and device is capable
  -> cloud AI, if connected and key is available
  -> static conversational fallback
```

Each step must fail gracefully without crashing or blocking launcher functionality.

## State management

Each feature `ViewModel` exposes a single sealed `UiState` through `StateFlow`.

Reference shape:

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

`LauncherViewModel` target state shape:

```kotlin
data class LauncherUiState(
    val apps: List<InstalledApp>,
    val suggestions: List<Suggestion>,
    val inputState: InputState,
    val aiState: AiResponseState
)
```

## Permission strategy

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

## Data persistence strategy

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

## Background processing strategy

Use `WorkManager` for:

- ONNX model download and verification.
- Daily suggestion pre-computation when battery conditions allow it.
- Usage data cleanup.

Rules:

- No background work on `LOW_END` by default.
- Workers must respect battery saver mode.
- Workers must be idempotent and cancellable.
- Foreground services are not used unless strictly required.

## Error handling strategy

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

## Testing strategy

- Domain use cases: unit tests, no Android dependencies.
- Intent matcher: table-driven tests with confidence thresholds.
- Core screens: Compose preview tests.
- Data layer: fake implementations for all repositories.
- Permission-denied, offline, and device-not-capable paths must be tested.

## Roadmap

### Phase 0: Documentation and decisions

- Create project documentation and AI handoff context.
- Record hybrid AI decision in ADR.
- Define architecture boundaries, security principles, and domain contracts.
- Define performance budgets, permission strategy, and persistence boundaries.

### Phase 1: Compile-ready skeleton

- Create multi-module Gradle project.
- Configure Kotlin, Android, Compose, Hilt, Ktor, Serialization, Coroutines, ONNX, Navigation Compose, DataStore, Room, and WorkManager dependencies.
- Add Android launcher manifest and basic `LauncherActivity`.
- Add domain models, repository interfaces, `OperationResult`, use-case stubs, and DI placeholders.
- Add initial `DeviceProfile`, navigation route contracts, and permission contracts.
- Verify the project builds.

### Phase 2: Launcher shell

- Implement Compose home screen shell with `StateFlow<UiState>`.
- Add single `NavHost` in the `app` module and feature route wiring.
- Display installed apps through a repository abstraction.
- Add text command input.
- Add basic settings and permission education screens.
- Keep home screen, app grid, and app launch fully offline-capable.

### Phase 3: Intent system

- Implement local rule-based intent matcher.
- Define action execution contracts and `OperationResult`-based error handling.
- Add app launch, search, settings, and simple command intents.
- Add confidence thresholds and safe fallback behavior.
- Persist intent match history through repository-backed Room storage where allowed.
- Add table-driven matcher tests.

### Phase 4: Persistence, state, and navigation hardening

- Implement DataStore-backed user preferences, feature flags, device profile cache, and last known suggestions.
- Implement Room-backed usage history, suggestion ranking history, and intent match history.
- Ensure feature modules access persistence only through repositories.
- Harden `UiState`, navigation events, and recoverable error states.

### Phase 5: Cloud AI integration

- Implement Ktor cloud AI client.
- Add streaming adapter using `Flow<AiChunk>`.
- Add prompt/context builder with privacy constraints.
- Add API key storage through `EncryptedSharedPreferences` or backend proxy.
- Add error, retry, timeout, offline, and static fallback states.

### Phase 6: Local NLU and embeddings

- Integrate ONNX Runtime Mobile.
- Add intent classifier and embeddings interfaces.
- Use NNAPI opportunistically where available.
- Add model availability and `DeviceProfile` capability checks.
- Add WorkManager model download and verification with battery-aware constraints.

### Phase 7: Voice and contextual suggestions

- Add `SpeechInputSource` abstraction over Android `SpeechRecognizer`.
- Add no-op/fake speech input implementation for tests and unsupported devices.
- Add context-aware suggestion pipeline.
- Keep suggestions useful without sensitive or unavailable data.
- Add user controls and permission education for calendar, location, audio, and boot warmup.

### Phase 8: Optional advanced automation

- Add optional Accessibility Service flow with explicit user-initiated consent.
- Implement only user-approved automation actions.
- Add clear disable path and audit-friendly UX.
- Ensure accessibility denial disables only advanced automation.

### Phase 9: Hardening

- Add tests for domain logic, intent matching, repositories, permissions, offline states, and device capability paths.
- Improve privacy, logging, crash-report filtering, and error handling.
- Optimize startup, app grid rendering, AI latency, and memory usage against performance budgets.
- Add R8/ProGuard rules for ONNX Runtime and release builds.
- Validate behavior on Android 9, 11, 13, and 14.
- Complete LOW_END memory profiling before release.

## Current implementation plan: Phase 3

The active implementation plan is Phase 3: Intent System.

Goal:

- Implement the first production-oriented version of the launcher intent system.
- Parse user commands locally.
- Map commands to safe executable actions.
- Apply confidence thresholds.
- Provide predictable fallback behavior without relying on cloud AI as the primary path.

Phase 3 covers:

- Local rule-based intent matching.
- Intent/action domain contracts refinement.
- Action execution pipeline.
- App launch intent.
- Search intent.
- Settings intent.
- Simple command intents.
- Confidence scoring.
- Safe fallback behavior.
- Basic tests for matcher and executor behavior.

Phase 3 does not cover:

- Cloud AI fallback implementation.
- ONNX model integration.
- Voice input.
- Accessibility-based automation.
- Full natural language understanding.
- Complex multi-step agent workflows.

Phase 3 command flow:

```text
User text input
    -> Normalize command
    -> Local rule-based matcher
    -> Intent candidate with confidence
    -> Confidence gate
    -> Action resolver
    -> Action executor
    -> Execution result
    -> UI feedback
```

The local matcher must be fast, deterministic, offline-first, and safe. LLM-based interpretation can be added later as a fallback, but Phase 3 should work without it.

## Atomic implementation checklist

### 3.0 Foundation alignment

- [x] `3.0.1` Add `OperationResult<T>` and shared error categories in `core/common` or `domain`.
- [ ] `3.0.2` Update repository and use-case contracts touched by Phase 3 to return `OperationResult<T>` instead of throwing to UI.
- [x] `3.0.3` Add non-sensitive logging hooks for `OperationResult` failures.
- [x] `3.0.4` Add fake `OperationResult` test helpers for domain and data tests.

### 3.1 Navigation foundation

- [ ] `3.1.1` Add route constants for launcher, assistant, settings, and permission education destinations.
- [ ] `3.1.2` Add a single `NavHost` in the `app` module.
- [ ] `3.1.3` Wire feature composable destinations without direct feature-to-feature dependencies.
- [ ] `3.1.4` Add `NavigationEvent` flow from ViewModels to the app-level `NavHost`.
- [ ] `3.1.5` Add safe fallback navigation to launcher home for unavailable destinations.

### 3.2 Persistence foundation

- [ ] `3.2.1` Add DataStore Preferences setup for user preferences, feature flags, device profile cache, and last known suggestions.
- [ ] `3.2.2` Add repository interfaces for preferences and feature flags.
- [ ] `3.2.3` Add Room database setup for app usage history, suggestion ranking history, and intent match history.
- [ ] `3.2.4` Add DAO stubs and entities for intent match history used by Phase 3.
- [ ] `3.2.5` Ensure feature modules access Room and DataStore only through repositories.
- [ ] `3.2.6` Add fake repository implementations for tests.

### 3.3 Permission education foundation

- [ ] `3.3.1` Add permission model for required and optional launcher permissions.
- [ ] `3.3.2` Add permission education UI destination in settings or launcher flow.
- [ ] `3.3.3` Add user-triggered permission request events for optional permissions.
- [ ] `3.3.4` Ensure denied optional permissions disable only the related feature.
- [ ] `3.3.5` Ensure launcher core does not depend on optional permissions.
- [ ] `3.3.6` Keep accessibility consent as a separate explicit user-initiated flow for a later phase.

### 3.4 Intent system implementation

- [ ] `3.4.1` Review existing intent, action, command, repository, and use-case contracts.
- [ ] `3.4.2` Define or refine supported launcher intent types.
- [ ] `3.4.3` Define or refine executable action types.
- [ ] `3.4.4` Add matcher contract and `IntentMatchResult`.
- [ ] `3.4.5` Implement command normalization.
- [ ] `3.4.6` Implement rule-based matcher.
- [ ] `3.4.7` Add confidence policy and thresholds.
- [ ] `3.4.8` Add app resolving for launch intents.
- [ ] `3.4.9` Add action resolver.
- [ ] `3.4.10` Add action executor contract.
- [ ] `3.4.11` Implement Android action executor.
- [ ] `3.4.12` Wire `HandleUserCommandUseCase`.
- [ ] `3.4.13` Connect launcher UI command input to the use case.
- [ ] `3.4.14` Add safe fallback behavior for empty, unknown, low-confidence, ambiguous, and failed commands.
- [ ] `3.4.15` Persist allowed intent match history through repository abstraction.
- [ ] `3.4.16` Add table-driven tests for matcher, confidence, resolver, and executor behavior.
- [ ] `3.4.17` Wire DI bindings.
- [ ] `3.4.18` Run build and focused tests.

## Phase 3 detailed implementation notes

### Intent types

Create or refine sealed/domain models for supported intents:

- `LaunchAppIntent`
  - Target app package name if known.
  - Display name query if package is not resolved yet.
- `SearchIntent`
  - Query text.
  - Search target, for example web/app/local where applicable.
- `OpenSettingsIntent`
  - Optional settings destination.
- `SimpleCommandIntent`
  - Known launcher commands such as clear input, show apps, open assistant, help.
- `UnknownIntent`
  - Original input.
  - Reason if useful.

Avoid naming conflicts with Android `Intent`. Prefer names such as:

- `UserIntent`
- `LauncherIntent`
- `IntentCandidate`
- `ExecutableAction`

### Action types

Create or refine action models:

- `LaunchAppAction`
  - Package name.
  - Optional activity/class name if needed later.
- `OpenSearchAction`
  - Query.
  - Target.
- `OpenLauncherSettingsAction`
  - Optional destination.
- `ShowMessageAction`
  - User-facing message.
- `NoOpAction`
  - Safe fallback for unsupported/low-confidence commands.

Intent recognition must stay separated from action execution.

### Matcher contract

Introduce matcher interface in the domain layer, for example:

```kotlin
interface IntentMatcher {
    suspend fun match(input: String): IntentMatchResult
}
```

The result should include:

- Normalized input.
- Best candidate intent.
- Confidence score.
- Optional alternative candidates.
- Match source, for example `RULE_BASED`.
- Optional explanation/debug reason for development builds.

Cloud AI, ONNX classifier, and rule-based matcher should later share this contract or an adapter pattern.

### Command normalization

Add a small normalization component before matching:

1. Trim whitespace.
2. Collapse repeated spaces.
3. Lowercase using a stable locale.
4. Keep original input for display and logging-safe diagnostics.
5. Avoid destructive transformations that break app names.
6. Support simple multilingual aliases only if already planned.

Examples:

```text
"  Open   Telegram " -> "open telegram"
"launch settings" -> "launch settings"
```

### Rule-based matcher

Implement deterministic local matching.

Rules should cover:

- App launch phrases:
  - `open <app>`
  - `launch <app>`
  - `start <app>`
  - Optional Russian equivalents if the app UX targets Russian users early.
- Search phrases:
  - `search <query>`
  - `find <query>`
  - `google <query>`
- Settings phrases:
  - `settings`
  - `open settings`
  - `launcher settings`
- Simple launcher commands:
  - `show apps`
  - `clear`
  - `help`

Matching principles:

- Prefer exact command prefixes over fuzzy matching.
- Do not execute ambiguous commands automatically.
- Return lower confidence for weak or partial matches.
- Return `UnknownIntent` for empty or unsupported input.

### Confidence thresholds

Define confidence levels and routing rules:

- High confidence, for example `>= 0.85`: can resolve and execute safe actions.
- Medium confidence, for example `0.50..0.84`: show confirmation or suggestion.
- Low confidence, for example `< 0.50`: must not execute action; show fallback or ask for clarification.

Reference policy:

```kotlin
object IntentConfidencePolicy {
    const val AUTO_EXECUTE_THRESHOLD = 0.85f
    const val SUGGEST_THRESHOLD = 0.50f
}
```

### App resolving for launch intents

For app launch commands:

1. Use installed apps repository abstraction.
2. Match by display name first.
3. Match by package name only when user input clearly looks like a package.
4. Handle duplicate or similar app names.
5. Return suggestions instead of auto-launching if ambiguous.
6. Do not block UI while loading app list.

### Action resolver

Map recognized intents to executable actions:

```text
LauncherIntent -> ExecutableAction
```

Responsibilities:

1. Convert `LaunchAppIntent` to `LaunchAppAction` only if app is resolved.
2. Convert `SearchIntent` to `OpenSearchAction`.
3. Convert `OpenSettingsIntent` to `OpenLauncherSettingsAction`.
4. Convert unsupported or low-confidence results to `NoOpAction` or `ShowMessageAction`.
5. Keep business rules out of UI composables.

### Action executor

Define platform-facing executor contract, for example:

```kotlin
interface ActionExecutor {
    suspend fun execute(action: ExecutableAction): ActionExecutionResult
}
```

Execution results should include:

- Success.
- Failure with user-safe message.
- Needs confirmation.
- Unsupported.
- No matching app/action.

Android executor responsibilities:

1. Launch apps through package manager launch intents.
2. Open launcher settings screen internally.
3. Open search through safe browser/search intent if supported.
4. Catch activity-not-found and security exceptions.
5. Return structured failures instead of crashing.
6. Avoid requesting sensitive permissions in Phase 3.

### Main use case

Add or refine a use case such as:

```text
HandleUserCommandUseCase
```

Suggested flow:

1. Receive raw input from UI.
2. Normalize input.
3. Match intent locally.
4. Apply confidence policy.
5. Resolve action.
6. Execute action when safe.
7. Return `OperationResult` with UI-friendly result or recoverable error.

Use cases must not throw exceptions to UI.

## Acceptance criteria for Phase 3

Phase 3 can be considered complete when:

- Text commands are routed through a local matcher.
- App launch, search, settings, and simple commands are represented as intents/actions.
- Low-confidence commands do not auto-execute.
- Unknown commands produce safe fallback UI feedback.
- Android app launch execution is implemented through safe platform APIs.
- `OperationResult` is used for Phase 3 repository and use-case errors.
- App-level `NavHost` can route launcher, assistant, settings, and permission education destinations.
- Room/DataStore setup is available behind repository interfaces.
- Permission-denied command paths route to recoverable education UI.
- Core matcher/resolver behavior has unit tests.
- `./gradlew assembleDebug` passes.

## Immediate next task: implement 3.0.1

Implement only this step first:

```text
3.0.1 Add OperationResult<T> and shared error categories in core/common or domain.
```

Recommended placement:

- Put generic result and error primitives in `core/common` because they are shared across domain, data, and feature layers.
- Domain may depend on `core/common`; it already does in `domain/build.gradle.kts`.

Suggested files:

```text
core/common/src/main/java/com/sidr/launcher/core/common/result/OperationResult.kt
core/common/src/main/java/com/sidr/launcher/core/common/result/OperationError.kt
```

Suggested model shape:

```kotlin
sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: OperationError) : OperationResult<Nothing>
}
```

Suggested error shape:

```kotlin
sealed interface OperationError {
    data class NetworkError(val retryable: Boolean = true) : OperationError
    data object AiUnavailable : OperationError
    data class PermissionDenied(val permission: String) : OperationError
    data class DeviceNotCapable(val feature: String) : OperationError
    data class UnknownError(val reason: String? = null) : OperationError
}
```

Keep the first implementation small. Do not add logging, repositories, Room, DataStore, navigation, or UI in step `3.0.1`; those are later checklist items.

After implementing `3.0.1`:

1. Run the smallest relevant build check, preferably:

```bash
./gradlew :core:common:compileKotlin
```

2. If that task is unavailable, run:

```bash
./gradlew assembleDebug
```

3. Update `ai-context/phase-3-intent-system-plan.md` checklist from:

```text
- [ ] `3.0.1` Add `OperationResult<T>` and shared error categories in `core/common` or `domain`.
```

to:

```text
- [x] `3.0.1` Add `OperationResult<T>` and shared error categories in `core/common` or `domain`.
```

4. Do not mark any other checklist item complete unless it was actually implemented and verified.

## Implementation constraints for the next model

- Keep every change compile-ready.
- Make the smallest correct change for the current checklist step.
- Do not jump ahead unless explicitly asked.
- Do not implement full business logic yet.
- Do not add AI cloud integration before the local rule-based pipeline exists.
- Do not add ONNX implementation before the local matcher and contracts are ready.
- Do not add Accessibility Service implementation in Phase 3.
- Do not store secrets or introduce placeholder API keys.
- Do not log user input, API keys, raw voice input, calendar data, location data, or crash-sensitive content.
- Preserve Clean Architecture dependency direction.
- Domain must remain free of Android, Compose, Ktor, Hilt, Room, DataStore, WorkManager, and ONNX.
- Feature modules must not access persistence directly.
- Optional permissions must not be requested at startup.
- If the tree is dirty, do not revert user changes.

## Verification commands

Use focused checks first:

```bash
./gradlew :core:common:compileKotlin
```

Then broader checks when cross-module changes are made:

```bash
./gradlew assembleDebug
```

For future tests:

```bash
./gradlew testDebugUnitTest
```

## Files to know

Primary handoff file:

```text
ai-context/handoff.md
```

Architecture and plans already updated:

```text
docs/architecture.md
docs/roadmap.md
ai-context/phase-3-intent-system-plan.md
ai-context/current-status.md
```

Gradle files:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/build.gradle.kts
domain/build.gradle.kts
```

Existing app files:

```text
app/src/main/java/com/sidr/launcher/SidrLauncherApp.kt
app/src/main/java/com/sidr/launcher/LauncherActivity.kt
```

## Final note

The next model should start at `3.0.1`, implement `OperationResult<T>` and `OperationError`, verify compilation, and update the checklist. All later work should follow the atomic checklist in this document.
