# Phase 7 — Voice input and contextual suggestions

> Format mirrors `phase-6-local-nlu-plan.md` / `phase-5-plan.md` / `phase-4-plan.md`: pre-flight
> repo-truth check → external-API verification → core stance → in-scope → hard invariants → block
> order + rationale → forks-before-code → open questions → pinned contracts → per-block sections
> (goal / depends-on / new files / checkbox steps / acceptance / demoable milestone) → frozen-forward →
> demoable milestones → tracking → per-block agent-model table → confirmed-decisions.
> Block lettering continues the alphabet (Phase 3 = A→D, Phase 4 = E→H, Phase 5 = I→N, Phase 6 = O→R),
> so **Phase 7 = Blocks S → W**.
> **Status (2026-07-01): FORKS DECIDED + BLOCKS S, T, U AND W COMPLETE.** All 11 forks below are **decided**. **Block S**
> (pure `:domain` contracts + `HeuristicSuggestionRanker` + fakes) is **done 2026-06-29** (S1–S6 ✅, 12 new JVM
> tests, no production file edited). **Block T** (voice input — `AndroidSpeechInputSource` in `:core:android`,
> `VoiceModule` DI, `RECORD_AUDIO` live request via the routed education flow, `refreshStatus()` dangerous-permission
> debt discharged, launcher mic affordance) is **done 2026-06-30** (T1–T6 ✅, 9 new JVM tests, 383 JVM total /
> 0 failures, `assembleDebug` + full `testDebugUnitTest` green, 0 new Gradle deps, `android.speech` confined
> to `:core:android`, `HandleUserCommandUseCase` untouched; real recognizer device-pending OQ#4). **Block U**
> (contextual suggestion engine — offline `TimeOfDaySuggestionProvider`/`UsageSuggestionProvider`, opt-in
> `CalendarSuggestionProvider`/`LocationSuggestionProvider`, `SuggestionEngineImpl` aggregate→rank→persist,
> the Block-T request-flow template reused verbatim for calendar/location) is **done 2026-06-30** (U1–U6 ✅,
> 16 new JVM tests, **399 JVM total / 0 failures**, `assembleDebug` + full `testDebugUnitTest` + `:domain:test`
> green, **0 new Gradle deps**, `android.location`/`CalendarContract` confined to `:data:repository`,
> `HandleUserCommandUseCase` untouched; privacy guard delivered as 4 executable proofs, see the Block U
> section). **Block W** is **done 2026-07-01** in two slices: **W-lite** closed the launcher-home user surface
> (`LauncherUiState.suggestions`, host-VM single owner, cache-first paint, fresh supersede, stateless
> `SuggestionsRow`, tap routing); **W proper** added periodic `SuggestionPrecomputeWorker` +
> `UsageCleanupWorker`, gate-before-enqueue scheduling, boot warmup, and docs sync. **Block V remains a
> separate model/runtime track** (ONNX `TextEmbedder` impl + semantic re-rank; gated on OQ#3) and does
> **not** block the user-facing Phase-7 close. Three pre-ADR
> items were closed before the
> flip: **co-residency** of the NLU + embedder ONNX
> sessions (§Block V — Option B, accepted-not-arbitrated), **suggestions single-source** (§F7-8 — owner =
> host `LauncherViewModel`/`LauncherUiState`, no parallel VM), and the **privacy-guard wording** (§F7-9 —
> "no new outbound category", not "voice never reaches cloud"). Two open questions gate the
> device/model-bearing blocks (OQ#3 embedding model + host → Block V; OQ#4 on-device STT availability
> across targets → Block T device acceptance); both gate the *real artifact / device run*, not the
> JVM-green wiring, exactly as OQ#1/#2 did in Phase 6.

## Pre-flight — repo-truth check (verified 2026-06-29, before any block)

This plan was written against the docs **and** the actual repo (codegraph + grep + file reads). The
checks below were run; **deltas are flagged inline and the plan is patched to reality**. Re-confirm the
ⓘ items at the start of the block that consumes them — they only change "create vs extend" and the
new-deps accounting, never the block structure.

- [x] **`:feature:suggestions` exists as an EMPTY scaffold.** It is `include`d in `settings.gradle.kts`
      (`:feature:suggestions`) and has a `build.gradle.kts` (`android.library` + `kotlin.android` +
      `kotlin.compose`, `namespace com.sidr.launcher.feature.suggestions`, `minSdk 28`, deps =
      `:core:common` + `:core:ui` + `:domain` + Compose BOM/ui/material3 + `lifecycle-viewmodel-compose`)
      but **zero `.kt` sources** (`feature/suggestions/src` has no files). ⓘ **Block W builds it out as a
      STATELESS `SuggestionsRow` composable only** (F7-8: the suggestion-list state is owned by the host
      `LauncherViewModel`, not a `SuggestionsViewModel`). Because the module hosts **no ViewModel** under
      that decision, it **does not gain Hilt** (`kapt`/`hilt.android`) — unlike `:feature:assistant` in
      Block N, which added Hilt precisely because it owns a VM. No new module is created. (Hilt is added
      only if a future standalone suggestions *screen* with its own VM lands — frozen-forward.)
- [x] **No `SpeechInputSource` / `SpeechRecognizer` anywhere.** Grep over the whole tree for
      `SpeechInputSource` / `SpeechRecognizer` is **empty**. `architecture.md:98` and `roadmap.md:67`
      both name `SpeechInputSource` as a **domain** interface over Android `SpeechRecognizer` with a
      no-op/fake impl in `:core:testing` — **aspirational, not built**. **Major delta:** Phase 7 must
      **create the `SpeechInputSource` port + the Android `SpeechRecognizer` impl + a no-op fake from
      scratch** (port in `:domain`, impl in `:core:android`, fake in `:core:testing`). Folded into Blocks
      S (port) + T (impl).
- [x] **Suggestion persistence ports already exist; the pipeline does not.** `:domain` has
      `SuggestionsCacheRepository` (DataStore display-repaint cache of `CachedSuggestion{label, actionId}`,
      bounded `MAX_CACHED_SUGGESTIONS`, impl in `:data:repository`), `SuggestionRankingRepository`
      (Room learning store of `SuggestionRankingRecord{actionId,label,score,lastUpdatedEpochMs}`, ordered
      by score desc, impl + retention cap 100 in `:data:repository`), and `UsageHistoryRepository`
      (`AppUsageRecord{packageName,lastUsedEpochMs,launchCount}`, recency/frequency — already powers the
      launcher grid sort). **Delta:** the **storage seams are built and tested**, but there is **no
      `Suggestion` domain model, no `SuggestionProvider`/`SuggestionEngine`/`SuggestionRanker` ports, and
      no context pipeline** — those are net-new (Block S → U). `CachedSuggestion`'s KDoc already pins the
      cache-vs-history split (display-only, no raw query / timestamps / location-or-calendar context /
      confidence) — Phase 7 must honour it.
- [x] **`TextEmbedder` port exists, impl deferred to Phase 7 by name.** `domain.ai.local.TextEmbedder`
      (`suspend fun embed(text): OperationResult<FloatArray>`) + `FakeTextEmbedder` in `:core:testing`.
      Its KDoc: "*Implementation deferred to Phase 7 (Fork P6-6): no ranking consumer exists in Phase 6 …
      the ONNX embedder impl will land behind this interface with zero domain change when Phase 7 ships.*"
      ⓘ Block V fills it **only if it ships a real reader** (the build-a-producer-with-no-reader
      discipline) — the reader is U's semantic re-rank seam.
- [x] **Permission framework is built with three DORMANT dangerous features waiting on Phase 7.**
      `PermissionFeature(requestable)` = `WALLPAPER(true)` + `VOICE_INPUT(false)` +
      `CALENDAR_SUGGESTIONS(false)` + `LOCATION_SUGGESTIONS(false)`; `AndroidPermissionChecker` already
      maps `VOICE_INPUT→RECORD_AUDIO`, `CALENDAR_SUGGESTIONS→READ_CALENDAR`,
      `LOCATION_SUGGESTIONS→ACCESS_FINE_LOCATION`. `PermissionEducationViewModel` is **hardcoded to
      `WALLPAPER`** (`private val feature = PermissionFeature.WALLPAPER`) with a comment that a future
      slice routes the feature in via nav-arg/`SavedStateHandle`. **`refreshStatus()` is upgrade-only and
      carries an explicit debt:** "*This guard is a partial fix sized to the current SET_WALLPAPER
      (normal-permission) scope; it must be revisited when the first dangerous permission lands
      (`RECORD_AUDIO`, Ph7).*" **Delta:** Phase 7 flips `VOICE_INPUT`/`CALENDAR_SUGGESTIONS`/
      `LOCATION_SUGGESTIONS` to `requestable = true`, routes the feature into the education VM, and
      discharges the `refreshStatus()` dangerous-permission debt (Block T for audio, Block U for
      calendar/location). `BIND_ACCESSIBILITY_SERVICE` stays **absent** (Ph8).
- [x] **WorkManager is fully wired; only model-download work exists.** `SidrLauncherApp :
      Configuration.Provider` provides `HiltWorkerFactory`; manifest `WorkManagerInitializer` is removed
      (`tools:node="remove"`); `WorkManagerModelDownloadScheduler` does `enqueueUniqueWork(KEEP)` +
      `Constraints(CONNECTED, batteryNotLow, storageNotLow)` + `EXPONENTIAL 30s`; `ModelDownloadWorker :
      CoroutineWorker @HiltWorker` lives in `:app`. ⓘ **No new WorkManager infra in Phase 7** — new
      periodic workers (suggestion pre-compute, usage cleanup) reuse the existing factory/config and the
      `enqueueUniquePeriodicWork` API. `androidx.work` 2.10.0 + `hilt-work` 1.2.0 already in the catalog.
- [x] **Model-provisioning machinery is general (bytes-in, SHA-256-verified, atomic-rename).** `ModelStore`
      (quarantine→verify→atomic rename, never exposes unverified), `Sha256Verifier`, `ModelProvisioner`,
      `ModelManager` (gate-before-enqueue), `ModelDownloadConfig.INTENT_NLU_PENDING` (inert OQ#2 seam),
      `KtorModelDownloader` (HTTPS-only, retry taxonomy), `ModelAvailabilityRepositoryImpl` (marker +
      disk cross-check). ⓘ **An embedding model reuses this verbatim** — a *second* `ModelId` +
      `ModelDownloadConfig` (OQ#3 seam), the *same* store/verifier/worker. No new download mechanism in
      Block V.
- [x] **The three-port invariant.** `IntentMatcher` (→ `IntentMatchResult`) and `GenerativeAiEngine`
      (→ `Flow<AiChunk>`) are separate ports (CLAUDE.md hard rule). Phase 7 adds a **third concern —
      *suggestion*** — that is neither: `SuggestionEngine` (→ a ranked `List<Suggestion>` /
      `Flow<List<Suggestion>>`) is its own port. **Voice is an input *modality*, not a fourth pipeline:**
      `SpeechInputSource` produces *text* that feeds the **existing** command-input → `IntentMatcher`
      path (and/or the assistant prompt). It must not become a matcher or an engine.
- [x] **`:core:testing` is JVM-only; `:core:android` has no test deps.** Fakes are pure JVM (no Android
      variants), so `FakeSpeechInputSource` is a pure-JVM scripted/flow fake. `AndroidSpeechInputSource`
      (like `AndroidPermissionChecker`/`AndroidConnectivityChecker`/`AndroidDeviceProfiler`) is **not**
      unit-tested in `core/android`; it is covered via its fake + a device run.
- [x] **`:domain` purity guard.** `domain/build.gradle.kts` = stdlib + coroutines only. All new
      suggestion/voice **ports + models + ranking policy live in `:domain`** (pure); impls in
      `:data:*` / `:core:android`. `Flow` allowed.
- [x] **Manifest today:** `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `SET_WALLPAPER` declared.
      ⓘ **Genuinely new manifest entries for Phase 7 = `READ_CALENDAR`, `ACCESS_FINE_LOCATION`, and
      `RECEIVE_BOOT_COMPLETED`** (boot warmup). `RECORD_AUDIO` is **already declared** (Block N added it
      for the assistant) — Block T only adds the runtime *request flow*, not the manifest line.

### External-API verification (context7, 2026-06-29)

- **WorkManager periodic work — verified against `/androidx/androidx`.** `PeriodicWorkRequest.Builder(
  Worker::class, interval, TimeUnit)`, `WorkManager.enqueueUniquePeriodicWork(name,
  ExistingPeriodicWorkPolicy, request)`, `CoroutineWorker.doWork(): Result`, and the documented
  `Constraints` (`setRequiresBatteryNotLow`, `setRequiresStorageNotLow`, `setRequiresDeviceIdle`,
  `setRequiredNetworkType`). `WorkInfo` stop-reason constants include battery / storage / device-idle /
  background-restriction. Minimum periodic interval is **15 minutes** (framework floor — a "daily"
  pre-compute uses a ~24h interval with a flex window). `work-runtime` line ~2.10–2.11. These confirm the
  Block-W pre-compute/cleanup workers use current API, not training memory.
- **`android.speech.SpeechRecognizer` — ⚠ NOT an androidx library; context7 returns only the Leanback
  *wrappers* (`SearchBar.setSpeechRecognizer`, deprecated `SpeechRecognitionCallback`), not the framework
  class.** Treat the framework API as **re-verify-at-Block-T**, the same discipline Phase 6 applied to the
  thin `ai.onnxruntime` Java coverage (Fork P6-5) and Phase 5 applied to Ktor `HttpTimeout`. Framework
  reality the plan is built on (web/SDK-doc knowledge, to be re-confirmed against the Android Javadoc at
  Block T): `SpeechRecognizer.createSpeechRecognizer(context)`; **on-device**
  `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` (API 31+); availability
  `SpeechRecognizer.isOnDeviceRecognitionAvailable()` / `isRecognitionAvailable(context)`;
  `RecognitionListener` callbacks (`onResults`/`onPartialResults`/`onError`/`onReadyForSpeech`/
  `onEndOfSpeech`); `RecognizerIntent.EXTRA_PREFER_OFFLINE` (API 23+) to bias offline; `RecognizerIntent.
  EXTRA_LANGUAGE`. **`SpeechRecognizer` is main-thread-bound** (its methods must be called on the main
  looper) — the `:core:android` impl must marshal to main and bridge callbacks into a `Flow` via
  `callbackFlow` (the `AndroidConnectivityChecker` precedent), `destroy()` on `awaitClose`. Error codes
  (`ERROR_NO_MATCH`, `ERROR_RECOGNIZER_BUSY`, `ERROR_INSUFFICIENT_PERMISSIONS`, `ERROR_NETWORK`, …) map to
  a pure `SpeechRecognitionError` taxonomy in `:domain`.

Run order: this check is done; Blocks S, T, U and W are complete. **Block V is intentionally separate**
and remains gated on OQ#3. (Block T re-confirmed the `SpeechRecognizer` framework signatures against the
Android Javadoc at its Step 0, as planned — context7's Leanback-only coverage was not trusted.)

---

## Core stance for this phase

Phase 7 makes the launcher **proactive and hands-free**, still offline-first. Two independent
capabilities land behind ports:

1. **Voice input** — a `SpeechInputSource` *input modality* that turns speech into the same normalized
   text the keyboard produces, then feeds the **existing** intent pipeline (offline rules → NLU → cloud
   assistant). **On-device recognition is preferred** (`EXTRA_PREFER_OFFLINE` / on-device recognizer);
   voice is **never** a new generative path and **never** sends audio to our backend (we have none — BYOK).
   Microphone is opt-in via the existing permission-education flow (`RECORD_AUDIO`, now a live request).

2. **Contextual suggestions** — a `SuggestionEngine` (a *third* port, distinct from matching and
   generation) that aggregates **offline-first** context signals (time-of-day, recent/frequent usage from
   Room) plus **opt-in, permission-gated** signals (calendar, location), ranks them, and surfaces a small
   suggestion row on the launcher home. **Suggestions are useful with zero sensitive data**: a denied
   calendar/location permission removes exactly that signal and the engine still produces usage/time
   suggestions — degrade, never block. **Sensitive context is never persisted raw and never sent to
   cloud** (the Block-L outbound allow-list stays fail-closed and unchanged).

Both are **capability-gated and feature-flagged**: `FeatureFlags.aiSuggestionsEnabled` gates the
suggestion surface; `LOW_END` and battery-saver throttle background pre-compute. The optional **ONNX
`TextEmbedder`** (Phase 6's deferred port) is filled **only** with a concrete reader — a semantic
re-rank of suggestion candidates — reusing Phase 6's model-store/WorkManager/gate machinery; if its model
host is unresolved (OQ#3) the embedder stays an inert seam and the heuristic ranker is the shipping path,
exactly as the NLU model is inert under OQ#2 today.

Everything degrades to the current behaviour when off: no mic permission → keyboard only; flag off / no
context → today's plain grid; no embedder → heuristic ranking. Nothing Phase 7 adds may regress cold
start, the `< 10ms` rule path, or the offline launcher core.

## In scope (unfreezing what Phases 4/6 deferred)

- **Suggestion + voice domain contracts (pure):** `Suggestion` model + provenance; `SuggestionContext`/
  context-signal value types; `SuggestionProvider` / `SuggestionRanker` / `SuggestionEngine` ports; a pure
  heuristic ranking policy; `SpeechInputSource` port + `SpeechRecognitionState`/`SpeechRecognitionError`;
  fakes. (Block S)
- **Voice input** in `:core:android`: `AndroidSpeechInputSource` over `SpeechRecognizer` (on-device
  preferred, callback→`Flow`, lifecycle/destroy, error mapping); no-op/fake; the **`RECORD_AUDIO` live
  request flow** + `refreshStatus()` dangerous-permission hardening (the deferred Block-H debt); a mic
  affordance on the command-input surface. (Block T)
- **Contextual suggestion pipeline** in `:data:repository` (+ `:core:android` for platform reads):
  offline providers (time, recent/frequent usage), opt-in providers (calendar, location), the
  `SuggestionEngine` aggregation + heuristic ranking, `SuggestionRankingRepository` learning + the
  `SuggestionsCacheRepository` **cold-start content-restore** (the deferred Fork-6 half); calendar/location
  **request flows + education**. (Block U)
- **ONNX `TextEmbedder` impl** in `:data:ai-local` (lazy, gated, behind the existing port, reusing the
  session + model-store infra) **with a concrete reader** — semantic re-rank of suggestion candidates;
  inert seam if OQ#3 unresolved. (Block V)
- **Suggestions UI + background pre-compute** in `:feature:suggestions` + `:app`: the suggestion row on
  the launcher home (grow `LauncherUiState`), the **periodic** `SuggestionPrecomputeWorker` + the
  `UsageCleanupWorker` (WorkManager beyond model download), `RECEIVE_BOOT_COMPLETED` warmup; docs-sync;
  Phase close. (Block W)

## Hard invariants (the plan must obey)

- `:domain` stays pure: **stdlib + kotlinx-coroutines only**. No Android, no `SpeechRecognizer`, no
  WorkManager, no ONNX, no `core/*`. All new ports + the ranking policy + the suggestion/voice/context
  models live in `:domain`; impls in `:data:*` / `:core:android`. (`Flow` allowed.)
- **Three concerns, three ports.** `IntentMatcher` ≠ `GenerativeAiEngine` ≠ `SuggestionEngine`. Voice is
  an input modality feeding the existing pipeline, **not** a new matcher/engine. `HandleUserCommandUseCase`
  stays untouched.
- **Offline-first + no AI on cold start.** Suggestion pre-compute is background/WorkManager, never on the
  cold-start path; the suggestion row first-paints from the DataStore **cache** (repaint), a fresh load
  supersedes it (never merged) per `architecture.md`'s reconciliation rule. The launcher core renders the
  grid with or without suggestions.
- **Privacy is fail-closed.** Calendar/location signals are **opt-in** (permission-gated), **never
  persisted raw** (only a derived display-safe `Suggestion{label, actionId}` may be cached — the
  `CachedSuggestion` contract), and **never added to a cloud `AiRequest`** (the Block-L
  `OutboundContextPolicy` allow-list `{USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS}` is
  **unchanged** — a guard test re-asserts it). The guard's invariant is **"no *new outbound category*
  (calendar event data, location coordinates, raw audio / a standalone transcript field) enters the
  allow-list"** — **not** "voice never reaches the cloud": voice-recognized text rides the **existing
  `USER_COMMAND` channel byte-for-byte like keyboard text** and is explicitly permitted (F7-9). **No raw
  voice persisted; no user text / recognition transcript in logs.** Block F SEARCH/UNKNOWN redaction still
  holds.
- **Permissions never block startup; each denial disables exactly its feature.** `RECORD_AUDIO` denied →
  keyboard input only; `READ_CALENDAR`/`ACCESS_FINE_LOCATION` denied → that signal absent, suggestions
  still produced; `RECEIVE_BOOT_COMPLETED` is a normal permission (warmup best-effort). Accessibility
  remains **absent** (Ph8). The dangerous-permission `PERMANENTLY_DENIED` lifecycle
  (`shouldShowRequestPermissionRationale`) is handled (the deferred Block-H debt).
- **Capability/flag gating.** `aiSuggestionsEnabled` gates the surface; `LOW_END` + battery-saver throttle
  pre-compute (no `LOW_END` background by default — `architecture.md` rule); the ONNX embedder is gated by
  the **same** `LocalInferenceGate` + `DeviceProfile` as the NLU (LOW_END never loads it).
- **Performance budgets are hard** (`architecture.md:45-58`): cold start `< 400ms` and the `< 10ms` rule
  path unaffected (suggestions are async, off the critical path); embedder inference on `MID_RANGE` shares
  the `< 150ms` ONNX budget (device measurement, not a JVM assert); per-profile heap ceilings respected
  (embedder closed under `onTrimMemory` like the NLU session).
- **Co-residency of the two ONNX sessions (NLU + embedder) is NOT coordinated — decided Option B (closed
  2026-06-29).** Each session is held by its **own** instance with its **own**
  `SessionLifecycle.releaseResources()` (verified in the repo: `OnnxIntentClassifier` and the future
  `OnnxTextEmbedder` are distinct `SessionLifecycle` holders; there is **no cross-session arbiter** today,
  and the plan does **not** build one). The two are not co-resident **by design of *when* each runs**, not
  by a lock: the embedder is consumed by the **deviceIdle** background pre-compute worker (Block W) when
  the foreground command path is idle and the NLU session has already been torn down by its
  sustained-gate-off (~30s debounce) / `onTrimMemory` path; both also tear down together on
  `onTrimMemory(≥TRIM_MEMORY_BACKGROUND)`/`onLowMemory`. A rare brief overlap (a live semantic re-rank
  while NLU is warm) is **explicitly accepted, not arbitrated**. The combined `< 150MB` MID_RANGE
  footprint is a **device-pending OQ#3 measurement**; a single-resident-session arbiter (one session
  evicting the other) is the **frozen-forward escalation** taken only if that measurement shows a breach —
  never built speculatively.
- **WorkManager:** **no `LOW_END` background by default**, battery-saver respected, **idempotent +
  cancellable**, **no foreground service**; periodic work is `enqueueUniquePeriodicWork` (no duplicate
  chains).
- Expected failures are **values, never thrown to UI**: `SpeechInputSource` emits a terminal
  `SpeechRecognitionState.Error(SpeechRecognitionError)` (the `Flow<AiChunk>` precedent —
  terminal-as-value), repositories/use-cases return `OperationResult`, the embedder returns
  `OperationResult` and degrades the ranker to heuristic on failure.

---

## Block order and rationale

```
S  Suggestion + voice domain contracts (pure)  — Suggestion/context model + SuggestionEngine/Provider/
   Ranker ports + SpeechInputSource port + heuristic ranking policy + fakes; lowest risk
T  Voice input (SpeechInputSource)             — AndroidSpeechInputSource over SpeechRecognizer, no-op
   + RECORD_AUDIO request flow                    fake, RECORD_AUDIO live request, refreshStatus() debt,
                                                  mic affordance; platform-risk (framework recognizer)
U  Contextual suggestion engine                — offline + opt-in context providers, SuggestionEngine
   + context sources + cache-restore             aggregation + heuristic rank, ranking/cache persistence,
                                                  calendar/location request flows
V  ONNX TextEmbedder impl + semantic re-rank   — fill the deferred port behind a real reader, reuse
                                                  Phase-6 session/model-store/gate; inert seam if OQ#3 open
W  Suggestions UI + WorkManager pre-compute    — feature/suggestions screen + launcher-home row,
   + boot warmup + docs-sync + close              periodic pre-compute + usage cleanup workers, boot
                                                  warmup, docs-sync, ADRs, Phase 7 close
```

- **S first** — pure JVM, everything else compiles against it; the `Suggestion`/context model + the three
  suggestion ports + the `SpeechInputSource` shape + the heuristic ranking policy are decided here. Lowest
  risk; a wrong port shape is the most expensive mistake.
- **T before U/W** — voice is **independent** of the suggestion pipeline and is the smaller, self-contained
  capability; doing it second lands the `RECORD_AUDIO` live-request flow + the `refreshStatus()` hardening
  early (they are reused as the template for U's calendar/location request flows). T is the platform-risk
  block (framework recognizer, main-thread, callback→Flow) and is fully port-gated, so U/V can be
  fake-tested independent of T's device run.
- **U before V** — V's embedder must have a **reader**; U builds the suggestion engine + the heuristic
  ranker + the explicit *re-rank seam* the embedder plugs into. The launcher ships correctly on heuristic
  ranking alone; V is a gated enhancement.
- **V before W** — W surfaces and schedules the pipeline; the (optional) embedder must exist behind its
  gate before the pre-compute worker can opportunistically use it. V is interface-gated so W's worker
  logic is fake-tested regardless of OQ#3.
- **W last** — the UI + periodic workers + boot warmup + docs-sync + phase close; the one
  correctness-sensitive spot (cold-start cache-vs-fresh reconciliation; no `LOW_END` background) is
  reviewed on Opus.

---

## Fork decisions (decided 2026-06-29 — the Phase-6 discipline; three pre-ADR items closed: co-residency §Block V, suggestions single-source §F7-8, privacy-guard wording §F7-9)

### Fork F7-1 — Voice scope — **decided: STT → text → existing pipeline; on-device preferred; never a new generative path, never our-backend audio**
`SpeechInputSource` returns recognized **text** (+ partials), which is fed into the **existing**
command-input → `IntentMatcher` flow (and may pre-fill the assistant prompt). It is an **input modality**,
not a matcher or engine — the three-port invariant holds. **On-device recognition is preferred**
(`createOnDeviceSpeechRecognizer` where available + `EXTRA_PREFER_OFFLINE`); the system recognizer (which
may use Google's cloud STT) is the fallback only when on-device is unavailable, and that fallback is the
**user's own** Google service, never our (nonexistent) backend — consistent with BYOK. **Raw audio is
never persisted; transcripts are never logged.** Wake-word / always-listening is **frozen-forward**.

### Fork F7-2 — Where voice lands — **decided: a mic affordance on the launcher command input (primary) reusing one `SpeechInputSource`; assistant prompt mic is a thin reuse**
The launcher command-input row gets a mic button: tap → (permission-gated) start recognition → partials
stream into `commandInput` (the existing `SavedStateHandle`-backed field) → final result submits through
the **unchanged** command path. The assistant screen may reuse the **same** injected `SpeechInputSource`
to fill its prompt, but the canonical surface is the launcher. One port, one Android impl, two optional
call sites — no duplicated recognizer lifecycle. Keeps scope bounded and avoids a second mic stack.

### Fork F7-3 — `RECORD_AUDIO` request flow + the `refreshStatus()` dangerous-permission debt — **decided: full dangerous-permission lifecycle, discharging the Block-H partial fix**
`PermissionFeature.VOICE_INPUT` flips to `requestable = true`. The education VM stops being hardcoded to
`WALLPAPER` — the feature is routed in (nav-arg / `SavedStateHandle`, the comment's own escape hatch). The
mic affordance launches the `ActivityResultContracts.RequestPermission` dialog (education ≠ request, Fork
5 still holds); the result refines `DENIED → PERMANENTLY_DENIED` via
`shouldShowRequestPermissionRationale` (`onPermissionResult` already models this). **The `refreshStatus()`
upgrade-only guard is revisited for the dangerous case** (its own KDoc names this Phase): a permanently-
denied mic shows a "open system Settings" deep-link path rather than a dead re-request. This is the
template Block U reuses for calendar/location.

**Decision (confirmed Block U, applies retroactively to T's `VOICE_INPUT` too): the per-feature
"dismissed" flag is intentionally NOT persisted for `VOICE_INPUT`/`CALENDAR_SUGGESTIONS`/
`LOCATION_SUGGESTIONS` — their feature names are forbidden terms in `PrivacyInventoryGuardTest`'s own
denylist (`"voice"`, `"calendar"`, `"location"`), so a DataStore key named `perm_dismissed_voice`/
`_calendar`/`_location` would fail that guard outright. `PermissionPrefsRepositoryImpl.keyFor()` returns
`null` for all three (only `WALLPAPER` has a key); `isDismissed()` always emits `false` and
`setDismissed()` is a no-op `Success` for them. Product consequence, accepted as a trade-off: closing the
education sheet ("don't show again") for these three features does NOT survive past the current
ViewModel instance — the rationale reappears on the next trigger, every time. This is consistent with the
phase's fail-closed spirit (a dangerous-permission feature leaves no disk trace of user interaction with
it) and is a deliberate trade-off, not a bug — do not "fix" it by inventing a denylist-safe key name.

### Fork F7-4 — Suggestion context sources — **decided: offline-first always-on (time + usage), calendar/location opt-in + degrade-to-nothing**
- **Always-on, zero-permission:** time-of-day bucket (morning/work/evening/night) + recent/frequent usage
  (from `UsageHistoryRepository` — already Room-backed and grid-proven). These alone produce useful
  suggestions on a fresh device with every optional permission denied.
- **Opt-in, permission-gated:** `CALENDAR_SUGGESTIONS` (next event → its app / a "join"/"navigate" action)
  and `LOCATION_SUGGESTIONS` (coarse place bucket → context). Each `SuggestionProvider` returns **empty**
  when its permission is not `GRANTED` — the engine simply has one fewer signal. **No sensitive raw value
  (event title, coordinates) ever leaves its provider** — providers emit only a derived display-safe
  `Suggestion{label, actionId}`. **Privacy guard test** asserts the suggestion path persists/sends nothing
  beyond `CachedSuggestion`'s allowed fields and that no calendar/location term reaches a cloud `AiRequest`.

### Fork F7-5 — Ranking — **decided: pure heuristic `SuggestionRanker` first (JVM-tested); ONNX semantic re-rank is an optional gated layer (Block V)**
The shipping ranker is a **pure, deterministic, JVM-tested** policy: weight by recency + frequency + a
time-of-day prior + per-source weights, dedup by `actionId`, bound to N (≤ `MAX_CACHED_SUGGESTIONS`). It
needs no model and runs on every device. Block V's embedder adds an **optional** re-rank — semantic
similarity between the current context/typed prefix and candidate labels — that only engages on
MID/HIGH + model-available + thermal/battery-OK, and **falls back to the heuristic order** otherwise. The
heuristic order is the contract; semantic is a bonus, exactly as CPU-vs-NNAPI in Phase 6.

### Fork F7-6 — `TextEmbedder` impl — **decided: build it behind a real reader, reuse Phase-6 infra, inert seam if OQ#3 open**
`OnnxTextEmbedder : TextEmbedder` in `:data:ai-local`: lazy single session on `Dispatchers.Default`,
`Mutex`-guarded, per-call `LocalInferenceGate` re-check, `AutoCloseable` + the existing `SessionLifecycle`
seam (so `onTrimMemory` already tears it down), graceful degrade (`OperationResult.Failure` → heuristic
ranker), no text logged. Its model is provisioned by the **existing** `ModelStore`/`ModelProvisioner`/
WorkManager with a **second** `ModelId` + `ModelDownloadConfig` (`EMBEDDING_PENDING`, blank URL/hash,
`isPinned=false` — the OQ#3 inert seam, exactly like `INTENT_NLU_PENDING`). **The reader is U's re-rank
seam** — no producer ships without it. If OQ#3 is unresolved at Block V the embedder is wired + JVM/fake-
tested but inert (no model → port returns `Failure`/degrades), and the heuristic ranker is the shipping
path. ONNX stays confined to `:data:ai-local`.

### Fork F7-7 — WorkManager — **decided: periodic pre-compute + usage cleanup, battery-aware, no `LOW_END`, reusing the existing factory**
- `SuggestionPrecomputeWorker : CoroutineWorker @HiltWorker` (in `:app`, the `ModelDownloadWorker`
  precedent): `enqueueUniquePeriodicWork(KEEP/UPDATE)` with a ~24h interval, `Constraints(batteryNotLow,
  storageNotLow)` + **`setRequiresDeviceIdle(true)`** for the daily pre-compute (verified API), **no
  network** (suggestions are local). Runs the `SuggestionEngine`, writes ranked results to
  `SuggestionRankingRepository` + the display cache. **Gate-before-enqueue:** `LOW_END` (or
  `aiSuggestionsEnabled == false`) never schedules.
- `UsageCleanupWorker : CoroutineWorker @HiltWorker`: periodic retention sweep of the history stores
  (the row-count caps are already enforced on write; this is the time-based complement). Idempotent +
  cancellable + no foreground service.
- **Boot warmup:** a `RECEIVE_BOOT_COMPLETED` receiver (gated, normal permission) that
  re-`enqueueUniquePeriodicWork`s the pre-compute after a reboot — best-effort, never required for
  correctness.

### Fork F7-8 — Suggestions surface — **decided: single owner = `LauncherUiState.suggestions` on the host `LauncherViewModel`; `:feature:suggestions` is a STATELESS row; cache-restore first-paint (the deferred Fork-6 half)**
**Single source of truth, closed 2026-06-29 (`architecture.md:229`).** The suggestion-list state has **one
owner: the host `LauncherViewModel`**, which gains a `suggestions` field on `LauncherUiState` fed by an
injected `SuggestionEngine` (honours `architecture.md:249` "`LauncherUiState` grows when that phase
lands"). **There is NO `SuggestionsViewModel` holding a parallel `StateFlow<List<Suggestion>>`** — the
earlier draft's "own `StateFlow`" is **dropped** because it would duplicate the concern (two sources for
one suggestion list). `:feature:suggestions` therefore contributes **only a stateless presentational
`SuggestionsRow(suggestions, onSuggestionTap)` composable** that the `:app` launcher host renders above
the grid from `LauncherUiState.suggestions` (no `feature→feature` edge; no business logic in the row).
Because the module hosts no ViewModel under this decision, it **does not gain its own Hilt graph** for a
parallel VM (see F7-11). **This ownership is pinned in Block S (contracts) and must not drift past W1.**
**Cold start** first-paints from `SuggestionsCacheRepository` (the display cache), then a fresh
`SuggestionEngine` load supersedes it — **never merged** (the `architecture.md` reconciliation rule:
`SavedStateHandle` owns transient input/route, the DataStore cache owns content first-paint). This
activates the Fork-6 content-restore deferred from Phase 4. Tapping a suggestion routes through the
**existing** `ExecutableAction`/`HandleUserCommandUseCase` path (`actionId` → launch/route).

### Fork F7-9 — Privacy — **decided: outbound allow-list unchanged; the guard asserts "no NEW outbound category", not "voice never reaches cloud"; sensitive context never persisted/sent/logged; inventory guards extended**
The Block-L `OutboundContextPolicy` allow-list stays `{USER_COMMAND, STATIC_SYSTEM_PROMPT,
GENERATION_LIMITS}` — **Phase 7 adds nothing to a cloud `AiRequest`**. **Guard-test invariant corrected
(closed 2026-06-29):** voice is an input *modality* — recognized text legitimately flows out as
`USER_COMMAND`, **byte-identical to keyboard text**, so a test asserting "voice text never reaches the
cloud" would assert something **false** and would fail the moment the user dictates a command they could
equally have typed. The invariant is therefore phrased as **"no *new outbound category* enters the
allow-list / outbound field inventory"**: specifically **calendar event data, location coordinates, and
raw audio / any standalone transcript field** must never appear in an `AiRequest` — while
voice-as-`USER_COMMAND` is **explicitly permitted** (it is not a new category). The guard re-asserts (1)
the allow-list set is unchanged, and (2) no calendar/location/raw-audio term appears in the outbound field
inventory — **and a positive regression keeps a voice-derived command flowing as `USER_COMMAND`** (the
Block-L precedent that kept "calendar" inside a *user command* legal). Any **new** DataStore keys (e.g. a
per-source suggestion-consent flag, if added beyond the existing `aiSuggestionsEnabled`) register in
`ALL_KEY_NAMES`; any **new** Room table registers in `TABLE_NAMES` (the Phase-4 `PrivacyInventoryGuardTest`
precedent). Raw voice/calendar/location values are **excluded from persistence entirely** — only derived
`CachedSuggestion{label, actionId}` is cached.

### Fork F7-10 — Testing strategy — **decided: interface-gate everything; framework + real ONNX only on device**
`SpeechRecognizer` and the real ONNX embedder cannot run on the JVM. **Every JVM test exercises ports/
fakes.** `:core:testing` gets `FakeSpeechInputSource` (scripts a partial→final / error sequence) and
reuses `FakeTextEmbedder`. JVM coverage: the heuristic `SuggestionRanker` (table-driven, like the intent
matcher), the `SuggestionEngine` aggregation (provider mix, opt-in absence, dedup/bound), the cache-vs-
fresh reconciliation, the worker enqueue/idempotency against fakes, the permission request-flow VM logic.
The **`AndroidSpeechInputSource` device run + the embedder `< 150ms` device measurement** are
**device-pending** acceptance items on SM-A325F, never JVM asserts (the Block-J/N/P precedent).

### Fork F7-11 — New dependencies — **decided: none beyond manifest permissions**
- **`SpeechRecognizer`** = Android **framework** (no dependency). **WorkManager / `hilt-work`** =
  **already in the catalog** (Phase 6). **ONNX** = already wired in `:data:ai-local`. **No new Gradle
  deps** are expected.
- **New manifest permissions:** `READ_CALENDAR`, `ACCESS_FINE_LOCATION`, `RECEIVE_BOOT_COMPLETED`
  (`RECORD_AUDIO` already present). Each declared but **never requested at startup** — only on feature
  trigger (the Block-G rule).
- **`:feature:suggestions` does NOT gain Hilt** under F7-8 (single-owner decision): it ships a stateless
  `SuggestionsRow` composable with no ViewModel, so no `kapt`/`hilt.android` is added. (Should a future
  standalone suggestions screen with its own VM land — frozen-forward — it would add Hilt then, mirroring
  the Block-N `:feature:assistant` change; catalog entries already exist, no new versions.)
- **R8/ProGuard for ONNX (embedder) = Ph9** (unchanged); debug builds (this phase) need no keep rules.
- **`EncryptedSharedPreferences` / `security-crypto`** remain **forbidden**.

### Open questions (gating — must be closed before the owning block)

3. **Embedding model + tokenizer + host (gates Block V).** Which embedding model (a small multilingual
   sentence-embedding ONNX — e.g. a distilled MiniLM-class encoder int8, reusing the WordPiece tokenizer
   if compatible), its label-free output contract (`[1, hiddenDim]` pooled vector), and its **host +
   pinned SHA-256** are undecided — the same shape as Phase 6 OQ#1 (model) + OQ#2 (host). Until resolved,
   `ModelDownloadConfig.EMBEDDING_PENDING` is an inert seam (blank URL/hash, `isPinned=false`) and the
   heuristic ranker ships. **The dimensionality + pooling must be pinned before any vector is consumed**
   (a `assert_embedding_contract` cross-check, the `assert_onnx_contract` precedent).
4. **On-device STT availability across target devices (gates Block T device acceptance).** On-device
   recognition (`createOnDeviceSpeechRecognizer`) and language packs vary by OEM/Android version; the
   SM-A325F (Android 13) behaviour + the API-28/29 fallback path (no on-device recognizer →
   `EXTRA_PREFER_OFFLINE` on the system recognizer, or graceful "voice unavailable") must be measured on
   device. The `SpeechInputSource` contract (availability probe + graceful unavailable state) is decided
   in Block S/T regardless; only the device matrix is pending.

### Pinned contracts (decided 2026-06-29)

| Item | Value |
|---|---|
| `SpeechInputSource` | `fun isAvailable(): Boolean` + `fun listen(languageTag: String?): Flow<SpeechRecognitionState>` (cold flow; `Partial(text)` → `Final(text)` → terminal; `Error(SpeechRecognitionError)` terminal-as-value); cancel = collect-cancel → `destroy()` |
| `SpeechRecognitionState` | `Ready` / `Partial(text)` / `Final(text)` / `Error(SpeechRecognitionError)` / `Ended` |
| `SpeechRecognitionError` | **`enum`** (UPPER_SNAKE_CASE, like `AiStopReason`/`ModelAvailability`): `UNAVAILABLE` / `PERMISSION_DENIED` / `NO_MATCH` / `BUSY` / `NETWORK` / `TIMEOUT` / `UNKNOWN` — pure; Block T maps `android.speech.SpeechRecognizer` error-codes to these names in `:core:android`. *(Plan draft used PascalCase; shipped form is enum/UPPER_SNAKE_CASE — explicit callout for Block T.)* |
| `Suggestion` | `data class Suggestion(label, actionId, source: SuggestionSource, score: Double)` — display-safe; `actionId` offline-resolvable (package / route) |
| `SuggestionSource` | `RECENT_USAGE` / `FREQUENT_USAGE` / `TIME_OF_DAY` / `CALENDAR` / `LOCATION` / `SEMANTIC` |
| `SuggestionProvider` | `suspend fun provide(context: SuggestionContext): List<Suggestion>` (empty when its permission/signal absent — never throws) |
| `SuggestionEngine` | `fun suggestions(): Flow<List<Suggestion>>` + `suspend fun refresh(): OperationResult<List<Suggestion>>` (aggregate enabled providers → rank → bound) |
| `SuggestionRanker` | pure `fun rank(candidates, context): List<Suggestion>` (heuristic; semantic re-rank is a decorator) |
| Embedding output | pooled `FloatArray` of pinned `hiddenDim`; `assert_embedding_contract` hard-fails on desync (OQ#3) |
| Cloud outbound | **unchanged** — allow-list `{USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS}`; suggestions/context add **no new category**; voice text rides the existing `USER_COMMAND` channel (not a new category) — guard = "no calendar/location/raw-audio category enters outbound" |

### Device / release-pending (Phase 7)

- The real **embedding `model.onnx` + its host/pinned SHA-256** (OQ#3), provisioned through the existing
  WorkManager/`ModelStore` path; `assert_embedding_contract` regenerated.
- On-device acceptance on **SM-A325F**: `AndroidSpeechInputSource` on-device + fallback recognition (OQ#4),
  mic permission grant/deny/permanently-denied transitions, the embedder `< 150ms` MID_RANGE measurement +
  `onTrimMemory` teardown, the periodic pre-compute worker firing under battery/idle constraints, boot
  warmup re-enqueue after reboot; real `CalendarSuggestionProvider`/`LocationSuggestionProvider` reads +
  the calendar/location grant/deny/permanently-denied request-flow UX (Block U); plus the inherited
  Block-J `SecretStoreInstrumentedTest` + Block-N N5 + Block-P P5 device items still open from Phases 5/6.

---

## Block S — Suggestion + voice domain contracts (pure) — ✅ DONE 2026-06-29

**Status:** complete (S1–S6). New `:domain` packages `…domain.suggestions` (`Suggestion`/`SuggestionSource`,
`SuggestionContext`/`TimeOfDay`, `SuggestionProvider`, `SuggestionEngine`, `SuggestionRanker` +
`HeuristicSuggestionRanker`) and `…domain.voice` (`SpeechInputSource`, `SpeechRecognitionState`,
`SpeechRecognitionError`); fakes `FakeSpeechInputSource`/`FakeSuggestionProvider`/`FakeSuggestionEngine` in
`:core:testing`. 12 new JVM tests (7 ranker table + 2 composition + 3 speech-fake), 131 domain total, 0
failures; purity guard machine-checked clean; `assembleDebug` + full `testDebugUnitTest` green; 0 new deps;
no production file edited. ADR: decisions.md "ADR 2026-06-29 — Block S complete".

**Goal:** vendor-/runtime-neutral suggestion + voice contracts + the heuristic ranking policy, in
`:domain`. JVM-only, no impls.
**Depends on:** nothing (reuses `OperationResult`, `DeviceProfile`, existing history/cache ports). Forks
1/2/4/5/8/10 inform the shapes.

**Pinned here (decision, no code this block):** the suggestion-list **single owner is the host
`LauncherViewModel`/`LauncherUiState.suggestions`** (F7-8) — the `SuggestionEngine.suggestions()` port is
designed to be collected by **one** host VM, **not** a `:feature:suggestions` `SuggestionsViewModel`. This
port shape is fixed in S so the surface (W1) cannot re-introduce a parallel `StateFlow`; the wiring lands
in W1 but **must not drift** from this owner.

**Reconciliation ownership moves wholly to `LauncherViewModel` (was implicitly the dropped
`SuggestionsViewModel`'s) — record it here so it is not lost.** The cold-start **cache-restore-then-
supersede** rule (the deferred Fork-6 half) is now a `LauncherViewModel` responsibility: first-paint reads
the existing **`SuggestionsCacheRepository`** (display cache), then a fresh `SuggestionEngine.suggestions()`/
`refresh()` load **supersedes it — never merged** (`architecture.md:254-256`: SavedStateHandle owns
transient input/route, the DataStore cache owns content first-paint, a fresh load supersedes the cached
repaint). **The S contract must give `LauncherViewModel` everything it needs for this with no second VM:**
the `SuggestionEngine` port (Flow + `refresh`) + the pre-existing `SuggestionsCacheRepository` read path are
sufficient — no new port is required, but S verifies the shapes compose (a fresh `List<Suggestion>` fully
replaces, not appends to, the cached list). Behaviour is wired + tested in **W2** (cache-restore-then-
supersede ordering); the rule and its single owner are **pinned now**.

**New files (`:domain`):**
- `…domain.suggestions`: `Suggestion`, `SuggestionSource`, `SuggestionContext` (time bucket + optional
  signal value types), `SuggestionProvider`, `SuggestionRanker` (+ a pure `HeuristicSuggestionRanker`
  default), `SuggestionEngine` port.
- `…domain.voice`: `SpeechInputSource`, `SpeechRecognitionState`, `SpeechRecognitionError`.

**New fakes (`:core:testing`, JVM-only):** `FakeSpeechInputSource` (scripted state sequence),
`FakeSuggestionProvider`, `FakeSuggestionEngine`. (`FakeTextEmbedder` already exists.)

**Steps:**
- [x] `S1` Suggestion model + `SuggestionSource` + `SuggestionContext` (pure value types; sensitive signals
      carried as already-derived display-safe values, never raw).
- [x] `S2` `SuggestionProvider` / `SuggestionEngine` ports (`OperationResult` / `Flow`, never throw).
- [x] `S3` Pure `HeuristicSuggestionRanker` (recency + frequency + time-prior + per-source weight, dedup
      by `actionId`, bound to N) implementing `SuggestionRanker`.
- [x] `S4` `SpeechInputSource` port + `SpeechRecognitionState`/`SpeechRecognitionError` (terminal-as-value).
- [x] `S5` Fakes in `:core:testing` (JVM-only, no Android).
- [x] `S6` JVM tests: ranker table (recency/frequency/time/dedup/bound), engine aggregation with opt-in
      provider absent, `FakeSpeechInputSource` emits Partial→Final→Ended and Error-terminal.

**Acceptance:** `:domain` stays stdlib+coroutines (purity guard green); no Android/ONNX/WorkManager term in
`:domain` (grep guard); fakes compile JVM-only; ranker + engine + speech-fake tests green; **intent +
generative + `HandleUserCommandUseCase` + Phase-3/5/6 tests untouched**; no new deps.

**Demoable milestone:** a unit test drives `HeuristicSuggestionRanker` over scripted usage + a time bucket
and asserts the ordered, deduped, bounded list; a `FakeSpeechInputSource` yields `Partial("op")` →
`Final("open camera")` → `Ended`.

---

## Block T — Voice input (`SpeechInputSource`) + `RECORD_AUDIO` request flow — ✅ DONE 2026-06-30

**Status:** complete (T1–T6). `AndroidSpeechInputSource : SpeechInputSource` in `:core:android` (on-device
recognizer preferred behind a `Build.VERSION.SDK_INT >= S` guard + `EXTRA_PREFER_OFFLINE` fallback;
`RecognitionListener` → `callbackFlow`; all recognizer calls marshalled to `Handler(Looper.getMainLooper())`;
`destroy()` on `awaitClose` with an `AtomicBoolean` cancel-before-create guard so no recognizer/mic leaks;
full error-code → `SpeechRecognitionError` map; **no transcript/audio logged**). DI: `VoiceModule` in `:app`
(`@ApplicationContext`); `FakeSpeechInputSource` (Block S) drives the JVM tests. `PermissionFeature.VOICE_INPUT.requestable`
flipped to **true**; the education VM is routed via a `SavedStateHandle` `feature` nav arg (`permission_education?feature={feature}`,
defaults WALLPAPER) replacing the hardcode; the screen's request permission + post-grant action are feature-driven
and it gains an **`ON_RESUME` `refreshStatus()`**. **Block-H `refreshStatus()` debt discharged for the dangerous case:**
a genuine `GRANTED → DENIED` revocation IS reflected (branch c), while the `PERMANENTLY_DENIED → DENIED` suppression
is reachable only from an established `PERMANENTLY_DENIED` (never from `GRANTED`) — the two never collide; KDoc
rewritten + JVM-tested for VOICE_INPUT (revocation, preservation, upgrade). Launcher mic affordance (text-glyph
`IconButton`, shown only when `isVoiceInputAvailable`): granted → `startVoiceInput()` (partials → `commandInput`,
Final → the **unchanged** `onCommandSubmitted` path); not-granted → route to `permission_education?feature=VOICE_INPUT`
(the Block-G Fork-5 flow does the request — framework `checkSelfPermission`, no `:feature:launcher` build change).
`HandleUserCommandUseCase` untouched. **9 new JVM tests (4 launcher-voice + 5 permission-VM); 383 JVM total, 0
failures; `assembleDebug` (Hilt graph valid) + full `testDebugUnitTest` green; 0 new Gradle deps; `android.speech`
confined to `:core:android` (grep-proven).** Real recognizer = **device-pending (OQ#4)**. ADR: decisions.md
"ADR 2026-06-29 — Block T complete".

**Goal:** an on-device-preferred `AndroidSpeechInputSource`, a no-op fake, the `RECORD_AUDIO` live request
flow + `refreshStatus()` dangerous-permission hardening, and a mic affordance on the command input.
**Depends on:** S (`SpeechInputSource` port), the existing permission framework. Forks 1/2/3/10 fixed.
✅ `android.speech.SpeechRecognizer` Java signatures re-verified against the Android Javadoc + dotnet-android
API mirror at Step 0 (main-thread-only; `createOnDeviceSpeechRecognizer`/`isOnDeviceRecognitionAvailable` API 31+;
`RESULTS_RECOGNITION` key; full `ERROR_*` set + API levels) — context7 (Leanback-only) was not trusted.

**New files (`:core:android`):** `AndroidSpeechInputSource : SpeechInputSource` (on-device recognizer when
available + `EXTRA_PREFER_OFFLINE`, `RecognitionListener` → `callbackFlow`, main-thread marshalling,
`destroy()` on `awaitClose`, error-code → `SpeechRecognitionError` map). DI provider in `:app`. Plus the
education-VM feature-routing + the mic affordance in `:feature:launcher`.

**Steps:**
- [x] `T1` `AndroidSpeechInputSource`: availability probe (`isRecognitionAvailable` /
      `isOnDeviceRecognitionAvailable`), `listen()` as `callbackFlow` over `RecognitionListener`,
      on-device-preferred construction, lifecycle (`destroy` on cancel), error mapping; no transcript logged.
- [x] `T2` `FakeSpeechInputSource` already in S — wire DI: real impl in `:app` (`VoiceModule`, application
      `Context`), fake drives tests; voice unavailable → `isAvailable() == false` (degrade to keyboard, mic hidden).
- [x] `T3` Permission: flipped `PermissionFeature.VOICE_INPUT.requestable = true`; routed the feature into
      `PermissionEducationViewModel` via `SavedStateHandle` (`permission_education?feature={feature}`, replacing
      the hardcoded `WALLPAPER`); the screen's `RequestPermission` + post-grant are feature-driven; result → `onPermissionResult`.
- [x] `T4` **Block-H debt discharged:** `refreshStatus()` now fires on `ON_RESUME` and reflects a genuine
      `GRANTED → DENIED` revocation; `PERMANENTLY_DENIED` shows the system-Settings deep-link (no dead re-request)
      and is never clobbered by a bare `DENIED` read; KDoc rewritten; JVM-tested for VOICE_INPUT.
- [x] `T5` Mic affordance on the launcher command input: tap → (permission-gated via `checkSelfPermission`;
      not-granted routes to education) `startVoiceInput()` → partials into `commandInput` → final submits via the
      **unchanged** command path. (Assistant prompt reuse left optional/frozen-forward.)
- [x] `T6` JVM tests: permission VM (route + grant/deny/permanently-denied/revocation/upgrade), voice-unavailable
      degrade, partial→final→submit wiring against `FakeSpeechInputSource`. Real recognizer = device-pending (OQ#4).

**Acceptance:** `SpeechRecognizer` confined to `:core:android` (grep: no `android.speech` import elsewhere);
mic denied → keyboard-only, core unaffected; permanently-denied handled (no dead re-request);
`HandleUserCommandUseCase` untouched (voice produces the same text the keyboard does); no transcript/audio
persisted or logged; `assembleDebug` + JVM regression green (device recognizer run pending).

**Demoable milestone (device-pending):** on the SM-A325F, tapping the mic and saying "open telegram" fills
the command input and launches Telegram via the existing rule path; denying the mic leaves the keyboard
working.

---

## Block U — Contextual suggestion engine + context sources + cache-restore — ✅ DONE 2026-06-30

**Status:** complete (U1–U6). Offline-first providers `TimeOfDaySuggestionProvider` (fixed per-`TimeOfDay`
prior, zero-permission, always contributes) + `UsageSuggestionProvider` (exponential-decay recency over a
7-day horizon + launch-count-ratio frequency, both over `UsageHistoryRepository`); opt-in providers
`CalendarSuggestionProvider` (`READ_CALENDAR`-gated, queries only `CalendarContract.Instances.EVENT_ID` in
a 2-hour lookahead, never reads/caches a raw title) and `LocationSuggestionProvider`
(`ACCESS_FINE_LOCATION`-gated, checks only whether `LocationManager.getLastKnownLocation` across
GPS/NETWORK/PASSIVE returns non-null, never reads/caches a coordinate) — both emit a single fixed, generic
`Suggestion` label, never derived from the raw signal; all four in `:data:repository`. `SuggestionEngineImpl`
(`:data:repository`): structured-concurrency aggregate (`coroutineScope` + `async` per provider,
fault-isolated — a throwing provider degrades to empty, never fails the aggregate) → `HeuristicSuggestionRanker`
→ persists to `SuggestionRankingRepository` (history) + `SuggestionsCacheRepository` (cold-start repaint,
`CachedSuggestion(label, actionId)` only) → exposes a hot `StateFlow`; gated end-to-end by
`FeatureFlagRepository.aiSuggestionsEnabled` (no work at all when disabled). `persist()`'s two writes are
fire-and-forget (no caller to return `Failure` to) — the module's first such path, logged via direct
`Log.w` (payload-free) rather than the module's normal return-`Failure` convention; `core/common`'s dormant
`ResultLogger` was deliberately **not** wired (an early draft did; reverted — wiring that seam is a separate
decision, not a U3 side-effect). DI: `SuggestionsProvidesModule` (`:app`, `@Provides`-composed, mirrors the
Block-M `GenerationProvidesModule` precedent since `SuggestionEngineImpl` takes a composed
`List<SuggestionProvider>`). U4 reused the Block-T template **literally**: `CALENDAR_SUGGESTIONS`/
`LOCATION_SUGGESTIONS.requestable` flipped to `true`, real rationale copy, manifest gained exactly
`READ_CALENDAR` + `ACCESS_FINE_LOCATION` (not `RECEIVE_BOOT_COMPLETED` — that's Block W's); zero edits needed
to `PermissionEducationScreen.androidPermission()` (already an exhaustive `when`, no `else`, pre-populated
ahead of this block) or to `PermissionEducationViewModel`/`LauncherViewModel`'s constructors (neither
changed — no test-factory itemization needed). **Per-feature dismissed flag is in-memory only for all three
non-`WALLPAPER` requestable features** (`VOICE_INPUT` included, since Block T) — `PermissionPrefsRepositoryImpl.keyFor()`
returns `null` for all three, not because U4 dropped it, but because `"voice"`/`"calendar"`/`"location"` are
literally forbidden terms in `PrivacyInventoryGuardTest`'s own denylist; a persisted key would fail that
guard outright. This is a Block-G-era structural fact (documented there since 2026-06-23), carried forward
unchanged through T and U — not a per-block deviation, so it needed no flag. **U5 privacy guard delivered as
4 executable proofs, not assertions:** `SuggestionEngineImplTest` (persistence carries exactly
`SuggestionRankingRecord`'s 4 fields / `CachedSuggestion`'s 2 fields, nothing wider) +
`SuggestionProviderPrivacyGuardTest` (plants a sensitive real event title inside a fake `CalendarContract`-authority
`ContentProvider`, and a real GPS fix via Robolectric's `LocationManager` shadow, runs the **actual production
providers** against them, asserts the planted value appears nowhere in the output — proves leak-freedom rather
than asserting it from reading the source) + `SuggestionOutboundIsolationTest` (`:domain`, pins
`OutboundContextPolicy.ALLOWED`/`OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` to their pre-Block-U literal
values) + a reflection-based fix to `AiRequestGuardTest` (Block L) closing a real gap found during U5 review:
`OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` were hand-maintained sets with nothing reflecting them against
`AiRequest`/`AiError`'s actual declared fields — a field silently added without a matching inventory update
would have shipped clean; two new tests there now reflect over the real classes and fail if the two ever
drift. **U6** added the remaining JVM coverage: real-output tests for both offline providers (not just
forwarding through fakes — `TimeOfDaySuggestionProviderTest` covers all 4 `TimeOfDay` buckets,
`UsageSuggestionProviderTest` covers the recency and frequency code paths separately plus the
no-history-no-throw case), the degrade test (every optional permission denied, run against the **real**
`CalendarSuggestionProvider`/`LocationSuggestionProvider`, time+usage still non-empty), provider opt-in
absence (covered by the privacy-guard test's "not granted, even with sensitive data present" cases),
engine mix + dedup-by-`actionId`-keep-highest-weighted (proven across two providers with the same `actionId`
and different scores/sources), and bound-to-ranker's-max with the bounded set verified to reach both
persistence targets unchanged. **16 new JVM tests; 399 JVM total, 0 failures**; `assembleDebug` (Hilt graph
valid) + full `testDebugUnitTest` + `:domain:test` green; **0 new Gradle deps**; `android.location`/
`android.provider.CalendarContract` confined to `:data:repository` (grep-proven, zero hits in `:domain`/
`:feature:*`/`:core:common`/`:core:ui`/`:core:testing`); `HandleUserCommandUseCase` untouched; suggestion tap
routing is Block W's job. ADR: decisions.md "ADR 2026-06-30 — Block U complete".

**Goal:** the offline-first suggestion pipeline made real — context providers, the `SuggestionEngine`
aggregation + heuristic rank, ranking/cache persistence, calendar/location request flows.
**Depends on:** S (ports + ranker), T (the request-flow template). Forks 4/5/8/9 fixed.

**New files:**
- `:data:repository` (or `:core:android` for platform reads): `TimeOfDaySuggestionProvider`,
  `UsageSuggestionProvider` (over `UsageHistoryRepository`), `CalendarSuggestionProvider`
  (`READ_CALENDAR`-gated, `CalendarContract`), `LocationSuggestionProvider` (`ACCESS_FINE_LOCATION`-gated,
  coarse bucket); `SuggestionEngineImpl` (aggregate enabled providers → `HeuristicSuggestionRanker` →
  bound; writes `SuggestionRankingRepository` + `SuggestionsCacheRepository`).
- `:app`: DI for providers + engine; flip `CALENDAR_SUGGESTIONS`/`LOCATION_SUGGESTIONS.requestable = true`;
  add `READ_CALENDAR` + `ACCESS_FINE_LOCATION` to the manifest.

**Steps:**
- [x] `U1` Offline providers (time, usage) — zero-permission, always contribute.
- [x] `U2` Opt-in providers (calendar, location) — return **empty** unless `PermissionChecker.status ==
      GRANTED`; emit only derived display-safe `Suggestion`s (no raw event title / coordinates).
- [x] `U3` `SuggestionEngineImpl`: aggregate → rank → dedup/bound; persist ranked results to
      `SuggestionRankingRepository` (learning) + `SuggestionsCacheRepository` (cold-start repaint);
      gated by `aiSuggestionsEnabled`.
- [x] `U4` Calendar/location request flows reusing the Block-T template (education ≠ request; per-feature
      dismissed flag — in-memory only, mirrors `VOICE_INPUT` since Block G's denylist-collision design;
      permanently-denied path).
- [x] `U5` **Privacy guard** test: suggestion path persists/sends nothing beyond `CachedSuggestion`'s
      allowed fields; no calendar/location term in the outbound `AiRequest` inventory (allow-list
      unchanged); new keys/tables (if any) registered. Delivered as 4 executable proofs (see Status above),
      including a reflection-based fix to Block L's `AiRequestGuardTest` closing a hand-maintained-inventory
      drift gap found during review.
- [x] `U6` JVM tests: provider opt-in absence (denied → empty), engine mix + dedup + bound, cache write,
      ranking persistence, degrade-with-zero-sensitive-data. **16 new JVM tests; 399 total, 0 failures.**

**Acceptance:** suggestions are produced with every optional permission denied (time + usage only); a
granted calendar/location adds exactly that signal; nothing sensitive is persisted raw or sent to cloud
(guard green); each denial disables exactly its provider; `assembleDebug` + JVM regression green.

**Demoable milestone:** with calendar/location denied, the engine returns time + usage suggestions; grant
calendar and a "next meeting" suggestion appears; the result is cached for cold-start repaint.
**Device-pending:** real on-device `CalendarSuggestionProvider`/`LocationSuggestionProvider` reads and the
live calendar/location request-flow UX (SM-A325F) — carried into Phase 7 Tracking alongside the Block-T
recognizer (OQ#4) and the Phase 5/6 device debt.

---

## Block V — ONNX `TextEmbedder` impl + semantic re-rank (gated; inert seam if OQ#3 open)

**Goal:** fill the deferred `TextEmbedder` port with a lazy, gated ONNX impl **behind a real reader** —
the semantic re-rank of U's suggestion candidates — reusing Phase-6 session/model-store/gate machinery.
**Depends on:** S (ranker seam), U (the reader), Phase-6 P/Q infra. Forks 5/6/10 fixed. Gated by **OQ#3**.

**Co-residency decision (closed 2026-06-29 — Option B).** The embedder session is **not** made
co-resident-safe with the NLU session via a cross-session arbiter. `OnnxTextEmbedder` reuses the existing
**per-instance** `SessionLifecycle` (its own `releaseResources()`, torn down by `onTrimMemory` exactly
like the NLU). The two avoid the `< 150MB` MID_RANGE ceiling **by scheduling, not by locking**: the
embedder's only producer is U's re-rank seam, consumed primarily by the **deviceIdle** pre-compute worker
(Block W) when the foreground command path is idle and the NLU session has already torn down via its
sustained-gate-off debounce. A rare brief overlap is **accepted**; the combined footprint is a
device-pending OQ#3 measurement (V5), and a **single-resident arbiter is frozen-forward** — built only if
the device measurement shows a breach. **No new lifecycle infra in this block** — the only `:app` wiring
change is that the single injected `SessionLifecycle` becomes a Hilt **multibinding** (`Set<SessionLifecycle>`,
or an `@IntoSet` provider per holder) so `onTrimMemory`/`onLowMemory` release **both** the NLU and
embedder sessions; the seam, threshold (`≥TRIM_MEMORY_BACKGROUND`), and teardown logic are unchanged.

**Status 2026-07-01:** Block V is code-implemented as an inert runtime seam. `OnnxTextEmbedder :
TextEmbedder` exists in `:data:ai-local`, `SemanticSuggestionRanker : SuggestionRanker` decorates the
heuristic ranker, `ModelDownloadConfig.EMBEDDING_PENDING` is blank/unpinned, and `:app` now releases a
`Set<SessionLifecycle>` so the NLU classifier and embedder both tear down on trim/low-memory. OQ#3 remains
unresolved: no production embedding model, host/hash, vocab asset, or final ONNX contract has been guessed
or pinned. Shipping no-model/gate-off/failure behavior is heuristic-order parity.

**New files (`:data:ai-local` / `:data:repository`):** `OnnxTextEmbedder : TextEmbedder` (lazy single
`Mutex`-guarded session on `Dispatchers.Default`, per-call `LocalInferenceGate` re-check, `AutoCloseable`
+ existing `SessionLifecycle`, graceful degrade, no text logged); `SemanticSuggestionRanker :
SuggestionRanker` decorator (embedding similarity re-rank over the heuristic order). Second `ModelId` +
`ModelDownloadConfig.EMBEDDING_PENDING` is the inert OQ#3 seam. The existing store can resolve the model
file by id, but production provisioning remains unpinned until OQ#3 closes.

**Steps:**
- [x] `V1` `OnnxTextEmbedder`: provisional input contract (reuse `WordPieceTokenizer` if compatible; pooled
      `[1, hiddenDim]` output read by index); files resolved via `LocalModelFiles` **before** any ORT call;
      any failure → `OperationResult.Failure` (never thrown).
- [x] `V2` `SemanticSuggestionRanker` decorator: re-rank candidates by similarity to the context/typed
      prefix **iff** the gate is on + model available; else delegate to the heuristic order verbatim.
- [x] `V3a` Embedding model config seam: second `ModelId`/`ModelDownloadConfig.EMBEDDING_PENDING`;
      blank URL/hash, `isPinned=false`, half-pinned guard unchanged. Full live provisioning + contract
      cross-check stays OQ#3-pending.
- [x] `V4` JVM tests (fakes): re-rank consults the embedder only when gated-on; embedder failure →
      heuristic order; no-model → heuristic order (shipping-state parity); empty/invalid vectors degrade.
- [ ] `V5` `androidTest` (device-pending): real embedder loads + embeds, `< 150ms` MID_RANGE measurement,
      `onTrimMemory` teardown, memory/co-residency measurement with the NLU session.

**Acceptance:** ONNX still confined to `:data:ai-local`; the embedder is lazy + single + closeable and is
released through the same `SessionLifecycle` seam as the NLU via Hilt multibinding; **no-model devices rank
identically to today's heuristic** (parity); embedder failure degrades silently; `assembleDebug` + JVM
regression green (device run pending).

**Demoable milestone (device-pending):** on a gated device with the embedding model present, two
semantically-close candidates re-order ahead of a lexically-closer-but-unrelated one; pull the model and
the heuristic order returns unchanged.

---

## Block W — Suggestions UI + WorkManager pre-compute/cleanup + boot warmup + docs-sync / phase close

**Goal:** surface the pipeline on the launcher home, schedule background pre-compute + cleanup, wire boot
warmup, reconcile docs, and close Phase 7.
**Depends on:** S, T, U. Forks 7/8/9 fixed. **Block V is separate and non-blocking for the user-facing
surface close.**

**New files:** `:feature:suggestions` — a **stateless `SuggestionsRow(suggestions, onSuggestionTap)`
composable only** (no `SuggestionsViewModel`, no parallel `StateFlow`, no Hilt — F7-8 single-owner
decision); the suggestion-list state lives on the host `LauncherViewModel` as a new
`LauncherUiState.suggestions` field fed by an injected `SuggestionEngine` (single source per
`architecture.md:229`); `:app` — `SuggestionPrecomputeWorker` + `UsageCleanupWorker` (`@HiltWorker`),
their periodic schedulers, a `RECEIVE_BOOT_COMPLETED` `BroadcastReceiver`; manifest
`RECEIVE_BOOT_COMPLETED`.

**Steps:**
- [x] `W1` `:feature:suggestions` **stateless** `SuggestionsRow` (no VM/StateFlow — single owner is the
      host VM); grow `LauncherUiState` with `suggestions` fed by the injected `SuggestionEngine`
      (`architecture.md:229` single source); tap → existing `ExecutableAction`/`HandleUserCommandUseCase`
      path (no `feature→feature` edge).
- [x] `W2` **Cold-start cache-restore** (the deferred Fork-6 half): first-paint from
      `SuggestionsCacheRepository`, a fresh `SuggestionEngine` load supersedes it (never merged — the
      `architecture.md` reconciliation rule).
- [x] `W3` `SuggestionPrecomputeWorker` (periodic ~24h, `batteryNotLow`+`storageNotLow`+`deviceIdle`, no
      network, **no `LOW_END`**, gated on `aiSuggestionsEnabled`, idempotent via
      `enqueueUniquePeriodicWork`); `UsageCleanupWorker` (time-based usage-history sweep, idempotent,
      cancellable). Scheduler cancels precompute fail-closed when the flag is off or the device is
      `LOW_END`; runtime execution also stops under battery saver.
- [x] `W4` Boot warmup: `RECEIVE_BOOT_COMPLETED` receiver re-enqueues the periodic work; permission
      education entry; best-effort (never required).
- [x] `W5` JVM tests (fakes): worker gate-before-enqueue (LOW_END / flag-off never schedules), idempotent
      re-run, cache-restore-then-supersede ordering, suggestion-tap routing.
- [x] `W6` **Docs-sync:** `architecture.md` AI-pipeline + "`LauncherUiState` grows" + the suggestions
      surface + voice modality + WorkManager pre-compute/cleanup rows; `CLAUDE.md` Contract→Owner rows for
      the new ports/impls; `roadmap.md` Phase 7 marked.
- [x] `W7` ADR in `decisions.md` ("ADR — Block W complete + Phase 7 close"); `CLAUDE.md` status advanced;
      Phase 7 marked closed; device-pending acceptance recorded (OQ#3/#4 + the inherited J/N/P items).

**Acceptance:** the suggestion row renders gated by `aiSuggestionsEnabled`; cold start first-paints from
cache then supersedes; pre-compute never runs on `LOW_END` / flag-off / battery-saver; workers are
idempotent + cancellable + no foreground service; tapping a suggestion launches via the existing path;
the offline launcher core + the `< 10ms` rule path are unchanged; docs match reality; `assembleDebug` +
full JVM regression green (device runs pending).

**Demoable milestone:** "open telegram" still resolves on the rule path; the home shows a time/usage
suggestion row that repaints instantly on cold start (cache) then refreshes; toggling `aiSuggestionsEnabled`
off returns today's plain grid — the Phase-7 analog of Phase-3 "open telegram".

---

## Frozen / pushed forward (Phase 8+)

- **Accessibility Service** (`BIND_ACCESSIBILITY_SERVICE`) + advanced automation → **Ph8** (its own
  user-initiated consent flow; it must NOT appear in `PermissionFeature` before then).
- **Wake-word / always-listening voice** → frozen (privacy + battery; this phase is tap-to-talk only).
- **Cloud STT via our own backend** → never (BYOK, no backend; on-device preferred, the user's own system
  recognizer is the only fallback).
- **Local generative LLM + the generative router's reserved ONNX slot** → **Ph8+** (still NOT this phase;
  Phase 7's ONNX is the embedder only, a non-generative encoder).
- **Joint intent+slot NLU model** (replacing Phase-6 heuristic slots) → frozen-forward.
- **Full R8/ProGuard rules for ONNX (NLU + embedder) + release memory profiling on `LOW_END`** → **Ph9**.
- **Full Hilt→KSP migration** → **Ph9** (the Room-KSP / Hilt-kapt hybrid stays).
- **ONNX version bump 1.20.0 → current** → deferred, its own change + device validation.
- **WorkManager network-bearing jobs** (none added here; pre-compute is local-only) → as-needed later.

## Demoable milestones (like `open telegram` for Phase 3)

- **S:** the ranker table + engine aggregation pass in unit tests; a fake speech source emits
  Partial→Final→Ended.
- **T (device-pending):** mic → "open telegram" fills + submits the command; denied mic leaves the keyboard.
- **U:** suggestions appear with every optional permission denied (time + usage); granting calendar adds a
  meeting suggestion; nothing sensitive persisted/sent.
- **V (device-pending):** with the embedding model present, semantically-close candidates re-rank; without
  it, heuristic order is unchanged.
- **W:** the home suggestion row repaints from cache on cold start then refreshes; flag-off → today's grid;
  pre-compute never runs on `LOW_END`.

## Tracking

- One execution prompt per block; **Block S first** (gated, like "Block O only" / "Block E only").
- Record each block as an ADR in `decisions.md`; advance `CLAUDE.md` per block.
- **Per-block gating:** S green before T; **T's device run is independent** (port-gated); U before V (V
  needs U's reader); V before W; W closes the phase.
- Device-pending acceptance carried forward: Block-T recognizer (OQ#4) + Block-V embedder budget (OQ#3) +
  the inherited Block-J `SecretStoreInstrumentedTest` + Block-N N5 + Block-P P5 (all SM-A325F).

## Agent model per block (which Claude to run Claude Code on)

Rule of thumb: **Opus 4.8** where the *abstraction is being decided* or correctness is subtle (contracts,
recognizer lifecycle, gating, privacy, the embedder session/memory, the cache-vs-fresh reconciliation).
**Sonnet 4.6** where the block is *execution against a pinned spec* (providers, DI, workers, UI, tests,
docs). Independent of which model executes, do **planning + diff review on Opus**.

| Block | Model | Why |
|---|---|---|
| **S** — Suggestion + voice contracts + ranker | **Opus 4.8** *(Sonnet acceptable)* | Foundation everything compiles against; the three suggestion ports + the `SpeechInputSource` shape + the heuristic ranking policy are decided here; a wrong port is the most expensive mistake. Shapes are pinned, so Sonnet *can* execute round 1 — prefer Opus. |
| **T** — Voice input + `RECORD_AUDIO` flow | **Opus 4.8** for the recognizer lifecycle (main-thread, callback→Flow, destroy, error map) + the dangerous-permission hardening; **Sonnet 4.6** for the DI/mic wiring | The framework recognizer is native-ish, main-thread-bound, and first-time-verified; the `refreshStatus()` debt is correctness-sensitive (Opus). The mic affordance + DI are pattern-following (Sonnet). |
| **U** — Context engine + sources + cache-restore | **Opus 4.8** for the engine/ranking/privacy seam; **Sonnet 4.6** for the providers + request-flow reuse | "No sensitive data persisted/sent" + the degrade-with-zero-permission contract are privacy-shaped (Opus); the calendar/location providers + the Block-T-template request flows are pattern-following (Sonnet). |
| **V** — ONNX embedder + semantic re-rank | **Opus 4.8** | Session lifecycle/memory, per-call gating, the heuristic-fallback parity, and the inert-seam discipline are subtle and costly to get wrong. |
| **W** — Suggestions UI + workers + boot + close | **Sonnet 4.6**, **review on Opus** | UI + periodic workers + DI + docs are mechanical; the cache-vs-fresh reconciliation + the no-`LOW_END`-background gate are the regression-sensitive spots — review them on Opus. |

## Decisions confirmed (decided 2026-06-29 — agreed; code not started)

1. **F7-1 — Voice scope:** STT → text → existing pipeline; on-device preferred; never a new generative
   path; raw audio never persisted, transcripts never logged. Wake-word frozen.
2. **F7-2 — Voice surface:** one `SpeechInputSource`, a mic affordance on the launcher command input
   (primary), optional assistant-prompt reuse.
3. **F7-3 — `RECORD_AUDIO`:** `VOICE_INPUT.requestable = true`; feature routed into the education VM; full
   dangerous-permission lifecycle; the `refreshStatus()` Block-H debt discharged.
4. **F7-4 — Context sources:** offline-first (time + usage) always on; calendar/location opt-in +
   degrade-to-nothing; only derived display-safe `Suggestion`s leave a provider.
5. **F7-5 — Ranking:** pure heuristic `SuggestionRanker` ships; the ONNX semantic re-rank is an optional
   gated decorator with heuristic fallback.
6. **F7-6 — `TextEmbedder` impl:** built behind a real reader (U's re-rank), gated by the **same**
   `LocalInferenceGate`/`DeviceProfile` as the NLU, reusing the Phase-6 model-store/WorkManager; inert
   `EMBEDDING_PENDING` seam if OQ#3 open.
7. **F7-7 — WorkManager:** periodic pre-compute + usage cleanup, battery/idle-aware, no `LOW_END`,
   idempotent + cancellable, no foreground service; boot warmup via `RECEIVE_BOOT_COMPLETED`; reuses the
   existing `Configuration.Provider`/`HiltWorkerFactory`.
8. **F7-8 — Surface:** **single owner = host `LauncherViewModel`/`LauncherUiState.suggestions`** (no
   parallel `SuggestionsViewModel`/`StateFlow`; `architecture.md:229`); `:feature:suggestions` is a
   stateless `SuggestionsRow` composable (no Hilt); cold-start cache-restore-then-supersede (the deferred
   Fork-6 half). Ownership pinned in Block S, wired in W1.
9. **F7-9 — Privacy:** outbound allow-list unchanged; the guard invariant is **"no new outbound category
   (calendar/location/raw audio) enters the allow-list"**, NOT "voice never reaches cloud" — voice rides
   the existing `USER_COMMAND` channel like keyboard text; sensitive context never persisted raw / logged;
   inventory guards extended for any new keys/tables.
10. **F7-10 — Testing:** interface-gate everything; fakes in `:core:testing` (JVM-only); real recognizer +
    real embedder only on SM-A325F; `< 150ms` embedder budget + recognizer run are device-pending.
11. **F7-11 — Deps:** **no new Gradle deps**; new manifest perms (`READ_CALENDAR`, `ACCESS_FINE_LOCATION`,
    `RECEIVE_BOOT_COMPLETED`); `:feature:suggestions` gains Hilt; full ONNX R8/ProGuard frozen to Ph9; ESP
    forbidden.
12. **First execution round:** **Block S only** (matches "Block O only" / "Block E only" gating).
