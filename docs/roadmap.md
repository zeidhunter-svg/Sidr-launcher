# Roadmap

## Phase 0: Documentation and decisions

- Create project documentation and AI handoff context.
- Record hybrid AI decision in ADR.
- Define architecture boundaries, security principles, and domain contracts.
- Define performance budgets, permission strategy, and persistence boundaries.

## Phase 1: Compile-ready skeleton

- Create multi-module Gradle project.
- Configure Kotlin, Android, Compose, Hilt, Ktor, Serialization, Coroutines, ONNX, Navigation Compose, DataStore, Room, and WorkManager dependencies.
- Add Android launcher manifest and basic `LauncherActivity`.
- Add domain models, repository interfaces, `OperationResult`, use-case stubs, and DI placeholders.
- Add initial `DeviceProfile`, navigation route contracts, and permission contracts.
- Verify the project builds.

## Phase 2: Launcher shell

- Implement Compose home screen shell with `StateFlow<UiState>`.
- Add single `NavHost` in the `app` module and feature route wiring.
- Display installed apps through a repository abstraction.
- Add text command input.
- Add basic settings and permission education screens.
- Keep home screen, app grid, and app launch fully offline-capable.

## Phase 3: Intent system

- Implement local rule-based intent matcher.
- Define action execution contracts and `OperationResult`-based error handling.
- Add app launch, search, settings, and simple command intents.
- Add confidence thresholds and safe fallback behavior.
- Persist intent match history through repository-backed Room storage where allowed.
- Add table-driven matcher tests.

## Phase 4: Persistence, state, and navigation hardening

- Implement DataStore-backed user preferences, feature flags, device profile cache, and last known suggestions.
- Implement Room-backed usage history, suggestion ranking history, and intent match history.
- Ensure feature modules access persistence only through repositories.
- Harden `UiState`, navigation events, and recoverable error states.

## Phase 5: Cloud AI integration

- Implement Ktor cloud AI client.
- Add streaming adapter using `Flow<AiChunk>`.
- Add prompt/context builder with privacy constraints.
- Add API key storage through `EncryptedSharedPreferences` or backend proxy.
- Add error, retry, timeout, offline, and static fallback states.

## Phase 6: Local NLU and embeddings

- Integrate ONNX Runtime Mobile.
- Add intent classifier and embeddings interfaces.
- Use NNAPI opportunistically where available.
- Add model availability and `DeviceProfile` capability checks.
- Add WorkManager model download and verification with battery-aware constraints.

## Phase 7: Voice and contextual suggestions

- Add `SpeechInputSource` abstraction over Android `SpeechRecognizer`.
- Add no-op/fake speech input implementation for tests and unsupported devices.
- Add context-aware suggestion pipeline.
- Keep suggestions useful without sensitive or unavailable data.
- Add user controls and permission education for calendar, location, audio, and boot warmup.

## Phase 8: Optional advanced automation

- Add optional Accessibility Service flow with explicit user-initiated consent.
- Implement only user-approved automation actions.
- Add clear disable path and audit-friendly UX.
- Ensure accessibility denial disables only advanced automation.

## Phase 9: Hardening

- Add tests for domain logic, intent matching, repositories, permissions, offline states, and device capability paths.
- Improve privacy, logging, crash-report filtering, and error handling.
- Optimize startup, app grid rendering, AI latency, and memory usage against performance budgets.
- Add R8/ProGuard rules for ONNX Runtime and release builds.
- Validate behavior on Android 9, 11, 13, and 14.
- Complete LOW_END memory profiling before release.
