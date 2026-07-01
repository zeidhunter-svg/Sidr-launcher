# CLAUDE.md — Sidr Launcher

Session digest. Read this first. **Phase 4 is DONE (Blocks E → H, 2026-06-23).** **Phase 5 (cloud AI,
multi-provider) is DONE — Blocks I → N complete (2026-06-24 – 2026-06-27).** Code + JVM green;
on-device acceptance **pending** device run on SM-A325F (Block-J `SecretStoreInstrumentedTest` +
Block-N N5 streaming/offline/cancel/rotation).
**Phase 6 (Local NLU + embeddings, Blocks O → R) is DONE — Blocks O → R complete (O 2026-06-27,
P + Q 2026-06-28, R 2026-06-29); code + JVM green, device acceptance + real model (OQ#1/#2) pending.**
**Block R wired the NLU source into the live pipeline + closed the phase:** rule-first
`LayeredIntentMatcher : IntentMatcher` (`:data:repository`, port-only, no `data→data` edge) — rule
returned verbatim when not low-confidence (NLU never consulted, `< 10ms` + Phase-3 parity preserved),
NLU consulted only on low confidence; **R1 calibration decided (§5.A): conservative band → Suggest** via
the pure `NluConfidenceCalibrator` (raw softmax → `[suggestThreshold, autoExecuteThreshold)`, always
Suggests, never silently auto-executes; `confidenceFloor` stays in `OnnxModelSpec`, calibrator floor a
decoupled plain `Float`); escape pinned structurally (`source==NLU && (conf==0f || UnknownIntent)`). DI
(R2): `@RuleMatcher`/`@NluMatcher` qualifiers + `NluMatcherProvidesModule`, unqualified `IntentMatcher` →
`LayeredIntentMatcher`, `HandleUserCommandUseCase` untouched; **§5.F deviation (recorded): `@NluMatcher`
binds the self-gating `OnnxIntentClassifier` UNCONDITIONALLY** (availability flips at runtime → live
re-check beats a stale graph-time NoOp swap; no production NoOp needed), same `@Singleton` exposed as
`@NluMatcher` + `SessionLifecycle`. R2.5: `SidrLauncherApp.onTrimMemory(≥TRIM_MEMORY_BACKGROUND)` /
`onLowMemory()` → `SessionLifecycle.releaseResources()` (`:app` holds only the ONNX-free seam). R3:
`ensureModel()` fired fire-and-forget on `@ApplicationScope` (IO) from `onCreate()` (inert under OQ#2).
With no model present (shipping state) the NLU secondary always escapes → exact rule-only parity. New
JVM tests green (fast-path "NLU never invoked", escape→rule, calibrated answer, no-model parity,
calibrator boundaries); `assembleDebug` (Hilt graph valid) + full regression green. Details: decisions.md
"ADR 2026-06-29 — Block R complete + Phase 6 close".
**Block Q delivered model-management + download gating:** pure
`DeviceProfileClassifier` (`(ram,cores)→DeviceProfile`: LOW_END `<2.5 GB` or `<4` cores, HIGH_END `≥5.5 GB`
+ `≥8` cores, else MID) + `DeviceProfileCacheMapping` (lossy LOW_END-vs-rest) + `AndroidDeviceProfiler :
DeviceProfileProvider` (`:core:android`, write-through to the **pre-existing** `DeviceProfileCacheRepository`,
capability re-read per call); `ModelStore` (quarantine→SHA-256-verify→atomic-rename, implements P's
`LocalModelFiles`, **never exposes an unverified file**, bundled-vocab `assets/nlu/` seam) +
`Sha256Verifier` + `ModelProvisioner` + `ModelManager` (**enqueue-gate-static-only** §6.A:
`profile≠LOW_END && availability≠Available && config.isPinned`; thermal/battery NOT in the enqueue
decision — they're WorkManager constraints + the per-inference re-check inside `OnnxIntentClassifier`) +
`ModelDownloader` port (**`:domain`**, rework P2-4) + `ModelFilePresence` port (`:domain`, rework P1-2) +
`ModelDownloadScheduler` port (`:data:ai-local`); `ModelAvailabilityRepositoryImpl` (`:data:repository`,
`model_available_ids` stringSet **cross-checked against `ModelFilePresence` disk truth** → all 3 Block-O
states reachable, marker-but-missing reads not-Available; privacy-guard green); `@HiltWorker
ModelDownloadWorker` (shell) + `WorkManagerModelDownloadScheduler` + `SidrLauncherApp :
Configuration.Provider`/`HiltWorkerFactory` + manifest WorkManagerInitializer removal (in `:app`);
`KtorModelDownloader` (HTTPS-only, **retry taxonomy** 4xx/non-HTTPS→permanent, 5xx/network→transient) is a
plain class in **`:data:ai-cloud`** (`@Provides`-wired in `:app`, reuses the cloud `HttpClient`) — keeps
`:data:ai-local` kapt/HTTP-free and adds **no `data→data` edge** (port in `:domain`); correctness logic
stays in the JVM-tested `ModelProvisioner`. **OQ#2 NOT resolved (expected):**
`ModelDownloadConfig.INTENT_NLU_PENDING` is the single inert seam (blank URL/hash, `TODO(OQ#2)`; `init`
`require` rejects a half-pinned config); whole mechanism JVM-tested vs fakes, live download +
`AndroidDeviceProfiler` reads device/release-pending on SM-A325F. New deps = WorkManager 2.10.0 +
hilt-work 1.2.0 only; `ai.onnxruntime` still confined to P's two shell files; `:domain` untouched. **39
new JVM tests** (0 failures); full `testDebugUnitTest` + `assembleDebug` green. **P's three seams
(`DeviceProfileProvider`/`ModelAvailabilityRepository`/`LocalModelFiles`) now have prod impls →
`OnnxIntentClassifier` is bindable; Block R does the bind + trim-hook + `ensureModel()` trigger.**
Block O delivered the pure domain contracts (`DeviceProfile`/`DeviceCapability`/
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
2026-06-28, **AMENDED multilingual 2026-06-29**: multilingual WordPiece teacher
(`bert-base-multilingual-uncased`) → prune+distill+int8 for `en/ar/tr/ru`, pruned `vocab.txt`
(data-driven ~20–30k), `maxLen 48`, 7 (language-independent) classes (`CLEAR` rule-only, slots heuristic).
21 new JVM tests (0 failures); ONNX confined to two shell files; pinned I/O contract
(`input_ids`/`attention_mask`[/`token_type_ids` if declared] int64 `[1,48]`, `logits` float `[1,7]`);
NLU softmax confidence **uncalibrated** vs rule scale (open Q → Block R); `assembleDebug` + full JVM
regression green. **Device-pending:** real `intent.onnx`/`vocab.txt` training + P5 SM-A325F run (no
torch/onnx/network here). `:app` trim-hook registration + DI binding were deferred to Q/R; **Block R
delivered them (see above) — Phase 6 is now CLOSED.** Next = the deferred **device-acceptance pass**
(SM-A325F: Block-J `SecretStoreInstrumentedTest`, Block-N N5, Block-P P5 `< 150ms` inference + on-device
NLU, Block-Q `AndroidDeviceProfiler` reads, Block-R `onTrimMemory` teardown — all gated on the real model
OQ#1/#2) and/or **Phase 7** per the roadmap. Plan + forks:
[ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md) (ADRs "Block O complete",
"Block P complete", "Block Q complete", "Block R complete + Phase 6 close" in decisions.md). Phase 5 plan: [ai-context/phase-5-plan.md](ai-context/phase-5-plan.md).

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

## Current goal (active work)

**NOW (2026-07-01): Phase 7 user-facing close is DONE — Blocks S + T + U + W complete; Block V runtime seam is implemented but inert pending OQ#3.** Phases 3 → 6 are code-closed
(MVP loop, persistence/Room/permission-education/hardening, cloud AI multi-provider, local ONNX NLU).
**Phase 7 Block S** delivered the pure `:domain` suggestion + voice contracts; **Phase 7 Block T** delivered
voice input (`AndroidSpeechInputSource` in `:core:android`, `VoiceModule` DI, the `RECORD_AUDIO` routed-education
request flow, the discharged Block-H `refreshStatus()` debt, and the launcher mic affordance) — 383 JVM tests
green, 0 new deps, `android.speech` confined to `:core:android`, real recognizer device-pending (OQ#4).
**Phase 7 Block U** delivered the contextual suggestion engine (offline `TimeOfDaySuggestionProvider`/
`UsageSuggestionProvider` + opt-in `CalendarSuggestionProvider`/`LocationSuggestionProvider` +
`SuggestionEngineImpl` aggregate→rank→persist, gated by `aiSuggestionsEnabled`) and reused Block T's
request-flow template verbatim for `CALENDAR_SUGGESTIONS`/`LOCATION_SUGGESTIONS` — 399 JVM tests green (16
new), 0 new deps, `android.location`/`CalendarContract` confined to `:data:repository`, privacy guard
delivered as 4 executable proofs (incl. a reflection-based fix closing a hand-maintained-inventory drift
gap in Block L's `AiRequestGuardTest`), real device reads device-pending. **Phase 7 Block W** then closed the
user-facing suggestions surface in two slices: **W-lite** made `LauncherUiState.suggestions` the single owner,
restored cached suggestions for first paint, refreshed with a fresh superseding engine pass, kept
`:feature:suggestions` stateless/UI-only, and wired suggestion taps into the existing launcher
launch/navigation path; **W proper** added periodic `SuggestionPrecomputeWorker` +
`UsageCleanupWorker`, gate-before-enqueue scheduling, boot warmup via `RECEIVE_BOOT_COMPLETED`, and docs sync.
**Phase 7 Block V** added `OnnxTextEmbedder : TextEmbedder` in `:data:ai-local`, the
`SemanticSuggestionRanker` decorator, `ModelDownloadConfig.EMBEDDING_PENDING`, and Hilt
`Set<SessionLifecycle>` teardown so NLU + embedder sessions both release on trim/low-memory. It is
**structurally ready but inert** until OQ#3 pins the embedding model/host/hash/ONNX contract; no-model,
gate-off, and failure paths preserve heuristic suggestion order exactly. Carried device-acceptance debt
(SM-A325F): Block-J Keystore, Block-N N5, Block-P P5/OQ#1-2, Block-T OQ#4, Block-U calendar/location reads,
and Block-V real embedding load + `<150ms` MID_RANGE latency + memory/co-residency check. **OQ#3 follow-up
debt is recorded in decisions.md:** revisit `SemanticSuggestionRanker`'s sync `runBlocking` boundary when a
real model is pinned; decide how live typed-prefix UX feeds `SuggestionContext.typedPrefix`; generalize
multi-model provisioning or add an embedding-specific manager/scheduler; run real embedder device
acceptance. See the session digest at the top + the [Phase 7 plan](ai-context/phase-7-voice-suggestions-plan.md).
*(The phase-by-phase history below is retained for context.)*

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

**Phase 5 CLOSED (code-closed, Blocks I → N, 2026-06-27). ⚠ device-pending** (NOT dissolved —
carried into Phase 7 Tracking): Block-J `SecretStoreInstrumentedTest` (real Keystore, SM-A325F) +
Block-N N5 streaming / offline / cancel / rotation. Details:
[ai-context/phase-5-plan.md](ai-context/phase-5-plan.md).

**Phase 6 CLOSED (code-closed, Blocks O → R, 2026-06-29). ⚠ device-pending** (NOT dissolved —
carried into Phase 7 Tracking): Block-P P5 `< 150ms` inference + on-device NLU + Block-Q
`AndroidDeviceProfiler` reads + Block-R `onTrimMemory` teardown — all gated on OQ#1/#2 (real
`intent.onnx`/`vocab.txt`). Details:
[ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md).

**Phase 7 — voice input + contextual suggestions** (Blocks S → W, see
[ai-context/phase-7-voice-suggestions-plan.md](ai-context/phase-7-voice-suggestions-plan.md); forks
decided 2026-06-29). **Block S DONE (2026-06-29):** pure `:domain` suggestion+voice contracts
(`Suggestion`/`SuggestionSource`/`SuggestionContext`/`TimeOfDay`, `SuggestionProvider`/
`SuggestionEngine`/`SuggestionRanker` + pure `HeuristicSuggestionRanker`; `SpeechInputSource` port
+ `SpeechRecognitionState`/`SpeechRecognitionError` — **enum, UPPER_SNAKE_CASE** like
`AiStopReason`); fakes in `:core:testing`; 12 new JVM tests / 131 domain total; `assembleDebug` +
`testDebugUnitTest` green; 0 new Gradle deps. **Block T DONE (2026-06-30):** voice input —
`AndroidSpeechInputSource : SpeechInputSource` in `:core:android` (on-device recognizer preferred behind
`SDK_INT>=S` + `EXTRA_PREFER_OFFLINE` fallback; `RecognitionListener` → `callbackFlow`; all recognizer
calls marshalled to `Handler(Looper.getMainLooper())`; `destroy()` on `awaitClose` + `AtomicBoolean`
cancel-before-create guard so no recognizer/mic leak; full error-code → `SpeechRecognitionError` map; **no
transcript/audio logged**) — the **only** `android.speech` site (grep-proven). `VoiceModule` DI in `:app`
(`@ApplicationContext`); `FakeSpeechInputSource` drives JVM tests. `PermissionFeature.VOICE_INPUT.requestable`
flipped **true**; education VM routed via `SavedStateHandle` (`permission_education?feature={feature}`, defaults
WALLPAPER), screen request/post-grant feature-driven + `ON_RESUME` `refreshStatus()`. **Block-H `refreshStatus()`
debt discharged:** genuine `GRANTED→DENIED` revocation reflected; `PERMANENTLY_DENIED→DENIED` suppression
reachable only from an established `PERMANENTLY_DENIED` (never from `GRANTED`) — the two never collide; KDoc
rewritten + JVM-tested. Launcher mic affordance (shown only when `isVoiceInputAvailable`): granted →
`startVoiceInput()` (partials → `commandInput`, Final → **unchanged** `onCommandSubmitted`); not-granted →
route to education (framework `checkSelfPermission`, **no `:feature:launcher` build change**).
`HandleUserCommandUseCase` untouched; voice text = `USER_COMMAND` byte-for-byte (F7-9). **9 new JVM tests; 383
JVM total / 0 failures; `assembleDebug` (Hilt graph valid) + `testDebugUnitTest` green; 0 new Gradle deps; no
manifest line (RECORD_AUDIO present since Block N).** Real recognizer **device-pending (OQ#4, independent of
U/V/W)**. Details: decisions.md "ADR 2026-06-30 — Block T complete". **Block U DONE (2026-06-30):**
contextual suggestion engine — `:data:repository` gained `TimeOfDaySuggestionProvider` (zero-permission,
fixed per-`TimeOfDay` table) + `UsageSuggestionProvider` (recency-decay + frequency-ratio over
`UsageHistoryRepository`) + `CalendarSuggestionProvider`/`LocationSuggestionProvider` (`READ_CALENDAR`/
`ACCESS_FINE_LOCATION`-gated, query/read only enough to decide *whether* a signal exists — never a raw
title/coordinate — and emit a single fixed generic `Suggestion`) + `SuggestionEngineImpl` (structured-
concurrency aggregate, fault-isolated per provider, `HeuristicSuggestionRanker`-ranked, persists to
`SuggestionRankingRepository`+`SuggestionsCacheRepository` as `CachedSuggestion(label,actionId)` only,
gated by `aiSuggestionsEnabled`, fire-and-forget persist failures logged via direct `Log.w` — the module's
first such path; `core/common`'s dormant `ResultLogger` was deliberately **not** wired). DI:
`SuggestionsProvidesModule` in `:app`. `CALENDAR_SUGGESTIONS`/`LOCATION_SUGGESTIONS.requestable` flipped
**true** reusing Block T's request-flow template **literally** (zero edits needed to
`PermissionEducationScreen.androidPermission()`'s already-exhaustive `when`, or to either VM's
constructor); manifest gained exactly `READ_CALENDAR`+`ACCESS_FINE_LOCATION`. Per-feature dismissed flag
stays in-memory only for all three non-`WALLPAPER` requestable features (`VOICE_INPUT` included) — a
Block-G-era structural fact (the feature names collide with `PrivacyInventoryGuardTest`'s own denylist),
not a per-block deviation. **Privacy guard delivered as 4 executable proofs**: persistence-shape test,
`SuggestionProviderPrivacyGuardTest` (plants a real sensitive event title/GPS fix and runs the **actual**
providers against them, asserts nothing leaks), an outbound-isolation regression in `:domain`, and a
reflection-based fix to Block L's `AiRequestGuardTest` (closed a real hand-maintained-inventory drift gap
found during review — `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` now reflect against `AiRequest`/
`AiError`'s actual declared fields). **16 new JVM tests; 399 JVM total / 0 failures; `assembleDebug` +
`testDebugUnitTest` + `:domain:test` green; 0 new Gradle deps; `android.location`/`CalendarContract`
confined to `:data:repository`.** `HandleUserCommandUseCase` untouched. **Block W DONE (2026-07-01):**
`LauncherUiState` now carries `suggestions`; the host `LauncherViewModel` owns the single suggestions source
and performs cache-first paint from `SuggestionsCacheRepository` followed by a fresh `SuggestionEngine`
result that supersedes rather than merges; `:feature:suggestions` stayed a stateless `SuggestionsRow`;
launcher-home rendering + suggestion tap routing are wired with no `feature→feature` edge. Background
completion added `SuggestionPrecomputeWorker` + `UsageCleanupWorker`, unique-periodic scheduling, boot
warmup via `RECEIVE_BOOT_COMPLETED`, and startup re-scheduling from `SidrLauncherApp`; precompute is
fail-closed and never schedules/runs when `aiSuggestionsEnabled == false`, on `LOW_END`, or while battery
saver is active. **Phase 7's user-facing part is therefore closed. Block V remains open as a separate
model/runtime track** (ONNX `TextEmbedder` impl + semantic re-rank; gated on OQ#3).

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
  Question #1 resolved (**amended multilingual 2026-06-29** — multilingual WordPiece teacher
  `bert-base-multilingual-uncased` → prune+distill+int8, pruned `vocab.txt` ~20–30k, `maxLen 48`),
  WordPiece **uncased** vocab, **7 (language-independent) classes**
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
  `input_ids`+`attention_mask`[+`token_type_ids` iff declared] int64 `[1,48]` (OQ#1 amended; was 32),
  `logits` float `[1,7]`
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
- **Phase 6 Block Q ✅ (2026-06-28)** — DeviceProfile detector + model management + WorkManager download
  gating. **OQ#2 branch = NOT resolved (expected):** `ModelDownloadConfig.INTENT_NLU_PENDING` is the
  single inert device/release-pending seam (blank URL/hash, `TODO(OQ#2)`, `isPinned=false`); the whole
  mechanism is JVM-tested vs fakes now, live download device-pending. `:core:android`: pure
  `DeviceProfileClassifier` (`(ram,cores)→DeviceProfile`; LOW_END `<2.5 GB` or `<4` cores, HIGH_END
  `≥5.5 GB`+`≥8` cores) + `(rawSignals)→DeviceCapability` (`nnapiAvailable=sdk≥29` hint, `thermalOk=status<SEVERE`
  w/ `-1` no-signal sentinel, `batteryOk=!powerSave`) + `DeviceProfileCacheMapping` (lossy LOW_END-vs-rest)
  + `AndroidDeviceProfiler : DeviceProfileProvider` (in-mem profile cache + write-through to the
  **pre-existing** `DeviceProfileCacheRepository` via `@ApplicationScope`; capability re-read per call) +
  `testImplementation(junit4)`. `:data:ai-local`: `ModelStore` (quarantine→SHA-256-verify→atomic
  `Files.move(ATOMIC_MOVE)`→ready, **never exposes unverified**, implements P's `LocalModelFiles`, vocab
  via injected `vocabOpener`/bundled `assets/nlu/`, also implements `ModelFilePresence`) + `Sha256Verifier`
  + `ModelDownloadScheduler` port + `ModelProvisioner` (`provision()`→`ProvisionResult`; idempotent,
  re-throws cancellation, permanent-vs-transient) + `ModelManager.ensureModel()` (**enqueue-gate-static-only §6.A**:
  `profile≠LOW_END && availability≠Available && config.isPinned` — thermal/battery are WM constraints +
  the per-inference re-check inside `OnnxIntentClassifier`, NOT the enqueue decision). `:domain` (rework):
  `ModelDownloader` + `ModelFilePresence` ports. `:data:repository`: `ModelAvailabilityRepositoryImpl`
  (shared `sidr_preferences`, `model_available_ids` stringSet **cross-checked vs `ModelFilePresence` disk
  truth** → all 3 Block-O states reachable, marker-but-missing = not-Available; `ALL_KEY_NAMES`, privacy
  guard green). `:data:ai-cloud` (rework): `KtorModelDownloader` plain class (HTTPS-only, retry taxonomy
  4xx/non-HTTPS→permanent / 5xx/network→transient, `@Provides`-wired in `:app`). `:app`: `@HiltWorker
  ModelDownloadWorker` (thin shell → `ModelProvisioner`, maps to `Result.success/retry/failure`, no
  foreground service) + `WorkManagerModelDownloadScheduler` (`enqueueUniqueWork` KEEP + CONNECTED/
  battery-not-low/storage-not-low constraints + EXPONENTIAL 30s backoff) + `SidrLauncherApp :
  Configuration.Provider`/`HiltWorkerFactory` + manifest `WorkManagerInitializer` removal
  (`tools:node="remove"`) + DI (`ModelProvisionProvidesModule`/`ModelProvisionBindsModule`,
  availability bound in `PersistenceBindsModule`). **Deliberate deviations (documented):** the `@HiltWorker`
  shell lives in `:app` (composition root, already kapt+Hilt) and the port fakes in `:data:ai-local-test`,
  to keep `:data:ai-local` kapt/HTTP-free; the runtime `ensureModel()` trigger is deferred to Block R
  (pairs with classifier consumption; inert under OQ#2). New deps = `androidx.work` 2.10.0 + `androidx.hilt`
  1.2.0 (`hilt-work` + compiler via kapt in `:app`) **only** (context7-verified). `:data:ai-local` gains no
  edge; `:data:ai-cloud` has **no `:data:ai-local` edge** (ports in `:domain`); `ai.onnxruntime` still
  confined to P's two shell files; `:domain` pure. **39 new JVM tests, 0 failures**; full
  `testDebugUnitTest` + `assembleDebug` **BUILD SUCCESSFUL**. **Reworked before close (review P1-1…P2-8):**
  context7-verified WM init pasted in ADR; availability disk cross-check (P1-2); downloader moved to
  `:data:ai-cloud` (P2-4); half-pinned-config `require` (P2-5); `noBackupFilesDir` confirmed (P2-6); retry
  taxonomy (P2-7). **Device/release-pending:** live download + real artifact URL/SHA-256 (OQ#2); **the
  pruned multilingual `vocab.txt` (data-driven ~20–30k, `en/ar/tr/ru`, byte-matched to the exported
  tokenizer) — OQ#1**; `AndroidDeviceProfiler` Android-API
  reads + thermal/battery transitions (SM-A325F). P's three seams now have prod impls →
  `OnnxIntentClassifier` bindable. Details: decisions.md "ADR 2026-06-28 — Block Q complete" + its
  "Rework before close" subsection. P's three seams now have prod impls → `OnnxIntentClassifier`
  bindable (consumed by Block R).
- **Phase 6 Block R ✅ (2026-06-29)** — wire NLU `IntentMatcher` source + docs-sync + **Phase 6 close**.
  `LayeredIntentMatcher : IntentMatcher` + `NluConfidenceCalibrator` (`:data:repository`, port-only, no
  `data→data` edge): **rule-first** — rule returned verbatim when `!isLowConfidence` (NLU never consulted,
  `< 10ms` + Phase-3 parity), NLU consulted only on low confidence; escape (`source==NLU && (conf==0f ||
  UnknownIntent)`) → rule stands; non-escape NLU wins iff calibrated conf clears `suggestThreshold`. **R1
  calibration = conservative band → Suggest (§5.A):** raw softmax remapped into `[suggest 0.50, autoExec
  0.85)` (always Suggests, never auto-executes a model-driven intent); `confidenceFloor` stays in
  `OnnxModelSpec`, calibrator floor a decoupled plain `Float` (0.60). DI (`:app`): `@RuleMatcher`/
  `@NluMatcher` qualifiers + `NluMatcherProvidesModule`; unqualified `IntentMatcher` →
  `LayeredIntentMatcher`; `HandleUserCommandUseCase` untouched. **§5.F deviation (recorded): `@NluMatcher`
  binds the self-gating `OnnxIntentClassifier` unconditionally** (availability flips at runtime → live
  re-check beats a stale graph-time NoOp swap), same `@Singleton` exposed as `@NluMatcher` +
  `SessionLifecycle`. R2.5: `SidrLauncherApp.onTrimMemory(≥TRIM_MEMORY_BACKGROUND)`/`onLowMemory()` →
  `releaseResources()` (`:app` holds only the ONNX-free seam). R3: `ensureModel()` fire-and-forget on
  `@ApplicationScope` (IO) from `onCreate()` (inert under OQ#2). **No-model parity** (shipping state):
  secondary always escapes → identical to rule-only. New JVM tests green (fast-path NLU-never-invoked,
  escape→rule, calibrated answer, no-model parity, calibrator boundaries); `assembleDebug` (Hilt graph
  valid) + full regression green. Two-port invariant intact; `:domain` pure; `ai.onnxruntime` still
  confined to P's two files; no new dep. Details: decisions.md "ADR 2026-06-29 — Block R complete + Phase
  6 close". **Phase 6 CLOSED (Blocks O → R). Next = deferred device-acceptance pass (SM-A325F, gated on
  OQ#1/#2) and/or Phase 7.**

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
| `LayeredIntentMatcher` (rule-first composite) + `NluConfidenceCalibrator` *(Block R ✅)* | `data/repository` |
| `OnnxIntentClassifier` (`@NluMatcher` + `SessionLifecycle`) + `OnnxTextEmbedder` *(Block V inert seam)* | `data/ai-local` |
| `@RuleMatcher`/`@NluMatcher` qualifiers + matcher DI swap + `onTrimMemory`/`ensureModel` wiring *(Block R ✅; lifecycle set updated in V)* | `app` |
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
| `ModelDownloader` port + `ModelFilePresence` port *(Block Q ✅, rework)* | `domain` |
| `ModelStore`/`Sha256Verifier`/`ModelProvisioner`/`ModelManager` + `ModelDownloadScheduler` port + `ModelDownloadConfig` *(Block Q ✅)* | `data/ai-local` |
| `AndroidDeviceProfiler` + pure `DeviceProfileClassifier`/`DeviceProfileCacheMapping` *(Block Q ✅)* | `core/android` |
| `ModelAvailabilityRepositoryImpl` (marker + disk cross-check) *(Block Q ✅)* | `data/repository` |
| `KtorModelDownloader` (HTTPS-only, retry taxonomy) *(Block Q ✅, rework)* | `data/ai-cloud` |
| `ModelDownloadWorker` (`@HiltWorker`) / `WorkManagerModelDownloadScheduler` + `Configuration.Provider`/`HiltWorkerFactory` *(Block Q ✅)* | `app` |
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
- Closed checklists *(**code-closed**; "closed" = the code phase, NOT the device debt — Phase 5/6
  device-acceptance items are still open and carried into Phase 7 Tracking, see below)*:
  [ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md)
  *(Phase 6 code-closed, Blocks O → R, 2026-06-29; device-pending: Block-P P5 + OQ#1/#2 real model)* ·
  [ai-context/phase-5-plan.md](ai-context/phase-5-plan.md)
  *(Phase 5 code-closed, Blocks I → N, 2026-06-27; device-pending: Block-J `SecretStoreInstrumentedTest`
  + Block-N N5)* · [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)
  *(Phase 4 closed, Blocks E → H, 2026-06-23)* ·
  [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md) *(Phase 3 closed 2026-06-21)*
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Active checklist: [ai-context/phase-7-voice-suggestions-plan.md](ai-context/phase-7-voice-suggestions-plan.md)
  *(Phase 7 — voice input + contextual suggestions, Blocks S → W; forks decided 2026-06-29; **Block S DONE
  2026-06-29** — pure `:domain` suggestion+voice contracts + `HeuristicSuggestionRanker` + fakes; **Block T DONE
  2026-06-30** — `AndroidSpeechInputSource` (`:core:android`) + `VoiceModule` DI + `RECORD_AUDIO` routed-education
  request flow + `refreshStatus()` debt discharged + launcher mic affordance, 9 new JVM tests / 383 JVM total,
  `assembleDebug` + `testDebugUnitTest` green, 0 new deps, `android.speech` confined to `:core:android`, real
  recognizer device-pending OQ#4; **Block U DONE 2026-06-30** — `TimeOfDaySuggestionProvider`/
  `UsageSuggestionProvider` (offline) + `CalendarSuggestionProvider`/`LocationSuggestionProvider` (opt-in,
  `:data:repository`) + `SuggestionEngineImpl` (aggregate→rank→persist, gated by `aiSuggestionsEnabled`) +
  Block-T request-flow template reused verbatim for calendar/location; privacy guard delivered as 4
  executable proofs incl. a reflection-based fix to Block L's `AiRequestGuardTest`; 16 new JVM tests /
  **399 JVM total**, `assembleDebug` + `testDebugUnitTest` + `:domain:test` green, 0 new deps,
  `android.location`/`CalendarContract` confined to `:data:repository`, real device reads device-pending;
  **Block W DONE 2026-07-01** — W-lite + W proper close the shipped launcher surface (single-owner
  suggestions state, cache→fresh supersede, stateless row, periodic precompute/cleanup, boot warmup);
  **Block V remains separate** — ONNX `TextEmbedder` impl + semantic re-rank; OQ#3 embedding model/host
  gates it)*
- Roadmap: [docs/roadmap.md](docs/roadmap.md)

## Do not

- Don't start Phase 5 (cloud AI) ahead of its own approved plan. Phase 4 (E → H) is closed;
  extend the existing persistence/hardening, don't re-scaffold it.
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets land in Phase 5 via a
  `SecureSecretStore` port (Fork 1).
