# Vision MVP (Preview) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Bring every launcher screen into visual conformance with the approved soft-classic-grey artifact,
and surface the not-yet-functional agentic + Python-terminal surfaces as clearly-badged PREVIEW screens in a
real bottom tab bar — a navigable "vision MVP".

**Architecture:** Presentation/composition only. New `core/ui` preview primitives (`SidrPreviewBadge`,
`SidrPreviewBanner`); an app-level 5-tab bottom bar hosting Home (real) + four static preview screens; each
existing real screen restyled to the artifact with DS-2/DS-3 primitives. No new domain/data contracts, no
fake runtime, no fake data, no prayer times.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation-Compose, existing `core/ui` DS (`SidrTheme`,
DS-2 primitives, DS-3 controls), Roborazzi screenshot harness.

**Spec:** `docs/superpowers/specs/2026-07-11-vision-mvp-preview-design.md`.

## Global Constraints

- Build with JDK 17: `export JAVA_HOME=/home/Suleiman/jdks/jdk-17.0.19+10` (system JDK is 25; Gradle 8.10.2
  can't parse it). Device for acceptance: SM-A325F (adb serial `RF8R705H38F`).
- **Presentation only.** No change to any ViewModel contract, route semantics (except the additive tab
  destinations), domain, data, or persistence. Existing behaviour tests (`LauncherViewModelTest`,
  `AppDrawerViewModelTest`, `SettingsViewModelTest`, `AssistantViewModelTest`) pass **unchanged**.
- **Honesty (hard):** no prayer times/data anywhere; no fabricated execution/activity/agent/alias/fact data
  or Python output presented as real; no fake system dialogs; every preview surface shows a persistent
  `PREVIEW — not live yet` marker.
- No new preference keys; no new outbound allow-list entries; no `feature→feature` dependency; ViewModels
  never touch the `NavController`.
- `core/ui/component` and `core/ui/primitive` must not import `domain`, `data`, or feature packages
  (`ControlsDependencyGuardTest` enforces this — new preview primitives are covered by it).
- Roborazzi: after an intentional visual change run `:core:ui:recordRoborazziDebug` then
  `:core:ui:verifyRoborazziDebug`. No commits unless the owner explicitly asks.

---

## Reference: artifact → repo token map (already in the codebase)

`SidrTheme.colors`: `ground/surface/raised/line/border/text/sacred/dim/faint/accent/accentBorder/
success/attention/caution/danger/info` — match the artifact `.scr` CSS byte-for-byte.
`SidrShapes`: `small`=7dp (chips), `medium`=10dp (input/tiles), `large`=12dp (modals).
`SidrTextRole`: `COMMAND`(mono) `SYSTEM`(mono label) `PROVENANCE`(mono small) `SACRED`(serif)
`HUMAN_BODY`(sans) `HUMAN_TITLE`(sans) `LABEL`. DS-3: `SidrTopBar`, `SidrIconButton`, `SidrSectionHeader`,
`SidrAlphabetHeader`, `SidrNavigationRow`, `SidrToggleRow`, `SidrRouteChip`/`SidrFilterChip`,
`SidrPrimaryButton`/`SidrSecondaryButton`/`SidrTerminalAction`, `SidrActionGate`, `SidrRiskChip`,
`SidrStatusChip`, `SidrSurface`, `SidrDivider`, `SidrProvenanceLine`.

---

## Task 1: Preview primitives (`SidrPreviewBadge` + `SidrPreviewBanner`)

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrPreview.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrPreviewTest.kt`
- Modify: `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ControlGallery.kt` (add a preview
  section), `ControlsScreenshotTest.kt` (goldens)

**Interfaces:**
- Produces: `@Composable fun SidrPreviewBadge(modifier: Modifier = Modifier)` — a small muted pill,
  label `PREVIEW`, mono `SYSTEM` role, `border` hairline, never colour-only.
  `@Composable fun SidrPreviewBanner(text: String = "PREVIEW — this screen is a design of what's coming; it isn't live yet.", modifier: Modifier = Modifier)` — full-width top banner using `SidrSurface(tone = RAISED)` + `SidrText(PROVENANCE)`, marked as an a11y heading.

- [ ] **Step 1: Write the failing semantics test**

```kotlin
package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun badge_shows_preview_label() {
        compose.setContent { SidrTheme(darkTheme = true) { SidrPreviewBadge() } }
        compose.onNodeWithText("PREVIEW").assertIsDisplayed()
    }

    @Test fun banner_shows_not_live_copy() {
        compose.setContent { SidrTheme(darkTheme = true) { SidrPreviewBanner() } }
        compose.onNodeWithText("PREVIEW — this screen is a design of what's coming; it isn't live yet.")
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `export JAVA_HOME=/home/Suleiman/jdks/jdk-17.0.19+10 && ./gradlew --no-daemon :core:ui:testDebugUnitTest --tests '*SidrPreviewTest*'`
Expected: FAIL — `SidrPreviewBadge`/`SidrPreviewBanner` unresolved.

- [ ] **Step 3: Implement `SidrPreview.kt`**

Compose `SidrPreviewBadge` as a `Box` with `SidrShapes.small`, `border(Strokes.hairline, colors.border)`,
padding `Spacing.sm`, containing `SidrText("PREVIEW", role = SidrTextRole.SYSTEM, color = colors.attention)`
(the `attention` status token — a fixed status colour, never the accent; label carries the meaning, colour
is secondary). `SidrPreviewBanner`: `SidrSurface(tone = RAISED, shape = SidrShapes.medium, modifier =
fillMaxWidth().padding(Spacing.lg))` wrapping `SidrText(text, role = PROVENANCE, color = colors.attention,
modifier = Modifier.padding(Spacing.md).semantics { heading() })`. No domain/data/feature imports.

- [ ] **Step 4: Run test, verify pass** — `./gradlew --no-daemon :core:ui:testDebugUnitTest --tests '*SidrPreviewTest*'` → PASS.

- [ ] **Step 5: Add gallery + goldens** — add a `PREVIEW` section to `ControlGallery.kt` (badge + banner,
  dark/light/font-scale-2.0); add `@Test fun preview_controls()` capture in `ControlsScreenshotTest.kt`.
  Run `./gradlew --no-daemon :core:ui:recordRoborazziDebug` then `:core:ui:verifyRoborazziDebug` → PASS.

- [ ] **Step 6: Commit (only if owner asked)** — `git add core/ui/src/... && git commit -m "feat(ui): SidrPreviewBadge + SidrPreviewBanner"`

---

## Task 2: App Drawer → artifact (icon grid + DS + Groups/A-Z toggle)

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/AppDrawerScreen.kt` (full
  presentation rewrite; VM untouched)
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/AppDrawerViewModelTest.kt` (must
  still pass unchanged)

**Interfaces:**
- Consumes: `AppDrawerViewModel` (`uiState: StateFlow<UiState<AppDrawerUiState>>` with
  `data.sections: List<DrawerSection>` (`letter`, `apps`), `query`, `onQueryChanged`, `onQuerySubmitted`,
  `onAppClicked`, `navigateTo`), `rememberAppIcon`, `AppTile`, `SidrPreviewBadge` (Task 1).
- Produces: nothing new consumed downstream.

- [ ] **Step 1: Replace the top bar + search** — swap the hand-rolled `Row`/`TopBarIcon`/`Text("All apps")`
  for `SidrTopBar(title = "All apps", navigationIcon = { SidrIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) }, actions = { GroupsAzToggle(...) })`, and replace `SidrSearchField` with a DS search
  row: `SidrSurface(tone = SURFACE, shape = SidrShapes.medium)` wrapping a `BasicTextField` styled like
  `SidrUniversalInput`'s field (placeholder `Search applications`, `SidrTextRole.HUMAN_BODY`, mono via
  `SidrText`), plus a clear affordance. Keep `onQueryChanged`/`onQuerySubmitted` wiring verbatim.

- [ ] **Step 2: Replace the list with a 4-column icon grid** — new private `DrawerGrid(sections, onAppClick)`:
  a `LazyColumn` where each `DrawerSection` emits a `stickyHeader { SidrAlphabetHeader(section.letter) }`
  followed by rows built by `section.apps.chunked(4)` → `Row { each app → Box(weight 1f, center) { AppTile(app.label, { onAppClick(app) }, icon = { DrawerAppIcon(app) }) } ; pad remaining cells with Spacer(weight 1f) }`.
  Keep `DrawerAppIcon` (already present). Preserve the "Ask assistant" affordance (query non-blank) using a
  DS `SidrTerminalAction`/text button.

- [ ] **Step 3: Add the `Groups / A-Z` toggle (A-Z real, Groups PREVIEW)** — a screen-local
  `var mode by remember { mutableStateOf(DrawerMode.AZ) }`; `GroupsAzToggle` = two `SidrFilterChip`s
  (`Groups` selected → `Groups`, `A-Z` → `Az`). When `mode == Az`: render `DrawerGrid(state.sections, …)`
  (the real alphabetical grid). When `mode == Groups`: render a **preview** grouped grid built from a local
  sample category map over the same installed apps (e.g. bucket by first letter into "Finance/Messaging/
  Media/Productivity/Other" is NOT real categorisation — instead show fixed sample category headers with the
  real app tiles distributed round-robin) **with a `SidrPreviewBanner` at the top** and a
  `SidrPreviewBadge` by the `Groups` chip. No real category engine. Add a bottom provenance line
  `SidrText("ON-DEVICE · OFFLINE", role = PROVENANCE)`.

- [ ] **Step 4: Empty/Loading/Error via DS** — `EmptyState`/`ErrorState` already DS; keep. Replace the raw
  `CircularProgressIndicator` with `SidrProgress` (DS-2) centered.

- [ ] **Step 5: Verify behaviour parity** — Run
  `./gradlew --no-daemon :feature:launcher:testDebugUnitTest` → `AppDrawerViewModelTest` PASS unchanged
  (filter, sections, launch, ask-assistant route). Fix compile drift only.

- [ ] **Step 6: Device smoke** — install, open All apps, confirm: DS grid + search, sticky letters,
  A-Z↔Groups toggle (Groups shows banner+badge, no real categories claimed), launch works, filter works.

- [ ] **Step 7: Commit (if asked)** — `git commit -m "feat(ui): App Drawer to artifact grid + Groups/A-Z preview"`

---

## Task 3: Assistant chat → artifact

**Files:** Modify `feature/assistant/src/main/java/com/sidr/launcher/feature/assistant/AssistantScreen.kt`
(the `AssistantScreen` + `ChatView` composables only; VM untouched). Test:
`feature/assistant/.../AssistantViewModelTest.kt` (unchanged).

**Interfaces:** Consumes `AssistantViewModel` (`uiState`, `send`, `retry`, `navigateBack`,
`openProviderSettings`), `AssistantUiState`/`AssistantStatus`.

- [ ] **Step 1:** Replace the header `Row`/`IconButton`/`Text("Assistant")` with `SidrTopBar(title = "Assistant", navigationIcon = { SidrIconButton(ArrowBack, "Back", viewModel::navigateBack) })`.
- [ ] **Step 2:** Answer text → `SidrText(role = HUMAN_BODY)` (sans prose) inside a left-aligned bubble
  (`SidrSurface(tone = SURFACE, shape = medium)` for user echoes if shown; assistant prose transparent per
  artifact). Streaming indicator → `SidrProgress`. Refusal/"declined" → `SidrText(PROVENANCE)`.
- [ ] **Step 3:** Under the answer, a mono provenance line `SidrText("CLOUD · <provider · model>", role = PROVENANCE)` when a config is present (derive display text from `uiState.form.baseUrl` host + `modelId`;
  presentation only — no key). Error CTA `SidrSecondaryButton("Fix provider settings", viewModel::openProviderSettings)`; retry `SidrPrimaryButton`.
- [ ] **Step 4:** Composer row → `SidrSurface(tone = SURFACE, shape = medium)` wrapping the `BasicTextField`
  (placeholder `Message`, `HUMAN_BODY`) + a send `SidrIconButton`/`SidrTerminalAction`. Preserve send/enabled
  logic verbatim.
- [ ] **Step 5:** Run `./gradlew --no-daemon :feature:assistant:testDebugUnitTest` → PASS unchanged.
- [ ] **Step 6:** Device smoke (needs owner BYOK key): open Assistant, confirm DS look, streaming still
  works, provenance shows provider·model, no "Edit provider". Commit (if asked).

---

## Task 4: AI provider screen → artifact DS form

**Files:** Modify `AssistantScreen.kt` (`AssistantProviderScreen` + `ProviderSettingsForm`). Test:
`AssistantViewModelTest` (unchanged — `saveProvider`).

- [ ] **Step 1:** `AssistantProviderScreen` header → `SidrTopBar(title = "AI provider", navigationIcon = back)`.
- [ ] **Step 2:** In `ProviderSettingsForm`, replace `OutlinedTextField`s with DS field composition
  (`SidrSurface(tone = SURFACE, shape = medium)` + `BasicTextField` + `SidrText` label via
  `SidrSectionHeader`/`PROVENANCE`), keep `PasswordVisualTransformation` on the key, keep the
  `key set — replace to update` label logic. Replace `Button("Save")` with `SidrPrimaryButton("Save", enabled = baseUrl.isNotBlank() && model.isNotBlank())`. Error text → `SidrText(PROVENANCE, color = danger)`.
- [ ] **Step 3:** `./gradlew --no-daemon :feature:assistant:testDebugUnitTest` → PASS. Key-never-in-state
  invariant unchanged (no key in any `remember`/state that survives). Device: Settings → AI provider shows
  DS form, Save persists. Commit (if asked).

---

## Task 5: Learned Choices / Memory → artifact Memory surface

**Files:** Modify `feature/settings/src/main/java/com/sidr/launcher/feature/settings/LearnedChoicesScreen.kt`.
Test: its existing tests (unchanged).

- [ ] **Step 1:** Read the current file first (`Read LearnedChoicesScreen.kt`) to learn its state/callbacks.
- [ ] **Step 2:** Header → `SidrTopBar(title = "Memory", navigationIcon = back)`.
- [ ] **Step 3:** "Learned choices" section (real data) → `SidrSectionHeader("LEARNED CHOICES")` + per item a
  memory card: `SidrSurface(tone = SURFACE, shape = medium)` with `SidrText(HUMAN_BODY)` "\"<phrase>\" →
  <app>", a `SidrText(PROVENANCE)` evidence/last-used line, and a `SidrDestructiveRow`/`SidrTerminalAction`
  "Forget". Keep the real delete/relearn callbacks.
- [ ] **Step 4:** Add a **preview** block below (`SidrPreviewBanner` + `SidrSectionHeader("ALIASES")`,
  `FACTS`, `DISMISSED` with sample rows + `SidrPreviewBadge`) — clearly not real (A3/S2-2/DS-7). Add
  `Export` / `Delete all` as `SidrSecondaryButton`/`SidrDestructiveButton` (Delete-all wired to the real
  learned-choices clear if it exists; otherwise leave the preview block's controls inert with the badge).
- [ ] **Step 5:** Run the settings module tests → PASS. Device: Settings → Learned choices shows the DS
  memory surface; real learned items behave; preview blocks are badged. Commit (if asked).

---

## Task 6: Permission Education → artifact

**Files:** Modify `feature/permission_education/src/main/java/com/sidr/launcher/feature/permission_education/PermissionEducationScreen.kt`.

- [ ] **Step 1:** Read the file first for its state/callbacks (feature, rationale, request/allow, `onBack`).
- [ ] **Step 2:** Header → `SidrTopBar`. Rationale prose → `SidrText(HUMAN_BODY)`. Add a
  `SidrSurface(tone = SURFACE)` "WITHOUT THIS PERMISSION" card (`SidrSectionHeader` + `SidrText`).
- [ ] **Step 3:** Buttons vertical: `SidrPrimaryButton("Continue", onRequest)` + `SidrSecondaryButton("Not now", onBack)`. Add `SidrText("NOT A SYSTEM DIALOG · YOU CHOOSE", role = PROVENANCE)` centered. Never
  imitate a system dialog; request-flow callbacks unchanged.
- [ ] **Step 4:** Build + module tests PASS. Device: trigger via mic-without-permission; confirm DS look +
  behaviour. Commit (if asked).

---

## Task 7: App-level bottom tab bar + Home reconciliation

**Files:**
- Create: `app/src/main/java/com/sidr/launcher/navigation/SidrTabScaffold.kt` (the 5-tab host)
- Modify: `app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt` (host the tabs at the Launcher
  root), `feature/launcher/.../LauncherScreen.kt` (Home reconciliation), `core/common/.../Routes.kt` (add
  `Tasks`/`Agents`/`Activity`/`Terminal` preview routes)
- Test: `feature/launcher/.../LauncherViewModelTest.kt` (unchanged)

**Interfaces:**
- Produces: `@Composable fun SidrTabBar(selected: SidrTab, onSelect: (SidrTab) -> Unit)` and
  `enum class SidrTab { HOME, TASKS, AGENTS, ACTIVITY, TERMINAL }` (in `SidrTabScaffold.kt`), plus
  `Routes.Tasks/Agents/Activity/Terminal` (`ROUTE` strings).

- [ ] **Step 1:** Add preview routes to `Routes.kt`: `object Tasks/Agents/Activity/Terminal : Routes() { const val ROUTE = "preview_tasks" /* etc */ }`.
- [ ] **Step 2:** Create `SidrTabScaffold.kt`: `SidrTab` enum + `SidrTabBar` using M3 `NavigationBar`
  themed via `SidrTheme` (labels `Home/Tasks/Agents/Activity/Terminal`; the four preview tabs also show a
  tiny `SidrPreviewBadge` or a `◦` mono marker in the label). Selected uses DS press-invert colours.
- [ ] **Step 3:** In `AppNavHost`, wrap the top-level tab destinations so the bar is shown only on the five
  tab roots and hidden on pushed destinations (App Drawer, Settings, Assistant, provider, learned choices,
  permission education). Concretely: render the `SidrTabBar` in a `Scaffold(bottomBar = …)` around the
  Launcher/preview roots; tapping a tab swaps the root content; pushes happen inside the Home root's own
  nav. Keep `handleNavigationEvent` + safe-fallback for pushes. Register `composable(Routes.Tasks.ROUTE){ TasksPreviewScreen() }` etc. (screens land in Tasks 8–11 as empty stubs first, filled next).
- [ ] **Step 4: Home reconciliation** in `LauncherScreen.kt`:
  - Remove `HomeBottomNav` (All apps/Assistant/Settings rows).
  - Add a compact **All apps** affordance to Home body (a single `SidrNavigationRow("All apps", → AppDrawer)`
    kept, since the drawer is not a tab).
  - Rework `HomePrivacyLine` into a `Row`: `SidrText("Local-first · on-device", weight 1f)` |
    `SidrIconButton(Icons.Filled.Settings, "Settings", → Routes.Settings.ROUTE)` | `SidrText("SIDR OS", PROVENANCE)` with the 7-tap dev-arm — gear immediately left of `SIDR OS`.
- [ ] **Step 5:** `./gradlew --no-daemon :feature:launcher:testDebugUnitTest testDebugUnitTest :app:assembleDebug` → PASS/BUILD SUCCESSFUL.
- [ ] **Step 6:** Device: tab bar shows 5 tabs, Home tab is the real home with gear left of SIDR OS, All apps
  reachable, preview tabs switch (empty stubs OK for now). Commit (if asked).

---

## Task 8: Tasks preview screen

**Files:** Create `feature/launcher/.../preview/TasksPreviewScreen.kt` (or a new `feature/preview` module if
the owner prefers; default: keep preview screens in `feature/launcher` to avoid a new module + `feature→feature`
edges). Test: a semantics test asserting the PREVIEW banner + "PLAN ≠ EXECUTION" copy.

- [ ] **Step 1:** Write `TasksPreviewScreen()` — a `verticalScroll` `Column` with `SidrPreviewBanner()` at
  top, then three artifact sections built from `SidrSurface`/`SidrText`/`SidrSectionHeader`/`SidrStatusChip`:
  **Intent + Plan** (goal echo `SidrText(COMMAND)`, interpreted intent `HUMAN_BODY`, numbered plan rows with
  LOCAL/WEB/CLOUD `SidrStatusChip`s, `SidrText("PLAN ≠ EXECUTION · nothing runs yet", PROVENANCE)`),
  **Execution** (done/running/queued step rows + one `SidrActionGate`-styled "cloud step — needs consent"
  sample, non-functional), **Result** (deliverable bullets + "what went where" provenance). All sample text;
  no callbacks that claim to run anything.
- [ ] **Step 2:** Semantics test: banner heading present + "PLAN ≠ EXECUTION" text displayed. Run the
  launcher module tests → PASS.
- [ ] **Step 3:** Wire `composable(Routes.Tasks.ROUTE){ TasksPreviewScreen() }` (replace the Task-7 stub).
  Roborazzi golden (dark/light) if a preview harness exists in the module; else device screenshot only.
  Device: Tasks tab shows the flow with the banner. Commit (if asked).

---

## Task 9: Agents preview screen

**Files:** Create `feature/launcher/.../preview/AgentsPreviewScreen.kt`.

- [ ] **Step 1:** `AgentsPreviewScreen()` — `SidrPreviewBanner()` + an agent card: `SidrTopBar`-less title
  `SidrText("Research agent", HUMAN_TITLE)` + `SidrStatusChip("Active", SUCCESS)`, a description
  `SidrText(HUMAN_BODY)`, `SidrSectionHeader("TOOLS")` list (Web search / Document reader) and
  `SidrSectionHeader("PERMISSIONS")` list (Network / Temporary context) as `SidrNavigationRow`-style rows
  (inert), `SidrSecondaryButton("Pause")` + `SidrPrimaryButton("Open")` (inert). Sample only.
- [ ] **Step 2:** Wire `composable(Routes.Agents.ROUTE)`. Device: Agents tab shows the card + banner.
  Commit (if asked).

---

## Task 10: Activity preview screen

**Files:** Create `feature/launcher/.../preview/ActivityPreviewScreen.kt`.

- [ ] **Step 1:** `ActivityPreviewScreen()` — `SidrPreviewBanner()` + a `SidrSectionHeader("ACTIVITY")` +
  sample timeline rows (`SidrStatusMarker` + `SidrText`): "Prepared Ktor report · 4 steps · 14:40",
  "Opened Türkiye Finans · learned · LOCAL", "Opened GitHub · confirmed external", "Web action failed · no
  browser" (danger marker), ending `SidrText("EPHEMERAL BY DEFAULT · OPT-IN PERSIST", PROVENANCE)`. Sample.
- [ ] **Step 2:** Wire `composable(Routes.Activity.ROUTE)`. Device check. Commit (if asked).

---

## Task 11: Terminal preview screen (Python REPL look, no execution)

**Files:** Create `feature/launcher/.../preview/TerminalPreviewScreen.kt`. Test: a semantics test asserting
that entering text and pressing enter produces **no output row** (guards "no fabricated execution").

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun terminal_never_produces_output() {
    compose.setContent { SidrTheme(darkTheme = true) { TerminalPreviewScreen() } }
    compose.onNodeWithText("PREVIEW — this screen is a design of what's coming; it isn't live yet.")
        .assertIsDisplayed()
    // typing + submit must NOT add any transcript output (no fake REPL result)
    compose.onNodeWithText(">>>").assertIsDisplayed()
    // (no assertion of any computed output — there must be none)
}
```

- [ ] **Step 2:** Run it, verify it fails (screen unresolved).
- [ ] **Step 3:** Implement `TerminalPreviewScreen()` — `SidrPreviewBanner("PREVIEW — on-device Python is coming; this terminal doesn't run yet.")` + a monospace transcript `Column` that stays **empty**, and a
  bottom prompt row `SidrText(">>>", role = COMMAND, color = accent)` + a `BasicTextField` (mono). On submit:
  clear the field only — **never append output**. No interpreter, no sample results.
- [ ] **Step 4:** Run test → PASS. Wire `composable(Routes.Terminal.ROUTE)`.
- [ ] **Step 5:** Device: Terminal tab shows REPL look + banner; typing + enter yields no output. Commit (if asked).

---

## Task 12: Interaction-moment previews (Result / partial / Error)

**Files:** Create `feature/launcher/.../preview/MomentsPreviewScreen.kt` reachable from the Tasks tab footer
or a small "Interaction moments" `SidrNavigationRow` inside Agents/Tasks preview (owner-visible, not in daily
flow). Clarify + Action Gate CONFIRM/SAFE already exist in the real command flow (leave them).

- [ ] **Step 1:** Build static previews for **Result (completed)** (`✓ COMPLETED` `SidrStatusChip`, "Opened
  <app>", `LOCAL · 14 MS` provenance, "Change" button), **Partial** (`! PARTIALLY COMPLETED`, "route
  prepared; reminder not created — permission off", Enable/View), and **Error** (`✕ ACTION FAILED`, "app
  isn't installed", "Open Play Store"/"Choose another" buttons). All sample; buttons inert.
- [ ] **Step 2:** Link from a preview screen; device check. Commit (if asked).

---

## Task 13: Full gate + device acceptance + docs

- [ ] **Step 1:** Re-record + verify goldens: `./gradlew --no-daemon :core:ui:recordRoborazziDebug :core:ui:verifyRoborazziDebug`.
- [ ] **Step 2:** Full gate: `./gradlew --no-daemon :core:ui:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:assistant:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest testDebugUnitTest assembleDebug` → all green.
- [ ] **Step 3:** Device acceptance (SM-A325F): every screen matches the artifact; 5-tab bar switches;
  every preview shows its banner and no fabricated data; **no prayer times anywhere**; Terminal never runs;
  App Drawer grid + Groups/A-Z; Home real flow (type/launch/WEB/SITE/ASK/router-off parity) unchanged;
  Settings gear (bottom-left of SIDR OS) opens Settings; Assistant clean; no crash.
- [ ] **Step 4:** Docs: add an ADR to `ai-context/decisions.md` ("2026-07-11 — Vision MVP preview + all
  screens to artifact"), update `ai-context/current-status.md` and `CLAUDE.md` DS line, and note in
  `docs/design/artifacts/e34033dd/README.md` that the app now renders the artifact set (previews badged).
- [ ] **Step 5:** Commit (only if owner asks).

---

## Self-Review

- **Spec coverage:** §2 owner decisions → Tasks 7 (tabs/gear), 11 (terminal preview), 1 (badge), all preview
  tasks (prayer omitted everywhere — no task renders it). §3 honesty → Task 11 test + banners in 8–12. §4
  nav → Task 7. §5 preview screens → Tasks 8–12 + App-Drawer-Groups in Task 2. §5A existing screens → Tasks
  2–6. §6 infra → Task 1. §9 verification → Task 13. No spec section is uncovered.
- **Placeholder scan:** no TBD/TODO; each task names exact files, DS components, and verification commands.
  Screen restyles reference concrete DS-3/DS-4 components already in the repo rather than re-pasting their
  source (established pattern) — acceptable given the executor has the reference token map above.
- **Type consistency:** `SidrPreviewBadge`/`SidrPreviewBanner`, `SidrTab`/`SidrTabBar`, and
  `Routes.Tasks/Agents/Activity/Terminal` names are used identically across Tasks 1, 7–12.

## Stop conditions

Stop and re-scope if: a screen restyle needs a ViewModel/route/persistence change; any preview would require
fabricating real-looking prayer/execution/activity/agent data; a new `feature→feature` edge is needed; or the
tab host cannot hide the bar on pushed destinations presentation-only.
