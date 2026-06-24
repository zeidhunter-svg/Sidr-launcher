# Phase 5 — Cloud AI Integration (multi-provider)

> Format mirrors `phase-4-plan.md`: forks-before-code → blocks → checkbox steps → acceptance →
> demoable milestones. Block lettering continues Phase 4 (E→H), so Phase 5 = **Blocks I → N**.
> **Status: forks decided 2026-06-24; plan agreed; no block started.** Each block gets its own
> execution prompt. **First execution round = Block I only** (confirmed; its prompt is drafted in
> `phase-5-block-I-prompt.md`).

## Pre-flight — verify before starting Block I (repo-truth check)

This plan was written against the project docs + the agent's earlier code-reading notes; the
following must be confirmed against the **actual repo** before Block I starts. If any differs, the
plan is patched at that point (structure is unaffected — these only change "create" vs "extend" and
the "new deps" accounting):

- [ ] **AI contracts are absent in `:domain`.** `grep -rn "GenerativeAiEngine\|AiChunk" domain/src/`
      returns only the KDoc reference in `IntentMatcher.kt` (no real files). If they already exist,
      Block I *extends* rather than *creates*.
- [ ] **`:data:ai-cloud` is an empty scaffold that already wires Ktor.** Its `build.gradle.kts` has
      Ktor (client-core/android/content-negotiation, serialization-kotlinx-json) + serialization, deps
      on `:core:common`/`:domain`, **no `:core:android` edge**, and **zero `.kt` sources**. Confirms
      Block K adds no *new* Ktor deps (only Ktor MockEngine, test-only).
- [ ] **`OperationResult`/`OperationError` live at `com.sidr.launcher.domain.result`.**
      `SecureSecretStore` (Block I) and all repo-style returns depend on this exact location.
- [ ] **`feature/assistant` is a stub** (`AssistantScreen` = centered `Text`, no ViewModel, no
      `NavigationEvent` wiring). Confirms Block N builds the real VM/stream from scratch.
- [ ] **Catalog has Ktor 3.0.1 + serialization 1.7.3 already.** Confirms the only genuinely new
      dependency in the whole phase is Ktor MockEngine (test); ESP / `security-crypto` stay forbidden.

Run order: do this check, note any deltas, then execute the Block I prompt.

## Core stance for this phase

The generative pipeline is **provider-neutral by construction**. Anthropic is the *first adapter*,
not a baked-in assumption. `:domain` names no vendor, no wire format, no sampling params. Adding a
second provider later (an **OpenAI-compatible** adapter covers OpenAI / OpenRouter / Together / Groq /
local llama.cpp / Ollama with one configurable base URL) must require **zero `:domain` changes** —
only a new `GenerativeAiEngine` impl + a new `AiProviderId` + a key entry.

For Phase 5 we build the vendor-neutral seam + **one** real adapter (Anthropic). The
OpenAI-compatible adapter is a **planned fast-follow right after Block N** (not optional) — a second
adapter is the only real test that the seam is actually vendor-neutral and not leaking Anthropic
assumptions. It is kept out of the Phase-5 close only so Block K isn't widened to two wire formats
before the first one ships on device.

## In scope (unfreezing what Phase 4 deferred)

- **Secrets:** `SecureSecretStore` port (domain) + first real on-device implementation (Fork 1 is
  un-deferred here). Per-provider keyed.
- **Cloud AI:** Ktor client + streaming adapter (`Flow<AiChunk>`), provider behind a port.
- **Prompt/context builder** with privacy allow-list.
- **Routing seam + static fallback** behind an ordered router (ONNX slot reserved for Ph6).
- **Assistant streaming UI** (real `feature/assistant`) + minimal inline key entry.
- **Connectivity** port + impl.

## Hard invariants (the plan must obey)

- `:domain` stays pure: stdlib + kotlinx-coroutines only. No Android, no `core/*`, no Ktor, no
  `kotlinx.serialization` annotations. (`Flow` is allowed, as in existing domain interfaces.)
- Ports in `:domain`; implementations in `:data:*` / `:core:android`. Wire formats (SSE, JSON
  schema, auth headers) live only inside an adapter.
- `IntentMatcher` ≠ `GenerativeAiEngine`. **`HandleUserCommandUseCase` is untouched.** Generation is
  reached from `:feature:assistant` via a new `GenerateReplyUseCase`. Matching ≠ generation.
- `feature/*` has no edge into `:data:*` — only domain ports, injected from `:app`.
- Streaming follows the unified contract `Flow<AiChunk>`, collected in the UI; expected failures are
  terminal **values** (`AiChunk.Failed`), never thrown to the collector — the Flow analog of
  "`OperationResult`, never throw to UI".
- Launcher core stays fully offline-capable; no AI init on launcher cold start (engine/secret store
  constructed lazily, only on the assistant surface).
- Security non-negotiables (from `architecture.md`): no key in source/VCS, no key in logs / crash
  reports / analytics, HTTPS-only network config, no `EncryptedSharedPreferences` (deprecated).

---

## Block order and rationale

```
I  AI domain contracts (pure)      — vendor-neutral seam; nothing else compiles against AI without it
J  Secure secret storage           — first real on-device secret; K needs a key source
K  Cloud engine (Anthropic adapter)— SSE → Flow<AiChunk>; testable via Ktor MockEngine against the port
L  Prompt/context builder + guard  — pure; minimal outbound context + privacy allow-list
M  Routing seam + static fallback  — GenerateReplyUseCase + StaticFallbackEngine + ordered router
N  Assistant UI + key entry + close — streaming screen, inline key entry, docs-sync, ADRs, phase close
```

- **I first** — it's the only thing every later block compiles against; pure JVM, lowest risk.
- **J before K** — the engine needs a key source. But note: K can be built and fully MockEngine-tested
  against the `SecureSecretStore` *port* (fake) before J's real impl lands, so J's platform risk does
  not gate K's logic.
- **L** is pure JVM and independent of J/K; it may run parallel with I or fold into an early round.
- **M** integrates K + static fallback behind the router and exposes the use-case.
- **N last** — UI needs a working engine (K) routed (M) and a key surface (J).

---

## Fork decisions (all decided 2026-06-24 — agreed before code)

### Fork P5-1 — Key storage / threat model — **recommend: BYOK + on-device Keystore (scoped interim)**
Decide the *product model*, since it sets the whole value of Block J:
- **Never** ship a developer API key inside the APK — on-device encryption cannot protect a key that
  is in every install.
- **BYOK** (the user pastes their own provider key): on-device Keystore is defensible — the threat is
  "the user's own device is compromised", blast radius is the user's own quota. This matches the
  inline-key-entry idea (P5 open question).
- **Backend proxy** (no key on device) is the honest long-term answer for a mass-market ship.

**Decided (2026-06-24):** **BYOK + on-device Keystore AES-GCM, scoped interim.** No proxy now (no
backend infra at this stage; BYOK removes the in-APK-key risk by definition). The `SecureSecretStore`
port keeps it reversible — a proxy-backed impl swaps in later behind the same port with no domain/UI
change. `EncryptedSharedPreferences` stays forbidden (deprecated at security-crypto 1.1.0-alpha07).

### Fork P5-2 — Provider/contract — **recommend: vendor-neutral port, Anthropic first adapter**
- Domain port `GenerativeAiEngine` is provider-neutral (Block I). Provider/model are opaque
  (`AiProviderId`/`AiModelId`).
- First adapter: **Anthropic Messages API, raw Ktor SSE** (`content_block_delta`/`text_delta`;
  `x-api-key` + `anthropic-version`). Provider lives behind the port.
- **Request body is model-aware** (a per-model capability map): Opus 4.7/4.8 reject
  `temperature`/`top_p`/`top_k` (HTTP 400) and force adaptive thinking; other models differ. A fixed
  JSON shape will 400 — the adapter selects the allowed shape per model.
- **Decided (2026-06-24):** **default model = Haiku 4.5** (`claude-haiku-4-5-20251001`) for the
  `< 2000ms` first-token budget + cost; Opus 4.8 (`claude-opus-4-8`) is opt-in. **Anthropic-only
  closes Phase 5; the OpenAI-compatible adapter is a planned fast-follow right after Block N.** Verify
  live model strings at build time (they move).

### Fork P5-3 — Prompt / outbound privacy — **recommend: pure builder + positive allow-list + guards**
- `PromptContextBuilder` is pure (`:domain` or pure data class), assembling **minimal context**.
- **Positive allow-list** (only the explicitly listed fields may go out) so future context additions
  fail closed. Today the assistant input is essentially the user's typed command — keep it that way.
- `AiRequestGuardTest` asserts no calendar/location/usage/raw-history field can reach the request.
- A separate **secret-not-in-logs guard** (mirrors the Phase-4 privacy key-name guard): the API key
  never appears in logs/crash/analytics, and `AiRequest`/`AiError` `toString()` cannot leak the key
  or raw prompt.

### Fork P5-4 — Routing seam — **recommend: `GenerateReplyUseCase` from the assistant feature**
Generation is invoked by a new `GenerateReplyUseCase` (returns `Flow<AiChunk>`) used by
`:feature:assistant`. `HandleUserCommandUseCase` is **unchanged**; the existing
`SimpleCommand.OPEN_ASSISTANT → CommandOutcome.OpenAssistant → navigate(Routes.Assistant)` handoff
(route string stays in the VM) is the entry point. Matching stays separate from generation.

### Fork P5-5 — Streaming state — **recommend: Flow + separate status + retry + cancellation/timeout**
The assistant VM exposes the response stream as `Flow<AiChunk>` (collected in the UI) and a **separate**
`StateFlow<UiState<AssistantStatus>>` (Loading/streaming/Done/Error) + `retry()` (latest-wins, like
Block H2). Cold-flow cancellation aborts the Ktor request on screen-leave/config-change. The adapter
enforces a first-token deadline tied to the 2000ms budget + an idle-between-chunks timeout
(→ `AiError.Timeout`).

### Fork P5-6 — Static fallback — **recommend: `StaticFallbackEngine` + ordered `GenerativeRouter`**
`StaticFallbackEngine implements GenerativeAiEngine` (canned conversational reply, no network). The
`GenerativeRouter` is an ordered composite: **cloud (if online + key present) → static fallback**, with
an explicit **reserved slot for Ph6 ONNX** between matcher and cloud, so ONNX inserts with no rewrite.
The use-case depends only on `GenerativeAiEngine` (the router at runtime).

### Fork P5-7 — Connectivity — **recommend: `ConnectivityChecker` port, impl in `core/android`**
Domain port (`isOnline()` + `Flow<Boolean>`); Android impl over `ConnectivityManager` in `core/android`
(precedent: `AndroidPermissionChecker`). Used by the router to choose cloud vs static.

### Fork P5-8 — Secret-impl module — **recommend: `:data:repository` (persistence), thin Keystore helper optional**
**Decided (2026-06-24):** impl lives in **`:data:repository`** (`…data.repository.security`) — it
persists an encrypted blob, so it follows the "persistence lives in data" principle and the Block-G
split (stateful `PermissionPrefsRepositoryImpl` in data, stateless `PermissionChecker` in
`core/android`). If the Keystore crypto primitive grows, a thin helper may move to `core/android`
later; no dedicated `:data:secrets` module at this stage.

### Fork P5-9 — New dependencies
- **Ktor client** — already wired in `:data:ai-cloud` (core/android-free), already in the catalog
  (3.0.1) — **not new**.
- **Ktor MockEngine** (test source set of `:data:ai-cloud`) — **genuinely new**, test-only.
- **Tink** — only if chosen over raw Keystore in Fork P5-1; recommend raw Keystore (no new dep).
- **Network security config** — XML + manifest, **no dependency**; HTTPS-only, `api.anthropic.com`
  (and any configured base URL) the only cleartext-forbidden egress.
- `EncryptedSharedPreferences` / `security-crypto` — **forbidden** (deprecated).

---

## Block I — AI domain contracts (pure, multi-provider)

**Goal:** vendor-neutral generative contracts in `:domain`. JVM-only, no impls.
**Depends on:** nothing. Forks 2/3/5/6 inform the shapes.

**New files (`:domain`):**
- `…domain.ai`: `AiProviderId`, `AiModelId` (opaque value classes); `AiRequest`/`AiMessage`/`AiRole`
  (content-only, `system` separate, **no sampling params**, optional `model` override);
  `AiChunk` (`Text`/`Completed`/`Failed`) + `AiStopReason` (incl. `REFUSAL` as a *success* terminal)
  + `AiUsage`; `AiError` (provider-neutral, no secret/raw-content fields); `GenerativeAiEngine` port
  + `GenerativeRouter` marker; `AiChunks.assembleText` pure helper.
- `…domain.security`: `SecureSecretStore` (generic, `OperationResult`, never throws) + `SecretKey` +
  `SecretKeys.apiKey(provider)` per-provider convention.
- `…domain.connectivity`: `ConnectivityChecker` port.

**New fakes (`:core:testing`):** `FakeGenerativeAiEngine` (scripted chunks), `FakeSecureSecretStore`,
`FakeConnectivityChecker`.

**Steps:**
- [ ] `I1` `domain.ai` contracts per the shapes above; refusal = stop reason, failures = terminal value.
- [ ] `I2` `domain.security` generic per-provider secret store port.
- [ ] `I3` `domain.connectivity` port.
- [ ] `I4` Fakes in `:core:testing`.
- [ ] `I5` JVM tests: `assembleText`; `SecretKeys` stability + privacy-denylist check; fake-engine collect.

**Acceptance:** `:domain` stays stdlib+coroutines (guard); **no vendor name / wire term / sampling
param anywhere in `:domain`** (grep guard for `anthropic|openai|gemini|claude`); fakes compile; tests
green; no new deps; intent code untouched.

*(Execution prompt for this block is already drafted: `phase-5-block-I-prompt.md`.)*

---

## Block J — Secure secret storage (first on-device secret)

**Goal:** real `SecureSecretStore` impl (BYOK, per-provider), no ESP.
**Depends on:** I (port). Fork 1/8/9 fixed.

**New files:** `SecureSecretStoreImpl` in `:data:repository` (or `core/android` per Fork 8) over
Android Keystore AES-GCM (or Tink) + persisted ciphertext; `:app` DI provider; manifest/XML
network-security-config (HTTPS-only).

**Steps:**
- [ ] `J1` Keystore-backed AES-GCM impl: key in Keystore (hardware-backed where available), IV managed,
      ciphertext persisted; `get/put/remove → OperationResult`, never throws.
- [ ] `J2` Key-invalidation handling: `KeyPermanentlyInvalidatedException` (lockscreen/biometric change)
      → a defined "secret lost, re-enter" path, not a crash. StrongBox/API-28-vs-34 differences handled.
- [ ] `J3` DI provider in `:app`; network-security-config added.
- [ ] `J4` Tests: `androidTest` (real Keystore — Robolectric/JVM cannot fake Keystore) for round-trip +
      per-provider isolation + invalidation path; on-device run like the Phase-4 `MigrationTest`.

**Acceptance:** a stored key survives process restart; two providers' keys don't collide; no key in
logs; no ESP; `androidTest` green on device.

---

## Block K — Cloud engine (Anthropic adapter; SSE → Flow<AiChunk>)

**Goal:** `CloudGenerativeAiEngine` (Anthropic) in `:data:ai-cloud`, full failure mapping.
**Depends on:** I (contracts), J (key source) — but logic is MockEngine-testable against the port.
Fork 2 fixed.

**New files (`:data:ai-cloud`):** Anthropic adapter (Ktor SSE client), model-aware request builder,
SSE-event → `AiChunk` mapper, HTTP/error → `AiError` mapper, `stop_reason` → `AiStopReason` mapper;
`:app` DI; Ktor MockEngine test deps.

**Steps:**
- [ ] `K1` Ktor SSE client to the Messages endpoint; `x-api-key` + `anthropic-version`; key fetched via
      `SecureSecretStore`; HTTPS-only.
- [ ] `K2` Model-aware request body (capability map): omit sampling params for Opus 4.7/4.8; default
      model = Haiku 4.5 (dated string), configurable.
- [ ] `K3` SSE parse → `AiChunk.Text` deltas + terminal `Completed(stopReason)`; `refusal` → REFUSAL.
- [ ] `K4` Failure mapping → `AiError` (offline/timeout/401/429/5xx/4xx/unknown), emitted as terminal
      `AiChunk.Failed`; first-token + idle timeouts; cold-flow cancellation aborts the request.
- [ ] `K5` Tests: Ktor MockEngine — happy stream, mid-stream error, refusal, 401/429/timeout mapping,
      cancellation; against a `FakeSecureSecretStore`.

**Acceptance:** a scripted SSE stream maps to the right `AiChunk` sequence; every error path yields a
terminal `AiError`, never an uncaught throw; cancelling collection cancels the call; no Ktor/SSE term
leaks into `:domain`.

---

## Block L — Prompt/context builder + outbound privacy guard (pure)

**Goal:** minimal-context assembly with a positive allow-list.
**Depends on:** I. Fork 3 fixed. (Pure JVM — can run early/parallel.)

**Steps:**
- [ ] `L1` `PromptContextBuilder` (pure) → `AiRequest`; positive allow-list (only listed fields out).
- [ ] `L2` `AiRequestGuardTest`: no calendar/location/usage/raw-history reaches the request.
- [ ] `L3` Secret-not-in-logs guard test: key absent from any log/`toString` surface.

**Acceptance:** builder produces minimal requests; guards green; pure (`:domain` purity intact).

---

## Block M — Routing seam + static fallback

**Goal:** `GenerateReplyUseCase` + `StaticFallbackEngine` + ordered `GenerativeRouter`.
**Depends on:** I, K, L. Fork 4/6/7 fixed.

**Steps:**
- [ ] `M1` `StaticFallbackEngine` (canned reply, no network) implementing `GenerativeAiEngine`.
- [ ] `M2` `DefaultGenerativeRouter`: ordered cloud→static, selecting via `ConnectivityChecker` +
      `SecureSecretStore`; reserved ONNX slot (Ph6). Pure where possible (ports + engine list).
- [ ] `M3` `ConnectivityChecker` Android impl in `core/android` + DI.
- [ ] `M4` `GenerateReplyUseCase` (`Flow<AiChunk>`) in `:domain`; DI wires router as the engine.
- [ ] `M5` Tests: offline → static; online+key → cloud; online+no-key → static (or a clear key-needed
      signal); `HandleUserCommandUseCase` proven untouched.

**Acceptance:** routing picks the right engine per connectivity/key; ONNX can insert later with no
rewrite; matching pipeline unchanged.

---

## Block N — Assistant streaming UI + key entry + docs-sync/close

**Goal:** real `:feature:assistant` streaming screen, minimal inline key entry, phase close.
**Depends on:** I–M. Fork 5 fixed.

**Steps:**
- [ ] `N1` `AssistantViewModel`: `Flow<AiChunk>` (UI-collected) + `StateFlow<UiState<AssistantStatus>>`
      + `retry()`; adopts the ADR-3.1.4 `NavigationEvent` pattern.
- [ ] `N2` `AssistantScreen`: streaming render, error/retry, refusal rendering; no business logic.
- [ ] `N3` Minimal inline API-key entry (masked, never logged, off the launcher cold path); relocate to
      `feature/settings` later (Block-G wallpaper-button precedent).
- [ ] `N4` `AppNavHost` real assistant destination + safe-fallback (3.1.5).
- [ ] `N5` On-device acceptance: real streaming reply; offline → static fallback; cancel mid-stream;
      retry without restart.
- [ ] `N6` **Docs-sync + close:** `architecture.md` (AI pipeline as built), **`roadmap.md`** (fix the
      Phase-5 line that still says `EncryptedSharedPreferences` → Keystore/proxy via `SecureSecretStore`),
      `CLAUDE.md` status, ADRs in `decisions.md`. Phase 5 closed.

**Acceptance:** typing in the assistant streams a real reply; offline degrades to static fallback; the
launcher core remains fully offline; key never logged; docs match reality.

---

## Frozen / pushed forward (Phase 6+)

- ONNX NLU/embeddings as another `IntentMatcher` source + the reserved router slot → **Ph6**.
- WorkManager (model download/verify, suggestion pre-compute, cleanup) → **Ph6/9**.
- Voice / `SpeechInputSource` + context-suggestion pipeline → **Ph7**.
- Accessibility + its consent → **Ph8**.
- Backend proxy as the secret model (if not chosen in Fork 1) → swap behind the same port, later.
- OpenAI-compatible adapter → **planned fast-follow right after Block N** (validates the vendor-neutral
  seam); further providers later, behind the same port.
- `:feature:settings` module (future home of the relocated key entry) → when needed.
- Full Hilt→KSP migration → **Ph9**.

## Demoable milestones (like `open telegram` for Phase 3)

- **I:** contracts compile; a fake engine streams scripted chunks in a unit test.
- **J:** a BYOK key survives an app restart; two providers' keys stay isolated.
- **K:** a (mock) SSE stream renders as ordered text + a clean terminal; every error maps to `AiError`.
- **L:** the outbound request carries only allow-listed context; guards catch a leak attempt.
- **M:** airplane mode → static fallback reply; online+key → cloud reply; same use-case either way.
- **N:** type a question in the assistant → streamed answer; go offline → static fallback; cancel/retry
  work without restart.

## Tracking

- One execution prompt per block; **Block I first** (drafted).
- Record each block as an ADR in `decisions.md`; advance `CLAUDE.md` per block.
- Per-block gating: I green before J/K; K's MockEngine tests + J's `androidTest` before M; M before N.

## Agent model per block (which Claude to run Claude Code on)

Rule of thumb: **Opus 4.8** where the *abstraction is being decided* or correctness is subtle and
expensive to get wrong (contracts, crypto, streaming/cancellation). **Sonnet 4.6** where the block is
*execution against a pinned spec* (pure builders, DI wiring, mappers, UI patterns, tests, docs).
Independent of which model executes, do the **planning + diff review on Opus** — executing on Sonnet
and reviewing the result on Opus is the cost-effective default.

| Block | Model | Why |
|---|---|---|
| **I** — AI contracts | **Opus 4.8** *(Sonnet 4.6 acceptable)* | Foundation everything compiles against; multi-provider neutrality + refusal-as-stop-reason + terminal-failure-as-value decided here. Shapes are pinned in the prompt, so Sonnet *can* execute it — but a wrong contract is the most expensive mistake, so prefer Opus for round 1. |
| **J** — Secret storage | **Opus 4.8** | Keystore crypto + key-invalidation / StrongBox / API-28 edge cases. Security correctness, hard to test, costly to get wrong. Don't cheap out on crypto. |
| **K** — Cloud engine | **Opus 4.8** | SSE parsing, `Flow` cancellation/backpressure, first-token + idle timeouts, model-aware request body, full error→`AiError` taxonomy. The most intricate runtime block. |
| **L** — Prompt builder + guards | **Sonnet 4.6** | Pure builder + positive allow-list + guard tests. Well-specified, mechanical. |
| **M** — Routing + fallback | **Sonnet 4.6** *(review router on Opus)* | Bounded and well-specified; the one subtle spot is the router selection order (cloud-if-online+key → static, ONNX slot reserved) — review that part on Opus. |
| **N** — Assistant UI + close | **Sonnet 4.6** | Compose UI + DI wiring + docs-sync, all pattern-following from prior blocks. The streaming-collection VM is a known pattern; escalate to Opus only if on-device cancellation misbehaves. |

## Decisions confirmed (2026-06-24)

1. **P5-1 — key storage:** BYOK + on-device Keystore AES-GCM (scoped interim); no proxy now; reversible
   via the `SecureSecretStore` port; no dev key in the APK; ESP forbidden.
2. **P5-2 — model + adapters:** default Haiku 4.5, Opus 4.8 opt-in; **Anthropic-only closes Phase 5**,
   **OpenAI-compatible adapter is a planned fast-follow right after Block N** (validates the seam).
3. **P5-8 — secret impl module:** `:data:repository` (`…data.repository.security`).
4. **Key-entry surface:** minimal inline entry in the assistant now (masked, never logged, off the
   launcher cold path); relocate to `feature/settings` later (Block-G wallpaper-button precedent).
5. **First execution round:** **Block I only** (matches the Phase-4 "Block E only" gating). Prompt =
   `phase-5-block-I-prompt.md`.
