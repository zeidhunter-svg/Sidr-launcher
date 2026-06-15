# Domain Model

This document defines the initial domain vocabulary. Keep these contracts Android-free.

## Core entities

### `AiRequest`

Represents a user request to the assistant.

Key fields:

- `input`: normalized user text
- `inputMode`: text, voice, suggestion, or system
- `context`: minimal allowed context for reasoning
- `capabilities`: current device/app capability snapshot

### `AiChunk`

A streamed AI response unit emitted by any AI engine.

Types:

- text delta
- tool/action proposal
- final answer
- error/status metadata

All cloud and local engines should expose `Flow<AiChunk>`.

### `AiEngine`

Interface for AI execution.

Responsibilities:

- accept `AiRequest`
- stream `AiChunk`
- expose availability and capability metadata

Expected implementations:

- cloud engine using Ktor
- optional local LLM engine placeholder
- local NLU/intent support using ONNX Runtime Mobile

### `IntentMatch`

Represents a possible interpreted user intent.

Key fields:

- `intent`: action category
- `confidence`: numeric confidence
- `source`: rule, ONNX, cloud, or user confirmation
- `slots`: extracted parameters

### `LauncherIntent`

High-level action the launcher can handle.

Initial categories:

- launch app
- search app or web
- open Android setting
- call/contact action
- create reminder or note
- ask assistant
- show suggestions

### `ActionRequest`

An executable action proposal derived from an intent.

Must include:

- action type
- parameters
- risk level
- whether confirmation is required

### `ActionResult`

Result of an attempted action.

States:

- success
- failure with reason
- cancelled
- requires permission
- requires confirmation

### `LauncherContext`

Minimal contextual state used for suggestions and AI routing.

May include:

- time bucket
- locale
- network state
- installed app summaries
- user-enabled context signals
- recent launcher interactions if allowed

Must not include sensitive content by default.

### `DeviceCapability`

Represents runtime capabilities relevant to AI and launcher features.

Examples:

- Android API level
- memory class
- network availability
- microphone availability
- NNAPI availability
- local model availability
- accessibility service enabled state

## Repository interfaces

Initial domain-facing repositories:

- `AppRepository`: installed apps, launch metadata, app search
- `AiRepository`: AI engine routing and streaming
- `IntentRepository`: local matching and classifier access
- `ActionRepository`: action execution and permission checks
- `SuggestionRepository`: contextual suggestions
- `UserPreferencesRepository`: settings and privacy controls
- `DeviceCapabilityRepository`: current device capabilities

## Use cases

Initial use-case stubs:

- `HandleAiCommandUseCase`
- `MatchIntentUseCase`
- `ExecuteActionUseCase`
- `ObserveSuggestionsUseCase`
- `ObserveInstalledAppsUseCase`
- `CheckDeviceCapabilitiesUseCase`

## Risk levels

Actions should be classified before execution:

- `LOW`: safe UI/navigation action, no confirmation needed
- `MEDIUM`: may affect user state, confirmation recommended
- `HIGH`: sensitive, external, destructive, or permission-heavy action; confirmation required

## Design rule

Domain models describe what should happen. Android, Compose, Ktor, ONNX, and permission APIs describe how it happens and belong outside the domain layer.
