# Current Status

> **Authoritative status lives in `CLAUDE.md` (session digest), `ai-context/decisions.md` (ADR log),
> and the per-phase plans.** This file is a short pointer/snapshot only — if it disagrees with those,
> they win. Last re-based: 2026-07-01.

## Where we are

**Phase 7 (voice input + contextual suggestions) — user-facing close SHIPPED (2026-07-01).**
Phases 3 → 7 are code-closed. There is **no phase actively in development**; what remains open is a
**model + device-acceptance track** (real ONNX models, the inert embedder seam, and the SM-A325F
acceptance pass), gated on open questions OQ#1–OQ#4.

## Phase ledger (see `docs/roadmap.md` + `ai-context/decisions.md`)

- **Phase 3 — intent system (A→D):** ✅ closed 2026-06-21. Rule-based `IntentMatcher`, action
  execution, confidence gating, MVP loop; live-verified on device.
- **Phase 4 — persistence/state/hardening (E→H):** ✅ closed 2026-06-23. DataStore, Room (usage /
  ranking / intent-match history + redaction), permission-education module, `UiState.Error(retryable)`.
- **Phase 5 — cloud AI (I→N):** ✅ code-closed 2026-06-27. OpenAI-compatible SSE engine
  (`Flow<AiChunk>`), `SecureSecretStore` (Keystore AES-256-GCM, BYOK), prompt/outbound privacy guards,
  `DefaultGenerativeRouter` + `StaticFallbackEngine`, assistant streaming UI. **Device-pending: N5**
  (real streaming / offline / cancel / rotation).
- **Phase 6 — local NLU + embeddings (O→R):** ✅ code-closed 2026-06-29. `OnnxIntentClassifier`
  (self-gating), rule-first `LayeredIntentMatcher` + `NluConfidenceCalibrator`, model provisioning
  (`ModelStore`/SHA-256/WorkManager). **Device/model-pending: OQ#1/#2** (real `intent.onnx` /
  `vocab.txt` + host/hash). With no model bundled today, NLU always escapes → exact rule-only parity.
- **Phase 7 — voice + contextual suggestions (S,T,U,W):** ✅ user-facing close 2026-07-01.
  `AndroidSpeechInputSource` + `RECORD_AUDIO` request flow, offline + opt-in suggestion providers,
  `SuggestionEngineImpl`, single-owner `LauncherUiState.suggestions` with cache-first paint, periodic
  precompute/cleanup workers + boot warmup.
  - **Block V (ONNX `TextEmbedder` + semantic re-rank):** implemented but **INERT** — gated on OQ#3
    (embedding model / host / hash / ONNX contract). Heuristic ranking is the shipping path.
- **Phase 8 — accessibility · Phase 9 — hardening:** ⛔ not started.

## Open questions gating the residual track

- **OQ#1 / OQ#2** — real NLU model (`intent.onnx`, pruned multilingual `vocab.txt`) + its host and
  pinned SHA-256. Until closed, `ModelDownloadConfig.INTENT_NLU_PENDING` is an inert seam.
- **OQ#3** — embedding model + tokenizer + host/hash (gates Block V). `EMBEDDING_PENDING` inert seam.
- **OQ#4** — on-device STT availability across the target device matrix (gates Block T acceptance).

## Device acceptance (SM-A325F / Android 13) — partial (see ADR 2026-07-01)

**Next step is a manual device-acceptance re-run — see
[`device-acceptance-brief.md`](device-acceptance-brief.md).** Scope: observation + matrix honesty
only, no runtime/model work.

- ✅ Block-J `SecretStoreInstrumentedTest` — real Keystore round-trip, `OK (3 tests)`.
- ✅ APK install presence; launcher launch-smoke; flag-off home (no suggestions row / no precompute).
- ⚠️ **Cold start:** `am start` showed `1381ms` then `1026ms` cold (hot `248ms`) vs the `< 400ms`
  budget (`docs/architecture.md:47`) → `PENDING / PERF-RISK`. Two `am start` calls are **not** a
  rigorous benchmark — a proper measure (force-stop + `-S` + N runs) **and** a root-cause are owed.
- ⚠️ **Trim:** `PARTIAL` — `RUNNING_CRITICAL` + backgrounded run = no-crash + renderer release, but
  no-model state, so native ONNX-session release is unproven. `BACKGROUND`/`COMPLETE` re-run pending.
- 🔁 **Three UI blockers hit during the pass were code-fixed the SAME day (ADR `decisions.md:262`) but
  NOT re-verified on device:** assistant route from home (`assistant`/`show assistant` rule entry
  restored), mic affordance (probe hardened + `<queries>`), and the `aiSuggestionsEnabled` toggle
  (`LauncherSettingsScreen`). Re-verifying these against the fixed code is the point of the next pass.
- ⏭️ Block-N N5 streaming/offline/cancel/rotation — needs a BYOK provider key (required setup, not
  optional); it is the only working AI path and is still unverified end-to-end.
- ⏭️ `OnnxIntentClassifierInstrumentedTest` assumption-skipped (no bundled model) — Block-P P5
  `< 150ms` inference + Block-Q real provisioning still model-blocked (OQ#1/#2).
- ⏭️ Block-U calendar/location granted/denied UX, Block-W boot warmup + WorkManager idempotency,
  Block-V embedder `< 150ms` + memory co-residency — pending.

## Not claimed done

Real assistant streaming against a live provider (needs BYOK config/key); OQ#1–OQ#4 real
models/vocab/embedder; the cold-start performance budget; full device-acceptance pass.

## Source of truth

- Session digest + hard rules: `CLAUDE.md`
- Decisions log (30 ADRs): `ai-context/decisions.md`
- Architecture (in sync): `docs/architecture.md` · Roadmap: `docs/roadmap.md`
- Active/last plan: `ai-context/phase-7-voice-suggestions-plan.md`
