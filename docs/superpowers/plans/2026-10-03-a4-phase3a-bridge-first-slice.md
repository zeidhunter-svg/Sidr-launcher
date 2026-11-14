# A4′ Phase 3a — the bridge, first slice · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: EXECUTED 2026-10-04 … 2026-11-07 — `CODE-GREEN` at 1673 / 0 / 0** (boundary `tools/gate.sh`,
> cleared tree, 557 executed; the plan predicted 1644). The record — commits, the measured gate chain (also
> under §0.8 below), execution findings, controller rulings, the final review and its fix `f6aa141`, the
> owner's answers of 2026-11-07 and what is named at close — is in `§HANDOFF` of the track plan, «Фаза 3a
> A4′». The body below is left as written except where execution corrected it in place («Measured in
> execution» notes).
>
> Original status line: **written 2026-10-03, reviewed once on 2026-10-04 against the tree and the spec, every
> finding applied (§0.11). The owner's three forks answered 2026-10-04 (§0.1). Not started.**
> - Branch `launcher--7`, base `fe495cd`, gate baseline **1560 / 0 / 0**.
> - **Spec:** [§7 in full — §7.12 (owner amendment D1–D3, accepted 2026-10-03) governs wherever it
>   differs from the rest of §7 — plus §9, §10, §11 of
>   2026-09-21-a4-runtime-design.md](../specs/2026-09-21-a4-runtime-design.md). Scope is **exactly
>   §7.12.4 «3a»**: nothing from «3b», nothing from «вне A4′».
> - **Context, not requirements:** the discussion memo
>   [2026-10-03-agent-architecture-discussion-design.md](../specs/2026-10-03-agent-architecture-discussion-design.md)
>   §2, §6, §16.1. Where it differs from §7.12, §7.12 holds.
> - **Form template:** [A4′ phase 2](2026-09-27-a4-phase2-partial-failure-and-undo.md). The record of
>   phases 0–2 and the R-rules this plan inherits are in `§HANDOFF` of
>   [the track plan](2026-08-18-agentic-track-restart.md), «Фаза 0 A4′» … «Фаза 3 A4′».
>
> **How this plan was produced — the owner's rule of 2026-09-26** (`§HANDOFF`, «как пишутся планы фаз
> 2–4»). Run before writing, on a scratch copy of the tree (`git archive fe495cd`), never in the
> repository: **only the one piece of evidence machinery that is new** — the switch that keeps the
> paid, networked transport measurement out of every gate run (§0.3). Nothing in this phase is a
> scope-deciding measurement that execution could not take itself: the transport measurement the spec
> asks for is a **task** (Task 6 builds it, the owner runs it, Task 12 applies it), and it chooses a
> default, not the scope.
>
> **Everything else below is a hypothesis.** That covers every address, every test verdict, every
> predicted `FAILED ::` set and the per-module gate decomposition. Execution checks each one
> (`mutation-prover`); a mismatch is a finding, and the plan is corrected, as phases 0–2 did.

**Goal.** The model stops choosing from seven router actions and starts proposing one step over our
own vocabulary of nineteen authored tools — and everything it proposes that acts in the world runs
through the engine:
- one model path: the router (`CommandPlanner` and its family) is deleted, not flagged (D3);
- the model sees the command and **our** words only; the device binds the model's words to the world
  and never tells the model what it found (D1);
- the answer is strict-shaped, its schema comes from the same filtered set as the prompt, a wrong
  answer gets exactly one retry carrying only a closed error code, and sending the schema to the
  provider is a transport switch chosen by measurement (D2);
- the four conditions of D3 hold: an interpretation card before any session, ambiguity resolved before
  the plan by a pick that binds, a question goes to the assistant, and a paused model plan goes back
  through the route.

**Architecture.**
- **`:domain`, package `com.sidr.launcher.domain.ai.bridge`** (new). The port `ModelPlanner` and its
  closed result `ModelPlanning`; the description port `ToolDescriptions`; the offering filter, the
  renderer and the JSON-schema generator (one source for prompt and schema); the strict validator
  (shape, membership in the offering, derivability of app mentions) with its closed error vocabulary;
  `BridgeModelPlanner` (one retry, a named budget whose ceiling covers the retry); the binding port
  `MentionBinder` and the transient `Interpretation` the card shows.
- **`:data:ai-cloud`.** `OpenAiCompatibleBridgeTransport` — its own non-streaming path, not
  `GenerativeAiEngine`, with three switchable forms (prompt / `response_format` / forced tool).
  `LlmCommandPlanner` is deleted.
- **`:data:repository`.** `web_search` and `open_url` projected as tools; `launch_app` pinnable to a
  package; `AppTargetResolver` returns candidates; `AuthoredToolDescriptions` (our vocabulary);
  `DeviceMentionBinder` (learned choices for `launch_app` only).
- **`:feature:launcher`.** The interpretation card, the binding pick, the visible wait with cancel,
  the prefilled assistant, the honest «model unavailable» lines; the router's card is deleted.
- **`:app`.** `BridgeProvidesModule` replaces `RouterProvidesModule`; the payload guard; the one-path
  guard; the transport measurement harness (excluded from every gate run).

`ActionIds`' seven values, `ObservedFact`, `CommandFailure`, `GoalShape`, `ArgType`, `ToolDescriptor`'s
type, `ExecutionState` and the Room schema are not in the diff.

**Tech Stack.** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain`
(`commonMain` + `jvmTest`) · Ktor 3.0.1 client + `MockEngine` · kotlinx-serialization-json · JUnit4 +
`kotlinx-coroutines-test` · Robolectric + Compose UI test.

---

## Global Constraints

Every task's requirements implicitly include this section.

### Running the gate

- **The gate is `tools/gate.sh`** (ruling R2). Compile-only probes use bare `./gradlew` with
  `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`.
- **Boundary run: `tools/gate.sh` with no arguments.** It closes every task. It clears
  `build/test-results` everywhere, passes `--rerun-tasks`, counts from the JUnit XML, and says
  `GATE GREEN` only at or above its floor (`BASELINE_TESTS = 1560`, `tools/gate.sh:195`).
- **Scoped run: `tools/gate.sh --scoped :module:task`**, for TDD steps and mutations. It labels
  itself `NOT a boundary gate`.
- **A mutation whose red set spans modules is run once per module** (no `--continue`; phase 1, review
  B). Every prediction below is given per module.
- **The Gradle daemon is ON** (owner, 2026-09-25, R8). **`--rerun-tasks`, never `--rerun`.** Counts
  from the XML, never the console, never through `tail`.
- **Stop the daemon (`./gradlew --stop`) before a boundary gate that follows a batch of mutations**
  (phase 2, T2: a two-hour daemon drove the machine into swap and reddened a boundary run by timeouts).
- **Known timing-sensitive test:** `:data:ai-cloud`'s `first token arriving just under the deadline
  still completes normally`. A red there is a reason to re-run from a cleared tree, not to believe.
- **`:core:ui` is not touched by this plan** (§0.4 P19), so `verifyRoborazziDebug` is not part of any
  task's gate. If execution finds it must touch `core/ui`, that is a finding: stop and add the
  Roborazzi run to the task.

### Reading the results

- **Baseline 1560 / 0 / 0**, per module:

  | Module | Tests |
  |---|---:|
  | `:domain` | 480 |
  | `:consumer:jvm` | 72 |
  | `:data:repository` | 410 |
  | `:feature:launcher` | 216 |
  | `:app` | 66 |
  | the other eight (`:data:ai-cloud` **32** among them) | 316 |

  **Predict the decomposition before every run and compare after** (§0.8 carries the whole chain). A
  total cannot tell "two added" from "two added and two lost" — and this phase deletes about forty
  tests on purpose, so the total alone would hide a lost guard behind a new test.
- **`:domain`'s `FAILED ::` lines end in `[jvm]`.** The predicted names below omit it.
- **A compile-red TDD step prints other `e:` lines besides the one named.** The named one must be
  among them.
- **Line addresses are at HEAD `fe495cd`.** Inside a task an earlier edit shifts later lines. Every
  instruction also quotes the text it replaces, and that text is what to match.

### The mutation protocol

Unchanged from phase 2 (ruling R1, never `git checkout`). Save the helper once per session, **at
column 0**:

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

- **A mutation that plants a NEW file** (Task 11, M11g) uses `printf '%s\n' '<content>' > "$F"` in
  place of `plant`, and its restore is `rm -f "$F"`, checked with `test ! -e "$F" && echo RESTORED`.
- In the mutation tables `\|` is a literal `|` and `\n` a newline; the plant text is the unescaped
  string.
- **A mutation is proved by WHICH tests failed**, read off the `FAILED ::` lines against the
  prediction. A different red set, `PLANT REFUSED`, or `gradle=1` with **no** `FAILED ::` line (a
  compile error) proves nothing: restore, find out why, run again. `RESTORED` must print.
- **Roles (R4, R10).** The implementer writes a task and the controller commits it; a reviewer checks.
  Mutations are proved by `mutation-prover`, or by the controller in the foreground. After any
  restart: `git status --porcelain --untracked-files=all` and `git log --oneline -3` first.
- **The last action of any mutation-proving task is a boundary gate from a cleared tree** (R14-44),
  before the commit.

### Fixtures and frozen code

- **Fixtures (R14-43): no two expected values share a substring, and none is a real app.**
  - Apps: «Lumora» `net.fixture.lumora` · «Vemblic» `net.fixture.vemblic` · two «Tessar»
    `net.fixture.tessar.one` / `net.fixture.tessar.two` · «Zantor» `net.fixture.zantor`.
  - Aliases: «кварцелот» → `net.fixture.zantor`; «плимдорф» → `net.fixture.plimdorf` (never typed).
  - **Added in execution (Task 7 review):** the plan's own Task 7 code also uses two «Pellix»
    `net.fixture.pellix.one` / `net.fixture.pellix.two` and the alias «телега» → `net.fixture.zantor`.
  - Learned choice: slot `tessar` → `net.fixture.tessar.two`; slot `фальмира` → `net.fixture.falmira`
    (never typed).
  - Shortcut: package `net.fixture.qorvath`, app label «Qorvath», shortcut label «Brimwell».
  - Model text: question «почему небо синее», URL `https://quillmere.example/path?q=a&b=c`, search
    «ostravel lectures».
  - Compare **by equality**. A substring check is allowed only where the test says why no other value
    can contain the needle.
- **Frozen, and checkable with `git diff --stat fe495cd -- <path>` at close:**
  - spec §10 criterion 10: `ActionIds` (values), `ObservedFact`, `CommandFailure`, `GoalShape`,
    `ArgType`;
  - **also frozen by this plan:** `ToolDescriptor`'s type, `ExecutionState`, `TraceEvent`, the Room
    schema (stays 4, no `@Entity` edit — `MODEL_PROPOSED` rides the existing TEXT column, the
    `COMPENSATION` precedent), every `strings_locked.xml` (no Class B key), everything under
    `core/ui/src/main`, everything under `consumer/jvm/src`.
- **`:domain` is `commonMain`: stdlib + coroutines only.** Tests live in `jvmTest`. This plan adds
  `kotlinx-serialization-json` to `:domain`'s **`jvmTest`** dependencies only (Task 3, §0.2 B-5) —
  production `:domain` stays stdlib + coroutines.
- **Strings:** every new key ships `en`/`ru`/`tr` in the same commit, in
  `feature/launcher/src/main/res/values*/strings.xml` (never `strings_locked.xml`). New `tr` text is
  for the owner's Turkish review at block acceptance (§0.7).
- **A line predicting what the user will see is a hypothesis** until run once on a phone, and is
  written in that tone (§0.7).

### Commits

- **The plan is committed before Task 1**, together with the `§HANDOFF` pointer and the owner's forks
  (§0.1). The `git status` expectations below count only a task's own files.
- Russian conventional commits, scope `agentic-6/A4' ф3a`, trailer
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (R5).
- **The agent commits and never pushes.** The phone and its network are not touched;
  `connectedAndroidTest` is never run.
- **Task 13, the close, ends in a *proposed* commit** (R7).

## Review Focus

Five input classes the spec implies but no criterion names. Each is likely to bite a person using this
build, and each is pinned by a test in the task that owns the code.

1. **A double tap on «Выполнить».** The card starts a session through `startPlanned`, which mints a new
   id per call, so the store does not de-duplicate two taps: two taps would run the step twice.
   → Task 9, `a second Run tap while the first is still starting starts one session`.
2. **The user edits the command while the card is on screen.** The card describes the old text; running
   it would act on a command the user has already abandoned.
   → Task 9, `editing the command dismisses the interpretation`.
3. **A model question carrying `&`, `?`, `#` or `=`.** The assistant prefill rides a navigation route;
   an unencoded question would be cut at the first reserved character or parsed as another argument.
   → Task 9, `a question with reserved characters reaches the assistant intact`.
4. **A provider that answers HTTP 200 with an error object instead of a completion** (OpenRouter does
   this for some upstream failures). It must be read as an unreadable reply, never as an answer.
   → Task 6, `a 200 that carries an error object is unparseable`.
5. **An app named in a word form** — «открой телегу» for an alias «телега», `telegramı`. Spec §7.12.1
   names this as a limit of 3a: the mention is derivable (the model copied the user's word), but no
   label or alias matches it. It must end as an honest «нет такого приложения», never as a guess and
   never as a second question to the model.
   → Task 7, `a word form that matches nothing is not found, never guessed`; Task 11,
   `a mention that matches no app says so and asks the model nothing more`.

---

## 0. Pre-flight scan of this plan against the tree

### 0.1 · The owner's forks — three, none blocks Tasks 1–10

Each is put the way the owner asked for owner items: what it means, the options with their cost, a
recommendation, and a one-line answer format. **Answer format for all three, one line:**
`A: <allow|deny> · B: <a|b> · C: <a|b>`.

> **Owner's answer, 2026-10-04: `A: allow · B: a · C: a`** — all three as recommended. So Task 3 builds
> the allow-list (`OFFERED_LEVELS = setOf(IN_APP, SYSTEM_INTENT)`) and its test reads `…is not offered`;
> Task 11 builds `OfferedToolIdsFreezeGuardTest`, its total is **1644** and ADR 2 carries the freeze
> clause; F3a-C (a) changes no task — the alias limit is named at close (Task 13 Step 5). The (deny)
> and (b) branches below are the record of what was weighed, not instructions.

**F3a-A — the shape of the offering filter (spec §7.6 against §7.12.1).** Spec §7.6, written
2026-09-21, gives the required filter as `registry.all().filter { it.level != ToolLevels.APP_SHORTCUT }`
(plus `!= LAUNCHER_MEMORY` from §7.5) — a **deny-list** of levels. §7.12.1, written 2026-10-03 under D1,
makes "the model never sees the user's world" a principle. The two agree today and disagree on the
**next** level: `ToolLevel` is an open value class, and the levels the track expects next —
AppFunctions, MCP — carry third-party names exactly like `app_shortcut`. Under the deny form such a
level is offered to the model **the day its adapter registers**, with every guard green (the payload
guard plants sentinels only for the sources it knows). Under an **allow-list** of levels it is offered
the day someone adds one line, by decision.
- **(allow)** `OFFERED_LEVELS = setOf(IN_APP, SYSTEM_INTENT)`. Fail-closed, the same posture as
  `OutboundContextPolicy` itself. Still a filter on a property every tool declares, not a list of ids:
  tool №21 at `system_intent` is offered with no edit (criterion 8 holds), and spec §7.9's obligation
  for local inference still reads "change one line". Cost: none beyond the spec's form. Test
  `a tool at a level the filter has not heard of is not offered` (Task 3).
- **(deny)** `level !in setOf(APP_SHORTCUT, LAUNCHER_MEMORY)`, the spec's literal form. Cost: the next
  third-party level is offered silently; Task 3's test above inverts to `…is offered`.
- **Recommendation: allow.** It is the reading of §7.6 that survives D1; the spec's sentence «расширить
  потом = удалить строку» becomes «добавить строку», and both are one line.

**F3a-B — freeze the nineteen offered `ToolId` strings (ADR 2, spec §7.12.3).** The router's seven
`ActionIds` were frozen byte for byte as the outbound contract to the model (ADR 3/4). After D3 that
role passes to the whole offered set — nineteen ids, fifteen of which (`system_intent`) were never
derived from an `ActionId`. The spec leaves "are they frozen" to the ADR. Phase 4's plan cache keys
will be built from them.
- **(a) freeze the values, not the size:** renaming or removing an offered id needs a superseding ADR;
  adding a tool does not. Held by `OfferedToolIdsFreezeGuardTest` (Task 11): the production offering
  must contain each of the nineteen strings. Cost: one test, one ADR clause.
- **(b) do not freeze now;** phase 4 decides when it has keys. Cost: a rename in the meantime silently
  orphans whatever was learned or cached under the old id.
- **Recommendation: (a).** It costs one test and forbids only what would break a contract.

**F3a-C — aliases against the honest «model unavailable» (found by reading, §0.4 P14).**
`ResolveCommandWithAliasUseCase` fills **only** `CommandOutcome.Unknown`. Today, with a provider
configured but a broken key, `LlmCommandPlanner` returns `NoPlan`, step 7 hands back FastPath's
`Unknown`, and a bare alias phrase («телега») still launches through the alias. Finding (c) of the A1″
acceptance — which this phase must close — replaces that silence with an honest «провайдер не принял
ключ»; that message is not `Unknown`, so **the alias no longer fires in exactly that case**. (With a
working key the alias gets *better*: the model's `launch_app(app="телега")` is bound through
`AppTargetResolver`, which consults aliases — the router's `launch_app(query=…)` never did.) The same
class already exists since Этап 4.0: under local-only, no provider or offline, an alias phrase gets the
honest «понимание недоступно» line and never reaches the alias.
- **(a) name it, keep the honest message;** the alias-before-the-model question goes to the owner /
  A3 with this measurement. Cost: one case (broken key + alias phrase) is worse than today until then.
- **(b) let the alias decorator also fill the four «understanding unavailable» messages** — an alias
  is deterministic local memory and should not depend on the cloud's state. Cost: one branch, ~3 tests,
  and a behaviour change under local-only (an alias starts working there) that is outside §7.12.4.
- **Recommendation: (a)** — §7.12.4 is the scope; (b) is a good change for a later block, recorded
  with its trigger.

### 0.2 · Decisions this plan takes — the eight the owner listed, and the ones they pull in

Each is decided here with its reason; none is a fork. A reviewer who disagrees raises it as a finding.

**B-1 · The port: `ModelPlanner`, package `com.sidr.launcher.domain.ai.bridge`, result `ModelPlanning`.**
- `fun interface ModelPlanner { suspend fun plan(command: String): ModelPlanning }` — the name says
  "the planner that is a model", against `Planner` (the deterministic port at step 2b and on resume,
  which a model must never sit behind — `DOC-ADL-3`, spec §7.12.2).
- `bridge` is a sub-package of `com.sidr.launcher.domain.ai`, so the three privacy guards' term
  `"com.sidr.launcher.domain.ai"` already covers it (§0.9).
- `ModelPlanning` is closed, six variants — five answers of the model and one outcome of the port
  (spec §7.12.2): `Proposed(step)` · `ToAssistant(question)` · `Navigate(destination)` ·
  `Clarify(question)` · `Declined` · `Unavailable(reason)`. `reason` is
  `ModelUnavailableReason { CREDENTIALS, REQUEST_REJECTED, PROVIDER_FAILING, TIMED_OUT, NETWORK,
  UNREADABLE }`, in `domain.intent` beside `CommandMessage`, which carries it to the screen.
- **One step in 3a.** Spec §7.12.4 puts «многошаговые планы на Android» in 3b, and with them the four
  tests phase 2 deferred «to the first multi-step plan» (N2, T3 m3, T4 M2, T6 m5). So the answer form
  carries one `step` object, not a list. Consequence, named rather than discovered: phase 1's
  two-goal commands («открой gmail и проверь письма») now reach the model and get **one** step's
  reading (or a `clarify`); the card shows exactly that one step, so nothing happens that the card did
  not say. The form widens to a list in 3b.

**B-2 · `CommandOutcome`: `Interpreted(interpretation)` replaces `RoutedAction`; `OpenAssistant`
becomes `data class OpenAssistant(val question: String?)`.** Counted in the tree by grep, which is
complete for this change because turning a `data object` into a `data class` makes every bare
reference a compile error (§0.4 P2):
- exhaustive `when` over `CommandOutcome`: **two**, both in `LauncherCommandSession`
  (`outcomeSummary`, `applyOutcome`); the other two `when`s (`SystemIntentToolWorker.map`,
  `SelectionDeclineMeasurement.describeForReport`) carry `else` and need nothing;
- producers of `OpenAssistant`: `HandleUserCommandUseCase.kt:128` (passes `null`),
  `ExecuteActionUseCase.kt:29` (passes the action's `prompt`);
- tests naming it: `ExecuteActionUseCaseTest:110`, `HandleUserCommandUseCaseTest:215`;
- producer of `RoutedAction`: `RouteCommandUseCase.kt:173` only.

`Interpreted` is added in Task 9 **beside** `RoutedAction`; Task 11 deletes `RoutedAction`. Between the
two, the card is reachable only from tests — on purpose, so the screen lands before the switch.

**B-3 · Binding `launch_app` before the card, keeping learned choices and `IntentActionResolver`.**
- **Before the card:** the model's word for an app goes into `launch_app`'s model-facing argument
  `app` (a *mention*). `DeviceMentionBinder` resolves it with `AppTargetResolver.candidates` — the
  resolver that already never guesses and already consults aliases — and, **for `launch_app` only**,
  the learned-choice store with the same key FastPath uses (`CapabilityKey(LAUNCH_APP,
  LaunchSlotExtractor.slotOf(normalize(mention)))`) and the same policy: a confident learned choice
  binds without asking, a weak one ranks first. `uninstall_app` and `open_app_info` never read learned
  choices (`AppTargetResolver`'s own KDoc: consent for opening is not consent for removing).
- **At execution, `IntentActionResolver` stays the one place that resolves a launch.** Its
  `LauncherIntent.LaunchAppIntent` **already has** `packageName: String? = null`, unused by anything
  today (§0.4 P8). Task 2 makes the resolver honour it: a pinned launch opens that package (or is
  «not found» under the typed name if it is gone); an unpinned one resolves the label exactly as today.
  `LauncherAction.LaunchApp` gains the same defaulted field, and `launch_app` gains an optional tool
  argument `app` (the pin). The user's words go to `query`, so the step line, the `resolved_query`
  output and a «not found» message keep naming what the user said.
- **The new resolver API:** `AppTargetResolver.candidates(query): AppCandidates` with
  `None | One(app) | Several(apps) | Unavailable`; `resolve(query): String?` stays, derived from it, so
  `ToolMatchPlanner` (step 2b) is untouched.
- **A pick binds; it does not launch.** The card holds an `AppChoice(arg, mention, candidates,
  learning)`; a tap writes the package into the step and the card appears. **The learned choice is
  recorded at «Выполнить», not at the pick** — a learned choice is consent for the launch act, and a
  card dismissed after a pick launched nothing. The record goes through the one gated call site
  `LauncherAppLaunch.recordChoiceIfPending` that `ResolutionPrivacyScopeGuardTest` pins (no second
  call site).
- **Why `ToolArgumentSorts` and not a special case:** its row means "the package goes in `arg`, the
  user's words in `labelArg`". `launch_app` gets the row `arg = "app", labelArg = "query"`. That
  `query` is a *required* label argument is safe: the planner rule "a label argument must be optional"
  exists because `ToolMatchPlanner` checks required arguments before resolution, and `launch_app` is
  never selected by the vocabulary at step 2b.

**B-4 · `ALLOWED`: `ACTION_CATALOG_SCHEMA` is replaced by `AUTHORED_TOOL_SCHEMA`** — the set stays four
(spec §7.12.3). The six pins change in Task 11 together with ADR 1 and the payload guard:
`AiRequestGuardTest`, `SuggestionOutboundIsolationTest`, `AliasPrivacyScopeGuardTest`,
`ResolutionPrivacyScopeGuardTest`, `PrayerLocationPrivacyGuardTest`, and `RouterOutboundGuardTest`'s pin,
which is deleted with the router and re-made in `BridgePayloadGuardTest`. **`PromptContextBuilder`
needs no edit:** it names `OutboundContextPolicy.ALLOWED`, not the value (§0.5 S3).

**B-5 · Schema text in pure Kotlin; JSON parsed only in tests.** `:domain` has no serialization library,
so `BridgeRendering.schemaJson` writes the JSON text itself (a fixed, ASCII-only shape: kinds,
destinations, tool ids, argument names). The agreement test (criterion 18) must read the **text** — a
check on a structure the generator also returns would not see "a tool only in the schema" — so
`:domain`'s `jvmTest` gains `kotlinx-serialization-json`. Test-only.

**B-6 · `StepRationale.MODEL_PROPOSED` and the resume outcome.** The new value is persisted by name in
the existing TEXT column (the `COMPENSATION` precedent: zero compile errors, Room's `readRationale`
learns it, `every StepRationale round-trips` goes red until it does). `ResumeAgentSessionUseCase.resume`
returns `OperationResult<Resumption>` with `Resumption = Ran(session) | NeedsRoute(goal)`: for a paused
plan the model wrote in which nothing has acted it returns `NeedsRoute` **before** touching the
deterministic planner. The surface then closes the stale session and re-submits the goal text through
the whole command pipeline, so gates 3/5/6 and the card are met again. `domain/agent` still names no
model port, and `AgentVocabularyGuardTest` gains the bridge's names to keep it that way.

**B-7 · The transport measurement runs outside every gate, by an explicit Gradle property.** Probed
before this plan (§0.3). The harness is a JUnit class in `:app`'s tests that every `Test` task of `:app`
excludes unless the run passes `-Psidr.measureBridgeTransport`; `tools/gate.sh` never passes it. The
owner runs `tools/measure-bridge-transport.sh <models>` with the key in `SIDR_MEASURE_API_KEY`. It
prints `BRIDGE-MEASURE ::` lines and a report (§ Task 6).

**B-8 · The gate floor stays 1560 during the phase and is raised once, at close, to the measured total.**
The deletion of the router takes about forty tests (§0.8: −41 exactly, by the files), but the tasks
before it add more than that, so no boundary run of this phase is predicted below 1560. **The floor
therefore does not watch the deletion — the per-module prediction does**, and Task 11 carries its
decomposition. No mid-phase edit of `tools/gate.sh`.

**B-9 · The model's navigation answers go through the card.** The router showed `open_settings` and
`show_apps` as a one-tap proposal (Fork R4); the card keeps that one tap, so nothing the model says
moves the screen by itself. `ToAssistant` is the exception the spec names: the prefilled, unsent
question **is** the confirmation (spec §7.12.3).

**B-10 · The visible wait is keyed on time, not on the model.** The command session shows «Разбираю
команду…» with «Отмена» when a submitted command has not resolved within 300 ms; FastPath answers
before that and never shows it. Cancel cancels the submit's coroutine, so the model call is cancelled
and no outcome is applied — nothing can run, because only «Выполнить» runs anything.

**B-11 · `RouteCommandUseCase` keeps its package `domain.ai.router`.** It is the routing gate, not the
router; moving it would touch every construction site for no behaviour. Criterion 14 («роутера в дереве
нет») is about the router's types, and `OneModelPathGuardTest` (Task 11) holds it by name.

**B-12 · The interpretation is transient.** It lives in the command session's memory, like today's
pending proposal: process death loses the card (the typed command survives in `SavedStateHandle`), and
nothing about it is written anywhere. The plan on disk, once «Выполнить» starts it, carries the bound
values exactly as every plan does (spec §7.12.1, «не хранится»).

### 0.3 · The one measurement this plan stands on — the measurement switch

Run on a scratch copy (`git archive fe495cd` plus `local.properties`), on `:data:ai-cloud`, with this
block appended to its `build.gradle.kts` and a dummy `BridgeTransportMeasurement` test that prints a
line and makes one public GET:

```kotlin
tasks.withType<Test>().configureEach {
    if (!providers.gradleProperty("sidr.measureBridgeTransport").isPresent) {
        filter { excludeTestsMatching("*.BridgeTransportMeasurement") }
    }
}
```

| Run | Command (after `--console=plain`, toolchain path, `--rerun-tasks`) | Result |
|---|---|---|
| 1 — a gate-shaped run | `:data:ai-cloud:testDebugUnitTest` | `BUILD SUCCESSFUL`, `32 actionable tasks: 32 executed`; XML: **two** suites, 11 + 21 = **32** tests — the measurement class neither ran nor was counted |
| 2 — the owner's run | `… --tests '*BridgeTransportMeasurement' -Psidr.measureBridgeTransport` | the class **ran** (`PROBE :: measurement class executed`); its GET failed with `java.net.SocketException: Сеть недоступна` — this session's sandbox has no network |
| 3 — named, not switched on | `… --tests '*BridgeTransportMeasurement'` | `No tests found for given includes`, `BUILD FAILED`, exit 1 — a refusal, not a silent pass |

- **What run 2 also shows:** `HttpClient(Android)` — the engine `:app` already ships — loads and runs on
  the JVM test classpath up to opening the socket; no `Stub!`. **A real HTTP response was not
  observed** (no network here). Task 6's dry run proves the rest in execution: the property path, the
  key reaching the test JVM, the corpus being read — without a network call.
- **What it does not show:** the switch was probed on a library module; Task 6 puts the harness in
  `:app` (it needs the production offering). The `Test` task type is the same, and Task 6 repeats runs
  1 and 3 on `:app` as its machinery step.

### 0.4 · Facts read out of the tree, with addresses at `fe495cd`

**(P1) The router, in full** (spec §7.12.3's list, checked):
- `:domain` `ai/router/`: `CommandPlanner.kt` (port + `PlanResult`), `ActionProposal.kt`,
  `CatalogSchemaRenderer.kt`, `ProposalValidator.kt`; `RouteCommandUseCase.kt` stays (B-11).
- `:data:ai-cloud`: `LlmCommandPlanner.kt` (2 000 ms, 256 tokens, "first `{` to last `}`").
- `:app`: `RouterProvidesModule.kt` (provides `CommandPlanner` and `RouteCommandUseCase`).
- `:core:testing`: `FakeCommandPlanner.kt`.
- `:feature:launcher`: `PendingRoutedAction.kt`; `LauncherCommandSession` `_pendingRoutedAction`,
  `confirm()`, `cancel()`, the `RoutedAction` arm of `applyOutcome` (`:340-349`), `commandLineFor` stays
  (the web chip uses it); `LauncherViewModel` `pendingRoutedAction` (`:343`), `confirmRoutedAction`
  (`:455`), `cancelRoutedAction` (`:460`), constructor param `actionCatalog` (`:79`, used only by the
  router path); `LauncherScreen` `PendingActionArea` (`:734-764`) and its call (`:235-253`); five
  strings ×3 locales (`launcher_confirm_execute_title`, `launcher_confirm_will_run`,
  `launcher_confirm_confirm`, `launcher_safe_proposal_description`, `launcher_safe_proposal_run`) — in
  `strings.xml`, not Class B.

**(P2) `CommandOutcome`'s closed-sum sites** — B-2 above.

**(P3) Ten files on `FakeCommandPlanner`** — exactly the spec's list, by grep:
`RouteCommandUseCaseTest`, `AgentEgressSentinelGuardTest` (its non-vacuity control is built on
`PlanResult.RoutedAction`, `:224`), `FastPathOverflowSeamTest` (`DOC-NYH-4`'s anchor, "the model is
asked exactly once", `:119`), `AgentActingSeamTest` (R14-39 pin, `:551-552`, `:569-570`),
`Tier0ToolExecutionEndToEndTest`, `FreeTextGoalEndToEndTest`, `LauncherViewModelTest`,
`LauncherScreenPrayerStripTest`, `LauncherScreenAgentProvenanceTest`, and `AgentVocabularyGuardTest`
(KDoc only). Every one constructs `RouteCommandUseCase` with **named** arguments
`planner = …, catalog = …`, which is what makes the migration a two-name substitution (Task 11).

**(P4) Tests the deletion removes, counted from the files:** `ProposalValidatorTest` **14**,
`RouterOutboundGuardTest` **8** (`:domain`), `LlmCommandPlannerTest` **11** (`:data:ai-cloud`),
`DefaultActionCatalogRouterSchemaGuardTest` **1** (`:data:repository`), two `RoutedAction` cases in
`RouteCommandUseCaseTest` (`:270`, `:284`), five router cases in `LauncherViewModelTest` (`:384`,
`:402`, `:421`, `:442`, `:459`) — **41**. `ToolSchemaWeightTest` (1) is re-anchored, not deleted
(Task 3).

**(P5) `OutboundContextPolicy.ACTION_CATALOG_SCHEMA`** is defined at `OutboundContextPolicy.kt:28`,
listed at `:35`, named in production only by `CatalogSchemaRenderer.kt:8` (KDoc), and pinned by the six
tests of B-4.

**(P6) The privacy guards' term lists** (`AliasPrivacyScopeGuardTest:33-41`,
`ResolutionPrivacyScopeGuardTest:37-45`, `PrayerLocationPrivacyGuardTest:291-299`) contain
`"CommandPlanner"`, `"CatalogSchemaRenderer"` and `"com.sidr.launcher.domain.ai"`. The last is the one
with teeth and covers `…domain.ai.bridge`; the two names go vacuous at the deletion and are replaced by
the bridge's names.

**(P7) `AgentVocabularyGuardTest:90-99`** forbids `CommandPlanner` in `domain/agent|tool|trace`. None of
the three names `com.sidr.launcher.domain.ai` today (grep: empty), so Task 3 can forbid the package
outright.

**(P8) `LauncherIntent.LaunchAppIntent(displayNameQuery, packageName: String? = null)`** —
`LauncherIntent.kt:5-8`. Nothing sets `packageName` (grep: no producer); `IntentActionResolver
.resolveAppLaunch` (`:37-64`) ignores it. `LauncherAction.LaunchApp(val query: String)` —
`LauncherAction.kt:23`.

**(P9) `AppTargetResolver.resolve(query): String?`** (`AppTargetResolver.kt:33-47`) folds "two
candidates", "none", "blank" and "repository failure" into `null`; its only production caller is
`ToolMatchPlanner.kt:129`.

**(P10) `ToolArgumentSorts`** (`ToolArgumentSorts.kt:65-75`) has three rows (`uninstall_app`,
`open_app_info`, `set_app_alias`). `ToolArgumentSortGuardTest` checks both directions over the authored
sources: every tool declaring an argument named `app` has a row, and no row names an undeclared
argument — it does **not** check that a label argument is optional.

**(P11) `SystemIntentToolSource`** projects two families (`:33-38`) with `effect = EXTERNAL` for all
(`:56`); `SystemIntentToolWorker` reads only `query` (`:40`) and maps `launch_app` /
`play_store_search` in `actionFor` (`:47-53`). `DefaultActionCatalog` declares `web_search`
(`query`, `SAFE`) and `open_url` (`url`, `CONFIRM`) (`DefaultActionCatalog.kt:37-44`, `:67-74`).
`SystemIntentToolContractTest:103-112` requires `FakeToolRegistry.withA0Tools()` to equal the whole
projection field for field — so the fake grows with it (Tasks 1, 2).

**(P12) "Add a tool" lists for an `in_app` tool** (by grep for `PLAY_STORE_SEARCH`, the floor, not the
ceiling — R14-41): `ToolPermissionCatalog.ROWS` (`:65-129`, a missing row withholds the tool),
`ToolInverseCatalog.ROWS` (`:75-99`, `ToolInverseCatalogGuardTest` requires a row per authored tool),
`DoctrineGuardTest.declaredRisk` (`:789-833`) and `REQUIRED_TOOL_IDS` (`:930-953`),
`ToolRegistryPermissionGuardTest.REQUIRED_TOOL_IDS` (`:298-323`), `SystemIntentToolSourceTest`
(equality lists), `SystemIntentToolContractTest` (cases), `FakeToolRegistry.withA0Tools()`,
`AgentSessionPresentation.toolLabelFor` + the literal `DoctrineGuardTest` scans for. **Run the gate
early in Task 1** — a list nobody knew about turns red there.

**(P13) `ResumeAgentSessionUseCase.resume`** returns `OperationResult<AgentSession>`
(`ResumeAgentSessionUseCase.kt:28-41`); its callers are `LauncherAgentSession.continueSession`
(`LauncherAgentSession.kt:118-129`) and `ResumeAgentSessionUseCaseTest` (six tests, all through a
`.value()` helper at `:57`). `:consumer:jvm` does not call it.

**(P14) The alias decorator fills only `Unknown`** — `ResolveCommandWithAliasUseCase.kt:27`; with
`RouteCommandUseCase.kt:153-169` that makes F3a-C.

**(P15) `CommandMessage.Verbatim`** (`CommandMessage.kt:29`) is produced only by the router's
`Clarify` (`RouteCommandUseCase.kt:178`) and rendered as bare `%1$s` (`launcher_message_verbatim`, all
three locales). Spec §7.12.2 wants the clarify text marked «сгенерировано» — Task 10 changes the value
of that key, not its name.

**(P16) The assistant prefill exists** (block X6-C): `Routes.Assistant.routeFor(encodedPrompt)`
(`Routes.kt:28`) — "must already be URL-encoded by the caller" — and `AppNavHost.kt:307-323` hands the
argument to `AssistantScreen(initialPrompt = …)`, which prefills and does not send. **`:feature:assistant`
needs no edit** (§0.5 S2). The two current callers encode with Android's `Uri.encode`
(`LauncherScreen.kt:220`, `AppDrawerScreen.kt:137`), which plain-JVM tests cannot call
(`LauncherViewModelTest` has no Robolectric runner).

**(P17) `LauncherAppLaunch.recordChoiceIfPending(packageName)`** (`LauncherAppLaunch.kt:118-133`) is
the one learned-choice record call site; `ResolutionPrivacyScopeGuardTest` requires exactly one
`recordResolutionChoice.record(` in `feature/launcher/src/main`, gated by
`if (!token.isAppAmbiguityFlow) return` in the same file.

**(P18) DI providers are callable objects.** `AgentProvidesModule.provideToolFederation(…)` is called
directly by `DoctrineGuardTest.graphFederation()` (`:244-276`) — the precedent the payload guard
follows for `BridgeProvidesModule`. `MemoryProvidesModule` already provides `ResolutionPreferencePolicy`
(`:41-42`) and `ResolutionPreferenceStore` is bound; `DeviceMentionBinder` can be `@Inject`.

**(P19) The card needs no new `core/ui` component.** `SidrActionProposal` (`SidrActionSafety.kt:63`)
takes a title, a description, a tone, a provenance slot, execute/cancel and an execute label, and is
one-shot (`DOC-HYA-2`'s two anchors). `AgentSessionSurface`'s private `AgentStepProvenance`
(`AgentSessionSurface.kt:305-311`) is the provenance line the card reuses (made `internal`, Task 10).

**(P20) `:app` tests already declare the whole repository's `**/src/main/**/*.kt` as an input**
(`app/build.gradle.kts:489-496`), so a new `:app` scan over `:data:ai-cloud` or `:domain` sources needs
no input edit. `:app`'s test classpath has `ktor-client-core`/`-android` (implementation) but **not**
`ktor-client-mock` or `kotlinx-serialization-json`; the payload guard adds both as
`testImplementation` (Task 11).

**(P21) Doctrine cells that name the router.** `DOC-AML-1` names `RouterOutboundGuardTest`, which the
deletion removes — `DoctrineMatrixGuardTest > every_named_test_exists_in_the_repository` goes red unless
the cell changes **in the same task**. `DOC-ADL-3`'s cell and the paragraph at
`sidr-doctrine-matrix-v1.0.md:149-151` name `CommandPlanner`. `DOC-ADL-1` and `DOC-NYH-1` name
`RouteCommandUseCaseTest`, which survives but loses the cases they leaned on (P4).

### 0.5 · Where the spec and the tree diverge, and what each changes

| # | Spec says | Tree says | Changes |
|---|---|---|---|
| S1 | §7.6: the filter is `level != APP_SHORTCUT` | open `ToolLevel`; D1 | owner fork F3a-A |
| S2 | §7.12.5: `:feature:assistant` (prefill) is touched | the prefill exists (P16) | `:feature:assistant` is **not** in the diff; the encoding lands in `:core:common` (`Routes.Assistant.routeForPrompt`), which §7.12.5 does not list |
| S3 | §7.12.3: `PromptContextBuilder` names the value | it names `ALLOWED`, not the value (P5) | no edit; its KDoc stays true |
| S4 | §7.12.3: "шесть закреплений" includes `RouterOutboundGuardTest` | that test dies with the router | its pin and its `ToolId` sentinel move into `BridgePayloadGuardTest` (Task 11); six pins after, as before |
| S5 | §7.12.6 crit. 21: «мутация на каждый источник» | the bridge graph holds **no** port to prayer, calendar or usage data | for those three the guard is structural: a reflection test that no `BridgeProvidesModule` provider takes such a port (Task 11, M11j); the four sources the graph does hold get one mutation each |
| S6 | §7.12.6 crit. 18: «мутация „исход привязки в тексте повтора“» | binding runs after the model returns, and the retry happens inside the planner before binding — that leak is unplantable in the retry as written | two plantable forms of the same leak: the retry **echoing the rejected reply** (M4e, M11f) and step 7 **re-asking the model with what binding found** (M11c–M11e) |
| S7 | §7.12.2: "транспорт по умолчанию выбирает замер внутри фазы 3a" | the measurement needs the owner's key and models | Task 6 builds it, the owner runs it, Task 12 applies it; until then the default is the prompt form (spec) and the budget numbers are provisional (`BridgeBudget` KDoc, ADR) |
| S8 | §9.3/§7.12.5: `:core:ui` "if the card needs new components" | it does not (P19) | no Roborazzi run |
| S9 | §7.12.5: `:consumer:jvm` "if `commonMain` edits touch it" | `MODEL_PROPOSED` decodes generically there; it calls neither `resume` nor `CommandOutcome` | `:consumer:jvm` is not in the diff (72 stays) |

### 0.6 · What this plan does NOT do — said, not implied

| Not here | Why / where |
|---|---|
| Multi-step model plans, bindings between steps | 3b (§7.12.4); B-1 |
| The full sort system, word forms («телегу», `telegramı`) | 3b with the `F6` ADR; Review Focus 5 names the limit |
| Re-planning after a failure (`priorObservations`) and its outbound question | 3b |
| `:consumer:jvm` parity (bridge port in `commonMain`, JVM transport) | 3b |
| Phase 2's deferred tests N2, T3 m3, T4 M2, T6 m5 | 3b, with the first multi-step plan |
| The dynamic tier (`app_shortcut`) in the model's view | D1 — never in A4′ |
| `launcher_memory` in the model's view | §7.5; the trigger to reopen (free-speech «запомни…») is A3's |
| A tool for content generation («составить текст»), a 0-step spoken reply | owner decision (memo §5, §15); `StartAgentSessionUseCase.isRunnable` still refuses 0 steps |
| Merging the interpretation card with the engine's consent card | out of A4′ (§7.12.4); the "fifth place" regression is accepted and named (§0.7) |
| Retrying a transport failure (5xx, timeout, network) | the spec's one retry is for the model's form; a transport failure is reported as it is |
| Fixing aliases against honest messages | F3a-C |
| Any device round | the block has one acceptance, at its end |
| Anchoring `DOC-ILM-5` («сгенерированное — помечено как сгенерированное») | 3a is the first block that shows model-generated text — the card's «Предложено моделью» and «Модель спрашивает» — and `InterpretationSurfaceTest > a model-generated address on an external step is shown verbatim, marked as the model's` / `> a value the user said carries no model mark` pin that marking. The cell stays `<нет>`: the rule is three classes, and its canonical/curated half — what *fetched* content must prove, the debt the cell addresses — is not touched here; naming a third of a rule as its test would overclaim. Listed in `§HANDOFF` as a candidate partial anchor in the `DOC-HMA-3` remainder-marker form, for the owner (plan review, finding 10) |
| Updating `docs/agentic-os-architecture.md`, the DS master plan and the agentic master plan where they name the router | named at close (Task 13) as follow-ups; `CLAUDE.md`, `current-status.md` and the matrix are synced |

### 0.7 · Owner dependencies and hypotheses

**Owner dependencies.**
1. **The three forks of §0.1 — answered 2026-10-04, `A: allow · B: a · C: a`** — needed by Task 3 (F3a-A) and Task 11 (F3a-B); F3a-C changes no task
   under (a). Tasks 1–2 do not depend on any.
2. **The transport measurement (Task 12):** a labelled corpus in
   `app/src/test/resources/bridge-measure/corpus.tsv` (format in Task 6's README), two to four models,
   the key in `SIDR_MEASURE_API_KEY`; the owner runs `tools/measure-bridge-transport.sh` and judges the
   per-line report for correctness.
3. **The Turkish review at block acceptance gains eighteen strings** (seventeen new keys — two in Task 1,
   one in Task 2, fourteen in Task 10 — and the changed `launcher_message_verbatim`).
4. **The doctrine-matrix edits of Task 11** (`DOC-AML-1`, `DOC-ADL-3` are forced there by
   `DoctrineMatrixGuardTest`; `DOC-ADL-1`, `DOC-NYH-1`, `DOC-NYH-2`, `DOC-HYA-2`, `DOC-MZN-2`,
   `DOC-ILM-3` are re-anchored consciously, spec §7.12.3) and the two ADRs are listed for the owner at
   close; the close commit is proposed (R7).

**Hypotheses for the block's acceptance checklist — nobody has seen these on a phone.**
- A command FastPath does not decide («найди мне лекцию по rust в интернете») is expected to show
  «Разбираю команду…» with «Отмена» for the model's few seconds, then a card «Понял так» naming one
  step («Найти в интернете: …») with «Предложено моделью: …» under a search text the user did not type,
  and «Выполнить» / «Отмена». Nothing is expected to happen before «Выполнить».
- After «Выполнить» the agent card is expected to take over (a `SAFE` step runs; «План выполнен» with
  its step line). **This is new for model actions:** the router opened the app or the page with no agent
  card; now every model action leaves the engine's card behind.
- **The named regression (spec §7.12.3, "the fifth place"):** a `CONFIRM` proposal — `open_url`,
  `play_store_search`, `uninstall_app` — is expected to cost the interpretation card **and** the
  engine's consent card, and a wait of up to the ceiling instead of 2 s.
- A command FastPath does not decide that names an app two installed apps share — e.g. «мне нужен
  telegram» with two apps labelled «Telegram» — is expected to show «Какое приложение — «telegram»?»
  with both, label and package; a tap is expected to show the card with «Открыть … (package)», and to
  launch nothing until «Выполнить». (Not «открой telegram»: FastPath decides that one itself and shows
  its own ambiguity chips. Not «открой телегу»: a word form is not bound in 3a — it reads «не найдено»,
  Review Focus 5 — the plan review caught the first draft of this line contradicting D1.)
- «почему небо синее» is expected to open the assistant with the question already typed, not sent.
- A broken key is expected to read «Ключ ИИ-провайдера не найден или не принят.» with «Настроить провайдера»
  (wording corrected in execution, T10 review I1 — `CREDENTIALS` also covers a key that is missing or unreadable); a model
  that answers prose twice, «Не удалось прочитать ответ модели.».
- A paused model plan («Продолжить» after the process died at the uninstall gate) is expected to close
  and come back as a fresh card for the same words — or as «Не распознано локально…» if local-only was
  switched on meanwhile.

### 0.8 · The gate chain and the floor — predicted before any run

Per module, after each task's boundary gate. Every number is a hypothesis; each task restates its own
delta.

| Module | Base | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | T9 | T10 | T11 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `:domain` | 480 | 480 | 483 | 495 | 520 | 520 | 520 | 524 | 526 | 527 | 527 | **509** |
| `:consumer:jvm` | 72 | 72 | 72 | 72 | 72 | 72 | 72 | 72 | 72 | 72 | 72 | 72 |
| `:data:repository` | 410 | 413 | 417 | 417 | 417 | 421 | 421 | 431 | 431 | 431 | 431 | **430** |
| `:feature:launcher` | 216 | 216 | 217 | 217 | 217 | 217 | 217 | 217 | 220 | 230 | 238 | **234** |
| `:app` | 66 | 66 | 66 | 66 | 66 | 68 | 68 | 68 | 68 | 68 | 68 | **76** |
| the other eight | 316 | 316 | 316 | 316 | 316 | 316 | 334 | 334 | 334 | 334 | 334 | **323** |
| **total** | **1560** | 1563 | 1571 | 1583 | 1608 | 1614 | 1632 | 1646 | 1651 | 1662 | 1670 | **1644** |

> **Measured in execution (2026-10-04), T1–T5:** 1563 · 1571 · 1583 · **1610** (T4 1608 + 2: fix round of the
> task review — `a rejected id gets the same retry text whether or not it exists on the device` and
> `a capital dotted I the user typed matches its plain lowercase form, and never splits the word`, both
> `:domain`) · **1617** (T5 1614 + 2 carried + 1: fix round — `every required argument a tool declares is
> either asked of the model or bound by the device`, `:data:repository`). Every task's own predicted delta
> matched to the test; the chain from T6 on is the plan's +3 (`:domain` +2, `:data:repository` +1), so
> T11 is expected at **1647**, not 1644. **T6: 1636** (plan 1632 + 3 carried + 1: the timeout test) → T11 **1648**.
> **T7: 1651** (plan 1646 + 4 carried + 1: a controller ruling — the behavioural `MENTION_ARG` test in
> `DeviceMentionBinderTest`, `:data:repository` 433) → T8 **1656**, T9 **1667**, T10 **1675**, T11 **1649**.
> **T8: 1656** as predicted (`:domain` 528, `:feature:launcher` 220; the fix round changed a fixture, not the count).
> **T9: 1672** = plan 1662 + 5 carried + 3 (controller rulings on the implementer's findings F8, F9, F10d — `a dismissed pick
> card leaves nothing to learn from a later tap`, `an assistant command without a question opens the bare assistant route`,
> `a newer command supersedes a slow answer still on its way`) + 2 (task-review fix round — `editing the command during the
> wait abandons it`, `dictating a new command dismisses the standing interpretation`), all `:feature:launcher` (235);
> `:domain` 529. → T10 **1680**, T11 **1654**.
> **T10: 1691** = 1680 + 11 (controller rulings on the implementer's test holes, all `:feature:launcher`, 254; detail under
> Task 10 Step 6) → T11 **1665**.
> **T11: 1669** = 1665 + 4 (controller rulings: T9's m2 +2, H2 +1 `:domain`, H8 +1) → review fix **1670** (+1, the ASK
> chip); `:domain` 512 · `:data:repository` 432 · `:feature:launcher` 254 · `:app` 76 · `:data:ai-cloud` 40. T12 and T13
> add no test.
> **T12: 1670. T13: 1673** — T13's own steps add none, but the final whole-branch review's fix round (`f6aa141`) added
> three (A1 and M1 `:feature:launcher` 256, A3 `:data:repository` 433). **The floor is 1673**, not 1644.

- Task 11 is the only task whose total falls: −41 removed (P4) and +15 added (`:domain` +6, `:app` +8,
  `:feature:launcher` +1; the migrated tests keep their counts) → **1670 − 26 = 1644**. With F3a-B (b)
  it is 1643 (no freeze guard). (Before the plan review added `:domain`'s sixth test these read 1643 /
  1642 — §0.11.)
- Tasks 12 and 13 add no test. **The floor becomes the measured total at close** (B-8).
- The other eight move only in `:data:ai-cloud`: 32 → 50 (Task 6) → 39 (Task 11).

### 0.9 · Guards and anchors the deletion breaks or empties — checked by grep, not by the spec

| Guard / anchor | What happens at the deletion | Where it is fixed |
|---|---|---|
| ten `FakeCommandPlanner` files (P3) | compile-red | Task 11: `FakeModelPlanner` keeps the property names `planCallCount` / `lastCommand`, so every "the model is (not) asked" assertion moves as is; each non-vacuity control is re-made on a `ModelPlanning` answer |
| `AgentEgressSentinelGuardTest` control on `PlanResult.RoutedAction` | compile-red | Task 11: the control answers `ModelPlanning.Navigate(APP_GRID)` |
| `FastPathOverflowSeamTest` (`DOC-NYH-4` anchor) | compile-red | Task 11: same assertions, model fake |
| `AgentActingSeamTest` R14-39 pin | compile-red | Task 11: same, model fake — R14-39 stays named, not closed (spec §7.12.3) |
| `AgentVocabularyGuardTest` forbidden `CommandPlanner` | vacuous | Task 3 adds `ModelPlanner`, `BridgeTransport`, `com.sidr.launcher.domain.ai`; Task 11 drops `CommandPlanner` |
| three privacy guards' `CommandPlanner`, `CatalogSchemaRenderer` (P6) | vacuous | Task 3 adds `ModelPlanner`, `BridgeRendering`, `BridgeTransport`; Task 11 drops the two dead names; M11g proves the teeth |
| the six `ALLOWED` pins (B-4) | red on the replacement | Task 11, with ADR 1 |
| `RouterOutboundGuardTest` (8; incl. the `ToolId` sentinel) | deleted | Task 11: `BridgePayloadGuardTest`; the static-text scans move to `BridgeRenderingTest` (Task 3) |
| `DefaultActionCatalogRouterSchemaGuardTest` | deleted | replaced by the payload guard (2)+(3), **not** carried over: its denylist scan over descriptions would redden on `open_location_settings` / `open_data_usage_settings` (spec §7.12.3 (3)) |
| `ToolSchemaWeightTest` (pins `ROUTER_INSTRUCTION`, 562) | compile-red | Task 3 re-anchors it on the bridge's instruction (1 140) |
| `DOC-AML-1` cell names `RouterOutboundGuardTest` (P21) | `DoctrineMatrixGuardTest` red | Task 11, same commit |
| `DOC-ADL-3` cell + matrix §3 paragraph name `CommandPlanner` | stale text | Task 11 |
| `DOC-ADL-1` (`RouteCommandUseCaseTest` loses the `needsConfirmation` cases) | vacuous in meaning | Task 7 adds the anchor (a `CONFIRM` step the model proposed stops at the engine exactly like a deterministic one); Task 11 edits the cell |
| `DOC-NYH-1` (same) | vacuous in meaning | criteria 15 and 17 tests (Tasks 9, 11); Task 11 edits the cell |
| `DOC-HYA-2` (`SidrActionSafetyTest` — the router's SAFE one-tap used `SidrActionProposal`) | still true: the card is built on `SidrActionProposal` | Task 10 adds the card tests; Task 11 edits the cell |
| `DOC-NYH-2`, `DOC-MZN-2` | still true | Task 10's card tests added to the cells (Task 11) |
| KDoc that becomes false: `Planner` ("A4' binds a model planner behind the same seam", `ExecutionPlan.kt:120`), `CompositePlanner` (`:5-6`), `StartAgentSessionUseCase` (`:13-18`), `IntentMatcher.kt:20`, `LauncherAction.kt:5`, `InvocationValidator.kt:35`, `ExecuteActionUseCase.kt:8-21`, `RouteCommandUseCase` (step 7), `IntentProvidesModule.kt:33-36`, `CommandMessage.Verbatim`, `HardcodedUiTextGuardTest.kt:82`, `InvocationValidatorTest.kt:11`, `SuggestionOutboundIsolationTest.kt:15-17` | false text | Task 11 (Task 10 for `Verbatim`) |
| `DoctrineGuardTest > the composition root's planner list matches the declared planners` | **unchanged, and now load-bearing for D2**: the model planner is not in `CompositePlanner`'s list | nothing to fix; cited in ADR 2 |

### 0.10 · How the predictions were produced — so a reviewer can reproduce them

- **The probe of §0.3:** `git archive fe495cd | tar -x -C <scratch>/wt`, `local.properties` copied, the
  filter appended to `data/ai-cloud/build.gradle.kts`, a one-test class added, three Gradle runs with
  the gate's toolchain argument and `--console=plain`; counts read from the XML.
- **The instruction length (1 140) and the synthetic rendering sizes in Task 3** were computed by a
  Python transcription of the exact strings and loops this plan specifies (n = 0 → 1 140; 2 → 1 351;
  15 → 1 668; 120 → 4 313; marginal ≈ 25.1 per descriptor). The Kotlin must reproduce them; a
  mismatch is a finding about the transcription or the code, not a number to widen.
- **Everything else is a hypothesis** under the owner's rule.

### 0.11 · What the plan review changed

**One review, 2026-10-04**, by a fresh `reviewer` agent against the tree at `fe495cd` and spec §7.12 — read
only, no probe (no `kotlinc`, no Gradle). **Verdict: FIX REQUIRED** — the scope matches §7.12.4 «3a» and
the architecture holds; one task's gate would have gone red as written, one spec criterion had no test,
and seven mutation predictions were wrong or would not compile. Every finding was checked against the
tree before it was applied; none was rejected.

| # | Finding | What changed |
|---|---|---|
| B1 | Task 1's gate (predicted green, 1563) goes red on two tests the plan did not name: `SystemIntentToolWorkerTest:97` uses `ToolId("web_search")` as its **unregistered** id; `ToolInverseCatalogGuardTest:56` pins `20` authored tools | Task 1: the id becomes `open_settings` (stays unprojected by decision), the count `20 → 22`, `RegistryCensusMeasurement`'s KDoc «20 rows» → 22. The count stays 1563 — tests change, none is added |
| M1 | Criterion 15's second half («детерминированные пути карточки не получили — отдельный тест») had no test | Task 11: `RouteCommandUseCaseTest > the deterministic paths never get an interpretation card` (FastPath decided, A0, step 2b — with a planner that WOULD answer) and mutation **M11l**; named in the Self-review and in `DOC-NYH-1`'s cell |
| M2 | Four predictions wrong: M2a's test stays green (its name matched nothing with the pin or without — vacuous); M4b, M5b, M1d each redden more than they said | M2a: the test's fixture now installs `Tessar` under another package, so an ignored pin launches it. M4b +3 `BridgeModelPlannerTest`, M5b +1 `AuthoredToolDescriptionsTest`, M1d + `RegistryCensusMeasurement` |
| M3 | M11a's plant calls `plan()`, a `domain.ai.bridge` extension `RouteCommandUseCase` does not import — `gradle=1`, no `FAILED ::` | The plant builds `ExecutionPlan(listOf(step))` fully qualified |
| M4 | The shared cloud client's 30 s socket would end a silent non-streaming answer before the 45 s ceiling (→ `TIMED_OUT`, not retried), while the harness measured on its own 60 s client — production and measurement disagreed | Task 6: `bridgeHttpClient(socketTimeoutMs)` — the bridge's own client; production passes the ceiling (Task 11's provider loses its `HttpClient` parameter), the harness passes the cap of Task 12 rule 2 (90 s) so it can see what the rule could accept; `BridgeBudget`'s KDoc and rule 2 say it |
| m1 | M9a/M9e/M9f copied the list's two-space shift — `PLANT REFUSED` | Indents 16/20/24; a note in Task 9 Step 5 |
| m2 | Cross-module red sets left out (M11b, M11e, M11k; M11c/M11d's controls "may" → will) | Rows completed. Checked rather than copied: M11b reddens **four** `BridgeOfferingTest` tests, not five — the first and the fourth fixtures sit at offered levels, described |
| m3 | M11d proves alias and learned sources in one plant | Kept as one plant; the proof is the failure **message**, read from the JUnit XML, which must name all three sources (each sentinel is listed under its own source) |
| m4 | The no-port structural scan forbids `domain.*` packages only; the calendar reader lives in `data.repository.suggestions` | `+ data.repository.suggestions, data.prayer, android.content, android.app.usage` |
| m5 | "Never throws" held only around the call: config, key, `encode` sat outside the `try` | The whole exchange runs inside one net; a non-I/O failure is `AiError.Unknown` (not "no network"); the existing network test gains a second half (a schema that does not parse) — no new test. The planner still does not catch: the contract is the transport's, and `FakeBridgeTransport`'s throw on an unscripted ask is what M4g's prediction stands on |
| m6 | `REQUEST_REJECTED` read «check the model name», but a non-`https` base URL and a 404 endpoint fold into it | «…Check the provider settings.» in all three locales, with the reason |
| m7 | `SystemIntentToolWorker` addresses wrong (`:115-150`, `:120`, `:127-133` came from a `cat -n` of two files) | `:35-70`, `:40`, `:47-53` |
| m8 | §0.7's «открой телегу» hypothesis contradicts D1 (a word form reads «не найдено») | Rewritten on a command FastPath does not decide, «мне нужен telegram» |
| m9 | `README.md`, `docs/roadmap.md` not named at close; CLAUDE.md's «twenty authored tools» | Task 13 Steps 5 and 7 |
| m10 | `DOC-ILM-5` — 3a is the first block that shows model-generated text | Decided, not anchored: §0.6's row says why the cell stays `<нет>`; `§HANDOFF` lists the partial anchor for the owner |
| m11 | Task 9's two screen tests build `StartAgentSessionUseCase` inline — there is no `val` to reuse | Task 9 says: lift it into one local `val`, pass it to both |

**What the review confirmed** (recorded so it is not re-derived): every «3a» item has a task and nothing
of «3b» or «вне A4′» crept in; F3a-A is described accurately against §7.6; **P4 = 41** by `grep -c @Test`
(`ProposalValidatorTest` 14, `RouterOutboundGuardTest` 8, `LlmCommandPlannerTest` 11,
`DefaultActionCatalogRouterSchemaGuardTest` 1, `RouteCommandUseCaseTest` 2, `LauncherViewModelTest` 5);
§0.8's chain is arithmetically consistent with the `@Test` counts in the plan's code; the `INSTRUCTION`
transcription (1140 chars; renders 1351/1668/4313; marginal 25.10) and its denylist cleanliness; the
walk-throughs of the rendering, validator, derivability, planner (incl. virtual `currentTime == 45_000`),
transport, binder, resume, VM and payload-guard tests; `CommandOutcome` has exactly two exhaustive
`when`s; no router reference outside the plan's lists but README/roadmap; every exact anchor string
the plan plants against exists.

**Net effect on the numbers:** one test added (`:domain`, Task 11) → **T11 = 1644** (`:domain` 509);
**1643 under F3a-B (b)**. Tasks 1–10 unchanged. Mutations: Task 11 eleven → **twelve**.

**No second review.** The delta is fifteen local corrections and one added test; no task, port or closed
type was added or removed — the only signature that moved is `BridgeProvidesModule.provideBridgeTransport`
(it no longer takes the shared `HttpClient`), beside one new top-level function, `bridgeHttpClient`.
The brief's bar for a second review — a large delta — is not met.

---

## Task 1: `web_search` and `open_url` become tools

**Spec:** §7.12.3 (table, «с исполнителем, проецируются»; the KDoc argument «истекает только при
исполнении трёх его частей»). **Modules:** `:data:repository`, `:core:testing`, `:feature:launcher`,
`:app`.

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt`
  — a public `object InAppToolIds` above the class; `projected` (`:33-38`); the class KDoc (`:15-28`).
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolWorker.kt`
  — `invoke`, `actionFor`, `outputFor` (`:35-70`).
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalog.kt`
  (`:66-67`), `.../agent/ToolInverseCatalog.kt` (`:76-77`).
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt` — two
  descriptors, KDoc.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt`
  — two constants beside `TIER0_*` (`:87`), two arms in `toolLabelFor` (`:167-193`).
- Modify: `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` — two keys each, after
  `launcher_agent_step_store`.
- Modify: `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` — `declaredRisk`
  (`:789-833`), `REQUIRED_TOOL_IDS` (`:930-953`); `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt`
  — `REQUIRED_TOOL_IDS` (`:298-323`).
- Modify (a count and a KDoc that the two new tools falsify — found by the plan review, not by the
  P12 grep): `app/src/test/java/com/sidr/launcher/agent/ToolInverseCatalogGuardTest.kt:56`
  (`assertEquals(20, authored.all().size)` → `22`); `app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt:30`
  (KDoc «20 rows» → «22 rows»; its one assertion compares sets and needs no edit).
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSourceTest.kt`
  (rewrite of five tests, no new one), `.../SystemIntentToolWorkerTest.kt` (+3, and one existing test's
  id changed),
  `.../SystemIntentToolContractTest.kt` (cases), `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentationTest.kt`
  (the list in `each new tool maps to its own label`).

**Interfaces.**
- Produces (public, `:data:repository`):
  ```kotlin
  object InAppToolIds {
      val WEB_SEARCH: ToolId   // == ToolId(ActionIds.WEB_SEARCH.value)
      val OPEN_URL: ToolId     // == ToolId(ActionIds.OPEN_URL.value)
  }
  ```
- Behaviour: `SystemIntentToolSource(DefaultActionCatalog()).all()` lists, in order, `launch_app`,
  `play_store_search`, `web_search`, `open_url` — all `IN_APP`, `EXTERNAL`, `TRANSIENT`, risk and
  arguments from the catalog. `open_settings`, `open_assistant`, `show_apps` stay answers, not tools
  (spec §7.12.3).

- [ ] **Step 1: write the failing tests.**

In `SystemIntentToolWorkerTest`, add imports `com.sidr.launcher.domain.intent.CommandFailure` (if
absent) and `com.sidr.launcher.domain.intent.ExecutableAction`, then append before the class's
closing `}`:

```kotlin

    /**
     * A4′ phase 3a, Task 1 (spec §7.12.3): the router's two actions with an executor become tools. The
     * worker reads each tool's OWN argument — `query` for a search, `url` for an address — through the
     * same unchanged `ExecuteActionUseCase` chain the launch has always used.
     */
    @Test
    fun `a web search effects with the query it was given and declares no output`() = runTest {
        val result = executor().invoke(
            ResolvedInvocation(InAppToolIds.WEB_SEARCH, mapOf("query" to "ostravel lectures")),
        )

        assertEquals(ToolResult.Effected(), result)
        val search = actionExecutor.executedActions.single() as ExecutableAction.OpenSearchAction
        assertEquals("ostravel lectures", search.query)
    }

    @Test
    fun `opening a url opens that address, never a search for it`() = runTest {
        val url = "https://quillmere.example/path?q=a&b=c"

        val result = executor().invoke(ResolvedInvocation(InAppToolIds.OPEN_URL, mapOf("url" to url)))

        assertEquals(ToolResult.Effected(), result)
        assertEquals(listOf(ExecutableAction.OpenUrlAction(url)), actionExecutor.executedActions)
    }

    @Test
    fun `a blank url fails closed and never reaches the action path`() = runTest {
        val result = executor().invoke(ResolvedInvocation(InAppToolIds.OPEN_URL, mapOf("url" to "  ")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertTrue(actionExecutor.executedActions.isEmpty())
    }
```

In the same file, the existing test `an unregistered tool id fails closed and never reaches the action
path` (`:95-101`) uses `ToolId("web_search")` as its **unregistered** id (`:97`). From this task on that
id is registered and the worker searches the web for «убер» — the test goes red for the right reason,
and its premise is what changed, not the guard. Replace the id with one that stays unprojected by
decision (spec §7.12.3: an answer of the model, not a tool):

```kotlin
        val result = executor().invoke(ResolvedInvocation(ToolId("open_settings"), mapOf("query" to "убер")))
```

In `SystemIntentToolSourceTest`, replace the five tests named below (bodies shown in full; keep the
imports the file has and add `com.sidr.launcher.domain.tool.ToolId` if absent):

```kotlin
    @Test
    fun `exactly four tools are registered, their ids derived from ActionIds`() {
        // Identity C (owner fork F1, 2026-08-29): the projected id must equal ActionIds directly. Since
        // A4′ phase 3a the two router actions with an executor are projected too (spec §7.12.3).
        assertEquals(
            listOf(
                ActionIds.LAUNCH_APP.value,
                ActionIds.PLAY_STORE_SEARCH.value,
                ActionIds.WEB_SEARCH.value,
                ActionIds.OPEN_URL.value,
            ),
            source.all().map { it.id.value },
        )
    }

    @Test
    fun `risk is carried over from the catalog, not restated`() {
        assertEquals(ActionRiskLevel.SAFE, source.find(ToolIds.LAUNCH_APP)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(ToolIds.PLAY_STORE_SEARCH)!!.risk)
        assertEquals(ActionRiskLevel.SAFE, source.find(InAppToolIds.WEB_SEARCH)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(InAppToolIds.OPEN_URL)!!.risk)
    }

    @Test
    fun `the argument schema is carried over from the catalog`() {
        assertEquals(listOf("query"), source.find(ToolIds.LAUNCH_APP)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(ToolIds.PLAY_STORE_SEARCH)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(InAppToolIds.WEB_SEARCH)!!.argSchema.map { it.name })
        assertEquals(listOf("url"), source.find(InAppToolIds.OPEN_URL)!!.argSchema.map { it.name })
    }

    @Test
    fun `every projected tool is transient - none writes durable state`() {
        assertEquals(List(4) { ToolDurability.TRANSIENT }, source.all().map { it.durability })
    }

    @Test
    fun `the three navigation families are answers, not tools`() {
        // Spec §7.12.3: open_settings, open_assistant and show_apps change what is on screen, not the
        // world. Making them tools would need a "worker in data -> navigation" channel the hard rules
        // forbid, so they stay variants of the model's answer.
        assertNull(source.find(ToolId("open_settings")))
        assertNull(source.find(ToolId("open_assistant")))
        assertNull(source.find(ToolId("show_apps")))
    }
```

and in `every projected tool declares the in-app level and an external effect` replace
`listOf(ToolLevels.IN_APP, ToolLevels.IN_APP)` with `List(4) { ToolLevels.IN_APP }` and
`listOf(ToolEffect.EXTERNAL, ToolEffect.EXTERNAL)` with `List(4) { ToolEffect.EXTERNAL }`.

In `SystemIntentToolContractTest`, give `Case` an argument map and add the two tools. Replace
`private data class Case(` … its closing `)` with the same fields plus
`val args: Map<String, String> = mapOf("query" to "убер"),` as the last field; replace
`val result = executor().invoke(ResolvedInvocation(case.toolId, mapOf("query" to "убер")))` with
`val result = executor().invoke(ResolvedInvocation(case.toolId, case.args))`; and add to `cases`:

```kotlin
            Case(InAppToolIds.WEB_SEARCH, listOf(uber), branch = "Effected"),
            Case(
                InAppToolIds.OPEN_URL,
                listOf(uber),
                branch = "Effected",
                args = mapOf("url" to "https://quillmere.example/path?q=a&b=c"),
            ),
```

In `AgentSessionPresentationTest > each new tool maps to its own label, not the generic one`, extend
the list: `listOf("set_alarm", "uninstall_app", "set_app_alias", "forget_app_alias",
"forget_learned_choice", "web_search", "open_url")`.

- [ ] **Step 2: run — expect compile-red.**
  - `tools/gate.sh --scoped :data:repository:testDebugUnitTest` → `Unresolved reference 'InAppToolIds'`.

- [ ] **Step 3: implement.**

At the top of `SystemIntentToolSource.kt`, after the imports, add:

```kotlin
/**
 * The ids of the two router actions projected as tools in A4′ phase 3a (spec §7.12.3). Identity C:
 * each id is DERIVED from its `ActionId` at this one expression, never copied into a second literal —
 * the seven `ActionIds` values stay frozen byte for byte (ADR 3/4), and these two are those values.
 * Public so `:app`'s guards can name them, as they name `Tier0ToolIds`.
 */
object InAppToolIds {
    val WEB_SEARCH: ToolId = ToolId(ActionIds.WEB_SEARCH.value)
    val OPEN_URL: ToolId = ToolId(ActionIds.OPEN_URL.value)
}
```

Replace

```kotlin
        listOfNotNull(
            project(ActionIds.LAUNCH_APP, outputSchema = RESOLVED_QUERY_OUTPUT),
            project(ActionIds.PLAY_STORE_SEARCH),
        )
```

with

```kotlin
        listOfNotNull(
            project(ActionIds.LAUNCH_APP, outputSchema = RESOLVED_QUERY_OUTPUT),
            project(ActionIds.PLAY_STORE_SEARCH),
            // A4′ phase 3a (spec §7.12.3, D3): the router's two other actions with an executor. The
            // three navigation families (settings, assistant, app grid) are NOT projected — they
            // change what is on screen, not the world, and stay variants of the model's answer.
            project(ActionIds.WEB_SEARCH),
            project(ActionIds.OPEN_URL),
        )
```

and replace the class KDoc's second paragraph («Only two of the seven families are projected.
Registering all seven would hand the planner capabilities this slice has neither gated, traced, nor
accepted on a device.») with:

```kotlin
 * Four of the seven families are projected since A4′ phase 3a. The argument that kept this at two —
 * "registering all seven would hand the planner capabilities this slice has neither gated, traced, nor
 * accepted on a device" — lapses for `web_search` and `open_url` only as its three parts are met (spec
 * §7.12.3): the gate is the engine's, with their risk pinned by `DoctrineGuardTest.declaredRisk`; the
 * trace is the engine's; the acceptance is the block's checklist. It still holds for `open_settings`,
 * `open_assistant` and `show_apps`, which are answers of the model, not tools.
```

In `SystemIntentToolWorker.kt`, add `import com.sidr.launcher.domain.action.LauncherAction` (already
present) and replace `invoke` … `outputFor` (`:35-70`, from `    override suspend fun invoke(` down to
the closing `}` of `outputFor`) with:

```kotlin
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        val action = actionFor(invocation) ?: return ToolResult.Failed(CommandFailure.Generic)
        return map(executeAction.execute(action), outputFor(invocation.id, action))
    }

    /**
     * Each tool reads its OWN argument (`query`, or `url` for `open_url`), trimmed once; a blank or
     * missing one fails closed before the action path. An unregistered tool never reaches it either.
     */
    private fun actionFor(invocation: ResolvedInvocation): LauncherAction? {
        fun arg(name: String): String? = invocation.args[name]?.trim()?.takeIf { it.isNotEmpty() }
        return when (invocation.id) {
            ToolIds.LAUNCH_APP -> arg("query")?.let { LauncherAction.LaunchApp(it) }
            ToolIds.PLAY_STORE_SEARCH -> arg("query")?.let { LauncherAction.PlayStoreSearch(it) }
            InAppToolIds.WEB_SEARCH -> arg("query")?.let { LauncherAction.WebSearch(it) }
            InAppToolIds.OPEN_URL -> arg("url")?.let { LauncherAction.OpenUrl(it) }
            // An unregistered tool never reaches the action path. Tested with a query in hand, so the
            // blank-argument guard above cannot answer for this branch.
            else -> null
        }
    }

    /**
     * `launch_app` reports `resolved_query` on every result it can carry an output on — the query the
     * action was BUILT from, so the output and the launch can never disagree (one read, not two). The
     * other three declare no output ([SystemIntentToolSource]) and stay the default empty [ToolOutput].
     */
    private fun outputFor(id: ToolId, action: LauncherAction): ToolOutput =
        if (id == ToolIds.LAUNCH_APP && action is LauncherAction.LaunchApp) {
            ToolOutput(mapOf(SystemIntentToolSource.RESOLVED_QUERY to action.query))
        } else {
            ToolOutput()
        }
```

In `ToolPermissionCatalog.kt`, after `            ToolIds.PLAY_STORE_SEARCH to emptyList(),` add:

```kotlin
            // A4′ phase 3a: the browser and the search provider need no permission of ours.
            InAppToolIds.WEB_SEARCH to emptyList(),
            InAppToolIds.OPEN_URL to emptyList(),
```

In `ToolInverseCatalog.kt`, after `            ToolIds.PLAY_STORE_SEARCH to Row.NOTHING_TO_UNDO,` add:

```kotlin
            // A4′ phase 3a: opening a page or a search leaves nothing to put back.
            InAppToolIds.WEB_SEARCH to Row.NOTHING_TO_UNDO,
            InAppToolIds.OPEN_URL to Row.NOTHING_TO_UNDO,
```

In `FakeToolRegistry.kt`, change the KDoc to «In-memory registry for domain tests. [withA0Tools]
mirrors **everything** `SystemIntentToolSource` projects — four families since A4′ phase 3a — field for
field (`SystemIntentToolContractTest` holds it); the name is A0's.», and append two descriptors after
`play_store_search`'s:

```kotlin
                ToolDescriptor(
                    id = ToolId("web_search"),
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.EXTERNAL,
                    argSchema = listOf(ActionArg("query", description = "What to search for")),
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = ToolId("open_url"),
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.EXTERNAL,
                    argSchema = listOf(ActionArg("url", description = "The web address to open")),
                    risk = ActionRiskLevel.CONFIRM,
                    durability = ToolDurability.TRANSIENT,
                ),
```

(`:core:testing` cannot see `InAppToolIds`; the literal is checked against production by the parity
test.)

In `AgentSessionPresentation.kt`, after `private const val TIER0_SET_TIMER = "set_timer"` add:

```kotlin
// A4′ phase 3a: the two router actions projected as tools (`InAppToolIds`, `:data:repository`). Literals
// for the same no-feature->data reason as the TIER0_* ones; `DoctrineGuardTest` scans for them.
private const val IN_APP_WEB_SEARCH = "web_search"
private const val IN_APP_OPEN_URL = "open_url"
```

and in `toolLabelFor`, after `    ToolIds.PLAY_STORE_SEARCH.value -> R.string.launcher_agent_step_store`
add:

```kotlin
    IN_APP_WEB_SEARCH -> R.string.launcher_agent_step_web_search
    IN_APP_OPEN_URL -> R.string.launcher_agent_step_open_url
```

Strings, after `launcher_agent_step_store` in each file:

| key | `values` | `values-ru` | `values-tr` |
|---|---|---|---|
| `launcher_agent_step_web_search` | `Search the web: %1$s` | `Найти в интернете: %1$s` | `İnternette ara: %1$s` |
| `launcher_agent_step_open_url` | `Open %1$s in the browser` | `Открыть %1$s в браузере` | `%1$s adresini tarayıcıda aç` |

In `DoctrineGuardTest.declaredRisk`, after `ToolIds.PLAY_STORE_SEARCH to ActionRiskLevel.CONFIRM,` add:

```kotlin
        // A4′ phase 3a (spec §7.12.3): the router's actions with an executor, now tools. The catalog is
        // authoritative; these pins are the gate half of "the KDoc argument lapses only as its three
        // parts are met".
        InAppToolIds.WEB_SEARCH to ActionRiskLevel.SAFE,
        InAppToolIds.OPEN_URL to ActionRiskLevel.CONFIRM,
```

and in its `REQUIRED_TOOL_IDS` and in `ToolRegistryPermissionGuardTest.REQUIRED_TOOL_IDS`, after
`ToolIds.PLAY_STORE_SEARCH,` add `InAppToolIds.WEB_SEARCH,` and `InAppToolIds.OPEN_URL,` (with the
import `com.sidr.launcher.data.repository.agent.InAppToolIds`).

- [ ] **Step 4: run the full gate early** (P12: the "add a tool" lists are a floor, not a ceiling).
  `tools/gate.sh` — expected `GATE GREEN`, `TOTAL tests=1563`: `:data:repository` **413** (+3), every
  other module unchanged. A red test in a list this plan did not name is a finding: add the two ids,
  record the list in the task report.

- [ ] **Step 5: mutations — four.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M1a | `SystemIntentToolWorker.kt` · `InAppToolIds.OPEN_URL -> arg("url")?.let { LauncherAction.OpenUrl(it) }` → `InAppToolIds.OPEN_URL -> arg("url")?.let { LauncherAction.WebSearch(it) }` | an address is opened, not searched | `:data:repository` — `SystemIntentToolWorkerTest > opening a url opens that address, never a search for it` **only** (the contract case stays `Effected`) |
| M1b | `SystemIntentToolWorker.kt` · `fun arg(name: String): String? = invocation.args[name]?.trim()?.takeIf { it.isNotEmpty() }` → `fun arg(name: String): String? = invocation.args[name]?.trim()` | a blank argument fails closed | `:data:repository` — `SystemIntentToolWorkerTest > a blank url fails closed and never reaches the action path` and `> a blank query fails closed and never reaches the action path` |
| M1c | `AgentSessionPresentation.kt` · `    IN_APP_WEB_SEARCH -> R.string.launcher_agent_step_web_search\n` → `` | the tool has its own line | `:feature:launcher` — `AgentSessionPresentationTest > each new tool maps to its own label, not the generic one` **only**. `:app`'s `DoctrineGuardTest` label scan is expected to stay **green** (the constant is still in the file — A1″ residual (8)). **Measured in execution 2026-10-04: `:app` green, `DoctrineGuardTest` 8/0** |
| M1d | `ToolPermissionCatalog.kt` · `            InAppToolIds.OPEN_URL to emptyList(),\n` → `` | the registry-keyed permission guard covers the new tools | `:app` — `ToolRegistryPermissionGuardTest > every registered tool has a permission row` and `RegistryCensusMeasurement > print the authored registry census` (its one assertion compares the catalog's keys with the three authored sources' ids). **Measured in execution 2026-10-04: `:data:repository` stays green (413/0)** — no in-module test enumerates the in-app rows (`ToolPermissionCatalogTest` does not list them; `SystemIntentToolSource` does not consult the catalog), so the property rests on `:app`'s guard alone (finding E1-F1) |

- [ ] **Step 6: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1563`.

- [ ] **Step 7: commit (controller).**

```bash
git add data/repository/src core/testing/src feature/launcher/src app/src/test
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): web_search и open_url — инструменты движка

D3 (спека §7.12.3): два действия роутера с исполнителем проецируются
инструментами (identity C — ToolId выводится из ActionId); воркер читает
собственный аргумент каждого (url у open_url). Навигационные три остаются
вариантами ответа. Риск закреплён DoctrineGuardTest.declaredRisk, строки
каталогов разрешений и откатов добавлены, строки шага — en/ru/tr.

Мутации M1a–M1d.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `launch_app` can be pinned to a package; the resolver returns candidates

**Spec:** §7.12.3 condition 2 («3a расширяет API резолвера (кандидаты вместо `String?`)… `launch_app`
сохраняет выученные выборы… как совместить с резолвом `query` в `IntentActionResolver` — решает план»);
plan §0.2 B-3. **Modules:** `:domain`, `:data:repository`, `:core:testing`, `:feature:launcher`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/action/LauncherAction.kt:23-25`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/ExecuteActionUseCase.kt:33-34`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/IntentActionResolver.kt:37-64`.
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt`
  (`project`, the companion), `.../agent/SystemIntentToolWorker.kt` (`actionFor`),
  `.../agent/ToolArgumentSorts.kt` (`ROWS`), `.../agent/AppTargetResolver.kt` (whole class body).
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt` (`launch_app`'s
  `argSchema`).
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt`
  (`PlanStep.line`, `:294-322`; one constant beside `APP_ARG`); strings ×3.
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/IntentActionResolverTest.kt` (+2),
  `.../intent/ExecuteActionUseCaseTest.kt` (+1); `data/repository/src/test/.../agent/SystemIntentToolWorkerTest.kt`
  (+1), `.../agent/SystemIntentToolSourceTest.kt` (`the argument schema…`), `.../agent/AppTargetResolverTest.kt`
  (+3); `feature/launcher/src/test/.../agent/AgentSessionSurfaceProvenanceTest.kt` (+1).

**Interfaces.**
- Produces:
  ```kotlin
  // :domain
  data class LauncherAction.LaunchApp(val query: String, val packageName: String? = null)
  // :data:repository
  sealed interface AppCandidates {
      data object None : AppCandidates
      data class One(val app: InstalledApp) : AppCandidates
      data class Several(val apps: List<InstalledApp>) : AppCandidates
      data object Unavailable : AppCandidates
  }
  suspend fun AppTargetResolver.candidates(query: String): AppCandidates
  suspend fun AppTargetResolver.resolve(query: String): String?   // unchanged signature, now derived
  SystemIntentToolSource.PIN_ARG == "app"   // internal companion constant
  ```
- Behaviour: a `LaunchAppIntent` with `packageName` opens exactly that installed package, or is
  `NoAppFound(displayNameQuery)` if it is gone; without it, resolution is byte-for-byte today's.
  `launch_app` declares an optional `app` argument; a step that carries it launches that package.

- [ ] **Step 1: write the failing tests.**

`IntentActionResolverTest`, append after `` `no app found carries the query not a sentence` `` … (any
place in the LaunchAppIntent section):

```kotlin

    // --- LaunchAppIntent: a pinned package (A4′ phase 3a, plan §0.2 B-3) ---

    @Test fun `a pinned package launches that package even when two apps share the label`() = runTest {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("net.fixture.tessar.one", "Tessar"),
            InstalledApp("net.fixture.tessar.two", "Tessar", activityName = ".Main"),
        )
        val action = resolveSuccess(
            LauncherIntent.LaunchAppIntent("tessar", packageName = "net.fixture.tessar.two"),
        )
        assertEquals(ExecutableAction.LaunchAppAction("net.fixture.tessar.two", ".Main"), action)
    }

    /**
     * The typed name still matches an installed app — a DIFFERENT package than the pinned one. So an
     * ignored pin launches `tessar.one` and this test sees it; a fixture where the name matched nothing
     * would read `NoAppFound` with the pin or without it and prove nothing (plan review, M2a).
     */
    @Test fun `a pinned package that is no longer installed is not found under the typed name`() = runTest {
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.tessar.one", "Tessar"))
        val action = resolveSuccess(
            LauncherIntent.LaunchAppIntent("tessar", packageName = "net.fixture.tessar.two"),
        )
        assertEquals(ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound("tessar")), action)
    }
```

`ExecuteActionUseCaseTest`, append:

```kotlin

    @Test
    fun `a pinned launch reaches the resolver with its package`() = runTest {
        repo.appsToReturn = listOf(
            InstalledApp("net.fixture.tessar.one", "Tessar"),
            InstalledApp("net.fixture.tessar.two", "Tessar"),
        )

        val outcome = useCase.execute(LauncherAction.LaunchApp("tessar", packageName = "net.fixture.tessar.one"))

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(
            ExecutableAction.LaunchAppAction("net.fixture.tessar.one", null),
            executor.executedActions.single(),
        )
    }
```

`SystemIntentToolWorkerTest`, append:

```kotlin

    @Test
    fun `a pinned launch opens the pinned package even when two apps share the label`() = runTest {
        apps.appsToReturn = listOf(
            InstalledApp(packageName = "com.a", label = "убер", activityName = "Main"),
            InstalledApp(packageName = "com.b", label = "убер", activityName = "Main"),
        )

        val result = executor().invoke(
            ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер", "app" to "com.b")),
        )

        assertEquals(ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер"))), result)
        assertEquals(listOf(ExecutableAction.LaunchAppAction("com.b", "Main")), actionExecutor.executedActions)
    }
```

`SystemIntentToolSourceTest > the argument schema is carried over from the catalog`: change the
`launch_app` line to

```kotlin
        // The catalog's `query`, plus the tool-level pin (A4′ phase 3a): optional, set only by the device.
        assertEquals(listOf("query", "app"), source.find(ToolIds.LAUNCH_APP)!!.argSchema.map { it.name })
        assertEquals(false, source.find(ToolIds.LAUNCH_APP)!!.argSchema.single { it.name == "app" }.required)
```

`AppTargetResolverTest`, append (the file's private `FakeApps`, `FakeAliases`, `EmptyAliases`,
`FailingApps` and `app(…)` helpers, `:18-50`, are reused as they are):

```kotlin

    // --- candidates (A4′ phase 3a, spec §7.12.3 condition 2): the decline is no longer one shape ---

    @Test
    fun `two apps sharing a label are both candidates, never one guessed`() = runTest {
        val one = app("Tessar", "net.fixture.tessar.one")
        val two = app("Tessar", "net.fixture.tessar.two")
        val resolver = AppTargetResolver(FakeApps(listOf(one, two)), EmptyAliases)

        assertEquals(AppCandidates.Several(listOf(one, two)), resolver.candidates("tessar"))
    }

    @Test
    fun `an alias names exactly one candidate`() = runTest {
        val zantor = app("Zantor", "net.fixture.zantor")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor)), FakeAliases(mapOf("кварцелот" to "net.fixture.zantor")))

        assertEquals(AppCandidates.One(zantor), resolver.candidates("Кварцелот"))
    }

    @Test
    fun `a repository failure is unavailable, not an empty answer`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases)

        assertEquals(AppCandidates.Unavailable, resolver.candidates("tessar"))
    }
```

`AgentSessionSurfaceProvenanceTest`, append before the class's closing `}` (reusing its `render` and
the imports it has; add `com.sidr.launcher.domain.tool.ToolIds` if absent):

```kotlin

    /**
     * A4′ phase 3a, Task 2. A launch the device pinned to a package (the binding pick, plan §0.2 B-3)
     * names BOTH what the user called it and the package that will open — two apps can share the label.
     * Fixture label and package share no substring (R14-43), so neither half can pass for the other.
     */
    @Test
    fun `a pinned launch step names the app and the package it is pinned to`() {
        val goal = AgentGoal("включи мне vemblic", GoalShape.Free("включи мне vemblic"))
        val session = AgentSession(
            id = AgentSessionId("s1"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            ToolIds.LAUNCH_APP,
                            mapOf("query" to ArgSource.Literal("Vemblic"), "app" to ArgSource.Literal("net.fixture.zantor")),
                        ),
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

        render(session, emptyMap())

        compose.onNodeWithText("Open Vemblic (net.fixture.zantor) — in progress").assertExists()
    }
```

- [ ] **Step 2: run — expect compile-red.** `tools/gate.sh --scoped :domain:jvmTest` →
  `No parameter with name 'packageName'`; `:data:repository` → `Unresolved reference 'AppCandidates'`.

- [ ] **Step 3: implement.**

`LauncherAction.kt` — replace

```kotlin
    data class LaunchApp(val query: String) : LauncherAction {
```

with

```kotlin
    /**
     * [packageName] pins the launch to one installed package (A4′ phase 3a): the device binds the
     * model's word for an app BEFORE the interpretation card, and two apps can share a label, so the
     * pick has to survive to execution. `null` — every path but the bridge's — resolves [query] by
     * label exactly as before.
     */
    data class LaunchApp(val query: String, val packageName: String? = null) : LauncherAction {
```

`ExecuteActionUseCase.kt` — replace `resolveAndRoute(LauncherIntent.LaunchAppIntent(action.query))`
with `resolveAndRoute(LauncherIntent.LaunchAppIntent(action.query, action.packageName))`.

`IntentActionResolver.kt` — in `resolveAppLaunch`, replace

```kotlin
        val apps = (appsResult as OperationResult.Success).value
        val query = intent.displayNameQuery.lowercase(Locale.ROOT)
```

with

```kotlin
        val apps = (appsResult as OperationResult.Success).value

        // A4′ phase 3a (plan §0.2 B-3): a launch the device already pinned to a package opens exactly
        // that package. `LaunchAppIntent.packageName` existed unused until now; nothing but the bridge
        // sets it, so every other path below is byte-for-byte unchanged. Gone since the pin was made →
        // "not found" under the words the user typed, never a fallback to the label.
        intent.packageName?.let { pinned ->
            val app = apps.firstOrNull { it.packageName == pinned }
            return OperationResult.Success(
                if (app != null) {
                    ExecutableAction.LaunchAppAction(packageName = app.packageName, activityName = app.activityName)
                } else {
                    ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound(intent.displayNameQuery))
                },
            )
        }

        val query = intent.displayNameQuery.lowercase(Locale.ROOT)
```

`SystemIntentToolSource.kt` — change `project` to take extra tool-level arguments and pin `launch_app`:
replace `project(ActionIds.LAUNCH_APP, outputSchema = RESOLVED_QUERY_OUTPUT),` with
`project(ActionIds.LAUNCH_APP, extraArgs = PIN_INPUT, outputSchema = RESOLVED_QUERY_OUTPUT),`; replace
the signature

```kotlin
    private fun project(
        actionId: ActionId,
        outputSchema: List<ActionArg> = emptyList(),
    ): ToolDescriptor? {
```

with

```kotlin
    private fun project(
        actionId: ActionId,
        extraArgs: List<ActionArg> = emptyList(),
        outputSchema: List<ActionArg> = emptyList(),
    ): ToolDescriptor? {
```

and `            argSchema = descriptor.argSchema,` with
`            argSchema = descriptor.argSchema + extraArgs,`; in the companion add

```kotlin

        /**
         * `launch_app`'s pin (A4′ phase 3a, plan §0.2 B-3). A property of the TOOL, like
         * [RESOLVED_QUERY] — `ActionDescriptor` has no such argument. Optional: every path but the
         * bridge leaves it out, and the device, never the model, fills it (`DeviceMentionBinder`).
         */
        internal const val PIN_ARG = "app"

        val PIN_INPUT = listOf(
            ActionArg(PIN_ARG, required = false, description = "The package this launch is pinned to"),
        )
```

`SystemIntentToolWorker.kt` — replace
`            ToolIds.LAUNCH_APP -> arg("query")?.let { LauncherAction.LaunchApp(it) }` with

```kotlin
            ToolIds.LAUNCH_APP -> arg("query")?.let {
                LauncherAction.LaunchApp(it, packageName = arg(SystemIntentToolSource.PIN_ARG))
            }
```

`ToolArgumentSorts.kt` — in `ROWS`, before the `uninstall_app` row, add

```kotlin
            // A4′ phase 3a (plan §0.2 B-3): the bridge binds the model's word for an app BEFORE the
            // card — the package into the pin `app`, the user's words into `query`, which the step
            // line, the `resolved_query` output and a "not found" message all speak. `query` is
            // REQUIRED, which this catalog's KDoc says a label argument must not be; that rule exists
            // because ToolMatchPlanner checks required arguments before resolving, and launch_app is
            // never selected by the vocabulary at step 2b. The bridge fills both before validation.
            ToolIds.LAUNCH_APP to AppArgumentBinding(arg = "app", labelArg = "query"),
```

(with `import com.sidr.launcher.domain.tool.ToolIds`).

`AppTargetResolver.kt` — above the class, add

```kotlin
/**
 * What a name resolves to on this device (A4′ phase 3a, spec §7.12.3 condition 2). Until then
 * [AppTargetResolver.resolve] folded "two candidates", "none", "blank" and "the list could not be read"
 * into one `null`; the bridge must tell them apart — two candidates are a choice for the user, none is
 * an honest "not found", a failed read is neither.
 */
sealed interface AppCandidates {
    data object None : AppCandidates
    data class One(val app: InstalledApp) : AppCandidates
    data class Several(val apps: List<InstalledApp>) : AppCandidates
    data object Unavailable : AppCandidates
}
```

(with `import com.sidr.launcher.domain.model.InstalledApp`) and replace the body of `resolve` with:

```kotlin
    /**
     * Every candidate the name can mean, never a guess: an alias first (if its target is still
     * installed), then every app whose normalized label is the normalized name.
     */
    suspend fun candidates(query: String): AppCandidates {
        val normalized = CommandNormalizer.normalize(query)
        if (normalized.isBlank()) return AppCandidates.None

        val installed = (apps.getInstalledApps() as? OperationResult.Success)?.value
            ?: return AppCandidates.Unavailable

        (aliases.find(normalized) as? OperationResult.Success)?.value?.target?.appPackageOrNull()
            ?.let { pkg -> installed.firstOrNull { it.packageName == pkg } }
            ?.let { return AppCandidates.One(it) }

        val matches = installed.filter { CommandNormalizer.normalize(it.label) == normalized }
        return when (matches.size) {
            0 -> AppCandidates.None
            1 -> AppCandidates.One(matches.single())
            else -> AppCandidates.Several(matches)
        }
    }

    /** One package or nothing — the shape `ToolMatchPlanner` (step 2b) has always declined on. */
    suspend fun resolve(query: String): String? =
        (candidates(query) as? AppCandidates.One)?.app?.packageName
```

`FakeToolRegistry.kt` — `launch_app`'s `argSchema` becomes

```kotlin
                    argSchema = listOf(
                        ActionArg("query", description = "The app name to launch"),
                        ActionArg("app", required = false, description = "The package this launch is pinned to"),
                    ),
```

`AgentSessionPresentation.kt` — after `private const val APP_LABEL_ARG = "app_label"` add
`private const val QUERY_ARG = "query"`; in `PlanStep.line`, inside `when (invocation.id.value) {`,
before `MEMORY_SET_APP_ALIAS -> {`, add

```kotlin
        ToolIds.LAUNCH_APP.value -> {
            // A4′ phase 3a: a launch the device pinned (the binding pick) names what the user called it
            // AND the package that will open — two apps can share a label. Unpinned, it has one literal
            // and falls through to the line it always had.
            val label = literal(QUERY_ARG)
            val pkg = literal(APP_ARG)
            if (label != null && pkg != null) {
                return sidrString(R.string.launcher_agent_step_launch_pinned, label, pkg)
            }
        }
```

Strings, after `launcher_agent_step_launch` in each file:

| key | `values` | `values-ru` | `values-tr` |
|---|---|---|---|
| `launcher_agent_step_launch_pinned` | `Open %1$s (%2$s)` | `Открыть %1$s (%2$s)` | `%1$s uygulamasını aç (%2$s)` |

- [ ] **Step 4: run — expect green.**
  - `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=483`.
  - `tools/gate.sh --scoped :data:repository:testDebugUnitTest` → `repository tests=417`.
  - `tools/gate.sh --scoped :feature:launcher:testDebugUnitTest` → `launcher tests=217`.

- [ ] **Step 5: mutations — three.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M2a | `IntentActionResolver.kt` · `intent.packageName?.let { pinned ->` → `(null as String?)?.let { pinned ->` | the pin reaches execution | `:domain` — `IntentActionResolverTest > a pinned package launches that package even when two apps share the label`, `> a pinned package that is no longer installed is not found under the typed name`, `ExecuteActionUseCaseTest > a pinned launch reaches the resolver with its package`; `:data:repository` — `SystemIntentToolWorkerTest > a pinned launch opens the pinned package even when two apps share the label` |
| M2b | `AppTargetResolver.kt` · `else -> AppCandidates.Several(matches)` → `else -> AppCandidates.One(matches.first())` | ambiguity stays a choice | `:data:repository` — `AppTargetResolverTest > two apps sharing a label are both candidates, never one guessed` and `> two apps with the same label decline rather than guess` |
| M2c | `AgentSessionPresentation.kt` · `return sidrString(R.string.launcher_agent_step_launch_pinned, label, pkg)` → `return sidrString(R.string.launcher_agent_step_launch, label)` | the card shows the pinned package | `:feature:launcher` — `AgentSessionSurfaceProvenanceTest > a pinned launch step names the app and the package it is pinned to` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1571`:
  `:domain` **483** (+3), `:data:repository` **417** (+4), `:feature:launcher` **217** (+1).

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src data/repository/src core/testing/src feature/launcher/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): launch_app привязывается к пакету; резолвер отдаёт кандидатов

Условие 2 D3 (спека §7.12.3), план §0.2 B-3. LaunchAppIntent.packageName
существовал и не использовался — IntentActionResolver теперь его чтит:
привязанный запуск открывает ровно этот пакет, остальные пути не тронуты.
launch_app получает необязательный аргумент app (пин), строку каталога сортов
и строку шага «Открыть X (пакет)». AppTargetResolver.candidates различает
«двух», «ни одного» и «не прочитать»; resolve() выведен из него.

Мутации M2a–M2c.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: the bridge's offering, its vocabulary port, and its rendering

**Spec:** §7.1 barriers 1–2, §7.5, §7.6 (with owner fork F3a-A), §7.12.1 («наш словарь, пространства
имён, аргументы»), §7.12.2 («схема генерируется в `:domain` из того же отфильтрованного набора, что
текст промпта»), §7.12.3 (`ToolSchemaWeightTest` re-anchored); criteria 6, 7, 8, 18 (agreement half).
**Modules:** `:domain`, `:app` (one guard's term list).

**Files:**
- Create in `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/bridge/`: `ToolDescriptions.kt`,
  `SidrDestination.kt`, `BridgeError.kt`, `BridgeOffering.kt`, `BridgeRendering.kt`.
- Modify: `domain/build.gradle.kts` — `jvmTest.dependencies` gains `implementation(libs.serialization.json)`.
- Create: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/bridge/BridgeOfferingTest.kt`,
  `.../ai/bridge/BridgeRenderingTest.kt`, `.../ai/bridge/ToolSchemaWeightTest.kt`.
- Delete: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/ToolSchemaWeightTest.kt` (its
  one test moves to `bridge/`, re-anchored — the count does not change).
- Modify: `app/src/test/java/com/sidr/launcher/agent/AgentVocabularyGuardTest.kt` — `forbidden`
  (`:90-99`) and its KDoc; `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/alias/AliasPrivacyScopeGuardTest.kt:33-41`,
  `.../memory/resolution/ResolutionPrivacyScopeGuardTest.kt:37-45`,
  `.../prayer/PrayerLocationPrivacyGuardTest.kt:291-299` — three bridge names added to each list.

**Interfaces.**
- Produces (`com.sidr.launcher.domain.ai.bridge`):
  ```kotlin
  enum class ToolNamespace { LAUNCHER, DEVICE, CONTENT }
  data class ModelArg(val name: String, val description: String, val required: Boolean, val mention: Boolean)
  data class ToolDescription(val namespace: ToolNamespace, val text: String, val args: List<ModelArg>)
  fun interface ToolDescriptions { fun describe(id: ToolId): ToolDescription? }
  enum class SidrDestination(val wire: String) { LAUNCHER_SETTINGS("launcher_settings"), APP_GRID("app_grid") }
  enum class BridgeError { SHAPE, UNKNOWN_TOOL, NOT_OFFERED, UNDERIVABLE_MENTION }
  data class OfferedTool(val descriptor: ToolDescriptor, val description: ToolDescription)
  data class BridgeOffering(val tools: List<OfferedTool>) { fun find(id: ToolId): OfferedTool? }
  object BridgeFilter { val OFFERED_LEVELS: Set<ToolLevel>; fun offeringOf(registry: ToolRegistry, descriptions: ToolDescriptions): BridgeOffering }
  object BridgeRendering {
      const val INSTRUCTION: String            // 1 140 chars
      const val KIND_PLAN / KIND_ASSISTANT / KIND_NAVIGATE / KIND_CLARIFY / KIND_DECLINE: String
      val KINDS: List<String>
      fun systemText(offering: BridgeOffering): String
      fun offeringText(offering: BridgeOffering): String
      fun schemaJson(offering: BridgeOffering): String
      fun retryNote(error: BridgeError): String
  }
  ```

- [ ] **Step 1: write the failing tests.** Create `BridgeOfferingTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the model is offered (spec §7.5, §7.6, §7.12.1; owner fork F3a-A). The filter is a predicate on
 * a property every tool declares about itself — its level — never a list of ids; and it is an
 * ALLOW-list, so a level nobody has decided about is not offered.
 */
class BridgeOfferingTest {

    private fun tool(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private val described = ToolDescriptions { id ->
        ToolDescription(ToolNamespace.DEVICE, "Fixture tool ${id.value}.", emptyList())
    }

    private fun offered(vararg tools: ToolDescriptor, descriptions: ToolDescriptions = described): List<String> =
        BridgeFilter.offeringOf(FakeToolRegistry(tools.toList()), descriptions).tools.map { it.descriptor.id.value }

    @Test
    fun `the authored levels are offered, each tool with its description`() {
        val registry = FakeToolRegistry(
            listOf(tool("fixture_quill", ToolLevels.IN_APP), tool("fixture_ledger", ToolLevels.SYSTEM_INTENT)),
        )

        val offering = BridgeFilter.offeringOf(registry, described)

        assertEquals(listOf("fixture_quill", "fixture_ledger"), offering.tools.map { it.descriptor.id.value })
        assertEquals("Fixture tool fixture_ledger.", offering.find(ToolId("fixture_ledger"))!!.description.text)
    }

    /** Criterion 7 (spec §7.5, `B13`): the agent changes its own memory only by an explicit trigger. */
    @Test
    fun `launcher memory is never offered`() {
        assertEquals(
            listOf("fixture_quill"),
            offered(tool("fixture_quill", ToolLevels.IN_APP), tool("fixture_note", ToolLevels.LAUNCHER_MEMORY)),
        )
    }

    /** Criterion 6 / D1: a shortcut's name is third-party text — the user's world, and an injection path. */
    @Test
    fun `a shortcut is never offered, even with a description`() {
        assertEquals(
            listOf("fixture_quill"),
            offered(
                tool("fixture_quill", ToolLevels.IN_APP),
                tool("shortcut:net.fixture.qorvath/brimwell", ToolLevels.APP_SHORTCUT),
            ),
        )
    }

    /** Criterion 8 (spec §7.6): tool №21 at an offered level is offered with no edit to the filter. */
    @Test
    fun `a new tool at an offered level is offered without touching the filter`() {
        assertEquals(listOf("fixture_parcel"), offered(tool("fixture_parcel", ToolLevels.SYSTEM_INTENT)))
    }

    /**
     * Owner fork F3a-A (allow). `ToolLevel` is open; AppFunctions or MCP would arrive as a new level
     * carrying third-party names. Under the allow-list such a level is offered only by decision.
     * Under (deny) this test inverts to `…is offered`.
     */
    @Test
    fun `a tool at a level the filter has not heard of is not offered`() {
        assertEquals(emptyList<String>(), offered(tool("fixture_unnote", ToolLevel("app_function"))))
    }

    /** Fail-closed: the model may only see what we have described in our own words. */
    @Test
    fun `a tool with no description is not offered`() {
        val partial = ToolDescriptions { id ->
            if (id.value == "fixture_quill") ToolDescription(ToolNamespace.DEVICE, "Fixture.", emptyList()) else null
        }

        assertEquals(
            listOf("fixture_quill"),
            offered(
                tool("fixture_quill", ToolLevels.IN_APP),
                tool("fixture_ledger", ToolLevels.SYSTEM_INTENT),
                descriptions = partial,
            ),
        )
    }
}
```

Create `BridgeRenderingTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The text that leaves the device for the model (spec §7.12.2–§7.12.3). Two halves, held apart:
 *  - the STATIC text — the instruction and the four retry notes — is the only text scanned against
 *    the denylist: the ids of our own tools legitimately contain `usage` and `location`, and narrowing
 *    the denylist for them is forbidden (spec §7.12.3 (3));
 *  - the OFFERING-derived text and the JSON schema must name one set — read off the schema's TEXT, so a
 *    tool that reached only the schema is seen (criterion 18).
 */
class BridgeRenderingTest {

    private val staticTexts: List<String> =
        listOf(BridgeRendering.INSTRUCTION) + BridgeError.entries.map(BridgeRendering::retryNote)

    private fun descriptor(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private fun fixtureOffering() = BridgeOffering(
        listOf(
            OfferedTool(
                descriptor("fixture_quill", ToolLevels.IN_APP),
                ToolDescription(
                    ToolNamespace.LAUNCHER,
                    "Open a quill.",
                    listOf(ModelArg("app", "the app, in the user's own words", required = true, mention = true)),
                ),
            ),
            OfferedTool(
                descriptor("fixture_ledger", ToolLevels.SYSTEM_INTENT),
                ToolDescription(
                    ToolNamespace.DEVICE,
                    "Write the ledger.",
                    listOf(ModelArg("duration", "how long", required = true, mention = false)),
                ),
            ),
            OfferedTool(
                descriptor("fixture_parcel", ToolLevels.IN_APP),
                ToolDescription(ToolNamespace.CONTENT, "Find a parcel.", emptyList()),
            ),
        ),
    )

    @Test
    fun `the static text carries no forbidden context term`() {
        val hits = staticTexts.flatMap { text ->
            OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS.filter { text.contains(it, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `the static text carries no credential term`() {
        val hits = staticTexts.flatMap { text ->
            OutboundContextPolicy.CREDENTIAL_TERMS.filter { text.contains(it, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), hits)
    }

    /** The renderer takes ONLY the offering: an empty one renders nothing but the static instruction. */
    @Test
    fun `an empty offering renders exactly the static instruction`() {
        assertEquals(BridgeRendering.INSTRUCTION, BridgeRendering.systemText(BridgeOffering(emptyList())))
    }

    @Test
    fun `the schema and the prompt name the same tools and the same argument names`() {
        val offering = fixtureOffering()
        val schema = Json.parseToJsonElement(BridgeRendering.schemaJson(offering)).jsonObject
        val stepProperties = schema.getValue("properties").jsonObject
            .getValue("step").jsonObject.getValue("properties").jsonObject
        val schemaTools = stepProperties.getValue("tool").jsonObject.getValue("enum").jsonArray
            .map { it.jsonPrimitive.content }
        val schemaArgs = stepProperties.getValue("args").jsonObject.getValue("properties").jsonObject.keys.toList()

        val prompt = BridgeRendering.systemText(offering)
        val promptTools = Regex("^- ([^:]+): ", RegexOption.MULTILINE).findAll(prompt)
            .map { it.groupValues[1] }.toList()
        val promptArgs = Regex("Every argument name: ([^.]*)\\.").find(prompt)!!.groupValues[1].split(", ")

        assertEquals(promptTools, schemaTools)
        assertEquals(promptArgs, schemaArgs)
        // The control: both are non-empty and in the offering's namespace order, so the two equalities
        // above cannot pass by comparing two empty lists.
        assertEquals(listOf("fixture_quill", "fixture_ledger", "fixture_parcel"), schemaTools)
        assertEquals(listOf("app", "duration"), schemaArgs)
    }

    /** Strict-compatible (D2, b′): every object closes its properties and requires every one of them. */
    @Test
    fun `every object in the schema closes its properties and requires all of them`() {
        val problems = mutableListOf<String>()
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> {
                    element["properties"]?.jsonObject?.let { properties ->
                        if (element["additionalProperties"] != JsonPrimitive(false)) problems += "$path: open"
                        val required = element["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
                        if (required != properties.keys) problems += "$path: required $required != ${properties.keys}"
                    }
                    element.forEach { (key, value) -> walk(value, "$path.$key") }
                }
                is JsonArray -> element.forEachIndexed { index, value -> walk(value, "$path[$index]") }
                else -> Unit
            }
        }

        walk(Json.parseToJsonElement(BridgeRendering.schemaJson(fixtureOffering())), "$")

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `the offering is grouped by namespace, one line per tool, in offering order`() {
        val expected =
            "Every argument name: app, duration. Give \"args\" every one of these names: the value for " +
                "each argument the chosen tool takes, null for the rest.\n" +
                "\n" +
                "Launcher (Sidr itself):\n" +
                "- fixture_quill: Open a quill. args: app (app, required) — the app, in the user's own words\n" +
                "\n" +
                "Device (this phone's own functions):\n" +
                "- fixture_ledger: Write the ledger. args: duration (required) — how long\n" +
                "\n" +
                "Content (the web and the app store):\n" +
                "- fixture_parcel: Find a parcel."

        assertEquals(expected, BridgeRendering.offeringText(fixtureOffering()))
    }
}
```

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/bridge/ToolSchemaWeightTest.kt` (and
`git rm` the old one under `ai/router/`):

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bridge's prompt weighs — re-anchored from the deleted router (spec §7.12.3). The previous
 * version measured a local helper modelled on `CatalogSchemaRenderer` with the router's 562-char
 * instruction as a proxy preamble; the real renderer exists now, so this measures it.
 *
 * Synthetic descriptors on purpose (`tool_i`, one launcher namespace, "Do one thing.", no arguments):
 * `:domain` cannot reach the production descriptions, and a synthetic series is what makes the
 * marginal cost a property of the RENDERER rather than of whatever we wrote about a tool. Every number
 * pinned below was computed before the code existed (plan §0.10) from the exact strings the plan
 * specifies; a mismatch is a finding about the renderer, not a band to widen.
 *
 * `B3`'s conclusion stands on the real renderer: two hundred synthetic tools render to about 1 600
 * tokens — the prompt size was never the barrier.
 */
class ToolSchemaWeightTest {

    @Test
    fun `the bridge prompt's preamble and per-tool cost are what they were measured to be`() {
        listOf(2, 15, 120, 200).forEach { n ->
            val text = render(n)
            println("bridge prompt (synthetic, no args): n=$n chars=${text.length} approx_tokens=${text.length / 4}")
        }

        assertEquals(
            "BridgeRendering.INSTRUCTION changed length; re-measure and update the ADR rather than this pin",
            1_140,
            render(0).length,
        )
        val marginal = (render(120).length - render(2).length) / 118.0
        assertTrue("marginal per-tool cost drifted to $marginal (measured ~25.1)", marginal in 24.0..26.5)
        assertTrue("15 tools render to ${render(15).length} (measured 1 668)", render(15).length in 1_650..1_690)
    }

    private fun render(n: Int): String {
        val registry = FakeToolRegistry(
            List(n) {
                ToolDescriptor(
                    id = ToolId("tool_$it"),
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.LOCAL,
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                )
            },
        )
        val offering = BridgeFilter.offeringOf(registry) { ToolDescription(ToolNamespace.LAUNCHER, "Do one thing.", emptyList()) }
        return BridgeRendering.systemText(offering)
    }
}
```

- [ ] **Step 2: run — expect compile-red.** `tools/gate.sh --scoped :domain:jvmTest` →
  `Unresolved reference 'BridgeFilter'` (and `kotlinx.serialization` until `build.gradle.kts` is edited).

- [ ] **Step 3: implement.**

`domain/build.gradle.kts` — in `jvmTest.dependencies { … }` add
`implementation(libs.serialization.json)` with the comment
`// A4′ phase 3a (plan §0.2 B-5): the schema-agreement test reads the schema's TEXT. Test-only.`

`ToolDescriptions.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.tool.ToolId

/**
 * Where a tool sits in OUR vocabulary for the model (spec §7.12.1). Three, so the model can tell
 * Sidr's own surfaces from the phone's functions from the world outside: "settings" alone means two
 * different screens (Sidr's, Android's), and the namespace is what separates them.
 */
enum class ToolNamespace { LAUNCHER, DEVICE, CONTENT }

/**
 * One argument as the MODEL sees it — deliberately not `ActionArg` (spec §7.12.1). `uninstall_app`
 * declares `app` as "Package of the app to remove"; the model must never write a package, so its view
 * of the same argument is "the app, in the user's own words".
 *
 * [mention] marks an argument that names something in the user's world (today: an app). The model
 * copies the user's words into it; `BridgeValidator` refuses words the user did not type; the device
 * binds them (`MentionBinder`). One bit, not a sort taxonomy — the sort system is 3b (spec §7.8, `F6`).
 */
data class ModelArg(
    val name: String,
    val description: String,
    val required: Boolean,
    val mention: Boolean,
)

/** What we tell the model about one tool: our literals only, never a third party's text. */
data class ToolDescription(
    val namespace: ToolNamespace,
    val text: String,
    val args: List<ModelArg>,
)

/**
 * The parallel port of descriptions (spec §7.1 barrier 1): beside `ToolDescriptor`, not a field in it,
 * so `ToolDescriptor` stays free of user-facing or model-facing copy. `null` means "we have not
 * described this tool", and an undescribed tool is never offered (`BridgeFilter`).
 */
fun interface ToolDescriptions {
    fun describe(id: ToolId): ToolDescription?
}
```

`SidrDestination.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

/**
 * A screen of Sidr itself the model may name in a `navigate` answer (spec §7.12.3: navigation is an
 * answer, not a tool). [wire] is what the model writes; it appears in the instruction and the schema.
 * The assistant is not here: it is its own answer kind, carrying the question.
 */
enum class SidrDestination(val wire: String) {
    LAUNCHER_SETTINGS("launcher_settings"),
    APP_GRID("app_grid"),
}
```

`BridgeError.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

/**
 * Why an answer was rejected — the CLOSED vocabulary the one retry may carry back to the model (spec
 * §7.12.2). A retry sends the code's static note and nothing else: never the rejected answer, never
 * anything the device found while binding.
 */
enum class BridgeError { SHAPE, UNKNOWN_TOOL, NOT_OFFERED, UNDERIVABLE_MENTION }
```

`BridgeOffering.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry

/** One tool the model is offered, with what we say about it. */
data class OfferedTool(val descriptor: ToolDescriptor, val description: ToolDescription)

/**
 * The filtered set the prompt, the schema and the validator all read (spec §7.12.2: membership is
 * checked against THIS set, not the federation — otherwise an invented or injected `set_app_alias` or
 * `shortcut:…` would pass). Computed per call: the federation's tool set moves while the process lives.
 */
data class BridgeOffering(val tools: List<OfferedTool>) {
    fun find(id: ToolId): OfferedTool? = tools.firstOrNull { it.descriptor.id == id }
}

object BridgeFilter {

    /**
     * Owner fork F3a-A (allow). A predicate on the level every tool declares about itself — not a list
     * of ids (spec §7.6, criterion 8) — and an ALLOW-list, so a level nobody decided about (AppFunctions,
     * MCP — third-party names, like `app_shortcut`) is not offered by default. `app_shortcut` is out
     * under D1 (the model never sees the user's world); `launcher_memory` is out under §7.5 (`B13`).
     *
     * **The obligation of spec §7.9, restated where it bites:** the day inference becomes local, whoever
     * does it re-checks whether the egress guard can still fail, and widens or keeps this set by a
     * written decision.
     */
    val OFFERED_LEVELS: Set<ToolLevel> = setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT)

    fun offeringOf(registry: ToolRegistry, descriptions: ToolDescriptions): BridgeOffering =
        BridgeOffering(
            registry.all()
                .filter { it.level in OFFERED_LEVELS }
                .mapNotNull { descriptor ->
                    descriptions.describe(descriptor.id)?.let { OfferedTool(descriptor, it) }
                },
        )
}
```

(Under F3a-A (deny): `val EXCLUDED_LEVELS = setOf(ToolLevels.APP_SHORTCUT, ToolLevels.LAUNCHER_MEMORY)` and
`.filter { it.level !in EXCLUDED_LEVELS }`; the KDoc's second sentence and test 5 invert.)

`BridgeRendering.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

/**
 * Everything the bridge says to the model, from ONE offering (spec §7.12.2): the instruction, the
 * offering text, the JSON schema, and the four retry notes. What leaves the device is exactly the
 * user's command, this text, and the generation limits (`OutboundContextPolicy.AllowedContext`
 * `USER_COMMAND`, `STATIC_SYSTEM_PROMPT`, `AUTHORED_TOOL_SCHEMA`, `GENERATION_LIMITS` — the last two
 * from Task 11; until then this object has no caller in production).
 *
 * **b′ (owner decision D2):** the answer form is strict-compatible — every field required, `null`
 * instead of "optional", nothing extra — whether or not the transport sends the schema. The schema is
 * written here as text because `:domain` carries no serialization library; its shape is fixed and
 * ASCII-only (kinds, destinations, tool ids, argument names), and `BridgeRenderingTest` parses it.
 */
object BridgeRendering {

    const val KIND_PLAN = "plan"
    const val KIND_ASSISTANT = "assistant"
    const val KIND_NAVIGATE = "navigate"
    const val KIND_CLARIFY = "clarify"
    const val KIND_DECLINE = "decline"
    val KINDS: List<String> = listOf(KIND_PLAN, KIND_ASSISTANT, KIND_NAVIGATE, KIND_CLARIFY, KIND_DECLINE)

    /**
     * Static and context-free; the only bridge text scanned against `FORBIDDEN_CONTEXT_TERMS` (with the
     * retry notes). One step per plan in 3a (plan §0.2 B-1). 1 140 characters — `ToolSchemaWeightTest`.
     */
    const val INSTRUCTION: String =
        "You read one request typed into Sidr, an Android home screen, and say what it asks for. You see " +
            "only the request and the tools listed below; you know nothing else about this phone. Reply with " +
            "ONLY one JSON object, without prose or code fences, of exactly this form:\n" +
            "{\"kind\": K, \"step\": S, \"question\": Q, \"destination\": D}\n" +
            "\"plan\": the request is one action that one listed tool performs. step = {\"tool\": " +
            "\"<tool id>\", \"args\": {...}}; question and destination are null.\n" +
            "\"assistant\": the request is a question, or a wish to talk. question = the question; step and " +
            "destination are null.\n" +
            "\"navigate\": the request is to open a screen of Sidr itself. destination = " +
            "\"launcher_settings\" (Sidr's own settings) or \"app_grid\" (all apps); step and question are " +
            "null.\n" +
            "\"clarify\": you must ask the user one short question first. question = that question; step and " +
            "destination are null.\n" +
            "\"decline\": nothing listed fits. step, question and destination are null.\n" +
            "A plan has exactly one step. An argument marked \"app\" must repeat the user's own words for the " +
            "app exactly as they typed them, never a package name. Never invent a tool, an argument or a " +
            "destination."

    /** The whole system text: the instruction, then — when anything is offered — the offering. */
    fun systemText(offering: BridgeOffering): String {
        val listed = offeringText(offering)
        return if (listed.isEmpty()) INSTRUCTION else "$INSTRUCTION\n\n$listed"
    }

    /** The offering in our words, grouped by namespace; the ONLY offering-derived part of the prompt. */
    fun offeringText(offering: BridgeOffering): String {
        val tools = ordered(offering)
        if (tools.isEmpty()) return ""
        val names = argNamesOf(offering)
        return buildString {
            append("Every argument name: ")
            append(if (names.isEmpty()) "(none)" else names.joinToString(", "))
            append(". Give \"args\" every one of these names: the value for each argument the chosen tool takes, null for the rest.")
            ToolNamespace.entries.forEach { namespace ->
                val inNamespace = tools.filter { it.description.namespace == namespace }
                if (inNamespace.isEmpty()) return@forEach
                append("\n\n")
                append(headerOf(namespace))
                inNamespace.forEach { tool ->
                    append("\n- ")
                    append(tool.descriptor.id.value)
                    append(": ")
                    append(tool.description.text)
                    if (tool.description.args.isNotEmpty()) {
                        append(" args: ")
                        append(
                            tool.description.args.joinToString(", ") { arg ->
                                "${arg.name} (${markersOf(arg)}) — ${arg.description}"
                            },
                        )
                    }
                }
            }
        }
    }

    /** The JSON schema of the answer, from the same offering and in the same order as [offeringText]. */
    fun schemaJson(offering: BridgeOffering): String {
        val names = argNamesOf(offering)
        val nullableString = JsonText.obj("type" to JsonText.arr(listOf(JsonText.str("string"), JsonText.str("null"))))
        val args = JsonText.obj(
            "type" to JsonText.str("object"),
            "additionalProperties" to "false",
            "required" to JsonText.arr(names.map(JsonText::str)),
            "properties" to JsonText.obj(*names.map { it to nullableString }.toTypedArray()),
        )
        val step = JsonText.obj(
            "type" to JsonText.arr(listOf(JsonText.str("object"), JsonText.str("null"))),
            "additionalProperties" to "false",
            "required" to JsonText.arr(listOf(JsonText.str("tool"), JsonText.str("args"))),
            "properties" to JsonText.obj(
                "tool" to JsonText.obj(
                    "type" to JsonText.str("string"),
                    "enum" to JsonText.arr(ordered(offering).map { JsonText.str(it.descriptor.id.value) }),
                ),
                "args" to args,
            ),
        )
        return JsonText.obj(
            "type" to JsonText.str("object"),
            "additionalProperties" to "false",
            "required" to JsonText.arr(listOf("kind", "step", "question", "destination").map(JsonText::str)),
            "properties" to JsonText.obj(
                "kind" to JsonText.obj("type" to JsonText.str("string"), "enum" to JsonText.arr(KINDS.map(JsonText::str))),
                "step" to step,
                "question" to nullableString,
                "destination" to JsonText.obj(
                    "type" to JsonText.arr(listOf(JsonText.str("string"), JsonText.str("null"))),
                    "enum" to JsonText.arr(SidrDestination.entries.map { JsonText.str(it.wire) } + "null"),
                ),
            ),
        )
    }

    /** The static note a retry adds — the code and nothing else (spec §7.12.2). */
    fun retryNote(error: BridgeError): String = when (error) {
        BridgeError.SHAPE ->
            "Your previous reply was rejected: it was not one JSON object of the required form. Reply again with only that object."
        // Corrected in execution (2026-10-04, Task 4 review, finding E4-R1): ONE text for both codes — two
        // texts told the model whether an id it proposed is registered on this phone (D1). Commit 7a5d8d8.
        BridgeError.UNKNOWN_TOOL, BridgeError.NOT_OFFERED ->
            "Your previous reply was rejected: it named a tool that is not among the tool ids listed above. Use only those ids."
        BridgeError.UNDERIVABLE_MENTION ->
            "Your previous reply was rejected: an argument marked \"app\" held words the user did not type. Copy the user's own words for the app."
    }

    private fun ordered(offering: BridgeOffering): List<OfferedTool> =
        ToolNamespace.entries.flatMap { namespace -> offering.tools.filter { it.description.namespace == namespace } }

    private fun argNamesOf(offering: BridgeOffering): List<String> =
        offering.tools.flatMap { it.description.args }.map { it.name }.distinct().sorted()

    private fun headerOf(namespace: ToolNamespace): String = when (namespace) {
        ToolNamespace.LAUNCHER -> "Launcher (Sidr itself):"
        ToolNamespace.DEVICE -> "Device (this phone's own functions):"
        ToolNamespace.CONTENT -> "Content (the web and the app store):"
    }

    private fun markersOf(arg: ModelArg): String =
        listOfNotNull(if (arg.mention) "app" else null, if (arg.required) "required" else "optional").joinToString(", ")
}

/** A minimal JSON writer for the schema's fixed shape — values passed in are already JSON text. */
internal object JsonText {
    fun str(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }

    fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

    fun obj(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", "{", "}") { (key, value) -> "${str(key)}:$value" }
}
```

`AgentVocabularyGuardTest` — extend `forbidden` with

```kotlin
        // A4′ phase 3a: the bridge. The engine plans nothing with a model and names no model port —
        // a paused plan the model wrote goes back through the ROUTE (spec §7.12.3 condition 4), never
        // through anything `domain/agent` can see. The package term covers every bridge type.
        "ModelPlanner",
        "BridgeTransport",
        "com.sidr.launcher.domain.ai",
```

and add one sentence to the KDoc after «…or to a transport — `GenerativeAiEngine`, `CommandPlanner`,
Ktor —»: «— and since A4′ phase 3a the bridge's model port and its package —». (`CommandPlanner` stays in
the list until Task 11 deletes the type.)

The three privacy guards — append to each `forbiddenTerms` list, after `"CatalogSchemaRenderer",`:
`"ModelPlanner",`, `"BridgeRendering",`, `"BridgeTransport",`.

- [ ] **Step 4: run — expect green.** `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=495`;
  `tools/gate.sh --scoped :app:testDebugUnitTest` → `app tests=66`.

- [ ] **Step 5: mutations — five.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M3a | `BridgeOffering.kt` · `.filter { it.level in OFFERED_LEVELS }` → `.filter { it.id.value in setOf("launch_app", "set_timer") }` | criterion 8: a list of ids is not the filter | `:domain` — `BridgeOfferingTest > the authored levels are offered, each tool with its description`, `> launcher memory is never offered`, `> a shortcut is never offered, even with a description`, `> a new tool at an offered level is offered without touching the filter`, `> a tool with no description is not offered` (five; the fixture names no production id). `ToolSchemaWeightTest` also reddens (nothing offered → preamble only) — record it |
| M3b | `BridgeOffering.kt` · `setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT)` → `setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT, ToolLevels.LAUNCHER_MEMORY)` | criterion 7 | `:domain` — `BridgeOfferingTest > launcher memory is never offered` **only** |
| M3c | `BridgeOffering.kt` · same old → `setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT, ToolLevels.APP_SHORTCUT)` | criterion 6 (D1) | `:domain` — `BridgeOfferingTest > a shortcut is never offered, even with a description` **only** |
| M3d | `BridgeRendering.kt` · `"enum" to JsonText.arr(ordered(offering).map { JsonText.str(it.descriptor.id.value) }),` → `"enum" to JsonText.arr(ordered(offering).map { JsonText.str(it.descriptor.id.value) } + JsonText.str("fixture_extra")),` | criterion 18: a tool only in the schema | `:domain` — `BridgeRenderingTest > the schema and the prompt name the same tools and the same argument names` **only** |
| M3e | `ResumeAgentSessionUseCase.kt` · `import com.sidr.launcher.domain.result.OperationResult` → `import com.sidr.launcher.domain.result.OperationResult\nimport com.sidr.launcher.domain.ai.bridge.BridgeRendering` | the engine may not name the bridge | `:app` — `AgentVocabularyGuardTest > the engine names no action vocabulary and no transport` **only** (an unused import is a warning) |

- [ ] **Step 6: boundary gate from a cleared tree** (`./gradlew --stop` first). Expected `GATE GREEN`,
  `TOTAL tests=1583`: `:domain` **495** (+12), every other module as after Task 2.

- [ ] **Step 7: commit (controller).**

```bash
git add domain/build.gradle.kts domain/src app/src/test
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): выдача моста, порт описаний, рендерер и схема

Спека §7.5, §7.6, §7.12.1–§7.12.2; развилка владельца F3a-A (allow-list уровней).
Порт ToolDescriptions — наш словарь с пространствами имён и аргументами
модели (упоминание приложения — отдельный бит). Промпт и JSON-схема
генерируются из одной выдачи; тест согласия читает ТЕКСТ схемы. Запрет-лист
сканирует только статический текст инструкции и повторов. ToolSchemaWeightTest
переякорен на преамбулу моста (1 140). Гварды словаря и приватности знают
имена моста.

Мутации M3a–M3e.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: the bridge's answer — the validator, derivability, one retry, the budget

**Spec:** §7.12.1 («упоминания вместо значений… выводимы из команды… минимум меток»), §7.12.2 (variants,
validator, one retry, budget, honest refusal); criteria 16, 18 (retry half), 19 (budget half).
**Modules:** `:domain`, `:core:testing`.

**Files:**
- Create in `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/bridge/`: `ModelPlanning.kt`,
  `BridgeAnswer.kt`, `Derivability.kt`, `BridgeValidator.kt`, `BridgeBudget.kt`,
  `BridgeModelPlanner.kt`.
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/ModelUnavailableReason.kt`.
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeModelPlanner.kt`,
  `.../FakeBridgeTransport.kt`.
- Create: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/bridge/BridgeFixtures.kt` (no test),
  `DerivabilityTest.kt` (5), `BridgeValidatorTest.kt` (12), `BridgeModelPlannerTest.kt` (8).

**Interfaces.**
- Produces (`com.sidr.launcher.domain.ai.bridge` unless noted):
  ```kotlin
  @JvmInline value class PrincipalId(val value: String) { companion object { val USER: PrincipalId } }
  sealed interface ValueOrigin { data class SaidBy(val principal: PrincipalId); data object Ours; data object ModelGenerated }
  data class ProposedStep(val tool: ToolId, val args: Map<String, String>, val origins: Map<String, ValueOrigin>,
                          val risk: ActionRiskLevel, val effect: ToolEffect)
  sealed interface ModelPlanning { Proposed(step) · ToAssistant(question) · Navigate(destination) ·
                                   Clarify(question) · Declined · Unavailable(reason: ModelUnavailableReason) }
  fun interface ModelPlanner { suspend fun plan(command: String): ModelPlanning }
  data class RawStep(val tool: String?, val args: Map<String, String?>)
  data class RawAnswer(val kind: String?, val step: RawStep?, val question: String?, val destination: String?)
  data class ReplyMeta(val finishReason: String?, val completionTokens: Int?, val reasoningTokens: Int?) { companion object { val NONE } }
  sealed interface TransportReply { Answer(answer, meta) · Unparseable(meta) · Failed(error: AiError) }
  data class BridgeRequest(val system: String, val schemaJson: String, val command: String,
                           val retryNote: String?, val maxOutputTokens: Int)
  fun interface BridgeTransport { suspend fun ask(request: BridgeRequest): TransportReply }
  object Derivability { fun isDerivable(mention: String, command: String): Boolean; fun originOf(value: String, command: String): ValueOrigin }
  sealed interface Verdict { Valid(planning: ModelPlanning) · Rejected(error: BridgeError) }
  object BridgeValidator { fun validate(answer: RawAnswer, offering: BridgeOffering, registry: ToolRegistry, command: String): Verdict }
  data class BridgeBudget(val ceilingMs: Long, val maxOutputTokens: Int) { companion object { val Default } }
  class BridgeModelPlanner(registry: ToolRegistry, descriptions: ToolDescriptions, transport: BridgeTransport,
                           budget: BridgeBudget = BridgeBudget.Default) : ModelPlanner
  // com.sidr.launcher.domain.intent
  enum class ModelUnavailableReason { CREDENTIALS, REQUEST_REJECTED, PROVIDER_FAILING, TIMED_OUT, NETWORK, UNREADABLE }
  // :core:testing
  class FakeModelPlanner(var answer: ModelPlanning = ModelPlanning.Declined) : ModelPlanner  // planCallCount, lastCommand
  class FakeBridgeTransport(replies: List<TransportReply>, delayMs: Long = 0L) : BridgeTransport  // requests
  ```

- [ ] **Step 1: write the failing tests.** Create `BridgeFixtures.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels

/**
 * One registry and one set of descriptions for the validator and planner tests. Three offered tools,
 * one memory tool and one shortcut — both REGISTERED, both outside the offering, so "not offered" is
 * a different answer from "unknown". Every fixture word is invented (R14-43).
 */
internal object BridgeFixtures {

    private fun descriptor(id: String, level: ToolLevel, risk: ActionRiskLevel = ActionRiskLevel.SAFE, args: List<String> = emptyList()) =
        ToolDescriptor(
            id = ToolId(id),
            level = level,
            effect = ToolEffect.EXTERNAL,
            argSchema = args.map { ActionArg(it, description = it) },
            risk = risk,
            durability = ToolDurability.TRANSIENT,
        )

    val registry = FakeToolRegistry(
        listOf(
            descriptor("launch_app", ToolLevels.IN_APP, args = listOf("query", "app")),
            descriptor("web_search", ToolLevels.IN_APP, args = listOf("query")),
            descriptor("open_url", ToolLevels.IN_APP, ActionRiskLevel.CONFIRM, listOf("url")),
            descriptor("set_app_alias", ToolLevels.LAUNCHER_MEMORY, args = listOf("app", "phrase", "app_label")),
            descriptor("shortcut:net.fixture.qorvath/brimwell", ToolLevels.APP_SHORTCUT),
        ),
    )

    val descriptions = ToolDescriptions { id ->
        when (id.value) {
            "launch_app" -> ToolDescription(
                ToolNamespace.LAUNCHER,
                "Open an app.",
                listOf(ModelArg("app", "the app, in the user's own words", required = true, mention = true)),
            )
            "web_search" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Search the web.",
                listOf(ModelArg("query", "what to search for", required = true, mention = false)),
            )
            "open_url" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Open an address.",
                listOf(ModelArg("url", "the address", required = true, mention = false)),
            )
            // Described on purpose: it is the LEVEL that keeps it out, not a missing description.
            "set_app_alias" -> ToolDescription(
                ToolNamespace.LAUNCHER,
                "Name an app.",
                listOf(
                    ModelArg("app", "the app", required = true, mention = true),
                    ModelArg("phrase", "the name", required = true, mention = false),
                ),
            )
            else -> null
        }
    }

    val offering: BridgeOffering get() = BridgeFilter.offeringOf(registry, descriptions)

    fun plan(tool: String?, vararg args: Pair<String, String?>) =
        RawAnswer(kind = BridgeRendering.KIND_PLAN, step = RawStep(tool, mapOf(*args)), question = null, destination = null)

    fun answer(kind: String?, question: String? = null, destination: String? = null) =
        RawAnswer(kind = kind, step = null, question = question, destination = destination)
}
```

Create `DerivabilityTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1's first mechanism (spec §7.12.1): the model writes the user's words, and the device checks they
 * ARE the user's words — whole words, in order, any case. The base case only: word forms and
 * references to an earlier turn are 3b's (sorts) or `B16`'s, and they fail here as named limits.
 */
class DerivabilityTest {

    @Test
    fun `a whole-word fragment of the command is derivable, whatever its case`() {
        assertTrue(Derivability.isDerivable("Lumora", "открой мне LUMORA пожалуйста"))
        assertTrue(Derivability.isDerivable("tessar two", "запусти Tessar Two"))
    }

    @Test
    fun `part of a word is not derivable`() {
        assertFalse(Derivability.isDerivable("lum", "открой lumora"))
    }

    /** The limit spec §7.12.1 names for 3a: «телегу» is not «телега». An honest refusal, never a guess. */
    @Test
    fun `another form of the word is not derivable`() {
        assertFalse(Derivability.isDerivable("телега", "открой телегу"))
    }

    @Test
    fun `a blank mention is not derivable`() {
        assertFalse(Derivability.isDerivable("  ", "открой lumora"))
    }

    @Test
    fun `a value the user typed is said by the user, anything else is the model's`() {
        assertEquals(
            ValueOrigin.SaidBy(PrincipalId.USER),
            Derivability.originOf("ostravel lectures", "найди ostravel lectures в интернете"),
        )
        assertEquals(
            ValueOrigin.ModelGenerated,
            Derivability.originOf("https://quillmere.example/path?q=a&b=c", "открой сайт quillmere"),
        )
    }
}
```

Create `BridgeValidatorTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.answer
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.offering
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.plan
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.registry
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bridge's first line (spec §7.12.2): shape, membership in THE OFFERING (not the federation),
 * derivability. Everything here runs before `startPlanned`; the engine's own check against the
 * federation is the second line, not the first.
 */
class BridgeValidatorTest {

    private fun validate(answer: RawAnswer, command: String = "открой lumora") =
        BridgeValidator.validate(answer, offering, registry, command)

    @Test
    fun `a plan over an offered tool is valid, with its risk and effect from the registry`() {
        val verdict = validate(plan("launch_app", "app" to "lumora", "query" to null, "url" to null))

        assertEquals(
            Verdict.Valid(
                ModelPlanning.Proposed(
                    ProposedStep(
                        tool = ToolId("launch_app"),
                        args = mapOf("app" to "lumora"),
                        origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                        risk = ActionRiskLevel.SAFE,
                        effect = ToolEffect.EXTERNAL,
                    ),
                ),
            ),
            verdict,
        )
    }

    @Test
    fun `a tool id nothing registers is unknown`() {
        assertEquals(Verdict.Rejected(BridgeError.UNKNOWN_TOOL), validate(plan("fixture_quill")))
    }

    /** Criterion 16, second half: membership is the OFFERING's. Both are registered; neither is offered. */
    @Test
    fun `a registered tool outside the offering is rejected before anything runs`() {
        assertEquals(
            Verdict.Rejected(BridgeError.NOT_OFFERED),
            validate(plan("set_app_alias", "app" to "lumora", "phrase" to "кварцелот")),
        )
        assertEquals(
            Verdict.Rejected(BridgeError.NOT_OFFERED),
            validate(plan("shortcut:net.fixture.qorvath/brimwell")),
        )
    }

    /** Criterion 16, first half: an app argument the user did not type is not the user's app. */
    @Test
    fun `an app mention the user did not type is rejected as underivable`() {
        assertEquals(
            Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION),
            validate(plan("launch_app", "app" to "lumora"), command = "открой мой мессенджер"),
        )
    }

    @Test
    fun `a missing required argument is a shape error`() {
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(plan("web_search", "query" to null)))
    }

    @Test
    fun `an argument the tool does not take, given a value, is a shape error`() {
        assertEquals(
            Verdict.Rejected(BridgeError.SHAPE),
            validate(plan("launch_app", "app" to "lumora", "url" to "https://quillmere.example")),
        )
    }

    @Test
    fun `an assistant answer needs its question`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.ToAssistant("почему небо синее")),
            validate(answer(BridgeRendering.KIND_ASSISTANT, question = " почему небо синее ")),
        )
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_ASSISTANT, question = " ")))
    }

    @Test
    fun `a navigation answer names one of Sidr's screens`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.Navigate(SidrDestination.APP_GRID)),
            validate(answer(BridgeRendering.KIND_NAVIGATE, destination = "app_grid")),
        )
        assertEquals(
            Verdict.Rejected(BridgeError.SHAPE),
            validate(answer(BridgeRendering.KIND_NAVIGATE, destination = "system_settings")),
        )
    }

    @Test
    fun `a clarify answer needs its question`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.Clarify("какой мессенджер?")),
            validate(answer(BridgeRendering.KIND_CLARIFY, question = "какой мессенджер?")),
        )
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_CLARIFY)))
    }

    @Test
    fun `a decline is a decline`() {
        assertEquals(Verdict.Valid(ModelPlanning.Declined), validate(answer(BridgeRendering.KIND_DECLINE)))
    }

    @Test
    fun `an unknown kind, no kind, or a plan with no step is a shape error`() {
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer("maybe")))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(null)))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_PLAN)))
    }

    /** The minimum of labels (spec §7.12.1): a search text the user did not type is the model's. */
    @Test
    fun `a search text the user did not type is labelled the model's`() {
        val verdict = validate(
            plan("web_search", "query" to "ostravel lectures by experts"),
            command = "найди лекции ostravel",
        ) as Verdict.Valid
        val step = (verdict.planning as ModelPlanning.Proposed).step

        assertEquals(mapOf("query" to ValueOrigin.ModelGenerated), step.origins)
    }
}
```

Create `BridgeModelPlannerTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeBridgeTransport
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.descriptions
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.plan
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.registry
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The bridge's one model call (spec §7.12.2): one ask, at most one retry that adds ONLY the closed
 * code's static note, a ceiling that covers both, and every failure said by its reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeModelPlannerTest {

    private val command = "открой lumora"
    private val valid = TransportReply.Answer(plan("launch_app", "app" to "lumora"), ReplyMeta.NONE)
    private val notOffered = TransportReply.Answer(
        plan("set_app_alias", "app" to "lumora", "phrase" to "SNTL-REJECTED-ECHO"),
        ReplyMeta.NONE,
    )

    private fun planner(transport: FakeBridgeTransport, budget: BridgeBudget = BridgeBudget.Default) =
        BridgeModelPlanner(registry, descriptions, transport, budget)

    @Test
    fun `a valid first answer asks the model once and returns its reading`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        val result = planner(transport).plan(command)

        assertEquals(ToolId("launch_app"), (result as ModelPlanning.Proposed).step.tool)
        assertEquals(1, transport.requests.size)
    }

    /** Criterion 18: one retry, and the retry is the first request plus the closed code's note — nothing else. */
    @Test
    fun `an invalid answer is asked again exactly once, with only the closed code added`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, valid))

        val result = planner(transport).plan(command)

        assertEquals(ToolId("launch_app"), (result as ModelPlanning.Proposed).step.tool)
        assertEquals(2, transport.requests.size)
        assertEquals(
            transport.requests[0].copy(retryNote = BridgeRendering.retryNote(BridgeError.NOT_OFFERED)),
            transport.requests[1],
        )
        assertFalse(transport.requests[1].toString().contains("SNTL-REJECTED-ECHO"))
    }

    /** Criterion 18: never a second retry. */
    @Test
    fun `a second invalid answer is unreadable, and the model is never asked a third time`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, notOffered, valid))

        val result = planner(transport).plan(command)

        assertEquals(ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE), result)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `an unparseable first reply is asked again with the shape code`() = runTest {
        val transport = FakeBridgeTransport(listOf(TransportReply.Unparseable(ReplyMeta.NONE), valid))

        planner(transport).plan(command)

        assertEquals(BridgeRendering.retryNote(BridgeError.SHAPE), transport.requests[1].retryNote)
    }

    /** Finding (c) of the A1″ acceptance: a broken key, a dead endpoint, a refused model each say so. */
    @Test
    fun `a transport failure is reported by its reason and never asked again`() = runTest {
        val table = listOf(
            AiError.Unauthorized to ModelUnavailableReason.CREDENTIALS,
            AiError.MissingCredentials to ModelUnavailableReason.CREDENTIALS,
            AiError.InvalidRequest("http_404") to ModelUnavailableReason.REQUEST_REJECTED,
            AiError.RateLimited(null) to ModelUnavailableReason.PROVIDER_FAILING,
            AiError.ServerError(502) to ModelUnavailableReason.PROVIDER_FAILING,
            AiError.Timeout to ModelUnavailableReason.TIMED_OUT,
            AiError.Network("IOException") to ModelUnavailableReason.NETWORK,
        )
        table.forEach { (error, reason) ->
            val transport = FakeBridgeTransport(listOf(TransportReply.Failed(error)))

            assertEquals("$error", ModelPlanning.Unavailable(reason), planner(transport).plan(command))
            assertEquals("$error", 1, transport.requests.size)
        }
    }

    /** Criterion 19: the ceiling bounds the WHOLE planning, retry included. */
    @Test
    fun `the ceiling covers both attempts`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, valid), delayMs = 30_000)

        val result = planner(transport, BridgeBudget(ceilingMs = 45_000, maxOutputTokens = 2_048)).plan(command)

        assertEquals(ModelPlanning.Unavailable(ModelUnavailableReason.TIMED_OUT), result)
        assertEquals(45_000L, currentTime)
    }

    @Test
    fun `the request carries the rendered offering and the command verbatim`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        planner(transport).plan(command)

        val offering = BridgeFilter.offeringOf(registry, descriptions)
        val request = transport.requests.single()
        assertEquals(BridgeRendering.systemText(offering), request.system)
        assertEquals(BridgeRendering.schemaJson(offering), request.schemaJson)
        assertEquals(command, request.command)
        assertEquals(null, request.retryNote)
    }

    @Test
    fun `the budget's output limit reaches the request`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        planner(transport, BridgeBudget(ceilingMs = 45_000, maxOutputTokens = 777)).plan(command)

        assertEquals(777, transport.requests.single().maxOutputTokens)
    }
}
```

- [ ] **Step 2: run — expect compile-red.** `tools/gate.sh --scoped :domain:jvmTest` →
  `Unresolved reference 'Derivability'` (and `FakeBridgeTransport`).

- [ ] **Step 3: implement.**

`domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/ModelUnavailableReason.kt`:

```kotlin
package com.sidr.launcher.domain.intent

/**
 * Why the model gave no usable answer — finding (c) of the A1″ acceptance: a wrong key, a wrong model,
 * a dead endpoint and "the model answered prose" used to fold into one silent "unknown command". Each
 * is now said as what it is; the feature layer picks the sentence.
 */
enum class ModelUnavailableReason { CREDENTIALS, REQUEST_REJECTED, PROVIDER_FAILING, TIMED_OUT, NETWORK, UNREADABLE }
```

`ModelPlanning.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId

/**
 * Who said a value (spec §7.12.1, manifest §19). Today the principal is always the user; an open value
 * class so a machine principal later is a new value, not a rename across the core.
 */
@JvmInline
value class PrincipalId(val value: String) {
    companion object {
        val USER = PrincipalId("user")
    }
}

/**
 * The minimum of labels (spec §7.12.1): where a value on the card came from. **For the card's lifetime
 * only — never stored**: the plan on disk carries the bound values, as every plan does.
 */
sealed interface ValueOrigin {
    /** Words the principal typed — derivable from the command. */
    data class SaidBy(val principal: PrincipalId) : ValueOrigin

    /** The device's own determination — a package the binder resolved. */
    data object Ours : ValueOrigin

    /** Text the model produced. On an `EXTERNAL` step the card shows it verbatim (`DOC-MZN-2`). */
    data object ModelGenerated : ValueOrigin
}

/**
 * One step the model proposed, validated (shape, membership, derivability) and not yet bound. [args]
 * are MODEL-facing names (an app mention under `app`); the device maps them onto the tool.
 */
data class ProposedStep(
    val tool: ToolId,
    val args: Map<String, String>,
    val origins: Map<String, ValueOrigin>,
    val risk: ActionRiskLevel,
    val effect: ToolEffect,
)

/**
 * The model planner's CLOSED result (spec §7.12.2): five answers of the model and one outcome of the
 * port. A new variant is a compile error at `RouteCommandUseCase` step 7, where the decision belongs —
 * the lesson `PlanningResult` paid for (spec §3 0.0).
 */
sealed interface ModelPlanning {
    data class Proposed(val step: ProposedStep) : ModelPlanning
    data class ToAssistant(val question: String) : ModelPlanning
    data class Navigate(val destination: SidrDestination) : ModelPlanning

    /** One verbatim question — today's single clarify, NOT a clarification protocol (spec §1, §3). */
    data class Clarify(val question: String) : ModelPlanning
    data object Declined : ModelPlanning

    /** Not an answer of the model: the port's honest "no usable answer, and why". */
    data class Unavailable(val reason: ModelUnavailableReason) : ModelPlanning
}

/**
 * The bridge's port (spec §7.12.2) — deliberately NOT `Planner`. `Planner` is asked at step 2b (above
 * the `localOnlyMode` gate) and on resume — exactly where a model may never be called (`DOC-ADL-3`).
 * This one is asked at `RouteCommandUseCase` step 7 only, below gates 3, 5 and 6.
 */
fun interface ModelPlanner {
    suspend fun plan(command: String): ModelPlanning
}
```

`BridgeAnswer.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.ai.AiError

/** One step as the transport read it — unvalidated, every field possibly absent. */
data class RawStep(val tool: String?, val args: Map<String, String?>)

/** The answer object as the transport read it: plain values, judged by `BridgeValidator`. */
data class RawAnswer(val kind: String?, val step: RawStep?, val question: String?, val destination: String?)

/**
 * What the provider reported about a reply, for the transport measurement (spec §7.12.2: «входят ли
 * скрытые рассуждения в лимит токенов»). Never user content.
 */
data class ReplyMeta(val finishReason: String?, val completionTokens: Int?, val reasoningTokens: Int?) {
    companion object {
        val NONE = ReplyMeta(finishReason = null, completionTokens = null, reasoningTokens = null)
    }
}

sealed interface TransportReply {
    data class Answer(val answer: RawAnswer, val meta: ReplyMeta) : TransportReply

    /** A reply that is not one JSON object in the expected place — prose, a cut answer, an error body. */
    data class Unparseable(val meta: ReplyMeta) : TransportReply

    /** No reply: the request failed, by the same vocabulary as the assistant's engine. */
    data class Failed(val error: AiError) : TransportReply
}

/**
 * Exactly what may leave the device (B-4): the system text (instruction + offering), the schema text
 * (sent only in the transport's schema forms), the user's command verbatim, an optional static retry
 * note, and the generation limit.
 */
data class BridgeRequest(
    val system: String,
    val schemaJson: String,
    val command: String,
    val retryNote: String?,
    val maxOutputTokens: Int,
)

/** The bridge's own transport — its own path, not `GenerativeAiEngine` (spec §7.12.2). Never throws. */
fun interface BridgeTransport {
    suspend fun ask(request: BridgeRequest): TransportReply
}
```

`Derivability.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

/**
 * "The model wrote the user's words" made checkable (spec §7.12.1, D1 mechanism 1): a value is
 * derivable when its words appear, whole and in order, in the command — case-insensitive, punctuation
 * ignored. The base case only, by design: «телегу» is not «телега», and a reference to an earlier turn
 * is not in the command at all. Both end as honest refusals (3b, `B16`), never as a guess.
 */
object Derivability {

    fun isDerivable(mention: String, command: String): Boolean {
        val wanted = words(mention)
        if (wanted.isEmpty()) return false
        val typed = words(command)
        return typed.windowed(wanted.size).any { it == wanted }
    }

    fun originOf(value: String, command: String): ValueOrigin =
        if (isDerivable(value, command)) ValueOrigin.SaidBy(PrincipalId.USER) else ValueOrigin.ModelGenerated

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
}
```

`BridgeValidator.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry

sealed interface Verdict {
    data class Valid(val planning: ModelPlanning) : Verdict
    data class Rejected(val error: BridgeError) : Verdict
}

/**
 * The bridge's first line (spec §7.12.2). Three checks, all before `startPlanned`:
 *  - **shape** — a known kind with what it needs; a plan's step names a tool and gives every argument
 *    it requires, and no argument it does not take;
 *  - **membership in the offering** — not in the federation: `set_app_alias` and `shortcut:…` are
 *    registered and `SAFE`, the engine would not stop them, and `B13` would rest on one render filter;
 *  - **derivability** — an argument the description marks as a mention holds the user's own words.
 * It reports nothing the device knows: binding happens after it, and its outcome never returns here.
 */
object BridgeValidator {

    fun validate(answer: RawAnswer, offering: BridgeOffering, registry: ToolRegistry, command: String): Verdict =
        when (answer.kind?.trim()) {
            BridgeRendering.KIND_PLAN -> validatePlan(answer.step, offering, registry, command)
            BridgeRendering.KIND_ASSISTANT -> answer.question.nonBlank()
                ?.let { Verdict.Valid(ModelPlanning.ToAssistant(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_NAVIGATE -> SidrDestination.entries.firstOrNull { it.wire == answer.destination?.trim() }
                ?.let { Verdict.Valid(ModelPlanning.Navigate(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_CLARIFY -> answer.question.nonBlank()
                ?.let { Verdict.Valid(ModelPlanning.Clarify(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_DECLINE -> Verdict.Valid(ModelPlanning.Declined)
            else -> Verdict.Rejected(BridgeError.SHAPE)
        }

    private fun validatePlan(step: RawStep?, offering: BridgeOffering, registry: ToolRegistry, command: String): Verdict {
        val toolText = step?.tool?.trim().orEmpty()
        if (step == null || toolText.isEmpty()) return Verdict.Rejected(BridgeError.SHAPE)
        val id = ToolId(toolText)
        val offered = offering.find(id)
            ?: return if (registry.find(id) == null) Verdict.Rejected(BridgeError.UNKNOWN_TOOL) else Verdict.Rejected(BridgeError.NOT_OFFERED)

        val declared = offered.description.args.map { it.name }.toSet()
        if (step.args.any { (name, value) -> name !in declared && !value.isNullOrBlank() }) {
            return Verdict.Rejected(BridgeError.SHAPE)
        }

        val values = linkedMapOf<String, String>()
        val origins = linkedMapOf<String, ValueOrigin>()
        for (arg in offered.description.args) {
            val value = step.args[arg.name]?.trim()
            if (value.isNullOrEmpty()) {
                if (arg.required) return Verdict.Rejected(BridgeError.SHAPE)
                continue
            }
            if (arg.mention && !Derivability.isDerivable(value, command)) {
                return Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION)
            }
            values[arg.name] = value
            origins[arg.name] = Derivability.originOf(value, command)
        }
        return Verdict.Valid(
            ModelPlanning.Proposed(ProposedStep(id, values, origins, offered.descriptor.risk, offered.descriptor.effect)),
        )
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
```

`BridgeBudget.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

/**
 * The bridge's own budget (spec §7.12.2) — named, and not the router's 2 000 ms / 256 tokens, which a
 * reasoning model never fits. [ceilingMs] bounds the WHOLE planning, the one retry included;
 * [maxOutputTokens] bounds each answer. The transport's socket is bounded by the same [ceilingMs] per
 * attempt (`bridgeHttpClient`), so the socket never ends a wait the ceiling still allows.
 *
 * **Provisional until the owner's transport measurement** (plan Task 12) and the ADR fix them: 45 s sits
 * inside the discussion memo's 30–60 s hypothesis; 2 048 tokens leaves room for hidden reasoning if the
 * provider counts it, which is one of the measurement's questions. The budget on ACTIONS
 * (`RuntimeBudget`) is a different thing and does not change.
 */
data class BridgeBudget(val ceilingMs: Long, val maxOutputTokens: Int) {
    companion object {
        val Default = BridgeBudget(ceilingMs = 45_000L, maxOutputTokens = 2_048)
    }
}
```

`BridgeModelPlanner.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The bridge's [ModelPlanner] (spec §7.12.2): one ask; on a rejected or unreadable answer exactly one
 * more, carrying ONLY the closed code's static note; a transport failure is never retried — it is
 * reported by its reason. The offering is computed per call (the federation moves while the process
 * lives), and the prompt, the schema and the validator all read that one offering.
 *
 * It binds nothing and knows nothing of the device: binding runs after it returns
 * (`RouteCommandUseCase` step 7, `MentionBinder`), so nothing binding finds can reach a retry.
 */
class BridgeModelPlanner(
    private val registry: ToolRegistry,
    private val descriptions: ToolDescriptions,
    private val transport: BridgeTransport,
    private val budget: BridgeBudget = BridgeBudget.Default,
) : ModelPlanner {

    override suspend fun plan(command: String): ModelPlanning =
        withTimeoutOrNull(budget.ceilingMs) { attempt(command) }
            ?: ModelPlanning.Unavailable(ModelUnavailableReason.TIMED_OUT)

    private suspend fun attempt(command: String): ModelPlanning {
        val offering = BridgeFilter.offeringOf(registry, descriptions)
        val first = BridgeRequest(
            system = BridgeRendering.systemText(offering),
            schemaJson = BridgeRendering.schemaJson(offering),
            command = command,
            retryNote = null,
            maxOutputTokens = budget.maxOutputTokens,
        )
        val firstReply = transport.ask(first)
        val error = when (val judged = judge(firstReply, offering, command)) {
            is Judged.Done -> return judged.planning
            is Judged.Retry -> judged.error
        }
        val retry = first.copy(retryNote = BridgeRendering.retryNote(error))
        return when (val judged = judge(transport.ask(retry), offering, command)) {
            is Judged.Done -> judged.planning
            is Judged.Retry -> ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE)
        }
    }

    private fun judge(reply: TransportReply, offering: BridgeOffering, command: String): Judged = when (reply) {
        is TransportReply.Failed -> Judged.Done(ModelPlanning.Unavailable(reasonOf(reply.error)))
        is TransportReply.Unparseable -> Judged.Retry(BridgeError.SHAPE)
        is TransportReply.Answer -> when (val verdict = BridgeValidator.validate(reply.answer, offering, registry, command)) {
            is Verdict.Valid -> Judged.Done(verdict.planning)
            is Verdict.Rejected -> Judged.Retry(verdict.error)
        }
    }

    private sealed interface Judged {
        data class Done(val planning: ModelPlanning) : Judged
        data class Retry(val error: BridgeError) : Judged
    }

    companion object {
        /** The assistant's transport vocabulary, folded to what the user can act on. */
        fun reasonOf(error: AiError): ModelUnavailableReason = when (error) {
            AiError.MissingCredentials, AiError.Unauthorized -> ModelUnavailableReason.CREDENTIALS
            is AiError.InvalidRequest -> ModelUnavailableReason.REQUEST_REJECTED
            is AiError.RateLimited, is AiError.ServerError, is AiError.Unknown -> ModelUnavailableReason.PROVIDER_FAILING
            AiError.Timeout -> ModelUnavailableReason.TIMED_OUT
            AiError.Offline, is AiError.Network -> ModelUnavailableReason.NETWORK
        }
    }
}
```

`core/testing/.../FakeModelPlanner.kt`:

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning

/**
 * In-memory [ModelPlanner]. Returns [answer] (default `Declined` — the case that leaves FastPath's
 * outcome in place) and records what it was asked. The property names are `FakeCommandPlanner`'s on
 * purpose (A4′ phase 3a, Task 11): every "the model was (not) asked" assertion in the ten files that
 * used the router's fake moves over unchanged.
 */
class FakeModelPlanner(var answer: ModelPlanning = ModelPlanning.Declined) : ModelPlanner {

    var planCallCount: Int = 0
        private set

    /** The last command the model was asked about; `null` until [plan] is called. */
    var lastCommand: String? = null
        private set

    override suspend fun plan(command: String): ModelPlanning {
        planCallCount++
        lastCommand = command
        return answer
    }
}
```

`core/testing/.../FakeBridgeTransport.kt`:

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.TransportReply
import kotlinx.coroutines.delay

/**
 * A scripted [BridgeTransport]: one reply per ask, in order, each after [delayMs] of (virtual) time.
 * An ask beyond the script throws — a planner that asks more often than the test expected fails loudly.
 */
class FakeBridgeTransport(replies: List<TransportReply>, private val delayMs: Long = 0L) : BridgeTransport {

    private val queue = ArrayDeque(replies)

    val requests = mutableListOf<BridgeRequest>()

    override suspend fun ask(request: BridgeRequest): TransportReply {
        requests += request
        if (delayMs > 0) delay(delayMs)
        return queue.removeFirstOrNull() ?: error("FakeBridgeTransport: ask #${requests.size} has no scripted reply")
    }
}
```

- [ ] **Step 4: run — expect green.** `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=520`.

- [ ] **Step 5: mutations — seven.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M4a | `Derivability.kt` · `return typed.windowed(wanted.size).any { it == wanted }` → `return true` | criterion 16a | `:domain` — `DerivabilityTest > part of a word is not derivable`, `> another form of the word is not derivable`, `> a value the user typed is said by the user, anything else is the model's`; `BridgeValidatorTest > an app mention the user did not type is rejected as underivable`, `> a search text the user did not type is labelled the model's` |
| M4b | `BridgeValidator.kt` · `else Verdict.Rejected(BridgeError.NOT_OFFERED)` → `else Verdict.Valid(ModelPlanning.Declined)` | criterion 16b | `:domain` — `BridgeValidatorTest > a registered tool outside the offering is rejected before anything runs`; `BridgeModelPlannerTest > an invalid answer is asked again exactly once, with only the closed code added`, `> a second invalid answer is unreadable, and the model is never asked a third time`, `> the ceiling covers both attempts` (all three script `notOffered` as their invalid answer, which the plant turns into a valid `Declined` — found by the plan review) |
| M4c | `BridgeModelPlanner.kt` · `return when (val judged = judge(transport.ask(retry), offering, command)) {` → `return when (val judged = judge(TransportReply.Unparseable(ReplyMeta.NONE), offering, command)) {` | criterion 18: zero retries | `:domain` — `BridgeModelPlannerTest > an invalid answer is asked again exactly once, with only the closed code added`, `> a second invalid answer is unreadable, and the model is never asked a third time`, `> an unparseable first reply is asked again with the shape code`, **and `> the ceiling covers both attempts`** (measured in execution 2026-10-04, finding E4-1: without the retry planning ends at 30 s with `UNREADABLE`, inside the 45 s ceiling — the same mechanism review M2 applied to M4b; M4b, M4c and M4f all redden the ceiling test, so a red ceiling test alone says nothing about the ceiling) |
| M4d | `BridgeModelPlanner.kt` · `is Judged.Retry -> ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE)` → `is Judged.Retry -> (judge(transport.ask(retry), offering, command) as? Judged.Done)?.planning ?: ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE)` | criterion 18: two retries | `:domain` — `BridgeModelPlannerTest > a second invalid answer is unreadable, and the model is never asked a third time` **only** |
| M4e | `BridgeModelPlanner.kt` · `val retry = first.copy(retryNote = BridgeRendering.retryNote(error))` → `val retry = first.copy(retryNote = BridgeRendering.retryNote(error) + " " + firstReply)` | the retry echoes nothing | `:domain` — `BridgeModelPlannerTest > an invalid answer is asked again exactly once, with only the closed code added`, `> an unparseable first reply is asked again with the shape code` |
| M4f | `BridgeModelPlanner.kt` · `withTimeoutOrNull(budget.ceilingMs)` → `withTimeoutOrNull(budget.ceilingMs * 2)` | criterion 19: the ceiling covers the retry | `:domain` — `BridgeModelPlannerTest > the ceiling covers both attempts` **only** |
| M4g | `BridgeModelPlanner.kt` · `is TransportReply.Failed -> Judged.Done(ModelPlanning.Unavailable(reasonOf(reply.error)))` → `is TransportReply.Failed -> Judged.Retry(BridgeError.SHAPE)` | a failure is not retried | `:domain` — `BridgeModelPlannerTest > a transport failure is reported by its reason and never asked again` **only** (the fake throws on the unscripted second ask) |

- [ ] **Step 6: boundary gate from a cleared tree** (`./gradlew --stop` first). Expected `GATE GREEN`,
  `TOTAL tests=1608`: `:domain` **520** (+25).

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src core/testing/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): ответ моста — валидатор, выводимость, один повтор, бюджет

Спека §7.12.1–§7.12.2. Порт ModelPlanner (не Planner) с закрытым
ModelPlanning: план · ассистент · навигация · уточнение · отказ модели ·
«модель недоступна: причина» (находка (c) A1″). Валидатор: форма,
принадлежность ВЫДАЧЕ, выводимость упоминаний; метки происхождения
значений. Ровно один повтор с кодом из закрытого словаря и ничем больше;
потолок бюджета покрывает повтор; сбой транспорта не повторяется.

Мутации M4a–M4g.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: our vocabulary — the authored descriptions, and the production offering held

**Spec:** §7.1 barrier 1, §7.12.1 («описания — наш словарь… пространства имён… покрывающий и
аргументы»), §7.12.3 («счёт выдачи … = 19»); criteria 6, 7 over the production graph. **Modules:**
`:data:repository`, `:app`.

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AuthoredToolDescriptions.kt`.
- Create: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AuthoredToolDescriptionsTest.kt` (4).
- Create: `app/src/test/java/com/sidr/launcher/agent/ProductionGraph.kt` (a fixture, no test) and
  `app/src/test/java/com/sidr/launcher/agent/BridgeOfferingGuardTest.kt` (2).

**Interfaces.**
- Produces: `class AuthoredToolDescriptions @Inject constructor() : ToolDescriptions` with
  `companion object { const val MENTION_ARG = "app" }` and `fun rows(): Map<ToolId, ToolDescription>`;
  `internal object ProductionGraph { val grantsEverything: PermissionPresence; fun shortcutSource(vararg shortcuts: AppShortcut): ShortcutToolSource; fun federation(shortcuts: ShortcutToolSource = shortcutSource()): ToolFederation }`
  (`:app` tests); `BridgeOfferingGuardTest.OFFERED_NINETEEN: Set<String>`.
- Consumes: `ToolDescriptions`, `ToolDescription`, `ModelArg`, `ToolNamespace`, `BridgeFilter` (Task 3);
  `InAppToolIds` (Task 1); `ToolArgumentSorts` with `launch_app`'s row (Task 2).

- [ ] **Step 1: write the failing tests.** `AuthoredToolDescriptionsTest.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Our vocabulary for the model (spec §7.12.1) against the tools that really exist. Read off the two
 * authored sources the bridge may offer — not off a list in this file — so a twentieth tool without a
 * description, or a description for a tool that is gone, is red here.
 */
class AuthoredToolDescriptionsTest {

    private val authored = SystemIntentToolSource(DefaultActionCatalog()).all() +
        Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }).all()

    private val descriptions = AuthoredToolDescriptions()

    @Test
    fun `every authored tool at an offered level has a description`() {
        val missing = authored.map { it.id }.filter { descriptions.describe(it) == null }

        assertEquals(emptyList<ToolId>(), missing)
        // The control: the two sources really are the nineteen (spec §7.12.3), so "nothing missing"
        // cannot mean "nothing read".
        assertEquals(19, authored.size)
    }

    @Test
    fun `every description names a tool the authored sources register`() {
        assertEquals(emptySet<ToolId>(), descriptions.rows().keys - authored.map { it.id }.toSet())
    }

    /** The binder reads ONE name; every app-sort tool must speak it, and nothing else may. */
    @Test
    fun `an app-sort tool takes exactly one mention, under the name the binder reads`() {
        val sorts = ToolArgumentSorts()
        descriptions.rows().forEach { (id, description) ->
            val expected = if (sorts.appBindingFor(id) != null) listOf(AuthoredToolDescriptions.MENTION_ARG) else emptyList()
            assertEquals(id.value, expected, description.args.filter { it.mention }.map { it.name })
        }
    }

    @Test
    fun `every other model argument is an argument the tool declares, with the same requiredness`() {
        val byId = authored.associateBy { it.id }
        descriptions.rows().forEach { (id, description) ->
            description.args.filterNot { it.mention }.forEach { arg ->
                val declared = byId.getValue(id).argSchema.singleOrNull { it.name == arg.name }
                assertTrue("${id.value}.${arg.name} is not declared by the tool", declared != null)
                assertEquals("${id.value}.${arg.name}", declared!!.required, arg.required)
            }
        }
    }
}
```

`ProductionGraph.kt` — the production federation through the composition root's own provider, the
`DoctrineGuardTest.graphFederation()` precedent (`:244-276`), shared by Tasks 5, 6 and 11 rather than
copied three times. `DoctrineGuardTest` itself is not edited.

```kotlin
package com.sidr.launcher.agent

import android.content.Intent
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.IntentLauncher
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolWorker
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.data.repository.agent.memory.MemoryToolWorker
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolFederation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The production tool federation, built by calling `AgentProvidesModule.provideToolFederation` itself
 * with the real sources — what the graph reaches is what the provider returns (`DoctrineGuardTest`'s
 * reasoning). Workers are trivial: nothing here invokes a tool. The shortcut source is NON-empty by
 * default, or every reading of it would pass in silence.
 */
internal object ProductionGraph {

    val grantsEverything = PermissionPresence { true }

    fun shortcutSource(
        vararg shortcuts: AppShortcut = arrayOf(
            AppShortcut(packageName = "com.example.chat", shortcutId = "new_message", appLabel = "Example Chat", shortcutLabel = "New message"),
        ),
    ): ShortcutToolSource {
        val catalog = ShortcutCatalog(query = { shortcuts.toList() }, ioDispatcher = Dispatchers.Unconfined)
        runBlocking { catalog.refresh() }
        return ShortcutToolSource(catalog)
    }

    fun federation(shortcuts: ShortcutToolSource = shortcutSource()): ToolFederation =
        AgentProvidesModule.provideToolFederation(
            inAppRegistry = SystemIntentToolSource(DefaultActionCatalog()),
            inAppWorker = SystemIntentToolWorker(
                ExecuteActionUseCase(resolver = IntentActionResolver(NoApps), executor = NoopExecutor),
            ),
            tier0Registry = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
            tier0Worker = Tier0IntentToolWorker(NoopIntentLauncher, ToolPermissionCatalog(), grantsEverything, "com.sidr.launcher"),
            shortcutRegistry = shortcuts,
            shortcutWorker = ShortcutToolWorker(ShortcutLauncher { _, _ -> }),
            memoryRegistry = MemoryToolSource(),
            memoryWorker = FakeAliasStore().let { aliases ->
                MemoryToolWorker(
                    save = SaveAliasUseCase(aliases),
                    deleteAlias = DeleteAliasUseCase(aliases),
                    deleteChoice = DeleteLearnedChoiceUseCase(FakeResolutionPreferenceStore()),
                    aliases = aliases,
                )
            },
        )

    private object NoApps : InstalledAppsRepository {
        override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> = OperationResult.Success(emptyList())
    }

    private object NoopExecutor : ActionExecutor {
        override suspend fun execute(action: ExecutableAction): ActionExecutionResult = ActionExecutionResult.Success
    }

    private object NoopIntentLauncher : IntentLauncher {
        override fun launch(intent: Intent) = Unit
    }
}
```

(Package paths of `AppShortcut`, `ShortcutCatalog`, `IntentLauncher`, `MemoryToolWorker` are
`DoctrineGuardTest`'s imports; copy them from there if any differs from the above.)

`BridgeOfferingGuardTest.kt`:

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offering over the PRODUCTION graph (spec §7.12.3: 22 authored − 3 memory = 19; criteria 6, 7).
 * `BridgeOfferingTest` (`:domain`) holds the filter on fixtures; this holds that the real federation,
 * through the real filter and our real descriptions, offers exactly the nineteen — with the shortcut
 * tier and the memory tools registered beside them, so "not offered" is not "not there".
 */
class BridgeOfferingGuardTest {

    private val federation = ProductionGraph.federation()
    private val offered = BridgeFilter.offeringOf(federation.registry, AuthoredToolDescriptions())
        .tools.map { it.descriptor.id.value }

    @Test
    fun `the production offering is the nineteen authored tools of the in-app and system levels`() {
        assertEquals(OFFERED_NINETEEN, offered.toSet())
        assertEquals("no tool is offered twice", offered.size, offered.toSet().size)
    }

    @Test
    fun `the shortcut tier and the launcher's memory are registered, and neither reaches the offering`() {
        val registered = federation.registry.all().map { it.id.value }
        assertTrue("control: a shortcut is registered", registered.any { it.startsWith(ShortcutToolIds.PREFIX) })
        assertTrue("control: the memory tools are registered", registered.contains("set_app_alias"))

        assertTrue(offered.none { it.startsWith(ShortcutToolIds.PREFIX) })
        assertTrue(offered.none { it in setOf("set_app_alias", "forget_app_alias", "forget_learned_choice") })
    }

    companion object {
        /** The outbound contract's ids (ADR 2, owner fork F3a-B). Written out, never derived from a source. */
        val OFFERED_NINETEEN: Set<String> = setOf(
            "launch_app", "play_store_search", "web_search", "open_url",
            "set_timer", "open_system_settings", "set_alarm", "uninstall_app", "show_alarms", "open_camera",
            "open_wifi_settings", "open_bluetooth_settings", "open_battery_settings", "open_data_usage_settings",
            "open_display_settings", "open_sound_settings", "open_location_settings",
            "open_notification_settings", "open_app_info",
        )
    }
}
```

- [ ] **Step 2: run — expect compile-red.** `tools/gate.sh --scoped :data:repository:testDebugUnitTest`
  → `Unresolved reference 'AuthoredToolDescriptions'`.

- [ ] **Step 3: implement** `AuthoredToolDescriptions.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.ai.bridge.ModelArg
import com.sidr.launcher.domain.ai.bridge.ToolDescription
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.bridge.ToolNamespace
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import javax.inject.Inject

/**
 * Our vocabulary for the model (spec §7.12.1, D1): one row per authored tool the bridge may offer,
 * written by us, in English — never a third party's text. The model reads these words and nothing of
 * this phone.
 *
 * **An app argument is a mention, not a package.** `uninstall_app` declares `app` as "Package of the app
 * to remove"; the model must never write a package, so every app-sort tool is described with one
 * argument, [MENTION_ARG], "the app, in the user's own words" — `BridgeValidator` checks those are the
 * user's words, `DeviceMentionBinder` turns them into a package. Every other argument keeps the tool's
 * own name and requiredness (`AuthoredToolDescriptionsTest`).
 *
 * The three `launcher_memory` tools have no row: they are never offered (spec §7.5), and a missing
 * description is a second reason they could not be (`BridgeFilter` offers only described tools).
 *
 * Two constructors, the [ToolArgumentSorts] shape: production takes no argument, a test may state rows.
 */
class AuthoredToolDescriptions internal constructor(
    private val rows: Map<ToolId, ToolDescription>,
) : ToolDescriptions {

    @Inject constructor() : this(ROWS)

    override fun describe(id: ToolId): ToolDescription? = rows[id]

    fun rows(): Map<ToolId, ToolDescription> = rows

    companion object {
        /** The model-facing name of every app mention; the binder reads exactly this name. */
        const val MENTION_ARG = "app"

        private const val THE_APP = "the app, in the user's own words"

        private fun mention() = ModelArg(MENTION_ARG, THE_APP, required = true, mention = true)

        private fun text(name: String, description: String) =
            ModelArg(name, description, required = true, mention = false)

        private fun device(text: String, vararg args: ModelArg) =
            ToolDescription(ToolNamespace.DEVICE, text, args.toList())

        private fun content(text: String, vararg args: ModelArg) =
            ToolDescription(ToolNamespace.CONTENT, text, args.toList())

        private val ROWS: Map<ToolId, ToolDescription> = mapOf(
            // Launcher — what Sidr itself does. Sidr's own screens are `navigate` answers, not tools.
            ToolIds.LAUNCH_APP to ToolDescription(ToolNamespace.LAUNCHER, "Open an app installed on this phone.", listOf(mention())),
            // Device — this phone's own functions. "Settings" here are the phone's.
            Tier0ToolIds.UNINSTALL_APP to device("Remove an app from this phone. The user is always asked first.", mention()),
            Tier0ToolIds.OPEN_APP_INFO to device("Show the system information screen of an installed app.", mention()),
            Tier0ToolIds.SET_TIMER to device("Start a countdown timer.", text("duration", "how long, e.g. 10 minutes")),
            Tier0ToolIds.SET_ALARM to device("Set an alarm.", text("time", "the clock time, e.g. 7:30")),
            Tier0ToolIds.SHOW_ALARMS to device("Show the list of alarms."),
            Tier0ToolIds.OPEN_CAMERA to device("Open the camera."),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to device("Open the phone's system settings (not Sidr's own settings)."),
            Tier0ToolIds.OPEN_WIFI_SETTINGS to device("Open the Wi-Fi settings."),
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to device("Open the Bluetooth settings."),
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to device("Open the battery settings."),
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to device("Open the mobile data settings."),
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to device("Open the display settings."),
            Tier0ToolIds.OPEN_SOUND_SETTINGS to device("Open the sound settings."),
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to device("Open the location settings."),
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to device("Open the notification settings."),
            // Content — the web and the app store.
            ToolIds.PLAY_STORE_SEARCH to content("Look up an app in the Play Store, to install it.", text("query", "the app's name to look for")),
            InAppToolIds.WEB_SEARCH to content("Search the web.", text("query", "what to search for")),
            InAppToolIds.OPEN_URL to content("Open a web address in the browser.", text("url", "the full address, starting with https://")),
        )
    }
}
```

- [ ] **Step 4: run — expect green.** `:data:repository` → `tests=421`; `:app` → `tests=68`.

- [ ] **Step 5: mutations — two.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M5a | `AuthoredToolDescriptions.kt` · `            Tier0ToolIds.OPEN_CAMERA to device("Open the camera."),\n` → `` | a tool without our words is caught, and is not offered | `:data:repository` — `AuthoredToolDescriptionsTest > every authored tool at an offered level has a description` **only**; `:app` — `BridgeOfferingGuardTest > the production offering is the nineteen authored tools of the in-app and system levels` **only** (run per module) |
| M5b | `AuthoredToolDescriptions.kt` · `ToolIds.LAUNCH_APP to ToolDescription(ToolNamespace.LAUNCHER, "Open an app installed on this phone.", listOf(mention())),` → `ToolIds.LAUNCH_APP to ToolDescription(ToolNamespace.LAUNCHER, "Open an app installed on this phone.", listOf(text(MENTION_ARG, THE_APP))),` | an app argument is a mention | `:data:repository` — `AuthoredToolDescriptionsTest > an app-sort tool takes exactly one mention, under the name the binder reads` and `> every other model argument is an argument the tool declares, with the same requiredness` (the planted `text(MENTION_ARG, …)` is required, the pin argument `launch_app` declares is optional — found by the plan review) |

- [ ] **Step 6: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1614`:
  `:data:repository` **421** (+4), `:app` **68** (+2).

- [ ] **Step 7: commit (controller).**

```bash
git add data/repository/src app/src/test
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): наш словарь для модели и выдача над продакшн-графом

Спека §7.12.1: девятнадцать описаний — наши литералы с пространствами имён;
аргумент-приложение для модели — упоминание словами пользователя, а не
пакет. Гвард над настоящей федерацией: выдача — ровно девятнадцать
авторских, ярлыки и память зарегистрированы и не выданы.

Мутации M5a–M5b.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: the transport, and the measurement that will choose its default

**Spec:** §7.12.2 («отправка схемы провайдеру — флаг транспорта… транспорт моста — свой путь, не
`GenerativeAiEngine`… инварианты `LlmCommandPlanner` переносятся требованием и тестами… замер
транспорта — задача внутри 3a… харнесс в гейт не входит никогда»); plan §0.2 B-7, §0.3. **Modules:**
`:data:ai-cloud`, `:app` (build file and an excluded test), `tools/`.

**Files:**
- Create: `data/ai-cloud/src/main/java/com/sidr/launcher/data/aicloud/OpenAiCompatibleBridgeTransport.kt`
  (with `enum class BridgeTransportMode`).
- Create: `data/ai-cloud/src/test/java/com/sidr/launcher/data/aicloud/OpenAiCompatibleBridgeTransportTest.kt` (18).
- Modify: `app/build.gradle.kts` — one `tasks.withType<Test>()` block after the existing ones.
- Create: `app/src/test/java/com/sidr/launcher/bridge/BridgeTransportMeasurement.kt` (excluded from
  every gate run — not counted).
- Create: `app/src/test/resources/bridge-measure/README.md`, `app/src/test/resources/bridge-measure/.gitignore`.
- Create: `tools/measure-bridge-transport.sh` (`chmod +x`).

**Interfaces.**
- Produces (`:data:ai-cloud`):
  ```kotlin
  enum class BridgeTransportMode { PROMPT_ONLY, RESPONSE_FORMAT, FORCED_TOOL }
  class OpenAiCompatibleBridgeTransport(
      httpClient: HttpClient, secretStore: SecureSecretStore, configRepository: AiProviderConfigRepository,
      ioDispatcher: CoroutineDispatcher, mode: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY,
  ) : BridgeTransport
  // companion: SCHEMA_NAME = "sidr_answer", TOOL_NAME = "submit_answer"
  fun bridgeHttpClient(socketTimeoutMs: Long): HttpClient   // the bridge's own client (plan review)
  ```
- Behaviour: the body is `{model, messages: [system, user (+ system retry note)], max_tokens, stream:
  false}` plus `response_format` or `tools`/`tool_choice` in the two schema modes — nothing else.

- [ ] **Step 1: write the failing tests.** `OpenAiCompatibleBridgeTransportTest.kt`:

```kotlin
package com.sidr.launcher.data.aicloud

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.security.SecretKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The bridge's transport (spec §7.12.2). `LlmCommandPlanner`'s invariants are carried over by these
 * tests, because its own tests are deleted with it (Task 11): https only, no call without a config or a
 * key, never a throw, cancellation propagates, the key only in the header. And the three forms of the
 * schema switch each send exactly what they say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatibleBridgeTransportTest {

    private val provider = AiProviderId("openai-compatible")
    private val config = AiProviderConfig(
        providerId = provider,
        baseUrl = "https://api.example.com/v1",
        modelId = AiModelId("fixture-model"),
    )
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val schema = """{"type":"object","additionalProperties":false,"required":["kind"],"properties":{"kind":{"type":"string"}}}"""
    private val request = BridgeRequest(
        system = "FIXTURE-SYSTEM-TEXT",
        schemaJson = schema,
        command = "открой lumora",
        retryNote = null,
        maxOutputTokens = 512,
    )

    private val bodies = mutableListOf<String>()
    private val sentHeaders = mutableListOf<Headers>()

    private fun completion(content: String, finish: String = "stop", usage: String = "") =
        """{"choices":[{"message":{"content":${Json.encodeToString(String.serializer(), content)}},"finish_reason":"$finish"}]$usage}"""

    private fun engine(
        body: String = completion(DECLINE),
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = jsonHeaders,
    ) = MockEngine { sent ->
        bodies += (sent.body as TextContent).text
        sentHeaders += sent.headers
        respond(body, status, headers)
    }

    private fun seededStore() =
        FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), "sk-SNTL-KEY") } }

    private fun transport(
        engine: MockEngine = engine(),
        mode: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY,
        store: FakeSecureSecretStore = seededStore(),
        cfg: AiProviderConfig? = config,
    ) = OpenAiCompatibleBridgeTransport(HttpClient(engine), store, FakeAiProviderConfigRepository(cfg), Dispatchers.Unconfined, mode)

    private fun sentJson(): JsonObject = Json.parseToJsonElement(bodies.single()).jsonObject

    @Test
    fun `a JSON object answer is read into its fields`() = runTest {
        val content = """{"kind":"plan","step":{"tool":"launch_app","args":{"app":"lumora","url":null}},"question":null,"destination":null}"""

        val reply = transport(engine(completion(content))).ask(request)

        assertEquals(
            TransportReply.Answer(
                RawAnswer("plan", RawStep("launch_app", mapOf("app" to "lumora", "url" to null)), null, null),
                ReplyMeta("stop", null, null),
            ),
            reply,
        )
    }

    @Test
    fun `an answer inside a code fence is read`() = runTest {
        val reply = transport(engine(completion("```json\n$DECLINE\n```"))).ask(request)

        assertEquals("decline", ((reply as TransportReply.Answer).answer.kind))
    }

    @Test
    fun `prose is unparseable, never a guess`() = runTest {
        val reply = transport(engine(completion("I think you want to open Lumora."))).ask(request)

        assertTrue(reply is TransportReply.Unparseable)
    }

    /** Review Focus 4: some upstream failures arrive as HTTP 200 with an error object. */
    @Test
    fun `a 200 that carries an error object is unparseable`() = runTest {
        val reply = transport(engine("""{"error":{"message":"upstream failure","code":502}}""")).ask(request)

        assertEquals(TransportReply.Unparseable(ReplyMeta.NONE), reply)
    }

    @Test
    fun `no active config is a credentials failure and makes no call`() = runTest {
        val reply = transport(cfg = null).ask(request)

        assertEquals(TransportReply.Failed(AiError.MissingCredentials), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `a missing key is a credentials failure and makes no call`() = runTest {
        val reply = transport(store = FakeSecureSecretStore()).ask(request)

        assertEquals(TransportReply.Failed(AiError.MissingCredentials), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `a non-https base url is refused and makes no call`() = runTest {
        val reply = transport(cfg = config.copy(baseUrl = "http://api.example.com/v1")).ask(request)

        assertEquals(TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https")), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `401 is a credentials failure`() = runTest {
        val reply = transport(engine("{}", HttpStatusCode.Unauthorized)).ask(request)

        assertEquals(TransportReply.Failed(AiError.Unauthorized), reply)
    }

    @Test
    fun `429 is rate limited, with the provider's hint`() = runTest {
        val headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("7"))

        val reply = transport(engine("{}", HttpStatusCode.TooManyRequests, headers)).ask(request)

        assertEquals(TransportReply.Failed(AiError.RateLimited(7_000)), reply)
    }

    @Test
    fun `500 is a server error`() = runTest {
        val reply = transport(engine("{}", HttpStatusCode.InternalServerError)).ask(request)

        assertEquals(TransportReply.Failed(AiError.ServerError(500)), reply)
    }

    @Test
    fun `a network exception is a network failure, never a throw`() = runTest {
        val failing = MockEngine { throw java.io.IOException("boom") }

        val reply = transport(failing).ask(request)

        assertEquals(TransportReply.Failed(AiError.Network(detail = "IOException")), reply)

        // The net covers the whole exchange, not only the call: a schema that does not parse fails in
        // encoding, before any socket, and is a failure — not a throw, and not "no network".
        val broken = transport(mode = BridgeTransportMode.RESPONSE_FORMAT).ask(request.copy(schemaJson = "{"))
        assertTrue("$broken", broken is TransportReply.Failed && broken.error is AiError.Unknown)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val cancelled = MockEngine { throw CancellationException("cancelled by the user") }

        try {
            transport(cancelled).ask(request)
            fail("a CancellationException must propagate")
        } catch (expected: CancellationException) {
            // the user's «Отмена» must reach the caller, not become a Failed reply
        }
    }

    @Test
    fun `prompt mode sends the system text and the command, and nothing else`() = runTest {
        transport().ask(request)

        val sent = sentJson()
        assertEquals(setOf("model", "messages", "max_tokens", "stream"), sent.keys)
        assertEquals("fixture-model", sent.getValue("model").jsonPrimitive.content)
        assertEquals(
            listOf("system" to "FIXTURE-SYSTEM-TEXT", "user" to "открой lumora"),
            sent.getValue("messages").jsonArray.map {
                it.jsonObject.getValue("role").jsonPrimitive.content to it.jsonObject.getValue("content").jsonPrimitive.content
            },
        )
        assertEquals("512", sent.getValue("max_tokens").jsonPrimitive.content)
        assertEquals("false", sent.getValue("stream").jsonPrimitive.content)
    }

    @Test
    fun `response-format mode adds the schema as a strict json_schema and nothing more`() = runTest {
        transport(mode = BridgeTransportMode.RESPONSE_FORMAT).ask(request)

        val sent = sentJson()
        assertEquals(setOf("model", "messages", "max_tokens", "stream", "response_format"), sent.keys)
        val format = sent.getValue("response_format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        val jsonSchema = format.getValue("json_schema").jsonObject
        assertEquals("sidr_answer", jsonSchema.getValue("name").jsonPrimitive.content)
        assertEquals(JsonPrimitive(true), jsonSchema.getValue("strict"))
        assertEquals(Json.parseToJsonElement(schema), jsonSchema.getValue("schema"))
    }

    @Test
    fun `forced-tool mode sends the schema as the only tool and reads the answer from its call`() = runTest {
        val toolCall = """{"choices":[{"message":{"tool_calls":[{"type":"function","function":{"name":"submit_answer","arguments":${Json.encodeToString(String.serializer(), DECLINE)}}}]},"finish_reason":"tool_calls"}]}"""

        val reply = transport(engine(toolCall), mode = BridgeTransportMode.FORCED_TOOL).ask(request)

        assertEquals("decline", (reply as TransportReply.Answer).answer.kind)
        val sent = sentJson()
        val function = (sent.getValue("tools") as JsonArray).single().jsonObject.getValue("function").jsonObject
        assertEquals("submit_answer", function.getValue("name").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(schema), function.getValue("parameters"))
        assertEquals(
            "submit_answer",
            sent.getValue("tool_choice").jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content,
        )
    }

    @Test
    fun `a retry adds only the static note after the command`() = runTest {
        transport().ask(request.copy(retryNote = "FIXTURE-RETRY-NOTE"))

        assertEquals(
            listOf("system" to "FIXTURE-SYSTEM-TEXT", "user" to "открой lumora", "system" to "FIXTURE-RETRY-NOTE"),
            sentJson().getValue("messages").jsonArray.map {
                it.jsonObject.getValue("role").jsonPrimitive.content to it.jsonObject.getValue("content").jsonPrimitive.content
            },
        )
    }

    /** `DOC-AML-3`, carried over: the key is in the Authorization header and nowhere else. */
    @Test
    fun `the key is sent only in the header and never in the body or a failure`() = runTest {
        transport().ask(request)
        val refused = transport(engine("{}", HttpStatusCode.Unauthorized)).ask(request)

        assertEquals("Bearer sk-SNTL-KEY", sentHeaders.first()[HttpHeaders.Authorization])
        assertFalse(bodies.any { it.contains("sk-SNTL-KEY") })
        assertFalse(refused.toString().contains("sk-SNTL-KEY"))
    }

    @Test
    fun `the reply's meta carries the finish reason and the reasoning tokens when the provider reports them`() = runTest {
        val usage = ""","usage":{"completion_tokens":321,"completion_tokens_details":{"reasoning_tokens":200}}"""

        val reply = transport(engine(completion(DECLINE, finish = "length", usage = usage))).ask(request)

        assertEquals(ReplyMeta("length", 321, 200), (reply as TransportReply.Answer).meta)
    }

    private companion object {
        const val DECLINE = """{"kind":"decline","step":null,"question":null,"destination":null}"""
    }
}
```

- [ ] **Step 2: run — expect compile-red.** `tools/gate.sh --scoped :data:ai-cloud:testDebugUnitTest` →
  `Unresolved reference 'OpenAiCompatibleBridgeTransport'`.

- [ ] **Step 3: implement** `OpenAiCompatibleBridgeTransport.kt`:

```kotlin
package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * How the answer's shape reaches the provider (D2, b′; spec §7.12.2). The shape is the same in all
 * three — strict-compatible, generated with the prompt — and only the CHANNEL differs:
 *  - [PROMPT_ONLY] — the prompt describes it, nothing else is sent (the default until the measurement);
 *  - [RESPONSE_FORMAT] — `response_format: json_schema, strict`;
 *  - [FORCED_TOOL] — one tool whose parameters are the schema, forced by `tool_choice`.
 * The safety of the bridge does not rest on this choice: the validator judges every answer the same.
 */
enum class BridgeTransportMode { PROMPT_ONLY, RESPONSE_FORMAT, FORCED_TOOL }

/**
 * The bridge's own HTTP client — not the shared cloud one. That client's 30 s socket timeout
 * (`AiCloudProvidesModule`) would cut a non-streaming reasoning model's silent wait before the bridge's
 * ceiling, and the spec names the ceiling, not the socket, as the bound (§7.12.2). [socketTimeoutMs] is
 * therefore the ceiling in production (`BridgeProvidesModule`) and the measurement's cap in the harness.
 */
fun bridgeHttpClient(socketTimeoutMs: Long): HttpClient = HttpClient(Android) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = socketTimeoutMs
    }
}

/**
 * The bridge's [BridgeTransport] over any OpenAI-compatible `chat/completions` endpoint — its OWN
 * non-streaming path, not `GenerativeAiEngine` (merging the assistant's port with the planner's is
 * change-control §5). It carries `LlmCommandPlanner`'s invariants, by requirement and by
 * `OpenAiCompatibleBridgeTransportTest`, since that class and its tests are deleted (Task 11):
 *  - `https://` only; with no active config or no key — **not one network call**;
 *  - never throws — the WHOLE exchange (config, key, encoding, call, decoding) is inside one net, not
 *    only the call; `CancellationException` is rethrown, so the user's «Отмена» cancels the request;
 *  - the key comes only from [SecureSecretStore], goes only into the `Authorization` header, and is
 *    never logged nor put in a reply (`DOC-AML-3`).
 * The body is exactly the [BridgeRequest]'s parts in the mode's form; this class adds nothing of the
 * device. Errors use the assistant engine's vocabulary ([AiError]) so the two surfaces stop disagreeing.
 */
class OpenAiCompatibleBridgeTransport(
    private val httpClient: HttpClient,
    private val secretStore: SecureSecretStore,
    private val configRepository: AiProviderConfigRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val mode: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY,
) : BridgeTransport {

    override suspend fun ask(request: BridgeRequest): TransportReply = withContext(ioDispatcher) {
        try {
            exchange(request)
        } catch (e: CancellationException) {
            throw e // never swallow the user's cancel
        } catch (e: Exception) {
            // A failure of the network is a network failure; anything else — a config flow that throws,
            // a schema that does not parse — is not the network, and saying "no network" would send the
            // user to the wrong place.
            val name = e::class.simpleName.orEmpty()
            TransportReply.Failed(
                when {
                    name.contains("Timeout") -> AiError.Timeout
                    e is java.io.IOException -> AiError.Network(detail = name)
                    else -> AiError.Unknown(detail = name)
                },
            )
        }
    }

    private suspend fun exchange(request: BridgeRequest): TransportReply {
        val config = configRepository.activeConfig().first()
            ?: return TransportReply.Failed(AiError.MissingCredentials)
        val baseUrl = config.baseUrl.trim()
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            return TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https"))
        }
        val key = when (val stored = secretStore.get(SecretKeys.apiKey(config.providerId))) {
            is OperationResult.Success -> stored.value?.trim()?.takeIf { it.isNotEmpty() }
            is OperationResult.Failure -> null
        } ?: return TransportReply.Failed(AiError.MissingCredentials)

        val body = encode(request, config.modelId.value.trim())
        val response = httpClient.post(baseUrl.removeSuffix("/") + "/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            return TransportReply.Failed(statusError(response.status.value, response.headers[HttpHeaders.RetryAfter]))
        }
        return decode(response.bodyAsText())
    }

    private fun encode(request: BridgeRequest, model: String): String = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", request.system)
            }
            addJsonObject {
                put("role", "user")
                put("content", request.command)
            }
            request.retryNote?.let { note -> addJsonObject { put("role", "system"); put("content", note) } }
        }
        put("max_tokens", request.maxOutputTokens)
        put("stream", false)
        when (mode) {
            BridgeTransportMode.PROMPT_ONLY -> Unit
            BridgeTransportMode.RESPONSE_FORMAT -> putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", SCHEMA_NAME)
                    put("strict", true)
                    put("schema", Json.parseToJsonElement(request.schemaJson))
                }
            }
            BridgeTransportMode.FORCED_TOOL -> {
                putJsonArray("tools") {
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", TOOL_NAME)
                            put("description", TOOL_DESCRIPTION)
                            put("strict", true)
                            put("parameters", Json.parseToJsonElement(request.schemaJson))
                        }
                    }
                }
                putJsonObject("tool_choice") {
                    put("type", "function")
                    putJsonObject("function") { put("name", TOOL_NAME) }
                }
            }
        }
    }.toString()

    private fun decode(bodyText: String): TransportReply {
        val root = runCatching { strict.parseToJsonElement(bodyText).jsonObject }.getOrNull()
            ?: return TransportReply.Unparseable(ReplyMeta.NONE)
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val usage = root["usage"] as? JsonObject
        val meta = ReplyMeta(
            finishReason = (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull,
            completionTokens = (usage?.get("completion_tokens") as? JsonPrimitive)?.intOrNull,
            reasoningTokens = ((usage?.get("completion_tokens_details") as? JsonObject)
                ?.get("reasoning_tokens") as? JsonPrimitive)?.intOrNull,
        )
        val carrier = when (mode) {
            BridgeTransportMode.FORCED_TOOL -> (((message?.get("tool_calls") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("function") as? JsonObject)?.get("arguments") as? JsonPrimitive
            BridgeTransportMode.PROMPT_ONLY, BridgeTransportMode.RESPONSE_FORMAT -> message?.get("content") as? JsonPrimitive
        }
        val text = carrier?.contentOrNull ?: return TransportReply.Unparseable(meta)
        return parseAnswer(text)?.let { TransportReply.Answer(it, meta) } ?: TransportReply.Unparseable(meta)
    }

    /** Strict: after trimming and at most one code fence, the text must be ONE JSON object. */
    private fun parseAnswer(text: String): RawAnswer? = runCatching {
        val obj = strict.parseToJsonElement(unfenced(text.trim())).jsonObject
        val step = when (val raw = obj["step"]) {
            null, JsonNull -> null
            is JsonObject -> RawStep(
                tool = (raw["tool"] as? JsonPrimitive)?.contentOrNull,
                args = (raw["args"] as? JsonObject)?.mapValues { (_, value) -> argValue(value) } ?: emptyMap(),
            )
            else -> return null
        }
        RawAnswer(
            kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull,
            step = step,
            question = (obj["question"] as? JsonPrimitive)?.contentOrNull,
            destination = (obj["destination"] as? JsonPrimitive)?.contentOrNull,
        )
    }.getOrNull()

    /** A string, a number or a boolean is read as text; `null` is absent; anything else is unreadable. */
    private fun argValue(value: JsonElement): String? = when (value) {
        JsonNull -> null
        is JsonPrimitive -> value.content
        else -> throw IllegalArgumentException("an argument must be a value or null")
    }

    private fun unfenced(text: String): String {
        if (!text.startsWith("```")) return text
        return text.substringAfter('\n', missingDelimiterValue = "").substringBeforeLast("```").trim()
    }

    private fun statusError(code: Int, retryAfter: String?): AiError = when {
        code == 401 || code == 403 -> AiError.Unauthorized
        code == 429 -> AiError.RateLimited(retryAfter?.trim()?.toLongOrNull()?.times(1_000))
        code in 500..599 -> AiError.ServerError(code)
        code in 400..499 -> AiError.InvalidRequest(detail = "http_$code")
        else -> AiError.Unknown(detail = "http_$code")
    }

    companion object {
        const val SCHEMA_NAME = "sidr_answer"
        const val TOOL_NAME = "submit_answer"
        const val TOOL_DESCRIPTION = "Submit your one answer in the required form."

        private val strict = Json { isLenient = false }
    }
}
```

- [ ] **Step 4: run — expect green.** `tools/gate.sh --scoped :data:ai-cloud:testDebugUnitTest` →
  `ai-cloud tests=50` (32 + 18).

- [ ] **Step 5: the measurement harness — machinery, so proved by running it, not by a mutation.**

Append to `app/build.gradle.kts`, after the last `tasks.withType<Test>()` block:

```kotlin
// A4′ phase 3a (plan §0.2 B-7, probed in §0.3): the transport measurement is PAID and NETWORKED, so it
// never runs in a gate. Every Test task of :app excludes it unless the run names the property, which
// tools/gate.sh never does and tools/measure-bridge-transport.sh does. Excluded = not executed and not
// counted, so the gate's floor and its per-module counts are untouched.
tasks.withType<Test>().configureEach {
    if (!providers.gradleProperty("sidr.measureBridgeTransport").isPresent) {
        filter { excludeTestsMatching("*.BridgeTransportMeasurement") }
    } else {
        systemProperty("sidr.measureModels", providers.gradleProperty("sidr.measureModels").getOrElse(""))
        systemProperty("sidr.measureDryRun", providers.gradleProperty("sidr.measureDryRun").isPresent.toString())
        testLogging.showStandardStreams = true
    }
}
```

Create `app/src/test/resources/bridge-measure/.gitignore`:

```
corpus.tsv
models.txt
report*
```

Create `app/src/test/resources/bridge-measure/README.md`:

```markdown
# Bridge transport measurement — inputs (git-ignored)

A4′ phase 3a, spec §7.12.2: which transport form becomes the default, and what the budget numbers are.
Paid and networked — it never runs in `tools/gate.sh`. The owner runs it:

    SIDR_MEASURE_API_KEY=... tools/measure-bridge-transport.sh <model>[,<model>...]
    SIDR_MEASURE_API_KEY=... tools/measure-bridge-transport.sh <model> --dry-run   # no network

- `corpus.tsv` — one command per line: `<label><TAB><command>`; `#` starts a comment. Labels:
  `act` (should become a plan), `question` (should go to the assistant), `nav` (a Sidr screen),
  `other`. The B15 corpus is a good start; the labels are the owner's.
- The base URL defaults to `https://openrouter.ai/api/v1`; override with `SIDR_MEASURE_BASE_URL`.
- The key is read from `SIDR_MEASURE_API_KEY` and is never printed, logged or written.

Output: `BRIDGE-MEASURE ::` lines in `build/bridge-measure.log`, and the full per-line report in
`app/build/bridge-measure/report.txt` — for the owner to judge each reading by hand.
```

Create `tools/measure-bridge-transport.sh` (and `chmod +x`):

```bash
#!/usr/bin/env bash
# The transport measurement of A4′ phase 3a (spec §7.12.2; plan §0.2 B-7, Task 6). PAID and NETWORKED:
# it never runs inside tools/gate.sh. The owner runs it with the key in SIDR_MEASURE_API_KEY:
#   tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]
# Neither this script nor the harness echoes, logs or writes the key.
set -uo pipefail
cd "$(dirname "$0")/.."
if [ -z "${SIDR_MEASURE_API_KEY:-}" ]; then echo "FATAL: SIDR_MEASURE_API_KEY is not set"; exit 2; fi
MODELS="${1:-}"
if [ -z "$MODELS" ]; then echo "usage: tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]"; exit 2; fi
CORPUS=app/src/test/resources/bridge-measure/corpus.tsv
if [ ! -f "$CORPUS" ]; then echo "FATAL: $CORPUS is missing — see app/src/test/resources/bridge-measure/README.md"; exit 2; fi
JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
EXTRA=()
if [ "${2:-}" = "--dry-run" ]; then EXTRA+=("-Psidr.measureDryRun"); fi
mkdir -p build
LOG=build/bridge-measure.log
./gradlew "-Porg.gradle.java.installations.paths=$JDK17" --console=plain --no-build-cache --rerun-tasks \
  :app:testDebugUnitTest --tests '*BridgeTransportMeasurement' \
  -Psidr.measureBridgeTransport "-Psidr.measureModels=$MODELS" ${EXTRA[@]+"${EXTRA[@]}"} -i > "$LOG" 2>&1
STATUS=$?
grep -h 'BRIDGE-MEASURE ::' "$LOG" || true
echo "exit=$STATUS log=$LOG report=app/build/bridge-measure/report.txt"
exit $STATUS
```

Create `app/src/test/java/com/sidr/launcher/bridge/BridgeTransportMeasurement.kt`:

```kotlin
package com.sidr.launcher.bridge

import com.sidr.launcher.agent.ProductionGraph
import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.data.aicloud.bridgeHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * The transport measurement (spec §7.12.2; plan Task 6, applied in Task 12). **Never in a gate:**
 * `app/build.gradle.kts` excludes this class from every Test task unless the run passes
 * `-Psidr.measureBridgeTransport` (probed, plan §0.3). Real production offering and descriptions, the
 * real `BridgeModelPlanner`, the real transport over the engine `:app` ships; only the config and the
 * key store are fakes, filled from the environment.
 *
 * Per (model × mode × corpus line) it records the reading, the attempts, the wall time and what the
 * provider reported (finish reason, completion and reasoning tokens); per (model × mode) it summarises
 * the share parsed on the first try, the share usable after at most one retry, retries, timeouts, the
 * median and p90 time, truncations, whether hidden reasoning was reported, and how many `question`
 * lines went to the assistant against how many became a web search. Correctness is the owner's to
 * judge from the per-line report — nothing here decides it.
 */
class BridgeTransportMeasurement {

    private class Recording(private val inner: BridgeTransport) : BridgeTransport {
        val replies = mutableListOf<TransportReply>()
        override suspend fun ask(request: BridgeRequest): TransportReply = inner.ask(request).also { replies += it }
    }

    private data class Row(
        val model: String,
        val mode: BridgeTransportMode,
        val label: String,
        val command: String,
        val result: ModelPlanning,
        val replies: List<TransportReply>,
        val ms: Long,
    )

    @Test
    fun measure() = runBlocking {
        val key = System.getenv("SIDR_MEASURE_API_KEY").orEmpty()
        val models = System.getProperty("sidr.measureModels").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val baseUrl = System.getenv("SIDR_MEASURE_BASE_URL")?.takeIf { it.isNotBlank() } ?: "https://openrouter.ai/api/v1"
        val corpus = File("src/test/resources/bridge-measure/corpus.tsv").takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") && '\t' in it }
            ?.map { it.substringBefore('\t').trim() to it.substringAfter('\t').trim() }
            .orEmpty()
        check(key.isNotEmpty()) { "BRIDGE-MEASURE :: refused - SIDR_MEASURE_API_KEY did not reach the test JVM" }
        check(corpus.isNotEmpty()) { "BRIDGE-MEASURE :: refused - no corpus lines in src/test/resources/bridge-measure/corpus.tsv" }
        check(models.isNotEmpty()) { "BRIDGE-MEASURE :: refused - pass -Psidr.measureModels=a,b" }

        val registry = ProductionGraph.federation().registry
        val descriptions = AuthoredToolDescriptions()
        val offered = BridgeFilter.offeringOf(registry, descriptions).tools.size
        if (System.getProperty("sidr.measureDryRun") == "true") {
            println("BRIDGE-MEASURE :: dry-run key=present corpus=${corpus.size} models=$models modes=${BridgeTransportMode.entries} offering=$offered")
            return@runBlocking
        }

        // The cap of Task 12 rule 2, not the provisional ceiling: the measurement must be able to SEE a
        // latency the rule could still accept, or it can never raise the ceiling it exists to set.
        val client = bridgeHttpClient(socketTimeoutMs = 90_000)
        val provider = AiProviderId("measure")
        val store = FakeSecureSecretStore().apply { put(SecretKeys.apiKey(provider), key) }
        val report = StringBuilder()
        fun emit(line: String) {
            println(line)
            report.appendLine(line)
        }

        models.forEach { model ->
            val config = FakeAiProviderConfigRepository(AiProviderConfig(provider, baseUrl, AiModelId(model)))
            BridgeTransportMode.entries.forEach { mode ->
                val rows = corpus.map { (label, command) ->
                    val recording = Recording(OpenAiCompatibleBridgeTransport(client, store, config, Dispatchers.IO, mode))
                    val started = System.nanoTime()
                    val result = BridgeModelPlanner(registry, descriptions, recording).plan(command)
                    Row(model, mode, label, command, result, recording.replies.toList(), (System.nanoTime() - started) / 1_000_000)
                }
                rows.forEach { emit(lineOf(it)) }
                emit(summaryOf(model, mode, rows))
            }
        }
        client.close()
        File("build/bridge-measure").mkdirs()
        File("build/bridge-measure/report.txt").writeText(report.toString())
    }

    private fun kindOf(result: ModelPlanning): String = when (result) {
        is ModelPlanning.Proposed -> "plan"
        is ModelPlanning.ToAssistant -> "assistant"
        is ModelPlanning.Navigate -> "navigate:${result.destination.wire}"
        is ModelPlanning.Clarify -> "clarify"
        ModelPlanning.Declined -> "decline"
        is ModelPlanning.Unavailable -> "unavailable:${result.reason}"
    }

    private fun lineOf(row: Row): String {
        val step = (row.result as? ModelPlanning.Proposed)?.step
        val meta = row.replies.lastOrNull()?.let { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta }
        return "BRIDGE-MEASURE :: line model=${row.model} mode=${row.mode} label=${row.label} kind=${kindOf(row.result)} " +
            "tool=${step?.tool?.value ?: "-"} args=${step?.args ?: "-"} attempts=${row.replies.size} ms=${row.ms} " +
            "finish=${meta?.finishReason ?: "-"} completion_tokens=${meta?.completionTokens ?: "-"} " +
            "reasoning_tokens=${meta?.reasoningTokens ?: "-"} command=${row.command}"
    }

    private fun summaryOf(model: String, mode: BridgeTransportMode, rows: List<Row>): String {
        val n = rows.size
        val parsedFirst = rows.count { it.replies.firstOrNull() is TransportReply.Answer }
        val usable = rows.count { it.result !is ModelPlanning.Unavailable }
        val retried = rows.count { it.replies.size == 2 }
        val timedOut = rows.count { (it.result as? ModelPlanning.Unavailable)?.reason?.name == "TIMED_OUT" }
        val sorted = rows.map { it.ms }.sorted()
        val median = sorted[n / 2]
        val p90 = sorted[((n * 9) / 10).coerceAtMost(n - 1)]
        val metas = rows.flatMap { row -> row.replies.mapNotNull { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta } }
        val truncated = metas.count { it.finishReason == "length" }
        val reasoning = metas.count { (it.reasoningTokens ?: 0) > 0 }
        val questions = rows.filter { it.label == "question" }
        val toAssistant = questions.count { it.result is ModelPlanning.ToAssistant }
        val toSearch = questions.count { (it.result as? ModelPlanning.Proposed)?.step?.tool?.value == "web_search" }
        return "BRIDGE-MEASURE :: summary model=$model mode=$mode n=$n parsed_first=$parsedFirst/$n usable=$usable/$n " +
            "retried=$retried timed_out=$timedOut median_ms=$median p90_ms=$p90 truncated=$truncated " +
            "reasoning_reported=$reasoning questions_to_assistant=$toAssistant/${questions.size} " +
            "questions_to_web_search=$toSearch/${questions.size}"
    }
}
```

Then run the machinery, in this order, and record each result in the task report:

1. **Gate-shaped, the class must not run:** `tools/gate.sh --scoped :app:testDebugUnitTest` → `app
   tests=68`, and `ls app/build/test-results/testDebugUnitTest/ | grep -c BridgeTransportMeasurement`
   → `0`.
2. **Named without the switch, it must refuse:** `./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10
   --console=plain :app:testDebugUnitTest --tests '*BridgeTransportMeasurement'` → `No tests found for
   given includes`, non-zero exit.
3. **The wrapper refuses without a key:** `env -u SIDR_MEASURE_API_KEY tools/measure-bridge-transport.sh
   x` → `FATAL: SIDR_MEASURE_API_KEY is not set`, exit 2, no Gradle run.
4. **A dry run proves the property path, the key reaching the test JVM, the corpus and the offering —
   with no network:** write a two-line `app/src/test/resources/bridge-measure/corpus.tsv`
   (`act<TAB>открой lumora`, `question<TAB>почему небо синее`), run
   `SIDR_MEASURE_API_KEY=dry-run-not-a-key tools/measure-bridge-transport.sh fixture-model --dry-run` →
   expected one line `BRIDGE-MEASURE :: dry-run key=present corpus=2 models=[fixture-model]
   modes=[PROMPT_ONLY, RESPONSE_FORMAT, FORCED_TOOL] offering=19`, exit 0; then delete the corpus file
   (`git status` must not show it — the `.gitignore` above).
   - **If it prints `refused - SIDR_MEASURE_API_KEY did not reach the test JVM`**, the daemon did not pass
     the environment to the test worker. Then add, inside the `else` branch of the Gradle block,
     `environment("SIDR_MEASURE_API_KEY", System.getenv("SIDR_MEASURE_API_KEY").orEmpty())`, re-run 1–4,
     and record the finding.

- [ ] **Step 6: mutations — five.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M6a | `OpenAiCompatibleBridgeTransport.kt` · `            BridgeTransportMode.PROMPT_ONLY -> Unit\n            BridgeTransportMode.RESPONSE_FORMAT -> putJsonObject("response_format") {` → `            BridgeTransportMode.PROMPT_ONLY, BridgeTransportMode.RESPONSE_FORMAT -> putJsonObject("response_format") {` | the prompt form sends no schema | `:data:ai-cloud` — `OpenAiCompatibleBridgeTransportTest > prompt mode sends the system text and the command, and nothing else` **only** (the retry test reads only `messages`) |
| M6b | `OpenAiCompatibleBridgeTransport.kt` · `?.get("function") as? JsonObject)?.get("arguments") as? JsonPrimitive` → `?.get("function") as? JsonObject)?.get("name") as? JsonPrimitive` | the forced tool's answer is its arguments | `:data:ai-cloud` — `> forced-tool mode sends the schema as the only tool and reads the answer from its call` **only** |
| M6c | `OpenAiCompatibleBridgeTransport.kt` · `val body = encode(request, config.modelId.value.trim())` → `val body = encode(request, config.modelId.value.trim() + key)` | the key never enters the body | `:data:ai-cloud` — `> the key is sent only in the header and never in the body or a failure`, `> prompt mode sends the system text and the command, and nothing else` |
| M6d | `OpenAiCompatibleBridgeTransport.kt` · `is OperationResult.Success -> stored.value?.trim()?.takeIf { it.isNotEmpty() }` → `is OperationResult.Success -> stored.value?.trim().orEmpty()` | no key, no call | `:data:ai-cloud` — `> a missing key is a credentials failure and makes no call` **only** |
| M6e | `OpenAiCompatibleBridgeTransport.kt` · `            request.retryNote?.let { note -> addJsonObject { put("role", "system"); put("content", note) } }\n` → `` | the retry's note is sent | `:data:ai-cloud` — `> a retry adds only the static note after the command` **only** |

> **Corrected in execution (2026-10-04) — the code blocks above are the record of what was planned; the tree
> differs where this note says.** Measured total **1636** (the +3 carried from T4/T5 fix rounds, +1 here).
> - **E6-R1 (review, Important):** the harness built `BridgeModelPlanner` with `BridgeBudget.Default` (45 s), so
>   the planner's ceiling cut every wait the 90 s socket was meant to observe and rule 2 of Task 12 would have
>   read a truncated p90. The harness passes `MEASURE_CEILING_MS = 180_000` (two attempts × the 90 s socket;
>   the ceiling covers the retry); the dry-run line gains `ceiling=180000`. p90 is nearest-rank
>   (`sorted[(9n+9)/10 - 1]`), not `sorted[9n/10]` (the maximum for n ≤ 10).
> - **E6-R2 (review, Important):** the script's dry-run guard depended on argument position (`--dry-run m1` ran
>   LIVE with model id `--dry-run`). It now parses strictly before the key check: one positional of printable
>   ASCII with no whitespace, no empty comma segment and no segment starting with `-`, optionally followed by
>   exactly `--dry-run`; anything else → usage, exit 2, no Gradle (twelve shapes run). Gradle runs with
>   `--no-daemon` and `-Pkotlin.compiler.execution.strategy=in-process` (no daemon keeps the key); the README
>   reads the key with `read -rs`.
> - **E6-F2:** new test `a request that times out is reported as a timeout, not as no network` (Ktor 3.0.1's
>   Android engine raises `ConnectTimeoutException` on the socket timeout — read in the jar); probe P2
>   (timeout → `Network`) reddens exactly it. **E6-F3:** the cancellation rethrow is held by `cancellation is
>   not swallowed` (probe P1 → RED) — row M6f in spirit, run, not reasoned.
> - Untested and deferred: 403, other 4xx, odd statuses, nested argument values.

- [ ] **Step 7: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1632`: the other
  eight **334** (`:data:ai-cloud` 32 → **50**); `:app` **68** (the harness is not counted).

- [ ] **Step 8: commit (controller).**

```bash
git add data/ai-cloud/src app/build.gradle.kts app/src/test tools/measure-bridge-transport.sh
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): транспорт моста и замер, выбирающий его умолчание

Спека §7.12.2 (D2, b′). Свой непотоковый путь, не GenerativeAiEngine; три
формы схемы — промпт / response_format / форсированный инструмент — флагом,
умолчание до замера — промпт. Инварианты LlmCommandPlanner перенесены тестами:
только https, без конфигурации или ключа — ни одного вызова, не бросает,
отмена проходит, ключ только в заголовке. Ответ 200 с объектом ошибки —
нечитаем. Харнесс замера исключён из каждого прогона тестов :app, пока не
передан -Psidr.measureBridgeTransport (проверено зондом §0.3 и четырьмя
прогонами задачи); ключ — из SIDR_MEASURE_API_KEY, не печатается.

Мутации M6a–M6e.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: binding on the device — the binder, the interpretation, `MODEL_PROPOSED`

**Spec:** §7.12.1 («привязка пишет пакет в аргумент-сущность, а упоминание — в аргумент-подпись… исход
привязки никогда не уходит модели»), §7.12.3 condition 2, condition 4's first half (the rationale);
`DOC-ADL-1`'s new anchor (§7.12.3); criterion 17 (binder half). **Modules:** `:domain`,
`:data:repository`, `:core:testing`.

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/bridge/Interpretation.kt`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt` — `StepRationale`
  (`:16-23`) and one extension after `ExecutionPlan`.
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt`
  — `readRationale` (`:428-433`).
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/DeviceMentionBinder.kt`.
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeMentionBinder.kt`.
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/bridge/InterpretationTest.kt` (3),
  `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt` (+1),
  `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/DeviceMentionBinderTest.kt` (10).
  `AgentSessionMappersTest > every StepRationale round-trips` iterates `entries`: it covers the new value
  with no edit, and goes red until the mapper learns it.

**Interfaces.**
- Produces:
  ```kotlin
  // :domain, com.sidr.launcher.domain.ai.bridge
  sealed interface Interpretation {
      data class Act(val goal: AgentGoal, val step: PlanStep, val origins: Map<String, ValueOrigin>,
                     val effect: ToolEffect, val choice: AppChoice?) : Interpretation
      data class Navigate(val destination: SidrDestination) : Interpretation
  }
  data class AppChoice(val arg: String, val mention: String, val candidates: List<InstalledApp>,
                       val learning: ResolutionLearningToken?)
  fun Interpretation.Act.choose(packageName: String): Interpretation.Act?
  fun Interpretation.Act.plan(): ExecutionPlan?
  interface MentionBinder { suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding }
  sealed interface Binding { Bound(interpretation: Interpretation.Act) · NotFound(mention: String) · Unavailable }
  // :domain, com.sidr.launcher.domain.agent
  enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION, MODEL_PROPOSED }
  fun ExecutionPlan.isModelProposed(): Boolean
  // :data:repository
  class DeviceMentionBinder @Inject constructor(appTargets: AppTargetResolver, sorts: ToolArgumentSorts,
      preferences: ResolutionPreferenceStore, policy: ResolutionPreferencePolicy) : MentionBinder
  // :core:testing
  class FakeMentionBinder(var binding: ((AgentGoal, ProposedStep) -> Binding)? = null) : MentionBinder  // bindCallCount
  ```
- Consumes: `ProposedStep`, `ValueOrigin`, `PrincipalId`, `SidrDestination` (Tasks 3–4);
  `AppTargetResolver.candidates` / `AppCandidates`, `launch_app`'s sort row (Task 2);
  `AuthoredToolDescriptions.MENTION_ARG` (Task 5).

- [ ] **Step 1: write the failing tests.** `InterpretationTest.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pick binds (spec §7.12.3 condition 2) — and a card with a pick still owed starts nothing. */
class InterpretationTest {

    private val pending = Interpretation.Act(
        goal = AgentGoal("удали pellix", GoalShape.Free("удали pellix")),
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolId("uninstall_app"), mapOf("app_label" to ArgSource.Literal("pellix"))),
            risk = ActionRiskLevel.CONFIRM,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = emptyMap(),
        effect = ToolEffect.EXTERNAL,
        choice = AppChoice(
            arg = "app",
            mention = "pellix",
            candidates = listOf(
                InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix"),
                InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix"),
            ),
            learning = null,
        ),
    )

    @Test
    fun `a pick writes the package into the step and leaves nothing to choose`() {
        val bound = pending.choose("net.fixture.pellix.two")!!

        assertEquals(ArgSource.Literal("net.fixture.pellix.two"), bound.step.invocation.args["app"])
        assertEquals(ValueOrigin.Ours, bound.origins["app"])
        assertNull(bound.choice)
    }

    @Test
    fun `a package that was not offered changes nothing`() {
        assertNull(pending.choose("net.fixture.lumora"))
    }

    @Test
    fun `the plan exists only once nothing is left to choose`() {
        assertNull(pending.plan())
        assertEquals(ExecutionPlan(listOf(pending.choose("net.fixture.pellix.one")!!.step)), pending.choose("net.fixture.pellix.one")!!.plan())
    }
}
```

`AgentExecutorTest` — append (it uses the file's `registry`, `budget`, and `FakeToolExecutor`; after
Task 1 `FakeToolRegistry.withA0Tools()` declares `play_store_search` `CONFIRM`):

```kotlin

    /**
     * `DOC-ADL-1`'s anchor after the router (spec §7.12.3): the same risk gets the same gate whoever
     * proposed the step. A `CONFIRM` step the MODEL proposed stops at the engine's consent exactly as a
     * deterministic one does — same state, same trace, nothing invoked.
     */
    @Test
    fun `a CONFIRM step the model proposed stops at the engine's consent exactly like a deterministic one`() = runTest {
        fun storeStep(rationale: StepRationale) = AgentSession(
            id = AgentSessionId("s-${rationale.name}"),
            goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = rationale,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
        val tools = FakeToolExecutor(emptyList())

        val deterministic = AgentExecutor(registry, tools, budget).advance(storeStep(StepRationale.GOAL_DIRECT))
        val proposed = AgentExecutor(registry, tools, budget).advance(storeStep(StepRationale.MODEL_PROPOSED))

        assertEquals(ExecutionState.AwaitingConsent, proposed.state)
        assertEquals(deterministic.state, proposed.state)
        assertEquals(deterministic.trace.events, proposed.trace.events)
        assertEquals(emptyList<Any>(), tools.invocations)
    }
```

`DeviceMentionBinderTest.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Binding on the device (spec §7.12.1, §7.12.3 condition 2): the model's words for an app become a
 * package here, before any card, or the user is asked — never a guess, and never a report to the
 * model. Learned choices answer for `launch_app` only. Fixtures are invented (R14-43).
 */
class DeviceMentionBinderTest {

    private val goal = AgentGoal("fixture command", GoalShape.Free("fixture command"))
    private val lumora = InstalledApp(packageName = "net.fixture.lumora", label = "Lumora")
    private val zantor = InstalledApp(packageName = "net.fixture.zantor", label = "Zantor")
    private val tessarOne = InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar")
    private val tessarTwo = InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar")

    private fun proposed(tool: ToolId, vararg args: Pair<String, String>, risk: ActionRiskLevel = ActionRiskLevel.SAFE) =
        ProposedStep(
            tool = tool,
            args = mapOf(*args),
            origins = args.associate { (name, _) -> name to ValueOrigin.SaidBy(PrincipalId.USER) },
            risk = risk,
            effect = ToolEffect.EXTERNAL,
        )

    private fun binder(
        apps: List<InstalledApp>,
        store: FakeResolutionPreferenceStore = FakeResolutionPreferenceStore(),
        appsFail: Boolean = false,
    ): DeviceMentionBinder {
        val repo = FakeInstalledAppsRepository().apply {
            appsToReturn = apps
            if (appsFail) errorToReturn = OperationError.UnknownError("io")
        }
        val aliases = FakeAliasStore().apply {
            runBlocking { upsert(Alias("телега", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L)) }
        }
        return DeviceMentionBinder(AppTargetResolver(repo, aliases), ToolArgumentSorts(), store, DefaultResolutionPreferencePolicy())
    }

    private fun Binding.act(): Interpretation.Act = (this as Binding.Bound).interpretation

    private fun learnedTessarTwo(streak: Int): FakeResolutionPreferenceStore {
        val candidates = CandidateSet(listOf(ResolvedTarget.App(tessarOne.packageName), ResolvedTarget.App(tessarTwo.packageName)))
        return FakeResolutionPreferenceStore().apply {
            runBlocking {
                upsert(
                    ResolutionPreference(
                        capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                        context = ResolutionContext.None,
                        preferredTarget = ResolvedTarget.App(tessarTwo.packageName),
                        evidence = PreferenceEvidence(streak = streak, totalChoices = streak, lastChosenAtEpochMs = 1L),
                        learnedInSetFingerprint = fingerprintOf(candidates),
                    ),
                )
            }
        }
    }

    @Test
    fun `a step without an app mention is bound as the model wrote it`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(InAppToolIds.WEB_SEARCH, "query" to "ostravel lectures")).act()

        assertEquals(mapOf("query" to ArgSource.Literal("ostravel lectures")), act.step.invocation.args)
        assertEquals(StepRationale.MODEL_PROPOSED, act.step.rationale)
        assertNull(act.choice)
    }

    @Test
    fun `one candidate binds the package and keeps the user's words where the tool shows them`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "lumora", risk = ActionRiskLevel.CONFIRM)).act()

        assertEquals(
            mapOf("app" to ArgSource.Literal("net.fixture.lumora"), "app_label" to ArgSource.Literal("lumora")),
            act.step.invocation.args,
        )
        assertEquals(
            mapOf("app" to ValueOrigin.Ours, "app_label" to ValueOrigin.SaidBy(PrincipalId.USER)),
            act.origins,
        )
    }

    @Test
    fun `launch_app is pinned to the package and keeps the words in query`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телега")).act()

        assertEquals(
            mapOf("query" to ArgSource.Literal("телега"), "app" to ArgSource.Literal("net.fixture.zantor")),
            act.step.invocation.args,
        )
    }

    @Test
    fun `open_app_info takes the package only`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "lumora")).act()

        assertEquals(mapOf("app" to ArgSource.Literal("net.fixture.lumora")), act.step.invocation.args)
    }

    /** Criterion 17 (binder half): two candidates are a choice BEFORE any plan, and nothing is bound yet. */
    @Test
    fun `two candidates give a choice before any plan, and nothing is bound yet`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choice!!.candidates)
        assertEquals("app", act.choice!!.arg)
        assertNull(act.step.invocation.args["app"])
    }

    /** `launch_app` keeps learned choices (the same act — a launch): a confident one binds without asking. */
    @Test
    fun `launch_app's confident learned choice binds without asking`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertNull(act.choice)
        assertEquals(ArgSource.Literal("net.fixture.tessar.two"), act.step.invocation.args["app"])
    }

    @Test
    fun `a weaker learned choice is offered first, still as a choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 1))
            .bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertEquals(listOf(tessarTwo, tessarOne), act.choice!!.candidates)
    }

    /** `AppTargetResolver`'s KDoc: consent learned for OPENING is not consent for REMOVING. */
    @Test
    fun `uninstall never reads a learned choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "tessar", risk = ActionRiskLevel.CONFIRM)).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choice!!.candidates)
        assertNull(act.choice!!.learning)
    }

    /** Review Focus 5 and the limit spec §7.12.1 names: «телегу» is not the alias «телега». */
    @Test
    fun `a word form that matches nothing is not found, never guessed`() = runTest {
        val result = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телегу"))

        assertEquals(Binding.NotFound("телегу"), result)
    }

    @Test
    fun `an app list that cannot be read is unavailable, not "not found"`() = runTest {
        val result = binder(emptyList(), appsFail = true).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "lumora"))

        assertEquals(Binding.Unavailable, result)
    }
}
```

- [ ] **Step 2: run — expect compile-red.** `:domain` → `Unresolved reference 'Interpretation'` /
  `'MODEL_PROPOSED'`; `:data:repository` → `Unresolved reference 'DeviceMentionBinder'`.

- [ ] **Step 3: implement.**

`ExecutionPlan.kt` — replace `enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION }`
and its KDoc's last paragraph with:

```kotlin
 * [COMPENSATION] (A4′ phase 2): the step undoes a step of another, finished plan. It is what marks a
 * plan as an undo — `Compensation.isUndo` — so that it offers no undo of its own and is never planned
 * again from its goal. Persisted by name in an existing TEXT column; no migration.
 *
 * [MODEL_PROPOSED] (A4′ phase 3a, spec §7.12.3 condition 4): the bridge's model planner wrote the step.
 * A paused plan made of such steps, in which nothing has acted, is never planned again from its goal by
 * the deterministic planner — it goes back through the route ([isModelProposed],
 * `ResumeAgentSessionUseCase`). Persisted by name, like [COMPENSATION]; never shown — a step's line is
 * keyed on its tool, so it needs no string.
 */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION, MODEL_PROPOSED }
```

and after `data class ExecutionPlan(…) { … }` add:

```kotlin

/** A plan the bridge's model planner wrote (A4′ phase 3a) — its steps carry [StepRationale.MODEL_PROPOSED]. */
fun ExecutionPlan.isModelProposed(): Boolean = steps.any { it.rationale == StepRationale.MODEL_PROPOSED }
```

`AgentSessionMappers.kt` — in `readRationale`, after the `COMPENSATION` line add
`        StepRationale.MODEL_PROPOSED.name -> StepRationale.MODEL_PROPOSED`.

`Interpretation.kt`:

```kotlin
package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect

/**
 * What the card shows BEFORE anything runs (spec §7.12.3 condition 1). Transient: held by the command
 * session in memory, never written anywhere (plan §0.2 B-12). Only the user's «Выполнить» turns it into
 * a session, through `StartAgentSessionUseCase.startPlanned` and the unchanged engine.
 */
sealed interface Interpretation {

    /**
     * One step over the offering, its app mention bound on this device — or awaiting the user's pick
     * ([choice] non-null). [origins] are keyed on the TOOL's argument names after binding.
     */
    data class Act(
        val goal: AgentGoal,
        val step: PlanStep,
        val origins: Map<String, ValueOrigin>,
        val effect: ToolEffect,
        val choice: AppChoice?,
    ) : Interpretation

    /** A screen of Sidr itself (plan §0.2 B-9) — opened only by «Выполнить», like everything else here. */
    data class Navigate(val destination: SidrDestination) : Interpretation
}

/**
 * The model's word for an app names more than one installed app (spec §7.12.3 condition 2). A pick
 * writes the package into [arg] — it binds; it launches nothing. [learning] is the learned-choice token
 * for `launch_app` only (the same act as a launch); the command session records the choice at
 * «Выполнить», through the one gated record call (`LauncherAppLaunch.recordChoiceIfPending`).
 */
data class AppChoice(
    val arg: String,
    val mention: String,
    val candidates: List<InstalledApp>,
    val learning: ResolutionLearningToken?,
)

/** The pick. [packageName] must be one of the candidates; anything else changes nothing (`null`). */
fun Interpretation.Act.choose(packageName: String): Interpretation.Act? {
    val pending = choice ?: return null
    if (pending.candidates.none { it.packageName == packageName }) return null
    return copy(
        step = step.copy(
            invocation = step.invocation.copy(args = step.invocation.args + (pending.arg to ArgSource.Literal(packageName))),
        ),
        origins = origins + (pending.arg to ValueOrigin.Ours),
        choice = null,
    )
}

/** The plan «Выполнить» starts — only once nothing is left to choose. One step in 3a (plan §0.2 B-1). */
fun Interpretation.Act.plan(): ExecutionPlan? = if (choice != null) null else ExecutionPlan(listOf(step))

/**
 * Device-side binding of the model's words (spec §7.12.1, D1): a mention becomes a package on this
 * phone, or the user is asked. **Its outcome never goes back to the model** — it goes to the user (a
 * pick, an honest "no such app"); `RouteCommandUseCase` calls it after the model has answered.
 */
interface MentionBinder {
    suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding
}

sealed interface Binding {
    data class Bound(val interpretation: Interpretation.Act) : Binding
    data class NotFound(val mention: String) : Binding
    data object Unavailable : Binding
}
```

`DeviceMentionBinder.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.LaunchSlotExtractor
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionDecision
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import javax.inject.Inject

/**
 * The bridge's [MentionBinder] on Android (spec §7.12.1, §7.12.3 condition 2; plan §0.2 B-3).
 *
 * For a tool with an app sort ([ToolArgumentSorts]) the model's word for the app
 * ([AuthoredToolDescriptions.MENTION_ARG]) leaves the model-facing name: the package goes into the
 * row's `arg` once known, the user's words into its `labelArg`. [AppTargetResolver.candidates] answers —
 * it never guesses and consults aliases; two candidates are a choice for the USER before any card.
 *
 * **Learned choices answer for `launch_app` only**, with FastPath's own key and policy — a confident one
 * binds without asking, a weak one is offered first. `uninstall_app` and `open_app_info` never read
 * them: consent learned for opening an app is not consent for removing it (`AppTargetResolver`).
 *
 * Nothing here reaches the model: `RouteCommandUseCase` calls this after the model has answered, and
 * returns what it finds to the screen.
 */
class DeviceMentionBinder @Inject constructor(
    private val appTargets: AppTargetResolver,
    private val sorts: ToolArgumentSorts,
    private val preferences: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
) : MentionBinder {

    override suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding {
        val sort = sorts.appBindingFor(proposed.tool)
        val mention = proposed.args[AuthoredToolDescriptions.MENTION_ARG]
        if (sort == null || mention == null) {
            return Binding.Bound(act(goal, proposed, proposed.args, proposed.origins, choice = null))
        }
        val words = (proposed.args - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sort.labelArg?.let { it to mention })
        val wordOrigins = (proposed.origins - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sort.labelArg?.let { it to ValueOrigin.SaidBy(PrincipalId.USER) })

        return when (val found = appTargets.candidates(mention)) {
            AppCandidates.None -> Binding.NotFound(mention)
            AppCandidates.Unavailable -> Binding.Unavailable
            is AppCandidates.One -> Binding.Bound(
                act(goal, proposed, words + (sort.arg to found.app.packageName), wordOrigins + (sort.arg to ValueOrigin.Ours), choice = null),
            )
            is AppCandidates.Several -> Binding.Bound(several(goal, proposed, sort, mention, found.apps, words, wordOrigins))
        }
    }

    private suspend fun several(
        goal: AgentGoal,
        proposed: ProposedStep,
        sort: AppArgumentBinding,
        mention: String,
        apps: List<InstalledApp>,
        words: Map<String, String>,
        origins: Map<String, ValueOrigin>,
    ): Interpretation.Act {
        if (proposed.tool != ToolIds.LAUNCH_APP) {
            return act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, learning = null))
        }
        val candidates = CandidateSet(apps.map { ResolvedTarget.App(it.packageName) })
        val key = CapabilityKey(ActionIds.LAUNCH_APP, LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(mention)))
        val token = ResolutionLearningToken(
            capabilityKey = key,
            context = ResolutionContext.None,
            candidateSet = candidates,
            fingerprint = fingerprintOf(candidates),
            isAppAmbiguityFlow = true,
        )
        val preference = (preferences.find(key, ResolutionContext.None) as? OperationResult.Success)?.value
        return when (val decision = policy.decide(preference, candidates, proposed.risk)) {
            is ResolutionDecision.AutoResolve -> {
                val target = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                act(goal, proposed, words + (sort.arg to target), origins + (sort.arg to ValueOrigin.Ours), choice = null)
            }
            is ResolutionDecision.RankFirst -> {
                val first = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps.sortedByDescending { it.packageName == first }, token))
            }
            ResolutionDecision.NoPreference -> act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))
            is ResolutionDecision.Stale -> {
                preferences.delete(key, ResolutionContext.None) // best-effort prune, as FastPath's decorator does
                act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))
            }
        }
    }

    private fun act(
        goal: AgentGoal,
        proposed: ProposedStep,
        args: Map<String, String>,
        origins: Map<String, ValueOrigin>,
        choice: AppChoice?,
    ) = Interpretation.Act(
        goal = goal,
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(proposed.tool, args.mapValues { (_, value) -> ArgSource.Literal(value) }),
            risk = proposed.risk,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = origins,
        effect = proposed.effect,
        choice = choice,
    )
}
```

`FakeMentionBinder.kt`:

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation

/**
 * A [MentionBinder] for tests that are not about binding: by default it binds the step exactly as the
 * model wrote it (no app lookup); [binding] scripts any other answer.
 */
class FakeMentionBinder(var binding: ((AgentGoal, ProposedStep) -> Binding)? = null) : MentionBinder {

    var bindCallCount: Int = 0
        private set

    override suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding {
        bindCallCount++
        return binding?.invoke(goal, proposed) ?: Binding.Bound(
            Interpretation.Act(
                goal = goal,
                step = PlanStep(
                    index = 0,
                    invocation = ToolInvocation(proposed.tool, proposed.args.mapValues { (_, value) -> ArgSource.Literal(value) }),
                    risk = proposed.risk,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
                origins = proposed.origins,
                effect = proposed.effect,
                choice = null,
            ),
        )
    }
}
```

- [ ] **Step 4: run — expect green.** `:domain` → `tests=524`; `:data:repository` → `tests=431`.

- [ ] **Step 5: mutations — six.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M7a | `DeviceMentionBinder.kt` · `        if (proposed.tool != ToolIds.LAUNCH_APP) {\n            return act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, learning = null))\n        }\n` → `` | learned choices answer for `launch_app` only | `:data:repository` — `DeviceMentionBinderTest > uninstall never reads a learned choice` **only** |
| M7b | `DeviceMentionBinder.kt` · `act(goal, proposed, words + (sort.arg to target), origins + (sort.arg to ValueOrigin.Ours), choice = null)` → `act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))` | a confident learned choice binds | `:data:repository` — `> launch_app's confident learned choice binds without asking` **only** |
| M7c | `DeviceMentionBinder.kt` · `ResolutionDecision.NoPreference -> act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))` → `ResolutionDecision.NoPreference -> act(goal, proposed, words + (sort.arg to apps.first().packageName), origins, choice = null)` | criterion 17: two candidates are a choice | `:data:repository` — `> two candidates give a choice before any plan, and nothing is bound yet` **only**. **Measured in execution 2026-10-04 (E7-1): two** — plus `> every app mention the model is asked for is one the binder reads, and every bindable tool asks for one`, the test a controller ruling added to this task (with `launch_app` bound at once, `choose` returns null and the mention lands in its `unread` set) |
| M7d | `Interpretation.kt` · `if (pending.candidates.none { it.packageName == packageName }) return null` → `` (the line removed) | a pick is one of the candidates | `:domain` — `InterpretationTest > a package that was not offered changes nothing` **only** |
| M7e | `AgentExecutor.kt` · `    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {\n` → `    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {\n        if (step.rationale == StepRationale.MODEL_PROPOSED) return null\n` | `DOC-ADL-1`: one gate whoever proposed | `:domain` — `AgentExecutorTest > a CONFIRM step the model proposed stops at the engine's consent exactly like a deterministic one` **only** (if `:app`'s `CompensationOutsideTheEngineGuardTest` also reddens on the word `StepRationale` in the engine, record it — not run here) |
| M7f | `AgentSessionMappers.kt` · `        StepRationale.MODEL_PROPOSED.name -> StepRationale.MODEL_PROPOSED\n` → `` | the rationale survives the store | `:data:repository` — `AgentSessionMappersTest > every StepRationale round-trips` **only** |

- [ ] **Step 6: boundary gate from a cleared tree** (`./gradlew --stop` first). Expected `GATE GREEN`,
  `TOTAL tests=1646`: `:domain` **524** (+4), `:data:repository` **431** (+10).

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src data/repository/src core/testing/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): привязка на устройстве — связыватель, толкование, MODEL_PROPOSED

Спека §7.12.1, условие 2 D3. Слово модели о приложении становится пакетом
до карточки или выбором для пользователя — никогда догадкой и никогда
сообщением модели. Выученные выборы — только для launch_app (тот же акт);
удаление их не читает. Выбор привязывает, не запускает. Шаги модели несут
StepRationale.MODEL_PROPOSED (по имени в TEXT-колонке, без миграции).
Якорь DOC-ADL-1: шаг CONFIRM от модели останавливается у согласия движка
ровно как детерминированный.

Мутации M7a–M7f.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: resuming a plan the model wrote goes back through the route

**Spec:** §7.12.3 condition 4 («`ResumeAgentSessionUseCase` для такого плана не перепланирует, а
отдаёт исход «нужен новый маршрут»… поверхность проводит текст цели через `RouteCommandUseCase.route` и
закрывает устаревшую сессию»); criterion 20; `DOC-ADL-3` (the resume half). **Modules:** `:domain`,
`:feature:launcher`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ResumeAgentSessionUseCase.kt`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSession.kt`
  — constructor, `continueSession` (`:118-129`), class KDoc.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt` — the
  `agentSession` construction (`:265-273`).
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/ResumeAgentSessionUseCaseTest.kt`
  (six call sites `.value()` → `.ran()`, +2), `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSessionTest.kt`
  (`collaborator`, +2), `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt` (+1).

**Interfaces.**
- Produces:
  ```kotlin
  sealed interface Resumption { data class Ran(val session: AgentSession); data class NeedsRoute(val goal: AgentGoal) }
  suspend fun ResumeAgentSessionUseCase.resume(paused: AgentSession): OperationResult<Resumption>
  LauncherAgentSession(…, onReroute: (String) -> Unit)   // new last constructor parameter, no default
  ```

- [ ] **Step 1: write the failing tests.** In `ResumeAgentSessionUseCaseTest`, add beside `value()`:

```kotlin
    private fun OperationResult<Resumption>.ran(): AgentSession =
        ((this as OperationResult.Success).value as Resumption.Ran).session

    /** A one-step store search the MODEL proposed, stopped at its consent card, the process killed. */
    private suspend fun pausedModelPlan(): AgentSession {
        val session = AgentSession(
            id = AgentSessionId("m1"),
            goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(1),
                    TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL)),
                    TraceEvent.SessionPaused,
                ),
            ),
        )
        store.save(session)
        return session
    }

    /** A planner that WOULD plan — so "it was not asked" cannot pass by its having nothing to say. */
    private class CountingPlanner : Planner {
        var calls = 0
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
            calls++
            return TemplatePlanner().plan(request, registry)
        }
    }
```

replace each of the six `.resume(…).value()` with `.resume(…).ran()`, and append:

```kotlin

    /**
     * Spec §7.12.3 condition 4, criterion 20. A plan the model wrote, in which nothing has acted, is not
     * planned again here: `CompositePlanner` would answer `NoPlan` (`ToolMatchPlanner` cannot read the
     * model's words), and M1(b) would end a legitimate plan «не удалось завершить». The engine hands it
     * back; the layer that holds the route takes its words through the route again.
     */
    @Test
    fun `a model-planned paused plan that has only looked is handed back for a new route, never planned again`() = runTest {
        val planner = CountingPlanner()
        val paused = pausedModelPlan()

        val result = resume(FakeToolExecutor(emptyList()), planner).resume(paused)

        assertEquals(OperationResult.Success(Resumption.NeedsRoute(paused.goal)), result)
        assertEquals(0, planner.calls)
        assertEquals("the stale session is the route layer's to close", paused, store.active)
    }

    /**
     * The phase-2 rule stands for a model plan too: a call in flight at the pause may have acted, so it is
     * resumed once, never re-routed. A `SAFE` step (`web_search`), so no consent question confounds it.
     */
    @Test
    fun `a model-planned plan paused mid-call resumes its call instead of going back to the route`() = runTest {
        val webSearch = ToolId("web_search")
        val paused = AgentSession(
            id = AgentSessionId("m2"),
            goal = AgentGoal("найди ostravel lectures", GoalShape.Free("найди ostravel lectures")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(webSearch, mapOf("query" to ArgSource.Literal("ostravel lectures"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(TraceEvent.PlanCreated(1), TraceEvent.ToolInvoked(0, webSearch), TraceEvent.SessionPaused),
            ),
        ).also { store.save(it) }
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val planner = CountingPlanner()

        val after = resume(tools, planner).resume(paused).ran()

        assertEquals(ExecutionState.Completed, after.state)
        assertEquals(listOf(webSearch), tools.invocations.map { it.id })
        assertEquals(0, planner.calls)
    }
```

(Imports as needed: `ActionRiskLevel`, `ArgSource`, `ToolId`, `ToolInvocation`, `ToolRegistry`,
`ConsentReason`, `TraceEvent`, `ExecutionTrace`, `PlanningRequest`, `PlanningResult`.)

In `LauncherAgentSessionTest`, add `private val rerouted = mutableListOf<String>()`, pass
`onReroute = { rerouted += it }` in `collaborator(…)`, and append:

```kotlin

    /** Spec §7.12.3 condition 4: the stale session is closed and its words go through the route again. */
    @Test
    fun `continuing a paused plan the model wrote closes it and takes its words through the route again`() = runTest {
        store.save(pausedModelPlan())
        val agent = collaborator(FakeToolExecutor(emptyList()), this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        advanceUntilIdle()

        assertEquals(listOf("найди lumora в магазине"), rerouted)
        assertNull(store.activeOrNull)
        assertNull(agent.session.value)
    }

    @Test
    fun `going back through the route performs nothing on the way`() = runTest {
        store.save(pausedModelPlan())
        val tools = FakeToolExecutor(emptyList())
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), tools.invocations)
    }
```

with `pausedModelPlan()` — the same session as in `ResumeAgentSessionUseCaseTest` (id `m1`, one
`play_store_search` step `MODEL_PROPOSED`, `Paused`, trace `PlanCreated(1)`,
`ConsentRequested(0, {RISK_LEVEL})`, `SessionPaused`) — written as a private function of this file.

In `LauncherViewModelTest`, give `buildViewModel` one more defaulted parameter — every existing call
is unchanged — and use it in place of the hard-wired route:

```kotlin
        // A4′ phase 3a, Task 8: a test may route through its own RouteCommandUseCase (Task 9 scripts it).
        route: CommandRouteStep = CommandRouteStep { routeUseCase.route(it) },
```

(`resolveCommand = aliasAwareResolveCommand(route = route, …)` in its body.) Then append (the file's
`useCase`, `startAgentSession`, `fakeAgentStore`, `testDispatcher`; imports `FakeFeatureFlagRepository`,
`FeatureFlags`, `CommandFeedback`, `CommandMessage`):

```kotlin

    /**
     * Spec §7.12.3 condition 4: the route's gates are met again. Local-only switched on while the plan
     * sat paused — «Продолжить» now says so honestly instead of acting on the model's old reading.
     */
    @Test
    fun `continuing a model plan after local-only was switched on says so honestly`() = runTest(testDispatcher) {
        fakeAgentStore.save(pausedModelPlanForVm())
        val localOnlyRoute = RouteCommandUseCase(
            handleUserCommand = useCase,
            planner = FakeCommandPlanner(),
            catalog = FakeActionCatalog(),
            featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = true)),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(),
            startAgentSession = startAgentSession,
        )
        val vm = buildViewModel(route = CommandRouteStep { localOnlyRoute.route(it) })
        advanceUntilIdle()

        vm.continueAgentSession()
        advanceUntilIdle()

        assertEquals(CommandFeedback.Domain(CommandMessage.UnderstandingLocalOnly), vm.commandFeedback.value)
        assertEquals(null, fakeAgentStore.activeOrNull)
    }
```

with `pausedModelPlanForVm()` the same session as above, as a private function of this file.

- [ ] **Step 2: run — expect compile-red.** `:domain` → `Unresolved reference 'Resumption'`;
  `:feature:launcher` → `No value passed for parameter 'onReroute'`.
  **Measured in execution 2026-10-04 (E8-1):** `:feature:launcher` reads `No parameter with name 'onReroute'
  found` (the tests pass it by name before the constructor has it); `:domain` also prints `Cannot infer type for
  type parameter 'T'`.

- [ ] **Step 3: implement.** `ResumeAgentSessionUseCase.kt` — add above the class:

```kotlin
/**
 * What «Продолжить» did — or that the ROUTE must decide instead (A4′ phase 3a, spec §7.12.3 condition 4).
 */
sealed interface Resumption {
    /** The engine took it: continued from the cursor, planned afresh, or ended honestly (phase 2, M1(b)). */
    data class Ran(val session: AgentSession) : Resumption

    /**
     * A plan the MODEL wrote, in which nothing has acted. It is not planned again here — the
     * deterministic planner would answer `NoPlan`, and the engine names no model port. The layer that
     * holds the route closes the stale session and takes [goal]'s words through the route, where gates
     * 3, 5 and 6 and the interpretation card are met afresh.
     */
    data class NeedsRoute(val goal: AgentGoal) : Resumption
}
```

replace `resume`'s body and signature with:

```kotlin
    suspend fun resume(paused: AgentSession): OperationResult<Resumption> {
        if (!paused.replansOnResume()) return run.run(paused.resumed()).ran()
        // A4′ phase 3a (condition 4): a plan the model wrote goes back through the route, never through
        // the deterministic planner here — and nothing about that is decided by the engine.
        if (paused.plan.isModelProposed()) return OperationResult.Success(Resumption.NeedsRoute(paused.goal))
        val fresh = start.plan(paused.goal) ?: return run.run(paused.ended(ExecutionState.Failed)).ran()
        val started = when (val saved = start.startPlanned(paused.goal, fresh)) {
            is OperationResult.Failure -> return saved
            // Practically unreachable: see the phase-2 note this line carried (M1(b)).
            is OperationResult.Success -> saved.value ?: return run.run(paused.ended(ExecutionState.Failed)).ran()
        }
        return run.run(started).ran()
    }

    private fun OperationResult<AgentSession>.ran(): OperationResult<Resumption> = when (this) {
        is OperationResult.Failure -> this
        is OperationResult.Success -> OperationResult.Success(Resumption.Ran(value))
    }
```

and add to the class KDoc: «A plan the model wrote (A4′ phase 3a) in which nothing has acted is handed
back as [Resumption.NeedsRoute] — see there.»

`LauncherAgentSession.kt` — add `private val onReroute: (String) -> Unit,` as the last constructor
parameter (KDoc line: «where a paused plan the model wrote goes: its words, back through the command
route (spec §7.12.3 condition 4)»), import `Resumption`, and replace `continueSession`'s `try` body
`show((resumeSession.resume(paused) as? OperationResult.Success)?.value)` with:

```kotlin
                when (val resumed = (resumeSession.resume(paused) as? OperationResult.Success)?.value) {
                    is Resumption.Ran -> show(resumed.session)
                    is Resumption.NeedsRoute -> {
                        // Condition 4: the model's old reading is not acted on. Close it, then take the
                        // user's own words through the whole command route again — local-only, the
                        // provider, the network and the interpretation card are all met afresh.
                        cancelSession.cancel(paused.id)
                        show(null)
                        onReroute(resumed.goal.text)
                    }
                    null -> show(null)
                }
```

`LauncherViewModel.kt` — give the property an explicit type (the forward reference below otherwise trips
the recursive-inference bug noted at `:244-247`) and wire the callback:

```kotlin
    private val agentSession: LauncherAgentSession = LauncherAgentSession(
        runSession = runAgentSession,
        resolveConsent = resolveAgentConsent,
        cancelSession = cancelAgentSession,
        store = agentSessionStore,
        scope = viewModelScope,
        resumeSession = resumeAgentSession,
        undoSession = undoAgentSession,
        // A4′ phase 3a (condition 4): forward-references [commandSession], declared below — legal for the
        // same reason as [appLaunch]'s onFeedback: the lambda runs only after construction.
        onReroute = { text -> commandSession.submit(text) },
    )
```

- [ ] **Step 4: run — expect green.** `:domain` → `tests=526`; `:feature:launcher` → `tests=220`.

- [ ] **Step 5: mutations — two.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M8a | `ResumeAgentSessionUseCase.kt` · `        if (paused.plan.isModelProposed()) return OperationResult.Success(Resumption.NeedsRoute(paused.goal))\n` → `` | criterion 20: never through `CompositePlanner` | `:domain` — `ResumeAgentSessionUseCaseTest > a model-planned paused plan that has only looked is handed back for a new route, never planned again` **only**; `:feature:launcher` — `LauncherAgentSessionTest > continuing a paused plan the model wrote closes it…`, `LauncherViewModelTest > continuing a model plan after local-only was switched on says so honestly` (the store step re-planned and run: `going back through the route performs nothing on the way` may stay green — the store step stops at consent; record) |
| M8b | `LauncherAgentSession.kt` · `                        cancelSession.cancel(paused.id)\n` → `` | the stale session is closed | `:feature:launcher` — `LauncherAgentSessionTest > continuing a paused plan the model wrote closes it and takes its words through the route again`, `LauncherViewModelTest > continuing a model plan after local-only was switched on says so honestly` |
| M8c | *(added in execution, Task 8 review)* `LauncherAgentSession.kt` · `                        cancelSession.cancel(paused.id)\n` → `                        runSession.run(paused.resumed())\n                        cancelSession.cancel(paused.id)\n` | the re-route performs nothing on the way | `:feature:launcher` — `LauncherAgentSessionTest > going back through the route performs nothing on the way` **only**, message `expected:<[]> but was:<[ResolvedInvocation(id=ToolId(value=web_search), …)]>`. **Measured 2026-10-04.** As planned that test could not fail — its only step was `CONFIRM`, so a stale run stopped at consent; its fixture is now a `SAFE` `web_search` step (fix round `ff7c5ad`). The plant keeps the re-route on purpose: one that replaced it would redden the test's `rerouted` assertion instead |

- [ ] **Step 6: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1651`:
  `:domain` **526** (+2), `:feature:launcher` **220** (+3).

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src feature/launcher/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): возобновление плана модели — через маршрут, не через планировщик

Условие 4 D3 (спека §7.12.3), критерий 20. ResumeAgentSessionUseCase
отдаёт Resumption.NeedsRoute для плана модели, в котором ничего не
совершено, — до CompositePlanner; домен по-прежнему не называет модельного
порта. Поверхность закрывает устаревшую сессию и проводит слова цели через
весь маршрут: local-only, провайдер, сеть и карточка толкования — заново.
Совершивший план продолжается с курсора (правило фазы 2).

Мутации M8a–M8b.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: the interpretation as a command outcome — the card's logic, the wait, the prefill

**Spec:** §7.12.3 conditions 1–3 («карточка толкования до старта сессии… касание по кандидату
привязывает… вопрос — в ассистента… `OpenAssistant` несёт вопрос… предзаполнение»), §7.12.2 («видимое
ожидание с отменой… отмена ожидания не исполняет ничего»); criteria 15 (the surface half), 17 (the pick),
19 (the cancel). Plan §0.2 B-2, B-3 (record at «Выполнить»), B-9, B-10, B-12. **Modules:** `:domain`,
`:core:common`, `:feature:launcher`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/CommandOutcome.kt` —
  `OpenAssistant` (`:47-48`), a new `Interpreted` after `AgentSessionStarted`.
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt:128`,
  `.../intent/ExecuteActionUseCase.kt:29`.
- Modify: `core/common/src/main/java/com/sidr/launcher/core/common/navigation/Routes.kt` (`Assistant`).
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt`
  (constructor, state, `onChanged`, `submit`, `outcomeSummary`, `applyOutcome`, four new functions),
  `.../LauncherViewModel.kt` (constructor, the `commandSession` construction, six delegations).
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/ExecuteActionUseCaseTest.kt:110`,
  `.../intent/HandleUserCommandUseCaseTest.kt:215`, `.../memory/AgentOutcomePassThroughTest.kt` (+1);
  `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt` (+10, and
  every `LauncherViewModel(` construction gains `startAgentSession = …`), and the two other files that
  construct `LauncherViewModel(` — `LauncherScreenPrayerStripTest`, `LauncherScreenAgentProvenanceTest`
  (one argument each).

**Interfaces.**
- Produces:
  ```kotlin
  // :domain
  data class CommandOutcome.OpenAssistant(val question: String?)
  data class CommandOutcome.Interpreted(val interpretation: Interpretation)
  // :core:common
  fun Routes.Assistant.routeForPrompt(prompt: String): String   // percent-encodes; == routeFor(encoded)
  // :feature:launcher (LauncherViewModel)
  val interpretation: StateFlow<Interpretation?>; val thinking: StateFlow<Boolean>
  fun runInterpretation(); fun dismissInterpretation(); fun chooseInterpretationApp(packageName: String); fun cancelThinking()
  // constructor gains: startAgentSession: StartAgentSessionUseCase
  ```
- Consumes: `Interpretation`, `AppChoice`, `choose`, `plan`, `SidrDestination` (Tasks 3, 7).

- [ ] **Step 1: write the failing tests.**

`AgentOutcomePassThroughTest` — append (same shape as the file's two tests; the decorators must hand the
card through untouched):

```kotlin

    @Test
    fun `both decorators pass an interpretation through untouched`() = runTest {
        val interpreted = CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.APP_GRID))
        val preference = ResolveCommandWithPreferenceUseCase(
            route = CommandRouteStep { interpreted },
            store = FakeResolutionPreferenceStore(),
            policy = DefaultResolutionPreferencePolicy(),
            catalog = FakeActionCatalog(),
        )
        val alias = ResolveCommandWithAliasUseCase(
            inner = ResolvedCommandStep { preference.resolve(it) },
            store = FakeAliasStore(),
            installedApps = FakeInstalledAppsRepository(),
        )

        assertEquals(ResolvedCommand.Outcome(interpreted, null), alias.resolve("открой все приложения"))
    }
```

`LauncherViewModelTest` — fixtures (private, in the class):

```kotlin
    private fun storeStepReading() = Interpretation.Act(
        goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
            risk = ActionRiskLevel.CONFIRM,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
        effect = ToolEffect.EXTERNAL,
        choice = null,
    )

    private val tessarOne = InstalledApp("net.fixture.tessar.one", "Tessar")
    private val tessarTwo = InstalledApp("net.fixture.tessar.two", "Tessar")

    private fun launchPickReading(): Interpretation.Act {
        val candidates = CandidateSet(listOf(ResolvedTarget.App(tessarOne.packageName), ResolvedTarget.App(tessarTwo.packageName)))
        return Interpretation.Act(
            goal = AgentGoal("открой tessar", GoalShape.Free("открой tessar")),
            step = PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("tessar"))),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
            origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
            effect = ToolEffect.EXTERNAL,
            choice = AppChoice(
                arg = "app",
                mention = "tessar",
                candidates = listOf(tessarOne, tessarTwo),
                learning = ResolutionLearningToken(
                    capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                    context = ResolutionContext.None,
                    candidateSet = candidates,
                    fingerprint = fingerprintOf(candidates),
                    isAppAmbiguityFlow = true,
                ),
            ),
        )
    }

    private fun answering(outcome: CommandOutcome) = buildViewModel(route = CommandRouteStep { outcome })
```

and the ten tests:

```kotlin

    // ── A4′ phase 3a: the interpretation card (spec §7.12.3) ─────────────────────────────

    /** Criterion 15, the surface half: the model's reading waits on the card; nothing has started. */
    @Test
    fun `a model's interpretation waits on the card and nothing runs`() = runTest(testDispatcher) {
        val reading = storeStepReading()
        val vm = answering(CommandOutcome.Interpreted(reading))

        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        assertEquals(reading, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
        assertTrue(fakeExecutor.executedActions.isEmpty())
    }

    /** «Выполнить» starts a session THROUGH THE ENGINE, which gates the CONFIRM step like any other. */
    @Test
    fun `Run starts the session through the engine and the card leaves`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(null, vm.interpretation.value)
        assertEquals(ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
        assertTrue(fakeExecutor.executedActions.isEmpty())
    }

    @Test
    fun `dismissing the card starts nothing`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.dismissInterpretation()
        advanceUntilIdle()

        assertEquals(null, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Criterion 17: a pick binds the step's app — it launches nothing and starts nothing. */
    @Test
    fun `a pick binds the app and launches nothing`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(launchPickReading()))
        vm.onCommandSubmitted("открой tessar")
        advanceUntilIdle()

        vm.chooseInterpretationApp("net.fixture.tessar.two")
        advanceUntilIdle()

        val bound = vm.interpretation.value as Interpretation.Act
        assertEquals(null, bound.choice)
        assertEquals(ArgSource.Literal("net.fixture.tessar.two"), bound.step.invocation.args["app"])
        assertTrue(fakeExecutor.executedActions.isEmpty())
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Plan §0.2 B-3: the learned choice is recorded at «Выполнить» — the launch act — not at the pick. */
    @Test
    fun `Run after a pick records the learned choice once`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(launchPickReading()))
        vm.onCommandSubmitted("открой tessar")
        advanceUntilIdle()
        vm.chooseInterpretationApp("net.fixture.tessar.two")
        advanceUntilIdle()
        assertTrue("nothing is learned at the pick", fakeResolutionStore.observeAll().first().isEmpty())

        vm.runInterpretation()
        advanceUntilIdle()

        val learned = fakeResolutionStore.observeAll().first().single()
        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), learned.capabilityKey)
        assertEquals(ResolvedTarget.App("net.fixture.tessar.two"), learned.preferredTarget)
    }

    /** Review Focus 2: the card describes the old text; an edit abandons it. */
    @Test
    fun `editing the command dismisses the interpretation`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.onCommandChanged("найди что-то другое")

        assertEquals(null, vm.interpretation.value)
    }

    /** Review Focus 1: two taps, one session. */
    @Test
    fun `a second Run tap while the first is still starting starts one session`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.runInterpretation()
        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(1, fakeAgentStore.saved.map { it.id }.distinct().size)
    }

    /** Criterion 19: the wait is visible after a moment, and cancelling it applies nothing. */
    @Test
    fun `the wait shows after a moment and cancelling it applies nothing`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                CommandOutcome.Interpreted(storeStepReading())
            },
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceTimeBy(299)
        runCurrent()
        assertFalse("no wait shown for a quick answer", vm.thinking.value)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(vm.thinking.value)

        vm.cancelThinking()
        advanceUntilIdle()

        assertFalse(vm.thinking.value)
        assertEquals(null, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Review Focus 3 and condition 3: the question arrives prefilled and intact, unsent. */
    @Test
    fun `a question with reserved characters reaches the assistant intact`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.OpenAssistant("почему 2+2=4? & #1"))
        val event = async { vm.navigationEvents.first() }

        vm.onCommandSubmitted("почему 2+2=4? & #1")
        advanceUntilIdle()

        assertEquals(
            NavigationEvent.NavigateTo(
                "assistant?prompt=%D0%BF%D0%BE%D1%87%D0%B5%D0%BC%D1%83%202%2B2%3D4%3F%20%26%20%231",
            ),
            event.await(),
        )
    }

    /** Plan §0.2 B-9: a navigation the model read is shown first, like everything it reads. */
    @Test
    fun `a navigation reading opens Sidr's settings only on Run`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.LAUNCHER_SETTINGS)))
        vm.onCommandSubmitted("открой настройки лаунчера")
        advanceUntilIdle()
        val event = async { vm.navigationEvents.first() }
        runCurrent()
        assertFalse("nothing navigated before Run", event.isCompleted)

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(NavigationEvent.NavigateTo(Routes.Settings.ROUTE), event.await())
    }
```

(Imports: `Interpretation`, `AppChoice`, `SidrDestination`, `ValueOrigin`, `PrincipalId`, `AgentGoal`,
`GoalShape`, `PlanStep`, `StepPrecondition`, `StepRationale`, `ExecutionState`, `ToolInvocation`,
`ArgSource`, `ToolIds`, `ToolEffect`, `ActionIds`, `ActionRiskLevel`, `CandidateSet`, `CapabilityKey`,
`ResolutionContext`, `ResolutionLearningToken`, `ResolvedTarget`, `fingerprintOf`, `NavigationEvent`,
`Routes`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.async`, `kotlinx.coroutines.flow.first`,
`kotlinx.coroutines.test.advanceTimeBy`, `kotlinx.coroutines.test.runCurrent`. `fakeResolutionStore` is
the class's existing fixture, shared with `buildViewModel`'s `RecordResolutionChoiceUseCase`.)

In `ExecuteActionUseCaseTest:110` replace `assertEquals(CommandOutcome.OpenAssistant, outcome)` with
`assertEquals(CommandOutcome.OpenAssistant(question = <the prompt that test passes, or null>), outcome)`
— read the test's `LauncherAction.OpenAssistant(…)` argument; in `HandleUserCommandUseCaseTest:215`
replace `CommandOutcome.OpenAssistant` with `CommandOutcome.OpenAssistant(question = null)`.

- [ ] **Step 2: run — expect compile-red.** `:domain` → `Unresolved reference 'Interpreted'`;
  `:feature:launcher` → `Unresolved reference 'interpretation'`.

- [ ] **Step 3: implement.**

`CommandOutcome.kt` — replace

```kotlin
    /** Navigate to the assistant — the ViewModel maps this to its navigation Channel. */
    data object OpenAssistant : CommandOutcome
```

with

```kotlin
    /**
     * Navigate to the assistant — the ViewModel maps this to its navigation Channel. [question], when
     * present, is put into the assistant's input UNSENT (A4′ phase 3a, spec §7.12.3 condition 3): the
     * model judged the command a question, and the user's «отправить» is the confirmation of that
     * reading. `null` opens it empty, as FastPath's "assistant" always has.
     */
    data class OpenAssistant(val question: String?) : CommandOutcome
```

and after `AgentSessionStarted` add

```kotlin

    /**
     * The model's reading of a command, bound to this device and shown BEFORE anything runs (A4′ phase
     * 3a, spec §7.12.3 condition 1) — the interpretation card. Nothing here executes: only the user's
     * «Выполнить» starts a session, through the engine, which gates it by risk like any plan. Replaces
     * the router's `RoutedAction` (removed in Task 11).
     */
    data class Interpreted(val interpretation: Interpretation) : CommandOutcome
```

(import `com.sidr.launcher.domain.ai.bridge.Interpretation`).

`HandleUserCommandUseCase.kt:128` — `SimpleCommand.OPEN_ASSISTANT -> CommandOutcome.OpenAssistant` →
`SimpleCommand.OPEN_ASSISTANT -> CommandOutcome.OpenAssistant(question = null)`.

`ExecuteActionUseCase.kt:29` — `is LauncherAction.OpenAssistant -> CommandOutcome.OpenAssistant` →
`is LauncherAction.OpenAssistant -> CommandOutcome.OpenAssistant(question = action.prompt)`.

`Routes.kt` — inside `object Assistant`, after `routeFor`:

```kotlin

        /**
         * [routeFor] with the encoding done here (A4′ phase 3a). The model's question for the assistant
         * reaches a plain-Kotlin command session, which cannot call Android's `Uri.encode`. Every byte of
         * the UTF-8 form is percent-encoded except RFC 3986's unreserved characters, which Navigation's
         * `Uri.decode` reads back exactly — `&`, `?`, `#`, `=`, `+` and spaces included.
         */
        fun routeForPrompt(prompt: String): String = routeFor(encodeQueryValue(prompt))

        private fun encodeQueryValue(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") {
                    append(char)
                } else {
                    append('%')
                    append(HEX[code shr 4])
                    append(HEX[code and 0x0F])
                }
            }
        }

        private const val HEX = "0123456789ABCDEF"
```

`LauncherCommandSession.kt`:
- constructor: after `private val agentSession: LauncherAgentSession,` add

  ```kotlin
      // A4′ phase 3a: «Выполнить» on the interpretation card starts the model's plan through the
      // engine — `startPlanned` validates it against the federation, the engine gates it by risk.
      private val startAgentSession: StartAgentSessionUseCase,
  ```

- state, after the `_pendingRoutedAction` pair:

  ```kotlin

      // ── The interpretation card (A4′ phase 3a, spec §7.12.3 condition 1) — transient (plan §0.2 B-12).
      private val _interpretation = MutableStateFlow<Interpretation?>(null)
      val interpretation: StateFlow<Interpretation?> = _interpretation

      // ── The visible wait (spec §7.12.2, plan §0.2 B-10) and the command it waits for. ──
      private val _thinking = MutableStateFlow(false)
      val thinking: StateFlow<Boolean> = _thinking
      private var pending: Job? = null

      // The package the user picked on the card's list, recorded as a learned choice at «Выполнить».
      private var pickedPackage: String? = null
  ```

- `onChanged`: after `_pendingRoutedAction.value = null` add
  `        _interpretation.value = null\n        pickedPackage = null` (Review Focus 2).
- `submit`: replace `        scope.launch {\n            when (val resolved = resolveCommand.resolve(text)) {`
  … through its closing — the whole `scope.launch { … }` block at the end of `submit` — with the same
  body wrapped as follows (the `when` and its two branches are unchanged, indented one level):

  ```kotlin
          pending?.cancel()
          pending = scope.launch {
              // Plan §0.2 B-10: FastPath answers before 300 ms and shows nothing; only a slow answer — the
              // model's — shows the wait, with «Отмена».
              val wait = launch {
                  delay(THINKING_DELAY_MS)
                  _thinking.value = true
              }
              try {
                  when (val resolved = resolveCommand.resolve(text)) {
                      // … the two existing branches, unchanged …
                  }
              } finally {
                  wait.cancel()
                  _thinking.value = false
              }
          }
  ```

- `outcomeSummary`: `CommandOutcome.OpenAssistant -> "→ assistant"` → `is CommandOutcome.OpenAssistant -> "→ assistant"`,
  and add `is CommandOutcome.Interpreted -> "? interpretation"`.
- `applyOutcome`: after `_pendingRoutedAction.value = null` add `        _interpretation.value = null`;
  replace the `OpenAssistant` arm with

  ```kotlin
              is CommandOutcome.OpenAssistant -> {
                  setCommandInput("")
                  _feedback.value = CommandFeedback.None
                  // Route string belongs to the UI layer — the domain only said "OpenAssistant". A question
                  // the model read arrives prefilled and UNSENT (spec §7.12.3 condition 3).
                  onNavigate(outcome.question?.let(Routes.Assistant::routeForPrompt) ?: Routes.Assistant.ROUTE)
              }
  ```

  and add, after the `AgentSessionStarted` arm:

  ```kotlin

              // A4′ phase 3a (condition 1): the model's reading waits on the card. A launch the user must
              // pick arms the learned-choice token; the pick binds and «Выполнить» records (B-3).
              is CommandOutcome.Interpreted -> {
                  _feedback.value = CommandFeedback.None
                  pickedPackage = null
                  appLaunch.rememberLearningToken((outcome.interpretation as? Interpretation.Act)?.choice?.learning)
                  _interpretation.value = outcome.interpretation
              }
  ```

- new functions, after `cancel()`:

  ```kotlin

      /** «Отмена» on the wait (spec §7.12.2): the command's coroutine — the model call with it — is cancelled. */
      fun cancelThinking() {
          pending?.cancel()
          pending = null
          _thinking.value = false
      }

      /** A pick on the card's list (spec §7.12.3 condition 2): it BINDS the step's app; it launches nothing. */
      fun chooseApp(packageName: String) {
          val act = _interpretation.value as? Interpretation.Act ?: return
          val bound = act.choose(packageName) ?: return
          pickedPackage = packageName
          _interpretation.value = bound
      }

      /**
       * «Выполнить» (condition 1). A navigation opens its screen. A step starts a session through the
       * engine, which gates it by its risk exactly as any plan; a `CONFIRM` step therefore costs a second
       * card — the regression spec §7.12.3 accepts. The card is taken down BEFORE the start, so a second
       * tap finds nothing to run (Review Focus 1).
       */
      fun runInterpretation() {
          when (val reading = _interpretation.value ?: return) {
              is Interpretation.Navigate -> applyOutcome(
                  when (reading.destination) {
                      SidrDestination.LAUNCHER_SETTINGS -> CommandOutcome.OpenSettings
                      SidrDestination.APP_GRID -> CommandOutcome.ShowApps
                  },
              )
              is Interpretation.Act -> {
                  val plan = reading.plan() ?: return // a pick is still owed
                  _interpretation.value = null
                  pickedPackage?.let(appLaunch::recordChoiceIfPending)
                  pickedPackage = null
                  scope.launch {
                      val started = (startAgentSession.startPlanned(reading.goal, plan) as? OperationResult.Success)?.value
                      applyOutcome(
                          if (started != null) CommandOutcome.AgentSessionStarted(started.id)
                          else CommandOutcome.Failed(CommandFailure.Generic),
                      )
                  }
              }
          }
      }

      /** «Отмена» on the card: nothing runs, nothing is learned. */
      fun dismissInterpretation() {
          _interpretation.value = null
          pickedPackage = null
          appLaunch.rememberLearningToken(null)
      }
  ```

- companion: add `const val THINKING_DELAY_MS = 300L`.
- imports: `Interpretation`, `SidrDestination`, `choose`, `plan` (`com.sidr.launcher.domain.ai.bridge.*`),
  `StartAgentSessionUseCase`, `CommandFailure`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`.

`LauncherViewModel.kt`:
- constructor, after `private val agentSessionStore: AgentSessionStore,` add
  `    // A4′ phase 3a: the interpretation card's «Выполнить» starts the model's plan through the engine.\n    private val startAgentSession: StartAgentSessionUseCase,`;
- the `commandSession` construction gains `startAgentSession = startAgentSession,` after
  `agentSession = agentSession,`;
- after `val pendingRoutedAction: …` add

  ```kotlin
      /** A4′ phase 3a: the model's reading, shown before anything runs (spec §7.12.3 condition 1). */
      val interpretation: StateFlow<Interpretation?> get() = commandSession.interpretation

      /** A4′ phase 3a: a command has not answered within 300 ms — the model is being asked. */
      val thinking: StateFlow<Boolean> get() = commandSession.thinking
  ```

  and after `cancelRoutedAction()`:

  ```kotlin

      fun runInterpretation() = commandSession.runInterpretation()

      fun dismissInterpretation() = commandSession.dismissInterpretation()

      fun chooseInterpretationApp(packageName: String) = commandSession.chooseApp(packageName)

      fun cancelThinking() = commandSession.cancelThinking()
  ```

Every `LauncherViewModel(` construction in tests (`LauncherViewModelTest` ×4 — `buildViewModel`,
`buildRouterViewModel`, and the two at `:~675`, `:~932` — `LauncherScreenPrayerStripTest:161`,
`LauncherScreenAgentProvenanceTest:251`) gains `startAgentSession = <the StartAgentSessionUseCase that
file already builds for its RouteCommandUseCase>,`. The two screen tests build that use case **inline**,
as an argument of `RouteCommandUseCase(` (`LauncherScreenPrayerStripTest:142-147`,
`LauncherScreenAgentProvenanceTest:232-237`) — lift it into a local `val startAgentSession =
StartAgentSessionUseCase(planner = TemplatePlanner(), store = agentStore, ids = agentIds, registry =
agentRegistry)` above the `RouteCommandUseCase(` and pass the same instance to both (one instance, so the
card's start and the route's start share one store and one id sequence).

- [ ] **Step 4: run — expect green.** `:domain` → `tests=527`; `:feature:launcher` → `tests=230`.

  > **Measured in execution (2026-10-04):** 529 / 230 at the tree of the day (+2 carried in `:domain`). Step 2 also
  > prints `No parameter with name 'startAgentSession' found.` ×6 and inference errors at the two `OpenAssistant(…)` test
  > sites; the two screen-test constructions sat at `:691` / `:948`, not `:~675` / `:~932`.

- [ ] **Step 5: mutations — six.** Plant strings carry the FILE's indentation: the code blocks of this
task sit inside a list and are shifted two spaces right of it (the plan review caught three plants that
had copied the shift and would have matched nothing).

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M9a | `LauncherCommandSession.kt` · `                _interpretation.value = outcome.interpretation\n` → `                _interpretation.value = outcome.interpretation\n                runInterpretation()\n` | criterion 15: nothing runs by itself | `:feature:launcher` — `LauncherViewModelTest > a model's interpretation waits on the card and nothing runs`, `> dismissing the card starts nothing`, `> a navigation reading opens Sidr's settings only on Run` (three). Worked through and expected GREEN: `editing the command…` (the card is gone either way), `a pick binds…` (a pick is owed, so the auto-run returns), `Run starts the session…`, `Run after a pick…`, `a second Run tap…`, the wait and the question tests |
| M9b | `LauncherCommandSession.kt` · `        pickedPackage = packageName\n        _interpretation.value = bound\n` → `        pickedPackage = packageName\n        _interpretation.value = bound\n        runInterpretation()\n` | criterion 17: a pick binds, it does not launch | `:feature:launcher` — `> a pick binds the app and launches nothing`, `> Run after a pick records the learned choice once` (recorded at the pick's auto-run: "nothing is learned at the pick" fails) |
| M9c | `LauncherCommandSession.kt` · `    fun cancelThinking() {\n        pending?.cancel()\n` → `    fun cancelThinking() {\n` | criterion 19: cancel applies nothing | `:feature:launcher` — `> the wait shows after a moment and cancelling it applies nothing` **only** |
| M9d | `Routes.kt` · `fun routeForPrompt(prompt: String): String = routeFor(encodeQueryValue(prompt))` → `fun routeForPrompt(prompt: String): String = routeFor(prompt)` | Review Focus 3 | `:feature:launcher` — `> a question with reserved characters reaches the assistant intact` **only** |
| M9e | `LauncherCommandSession.kt` · `                val plan = reading.plan() ?: return // a pick is still owed\n                _interpretation.value = null\n` → `                val plan = reading.plan() ?: return // a pick is still owed\n` and `                    applyOutcome(\n                        if (started != null)` → `                    _interpretation.value = null\n                    applyOutcome(\n                        if (started != null)` (one plant per invocation, both restored) | Review Focus 1 | `:feature:launcher` — `> a second Run tap while the first is still starting starts one session` **only** |
| M9f | `LauncherCommandSession.kt` · `                pickedPackage?.let(appLaunch::recordChoiceIfPending)\n` → `` | the learned choice is kept at «Выполнить» | `:feature:launcher` — `> Run after a pick records the learned choice once` **only** |

(M9e is two plants of one file: run them as two `plant` calls in the SAME invocation, then one restore —
the protocol's single `cp` restores both.)

> **Measured in execution (2026-10-04) — M9a–M9f PROVED exactly as predicted.** The implementer found five tests that
> could not fail under a plausible regression; the controller ruled them into the task, and the task review added a
> sixth (I1 — an edit during the wait did not cancel the in-flight command: a late answer put up a card for abandoned
> text, a SAFE step one tap from running; the in-flight half of Review Focus 2). Seven more plants, each PROVED with
> exactly its own test red:
>
> | # | Plant | Red |
> |---|---|---|
> | M9g | drop `wait.cancel()` in `submit`'s `finally` | `a model's interpretation waits on the card and nothing runs` (new assert: a quick answer never shows the wait) |
> | M9h | drop `_interpretation.value = null` at the top of `applyOutcome` | `a navigation reading opens Sidr's settings only on Run` (new assert) |
> | M9i | drop `appLaunch.rememberLearningToken(null)` in `dismissInterpretation` | `a dismissed pick card leaves nothing to learn from a later tap` (new) |
> | M9j | `routeForPrompt(outcome.question ?: "")` | `an assistant command without a question opens the bare assistant route` (new) |
> | M9k | drop `pending?.cancel()` in `submit` (with the `pending = scope.launch {` line as context — the bare line occurs more than once) | `a newer command supersedes a slow answer still on its way` (new) |
> | M9l | drop `pending?.cancel()` in `onChanged` (review I1, fix `5a75f6d`) | `editing the command during the wait abandons it` (new) |
> | M9m | voice `onPartial` → `setCommandInput` instead of `onChanged` (review m1) | `dictating a new command dismisses the standing interpretation` (new) |
>
> Also `AgentOutcomePassThroughTest`'s new test now asserts both halves of its file's property (equality **and** no store
> read), per the file's own KDoc.

- [ ] **Step 6: boundary gate from a cleared tree** (`./gradlew --stop` first). Expected `GATE GREEN`,
  `TOTAL tests=1662`: `:domain` **527** (+1), `:feature:launcher` **230** (+10). *(Measured: 1670 at `892f85a`, 1672
  after the review fix `5a75f6d` — see the note under Step 5.)*

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src core/common/src feature/launcher/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): толкование как исход команды — карточка, выбор, ожидание, предзаполнение

Условия 1–3 D3 (спека §7.12.3), §7.12.2. CommandOutcome.Interpreted — карточка
до старта сессии; «Выполнить» стартует план через движок (startPlanned),
второй тап не стартует второй сессии. Выбор кандидата привязывает, выученный
выбор пишется при «Выполнить». OpenAssistant несёт вопрос — он приходит в
ассистента предзаполненным и неотправленным (кодирование — в Routes).
Видимое ожидание после 300 мс с «Отмена»: отмена гасит вызов, ничего не
применяет. RoutedAction пока жив — его удалит задача 11.

Мутации M9a–M9f.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: the card on screen, the honest refusal, the strings

**Spec:** §7.12.1 («значение „сгенерировано моделью“ в аргументе шага с эффектом `EXTERNAL`
показывается на карточке дословно»), §7.12.2 («модель недоступна: причина»; «уточнение… текст помечен
„сгенерировано“»; «видимое ожидание»), §7.12.3 condition 1; `DOC-NYH-2`, `DOC-HYA-2`, `DOC-MZN-2`.
**Modules:** `:domain`, `:feature:launcher`.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/CommandMessage.kt` — one variant;
  `Verbatim`'s KDoc.
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/InterpretationSurface.kt`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt`
  — `private fun AgentStepProvenance` → `internal fun AgentStepProvenance` (`:305`).
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherPresentation.kt` —
  one `when` arm and `offersProviderSetup`.
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt` — two
  collected flows, the two areas, the provider-button condition (`:814-815`).
- Modify: `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml`.
- Create: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/InterpretationSurfaceTest.kt` (6).
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherPresentationTest.kt` (+2).

**Interfaces.**
- Produces: `data class CommandMessage.UnderstandingModelUnavailable(val reason: ModelUnavailableReason)`;
  `internal fun CommandMessage.offersProviderSetup(): Boolean`;
  `@Composable internal fun InterpretationArea(interpretation, provenance: Map<ToolId, StepProvenance>,
  dynamicLabels: Map<ToolId, DynamicToolLabel>, onRun: () -> Unit, onDismiss: () -> Unit,
  onChoose: (String) -> Unit, modifier: Modifier = Modifier)`;
  `@Composable internal fun ThinkingArea(onCancel: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: write the failing tests.** `InterpretationSurfaceTest.kt`:

```kotlin
package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interpretation card (spec §7.12.3 condition 1; `DOC-NYH-2`, `DOC-HYA-2`, `DOC-MZN-2`), rendered
 * with real resources. Every expected sentence is whole and exact; the fixture values share no
 * substring with each other or with the sentences around them (R14-43).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterpretationSurfaceTest {

    @get:Rule val compose = createComposeRule()

    private var runs = 0
    private var dismissals = 0
    private val picks = mutableListOf<String>()

    private fun act(tool: String, arg: String, value: String, origin: ValueOrigin, choice: AppChoice? = null) =
        Interpretation.Act(
            goal = AgentGoal("fixture goal", GoalShape.Free("fixture goal")),
            step = PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolId(tool), mapOf(arg to ArgSource.Literal(value))),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
            origins = mapOf(arg to origin),
            effect = ToolEffect.EXTERNAL,
            choice = choice,
        )

    private fun render(interpretation: Interpretation) {
        compose.setContent {
            SidrTheme {
                InterpretationArea(
                    interpretation = interpretation,
                    provenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    onRun = { runs++ },
                    onDismiss = { dismissals++ },
                    onChoose = { picks += it },
                )
            }
        }
    }

    /** `DOC-NYH-2` and `DOC-HYA-2`: the reading is visible before anything runs, and only Run runs it. */
    @Test
    fun `the card names the step and runs nothing until Run`() {
        render(act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)))

        compose.onNodeWithText("Here is how I understood it").assertExists()
        compose.onNodeWithText("Search the web: ostravel lectures").assertExists()
        assertEquals(0, runs)

        compose.onNodeWithText("Run").performClick()
        assertEquals(1, runs)
    }

    /** `DOC-MZN-2`: an address the model wrote, on a step whose effect leaves the phone, is shown as written. */
    @Test
    fun `a model-generated address on an external step is shown verbatim, marked as the model's`() {
        render(act("open_url", "url", "https://quillmere.example/path?q=a&b=c", ValueOrigin.ModelGenerated))

        compose.onNodeWithText("Suggested by the model: https://quillmere.example/path?q=a&b=c").assertExists()
    }

    @Test
    fun `a value the user said carries no model mark`() {
        render(act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)))

        compose.onNodeWithText("Suggested by the model", substring = true).assertDoesNotExist()
    }

    /** Condition 2: the list names label AND package — two apps can share a label — and a tap binds that one. */
    @Test
    fun `the pick list shows each candidate's label and package, and a tap binds that one`() {
        val choice = AppChoice(
            arg = "app",
            mention = "pellix",
            candidates = listOf(
                InstalledApp(packageName = "net.fixture.quarzel", label = "Pellix"),
                InstalledApp(packageName = "net.fixture.ondrami", label = "Pellix"),
            ),
            learning = null,
        )
        render(act("launch_app", "query", "pellix", ValueOrigin.SaidBy(PrincipalId.USER), choice))

        compose.onNodeWithText("Which app is \"pellix\"?").assertExists()
        compose.onNodeWithText("Pellix (net.fixture.ondrami)").performClick()

        assertEquals(listOf("net.fixture.ondrami"), picks)
        assertEquals(0, runs)
    }

    @Test
    fun `the wait can be cancelled`() {
        var cancelled = 0
        compose.setContent { SidrTheme { ThinkingArea(onCancel = { cancelled++ }) } }

        compose.onNodeWithText("Working out what you meant…").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun `a navigation reading names Sidr's screen`() {
        render(Interpretation.Navigate(SidrDestination.LAUNCHER_SETTINGS))

        compose.onNodeWithText("Open Sidr settings").assertExists()
    }
}
```

(`SidrTheme`'s package and the core/ui `Cancel` label are those `AgentSessionSurfaceProvenanceTest` and
`SidrActionSafetyTest` use; if `ui_action_cancel` reads otherwise in `en`, use its text.)

`LauncherPresentationTest` — append:

```kotlin

    /** Finding (c) of the A1″ acceptance: each reason the model gave nothing usable is its own sentence. */
    @Test
    fun `each model-unavailable reason has its own sentence`() {
        val ids = ModelUnavailableReason.entries.map { reason ->
            feedbackText(CommandFeedback.Domain(CommandMessage.UnderstandingModelUnavailable(reason)))!!.id
        }
        assertEquals(ModelUnavailableReason.entries.size, ids.toSet().size)
    }

    /** The one affordance (Этап 4.0, F1) is offered exactly where the user can fix it in the provider screen. */
    @Test
    fun `the provider-setup affordance follows the reasons the user can fix there`() {
        assertTrue(CommandMessage.UnderstandingNeedsProvider.offersProviderSetup())
        assertTrue(CommandMessage.UnderstandingModelUnavailable(ModelUnavailableReason.CREDENTIALS).offersProviderSetup())
        assertTrue(CommandMessage.UnderstandingModelUnavailable(ModelUnavailableReason.REQUEST_REJECTED).offersProviderSetup())
        assertFalse(CommandMessage.UnderstandingModelUnavailable(ModelUnavailableReason.NETWORK).offersProviderSetup())
        assertFalse(CommandMessage.UnderstandingLocalOnly.offersProviderSetup())
    }
```

(`FeedbackText.id` is the resource id field the file's existing tests read; use the name they use.)

- [ ] **Step 2: run — expect compile-red.** `:feature:launcher` → `Unresolved reference 'InterpretationArea'`.

- [ ] **Step 3: implement.**

`CommandMessage.kt` — after `UnderstandingNeedsNetwork` add

```kotlin

    /**
     * A provider is configured and the device is online, but the model gave no usable answer, and
     * [reason] says why (A4′ phase 3a; finding (c) of the A1″ acceptance — a broken key, a refused model,
     * a dead endpoint and "the model answered prose" all used to read «Неизвестная команда»). A fourth
     * state beside the three above, and reached only past them: it comes from step 7 itself.
     */
    data class UnderstandingModelUnavailable(val reason: ModelUnavailableReason) : CommandMessage
```

and in `Verbatim`'s KDoc replace «today only the LLM router's clarify question» with «the bridge's one
clarify question (A4′ phase 3a) — model text, so the sentence around it says so».

`LauncherPresentation.kt` — in the `is CommandFeedback.Domain -> when (val m = feedback.message)` block
add, after the `UnderstandingNeedsNetwork` arm:

```kotlin
        is CommandMessage.UnderstandingModelUnavailable -> FeedbackText(
            when (m.reason) {
                ModelUnavailableReason.CREDENTIALS -> R.string.launcher_model_unavailable_credentials
                ModelUnavailableReason.REQUEST_REJECTED -> R.string.launcher_model_unavailable_request_rejected
                ModelUnavailableReason.PROVIDER_FAILING -> R.string.launcher_model_unavailable_provider_failing
                ModelUnavailableReason.TIMED_OUT -> R.string.launcher_model_unavailable_timed_out
                ModelUnavailableReason.NETWORK -> R.string.launcher_model_unavailable_network
                ModelUnavailableReason.UNREADABLE -> R.string.launcher_model_unavailable_unreadable
            },
        )
```

and at file level:

```kotlin

/**
 * Whether the line carries «Настроить провайдера» (Этап 4.0, F1): only where the provider screen can fix
 * the cause — no provider, a key the provider refused, a request it rejected (usually the model's name).
 */
internal fun CommandMessage.offersProviderSetup(): Boolean = when (this) {
    CommandMessage.UnderstandingNeedsProvider -> true
    is CommandMessage.UnderstandingModelUnavailable ->
        reason == ModelUnavailableReason.CREDENTIALS || reason == ModelUnavailableReason.REQUEST_REJECTED
    else -> false
}
```

`LauncherScreen.kt` — replace
`            feedback.message == CommandMessage.UnderstandingNeedsProvider -> Column(` with
`            feedback.message.offersProviderSetup() -> Column(`; collect the two flows beside
`pendingRoutedAction`:

```kotlin
    val interpretation by viewModel.interpretation.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
```

and right after the `CommandFeedbackArea(…)` call add:

```kotlin

            // A4′ phase 3a (spec §7.12.2): a command the model is reading shows the wait, with «Отмена».
            if (thinking) {
                ThinkingArea(onCancel = viewModel::cancelThinking)
            }

            // A4′ phase 3a (condition 1): the model's reading, before anything runs.
            interpretation?.let { reading ->
                InterpretationArea(
                    interpretation = reading,
                    provenance = viewModel.agentToolProvenance,
                    dynamicLabels = viewModel.agentDynamicToolLabels,
                    onRun = viewModel::runInterpretation,
                    onDismiss = viewModel::dismissInterpretation,
                    onChoose = viewModel::chooseInterpretationApp,
                )
            }
```

`AgentSessionSurface.kt:305` — `private fun AgentStepProvenance(` → `internal fun AgentStepProvenance(`.

`InterpretationSurface.kt`:

```kotlin
package com.sidr.launcher.feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrActionProposal
import com.sidr.launcher.core.ui.component.SidrActionProposalTone
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.feature.launcher.agent.AgentStepProvenance
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabel
import com.sidr.launcher.feature.launcher.agent.StepProvenance
import com.sidr.launcher.feature.launcher.agent.line
import com.sidr.launcher.feature.launcher.agent.subject
import com.sidr.launcher.core.ui.R as UiR

/**
 * The interpretation card (A4′ phase 3a, spec §7.12.3 condition 1): what the model understood, BEFORE
 * anything runs. Built on `SidrActionProposal` — one-shot, never executing on render (`DOC-HYA-2`'s two
 * anchors in `SidrActionSafetyTest`) — so no `core/ui` component is added. It asks "is this what you
 * meant", not "do you allow the consequence": a `CONFIRM` step still stops at the engine's own consent
 * card after «Выполнить» (`DOC-ADL-1` untouched).
 */
@Composable
internal fun InterpretationArea(
    interpretation: Interpretation,
    provenance: Map<ToolId, StepProvenance>,
    dynamicLabels: Map<ToolId, DynamicToolLabel>,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (interpretation) {
        is Interpretation.Navigate -> SidrActionProposal(
            title = sidrString(R.string.launcher_interpretation_title),
            description = sidrString(destinationLine(interpretation.destination)),
            tone = SidrActionProposalTone.Safe,
            onExecute = onRun,
            onCancel = onDismiss,
            executeLabel = sidrString(R.string.launcher_interpretation_run),
            modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        is Interpretation.Act -> {
            val choice = interpretation.choice
            if (choice != null) {
                AppChoiceList(choice = choice, onChoose = onChoose, onDismiss = onDismiss, modifier = modifier)
            } else {
                SidrActionProposal(
                    title = sidrString(R.string.launcher_interpretation_title),
                    description = interpretation.step.line(interpretation.goal.subject(), dynamicLabels),
                    tone = if (requiresConsent(interpretation.step.risk)) SidrActionProposalTone.Confirm else SidrActionProposalTone.Safe,
                    provenance = {
                        ModelValues(interpretation)
                        AgentStepProvenance(provenance[interpretation.step.invocation.id])
                    },
                    onExecute = onRun,
                    onCancel = onDismiss,
                    executeLabel = sidrString(R.string.launcher_interpretation_run),
                    modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
    }
}

/** «Разбираю команду…» with «Отмена» (spec §7.12.2, plan §0.2 B-10). */
@Composable
internal fun ThinkingArea(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SidrText(text = sidrString(R.string.launcher_thinking), role = SidrTextRole.PROVENANCE, modifier = Modifier.weight(1f))
        SidrText(
            text = sidrString(UiR.string.ui_action_cancel),
            role = SidrTextRole.HUMAN_BODY,
            color = SidrTheme.colors.accent,
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).clickable(onClick = onCancel).padding(Spacing.sm),
        )
    }
}

/**
 * Spec §7.12.1 / `DOC-MZN-2`: a value the MODEL produced, on a step whose effect leaves the phone (an
 * address, a search text), is shown as written. A value the user said needs no mark — it is theirs.
 */
@Composable
private fun ModelValues(interpretation: Interpretation.Act) {
    if (interpretation.effect != ToolEffect.EXTERNAL) return
    interpretation.origins
        .filterValues { it == ValueOrigin.ModelGenerated }
        .keys
        .mapNotNull { name -> (interpretation.step.invocation.args[name] as? ArgSource.Literal)?.value }
        .forEach { value ->
            SidrText(text = sidrString(R.string.launcher_interpretation_model_value, value), role = SidrTextRole.PROVENANCE)
        }
}

/** Condition 2: label AND package (two apps can share a label); a tap binds that one and launches nothing. */
@Composable
private fun AppChoiceList(choice: AppChoice, onChoose: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        SidrText(text = sidrString(R.string.launcher_interpretation_choose_app, choice.mention), role = SidrTextRole.HUMAN_TITLE)
        choice.candidates.forEach { app ->
            SidrText(
                text = sidrString(R.string.launcher_interpretation_candidate, app.label, app.packageName),
                role = SidrTextRole.HUMAN_BODY,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouchTarget)
                    .clickable { onChoose(app.packageName) }
                    .padding(vertical = Spacing.sm),
            )
        }
        SidrText(
            text = sidrString(UiR.string.ui_action_cancel),
            role = SidrTextRole.HUMAN_BODY,
            color = SidrTheme.colors.accent,
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).clickable(onClick = onDismiss).padding(vertical = Spacing.sm),
        )
    }
}

private fun destinationLine(destination: SidrDestination): Int = when (destination) {
    SidrDestination.LAUNCHER_SETTINGS -> R.string.launcher_interpretation_open_settings
    SidrDestination.APP_GRID -> R.string.launcher_interpretation_show_apps
}
```

(The import paths of `sidrString`, `SidrText`, `SidrTheme`, `Sizes`, `Spacing` are those
`LauncherScreen.kt` already uses; copy them from there if any differs.)

Strings — new keys (a block «A4′ phase 3a — the interpretation card» after the `launcher_understanding_*`
group), and one changed value:

| key | `values` | `values-ru` | `values-tr` |
|---|---|---|---|
| `launcher_interpretation_title` | `Here is how I understood it` | `Понял так` | `Şöyle anladım` |
| `launcher_interpretation_run` | `Run` | `Выполнить` | `Çalıştır` |
| `launcher_interpretation_model_value` | `Suggested by the model: %1$s` | `Предложено моделью: %1$s` | `Modelin önerisi: %1$s` |
| `launcher_interpretation_open_settings` | `Open Sidr settings` | `Открыть настройки Sidr` | `Sidr ayarlarını aç` |
| `launcher_interpretation_show_apps` | `Show all apps` | `Показать все приложения` | `Tüm uygulamaları göster` |
| `launcher_interpretation_choose_app` | `Which app is \"%1$s\"?` | `Какое приложение — «%1$s»?` | `\"%1$s\" hangi uygulama?` |
| `launcher_interpretation_candidate` | `%1$s (%2$s)` | `%1$s (%2$s)` | `%1$s (%2$s)` |
| `launcher_thinking` | `Working out what you meant…` | `Разбираю команду…` | `Ne demek istediğinizi anlamaya çalışıyorum…` |
| `launcher_model_unavailable_credentials` | `The AI provider key is missing or was not accepted.` | `Ключ ИИ-провайдера не найден или не принят.` | `Yapay zekâ sağlayıcısının anahtarı bulunamadı veya kabul edilmedi.` |
| `launcher_model_unavailable_request_rejected` | `The AI provider refused the request. Check the provider settings.` | `ИИ-провайдер отклонил запрос. Проверьте настройки провайдера.` | `Yapay zekâ sağlayıcısı isteği reddetti. Sağlayıcı ayarlarını kontrol edin.` |
| `launcher_model_unavailable_provider_failing` | `The AI provider is busy or failing. Try again later.` | `ИИ-провайдер перегружен или сбоит. Попробуйте позже.` | `Yapay zekâ sağlayıcısı meşgul ya da hata veriyor. Daha sonra tekrar deneyin.` |
| `launcher_model_unavailable_timed_out` | `The AI provider did not answer in time.` | `ИИ-провайдер не ответил вовремя.` | `Yapay zekâ sağlayıcısı zamanında yanıt vermedi.` |
| `launcher_model_unavailable_network` | `Could not reach the AI provider.` | `Не удалось связаться с провайдером ИИ.` | `Yapay zekâ sağlayıcısına ulaşılamadı.` |
| `launcher_model_unavailable_unreadable` | `The model's answer could not be read.` | `Не удалось прочитать ответ модели.` | `Modelin yanıtı okunamadı.` |
| `launcher_message_verbatim` (**value changed**) | `The model asks: %1$s` | `Модель спрашивает: %1$s` | `Model soruyor: %1$s` |

(None is Class B; `strings_locked.xml` is not touched; the locale signature is not re-issued. The
`launcher_model_unavailable_*` lines follow `launcher_understanding_*` in tone: what happened, and where
the fix is, with no pressure — `DOC-HYA-1`. `request_rejected` names the provider *settings*, not the
model name: `AiError.InvalidRequest` is also what a non-`https` base URL and a 404 endpoint fold into
(Task 6, `statusError`), so «check the model name» would send some users to the wrong field — plan
review. The reason a refused request cannot be told apart further is the provider's error body, which
the transport deliberately does not read into a user string.)

- [ ] **Step 4: run — expect green.** `:feature:launcher` → `tests=238`; `:domain` unchanged (527); `:app`
  unchanged (68) — `LocaleCompletenessGuardTest`, `HardcodedUiTextGuardTest`, `StringSeamGuardTest`,
  `DomainIdentifierLeakGuardTest` stay green.

- [ ] **Step 5: mutations — four.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M10a | `InterpretationSurface.kt` · `        is Interpretation.Act -> {\n            val choice = interpretation.choice\n` → `        is Interpretation.Act -> {\n            androidx.compose.runtime.LaunchedEffect(Unit) { onRun() }\n            val choice = interpretation.choice\n` | `DOC-HYA-2`: nothing runs on render | `:feature:launcher` — `InterpretationSurfaceTest > the card names the step and runs nothing until Run`, `> the pick list shows each candidate's label and package, and a tap binds that one` |
| M10b | `InterpretationSurface.kt` · `.filterValues { it == ValueOrigin.ModelGenerated }` → `.filterValues { false }` | `DOC-MZN-2` | `:feature:launcher` — `> a model-generated address on an external step is shown verbatim, marked as the model's` **only** |
| M10c | `LauncherPresentation.kt` · `ModelUnavailableReason.CREDENTIALS -> R.string.launcher_model_unavailable_credentials` → `ModelUnavailableReason.CREDENTIALS -> R.string.launcher_model_unavailable_network` | each reason is said as itself | `:feature:launcher` — `LauncherPresentationTest > each model-unavailable reason has its own sentence` **only** |
| M10d | `LauncherPresentation.kt` · `reason == ModelUnavailableReason.CREDENTIALS \|\| reason == ModelUnavailableReason.REQUEST_REJECTED` → `reason == ModelUnavailableReason.CREDENTIALS` | the affordance follows the fixable reasons | `:feature:launcher` — `> the provider-setup affordance follows the reasons the user can fix there` **only** |

- [ ] **Step 6: boundary gate from a cleared tree.** Expected `GATE GREEN`, `TOTAL tests=1670`:
  `:feature:launcher` **238** (+8).

> **Measured in execution (2026-10-04/05) — T10: 1691** = plan 1670 + 10 carried + 11 (controller rulings on the
> implementer's holes: seven `InterpretationSurfaceTest` tests — Cancel on the card and on the pick list, Run on a
> navigation card, `APP_GRID`, no model mark on a package bound by a pick (`ValueOrigin.Ours`), the `CONFIRM` tone, the
> provenance line — and a new `LauncherScreenInterpretationTest`, four tests: a card on the real screen and its Run
> starts the session, the wait drawn and cancelled, a refused/missing key with its button, the «The model asks:» mark).
> `LauncherPresentationTest`'s two tests became total over the enum (map / set equality). Fixture `pellix` → `kelmora`
> (R14-43: «Pellix»/«pellix» shared a substring). **M10a–M10d PROVED; M10a's red set is five** (+ the two Cancel tests and
> the screen test), **M10c's two** (+ the screen test). Thirteen more plants M10e–M10q, each reddening exactly its own
> test. Task review I1 (`75fb672`): «The AI provider did not accept the key» was untrue for `MissingCredentials`, which
> `CREDENTIALS` also covers (no config, or a Keystore key that cannot be read — no request ever sent) — reworded to
> «missing or was not accepted» in three locales, no new enum value; ru now says «ИИ-провайдер» on the whole line.
> → T11 **1665** (plan 1644 + 21).

- [ ] **Step 7: commit (controller).**

```bash
git add domain/src feature/launcher/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): карточка толкования на экране, честный отказ модели, строки

Условие 1 D3, §7.12.1–§7.12.2. Карточка на SidrActionProposal (без новых
компонентов core/ui): строка шага, провенанс, значение модели на шаге
EXTERNAL — дословно с пометкой (DOC-MZN-2). Список выбора — метка и пакет,
касание привязывает. Ожидание с «Отмена». «Модель недоступна: причина» —
шесть причин, кнопка настройки провайдера там, где это лечится. Уточнение
модели помечено как её вопрос. en/ru/tr; новые tr — на суд владельца.

Мутации M10a–M10d.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: one model path — the switch, the deletion, the payload guard, two ADRs

**Spec:** §7.12.3 in full (D3; «Нагрузка и `ALLOWED`»; «Гварды и тесты, которые удаление роутера
сломает»; «ADR фазы 3a»); §7.12.6 criteria 14, 15 (route half), 18 (payload half), 21; change-control
§5; plan §0.2 B-4, §0.9. Owner fork F3a-B. **Modules:** all but `:consumer:jvm` and `:core:ui`, plus
`docs/`, `ai-context/`.

This is the one task that deletes. Its order inside is fixed: switch the route, migrate every caller,
delete, then the guards and the documents — and **the per-module prediction below, not the floor,
watches the forty-one deletions** (B-8).

**Files.**
- **Delete (production):** `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/CommandPlanner.kt`,
  `.../router/ActionProposal.kt`, `.../router/CatalogSchemaRenderer.kt`, `.../router/ProposalValidator.kt`;
  `data/ai-cloud/src/main/java/com/sidr/launcher/data/aicloud/LlmCommandPlanner.kt`;
  `app/src/main/java/com/sidr/launcher/di/RouterProvidesModule.kt`;
  `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeCommandPlanner.kt`;
  `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/PendingRoutedAction.kt`.
- **Delete (tests):** `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/ProposalValidatorTest.kt`,
  `.../router/RouterOutboundGuardTest.kt`; `data/ai-cloud/src/test/java/com/sidr/launcher/data/aicloud/LlmCommandPlannerTest.kt`;
  `data/repository/src/test/java/com/sidr/launcher/data/repository/action/DefaultActionCatalogRouterSchemaGuardTest.kt`.
- **Create:** `app/src/main/java/com/sidr/launcher/di/BridgeProvidesModule.kt`;
  `app/src/test/java/com/sidr/launcher/agent/BridgePayloadGuardTest.kt` (5),
  `.../agent/OneModelPathGuardTest.kt` (2), `.../agent/OfferedToolIdsFreezeGuardTest.kt` (1, under F3a-B (a)).
- **Modify (production):** `RouteCommandUseCase.kt` (constructor, step 7, KDoc); `CommandOutcome.kt`
  (`RoutedAction` removed); `OutboundContextPolicy.kt` (`:21-36`); `LauncherCommandSession.kt`,
  `LauncherViewModel.kt`, `LauncherScreen.kt` (the router's card and its state); `strings.xml` ×3 (five
  keys removed); `app/build.gradle.kts` (two `testImplementation` lines); KDoc only: `ExecutionPlan.kt:120`
  (`Planner`), `CompositePlanner.kt:5-6`, `StartAgentSessionUseCase.kt:13-18`, `IntentMatcher.kt:20`,
  `LauncherAction.kt:5`, `InvocationValidator.kt:35`, `ExecuteActionUseCase.kt:8-21`,
  `IntentProvidesModule.kt:33-36,52-55`.
- **Modify (tests):** `RouteCommandUseCaseTest` (migrate, −2, +6), `AgentEgressSentinelGuardTest`,
  `FastPathOverflowSeamTest`, `AgentActingSeamTest`, `Tier0ToolExecutionEndToEndTest`,
  `FreeTextGoalEndToEndTest`, `LauncherViewModelTest` (migrate, −5, +1), `LauncherScreenPrayerStripTest`,
  `LauncherScreenAgentProvenanceTest`; the five `ALLOWED` pins (`AiRequestGuardTest`,
  `SuggestionOutboundIsolationTest`, `AliasPrivacyScopeGuardTest`, `ResolutionPrivacyScopeGuardTest`,
  `PrayerLocationPrivacyGuardTest`); the three privacy guards' term lists; `AgentVocabularyGuardTest`
  (term list, KDoc); KDoc only: `HardcodedUiTextGuardTest.kt:82`, `InvocationValidatorTest.kt:11`.
- **Modify (documents):** `docs/governing/sidr-doctrine-matrix-v1.0.md` (cells and journal),
  `ai-context/decisions.md` (two ADRs).

**Interfaces.**
- Produces:
  ```kotlin
  class RouteCommandUseCase(
      handleUserCommand: HandleUserCommandUseCase, modelPlanner: ModelPlanner, binder: MentionBinder,
      featureFlagRepository: FeatureFlagRepository, providerConfigRepository: AiProviderConfigRepository,
      connectivityChecker: ConnectivityChecker, startAgentSession: StartAgentSessionUseCase,
  )
  enum class OutboundContextPolicy.AllowedContext { USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS, AUTHORED_TOOL_SCHEMA }
  object BridgeProvidesModule {   // :app, Hilt
      val DEFAULT_TRANSPORT_MODE: BridgeTransportMode          // PROMPT_ONLY until Task 12
      fun provideBridgeTransport(secretStore, configRepository, @IoDispatcher io): BridgeTransport   // its own client
      fun provideToolDescriptions(impl: AuthoredToolDescriptions): ToolDescriptions
      fun provideModelPlanner(registry: ToolRegistry, descriptions: ToolDescriptions, transport: BridgeTransport): ModelPlanner
      fun provideMentionBinder(impl: DeviceMentionBinder): MentionBinder
      fun provideRouteCommandUseCase(…same seven as the constructor…): RouteCommandUseCase
  }
  ```
- `LauncherViewModel` loses `actionCatalog`, `pendingRoutedAction`, `confirmRoutedAction`,
  `cancelRoutedAction`; `LauncherCommandSession` loses `actionCatalog`, `pendingRoutedAction`, `confirm`,
  `cancel`.

- [ ] **Step 1: switch the route.** `RouteCommandUseCase.kt` — replace the constructor parameters
  `private val planner: CommandPlanner,` and `private val catalog: ActionCatalog,` with

```kotlin
    // A4′ phase 3a (D3): the ONE model path. Not `Planner` — that port is asked at step 2b and on resume,
    // where a model may never be called (`DOC-ADL-3`). This one is asked here, at step 7, only.
    private val modelPlanner: ModelPlanner,
    // Binding on the device, AFTER the model has answered: nothing it finds goes back to the model (D1).
    private val binder: MentionBinder,
```

replace step (7) — from `        // (7) Consult the planner and map its structured decision;` to the closing
`        }` of that `when` — with

```kotlin
        // (7) The one model path (A4′ phase 3a, D3). The model reads the command and our nineteen tools'
        // descriptions — nothing of this phone (D1). The device then binds the model's words to this
        // phone, and NOTHING the binding finds is sent back: a pick, a "not found" go to the user. And
        // nothing here runs anything (condition 1): a reading becomes a card, and only the user's
        // «Выполнить» starts a session, through the engine.
        val command = rawInput.trim()
        return when (val answer = modelPlanner.plan(command)) {
            is ModelPlanning.Proposed ->
                when (val bound = binder.bind(AgentGoal(text = command, shape = GoalShape.Free(command)), answer.step)) {
                    is Binding.Bound -> CommandOutcome.Interpreted(bound.interpretation)
                    is Binding.NotFound -> CommandOutcome.Message(CommandMessage.NoAppFound(bound.mention))
                    Binding.Unavailable -> CommandOutcome.Failed(CommandFailure.Generic)
                }
            // Condition 3: a question goes to the assistant, prefilled and unsent — never to a web search.
            is ModelPlanning.ToAssistant -> CommandOutcome.OpenAssistant(answer.question)
            is ModelPlanning.Navigate -> CommandOutcome.Interpreted(Interpretation.Navigate(answer.destination))
            is ModelPlanning.Clarify -> CommandOutcome.Message(CommandMessage.Verbatim(answer.question))
            ModelPlanning.Declined -> ruleOutcome
            // Finding (c) of the A1″ acceptance: a broken key or a dead endpoint is said, not hidden.
            is ModelPlanning.Unavailable -> CommandOutcome.Message(CommandMessage.UnderstandingModelUnavailable(answer.reason))
        }
```

delete `needsConfirmation` (`:195-201`) and the imports it leaves unused (`ActionCatalog`,
`ActionRiskLevel`, `requiresConsent`); in the class KDoc replace item 7 («Map the plan: a
[PlanResult.RoutedAction] surfaces as a **non-executing** proposal…») with «7. **The one model path**
(A4′ phase 3a, D3): ask the [ModelPlanner]; bind its step on the device ([MentionBinder]); hand back a
reading for the card ([CommandOutcome.Interpreted]), the assistant with the question, a verbatim clarify,
an honest «model unavailable», or — when the model declines — FastPath's outcome untouched. Nothing here
executes (`DOC-NYH-1`, Fork R4); the model is below gates 3, 5 and 6 (`DOC-ADL-3`).» Add `CommandFailure`,
`AgentGoal`, `GoalShape` (already), `Binding`, `Interpretation`, `MentionBinder`, `ModelPlanner`,
`ModelPlanning` imports.

`CommandOutcome.kt` — delete `RoutedAction` (`:59-71`) and the `LauncherAction` import it leaves unused.

- [ ] **Step 2: wire it.** Create `BridgeProvidesModule.kt`:

```kotlin
package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.aicloud.bridgeHttpClient
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.DeviceMentionBinder
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.bridge.BridgeBudget
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.security.SecureSecretStore
import com.sidr.launcher.domain.tool.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * The bridge (A4′ phase 3a, D1–D3) — replaces `RouterProvidesModule`. One model path: the
 * [ModelPlanner] over the federation's offered tools and our descriptions, its own transport over its
 * own HTTP client (socket bound = the ceiling), the device-side [MentionBinder], and [RouteCommandUseCase] asking the
 * planner at step 7 only. Lazy singletons; nothing here is on the launcher's cold path.
 *
 * The model planner is NOT in `AgentProvidesModule`'s `CompositePlanner` list — that list is what step
 * 2b and resume ask, and `DoctrineGuardTest > the composition root's planner list matches the declared
 * planners` keeps it at the two deterministic planners (ADR 2).
 */
@Module
@InstallIn(SingletonComponent::class)
object BridgeProvidesModule {

    /** The transport form (D2, b′). The prompt form until the owner's measurement chooses (Task 12). */
    val DEFAULT_TRANSPORT_MODE: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY

    @Provides
    @Singleton
    fun provideBridgeTransport(
        secretStore: SecureSecretStore,
        configRepository: AiProviderConfigRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BridgeTransport = OpenAiCompatibleBridgeTransport(
        // Not the shared cloud client: its 30 s socket would end a silent reasoning model's wait before
        // the ceiling does. Each attempt's socket is bounded by the ceiling; the planner bounds the whole.
        bridgeHttpClient(BridgeBudget.Default.ceilingMs),
        secretStore,
        configRepository,
        ioDispatcher,
        DEFAULT_TRANSPORT_MODE,
    )

    @Provides
    @Singleton
    fun provideToolDescriptions(impl: AuthoredToolDescriptions): ToolDescriptions = impl

    @Provides
    @Singleton
    fun provideModelPlanner(
        registry: ToolRegistry,
        descriptions: ToolDescriptions,
        transport: BridgeTransport,
    ): ModelPlanner = BridgeModelPlanner(registry, descriptions, transport, BridgeBudget.Default)

    @Provides
    @Singleton
    fun provideMentionBinder(impl: DeviceMentionBinder): MentionBinder = impl

    @Provides
    @Singleton
    fun provideRouteCommandUseCase(
        handleUserCommand: HandleUserCommandUseCase,
        modelPlanner: ModelPlanner,
        binder: MentionBinder,
        featureFlagRepository: FeatureFlagRepository,
        providerConfigRepository: AiProviderConfigRepository,
        connectivityChecker: ConnectivityChecker,
        startAgentSession: StartAgentSessionUseCase,
    ): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = handleUserCommand,
        modelPlanner = modelPlanner,
        binder = binder,
        featureFlagRepository = featureFlagRepository,
        providerConfigRepository = providerConfigRepository,
        connectivityChecker = connectivityChecker,
        startAgentSession = startAgentSession,
    )
}
```

and delete `RouterProvidesModule.kt`.

- [ ] **Step 3: the router's card leaves the screen.**
  - `LauncherCommandSession.kt`: delete `actionCatalog` (constructor), the `_pendingRoutedAction` pair,
    its two resets (`onChanged`, `applyOutcome`), the `RoutedAction` arms of `outcomeSummary` and
    `applyOutcome`, `confirm()`, `cancel()`, and `RISK_CONFIRM_LABEL`. `commandLineFor` stays (the web
    chip). Fix the class KDoc's mentions of «the AIL-5 pending router proposal».
  - `LauncherViewModel.kt`: delete the `actionCatalog` constructor parameter (`:76-79`) and its pass-through,
    `pendingRoutedAction` (`:343`), `confirmRoutedAction`, `cancelRoutedAction` (`:454-462`).
  - `LauncherScreen.kt`: delete `val pendingRoutedAction by …` (`:115`), the `pendingRoutedAction?.let`
    block (`:235-253`) with its comment, and `PendingActionArea` (`:726-764`) with its section comment;
    drop the imports that become unused.
  - `strings.xml` ×3: delete `launcher_confirm_execute_title`, `launcher_confirm_will_run`,
    `launcher_confirm_confirm`, `launcher_safe_proposal_description`, `launcher_safe_proposal_run` and
    their comment.
  - Delete `PendingRoutedAction.kt`.

- [ ] **Step 4: migrate every caller of the router's fake** (P3). In each of the nine code files, the
  substitutions are mechanical:

| was | becomes |
|---|---|
| `import com.sidr.launcher.core.testing.FakeCommandPlanner` | `import com.sidr.launcher.core.testing.FakeModelPlanner` (+ `FakeMentionBinder`) |
| `FakeCommandPlanner()` / `FakeCommandPlanner(resultToReturn = X)` | `FakeModelPlanner()` / `FakeModelPlanner(answer = X′)` |
| `planner = <p>,` and `catalog = <c>,` in a `RouteCommandUseCase(` call | `modelPlanner = <p>,` and `binder = FakeMentionBinder(),` |
| `.resultToReturn = PlanResult.RoutedAction(…)` | `.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)` — a planner that WOULD say something, so "not asked" stays non-vacuous |
| `.resultToReturn = PlanResult.NoPlan` | `.answer = ModelPlanning.Declined` |
| `.resultToReturn = PlanResult.Clarify(q)` | `.answer = ModelPlanning.Clarify(q)` |
| `planCallCount`, `lastCommand` | unchanged — `FakeModelPlanner` keeps the names |

  Then, file by file, what is NOT mechanical:
  - **`RouteCommandUseCaseTest`.** Delete `CONFIRM-risk proposal surfaces as needsConfirmation
    RoutedAction (R4)` and `SAFE proposal surfaces as one-tap RoutedAction, still not executed (R4)`;
    rename `planner returns NoPlan - keeps the FastPath outcome` to `the model declines - keeps the
    FastPath outcome`; delete the `catalog` fixture and its imports; in the class KDoc, `DOC-NYH-1`'s
    paragraph now reads «a model reading surfaces only as a non-executing [CommandOutcome.Interpreted]».
    Add `private val binder = FakeMentionBinder()` and pass it. Append the six tests (the sixth added
    by the plan review — criterion 15's other half):

```kotlin

    // --- A4′ phase 3a: the one model path (spec §7.12.3) ----------------------------------------

    /** Criterion 15, the route half: the model's reading becomes a card; NO session is started. */
    @Test
    fun `a model plan becomes an interpretation, and no session is started`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.PLAY_STORE_SEARCH,
                args = mapOf("query" to "lumora"),
                origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.CONFIRM,
                effect = ToolEffect.EXTERNAL,
            ),
        )

        val outcome = useCase().route("найди lumora в магазине")

        val act = (outcome as CommandOutcome.Interpreted).interpretation as Interpretation.Act
        assertEquals(ToolIds.PLAY_STORE_SEARCH, act.step.invocation.id)
        assertEquals("найди lumora в магазине", act.goal.text)
        assertTrue("nothing is started before «Выполнить»", agentStore.saved.isEmpty())
    }

    @Test
    fun `an assistant answer opens the assistant with the question`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.ToAssistant("почему небо синее")

        assertEquals(CommandOutcome.OpenAssistant("почему небо синее"), useCase().route("почему небо синее"))
    }

    @Test
    fun `a navigation answer is shown before it opens anything`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        assertEquals(
            CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.APP_GRID)),
            useCase().route("покажи все приложения"),
        )
    }

    @Test
    fun `an unavailable model is said so, with its reason`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Unavailable(ModelUnavailableReason.CREDENTIALS)

        assertEquals(
            CommandOutcome.Message(CommandMessage.UnderstandingModelUnavailable(ModelUnavailableReason.CREDENTIALS)),
            useCase().route("сделай что-нибудь"),
        )
    }

    /**
     * Criterion 15, the other half (plan review): the deterministic paths get NO card. The model here
     * WOULD answer with a step, so "it was not asked" is not vacuous — FastPath's decision, A0's plan and
     * step 2b's tool match each return before step 7, and none of them waits on «Выполнить».
     */
    @Test
    fun `the deterministic paths never get an interpretation card`() = runTest {
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.PLAY_STORE_SEARCH,
                args = mapOf("query" to "lumora"),
                origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.CONFIRM,
                effect = ToolEffect.EXTERNAL,
            ),
        )

        driveConfidentShowApps()
        val fastPath = useCase().route("apps")

        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()
        val a0 = useCase().route("открой убер")

        driveUnknown()
        val toolMatch = useCase(agentPlanner = plansOneStep()).route("do a barrel roll")

        assertEquals(CommandOutcome.ShowApps, fastPath)
        assertTrue("A0: $a0", a0 is CommandOutcome.AgentSessionStarted)
        assertTrue("2b: $toolMatch", toolMatch is CommandOutcome.AgentSessionStarted)
        assertEquals(0, planner.planCallCount)
    }

    /** Review Focus 5, D1: what binding found goes to the user — the model is asked exactly once. */
    @Test
    fun `a mention that matches no app says so and asks the model nothing more`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.LAUNCH_APP,
                args = mapOf("app" to "телегу"),
                origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.SAFE,
                effect = ToolEffect.EXTERNAL,
            ),
        )
        binder.binding = { _, _ -> Binding.NotFound("телегу") }

        val outcome = useCase().route("открой телегу")

        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("телегу")), outcome)
        assertEquals(1, planner.planCallCount)
    }
```

  - **`AgentEgressSentinelGuardTest`.** Its non-vacuity control (`:224`) answers
    `ModelPlanning.Navigate(SidrDestination.APP_GRID)`; its KDoc's `FakeCommandPlanner` → `FakeModelPlanner`.
  - **`FastPathOverflowSeamTest`** (`DOC-NYH-4`), **`AgentActingSeamTest`** (R14-39 — its KDoc keeps
    saying the leak is named, not closed; spec §7.12.3), **`Tier0ToolExecutionEndToEndTest`**,
    **`FreeTextGoalEndToEndTest`**: mechanical only.
  - **`LauncherViewModelTest`.** Delete the five router tests (`:384`, `:402`, `:421`, `:442`, `:459`).
    `buildRouterViewModel(plannerResult: PlanResult, catalog, online, resolutionStore)` becomes
    `buildBridgeViewModel(answer: ModelPlanning, online: Boolean = true, resolutionStore: … = fakeResolutionStore)`
    over `RouteCommandUseCase(… modelPlanner = FakeModelPlanner(answer), binder = FakeMentionBinder(), …)`;
    every remaining caller of the old builder moves to the new one by the table above. The class-level
    `routeUseCase` (`:205-213`) and the local-only route of Task 8 migrate by the table. Remove
    `actionCatalog = …` from every `LauncherViewModel(` construction. Append:

```kotlin

    /** End to end through the real route: the model's reading waits on the card; «Выполнить» starts it. */
    @Test
    fun `a model plan through the real route waits on the card, and Run starts an engine session`() = runTest(testDispatcher) {
        val vm = buildBridgeViewModel(
            answer = ModelPlanning.Proposed(
                ProposedStep(
                    tool = ToolIds.PLAY_STORE_SEARCH,
                    args = mapOf("query" to "lumora"),
                    origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.CONFIRM,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()
        assertTrue(vm.interpretation.value is Interpretation.Act)
        assertTrue("nothing started before «Выполнить»", fakeAgentStore.saved.isEmpty())

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
    }
```

  - **`LauncherScreenPrayerStripTest`, `LauncherScreenAgentProvenanceTest`:** the table; remove
    `actionCatalog = FakeActionCatalog(),` from the `LauncherViewModel(` call.

- [ ] **Step 5: delete the router.** `git rm` the eight production files and the four test files listed
  under **Files**. Then the KDoc that the deletion makes false (§0.9) — each replaced, not deleted:
  - `ExecutionPlan.kt` `Planner`'s KDoc: «Port. A0 binds the deterministic `TemplatePlanner`; A4' binds a
    model planner behind the same seam.» → «Port of the DETERMINISTIC planners, asked at step 2b and on
    resume — where no model may be called (`DOC-ADL-3`). The model planner is a different port,
    `com.sidr.launcher.domain.ai.bridge.ModelPlanner`, asked at step 7 only (A4′ phase 3a, spec §7.12.2).»
  - `CompositePlanner.kt:5-6`: «…the shape that stops growing when A4' adds the model planner behind this
    same port.» → «…the shape that grows by a planner, not by a branch. The model planner is NOT one of
    them — it is its own port, at step 7 (A4′ phase 3a).»
  - `StartAgentSessionUseCase.kt:13-18`: «…but the port exists so A4' can bind a model planner behind it…»
    → «…and since A4′ phase 3a the model's plans arrive here too, through `startPlanned` from the
    interpretation card — validated against the whole federation, the engine's second line behind the
    bridge's validator.»
  - `IntentMatcher.kt:20`, `LauncherAction.kt:5`, `InvocationValidator.kt:35`, `ExecuteActionUseCase.kt:8-21`,
    `IntentProvidesModule.kt:33-36,52-55`, `HardcodedUiTextGuardTest.kt:82`, `InvocationValidatorTest.kt:11`,
    `SuggestionOutboundIsolationTest.kt:15-17`: each names the router as current; each now says what holds
    (the bridge; `ExecuteActionUseCase` serves the tool worker and the web chip; `InvocationValidator`'s
    model-side twin is `BridgeValidator`).

- [ ] **Step 6: `ALLOWED`, its six pins, and the guards' names** — together, with ADR 1 (Step 9).
  `OutboundContextPolicy.kt` — replace

```kotlin
        /**
         * The static Action Registry tool schema the AIL-4 router sends alongside the user command
         ...
         */
        ACTION_CATALOG_SCHEMA,
```

  (the whole KDoc and the value) with

```kotlin
        /**
         * Our descriptions of the AUTHORED tools the bridge offers, and the JSON schema of the answer
         * made from the same offering (A4′ phase 3a, ADR 2026-10 «ACTION_CATALOG_SCHEMA → AUTHORED_TOOL_SCHEMA»;
         * change-control §5). Our literals only — tool ids, our sentences, argument names — never a
         * third party's text: the dynamic tier and the launcher's memory are not offered
         * (`BridgeFilter`), and nothing the device knows (apps, aliases, learned choices, prayer,
         * calendar, usage) has a path here (`BridgePayloadGuardTest`, which reads the PAYLOAD). It
         * REPLACES the router's `ACTION_CATALOG_SCHEMA`, whose producer is gone — the set stays four.
         */
        AUTHORED_TOOL_SCHEMA,
```

  and `AllowedContext.ACTION_CATALOG_SCHEMA,` in `ALLOWED` with `AllowedContext.AUTHORED_TOOL_SCHEMA,`.
  In the five remaining pins (`AiRequestGuardTest:30`, `SuggestionOutboundIsolationTest:28`,
  `AliasPrivacyScopeGuardTest:23`, `ResolutionPrivacyScopeGuardTest:28`, `PrayerLocationPrivacyGuardTest:55`)
  replace `AllowedContext.ACTION_CATALOG_SCHEMA` with `AllowedContext.AUTHORED_TOOL_SCHEMA` and the
  comments beside them («AIL-4 added exactly one … ACTION_CATALOG_SCHEMA (the router's tool schema)») with
  «A4′ phase 3a replaced the router's schema category with AUTHORED_TOOL_SCHEMA (ADR 1 of the phase)».
  Rename `SuggestionOutboundIsolationTest`'s first test `allow-list adds only the AIL-4 schema category,
  never a suggestion-context category` → `allow-list holds only the authored tool schema beside the
  basics, never a suggestion-context category`.
  The three privacy guards' term lists: delete `"CommandPlanner",` and `"CatalogSchemaRenderer",` (the
  bridge's names added in Task 3 and `"com.sidr.launcher.domain.ai"` stay). `AgentVocabularyGuardTest`:
  delete `"CommandPlanner",` from `forbidden` and its mention in the KDoc; the comment-only samples in
  `the scan catches an import and a fully qualified reference alike` may keep the word — they are
  comments by design.

- [ ] **Step 7: the three new guards.** `app/build.gradle.kts`, after `testImplementation(libs.coroutines.test)`:

```kotlin
    // A4′ phase 3a: the bridge's payload guard drives the real transport over a MockEngine and reads
    // the JSON it sent. Test-only.
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.serialization.json)
```

  `BridgePayloadGuardTest.kt`:

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.repository.agent.AppTargetResolver
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.DeviceMentionBinder
import com.sidr.launcher.data.repository.agent.ToolArgumentSorts
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.di.BridgeProvidesModule
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import com.sidr.launcher.domain.ai.bridge.BridgeError
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.tool.ArgSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge's egress, read off the PAYLOAD (spec §7.12.3; criterion 21; `DOC-AML-1`) — §7.1's sixth
 * barrier: the router's guard scanned our inventories, not what left the phone. This captures every body
 * the real transport sends through the real route and reads it.
 *
 * The graph is built by `BridgeProvidesModule`'s own providers over the production federation (a
 * NON-empty shortcut source carrying sentinels), our real descriptions, the real transport over a
 * MockEngine, and the real binder over apps, aliases and learned choices carrying sentinels. FastPath is
 * forced undecided, so every command reaches step 7. The mock REJECTS every first answer (a registered
 * tool outside the offering, carrying an echo sentinel), so every command also produces a retry body,
 * and accepts the second.
 *
 * Seven private sources (spec §7.12.3 (1)), in two groups — said, not blurred (plan §0.5 S5):
 *  - four the bridge graph DOES read — the dynamic tier (registry), installed apps, the alias table,
 *    learned choices (binder): planted, asserted absent from every body, each with a control proving it
 *    was read on the device;
 *  - three it holds NO port to — prayer location, calendar, usage history: asserted absent, and held
 *    STRUCTURALLY by `the bridge's classes take no port to prayer, calendar or usage data`.
 */
class BridgePayloadGuardTest {

    private val provider = AiProviderId("payload-guard")
    private val keySentinel = "sk-SNTL-KEY"
    private val echoSentinel = "SNTL-REJECTED-ECHO"

    private val sources: Map<String, List<String>> = mapOf(
        "dynamic tier" to listOf("net.fixture.qorvath", "Qorvath", "Brimwell"),
        "installed apps" to listOf("net.fixture.lumora", "net.fixture.vemblic", "Vemblic", "net.fixture.pellix.one", "net.fixture.pellix.two"),
        "alias table" to listOf("net.fixture.zantor", "Zantor", "плимдорф", "net.fixture.plimdorf"),
        "learned choices" to listOf("net.fixture.tessar.one", "net.fixture.tessar.two", "фальмира", "net.fixture.falmira"),
        "prayer location (no port)" to listOf("Ostrevik"),
        "calendar (no port)" to listOf("Halvorn standup"),
        "usage history (no port)" to listOf("net.fixture.usagelog"),
        "the rejected answer" to listOf(echoSentinel),
        "the key" to listOf(keySentinel),
    )

    /** Each names ONE app the way a user types it; the binder resolves it on the device. */
    private val commands = listOf("давай lumora", "давай кварцелот", "давай tessar", "давай pellix")

    private val federation = ProductionGraph.federation(
        ProductionGraph.shortcutSource(
            AppShortcut(packageName = "net.fixture.qorvath", shortcutId = "brimwell_now", appLabel = "Qorvath", shortcutLabel = "Brimwell"),
        ),
    )

    private fun apps() = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(
            InstalledApp(packageName = "net.fixture.lumora", label = "Lumora"),
            InstalledApp(packageName = "net.fixture.vemblic", label = "Vemblic"),
            InstalledApp(packageName = "net.fixture.zantor", label = "Zantor"),
            InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar"),
            InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar"),
            InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix"),
            InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix"),
        )
    }

    private fun aliases() = FakeAliasStore().apply {
        runBlocking {
            upsert(Alias("кварцелот", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L))
            upsert(Alias("плимдорф", AliasTarget.App("net.fixture.plimdorf"), createdAtEpochMs = 0L))
        }
    }

    private fun learned() = FakeResolutionPreferenceStore().apply {
        val tessar = CandidateSet(listOf(ResolvedTarget.App("net.fixture.tessar.one"), ResolvedTarget.App("net.fixture.tessar.two")))
        runBlocking {
            upsert(ResolutionPreference(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), ResolutionContext.None,
                ResolvedTarget.App("net.fixture.tessar.two"), PreferenceEvidence(3, 3, 1L), fingerprintOf(tessar)))
            upsert(ResolutionPreference(CapabilityKey(ActionIds.LAUNCH_APP, "фальмира"), ResolutionContext.None,
                ResolvedTarget.App("net.fixture.falmira"), PreferenceEvidence(3, 3, 1L),
                fingerprintOf(CandidateSet(listOf(ResolvedTarget.App("net.fixture.falmira"))))))
        }
    }

    private class Capture(private val mode: BridgeTransportMode) {
        val bodies = mutableListOf<String>()

        val engine = MockEngine { sent ->
            val body = (sent.body as TextContent).text
            bodies += body
            val messages = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
            val command = messages[1].jsonObject.getValue("content").jsonPrimitive.content
            val answer = if (messages.size == 2) REJECTED else accepted(command.substringAfterLast(' '))
            respond(envelope(answer), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        private fun quote(text: String) = Json.encodeToString(String.serializer(), text)

        private fun envelope(answer: String): String = when (mode) {
            BridgeTransportMode.FORCED_TOOL ->
                """{"choices":[{"message":{"tool_calls":[{"type":"function","function":{"name":"submit_answer","arguments":${quote(answer)}}}]}}]}"""
            BridgeTransportMode.PROMPT_ONLY, BridgeTransportMode.RESPONSE_FORMAT ->
                """{"choices":[{"message":{"content":${quote(answer)}}}]}"""
        }

        private fun accepted(mention: String) =
            """{"kind":"plan","step":{"tool":"launch_app","args":{"app":"$mention","duration":null,"query":null,"time":null,"url":null}},"question":null,"destination":null}"""

        companion object {
            const val REJECTED =
                """{"kind":"plan","step":{"tool":"set_app_alias","args":{"app":"lumora","phrase":"SNTL-REJECTED-ECHO"}},"question":null,"destination":null}"""
        }
    }

    private fun route(mode: BridgeTransportMode, capture: Capture) = BridgeProvidesModule.provideRouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = FakeIntentMatcher(),
            resolver = IntentActionResolver(apps()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(Dispatchers.Unconfined),
        ),
        modelPlanner = BridgeProvidesModule.provideModelPlanner(
            federation.registry,
            BridgeProvidesModule.provideToolDescriptions(AuthoredToolDescriptions()),
            OpenAiCompatibleBridgeTransport(
                HttpClient(capture.engine),
                FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), keySentinel) } },
                FakeAiProviderConfigRepository(AiProviderConfig(provider, "https://api.example.com/v1", AiModelId("fixture-model"))),
                Dispatchers.Unconfined,
                mode,
            ),
        ),
        binder = BridgeProvidesModule.provideMentionBinder(
            DeviceMentionBinder(AppTargetResolver(apps(), aliases()), ToolArgumentSorts(), learned(), DefaultResolutionPreferencePolicy()),
        ),
        featureFlagRepository = FakeFeatureFlagRepository(),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(
            TemplatePlanner(),
            FakeAgentSessionStore(),
            object : AgentSessionIdFactory {
                private var n = 0
                override fun newId() = AgentSessionId("guard-${++n}")
            },
            federation.registry,
        ),
    )

    private data class Run(val mode: BridgeTransportMode, val bodies: List<String>, val outcomes: List<CommandOutcome>)

    private val runs: List<Run> by lazy {
        BridgeTransportMode.entries.map { mode ->
            val capture = Capture(mode)
            val route = route(mode, capture)
            val outcomes = commands.map { runBlocking { route.route(it) } }
            Run(mode, capture.bodies.toList(), outcomes)
        }
    }

    private fun messagesOf(body: String) = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray

    @Test
    fun `allow-list holds exactly the vetted categories, the authored tool schema among them`() {
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
                AllowedContext.AUTHORED_TOOL_SCHEMA,
            ),
            OutboundContextPolicy.ALLOWED,
        )
    }

    /** Criterion 21: every source, every form, every retry. Violations are collected, so a red names them all. */
    @Test
    fun `no private source reaches any body, in any transport form, or in the retry`() {
        val violations = runs.flatMap { run ->
            run.bodies.flatMap { body ->
                sources.flatMap { (source, sentinels) ->
                    sentinels.filter { body.contains(it) }.map { "${run.mode} / $source: $it" }
                }
            }
        }.distinct()
        assertEquals(emptyList<String>(), violations)

        // The retry carries the closed code's note and nothing else (spec §7.12.2).
        runs.forEach { run ->
            run.bodies.chunked(2).forEach { (first, retry) ->
                assertEquals(2, messagesOf(first).size)
                val retryMessages = messagesOf(retry)
                assertEquals(3, retryMessages.size)
                assertEquals(
                    BridgeRendering.retryNote(BridgeError.NOT_OFFERED),
                    retryMessages[2].jsonObject.getValue("content").jsonPrimitive.content,
                )
            }
        }
    }

    /** §7.12.3 (2): the catalog part of the payload IS the rendered offering of the nineteen — not one line more. */
    @Test
    fun `the catalog part of every body is exactly the rendered offering of the nineteen authored tools`() {
        val offering = BridgeFilter.offeringOf(federation.registry, AuthoredToolDescriptions())
        val system = BridgeRendering.systemText(offering)
        val schema = Json.parseToJsonElement(BridgeRendering.schemaJson(offering))

        assertEquals(BridgeOfferingGuardTest.OFFERED_NINETEEN, offering.tools.map { it.descriptor.id.value }.toSet())
        runs.forEach { run ->
            run.bodies.forEach { body ->
                val json = Json.parseToJsonElement(body).jsonObject
                assertEquals("${run.mode}", system, messagesOf(body)[0].jsonObject.getValue("content").jsonPrimitive.content)
                when (run.mode) {
                    BridgeTransportMode.PROMPT_ONLY -> assertEquals(setOf("model", "messages", "max_tokens", "stream"), json.keys)
                    BridgeTransportMode.RESPONSE_FORMAT -> assertEquals(
                        schema,
                        json.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema"),
                    )
                    BridgeTransportMode.FORCED_TOOL -> assertEquals(
                        schema,
                        json.getValue("tools").jsonArray.single().jsonObject.getValue("function").jsonObject.getValue("parameters"),
                    )
                }
            }
        }
    }

    /** Non-vacuity: every planted value WAS read on the device, and the body WAS captured. */
    @Test
    fun `the controls - the device read every planted source, and the command and offering are in the body`() {
        assertTrue(federation.registry.all().any { it.id.value.startsWith(ShortcutToolIds.PREFIX) && "net.fixture.qorvath" in it.id.value })
        runs.forEach { run ->
            val bound = run.outcomes.map { (it as CommandOutcome.Interpreted).interpretation as Interpretation.Act }
            fun pinned(index: Int) = (bound[index].step.invocation.args["app"] as? ArgSource.Literal)?.value
            assertEquals("${run.mode}: installed apps", "net.fixture.lumora", pinned(0))
            assertEquals("${run.mode}: alias table", "net.fixture.zantor", pinned(1))
            assertEquals("${run.mode}: learned choices", "net.fixture.tessar.two", pinned(2))
            assertEquals("${run.mode}: a choice", listOf("net.fixture.pellix.one", "net.fixture.pellix.two"), bound[3].choice!!.candidates.map { it.packageName })
            assertEquals(commands.size * 2, run.bodies.size)
            run.bodies.forEach { body ->
                assertTrue(body.contains("давай"))
                assertTrue(body.contains("launch_app"))
            }
        }
    }

    /** Spec §7.12.3 (1), the three sources with no port (plan §0.5 S5): held by the bridge's signatures. */
    @Test
    fun `the bridge's classes take no port to prayer, calendar or usage data`() {
        val forbiddenPackages = listOf(
            "com.sidr.launcher.domain.prayer",
            "com.sidr.launcher.domain.history",
            "com.sidr.launcher.domain.suggestions",
            // The implementations, not only the ports (plan review): a provider taken directly would
            // pass a scan of domain packages alone.
            "com.sidr.launcher.data.repository.suggestions",
            "com.sidr.launcher.data.prayer",
            // Android's own doors to calendar, location and usage — a Context opens all of them.
            "android.content",
            "android.app.usage",
        )
        val types = listOf(
            BridgeModelPlanner::class.java,
            OpenAiCompatibleBridgeTransport::class.java,
            DeviceMentionBinder::class.java,
            AppTargetResolver::class.java,
            AuthoredToolDescriptions::class.java,
        ).flatMap { type -> type.constructors.flatMap { it.parameterTypes.toList() } } +
            BridgeProvidesModule::class.java.declaredMethods.flatMap { it.parameterTypes.toList() }

        val reaching = types.filter { type -> forbiddenPackages.any { type.name.startsWith(it) } }.map { it.name }.distinct()
        assertEquals(emptyList<String>(), reaching)
        // The control: the scan read real signatures — it sees the ports the bridge DOES hold.
        assertTrue(types.any { it.simpleName == "ToolRegistry" })
        assertTrue(types.any { it.simpleName == "ResolutionPreferenceStore" })
    }
}
```

  `OneModelPathGuardTest.kt`:

```kotlin
package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Criterion 14 (spec §7.12.6): one model path. The router is gone from production code, and nothing the
 * model proposes that acts can bypass the engine — the one direct execution left in the launcher is the
 * web chip, which the USER points at a destination (spec §7.12.3: FastPath and the route chips are not
 * the model's path). Comments are stripped: history may name the router; code may not.
 */
class OneModelPathGuardTest {

    // Canonical on purpose: `File("..")` is NAMED "..", and the hidden-directory filter below would skip
    // the root itself — a scan that reads nothing and passes (the control catches it, but should not have to).
    private val repoRoot = File("..").canonicalFile

    private fun strip(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    private fun productionSources(): List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "kt" }
        .filter { val p = it.invariantSeparatorsPath; "/src/main/" in p || "/src/commonMain/" in p }
        .toList()

    @Test
    fun `no production source names the deleted router`() {
        val names = listOf(
            "CommandPlanner", "LlmCommandPlanner", "CatalogSchemaRenderer", "ProposalValidator", "ActionProposal",
            "PlanResult", "RoutedAction", "PendingRoutedAction", "RouterProvidesModule", "ACTION_CATALOG_SCHEMA",
        )
        val sources = productionSources()
        val hits = sources.flatMap { file ->
            val code = strip(file.readText())
            names.filter { Regex("\\b$it\\b").containsMatchIn(code) }.map { "${file.invariantSeparatorsPath}: $it" }
        }

        assertEquals(emptyList<String>(), hits)
        assertTrue("the scan must read the whole tree, not a corner of it (${sources.size})", sources.size > 300)
    }

    @Test
    fun `the launcher executes an action directly in exactly one place, the web chip`() {
        val launcher = File(repoRoot, "feature/launcher/src/main")
        val calls = launcher.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            Regex("""executeAction\.execute\(""").findAll(strip(file.readText())).map { file.name }
        }.toList()

        assertEquals(listOf("LauncherCommandSession.kt"), calls)
        val chip = File(launcher, "java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt").readText()
            .substringAfter("fun submitWebSearch(").substringBefore("\n    fun ")
        assertTrue("the one call is the web chip's", chip.contains("executeAction.execute("))
    }
}
```

  `OfferedToolIdsFreezeGuardTest.kt` (F3a-B (a)):

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR 2 of A4′ phase 3a (owner fork F3a-B): the outbound contract with the model passed from the seven
 * `ActionIds` to the offered tools. Their VALUES are frozen — a rename or a removal orphans what phase 4
 * caches and what a model was taught, and needs a superseding ADR. Their NUMBER is not: a new tool is
 * offered by registering it, with no edit here (containment, not equality).
 */
class OfferedToolIdsFreezeGuardTest {

    @Test
    fun `the nineteen offered tool ids are frozen byte for byte`() {
        val offered = BridgeFilter.offeringOf(ProductionGraph.federation().registry, AuthoredToolDescriptions())
            .tools.map { it.descriptor.id.value }.toSet()

        assertEquals(emptySet<String>(), BridgeOfferingGuardTest.OFFERED_NINETEEN - offered)
    }
}
```

- [ ] **Step 8: the doctrine matrix** (`docs/governing/sidr-doctrine-matrix-v1.0.md`) — each cell edited
  consciously, each with a journal row in §6 dated the day of the edit (the 2026-08-22 `DOC-ILM-3` row's
  form: what changed, why the type stays, what it does not close):
  - **`DOC-AML-1`** (forced: `RouterOutboundGuardTest` no longer exists): `AiRequestGuardTest` ·
    `BridgePayloadGuardTest` (сентинел каждого приватного источника отсутствует во всех трёх формах тела и
    в теле повтора; каталожная часть нагрузки — ровно отрендеренная выдача девятнадцати) ·
    `BridgeRenderingTest` (запрет-лист — только по статическому тексту инструкции и повторов).
  - **`DOC-ADL-1`**: `ConsentPolicyTest` · `AgentExecutorTest` (шаг `CONFIRM`, предложенный моделью,
    останавливается у согласия движка ровно как детерминированный) — `RouteCommandUseCaseTest` leaves this
    cell: its `needsConfirmation` cases were the router's.
  - **`DOC-NYH-1`**: `RouteCommandUseCaseTest` (толкование модели — карточка, сессия не стартует;
    детерминированные пути карточки не получают) ·
    `LauncherViewModelTest` (выбор кандидата привязывает, не запускает) · `IntentConfidencePolicyTest`.
  - **`DOC-NYH-2`**: `SidrActionGateTest#consequence_is_always_visible` · `InterpretationSurfaceTest`
    (карточка называет шаг до исполнения).
  - **`DOC-HYA-2`**: the two `SidrActionSafetyTest` anchors · `InterpretationSurfaceTest` (карточка не
    исполняет при отрисовке) · `RouteCommandUseCaseTest`.
  - **`DOC-MZN-2`**: `SidrActionGateTest#target_url_is_visible` · `InterpretationSurfaceTest` (адрес,
    сгенерированный моделью, показан дословно).
  - **`DOC-ILM-3`**: append to its cell «С фазы 3a A4′ действия модели исполняются только движком — роутер
    удалён (`OneModelPathGuardTest`), — поэтому правило покрывает и их.»
  - **`DOC-ADL-3`** (forced by its text): «…не может **назвать** `GenerativeAiEngine` / `CommandPlanner` /
    Ktor…» → «…`GenerativeAiEngine` / `ModelPlanner` / `BridgeTransport` / пакет моста / Ktor…»; add
    `ResumeAgentSessionUseCaseTest` (план модели на возобновлении уходит в маршрут, а не в
    `CompositePlanner`). The paragraph at `:149-151` the same way.
  - `DoctrineMatrixGuardTest` checks every named class exists — run it.

- [ ] **Step 9: two ADRs** — appended to `ai-context/decisions.md`, in Russian, in the house form:

  **ADR «<date> — A4′ ф3a: `ACTION_CATALOG_SCHEMA` → `AUTHORED_TOOL_SCHEMA`» (change-control §5).**
  Контекст: роутер удалён (D3), его категория осталась без производителя; мосту нужна категория для
  наших описаний авторских инструментов и схемы ответа. Решение: замена, не добавление — `ALLOWED`
  остаётся из четырёх. Что уходит: команда дословно (`USER_COMMAND`), статический текст инструкции и
  повторов (`STATIC_SYSTEM_PROMPT`), наши описания девятнадцати и JSON-схема из той же выдачи
  (`AUTHORED_TOOL_SCHEMA`), предел вывода (`GENERATION_LIMITS`). Чего не уходит никогда (D1): мир
  пользователя — динамический ярус, приложения, алиасы, выученные выборы, намаз, календарь, история;
  исход привязки. Чем держится: `BridgePayloadGuardTest` — по нагрузке, во всех трёх формах тела и в
  теле повтора, сентинел на каждый источник; запрет-лист — только по статическому тексту (сужать его ради
  id инструментов запрещено). Обязательство §7.9: в день локального вывода — перепроверить, может ли этот
  гвард упасть, и записать решение о категории. Что не закрыто: квитанции исходящего, маскирование в самой
  команде (после `B16`).

  **ADR «<date> — A4′ ф3a: один модельный путь; исходящий контракт — девятнадцать `ToolId`».** Контекст:
  D3, условия 1–4. Решение: роутер (`CommandPlanner`, `LlmCommandPlanner`, `CatalogSchemaRenderer`,
  `ProposalValidator`, `ActionProposal`, `PlanResult`, `CommandOutcome.RoutedAction`, его карточка,
  `RouterProvidesModule`) удалён без флага. Роль «исходящего контракта к модели» (ADR 3/4) перешла от
  семи `ActionIds` ко **всему выдаваемому набору** из девятнадцати `ToolId` (пятнадцать `system_intent` ни
  из какого `ActionId` не выведены); значения семи `ActionIds` не изменились ни на байт. Заморозка
  (развилка F3a-B): <ответ владельца; при (a) — значения заморожены, число нет; `OfferedToolIdsFreezeGuardTest`>.
  Модельный порт — `ModelPlanner`, не `Planner`: его нет в списке `CompositePlanner`, и это держит
  `DoctrineGuardTest > the composition root's planner list matches the declared planners`. Одним путём
  **не** становятся FastPath и чипы маршрутов — они исполняют напрямую, как и раньше. Принятый регресс
  («пятое место»): предложение `CONFIRM` стоит карточку толкования плюс карточку согласия и ожидание до
  потолка. R14-39 — назван, не закрыт (спека §7.12.3). Бюджет и транспорт по умолчанию — в дополнении по
  замеру (задача 12).

- [ ] **Step 10: run, module by module, then the full gate.**
  - `tools/gate.sh --scoped :domain:jvmTest` → `domain tests=509` (527 − 14 − 8 − 2 + 6);
  - `tools/gate.sh --scoped :data:ai-cloud:testDebugUnitTest` → `39` (50 − 11);
  - `tools/gate.sh --scoped :data:repository:testDebugUnitTest` → `430` (431 − 1);
  - `tools/gate.sh --scoped :feature:launcher:testDebugUnitTest` → `234` (238 − 5 + 1);
  - `tools/gate.sh --scoped :app:testDebugUnitTest` → `76` (68 + 5 + 2 + 1);
  - `:core:testing` compiles with `FakeCommandPlanner` gone; `:consumer:jvm` unchanged (72).

  > **Measured in execution (2026-10-05):** these numbers came from pre-T4 bases. The module DELTAS held exactly; on the
  > tree of the day (T10, 1691) they read `:domain` 529 → **511**, `:data:ai-cloud` 51 → **40**, `:data:repository`
  > 433 → **432**, `:feature:launcher` 254 → **250**, `:app` 68 → **76**. On top of that, controller rulings added
  > `:feature:launcher` +2 (T9's deferred m2 — the WEB chip and a grid tap supersede a command still waiting on the
  > model), `:domain` +1 (H2 — `Binding.Unavailable` → `Failed(Generic)`) and `:feature:launcher` +1 (H8 — a suggestion
  > tap supersedes) → **1669**. The review's fix round added +1 `:feature:launcher` (the ASK chip, screen level) → **1670**.
  > Step 4 missed one file: `LauncherScreenInterpretationTest` (Task 10) also passes `actionCatalog =` and loses it.
  > The brief's line addresses had drifted (`RoutedAction` `:65-77`; `LauncherViewModel` `:79-81`, `:351`, `:472-480`;
  > `LauncherScreen` `:114`, `:253-271`, `:741-780`); the quoted texts matched.

- [ ] **Step 11: mutations — twelve.**

| # | File · plant: old → new | Proves | Predicted `FAILED ::` |
|---|---|---|---|
| M11a | `RouteCommandUseCase.kt` · `is Binding.Bound -> CommandOutcome.Interpreted(bound.interpretation)` → `is Binding.Bound -> com.sidr.launcher.domain.agent.ExecutionPlan(listOf(bound.interpretation.step)).let { startAgentSession.startPlanned(bound.interpretation.goal, it) }.let { CommandOutcome.Interpreted(bound.interpretation) }` (fully qualified: `plan()` is an extension in `domain.ai.bridge` that `RouteCommandUseCase` does not import — the plan review caught a plant that would not have compiled) | criterion 15: step 7 starts nothing itself | `:domain` — `RouteCommandUseCaseTest > a model plan becomes an interpretation, and no session is started`; `:feature:launcher` — `LauncherViewModelTest > a model plan through the real route waits on the card, and Run starts an engine session` |
| M11b | `BridgeOffering.kt` · `            registry.all()\n                .filter { it.level in OFFERED_LEVELS }\n                .mapNotNull { descriptor ->\n                    descriptions.describe(descriptor.id)?.let { OfferedTool(descriptor, it) }\n                },` → `            registry.all()\n                .map { descriptor ->\n                    OfferedTool(descriptor, descriptions.describe(descriptor.id) ?: ToolDescription(ToolNamespace.DEVICE, descriptor.id.value, emptyList()))\n                },` | criterion 21, the dynamic tier: the whole federation offered | `:app` — `BridgePayloadGuardTest > no private source reaches any body…` (dynamic tier: `net.fixture.qorvath`), `> the catalog part of every body is exactly…`, `BridgeOfferingGuardTest` (both); `OfferedToolIdsFreezeGuardTest` stays green (containment); `:domain` — `BridgeOfferingTest > launcher memory is never offered`, `> a shortcut is never offered, even with a description`, `> a tool at a level the filter has not heard of is not offered`, `> a tool with no description is not offered` (its first and fourth tests stay green: both fixtures sit at offered levels, described) |
| M11c | `RouteCommandUseCase.kt` · `is Binding.Bound -> CommandOutcome.Interpreted(bound.interpretation)` → `is Binding.Bound -> CommandOutcome.Interpreted(bound.interpretation).also { bound.interpretation.choice?.let { c -> modelPlanner.plan(command + " " + c.candidates.joinToString(" ") { it.packageName }) } }` | criterion 21, installed apps: a pick list "helpfully" told to the model | `:app` — `BridgePayloadGuardTest > no private source reaches any body…` (installed apps: `net.fixture.pellix.one`, `.two`) and `> the controls - …` (the extra planning adds two bodies: 10 ≠ `commands.size * 2`) |
| M11d | same old → `is Binding.Bound -> CommandOutcome.Interpreted(bound.interpretation).also { modelPlanner.plan(command + " " + bound.interpretation.step.invocation.args.values.joinToString(" ")) }` | criterion 21, aliases and learned choices: a bound package told to the model | `:app` — `> no private source reaches any body…` and `> the controls - …` (four extra plannings, 16 ≠ 8 bodies). **The proof for the alias and learned sources is the message, not the red** — installed apps alone would redden it: read the failure message from `app/build/test-results/testDebugUnitTest/TEST-com.sidr.launcher.agent.BridgePayloadGuardTest.xml` and require all three of `installed apps: net.fixture.lumora`, `alias table: net.fixture.zantor`, `learned choices: net.fixture.tessar.two` (each source's sentinel is listed under its own name, so an empty alias list would drop exactly that line) |
| M11e | `BridgeModelPlanner.kt` · `val retry = first.copy(retryNote = BridgeRendering.retryNote(error))` → `val retry = first.copy(retryNote = BridgeRendering.retryNote(error) + " " + firstReply)` | criterion 18 at the payload: the retry echoes nothing | `:app` — `> no private source reaches any body…` (the rejected answer: `SNTL-REJECTED-ECHO`); `:domain` — `BridgeModelPlannerTest > an invalid answer is asked again exactly once, with only the closed code added`, `> an unparseable first reply is asked again with the shape code` (the M4e pair) |
| M11f | `OutboundContextPolicy.kt` · `        AllowedContext.GENERATION_LIMITS,\n        AllowedContext.AUTHORED_TOOL_SCHEMA,\n    )` → `        AllowedContext.AUTHORED_TOOL_SCHEMA,\n    )` | the six pins | `:domain` — `AiRequestGuardTest > allow-list holds exactly the vetted categories`, `SuggestionOutboundIsolationTest > allow-list holds only the authored tool schema beside the basics…`, `AliasPrivacyScopeGuardTest > explicit aliases keep outbound allow-list byte-for-byte unchanged`, `ResolutionPrivacyScopeGuardTest > learned resolutions keep outbound allow-list byte-for-byte unchanged`, `PrayerLocationPrivacyGuardTest > guard 1 - …`; `:app` — `BridgePayloadGuardTest > allow-list holds exactly the vetted categories, the authored tool schema among them` |
| M11g | **new file** `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/alias/BridgeLeak.kt` = `package com.sidr.launcher.domain.memory.alias\n\nimport com.sidr.launcher.domain.ai.bridge.BridgeRendering\n\ninternal val bridgeLeak: String = BridgeRendering.INSTRUCTION\n` | criterion 14: «память импортирует рендерер моста» | `:domain` — `AliasPrivacyScopeGuardTest > alias memory has no outbound or generative dependency` **only** |
| M11h | `LauncherCommandSession.kt` · `    fun dismissFeedback() {` → `    fun runLegacy(action: LauncherAction) {\n        scope.launch { applyOutcome(executeAction.execute(action)) }\n    }\n\n    fun dismissFeedback() {` | criterion 14: no second direct path | `:app` — `OneModelPathGuardTest > the launcher executes an action directly in exactly one place, the web chip` **only** |
| M11i | **new file** `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/CommandPlanner.kt` = `package com.sidr.launcher.domain.ai.router\n\ninterface CommandPlanner\n` | criterion 14: «роутера в дереве нет» | `:app` — `OneModelPathGuardTest > no production source names the deleted router` **only** |
| M11j | `BridgeModelPlanner.kt` · `    private val budget: BridgeBudget = BridgeBudget.Default,\n) : ModelPlanner {` → `    private val budget: BridgeBudget = BridgeBudget.Default,\n    private val usage: com.sidr.launcher.domain.history.UsageHistoryRepository? = null,\n) : ModelPlanner {` | the three no-port sources (S5) | `:app` — `BridgePayloadGuardTest > the bridge's classes take no port to prayer, calendar or usage data` **only** |
| M11k | `AuthoredToolDescriptions.kt` · `            InAppToolIds.OPEN_URL to content("Open a web address in the browser.", text("url", "the full address, starting with https://")),\n` → `` | F3a-B (a): an offered id cannot silently vanish | `:app` — `OfferedToolIdsFreezeGuardTest > the nineteen offered tool ids are frozen byte for byte`, `BridgeOfferingGuardTest > the production offering is the nineteen…`, `BridgePayloadGuardTest > the catalog part of every body is exactly…`; `:data:repository` — `AuthoredToolDescriptionsTest > every authored tool at an offered level has a description` (the M5a test) |
| M11l | `RouteCommandUseCase.kt` · `        if (!ruleOutcome.isUndecided()) return ruleOutcome\n` → `` | criterion 15: a decided FastPath outcome gets no card | `:domain` — `RouteCommandUseCaseTest > the deterministic paths never get an interpretation card`, `> FastPath hit - planner never consulted, latency optimization not a filter (R1)`, `> empty input - never reaches the planner and is not an unavailable-state` (offline in that test: `Empty` now falls to step 6). `:feature:launcher` expected green (its decided commands meet a planner that declines by default) — run it, record |

  (M11g and M11i plant a new file: restore with `rm -f "$F"` and `test ! -e "$F" && echo RESTORED`.
  `:domain` compiles M11g; M11i compiles in `:domain` too — an unused interface.)

> **Measured in execution (2026-10-05) — M11a–M11l PROVED, with four rows corrected by reading before the run and confirmed
> by it.** **M11b** reddens nine `:domain` tests, not four: also `BridgeValidatorTest > a registered tool outside the
> offering is rejected before anything runs` and four `BridgeModelPlannerTest` tests (`an invalid answer is asked again
> exactly once…`, `a rejected id gets the same retry text…`, `a second invalid answer is unreadable…`, `the ceiling covers
> both attempts`) — the bridge fixtures register `set_app_alias` and a shortcut, which the plant offers; one of the nine
> fails with `IndexOutOfBoundsException` (a retry request never sent), behavioural, not a compile error. **M11e** adds
> `> a rejected id gets the same retry text…` (three in `:domain`). **M11l** adds `:data:repository` —
> `FastPathOverflowSeamTest > a search whose conjunction joins two nouns is still run by FastPath and the model is not
> asked` (`Executed` is decided; with step 4 gone it reaches step 7); `:feature:launcher` measured green. **M11d** also
> reddens `:domain` test (a) once H1 pins `lastCommand`; its XML message names `installed apps: net.fixture.lumora`,
> `alias table: net.fixture.zantor`, `learned choices: net.fixture.tessar.two` in all three transport forms. Task 9's
> **M9h** lost its anchor (`_pendingRoutedAction.value = null` is deleted here) — re-anchored on
> `        _interpretation.value = null\n        when (outcome) {\n` → `        when (outcome) {\n`.
>
> Eleven more plants, from two controller rulings and the implementer's hunt for tests that cannot fail, each PROVED with
> exactly its own red:
>
> | # | What it removes | Red |
> |---|---|---|
> | M11-R1 | a forbidden term («functions»→«locations», same length) in the DEVICE header of the offering's fixed text | `:domain` `BridgeRenderingTest > the offering is grouped by namespace…`; `:app` `BridgePayloadGuardTest > the catalog part…` (message `a denylisted term in the offering's fixed text … [location]`) |
> | R2a / R2b | the supersede call in the WEB chip / in a grid tap | `LauncherViewModelTest > the web chip supersedes…` / `> a tap on an app supersedes…` |
> | H1a / H1b | the trim of the command at step 7 / `GoalShape.Free` | `RouteCommandUseCaseTest > a model plan becomes an interpretation…` |
> | H2 | `Binding.Unavailable -> ruleOutcome` | `> an unreadable app list is said as a failure, not hidden behind FastPath's answer` (new) |
> | H3 | one fixed reason for every `Unavailable` | `> an unavailable model is said so, with its reason` (now total over the enum) |
> | H4 | `"~ " +` before the command in the user message | `:app` controls test; `:data:ai-cloud` two transport tests |
> | H5 | a `() -> UsageHistoryRepository` port (erased type) | `> the bridge's classes take no port to prayer, calendar or usage data` (generic signatures now read) |
> | H6 | `executeAction.let { it.execute(…) }` beside the chip | `OneModelPathGuardTest > the launcher executes a LauncherAction directly in exactly one place, the web chip` |
> | H8 | the supersede call in a suggestion tap | `LauncherViewModelTest > a suggestion that opens a screen supersedes…` (new) |
>
> Task review fix round (`8b338a9`): F1-ASK and F1-ENTRY (the ASK chip back on `navigateTo` / `navigateByUser` without the
> supersede) → `LauncherScreenInterpretationTest > the ASK chip abandons a command still waiting on the model`; F1-M3
> (`executeAction.let { it.execute(…) }` in the ViewModel) and M11h/H6 against the guard rewritten on the
> `ExecuteActionUseCase` type → the renamed `OneModelPathGuardTest` test. **F1-PRAYER is not proved by design**: the
> prayer-details call site goes through `navigateByUser` and no test holds it — reachable during a wait when «Продолжить»
> reroutes a model plan with an empty buffer (named, deferred).

- [ ] **Step 12: boundary gate from a cleared tree** (`./gradlew --stop` first). Expected `GATE GREEN`,
  `TOTAL tests=1644`: `:domain` **509** · `:consumer:jvm` **72** · `:data:repository` **430** ·
  `:feature:launcher` **234** · `:app` **76** · the other eight **323** (`:data:ai-cloud` **39**). **The
  floor (1560) does not see the 41 deletions; the per-module numbers do** — a module short of its number
  is a red result whatever the total says. Also run `:app:assembleRelease --rerun-tasks` once: the router
  left the release graph (and `checkOwnerReviewedLocaleStrings` stays green — no Class B key moved).

  > **Measured in execution (2026-10-05):** GATE GREEN **1669** at `9ecbcf1` (`:domain` 512 · `:consumer:jvm` 72 ·
  > `:data:repository` 432 · `:feature:launcher` 253 · `:app` 76 · the other eight 324, `:data:ai-cloud` 40), then
  > **1670** after the review fix `8b338a9` (`:feature:launcher` 254). `:app:assembleRelease --rerun-tasks` green,
  > `checkOwnerReviewedLocaleStrings` ran. The deep reviewer re-ran the boundary gate itself: 1669, identical.

- [ ] **Step 13: commit (controller).**

```bash
git add -A domain/src data core/testing/src feature/launcher/src app docs/governing ai-context/decisions.md
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): один модельный путь — роутер удалён, ALLOWED заменён, гвард по нагрузке

D3 (спека §7.12.3): шаг 7 спрашивает ModelPlanner, привязка — на устройстве
после ответа, итог — карточка толкования; ничто из предложенного моделью и
действующего в мире не исполняется мимо движка. Роутер удалён целиком
(CommandPlanner, LlmCommandPlanner, CatalogSchemaRenderer, ProposalValidator,
ActionProposal, PlanResult, RoutedAction с карточкой, RouterProvidesModule)
и с ним 41 тест. ACTION_CATALOG_SCHEMA заменён AUTHORED_TOOL_SCHEMA (ADR 1,
change-control §5) — шесть закреплений меняются вместе. BridgePayloadGuardTest
читает НАГРУЗКУ: сентинел каждого приватного источника отсутствует во всех
трёх формах тела и в теле повтора. ADR 2: исходящий контракт — девятнадцать
ToolId (развилка F3a-B). Ячейки доктрины переякорены.

Мутации M11a–M11l.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: apply the transport measurement — the owner's run

**Spec:** §7.12.2 («транспорт по умолчанию выбирает замер… именованные пределы… числа — из замера и
ADR»); criterion 19 (the numbers). **Modules:** `:domain` (`BridgeBudget`), `:app`
(`BridgeProvidesModule`), `ai-context/decisions.md`. **Owner dependency:** §0.7 item 2.

This task cannot start before the owner's run. If Tasks 1–11 are done and the owner has not run it, the
controller **stops and asks** whether to wait or to close the phase with the provisional numbers and the
measurement as an open owner item — the plan does not choose for the owner.

**The decision rule, written before the numbers exist** (so the measurement cannot be read to fit):
1. **Default transport:** the mode with the highest `usable` share summed over the owner's models; a
   mode whose `usable` on ANY of the owner's models is more than one line below `PROMPT_ONLY`'s on that
   model is not eligible (the switch is global, and the owner said "any model"). Ties → fewer `retried`,
   then lower `median_ms`. `PROMPT_ONLY` wins every tie with itself.
2. **`BridgeBudget.ceilingMs`:** `max(45 000, 1.5 × the largest p90_ms of the chosen mode over the
   owner's models)`, rounded up to 5 000, capped at 90 000. Above the cap → the owner decides (a model
   that slow is a product question, not a constant). The harness's socket is the cap (90 000), so a
   latency below it is observed rather than cut; production's socket follows the chosen ceiling
   (`bridgeHttpClient(BridgeBudget.Default.ceilingMs)`), so raising the constant raises both.
3. **`BridgeBudget.maxOutputTokens`:** 2 048 unless any line of the chosen mode reports
   `finish=length` with `reasoning_tokens` > 0 — then 4 096, and the ADR names the model.
4. **The question share** (`questions_to_assistant` against `questions_to_web_search`) is recorded, not
   enforced: condition 3 is held by measurement, not by a unit test (spec §7.12.3).

- [ ] **Step 1:** read `app/build/bridge-measure/report.txt` from the owner's run; copy the
  `summary` lines into the ADR addendum as a table (model × mode); apply rules 1–3.
- [ ] **Step 2:** `BridgeProvidesModule.DEFAULT_TRANSPORT_MODE` ← rule 1; `BridgeBudget.Default` ← rules
  2–3; `BridgeBudget`'s KDoc «Provisional until…» → «Fixed on <date> by the owner's measurement (ADR …):
  <models>, <mode>, p90 <n> ms».
- [ ] **Step 3:** ADR addendum under ADR 2 («Бюджет и транспорт по умолчанию — по замеру»): the table,
  the three rules applied, the question share, and what the measurement did NOT measure (one owner, one
  corpus, the providers OpenRouter routed to that day — the routing changes).
- [ ] **Step 4:** boundary gate from a cleared tree — expected `GATE GREEN`, `TOTAL tests=1644` (no test
  pins the numbers; `BridgeModelPlannerTest` builds its own budgets).

  > **Measured in execution (2026-11-07):** the run was executed by the agent at the owner's request, models chosen by
  > the agent (seven, via OpenRouter), corpus = B15's 24 lines + 8, labels an unreviewed draft; 672 lines, $0.46.
  > Rule 1 → `PROMPT_ONLY` (222/224; RESPONSE_FORMAT 0/32 on Haiku and Sonnet, FORCED_TOOL 0/32 on Sonnet and Llama —
  > `REQUEST_REJECTED`); rule 2 → **50 000 ms** (qwen3.7-flash p90 31 635 × 1.5); rule 3 → **4 096** (deepseek-v4.1-flash
  > and qwen3.7-flash cut at 2 048 tokens, all reasoning). Gate **1670** (the chain's total since T11 — not 1644).
  > Task review I1: the pair 50 s × 4 096 was never measured together — named in the ADR, re-run is the owner's option.
- [ ] **Step 5: commit (controller).**

```bash
git add domain/src app/src/main ai-context/decisions.md
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф3a): транспорт моста и бюджет — по замеру владельца

Спека §7.12.2. Правила выбора записаны в плане до замера; применены к
отчёту владельца (модели × три формы): транспорт по умолчанию — <mode>,
потолок — <n> мс, предел вывода — <n>. Таблица и доля вопросов, ушедших в
ассистента, — в дополнении к ADR 2.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: close the phase — documents, the floor. The commit is proposed, not made

**Spec:** §7.12.6 («Синхронизация при закрытии»), §10 criteria 11–12. **Modules:** none (documents and
`tools/gate.sh`).

- [ ] **Step 1: final whole-branch review** (phases 0–2 precedent), before any document: its fixes are
  their own commit, with mutations and a boundary gate.
- [ ] **Step 2: boundary gate from a cleared tree** — the measured total becomes the floor. Expected
  `TOTAL tests=1644` (a hypothesis; 1643 under F3a-B (b)).
- [ ] **Step 3: `tools/gate.sh`** — `BASELINE_TESTS` (`:195`) ← the measured total; the label (`:236`),
  the comment on `:195` and the header (`:19`) from «phase 2» to «phase 3a», keeping the history
  («1560 after phase 2, 1485 after phase 1, 1463 after phase 0, 1440 before»). Machinery: re-run the
  boundary gate (`GATE GREEN`), then set the floor to measured + 1 once (`GATE RED`, `BELOW BASELINE`),
  then back (R9's lesson).
- [ ] **Step 4: `§HANDOFF`** of the track plan — a subsection «Фаза 3a A4′ — мост, первый срез —
  `CODE-GREEN` <date>, гейт **<N>**» in the form of «Фаза 2 A4′»: state and commits; the gate chain per
  task; what was built, one line per task; the owner's three forks and answers; the measurement and Task
  12's result; the plan's defects found by execution; the controller's rulings; the hypotheses for
  acceptance (§0.7); what 3a did NOT close (§0.6) — and correct what it falsifies above: «Фаза 2»'s «С
  чего начинать фазу 3» now splits 3a/3b; the R14-39 paragraph (named, not closed, its cause narrowed).
- [ ] **Step 5: `CLAUDE.md`** (spec §7.12.6): the stage-table row for phase 3a; the baseline paragraph
  (new number, decomposition, the chain — the whole paragraph, as the owner ruled in phase 1); **Hard
  rules** — «…from the structured `Planner` / `CommandPlanner`…» → «…from the structured `Planner` and
  the bridge's `ModelPlanner`…», and «LLM-proposed actions never auto-execute a risky action» → «nothing
  the model reads runs before the interpretation card's «Выполнить», and then only through the engine»;
  the **contract table** — `CommandPlanner + PlanResult/ActionProposal/ProposalValidator/
  CatalogSchemaRenderer/RouteCommandUseCase` → `ModelPlanner` + `ModelPlanning` / `BridgeModelPlanner` /
  `BridgeValidator` / `BridgeRendering` / `MentionBinder` / `Interpretation` (`domain/ai/bridge`) and
  `RouteCommandUseCase`; «Cloud AI client (Ktor SSE engine, `LlmCommandPlanner`)» → «…,
  `OpenAiCompatibleBridgeTransport`»; `AuthoredToolDescriptions`, `DeviceMentionBinder` in the
  `:data:repository` rows; **Shipped surface** («BYOK cloud AI — … the LLM action router (`CommandPlanner`
  → risk-gated confirm card → `ExecuteActionUseCase`)») rewritten for the bridge; every «twenty authored
  tools» (the A1″ bullet, the stage-5.5 paragraph) → «twenty-two authored tools, nineteen of them offered
  to the model» — `web_search`/`open_url` are authored from Task 1 on; **Known debt** — finding
  (c) and (d) of the A1″ acceptance closed in code (not seen on a phone), R14-39 narrowed, A1′ residual
  (3)'s "raw command text sent to the cloud" paragraph re-read against step 7's new shape, F3a-C's alias
  limit named.
- [ ] **Step 6: `ai-context/current-status.md`** — the header entry, then **re-read its last three
  sections** («Not claimed done», «Source of truth», «Keeping this file honest») and correct what 3a
  falsified (the rule of 2026-09-20).
- [ ] **Step 7: the discussion memo** (`docs/superpowers/specs/2026-10-03-agent-architecture-discussion-design.md`)
  — a note at its top that spec §7.12 replaced it where they differ (spec §7.12.6). Name, do not edit:
  `docs/agentic-os-architecture.md`, the agentic and DS master plans, `README.md` (`:47`, `:73` —
  `CommandPlanner`, «router-off») and `docs/roadmap.md` (`:22`, `:36`, `:98` — the router as the AI core)
  still name the router — each is a follow-up with its own change control (the last two found by the plan
  review).
  > **Measured in execution (2026-11-07):** the floor is **1673**, not 1644 (Step 2: boundary GATE GREEN at
  > `f6aa141`, 557 executed — `:domain` 512 · `:consumer:jvm` 72 · `:data:repository` 433 · `:feature:launcher` 256 ·
  > `:app` 76 · other eight 324); the +29 over the plan are tests controller rulings and review rounds added, each in
  > `§HANDOFF`. Step 3's machinery check ran as written (1673 GREEN → 1674 `GATE RED`/`BELOW BASELINE` → 1673
  > GREEN). Step 1, the final review (`reviewer-deep`, `beff4da..56166d3`): 0 Critical, **4 Important** — A1 the
  > model's `app_grid` reading did not open the drawer; A2 the freeze guard had no red set of its own; A3 `open_url`
  > sent a model-written URL to `ACTION_VIEW` with no scheme check; A4 `DOC-AML-1`'s main cell over-claimed — all
  > fixed in **`f6aa141`** with M1 (voice Final) and a KDoc sweep; four mutations proved. **E13-1:** the fix brief's
  > two-file A2 plant also reddens `DoctrineGuardTest` (the id is a literal in `AgentSessionPresentation.kt` too); the
  > proving plant is the complete three-file rename. Step 7 named, did not edit, the router mentions — more than the
  > lines listed above; among them `README.md` `:138`, `:175`, `:193`, `:212–214`, `:231–236`, the agentic master plan
  > `:427`, `:542`, `docs/agentic-os-architecture.md` `:243`, the DS master plan `:1232`, `docs/roadmap.md` `:91`, `:194`
  > (full list in `§HANDOFF`).
- [ ] **Step 8: propose the commit to the owner (R7)** — do not make it without the owner's yes:

```bash
git add tools/gate.sh docs CLAUDE.md ai-context/current-status.md
git commit -m "$(cat <<'EOF'
docs(agentic-6/A4' ф3a): фаза 3a закрыта — CODE-GREEN на <N>

Мост, первый срез: один модельный путь, наш словарь, привязка на
устройстве, карточка толкования, честный отказ модели, гвард по нагрузке.
Роутер удалён (−41 тест), ALLOWED заменён по ADR. Пол gate.sh — <N>.
Устройства не было (одна приёмка, в конце блока).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Self-review against the spec

| Spec (§7.12.4 «3a») | Where |
|---|---|
| own model-planner port at step 7; answer variants; honest refusal | Tasks 4 (port, variants, reasons), 11 (step 7) |
| description port — our vocabulary, namespaces, arguments | Tasks 3 (port, rendering), 5 (rows) |
| renderer = filter by level, without `launcher_memory` (§7.5, §7.6) | Task 3 (F3a-A), Task 5 (production) |
| `web_search`, `open_url` projected; the worker learns `url` | Task 1 |
| `OpenAssistant` with the question, prefilled | Task 9 (outcome, route encoding), Task 11 (step 7) |
| answer form, schema generator, bridge validator (shape, membership, derivability), one retry | Tasks 3, 4 |
| minimum labels for the card's lifetime | Task 4 (`ValueOrigin`), Task 7 (binder), Task 10 (the card) |
| own budget and visible wait | Task 4 (`BridgeBudget`), Task 9 (wait + cancel), Task 10 (the surface), Task 12 (numbers) |
| D3 condition 1 — card before the session; and only the model's path gets one | Tasks 9, 10, 11 (criterion 15, M9a, M11a, M11l) |
| D3 condition 2 — ambiguity before the plan; a pick binds; `launch_app` keeps learned choices | Tasks 2 (resolver, pin), 7 (binder), 9 (pick, record at Run) |
| D3 condition 3 — a question goes to the assistant | Tasks 9, 11; held by measurement (Task 12, rule 4) |
| D3 condition 4 — resume of a model plan through the route | Task 7 (`MODEL_PROPOSED`), Task 8 |
| router deleted; guards and anchors of §7.12.3 | Task 11 (§0.9's table, every row) |
| payload guard and two ADRs | Task 11 (Steps 7, 9) |
| transport measurement, outside the gate | §0.3, Task 6 (harness, machinery), Task 12 |
| criteria 14–21 | 14: Task 11 (M11g–M11i); 15: Tasks 9, 11 (M9a, M11a, M11l); 16: Task 4 (M4a, M4b); 17: Tasks 7, 9 (M7c, M9b); 18: Tasks 3, 4, 11 (M3d, M4c–M4e, M11e); 19: Tasks 4, 9, 12 (M4f, M9c); 20: Task 8 (M8a); 21: Task 11 (M11b–M11e, M11j) |
| criteria 1–13 of §10 that this phase touches | 6, 7, 8 (Tasks 3, 5, 11); 10 (frozen — Global Constraints); 11 (every task's Step 6; Task 13); 12 (Task 11 Step 8) |

**Placeholders scanned.** Three deliberate places name what to copy rather than repeating it:
- Task 5 and Task 10 name `DoctrineGuardTest`'s and `LauncherScreen.kt`'s import paths as the source for
  a handful of imports the plan writes from reading, not compiling;
- Task 9 Step 1 asks the implementer to read `ExecuteActionUseCaseTest:110`'s `OpenAssistant` argument
  and put it in the new constructor call;
- Task 12 cannot carry numbers that do not exist yet; it carries the rules that produce them.

**Type consistency checked.** Used identically wherever they appear: `ModelPlanner.plan(command)`,
`ModelPlanning.{Proposed, ToAssistant, Navigate, Clarify, Declined, Unavailable}`, `ProposedStep(tool,
args, origins, risk, effect)`, `ValueOrigin.{SaidBy(PrincipalId), Ours, ModelGenerated}`,
`BridgeFilter.offeringOf`, `BridgeRendering.{INSTRUCTION, systemText, offeringText, schemaJson,
retryNote, KIND_*}`, `BridgeError`, `BridgeRequest(system, schemaJson, command, retryNote,
maxOutputTokens)`, `TransportReply.{Answer, Unparseable, Failed}`, `ReplyMeta`, `BridgeTransportMode`,
`Interpretation.{Act(goal, step, origins, effect, choice), Navigate(destination)}`, `AppChoice(arg,
mention, candidates, learning)`, `choose`, `plan`, `MentionBinder.bind(goal, proposed)`,
`Binding.{Bound, NotFound, Unavailable}`, `AppCandidates.{None, One, Several, Unavailable}`,
`AuthoredToolDescriptions.MENTION_ARG`, `InAppToolIds.{WEB_SEARCH, OPEN_URL}`, `SystemIntentToolSource.PIN_ARG`,
`Resumption.{Ran, NeedsRoute}`, `CommandOutcome.{Interpreted, OpenAssistant(question)}`,
`CommandMessage.UnderstandingModelUnavailable(reason)`, `ModelUnavailableReason`,
`OutboundContextPolicy.AllowedContext.AUTHORED_TOOL_SCHEMA`, `FakeModelPlanner.{answer, planCallCount,
lastCommand}`, `FakeMentionBinder.{binding, bindCallCount}`, `FakeBridgeTransport.requests`,
`LauncherViewModel.{interpretation, thinking, runInterpretation, dismissInterpretation,
chooseInterpretationApp, cancelThinking}`, `ProductionGraph.{federation, shortcutSource, grantsEverything}`,
`BridgeOfferingGuardTest.OFFERED_NINETEEN`.

**Review Focus.** 1, 2, 3 → Task 9; 4 → Task 6; 5 → Tasks 7, 11.

**Gate chain (hypothesis):** 1560 → T1 **1563** → T2 **1571** → T3 **1583** → T4 **1608** → T5 **1614** →
T6 **1632** → T7 **1646** → T8 **1651** → T9 **1662** → T10 **1670** → T11 **1644** → T12 **1644**.
