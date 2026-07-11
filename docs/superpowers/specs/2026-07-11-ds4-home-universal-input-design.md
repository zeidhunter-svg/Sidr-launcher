# DS-4 — Home Shell and Universal Input (Design Spec)

> **Status: PROPOSED (2026-07-11).** Repository-grounded design for migrating Home to the approved
> soft-classic-grey visual direction and replacing the legacy `SidrCommandPrompt` presentation with
> `SidrUniversalInput`.
>
> **Prerequisite:** DS-3 controls must be implemented and green first. DS-4 consumes DS-3 buttons/chips/rows
> and `SidrActionGate`; it does not create a competing control language.
>
> **Governing sources:** `docs/design/SIDR Design System Master Plan.md` DS-4, `docs/design/SIDR Design
> Migration Plan v1.1.md` DS-4, `docs/design/artifacts/e34033dd/` Claude artifact captures,
> `docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md`, DS-1/DS-2/DS-3 specs.

## 1. Goal

Move the launcher Home from the transitional terminal shell to the approved intent-first Home layout while
preserving the existing command pipeline, input routing, voice path, app launch behaviour, and offline
parity.

DS-4 is a **presentation and composition migration**:

- replace `SidrCommandPrompt` with `SidrUniversalInput`;
- recompose Home around the approved hierarchy;
- keep current ViewModel state and callbacks intact;
- keep search-overtakes behaviour intact;
- render only real data that exists today.

## 2. Non-Negotiable Behaviour Parity

The following must remain unchanged:

- blank input behaviour;
- `onCommandChanged` and `onCommandSubmitted` contract;
- live app filtering;
- app click launches and records usage as today;
- WEB route;
- SITE route;
- ASK route and assistant prompt prefill;
- rule-first matching;
- LLM planner trigger conditions;
- SAFE routed proposal behaviour;
- CONFIRM routed proposal behaviour;
- Cancel behaviour for pending routed actions;
- voice final transcript path;
- mic permission education route;
- clear input behaviour;
- IME submission;
- offline fallback;
- router-off parity;
- hidden dev console arming path unless explicitly re-scoped.

No ViewModel rewrite is required for DS-4. Presentation code maps existing state to the new visual surface.

## 3. Current Baseline

Current `LauncherScreen`:

- reads `uiState`, `commandInput`, `commandFeedback`, `showMic`, `inputResults`, `pendingRoutedAction`,
  `devConsoleOn`, `consoleLines`, and `isOnline` from `LauncherViewModel`;
- renders a top bar with `SIDR//` and `HomeStatus`;
- renders `SidrCommandPrompt`;
- renders `CommandFeedbackArea` below the input;
- renders pending routed actions through `PendingActionArea`;
- when `inputResults.active`, replaces Home content with `InputResultsPanel`;
- otherwise renders loading/empty/error or `HomeContent`;
- renders a persistent bottom `CommandBar` for Assistant, All Apps, and Settings when input results/dev
  console are inactive.

Current `HomeInputResults` is intentionally small:

```kotlin
data class HomeInputResults(
    val active: Boolean = false,
    val appMatches: List<InstalledApp> = emptyList(),
    val chips: List<RouteChipKind> = emptyList(),
)

enum class RouteChipKind { WEB, ASK, SITE }
```

DS-4 should keep this shape unless a presentation-only mapper is needed.

## 4. Artifact Target

The `visual-direction-study-soft-classic-grey.png` and `screen-set-01-shipping-screens.png` captures define
the Home direction:

- `SIDR` wordmark at top-left;
- Gregorian date + Hijri date + settings icon at top-right;
- quiet sacred/spiritual anchor area;
- prayer context as a thin secondary strip, **only when real prayer data exists**;
- Universal Input as the primary control;
- route chips under input with colourless press-invert selected state;
- Favorites as compact icon tiles;
- Recent/relevant rows only if backed by real current data;
- All Apps row/link;
- local-first privacy line;
- dark and light parity.

The artifact also shows Home typing/results:

- input stays fixed and stable;
- results overtake the body;
- APP/WEB/SITE/ASK routes remain visible;
- local app results remain local;
- unrelated idle content recedes.

## 5. Scope

In scope:

- `SidrUniversalInput` presentation module in `core/ui/component`;
- Home top row / wordmark / date / settings affordance composition;
- Home body hierarchy using existing launcher data;
- route chip presentation using DS-3 chips;
- typing/results overtakes layout;
- pending action / feedback slots integrated into the Home flow;
- Favorites and All Apps access;
- local-first privacy line;
- screenshot tests for input/home states where practical;
- device acceptance for typed, voice, route, SAFE, CONFIRM, offline, router-off.

Out of scope:

- prayer calculation, location access, timezone correctness, or Diyanet/authority data (DS-6B);
- full sacred header implementation if DS-6A is not yet complete;
- Activity journal;
- Agents surface;
- execution stream;
- new bottom navigation;
- app categorization engine for grouped App Drawer;
- new memory surfaces;
- new ViewModel/domain/data contracts.

## 6. Sacred and Prayer Handling

The artifact includes Shahada and a prayer strip, but the DS sequence separates those concerns:

- DS-4 may reserve a `sacredAnchor` slot in Home composition.
- DS-4 may render a quiet non-data visual anchor only if approved by the DS-6A owner decision, but it must
  not claim prayer correctness.
- DS-4 must not render fake prayer times, fake sources, or plausible-but-unverified religious data.
- DS-6A owns the final Sacred Header component.
- DS-6B owns prayer data correctness, source, method, locality, cache, provenance, and failure states.

This keeps the Home layout aligned with the north-star while preserving the "no fake state" rule.

## 7. Public Interface: `SidrUniversalInput`

`SidrUniversalInput` is a deep presentation module: small caller interface, internal handling for prompt
marker, caret, focus, mic, clear, supporting text, semantics, and layout stability.

```kotlin
enum class SidrUniversalInputState {
    Idle,
    Focused,
    Typing,
    Listening,
    Interpreting,
    Ambiguous,
    Proposed,
    Executing,
    Error,
    Disabled,
}

@Composable
fun SidrUniversalInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    state: SidrUniversalInputState = SidrUniversalInputState.Idle,
    placeholder: String = "Ask, search, open or automate",
    enabled: Boolean = true,
    voiceAvailable: Boolean = false,
    onVoiceClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    routeContent: (@Composable (() -> Unit))? = null,
    supportingText: String? = null,
)
```

Rules:

- `>` prompt marker stays, but TalkBack must not read it as "greater than";
- block caret appears only when appropriate for real focus/idle state;
- no fake spinner/waveform;
- mic state is explicit and accessible;
- clear action has a 48dp target;
- route content is a slot so route mapping remains feature-owned;
- `onSubmit` is parameterless; the caller already owns `value` and routes to existing ViewModel callbacks;
- IME search/submit path must call the same callback as before.

## 8. Home Composition

Feature-level Home composition remains in `feature/launcher`; `core/ui` must not contain a full
`LauncherScreen`.

Target hierarchy before DS-6A/DS-6B:

```text
SidrScaffold(Home)
├── Home top row
├── Sacred/quiet anchor slot
├── Universal Input
├── Route chips
├── Active slot: feedback / pending action / clarification / error
├── Relevant continuation slot (existing suggestions only)
├── Favorites
├── All Apps
└── Privacy line
```

When typing:

```text
Home top row compact
Universal Input
Route chips
Input results
```

Rules:

- typing/results dominate without async icon layout jumps;
- ordinary completed app launches produce no Home result card;
- pending actions use DS-3 `SidrActionGate` if DS-3 migrated it;
- `CommandFeedback.Ambiguous` should read as clarification, not error;
- suggestions/recent rows must come from existing real data, not placeholder history;
- bottom `CommandBar` should be retired or folded into visible Home rows only when the same navigation
  actions remain discoverable.

## 9. State Mapping

Map only states that exist:

- `Idle`: empty input, no pending action, no active results;
- `Typing`: `commandInput.isNotBlank()` / `inputResults.active`;
- `Listening`: voice recognition active if ViewModel exposes it; otherwise do not fake it;
- `Ambiguous`: `CommandFeedback.Ambiguous`;
- `Proposed`: pending SAFE routed action;
- `Executing`: only if there is a real in-flight state; otherwise not used;
- `Error`: `UiState.Error` or command feedback error text;
- `Disabled`: only if input is truly unavailable.

If a state is not observable from existing state, leave it preview-only in `SidrUniversalInput`.

## 10. Verification

Required:

- `unit`: state mapping helpers if introduced;
- `semantics`: prompt marker, mic, clear, route chips, app result rows, All Apps row;
- `screenshot`: universal input idle/focused/typing/listening-disabled, route chips, Home idle, Home typing,
  dark/light, font-scale-2.0, RTL safety;
- `feature tests`: existing `LauncherViewModelTest` and `AppDrawerViewModelTest` pass unchanged;
- `device/manual`: typed app launch, web/site/ask route, SAFE route, CONFIRM route, Cancel, mic permission,
  offline, router-off.

Suggested implementation gate:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

## 11. Success Criteria

- `SidrUniversalInput` replaces `SidrCommandPrompt` at Home without behaviour changes.
- Home visually matches the approved soft-grey direction while rendering only real data.
- Typing/results overtakes works without layout instability.
- Favorites, All Apps, Settings, Assistant, and mic access remain discoverable.
- No fake prayer, agent, activity, execution, or memory state is introduced.
- DS-5/DS-6 can extend the shell through explicit slots rather than another Home rewrite.
