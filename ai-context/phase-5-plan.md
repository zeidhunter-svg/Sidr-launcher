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

The generative pipeline is **provider-neutral by construction**. `:domain` names no vendor, no wire
format, no sampling params. The product end-goal is that **the user pastes any API (base URL + key)
and picks any model (a free-text model string)** — no per-vendor code. The lingua franca that
delivers this is the **OpenAI-compatible** endpoint (OpenRouter, Google's OpenAI endpoint, Together,
Groq, local Ollama / LM Studio, OpenAI itself).

So the OpenAI-compatible adapter is the **first and primary** adapter — it is **Block K**. A native
Anthropic adapter (its own `x-api-key` / SSE shape) is an **optional fast-follow after Block N**, only
needed if a user wants to paste `sk-ant-…` directly against `api.anthropic.com`; otherwise Anthropic
models are already reachable today via an OpenAI-compatible provider like OpenRouter through the
Block-K adapter. Adding any further native provider later must require **zero `:domain` changes** —
only a new `GenerativeAiEngine` impl + a new `AiProviderId` + the provider config + a key entry.

## In scope (unfreezing what Phase 4 deferred)

- **Secrets:** `SecureSecretStore` port (domain) + first real on-device implementation (Fork 1 is
  un-deferred here). Per-provider keyed. The **API key stays in Keystore** (Block J).
- **Non-secret provider config:** active provider id + base URL + free-text model string, persisted
  in DataStore (Block E pattern). Domain contract `AiProviderConfig` + `AiProviderConfigRepository`
  (built in the Block I addendum, 2026-06-24); its DataStore impl lands in Block K. The key is **not**
  here — it lives in Keystore (`SecureSecretStore`).
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
K  Cloud engine (OpenAI-compatible) — SSE → Flow<AiChunk>; testable via Ktor MockEngine against the port
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

### Fork P5-2 — Provider/contract — **recommend: vendor-neutral port, OpenAI-compatible first adapter**
- Domain port `GenerativeAiEngine` is provider-neutral (Block I). Provider/model are opaque
  (`AiProviderId`/`AiModelId`).
- First adapter: **OpenAI-compatible** (Block K) — `POST {baseUrl}/chat/completions`,
  `Authorization: Bearer <key>`, SSE streaming via `choices[].delta.content` + terminal
  `data: [DONE]`. One configurable base URL reaches OpenRouter / Google's OpenAI endpoint / Together /
  Groq / local Ollama / LM Studio / OpenAI itself. Provider lives behind the port.
- **No hardcoded model and no hardcoded model catalog.** The model is a **user-set free-text string**
  (`AiModelId`), read from `AiProviderConfigRepository`. The adapter sends a **minimal request body**
  (`messages`, `model`, `max_tokens`, `stream:true`) and **omits sampling params by default** for the
  widest backend compatibility (some models 400 on `temperature`/`top_p`/`top_k`).
- **Decided (2026-06-24, re-oriented):** ship a sensible **light** default model **string** + a UI
  hint "prefer light/fast models on a phone"; the `< 2000ms` first-token figure stays **guidance**,
  not a hard model pin. **The OpenAI-compatible adapter closes Phase 5 (Block K, first/primary); a
  native Anthropic adapter is an optional fast-follow after Block N** — only needed for pasting
  `sk-ant-…` directly against `api.anthropic.com`; Anthropic is otherwise reachable today via an
  OpenAI-compatible provider (e.g. OpenRouter). Verify live model strings at build time (they move).

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
- **Network security config** — XML + manifest, **no dependency**; HTTPS-only. It concerns the HTTP
  client, so it is added in **Block K** (not Block J); the user-configured base URL must be `https://`
  and cleartext egress is forbidden.
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
- [x] `I1` `domain.ai` contracts per the shapes above; refusal = stop reason, failures = terminal value.
- [x] `I2` `domain.security` generic per-provider secret store port.
- [x] `I3` `domain.connectivity` port.
- [x] `I4` Fakes in `:core:testing`.
- [x] `I5` JVM tests: `assembleText`; `SecretKeys` stability + privacy-denylist check; fake-engine collect.

**Status: Block I DONE (2026-06-24).** `domain.ai` (`AiProviderId`/`AiModelId`, `AiRequest`/`AiMessage`/
`AiRole`, `AiChunk`+`AiStopReason`(refusal=success terminal)+`AiUsage`, `AiError`, `GenerativeAiEngine`+
`GenerativeRouter`, `AiChunks.assembleText`), `domain.security` (`SecureSecretStore`+`SecretKeys`),
`domain.connectivity` (`ConnectivityChecker`); 3 fakes; 10 JVM tests green; grep guard empty; no new deps.
**Addendum:** added `AiProviderConfig` + `AiProviderConfigRepository` + fake (DataStore impl deferred to
Block K). See decisions.md "ADR Block I".

**Acceptance:** `:domain` stays stdlib+coroutines (guard); **no vendor name / wire term / sampling
param anywhere in `:domain`** (grep guard for `anthropic|openai|gemini|claude`); fakes compile; tests
green; no new deps; intent code untouched.

*(Execution prompt for this block is already drafted: `phase-5-block-I-prompt.md`.)*

**Addendum (2026-06-24, additive — Block I files unmodified):** added the pure provider-config
contract the "any API" goal needs — `AiProviderConfig` (`providerId`, `baseUrl`, `modelId`,
`displayName?`) + `AiProviderConfigRepository` (`activeConfig(): Flow<AiProviderConfig?>` /
`setActiveConfig → OperationResult` / `clearActiveConfig → OperationResult`) in `…domain.ai`. Single
active config for Phase 5 (multi-config later); reads `Flow`, writes `OperationResult`, never throws.
The key is **not** here — it stays in `SecureSecretStore`. New fake
`FakeAiProviderConfigRepository` in `:core:testing` + a round-trip JVM test. **The DataStore impl is
NOT built here — it lands in Block K.** Same purity rules as Block I (no vendor / "openai-compatible"
literal in `:domain`; the grep guard stays empty).

---

## Block J — Secure secret storage (first on-device secret)

**Goal:** real `SecureSecretStore` impl (BYOK, per-provider), no ESP.
**Depends on:** I (port). Fork 1/8/9 fixed.

**New files:** `SecureSecretStoreImpl` in `:data:repository` (or `core/android` per Fork 8) over
Android Keystore AES-GCM (or Tink) + persisted ciphertext; `:app` DI provider. (Network-security-config
is **not** here — it concerns the HTTP client and lands in Block K.)

**Steps:**
- [x] `J1` Keystore-backed AES-GCM impl: key in Keystore (hardware-backed where available), IV managed,
      ciphertext persisted; `get/put/remove → OperationResult`, never throws.
- [x] `J2` Key-invalidation handling: `KeyPermanentlyInvalidatedException` (lockscreen/biometric change)
      → a defined "secret lost, re-enter" path, not a crash. StrongBox/API-28-vs-34 differences handled.
- [x] `J3` DI provider in `:app`. (Network-security-config is added in Block K, with the HTTP client.)
- [x] `J4` Tests: `androidTest` (real Keystore — Robolectric/JVM cannot fake Keystore) for round-trip +
      per-provider isolation + invalidation path; on-device run like the Phase-4 `MigrationTest`.

**Status: Block J DONE (2026-06-24).** `:data.repository.security`: `SecretCipher`+`EncryptedBlob` seam,
`KeystoreSecretCipher` (AES-256-GCM in `AndroidKeyStore`, StrongBox-with-fallback), `SecureSecretStoreImpl`
over a **dedicated `sidr_secrets` DataStore** (`@SecretsDataStore`, so credential keys never enter the
privacy-guarded `ALL_KEY_NAMES`); never throws, `CancellationException` re-thrown. DI split modules in
`:app`. `FakeSecretCipher` + **9 pure-JVM tests** green. ⚠️ `SecretStoreInstrumentedTest` (3 tests, real
Keystore) **compiles but the on-device run is still pending** on the SM-A325F — it must run before Block
N's on-device acceptance. ESP/security-crypto absent; no secret logged. See decisions.md "ADR Block J".

**Acceptance:** a stored key survives process restart; two providers' keys don't collide; no key in
logs; no ESP; `androidTest` green on device.

---

## Block K — Cloud engine — OpenAI-compatible adapter (configurable base URL + key + model)

**Goal:** `CloudGenerativeAiEngine` (OpenAI-compatible) in `:data:ai-cloud`, full failure mapping,
driven entirely by user config (base URL + free-text model) + a Keystore key.
**Depends on:** I (contracts + the addendum's `AiProviderConfig`), J (key source) — but logic is
MockEngine-testable against the ports. Fork 2 fixed.

**New files (`:data:ai-cloud`):** OpenAI-compatible adapter (Ktor SSE client), minimal request builder,
SSE-`delta` → `AiChunk` mapper, HTTP/error → `AiError` mapper, `finish_reason` → `AiStopReason` mapper;
`AiProviderConfigRepository` **DataStore impl** (the Block-I-addendum contract — active provider id +
base URL + model string; Block E pattern); `:app` DI; network-security-config (XML + manifest,
HTTPS-only); Ktor MockEngine test deps.

**Steps:**
- [x] `K1` Ktor SSE client to `POST {baseUrl}/chat/completions`; `Authorization: Bearer <key>`; base URL
      + model read from `AiProviderConfigRepository`, key fetched via `SecureSecretStore`. **HTTPS-only**
      (reject a non-`https://` base URL); network-security-config enforces no cleartext egress.
- [x] `K2` **Minimal request body**: `messages`, `model` (the free-text `AiModelId`), `max_tokens`,
      `stream:true`. **Omit sampling params by default** (`temperature`/`top_p`/`top_k`) for widest
      backend compatibility. No hardcoded model / catalog — model comes from config; ship a light
      default string + a "prefer light/fast models on a phone" UI hint (the `< 2000ms` first-token
      budget is guidance, not a hard pin).
- [x] `K3` SSE parse → `AiChunk.Text` from `choices[].delta.content` deltas + terminal
      `Completed(stopReason)` from `finish_reason`; terminal `data: [DONE]` ends the stream;
      a `refusal`/content-filter finish → REFUSAL (success terminal).
- [x] `K4` Failure mapping → `AiError` (offline/timeout/401/429/5xx/4xx/unknown), emitted as terminal
      `AiChunk.Failed`; first-token + idle timeouts; cold-flow cancellation aborts the request.
- [x] `K5` Tests: Ktor MockEngine — happy stream, mid-stream error, refusal, 401/429/timeout mapping,
      cancellation; against `FakeSecureSecretStore` + `FakeAiProviderConfigRepository`. DataStore-impl
      round-trip test for `AiProviderConfigRepository`.

**Status: Block K DONE (2026-06-24).** `OpenAiCompatibleGenerativeAiEngine` in `:data:ai-cloud`
(manual SSE, no `ktor-client-sse`); `AiProviderConfigRepositoryImpl` in **`:data:repository`** over the
shared `sidr_preferences` store (+ 4 `ai_provider_*` keys, privacy-guard-clean); `@CloudEngine` engine
+ `HttpClient` providers + `network_security_config` in `:app`. 20 MockEngine tests + 4 config-repo
tests + full regression green; domain stays vendor-neutral/pure; only `ktor-client-mock` added (test).
See decisions.md "ADR Block K". **Next = Block M.**

**Acceptance:** a scripted SSE stream maps to the right `AiChunk` sequence; the request targets the
configured base URL + free-text model with no sampling params; every error path yields a terminal
`AiError`, never an uncaught throw; cancelling collection cancels the call; non-`https://` base URL is
rejected; no Ktor/SSE term leaks into `:domain`.

*(Execution prompt for this block is drafted: `phase-5-block-K-prompt.md`. Two repo-truth corrections
baked in there: the `AiProviderConfigRepository` DataStore impl lands in **`:data:repository`** (not
`:data:ai-cloud`, which is the Hilt-free HTTP client with no DataStore dep); SSE is parsed **manually**
over the response channel so **Ktor MockEngine stays the only new dep** — no `ktor-client-sse`.)*

---

## Block L — Prompt/context builder + outbound privacy guard (pure)

**Goal:** minimal-context assembly with a positive allow-list.
**Depends on:** I. Fork 3 fixed. (Pure JVM — can run early/parallel.)

**Steps:**
- [x] `L1` `PromptContextBuilder` (pure) → `AiRequest`; positive allow-list (only listed fields out).
- [x] `L2` `AiRequestGuardTest`: no calendar/location/usage/raw-history reaches the request.
- [x] `L3` Secret-not-in-logs guard test: key absent from any log/`toString` surface.

**Status: Block L DONE (2026-06-27).** Pure `PromptContextBuilder` (`build(userCommand)` only →
minimal `AiRequest`: one verbatim `USER` message + static `DEFAULT_SYSTEM_PROMPT` + `maxOutputTokens=512`,
`model=null`) + `OutboundContextPolicy` (positive allow-list `{USER_COMMAND, STATIC_SYSTEM_PROMPT,
GENERATION_LIMITS}` + `FORBIDDEN_CONTEXT_TERMS`/`CREDENTIAL_TERMS` + hand-synced `OUTBOUND_FIELD_NAMES`/
`AIERROR_FIELD_NAMES`) in `…domain.ai`; 11 reflection-free JVM tests (`AiRequestGuardTest` 5 +
`OutboundSecretLeakGuardTest` 6) green, 91 domain total. Denylist scanned over **static text + field
inventories, never user content**; `token` excluded (collides with `maxOutputTokens`); credential terms
scanned over field-name inventories (not rendered `toString`) to avoid the `MissingCredentials`/"credential"
vacuous collision. `:domain` stays stdlib+coroutines/vendor-neutral; no new deps. See decisions.md
"ADR Block L". **Next = Block M.**

**Acceptance:** builder produces minimal requests; guards green; pure (`:domain` purity intact).

*(Execution prompt for this block is drafted: `phase-5-block-L-prompt.md`. Pure JVM in `:domain`,
depends only on Block I — runnable in parallel with K/M. Key precision baked in: the privacy denylist is
scanned over **static text + a hand-synced field inventory, never over the user's command**; guards are
**reflection-free** (no `kotlin-reflect`), mirroring the Phase-4 hand-synced guards.)*

---

## Block M — Routing seam + static fallback

**Goal:** `GenerateReplyUseCase` + `StaticFallbackEngine` + ordered `GenerativeRouter` over the
OpenAI-compatible cloud engine + static fallback.
**Depends on:** I, K, L. Fork 4/6/7 fixed.

**Steps:**
- [x] `M1` `StaticFallbackEngine` (canned reply, no network) implementing `GenerativeAiEngine`.
- [x] `M2` `DefaultGenerativeRouter`: ordered **OpenAI-compatible cloud → static**, selecting via
      `ConnectivityChecker` + `SecureSecretStore` (+ provider config present); reserved ONNX slot (Ph6).
      Pure where possible (ports + engine list).
- [x] `M3` `ConnectivityChecker` Android impl in `core/android` + DI.
- [x] `M4` `GenerateReplyUseCase` (`Flow<AiChunk>`) in `:domain`; DI wires router as the engine.
- [x] `M5` Tests: offline → static; online+key → cloud; online+no-key → static (or a clear key-needed
      signal); `HandleUserCommandUseCase` proven untouched.

**Status: Block M DONE (2026-06-27).** `StaticFallbackEngine` + `DefaultGenerativeRouter` in
`…data.repository.ai`; `AndroidConnectivityChecker` in `core/android/connectivity/`; `GenerateReplyUseCase`
in `…domain.ai`; `@FallbackEngine` + `ConnectivityModule` + `GenerationProvidesModule` in `:app`;
`ACCESS_NETWORK_STATE` in manifest; `core/android` gains `coroutines.core`. 7 router tests + 6 use-case
tests green; full JVM regression green; `assembleDebug` green (single unqualified engine binding);
domain pure; no data→data edge. See decisions.md "ADR Block M". **Next = Block N.**

**Acceptance:** routing picks the right engine per connectivity/key; ONNX can insert later with no
rewrite; matching pipeline unchanged.

---

## Block N — Assistant streaming UI + key entry + docs-sync/close

**Status: Block N DONE (2026-06-27). Phase 5 CLOSED.** Code + JVM (262 tests) green; `assembleDebug`
green; on-device acceptance (N5) pending SM-A325F device run (no device in execution environment).

**Goal:** real `:feature:assistant` streaming screen, minimal inline provider-settings form (base URL
+ key + model), phase close.
**Depends on:** I–M. Fork 5 fixed.

**Steps:**
- [x] `N1` `AssistantViewModel`: `Flow<AiChunk>` collected in `viewModelScope` + `StateFlow<AssistantUiState>`
      + `retry()` (latest-wins); adopts the ADR-3.1.4 `NavigationEvent` pattern.
- [x] `N2` `AssistantScreen`: streaming render, error/retry, refusal rendering; no business logic.
- [x] `N3` Minimal inline **provider-settings** form on the assistant screen: **base URL + API key +
      model string** (key field masked, never logged, off the launcher cold path). Config saved via
      `AiProviderConfigRepository`; key saved via `SecureSecretStore`. Relocate to `feature/settings`
      later (Block-G wallpaper-button precedent).
- [x] `N4` `AppNavHost` real assistant destination + `LaunchedEffect(navigationEvents)` + safe-fallback (3.1.5).
- [ ] `N5` On-device acceptance: real streaming reply; offline → static fallback; cancel mid-stream;
      retry without restart. **PENDING device run on SM-A325F** (no device in execution environment).
- [x] `N6` **Docs-sync + close:** `architecture.md` (AI pipeline as built + SecureSecretStore built),
      **`roadmap.md`** (fixed Phase-5 line: ESP → Keystore AES-GCM via `SecureSecretStore`),
      `CLAUDE.md` status (Phase 5 complete), ADRs in `decisions.md`. Phase 5 closed.

**Acceptance:** typing in the assistant streams a real reply; offline degrades to static fallback; the
launcher core remains fully offline; key never logged; docs match reality.

*(Execution prompt for this block is drafted: `phase-5-block-N-prompt.md`. Precision baked in:
`:feature:assistant` gains Hilt (copy the `permission_education` build); the VM's 3 deps are domain ports
already bound by J/K/M so **no new `:app` DI**; the stream is **collected in `viewModelScope`** (resolves
the Fork-P5-5 "UI-collected vs retry()-latest-wins vs config-change" tension — survives rotation, aborts
on screen-leave, `retry()` cancels in-flight) with **refusal = success terminal**; the masked key goes
straight to `SecureSecretStore` and is **never displayed back/logged/in state**; `providerId` is
host-derived; `MissingCredentials`/`Unauthorized` → a "set up provider" CTA, not a dead retry. N6 fixes
`roadmap.md:49` ESP→Keystore. **Closes Phase 5.** Review the VM streaming/cancellation design on Opus.)*

---

## Frozen / pushed forward (Phase 6+)

- ONNX NLU/embeddings as another `IntentMatcher` source + the reserved router slot → **Ph6**.
- WorkManager (model download/verify, suggestion pre-compute, cleanup) → **Ph6/9**.
- Voice / `SpeechInputSource` + context-suggestion pipeline → **Ph7**.
- Accessibility + its consent → **Ph8**.
- Backend proxy as the secret model (if not chosen in Fork 1) → swap behind the same port, later.
- Native Anthropic adapter (its own `x-api-key` / SSE shape) → **optional fast-follow after Block N**,
  behind the same `GenerativeAiEngine` port — only needed for pasting `sk-ant-…` directly against
  `api.anthropic.com` (Anthropic is otherwise reachable via an OpenAI-compatible provider in Block K).
  Additional native providers later, same port.
- `:feature:settings` module (future home of the relocated provider-settings form) → when needed.
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
| **K** — Cloud engine | **Opus 4.8** | SSE parsing (OpenAI-compatible `delta` + `[DONE]`), `Flow` cancellation/backpressure, first-token + idle timeouts, config-driven request, full error→`AiError` taxonomy. Still the most intricate runtime block — the wire format changed, the difficulty did not. |
| **L** — Prompt builder + guards | **Sonnet 4.6** | Pure builder + positive allow-list + guard tests. Well-specified, mechanical. |
| **M** — Routing + fallback | **Sonnet 4.6** *(review router on Opus)* | Bounded and well-specified; the one subtle spot is the router selection order (cloud-if-online+key → static, ONNX slot reserved) — review that part on Opus. |
| **N** — Assistant UI + close | **Sonnet 4.6** | Compose UI + DI wiring + docs-sync, all pattern-following from prior blocks. The streaming-collection VM is a known pattern; escalate to Opus only if on-device cancellation misbehaves. |

## Decisions confirmed (2026-06-24)

1. **P5-1 — key storage:** BYOK + on-device Keystore AES-GCM (scoped interim); no proxy now; reversible
   via the `SecureSecretStore` port; no dev key in the APK; ESP forbidden.
2. **P5-2 — model + adapters (re-oriented 2026-06-24):** **no hardcoded model / catalog** — model is a
   user-set free-text `AiModelId`; ship a light default string + a "prefer light/fast models" hint
   (`< 2000ms` first-token is guidance, not a pin). **OpenAI-compatible adapter is Block K, first and
   primary, and closes Phase 5**; native Anthropic is an **optional fast-follow after Block N** (only
   for pasting `sk-ant-…` directly; Anthropic is reachable via an OpenAI-compatible provider already).
3. **P5-8 — secret impl module:** `:data:repository` (`…data.repository.security`). Non-secret provider
   config (`AiProviderConfigRepository`) is DataStore-backed (Block E pattern), impl in Block K.
4. **Key-entry surface:** minimal inline **provider-settings** form in the assistant now (base URL + key
   + model; key masked, never logged, off the launcher cold path); config via `AiProviderConfigRepository`,
   key via `SecureSecretStore`; relocate to `feature/settings` later (Block-G wallpaper-button precedent).
5. **First execution round:** **Block I only** (matches the Phase-4 "Block E only" gating). Prompt =
   `phase-5-block-I-prompt.md`.
