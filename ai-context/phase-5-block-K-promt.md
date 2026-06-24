# Phase 5 — Block K execution prompt: OpenAI-compatible cloud engine (SSE → Flow<AiChunk>)

> **Run this block on Opus 4.8** (SSE parsing, `Flow` cancellation/backpressure, first-token + idle
> timeouts, full error→`AiError` taxonomy — the most intricate runtime block; the wire format changed
> from the original plan, the difficulty did not).
> Scope = **Block K only.** Blocks I (+ provider-config addendum) and J are done. Do not start L/M/N.
> Prerequisite: Block J (`SecureSecretStore` impl) is green; Block I addendum (`AiProviderConfig` +
> `AiProviderConfigRepository`) exists in `:domain`.

## Role & context

Sidr Launcher, Phase 5, re-oriented to **OpenAI-compatible-first** (see `phase-5-plan.md` Core stance +
Fork P5-2). Block K builds the **first and primary** generative adapter: a `GenerativeAiEngine` impl
that streams from any **OpenAI-compatible** `chat/completions` endpoint (OpenRouter, Google's OpenAI
endpoint, Together, Groq, local Ollama / LM Studio, OpenAI itself). The whole point is **"the user
pastes any API (base URL + key) and picks any model (free-text string)"** — no per-vendor code, no
hardcoded model, no model catalog.

The engine reads its **base URL + free-text model** from `AiProviderConfigRepository` (Block I
addendum) and its **API key** from `SecureSecretStore` (Block J, Keystore-backed), and maps the wire
stream onto the **unchanged** Block-I `Flow<AiChunk>` / `AiError` / `AiStopReason` contracts.

## Pre-flight — verify before coding (repo-truth check; patch the plan at the point of any delta)

- [ ] **`:data:ai-cloud` is a Ktor-wired scaffold with zero `.kt` sources.** `build.gradle.kts`:
      `android.library` + `kotlin.android` + `kotlin.serialization`; deps `:core:common` + `:domain`,
      `coroutines.core`, `serialization.json`, `ktor.client.core/android/content-negotiation`,
      `ktor.serialization.kotlinx.json`. **No Hilt, no `:core:android` edge.** Confirms: add **no** new
      Ktor runtime deps; the engine is a plain class (Hilt-free module — Block G `core/android`
      precedent), `@Provides`-constructed in `:app`.
- [ ] **MockEngine is the only genuinely-new dependency** (test-only). It is **not** in the catalog yet
      — add `ktor-client-mock` (ref `ktor` = 3.0.1) to `gradle/libs.versions.toml` and as
      `testImplementation` in `:data:ai-cloud`. Add a JVM unit-test toolchain to that module
      (`testImplementation` junit4 + coroutines-test; the module currently has no test deps).
- [ ] **The `AiProviderConfigRepository` DataStore impl belongs in `:data:repository`, NOT
      `:data:ai-cloud`** (the plan's Block-K file list is loose here). Reason: every DataStore repo,
      `PreferencesKeys`, the `PreferencesMapper` `@Serializable`-DTO pattern, and the `createTestDataStore`
      helper live in `:data:repository`; `:data:ai-cloud` has no `datastore-preferences` dep and is the
      HTTP client. The Ktor engine depends only on the **domain port** `AiProviderConfigRepository`,
      injected from `:app`.
- [ ] **Provider config is NON-secret → shared `sidr_preferences` store** (Block E), key in Keystore
      (Block J, separate `sidr_secrets`). The new config key names (`ai_provider_id`,
      `ai_provider_base_url`, `ai_provider_model`, `ai_provider_display_name`) **pass the privacy
      denylist** (no `voice/query/search/location/calendar/history/conversation/message/transcript/
      secret/token/api` substring — verify each) → they ARE added to `PreferencesKeys` +
      `ALL_KEY_NAMES` so the guard keeps inventorying them. (The API key is never here.)
- [ ] **Domain contracts are present and exactly:** `AiRequest(messages: List<AiMessage>, system:
      String?, maxOutputTokens: Int, model: AiModelId?, stopSequences: List<String>)`,
      `AiMessage(role: AiRole, content)`, `AiRole{USER, ASSISTANT}`; `AiChunk.{Text(delta) /
      Completed(stopReason, usage?) / Failed(error)}`; `AiStopReason{COMPLETE, MAX_TOKENS, STOP_SEQUENCE,
      REFUSAL, OTHER}`; `AiUsage(inputTokens?, outputTokens?)`; the full `AiError` taxonomy;
      `GenerativeAiEngine.generate(AiRequest): Flow<AiChunk>`; `AiProviderConfig(providerId, baseUrl,
      modelId, displayName?)`; `SecretKeys.apiKey(provider)`. Fakes `FakeSecureSecretStore` +
      `FakeAiProviderConfigRepository` exist in `:core:testing`.

Run order: do this check, note deltas, then execute the steps.

## Hard invariants (do NOT violate)

- **`:domain` untouched.** No vendor name, no wire term (`chat/completions`, `delta`, `Bearer`, SSE),
  no sampling param, no Ktor/serialization type anywhere in `:domain`. The grep guard
  `anthropic|openai|gemini|claude` over `domain/src/` **stays empty**.
- The engine implements the **existing** `GenerativeAiEngine` port unchanged. **Terminal failures are
  values, never throws:** every expected failure is emitted as a terminal `AiChunk.Failed(AiError)`
  and the flow then completes normally — the collector never sees a thrown expected error (the `Flow`
  analog of "`OperationResult`, never throw to UI"). `CancellationException` is **not** caught/converted
  — it propagates so collection-cancel aborts the Ktor request.
- **No key / raw prompt in logs, crash reports, analytics, or any `AiError`/exception `detail`.**
  `AiError.detail` is a short safe diagnostic only (status hint, parser note). No `Log.*`/`println` of
  the key, the Authorization header, the request body, or response text.
- **HTTPS-only.** App-level `network-security-config` forbids cleartext; the adapter additionally
  rejects a non-`https://` configured base URL (→ terminal `AiChunk.Failed`, no raw URL in `detail`).
- `:data:ai-cloud` stays **Hilt-free** and keeps **no `:core:android` edge**. No `feature → data` edge.
- Launcher core stays offline-capable: the engine/HttpClient are constructed lazily (only on the
  assistant surface, Block N) — Block K adds DI providers but nothing on the launcher cold path.
- **Add only the MockEngine test dep.** No new runtime deps; do **not** add `ktor-client-sse` — parse
  SSE manually over the response channel (keeps deps minimal and MockEngine-testable).
- `IntentMatcher` / `HandleUserCommandUseCase` / `feature/assistant` untouched. `IntentMatcher` ≠
  `GenerativeAiEngine`.

## Out of scope (built later — do not touch)

`PromptContextBuilder` + outbound allow-list guard (L) · `StaticFallbackEngine` + `GenerativeRouter`
impl + `ConnectivityChecker` Android impl + `GenerateReplyUseCase` (M) · assistant UI + provider-settings
form (N) · native Anthropic adapter (optional post-N fast-follow). **Do not pre-build the router or the
connectivity precheck** — Block K maps transport failures itself (see error taxonomy).

## Design decisions (bake these in)

1. **Wire shape — OpenAI `chat/completions`, streaming SSE.** `POST {baseUrl}/chat/completions`,
   header `Authorization: Bearer <key>`, `Content-Type: application/json`, `Accept: text/event-stream`.
   `{baseUrl}` is the user value (e.g. `https://openrouter.ai/api/v1`).
   **Sanitize all user-supplied strings at the engine boundary before use:** `.trim()` the base URL,
   the API key, and the model string. A trailing newline/space pasted into the key produces a 401
   that is painful to debug (some clients also reject illegal header chars) — trim it, but keep the
   trimmed value out of logs all the same.
   **Join the URL robustly:** strip exactly one trailing `/` from the trimmed base, then append
   `chat/completions`, so exactly one `/` sits between them and a user-entered `.../v1/` keeps its
   `/v1` segment. If you use Ktor `URLBuilder`, use `appendPathSegments("chat", "completions")` —
   **not** `path("chat/completions")`, which *replaces* the path and silently drops `/v1`.
   Reject a non-`https://` base URL (→ terminal `AiChunk.Failed`, no raw URL in `detail`).
2. **Minimal request body — no sampling.** Serialize only: `model` (the free-text `AiModelId.value`),
   `messages`, `max_tokens` (= `AiRequest.maxOutputTokens`), `stream: true`. **Omit
   `temperature`/`top_p`/`top_k`** by default (widest backend compatibility — some models 400 on them).
   `AiRequest.system` (when non-null) becomes a **leading** `{"role":"system", ...}` message;
   `USER`/`ASSISTANT` map to `"user"`/`"assistant"`. `stopSequences` (when non-empty) → `stop`.
   *(Optional: `stream_options:{"include_usage":true}` to populate `AiUsage`; keep it optional and out
   of the body when not needed.)* Request/response **DTOs are `@Serializable` and private to the
   adapter** — never domain types.
3. **No hardcoded model / catalog.** Model comes from `AiProviderConfigRepository.activeConfig()`.
   Provide a **light default model string** constant + a "prefer light/fast models on a phone" doc note
   for the Block-N UI hint; the `< 2000ms` first-token budget is **guidance**, not a pin. If a request
   carries `AiRequest.model`, it overrides the config model; else use the config model.
4. **SSE parse (manual).** Stream the response body as a channel (`response.bodyAsChannel()`) — **do
   not** use `bodyAsText()`/buffered receive (it buffers the whole body and defeats streaming +
   cancellation). Read line-by-line with non-blocking `readUTF8Line()` in a loop; `readUTF8Line()`
   returns `null` at end-of-stream, so the loop body is `val line = channel.readUTF8Line() ?: break`
   (don't rely on `isClosedForRead` alone). For each `data: <payload>` line: `payload == "[DONE]"`
   ends the stream; otherwise JSON-decode the chunk and emit `AiChunk.Text(choices[0].delta.content)`
   **only when content is non-null/non-empty** (the role-only first delta emits nothing).
   **Capture `choices[0].finish_reason` and any final `usage` even on a content-empty delta** — the
   terminal delta typically carries `finish_reason` with empty `content`, so capture it *before* you
   skip the empty-content emit (don't `continue` past it). Ignore blank lines and non-`data:` fields
   (`event:`/`id:`/`:` comments).
5. **Stop-reason mapping.** `finish_reason`: `"stop"` → `COMPLETE`; `"length"` → `MAX_TOKENS`;
   `"content_filter"` (or a `delta.refusal`) → `REFUSAL` (**success terminal**, emitted as
   `AiChunk.Completed(REFUSAL)`, never an `AiError`); anything else / unknown → `OTHER`. (OpenAI has no
   distinct stop-sequence code; `STOP_SEQUENCE` will rarely be inferable — `COMPLETE` is acceptable.)
   End of stream → exactly one terminal `AiChunk.Completed(stopReason, usage?)`.
6. **Error → `AiError` taxonomy (emitted as terminal `AiChunk.Failed`).** No key configured (empty
   from `SecureSecretStore`) → `MissingCredentials` (don't even open the socket). HTTP `401/403` →
   `Unauthorized`; `429` → `RateLimited(retryAfterMs from Retry-After)` (parse both forms: delta-seconds
   **and** an HTTP-date; fall back to `null` if absent/unparseable, never throw on a bad header); `5xx` →
   `ServerError(statusCode)`; other `4xx` → `InvalidRequest(safeDetail)`; connect/transport
   `IOException` → `Network(safeDetail)`; `UnknownHostException`/no-route → `Offline`; first-token or
   idle-between-chunks deadline exceeded → `Timeout`; anything else → `Unknown(safeDetail)`. A `get`
   from `SecureSecretStore` returning `Failure` → treat as no usable key → `MissingCredentials`.
   *(The explicit pre-request connectivity check is Block M's `ConnectivityChecker`; K maps transport
   failures only.)*
7. **Timeouts + cancellation.** Enforce a **first-token deadline** (tied to the 2000ms budget,
   generous default e.g. ~15s for slow models — make it a constructor/config constant, documented as
   guidance) and an **idle-between-chunks** deadline → `AiError.Timeout`. Implement these with
   `withTimeoutOrNull` around the *first line read* and around *each subsequent read* — **not** as a
   whole-request timeout. **Do NOT set Ktor `HttpTimeout.requestTimeoutMillis` on this streaming call**
   — it would abort a long but legitimate stream; only connect/socket timeouts are safe here
   (decision 8). Build the stream as a cold `flow {}` running `client.preparePost(...).execute { ... }`;
   read the channel and `emit` **inside** the flow builder, push I/O off-thread with
   `.flowOn(ioDispatcher)` — never wrap `emit` in a nested `withContext`/`launch` (it violates the
   flow context-preservation invariant and throws). Collection-cancel cancels the flow coroutine,
   which aborts the in-flight `execute` (Ktor request cancelled).
   **Cancellation must propagate, not be swallowed:** `CancellationException` is **not** converted to
   `AiChunk.Failed`. If you wrap the call in a `try/catch` to map network errors, re-throw cancellation
   *before* any generic handler — first line of the catch: `if (e is CancellationException) throw e`
   (or `currentCoroutineContext().ensureActive()`), and prefer catching narrow types (`IOException`,
   specific Ktor exceptions) over a blanket `catch (e: Exception)`.
8. **DI in `:app` (Hilt-free data module).** A `@Provides` for the Ktor `HttpClient` (Android engine,
   `HttpTimeout` for **connect/socket only — no `requestTimeoutMillis`** on a streaming client; JSON
   content-negotiation as needed) and a `@Provides` constructing
   the engine from `HttpClient` + `SecureSecretStore` + `AiProviderConfigRepository` + `@IoDispatcher`;
   `@Binds`/provider exposing it as `GenerativeAiEngine` (qualify if needed so Block M's router can
   later compose it). The config-repo impl (in `:data:repository`) is bound in the existing
   `PersistenceBindsModule` (+ a `@Provides`-free `@Binds`), reusing the shared `sidr_preferences`
   DataStore. Split `@Provides`/`@Binds` per the recurring Hilt rule.
9. **`network-security-config` (app-level).** `app/src/main/res/xml/network_security_config.xml` with
   `cleartextTrafficPermitted="false"`; reference it from the `:app` manifest
   (`android:networkSecurityConfig`, and keep `usesCleartextTraffic` false/absent). HTTPS-only egress.

## Steps

- [ ] `K0` Catalog: add `ktor-client-mock` (ref `ktor`). `:data:ai-cloud` build: add `testImplementation`
      junit4 + coroutines-test + `ktor.client.mock` + `project(":core:testing")`. (No runtime deps added.)
- [ ] `K1` `AiProviderConfigRepository` **DataStore impl** in `:data:repository`
      (`…data.repository.ai` or extend `…preferences`): over the shared `sidr_preferences`
      `DataStore<Preferences>` + `@IoDispatcher`; `activeConfig(): Flow<AiProviderConfig?>` (null until
      all required keys present), `setActiveConfig → OperationResult`, `clearActiveConfig →
      OperationResult`; reads fall back on `IOException`, writes catch → `OperationError`, never throw.
      Add the 4 config keys to `PreferencesKeys` + `ALL_KEY_NAMES`. Bind in `PersistenceBindsModule`.
- [ ] `K2` Ktor engine `OpenAiCompatibleGenerativeAiEngine` (name it provider-neutrally — it's the
      OpenAI-*compatible* adapter, not "OpenAI") in `:data:ai-cloud`: reads base URL + model from
      `AiProviderConfigRepository`, key from `SecureSecretStore`; rejects non-`https://`; builds the
      minimal request; `Authorization: Bearer`. Private `@Serializable` request/response DTOs.
- [ ] `K3` Manual SSE parse → `AiChunk.Text` deltas; terminal `data: [DONE]` / end-of-body →
      `AiChunk.Completed(stopReason, usage?)`; `finish_reason`/`refusal` → `AiStopReason` per decision 5.
- [ ] `K4` Full failure mapping → `AiError` (decision 6), each emitted as terminal `AiChunk.Failed`;
      first-token + idle timeouts → `Timeout`; cold-flow cancellation aborts the request; no expected
      throw reaches the collector.
- [ ] `K5` DI in `:app`: `HttpClient` provider + engine provider/bind as `GenerativeAiEngine` (split
      modules); `network-security-config` XML + manifest wiring.
- [ ] `K6` Tests + verification + ADR + advance `CLAUDE.md` to Block K.

## Tests (K6)

- **`:data:ai-cloud` JVM, Ktor MockEngine** against `FakeSecureSecretStore` + `FakeAiProviderConfigRepository`:
  - happy stream: scripted `data:` deltas (content split across several) + a trailing
    **content-empty delta carrying `finish_reason:"stop"`** + `[DONE]` → ordered `AiChunk.Text…`
    then exactly one `Completed(COMPLETE)` (proves `finish_reason` is captured off the empty delta);
    `AiChunks.assembleText` reconstructs the message.
  - request assertion: captured request hits `{baseUrl}/chat/completions`, has `Authorization: Bearer`,
    body carries `model`/`messages`/`max_tokens`/`stream:true` and **no** `temperature`/`top_p`/`top_k`;
    `system` is the leading message; the free-text model string is sent verbatim.
  - refusal: `finish_reason:"content_filter"` (or `delta.refusal`) → `Completed(REFUSAL)`, **not** a
    `Failed`.
  - mid-stream error + mappings: `401/403→Unauthorized`, `429`+`Retry-After`→`RateLimited(ms)`,
    `5xx→ServerError`, other `4xx→InvalidRequest`, transport `IOException→Network`,
    `UnknownHost→Offline`, deadline→`Timeout` — each a terminal `AiChunk.Failed`, never a throw.
  - missing key: empty `SecureSecretStore` → `MissingCredentials` with no socket opened.
  - non-`https://` base URL → terminal `Failed` (no raw URL in `detail`).
  - cancellation: cancelling collection mid-stream cancels the call. The MockEngine must return a
    **suspending, segmented `ByteReadChannel`** (delivers bytes in chunks and suspends between them) —
    a fully-formed string response tests parsing but not streaming/cancellation. Assert the `execute`
    block / request was aborted (e.g. the channel stops being read after cancel; no terminal `Failed`
    is emitted from a swallowed `CancellationException`).
- **`:data:repository` JVM** (`createTestDataStore`, pure JVM): `AiProviderConfigRepositoryImpl`
  round-trip (null until configured; set→read; clear→null; survives simulated restart). Re-run
  `PrivacyInventoryGuardTest` green with the 4 new keys.

## Verification (run and paste actual output)

```bash
./gradlew :data:ai-cloud:testDebugUnitTest                       # MockEngine stream/error/cancel suite
./gradlew :data:repository:testDebugUnitTest                     # config-repo round-trip + privacy guard
./gradlew assembleDebug                                          # full Hilt graph + network-security-config
./gradlew testDebugUnitTest --rerun-tasks                        # full JVM regression
grep -rni "anthropic\|openai\|gemini\|claude" domain/src/        # empty (vendor-neutral domain)
grep -rn "import android\|io.ktor\|kotlinx.serialization" domain/src/   # empty (domain purity)
./gradlew :domain:dependencies --configuration compileClasspath  # stdlib + coroutines ONLY
grep -rniE "Log\.|println" data/ai-cloud/src/main/                # no key/prompt/body logging
# confirm only ktor-client-mock was added to the catalog (no new runtime dep); :data:ai-cloud has no Hilt edge
```

## Acceptance criteria

- A scripted SSE stream maps to the correct ordered `AiChunk` sequence ending in a single terminal
  `Completed`/`Failed`; `refusal` is a `Completed(REFUSAL)`, not an error.
- The outbound request targets the **configured base URL** + **free-text model** with `Bearer` auth and
  **no sampling params**; `system` is the leading message.
- Every error path yields a terminal `AiError` (`Unauthorized/RateLimited/ServerError/InvalidRequest/
  Network/Offline/Timeout/MissingCredentials/Unknown`), never an uncaught throw; cancelling collection
  cancels the call.
- Non-`https://` base URL is rejected; `network-security-config` forbids cleartext; no key/prompt in
  logs or `AiError.detail`.
- `AiProviderConfigRepository` DataStore impl round-trips and the 4 config keys keep
  `PrivacyInventoryGuardTest` green (inventoried, denylist-clean); the API key is **not** in DataStore.
- `:domain` untouched and vendor-neutral; `:data:ai-cloud` Hilt-free, no `:core:android` edge, only the
  MockEngine test dep added; `feature/*` + `IntentMatcher` + `HandleUserCommandUseCase` untouched.
- ADR `Block K complete` in `decisions.md` (wire shape, input sanitization + robust URL joining,
  minimal-body/no-sampling, free-text model, manual-SSE choice + no `ktor-client-sse`, streaming
  timeout model (per-read deadlines, no `requestTimeoutMillis`), cancellation-propagation rule,
  stop-reason + error taxonomy, config-impl-in-`:data:repository`
  correction, timeouts/cancellation, network-security-config, verification output); `CLAUDE.md` advanced
  to Block K; check off Block K in `phase-5-plan.md`. Do not touch L–N artifacts. **Next = Block M**
  (router + static fallback; L may run earlier/parallel as pure JVM).

## Agent model

**Opus 4.8** — streaming/cancellation/timeout correctness and the error taxonomy are subtle and costly
to get wrong. Plan + diff review on Opus regardless of executor.