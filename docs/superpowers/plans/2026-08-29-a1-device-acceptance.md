# A1′ — device acceptance (federated `ToolRegistry`)

> **RUN 2026-09-10 — Parts A and B performed by the owner on the SM-A325F (Android 13, `ru-RU`) and
> signed off. Block A1′ is `CLOSED`.** The run itself produced three fixes — `set_timer` needed
> `com.android.alarm.permission.SET_ALARM` (2026-09-05, `939d1d7`); this checklist read the database
> without its WAL and so reported two "defects" that were one measurement fault (`43751f8`); and
> leaving the agent surface left the launcher in search mode (`f228d10`). The final pass ran on the
> fixed build `1ef158d`. Two owner rulings were taken: `set_timer` keeps `SAFE` on a corrected
> rationale, and the block closes with its residuals named and carried forward. **What this run did
> NOT cover:** `ru` only — `en` and `tr` never ran on the phone, and `"sayaç ayarla"` was **not judged
> by a native speaker** (none present; see §A1, which requires that this be said, not skipped). Part C
> cleared nothing, by construction. Full record: ADR «2026-09-03 — Этап 5 (A1′)» in
> `ai-context/decisions.md`, § «Приёмка на устройстве». This file is kept as the record of the
> protocol rather than deleted; the boxes below carry what was observed.
>
> **Why this file existed.** Block A1′ (Этап 5) was `CODE-GREEN`: gate green at 1304 tests (1298 at
> closing on 2026-09-03, +6 from the final-review fix `356fe1a`), every new
> guard mutation-proved, ADR written. It was **not** `DEVICE-ACCEPTED`. Unlike A0.5 (whose device
> acceptance was marked *not applicable*, because that block changed nothing on the phone), A1′ changes
> exactly what the user can reach: two new system intents — `set_timer` and `open_system_settings` —
> registered in the federation and made reachable from a typed command through routing step 2b. Spec
> `2026-08-29-a1-federated-tool-registry-design.md` §16 criterion 1 required an owner run on-device.
> This is the checklist that closed that gap.
>
> Structure follows [2026-08-23-a0-device-recheck.md](2026-08-23-a0-device-recheck.md) on purpose — the
> three-way split is half the file's value: **A** — what the owner must look at with their own eyes;
> **B** — no-regression re-runs on paths the block did not change; **C** — what only an agent can show
> instrumentally, which therefore clears **nothing**.
>
> **Owner-run means owner-run.** An `adb`/`uiautomator` pass driven by an agent is evidence, not
> acceptance (Этап 0.5's status vocabulary).
>
> Source of the changes: ADR «2026-09-03 — Этап 5 (A1′)» in `ai-context/decisions.md`. Full task-by-task
> ledger: `.superpowers/sdd/2026-08-29-a1-federated-tool-registry/progress.md`.

---

## Device state to establish first

Same device as every prior acceptance round, for the same reason — comparing against what is already
accepted:

- SM-A325F, Android 13, locale **`ru-RU`** (matches Task 15 and the 2026-08-23 re-check; `en`/`tr` ship
  in the same commit and are listed below for completeness, not as the primary run).
- Install the new debug build **over** the existing one — no uninstall. The database stays at
  `user_version = 4`; this block made **no schema change** (`schemas/…/4.json` diff is empty,
  `identityHash` unchanged), so no migration runs and none should.
- `com.sidr.launcher` is the package. Reading the agent tables after each run — **one command**:
  ```
  tools/device/pull-agent-db.sh
  ```
  It prints `agent_session`, `agent_plan_step`, `agent_trace_event` and the at-rest row counts.
  Pass an output directory as `$1` to keep the pulled files, and a query as `$2` to ask something else.

  **Do not read the database by copying `databases/sidr_history.db` on its own** — that is what the
  2026-09-10 run did, and it is why that run failed on a product that was working. Room opens this
  database in **WAL mode** (`PRAGMA journal_mode` → `wal`, measured on this device), so a committed
  write lives in `sidr_history.db-wal` and reaches the main `.db` file only at a *checkpoint*. The main
  file alone is therefore the database **as of the last checkpoint** — on 2026-09-10 that was three
  hours and four state transitions stale, and it reported a session the engine had correctly deleted as
  still present. It fails the other way too: it can show all three `agent_*` tables empty while a row
  holding `agent_session.goal_text` — the user's raw command — is on disk, which would be a **false
  green on the privacy guarantee**. Pull the pair, always:
  ```
  adb exec-out run-as com.sidr.launcher cat databases/sidr_history.db     > /tmp/sidr/sidr_history.db
  adb exec-out run-as com.sidr.launcher cat databases/sidr_history.db-wal > /tmp/sidr/sidr_history.db-wal
  $ANDROID_HOME/platform-tools/sqlite3 -header -column /tmp/sidr/sidr_history.db \
    "SELECT seq, type, step_index, detail FROM agent_trace_event ORDER BY seq;"
  ```
  The SDK's own `sqlite3` is the one to use: this device exposes **no** `sqlite3` binary through
  `run-as` (measured 2026-09-05: `run-as: exec failed for sqlite3: No such file or directory`).
  Re-pull after each run rather than re-querying an old copy. To read `user_version` without `sqlite3`
  at all, the value is four big-endian bytes at offset 60 of the main file.

  `DeviceDatabaseReadGuardTest` (`:app`) holds all of this mechanically, so the next checklist cannot
  quietly reintroduce the truncated read.
- **Both new tools are `SAFE`.** `requiresConsent(SAFE) == false`, so neither plan stops at
  `AwaitingConsent` — the step runs immediately and the session lands on the completed surface without
  a confirm tap. This is expected and is not a missed consent gate; see Part A for what the owner
  should see instead.

---

## Part A — the owner must look, because this block changes what the user can reach

### A1. A typed timer command runs a real Android timer

Type each of these into the command bar (not the system search — the same entry surface Task 15 used
for A0's «открой …» verification), one locale at a time if more than one is available on the device:

| Locale | Command | Expected duration |
|---|---|---|
| `ru` | «поставь таймер на 5 минут» | 5 minutes |
| `en` | "set a timer for 5 minutes" | 5 minutes |
| `tr` | "5 dakika zamanlayıcı ayarla" | 5 minutes |
| `tr` (owner-level judgment, see below) | "5 dakika sayaç ayarla" | 5 minutes |

- [x] **`ru`, 2026-09-10.** The system's own **clock/timer** screen opens (`AlarmClock.ACTION_SET_TIMER`,
      `EXTRA_SKIP_UI=false`). **Corrected 2026-09-10 — expect the timer to be RUNNING.** This item used
      to read "Sidr never sends the timer itself; the last act is the user's … the 'prefilled but not
      sent' shape Master Plan §3.6 `B4` describes". The owner's own run (SM-A325F, 2026-09-05)
      falsified that: the Samsung clock opened with the timer **already counting down**
      (`Пауза`/`Удалить`, no start button). `EXTRA_SKIP_UI` governs whether the responding app shows
      its UI, not whether it acts. A running timer is therefore the **expected** result here, not a
      defect — and `SAFE` stands on other grounds by owner decision 2026-09-10 (reversible in one tap,
      immediately visible, provenance disclosed, nothing leaves the device; spec §8.1).
- [x] **`ru`, 2026-09-10 — 5 minutes, timer already running.** The duration matches the command (5 minutes for the phrasing above), whether the clock shows it
      counting down or waiting to start — vendor clocks may differ, and only the duration is Sidr's.
- [x] **`ru`, 2026-09-10 — «Поставить таймер на 5 минут».** The agent surface (reachable from wherever A0's plan surface already showed) displays a
      **one-step, completed** plan whose step line reads (locale-dependent):
      - `ru`: «Поставить таймер на 5 минут»
      - `en`: "Set a timer for 5 minutes"
      - `tr`: "5 dakika için zamanlayıcı kur"
- [x] **`ru`, 2026-09-10 — present.** Directly beneath that step line, a second, smaller line reads **`SYSTEM INTENT · EXTERNAL`** —
      locked English in every locale (Class A, `launcher_tool_level_system_intent`; not translated on
      purpose, see A3 below). This is the provenance disclosure `DOC-ILM-2` is about: a `SAFE` tool
      that also leaves the device (`EXTERNAL`) has *nothing else* telling the user the effect crossed
      the app boundary, because the consent gate — the other place that would normally stop and
      disclose — never fires for a `SAFE` step.

**`"5 dakika sayaç ayarla"` is a genuine owner-level judgment call, not a control.** Both `"zamanlayıcı
ayarla"` and `"sayaç ayarla"` ship as `tr` suffix triggers for `set_timer`
(`ToolVocabulary.kt:178`) — the implementer's recommendation to drop `"sayaç ayarla"` (it reads as
counter/meter, not kitchen timer, to a native speaker's eye) was **not** acted on; it is an open
finding, not a shipped decision. `ToolVocabularyReachabilityTest` proves the phrase is reachable and
un-shadowed — that is a mechanical fact, not a judgment about whether it reads naturally. If whoever
runs this checklist reads Turkish, this is the moment to judge it and record the verdict here or in the
ADR; if not, name that explicitly rather than silently skipping the row — "not judged, no Turkish
speaker available" is honest, "assumed fine" is not.

**Verdict 2026-09-10: NOT JUDGED — no Turkish speaker was available, and the `tr` rows were not run on
the device at all.** That is the honest answer this paragraph demands; "assumed fine" was not
available. The trigger ships unchanged; the question stays owner-level and is addressed to A1″.

### A2. A typed command opens real Android settings

| Locale | Command |
|---|---|
| `ru` | «системные настройки» |
| `en` | "system settings" |
| `tr` | "sistem ayarları" |

- [x] **`ru`, 2026-09-10.** The real Android **Settings** app opens (`Settings.ACTION_SETTINGS`) — not Sidr's own settings
      screen.
- [x] **`ru`, 2026-09-10 — «Открыть настройки Android».** The agent surface shows a completed one-step plan with the step line:
      `ru` «Открыть настройки Android» / `en` "Open Android settings" / `tr` "Android ayarlarını aç".
- [x] **`ru`, 2026-09-10 — present.** The same `SYSTEM INTENT · EXTERNAL` provenance line appears beneath it.
- [x] **`ru`, 2026-09-10 — behaved as designed: FastPath claimed it and A0's two-step plan offered the store.** **Negative check** — type «открой настройки системы» (`ru`) or "open system settings" (`en`).
      Both are FastPath launch commands (`LAUNCH_VERBS` claims `"открой"`/`"open"` first), **not** the
      tool trigger — expect an app-launch attempt (probably "no such app"), not the Settings screen.
      This is intentional (`ToolVocabulary.kt`'s own KDoc names it) and is what
      `ToolVocabularyReachabilityTest` holds mechanically; it is here so the owner sees it once rather
      than reads it as a bug during A1.

### A3. The A0 surface now carries the same provenance line — read it once here, verified in full there

Both `set_timer`/`open_system_settings` (A1/A2 above) and A0's own two tools (`launch_app`,
`play_store_search`) are `EXTERNAL` — the provenance line is not new to Tier-0, it is new to **every**
registered tool, A0's included. The full before/after comparison for A0's «открой <неустановленное
приложение>» surface — the two **added** `SIDR · EXTERNAL` lines under a plan the owner already accepted
on 2026-08-22 — is tracked as its own item in
[2026-08-23-a0-device-recheck.md](2026-08-23-a0-device-recheck.md) (§ "A3. New provenance lines on the
already-accepted A0 plan"), not duplicated here. Read it there; this file's job is only the two **new**
tools.

### A4. Read the six new strings

`ru` is what ships on this device; `en`/`tr` ship in the same commit and are listed for completeness.
Three are Class B (translated, read them for sense); three are Class A (`translatable="false"`, locked
English by design — read them for correctness of the *English*, not for translation, since none of
Ruling R18's provenance tokens is meant to be translated at all).

| key | class | ru | en | tr |
|---|---|---|---|---|
| `launcher_agent_step_timer` | B | Поставить таймер на %1$s | Set a timer for %1$s | %1$s için zamanlayıcı kur |
| `launcher_agent_step_settings` | B | Открыть настройки Android | Open Android settings | Android ayarlarını aç |
| `launcher_agent_step_generic` | B | Выполнить шаг для %1$s | Run this step for %1$s | %1$s için bu adımı çalıştır |
| `launcher_tool_level_in_app` | A (locked, all locales) | SIDR · EXTERNAL | SIDR · EXTERNAL | SIDR · EXTERNAL |
| `launcher_tool_level_system_intent` | A (locked, all locales) | SYSTEM INTENT · EXTERNAL | SYSTEM INTENT · EXTERNAL | SYSTEM INTENT · EXTERNAL |
| `launcher_tool_level_unknown` | A (locked, all locales) | ANOTHER APP · EXTERNAL | ANOTHER APP · EXTERNAL | ANOTHER APP · EXTERNAL |

- [x] **2026-09-10 — read and accepted as-is, no amendments.** Read and accepted, or amended. None of the three Class B strings is Class B *locked-and-signed*
      vocabulary (calculation names, Shahada) — an amendment here is a normal string edit, one per
      locale, not a re-signature of the locale digest.
- [x] **2026-09-10 — read; nothing to demonstrate on device, by construction.** `launcher_tool_level_unknown` ("ANOTHER APP · EXTERNAL") is the fail-closed label for a
      `ToolLevel` this surface has no specific sentence for — it is reachable by construction (`ToolLevel`
      is an open value class) but **not** reachable by either tool shipped in this block; it has no
      on-device trigger to demonstrate today. Named here so its absence from A1/A2 above is not mistaken
      for an oversight.

---

## Part B — the owner re-runs to confirm nothing regressed

These paths are **unchanged in behaviour** by this block; they are here because the block touched the
engine and the surface they run through, and "unchanged" is worth one minute of device time each.

- [x] **2026-09-10 — ran to completion through the store step (`market://search`).** **A0's two-step plan still runs.** «открой <не установленное>» still produces launch → store,
      still stops at the risk transition before the store step, still completes the same way it did on
      2026-08-22/23 — **except** for the two added provenance lines, which is A3/§A5 above, not a
      regression.
- [x] **2026-09-10 — `0/0/0` at rest, read with the script. This is the item the first pass failed, and it failed on the read, not on the engine.** **Cancelling mid-plan** still runs nothing further, and the three `agent_*` tables are empty
      afterwards (cascade delete, unaffected by federation). **Read them with
      `tools/device/pull-agent-db.sh`, not by copying the `.db`** — this is the exact item the
      2026-09-10 run failed, and it failed on the read: the engine had deleted the session, the
      truncated copy still showed it. Same for the item below; a `force-stop` never checkpoints, so a
      main-file-only copy is guaranteed to still be carrying whatever it was carrying before.
- [x] **2026-09-10 — no stuck session, no orphaned row, launcher healthy; same read caveat as above.** **`adb shell am force-stop com.sidr.launcher`** at any point during either new tool's (near-instant)
      execution, then relaunch — no stuck `AwaitingConsent` or orphaned row; a `SAFE` step either
      completed before the kill or the session offers to resume, matching A0's existing resume shape.
- [x] **2026-09-10 — unchanged.** **The FastPath verbs A1′ did not touch** still work as before: an installed app still opens on
      «открой <установленное>» without ever reaching the agent surface at all.
- [x] **2026-09-10 — streams normally.** **Assistant / BYOK chat** (unrelated surface, unrelated code) still streams normally — confirms the
      federation change has no reach outside the agent surface.

---

## Part C — agent-verifiable evidence, which does **not** clear anything

Recorded so a later session does not mistake "hard to check on device" for "unchecked". None of these
counts as acceptance.

### C1. The federation is really two adapters in one object, not two independently wired ports

`ToolFederationTest` and `DoctrineGuardTest` hold this in code (`all concatenates its sources in adapter
order`, `find reaches a tool in any source`, and the production-graph scan in `:app`'s test source set).
Not independently observable on-device beyond "both tools work" (Parts A1/A2) — the *mechanism* (one
object, two faces, first-adapter-wins collision) has no on-device signature distinct from "it worked."

### C2. A duplicate `ToolId` is refused

Needs a build with a deliberately colliding second adapter — not a state this repository's production
graph can reach without a code change, and not worth an APK pair. Covered by `DoctrineGuardTest`
(`productionAdapters().flatMap { it.registry.all() }`, no ids collide), mutation-proved twice (Task 11,
rounds 1–2 in the ledger).

### C3. The risk→gate unification (`requiresConsent`) behaves identically for a level above `SAFE`

Both shipped Tier-0 tools are `SAFE`, so this device round exercises the "no gate" arm only — the
"gate does stop the step" arm is the same code path A0's existing "no such app" plan already exercises
in Part B, unchanged by this block. A `CONFIRM`-or-above Tier-0 tool does not exist yet (A1″'s job), so
there is no on-device path today that would show a *new* consent screen distinct from A0's. Covered by
`ConsentPolicyTest`'s parity and wake-up tests (with the Ruling R4 caveat: the wake-up test cannot
distinguish `risk != entries.first()` from `risk >= CONFIRM` while three risk levels exist — see the ADR
and `§HANDOFF`).

### C4. The `AgentSessionMappers.toSessionEntity` fix for `GoalShape.Free`

The CRITICAL finding this block's own review caught — a timer command would have silently fallen through
to the model path with no session, no timer and no error, on a tree where 1285 unrelated tests were
green. Reproducing the *broken* state on-device would require re-introducing the bug; the *fixed* state
is exactly what Parts A1/A2 already demonstrate (a session is created and a real Android intent fires).
Covered end-to-end by `FreeTextGoalEndToEndTest` (real Room, real `CompositePlanner`, real federation,
real `RouteCommandUseCase`) with `assertEquals(0, modelPlanner.planCallCount)` as the fall-through
tripwire — the join no device tap can observe directly, only its absence of failure.

### C5. Not producible on device without contrivance — named, not scheduled

- **A `SAFE → CONFIRM` risk change on an already-registered tool.** Needs a build that ships one risk
  level, a device that reaches the federation with it, then a build that raises the risk — `DoctrineGuardTest`
  does not pin declared risk beyond the two A0 in-app tools (Task 12's parity test), so this class is
  open by design, not merely untested. Covered nowhere on-device; owned by whichever block widens the
  parity test or the guard.
- **The `core/testing/src/main/java` call-site blind spot (Ruling R7).** A `ToolWorker` declared there
  is invisible to `ToolWorkerCallSiteGuardTest` by construction — there is no on-device signature for a
  scan gap; it either exists in the source tree or it doesn't.
- **The `RouteCommandUseCase:147-150` fail-open class beyond the one fixed instance.** Reproducing it
  needs a disk-full or corrupted-row `AgentSessionStore` failure — not a state `adb` can induce cleanly
  on a device the owner is using for acceptance. Covered by nothing today; addressed to A4′.

---

## Closing this file — DONE 2026-09-10

Parts A and B were performed by the owner on the SM-A325F (Android 13, `ru-RU`) and signed off. What
each numbered step of the original closing protocol produced:

1. **Recorded in the ADR** «2026-09-03 — Этап 5 (A1′)», `ai-context/decisions.md`, § «Приёмка на
   устройстве» — in the shape Task 15's A0 section takes: what was observed, not what was expected,
   including the three findings the run produced and the two owner rulings.
2. **`CLAUDE.md` and `ai-context/current-status.md` updated: A1′ moved `CODE-GREEN` → `CLOSED`.** The
   owner took the second option this step offers — the block's other named residuals (`DOC-ADL-1`'s
   closure resting on the single call site, the `core/testing` scan gap, `RouteCommandUseCase`'s
   fail-open class, `DoctrineGuardTest`'s risk-pinning gap, the `line()` argument-count limit, the
   permission guard's two measured blind spots, per-worker exception containment, `"sayaç ayarla"`)
   are **carried forward as named residuals**, not cleared. `CLOSED` requires naming them, not
   clearing them, and none of them is implied absent anywhere.
3. **Part C is recorded beside the acceptance as evidence, and cleared nothing** — that is stated in
   both the ADR section and `CLAUDE.md`.
4. **`"sayaç ayarla"` — NOT JUDGED** (see §A1). No Turkish speaker was available and the `tr` rows
   never ran on the device; `en` did not run either. The trigger ships unchanged. If the verdict ever
   becomes "drop it", that is a code change in `ToolVocabulary.kt`'s `tr` suffix set and belongs to
   whichever session makes it.
5. **This file is kept, not deleted** — the same call A0's checklists took. It is now the record of a
   protocol that ran, including the two Part B items that failed on the first pass because of how the
   database was read rather than because of the engine.

**Status: A1′ is `CLOSED` as of 2026-09-10** — gate green plus owner device acceptance in one locale
(`ru`), with every residual named rather than implied absent. `CLOSED` is not a zero-debt claim.
