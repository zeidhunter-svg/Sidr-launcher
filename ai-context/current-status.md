# Current Status

> **Authoritative status lives in `CLAUDE.md` (session digest), `ai-context/decisions.md` (ADR log),
> and the per-phase plans.** This file is a short pointer/snapshot only — if it disagrees with those,
> they win. Last re-based: 2026-08-08 (DS-6B Prayer Correctness COMPLETE — device-accepted by owner; prior
> re-base 2026-07-13 DS-5 + auto-hide nav + accent reactivation code-closed).

## Design track (DS) — DS-1…DS-4 + Vision MVP DONE; DS-5 CODE-CLOSED (2026-07-13, device-pending);
DS-6B COMPLETE — device-accepted by owner (2026-08-08)

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
testDebugUnitTest assembleDebug` — BUILD SUCCESSFUL. **Device status is PARTIAL**: on SM-A325F the
no-data invariant passed (fresh install → calm Home, no strip/prompt/fabricated times) and a real
Diyanet/Turkey setup rendered a correct-looking strip, but the full interactive + religious-correctness
acceptance (authority-table cross-check, airplane-mode cache, stale path, device-location grant,
tz-conflict, font-scale 2.0, TalkBack, router-off parity) is **PENDING the owner** — mirrors the DS-5
device-pending precedent. **Known limitation:** Istanbul (Diyanet) and Makkah (Umm al-Qura) golden times
are authority-table-anchored; London and Kazan (MWL) are cross-implementation-verified only (MWL has no
official authority portal). DS-6A (the Shahada component) stays UI-only and separate from DS-6B. ADR:
decisions.md "2026-08-08 — DS-6B Prayer Correctness (COMPLETE — device-accepted by owner)".

**DS-5 Action & Safety is CODE-CLOSED (implemented 2026-07-12, reconciled + gated 2026-07-13);
device acceptance PENDING.** `core/ui/component/SidrActionSafety.kt` (`SidrActionProposal`,
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
by `docs/design/SIDR Design System Master Plan v1.2` (§5.1 precedence, §5.2 verify-vocab, §20.1 calm budgets
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
complete". **DS-5 Action & Safety is CODE-CLOSED (2026-07-13, see the section above; device-pending)**:
`docs/superpowers/specs/2026-07-11-ds5-action-safety-design.md` +
`docs/superpowers/plans/2026-07-11-ds5-action-safety.md`. **Next design block: DS-7 Memory Surfaces
(recommended — its DS-5 dependency is now met and it unblocks the paused S2-2 Explicit Aliases UI),
after DS-5 device acceptance.** **DS-6A Sacred Header is drafted but gated after
DS-5, DS-4 Home integration, and Arabic rendering approval**:
`docs/superpowers/specs/2026-07-11-ds6a-sacred-header-design.md` +
`docs/superpowers/plans/2026-07-11-ds6a-sacred-header.md`. **DS-6B Prayer Correctness is now drafted as
the separate religious-correctness capability track**:
`docs/superpowers/specs/2026-07-11-ds6b-prayer-correctness-design.md` +
`docs/superpowers/plans/2026-07-11-ds6b-prayer-correctness.md`; it requires authority/method/privacy
requirements before code and forbids fake prayer times or precise-location leakage into AI/cloud. **DS-7 Memory Surfaces is drafted as the
Learned Choices migration + conditional Aliases path**:
`docs/superpowers/specs/2026-07-11-ds7-memory-surfaces-design.md` +
`docs/superpowers/plans/2026-07-11-ds7-memory-surfaces.md`; production work should wait for DS-3/DS-5
controls/safety to land and for S2-2 scope before shipping Aliases UI. **DS-8 Activity Foundations, DS-9
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
**Next Stage-2 slice (S2-2) not yet scoped — brainstorm before planning.**

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
after reboot (C.5).
*(Real assistant streaming against a live provider — done Round 3.)*

## Source of truth

- Session digest + hard rules: `CLAUDE.md`
- Decisions log (latest: ADR 2026-07-11 — S2-1 Learned Resolutions device accepted + closed): `ai-context/decisions.md`
- Architecture (in sync): `docs/architecture.md` · Roadmap: `docs/roadmap.md`
- Active/last plan: `docs/superpowers/plans/2026-07-06-learned-resolutions.md` (S2-1, CLOSED)
