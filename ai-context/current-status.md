# Current Status

> **Authoritative status lives in `CLAUDE.md` (session digest), `ai-context/decisions.md` (ADR log),
> and the per-phase plans.** This file is a short pointer/snapshot only — if it disagrees with those,
> they win. Last re-based: 2026-07-02 (Device Acceptance Round 3).

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
- **Phase UX — home redesign & design system:** ✅ CODE-CLOSED 2026-07-03 (Blocks X1 → X6; owner
  decision 2026-07-02, U1/U5 decided). Minimal home + App Drawer (grid leaves home), discoverable
  Settings/Assistant, `core/ui` design system, real `:feature:settings`, deferred settings + a11y +
  first-run nudge + assistant prefill. Plan: `ai-context/phase-ux-plan.md`.
  **✅ DEVICE PASS DONE 2026-07-04 (SM-A325F / Android 13).** Full acceptance matrix PASS (X2 home
  discoverability + typed commands `settings`/`open plus`; X3–X4 drawer alphabetical/sticky/live-filter/
  clear/enter-launch; X5–X6 settings — theme applies-immediately+persists, AI-suggestions gate, voice
  toggle→mic hide, AI provider form; **persistence** across `force-stop` for theme/voice/AI/favorites-count/
  setup-hint incl. datastore keys; **X6-C** ask-assistant prefill visible + not-auto-sent + no saved-state;
  **X6-D** first-run nudge shown/dismiss/no-reshow; a11y content-desc + 48dp). **One real bug found + fixed
  on-run:** "Set as default launcher" (Settings **and** nudge) launched the ROLE_HOME intent with plain
  `startActivity` → null caller → system `RequestRoleActivity` aborted, **no chooser** — fixed via
  `rememberLauncherForActivityResult(StartActivityForResult())` (helper → pure `defaultLauncherIntent`),
  rebuilt+reinstalled+retested (role dialog now shows); touched-module tests green. **Bonus (owner entered
  a real provider):** Assistant **real streaming PASS** (openrouter/`gpt-4o-mini`) + Block-J BYOK Keystore
  **PASS** (key encrypted in `sidr_secrets`, decrypted+used) → retires those two carried device-debt items.
  **Still PENDING/PERF-RISK:** cold-start `<400ms` (measured min 1956 / median ~2021 / max 2066 ms → Phase 9).
  **Follow-up fix (owner-approved, same run):** added a **"Personalize from usage"** opt-in `Switch` to
  Settings (writes `FeatureFlags.usageHistoryEnabled`, off by default) — device-verified that the Favorites
  row now populates, the count selector visibly changes it (4→5), and usage-based suggestions surface real
  installed apps. **Finding still open (deferred to Phase 9):** `TimeOfDaySuggestionProvider` hardcoded AOSP
  package IDs unfiltered vs installed apps (Clock/Music tap → "Couldn't open that app" on Samsung).
  ADR: decisions.md "2026-07-04 — Phase UX device acceptance (SM-A325F) + set-as-default bug fix".
  - **Block X1 (design-system foundation) ✅ 2026-07-03** — `core/ui` filled: `SidrTheme` (neutral M3
    + dynamic colour on API 31+), typography/shapes/spacing tokens, and components `SidrScaffold`,
    `SidrSearchField` (unified search/command + mic), `AppTile` (icon slot — no domain/data edge),
    `SectionHeader`, `TopBarIcon`, `EmptyState`, `ErrorState`. `LauncherActivity` now themes via
    `SidrTheme`. 0 new Gradle deps (icons = core set + one bundled mic vector); presentation-only, no
    behavior change; `assembleDebug` + `testDebugUnitTest` green. ADR: decisions.md "2026-07-03 —
    Phase UX Block X1 complete".
  - **Block X2 (home declutter) ✅ 2026-07-03** — `LauncherScreen` rebuilt on `SidrScaffold`:
    top-bar `TopBarIcon` Settings + Assistant (bundled `ic_assistant_24` via a new `Painter`
    overload) → `navigateTo`; `SidrSearchField` replaced the private `CommandInputBar` (`onMicTap`
    verbatim); Suggestions unchanged; new **Favorites** row (`SectionHeader` + `LazyRow`/`AppTile`,
    top-N most-used); **All apps** button → new `Routes.AppDrawer.ROUTE` (X3 registers it; interim
    safe-fallback to home). **`AppGrid` removed from home** (`apps` still loads for drawer + suggestion
    resolution). `LauncherUiState.favorites` derived in the existing `combine` (`deriveFavorites`, cap
    8, usage-order ∩ installed) — no new VM dep. 0 new Gradle deps; `core/ui` still `core/common`-only;
    typed commands byte-for-byte; offline intact. 7 new JVM tests; `assembleDebug` + `testDebugUnitTest`
    green. Device pass (SM-A325F) pending. ADR: decisions.md "2026-07-03 — Phase UX Block X2 complete".
  - **Block X3 (App Drawer) ✅ 2026-07-03** — `AppNavHost` registers `composable(Routes.AppDrawer.ROUTE)`
    (retires X2's interim safe-fallback). New drawer surface **inside `feature/launcher`** (no new
    module, no `feature→feature` edge): `AppDrawerViewModel` (`@HiltViewModel`; no
    `HandleUserCommandUseCase`/`IntentMatcher`) → `UiState<AppDrawerUiState>` from the **pure**
    `groupIntoSections` (label-sorted, lettered buckets + trailing `#`); `AppDrawerScreen` = `SidrScaffold`
    + Back `TopBarIcon` + `LazyColumn` with `stickyHeader` `SectionHeader`s + compact icon+label rows,
    `Empty`/`Error(retry)` via `core/ui`. Icon helpers lifted to `internal AppIcon.kt` (shared home +
    drawer). launch/usage = **verbatim copy** of `LauncherViewModel`'s (owner-confirmed; no shared
    use-case, `:domain` untouched). Fast-scroll = sticky headers; A–Z side rail deferred (non-blocking).
    15 new JVM tests; `assembleDebug` + `testDebugUnitTest` green; 0 new deps. Device pass (SM-A325F)
    pending. ADR: decisions.md "2026-07-03 — Phase UX Block X3 complete".
  - **Block X4 (search ⇄ command unification) ✅ 2026-07-03** — the App Drawer now filters live (fork
    X4-A = filter in drawer; home stays command-first, no regression). Pure `filterApps(apps, query)`
    (case-insensitive substring, blank → full list) in `AppDrawerUiState.kt`; `AppDrawerViewModel` gains
    `_query`/`onQueryChanged` + exposed `query`, `uiState = combine(_rawAppsResult, _query)` → filter →
    `groupIntoSections`; IME submit → `onQuerySubmitted()` launches the top (alphabetical) match via the
    existing launch path (drawer stays command-free — no `HandleUserCommandUseCase`). `AppDrawerScreen`
    adds `SidrSearchField` under the top bar (`showMic=false`, built-in Clear); Empty message keyed off
    the query ("No apps found." vs "Nothing found."). **"Ask assistant" affordance deferred to X6** (a
    prompt-prefill nav-arg needs deliberate design; the "key never in saved state" invariant is
    untouched). `HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer` unchanged; `core/ui`
    untouched; 0 new deps. 10 new JVM tests; `assembleDebug` + `testDebugUnitTest` green. Device pass
    (SM-A325F) pending. ADR: decisions.md "2026-07-03 — Phase UX Block X4 complete".
  - **Block X5 (real Settings surface) ✅ 2026-07-03** — the stub `com.sidr.launcher.settings.*` in `:app`
    is replaced by a real, icon-reachable **`:feature:settings`** module (Compose + Hilt kapt, 0 new deps)
    holding `SettingsScreen`/`SettingsViewModel`/`SettingsUiState`. **MVP slice (fork X5-B):** theme
    (system/light/dark), AI suggestions (existing flag), Assistant provider entry (nav only), Set-as-default
    — voice on/off + favorites-count **deferred to X6** (they'd need new pref keys; deferring keeps
    `PrivacyInventoryGuardTest` untouched/green). **X5-A:** new `SuggestionScheduling` `:domain` port +
    `SuggestionSchedulingImpl` in `:app` over `SuggestionsWorkScheduler` (Hilt-bound) → the toggle re-syncs
    WorkManager with no `feature→:app` edge, gate-before-enqueue unchanged. **X5-C:** default-launcher
    intent from the screen via `LocalContext` (RoleManager `ROLE_HOME` API 29+, else `ACTION_HOME_SETTINGS`).
    **X5-D:** `LauncherActivity` observes `UserPreferencesRepository` → `themeName` → `SidrTheme(darkTheme=…)`,
    `dynamicColor` stays on. Assistant key invariant untouched (nav-only). 6 new JVM tests; `assembleDebug`
    + `testDebugUnitTest` green. Device pass (SM-A325F) pending. ADR: decisions.md "2026-07-03 — Phase UX
    Block X5 complete".
  - **Block X6 (polish, a11y, first-run, deferred settings) ✅ 2026-07-03 — Phase UX CLOSED.** All five
    X6 forks landed on the recommended option. Three deferred prefs live in `UserPreferences`
    (`favoritesCount=8` / `micInputEnabled=true` / `setupHintDismissed=false`; keys `user_favorites_count`
    / `user_mic_input_enabled` / `user_setup_hint_dismissed` — all denylist-clean, `PrivacyInventoryGuardTest`
    green). `:feature:settings` gained a HOME section (favorites `FilterChip` 4/6/8/10 + voice `Switch`) +
    `setFavoritesCount`/`setMicInputEnabled`. `LauncherViewModel` injects `UserPreferencesRepository`,
    exposes `showMic: StateFlow` (`micInputEnabled && recognizer available`), `deriveFavorites` reads the
    pref (`const FAVORITES_COUNT` removed), `startVoiceInput` no-ops when the mic pref is off, and
    `dismissSetupHint()` persists the one-shot nudge. `LauncherScreen` renders the dismissible first-run
    `SetupNudge` (`!isDefaultLauncher && !setupHintDismissed`; CTA → system launcher chooser) and routes
    empty/error through `core/ui` `EmptyState`/`ErrorState`. **X6-C** "Ask assistant" prefill: optional
    `Routes.Assistant.prompt` nav-arg + drawer affordance, seeded into the assistant input once — never
    auto-sent, **never in `SavedStateHandle`**. Cold-start = re-measure only (no startup-path code). New
    JVM tests across settings/launcher/persistence; all touched-module test tasks + `assembleDebug` green;
    0 new Gradle deps. Device pass (SM-A325F) batched-pending. ADR: decisions.md "2026-07-03 — Phase UX
    Block X6 complete".
- **MVP sequencing (owner 2026-07-02):** numeric 8→9 is NOT the ship order → **Phase UX → Phase 9
  (hardening, pre-ship gate) → Phase 8 (optional, deferred)**.
  - **Phase 9 — hardening:** ⛔ not started; pre-ship gate after Phase UX. Absorbs the residual
    cold-start perf-fix (if Block X6's re-measure still misses `< 400ms`), release build (R8/ProGuard),
    multi-version validation, LOW_END profiling.
  - **Phase 8 — accessibility automation:** ⛔ OPTIONAL / DEFERRED (post-MVP); not a ship blocker.

## Open questions gating the residual track

- **OQ#1 / OQ#2** — real NLU model (`intent.onnx`, pruned multilingual `vocab.txt`) + its host and
  pinned SHA-256. Until closed, `ModelDownloadConfig.INTENT_NLU_PENDING` is an inert seam.
- **OQ#3** — embedding model + tokenizer + host/hash (gates Block V). `EMBEDDING_PENDING` inert seam.
- **OQ#4** — on-device STT availability across the target device matrix (gates Block T acceptance).

## Device acceptance (SM-A325F / Android 13) — see ADR 2026-07-02 (Rounds 2 + 3)

**Round 3 (2026-07-02, BYOK key in-app) retired the last big AI blocker: assistant real streaming is
now PASS end-to-end.** Residual debt is perf (cold-start fix), voice intermediate states, boot-warmup,
and the model track. See [`device-acceptance-brief.md`](device-acceptance-brief.md).

**Verified on device (Round 3):**
- ✅ **C.1 Assistant real streaming — PASS end-to-end** (was PENDING-CONFIG). Live provider
  (`openrouter.ai` / `openai/gpt-4o-mini`): no-config form → `saveProvider` persists config
  (`sidr_preferences`) + key (encrypted `sidr_secrets`, absent from prefs) → **tokens streamed**
  (key decrypts & is used; first model's `429` was external throttle) → **offline static fallback**
  ("I can't reach an AI service right now…") → **cancel / retry / rotation** all PASS.
- ✅ **Part E Trim BACKGROUND/COMPLETE — PASS (no-crash).** Process alive; renderer
  `destroyRenderingContext`; **`OnnxIntentClassifier: ONNX session released (trim)` ×2** (both Block-V
  `SessionLifecycle` seams fired). Native ONNX teardown still unprovable (no model) — release wiring +
  survival proven.
- ✅ **C.4 Calendar/Location opt-in + privacy — PASS.** Denied → suggestions still from time/usage.
  Granted → cache held only generic `"Nearby places"` → maps (no coordinate), `Clock`, `Music`; no
  calendar-generic (no event → empty). **No raw event title / coordinate in cache or logcat.**

**Verified on device (Round 1 + Round 2):**
- ✅ Block-J `SecretStoreInstrumentedTest` — real Keystore round-trip, `OK (3 tests)`.
- ✅ APK install presence; launcher launch-smoke; flag-off home (no suggestions row / no precompute).
- ✅ **[Round 2] The three ADR-262 UI blockers are now re-verified PASS on device:** `settings` →
  `LauncherSettingsScreen` with a sanctioned `aiSuggestionsEnabled` toggle; toggle **ON** renders the
  suggestions row, **OFF** clears it; suggestion tap routing works (the `Настройки` chip launched the
  system Settings app); mic affordance is visible and no-permission mic tap routes to education.
- ✅ **[Round 2] WorkManager ON/OFF gate** — device-verified via the app's real WorkManager DB
  (`sidr_usage_cleanup` + `sidr_suggestion_precompute` at ON; `sidr_suggestion_precompute` → `state=5`
  at OFF) plus `dumpsys jobscheduler` (`2` Sidr jobs ON → `1` OFF; both constrained while
  `Battery not low: false`).
- ✅ **[Round 2] Voice grant + submit path** — in-app enable ended with `RECORD_AUDIO granted=true`;
  one post-grant run produced a final transcript that went through the unchanged
  `Final → onCommandSubmitted` command path (unknown-command feedback surfaced).

**Still open after Round 3:**
- ⚠️ **Cold start — measured, fix owed.** Rigorous `am start -S -W` ×6 cold: min 1711 / median ~1740
  / max 2172 ms (hot 0/16) vs the `< 400ms` budget (`docs/architecture.md:47`) → `PENDING / PERF-RISK`.
  Root-cause hypothesis: `PackageManager` app enumeration on the first-frame path + Hilt graph + Room
  open + first DataStore read + fire-and-forget `ensureModel()`. Needs a profiled attribution
  (`--start-profiler` / Perfetto) then deferral of first-frame work (**separate fix task**).
- 🔧 **Two C.1 findings (recorded, not fixed):** (1) `keySet` indicator race — `saveProvider` recomputes
  keySet before `secretStore.put`, so the "key set" tick stays false though the key is saved & usable
  (cosmetic); (2) RateLimited string typo `retray` → `retry`.
- ⚠️ **Voice recognizer intermediate states** — `Ready` / `Partial` not evidenced (OQ#4); C.2 not a full
  recognizer PASS.
- ⏭️ **C.5 boot warmup** — `RECEIVE_BOOT_COMPLETED` re-enqueue after a reboot not yet exercised.
- ⏭️ `OnnxIntentClassifierInstrumentedTest` assumption-skipped (no bundled model) — no `intent.onnx` /
  `vocab.txt` in repo or app sandbox → **PENDING-MODEL**; Block-P P5 `< 150ms` inference + Block-Q real
  provisioning still model-blocked (OQ#1/#2).
- ⏭️ Block-V embedder `< 150ms` + memory co-residency — pending (OQ#3).

## Not claimed done

The cold-start performance **budget** (`< 400ms`) — measured at ~1740ms median, root-caused, fix
owed; OQ#1–OQ#4 real models/vocab/embedder + on-device NLU acceptance; voice `Ready`/`Partial`
intermediate states (OQ#4); boot-warmup after reboot (C.5). *(Real assistant streaming against a live
provider — done Round 3.)*

## Source of truth

- Session digest + hard rules: `CLAUDE.md`
- Decisions log (latest: ADR 2026-07-02 — Device Acceptance Round 2): `ai-context/decisions.md`
- Architecture (in sync): `docs/architecture.md` · Roadmap: `docs/roadmap.md`
- Active/last plan: `ai-context/phase-7-voice-suggestions-plan.md`
