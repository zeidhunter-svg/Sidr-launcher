# Roadmap

## Phase 0: Documentation and decisions

- Create project documentation and AI handoff context.
- Record hybrid AI decision in ADR.
- Define architecture boundaries and domain contracts.

## Phase 1: Compile-ready skeleton

- Create multi-module Gradle project.
- Configure Kotlin, Android, Compose, Hilt, Ktor, Serialization, Coroutines, and ONNX dependencies.
- Add Android launcher manifest and basic `LauncherActivity`.
- Add domain models, repository interfaces, use-case stubs, and DI placeholders.
- Verify the project builds.

## Phase 2: Launcher shell

- Implement Compose home screen shell.
- Display installed apps through a repository abstraction.
- Add text command input.
- Add basic settings and permission education screens.

## Phase 3: Intent system

- Implement local rule-based intent matcher.
- Define action execution contracts.
- Add app launch, search, settings, and simple command intents.
- Add confidence thresholds and safe fallback behavior.

## Phase 4: Cloud AI integration

- Implement Ktor cloud AI client.
- Add streaming adapter using `Flow<AiChunk>`.
- Add prompt/context builder with privacy constraints.
- Add error, retry, timeout, and offline states.

## Phase 5: Local NLU and embeddings

- Integrate ONNX Runtime Mobile.
- Add intent classifier and embeddings interfaces.
- Use NNAPI opportunistically where available.
- Add model availability and device capability checks.

## Phase 6: Voice and contextual suggestions

- Add speech input abstraction.
- Add context-aware suggestion pipeline.
- Keep suggestions useful without sensitive or unavailable data.
- Add user controls for context collection.

## Phase 7: Optional advanced automation

- Add optional Accessibility Service flow with explicit consent.
- Implement only user-approved automation actions.
- Add clear disable path and audit-friendly UX.

## Phase 8: Hardening

- Add tests for domain logic and intent matching.
- Improve privacy, logging, and error handling.
- Optimize startup and memory usage.
- Validate behavior across Android 9+ devices.
