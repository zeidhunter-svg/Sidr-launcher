# ADR-001: Hybrid AI Execution

## Status

**Accepted 2026-06 · SUPERSEDED IN PART 2026-08-19** by the agentic-restart ADRs in
[ai-context/decisions.md](../../ai-context/decisions.md):

- **§1** (local rule matching first) — superseded by *ADR 1/4 — deterministic-first redefined*: FastPath
  is a latency optimization, not a filter on understanding.
- **§2** (ONNX Runtime Mobile for local NLU / classification / embeddings) — **void**. Closed by
  *ADR 2/4 — platform re-baseline 2026*; the stack is removed in Этап 0.3. OQ#1/#2/#3 closed with it.
- **§4** ("future MediaPipe LLM or llama.cpp integration") — **restated**. The designated local-inference
  runtime is **LiteRT / LiteRT-LM** (MediaPipe LLM Inference is built on LiteRT, so the old wording named
  a wrapper instead of its base), with **ExecuTorch** recorded as the alternative and an explicit switch
  condition, and **AICore / Gemini Nano** as a separate path on devices that have it. The `AiEngine`-style
  port stays runtime-agnostic, which is what keeps the choice replaceable.
- **§3, §5, §6 stand** (cloud as the default generative path; one streaming contract `Flow<AiChunk>`;
  capability/network/settings/privacy gating of engine selection).

## Context

Sidr Launcher needs AI-assisted commands, contextual suggestions, and possible natural-language reasoning. The app targets Android 9+ and must run across low-end and modern devices.

A fully local generative LLM is not practical as the default path because of model size, memory pressure, latency, battery use, and device fragmentation. ONNX Runtime Mobile is useful on Android, but it is better suited to smaller NLU, classification, and embedding models than full generative LLM serving.

## Decision

Use a hybrid AI architecture:

1. Run fast local rule-based intent matching first.
2. ~~Use ONNX Runtime Mobile for local NLU, intent classification, and embeddings.~~ **Void 2026-08-19.**
3. Use cloud AI as the default generative reasoning engine.
4. Keep local generative LLM support behind a runtime-agnostic engine/planner port. **Designated runtime
   (2026-08-19): LiteRT / LiteRT-LM**; alternative on record: ExecuTorch; separate path: AICore / Gemini
   Nano. ~~future MediaPipe LLM or llama.cpp integration~~
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
- ~~ONNX Runtime Mobile implements local classifier/embedding adapters in data modules.~~ Void 2026-08-19.
- Local inference remains unbuilt; when built it targets LiteRT / LiteRT-LM behind a runtime-agnostic port.
- ~~AI routing must prefer safe deterministic actions when confidence is high.~~ Restated 2026-08-19: AI
  routing **understands** freely; *execution* is what must pass the deterministic gates.

## Alternatives considered

### Cloud-only AI

Rejected as the only path because basic launcher commands should work quickly and partially offline.

### Fully local generative AI

Rejected as the default because it is not feasible across the target Android 9+ device range, especially on low-end devices.

### ONNX Runtime Mobile as primary LLM runtime

Rejected because ONNX Runtime Mobile is better aligned with NLU/classification/embeddings on mobile for this project.
