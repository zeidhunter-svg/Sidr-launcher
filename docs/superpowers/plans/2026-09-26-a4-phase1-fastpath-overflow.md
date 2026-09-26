# A4′ Phase 1 — FastPath overflow · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: EXECUTED 2026-09-26 — `CODE-GREEN` at 1485 / 0 / 0** (boundary `tools/gate.sh`, cleared
> tree, 557 executed). Tasks 1–4 landed exactly the predicted 1483, every per-task total and module
> delta as predicted; the final-review fix added 2 in `:feature:launcher`. Code commits `7106653` (T1)
> · `efcfb08` (T2) · `0ea0470` (T3) · `eb86c3c` (T4), the final-review fix `bffa146`, then the Task 5 docs commit.
> All 21 mutation runs matched their predicted `FAILED ::` sets name for name. Owner decisions at
> close: doctrine reading **(б)**, `DOC-NYH-4` with the draft wording; §0.6 answers in `§HANDOFF`
> «Фаза 1 A4′». `B15` after: exactly §0.2, recorded in the `B15` document §9.
> **Deviations from the body below, named** (the body is left as written):
> - Two addresses in Task 5 did not match the tree, and execution stopped on both for the owner:
>   `CLAUDE.md`'s baseline paragraph is `:680-720`, not `:684-705` (owner: edit the whole paragraph);
>   the matrix's 2026-09-20 journal rows are `:223-224`, not `:222-223` (a pointer to form only).
> - Task 5 step 1 was taken from Task 4's boundary run (identical content, 1483); the closing
>   boundary run came after every Task 5 edit. `B15` «before» is the harness report from Task 1's
>   boundary run (FastPath not yet changed), not a run at `1f5dbbf` — it matched §0.2 line for line.
> - Task 3's first boundary run went red on the known timing-sensitive `:data:ai-cloud` test only
>   (aborted at 406/557); the re-run from a cleared tree was green at 1477.
> - `CLAUDE.md`'s stage table got a **new** row for phase 1 rather than an edit of phase 0's row.
> - **Not in this plan at all:** the final whole-branch review found that the WEB route chip
>   (`LauncherCommandSession.submitWebSearch`) re-routed its text through FastPath, so Task 2 made the
>   chip refuse exactly the text it withdraws. The owner ruled «fix now»: the chip dispatches
>   `LauncherAction.WebSearch` directly (+2 tests in `:feature:launcher`, mutations MF1–MF2), together
>   with three review minors (KDoc for `tr`/search arm, a no-whitespace guard in `SecondGoal`'s lists,
>   a pinned single installed-apps read — MF3–MF5). Hence 1485, and the gate floor is 1485, not 1483.
>
> Original status line: **written, reviewed twice and corrected, 2026-09-26.** Not started.
> - Branch `launcher--7`, base `1f5dbbf`, gate baseline **1463 / 0 / 0**.
> - **Spec:** [§0, §1, §2, §5, §9, §10 of
>   2026-09-21-a4-runtime-design.md](../specs/2026-09-21-a4-runtime-design.md). It is `ПОЛНА`; the
>   owner approved it section by section on 2026-09-21. **The owner amended §5.1 on 2026-09-26**
>   (fork F1-3, §0.1): the prefix rule is deferred until offline understanding exists.
> - **Form template:** [A4′ phase 0](2026-09-22-a4-phase0-engine-preconditions.md). The record of
>   phase 0 and the owner questions this phase inherits are in `§HANDOFF` of
>   [the track plan](2026-08-18-agentic-track-restart.md), subsection «Фаза 0 A4′».
>
> **Every number and every predicted red set below comes from a run, not from reading.**
> - The code in Tasks 1–4 was built on a scratch copy of the tree (`git archive 1f5dbbf`, §0.7).
> - All five test modules were run against it, and `B15` was re-measured on it.
> - The TDD-red sets were taken against HEAD's use case and matcher.
> - Every mutation was planted and run with this plan's own protocol.
> - Where a set is *derived* from a measured one, because a test does not exist yet at that task's
>   point in the timeline, the derivation is stated.
>
> **Reviews.** Two separate agents reviewed the first design, which still had the prefix rule (§0.8).
> Review B re-executed every step of it from the plan text and matched every prediction. The design
> after F1-3 was re-measured the same way, but by the author only, and §0.8 says so.

**Goal.** FastPath stops claiming confidence the rest of the command did not earn.
- «открой gmail и проверь новые письма» no longer becomes a Play Store search for «gmail и проверь
  новые письма».
- «найди билеты … и сделай бронь» no longer googles the booking.

Both are left *undecided*, so routing step 2b and the model get them. **The deliverable is "stopped
getting a confident wrong answer", not "started working"** (spec §5.3). They start working in phase
3, through the bridge.

**Architecture.** One rule, `SecondGoal` in `:domain` `commonMain`: a conjunction next to a listed
verb means the argument carries a second goal. It is a closed vocabulary (owner fork F1-1). Two arms
consult it:
- **The search arm** (`RuleBasedIntentMatcher`, `:data:repository`) does not claim a query that
  carries a second goal. It gives 0.30, the matcher's own value for a verb it cannot use.
- **The launch arm** (`HandleUserCommandUseCase`, `:domain`) **resolves the claimed launch before the
  confidence gate**.
  - If the whole app name failed to resolve and it carries a second goal, the claim is withdrawn
    (F1-2).
  - If the whole name failed to resolve without a second goal, that is a fact about the world:
    `NoAppFound`, and A0's store plan runs **byte for byte**. That includes «открой youtube music» on
    a phone with YouTube installed (F1-3).

`RouteCommandUseCase` is not in the diff.

**Tech Stack.** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain`
(`commonMain` + `jvmTest`) · JUnit4 + `kotlinx-coroutines-test`.

---

## Global Constraints

Every task's requirements implicitly include this section.

### Running the gate

- **The gate is `tools/gate.sh`** (phase 0 Task 1). Ruling R2: every step that runs tests goes
  through it. Compile-only probes use bare `./gradlew` with
  `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`.
- **Boundary run: `tools/gate.sh` with no arguments.** It closes every task. It clears
  `build/test-results` everywhere, passes `--rerun-tasks`, counts from the JUnit XML, and says
  `GATE GREEN` only at or above its floor (`BASELINE_TESTS = 1463`, `tools/gate.sh:195`).
- **Scoped run: `tools/gate.sh --scoped :module:task`.** It is for TDD steps and mutations, and it
  labels itself `NOT a boundary gate`.
- **A mutation whose red set spans modules is run once per module.** Without `--continue`, Gradle
  starts no new task after the first failure, but tasks already running in parallel finish. So which
  modules a multi-module scoped run reports depends on scheduling, not on module order.
  - Review B measured it: under M4c, the same five-task command left 3 of 4 later test tasks `ABSENT
    from the log` on a warm build, and 1 of 4 right after a boundary gate.
  - `--scoped` refuses `--continue` (`tools/gate.sh:107-115`; measured: `FATAL … (got '--continue')`,
    exit 2).
  - Every prediction below is therefore given per module.
- **The Gradle daemon is ON** (owner decision 2026-09-25, ruling R8).
- **`--rerun-tasks`, never `--rerun`.** Take counts from the XML, never from the console, and never
  pipe through `tail`.

### Reading the results

- **Baseline 1463 / 0 / 0**, per module:

  | Module | Tests |
  |---|---:|
  | `:domain` | 445 |
  | `:consumer:jvm` | 57 |
  | `:data:repository` | 384 (re-measured 2026-09-26 on `1f5dbbf`) |
  | `:feature:launcher` | 200 |
  | `:app` | 61 |
  | the other eight | 316 |

  **Predict this decomposition before every run and compare after.** A total cannot tell "two added"
  from "two added and two lost".
- **`:domain`'s `FAILED ::` lines end in `[jvm]`**, a KMP target suffix — e.g. `SecondGoalTest > a
  conjunction joining two nouns is one query, not two goals[jvm]`. The predicted names below omit it,
  so compare names, not raw lines.
- **A compile-red TDD step prints other `e:` lines besides the one named** (receiver-type mismatches,
  `Cannot infer type`). The named `Unresolved reference` must be among them.
- **Line addresses are at HEAD `1f5dbbf`.** Inside a task, an earlier edit shifts later lines. Every
  instruction also quotes the text it replaces, and that text is what to match.

### The mutation protocol

**One shape for every mutation in this plan, and never `git checkout`** (ruling R1). Each tool shell
is a fresh process, so the helper lives in a file outside the repo and is **sourced in every mutation
invocation**. Save it once per session, exactly as below. The heredoc must stay at column 0: review B
found that an indented copy fails with `plant: command not found`.

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

Each mutation is then **one** shell invocation:

```bash
source "${TMPDIR:-/tmp}/sidr-plant.sh"
F=<file>; MUT="$(mktemp -d -t sidr-mut-XXXXXX)"; cp "$F" "$MUT/orig"
trap 'cp "$MUT/orig" "$F"' EXIT          # restores on error or interrupt (not on SIGKILL — R10)
plant "$F" '<old>' '<new>' && tools/gate.sh --scoped <:module:task>
cp "$MUT/orig" "$F"; cmp "$MUT/orig" "$F" && echo RESTORED && trap - EXIT
```

- **A mutation is proved by WHICH tests failed**, read off the `FAILED ::` lines against the
  predicted set.
- **None of these proves anything:** a red set that differs from the prediction; `PLANT REFUSED`;
  `gradle=1` with **no** `FAILED ::` lines (that is a compile error). In each case, restore, find out
  why, and run it again.
- `RESTORED` must print.
- **Roles (R4, R10).**
  - The implementer writes a task, and the controller commits it. A reviewer checks.
  - Mutations are proved by a different agent (`mutation-prover`), or by the controller in the
    foreground when they are the short, pre-written ones below.
  - Why the foreground: session restarts have killed background agents mid-run, and a killed `trap`
    does not fire.
  - **After any restart, first run `git status --porcelain --untracked-files=all` and
    `git log --oneline -3`.**
- **The last action of any mutation-proving task is a boundary gate from a cleared tree** (R14-44,
  R16). It runs before the commit, never after.

### Fixtures and frozen code

- **Fixtures (R14-43).** No fixture is a real app or package.
  - Installed: "Lumora" (`net.fixture.alpha`) and "Pack and Send" (`net.fixture.parcel`).
  - Not installed: "veltrix", "corvane" and "quendor saga".
  - "lumora studio" extends an installed label *on purpose*: it is F1-3's case.
  - Every expected value is compared **by equality, never by `contains`**, so a shared substring
    cannot blind an assertion.
  - **The owner's `B15` corpus is not a test fixture** (§0.2). It is the measurement, and a test
    written from it would measure the author.
- **Frozen, not touched by this phase:**
  - spec §10 criterion 10: `ActionIds`, `ObservedFact`, `CommandFailure`, `GoalShape`, `ArgType`.
  - **Also frozen by this plan, each checkable with `git diff`:**
    - `RouteCommandUseCase.kt` — spec §5.3: the order of the three states and step 2b do not move.
    - `LauncherIntent`, `CommandOutcome`, `ExecutableAction`, `IntentMatchResult`, `IntentCandidate`
      — no sealed value and no field is added.
    - `IntentActionResolver.kt`, in full. The A0 tool observes `APP_NOT_INSTALLED` through it.
    - `IntentMatchMapper` (§0.6).
- **`:domain` is `commonMain`:** stdlib + coroutines only. Tests live in `jvmTest`.
- **Strings:** this phase adds **none** (§0.3 P9). If a task finds it needs one, it ships
  `en`/`ru`/`tr` in the module's ordinary `values*/strings.xml`, never `strings_locked.xml`.
- **A line predicting what the user will see is a hypothesis** until it has been run once, and is
  written in that tone (§0.6, «Hypotheses»).

### Commits

- **The plan is committed before Task 1.** Its own commit carries this file, the `§HANDOFF`
  paragraph, and F1-3's notes in the spec and the Master Plan. So the `git status` expectations below
  count only a task's own files.
- Commit messages are Russian conventional commits, scope `agentic-6/A4' ф1`, with the trailer
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (R5).
- **The agent commits and never pushes.** The phone and its network are not touched, and
  `connectedAndroidTest` is never run.

## Review Focus

Five input classes the spec implies but does not name. Each is among those most likely to bite
someone using this build, and each is pinned by a test in the task that owns the code.

1. **An installed app whose own label reads like a second goal — "Pack and Send".** It must still
   launch. The second goal is consulted only *after* the whole name failed to resolve.
   → Task 3, `an installed app whose own name carries a conjunction and a verb still launches` (M3b).
2. **An installed brand's variant that is not installed — «открой youtube music», «открой google
   maps» on a phone that labels Maps «Карты».** Both are common on the owner's phone (§0.3 P4). They
   must keep A0's store offer byte for byte (F1-3).
   → Task 3, `a launch naming an installed brand's variant that is not installed is still claimed`;
   Task 4, the (в) test (M4c).
3. **A search whose conjunction joins nouns («рецепт хлеба и соли»), and the commonest how-to shape
   ("how to download and install python").** Both must still run as searches (spec §5.2: «не понижаем
   весь рукав»).
   → Task 1: the noun test (M1a) and the two-verbs test (M1e). Task 2: the matcher keeps 0.90 (M2b).
   Task 4: the noun search still runs at the seam (M4d).
4. **A newly undecided command in a local state.** Local-only, no provider or offline must each give
   an honest message and no model call (`DOC-ADL-3` as amended).
   → Task 4, `in local-only mode the same command says so plainly and consults nothing`.
   - No-provider and offline sit behind the same single `isUndecided()` check
     (`RouteCommandUseCase.kt:159`, `:188-189`), and the seam test proves `LowConfidence` passes
     through it.
   - The router's own tests for those two states drive `Unknown` (`RouteCommandUseCaseTest.kt:142-145`),
     so their evidence here is indirect.
5. **The history row a withdrawn launch leaves behind.** It must carry the confidence the gate acted
   on, not the matcher's 0.90.
   → Task 3, `an unearned launch is recorded with the confidence it earned, not the one it claimed`
   (M3a).

---

## 0. Pre-flight scan of this plan against the tree

### 0.1 · The three owner forks of this phase, resolved 2026-09-26

- **F1-1 and F1-2** were put to the owner before a line of this plan was written, per
  `forks-before-code`.
- **F1-3** came from review A's finding 1 (§0.8). It is the one decision that amends spec §5, and the
  owner made it.

| # | Fork | Owner's call |
|---|---|---|
| **F1-1** | Spec §5.2 says «союз + глагол» but does not say how a *verb* is recognised. No vocabulary in the tree contains «сделай», «скачай», «проверь», "write", "transfer" or "check" (§0.3 P5). Three options were put: conjunction + a closed list; the conjunction alone (downgrades «рецепт хлеба и соли»); "the clause after the conjunction is itself a product command" (catches **0** corpus rows) | **Conjunction + a closed per-locale list** (`SecondGoal`, Task 1). The option carried a caveat, repeated here. The list was written after the corpus had been read, so the rows it catches are **calibration, not evidence**. And the `intent_match` history cannot validate it, because SEARCH text is redacted there (§0.3 P6) |
| **F1-2** | Spec §5.1's prefix rule changes 2 of the 7 launch rows on the owner's 144 apps. The spec's own example for the second-goal rule, `and transfer money`, comes from a *launch* row | **The second goal applies to the launch arm too, but only after the whole name failed to resolve.** An installed app whose label reads like a second goal still launches. **Cost:** «открой uber и закажи такси», with Uber not installed, no longer offers the store |
| **F1-3** | Review A's probe, on the real matcher, resolver and use case: the prefix rule also withdraws A0's store offer for **an installed brand's variant that is not installed** — «открой youtube music», "open whatsapp business", "open google earth", «открой сбербанк онлайн». On the owner's phone, Google's apps carry Russian labels («Карты», «Диск», «Переводчик»), so «открой google maps» falls into the same class. Spec §5.1's premise, «меняется ровно тот случай, где разбор был неверен», is therefore false. To deterministic code, «youtube music» and «gmail новые письма» have the same shape; only the store's catalogue or a model can tell them apart. On the corpus, the prefix rule catches **no row by itself**: both rows it catches also carry a listed verb | **Drop the prefix rule now, and bring it back with offline understanding.** Phase 1 keeps the second-goal rule only; the same 7 of 13 rows change, and brand variants keep the store offer. **Trigger:** when the launcher has offline understanding (Master Plan row `B16`, a local model), the prefix rule returns, because its withdrawn commands then reach a model even offline. Its prototyped, mutation-proved code is kept in **Appendix A**. **Recorded alternative without a model:** a choice card «Открыть YouTube / Найти "youtube music" в магазине». It is right in both cases, offline, today, but it is a new UI element, which spec §11 excludes from A4′. **Named limit:** the verbless compound «открой gmail новые письма» keeps today's store offer with a wrong name (the corpus has 0 such lines) |

**What F1-3 removes from spec §5.4:** mutation **(б)**, «проверка префикса убрана», has no rule left
to remove. (а) and (в) stand, and §10 criterion 1's "two separate mutations" is (а) + (в).

### 0.2 · The numbers this phase stands on, re-measured

**`B15`, re-run 2026-09-26 on `1f5dbbf`** — a scoped `:data:repository` run, 384 tests, green. The
git-ignored inputs are present locally. Corpus 25, registry 243 (223 shortcuts).

**Identical to spec §1:**

| Category | Count |
|---|---:|
| `PLANNED` | 0 |
| `HANDLED_BY_FASTPATH` | **13** |
| `DECLINE_NO_MATCH` | **9** |
| `DECLINE_DYNAMIC_UNQUALIFIED` | **3** — the three false positives `B15` §7.1 already unmasked |
| ambiguity | **0** |

**Measured versus read:**
- *Measured:* for 13 lines FastPath decided — 7 `Message(NoAppFound)` and 6 `Executed`, all
  `SEARCH[WEB]`. The harness printed what each one did.
- *Read:* "12 of 13 were decided wrongly" is a human judgement over that output (spec §1, `B15` §7.5).
  This plan does not turn it into a number.
- *What this phase measures instead:* which of the 13 lines **stop being claimed**. That is
  mechanical.

**The 13 rows against this plan's rule, measured on the prototype (§0.7):**

| # | Line (normalized) | Today | Second goal | After phase 1 |
|---|---|---|---|---|
| 1 | открой gmail и проверь новые письма | NoAppFound → store | «и проверь» ✓ | **LowConfidence** |
| 2 | open protonmail and check for messges from anthropic | NoAppFound → store | «and check» ✓ | **LowConfidence** |
| 3 | открой календарь и поставь урок на завтра в восемь | NoAppFound → store | «и поставь» ✓ | **LowConfidence** |
| 4 | open the terminal and write a script | NoAppFound → store | «and write» ✓ | **LowConfidence** |
| 5 | открой новости в телеграмм | NoAppFound → store | ✗ (Telegram's label is «Plus») | unchanged — **named limit** |
| 6 | открой онлайн-банк | NoAppFound → store | ✗ | unchanged — **named limit**; arguably A0 behaving as designed |
| 7 | open online-banking app and transfer money to mom | NoAppFound → store | «and transfer» ✓ | **LowConfidence** |
| 8 | найди дешевые билеты в бишкек и сделай бронь | search executed | «и сделай» ✓ | **LowConfidence** |
| 9 | найди в интернете лекцию по rust | search executed | ✗ | unchanged — **named limit** (no stop-words, §5.2) |
| 10 | найди лучший рецепт шашлыка | search executed | ✗ | unchanged — **correct**, and must stay |
| 11 | найди классный фильм на youtube | search executed | ✗ | unchanged — **named limit** |
| 12 | найди в интернете книгу по языку лисп и скачай ее | search executed | «и скачай» ✓ | **LowConfidence** |
| 13 | найди мне работу | search executed | ✗ | unchanged — **named limit**, spec §5.2 by name |

**7 of 13 stop being claimed.** F1-3 did not change the count: rows 1 and 3 — the only ones the
prefix rule caught — carry a listed verb too.

**`B15` after phase 1, measured on the prototype:**

| Category | Before | After |
|---|---:|---:|
| `PLANNED` | 0 | 0 |
| `SHADOWED_BY_FASTPATH` | 0 | 0 |
| `HANDLED_BY_FASTPATH` | 13 | **6** |
| `DECLINE_DYNAMIC_UNQUALIFIED` | 3 | 3 |
| `DECLINE_NO_MATCH` | 9 | **16** |
| every other category | 0 | 0 |
| declined, any reason | 12 (48 %) | **19 (76 %)** |
| of which "ambiguity" (all three are known false positives) | 3 | 3 |

**The rise from 48 % to 76 % is the phase working, not failing.**
- The seven rows used to be answered confidently and wrongly.
- Now they reach step 2b, where no tool matches (`NoPlan`), and then the model or an honest
  local-state message.
- `PLANNED` stays 0, because no registered tool matches any of them (P8).

Task 5 records this in the `B15` document, so that nobody reads the 76 % as a regression.

### 0.3 · Facts read out of the tree, with addresses at HEAD

**(P1) The spec's mechanism is where it says it is.**
- `HandleUserCommandUseCase.handle` executes inside itself: `mapExecution` at `:108-112`, and `:109`
  maps `ActionExecutionResult.Success` to `CommandOutcome.Executed`.
- The gate at `:61-70` runs before resolution at `:80`, and its own comment at `:61` declares that
  order as the design.
- So `Executed` reaches `RouteCommandUseCase` after the fact (`RouteCommandUseCase.kt:111`), and a
  repair can only sit **before** the gate.

**(P2) Where each arm claims its 0.90.**
- Launch: `RuleBasedIntentMatcher.kt:114-118`, unconditionally.
- Search: `:175-181`, unconditionally.
- The resolver runs after the gate (`HandleUserCommandUseCase.kt:80`) and compares whole labels,
  lowercased with `Locale.ROOT` (`IntentActionResolver.kt:44-45`).
- One match is a launch, several are `AmbiguousAppAction`, none is `NoAppFound` (`:47-63`).

**(P3) `IntentActionResolver` is not touched.** The A0 `launch_app` tool observes
`APP_NOT_INSTALLED` through the unchanged chain `ExecuteActionUseCase → IntentActionResolver →
ActionExecutor` (`CLAUDE.md`, Contract table). Since F1-3 the phase needs nothing new from it.

**(P4) The owner's installed apps (`apps.jsonl`, 144), as they bear on the rule.**
- Gmail (`com.google.android.gm`) and Календарь are installed.
- Proton Mail's label is **two words**.
- Telegram is installed as **«Plus»** (`org.telegram.plus`).
- **Google** (`com.google.android.googlequicksearchbox`) is installed, and its apps carry Russian
  labels: «Карты», «Диск», «Переводчик». So «открой google maps» today is `NoAppFound`, and A0 offers
  the store. F1-3 keeps that.
- YouTube, WhatsApp and СберБанк are installed. Their variants — Music, Business, Онлайн — are not.
- Two apps share the label «Сообщения».

**(P5) No vocabulary in the tree knows a second-goal verb.**
- «сделай», «скачай», «проверь», «напиши», «отправь», "write", "transfer" and "check" appear in
  neither `RuleBasedIntentMatcher.kt` nor `ToolVocabulary.kt`.
- «поставь» appears only inside two three-word triggers: «поставь таймер на» (`ToolVocabulary.kt:219`)
  and «поставь будильник на» (`:238`).
- F1-1 was a real fork, not a lookup.

**(P6) `intent_match` sees only the launch arm, and it keeps the launch arm's second clauses
verbatim.**
- `IntentMatchMapper.kt:15-18` redacts SEARCH and UNKNOWN text to a placeholder.
- LAUNCH_APP text is kept, on the premise at `:10-13` that it "resolve[s] to a closed vocabulary".
  **B15 shows that premise false for launch remainders.**
- For the owner (§0.6, not decided here): the history can check only the launch arm. With
  `usageHistoryEnabled` on, the second clauses a user types after a launch verb are persisted.
- After Task 3, a withdrawn launch is recorded as LAUNCH_APP at **0.30**. The matcher never emits a
  `LaunchAppIntent` at 0.30, so `(LAUNCH_APP, 0.30)` picks out exactly phase 1's withdrawals.
- **Query it with a tolerance.** `recordMatch` stores `confidence.toDouble()`
  (`HandleUserCommandUseCase.kt:134`), so 0.30f reads back as 0.3000000119… and `= 0.3` returns
  nothing.

**(P7) Two KDocs state a premise this phase falsifies.** Task 3 corrects both, and does not act on
what they now invite.
- `ToolSelector.kt:68-78` rejects stripping a launch verb *on evidence*: a verb-led command "can never
  come back `Unknown`/`LowConfidence`".
- `ToolSelector.kt:101-102` repeats that such a command "never reaches this selector at all".
- `ToolVocabulary.kt:49-51` says "a trigger colliding with a FastPath verb never reaches the planner".
- After Task 3, a verb-led command does reach the selector when its app name carries a second goal.
- Whether verb stripping would now convert such commands is for phase 3 and the owner. Spec §11 lists
  replacing `ToolSelector` as a non-goal.

**(P8) A newly undecided verb-led command cannot select a registered tool.**
- No authored trigger begins with a FastPath verb. `ToolVocabularyReachabilityTest` holds that, and
  `ToolVocabulary.kt:49-62` records the six triggers that died on it.
- `ToolSelector` rule 4 would need «открой»/"open" to be a name or qualifier token of a shortcut.
- Measured on the prototype: `PLANNED` stays 0.

So phase 1 opens no new path to an **act**. It opens a path to the **model**, which is the point
(P12).

**(P9) Zero new strings.** `CommandOutcome.LowConfidence` → `CommandFeedback.LowConfidence`
(`LauncherCommandSession.kt:276-277`) → `launcher_feedback_low_confidence`
(`LauncherPresentation.kt:39`), already in `en`/`ru`/`tr`. The local states use the existing
`launcher_understanding_*` keys.

**(P10) One production confidence policy.** `IntentProvidesModule.kt:44` binds
`DefaultIntentConfidencePolicy()`, whose suggest threshold is 0.50 (`DefaultIntentConfidencePolicy.kt:5`).
The withdrawal value, 0.30, sits below it, so the gate answers `LowConfidence`, not `Suggest`. That
is a coupling. It is named in the constant's KDoc, not guarded.

**(P11) The tests that hold A0, measured by breaking A0 on purpose.** On the scratch copy, every
`NoAppFound` was turned into `LowConfidence` (§0.7, run E1). It reddened:
- `:domain`, **12**: `HandleUserCommandUseCaseTest` 1, `RouteCommandUseCaseTest` 5,
  `AgentEgressSentinelGuardTest` 6.
- `:feature:launcher`, **2**: both in `LauncherViewModelTest`.
- `:data:repository`, `:app` and `:consumer:jvm`: **0**.

All of these drive **one-word** names («убер»). None of them crosses the real matcher above a router:
`RouteCommandUseCaseTest` uses `FakeIntentMatcher` (`:78`). That is why Task 4's seam test exists.

**(P12) Egress changes, and it is the intended change.**
- With a provider and a network, the seven rows now reach `RouteCommandUseCase` step (7). Their raw
  text goes to the model, where before it stayed on the device (the A0 plan) or went to the browser
  (the search).
- It is not a widening of `OutboundContextPolicy`: it is the channel every undecided command already
  uses, and exactly what ADR 1/4 says FastPath must not filter.
- In the three local states, nothing new leaves the device.

**(P13) The doctrine matrix counts its rows, and a guard holds the count.**
- The matrix declares `**Правил: 30.**`, and `DoctrineMatrixGuardTest.kt:220` fails on a mismatch.
  The table has 30 rows.
- `DOC-NYH-1` is at `sidr-doctrine-matrix-v1.0.md:111`. It is anchored by **two** tests,
  `RouteCommandUseCaseTest` · `IntentConfidencePolicyTest`.
- `CLAUDE.md:33-34` says "30 `DOC-*` rules".

**(P14) `recordMatch` is launched before the gate** (`HandleUserCommandUseCase.kt:59`). Task 3
computes the earned confidence first, so the row carries what the gate acted on.

### 0.4 · Where the spec and the tree diverge, and what each changes

| # | Spec says | Tree / measurement says | Changes |
|---|---|---|---|
| S1 | §5.2 «союз + глагол» | "verb" is undefined; no vocabulary has one (P5) | F1-1 → Task 1 |
| S2 | §5.2 lists `and transfer money` as a search-arm example | it is row 7, a **launch** row | F1-2 → Task 3 |
| S3 | §5.1: "only the misparse case changes" | false: an installed brand + an uninstalled variant has the same shape (review A) | F1-3 → prefix rule deferred; spec §5.1 amended by the owner |
| S4 | §5.4 (б) | no prefix rule left to remove | void; §0.1 |
| S5 | §5.3 names the test `all three causes at once — only the outermost is reported` | its real name has a hyphen, `…at once - only…` (`RouteCommandUseCaseTest.kt:236`) | nothing; the test is untouched |
| S6 | §9's gate command includes `--no-daemon` | retired by the owner 2026-09-25 (R8); the gate is `tools/gate.sh` | Global Constraints |
| S7 | §4 proposal 1 / `§HANDOFF` item 3: phase 1 is checked against `intent_match` | only the launch arm is visible there (P6) | §0.6 item 1 |
| S8 | §9.3: phase 1 touches `:data:repository` (matcher) and `:domain` (gate) | confirmed; plus test-only additions in both; no other module | decomposition below |
| S9 | §1's "12 of 13" | a reading, not a measurement (§0.2) | measured by "stopped being claimed" |

### 0.5 · What this plan does NOT do — said, not implied

| Not here | Why / where |
|---|---|
| Make the 7 rows *work* | spec §5.3 — phase 3, the bridge. They reach the model with seven `ActionIds` |
| Rows 5, 6, 9, 11, 13 | **named limits**: no second goal, and no stop-words (§5.2) |
| **The prefix rule** («открой gmail новые письма» stays a store offer with a wrong name) | **F1-3**: deferred to offline understanding (`B16`); the code is kept in Appendix A |
| **The choice card** «открыть X / найти в магазине» | F1-3's recorded alternative without a model; a new UI element, which spec §11 excludes from A4′ |
| **The install arm** («установи X и открой его») | the same failure class, named by neither spec nor corpus. One `SecondGoal` call would cover it, and that one line is a scope decision → owner / a later phase |
| "then" / «потом» / "sonra"; infinitives («и забронировать»); punctuation stuck to a token («… и проверь.»); a second app with no verb ("open veltrix and gmail") | named limits of F1-1's closed list, stated in `SecondGoal`'s KDoc. A comma *before* the conjunction does not defeat it (review A probe) |
| Label spelling («protonmail» vs «Proton Mail»); inflected names («в телеграмме») | pre-existing limits of whole-label resolution (`ToolSelector.kt`, "Recall gaps") |
| Verb stripping in `ToolSelector` | P7: its evidence has expired, and the question is re-opened in the KDoc; not decided here |
| `RouteCommandUseCase`, step 2b, the three-state order | spec §5.3; frozen |
| The doctrine line of spec §2 | **the owner's** (§0.6 item 2). **The phase does not close without it** (Task 5) |
| Any device round | the block has one acceptance, at its end (spec §0 decision 1) |

### 0.6 · Owner dependencies — named, not decided by this plan

1. **`usageHistoryEnabled` on the SM-A325F — confirmed on, and from what date.** Phase 1's acceptance
   at block end is read from usage recorded after that date.
   - **Before turning it on, know P6:** the history can check only the launch arm, and LAUNCH_APP
     rows keep second clauses verbatim.
   - After Task 3, a row `(LAUNCH_APP, ≈0.30)` is exactly a phase-1 withdrawal. Query it with a
     tolerance (P6).
   - Whether the `IntentMatchMapper` premise is corrected, and whether launch remainders should be
     redacted too, is a privacy decision. It is not this phase's; the mapper is frozen here.
2. **The doctrine line (spec §2, §5.5).** Change-control §5: «**добавить** правило», not a third
   amendment of `DOC-ADL-3`. The two honest readings are named here, and neither is chosen:
   - **(a)** `DOC-NYH-1` («Неоднозначная команда не исполняется молча») already covers overflow and
     needs a real anchor. Its two current anchors check routing and threshold arithmetic, not whether
     0.90 was earned.
   - **(b)** Ambiguity and overflow are different things, and the matrix gets a new row.

   Task 5 carries the exact edit for each. **Phase 1 does not close until one is chosen.**
3. **R14** (`§HANDOFF` «Фаза 0 A4′», question 1). Phase 1 touches none of R14's files
   (`AgentExecutor.perform`, the workers), so any task the answer adds can go anywhere without
   conflict. **Where it goes is the owner's call**, and this plan reserves no slot.
4. **DoD §4's parity line vs `DOC-ADL-3`** (spec §2).
   - Phase 1 makes the DoD line «router-off / offline / no-key ведут себя **как сегодня**» literally
     false. In local-only mode, «открой gmail и проверь новые письма» used to start an A0 session and
     now says «Не распознано локально».
   - Measured against `DOC-ADL-3` as amended, the phase passes, and Task 4 holds it.
   - The spec assigns the fix to **block close**. Whether it moves earlier is the owner's call.
5. **The Turkish review — one session for everything unjudged.**
   - Inherited from phase 0 (`§HANDOFF` «Фаза 0 A4′», item 2): six unjudged `tr` strings, including
     the proposed «Plan bitti — sonuç sistemde» and «Derleme».
   - New in this phase: `SecondGoal`'s «ve» and its 20 before-verbs.
   - Known homonyms: «et» (meat), «yaz» (summer), «al» (red), «aç» (hungry), «ara» (interval).
   - Matching is locale-blind, so all of it is testable from a `ru-RU` phone.
6. **The en/ru half of `SecondGoal`, which nobody has judged either.** Named homonym classes: "order",
   "pay", "book", "play", "call", "check" and "show" are also nouns ("law and order", "tap and pay").
   Whether any should leave the list is a judgement for the acceptance round, not the plan.

**Hypotheses for the block's acceptance checklist.** Nobody has seen these on a phone.
- With a provider and a network, «открой gmail и проверь новые письма» is expected to show **no**
  store card. Most likely it shows a model proposal (a `RoutedAction`), probably a one-tap "open
  Gmail".
- In local-only mode, the same command is expected to show «Не распознано локально. Понимание облаком
  выключено.»
- With a provider configured but the model answering nothing usable — for example, A1″ finding (c)'s
  silent BYOK misconfiguration — it is expected to show «Не удалось распознать — уточните команду».
  Before this phase, A0 offered a store search with a wrong name.
- «найди билеты в … и сделай бронь» is expected **not** to open the browser.
- «найди лучший рецепт шашлыка» and "search how to download and install python" are expected to open
  the browser as before.
- «открой <app not installed>», «открой youtube music» and «открой google maps» are expected to show
  A0's two-step plan exactly as before.

### 0.7 · How the predictions were produced — so a reviewer can reproduce them

The scratch copy is `git archive 1f5dbbf | tar -x -C <scratch>/wt`, plus `local.properties` and the
three git-ignored `b15/*` inputs copied in. It is not a git repository, so no `git checkout` was
possible. On it:
- **E1** — every `NoAppFound` → `LowConfidence` in `HandleUserCommandUseCase.routeAction`. This gives
  P11's A0-holding set. It was re-run per module after the multi-module run truncated.
- **E2** — the search-verb arm returns 0.30 unconditionally. It reddened exactly two tests,
  `RuleBasedIntentMatcherTest`'s `search weather tomorrow returns SearchIntent` and `ru and tr forms
  match english confidence parity`, and nothing in the other four modules.
- **Prototype v1** — the first design, with the prefix rule. Review B reviewed it and re-executed it
  fully (§0.8).
- **Prototype v2** — this plan's code, as written below:
  - all five modules green: `:domain` 455, `:data:repository` 394, `:feature:launcher` 200, `:app`
    61, `:consumer:jvm` 57;
  - `B15` gives §0.2's after-table;
  - the TDD reds, taken against HEAD's `HandleUserCommandUseCase` and `RuleBasedIntentMatcher`, match
    Task 2 step 2, Task 3 step 2 and Task 4 step 2;
  - every mutation in the tables below was planted with the protocol and printed `restored ok`;
  - each predicted set is the measured one, trimmed of tests that do not exist yet at that task —
    and it says so where it is trimmed.

### 0.8 · What the plan reviews changed

Two reviews ran on 2026-09-26, by separate agents, against the tree, on the first design (the one
with the prefix rule).
- **Review A** checked the plan against the spec and the tree. Its script applied every "replace X
  with Y" instruction as an exact string, reproduced the counts and `B15`, and probed behaviour with
  the real matcher, resolver and use case.
- **Review B** re-executed the evidence machinery from the plan's text, task by task in timeline
  order. It applied the code, ran every TDD red and every mutation with the plan's protocol, and ran
  four boundary gates (1468 → 1472 → 1480 → 1486) and `B15`. **Every count, every TDD red and every
  predicted `FAILED ::` set matched.** No plant was refused.

**Neither review found a blocker.** What they found:

| # | Finding | Source · how | Fixed where |
|---|---|---|---|
| **1** | **The prefix rule withdraws A0's store offer for an installed brand's uninstalled variant** («открой youtube music», "whatsapp business", "google earth", «сбербанк онлайн»). Spec §5.1's own premise is false | A · probe run on the real stack | **Owner fork F1-3**: the rule is deferred (§0.1) and Appendix A keeps it; a test pins the kept behaviour (Tasks 3, 4) |
| **2** | **Correct, common searches were withdrawn**: "how to download and install python", «как скачать и установить майнкрафт», "apple and google", "law and order", "tap and pay", «yaz ve kış lastiği ara». Causes: the subset guard forced FastPath's infinitives and "google" into the list; the KDoc's "imperatives only" contradicted its own list; homonyms | A · probe run | The infinitives and "google" leave the list, and the guard names them as **exemptions with reasons** (M2d holds the exemption half). A new structural rule: **a listed verb on both sides of the conjunction joins one clause** (Task 1, M1e). The homonyms are named in the KDoc and in §0.6 items 5–6 |
| **3** | The resolver test's "and nothing less" half held nothing (a `startsWith` mutation stayed green); Review Focus 5 was credited to the wrong mutation | A · ran the mutation | Moot after F1-3: `namesAnInstalledApp` left the phase. Appendix A carries the fix (`"lum"`) for the day it returns |
| **4** | "A multi-module scoped run stops at the first red module" is not a property of the tool: it depends on build state and scheduling | B · five-task M4c run, cold and warm | Global Constraints (reworded, with the mechanism); Task 4's commit text; Task 5 step 6 |
| 5 | `plant` fails when pasted verbatim, because of the indented heredoc terminator; a separate Bash tool call is a fresh shell | B · ran it; author | The helper is a file at column 0, sourced in every invocation |
| 6 | The plan file was untracked, so three `git status` expectations fail | A, B · read `git status` and phase 0's `c91c0a5` | Commits: the plan is committed before Task 1 |
| 7 | `B15`'s report can be stale: `gate.sh` clears only `build/test-results`, and a `SKIPPED` run leaves the old report | B · read the harness; found a 07:18 report in the repo | Task 5 step 2 deletes the report first and greps the `B15 ::` marker from the XML |
| 8 | `:domain` `FAILED ::` lines carry `[jvm]`; compile-reds print extra `e:` lines; addresses drift within a task | B · ran | Global Constraints |
| 9 | The `ToolSelector:101-102` edit could not be applied mechanically | B · applied it | Task 3 step 6 gives the exact lines |
| 10 | The phase-0 owner item about six unjudged `tr` strings was dropped | A · read `§HANDOFF` | §0.6 item 5 |
| 11 | `(LAUNCH_APP, 0.30)` is stored as 0.3000000119… | A · read `:134` | P6, §0.6 item 1, the test's KDoc |
| 12 | Review Focus 3 was not held at the seam (the "plain search" had no conjunction); RF2's read counts; a dead `tokens.size < 2` guard; an imprecise comment about re-reads | A · read / probe | The seam's noun search («рецепт хлеба и соли»). F1-3 removed the second read and the dead code |
| 13 | RF4's evidence is indirect: the router tests drive `Unknown` | A · read `:142-145` | Review Focus 4, which now says it is indirect |
| 14 | The punctuation limit was mis-described; "open veltrix and gmail" was unnamed | A · probe | `SecondGoal` KDoc, §0.5 |
| 15 | Citations: `DOC-NYH-1` has two anchors; «поставь» is in two three-word triggers; one test was cited by a name it does not have; the R14-43 sentences were literally false; `CLAUDE.md`'s baseline paragraph runs to `:705` | A · read | P5, P13, the Task 3/4 KDocs, Task 5 step 6 |
| 16 | A missing hypothesis: provider configured, but the model answering nothing usable | A | §0.6 hypotheses |

**What was not independently reviewed, said rather than implied.**
- The design after F1-3 has not been re-executed by a third reviewer. That covers the simplified
  Task 3, `SecondGoal`'s narrowed list, the two-verb rule, the exemption guard, and the revised seam
  test.
- The author re-measured it on prototype v2: all modules, `B15`, the TDD reds, and all fourteen
  mutations.
- Under the owner's rule of 2026-09-26 (`§HANDOFF`, «как пишутся планы фаз 2–4»), that verification
  now happens in execution's `mutation-prover`. A mismatch there is a finding, and the plan is
  corrected.

---

## Task 1: `SecondGoal` — the closed vocabulary both arms use

**Spec:** §5.2, owner fork F1-1. **Module:** `:domain`.

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/SecondGoal.kt`
- Create: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/SecondGoalTest.kt`

**Interfaces.**
- Produces:
  ```kotlin
  object SecondGoal {
      val CONJUNCTIONS_BY_LOCALE: Map<String, Set<String>>
      val VERBS_AFTER_CONJUNCTION_BY_LOCALE: Map<String, Set<String>>
      val VERBS_BEFORE_CONJUNCTION_BY_LOCALE: Map<String, Set<String>>
      fun carriedBy(remainder: String): Boolean
  }
  ```
- Consumed by Task 2 (the matcher's search arm, and `FastPathLocaleGuardTest`) and by Task 3
  (`HandleUserCommandUseCase`'s launch arm).

- [ ] **Step 1: write the failing tests**

```kotlin
package com.sidr.launcher.domain.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 1, spec §5.2. [SecondGoal] answers one question — does this remainder discard a second
 * goal? — and each test below pins one half of that answer. The phrases are invented, not the owner's
 * `B15` corpus: the corpus is the measurement, and a test written from it would measure the author.
 */
class SecondGoalTest {

    @Test fun `a listed verb right after the conjunction opens a second goal`() {
        assertTrue(SecondGoal.carriedBy("билеты в казань и закажи такси"))
        assertTrue(SecondGoal.carriedBy("the quarterly report and send it"))
    }

    /** `tr` is SOV: FastPath only claims a text ending in its own verb, so the clause BEFORE `ve` ends in its verb. */
    @Test fun `a listed verb right before the conjunction closes a first goal`() {
        assertTrue(SecondGoal.carriedBy("haberleri oku ve pilav tarifi"))
    }

    /** The case spec §5.2 exists to keep: a conjunction inside ONE query discards nothing. */
    @Test fun `a conjunction joining two nouns is one query, not two goals`() {
        assertFalse(SecondGoal.carriedBy("рецепт хлеба и соли"))
        assertFalse(SecondGoal.carriedBy("salt and pepper"))
        assertFalse(SecondGoal.carriedBy("tavuk ve pilav"))
    }

    /**
     * The verb must stand NEXT TO a conjunction. Each phrase puts a listed verb after position 0, so a
     * rule that stopped looking for the conjunction would fire on every one of them.
     */
    @Test fun `a listed verb with no conjunction beside it is not a second goal`() {
        assertFalse(SecondGoal.carriedBy("срочно закажи такси"))
        assertFalse(SecondGoal.carriedBy("please send it"))
        assertFalse(SecondGoal.carriedBy("tarifi oku lütfen"))
    }

    /**
     * A listed verb on BOTH sides of the conjunction is one clause with two verbs — a query about one
     * thing ("how to download and install python"), not a second goal. Without this, the commonest
     * how-to search shape would be withdrawn.
     */
    @Test fun `a conjunction between two listed verbs joins one clause, it does not open a second`() {
        assertFalse(SecondGoal.carriedBy("how to download and install python"))
        assertFalse(SecondGoal.carriedBy("скачай и установи майнкрафт"))
        assertFalse(SecondGoal.carriedBy("python indir ve kur"))
    }

    /** `FastPathLocaleGuardTest`'s shape, for a vocabulary that lives in `:domain`. */
    @Test fun `every supported locale has a conjunction and a verb on its side of it`() {
        val missing = listOf("en", "ru", "tr").filter { locale ->
            SecondGoal.CONJUNCTIONS_BY_LOCALE[locale].orEmpty().isEmpty() ||
                (SecondGoal.VERBS_AFTER_CONJUNCTION_BY_LOCALE[locale].orEmpty().isEmpty() &&
                    SecondGoal.VERBS_BEFORE_CONJUNCTION_BY_LOCALE[locale].orEmpty().isEmpty())
        }
        assertTrue("SecondGoal has no conjunction or no verb form for: $missing", missing.isEmpty())
    }
}
```

- [ ] **Step 2: run — expect a compile failure, and only that**

```bash
tools/gate.sh --scoped :domain:jvmTest
```
Expected:
- `SCOPED RED (gradle=1 …)`, with **no** `FAILED ::` lines;
- among the log's `e:` lines (the log path is printed), `Unresolved reference 'SecondGoal'`.

This is the TDD red. The behavioural reds come from M1a–e.

- [ ] **Step 3: write `SecondGoal`**

```kotlin
package com.sidr.launcher.domain.intent

/**
 * Does a FastPath remainder carry a SECOND goal — one the arm would silently fold into its own
 * argument? A4′ phase 1, spec §5.2, owner fork F1-1 (2026-09-26): «союз + закрытый список».
 *
 * **What it detects is discarded content, not query quality.** "найди билеты в казань и закажи
 * такси" handed to a web search searches for the taxi order too; "найди рецепт хлеба и соли" does
 * not discard anything — its conjunction joins two nouns of one query. The rule tells those apart by
 * one thing: a verb from [VERBS_AFTER_CONJUNCTION_BY_LOCALE] / [VERBS_BEFORE_CONJUNCTION_BY_LOCALE]
 * standing next to a conjunction from [CONJUNCTIONS_BY_LOCALE]. It is the same idea as
 * `ToolSelector`'s rule 4 — the command must not be bounded from above by nothing — said for a layer
 * that has no target to bound it with.
 *
 * **The position is grammar, not locale** — the axis `RuleBasedIntentMatcher.VerbForms` already
 * uses. `en`/`ru` are SVO: a second clause's verb FOLLOWS the conjunction ("… and send it", "… и
 * закажи такси"). `tr` is SOV: FastPath only claims a Turkish text that ENDS in its own verb, so
 * whatever clause precedes the conjunction ends in ITS verb ("haberleri oku ve …"). Matching is
 * locale-blind, like every FastPath and tool vocabulary in this repo: all forms are live at once.
 *
 * **A conjunction with a listed verb on BOTH sides joins two verbs of one clause**, not two goals:
 * "how to download and install python", «скачай и установи» — the query is about one thing. That
 * case is skipped, so the rule fires only where a verb stands on the side of the conjunction its
 * grammar puts a new clause.
 *
 * **Imperatives only.** The lists hold the imperative forms of FastPath's own command verbs — a
 * clause led by one of them is a second command by definition — and a closed list of imperatives of
 * acts a phone is asked to do. FastPath's infinitives («открыть», «запустить», «установить»,
 * «найти») and "google" are left out on purpose, and `FastPathLocaleGuardTest` names each exemption:
 * as a noun or an infinitive inside a query ("apple and google", «как скачать и установить») they
 * would withdraw correct searches. **The list was written after the owner's `B15` corpus had been
 * read**, so the corpus rows it catches are its calibration, not evidence of its generality; the
 * `intent_match` history cannot validate it, because SEARCH text is redacted there (`IntentMatchMapper`).
 *
 * **Named limits, not oversights.** One conjunction per locale (no "then" / "потом" / "sonra").
 * Infinitives and verbs outside the list are not caught ("… и забронировать"). Punctuation stuck to
 * a token defeats the match, because `CommandNormalizer` strips none ("… и проверь." — the verb
 * token is «проверь.»); a comma before the conjunction does not ("… почту, и проверь"). A second
 * app named without a verb ("open veltrix and gmail") is not a second goal here. And a word spelled
 * like a listed verb reads as a verb: `en` "order", "pay", "book", "play", "call", "check", "show"
 * are also nouns ("law and order", "tap and pay"); `tr` "et" is also "meat", "yaz" "summer", "al"
 * "red", "aç" "hungry", "ara" "interval". Each miss costs, on one more command, what the phase set
 * out to remove — a confident, slightly wrong answer; each false hit costs an honest "not understood"
 * or a round-trip to the model, never a wrong act.
 */
object SecondGoal {

    val CONJUNCTIONS_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "en" to setOf("and"),
        "ru" to setOf("и"),
        "tr" to setOf("ve"),
    )

    /** SVO: the verb that opens the second clause, right after the conjunction. */
    val VERBS_AFTER_CONJUNCTION_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "en" to setOf(
            // FastPath's own verbs (RuleBasedIntentMatcher LAUNCH/INSTALL/SEARCH, en prefixes; not "google")
            "open", "launch", "start", "install", "search", "find",
            // acts
            "do", "make", "download", "check", "set", "write", "send", "call", "buy", "order", "book",
            "pay", "transfer", "save", "delete", "turn", "show", "read", "add", "create", "remind", "play",
        ),
        "ru" to setOf(
            // FastPath's own verbs (ru prefixes, imperatives only)
            "открой", "запусти", "установи", "найди",
            // acts
            "сделай", "скачай", "проверь", "поставь", "напиши", "отправь", "позвони", "купи", "закажи",
            "забронируй", "оплати", "переведи", "запиши", "сохрани", "удали", "включи", "выключи",
            "покажи", "прочитай", "добавь", "создай", "напомни",
        ),
    )

    /** SOV: the verb that closes the first clause, right before the conjunction. */
    val VERBS_BEFORE_CONJUNCTION_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "tr" to setOf(
            // FastPath's own verbs (tr suffixes)
            "aç", "kur", "ara",
            // acts
            "yap", "et", "indir", "yaz", "gönder", "al", "öde", "kaydet", "sil", "göster", "oku", "ekle",
            "oluştur", "hatırlat", "bul", "başlat", "kapat",
        ),
    )

    private val conjunctions: Set<String> = CONJUNCTIONS_BY_LOCALE.values.flatten().toSet()
    private val verbsAfter: Set<String> = VERBS_AFTER_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
    private val verbsBefore: Set<String> = VERBS_BEFORE_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
    private val anyVerb: Set<String> = verbsAfter + verbsBefore

    fun carriedBy(remainder: String): Boolean {
        val tokens = CommandNormalizer.normalize(remainder).split(' ')
        return tokens.indices.any { i ->
            val before = tokens.getOrNull(i - 1)
            val after = tokens.getOrNull(i + 1)
            tokens[i] in conjunctions &&
                !(before in anyVerb && after in anyVerb) &&
                (after in verbsAfter || before in verbsBefore)
        }
    }
}
```

**Do not paraphrase the lines the mutations plant on.** `plant` refuses unless each occurs exactly
once, and in this text each does:
- `(after in verbsAfter || before in verbsBefore)`
- `tokens[i] in conjunctions &&`
- `return tokens.indices.any { i ->`
- the line `                !(before in anyVerb && after in anyVerb) &&`
- the line `        "tr" to setOf(` that opens the before-verb map. The conjunction map's `tr` line
  is `"tr" to setOf("ve"),`, a different string.
- `"открой", "запусти", "установи", "найди",`
- `"open", "launch", "start", "install", "search", "find",`

- [ ] **Step 4: run — expect green:** `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=451
  failures=0`.

- [ ] **Step 5: mutations — five, each on its own assert, all in `:domain`.**
  `F=domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/SecondGoal.kt`.

| # | Plant: old → new | Proves | Predicted `FAILED ::` (`:domain`) |
|---|---|---|---|
| M1a | `(after in verbsAfter \|\| before in verbsBefore)` → `(after != null \|\| before != null)` | the verb is required, not just the conjunction | `SecondGoalTest > a conjunction joining two nouns is one query, not two goals` — **only** |
| M1b | `tokens[i] in conjunctions &&` → `true &&` | the conjunction is required | `SecondGoalTest >` `a listed verb with no conjunction beside it is not a second goal`, `a conjunction between two listed verbs joins one clause, it does not open a second` — **exactly these two**. The second reddens because "how *to download*" puts a listed verb after a non-verb |
| M1c | `        "tr" to setOf(` + newline → `        "tx" to setOf(` + newline (pass both with `$'…\n'`) | the locale guard bites; detection is untouched, because matching is locale-blind | `SecondGoalTest > every supported locale has a conjunction and a verb on its side of it` — **only** |
| M1d | `return tokens.indices.any { i ->` → `return false && tokens.indices.any { i ->` | both positive tests assert, and do not merely compile | `SecondGoalTest >` `a listed verb right after the conjunction opens a second goal`, `a listed verb right before the conjunction closes a first goal` — **exactly these two** |
| M1e | the line `                !(before in anyVerb && after in anyVerb) &&` + newline → nothing | the two-verb skip | `SecondGoalTest > a conjunction between two listed verbs joins one clause, it does not open a second` — **only** |

These were measured on prototype v2, where Tasks 2–4 already existed.
- M1a, M1b, M1c and M1e reddened exactly the sets above.
- M1d also reddened Task 3's `…app name carries a second goal…` and `…unearned launch is recorded…`.
  Those tests do not exist at this point in the timeline, so the prediction leaves them out.

Each row is one invocation. For example:

```bash
source "${TMPDIR:-/tmp}/sidr-plant.sh"
F=domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/SecondGoal.kt; MUT="$(mktemp -d -t sidr-mut-XXXXXX)"; cp "$F" "$MUT/orig"
trap 'cp "$MUT/orig" "$F"' EXIT
plant "$F" '(after in verbsAfter || before in verbsBefore)' '(after != null || before != null)' && tools/gate.sh --scoped :domain:jvmTest
cp "$MUT/orig" "$F"; cmp "$MUT/orig" "$F" && echo RESTORED && trap - EXIT
```

- [ ] **Step 6: boundary gate from a cleared tree**

```bash
git status --porcelain --untracked-files=all   # exactly the two new files
tools/gate.sh
```
Expected:
- `557 actionable tasks: 557 executed`;
- `GATE GREEN`, `TOTAL tests=1469`;
- `:domain` **451** (+6), every other module unchanged.

- [ ] **Step 7: commit (controller)**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/SecondGoal.kt \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/SecondGoalTest.kt
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф1): SecondGoal — закрытый словарь «союз + глагол»

Спека §5.2, развилка владельца F1-1 (2026-09-26): «глагол» распознаётся
закрытым списком повелительных форм по локалям, в идиоме VerbForms. Позиция —
грамматика, а не локаль: en/ru (SVO) — глагол ПОСЛЕ союза, tr (SOV) — ПЕРЕД ним.
Союз между двумя глаголами из списка — одно предложение («how to download and
install»), а не вторая цель.

Инфинитивы FastPath и «google» в список не входят — внутри запроса это
существительное или инфинитив, и отзывались бы верные поиски (ревью A, находка 2).
Список написан после прочтения корпуса B15: пойманные им строки — калибровка,
а не свидетельство общности.

Мутации M1a–e, каждая на своём ассерте.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: the search arm stops claiming a query that carries a second goal

**Spec:** §5.2 — only the discarded-content rule; no stop-words; the arm as a whole is **not**
downgraded. **Module:** `:data:repository`. **Depends on:** Task 1.

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/intent/RuleBasedIntentMatcher.kt`
  — an import after `:8`; KDoc rule 2 at `:23`; the search success branch at `:175-181`.
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/intent/RuleBasedIntentMatcherTest.kt`
  — append before the final `}` (`:318`).
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/intent/FastPathLocaleGuardTest.kt`
  — append before the final `}` (`:52`), and import `SecondGoal`.

**Interfaces.**
- Consumes:
  - from Task 1: `SecondGoal.carriedBy` and the two verb maps;
  - existing: `RuleBasedIntentMatcher.LAUNCH_VERBS` / `INSTALL_VERBS` / `SEARCH_VERBS`, and
    `VerbForms.allPrefixForms` / `allSuffixForms`.
- Produces: a `SearchIntent` classification at **0.30** when its query carries a second goal. The
  intent and the query are unchanged. `HandleUserCommandUseCase`'s gate turns this into
  `CommandOutcome.LowConfidence` with no code change there.

- [ ] **Step 1: write the failing tests.** Append to `RuleBasedIntentMatcherTest`:

```kotlin

    // --- A4′ phase 1 (spec §5.2): a search does not claim confidence for a goal it would discard ---

    @Test fun `a search whose query carries a second goal does not claim a search's confidence`() = runTest {
        val best = matcher.match("найди билеты в казань и закажи такси").best
        val intent = best.intent as LauncherIntent.SearchIntent
        assertEquals("билеты в казань и закажи такси", intent.query)
        assertTrue("got ${best.confidence}", best.confidence < 0.50f)
    }

    @Test fun `a Turkish search whose first clause ends in a listed verb does not claim it either`() = runTest {
        val best = matcher.match("haberleri oku ve pilav tarifi ara").best
        assertTrue(best.intent is LauncherIntent.SearchIntent)
        assertTrue("got ${best.confidence}", best.confidence < 0.50f)
    }

    /** Spec §5.2: «не понижаем весь рукав». A conjunction inside one query keeps the arm's 0.90. */
    @Test fun `a search whose conjunction joins two nouns keeps a search's confidence`() = runTest {
        val best = matcher.match("найди рецепт хлеба и соли").best
        assertTrue(best.intent is LauncherIntent.SearchIntent)
        assertTrue("got ${best.confidence}", best.confidence >= 0.85f)
    }
```

Append to `FastPathLocaleGuardTest`, and add `import com.sidr.launcher.domain.intent.SecondGoal`
above `import org.junit.Assert.assertTrue`:

```kotlin

    /**
     * A4′ phase 1: a clause led by one of FastPath's own command verbs is a second command by
     * definition, so every such verb is either one [SecondGoal] recognises on its side of a
     * conjunction (`en`/`ru` prefix verbs after it, `tr` suffix verbs before it) or is exempted HERE,
     * by name and with its reason. Adding a FastPath verb therefore forces a decision instead of
     * leaving a silent hole; and an exempted verb that crept back into the list is red too.
     */
    @Test fun every_fastpath_verb_is_a_second_goal_verb_or_named_as_an_exemption() {
        val exempt = mapOf(
            "открыть" to "infinitive — «как открыть и настроить …» is one query",
            "запустить" to "infinitive",
            "установить" to "infinitive — «как скачать и установить …» is one query",
            "найти" to "infinitive",
            "google" to "a noun inside a query far more often than a verb (\"apple and google\")",
        )
        val verbSets = listOf(
            RuleBasedIntentMatcher.LAUNCH_VERBS,
            RuleBasedIntentMatcher.INSTALL_VERBS,
            RuleBasedIntentMatcher.SEARCH_VERBS,
        )
        val after = SecondGoal.VERBS_AFTER_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
        val before = SecondGoal.VERBS_BEFORE_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
        val missing = verbSets.flatMap { it.allPrefixForms - after - exempt.keys } +
            verbSets.flatMap { it.allSuffixForms - before - exempt.keys }
        assertTrue("FastPath verbs SecondGoal neither knows nor exempts: $missing", missing.isEmpty())
        val readmitted = exempt.keys.filter { it in after || it in before }
        assertTrue("exempted verbs back in SecondGoal's lists: $readmitted", readmitted.isEmpty())
    }
```

- [ ] **Step 2: run — expect exactly two FAILED.** `tools/gate.sh --scoped
  :data:repository:testDebugUnitTest`. Expected (measured against HEAD's matcher): `data/repository
  tests=388 failures=2`, namely:
  - `RuleBasedIntentMatcherTest > a search whose query carries a second goal does not claim a search's confidence`
  - `RuleBasedIntentMatcherTest > a Turkish search whose first clause ends in a listed verb does not claim it either`

  Two tests **pass already**, and step 5 proves both:
  - the noun test — it pins behaviour that must survive;
  - the guard — Task 1 built the lists.

- [ ] **Step 3: implement.**

Add `import com.sidr.launcher.domain.intent.SecondGoal` after `:8`
(`import com.sidr.launcher.domain.intent.SearchTarget`).

Replace the success branch at `:175-181`:
```kotlin
            return if (query.isNotEmpty()) {
                Classification(
                    LauncherIntent.SearchIntent(query = query, target = SearchTarget.WEB),
                    0.90f,
                    "search verb '$verb' → query '$query'",
                )
            } else {
```
with:
```kotlin
            return if (query.isNotEmpty()) {
                // A4′ phase 1 (spec §5.2): a query that carries a second goal would hand that goal to
                // the search engine as words ("… и закажи такси"). It is not claimed — 0.30, the value
                // this matcher gives a verb it cannot use — so the use case answers `LowConfidence`
                // and routing offers the command to the tool planner and the model. The INTENT does
                // not change: what the words say is still a search; only the confidence is withdrawn.
                val secondGoal = SecondGoal.carriedBy(query)
                Classification(
                    LauncherIntent.SearchIntent(query = query, target = SearchTarget.WEB),
                    if (secondGoal) 0.30f else 0.90f,
                    if (secondGoal) "search verb '$verb' → query '$query' carries a second goal" else "search verb '$verb' → query '$query'",
                )
            } else {
```

In the class KDoc, replace rule 2's line (` *  2. Search verb (en/ru prefix, tr suffix).`) with:
```kotlin
 *  2. Search verb (en/ru prefix, tr suffix). A query carrying a second goal (`SecondGoal`, A4′
 *     phase 1) is classified at 0.30, not 0.90: the intent is still a search, the claim is withdrawn.
```

- [ ] **Step 4: run — expect green:** `data/repository tests=388 failures=0`.

- [ ] **Step 5: mutations — four, all read in `:data:repository`**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M2a | matcher · `val secondGoal = SecondGoal.carriedBy(query)` → `val secondGoal = false` | the arm consults the rule | `RuleBasedIntentMatcherTest >` `a search whose query carries a second goal…`, `a Turkish search whose first clause ends in a listed verb…` — **exactly these two** |
| M2b | matcher · `if (secondGoal) 0.30f else 0.90f` → `0.30f` | spec §5.2's forbidden move: the whole arm downgraded | `RuleBasedIntentMatcherTest >` `a search whose conjunction joins two nouns keeps a search's confidence`, `search weather tomorrow returns SearchIntent`, `ru and tr forms match english confidence parity` — **exactly these three** |
| M2c | `SecondGoal.kt` · `"открой", "запусти", "установи", "найди",` → `"открой", "запусти", "установи",` | the guard's first half: a FastPath verb neither listed nor exempt | `FastPathLocaleGuardTest > every_fastpath_verb_is_a_second_goal_verb_or_named_as_an_exemption` — **only** |
| M2d | `SecondGoal.kt` · `"open", "launch", "start", "install", "search", "find",` → the same with `"google",` appended | the guard's second half: an exempted verb readmitted | the same test — **only** |

All four were measured on prototype v2, with Task 4's seam test present.
- M2a also reddened the seam's `a search carrying a second goal…`, and M2b the seam's noun-conjunction
  search. Both are left out of the prediction because Task 4 does not exist yet.
- M2b's two pre-existing tests are exactly the ones §0.7's run E2 found. E2 found nothing in any other
  module.

- [ ] **Step 6: boundary gate from a cleared tree.** Expected: `GATE GREEN`, `TOTAL tests=1473`;
  `:data:repository` **388** (+4), `:domain` 451, the rest unchanged.

- [ ] **Step 7: commit (controller)**

```bash
git add data/repository/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф1): рукав поиска не заявляет запрос со второй целью

Спека §5.2. «найди билеты … и закажи такси» больше не гуглит заказ такси:
запрос со второй целью классифицируется 0.30 — значение, которое матчер даёт
глаголу, которым не может воспользоваться. Интент тот же, снята только
заявка; гейт use case отвечает LowConfidence без правки в нём.

Весь рукав НЕ понижен: «найди рецепт хлеба и соли» — 0.90, тест держит это
(M2b — «понизить весь рукав» — краснит ровно три теста, два прежних).

Гвард: каждый глагол FastPath — либо глагол второй цели, либо поимённое
исключение с причиной (инфинитивы, «google»); M2c и M2d — по половине.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: the launch arm — resolve first, then believe

**Spec:** §5.1 as amended by F1-3: the discriminator is the second goal. Owner fork F1-2: that goal is
asked only after the whole name failed to resolve. §5.4 **(а)**. **Module:** `:domain`, plus KDoc-only
edits in `:data:repository`. **Depends on:** Task 1.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt` —
  KDoc `:17` and `:26`; `:52-61`; `:80`; a new private function before `:86`; a companion before
  `:145`.
- Modify, KDoc only (P7): `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolSelector.kt`
  `:68-78` and `:101-102`; `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt`
  `:49-51`.
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCaseTest.kt` —
  insert before `// ── helpers ──` (`:368`).
- **Not modified:** `IntentActionResolver.kt` (P3).

**Interfaces.**
- Consumes:
  - from Task 1: `SecondGoal.carriedBy`;
  - existing, seen in the file: `IntentActionResolver.resolve`, `confidencePolicy.shouldAutoExecute`,
    `recordMatch`.
- Produces: `HandleUserCommandUseCase.handle` returns `CommandOutcome.LowConfidence` for a launch
  whose unresolved name carries a second goal, and records it at 0.30. Every other launch outcome is
  unchanged.
- Task 4 crosses this seam from the real matcher up to the real router.

- [ ] **Step 1: write the tests** — insert before
  `    // ── helpers ──────────────────────────────────────────────────────────────` (`:368`):

```kotlin
    // ── A4′ phase 1 (spec §5; owner forks F1-2, F1-3): a launch's confidence is earned ───────────
    // Fixtures (R14-43): the installed label "Lumora" (net.fixture.alpha), the uninstalled "corvane",
    // and the installed "Pack and Send" (net.fixture.parcel). "lumora studio" extends the installed
    // label ON PURPOSE — that is the case under test there. Every expected value is compared by
    // equality, never by `contains`, so a shared substring cannot blind an assertion.

    @Test fun `a launch whose app name carries a second goal does not claim confidence`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("corvane and send the report"), 0.90f)

        assertEquals(CommandOutcome.LowConfidence, useCase.handle("open corvane and send the report"))
        assertEquals(0, fakeExecutor.callCount)
    }

    /**
     * Owner fork F1-3 (2026-09-26): an installed brand followed by more words is a fact about the WORLD
     * — the user named a variant that is not installed ("открой youtube music", "google maps" on a
     * phone that labels Maps «Карты») — so `NoAppFound` is still claimed and step (2) of
     * `RouteCommandUseCase` still offers the store. Spec §5.1's prefix rule would withdraw this; it is
     * deferred until offline understanding exists (Master Plan row `B16`). Re-adding it must delete
     * this test on purpose.
     */
    @Test fun `a launch naming an installed brand's variant that is not installed is still claimed`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("lumora studio"), 0.90f)

        assertEquals(
            CommandOutcome.Message(CommandMessage.NoAppFound("lumora studio")),
            useCase.handle("открой lumora studio"),
        )
    }

    /**
     * The second goal is asked only AFTER the whole name failed to resolve (owner fork F1-2). An
     * installed app whose own label reads like a second goal must still launch.
     */
    @Test fun `an installed app whose own name carries a conjunction and a verb still launches`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.parcel", "Pack and Send"))
        matcherReturns(LauncherIntent.LaunchAppIntent("pack and send"), 0.90f)

        assertEquals(CommandOutcome.Executed, useCase.handle("open pack and send"))
        val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
        assertEquals("net.fixture.parcel", action.packageName)
    }

    /**
     * The history row must not claim a confidence that did not govern the outcome (DOC-ILM-3's
     * spirit). Stored as a Double, 0.30f reads back as 0.3000000119… — hence the tolerance, here and
     * in any query of the table.
     */
    @Test fun `an unearned launch is recorded with the confidence it earned, not the one it claimed`() = runTest(testDispatcher) {
        val history = RecordingIntentMatchHistory()
        val useCaseWithHistory = useCaseWith(history)
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("corvane and send the report"), 0.90f)

        useCaseWithHistory.handle("open corvane and send the report")
        advanceUntilIdle() // flush the fire-and-forget recordMatch

        assertEquals(0.30, history.recorded.single().confidence, 0.0001)
    }

```

- [ ] **Step 2: run — expect exactly two FAILED.** `tools/gate.sh --scoped :domain:jvmTest`.
  Expected (measured against HEAD's use case): `domain tests=455 failures=2`, namely:
  - `HandleUserCommandUseCaseTest > a launch whose app name carries a second goal does not claim confidence`
  - `HandleUserCommandUseCaseTest > an unearned launch is recorded with the confidence it earned, not the one it claimed`

  The brand-variant test and the Pack-and-Send test **pass already**. They pin behaviour that must
  survive, and M4c and M3b prove them.

- [ ] **Step 3: implement.**

KDoc: replace `:17` (` * Pipeline: normalize → (empty?) → match → confidence gate → route → execute-when-safe.`) with:
```kotlin
 * Pipeline: normalize → (empty?) → match → resolve a claimed launch → confidence gate → route →
 * execute-when-safe.
```
Replace `:26` (` * - Low/medium confidence never auto-executes.`) with:
```kotlin
 * - Low/medium confidence never auto-executes.
 * - A claimed launch's confidence is EARNED, not assumed (A4′ phase 1, spec §5; owner forks F1-2,
 *   F1-3). A launch whose app name does not resolve keeps its claim when that is a fact about the
 *   world — "открой wattsupp", or an installed brand's variant that is not installed ("открой youtube
 *   music"): `NoAppFound`, and `RouteCommandUseCase` step (2) offers the store, byte for byte as A0
 *   shipped it. When the name carries a second goal ([SecondGoal]: "открой gmail и проверь почту"),
 *   the parse folded two goals into one name and the claim is withdrawn:
 *   [CommandOutcome.LowConfidence], undecided, so step 2b and the model get the command.
```

Replace `:52-61`:
```kotlin
        val match = matcher.match(normalized)
        val intent = match.best.intent
        val confidence = match.best.confidence

        // Fire-and-forget: record on the injected scope so the I/O never delays the outcome.
        // Placed BEFORE the confidence gate so suggest, low-confidence and unknown matches are
        // all captured. Never affects the outcome below.
        recordingScope.launch { recordMatch(normalized, intent, confidence) }

        // ── Confidence gate — runs before any resolution or execution ────────
```
with:
```kotlin
        val match = matcher.match(normalized)
        val intent = match.best.intent

        // A4′ phase 1 (spec §5; owner forks F1-2, F1-3): a launch the matcher claimed is RESOLVED
        // before its confidence is believed. "открой gmail и проверь новые письма" is claimed at 0.90
        // exactly like "открой gmail"; whether that 0.90 was earned depends on what the name resolved
        // to. This is a read — nothing executes before the gate below — and its result is reused after
        // the gate, so a launch still reads the installed apps exactly once.
        val launchResolution = (intent as? LauncherIntent.LaunchAppIntent)
            ?.takeIf { confidencePolicy.shouldAutoExecute(match.best.confidence) }
            ?.let { resolver.resolve(it) }
        val confidence =
            if (launchResolution != null && overreaches(intent as LauncherIntent.LaunchAppIntent, launchResolution)) {
                UNEARNED_LAUNCH_CONFIDENCE
            } else {
                match.best.confidence
            }

        // Fire-and-forget: record on the injected scope so the I/O never delays the outcome.
        // Placed BEFORE the confidence gate so suggest, low-confidence and unknown matches are
        // all captured. Never affects the outcome below. Since A4′ phase 1 the confidence recorded
        // is the one the gate acts on, not the one the matcher claimed.
        recordingScope.launch { recordMatch(normalized, intent, confidence) }

        // ── Confidence gate — runs before any execution ──────────────────────
```

Replace `:80` (`        return when (val resolved = resolver.resolve(intent)) {`) with:
```kotlin
        return when (val resolved = launchResolution ?: resolver.resolve(intent)) {
```

Insert before `    private fun routeSimpleCommand(` (`:86`):
```kotlin
    /**
     * A claimed launch whose app name did not resolve: is that a fact about the WORLD (no such app —
     * keep `NoAppFound`, and A0's store plan) or about the PARSE (FastPath folded a second goal into
     * the app name)? Only a second goal says "the parse" (owner fork F1-3, 2026-09-26). An installed
     * brand followed by more words — "открой youtube music", or "google maps" on a phone that labels
     * Maps «Карты» — is the world: the user named a variant that is not installed, and A0's store offer
     * is the right answer. Consulted only after the whole name failed to resolve, so an installed app
     * whose own label reads like a second goal ("Pack and Send") still launches (owner fork F1-2).
     */
    private fun overreaches(
        intent: LauncherIntent.LaunchAppIntent,
        resolution: OperationResult<ExecutableAction>,
    ): Boolean {
        val shown = (resolution as? OperationResult.Success)?.value as? ExecutableAction.ShowMessageAction
        if (shown?.message !is CommandMessage.NoAppFound) return false
        return SecondGoal.carriedBy(intent.displayNameQuery)
    }

```

Insert before `    private fun LauncherIntent.toMatchType()` (`:145`):
```kotlin
    private companion object {
        /**
         * What a launch verb whose app name cannot be used earns — the value `RuleBasedIntentMatcher`
         * already gives a launch verb with no app name. Below `DefaultIntentConfidencePolicy`'s suggest
         * threshold (0.50), so the gate answers `LowConfidence`: undecided, never `Suggest`. That holds
         * for the only policy production binds (`IntentProvidesModule.provideConfidencePolicy`); a
         * policy whose suggest threshold sat at or below this value would turn it into a suggestion to
         * launch a name that does not resolve — named here, not guarded.
         */
        const val UNEARNED_LAUNCH_CONFIDENCE = 0.30f
    }

```

No new import is needed: `SecondGoal`, `CommandMessage` and `ExecutableAction` share the package,
and `OperationResult` is already imported (`:7`).

**The plant strings must each occur exactly once, so no comment may quote them:**
- `const val UNEARNED_LAUNCH_CONFIDENCE = 0.30f`
- the line `        if (shown?.message !is CommandMessage.NoAppFound) return false`
- the line `        return SecondGoal.carriedBy(intent.displayNameQuery)`

- [ ] **Step 4: run — expect green:** `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=455
  failures=0`.

- [ ] **Step 5: mutations — two, read in `:domain`**
  (`U=domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt`).
  Both sets are the measured ones, and neither needed trimming.

| # | Plant: old → new | Proves | Predicted `FAILED ::` (`:domain`) |
|---|---|---|---|
| **M3a = spec §5.4 (а)** | `const val UNEARNED_LAUNCH_CONFIDENCE = 0.30f` → `const val UNEARNED_LAUNCH_CONFIDENCE = 0.90f` — «безусловные 0.90 возвращены в рукав запуска» | the claimed confidence depends on the remainder | `HandleUserCommandUseCaseTest >` `a launch whose app name carries a second goal does not claim confidence`, `an unearned launch is recorded with the confidence it earned, not the one it claimed` — **exactly these two** |
| M3b | the line `        if (shown?.message !is CommandMessage.NoAppFound) return false` → `        if (SecondGoal.carriedBy(intent.displayNameQuery)) return true` + newline + that same line | "only after the whole name failed to resolve" (F1-2) | `HandleUserCommandUseCaseTest > an installed app whose own name carries a conjunction and a verb still launches` — **only** |

Spec §5.4 **(б)** is void since F1-3 (§0.1). **(в)** is Task 4's M4c, because it needs the seam test.

- [ ] **Step 6: correct the two KDocs P7 names — comments only.**

`ToolSelector.kt:68-78`: replace the paragraph that begins `     **Why not strip a launch verb
instead**` and ends `structurally cannot arrive.` with:
```kotlin
 *     **Why not strip a launch verb instead** (reusing `RuleBasedIntentMatcher.LAUNCH_VERBS`, stripping a
 *     leading/trailing verb before matching) **— considered and rejected on evidence, not on cost; the
 *     evidence was narrowed by A4′ phase 1 and the question is open again.** Until that phase,
 *     `RuleBasedIntentMatcher` returned `LaunchAppIntent` for every verb-led command at 0.90 — above the
 *     0.85 auto-execute threshold — so FastPath always *decided* it and it never reached step 2b. Since
 *     phase 1 a verb-led command whose app name does not resolve comes back `LowConfidence` —
 *     undecided — when the name carries a second goal (`SecondGoal`: "открой gmail и проверь почту"),
 *     and so it DOES reach this selector. It still selects nothing here: no authored trigger begins
 *     with a FastPath verb (`ToolVocabularyReachabilityTest`), and rule 4 would need "открой"/"open" to
 *     be a name or qualifier token of a shortcut. Whether stripping the verb would now convert such
 *     commands is a scope question for phase 3 and the owner — spec §11 lists replacing `ToolSelector`
 *     as a non-goal of A4′ — not a decision this rule takes on its own.
```

`ToolSelector.kt:101-102` at HEAD (`:102-103` after the edit above): replace
```kotlin
 * because of `"в"`. Both are verbless, so unlike the verb-led case above — which never reaches this
 * selector at all — these **do** reach step 2b and are declined here. The trade is deliberate: a
```
with
```kotlin
 * because of `"в"`. Both are verbless, so unlike the verb-led case above — which reaches this selector
 * only since A4′ phase 1, and only when its app name carries a second goal — these reach step 2b
 * routinely and are declined here. The trade is deliberate: a
```

`ToolVocabulary.kt:49-51`: replace
```kotlin
 * **A trigger FastPath already claims is a capability that exists only in this table.** FastPath runs
 * first and `RouteCommandUseCase` only builds a `GoalShape.Free` goal for a command it left undecided,
 * so a trigger colliding with a FastPath verb never reaches the planner. Six of the triggers this task's
```
with
```kotlin
 * **A trigger FastPath already claims is a capability that exists only in this table.** FastPath runs
 * first and `RouteCommandUseCase` only builds a `GoalShape.Free` goal for a command it left undecided,
 * so a trigger colliding with a FastPath verb does not reach the planner as itself. Since A4′ phase 1
 * a verb-led command is left undecided when its app name carries a second goal, but its text then
 * still begins with the verb, and no entry here does. Six of the triggers this task's
```

- [ ] **Step 7: run both modules the diff touches.** Expected: `:domain` 455/0, `:data:repository`
  388/0. The second one has KDoc changes only, but it is also where the matcher, the reachability
  guard and `B15` live.

- [ ] **Step 8: boundary gate from a cleared tree, then the frozen-file checks**

```bash
git status --porcelain --untracked-files=all
tools/gate.sh
git diff --stat 1f5dbbf -- domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt \
                         domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/IntentActionResolver.kt   # must print nothing
```
Expected: `GATE GREEN`, `TOTAL tests=1477`; `:domain` **455** (+4), `:data:repository` 388.

- [ ] **Step 9: commit (controller)**

```bash
git add domain/src data/repository/src/main
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф1): рукав запуска — сначала резолв, потом вера в 0.90

Спека §5 с поправкой владельца F1-3. Заявленный запуск резолвится ДО гейта
уверенности; если имя целиком не разрешилось и несёт вторую цель, заявка
снимается (LowConfidence, 0.30). Без второй цели — это факт о мире:
NoAppFound и ветка A0 «запустить → магазин» байт-в-байт, включая вариант
установленного бренда («открой youtube music», «google maps» при «Картах»).

Вторая цель спрашивается ТОЛЬКО после провала резолва: «Pack and Send»
по-прежнему запускается (M3b). История пишет заработанную уверенность:
(LAUNCH_APP, ≈0.30) — ровно отозванные фазой 1 заявки.

Мутация (а) M3a «безусловные 0.90» -> 2 RED. (б) снята вместе с правилом
префикса (F1-3); (в) — в задаче 4.

KDoc ToolSelector и ToolVocabulary: посылка «глагольная команда не доходит
до 2b» перестала быть верной — исправлено; вопрос снятия глагола назван
открытым, не решён. RouteCommandUseCase и IntentActionResolver не тронуты.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: the seam — real matcher, real use case, real router

**Spec:** §5.4 **(в)** — a separate test that `NoAppFound` → store survives, so that breaking A0 goes
red on its own — and §10 criterion 1. It also carries the SDD rule A1′ paid for: when a block
creates a new seam between layers, a task must carry a test that crosses it (§0 «Механика
SDD-блока»).

**Module:** `:data:repository`, the lowest module that sees both the real matcher and `:domain`.
**Depends on:** Tasks 2 and 3.

**Files:**
- Create: `data/repository/src/test/java/com/sidr/launcher/data/repository/intent/FastPathOverflowSeamTest.kt`

**Interfaces.**
- Consumes, all real: `RuleBasedIntentMatcher`, `HandleUserCommandUseCase`, `IntentActionResolver`,
  `RouteCommandUseCase`, `StartAgentSessionUseCase`, `TemplatePlanner`.
- Consumes, from `:core:testing` (each checked to exist with the member named):
  - `FakeInstalledAppsRepository.appsToReturn`
  - `FakeActionExecutor.executedActions`
  - `FakeCommandPlanner.planCallCount` (default `PlanResult.NoPlan`)
  - `FakeAgentSessionStore.saved`
  - `FakeToolRegistry.withA0Tools()`
  - `FakeActionCatalog()`, `FakeFeatureFlagRepository`, `FakeConnectivityChecker`,
    `configuredProvider()`

- [ ] **Step 1: write the seam test**

```kotlin
package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The seam A4′ phase 1 creates, crossed once, with every layer real that a JVM test can have.**
 *
 * Phase 1 moves a decision ACROSS a layer boundary: whether FastPath's 0.90 is earned is now decided
 * in `HandleUserCommandUseCase`, and what that decision is FOR happens one layer up, in
 * `RouteCommandUseCase` — step (2) (the A0 store plan) versus steps (3)–(7) (local-only message, or the
 * model). Each layer's own tests use a fake of the other (`RouteCommandUseCaseTest` drives
 * `FakeIntentMatcher`), which is exactly how A1′ shipped four green layers and a dead capability
 * (§0 «Механика SDD-блока»). So here the matcher is the real [RuleBasedIntentMatcher], the use case
 * and the resolver are real, and the router is real; only the world (installed apps, executor, model
 * planner, provider, connectivity, session store) is faked, each in the state that makes the branch
 * under test the live one.
 *
 * Fixtures (R14-43): "Lumora" is the only installed label; "veltrix", "quendor saga" and "corvane"
 * are not installed; "lumora studio" extends the installed label ON PURPOSE (owner fork F1-3's case).
 * Every expected value is compared by equality, never by `contains`.
 */
class FastPathOverflowSeamTest {

    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
    }
    private val executor = FakeActionExecutor()
    private val modelPlanner = FakeCommandPlanner()
    private val agentStore = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    private fun router(localOnly: Boolean = false) = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = RuleBasedIntentMatcher(),
            resolver = IntentActionResolver(apps),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        planner = modelPlanner,
        catalog = FakeActionCatalog(),
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(TemplatePlanner(), agentStore, ids, FakeToolRegistry.withA0Tools()),
    )

    /**
     * Spec §5.4 (в), §10 criterion 1: the A0 branch — accepted by the owner on the device 2026-08-22
     * and written into `DOC-ADL-3`'s second amendment — survives phase 1 byte for byte. Held
     * separately from the phase's positive tests so that breaking A0 goes red on its own. Three
     * shapes: a one-word name, a two-word name with nothing of it installed, and an installed brand's
     * variant that is not installed — owner fork F1-3 (2026-09-26): the world, not the parse, so the
     * store offer stays.
     */
    @Test
    fun `a launch naming nothing installed still becomes the A0 launch-then-store plan, byte for byte`() = runTest {
        val outcomes = listOf("открой veltrix", "открой quendor saga", "открой lumora studio").map { router().route(it) }

        outcomes.forEach { assertTrue("expected an agent session, was $it", it is CommandOutcome.AgentSessionStarted) }
        assertEquals(
            listOf(
                AgentGoal("открой veltrix", GoalShape.AppNotInstalled("veltrix")),
                AgentGoal("открой quendor saga", GoalShape.AppNotInstalled("quendor saga")),
                AgentGoal("открой lumora studio", GoalShape.AppNotInstalled("lumora studio")),
            ),
            agentStore.saved.map { it.goal },
        )
        assertEquals(
            listOf(
                ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("veltrix"))),
                ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            ),
            agentStore.saved.first().plan.steps.map { it.invocation },
        )
        assertEquals(0, modelPlanner.planCallCount)
    }

    @Test
    fun `a launch carrying a second goal reaches the model instead of the store, even when its app is installed`() = runTest {
        val outcome = router().route("открой lumora и проверь почту")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertEquals("the model is asked exactly once", 1, modelPlanner.planCallCount)
        assertTrue("no store plan may start", agentStore.saved.isEmpty())
        assertTrue("nothing is launched", executor.executedActions.isEmpty())
    }

    /** `DOC-ADL-3` as amended: in a local state the model is not asked and nothing leaves the device. */
    @Test
    fun `in local-only mode the same command says so plainly and consults nothing`() = runTest {
        val outcome = router(localOnly = true).route("открой lumora и проверь почту")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, modelPlanner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    @Test
    fun `a launch carrying a second goal with nothing of it installed reaches the model too`() = runTest {
        val outcome = router().route("open corvane and send the report")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertEquals(1, modelPlanner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    @Test
    fun `a search carrying a second goal is not run and reaches the model`() = runTest {
        val outcome = router().route("найди билеты в казань и закажи такси")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertTrue("the search must not run with the second goal inside it", executor.executedActions.isEmpty())
        assertEquals(1, modelPlanner.planCallCount)
    }

    /** Spec §5.2: «не понижаем весь рукав» — at the seam, not only in the matcher. */
    @Test
    fun `a search whose conjunction joins two nouns is still run by FastPath and the model is not asked`() = runTest {
        val outcome = router().route("найди рецепт хлеба и соли")

        assertEquals(CommandOutcome.Executed, outcome)
        val search = executor.executedActions.single() as ExecutableAction.OpenSearchAction
        assertEquals("рецепт хлеба и соли", search.query)
        assertEquals(0, modelPlanner.planCallCount)
    }
}
```

**A narrowed fidelity, said rather than hidden.**
- Step 2b here runs `TemplatePlanner` over `GoalShape.Free`, which answers `NoPlan`, so the command
  falls through to steps (3)–(7).
- `ToolMatchPlanner` is not in this composition. P8 says why it would plan nothing for these texts,
  and the prototype's `B15` run shows `PLANNED` 0 for all seven rows.

- [ ] **Step 2: run — expect green the first time, which proves nothing yet.** `tools/gate.sh
  --scoped :data:repository:testDebugUnitTest` → `data/repository tests=394 failures=0`.

  **A test written after its implementation has never been seen red.** Step 3 is what makes these
  six count.
  - Measured against HEAD's use case and matcher, four were red: the two second-goal launches, the
    local-only one, and the search.
  - The other two pin preserved behaviour. Only M4c and M4d prove them.

- [ ] **Step 3: mutations — every seam test seen red at least once**

| # | Plant (same file and text as) | Module(s), one invocation each | Predicted `FAILED ::` |
|---|---|---|---|
| M4a | M3a (`UNEARNED_LAUNCH_CONFIDENCE` → `0.90f`) | `:data:repository` | `FastPathOverflowSeamTest >` `a launch carrying a second goal reaches the model instead of the store, even when its app is installed`, `in local-only mode the same command says so plainly and consults nothing`, `a launch carrying a second goal with nothing of it installed reaches the model too` — **exactly three** |
| M4b | M2a (`val secondGoal = false`) | `:data:repository` | `RuleBasedIntentMatcherTest >` `a search whose query carries a second goal…`, `a Turkish search…`; `FastPathOverflowSeamTest > a search carrying a second goal is not run and reaches the model` — **exactly three** |
| **M4c = spec §5.4 (в)** | U · the line `        return SecondGoal.carriedBy(intent.displayNameQuery)` → `        return true` (every `NoAppFound` is withdrawn, i.e. A0 is broken through the new code) | **three runs:** `:domain`, `:data:repository`, `:feature:launcher` | see below |
| M4d | M2b (`if (secondGoal) 0.30f else 0.90f` → `0.30f`) | `:data:repository` | `RuleBasedIntentMatcherTest >` `a search whose conjunction joins two nouns…`, `search weather tomorrow returns SearchIntent`, `ru and tr forms match english confidence parity`; `FastPathOverflowSeamTest > a search whose conjunction joins two nouns is still run by FastPath…` — **exactly four** |

**M4c's predicted set is measured:**
- `:domain`: **13** — `domain tests=455 failures=13`:
  - `HandleUserCommandUseCaseTest`:
    - `a launch naming an installed brand's variant that is not installed is still claimed`
    - `app not found returns Message without executing`
  - `RouteCommandUseCaseTest`:
    - `a command for an app that is not installed becomes an agent session`
    - `the agent runs in local-only mode and the model planner is still never consulted`
    - `the goal handed to the agent carries the extracted app name, not the whole command`
    - `when the agent produces no plan the FastPath outcome is returned byte-for-byte`
    - `the agent runs offline and with no provider configured, and nothing is transmitted`
  - `AgentEgressSentinelGuardTest`:
    - `no provider configured - the agent still runs and the seam is still never asked`
    - `local-only off and online - the whole session runs and nothing leaves the device`
    - `local-only on and offline - …`
    - `local-only off and offline - …`
    - `local-only on and online - …`
    - `the sentinel does reach the on-device tool boundary`
- `:data:repository`: **1** — `FastPathOverflowSeamTest > a launch naming nothing installed still
  becomes the A0 launch-then-store plan, byte for byte`.
- `:feature:launcher`: **2** — `LauncherViewModelTest > refusing consent returns the launcher to its
  resting home state` and `LauncherViewModelTest > dismissing the agent surface returns the launcher to
  its resting home state`.

**What makes (в) independent of (а):**
- Under M4c, every positive test of the phase stays green, and only A0's tests go red.
- Under M3a/M4a, A0's tests stay green.
- Of M4c's thirteen `:domain` reds, twelve are the pre-existing A0 guard that P11 measured, and the
  thirteenth is F1-3's pin.
- The seam test is the only one among them that crosses the real matcher.

- [ ] **Step 4: boundary gate from a cleared tree**

```bash
git status --porcelain --untracked-files=all   # exactly the one new file
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1483`; `:data:repository` **394** (+6), `:domain` 455, the rest
unchanged.

- [ ] **Step 5: commit (controller)**

```bash
git add data/repository/src/test/java/com/sidr/launcher/data/repository/intent/FastPathOverflowSeamTest.kt
git commit -m "$(cat <<'EOF'
test(agentic-6/A4' ф1): шов фазы — настоящий матчер, use case и роутер

Спека §5.4 (в), §10 критерий 1: ветка A0 «нет приложения → магазин» держится
ОТДЕЛЬНЫМ тестом, пересекающим новый шов снизу доверху, в трёх формах —
включая вариант установленного бренда (F1-3). Слоевые тесты пользуются
фейком соседа — ровно так A1′ отгрузил четыре зелёных слоя и мёртвую
способность.

Мутация (в) M4c — каждое NoAppFound отозвано — краснит 13 в :domain, 1 здесь,
2 в :feature:launcher, и НИ ОДНОГО позитивного теста фазы. По одному вызову
scoped на модуль: без --continue Gradle не запускает новых задач после
первого падения, и какие модули успеют — вопрос расписания (ревью B).

Каждый тест шва увиден красным хотя бы раз: M4a, M4b, M4c, M4d.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: re-measure `B15`, and close the phase — the close is owner-gated

**Spec:** §5.5 (doctrine), §9, §10 criteria 1 and 11. Files are listed per step. Nothing in this task
changes production code.

- [ ] **Step 1: the boundary gate, from a genuinely cleared tree**

```bash
git status --porcelain      # must be clean
tools/gate.sh
```
**Predicted decomposition, written before the run:**

| Module | Baseline 1463 | Δ | Predicted |
|---|---:|---:|---:|
| `:domain` (`jvmTest`) | 445 | +6 (T1) +4 (T3) | **455** |
| `:consumer:jvm` | 57 | 0 | **57** |
| `:data:repository` | 384 | +4 (T2) +6 (T4) | **394** |
| `:feature:launcher` | 200 | 0 | **200** |
| `:app` | 61 | 0 | **61** |
| the other eight | 316 | 0 | **316** |
| **TOTAL** | **1463** | **+20** | **1483** |

- Per-task chain: 1463 → T1 1469 · T2 1473 · T3 1477 · T4 1483 · T5 1483.
- `557 actionable tasks: 557 executed`: no module and no task is added.
- A mismatch in either direction is a finding to run down before anything is committed.
- **The known timing-sensitive test** (spec §9.1) is `:data:ai-cloud`, `first token arriving just
  under the deadline still completes normally`. Red there is a reason to re-run, not to believe.

- [ ] **Step 2: re-run `B15` on the finished tree — and make sure the report is fresh**

```bash
ls data/repository/src/test/resources/b15/{apps.jsonl,corpus.txt,shortcuts.jsonl}   # all three must exist
rm -f data/repository/build/reports/b15/selection-decline.txt                        # a SKIPPED run leaves an old one
tools/gate.sh --scoped :data:repository:testDebugUnitTest
/usr/bin/grep -rhoE 'B15 :: [A-Z]+' data/repository/build/test-results || echo "B15 ran (no SKIPPED marker)"
cat data/repository/build/reports/b15/selection-decline.txt
```
If `B15 :: SKIPPED` prints, or the report is missing, the step is **unexecuted**, not green: say so and
stop.

Expected: §0.2's after-table exactly. `HANDLED_BY_FASTPATH` **6**, `DECLINE_NO_MATCH` **16**,
`DECLINE_DYNAMIC_UNQUALIFIED` 3, `PLANNED` 0. Rows 1, 2, 3, 4, 7, 8 and 12 now read `-> fastpath:
LowConfidence`.

- [ ] **Step 3: record it where `B15` lives — one truth in one place.** Append `## 9. Повторный прогон
  после A4′ фазы 1 — 2026-09-2x` to [2026-09-20-b15-selection-decline.md](2026-09-20-b15-selection-decline.md).
  It carries:
  - the before/after table and the seven rows;
  - the sentence **"рост доли отказов 48 % → 76 % — это фаза, а не регрессия: семь строк больше не
    получают уверенный неверный ответ и доходят до 2b и модели; `PLANNED` остаётся 0 — работать они
    начнут в фазе 3"**;
  - F1-3's finding that the prefix rule caught no row by itself.

  `§HANDOFF` and this plan's status point there; they do not copy the table.

- [ ] **Step 4: raise the gate's floor** — three lines in `tools/gate.sh`, each replaced exactly.

`:19-20`:
```
#                        baseline floor (`BASELINE_TESTS` below — 1463 since A4′ phase 0; it was
#                        1440 before) before it will say `GATE GREEN`; short of the floor, or any
```
→
```
#                        baseline floor (`BASELINE_TESTS` below — 1483 since A4′ phase 1, 1463 after
#                        phase 0, 1440 before) before it will say `GATE GREEN`; short of the floor, or any
```

`:195`:
```
BASELINE_TESTS = 1463  # after A4′ phase 0 (1440 before it); a boundary total below this is RED
```
→
```
BASELINE_TESTS = 1483  # after A4′ phase 1 (1463 after phase 0, 1440 before it); a boundary total below this is RED
```

`:236`:
```
    print(f"BASELINE after A4′ phase 0: tests={BASELINE_TESTS} failures=0 errors=0")
```
→ the same line, with `phase 1`.

Then run `tools/gate.sh` once more. Expected: `BASELINE after A4′ phase 1: tests=1483`, `GATE GREEN`.
This follows the R18 precedent: the floor's behaviour was proved in phase 0, and this changes its
number, not its logic.

- [ ] **Step 5: the doctrine line — BLOCKED until the owner chooses** (§0.6 item 2).

Neither edit is made without the owner's word. Both are written out here, so that the choice is the
owner's and not a drafting job for whoever closes the phase.

**Reading (a) — `DOC-NYH-1` already covers it, and gets a real anchor.**
- The row text is unchanged, and the count stays **30**.
- Append to its third column (`sidr-doctrine-matrix-v1.0.md:111`):
  ```
   · `HandleUserCommandUseCaseTest` (заявленная уверенность зависит от остатка — A4′ ф1) · `FastPathOverflowSeamTest`
  ```
- Add a §6 journal row dated the day of the edit:
  > якорь, а не правило: прежние якоря проверяли маршрутизацию и арифметику порога, не
  > заработанность `0.90`

**Reading (b) — overflow is not ambiguity; add a new row** (change-control §5 «добавить правило»).
- Insert after the `DOC-NYH-3` row:
  ```
  | `DOC-NYH-4` | <формулировка владельца> | `unit` | `HandleUserCommandUseCaseTest` · `RuleBasedIntentMatcherTest` · `FastPathOverflowSeamTest` |
  ```
- The rule's wording is the owner's. A draft is offered only as a draft:
  > FastPath не заявляет уверенность, не заработанную остатком команды: вторая цель в остатке
  > снимает заявку
- `**Правил: 30.**` → `**Правил: 31.**`.
- `CLAUDE.md:33-34`: "30 `DOC-*` rules" → 31.
- Add a §6 journal row «Правило ДОБАВЛЕНО», in the form of the 2026-09-20 rows (`:222-223`).

Either way, run `tools/gate.sh --scoped :app:testDebugUnitTest` (it includes
`DoctrineMatrixGuardTest`). Expected: green, 61. **A test name that does not exist is a build
failure** — that is the table's purpose.

- [ ] **Step 6: write `§HANDOFF` — subsection «Фаза 1 A4′», under «Фаза 0 A4′».** Keep it to what is
  true, and point rather than copy. It covers:
  - `CODE-GREEN` at **1483**, with the per-task chain. No device round, by design.
  - The owner forks F1-1 to F1-3 and their answers. F1-3's trigger: the prefix rule returns with
    offline understanding; the choice card is the alternative without a model.
  - The §0.6 items and their state at close, and the §0.6 hypotheses, marked as hypotheses.
  - What was not done (§0.5), and the plan-review record (§0.8).
  - The measured tooling fact: without `--continue`, what a multi-module scoped run reports is
    truncated by Gradle's scheduling, not by module order.

  **`CLAUDE.md` and `current-status.md` are corrected only where phase 1 falsified them. No content is
  duplicated into them.**
  - `CLAUDE.md`: the gate-baseline paragraph (`:684-705`: 1463 → 1483, with the decomposition), and
    the A4′ table row (`:75`: one line — phase 1 `CODE-GREEN` 1483, record in `§HANDOFF`).
  - `current-status.md`: one header entry, pointing to `§HANDOFF`.
  - By the rule of 2026-09-20, re-read its last three sections («Not claimed done», «Source of truth»,
    «Keeping this file honest») and correct whatever phase 1 falsified.

- [ ] **Step 7: status header of this plan.** Record: executed, `CODE-GREEN` at the measured number,
  the commits, and every deviation from this body, named. The body is left as written.

- [ ] **Step 8: propose the phase commit to the owner**

```bash
git add docs/ tools/gate.sh CLAUDE.md ai-context/current-status.md
git commit -m "$(cat <<'EOF'
docs(agentic-6/A4' ф1): фаза 1 закрыта — CODE-GREEN на 1483

Переполнение FastPath (спека §5 с поправкой владельца F1-3): семь строк
корпуса B15 из тринадцати перестали получать уверенный неверный ответ и
доходят до 2b и модели; HANDLED_BY_FASTPATH 13 -> 6, PLANNED по-прежнему 0 —
работать они начнут в фазе 3. Рост доли отказов 48 % -> 76 % — фаза, а не
регрессия (документ B15, §9).

Доктрина: <прочтение (а) или (б), выбранное владельцем>.

Устройства не было и быть не должно. Видимое — гипотезами в §HANDOFF.
Пол tools/gate.sh -> 1483.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```
Then **stop and propose it to the owner.** The agent commits; the agent never pushes.

---

## Self-review against the spec

| Spec item | Task | Complete? |
|---|---|---|
| §5 preamble: repair only **before** the confidence gate | 3 | yes — resolution moved above the gate (`:52-61`), execution stays below it (P1) |
| §5.0: FastPath may not silently discard | 1, 2, 3 | yes — the second-goal rule, in both arms |
| §5.1: the prefix discriminator | — | **deferred by owner fork F1-3**; kept in Appendix A with its trigger |
| §5.1: A0 byte for byte | 3, 4 | yes — including an installed brand's variant; M4c |
| §5.2: discarded-content rule, no stop-words, arm not downgraded | 1, 2 | yes — M2b is the forbidden move and goes red; the two-verb rule protects how-to searches |
| §5.2: «найди мне работу» stays | — | yes — row 13, named |
| §5.3: the 36 % untouched; 2b and the three-state order unmoved | frozen | yes — `RouteCommandUseCase.kt` and `IntentActionResolver.kt` are not in the diff (Task 3, step 8) |
| §5.3: the deliverable is "stopped claiming", not "works" | §0.2, 5 | yes — `PLANNED` stays 0, and the plan says so |
| §5.4 (а) | 3 (M3a), 4 (M4a) | yes |
| §5.4 (б) | — | void since F1-3 (§0.1) |
| §5.4 (в) | 4 (M4c) | yes — A0's tests only; no positive test of the phase |
| §5.4: fixtures R14-43 | 1–4 | yes — invented names, equality assertions only, and the one deliberate extension explained |
| §5.5 / §2: the doctrine line | 5, step 5 | **owner-gated** — both readings written out, neither chosen |
| §9.3: modules | all | `:domain` + `:data:repository`, with test-only files in both; no other module |
| §10 criterion 1 | 3, 4 | yes — (а) and (в), as separate mutation families |
| §10 criterion 10 | frozen | yes |
| §10 criterion 11 | 5 | 1483 predicted, from a cleared tree, 557 executed |

**Type consistency:**
- `SecondGoal.carriedBy`, `CONJUNCTIONS_BY_LOCALE`, `VERBS_AFTER_CONJUNCTION_BY_LOCALE` and
  `VERBS_BEFORE_CONJUNCTION_BY_LOCALE` are spelled identically in Tasks 1, 2 and 3 and in the guard.
- `UNEARNED_LAUNCH_CONFIDENCE` and `overreaches` are private to `HandleUserCommandUseCase`; M3a/b,
  M4a and M4c address them by exact text.
- Every mutation's old string was planted with `plant` on prototype v2, and occurs exactly once in
  the code this plan gives.

**Placeholders.** Every code step carries the code that compiled and ran on the prototype. Task 5 has
three deliberate blanks, none knowable before it happens:
- step 5's rule wording, which is the owner's by design;
- the date in step 3's heading;
- the commit hashes in step 7.

---

## Appendix A — the deferred prefix rule, kept for its trigger (owner fork F1-3)

**Trigger:** the launcher gains offline understanding (Master Plan row `B16`: a local model behind
the existing ports).
- **Why that is the moment:** a withdrawn command then reaches a model even offline. The rule's cost
  disappears — brand variants lose the A0 store offer only where a model can take over.
- **What it keeps gaining:** verbless compounds («открой gmail новые письма») stop getting a store
  search with a wrong name.

**Status:** prototyped on 2026-09-26 (prototype v1). Review B re-executed all of it from the plan
text, and every set matched.

**What returns, verbatim as proved:**

In `IntentActionResolver`, before `private fun resolveSimpleCommand(`:
```kotlin
    /**
     * A4′ phase 1 (spec §5.1): does any of [names] equal an installed app's label, compared the way
     * [resolveAppLaunch] compares (`Locale.ROOT` lowercase, whole label)? One label shared by two apps
     * counts — "Сообщения" names installed apps even though it cannot pick one.
     *
     * An empty [names] answers `false` without reading the repository, and a repository failure
     * answers `false` too: "no evidence the parse overreached", so the caller keeps the outcome it
     * already had rather than inventing a new one from an error.
     */
    suspend fun namesAnInstalledApp(names: List<String>): Boolean {
        if (names.isEmpty()) return false
        val apps = (repository.getInstalledApps() as? OperationResult.Success)?.value ?: return false
        val labels = apps.mapTo(HashSet()) { it.label.lowercase(Locale.ROOT) }
        return names.any { it.lowercase(Locale.ROOT) in labels }
    }
```

In `HandleUserCommandUseCase`, the pieces that change:
- `overreaches` becomes `suspend` and gains `normalized`;
- its last line becomes the disjunction below;
- `runsNextToVerb` is added.

The dead `if (tokens.size < 2)` guard of v1 is dropped (review A): `(1 until 1)` is already empty.

```kotlin
        return SecondGoal.carriedBy(query) || resolver.namesAnInstalledApp(runsNextToVerb(normalized, query))
```
```kotlin
    private fun runsNextToVerb(normalized: String, query: String): List<String> {
        val tokens = query.split(' ')
        return when {
            normalized.endsWith(" $query") -> (1 until tokens.size).map { tokens.take(it).joinToString(" ") }
            normalized.startsWith("$query ") -> (1 until tokens.size).map { tokens.takeLast(it).joinToString(" ") }
            else -> emptyList()
        }
    }
```

**Tests that return.** Insert them in `IntentActionResolverTest`, and add
`import org.junit.Assert.assertFalse`:
```kotlin
    @Test fun `namesAnInstalledApp finds a name equal to an installed label, ignoring case, and nothing less`() = runTest {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("net.fixture.alpha", "Lumora"),
            InstalledApp("net.fixture.beta", "Lumora"),
        )

        assertTrue(resolver.namesAnInstalledApp(listOf("veltrix", "LUMORA")))
        assertFalse(resolver.namesAnInstalledApp(listOf("veltrix", "lumora novo")))
        assertFalse(resolver.namesAnInstalledApp(listOf("lum")))   // review A: the "nothing less" half
    }

    @Test fun `namesAnInstalledApp answers no on a repository failure and reads nothing for no names`() = runTest {
        fakeRepo.errorToReturn = OperationError.UnknownError("db down")
        assertFalse(resolver.namesAnInstalledApp(listOf("lumora")))

        fakeRepo.reset()
        assertFalse(resolver.namesAnInstalledApp(emptyList()))
        assertEquals(0, fakeRepo.callCount)
    }
```
And in `HandleUserCommandUseCaseTest`:
```kotlin
    @Test fun `a launch whose remainder only begins with an installed app's name does not claim confidence`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("lumora новые письма"), 0.90f)

        assertEquals(CommandOutcome.LowConfidence, useCase.handle("открой lumora новые письма"))
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test fun `a Turkish launch reads the end of its remainder, next to the verb`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
        matcherReturns(LauncherIntent.LaunchAppIntent("notlar lumora"), 0.90f)

        assertEquals(CommandOutcome.LowConfidence, useCase.handle("notlar lumora aç"))
    }
```

**Mutations proved in v1** — each reddened only its own assertion:

| Mutation | Change |
|---|---|
| (б) | the prefix disjunct → `false` |
| orientation | `takeLast` → `take` |
| whole-label compare | `in labels` → a `contains` over labels |
| empty-names read | the `isEmpty()` guard removed |

**Add on return:** a `startsWith` mutation row for the `"lum"` assertion. Review A showed the
"nothing less" half held nothing without it.

**On return, these are deleted on purpose** — their KDocs say so:
- `a launch naming an installed brand's variant that is not installed is still claimed`;
- the seam's third shape, «открой lumora studio».
