# Architecture

## Goal

Sidr Launcher is an AI-first Android launcher for Android 9+ (API 28+) that lets users start actions through text, voice, and contextual suggestions.

## Principles

- AI-first UX, but Android launcher behavior must remain reliable without AI.
- Fast local intent matching runs before any LLM call.
- Cloud AI is the default generative path; local AI is optional and capability-gated.
- Local ONNX Runtime Mobile is used for NLU, intent classification, and embeddings, not full LLM generation.
- Streaming responses use one unified contract: `Flow<AiChunk>`.
- Accessibility features are optional and require explicit user consent.
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

## Module layout

```text
app/                         Android launcher app and composition root
core/                        Shared utilities and platform abstractions
core/ui/                     Design system, Compose components, theme
core/common/                 Result types, dispatchers, logging contracts
core/android/                Android-specific helpers and capability checks
domain/                      Pure domain models, repositories, use cases
data/                        Repository implementations and data sources
data/ai-cloud/               Ktor cloud AI client and streaming adapter
data/ai-local/               ONNX NLU, embeddings, local AI interfaces
feature/launcher/            Home screen and launcher interactions
feature/assistant/           Text/voice AI command UI
feature/suggestions/         Context-aware suggestions UI
```

Exact module names may be adjusted during setup, but dependencies must preserve Clean Architecture boundaries.

## Dependency direction

```text
app -> feature/* -> domain
app -> data/* -> domain
data/* -> core/*
feature/* -> core/ui, core/common
domain -> Kotlin stdlib / coroutines only
```

The domain layer must not depend on Android, Compose, Ktor, Hilt, or ONNX.

## AI execution pipeline

1. User enters text or voice command.
2. Input is normalized and passed to a local fast matcher.
3. If confidence is sufficient, execute mapped intent.
4. If confidence is low, route to AI engine selection.
5. Cloud AI handles generative reasoning by default.
6. Optional local runtime can be used only when device capability and model availability allow it.
7. All engines stream output as `Flow<AiChunk>`.
8. Final action is confirmed or executed depending on risk level.

## Launcher responsibilities

- Provide a compliant Android launcher activity.
- Show installed apps and common actions reliably.
- Surface AI command entry and contextual suggestions.
- Degrade gracefully when AI, network, microphone, or accessibility permissions are unavailable.

## Data boundaries

- App inventory, usage context, and user preferences stay behind repository interfaces.
- Sensitive user context must not be sent to cloud AI unless explicitly required and allowed.
- Cloud requests should use minimal context and avoid secrets.

## Build expectation

The first implementation milestone is a compile-ready skeleton with stubs and interfaces, not complete business logic.
