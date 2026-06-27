# Phase 6 — Local NLU and embeddings (ONNX Runtime Mobile)

> Format mirrors `phase-5-plan.md` / `phase-4-plan.md`: pre-flight repo-truth check → core stance →
> in-scope → hard invariants → block order + rationale → forks-before-code → per-block sections
> (goal / depends-on / new files / checkbox steps / acceptance / demoable milestone) → frozen-forward →
> demoable milestones → tracking → per-block agent-model table → confirmed-decisions.
> Block lettering continues the alphabet (Phase 4 = E→H, Phase 5 = I→N), so **Phase 6 = Blocks O → R**.
> **Status: forks decided 2026-06-27; plan agreed; no block started.** Each block gets its own
> execution prompt. **First execution round = Block O only** (gated, like "Block E only" / "Block I only").

## Pre-flight — repo-truth check (verified 2026-06-27, before any block)

This plan was written against the docs **and** the actual repo (codegraph + grep + file reads). The
checks below were run; **deltas are flagged inline and the plan is patched to reality**. Re-confirm the
ⓘ items at the start of the block that consumes them — they only change "create vs extend" and the
new-deps accounting, never the block structure.

- [x] **`:data:ai-local` exists as an empty scaffold and *already wires ONNX*.** Its
      `build.gradle.kts` is an `android.library` (`namespace com.sidr.launcher.data.ailocal`,
      `minSdk 28`, JVM 17) depending on `:core:common` + `:domain` + `coroutines.core` +
      **`libs.onnxruntime.android`**, and it has **zero `.kt` sources**. It is `include`d in
      `settings.gradle.kts` (`:data:ai-local`). **Delta vs Phase-5 assumption:** ONNX is *not* merely
      "planned in architecture.md" — the dependency is already declared. ⓘ **No new ONNX dep is added;
      Block P only adds source.** The module currently has **no `:core:android` edge** — Block P/Q adds
      it (architecture.md already allows `data/* -> core/android`).
- [x] **`DeviceProfile` detection does NOT exist.** Grep over `core/` + `domain/` for
      `DeviceProfile`/`DeviceCapability`/`LOW_END`/`MID_RANGE`/`HIGH_END` finds **only** the
      `enum class DeviceProfile { LOW_END, MID_RANGE, HIGH_END }` snippet *in `architecture.md`* and a
      flattened cache DTO `DeviceProfileCacheEntry(isLowEndDevice, cachedAtEpochMs)` in `:domain`.
      `core/android/src/main` contains exactly two files: `AndroidPermissionChecker` and
      `AndroidConnectivityChecker`. **There is no `DeviceProfile` model, no `DeviceCapability` model,
      and no detector.** The `DeviceProfileCacheEntry` KDoc even says "*The detector lives in
      `:core:android`*" — that is **aspirational, not true today**. **Major delta:** Phase 6 gating
      hangs entirely on `DeviceProfile`, so Phase 6 must **build the `DeviceProfile`/`DeviceCapability`
      model + detector from scratch** (model in `:domain`, detector in `:core:android`). This is folded
      into Blocks O (model/policy) + Q (detector). `decisions.md:261-263` already anticipated this:
      "*the domain `DeviceProfile`/capability model isn't formalised yet … when `DeviceProfile` lands,
      only the mapper + … change*".
- [x] **ONNX Runtime in the catalog: yes; WorkManager: no.** `gradle/libs.versions.toml` has
      `onnxRuntime = "1.20.0"` + `onnxruntime-android` (module `com.microsoft.onnxruntime:onnxruntime-android`).
      **No** `androidx.work` / `work-runtime` / `hilt-work` / NNAPI package anywhere in the catalog.
      NNAPI ships **inside** the `onnxruntime-android` AAR (no separate dependency). ⓘ Genuinely new
      deps for Phase 6 = **WorkManager + `androidx.hilt:hilt-work` (+ its compiler)** only (Fork P6-11).
- [x] **`IntentMatcher` is the port a local NLU classifier implements; `MatcherSource.NLU` is the
      reserved second source.** `domain/.../intent/IntentMatcher.kt` — `interface IntentMatcher {
      suspend fun match(normalizedInput: String): IntentMatchResult }`, `enum class MatcherSource {
      RULE_BASED, NLU }`, and `IntentMatchResult.source: MatcherSource = RULE_BASED`. The KDoc is
      explicit: "*ONNX NLU classifiers (Phase 6) are a valid matcher source*" and "*Do NOT fold
      generation into this contract*". `RuleBasedIntentMatcher` (in `:data:repository`) is the only
      impl; `FakeIntentMatcher` is in `:core:testing`.
- [x] **The `DefaultGenerativeRouter` "ONNX slot" is a comment-only reservation between matcher and
      cloud.** Verbatim (`data/repository/.../ai/DefaultGenerativeRouter.kt`): the ordering KDoc says
      "*1. Ph6 ONNX slot (reserved — no impl yet; insert here with no edit to cloud/static branches)*",
      and the body has "*Ph6 ONNX slot — ahead of cloud so a local engine inserts here with no rewrite
      of the cloud/static branches. Today there is no ONNX impl; this comment is the slot.*" **This slot
      is for the *generative* router (`GenerativeAiEngine`), which Phase 6 does NOT fill** — see Fork
      P6-1. Phase 6's ONNX work feeds the **`IntentMatcher`** pipeline, a different port.
- [x] **`IntentConfidencePolicy` admits a second source with no `HandleUserCommandUseCase` rewrite.**
      `interface IntentConfidencePolicy { autoExecuteThreshold; suggestThreshold; shouldAutoExecute;
      shouldSuggest; isLowConfidence }`; `DefaultIntentConfidencePolicy(autoExecute=0.85f,
      suggest=0.50f)`. `HandleUserCommandUseCase.handle()` calls a **single** `matcher.match(normalized)`
      and routes on `match.best.confidence` through the policy. **Therefore the NLU source merges by
      swapping the bound `IntentMatcher` to a composite that itself composes rule + NLU — the use case
      and its tests are untouched** (Fork P6-2). `decisions.md:116` notes the policy is "*overridable per
      DeviceProfile/feature-flag (C7)*", which is exactly the gate hook Phase 6 needs.
- [x] **`:domain` purity guard.** `domain/build.gradle.kts`: `implementation(libs.coroutines.core)` +
      `testImplementation(libs.coroutines.test)` only, with the comment "*domain is pure Kotlin: stdlib
      + coroutines only. No core/*, no Android. (Block A / A2)*". `:core:testing` is **JVM-only** (Block
      I caveat) — fakes must be pure JVM, **no Android variants**, so the ONNX-touching fakes are no-ops.

### External-API verification (context7, 2026-06-27)

- **WorkManager / Hilt-WorkManager** — verified against `/androidx/androidx`: `@HiltWorker` +
  `@AssistedInject(@Assisted Context, @Assisted WorkerParameters)`, `HiltWorkerFactory`,
  `Configuration.Provider` (with the documented manifest `WorkManagerInitializer` handling),
  `CoroutineWorker.doWork()`, and `Constraints.Builder` exposing `setRequiresBatteryNotLow`,
  `setRequiresStorageNotLow`, `setRequiresCharging`, `setRequiresDeviceIdle`, `setRequiredNetworkType`.
  Current lines seen: `work-runtime` ~2.10.0, `hilt-work` ~1.3.0-rc01. These confirm Fork P6-7's
  battery-aware constraints + Hilt-worker wiring are current API, not training-memory guesses.
- **ONNX Runtime — ⚠ thin Android-Java coverage.** `/microsoft/onnxruntime` on context7 returned
  predominantly **Python** surfaces (`onnxruntime.InferenceSession`, `SessionOptions`,
  `providers=[...]` ordered list with CPU fallback, `session.disable_cpu_ep_fallback`). The **Android
  Java/Kotlin binding** (`ai.onnxruntime.OrtEnvironment.getEnvironment()`,
  `OrtSession.SessionOptions().addNnapi(...)`, `OnnxTensor.createTensor(...)`, `session.run(...)`,
  `AutoCloseable` lifecycle) was **not well covered**. The concept is confirmed (EPs are an ordered
  list; CPU is the deterministic fallback), but **the exact Java method signatures must be re-verified
  against the `ai.onnxruntime` Javadoc at Block P execution time** (flagged in Fork P6-5). This is the
  same discipline as the Phase-5 Ktor `HttpTimeout` reconciliation: do not pin the Java API from memory.
  **Web-verified 2026-06-27 (platform reality, drives Fork P6-5):** NNAPI was **deprecated in Android 15**
  (Google steers on-device ML to TFLite-in-Play-Services / GPU delegate), and the **ORT NNAPI EP is only
  available on API 29+ and is silently ignored on API 28** — the launcher targets API 28+, so on Android 9
  the NNAPI EP never activates regardless. Hence CPU is the deterministic default and NNAPI is opportunistic.

Run order: this check is done; execute the Block O prompt next.

---

## Core stance for this phase

Phase 6 makes the launcher **smarter offline**, not heavier. ONNX Runtime Mobile is introduced **only**
for **NLU / intent classification** (and an **embeddings port with no impl yet**) — it is **never** a
generative LLM runtime (`decisions.md:16-18`). The local model is a **second `IntentMatcher` source**
(`MatcherSource.NLU`) behind the existing port, consulted **only when the rule matcher is
low-confidence**, so the `< 10ms` rule fast path and every Phase-3 test stay exactly as they are.

Everything ONNX is **isolated in `:data:ai-local`** and **capability-gated by a single `DeviceProfile`
gate *policy***: `LOW_END` never loads ONNX (rule + cloud only); `MID_RANGE` loads it only when a verified model
is present and thermals/battery allow; `HIGH_END` enables it. The model is **downloaded and
integrity-verified by WorkManager** (battery-aware, never on `LOW_END`, idempotent, cancellable) and
**no model is ever loaded from an unverified source**. The session is **lazy** (never on launcher cold
start), **single + shared**, and **closed under memory pressure**. If anything is missing, unverified,
or fails — including NNAPI init — the pipeline **degrades to rule/cloud and never crashes or blocks the
launcher**.

The generative router's reserved **ONNX slot stays reserved**: local *generation* (MediaPipe / llama.cpp
behind an interface) is deferred past Phase 6 (`decisions.md:18`). Phase 6 touches the **matching**
pipeline, not the generative one.

## In scope (unfreezing what Phases 4/5 deferred)

- **Local-AI domain contracts (pure):** `IntentClassifier` and `TextEmbedder` ports;
  `DeviceProfile` + `DeviceCapability` model (formalised at last); `ModelId` / `ModelAvailability`
  contracts; a pure **capability-gating policy**. (Block O)
- **ONNX runtime integration** in `:data:ai-local`: lazy single session, **deterministic CPU default +
  opportunistic NNAPI (API 29+, off-by-default until proven)**, tensor I/O, lifecycle/memory. Implements
  `IntentClassifier`. (Block P)
- **`DeviceProfile` detection** in `:core:android` (RAM/cores/NNAPI-availability/thermal/battery), the
  **single gate**, **model management** (on-disk store + availability surface), and **WorkManager
  download + SHA-256 verification** with battery-aware constraints. (Block Q)
- **Wire the NLU `IntentMatcher` source** via a composite that preserves the rule fast path and merges
  through `IntentConfidencePolicy`; docs-sync (ONNX-slot wording, AI pipeline as built); Phase close. (Block R)
- **`TextEmbedder` port only** — no ONNX embedder impl this phase (Fork P6-6: no consumer yet).

## Hard invariants (the plan must obey)

- `:domain` stays pure: **stdlib + kotlinx-coroutines only**. **No ONNX, no Android, no WorkManager,
  no `core/*`.** Ports + the gating policy + the `DeviceProfile`/`DeviceCapability` model live in
  `:domain`; impls in `:data:ai-local` + `:core:android`. (`Flow` allowed, as today.)
- **ONNX is isolated in `:data:ai-local`** — no other module imports `ai.onnxruntime`. Allowed edges:
  `:data:ai-local -> :domain, :core:common, :core:android, ONNX` only.
- **`IntentMatcher` ≠ `GenerativeAiEngine` still holds.** **`HandleUserCommandUseCase` is extended
  through the existing source-merge seam (swap the bound `IntentMatcher`), not rewritten** — adding the
  NLU source must not regress the rule path or any Phase-3 test.
- **Launcher core stays fully offline + no AI on cold start.** ONNX is lazy, capability-gated, and never
  blocks startup; `LOW_END` never loads it. `feature/*` has no edge into `:data:*` (only domain ports,
  injected from `:app`).
- **Performance budgets are hard** (`architecture.md:45-58`): rule match `< 10ms` preserved (NLU only on
  low-confidence); ONNX inference `< 150ms` on `MID_RANGE` (measured on the **CPU path**; **device measurement**, not a JVM assert);
  per-profile heap ceilings (LOW_END `< 80MB` → no ONNX, MID_RANGE `< 150MB`, HIGH_END `< 250MB`);
  graceful degrade (→ rule/cloud) when a model is missing/unverified.
- **WorkManager:** **no `LOW_END` background work by default**, battery-saver respected, idempotent +
  cancellable, **no foreground service**.
- **Privacy:** on-device inference only (network used **solely** for model download); NLU/embeddings
  inputs **never persisted or sent to cloud**; **no user text in logs**; Block F SEARCH/UNKNOWN
  redaction still holds. **No model loaded from an unverified source.**
- Expected failures are **values, never thrown to UI** (`IntentMatchResult` / `OperationResult`); ONNX
  failures degrade silently to the rule path.

---

## Block order and rationale

```
O  Local-AI domain contracts (pure)     — ports + DeviceProfile/DeviceCapability + gating policy; lowest risk
P  ONNX runtime in :data:ai-local       — session, NNAPI+CPU fallback, tensor I/O, memory; platform-risk block
Q  DeviceProfile detector + model mgmt  — detector, single gate, on-disk store, WorkManager download/verify
   + WorkManager
R  Wire NLU IntentMatcher + close        — composite matcher via IntentConfidencePolicy, docs-sync, ADRs, close
```

- **O first** — pure JVM, everything else compiles against it; it is the only place the new ports +
  the (currently missing) `DeviceProfile` model + the gating policy are decided. Lowest risk.
- **P before Q** — Q's gate and WorkManager only matter once there is a real ONNX session to gate and a
  real model to load; P proves the session/NNAPI/memory mechanics on device first. P is the platform-risk
  block and is fully interface-gated, so Q (detector/worker) can be MockEngine/fake-tested against the
  ports independent of P's device run.
- **Q before R** — R wires the NLU source into the live pipeline; it must not light up until the gate +
  model availability exist, or `LOW_END`/no-model devices would attempt to load ONNX.
- **R last** — the merge into `HandleUserCommandUseCase`'s seam + docs-sync + phase close; the one
  correctness-sensitive spot (rule fast path must not regress) is reviewed on Opus.

---

## Fork decisions (all decided 2026-06-27 — agreed before code)

### Fork P6-1 — ONNX scope — **decided: NLU / classification / embeddings only; NOT generation**
`decisions.md:16-18,26` is unambiguous: ONNX is for **NLU / intent classification / embeddings**, it is
**not** the primary generative LLM runtime, and the local generative runtime stays **behind an
interface** for a future MediaPipe / llama.cpp integration. `architecture.md` has a **wording bug** —
it twice says the reserved router slot is for "*local NLU/**generation**, Phase 6*"
(`architecture.md:169,192`). **Decision:** Phase 6 feeds the **`IntentMatcher`** pipeline only
(`MatcherSource.NLU`); the **generative router's ONNX slot stays reserved** (local generation deferred).
**Block R fixes `architecture.md:169,192`** to read "*local NLU / classification, Phase 6*" and clarifies
that the generative ONNX slot is a *separate, still-reserved* concern. This keeps the two-port invariant
(`IntentMatcher` ≠ `GenerativeAiEngine`) intact.

### Fork P6-2 — NLU `IntentMatcher` composition — **decided: pure `LayeredIntentMatcher` over the port, rule-first**
The NLU source combines with `RuleBasedIntentMatcher` via a **composite that itself implements
`IntentMatcher`** — exactly mirroring how `DefaultGenerativeRouter` composes engines behind the
`GenerativeAiEngine` port. `LayeredIntentMatcher(primary = rule, secondary = nlu, policy)` lives in
**`:data:repository`** (same module + precedent as `DefaultGenerativeRouter`; refs only the **domain
port + policy**, so **no `data→data` edge** — it never imports `RuleBasedIntentMatcher` or the ONNX class,
both injected as the `IntentMatcher` port).
- **Rule-first fast path preserved (`< 10ms`):** if the rule result is **not** low-confidence
  (`!policy.isLowConfidence(rule.confidence)`), return it **verbatim**, NLU **never consulted**.
- **NLU only on low confidence:** consult `secondary.match(...)`, then pick the higher-confidence result;
  if NLU clears `suggestThreshold` it wins (`source = NLU`), else the rule result stands.
- **Gate short-circuit:** when the gate is off (`LOW_END` / no verified model / thermals), the bound
  secondary is a **`NoOpIntentClassifier`** (always lowest confidence) so `LayeredIntentMatcher` collapses
  to the rule result — **identical** behaviour and timing to today.
- **`HandleUserCommandUseCase` untouched:** Block R only changes the **DI binding** of the unqualified
  `IntentMatcher` from `RuleBasedIntentMatcher` to `LayeredIntentMatcher` (qualified `@RuleMatcher` /
  `@NluMatcher` providers → composite), exactly like Phase 5's `@CloudEngine`/`@FallbackEngine` → router.

### Fork P6-3 — Model provisioning + verification — **decided: WorkManager download + SHA-256 verify, internal storage, no bundling**
- **Not bundled in the APK** — a model in every install bloats the APK and can't be updated; download
  keeps the base install small and the model swappable.
- **On-disk location:** app-internal storage only (`context.filesDir`/`noBackupFilesDir`, e.g.
  `filesDir/models/`), never external/shared storage.
- **Integrity:** **SHA-256 checksum verified before the model is ever loaded** — the expected hash is
  **pinned in code/config** (a signature scheme is a later hardening, noted frozen). Download writes to a
  **quarantine temp file**; only after the hash matches is it **atomically renamed** into the
  ready location and marked available. **No model is loaded from an unverified source** (hard invariant).
- Tied to Fork P6-7's WorkManager constraints; availability is surfaced via Fork P6-4's gate input.

### Fork P6-4 — `DeviceProfile` gating — **decided: single pure gate, building `DeviceProfile` from scratch**
Because `DeviceProfile` **does not exist** (pre-flight delta), Phase 6 **creates** it:
- `:domain` (Block O): `enum class DeviceProfile { LOW_END, MID_RANGE, HIGH_END }` +
  `DeviceCapability` (RAM bytes, CPU cores, NNAPI-available, thermal-OK, battery-OK) +
  a **pure** `LocalInferenceGate` policy: `fun allowsLocalNlu(profile, capability, availability): Boolean`.
  **`online` is irrelevant to `allowsLocalNlu`** (local NLU needs no network), so the gate function must
  **not** take it; `online` may remain on `DeviceCapability` for other consumers, but the gate never reads it.
- `:core:android` (Block Q): `AndroidDeviceProfiler` produces `DeviceProfile`/`DeviceCapability`
  (precedent: `AndroidConnectivityChecker`); `PreferencesMapper` maps `DeviceProfile ↔
  DeviceProfileCacheEntry` (the existing flattened cache DTO) with no new DataStore keys.
- **Single gate *policy*, evaluated at two moments (not "one DI site").** The rule lives in exactly one
  pure function — `LocalInferenceGate.allowsLocalNlu(profile, capability, availability)` — but its
  inputs split by volatility:
  - **Static inputs — evaluated once at DI/graph time:** `DeviceProfile` (`LOW_END` never loads ONNX) +
    model `availability`. These decide whether the bound NLU secondary is the real
    `OnnxIntentClassifier` or the `NoOpIntentClassifier`, and whether the download worker may be enqueued.
  - **Dynamic inputs — re-evaluated at inference time inside `OnnxIntentClassifier`:** `thermalOk` /
    `batteryOk` change at runtime; a static binding cannot react to them. Before loading/running the
    session the classifier calls the **same** pure gate with fresh capability — if it returns `false`
    (thermal throttle / battery-saver) it returns a low-confidence/empty result so `LayeredIntentMatcher`
    falls back to the rule path, and the session is closed (Fork P6-9).
  One policy, one rule, two evaluation moments — **no second policy and no duplicated decision logic**.
- `LOW_END` → never loads ONNX (rule + cloud only); `MID_RANGE` → ONNX **iff** model available **and**
  thermals/battery OK; `HIGH_END` → enabled (still gated by model availability + thermals/battery).
- Phase 6 effectively uses **LOW_END-vs-rest**: `MID_RANGE` and `HIGH_END` share the same effective gate
  (model available + thermal/battery OK), and the three-way split is **forward-looking** — it is not yet
  differentiating MID from HIGH.

### Fork P6-5 — NNAPI / execution provider — **decided: CPU is the default deterministic path; NNAPI is an opportunistic bonus on a narrow API window, off-by-default until proven, inside `:data:ai-local`**

**Repo/platform reality (web-verified 2026-06-27):** **NNAPI was deprecated in Android 15** (Google
steers on-device ML to TFLite-in-Play-Services / TFLite GPU delegate). The **ONNX Runtime NNAPI EP is
only available on API level 29+ and is silently ignored on API 28 and below** — and the launcher
targets **API 28+**, so on Android 9 the NNAPI EP never activates regardless. In the middle window
(API 29–34) NNAPI is real but **vendor-driver-provided and historically unstable**: unsupported ops are
offloaded to a CPU reference (often *slower*) and some SoCs return *incorrect* results.

**Decision:**
- **CPU EP is the deterministic default.** ORT EPs are an ordered list with CPU always retained; the
  session is correct and usable on CPU alone on every supported device. The `< 150ms` MID_RANGE budget
  is **measured on the CPU path**.
- **NNAPI is appended opportunistically only on API 29+** (skipped by construction on API 28 — the EP
  would be ignored anyway). It is best-effort acceleration, **not** a dependency.
- **Two failure modes, both handled.** (a) *Init failure* — `addNnapi` throws / op unsupported → catch,
  build a CPU-only session, log a non-PII warning, never crash. (b) *Init-success-but-degraded* — a
  session that builds with NNAPI but runs slower/wrong is the **real** risk and the one a naive
  "catch init exception" misses. Phase 6 therefore keeps **NNAPI off by default, behind a
  build/profile flag**, and only enables it per-device after the Block-P device run (SM-A325F + any
  target SoC) shows it is **faster *and* correct** against the CPU baseline.
- All EP detail is **contained in `:data:ai-local`** — no EP leaks to other modules. NNAPI ships inside
  the `onnxruntime-android` AAR → **no extra dependency**.
- ⚠ The exact `ai.onnxruntime` Java signatures (`OrtSession.SessionOptions().addNnapi(EnumSet<NNAPIFlags>)`,
  `OrtEnvironment.getEnvironment()`, `OnnxTensor.createTensor`) must still be **re-verified against the
  `ai.onnxruntime` Javadoc at Block P** — context7's Android-Java coverage was thin (see pre-flight).
- **Migration note (frozen-forward):** because NNAPI is deprecated, the long-term acceleration path is
  TFLite-in-Play-Services / GPU delegate (or an ORT successor EP); revisited in a later hardening
  phase, not Phase 6.

### Fork P6-6 — Embeddings — **decided: ship the `TextEmbedder` PORT ONLY; defer the impl (no consumer yet)**
Phase 6's real consumer is **intent classification**, not embeddings. Block F's
`SuggestionRankingRepository` is a row-count-capped history store, **not** a semantic-ranking surface;
semantic suggestion ranking is **Phase 7 (contextual suggestions)**. **Decision:** define `TextEmbedder`
(`suspend fun embed(text): OperationResult<FloatArray>` — the codebase-wide "OperationResult, never
throw" invariant) + a **fake** in `:core:testing`
in Block O, and **do not build an ONNX embedder impl** this phase — building a producer with no reader is
forbidden by the prompt's discipline. The impl lands when Phase 7's ranking consumes it, behind the same
port, with zero domain change.

### Fork P6-7 — WorkManager — **decided: `CoroutineWorker` + `@HiltWorker`, battery-aware, no `LOW_END` background**
- `ModelDownloadWorker : CoroutineWorker`, `@HiltWorker` + `@AssistedInject(@Assisted Context, @Assisted
  WorkerParameters)`; `HiltWorkerFactory` provided via `app`'s `Configuration.Provider` (manifest
  `WorkManagerInitializer` handled per the AndroidX note — exact init path re-verified at Block Q).
- **Constraints (verified API):** `setRequiredNetworkType(CONNECTED/UNMETERED)`,
  `setRequiresBatteryNotLow(true)`, `setRequiresStorageNotLow(true)`. **Not** `requiresCharging` by
  default (too restrictive for a one-shot small model), but **battery-saver respected**.
- **No `LOW_END` background by default:** the **gate is checked before `enqueueUniqueWork`** — `LOW_END`
  never schedules the worker. **Idempotent:** the worker no-ops if a verified model already exists.
  **Cancellable:** `enqueueUniqueWork(KEEP)` (don't restart an in-flight download) + cooperative
  cancellation in `doWork`. **No
  foreground service** (`architecture.md` background-processing rules).
- **Flow:** download → SHA-256 verify (Fork P6-3) → atomic mark-available → availability surfaced to the
  gate (file presence + a small DataStore/`ModelAvailabilityRepository` flag). New deps in Fork P6-11.
- Note the **existing Room-KSP / Hilt-kapt hybrid**: `hilt-work`'s processor runs through the **Hilt
  (kapt)** path, not KSP.

### Fork P6-8 — Testing strategy — **decided: interface-gate everything; ONNX only in `androidTest` on device**
ONNX cannot run on the JVM. **Every JVM test exercises the ports/fakes, never a real session.**
`:core:testing` (JVM-only, Block I caveat) gets a **`FakeIntentClassifier`/`NoOpIntentClassifier`** and a
**`FakeModelAvailabilityRepository`** (+ a `FakeTextEmbedder`). The **real ONNX session runs only in
`data/ai-local/src/androidTest` on the SM-A325F** (Block J / `MigrationTest` precedent). The
**`< 150ms` `MID_RANGE` inference budget is a device measurement**, recorded as a **device-pending
acceptance item**, never a JVM assertion. JVM coverage: the gate policy, the `LayeredIntentMatcher`
merge logic (rule-first, low-confidence-consult), the SHA-256 verifier, and the worker's
enqueue/idempotency logic against fakes.

### Fork P6-9 — Model lifecycle / memory — **decided: lazy single shared session, closed under memory pressure**
- **Lazy init** — the session is created on the **first inference**, **never on launcher cold start**
  (hard invariant). A `LowEnd`/no-model device never creates one (the bound classifier is the no-op).
- **Single shared session** — one `@Singleton`-scoped `OnnxIntentClassifier` holding one `OrtSession`
  (`AutoCloseable`), guarded for concurrent `run`.
- **Closed under memory pressure** — register `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_*)` (in
  `:app` or via a lifecycle hook) to **close** the session and free native memory; it lazily re-inits on
  the next gated inference. The session is **also** closed when the **dynamic gate re-evaluation in Fork
  P6-4** returns `false` (thermal throttle / battery-saver, checked inside the classifier before each
  inference) — same `LocalInferenceGate` re-check, no new mechanism — lazily re-initing on the next
  gated inference.
- **Per-profile heap ceilings respected** (LOW_END `< 80MB` never loads; MID `< 150MB`; HIGH `< 250MB`).

### Fork P6-10 — Privacy — **decided: on-device only, no input persisted/logged, network only for download**
Inference is **fully on-device**; the **only** network use in Phase 6 is the WorkManager **model
download**. NLU/embeddings **inputs are never persisted and never sent to cloud**; the classifier
receives the same **normalized** string the rule matcher does, so **Block F SEARCH/UNKNOWN redaction
still governs what (if anything) reaches history** — and the classifier itself **persists nothing**.
**No user text in logs** (NNAPI-fallback and error logs carry only non-PII reasons). A grep/inventory
guard (Phase-4/5 precedent) asserts `:data:ai-local` performs **no network I/O on the inference path**
and logs no input.

### Fork P6-11 — New dependencies
- **ONNX Runtime Mobile** — **already in the catalog** (`onnxruntime-android` 1.20.0) and **already
  wired** in `:data:ai-local/build.gradle.kts` → **NOT new**. **Decision: keep `1.20.0` pinned for
  Phase 6** for reproducibility (upstream is at 1.25.0 per context7; any bump is its own change with its
  own device validation, frozen out of this phase). **NNAPI EP is bundled in the AAR** — no separate package.
- **WorkManager** (`androidx.work:work-runtime-ktx`, ~2.10.0) — **genuinely new** (catalog + `:app`/Q
  module deps).
- **`androidx.hilt:hilt-work`** (+ `androidx.hilt:hilt-compiler` for the worker factory, ~1.2.0/1.3.0) —
  **genuinely new**; processor runs via the **Hilt kapt** path (existing hybrid).
- **New module edge:** `:data:ai-local` gains a **`:core:android`** dependency (currently absent) — needed
  for `Context` (model file load) + `DeviceProfile`. Allowed by `architecture.md:117`.
- **R8/ProGuard for ONNX = Phase 9** (`roadmap.md:81`, `architecture.md:395,403`). Phase 6 adds **only**
  the minimal keep rule needed to *load* the runtime in any minified build if one proves necessary
  (`-keep class ai.onnxruntime.** { *; }`); the **full release rule set + memory profiling on `LOW_END`**
  stay frozen to **Ph9**. Debug builds (this phase's target) need no keep rules.
- **`security-crypto` / `EncryptedSharedPreferences`** — remain **forbidden** (deprecated).

### Open questions (gating — must be closed before the owning block)

1. **Model + tokenizer + label-set selection (gates Block P) — RESOLVED 2026-06-28.**
   - **Base model:** a **BERT-Mini / TinyBERT-4L-class** encoder (e.g. `google/bert_uncased_L-4_H-256`
     or TinyBERT-4L), **fine-tuned** for sequence classification on a small in-repo dataset of
     launcher-style commands, **dynamic int8** quantized, exported to ONNX at an opset **ORT 1.20.0
     supports** (≤ ~22 — pin + verify at export). Target artifact ~6–15 MB. Chosen for the smallest
     download/load and a comfortable margin under the `< 150ms` MID_RANGE CPU budget; a 7-class task
     does not need a larger backbone. (MobileBERT int8 ~25MB is the fallback only if accuracy on our
     dataset is insufficient.)
   - **Tokenizer:** **BERT WordPiece, uncased `vocab.txt` (30522)** — MUST equal the base model's
     tokenizer; shipped as an asset alongside the model. No approximation; a minimal Kotlin WordPiece
     implementation (or a vetted lib) in `:data:ai-local`.
   - **Label set (7 classes, argmax → `LauncherIntent`):**
     `LAUNCH_APP` → `LaunchAppIntent(displayNameQuery = <heuristic slot>)`;
     `SEARCH` → `SearchIntent(query = <heuristic slot>, target = WEB)`;
     `OPEN_SETTINGS` → `OpenSettingsIntent()`;
     `SHOW_APPS` → `SimpleCommandIntent(SHOW_APPS)`;
     `HELP` → `SimpleCommandIntent(HELP)`;
     `OPEN_ASSISTANT` → `SimpleCommandIntent(OPEN_ASSISTANT)`;
     `UNKNOWN` → `UnknownIntent`. **`CLEAR` is NOT a class** — "clear input" is a fixed UI phrase the
     rule matcher already nails; left rule-only.
   - **Slot-filling is heuristic, NOT modeled.** A sequence classifier emits the intent *family* only;
     the app-name / query string for `LAUNCH_APP` / `SEARCH` is derived by stripping a leading
     verb/filler, else passing the whole normalized input as the slot, which `IntentActionResolver`
     fuzzy-matches against installed apps. A joint intent+slot model is **frozen-forward (Phase 7+)**.
   - **Pipeline deliverable:** an **offline** fine-tune → int8-quantize → ONNX-export script/notebook
     in-repo (NOT app code) authoring a small labeled launcher-command dataset across the 7 classes;
     it produces the artifact + `vocab.txt` that Block Q downloads + SHA-256-verifies. The pinned hash
     and host are Open Question #2 (Block Q).
2. **Model download source / hosting (gates Block Q).** Fork P6-3 pins a SHA-256 hash but a pinned hash
   is meaningless without a pinned artifact, and Phase 5 deliberately chose **no backend** (BYOK). The
   host (CDN / GitHub release / object store) must be decided before Block Q wires the download worker;
   until then the worker has no URL.

---

## Block O — Local-AI domain contracts (pure)

**Goal:** vendor-/runtime-neutral local-AI contracts + the `DeviceProfile`/`DeviceCapability` model +
the pure gating policy, in `:domain`. JVM-only, no impls.
**Depends on:** nothing. Forks 1/2/4/6/8 inform the shapes.

**New files (`:domain`):**
- `…domain.ai.local`: `IntentClassifier` (a thin alias-shaped port producing `IntentMatchResult` with
  `source = MatcherSource.NLU` — **NOT** a new `MatcherSource`); `TextEmbedder` (**port only**, Fork P6-6);
  `ModelId` (opaque value class) + `ModelAvailability` (`Available`/`Missing`/`Unverified`).
- `…domain.device`: `DeviceProfile` (`LOW_END`/`MID_RANGE`/`HIGH_END`) + `DeviceCapability` (RAM, cores,
  nnapiAvailable, thermalOk, batteryOk, online) + `DeviceProfileProvider` port; `LocalInferenceGate`
  (pure policy: `allowsLocalNlu(profile, capability, availability): Boolean`).
- `…domain.ai.local`: `ModelAvailabilityRepository` port (`availability(modelId): Flow<ModelAvailability>`
  / `markAvailable / markMissing → OperationResult`).

**New fakes (`:core:testing`, JVM-only):** `FakeIntentClassifier` / `NoOpIntentClassifier` (scripted /
always-lowest-confidence), `FakeTextEmbedder`, `FakeModelAvailabilityRepository`, `FakeDeviceProfileProvider`.

**Steps:**
- [ ] `O1` Local-AI ports (`IntentClassifier`, `TextEmbedder` port-only, `ModelId`, `ModelAvailability`).
- [ ] `O2` `DeviceProfile` + `DeviceCapability` + `DeviceProfileProvider` + `ModelAvailabilityRepository`.
- [ ] `O3` Pure `LocalInferenceGate` policy (LOW_END never; MID conditional; HIGH enabled).
- [ ] `O4` Fakes in `:core:testing` (JVM-only, no Android variants).
- [ ] `O5` JVM tests: gate truth-table (all 3 profiles × availability × thermal/battery); fake classifier
      returns `source = NLU`; `NoOpIntentClassifier` is always low-confidence.

**Acceptance:** `:domain` stays stdlib+coroutines (purity guard green); no ONNX/Android/WorkManager term
anywhere in `:domain` (grep guard); fakes compile JVM-only; gate truth-table green; **intent code +
`HandleUserCommandUseCase` + Phase-3/5 tests untouched**; no new deps.

**Demoable milestone:** a unit test drives `LocalInferenceGate` — `LOW_END` → `false` regardless of model,
`MID_RANGE` + available + cool → `true`, `MID_RANGE` + unverified → `false`; a `FakeIntentClassifier`
returns an `IntentMatchResult(source = NLU)`.

---

## Block P — ONNX runtime integration in `:data:ai-local`

**Goal:** a lazy, single, memory-safe `OnnxIntentClassifier : IntentClassifier` with opportunistic NNAPI
and deterministic CPU fallback. The platform-risk block.
**Depends on:** O (ports). Forks 5/8/9/11 fixed.

**New files (`:data:ai-local`):** `OnnxIntentClassifier` (session mgmt, tensor I/O, `IntentMatchResult`
mapping, `source = NLU`); `OnnxSessionFactory` (env + `SessionOptions` + NNAPI-then-CPU); a small
tokenizer/label-map helper matching the chosen model; `:data:ai-local/src/androidTest/...` device tests.
Build: add `:core:android` edge (+ minimal ONNX keep rule note, deferred to Ph9 for full rules).

**Steps:**
- [ ] `P1` `OnnxSessionFactory`: `OrtEnvironment` + `SessionOptions`; **CPU is the deterministic default
      — always kept**; **append NNAPI EP only on API 29+** (skipped by construction on API 28); handle
      **both** failure modes per Fork P6-5 — (a) *init failure* → catch → CPU-only session, no crash, and
      (b) *init-success-but-degraded* → **NNAPI off by default** behind a build/profile flag until the
      Block-P device run proves it **faster *and* correct** vs the CPU baseline. ⚠ verify
      `ai.onnxruntime` Java signatures against the Javadoc first.
- [ ] `P2` `OnnxIntentClassifier`: **lazy** session (first inference only, never cold start); **single
      shared** session; input tokenization → `OnnxTensor`; `session.run` → output → `IntentMatchResult`
      (`source = NLU`, confidence from softmax); concurrency-guarded.
- [ ] `P3` **Memory lifecycle**: `close()`/`AutoCloseable`; closeable under `onTrimMemory`; lazily
      re-inits next inference; respects per-profile heap ceilings.
- [ ] `P4` **Graceful degrade**: any session/inference failure → low-confidence/empty result (so
      `LayeredIntentMatcher` falls back to rule), never an exception to the caller; no user text logged.
- [ ] `P5` `androidTest` on SM-A325F: loads a small verified model, runs inference, NNAPI→CPU fallback
      path exercised, **records `< 150ms` MID_RANGE latency (device-pending acceptance)**.

**Acceptance:** ONNX confined to `:data:ai-local` (grep: no `ai.onnxruntime` import elsewhere); session
is lazy + single + closeable; NNAPI failure degrades to CPU without crashing; inference failure degrades
to rule path; `androidTest` compiles (device run pending, like Block J); no user text in logs.

**Demoable milestone (device-pending):** on the SM-A325F, a tiny intent model classifies a phrase the
rules miss and returns an `IntentMatchResult(source = NLU)`; pulling NNAPI still yields a CPU result.

---

## Block Q — `DeviceProfile` detector + model management + WorkManager gating

**Goal:** the single capability gate made real — `DeviceProfile` detection, on-disk model store +
availability surface, and the battery-aware WorkManager download/verify worker.
**Depends on:** O (gate policy + `DeviceProfileProvider`/`ModelAvailabilityRepository` ports), P (a
session to gate). Forks 3/4/7/11 fixed.

**New files:**
- `:core:android`: `AndroidDeviceProfiler : DeviceProfileProvider` (RAM via `ActivityManager.MemoryInfo`,
  cores via `Runtime.availableProcessors`, NNAPI-availability heuristic, thermal via `PowerManager`,
  battery via `BatteryManager`/`PowerManager.isPowerSaveMode`).
- `:data:ai-local`: `ModelStore` (internal-storage paths, quarantine→atomic rename), `Sha256Verifier`,
  `ModelDownloadWorker : CoroutineWorker` (`@HiltWorker`), `ModelManager` (enqueue-if-gated + idempotent)
  — they handle model files for ONNX and the module is already gaining the `:core:android` edge for
  `Context`.
- `:data:repository`: `ModelAvailabilityRepositoryImpl` (DataStore flag + file presence) — persistence-layer
  precedent.
- `:app`: `Configuration.Provider` + `HiltWorkerFactory` wiring; DI for profiler/store/worker; catalog +
  module deps for WorkManager + `hilt-work`.

**Steps:**
- [ ] `Q1` `AndroidDeviceProfiler` → `DeviceProfile`/`DeviceCapability`; map to/from
      `DeviceProfileCacheEntry` in `PreferencesMapper` (no new DataStore keys).
- [ ] `Q2` `ModelStore` + `Sha256Verifier`: download to quarantine, verify against the **pinned**
      expected hash, **atomic rename** on match, mark available; **never expose an unverified file**.
- [ ] `Q3` `ModelDownloadWorker` (`CoroutineWorker`+`@HiltWorker`) with constraints
      `requiresBatteryNotLow` + `requiresStorageNotLow` + network; idempotent (no-op if verified model
      exists); cancellable; **no foreground service**.
- [ ] `Q4` `ModelManager.ensureModel()`: **checks the gate first** — `LOW_END` (or gate-off) → never
      enqueue; otherwise `enqueueUniqueWork`. WorkManager + `HiltWorkerFactory` + `Configuration.Provider`
      wired in `:app` (init path re-verified via context7).
- [ ] `Q5` JVM tests (against fakes): gate-before-enqueue (LOW_END never schedules); SHA-256 verify
      accept/reject; idempotent re-run; availability flips on verified rename. Worker logic tested via
      fakes; no real ONNX.

**Acceptance:** `DeviceProfile` is produced and cached; the gate policy is the **single** source of the
rule (static inputs at DI, dynamic thermal/battery re-checked at inference — Fork P6-4);
`LOW_END` never enqueues download or loads ONNX; a corrupt/unmatched download is rejected and never
loaded; worker is idempotent + cancellable + battery-aware + no foreground service; new deps limited to
WorkManager + `hilt-work`.

**Demoable milestone:** on a `MID_RANGE`+battery-not-low device, `ensureModel()` downloads, SHA-256-passes,
and flips availability → `Available`; flip to `LOW_END` (or battery-saver) and `ensureModel()` enqueues
nothing.

---

## Block R — Wire NLU `IntentMatcher` source + docs-sync / phase close

**Goal:** merge the NLU source into the live pipeline via a composite that preserves the rule fast path,
reconcile the docs to as-built, and close Phase 6.
**Depends on:** O, P, Q. Forks 1/2 fixed.

**New files:** `LayeredIntentMatcher : IntentMatcher` in `:data:repository` (composes the `IntentMatcher`
port + `IntentConfidencePolicy`); `:app` DI — `@RuleMatcher`/`@NluMatcher` qualified providers +
gate-driven selection of real `OnnxIntentClassifier` vs `NoOpIntentClassifier` for the secondary; the
unqualified `IntentMatcher` binding becomes `LayeredIntentMatcher`.

**Steps:**
- [ ] `R1` `LayeredIntentMatcher`: rule-first; return rule verbatim when not low-confidence (NLU never
      called, `< 10ms` preserved); on low-confidence consult NLU and pick the higher-confidence result.
- [ ] `R2` DI: qualified `@RuleMatcher`(=`RuleBasedIntentMatcher`) + `@NluMatcher`(=gate ? `OnnxIntentClassifier`
      : `NoOpIntentClassifier`) → `LayeredIntentMatcher` as the unqualified `IntentMatcher`.
      **`HandleUserCommandUseCase` and `IntentProvidesModule.provideHandleUserCommandUseCase` only see
      the swapped binding — no use-case code change.**
- [ ] `R3` JVM tests: rule-high-confidence → NLU never invoked (verified via a counting fake) + identical
      outcome to Phase 3; rule-low + NLU-high → NLU wins (`source = NLU`); gate-off (NoOp secondary) →
      behaviour identical to rule-only; **all Phase-3 intent tests still green**.
- [ ] `R4` **Docs-sync:** fix `architecture.md:169,192` ONNX-slot wording (NLU/classification, generative
      slot still reserved); document the as-built local-NLU pipeline (`DeviceProfile`, gate, ONNX
      isolation, WorkManager download/verify); update the Contract→Owner table for the new ports/owners.
- [ ] `R5` ADRs in `decisions.md` (ONNX scope, composite-matcher seam, gate, model verify, WorkManager);
      advance `CLAUDE.md` status; mark Phase 6 closed; note device-pending acceptance (P5/Q on SM-A325F).

**Acceptance:** the rule fast path and every Phase-3 test are unchanged; a rules-missed phrase resolves
via NLU when the gate is on; gate-off devices behave exactly as today; `HandleUserCommandUseCase`
untouched; docs match reality; `assembleDebug` + full JVM regression green (ONNX device run pending).

**Demoable milestone:** "open telegram" still resolves on the `< 10ms` rule path (NLU not consulted);
"fire up the camera" (no rule) routes through NLU and resolves on a gated device — the Phase-6 analog of
Phase-3 "open telegram" / Phase-5 "airplane → static fallback".

---

## Frozen / pushed forward (Phase 7+)

- **Local generative LLM** (MediaPipe / llama.cpp behind an interface) + filling the **generative
  router's reserved ONNX slot** → **Ph7+** (explicitly NOT Phase 6; `decisions.md:18`).
- **ONNX `TextEmbedder` impl** + semantic suggestion ranking consuming it → **Ph7** (contextual
  suggestions); the port ships now, the impl when there is a reader.
- **Voice / `SpeechInputSource`** → **Ph7**.
- **Full R8/ProGuard rules for ONNX + release memory profiling on `LOW_END`** → **Ph9**
  (`roadmap.md:81`).
- **WorkManager beyond model download** (suggestion pre-compute, history cleanup) → **Ph7/9**.
- **ONNX version bump 1.20.0 → current** → deferred, its own change + device validation.
- **NNAPI deprecation / migration to TFLite-in-Play-Services or GPU delegate** → later hardening phase
  (NNAPI deprecated in Android 15; Phase 6 keeps CPU-default with opportunistic NNAPI on API 29+).
- **Model signature verification** (beyond SHA-256 checksum) → later hardening.
- **Full Hilt→KSP migration** → **Ph9** (Phase 6 keeps the Room-KSP / Hilt-kapt hybrid; `hilt-work`
  runs via kapt).

## Demoable milestones (like `open telegram` for Phase 3)

- **O:** the gate truth-table passes in a unit test; a fake classifier emits `source = NLU`.
- **P (device-pending):** a tiny ONNX model classifies on the SM-A325F; NNAPI→CPU fallback still returns.
- **Q:** a `MID_RANGE`+battery-not-low device downloads + SHA-256-verifies a model and flips availability;
  `LOW_END`/battery-saver enqueues nothing.
- **R:** "open telegram" stays on the `< 10ms` rule path; a rules-missed phrase resolves through NLU on a
  gated device; gate-off devices behave exactly as before.

## Tracking

- One execution prompt per block; **Block O first** (gated, like "Block E only" / "Block I only").
- Record each block as an ADR in `decisions.md`; advance `CLAUDE.md` per block.
- **Per-block gating:** O green before P; **P's `androidTest` (device) + Q before R**; R closes the phase.
- Device-pending acceptance carried forward: Block-P inference budget + Block-J `SecretStoreInstrumentedTest`
  + Block-N N5 (all SM-A325F).

## Agent model per block (which Claude to run Claude Code on)

Rule of thumb: **Opus 4.8** where the *abstraction is being decided* or correctness is subtle and
expensive to get wrong (contracts, session/memory, NNAPI fallback, gating, verification, the merge seam).
**Sonnet 4.6** where the block is *execution against a pinned spec* (worker wiring, DI, mappers, tests,
docs). Independent of which model executes, do **planning + diff review on Opus**.

| Block | Model | Why |
|---|---|---|
| **O** — Local-AI contracts + `DeviceProfile` + gate | **Opus 4.8** *(Sonnet acceptable)* | Foundation everything compiles against; the `DeviceProfile`/capability model + the gating policy are decided here, and a wrong gate contract is the most expensive mistake. Shapes are pinned, so Sonnet *can* execute round 1 — prefer Opus. |
| **P** — ONNX runtime integration | **Opus 4.8** | Session lifecycle, NNAPI-with-CPU-fallback, tensor I/O, memory/`onTrimMemory`, lazy-single-session. Subtle, native, costly to get wrong; the Java API also needs careful first-time verification. |
| **Q** — Detector + model mgmt + WorkManager | **Opus 4.8** for the gate + SHA-256 verify + gate-before-enqueue; **Sonnet 4.6** for the worker/DI wiring | Verification + "no unverified model loaded" + single-gate correctness are security-shaped (Opus); the `CoroutineWorker`/`HiltWorkerFactory`/constraints wiring is pattern-following (Sonnet). |
| **R** — Wire NLU source + close | **Sonnet 4.6**, **review the merge seam on Opus** | DI swap + docs-sync + ADRs are mechanical, but the `LayeredIntentMatcher` rule-fast-path-preservation is the one regression-sensitive spot — review it on Opus. |

## Decisions confirmed (2026-06-27)

1. **P6-1 — ONNX scope:** NLU / classification / embeddings only; **not** generation. Feeds the
   `IntentMatcher` pipeline; the **generative router's ONNX slot stays reserved**; Block R fixes the
   `architecture.md` "NLU/generation" wording.
2. **P6-2 — NLU composition:** pure `LayeredIntentMatcher` (in `:data:repository`, port-only, no
   data→data edge) — rule-first `< 10ms` fast path, NLU consulted only on low confidence, merged via
   `IntentConfidencePolicy`; **`HandleUserCommandUseCase` untouched** (DI binding swap only).
3. **P6-3 — Model provisioning:** WorkManager download (not bundled), internal storage, **SHA-256
   verify against a pinned hash before any load**, quarantine→atomic-rename; **no unverified model loaded**.
4. **P6-4 — Gating:** build `DeviceProfile`/`DeviceCapability` from scratch (model in `:domain`, detector
   in `:core:android`); **single** pure `LocalInferenceGate`; `LOW_END` never loads ONNX, `MID_RANGE`
   conditional, `HIGH_END` enabled.
5. **P6-5 — NNAPI:** **CPU is the deterministic default**; NNAPI opportunistic on **API 29+ only**,
   **off-by-default until a device run proves it faster-and-correct**; **both** init-failure and
   degraded-success handled; NNAPI **deprecated (Android 15) → migration noted frozen-forward**; exact
   `ai.onnxruntime` Java signatures re-verified at Block P; confined to `:data:ai-local`.
6. **P6-6 — Embeddings:** **`TextEmbedder` port only**, impl deferred to Phase 7 (no consumer yet).
7. **P6-7 — WorkManager:** `CoroutineWorker`+`@HiltWorker`, battery/storage-not-low constraints,
   **no `LOW_END` background**, idempotent + cancellable, no foreground service.
8. **P6-8 — Testing:** interface-gate everything; fakes in `:core:testing` (JVM-only); real ONNX only in
   `androidTest` on SM-A325F; `< 150ms` MID_RANGE is a **device-pending** acceptance item.
9. **P6-9 — Lifecycle/memory:** lazy single shared session, never on cold start, closed on
   `onTrimMemory`/gate-off, per-profile heap ceilings respected.
10. **P6-10 — Privacy:** on-device inference only (network solely for download); inputs never
    persisted/sent/logged; Block F redaction holds.
11. **P6-11 — Deps:** ONNX already in catalog (pin 1.20.0); **new = WorkManager + `hilt-work`**;
    `:data:ai-local` gains a `:core:android` edge; full ONNX R8/ProGuard frozen to **Ph9**; ESP forbidden.
12. **First execution round:** **Block O only** (matches "Block E only" / "Block I only" gating).
