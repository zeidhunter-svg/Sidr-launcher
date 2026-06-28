# CLAUDE.md — Sidr Launcher

Session digest. Read this first. **Phase 4 is DONE (Blocks E → H, 2026-06-23).** **Phase 5 (cloud AI,
multi-provider) is DONE — Blocks I → N complete (2026-06-24 – 2026-06-27).** Code + JVM green;
on-device acceptance **pending** device run on SM-A325F (Block-J `SecretStoreInstrumentedTest` +
Block-N N5 streaming/offline/cancel/rotation).
**Phase 6 (Local NLU + embeddings, Blocks O → R) is IN PROGRESS — Blocks O + P complete (O 2026-06-27,
P 2026-06-28).** Block O delivered the pure domain contracts (`DeviceProfile`/`DeviceCapability`/
`DeviceProfileProvider` + `LocalInferenceGate` + `domain.ai.local` ports + fakes; NLU rides the existing
`IntentMatcher` port — **no parallel `IntentClassifier`**). **Block P delivered the ONNX runtime in
`:data:ai-local`:** `OnnxIntentClassifier : IntentMatcher` (`source = NLU`; lazy + single + Mutex-guarded
session on `Dispatchers.Default`, per-inference `LocalInferenceGate` re-check, `AutoCloseable` +
`SessionLifecycle` seam with transient-vs-sustained teardown, graceful degrade, no user text logged) +
`OnnxSessionFactory` (CPU default + opportunistic NNAPI on API 29+ behind `OnnxRuntimeFlags`, both
Fork-P6-5 failure modes handled, test seam) + a **pure JVM-tested P2a layer** (`WordPieceTokenizer`
byte-exact vs an independent golden, `IntentLabelMapper` softmax/label-map + confidence escape,
`SlotExtractor`, `NluLabel`, `OnnxModelSpec`) + `LocalModelFiles` seam (Q implements) + P0 pipeline in
`tools/nlu/` + device-pending `androidTest`. Open Question #1 (model/tokenizer/7-label set) RESOLVED
2026-06-28: BERT-Mini int8, WordPiece uncased vocab, 7 classes (`CLEAR` rule-only, slots heuristic).
21 new JVM tests (0 failures); ONNX confined to two shell files; pinned I/O contract
(`input_ids`/`attention_mask`[/`token_type_ids` if declared] int64 `[1,32]`, `logits` float `[1,7]`);
NLU softmax confidence **uncalibrated** vs rule scale (open Q → Block R); `assembleDebug` + full JVM
regression green. **Device-pending:** real `intent.onnx`/`vocab.txt` training + P5 SM-A325F run (no
torch/onnx/network here). `:app` trim-hook registration + DI binding deferred to Q/R (classifier not
bindable until Q's impls exist). Next = **Block Q** (DeviceProfile detector + ModelStore + SHA-256 +
WorkManager; gated on Open Question #2 — model hosting). Plan + forks:
[ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md) (ADRs "Block O complete" +
"Block P complete" in decisions.md). Phase 5 plan: [ai-context/phase-5-plan.md](ai-context/phase-5-plan.md).

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

## Current goal (active work)

**Phase 3 is DONE (Blocks A → D, 2026-06-21).** The MVP loop works: type `open telegram` →
resolves + launches offline; tap a grid app → launches; unknown → fallback UI, no crash.
**Live launch verified on device (SM-A325F, Android 13).** `assembleDebug` +
`testDebugUnitTest` green. Details in
[ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md) and the
Block D ADR in [ai-context/decisions.md](ai-context/decisions.md).

**Phase 4 — persistence, state & navigation hardening** (Blocks E → H, see
[ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)). **Block E DONE (2026-06-22):** DataStore
Preferences foundation — `UserPreferences`/`FeatureFlags`/`DeviceProfileCacheEntry`/`CachedSuggestion`
+ 4 repos (`Flow` read / `OperationResult` write), impls over one `DataStore<Preferences>`,
`PersistenceProvidesModule`+`PersistenceBindsModule` in `:app`, 4 fakes, 14 JVM tests green
(round-trip survives simulated restart). Privacy inventory enforced by a key-name guard test.
**Block F DONE (2026-06-22):** Room persistence — 3 history domain models + repo interfaces
(`AppUsageRecord`/`SuggestionRankingRecord`/`IntentMatchRecord` + `IntentMatchType`) in `:domain`;
Room entities, DAOs, `SidrDatabase` (v1, `exportSchema=true`), `TypeConverters`, mappers with
**SEARCH/UNKNOWN redaction** (query content never stored), 3 `*RepositoryImpl` with retention-caps
(200/100/200), `DatabaseModule`+`HistoryBindsModule` in `:app`; intent-match write live in
`HandleUserCommandUseCase`; grid sorts by usage recency/frequency; 2 fakes in `:core:testing`;
17 new JVM tests (Robolectric DAO/repo + redaction + column-privacy guard) green. Golden schema
`schemas/1.json` committed; `MigrationTestHelper` runway in `androidTest`.
**Block G DONE (2026-06-23):** permission-education — new `:feature:permission_education` module
(Compose + Hilt) replaces the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus` +
`PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker`
in `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (key
`perm_dismissed_wallpaper`); education ≠ request (Fork 5); live `SET_WALLPAPER` trigger from a
launcher "Wallpaper" button; denial disables only that feature, core never blocked.
**`BIND_ACCESSIBILITY_SERVICE` not requested/educated (Ph8).** 2 fakes + 12 new JVM tests green.
**Block H DONE (2026-06-23):** hardening + docs-sync — `UiState.Error(retryable)` + `retry()`
(H2, no process restart); `commandInput` via `SavedStateHandle` (H3 process-death restore);
`refreshStatus()` made upgrade-only (H-b, never downgrades `PERMANENTLY_DENIED`); privacy guard
extended to Room **table names** (H-a); temporary home-screen "Wallpaper" button removed (H4);
nav safe-fallback + kill→reopen verified on device (H5); `docs/architecture.md` synced to the real
3-flow `LauncherViewModel` design + Forks 1/2/8/9 (H6). Content-restore half of Fork 6 deferred to
Ph7 (no Ph4 display surface). `assembleDebug` + `testDebugUnitTest --rerun-tasks` green.
**Phase 4 closed; next = Phase 5 (cloud AI).** Details in
[ai-context/decisions.md](ai-context/decisions.md) ("ADR Block H").

Phase 3 result, Blocks A → D:

- **A ✅** `OperationResult` / `OperationError` in `domain`; `domain → core/common` edge removed.
- **B ✅** `:data:repository`; `InstalledAppsRepository` (PackageManager, offline); app grid +
  command input in `feature/launcher`; `:core:testing` bootstrapped.
- **C ✅** `LauncherIntent` / `ExecutableAction` / `IntentCandidate`; `IntentMatcher` +
  `IntentMatchResult`; `CommandNormalizer`; `DefaultIntentConfidencePolicy`; `RuleBasedIntentMatcher`
  (`:data:repository`, Android-free); `IntentActionResolver`; 65 JVM tests.
- **D ✅** `ActionExecutor` port + truncated `ActionExecutionResult` + `CommandOutcome` (12
  variants) + `HandleUserCommandUseCase` (domain); `AndroidActionExecutor` (`:data:repository`);
  VM wiring + `CommandFeedback` fallback UI (`feature/launcher`); DI via `IntentBindsModule` +
  `IntentProvidesModule` (`:app`). Routing: low/medium never auto-executes; navigation + CLEAR +
  SHOW_APPS + ambiguity never go through the executor.

**All Phase-4 slices delivered:** DataStore (E), Room (F), permission-education (G), hardening (H).
**Frozen to Phase 5+:** secrets (Ph5), cloud AI (Ph5), ONNX (Ph6), voice + context-suggestions
(Ph7), accessibility (Ph8), WorkManager (Ph6/9), full Hilt→KSP migration (Ph9).

## Status snapshot

- P0 ✅ docs/decisions · P1 ✅ compile-ready skeleton · P2 ⏭ reordered into Block B ✅ ·
  P3 foundation `3.0.1`–`3.1.5` ✅ (result types + navigation).
- Block A ✅ `OperationResult`/`OperationError` in `domain`; `domain → core/common` edge gone.
- Block B ✅ `:data:repository`, `InstalledAppsRepositoryImpl`, app grid, command input,
  `:core:testing` with `FakeInstalledAppsRepository`.
- Block C ✅ full intent domain — contracts, normalizer, rule-based matcher, resolver, 65 tests.
- Block D ✅ MVP loop — `ActionExecutor` + `CommandOutcome` + `HandleUserCommandUseCase`,
  `AndroidActionExecutor`, VM wiring + `CommandFeedback`, DI split modules. Live-verified on device.
- **Phase 3 CLOSED (2026-06-21).**
- **Phase 4 Block E ✅ (2026-06-22)** — DataStore Preferences: domain models + 4 repo interfaces,
  impls + mappers (`@Serializable` DTO stays in `:data:repository`), DI split modules, fakes, 14
  JVM tests. Privacy key-name guard green.
- **Phase 4 Block F ✅ (2026-06-22, fixes 2026-06-23)** — Room: 3 history domain models + interfaces
  in `:domain`; entities + DAOs + `SidrDatabase` (v1) + mappers with SEARCH/UNKNOWN redaction + 3
  `*RepositoryImpl` (retention 200/100/200) in `:data:repository`; `DatabaseModule`+`HistoryBindsModule`
  in `:app`; intent-match write live in use case; usage-sorted grid; 2 fakes + 17 new JVM tests
  (Robolectric + column-privacy guard) green; `MigrationTestHelper` v1 baseline verified on SM-A325F
  (Android 13); both history writes gated behind `FeatureFlags.usageHistoryEnabled` (4 flag-gate tests).
  Follow-up (2026-06-23): `recordMatch` moved off critical path via `recordingScope.launch {}` (injected
  `@ApplicationScope CoroutineScope`, required param, no lifecycle-less default); `CancellationException`
  re-thrown in `LauncherViewModel.recordUsage`; 2 more tests; 67 domain + 25 launcher JVM, 0 failures.
- **Phase 4 Block G ✅ (2026-06-23)** — permission-education: new `:feature:permission_education`
  module (Compose + Hilt) replacing the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus`
  + `PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker` in
  `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (`perm_dismissed_wallpaper`);
  education ≠ request (Fork 5) with a live `SET_WALLPAPER` trigger from a launcher "Wallpaper" button;
  denial disables only that feature, core never blocked; accessibility deferred (Ph8). 2 fakes + 12 JVM
  tests green.
- **Phase 4 Block H ✅ (2026-06-23)** — hardening + docs-sync (Fork 6 + Fork 9): `UiState.Error` gained
  `retryable: Boolean = false` + `LauncherViewModel.retry()` (no process restart, retry action not in the
  data class); `commandInput` backed by `SavedStateHandle` (process-death restore); `refreshStatus()`
  upgrade-only (never downgrades `PERMANENTLY_DENIED`, partial fix — revisit Ph7/`RECORD_AUDIO`); privacy
  guard extended to Room **table names** (hand-synced `TABLE_NAMES`); temporary home-screen wallpaper
  button removed (entry returns with `feature/settings`); nav safe-fallback latent (no bad route in Ph4)
  + kill→reopen verified on device (PID change, input restored, no crash); content-restore half of Fork 6
  deferred to Ph7 (no display surface yet). `docs/architecture.md` synced to the real 3-flow
  `LauncherViewModel`. `assembleDebug` + 105 JVM tests green. **Phase 4 CLOSED (Blocks E → H).**
- **Phase 5 Block I ✅ (2026-06-24)** — multi-provider AI domain contracts (pure): `domain.ai`
  (`AiProviderId`/`AiModelId` opaque value classes, `AiRequest`/`AiMessage`/`AiRole` with **no sampling
  params**, `AiChunk`+`AiStopReason`(refusal = success terminal)+`AiUsage`, `AiError`,
  `GenerativeAiEngine`+`GenerativeRouter`, `AiChunks.assembleText`), `domain.security`
  (`SecureSecretStore`+`SecretKey`+`SecretKeys.apiKey(provider)`, per-provider, `OperationResult`/never
  throws), `domain.connectivity` (`ConnectivityChecker`); 3 fakes in `:core:testing`; 10 new JVM tests
  green. Vendor-neutral (grep `anthropic|openai|gemini|claude` over `domain/src/` empty); `:domain`
  stays stdlib+coroutines; no new deps; intent code + `feature/assistant` untouched. Details:
  decisions.md "ADR Block I". **Addendum (2026-06-24):** added pure `AiProviderConfig` +
  `AiProviderConfigRepository` (`…domain.ai`) for user-configurable providers (base URL + free-text
  model; key stays in `SecureSecretStore`) + fake + round-trip test; plan re-oriented to
  OpenAI-compatible-first.
- **Phase 5 Block J ✅ (2026-06-24)** — Keystore-backed `SecureSecretStore` (BYOK, per-provider).
  `:data.repository.security`: `SecretCipher`+`EncryptedBlob` crypto seam (Fork 7); `KeystoreSecretCipher`
  (AES-256-GCM in `AndroidKeyStore`, alias `sidr_secret_aead_v1`, StrongBox-with-fallback,
  `userAuthRequired=false`); `SecureSecretStoreImpl` over a **dedicated `sidr_secrets` DataStore**
  (`@SecretsDataStore` qualifier — separate file from Block E's `sidr_preferences`, so credential keys
  never enter the privacy-guarded `ALL_KEY_NAMES`); `get` decrypt-fail/invalidation/corrupt →
  `Success(null)` + clear-entry, `put`/`remove` → `Failure` on I/O, never throws, `CancellationException`
  re-thrown. `java.util.Base64` (no Robolectric). DI: `SecretsProvidesModule`+`SecretsBindsModule` in
  `:app`. `FakeSecretCipher` + **9 JVM tests** (pure JVM) green; `SecretStoreInstrumentedTest` (3 tests,
  real Keystore) **compiles — device run pending on SM-A325F**. ESP/security-crypto absent; no secret
  logged; `PrivacyInventoryGuardTest` green; `:domain`/`feature` untouched. Details: decisions.md
  "ADR Block J".
- **Phase 5 Block K ✅ (2026-06-24)** — OpenAI-compatible cloud engine (SSE → `Flow<AiChunk>`).
  `:data:ai-cloud` (Hilt-free, no `:core:android` edge): `OpenAiCompatibleGenerativeAiEngine` streams any
  OpenAI-compatible `chat/completions` endpoint — base URL + free-text model from
  `AiProviderConfigRepository`, key from `SecureSecretStore`, `Authorization: Bearer`. **Minimal body**
  (`model`/`messages`/`max_tokens`/`stream:true`, `system` leading), **no sampling params** (absent from
  the private `@Serializable` DTOs); robust URL join (`removeSuffix("/")+"/chat/completions"`, keeps
  `/v1`); inputs trimmed; **HTTPS-only** (non-`https://`→`InvalidRequest`, socket never opened).
  **Manual SSE** over `bodyAsChannel()`+`readUTF8Line()` (no `ktor-client-sse`): `Text` deltas, terminal
  `Completed(stopReason,usage?)` at `[DONE]`/EOF; `finish_reason` captured off the content-empty terminal
  delta; `content_filter`/`delta.refusal`→`REFUSAL` (**sticky**, success terminal). Full `AiError`
  taxonomy as terminal `Failed` (MissingCredentials/Unauthorized/RateLimited(Retry-After
  delta+date)/ServerError/InvalidRequest/Network/Offline/Timeout/Unknown); `detail` safe-only. Per-read
  first-token + idle `withTimeoutOrNull` (**no `requestTimeoutMillis`**); cold `flow{}` + `execute{}` +
  `flowOn`, `CancellationException` re-thrown (collection-cancel aborts the request).
  `AiProviderConfigRepositoryImpl` lives in **`:data:repository`** over the shared `sidr_preferences`
  store (+ 4 denylist-clean `ai_provider_*` keys in `ALL_KEY_NAMES`); `@CloudEngine` engine + `HttpClient`
  (Android) providers in `:app` (`AiCloudProvidesModule`), config-repo bound in `PersistenceBindsModule`;
  `network_security_config.xml` (no cleartext) wired in the manifest. 20 MockEngine tests + 4 config-repo
  tests + full regression green; domain vendor-neutral/pure; only `ktor-client-mock` added (test-only).
  Details: decisions.md "ADR Block K". **Next = Block M (router + static fallback).**
- **Phase 5 Block L ✅ (2026-06-27)** — prompt/context builder + outbound privacy guards (pure, `:domain`).
  `…domain.ai`: `PromptContextBuilder` (public `build(userCommand)` **only** — no context-bag overload)
  → minimal `AiRequest` = **one verbatim `USER` message** + static `DEFAULT_SYSTEM_PROMPT` (short,
  context-free, vendor-neutral, no model pinned) + `maxOutputTokens=512` (guidance) + `model=null`;
  nothing else assembled (no device/usage/calendar/location/history/contacts/clipboard). `OutboundContextPolicy`
  = **positive allow-list** `{USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS}` (fail-closed, Fork
  P5-3) + `FORBIDDEN_CONTEXT_TERMS`/`CREDENTIAL_TERMS` + hand-synced `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES`
  (Phase-4 `TABLE_NAMES` precedent). Denylist scanned over **static text + field inventories, NEVER user
  content** (regression test keeps "calendar" in a user command); `token` excluded (collides with
  `maxOutputTokens`); credential terms scanned over field-name inventories **not rendered `toString`**
  (avoids the `MissingCredentials`/"credential" vacuous collision — a refinement past the prompt's literal
  toString scan); leak guard scans `toString` only for a planted sentinel. 11 reflection-free JVM tests
  (`AiRequestGuardTest` 5 + `OutboundSecretLeakGuardTest` 6) green, **91 domain total**; `:domain` stays
  stdlib+coroutines/vendor-neutral; no new deps; `IntentMatcher`/`HandleUserCommandUseCase`/`feature/*`/
  K-M-N untouched. Details: decisions.md "ADR Block L". **Next = Block M (router + static fallback,
  consumes K + L).**
- **Phase 5 Block M ✅ (2026-06-27)** — routing seam + static fallback + `GenerateReplyUseCase`.
  `:data.repository.ai`: `StaticFallbackEngine` (canned reply, no network, always `Completed`);
  `DefaultGenerativeRouter : GenerativeRouter` — cold `flow { emitAll(selectEngine().generate(request)) }`,
  ordered **ONNX slot (reserved) → cloud (online + config + non-blank key) → static** (latest-wins,
  selection at collection time; `firstOrNull()` on config flow; `Failure` from secret store → static;
  never throws expected errors). `core/android/connectivity/AndroidConnectivityChecker` (Hilt-free,
  `callbackFlow` + `conflate` + `distinctUntilChanged`, `ACCESS_NETWORK_STATE` added to manifest);
  `GenerateReplyUseCase` in `:domain` (`generate(command)` = `engine.generate(builder.build(command))`);
  `@FallbackEngine` qualifier co-located with `@CloudEngine` in `:app`; `GenerationProvidesModule`
  (fallback + router + unqualified-engine→router + builder + use case); `ConnectivityModule`. No
  data→data edge (router refs port only); single unqualified `GenerativeAiEngine` binding (the router);
  `HandleUserCommandUseCase` untouched; `:domain` pure; `core/android` gains `coroutines.core`.
  7 router tests + 6 use-case tests green; **full JVM regression green** (96 domain total); `assembleDebug`
  green (Hilt graph valid). Details: decisions.md "ADR Block M".
- **Phase 5 Block N ✅ (2026-06-27)** — assistant streaming UI + provider-settings form + phase close.
  `:feature:assistant` gains Hilt (`kapt` + `hilt.android` + `hilt.navigation.compose` etc., mirroring
  `permission_education`). `AssistantViewModel` (`@HiltViewModel`, 3 domain-port deps):
  `Flow<AiChunk>` collected in `viewModelScope` (survives rotation, aborted on back-nav, latest-wins
  `retry()`); `AssistantUiState(reply, status, form)` in a single `StateFlow`; `status` =
  `AssistantStatus {Idle/Streaming/Done(refused)/Error(error,retryable,showProviderCta)}`; refusal =
  `Done(refused=true)` (success terminal, not an error); credential errors → `showProviderCta=true`
  (CTA, not Retry); `AiError→UiError` mapper feature-local (prevents `core/common→domain` edge).
  **No `SavedStateHandle`** (deliberate — key must never touch saved state; prompt/reply are transient;
  see decisions.md "ADR Block N"). `saveProvider`: `providerId` derived from host (lowercase, path
  stripped), `https://`-validated, config written to `AiProviderConfigRepository`, key to
  `SecureSecretStore` (blank key skips put); key never logged/in state/displayed back. `AssistantScreen`
  pure render: first-run form when no config; streaming chat + expandable provider form when configured.
  `AppNavHost`: real `hiltViewModel()` destination + `LaunchedEffect(navigationEvents)` + safe-fallback.
  14 JVM tests green; **262 total JVM tests**; `assembleDebug` green. On-device (N5) + Block-J
  `androidTest` **pending** SM-A325F device run. Details: decisions.md "ADR Block N + Phase 5 close".
  **Phase 5 CLOSED (Blocks I → N). Next = Phase 6 (ONNX NLU).**
- **Phase 6 Block O ✅ (2026-06-27)** — local-AI domain contracts + `DeviceProfile`/`DeviceCapability`
  model + gating policy. `domain.ai.local`: `ModelId`, `ModelAvailability`, `ModelAvailabilityRepository`,
  `TextEmbedder` (port-only, impl deferred Phase 7). `domain.device`: `DeviceProfile` (enum
  `LOW_END/MID_RANGE/HIGH_END`), `DeviceCapability` (ramBytes/cpuCores/nnapiAvailable/thermalOk/
  batteryOk; **no `online` field** — owned by `ConnectivityChecker`), `DeviceProfileProvider` port,
  `LocalInferenceGate` (pure policy: LOW_END→false always; MID/HIGH→true iff Available+thermalOk+
  batteryOk). Port-topology: **NLU rides the existing `IntentMatcher` port**; no parallel
  `IntentClassifier` created. 4 fakes in `:core:testing` (`NoOpIntentMatcher`, `FakeTextEmbedder`,
  `FakeModelAvailabilityRepository`, `FakeDeviceProfileProvider`). 22 new JVM tests (**284 total**, 0
  failures); purity guard green; all greps clean; intent pipeline untouched.
  Details: decisions.md "ADR 2026-06-27 — Block O complete". **Next = Block P** (gated on model +
  tokenizer + label-set selection — open question must be resolved first).
- **Phase 6 Block P ✅ (2026-06-28)** — ONNX runtime in `:data:ai-local` (platform-risk block). Open
  Question #1 resolved: BERT-Mini/TinyBERT-4L int8, WordPiece **uncased** vocab, **7 classes**
  (`NluLabel` argmax order LAUNCH_APP/SEARCH/OPEN_SETTINGS/SHOW_APPS/HELP/OPEN_ASSISTANT/UNKNOWN;
  `CLEAR` rule-only; slots heuristic). **P2a pure, ONNX-free, JVM-tested:** `WordPieceTokenizer`
  (faithful HF BasicTokenizer+Wordpiece; byte-exact vs an **independent** stdlib reference golden —
  the #1 silent-failure guard), `IntentLabelMapper` (softmax→argmax→label→`IntentMatchResult` +
  **confidence escape** at floor 0.60 / argmax==UNKNOWN), `SlotExtractor` (verb/filler strip),
  `NluLabel`, `OnnxModelSpec`. **P2b/P3/P4 thin shell** `OnnxIntentClassifier : IntentMatcher`
  (`source = NLU`): lazy + single + `Mutex`-serialized session on `Dispatchers.Default`;
  **per-inference** `LocalInferenceGate.allowsLocalNlu` re-check with fresh `capability()` (Fork P6-4
  moment 2); files resolved via `LocalModelFiles` seam (Q impl) **before** any `OrtEnvironment` call so
  missing model/vocab degrades JVM-testably; tensors + `OrtSession.Result` in `use{}`; any failure →
  lowest-confidence result, never thrown; **no user text logged**. **Lifecycle:** `AutoCloseable` +
  `SessionLifecycle` ONNX-free seam (`:app onTrimMemory` wiring deferred to Q/R — classifier not
  bindable until Q's impls exist); **transient gate-off keeps the session, sustained (debounced ~30s)
  + trim tears it down**, re-inits lazily; `runMutex.tryLock()` + `pendingTeardown` avoids closing
  mid-run. **P1** `OnnxSessionFactory`: **CPU deterministic default** + NNAPI appended only when
  `nnapiEnabled && sdkInt>=29` (flag in `OnnxRuntimeFlags`, off by default; both init-failure and
  degraded-success handled; `nnapiEnabled`/`sdkInt` test seams for P5 path comparison). Pinned I/O:
  `input_ids`+`attention_mask`[+`token_type_ids` iff declared] int64 `[1,32]`, `logits` float `[1,7]`
  read by index 0. **P0** `tools/nlu/` (out of source sets): stdlib golden generator (ran), placeholder
  + train/export scripts (device-pending — no torch/onnx/net). ONNX Java surface re-verified vs the
  bundled 1.20.0 AAR (`javap`). `:core:android` edge added; **no new dep**. ONNX confined to two shell
  files (`OnnxIntentClassifier`/`OnnxSessionFactory`) — grep clean incl. pure layer; `:domain`
  untouched; no network on inference path; generative router slot + intent/Phase-3/5 untouched. **21
  new JVM tests, 0 failures**; `androidTest` compiles (device-pending, Assume-skips w/o asset);
  `assembleDebug` (clean baseline) + full JVM regression green. NLU softmax confidence **uncalibrated**
  vs rule scale → open Q to **Block R**. Details: decisions.md "ADR 2026-06-28 — Block P complete".
  **Next = Block Q** (DeviceProfile detector + ModelStore + SHA-256 + WorkManager; gated on Open
  Question #2 — model hosting/URL). **Phase 6 NOT closed (Block R closes it).**

## Hard rules

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`.
- Interfaces in `domain`; implementations in `data/*`. UI holds no business logic.
- No `feature -> feature` deps. Single `NavHost` in `app`. ViewModels emit
  `NavigationEvent`; they never touch `NavHostController`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- `IntentMatcher` (→ `IntentMatchResult`) is a **different port** from `GenerativeAiEngine`
  (→ `Flow<AiChunk>`). Matching ≠ generation.
- Launcher core works fully offline; optional permissions never block startup.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` *(Block D)* | `domain` |
| `DeviceProfile`/`DeviceCapability` model + `DeviceProfileProvider` port + `LocalInferenceGate` *(Block O ✅)* | `domain` |
| `ModelId`/`ModelAvailability`/`ModelAvailabilityRepository`/`TextEmbedder` port *(Block O ✅)* | `domain` |
| Rule-based matcher impl, `InstalledAppsRepository` impl, Android `ActionExecutor` impl | `data/repository` |
| Pref domain models (`UserPreferences`, `FeatureFlags`, `DeviceProfileCacheEntry`, `CachedSuggestion`) + their repo interfaces *(Block E ✅)* | `domain` |
| DataStore Preferences impls + `PreferencesMapper` + `PreferencesKeys` *(Block E ✅)* | `data/repository` |
| History domain models (`AppUsageRecord`, `SuggestionRankingRecord`, `IntentMatchRecord`) + repo interfaces (`UsageHistoryRepository`, `SuggestionRankingRepository`, `IntentMatchHistoryRepository`) *(Block F)* | `domain` |
| Room entities, DAOs, `SidrDatabase`, `TypeConverters`, `migrations/`, mappers *(Block F)* | `data/repository` |
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) *(Block G)* | `domain` |
| `AndroidPermissionChecker` impl *(Block G)* | `core/android` |
| `PermissionPrefsRepositoryImpl` (DataStore) *(Block G)* | `data/repository` |
| Permission-education UI (`PermissionEducationScreen`/`ViewModel`, rationale, request flow) *(Block G)* | `feature/permission_education` |
| Cloud AI client (Ktor) | `data/ai-cloud` |
| `PromptContextBuilder` + `OutboundContextPolicy` (outbound allow-list/guards) *(Block L)* | `domain` |
| ONNX NLU / embeddings | `data/ai-local` |
| `UiState`, dispatchers, logging contracts | `core/common` |
| `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| `DeviceProfile` detection, `PackageManager` access, `SpeechInputSource` Android impl | `core/android` |
| Design system / theme | `core/ui` |
| Test fakes / fixtures | `core/testing` |
| Single `NavHost`, composition root, Hilt graph | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |

## Source of truth

- Architecture & target module structure: [docs/architecture.md](docs/architecture.md)
  **IN SYNC** as of Block H6 (2026-06-23) — real 3-flow `LauncherViewModel`, `UiState.Error(retryable)`,
  per-feature permission education + upgrade-only `refreshStatus()`, Forks 1/2/8/9 reflected.
  `EncryptedSharedPreferences` documented as deprecated/not used (Fork 1 defers secrets to Phase 5).
- Closed checklists: [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md) *(Phase 4 closed,
  Blocks E → H, 2026-06-23)* · [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md)
  *(Phase 3 closed 2026-06-21)*
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Active checklist: [ai-context/phase-5-plan.md](ai-context/phase-5-plan.md) *(Phase 5 cloud AI,
  multi-provider — Blocks I/J/K done 2026-06-24, Block L done 2026-06-27, Block M next; forks decided 2026-06-24)*
- Roadmap: [docs/roadmap.md](docs/roadmap.md)

## Do not

- Don't start Phase 5 (cloud AI) ahead of its own approved plan. Phase 4 (E → H) is closed;
  extend the existing persistence/hardening, don't re-scaffold it.
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets land in Phase 5 via a
  `SecureSecretStore` port (Fork 1).
