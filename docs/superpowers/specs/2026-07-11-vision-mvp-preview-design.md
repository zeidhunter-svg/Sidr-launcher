# Vision MVP (Preview) — Design Spec

> **Status: APPROVED (2026-07-11, owner).** A presentation-only "vision MVP": surface the approved
> soft-classic-grey artifact — including the not-yet-functional agentic and Python-terminal surfaces — as
> real, navigable **PREVIEW** screens so people can see the project's direction with their own eyes.
> Nothing here fabricates working functionality or sensitive data.
>
> **Governing sources:** `docs/design/artifacts/e34033dd/` (artifact HTML is the visual ground truth),
> DS-1..DS-4 (shipped design system), and the drafted future-track specs it previews — DS-6A/6B (sacred /
> prayer), DS-8 (activity), DS-9 (execution), DS-10 (assistant), A1–A6 (agentic architecture).

## 1. Goal

Let the launcher visually represent the full product vision now, as an MVP demo, while the engines behind
the advanced surfaces do not yet exist. Every advanced surface is a **static preview** built with the real
DS design system and clearly badged, so it reads as "designed, coming" — never as a working feature.

This is **presentation/composition only**: no new domain/data contracts, no fake runtime, no fake data.

**Owner amendment (2026-07-11): every screen matches the artifact.** Not just the new preview surfaces —
**all existing real screens** are brought into visual conformance with `docs/design/artifacts/e34033dd/`
(soft-classic-grey, DS-2/DS-3 primitives, tri-font). The App Drawer in particular is still the old
Material alphabetical list and must move to the artifact's search + icon-grid + provenance treatment. See
§5A.

## 2. Owner decisions (2026-07-11)

1. **Honesty of non-functional parts:** render everything as PREVIEW-badged mockups, **but do not render
   prayer times at all** until DS-6B ships a real source/method (religious-correctness harm — fabricated
   times could make people pray at the wrong time). The Shahada (real, static) stays.
2. **Python terminal:** ship a **Terminal preview** now (a Python-REPL-looking screen with an input but
   **no execution and no fabricated output**); the real on-device Python (e.g. Chaquopy) is a separate
   spec/plan later.
3. **Delivery:** **integrate into navigation** — a real app-level bottom tab bar surfaces the future
   screens (each PREVIEW-badged); interaction moments remain real overlays in the command flow.
4. **Tab set:** five tabs — `Home` · `Tasks◦` · `Agents◦` · `Activity◦` · `Terminal◦` (◦ = PREVIEW).
5. **Settings:** a gear icon at the very bottom of Home, immediately left of the `SIDR OS` wordmark.

## 3. Non-negotiable honesty constraints

- **No prayer times / prayer data** anywhere (DS-6B owns it). The prayer strip is not rendered.
- **No fabricated "real" data:** no fake execution traces, activity entries, agent runs, cloud steps,
  grants, aliases/facts, or Python output presented as genuine. Preview content is visibly sample/inert.
- **No fake system dialogs.** Preview screens never imitate an OS permission/consent dialog as if real.
- Every preview surface carries a persistent, unmissable **PREVIEW — not live yet** marker.
- The launcher's real, shipped behaviour (command pipeline, routing, voice, app launch, offline/router-off
  parity) is **unchanged**. `LauncherViewModelTest` and existing behaviour tests stay green.

## 4. Navigation model

### 4.1 App-level bottom tab bar

A new persistent bottom navigation (DS-styled `NavigationBar`) hosts five top-level destinations:

```
[ Home ]   [ Tasks◦ ]   [ Agents◦ ]   [ Activity◦ ]   [ Terminal◦ ]
```

- **Home** — the current real launcher home (unchanged content).
- **Tasks / Agents / Activity / Terminal** — static preview destinations (see §5).
- The bar is app-level (persists across the five tabs). Secondary destinations reached from a tab
  (App Drawer, Settings, Assistant chat, provider settings, learned choices, permission education, the
  preview detail screens) push over the current tab and are **not** part of the bar.
- Selected tab uses the DS press-invert/selected treatment; PREVIEW tabs additionally show the small badge.

**Architecture:** introduce a top-level tab host (a `Scaffold` with `bottomBar` = the tab bar) that
switches the primary content between the five roots. The existing `AppNavHost` graph is preserved for
push destinations; the tab host sits above it (or the tabs are top-level graph destinations with the bar
shown only on them). Chosen concretely in the plan; the constraint is: **one owner of the selected tab,
no `feature→feature` edge, ViewModels never touch the NavController.**

### 4.2 Home reconciliation (answers owner Q1/Q2)

- **Route chips (APP/WEB/SITE/ASK)** stay as the input's route lane, but their meaning is made legible:
  APP is the selected lane (installed-app results below), WEB searches the typed text, SITE opens a typed
  URL, ASK sends the typed text to the Assistant. They are a "where does my text go" selector, inert on a
  blank query. (No behaviour change — presentation/labeling only.)
- **Dedup:** remove the standalone bottom **Assistant** row (ASK + typing already open the assistant); also
  remove the All apps / Assistant / Settings text rows from the Home body — their roles move to: All apps →
  App Drawer affordance, Assistant → ASK/typing, Settings → the bottom gear.
- **Bottom brand line of Home:** `Local-first · on-device` on the left; on the right `[⚙] SIDR OS` — the
  Settings gear immediately left of the `SIDR OS` wordmark. The hidden dev-mode arm (7 taps) stays on
  `SIDR OS`. This line sits at the bottom of the Home tab content, above the tab bar.

## 5. Preview screens (from the artifact)

Each is a **stateless composable** (no VM, or a trivial preview-only state holder) built from DS-2/DS-3
primitives, mirroring the artifact layout, with a PREVIEW banner. No domain/data dependency.

- **Tasks◦** — the full agentic flow shown as one vertical scroll: **Intent + Plan** (goal → interpreted
  intent → numbered plan with LOCAL/WEB/CLOUD step tags, "PLAN ≠ EXECUTION — nothing runs yet") →
  **Execution** (done/running/queued steps + an inline "cloud step — needs consent" gate, all sample) →
  **Result** (deliverable + "what went where" provenance). Previews DS-9 / A4.
- **Agents◦** — a single agent card: description, Tools list, Permissions list, Pause/Open buttons. Sample
  content. Previews A1/A6.
- **Activity◦** — a journal list (prepared report / opened app / opened site / failed action) with an
  `EPHEMERAL by default, opt-in persist` provenance line. Sample entries. Previews DS-8 / A5.
- **Terminal◦** — a Python-REPL-looking surface: a `>>>` prompt with a real text input, a monospace
  transcript area that stays **empty** (no execution, no fabricated output), and a PREVIEW banner stating
  on-device Python is coming. Previews the future Python track.
- **Memory surface◦** — Learned choices (real count via existing S2-1 data is acceptable) + Aliases /
  Facts / Dismissed rows (sample) + Export / Delete-all. Previews A3 / S2-2 / DS-7.
- **App Drawer — Groups◦** — the category-grouped icon grid + `Groups / A-Z` toggle, as a preview variant
  reachable from the real (alphabetical) App Drawer; grouping is sample, not a real category engine.
- **Interaction moments** — Clarify, Action Gate (CONFIRM external / SAFE one-tap), Result
  (completed + partial), Error. The real command flow already produces Clarify and the router Action
  Gates; any missing states (Result, partial, richer Error) are added as preview compositions.

## 5A. Existing real screens — visual conformance to the artifact

Every already-shipped screen is restyled to the artifact using DS-2/DS-3 primitives. **Behaviour, VMs,
routes, and data are unchanged** — presentation only; each screen's existing tests stay green.

- **App Drawer** (`AppDrawerScreen`) — currently the old Material alphabetical **list** (`SectionHeader`,
  `SidrSearchField`, `MaterialTheme.typography` rows, `TopBarIcon`, `CircularProgressIndicator`). Migrate to
  the artifact: `SidrTopBar` + back `SidrIconButton`, a DS search field, a **4-column icon grid** of app
  tiles under `SidrAlphabetHeader` sticky headers, DS empty/loading/error, and a bottom provenance line
  (`ON-DEVICE, OFFLINE`). Add the `Groups / A-Z` toggle: **A-Z is the real alphabetical grid**; **Groups is
  a PREVIEW** category-grouped grid (sample grouping, badge) — the real category engine stays out of scope
  (§8). Live filter, launch, and "Ask assistant" affordance behaviour are preserved.
- **Assistant chat** (`AssistantScreen`) — replace raw Material (`OutlinedTextField`, `Button`,
  `headlineSmall`, `LinearProgressIndicator`) with the artifact: `SidrTopBar`, **sans prose** answer bubbles,
  **mono provenance** line disclosing `CLOUD · <provider · model>`, a DS composer row. Streaming/retry/
  refusal/error behaviour unchanged.
- **AI provider** (`AssistantProviderScreen`) — DS form: `SidrTopBar`, DS text fields / labels, a SIDR
  button for Save, DS error text. `saveProvider` behaviour and the key-never-in-state invariant unchanged.
- **Learned Choices / Memory** (`LearnedChoicesScreen`) — the artifact Memory surface: `SidrTopBar`,
  `SidrSectionHeader`s, memory cards (`✓ LEARNED LOCALLY`, evidence, `LOCAL · LAST USED …`, EDIT/FORGET),
  DS rows. Real learned-resolution data; Aliases/Facts/Dismissed are the preview part (§5).
- **Permission Education** (`PermissionEducationScreen`) — the artifact "explain before request": DS text,
  a `WITHOUT THIS PERMISSION` card, vertical SIDR buttons (Continue / Not now), `NOT A SYSTEM DIALOG ·
  YOU CHOOSE` provenance. Request-flow behaviour unchanged; never imitates a system dialog.
- **Settings** — already DS-3; verify parity with the artifact (sections by spacing, no card-per-row) and
  fix any drift only.

## 6. Preview infrastructure

- **`SidrPreviewBadge`** (new `core/ui/component`): a small, muted "PREVIEW" pill (label + marker, never
  colour-only, per DS status rules) for tab labels and inline use.
- **`SidrPreviewBanner`** (new `core/ui/component`): a full-width top banner — "PREVIEW — this screen is a
  design of what's coming; it isn't live yet." Placed at the top of every preview screen.
- Both are presentation-only, no domain/data/feature imports, and covered by the DS Roborazzi harness.

## 7. What stays real vs preview

| Surface | Status |
|---|---|
| Home (input, chips, favorites, Shahada, dates, gear, SIDR OS) | Real — DS-4 done |
| App Drawer (A-Z grid, search) | Real — **restyle to artifact** (§5A); **Groups** view is preview |
| Assistant chat, AI provider, Learned choices, Permission education | Real — **restyle to artifact** (§5A) |
| Settings | Real — DS-3 done; verify/fix drift only |
| Clarify / Action Gate CONFIRM·SAFE | Real (command flow); polished |
| Tasks / Agents / Activity / Terminal / Memory-surface / Result / partial | **Preview** |
| Prayer strip / times | **Not rendered** (DS-6B) |

## 8. Out of scope (separate tracks, later)

Real on-device Python (Chaquopy), real agent runtime (A4), real activity trace (A5), real user
memory / aliases / facts (A3 / S2-2 / DS-7), real App Drawer category engine, prayer-time correctness
(DS-6B), the full Sacred Header (DS-6A). No new preference keys, no new outbound allow-list entries, no
new domain/data contracts.

## 9. Verification

- **Unit/semantics:** `SidrPreviewBadge`/`SidrPreviewBanner` semantics; tab-host selects one tab; each
  preview screen has a11y heading + the PREVIEW marker read by TalkBack.
- **Screenshot (Roborazzi):** badge/banner and each preview screen (dark/light/font-scale-2.0); Home with
  the tab bar + bottom gear/SIDR OS line.
- **Parity:** existing `LauncherViewModelTest`, `AppDrawerViewModelTest`, `SettingsViewModelTest`,
  `AssistantViewModelTest` pass unchanged. No privacy allow-list change.
- **Device (SM-A325F):** tab bar switches between the five tabs; every preview shows its banner and no
  fabricated data; no prayer times anywhere; Terminal input accepts text but never "runs"; Home real flow
  (type/launch/WEB/SITE/ASK/router-off parity) unchanged; Settings gear opens Settings; no crash.

## 10. Success criteria

- A person can open the app and, through the real bottom navigation, **see the whole product vision** —
  tasks, agents, activity, terminal — rendered in the approved soft-classic-grey identity.
- Every not-yet-real surface is unmistakably marked PREVIEW; nothing fabricates working behaviour or
  sensitive (prayer) data.
- The shipped launcher behaviour and its parity are untouched; the advanced tracks (Python, A4/A5, DS-6B,
  etc.) can later replace each preview in place without another navigation rewrite.
