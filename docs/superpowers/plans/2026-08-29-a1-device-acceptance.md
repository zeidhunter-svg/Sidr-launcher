# A1′ — device acceptance (federated `ToolRegistry`)

> **Why this file exists.** Block A1′ (Этап 5) is `CODE-GREEN`: gate green at 1304 tests (1298 at
> closing on 2026-09-03, +6 from the final-review fix `356fe1a`), every new
> guard mutation-proved, ADR written. It is **not** `DEVICE-ACCEPTED`. Unlike A0.5 (whose device
> acceptance was marked *not applicable*, because that block changed nothing on the phone), A1′ changes
> exactly what the user can reach: two new system intents — `set_timer` and `open_system_settings` —
> registered in the federation and made reachable from a typed command through routing step 2b. Spec
> `2026-08-29-a1-federated-tool-registry-design.md` §16 criterion 1 requires an owner run on-device, and
> it has not happened. This is the checklist that closes that gap.
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
- `com.sidr.launcher` is the package. Reading the agent tables after each run:
  ```
  adb shell run-as com.sidr.launcher sqlite3 databases/sidr_history.db \
    "SELECT seq, type, step_index, detail FROM agent_trace_event ORDER BY seq;"
  ```
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

- [ ] The system's own **Set Timer** UI opens (`AlarmClock.ACTION_SET_TIMER`, `EXTRA_SKIP_UI=false`) —
      Sidr never sends the timer itself; the last act is the user's, inside the OS's own screen. This is
      the "prefilled but not sent" shape Master Plan §3.6 `B4` describes, and it is what makes `SAFE` an
      honest declaration here rather than a convenient one.
- [ ] The prefilled duration matches the command (5 minutes for the phrasing above).
- [ ] The agent surface (reachable from wherever A0's plan surface already showed) displays a
      **one-step, completed** plan whose step line reads (locale-dependent):
      - `ru`: «Поставить таймер на 5 минут»
      - `en`: "Set a timer for 5 minutes"
      - `tr`: "5 dakika için zamanlayıcı kur"
- [ ] Directly beneath that step line, a second, smaller line reads **`SYSTEM INTENT · EXTERNAL`** —
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

### A2. A typed command opens real Android settings

| Locale | Command |
|---|---|
| `ru` | «системные настройки» |
| `en` | "system settings" |
| `tr` | "sistem ayarları" |

- [ ] The real Android **Settings** app opens (`Settings.ACTION_SETTINGS`) — not Sidr's own settings
      screen.
- [ ] The agent surface shows a completed one-step plan with the step line:
      `ru` «Открыть настройки Android» / `en` "Open Android settings" / `tr` "Android ayarlarını aç".
- [ ] The same `SYSTEM INTENT · EXTERNAL` provenance line appears beneath it.
- [ ] **Negative check** — type «открой настройки системы» (`ru`) or "open system settings" (`en`).
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

- [ ] Read and accepted, or amended. None of the three Class B strings is Class B *locked-and-signed*
      vocabulary (calculation names, Shahada) — an amendment here is a normal string edit, one per
      locale, not a re-signature of the locale digest.
- [ ] `launcher_tool_level_unknown` ("ANOTHER APP · EXTERNAL") is the fail-closed label for a
      `ToolLevel` this surface has no specific sentence for — it is reachable by construction (`ToolLevel`
      is an open value class) but **not** reachable by either tool shipped in this block; it has no
      on-device trigger to demonstrate today. Named here so its absence from A1/A2 above is not mistaken
      for an oversight.

---

## Part B — the owner re-runs to confirm nothing regressed

These paths are **unchanged in behaviour** by this block; they are here because the block touched the
engine and the surface they run through, and "unchanged" is worth one minute of device time each.

- [ ] **A0's two-step plan still runs.** «открой <не установленное>» still produces launch → store,
      still stops at the risk transition before the store step, still completes the same way it did on
      2026-08-22/23 — **except** for the two added provenance lines, which is A3/§A5 above, not a
      regression.
- [ ] **Cancelling mid-plan** still runs nothing further, and the three `agent_*` tables are empty
      afterwards (cascade delete, unaffected by federation).
- [ ] **`adb shell am force-stop com.sidr.launcher`** at any point during either new tool's (near-instant)
      execution, then relaunch — no stuck `AwaitingConsent` or orphaned row; a `SAFE` step either
      completed before the kill or the session offers to resume, matching A0's existing resume shape.
- [ ] **The FastPath verbs A1′ did not touch** still work as before: an installed app still opens on
      «открой <установленное>» without ever reaching the agent surface at all.
- [ ] **Assistant / BYOK chat** (unrelated surface, unrelated code) still streams normally — confirms the
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

## Closing this file

When Parts A and B are signed off by the owner:

1. Record the run in the ADR «2026-09-03 — Этап 5 (A1′)» — a "Device acceptance" section, in the shape
   Task 15's A0 section and the 2026-08-23 re-check both take: what was observed, not what was expected.
2. Update `CLAUDE.md` § Shipped surface and § Known debt, and `ai-context/current-status.md`: A1′ moves
   from `CODE-GREEN` to `DEVICE-ACCEPTED` (or `CLOSED`, if the owner also considers the block's other
   named residuals — `DOC-ADL-1`'s wake-up-test caveat, the `core/testing` scan gap, the
   `RouteCommandUseCase` fail-open class, `DoctrineGuardTest`'s risk-pinning gap, the `line()`
   argument-count limit, `"sayaç ayarla"` — acceptable to carry forward as named residuals rather than
   blockers; `CLOSED` requires naming them, not clearing them).
3. Carry Part C's outcome as evidence beside it, labelled as evidence, not as acceptance.
4. If the owner does **not** accept a wording or a trigger — `"sayaç ayarla"` in particular (Part A
   above; it is still shipped, the implementer's recommendation to drop it was never acted on; see
   `§HANDOFF`) — record the decision here or in the ADR before closing. If the verdict is "drop it",
   that is a code change (removing one string from `ToolVocabulary.kt`'s `tr` suffix set) and belongs
   to whichever session makes it, not to this documents-only pass.
5. Delete this file, or leave it as the record of the protocol — the owner's call, same as A0's.

Until then: **A1′ is `CODE-GREEN` as of 2026-09-03; `DEVICE-ACCEPTED` is a plain gap, not an
inapplicability — this checklist is what closes it.**
