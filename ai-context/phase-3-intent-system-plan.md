# Phase 3 (reordered): Intent System + Minimal Launcher Slice

> This document supersedes the previous linear `3.0`–`3.4` checklist. The work is
> regrouped into four ordered blocks (A → D) plus an explicit deferred list, based on
> the approved audit recommendations.

## Status & reordering note

- **P0** (docs / decisions) — done.
- **P1** (compile-ready skeleton) — done.
- **P2 (launcher shell) — intentionally skipped / reordered.** Its navigation half was
  absorbed into `3.1.x`. Its *product floor* — installed-apps repository, app grid,
  command input, offline app launch — was **not** built, yet it is a hard dependency of
  the Phase 3 intent system (you cannot resolve or launch `open telegram` without it).
  This plan folds a **minimal P2 slice** into Phase 3 (Block B) instead of running P2 as
  a separate phase. Full launcher-shell polish stays deferred.
- **P3 foundation `3.0.1`–`3.1.5`** — done (OperationResult/logging + navigation), with one
  known defect: `OperationResult` / `OperationError` live in `core/common`, creating a
  `domain -> core/common` edge. **Block A corrects this first.**

> "Done up to 3.1.5" = foundation + navigation only. The intent system proper has **not**
> started; it begins at Block C.

## Guiding rules (do not violate)

- `domain` depends only on Kotlin stdlib + coroutines. No `core/*`, no Android.
- Interfaces live in `domain`; implementations live in `data/*`. UI holds no business logic.
- No `feature -> feature` dependencies. Single `NavHost` in `app`; ViewModels emit
  `NavigationEvent` and never touch `NavHostController`.
- Repository / use-case operations return `OperationResult<T>`; never throw to UI.
- **Two distinct ports:** `IntentMatcher` (input → `IntentMatchResult`) is separate from the
  future `GenerativeAiEngine` (→ `Flow<AiChunk>`). Matching ≠ generation.
- Persistence (Room / DataStore) and intent-match-history are **frozen** for this slice
  (moved to Phase 4). Do not add them here.

## Execution order

Block **A** is the emergency prerequisite. Blocks **B** (Android-facing) and **C**
(pure domain) can largely proceed in parallel. Block **D** integrates B + C into the
demoable MVP loop. Do not start Block D before B and C land.

---

## Block A — Foundation correction (emergency, do first)

- [x] `A1` Move `OperationResult<T>` + `OperationError` from `core/common/result` to `domain`.
      Update all imports across modules.
- [x] `A2` Remove `implementation(project(":core:common"))` from `domain/build.gradle.kts`.
      Confirm `domain` depends only on stdlib + coroutines.
- [x] `A3` Update repository + use-case contracts touched by Phase 3 to return
      `OperationResult<T>` instead of throwing (was the open `3.0.2`).
- [x] `A4` Decouple logging from the moved types: either keep `ResultLogger` /
      `NoOpResultLogger` in `core/common` operating on **non-sensitive primitives**
      (category + retryable flag), or relocate the logging port to `domain`. Goal: the move
      introduces **no** new `core/common -> domain` edge.
- [x] `A5` Update existing test helpers (`TestFixtures`) and any `core/testing` fakes to the
      new `OperationResult` location.
- [x] `A6` Build guard: `./gradlew :domain:dependencies` shows only stdlib + coroutines; project compiles.

---

## Block B — Minimal P2 slice (product floor; unblocks app launch)

- [x] `B1` Create `:data:repository` module. Dependencies: `domain`, `core/common`,
      `core/android`. Register in `settings.gradle.kts`.
- [x] `B2` Define `InstalledApp` domain model + `InstalledAppsRepository` interface
      (`suspend fun getInstalledApps(): OperationResult<List<InstalledApp>>`) in `domain`.
- [x] `B3` Implement `InstalledAppsRepository` over `PackageManager` (launchable-apps query,
      fully offline) in `:data:repository`.
- [x] `B4` Add a fake `InstalledAppsRepository` for previews/tests. Bootstrap `core/testing`
      here if it does not exist yet.
- [x] `B5` `feature/launcher`: build the home shell — app grid rendered from
      `LauncherViewModel` via `StateFlow<UiState>`; offline app launch (through executor, see
      D2). No business logic in composables.
- [x] `B6` `feature/launcher`: add the text command-input field bound to `LauncherViewModel`
      (submits to a temporary stub handler until D3 lands).
- [x] `B7` DI: provide the `InstalledAppsRepository` binding in the `app` Hilt graph.

---

## Block C — Pure-domain intent core (no Android, no persistence; parallel to B)

- [ ] `C1` Review/refine intent/action/command contracts; avoid the Android `Intent` name
      collision (`LauncherIntent`, `ExecutableAction`, `IntentCandidate`).
- [ ] `C2` Define intent types: `LaunchAppIntent`, `SearchIntent`, `OpenSettingsIntent`,
      `SimpleCommandIntent`, `UnknownIntent`.
- [ ] `C3` Define action types: `LaunchAppAction`, `OpenSearchAction`,
      `OpenLauncherSettingsAction`, `ShowMessageAction`, `NoOpAction`.
- [ ] `C4` Define the `IntentMatcher` port + `IntentMatchResult` (normalized input, best
      candidate, confidence, alternatives, `source = RULE_BASED`, optional debug reason) in
      `domain`. Explicitly document that the future `GenerativeAiEngine` (`Flow<AiChunk>`) is a
      **separate** port — do not fold generation into the matcher contract.
- [ ] `C5` Implement command normalization (trim, collapse spaces, stable-locale lowercase,
      keep original input for display/diagnostics; no destructive transforms).
- [ ] `C6` Implement the rule-based matcher in `:data:repository`: `open/launch/start <app>`,
      `search/find/google <query>`, settings phrases, simple commands (`show apps`, `clear`,
      `help`). Prefer exact prefixes; weak/partial → lower confidence; empty/unsupported →
      `UnknownIntent`.
- [ ] `C7` Define `IntentConfidencePolicy` **behind an interface** (defaults
      `AUTO_EXECUTE = 0.85`, `SUGGEST = 0.50`) so thresholds can later be overridden per
      `DeviceProfile` / feature flag without changing the domain.
- [ ] `C8` Implement the action resolver (`LauncherIntent -> ExecutableAction`). Resolve
      `LaunchAppIntent` only when the app is resolved via `InstalledAppsRepository`; ambiguous
      names → suggestions, never auto-launch.
- [ ] `C9` Table-driven JVM unit tests (no Android): normalization, app-launch / search /
      settings / unknown matching, confidence thresholds, resolver behavior.

---

## Block D — MVP loop integration (the demoable slice)

- [ ] `D1` Define the `ActionExecutor` contract + `ActionExecutionResult` (success /
      failure-with-safe-message / needs-confirmation / unsupported / no-match) in `domain`.
- [ ] `D2` Implement the Android `ActionExecutor` in `:data:repository`: launch via
      `PackageManager` launch intents; catch `ActivityNotFoundException` / `SecurityException`;
      return structured failures. No sensitive permissions in this slice.
- [ ] `D3` Implement `HandleUserCommandUseCase` in `domain`: normalize → match → confidence
      gate → resolve → execute-when-safe → `OperationResult`. No exceptions to UI.
- [ ] `D4` Wire `feature/launcher` command input → `HandleUserCommandUseCase`; render results;
      clear input on success.
- [ ] `D5` Safe fallback behavior: empty (hint), unknown (show examples), low-confidence (ask
      for specificity), ambiguous (app suggestions), execution failure (concise message). No
      silent failures, no unsafe auto-execution.
- [ ] `D6` DI wiring: `IntentMatcher`, normalizer, `IntentConfidencePolicy`, action resolver,
      `ActionExecutor`, `HandleUserCommandUseCase`, `InstalledAppsRepository`.
- [ ] `D7` Verify: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`. The launcher
      shell must not regress.

---

## Deferred / frozen (explicitly NOT in this slice)

- Full DataStore preferences + Room database setup (old `3.2.1`–`3.2.6`) → **Phase 4**.
- Persist intent match history (old `3.4.15`) → **Phase 4** (the matcher works without it).
- Permission education module + request flows (old `3.3.1`–`3.3.6`) → after the MVP loop;
  the inline placeholder stays. Permission-related execution failures route to that
  placeholder for now.
- Extraction of `core/navigation` → on trigger (see the target-structure note in
  `docs/architecture.md`).
- `build-logic` convention plugins → create on first need.
- Cloud AI (Phase 5), ONNX NLU (Phase 6), voice (Phase 7), accessibility (Phase 8).

## MVP acceptance criteria

- Typing `open telegram` resolves an installed app and launches it, fully offline.
- Low-confidence input does not auto-execute; unknown input shows safe fallback UI.
- App grid renders installed apps via repository behind `StateFlow<UiState>`.
- All Phase 3 repository / use-case errors flow through `OperationResult` (now owned by `domain`).
- `domain` depends only on stdlib + coroutines (Block A verified).
- `./gradlew assembleDebug` and the focused unit tests pass.

## Tracking

- The previous linear checklist (`3.0`–`3.4`) and "Implementation Order" are superseded by
  Blocks A–D above.
- Record the P2 reorder decision and the `OperationResult` relocation in
  `ai-context/decisions.md`.
- Per-block acceptance: Block C is unit-testable on the JVM and should be green before
  Block D starts.

## Reference details (carried over)

### Intent types
- `LaunchAppIntent` — package if known, else display-name query.
- `SearchIntent` — query text, search target (web/app/local).
- `OpenSettingsIntent` — optional destination.
- `SimpleCommandIntent` — clear input, show apps, open assistant, etc.
- `UnknownIntent` — original input, optional reason.

### Action types
- `LaunchAppAction` — package (+ optional activity later).
- `OpenSearchAction` — query, target.
- `OpenLauncherSettingsAction` — optional destination.
- `ShowMessageAction` — user-facing message.
- `NoOpAction` — safe fallback for unsupported/low-confidence.

### Confidence levels
- High `>= 0.85` — resolve and execute safe actions.
- Medium `0.50..0.84` — show confirmation/suggestion.
- Low `< 0.50` — never execute; show fallback or ask to clarify.

### Normalization examples
```text
"  Open   Telegram " -> "open telegram"
"launch settings"     -> "launch settings"
```

### Example test cases
```text
"open telegram"          -> LaunchAppIntent("telegram"), high confidence
"search weather tomorrow" -> SearchIntent("weather tomorrow")
"settings"               -> OpenSettingsIntent
""                       -> UnknownIntent / NoOpAction
"open"                   -> low confidence / fallback
```

## Notes for future phases

- Phase 4: DataStore + Room persistence, intent match history, full permission education.
- Phase 5: cloud AI fallback behind the same matcher/result pipeline (`GenerativeAiEngine`).
- Phase 6: ONNX intent classifier as another `IntentMatcher` source.
- The local rule matcher remains the fastest, safest first-pass matcher.
- Accessibility automation stays out — optional, later phase, explicit consent.
