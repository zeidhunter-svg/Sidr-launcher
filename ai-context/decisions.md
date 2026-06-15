# Decisions

## Accepted architecture

- Use multi-module Clean Architecture.
- Keep domain models and use cases independent from Android APIs.
- Use Compose and Material 3 for UI.
- Use Hilt for dependency injection.
- Use Ktor for cloud AI calls and streaming.
- Use Kotlin Serialization for DTOs.

## AI decisions

- Use local fast intent matching before LLM calls.
- Use cloud AI as the default generative engine.
- Use ONNX Runtime Mobile for NLU, intent classification, and embeddings.
- Do not use ONNX Runtime Mobile as the primary generative LLM runtime.
- Keep local LLM runtime behind an interface for future MediaPipe LLM or llama.cpp integration.
- Use unified `Flow<AiChunk>` streaming for all AI engines.

## Android decisions

- Support Android 9+ (API 28+).
- Accessibility Service is optional and requires explicit user consent.
- Core launcher behavior must work without accessibility permissions.
- Low-end devices should use cloud AI or rule-based behavior, not local generative LLM.

## Implementation decision

Start with documentation and compile-ready skeleton only. Add business logic in later phases.
