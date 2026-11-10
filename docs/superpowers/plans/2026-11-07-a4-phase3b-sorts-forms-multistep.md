# A4′ Phase 3b — sorts, word forms, multi-step plans · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: written 2026-11-07, reviewed once on 2026-11-08 against the tree and the spec, every finding
> applied (§0.11). Approved by the owner 2026-11-08, together with §0.1's two refinements. Not started.** Branch `launcher--7`, base `4138c1f`, gate baseline **1673 / 0 / 0**
> (measured at the start of this session: boundary `tools/gate.sh`, cleared tree, `557 executed`, per
> module exactly as 3a closed).
> - **Spec:** [A4′ §7 — §7.12 governs, scope §7.12.4 «3b»](../specs/2026-09-21-a4-runtime-design.md), as
>   amended 2026-11-07 by the owner's answers to this plan's forks (§0.1; the amendment note sits at the
>   end of §7.12.4). §7.8 (sorts, `F6`), §9, §10, §11 in force.
> - **Form:** [A4′ phase 3a](2026-10-03-a4-phase3a-bridge-first-slice.md) — its Global Constraints,
>   mutation protocol and «Measured in execution» practice are inherited unless this plan says otherwise.
> - **Record of phases 0–3a:** `§HANDOFF` of [the track plan](2026-08-18-agentic-track-restart.md).
>
> **How this plan was produced — the owner's rule of 2026-09-26** («как пишутся планы фаз 2–4»). Run
> before writing: **only the scope-deciding measurement** — the re-run of the shipped pair «50 s × 4 096»
> (§0.3; rules written before the numbers, the owner authorised the paid run). **Everything below is a
> hypothesis:** every address, every test verdict, every predicted `FAILED ::` set, the per-module gate
> chain. Execution checks each one; a mismatch is a finding and the plan is corrected in place.
>
> **One deliberate departure from 3a's form (writing-plans v7):** tasks carry **interfaces, test names
> and their assertions, and the algorithm where the signature does not determine it** — not function
> bodies. Consequences: (1) the implementer tier is `implementer-critical` by default (no task carries
> complete code — the tier rule of 2026-10-04); (2) mutation plants are **semantic** (the function, the
> construct, the edit) and the prover fixes the exact string against the implemented code —
> `mutation-prover-deep` where the plant must be designed, `mutation-prover` where a row names one
> expression to flip. The predicted red set is still the proof, and still written here.

**Goal.** The bridge learns what a word means, in any language, and plans more than one step — while
nothing about the user's world leaves the phone:
- **sorts** — a word becomes a value (an app, a duration, a time, a URL, a sandbox file) through a
  resolver registered per sort, before any card; `ArgType` stays `STRING` for good (ADR closing `F6`);
- **word forms in any language** — one universal matcher («край слова») over a platform text-folding
  port; a form-match is only ever **offered** for confirmation, and a confirmation is learned; FastPath,
  2b and the bridge answer the same whole name the same way;
- **R14-39 closed locally** — a target the tool vocabulary claimed is searched in windows of the user's
  words on the device; never sent to the model;
- **multi-step model plans** with references to earlier steps' outputs, bound on the device; the
  consent card shows the value a step will receive;
- **re-planning without egress** — an acted, paused or failed model plan continues as its re-bound
  **remainder**, through the card again; the model is not asked;
- **`:consumer:jvm` parity** — the same bridge on the JVM, its own transport, its own sort (a file);
- the budget re-measured (45 s), a reasoning cap behind a switch, decided by an in-phase measurement.

**Architecture.**
- **`:domain` (`commonMain`).** `TextFolding` port + `PlainTextFolding` + `ScriptFolding`;
  `AppNameMatcher` (whole-name exact/edge; windows); sort vocabulary — `ArgSort`, `ArgSorts`,
  `SortResolver`, `SortResolution`, `ArgumentSorts`; pure sorts `DurationSort`, `ClockTimeSort`,
  `UrlSort`; `PlanningResult.Unresolved`; multi-step bridge answer (`RawAnswer.steps`, `RawArg`,
  `ModelArg.acceptsRef`, `ProposedStep.refs`, `ModelPlanning.Proposed.steps`, `Interpretation.Act.steps`);
  `RemainderPlanner`; `UndoAgentSessionUseCase` (N2); offering levels as a parameter; `BridgeBudget`
  re-measured, `reasoningMaxTokens`.
- **`:data:repository`.** `IcuTextFolding`; `CatalogArgumentSorts` (replaces `ToolArgumentSorts`),
  `AppSortResolver`; `AppTargetResolver` over `AppNameMatcher` with aliases; `ToolMatchPlanner`
  (sorts, `SecondGoal` gate, windows, `Unresolved`); `DeviceMentionBinder` (sorts, N steps, `rebind`);
  workers read values through the pure sorts.
- **`:data:ai-cloud`.** The transport reads `steps[]` and reference objects; sends a reasoning cap only
  to OpenRouter and only when the budget names one.
- **`:feature:launcher`.** The card lists N steps; picks are owed in order; a word-form pick asks
  «Вы имели в виду…»; the consent card shows a resolved reference; a 2b target pick that binds; «Повторить
  остаток»; FastPath «apps» opens the grid; a failed resume says so.
- **`:consumer:jvm`.** `JdkTextFolding`, `JdkBridgeTransport` (`java.net.http` behind a sender seam),
  `SandboxToolDescriptions`, `SandboxFileSort`, `SandboxMentionBinder`, a model path in `ConsoleHarness`.
- **`:app`.** DI for the sort registry, the folding, the levels; guards.

`ActionIds`' seven values, `ObservedFact`, `CommandFailure`, `GoalShape`, `ArgType`, `ToolDescriptor`'s
type, `ExecutionState`, `TraceEvent`, `OutboundContextPolicy.ALLOWED` and the Room schema (4) are not in
the diff.

**Tech Stack.** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain`
(`commonMain` + `jvmTest`) · `android.icu.text.BreakIterator` (API 24+) · `java.text.BreakIterator` +
`java.net.http` (JDK 17) on the JVM consumer · Ktor 3.0.1 + `MockEngine` · kotlinx-serialization-json ·
JUnit4 + `kotlinx-coroutines-test` · Robolectric + Compose UI test.

---

## Global Constraints

Every task's requirements implicitly include this section. Everything 3a's Global Constraints say about
**running the gate, reading the results, the mutation protocol, fixtures and commits** holds verbatim,
with these values replaced:

- **Floor and baseline: 1673 / 0 / 0** (`tools/gate.sh`, `BASELINE_TESTS = 1673`). Per module:
  `:domain` 512 · `:consumer:jvm` 72 · `:data:repository` 433 · `:feature:launcher` 256 · `:app` 76 ·
  the other eight 324 (`:data:ai-cloud` **40**, `:core:android` 12). The floor stays 1673 for the whole
  phase and is raised at close to the measured total (3a's B-8).
- **Predict the decomposition before every boundary run and compare after** (§0.8).
- **Stop the daemon before a boundary gate that follows mutations; the known timing-sensitive test**
  (`first token arriving just under the deadline still completes normally`) is a reason to re-run, not
  to believe.
- **`:core:ui` is not touched** (the card reuses existing components). If a task must touch it, that is a
  finding: stop, add `:core:ui:verifyRoborazziDebug` to the task.
- **Fixtures (R14-43): no two expected values share a substring, none is a real app; assertions compare
  by equality.** 3a's set holds («Lumora», «Vemblic», two «Tessar», «Zantor», aliases
  «кварцелот»/«плимдорф», «телега», shortcut «Brimwell», URL `https://quillmere.example/path?q=a&b=c`).
  **Added for 3b** (checked against each other in execution; a clash is a finding):
  - word forms: alias «кварцелот» typed «кварцелота»; «Миравин» (`net.fixture.miravin`) typed «миравину»;
    «Vemblic» typed «Vemblic'i»; Arabic «سنداف» (`net.fixture.sindaf`) typed «بالسنداف» and vowelled
    «سِنْدَاف»;
  - an edge pair that must stay a choice: «Плинта» (`net.fixture.plinta`) and «Плинт»
    (`net.fixture.wexdor`), typed «плинту» (accusative of the one, dative of the other). The two **labels**
    share a substring on purpose — the edge rule is what is under test; every assertion over them is an
    equality of package lists, never a `contains`. *Measured in execution (Task 4, ruling E4-P1): the
    plan's «Плинтарь» fails clause (i)'s ratio (3·5 = 15 < 2·8 = 16) — the rule is right, «плинтарь» has
    no form «плинту»; the fixture changed, B-2 did not;*
  - a window pair: «Velmo» (`net.fixture.velmo`) and the two-word «Velmo Pad» (`net.fixture.quarnet`);
  - durations «10 dakika», «полчасика» (unreadable); time «7:30»; URLs `HTTPS://Quillmere.example/Path`
    and `https://quillmere.example/a b`;
  - sandbox file «orvelle.txt»; CJK «打开微信» and Thai «เปิดไลน์» appear only as segmentation inputs.
- **Frozen, checked with `git diff --stat 4138c1f -- <path>` at close:** spec §10 criterion 10
  (`ActionIds` values, `ObservedFact`, `CommandFailure`, `GoalShape`; `ArgType` unchanged — the `F6` ADR
  closes it as permanent `STRING`); `ToolDescriptor`'s type; `ExecutionState`; `TraceEvent`;
  `OutboundContextPolicy.ALLOWED`; the Room schema (`schemas/…/4.json`, no `@Entity` edit); every
  `strings_locked.xml`; everything under `core/ui/src/main`.
- **`:domain` stays stdlib + coroutines.** `TextFolding`'s ICU and JDK implementations live outside it.
  `:consumer:jvm` keeps zero Android artifacts (`ModuleIsolationTest`); `java.net.http` is JDK.
- **Strings:** five new keys (Tasks 4, 6, 10, 11), `en`/`ru`/`tr` in the same commit, in
  `feature/launcher/src/main/res/values*/strings.xml`; the `tr` text joins the owner's Turkish review at
  block acceptance.
- **A line predicting what the user will see is a hypothesis** until run once on a phone.
- **Nothing about the user's world leaves the device** (3b-A, D1): no task adds anything to a bridge
  request but the command, our words, the schema and the generation limits (the reasoning cap is a
  generation limit, `GENERATION_LIMITS`).
- Commits: Russian, scope `agentic-6/A4' ф3b`, trailer `Co-Authored-By: Claude Opus 5.5
  <noreply@anthropic.com>`; the agent commits and never pushes; the phone is not touched;
  `connectedAndroidTest` is never run; Task 15 ends in a **proposed** commit (R7).

## Review Focus

Five input classes the spec implies but no criterion names, each likely to bite a person, each pinned
by a test in the task that owns the code.

1. **A word form that fits two apps** — «плинту» with «Плинта» and «Плинт» installed (E4-P1). Binding either
   would be a guess. → Task 4, `AppNameMatcherTest > edge matches on two apps are both offered, never one picked`.
2. **A reference to an output that never arrives** — the model writes «find orvelle.txt, then delete it»,
   and the find finds nothing. Nothing may be deleted, and the plan must end saying why.
   → Task 13, `ConsoleHarnessTest > a delete bound to a find that found nothing deletes nothing`.
3. **Two ambiguous apps in one command.** Run must wait for both picks; dismissing the second abandons
   the whole reading. → Task 10, `LauncherViewModelTest > two picks are owed one after the other and Run waits for both`.
4. **Resuming an acted plan whose app was removed meanwhile.** The remainder must say «нет такого
   приложения», never launch a stale package. → Task 11, `DeviceMentionBinderTest > a remainder whose app is gone is not found`.
5. **A URL the model wrote with an upper-case scheme or a space inside** — Android's filter matching is
   case-sensitive; a space is not a URL. Normalised or refused **before** the card.
   → Task 6, `DeviceMentionBinderTest > a url the model wrote is normalised or refused before the card`.

---

## 0. Pre-flight

### 0.1 · The owner's forks — answered 2026-11-07, before this plan was written

One at a time, each with meaning, options and cost. **Answer line: `A: a · B: b (no new tool) · C: a ·
D→G · E: a′ · F: 1,2,3 · G: a‴ + Arabic · H: a`.**

| Fork | Answer | What it decides |
|---|---|---|
| **3b-A** do observations leave the device | **(a) nothing leaves** | D1 without exception; `ALLOWED` unchanged. `priorObservations`' consumer is local: an acted model plan continues as its re-bound **remainder** (new session, card again); the model is not asked |
| **3b-B** shape of a model plan | **(b) with references, no new tool** | `steps[1..RuntimeBudget.maxSteps]`; an argument is words or `{from_step, key}`; the model writes the wiring, the device carries the value. Consumer: the JVM. Android's boundary waits for its first producer — **named**. The consent card shows the resolved value |
| **3b-C** sort system, `F6` | **(a) a separate axis** | open `ArgSort` + a resolver per sort, registered per consumer; `ArgType` stays `STRING` permanently (ADR) |
| **3b-D** word forms | superseded by **3b-G** | «all paths» stays (FastPath, 2b, bridge — one rule); language tables dropped |
| **3b-E** R14-39 | **(a′) local, with windows** | a target the vocabulary claimed is searched in the windows of the user's words; never step 7 |
| **3b-F** small candidates | **learned-choice key; `continueSession` null; FastPath «apps»** | the orphan `Running` row stays named |
| **3b-G** multilingual binding | **(a‴) universal + learning, plus Arabic readiness** | `TextFolding` (ICU / JDK), the edge rule at both ends → «Вы имели в виду…?» → a confirmation is a learned choice (launch only); no language tables |
| **3b-H** reasoning cap | **(a) switch + in-phase measurement** | named `reasoningMaxTokens`, off by default; Task 14 measures off and on |

**Two refinements the plan review forced, applied under the owner's rules and listed for the owner at
close** (§0.11 B1):
- **3b-E «одно → план» is narrowed to «окно, равное всему слоту → план; окно короче слота → на
  подтверждение».** Phase 1's owner fork F1-2 («the launch arm withdraws only after the **whole** name
  failed») and its pinned tests (`HandleUserCommandUseCaseTest:394-402`, `FastPathOverflowSeamTest:91-121`)
  mean extra words may name a different, uninstalled app («lumora studio»); binding the shorter window
  would act on a guess. So a partial window is offered, never bound.
- **Windows run at 2b only**, after phase 1's `SecondGoal` check (B-15); FastPath and the bridge use the
  whole-name match.

**Recorded beside the forks (owner, 2026-11-07):** a UI in any language («как в Android») is a candidate
**block i18n-3** — `§HANDOFF`, not 3b.

### 0.2 · Decisions this plan takes (the owner may overturn any; each is named at close)

- **B-1 · `TextFolding` — three implementations, one contract.** `ScriptFolding.foldChars` (shared):
  invariant `lowercase()`; drop combining marks **in two ranges only** — U+0300–U+036F (Latin/Greek/
  Cyrillic; covers the U+0307 of «İ».lowercase()) and the Arabic harakat U+064B–U+065F, U+0670 (marks of
  other scripts are kept: Thai and Devanagari vowel signs are part of the word); map `أ إ آ ٱ → ا`,
  `ى → ي`, `ی → ي`, `ک → ك`, `ة → ه`; drop U+0640 (tatweel); drop `'` and `’` between two letters.
  `words` = the platform's segments (`PlainTextFolding`: the whole text; `IcuTextFolding`:
  `android.icu.text.BreakIterator.getWordInstance(ULocale.ROOT)`; `JdkTextFolding`:
  `java.text.BreakIterator.getWordInstance(Locale.ROOT)`), **each then split on `[^\p{L}\p{M}\p{N}]+`**,
  empty pieces dropped — so `orvelle.txt` and `7.30` are two words on every platform. Transliteration is
  **not** built (`Transliterator` is API 29; `minSdk` 28).
- **B-2 · The edge rule** (an algorithm the signature does not determine — Task 4 carries it) with
  three named constants `MIN_STEM = 3`, `MAX_ENDING = 3`, `MAX_PREFIX = 3` and a ratio condition —
  **chosen, not measured**; a wrong value costs one question, never a bind, because an edge match is only
  ever offered.
- **B-3 · A confirmation is learned through the existing learned-choice store and policy** (streak ≥ 3
  auto-resolves) — no new store, no new constant. Launch only; destructive tools never read it.
- **B-4 · One key for a learned launch choice on every path:** `LearnedChoiceKeys.launch(name, folding)` =
  `CapabilityKey(launch_app, folding.fold(LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))))` *(Measured in
  execution, E4-F3: `normalize` before `slotOf`, which documents normalised input; keeps 3a's keys byte-identical
  for unmarked names)*, `name` = the app name the
  path extracted (FastPath `displayNameQuery`, the bridge's mention). Old `ru`/`tr` rows keyed on the
  whole phrase stop matching: asked again (safe, no migration).
- **B-5 · Duration and clock-time sorts validate, they do not rewrite** (the worker reads the user's
  words with the same pure parser; persisted plans and step lines unchanged). The URL sort normalises
  (lower-case scheme) and refuses whitespace and other schemes.
- **B-6 · `PlanningResult` gains `Unresolved`** (spec §3 0.0 built the two compile-error addresses for
  this): `CompositePlanner` returns it as the first planner's answer; `StartAgentSessionUseCase` gains
  `planning(goal)`; `plan(goal)` keeps `ExecutionPlan?` (`Unresolved` → `null`).
- **B-7 · A 2b pick is not an interpretation card and not FastPath's launch list.** `CommandOutcome.ChooseTarget`
  is drawn by the interpretation surface's pick list (`AppChoiceList`, which binds); a tap binds and
  starts the session directly — criterion 15's second half holds. FastPath's own confirm keeps
  `CommandFeedback.Ambiguous` (a tap **launches** — the act it confirms is a launch) with a `confirm`
  flag for the title.
- **B-8 · Android describes no tool output to the model**, and the reference sentence is in the
  **offering text only when an offered tool describes an output** — the Android prompt never mentions
  references. The JVM sandbox descriptions declare theirs.
- **B-9 · A reference error is `BridgeError.SHAPE`** — no fifth code; retry notes and E4-R1 untouched.
- **B-10 · The remainder is for model plans.** = the steps after the last step that acted
  (`Effected`/`HandedOff`), references into finished steps replaced by their values
  (`priorObservations`), re-indexed from 0. A call in flight still resumes once (`DOC-HMA-1`); an undo
  plan, and **a deterministic acted plan**, still continue from the cursor
  (`ResumeAgentSessionUseCaseTest:128-153` stays green and is named). `PlanningRequest` gains
  `previous: ExecutionPlan? = null`.
- **B-11 · N2 is answered «partial»** (`DOC-ILM-4`): `UndoAgentSessionUseCase.review` offers undo for a
  `Cancelled` session in which a step acted; Android draws the finished card for it; the JVM harness
  already calls `offerUndo` for every resolved state, so it follows the domain. Owner may overturn.
- **B-12 · The offering's levels become a parameter** — `offeringOf(registry, levels, descriptions)`
  (levels **second**, so the trailing-lambda calls `offeringOf(registry) { … }` become
  `offeringOf(registry, levels) { … }`); `BridgeModelPlanner(…, levels)` without a default; Android
  passes `BridgeFilter.ANDROID_OFFERED_LEVELS` (F3a-A's allow-list), the JVM `{sandbox}`.
- **B-13 · The JVM transport is `PROMPT_ONLY` only**, over `java.net.http` behind a `BridgeHttpSender`
  seam (tests use a fake sender; production's checks `https://` before sending), key from
  `SIDR_JVM_API_KEY`; the wire parse is duplicated from `:data:ai-cloud` and held by the same golden JSON
  in both modules (Tasks 8, 13). A live JVM run is never in a gate.
- **B-14 · The in-phase measurement (Task 14) runs `PROMPT_ONLY` only**, reasoning off and on, through
  environment switches validated by the harness (the script's argument parsing is not touched).
- **B-15 · At 2b, phase 1's `SecondGoal` check runs before the windows:** a slot carrying a second goal
  answers `NoPlan` and reaches the model, which now plans several steps — the F1-1 rule applied to the
  new windows; nothing is silently dropped.
- **B-16 · The reasoning cap is sent only when the provider's base URL host is `openrouter.ai`** — the
  one provider the measurement covers; OpenAI's endpoint rejects an unknown field with 400
  (`REQUEST_REJECTED`). Another endpoint never receives it.
- **B-17 · Derivability stays exact** (after folding): «a fragment or another form is not the user's
  word» (3a, `DerivabilityTest:22-30`) holds. The instruction already tells the model to repeat the
  user's words exactly; the device then edge-matches the **typed** form («телегу») against labels and
  aliases. A model that writes the lemma pays one `UNDERIVABLE_MENTION` retry.
- **B-18 · An argument accepts a reference only where its description says so** (`ModelArg.acceptsRef`);
  a reference elsewhere is `SHAPE`. Mentions stay words (derivable); a sandbox path argument is a mention
  that also accepts a reference.
- **B-19 · Several launch picks in one reading:** each pick on a `launch_app` step keeps its own
  learning token; all are recorded at «Выполнить» through the one gated call, in step order.

### 0.3 · The measurement this plan stands on — the re-run of «50 s × 4 096» (done before the plan)

**Rules, written 2026-11-07T22:48+03:00 before the run** (П0–П5): validity; ceiling by 3a's rule 2
formula plus a direct count above 50 s; truncation triggers a reasoning-cap fork, never a blind raise;
transport re-checked; question share recorded; per-model comparison with the first run.

**Run:** owner-authorised, same seven models, same corpus (32 lines), three modes, 672 lines, exit 0;
the key absent from both outputs (checked); raw in git-ignored
`.superpowers/sdd/2026-11-07-a4-phase3b/measure-2026-11-07b/`.

| `PROMPT_ONLY` | usable · retried · median · p90 ms |
|---|---|
| claude-haiku-5.5 | 32 · 0 · 1 811 · 2 567 |
| gpt-6-luna | 32 · 0 · 2 658 · 3 543 |
| gemini-3.8-flash | 32 · 0 · 3 516 · 6 639 |
| deepseek-v4.1-flash | 32 · 0 · 1 542 · 5 142 |
| claude-sonnet-5.5 | 32 · 1 · 2 369 · 3 891 |
| qwen3.7-flash | 32 · 1 · 8 777 · 27 342 |
| llama-3.1-70b-instruct | 31 · 6 · 819 · 1 531 |

- **П1 → `ceilingMs = 45 000`** (27 342 × 1.5 = 41 013 → 45 000). One line above 50 s and the same one
  above 45 s (qwen, 91 751 ms, two attempts, the first cut by the limit) — cut at either ceiling;
  nothing between 45 and 50 s. → Task 1.
- **П2 fired** (`truncated = 1`, qwen; deepseek at 4 044 / 4 096 with 4 020 reasoning) → fork 3b-H.
- **П3:** `PROMPT_ONLY` 223 / 224 against 159 and 142 — unchanged.
- **П4:** 30 / 35 questions to the assistant, 4 / 35 to web search (qwen 3, Sonnet 1).
- **П5:** no model fell by more than one line (llama 32 → 31, one `UNREADABLE` after retry).

**Task 14's rules, written now, before its numbers.** Inputs: the 32 lines + 8 `multi` lines (agent's
draft labels, the owner may edit before the run), `PROMPT_ONLY`, seven models, through OpenRouter,
reasoning **off** and **on** (`on` = `maxOutputTokens / 2` = 2 048 — derived, not new); 560 lines.
- **R1 — the multi-step form is not a regression:** Σ usable over the 32 single lines, reasoning off,
  ≥ 220 / 224. Below → finding, owner (the instruction is not changed by the agent to fit).
- **R2 — multi share:** `multi` lines answered with ≥ 2 steps — recorded, not enforced.
- **R3 — reasoning default ON (for OpenRouter only, B-16)** iff for every model
  `REQUEST_REJECTED(on) ≤ 1` and `usable(on) ≥ usable(off) − 1`, **and** (Σ `truncated(on)` <
  Σ `truncated(off)` **or** max p90(on) < max p90(off)). Otherwise OFF.
- **R4 — ceiling:** П1's formula over the chosen setting's `PROMPT_ONLY` p90 (all 40 lines).
- **R5 — questions share** recorded.

### 0.4 · Facts read out of the tree (at `4138c1f`)

- `ModelPlanning.Proposed(step: ProposedStep)` — one step (`ModelPlanning.kt:52`); `Interpretation.Act(step,
  …, choice)`, `plan()` = one-step plan (`Interpretation.kt:22-61`).
- The bridge port is **already in `commonMain`** (`domain/…/ai/bridge/`, 12 files) — §0.5.
- `BridgeFilter.OFFERED_LEVELS = setOf(IN_APP, SYSTEM_INTENT)` is hard-coded (`BridgeOffering.kt:33`);
  `ToolSchemaWeightTest.kt:59` calls `offeringOf(registry) { … }` with a trailing lambda.
- `RawStep.args: Map<String, String?>`; the transport throws on a non-primitive argument
  (`OpenAiCompatibleBridgeTransport.kt:219-223`).
- `INSTRUCTION` speaks of one `step` in its form line and four «… are null» phrases, «A plan has exactly
  one step» (`BridgeRendering.kt:27-46`); `ToolSchemaWeightTest` pins its length (1 140) and the
  rendering of 15 tools (1650..1690).
- `Derivability.words` = `lowercase()` + strip U+0307 + split on `[^\p{L}\p{N}]+` — an Arabic vowel mark
  splits a word (`Derivability.kt:21-27`); `DerivabilityTest:22-30` pins «a fragment / another form is
  not derivable», `:38-41` the dotted I.
- FastPath resolves names in `IntentActionResolver.resolveAppLaunch` by `label.lowercase(Locale.ROOT) ==
  query` (`:60-79`); phase 1 pins «lumora studio» → `NoAppFound` (`HandleUserCommandUseCaseTest:394-402`,
  `FastPathOverflowSeamTest:91-121`, the `DOC-NYH-4` anchor). 2b and the bridge resolve through
  `AppTargetResolver.candidates` (`:51-68`), aliases first, exact only; `AliasStore` offers `find` and
  `observeAll()`.
- `parseSeconds` / `parseClockTime` are private in `Tier0IntentToolWorker` (`:378-391`, `:327-332`) and run
  after «Выполнить»; the `open_url` scheme check is in `SystemIntentToolWorker` (`:44`, `:97`).
- `AgentExecutor.prepare`: the consent checkpoint (`:131`) precedes binding (`:148`).
- `ResumeAgentSessionUseCase.resume`: an acted plan continues from the cursor (`:48`), pinned for a
  deterministic plan by `ResumeAgentSessionUseCaseTest:128-153`; `Resumption` is `Ran | NeedsRoute`.
- `UndoAgentSessionUseCase.review` (`:32-36`): `Cancelled` → nothing; `Blocked` ending `HandedOff` →
  nothing; pinned by `UndoAgentSessionUseCaseTest:140-148`.
- `LauncherAgentSession.continueSession`: `null -> show(null)` (`:139`).
- `LauncherCommandSession.applyOutcome(ShowApps)` clears the field only (`:401-404`); the bridge path
  navigates itself (`:456-461`); `CommandOutcome` has exactly two exhaustive `when`s (`:247`, `:406`).
- `CommandFeedback.Ambiguous(candidates)` launches on tap (`CommandFeedback.kt:44`,
  `LauncherCommandSession.kt:373`, `LauncherScreen.kt:737`, `LauncherPresentation.kt:37-110`).
- `ResolveCommandWithPreferenceUseCase` builds its key at `:33` from `slotOf(normalize(rawInput))`;
  `CommandOutcome.NeedsConfirmation(candidates)` carries no query (five construction sites).
- `ToolArgumentSorts`: 33 references in 16 files; its guard is `ToolArgumentSortGuardTest`
  (`:data:repository`, 2 tests).
- `find_file` requires `query` **and** `root` (`SandboxToolSource.kt:76`); `FilePlanner` writes three
  steps, `workspace_info` → `find_file(root ← 0)` → `delete_file(path ← 1)` (`FilePlanner.kt:69`, `:80`);
  `ConsoleHarness.gate` calls `offerUndo` for every resolved state and has no `when` over `Resumption`.
- Room persists `ArgSource.FromStep` (`AgentSessionMappers.kt:237`, `:320`) — no migration.
- `:data:repository` has Robolectric; `:core:android` does not.

### 0.5 · Where the spec and the tree diverge, and what each changes

- **«Порт моста в `commonMain`»:** already there since 3a. JVM parity = transport + descriptions + file
  sort + binder + offering levels as a parameter (Task 13).
- **«Гвард, что обратный канал несёт только закрытый словарь `ObservedFact`»:** the owner answered
  «nothing leaves» (3b-A). The guard becomes «no remainder path asks the model» (Task 11). Spec note.
- **«Формы слова» (§7.8; 3a's Review Focus 5):** a universal matcher and learning, not tables (3b-G), on
  every path, including FastPath — which closes the Turkish FastPath debt **at confirmation level**.
  Derivability stays exact (B-17).
- **R14-39 (§7.12.3: «→ 3b или владелец»):** closed (3b-E, as refined in §0.1).
- **The spec's 3b list does not name** the learned-choice key, the `continueSession` null branch,
  FastPath «apps» — the owner added them (3b-F).

### 0.6 · What this plan does NOT do — said, not implied

| Not here | Why / where |
|---|---|
| An Android tool that produces a value for a later step | 3b-B: named; the boundary waits for its first producer |
| UI in any language | candidate block i18n-3 (`§HANDOFF`) |
| Transliteration («телеграм» ↔ Telegram, واتساب ↔ WhatsApp) | `Transliterator` is API 29 > `minSdk` 28; aliases cover it |
| Language morphology tables | 3b-G; tables only if a measurement shows confirmations too frequent |
| Edge-tolerant derivability | B-17 |
| Observations to the model | 3b-A |
| A remainder for deterministic acted plans | B-10: they continue from the cursor, as since A0 |
| Re-planning on resume in the JVM harness («паритет возобновления упряжи», phase 2 `§HANDOFF`) | phase 2's P9 stands: sandbox reads answer `Effected`, `ObservedFact` is frozen — the stand cannot express staleness; named |
| Branches, conditional steps from the model | D4, out of A4′ |
| The orphan `Running` row | 3b-F: named |
| Merging the interpretation and consent cards; a 0-step spoken reply; «составить текст» | out of A4′ (§7.12.4) |
| The reasoning cap for providers other than OpenRouter | B-16 |
| Any device round | one acceptance, at A4′'s end |

### 0.7 · Owner dependencies and hypotheses

**Owner dependencies.** (1) Task 14's paid run (~560 requests, ≈ $0.5) and the `multi` lines' labels;
(2) the Turkish review grows by **five** strings; (3) §0.1's two refinements and §0.2's decisions (B-11,
B-15, B-17 first) are listed at close; (4) the doctrine-matrix edits of Tasks 5, 11, 12 and two ADRs
(Task 3; Tasks 1+14) at close; the close commit is proposed (R7).

**Hypotheses for the block's acceptance checklist — nobody has seen these on a phone.**
- «открой телегу» (alias «телега») is expected to ask «Вы имели в виду «…»?» with one app, and to launch
  on the tap; after three confirmations it is expected to launch without asking.
- «telegramı aç» / «Telegram'ı kaldır» with Telegram installed are expected to ask «did you mean»; the
  uninstall then shows the engine's consent card naming the package.
- «удали приложение миравин» is expected to plan the uninstall locally; «удали приложение миравин
  пожалуйста» to ask «did you mean «Миравин»?» (a window short of the slot); «удали приложение
  несуществующее» to read «не нашёл…» with **no** model wait — online or not.
- «поставь будильник на 7:30 и открой lumora» (online) is expected to show one card with two lines; Run is
  expected to set the alarm, then open the app.
- «поставь таймер на полчасика» is expected, online, to reach the model (which may read it as 30 minutes) and,
  local-only / without a provider / offline, to read that state's honest line — never a card the device could
  not fill. *Measured in execution (Task 6 review, ruling E6-R1): the plan first predicted «не понял «полчасика»»
  at 2b; that would make a deterministic miss a filter on understanding (ADR 1/4) and would also stop the line
  above from ever reaching the model. «Не понял «…»» is now the bridge's answer only — owner item at close.*
- «показать все приложения» (FastPath) is expected to open the app grid.
- An Arabic command naming an installed app with a prefix («افتح بالسنداف»-shaped) is expected to ask
  «did you mean» (bridge path; no Arabic UI exists).
- A paused two-step model plan whose first step ran is expected to come back, on «Продолжить», as a card
  for the second step only.

### 0.8 · The gate chain — predicted before any run

| Module | Base | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | T9 | T10 | T11 | T12 | T13 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `:domain` | 512 | 514 | 521 | 529 | 538 | 542 | 543 | 547 | 552 | 558 | 558 | 564 | 567 | 568 |
| `:consumer:jvm` | 72 | 72 | 74 | 74 | 74 | 74 | 74 | 74 | 74 | 74 | 74 | 74 | 75 | 84 |
| `:data:repository` | 433 | 433 | 436 | 437 | 442 | 446 | 450 | 451 | 452 | 454 | 454 | 456 | 456 | 456 |
| `:feature:launcher` | 256 | 256 | 256 | 256 | 257 | 259 | 260 | 262 | 262 | 262 | 267 | 269 | 272 | 272 |
| `:app` | 76 | 76 | 76 | 77 | 77 | 77 | 77 | 77 | 77 | 77 | 77 | 78 | 78 | 78 |
| the other eight | 324 | 327 | 327 | 327 | 327 | 327 | 327 | 327 | 329 | 329 | 329 | 329 | 329 | 329 |
| **total** | **1673** | 1678 | 1690 | 1700 | 1715 | 1725 | 1731 | 1738 | 1746 | 1754 | 1759 | 1770 | 1777 | **1787** |

*Measured in execution: Task 4's ruling E4-P2 (one `AppSortResolverTest` pin) moves every column from T4 on by
+1 (T4 1716), and Task 5's ruling E5-P1 (an unreadable target list at 2b, two tests: `:domain` +1,
`:data:repository` +1) by +2 more from T5 on — T5 measured **1728** (`:domain` 543, `:data:repository` 448,
`:feature:launcher` 259); Task 5's review round (rulings E5-R1: a paused 2b pick goes back to the route on
resume; E5-R3: the 2b pick is drawn) adds `:domain` +1 and `:feature:launcher` +1 — T5 measured **1730**
(`:domain` 544, `:data:repository` 448, `:feature:launcher` 260); T6 … T13 read 1736 … 1792 until the next
deviation. Task 6's ruling E6-P2 (step 7's `NotUnderstood` mapping gets its own `:domain` test) adds +1 more:
T6 predicted **1737** (`:domain` 546, `:data:repository` 452, `:feature:launcher` 261); T7 … T13 then read
1744 … 1793.*

The other eight move only in `:data:ai-cloud` (40 → 43 → 45). Tasks 14 and 15 add no test (Task 1's
budget pin is edited in Task 14). **No task deletes a test.** Existing tests **rewritten at constant
count** are listed in §0.9 and in each task.

### 0.9 · Guards, anchors and existing tests this plan moves — checked against the tree

| Guard / test | What happens | Where |
|---|---|---|
| `HandleUserCommandUseCaseTest:394-402`, `FastPathOverflowSeamTest:91-121` (F1-2, `DOC-NYH-4`) | **stay green** — FastPath uses the whole-name match | Task 7 |
| `DerivabilityTest:22-30`, `:38-41` | **stay green** (B-17); `:38-41` now rests on `ScriptFolding` (M2a) | Task 2 |
| `DeviceMentionBinderTest:176-180` (3a Review Focus 5: a word form → not found) | rewritten: «телегу» with alias «телега» → a pick to confirm; a not-found case kept on an unrelated word | Task 4 |
| `ToolMatchPlannerTest:301-315` (unresolvable name → `NoPlan`) | rewritten → `Unresolved` | Task 5 |
| `AgentActingSeamTest` (`:data:repository`) R14-39 pin | flips: the model is never asked; its first assertion (the vocabulary claimed the text) kept | Task 5 |
| `ToolArgumentSortGuardTest` (2) | follows the rename; `binding.arg` reads → `SortedArg` | Task 3 |
| `ToolSchemaWeightTest` (1 140; `render(15)` 1650..1690) | both re-pinned on the new text, never widened | Tasks 8, 9 |
| `BridgeRenderingTest` forbidden-terms scan | runs over the new instruction | Task 8 |
| `UndoAgentSessionUseCaseTest:140-148` (`Cancelled` → nothing) | rewritten: `Cancelled` with no acted step still nothing; with one → Task 12's new test | Task 12 |
| `ResumeAgentSessionUseCaseTest:128-153` (deterministic acted plan continues, same id) | **stays green** (B-10) | Task 11 |
| `OfferedToolIdsFreezeGuardTest`, `BridgeOfferingGuardTest`, `BridgeFixtures`, `BridgeTransportMeasurement` | compile-follow `offeringOf(registry, levels, …)` | Task 13 |
| `BridgePayloadGuardTest` | + remainder: zero asks (non-vacuous by control) | Task 11 |
| `DoctrineGuardTest > the composition root's planner list…` | `RemainderPlanner` is not in `CompositePlanner` — stays green | Task 11 |
| `CoreVocabularyFreezeGuardTest`, `AgentVocabularyGuardTest`, `ModuleIsolationTest`, privacy guards | must stay green (`LearnedChoiceKeys` never names `com.sidr.launcher.domain.ai`) | every task |
| `DOC-HMA-3` marker «← долг A4′ ф3: N2 и многошаговый откат на Android»; `DOC-ADL-3`; `DOC-AML-1` | cleared / + test / + test | Tasks 12, 5, 11 |
| KDoc made false: `ToolArgumentSorts`, `ModelPlanning.Proposed`, `Interpretation.Act.plan`, `PlanningRequest`, `ResumeAgentSessionUseCase`, `UndoAgentSessionUseCase.review`, `BridgeBudget`, `DeviceMentionBinder` (key), `AppTargetResolver` (exact only) | rewritten | the task that changes the code |

### 0.10 · How the predictions were produced

Test counts are this plan's own (one row per named test); the review recounted them. No probe ran; the
only pre-plan run was §0.3's measurement and its free dry run (`corpus=32 … offering=19 ceiling=180000`).
The review ran JDK 17's `java.text.BreakIterator` once on `orvelle.txt` (one segment) and checked the
Thai marks' category in Python — both now in B-1.

### 0.11 · What the plan review changed

**One review, 2026-11-08** (`reviewer`, read-only, against `4138c1f` and the spec). **Verdict: FIX
REQUIRED** — 3 blocking, 11 substantial, minor. Each finding was checked against the tree before it was
applied; none was rejected.

| # | Finding | What changed |
|---|---|---|
| B1 | Windows in FastPath and the bridge break F1-2 and the `DOC-NYH-4` anchor («lumora studio», «lumora и проверь почту»); a mention «lumora studio» would bind Lumora | `AppNameMatcher` split into `matchWhole` (FastPath, bridge) and `matchWindows` (2b only); a window short of the slot is `Confirm`, never `One`; §0.1 refinement; B-15 |
| B2 | Task 2's Thai test cannot pass if all Mn are stripped; `BreakIterator` keeps `orvelle.txt` whole — «one contract» was false | B-1: two mark ranges only; segments split again on `[^\p{L}\p{M}\p{N}]+`; a dotted row in the contract |
| B3 | Ten existing tests go red unnamed (Derivability ×3, binder :176, ToolMatchPlanner :301, Undo :140, F1-2 pins) | B-17 (derivability stays exact); every remaining one named in §0.9 and its task, rewritten at constant count |
| S1 | Edge derivability would mark a model-altered query as the user's (`DOC-MZN-2`) | gone with B-17 |
| S2 | Sandbox mapping undetermined: `root` required and unknowable to the model; «ref on a mention → SHAPE» rejects the JVM's own reference | Task 13 specifies every model argument; `root` device-bound; B-18 `acceptsRef` |
| S3 | `https`-only transport vs a plain-HTTP test server | B-13 sender seam |
| S4 | A reasoning field sent to non-OpenRouter endpoints → 400 | B-16; Task 1 test; R3 scoped |
| S5 | `CommandFeedback.Ambiguous` launches on tap | B-7: 2b pick drawn by `AppChoiceList`; FastPath's confirm keeps the launch list with a flag; files listed |
| S6 | Remainder for deterministic acted plans contradicts a pinned test | B-10: model plans only |
| S7 | The sentinel half of the remainder guard was vacuous | dropped; zero asks with a control kept |
| S8 | T3 m3 test direction inverted; wrong file | Task 12 rewritten (`UndoAgentSessionUseCaseTest`, undo **is** offered) |
| S9 | M2a, M4e, M7b, M5a predictions wrong | corrected (dotted-I test; two-word window fixture; the cross-path test drives the production decorator and binder; `AgentActingSeamTest` is `:data:repository`) |
| S10 | 2b windows would swallow a second goal | B-15 + a test |
| S11 | The sort-registry guard vs the app sort | `AppSortResolver` registered for `APP` |
| minor | addresses (`:33`, `ToolArgumentSortGuardTest`, 33 refs in 16 files, no `when` over `Resumption`); incomplete compile lists; both `ToolSchemaWeightTest` pins; every «step» in `INSTRUCTION`; Task 8's interim must refuse >1 step; the reference sentence only with outputs; fixture overlap wording; harness resume parity; M8e fixture; several launch picks | all applied (Task 8 advertises one step until Task 9; B-8; B-19; §0.6) |

**Net effect on the numbers:** T1 +1 (`:data:ai-cloud`), T4 +1 net, T5 +1, T8 −1 / T9 +2 (the step cap
moves), T12 +1, T13 +1 → **1787** at T13 (was 1781). **No second review** under the owner's rule: the
delta is corrections inside the same fifteen tasks — no task, port or closed type added or removed.

---

## Task 1: the budget re-measured, and a reasoning cap behind a switch

**Spec:** §7.12.2, criterion 19; fork 3b-H; §0.3; B-16. **Modules:** `:domain`, `:data:ai-cloud`,
`:app` (harness, outside the gate), `ai-context/decisions.md`.

**Files:** `BridgeBudget.kt`, `BridgeAnswer.kt` (`BridgeRequest`), `BridgeModelPlanner.kt`;
`OpenAiCompatibleBridgeTransport.kt`; `BridgeTransportMeasurement.kt`. Tests: `BridgeModelPlannerTest`,
`OpenAiCompatibleBridgeTransportTest`.

**Interfaces — Produces:**
- `data class BridgeBudget(val ceilingMs: Long, val maxOutputTokens: Int, val reasoningMaxTokens: Int? = null)`;
  `Default = BridgeBudget(45_000L, 4_096, null)`.
- `BridgeRequest(…, val reasoningMaxTokens: Int? = null)` — set from the budget on the first ask **and**
  the retry.
- Transport: the body gains `"reasoning": {"max_tokens": n}` iff `reasoningMaxTokens != null` **and** the
  configured base URL's host is `openrouter.ai`; otherwise no `reasoning` key.
- Harness: env `SIDR_MEASURE_MODES` (comma list of `BridgeTransportMode`; default all three),
  `SIDR_MEASURE_REASONING` (`off` | `on` | `off,on`; default `off`); an unknown value → `BRIDGE-MEASURE ::
  refused …` before any request; the dry run prints both; `on` = `BridgeBudget.Default.maxOutputTokens / 2`;
  each `line`/`summary` gains `reasoning=<off|on>`.

- [ ] **Step 1: failing tests** — `BridgeModelPlannerTest`: `the shipped budget is the re-measured pair`
  (`assertEquals(BridgeBudget(45_000L, 4_096, null), BridgeBudget.Default)`) · `the reasoning cap travels
  with the first ask and the retry` (2); `OpenAiCompatibleBridgeTransportTest`: `a reasoning cap is sent to
  OpenRouter as reasoning.max_tokens` · `no reasoning cap sends no reasoning object` · `a reasoning cap is
  never sent to another provider` (base URL `https://api.quillmere.example/v1`) (3).
  *Measured in execution (E1-F1):* a JVM method name cannot hold `.` — the first test is
  `… as reasoning max_tokens`. *(E1-F2)* «ADR 2» in Step 5 is 3a's second ADR «2026-10-05 — один модельный
  путь…», which holds the «по замеру» addendum — not ADR 2/4.
- [ ] **Step 2:** scoped `:domain:jvmTest`, `:data:ai-cloud:testDebugUnitTest` → red.
- [ ] **Step 3:** implement; `BridgeBudget`'s KDoc: the re-measure, П2, the switch, B-16, and «pinned by
  `the shipped budget is the re-measured pair`, which a legitimate re-measure edits».
- [ ] **Step 4:** scoped green; dry run with `SIDR_MEASURE_MODES=PROMPT_ONLY SIDR_MEASURE_REASONING=off,on`
  prints both; `SIDR_MEASURE_REASONING=maybe` → `refused`.
- [ ] **Step 5:** ADR addendum under ADR 2 «Повторный замер «50 с × 4 096» (2026-11-07b)» — §0.3's table,
  П0–П5 applied, the switch, B-16, what it did not measure.
- [ ] **Step 6: mutations**

| # | Plant (semantic) | Predicted `FAILED ::` |
|---|---|---|
| M1a | `Default.ceilingMs` → `50_000L` | `:domain` — `> the shipped budget is the re-measured pair` only |
| M1b | the retry request drops `reasoningMaxTokens` | `:domain` — `> the reasoning cap travels…` only |
| M1c | transport never writes `reasoning` | `:data:ai-cloud` — `> a reasoning cap is sent to OpenRouter…` only |
| M1d | transport writes `reasoning` when the cap is null | `:data:ai-cloud` — `> no reasoning cap sends no reasoning object` only |
| M1e | the host check removed | `:data:ai-cloud` — `> a reasoning cap is never sent to another provider` only |

- [ ] **Step 7:** `./gradlew --stop`; boundary gate → **1678** (`:domain` 514, `:data:ai-cloud` 43).
  **Step 8: commit** — `feat(agentic-6/A4' ф3b): потолок моста 45 с по повторному замеру, предел рассуждений за переключателем`.

---

## Task 2: `TextFolding` — one folding contract, three platforms, and the Arabic mark defect

**Spec:** fork 3b-G (+ Arabic readiness); §7.12.1; B-1, B-17. **Modules:** `:domain`, `:data:repository`,
`:consumer:jvm`, `:app` (DI).

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/text/TextFolding.kt` (`TextFolding`,
  `ScriptFolding`, `PlainTextFolding`)
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/text/IcuTextFolding.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/text/JdkTextFolding.kt`
- Modify: `Derivability.kt` (`isDerivable(mention, command, folding = PlainTextFolding)` — exact words
  after folding, B-17), `BridgeValidator.kt`, `BridgeModelPlanner.kt` (`folding: TextFolding = PlainTextFolding`),
  `BridgeProvidesModule.kt` (passes `IcuTextFolding`; `@Provides TextFolding` for Tasks 4–7)
- Test: `domain/src/jvmTest/…/text/PlainTextFoldingTest.kt`, `DerivabilityTest`,
  `data/repository/src/test/…/text/IcuTextFoldingTest.kt` (Robolectric), `consumer/jvm/src/test/…/text/JdkTextFoldingTest.kt`

**Interfaces — Produces:**
```kotlin
interface TextFolding { fun fold(text: String): String; fun words(text: String): List<String> }
object ScriptFolding { fun foldChars(text: String): String }      // B-1, shared by all three
object PlainTextFolding : TextFolding                              // segments = the whole text
```

**The contract — six rows, asserted by equality in all three modules:**

| input | expected |
|---|---|
| `İnstagram` | `words == [instagram]` |
| `سِنْدَاف` | `words == [سنداف]` |
| `أإآى` | `words == [اااي]` |
| `ةـ ی ک` | `fold == "ه ي ك"` |
| `Vemblic'i aç` | `words == [vemblici, aç]` |
| `orvelle.txt, 7.30!` | `words == [orvelle, txt, 7, 30]` |

- [ ] **Step 1: failing tests** — `PlainTextFoldingTest`: `a capital dotted I folds to plain i and never
  splits the word` · `harakat are removed before words are split` · `arabic-script letter variants fold to
  one form` · `an apostrophe inside a word joins it` · `marks of other scripts are kept` (`fold("เปิด")
  == "เปิด"`) (5); `DerivabilityTest`: `a vowelled arabic mention is derivable from an unvowelled command` ·
  `an apostrophe form is one word for derivability` (2); `IcuTextFoldingTest`: `folds the six contract rows
  exactly as the plain folding does` · `segments a CJK command into words` (`["打开", "微信"]`) ·
  `segments Thai without spaces` (`["เปิด", "ไลน์"]`) (3); `JdkTextFoldingTest`: `folds the six contract
  rows` · `a Cyrillic file name keeps its digits` (`words("отчёт-2.txt") == [отчёт, 2, txt]`) (2).
- [ ] **Step 2:** scoped `:domain:jvmTest`, `:data:repository:testDebugUnitTest`, `:consumer:jvm:test` → red.
- [ ] **Step 3:** implement. **If Robolectric's ICU lacks the CJK/Thai dictionaries** (hypothesis), the two
  segmentation tests become a named limit in the task record, not a weakened assertion.
  > **Measured in execution (2026-11-08, E2-F1).** The hypothesis was wrong in direction: Robolectric's
  > ICU 72.1 carries both dictionaries (Thai passes; `打开设置`, `打开相机`, `打开地图` split into dictionary
  > words). `打开微信` → `[打开, 微, 信]` — `微信` (also a real app, R14-43) is not a dictionary word. Fixture
  > → `打开设置` → `["打开", "设置"]`. **Named limit:** a CJK app name absent from ICU's dictionary
  > segments into single characters, so a whole-word match over `words()` cannot see it as one word
  > (Task 4; aliases cover it, as for transliteration, §0.6). Also measured: `BridgePayloadGuardTest:209`
  > calls `BridgeProvidesModule.provideModelPlanner` directly — passes `PlainTextFolding` (compile-follow).
- [ ] **Step 4:** scoped green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M2a | `foldChars` drops no combining mark | `:domain` — `PlainTextFoldingTest > a capital dotted I…`, `> harakat are removed…`, `DerivabilityTest > a vowelled arabic mention…`, `DerivabilityTest:38-41` (the dotted I); `:data:repository` — `> folds the six contract rows…`; `:consumer:jvm` — `> folds the six contract rows` |
| M2b | drop the Arabic letter map | `:domain` — `> arabic-script letter variants…` (+ the contract tests of the two other modules) |
| M2c | drop the apostrophe joiner | `:domain` — `> an apostrophe inside a word joins it`, `DerivabilityTest > an apostrophe form…` (+ the two contract tests) |
| M2d | drop all `Mn`, not two ranges | `:domain` — `> marks of other scripts are kept`; `:data:repository` — `> segments Thai…` |
| M2e | `IcuTextFolding` segments as the plain folding | `:data:repository` — `> segments a CJK command…`, `> segments Thai…` |

- [ ] **Step 6:** boundary gate → **1690** (`:domain` 521, `:data:repository` 436, `:consumer:jvm` 74).
  **Step 7: commit** — `feat(agentic-6/A4' ф3b): TextFolding — одна свёртка текста на трёх платформах, огласовки снимаются до разбиения`.

---

## Task 3: the sort system — a separate axis; `F6` closed by ADR

**Spec:** §7.8; fork 3b-C; criterion 10. **Modules:** `:domain`, `:data:repository`, `:app`,
`ai-context/decisions.md`. **No routing change**: wiring is Tasks 4–7.

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/sort/` — `ArgSort.kt`, `SortResolution.kt`,
  `ArgumentSorts.kt`, `DurationSort.kt`, `ClockTimeSort.kt`, `UrlSort.kt`
- Rename: `ToolArgumentSorts.kt` → `CatalogArgumentSorts.kt` implementing `ArgumentSorts` (rows gain
  `set_timer.duration → DURATION`, `set_alarm.time → CLOCK_TIME`, `open_url.url → URL`); 33 references in
  16 files follow; `ToolArgumentSortGuardTest` follows
- Create: `data/repository/…/agent/AppSortResolver.kt` (adapter over `AppTargetResolver.candidates`:
  `One` → `Resolved(pkg)`, `Several` → `Choice(confirm = false)`, `None` → `NotFound`, `Unavailable` →
  `Unavailable`; Task 4 adds `Confirm` → `Choice(confirm = true)`)
- Modify: `Tier0IntentToolWorker.kt` (private parsers deleted; reads `DurationSort.secondsOf` /
  `ClockTimeSort.timeOf`), `SystemIntentToolWorker.kt` (`open_url` reads `UrlSort.normalized`)
- Create: `app/src/main/java/com/sidr/launcher/di/SortProvidesModule.kt` (`Map<ArgSort, SortResolver>`:
  APP, DURATION, CLOCK_TIME, URL)
- Test: `domain/src/jvmTest/…/sort/DurationSortTest.kt`, `ClockTimeSortTest.kt`, `UrlSortTest.kt`;
  `ToolArgumentSortGuardTest` (+1); `app/src/test/…/agent/SortRegistryGuardTest.kt`

**Interfaces — Produces:**
```kotlin
@JvmInline value class ArgSort(val value: String)
object ArgSorts { val APP = ArgSort("app"); val DURATION = ArgSort("duration"); val CLOCK_TIME = ArgSort("clock_time")
                  val URL = ArgSort("url"); val FILE = ArgSort("file") }
data class SortedArg(val sort: ArgSort, val labelArg: String?)
interface ArgumentSorts { fun sortOf(tool: ToolId, arg: String): SortedArg?; fun all(): Map<ToolId, Map<String, SortedArg>> }
data class SortCandidate(val value: String, val label: String)
sealed interface SortResolution {
    data class Resolved(val value: String) : SortResolution
    data class Choice(val candidates: List<SortCandidate>, val confirm: Boolean) : SortResolution
    data object NotFound : SortResolution
    data object Unreadable : SortResolution
    data object Unavailable : SortResolution
}
fun interface SortResolver { suspend fun resolve(words: String): SortResolution }
object DurationSort { fun secondsOf(words: String): Int?; val resolver: SortResolver }    // Resolved(words) | Unreadable
object ClockTimeSort { fun timeOf(words: String): Pair<Int, Int>?; val resolver: SortResolver }
object UrlSort { fun normalized(words: String): String?; val resolver: SortResolver }      // Resolved(normalized) | Unreadable
```
`secondsOf` / `timeOf` are `parseSeconds` / `parseClockTime` **moved verbatim** (unit sets, exact-token rule,
4-digit cap, `H:MM`/`HH:MM`, ranges). `UrlSort.normalized`: trim; any whitespace inside → `null`; scheme
`http`/`https` case-insensitively, else `null`; the input with the scheme lower-cased, the rest byte for byte.

**ADR «2026-11-07 — A4′ ф3b: сорт — отдельная ось; F6 закрыт»:** `ArgType` stays `STRING` permanently — the
type is the wire form, the sort is meaning + resolver, declared beside `ToolDescriptor` by each consumer; a
new sort costs zero edits in other modules (open value class).

- [ ] **Step 1: failing tests** — `DurationSortTest`: `en ru tr unit words read as the worker read them`
  (`10 minutes`, `10 минут`, `10 dakika` = 600; `2 saat` = 7200; `30 сек` = 30) · `a unit is matched whole,
  never by prefix` (`10 minecraft`, `1 min 30 sec` → null) · `a bare number is minutes, five digits are
  refused` (`5` = 300; `12345` → null) (3); `ClockTimeSortTest`: `H:MM and HH:MM resolve` · `out of range and
  other shapes are refused` (`24:00`, `7.30`, `7:3`) (2); `UrlSortTest`: `an upper-case scheme is lowered and
  the rest kept byte for byte` · `whitespace inside an address is refused` · `a scheme other than http or
  https is refused` (`ftp://…`, `quillmere.example`) (3); `ToolArgumentSortGuardTest > duration, clock time
  and url arguments are declared` (1); `SortRegistryGuardTest > every sort the catalog declares has a
  resolver in the production graph` (1).
  *Measured in execution (2026-11-09):* *(E3-F1)* a JVM method name cannot hold `:` — the test is
  `H colon MM and HH colon MM resolve`. *(E3-P1)* «33 references in 16 files» = 16 files besides the
  definition (17 with it; one a KDoc mention in `commonMain` `ToolInverses.kt`). *(E3-P2)* the guard sees
  «the production graph» by calling `SortProvidesModule`'s `@Provides` function with the production
  `CatalogArgumentSorts()` (the `BridgePayloadGuardTest` precedent). Callers find the app argument through
  one method, `CatalogArgumentSorts.appArgOf(tool)`; no other sort is resolved before Task 6.
- [ ] **Step 2:** scoped → red. **Step 3:** implement; `Tier0IntentToolWorkerTest` and
  `SystemIntentToolWorkerTest` stay green untouched (they now prove the move). **Step 4:** scoped green.
  **Step 5:** the ADR.
- [ ] **Step 6: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M3a | remove `"dakika"` from the minute forms | `:domain` — `DurationSortTest > en ru tr unit words…`; `:data:repository` — `Tier0IntentToolWorkerTest` (the `dakika` case, `:200`) |
| M3b | `UrlSort.normalized` returns its input unchanged on success | `:domain` — `> an upper-case scheme is lowered…` only |
| M3c | `SortProvidesModule` omits `CLOCK_TIME` | `:app` — `SortRegistryGuardTest` only |

- [ ] **Step 7:** boundary gate → **1700** (`:domain` 529, `:data:repository` 437, `:app` 77).
  **Step 8: commit** — `feat(agentic-6/A4' ф3b): сорт — отдельная ось, F6 закрыт ADR; длительность, время и URL — чистые резолверы`.

---

## Task 4: one name matcher — whole names on the bridge, windows for 2b; a form is offered, not bound

**Spec:** forks 3b-G, 3b-E (§0.1 refinement); B-2, B-3, B-17. **Modules:** `:domain`, `:data:repository`,
`:feature:launcher`.

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/text/AppNameMatcher.kt`,
  `domain/…/memory/resolution/LearnedChoiceKeys.kt`
- Modify: `AppTargetResolver.kt` (`candidates` over `matchWhole`, aliases exact then edge via
  `observeAll().first()`; `AppCandidates.Confirm(apps)`; `windows(words)` for Task 5), `AppSortResolver.kt`
  (`Confirm`), `DeviceMentionBinder.kt` (`Confirm` → `AppChoice(confirm = true)`; launch learning keyed by
  `LearnedChoiceKeys.launch`), `Interpretation.kt` (`AppChoice.confirm: Boolean = false`),
  `InterpretationSurface.kt` (did-you-mean title when `confirm`)
- Rewrite at constant count: `DeviceMentionBinderTest:176-180` (§0.9)
- Strings: `launcher_interpretation_did_you_mean` — en «Did you mean «%1$s»?», ru «Вы имели в виду «%1$s»?»,
  tr ««%1$s» mı demek istediniz?»
- Test: `domain/src/jvmTest/…/text/AppNameMatcherTest.kt`, `AppTargetResolverTest`, `DeviceMentionBinderTest`,
  `InterpretationSurfaceTest`

**Interfaces — Produces:**
```kotlin
sealed interface NameMatch {
    data class Exact(val apps: List<InstalledApp>) : NameMatch
    data class Edge(val apps: List<InstalledApp>) : NameMatch
    data object None : NameMatch
}
data class WindowMatch(val match: NameMatch, val whole: Boolean)   // whole = the window is all the words
object AppNameMatcher {
    const val MIN_STEM = 3; const val MAX_ENDING = 3; const val MAX_PREFIX = 3
    fun wordsMatch(typed: String, label: String): Boolean?          // true exact, false edge, null none (folded words)
    fun matchWhole(words: String, apps: List<InstalledApp>, folding: TextFolding): NameMatch
    fun matchWindows(words: String, apps: List<InstalledApp>, folding: TextFolding): WindowMatch
}
object LearnedChoiceKeys { fun launch(name: String, folding: TextFolding): CapabilityKey }
```
**The algorithm** (B-2):
1. Typed word `a`, label word `b`, both folded. **Exact:** `a == b`. **Edge** — either:
   (i) common prefix length `p ≥ MIN_STEM`, `a.length − p ≤ MAX_ENDING`, `b.length − p ≤ MAX_ENDING`, and
   `3p ≥ 2·max(a.length, b.length)` (a fragment such as «lum» / «lumora» is not a form);
   (ii) `b.length ≥ MIN_STEM`, `b` occurs in `a` at offset `1..MAX_PREFIX`, and what follows it in `a` is
   `≤ MAX_ENDING` characters.
2. `matchWhole`: an app matches when its label has as many words as the text and every pair is exact or
   edge; exact iff every pair is exact. Any exact → `Exact(those)`; else any edge → `Edge(those)`; else `None`.
3. `matchWindows`: windows of consecutive words, longest first; at the first length where any app matches
   (as in 2), the result is that match, `whole = (length == word count)`.
4. `AppTargetResolver.candidates(mention)`: alias exact (folded phrase, target installed) → `One`;
   `matchWhole` `Exact` one → `One`, several → `Several`; alias edge or `Edge` → `Confirm(apps)`; else
   `None`. `windows(slot)`: `matchWindows`; a `whole` exact single → `One`, whole exact several → `Several`;
   a non-whole result, or any edge → `Confirm(apps)`. **An edge match, or a window short of the slot, is
   never `One`.**

- [ ] **Step 1: failing tests** — `AppNameMatcherTest`: `an exact label wins over any edge match` · `a russian
  case ending matches by edge` (`миравину` → `Edge([Миравин])`) · `a turkish suffix after an apostrophe matches
  by edge` (`Vemblic'i` → `Edge([Vemblic])`) · `an arabic prefix and vowel marks match` (`بالسنداف` →
  `Edge`; `سِنْدَاف` → `Exact`) · `a stem shorter than three letters never matches by edge` · `a fragment of a
  longer word is not a form` (`lum` vs «Lumora» → `None`) · `edge matches on two apps are both offered, never
  one picked` (`плинту` → `Edge([Плинта, Плинт])`, E4-P1) · `a whole name with extra words is no whole match`
  (`lumora studio` vs «Lumora» → `None`) · `windows go longest first and mark a short window` (`velmo pad` →
  `Exact([Velmo Pad]), whole`; `velmo пожалуйста` → `Exact([Velmo]), not whole`) (9);
  `AppTargetResolverTest`: `an alias in another form is offered for confirmation` (`кварцелота` →
  `Confirm([zantor])`) · `an exact alias still binds without asking` · `an edge label match is a
  confirmation, never a bind` (3); `DeviceMentionBinderTest`: `a word form becomes a one-app pick marked to
  confirm` · `a confirmed word form for launch carries a learning token keyed on the name, for uninstall none`
  (2); `InterpretationSurfaceTest > a word-form pick asks did-you-mean with the one app` (literal title) (1).
- [ ] **Step 2:** scoped → red. **Step 3:** implement; rewrite `DeviceMentionBinderTest:176-180` (alias
  «телега» typed «телегу» → `Confirm`; not-found kept on «вельтрикс», a word no fixture labels); KDoc of
  `AppTargetResolver` and `DeviceMentionBinder`. **Step 4:** scoped green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M4a | `candidates` maps a single `Edge` to `One` | `:data:repository` — `> an edge label match is a confirmation…`, `> a word form becomes a one-app pick…`, `AppSortResolverTest > each candidate answer maps to its sort resolution` *(Measured in execution, E4-F7: the third test is E4-P2's, added in execution; its `Confirm` row is a label edge)* |
| M4b | `matchWhole` keeps the first app of an `Edge` list | `:domain` — `> edge matches on two apps are both offered…` only |
| M4c | clause (ii) removed | `:domain` — `> an arabic prefix and vowel marks match` only |
| M4d | the ratio condition removed | `:domain` — `> a fragment of a longer word is not a form` only |
| M4e | `matchWindows` shortest first | `:domain` — `> windows go longest first…` only |
| M4f | `matchWhole` accepts a label with fewer words than the text | `:domain` — `> a whole name with extra words is no whole match` only |
| M4g | binder builds a learning token for `uninstall_app` too | `:data:repository` — `> a confirmed word form for launch…` only *(Measured in execution, E4-F7: planted on the confirm path only; a path-wide plant also reds 3a's `> uninstall never reads a learned choice`, since `Several` and `Confirm` share one gate)* |

- [ ] **Step 6:** boundary gate → **1715** (`:domain` 538, `:data:repository` 442, `:feature:launcher` 257).
  **Step 7: commit** — `feat(agentic-6/A4' ф3b): один сопоставитель имён — целое имя в мосту, окна для 2b; форма слова — только на подтверждение`.

---

## Task 5: R14-39 closed — step 2b answers an unresolved target on the device

**Spec:** fork 3b-E (§0.1 refinement); §7.12.3; spec §3 0.0; B-6, B-7, B-15. **Modules:** `:domain`,
`:data:repository`, `:feature:launcher`.

**Files:**
- Modify: `ExecutionPlan.kt` (`PlanningResult.Unresolved`, `UnresolvedTarget`), `CompositePlanner.kt`,
  `StartAgentSessionUseCase.kt` (`planning(goal)`), `RouteCommandUseCase.kt` (2b), `CommandOutcome.kt`
  (`ChooseTarget`)
- Modify: `ToolMatchPlanner.kt` — `SecondGoal` check on the slot first (B-15: a second goal → `NoPlan`);
  then `AppTargetResolver.windows`; `Unresolved` instead of `NoPlan` once the vocabulary matched
- Modify: `LauncherCommandSession.kt` (`ChooseTarget` → a target-pick state drawn by `AppChoiceList`; a tap
  binds the package into `pending` and calls `startPlanned` directly; both exhaustive `when`s, `:247`,
  `:406`), `LauncherScreen.kt`, `InterpretationSurface.kt` (`AppChoiceList` reused, made `internal`)
- Rewrite at constant count: `ToolMatchPlannerTest:301-315`; `AgentActingSeamTest` R14-39 test
  (`:data:repository`)
- Test: `ToolMatchPlannerTest`, `RouteCommandUseCaseTest`, `CompositePlannerTest`, `AgentSessionUseCasesTest`,
  `LauncherViewModelTest`

**Interfaces — Produces:**
```kotlin
data class UnresolvedTarget(
    val goal: AgentGoal, val pending: PlanStep, val arg: String, val labelArg: String?,
    val sort: ArgSort, val words: String, val candidates: List<InstalledApp>, val confirm: Boolean,
)   // candidates empty = not found / not understood
sealed interface PlanningResult { /* Planned, NoPlan, */ data class Unresolved(val target: UnresolvedTarget) : PlanningResult }
suspend fun StartAgentSessionUseCase.planning(goal: AgentGoal): PlanningResult    // Planned only if runnable
data class CommandOutcome.ChooseTarget(val target: UnresolvedTarget) : CommandOutcome
```
2b: `Planned` → `startPlanned`; `Unresolved` empty, sort `APP` → `Message(NoAppFound(words))`; non-empty →
`ChooseTarget`; `NoPlan` → falls through. **`Unresolved` never reaches step 7.**

- [ ] **Step 1: failing tests** — `ToolMatchPlannerTest`: `a claimed target that resolves to nothing is
  unresolved, not NoPlan` · `a target inside extra words is offered by windows, to confirm`
  (`удали приложение миравин пожалуйста` → `Unresolved`, candidates `[Миравин]`, `confirm = true`) · `two
  candidates are an unresolved target with both` · `a second goal in the slot goes to the model, not to
  windows` (`удали приложение lumora и проверь почту` → `NoPlan`) (4); `RouteCommandUseCaseTest`: `an
  unresolved 2b target is answered on the device and the model is never asked` (`planCallCount == 0`;
  control: a text no vocabulary claims reaches the model) · `a 2b pick starts the session on the tap,
  without an interpretation card` (2); `CompositePlannerTest > an unresolved answer stops the composite` (1);
  `AgentSessionUseCasesTest > planning returns Planned only for a runnable plan and passes Unresolved
  through` (1); `LauncherViewModelTest`: `a 2b target pick starts the agent session directly` · `a 2b target
  that is not found reads no such app with no wait` (2).
- [ ] **Step 2–4:** red → implement (rewrites listed above) → green. KDoc: `PlanningResult`, 2b.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M5a | 2b treats `Unresolved` as `NoPlan` (falls through) | `:domain` — `> an unresolved 2b target is answered on the device…`; `:data:repository` — `AgentActingSeamTest` (R14-39 test) |
| M5b | `ToolMatchPlanner` returns `NoPlan` where it now returns `Unresolved` | `:data:repository` — `> a claimed target that resolves to nothing…`, `> two candidates are an unresolved target…`, `> a target inside extra words…`, `ToolMatchPlannerTest:301-315` (rewritten) |
| M5c | the `SecondGoal` check removed | `:data:repository` — `> a second goal in the slot goes to the model…` only |
| M5d | `ChooseTarget` handled by setting an interpretation card | `:feature:launcher` — `> a 2b target pick starts the agent session directly` |
| M5e | `CompositePlanner` asks the next planner after `Unresolved` | `:domain` — `> an unresolved answer stops the composite` only |
| M5f *(E5-P1)* | `ToolMatchPlanner` maps the resolver's `Unavailable` to `NoPlan` | `:data:repository` — `> an unreadable app list is an unreadable unresolved target, never NoPlan` only |

*Measured in execution (Task 5, rulings E5-P1, E5-F5): two tests beyond this task's list pin an unreadable
target list (`RouteCommandUseCaseTest > an unreadable 2b target fails on the device and the model is never
asked`, `ToolMatchPlannerTest > an unreadable app list is …`; answered `Failed(Generic)`, as step 7 answers
`Binding.Unavailable`). Three red sets are wider than the table: M5a also reds `:domain` `> a 2b pick starts
the session on the tap…` and `:feature:launcher` both new `LauncherViewModelTest` tests (they drive the real
route); M5b also reds the second-goal test (its control half is an `Unresolved`), the E5-P1 test and the
flipped `AgentActingSeamTest` test; M5e also reds the flipped seam test in `:data:repository` (the production
composite's closing `NoPlan` erases a trailing `Unresolved`).*

- [ ] **Step 6:** boundary gate → **1725**. **Step 7: commit** — `feat(agentic-6/A4' ф3b): R14-39 закрыт — неразрешённая цель на 2b отвечается на устройстве, наружу не уходит`.
  `DOC-ADL-3`'s cell gains `> an unresolved 2b target is answered on the device…` (matrix journal, same commit).

---

## Task 6: sorts resolve before the card — 2b and the bridge

**Spec:** §7.8; fork 3b-C; Review Focus 5; B-5. **Modules:** `:domain`, `:data:repository`, `:feature:launcher`.

**Files:** `CommandMessage.kt` (`ValueNotUnderstood(words)`), `Interpretation.kt` (`Binding.NotUnderstood(words)`),
`RouteCommandUseCase.kt` (2b: `Unresolved` of a non-app sort → `ValueNotUnderstood`; step 7 maps
`NotUnderstood`); `ToolMatchPlanner.kt`, `DeviceMentionBinder.kt` (every sorted argument through
`Map<ArgSort, SortResolver>`); `LauncherPresentation.kt`. Strings: `launcher_message_value_not_understood` —
en «I didn't understand «%1$s».», ru «Не понял «%1$s».», tr ««%1$s» anlaşılamadı.». Tests:
`ToolMatchPlannerTest`, `DeviceMentionBinderTest`, `RouteCommandUseCaseTest`, `LauncherPresentationTest`.

- [ ] **Step 1: failing tests** — `ToolMatchPlannerTest`: `an unreadable duration is not understood before any
  session` (`поставь таймер на полчасика` → `Unresolved`, sort `DURATION`, words `полчасика`, no candidates) ·
  `an upper-case url scheme is normalised at 2b` (2); `DeviceMentionBinderTest`: `a duration the model wrote
  that the device cannot read is not understood, and nothing goes back to the model` · `a url the model wrote
  is normalised or refused before the card` (2); `RouteCommandUseCaseTest > not understood is said in the
  user's words` (1); `LauncherPresentationTest > a value not understood names the words` (1).
  *Measured in execution (Task 6, rulings E6-P1, E6-P2): `ToolVocabulary` has no `open_url` entry — a typed URL
  is FastPath's (`UrlDetector`), never 2b — so the 2b URL test had no reachable address; it is replaced at the
  same count by `ToolMatchPlannerTest > an unreadable clock time is not understood before any session`
  («поставь будильник на 25:99» → `Unresolved`, sort `CLOCK_TIME`). URL normalisation before the card is held
  at the bridge only (the one path a URL arrives unbound). `RouteCommandUseCaseTest > not understood…` drives
  2b (`answerOnDevice`'s former `else -> Failed(Generic)`, E5-F3); step 7's `Binding.NotUnderstood` mapping
  got its own test, `> a value the bridge's binding did not understand is said in the user's words and the
  model is asked once` — `:domain` +2, T6 **1737**. Review round 1 (ruling E6-R1, review I1/I2): an unreadable
  value at 2b is `NoPlan`, not `Unresolved` — the command goes on to steps 5–7 like any unclaimed one; the two
  `ToolMatchPlannerTest` tests became `an unreadable duration at 2b is left to the model` and `a second goal in a
  value slot is left to the model` («поставь будильник на 7:30 и открой lumora», B-15 for value slots) at the
  same count; `answerOnDevice`'s value branch stays as a fail-safe no production planner reaches; the step-7
  test reads `…said in the words written for it…` (E6-R2).*
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M6a | binder passes a sorted argument through unresolved | `:data:repository` — both `DeviceMentionBinderTest` tests of this task |
| M6b | 2b skips non-app sorts | `:data:repository` — both `ToolMatchPlannerTest` tests of this task |
| M6c | `ValueNotUnderstood` rendered without the words | `:feature:launcher` — `> a value not understood names the words` only |
| M6d | 2b's empty non-app `Unresolved` answered `Failed(Generic)` again (*measured in execution, E6-P2*) | `:domain` — `RouteCommandUseCaseTest > not understood is said in the user's words` only |
| M6e | step 7 maps `Binding.NotUnderstood` → `Failed(Generic)` (*measured in execution, E6-P2*) | `:domain` — `RouteCommandUseCaseTest > a value the bridge's binding did not understand…` only |

- [ ] **Step 6:** boundary gate → **1731**. **Step 7: commit** — `feat(agentic-6/A4' ф3b): сорта разрешаются до карточки — на 2b и в мосту`.

---

## Task 7: FastPath on the whole-name matcher; one learned-choice key; FastPath «apps» opens the grid

**Spec:** forks 3b-D/3b-G («all paths»), 3b-F; B-4, B-7. **Modules:** `:domain`, `:data:repository`,
`:feature:launcher`.

**Files:**
- Modify: `IntentActionResolver.kt` (`matchWhole` — never windows; `Edge` → `AmbiguousAppAction(…, confirm =
  true)`), `ExecutableAction` (`AmbiguousAppAction.confirm = false`), `CommandOutcome.kt`
  (`NeedsConfirmation(candidates, query: String, confirm: Boolean = false)` — five construction sites),
  `HandleUserCommandUseCase.kt`, `ResolveCommandWithPreferenceUseCase.kt` (`:33` → `LearnedChoiceKeys.launch(
  outcome.query, folding)`; `reorder` keeps `query`/`confirm`), `IntentProvidesModule.kt` (folding)
- Modify: `CommandFeedback.kt` (`Ambiguous(candidates, confirm: Boolean = false)`), `LauncherPresentation.kt`
  (`:37-110`), `LauncherScreen.kt` (`:737`, the title), `LauncherCommandSession.kt` (`:373`; `applyOutcome(
  ShowApps)` navigates to `Routes.AppDrawer`; the bridge's own navigate in `runInterpretation` removed — one place)
- Test: `IntentActionResolverTest`, `ResolveCommandWithPreferenceUseCaseTest`, `DeviceMentionBinderTest`,
  `LauncherViewModelTest`

- [ ] **Step 1: failing tests** — `IntentActionResolverTest`: `a word form of an installed label asks for
  confirmation` · `an exact label still launches without asking` (2); `ResolveCommandWithPreferenceUseCaseTest`:
  `a choice is keyed on the app name, not on the verb` (`открой tessar` → key `tessar`) · `a confirmed word form
  is learned like a choice` (2); `DeviceMentionBinderTest > a choice learned by FastPath for «открой tessar» is
  the one the bridge reads for «tessar»` — drives the **production** `ResolveCommandWithPreferenceUseCase` and
  `DeviceMentionBinder` over one recording `ResolutionPreferenceStore`, asserting the binder's `find` key equals
  the decorator's `upsert` key (1); `LauncherViewModelTest`: `FastPath show apps opens the app grid` · `a
  FastPath word form shows did-you-mean` (2).
- [ ] **Step 2–4:** red → implement → green. `HandleUserCommandUseCaseTest:394-402` and
  `FastPathOverflowSeamTest:91-121` stay green (whole-name match). Known debt «Turkish morphology» → «closed at
  confirmation level» at close.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M7a | `IntentActionResolver` keeps `lowercase ==` | `:domain` — `> a word form of an installed label asks for confirmation` only |
| M7b | the decorator keys on `slotOf(normalize(rawInput))` again | `:domain` — `> a choice is keyed on the app name…`; `:data:repository` — `> a choice learned by FastPath…` |
| M7c | `IntentActionResolver` uses `matchWindows` | `:domain` — `HandleUserCommandUseCaseTest:394-402`; `:data:repository` — `FastPathOverflowSeamTest:91-111`, `:114-121` |
| M7d | `applyOutcome(ShowApps)` without the navigate | `:feature:launcher` — `> FastPath show apps opens the app grid`, 3a's `runInterpretation` APP_GRID test |

- [ ] **Step 6:** boundary gate → **1738**. **Step 7: commit** — `feat(agentic-6/A4' ф3b): FastPath на сопоставителе целого имени, один ключ выученного выбора, «apps» открывает сетку`.

---

## Task 8: the answer form — `steps[]` and references (advertised: one step)

**Spec:** fork 3b-B; §7.12.2; B-8, B-9, B-18. **Modules:** `:domain`, `:data:ai-cloud`, `:data:repository`,
`:app` (harness; fixtures).

**Files:**
- Modify: `BridgeAnswer.kt` — `RawAnswer(kind, steps: List<RawStep>?, question, destination)`,
  `RawStep.args: Map<String, RawArg?>`, `sealed interface RawArg { Text(value); Ref(step: Int, key: String) }`
- Modify: `ToolDescriptions.kt` (`ToolDescription.outputs: List<ModelOutput> = emptyList()`,
  `ModelOutput(name, description)`; `ModelArg.acceptsRef: Boolean = false`), `BridgeOffering.kt`,
  `BridgeRendering.kt` (`MAX_STEPS_OFFERED = 1` in this task; the offering text lists outputs and carries the
  reference sentence **only when an offered tool describes an output**; schema), `BridgeValidator.kt`,
  `ModelPlanning.kt` (`Proposed(steps: List<ProposedStep>)`, `ProposedStep.refs: Map<String, StepRef>`)
- Modify: `OpenAiCompatibleBridgeTransport.kt` (reads `steps`, reference objects); `BridgeTransportMeasurement.kt`
  (`kind=plan:<n>`; summary `multi_steps_ge2=x/y` over label `multi`)
- `INSTRUCTION`: the form line and all four «… are null» phrases say `steps`; «A plan has exactly one step»
  → «A plan has from one to `MAX_STEPS_OFFERED` steps, in order.»; the reference sentence lives in the
  offering text (B-8). `ToolSchemaWeightTest`: both pins re-measured, never widened.
- Compile-red sites, counts unchanged: `BridgeFixtures`, `BridgeValidatorTest`, `BridgeModelPlannerTest`,
  `BridgeRenderingTest`, `RouteCommandUseCaseTest`, `FakeModelPlanner`, `AgentEgressSentinelGuardTest`,
  `OpenAiCompatibleBridgeTransportTest`, `BridgePayloadGuardTest`; `RouteCommandUseCase` step 7 takes
  `steps.single()` (the validator admits exactly one until Task 9)
- Test: `BridgeValidatorTest`, `BridgeRenderingTest`, `OpenAiCompatibleBridgeTransportTest`, `AuthoredToolDescriptionsTest`

**Schema** (from the same offering as the prompt): `"steps": {"type": ["array","null"], "minItems": 1,
"maxItems": MAX_STEPS_OFFERED, "items": {tool: enum(offered ids), args: {<name>: <string | null>, or for an
argument with acceptsRef: anyOf [string, null, {"from_step": integer, "key": enum(described output names)}]}}}`.

**Validator** (each step as today, then): `steps.size > MAX_STEPS_OFFERED` → `SHAPE`; a `Ref` on an argument
without `acceptsRef` → `SHAPE`; a `Ref` must name a step `< this index` whose tool describes the key → else
`SHAPE`; a mention's words must be derivable (exact, B-17).

- [ ] **Step 1: failing tests** — `BridgeValidatorTest`: `every step of a plan is judged, and one bad step
  rejects the plan` · `a reference to a later or the same step is rejected` · `a reference to an output the
  step's tool does not describe is rejected` · `a reference on an argument that does not accept one is
  rejected` (4); `BridgeRenderingTest > outputs and the reference sentence appear only when an offered tool
  describes an output` (1); `OpenAiCompatibleBridgeTransportTest`: `steps and a reference object are read` ·
  `a reference with a non-integer step is unreadable` (fixture `"from_step": "0"`) (2);
  `AuthoredToolDescriptionsTest > no authored tool describes an output on Android yet` (B-8) (1).
- [ ] **Step 2–4:** red → implement → green; fixtures rewritten; both `ToolSchemaWeightTest` pins re-measured;
  the forbidden-terms scan passes.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M8a | validator judges `steps.first()` only | `:domain` — `> every step of a plan is judged…` only |
| M8b | reference index check `<=` | `:domain` — `> a reference to a later or the same step…` only |
| M8c | output membership unchecked | `:domain` — `> a reference to an output…` only |
| M8d | `acceptsRef` unchecked | `:domain` — `> a reference on an argument that does not accept one…` only |
| M8e | transport reads `from_step` leniently (`content.toIntOrNull()`) | `:data:ai-cloud` — `> a reference with a non-integer step is unreadable` only |

- [ ] **Step 6:** boundary gate → **1746** (`:domain` 552, `:data:ai-cloud` 45, `:data:repository` 452).
  **Step 7: commit** — `feat(agentic-6/A4' ф3b): форма ответа моста — шаги и ссылки на выходы прежних шагов`.

---

## Task 9: binding a multi-step reading; the step cap opens to the engine's

**Spec:** fork 3b-B; §7.12.3 condition 2; B-19. **Modules:** `:domain`, `:data:repository`.

**Files:**
- Modify: `BridgeRendering.kt` (`MAX_STEPS_OFFERED = RuntimeBudget.Default.maxSteps`), `Interpretation.kt`
  (`Act(goal, steps: List<PlanStep>, origins: Map<Int, Map<String, ValueOrigin>>, effects: Map<Int,
  ToolEffect>, choices: List<PendingChoice>)`, `PendingChoice(step: Int, choice: AppChoice)`; `choose`
  answers the **head**; `plan()` non-null only when `choices` is empty; refs → `ArgSource.FromStep`),
  `MentionBinder.bind(goal, steps: List<ProposedStep>)`, `DeviceMentionBinder` (per step; any `NotFound` /
  `NotUnderstood` ends the whole reading), `RouteCommandUseCase.kt` step 7
- Compile-red sites, counts unchanged: `InterpretationTest`, `FakeMentionBinder`, `BridgePayloadGuardTest`,
  `DeviceMentionBinderTest`, `InterpretationSurface` + `InterpretationSurfaceTest`, `LauncherScreenInterpretationTest`,
  `LauncherViewModelTest`, `LauncherCommandSession` (one learning token → a list, B-19);
  `ToolSchemaWeightTest` re-measured for the new cap
- Test: `InterpretationTest`, `RouteCommandUseCaseTest`, `BridgeValidatorTest`, `BridgeRenderingTest`,
  `DeviceMentionBinderTest`

- [ ] **Step 1: failing tests** — `InterpretationTest`: `picks are owed one at a time, in step order` · `the
  plan exists only when no pick is owed` · `a reference becomes a FromStep binding in the plan` (3);
  `RouteCommandUseCaseTest > a two-step model reading is one card and nothing is started` (1);
  `BridgeValidatorTest > more steps than the engine allows is rejected` (1); `BridgeRenderingTest > the schema
  allows one to maxSteps steps` (1); `DeviceMentionBinderTest`: `each step's app mention is bound before the
  card` · `one unknown app in any step means not found for the whole reading` (2).
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M9a | `choose` answers the last pending choice | `:domain` — `> picks are owed one at a time…` only |
| M9b | `plan()` ignores pending choices | `:domain` — `> the plan exists only when no pick is owed` only |
| M9c | refs dropped when building the plan | `:domain` — `> a reference becomes a FromStep binding…` only |
| M9d | `MAX_STEPS_OFFERED` stays `1` | `:domain` — `> the schema allows one to maxSteps steps`, `> a two-step model reading is one card…` |
| M9e | binder binds the first step only | `:data:repository` — both tests of this task |

- [ ] **Step 6:** boundary gate → **1754** (`:domain` 558, `:data:repository` 454). **Step 7: commit** — `feat(agentic-6/A4' ф3b): многошаговое толкование — привязка каждого шага до карточки`.

---

## Task 10: the card for N steps; the consent card shows a resolved reference

**Spec:** fork 3b-B; `DOC-MZN-2`; A0.5's F5. **Modules:** `:feature:launcher`.

**Files:** `InterpretationSurface.kt` (steps in order; a `FromStep` argument reads «← из шага %1$d»,
1-based; the head pending choice drawn; dismiss abandons the reading); `LauncherCommandSession.kt` (picks in
order; Run enabled only when none is owed; B-19 tokens recorded in step order at Run);
`agent/AgentSessionPresentation.kt` (`PlanStep.line` for a step awaiting consent resolves `FromStep` args with
`InvocationValidator.resolve(invocation, session.observations)` — pure, display only). Strings:
`launcher_interpretation_from_step` — en «← from step %1$d», ru «← из шага %1$d», tr «← %1$d. adımdan». Tests:
`InterpretationSurfaceTest`, `AgentSessionPresentationTest`, `LauncherViewModelTest`, `LauncherScreenInterpretationTest`.

- [ ] **Step 1: failing tests** — `InterpretationSurfaceTest`: `a two-step reading lists both steps in order` ·
  `an argument taken from an earlier step says so` (2); `AgentSessionPresentationTest > a step awaiting consent
  shows the value it will receive from an earlier step` (step 0 `Effected(ToolOutput(resolved_query =
  "Lumora"))`, step 1 bound to it) (1); `LauncherViewModelTest > two picks are owed one after the other and Run
  waits for both` (1); `LauncherScreenInterpretationTest > a two-step card runs both steps through the engine
  after Run` (1).
- [ ] **Step 2–4:** red → implement → green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M10a | card draws `steps.first()` only | `:feature:launcher` — `> a two-step reading lists both steps…`, `> a two-step card runs both steps…` |
| M10b | consent line reads literals only | `:feature:launcher` — `> a step awaiting consent shows the value…` only |
| M10c | Run enabled with a pick still owed | `:feature:launcher` — `> two picks are owed…` only |

- [ ] **Step 6:** boundary gate → **1759**. **Step 7: commit** — `feat(agentic-6/A4' ф3b): карточка из N шагов; карточка согласия показывает значение из прошлого шага`.

---

## Task 11: re-planning without egress — the remainder of a model plan; a failed resume says so

**Spec:** fork 3b-A; §6.4 (staleness layer 1); 3b-F; B-10. **Modules:** `:domain`, `:data:repository`,
`:feature:launcher`, `:app`.

**Files:**
- Create: `domain/…/agent/RemainderPlanner.kt` — `class RemainderPlanner : Planner` reading
  `PlanningRequest.previous` and `priorObservations`
- Modify: `ExecutionPlan.kt` (`PlanningRequest.previous: ExecutionPlan? = null`; KDoc «empty at every call
  site» → its first consumer), `ResumeAgentSessionUseCase.kt` (a model plan, acted, not undo, no call in
  flight → `Resumption.NeedsCard(goal, remainder)`; deterministic acted plans unchanged), `Interpretation.kt`
  (`MentionBinder.rebind(goal, plan: ExecutionPlan): Binding`), `DeviceMentionBinder.kt` (re-resolves each
  app argument from its `labelArg` words, else checks the package is still installed), `FakeMentionBinder`
- Modify: `agent/LauncherAgentSession.kt` (`NeedsCard` → `onRemainder(goal, plan)`, the shape of
  `onReroute`; «Повторить остаток» on a finished `Failed`/`Blocked` model-plan card builds the same remainder
  from the in-memory session; the `null` branch shows «Не удалось продолжить план.»),
  `LauncherCommandSession.kt` (`showRemainder` → `rebind` → the interpretation card)
- Strings: `launcher_agent_retry_remainder` — en «Retry the rest», ru «Повторить остаток», tr «Kalanı yeniden
  dene»; `launcher_agent_resume_failed` — en «The plan could not be continued.», ru «Не удалось продолжить
  план.», tr «Plan sürdürülemedi.»
- Test: `domain/src/jvmTest/…/agent/RemainderPlannerTest.kt`, `ResumeAgentSessionUseCaseTest`,
  `DeviceMentionBinderTest`, `LauncherAgentSessionTest`, `BridgePayloadGuardTest`

**B-10 precisely:** remainder = steps after the last index whose observation is `Effected`/`HandedOff`; each
`FromStep(k, key)` with `k` finished → `Literal(InvocationValidator.resolve` of that arg over
`priorObservations)`; one that resolves to nothing → `NoPlan` (cannot continue honestly → the stale session
ends `Failed`, M1(b)); re-indexed from 0, rationale `MODEL_PROPOSED` kept.

- [ ] **Step 1: failing tests** — `RemainderPlannerTest`: `the remainder starts after the last step that acted`
  · `a reference into a finished step becomes its value` · `a reference into a finished step with no value
  leaves no remainder` · `an undo plan has no remainder` (4); `ResumeAgentSessionUseCaseTest`: `an acted model
  plan comes back as a card for its remainder and nothing is re-run` · `a call in flight is still resumed once,
  not re-planned` (2); `DeviceMentionBinderTest`: `a remainder re-binds its app from the user's words` · `a
  remainder whose app is gone is not found` (2); `LauncherAgentSessionTest`: `retry the rest on a failed card
  shows the remainder card` · `a resume the store fails says so instead of vanishing` (2);
  `BridgePayloadGuardTest > a remainder asks the model nothing` (real graph; the recording transport sees zero
  asks across resume and retry; control: the same goal routed fresh asks once) (1).
  `ResumeAgentSessionUseCaseTest:128-153` stays green (deterministic acted plan).
- [ ] **Step 2–4:** red → implement → green. KDoc: `ResumeAgentSessionUseCase`, `AgentSession.resumed` (layer 1).
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M11a | remainder starts at the cursor | `:domain` — `> the remainder starts after the last step that acted` only |
| M11b | a ref into a finished step kept as `FromStep` | `:domain` — `> a reference into a finished step becomes its value` only |
| M11c | an acted model plan continues from the cursor (3a) | `:domain` — `> an acted model plan comes back as a card…` |
| M11d | `rebind` keeps a package no longer installed | `:data:repository` — `> a remainder whose app is gone is not found` only |
| M11e | the remainder path routes the goal (`NeedsRoute`) | `:app` — `> a remainder asks the model nothing`; `:domain` — `> an acted model plan comes back as a card…` |
| M11f | the `null` branch back to `show(null)` | `:feature:launcher` — `> a resume the store fails says so…` only |

- [ ] **Step 6:** boundary gate → **1770** (`:domain` 564, `:data:repository` 456, `:feature:launcher` 269,
  `:app` 78). **Step 7: commit** — `feat(agentic-6/A4' ф3b): перепланирование без выхода наружу — остаток совершившего плана модели`.
  `DOC-AML-1`'s cell gains the payload test (matrix journal, same commit).

---

## Task 12: phase 2's deferred tests on the first multi-step plans — N2, T3 m3, T4 M2, T6 m5

**Spec:** `§HANDOFF` «Фаза 2 A4′» (track plan `:2104-2124`); B-11. **Modules:** `:domain`,
`:feature:launcher`, `:consumer:jvm`.

**Files:** `UndoAgentSessionUseCase.kt` (`review`: `Cancelled` with an acted step → `Compensation.review`;
without → empty; KDoc), `agent/AgentSessionSurface.kt` / `LauncherAgentSession.kt` (the finished card for a
`Cancelled` session with an acted step). Rewrite at constant count: `UndoAgentSessionUseCaseTest:140-148`
(`Cancelled` leaves the loop; renamed `a paused or unfinished session, or one cancelled before anything acted,
has nothing to undo`).

- [ ] **Step 1: tests** — `UndoAgentSessionUseCaseTest`: `undo is still offered for a finished plan whose last
  step was handed off, when the clock did not stop it` (T3 m3: `Completed` ending `HandedOff` with an undoable
  earlier step → that step's undo offered) · `a plan cancelled after a step acted offers undo for what acted`
  (N2) (2); `ResumeAgentSessionUseCaseTest > a paused session holding a handed-off step is not planned afresh`
  (T4 M2) (1); `AgentSessionSurfaceUndoTest`: `a plan cancelled after a step acted shows what acted and offers
  undo` (N2) · `a blocked plan's undo is wired` · `a failed plan with nothing undoable says what cannot be
  undone` (T6 m5) (3); `ConsoleHarnessTest > a plan cancelled after a step acted offers undo` (N2 on the JVM —
  the harness follows the domain) (1).
- [ ] **Step 2:** implement N2 in `:domain` and on the Android card; the T3 m3 / T4 M2 / T6 m5 tests are green
  on arrival — **proved by mutation**, below.
- [ ] **Step 3: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M12a | `review`'s `state == Blocked &&` conjunct removed (`UndoAgentSessionUseCase.kt:34`) | `:domain` — `> undo is still offered for a finished plan whose last step was handed off…` only |
| M12b | `HandedOff` removed from `replansOnResume` | `:domain` — `> a paused session holding a handed-off step…` only |
| M12c | `Blocked` undo wiring removed on the card | `:feature:launcher` — `> a blocked plan's undo is wired` only |
| M12d | `next = notUndoable` → `undoable` at `Failed` | `:feature:launcher` — `> a failed plan with nothing undoable…` only |
| M12e | `review` returns empty for every `Cancelled` (pre-N2) | `:domain` — `> a plan cancelled after a step acted offers undo…`; `:feature:launcher` — `> a plan cancelled after a step acted shows…`; `:consumer:jvm` — `ConsoleHarnessTest > a plan cancelled after a step acted…` |

- [ ] **Step 4:** boundary gate → **1777** (`:domain` 567, `:feature:launcher` 272, `:consumer:jvm` 75).
  **Step 5: commit** — `test(agentic-6/A4' ф3b): отложенные тесты фазы 2 на многошаговых планах; N2 — частичный исход`.
  `DOC-HMA-3`'s remainder marker cleared (matrix journal, same commit).

---

## Task 13: `:consumer:jvm` parity — the same bridge, its own transport, sort and binder

**Spec:** §7.12.4; B-12, B-13, B-18; Review Focus 2. **Modules:** `:domain`, `:consumer:jvm`, `:app` (DI).

**Files:**
- Modify: `BridgeOffering.kt` — `offeringOf(registry, levels: Set<ToolLevel>, descriptions)`,
  `ANDROID_OFFERED_LEVELS`; `BridgeModelPlanner(…, levels)` (no default); `BridgeProvidesModule`; compile-follow
  `OfferedToolIdsFreezeGuardTest`, `BridgeOfferingGuardTest`, `BridgeFixtures`, `ToolSchemaWeightTest:59`,
  `BridgeTransportMeasurement`
- Create (`consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/bridge/`):
  - `JdkBridgeTransport.kt` — `PROMPT_ONLY`; `fun interface BridgeHttpSender { suspend fun send(url: String,
    body: String, key: String): Pair<Int, String> }`; production `JdkHttpSender` (`java.net.http`) refuses a
    non-`https://` URL before sending; no key → no send; never throws; rethrows cancellation; the wire parse
    mirrors `:data:ai-cloud`'s
  - `SandboxToolDescriptions.kt` — **what the model sees, per tool:** `workspace_info()` — no arguments, not
    offered as useful (described, outputs none to the model); `find_file(name)` — `name` = «the file name, in
    the user's own words», a mention; **`root` is bound by the device** (the sandbox root), never asked of the
    model; output `resolved_path` described; `delete_file(path)` — `path` is a mention (a file name in the
    user's words, resolved by `SandboxFileSort`) that `acceptsRef` (`{from_step, key: resolved_path}`);
    `rename_file(path, new_name)` — `path` as `delete_file`, `new_name` text
  - `SandboxFileSort.kt` — `SortResolver` for `FILE`: words → sandbox files whose folded name
    `matchWhole`es; one → `Resolved(path)`, several → `Choice`, none → `NotFound`
  - `SandboxMentionBinder.kt` — binds `root`, maps `name` → `find_file.query`, resolves word `path`s through
    `SandboxFileSort`, keeps references as `FromStep`; `rebind` (Task 11's port)
- Modify: `ConsoleHarness.kt` — `ask <goal>`: model → validator → binder → the card printed → `y` →
  `startPlanned` → run (consent prompts unchanged)
- Test: `JdkBridgeTransportTest.kt` (a fake sender), `SandboxFileSortTest.kt`, `SandboxMentionBinderTest.kt`,
  `ConsoleHarnessTest`, `BridgeOfferingTest`

- [ ] **Step 1: failing tests** — `JdkBridgeTransportTest`: `a prompt-only answer with steps and a reference is
  read` (Task 8's golden JSON, field for field) · `no key means nothing is sent` · `a non-https base url is
  refused before sending` · `a 200 carrying an error object is unreadable` (4); `SandboxFileSortTest`: `a file
  name resolves to its path` · `two files sharing a name are a choice` (2); `SandboxMentionBinderTest > the
  sandbox root is bound by the device and never asked of the model` (1); `ConsoleHarnessTest`: `a free goal goes
  through the model, the card, then the engine` · `a delete bound to a find that found nothing deletes nothing`
  (fake transport: `find_file(name = "orvelle.txt")` → `delete_file(path ← step 0 resolved_path)`; the plan
  **validates and starts** (asserted); no `orvelle.txt` in the sandbox; the file tree byte-identical after; the
  outcome names the unresolved binding) (2); `BridgeOfferingTest > the offering is the levels its consumer
  passes` (1, `:domain`).
- [ ] **Step 2–4:** red → implement → green; `ModuleIsolationTest` green.
- [ ] **Step 5: mutations**

| # | Plant | Predicted `FAILED ::` |
|---|---|---|
| M13a | `offeringOf` ignores `levels` (Android set) | `:domain` — `> the offering is the levels its consumer passes`; `:consumer:jvm` — `> a free goal goes through the model…` |
| M13b | sender called with a blank key | `:consumer:jvm` — `> no key means nothing is sent` only |
| M13c | `root` asked of the model (described as an argument) | `:consumer:jvm` — `> the sandbox root is bound by the device…` only |
| M13d | the harness substitutes an empty path for an unresolved reference | `:consumer:jvm` — `> a delete bound to a find that found nothing…` (if the engine's `UNRESOLVED_ARG_SOURCE` stops it first, the finding is «the test proves the engine»; re-plant on the harness's own branch) |

- [ ] **Step 6:** boundary gate → **1787** (`:consumer:jvm` 84, `:domain` 568). **Step 7: commit** — `feat(agentic-6/A4' ф3b): паритет :consumer:jvm — тот же мост, свой транспорт, сорт «файл» и привязка`.

---

## Task 14: the in-phase measurement — the owner's run

**Spec:** fork 3b-H; §0.3 R1–R5; B-16. **Modules:** `:domain` (`BridgeBudget`), the corpus (git-ignored),
`ai-context/decisions.md`.

This task cannot start before the owner's run. If Tasks 1–13 are done and the owner has not run it, the
controller **stops and asks** — the plan does not choose for the owner.

- [ ] **Step 1:** eight `multi` lines in the git-ignored corpus (agent's draft; the owner may edit); dry run
  `SIDR_MEASURE_MODES=PROMPT_ONLY SIDR_MEASURE_REASONING=off,on … --dry-run` → `corpus=40 modes=[PROMPT_ONLY]
  reasoning=[off, on]`.
- [ ] **Step 2:** the owner's run (or the agent's, by the owner's word; key from `~/.config/sidr/measure.key`,
  never printed; base URL OpenRouter); raw → `.superpowers/sdd/2026-11-07-a4-phase3b/measure-in-phase/`.
- [ ] **Step 3:** apply R1–R5 **as written in §0.3**; `BridgeBudget.Default` ← R3, R4; the pin test edited to
  the new value; KDoc; ADR 2 addendum «Замер фазы 3b».
- [ ] **Step 4:** boundary gate → **1787**. **Step 5: commit** — `feat(agentic-6/A4' ф3b): предел рассуждений и потолок — по замеру фазы`.

---

## Task 15: close the phase — documents, the floor. The commit is proposed, not made

- [ ] **Step 1:** final whole-branch review (`reviewer-deep`, owner's standing rule), its fix round and
  mutations, a boundary gate from a cleared tree.
- [ ] **Step 2:** floor `BASELINE_TESTS` ← the measured total; three boundary runs (N green, N+1 `BELOW
  BASELINE`, N green).
- [ ] **Step 3:** `git diff --stat 4138c1f --` over the frozen list — empty, or each line read and explained.
- [ ] **Step 4:** `§HANDOFF` «Фаза 3b A4′»; `CLAUDE.md` (table row; Known debt: R14-39 closed, Turkish
  morphology closed at confirmation level; the contract table: `ToolArgumentSorts` → `CatalogArgumentSorts`,
  the sort port, `TextFolding`); the matrix journal; `current-status.md` header **and its tail**.
- [ ] **Step 5:** for the owner: §0.1's two refinements, §0.2's decisions (B-11, B-15, B-17 first), the five
  `tr` strings, the two ADRs, the matrix edits, i18n-3. **Propose** the close commit (R7).

---

## Self-review against the spec

- **§7.12.4 «3b»:** sorts + `F6` ADR — Tasks 3, 6; word forms — Tasks 2, 4, 7; re-planning + the outbound fork
  — Task 11; multi-step on Android — Tasks 8–10; JVM parity — Task 13; N2, T3 m3, T4 M2, T6 m5 — Task 12. Owner
  additions: R14-39 — Task 5; reasoning cap — Tasks 1, 14; learned key, «apps» — Task 7; `continueSession` —
  Task 11.
- **Criteria:** 1 — the F1-2 pins stay green (Task 7, M7c proves the whole-name boundary); 10 — the frozen list,
  checked at close; 11 — the chain; 12 — `DOC-ADL-3` (T5), `DOC-AML-1` (T11), `DOC-HMA-3` (T12); 14–21 of 3a stay
  green (their tests edited only for `steps`, never weakened); 19 — the pin now exists (T1, edited in T14).
- **Type consistency:** `SortResolution.Choice(…, confirm)` (T3) ↔ `AppCandidates.Confirm` (T4) ↔
  `AppChoice.confirm` (T4) ↔ `UnresolvedTarget.confirm` (T5) ↔ `NeedsConfirmation.confirm` /
  `CommandFeedback.Ambiguous.confirm` (T7); `ModelArg.acceptsRef` (T8) ↔ the sandbox `path` (T13);
  `ProposedStep.refs` (T8) → `ArgSource.FromStep` (T9); `MentionBinder.bind(goal, steps)` (T9),
  `rebind(goal, plan)` (T11, implemented on the JVM in T13); `offeringOf(registry, levels, descriptions)` (T13);
  `BridgeModelPlanner(…, folding, levels)` (T2, T13).
- **Review Focus:** five lines, five owning tests (T4, T13, T10, T11, T6).
- **Proportion:** interfaces, test names, assertions, algorithms where undetermined; no function bodies.
