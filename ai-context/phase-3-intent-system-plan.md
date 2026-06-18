# Phase 3: Intent System Plan

## Goal

Implement the first production-oriented version of the launcher intent system: parse user commands locally, map them to safe executable actions, apply confidence thresholds, and provide predictable fallback behavior without relying on cloud AI as the primary path.

## Scope

Phase 3 covers:

- Local rule-based intent matching
- Intent/action domain contracts refinement
- Action execution pipeline
- App launch intent
- Search intent
- Settings intent
- Simple command intents
- Confidence scoring
- Safe fallback behavior
- Basic tests for matcher and executor behavior

Phase 3 does not cover:

- Cloud AI fallback implementation
- ONNX model integration
- Voice input
- Accessibility-based automation
- Full natural language understanding
- Complex multi-step agent workflows

## Architecture Direction

The intent system should follow this flow:

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

## Current Step

Current implementation step: `3.1.1`.

## Atomic Implementation Checklist

### 3.0 Foundation alignment

- [x] `3.0.1` Add `OperationResult<T>` and shared error categories in `core/common` or `domain`.
- [ ] `3.0.2` Update repository and use-case contracts touched by Phase 3 to return `OperationResult<T>` instead of throwing to UI.
- [x] `3.0.3` Add non-sensitive logging hooks for `OperationResult` failures.
- [x] `3.0.4` Add fake `OperationResult` test helpers for domain and data tests.

### 3.1 Navigation foundation

- [x] `3.1.1` Add route constants for launcher, assistant, settings, and permission education destinations.
- [x] `3.1.2` Add a single `NavHost` in the `app` module.
- [x] `3.1.3` Wire feature composable destinations without direct feature-to-feature dependencies.
- [x] `3.1.4` Add `NavigationEvent` flow from ViewModels to the app-level `NavHost`.
- [x] `3.1.5` Add safe fallback navigation to launcher home for unavailable destinations.

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

## Step 1: Review existing contracts

1. Inspect current domain models and interfaces related to:
   - `Intent`
   - `Action`
   - `Command`
   - `AiRequest`
   - `AiResponse`
   - repositories
   - use cases
2. Check whether existing names conflict with Android `Intent`.
3. Prefer project-specific names if needed, for example:
   - `UserIntent`
   - `LauncherIntent`
   - `IntentCandidate`
   - `ExecutableAction`
4. Keep domain contracts platform-independent where possible.

Expected result:

- Clear domain model boundaries
- No accidental dependency on Android framework types in pure domain modules
- Compile-ready contracts before implementation

## Step 2: Define intent types

Create or refine sealed/domain models for supported intents:

1. `LaunchAppIntent`
   - Target app package name if known
   - Display name query if package is not resolved yet
2. `SearchIntent`
   - Query text
   - Search target, for example web/app/local where applicable
3. `OpenSettingsIntent`
   - Optional settings destination
4. `SimpleCommandIntent`
   - Known launcher commands such as clear input, show apps, open assistant, etc.
5. `UnknownIntent`
   - Original input
   - Reason if useful

Expected result:

- Intent types are explicit and easy to extend
- Unknown or unsupported commands are represented safely

## Step 3: Define action types

Create or refine action models that describe what the launcher can execute:

1. `LaunchAppAction`
   - Package name
   - Optional activity/class name if needed later
2. `OpenSearchAction`
   - Query
   - Target
3. `OpenLauncherSettingsAction`
   - Optional destination
4. `ShowMessageAction`
   - User-facing message
5. `NoOpAction`
   - Safe fallback for unsupported/low-confidence commands

Expected result:

- Intent recognition is separated from action execution
- Unsafe or unknown commands never directly execute platform actions

## Step 4: Add matcher contract

Introduce a matcher interface in the domain layer, for example:

```kotlin
interface IntentMatcher {
    suspend fun match(input: String): IntentMatchResult
}
```

The result should include:

- Normalized input
- Best candidate intent
- Confidence score
- Optional alternative candidates
- Match source, for example `RULE_BASED`
- Optional explanation/debug reason for development builds

Expected result:

- Cloud AI, ONNX classifier, and rule-based matcher can later share the same contract or adapter pattern

## Step 5: Implement command normalization

Add a small normalization component before matching:

1. Trim whitespace
2. Collapse repeated spaces
3. Lowercase using a stable locale
4. Keep original input for display and logging-safe diagnostics
5. Avoid destructive transformations that break app names
6. Support simple multilingual aliases only if already planned

Examples:

```text
"  Open   Telegram " -> "open telegram"
"launch settings" -> "launch settings"
```

Expected result:

- Rule matching is stable and predictable
- Original input remains available for UI feedback

## Step 6: Implement rule-based matcher

Add the first local matcher implementation in the data layer or appropriate intent module.

Rules should cover:

1. App launch phrases:
   - `open <app>`
   - `launch <app>`
   - `start <app>`
   - optionally Russian equivalents if the app UX targets Russian users early
2. Search phrases:
   - `search <query>`
   - `find <query>`
   - `google <query>`
3. Settings phrases:
   - `settings`
   - `open settings`
   - `launcher settings`
4. Simple launcher commands:
   - `show apps`
   - `clear`
   - `help`

Matching principles:

- Prefer exact command prefixes over fuzzy matching
- Do not execute ambiguous commands automatically
- Return lower confidence for weak or partial matches
- Return `UnknownIntent` for empty or unsupported input

Expected result:

- Deterministic local intent matching works offline
- Matcher remains small and easy to test

## Step 7: Add confidence thresholds

Define confidence levels and routing rules:

1. High confidence, for example `>= 0.85`
   - Can resolve and execute safe actions
2. Medium confidence, for example `0.50..0.84`
   - Can show confirmation or suggestion
3. Low confidence, for example `< 0.50`
   - Must not execute action
   - Show fallback message or ask for clarification

Threshold values can be constants in a domain policy object, for example:

```kotlin
object IntentConfidencePolicy {
    const val AUTO_EXECUTE_THRESHOLD = 0.85f
    const val SUGGEST_THRESHOLD = 0.50f
}
```

Expected result:

- The launcher avoids unsafe accidental actions
- Future AI matchers can use the same confidence gate

## Step 8: Add app resolving for launch intents

For app launch commands:

1. Use installed apps repository abstraction
2. Match by display name first
3. Match by package name only when user input clearly looks like a package
4. Handle duplicate or similar app names
5. Return suggestions instead of auto-launching if ambiguous
6. Do not block UI while loading app list

Expected result:

- `open telegram` can resolve to a launchable app if installed
- Ambiguous names do not cause random app launches

## Step 9: Add action resolver

Introduce a resolver that maps recognized intents to executable actions:

```text
LauncherIntent -> ExecutableAction
```

Resolver responsibilities:

1. Convert `LaunchAppIntent` to `LaunchAppAction` only if app is resolved
2. Convert `SearchIntent` to `OpenSearchAction`
3. Convert `OpenSettingsIntent` to `OpenLauncherSettingsAction`
4. Convert unsupported or low-confidence results to `NoOpAction` or `ShowMessageAction`
5. Keep business rules out of UI composables

Expected result:

- UI only submits commands and renders results
- Domain/data layers decide what action is safe to execute

## Step 10: Add action executor contract

Define a platform-facing executor contract, for example:

```kotlin
interface ActionExecutor {
    suspend fun execute(action: ExecutableAction): ActionExecutionResult
}
```

Execution results should include:

- Success
- Failure with user-safe message
- Needs confirmation
- Unsupported
- No matching app/action

Expected result:

- Android-specific launching logic stays outside pure domain code
- UI receives structured execution results

## Step 11: Implement Android action executor

In the Android/app-facing layer:

1. Launch apps through package manager launch intents
2. Open launcher settings screen internally
3. Open search through safe browser/search intent if supported
4. Catch activity-not-found and security exceptions
5. Return structured failures instead of crashing
6. Avoid requesting sensitive permissions in this phase

Expected result:

- Basic actions can be executed safely on-device
- Failure paths are visible to the UI

## Step 12: Wire use case pipeline

Add or refine a use case such as:

```text
HandleUserCommandUseCase
```

Suggested flow:

1. Receive raw input from UI
2. Normalize input
3. Match intent locally
4. Apply confidence policy
5. Resolve action
6. Execute action when safe
7. Return `OperationResult` with UI-friendly result or recoverable error

Expected result:

- One main entry point for text command execution
- Easy future integration with cloud/local AI fallback
- No exceptions are thrown from use cases to UI


## Step 13: Update launcher UI integration

Connect the command input from the launcher shell to the new use case.

UI should:

1. Submit command text
2. Show loading/progress only if needed
3. Clear input after successful command if appropriate
4. Show fallback messages for unknown commands
5. Show suggestions for ambiguous app names
6. Route permission-related failures to permission education UI
7. Avoid displaying technical matcher details to users

Expected result:

- Users can type simple commands and see predictable behavior
- Intent system is integrated without overcomplicating UI state
- Permission-denied states are recoverable without restart

## Step 14: Add fallback behavior

Fallbacks should be safe and user-friendly:

1. Empty input:
   - Do nothing or show hint
2. Unknown command:
   - Show example commands
3. Low confidence:
   - Ask user to be more specific
4. Ambiguous app name:
   - Show matching apps as suggestions
5. Execution failure:
   - Show concise error message

Expected result:

- No silent failures
- No unsafe auto-execution

## Step 15: Add tests

Add focused unit tests for:

1. Command normalization
2. Rule-based app launch matching
3. Search command matching
4. Settings command matching
5. Unknown command behavior
6. Confidence threshold behavior
7. Action resolver behavior
8. Executor result handling with fake executor/repository
9. `OperationResult` error mapping
10. Permission-denied fallback behavior
11. Fake Room/DataStore repository behavior
12. Navigation event emission

Example test cases:

```text
"open telegram" -> LaunchAppIntent("telegram") with high confidence
"search weather tomorrow" -> SearchIntent("weather tomorrow")
"settings" -> OpenSettingsIntent
"" -> UnknownIntent / NoOpAction
"open" -> low confidence or fallback
```

Expected result:

- Core intent behavior is protected before adding AI fallback
- Future matcher changes are safer

## Step 16: Dependency injection wiring

Add DI bindings for:

1. `IntentMatcher`
2. Normalizer if represented as a separate class
3. Action resolver
4. Action executor
5. `HandleUserCommandUseCase`
6. Room database, DAOs, and repositories
7. DataStore preferences and repositories
8. Permission state provider

Use fake/stub implementations only where real platform behavior is not ready yet.

Expected result:

- App compiles and runs through real DI graph
- No manual construction in UI layer

## Step 17: Build verification

After implementation, run:

```bash
./gradlew assembleDebug
```

If tests are added, also run the focused test task, for example:

```bash
./gradlew testDebugUnitTest
```

Expected result:

- Project remains compile-ready
- Intent system does not break existing launcher shell

## Step 18: Acceptance criteria

Phase 3 can be considered complete when:

- Text commands are routed through a local matcher
- App launch, search, settings, and simple commands are represented as intents/actions
- Low-confidence commands do not auto-execute
- Unknown commands produce safe fallback UI feedback
- Android app launch execution is implemented through safe platform APIs
- `OperationResult` is used for Phase 3 repository and use-case errors
- App-level `NavHost` can route launcher, assistant, settings, and permission education destinations
- Room/DataStore setup is available behind repository interfaces
- Permission-denied command paths route to recoverable education UI
- Core matcher/resolver behavior has unit tests
- `./gradlew assembleDebug` passes

## Implementation Order

Recommended order for actual coding:

1. `OperationResult` and shared error categories
2. App-level `NavHost` and route contracts
3. Room/DataStore setup behind repositories
4. Permission model and education UI route
5. Domain models for intents/actions/results
6. Matcher and normalization contracts
7. Rule-based matcher implementation
8. Confidence policy
9. Action resolver
10. Action executor contract
11. Android action executor implementation
12. Main command handling use case
13. UI integration
14. Tests
15. DI wiring cleanup
16. Build and test verification

## Notes for future phases

- Phase 4 can add cloud AI fallback behind the same matcher/result pipeline.
- Phase 5 can add ONNX intent classifier as another matcher source.
- The local rule matcher should remain as the fastest and safest first-pass matcher.
- Accessibility-based automation must not be introduced here; keep it optional for a later phase with explicit consent.
