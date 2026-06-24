# Phase 5 — Block M execution prompt: Routing seam + static fallback + GenerateReplyUseCase

> **Run this block on Sonnet 4.6**, but **review the router selection order on Opus** (the one subtle
> spot: cloud-iff-online+config+key → static, with the ONNX slot reserved ahead of cloud).
> Scope = **Block M only.** Depends on **I, K, L** (all must be green). Do not start N. Do not modify
> the Block-K cloud engine internals or the Block-L builder — M *composes* them.

## Role & context

Sidr Launcher, Phase 5. Block M assembles the generation seam the assistant feature (Block N) will call:
- `StaticFallbackEngine` — a canned, no-network `GenerativeAiEngine` (graceful degrade).
- `DefaultGenerativeRouter : GenerativeRouter` — an **ordered composite** that picks **cloud (if online
  + provider configured + key present) → static fallback**, with an explicit **reserved slot for Ph6
  ONNX ahead of cloud** so ONNX inserts later with no rewrite.
- `ConnectivityChecker` Android impl in `core/android` (the router's online check).
- `GenerateReplyUseCase` (pure `:domain`) — the single entry point the assistant VM uses; builds the
  request via the Block-L `PromptContextBuilder` and streams from the router.

Matching ≠ generation: **`HandleUserCommandUseCase` is untouched.** The existing
`SimpleCommand.OPEN_ASSISTANT → CommandOutcome.OpenAssistant → navigate(Routes.Assistant)` handoff (route
string stays in the VM, Block D) remains the entry into the assistant; generation is a *new* path.

## Pre-flight — verify before coding (repo-truth check)

- [ ] **Marker + ports exist:** `GenerativeRouter : GenerativeAiEngine` (Block I, KDoc says "impl lands
      in Block M"); `ConnectivityChecker { fun isOnline(): Boolean; val connectivity: Flow<Boolean> }`
      (Block I, `domain.connectivity`); `SecureSecretStore` + `SecretKeys.apiKey(provider)` (I/J);
      `AiProviderConfigRepository.activeConfig(): Flow<AiProviderConfig?>` (Block I addendum);
      `PromptContextBuilder.build(userCommand): AiRequest` (Block L).
- [ ] **Fakes exist in `:core:testing`:** `FakeGenerativeAiEngine` (records `lastRequest`, scripted
      chunks), `FakeConnectivityChecker(initiallyOnline)` (settable `online`), `FakeSecureSecretStore`
      (empty by default; `errorToReturn`), `FakeAiProviderConfigRepository` (null by default).
- [ ] **`core/android` precedent (Fork P5-7):** `AndroidPermissionChecker` is a **plain, Hilt-free**
      class constructed in `:app`'s `PermissionModule` with `@ApplicationContext`; `core/android` has
      **no test deps** and the glue is **not** unit-tested there (the port is faked in the consuming
      tests). Follow this exactly for `AndroidConnectivityChecker`.
- [ ] **`:app` manifest** declares `INTERNET` but **not** `ACCESS_NETWORK_STATE` → add it (normal,
      install-time permission; no runtime dialog).
- [ ] **Block K's cloud engine binding.** Confirm how Block K exposed the cloud `GenerativeAiEngine` in
      DI. The router needs it as **`@CloudEngine GenerativeAiEngine`**. If Block K left it **unqualified**,
      re-tag it `@CloudEngine` (an unqualified second `GenerativeAiEngine` binding will become a Hilt
      duplicate once the router is also a `GenerativeAiEngine`). Coordinate this one-line change.
- [ ] **Pure-collaborator DI precedent:** plain domain classes (`RuleBasedIntentMatcher`,
      `HandleUserCommandUseCase`) are `@Provides`-constructed in `:app` object modules (no `@Inject`).
      `StaticFallbackEngine` / `DefaultGenerativeRouter` / `GenerateReplyUseCase` follow this.

## Hard invariants (do NOT violate)

- **`:domain` stays pure** (stdlib + coroutines). `GenerateReplyUseCase` is the only new `:domain` file;
  it depends only on domain types (`GenerativeAiEngine` port + `PromptContextBuilder`). No Android/Ktor.
- **Implementations are NOT in `:domain`** (the "interfaces in domain, impls in data/*" rule). The router
  + static engine live in a `:data:*` module (see decision 1). `GenerateReplyUseCase` is a *use case*
  (domain-owned, like `HandleUserCommandUseCase`), so it stays in `:domain` — it holds no impl detail.
- **The router depends only on ports + injected `GenerativeAiEngine` instances** — **no data→data edge**
  (the cloud engine and the future ONNX engine are injected as `GenerativeAiEngine` from `:app`, never
  imported as concrete types). Keeps the seam vendor-/transport-neutral and lets ONNX (`:data:ai-local`)
  slot in with no module edge.
- **Terminal-failure-as-value preserved end to end.** The router/use-case never throw expected errors to
  the collector; they `emitAll` the chosen engine's `Flow<AiChunk>` (which already terminates with
  `Completed`/`Failed`). `CancellationException` propagates (cold flow; cancel aborts the underlying call).
- **Selection happens at collection time** (inside the cold `flow {}`), so a connectivity/key/config
  change between calls is respected (latest-wins) — not decided once at construction.
- **`HandleUserCommandUseCase` and the whole intent/matching pipeline are untouched.** `IntentMatcher` ≠
  `GenerativeAiEngine`. No `feature → data` edge. Launcher core stays offline (router/engines built lazily,
  only on the assistant path — Block N; M adds DI but nothing on the launcher cold path).
- **No key / raw prompt in logs.** The static reply is context-free and does not echo user content.

## Out of scope (built later — do not touch)

Assistant streaming UI + provider-settings form + `AssistantViewModel` (N) · Block-K engine internals ·
Block-L builder internals · ONNX engine (Ph6 — only the reserved slot is prepared here, no impl) ·
multi-turn history. Do not build the assistant VM or wire navigation — M stops at the use-case + DI.

## Design decisions (bake these in)

1. **Module placement — `:data:repository`** for `StaticFallbackEngine` + `DefaultGenerativeRouter`
   (Android-free pure classes, the `RuleBasedIntentMatcher` precedent). **Not `:data:ai-cloud`** — the
   static engine has no business pulling the Ktor module, and the router references only ports +
   injected engines. (`:data:ai-cloud` remains strictly the cloud adapter.) Qualifiers `@CloudEngine`
   / `@FallbackEngine` live where both `:app` and the router module see them — put them in
   `:data:repository` (or `core/common/di` next to `@IoDispatcher`); pick one and keep AI naming out of
   `core/common` if you can.
2. **`StaticFallbackEngine : GenerativeAiEngine`** — canned, no network, no key:
   ```kotlin
   override fun generate(request: AiRequest): Flow<AiChunk> = flow {
       emit(AiChunk.Text(STATIC_REPLY))                 // optionally split into a few Text chunks
       emit(AiChunk.Completed(AiStopReason.COMPLETE))
   }
   ```
   `STATIC_REPLY` is a short, static, **context-free** conversational message (e.g. "I can't reach an AI
   service right now — check your connection or set up a provider in settings."). It must **not** echo
   `request` content. No `AiChunk.Failed` (fallback is always a graceful success terminal).
3. **`DefaultGenerativeRouter`** (the subtle part — review on Opus):
   ```kotlin
   class DefaultGenerativeRouter(
       private val cloud: GenerativeAiEngine,        // @CloudEngine (Block K), injected as a port
       private val fallback: GenerativeAiEngine,     // @FallbackEngine (StaticFallbackEngine)
       private val connectivity: ConnectivityChecker,
       private val secretStore: SecureSecretStore,
       private val configRepo: AiProviderConfigRepository,
   ) : GenerativeRouter {
       override fun generate(request: AiRequest): Flow<AiChunk> = flow {
           emitAll(selectEngine().generate(request))
       }
       private suspend fun selectEngine(): GenerativeAiEngine {
           // Reserved Ph6 ONNX slot sits HERE, ahead of cloud (eligible iff a local model is ready).
           if (canUseCloud()) return cloud
           return fallback
       }
       private suspend fun canUseCloud(): Boolean {
           if (!connectivity.isOnline()) return false
           val config = configRepo.activeConfig().first() ?: return false
           val key = (secretStore.get(SecretKeys.apiKey(config.providerId)) as? OperationResult.Success)?.value
           return !key.isNullOrBlank()
       }
   }
   ```
   - **Order:** ONNX (reserved, absent) → cloud (iff online **and** config present **and** non-blank key)
     → static fallback (always eligible, terminal). Structure `selectEngine()` so inserting ONNX is one
     new check ahead of cloud with **no** edit to the cloud/static branches (Fork P5-6 "no rewrite").
     Optionally model it as an ordered `List` of (engine, `suspend isEligible()`) and pick the first
     eligible — document whichever you choose.
   - **No-key / no-config / offline → static**, never a cloud call that would just `MissingCredentials`.
     A `secretStore.get` returning `Failure` is treated as **no usable key → static** (not a crash).
   - The router reads config to get `providerId` for the key lookup; that double-read with the cloud
     engine (which re-reads at request time) is intentional — it is the eligibility gate, documented.
4. **`GenerateReplyUseCase` (pure `:domain`)** — the assistant's single entry point:
   ```kotlin
   class GenerateReplyUseCase(
       private val engine: GenerativeAiEngine,         // the router at runtime (unqualified binding)
       private val promptContextBuilder: PromptContextBuilder,
   ) {
       fun generate(userCommand: String): Flow<AiChunk> =
           engine.generate(promptContextBuilder.build(userCommand))
   }
   ```
   Depends on the **port**, not the router type — so DI provides the **unqualified** `GenerativeAiEngine`
   = the router (cloud/fallback are qualified, so the router is the only unqualified binding → no Hilt
   ambiguity). Keep the use case this thin; routing/building live in their own units.
5. **`AndroidConnectivityChecker`** in `core/android` (Hilt-free, Fork P5-7): `isOnline()` via
   `ConnectivityManager.activeNetwork` + `getNetworkCapabilities(...).hasCapability(NET_CAPABILITY_INTERNET)`
   (and `NET_CAPABILITY_VALIDATED` where available); `connectivity: Flow<Boolean>` via `callbackFlow`
   over `registerDefaultNetworkCallback` (emit current state on subscription, then on
   `onAvailable`/`onLost`/`onCapabilitiesChanged`; `awaitClose { unregister }`; `conflate()` /
   `distinctUntilChanged()`). Plain class taking `Context`. **Not unit-tested in `core/android`** (no
   test deps there — the `AndroidPermissionChecker` precedent); the router logic is fully covered via
   `FakeConnectivityChecker`. `core/android` gains the `coroutines.core` dep if not already present
   (for `callbackFlow`/`Flow`).
6. **DI in `:app`:**
   - `ConnectivityModule` (object): `@Provides @Singleton ConnectivityChecker =
     AndroidConnectivityChecker(context)` (mirror `PermissionModule`).
   - `GenerationProvidesModule` (object): `@Provides @Singleton @FallbackEngine GenerativeAiEngine =
     StaticFallbackEngine()`; `@Provides @Singleton GenerativeRouter = DefaultGenerativeRouter(cloud =
     @CloudEngine, fallback = @FallbackEngine, connectivity, secretStore, configRepo)`; `@Provides
     @Singleton GenerativeAiEngine = <the GenerativeRouter>` (the single unqualified binding the use case
     consumes); `@Provides @Singleton PromptContextBuilder = PromptContextBuilder()`; `@Provides
     @Singleton GenerateReplyUseCase(engine, promptContextBuilder)`. Ensure the Block-K cloud engine is
     `@CloudEngine`-qualified (re-tag if needed — see pre-flight).
   - Manifest: add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.

## Steps

- [ ] `M1` `StaticFallbackEngine` (decision 2) in `:data:repository`.
- [ ] `M2` `DefaultGenerativeRouter : GenerativeRouter` (decision 3) — ordered cloud→static, ONNX slot
      reserved; selection at collection time; never throws expected errors.
- [ ] `M3` `AndroidConnectivityChecker` in `core/android` (decision 5) + `ConnectivityModule` + manifest
      `ACCESS_NETWORK_STATE`.
- [ ] `M4` `GenerateReplyUseCase` in `:domain` (decision 4); `GenerationProvidesModule` wiring
      (decision 6); ensure exactly one unqualified `GenerativeAiEngine` binding (the router).
- [ ] `M5` Tests + verification + ADR + advance `CLAUDE.md` to Block M.

## Tests (M5) — JVM, in `:data:repository` (router) and `:domain` (use case), via fakes

- **Router selection** (`DefaultGenerativeRouter` + `FakeGenerativeAiEngine` as cloud +
  `StaticFallbackEngine` real, or a second fake as fallback; `FakeConnectivityChecker` +
  `FakeSecureSecretStore` + `FakeAiProviderConfigRepository`):
  - offline → **static** (cloud fake's `lastRequest` stays null; static canned sequence collected).
  - online + config + non-blank key → **cloud** (cloud fake invoked with the request; static not used).
  - online + config + **no key** (empty store) → **static** (cloud not invoked).
  - online + **no config** (null) → **static**.
  - online + config + key-read **Failure** → **static** (no crash).
  - selection is re-evaluated per collection: flip `FakeConnectivityChecker.online` between two
    `generate(...).toList()` calls and assert the engine choice changes (latest-wins).
  - cancellation: cancelling collection mid-stream cancels (no leaked emissions / no throw).
- **`GenerateReplyUseCaseTest`** (`:domain`, `FakeGenerativeAiEngine` + real `PromptContextBuilder`):
  `generate("open telegram")` streams the engine's chunks; assert the engine received exactly
  `PromptContextBuilder().build("open telegram")` (one USER message = the command, static system prompt) —
  proving L feeds M.
- **Matching untouched:** assert/grep that `HandleUserCommandUseCase` and the intent pipeline are
  unchanged (no edits); the Phase-3/4 intent tests still green.

## Verification (run and paste actual output)

```bash
./gradlew :data:repository:testDebugUnitTest                     # router selection suite
./gradlew :domain:test                                          # GenerateReplyUseCase + (L/I) suites
./gradlew assembleDebug                                         # full Hilt graph; one unqualified engine binding
./gradlew testDebugUnitTest --rerun-tasks                       # full JVM regression (intent pipeline green)
grep -rn "import android\|io.ktor" domain/src/                  # empty (domain purity)
./gradlew :domain:dependencies --configuration compileClasspath  # stdlib + coroutines ONLY
grep -rn "data.aicloud\|data.repository" domain/src/            # empty (no impl import in domain)
# router references NO concrete engine type (only GenerativeAiEngine + ports):
grep -rni "OpenAiCompatible\|CloudGenerativeAiEngine\|ai-local\|aicloud" data/repository/src/main/java/com/sidr/launcher/data/repository/ai/   # empty
git diff --stat -- domain/src/main/java/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt   # no change
```

## Acceptance criteria

- The router picks **cloud** only when online **and** a provider is configured **and** a non-blank key
  exists for it; **static** in every other case (offline / no-config / no-key / key-read-failure) — never
  a crash, never an expected throw to the collector; selection re-evaluated each collection.
- The ONNX slot is reserved ahead of cloud so a Ph6 local engine inserts with no rewrite of the
  cloud/static branches; the router depends only on the `GenerativeAiEngine` port + domain ports (no
  data→data edge, no concrete engine import).
- `GenerateReplyUseCase` streams the router's `Flow<AiChunk>` from a request built by `PromptContextBuilder`
  (L→M proven); it depends only on `:domain` types and stays pure.
- `AndroidConnectivityChecker` provides reachability (Hilt-free, `ACCESS_NETWORK_STATE` declared); router
  logic fully covered via `FakeConnectivityChecker`.
- `HandleUserCommandUseCase` / intent matching untouched; `:domain` pure + vendor-neutral; no
  `feature → data` edge; full JVM regression green.
- ADR `Block M complete` in `decisions.md` (module placement + no-data→data-edge rationale, router order
  + ONNX-slot + selection-at-collection-time, no-key→static decision, single-unqualified-engine binding,
  connectivity glue not unit-tested in core/android + precedent, `@CloudEngine` re-tag coordination with
  K, verification output); `CLAUDE.md` advanced; Block M checked off in `phase-5-plan.md`. **Next =
  Block N** (assistant streaming UI + provider-settings form + phase close).

## Agent model

**Sonnet 4.6** to execute; **review the router selection order + the ONNX-slot structure on Opus** (the
plan's flagged subtle spot). The rest is bounded DI wiring + fake-driven tests.
