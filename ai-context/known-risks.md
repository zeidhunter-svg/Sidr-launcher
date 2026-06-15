# Known Risks

## Local AI feasibility

Local generative LLMs may be too slow or memory-heavy for low-end Android devices. Keep local LLM support optional and capability-gated.

## ONNX scope creep

Do not treat ONNX Runtime Mobile as the primary LLM runtime. Use it for classifier, NLU, and embedding workloads.

## Cloud privacy

Cloud AI requests can leak sensitive context if prompt construction is careless. Send only minimal, user-approved context.

## Launcher restrictions

Default launcher selection, package visibility, background policies, and settings intents vary across Android versions and OEMs.

## Accessibility sensitivity

Accessibility Service can create store review, privacy, and trust issues. It must be optional, transparent, and non-essential.

## Streaming complexity

Cloud and future local engines must follow the same `Flow<AiChunk>` contract to avoid UI duplication.

## Dependency drift

The project uses several moving Android/Kotlin dependencies. Pin versions in the version catalog and keep the skeleton buildable before expanding features.
