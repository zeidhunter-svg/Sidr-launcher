# Next-agent brief — Manual device-acceptance pass + acceptance-matrix honesty

> **Scope owner:** the next agent. **Type of work:** manual/observational device acceptance +
> documentation (the acceptance matrix). **NOT** a coding/runtime/model track.
> **Device:** SM-A325F, Android 13 (SDK 33), unlocked/interactive. Build/run of the app is fine;
> writing new production runtime is not (see boundaries).
> **Basis:** ADR 2026-07-01 device pass (`decisions.md:2509`) + ADR 2026-07-01 acceptance-blocker
> follow-up (`decisions.md:262`). Read both before starting.

---

## Why this pass exists (the one non-obvious fact)

The last device pass (`decisions.md:2509`) hit three UI blockers: **no route into the assistant from
launcher home, no mic affordance, no way to enable `aiSuggestionsEnabled`.** Those three were
**code-fixed the same day** in `decisions.md:262` (assistant rule entry restored, mic probe hardened
+ `<queries>`, a real `LauncherSettingsScreen` toggle) — **but the device pass predates the fixes and
nobody re-verified them on hardware.** So this pass is mostly: *re-run acceptance against the
just-fixed code and record what is now actually observable.*

---

## Execution model — agent-directed, human-hands

**The agent runs this pass; a human operator is the hands on the real device.** The agent CANNOT
touch the device directly and CANNOT observe interactive UI from a shell. So for every check the
agent must relay a concrete instruction and then adjudicate from what the operator reports back.

Two kinds of checks — the agent must know which it is issuing:

- **[SHELL]** — the agent gives an **exact copy-paste `adb` command** (see the Command Appendix) and
  the operator pastes the output back; the agent judges PASS/PARTIAL/FAIL from the output. Covers:
  install/launch timing (D), trim (E), `dumpsys jobscheduler` (C.5), recognizer-component probe,
  permission state, package presence.
- **[UI-EYES]** — the agent gives a **precise tap/type/rotate instruction + the exact thing to look
  for**, and the operator reports what they saw ("mic visible: yes", "tokens streamed: yes", "key
  shown back: no"). Covers most of Part C (assistant streaming, mic affordance, suggestions row,
  calendar/location UX) and the visible half of Part B.

**Protocol per check:** the agent issues one instruction at a time in the form
`(1) run/tap THIS → (2) expected: THAT → (3) paste output / report what you saw`, waits for the
operator, records a verdict, then moves on. Do not batch many checks blind. Never ask the operator to
paste secrets (the API key) back — key entry is UI-side and its value is never echoed.

## Hard boundaries (do NOT cross)

- **Do NOT write new production runtime** (no new engines, matchers, providers, workers, DI graphs).
  Fixing a genuine acceptance-blocking bug found on device is allowed **only** if it is small and
  flagged as such in the report; otherwise record it as a finding, don't build.
- **Do NOT start OQ#1/OQ#2/OQ#3** — no NLU model, no `vocab.txt`, no hosting/SHA-256, no embedder.
  Those are a separate later track and are explicitly out of scope here.
- **Do NOT start Phase 8** (accessibility).
- **Do NOT re-architect** the acceptance matrix or docs beyond honest status edits.

---

## Preconditions (verify, don't assume)

1. `git status` clean-ish / on the intended branch (`feature/launcher-3` at time of writing).
2. Device connected: `adb devices -l` shows `model:SM_A325F`.
3. App installs and launches (`installDebug`, then `am start`). Launch-smoke is a PASS already.
4. **BYOK provider key — AVAILABLE (owner has one).** So the assistant streaming path (C.1) is
   unblocked and is **in scope for this pass**, not deferred. Enter the key **in-app on the device**
   (Assistant provider form → Keystore); it must never be committed, logged, or pasted into any file
   or the chat. Also needs base URL (https-only) + model string. See Part C.1.

---

## Part A — Fix the acceptance matrix (honest statuses first)

Set these statuses in the tracking doc so the baseline is honest before observing:

- **Block J (Keystore):** `PASS` — `SecretStoreInstrumentedTest` `OK (3 tests)` on real Keystore
  (`decisions.md:2527`). Keep as PASS.
- **Cold-start perf:** `PENDING / PERF-RISK` — `am start -W` showed `1381ms` then `1026ms` cold
  (hot `248ms`) vs the `<400ms` budget (`architecture.md:47`). **Not a rigorous benchmark yet** — two
  `am start` calls ≠ a cold-start measurement. Needs a proper measure **and** a root-cause (Part D).
- **Trim release:** `PARTIAL / PENDING` — `RUNNING_CRITICAL` + a backgrounded run left the process
  alive with `OpenGLRenderer …destroyRenderingContext`, no fatal. But **no-model state**, so native
  ONNX session release is unproven. `BACKGROUND`/`COMPLETE` re-run pending (Part E).

The authoritative matrix update lands **after** Parts B–E produce evidence, not before.

---

## Part B — Re-verify the ADR-262 fixes (the checklist that was code-fixed but never device-checked)

Each item was broken during the last pass and fixed in `decisions.md:262`. Confirm on device:

- [ ] **Assistant reachable from launcher home.** Type `assistant` (or `show assistant`) in the
      command field → routes to `Routes.Assistant`. (Source now has this: `RuleBasedIntentMatcher`
      `SIMPLE_COMMANDS` includes `assistant` / `show assistant` → `OPEN_ASSISTANT`.) During the last
      pass this route did not exist — confirm it now does.
- [ ] **Mic affordance is visible** on the command-input row. Last pass: recognizer components
      resolved on device but the mic did not render. The probe was hardened
      (`AndroidSpeechInputSource.isAvailable()` now accepts a resolvable `RecognitionService` /
      `ACTION_RECOGNIZE_SPEECH` handler + manifest `<queries>`). Confirm the mic now shows.
- [ ] **Settings toggle exists and works.** There is now an in-app `LauncherSettingsScreen` with a
      sanctioned `aiSuggestionsEnabled` toggle (route to `Routes.Settings`). Toggling ON must
      immediately re-sync `SuggestionsWorkScheduler` and make `LauncherViewModel` restore/refresh
      suggestions; OFF must clear them. Confirm — this unblocks Part C.3/C.4.

If any of these three is still broken on device, it is a **blocker finding** (report it; it gates the
dependent acceptance below).

---

## Part C — Device UI acceptance

### C.1 Assistant (streaming) — **BYOK key REQUIRED**
- [ ] **No-config state:** fresh (no provider saved) → assistant shows the provider-setup form / CTA,
      not a crash.
- [ ] **saveProvider flow:** enter base URL (https-only validation) + key → saved; key goes to
      Keystore and is **never displayed back / never in logs**. Confirm re-opening the form shows
      `keySet = true` but no key value.
- [ ] **Real streaming:** with the BYOK key configured + online, send a prompt → tokens stream into
      the reply (`AiChunk.Text` deltas), terminal `Done`. This is the **only** actually-working AI path
      and has never been verified end-to-end — do not skip.
- [ ] **Offline static fallback:** disable network → send → `StaticFallbackEngine` canned reply
      (`"I can't reach an AI service right now…"`), never a crash/Failed.
- [ ] **cancel / retry / rotation-mid-stream:** back-nav aborts the stream; `retry()` reruns
      latest-wins (no double stream); rotate mid-stream → reply survives, key never enters saved state.
- [ ] **Refusal (if reproducible):** a refused generation shows `Done(refused=true)`, treated as
      success, not an error.

### C.2 Voice
- [ ] Mic tap when **not granted** → routes to `permission_education?feature=VOICE_INPUT` (education ≠
      request), then the system dialog.
- [ ] **Granted:** listening starts; partials stream into `commandInput`; **Final transcript submits
      through the unchanged command path** (`onCommandSubmitted`) — i.e. voice text behaves byte-for-byte
      like typed text.
- [ ] **PERMANENTLY_DENIED path:** deny twice → status becomes `PERMANENTLY_DENIED` → a "open system
      Settings" deep-link (not a dead re-request). On `ON_RESUME`, `refreshStatus()` reflects a genuine
      `GRANTED→DENIED` revocation.
- [ ] **Unavailable path (OQ#4):** if on-device recognizer / language pack is absent, the UX degrades
      gracefully (mic hidden or a clean "voice unavailable"), keyboard still works. Record the device's
      actual on-device-vs-system recognizer behavior.

### C.3 Suggestions (needs Part B toggle working)
- [ ] **Flag OFF (default):** plain grid, **no suggestions row** (already PASS last pass;
      `dumpsys jobscheduler` showed no precompute scheduled).
- [ ] **Flag ON:** suggestions row renders above the grid; offline providers (time-of-day + recent/
      frequent usage) produce entries with no permissions granted.
- [ ] **Cache-first paint:** on relaunch, the row first-paints from `SuggestionsCacheRepository`, then
      a fresh engine result supersedes it (not merged).
- [ ] **Suggestion tap** routes through the existing launch/nav path (`actionId` → launch/route).

### C.4 Calendar / Location (opt-in, privacy-critical)
- [ ] **Denied:** each provider returns empty → suggestions still produced from time/usage
      (degrade-not-block).
- [ ] **Granted:** a **single generic** suggestion appears ("Upcoming event" → calendar app;
      "Nearby places" → maps app). **Confirm no raw event title / no coordinates** anywhere in UI,
      cache, or logs — only the fixed generic label + `actionId`.

### C.5 WorkManager — test the NEGATIVE, not just the positive
- [ ] **Flag OFF → not scheduled:** `adb shell dumpsys jobscheduler com.sidr.launcher` shows no
      suggestion-precompute (gate-before-enqueue). Already evidenced last pass — re-confirm.
- [ ] **Flag ON → scheduled unique/idempotent:** toggling ON schedules exactly one periodic
      precompute; toggling OFF cancels it. Confirm no duplicate chains.
- [ ] **Boot warmup:** reboot device → `RECEIVE_BOOT_COMPLETED` re-enqueues precompute (best-effort).
- [ ] LOW_END gating is not testable on this MID device — note as N/A here.

---

## Part D — Cold-start: root-cause, not just re-measure

- [ ] **Proper measurement:** `adb shell am force-stop` → `am start -S -W …` across **N≥5** cold runs
      (optionally after clearing caches), report min/median/max — not a single `am start`.
- [ ] **Root-cause (cheap, this session):** identify what dominates `SidrLauncherApp.onCreate` /
      first frame — Hilt graph construction, WorkManager init, the fire-and-forget `ensureModel()`,
      DataStore reads, first Compose composition. Even a hot-start of `248ms` + cold `~1000ms+` points
      at heavy startup work. **Record the suspected cause; do not fix it here** (fix is a separate task).
- Deliverable: a measured cold-start distribution + a one-paragraph root-cause hypothesis.

---

## Part E — Repeat trim test, then STOP

- [ ] `am send-trim-memory com.sidr.launcher BACKGROUND` and `… COMPLETE` (backgrounded).
- [ ] Confirm `pidof com.sidr.launcher` alive (no-crash) + `logcat` release-path evidence.
- [ ] **Stop after one clean run.** Without a loaded model this only re-proves no-crash + renderer
      release — native ONNX-session release is **structurally unprovable until OQ#1/#2 lands**. Do not
      loop trying to prove it; update the matrix wording to "no-crash confirmed on BACKGROUND/COMPLETE".

---

## Part F — Finalize the matrix + report

- [ ] Update the acceptance matrix with the real observations from B–E (PASS / PARTIAL / PENDING /
      BLOCKER-finding per item).
- [ ] Write a short device-pass ADR addendum (append to `decisions.md`, mirror the `2509` format):
      what was re-verified after the ADR-262 fixes, cold-start distribution + root-cause hypothesis,
      trim BACKGROUND/COMPLETE result, any blocker findings.
- [ ] Sync `ai-context/current-status.md` device-acceptance section to match.

---

## The gate AFTER this pass (do not skip to models automatically)

Before anyone starts OQ#1/OQ#2 (Stage-2 NLU model pipeline), there is a **product decision** to make,
not an automatic next step:

> **Is on-device NLU a shipping feature, or a research bet?** The launcher ships today on rule +
> cloud + heuristic suggestions; the NLU (per Block R) only ever *Suggests* on low-confidence input,
> never auto-executes. If NLU is not a committed shipping feature, the `:data:ai-local` ONNX stack can
> stay an inert seam and the priority is the cold-start fix (Part D) + closing N5. Only if NLU is
> committed does Stage-2 (produce `intent.onnx` + pruned `vocab.txt`, dataset is already frozen in
> `tools/nlu/data/`) become the next track — and it runs as a **parallel** ML workstream, off the
> Kotlin critical path.

---

## Deliverables checklist

- [ ] Honest acceptance matrix (Part A statuses → updated with B–E evidence in Part F).
- [ ] Re-verification results for the three ADR-262 fixes (Part B).
- [ ] Device UI acceptance results incl. **real streaming with a BYOK key** (Part C).
- [ ] Cold-start distribution + root-cause hypothesis (Part D) — measured, not fixed.
- [ ] One clean trim BACKGROUND/COMPLETE result (Part E).
- [ ] ADR addendum + `current-status.md` sync (Part F).
- [ ] Explicit statement of the product gate above — do NOT start models without it.

---

## Command Appendix (copy-paste `adb` for the [SHELL] checks)

> The agent relays these verbatim to the operator; package = `com.sidr.launcher`, activity =
> `com.sidr.launcher/.LauncherActivity`. Re-verify the activity name once via the launch command.

**Baseline / presence**
```sh
adb devices -l
adb shell pm list packages | grep sidr
adb shell pm path com.sidr.launcher
```

**Cold-start (Part D — proper, not one shot)**
```sh
# run the pair 5+ times; read "TotalTime" each run; -S force-stops before start
adb shell am force-stop com.sidr.launcher
adb shell am start -S -W -n com.sidr.launcher/.LauncherActivity
```
Report each `TotalTime` (cold) + one hot relaunch (start again without force-stop). Budget `<400ms`.

**Trim (Part E — background the app first for BACKGROUND/COMPLETE)**
```sh
adb logcat -c                      # clear log
adb shell input keyboard event 3   # HOME (background the app)
adb shell am send-trim-memory com.sidr.launcher BACKGROUND
adb shell am send-trim-memory com.sidr.launcher COMPLETE
adb shell pidof com.sidr.launcher  # must print a PID = alive/no-crash
adb logcat -d | grep -iE "OpenGLRenderer|AndroidRuntime|OnnxIntentClassifier"
```
Expected: PID present; `trimMemory(...)::destroyRenderingContext`; no `AndroidRuntime` FATAL.

**WorkManager gate (Part C.5 — the negative test)**
```sh
adb shell dumpsys jobscheduler | grep -A6 com.sidr.launcher
```
Flag OFF → no suggestion-precompute job. Flag ON → exactly one periodic job; toggling OFF cancels it.

**Voice recognizer components present (Part B / C.2)**
```sh
adb shell cmd package query-services   -a android.speech.RecognitionService
adb shell cmd package query-activities -a android.speech.action.RECOGNIZE_SPEECH
```
Components resolving here + mic **still not visible** = the hardened-probe fix regressed (blocker).

**Permission state (Part C.2 / C.4)**
```sh
adb shell dumpsys package com.sidr.launcher | grep -iE "RECORD_AUDIO|READ_CALENDAR|ACCESS_FINE_LOCATION|granted=true|granted=false"
```

**Offline test toggle (Part C.1 static fallback)**
```sh
adb shell svc wifi disable && adb shell svc data disable    # go offline
adb shell svc wifi enable  && adb shell svc data enable     # restore after
```
(If `svc data` needs privilege, use airplane mode from the UI instead.)

**Assistant / streaming / mic / rotation (Part C.1–C.4) = [UI-EYES], not shell.** The agent relays
tap/type/rotate instructions; the operator reports what they saw. Rotation can be forced via
`adb shell settings put system accelerometer_rotation 0` + `... user_rotation 1|0`, but the *stream
survives rotation* observation is the operator's eyes.
