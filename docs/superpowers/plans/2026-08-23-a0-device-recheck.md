# A0 — device re-check after the 2026-08-23 review round

> **Why this file exists.** Block A0 was `DEVICE-ACCEPTED` on 2026-08-22: the owner ran all eight §12
> items on the SM-A325F and signed off. The review round of 2026-08-23 (`a7f4755`) then changed
> behaviour inside that accepted surface. Этап 0.5's status vocabulary does not let a signature carry
> across a behavioural change, so **the changed behaviour is currently accepted by the gate, not by the
> owner** — and A0's `CLOSED` carries that as a named residual until this checklist is run.
>
> It is a file rather than a line in a ledger on purpose: three debts of this family were once lost by
> living only in `§HANDOFF`. Referenced from `CLAUDE.md` § Known debt, `ai-context/current-status.md`
> and `§HANDOFF`.
>
> **Owner-run means owner-run.** An `adb`/`uiautomator` pass driven by an agent is evidence, not
> acceptance (Этап 0.5). Part C below is explicitly the agent's half and does **not** clear anything.
>
> Source of the changes: ADR «2026-08-23 — Сквозное ревью блока A0» in `ai-context/decisions.md`.
> Acceptance list being re-run: spec `2026-08-20-a0-thin-agentic-spike-design.md` §12.

---

## Device state to establish first

Same starting conditions as Task 15, because the point is to compare against what was accepted:

- SM-A325F, Android 13, locale **`ru-RU`**.
- Install the new debug build **over** the existing one — no uninstall. The database is already at
  `user_version = 4`; nothing in this round changed the schema, so no migration runs and none should.
- `com.sidr.launcher` is the package. Reading the agent tables:
  ```
  adb shell run-as com.sidr.launcher sqlite3 databases/sidr_history.db \
    "SELECT seq, type, step_index, detail FROM agent_trace_event ORDER BY seq;"
  ```

---

## Part A — the owner must look, because the product surface changed

### A1. §12.8 re-run — app **installed**, so the store step is skipped

Run «открой <установленное приложение>» for an app that **is** installed.

| | Accepted 2026-08-22 | Expected now |
|---|---|---|
| title | «План выполнен» | **«План пройден, выполнено не всё»** |
| tone | Completed | **Partial** |
| body | — | **«Sidr прошёл план до конца. Часть шагов не выполнялась — рядом с каждым написано, что с ним.»** |
| step 0 | green marker, «Открой …» | green marker, **«Открой … — выполнено»** |
| step 1 | **green marker**, «Найти … в магазине» | **grey/INFO marker**, **«Найти … в магазине — не потребовалось»** |

- [ ] The store did **not** open (it never should have; this is the control).
- [ ] Step 1 no longer reads as done. **This is the whole point of the change:** the old surface put a
      success marker on a step that never ran.
- [ ] The wording is acceptable as product copy. If it is not, say so — it is copy, not a mechanism,
      and changing it costs one string per locale.

> The §12.8 acceptance item's original phrasing — "`Completed`, not partial" — is **superseded** and the
> spec says so. A skipped step makes the run partial; saying so is the correction, not a regression.

### A2. Read the six new strings

`ru` is what ships on this device; `en`/`tr` ship in the same commit and are listed for completeness.
None is Class B, so the locale signature was neither touched nor re-signed.

| key | ru |
|---|---|
| `launcher_agent_completed_partial_title` | План пройден, выполнено не всё |
| `launcher_agent_completed_partial_body` | Sidr прошёл план до конца. Часть шагов не выполнялась — рядом с каждым написано, что с ним. |
| `launcher_agent_step_line` | `%1$s — %2$s` |
| `launcher_agent_step_state_done` | выполнено |
| `launcher_agent_step_state_skipped` | не потребовалось |
| `launcher_agent_step_state_failed` | не получилось |
| `launcher_agent_step_state_current` | выполняется |
| `launcher_agent_step_state_pending` | не начат |

- [ ] Read and accepted, or amended.

---

## Part B — the owner re-runs to confirm nothing regressed

These paths are **unchanged by the fix round**; they are here because the round touched the engine they
run through, and "unchanged" is a claim worth one minute of device time each.

- [ ] **§12.1** — «открой <не установленное>» still produces a two-step plan.
- [ ] **§12.2** — the gate still stops at the risk transition, before the store step.
- [ ] **§12.3** — cancelling mid-plan still runs nothing further, and the three `agent_*` tables are
      empty afterwards.
- [ ] **§12.4** — the trace still shows every step.
- [ ] **§12.5** — `adb shell am force-stop com.sidr.launcher` **at the gate**, relaunch → the session is
      offered as `Paused`, and the trace grows by exactly **one** `SessionPaused`.
      *Expected to be identical to 2026-08-22.* The engine's mid-step predicate changed, but a session
      paused at the gate has already recorded `ToolObserved(0)`, so both the old and the new predicate
      answer "not mid-step". The difference lives only in the mid-call shape — see C1.
- [ ] **§12.6** — double-tapping confirm still opens the Play Store exactly once.

---

## Part C — agent-verifiable evidence, which does **not** clear anything

Recorded so a later session does not mistake "hard to check on device" for "unchecked". None of these
counts as acceptance; each is instrumentation the agent can run and log.

### C1. The mid-call resume — the actual subject of finding F1

The only shape where §12.5's behaviour differs. Reproduced the way Task 15 reproduced it, and for the
same reason: the window is **157–170 ms warm, 1033 ms cold**, so the poll must run **on the phone** —
a USB round-trip misses it, and the miss is indistinguishable from success until you read the trace.

```
pm disable-user <target package>     # make FastPath miss, so a plan is created
# on-device loop: poll agent_trace_event for the goal word, then am force-stop
pm enable <target package>
# relaunch, press «Продолжить»
```

- [ ] After Continue, `agent_trace_event` holds **one** `ToolInvoked` with `step_index = 0`, not two.
      Before the fix it held two, with a single `ToolObserved`.
- [ ] The tool ran once — the app comes to the front once in logcat.

### C2. One refusal is one event (finding F4)

- [ ] Reach the gate, refuse, and read the trace **before** the cascade delete: exactly one
      `ConsentResolved(1, false)` row. Before the fix there were two.
      *Timing note:* a terminal state deletes the session, so this must be read from a trace captured at
      the moment of refusal — Task 15 hit the same problem on §12.6 and proved it from logcat instead.

### C3. Not producible on device without contrivance — named, not scheduled

- **F2 (stale risk).** Needs a build that lowers a tool's risk, a plan persisted under it, and an
  upgrade to a build that raises it. Covered by `AgentExecutorTest`, mutation-verified in both
  directions. Not worth an APK pair.
- **F10 (unreadable row deleted).** Needs a corrupt `agent_session` row seeded by hand. Covered by
  `RoomAgentSessionStoreTest`, mutation-verified.
- **The failed-step shape** — a step 0 that *fails* now closes the plan `Partial` instead of «План
  выполнен» with two green markers. This is a **new** observable shape with no §12 item, because A0's
  acceptance list predates the finding. Producing it needs a tool failure the launcher does not
  normally generate. Covered by `AgentSessionPresentationTest`; named here so it is a known gap rather
  than an assumed pass.

---

## Closing this file

When Parts A and B are signed off by the owner:

1. Record the run in the ADR «2026-08-23 — Сквозное ревью блока A0» — a «Device re-check» section, in
   the shape Task 15's section takes: what was observed, not what was expected.
2. Lift the residual from `CLAUDE.md` § Known debt and from `ai-context/current-status.md`; the fix
   round becomes `DEVICE-ACCEPTED` and A0 returns to a clean `CLOSED` with its three standing
   limitations.
3. Carry Part C's outcome as evidence beside it, labelled as evidence.
4. Delete this file, or leave it as the record of the protocol — the owner's call. If it is deleted,
   the ADR section must be self-contained first.

Until then: **A0 is `CLOSED` as of 2026-08-22, plus one named residual — the 2026-08-23 fix round is
`CODE-GREEN` only.**
