# A0.5 — Second Consumer of the Portable Core (Design Spec)

> **Status: PROPOSED (2026-08-23).** A0.5 turns "portable core" from a property of the build
> configuration into a measured fact, by giving the `jvm()` target its first **product** consumer: a
> headless console harness that runs one goal through `goal → plan → gate → tool → observe → tool →
> result → trace` with no Android anywhere in its graph.
>
> **The block's deliverable is knowledge, not machinery.** Master Plan §3.1a lists four questions the
> block is *obliged to answer and record*, negative answers included. The harness exists to produce
> evidence for those answers. Where the core turns out to be launcher-shaped, this block **records the
> finding with an address** and does not fix it — see §2 (owner decision, Approach A) and §11.
>
> **Amendment 2026-08-23 (evening) — re-anchored to `a7f4755` after the A0 review round.** This spec
> was written against `7442da4`. A cross-cutting review of the closed A0 block then found nine defects
> and fixed eight of them, **in `commonMain`** — the very code this consumer is the second user of. ADR
> «2026-08-23 — Сквозное ревью блока A0». Four consequences for this block, all of them making it
> easier rather than harder:
>
> - **The engine repairs are consumer-neutral, and this block is where that gets measured.** None of
>   the eight fixes added a platform branch, an `expect`/`actual`, or a `java.*` import; `:consumer:jvm`
>   inherits every one of them without writing a line. That is itself evidence for §11.2, and it is
>   reported there rather than assumed.
> - **The consent gate now reads `maxOf(PlanStep.risk, registry.risk)`, not the plan alone** (finding
>   F2). For this consumer the two always agree — `FilePlanner` copies risk out of `SandboxToolSource`,
>   exactly as `TemplatePlanner` copies it out of the projection — so the happy path is unchanged and
>   §6.2 still takes `checkpointFor`'s first branch. It sharpens §11.3's answer: see there.
> - **`StartAgentSessionUseCase` now validates a plan before persisting it** (finding F10). The JVM
>   consumer gets that free, and it is the first time the seam is exercised by a `Planner` the core
>   does not own. §6.3's miss path is **unaffected** — that failure is a *resolution* failure at run
>   time, not a shape defect at plan time.
> - **The mid-call restore seam is now the cheapest thing this consumer can prove that Android could
>   not.** Finding F1 was that the engine's "the process died during a tool call" signal was unreadable
>   on the only path that produces it, and Android could reach that window only with `pm disable-user`
>   plus an on-device poll (157–170 ms warm). On JVM it is a function call. §9 and §16 now require it.
>
> **Governing sources:** `docs/governing/sidr-agentic-master-plan-v1.0.md` §2 criterion 10, §3.1a
> (block A0.5), §3.4 (the `:data:ai-local` rule), §3.6 row `B1`, §4 (agentic-block DoD), §5
> (change-control), §6 M-A1, §7 (no SDK); ADR «2026-08-21 — Развилка агентного трека» and ADR
> «2026-08-22 — Этап 4 (A0)» in `ai-context/decisions.md`;
> `docs/superpowers/plans/2026-08-21-a05-second-consumer.md` (the block brief this spec supersedes on
> three points of fact — §3); `docs/superpowers/plans/2026-08-18-agentic-track-restart.md` §HANDOFF;
> `CLAUDE.md` Hard rules.
>
> **Relationship to the brief.** The brief (2026-08-21) established that the block exists, why, and
> which forks to raise. This spec supersedes it wherever fact contradicts it — three of its load-bearing
> claims were verified against the tree and two do not hold as written (§3). It also carries two forks
> the brief does not contain (`F5`, and the goal-shape half of `F4`), both raised before any code per
> the track's forks-before-code rule.
>
> **Hard rules this block obeys.** `:domain` stays stdlib + coroutines and compiles for both KMP
> targets. No `feature → feature` edge. Repository/use-case ops return `OperationResult`. `ActionIds`'
> seven values are untouched. The A1 fork is **not** decided — both of its branches must remain equally
> cheap when this block closes. No Android code, no desktop UI, no distribution, no public SDK, no
> model planner.

---

## 1. Goal

Give the portable engine a **second consumer that is not a test**, and use the friction of building it
to answer, in evidence, what the core forces a non-Android consumer to say.

One goal, three tool calls, two step-to-step bindings, one risk transition that stops the loop for
consent, a session that survives process death — on plain JVM, over the **same** `domain/agent`,
`domain/tool` and `domain/trace` contracts that ship on the phone.

Everything that does not serve that sentence is a non-goal (§13).

## 2. Owner-resolved forks

Seven decisions were taken by the owner at the block kickoff, before any code, per the track's
forks-before-code rule. `F1`–`F4` come from the brief; `F5` and the two design decisions were raised
during the reading pass, matching the precedent of Этап 4.0's `F5` and A0's `F6` — both of which
surfaced while reading, not while writing code.

| # | Fork | Decision |
|---|---|---|
| F1 | Where the second consumer lives | **A Gradle module in this repository.** A separate repository immediately requires a publishable artifact, which is the SDK Master Plan §7 forbids. |
| F2 | Session store on JVM — in-memory or file | **File-based.** "The session survives process death" is an M-A1 exit criterion, and `recordConsentIfPending` is a compare-and-set contract that exists because Room can express `UPDATE … WHERE`. Only a real store tests whether that port is Room-shaped. |
| F3 | Tools on JVM — stubs, real local, or an MCP client | **Real local tools, no MCP client.** Choosing tool sources is A1′'s work (§3.6 `B2`/`B3`/`B4`); an MCP client here pre-empts it and drags a transport into a block whose subject is that the core has none. The arity question is answerable without it — see §11.1. Stubs prove nothing. |
| F4 | Planner on JVM — reuse `TemplatePlanner`, or a model planner | **The consumer brings its own deterministic `Planner`, and `GoalShape` gains one neutral value.** Reuse is impossible in substance: `TemplatePlanner` hard-codes `ToolIds.LAUNCH_APP`/`PLAY_STORE_SEARCH`, so reusing it would force the JVM consumer to name its tools after Android families. A model planner is A4′. |
| F5 | Does the second `ToolExecutor` fall under the same boundary as the first | **Extend the existing guards to the new module.** The growth rule (Master Plan §4) puts boundaries on the first slice, not behind the second consumer. |
| — | Which local tools | **A sandboxed filesystem triple** — see §5. Chosen because it is the only candidate that exercises all three arities inside one plan and makes `DANGEROUS`/`DURABLE` producible for the first time. |
| — | How much the block changes in the core | **Approach A — record, don't fix.** The only `commonMain` change is `F4`'s single `GoalShape` value. Every other launcher-shaped contract the consumer collides with is demonstrated by a passing test and recorded with an address. Rationale: Master Plan §3.4 forbids building against a consumer that has not arrived, and A1′'s tool sources have not arrived — opening the core's vocabularies now would build it *for* them, and would softly decide the A1 fork by documentation. |

**Two options considered and rejected** for the last row, recorded so the reasoning survives: **(B)**
converting `ObservedFact` to a value class over `String` on the `ActionId`/`ToolId` precedent —
mechanically safe (§11.2) but it lets the Android string seam receive a fact it has no `sidrString`
for, impossible with one source and real from A1′; **(C)** B plus lifting `CommandFailure` out of
`ToolResult` and introducing `expect`/`actual` for the `java.*` residue — the expensive half fixes
`prayer`/`intent`, which this consumer never touches, and `expect`/`actual` on engine files trips
finding `D9` and obliges the `domain/src` Gradle-input widening in the same commit.

## 3. Verification of the brief's premises — what actually holds

The brief was written 2026-08-21; block A0 has since added `tool/`, `agent/` and `trace/` to
`:domain`. Every load-bearing claim was re-checked against the tree at `7442da4`, and re-confirmed at
`a7f4755` after the A0 review round. **Two of three do not hold as written**, and the corrections
change what the block is for.

**3.1 — "The `jvm()` target has zero consumers; all twelve dependants of `:domain` are Android." —
FALSE as stated.** Twelve modules depend on `:domain`. Eleven are `com.android.library` or
`com.android.application`. The twelfth, `:core:testing`, is a plain `kotlin.jvm` module, and Gradle
resolution confirms it consumes `:domain`'s **`jvmApiElements`** variant
(`org.jetbrains.kotlin.platform.type = jvm`). `:domain:jvmTest` is a second such consumer — 416 tests
(405 when this was first checked at `7442da4`).

*What is true:* the `jvm()` target has **zero product consumers**. Nothing outside test and fixture
code runs a goal.

*Why the correction matters:* "can the engine compile and run without Android" is already answered
**yes**, daily, by 416 tests. The open question is not the core's portability in the abstract but what
a **real adapter** costs and what the core forces it to say. This spec is written against that
question, not the brief's.

**3.2 — "Eight `java.*` imports in `commonMain`." — TRUE, exactly eight, and misdirected.** Unchanged
since 2026-08-21: `java.util.Locale` ×3 (`input/UniversalInputRouter`, `intent/CommandNormalizer`,
`intent/IntentActionResolver`) and `java.time.*` ×5 (`prayer/GetPrayerContextUseCase`,
`prayer/PrayerCalculator`, `prayer/PrayerModels`).

*The new fact the brief could not know:* A0 added roughly 1085 lines to `commonMain` (`agent/` 731,
`tool/` 320, `trace/` 34) and **zero** `java.*` imports — and the 2026-08-23 repair round added none
either, so the count is still exactly eight and all eight still sit outside the engine. All eight sit **outside** the engine. The
"core is JVM-locked" framing is therefore not the portability obstacle for this block: the engine is
already `java.*`-free, and what is JVM-locked is prayer plus the intent normalizer, which a PC consumer
never touches. This is why Approach C's `expect`/`actual` half was rejected — it would fix code the
second consumer does not use.

**3.3 — "Zero `expect`/`actual`." — TRUE.** Zero. `:domain` also has exactly two source sets on disk,
`commonMain` and `jvmTest`: no `androidMain`, no `jvmMain`, no `commonTest`.

**3.4 — What the brief missed, and what this block is actually about.** The engine's *machinery*
(session, cursor, budget, validator, consent checkpoint, trace) is genuinely portable. The engine's
*vocabulary* is launcher-shaped and lives in the same `commonMain`:

| Contract | Shape today | Consequence for a second consumer |
|---|---|---|
| `GoalShape` | one value, `AppNotInstalled(query)`; `AgentGoal.shape` is **non-nullable** | a JVM goal cannot be phrased at all without either a costume or one new value (`F4`) |
| `TemplatePlanner` | hard-codes `ToolIds.LAUNCH_APP` / `PLAY_STORE_SEARCH` | reuse forces the consumer to name its tools after Android families |
| `ObservedFact` | closed enum, 2 values, both Android-app-shaped | "the file is not there" is **unsayable** (§6.3) |
| `CommandFailure` | closed, 5 values, 4 launcher-shaped; imported by `domain/tool` | a PC tool can only ever report `Generic` |
| `StepRationale` | 2 values, one is `APP_NOT_INSTALLED_FALLBACK` | dead vocabulary for any non-launcher consumer |
| `ArgType` | one value, `STRING` | no MCP-shaped schema is expressible (§11.1) |

Two contracts came out the **other** way, and that is equally a finding: `ToolId` is a value class over
`String` specifically so "adding one requires no change to a central enum" (its own KDoc), and
`AgentSessionIdFactory` is a port specifically because `java.util.UUID` is not in `commonMain`. Both
survive contact with a second consumer without a single edit. §11.2 turns that contrast into the
block's recommendation to A1′.

## 4. The module

`:consumer:jvm`, at `consumer/jvm`. Added to `settings.gradle.kts`; **nothing depends on it**, and it
is not in `:app`'s graph.

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
```

`:domain` is the **only** project dependency. No `core/*`, no `data/*`, no `feature/*`, nothing
Android. The module is proved to consume the `jvm()` variant the same way §3.1 proved `:core:testing`
does — by resolution, not by assumption (§14).

**Adding a module to `settings.gradle.kts` is gate-safe here, and that was checked rather than
assumed.** Three guard-the-guard tests parse `settings.gradle.kts` for every included module:
`LocaleCompletenessGuardTest.module_prefixes_cover_every_module_that_ships_strings`,
`HardcodedUiTextGuardTest.scoped_roots_cover_every_ui_module` and
`DomainIdentifierLeakGuardTest.scoped_roots_cover_every_ui_module`. Each gates on the module actually
containing `@Composable` under `src/main` or shipping `res/values/strings*.xml`. A JVM console module
has neither, so all three pass. §13 states what that means for localization.

## 5. The tool set — three arities in one plan

A **sandbox root**: a directory the harness creates and owns. Every tool resolves its paths against it
and **refuses** any path that escapes it, checked after canonicalization so `..` and symlinks cannot
walk out. That refusal is a `ToolResult.Failed`, not an exception.

| Tool | Args | Risk / durability | Declared output |
|---|---|---|---|
| `workspace_info` | **none** | `SAFE` / `TRANSIENT` | `root` |
| `find_file` | `query`, `root` | `SAFE` / `TRANSIENT` | `resolved_path` |
| `delete_file` | `path` | **`DANGEROUS`** / **`DURABLE`** | — |

Each covers one leg of Master Plan §3.1a question 1: **zero arity** (`workspace_info`), **typed
arguments** (`find_file`, two of them, one literal and one bound), and a fully-bound single argument
(`delete_file`). None is decoration — every step's output is consumed by the next.

**Tool ids live in the consumer, not in `commonMain`.** `ToolIds` in `domain/tool` keeps exactly its
two A0 values; `SandboxToolIds` in `:consumer:jvm` declares its own three as
`ToolId("workspace_info")` and so on. Adding them to the shared object would be precisely the
per-consumer taxonomy this block exists to warn about, and `ToolId`'s value-class design already makes
it unnecessary.

**`find_file` emits `resolved_path` on every branch, blank when nothing matched.** `ToolDescriptor`'s
KDoc requires that a tool declaring an output returns it on *every* result — "outputs are a property of
the tool, not of the branch it happened to take, and a schema that only sometimes holds is not a
schema" — and `SystemIntentToolContractTest` enforces exactly this for the Android adapter. A blank
value is **not** a loophole: `InvocationValidator.resolve` applies `.takeIf { it.isNotBlank() }`, so a
blank binding fails closed as `UNRESOLVED_ARG_SOURCE` and the step does not run. Contract honoured,
fail-closed preserved. §6.3 is about what that costs in the trace.

## 6. The proof goal and the plan

**Goal text:** a command naming something to remove, e.g. `remove stale.lock`. `FilePlanner` takes the
target as the text after a single recognised verb and **deliberately does nothing more** — not a
parser, not a matcher, not an understanding layer. Anything richer would be building the consumer's
own FastPath, which is neither this block's subject nor in its budget; what matters is only that the
engine **below** the `Planner` port is untouched. A goal it cannot read returns `NoPlan`.

**6.1 — The plan.** Three steps, two bindings, one risk transition:

| # | Invocation | Risk | Precondition | Rationale |
|---|---|---|---|---|
| 0 | `workspace_info()` | `SAFE` | `None` | `GOAL_DIRECT` |
| 1 | `find_file(query = Literal(target), root = FromStep(0, "root"))` | `SAFE` | `None` | `GOAL_DIRECT` |
| 2 | `delete_file(path = FromStep(1, "resolved_path"))` | `DANGEROUS` | `None` | `GOAL_DIRECT` |

`GOAL_DIRECT` is honest for all three: each step directly serves the goal. `StepRationale`'s other
value is `APP_NOT_INSTALLED_FALLBACK`, which no non-launcher consumer can ever use — recorded in §11.2,
not worked around.

**6.2 — The hit path.** Steps 0 and 1 run and produce outputs. At step 2 `checkpointFor` takes its
first branch — since 2026-08-23 the condition is `maxOf(PlanStep.risk, registry.risk) >= CONFIRM`, and
for this consumer the two are the same value because `FilePlanner` copies risk out of the registry — and
returns `ConsentCheckpoint(2, RISK_LEVEL)`; the session goes `AwaitingConsent` **before** `ToolExecutor`
is reached. On consent the file is deleted and the session
ends `Completed`. On refusal it ends `Cancelled` and nothing is deleted.

**6.3 — The miss path, and the block's sharpest finding.** When nothing matches, `find_file` emits a
blank `resolved_path`. Step 2's binding fails closed: `StepRejected(2, UNRESOLVED_ARG_SOURCE)`, session
ends **`Failed`**.

That is correct, fail-closed engine behaviour and it is also the finding. On Android the *same shape of
reality* — "the thing you asked about is not there" — has a name (`ObservedFact.APP_NOT_INSTALLED`),
becomes a satisfied precondition on the next step, and the session ends **`Completed`**. On the JVM
consumer it has no name, surfaces as an unbindable argument, and ends **`Failed`**. Same reality, two
different session outcomes and two different traces, purely because the observation vocabulary is a
closed two-value Android enum.

**Plan-time validation does not touch this** (A0 finding F10, 2026-08-23). `InvocationValidator.validate`
is a *shape* check: `FromStep(1, "resolved_path")` names a key `find_file` really does declare, so the
plan is valid when it is written and persisted. What fails is `resolve`, at run time, when the value
turns out blank. The two halves of the validator staying separate is exactly what makes this finding
expressible at all.

**This is a trace-fidelity gap, not a safety gap** — the step correctly does not run either way. It is
recorded against A1′ (§11.2) and pinned by a test that asserts both outcomes side by side, so the
divergence is a fact in CI rather than a paragraph in a document.

> **Incomplete as written — sharpened 2026-08-26 by the block's ADR (Ruling P18).** The consent gate
> fires **before** argument resolution: `AgentExecutor.prepare` runs `InvocationValidator.validate`
> (shape — passes), then `checkpointFor` (consent), then `InvocationValidator.resolve` (value — fails
> on the blank). So on the miss path the trace reads `ConsentRequested(2, RISK_LEVEL)` followed by
> `StepRejected(2, UNRESOLVED_ARG_SOURCE)`: the user is asked to approve deleting a file that was
> never found, and that approval is what unblocks the discovery that nothing can bind. The order is
> deliberate, documented at the binding site, and fail-**safe** — the user is stopped more than
> necessary, never less. It makes the asymmetry **sharper**: on Android the same "yes" leads to an act
> that happens; here it leads nowhere.

> **Owner instruction, 2026-08-23 — the asymmetry STAYS. Unifying the two outcomes is NOT a task in
> this block, and no implementer may take it as one.** §6.3 describes a recorded architectural finding,
> not a defect awaiting repair. Making the JVM consumer end `Completed` here would require naming the
> fact — a new `ObservedFact` value in `commonMain` — which is exactly the Approach B this block
> rejected in §2 and exactly the "fix it now" reflex Master Plan §3.4 warns against. The finding is
> owned by **A1′**.
>
> This is held **mechanically, not by this paragraph.** The divergence test asserts both outcomes, so a
> "fix" that unifies them turns it **RED**; its name and KDoc say the divergence is intended, so
> deleting or relaxing it is a conscious act against a named owner decision rather than a tidy-up. A
> guard also pins `ObservedFact` at its two values and `GoalShape` at its two, so growing either fails
> closed — which incidentally gives Master Plan §3.6 `B1` its first test.

## 7. What changes in `commonMain` — exactly two edits

**7.1 — `GoalShape` gains one value.**

```kotlin
sealed interface GoalShape {
    data class AppNotInstalled(val query: String) : GoalShape
    /** No known shape was recognised; the planner receives the raw goal text. */
    data class Free(val text: String) : GoalShape
}
```

**Why `Free` specifically, and why this honours `B1` rather than merely excepting it.** Master Plan
§3.6 `B1` holds `GoalShape` at one value, and its stated reason is the failure mode, not the count:
"каждое добавленное значение — ветка планировщика … на третьем это таксономия под непришедшего
потребителя". `Free` is the one addition that **cannot start a taxonomy**: it is the *absence* of a
recognised shape, so there can never be a second `Free`. It is also the shape A4′'s model planner needs
regardless — a model plans from text. §12 records this as `B1`'s answer.

**7.2 — `TemplatePlanner` gains one arm.** `is GoalShape.Free -> PlanningResult.NoPlan`. The Android
planner does not plan free-text goals; that is A4′'s job. The `when` stays exhaustive, which is the
property `GoalShape`'s KDoc asks for.

**Nothing else in `commonMain` moves.** No new `ObservedFact`, no new `CommandFailure`, no new
`ToolIds`, no new `StepRationale`, no `ArgType`, no `expect`/`actual`, no new source set. That last one
matters: creating `jvmMain` or `androidMain` in `:domain` would oblige declaring `domain/src` as a
Gradle input in the same commit (findings `D3`/`D8`) and would make `expect`/`actual` file pairs read
as a duplicate-scan bug in the call-site guard (finding `D9`). Approach A avoids both by construction.

## 8. Persistence — `JvmAgentSessionStore`

A single JSON file under the sandbox root, holding at most one session — the same "no history at rest"
contract `AgentSessionStore`'s KDoc states, so a terminal state deletes the file.

**Own DTOs; domain types stay unannotated.** `:domain` is stdlib + coroutines, so no `@Serializable`
may touch it. The consumer defines its own serializable mirror of the session graph and maps
domain ↔ DTO in both directions, exactly as `AgentSessionMappers` does on the Android side.

*That is itself a measured answer:* **both** consumers had to hand-write a full mapper, because the
core is deliberately serialization-free. The cost of a second consumer includes one mapper per
consumer, and this block reports the number rather than estimating it (§14).

**`recordConsentIfPending` is implemented as a real compare-and-set** — a `Mutex` for in-process
exclusion plus a `FileLock` over the session file for cross-process exclusion, wrapping the
read-modify-write. It returns `true` **only if** the step was still awaiting a decision. Whole-object
writes are avoided for the reason the port's KDoc gives: that pattern already cost this project the
`autoHideNavBar` bug (DS-11).

This is the single most informative part of the block for question 2. The method exists because Room
can express `UPDATE … WHERE state = pending` in one statement. Whether a file store can honour the same
contract honestly — rather than by weakening it to a whole-object write — is what decides whether
`AgentSessionStore` is a portable port or a Room shape wearing an interface. §11.2 records the verdict
that the implementation produces.

Pinned by a test that fires two concurrent `recordConsentIfPending(id, 2, true)` calls and asserts
**exactly one** returns `true` — the "double confirmation must not execute a step twice" property,
proved on the second consumer.

**One decision is one trace event, and the consumer inherits that** (A0 finding F4, 2026-08-23).
`ResolveConsentUseCase` no longer writes `ConsentResolved` — `AgentExecutor.prepare` is the single
writer, on both the granted and the refused branch. Before the fix a refusal produced the event twice,
so any consumer printing or persisting the trace would have printed it twice. The store's DTO mapping
is unaffected; what changes is how many events reach it.

## 9. The console driver

`main()`: take the goal on the command line, build `AgentGoal(text, GoalShape.Free(text))`, wire
`FilePlanner`, `SandboxToolSource`, `SandboxToolExecutor`, `JvmAgentSessionStore` and
`JvmAgentSessionIdFactory` (which is where `java.util.UUID` lives — in the consumer, which is the whole
point of that port), then drive `StartAgentSessionUseCase` → `RunAgentSessionUseCase`, print the trace
event by event, and prompt on stdin at `AwaitingConsent` before calling `ResolveConsentUseCase`.

**The harness never calls `ToolExecutor.invoke` itself.** It drives `AgentExecutor`, which owns the one
call site below the consent checkpoint. §10 holds that mechanically rather than by intention.

**Process-death demonstration, in two shapes.** The first is the cheap one: the harness exits at
`AwaitingConsent`, re-running it finds the persisted session, presents it as `Paused` via
`pausedForRestore()`, and continues only on an explicit choice — never a silent resume. That is M-A1's
"session survives process death", on the second consumer.

**The second shape is the one this consumer can prove and Android could not**, and it is required
rather than optional (added 2026-08-23). A process that dies **during a tool call** leaves
`ToolInvoked(i)` with no `ToolObserved(i)` — the signal the engine's `prepare`/`perform` split exists
to record. A0 finding F1 was that this signal was unreadable on the only path that produces it: the
restore transitions write `SessionPaused` and `SessionResumed` on top of the pending event, so a
predicate keyed on the trace *tail* missed it, the step was re-cleared, re-traced and re-run, and a
step whose consent had already been granted ran twice. Android could reach that window only with
`pm disable-user` plus an on-device `force-stop` poll — 157–170 ms on a warm process. **Here it is a
function call:** persist after `prepare`, drop the harness, start a new one. The consumer therefore
holds a core invariant that the first consumer's own acceptance could only approximate, which is a
concrete answer to "what is a second consumer *for*" beyond portability.

## 10. Guards — the boundary, extended (`F5`)

**`ToolExecutorCallSiteGuardTest`.** `consumer/jvm/src/main/kotlin` joins `productionRoots`. Declared
holders go from three files to four: `AgentExecutor.kt`, `AgentProvidesModule.kt`,
`SystemIntentToolExecutor.kt`, `SandboxToolExecutor.kt`. **The call-site count stays exactly one** —
`AgentExecutor.kt` — which is the property worth holding: a second consumer must not become a second
way to reach the world. The guard's KDoc gains the new root and keeps its existing honesty about what
the scan does **not** hold (position, reflection, roots outside its list).

**`app/build.gradle.kts`** gains one `inputs.dir(rootProject.file("consumer/jvm/src/main/kotlin"))`
beside the three already there, or the widened guard silently would not re-run — the `UP-TO-DATE` trap
`§HANDOFF` documents. This is a single named directory, not the repo-wide widening findings
`D1`/`D3`/`D8` describe as an owner-level build trade-off.

**`AgentVocabularyGuardTest` is deliberately NOT extended.** It scans `domain/agent`, `domain/tool`
and — since 2026-08-23 (A0 finding F7) — `domain/trace`, because the *engine* is what must stay free of
action vocabulary and transports, and the contract table has always called those three the portable
engine. A consumer may name
whatever it likes; that is what being a consumer means. Extending it would misstate what the guard
protects.

**Both changes are proved by mutation, not by a green run.** Per the standing rule — a green run of a
new guard proves nothing — the plan must plant, and log RED for: (a) a second `toolExecutor.invoke(` in
`:consumer:jvm`; (b) a fourth `ToolExecutor` holder; (c) the new `inputs.dir` line removed together
with a **bytecode-identical** mutation, per the `m7` series lesson: any mutation that shifts compiled
output re-triggers the task for an unrelated reason and reports a false pass.

## 11. The four questions — how each gets answered

Master Plan §3.1a obliges the block to record all four, negative answers included. Some are already
answerable from reading; the block's job is to state them with evidence, not to discover them
theatrically.

**11.1 — Does `ToolDescriptor` stretch to three arities? Partly — and the negative half is already
determinable.** Zero arity is expressible (`argSchema = emptyList()`, demonstrated by
`workspace_info`). Typed arguments are expressible only in the weak sense that every argument is a
`STRING`: `ArgType` has exactly one value, so "typed" today means "named". A **rich MCP schema is not
expressible** — nested objects, arrays, enums and numbers have no representation in
`List<ActionArg>`, and `ToolOutput` is `Map<String, String>` on the other end. Recorded as a negative
answer with address A1′. This is why an MCP client was rejected in `F3`: it would confirm a
determinable answer at the cost of a transport dependency and a decision that belongs to A1′.

**11.2 — What in the core is secretly Android-shaped?** The answer is *not* the eight `java.*` imports
(§3.2). It is the vocabulary table of §3.4, and the block reports it as a contrast rather than a list:

- **Portable by design, survived unchanged:** `ToolId` (value class over `String`, so a consumer
  declares its own ids with no core edit); `AgentSessionIdFactory` (a port precisely because `UUID` is
  not in `commonMain`); the whole execution machinery — `AgentSession`, cursor, `RuntimeBudget`,
  `InvocationValidator`, `ConsentCheckpoint`, `ExecutionTrace`.

  **"Survived unchanged" is about the *shape*, and the 2026-08-23 repair round is the sharper evidence
  for it.** Eight defects were fixed inside exactly this machinery — the mid-step predicate, the risk
  the gate acts on, the single writer of `ConsentResolved`, plan-time validation, the deletion of an
  unreadable session row — and **not one of them needed a platform branch, an `expect`/`actual`, or a
  `java.*` import.** `:consumer:jvm` inherits every fix by existing. A core that can be repaired in
  eight places without either consumer noticing is portable in the way that matters; a core that is
  merely *compilable* for two targets is not the same claim. The block reports this as a measured
  fact rather than as a prediction.
- **Launcher-shaped, cost measured:** `GoalShape` (§7.1 — the one edit this block makes);
  `ObservedFact` (§6.3 — a whole class of reality unsayable, with a user-visible `Failed`/`Completed`
  divergence); `CommandFailure`; `StepRationale`; `ArgType` (§11.1); `TemplatePlanner`.
- **`AgentSessionStore` — verdict produced by §8, not predicted here.** The design predicts it is
  portable, because compare-and-set is a semantic contract and not a SQL feature. The spec deliberately
  does not assert the outcome in advance; the implementation reports it.

**The recommendation this contrast yields, which decides nothing:** the two contracts that survived are
the two built as **open value types**, and the six that did not are all **closed sums**.
[*Corrected 2026-08-26 by the block's ADR: the six are indeed all closed sums, and `ToolId` is indeed
an open value class — but the second survivor, `AgentSessionIdFactory`, is a **port**, not a value
type. It demonstrates a different mechanism for the same end: push the unportable thing (`UUID`)
across a boundary rather than name it in the core.*] A1′ may take
that either way — a parallel vocabulary or an evolved `ActionCatalog` — and this block deliberately
does not choose. It records which shape survived contact with a second consumer.

**11.3 — Is risk a property of the platform rather than the core? Renamed with an address, not
closed.** The recorded finding is:

> `DURABLE_EFFECT` is unreachable for any tool at `CONFIRM` or above. Not a property of either consumer
> but of `checkpointFor`'s branch order in `AgentExecutor` (`commonMain`), where `step.risk >= CONFIRM`
> is tested first: the reason fires only for a tool that is `SAFE`, gate-free and `DURABLE`, so the
> `DURABLE` marking is inert exactly where risk is highest. **The user is still stopped** —
> `RISK_LEVEL` takes the branch — so this is a trace-fidelity gap, not a safety hole: the stop is
> attributed to risk rather than to durability. → A4′.
>
> This consumer deliberately exercises the existing three-level scale rather than extending it.
> Inserting a level below `CONFIRM` belongs to A1′, and it is the same edit that revives `RISK_RAISED`
> (`D11`).
>
> Risk **assignment** is adapter-owned — `DefaultActionCatalog` assigns all seven literally,
> `SystemIntentToolSource` copies them, `SandboxToolSource` assigns its own — while the **scale** is
> core-owned. Two adapters over one scale, disagreeing about what a comparable act is worth
> (`CONFIRM` for a store page, `DANGEROUS` for a file delete), is the evidence. Question 3 is therefore
> **renamed with an address (A1′)**, not closed.
>
> **A third owner surfaced on 2026-08-23, and it is neither adapter: the persisted plan.** A0's review
> (finding F2) found the gate reading `risk` off the stored `PlanStep` while reading `permissionGate`
> and `durability` off the live registry, so a plan written when a tool was `SAFE` ran it with **no**
> `ConsentRequested` after a build raised it to `CONFIRM`. The gate now acts on
> `maxOf(plan, registry)`. That refines this question's answer rather than changing its address: risk
> has *two* sources with different lifetimes — a snapshot taken when the plan was written and the
> declaration in force when it runs — and the core resolves the disagreement conservatively instead of
> picking a winner. **This consumer is where that stops being theoretical**: A1′ federates sources, and
> two sources mean two lifetimes per plan. `SandboxToolSource` and `SystemIntentToolSource` agreeing
> with their planners today is what makes the property checkable at all; nothing guarantees a third
> adapter will.

The "user is still stopped" sentence is load-bearing and must survive editing: without it the finding
reads as though durable actions slip past consent, which is false and would send a future session
chasing a bug that does not exist.

**11.4 — How general is the execution model? Confirmed adapter, not core — and cheaply.** "Launching an
app throws you out of the process" has no analogue here: a JVM tool call returns. The consumer needs no
foreground service, no assistant role, and none of the four execution layers §3.6 `B6` assigns to A4′.
The block records that those four layers are an **Android adapter concern**, which is what A4′ needed
to learn before building them into the core. Nothing in `domain/agent` had to change to accommodate a
consumer with a different process model — that is the positive half of the answer, and it is the
strongest single piece of evidence that the *machinery* is portable even where the *vocabulary* is not.

## 12. §3.6 backlog rows addressed to this block

Master Plan §4 DoD requires every row with this block's address to be answered explicitly; a silent
skip is a spec defect. Exactly one row addresses A0.5.

| Row | Address | Answer |
|---|---|---|
| `B1` — do not grow `GoalShape` past one value | "действует сейчас" → resolved in A4′ | **Included, with one value and a recorded argument.** §7.1 adds `GoalShape.Free(text)`. `B1`'s stated failure mode is a taxonomy built for an absent consumer; `Free` is the absence of a shape and cannot have a second instance, so the count rises to two while the failure mode stays unreachable. Owner-decided as `F4` at kickoff. The rule otherwise stands: no *recognised* shape may be added before A4′. Change-control: recorded in this block's ADR. |

Rows `B2`/`B3`/`B4` (A1′), `B5`/`B6` (A4′), `B7` (A2), `B8` (A6) and `B9` (owner product decision) carry
other addresses and are untouched, except that §11.4 hands `B6` a finding it did not have.

## 13. Doctrine

**This block closes no doctrine rule, and that is by design, not omission.** Master Plan §3.1a states
it: "блок доказывает портируемость, а не этику", and criterion 10 of §2 is not a doctrinal rule. No
`DOC-*` row changes, no verification type changes, no row is added — adding one would be
change-control §5 and would, for the vocabulary rules, decide the A1 fork by documentation.

Two rules gain **evidence without changing their rows**, and this is recorded in the ADR rather than in
the matrix: `DOC-HMA-3` (rollback/marking) gains the precise reachability finding of §11.3, which
sharpens the existing debt; `DOC-ILM-3` (trace 1:1 with reality) gains §6.3, where the trace is
faithful to the *engine* and coarse about the *world* for want of a fact name.

**Localization: the block ships no `en`/`ru`/`tr` strings, because it ships no product surface.** The
console harness prints English developer output. That is a deliberate, recorded exception to the
strings-in-the-same-commit rule, whose subject is user-facing product text; it is not a gap.
`LocaleCompletenessGuardTest` is satisfied vacuously (§4), and this sentence exists so that a future
reader does not read the vacuum as an oversight.

## 14. Verification

Under JDK 17 (`-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`), exit code
checked, output **never** piped through `tail`.

1. **Gate:** `:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test`. `:domain:jvmTest` is
   listed explicitly because `testDebugUnitTest` has not reached it since Этап 2.2 and the whole engine
   lives there.
2. **`:core:ui:verifyRoborazziDebug`** — green with no golden changes, as evidence that `core/ui` is
   untouched rather than as a claim that it is.
3. **Variant proof:** `:consumer:jvm:dependencyInsight --configuration compileClasspath --dependency
   :domain` shows `jvmApiElements`. The second consumer consuming the `jvm()` target is a resolved
   fact, not an inference from the plugin id.
4. **Android-surface diff empty:** `git diff` touches no `feature/*`, no `core/*`, no `data/*`, no
   `app/src/main`. The only `app/` change is the one `inputs.dir` line and the guard test; the only
   `:domain` change is §7's two edits.

   > **FALSE as written — corrected 2026-08-26 by the block's ADR, and the correction strengthens the
   > spec's own thesis.** Measured at close: `git diff --stat a7f4755..HEAD -- feature core data app`
   > touches **six** files. Beyond the two predicted, `GoalShape` is a closed sum with exhaustive
   > else-free `when` sites **outside** `:domain` that `:domain:jvmTest` never compiles —
   > `feature/launcher/.../AgentSessionPresentation.kt` and
   > `data/repository/.../AgentSessionMappers.kt` (plus each one's test). That is §11.2's measured cost
   > of a closed sum, arriving through the verification section instead of the findings section.
   > **§7 stays TRUE:** neither file is `commonMain`, and nothing else in `commonMain` moved.
5. **Guard mutations** — the three of §10, each planted and reverted inside **one** shell invocation
   with a `trap … EXIT` restore, per the precedent of an agent dying mid-round and leaving a probe in
   production source.
6. **No test in this block may *simulate* a path the harness actually walks** — added 2026-08-23, and
   it is the single most transferable lesson of the A0 review. Three of that block's nine findings grew
   from one domain test that stood in for the restart by calling `advance` on a prepared session, while
   the product went through `restoreOnStart()` → `pausedForRestore()` and `continueSession()` →
   `resumed()`; both of those write trace events, and those events were what hid the defect. The rule
   for this block: **if the console harness has a transition, a test drives the harness through it, not
   an equivalent-looking sequence of use-case calls.** A path with a transition no test walks is an
   unverified path, however complete the case list looks.
6. **Reported numbers, not estimates:** lines of adapter code the second consumer required, split by
   mapper / store / tools / planner / driver. §8 claims a mapper per consumer is part of the cost; the
   ADR states the measured figure.

**No device acceptance.** The block changes nothing on the phone, and requiring acceptance would be
pretending that it does — the kind of decorative gate Этап 0.5's status vocabulary exists to prevent.
Target status at close: **`CODE-GREEN`**, with `DEVICE-ACCEPTED` explicitly marked not-applicable
rather than absent.

## 15. Non-goals

Desktop UI. Distribution or packaging. A second product surface. A public SDK — "portable core" means
two **own** consumers, not a published API (§7). Any Android code. A model planner. A local model. An
MCP client (`F3`). Deciding the A1 fork — both branches must be exactly as cheap after this block as
before. Widening `ActionIds`. Widening `OutboundContextPolicy.ALLOWED`. Fixing the vocabulary findings
of §11.2 — they are recorded with addresses, and fixing them here is Approach B/C, rejected in §2.

**Named explicitly because it is the one an implementer will reach for:** unifying the
`Failed` / `Completed` divergence of §6.3. It is a recorded finding owned by A1′, it stays, and §6.3
holds it with a test rather than with a sentence.

## 16. Work order

1. Module skeleton + `settings.gradle.kts`; variant proof (§14.3).
2. `GoalShape.Free` + `TemplatePlanner` arm; `:domain:jvmTest` green (§7).
3. `SandboxToolIds`, `SandboxToolSource`, `SandboxToolExecutor` + a contract test mirroring
   `SystemIntentToolContractTest`'s every-branch output check (§5).
4. `FilePlanner` + its tests (§6.1).
5. `JvmAgentSessionStore` + the concurrent-consent test (§8).
6. End-to-end: hit path, miss path, and the `Failed`/`Completed` divergence test (§6.2, §6.3).
7. Console driver + **both** process-death demonstrations (§9): died-at-the-gate, and died-mid-call —
   the second is required, not optional, and is the invariant Android's own acceptance could only
   approximate through a 157–170 ms window.
8. Guards extended and **mutated** (§10).
9. Full gate, ADR, `CLAUDE.md` + `ai-context/current-status.md` sync, commit proposed to the owner.

## 17. Success criteria

- One goal passes `goal → plan → gate → tool → observe → tool → result → trace` on JVM, with no
  Android in the module's graph, over unchanged `domain/agent` / `domain/tool` / `domain/trace`
  contracts.
- Three steps, two step-to-step bindings, one risk transition that stops the loop before the world is
  touched, and a session that survives process death.
- No core contract acquired a platform branch; no `expect`/`actual` was needed, and §11.2 says why that
  is a finding rather than a shortcut.
- All four §3.1a questions have written answers, negative ones included; `B1` has a written answer.
- The second `ToolExecutor` is under the same mechanical boundary as the first, proved by mutation.
- A process death **during a tool call** is resumed rather than re-issued, proved on the harness — the
  seam A0's review found unheld and Android could only reach with an on-device timing poll.
- Gate green; `git diff` over the Android surface empty; both A1 branches still equally cheap.
