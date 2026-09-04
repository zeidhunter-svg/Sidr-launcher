# Current Status

> **Authoritative history lives in `ai-context/decisions.md` (ADR log); `CLAUDE.md` carries current
> state, hard rules and pointers only (compressed 2026-08-19 by Этап 0.4 — it is no longer a session
> digest); per-phase plans carry their own checklists.** This file is the status snapshot — if it
> disagrees with an ADR, the ADR wins. Status labels follow Этап 0.5's vocabulary: `CODE-GREEN`
> (gate green, no device claim) / `DEVICE-ACCEPTED` (owner ran on-device verification and signed off)
> / `CLOSED` (both, with any residual limitation named, not implied absent). Last re-based: 2026-09-03
> (**Этап 5 (A1′) — federated `ToolRegistry`: one `ToolRegistry` port now federates N adapters behind one
> object; a second real Android source (`Tier0IntentToolSource`, level `system_intent`) registers
> `set_timer`/`open_system_settings` and both are reachable from a typed command via routing step 2b;
> `requiresConsent` unifies four risk-gate spellings into one; `CODE-GREEN` at 1298 tests, `DEVICE-ACCEPTED`
> not met — spec §16 criterion 1, the block's one open criterion**); prior 2026-08-26
> (**A0.5 — second consumer of the portable core: `:consumer:jvm` runs a goal end to end on plain JVM over
> unchanged engine contracts; `CODE-GREEN` at 1219 tests, device acceptance *not applicable*, all four
> §3.1a questions answered with addresses**); prior 2026-08-23
> (**cross-cutting review of the whole A0 block — nine findings, eight fixed and mutation-verified,
> `CODE-GREEN` at 1158 tests; two §12 items await a device re-check because the fixes changed accepted
> behaviour**); prior 2026-08-22
> (**Task 15 — A0 device acceptance on the SM-A325F: all eight §12 items run by the owner and signed
> off, the inherited Этап 4.0 check passed in the same session, migration 3→4 executed for real by both
> routes; A0 and Этап 4.0 are now `DEVICE-ACCEPTED`, A0 `CLOSED` with four named residuals**);
> same-day (A0 work-order items 8–9 — the four mutation-verified guards, the second `DOC-ADL-3`
> amendment and the ADR); prior 2026-08-21
> (A0 work-order items 2b/4/5/6/7 — F6, the one tool source, Room 3→4 + the session store, the
> `RouteCommandUseCase` cut, the execution surface; `CODE-GREEN`, nothing on device); same-day (agentic-track revision — the two-consumers fork, block A0.5, fork F6 in
> A0; docs only); prior 2026-08-20
> (Этап 4.0 — understanding-flag inversion, first behavioural change of the agentic track); prior 2026-08-19
> (Этап 0.5 — corrected I18N-1's and DS-5's headline labels from an unqualified `CLOSED`/`CODE-CLOSED`
> to `CODE-GREEN`, see below; DS-6B checked and confirmed `CLOSED`); same-day Этап 0.4 — `CLAUDE.md`
> compressed 101 KB → 15 KB; same-day I18N-2 residual localization + barrier 4 CLOSED; same-day Этап 2
> toolchain (AGP 9.3.1/Kotlin 2.4.10/Gradle 9.5.0) + `:domain` → Kotlin Multiplatform CLOSED; prior re-base
> 2026-08-16 I18N-1 Multilingual UI CODE-GREEN — agent-driven device smoke, owner did not run
> acceptance; prior re-base 2026-08-10 DS-10 Assistant Migration CLOSED — device-accepted; same-day
> DS-7 Memory Surfaces + S2-2 Explicit Aliases CLOSED; prior re-base 2026-08-08 DS-6B Prayer
> Correctness CLOSED — device-accepted by the owner).

## Agentic track — Этап 5 (A1′) — federated `ToolRegistry` — `CODE-GREEN` (2026-09-03)

**`CODE-GREEN`, `DEVICE-ACCEPTED` NOT met — a plain gap, not an inapplicability.** Unlike A0.5, this
block changes what the user can reach: two new system intents registered and made reachable from a
typed command, neither run on a phone. Full record: ADR «2026-09-03 — Этап 5 (A1′)» in
[decisions.md](decisions.md). Range `3bcf0a5..23f9152`, 23 commits, branch `launcher--7`.

**What was built.** The `ToolRegistry` port stopped projecting one catalog and started federating N
adapters behind one object, `ToolFederation`: its `registry` (`all()`/`find()`) and its `executor` —
the sole `ToolExecutor` implementation — read the same adapter list by construction, so no wiring path
can advertise a tool the dispatcher fails to route (the direct fix for A0 review finding F2, where the
consent gate once read `risk` from the plan and `permissionGate` from a separately-wired registry). A
**second real Android source**, `Tier0IntentToolSource`/`Tier0IntentToolWorker` (level `system_intent`),
sits beside the existing `in_app` adapter and registers `set_timer` (arity 1, `SAFE`, `EXTERNAL`) and
`open_system_settings` (arity 0, `SAFE`, `EXTERNAL`) — zero new Android permissions. Every registered
tool now carries `level`/`effect`, and an `EXTERNAL` tool's provenance reaches the user
(`AgentSessionSurfaceProvenanceTest`, `LauncherScreenAgentProvenanceTest` — behavioural, not a
structural scan, per spec §6.3's own warning against a vacuous guard). `requiresConsent(risk)` in
`domain/action` replaces four independently-spelled gate checks with one predicate. Reachability needed
an unplanned addition found while designing (§8.4 of the spec, change-control before code): one generic
planner arm, `ToolMatchPlanner` + a localized (`en`/`ru`/`tr`) `ToolVocabulary`, composed behind
`Planner` and reached from a new routing step **2b** in `RouteCommandUseCase` — no new `GoalShape`
value, honouring `B1`.

**Seven owner forks, decided 2026-08-29 before code.** The headline one, F1: the long-open A1 fork
(parallel tool vocabulary vs. evolving `ActionCatalog` in place) is resolved — **parallel vocabulary +
federation, identity C** (a projected tool's `ToolId` is *derived* from its `ActionId`, not hand-copied).
F2 split the block's scope: **boundaries + exactly one real second source** ship here; tool *mass*
(`B2`/`B4`) and *selection* (`B3`) move to a new block, **`A1″`**.

**A CRITICAL found by review, not by any task's own tests.** `AgentSessionMappers.toSessionEntity`
threw on `GoalShape.Free` — correct when written, since A0's planner never produced `Free`, but Task 9
changed that premise by composing `ToolMatchPlanner`. Live path: a timer command → step 2b builds a
`Free` goal → a plan is found → `store.save` throws → wrapped as `Failure` → `RouteCommandUseCase`'s
existing fail-open branch (see residuals) silently falls through to the model path. **No session, no
timer, no error, and all 1285 tests green at the time**, because every layer was tested in isolation.
Fixed in `1c4a61e`; no schema change (`identityHash` unchanged). The lesson recorded in the ADR: *if a
product path has a join no test crosses, that path is unverified* — the same shape A0's own review
found in `restoreOnStart`.

**Guards, all mutation-proved, none merely green.** `ToolWorkerCallSiteGuardTest` (new) and
`ToolExecutorCallSiteGuardTest` (re-anchored) both survived W1–W4/E1–E2. `DoctrineGuardTest`'s
duplicate-`ToolId` assertion did **not** meet spec success criterion 3 on its first draft — it read
`federation.registry.all()`, which the federation had already de-duplicated, so a planted duplicate
never reached the assertion meant to catch it. Fixed by reading what the adapters **declare**, before
dedup; both the collision case and a zero-tool adapter's vacuous-pass case are now held by a shared
non-vacuous accessor, mutation-proved in two rounds.

**Residual limitations, named rather than implied absent.** `DOC-ADL-1`'s closure rests on the
**single call site**, not on `ConsentPolicyTest`'s wake-up test, which cannot distinguish
`risk != entries.first()` from `risk >= CONFIRM` while exactly three risk levels exist. `core/testing/
src/main/java` is scanned by neither call-site guard (owner-level, the `D1`/`D4` family).
`RouteCommandUseCase:147-150` still fails open to the model on **any** non-`Success` from the session
store — the block fixed one instance of that class (the `Free`-encode throw above), not the class
itself, which stays A4′'s. `DoctrineGuardTest` does not pin a registered tool's declared **risk**;
Task 12's parity test closes that narrowly for the two A0 in-app tools' descriptors only. The step-line
rule is keyed on argument **count** — a tool with two or more literal arguments falls back to the goal
text. `"sayaç ayarla"` (the `tr` timer trigger) is reachable and un-shadowed but needs a native
speaker's read — `sayaç` reads as counter/meter, not kitchen timer. `docs/governing/
sidr-doctrine-matrix-v1.0.md`'s `DOC-HMA-2` row is **not** claimed closed — levels now exist, but
whether a level *change* stops the loop is still A4′'s.

**What it answers of A0.5's four vocabulary findings.** `ObservedFact` stays deliberately untouched
(owner instruction 2026-08-23). `CommandFailure` is rejected with a measured reason. `StepRationale` is
replaced — `PlanStep.line`, keyed on `ToolId` (the argument-count limit above is its residual).
`ArgType` stays at one value, rejected with a measured reason (owner fork F6): a second flag-value is
still categorically insufficient for an MCP/AppFunctions JSON Schema. Fork F5 (consent fires before
argument binding) is recorded as an address, `A4′`, not a decision.

**Device-acceptance checklist** — split A/B/C the way the A0 re-check file is:
[docs/superpowers/plans/2026-08-29-a1-device-acceptance.md](../docs/superpowers/plans/2026-08-29-a1-device-acceptance.md).

**Gate.** `:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks` — BUILD
SUCCESSFUL, exit 0, **557 actionable tasks: 557 executed**, **1298 tests / 0 failures** (up from the
1219 baseline); `core/ui` untouched, so `verifyRoborazziDebug` was not part of the closing gate.

## Agentic track — Этап 4.5 (A0.5) — second consumer — `CODE-GREEN` (2026-08-26)

**The portable core now has a consumer that is not a test.** `:consumer:jvm` — a plain `kotlin.jvm`
module with **zero Android artifacts** on its resolved classpath — takes one goal (`remove stale.lock`)
through `goal → plan → gate → tool → observe → tool → result → trace`: three steps, two step-to-step
bindings, one risk transition that stops the loop for consent before anything is touched, and a session
that survives process death in **two** shapes. The engine contracts (`domain/agent`, `domain/tool`,
`domain/trace`) are **unchanged**; the block's whole `commonMain` footprint is **two** edits in two
files — the `GoalShape.Free` value and the `NoPlan` arm it forces in `TemplatePlanner` — both from one
owner decision. Full record: ADR «2026-08-26 — Этап 4.5 (A0.5)» in [decisions.md](decisions.md).

**Status is `CODE-GREEN`, and `DEVICE-ACCEPTED` is marked *not applicable* rather than absent** — the
block changes nothing on the phone, and demanding an acceptance run would pretend that it does. That
distinction is Этап 0.5's whole point.

**The central finding, measured rather than argued.** `ToolId` is an open value class over `String`: the
new consumer declared **three** of its own ids with **zero** core edits. `GoalShape` is a closed sum: **one**
added value cost **four files across three modules**, because exhaustive else-free `when` sites live
outside `:domain` and `:domain:jvmTest` never compiles them. The six contracts that did **not** survive
contact with a second consumer are all **closed sums**; the one that survived with zero core edits is
`ToolId`, an open value class. (The second survivor, `AgentSessionIdFactory`, is a **port**, not a value
type — it shows a different mechanism for the same end: push the unportable thing across a boundary
rather than name it in the core.) That is an **observation for A1′, not a decision** — the A1 fork stays
open and both its branches are exactly as cheap as they were.

**The sharpest recorded finding, and it is not a defect to fix.** When nothing matches, the JVM
consumer's session ends **`Failed`**; on Android the same reality — "the thing you asked about is not
there" — has a name (`ObservedFact.APP_NOT_INSTALLED`), satisfies the next step's precondition, and ends
**`Completed`**. Same reality, two outcomes, purely because the observation vocabulary is a closed
two-value enum written for a launcher. The owner instructed on 2026-08-23 that the asymmetry **stays**,
and it is held by two guards in two modules rather than by a paragraph. A0.5 also sharpened it: the
consent gate fires **before** argument binding, so on the miss path the user approves deleting a file
that was never found, and that approval is what unblocks the discovery that nothing can bind.

**What a second consumer is *for*, beyond portability.** A process that dies **during** a tool call is
the seam A0's review found unheld — and Android could reach that window only with `pm disable-user` plus
an on-device poll inside 157–170 ms. Here it is a function call on a seeded file, walked by the harness
itself rather than simulated. The eight engine repairs of 2026-08-23 are the other half of the evidence:
not one needed a platform branch, an `expect`/`actual`, or a `java.*` import, and `:consumer:jvm`
inherited every one of them by existing.

**The harness was finally run from a terminal, and that caught something no review had.** Until the
last commit of the block every proof — including both process-death shapes — came from a test, and
`Main.kt` documented a `./gradlew :consumer:jvm:run` that **did not exist**. Making the documented
command true (a `JavaExec` task, not the `application` plugin — distribution is a named non-goal) and
then actually walking it exposed that Gradle hands such a task an **empty stdin**, so `readlnOrNull`
returns `null` and consent could never have been typed. The first real run then went
`PlanCreated → … → ConsentRequested(2, RISK_LEVEL) → ConsentResolved(2, granted=true) → delete_file →
SessionEnded(Completed)`: the file really deleted, `session.json` really removed on the terminal state,
and the orphaned `.lock` really left behind — the store's first named limitation, observed rather than
argued.

**Named residuals** (they are named, not implied absent): the sandbox's `findFile` follows symlinks when
testing `isRegularFile`, so an in-sandbox link can be reported under its in-sandbox path — a leaked
*name*, never content, and binding it into `delete_file` is refused; the TOCTOU window inherent to
path-based containment; `JvmAgentSessionStore`'s `FileLock` is **proved by nothing** and the KDoc now says
so instead of claiming otherwise; `SessionDto` has no version field while an undecodable file is deleted,
so version skew is indistinguishable from corruption (→ A5). `DURABLE_EFFECT` is unreachable for any tool
at `CONFIRM` or above — **the user is still stopped**, so it is a trace-fidelity gap, not a safety hole
(→ A4′).

## Agentic track — A0 cross-cutting review + fix round — `CODE-GREEN` (2026-08-23)

**Nine findings, eight fixed, one withdrawn as unreachable.** The whole block (`dc80c1f..7442da4`, 39
commits, 94 files) was reviewed as one object rather than task by task — every task in it had already
passed its own review, so this pass looked for what a task review structurally cannot see: drift
between spec, ADR, KDoc and code; holes at the seams between layers; and document claims nobody had
checked against the code as a whole. Full record: ADR «2026-08-23 — Сквозное ревью блока A0» in
[decisions.md](decisions.md).

**One root cause produced three of the nine.** The domain test for resume simulated the restart by
calling `advance` on the prepared session; the product instead goes through `restoreOnStart()` →
`pausedForRestore()` and `continueSession()` → `resumed()`, and **both write trace events**. Keyed on
the trace *tail*, `AgentExecutor`'s mid-step predicate therefore answered "not mid-step" on exactly the
shape it exists to recognise: the engine re-cleared the step, wrote a second `StepStarted` +
`ToolInvoked`, and a step whose consent had already been granted ran a second time. The predicate now
reads the last `ToolInvoked` rather than the last event. Two more followed from the same seam being
untested: the gate read `risk` from the persisted plan while reading `permissionGate` and `durability`
from the live registry (so a plan written when a tool was `SAFE` ran it with **no** `ConsentRequested`
after a build raised it to `CONFIRM`), and a refused consent was written to the trace twice.

**The surface was claiming things that did not happen.** Step markers came from the cursor, and the
cursor moves for three different reasons — a step ran, a step was skipped by its precondition, a step
failed. All three read as a green success marker, so the two commonest shapes of this engine lied:
the app-is-installed run (spec §12.8 itself) showed a completed store step over a store that never
opened, and a failed step 0 under the default budget produced «План выполнен» with two green markers
over a plan in which nothing succeeded. That is `DOC-ILM-4` — a partial result shown as success — and
everything needed to tell the three apart was already in the session.

**Withdrawn, and that is a result too.** The first version of the consent fix carried an idempotence
guard; the mutation "record always" came back **green**, because a cleared step short-circuits before
it can re-enter the consent block. The guard was unreachable and its test could not fail — the F2/D10
family again, this time caught inside the same round rather than by the next review. Guard and test
both replaced with an assertion about re-entry that does go red.

**Still owed on device.** Three fixes change behaviour accepted on 2026-08-22, so §12.5 and §12.8 need
re-running on the phone. Until then the changed behaviour is accepted by the gate, not by the owner —
Этап 0.5's vocabulary does not allow carrying a signature across a behavioural change. The debt is a
checklist rather than a sentence:
[docs/superpowers/plans/2026-08-23-a0-device-recheck.md](../docs/superpowers/plans/2026-08-23-a0-device-recheck.md),
which separates what the **owner** must look at (the changed §12.8 surface and the six new strings)
from what is a no-regression re-run, from what only an agent can instrument and which therefore clears
nothing.

## Agentic track — Этап 4 (A0) — `CLOSED`, device-accepted (2026-08-22)

**All nine work-order items are done, and so is Task 15.** The owner personally ran all eight §12
acceptance items on the SM-A325F and signed off, so A0 is `CLOSED`: gate green **and**
`DEVICE-ACCEPTED`, with four residual limitations named rather than implied absent (below). The
inherited Этап 4.0 check ran in the same session and passed, which lifts **4.0** to `DEVICE-ACCEPTED`
and removes the `§HANDOFF` bar on declaring later blocks accepted. Full record: ADR
«2026-08-22 — Этап 4 (A0)» in [decisions.md](decisions.md), section «Task 15».

**What the device added to what the gate already claimed.** Migration 3→4 executed for real by both
routes — instrumented `:data:repository:connectedDebugAndroidTest` 9/9 on the phone, and a genuine
`user_version` 3→4 upgrade of the owner's own database with `identity_hash` matching `4.json`; that
debt is retired. F6 was confirmed on hardware (step 1's `args_json` held `from_step`, not a frozen
copy). `force-stop` mid-plan came back as `Paused` with exactly one `SessionPaused`; a double-tapped
confirm opened the store exactly once; a cancelled plan left all three `agent_*` tables empty by
cascade.

**Four residual limitations.** (1) §12.8 — "app installed ⇒ store step skipped" — is **unreachable by
any path a user can take**: `resumed()` continues from the persisted cursor and nothing re-plans, so a
stale `APP_NOT_INSTALLED` observation still opens the store. It passed only via the `cursor == 0` /
mid-step shapes, the latter produced with `pm disable-user` and an on-device `force-stop` poll (window
157–170 ms warm, 1033 ms cold). Recorded as an **A4′ staleness debt** beside the missing wall-clock
budget; `TemplatePlanner`'s KDoc, which claimed the user-facing version, was corrected in the closing
commit. (2) The plan list is not drawn in `AwaitingConsent`. (3) «План выполнен» means "every step
ran", not "the goal was achieved". (4) Item 8's acceptance needed agent intervention, so it exercises
the engine rather than the product.

**What the slice does.** One goal crosses two tool calls where the second consumes what the first
*observed*, and the risk transition between them stops the loop for consent. «открой убер» with Uber
not installed becomes a two-step plan — launch, observe `APP_NOT_INSTALLED`, offer the store — in
**every** network state, because the A0 planner is deterministic and consults no model. That is what
forced the second `DOC-ADL-3` amendment below.

**Items 8–9, closed 2026-08-22.** Item 8 — four guards, each mutation-verified, over three rounds
(`651d718`, `5e315d7`, `2e43b5d`), each round independently reviewed and approved: one call site to the
world; no action vocabulary and no transport inside the engine; a planted sentinel reaching no outbound
channel in four local-only × offline states plus «no provider configured»; the agent tables empty at
rest in all five terminal paths. Item 9 — the doctrine amendment, the ADR and this sync. Ten review
findings are deferred as **D1–D10**, none blocking, and they live in `§HANDOFF` and `CLAUDE.md`
§ Known debt rather than only in the SDD ledger — three items of that family were lost once exactly
that way.

**The mutation row worth keeping.** Proving a text-scanning guard's Gradle input declaration needs a
mutation whose **bytecode is identical**: the naive version (add an import) shifts every line below it,
`:domain`'s compiled output changes, the task re-runs for an unrelated reason and reports a false
"trap closed". The line-balanced variant left `:app:testDebugUnitTest` `FROM-CACHE` at exit 0 while
`domain/agent` sat there importing `LauncherAction`; with the inputs block restored, the same mutation
went RED.

**Doctrine.** `DOC-ADL-3` amended a second time — parity narrowed to «the model planner is not
consulted and nothing leaves the device», because deterministic replay *may* change an outcome FastPath
decided and in A0 it does. `DOC-ILM-3` and `DOC-HMA-2` got real test names; `DOC-ILM-3`'s verification
type was corrected `arch-guard` → `unit` with its own journal row; `DOC-HMA-2` is closed only for the
risk-transition half — tool levels do not exist yet and the cell says so. `DOC-HMA-3` is **not** closed:
A0 has the marking (`ToolDurability.TRANSIENT|DURABLE`), not the rollback.

**Below: the earlier items, as recorded on 2026-08-21.** Three of the nine landed the same day the
revision reframed the block, and the ordering the revision insisted on was honoured. Recorded then
rather than at block close because the in-flight state was being read wrong: `§HANDOFF`, `CLAUDE.md`
and the spec's own work order all still said items 4–9 were open.

- **Item 2b — F6, step-to-step data flow (`08b632f`).** Landed **before** the migration, which was the
  whole point: afterwards the same change would have cost a migration 4→5 plus a persisted-trace
  conversion. Two properties it leaves standing: `ToolExecutor` accepts only `ResolvedInvocation` and
  `InvocationValidator.resolve` is its sole constructor, so an unbound reference cannot **compile** its
  way to the world; and a plan persists `ArgSource`, never resolved values.
- **Item 4 — the one tool source (`7baa7ae`, with `89e3bfb`).** `SystemIntentToolSource` +
  `SystemIntentToolExecutor` over the **unchanged** `ExecuteActionUseCase → IntentActionResolver →
  ActionExecutor` chain. `ActionIds`' seven values untouched (ADR 3/4).
- **Item 5 — Room 3→4 + `RoomAgentSessionStore` (`538a689`, review fixes `c6cc2bf`).** Three tables,
  cascade on `session_id`, schema `4.json` committed; `1/2/3.json` frozen. Consent is a conditional
  `UPDATE`, so a double confirmation applies to nothing instead of running a step twice. `args_json`
  holds `ArgSource` and `observation_output_json` its producing half; a step with no output stores
  `NULL`, not `{}`.

**The review of item 5 found one defect with a real failure mode, fixed in `c6cc2bf`.** Every write was
transactional and the read was not: `active()` issued three separate queries, so a `delete` landing
between two of them returned a session row with no steps — and `ExecutionPlan(emptyList())` is
constructible, so that assembled into a well-formed `Success` with an empty plan. `AgentExecutor.prepare`
then finds no step at the cursor and ends the session `Completed`: **success reported for a goal on
which nothing executed and nothing was traced**, the same fail-silent shape the `index == position`
invariant was added to prevent. Now `AgentSessionDao.loadActive()` reads all three tables in one
transaction, and the mapper refuses a session row with zero steps outright — the second is the half that
can be broken and caught, and is.

**Owner decision, 2026-08-21:** the session table's shape argument is `goal_shape_arg`, not the spec's
original `goal_query` — `query` is a forbidden term in `RoomColumnNamesGuardTest`'s denylist and the old
name would have widened the database's one owner-granted exemption to two. The **spec** was amended to
match the code (`b0bc428`); `resolution_preferences.query` stays the only approved collision.

**Status was `CODE-GREEN` at code completion; Task 15 lifted it to `CLOSED` on 2026-08-22.** Gate
`:domain:jvmTest testDebugUnitTest assembleDebug` green at 1098 tests, 0 failures. What was written
here as "nothing has run on device" — in particular that `MigrationTest`'s 3→4 and 1→4 cases are
`androidTest`, compiled but executed by no task in the unit gate, so `Migration3To4`'s SQL had run
nowhere — is **no longer true**: both cases ran on the SM-A325F, and the same migration then ran again
as a genuine upgrade of the owner's database.

- **Item 6 — the cut into the command pipeline (`c7152e1`, review fixes `f4e61d3`).**
  `RouteCommandUseCase` gains one branch keyed on `CommandMessage.NoAppFound`, above the local-only
  check: A0's planner is deterministic and offline, so it consults no model and transmits nothing.
  Fails open to the FastPath outcome on `NoPlan`. `CommandOutcome.AgentSessionStarted` is added, and a
  test pins that both memory decorators pass it through without consulting their stores.
- **Item 7 — the execution surface (`5f04a1d`, review fixes `fa1fded`).** One component per runtime
  state, from DS-5 primitives only; nothing new in `core/ui`. `en`/`ru`/`tr` ship in the same commit,
  15 keys each. A session that outlived its process is presented as `Paused` with an offer, never
  resumed silently.

**Every reviewed item carried a defect with a reproducible failure scenario.** Item 5: a non-transactional
read (above). Item 6: the narrowness of the cut and the content of the goal handed over were held by
nothing — widening the key from `NoAppFound` to any `Message`, and substituting the raw command for the
extracted app name, were both green across all 396 domain tests. Item 7: `restoreOnStart` assumed the row
on disk is always an interrupted `Running` session, so a terminal session was offered for continuation
("you left before this plan finished" about a plan that finished) and every process restart appended
another `SessionPaused` to a session that paused once — unbounded on a home-screen app, and a trace no
longer 1:1 with reality (`DOC-ILM-3`). All five findings are fixed and mutation-verified, each caught by
only its own test.

**Task 15 — done 2026-08-22, owner-run on the SM-A325F.** All eight items of spec §12 passed, and the
inherited Этап 4.0 check passed in the same session (`ru-RU`, no provider ⇒ «ИИ-провайдер ещё не
настроен» plus a route to the provider screen, not `Unknown command`). The owner performed every item
personally; the agent prepared, drove instrumentation and recorded, and no claim here rests on an
agent-driven `adb` pass (Этап 0.5 vocabulary). One finding, four residual limitations — see the head of
this section and the ADR. A1′ is **not** started (Master Plan §4 DoD); the next block in the queue is
**A0.5**, a brief with four forks for the owner before any code.

**Gate at close (2026-08-22).** `:domain:jvmTest testDebugUnitTest assembleDebug --rerun-tasks` —
BUILD SUCCESSFUL, exit 0, **553/553 tasks genuinely executed**, **1138 tests / 0 failures**;
`:core:ui:verifyRoborazziDebug` — BUILD SUCCESSFUL, exit 0, no golden changes, which is the proof that
`core/ui` was not touched.

## Agentic track — revision + the two-consumers fork (2026-08-21) — DOCS ONLY

**One owner decision, not a package of changes.** A revision of the agentic track produced eight
proposals; rather than running eight of them through change-control, they were reduced to the single
fork that determines six: *is the track's subject an Android agent that PC is bolted onto later, or a
portable core with two consumers from the start?* The owner chose **two consumers from the start**.
Full record, including the evidence and the five consequences: ADR "2026-08-21 — Развилка агентного
трека" in [decisions.md](decisions.md).

- **The evidence that forced the fork.** ADR 3/4 declared the portable core on 2026-08-19, but it was
  backed only by build configuration: `:domain` builds for `androidTarget()` and `jvm()`, yet **`jvm()`
  has zero consumers** (all twelve modules depending on `:domain` are Android), the module has **zero
  `expect`/`actual`**, and `commonMain` carries **eight `java.*` imports**. That is the `:data:ai-local`
  pattern — a contract built against a consumer that never arrives — at the level of the core itself.
- **Master Plan amended in three places:** readiness criterion **10** (one goal runs the same cycle on
  the second consumer); block **A0.5** inserted between A0 and A1′ (§3.1a); milestone M-A1 extended.
  §5 change-control gained the reverse entry, so dropping the decision costs the same ADR it cost to
  take.
- **A0 spec amended — fork F6, step-to-step data flow.** Today `ToolResult.Observed` carries a
  two-valued enum and `ToolInvocation.args` carries literal strings, so a step cannot hand a value to
  the next one — unexpressible in the types, not merely unimplemented, which makes every plan the
  engine can hold a fallback chain rather than a composition. F6 adds `ToolDescriptor.outputSchema`,
  `ToolOutput`, `ArgSource.Literal`/`FromStep`, `ResolvedInvocation` and a two-phase validator
  (static check + binding), with three new fail-closed rejection reasons. `ToolExecutor` accepts only
  `ResolvedInvocation` and `resolve` is its only constructor, so an unbound reference **cannot compile**
  its way to the world. **It must land before the Room 3→4 migration** — afterwards the same change
  costs a migration 4→5 plus a persisted-trace conversion. Work order gained item **2b**; the A0 plan
  gained Task 5b, written compactly on purpose.
- **A0.5 is a brief, not an implementation plan** —
  [2026-08-21-a05-second-consumer.md](../docs/superpowers/plans/2026-08-21-a05-second-consumer.md).
  Four forks go to the owner before any code, with the agent's recommendations recorded.
- **Everything else the revision found is parked in `§HANDOFF`, not built:** `LauncherApps` + app
  shortcuts as a runtime-discovered tool source (unused in the repo today; available at minSdk 28
  because Sidr is the default launcher), tool selection before the planner (`CatalogSchemaRenderer`
  renders the whole catalog — fine at seven tools, impossible at two hundred), Tier-0 system intents,
  the planner's router-sized budget, the agent's missing "body" (foreground service + assist role),
  outbound context by reference, and BYOK as the real distribution ceiling.
- **Zero code changed.** `git diff --stat` is `.md` files only; no gate run is claimed because no source
  set was touched. The spec now deliberately runs **ahead** of the code — Task 5b is the work that
  reconciles them, and reconciling by editing the spec back down would be the wrong direction.

## Agentic track — Этап 4.0 (understanding-flag inversion) CODE-GREEN (2026-08-20)

**The first behavioural change of the agentic track.** Executes ADR 1/4, which had been decided on
2026-08-19 and deliberately left unimplemented. Full record: ADR «2026-08-20 — Этап 4.0» in
[decisions.md](decisions.md).

- **The flag.** `FeatureFlags.llmRouterEnabled` (key `flag_llm_router_enabled`) removed;
  `localOnlyMode` on the **new** key `flag_local_only`, default `false`. No migration — a default flip
  in place would have been inert for anyone who ever changed a setting (`PreferencesMapper` writes the
  whole object; DS-11 `autoHideNavBar` precedent, caught on device there).
- **The gate.** `RouteCommandUseCase` is an ordered chain of early returns — FastPath → `localOnlyMode`
  → FastPath-decided → provider configured → online → planner — so the three "understanding
  unavailable" states are mutually exclusive **by construction**. A FastPath miss no longer answers
  `Unknown command`: that was a claim about the *command* when the truth was about the *system*. The
  "is a provider configured" check moved **into the gate** (fork F4) — with the flag inverted, "no
  provider ⇒ no outbound call" is a privacy property of the execution path, not of how one adapter
  happens to be written. It cost no new module edge: `AiProviderConfigRepository` was already a
  `commonMain` port.
- **The toggle** is now "Local-only mode" / «Только локально» / "Yalnızca yerel", default off; its
  Class B description was rewritten to state *both* switch positions and **re-read and re-signed by
  the owner** — the first time an already-signed Class B string has changed in this project.
- **The owner-review gate is no longer decorative** (fork F2, closing a `CLAUDE.md` § Known debt item).
  `checkOwnerReviewedLocaleStrings` checked marker *presence*; it now checks a signature *over
  content* — `OWNER-REVIEWED <date> sha256:<16 hex>`, the digest covering that file's Class B keys.
  A second hole turned up while fixing the first and was **not** on the debt list: four locked files
  mention the literal token in ordinary prose, and a file carrying only that sentence passed. All ten
  locale files now carry a digest.
- **`DOC-ADL-3` amended** under Master Plan §5 change-control; the doctrine matrix gained §6, an
  amendment log. Parity is now stated as what it always meant operationally — planner not consulted,
  nothing leaves the device, every outcome FastPath *decided* returned byte-for-byte — rather than a
  promise to keep printing one false sentence.
- **Forks:** F1–F4 answered by the owner before any code. **F5 was not in the plan's four** — plan
  point 3 and `DOC-ADL-3` as worded contradicted each other, so it was asked rather than guessed; the
  owner chose a third, neutral message with no call to action.
- **Verification:** `testDebugUnitTest assembleDebug --rerun-tasks` — 551/551 tasks executed, 653
  tests / 0 failures; `:domain:jvmTest --rerun-tasks` — 315 / 0; `:app:assembleRelease` green. Three
  mutations, each red on exactly its own test. `core/ui` untouched, so no Roborazzi run.
- **`CODE-GREEN`, not `DEVICE-ACCEPTED`.** The stage's device check — `ru-RU`, no provider configured,
  expect the honest message and the "Set up provider" tap, not `Unknown command` — has not been run.

## Agentic track — Этап 3 (agentic Master Plan + doctrine matrix) CODE-GREEN (2026-08-19)

Docs plus one guard test; no production source file changed. New folder **`docs/governing/`** —
the *living* governing documents, as opposed to `docs/design/`, which ADR DS-0 declared an archive
of imported inputs.

- **[`sidr-agentic-master-plan-v1.0.md`](../docs/governing/sidr-agentic-master-plan-v1.0.md)** — the
  instrument the engine track never had: block sequence (Этап 4.0 → A0 → A1′ → A4′ → A2/A3 → A5/A6,
  each with goal / inputs / outputs / what is reused / what is out of scope / acceptance), Definition
  of Done, change-control, five milestones, and `Agentic Shell v1 = DONE` (9 criteria, each mapped to
  the `DOC-*` rules that make it checkable). It does **not** supersede the restart plan (owner's
  choice): that document keeps the four owner decisions, the §0 session protocol and `§HANDOFF`.
  It also does **not** pre-empt the A1 fork — parallel tool vocabulary vs. evolving `ActionCatalog`
  in place stays open for A1′.
- **[`sidr-doctrine-matrix-v1.0.md`](../docs/governing/sidr-doctrine-matrix-v1.0.md)** — Master Plan
  §5/§5.1/§5.2/§20.1 extracted out of a design-track document, because half the matrix pointed at
  engineering layers. 28 `DOC-*` rules, four columns: stable ID, rule, verification type from the
  closed vocabulary, and **a real test name or an honest `<нет>` with the debt's address** (12 of 28
  are empty today: A1′ ×2, A2 ×1, A4′ ×5, manual-by-nature ×3, design-track ×1). The philosophy —
  the eight principles in prose — deliberately stayed in the design Master Plan §4.
- **`DoctrineMatrixGuardTest`** (`app/src/test/java/com/sidr/launcher/doctrine/`, 8 tests) fails on a
  false claim: a named test that exists in no source set, a type outside the vocabulary, a vocabulary
  the document widened but the test did not, an anonymous `<нет>`, a lost principle, a rule count that
  drifted. It never fails on an honest `<нет>`. **Proven by six mutations, not assumed** — the first
  negative pass exposed a real defect: the test task was `UP-TO-DATE` because a `.md` outside every
  source set is not a Gradle input, so all four broken matrices "passed". Fixed by declaring the
  matrix as a test-task input in `app/build.gradle.kts`.
- **Filing fixed:** the *living* design Master Plan had been sitting inside the archive folder and was
  not one of its seven documents — `git mv`'d to
  [`docs/governing/sidr-design-system-master-plan-v1.2.md`](../docs/governing/sidr-design-system-master-plan-v1.2.md)
  with the version now in the filename. Its header, §1.2 "Следующая точка" and §24 "Immediate next
  action" all still claimed "next block DS-2" (~10 blocks stale) and were rewritten. §5/§5.1/§5.2/§20.1
  keep their headings as pointer stubs so cited section numbers stay valid.

Gate: `testDebugUnitTest assembleDebug --rerun-tasks` — 551/551 tasks executed, 652 tests / 0
failures; `:domain:jvmTest --rerun-tasks` — 311 tests / 0 failures. `verifyRoborazziDebug` not run —
`core/ui` untouched. ADR: decisions.md "2026-08-19 — Этап 3 complete".

## Agentic track — Этап 1 (strategic ADR package) DONE (2026-08-19)

Docs only, zero code. Governing document:
[docs/superpowers/plans/2026-08-18-agentic-track-restart.md](../docs/superpowers/plans/2026-08-18-agentic-track-restart.md)
(owner-approved 2026-08-18). Four ADRs accepted 2026-08-19 in `decisions.md`, plus the edits they
mandate in `CLAUDE.md`, `docs/architecture.md`, `docs/roadmap.md`, `docs/agentic-os-architecture.md` and
`docs/adr/ADR-001-hybrid-ai.md`:

1. **Deterministic-first redefined** — understanding belongs to the model, execution to the
   deterministic layer. FastPath is a latency optimization, not a filter; a FastPath miss no longer
   answers "Unknown command". Owner fork resolved: `llmRouterEnabled` is **inverted onto a new key**
   (`localOnlyMode` / `flag_local_only`, default `false`) — a default flip in place would be inert on
   any install that ever saved a setting (DS-11 precedent). Sequencing consequence recorded: Этап 0.2
   (FastPath `ru`/`tr`) is a prerequisite of shipping that flag.
2. **Platform re-baseline 2026** — ONNX NLU closed and OQ#1/#2/#3 closed; **LiteRT / LiteRT-LM**
   designated as the local-inference runtime (ExecuTorch recorded as the alternative with a switch
   condition; AICore a separate path); `AppFunctions`/`MCP` named as first-class tool sources;
   performance budgets rewritten as three tiers (invariant / measured-baseline-with-gate / per-profile).
3. **Portable core boundary** — "Framework" = a portable agent core with two consumers (Android + PC);
   Stage 2 abolished as a schedule stage; `:domain` → KMP in Этап 2.2; **`ActionIds` frozen
   byte-for-byte**, with both of its contracts spelled out (`launch_app` = Room PK; all seven = the
   outbound wire contract).
4. **Assistant ⊕ Agent** — one conversational loop, two surfaces (owner fork resolved).

**Этап 0.1 CLOSED the same day (2026-08-19) — the release gate is cleared.** The owner reviewed the
locale package and directed the marker; all 10 `values-{ru,tr}/strings_locked.xml` files carry
`OWNER-REVIEWED 2026-08-19`, each header recording that the review was the owner's and the token was
typed by the agent on their instruction (the gate cannot distinguish the two, so the file says which it
was). `:app:checkOwnerReviewedLocaleStrings` and `:app:assembleRelease` both exit 0 — **the first
release artifact this project has produced**. Gap found and fixed before signing: the review package did
not list the five `launcher_prayer_name_*` keys I18N-2 had added to a file it was asking the owner to
sign (now Section 6e; byte-identical to the brief-verbatim Section 1b). **Measured baseline for Этап
0.3: 78 MB APK, ONNX 70.4 MB of it (~90%; ~18.5 MB per-device via AAB)** — in ADR 2/4. **Still open:**
the gate checks marker *presence*, not *coverage*.

**Этап 0.2 CLOSED the same day (2026-08-19) — FastPath localized to `ru`/`tr`.**
`RuleBasedIntentMatcher` (`:data:repository`) gained `ru`/`tr` forms for `LAUNCH_VERBS`/
`INSTALL_VERBS`/`SEARCH_VERBS`/`SETTINGS_KEYWORDS`/`SIMPLE_COMMANDS`, closing the device regression
("открой телеграм" → `UnknownIntent` at 0.10 confidence on `ru-RU`) and clearing 0.1's own
prerequisite for shipping the `llmRouterEnabled` → `localOnlyMode` flag. Verb forms became
locale-tagged (`VerbForms(prefixByLocale, suffixByLocale)`, not a flat set) and gained a suffix-match
path alongside the existing prefix-match — both forced by Turkish being verb-final (SOV: "telegramı
aç", not "aç telegram"), which a flat English/Russian-shaped set cannot recognize. New
`FastPathLocaleGuardTest` fails the build if any set is missing an `en`/`ru`/`tr` form. Documented
limitation, not silently fixed: Turkish noun-case suffixes are not stripped from the extracted
app-name query (out of this stage's morphology scope). Gate (JDK-17): root `testDebugUnitTest
assembleDebug` + `:core:ui:verifyRoborazziDebug` SUCCESSFUL; `RuleBasedIntentMatcherTest` 34→43 tests.
ADR "2026-08-19 — Этап 0.2 complete — FastPath localized to ru/tr" in `decisions.md`.

**Этап 0.3 CLOSED the same day (2026-08-19) — the ONNX stack is deleted.** Executes ADR 2/4. Removed:
the whole `:data:ai-local` module, its `:app` DI/work wiring, `SidrLauncherApp`'s ONNX teardown branch,
`LayeredIntentMatcher`/`NluConfidenceCalibrator` (unqualified `IntentMatcher` now binds
`RuleBasedIntentMatcher()` directly), `ModelAvailabilityRepositoryImpl`, `KtorModelDownloader`, and
`domain/ai/local/` + `MatcherSource.NLU`. Compiler-forced beyond the plan's named list:
`SemanticSuggestionRanker` (its whole body depended on the now-deleted `TextEmbedder`; suggestions now
rank via plain `HeuristicSuggestionRanker`) and `LocalInferenceGate` (zero callers left). `DeviceProfile`/
`DeviceCapability`/`DeviceProfileProvider`/`AndroidDeviceProfiler` are untouched per the plan, though
`DeviceProfileProvider`'s `@Provides` had to move to a new `app/di/DeviceProfileProvidesModule.kt` (its
old home was deleted with `ModelProvisionProvidesModule`). **Release APK: 78 MB → 6.8 MB.** Gate
(JDK-17): root `testDebugUnitTest assembleDebug` + `:core:ui:verifyRoborazziDebug` +
`:app:assembleRelease` all SUCCESSFUL; `:domain` classpath = stdlib + coroutines only. ADR "2026-08-19 —
Этап 0.3 complete — ONNX stack removed" in `decisions.md`.

**Этап 0.4 CLOSED the same day (2026-08-19) — `CLAUDE.md` compressed 101 KB → 15 KB.** The file is now
current state + hard rules + pointers only; every historical block it narrated (Blocks A→W, Phase UX
X1–X6, Phase 9 Y1–Y7, AIL-0…6, S2-1/S2-2, DS-0…DS-11, Vision MVP, I18N-1/2, the four agentic ADRs) was
verified block-by-block to already exist as an ADR in `decisions.md` before its narration was deleted;
nothing was copied, nothing was lost, and the pre-compression text stays verbatim in git at
`9de23ab:CLAUDE.md`. Owner decided both forks at the start of the stage: **pointers only** (no archive
duplicate file) and the **`Contract → Owner module` table stays in `CLAUDE.md`**, cleaned of per-block
annotations and of the ONNX rows deleted by 0.3. Three things were carried forward rather than dropped
because they were load-bearing but buried in the archive: a `Build & verification gate` section (JDK 17
toolchain, the gate command, the "never pipe `gradlew` through `tail`" rule that masked a red gate on
2026-07-13, commit-but-never-push), a `Known debt` list (device-pending DS-5/I18N-1/DS-6B items, the
766 ms cold start and never-measured heap, the `OWNER-REVIEWED` presence-vs-coverage gap, the Turkish
case-suffix limitation, the untested device matrices), and a `History map` naming which ADR to open per
track. Docs only — zero code, zero test changes. ADR "2026-08-19 — Этап 0.4 complete — CLAUDE.md
compressed" in `decisions.md`.

**Этап 0.5 CLOSED the same day (2026-08-19) — honest statuses.** Introduced `CODE-GREEN` /
`DEVICE-ACCEPTED` / `CLOSED` (definitions at the top of this file) and re-audited the three blocks the
plan names. **DS-5** and **I18N-1** were both self-labelled `CLOSED`-adjacent (`CODE-CLOSED`, `CLOSED —
device-verified`) while their own ADR bodies already listed real, un-run device checks — corrected to
**CODE-GREEN** for both; no fact changed, only the terminal label, which previously overclaimed. **DS-6B**
was checked and confirmed correctly **CLOSED** — the owner ran the full on-device acceptance and its MWL
limitation is a named residual, not a hidden one; `CLOSED` does not mean zero debt. Docs-only, zero code:
`CLAUDE.md`'s `Known debt`/`Shipped surface` and this file's headings for DS-5/I18N-1 updated to match.
ADR "2026-08-19 — Этап 0.5 complete — honest statuses (CODE-GREEN / DEVICE-ACCEPTED / CLOSED)" in
`decisions.md`.

**Этап 0.6 CLOSED the same day (2026-08-19) — budgets rewritten on measured numbers.** First-ever heap
measurement for this project: **55 MB PSS steady-state Home** (SM-A325F, Android 13, current release
build, `dumpsys meminfo`, 4 readings 5 s apart, first dropped, remaining three stable within 0.1 MB —
Native Heap 33 MB + Dalvik Heap 5 MB + Code/other 18 MB, Heap Alloc ≈ 25 MB of that). Comfortably under
the old, never-verified 80/150/250 MB ceilings, which the three-tier scheme (already rewritten in Этап 1)
now supersedes rather than gates on. `docs/architecture.md`'s tier-2 heap row and tier-3 ceiling note
updated with the measured value; `CLAUDE.md`'s `Known debt` performance line updated to match. Optional
hardening (`MacrobenchmarkRule` + `StartupTimingMetric`/`MemoryUsageMetric` in `baselineprofile/`) left
undone — the section marks it "при желании", not required, and `baselineprofile/` still contains only
`BaselineProfileGenerator.kt`. Docs-only, zero code/test changes: `git diff --stat` is `.md` files.
ADR "2026-08-19 — Этап 0.6 complete — performance budgets rewritten on measured numbers" in
`decisions.md`.

**Этап 0.7 CLOSED the same day (2026-08-19) — I18N-2 residue, both items pre-resolved.** An audit against
the two items the plan named found both already fixed by I18N-2 work predating this plan: the
`PrayerSummaryMapper` `contentDescription` fix (`85b5f0a`) and the `DEVICE_LOCATION_LABEL` KDoc
(`c51459e`) confirming it is a cache-identity key, not display copy, translated separately at render time.
The `CLAUDE.md` follow-up (remove the stale "hardcoded Current location label" debt note) was likewise
already done by `8ca58ab`. This stage's own ADR entry had not been written until this audit — the
"✅ ЗАКРЫТО" text in the plan document dates to the plan's first commit, not a dedicated closing session;
`decisions.md` now carries "2026-08-19 — Этап 0.7 complete — I18N-2 residue reviewed, both items
pre-resolved" to close that gap. Docs-only, zero code changes.

**Этап 2 CLOSED (2026-08-19) — toolchain bumped to Aug-2026-current, `:domain` on Kotlin Multiplatform.**
2.1: version catalog jumped to the real current versions — Gradle 9.5.0, AGP 9.3.1, Kotlin 2.4.10,
Compose BOM 2026.08.00, compileSdk 37 (owner chose this "aggressive" set over a conservative
one-AGP-minor-step alternative after being shown both, per forks-before-code — the plan named the
direction but not exact versions). Forced follow-ons, each a compiler/framework rejection rather than
a discretionary choice: Hilt → 2.60.1 (2.52 couldn't classload against the new KSP), Room → 2.8.4
(2.6.1's KSP processor crashed), `androidx.hilt` → 1.4.0 (1.2.0 couldn't read Kotlin 2.4.10 metadata),
Robolectric → 4.16.1 (4.14.1 doesn't know API 37 — and 4.16.1, current-latest, still caps at 36, so
`targetSdk` stays 36 while `compileSdk` is 37); `material-icons-core` now declared explicitly in 7
modules (Compose 2026.08 stopped pulling it in transitively via material3); AGP 9's built-in-Kotlin +
new DSL opted out via `gradle.properties` (a separate migration, not folded in here). 34 of `core/ui`'s
128 Roborazzi goldens drifted from the Compose BOM jump — reviewed visually before re-recording, all
benign spacing-only reflow, no content/logic change. 2.2: `:domain` converted to `kotlin.multiplatform`
(`android`+`jvm` targets, ADR 3/4) — 108 production files moved to `commonMain` (`git mv`, history
preserved), 41 test files to `jvmTest` (JUnit4 isn't `commonTest`-portable). Three privacy/scope guard
tests hardcoded the old source path; two would have passed **vacuously** post-move (empty directory
scan, no exception) — found by grep, fixed before it mattered. `:domain:dependencies` confirmed
stdlib+coroutines only, post-conversion. Full gate green (`testDebugUnitTest assembleDebug
--rerun-tasks`, 551/551 tasks genuinely executed, 955 tests/0 failures), `:core:ui:verifyRoborazziDebug`
green, `:app:assembleRelease` green both sides of the `:domain` conversion (APK 7,031,864 bytes, no
regression from AGP 9's stricter R8 keep-rule semantics). ADR "2026-08-19 — Этап 2 complete —
toolchain bumped to Aug-2026-current, `:domain` on Kotlin Multiplatform" in `decisions.md`.

**Next:** Этап 3 (agentic Master Plan + doctrinal matrix) → Этап 4.0 (invert the understanding flag) →
Этап 4 (A0 thin spike). Этап 0 and Этап 2 are now fully closed.

## I18N-2 residual localization + barrier 4 — CLOSED (2026-08-19)

Closes two of I18N-1's own device-smoke residue items and installs a fourth regression barrier the
first three could not have caught. Fix 1: Home's per-cell prayer `contentDescription` was reading the
raw `PrayerName` enum literal (`name = name.name`) instead of resolving through `sidrString` — invisible
in English (the locked resource happens to equal the enum name), audible as literal English in `ru`/`tr`
TalkBack. Fix 2: `AndroidPrayerLocationProvider`'s `"Current location"` label rendered untranslated on
Home, the prayer detail screen, and (a third, previously unnoticed instance) the prayer settings screen
— fixed by adding a `PrayerLocationSource` discriminator to `PrayerScheduleProvenance` and resolving the
display text at render time, while the **stored** value (which doubles as
`GetPrayerContextUseCase.matchesSetup`'s cache-validity key) stays byte-identical, so no cache
invalidation. Barrier 4, `DomainIdentifierLeakGuardTest`, catches a raw `.name`/`.toString()`/`.key`/
`<Id>.value` property chain assigned directly to a display sink — the class of bug I18N-1's three
barriers structurally could not detect (a real, translated resource existed and was simply never
called). Proven regression-catching both ways: the fixed tests were run red against the reverted code,
and the new barrier was run red against the reverted `PrayerSummaryMapper.kt`, before both were restored
green. Test deltas: `:data:prayer` 37→38, `:feature:launcher` 156→161, `:app` 15→18 (on top of Task 0's
+2 guard-the-guard tests already folded in); `:domain`/`:core:android`/`:feature:prayer` untouched by
count (existing call sites updated in place, compiler-enforced). Gate green (JDK-17), goldens untouched.
Deliberately out of scope: `CalendarSuggestionProvider`/`LocationSuggestionProvider`'s fixed labels
(near-zero real reach) and `RISK_CONFIRM_LABEL` (already-recorded owner-approved locked-vocabulary
exemption) — both remain routed to a future I18N pass. A named, unclosed gap surfaced but not fixed
here: `checkOwnerReviewedLocaleStrings` checks the `OWNER-REVIEWED` marker's *presence*, not *coverage*
— a signed file can silently gain an unreviewed key later. ADR "2026-08-19 — I18N-2 residual
localization + barrier 4" in `decisions.md`.

## I18N-1 Multilingual UI — CODE-GREEN (2026-08-16); not device-accepted, not closed

`en`/`ru`/`tr` shipped across every migrated production screen behind a single `core/ui` seam
(`sidrString`, entry-name-keyed overlay for a future `translate_ui`). Two owner exemptions stay English
(the five `PREVIEW` mock-up tabs, the hidden dev console). 11 sub-UI strings got a new typed
`domain` contract (`CommandMessage`/`CommandFailure`) resolved by a feature-layer mapper — a scope
expansion found while planning. Three regression barriers (`StringSeamGuardTest`,
`LocaleCompletenessGuardTest`, `HardcodedUiTextGuardTest`) plus a new release gate,
`checkOwnerReviewedLocaleStrings`, that makes `:app:assembleRelease`/`:app:assemble`/`:app:bundleRelease`/
root `./gradlew build` **RED BY DESIGN** until the owner marks all 10 locale `strings_locked.xml` files
`OWNER-REVIEWED`. Per-app language switch via `AppCompatDelegate` (`LauncherActivity` →
`AppCompatActivity`, theme re-parented, `windowBackground` unchanged — no white-flash regression); no new
DataStore key. Cannot claim byte-for-byte VM-suite parity (a first for a DS-adjacent block) — see the ADR
for the itemized before/after. Zero `<plurals>` needed; the pseudolocale barrier resolved to real
`en-XA` (no overlay fallback needed). Gate green (JDK-17): `:domain` 333/0, `:core:ui` 126/0 +
`verifyRoborazziDebug`, `:feature:launcher` 156/0, `:feature:settings` 33/0, `:feature:assistant` 38/0,
`:feature:prayer` 12/0, `:feature:permission_education` 15/0, `:data:repository` 167/0, `:app` 13/0, root
`testDebugUnitTest` + `assembleDebug` SUCCESSFUL; goldens untouched (36 PNGs, 0 new/modified). Device
(SM-A325F, system locale `ru-RU` throughout): in-app language switch instant/correct on every migrated
screen, Turkish dotted-İ correct, force-stop persistence confirmed, Home date line proven to follow the
app locale (read Turkish while the device stayed on `ru-RU`) — the exact regression the block's own brief
flagged. Not device-covered: system per-app-language picker (owner-gated), offline path (tethering), live
TalkBack, fontScale 2.0, release-build cold-start comparison (blocked by the new gate itself). Two new
residue items found during this pass's own smoke, routed to I18N-2 rather than fixed here: a hardcoded
`"Current location"` label in `core/android` (out of spec scope, same class as the already-known
suggestion-label gaps), and a real bug where Home's per-cell prayer `contentDescription` announces the
untranslated enum name instead of the localized prayer name (`PrayerSummaryMapper.kt:64`). **All device
verification above was agent-driven (`adb`/`uiautomator`); the owner has not run an on-device acceptance
pass.** Under Этап 0.5's vocabulary this block is **CODE-GREEN**, not `CLOSED` — the four "not
device-covered" items are real open debt, carried in `CLAUDE.md`'s `Known debt` since 0.4. ADR
"2026-08-16 — I18N-1 Multilingual UI complete" in `decisions.md`, corrected by the Этап 0.5 addendum;
plan `docs/superpowers/plans/2026-08-11-i18n-1-multilingual.md` (code-complete; device acceptance still
pending the owner).

## Design track (DS) — DS-1…DS-4 + Vision MVP DONE; DS-5 CODE-GREEN (2026-07-13, not device-accepted);
DS-6B CLOSED (2026-08-08); DS-7 + S2-2 CLOSED (2026-08-10); DS-10 CLOSED — device-accepted
(2026-08-10). **The DS v1.1 release gate is now open, but blocked from producing a release artifact
until the owner completes I18N-1's `OWNER-REVIEWED` sign-off (see above).**

**DS-10 Assistant Migration is CLOSED — device-accepted on SM-A325F (2026-08-10).** The Assistant was the
last production surface not yet speaking SIDR v1.1. Note the spec's baseline was stale: the Vision MVP
pass had already moved the screen off raw Material, so DS-10's real delta was the DS-5 layer, the
accessibility contract, the layout split, and the missing coverage. Landed: presentation-only
`core/ui/component/SidrAssistant.kt` (`SidrAssistantComposer`, one shared send guard for IME + button,
send's `contentDescription` names why it is unavailable; `SidrStreamingIndicator`, a single polite live
region so "Replying…" is announced once per state change rather than per token) and pure
`feature/assistant/AssistantPresentation.kt` (DS-5 what/why/next copy, at most one action —
`Retry` / `Fix provider settings` / none for `InvalidRequest` — cloud-disclosure text, host+model+key
provenance). `AssistantScreen` split into shell/content/message/status-line/composer/provider panels;
idle-with-no-reply *is* the cloud disclosure; provenance pinned above the composer; errors render through
`SidrErrorSurface`; refusal stays calm text; the API-key field gained `password()` semantics.
**Nothing behavioural changed by the migration** — `AssistantUiState` and the domain/data path were not
edited, `AssistantViewModel` was touched only by the write-order fix below, and its original 21 tests
pass unchanged. Gate (JDK-17, force-rerun, exit 0):
`:core:ui` 117/0 + `verifyRoborazziDebug`, `:feature:assistant` 35/0, `:domain` 332/0, every other module
green, root `testDebugUnitTest` + `assembleDebug` SUCCESSFUL; four additive `assistant_*` goldens.
Device: no-provider disclosure → provider form (masked key, "Key set" copy, gated Save) → real BYOK
streaming through OpenRouter/`openai/gpt-4o-mini`; ASK-route prompt prefilled and never auto-sent; a real
404 rendered the unactionable `SidrErrorSurface`; force-stop proved nothing is persisted; 0 key leaks.
**Fixed mid-pass:** an empty provider form no longer claims `CLOUD`. **Also fixed (pre-existing race, surfaced on
device):** right after a key save the chat VM instance showed `NO KEY SET`, because `keySet` is
recomputed only on `activeConfig()` emits while `saveProvider` wrote the config before the Keystore put.
`saveProvider` now writes the key first and the config last, guarded by an ordering test proven to fail
on the old order; no other ViewModel change.
**Not device-covered:** retryable-network error + Retry (cutting mobile data killed the owner's tethered
laptop, so the experiment was stopped — the mapping is unit-covered), credential-CTA 401, refusal,
fontScale 2.0 / RTL / light on the live screen, live TalkBack. **Deviation:** Task 6 ships as a `core/ui`
gallery of the Assistant's states rather than whole-screen goldens, since Roborazzi is wired only in
`:core:ui`. ADR: decisions.md "2026-08-10 — DS-10 Assistant Migration complete (device-accepted)".

**DS-7 Memory Surfaces and Stage-2 S2-2 Explicit Aliases are CLOSED — device-accepted on SM-A325F
(2026-08-10).** Both were implemented on `launcher--7` back on 2026-07-13 (`8e3f317` `core/ui`
`SidrMemoryItem`/`SidrMemoryDisclosure`/`SidrForgetGate` + 4 `memory_*` goldens; `b2affdd` Learned
Choices migrated behind a feature-local `LearnedChoiceMemoryUiModel` mapper + new
`AliasesScreen`/`AliasesViewModel` + `Routes.Aliases` + Settings **MEMORY** section) and shipped in every
build since — the docs simply never recorded it, which is why this file used to say "next block: DS-7".
S2-2's stranded `launcher-4` last mile was re-applied **by hand** as `7c20b63` (alias decorator into
`LauncherViewModel`) + `0198abc` (`AliasPrivacyScopeGuardTest` + `RoomColumnNames` entry); the third
stranded commit (`cef4111`, docs) was intentionally dropped. Alias resolution still fires **only** on
`CommandOutcome.Unknown`, the outbound allow-list is widened by **zero**, and `core/ui` still imports no
domain/data type. Gate (JDK-17, 2026-08-10, force-rerun): `:domain` 332/0, `:core:ui` 108/0 +
`verifyRoborazziDebug`, `:feature:settings` 33/0, `:feature:launcher` 130/0, root `testDebugUnitTest` +
`assembleDebug` green. Device pass: alias add → row → phrase launches the declared app directly; `open
opera` parity; unknown phrase → unchanged fallback; Forget gate (Cancel never deletes, Forget deletes
once) on **both** memory surfaces; `open python` ambiguity → learned choice `LEARNING (1/3)` → Forget →
asks again; fontScale 2.0 wraps cleanly. **Not covered on device:** unavailable-target prune (would
require disabling one of the owner's apps) and a live TalkBack session (accessibility tree read
instead). **Deviation:** `SidrMemoryDisclosure` is preview-only — no production screen shows it, since
no honest "a stable preference just formed" event exists (the plan permits this). **Follow-ups
(non-blocking):** the Aliases list renders *after* the whole app picker (long scroll); `AliasesViewModel`
discards save/delete `OperationResult`s (silent on genuine I/O failure); the bottom tab bar clips its
labels at fontScale 2.0 (DS-5/Vision-MVP territory). ADR: decisions.md "2026-08-10 — DS-7 Memory Surfaces
+ S2-2 Explicit Aliases complete (device-accepted)".

**DS-6B Prayer Correctness is COMPLETE — device-accepted by the owner on SM-A325F (2026-08-08); the
shipped Home strip is times-only (calm status chip + provenance line hidden, degraded-state warnings kept,
provenance invariant intact — retained in TalkBack + on the detail screen).** All
build tasks are implemented and each passed a fresh-reviewer gate (ledger:
`.superpowers/sdd/progress.md`). New `:data:prayer` (offline `AdhanPrayerCalculator` over
`com.batoulapps.adhan:adhan2:0.0.5`, MIT-in-POM, pinned for Kotlin/kotlinx-datetime compatibility, the
Kotlin port used instead of the originally-named Java "adhan-java" because that port lacks a
`TURKEY`/Diyanet method) + new `:feature:prayer` (setup/detail screens, pushed route, no new tab).
Method + Asr madhab are explicit first-run choices with no default (11 supported methods; `OTHER`/
`TEHRAN` excluded — genuinely absent from adhan2:0.0.5). Location: a bundled offline GeoNames city index
(19,481 cities, 286.8 KB gzipped, CC-BY 4.0 attributed) plus an optional one-shot device-location read
that rounds to 2dp before crossing the port boundary — no continuous tracking, no coordinate logging.
Privacy proven by a 10-guard `PrayerLocationPrivacyGuardTest`: `OutboundContextPolicy.ALLOWED` unchanged
(4 values, zero new outbound surface), no prayer/location field in `AiRequest`, a planted coordinate
sentinel never reaches `PromptContextBuilder.build`. New `PermissionFeature.PRAYER_LOCATION` (documented
deviation — honest prayer-specific copy instead of reusing `LOCATION_SUGGESTIONS`'; no new manifest
permission, `ACCESS_FINE_LOCATION` already present since Block U). `LauncherViewModel` gained exactly one
new dependency (`GetPrayerContextUseCase`) behind a lazy `WhileSubscribed` `StateFlow`; the strip renders
below the Shahada anchor, opt-in, only when `Available` — never fabricated times; `LauncherViewModelTest`
parity held byte-for-byte. **Owner UI refinement (2026-08-08, on-device SM-A325F):** the shipped strip is
**times-only** — status chip hidden for calm states but kept as a warning label for degraded/stale
states, prayer names moved to per-cell `contentDescription`, provenance line removed from the strip
(still required by its invariant, still in the strip's own `contentDescription`, still full on the
detail screen). **Full Verification Gate GREEN** (JDK-17): `:domain:test :data:prayer:testDebugUnitTest
:data:repository:testDebugUnitTest :feature:prayer:testDebugUnitTest :feature:settings:testDebugUnitTest
:feature:launcher:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL. **Device status: the agent-driven pass was
PARTIAL** — on SM-A325F the no-data invariant passed (fresh install → calm Home, no
strip/prompt/fabricated times) and a real Diyanet/Turkey setup rendered a correct-looking strip, but the
full interactive + religious-correctness acceptance (authority-table cross-check, airplane-mode cache,
stale path, device-location grant, tz-conflict, font-scale 2.0, TalkBack, router-off parity) needed the
owner. **The owner then ran that full pass the same day (2026-08-08) and accepted the block** —
interactive setup, the strip rendering a correct schedule, and the authority-table cross-check were all
performed and signed off; **DS-6B is CLOSED**, not merely code-green. **Known limitation, still open and
named rather than hidden:** Istanbul (Diyanet) and Makkah (Umm al-Qura) golden times are
authority-table-anchored; London and Kazan (MWL) are cross-implementation-verified only (MWL has no
official authority portal) — this stands next to the CLOSED label, not instead of it (Этап 0.5). DS-6A
(the Shahada component) stays UI-only and separate from DS-6B. ADR: decisions.md "2026-08-08 — DS-6B
Prayer Correctness (COMPLETE — device-accepted by owner)".

**DS-5 Action & Safety is CODE-GREEN (implemented 2026-07-12, reconciled + gated 2026-07-13);
not device-accepted, not closed (Этап 0.5).** `core/ui/component/SidrActionSafety.kt` (`SidrActionProposal`,
`SidrPermissionNotice`, `SidrPrivacyNotice`, `SidrResultSurface`, `SidrErrorSurface`,
`SidrOfflineState`, `SidrBlockedState`, shared `SidrSurfaceActions` stacking at fontScale ≥ 1.7) +
hardened one-shot `SidrActionGate`; adopted in `LauncherScreen` (SAFE → `SidrActionProposal`,
CONFIRM → `SidrActionGate`) and `PermissionEducationScreen`; `ErrorState` delegates to
`SidrErrorSurface`; `ConfirmActionCard` usage now zero (not yet `@Deprecated`). No ViewModel change →
parity structural. Deviation: the plan's `ActionSafetyGallery` screenshot matrix was not delivered
(behavioural tests only; follow-up). **Same pass closed two owner features (commit `a6ec4e6`):
auto-hide bottom nav** (5 s idle → `SidrChromeHandle` pill; pin via `UserPreferences.alwaysShowNavBar`
+ Settings toggle; spec `2026-07-12-auto-hide-nav-bar-design.md`) **and accent reactivation** —
`AccentColor {GREY, GREEN, AMBER}` as full themes (`sidrColorsFor`; sacred + status pinned; default now
`"grey"`; Settings selector). **Stabilization (2026-07-13):** `alwaysShowNavBar` round-trip test added;
4 new `green/amber_sample_*` goldens (+ accent/status proof lines in the theme samples); stale
`withAccent` comment fixed; brittle `AppNavHostReentryGuardTest` literal relaxed (re-entry behaviour
itself verified intact); **full gate green** (JDK-17): `:core:ui:verifyRoborazziDebug` `:domain:test`
`testDebugUnitTest` `assembleDebug`. ADR: decisions.md "2026-07-13 — DS-5 + auto-hide nav + accent
reactivation closed". Device-pending list lives in that ADR.

**Vision MVP (Preview) is DONE, device-accepted (SM-A325F).** Plan:
`docs/superpowers/plans/2026-07-11-vision-mvp-preview.md` (13 tasks). Extends the DS-3/DS-4 artifact look
to every remaining production screen (App Drawer grid + Groups/A-Z toggle, Assistant, AI provider
settings, Memory/Learned Choices, Permission Education), adds the 5-tab bottom bar
(`SidrTab`/`SidrTabBar` in `app/navigation/SidrTabScaffold.kt`) with Home reconciliation (gear icon,
de-duped Assistant row), and delivers DS-8/DS-9/DS-10's Tasks/Agents/Activity/Terminal surfaces in the
**preview-only** form those specs already called for (`feature/launcher/preview/*PreviewScreen.kt` — every
screen `PREVIEW`-badged, every callback inert, zero fabricated data, Terminal architecturally incapable of
producing output), plus an Interaction-Moments (Result/Partial/Error) preview. Presentation-only
throughout — no domain/data/VM/route/persistence change; every ViewModel test suite passes byte-for-byte.
Full gate + SM-A325F device acceptance green. ADR: decisions.md "2026-07-11 — Vision MVP preview + all
screens to artifact".

Parallel presentation-only design-system migration to the approved **soft-classic-grey** identity, governed
by `docs/governing/sidr-design-system-master-plan-v1.2.md` (§5.1 precedence, §5.2 verify-vocab, §20.1 calm budgets
— all three extracted to `docs/governing/sidr-doctrine-matrix-v1.0.md` in Этап 3; the Master Plan keeps pointer stubs at those numbers
folded in from the principles rulebook). **DS-1** = grey token layer (both themes) + tri-font + softened
shapes + `SidrTheme.colors`/`textStyles` (accent inert) + global CRT removed + **Roborazzi harness**.
**DS-2** = eight additive `core/ui/primitive/` primitives + `Strokes` token + preview gallery goldens
(dark/light/font-scale-2.0/RTL); keystone `SidrProvenanceLine` (semantic `source`+`details`, TalkBack);
status≠accent; **no production screen/nav/VM/persistence/domain change**. Full gate green. **DS-3 is DONE**:
SIDR controls (buttons, chips with press-invert, rows, top bar, Action Gate) live in `core/ui/component/`;
Settings migrated as proof surface (no VM/key/routing change); routed confirmation migrated to
`SidrActionGate` (SAFE one-tap parity preserved). Artifact captures: `docs/design/artifacts/e34033dd/`;
spec: `docs/superpowers/specs/2026-07-11-ds3-controls-design.md`; plan (DONE):
`docs/superpowers/plans/2026-07-11-ds3-controls.md`. ADR: decisions.md "2026-07-11 — DS-3 controls complete".
**DS-4 is DONE (2026-07-11, device-accepted)**: Home migrated to the soft-classic-grey intent-first layout
and `SidrCommandPrompt` replaced by `core/ui` `SidrUniversalInput` (`>` prompt/block-caret/mic/clear/route
slot, parameterless `onSubmit`). `LauncherScreen` recomposed feature-locally: `HomeTopRow` (SIDR wordmark +
dev-arm + **Gregorian+Hijri** date via `java.time.chrono.HijrahDate` + Settings icon), **empty
`HomeAnchorSlot`** seam reserved for DS-6A (no fake prayer/sacred data), results overtake on DS-3
`SidrRouteChip` + DS-2 `SidrText` with stable row heights, feedback/pending on DS-2/DS-3
(`Ambiguous`=clarification, CONFIRM=`SidrActionGate`, SAFE=one-tap chip), bottom `CommandBar` **retired** →
All Apps + Assistant `SidrNavigationRow`s + local-first `HomePrivacyLine`. **Command pipeline / routing /
voice / offline / router-off parity intact** (`LauncherViewModelTest` byte-for-byte). Owner decisions: empty
sacred seam, Gregorian+Hijri, retire+redistribute CommandBar. `SidrCommandPrompt` `@Deprecated` (usage
zero). 3 new `universal_input_*` Roborazzi goldens. Full gate green (JDK-17 toolchain) + **SM-A325F device
pass** (typed launch, WEB/SITE/ASK, router-off "Unknown command" parity, Settings/All apps/Assistant
discoverability, no crash). Spec:
`docs/superpowers/specs/2026-07-11-ds4-home-universal-input-design.md`; plan (DONE):
`docs/superpowers/plans/2026-07-11-ds4-home-universal-input.md`; ADR: decisions.md "2026-07-11 — DS-4
complete". **DS-5 Action & Safety is CODE-GREEN (2026-07-13, see the section above; not device-accepted)**:
`docs/superpowers/specs/2026-07-11-ds5-action-safety-design.md` +
`docs/superpowers/plans/2026-07-11-ds5-action-safety.md`. **Next design block: DS-10 Assistant Migration
— the last production surface still in the pre-v1.1 look — then the DS v1.1 release gate** (DS-7 closed
2026-08-10; DS-8/DS-9 stay contract-only until A4/A5 exist). **DS-6A Sacred Header is drafted but gated after
DS-5, DS-4 Home integration, and Arabic rendering approval**:
`docs/superpowers/specs/2026-07-11-ds6a-sacred-header-design.md` +
`docs/superpowers/plans/2026-07-11-ds6a-sacred-header.md`. **DS-6B Prayer Correctness is now drafted as
the separate religious-correctness capability track**:
`docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md` +
`docs/superpowers/plans/2026-07-11-ds6b-prayer-correctness.md`; it requires authority/method/privacy
requirements before code and forbids fake prayer times or precise-location leakage into AI/cloud. **DS-7 Memory Surfaces is CLOSED
(device-accepted 2026-08-10) — Learned Choices migrated + the Aliases surface shipped with S2-2**:
`docs/superpowers/specs/2026-07-11-ds7-memory-surfaces-design.md` +
`docs/superpowers/plans/2026-07-11-ds7-memory-surfaces.md` (STATUS DONE). **DS-8 Activity Foundations, DS-9
Execution Foundations, and DS-10 Assistant Migration are also drafted as next design-track specs/plans**:
`docs/superpowers/specs/2026-07-11-ds8-activity-foundations-design.md` +
`docs/superpowers/plans/2026-07-11-ds8-activity-foundations.md`,
`docs/superpowers/specs/2026-07-11-ds9-execution-foundations-design.md` +
`docs/superpowers/plans/2026-07-11-ds9-execution-foundations.md`, and
`docs/superpowers/specs/2026-07-11-ds10-assistant-migration-design.md` +
`docs/superpowers/plans/2026-07-11-ds10-assistant-migration.md`. DS-8/DS-9 are preview/contract-only until
real A4/A5 runtime/activity backing exists; DS-10 is the next practical existing-surface migration after
DS-3/DS-5 components land. **Downstream A4/A5/A6 architecture specs/plans and the DS v1.1 release gate are
now drafted, and the missing A1-A3 prerequisite specs/plans have been added**:
`docs/superpowers/specs/2026-07-11-a1-tool-capability-design.md` +
`docs/superpowers/plans/2026-07-11-a1-tool-capability.md`,
`docs/superpowers/specs/2026-07-11-a2-context-engine-design.md` +
`docs/superpowers/plans/2026-07-11-a2-context-engine.md`,
`docs/superpowers/specs/2026-07-11-a3-user-memory-design.md` +
`docs/superpowers/plans/2026-07-11-a3-user-memory.md`,
`docs/superpowers/specs/2026-07-11-a4-agent-runtime-design.md` +
`docs/superpowers/plans/2026-07-11-a4-agent-runtime.md`,
`docs/superpowers/specs/2026-07-11-a5-activity-trace-design.md` +
`docs/superpowers/plans/2026-07-11-a5-activity-trace.md`,
`docs/superpowers/specs/2026-07-11-a6-grants-automation-design.md` +
`docs/superpowers/plans/2026-07-11-a6-grants-automation.md`, and
`docs/superpowers/plans/2026-07-11-design-system-v11-release-gate.md`. A1 is the next architectural slice
after S2-2/Alias scope; A4/A5/A6 remain gated on A1-A3 and the golden rule: no agentic surface without real engine backing. ADRs: decisions.md "2026-07-11 — DS-1 complete"
and "2026-07-11 — DS-2 primitives complete".

## Stage 2 — AI Framework · S2-1 "Learned Resolutions" — CLOSED (2026-07-11)

On-device learning of which app the user meant for an ambiguous launch command (rank-first → threshold
auto-resolve at `streak ≥ 3`, fully offline, no LLM/cloud), correctable via Settings → Learned Choices.
**Tasks 1–16 are done on `launcher-4`; build gate (`:domain:test` + `testDebugUnitTest` +
`assembleDebug`) green; SM-A325F device acceptance passed.** Pure
`domain/memory/resolution/` policy + use-cases + decorator (applied only on the rule `NeedsConfirmation`
app-ambiguity branch, record-on-explicit-choice); Room `resolution_preferences` (`SidrDatabase` v2 +
`Migration1To2` + golden `2.json`); DI; `LauncherViewModel` wiring (`AutoLaunch` directive → real
`launchApp`); Settings management screen. Parity intact (`HandleUserCommandUseCase`/`RouteCommandUseCase`
untouched; outbound allow-list widened by zero). Device pass used temporary same-label local `SidrProbe`
fixture APKs, then removed them; screenshots live under `/tmp/sidr_acceptance_*.png`. Observed ambiguous
choice, `learning 1/3`, threshold auto-launch, delete/relearn, correction before K, preferred-target
uninstall invalidation, no stale auto-resolve, and router-off non-ambiguous parity (`open Salatuk`).
Plan: `docs/superpowers/plans/2026-07-06-learned-resolutions.md`; ADR: decisions.md "2026-07-11 — S2-1
Learned Resolutions device accepted + closed".
**S2-2 "Explicit Aliases" is CLOSED (device-accepted 2026-08-10)** — see the DS-7 + S2-2 section at the
top of this file and decisions.md "2026-08-10 — DS-7 Memory Surfaces + S2-2 Explicit Aliases complete".
**Next Stage-2 architectural slice = A1 Tool & Capability** (`docs/superpowers/plans/2026-07-11-a1-tool-capability.md`).

## Project reframed (2026-07-05) — three stages

Owner reframed the project into **Stage 1 — AI Launcher (MVP, now)** → **Stage 2 — AI Framework** →
**Stage 3 — Agentic OS** (ADR "2026-07-05 — Project reframed into three stages"). The Phase 0–9 + Phase
UX foundation below is **done and device-accepted**. The **Stage-1 AI-Launcher MVP is now CLOSED
(2026-07-06, blocks AIL-0…6)** and device-accepted on SM-A325F: universal input + **BYOK cloud LLM action
router** (`CommandPlanner`, a sanctioned third pipeline) + Action Registry + web/URL/Play-Store routing +
risk-gated confirmation — the launcher is genuinely AI-first, with router-off/offline = byte-for-byte
rule-only parity. Plan (closed): `ai-context/ai-launcher-mvp-plan.md`; roadmap: `docs/roadmap.md`. The
**active work is now Stage 2 — AI Framework** (generalize router/registry/context/memory into a reusable
on-device AI framework: Action Registry v2, Context Engine v2, User Memory — built feature-first). **First
slice (S2-1 "Learned Resolutions") is CLOSED (2026-07-11)** — see the dedicated section at the top of this
file. The model track (OQ#1–#4), device matrix, and
RC/hardening polish run in parallel, off the ship gate.

**Stage-1B progress:** **AIL-0 ✅ done (2026-07-05)** — `core/ui` re-skinned to the "ultra-cyberpunk /
early-computer terminal" identity (4 `ColorScheme`s green-default/amber-alt × dark/light, dynamic colour
off by default, `AccentColor` enum, JetBrains Mono bundled/OFL, brutalist 0/2/4/8dp shapes, brand window
background). Presentation-only, `testDebugUnitTest`+`assembleDebug` green; screen redesign deferred to
AIL-3/5/6 (forks DF-1…DF-8). **AIL-1 ✅ done (2026-07-05)** — Action Registry contracts in `domain/action/`
(pure, additive above the untouched `ExecutableAction` path): `ActionId`/`ActionIds` (7 family wire ids),
`LauncherAction` (sealed, unresolved semantic args + `id`), `ActionDescriptor` (catalog+risk+schema),
`ActionRiskLevel {SAFE,CONFIRM,DANGEROUS}`, `ActionCategory`, `ArgType {STRING}`/`ActionArg`, `ActionCatalog`
port + `FakeActionCatalog`. Forks decided: argSchema = `List<ActionArg>` string-only; `LauncherAction` is a
parallel hierarchy (not a wrapper); concrete descriptor catalog + impl deferred to AIL-2. No behaviour
change; 9 new domain tests; `:domain:test`+`testDebugUnitTest`+`assembleDebug` green. ADR: decisions.md
"ADR 2026-07-05 — AIL-1 complete". **AIL-2 ✅ done (2026-07-05)** — Web / URL / Play-Store routing (no AI):
concrete `DefaultActionCatalog` (7 descriptors; open_url/play_store CONFIRM, rest SAFE; `ActionBindsModule`)
+ pure `domain/intent/UrlDetector` (AIL-Q3: http/https scheme allow-list only, curated-TLD open, punycode/IDN
+ query-string → web search, never a silent `intent://`/user `market://`) + new `OpenUrlIntent`/`PlayStoreSearchIntent`
→ `OpenUrlAction`/`PlayStoreSearchAction` executed via `ACTION_VIEW` / `market://` (web fallback). Rule matcher
learned launch-verb URL divert (Q2), `install <app>`→store (Q3 install-only), bare-URL/ambiguous recognition
(R6); **R5** configurable provider from `UserPreferences.webProviderTemplate` (default Google, denylist-clean
key). Contracts (`HandleUserCommandUseCase`/`IntentMatcher`/`GenerateReplyUseCase`) unchanged; launcher fully
offline; `testDebugUnitTest`+`assembleDebug` green; new tests UrlDetector 25 / matcher 25→35 / catalog 7 /
resolver +2 / use-case +2; privacy guard green. ADR: "ADR 2026-07-05 — AIL-2 complete". **AIL-3 ✅ done
(2026-07-05)** — Universal Input: additive `domain/input/UniversalInputRouter` + sealed `InputIntent`
(reuses `UrlDetector`) over one home field; `core/ui` `SidrCommandPrompt` (terminal `>` prompt, DF-2) +
`RouteChipRow` (DF-3 chips); `LauncherViewModel` `inputResults` (live app-filter + WEB/ASK/SITE chips) +
`submitWebSearch`/`submitSite` delegating to the **unchanged** `onCommandSubmitted`; `LauncherScreen`
"search overtakes" body + ASK assistant-prefill nav + hidden session-only dev Command console (7-tap
`SIDR//` arm + `//dev-mode` toggle, no persisted key). Command pipeline **byte-for-byte** (intent-file diff
empty); voice (R8) reuses the path; launcher fully offline; no LLM. `testDebugUnitTest`+`assembleDebug`
green (router 7 / VM 62→69). Deferred to DF-5/AIL-6: true block caret + CRT motion, `>`-glyph a11y polish.
ADR: "ADR 2026-07-05 — AIL-3 complete". **AIL-4 ✅ done (2026-07-06)** — LLM Action Router (BYOK cloud, the
sanctioned **third pipeline**): `domain/ai/router/` `CommandPlanner` port + `PlanResult` + `ActionProposal`
+ fail-closed `ProposalValidator` + `CatalogSchemaRenderer` + `RouteCommandUseCase` (rule-first; planner
consulted **only** on `Unknown`/`LowConfidence`, only when `llmRouterEnabled` + online; proposals surface as
non-executing `CommandOutcome.RoutedAction`, never auto-execute — R4). `data/ai-cloud/LlmCommandPlanner` = a
**separate non-streaming** OpenAI-compatible call reusing the shared `HttpClient`/config/Keystore key;
hard-timeout→`NoPlan` (AIL-Q1), strict content-JSON parse (tolerates fences), prose/hallucination/
non-tool-capable→`NoPlan` (AIL-Q2), **never throws**. `FeatureFlags.llmRouterEnabled` (default off,
denylist-clean key) + "Smart command routing" Settings toggle; `:app` `RouterProvidesModule`; VM injects
`RouteCommandUseCase` (`HandleUserCommandUseCase` unmodified). **§0 guards green:** privacy allow-list
widened by exactly `ACTION_CATALOG_SCHEMA` (domain + real-catalog + outbound-body guard tests; planted
sensitive value never leaves); **router-off / confident / offline ⇒ byte-for-byte rule-only parity** (planner
never consulted). Forks R1–R4 as recommended (no deviation); three ports kept distinct; launcher fully
offline. `:domain:test`+`testDebugUnitTest`+`assembleDebug` green. Device acceptance + confirmation-card/
execution deferred to AIL-6/AIL-5. ADR: "ADR 2026-07-06 — AIL-4 complete". **AIL-5 ✅ done (2026-07-06)** —
Confirmation & safety gating: turned AIL-4's display-only `CommandOutcome.RoutedAction` into an executing
surface (**router-proposals only** → the rule path + its parity are untouched). New pure
`domain/intent/ExecuteActionUseCase` maps a confirmed `LauncherAction` → `LauncherIntent` → the **unchanged**
`IntentActionResolver` + `ActionExecutor` (never throws). VM gained Android-free `PendingRoutedAction` +
`pendingRoutedAction` state: `needsConfirmation` → **confirm card** (CONFIRM / unregistered) or **one-tap**
(SAFE); `confirmRoutedAction()` executes + re-applies the outcome, `cancelRoutedAction()` dismisses; neither
auto-executes (R4). Permission gate handled in the screen via the existing education route (inert in MVP —
all catalog gates `null` — but wired + tested). New dumb `core/ui` `ConfirmActionCard` = **DF-4 terminal
confirm block** (`EXECUTE?` + bracketed `[CONFIRM]` accent risk chip + `> commandLine` + bracketed
CANCEL/CONFIRM; AIL-0 tokens; no `domain→ui` edge). `:app` `provideExecuteActionUseCase`; VM injects it + the
bound `ActionCatalog`. Hard rules intact; rule-only parity structural. New `ExecuteActionUseCaseTest` (11) +
5 VM tests (VM 70→75); `:domain:test`+`testDebugUnitTest`+`assembleDebug` green. Device acceptance deferred
to AIL-6. ADR: "ADR 2026-07-06 — AIL-5 complete". **AIL-6 ✅ done (2026-07-06) — closes the Stage-1 MVP
(AIL-0…6).** Polish + mandatory SM-A325F device acceptance; no new product runtime. Cleared the RC gate
(`:app:assembleRelease`, plus `:domain:test`+`testDebugUnitTest`+`assembleDebug`) — env note (machine-only,
no repo change): system JDK had rolled to 25 (Gradle 8.10.2 can't parse it → `IllegalArgumentException:
25.0.3`), fixed via JBR 21 to run Gradle + a locally-downloaded Temurin **JDK 17** toolchain
(`-Porg.gradle.java.installations.paths`) + git-ignored `local.properties`. **Device pass (SM-A325F/A13,
agent-drove `adb`, owner typed the BYOK key on-device only):** one-field routing (app/site/web/Play-Store/
settings/assistant, external `ACTION_VIEW`, mic visible); **NL router** (real OpenRouter/gpt-4o-mini) —
CONFIRM proposal ("take me to the github homepage" → `open https://github.com`) → **DF-4 confirm card**,
CANCEL = no-op, CONFIRM → github opens; SAFE ("see everything installed" → `show_apps`) → **one-tap
accelerator**; **R4** nothing auto-runs; **router-off/offline ⇒ rule-only parity** (airplane + flag-on NL →
"Unknown command" fallback; launcher fully offline); **privacy** 0 `sk-or-` in logcat + guard tests green;
**DF-7** green↔amber instant + persists across force-stop, **DF-5** block caret + chip press-invert, **DF-6**
`>_` mark / no white flash. Honest partials (non-gating): DF-5 scanlines too faint to frame-capture;
assistant chat streaming (Phase-5 item, not an AIL-6 gate) not re-driven via adb but its transport is proven
by the router round-trip; boot-warmup + LOW_END motion suppression unexercised on this MID/HIGH device. ADR:
"ADR 2026-07-06 — AIL-6 complete". **Stage-1 AI-Launcher MVP CLOSED; active work = Stage 2 — AI Framework
(open a Stage-2 plan before coding).**

## Where we are (foundation — done)

**Phase 7 (voice input + contextual suggestions) — user-facing close SHIPPED (2026-07-01).**
Phases 3 → 7, Phase UX, and **Phase 9 hardening are done** for the available matrix: Blocks
**Y1/Y2/Y3/Y4/Y5/Y6/Y7 are done** (startup release perf + release R8/Baseline Profile +
contextual-suggestions correctness + test-coverage hardening + privacy/logging/error handling +
Android 13 / LOW_END validation pass + residual cosmetic cleanup). The separate model track (real ONNX
models, the inert embedder seam, OQ#1-OQ#4) is still outside Phase 9.

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
  **Startup perf debt retired by Phase 9 Y1/Y2 (2026-07-04):** final release on SM-A325F warm median
  ~102ms, cold median 766ms after dropping first, and no Loading spinner on the first home frame
  (`<400ms` remains aspirational, not a ship gate).
  **Follow-up fix (owner-approved, same run):** added a **"Personalize from usage"** opt-in `Switch` to
  Settings (writes `FeatureFlags.usageHistoryEnabled`, off by default) — device-verified that the Favorites
  row now populates, the count selector visibly changes it (4→5), and usage-based suggestions surface real
  installed apps. **Phase 9 Y3 follow-up done:** unlaunchable suggestion chips are now filtered
  at the launcher VM choke-point and the old `TimeOfDaySuggestionProvider` AOSP package table was replaced
  with resolved universal anchors; SM-A325F smoke passed.
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
  - **Phase 9 — hardening:** ✅ DONE 2026-07-04 for the available matrix. **Y1 startup + Y2 release build
    are done.**
    Final release on SM-A325F: warm median ~102ms, cold median 766ms (drop-first protocol), first home
    frame has no Loading spinner; R8/resource shrink enabled; Baseline Profile generated/shipped
    (`app/src/main/baseline-prof.txt`, 18,862 lines); release smoke-clean; `testDebugUnitTest` +
    `assembleDebug` + `:app:assembleRelease` green. **Y3 done 2026-07-04:** VM filters suggestion
    chips to installed launchable packages/known routes, `SuggestionEngineImpl` filters unsupported
    actionIds before ranking/cache, and time-of-day fallback now resolves Alarm/Camera anchors through
    `PackageManager` instead of hardcoded AOSP packages; full Gradle verification green; SM-A325F smoke
    showed `A101` usage suggestion and resolved Samsung Clock (`Часы`) launching successfully, no
    AndroidRuntime crash. **Y4 done 2026-07-04:** VM-level regression coverage broadened for
    suggestions first-paint/supersede, `deriveFavorites`, `onSuggestionClicked` routing,
    usage-history/AI-suggestions gates, and Settings VM no-op/navigation paths; production code unchanged.
    **Y5 done 2026-07-04:** logging audit stripped the only raw-route/exception-bearing log surface
    (`AppNavHost` fallback, which could carry assistant prompt text), added a source guard for payload-free
    nav logging, confirmed no crash-report SDK/surface is wired, and pinned full assistant `AiError`
    retryable/provider-CTA classification; full Gradle verification green. **Y6 done 2026-07-04:**
    validation-first pass with no production changes. Available runtime matrix was SM-A325F / Android 13
    only; debug APK installed via `adb install -r --no-streaming`; launch/home/offline core/settings
    persistence/set-as-default ROLE_HOME/voice education all passed; trim-memory BACKGROUND/COMPLETE
    released `OnnxTextEmbedder` + `OnnxIntentClassifier` through `SessionLifecycle`; no `files/models`
    directory, so local AI paths stayed inert/gated-off; device is HIGH_END by current classifier
    (~5.8GB RAM / 8 cores), so LOW_END hardware plus Android 9/11/14 remain residual until a matrix exists.
    **Y7 done 2026-07-04:** `saveProvider` now reflects a newly saved non-blank API key in `keySet`
    immediately without exposing the key, `RateLimited` text is regression-pinned as `retry`, and
    relaunch/re-entry while `LauncherActivity` is alive resets nested nav (drawer/settings/etc.) back to
    launcher home via `singleTop` + `onNewIntent` + `AppNavHost` home reset. SM-A325F debug smoke passed:
    drawer -> `am start -W -n com.sidr.launcher/.LauncherActivity` delivered the new intent to the running
    top instance and returned to home; `AndroidRuntime:E` empty. Model-gated OQ#1–#4 remain a separate
    track, out of the ship gate.
  - **Phase 8 — accessibility automation:** ⛔ OPTIONAL / DEFERRED (post-MVP); not a ship blocker.

## Open questions gating the residual track

- **OQ#1 / OQ#2** — real NLU model (`intent.onnx`, pruned multilingual `vocab.txt`) + its host and
  pinned SHA-256. Until closed, `ModelDownloadConfig.INTENT_NLU_PENDING` is an inert seam.
- **OQ#3** — embedding model + tokenizer + host/hash (gates Block V). `EMBEDDING_PENDING` inert seam.
- **OQ#4** — on-device STT availability across the target device matrix (gates Block T acceptance).

## Device acceptance (SM-A325F / Android 13) — see ADR 2026-07-02 (Rounds 2 + 3)

**Round 3 (2026-07-02, BYOK key in-app) retired the last big AI blocker: assistant real streaming is
now PASS end-to-end.** Phase 9 later retired startup perf and Y7 cosmetic findings; residual debt is voice
intermediate states, boot-warmup, unavailable Android 9/11/14 + real LOW_END matrix, and the model track.
See [`device-acceptance-brief.md`](device-acceptance-brief.md).

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
- ✅ **Startup perf — Phase 9 Y1/Y2 PASS.** Final release on SM-A325F: warm median ~102ms and no spinner;
  cold median 766ms after dropping the first run, in the ~500-800ms release band. Perfetto cold trace
  (`TotalTime` 760ms) showed the remaining cost concentrated in normal process/app first-frame work
  (`bindApplication` ~177ms, `activityStart` ~76ms, `performCreate` ~44ms, first traversal/doFrame
  ~376ms), with the launcher-owned Loading state removed from the first frame.
- ✅ **C.1 cosmetic findings retired by Phase 9 Y7:** `keySet` now flips true immediately after a successful
  non-blank key save, and `RateLimited` text is pinned as `Rate limited. Please wait and retry.`.
- ⚠️ **Voice recognizer intermediate states** — `Ready` / `Partial` not evidenced (OQ#4); C.2 not a full
  recognizer PASS.
- ⏭️ **C.5 boot warmup** — `RECEIVE_BOOT_COMPLETED` re-enqueue after a reboot not yet exercised.
- ⏭️ `OnnxIntentClassifierInstrumentedTest` assumption-skipped (no bundled model) — no `intent.onnx` /
  `vocab.txt` in repo or app sandbox → **PENDING-MODEL**; Block-P P5 `< 150ms` inference + Block-Q real
  provisioning still model-blocked (OQ#1/#2).
- ⏭️ Block-V embedder `< 150ms` + memory co-residency — pending (OQ#3).

## Not claimed done

The `<400ms` cold-start number remains aspirational (final release median 766ms, ship-band PASS).
Still not claimed: Android 9/11/14 and real LOW_END hardware validation; OQ#1–OQ#4 real models/vocab/
embedder + on-device NLU acceptance; voice `Ready`/`Partial` intermediate states (OQ#4); boot-warmup
after reboot (C.5); **`Migration3To4` has never been executed** — its `MigrationTest` cases are
`androidTest` and no device has run them, so schema v4 is proven only against the generated `4.json`.
*(Real assistant streaming against a live provider — done Round 3.)*

## Source of truth

- Session digest + hard rules: `CLAUDE.md`
- Decisions log (latest: ADR 2026-07-11 — S2-1 Learned Resolutions device accepted + closed): `ai-context/decisions.md`
- Architecture (in sync): `docs/architecture.md` · Roadmap: `docs/roadmap.md`
- Active/last plan: `docs/superpowers/plans/2026-07-06-learned-resolutions.md` (S2-1, CLOSED)
