# ADR-001: Hybrid AI Execution

## Status

Accepted

## Context

Sidr Launcher needs AI-assisted commands, contextual suggestions, and possible natural-language reasoning. The app targets Android 9+ and must run across low-end and modern devices.

A fully local generative LLM is not practical as the default path because of model size, memory pressure, latency, battery use, and device fragmentation. ONNX Runtime Mobile is useful on Android, but it is better suited to smaller NLU, classification, and embedding models than full generative LLM serving.

## Decision

Use a hybrid AI architecture:

1. Run fast local rule-based intent matching first.
2. Use ONNX Runtime Mobile for local NLU, intent classification, and embeddings.
3. Use cloud AI as the default generative reasoning engine.
4. Keep local generative LLM support behind an `AiEngine` interface for future MediaPipe LLM or llama.cpp integration.
5. Expose all AI engines through a unified streaming contract: `Flow<AiChunk>`.
6. Gate engine selection by device capability, network availability, model availability, user settings, and privacy constraints.

## Consequences

### Positive

- Reliable behavior on low-end devices.
- Faster common commands through local matching.
- Clean path for future local LLM support.
- Unified UI streaming regardless of engine.
- Better privacy control because context construction is explicit.

### Negative

- Cloud AI introduces network dependency for generative responses.
- Requires engine routing and fallback logic.
- Local and cloud outputs may differ.
- Model packaging and updates need separate planning.

## Implementation notes

- Domain owns `AiEngine`, `AiRequest`, `AiChunk`, and routing contracts.
- Ktor implements cloud streaming in data modules.
- ONNX Runtime Mobile implements local classifier/embedding adapters in data modules.
- Local LLM implementation remains a stub until device and model strategy is selected.
- AI routing must prefer safe deterministic actions when confidence is high.

## Alternatives considered

### Cloud-only AI

Rejected as the only path because basic launcher commands should work quickly and partially offline.

### Fully local generative AI

Rejected as the default because it is not feasible across the target Android 9+ device range, especially on low-end devices.

### ONNX Runtime Mobile as primary LLM runtime

Rejected because ONNX Runtime Mobile is better aligned with NLU/classification/embeddings on mobile for this project.
