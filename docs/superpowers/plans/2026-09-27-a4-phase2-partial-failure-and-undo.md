# A4′ Phase 2 — partial failure and undo · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: written 2026-09-27, reviewed once against the tree and the spec (§0.8). Not started.**
> - Branch `launcher--7`, base `4cf37c7`, gate baseline **1485 / 0 / 0**.
> - **Spec:** [§6 in full, plus §3 (0.2, 0.3, 0.4), §9, §10 of
>   2026-09-21-a4-runtime-design.md](../specs/2026-09-21-a4-runtime-design.md). It is `ПОЛНА`; the
>   owner approved it section by section on 2026-09-21. **The owner amended §6.4 and §6.5 on
>   2026-09-27** (fork F2-3.1, §0.1). The amendment ships in this plan's own commit.
> - **Form template:** [A4′ phase 1](2026-09-26-a4-phase1-fastpath-overflow.md). The record of phases 0
>   and 1, and the owner answers this phase inherits, are in `§HANDOFF` of
>   [the track plan](2026-08-18-agentic-track-restart.md), subsections «Фаза 0 A4′» and «Фаза 1 A4′».
>
> **How this plan was produced — the owner's rule of 2026-09-26** (`§HANDOFF`, «как пишутся планы фаз
> 2–4»). Only two kinds of thing were run before writing, both on a scratch copy of the tree, never in
> the repository:
> - **measurements that decide scope or an owner fork** (§0.2): what each R14 mechanism does to a
>   blocked call, and how many edit sites each candidate closed-type change costs;
> - **evidence machinery that is new**: the one new test technique this phase relies on — a real
>   parked thread under `runTest`'s virtual time — was probed for determinism (§0.2, probe 3).
>
> **Everything else below is a hypothesis.** That covers every address, every test, every predicted
> `FAILED ::` set and the per-module gate decomposition. Execution checks each one
> (`mutation-prover`); a mismatch is a finding, and the plan is corrected, as phase 0 did with M5 and M7b.

**Goal.** A plan that fails part-way becomes a product surface, not a type question (`B14`: «что
произошло, что изменилось, что откатить»):
- the engine records **every** reason a step stops for, so an irreversible step is marked before it
  runs (`DOC-HMA-3`, first half);
- a finished plan offers **«Отменить»** for what it did that can be put back, and names what cannot
  (`DOC-HMA-3`, second half). No gate asks less than it does today (spec §6.3, criterion 5);
- a paused plan that has only *looked* at the world looks again on «Продолжить» instead of acting on
  a stale observation (A0 acceptance §12.8, staleness layer 1);
- a world call blocked in native code no longer holds the engine: the engine stops waiting at the
  budget, and the plan stops there (R14).

**Architecture.**
- **R14 (Task 1).** The one call to the world runs in its own scope on an injected context. Android
  passes `@IoDispatcher`, the JVM consumer passes `Dispatchers.IO`. The engine awaits it only until
  `RuntimeBudget.maxStepWallClockMs`, records `HandedOff`, and ends the plan `Blocked`.
- **Reasons (Task 2).** `ConsentCheckpoint` and `TraceEvent.ConsentRequested` carry a **set** of
  reasons. Both stores keep it without a migration.
- **Undo (Tasks 3, 5, 6, 7).** A parallel port `ToolInverses` says how each tool is undone. The pure
  `Compensation` turns a finished session into an undo plan, and `UndoAgentSessionUseCase` runs that
  plan as a **new session** through the unchanged engine. The engine never sees the port.
- **Resume (Task 4).** «Продолжить» on a paused plan in which nothing has acted plans the goal afresh,
  as a new session. The store keeps one session at rest, so the fresh session replaces the stale one
  atomically.
- **`:consumer:jvm` (Task 7)** proves «failed part-way → undo what it did» on real files.

`RouteCommandUseCase`, `IntentActionResolver`, `ExecutionState`, `ToolDescriptor` and the Room schema
are not in the diff.

**Tech Stack.** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain`
(`commonMain` + `jvmTest`) · JUnit4 + `kotlinx-coroutines-test` · Robolectric + Compose UI test for the
two new surface tests.

---

## Global Constraints

Every task's requirements implicitly include this section.

### Running the gate

- **The gate is `tools/gate.sh`** (ruling R2). Compile-only probes use bare `./gradlew` with
  `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`.
- **Boundary run: `tools/gate.sh` with no arguments.** It closes every task. It clears
  `build/test-results` everywhere, passes `--rerun-tasks`, counts from the JUnit XML, and says
  `GATE GREEN` only at or above its floor (`BASELINE_TESTS = 1485`, `tools/gate.sh:195`).
- **Scoped run: `tools/gate.sh --scoped :module:task`**, for TDD steps and mutations. It labels
  itself `NOT a boundary gate`.
- **A mutation whose red set spans modules is run once per module.** Without `--continue`, which
  modules a multi-module scoped run reports depends on scheduling (phase 1, review B). Every prediction
  below is given per module.
- **The Gradle daemon is ON** (owner, 2026-09-25, ruling R8). **`--rerun-tasks`, never `--rerun`.**
  Take counts from the XML, never from the console, and never pipe through `tail`.
- **Known timing-sensitive test:** `:data:ai-cloud`'s `first token arriving just under the deadline
  still completes normally`. A red there is a reason to re-run from a cleared tree, not to believe.

### Reading the results

- **Baseline 1485 / 0 / 0**, per module:

  | Module | Tests |
  |---|---:|
  | `:domain` | 455 |
  | `:consumer:jvm` | 57 |
  | `:data:repository` | 394 |
  | `:feature:launcher` | 202 |
  | `:app` | 61 |
  | the other eight | 316 |

  **Predict this decomposition before every run and compare after.** A total cannot tell "two added"
  from "two added and two lost".
- **`:domain`'s `FAILED ::` lines end in `[jvm]`.** The predicted names below omit it, so compare
  names, not raw lines.
- **A compile-red TDD step prints other `e:` lines besides the one named.** The named
  `Unresolved reference` / `Argument type mismatch` must be among them.
- **Line addresses are at HEAD `4cf37c7`.** Inside a task, an earlier edit shifts later lines. Every
  instruction also quotes the text it replaces, and that text is what to match.

### The mutation protocol

One shape for every mutation, never `git checkout` (ruling R1). Save the helper once per session,
**at column 0** (an indented copy fails with `plant: command not found`):

```bash
cat > "${TMPDIR:-/tmp}/sidr-plant.sh" <<'SH'
plant() {  # plant FILE OLD NEW — exact string; refuses unless OLD occurs exactly once
python3 - "$1" "$2" "$3" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
n = s.count(old)
if n != 1:
    sys.exit(f"PLANT REFUSED: {n} occurrences of the old text in {p}")
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
}
SH
```

Each mutation is **one** shell invocation:

```bash
source "${TMPDIR:-/tmp}/sidr-plant.sh"
F=<file>; MUT="$(mktemp -d -t sidr-mut-XXXXXX)"; cp "$F" "$MUT/orig"
trap 'cp "$MUT/orig" "$F"' EXIT          # restores on error or interrupt (not on SIGKILL — R10)
plant "$F" '<old>' '<new>' && tools/gate.sh --scoped <:module:task>
cp "$MUT/orig" "$F"; cmp "$MUT/orig" "$F" && echo RESTORED && trap - EXIT
```

- **In the mutation tables, `\|` is a literal `|`** (Markdown table escaping) and `\n` is a newline.
  The plant text is the unescaped string.
- **A mutation is proved by WHICH tests failed**, read off the `FAILED ::` lines against the
  predicted set.
- **None of these proves anything:** a red set that differs from the prediction; `PLANT REFUSED`;
  `gradle=1` with **no** `FAILED ::` lines (a compile error). Restore, find out why, run again.
- `RESTORED` must print.
- **Two mutations in this plan hang by design (M1a, M1c).** They put a parked call back on the test's
  own thread, which is exactly the defect they plant. Their tests carry `@Test(timeout = 20_000)`, so
  the plant ends as a JUnit timeout failure after about 20 s rather than as a hung build. That timeout
  **is** the predicted `FAILED ::` line.
- **Roles (R4, R10).** The implementer writes a task and the controller commits it; a reviewer
  checks. Mutations are proved by `mutation-prover`, or by the controller in the foreground. **After
  any restart, first run `git status --porcelain --untracked-files=all` and `git log --oneline -3`.**
- **The last action of any mutation-proving task is a boundary gate from a cleared tree** (R14-44). It
  runs before the commit.

### Real threads under `runTest` — a rule this phase needs (§0.2, probe 3)

- Under `runTest`, a world call that runs on a **real** dispatcher loses to virtual time. The test
  scheduler has nothing to do while the test coroutine awaits, so it advances straight to the budget
  and the call is cut **at once**, however fast the real thread is.
- **So an `AgentExecutor` built with a real `worldContext` is allowed under `runTest` only in a test
  whose subject is the cut** (Task 1: two tests). Everywhere else the default `EmptyCoroutineContext`
  applies, and the default is what every existing construction site already gets.
- A test with a parked real thread parks it on a `CountDownLatch`, never on `sleep`, and releases it
  in `finally`.

### Fixtures and frozen code

- **Fixtures (R14-43).** No new fixture is a real app, package or file.
  - Packages: `net.fixture.quill`, `net.fixture.parcel`.
  - Alias phrase: «вельтрикс». App names: «лумора», «вельтрикс».
  - Files: `ledger.txt` → `ledger.old`, `absent.txt` (never created), `other.txt`.
  - Fixture tool ids: `fixture_quill`, `fixture_ledger`, `fixture_parcel`, `fixture_note`,
    `fixture_unnote`.
  - Every expected value is compared **by equality**. Two surface texts share a prefix («You left
    before this plan finished…»), so they are matched as whole texts, never with `substring = true`.
- **Frozen, and checkable with `git diff`:**
  - spec §10 criterion 10: `ActionIds`, `ObservedFact`, `CommandFailure`, `GoalShape`, `ArgType`;
  - **also frozen by this plan:**
    - `ExecutionState` — spec §6.0: partiality stays derived;
    - `ToolDescriptor`'s type — undo is a parallel port (phase 0 precedent);
    - `RouteCommandUseCase.kt` and `IntentActionResolver.kt`;
    - the Room schema — stays 4, no migration, no `@Entity` edit; the new data rides existing TEXT
      columns (§0.3 P4);
    - every `strings_locked.xml` — no Class B key, so the locale signature is not re-issued.
- **`:domain` is `commonMain`:** stdlib + coroutines only. Tests live in `jvmTest`.
- **Strings:** this phase adds **three** keys (§0.3 P16), each in `en`/`ru`/`tr`, in
  `feature/launcher/src/main/res/values*/strings.xml` (never `strings_locked.xml`).
- **A line predicting what the user will see is a hypothesis** until it has been run once on a phone,
  and is written in that tone (§0.6).

### Commits

- **The plan is committed before Task 1.** Its commit carries this file, the spec amendment of §6.4 and
  §6.5 (owner fork F2-3.1), and the `§HANDOFF` pointer. So the `git status` expectations below count
  only a task's own files.
- Commit messages are Russian conventional commits, scope `agentic-6/A4' ф2`, with the trailer
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (R5).
- **The agent commits and never pushes.** The phone and its network are not touched, and
  `connectedAndroidTest` is never run.
- **Task 8, the close, ends in a *proposed* commit** (ruling R7): its doctrine-matrix edit anchors
  `DOC-HMA-3`, and that is the owner's to approve.

## Review Focus

Five input classes the spec implies but does not name. Each is among those most likely to bite someone
using this build, and each is pinned by a test in the task that owns the code.

1. **A double tap on «Продолжить» or «Отменить».** Re-planning makes a new session id per tap, so the
   store's conditional write no longer de-duplicates. Two taps must start one run. This gap exists
   today and is not introduced by this phase (§0.3 P6).
   → Task 4, `a second Continue tap while the first is still running starts nothing`;
   Task 6, `a second Undo tap while the first is running starts nothing`.
2. **An alias that replaced an older one.** `set_app_alias` is an upsert (§0.3 P7). Undo must point the
   phrase back at the older app, not delete it.
   → Task 5, `set_app_alias reports the app an overwritten alias pointed at` and
   `an alias that replaced another is undone by pointing it back, not by forgetting it`.
3. **A paused plan with a call in flight.** The act may already have happened, so re-planning would
   perform it twice. Such a plan must continue, never re-plan.
   → Task 4, `a call in flight at the pause is resumed, never planned again`.
4. **An undo that was itself paused before it ran.** Its goal is the original goal, so re-planning it
   would redo what it undoes.
   → Task 4, `an undo plan is never planned again from its goal, which would redo what it undoes`.
5. **A cancelled uninstall.** It is handed off, and its outcome was never seen, so it must never be
   offered for undo: the "inverse" would act on something that did not happen.
   → Task 3, `a handed-off step is never undone, because its outcome was never seen`.

---

## 0. Pre-flight scan of this plan against the tree

### 0.1 · The three owner forks of this phase, resolved 2026-09-27

All three were put to the owner before a line of this plan was written, each with its options,
their cost and a recommendation. The measurements behind the costs are §0.2. The owner answered in
one line: `1: B, стоп · 2: A · 3: а/а/а`.

| # | Fork | Owner's call |
|---|---|---|
| **F2-1** | R14 (`§HANDOFF` «Фаза 1 A4′», answer 3): *how* to extend the wall-clock cut to synchronous world calls. **A** `runInterruptible(IO)` in each worker; **B** run the call in its own scope on an injected context and stop waiting at the budget; **C** a cooperative check in each worker. Measured (§0.2 probe 2): only B returns at the budget for an uninterruptible block. A waits the full block out, then **discards** the real result. C cannot shorten anything | **B.** One call site, `AgentExecutor.perform`. Production passes an IO context. The act can still happen after `HandedOff` is recorded — which is what `HandedOff` already says |
| **F2-1b** | Raised by B: a call the engine stopped waiting for may still be acting. Keep today's rule (after a cut, the plan goes on) or stop | **Stop: the plan ends `Blocked`.** A second world call must not start while the first may still act. The Blocked body «продолжать его безопасно уже нельзя» fits word for word. Cost: the Blocked card draws no step list (a named A0 limit), so the handed-off step shows only in the trace |
| **F2-2** | Spec §6.1: a step can have more than one reason to stop, but `ConsentCheckpoint` carries one. **2A** a set of reasons on the checkpoint and the trace event; **2B** a separate trace event "this step is irreversible"; **2C** several `ConsentRequested` events for one stop | **2A.** One stop, one event, every reason. The uninstall card's chip goes `CONFIRM` → `DESTRUCTIVE`. `RISK_RAISED` counts only when a step already ran before this one |
| **F2-3.1** | What triggers re-planning in phase 2. **(a)** only staleness on resume, minimally: a paused plan in which nothing has acted is planned afresh; re-planning after a failure moves to phase 3. **(b)** the full revision mechanism now, on both triggers | **(a).** **This amends spec §6.4 and §6.5** (the amendment is in this plan's commit). `PlanningRequest.priorObservations` gets its consumer in phase 3, the bridge's model planner — the first planner that could do anything with "step 2 failed because X" |
| **F2-3.2** | What the paused card says when «Продолжить» will re-plan. Today it promises «Sidr возьмётся с того места, где остановился», which is false for a re-plan. **(a)** a second body text; **(b)** silence; **(c)** two buttons | **(a).** A new key, chosen by the same predicate the use case uses (`AgentSession.replansOnResume`) |
| **F2-3.3** | «Что откатить». **(a)** one «Отменить» on the finished card: never automatic, a new session through the same engine and gates, names what it cannot undo, lives only while the card is on screen; **(b)** a button per step; **(c)** automatic rollback on failure | **(a).** An overwritten alias is restored, not deleted, so the tool captures the prior value. `forget_learned_choice` has no undo. The JVM stand gets one reversible tool, `rename_file` |

### 0.2 · The measurements this plan stands on

All three probes ran on a scratch copy, `git archive 4cf37c7` plus `local.properties`, at
`/tmp/claude-1000/-home-Suleiman-Sidr-launcher/ee739671-4c86-4f4a-bf08-75fbaa34ee5e/scratchpad/wt`.
The tree was not touched. §0.7 says how to reproduce them.

**Probe 1 — closed-type edit sites, by compiling.** The compile set was
`:domain:compileTestKotlinJvm :data:repository:compileDebugUnitTestKotlin
:feature:launcher:compileDebugUnitTestKotlin :consumer:jvm:compileTestKotlin
:app:compileDebugUnitTestKotlin` with `--continue`. The baseline compile was clean (0 `e:`).

| Change probed | Result |
|---|---|
| **+1 value in `TraceEvent`** — the cost of option 2B, and of any re-plan event under F2-3.1(b) | **4 `e:`** in 2 modules: `AgentSessionMappers.kt:185`, `:218`, `SessionMapper.kt:162` (`:consumer:jvm`), `ConsoleHarness.kt:205`. Also 2 **string** decoders, which are silent by construction: Room's (`AgentSessionMappers.kt:356+`) is held by the `sealedSubclasses` tripwire (`AgentSessionMappersTest:245-258`); the JVM one (`SessionMapper.kt:~175+`) has **no** tripwire |
| **`ConsentRequested.reason` → `reasons: Set`** — option 2A, with `AgentExecutor`'s one writer adapted | **7 production sites in 5 files, 4 modules**: `AgentSessionMappers.kt` ×2, `SessionMapper.kt` ×2, `ConsoleHarness.kt` ×1, `AgentSessionSurface.kt` ×1, `AgentExecutorTest.kt` ×1 (a test reading `.reason`). The test constructors that stop compiling are counted by grep (§0.3 P12). No schema change: Room's `detail` is TEXT |
| **+1 value in `StepRationale`** — `COMPENSATION`, Task 3 | **0 `e:`**. Room's `readRationale` (`AgentSessionMappers.kt:428-432`) falls to `else -> throw` for the new value, but `AgentSessionMappersTest > every StepRationale round-trips` iterates `entries` and goes red until the decoder learns it. The JVM decoder is generic (`enum(StepRationale.entries, …)`, `SessionMapper.kt:97`) |

**Probe 2 — what each R14 mechanism does to a blocked call (JVM, real time).** The block holds
**2 000 ms** and the budget is **300 ms**. The "uninterruptible" block is `FileInputStream(fifo)`,
which parks in `open(2)` until a writer appears. That is the kernel-side shape of a binder `ioctl`:
`Thread.interrupt` reaches neither.

| Mechanism | `Thread.sleep` (interruptible) | FIFO open (uninterruptible) |
|---|---|---|
| today, a synchronous call under `withTimeout` | 2029 ms · **real result** | 2009 ms · **real result** |
| today, the block inside `withContext(IO)` | 2023 ms · result discarded → `HandedOff` | — |
| **A** `runInterruptible(IO)` | 302 ms · `HandedOff` | **2004 ms**, then result discarded → `HandedOff` |
| **B** own scope on IO, stop waiting at the budget | 305 ms · `HandedOff` | **302 ms** · `HandedOff` |

- **The two "today" rows are the first measurement of phase 0's R14 claims**, which stood "by reading"
  in `RuntimeBudget`'s KDoc. Both held.
- **Binder itself was not measured**, because the phone is off-limits. That binder calls ignore
  `Thread.interrupt` is by reading: a JNI `ioctl` is not an interruptible Java primitive.

**Probe 3 — the test technique (new evidence machinery).**
- **Setup:** B's shape under `runTest`, with a real `Dispatchers.IO` thread parked on a
  `CountDownLatch` and a 10 000 ms budget.
- **Result:** **20 runs of 20** returned `HandedOff`, with `currentTime == 10_000`, in **0–3 ms of
  real time**. The latch was released afterwards.
- **The same shape with the default context** (`EmptyCoroutineContext`, the caller's dispatcher) and
  a suspending fake keeps every existing virtual-time test honest: `delay(20_000)` was cut at
  10 000, and `delay(9_000)` returned its real value.
- **This is also the finding behind the "real threads" rule** (Global Constraints): under `runTest`, a
  real-dispatcher call is cut at virtual time regardless of how fast the real thread is.

### 0.3 · Facts read out of the tree, with addresses at `4cf37c7`

**(P1) On Android the agent runs on the main thread, and so do two of its three kinds of world call.**
- `LauncherViewModel.kt:261-267` builds `LauncherAgentSession` with `scope = viewModelScope`, and
  `RunAgentSessionUseCase` switches no dispatcher.
- `ContextIntentLauncher.launch` (`Tier0IntentToolWorker.kt:45-47`) and `ShortcutToolWorker.invoke`
  (`ShortcutToolWorker.kt:73-83`) call `startActivity` / `startShortcut` synchronously on the calling
  thread — every Tier-0 tool and every shortcut.
- `SystemIntentToolWorker` reaches the world inside `withContext(io)`, via the repository and the
  executor.
- By reading kotlinx.coroutines: `withTimeout` on the Main dispatcher schedules its timeout as a Handler
  callback **on the main thread**. So while a binder call holds Main, today's budget cannot fire at
  all, and a hang shows up as a frozen home screen (ANR after ~5 s). F2-1's IO context moves the call
  off Main as a side effect.

**(P2) The one call site** is `AgentExecutor.kt:225-241`. `ToolExecutorCallSiteGuardTest` (`:app`)
requires exactly one spelled `toolExecutor.invoke(` in the file `AgentExecutor.kt`. Task 1 keeps it
at one, inside `async { … }`.

**(P3) The single-reason chain, end to end:**
- `checkpointFor` returns the first matching branch (`AgentExecutor.kt:318-337`).
- `ConsentCheckpoint(stepIndex, reason)` is at `AgentSession.kt:21`, and
  `TraceEvent.ConsentRequested(index, reason)` at `TraceEvent.kt:24`.
- Room: encode at `AgentSessionMappers.kt:193`; decode at `:364` with `readConsentReason` `:444-449`.
- JVM: `TraceDto.reason` at `SessionDto.kt:69`; encode at `SessionMapper.kt:167`, decode at `:187-190`.
- Harness render at `ConsoleHarness.kt:210`.
- Surface: `AgentSessionSurface.kt:96` → `ConsentReason.gateType()` at
  `AgentSessionPresentation.kt:396-401`, where `DURABLE_EFFECT` is mapped to `ExternalHandoff`.

**(P4) Room keeps one result per step index, and the trace's `ToolObserved` reads it back from the
step row.**
- Encode writes no result into the trace row (`AgentSessionMappers.kt:198`); decode takes the step
  row's (`:370-377`).
- So re-running a step under the **same** index would make the old `ToolObserved(i)` decode as the
  new result — a lying trace.
- This is why F2-3.1's re-plan is a **new session**, not a revision of the old one.

**(P5) The store keeps one session at rest, enforced on write.**
- Room: `AgentSessionDao.replaceSession` deletes every other session id inside its transaction
  (`AgentSessionDao.kt:112-114`, `:131`).
- JVM: one file. The fake does `current = session`.
- So saving the fresh session **replaces** the stale one atomically. `ResumeAgentSessionUseCase` needs
  no delete, and a failed save leaves the stale session in place.

**(P6) A pre-existing gap Task 4 closes: «Продолжить» has no single-flight guard.**
- `LauncherAgentSession.continueSession` (`:87-90`) launches a run per call.
- Two taps can drive the same session twice. Today the engine's mid-step logic narrows the harm.
- Under F2-3.1 each tap would re-plan into a **new** id, and two fresh sessions would each perform
  step 0. «Отменить» (Task 6) has the same shape. Both get one in-memory flag, which is Main-confined,
  so the check-and-set is atomic. Named by reading, not observed on a phone.

**(P7) `set_app_alias` can overwrite, and the keys are normalized on one side only.**
- `SaveAliasUseCase.save` calls `store.upsert` after `CommandNormalizer.normalize(phrase)`
  (`SaveAliasUseCase.kt:14-27`).
- `forget_app_alias` passes its phrase to `DeleteAliasUseCase` **unchanged**
  (`MemoryToolWorker.kt:87-93`; pinned by
  `MemoryToolWorkerTest > forget_app_alias passes the phrase to the store UNCHANGED…`).
- So an honest undo of `set_app_alias` needs **the key it wrote** and **what that key pointed at
  before** — both known only inside the worker. Task 5 makes them the tool's declared outputs.

**(P8) No Android planner produces a multi-step plan with an undoable step.**
- `TemplatePlanner` builds A0's launch → store, which leaves nothing to undo.
- `ToolMatchPlanner` builds one step.
- So on Android, «Отменить» appears only on a finished **one-step memory plan**. A mixed card —
  something undone, something named as not undoable — is reachable only on the JVM stand until phase
  3's model planner. Task 6 proves the mixed rendering with a constructed session.

**(P9) The JVM stand cannot express staleness.**
- Its reads report `Effected` (`SandboxToolWorker.kt:44-55`, `WORKSPACE_INFO`/`FIND_FILE`), so
  "nothing has acted" never holds past step 0 there.
- `ObservedFact` is frozen and launcher-shaped.
- The harness's restore path (`ConsoleHarness.restore`, `:100-108`) is **not** changed by this plan.
  This is a named parity gap, owned by phase 3.

**(P10) The surfaces already have the slots.**
- `SidrResultSurface`, `SidrErrorSurface` and `SidrBlockedState` each take `secondaryAction`
  (`SidrActionSafety.kt:180`, `:212`, `:273`). Error and Blocked also take `next`.
- `SidrActionGateType.Destructive` exists, labels its chip `DESTRUCTIVE` (`strings_locked.xml:29`),
  and tones the gate surface `RISK` (`SidrActionGate.kt:~86`).
- No `core/ui` file changes, so no Roborazzi golden moves.

**(P11) `AgentExecutor` is constructed in 14 files outside its own.** A defaulted constructor parameter
changes none of them, and probe 3 showed the default keeps their virtual-time tests honest.

**(P12) Tests whose expectations the owner's decisions change** (each named in its task, with the
reason):
- **F2-3.1:** `LauncherAgentSessionTest > continuing a paused session that owes consent returns to
  AwaitingConsent` (`:157-177`). Its seed is exactly the §12.8 stale shape — step 0 has only
  *observed* — so «Продолжить» now looks at the world again. The test is rewritten, not deleted.
- **F2-3.1:** `LauncherAgentSessionTest > confirming the same step twice invokes the tool once`
  (`:179-201`) reaches its gate through the same resume. Only its script grows.
- **F2-2:** `AgentActingSeamTest` `:326-330` and `:371-384`, where uninstall's stop becomes
  `{RISK_LEVEL, DURABLE_EFFECT}`.
- **F2-2:** `AgentLoopTest:78-84`, whose own comment calls the single reason «spec §11.3's recorded
  finding, not a defect to fix here». It becomes `{RISK_LEVEL, RISK_RAISED, DURABLE_EFFECT}`.
- **F2-2:** `AgentExecutorTest:101-104`, which reads `.reason`.
- **Compile-only (F2-2), constructing `ConsentRequested` with a single reason:**
  `RoomAgentSessionStoreTest:97`, `AgentSessionMappersTest:173`, `:191`,
  `JvmAgentSessionStoreTest:133`, `AgentSessionSurfaceProvenanceTest:262`, `:358`.
- **F2-3.3 (Task 5), found by the plan review:** `AgentActingSeamTest:432` and `:555` assert
  `ToolResult.Effected()` for `set_app_alias`, which now reports what it wrote and replaced. The
  assertions move to the output-carrying value.
- **Compile-only (Task 5), found by the plan review:** `MemoryToolWorker` gains a parameter, and it is
  built in two test files besides its own — `AgentActingSeamTest:214` and `DoctrineGuardTest:262`
  (`:app`).

**(P13) Doctrine.**
- `DOC-HMA-3` («Обратимые шаги откатываются; необратимые помечены ДО исполнения») reads
  `<нет> ← долг A4′` (`sidr-doctrine-matrix-v1.0.md:139`).
- The matrix holds **31** rules. `DoctrineMatrixGuardTest` fails on a named test that does not exist.

**(P14) `RuntimeBudget`'s KDoc** (`AgentSession.kt:23-68`) says the cut is cooperative and hands R14 to
the owner. Task 1 rewrites it. `ToolResult.HandedOff`'s KDoc (`ToolInvocation.kt`, the "wall-clock
producer" bullet) is amended in the same task.

**(P15) `PlanningRequest`'s KDoc** (`ExecutionPlan.kt:99-103`) names «re-planning after a partial
failure, phase 2 (§6.4)» as `priorObservations`' consumer. F2-3.1 moves that to phase 3. Task 4
corrects the KDoc; the spec amendment is in the plan's commit.

**(P16) Three new string keys, `en`/`ru`/`tr`, all in `:feature:launcher`'s ordinary `strings.xml`:**
- `launcher_agent_paused_body_replan` (Task 4);
- `launcher_agent_undo` (Task 6);
- `launcher_agent_undo_not_all` (Task 6).

None is Class B, so the locale signature is not re-issued. The step list itself gains no word: an
undone plan's steps read in the existing step-state words.

### 0.4 · Where the spec and the tree diverge, and what each changes

| # | Spec says | Tree / measurement says | Changes |
|---|---|---|---|
| S1 | §6.4: phase 2 builds re-planning after a partial failure | none of the three deterministic planners can offer anything after a failure (P8); a re-run under the same index lies (P4) | **F2-3.1**: resume-only re-planning; the failure trigger → phase 3. Spec amended |
| S2 | §6.5: the JVM stand proves «упал → перепланировали → компенсировали» | JVM cannot express staleness (P9) | The stand proves «упал → отменили отменимое» on real files (Task 7); re-plan on resume is proved in `:domain` (Task 4) and on Android. Spec amended |
| S3 | §6.2: `launcher_memory`'s «два инструмента буквально обратны друг другу» | true only for a **new** alias; an overwrite needs the prior target (P7) | Task 5: prior-state capture as declared outputs |
| S4 | §6.1: the exact form is the plan's | — | F2-2 → 2A (Task 2) |
| S5 | §10 criterion 5: «мутация „понизить“ → RED» | the engine cannot see inverses at all, so the only natural lowering site is an undo-specific branch in `checkpointFor` | Two nets: a behavioural test with a planted branch (M3a) **and** a structural guard that `AgentExecutor.kt` names no undo vocabulary (Task 3) |
| S6 | §9: the gate command carries `--no-daemon` | retired by the owner 2026-09-25 (R8); the gate is `tools/gate.sh` | Global Constraints |
| S7 | §9.3: phase 2 touches `:domain`, `:consumer:jvm`, `:feature:launcher` | also `:data:repository` (both mappers of the reason set, the memory worker, the inverse catalog) and `:app` (wiring and two guards) | the decomposition below |
| S8 | §6.6: «что откатить» as an action on the same card | the card exists only while on screen; at rest the store is empty by design (A5's journal decision) | Undo lives exactly as long as the card; Settings remain the undo for aliases after that. Named in Task 6 |

### 0.5 · What this plan does NOT do — said, not implied

| Not here | Why / where |
|---|---|
| Re-planning after a failure | F2-3.1 → phase 3; its producer is the bridge's model planner |
| Re-plan on resume in the JVM harness | P9; parity gap owned by phase 3 |
| A multi-step undo on Android | P8; appears with phase 3's plans. The mechanism is proved on JVM (Task 7) and the mixed card on a constructed session (Task 6) |
| Undo of `set_timer` / `set_alarm` | no tool removes a timer or an alarm (spec §6.2); `NOT_UNDOABLE` rows |
| Undo of `forget_learned_choice` | a learned streak cannot be put back; `NOT_UNDOABLE` |
| Undo of a shortcut | the publisher's intent is unknown (spec §6.2); not in the catalog ⇒ not undoable |
| Undo of a handed-off step (`uninstall_app`) | its outcome was never seen; an inverse could act on nothing (Review Focus 5) |
| Undo after the card is dismissed, or after a restart | nothing is kept at rest (A5); Settings remain |
| A distinct title for a finished undo | the card reads «План выполнен» over the undo's own step lines — a hypothesis for acceptance (§0.6), not decided here |
| A button per step | F2-3.3 (b) rejected |
| Lowering any gate for an undoable tool | spec §6.3 (owner); reopens only with the first reversible `CONFIRM` tool, by ADR |
| The persisted `Failed` keeping its `CommandFailure` variant | needs either a column overload or a 4→5 migration, and no surface draws the variant; stays named (`DOC-ILM-3` cell) |
| Updating the A0 device-recheck checklist | §12.8 is now reachable by the user's own path (install while paused → «Продолжить»); the block's single acceptance checklist carries it (§0.6) |
| A link from a re-planned session back to the stale one it replaced | the fresh session's trace starts at `PlanCreated`; the stale session's `SessionPaused` and its observations are gone with it, as every session's are at rest. Recording the link would cost a new `TraceEvent` value (measured, §0.2 probe 1: 4 compile sites, 2 decoders, one without a tripwire). **Named, not built** — the trace of each session stays 1:1 with that session (`DOC-ILM-3`), and nothing at rest was ever kept |
| Any device round | the block has one acceptance, at its end (spec §0 decision 1) |

### 0.6 · Owner dependencies and hypotheses

**Owner dependencies — none blocks a task.** Two are for the block's acceptance, one for the close.
1. **The Turkish review at block acceptance gains three strings:**
   - `launcher_agent_paused_body_replan` «…Sidr planı baştan gözden geçirir…»;
   - `launcher_agent_undo` «Geri al»;
   - `launcher_agent_undo_not_all` «Burada geri alınamaz: %1$s».

   These join the six phase-0 strings and phase 1's `SecondGoal` list (`§HANDOFF` «Фаза 1 A4′»,
   answer 5).
2. **The block's acceptance checklist must re-derive §12.8.** It is reachable by the user's own path
   now. The 2026-08-23 recheck document predicts the pre-phase-2 behaviour and is not edited here.
3. **Task 8's commit anchors `DOC-HMA-3`** with test names. It is proposed to the owner, not made
   (R7).

**Hypotheses for the block's acceptance checklist.** Nobody has seen these on a phone.
- The uninstall consent card is expected to show the chip **`DESTRUCTIVE`** instead of `CONFIRM`, on
  the risk-toned surface.
- A paused A0 plan (`открой <app>` with the app missing, process killed at the gate) is expected to
  read «…а с тех пор всё могло измениться. Продолжите — и Sidr проверит план заново…».
  - If the app was installed meanwhile, «Продолжить» is expected to open it and to offer no store.
  - If not, the card is expected to return to the store's consent gate — under a **new** session.
- «назови вельтрикс лумора»-style alias commands are expected to finish with a second button,
  **«Отменить»**.
  - Tapping it is expected to show a finished card whose step reads «Забыть алиас «вельтрикс»».
  - If the alias had replaced an older one, the step is expected to read «Называть «<package>»
    «вельтрикс»». It shows the **package**, not a label: the restore carries no `app_label`, the same
    legibility limit as `open_app_info`.
- Agent calls now leave the main thread. **No visible change is expected**; a hung binder no longer
  freezes the home screen, but that cannot be provoked on demand.
- A step cut by the budget is expected to end on «План остановлен» / «Sidr остановил этот план:
  продолжать его безопасно уже нельзя…».

### 0.7 · How the predictions were produced — so a reviewer can reproduce them

- **Scratch copy:** `git archive 4cf37c7 | tar -x -C <scratch>/wt` plus `local.properties`.
- **Probe 1:** one edit per run to the probed type, the compile set of §0.2, `--continue`, then
  `grep '^e: '`, and the original restored with `cp`.
  - The `ConsentRequested` probe also adapted `AgentExecutor.kt`'s one writer to
    `setOf(checkpoint.reason)`. That is why `:domain`'s only `e:` is the test at `AgentExecutorTest:103`.
- **Probes 2 and 3:** a throwaway `jvmTest` in the scratch copy's `:domain` (`probe.R14Probe`,
  `probe.R14TestTechniqueProbe`), run with `-i` and grepped for their `R14PROBE ::` / `R14TECH ::`
  lines. Both were deleted after the run.
  - Probe 2's FIFO is created with `mkfifo`, and a writer thread opens it after `hold` ms.
- **Everything else is a hypothesis** under the owner's rule. That covers every address outside §0.3,
  every new test's verdict, every mutation's red set, and the decomposition.

### 0.8 · What the plan review changed

One review ran on 2026-09-27, by a separate agent, against the tree at `4cf37c7` and the spec with its
§6.4/§6.5 amendment. It was told the owner's forks and not to re-argue them.

**What it ran**, on its own scratch copy: Task 1 applied as written (the `AgentExecutor` change and
its two tests). Then `:domain:jvmTest :consumer:jvm:test :data:repository:testDebugUnitTest
:feature:launcher:testDebugUnitTest` ran with `--no-build-cache`: 139 tasks executed, exit 0.
- XML counts: `:domain` **457**, `:consumer:jvm` 57, `:data:repository` 394, `:feature:launcher` 202.
- Both new tests passed, in 5–6 ms each.

That is a measurement of the claim "a defaulted `worldContext` changes no existing test". Everything
else it settled by reading.

| # | Finding | Kind | Fixed where |
|---|---|---|---|
| **B1** | `MemoryToolWorker` is built in two more test files (`AgentActingSeamTest:214`, `DoctrineGuardTest:262`): compile-red. Once compiling, `AgentActingSeamTest:432`, `:555` assert `Effected()` for `set_app_alias`, which now reports its outputs | blocking | Task 5 lists both constructions and both assertions (§0.3 P12) |
| **B2** | There are **six** `LauncherViewModel(` constructions — `LauncherViewModelTest:~926` was missing | blocking | Tasks 4 and 6 tables |
| **B3** | A `Failed` case in `SandboxToolContractTest` would be red: the test compares emitted keys with the declared schema, and `Failed` carries none | blocking | Task 7 adds the `Effected` case only |
| **C1** | §10 criterion 5 / §6.2 — "compensation works on `launcher_memory`" — was tested only in pieces; nothing ran the real worker + real catalog + engine + store through one undo | spec coverage | Task 5: two end-to-end tests in `AgentActingSeamTest` (+2 in `:data:repository`), named in `DOC-HMA-3`'s cell |
| m1 | The Blocked card would offer «Отменить» after a budget cut, i.e. a world call while the cut call may still act — F2-1b's own reason | minor | Task 3: `UndoAgentSessionUseCase.review` returns nothing for a `Blocked` session whose last step is `HandedOff`, with a test and M3i |
| m2 | No test asserted a non-empty review for `Failed` / `Blocked`; "Completed only" would survive | minor | Task 3: a positive test, and M3j |
| m3 | The catalog guard's third test checked only the "point back" branch | minor | Task 5: runs with and without a prior target (3 inverses checked) |
| m4 | A re-planned session keeps no link to the stale one | minor | §0.5 names it, with the measured cost of a link |
| m5 | Missing imports in three test sketches; one unused import | minor | Tasks 4, 6, 7 |
| m6 | `tools/gate.sh:19` also carries "1485 since A4′ phase 1" | minor | Task 8 Step 2 |
| m7 | `SandboxToolSourceTest`'s expected list holds id strings | minor | Task 7 |

**The review confirmed the following by reading:**
- Every quoted anchor, and every plant being unique after its task's edits.
- The helper names and type signatures.
- M2d's hedged set — exactly the three tests that re-read the session through Room.
- The "stays green" predictions (M3e, M4c).
- P5.
- The composable-in-inline-lambda call.
- The gate arithmetic.

**Net effect on the numbers:**
- `:domain` +2 (Task 3, m1 and m2);
- `:data:repository` +2 (Task 5, C1).

The chain is 1485 → 1488 → 1497 → **1510** → **1520** → **1535** → **1543** → **1556**.

**Not independently reviewed, said rather than implied.** The fixes above were written after the
review and were not re-reviewed. Under the owner's rule of 2026-09-26, execution's `mutation-prover`
checks them, and a mismatch there is a finding.

---

## Task 1: R14 — the engine stops waiting for a blocked world call, and a cut ends the plan

**Spec:** §3 0.3, §10 criterion 3; owner forks F2-1 and F2-1b. **Modules:** `:domain`, `:app`,
`:consumer:jvm`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt` — imports
  (`:14-16`), constructor (`:57-61`), the call site and the tail of `perform` (`:206-254`).
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt` —
  `RuntimeBudget`'s KDoc (`:23-68`).
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt` —
  `HandedOff`'s second producer bullet.
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` — `provideAgentExecutor`
  (`:265-275`) and two imports.
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt` —
  constructor (`:50-54`), `:63`; `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/Main.kt:24-26`.
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt` — append two
  tests after `the shipped budget bounds a step by wall-clock time` (`:357-360`), plus three imports.
- Create: `app/src/test/java/com/sidr/launcher/agent/AgentExecutorWiringTest.kt`.

**Interfaces.**
- Produces:
  ```kotlin
  class AgentExecutor(
      registry: ToolRegistry,
      toolExecutor: ToolExecutor,
      budget: RuntimeBudget = RuntimeBudget.Default,
      worldContext: CoroutineContext = EmptyCoroutineContext,
  )
  // AgentProvidesModule
  fun provideAgentExecutor(registry: ToolRegistry, toolExecutor: ToolExecutor,
                           @IoDispatcher ioDispatcher: CoroutineDispatcher): AgentExecutor
  // ConsoleHarness(root, out, ask, worldContext: CoroutineContext = EmptyCoroutineContext)
  ```
- Behaviour: a call cut by the budget is observed `HandedOff`, and the session ends `Blocked`
  (`TraceEvent.SessionEnded(Blocked)` last).

- [ ] **Step 1: write the failing tests.** In `AgentExecutorTest`, add imports
  `java.util.concurrent.CountDownLatch`, `kotlinx.coroutines.Dispatchers` and
  `kotlinx.coroutines.test.currentTime`. Then append after the test
  `` `the shipped budget bounds a step by wall-clock time` ``:

```kotlin

    /**
     * R14, closed by owner fork F2-1 (plan §0.1). A call parked in NON-suspending code — the shape of
     * every binder call Android makes — is interrupted by neither `withTimeout` nor
     * `runInterruptible` (plan §0.2, probe 2: an uninterruptible block waited out its full time under
     * both). What the budget CAN do is stop waiting for it, and that is what this pins: the engine is
     * back at exactly the budget in virtual time while the worker thread is still parked.
     *
     * The JUnit timeout is what makes the mutation that puts the call back on the caller's thread
     * (M1a) FAIL instead of hang: the latch then parks the test's own thread, and only JUnit can end it.
     */
    @Test(timeout = 20_000)
    fun `a call blocked outside any suspension point is abandoned at the budget, not waited for`() = runTest {
        val release = CountDownLatch(1)
        val parked = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                release.await()
                return notInstalled("вельтрикс")
            }
        }
        try {
            val executor = AgentExecutor(
                registry,
                parked,
                RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2, maxStepWallClockMs = 1_000),
                worldContext = Dispatchers.IO,
            )

            val after = executor.advance(session("вельтрикс"))

            assertEquals(ToolResult.HandedOff(), after.observations[0])
            assertEquals(1_000L, currentTime)
        } finally {
            release.countDown()
        }
    }

    /**
     * Owner fork F2-1b. A call the engine stopped waiting for may still be acting, so the plan does not
     * start another: two world calls in flight at once would break the order the plan promised. The
     * two steps below do not depend on each other — the only shape in which the second could run at
     * all — and the second must never be called.
     */
    @Test
    fun `a step cut by the clock ends the plan, so no second call starts while the first may still act`() = runTest {
        val calls = mutableListOf<ToolId>()
        val firstHangs = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                calls += invocation.id
                if (calls.size == 1) delay(Long.MAX_VALUE / 2)
                return ToolResult.Effected()
            }
        }
        val independent = ExecutionPlan(
            listOf(
                planForMissingApp("вельтрикс").steps[0],
                planForMissingApp("лумора").steps[0].copy(index = 1),
            ),
        )
        val executor = AgentExecutor(
            registry,
            firstHangs,
            RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2, maxStepWallClockMs = 1_000),
        )

        var current = executor.advance(session("вельтрикс").copy(plan = independent))
        current = executor.advance(current)

        assertEquals(ExecutionState.Blocked, current.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), calls)
        assertEquals(TraceEvent.SessionEnded(ExecutionState.Blocked), current.trace.events.last())
    }
```

Create `app/src/test/java/com/sidr/launcher/agent/AgentExecutorWiringTest.kt`:

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A4′ phase 2, Task 1 (owner fork F2-1). `AgentExecutorTest` proves the ENGINE stops waiting when it
 * is handed a real context; this proves the composition root HANDS it one. The production provider is
 * called directly — the same seam `DynamicToolLabelWiringTest` uses — with the IO dispatcher Hilt binds
 * under `@IoDispatcher`.
 *
 * Why it matters on Android specifically: the agent runs on `viewModelScope` (Main), and a Tier-0 or
 * shortcut call reaches `startActivity` / `startShortcut` synchronously (plan §0.3 P1). A provider
 * that forgot the context would put every world call back on the main thread, where the budget's own
 * timeout is a Handler callback that cannot fire while the thread is held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentExecutorWiringTest {

    @Test(timeout = 20_000)
    fun `the shipped executor stops waiting for a blocked world call at the budget`() = runTest {
        val release = CountDownLatch(1)
        val parked = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                release.await()
                return ToolResult.Effected()
            }
        }
        val session = AgentSession(
            id = AgentSessionId("wiring"),
            goal = AgentGoal("открой лумора", GoalShape.AppNotInstalled("лумора")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("лумора"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
        try {
            val executor = AgentProvidesModule.provideAgentExecutor(
                FakeToolRegistry.withA0Tools(),
                parked,
                Dispatchers.IO,
            )

            val after = executor.advance(session)

            assertEquals(ToolResult.HandedOff(), after.observations[0])
            assertEquals(RuntimeBudget.Default.maxStepWallClockMs, currentTime)
        } finally {
            release.countDown()
        }
    }
}
```

- [ ] **Step 2: run — expect compile-red in both modules.**
  - `tools/gate.sh --scoped :domain:jvmTest` is expected to fail compiling: `Unresolved reference
    'worldContext'` or `No parameter with name 'worldContext'` in `AgentExecutorTest`.
  - `tools/gate.sh --scoped :app:testDebugUnitTest` is expected to show a too-many-arguments error
    on `provideAgentExecutor`.

- [ ] **Step 3: implement.**

In `AgentExecutor.kt`, replace the imports `:14-16`:
```kotlin
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
```
with:
```kotlin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
```

Replace the constructor `:57-61`:
```kotlin
class AgentExecutor(
    private val registry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val budget: RuntimeBudget = RuntimeBudget.Default,
) {
```
with:
```kotlin
class AgentExecutor(
    private val registry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val budget: RuntimeBudget = RuntimeBudget.Default,
    /**
     * Where the one call to the world runs (A4′ phase 2, owner fork F2-1). Production passes an IO
     * dispatcher — `:app` its `@IoDispatcher`, `:consumer:jvm` `Dispatchers.IO` in `Main.kt` — so a
     * call parked in native code holds a pool thread, not the thread driving the engine (on Android,
     * the main thread). The default keeps the call on the caller's dispatcher, and every test that
     * drives this engine under `runTest` needs exactly that: there, a call on a real dispatcher loses
     * to virtual time and is cut at once (plan §0.2, probe 3).
     */
    private val worldContext: CoroutineContext = EmptyCoroutineContext,
) {
```

Replace `:225-254` — from `        val result = try {` down to the closing `}` of `perform`:
```kotlin
        val result = try {
            withTimeout(budget.maxStepWallClockMs) { toolExecutor.invoke(resolved) }
        } catch (e: TimeoutCancellationException) {
```
…through…
```kotlin
        return if (result is ToolResult.Failed && observed.trailingFailures() >= budget.maxConsecutiveFailures) {
            observed.ended(ExecutionState.Failed)
        } else {
            observed
        }
    }
```
with:
```kotlin
        //
        // A4′ phase 2 (owner fork F2-1, R14): the call runs in its OWN scope on [worldContext], and
        // the engine waits for it only until the budget. A call parked in non-suspending code — every
        // binder call Android makes, every JVM file operation — can be interrupted by nothing
        // (measured, plan §0.2 probe 2); it can only stop being waited for. `withTimeout` around a call
        // in the caller's own context did neither: it sat out the whole block and then kept or
        // discarded the result depending on the call's shape.
        val call = CoroutineScope(currentCoroutineContext().minusKey(Job) + worldContext + SupervisorJob())
            .async { toolExecutor.invoke(resolved) }
        var cut = false
        val result = try {
            withTimeout(budget.maxStepWallClockMs) { call.await() }
        } catch (e: TimeoutCancellationException) {
            // FIRST, and the order IS the behaviour: TimeoutCancellationException IS a
            // CancellationException, so placing this after the rethrow below would send the timeout
            // up as parent cancellation and kill the session instead of bounding the step.
            //
            // `HandedOff` rather than `Failed`, for the same reason `uninstall_app` reports it: the
            // call was made and we stopped waiting. The side effect may have happened — for a parked
            // call it may still happen after this line — and `Failed` would claim knowledge in the
            // other direction.
            cut = true
            ToolResult.HandedOff()
        } catch (e: CancellationException) {
            throw e // never swallow parent cancellation
        } catch (e: Exception) {
            ToolResult.Failed(CommandFailure.Generic)
        } finally {
            // A no-op for a call that finished. For one we stopped waiting for: a suspending call
            // stops here; a parked one runs to its end on its own thread and its result is dropped.
            call.cancel()
        }

        val observed = session
            .record(TraceEvent.ToolObserved(invoked.index, result))
            .copy(
                cursor = invoked.index + 1,
                observations = session.observations + (invoked.index to result),
            )

        return when {
            // Owner fork F2-1b: a cut call may still be acting. Starting another world call now would
            // put two in flight and break the order the plan promised, so the plan stops here.
            cut -> observed.ended(ExecutionState.Blocked)
            result is ToolResult.Failed && observed.trailingFailures() >= budget.maxConsecutiveFailures ->
                observed.ended(ExecutionState.Failed)
            else -> observed
        }
    }
```

In `AgentSession.kt`, replace `RuntimeBudget`'s KDoc body from ` * **The cut is COOPERATIVE, not preemptive` down to the end of the paragraph
` * phase close (controller ruling R14), not decided by this task.` (`:31-60`) with:
```kotlin
 * **Since A4′ phase 2 the cut is the engine's, not the tool's** (owner fork F2-1, R14 closed).
 * `AgentExecutor` runs the one world call in its own scope on an injected context and waits only until
 * this budget; then it records [ToolResult.HandedOff] and ends the plan `Blocked` (fork F2-1b — a
 * call it stopped waiting for may still be acting, so no second call starts). Measured on JVM before
 * the choice (plan §0.2, probe 2): with a 2 000 ms block and a 300 ms budget, an interruptible and an
 * uninterruptible block both came back at ~300 ms under this scheme; under the previous
 * `withTimeout`-only shape both took the full 2 000 ms, and `runInterruptible` cut only the
 * interruptible one. Binder calls were not measured — the phone was off-limits — and are, by
 * reading, of the uninterruptible kind.
 *
 * **What it still does not do, named:** it does not STOP the call. A parked call runs on in the
 * background and may complete its act after `HandedOff` is recorded; that is exactly what
 * `HandedOff` says. It bounds how long the ENGINE waits, not how long the world takes.
```
(The paragraph that follows, «The default is an order of magnitude above the slowest path…», stays.)

In `ToolInvocation.kt`, replace the bullet
```kotlin
     *  - a step cut by [com.sidr.launcher.domain.agent.RuntimeBudget.maxStepWallClockMs] — the call
     *    was made, we stopped waiting, and the side effect may well have happened.
```
with:
```kotlin
     *  - a step cut by [com.sidr.launcher.domain.agent.RuntimeBudget.maxStepWallClockMs] — the call
     *    was made, we stopped waiting, and the side effect may well have happened (or, for a call
     *    parked in native code, may still happen: since A4′ phase 2 the engine stops waiting rather
     *    than waiting the call out, and ends the plan there).
```

In `AgentProvidesModule.kt`, add imports `com.sidr.launcher.core.common.di.IoDispatcher` and
`kotlinx.coroutines.CoroutineDispatcher`, and replace `provideAgentExecutor`:
```kotlin
    fun provideAgentExecutor(
        registry: ToolRegistry,
        toolExecutor: ToolExecutor,
    ): AgentExecutor = AgentExecutor(
        registry = registry,
        toolExecutor = toolExecutor,
        budget = RuntimeBudget.Default,
    )
```
with:
```kotlin
    fun provideAgentExecutor(
        registry: ToolRegistry,
        toolExecutor: ToolExecutor,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AgentExecutor = AgentExecutor(
        registry = registry,
        toolExecutor = toolExecutor,
        budget = RuntimeBudget.Default,
        // A4′ phase 2 (F2-1): world calls leave the thread that drives the engine — on Android the
        // main thread — so a parked binder call holds a pool thread and the budget can still fire.
        worldContext = ioDispatcher,
    )
```

In `ConsoleHarness.kt`, add imports `kotlin.coroutines.CoroutineContext` and
`kotlin.coroutines.EmptyCoroutineContext`. Replace
```kotlin
class ConsoleHarness(
    root: Path,
    private val out: (String) -> Unit,
    private val ask: () -> String?,
) {
```
with:
```kotlin
class ConsoleHarness(
    root: Path,
    private val out: (String) -> Unit,
    private val ask: () -> String?,
    /**
     * Where the sandbox's file calls run (A4′ phase 2, F2-1). `Main.kt` passes `Dispatchers.IO`; the
     * default keeps them on the caller's dispatcher, which is what the tests driving this harness under
     * `runTest` need (plan §0.2, probe 3). That leaves `Main.kt`'s one line the only unproven wiring
     * here — named, not tested.
     */
    worldContext: CoroutineContext = EmptyCoroutineContext,
) {
```
and replace `    private val executor = AgentExecutor(registry, federation.executor)` with
`    private val executor = AgentExecutor(registry, federation.executor, worldContext = worldContext)`.

In `Main.kt`, add `import kotlinx.coroutines.Dispatchers` and replace
`        ConsoleHarness(root = root, out = ::println, ask = ::readlnOrNull).run(goal)` with
`        ConsoleHarness(root = root, out = ::println, ask = ::readlnOrNull, worldContext = Dispatchers.IO).run(goal)`.

- [ ] **Step 4: run — expect green.**
  - `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=457 failures=0`.
  - `tools/gate.sh --scoped :app:testDebugUnitTest` → `app tests=62 failures=0`.

- [ ] **Step 5: mutations — three.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M1a | `AgentExecutor.kt` · `currentCoroutineContext().minusKey(Job) + worldContext + SupervisorJob()` → `currentCoroutineContext().minusKey(Job) + SupervisorJob()` | the call leaves the caller's thread | `:domain:jvmTest` — `AgentExecutorTest > a call blocked outside any suspension point is abandoned at the budget, not waited for` **only**, as a ~20 s JUnit timeout |
| M1b | `AgentExecutor.kt` · `cut -> observed.ended(ExecutionState.Blocked)` → `cut -> observed` | F2-1b: a cut ends the plan | `:domain:jvmTest` — `AgentExecutorTest > a step cut by the clock ends the plan, so no second call starts while the first may still act` **only** |
| M1c | `AgentProvidesModule.kt` · `        worldContext = ioDispatcher,\n` → `` (the line removed) | the composition root hands the context over | `:app:testDebugUnitTest` — `AgentExecutorWiringTest > the shipped executor stops waiting for a blocked world call at the budget` **only**, as a ~20 s JUnit timeout. `ioDispatcher` becomes an unused parameter — a warning, not an error |

- M1a's plant leaves `worldContext` unused. That is a warning, and this build sets no
  `allWarningsAsErrors`.
- Existing wall-clock tests stay green under M1a, because they run on the default context. That is
  the point of the default.

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1488`.
  - Per module: `:domain` **457** (+2), `:app` **62** (+1), the rest unchanged.

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src app/src consumer/jvm/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): R14 — движок перестаёт ждать заблокированный вызов, срез останавливает план

Развилка владельца F2-1: вызов мира идёт в собственной области на внедрённом
контексте (Android — @IoDispatcher, JVM — Dispatchers.IO в Main.kt), движок
ждёт его только до бюджета и пишет HandedOff. Замерено до выбора (план §0.2):
непрерываемый блок 2 с при бюджете 300 мс возвращается за ~300 мс только так;
runInterruptible его пересидел. Попутно вызовы агента уходят с главного потока.

F2-1b: срезанный вызов может ещё действовать, поэтому план заканчивается
Blocked — второй вызов мира не стартует, пока первый, может быть, в полёте.

Умолчание — контекст вызывающего: под runTest вызов на настоящем диспетчере
проигрывает виртуальному времени (зонд 3). Мутации M1a–M1c — по одной на
свойство; M1a и M1c краснеют таймаутом JUnit.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: more than one reason to stop — the checkpoint carries a set

**Spec:** §6.1, §3 0.2(2); owner fork F2-2. **Modules:** `:domain`, `:data:repository`,
`:consumer:jvm`, `:feature:launcher`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt:21`
  (`ConsentCheckpoint`).
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/trace/TraceEvent.kt:24`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt` — `:119`
  and `checkpointFor` with its KDoc (`:295-337`).
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt`
  — `:193`, `:364`, after `readConsentReason` (`:444-449`).
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionDto.kt:69`;
  `SessionMapper.kt:167`, `:187-190`; `ConsoleHarness.kt:210`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt:96`;
  `AgentSessionPresentation.kt:388-401` (`gateType` and its KDoc).
- Test (edits, §0.3 P12): `AgentExecutorTest:101-104`;
  `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentActingSeamTest.kt:326-330`,
  `:371-384`; `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt:78-84`;
  `RoomAgentSessionStoreTest.kt:97`; `AgentSessionMappersTest.kt:170-176`, `:191`;
  `JvmAgentSessionStoreTest.kt:133`; `AgentSessionSurfaceProvenanceTest.kt:262`, `:358`.
- Test (new): `AgentExecutorTest` (+4), `AgentSessionMappersTest` (+2), `JvmAgentSessionStoreTest`
  (+1).
- Create: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionGateChipTest.kt`
  (+2).

**Interfaces.**
- Produces:
  ```kotlin
  data class ConsentCheckpoint(val stepIndex: Int, val reasons: Set<ConsentReason>)
  data class TraceEvent.ConsentRequested(val index: Int, val reasons: Set<ConsentReason>)
  internal fun Set<ConsentReason>.gateType(): SidrActionGateType   // :feature:launcher
  ```
- On disk:
  - Room `agent_trace_event.detail` for `ConsentRequested` is the reasons' names in
    **declaration order**, comma-joined (`"RISK_LEVEL,DURABLE_EFFECT"`). A pre-phase-2 row holds one
    name and reads back as a one-reason set.
  - JVM `TraceDto` gains `reasons: List<String>? = null`. A file carrying only `reason` reads back as
    a one-reason set.

- [ ] **Step 1: write the failing tests and adapt the compile-only sites.**

In `AgentExecutorTest`, add imports `com.sidr.launcher.domain.permission.PermissionFeature`,
`com.sidr.launcher.domain.tool.ToolDescriptor`, `com.sidr.launcher.domain.tool.ToolDurability`,
`com.sidr.launcher.domain.tool.ToolEffect` and `com.sidr.launcher.domain.tool.ToolLevels`. Replace
`:101-104`:
```kotlin
        assertTrue(
            afterSecond.trace.events.any {
                it is TraceEvent.ConsentRequested && it.reason == ConsentReason.RISK_LEVEL
            },
        )
```
with:
```kotlin
        assertTrue(
            afterSecond.trace.events.any {
                it is TraceEvent.ConsentRequested && ConsentReason.RISK_LEVEL in it.reasons
            },
        )
```
Append before `private fun unconditionalFallbackPlan()`:
```kotlin
    // --- More than one reason to stop (A4′ phase 2, spec §6.1, owner fork F2-2) -----------------

    private val quill = ToolId("fixture_quill")     // CONFIRM + DURABLE — uninstall_app's shape
    private val ledger = ToolId("fixture_ledger")   // SAFE + DURABLE — no such tool ships yet
    private val parcel = ToolId("fixture_parcel")   // CONFIRM + a declared permission gate

    private fun fixtureTool(
        id: ToolId,
        risk: ActionRiskLevel,
        durability: ToolDurability,
        gate: PermissionFeature? = null,
    ) = ToolDescriptor(
        id = id,
        level = ToolLevels.SANDBOX,
        effect = ToolEffect.LOCAL,
        risk = risk,
        durability = durability,
        permissionGate = gate,
    )

    private val stops = FakeToolRegistry(
        listOf(
            fixtureTool(quill, ActionRiskLevel.CONFIRM, ToolDurability.DURABLE),
            fixtureTool(ledger, ActionRiskLevel.SAFE, ToolDurability.DURABLE),
            fixtureTool(parcel, ActionRiskLevel.CONFIRM, ToolDurability.TRANSIENT, PermissionFeature.WALLPAPER),
        ),
    )

    private fun oneStep(id: ToolId, risk: ActionRiskLevel) = session("вельтрикс").copy(
        plan = ExecutionPlan(
            listOf(PlanStep(0, ToolInvocation(id), risk, StepPrecondition.None, StepRationale.GOAL_DIRECT)),
        ),
    )

    /**
     * `DOC-HMA-3`, first half: an irreversible step is marked BEFORE it runs. Until phase 2 the
     * `DURABLE_EFFECT` branch sat below `requiresConsent(risk)` and was never reached for exactly the
     * risk band where irreversibility matters most (spec §3 0.2(2); A0.5's named finding).
     */
    @Test
    fun `a CONFIRM step that is also DURABLE records both reasons before it runs`() = runTest {
        val tools = FakeToolExecutor(emptyList())

        val after = AgentExecutor(stops, tools, budget).advance(oneStep(quill, ActionRiskLevel.CONFIRM))

        assertEquals(ExecutionState.AwaitingConsent, after.state)
        assertEquals(
            listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT))),
            after.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
        assertTrue("nothing may run before consent", tools.invocations.isEmpty())
    }

    @Test
    fun `a SAFE step that is DURABLE stops for its durability alone`() = runTest {
        val tools = FakeToolExecutor(emptyList())

        val after = AgentExecutor(stops, tools, budget).advance(oneStep(ledger, ActionRiskLevel.SAFE))

        assertEquals(
            listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.DURABLE_EFFECT))),
            after.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
        assertTrue(tools.invocations.isEmpty())
    }

    @Test
    fun `a declared permission gate is recorded even when risk already stops the step`() = runTest {
        val after = AgentExecutor(stops, FakeToolExecutor(emptyList()), budget)
            .advance(oneStep(parcel, ActionRiskLevel.CONFIRM))

        assertEquals(
            listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.MISSING_PERMISSION))),
            after.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
    }

    /**
     * `RISK_RAISED` is a claim about a transition, so it needs a step that ran before this one. A0's
     * store step is the shape that has one: `launch_app` (SAFE) ran, `play_store_search` (CONFIRM) is
     * next. The first-step tests above pin the other half by their exact sets: nothing ran before
     * them, so no rise is recorded.
     */
    @Test
    fun `the A0 store step records the rise in risk as well as the risk level`() = runTest {
        val executor = AgentExecutor(registry, FakeToolExecutor(listOf(notInstalled())), budget)

        val atGate = executor.advance(executor.advance(session()))

        assertEquals(
            listOf(TraceEvent.ConsentRequested(1, setOf(ConsentReason.RISK_LEVEL, ConsentReason.RISK_RAISED))),
            atGate.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
    }
```

**Compile-only adaptations.** Replace each single-reason constructor as follows; nothing else about
those tests changes.

| File | Replace | With |
|---|---|---|
| `RoomAgentSessionStoreTest.kt:97` | `TraceEvent.ConsentRequested(1, ConsentReason.RISK_LEVEL),` | `TraceEvent.ConsentRequested(1, setOf(ConsentReason.RISK_LEVEL)),` |
| `AgentSessionMappersTest.kt:173` | `TraceEvent.ConsentRequested(0, reason),` | `TraceEvent.ConsentRequested(0, setOf(reason)),` |
| `AgentSessionMappersTest.kt:191` | `TraceEvent.ConsentRequested(1, ConsentReason.RISK_LEVEL),` | `TraceEvent.ConsentRequested(1, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT)),` — the sample now round-trips a multi-reason event |
| `JvmAgentSessionStoreTest.kt:133` | `TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL),` | `TraceEvent.ConsentRequested(2, setOf(ConsentReason.RISK_LEVEL, ConsentReason.RISK_RAISED, ConsentReason.DURABLE_EFFECT)),` |
| `AgentSessionSurfaceProvenanceTest.kt:262` | `trace = listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL)),` | `trace = listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL))),` |
| `AgentSessionSurfaceProvenanceTest.kt:358` | `trace = ExecutionTrace(listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL))),` | `trace = ExecutionTrace(listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL)))),` |

**Assertions the owner's decision changes** (F2-2; each a behaviour change, and its comment says so):
- `AgentActingSeamTest.kt:326-330`. Replace
  ```kotlin
              "the stop must be recorded as a risk-level checkpoint on step 0",
              listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL)),
  ```
  with
  ```kotlin
              "the stop must carry every reason on step 0 — risk, and irreversibility (A4′ phase 2, F2-2)",
              listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT))),
  ```
- `AgentActingSeamTest.kt:372`. Replace
  `TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL),` with
  `TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT)),`.
- `AgentLoopTest.kt:78-84`. Replace
  ```kotlin
            // `checkpointFor`'s first branch since 2026-08-23 is `maxOf(PlanStep.risk, registry.risk)
            // >= CONFIRM` (A0 finding F2). DANGEROUS takes it either way, and the reason is RISK_LEVEL
            // rather than DURABLE_EFFECT — spec §11.3's recorded finding, not a defect to fix here.
            "the gate names the risk level, not durability — DANGEROUS takes checkpointFor's first branch",
            paused.trace.events.contains(TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL)),
  ```
  with
  ```kotlin
            // A0.5 recorded that DANGEROUS took checkpointFor's first branch, so the gate named the
            // risk and never the durability (spec §11.3). A4′ phase 2 (F2-2) records every reason:
            // risk, the rise from the two SAFE steps that ran, and irreversibility.
            "the gate names every reason delete_file stops for",
            paused.trace.events.contains(
                TraceEvent.ConsentRequested(
                    2,
                    setOf(ConsentReason.RISK_LEVEL, ConsentReason.RISK_RAISED, ConsentReason.DURABLE_EFFECT),
                ),
            ),
  ```

In `AgentSessionMappersTest`, append after
`` `every ConsentReason round-trips through a ConsentRequested event` ``:
```kotlin

    /**
     * One set, one row: the reasons are written in declaration order whatever order the set was built
     * in, so the same checkpoint never reads as two different rows. A row written before A4′ phase 2
     * holds one name, and `every ConsentReason round-trips…` above is what reads it back.
     */
    @Test
    fun `a ConsentRequested row lists its reasons in declaration order`() {
        val session = read().copy(
            trace = ExecutionTrace(
                listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.DURABLE_EFFECT, ConsentReason.RISK_LEVEL))),
            ),
        )

        assertEquals(
            "RISK_LEVEL,DURABLE_EFFECT",
            AgentSessionMappers.toTraceEntities(session, now = 1L).single().detail,
        )
    }

    @Test
    fun `a ConsentRequested row with no reason at all is corrupt, not an empty checkpoint`() {
        assertThrows(CorruptAgentRowException::class.java) {
            read(trace = listOf(traceRow("ConsentRequested", 0, "")))
        }
    }
```
(Add `import com.sidr.launcher.domain.trace.ExecutionTrace` if the file lacks it.)

In `JvmAgentSessionStoreTest`, append:
```kotlin

    /**
     * A file written before A4′ phase 2 carries `"reason": "RISK_LEVEL"` where it now carries a
     * `reasons` list. The fixture is produced by saving the new form and rewriting it to the old one,
     * so the test cannot pass on a file that never contained the new form.
     */
    @Test
    fun `a session file written before reasons were a list still reads its one reason`() = runTest {
        store().save(
            session(
                state = ExecutionState.AwaitingConsent,
                trace = ExecutionTrace(
                    listOf(TraceEvent.PlanCreated(3), TraceEvent.ConsentRequested(2, setOf(ConsentReason.RISK_LEVEL))),
                ),
            ),
        )
        val file = temp.root.toPath().resolve("session.json")
        val current = Files.readString(file)
        val legacy = Regex(""""reasons"\s*:\s*\[\s*"RISK_LEVEL"\s*]""").replace(current, """"reason": "RISK_LEVEL"""")
        check(legacy != current) { "the fixture must contain the new form before it is rewritten:\n$current" }
        Files.writeString(file, legacy)

        val read = store().active().value()!!

        assertEquals(
            listOf(TraceEvent.ConsentRequested(2, setOf(ConsentReason.RISK_LEVEL))),
            read.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
    }
```

Create `AgentSessionGateChipTest.kt`:
```kotlin
package com.sidr.launcher.feature.launcher.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, Task 2 (owner fork F2-2). `DOC-HMA-3`'s first half reaches the user HERE: before an
 * irreversible step runs, the gate announces it as `DESTRUCTIVE` — a `SidrActionGateType` that already
 * existed (and tones the surface as a risk) but that no agent step could reach, because the checkpoint
 * carried one reason and risk always won. The chip labels are locked machine vocabulary
 * (`ui_gate_type_*`), identical in every locale, and share no substring with each other or with the
 * `Confirm` button (case differs), so a whole-text match separates all three.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionGateChipTest {

    @get:Rule val compose = createComposeRule()

    private fun awaiting(reasons: Set<ConsentReason>) = AgentSession(
        id = AgentSessionId("s-chip"),
        goal = AgentGoal("удали лумору", GoalShape.Free("удали лумору")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(
                        ToolId("uninstall_app"),
                        mapOf(
                            "app" to ArgSource.Literal("net.fixture.quill"),
                            "app_label" to ArgSource.Literal("Lumora"),
                        ),
                    ),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = 0,
        state = ExecutionState.AwaitingConsent,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(listOf(TraceEvent.ConsentRequested(0, reasons))),
    )

    private fun render(session: AgentSession) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = session,
                    toolProvenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a step that cannot be undone is announced as destructive before it runs`() {
        render(awaiting(setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT)))

        compose.onNodeWithText("DESTRUCTIVE").assertExists()
        compose.onNodeWithText("CONFIRM").assertDoesNotExist()
    }

    @Test
    fun `a step stopped for its risk alone is announced as a confirmation`() {
        render(awaiting(setOf(ConsentReason.RISK_LEVEL)))

        compose.onNodeWithText("CONFIRM").assertExists()
        compose.onNodeWithText("DESTRUCTIVE").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: run — expect compile-red**, per module, in order:
  - `:domain:jvmTest`: `Unresolved reference 'reasons'` (`AgentExecutorTest`), plus
    argument-type-mismatch errors at the `setOf(...)` constructors.
  - Then, once `:domain` compiles, the same class of error in `:data:repository`, `:consumer:jvm` and
    `:feature:launcher`. Do not chase the downstream reds before Step 3.

- [ ] **Step 3: implement.**

`AgentSession.kt:21` — replace `data class ConsentCheckpoint(val stepIndex: Int, val reason: ConsentReason)` with:
```kotlin
/**
 * Every reason the step at [stepIndex] must stop for — not the first one found (A4′ phase 2, spec
 * §6.1, owner fork F2-2). One stop is one checkpoint and one trace event, however many reasons it has;
 * never empty, because a step with no reason to stop gets no checkpoint at all.
 */
data class ConsentCheckpoint(val stepIndex: Int, val reasons: Set<ConsentReason>)
```

`TraceEvent.kt:24` — replace
`    data class ConsentRequested(val index: Int, val reason: ConsentReason) : TraceEvent` with
`    data class ConsentRequested(val index: Int, val reasons: Set<ConsentReason>) : TraceEvent`.

`AgentExecutor.kt:119` — replace `                .record(TraceEvent.ConsentRequested(next.index, checkpoint.reason))`
with `                .record(TraceEvent.ConsentRequested(next.index, checkpoint.reasons))`.

`AgentExecutor.kt:295-337` — replace `checkpointFor`'s KDoc and body (from `    /**\n     * Four triggers, all fail-safe.` to the function's closing `}`) with:
```kotlin
    /**
     * Four triggers, all fail-safe — and since A4′ phase 2 (spec §6.1, owner fork F2-2) **every one
     * that holds is recorded**, not the first. Until then this was a `when` whose first branch,
     * `requiresConsent(risk)`, shadowed the rest: `uninstall_app` (CONFIRM and DURABLE) stopped for
     * its risk and its irreversibility was never written down — exactly the risk band where it matters
     * (A0.5's finding, spec §3 0.2(2)). Reordering the branches was not a fix: A1″'s M1a went red
     * precisely because the stop's reason is load-bearing.
     *
     * [ConsentReason.MISSING_PERMISSION] fires whenever a tool merely *declares* a gate: consulting
     * the real grant state would put `PermissionChecker` inside the engine, and stopping unconditionally
     * is the conservative half of that.
     *
     * [ConsentReason.RISK_RAISED] is a claim about a transition, so it needs a step that already ran
     * before this one. Measured against a default of SAFE, it would stamp every first CONFIRM step with
     * a "rise" from a step that never existed.
     *
     * **Every input is read through [effectiveRisk], not off the plan** (review finding F2,
     * 2026-08-23): a plan written when a tool was `SAFE` must still stop after a build that declared
     * it `CONFIRM`, and the higher of plan and registry closes that in the direction that cannot be
     * wrong.
     *
     * Coverage (D11, `§HANDOFF`): since phase 2, `AgentExecutorTest` builds a descriptor with a gate
     * and one that is `DURABLE`, and drives the A0 store step past a SAFE step that ran — all four
     * triggers are now exercised.
     */
    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {
        val descriptor = registry.find(step.invocation.id)
        val risk = effectiveRisk(step)
        val ran = session.plan.steps
            .filter { it.index < step.index && session.observations.containsKey(it.index) }

        val reasons = buildSet {
            if (requiresConsent(risk)) add(ConsentReason.RISK_LEVEL)
            if (ran.isNotEmpty() && risk > ran.maxOf { effectiveRisk(it) }) add(ConsentReason.RISK_RAISED)
            if (descriptor?.permissionGate != null) add(ConsentReason.MISSING_PERMISSION)
            if (descriptor?.durability == ToolDurability.DURABLE) add(ConsentReason.DURABLE_EFFECT)
        }
        return if (reasons.isEmpty()) null else ConsentCheckpoint(step.index, reasons)
    }
```

`AgentSessionMappers.kt:193` — replace
`                is TraceEvent.ConsentRequested -> event.index to event.reason.name` with
`                is TraceEvent.ConsentRequested -> event.index to encodeConsentReasons(event.reasons)`.

`AgentSessionMappers.kt:364` — replace
`        "ConsentRequested" -> TraceEvent.ConsentRequested(row.requireStepIndex(), readConsentReason(row.requireDetail()))`
with
`        "ConsentRequested" -> TraceEvent.ConsentRequested(row.requireStepIndex(), readConsentReasons(row.requireDetail()))`.

After `readConsentReason`'s closing `}` (`:449`), insert:
```kotlin

    /**
     * Every reason a step stopped for (A4′ phase 2, F2-2), comma-joined in DECLARATION order, so one
     * set is always one row. `detail` is TEXT, so the format widened without a migration: a row
     * written before phase 2 holds one name and reads back as a one-reason set.
     */
    private fun encodeConsentReasons(reasons: Set<ConsentReason>): String =
        ConsentReason.entries.filter { it in reasons }.joinToString(",") { it.name }

    /** An empty or unknown name throws through [readConsentReason] — an empty checkpoint is corrupt. */
    private fun readConsentReasons(raw: String): Set<ConsentReason> =
        raw.split(',').map { readConsentReason(it) }.toSet()
```

`SessionDto.kt:69` — after `    val reason: String? = null,` insert
`    /** Since A4′ phase 2: every reason a `ConsentRequested` stopped for. A file written before carries `reason`. */`
`    val reasons: List<String>? = null,`.

`SessionMapper.kt:167` — replace
`        is TraceEvent.ConsentRequested -> TraceDto("ConsentRequested", index = event.index, reason = event.reason.name)`
with
`        is TraceEvent.ConsentRequested -> TraceDto("ConsentRequested", index = event.index, reasons = ConsentReason.entries.filter { it in event.reasons }.map { it.name })`.

`SessionMapper.kt:187-190` — replace
```kotlin
        "ConsentRequested" -> TraceEvent.ConsentRequested(
            require(dto.index, "ConsentRequested.index"),
            enum(ConsentReason.entries, require(dto.reason, "ConsentRequested.reason"), "ConsentReason"),
        )
```
with
```kotlin
        "ConsentRequested" -> TraceEvent.ConsentRequested(
            require(dto.index, "ConsentRequested.index"),
            // `reasons` since A4′ phase 2; a file written before carries one `reason`.
            (dto.reasons ?: listOf(require(dto.reason, "ConsentRequested.reason")))
                .map { enum(ConsentReason.entries, it, "ConsentReason") }
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("empty ConsentReason list in the persisted session"),
        )
```

`ConsoleHarness.kt:210` — replace `"ConsentRequested(${event.index}, ${event.reason})"` with
`"ConsentRequested(${event.index}, ${event.reasons})"`.

`AgentSessionSurface.kt:96` — replace `                    type = checkpoint.reason.gateType(),` with
`                    type = checkpoint.reasons.gateType(),`.

`AgentSessionPresentation.kt:388-401` — replace the KDoc and function from
`/**\n * Why the engine stopped -> which kind of boundary the gate announces.` through
`    ConsentReason.DURABLE_EFFECT -> SidrActionGateType.ExternalHandoff\n}` with:
```kotlin
/**
 * Why the engine stopped -> which kind of boundary the gate announces. This is a mapping of typed
 * values, NOT of copy: `SidrActionGate` renders the type as its own already-translated risk chip
 * (`ui_gate_type_*`), so the reason survives translation without this task inventing more keys.
 *
 * **A stop can have several reasons** (A4′ phase 2, F2-2), and the chip shows the one that says most
 * about what is at stake: irreversibility first (`DESTRUCTIVE`, which also tones the surface as a
 * risk — `DOC-HMA-3`'s "marked before execution" as the user sees it), then a declared permission,
 * then risk. The trace keeps every reason; the chip is one word. Until phase 2 `DURABLE_EFFECT` mapped
 * to `ExternalHandoff`, which was never reached and would have understated it.
 */
internal fun Set<ConsentReason>.gateType(): SidrActionGateType = when {
    ConsentReason.DURABLE_EFFECT in this -> SidrActionGateType.Destructive
    ConsentReason.MISSING_PERMISSION in this -> SidrActionGateType.Permission
    else -> SidrActionGateType.Confirmation
}
```

- [ ] **Step 4: run — expect green, per module.**
  - `:domain:jvmTest` **461**;
  - `:data:repository:testDebugUnitTest` **396**;
  - `:consumer:jvm:test` **58**;
  - `:feature:launcher:testDebugUnitTest` **204**.

- [ ] **Step 5: mutations — six.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M2a | `AgentExecutor.kt` · `            if (descriptor?.durability == ToolDurability.DURABLE) add(ConsentReason.DURABLE_EFFECT)\n` → `` | durability is recorded | `:domain` — `AgentExecutorTest >` `a CONFIRM step that is also DURABLE records both reasons before it runs`, `a SAFE step that is DURABLE stops for its durability alone`. `:data:repository` — `AgentActingSeamTest >` `a typed uninstall command stops at AwaitingConsent with the resolved package already bound`, `consent granted - the uninstall dispatches the resolved package and the trace records the order`. `:consumer:jvm` — `AgentLoopTest > the loop stops for consent at the risk transition, before the world is touched` |
| M2b | `AgentExecutor.kt` · `return if (reasons.isEmpty()) null else ConsentCheckpoint(step.index, reasons)` → `return if (reasons.isEmpty()) null else ConsentCheckpoint(step.index, setOf(reasons.first()))` | every reason, not the first | `:domain` — `a CONFIRM step that is also DURABLE…`, `a declared permission gate is recorded even when risk already stops the step`, `the A0 store step records the rise in risk as well as the risk level`. `:data:repository` — the same two `AgentActingSeamTest` tests. `:consumer:jvm` — the same `AgentLoopTest` test |
| M2c | `AgentExecutor.kt` · `if (ran.isNotEmpty() && risk > ran.maxOf { effectiveRisk(it) }) add(ConsentReason.RISK_RAISED)` → `if (risk > (ran.maxOfOrNull { effectiveRisk(it) } ?: ActionRiskLevel.SAFE)) add(ConsentReason.RISK_RAISED)` | a rise needs a step that ran | `:domain` — `a CONFIRM step that is also DURABLE…`, `a declared permission gate is recorded…`. `:data:repository` — the same two `AgentActingSeamTest` tests |
| M2d | `AgentSessionMappers.kt` · `raw.split(',').map { readConsentReason(it) }.toSet()` → `setOf(readConsentReason(raw))` | Room reads a multi-reason row | `:data:repository` — `AgentSessionMappersTest > every TraceEvent variant round-trips`. Also every `AgentActingSeamTest` uninstall test that re-reads the stored session: `consent granted…`, `consent refused - the session is cancelled and no intent is ever issued`, `our own package is refused by the worker - after a consent card has already named it` — **a hypothesis about which of them read back from Room; execution settles it**. **Execution (2026-09-27): 7 red, not 4** — the three above plus `AgentAtRestGuardTest >` `Completed leaves nothing at rest`, `Cancelled by a refused consent leaves nothing at rest`, `a session still awaiting consent is deliberately not at rest` (they run A0's launch→store plan through Room, whose store step now records two reasons). `a typed uninstall command stops…` stays green (never reads back). §0.8's "M2d's hedged set confirmed by reading" was wrong on this point |
| M2e | `AgentSessionPresentation.kt` · `    ConsentReason.DURABLE_EFFECT in this -> SidrActionGateType.Destructive` → `    ConsentReason.DURABLE_EFFECT in this -> SidrActionGateType.Confirmation` | the chip is chosen by irreversibility first | `:feature:launcher` — `AgentSessionGateChipTest > a step that cannot be undone is announced as destructive before it runs` **only** |
| M2f | `SessionMapper.kt` · `(dto.reasons ?: listOf(require(dto.reason, "ConsentRequested.reason")))` → `require(dto.reasons, "ConsentRequested.reasons")` | a pre-phase-2 JVM file still reads | `:consumer:jvm` — `JvmAgentSessionStoreTest > a session file written before reasons were a list still reads its one reason` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1497`.
  - Per module: `:domain` **461** (+4), `:data:repository` **396** (+2), `:consumer:jvm` **58** (+1),
    `:feature:launcher` **204** (+2), `:app` 62.

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src data/repository/src consumer/jvm/src feature/launcher/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): у остановки шага — множество причин, необратимость помечена до исполнения

Спека §6.1, развилка владельца F2-2 (вариант A). ConsentCheckpoint и
ConsentRequested несут множество причин: удаление приложения останавливается
за риск И необратимость, и обе записаны до исполнения (DOC-HMA-3, половина
первая). RISK_RAISED — только если до шага что-то исполнилось.

На диске без миграции: Room пишет имена через запятую в порядке объявления
(старая строка с одним именем читается множеством из одного), JVM — список
reasons с чтением старого поля reason. На карточке удаления чип CONFIRM
сменяется на DESTRUCTIVE — тип уже был в DS, до него не доходил ни один шаг.

Ожидания трёх тестов изменены решением владельца (AgentActingSeamTest ×2,
AgentLoopTest). Мутации M2a–M2f.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: compensation — the port, the derivation, and an undo that runs as a new session

**Spec:** §6.2, §6.3, §10 criterion 5; owner fork F2-3.3. **Modules:** `:domain`, `:data:repository`
(one decoder line), `:app` (one guard).

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInverses.kt`.
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/Compensation.kt`.
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/UndoAgentSessionUseCase.kt`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt:16-17`
  (`StepRationale`), `:74` (a KDoc address).
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/StartAgentSessionUseCase.kt:31-66`.
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt:430`.
- Create tests: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CompensationTest.kt` (6);
  `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/UndoAgentSessionUseCaseTest.kt` (5).
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentSessionUseCasesTest.kt` (+1).
- Create: `app/src/test/java/com/sidr/launcher/agent/CompensationOutsideTheEngineGuardTest.kt` (1).

**Interfaces.**
- Produces:
  ```kotlin
  // domain/tool
  sealed interface Undo {
      data class By(val invocation: ToolInvocation) : Undo
      data object NotUndoable : Undo
      data object NothingToUndo : Undo
  }
  fun interface ToolInverses { fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo }
  // domain/agent
  enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION }
  data class StepUndo(val stepIndex: Int, val undo: Undo)
  object Compensation {
      fun review(session: AgentSession, inverses: ToolInverses): List<StepUndo>
      fun plan(review: List<StepUndo>, registry: ToolRegistry): ExecutionPlan?
      fun isUndo(plan: ExecutionPlan): Boolean
  }
  class UndoAgentSessionUseCase(start: StartAgentSessionUseCase, inverses: ToolInverses, registry: ToolRegistry) {
      fun review(session: AgentSession): List<StepUndo>
      suspend fun undo(finished: AgentSession): OperationResult<AgentSession?>
  }
  // StartAgentSessionUseCase — start(goal) unchanged; two new members:
  suspend fun plan(goal: AgentGoal): ExecutionPlan?
  suspend fun startPlanned(goal: AgentGoal, plan: ExecutionPlan): OperationResult<AgentSession?>
  ```
- Consumed by Tasks 4, 5, 6 and 7.

- [ ] **Step 1: write the failing tests.**

Create `CompensationTest.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2, spec §6.2 — "what to undo", derived from what a finished session actually recorded.
 * Pure: the engine is not involved, and `CompensationOutsideTheEngineGuardTest` (`:app`) holds that
 * it never will be.
 */
class CompensationTest {

    private val note = ToolId("fixture_note")       // reversible: undone by fixture_unnote
    private val unnote = ToolId("fixture_unnote")
    private val open = ToolId("fixture_open")       // leaves nothing that stays
    private val erase = ToolId("fixture_erase")     // irreversible

    private fun tool(id: ToolId, risk: ActionRiskLevel, durability: ToolDurability = ToolDurability.TRANSIENT) =
        ToolDescriptor(id = id, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, risk = risk, durability = durability)

    /** `fixture_unnote` is CONFIRM on purpose: the undo plan must carry the REGISTRY's risk. */
    private val registry = FakeToolRegistry(
        listOf(
            tool(note, ActionRiskLevel.SAFE),
            tool(unnote, ActionRiskLevel.CONFIRM),
            tool(open, ActionRiskLevel.SAFE),
            tool(erase, ActionRiskLevel.DANGEROUS, ToolDurability.DURABLE),
        ),
    )

    private val inverses = ToolInverses { forward, _ ->
        when (forward.id) {
            note -> Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name")))))
            open -> Undo.NothingToUndo
            else -> Undo.NotUndoable
        }
    }

    private fun step(index: Int, id: ToolId, name: String, rationale: StepRationale = StepRationale.GOAL_DIRECT) =
        PlanStep(index, ToolInvocation(id, mapOf("name" to ArgSource.Literal(name))), ActionRiskLevel.SAFE, StepPrecondition.None, rationale)

    private fun finished(steps: List<PlanStep>, observations: Map<Int, ToolResult>) = AgentSession(
        id = AgentSessionId("s-finished"),
        goal = AgentGoal("разбери вельтрикс", GoalShape.Free("разбери вельтрикс")),
        plan = ExecutionPlan(steps),
        cursor = steps.size,
        state = ExecutionState.Completed,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `review lists every step that acted, newest first, with how each is undone`() {
        val session = finished(
            listOf(step(0, note, "вельтрикс"), step(1, open, "лумора"), step(2, erase, "ledger")),
            mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected(), 2 to ToolResult.Effected()),
        )

        assertEquals(
            listOf(
                StepUndo(2, Undo.NotUndoable),
                StepUndo(1, Undo.NothingToUndo),
                StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс"))))),
            ),
            Compensation.review(session, inverses),
        )
    }

    /** Review Focus 5. A cancelled uninstall is exactly this: the act left the launcher, unseen. */
    @Test
    fun `a handed-off step is never undone, because its outcome was never seen`() {
        val session = finished(listOf(step(0, note, "вельтрикс")), mapOf(0 to ToolResult.HandedOff()))

        assertEquals(listOf(StepUndo(0, Undo.NotUndoable)), Compensation.review(session, inverses))
    }

    @Test
    fun `a step that observed, failed, was skipped or never ran is not in the review`() {
        val session = finished(
            listOf(step(0, note, "а"), step(1, note, "б"), step(2, note, "в"), step(3, note, "г")),
            mapOf(
                0 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED),
                1 to ToolResult.Failed(CommandFailure.Generic),
            ),
        )

        assertEquals(emptyList<StepUndo>(), Compensation.review(session, inverses))
    }

    @Test
    fun `the undo plan runs the inverses newest first, as undo steps, at the registry's risk`() {
        val session = finished(
            listOf(step(0, note, "вельтрикс"), step(1, note, "лумора")),
            mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected()),
        )

        val plan = Compensation.plan(Compensation.review(session, inverses), registry)

        assertEquals(
            ExecutionPlan(
                listOf(
                    PlanStep(0, ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("лумора"))), ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.COMPENSATION),
                    PlanStep(1, ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.COMPENSATION),
                ),
            ),
            plan,
        )
    }

    @Test
    fun `nothing that can be undone gives no plan at all`() {
        assertNull(
            Compensation.plan(listOf(StepUndo(1, Undo.NotUndoable), StepUndo(0, Undo.NothingToUndo)), registry),
        )
    }

    /** The undo of an undo is a redo, and nobody asked for one. */
    @Test
    fun `a plan that is itself an undo offers no undo of its own`() {
        val undo = finished(
            listOf(step(0, note, "вельтрикс", rationale = StepRationale.COMPENSATION)),
            mapOf(0 to ToolResult.Effected()),
        )

        assertTrue(Compensation.isUndo(undo.plan))
        assertEquals(emptyList<StepUndo>(), Compensation.review(undo, inverses))
    }
}
```

Create `UndoAgentSessionUseCaseTest.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2 — «Отменить» is a NEW session that runs through the unchanged engine. Spec §6.3 (owner,
 * 2026-09-21): compensation is built as a pure addition, and no step asks less than it does today.
 */
class UndoAgentSessionUseCaseTest {

    private val note = ToolId("fixture_note")
    private val unnote = ToolId("fixture_unnote")
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("u${++n}")
    }
    private val inverses = ToolInverses { forward, _ ->
        if (forward.id == note) {
            Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name")))))
        } else {
            Undo.NotUndoable
        }
    }

    private fun registry(unnoteRisk: ActionRiskLevel) = FakeToolRegistry(
        listOf(
            ToolDescriptor(id = note, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(com.sidr.launcher.domain.action.ActionArg("name", description = "fixture")), risk = ActionRiskLevel.SAFE, durability = ToolDurability.TRANSIENT),
            ToolDescriptor(id = unnote, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(com.sidr.launcher.domain.action.ActionArg("name", description = "fixture")), risk = unnoteRisk, durability = ToolDurability.TRANSIENT),
        ),
    )

    private val noPlanner = object : Planner {
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult = PlanningResult.NoPlan
    }

    private fun useCase(registry: ToolRegistry) =
        UndoAgentSessionUseCase(StartAgentSessionUseCase(noPlanner, store, ids, registry), inverses, registry)

    private fun finished(state: ExecutionState = ExecutionState.Completed) = AgentSession(
        id = AgentSessionId("s-finished"),
        goal = AgentGoal("запомни вельтрикс", GoalShape.Free("запомни вельтрикс")),
        plan = ExecutionPlan(
            listOf(PlanStep(0, ToolInvocation(note, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)),
        ),
        cursor = 1,
        state = state,
        observations = mapOf(0 to ToolResult.Effected()),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `undoing a finished plan starts a new session of its inverses and runs it through the same engine`() = runTest {
        val registry = registry(ActionRiskLevel.SAFE)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val started = (useCase(registry).undo(finished()) as OperationResult.Success).value!!
        val ran = (RunAgentSessionUseCase(AgentExecutor(registry, tools), store).run(started) as OperationResult.Success).value

        assertEquals(AgentSessionId("u1"), started.id)
        assertEquals(listOf(StepRationale.COMPENSATION), started.plan.steps.map { it.rationale })
        assertEquals(ExecutionState.Completed, ran.state)
        assertEquals(listOf(unnote), tools.invocations.map { it.id })
        assertEquals(mapOf("name" to "вельтрикс"), tools.invocations.single().args)
    }

    /**
     * Spec §6.3 and §10 criterion 5: undo NEVER lowers a gate. Here the inverse is CONFIRM, and the
     * undo stops at consent exactly as that tool would anywhere else. M3a plants the lowering this
     * exists to catch — a branch in `checkpointFor` that waves undo steps through.
     */
    @Test
    fun `an undo whose inverse asks for consent stops at the gate - undo never lowers a gate`() = runTest {
        val registry = registry(ActionRiskLevel.CONFIRM)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val started = (useCase(registry).undo(finished()) as OperationResult.Success).value!!
        val ran = (RunAgentSessionUseCase(AgentExecutor(registry, tools), store).run(started) as OperationResult.Success).value

        assertEquals(ExecutionState.AwaitingConsent, ran.state)
        assertTrue("nothing may run before consent", tools.invocations.isEmpty())
    }

    /** Spec §6.6: a plan that failed or stopped part-way is exactly where «что откатить» matters. */
    @Test
    fun `a plan that failed or stopped part-way still offers what it did`() = runTest {
        val useCase = useCase(registry(ActionRiskLevel.SAFE))
        for (state in listOf(ExecutionState.Failed, ExecutionState.Blocked)) {
            assertEquals(
                "review for $state",
                listOf(StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс")))))),
                useCase.review(finished(state)),
            )
        }
    }

    /**
     * Owner fork F2-1b, applied to undo: a plan the clock stopped may still have its cut call acting,
     * and an undo is a world call too. The last step here was cut (`HandedOff` at the cursor's last
     * step of a `Blocked` session), so nothing is offered — not even the undoable step before it.
     */
    @Test
    fun `a plan stopped by a cut offers no undo while the cut call may still act`() = runTest {
        val cut = finished(ExecutionState.Blocked).let { done ->
            done.copy(
                plan = ExecutionPlan(
                    done.plan.steps + PlanStep(1, ToolInvocation(note, mapOf("name" to ArgSource.Literal("лумора"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
                ),
                cursor = 2,
                observations = mapOf(0 to ToolResult.Effected(), 1 to ToolResult.HandedOff()),
            )
        }

        assertEquals(emptyList<StepUndo>(), useCase(registry(ActionRiskLevel.SAFE)).review(cut))
    }

    @Test
    fun `a cancelled, paused or unfinished session has nothing to undo`() = runTest {
        val useCase = useCase(registry(ActionRiskLevel.SAFE))
        for (state in listOf(ExecutionState.Cancelled, ExecutionState.Paused, ExecutionState.AwaitingConsent, ExecutionState.Running)) {
            val session = finished(state)

            assertEquals("review for $state", emptyList<StepUndo>(), useCase.review(session))
            assertNull("undo for $state", (useCase.undo(session) as OperationResult.Success).value)
        }
        assertTrue("nothing may be persisted", store.saved.isEmpty())
    }
}
```

Append to `AgentSessionUseCasesTest` (before its final `}`):
```kotlin

    /**
     * A4′ phase 2: `startPlanned` is how a plan built outside the planner — an undo — reaches the
     * store, and it validates exactly as `start` does (F10), so no caller can persist an unchecked
     * plan.
     */
    @Test
    fun `startPlanned refuses a plan the registry cannot run and persists nothing`() = runTest {
        val unknown = ExecutionPlan(
            listOf(
                PlanStep(0, ToolInvocation(ToolId("fixture_unregistered")), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.COMPENSATION),
            ),
        )

        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).startPlanned(goal, unknown)

        assertNull((started as OperationResult.Success).value)
        assertTrue(store.saved.isEmpty())
    }
```

Create `app/src/test/java/com/sidr/launcher/agent/CompensationOutsideTheEngineGuardTest.kt`:
```kotlin
package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A4′ phase 2, spec §6.3 (owner, 2026-09-21): compensation must not buy silence. The strongest form of
 * that promise is structural — the engine that decides consent cannot see an inverse, an undo review
 * or an undo step, so no edit INSIDE it can let "this can be undone" lower a gate. Every rule for undo
 * lives outside `AgentExecutor.kt` (`Compensation`, `UndoAgentSessionUseCase`, the per-consumer
 * `ToolInverses`); this reads the engine's code, comments stripped, and requires it to name none of it.
 *
 * `UndoAgentSessionUseCaseTest > an undo whose inverse asks for consent stops at the gate…` is the
 * behavioural half. Both go red under M3a (plan Task 3).
 *
 * Reopening spec §6.3 (the first reversible CONFIRM tool, by ADR) is exactly the edit that must
 * change this guard — deliberately, in the same commit as the ADR.
 */
class CompensationOutsideTheEngineGuardTest {

    private val engine = File("../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt")

    @Test
    fun `the engine that decides consent never sees an inverse or an undo`() {
        assertTrue("missing engine source: ${engine.canonicalPath}", engine.isFile)
        val code = stripComments(engine.readText())

        val seen = listOf("ToolInverses", "Undo", "Compensation", "COMPENSATION", "StepUndo").filter { code.contains(it) }

        assertEquals(
            "AgentExecutor must not consult undo in any form (spec §6.3). Found: $seen",
            emptyList<String>(),
            seen,
        )
    }
}
```

- [ ] **Step 2: run — expect compile-red.**
  - `tools/gate.sh --scoped :domain:jvmTest` is expected to show `Unresolved reference 'ToolInverses'`,
    `'Undo'`, `'Compensation'`, `'StepUndo'`, `'UndoAgentSessionUseCase'`, `'COMPENSATION'` and
    `'startPlanned'`.
  - The `:app` guard compiles, and it is expected to **pass already**: the engine names none of
    these today. Step 5's M3a proves it can go red.

- [ ] **Step 3: implement.**

Create `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInverses.kt`:
```kotlin
package com.sidr.launcher.domain.tool

/**
 * How one step that acted can be put back (A4′ phase 2, spec §6.2, owner fork F2-3.3).
 */
sealed interface Undo {
    /**
     * Put back by running [invocation]. Its arguments are concrete [ArgSource.Literal]s built from
     * what the step was called with and what it REPORTED — an undo that re-read the world would undo
     * something other than what happened.
     */
    data class By(val invocation: ToolInvocation) : Undo

    /** The step changed something that stays, and nothing registered puts it back. */
    data object NotUndoable : Undo

    /** The step left nothing that stays — a screen opened, a search shown. */
    data object NothingToUndo : Undo
}

/**
 * Port: which registered tool undoes which. **A parallel port, never a field on [ToolDescriptor]** —
 * the phase 0 precedent (`ToolArgumentSorts`); seventeen production files read the descriptor. Each
 * consumer implements its own: `:data:repository` for the launcher's tools, `:consumer:jvm` for the
 * sandbox. A tool an implementation does not know is [Undo.NotUndoable] — a missing answer is never
 * "nothing to undo".
 *
 * **Read by compensation only, never by the engine** (spec §6.3): an inverse the gate could see is an
 * inverse that could buy a step silence. `CompensationOutsideTheEngineGuardTest` (`:app`) holds that
 * `AgentExecutor.kt` names none of this.
 */
fun interface ToolInverses {
    /** How to undo [forward], which returned [result]. Only an `Effected` step reaches here. */
    fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo
}
```

Create `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/Compensation.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo

/** One step that acted, and how it is undone. [stepIndex] is its index in the finished plan. */
data class StepUndo(val stepIndex: Int, val undo: Undo)

/**
 * "What to undo", derived from what a finished session recorded (A4′ phase 2, spec §6.2, `B14`).
 * Pure: no store, no clock, and no engine — the engine never sees any of this (spec §6.3).
 *
 * **Coverage is small, and said so** (spec §6.2): on Android only `launcher_memory`'s two alias tools
 * have an inverse, and every Android plan that reaches them is one step (plan §0.3 P8).
 */
object Compensation {

    /**
     * Every step whose act this session knows happened, newest first, each with how it is undone:
     *  - `Effected` → [inverses];
     *  - `HandedOff` → [Undo.NotUndoable]: the act left the launcher and its outcome was never seen, so
     *    an inverse could act on something that did not happen (a cancelled uninstall). Never offered;
     *  - `Observed`, `Failed`, skipped, never reached → absent: nothing happened.
     *
     * A plan that is itself an undo reviews to nothing: the undo of an undo is a redo.
     */
    fun review(session: AgentSession, inverses: ToolInverses): List<StepUndo> {
        if (isUndo(session.plan)) return emptyList()
        return session.plan.steps.reversed().mapNotNull { step ->
            when (val result = session.observations[step.index]) {
                is ToolResult.Effected -> StepUndo(step.index, undoOfEffected(step, result, session, inverses))
                is ToolResult.HandedOff -> StepUndo(step.index, Undo.NotUndoable)
                is ToolResult.Observed, is ToolResult.Failed, null -> null
            }
        }
    }

    /**
     * The plan that runs [review]'s inverses in the review's order — newest first — or `null` when
     * nothing in it can be undone.
     *
     * **Risk is the registry's, never lower** (spec §6.3): an undo step asks exactly what the same tool
     * asks anywhere else. The engine takes the higher of this and the registry anyway (A0 finding F2),
     * so a plan-level mistake here cannot lower a gate — M3e shows that.
     */
    fun plan(review: List<StepUndo>, registry: ToolRegistry): ExecutionPlan? {
        val inverses = review.mapNotNull { (it.undo as? Undo.By)?.invocation }
        if (inverses.isEmpty()) return null
        return ExecutionPlan(
            inverses.mapIndexed { index, invocation ->
                PlanStep(
                    index = index,
                    invocation = invocation,
                    risk = registry.find(invocation.id)?.risk ?: ActionRiskLevel.DANGEROUS,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.COMPENSATION,
                )
            },
        )
    }

    /**
     * Whether [plan] undoes another. Such a plan never offers an undo of its own, and is never planned
     * again from its goal on resume (§6.4): its goal is the ORIGINAL goal, and re-planning it would
     * redo what it undoes.
     */
    fun isUndo(plan: ExecutionPlan): Boolean = plan.steps.any { it.rationale == StepRationale.COMPENSATION }

    private fun undoOfEffected(
        step: PlanStep,
        result: ToolResult.Effected,
        session: AgentSession,
        inverses: ToolInverses,
    ): Undo = when (val resolved = InvocationValidator.resolve(step.invocation, session.observations)) {
        is ResolutionResult.Resolved -> inverses.undoOf(resolved.invocation, result)
        // Unreachable for a step that ran: it was bound from these same observations. If it cannot be
        // bound now, the values an inverse needs are unknown, and a guessed undo is worse than none.
        is ResolutionResult.Rejected -> Undo.NotUndoable
    }
}
```

Create `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/UndoAgentSessionUseCase.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult

/**
 * «Отменить» (A4′ phase 2, spec §6.6, owner fork F2-3.3): a NEW session whose plan undoes a finished
 * one, run through the unchanged engine — the same gates, the same trace, deleted at rest like any
 * session. Never automatic; only on the user's tap.
 *
 * **Nothing about it asks less than today** (spec §6.3, owner): the undo plan carries the registry's
 * risk, and the engine cannot see that a step is an undo (`CompensationOutsideTheEngineGuardTest`).
 */
class UndoAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val inverses: ToolInverses,
    private val registry: ToolRegistry,
) {
    /**
     * What «Отменить» on [session]'s card would undo, and what it could not. Empty unless the session
     * is finished: a paused or waiting plan has nothing final to undo, and a cancelled one draws no card.
     *
     * Also empty for a plan the clock stopped (owner fork F2-1b): its cut call may still be acting,
     * and an undo is a world call too. A `Blocked` session whose last step is `HandedOff` is exactly
     * that shape — `AgentExecutor` ends the plan there and nowhere else with that pair.
     */
    fun review(session: AgentSession): List<StepUndo> = when {
        !session.state.isTerminal || session.state == ExecutionState.Cancelled -> emptyList()
        session.state == ExecutionState.Blocked && session.observations[session.cursor - 1] is ToolResult.HandedOff -> emptyList()
        else -> Compensation.review(session, inverses)
    }

    /** A persisted `Running` undo session, or `Success(null)` when there is nothing to undo. */
    suspend fun undo(finished: AgentSession): OperationResult<AgentSession?> {
        val plan = Compensation.plan(review(finished), registry) ?: return OperationResult.Success(null)
        return start.startPlanned(finished.goal, plan)
    }
}
```

`ExecutionPlan.kt:16-17` — replace
```kotlin
/** Typed provenance — why this step is in the plan. The feature layer renders it to localized text. */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK }
```
with
```kotlin
/**
 * Typed provenance — why this step is in the plan. The feature layer renders it to localized text.
 *
 * [COMPENSATION] (A4′ phase 2): the step undoes a step of another, finished plan. It is what marks a
 * plan as an undo — `Compensation.isUndo` — so that it offers no undo of its own and is never planned
 * again from its goal. Persisted by name in an existing TEXT column; no migration.
 */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION }
```
and at `:74` replace
` *  - `StartAgentSessionUseCase.start` — where a non-`Planned` result used to become `Success(null)`.`
with
` *  - `StartAgentSessionUseCase.plan` — where a non-`Planned` result used to become `Success(null)`.`

`StartAgentSessionUseCase.kt:31-66` — replace from
`    /**\n     * The request carries no prior observations:` down to the closing `}` of `start` with:
```kotlin
    /** Plans [goal] and persists a `Running` session; `Success(null)` when there is nothing to run. */
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        val plan = plan(goal) ?: return OperationResult.Success(null)
        return when (val started = startPlanned(goal, plan)) {
            is OperationResult.Failure -> started
            is OperationResult.Success -> OperationResult.Success(started.value?.id)
        }
    }

    /**
     * The first attempt's plan for [goal] if it is runnable against the registry NOW; persists
     * nothing. The request carries no prior observations: a first attempt has observed nothing, and
     * since owner fork F2-3.1 re-planning after a failure is phase 3's (spec §6.4, amended).
     */
    suspend fun plan(goal: AgentGoal): ExecutionPlan? {
        // Exhaustive with no `else` (spec §3 0.0). Whoever adds a third variant must decide here what a
        // session-less answer means for it, and the compiler is what makes them.
        val plan = when (val planned = planner.plan(PlanningRequest(goal), registry)) {
            is PlanningResult.Planned -> planned.plan
            PlanningResult.NoPlan -> return null
        }
        return plan.takeIf(::isRunnable)
    }

    /**
     * Persists a `Running` session for [plan] and returns it — a plan the caller built itself (an
     * undo, spec §6.2) or took from [plan]. `Success(null)` when [plan] is not runnable: validated
     * here too, so no caller can persist an unchecked plan (F10).
     *
     * **Saving REPLACES whatever session the store held** — the store keeps one session at rest (Room:
     * `replaceSession` deletes every other id; JVM: one file). That is what lets a fresh plan supersede
     * a stale one atomically on resume (spec §6.4, plan §0.3 P5).
     */
    suspend fun startPlanned(goal: AgentGoal, plan: ExecutionPlan): OperationResult<AgentSession?> {
        if (!isRunnable(plan)) return OperationResult.Success(null)
        val session = AgentSession(
            id = ids.newId(),
            goal = goal,
            plan = plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(plan.steps.size))),
        )
        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(session)
        }
    }
```

`AgentSessionMappers.kt:430` — after
`        StepRationale.APP_NOT_INSTALLED_FALLBACK.name -> StepRationale.APP_NOT_INSTALLED_FALLBACK` insert
`        StepRationale.COMPENSATION.name -> StepRationale.COMPENSATION`.

- [ ] **Step 4: run — expect green.**
  - `:domain:jvmTest` **473**;
  - `:data:repository:testDebugUnitTest` **396** — it includes `every StepRationale round-trips`, which
    would be red without the decoder line;
  - `:app:testDebugUnitTest` **63**.

- [ ] **Step 5: mutations — ten.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M3a | `AgentExecutor.kt` · `    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {\n` → the same line followed by `        if (step.rationale == StepRationale.COMPENSATION) return null\n` | **§10 criterion 5**: "lower the gate for undo" → RED, twice | `:domain` — `UndoAgentSessionUseCaseTest > an undo whose inverse asks for consent stops at the gate - undo never lowers a gate`. `:app` — `CompensationOutsideTheEngineGuardTest > the engine that decides consent never sees an inverse or an undo` |
| M3b | `Compensation.kt` · `is ToolResult.HandedOff -> StepUndo(step.index, Undo.NotUndoable)` → `is ToolResult.HandedOff -> StepUndo(step.index, Undo.By(step.invocation))` | a handed-off act is never offered | `:domain` — `CompensationTest > a handed-off step is never undone, because its outcome was never seen` **only** |
| M3c | `Compensation.kt` · `session.plan.steps.reversed().mapNotNull` → `session.plan.steps.mapNotNull` | newest first | `:domain` — `CompensationTest >` `review lists every step that acted, newest first…`, `the undo plan runs the inverses newest first…` |
| M3d | `Compensation.kt` · `        if (isUndo(session.plan)) return emptyList()\n` → `` | no undo of an undo | `:domain` — `CompensationTest > a plan that is itself an undo offers no undo of its own` **only** |
| M3e | `Compensation.kt` · `risk = registry.find(invocation.id)?.risk ?: ActionRiskLevel.DANGEROUS,` → `risk = ActionRiskLevel.SAFE,` | the plan carries the registry's risk | `:domain` — `CompensationTest > the undo plan runs the inverses newest first, as undo steps, at the registry's risk` **only**. **`…undo never lowers a gate` stays GREEN**: the engine takes `maxOf(plan, registry)` (F2). That green is a finding to record, not a miss |
| M3f | `UndoAgentSessionUseCase.kt` · `        !session.state.isTerminal \|\| session.state == ExecutionState.Cancelled -> emptyList()\n` → `` | only a finished, shown plan offers undo | `:domain` — `UndoAgentSessionUseCaseTest > a cancelled, paused or unfinished session has nothing to undo` **only** |
| M3i | `UndoAgentSessionUseCase.kt` · `        session.state == ExecutionState.Blocked && session.observations[session.cursor - 1] is ToolResult.HandedOff -> emptyList()\n` → `` | F2-1b: no undo while a cut call may act | `:domain` — `UndoAgentSessionUseCaseTest > a plan stopped by a cut offers no undo while the cut call may still act` **only** |
| M3j | `UndoAgentSessionUseCase.kt` · `        !session.state.isTerminal \|\| session.state == ExecutionState.Cancelled -> emptyList()` → `        session.state != ExecutionState.Completed -> emptyList()` | a part-way failure offers undo (§6.6) | `:domain` — `UndoAgentSessionUseCaseTest > a plan that failed or stopped part-way still offers what it did` **only** |
| M3g | `StartAgentSessionUseCase.kt` · `        if (!isRunnable(plan)) return OperationResult.Success(null)\n        val session = AgentSession(` → `        val session = AgentSession(` | `startPlanned` validates | `:domain` — `AgentSessionUseCasesTest > startPlanned refuses a plan the registry cannot run and persists nothing` **only** |
| M3h | `AgentSessionMappers.kt` · `        StepRationale.COMPENSATION.name -> StepRationale.COMPENSATION\n` → `` | Room reads an undo step | `:data:repository` — `AgentSessionMappersTest > every StepRationale round-trips` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1510`.
  - Per module: `:domain` **473** (+12), `:app` **63** (+1), `:data:repository` 396, the rest
    unchanged.

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src data/repository/src app/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): компенсация — порт обратных шагов, разбор «что откатить», отмена новой сессией

Спека §6.2–§6.3, развилка владельца F2-3.3. ToolInverses — параллельный
порт (ToolDescriptor не тронут): какой инструмент отменяет какой. Compensation
чисто выводит из законченной сессии, что откатывается (новейшее первым), что
нет (HandedOff — никогда: исход не видели), и чего откатывать нечего.
UndoAgentSessionUseCase запускает отмену НОВОЙ сессией через тот же движок.

Гейт не понижается: план отмены несёт риск реестра; движок не видит ни порта,
ни шагов отмены — это держат поведенческий тест и структурный гвард в :app,
мутация M3a краснит оба. StartAgentSessionUseCase получил plan() и
startPlanned() — одна валидация на оба пути. Мутации M3a–M3h.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: resume — a paused plan that has only looked, looks again

**Spec:** §6.4 as amended by owner fork F2-3.1; §3 0.3 (staleness layer 1); fork F2-3.2.
**Modules:** `:domain`, `:feature:launcher`, `:app`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt`. Add
  `replansOnResume()` and the top-level `midStepInvocation()`, moved from `AgentExecutor`. Update
  `resumed()`'s KDoc, layer 1 (`:134-140`).
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt:257-281`.
  Delete the private `midStepInvocation`; its KDoc moves with it.
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ResumeAgentSessionUseCase.kt`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt:99-103`
  (`PlanningRequest` KDoc).
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSession.kt`
  — constructor, `continueSession` (`:87-90`), and one single-flight flag.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt` —
  constructor (after `:98`) and the `LauncherAgentSession(` block (`:261-267`).
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt`
  — the `Paused` body.
- Modify: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/strings.xml`,
  `values-tr/strings.xml` — one key each, after `launcher_agent_paused_body`.
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` — one provider.
- Create: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/ResumeAgentSessionUseCaseTest.kt`
  (6).
- Create: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/AgentUseCaseFixtures.kt`
  (test helpers, no tests).
- Create: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurfacePausedTest.kt`
  (2).
- Test: `LauncherAgentSessionTest` — `collaborator(...)`, two rewritten tests (§0.3 P12), +2 new.
- Test (compile only): the six `LauncherViewModel(` constructions — four in `LauncherViewModelTest`
  (`~:290`, `~:355`, `~:689`, `~:946`), one each in `LauncherScreenPrayerStripTest` (`~:179`) and
  `LauncherScreenAgentProvenanceTest` (`~:231`).

**Interfaces.**
- Produces:
  ```kotlin
  fun AgentSession.replansOnResume(): Boolean              // member of AgentSession
  internal fun AgentSession.midStepInvocation(): TraceEvent.ToolInvoked?   // top level, :domain
  class ResumeAgentSessionUseCase(start: StartAgentSessionUseCase, run: RunAgentSessionUseCase) {
      suspend fun resume(paused: AgentSession): OperationResult<AgentSession>
  }
  // LauncherAgentSession(runSession, resolveConsent, cancelSession, store, scope, resumeSession)
  // LauncherViewModel(..., resumeAgentSession: ResumeAgentSessionUseCase, ...)
  // test helpers (feature/launcher tests):
  internal class SequentialIds(prefix: String) : AgentSessionIdFactory
  internal fun resumeOver(store: AgentSessionStore, run: RunAgentSessionUseCase, registry: ToolRegistry): ResumeAgentSessionUseCase
  ```
- Consumes: from Task 3 — `StartAgentSessionUseCase.plan` / `startPlanned`, and `Compensation.isUndo`.

- [ ] **Step 1: write the failing tests.**

Create `ResumeAgentSessionUseCaseTest.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2 — staleness, layer 1 (spec §3 0.3, §6.4 as amended by owner fork F2-3.1).
 *
 * The A0 acceptance found (§12.8) that a paused plan acts on what it saw before the pause: it had
 * observed "not installed", the user installed the app meanwhile, and «Продолжить» opened the store
 * anyway. A plan in which nothing has ACTED is now planned afresh — a new session, which replaces the
 * stale one in the store (plan §0.3 P5). Anything that may already have acted continues as before.
 */
class ResumeAgentSessionUseCaseTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal("открой вельтрикс", GoalShape.AppNotInstalled("вельтрикс"))

    private fun notInstalled() = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to "вельтрикс")),
    )

    private fun runner(tools: FakeToolExecutor) = RunAgentSessionUseCase(AgentExecutor(registry, tools), store)

    private fun resume(tools: FakeToolExecutor, planner: Planner = TemplatePlanner()) =
        ResumeAgentSessionUseCase(StartAgentSessionUseCase(planner, store, ids, registry), runner(tools))

    /** The §12.8 shape: step 0 observed "not installed", the store step waits for consent, the process died. */
    private suspend fun pausedAfterLooking(): AgentSession {
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner(FakeToolExecutor(listOf(notInstalled()))).run(store.active)
        val paused = store.active.pausedForRestore()
        store.save(paused)
        return paused
    }

    private fun <T> OperationResult<T>.value(): T = (this as OperationResult.Success).value

    @Test
    fun `a paused plan that has only looked is planned again, and an app installed meanwhile opens`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools).resume(pausedAfterLooking()).value()

        assertEquals(AgentSessionId("s2"), after.id)
        assertEquals(ExecutionState.Completed, after.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    @Test
    fun `a paused plan still missing its app offers the store again, after a fresh look`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))

        val after = resume(tools).resume(pausedAfterLooking()).value()

        assertEquals(AgentSessionId("s2"), after.id)
        assertEquals(ExecutionState.AwaitingConsent, after.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertEquals("the fresh session replaced the stale one", AgentSessionId("s2"), store.active.id)
    }

    @Test
    fun `a paused plan in which a step already acted continues from its cursor`() = runTest {
        val launch = { index: Int, name: String ->
            PlanStep(index, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal(name))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)
        }
        val paused = AgentSession(
            id = AgentSessionId("s-acted"),
            goal = goal,
            plan = ExecutionPlan(listOf(launch(0, "лумора"), launch(1, "вельтрикс"))),
            cursor = 1,
            state = ExecutionState.Paused,
            observations = mapOf(0 to ToolResult.Effected()),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Effected()), TraceEvent.SessionPaused,
                ),
            ),
        )
        store.save(paused)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools).resume(paused).value()

        assertEquals(AgentSessionId("s-acted"), after.id)
        assertEquals(listOf(mapOf("query" to "вельтрикс")), tools.invocations.map { it.args })
    }

    /** Review Focus 3: the act may already have happened, so planning it again would perform it twice. */
    @Test
    fun `a call in flight at the pause is resumed, never planned again`() = runTest {
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        store.save(AgentExecutor(registry, FakeToolExecutor(emptyList())).prepare(store.active).pausedForRestore())
        val tools = FakeToolExecutor(listOf(notInstalled()))

        val after = resume(tools).resume(store.active).value()

        assertEquals(AgentSessionId("s1"), after.id)
        assertEquals(1, tools.invocations.size)
        assertEquals(1, after.trace.events.count { it is TraceEvent.ToolInvoked && it.index == 0 })
    }

    /** Review Focus 4: an undo's goal is the ORIGINAL goal — planning it again would redo what it undoes. */
    @Test
    fun `an undo plan is never planned again from its goal, which would redo what it undoes`() = runTest {
        var asked = 0
        val eager = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                asked++
                return TemplatePlanner().plan(request, registry)
            }
        }
        val undo = AgentSession(
            id = AgentSessionId("s-undo"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("лумора"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.COMPENSATION)),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(1), TraceEvent.SessionPaused)),
        )
        store.save(undo)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools, eager).resume(undo).value()

        assertEquals(0, asked)
        assertEquals(AgentSessionId("s-undo"), after.id)
        assertEquals(listOf(mapOf("query" to "лумора")), tools.invocations.map { it.args })
    }

    @Test
    fun `a goal that no longer plans leaves the paused plan to continue as it was`() = runTest {
        val nothing = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult = PlanningResult.NoPlan
        }
        val paused = pausedAfterLooking()
        val tools = FakeToolExecutor(emptyList())

        val after = resume(tools, nothing).resume(paused).value()

        assertEquals(AgentSessionId("s1"), after.id)
        assertEquals(ExecutionState.AwaitingConsent, after.state)
        assertTrue(tools.invocations.isEmpty())
        assertTrue(after.trace.events.contains(TraceEvent.SessionResumed))
    }
}
```

Create `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/AgentUseCaseFixtures.kt`:
```kotlin
package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * A4′ phase 2 — the agent use cases a `LauncherViewModel` or `LauncherAgentSession` now also needs,
 * built one way for every test that constructs them. Not a fake: the real domain use cases over
 * whatever store, runner and registry the test already holds.
 */
internal class SequentialIds(private val prefix: String) : AgentSessionIdFactory {
    private var n = 0
    override fun newId() = AgentSessionId("$prefix${++n}")
}

internal fun resumeOver(
    store: AgentSessionStore,
    run: RunAgentSessionUseCase,
    registry: ToolRegistry,
): ResumeAgentSessionUseCase = ResumeAgentSessionUseCase(
    StartAgentSessionUseCase(TemplatePlanner(), store, SequentialIds("resumed-"), registry),
    run,
)
```

In `LauncherAgentSessionTest`:
- Add the import `com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase`.
- In `collaborator(...)`, after `            scope = scope,` add
  `            resumeSession = ResumeAgentSessionUseCase(StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry), runner),`.
- **Rewrite** the test at `:157-177`, replacing it whole (the old name included) with:
```kotlin
    /**
     * A4′ phase 2 (owner fork F2-3.1). This test used to pin «resuming re-evaluates rather than steps
     * past, and the world is still untouched». Its seed is exactly A0's §12.8 stale shape — step 0 has
     * only LOOKED — and the owner decided such a plan looks again: a fresh session re-runs the launch
     * and, still finding nothing, returns to the store's consent gate. The world is touched once more,
     * on purpose; the gate is still there.
     */
    @Test
    fun `continuing a plan that has only looked looks at the world again before it asks`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals(ExecutionState.AwaitingConsent, agent.session.value?.state)
        assertEquals(AgentSessionId("s2"), agent.session.value?.id)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    /** A0 acceptance §12.8, through the product's own path: the app appeared while the plan waited. */
    @Test
    fun `continuing a stale plan opens an app installed while it waited, and offers no store`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals(ExecutionState.Completed, agent.session.value?.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    /** Review Focus 1 (plan §0.3 P6): each tap would re-plan into a new id, so two taps = two looks. */
    @Test
    fun `a second Continue tap while the first is still running starts nothing`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled(), notInstalled()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals("two taps, one fresh look", 2, tools.invocations.size)
    }
```
- In `` `confirming the same step twice invokes the tool once` `` (`:179-201`), replace its first line
  `        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))` with
  `        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled(), ToolResult.Effected()))`,
  and add above the `@Test` the comment
  `    // A4′ phase 2: the resume re-plans (F2-3.1), so the fresh session's own look takes the second script entry.`

Create `AgentSessionSurfacePausedTest.kt`:
```kotlin
package com.sidr.launcher.feature.launcher.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, owner fork F2-3.2. The paused card promised «Sidr picks it up where it stopped», which
 * is false for a plan that will be planned afresh. The body is chosen by `AgentSession.replansOnResume`
 * — the SAME predicate `ResumeAgentSessionUseCase` acts on — so the card cannot say one thing while
 * the button does another. The two bodies share their first words, so they are matched whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfacePausedTest {

    @get:Rule val compose = createComposeRule()

    private val continuesBody =
        "You left before this plan finished. Continue and Sidr picks it up where it stopped; dismiss it and nothing further runs."
    private val replansBody =
        "You left before this plan finished, and things may have changed since. Continue and Sidr looks at the plan afresh; dismiss it and nothing further runs."

    private val plan = ExecutionPlan(
        listOf(
            PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
            PlanStep(1, ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))), ActionRiskLevel.CONFIRM, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED), StepRationale.APP_NOT_INSTALLED_FALLBACK),
        ),
    )

    private fun paused(cursor: Int, observations: Map<Int, ToolResult>, trace: List<TraceEvent>) = AgentSession(
        id = AgentSessionId("s-paused"),
        goal = AgentGoal("открой вельтрикс", GoalShape.AppNotInstalled("вельтрикс")),
        plan = plan,
        cursor = cursor,
        state = ExecutionState.Paused,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(trace),
    )

    private fun render(session: AgentSession) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = session,
                    toolProvenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a paused plan that has only looked says it will look again`() {
        val looked = ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "вельтрикс")))
        render(
            paused(
                cursor = 1,
                observations = mapOf(0 to looked),
                trace = listOf(TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP), TraceEvent.ToolObserved(0, looked), TraceEvent.SessionPaused),
            ),
        )

        compose.onNodeWithText(replansBody).assertExists()
        compose.onNodeWithText(continuesBody).assertDoesNotExist()
    }

    @Test
    fun `a paused plan with a call in flight says it will pick up where it stopped`() {
        render(
            paused(
                cursor = 0,
                observations = emptyMap(),
                trace = listOf(TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP), TraceEvent.SessionPaused),
            ),
        )

        compose.onNodeWithText(continuesBody).assertExists()
        compose.onNodeWithText(replansBody).assertDoesNotExist()
    }
}
```

**The six `LauncherViewModel(` constructions (compile only).** All six are in
`com.sidr.launcher.feature.launcher`, the package of `resumeOver`, so no import is needed. After each line
`cancelAgentSession = CancelAgentSessionUseCase(<store>),`, insert the matching line:

| File · site | Insert |
|---|---|
| `LauncherViewModelTest` `~:292` (`runAgentSession = runAgent`) | `        resumeAgentSession = resumeOver(fakeAgentStore, runAgent, agentRegistry),` |
| `LauncherViewModelTest` `~:357` (`runAgentSession = runAgentSession`) | `            resumeAgentSession = resumeOver(fakeAgentStore, runAgentSession, agentRegistry),` |
| `LauncherViewModelTest` `~:691` (`runAgentSession = runAgentSession`) | `            resumeAgentSession = resumeOver(fakeAgentStore, runAgentSession, agentRegistry),` |
| `LauncherViewModelTest` `~:948` (`runAgentSession = runAgentSession`) — found by the plan review | `            resumeAgentSession = resumeOver(fakeAgentStore, runAgentSession, agentRegistry),` |
| `LauncherScreenPrayerStripTest` `~:181` | `            resumeAgentSession = resumeOver(agentStore, runAgent, agentRegistry),` |
| `LauncherScreenAgentProvenanceTest` `~:233` | `            resumeAgentSession = resumeOver(agentStore, runAgent, agentRegistry),` |

The local names are the ones each site already passes for `runAgentSession =` and `agentSessionStore =`
— **match them in the file, not from this table**, if they differ.

- [ ] **Step 2: run — expect compile-red.**
  - `:domain:jvmTest`: `Unresolved reference 'ResumeAgentSessionUseCase'`.
  - `:feature:launcher`: `No parameter with name 'resumeSession'` and
    `No parameter with name 'resumeAgentSession'`.

- [ ] **Step 3: implement.**

`AgentExecutor.kt:257-281` — delete the whole private member
`    private fun AgentSession.midStepInvocation(): TraceEvent.ToolInvoked? {` with its KDoc (from
`    /**\n     * The [TraceEvent.ToolInvoked] event of the step whose tool call has been decided`). The engine's
three call sites keep compiling against the top-level function below.

`AgentSession.kt` — append at the end of the file, after `AgentSession`'s closing `}`:
```kotlin

/**
 * The [TraceEvent.ToolInvoked] event of the step whose tool call has been decided but not yet
 * performed, or `null` if [AgentSession] is not currently mid-step. Moved here from `AgentExecutor`
 * in A4′ phase 2 so the resume rule ([AgentSession.replansOnResume]) reads the SAME predicate the
 * engine does, rather than a copy.
 *
 * **It reads the last `ToolInvoked`, not the last trace event, and that difference is the whole
 * behaviour** (review finding F1, 2026-08-23). The restart path appends [TraceEvent.SessionPaused] and
 * [TraceEvent.SessionResumed] *after* the pending `ToolInvoked`; keyed on the trace tail the predicate
 * answered `null` on exactly the shape it exists to recognise, and one granted consent paid for two
 * executions (`DOC-HMA-1`). "The last `ToolInvoked` has no `ToolObserved` for its index" is the same
 * predicate without the adjacency assumption.
 */
internal fun AgentSession.midStepInvocation(): TraceEvent.ToolInvoked? {
    val last = trace.events.filterIsInstance<TraceEvent.ToolInvoked>().lastOrNull() ?: return null
    val alreadyObserved = trace.events.any { it is TraceEvent.ToolObserved && it.index == last.index }
    return if (alreadyObserved) null else last
}
```
Inside `data class AgentSession`, after `resumed()`, add:
```kotlin

    /**
     * Whether «Продолжить» on this paused session plans the goal AFRESH rather than picking up at the
     * cursor (A4′ phase 2, spec §6.4 as amended by owner fork F2-3.1; staleness layer 1, spec §3 0.3).
     *
     * True exactly when nothing can have acted yet, so looking again cannot do anything twice:
     *  - no result is `Effected` or `HandedOff` — only looks (`Observed`) and failures;
     *  - no call is in flight ([midStepInvocation]) — a call cut off by the process's death may have
     *    acted, and resuming it once is what `DOC-HMA-1` requires;
     *  - the plan is not an undo ([Compensation.isUndo]) — its goal is the original goal, and planning
     *    it again would redo what it undoes.
     *
     * One predicate for two readers: `ResumeAgentSessionUseCase` acts on it, and the paused card picks
     * its body by it (F2-3.2), so the card cannot promise one thing while the button does another.
     */
    fun replansOnResume(): Boolean =
        state == ExecutionState.Paused &&
            !Compensation.isUndo(plan) &&
            midStepInvocation() == null &&
            observations.values.none { it is ToolResult.Effected || it is ToolResult.HandedOff }
```
In `resumed()`'s KDoc, replace the layer 1 bullet's last sentence
`**Addressed to phase 2**, where re-planning on partial failure is the same mechanism and where`
`    `PlanningRequest.priorObservations` is the input it needs — you cannot react to a re-check`
`    without something that re-plans.`
with
`**Built in phase 2** — not here, in `ResumeAgentSessionUseCase`: a plan in which nothing has`
`    acted is planned afresh ([replansOnResume]); anything that may have acted still continues`
`    from this cursor, and that remainder is named, not closed.`

Create `ResumeAgentSessionUseCase.kt`:
```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * «Продолжить» on a paused plan (A4′ phase 2, spec §6.4 as amended by owner fork F2-3.1).
 *
 * If nothing in the plan can have acted yet ([AgentSession.replansOnResume]), the goal is planned
 * AFRESH and run as a new session. What it saw before the pause may be stale — A0's acceptance
 * (§12.8) opened the store for an app the user had installed meanwhile. The new session **replaces**
 * the stale one in the store, because the store keeps one session at rest (plan §0.3 P5). A re-run
 * under the same step index would have made the trace lie (§0.3 P4), which is why this is a new
 * session and not a revision.
 *
 * Otherwise — something may have acted, a call is in flight, the plan is an undo, or the goal no
 * longer plans at all — the paused session continues from its cursor exactly as before phase 2.
 *
 * Re-planning after a FAILURE is not here: none of the deterministic planners can do anything better
 * after one, and its first consumer is phase 3's model planner (the amendment).
 */
class ResumeAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val run: RunAgentSessionUseCase,
) {
    suspend fun resume(paused: AgentSession): OperationResult<AgentSession> {
        if (!paused.replansOnResume()) return run.run(paused.resumed())
        val fresh = start.plan(paused.goal) ?: return run.run(paused.resumed())
        val started = when (val saved = start.startPlanned(paused.goal, fresh)) {
            is OperationResult.Failure -> return saved
            // Unreachable: `plan` already validated against the same registry. Continuing the stale
            // session is the honest fallback — it is still the one in the store.
            is OperationResult.Success -> saved.value ?: return run.run(paused.resumed())
        }
        return run.run(started)
    }
}
```

`ExecutionPlan.kt:99-103` — replace
```kotlin
 * [priorObservations] is **not speculative**: its consumer is named and dated — re-planning after a
 * partial failure, phase 2 (§6.4), which cannot work without what the first attempt observed. It is
 * empty at every call site phase 0 ships, and that is the honest state rather than a placeholder: a
 * first attempt has observed nothing.
```
with
```kotlin
 * [priorObservations] is **not speculative, and its consumer moved once**: re-planning after a
 * partial failure was dated to phase 2 (§6.4), and the owner moved it to phase 3 on 2026-09-27 (fork
 * F2-3.1). None of the deterministic planners can do anything better after a failure, and the first
 * planner that can — the bridge's model planner — arrives there. Phase 2's re-planning (on resume)
 * plans afresh with an EMPTY request on purpose: what the stale attempt observed is exactly what is
 * no longer trusted. It is empty at every call site, and that is the honest state.
```

`LauncherAgentSession.kt`:
- Add the import `com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase`.
- Add the constructor parameter after `    private val scope: CoroutineScope,`:
  `    private val resumeSession: ResumeAgentSessionUseCase,`.
- Replace `continueSession` (`:87-90`):
```kotlin
    fun continueSession() = scope.launch {
        val paused = _session.value ?: return@launch
        _session.value = (runSession.run(paused.resumed()) as? OperationResult.Success)?.value
    }
```
with:
```kotlin
    /**
     * A tap that lands while a previous «Продолжить» (or, from Task 6, «Отменить») is still running
     * does nothing. Before A4′ phase 2 two taps drove the same session twice; since resume can
     * re-plan into a NEW session id (F2-3.1), the store's conditional write no longer de-duplicates
     * them, and each tap would perform step 0 (plan §0.3 P6). Confined to the main thread like every
     * caller here, so the check and the set cannot interleave.
     */
    private var busy = false

    fun continueSession() {
        if (busy) return
        val paused = _session.value ?: return
        busy = true
        scope.launch {
            try {
                _session.value = (resumeSession.resume(paused) as? OperationResult.Success)?.value
            } finally {
                busy = false
            }
        }
    }
```
`LauncherViewModel.continueAgentSession` calls `agentSession.continueSession()` and ignores the
result. The return type changing from `Job` to `Unit` compiles unchanged.

`LauncherViewModel.kt`:
- Add the import `com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase`.
- After `    private val cancelAgentSession: CancelAgentSessionUseCase,` add
  `    private val resumeAgentSession: ResumeAgentSessionUseCase,`.
- In the `LauncherAgentSession(` block, after `        scope = viewModelScope,` add
  `        resumeSession = resumeAgentSession,`.

`AgentSessionSurface.kt` — in the `ExecutionState.Paused ->` branch, replace
`            body = sidrString(R.string.launcher_agent_paused_body),` with:
```kotlin
            // F2-3.2: the body says what «Продолжить» will actually do, by the predicate the resume
            // use case acts on — a plan that has only looked is planned afresh.
            body = sidrString(
                if (session.replansOnResume()) {
                    R.string.launcher_agent_paused_body_replan
                } else {
                    R.string.launcher_agent_paused_body
                },
            ),
```

Strings — insert after the `launcher_agent_paused_body` line in each file:
- `values/strings.xml`:
  `    <string name="launcher_agent_paused_body_replan">You left before this plan finished, and things may have changed since. Continue and Sidr looks at the plan afresh; dismiss it and nothing further runs.</string>`
- `values-ru/strings.xml`:
  `    <string name="launcher_agent_paused_body_replan">Вы вышли до того, как план завершился, а с тех пор всё могло измениться. Продолжите — и Sidr проверит план заново; закройте — и больше ничего не выполнится.</string>`
- `values-tr/strings.xml`:
  `    <string name="launcher_agent_paused_body_replan">Bu plan bitmeden ayrıldınız; o zamandan beri bir şeyler değişmiş olabilir. Devam ederseniz Sidr planı baştan gözden geçirir; kapatırsanız başka hiçbir şey çalışmaz.</string>`
  — **unjudged**, and added to the Turkish review (§0.6).

`AgentProvidesModule.kt` — add the import `com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase`
and, after `provideRunAgentSessionUseCase`:
```kotlin

    @Provides
    @Singleton
    fun provideResumeAgentSessionUseCase(
        start: StartAgentSessionUseCase,
        run: RunAgentSessionUseCase,
    ): ResumeAgentSessionUseCase = ResumeAgentSessionUseCase(start = start, run = run)
```

- [ ] **Step 4: run — expect green.**
  - `:domain:jvmTest` **479**;
  - `:feature:launcher:testDebugUnitTest` **208**;
  - `:app:assembleDebug` compiles the Hilt graph. It is a compile probe, run with bare `./gradlew`.

- [ ] **Step 5: mutations — seven.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M4a | `AgentSession.kt` · `        state == ExecutionState.Paused &&\n            !Compensation.isUndo(plan) &&` → `        false &&\n            !Compensation.isUndo(plan) &&` | a stale plan is re-planned at all | `:domain` — `ResumeAgentSessionUseCaseTest >` `a paused plan that has only looked is planned again…`, `a paused plan still missing its app offers the store again…`. `:feature:launcher` — `LauncherAgentSessionTest >` `continuing a plan that has only looked looks at the world again before it asks`, `continuing a stale plan opens an app installed while it waited, and offers no store`, `a second Continue tap while the first is still running starts nothing` (its expected count includes the one fresh look); `AgentSessionSurfacePausedTest > a paused plan that has only looked says it will look again` |
| M4b | `AgentSession.kt` · `            observations.values.none { it is ToolResult.Effected \|\| it is ToolResult.HandedOff }` → `            true` | an acted step is never re-run | `:domain` — `ResumeAgentSessionUseCaseTest > a paused plan in which a step already acted continues from its cursor` **only** |
| M4c | `AgentSession.kt` · `            midStepInvocation() == null &&\n` → `` | a call in flight is resumed, not re-planned | `:domain` — `ResumeAgentSessionUseCaseTest > a call in flight at the pause is resumed, never planned again`. `:feature:launcher` — `AgentSessionSurfacePausedTest > a paused plan with a call in flight says it will pick up where it stopped`. **`LauncherAgentSessionTest > continuing a session interrupted mid-tool-call…` stays GREEN** (it cannot tell a resumed call from a fresh one) — recorded, not a miss |
| M4d | `AgentSession.kt` · `            !Compensation.isUndo(plan) &&\n` → `` | an undo is never planned from its goal | `:domain` — `ResumeAgentSessionUseCaseTest > an undo plan is never planned again from its goal, which would redo what it undoes` **only** |
| M4e | `ResumeAgentSessionUseCase.kt` · `        val fresh = start.plan(paused.goal) ?: return run.run(paused.resumed())` → `        val fresh = start.plan(paused.goal) ?: return OperationResult.Success(paused)` | no plan ⇒ continue, not a dead card | `:domain` — `ResumeAgentSessionUseCaseTest > a goal that no longer plans leaves the paused plan to continue as it was` **only** |
| M4f | `LauncherAgentSession.kt` · `        if (busy) return\n        val paused` → `        val paused` | one run per double tap | `:feature:launcher` — `LauncherAgentSessionTest > a second Continue tap while the first is still running starts nothing` **only** |
| M4g | `AgentSessionSurface.kt` · `if (session.replansOnResume()) {` → `if (!session.replansOnResume()) {` | the card says what the button does | `:feature:launcher` — both `AgentSessionSurfacePausedTest` tests |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1520`.
  - Per module: `:domain` **479** (+6), `:feature:launcher` **208** (+4), the rest as after Task 3.
  - `LocaleCompletenessGuardTest` (`:app`) holds that the new key exists in all three locales.

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src feature/launcher/src app/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): возобновление — план, который только смотрел, смотрит заново

Спека §6.4 с поправкой владельца F2-3.1; staleness, слой 1. «Продолжить» у
приостановленного плана, в котором ничего не совершено (нет Effected/HandedOff,
нет вызова в полёте, план — не отмена), планирует цель заново новой сессией;
она замещает старую в хранилище (одна сессия в покое). Закрывает §12.8 A0:
пока план ждал, приложение поставили — «Продолжить» его открывает.

Карточка паузы говорит, что сделает кнопка (F2-3.2), по тому же предикату,
что и use case. «Продолжить» — одно касание, один прогон (пробел был и до
фазы; с перепланированием он удвоил бы шаг 0). Два теста LauncherAgentSession
переписаны решением владельца. Мутации M4a–M4g.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: the launcher's inverses — honest alias undo and one row per authored tool

**Spec:** §6.2; owner fork F2-3.3. **Modules:** `:data:repository`, `:app`.

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolIds.kt`
  — add `MemoryToolKeys`.
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolSource.kt`
  — `outputSchema` on the two alias tools.
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolWorker.kt`
  — constructor, `setAppAlias` (`:74-85`), `forgetAppAlias` (`:87-93`), one private helper, KDoc.
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolInverseCatalog.kt`.
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` — `provideToolInverses`.
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolWorkerTest.kt`
  — the `memoryWorker(...)` helper (`:120-128`) and +4 tests;
  `MemoryToolSourceTest.kt` +1.
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentActingSeamTest.kt`
  — the `MemoryToolWorker(` construction (`:214-218`), two assertions (`:432`, `:555`), and +2 tests
  (the end-to-end undo of spec §10 criterion 5 — added by the plan review, finding C1).
- Test (compile only): `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt:262-266`.
- Create: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolInverseCatalogTest.kt`
  (5).
- Create: `app/src/test/java/com/sidr/launcher/agent/ToolInverseCatalogGuardTest.kt` (3).

**Interfaces.**
- Consumes: from Task 3 — `ToolInverses`, `Undo`.
- Produces:
  ```kotlin
  object MemoryToolKeys { const val STORED_PHRASE = "stored_phrase"; const val PREVIOUS_APP = "previous_app" }
  class ToolInverseCatalog @Inject constructor() : ToolInverses {
      enum class Row { BY_INVERSE, NOTHING_TO_UNDO, NOT_UNDOABLE }
      fun rows(): Map<ToolId, Row>
  }
  // AgentProvidesModule.provideToolInverses(impl: ToolInverseCatalog): ToolInverses
  ```
- Outputs:
  - `set_app_alias` → `Effected(ToolOutput({stored_phrase: <key written>, previous_app: <package or "">}))`;
  - `forget_app_alias` → `Effected(ToolOutput({previous_app: <package or "">}))`.

- [ ] **Step 1: write the failing tests.**

In `MemoryToolWorkerTest`:
- Add imports `com.sidr.launcher.domain.tool.ToolOutput` and
  `com.sidr.launcher.domain.result.OperationError` (if absent).
- In `memoryWorker(...)`, after `        deleteChoice = DeleteLearnedChoiceUseCase(preferenceStore),` add
  `        aliases = aliasStore,`.
- Append:
```kotlin

    // --- A4′ phase 2: what an undo needs, reported by the act itself (spec §6.2, plan §0.3 P7) ---

    private class FindFailingAliasStore : AliasStore {
        val upserted = mutableListOf<Alias>()
        override suspend fun find(phrase: String): OperationResult<Alias?> =
            OperationResult.Failure(OperationError.UnknownError("io"))
        override suspend fun upsert(alias: Alias): OperationResult<Unit> {
            upserted += alias
            return OperationResult.Success(Unit)
        }
        override suspend fun delete(phrase: String): OperationResult<Unit> = OperationResult.Success(Unit)
        override fun observeAll(): Flow<List<Alias>> = MutableStateFlow(emptyList())
    }

    @Test
    fun `set_app_alias reports the key it wrote and that nothing was there before`() = runTest {
        val result = memoryWorker().invoke(
            ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
        )

        assertEquals(
            ToolResult.Effected(ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to ""))),
            result,
        )
    }

    /** Review Focus 2: `save` is an upsert, so this is the alias an undo must point back to. */
    @Test
    fun `set_app_alias reports the app an overwritten alias pointed at`() = runTest {
        val store = RecordingAliasStore().apply {
            upserted += Alias("вельтрикс", AliasTarget.App("net.fixture.parcel"), createdAtEpochMs = 0L)
        }

        val result = memoryWorker(aliasStore = store).invoke(
            ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
        )

        assertEquals(
            ToolResult.Effected(ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"))),
            result,
        )
    }

    @Test
    fun `forget_app_alias reports the app the removed alias pointed at`() = runTest {
        val store = RecordingAliasStore().apply {
            upserted += Alias("вельтрикс", AliasTarget.App("net.fixture.parcel"), createdAtEpochMs = 0L)
        }

        val result = memoryWorker(aliasStore = store).invoke(
            ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "вельтрикс")),
        )

        assertEquals(ToolResult.Effected(ToolOutput(mapOf(MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"))), result)
    }

    /** An act whose undo cannot be told honestly is not taken. */
    @Test
    fun `a prior alias that cannot be read writes nothing`() = runTest {
        val store = FindFailingAliasStore()
        val worker = MemoryToolWorker(
            save = SaveAliasUseCase(store),
            deleteAlias = DeleteAliasUseCase(store),
            deleteChoice = DeleteLearnedChoiceUseCase(RecordingPreferenceStore()),
            aliases = store,
        )

        val result = worker.invoke(
            ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
        )

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, store.upserted.size)
    }
```

In `MemoryToolSourceTest`, append:
```kotlin

    /** A4′ phase 2: the alias tools report what an undo needs (plan §0.3 P7); the third reports nothing. */
    @Test
    fun `the two alias tools declare what an undo needs, and forget_learned_choice declares nothing`() {
        val source = MemoryToolSource()

        assertEquals(
            listOf(MemoryToolKeys.STORED_PHRASE, MemoryToolKeys.PREVIOUS_APP),
            source.find(MemoryToolIds.SET_APP_ALIAS)!!.outputSchema.map { it.name },
        )
        assertEquals(listOf(MemoryToolKeys.PREVIOUS_APP), source.find(MemoryToolIds.FORGET_APP_ALIAS)!!.outputSchema.map { it.name })
        assertEquals(emptyList<String>(), source.find(MemoryToolIds.FORGET_LEARNED_CHOICE)!!.outputSchema.map { it.name })
    }
```

In `AgentActingSeamTest` (all found by the plan review):
- Add the imports `com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys`,
  `com.sidr.launcher.domain.agent.UndoAgentSessionUseCase`, `com.sidr.launcher.domain.memory.alias.Alias`
  and `com.sidr.launcher.domain.tool.ToolOutput`, plus any of `AliasTarget` / `first` the file lacks.
- In `federation()`, replace
  ```kotlin
                MemoryToolWorker(
                    save = SaveAliasUseCase(aliasStore),
                    deleteAlias = DeleteAliasUseCase(aliasStore),
                    deleteChoice = DeleteLearnedChoiceUseCase(preferenceStore),
                ),
  ```
  with the same block plus `                    aliases = aliasStore,` after `deleteChoice`.
- **Two assertions the owner's decision changes** (F2-3.3). At `:432` and at `:555`, replace
  `assertEquals(ToolResult.Effected(), session.observations[0])` (the first is inside
  `` `a SAFE memory tool runs to Completed without ever raising a checkpoint` ``, the second inside
  `` `parity - a recognised tool runs to completion in local-only mode with nothing leaving` ``) with
  ```kotlin
          assertEquals(
              // A4′ phase 2 (F2-3.3): the alias tool reports the key it wrote and what it replaced.
              ToolResult.Effected(ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to ALIAS_PHRASE, MemoryToolKeys.PREVIOUS_APP to ""))),
              session.observations[0],
          )
  ```
  (The second site names its session `checkNotNull(session)` inline — keep that.)
- Append:
  ```kotlin

      // --- A4′ phase 2, spec §6.2 / §10 criterion 5: compensation on launcher_memory, end to end ---

      private fun undoUseCase(federation: ToolFederation) =
          UndoAgentSessionUseCase(startUseCase(federation), ToolInverseCatalog(), federation.registry)

      /**
       * The whole chain the pieces are tested for separately: a typed alias command runs through the
       * REAL memory worker, which reports what it wrote; the REAL catalog turns that into an inverse; the
       * inverse runs as a new session through the real engine and the Room store; the alias is gone.
       */
      @Test
      fun `an alias set by a typed command is undone through the real worker, catalog, engine and store`() = runTest {
          val federation = federation()
          val (_, finished) = routeAndRun(GOAL_ALIAS, federation)
          assertEquals(ExecutionState.Completed, checkNotNull(finished).state)

          val undoing = (undoUseCase(federation).undo(finished) as OperationResult.Success).value!!
          val undone = (runUseCase(federation).run(undoing) as OperationResult.Success).value

          assertEquals(ExecutionState.Completed, undone.state)
          assertEquals(emptyList<Alias>(), aliasStore.observeAll().first())
      }

      /** Review Focus 2, end to end: the older alias comes back, it is not deleted with ours. */
      @Test
      fun `an alias that replaced an older one is pointed back by undo, not forgotten`() = runTest {
          aliasStore.upsert(Alias(ALIAS_PHRASE, AliasTarget.App("net.fixture.parcel"), createdAtEpochMs = 0L))
          val federation = federation()
          val (_, finished) = routeAndRun(GOAL_ALIAS, federation)
          check(aliasStore.observeAll().first().single().target == AliasTarget.App(TARGET_PACKAGE))

          val undoing = (undoUseCase(federation).undo(checkNotNull(finished)) as OperationResult.Success).value!!
          runUseCase(federation).run(undoing)

          assertEquals(AliasTarget.App("net.fixture.parcel"), aliasStore.observeAll().first().single().target)
      }
  ```
  `ALIAS_PHRASE` is `"marmoset"`, already normalized, so the stored key equals it. If `routeAndRun`'s
  resolver in this file cannot resolve `TARGET_LABEL` while an older alias exists, read the file's
  `GOAL_ALIAS` fixture and seed the prior alias under the same key the worker writes.

In `DoctrineGuardTest.kt:262-266` (compile only), replace
```kotlin
        memoryWorker = MemoryToolWorker(
            save = SaveAliasUseCase(FakeAliasStore()),
            deleteAlias = DeleteAliasUseCase(FakeAliasStore()),
            deleteChoice = DeleteLearnedChoiceUseCase(FakeResolutionPreferenceStore()),
        ),
```
with
```kotlin
        memoryWorker = FakeAliasStore().let { aliases ->
            MemoryToolWorker(
                save = SaveAliasUseCase(aliases),
                deleteAlias = DeleteAliasUseCase(aliases),
                deleteChoice = DeleteLearnedChoiceUseCase(FakeResolutionPreferenceStore()),
                aliases = aliases,
            )
        },
```

Create `ToolInverseCatalogTest.kt`:
```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Test

/** A4′ phase 2, spec §6.2: which launcher tool undoes which, and — as often — why none does. */
class ToolInverseCatalogTest {

    private val catalog = ToolInverseCatalog()

    private fun effected(vararg values: Pair<String, String>) = ToolResult.Effected(ToolOutput(mapOf(*values)))

    @Test
    fun `a new alias is undone by forgetting the key that was written`() {
        assertEquals(
            Undo.By(ToolInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to ArgSource.Literal("вельтрикс")))),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "  ВЕЛЬТРИКС ")),
                effected(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to ""),
            ),
        )
    }

    /** Review Focus 2. Forgetting here would delete the user's older alias along with ours. */
    @Test
    fun `an alias that replaced another is undone by pointing it back, not by forgetting it`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    MemoryToolIds.SET_APP_ALIAS,
                    mapOf("app" to ArgSource.Literal("net.fixture.parcel"), "phrase" to ArgSource.Literal("вельтрикс")),
                ),
            ),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
                effected(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"),
            ),
        )
    }

    @Test
    fun `a forgotten alias is undone by setting it again`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    MemoryToolIds.SET_APP_ALIAS,
                    mapOf("app" to ArgSource.Literal("net.fixture.parcel"), "phrase" to ArgSource.Literal("вельтрикс")),
                ),
            ),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "вельтрикс")),
                effected(MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"),
            ),
        )
    }

    @Test
    fun `forgetting an alias that did not exist leaves nothing to undo`() {
        assertEquals(
            Undo.NothingToUndo,
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "вельтрикс")),
                effected(MemoryToolKeys.PREVIOUS_APP to ""),
            ),
        )
    }

    @Test
    fun `a learned choice, a running timer, a removed app and a shortcut cannot be undone here - a screen leaves nothing`() {
        val cases = mapOf(
            MemoryToolIds.FORGET_LEARNED_CHOICE to Undo.NotUndoable,
            Tier0ToolIds.SET_TIMER to Undo.NotUndoable,
            Tier0ToolIds.UNINSTALL_APP to Undo.NotUndoable,
            ToolId("shortcut:net.fixture.alpha/compose") to Undo.NotUndoable,
            Tier0ToolIds.OPEN_WIFI_SETTINGS to Undo.NothingToUndo,
        )
        cases.forEach { (id, expected) ->
            assertEquals("$id", expected, catalog.undoOf(ResolvedInvocation(id), ToolResult.Effected()))
        }
    }
}
```

Create `app/src/test/java/com/sidr/launcher/agent/ToolInverseCatalogGuardTest.kt`:
```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.ToolInverseCatalog
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2, Task 5. `ToolInverseCatalog` answers "how is this undone" for the tools the launcher
 * AUTHORS, and a missing row means `NotUndoable` — fail-safe, but silent: a new navigating tool without
 * a row would be named «не отменить» on a card when the truth is «нечего отменять». This guard makes a
 * new authored tool force the question, over the PRODUCTION federation's authored adapters. Shortcuts
 * are dynamic and have no row by design (spec §6.2: the publisher's intent is unknown).
 *
 * This is one more list that changes when a tool is added — the family A1″ residual (5) measured
 * (five → eight lists). It is registry-KEYED, so it cannot silently skip a tool, but it is still a list.
 */
class ToolInverseCatalogGuardTest {

    private val catalog = ToolInverseCatalog()

    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Failed(com.sidr.launcher.domain.intent.CommandFailure.Generic)
    }

    /** Everything granted, for the reason `ToolRegistryPermissionGuardTest` states: a filtered registry would pass by absence. */
    private val authored = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }), NoopWorker),
            ToolAdapter(ToolLevels.LAUNCHER_MEMORY, MemoryToolSource(), NoopWorker),
        ),
    ).registry

    @Test
    fun `every authored tool has a row, so a new one forces the undo question`() {
        assertEquals(20, authored.all().size)
        assertEquals(authored.all().map { it.id }.toSet(), catalog.rows().keys)
    }

    @Test
    fun `a durable tool is never declared undoable`() {
        val durable = authored.all().filter { it.durability == ToolDurability.DURABLE }
        assertTrue("no DURABLE tool reached this guard — it would pass by absence", durable.isNotEmpty())
        durable.forEach { assertEquals("${it.id}", ToolInverseCatalog.Row.NOT_UNDOABLE, catalog.rows()[it.id]) }
    }

    /**
     * Every inverse a row promises is a registered tool whose arguments validate — the undo can run.
     * Both branches of each row: with a prior target (point back / set again) and, where the row has
     * one, without (a new alias → forget; a forget of nothing → nothing to undo, so skipped).
     */
    @Test
    fun `every inverse a row promises names a tool the same federation can run`() {
        val promising = catalog.rows().filterValues { it == ToolInverseCatalog.Row.BY_INVERSE }.keys
        assertTrue(promising.isNotEmpty())
        var checked = 0
        for (previous in listOf("net.fixture.parcel", "")) {
            val output = ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to previous))
            promising.forEach { id ->
                val undo = catalog.undoOf(
                    ResolvedInvocation(id, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
                    ToolResult.Effected(output),
                )
                if (undo is Undo.By) {
                    checked++
                    assertEquals("$id / previous='$previous' must validate", InvocationCheck.Valid, InvocationValidator.validate(undo.invocation, emptyList(), authored))
                }
            }
        }
        assertEquals("both rows with a prior target, and set_app_alias without one", 3, checked)
    }
}
```

- [ ] **Step 2: run — expect compile-red.**
  - `:data:repository`: `Unresolved reference 'MemoryToolKeys'`, `'ToolInverseCatalog'`, and
    `No parameter with name 'aliases'`.
  - `:app`: `Unresolved reference 'ToolInverseCatalog'`.

- [ ] **Step 3: implement.**

`MemoryToolIds.kt` — append:
```kotlin

/**
 * What the two alias tools report, so an undo can be told honestly (A4′ phase 2, spec §6.2, plan §0.3
 * P7): the key actually written — `SaveAliasUseCase` normalizes it, `forget_app_alias` does not — and
 * the package the key pointed at BEFORE, blank when there was none.
 */
object MemoryToolKeys {
    const val STORED_PHRASE = "stored_phrase"
    const val PREVIOUS_APP = "previous_app"
}
```

`MemoryToolSource.kt`:
- In `SET_APP_ALIAS`'s descriptor, after its `argSchema = listOf(...)` block, add:
```kotlin
            outputSchema = listOf(
                ActionArg(MemoryToolKeys.STORED_PHRASE, description = "The alias key as stored"),
                ActionArg(MemoryToolKeys.PREVIOUS_APP, description = "The package the key pointed at before; blank if it was new"),
            ),
```
- In `FORGET_APP_ALIAS`'s descriptor, after its `argSchema`, add:
```kotlin
            outputSchema = listOf(
                ActionArg(MemoryToolKeys.PREVIOUS_APP, description = "The package the removed alias pointed at; blank if there was none"),
            ),
```

`MemoryToolWorker.kt`:
- Add the imports `com.sidr.launcher.domain.intent.CommandNormalizer`,
  `com.sidr.launcher.domain.memory.alias.AliasStore`,
  `com.sidr.launcher.domain.memory.alias.appPackageOrNull` and
  `com.sidr.launcher.domain.tool.ToolOutput`.
- Change the constructor to add, after `    private val deleteChoice: DeleteLearnedChoiceUseCase,`:
  `    private val aliases: AliasStore,`.
- Replace `setAppAlias`'s tail
```kotlin
        return when (save.save(phrase, AliasTarget.App(app))) {
            is OperationResult.Success -> ToolResult.Effected()
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }
```
with:
```kotlin
        // A4′ phase 2 (spec §6.2): read what the key points at BEFORE writing. `save` is an upsert, so
        // an undo that only forgot would delete the user's older alias along with ours. A prior alias
        // that cannot be read writes nothing — an act whose undo cannot be told is not taken.
        val key = CommandNormalizer.normalize(phrase)
        val replaced = aliasTargetOf(key) ?: return ToolResult.Failed(CommandFailure.Generic)
        return when (save.save(phrase, AliasTarget.App(app))) {
            is OperationResult.Success -> ToolResult.Effected(
                ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to key, MemoryToolKeys.PREVIOUS_APP to replaced)),
            )
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }
```
- Replace `forgetAppAlias`:
```kotlin
    private suspend fun forgetAppAlias(args: Map<String, String>): ToolResult {
        val phrase = args["phrase"].orEmpty()
        return when (deleteAlias.delete(phrase)) {
            is OperationResult.Success -> ToolResult.Effected()
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }
```
with:
```kotlin
    private suspend fun forgetAppAlias(args: Map<String, String>): ToolResult {
        val phrase = args["phrase"].orEmpty()
        // The same key the delete uses — unchanged, never re-normalized (the test above pins why).
        val removed = aliasTargetOf(phrase) ?: return ToolResult.Failed(CommandFailure.Generic)
        return when (deleteAlias.delete(phrase)) {
            is OperationResult.Success -> ToolResult.Effected(ToolOutput(mapOf(MemoryToolKeys.PREVIOUS_APP to removed)))
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }

    /** The package [key] points at now, `""` when nothing, `null` when the store could not say. */
    private suspend fun aliasTargetOf(key: String): String? = when (val found = aliases.find(key)) {
        is OperationResult.Success -> found.value?.target?.appPackageOrNull().orEmpty()
        is OperationResult.Failure -> null
    }
```
- In the class KDoc, replace the paragraph starting
  `**`Effected` for `forget_*` means the operation RAN, not that a row was removed.**` with:
```kotlin
 * **`Effected` for `forget_*` means the operation RAN, not that a row was removed.** Since A4′ phase 2
 * `forget_app_alias` reads the alias before deleting it and reports what it pointed at
 * ([MemoryToolKeys.PREVIOUS_APP]) — blank means there was nothing to remove, and an undo then has
 * nothing to put back. `forget_learned_choice` reports nothing, and cannot be undone.
```

Create `ToolInverseCatalog.kt`:
```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import javax.inject.Inject

/**
 * How each tool the launcher authors is undone (A4′ phase 2, spec §6.2). **Compensable: one family of
 * six, and within it two tools of three** — said, not implied:
 *  - `set_app_alias` → forget the key it wrote, or, if it replaced an alias, point the key back;
 *  - `forget_app_alias` → set the alias again, if there was one;
 *  - everything else either leaves nothing that stays (a screen, a launch, a search) or changed
 *    something no registered tool puts back (a running timer, an alarm, a removed app, a learned
 *    streak).
 *
 * A missing row means [Undo.NotUndoable] — which is also the answer for every app-shortcut tool, whose
 * publisher's intent is unknown. `ToolInverseCatalogGuardTest` (`:app`) makes every AUTHORED tool have
 * an explicit row, so a new one cannot be silently misnamed.
 *
 * The inverses are the tools' own siblings, and they pass the same gates (spec §6.3): both are `SAFE`
 * today, so undo asks nothing, exactly as the act asked nothing.
 */
class ToolInverseCatalog @Inject constructor() : ToolInverses {

    enum class Row { BY_INVERSE, NOTHING_TO_UNDO, NOT_UNDOABLE }

    fun rows(): Map<ToolId, Row> = ROWS

    override fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo = when (forward.id) {
        MemoryToolIds.SET_APP_ALIAS -> {
            val key = result.output.values[MemoryToolKeys.STORED_PHRASE].orEmpty()
            val previous = result.output.values[MemoryToolKeys.PREVIOUS_APP].orEmpty()
            when {
                key.isBlank() -> Undo.NotUndoable
                previous.isBlank() -> Undo.By(
                    ToolInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to ArgSource.Literal(key))),
                )
                else -> Undo.By(
                    ToolInvocation(
                        MemoryToolIds.SET_APP_ALIAS,
                        mapOf("app" to ArgSource.Literal(previous), "phrase" to ArgSource.Literal(key)),
                    ),
                )
            }
        }
        MemoryToolIds.FORGET_APP_ALIAS -> {
            val phrase = forward.args["phrase"].orEmpty()
            val previous = result.output.values[MemoryToolKeys.PREVIOUS_APP].orEmpty()
            if (phrase.isBlank() || previous.isBlank()) {
                Undo.NothingToUndo
            } else {
                Undo.By(
                    ToolInvocation(
                        MemoryToolIds.SET_APP_ALIAS,
                        mapOf("app" to ArgSource.Literal(previous), "phrase" to ArgSource.Literal(phrase)),
                    ),
                )
            }
        }
        else -> when (ROWS[forward.id]) {
            Row.NOTHING_TO_UNDO -> Undo.NothingToUndo
            Row.BY_INVERSE, Row.NOT_UNDOABLE, null -> Undo.NotUndoable
        }
    }

    private companion object {
        val ROWS: Map<ToolId, Row> = mapOf(
            ToolIds.LAUNCH_APP to Row.NOTHING_TO_UNDO,
            ToolIds.PLAY_STORE_SEARCH to Row.NOTHING_TO_UNDO,
            // A real timer / alarm runs (spec §6.2, rows 13/29 of the A1″ measurements); no tool removes one.
            Tier0ToolIds.SET_TIMER to Row.NOT_UNDOABLE,
            Tier0ToolIds.SET_ALARM to Row.NOT_UNDOABLE,
            // DURABLE, and it reports HandedOff — which Compensation never offers anyway.
            Tier0ToolIds.UNINSTALL_APP to Row.NOT_UNDOABLE,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.SHOW_ALARMS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_CAMERA to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_WIFI_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_SOUND_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_APP_INFO to Row.NOTHING_TO_UNDO,
            MemoryToolIds.SET_APP_ALIAS to Row.BY_INVERSE,
            MemoryToolIds.FORGET_APP_ALIAS to Row.BY_INVERSE,
            // A learned streak cannot be put back by any tool.
            MemoryToolIds.FORGET_LEARNED_CHOICE to Row.NOT_UNDOABLE,
        )
    }
}
```

`AgentProvidesModule.kt` — add the imports `com.sidr.launcher.data.repository.agent.ToolInverseCatalog`
and `com.sidr.launcher.domain.tool.ToolInverses`, and after `provideToolExecutor` add:
```kotlin

    /** A4′ phase 2 (spec §6.2): how the launcher's tools are undone. Read by compensation only. */
    @Provides
    @Singleton
    fun provideToolInverses(impl: ToolInverseCatalog): ToolInverses = impl
```

- [ ] **Step 4: run — expect green.**
  - `:data:repository:testDebugUnitTest` **408**;
  - `:app:testDebugUnitTest` **66**.

- [ ] **Step 5: mutations — five.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M5a | `MemoryToolWorker.kt` · `MemoryToolKeys.PREVIOUS_APP to replaced` → `MemoryToolKeys.PREVIOUS_APP to ""` | the overwrite is reported | `:data:repository` — `MemoryToolWorkerTest > set_app_alias reports the app an overwritten alias pointed at`; `AgentActingSeamTest > an alias that replaced an older one is pointed back by undo, not forgotten` |
| M5b | `MemoryToolWorker.kt` · `val replaced = aliasTargetOf(key) ?: return ToolResult.Failed(CommandFailure.Generic)` → `val replaced = aliasTargetOf(key) ?: ""` | an unreadable prior writes nothing | `:data:repository` — `MemoryToolWorkerTest > a prior alias that cannot be read writes nothing` **only** |
| M5c | `ToolInverseCatalog.kt` · `                previous.isBlank() -> Undo.By(` → `                true -> Undo.By(` | an overwrite is pointed back, not forgotten | `:data:repository` — `ToolInverseCatalogTest > an alias that replaced another is undone by pointing it back, not by forgetting it`; `AgentActingSeamTest > an alias that replaced an older one is pointed back by undo, not forgotten` |
| M5d | `ToolInverseCatalog.kt` · `            Tier0ToolIds.OPEN_CAMERA to Row.NOTHING_TO_UNDO,\n` → `` | every authored tool has a row | `:app` — `ToolInverseCatalogGuardTest > every authored tool has a row, so a new one forces the undo question` **only** |
| M5e | `ToolInverseCatalog.kt` · `            Tier0ToolIds.UNINSTALL_APP to Row.NOT_UNDOABLE,` → `            Tier0ToolIds.UNINSTALL_APP to Row.NOTHING_TO_UNDO,` | a durable tool is never undoable-in-name | `:app` — `ToolInverseCatalogGuardTest > a durable tool is never declared undoable`. `:data:repository` — `ToolInverseCatalogTest > a learned choice, a running timer, a removed app and a shortcut cannot be undone here - a screen leaves nothing` |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1535`.
  - Per module: `:data:repository` **408** (+12), `:app` **66** (+3), the rest as after Task 4.
  - `DoctrineGuardTest.declaredRisk` is untouched (no risk moved).

- [ ] **Step 7: commit (controller).**

```bash
git add data/repository/src app/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): обратные шаги лаунчера — честная отмена алиаса, строка на каждый авторский инструмент

Спека §6.2. set_app_alias — upsert: отмена через «забыть» удалила бы
прежний алиас пользователя. Теперь инструмент сам сообщает записанный ключ и
на что он указывал раньше; отмена возвращает прежний, а не удаляет. Если
прежнее не прочитать — запись не делается. forget_app_alias сообщает, что
удалил.

ToolInverseCatalog: 20 строк — отменяемы два инструмента одной семьи,
остальное «нечего отменять» или «не отменить»; ярлыки — не отменить (намерение
издателя неизвестно). Гвард по production-федерации: у каждого авторского
инструмента строка, DURABLE — никогда не отменяем. Мутации M5a–M5e.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: «Отменить» on the card

**Spec:** §6.6 (`B14`), §6.3; owner fork F2-3.3. **Modules:** `:feature:launcher`, `:app`.

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSession.kt`
  — constructor, an `undoReview` state, `undo()`, and one private `show(...)` through which every
  `_session.value =` assignment goes.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt` —
  constructor, the `LauncherAgentSession(` block, `agentUndoReview`, `undoAgentSession()`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt:119-120`,
  `:296-305`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt`
  — the signature, and the `Completed` / `Failed` / `Blocked` branches.
- Modify: `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` — two keys each.
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` — one provider.
- Modify (tests): `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/AgentUseCaseFixtures.kt`
  (+ `undoOver`); the six `LauncherViewModel(` constructions; `LauncherAgentSessionTest.collaborator`.
- Create: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurfaceUndoTest.kt`
  (4).
- Test: `LauncherAgentSessionTest` (+3); `LauncherScreenAgentProvenanceTest` (+1, the seam).

**Interfaces.**
- Consumes: from Task 3 — `UndoAgentSessionUseCase`, `StepUndo`, `Undo`; from Task 4 — the
  `busy` flag and `LauncherAgentSession`'s constructor; from Task 5 — the `ToolInverses` binding.
- Produces:
  ```kotlin
  // LauncherAgentSession(..., resumeSession, undoSession: UndoAgentSessionUseCase)
  val undoReview: StateFlow<List<StepUndo>>
  fun undo()
  // LauncherViewModel(..., resumeAgentSession, undoAgentSession: UndoAgentSessionUseCase, ...)
  val agentUndoReview: StateFlow<List<StepUndo>>
  fun undoAgentSession()
  // AgentSessionSurface(..., undoReview: List<StepUndo> = emptyList(), onUndo: () -> Unit = {}, modifier)
  internal fun undoOver(store: AgentSessionStore, registry: ToolRegistry, inverses: ToolInverses = …): UndoAgentSessionUseCase
  ```
- Strings:
  - `launcher_agent_undo`: «Undo» / «Отменить» / «Geri al»;
  - `launcher_agent_undo_not_all`: «Can’t be undone here: %1$s» / «Здесь не отменить: %1$s» /
    «Burada geri alınamaz: %1$s».

- [ ] **Step 1: write the failing tests.**

Append to `AgentUseCaseFixtures.kt`:
```kotlin

/** An undo that has nothing to offer — what every test that does not exercise undo passes. */
internal val noInverses = com.sidr.launcher.domain.tool.ToolInverses { _, _ -> com.sidr.launcher.domain.tool.Undo.NotUndoable }

internal fun undoOver(
    store: AgentSessionStore,
    registry: ToolRegistry,
    inverses: com.sidr.launcher.domain.tool.ToolInverses = noInverses,
): com.sidr.launcher.domain.agent.UndoAgentSessionUseCase = com.sidr.launcher.domain.agent.UndoAgentSessionUseCase(
    StartAgentSessionUseCase(TemplatePlanner(), store, SequentialIds("undo-"), registry),
    inverses,
    registry,
)
```
(Or add the three imports at the top instead of qualifying. Either way is fine; the file is test-only.)

**The six `LauncherViewModel(` constructions (compile only).** After each line inserted in Task 4,
`resumeAgentSession = resumeOver(<store>, <run>, agentRegistry),`, insert
`undoAgentSession = undoOver(<store>, agentRegistry),` with the same `<store>` and the same
indentation. The exception is `LauncherScreenAgentProvenanceTest`: its `buildViewModel` gains the
parameters described in that test below.

In `LauncherAgentSessionTest`:
- Add the imports `com.sidr.launcher.domain.agent.UndoAgentSessionUseCase`,
  `com.sidr.launcher.domain.agent.StepUndo`, `com.sidr.launcher.domain.agent.StepRationale`,
  `com.sidr.launcher.domain.agent.ExecutionPlan`, `com.sidr.launcher.domain.agent.PlanStep`,
  `com.sidr.launcher.domain.agent.StepPrecondition`, `com.sidr.launcher.domain.action.ActionArg`,
  `com.sidr.launcher.domain.action.ActionRiskLevel`, `com.sidr.launcher.domain.result.OperationResult`,
  `com.sidr.launcher.domain.tool.ArgSource`, `ToolDescriptor`, `ToolDurability`, `ToolEffect`,
  `ToolId`, `ToolInverses`, `ToolInvocation`, `ToolLevels`, `ToolRegistry`, `Undo` (all in
  `com.sidr.launcher.domain.tool`) and `kotlinx.coroutines.test.TestScope` — the review listed them.
- Change `collaborator(...)` to take `registry: ToolRegistry = this.registry` and
  `inverses: ToolInverses = ToolInverses { _, _ -> Undo.NotUndoable }`, to build its runner over that
  `registry`, and after the `resumeSession = …` line add
  `            undoSession = UndoAgentSessionUseCase(StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry), inverses, registry),`.
- Append:
```kotlin

    // --- «Отменить» (A4′ phase 2, spec §6.6, owner fork F2-3.3) --------------------------------

    private val note = ToolId("fixture_note")
    private val unnote = ToolId("fixture_unnote")
    private val undoRegistry = FakeToolRegistry(
        listOf(note, unnote).map {
            ToolDescriptor(id = it, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(ActionArg("name", description = "fixture")), risk = ActionRiskLevel.SAFE, durability = ToolDurability.TRANSIENT)
        },
    )
    private val undoInverses = ToolInverses { forward, _ ->
        if (forward.id == note) Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name"))))) else Undo.NotUndoable
    }
    private val noteGoal = AgentGoal("запомни вельтрикс", GoalShape.Free("запомни вельтрикс"))
    private val notePlan = ExecutionPlan(
        listOf(PlanStep(0, ToolInvocation(note, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)),
    )

    /** A finished one-step note plan on screen, the way a real one arrives: started, then attached. */
    private suspend fun TestScope.finishedNote(tools: FakeToolExecutor): LauncherAgentSession {
        val agent = collaborator(tools, this, registry = undoRegistry, inverses = undoInverses)
        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, undoRegistry).startPlanned(noteGoal, notePlan)
        agent.attach((started as OperationResult.Success).value!!.id)
        advanceUntilIdle()
        check(agent.session.value?.state == ExecutionState.Completed) { "seed expected Completed, was ${agent.session.value?.state}" }
        return agent
    }

    @Test
    fun `the undo review follows the session on screen`() = runTest {
        val agent = finishedNote(FakeToolExecutor(listOf(ToolResult.Effected())))

        assertEquals(
            listOf(StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс")))))),
            agent.undoReview.value,
        )

        agent.cancel()
        advanceUntilIdle()
        assertEquals(emptyList<StepUndo>(), agent.undoReview.value)
    }

    @Test
    fun `Undo starts a session that undoes the finished plan and shows it`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected()))
        val agent = finishedNote(tools)

        agent.undo()
        advanceUntilIdle()

        assertEquals(listOf(note, unnote), tools.invocations.map { it.id })
        assertEquals(ExecutionState.Completed, agent.session.value?.state)
        assertEquals(listOf(StepRationale.COMPENSATION), agent.session.value?.plan?.steps?.map { it.rationale })
        assertEquals("an undo offers no undo of its own", emptyList<StepUndo>(), agent.undoReview.value)
    }

    /** Review Focus 1: two taps, two new sessions otherwise — the store's conditional write cannot see them. */
    @Test
    fun `a second Undo tap while the first is running starts nothing`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected(), ToolResult.Effected()))
        val agent = finishedNote(tools)

        agent.undo()
        agent.undo()
        advanceUntilIdle()

        assertEquals(listOf(note, unnote), tools.invocations.map { it.id })
    }
```
(`TestScope` is `kotlinx.coroutines.test.TestScope`. `attach` is `LauncherAgentSession`'s existing
entry for a session the command path started.)

Create `AgentSessionSurfaceUndoTest.kt`:
```kotlin
package com.sidr.launcher.feature.launcher.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, owner fork F2-3.3 — `B14`'s «что откатить» as an action on the same card. The card
 * offers «Undo» only when something can be undone, and — when only part can — names what cannot, so
 * the button's reach is never implied larger than it is. On Android today the mixed case is reachable
 * only through phase 3's plans (plan §0.3 P8); it is rendered here from a constructed session.
 *
 * `memory tool` ids are written as literals: `MemoryToolIds` lives in `:data:repository`, which this
 * module must not depend on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceUndoTest {

    @get:Rule val compose = createComposeRule()

    private val setAlias = ToolId("set_app_alias")
    private val forgetChoice = ToolId("forget_learned_choice")
    private val byUndo = Undo.By(ToolInvocation(ToolId("forget_app_alias"), mapOf("phrase" to ArgSource.Literal("вельтрикс"))))

    private fun aliasStep(index: Int) = PlanStep(
        index,
        ToolInvocation(setAlias, mapOf("app" to ArgSource.Literal("net.fixture.quill"), "app_label" to ArgSource.Literal("Lumora"), "phrase" to ArgSource.Literal("вельтрикс"))),
        ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    private fun choiceStep(index: Int) = PlanStep(
        index, ToolInvocation(forgetChoice, mapOf("phrase" to ArgSource.Literal("лумора"))),
        ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    private fun session(state: ExecutionState, steps: List<PlanStep>, observations: Map<Int, ToolResult>) = AgentSession(
        id = AgentSessionId("s-undo-card"),
        goal = AgentGoal("назови вельтрикс", GoalShape.Free("назови вельтрикс")),
        plan = ExecutionPlan(steps),
        cursor = steps.size,
        state = state,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    private fun render(session: AgentSession, review: List<StepUndo>, onUndo: () -> Unit = {}) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = session,
                    toolProvenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                    undoReview = review,
                    onUndo = onUndo,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a finished plan with something to undo offers Undo, and Undo is what it calls`() {
        var undone = false
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0)), mapOf(0 to ToolResult.Effected())),
            listOf(StepUndo(0, byUndo)),
            onUndo = { undone = true },
        )

        compose.onNodeWithText("Undo").performClick()

        assertTrue(undone)
    }

    @Test
    fun `nothing to undo means no Undo`() {
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0)), mapOf(0 to ToolResult.Effected())),
            listOf(StepUndo(0, Undo.NothingToUndo)),
        )

        compose.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `when only part can be undone the card names what cannot`() {
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0), choiceStep(1)), mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected())),
            listOf(StepUndo(1, Undo.NotUndoable), StepUndo(0, byUndo)),
        )

        compose.onNodeWithText("Undo").assertExists()
        compose.onNodeWithText("Can’t be undone here: Forget the learned choice for “лумора”").assertExists()
    }

    @Test
    fun `a plan that failed part-way offers Undo too`() {
        render(
            session(ExecutionState.Failed, listOf(aliasStep(0), choiceStep(1)), mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Failed(CommandFailure.Generic))),
            listOf(StepUndo(0, byUndo)),
        )

        compose.onNodeWithText("Undo").assertExists()
    }
}
```

**The seam — `LauncherScreenAgentProvenanceTest`** (+1). Give `buildViewModel()` two parameters,
`tools: FakeToolExecutor = FakeToolExecutor(emptyList())` and
`inverses: ToolInverses = noInverses`. Use `tools` in `runAgent`'s `AgentExecutor(...)` in place of
`FakeToolExecutor(emptyList())`. Pass `resumeAgentSession = resumeOver(agentStore, runAgent, agentRegistry),`
and `undoAgentSession = undoOver(agentStore, agentRegistry, inverses),`. Then append:
```kotlin

    /**
     * A4′ phase 2 — the seam for «Отменить», the way the precedent seams hold `DOC-ILM-2`: the real
     * screen, the real ViewModel, the real collaborator and use cases, and a fake only at the world.
     *
     * A session that died mid-call on `launch_app` comes back Paused; «Continue» performs the pending
     * call (a call in flight is resumed, never re-planned — plan Task 4), which finishes the plan; the
     * finished card offers «Undo»; «Undo» starts an undo session. The fixture's inverse for
     * `launch_app` is `play_store_search` — **a fixture pairing, not a claim that a store search
     * undoes a launch**: it is the one pair of tools this registry has, and `play_store_search` is
     * CONFIRM here, so the undo stops at the gate (spec §6.3) and nothing further reaches the world.
     */
    @Test
    fun `a finished plan on the screen offers Undo, and Undo reaches the engine through its gate`() {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val inverses = ToolInverses { forward, _ ->
            if (forward.id == ToolIds.LAUNCH_APP) {
                Undo.By(ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal(forward.args.getValue("query")))))
            } else {
                Undo.NotUndoable
            }
        }
        runBlocking { seedPausedMidCall("лумора") }
        val vm = buildViewModel(tools, inverses)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()

        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        compose.onNodeWithText("Confirm this step").assertExists()
    }
```
`seedPausedMidCall(name)` is a new private helper next to `seedPausedA0Session`. It saves an
`AgentSession` with:
- plan `[PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal(name))), SAFE, None, GOAL_DIRECT)]`;
- `cursor = 0`, `state = Paused`;
- trace `[PlanCreated(1), StepStarted(0), ToolInvoked(0, ToolIds.LAUNCH_APP), SessionPaused]`.

The file sets `Dispatchers.Main` to an `UnconfinedTestDispatcher` (`:127`, `:137`), so the
ViewModel's coroutines run eagerly and `compose.waitForIdle()` is the only wait needed — the idiom of
its existing tests. `agentRegistry` is `FakeToolRegistry.withA0Tools()` by default, where
`play_store_search` is `CONFIRM`; this test must not swap the registry. Add the imports `ToolInverses`,
`Undo`, `ToolInvocation`, `ArgSource` and `ToolResult` as needed.

- [ ] **Step 2: run — expect compile-red.** `:feature:launcher`:
  - `No parameter with name 'undoSession'` / `'undoAgentSession'`;
  - `Unresolved reference 'undoReview'` / `'undo'`;
  - `No parameter with name 'undoReview'` (surface).

- [ ] **Step 3: implement.**

`LauncherAgentSession.kt`:
- Add the imports `com.sidr.launcher.domain.agent.StepUndo` and
  `com.sidr.launcher.domain.agent.UndoAgentSessionUseCase`.
- Add the constructor parameter after `resumeSession`:
  `    private val undoSession: UndoAgentSessionUseCase,`.
- After the `confirming` property, add:
```kotlin

    /**
     * What «Отменить» would undo for the session on screen, and what it could not (A4′ phase 2, spec
     * §6.6). Recomputed every time the session on screen changes — through [show], never by a
     * collector that would outlive a test's scope.
     */
    private val _undoReview = MutableStateFlow<List<StepUndo>>(emptyList())
    val undoReview: StateFlow<List<StepUndo>> = _undoReview.asStateFlow()

    private fun show(next: AgentSession?) {
        _session.value = next
        _undoReview.value = next?.let(undoSession::review).orEmpty()
    }
```
- Replace every assignment `_session.value = X` in the file with `show(X)`. There are six:
  - `attach`;
  - `restoreOnStart` ×2 (`_session.value = active`, `_session.value = paused`);
  - `continueSession`;
  - `resolve`;
  - `cancel` (`_session.value = null` → `show(null)`).
- Append:
```kotlin

    /**
     * «Отменить» (owner fork F2-3.3): a NEW session that undoes the finished one on screen, run through
     * the same engine and gates. Never automatic. Shares [busy] with [continueSession] — one run at a
     * time from this card, and a second tap does nothing (plan §0.3 P6).
     */
    fun undo() {
        if (busy) return
        val finished = _session.value ?: return
        busy = true
        scope.launch {
            try {
                val undoing = (undoSession.undo(finished) as? OperationResult.Success)?.value ?: return@launch
                show((runSession.run(undoing) as? OperationResult.Success)?.value)
            } finally {
                busy = false
            }
        }
    }
```

`LauncherViewModel.kt`:
- Add the imports `com.sidr.launcher.domain.agent.StepUndo` and
  `com.sidr.launcher.domain.agent.UndoAgentSessionUseCase`.
- Constructor: after `    private val resumeAgentSession: ResumeAgentSessionUseCase,` add
  `    private val undoAgentSession: UndoAgentSessionUseCase,`.
- `LauncherAgentSession(` block: after `        resumeSession = resumeAgentSession,` add
  `        undoSession = undoAgentSession,`.
- After `val agentConfirming…` add:
```kotlin

    /** What «Отменить» would undo on the agent card now on screen (A4′ phase 2). */
    val agentUndoReview: StateFlow<List<StepUndo>> get() = agentSession.undoReview
```
- After `continueAgentSession()` add:
```kotlin

    /** Undo what the finished plan on screen did, as far as it can be undone — see [LauncherAgentSession.undo]. */
    fun undoAgentSession() {
        agentSession.undo()
    }
```

`LauncherScreen.kt`:
- After `:120` (`val agentConfirming by …`) add
  `    val agentUndoReview by viewModel.agentUndoReview.collectAsStateWithLifecycle()`.
- In the `AgentSessionSurface(` call, after `onDismiss = viewModel::dismissAgentSession,` add:
```kotlin
                    undoReview = agentUndoReview,
                    onUndo = viewModel::undoAgentSession,
```

`AgentSessionSurface.kt`:
- Add the imports `com.sidr.launcher.domain.agent.StepUndo` and `com.sidr.launcher.domain.tool.Undo`.
- In the signature, before `    modifier: Modifier = Modifier,` add:
```kotlin
    // A4′ phase 2 (F2-3.3). Defaulted so a caller that has no undo — a test of another state — draws
    // no button; the one production caller, `LauncherScreen`, passes both.
    undoReview: List<StepUndo> = emptyList(),
    onUndo: () -> Unit = {},
```
- After `    val dismiss = SidrSurfaceAction(sidrString(R.string.launcher_agent_cancel), onDismiss)` add:
```kotlin
    // «Отменить» exists only when at least one step can actually be undone; and when only part can,
    // the card names the rest, so the button's reach is never implied larger than it is (B14).
    val undo = if (undoReview.any { it.undo is Undo.By }) {
        SidrSurfaceAction(sidrString(R.string.launcher_agent_undo), onUndo)
    } else {
        null
    }
    val notUndoable = if (undo == null) {
        null
    } else {
        undoReview
            .filter { it.undo == Undo.NotUndoable }
            .mapNotNull { pending -> session.plan.steps.firstOrNull { it.index == pending.stepIndex } }
            .map { it.line(subject, dynamicLabels) }
            .takeIf { it.isNotEmpty() }
            ?.let { sidrString(R.string.launcher_agent_undo_not_all, it.joinToString(", ")) }
    }
```
- In `ExecutionState.Completed`'s `SidrResultSurface(...)`:
  - add `                secondaryAction = undo,` after `                primaryAction = dismiss,`;
  - inside its `provenance = { … }` lambda, after `AgentProvenance(session = session)`, add
    `                    notUndoable?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }`.
- Replace `ExecutionState.Failed`'s surface with:
```kotlin
        ExecutionState.Failed -> SidrErrorSurface(
            title = title,
            modifier = modifier,
            next = notUndoable,
            primaryAction = dismiss,
            secondaryAction = undo,
        )
```
- Replace `ExecutionState.Blocked`'s surface with:
```kotlin
        ExecutionState.Blocked -> SidrBlockedState(
            title = title,
            body = sidrString(R.string.launcher_agent_blocked_body),
            modifier = modifier,
            next = notUndoable,
            primaryAction = dismiss,
            secondaryAction = undo,
        )
```

**The `.map { it.line(...) }` call.** `line` is `@Composable`, and `map` / `mapNotNull` are inline, so
the call compiles inside them. If the compiler objects, build the list with a plain `for` loop into a
local `mutableListOf`.

Strings — after the `launcher_agent_blocked_body` line in each file:
- `values/strings.xml`:
  ```xml
      <string name="launcher_agent_undo">Undo</string>
      <string name="launcher_agent_undo_not_all">Can’t be undone here: %1$s</string>
  ```
- `values-ru/strings.xml`:
  ```xml
      <string name="launcher_agent_undo">Отменить</string>
      <string name="launcher_agent_undo_not_all">Здесь не отменить: %1$s</string>
  ```
- `values-tr/strings.xml` (**unjudged**, §0.6):
  ```xml
      <string name="launcher_agent_undo">Geri al</string>
      <string name="launcher_agent_undo_not_all">Burada geri alınamaz: %1$s</string>
  ```

`AgentProvidesModule.kt` — add the imports `com.sidr.launcher.domain.agent.UndoAgentSessionUseCase`
and, after `provideResumeAgentSessionUseCase`:
```kotlin

    @Provides
    @Singleton
    fun provideUndoAgentSessionUseCase(
        start: StartAgentSessionUseCase,
        inverses: ToolInverses,
        registry: ToolRegistry,
    ): UndoAgentSessionUseCase = UndoAgentSessionUseCase(start = start, inverses = inverses, registry = registry)
```

- [ ] **Step 4: run — expect green.**
  - `:feature:launcher:testDebugUnitTest` **216**;
  - `:app:assembleDebug` compiles the Hilt graph (compile probe).

- [ ] **Step 5: mutations — five.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M6a | `AgentSessionSurface.kt` · `val undo = if (undoReview.any { it.undo is Undo.By }) {` → `val undo = if (undoReview.isNotEmpty()) {` | Undo only when something can be undone | `:feature:launcher` — `AgentSessionSurfaceUndoTest > nothing to undo means no Undo` **only** |
| M6b | `AgentSessionSurface.kt` · `                    notUndoable?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }\n` → `` | the card names what cannot be undone | `:feature:launcher` — `AgentSessionSurfaceUndoTest > when only part can be undone the card names what cannot` **only** |
| M6c | `AgentSessionSurface.kt` · the Failed branch's `            secondaryAction = undo,\n        )\n\n        ExecutionState.Blocked` → `        )\n\n        ExecutionState.Blocked` | a part-way failure offers undo | `:feature:launcher` — `AgentSessionSurfaceUndoTest > a plan that failed part-way offers Undo too` **only** |
| M6d | `LauncherAgentSession.kt` · `        if (busy) return\n        val finished` → `        val finished` | one undo per double tap | `:feature:launcher` — `LauncherAgentSessionTest > a second Undo tap while the first is running starts nothing` **only** |
| M6e | `LauncherScreen.kt` · `                    onUndo = viewModel::undoAgentSession,\n` → `` | the screen wires the button | `:feature:launcher` — `LauncherScreenAgentProvenanceTest > a finished plan on the screen offers Undo, and Undo reaches the engine through its gate` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1543`.
  - Per module: `:feature:launcher` **216** (+8), the rest as after Task 5.

- [ ] **Step 7: commit (controller).**

```bash
git add feature/launcher/src app/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): «Отменить» на карточке — что откатить, как действие, а не сообщение

Спека §6.6 (B14), развилка владельца F2-3.3 (вариант а). Законченная карточка
(выполнено / не завершено / остановлено) предлагает «Отменить», только если
хоть что-то отменимо, и называет то, что отменить нельзя. Нажатие —
новая сессия отмены через тот же движок и гейты; двойное касание — один
прогон. Кнопка живёт, пока карточка на экране: в покое хранилище пусто.

Слоты secondaryAction/next в DS уже были — core/ui не тронут, эталоны
Roborazzi не сдвинулись. Шов экрана: настоящие экран, ViewModel и use case,
фейк только у мира. Строки en/ru/tr; турецкие не судимы. Мутации M6a–M6e.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: `:consumer:jvm` — a reversible sandbox tool, and a partial failure undone on real files

**Spec:** §6.5 as amended by F2-3.1; §6.2. **Module:** `:consumer:jvm`.

**Files:**
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolIds.kt` — one
  id, three keys.
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSource.kt` —
  one descriptor.
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolWorker.kt` —
  one `when` arm and `renameFile`.
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolInverses.kt`.
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlanner.kt` — a
  second verb.
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt` — offer
  undo after a finished outcome; the "No plan" message.
- Test: `SandboxToolWorkerTest` (+4), `SandboxToolSourceTest` (+1, and one rename of «three» →
  «four»), `SandboxToolContractTest` (cases only), `FilePlannerTest` (+1), `AgentLoopTest` (+2),
  `ConsoleHarnessTest` (+1).
- Create: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolInversesTest.kt`
  (4).

**Interfaces.**
- Consumes: Task 3's `ToolInverses`, `Undo`, `UndoAgentSessionUseCase`, `startPlanned`, `StepUndo`;
  Task 1's `ConsoleHarness` constructor.
- Produces:
  - `SandboxToolIds.RENAME_FILE = ToolId("rename_file")`;
  - `SandboxKeys.NEW_NAME = "new_name"`, `RENAMED_PATH = "renamed_path"`,
    `PREVIOUS_NAME = "previous_name"`;
  - `class SandboxToolInverses : ToolInverses`;
  - `FilePlanner` understands `rename <a> to <b>`.

- [ ] **Step 1: write the failing tests.**

The sketches below name the test class's existing helpers where they are known (`temp`, `store()`,
`executor()`, `registry`, `harness(...)`, `printed`). **Read each test file's own helpers and use
them.** A worker test drives `SandboxToolWorker(temp.root.toPath()).invoke(ResolvedInvocation(...))`
directly.

`SandboxToolWorkerTest` — add the imports `kotlinx.coroutines.runBlocking`,
`com.sidr.launcher.domain.tool.ToolOutput` and `org.junit.Assert.assertFalse` if absent (or write the
helper with the file's own `runTest`), then append:
```kotlin

    // --- rename_file (A4′ phase 2): the stand's one reversible tool --------------------------------

    private fun rename(path: String, newName: String) = runBlocking {
        SandboxToolWorker(temp.root.toPath()).invoke(
            ResolvedInvocation(SandboxToolIds.RENAME_FILE, mapOf(SandboxKeys.PATH to path, SandboxKeys.NEW_NAME to newName)),
        )
    }

    @Test
    fun `rename_file renames inside the sandbox and reports what an undo needs`() {
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()

        val result = rename(ledger.toString(), "ledger.old")

        val renamed = ledger.resolveSibling("ledger.old")
        assertEquals(
            ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.RENAMED_PATH to renamed.toString(), SandboxKeys.PREVIOUS_NAME to "ledger.txt"))),
            result,
        )
        assertFalse(Files.exists(ledger))
        assertTrue(Files.exists(renamed))
    }

    /** A rename that could also move is a move; `archive/` is INSIDE the sandbox, so only the name rule stops it. */
    @Test
    fun `rename_file refuses a new name that is a path, even one inside the sandbox`() {
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()
        temp.newFolder("archive")

        assertTrue(rename(ledger.toString(), "archive/ledger.txt") is ToolResult.Failed)
        assertTrue(Files.exists(ledger))
    }

    /** Its undo would have to bring the overwritten file back, and nothing kept it. */
    @Test
    fun `rename_file never renames over an existing file`() {
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()
        val other = temp.newFile("other.txt").toPath().toRealPath()

        assertTrue(rename(ledger.toString(), "other.txt") is ToolResult.Failed)
        assertTrue(Files.exists(ledger))
        assertTrue(Files.exists(other))
    }

    @Test
    fun `rename_file on a file that is not there fails`() {
        assertTrue(rename(temp.root.toPath().resolve("absent.txt").toString(), "absent.old") is ToolResult.Failed)
    }
```

`SandboxToolSourceTest`:
- Rename `` `the registry holds exactly these three and nothing else` `` to
  `` `the registry holds exactly these four and nothing else` ``, and add the string `"rename_file"` to
  its expected list (the list holds id **strings**).
- Add the imports `ActionRiskLevel` and `ToolDurability` if the file lacks them.
- Append:
```kotlin

    @Test
    fun `rename_file is SAFE and TRANSIENT, takes a path and a bare name, and reports what an undo needs`() {
        val d = source.find(SandboxToolIds.RENAME_FILE)!!
        assertEquals(listOf(SandboxKeys.PATH, SandboxKeys.NEW_NAME), d.argSchema.map { it.name })
        assertEquals(listOf(SandboxKeys.RENAMED_PATH, SandboxKeys.PREVIOUS_NAME), d.outputSchema.map { it.name })
        assertEquals(ActionRiskLevel.SAFE, d.risk)
        assertEquals(ToolDurability.TRANSIENT, d.durability)
    }
```

`SandboxToolContractTest` — add **one** `Case(...)` to the list in
`` `every result that can carry an output carries exactly the keys its tool declares` ``, after the
`DELETE_FILE` case:
```kotlin
            Case(
                SandboxToolIds.RENAME_FILE,
                mapOf(SandboxKeys.PATH to File(temp.root, "ledger.txt").absolutePath, SandboxKeys.NEW_NAME to "ledger.old"),
                { temp.newFile("ledger.txt") },
                "Effected",
            ),
```
Only the `Effected` branch: the test compares emitted keys with the declared `outputSchema`, and a
`Failed` carries none by construction (found by the plan review — a `Failed` case would be red). The
count is unchanged.

Create `SandboxToolInversesTest.kt`:
```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Test

class SandboxToolInversesTest {

    private val inverses = SandboxToolInverses()

    @Test
    fun `a rename is undone by renaming the result back to its old name`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    SandboxToolIds.RENAME_FILE,
                    mapOf(SandboxKeys.PATH to ArgSource.Literal("/box/ledger.old"), SandboxKeys.NEW_NAME to ArgSource.Literal("ledger.txt")),
                ),
            ),
            inverses.undoOf(
                ResolvedInvocation(SandboxToolIds.RENAME_FILE, mapOf(SandboxKeys.PATH to "/box/ledger.txt", SandboxKeys.NEW_NAME to "ledger.old")),
                ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.RENAMED_PATH to "/box/ledger.old", SandboxKeys.PREVIOUS_NAME to "ledger.txt"))),
            ),
        )
    }

    @Test
    fun `a deleted file cannot be undone - nothing kept it`() {
        assertEquals(Undo.NotUndoable, inverses.undoOf(ResolvedInvocation(SandboxToolIds.DELETE_FILE), ToolResult.Effected()))
    }

    @Test
    fun `a read leaves nothing to undo`() {
        assertEquals(Undo.NothingToUndo, inverses.undoOf(ResolvedInvocation(SandboxToolIds.WORKSPACE_INFO), ToolResult.Effected()))
        assertEquals(Undo.NothingToUndo, inverses.undoOf(ResolvedInvocation(SandboxToolIds.FIND_FILE), ToolResult.Effected()))
    }

    @Test
    fun `a rename that reported nothing cannot be undone`() {
        assertEquals(Undo.NotUndoable, inverses.undoOf(ResolvedInvocation(SandboxToolIds.RENAME_FILE), ToolResult.Effected()))
    }
}
```

`FilePlannerTest` — append (following its existing `plan(...)` call shape):
```kotlin

    @Test
    fun `a rename goal plans find-then-rename, binding the found path`() = runTest {
        val planned = FilePlanner().plan(
            PlanningRequest(AgentGoal("rename ledger.txt to ledger.old", GoalShape.Free("rename ledger.txt to ledger.old"))),
            SandboxToolSource(),
        ) as PlanningResult.Planned

        assertEquals(
            listOf(SandboxToolIds.WORKSPACE_INFO, SandboxToolIds.FIND_FILE, SandboxToolIds.RENAME_FILE),
            planned.plan.steps.map { it.invocation.id },
        )
        assertEquals(
            mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH), SandboxKeys.NEW_NAME to ArgSource.Literal("ledger.old")),
            planned.plan.steps[2].invocation.args,
        )
    }
```

`AgentLoopTest` — add the imports `SandboxKeys`, `SandboxToolInverses`, `ToolInvocation`,
`ArgSource`, `ExecutionPlan`, `PlanStep`, `StepPrecondition`, `StepRationale`, `ActionRiskLevel`,
`UndoAgentSessionUseCase`, `StepUndo`, `Undo` and `java.nio.file.Files`. Then append:
```kotlin

    // --- A4′ phase 2, spec §6.5 as amended: failed part-way → undo what it did, on real files ----

    private fun renameStep(index: Int, path: String, newName: String) = PlanStep(
        index,
        ToolInvocation(SandboxToolIds.RENAME_FILE, mapOf(SandboxKeys.PATH to ArgSource.Literal(path), SandboxKeys.NEW_NAME to ArgSource.Literal(newName))),
        ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    private fun undo(store: JvmAgentSessionStore) = UndoAgentSessionUseCase(
        StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry),
        SandboxToolInverses(),
        registry,
    )

    @Test
    fun `a plan that failed part-way is undone for what it did, and only for that`() = runTest {
        val store = store()
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()
        val absent = temp.root.toPath().toRealPath().resolve("absent.txt")
        val plan = ExecutionPlan(listOf(renameStep(0, ledger.toString(), "ledger.old"), renameStep(1, absent.toString(), "absent.old")))
        val goal = AgentGoal("tidy the ledger", GoalShape.Free("tidy the ledger"))

        val started = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry).startPlanned(goal, plan).value()!!
        val finished = RunAgentSessionUseCase(executor(), store).run(started).value()
        assertTrue("step 1 must have failed", finished.observations[1] is ToolResult.Failed)
        assertTrue(Files.exists(ledger.resolveSibling("ledger.old")))

        val undoing = undo(store).undo(finished).value()!!
        val undone = RunAgentSessionUseCase(executor(), store).run(undoing).value()

        assertEquals(ExecutionState.Completed, undone.state)
        assertEquals(1, undoing.plan.steps.size)
        assertTrue(Files.exists(ledger))
        assertFalse(Files.exists(ledger.resolveSibling("ledger.old")))
    }

    @Test
    fun `an irreversible step is named and never compensated, whatever came before it`() = runTest {
        val store = store()
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()
        val other = temp.newFile("other.txt").toPath().toRealPath()
        val plan = ExecutionPlan(
            listOf(
                renameStep(0, ledger.toString(), "ledger.old"),
                PlanStep(1, ToolInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to ArgSource.Literal(other.toString()))), ActionRiskLevel.DANGEROUS, StepPrecondition.None, StepRationale.GOAL_DIRECT),
            ),
        )
        val goal = AgentGoal("tidy the ledger", GoalShape.Free("tidy the ledger"))
        val run = RunAgentSessionUseCase(executor(), store)
        val started = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry).startPlanned(goal, plan).value()!!
        val atGate = run.run(started).value()
        val finished = ResolveConsentUseCase(store, run).resolve(atGate.id, 1, granted = true).value()!!

        val review = undo(store).review(finished)
        assertEquals(StepUndo(1, Undo.NotUndoable), review.first())
        assertTrue(review[1].undo is Undo.By)

        run.run(undo(store).undo(finished).value()!!)

        assertTrue(Files.exists(ledger))
        assertFalse("a deleted file stays deleted", Files.exists(other))
    }
```
(Import `org.junit.Assert.assertFalse` if absent.)

`ConsoleHarnessTest` — append:
```kotlin

    @Test
    fun `after a rename the harness offers undo, and undo puts the name back`() = runTest {
        temp.newFile("ledger.txt")

        val state = harness(mutableListOf("y")).run("rename ledger.txt to ledger.old")

        assertEquals(ExecutionState.Completed, state)
        val log = printed.joinToString("\n")
        assertTrue(log, log.contains("Undo outcome: Completed"))
        assertTrue(File(temp.root, "ledger.txt").exists())
        assertFalse(File(temp.root, "ledger.old").exists())
    }
```

- [ ] **Step 2: run.** `tools/gate.sh --scoped :consumer:jvm:test` is expected to be compile-red:
  `Unresolved reference 'RENAME_FILE'`, `'NEW_NAME'`, `'SandboxToolInverses'`.

- [ ] **Step 3: implement.**

`SandboxToolIds.kt`:
- In `SandboxToolIds`, add `    val RENAME_FILE = ToolId("rename_file")`.
- In `SandboxKeys`, add:
```kotlin
    const val NEW_NAME = "new_name"
    const val RENAMED_PATH = "renamed_path"
    const val PREVIOUS_NAME = "previous_name"
```

`SandboxToolSource.kt` — append to `descriptors`, after `DELETE_FILE`'s descriptor:
```kotlin
        ToolDescriptor(
            id = SandboxToolIds.RENAME_FILE,
            level = ToolLevels.SANDBOX,
            effect = ToolEffect.LOCAL,
            argSchema = listOf(
                ActionArg(SandboxKeys.PATH, description = "Absolute path of the file to rename"),
                ActionArg(SandboxKeys.NEW_NAME, description = "The new file name — a bare name, never a path"),
            ),
            outputSchema = listOf(
                ActionArg(SandboxKeys.RENAMED_PATH, description = "Absolute path after the rename"),
                ActionArg(SandboxKeys.PREVIOUS_NAME, description = "The file name before the rename"),
            ),
            // A4′ phase 2: the stand's one reversible tool (spec §6.5). It writes durable state but is
            // undone by itself, so it is TRANSIENT in this codebase's sense — the precedent of the
            // launcher's alias tools; DURABLE here means "cannot be put back".
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
```

`SandboxToolWorker.kt`:
- In `invoke`'s `when`, before the `else ->` arm, add
  `            SandboxToolIds.RENAME_FILE -> renameFile(invocation)`.
- After `deleteFile`, add:
```kotlin

    /**
     * A4′ phase 2. Renames within one directory. Refuses a new name that is a path — a rename that
     * could also move is a move, and a move can leave the sandbox — and never renames over an existing
     * file: `Files.move` without `REPLACE_EXISTING` throws `FileAlreadyExistsException`, which
     * [invoke]'s catch turns into `Failed`. That refusal is load-bearing for undo, which could not
     * bring back an overwritten file.
     */
    private fun renameFile(invocation: ResolvedInvocation): ToolResult {
        val source = contained(invocation.args[SandboxKeys.PATH].orEmpty())
            ?: return ToolResult.Failed(CommandFailure.Generic)
        val newName = invocation.args[SandboxKeys.NEW_NAME].orEmpty()
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') || newName == "." || newName == "..") {
            return ToolResult.Failed(CommandFailure.Generic)
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return ToolResult.Failed(CommandFailure.Generic)
        val target = source.resolveSibling(newName)
        if (contained(target.toString()) == null) return ToolResult.Failed(CommandFailure.Generic)
        Files.move(source, target)
        return ToolResult.Effected(
            ToolOutput(mapOf(SandboxKeys.RENAMED_PATH to target.toString(), SandboxKeys.PREVIOUS_NAME to source.fileName.toString())),
        )
    }
```

Create `SandboxToolInverses.kt`:
```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo

/**
 * The second consumer's inverses (A4′ phase 2, spec §6.5): `rename_file` undoes itself, a read leaves
 * nothing, and `delete_file` cannot be undone — nothing kept the file. Built from what the rename
 * REPORTED, never from a re-read of the directory.
 */
class SandboxToolInverses : ToolInverses {
    override fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo = when (forward.id) {
        SandboxToolIds.RENAME_FILE -> {
            val renamed = result.output.values[SandboxKeys.RENAMED_PATH].orEmpty()
            val previous = result.output.values[SandboxKeys.PREVIOUS_NAME].orEmpty()
            if (renamed.isBlank() || previous.isBlank()) {
                Undo.NotUndoable
            } else {
                Undo.By(
                    ToolInvocation(
                        SandboxToolIds.RENAME_FILE,
                        mapOf(SandboxKeys.PATH to ArgSource.Literal(renamed), SandboxKeys.NEW_NAME to ArgSource.Literal(previous)),
                    ),
                )
            }
        }
        SandboxToolIds.WORKSPACE_INFO, SandboxToolIds.FIND_FILE -> Undo.NothingToUndo
        SandboxToolIds.DELETE_FILE -> Undo.NotUndoable
        else -> Undo.NotUndoable
    }
}
```

`FilePlanner.kt`:
- Restructure `plan(...)` so that the existing removal plan is built when the text starts with
  `"$VERB "`, and a rename plan when it matches `rename <a> to <b>`.
- Add `const val RENAME_VERB = "rename"` beside `VERB`.
- The rename plan is `[workspace_info, find_file(query = a, root ← step 0), rename_file(path ← step 1's
  resolved_path, new_name = b)]`: all `GOAL_DIRECT`, `StepPrecondition.None`, risk from the registry.
  It is `NoPlan` if the registry lacks any of the three tools, or if `a` or `b` is blank.
- Parse with
  `Regex("^$RENAME_VERB (.+?) to (.+)$").matchEntire(text)?.destructured?.let { (from, to) -> from.trim() to to.trim() }`.

`ConsoleHarness.kt`:
- Add the imports `SandboxToolInverses`, `UndoAgentSessionUseCase` and `com.sidr.launcher.domain.tool.Undo`.
- After `private val runner = …`, add:
```kotlin

    // A4′ phase 2 (spec §6.5 as amended): after a finished outcome, offer to undo what can be undone.
    private val undo = UndoAgentSessionUseCase(
        StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry),
        SandboxToolInverses(),
        registry,
    )
```
- In `run(...)`, replace
```kotlin
        if (ran.state != ExecutionState.AwaitingConsent) {
            out("Outcome: ${ran.state}")
            return ran.state
        }
```
with
```kotlin
        if (ran.state != ExecutionState.AwaitingConsent) {
            out("Outcome: ${ran.state}")
            offerUndo(ran)
            return ran.state
        }
```
- In `gate(...)`, replace the final `        out("Outcome: ${resolved.state}")\n        return resolved.state` with:
```kotlin
        out("Outcome: ${resolved.state}")
        offerUndo(resolved)
        return resolved.state
```
- Add:
```kotlin

    /** Offers «undo» when the finished session has something undoable, and names what it cannot undo. */
    private suspend fun offerUndo(finished: AgentSession) {
        val review = undo.review(finished)
        if (review.none { it.undo is Undo.By }) return
        review.filter { it.undo == Undo.NotUndoable }.forEach { out("Cannot be undone: step ${it.stepIndex}") }
        out("Undo what can be undone? [y/N]")
        if (ask()?.trim()?.lowercase() != "y") return
        val undoing = (undo.undo(finished) as? OperationResult.Success)?.value ?: return
        val done = (runner.run(undoing) as? OperationResult.Success)?.value ?: return
        printTrace(done)
        out("Undo outcome: ${done.state}")
    }
```
- In `start(...)`, replace
  `            out("No plan for: \"$goalText\". This harness understands: remove <file name>")` with
  `            out("No plan for: \"$goalText\". This harness understands: remove <file name> · rename <file name> to <new name>")`.

**`printTrace` reads a running `printed` counter.** The undo session's trace starts at 0 again, so
reset `printed = 0` before `printTrace(done)`. The existing harness keeps one session per `run`, and
this is the first place a second one is printed.

- [ ] **Step 4: run — expect green:** `:consumer:jvm:test` **71**.

- [ ] **Step 5: mutations — five, all read in `:consumer:jvm`.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M7a | `SandboxToolWorker.kt` · `        Files.move(source, target)` → `        Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)` | never over an existing file | `SandboxToolWorkerTest > rename_file never renames over an existing file` **only** |
| M7b | `SandboxToolWorker.kt` · `if (newName.isBlank() \|\| newName.contains('/') \|\| ` → `if (newName.isBlank() \|\| ` | a name, never a path | `SandboxToolWorkerTest > rename_file refuses a new name that is a path, even one inside the sandbox` **only** |
| M7c | `SandboxToolInverses.kt` · `mapOf(SandboxKeys.PATH to ArgSource.Literal(renamed), SandboxKeys.NEW_NAME to ArgSource.Literal(previous))` → `mapOf(SandboxKeys.PATH to ArgSource.Literal(previous), SandboxKeys.NEW_NAME to ArgSource.Literal(renamed))` | the undo renames the RESULT back | `SandboxToolInversesTest > a rename is undone by renaming the result back to its old name`; `AgentLoopTest >` `a plan that failed part-way is undone for what it did, and only for that`, `an irreversible step is named and never compensated, whatever came before it`; `ConsoleHarnessTest > after a rename the harness offers undo, and undo puts the name back` |
| M7d | `SandboxToolInverses.kt` · `        SandboxToolIds.DELETE_FILE -> Undo.NotUndoable` → `        SandboxToolIds.DELETE_FILE -> Undo.NothingToUndo` | a delete is named, not hidden | `SandboxToolInversesTest > a deleted file cannot be undone - nothing kept it`; `AgentLoopTest > an irreversible step is named and never compensated…` |
| M7e | `ConsoleHarness.kt` · `        if (review.none { it.undo is Undo.By }) return` → `        return` | the harness offers undo at all | `ConsoleHarnessTest > after a rename the harness offers undo, and undo puts the name back` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.**
  - Expected: `GATE GREEN`, `TOTAL tests=1556`.
  - Per module: `:consumer:jvm` **71** (+13), the rest as after Task 6.

- [ ] **Step 7: commit (controller).**

```bash
git add consumer/jvm/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф2): второй потребитель — обратимый rename_file и отмена частичного отказа на настоящих файлах

Спека §6.5 с поправкой F2-3.1. У песочницы появился rename_file (SAFE,
сам себе обратный): только имя, не путь; никогда поверх существующего.
SandboxToolInverses: переименование отменяется переименованием назад, чтения —
отменять нечего, удаление — не отменить.

AgentLoopTest на настоящих файлах: план упал на втором шаге — отменено ровно
то, что сделано; удаление названо и не компенсируется. Консольная упряжь
предлагает отмену после законченного исхода. Мутации M7a–M7e.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: close the phase — documents, doctrine, the gate floor. The commit is proposed, not made

**Spec:** §10 criteria 5, 11, 12. **Modules:** none (documents and `tools/gate.sh`).

- [ ] **Step 1: boundary gate from a cleared tree** — the measured total becomes the new floor.
  Expected `TOTAL tests=1556` (a hypothesis).

- [ ] **Step 2: `tools/gate.sh:195`.** Set `BASELINE_TESTS` to the measured total, and change the
  label on `:236`, the comment on `:195` and the header comment on `:19` («1485 since A4′ phase 1»)
  from phase 1 to phase 2, keeping the history («1485 after phase 1, 1463 after phase 0, 1440
  before»). This is **machinery**: after
  the edit, run the boundary gate once more and confirm it still prints `GATE GREEN`. A floor above
  the real total must print `GATE RED`; check that by setting it to measured+1 once, then back
  (ruling R9's lesson).

- [ ] **Step 3: doctrine matrix** (`docs/governing/sidr-doctrine-matrix-v1.0.md:139`).
  - Replace `DOC-HMA-3`'s third column `<нет> ← долг A4′ (откат и компенсация не заспецифицированы)`
    with:
    - `AgentExecutorTest` (both reasons recorded before a `CONFIRM`+`DURABLE` step runs);
    - `AgentSessionGateChipTest` (the irreversible step is announced `DESTRUCTIVE` before it runs);
    - `CompensationTest` (what acted is reviewed newest first; a handed-off act is never offered);
    - `UndoAgentSessionUseCaseTest` (the undo is a new session through the same gates; a `CONFIRM`
      inverse stops at consent);
    - `CompensationOutsideTheEngineGuardTest` (the engine cannot see an inverse);
    - `AgentActingSeamTest` (an alias undone end to end through the real worker, catalog, engine and
      Room; an overwritten alias pointed back, not forgotten);
    - `AgentLoopTest` (JVM: a partial failure undone on real files; the delete named, not compensated);
    - `AgentSessionSurfaceUndoTest` (the card offers undo and names what it cannot).
  - Close the cell with **the named coverage**: two of three `launcher_memory` tools, one step at a
    time; no gate lowered (spec §6.3).
  - Type stays `unit`, wording unchanged. Add a journal row to §6 of the matrix, dated at close, in the
    form of the 2026-08-22 `DOC-ILM-3` entry: the column filled, why the type stays, what it does not
    close.
  - `DoctrineMatrixGuardTest` checks every class name exists.

- [ ] **Step 4: `§HANDOFF`** of the track plan — a subsection «Фаза 2 A4′ — частичный отказ и откат —
  `CODE-GREEN` <date>, гейт **<N>**», in the form of «Фаза 1 A4′»:
  - the state and the commits;
  - the gate chain per task;
  - what was built, one line per task;
  - the three forks and the owner's answers (a pointer to §0.1);
  - the plan's defects found by execution;
  - the controller's rulings;
  - the hypotheses for acceptance (§0.6);
  - what the phase did NOT close (§0.5), including re-plan after failure → phase 3 and JVM resume
    parity → phase 3.

  Also correct the paragraphs it falsifies:
  - «Фаза 0» question 1 (R14) → closed by F2-1;
  - 0.2(2) «инертный `DURABLE_EFFECT`» → closed;
  - staleness layer 1 → closed for a plan in which nothing acted; the acted remainder named.

- [ ] **Step 5: `CLAUDE.md`.**
  - Add a stage-table row for phase 2 (a new row, like phase 1's).
  - Update the baseline paragraph: the new number, its per-module decomposition, and the chain
    1485 → … → N. Edit the whole paragraph, as the owner ruled in phase 1.
  - **Known debt**, corrected where the phase falsified it:
    - A0 residual (1) (§12.8 unreachable by the user's path) → reachable and fixed in code for a plan
      that has only looked; device-unseen;
    - A0.5 residual (2) (`DURABLE_EFFECT` unreachable for `CONFIRM+`) → closed in code;
    - the A0-engine row's wall-clock gap → R14 closed in code;
    - D11 → all four triggers covered.

- [ ] **Step 6: `ai-context/current-status.md`.** Add the header entry, then **re-read the file's last
  three sections** («Not claimed done», «Source of truth», «Keeping this file honest») and correct
  what the phase falsified. This is the rule of 2026-09-20.

- [ ] **Step 7: final whole-branch review** (as in phases 0 and 1), before the commit. Its fixes are
  their own commit, with mutations and a boundary gate.

- [ ] **Step 8: propose the commit to the owner (R7)** — do not make it without the owner's yes:

```bash
git add tools/gate.sh docs CLAUDE.md ai-context/current-status.md
git commit -m "$(cat <<'EOF'
docs(agentic-6/A4' ф2): фаза 2 закрыта — CODE-GREEN на <N>

Частичный отказ и откат. DOC-HMA-3 получил имена тестов (обе половины;
покрытие названо: два инструмента одной семьи, шаг за раз). R14 закрыт в коде.
§12.8 A0 достижим путём пользователя и починен для плана, который только
смотрел. Пол gate.sh — <N>. Устройства не было (одна приёмка, в конце).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Self-review against the spec

| Spec | Where |
|---|---|
| §6.0 partiality stays derived; `ExecutionState` not grown | frozen (Global Constraints); no task touches it |
| §6.1 more than one reason; not a branch reorder | Task 2 (F2-2); M2b ("first reason only") is the reorder's red |
| §6.2 compensation built and proved on `launcher_memory`; coverage named small | Tasks 3, 5 — end to end in `AgentActingSeamTest` (real worker, catalog, engine, Room); `ToolInverseCatalog`'s KDoc and the matrix cell name the coverage |
| §6.3 the gate is not lowered; mutation "lower" → RED | Task 3 M3a (behavioural + structural); M3e shows plan-level risk cannot lower it (F2) |
| §6.4 re-planning = the staleness mechanism | Task 4, as amended by F2-3.1; the failure trigger → phase 3 (§0.4 S1) |
| §6.5 `:consumer:jvm` gets real work | Task 7 (undo on real files, `rename_file`); Task 1 (R14 wiring); Task 2 (reasons in its store) |
| §6.6 `B14` — «что произошло, что изменилось, что откатить» | step states (phase 0) + Task 6 (the action on the same card) |
| §3 0.3 the clock bounds a step; staleness decided for both layers | Task 1 (R14 closed); Task 4 (layer 1); layer 2 stays excluded (phase 0) |
| §3 0.2(2) inert `DURABLE_EFFECT` | Task 2 |
| §3 0.4 `PlanningRequest.priorObservations` has a consumer | moved to phase 3 by F2-3.1; KDoc corrected in Task 4 |
| §9 gate, rules | Global Constraints; every task closes on a cleared-tree boundary gate |
| §10 criterion 5 | Task 3 M3a |
| §10 criterion 10 (frozen) | Global Constraints — `git diff` checkable |
| §10 criterion 11 (gate ≥ floor, cleared, `N executed`) | every task's Step 6; Task 8 raises the floor |
| §10 criterion 12 (`DOC-HMA-3` named) | Task 8 Step 3 |
| §10 criterion 13 (owner device acceptance) | block end; hypotheses in §0.6 |

**Placeholders scanned.**
- Task 7 describes three edits in prose rather than as whole code:
  - `FilePlanner`'s restructure — the exact regex, plan shape and `NoPlan` conditions are given;
  - the `SandboxToolContractTest` case entries — the file's own `Case` shape;
  - the harness's `printed` reset.
- Task 6's seam test leans on the screen test file's own render / advance idiom.

These are deliberate. Each names exactly what to write, and the surrounding file fixes its form.

**Type consistency checked.** These names are used identically in every task that touches them:
`ConsentCheckpoint.reasons`, `ConsentRequested.reasons`, `Undo.By` / `NotUndoable` / `NothingToUndo`,
`ToolInverses.undoOf(forward, result)`, `StepUndo(stepIndex, undo)`, `Compensation.review` / `plan` /
`isUndo`, `StartAgentSessionUseCase.plan` / `startPlanned`, `UndoAgentSessionUseCase.review` / `undo`,
`ResumeAgentSessionUseCase.resume`, `AgentSession.replansOnResume`, `LauncherAgentSession.undoReview`
/ `undo`, `MemoryToolKeys.STORED_PHRASE` / `PREVIOUS_APP`, `SandboxKeys.NEW_NAME` / `RENAMED_PATH` /
`PREVIOUS_NAME`, and `worldContext`.

**Review Focus.** Each of its five lines has its test in the owning task (1 → Tasks 4, 6; 2 → Task 5;
3, 4 → Task 4; 5 → Task 3).

**Gate chain (hypothesis).** 1485 → T1 **1488** → T2 **1497** → T3 **1510** → T4 **1520** → T5
**1535** → T6 **1543** → T7 **1556**.

| Module | Base | T1 | T2 | T3 | T4 | T5 | T6 | T7 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `:domain` | 455 | 457 | 461 | 473 | 479 | 479 | 479 | 479 |
| `:consumer:jvm` | 57 | 57 | 58 | 58 | 58 | 58 | 58 | 71 |
| `:data:repository` | 394 | 394 | 396 | 396 | 396 | 408 | 408 | 408 |
| `:feature:launcher` | 202 | 202 | 204 | 204 | 208 | 208 | 216 | 216 |
| `:app` | 61 | 62 | 62 | 63 | 63 | 66 | 66 | 66 |
| the other eight | 316 | 316 | 316 | 316 | 316 | 316 | 316 | 316 |
