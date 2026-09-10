# CLAUDE.md — Sidr Launcher

> **Read-first for every session.** This file is **current state + rules + pointers only**.
> History does not live here — it lives in `ai-context/decisions.md` (ADR log), one ADR per closed
> block. Compressed 2026-08-19 by Этап 0.4 of the agentic track (101 KB → ~15 KB); the
> pre-compression text is preserved verbatim in git at `9de23ab:CLAUDE.md` and every fact it carried
> is in an ADR (verified block-by-block before deletion).

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
(owner-approved 2026-08-18). Session kickoff template + closing checklist are its §0; the closing
checklist is binding for every stage. `§HANDOFF` at the end of that file is written by the closing
session for the next one — read it.

Since Этап 3 the track also has a **Master Plan** — block sequence A0…A6, DoD, change-control,
milestones, and the `Agentic Shell v1 = DONE` definition:
[docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md).
Different layer, not a replacement: the restart plan keeps the owner's four decisions, the §0 session
protocol and `§HANDOFF`; the Master Plan governs how blocks are run from Этап 4 on. The eight Islamic
principles' **checkable** part now lives in
[docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md) as 28
`DOC-*` rules — cite a rule by ID, never paraphrase it; a rule's third column is a real test name or
an honest `<нет>`, and `DoctrineMatrixGuardTest` fails the build on a false claim.

**Owner decision 2026-08-21 — two consumers from the start.** ADR 3/4's "portable core, two consumers"
was backed only by a build flag: `:domain` builds for `androidTarget()` + `jvm()`, but `jvm()` has zero
consumers, the module has zero `expect`/`actual`, and `commonMain` carries eight `java.*` imports. The
owner resolved the fork in favour of a real second consumer. Consequences already in force: criterion 10
in the Master Plan's readiness definition; a new block **A0.5** between A0 and A1′
([brief](docs/superpowers/plans/2026-08-21-a05-second-consumer.md)); and fork **F6** in the A0 spec —
tools return values and steps bind to them, landing **before** the Room 3→4 migration. Full record: ADR
"2026-08-21 — Развилка агентного трека" in `decisions.md`. Everything else the revision found is parked
in `§HANDOFF`, not built.

| Stage | State |
|---|---|
| **1** — strategic ADR package | ✅ 2026-08-19 — four ADRs, docs only |
| **0.1** owner sign-off (release gate cleared — `:app:assembleRelease` green for the first time) · **0.2** FastPath `ru`/`tr` · **0.3** delete ONNX (APK 78 MB → 6.8 MB) · **0.4** compress this file · **0.5** honest statuses (`CODE-GREEN`/`DEVICE-ACCEPTED`/`CLOSED`) · **0.6** budgets rewritten on measured numbers (heap 55 MB PSS) · **0.7** I18N residue | ✅ 2026-08-19 |
| **2** toolchain (AGP 9.3.1/Kotlin 2.4.10/Gradle 9.5.0/compileSdk 37) + `:domain` → KMP | ✅ 2026-08-19 |
| **3** agentic Master Plan + doctrinal matrix (`docs/governing/`) | ✅ 2026-08-19 — docs + one guard test |
| **4.0** — invert the understanding flag (`llmRouterEnabled` → `localOnlyMode`, ADR 1/4) | ✅ 2026-08-20 code, **`DEVICE-ACCEPTED` 2026-08-22** in the Task 15 session (`ru-RU`, no provider ⇒ «ИИ-провайдер ещё не настроен» + a route to the provider screen, not `Unknown command`); first behavioural change of the track |
| **4** — A0 thin agentic spike | ✅ **2026-08-22 — `CLOSED`** — all nine work-order items, then Task 15: the owner ran all eight §12 acceptance items on the SM-A325F and signed off. Migration 3→4 executed for real by both routes (instrumented 9/9 on device; and a genuine `user_version` 3→4 upgrade of the owner's own database, `identity_hash` matching `4.json`). Residual limitations named in Known debt — the largest is that §12.8 is unreachable by any path a user can take (an A4′ debt). **Reviewed end-to-end 2026-08-23**: nine findings, eight fixed and mutation-verified (`CODE-GREEN`; two §12 items await a device re-check) |
| **4.5** — A0.5 second consumer of the portable core (`:consumer:jvm`) | ✅ **2026-08-26 — `CODE-GREEN`**; `DEVICE-ACCEPTED` **not applicable**, not absent — the block changes nothing on the phone. All four §3.1a questions answered with addresses, `B1` answered, the §6.3 divergence recorded and held by a test |
| **5** — A1′ federated `ToolRegistry` | ✅ **2026-09-03 — `CODE-GREEN`**; `DEVICE-ACCEPTED` **not met** — two new user-reachable system intents (`set_timer`, `open_system_settings`) shipped and made reachable from a typed command, and none of it has run on the phone (spec §16 criterion 1, the one open criterion of nine). Tool *mass* and *selection* split out as a new block, `A1″` |
| **5.5–7** — A1″ tool mass + selection, A4′ runtime, A2/A3/A5/A6 | each needs its own spec + plan (`brainstorm → spec → plan → build`) |

The four strategic ADRs (all 2026-08-19, in `decisions.md`):

1. **Deterministic-first redefined** — understanding belongs to the model, execution to the
   deterministic layer (see Hard rules). `FeatureFlags.llmRouterEnabled` was **inverted onto the new
   key** `localOnlyMode` / `flag_local_only`, default `false` (a plain default flip would have been
   inert — DS-11 `autoHideNavBar` precedent). **Implemented 2026-08-20 by Этап 4.0**, `DEVICE-ACCEPTED`
   2026-08-22; the old key is orphaned and there is no migration.
2. **Platform re-baseline 2026** — ONNX NLU closed, OQ#1/#2/#3 closed; local-inference runtime is
   **LiteRT / LiteRT-LM** (alternative on record: ExecuTorch; separate path: AICore); `AppFunctions` /
   `MCP` are first-class tool sources; performance budgets become three tiers.
3. **Portable core boundary** — "Framework" = a portable agent core, two consumers (Android + PC);
   `:domain` → KMP in Этап 2.2; **`ActionIds`' seven values are frozen byte-for-byte** (`launch_app`
   is a Room PK in `resolution_preferences`; all seven are the outbound wire contract to the LLM).
4. **Assistant ⊕ Agent** — one conversational loop, two surfaces: one `AgentSession`/`Planner`
   contract; a 0-step plan *is* a spoken reply, an N-step plan is a task.

**The A1 fork is decided.** Owner fork F1 (spec `2026-08-29-a1-federated-tool-registry-design.md`,
§2, ADR «Этап 5 (A1′)»): **parallel vocabulary + federation, identity C** — `ToolId`/`ToolDescriptor`
stay the agent's own vocabulary, `ActionCatalog` becomes one adapter among N, and a projected tool's
`ToolId` is *derived* from its `ActionId` rather than hand-copied. `ActionIds` is untouched.

## Shipped surface (2026-09-03)

Everything below is on `launcher--7` and `CODE-GREEN` (gate green: `:domain:jvmTest testDebugUnitTest
assembleDebug :consumer:jvm:test` — `:domain:jvmTest` and `:consumer:jvm:test` must both be listed,
`testDebugUnitTest` reaches neither since `:domain` went KMP; plus `verifyRoborazziDebug` where
`core/ui` is touched). Most of it is also `DEVICE-ACCEPTED` on SM-A325F / Android 13 — the owner
personally ran on-device verification and signed off — **except** Action & Safety (DS-5), the i18n
surface (I18N-1), FastPath's `tr` locale forms, and the whole A1′ federated-tool-registry slice below
(Этап 5) — all `CODE-GREEN` only; see Known debt. Этап 4.0 and the whole A0 agent slice (Этап 4) became
`DEVICE-ACCEPTED` on 2026-08-22 in the Task 15 session, which also exercised FastPath's `ru` launch
verb («открой …») on the phone — `tr` still has not run there. `CLOSED` = both, with any residual limitation named rather than implied absent (Этап 0.5 —
status vocabulary).

- **Launcher core** — home, app drawer, settings, app launch; fully offline.
- **FastPath routing** — `RuleBasedIntentMatcher`, verb/keyword vocabulary in `en`/`ru`/`tr`
  (`FastPathLocaleGuardTest`). Since Этап 0.3 the unqualified `IntentMatcher` binds it **directly**.
- **BYOK cloud AI** — Assistant (SSE streaming, OpenAI-compatible, key in Keystore) + the LLM action
  router (`CommandPlanner` → risk-gated confirm card → `ExecuteActionUseCase`). Since Этап 4.0 a
  FastPath miss reaches the planner **by default**, gated only by `FeatureFlags.localOnlyMode`
  (default off), a configured provider and connectivity; each blocked state says which one it is
  instead of answering "Unknown command".
- **Memory** — learned resolutions (auto-resolve at streak ≥ 3) + explicit aliases; on-device,
  correctable from Settings.
- **Suggestions** — time-of-day / usage (offline) + calendar / location (permission-gated),
  heuristically ranked, precomputed by WorkManager.
- **Prayer** — offline `adhan2`, bundled GeoNames city index, one-shot device location rounded to 2dp
  before crossing the port boundary; coordinates never persisted, never leave the device.
- **Design system v1.1** — grey tokens, `core/ui` primitives + controls, Roborazzi goldens.
- **i18n** — `en`/`ru`/`tr` on every migrated screen through one `sidrString` seam; per-app language
  switch; four guard tests + an owner-sign-off release gate.
- **Agent slice (A0, `CLOSED` — owner-accepted on the SM-A325F 2026-08-22; reviewed and repaired 2026-08-23, residual limitations in Known debt)** — one goal crosses two tool
  calls where the second consumes what the first **observed**, and the risk transition between them
  stops the loop for consent. `domain/tool` + `domain/agent` + `domain/trace` are the portable engine;
  `ToolExecutor` is its only path to the world; `ToolExecutorCallSiteGuardTest` holds mechanically that
  there is exactly **one** call site and which file it is in, and `AgentExecutorTest` holds
  behaviourally that it sits below the consent checkpoint — the scan reads a file name and a count,
  never a position. The planner is deterministic (`TemplatePlanner` over `GoalShape`),
  so the agent runs in **every** network state and consults no model — the branch cuts into
  `RouteCommandUseCase` **above** the `localOnlyMode` check. A FastPath "no such app" therefore becomes
  a two-step plan (launch → offer the store) offline, local-only and online alike, instead of a dead
  end. The whole session — goal, plan, observations, consents, trace — lives in Room (schema 4) and is
  **deleted by cascade** on any terminal state (`AgentAtRestGuardTest`, five terminal paths). The one
  registered tool source projects the **unchanged** `ExecuteActionUseCase → IntentActionResolver →
  ActionExecutor` chain into two descriptors.
- **Second consumer of the portable core (A0.5, `CODE-GREEN` — device acceptance **not applicable**, the
  block changes nothing on the phone)** — `:consumer:jvm`, a plain `kotlin.jvm` module with **zero Android
  artifacts on its resolved classpath**, runs one goal through `goal → plan → gate → tool → observe → tool
  → result → trace` on the same **unchanged** `domain/agent` / `domain/tool` / `domain/trace` contracts.
  Its own three sandboxed file tools (zero-, two- and one-argument), its own deterministic `FilePlanner`,
  its own JSON session store honouring `recordConsentIfPending` as a **real** compare-and-set, and a
  console harness that walks both process-death shapes — including a death **mid tool call**, the seam
  Android's own acceptance could only reach through a 157–170 ms window. `SandboxToolExecutor` is inside
  the **same** mechanical boundary as the Android one: `ToolExecutorCallSiteGuardTest` scans the consumer
  too, holders go three → four, and the call-site count stays exactly **one**. The whole block's `commonMain`
  footprint is two edits in two files: the `GoalShape.Free` value and the `NoPlan` arm it forces in
  `TemplatePlanner`.
- **Federated `ToolRegistry` (A1′, `CODE-GREEN` — `DEVICE-ACCEPTED` not met, spec §16 criterion 1: this
  block has not run on a phone)** — the one `ToolRegistry` port now federates **N** adapters behind one
  object, `ToolFederation`, whose `registry` (`all()`/`find()`) and `executor` read the same adapter
  list by construction, so no wiring path can advertise a tool the dispatcher cannot route (the direct
  fix for A0 review finding F2). A **second real Android source**, `Tier0IntentToolSource` +
  `Tier0IntentToolWorker` (level `system_intent`), registers two Tier-0 system intents — `set_timer`
  (arity 1, `SAFE`, `EXTERNAL`) and `open_system_settings` (arity 0, `SAFE`, `EXTERNAL`) — beside the
  existing `in_app` adapter (`SystemIntentToolSource`/
  `SystemIntentToolWorker`, still the unchanged `ExecuteActionUseCase → IntentActionResolver →
  ActionExecutor` chain). Every registered tool now declares `level`/`effect`, and an `EXTERNAL` tool's
  provenance reaches the user (`AgentSessionPresentation.toolLabelFor`/`provenanceLabelFor`, rendered on
  `AgentSessionSurface` and on `LauncherScreen` — `DOC-ILM-2`, held behaviourally, not by a structural
  scan). `requiresConsent(risk: ActionRiskLevel)` in `domain/action` replaces four independently-spelled
  gate checks with **one** predicate (`DOC-ADL-1`; see Known debt for what its closure actually rests
  on). A FastPath miss now also tries one **generic** planner arm — `ToolMatchPlanner` in
  `data/repository`, matching against a localized (`en`/`ru`/`tr`) tool vocabulary — from routing step
  **2b** in `RouteCommandUseCase` (between the existing A0 agent branch and the `localOnlyMode` check);
  a match starts a one-step agent session in every local state, exactly like A0's launch→store plan, and
  `GoalShape` gains **no** new value for it. `ToolWorkerCallSiteGuardTest` holds the second hop
  (`ToolWorker` invoked only from inside `ToolFederation`) the same mechanical way
  `ToolExecutorCallSiteGuardTest` (re-anchored, not weakened) holds the first. Tool *mass* beyond these
  two tools (`LauncherApps`/shortcuts, ~10 more Tier-0 intents) and tool *selection* before the planner
  are **not** in this block — split out as **A1″**, owner fork F2.
- **PREVIEW surfaces** — Tasks / Agents / Activity / Terminal: non-functional badged mock-ups, zero
  fabricated data (owner decision 2026-08-18: they stay).

## Known debt (honest list)

Not `CLOSED`. Status vocabulary (Этап 0.5): `CODE-GREEN` (gate green, no device claim) /
`DEVICE-ACCEPTED` (owner ran on-device verification and signed off — an agent-driven `adb`/`uiautomator`
pass does not count) / `CLOSED` (both, plus any residual limitation named, not implied absent). Each item
below is recorded in its own ADR.

- **A0 and Этап 4.0 are `DEVICE-ACCEPTED` as of 2026-08-22, with three residual limitations** (owner ran
  all eight §12 items on the SM-A325F and signed off; full record in the A0 ADR): **(1)** §12.8 —
  "app installed ⇒ the store step is skipped" — is **unreachable by any path a user can take**, because
  `AgentSession.resumed()` continues from the persisted cursor and nothing re-plans, so a session that
  already observed `APP_NOT_INSTALLED` opens the store even if the app was installed while the plan sat
  paused; it passed only through the `cursor == 0` / mid-step shapes, the latter produced with
  `pm disable-user` plus an on-device `force-stop` poll (window 157–170 ms warm, 1033 ms cold). This is
  a **staleness** debt owned by **A4′**, alongside the missing wall-clock budget, and it is narrow today
  only because Master Plan §3.6 `B1` holds `GoalShape` at one value. **(2)** the plan list is not drawn
  in `AwaitingConsent` — the two-step shape is visible in `Paused` and `Completed` only. **(3)** item 8's
  acceptance needed agent intervention, so it exercises the engine rather than the product. Migration 3→4
  is no longer a debt: it executed on device both instrumented (9/9) and as a genuine `user_version` 3→4
  upgrade with a matching `identity_hash`. The former limitation «"План выполнен" means "every step ran"»
  is **fixed** — and it turned out to understate the problem: a plan closed `Completed` even when a step
  *failed*, so the wording is now `Partial`-toned and each step carries its own state (see the row below).
- **The 2026-08-23 fix round is `CODE-GREEN`, not `DEVICE-ACCEPTED`, and A0's `CLOSED` carries that as a
  named residual.** A cross-cutting review of the whole block found nine defects; eight are fixed, each
  mutation-verified, gate green at 1158 tests (ADR «2026-08-23 — Сквозное ревью блока A0»). Three of them
  change behaviour the owner accepted on 2026-08-22, so **two §12 items need re-running on the phone**:
  §12.5 (`force-stop` mid-plan → Continue now *resumes* the pending call instead of re-issuing it — one
  `ToolInvoked` on disk, not two) and §12.8 (the wording changed: «План пройден, выполнено не всё» with the
  store step marked «не потребовалось», instead of «План выполнен» with two green markers). The six new
  strings are not Class B, so the locale signature was neither touched nor re-signed. Until that re-check,
  the changed behaviour is accepted by the gate, not by the owner. **The re-check is written out as a
  runnable checklist, not left as this sentence:**
  [docs/superpowers/plans/2026-08-23-a0-device-recheck.md](docs/superpowers/plans/2026-08-23-a0-device-recheck.md)
  — what the owner must look at, what is a no-regression re-run, and what is agent evidence that clears
  nothing.
- **A0.5 (second consumer) is `CODE-GREEN`; `DEVICE-ACCEPTED` is marked *not applicable*, not absent** —
  the block changes nothing on the phone, so requiring acceptance would pretend that it does (Этап 0.5's
  vocabulary). What it leaves behind, each named rather than implied absent (full record in the A0.5 ADR):
  **(1) four vocabulary findings addressed to A1′** — `ObservedFact` (a whole class of reality unsayable:
  the same "it is not there" ends `Failed` on JVM and `Completed` on Android, §6.3, held by a test and
  **not to be "fixed"** — it is a recorded owner decision), `CommandFailure`, `StepRationale`, `ArgType`
  (one value, so "typed" means "named" and a rich MCP schema is not expressible). **(2) `DURABLE_EFFECT`
  is unreachable for any tool at `CONFIRM` or above** — a branch-order property of `checkpointFor`, so the
  `DURABLE` marking is inert exactly where risk is highest. **The user is still stopped** (`RISK_LEVEL`
  takes the branch): a trace-fidelity gap, not a safety hole. → A4′. **(3) the JVM sandbox's own limits** —
  `findFile` follows symlinks when testing `isRegularFile`, so an in-sandbox link to an outside file can be
  reported under its in-sandbox path (leaks a *name*, never content; binding it into `delete_file` is
  refused); the TOCTOU window inherent to path-based containment without `O_NOFOLLOW`/dirfd; the
  unreadable-directory test yields coverage only on non-root runs. **(4) `JvmAgentSessionStore`** — its
  `FileLock` is **proved by nothing** (no single-JVM test can discriminate it; the mechanism is kept and
  the claim dropped), `SessionDto` carries no version field while an undecodable file is now deleted, so
  version skew is indistinguishable from corruption (→ A5), and `delete()` leaves an orphaned `.lock`
  (observed in the first terminal run, not merely predicted). **(5) the two consumers disagree about
  what `pausedForRestore()` records** — `ConsoleHarness` writes a pause on every restore, while
  `LauncherAgentSession.restoreOnStart` has an explicit branch against exactly that; both are defensible
  (a harness restart *is* a process death, an app start is not), so the real gap is that the engine
  never says which the event means. → A4′.
- **A1′ (federated `ToolRegistry`) is `CODE-GREEN`; `DEVICE-ACCEPTED` is a plain gap, not inapplicable** —
  unlike A0.5, this block changes what the user can reach (two new system intents, `set_timer` and
  `open_system_settings`, now reachable from a typed command), so spec §16 criterion 1 requires an owner
  device run. **A first owner device run happened 2026-09-05 and found the block's headline tool dead:**
  `set_timer` shipped documented in eight places as needing "zero new permissions", but
  `AlarmClock.ACTION_SET_TIMER` requires `com.android.alarm.permission.SET_ALARM`, which the manifest
  did not declare — so `ActivityTaskManager` refused every invocation and the step failed on every
  device. No test could see it: `Tier0IntentToolWorkerTest` injects a fake `IntentLauncher`, so the real
  `startActivity` is never reached, and a manifest omission has no unit-test signature. The permission
  is now declared and the class is held by `ToolPermissionManifestGuardTest` (`:app`), which fails when
  an intent a registered tool issues needs a permission the manifest does not declare — the mapping it
  checks against is hand-written, and that column is the new weak point, stated in the test's own KDoc.
  **An independent mutation round then measured that guard, and it holds its main directions** (a
  removed manifest declaration and an unmapped new action both go RED; legitimate complete growth stays
  GREEN; 3 of its 4 tests still fire on an empty scan; the hand-written column is exactly as weak as
  declared, no weaker) — **and it measured two further limits, neither of them a live defect today**,
  because both shipped workers build their intents inline and the manifest is correct: **(a)** an
  `Intent(…)` moved one file sideways — built in a plain helper the worker calls — is invisible to the
  *whole* guard, since its scan admits only files that both declare a `ToolWorker` and construct an
  `Intent`; **(b)** the guard keys on workers, not on registered tools, so a tool registered in a source
  with no worker branch is unseen — it answers "does every intent a scanned worker issues have a
  declared permission", not "can every registered tool run". These are limits on what the guard can
  *see*, and the person at risk is the next author: A1″'s entire content is more Tier-0 intents, where a
  shared intent-building helper is a natural thing to write. Full statement in the test's own KDoc;
  address to adapter #3 in the track plan's `§HANDOFF`.
  **`DEVICE-ACCEPTED` remains unmet**: the fix has run on the phone under agent drive only, which this
  project's status vocabulary explicitly does not count as acceptance. **The same run also falsified a
  second claim of the block — and that one is now DECIDED, not open:** the spec and the source both
  justified `set_timer`'s `SAFE` rating with "neither skips the OS's own UI — the final act is the
  user's", but with `EXTRA_SKIP_UI = false` the Samsung clock opened *with the timer already counting
  down* (Пауза/Удалить, not a start button). `EXTRA_SKIP_UI` governs whether the responding app shows
  its UI, not whether it acts. **Owner decision 2026-09-10: the level stands, the reason does not.**
  `set_timer` stays `SAFE` — no risk level and no behaviour changed — and what `SAFE` rests on is now
  stated instead of assumed: the effect is trivially reversible (one tap), immediately visible (the
  clock opens in front of the user), disclosed (`EXTERNAL` draws a provenance line, the only such
  mechanism a `SAFE` step gets), and local (nothing leaves the device). `open_system_settings` is a
  different case, not the same one: opening a settings screen performs nothing at all. The withdrawn
  sentence was corrected in `Tier0IntentToolSource`, `Tier0IntentToolWorker`, spec §8.1 + fork F3, the
  A1′ device-acceptance checklist and Master Plan §3.6 `B4` — whose "prefilled but not sent" shape no
  longer describes this tool and which A1″ must **measure** per intent rather than inherit. The
  falsification itself is kept on purpose: a rating whose stated reason was measured false is the more
  useful record.
  **A second device-found defect from the 2026-09-10 acceptance round is fixed and is `CODE-GREEN`
  only:** dismissing the agent surface («закрыть») or refusing its consent gate («Отмена») removed the
  surface and left the launcher in search-active mode with the command still in the buffer, so the home
  body (Shahada, date line, prayer strip) never came back and only a force-stop restored it.
  `LauncherViewModel.dismissAgentSession` deleted the session and did nothing else, and the refusal path
  ends `Cancelled`, which the surface draws as nothing. **Origin is A0, not A1′** (`5f04a1d`) — but A1′
  is what made it routine, since before it only a FastPath "no such app" reached that surface and now
  every recognised tool command does. Both exits now clear the command buffer, which is the whole of
  the search-active state; held by three tests in `LauncherViewModelTest` that assert the *user-visible*
  state after the exit rather than only that the row was deleted. Agent-driven on the SM-A325F for both
  plan shapes; the owner re-runs it.
  What the block leaves
  behind, each named rather than implied absent: **(1)** `DOC-ADL-1`'s closure rests on the **single call site**, not on
  `ConsentPolicyTest`'s "wake-up" test — that test cannot distinguish `risk != entries.first()` from
  `risk >= CONFIRM` while exactly three risk levels exist, and only bites the day a fourth level lands
  below `CONFIRM`. **(2)** `core/testing/src/main/java` is scanned by neither call-site guard, so a
  `ToolWorker` declared there would be invisible to `ToolWorkerCallSiteGuardTest` — owner-level, costs
  another repo-wide `Test`-input snapshot per test task (the `D1`/`D4` family). **(3)**
  `RouteCommandUseCase:147-150` still fails open to the model on **any** non-`Success` from the session
  store — disk-full, a corrupt row, any future store failure falls through silently with no session, no
  error, no trace. **Step 2b changed what that costs, and that consequence is doctrinal.** Under branch
  (2) the fall-through is benign: that branch fires only when FastPath *decided*, so the command reaches
  step (4) and the FastPath answer is returned untouched — no model call, nothing leaves the device. 2b
  fires on the opposite condition (`isUndecided()`), so the same fall-through continues to steps (5)–(7)
  and **the raw command text is sent to the cloud model** — on a goal a registered tool had already
  matched deterministically. A store failure therefore bypasses deterministic-first: not a widening of
  `OutboundContextPolicy` (the same text the model path would have received had nothing matched), but
  the decision to send it is made by a disk error rather than by the routing rules. This block found and
  fixed **one** instance of that class in review (a `GoalShape.Free` encode that threw, silently killing
  the block's own headline capability in production before the fix); the class itself is untouched,
  owned by A4′. **(4)** `DoctrineGuardTest` does not pin a registered tool's declared **risk** — a
  `SAFE → CONFIRM` change on an already-registered tool passes the guard and the whole `:app` suite
  silently. It does **not** pass unnoticed for the four tools shipped today: `Tier0IntentToolSourceTest`'s
  `none { requiresConsent(it.risk) }` catches it for both Tier-0 tools and Task 12's parity test catches
  it for the two A0 in-app tools' descriptors — both live in `:data:repository`, which is why the `:app`
  suite stays green. The real hole is a **future** adapter's tools: the assertion sits in each source's
  own test rather than in the guard, so a new source ships with no risk pin unless its author writes one. **(5)** the step-line rule
  (`PlanStep.line`) is keyed on argument **count**: a tool with two or more literal arguments falls back
  to the goal text and reproduces the duplication the rule exists to prevent — true for every tool
  shipped so far, named as a limit rather than a general property. **(6)** `"sayaç ayarla"` (the `tr`
  timer trigger) is proved reachable and un-shadowed by every guard, but reads as counter/meter rather
  than kitchen timer to a native speaker's eye — no guard can catch "reachable but nobody types it";
  unresolved, owner-level. **(7)** of A0.5's four vocabulary findings addressed to A1′:
  `ObservedFact` stays **deliberately untouched** (owner instruction 2026-08-23, see Do not);
  `CommandFailure` is rejected with a measured reason (a rich per-tool failure vocabulary was judged not
  worth its own type yet); `StepRationale.label` is **deleted**, replaced by a `PlanStep`-level line
  keyed on `ToolId` (limitation (5) above); `ArgType` stays at one value, rejected with a measured
  reason rather than deferred (owner fork F6) — a second flag-value is still categorically insufficient
  for an MCP/AppFunctions JSON Schema, and widening it would spend work at the model's input boundary
  (`ProposalValidator`) without moving A1′ toward a finished state. The A0.5 finding "consent fires
  before argument binding" (fork F5) is recorded as an address, not a decision: **A4′**. **(8)**
  exception containment is **per-worker**: `ToolWorker`'s "an invocation always yields a `ToolResult`"
  is held by convention plus three implementations, not by a mechanism — `AgentExecutor.perform`'s one
  `toolExecutor.invoke` call site still has **no `try`**, so a *future* adapter whose worker throws
  reopens the crash the final review fixed (`Tier0IntentToolWorker`'s intent seam called `startActivity`
  bare, and a typed timer command killed the home-screen process on a device with no clock app). **Not a
  live crash today** — all three shipped workers contain: the Tier-0 one catches
  `ActivityNotFoundException`/`SecurityException` (the set `AndroidActionExecutor` catches at each of its
  three `startActivity` sites), `SystemIntentToolWorker` inherits containment from that same unchanged
  action chain, and `SandboxToolWorker` catches broadly. Neither call-site guard sees it: they hold
  **where** a tool is invoked from, not **what** comes back out. The repair is the engine's → **A4′**;
  the obligation until then is **A1″**'s, which ships more workers first (argument and the adapter-#3
  warning: the A1′ ADR's residuals, and `§HANDOFF` of the track plan).
- **`CODE-GREEN`, not `DEVICE-ACCEPTED`:** DS-5's own acceptance checklist has never been run
  (since 2026-07-13); I18N-1 was verified only by agent-driven `adb`/`uiautomator` — its offline path, live
  TalkBack, fontScale 2.0, and the system per-app-language picker are untested. DS-6B is `CLOSED` (owner
  ran full on-device acceptance 2026-08-08) but carries one named residual: its MWL times are
  cross-implementation-verified only (Istanbul/Makkah are authority-table-anchored) — `CLOSED` is not a
  zero-debt claim.
- **Performance:** cold start 766 ms (`< 400 ms` was never met — aspirational, not a ship gate);
  heap 55 MB PSS steady-state Home (SM-A325F, Android 13, release, measured 2026-08-19, Этап 0.6 —
  first-ever measurement, comfortably under the old unverified 80/150/250 MB ceilings); `baselineprofile/`
  still has no `StartupTimingMetric`/`MemoryUsageMetric` — optional hardening, not done.
- **Release gate:** closed by Этап 4.0 — `checkOwnerReviewedLocaleStrings` now checks a *signature
  over content* (`OWNER-REVIEWED <date> sha256:<16 hex>` covering that file's Class B keys), so an
  edited or added key invalidates the signature. All 10 locale files carry a digest.
- **Turkish morphology:** noun-case suffixes (accusative `-ı`) are not stripped from the extracted
  app name, so `telegramı aç` may not exact-match an installed label (Этап 0.2, documented).
- **Untested matrices:** Android 9 / 11 / 14, real LOW_END hardware, on-device STT states
  (`Ready`/`Partial`, OQ#4), boot warmup after a physical reboot.
- **A0 engine, named gaps (ADR «2026-08-22 — Этап 4 (A0)»):** no **wall-clock** budget —
  `RuntimeBudget` bounds steps and consecutive failures only, and the domain is deliberately clock-free,
  so a hanging tool is bounded by nothing (A4′); a persisted `Failed` observation **loses its
  `CommandFailure` variant** and restores as `Generic`, so a resumed session reports a less specific
  failure than the one that occurred; **a resumed plan never re-checks its preconditions against the
  world** — `resumed()` continues from the persisted cursor, so an observation taken before the pause is
  acted on afterwards however stale it has become (found by Task 15's device acceptance, same A4′
  staleness family as the wall-clock gap); `GoalShape` must stay at **one** value until A4′ (Master Plan
  §3.6 `B1`).
- **A0 guards, deferred findings D1–D11** (full text in `§HANDOFF` of the track plan).
  *Owner-level* — they need the `Test` inputs block in `app/build.gradle.kts` widened, which costs
  another repo-wide snapshot per test task: **D1** the `ToolExecutor` declaration scan covers four
  roots only, so an implementation in `data/ai-cloud`, `core/android` or another `feature/*` is
  invisible to both halves of the call-site guard; **D3/D8** both scans are now wider than their
  declared input (`domain/src/commonMain/kotlin`) — harmless only while the widened region is empty,
  and whoever adds a production source set to `:domain` must declare `domain/src` **in the same
  commit** (said in both guards' KDoc). *Cheap:* **D2** the holder regex misses
  `List<ToolExecutor>`/`Map<…, ToolExecutor>`;
  **D4** `src/testFixtures` would scan as production and three call-site roots still miss
  `src/main/kotlin`; **D6** AGP's *variant* test source sets (`testDebug`, `androidTestDebug`) are still
  admitted — loud false RED, never a silent miss; **D7** an assertion recomputes the roots instead of
  checking the field it protects; **D9** the call-site guard compares file *names*, so an
  `expect`/`actual` split reads as a duplicate-scan bug; **D10** one assertion is a tautology after the
  derivation started filtering by the same predicate; **D11** (from the Task 14 review) three of
  `checkpointFor`'s four consent triggers — `RISK_RAISED`, `MISSING_PERMISSION`, `DURABLE_EFFECT` — have
  **zero** test coverage: the first is unreachable while risk has three levels, the other two because no
  test builds a `ToolDescriptor` with a gate or `DURABLE`. Fail-safe by construction, unproven by test;
  owned by whoever inserts a risk level below `CONFIRM` (A1′).
- **Not owned by any block:** `RoomColumnNames` is a hand-written inventory and its guard scans **it**,
  not the entities or the exported schema — a column added to an `@Entity` and forgotten there passes
  silently, including one with a denylisted term in its name (Block F design). It matters more since A0
  put the first **raw command text** into the database (`agent_session.goal_text`); inventory and
  schema agree today, checked against `schemas/…/4.json`. `FakeToolRegistry.withA0Tools()`'s former
  "pinned to `SystemIntentToolSource` by nothing" debt is **closed** — Task 12 (A1′) added a parity
  test comparing all eight `ToolDescriptor` fields field-for-field, mutation-proved.

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
  different port from `IntentMatcher` (→ `IntentMatchResult`) and from the structured `Planner` /
  `CommandPlanner` — those are different *shapes of answer*, and that separation is unaffected.
  LLM-proposed actions **never auto-execute a risky action** (confirmation-gated, Fork R4).
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
- Gate: `./gradlew --no-daemon :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test` —
  plus `:core:ui:verifyRoborazziDebug` whenever `core/ui` is touched, and `:app:assembleRelease` for
  release-affecting work. **`:domain:jvmTest` and `:consumer:jvm:test` must be listed explicitly:**
  `testDebugUnitTest` has not reached `:domain` since it went KMP, and it never reaches `:consumer:jvm`
  at all. Baseline at 2026-09-04 (`356fe1a`): **1304 tests, 0 failures** (434 `:domain:jvmTest` + 56
  `:consumer:jvm` + 271 `:data:repository`). A1′ *closed* at 1298 on 2026-09-03; the final-review fix
  added six tests, all in `:data:repository` (265 → 271) — four in `Tier0IntentToolWorkerTest` (16 total)
  and the two of the new `Tier0ToolExecutionEndToEndTest`. Compare a fresh gate against **1304**, not
  against the closing number quoted in the A1′ ADR.
  Read counts from the JUnit XML, not the console. **Use `--rerun-tasks`, never the plan-text `--rerun`**
  — the latter is not a valid Gradle 9.5.0 build-level flag and silently returns everything `UP-TO-DATE`
  while still printing `BUILD SUCCESSFUL` (it produced one false green inside the A1′ block); a genuine
  run prints `N actionable tasks: N executed`.
- **Never pipe `gradlew` through `tail`** — that masked a red gate as exit 0 on 2026-07-13. Check the
  exit code and read the real output.
- A stage/block is not closed until the gate is green, an ADR is written, `CLAUDE.md` +
  `ai-context/current-status.md` are synced, and a commit is **proposed to the owner**. The agent
  commits; the agent never pushes.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` | `domain` |
| `ActionId`/`ActionIds` (**frozen**), `LauncherAction`, `ActionDescriptor`, `ActionCatalog`, `ActionRiskLevel`, `requiresConsent(risk)` (the one risk → gate predicate, `DOC-ADL-1`) | `domain` |
| `CommandPlanner` + `PlanResult`/`ActionProposal`/`ProposalValidator`/`CatalogSchemaRenderer`/`RouteCommandUseCase` (incl. step 2b — a FastPath miss tried against the tool vocabulary) | `domain` |
| `ExecuteActionUseCase` (confirmed `LauncherAction` → resolve → execute → `CommandOutcome`) | `domain` |
| Tool vocabulary — `ToolId`/`ToolIds`, `ToolDescriptor`/`ToolDurability`/`ToolLevel`/`ToolLevels`/`ToolEffect` (provenance, `DOC-ILM-2`), `ToolInvocation`/`ResolvedInvocation`, `ToolOutput`, `ArgSource`, `ToolResult`/`ObservedFact`, `InvocationValidator` | `domain/tool` |
| Ports: `ToolRegistry`, `ToolExecutor` (**the only path to the world**; one call site, below the consent checkpoint), `ToolWorker` (per-adapter port, one hop below `ToolExecutor`, its own call-site guard) | `domain/tool` |
| `ToolAdapter` + `ToolFederation` (one object; `registry`/`all()`/`find()` and the sole `ToolExecutor` implementation read the same adapter list by construction; first-adapter-wins collision) | `domain/tool` |
| Agent engine — `AgentGoal`/`GoalShape`, `ExecutionPlan`/`PlanStep`, `AgentSession`/`ExecutionState`/`ConsentCheckpoint`/`RuntimeBudget`, `AgentExecutor`, `Planner`/`TemplatePlanner`, the four use cases (`Start`/`Run`/`ResolveConsent`/`Cancel`) | `domain/agent` |
| Ports: `AgentSessionStore`, `AgentSessionIdFactory` | `domain/agent` |
| `TraceEvent` / `ExecutionTrace` (no timestamps — the data layer stamps rows) | `domain/trace` |
| `PromptContextBuilder` + `OutboundContextPolicy` (outbound allow-list/guards) | `domain` |
| Pref models (`UserPreferences`, `FeatureFlags`, `CachedSuggestion`) + history models + their repos | `domain` |
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) | `domain` |
| `DeviceProfile`/`DeviceCapability` + `DeviceProfileProvider` port | `domain` |
| Suggestion + voice contracts; prayer domain (`PrayerContext`, `GetPrayerContextUseCase`) | `domain` |
| `RuleBasedIntentMatcher`, `InstalledAppsRepository` impl, `AndroidActionExecutor` | `data/repository` |
| `SystemIntentToolSource` (projects `ActionCatalog` → two `ToolDescriptor`s, level `in_app`) + `SystemIntentToolWorker` (over the **unchanged** action path; renamed from `SystemIntentToolExecutor` when the `ToolWorker` port landed) + `RoomAgentSessionStore` | `data/repository` |
| `Tier0IntentToolSource`/`Tier0IntentToolWorker` (level `system_intent`: `set_timer`, needing `com.android.alarm.permission.SET_ALARM` — `normal`, install-time; `open_system_settings`, needing none) + `ToolMatchPlanner`/`ToolVocabulary` (localized `en`/`ru`/`tr` reachability feeding `RouteCommandUseCase` step 2b) | `data/repository` |
| DataStore impls + `PreferencesMapper`/`PreferencesKeys`; Room entities/DAOs/`SidrDatabase`/migrations/mappers | `data/repository` |
| Suggestion providers + `SuggestionEngineImpl`; `SecureSecretStore` impl + `KeystoreSecretCipher` | `data/repository` |
| Cloud AI client (Ktor SSE engine, `LlmCommandPlanner`) | `data/ai-cloud` |
| Offline prayer calculation (`AdhanPrayerCalculator`), city index, schedule cache DTOs | `data/prayer` |
| `AndroidPermissionChecker`, `AndroidDeviceProfiler` + `DeviceProfileClassifier`, `AndroidSpeechInputSource`, `PackageManager` access | `core/android` |
| `UiState`, dispatchers, logging contracts; `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| Design system, theme, primitives/controls, `sidrString`/`SidrStringOverlay`, Roborazzi goldens | `core/ui` |
| `SandboxToolIds`/`SandboxToolSource`/`SandboxToolWorker` (level `sandbox`, one adapter in the same `ToolFederation`; renamed from `SandboxToolExecutor` when the `ToolWorker` port landed) + `FilePlanner` + `JvmAgentSessionStore`/`JvmAgentSessionIdFactory` + `SessionDto`/`SessionMapper` + `ConsoleHarness` | `consumer/jvm` |
| Test fakes / fixtures | `core/testing` |
| Feature UI + ViewModels + feature-local presentation mappers | `feature/*` |
| Single `NavHost`, composition root, Hilt graph, DI modules, i18n guard tests, WorkManager wiring | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |

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
| Active track plan (stages, §0 protocol, §HANDOFF) | [docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |
| Agentic target architecture A1–A6 | [docs/agentic-os-architecture.md](docs/agentic-os-architecture.md) |
| Doctrine — rules `DOC-*`, verification types, test per rule | [docs/governing/sidr-doctrine-matrix-v1.0.md](docs/governing/sidr-doctrine-matrix-v1.0.md) |
| Agentic track Master Plan — blocks A0…A6, DoD, change-control | [docs/governing/sidr-agentic-master-plan-v1.0.md](docs/governing/sidr-agentic-master-plan-v1.0.md) |
| Design system rulebook | [docs/governing/sidr-design-system-master-plan-v1.2.md](docs/governing/sidr-design-system-master-plan-v1.2.md) |
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
  **Этап 5 (A1′)** — federated `ToolRegistry` (2026-09-03)
