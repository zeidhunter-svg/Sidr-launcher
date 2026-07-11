# DS-4 — Home Shell and Universal Input Implementation Plan

> **STATUS: DONE (2026-07-11).** Home migrated to the approved soft-classic-grey intent-first layout and
> `SidrCommandPrompt` replaced by `SidrUniversalInput`, with the command pipeline / routing / voice / offline
> / router-off parity preserved (`LauncherViewModelTest` unchanged). Full gate green; **device-accepted on
> SM-A325F** (typed launch, WEB/SITE/ASK routes, router-off "Unknown command" parity, Settings/All apps/
> Assistant discoverability, Gregorian+Hijri date, empty sacred seam, no crash). Owner decisions applied:
> empty sacred anchor seam, Gregorian+Hijri date, retire+redistribute CommandBar. ADR: decisions.md
> "2026-07-11 — DS-4 complete". Next: DS-5 (Action & Safety).

**Goal:** Recompose Home around the intent-first layout and migrate Universal Input presentation while
preserving existing launcher behaviour.

**Spec:** `docs/superpowers/specs/2026-07-11-ds4-home-universal-input-design.md`.

## Prerequisites

- DS-3 Controls implemented and green:
  - SIDR buttons/chips/rows/top bar available;
  - `SidrActionGate` available or its migration explicitly deferred;
  - `RouteChipRow` either migrated to DS-3 chips or kept intentionally as legacy until this block.
- DS-1/DS-2 Roborazzi harness still green.
- Artifact captures available under `docs/design/artifacts/e34033dd/`.

Do not start DS-4 implementation if DS-3 is only scoped but not implemented, unless the owner explicitly
splits off a prototype branch.

## Global Constraints

- No `LauncherViewModel` contract rewrite.
- No domain/data/persistence changes.
- No new preference keys.
- No prayer calculation, location, authority/method, or fake prayer times.
- No Activity, Agents, Execution Stream, bottom nav, or task-flow UI.
- No fake recent history if no real data backs it.
- No Assistant pipeline merge.
- No App Drawer categorization engine.
- No removal of legacy `SidrCommandPrompt` until all usages and tests are migrated.
- No commits unless the owner explicitly asks.

## Task 1: Baseline and Parity Inventory

**Purpose:** Lock down what DS-4 must preserve before touching Home.

- [ ] Use CodeGraph to inspect `LauncherScreen`, `LauncherViewModel`, `HomeInputResults`,
      `CommandFeedback`, `PendingRoutedAction`, `RouteChipRow`, and `SidrCommandPrompt`.
- [ ] List every callback currently passed from `LauncherScreen` to the ViewModel.
- [ ] List every visible Home state: loading, empty, error, success idle, input active, pending routed
      action, command feedback, dev console, setup nudge.
- [ ] Record which artifact elements are real now vs future slots:
      - real: input, route chips, favorites, all apps, suggestions, pending action, feedback, setup nudge;
      - future: prayer data, real recents if not backed, agent/activity/execution.
- [ ] Run current launcher tests before edits:

```text
./gradlew :feature:launcher:testDebugUnitTest
```

Acceptance:

- A short checklist exists in the implementation notes or task PR body.
- No code edited in this task except optional docs/checklist.

## Task 2: `SidrUniversalInput` Core UI Module

**Purpose:** Build the deep presentation module before migrating Home.

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrUniversalInput.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrUniversalInputTest.kt`
- update or add control/input gallery screenshot tests

Steps:

- [ ] Add `SidrUniversalInputState`.
- [ ] Implement `SidrUniversalInput` with the spec interface.
- [ ] Preserve `>` prompt marker, block caret, mic affordance, clear affordance, supporting text, and
      `routeContent` slot.
- [ ] Use DS-3 buttons/chips/icon controls and DS-2 primitives internally.
- [ ] Make `onSubmit` parameterless and wire IME submit to it.
- [ ] Semantics: prompt marker not read as greater-than; mic and clear have clear content descriptions.
- [ ] Screenshot states: idle, focused, typing, with routes, mic available, mic unavailable, long text,
      dark/light, font-scale-2.0.

Acceptance:

- `:core:ui:testDebugUnitTest` green.
- `:core:ui:verifyRoborazziDebug` green after goldens are updated intentionally.
- No domain/feature imports in `core/ui`.

## Task 3: Route Chip Presentation

**Purpose:** Make Home route chips match the approved APP/WEB/SITE/ASK press-invert language.

Steps:

- [ ] Map `RouteChipKind.WEB/ASK/SITE` to labels and callbacks at the feature layer.
- [ ] Use DS-3 `SidrRouteChip` for route chips.
- [ ] Decide whether APP is a visual selected lane or implicit in app result rows. If rendered, APP must not
      add new behaviour.
- [ ] Keep route ordering stable and test long labels / narrow width.

Acceptance:

- WEB/SITE/ASK callbacks call the same ViewModel methods as before.
- Route chip selected state is visible without relying only on colour.
- No route auto-submits on render.

## Task 4: Feature-Local Home Shell

**Purpose:** Recompose the Home surface around real slots without moving feature composition into `core/ui`.

Steps:

- [ ] Add private/feature-local composables in `LauncherScreen.kt` or a nearby feature file if the file
      becomes too large:
      - `HomeTopRow`;
      - `HomeAnchorSlot`;
      - `HomePrivacyLine`;
      - `HomeAllAppsRow`;
      - `HomeRelevantSlot`;
      - `HomeTypingResults`.
- [ ] Top row uses `SIDR`, date/Hijri text if available as static/current UI data, and Settings action.
- [ ] Keep hidden dev-mode arming path if it remains owner-approved; if moved from wordmark, preserve access.
- [ ] Replace bottom `CommandBar` only after Assistant, All Apps, and Settings remain discoverable in the
      new Home body/top row.
- [ ] Do not render prayer times until DS-6B.
- [ ] Do not render real Recent rows unless backed by existing real data. Existing `suggestionsContent` may
      fill relevant continuation.

Acceptance:

- Home idle reads like the artifact without fake data.
- Settings, Assistant, and All Apps are still reachable.
- No new navigation routes.

## Task 5: Migrate LauncherScreen Input Wiring

**Purpose:** Replace the production Home input presentation while preserving callback flow.

Steps:

- [ ] Replace `SidrCommandPrompt` usage with `SidrUniversalInput`.
- [ ] Map `commandInput` and `inputResults.active` to `SidrUniversalInputState`.
- [ ] Wire `onValueChange = viewModel::onCommandChanged`.
- [ ] Wire `onSubmit = { viewModel.onCommandSubmitted(commandInput) }` or equivalent preserving the old
      submitted value.
- [ ] Wire mic through the existing `onMicTap`.
- [ ] Wire clear through the existing input change path; if no clear callback exists, call
      `viewModel.onCommandChanged("")` only.
- [ ] Put route chips in `routeContent`.

Forbidden:

- no change to `LauncherViewModel.onCommandSubmitted` semantics;
- no auto-submitting voice partial text;
- no new cloud context;
- no SavedStateHandle use for assistant prompt.

Acceptance:

- Existing `LauncherViewModelTest` passes unchanged.
- IME submit still executes the same command path.
- Clear does not submit.

## Task 6: Typing / Results Overtake

**Purpose:** Make the typed state match the artifact while preserving `HomeInputResults`.

Steps:

- [ ] Rework `InputResultsPanel` presentation around DS-3 rows/chips and DS-2 text/surface primitives.
- [ ] Keep `appMatches` local and clickable through `onAppClick`.
- [ ] Render route rows/chips for WEB/SITE/ASK with the existing callbacks.
- [ ] Ensure async app icons do not resize rows or shift input.
- [ ] Preserve Ask Assistant prefill path with URL encoding.

Acceptance:

- Typing "telegr" still shows app matches and routes.
- App result tap launches the app.
- WEB/SITE/ASK still route correctly.
- Input/results layout stable at 360dp width and font-scale-2.0.

## Task 7: Active Slot: Feedback and Pending Actions

**Purpose:** Place command feedback, clarification, and pending routed actions into the new Home hierarchy.

Steps:

- [ ] Render `CommandFeedback.Ambiguous` as clarification, not error.
- [ ] Render `CommandFeedback.Message` / `Suggestion` through DS-3 rows/surfaces.
- [ ] If DS-3 already migrated routed confirmation, render pending actions through `SidrActionGate`.
- [ ] If DS-3 did not migrate routed confirmation, keep `PendingActionArea` behaviour unchanged and mark
      Action Gate migration as a dependency.
- [ ] Do not introduce completed result cards for ordinary app launches.

Acceptance:

- Ambiguous app choice still launches only after explicit candidate tap.
- SAFE routed proposal still requires deliberate tap.
- CONFIRM routed proposal still requires confirmation.
- Cancel still dismisses without action.

## Task 8: Home Idle Content

**Purpose:** Align the idle Home with the artifact using only current real data.

Steps:

- [ ] Recompose setup nudge, suggestions/relevant continuation, Favorites, All Apps, and privacy line.
- [ ] Favorites retain existing cap/count behaviour from `LauncherUiState`.
- [ ] Suggestions remain optional/opt-in and do not become random AI insight cards.
- [ ] All Apps routes to `Routes.AppDrawer.ROUTE`.
- [ ] Privacy line reflects current local/cloud behaviour honestly.

Acceptance:

- No first-frame network requirement.
- No Loading spinner on first successful cached frame.
- Empty space is acceptable.
- Ordinary app launch does not produce extra result UI.

## Task 9: Screenshots and Tests

Core UI:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
```

Launcher:

```text
./gradlew :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

Screenshot states:

- universal input idle/focused/typing;
- route chips;
- Home idle dark/light;
- Home typing/results;
- pending confirm/Safe if integrated;
- font-scale-2.0;
- 360dp baseline;
- RTL smoke for input/results text.

Acceptance:

- existing behaviour tests green;
- intentional visual golden updates reviewed against `docs/design/artifacts/e34033dd/`;
- no new privacy allow-list entries.

## Task 10: Device Acceptance

Run on the available Android device after build green:

- [ ] first frame / no white flash / no first-frame spinner;
- [ ] type and launch installed app;
- [ ] type unknown natural-language command with router off;
- [ ] WEB route;
- [ ] SITE route;
- [ ] ASK route and assistant prefill not auto-sent;
- [ ] SAFE routed proposal;
- [ ] CONFIRM routed proposal;
- [ ] Cancel;
- [ ] mic permission education path;
- [ ] offline fallback;
- [ ] router disabled parity;
- [ ] Settings / Assistant / All Apps discoverability.

## Task 11: Documentation and Status

- [ ] Add DS-4 completion ADR only after implementation + verification.
- [ ] Update `ai-context/current-status.md`.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` if implementation deliberately deviates from the
      artifact.
- [ ] Mark `SidrCommandPrompt` deprecated only after usage reaches zero.

## Full Verification Gate

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before closing DS-4 because this block touches the launcher's central
interaction loop.

## Stop Conditions

Stop and re-scope if:

- implementation needs a ViewModel contract rewrite;
- prayer data is requested before DS-6B;
- Home needs fake recent/activity/agent state to match the artifact;
- App Drawer categorization work starts creeping into Home;
- route/click/voice/IME parity fails and cannot be fixed presentation-only;
- screenshot changes hide text at font-scale-2.0 or 360dp width.
