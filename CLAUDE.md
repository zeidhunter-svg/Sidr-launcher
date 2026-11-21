# CLAUDE.md — Sidr Launcher

> **Read-first for every session.** This file is **current state + rules + pointers only**.
> History does not live here — it lives in `ai-context/decisions.md` (ADR log), one ADR per closed
> block. **Compressed 2026-11-11 (98 KB → ~42 KB; the five verbatim rule sections alone are ~24 KB, the status sections ~18 KB) by the context-hygiene session, owner decision
> «1: да»; the pre-compression text is preserved verbatim in git at `b42b53b:CLAUDE.md` and, section by
> section, in the appendix of ADR «2026-11-11 — Сжатие CLAUDE.md (гигиена контекста)»** — every
> paragraph that left this file is there, with its best living address beside it. The earlier
> compression (Этап 0.4, 2026-08-19, 101 → ~15 KB) is at `9de23ab:CLAUDE.md`. A guard test
> (`ClaudeMdSizeGuardTest`, `:app`) fails the build when this file grows past its ceiling again: write
> a line of status and a pointer here, the narrative in the ADR or `§HANDOFF`.

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

Vision (owner reframe 2026-07-05, amended by ADR 3/4): **Stage 1 — AI Launcher** (closed) →
**portable agent core**, two consumers, Android + PC (Stage 2 as a *schedule* stage is abolished;
layers grow inside vertical slices) → **Agentic OS** (consented automation + the AI shell).

## Current goal (active work)

**The AGENTIC TRACK is the active track.** Governing document:
[docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md)
(owner-approved 2026-08-18): §0 holds the session kickoff template and the **binding** closing checklist;
`§HANDOFF` at its end is written by the closing session for the next one — read it. Since 2026-11-11
`§HANDOFF` is the **live** part only; closed phase records (A4′ Фазы 0–4, the A1″ handoff) are in
[the handoff archive](docs/superpowers/plans/2026-08-18-agentic-track-restart-handoff-archive.md) under
their original headings, so «`§HANDOFF` «Фаза 2 A4′»» resolves there.

**Right now: A4′ phase 4 — the learned-plan cache — is `CODE-GREEN`** (2026-11-20, gate **1910**; **1912** after T8): a command
confirmed three times replays on the device at step 2c, no model, consent every time. D3 is amended by ADR
«2026-11-20 — D3, поправка: выученная команда» — **signed by the owner 2026-11-20**; I1 «a»: the locked
`settings_local_only_description` is reworded (both halves) and **re-signed by the owner 2026-11-20** (`072889ca`); the
learned-commands «Забыть» goes through `SidrForgetGate` (T8, `bb4656fa`, consequence line signed in the same commit).
**Acceptance run 2026-11-21 (`ru`, owner, SM-A325F):** Part A/B done, `MigrationTest` 11/0 on the phone, two
defects fixed in the round (timer/alarm `EXTRA_SKIP_UI = true`; learned-commands screen crash), gate **1914**;
twelve owner rulings → tasks — [checklist](docs/superpowers/plans/2026-11-21-a4-device-acceptance.md).
§A7 + Part D (Turkish) done 2026-11-21 («Yapay zekâ» everywhere, two locked files re-signed). **Next: the block ADR A4′.** Record: `§HANDOFF` «Фаза 4 A4′»
(archive); plan [2026-11-18-a4-phase4-learned-plan-cache.md](docs/superpowers/plans/2026-11-18-a4-phase4-learned-plan-cache.md).

Layers above the track plan: the **Master Plan** (blocks A0…A6, DoD, change-control, `Agentic Shell v1 =
DONE`: [docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md))
governs how blocks run from Этап 4 on; the **doctrine matrix** (31 `DOC-*` rules:
[docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md)) — cite a rule
by ID, never paraphrase; its third column is a real test name or an honest `<нет>`, and
`DoctrineMatrixGuardTest` fails the build on a false class name (it reads `` `XTest` ``, `` `XTest#fn` `` and,
since 2026-11-17, `` `XTest > name` `` — rule rows only). Owner decisions in force: **two consumers from
the start** (2026-08-21, ADR «Развилка агентного трека»: `:consumer:jvm` is real, block A0.5, fork F6) and
**two principles** (2026-11-07, ADR «Два принципа агентной ОС…»: the command set is unbounded and grows by
registering sources, the gate set is finite and the same for every command; whatever can run on the device
runs there, the cloud is an accelerator). Beside them a **draft for discussion, not a decision**:
[docs/governing/sidr-ontology-v0.1.md](docs/governing/sidr-ontology-v0.1.md).

**Tool count is reported split, always:** A1″ built sixteen authored tools, five of them acting; the
federation held **twenty** at its close (four predate the block); since A4′ phase 3a **twenty-two authored
tools, nineteen offered to the model** (`web_search`/`open_url` became tools; `launcher_memory`'s three
are not offered).

| Stage | State · date · gate | Full record |
|---|---|---|
| **1** strategic ADR package | ✅ 2026-08-19 | ADR 1/4 … 4/4 |
| **0.1–0.7** sign-off, FastPath `ru`/`tr`, ONNX deleted (APK 78 → 6.8 MB), this file compressed, honest statuses, measured budgets, I18N residue | ✅ 2026-08-19 | ADRs «Этап 0.2 … 0.7» |
| **2** toolchain (AGP 9.3.1 / Kotlin 2.4.10 / Gradle 9.5.0 / compileSdk 37) + `:domain` → KMP | ✅ 2026-08-19 | ADR «Этап 2» |
| **3** Master Plan + doctrine matrix | ✅ 2026-08-19 | ADR «Этап 3» |
| **4.0** `llmRouterEnabled` → `localOnlyMode` | ✅ `DEVICE-ACCEPTED` 2026-08-22 | ADR «Этап 4.0» |
| **4** A0 thin agentic spike | ✅ `CLOSED` 2026-08-22; review round 2026-08-23 `CODE-GREEN` (gate 1158) | ADRs «Этап 4 (A0)», «Сквозное ревью блока A0» |
| **4.5** A0.5 second consumer | ✅ `CODE-GREEN` 2026-08-26; device acceptance **not applicable** | ADR «Этап 4.5 (A0.5)» |
| **5** A1′ federated `ToolRegistry` | ✅ `CLOSED` 2026-09-10 (`ru-RU` only) | ADR «Этап 5 (A1′)» |
| **5.5 / 5.5b** A1″ tool mass + selection, phases 0–3b | ✅ `CLOSED` 2026-09-20, gate **1439**, `ru`/`tr`/`en`; signed build `db1e75d` **unfixed** | ADR «Этап 5.5 (A1″)» |
| **6** A4′ runtime, spec [2026-09-21-a4-runtime-design.md](docs/superpowers/specs/2026-09-21-a4-runtime-design.md) — **one acceptance at the block's end, no device round per phase**; block ADR at close | phase 0 `CODE-GREEN` 2026-09-26, **1463** (`HandedOff`, cooperative wall-clock budget, `ToolArgumentSorts` closes R14-37 in code) · phase 1 2026-09-26, **1485** (`SecondGoal` FastPath overflow, `DOC-NYH-4`) · phase 2 2026-10-03, **1560** (R14 closed in code, consent reasons as a **set**, re-plan on «Продолжить», undo) · phase 3a 2026-11-07, **1673** (the bridge, **one model path**, router deleted, nineteen `ToolId`s frozen; budget by measurement: `PROMPT_ONLY`, 50 s, 4 096 tokens at 3a) · phase 3b 2026-11-17, **1821** (sorts an axis, `F6` closed; `TextFolding` + word forms, offered never bound; R14-39 closed; multi-step model plans with references; the remainder without the model; N2 «partial»; `:consumer:jvm` bridge parity; budget re-measured, per host since the owner's answers, **1841**) · phase 4 2026-11-20, **1910** (learned-plan cache: a form with slots + the model's validated answer, pinned at 3, replayed at step 2c without the model or a card, consent never skipped; schema 5; memory out of Auto Backup; D3 amended; T8 forget gate after the answers, **1912**) | `§HANDOFF` «Фаза N A4′» (archive, 0–4); plans `2026-09-22-a4-phase0-…`, `2026-09-26-a4-phase1-…`, `2026-09-27-a4-phase2-…`, `2026-10-03-a4-phase3a-…`, `2026-11-07-a4-phase3b-…`, `2026-11-18-a4-phase4-…` in `docs/superpowers/plans/`; ADRs «2026-10-05 — A4′ ф3a …» (two; addendum «Замер фазы 3b»), «2026-11-07 — A4′ ф3b: сорт — отдельная ось», «2026-11-20 — A4′ ф4: кэш выученных планов…», «2026-11-20 — D3, поправка…» |
| **6–7** A4′ acceptance + block ADR, then A2/A3/A5/A6 | each its own plan; A2/A3/A5/A6 their own spec (`brainstorm → spec → plan → build`) | |

The four strategic ADRs (2026-08-19): **1/4** understanding belongs to the model, execution to the
deterministic layer — `llmRouterEnabled` inverted onto `localOnlyMode` / `flag_local_only`, default
`false`, old key orphaned, no migration; **2/4** platform re-baseline — ONNX closed, local runtime is
LiteRT / LiteRT-LM (alternative ExecuTorch; AICore separate), `AppFunctions` / `MCP` first-class sources;
**3/4** portable core — `:domain` is KMP, two consumers, **`ActionIds`' seven values frozen byte-for-byte**
(`launch_app` is a Room PK), and since phase 3a the outbound contract is the **nineteen offered `ToolId`s**,
values frozen, count not (`OfferedToolIdsFreezeGuardTest`); **4/4** Assistant ⊕ Agent — one loop, two
surfaces, a 0-step plan *is* a spoken reply. **The A1 fork is decided** (owner fork F1): parallel
vocabulary + federation, identity C — `ToolId`/`ToolDescriptor` are the agent's vocabulary,
`ActionCatalog` one adapter among N, a projected `ToolId` derived from its `ActionId`.

## Shipped surface (2026-11-20)

Everything below is on `launcher--7` and `CODE-GREEN`; most of it `DEVICE-ACCEPTED` on the SM-A325F
(Android 13, the owner's own run) **except** DS-5, I18N-1, FastPath's `tr` forms and everything A4′ changed
(phases 0–4) — `CODE-GREEN` only, see Known debt. Status vocabulary (Этап 0.5): `CODE-GREEN` (gate green,
no device claim) / `DEVICE-ACCEPTED` (owner ran on-device verification and signed off — an agent-driven
`adb`/`uiautomator` pass does not count) / `CLOSED` (both, plus any residual limitation named, not implied
absent).

- **Launcher core** — home, drawer, settings, launch; fully offline. **FastPath** —
  `RuleBasedIntentMatcher`, `en`/`ru`/`tr` vocabulary (`FastPathLocaleGuardTest`); trigger matching is
  **locale-blind**, only rendered strings need a locale switch.
- **BYOK cloud AI** — Assistant (SSE, OpenAI-compatible, key in Keystore) + **the bridge**, the one model
  path: a FastPath miss past the local-only / provider / connectivity gates (each blocked state says which)
  sends the command and our tool descriptions; an interpretation card comes back; nothing runs before
  «Выполнить» (but a learned command, D3 as amended), then only through the engine.
- **Memory** (learned resolutions at streak ≥ 3, aliases; on-device, correctable) · **Suggestions**
  (time/usage offline, calendar/location permission-gated, WorkManager) · **Prayer** (offline `adhan2`,
  bundled city index, location rounded to 2dp before the port, never persisted) · **Design system v1.1**
  (`core/ui`, Roborazzi) · **i18n** (`en`/`ru`/`tr` via `sidrString`, four guard tests, owner-sign-off
  release gate).
- **Agent engine (A0)** — `domain/tool` + `domain/agent` + `domain/trace`; `ToolExecutor` the only path to
  the world (**one** call site below the consent checkpoint, `ToolExecutorCallSiteGuardTest` +
  `AgentExecutorTest`); deterministic `TemplatePlanner` over `GoalShape`, runs in every network state (a
  FastPath «no such app» → launch → offer the store); session in Room (schema 5), cascade-deleted on any
  terminal state (`AgentAtRestGuardTest`).
- **Second consumer (A0.5)** — `:consumer:jvm`, zero Android artifacts, same engine contracts, sandboxed
  file tools, `FilePlanner`, JSON session store, console harness walking both process-death shapes;
  `commonMain` footprint `GoalShape.Free` + its `NoPlan` arm.
- **Federated registry (A1′)** — `ToolFederation`'s `registry` and `executor` read one adapter list by
  construction; every tool declares `level`/`effect`, `EXTERNAL` provenance reaches the user
  (`DOC-ILM-2`); `requiresConsent(risk)` the one gate predicate (`DOC-ADL-1`); routing step **2b** tries a
  miss against the tool vocabulary in every local state; `ToolWorkerCallSiteGuardTest` holds the second hop.
- **Tool mass + selection (A1″)** — five acting tools (`set_alarm`; **`uninstall_app`**, first
  `CONFIRM`/`DURABLE`; three `launcher_memory` tools), eleven navigating screens (each `Intent` inline in
  its own `when` arm; `SAFE` because they perform no act); `ShortcutToolSource` (every app shortcut a tool
  whose name is data; gated by the `HOME` **role**); `ToolSelector` (authored beats dynamic, equal
  candidates **decline**, every token accounted for); `AppTargetResolver` resolves **above** the consent
  gate (fork F5) so the `CONFIRM` card names the package; permissions keyed on the registry; worker throws
  contained at the single call site; two-slot form `<A> <infix> <B> <suffix>`.
- **Learned-plan cache (A4′ phase 4)** — a model reading confirmed three times (one form + template) is kept as
  a form with slots + the validated answer (`learned_plans`, schema 5); step **2c** replays it on the device
  through the same validator, binder and engine, in every network state; a card only for an owed pick or model
  text going out; the session card says «Выученная команда» + «Забыть»; Settings → «Выученные команды»; databases
  and DataStore out of Auto Backup / transfer (`BackupRulesGuardTest`); `:consumer:jvm` parity over a JSON file.
- **A4′ so far** — see the stage table. **PREVIEW surfaces** (Tasks / Agents / Activity / Terminal) —
  non-functional badged mock-ups, zero fabricated data (owner decision 2026-08-18: they stay).

## Known debt (honest list)

Not `CLOSED`. One line per debt: what · status · address · full text. Item numbers are kept exactly as
ADRs and KDoc cite them. `CLOSED` is never a zero-debt claim (DS-6B precedent).

- **A0 (ADR «Этап 4 (A0)»; `DEVICE-ACCEPTED` 2026-08-22):** **(1)** §12.8 unreachable by any user path
  — fixed in code by A4′ phase 2 (F2-3.1) for a plan that has only looked, not seen on a phone; an acted
  **model** plan comes back as its remainder card since 3b Task 11 (Android; the JVM restore still continues
  on pre-pause observations — B-10 parity gap), an acted **deterministic** plan continues from the cursor
  (B-10, named); **(2)** the plan list is not
  drawn in `AwaitingConsent`; **(3)** §12 item 8 needed agent intervention. **Fix round 2026-08-23** is
  `CODE-GREEN` only: §12.5 and §12.8 need re-running —
  [checklist](docs/superpowers/plans/2026-08-23-a0-device-recheck.md).
- **A0.5 (ADR «Этап 4.5 (A0.5)»):** **(1)** four vocabulary findings → A1′ (`ObservedFact` §6.3 — owner
  decision, see Do not; `CommandFailure`; `StepRationale`; `ArgType`); **(2)** `DURABLE_EFFECT` inert —
  closed by A4′ phase 2; **(3)** JVM sandbox limits (symlink name leak, TOCTOU, `rename_file`); **(4)**
  `JvmAgentSessionStore` (`FileLock` proved by nothing; no `SessionDto` version → A5; orphaned `.lock`);
  **(5)** the two consumers disagree what `pausedForRestore()` records → A4′.
- **A1′ (ADR «Этап 5 (A1′)» § «Приёмка на устройстве»):** read agent tables **only with
  `tools/device/pull-agent-db.sh`** (a bare read misses the WAL and lies both ways;
  `DeviceDatabaseReadGuardTest` scans `docs/superpowers/plans`). Residuals: **(1)** `DOC-ADL-1` closure
  rests on the single call site, not `ConsentPolicyTest`; **(2)** `core/testing/src/main/java` scanned by
  neither call-site guard (owner-level); **(3)** step 2b fails open to the model on any non-`Success` from
  the store — a disk error decides the send (→ A4′; its third fail-silent instance,
  `LauncherAgentSession.continueSession`'s `null` branch, closed by 3b Task 11); **(4)** closed by A1″ phase 0
  (`DoctrineGuardTest.declaredRisk`); **(5)** `PlanStep.line` keyed on argument **count**; **(6)**
  `sayaç ayarla` judged and accepted 2026-09-20; **(7)** of A0.5's four: `ObservedFact` untouched,
  `CommandFailure` rejected with a measured reason, `StepRationale.label` deleted, `ArgType` one value
  (fork F6); F5 closed for deterministically resolvable arguments only; **(8)** worker throws — closed by
  A1″ phase 0 (contained at `AgentExecutor.perform`); **(9)** `ToolPermissionManifestGuardTest`'s two blind
  spots — closed for permissions by `ToolRegistryPermissionGuardTest`, worker holder list still file-keyed;
  **(10)** deleted `goal_text` readable from the SQLite file image (`secure_delete` → owner / A4′).
- **A1″ (ADR «Этап 5.5 (A1″)» § «Приёмка на устройстве»; signed build `db1e75d` unfixed):** `tr`
  **triggers** all judged (zero unjudged) — A4′ phase 0's six `tr` **strings** are not (owner item,
  `§HANDOFF` «Фаза 0 A4′»); not covered: §A8 sampled, wire never captured, Part C not run, P2 not
  narrowed. Findings **(a)** cancelled uninstall reads «выполнено» — repaired in code by phase 0
  (`HandedOff`); **(b)** `HOME`-role grant unseen by a live process (`ShortcutRefreshTrigger`) — excluded
  from A4′, own address; **(c)** silent BYOK misconfiguration and **(d)** no answer to a question —
  closed in code by phase 3a. Four checklist defects → rule: a line predicting a visible rendering is a
  hypothesis until run once. Residuals: **(1)** `app` resolved regardless of `required` (R14-37) — closed
  in code by phase 0 (`ToolArgumentSorts`, since 3b Task 3 `CatalogArgumentSorts`); step-line rendering
  still keys on `"app"`/`"app_label"` (not taken by 3b; open); **(2)** self-uninstall refusal fires **after** consent
  (R14-38; owner decision to move it); **(3)** `NoPlan` at 2b from an unresolved name sends the raw
  command off-device (R14-39) — narrowed by 3a, **closed by 3b Task 5** (answered on the device; value
  slots → model by E6-R1); **(4)** closed; **(5)** `ToolWorker` holder list file-keyed, «add a tool» guard
  surface not enumerable in advance (five → eight lists) — closure = spec §8.2 for workers; **(6)** two-slot
  form bounded by **order** (Turkish); **(7)** only `uninstall_app` reads arguments by name; **(8)**
  `DoctrineGuardTest`'s authored branch has `String.contains` blindness; **(9)** timing-sensitive
  `:data:ai-cloud` test (Build gate); **(10)** `open_app_info` line renders the package; **(11)**
  `open_location_settings` `emptyList()` unmeasured (P2); **(12)** `open_battery_settings` ROM-dependent.
- **Method debt (A1″, binding):** eight cases of evidence not describing what it claims (R14-15/28/31/36/
  40/43×2, the `open_app_info` pin; R14-44 at gate level). **(a)** a rendering assertion whose expected
  values share substrings proves nothing — realistic fixtures are the dangerous ones; **(b)** the last
  action of a mutation-proving task is a gate re-run from a cleared `build/test-results`.
- **A4′ phases 0–4: nothing seen on a phone** (one acceptance at the end; every on-screen line in the
  phase records is a hypothesis). 3a residuals (`§HANDOFF` «Фаза 3a A4′»): **(1)** orphan `Running` row on
  a mid-save supersede/cancel; **(2)** F3a-C (a): a bare alias phrase under a broken key reads
  «understanding unavailable» («alias before the model» → owner / A3); **(3)** learned choices do not
  carry between FastPath and the bridge for `ru`/`tr` (fails safe); **(4)** `open_url` scheme case —
  closed by 3b Task 3 (`UrlSort`); **(5)** the bridge ceiling is cooperative; **(6)** the budget is pinned by a
  test since 3b (40 s + reasoning cap on OpenRouter, 50 s elsewhere — owner 2026-11-17), corpus labels still the
  agent's draft; **(7)** docs naming the deleted router — closed 2026-11-20 by the docs sync
  (`README.md` rewritten). **3b named limits** (`§HANDOFF` «Фаза 3b A4′», archive): a
  fold-exact name binds with no pick on FastPath — «открой lumora» launches «Lumora+», a symbols-only label
  cannot be typed (`uninstall_app` asks instead since 2026-11-17, owner condition (3)); a broken alias table
  reads as «no alias forms» (`AliasStoreImpl.observeAll`); learned-choice rows keyed before 2026-11-17 under a
  symbol («tessar.») are orphans, visible in Settings; JVM harness one consent per invocation; `NeedsCard` drops an acted
  step's undo (latent, «first producer»). **Phase 4** (ADR «2026-11-20 — A4′ ф4…»): **(1)** the learned mark lives in
  process memory — gone after process death, absent on a learned card before «Выполнить» (fork 4-5); **(2)** B-5
  fails open — an unreadable store or a failed 2c start sends the command to the model online (JVM ends it);
  **(3)** not learnable, asked every time: no-space scripts, a value cutting a token, a form with no word, adjacent
  slots, > 64 chars; each wording pins on its own streak (§8.1 «multilingual for free» not delivered); **(4)**
  instrumented `MigrationTest` `v4_to_v5`/`v1_to_v5` written, not run; **(5)** backup rules `CODE-GREEN` only, no
  restore/transfer seen; memory **and every setting** (`flag_local_only` too) not moved to a new phone; **(6)**
  I1 closed 2026-11-20: rewritten, re-signed (`072889ca`), not seen on a phone; **(7)** a replayed
  URL ending «)» is cut (`SLOT_EDGE_PUNCTUATION`) and opens at once; **(8)** «Забыть» on the screen asks first since
  2026-11-20 (`SidrForgetGate`, T8 «a», `bb4656fa`); a forgotten row stays in SQLite free pages (A1′ (10)).
- **A0 engine gaps:** the engine stops **waiting** at the budget (phase 2), not the **act**; binder never
  measured; the clock is the dispatcher's, not a port; a persisted `Failed` loses its `CommandFailure`
  variant; `GoalShape` stays at one value until A4′ (Master Plan §3.6 `B1`). **Guards D1–D11** (`§HANDOFF`
  archive): owner-level D1, D3/D8 (declare `domain/src` in the same commit as a new production source set);
  cheap D2, D4, D6, D7, D9, D10; D11 closed by phase 2.
- **`CODE-GREEN`, not `DEVICE-ACCEPTED`:** DS-5's checklist never run (since 2026-07-13); I18N-1 verified
  only by agent-driven `adb`/`uiautomator`; DS-6B's MWL times cross-implementation-verified only.
- **Performance:** cold start 766 ms (`< 400 ms` aspirational); heap 55 MB PSS (2026-08-19);
  `baselineprofile/` has no startup/memory metric. **Release gate:** `checkOwnerReviewedLocaleStrings`
  checks a content signature (`OWNER-REVIEWED <date> sha256:<16 hex>`). **Turkish morphology:** closed at
  confirmation level by 3b (B-2: `telegramı aç` is offered «did you mean», learned at streak ≥ 3 for a launch,
  never bound alone; no language tables). **Untested:**
  Android 9/11/14, LOW_END hardware, STT `Ready`/`Partial` (OQ#4), boot warmup after a physical reboot.
- **Not owned by any block:** `RoomColumnNames` is hand-written and its guard scans **it**, not the
  entities or the exported schema (a forgotten column passes; matters since `goal_text` is raw command
  text; agrees with `schemas/…/5.json` today).

## Hard rules

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`. It is now
  `kotlin.multiplatform` (`android` + `jvm` targets, Этап 2.2, ADR 3/4) — production code lives in
  `commonMain`, so introduce nothing JVM- or Android-specific there; it would fail to compile for the
  other target. Tests live in `jvmTest` (JUnit4 isn't `commonTest`-portable) — see `core:testing`.
- **A closed sum in `commonMain` is not a one-file edit — weigh that before adding a value.** Measured in
  A0.5: `GoalShape` gained **one** value and cost **four files in three modules** (`:domain`,
  `:data:repository`, `:feature:launcher`), because exhaustive else-free `when` sites live outside
  `:domain` and `:domain:jvmTest` never compiles them. The open value types over `String` (`ToolId`,
  `ActionId`) cost **zero** for the same kind of addition.
- Interfaces in `domain`; implementations in `data/*`. UI holds no business logic.
- No `feature -> feature` deps. Single `NavHost` in `app`. ViewModels emit
  `NavigationEvent`; they never touch `NavHostController`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- **Understanding belongs to the model. Execution belongs to the deterministic layer.**
  *(ADR "2026-08-19 — ADR 1/4 (agentic restart)"; replaces the former "fast local intent matching runs
  before any LLM call".)*
  1. **FastPath** (deterministic, localized) answers frequent exact commands without a model. It is a
     **latency optimization, NOT a filter on understanding**.
  2. The **learned-plan cache** replays already-understood goal shapes deterministically and offline.
  3. Everything else goes to the **model planner**. A FastPath miss is **no longer** grounds to answer
     "Unknown command".
  4. Nothing the model proposes executes, gains rights, or leaves the device except through
     deterministic gates: `ToolRegistry` → argument validation → preconditions → risk gate / consent
     → loop bounds → egress allow-list → trace.
  5. `localOnlyMode` / no provider / offline ⇒ FastPath + plan cache + an honest statement of which
     of the three it is — never "Unknown command", which blames the command for the system's state.
     Parity stays test-checkable and means exactly two things: **the model planner is not consulted and
     nothing leaves the device.** Deterministic plan replay is part of the local path (rule 2 above) and
     **may** change an outcome FastPath decided — A0's two-step plan replacing "no such app" is that
     case (`DOC-ADL-3`, amended twice: 2026-08-20 and 2026-08-22; cite the ID, the text moves).
- **Understanding vs. execution, not matching vs. generation.** One contour may both speak and act
  (ADR 4/4 — one `AgentSession`, a 0-step plan *is* a spoken reply); what may never merge is
  **proposing** and **executing**. `GenerativeAiEngine` (→ `Flow<AiChunk>`, transport) stays a
  different port from `IntentMatcher` (→ `IntentMatchResult`) and from the structured `Planner` and the
  bridge's `ModelPlanner` — those are different *shapes of answer*, and that separation is unaffected.
  **Nothing the model proposes runs before the interpretation card's «Выполнить», and then only through
  the engine — except a learned command** (a form + template the user confirmed three times, step 2c), which
  starts its session through the same engine with no gate skipped; a risky step's consent card is asked every
  time (Fork R4, D3 amended by ADR «2026-11-20 — D3, поправка: выученная команда», signed 2026-11-20;
  `OneModelPathGuardTest` holds that the router is gone and that the launcher's one direct `LauncherAction`
  execution is the web chip the user points).
- **Boundaries are laid on the first slice, for two tools — not "when needed".** Registry as the only
  path to the world, argument validation, consent gate, loop limits, trace, egress allow-list,
  rollback. Functionality scales with need; boundaries do not (plan §«Правило роста»).
- Launcher core works fully offline; optional permissions never block startup.
- **User-facing text never originates in `domain` — and not in a ViewModel either.** Domain and
  ViewModels emit typed results (`CommandMessage`, `CommandFailure`, `CommandFeedback`); the feature
  layer chooses the string via `sidrString(R.string.…)`. Enforced by `HardcodedUiTextGuardTest` and
  `StringSeamGuardTest`.
- **Strings and all main-locale translations ship in the same commit as the feature.** A block is not
  gate-green until `en`/`ru`/`tr` are complete — enforced by `LocaleCompletenessGuardTest`.
- Outbound content is a **positive allow-list** (`OutboundContextPolicy`), widened only by an ADR and
  only with a guard test proving a planted sentinel never leaves the device.

## Build & verification gate

- **JDK 17.** The machine's default JDK is newer and Gradle cannot parse it; run with the Temurin 17
  toolchain (`-Porg.gradle.java.installations.paths`, `local.properties` is git-ignored).
- Gate: `./gradlew :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test` —
  plus `:core:ui:verifyRoborazziDebug` whenever `core/ui` is touched, and `:app:assembleRelease` for
  release-affecting work. **`:domain:jvmTest` and `:consumer:jvm:test` must be listed explicitly:**
  `testDebugUnitTest` has not reached `:domain` since it went KMP, and it never reaches `:consumer:jvm`
  at all. **Baseline: the floor in `tools/gate.sh` (`BASELINE_TESTS`, 1914 since the acceptance's in-round fixes (2026-11-21; 1912 after T8, 1910
  at the phase's close, 1841 before it), 557 actionable tasks executed, `build/test-results` cleared first) — compare a
  fresh gate against the floor and against the active phase's predicted chain (its plan), not against
  any number quoted in an older ADR.** The decomposition by module is derived **before** every boundary
  run and compared with it — a sum alone cannot tell «two added» from «two added and two lost»; the chains
  of closed phases are in `§HANDOFF` (archive) and the ADRs.
  **Two environmental shapes that redden a gate for a reason unrelated to the diff:** `:data:ai-cloud`'s
  `first token arriving just under the deadline still completes normally` is timing-sensitive under
  back-to-back Gradle load (passed on an immediate re-run from a cleared tree); and after about twenty
  mutation runs a two-hour-old Gradle daemon (2.7 GB) plus Kotlin daemon (2.1 GB) drove the machine into
  swap and a boundary run went red on 60-s timeouts — **stop the daemon (`gradlew --stop`) before a
  boundary gate that follows a mutation batch.**
  **Clear `build/test-results` before a boundary gate** (R14-44): stale XML left by a mutation run is
  indistinguishable from a red gate and misleads in both directions. The last action of any
  mutation-proving task is a gate re-run from a cleared tree.
  Read counts from the JUnit XML, not the console. **Use `--rerun-tasks`, never the plan-text `--rerun`**
  — the latter is not a valid Gradle 9.5.0 build-level flag and silently returns everything `UP-TO-DATE`
  while still printing `BUILD SUCCESSFUL` (one false green inside A1′); a genuine run prints
  `N actionable tasks: N executed`.
- **The Gradle daemon is ON** (owner decision 2026-09-25; `gradle.properties` sets no `org.gradle.daemon`
  line, nothing is passed). What makes it safe is two flags, not the daemon: `org.gradle.caching=true`
  and the daemon's watched file-system state mean a run that passed neither could be served a test
  task's pre-change XML `FROM-CACHE`. **`tools/gate.sh` runs the gate** in two modes whose labels differ
  on purpose: the **boundary** run (no arguments; it refuses a task list) clears every module's
  `build/test-results`, passes `--rerun-tasks`, counts from the JUnit XML and says `GATE GREEN` only with
  zero failures/errors **and** a total at or above its floor; a **scoped** run
  (`--scoped :module:task …`, for a TDD step or a mutation) clears and counts the named modules only,
  passes `--no-build-cache --no-watch-fs`, reads from Gradle's log how **each named task** ran, fails as
  `SCOPED NOT RUN` when a named test task did not execute, prints each failing test as a `FAILED ::`
  line, and labels itself `SCOPED GREEN … — NOT a boundary gate`.
- **Never pipe `gradlew` through `tail`** — that masked a red gate as exit 0 on 2026-07-13. Check the
  exit code and read the real output. (`tools/claude/guard-bash.sh` refuses it, a **destructive** push —
  force, `--force-with-lease`, delete, `--mirror`, `--prune`, a `+`/leading-`:` refspec — and Gradle-driven
  instrumented tests on the owner's phone.)
- A stage/block is not closed until the gate is green, an ADR is written, `CLAUDE.md` +
  `ai-context/current-status.md` are synced, and a commit is **proposed to the owner**. The agent commits
  **and pushes** (owner decision 2026-11-17; before it, the agent never pushed): `git push origin <branch>`,
  `origin` = GitHub named explicitly (the second remote is Codeberg), never a force push — a diverged
  branch is fetched and merged.
- **"Synced" includes the TAIL, not just the header** (rule added 2026-09-20, after A1″'s acceptance
  found `current-status.md` asserting that `Migration3To4` had never run — disproved on device two months
  and five blocks earlier). At every close, **after** the header entry, re-read that file's last three
  sections — «Not claimed done», «Source of truth» and «Keeping this file honest» — and correct whatever
  the closed block falsified. A status snapshot carrying a false claim is worse than one carrying none.
- **This file has a size ceiling** (`ClaudeMdSizeGuardTest`, `:app`): status and pointers here, narrative
  in the ADR or `§HANDOFF`. A red guard is not a reason to raise the ceiling; it is a reason to move text.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` | `domain` |
| `ActionId`/`ActionIds` (**frozen**), `LauncherAction`, `ActionDescriptor`, `ActionCatalog`, `ActionRiskLevel`, `requiresConsent(risk)` (the one risk → gate predicate, `DOC-ADL-1`) | `domain` |
| The bridge (A4′ phase 3a, the one model path): `ModelPlanner` + `ModelPlanning` / `BridgeModelPlanner` / `BridgeValidator` / `BridgeRendering` / `BridgeFilter` / `BridgeBudget` / `BridgeTransport` / `ToolDescriptions` / `MentionBinder` / `Interpretation` | `domain/ai/bridge` |
| `RouteCommandUseCase` (incl. step 2b — a FastPath miss tried against the tool vocabulary — step 2c, a learned command, and step 7, the bridge) | `domain/ai/router` |
| `ExecuteActionUseCase` (`LauncherAction` → resolve → execute → `CommandOutcome`; callers: the `in_app` tool worker and the web chip) | `domain` |
| Tool vocabulary — `ToolId`/`ToolIds`, `ToolDescriptor`/`ToolDurability`/`ToolLevel`/`ToolLevels`/`ToolEffect` (provenance, `DOC-ILM-2`), `ToolInvocation`/`ResolvedInvocation`, `ToolOutput`, `ArgSource`, `ToolResult`/`ObservedFact`, `InvocationValidator` | `domain/tool` |
| Argument sorts (A4′ phase 3b): `ArgSort`/`ArgSorts`, `SortResolver`/`SortResolution`, `ArgumentSorts` port (`CatalogArgumentSorts` in `:data:repository` since Task 3; was `ToolArgumentSorts`), the pure value sorts (`DurationSort`, `ClockTimeSort`, `UrlSort`), `TextFolding` port (`IcuTextFolding` on Android, JDK on JVM) + `AppNameMatcher` (word-form rule B-2), `LearnedChoiceKeys` | `domain/sort`, `domain/text`, `domain/memory/resolution` |
| Learned-plan cache (A4′ phase 4): `CommandForm`/`FormPart`, `PlanTemplate`/`TemplateStep`/`TemplateArg`, `LearnedPlan`, `LearnedForms` (learn/match/fill), `LearnedPlanStore` port + `Record`/`Forget`/`ObserveLearnedPlansUseCase`, `LearnedPlans`/`DefaultLearnedPlans`/`Replay` (replay through the bridge's validator and binder) | `domain/memory/plan` |
| Ports: `ToolRegistry`, `ToolExecutor` (**the only path to the world**; one call site, below the consent checkpoint), `ToolWorker` (per-adapter port, one hop below `ToolExecutor`, its own call-site guard) | `domain/tool` |
| `ToolAdapter` + `ToolFederation` (one object; `registry`/`all()`/`find()` and the sole `ToolExecutor` implementation read the same adapter list by construction; first-adapter-wins collision; **both faces derived per call since A1″**) | `domain/tool` |
| Agent engine — `AgentGoal`/`GoalShape`, `ExecutionPlan`/`PlanStep`, `AgentSession`/`ExecutionState`/`ConsentCheckpoint`/`RuntimeBudget`, `AgentExecutor`, `Planner`/`TemplatePlanner`/`CompositePlanner`, `PlanningResult`, the use cases (`Start`/`Run`/`ResolveConsent`/`Cancel`/`Resume`) | `domain/agent` |
| Ports: `AgentSessionStore`, `AgentSessionIdFactory` | `domain/agent` |
| `TraceEvent` / `ExecutionTrace` (no timestamps — the data layer stamps rows) | `domain/trace` |
| `PromptContextBuilder` + `OutboundContextPolicy` (outbound allow-list/guards) | `domain` |
| Pref models (`UserPreferences`, `FeatureFlags`, `CachedSuggestion`) + history models + their repos | `domain` |
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) | `domain` |
| `DeviceProfile`/`DeviceCapability` + `DeviceProfileProvider` port | `domain` |
| Suggestion + voice contracts; prayer domain (`PrayerContext`, `GetPrayerContextUseCase`) | `domain` |
| `RuleBasedIntentMatcher`, `InstalledAppsRepository` impl, `AndroidActionExecutor` | `data/repository` |
| `SystemIntentToolSource` (projects `ActionCatalog` → four `ToolDescriptor`s, level `in_app`: A0's two + `web_search`/`open_url` since A4′ phase 3a) + `SystemIntentToolWorker` (over the unchanged `ExecuteActionUseCase → IntentActionResolver → ActionExecutor` chain) + `RoomAgentSessionStore` | `data/repository` |
| `Tier0IntentToolSource`/`Tier0IntentToolWorker` (level `system_intent`, **fifteen tools in four families**: `set_timer`/`set_alarm` (both need `com.android.alarm.permission.SET_ALARM`); **`uninstall_app`** (the track's first `CONFIRM`/`DURABLE` tool, needs `android.permission.REQUEST_DELETE_PACKAGES`, refuses our own package); `show_alarms` + `open_camera`; and the settings-screen family `open_system_settings` / `open_wifi_settings` / `open_bluetooth_settings` / `open_battery_settings` / `open_data_usage_settings` / `open_display_settings` / `open_sound_settings` / `open_location_settings` / `open_notification_settings` / `open_app_info` — all `SAFE`, none needing a declared permission, `open_app_info` the only argument-carrying one. Every `Intent` is built **inline in its own `when` arm**: a shared helper would take the whole worker out from under `ToolPermissionManifestGuardTest`) + `ToolMatchPlanner`/`ToolVocabulary` (localized `en`/`ru`/`tr` reachability feeding `RouteCommandUseCase` step 2b; **bounded two-slot form** `<A> <infix> <B> <suffix>`) | `data/repository` |
| `AuthoredToolDescriptions` (the bridge's `ToolDescriptions`) + `DeviceMentionBinder` (the bridge's `MentionBinder`: binds the model's app mention on the device, after the answer) + `AppSortResolver` + `SortProvidesModule`'s registry (`SortRegistryGuardTest`, `:app`) | `data/repository` |
| `MemoryToolSource`/`MemoryToolWorker`/`MemoryToolIds` (level `launcher_memory`, all `SAFE`+`LOCAL`+`TRANSIENT`: `set_app_alias`, `forget_app_alias`, `forget_learned_choice` — thin adapters over `SaveAliasUseCase`/`DeleteAliasUseCase`/`DeleteLearnedChoiceUseCase`) | `data/repository` |
| `ShortcutToolSource`/`ShortcutToolWorker`/`ShortcutToolIds`/`ShortcutCatalog` (level `app_shortcut`; one tool per Android app shortcut, **name is data not a literal**; availability is the `android.app.role.HOME` **role** — without it the catalog is empty and the source advertises nothing) + `DynamicToolNames`/`DynamicToolName` | `data/repository` |
| `ToolSelector` (one command → at most one tool over a partly third-party registry: authored beats dynamic, a dynamic candidate must name its app, equal candidates **decline**, every command token must be accounted for) + `AppTargetResolver` (a name → one package or nothing, never a guess; consulted by the planner **above** the consent gate; windows + word forms since 3b) + `ToolPermissionCatalog` (production `ToolId` → manifest permissions; a missing row is **not** consent) | `data/repository` |
| DataStore impls + `PreferencesMapper`/`PreferencesKeys`; Room entities/DAOs/`SidrDatabase`/migrations/mappers; `LearnedPlanStoreImpl` (`learned_plans`, schema 5) | `data/repository` |
| Suggestion providers + `SuggestionEngineImpl`; `SecureSecretStore` impl + `KeystoreSecretCipher` | `data/repository` |
| Cloud AI client (Ktor SSE engine, `OpenAiCompatibleBridgeTransport`) | `data/ai-cloud` |
| Offline prayer calculation (`AdhanPrayerCalculator`), city index, schedule cache DTOs | `data/prayer` |
| `AndroidPermissionChecker`, `AndroidDeviceProfiler` + `DeviceProfileClassifier`, `AndroidSpeechInputSource`, `PackageManager` access | `core/android` |
| `UiState`, dispatchers, logging contracts; `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| Design system, theme, primitives/controls, `sidrString`/`SidrStringOverlay`, Roborazzi goldens | `core/ui` |
| `SandboxToolIds`/`SandboxToolSource`/`SandboxToolWorker` (level `sandbox`, one adapter in the same `ToolFederation`) + `FilePlanner` + `JvmAgentSessionStore`/`JvmAgentSessionIdFactory` + `SessionDto`/`SessionMapper` + `ConsoleHarness`; since A4′ 3b the same bridge: `JdkBridgeTransport` (`PROMPT_ONLY`, https only) + `SandboxToolDescriptions` + `SandboxArgumentSorts`/`SandboxFileSort` + `SandboxMentionBinder` + `JdkTextFolding`; since A4′ phase 4 `JvmLearnedPlanStore` (learned commands in a JSON file) | `consumer/jvm` |
| Test fakes / fixtures | `core/testing` |
| Feature UI + ViewModels + feature-local presentation mappers | `feature/*` |
| Single `NavHost`, composition root, Hilt graph, DI modules, i18n guard tests, WorkManager wiring, doc guards (`DoctrineMatrixGuardTest`, `DeviceDatabaseReadGuardTest`, `ClaudeMdSizeGuardTest`) | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |
| Claude Code hooks: `tools/claude/guard-bash.sh` (PreToolUse), `tools/claude/codegraph-prompt-hook.sh` (UserPromptSubmit filter), each with a cases file and a test script | `tools/claude` |

## Do not

- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets go through the
  `SecureSecretStore` port (Keystore-backed).
- Don't resurrect the deleted ONNX stack (`LayeredIntentMatcher`, `LocalInferenceGate`, `ModelStore`,
  `TextEmbedder`, `:data:ai-local`). A future local runtime is a **new design** on LiteRT/LiteRT-LM,
  not a restoration (ADR 2/4).
- Don't widen `ActionIds` or change its seven string values (ADR 3/4).
- Don't unify the `Failed`/`Completed` divergence of A0.5 §6.3. The same reality — "the thing you asked
  about is not there" — ends `Failed` on `:consumer:jvm` and `Completed` on Android, because the
  observation vocabulary is a closed two-value enum. That is a **recorded finding owned by A1′**, not a
  defect awaiting repair (owner instruction 2026-08-23), and it is held mechanically by
  `CoreVocabularyFreezeGuardTest` and by `AgentLoopTest`'s named divergence test. A diff that touches
  either to make the two agree is a revert, not a result.
- Don't add autonomy without consent, and don't route around the risk/permission gates.
- Don't put history back into this file.

## Source of truth

| What | Where |
|---|---|
| Architecture, module structure, performance tiers | [docs/architecture.md](docs/architecture.md) |
| ADR log — **all history, one ADR per closed block** | [ai-context/decisions.md](ai-context/decisions.md) |
| Recent status snapshot | [ai-context/current-status.md](ai-context/current-status.md) |
| Active track plan (stages, §0 protocol, live `§HANDOFF`) | [docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md) |
| `§HANDOFF` archive — closed phase records (A4′ Фазы 0–4, the A1″ handoff) under their original headings | [docs/superpowers/plans/2026-08-18-agentic-track-restart-handoff-archive.md](docs/superpowers/plans/2026-08-18-agentic-track-restart-handoff-archive.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |
| Agentic target architecture A1–A6 | [docs/agentic-os-architecture.md](docs/agentic-os-architecture.md) |
| Doctrine — rules `DOC-*`, verification types, test per rule | [docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md) |
| Agentic track Master Plan — blocks A0…A6, DoD, change-control | [docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md) |
| Design system rulebook | [docs/governing/sidr-design-system-master-plan-v1.2.md](docs/governing/sidr-design-system-master-plan-v1.2.md) |
| Two principles of the agentic OS — protected openness, local by default | ADR «2026-11-07 — Два принципа агентной ОС…» in [ai-context/decisions.md](ai-context/decisions.md) |
| Ontology v0.1 — **a draft for discussion, not a decision** | [docs/governing/sidr-ontology-v0.1.md](docs/governing/sidr-ontology-v0.1.md) |
| Specs, task plans, closed phase checklists | [docs/superpowers/specs/](docs/superpowers/specs/) · [docs/superpowers/plans/](docs/superpowers/plans/) · `ai-context/phase-{3,4,5,6,7}-*.md` |

### History map — which ADR to open in `decisions.md`

- Phases 3–4 (foundation): Blocks **A → H** · Phase 5 (cloud AI): **I → N** + "Phase 5 close summary"
- Phase 6 (local NLU, stack since deleted): **O → R** + "OQ#1 amended" · Phase 7 (voice + suggestions): **S → W**
- UX + hardening: **X1 → X6**, **Y1 → Y7**, device-acceptance rounds 1–3
- Stage-1 AI Launcher: **AIL-0 → AIL-6** · Stage-2 memory: **S2-1**, **S2-2**
- Design track: **DS-0 → DS-11** + Vision MVP preview · Localization: **I18N-1**, **I18N-2**
- Agentic restart: **ADR 1/4 … 4/4**, **Этап 0.2 / 0.3 / 0.4 / 0.5 / 0.6 / 0.7 / 2 / 3** (2026-08-19) ·
  **Этап 4.0** (2026-08-20) · «Развилка агентного трека» — two consumers (2026-08-21) · **Этап 4 (A0)** (2026-08-22) ·
  «Сквозное ревью блока A0» (2026-08-23) · **Этап 4.5 (A0.5)** — second consumer (2026-08-26) ·
  **Этап 5 (A1′)** — federated `ToolRegistry` (2026-09-03; the owner's device acceptance of
  2026-09-10 is a section inside that same ADR) · **Этап 5.5 (A1″)** — tool mass + selection,
  phases 0–3a (2026-09-19), **phase 3b as a section inside that same ADR** (2026-09-20), **and the
  owner's device acceptance as its last section** (2026-09-20) · **«Две строки доктрины»** —
  `DOC-HMA-5` and `DOC-ILM-5` (2026-09-20; docs only)
- A4′ (Этап 6) has no block ADR until it closes; its phase records are in the `§HANDOFF` archive (Фазы
  0–4). Inside it, two ADRs of phase 3a: «2026-10-05 — A4′ ф3a: `ACTION_CATALOG_SCHEMA` →
  `AUTHORED_TOOL_SCHEMA`» and «2026-10-05 — A4′ ф3a: один модельный путь; исходящий контракт —
  девятнадцать `ToolId`», whose addendum «Бюджет и транспорт по умолчанию — по замеру» (2026-11-07) fixes
  the bridge's numbers and whose subsection «Замер фазы 3b» (2026-11-15, clarified at close) re-fixes them;
  3b Task 3's ADR on argument sorts (header date 2026-11-07, written 2026-11-09, clarified at close); phase 4:
  «2026-11-20 — A4′ ф4: кэш выученных планов — форма со слотами, шаблон, схема 5» and «2026-11-20 — D3,
  поправка: выученная команда» (signed 2026-11-20) ·
  **«2026-11-07 — Два принципа агентной ОС: защищённая открытость и локальное по умолчанию»** (docs only) ·
  **«2026-11-11 — Сжатие CLAUDE.md (гигиена контекста)»** — this file's compression, with the verbatim
  appendix of everything that left it
