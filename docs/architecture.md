# Architecture

## Goal

Sidr Launcher is an AI-first Android launcher for Android 9+ (API 28+) that lets users start actions through text, voice, and contextual suggestions.

> **Design & agentic direction (pointers, not duplicated here).** Visual identity = "soft classic grey"
> ([spec](superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md)); target IA = **4 surfaces**
> (Home · App Drawer · Assistant · Settings). Six-layer agentic target A1–A6 in
> [agentic-os-architecture.md](agentic-os-architecture.md). Imported design source docs + reconciliation:
> [design/](design/) + ADR "2026-07-11 — DS-0" in [../ai-context/decisions.md](../ai-context/decisions.md).

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
- Secret/API-key storage: `SecureSecretStore` port (`domain.security`) is **built** (Phase 5, Block J).
  Impl: Keystore AES-256-GCM, dedicated `sidr_secrets` DataStore (separate from the privacy-guarded
  `sidr_preferences` store), BYOK model — the user pastes their own provider key; a backend-proxy impl
  swaps in later behind the same port. `EncryptedSharedPreferences` is deprecated and is **not** used.
  DataStore (Block E) holds **non-secret data only** (provider config = base URL + model, not the key).
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
feature/suggestions/          Stateless context-aware suggestions row (UI only; host VM owns state)
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

**Intent matching** (Phase 3, built) and **generative AI** (Phase 5, built) are two separate
pipelines that share no code. Matching ≠ generation; `HandleUserCommandUseCase` is untouched by the
generative path.

### Intent matching (offline, fast)

1. User enters text command in the launcher.
2. Input is normalized and passed to the unqualified `IntentMatcher`, which is the **rule-first
   `LayeredIntentMatcher`** (Phase 6, Block R). It runs `RuleBasedIntentMatcher` first (offline,
   `< 10ms`); if the rule result is **not** low-confidence it is returned **verbatim** and the NLU
   secondary is **never consulted** — the fast path and every Phase-3 outcome are preserved exactly.
3. Only on a low-confidence rule does it consult the local NLU secondary (`OnnxIntentClassifier`,
   `MatcherSource.NLU`), which **self-gates** per inference (LOW_END / no verified model /
   thermal/battery → escape without loading ONNX). An NLU escape leaves the weak rule standing; a
   real NLU answer wins only if it clears `suggestThreshold`, with its over-confident softmax
   **calibrated** into the `[suggestThreshold, autoExecuteThreshold)` band (`NluConfidenceCalibrator`)
   so a model-driven intent always **Suggests** and never silently auto-executes. With no model
   present (today's shipping state) the secondary always escapes, so behaviour is identical to
   rule-only. `HandleUserCommandUseCase` sees a single unqualified `IntentMatcher` and is untouched.
4. If confidence is sufficient, the mapped intent is executed via `HandleUserCommandUseCase`.
5. `SimpleCommand.OPEN_ASSISTANT` routes to the assistant screen; the generative pipeline starts there.

### Generative AI (assistant screen, Phase 5)

The assistant screen drives the generative pipeline via `GenerateReplyUseCase`:

1. User enters a prompt on the `AssistantScreen`.
2. `GenerateReplyUseCase.generate(command)` builds a minimal `AiRequest` via `PromptContextBuilder`
   (positive allow-list: only the verbatim user command + a static system prompt; no calendar /
   location / history / device context assembled — fail-closed privacy guard).
3. `DefaultGenerativeRouter` selects the engine in order:
   - **ONNX slot** (reserved for local *generation*, Phase 7+ — still empty; Phase 6's ONNX work
     feeds the separate `IntentMatcher` pipeline, not this generative router)
   - **Cloud** (`OpenAiCompatibleGenerativeAiEngine`) — if online + provider config present + non-blank
     key in Keystore. Sends `POST {baseUrl}/chat/completions` (configurable base URL, free-text model,
     `Authorization: Bearer <key>`, `stream: true`). Parses SSE `choices[].delta.content` deltas.
   - **Static fallback** (`StaticFallbackEngine`) — always available, canned conversational reply.
4. `Flow<AiChunk>` is collected in `AssistantViewModel` (inside `viewModelScope` — survives rotation,
   aborted on screen-leave, latest-wins `retry()`).
5. `AssistantScreen` renders streaming text from `StateFlow<AssistantUiState>`.

Terminal events are **values, not exceptions**: `Completed(stopReason)` or `Failed(AiError)`;
`REFUSAL` is a success terminal (the model declined), not an error. All `AiError` subtypes are
mapped to `UiError` in the feature layer — the domain type never reaches the UI directly.

**Provider config** (base URL + free-text model string) is persisted in DataStore via
`AiProviderConfigRepository`. The **API key** is persisted in Keystore via `SecureSecretStore`
(BYOK, per-provider slot keyed by the host component of the base URL). The inline provider-settings
form on the assistant screen is the Phase 5 key-entry surface; it relocates to `:feature:settings`
in a later phase.

When the primary path fails, fallback order is:

```text
rule matcher (offline, always)
  -> ONNX (reserved, Ph7+ — local *generation*; Phase 6 NLU lives in the IntentMatcher pipeline, not here)
  -> cloud AI (if online + config + key present)
  -> static conversational fallback (always available)
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

`LauncherViewModel` state shape — the launcher exposes **three independent `StateFlow`s**
(single-source-per-concern, not one source holding several states):

```kotlin
val uiState: StateFlow<UiState<LauncherUiState>>   // LauncherUiState(apps, suggestions); Loading/Empty/Error/Success
val commandInput: StateFlow<String>                // backed by SavedStateHandle (H3 process-death restore)
val commandFeedback: StateFlow<CommandFeedback>    // transient last-command result (ephemeral, not restored)
// + navigationEvents: Flow<NavigationEvent> via a Channel

data class LauncherUiState(
    val apps: List<InstalledApp>,
    val suggestions: List<Suggestion>,
)
```

Generative AI (Ph5) is **built and lives on the assistant screen**, NOT folded into `LauncherUiState`
(`aiState` was never added): `AssistantViewModel` holds its own `StateFlow<AssistantUiState>` with
`reply`, `status` (`Streaming`/`Done`/`Error`), and `form` (provider config); `LauncherViewModel`
is not touched by the generative pipeline. `suggestions` is now built and stays single-owner on the
host `LauncherViewModel`: first paint comes from `SuggestionsCacheRepository` (display cache), then a
fresh `SuggestionEngine` result supersedes the cached repaint — never merged.

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
Phase 4 wired the live `SET_WALLPAPER` trigger; Phase 7 then activated the dangerous-permission request
flows for `RECORD_AUDIO`, `READ_CALENDAR`, and `ACCESS_FINE_LOCATION` through the same education surface.
`RECEIVE_BOOT_COMPLETED` is a normal best-effort warmup permission (no dialog). `BIND_ACCESSIBILITY_SERVICE`
is **neither requested nor educated** (deferred to Phase 8). Note: `PERMANENTLY_DENIED` is derivable only
from the request callback (`shouldShowRequestPermissionRationale`), not from `checkSelfPermission`; the
Phase-7 `refreshStatus()` work discharged the original dangerous-permission debt for these live flows.

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
- Retention is enforced in the repository on write (row-count caps) and complemented by periodic
  WorkManager cleanup for stale usage-history rows.

## Background processing

`WorkManager` is used for:

- ONNX model download and verification.
- Daily suggestion pre-computation when battery conditions allow it.
- Usage data cleanup.

Rules:

- No background suggestion pre-compute on `LOW_END` by default.
- Suggestion pre-compute is gated by `aiSuggestionsEnabled`, scheduled as unique periodic work, and
  cancelled fail-closed when the gate is shut.
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
- `SecureSecretStore` is **built** (Phase 5, Block J). Impl: AES-256-GCM in the Android Keystore
  (`AndroidKeyStore` provider, `sidr_secret_aead_v1` alias, StrongBox-with-fallback), ciphertext
  persisted in a dedicated `sidr_secrets` DataStore. BYOK model — no developer key in the APK;
  backend-proxy impl swaps in later behind the same port. `EncryptedSharedPreferences` is deprecated
  and is **not** used.
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
