# A4′ Phase 4 — the learned-plan cache · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: written 2026-11-18, reviewed once the same day against the tree and the spec (`reviewer-deep`,
> FIX REQUIRED: 0 blocking, 4 substantial, 6 minor — every finding applied, §0.11). Approved by the owner
> 2026-11-19 with the answer «a» to §0.7 item 6 — Task 10 (Auto Backup) added, the close is Task 11 (§0.12).
> Started 2026-11-19. Closed 2026-11-20, `CODE-GREEN`, gate 1910** (plan 1902; +8 from review and fix rounds) —
> ADRs «2026-11-20 — A4′ ф4: кэш выученных планов — форма со слотами, шаблон, схема 5» and «2026-11-20 — D3,
> поправка: выученная команда» (owner sign-off pending) in `ai-context/decisions.md`; record in the `§HANDOFF` archive,
> «Фаза 4 A4′ — кэш выученных планов». Branch `launcher--7`,
> base `9fe73aa` (the forks commit over `abf3531`), gate baseline **1841 / 0 / 0** — measured at the start of
> this session: boundary `tools/gate.sh`, cleared tree, `557 actionable tasks: 557 executed`, per module
> exactly the floor.
> - **Spec:** [A4′ §8 — phase 4, the learned-plan cache](../specs/2026-09-21-a4-runtime-design.md) (approved
>   2026-09-21), as decided by the owner's six answers (§0.1). §9 (gate), §10, §11 in force.
> - **Form:** [A4′ phase 3b](2026-11-07-a4-phase3b-sorts-forms-multistep.md) — its Global Constraints, mutation
>   protocol and «Measured in execution» practice are inherited unless this plan says otherwise; the owner's
>   rule of 2026-11-13 governs the form: **test names, assertions, predicted red sets, the count chain — no
>   narrative**; mutations only for guards and silent tests.
> - **Record of phases 0–3b:** the `§HANDOFF` archive of [the track plan](2026-08-18-agentic-track-restart.md).
>
> **Everything below is a hypothesis:** every address, every test verdict, every predicted `FAILED ::` set,
> the per-module chain. Execution checks each one; a mismatch is a finding, ledgered with its `[found: …]`
> tag, and the plan is edited only where a later task reads the changed fact.

**Goal.** A command the user confirmed three times is understood again **on the device, offline, without
the model** — through the same validator, the same binder and the same engine gates as the model's own
reading, for any value in the slots the user's words filled:
- **a form with slots** is learned from the model's validated answer at «Выполнить» (fork 4-1, 4-2);
- **pinned at three** confirmations of the same form and template (fork 4-4), it **replays at step 2c** —
  above the local-only, provider and network gates — and starts its session **without the interpretation
  card**, unless the user must pick or the model's own text would leave the phone (fork 4-3); the engine's
  consent card is never skipped (spec §8.3, `DOC-HMA-5`);
- the session card of a learned command says «Выученная команда» and offers «Забыть» (fork 4-5);
- every learned command is **visible and deletable** in a screen reached from Settings (`DOC-AML-5`);
- stored in a new Room table, schema 4 → 5 (fork 4-6); the JVM consumer gets the same mechanism over a file.

**Architecture.**
- **`:domain` (`commonMain`, new package `com.sidr.launcher.domain.memory.plan`).** The form and the template
  (`CommandForm`, `FormPart`, `PlanTemplate`, `TemplateStep`, `TemplateArg`, `LearnedPlanShape`,
  `LearnedPlan`); `LearnedForms` (learn, match, fill — pure); `LearnedPlanStore` port; `RecordLearnedPlanUseCase`,
  `ForgetLearnedPlanUseCase`, `ObserveLearnedPlansUseCase`; the `LearnedPlans` port + `DefaultLearnedPlans`
  (replay through `BridgeValidator` and `MentionBinder`) + `Replay` + `Interpretation.Act.needsCard()`.
  `RouteCommandUseCase` gains step **2c** and `learnedPlans`; `CommandOutcome.Interpreted` gains `learnable`,
  and `learned`, `CommandOutcome.AgentSessionStarted` gains `learned` (fields with defaults — no new variant, no
  `when` edit).
- **`:data:repository`.** `LearnedPlanEntity` / `LearnedPlanDao` / `Migration4To5` / `LearnedPlanStoreImpl`;
  `SidrDatabase` version 5; `schemas/…/5.json`; `RoomColumnNames.LEARNED_PLANS`.
- **`:app`.** DI (`MemoryBindsModule`, `MemoryProvidesModule`, `BridgeProvidesModule.provideLearnedPlans`,
  `DatabaseModule.addMigrations(…, Migration4To5)`, the route's new parameter); `BridgePayloadGuardTest` gains the
  fifth private source; the `NavHost` entry.
- **`:feature:launcher`.** «Выполнить» records; the session card's mark and «Забыть»; the learned-commands
  screen (its step lines are the card's own `PlanStep.line`).
- **`:feature:settings`.** One row opening that screen. **`:core:common`.** `Routes.LearnedCommands`.
- **`:consumer:jvm`.** `JvmLearnedPlanStore` (a JSON file) and step 2c in `ConsoleHarness`'s `ask` path.
- **`:app`, memory stays on the device (owner, 2026-11-19, §0.7 item 6 «a»).** `res/xml/data_extraction_rules.xml`
  (API 31+: cloud backup and device-to-device transfer) and `res/xml/backup_rules.xml` (API 28–30) leave out every
  database and every DataStore file; the manifest names both; `BackupRulesGuardTest` holds it.

`ActionIds`' seven values, `ObservedFact`, `CommandFailure`, `GoalShape`, `ArgType`, `ToolDescriptor`'s type,
`ExecutionState`, `TraceEvent`, `StepRationale`, `PlanningResult`, `ModelPlanning`, `Binding`, `BridgeRequest`,
`BridgeRendering`'s instruction and `OutboundContextPolicy.ALLOWED` are not in the diff. **What the model is
asked does not change.**

**Tech Stack.** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain` (`commonMain` +
`jvmTest`) · Room 2.8.4 (+ `room-testing` for the Robolectric migration test, §0.2 B-14) · kotlinx-serialization-json
· JUnit4 + `kotlinx-coroutines-test` · Robolectric + Compose UI test.

---

## Global Constraints

Every task's requirements implicitly include this section. 3b's Global Constraints on **running the gate,
reading the results, the mutation protocol, fixtures and commits** hold verbatim, with these values:

- **Floor and baseline: 1841 / 0 / 0** (`tools/gate.sh`, `BASELINE_TESTS = 1841`). Per module: `:domain` 582 ·
  `:consumer:jvm` 93 · `:data:repository` 468 · `:feature:launcher` 281 · `:app` 84 · `:feature:settings` 34 ·
  the other seven 299 (`:core:ui` 128, `:data:ai-cloud` 49, `:data:prayer` 38, `:feature:assistant` 38,
  `:feature:prayer` 19, `:feature:permission_education` 15, `:core:android` 12). The floor stays 1841 for the
  whole phase and is raised at close to the measured total.
- **Predict the decomposition before every boundary run and compare after** (§0.8). **Stop the daemon before a
  boundary gate that follows mutations.** The timing-sensitive `:data:ai-cloud` test (`first token arriving just
  under the deadline still completes normally`) is a reason to re-run, not to believe.
- **`:core:ui` is not touched** (the screen and the mark reuse `SidrMemoryItem`, `SidrSectionHeader`,
  `SidrText`). A task that must touch it stops: finding, `:core:ui:verifyRoborazziDebug` added.
- **Fixtures (R14-43): assertions compare by equality; no fixture is a real app.** Phase 4's set: apps «Lumora»
  (`net.fixture.lumora`), «Vemblic» (`net.fixture.vemblic`), «Miravin» (`net.fixture.miravin`); learned
  commands «сотри lumora» → `uninstall_app(app = lumora)` (form «сотри …»), «поищи орвелин» →
  `web_search(query = орвелин)` (form «поищи …»), replayed as «сотри Vemblic!» and «поищи глимсар»;
  «будильник на рассвете» → `set_alarm(time = 6:00)` (a constant, form = every word); «поищи что-нибудь про
  орвелин» → `web_search(query = орвелин история)` (model text going out); the address
  `HTTPS://Quillmere.example/Path`; the payload-guard sentinels «плексорин» and `SNTL-LEARNED-CONST` (planted,
  never typed), and the payload guard's typed learned form «тирмалон …».
- **Frozen, checked with `git diff --stat abf3531 -- <path>` at close:** the list under **Architecture** above,
  every `strings_locked.xml`, everything under `core/ui/src/main`, `schemas/…/1.json`…`4.json` (5.json is new).
- **`:domain` stays stdlib + coroutines.** `:consumer:jvm` keeps zero Android artifacts (`ModuleIsolationTest`).
- **Strings:** eight new keys (Task 7: 2, Task 8: 6 — one of them in `:feature:settings`), `en`/`ru`/`tr` in the
  same commit; the `tr` text joins the owner's Turkish review at block acceptance.
- **Nothing about the user's world leaves the device through the bridge** — the learned store is a fifth private
  source the bridge graph reads, and Task 6 proves its sentinels reach no body (`DOC-AML-1`). **Not** a claim that
  nothing leaves at all until Task 10: Android Auto Backup copies the database and the DataStore files today (§0.7
  item 6); Task 10 leaves them out of cloud backup and device transfer (`DOC-AML-4`), in code, not seen on a phone.
- **A line predicting what the user will see is a hypothesis** until run once on a phone.
- **Tiers:** no task carries complete code → `implementer-critical` by default. Task review by risk:
  `reviewer-deep` after Tasks 3, 4, 5, 6, 7, 9, 10 (egress, consent, memory, migration, lifecycle, backup); `reviewer`
  after 1, 2, 8; none after 11; the final whole-branch review `reviewer-deep`.
- Commits: Russian, scope `agentic-6/A4' ф4`, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`;
  the agent commits and pushes to `origin` (owner, 2026-11-17), never a force push; the phone is not touched;
  `connectedAndroidTest` and every instrumented test are never run by the agent.

## Review Focus

Five input classes the spec implies but no criterion names, each likely to bite a person, each pinned by a test
in the task that owns the code.

1. **A command that fits a learned form but names something the device cannot bind** — «сотри все фотографии»
   once «сотри …» is pinned. The cache must not answer «нет такого приложения» for understanding's sake: it
   misses, and the command goes on down the chain (the model, or the honest local-only line).
   → Task 4, `RouteCommandUseCaseTest > a learned command that does not bind goes on down the chain`.
2. **The same command typed with other case or trailing punctuation** — «Сотри Vemblic!» must match «сотри …»,
   and the slot must hold «Vemblic», not «Vemblic!».
   → Task 1, `LearnedFormsTest > a form takes another value in its slot, as the user typed it`.
3. **The app the plan was learned on is gone** — the template holds no package; replay binds the words again
   and a gone app misses (spec §8.2). → Task 3, `LearnedPlanReplayTest > a slot the binder cannot bind misses`.
4. **A tool that left the offering** (a role revoked, a provider level change) — replay misses, the model is
   asked as before, the entry is kept for when the tool returns.
   → Task 3, `LearnedPlanReplayTest > a tool no longer offered misses and the entry stays`.
5. **A learned uninstall, the hundredth time** — no interpretation card, and still the engine's consent card
   naming the package, every time. → Task 5, `LearnedPlanSeamTest > a learned uninstall stops at consent every time`.

---

## 0. Pre-flight

### 0.1 · The owner's forks — answered 2026-11-17/18, before this plan was written

Asked one at a time, each with meaning, options and cost, in the session of 2026-11-17 20:46–22:11 UTC
(23:46–01:11 Moscow), which ended before it wrote anything; fork 6 was answered in the next session
(2026-11-18). The questions, options and answers verbatim: git-ignored
`.superpowers/sdd/2026-11-18-a4-phase4-plan/forks-session-a62d319e-raw.txt`.
**Answer line: `1: a · 2: a · 3: c · 4: a (3) · 5: a · 6: a`.**

| Fork | Answer | What it decides |
|---|---|---|
| **4-1** the cache's key (spec §8.1) | **(a) a form with slots** | the user's words the model took into an argument become a slot: «удали телеграм» → «удали …»; the streak is the form's; once pinned, «удали X» works for any X; each language is its own form around one template |
| **4-2** what is stored (§8.2) | **(a) the model's answer with slots** | the validated answer in its own shape: tool, arguments as words (the user's words → slots, the model's own words → constants), references; no package, no risk, no effect. Replay goes through **the same validator and binder** as step 7 |
| **4-3** what the 4th time shows (§8.3) | **(c) at once, no switch** | a pinned command runs like an authored one; a risky step's consent card is the floor, asked every time in any mode; the **interpretation** card only when the user must choose or the model's text goes out (`DOC-MZN-2`). The switch «Спрашивать перед выученными командами» comes with a modes block — **candidate block** (modes as in Claude Code + grant rules + the floor + an anonymity axis + a full-access window; brainstorm → spec). Needs an ADR amending D3 |
| **4-4** the threshold (§8.4) | **(a) 3, the shared constant** | `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD` for everything learned; one «раз» = «Выполнить» on a model card of the same form and template **after which the session started** |
| **4-5** provenance (`DOC-ILM-5`, §8.5) | **(a) a mark in process memory** | the session card of a learned command: «Выученная команда» + «Забыть»; lives while the process lives. Named limit: a card restored after process death mid-plan does not carry it. A Settings section «Выученные команды» (form, steps, streak N/3, forget) in every variant — `DOC-AML-5` |
| **4-6** storage | **(a) a new table, schema 4 → 5** | `learned_plans` in `sidr_history.db`; `Migration4To5` = `CREATE TABLE` only; `5.json`; the column-name guard; a cap with eviction. Structured rows are the future source of «goal → plan» pairs (ADR 2/4). The migration: an attempt to prove it in the gate (Robolectric) + a case in `MigrationTest` for the owner's acceptance |

**Recorded beside the forks (owner, 2026-11-17):** «agentic control of the system — one main agent between
the user and the system» — answered: yes, that is Sidr's thought (the session's answer, raw file 21:33 UTC);
«where does the agent's memory of the user live» — three levels (memory without training, today; personal
context for a local model, A2/A3; LoRA for style only), and this phase's store is the first structured
source of confirmed «goal → plan» pairs (raw file 22:11 UTC). Neither is a decision of this plan.

### 0.2 · Decisions this plan takes (the owner may overturn any; each is named at close)

- **B-1 · Words and tokens.** A command is read as its **space-separated tokens** (`Regex("\\S+")` over the
  trimmed command, with their character ranges); each token's words are `folding.words(token)`, and the
  command's word list `W` is their concatenation in order. A **form** is a list of `FormPart.Word(word)` (a
  folded word) and `FormPart.Slot`; a slot always covers **whole tokens**. Its **key** is the parts joined by
  one space, a slot written `…` (U+2026 — never a folded word, which holds only letters, marks and digits), so
  the key is canonical and is also what the screen shows: «сотри …».
- **B-2 · `learn` — what a confirmed reading teaches, or `null`.** Over the validated steps
  (`ModelPlanning.Proposed.steps`) before binding:
  - an argument whose origin is `ValueOrigin.SaidBy` → its words are found in `W`; **not exactly one
    occurrence → `null`**; an occurrence that does not start at a token's first word and end at a token's last
    word → `null`; two arguments with the same span share one slot; overlapping different spans → `null`;
  - any other origin → `TemplateArg.Constant(value)`; a reference → `TemplateArg.Ref(step, key)`;
  - slots are numbered in the order their spans occur in the command; the form replaces each span by one
    `Slot`;
  - **`null` when** the form has no `Word`, two `Slot`s stand side by side, the key exceeds
    `MAX_FORM_LENGTH = 64`, or a constant exceeds `MAX_CONSTANT_LENGTH = 64` (chosen, not measured — the
    learned-choice store's `MAX_QUERY_LENGTH` is 64);
  - **`null` unless `match(form, command)` gives back exactly the slot values just learned** — a form that could
    not replay its own command would pin, show «выучено», and never run (review M1: «найди книгу в магазине в
    центре» learns «найди … в …» and matches it two ways).
- **B-3 · `match` — a form and a command, or `null`.** Enumerate every assignment of `W` to the form's
  parts (a `Word` equals the next word; a `Slot` takes one or more whole tokens). **Exactly one complete
  assignment → the slot values; none or more → `null`.** A slot's value is the trimmed command's substring
  from its first token's start to its last token's end, with leading and trailing characters of
  `SLOT_EDGE_PUNCTUATION = ".,;:!?…\"'«»“”„()[]"` removed; an empty value → `null`. Case, marks and inner
  punctuation stay as typed (an address stays an address).
- **B-4 · Replay is step 7 without the model.** `fill(template, slots)` builds a `RawAnswer(kind = plan)`;
  `BridgeValidator.validate` judges it against **the current offering** (`BridgeFilter.offeringOf(registry,
  levels, descriptions)`), the registry and **this** command with the device's folding; `MentionBinder.bind`
  binds it. Risk and effect come from today's registry; packages from today's binder (spec §8.2 by
  construction: a mention is derivable, so it is always a slot, never a constant).
- **B-5 · The cache never answers on understanding's behalf.** Anything but `Binding.Bound` — a validator
  rejection, `NotFound`, `NotUnderstood`, `Unavailable` — is `Replay.Miss`, and the command goes on down the
  chain exactly as if nothing were learned (the E6-R1 rule: a deterministic miss is not a filter on
  understanding). An unreadable store is a miss too; it fails open to step 7 (named beside A1′ residual (3)).
  **Exactly one pinned form must match**; two → miss.
- **B-6 · Step 2c** sits after 2b and before gate 3, only when FastPath was undecided and 2b answered
  `NoPlan`. `DOC-ADL-3` permits it as written (deterministic replay is part of the local path); no amendment.
- **B-7 · What the 4th time shows.** `Interpretation.Act.needsCard()` = a pick is owed **or** a step whose
  effect is `EXTERNAL` carries a `ValueOrigin.ModelGenerated` value (the same predicate the card's
  `ModelValues` draws, `DOC-MZN-2`). Needs a card → `CommandOutcome.Interpreted(act, learned = form)` (no
  `learnable`), and the card's «Выполнить» passes `learned` on into `AgentSessionStarted` — a learned command keeps
  its mark whether or not it needed the card (fork 4-5; review S4). Otherwise `startPlanned` at once →
  `AgentSessionStarted(id, learned = form)`; a store failure falls through, as 2b's does.
- **B-8 · Recording.** Step 7 attaches `learnable = learnedPlans.learnable(command, answer.steps)` to the
  `Interpreted` it returns for a `Bound` reading — **in step 7 itself, never inside `Binding.toCommandOutcome()`**,
  which the remainder card shares and `BridgePayloadGuardTest:609-625` compares by equality; a remainder card and a
  replay card carry none. The launcher
  records it **after `startPlanned` succeeds**, on `applicationScope` (like `recordChoiceIfPending`).
  `RecordLearnedPlanUseCase` keeps the learned-choice semantics: none → streak 1; same template → +1; another
  template → streak 1 with the new template. `pinned` = streak ≥ `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD`.
- **B-9 · Kept and capped.** A stale entry is kept (a tool may return); a forgotten one is deleted. At most
  `MAX_LEARNED_PLANS = 200` rows (chosen), evicted in the store's `upsert` **unpinned first** (lowest streak, then
  the oldest confirmation), a pinned row only when no unpinned one is left (oldest confirmation first). Review S1: a
  replay writes nothing, so «least recently confirmed» alone would evict the command used every day before the
  streak-1 noise every fresh reading leaves. A row whose template cannot be read is **deleted when the store meets
  it** — it can never replay, and a row the screen cannot show would break `DOC-AML-5` (review M3; the precedent is
  `PruneUnavailableLearnedChoicesUseCase`).
- **B-10 · The mark** is `(session id, form)` held by `LauncherAgentSession`, shown while that session is on
  screen; another session, `show(null)` or `restoreOnStart` drops it (fork 4-5's named limit).
- **B-11 · The screen lives in `:feature:launcher`.** Its step lines must be the card's (`PlanStep.line`, tool
  labels are launcher resources), and `feature → feature` is forbidden: Settings gets one row that navigates to
  `Routes.LearnedCommands`, the `NavHost` composes the launcher's screen. A slot is drawn `…`.
- **B-12 · `RouteCommandUseCase(…, learnedPlans: LearnedPlans = LearnedPlans.NONE)`** — a default keeps its twelve
  test construction sites unchanged; the production provider takes it **without** a default, so the Hilt graph
  cannot build a route without it.
- **B-13 · JVM parity.** `JvmLearnedPlanStore(file: Path)` (kotlinx JSON, write-to-temp-then-move like
  `JvmAgentSessionStore`; one process at a time, no `FileLock`); `ConsoleHarness`'s `ask` path replays before the
  model, prints the learned line, records after a card's «yes» once the session started.
- **B-14 · The migration proved in the gate, by attempt.** `testImplementation(libs.room.testing)` + the schema
  directory as test assets; `Migration4To5Test` (Robolectric) runs `MigrationTestHelper` 4 → 5. **If
  `MigrationTestHelper` cannot run under Robolectric** (a finding, ledgered): the fallback test creates a v4
  database from `4.json`'s `createSql` **and its `setupQueries` (`room_master_table` with the v4 identity hash),
  with `PRAGMA user_version = 4`** — without them Room opens the file as a new database and proves nothing — then
  opens it with Room 5 through `addMigrations(Migration4To5)` (Room checks the identity hash and every table on
  open) and reads one row of each pre-existing table back. The helper route needs
  `sourceSets["test"].assets.srcDirs(schemas)`, mirroring the `androidTest` block (`data/repository/build.gradle.kts:35-37`). The
  instrumented `MigrationTest` gets `v4_to_v5` and `v1_to_v5` for the owner's acceptance either way.
- **B-15 · D3 is amended by ADR at close** (fork 4-3): «nothing the model proposes runs before «Выполнить» —
  except a learned command, a form and template the user confirmed three times, which runs through the engine;
  no gate of the engine is skipped». `OneModelPathGuardTest` is unaffected (the learned path executes no
  `LauncherAction`; it starts a session).

### 0.3 · The measurement this plan stands on

Offline, free, done by the forks session (2026-11-17, its scratchpad script over phase 3b's git-ignored
measurement): **337 plan lines, 7 models** — an app mention was the user's own words **94 of 94**; a clock time
the model's own words **22 of 22** («в девять» → 9:00); a duration and a search text about half and half. It
decided fork 4-1's shape (mentions → slots; times → constants). **No paid run and no early model probe**: what the
model is asked does not change (the rule of 2026-11-13 asks for one only then).

### 0.4 · Facts read out of the tree (at `abf3531`)

- `ModelPlanning.Proposed(steps)` (`ModelPlanning.kt:66`); `ProposedStep(tool, args, origins, risk, effect, refs)`
  (`:46-53`) — args model-facing (`app`, `time`, `duration`, `query`, `url`; `AuthoredToolDescriptions.kt:43-76`).
- `BridgeValidator.validate(answer, offering, registry, command, folding)` (`BridgeValidator.kt:36-42`) — shape,
  membership (`UNKNOWN_TOOL` / `NOT_OFFERED`), derivability exact after folding, references.
- `Derivability.originOf` makes every derivable text argument `SaidBy(USER)`, any other `ModelGenerated`
  (`Derivability.kt:24-26`); a mention must be derivable (`BridgeValidator.kt:107-109`).
- `MentionBinder.bind(goal, steps)` → `Binding.{Bound, NotFound, NotUnderstood, Unavailable}` (`Interpretation.kt:118-148`).
- The engine's consent floor is `effectiveRisk = maxOf(step.risk, registry risk now)` (`AgentExecutor.kt:344-359`):
  no plan-side risk can lower it.
- «удали …» is claimed at 2b (`ToolVocabulary.kt:259`) and «найди …» decided by FastPath
  (`RuleBasedIntentMatcher.kt:269`) — neither reaches step 7, so neither is ever learned; «сотри», «поищи» are
  claimed by neither.
- `RouteCommandUseCase`: 2b `:173-191`, gate 3 `:195-198`, step 7 `:215-232`; constructor `:127-138`; 13 construction
  sites (1 production, `BridgeProvidesModule.kt:101-118`; 12 tests).
- `CommandOutcome.AgentSessionStarted(id)` (`CommandOutcome.kt:76`), `Interpreted(interpretation)` (`:84`).
- `LauncherCommandSession.runInterpretation` (`:507-533`): picks recorded, `startPlanned`, `applyOutcome`;
  `showRemainder` (`:566-569`) → `toCommandOutcome()`; `applyOutcome` `AgentSessionStarted` (`:447-450`),
  `Interpreted` (`:455-459`); `dismissInterpretation` (`:577-581`).
- `LauncherAppLaunch.recordChoiceIfPending` writes on `applicationScope` (`:118-133`).
- `LauncherAgentSession`: `show` (`:100-106`), `attach(id)` (`:108-112`), `restoreOnStart` (`:129-154`); a finished
  session stays on screen.
- `InterpretationSurface.ModelValues` shows a `ModelGenerated` value of an `EXTERNAL` step (`:137-148`).
- `RecordResolutionChoiceUseCase.record`: create / reinforce / hard-switch; `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD = 3`
  (`Model.kt:6`). Fakes of memory stores live in `core/testing` (a JVM module `:domain:jvmTest` depends on).
- `SidrDatabase` `version = 4` (`SidrDatabase.kt:55`); migrations wired in `DatabaseModule.kt:50`; `room-testing`
  is `androidTestImplementation` only (`data/repository/build.gradle.kts:79`); Robolectric is a test dependency
  (`:72`); `MigrationTest` is instrumented only.
- `RoomColumnNames` is hand-written; the guard's forbidden terms include `query`, `search`, `message`, `history`,
  `token`, `api`, `secret`.
- Tool labels: `PlanStep.line` in `feature/launcher/…/agent/AgentSessionPresentation.kt:318-360`. Settings'
  memory screen `LearnedChoicesScreen` (`:feature:settings`), `Routes.LearnedChoices` (`Routes.kt:65`),
  `AppNavHost.kt:344`.
- `BridgePayloadGuardTest` plants four device sources (`:150-160`) and builds the route through the production
  provider (`:231`). `AgentVocabularyGuardTest` scans `:domain`'s `agent`, `tool`, `trace` only.
- `LauncherViewModel(` — 7 test construction sites. `ConsoleHarness.interpret` (`ConsoleHarness.kt:215`) after
  `modelCommandOf` (`:194`).
- A replayed step is built by `ProposedStep.toPlanStep` → `StepRationale.MODEL_PROPOSED`, so a paused learned plan
  resumes as any model plan (its remainder card, phase 3b B-10).

### 0.5 · Where the spec and the tree diverge, and what each changes

1. **§8.2 «cache the template» — the bound plan cannot be the template**: it keeps the user's words only where
   the tool has a label argument (`launch_app.query`, `uninstall_app.app_label`), and `open_app_info` keeps only
   the package. → The template is the validated answer **before** binding (fork 4-2, B-4).
2. **§8.5 «the trace knows» — it does not**: `PlanCreated` carries a step count, and a replayed step is
   `MODEL_PROPOSED` like any model step (resume keys on it). → Provenance is the store plus the card (fork 4-5).
3. **§8.6's argument (`ToolSelector`, shortcuts, the moving registry) does not reach the cache**: since 3a the
   model is offered the nineteen authored tools only, and `ToolSelector` works only at 2b. → What the cache
   stabilises is the model's reading itself and the resolution of names on a changing phone.
4. **§8.2's invalidation has two checks, not one**: `InvocationValidator` (`UNKNOWN_TOOL`, in `startPlanned`) and
   `BridgeValidator`'s membership in the current offering. Replay goes through both (B-4).
5. **§8.4's threshold is unchanged**: 3b only made word-form confirmations count toward it (B-3 of 3b).
6. **A doctrine address now points nowhere**: `DOC-HMA-3`'s marker «Остаток ← долг A4′ ф4» (multi-step undo on
   Android) — phase 4 builds no model-offered tool with an inverse. → Re-addressed at close (owner item).
7. **The spec does not say what the user sees on the 4th time beyond «not the consent»** — fork 4-3 decided it,
   and it amends D3 (B-15).

### 0.6 · What this plan does NOT do — said, not implied

| Not here | Why / where |
|---|---|
| Modes («как в Claude Code»), the switch «Спрашивать перед выученными командами» | fork 4-3: candidate block, brainstorm → spec |
| Learning from FastPath, 2b, a remainder card or a replayed card | only step 7's fresh reading teaches (B-8) |
| Caching `ToAssistant` / `Navigate` / `Clarify` readings | spec §8 caches plans |
| Learning a command written without spaces (CJK, Thai) or a value that cuts a token | B-2: not learnable, asked every time — named |
| A form with no word of its own («lumora» alone) | B-2 |
| Provenance that survives process death | fork 4-5 |
| Tool labels inside `:feature:settings` | B-11 |
| Moving a learned command to another device; LoRA; local inference | A3 + A5; `B16` |
| `secure_delete` for a forgotten row | A1′ residual (10) covers it — named |
| Any device round | one acceptance, at A4′'s end |

### 0.7 · Owner dependencies and hypotheses

**Owner dependencies.** (1) The D3 amendment ADR and the CLAUDE.md hard-rule text (Task 11); (2) `DOC-HMA-3`'s
marker gets a new address (Task 11); (3) the Turkish review grows by seven strings; (4) the instrumented
`MigrationTest` `v4_to_v5` / `v1_to_v5` run at acceptance; (5) §0.2's decisions, B-5, B-7 and B-11 first;
(6) **a finding outside the spec, found writing this plan:** `app/src/main/AndroidManifest.xml:64` sets
`android:allowBackup="true"` with no `dataExtractionRules` / `fullBackupContent`, so Android Auto Backup copies
`sidr_history.db` (learned choices, aliases — and from this phase learned commands) and the DataStore files to the
user's cloud backup. That is memory leaving the device as a side effect (`DOC-AML-4`), and no guard sees it. It
predates this phase; excluding the memory files changes what a restore on a new phone brings back, so it is the
owner's call — **the owner added it, 2026-11-19, answer «a»: Task 10** (with `targetSdk` 36 and
`minSdk` 28 it needs both `dataExtractionRules` (API 31+, cloud backup and device-to-device transfer) and
`fullBackupContent` (API 28–30) excluding the database and the DataStore files, plus a guard on the manifest; the
Keystore-wrapped secrets DataStore already restores as ciphertext a new device cannot read). The cost the owner
accepted: on a new phone the memory is not restored. Task 11 writes the outcome in Known debt / the shipped surface
and the ADR names what this phase starts storing: `learned_plans` is the
first persistent holder of **confirmed command words** — a form with no slot is the whole folded command, a
constant may hold model-written search text, and every fresh reading writes a streak-1 row.

**Hypotheses for the block's acceptance checklist — nobody has seen these on a phone.** (Review S2: «удали …»
is 2b's and «найди …» FastPath's — they never reach the model, so they cannot test the cache.)
- «сотри <app>» (online) is expected to show the interpretation card the first three times (control), and the
  fourth time «сотри <another app>» to show **no** interpretation card, only the engine's consent card naming the
  package — online, offline or local-only.
- «поищи <words>» is expected to show the card three times, then «поищи <other words>» to open the search at once,
  its session card reading «Выученная команда» with «Забыть».
- «Забыть» on that card is expected to make the next «поищи …» show the interpretation card again.
- Settings → «Выученные команды» is expected to list «сотри …» and «поищи …» with their step lines, the streak and
  «Забыть».
- The app updated over a v4 install is expected to start, with learned choices and aliases intact (migration).

### 0.8 · The gate chain — predicted before any run

| Module | Base | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | T9 | T10 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `:domain` | 582 | 597 | 602 | 609 | 615 | 615 | 615 | 615 | 615 | 615 | 615 |
| `:data:repository` | 468 | 468 | 468 | 468 | 468 | 477 | 477 | 477 | 477 | 477 | 477 |
| `:app` | 84 | 84 | 84 | 84 | 84 | 84 | 85 | 85 | 85 | 85 | 89 |
| `:feature:launcher` | 281 | 281 | 281 | 281 | 281 | 281 | 281 | 287 | 290 | 290 | 290 |
| `:feature:settings` | 34 | 34 | 34 | 34 | 34 | 34 | 34 | 34 | 35 | 35 | 35 |
| `:consumer:jvm` | 93 | 93 | 93 | 93 | 93 | 93 | 93 | 93 | 93 | 97 | 97 |
| the other seven | 299 | 299 | 299 | 299 | 299 | 299 | 299 | 299 | 299 | 299 | 299 |
| **total** | **1841** | 1856 | 1861 | 1868 | 1874 | 1883 | 1884 | 1890 | 1894 | 1898 | **1902** |

Task 11 adds no test. **No task deletes a test.** Existing tests changed at constant count are in §0.9.

### 0.9 · Guards, anchors and existing tests this plan moves

| Guard / test | What happens | Where |
|---|---|---|
| `RouteCommandUseCaseTest` (all), `AgentEgressSentinelGuardTest`, the twelve route construction sites | **stay green unchanged** — `LearnedPlans.NONE` by default | Task 4 |
| `BridgePayloadGuardTest > a remainder asks the model nothing` (`:609-625`, compares a remainder `Interpreted` by equality) | **stays green** — `learnable` is attached in step 7, never in `toCommandOutcome()` (B-8) | Task 4 |
| `RoomColumnNamesGuardTest` scans | run over `learned_plans` (+1 inventory test) | Task 5 |
| `BridgePayloadGuardTest > no private source reaches any body…`, `> the controls - the device read every planted source…` | rewritten at constant count: + «learned plans» source, + its read control | Task 6 |
| `ProductionGraph`, `BridgeProvidesModule.provideRouteCommandUseCase` callers | compile-follow the new parameter | Task 6 |
| `LauncherViewModel(` (7 test sites) | compile-follow the two new use cases | Task 7 |
| `OneModelPathGuardTest`, `DoctrineGuardTest > the composition root's planner list…` | **stay green** — no direct `LauncherAction`; `DefaultLearnedPlans` is not a `Planner` | Tasks 4, 6 |
| `CoreVocabularyFreezeGuardTest`, `AgentVocabularyGuardTest`, `ModuleIsolationTest`, `HardcodedUiTextGuardTest`, `StringSeamGuardTest`, `LocaleCompletenessGuardTest` | must stay green | every task |
| `ToolPermissionManifestGuardTest` (reads the same manifest) | **stays green** — `<application>` attributes only, no `<uses-permission>` | Task 10 |
| `DOC-ADL-3`, `DOC-AML-1`, `DOC-AML-4`, `DOC-AML-5`, `DOC-NYH-1`, `DOC-ILM-5`, `DOC-HMA-3` | + tests / note / decision / re-address | Task 11 |
| KDoc made false: `RouteCommandUseCase` (order of decision), `CommandOutcome.Interpreted`, `SidrDatabase` (version list), `RoomColumnNames`, `migrations/README.md`, `LauncherAgentSession`, `ConsoleHarness` | rewritten | the task that changes the code |

### 0.10 · How the predictions were produced

Test counts are this plan's own (one row per named test). No probe ran; the only pre-plan run was §0.3's offline
count and the start gate. The Robolectric migration test is a hypothesis with a named fallback (B-14), because a
schema-loading failure is red, never green — execution catches it.

### 0.11 · What the plan review changed

**One review, 2026-11-18** (`reviewer-deep`, read-only, against `54ffcf0` and the spec). **Verdict: FIX
REQUIRED** — 0 blocking, 4 substantial, 6 minor. It confirmed against the tree: replay as B-4 works (a mention is
always a slot; the binder's irreversible rule applies as at step 7); per-token words equal the command's words
for Plain and ICU; the default parameter leaves the twelve route sites green; `needsCard` is `ModelValues`' predicate;
no test pins DB version 4; the chain recounted exactly. Each finding was checked against the tree before it was
applied; none was rejected.

| # | Finding | What changed |
|---|---|---|
| S1 | Eviction by «least recently confirmed» drops the command used daily before the streak-1 noise (a replay writes nothing) | B-9: unpinned first; Task 5 test 2 rewritten, + `a pinned command survives 200 one-off readings` |
| S2 | «удали …» is 2b's (`ToolVocabulary.kt:259`), «найди …» FastPath's (`RuleBasedIntentMatcher.kt:269`) — the acceptance lines could not test the cache | §0.7 hypotheses on «сотри …» / «поищи …» with a control line; §0.4 fact |
| S3 | Spec §10 criterion 9 asks for three separate mutations (streak · re-resolve · consent); M5-1 was vacuous — the engine's floor is `maxOf(step.risk, registry risk)` (`AgentExecutor.kt:344-359`) | Task 3: M3-2 (threshold), M3-3 (re-resolve); Task 5: M5-1 replaced by a plant that pre-grants the replayed session's consent |
| S4 | A learned command that needed the card lost its mark (`runInterpretation` emits `AgentSessionStarted(id)` with no form) | B-7: `Interpreted.learned`; Task 4 test 3 asserts it; Task 7 + `a learned command that needed the card keeps its mark after Run` |
| M1 | `learn` and `match` can disagree: a form that pins but never matches its own command | B-2 last clause; Task 1 + `a form that cannot match its own command is not learned` |
| M2 | Task 6: M6-1's red set too narrow and its plant site unable to see the store; the new test's control counted all bodies though the mock rejects every first ask; its command typed a sentinel | Task 6 rewritten (plant in `provideRouteCommandUseCase`; three reds; first asks counted; the form «тирмалон …») |
| M3 | Hiding an unreadable row breaks `DOC-AML-5` | B-9: deleted when met; Task 5 test 3 rewritten |
| M4 | File lists: `LauncherScreen.kt` (Task 7), `DatabaseModule.provideLearnedPlanDao` (Task 6), the session file is built in `ConsoleHarness.kt:105` not `Main.kt` (Task 9), a string for the unreadable state (Task 8: eight keys), full `XTest > name` matrix citations (Task 10) | applied |
| M5 | B-14's fallback needs `setupQueries` + `user_version = 4`; the helper needs test assets | B-14 |
| M6 | address drift | §0.4 corrected |
| §0.7 (6) | Auto Backup verified; the fix sketch needs `dataExtractionRules` + `fullBackupContent`; the «nothing leaves» constraint was false as worded; the ADR must name what this phase starts storing | §0.7 item 6, Global Constraints, Task 10 |

**Net effect on the numbers:** T1 +1, T5 +1, T7 +1 → **1898** at T9 (was 1895). **No second review** under the
owner's rule: the delta is corrections inside the same ten tasks — no task, port or closed type added or removed.

### 0.12 · What the owner added after the review (2026-11-19)

**§0.7 item 6, answer «a»:** leave the memory out of Auto Backup and device transfer, a small task of this phase
with a guard. It became **Task 10**; the close moved to **Task 11** (the §0.11 table above keeps the review's own
numbering). Decisions it takes (each named at close):
- **B-16 · Domains, not file names.** The rules leave out the whole `database` domain (`path="."`) and the
  DataStore directory (`domain="file" path="datastore/"`), so a database or DataStore added later is left out
  without an edit. Everything else the app writes (today the AppCompat locale record in `files/`) is still backed
  up. `android:allowBackup` stays `true`.
- **B-17 · The guard holds the reach, not only the XML.** Production code may open storage only through
  `Room.databaseBuilder` and `preferencesDataStoreFile` — the two shapes whose files the rules cover; any other
  storage call (`getSharedPreferences`, `openFileOutput`, `dataStoreFile`, a `File(` under `filesDir`/`getDir`, …)
  in any module's `src/main` is red until the rules and the guard are widened together.
- The chain: `:app` +4 → **1902** at T10.

---

## Task 1: the form and the template — learn, match, fill (`:domain`)

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlan.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedForms.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/plan/LearnedFormsTest.kt`

**Interfaces:**
- Consumes: `ProposedStep`, `ValueOrigin`, `PrincipalId` (`domain/ai/bridge`); `RawAnswer`, `RawStep`, `RawArg`,
  `BridgeRendering.KIND_PLAN`; `TextFolding`; `ToolId`; `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD`.
- Produces (in `LearnedPlan.kt`):
  - `const val MAX_FORM_LENGTH = 64`, `MAX_CONSTANT_LENGTH = 64`, `MAX_LEARNED_PLANS = 200`, `SLOT_MARK = "…"`,
    `SLOT_EDGE_PUNCTUATION = ".,;:!?…\"'«»“”„()[]"`
  - `sealed interface FormPart { data class Word(val word: String); data object Slot }`
  - `data class CommandForm(val parts: List<FormPart>) { val key: String; val slotCount: Int; companion object { fun parse(key: String): CommandForm } }`
  - `sealed interface TemplateArg { data class Slot(val index: Int); data class Constant(val text: String); data class Ref(val step: Int, val key: String) }`
  - `data class TemplateStep(val tool: ToolId, val args: Map<String, TemplateArg>)`, `data class PlanTemplate(val steps: List<TemplateStep>)`
  - `data class LearnedPlanShape(val form: CommandForm, val template: PlanTemplate)`
  - `data class LearnedPlan(val shape: LearnedPlanShape, val streak: Int, val confirmations: Int, val confirmedAtEpochMs: Long) { val pinned: Boolean }`
- Produces (in `LearnedForms.kt`): `object LearnedForms { fun learn(command: String, steps: List<ProposedStep>, folding: TextFolding): LearnedPlanShape?; fun match(form: CommandForm, command: String, folding: TextFolding): List<String>?; fun fill(template: PlanTemplate, slots: List<String>): RawAnswer }`

- [ ] **Step 1: Write the failing tests** — `LearnedFormsTest`, with `PlainTextFolding` and `ProposedStep`s built
  directly (`said(value)` = `SaidBy(USER)`, `model(value)` = `ModelGenerated`):
  1. `a mention the user typed becomes a slot and the rest stays words` — «сотри lumora», `uninstall_app(app = said lumora)`
     → `form.key == "сотри …"`, `template == PlanTemplate(listOf(TemplateStep(uninstall_app, mapOf("app" to Slot(0)))))`.
  2. `a value the model wrote stays a constant` — «будильник на рассвете», `set_alarm(time = model 6:00)` →
     `key == "будильник на рассвете"`, `args == mapOf("time" to Constant("6:00"))`.
  3. `a reference stays a reference` — two steps, the second's `refs = mapOf("path" to StepRef(0, "path"))` →
     `TemplateArg.Ref(0, "path")` on step 1.
  4. `the same words in two arguments are one slot` — «покажи lumora», steps `launch_app(app = said lumora)` and
     `open_app_info(app = said lumora)` → `form.key == "покажи …"`, `form.slotCount == 1`, both args `Slot(0)`.
  5. `words that occur twice are not learned` — «lumora сотри lumora», `said lumora` → `null`.
  6. `a value that cuts a space-separated word is not learned` — «открой lumora-studio», `launch_app(app = said lumora)` → `null`.
  7. `a form with no word of its own is not learned` — «lumora», `launch_app(app = said lumora)` → `null`.
  8. `two slots side by side are not learned` — «открой lumora vemblic», two `launch_app` steps → `null`.
  9. `a form or a constant past its cap is not learned` — a 65-character form → `null`; a 65-character constant → `null`.
  10. `a form takes another value in its slot, as the user typed it` — form «сотри …», «Сотри Vemblic!» → `listOf("Vemblic")`.
  11. `a form does not match a command with a different or a missing word` — «удали Vemblic» → `null`; «сотри» → `null`.
  12. `a form that fits a command two ways does not match` — `CommandForm.parse("найди … в …")`, «найди книгу в
      магазине в центре» → `null`.
  13. `the key round-trips and marks a slot with an ellipsis` — `CommandForm.parse("сотри …") == form` from test 1;
      `parse(form.key).key == form.key`.
  14. `an address slot keeps the user's text whole` — «открой HTTPS://Quillmere.example/Path», `open_url(url = said
      https://quillmere.example/path)` → `key == "открой …"`; `match` on «открой HTTPS://Quillmere.example/Other» →
      `listOf("HTTPS://Quillmere.example/Other")`; `fill` → `RawArg.Text("HTTPS://Quillmere.example/Other")` under `url`.
  15. `a form that cannot match its own command is not learned` — «найди книгу в магазине в центре», a step with
      `query = said книгу в магазине` and a second argument `said центре` → `null` (the form «найди … в …» fits its
      own command two ways; B-2's last clause).

- [ ] **Step 2: Run, expect an assertion red after the types compile** — `tools/gate.sh --scoped :domain:jvmTest`;
  expected `FAILED ::` lines for all fifteen (stub bodies return `null` / empty).
- [ ] **Step 3: Implement** B-1, B-2, B-3 exactly; `fill` maps `Slot(i)` → `RawArg.Text(slots[i])`, `Constant` →
  `RawArg.Text(text)`, `Ref` → `RawArg.Ref(step, key)`, `kind = BridgeRendering.KIND_PLAN`, `question`/`destination`
  `null`. `match` is a backtracking walk over `W`; commands are short, no index is needed.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:domain` 597.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): форма и шаблон выученной команды`.

## Task 2: the store port; record, forget, observe (`:domain`)

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlanStore.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlanUseCases.kt`
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeLearnedPlanStore.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlanUseCasesTest.kt`

**Interfaces:**
- Consumes: Task 1's types; `OperationResult`, `OperationError`.
- Produces:
  - `interface LearnedPlanStore { suspend fun find(form: CommandForm): OperationResult<LearnedPlan?>; suspend fun all(): OperationResult<List<LearnedPlan>>; suspend fun upsert(plan: LearnedPlan): OperationResult<Unit>; suspend fun delete(form: CommandForm): OperationResult<Unit>; fun observeAll(): Flow<List<LearnedPlan>> }`
  - `class RecordLearnedPlanUseCase(store) { suspend fun record(shape: LearnedPlanShape, nowEpochMs: Long = System.currentTimeMillis()): OperationResult<Unit> }` — never throws (rethrows `CancellationException`).
  - `class ForgetLearnedPlanUseCase(store) { suspend fun forget(form: CommandForm): OperationResult<Unit> }`
  - `class ObserveLearnedPlansUseCase(store) { fun observe(): Flow<List<LearnedPlan>> }`
  - `class FakeLearnedPlanStore(var failReads: Boolean = false) : LearnedPlanStore` with `val rows: Map<String, LearnedPlan>` and a `reads` counter.

- [ ] **Step 1: Write the failing tests** — `LearnedPlanUseCasesTest`:
  1. `the first confirmation is a streak of one` — `record(shape, 10)` → `rows[key] == LearnedPlan(shape, 1, 1, 10)`, `pinned == false`.
  2. `the same form and template count up and pin at the threshold` — three records → `streak == 3`, `pinned == true`.
  3. `another template for the same form starts over and is kept` — two records, then one with another template →
     `streak == 1`, `confirmations == 3`, `shape.template ==` the new one.
  4. `a store failure is returned, never thrown` — a failing store → `OperationResult.Failure`.
  5. `forget deletes the form and observe lists what is stored` — `forget(form)` → `rows` empty; `observe().first()`
     before it lists the one plan.
- [ ] **Step 2: Run** — `--scoped :domain:jvmTest`; expected five `FAILED ::`.
- [ ] **Step 3: Implement** the three use cases (B-8's semantics) and the fake.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:domain` 602.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): хранилище выученных команд — порт, запись, забывание`.

## Task 3: replay — through the validator and the binder (`:domain`)

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlans.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/plan/LearnedPlanReplayTest.kt`

**Interfaces:**
- Consumes: Tasks 1–2; `BridgeValidator`, `BridgeFilter.offeringOf`, `ToolDescriptions`, `ToolRegistry`,
  `ToolLevel`, `MentionBinder`, `Binding`, `Interpretation.Act`, `AgentGoal`, `GoalShape.Free`, `ToolEffect`, `ValueOrigin`.
- Produces:
  - `sealed interface Replay { data object Miss : Replay; data class Bound(val act: Interpretation.Act, val form: CommandForm) : Replay }`
  - `interface LearnedPlans { suspend fun replay(command: String): Replay; fun learnable(command: String, steps: List<ProposedStep>): LearnedPlanShape?; companion object { val NONE: LearnedPlans } }` — `NONE` misses and learns nothing.
  - `class DefaultLearnedPlans(store: LearnedPlanStore, registry: ToolRegistry, descriptions: ToolDescriptions, levels: Set<ToolLevel>, binder: MentionBinder, folding: TextFolding) : LearnedPlans`
  - `fun Interpretation.Act.needsCard(): Boolean` (B-7)

- [ ] **Step 1: Write the failing tests** — `LearnedPlanReplayTest`, over the bridge test fixtures (registry with
  `uninstall_app`, `web_search`, `set_alarm`; a fake binder that binds «Vemblic» and finds nothing else):
  1. `a pinned form replays through the validator and the binder with the new words` — «сотри …» pinned; «сотри
     Vemblic» → `Replay.Bound`, `act.steps.single().invocation.args["app"] == Literal("net.fixture.vemblic")`, `form.key == "сотри …"`.
  2. `a form below the threshold does not replay` — streak 2 → `Replay.Miss`.
  3. `a tool no longer offered misses and the entry stays` — levels without `uninstall_app`'s → `Miss`; `store.rows` unchanged.
  4. `a slot the binder cannot bind misses` — «сотри все фотографии» → `Miss`.
  5. `two pinned forms that fit the same command miss` — «найди … в …» and «найди книгу в …» both pinned, «найди
     книгу в магазине» → `Miss`.
  6. `an unreadable store misses` — `failReads = true` → `Miss`.
  7. `a card is needed for an owed pick or model text going out, not for the user's words going out` — three acts:
     a pick owed → `true`; `web_search` with `query` `ModelGenerated` → `true`; with `query` `SaidBy` → `false`.
- [ ] **Step 2: Run** — `--scoped :domain:jvmTest`; expected seven `FAILED ::`.
- [ ] **Step 3: Implement** B-4, B-5, B-7: `replay` = `all()` → pinned → `match` each → exactly one → `fill` →
  `validate` (`Valid` with `Proposed`, else miss) → `bind(AgentGoal(text = command, shape = GoalShape.Free(command)), steps)`
  (`Bound`, else miss) — the goal step 7 builds. `learnable` = `LearnedForms.learn(…, folding)`.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:domain` 609.
- [ ] **Mutations — two of spec §10 criterion 9's three separate legs (streak, re-resolve; consent is Task 5's):**
  - M3-1 (silent test 3, `mutation-prover`) — on a validator rejection, `DefaultLearnedPlans` deletes the entry →
    RED exactly `LearnedPlanReplayTest > a tool no longer offered misses and the entry stays`.
  - M3-2 (streak, `mutation-prover`) — `replay` drops the `pinned` filter → RED exactly `> a form below the
    threshold does not replay`.
  - M3-3 (re-resolve, `mutation-prover-deep` designs the plant) — `replay` keeps the first `Bound` act per form and
    returns it again without binding → RED predicted `> a pinned form replays through the validator and the binder
    with the new words` and `> a slot the binder cannot bind misses` (the prover fixes the exact set; a mismatch is
    a finding).
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): повтор выученной команды — тот же валидатор и привязчик`.

## Task 4: the route — step 2c, and what a fresh reading teaches (`:domain`)

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt` (constructor
  `:136-147`, after 2b `:191`, step 7 `:220-221`, class KDoc «Order of decision»)
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/CommandOutcome.kt` (`:76`, `:84`)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 3's `LearnedPlans`, `Replay`, `needsCard`.
- Produces: `RouteCommandUseCase(…, startAgentSession, learnedPlans: LearnedPlans = LearnedPlans.NONE)`;
  `CommandOutcome.Interpreted(interpretation: Interpretation, learnable: LearnedPlanShape? = null, learned: CommandForm? = null)`;
  `CommandOutcome.AgentSessionStarted(id: AgentSessionId, learned: CommandForm? = null)`.

- [ ] **Step 1: Write the failing tests** — in `RouteCommandUseCaseTest`, with a `DefaultLearnedPlans` over the
  fake store and the test binder:
  1. `a learned command starts its session without a card and the model is never asked` — «сотри …» pinned,
     online with provider; «сотри Vemblic» → `AgentSessionStarted(_, learned = CommandForm.parse("сотри …"))`;
     model planner calls `== 0`.
  2. `in local-only mode a learned command still runs and the model is never asked` — same, `localOnlyMode = true`.
  3. `a learned command that owes a pick or shows model text gets the card` — two «Vemblic» apps → `Interpreted`
     with one pick, `learned == CommandForm.parse("сотри …")` and `learnable == null`; the model-text case →
     `Interpreted` with `learned` set; model calls `== 0`.
  4. `a learned command that does not bind goes on down the chain` — «сотри все фотографии» online → the model is
     asked once; local-only → `Message(UnderstandingLocalOnly)`.
  5. `a fresh model reading carries what it teaches, and one that cannot be learned carries nothing` — step 7
     `Proposed` for «поищи орвелин» → `Interpreted.learnable.form.key == "поищи …"`; for «lumora» →
     `learnable == null`.
  6. `a text FastPath or 2b decided never consults the learned plans` — a pinned form that would match a
     2b-claimed text: 2b's session starts; `store.reads == 0`.
- [ ] **Step 2: Run** — `--scoped :domain:jvmTest`; expected six `FAILED ::`; every other `RouteCommandUseCaseTest`
  test green (default `NONE`).
- [ ] **Step 3: Implement** B-6, B-7, B-8 (route half); rewrite the class KDoc's order of decision with 2c and why
  it is `DOC-ADL-3` applied.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:domain` 615.
- [ ] **Mutation (silent tests 1, 2), `mutation-prover`:** M4-1 — step 2c calls `modelPlanner.plan(command)` and
  discards it before starting → RED exactly tests 1 and 2.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): шаг 2c — выученная команда без модели и без карточки`.

## Task 5: Room — `learned_plans`, schema 5, and the seam (`:data:repository`)

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/LearnedPlanEntity.kt`
  (`learned_plans`, PK `form`; `template_json` TEXT, `streak` INT, `confirmations` INT, `confirmed_at` INT)
- Create: `…/db/dao/LearnedPlanDao.kt`, `…/db/migrations/Migration4To5.kt`, `…/db/LearnedPlanStoreImpl.kt`
- Modify: `…/db/SidrDatabase.kt` (`version = 5`, entity, `learnedPlanDao()`, KDoc), `…/db/RoomColumnNames.kt`
  (`LEARNED_PLANS`, `ALL`, `TABLE_NAMES`, `BY_TABLE`), `…/db/migrations/README.md`
- Create: `data/repository/schemas/com.sidr.launcher.data.repository.db.SidrDatabase/5.json` (generated by the build)
- Modify: `data/repository/build.gradle.kts` (`testImplementation(libs.room.testing)`, schemas as test assets — B-14)
- Modify: `data/repository/src/androidTest/…/db/MigrationTest.kt` (`v4_to_v5…`, `v1_to_v5_migrationRunway_composes`; **not run**)
- Test: `…/db/LearnedPlanStoreImplTest.kt`, `…/db/Migration4To5Test.kt`, `…/db/RoomColumnNamesGuardTest.kt`,
  `…/agent/LearnedPlanSeamTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4; `DeviceMentionBinder`, `AuthoredToolDescriptions`, `IcuTextFolding` (Robolectric), the
  engine (`StartAgentSessionUseCase`, `RunAgentSessionUseCase`) as `AgentActingSeamTest` builds them.
- Produces: `LearnedPlanStoreImpl @Inject constructor(dao: LearnedPlanDao, @IoDispatcher …) : LearnedPlanStore`;
  `Migration4To5`; `SidrDatabase.learnedPlanDao()`.

- [ ] **Step 1: Write the failing tests:**
  - `LearnedPlanStoreImplTest` (Robolectric, in-memory Room): 1 `a learned plan round-trips with its template`
    (all three `TemplateArg` kinds); 2 `past the cap an unpinned row goes first, then the oldest confirmation`
    (200 rows, the 201st upsert → the lowest-streak, oldest unpinned row absent, 200 rows); 3 `a pinned command
    survives 200 one-off readings` (one pinned row confirmed first, then 200 streak-1 upserts → the pinned row is
    present); 4 `a row whose template cannot be read is deleted when it is met` (a raw row with `template_json =
    "{"` → `all()` omits it and the table no longer holds it; `find` → `Success(null)`); 5 `observeAll follows upsert
    and delete`.
  - `Migration4To5Test` (Robolectric): `a version-4 database with rows migrates to 5, keeps every row and opens`
    — one row in `resolution_preferences` and in `aliases` before; equal after; `learned_plans` empty and writable.
  - `RoomColumnNamesGuardTest > learned plans table is inventoried and needs no exemption` —
    `BY_TABLE["learned_plans"] == setOf("form", "template_json", "streak", "confirmations", "confirmed_at")`;
    `APPROVED_SENSITIVE_COLUMNS == setOf("resolution_preferences.query")`.
  - `LearnedPlanSeamTest` (production validator, binder, Room store, engine): 1 `three confirmed readings, then the
    fourth replays on the device and the model is not asked`; 2 `a learned uninstall stops at consent every time`
    — «сотри …» pinned; two replays → each run ends `AwaitingConsent` on `uninstall_app` with `CONFIRM`.
- [ ] **Step 2: Run** — `--scoped :data:repository:testDebugUnitTest`; expected nine `FAILED ::`.
- [ ] **Step 3: Implement** entity, DAO (`find`, `all`, `upsert`, `delete`, `observeAll`, `count`, an eviction
  query ordered by `streak`, then `confirmed_at`), `Migration4To5` (`CREATE TABLE IF NOT EXISTS learned_plans (…)`
  only), the store (template via a `@Serializable` DTO; an unreadable row deleted when met — B-9), `version = 5`;
  build once so KSP writes `5.json`; B-14 (fallback if `MigrationTestHelper` cannot run under Robolectric — ledger
  it). `MigrationTest` cases written, not run.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:data:repository` 477.
- [ ] **Mutation (consent, the third leg of spec §10 criterion 9, `mutation-prover-deep`):** M5-1 — step 2c, after
  `startPlanned`, pre-grants the new session's consent checkpoints (`AgentSessionStore.recordConsentIfPending`)
  before returning → RED exactly `LearnedPlanSeamTest > a learned uninstall stops at consent every time`. (Not a
  lowered step risk: the engine's floor `maxOf(step.risk, registry risk)` makes that plant vacuous — review S3.)
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): таблица learned_plans — схема 5, миграция, хранилище`.

## Task 6: wiring, and the payload guard's fifth source (`:app`)

**Files:**
- Modify: `app/src/main/java/com/sidr/launcher/di/MemoryBindsModule.kt` (bind `LearnedPlanStoreImpl`),
  `MemoryProvidesModule.kt` (the three use cases), `BridgeProvidesModule.kt` (`provideLearnedPlans(store, registry,
  descriptions, binder, folding) = DefaultLearnedPlans(…, BridgeFilter.ANDROID_OFFERED_LEVELS, folding)`; the route
  provider takes `learnedPlans: LearnedPlans` with no default), `DatabaseModule.kt` (`addMigrations(…, Migration4To5)`,
  `provideLearnedPlanDao`)
- Modify: `app/src/test/java/com/sidr/launcher/agent/BridgePayloadGuardTest.kt`, `ProductionGraph.kt`

**Interfaces:**
- Consumes: Tasks 3–5.
- Produces: the Hilt graph's `LearnedPlans`, `LearnedPlanStore`, `RecordLearnedPlanUseCase`, `ForgetLearnedPlanUseCase`,
  `ObserveLearnedPlansUseCase`.

- [ ] **Step 1: Write the failing test and rewrite two at constant count** — the guard's route gets a
  `DefaultLearnedPlans` over a store holding a **pinned** «плексорин …» with a constant `SNTL-LEARNED-CONST` that
  none of the four commands matches:
  - `sources` gains `"learned plans" to listOf("плексорин", "SNTL-LEARNED-CONST")`; `no private source reaches any
    body, in any transport form, or in the retry` scans it unchanged;
  - `the controls - the device read every planted source…` gains: the learned store was read once per command;
  - new: `a learned command sends nothing, and the same command unlearned is asked once` — a second store with
    «тирмалон …» pinned, «тирмалон lumora» → zero bodies; the same command over an empty store → **one first ask**
    (count bodies whose `messages.size == 2`, as `:629` does — the mock rejects every first ask, so the raw body
    count is two).
- [ ] **Step 2: Run** — `--scoped :app:testDebugUnitTest`; expected `FAILED ::` on the new test and the control.
- [ ] **Step 3: Implement** the wiring; `ProductionGraph` compile-follows.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:app` 85.
- [ ] **Mutations (privacy, `mutation-prover-deep`):** M6-1 — inside `provideRouteCommandUseCase` (where
  `learnedPlans` and its store are in reach — `provideModelPlanner`'s signature, which the guard calls at `:239`, does
  not reach them), the model planner is wrapped so the command carries every stored form's key → RED exactly `no
  private source reaches any body…`, `an OpenRouter body adds only the budget's reasoning cap and carries no private
  source` (it scans `sources` too, `:422-425`) and `the controls…` (the user content must equal the command,
  `:502-507`); M6-2 — step 2c skipped (`learnedPlans` not consulted) → RED exactly the control and `a learned
  command sends nothing…`.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): проводка и пятый приватный источник гварда нагрузки`.

## Task 7: «Выполнить» teaches; the mark and «Забыть» on the session card (`:feature:launcher`)

**Files:**
- Modify: `feature/launcher/…/LauncherCommandSession.kt` (`applyOutcome` `Interpreted` keeps `learnable` and
  `learned` beside the card; `runInterpretation` records after `startPlanned` succeeds and emits
  `AgentSessionStarted(id, learned)`; `dismissInterpretation` and `showRemainder` clear both; `AgentSessionStarted`
  passes `learned` to `attach`)
- Modify: `feature/launcher/…/agent/LauncherAgentSession.kt` (`attach(id, learned: CommandForm? = null)`;
  `val learned: StateFlow<CommandForm?>`; `fun forgetLearned()`; B-10)
- Modify: `feature/launcher/…/agent/AgentSessionSurface.kt` (the line and the button), `LauncherScreen.kt` (the
  surface's only caller, `:308-321`), `LauncherViewModel.kt` (two use cases, wiring),
  `res/values{,-ru,-tr}/strings.xml` (`launcher_agent_learned_mark`, `launcher_agent_learned_forget`)
- Test: `LauncherViewModelTest`, `agent/LauncherAgentSessionTest`, new `agent/AgentSessionSurfaceLearnedTest`

**Interfaces:**
- Consumes: Task 2's `RecordLearnedPlanUseCase`, `ForgetLearnedPlanUseCase`; Task 4's outcome fields.
- Produces: `LauncherAgentSession.learned`, `forgetLearned()`; `LauncherViewModel.onForgetLearnedCommand()`.

- [ ] **Step 1: Write the failing tests:**
  1. `LauncherViewModelTest > Run on a fresh model card records what it teaches once the session started` —
     `FakeLearnedPlanStore.rows["поищи …"]?.streak == 1` after Run.
  2. `LauncherViewModelTest > a dismissed card, a session that did not start and a remainder card record nothing`
     — three cases → `rows` empty.
  3. `LauncherAgentSessionTest > a learned session says so and Forget deletes its form` — `attach(id, form)` →
     `learned.value == form`; `forgetLearned()` → store row gone, `learned.value == null`.
  4. `LauncherAgentSessionTest > the mark stays with its session only` — a different session shown → `null`;
     `restoreOnStart` → `null` (fork 4-5's limit, named in the test's KDoc).
  5. `AgentSessionSurfaceLearnedTest > the learned line and Forget are drawn only for a learned session`.
  6. `LauncherViewModelTest > a learned command that needed the card keeps its mark after Run` — an `Interpreted`
     with `learned` set → Run → the session on screen's `learned == form`; nothing recorded.
- [ ] **Step 2: Run** — `--scoped :feature:launcher:testDebugUnitTest`; expected six `FAILED ::`.
- [ ] **Step 3: Implement** B-7 (launcher half), B-8 (launcher half), B-10; the seven `LauncherViewModel(` test
  sites compile-follow.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:feature:launcher` 287.
- [ ] **Mutations (silent tests 2, 4), `mutation-prover`:** M7-1 — `runInterpretation` records before `startPlanned`
  regardless of its result → RED exactly test 2; M7-2 — `show` keeps the mark across session ids → RED exactly test 4.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): «Выполнить» учит; пометка и «Забыть» на карточке сессии`.

## Task 8: the learned-commands screen, reached from Settings

**Files:**
- Create: `feature/launcher/…/learned/LearnedCommandsViewModel.kt`, `…/learned/LearnedCommandsScreen.kt`
- Modify: `core/common/…/navigation/Routes.kt` (`LearnedCommands`), `app/…/navigation/AppNavHost.kt`,
  `feature/settings/…/SettingsScreen.kt` + `SettingsViewModel.kt` (`openLearnedCommands()`), strings: launcher
  `launcher_learned_commands_title`, `launcher_learned_commands_empty`, `launcher_learned_commands_learning`
  (`%1$d / %2$d`), `launcher_learned_commands_learned`, `launcher_learned_commands_failed` (the unreadable store,
  with a retry); settings `settings_learned_commands_title` — `en`/`ru`/`tr`
- Test: `feature/launcher/…/learned/LearnedCommandsViewModelTest.kt`, `feature/settings/…/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2's observe/forget; `PlanStep.line`; a template step drawn as a `PlanStep` whose slot arguments are
  `Literal(SLOT_MARK)`, constants `Literal(text)`, references `FromStep`.
- Produces: `LearnedCommandsUiState(commands: List<LearnedCommandUiModel>, isLoading, failed)`,
  `LearnedCommandUiModel(form: String, steps: List<PlanStep>, streak: Int, threshold: Int, pinned: Boolean)`.

- [ ] **Step 1: Write the failing tests:**
  1. `LearnedCommandsViewModelTest > every stored command is listed with its form, its steps and its streak`.
  2. `LearnedCommandsViewModelTest > forget deletes the command`.
  3. `LearnedCommandsViewModelTest > an unreadable store says so and offers a retry`.
  4. `SettingsViewModelTest > the learned commands row opens its screen` —
     `NavigationEvent.NavigateTo(Routes.LearnedCommands.ROUTE)`.
- [ ] **Step 2: Run** — `--scoped :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest`; four `FAILED ::`.
- [ ] **Step 3: Implement** B-11.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:feature:launcher` 290, `:feature:settings` 35.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): экран выученных команд — видно и удаляемо`.

## Task 9: `:consumer:jvm` parity

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/JvmLearnedPlanStore.kt`
- Modify: `consumer/jvm/…/ConsoleHarness.kt` (the learned-plan file built beside the session file, where that is
  built, `:105`; 2c before `interpret`; record after a card's «yes» once started; KDoc). `Main.kt` passes only
  `root` and is not touched.
- Test: `consumer/jvm/src/test/…/store/JvmLearnedPlanStoreTest.kt`, `ConsoleHarnessTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3 (`DefaultLearnedPlans` over the sandbox levels, `SandboxToolDescriptions`, `SandboxMentionBinder`, `JdkTextFolding`).
- Produces: `JvmLearnedPlanStore(file: Path) : LearnedPlanStore`.

- [ ] **Step 1: Write the failing tests:**
  1. `JvmLearnedPlanStoreTest > a learned plan survives a new store over the same file`.
  2. `JvmLearnedPlanStoreTest > an unreadable file is a failure, not an empty memory` — `all()` → `Failure`.
  3. `ConsoleHarnessTest > the fourth ask of a confirmed form runs without the model` — three asks answered «yes»,
     the fourth → the fake sender's request count stays 3; the session ran.
  4. `ConsoleHarnessTest > a learned delete still asks consent` — the replayed `delete_file` prompts; «no» deletes nothing.
- [ ] **Step 2: Run** — `--scoped :consumer:jvm:test`; four `FAILED ::`.
- [ ] **Step 3: Implement** B-13.
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:consumer:jvm` 97.
- [ ] **Mutations (`mutation-prover`):** M9-1 — the replay path answers the gate «yes» itself → RED exactly test 4;
  M9-2 — `ask` calls the model before 2c → RED exactly test 3.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): :consumer:jvm — выученные команды из файла`.

## Task 10: the memory stays on the device — Auto Backup and device transfer (`:app`)

**Files:**
- Create: `app/src/main/res/xml/data_extraction_rules.xml` (`<data-extraction-rules>`: `<cloud-backup>` and
  `<device-transfer>`, each `<exclude domain="database" path="."/>` and `<exclude domain="file" path="datastore/"/>`)
- Create: `app/src/main/res/xml/backup_rules.xml` (`<full-backup-content>` with the same two excludes)
- Modify: `app/src/main/AndroidManifest.xml` (`<application>`: `android:dataExtractionRules="@xml/data_extraction_rules"`,
  `android:fullBackupContent="@xml/backup_rules"`; `allowBackup` stays `true`; one comment naming `DOC-AML-4`)
- Test: `app/src/test/java/com/sidr/launcher/doctrine/BackupRulesGuardTest.kt`

**Interfaces:** none produced; consumes the manifest and the two XML files as text (parsed, attributes compared by
equality), and every module's `src/main/**/*.kt` (B-17).

- [ ] **Step 1: Write the failing tests** — `BackupRulesGuardTest`:
  1. `the manifest points backup and device transfer at the rules` — `<application>`'s `android:dataExtractionRules
     == "@xml/data_extraction_rules"`, `android:fullBackupContent == "@xml/backup_rules"`.
  2. `cloud backup and device transfer leave out every database and every DataStore file` — `<cloud-backup>` and
     `<device-transfer>` each hold exactly the excludes `{(database, .), (file, datastore/)}` and no `<include>`.
  3. `before Android 12 the same two are left out` — `<full-backup-content>` holds the same set and no `<include>`.
  4. `every store the app opens lives where the rules leave it out` — over every module's `src/main/**/*.kt`
     (`build/` excluded): a storage-opening call other than `Room.databaseBuilder(` and `preferencesDataStoreFile(`
     — `getSharedPreferences(`, `openFileOutput(`, `dataStoreFile(`, `getDir(`, `filesDir`, `externalFilesDir`,
     `noBackupFilesDir`, `SQLiteDatabase.openOrCreateDatabase(` — fails with `file:line`; the control: the two
     allowed shapes are found (≥ 1 each), so an empty scan cannot pass.
- [ ] **Step 2: Run** — `--scoped :app:testDebugUnitTest`; expected `FAILED ::` on tests 1–3 (test 4 green on today's tree:
  the two DataStores and the one Room builder are the only stores).
- [ ] **Step 3: Implement** B-16 (the two XML files, the manifest attributes).
- [ ] **Step 4: Run** — `SCOPED GREEN`, `:app` 89.
- [ ] **Mutations (privacy, `mutation-prover-deep`):** M10-1 — `<device-transfer>` loses the database exclude → RED
  exactly test 2; M10-2 — a production file in another module gains `context.getSharedPreferences("x", 0)` → RED
  exactly test 4; M10-3 — the manifest loses `android:fullBackupContent` → RED exactly test 1.
- [ ] **Step 5: Commit** — `feat(agentic-6/A4' ф4): память не уходит в резервную копию — правила бэкапа и гвард`.

## Task 11: close the phase — documents, the floor

- [ ] Boundary gate from a cleared tree after `gradlew --stop`: predicted **1902**, per module as §0.8.
- [ ] ADRs in `ai-context/decisions.md`: «A4′ ф4: кэш выученных планов — форма со слотами, шаблон, схема 5» (B-1…B-14,
  the six forks, spec §10 criterion 9's three mutations and their results, **what this phase starts storing** —
  §0.7 item 6 — and B-16/B-17: the memory left out of Auto Backup and device transfer, the cost the owner accepted)
  and «D3, поправка: выученная команда» (B-15; owner sign-off).
- [ ] Doctrine matrix — every citation the full `` `XTest > name` `` (`DoctrineMatrixGuardTest` reads it):
  `DOC-ADL-3` + `` `RouteCommandUseCaseTest > in local-only mode a learned command still runs and the model is never asked` ``;
  `DOC-AML-1` + the fifth source; `DOC-AML-4` + `` `BackupRulesGuardTest` ``; `DOC-AML-5` + `` `LearnedPlanUseCasesTest` ``, `` `LearnedCommandsViewModelTest` ``;
  `DOC-NYH-1` note (a learned command is a deterministic path); `DOC-ILM-5` — fork 4-5 recorded, third column stays
  `<нет>` unless the owner decides the mark tests it; `DOC-HMA-3`'s marker re-addressed (owner item).
- [ ] `CLAUDE.md` (stage row, «Right now», the D3 hard rule, shipped surface, known debt: the fork 4-5 limit, B-5's
  fail-open, B-2's non-learnable commands, `MigrationTest` unrun, the backup rules `CODE-GREEN` only — no restore or
  transfer seen; the memory does not move to a new phone — §0.7 item 6), `ai-context/current-status.md` (header **and**
  tail), `§HANDOFF` (phase 4's record to the archive, next: the block's acceptance and block ADR), `tools/gate.sh`
  floor → measured total; `ClaudeMdSizeGuardTest` stays green.
- [ ] Commit and push — `docs(agentic-6/A4' ф4): фаза 4 закрыта — пол <N>, документы синхронизированы`.

---

## Self-review against the spec

| Spec | Where |
|---|---|
| §8.1 key not the wording, many-to-one | B-1, B-3; Task 1 tests 1, 10, 13 |
| §8.2 template, re-resolve on replay, invalidation | B-4, B-9; Task 3 tests 1, 3, 4 |
| §8.3 the plan, not the consent | B-7; Task 5 seam test 2; Task 9 test 4 |
| §8.4 streak ≥ 3 | B-8; Task 2 test 2; Task 3 test 2 |
| §8.5 provenance decided | fork 4-5, B-10; Task 7 tests 3–5 |
| §8.6 predictability on a moving registry | §0.5 item 3 (it is the model and the names it stabilises) |
| §8.7 not skipping gates, not caching packages, not touching S2-1/S2-2, not before the 4th | B-4, B-7; the stores untouched; B-8 |
| §9 gate | Global Constraints, §0.8 |
| outside the spec: §0.7 item 6, the owner's «a» (`DOC-AML-4`) | B-16, B-17; Task 10 tests 1–4 |
