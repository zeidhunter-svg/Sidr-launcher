## Phase 5 — Block N execution prompt: Assistant streaming UI + provider-settings form + phase close

> **Run this block on Sonnet 4.6** (Compose UI + DI wiring + docs-sync, all pattern-following from prior
> blocks). **Escalate to Opus only if on-device cancellation/streaming misbehaves.** Plan + diff review
> on Opus regardless.
> Scope = **Block N only — the Phase-5 capstone.** Depends on **I, J, K, L, M** (all green). This block
> closes Phase 5.

## Role & context

Sidr Launcher, Phase 5, final block. Everything below the UI exists: the `GenerateReplyUseCase` (M,
streams `Flow<AiChunk>` through the router → cloud/static), `SecureSecretStore` (J, Keystore), and
`AiProviderConfigRepository` (K, DataStore). Block N builds the **real assistant screen** that drives
them, a **minimal inline provider-settings form** (base URL + API key + model), wires the navhost
destination, runs on-device acceptance, and **syncs docs + closes the phase**.

`:feature:assistant` is a **stub today** (`AssistantScreen` = a centered `Text("Assistant")`, no
ViewModel, no Hilt). The entry into it is the existing Block-D handoff
(`SimpleCommand.OPEN_ASSISTANT → CommandOutcome.OpenAssistant → LauncherViewModel.navigateTo(Routes.Assistant.ROUTE)`)
— **unchanged**. Matching ≠ generation; `HandleUserCommandUseCase` stays untouched.

## Pre-flight — verify before coding (repo-truth check)

- [ ] **`:feature:assistant` is a stub with no Hilt.** Its `build.gradle.kts` has compose but **no**
      `kotlin.kapt`/`hilt`/`hilt.navigation.compose`/`activity.compose`/`lifecycle.runtime.compose`/test
      deps. Add them — copy the **`feature/permission_education` build** verbatim as the template (it is
      the proven Hilt-feature precedent: `kapt` + `hilt.android` + `hilt.compiler` +
      `hilt.navigation.compose` + `activity.compose` + `lifecycle.viewmodel.compose` +
      `lifecycle.runtime.compose`; `testImplementation` `core:testing` + junit4 + coroutines-test).
- [ ] **The VM's three deps are domain ports already bound** — `GenerateReplyUseCase` (M's
      `GenerationProvidesModule`), `AiProviderConfigRepository` (K, bound in `PersistenceBindsModule`),
      `SecureSecretStore` (J, `SecretsBindsModule`). So **no new `:app` DI module is needed**; the
      `@HiltViewModel` is auto-constructed. **No `feature → data` edge** (deps are domain interfaces).
- [ ] **Patterns to mirror:** `LauncherViewModel` — `Channel<NavigationEvent>(BUFFERED)` +
      `receiveAsFlow()` + `navigateTo/Back` (3.1.4); `retry()` cancels the in-flight job and resets to
      `Loading` (latest-wins, H2); `UiState` mapping via `OperationError.toUiError()` + `isRetryable()`.
      `AppNavHost` already collects `navigationEvents` per destination and routes via
      `handleNavigationEvent(...)` (3.1.5 safe-fallback) — replicate for the assistant node.
- [ ] **Contracts:** `AiChunk.{Text(delta)/Completed(stopReason,usage?)/Failed(error)}`,
      `AiStopReason{…REFUSAL…}`, `AiError` (+ its Block-I KDoc UI-retryability table),
      `AiProviderConfig(providerId, baseUrl, modelId, displayName?)`, `AiProviderId`/`AiModelId`,
      `SecretKeys.apiKey(provider)`. `Routes.Assistant.ROUTE = "assistant"` exists.
- [ ] **`roadmap.md:49`** reads `Add API key storage through EncryptedSharedPreferences or backend proxy.`
      — N6 must correct it (ESP is deprecated/not used; the impl is Keystore AES-GCM via `SecureSecretStore`).

## Hard invariants (do NOT violate)

- **No `feature → feature` / `feature → data` edge.** `:feature:assistant` depends only on
  `domain` + `core:ui` + `core:common`. The VM uses **domain ports**, injected by Hilt from `:app`.
- **VM holds no Android types; UI holds no business logic** (the Block-H rule). The ViewModel emits
  `NavigationEvent` via the Channel — it never touches `NavHostController`.
- **The API key is never logged, never shown back in a field, never put in `SavedStateHandle`/state.**
  The key field is masked (`PasswordVisualTransformation`); on save it goes straight to
  `SecureSecretStore` and is dropped from memory. The form reads back only base URL + model (config) and
  a **boolean "key is set"** — never the key value.
- **Terminal-failure-as-value, end to end.** The VM collects `Flow<AiChunk>`; an `AiChunk.Failed` is a
  state update, never a thrown error. **Refusal (`AiChunk.Completed(REFUSAL)`) is a SUCCESS terminal**,
  rendered as a normal (declined) reply — **not** an error, **not** retryable.
- **`retry()` is latest-wins** (cancel the in-flight stream job before relaunching — H2). Streaming is
  collected in `viewModelScope` so it **survives config-change** (rotation does not abort/restart the
  request) and is aborted on **screen-leave** (`onCleared` cancels `viewModelScope`).
- **Launcher core stays offline + fast.** The engine/secret store/HTTP client are built lazily (only
  when the assistant VM is created); nothing added to the launcher cold path. `HandleUserCommandUseCase`
  + intent pipeline untouched.

## Out of scope (do not build here)

`:feature:settings` module (the form relocates there later — Block-G wallpaper-button precedent; leave a
NOTE) · multi-turn conversation history · voice input (Ph7) · ONNX (Ph6) · native Anthropic adapter
(optional post-N fast-follow). Do not modify Block K/L/M internals — N consumes them.

## Design decisions (bake these in)

1. **`AssistantViewModel` (`@HiltViewModel`, single state + nav channel).**
   ```kotlin
   @HiltViewModel
   class AssistantViewModel @Inject constructor(
       private val generateReply: GenerateReplyUseCase,
       private val providerConfig: AiProviderConfigRepository,
       private val secretStore: SecureSecretStore,
   ) : ViewModel() {
       private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
       val navigationEvents = _navigationEvents.receiveAsFlow()
       fun navigateBack() { _navigationEvents.trySend(NavigationEvent.NavigateBack) }

       private val _uiState = MutableStateFlow(AssistantUiState())   // single source of truth
       val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

       private var streamJob: Job? = null
       private var lastPrompt: String? = null
       // init: observe providerConfig.activeConfig() → reflect base-url/model + a `keySet` flag in state.
       //   keySet is derived per emit: config → providerId → secretStore.get(apiKey(providerId)) →
       //   boolean(non-blank). ONLY the boolean reaches state (never the key value), and it is
       //   RECOMPUTED on every activeConfig() re-emit so switching base-URL/host (new providerId, new
       //   key slot) never shows a stale "key set ✓" from the previous provider's slot.
       fun send(prompt: String) { /* cancel streamJob; launch in viewModelScope; collect chunks */ }
       fun retry() { lastPrompt?.let(::send) }     // latest-wins via the cancel in send()
       fun saveProvider(baseUrl: String, model: String, apiKey: String) { /* see decision 3 */ }
   }
   ```
   - **No `SavedStateHandle` here (deliberate).** Unlike the launcher (H3 restores `commandInput` via
     `SavedStateHandle`), the assistant does **not** persist prompt/reply across process death —
     `lastPrompt` is a plain field, transient by design — and the **key must never touch
     `SavedStateHandle`**. Call this out in the ADR as an intentional deviation from the H3 precedent,
     not an oversight.
   - `AssistantUiState(reply: String = "", status: AssistantStatus = Idle, form: ProviderFormState)` —
     a **plain data class** (the `PermissionEducationViewModel` precedent: a VM may hold plain state when
     there is no async-load/empty surface to model with `UiState<T>`). `status` is a sealed
     `AssistantStatus { Idle; Streaming; Done(refused: Boolean); Error(error: UiError, retryable: Boolean) }`
     — this is the "separate status" of Fork P5-5 expressed as a field, keeping one StateFlow.
   - **`send()`**: `streamJob?.cancel()`; set `reply=""`, `status=Streaming`; `streamJob =
     viewModelScope.launch { generateReply.generate(prompt).collect { chunk -> when … } }`:
     `Text→ reply += delta` (status stays Streaming); `Completed→ status=Done(refused = stopReason==REFUSAL)`;
     `Failed→ status=Error(error.toUiError(), retryable = error.isButtonRetryable())`. Re-throw
     `CancellationException` (don't convert it to an error state).
   - **VM-collected stream (resolves the Fork P5-5 tension).** Fork P5-5 says "Flow<AiChunk> (UI-collected)"
     *and* "retry() latest-wins" *and* "config-change". Collecting in `viewModelScope` is what makes
     latest-wins `retry()` and screen-leave abort work and keeps it unit-testable; the UI collects the
     VM's `StateFlow`. Config-change does **not** restart the stream (better UX than the literal note).
     **Flag this resolution for Opus review.**
2. **`AssistantScreen(viewModel)`** — pure render, no logic:
   - If **no provider configured** (`form` shows no base URL/model) → surface the **provider-settings
     form** prominently (first-run). Else show: a prompt input + Send, the streaming `reply` text,
     a status affordance, and an edit-provider entry (expander/gear) hosting the same form.
   - `status`: `Streaming` → progress indicator while `reply` grows; `Done(refused=true)` → render the
     reply with a subtle "the assistant declined" note (no retry); `Done(refused=false)` → final reply;
     `Error(error, retryable)` → message + a **Retry** button iff `retryable`. **`MissingCredentials`/
     `Unauthorized` are not button-retryable** → instead surface a "Set up / fix provider" CTA that opens
     the form (the actionable fix). Collect state with `collectAsStateWithLifecycle()`.
3. **Provider-settings form + `saveProvider`** (N3):
   - Fields: **base URL** (text), **model** (free-text string), **API key** (masked,
     `PasswordVisualTransformation`). Pre-fill base URL + model from `activeConfig()`; **never** pre-fill
     the key — show "key set ✓ / replace key" from the `keySet` boolean only.
   - `saveProvider(baseUrl, model, apiKey)`: derive a stable `providerId` from the base-URL host
     (e.g. `AiProviderId(host.lowercase())`) so switching endpoints keeps isolated key slots. **Pin the
     normalization rule in the ADR and make it deterministic** — host only, lowercased, port and
     userinfo/path stripped — and ensure it agrees with how Block K parses the base URL (so the eligibility
     gate and the request target never disagree). For Phase 5 (single active config) two providers sharing
     a host collapsing to one key slot is acceptable; document it, don't over-engineer. Validate
     `https://` (reject otherwise with an inline error — defense-in-depth with Block K);
     `providerConfig.setActiveConfig(AiProviderConfig(providerId, baseUrl, AiModelId(model), displayName
     = host))`; **if** `apiKey.isNotBlank()` → `secretStore.put(SecretKeys.apiKey(providerId), apiKey)`.
     Map any `OperationResult.Failure` to an inline form error. **Never log the key.**
   - `AiError → UiError` + `isButtonRetryable()` mapping per the Block-I KDoc table (Offline/Network/
     Timeout/RateLimited/ServerError/Unknown → retryable; MissingCredentials/Unauthorized/InvalidRequest
     → not button-retryable). **This mapper lives in `:feature:assistant`, NOT in `core/common`.**
     Per ADR Block B, `UiError` is in `core/common` **with no `domain` dep by design**, and the existing
     `OperationError → UiError` mapping happens **in the VM/feature layer**, not in `core/common`. Since
     `AiError` is a `domain` type, putting the mapper in `core/common` would add a forbidden
     `core/common → domain` edge — so keep it feature-local (next to the VM, which legitimately sees both
     `domain` and `core/common`). Verify where `OperationError.toUiError()` actually lives before
     mirroring it — it is not `core/common`. Do not leak a key/raw prompt into `UiError`.
4. **`AppNavHost` real destination (N4).** Replace `composable(Routes.Assistant.ROUTE) { AssistantScreen() }`
   with the launcher pattern:
   ```kotlin
   composable(Routes.Assistant.ROUTE) {
       val vm: AssistantViewModel = hiltViewModel()
       LaunchedEffect(vm.navigationEvents) {
           vm.navigationEvents.collect { handleNavigationEvent(navController, it) }
       }
       AssistantScreen(viewModel = vm)
   }
   ```
   Keep the 3.1.5 safe-fallback (already in `handleNavigationEvent`). Back from the screen calls
   `vm.navigateBack()`.

## Steps

- [ ] `N0` `:feature:assistant` build: add Hilt + compose-nav + lifecycle + test deps (copy
      `permission_education` build).
- [ ] `N1` `AssistantViewModel` + `AssistantUiState`/`AssistantStatus`/`ProviderFormState` (decision 1).
- [ ] `N2` `AssistantScreen` streaming render + status/refusal/error+retry + CTA (decision 2). No logic.
- [ ] `N3` Provider-settings form + `saveProvider` (masked key, https-validate, config+key persist) (decision 3).
- [ ] `N4` `AppNavHost` real assistant destination + nav-event collection + safe-fallback (decision 4).
- [ ] `N5` On-device acceptance (below).
- [ ] `N6` Docs-sync + **Phase-5 close** (below).

## Tests (JVM, `:feature:assistant`, via `:core:testing` fakes)

`AssistantViewModelTest` with `FakeGenerativeAiEngine`-backed `GenerateReplyUseCase` (construct the real
`GenerateReplyUseCase(fakeEngineAsRouter, PromptContextBuilder())`) + `FakeAiProviderConfigRepository` +
`FakeSecureSecretStore` (+ `UnconfinedTestDispatcher`/`runTest`):
- `send` accumulates `Text` deltas into `reply` in order; ends `Done(refused=false)` on `Completed(COMPLETE)`.
- **refusal**: `Completed(REFUSAL)` → `Done(refused=true)`, **not** an `Error`.
- **failure**: `Failed(Unauthorized)` → `Error(retryable=false)`; `Failed(Network)` → `Error(retryable=true)`.
- **retry latest-wins**: a second `send`/`retry` cancels the first stream (no interleaved/stale deltas).
- **saveProvider**: persists `AiProviderConfig` via the config fake **and** `put`s the key via the secret
  fake under `SecretKeys.apiKey(host)`; a blank key skips the `put`; non-`https://` base URL → inline
  error, nothing persisted; the key value never appears in `uiState`/any logged surface.
- **config presence** drives the first-run form vs. chat view (`activeConfig()` null vs set).

## On-device acceptance (N5 — run on SM-A325F, paste observations)

Requires a real provider configured on-device (paste a base URL + key + a light model). Verify:
- typing a question → **streamed** reply renders incrementally;
- **airplane mode → static fallback** reply (graceful, no crash);
- **cancel mid-stream** (navigate back) aborts the request; **retry** re-streams without app restart;
- **no key configured → "set up provider" CTA**, not a crash; key entry is masked.
- **The two behaviors the Fork-P5-5 resolution actually buys (check both explicitly):**
  - **rotate mid-stream → the request is NOT restarted** and the accumulated `reply` survives (VM +
    its `StateFlow` outlive config-change);
  - **navigate back mid-stream → the request IS aborted** (destination popped → VM `onCleared` →
    `viewModelScope` cancelled → the cold Ktor flow tears down). A logcat with no continued network
    traffic after back confirms it.
*(If no device is available in the execution environment, compile + JVM-test must still pass; record the
device run as pending, like the Block-J androidTest.)*

## N6 — Docs-sync + Phase-5 close

- **`docs/architecture.md`**: AI pipeline **as built** — `Flow<AiChunk>` streaming via
  `GenerateReplyUseCase` → `GenerativeRouter` (cloud-iff-online+config+key → static fallback; ONNX slot
  reserved Ph6); the **OpenAI-compatible** adapter (configurable base URL + free-text model, minimal body,
  no sampling); provider config in DataStore + **API key in Keystore via `SecureSecretStore`**. Update the
  security/secret section: `SecureSecretStore` is now **built** (Keystore AES-256-GCM, dedicated
  `sidr_secrets` store, BYOK) — drop the "deferred to Phase 5" framing; ESP stays deprecated/not used.
  Note generative AI lives on the **assistant screen**, not folded into `LauncherUiState` (`aiState`).
- **`docs/roadmap.md:49`**: replace `EncryptedSharedPreferences or backend proxy` with **Keystore
  AES-GCM via `SecureSecretStore` (BYOK; backend proxy a later drop-in behind the same port)**; note
  OpenAI-compatible BYOK + free-text model.
- **`decisions.md`**: ADR `Block N complete` (VM-collected streaming + Fork-P5-5 resolution, plain-state
  rationale, refusal-as-success, key-never-displayed/logged + **deliberate no-`SavedStateHandle`** vs the
  H3 precedent, host-derived providerId **+ its pinned normalization rule**, https-validate,
  CTA-not-retry for credentials, navhost wiring, **`AiError→UiError` mapper kept feature-local to avoid a
  `core/common→domain` edge**) **+ a short Phase-5 close summary** (Blocks I–N). **Honest close:** the
  summary must explicitly list the on-device items still **pending** a device run (Block-J `androidTest`
  round-trip + the N5 acceptance) rather than implying they passed — frame it "code + JVM green; on-device
  acceptance pending device run", exactly as Block J was recorded.
- **`CLAUDE.md`**: advance to **Phase 5 complete (Blocks I → N)** — with the same explicit
  on-device-pending caveat (J + N5) in the snapshot, not a blanket "verified on device"; next per roadmap.
- **`phase-5-plan.md`**: check off Block N; mark **Phase 5 closed**.

## Verification (run and paste actual output)

```bash
./gradlew :feature:assistant:testDebugUnitTest                  # AssistantViewModel suite
./gradlew assembleDebug                                         # full Hilt graph + real assistant node
./gradlew testDebugUnitTest --rerun-tasks                       # full JVM regression
grep -rn "data.repository\|data.aicloud" feature/assistant/src/  # empty (no feature→data edge)
grep -rn "com.sidr.launcher.feature\." feature/assistant/src/main/  # empty (no feature→feature edge)
grep -rn "SavedStateHandle" feature/assistant/src/main/         # empty (key/prompt deliberately not persisted)
grep -rniE "Log\.|println" feature/assistant/src/main/          # no key/prompt logging (manual review of any hit)
grep -rn "import android" domain/src/                          # empty (domain untouched)
git diff --stat -- domain/src/main/java/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt  # no change
```

## Acceptance criteria

- Typing in the assistant **streams** a real reply; **offline → static fallback**; **cancel** mid-stream
  aborts; **retry** re-streams without restart (on-device, or recorded pending).
- Refusal renders as a declined reply (not an error/retry); failures map to `Error(retryable)` with the
  correct retryability; **MissingCredentials/Unauthorized → a "set up provider" CTA**, not a dead retry.
- The provider-settings form saves base URL + free-text model (config) + masked key (Keystore); the key
  is **never** displayed back, logged, or placed in state/`SavedStateHandle`; non-`https://` is rejected.
- `:feature:assistant` depends only on `domain`/`core:*` (no feature→data/feature edge); VM holds no
  Android type; UI holds no business logic; launcher core + `HandleUserCommandUseCase` untouched.
- Docs synced (`architecture.md` AI-pipeline + secret-store-built; `roadmap.md` ESP→Keystore line);
  ADR `Block N` + Phase-5 close in `decisions.md`; `CLAUDE.md` = **Phase 5 complete (I → N)**;
  `phase-5-plan.md` Block N checked + phase closed. Full JVM regression green; `assembleDebug` green.

