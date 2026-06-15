# Requirements

## Product

Build Sidr Launcher: an AI-first Android launcher for Android 9+.

Core capabilities:

- home screen launcher experience
- text command input
- voice command input with text fallback
- intent-based actions
- contextual suggestions
- hybrid AI execution
- graceful offline and permission-denied behavior

## Technical

Use:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM + Clean Architecture
- Hilt
- Coroutines and Flow
- Ktor
- Kotlin Serialization
- ONNX Runtime Mobile
- NNAPI where available

## Architecture

- Multi-module Gradle project.
- Domain layer must be Android-free.
- Features depend on domain, not data implementations.
- Data modules implement repositories and platform integrations.
- AI streaming contract is `Flow<AiChunk>`.

## First milestone

Create a compile-ready project skeleton with stubs. Do not implement full business logic before the skeleton builds.
