# DS-3 — SIDR Controls (Design Spec)

> **Status: PROPOSED (2026-07-11).** Repository-grounded design for the DS-3 controls and semantic
> interaction layer. This composes the completed DS-1 grey token layer and DS-2 primitive layer into
> reusable controls, then proves them on Settings before Home is migrated.
>
> **Governing sources:** `docs/design/SIDR Design System Master Plan.md` DS-3, `docs/design/SIDR Design
> Migration Plan v1.1.md` DS-3, `docs/design/artifacts/e34033dd/` Claude artifact captures,
> `docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md`, DS-1/DS-2 ADRs in
> `ai-context/decisions.md`.

## 1. Goal

Create SIDR's control language: buttons, chips, rows, top bars, section labels, and the first semantic
Action Gate. Controls must feel like the approved soft classic grey artifact set, but keep all existing
feature behaviour intact.

DS-3 is a **presentation migration**:

- no routing changes;
- no Settings preference contract changes;
- no new persistence keys;
- no ViewModel ownership changes;
- no `domain` or feature imports in `core/ui`;
- no Home/Universal Input restructuring yet (that is DS-4).

## 2. Current Baseline

Already complete:

- DS-1: `SidrColors`, fixed status palette, tri-font typography, softened shapes, Roborazzi harness.
- DS-2: `Strokes`, `SidrText`, `SidrSystemLabel`, `SidrProvenanceLine`, `SidrStatusMarker`, `SidrSurface`,
  `SidrDivider`, `sidrFocusRing`, `SidrProgress`.

Legacy production controls still visible:

- `SettingsScreen` mixes raw Material `Button`, `Switch`, `RadioButton`, `FilterChip`, and old
  `SectionHeader`.
- `RouteChipRow` renders bracketed terminal chips and owns press state itself.
- `ConfirmActionCard` is the old DF-4 terminal block, not the shared gate family.
- `TopBarIcon` is correct in touch target, but should be wrapped/renamed into the SIDR control family.
- `SidrCommandPrompt` stays for now; `SidrUniversalInput` is DS-4.

## 3. Visual Target From Artifact e34033dd

Shipping screens:

- Settings: sections by spacing and hairlines, no card-per-row, quiet rows, compact right-aligned values.
- Home: route chips use colourless press-invert selection; input is primary, but Home is DS-4.
- App Drawer: grouped icon grid, local search, `Groups / A-Z` toggle, provenance line. Grouping logic is
  not a DS-3 UI responsibility.
- Permission Education: clear prose, one primary action, one quiet secondary action.
- Memory: local-only evidence, edit/forget controls, destructive action de-emphasized until confirmed.

Interaction moments:

- clarification is not an error;
- one Action Gate shape handles confirmation and consent boundaries;
- SAFE proposal is one tap but still deliberate;
- risk is label + marker + consequence text, never colour alone;
- completed and partial results are visually distinct;
- errors state what failed, why, and what can be done next.

Future-contract screens in the artifact (agent cards, Activity, task execution stream) are **not** DS-3
implementation scope unless real A-layer engines already exist.

## 4. Design Principles

1. **Deep controls, small interfaces.** Each public composable should hide layout, token choice, focus,
   semantics, disabled/loading behaviour, and font-scale fallback behind a small caller interface.
2. **No giant nullable universal control.** Public functions are named by semantic role (`SidrToggleRow`,
   `SidrRouteChip`, `SidrActionGate`) even when they share an internal frame.
3. **Presentation models stay local to `core/ui`.** Feature modules map domain state to display-safe strings,
   local enums, callbacks, and slots.
4. **Risk is stable.** The same consequence gets the same marker, title hierarchy, and button treatment,
   regardless of whether it came from rules, cloud routing, assistant, or future agent runtime.
5. **Press-invert is functional, not decorative.** Selected/pressed state flips foreground/background with
   no glow, no scale bounce, and no layout shift.
6. **Rows own interaction once.** A toggle row must not toggle twice when the row and switch are tapped; the
   row owns the interaction and the trailing control reflects state.

## 5. Public Control Families

All live under `core/ui/src/main/java/com/sidr/launcher/core/ui/component/` and may share private/internal
frames. Public composables take strings, callbacks, slots, and `core/ui` enums only.

### 5.1 Buttons

```kotlin
@Composable
fun SidrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
)

@Composable
fun SidrSecondaryButton(...)

@Composable
fun SidrTertiaryButton(...)

@Composable
fun SidrDestructiveButton(...)

@Composable
fun SidrTerminalAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
)
```

Rules:

- 48dp minimum touch target;
- 8-10dp visual radius, never pill by default;
- loading disables duplicate taps and exposes progress semantics;
- destructive is muted border/text, not a loud red fill;
- terminal action is reserved for compact system/action surfaces, not normal Settings rows.

### 5.2 Icon Button

`TopBarIcon` evolves into `SidrIconButton`, preserving its 48dp target and required content description.

```kotlin
@Composable
fun SidrIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
```

Provide a `Painter` overload for bundled vector drawables. Keep `TopBarIcon` as a compatibility wrapper
until usages are migrated.

### 5.3 Chips

```kotlin
@Composable
fun SidrRouteChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun SidrFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun SidrSuggestionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun SidrActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun SidrStatusChip(label: String, status: SidrStatus, modifier: Modifier = Modifier)

enum class SidrRiskTone { Safe, Confirm, External, Destructive }

@Composable
fun SidrRiskChip(label: String, tone: SidrRiskTone, modifier: Modifier = Modifier)
```

Rules:

- selected route/filter chip uses press-invert (`text` fill + `ground`/`surface` text depending theme);
- status chips use fixed status tokens, never the pewter accent;
- risk chip always includes text and marker;
- long labels wrap or constrain without resizing the row height unpredictably;
- `RouteChipRow` remains a feature-level row composition or becomes a thin row over `SidrRouteChip`, not a
  second chip implementation.

### 5.4 Rows

```kotlin
@Composable
fun SidrNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
)

@Composable
fun SidrToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
)

@Composable
fun SidrChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
)

@Composable
fun SidrStatusRow(...)

@Composable
fun SidrDestructiveRow(...)
```

Rules:

- rows are full-width, 48dp minimum touch target;
- section separation is spacing and optional `SidrDivider`, not a card around every row;
- `SidrToggleRow` uses one `toggleable` owner; the visual switch has `onCheckedChange = null`;
- value text wraps under the title at large font scale rather than clipping;
- TalkBack reads title, value/status, description, and role in a sensible order.

### 5.5 Sections and Top Bars

```kotlin
@Composable
fun SidrSectionHeader(text: String, modifier: Modifier = Modifier)

@Composable
fun SidrAlphabetHeader(text: String, modifier: Modifier = Modifier)

@Composable
fun SidrTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable (() -> Unit))? = null,
    actions: @Composable RowScope.() -> Unit = {},
)
```

Rules:

- `SidrSectionHeader` replaces semantic section usage of old `SectionHeader`;
- `SidrAlphabetHeader` replaces App Drawer sticky alphabet/category headers;
- `SidrTopBar` is presentation-only and never owns navigation.

### 5.6 Action Gate

```kotlin
enum class SidrActionGateType {
    Confirmation,
    Permission,
    SensitiveData,
    ExternalHandoff,
    Destructive,
}

@Composable
fun SidrActionGate(
    type: SidrActionGateType,
    title: String,
    consequence: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    target: String? = null,
    reason: String? = null,
    provenance: (@Composable (() -> Unit))? = null,
    privacyText: String? = null,
    details: (@Composable (() -> Unit))? = null,
    confirming: Boolean = false,
)
```

Rules:

- consequence is always visible;
- Cancel is always visible and does not perform the action;
- dismiss/back equals Cancel when hosted in a dismissable surface;
- confirming disables both controls and prevents duplicate confirm;
- URL/target text wraps, never silently truncates meaningful origin;
- risk uses `SidrRiskChip` + title/consequence text, not colour alone;
- feature modules map domain risk/action into this presentation interface.

## 6. Proof Surfaces

### First proof: Settings

Migrate `SettingsScreen` presentation to:

- `SidrTopBar`;
- `SidrSectionHeader`;
- `SidrToggleRow`;
- `SidrChoiceRow`;
- `SidrNavigationRow`;
- `SidrFilterChip` for favorites count;
- `SidrStatusRow` for assistant provider / learned choice counts if useful.

Preserve:

- every `SettingsViewModel` callback;
- `defaultLauncherIntent` and `rememberLauncherForActivityResult` flow;
- persisted keys and labels unless a copy change is explicitly part of the migration;
- no new settings.

### Second proof: routed proposal / confirmation

Replace `ConfirmActionCard` with `SidrActionGate` only after the control family is stable.

Preserve:

- no auto-execution;
- Cancel callback exactly once;
- Confirm callback exactly once;
- SAFE vs CONFIRM proposal behaviour;
- feature-level mapping from domain action/risk to display strings.

## 7. Verification

Required:

- `unit`: role-to-token helpers, risk/status mappings, loading/disabled duplicate-click guards where pure;
- `semantics`: required labels, toggle row single owner, prompt marker/risk marker not colour-only;
- `screenshot`: control gallery dark/light, pressed/selected, disabled, loading, long content, font-scale
  1.5/2.0, RTL where text-bearing;
- `arch-guard`: no `domain`, `data`, or feature imports in new `core/ui/component` controls;
- `feature tests`: existing Settings and Launcher ViewModel tests pass unchanged;
- `device/manual` for the routed confirmation migration and Home input later.

Suggested gate after implementation:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug testDebugUnitTest assembleDebug
```

## 8. Non-goals

- No `SidrUniversalInput` or `SidrCommandPrompt` replacement in DS-3; DS-4 owns it.
- No App Drawer grouping data model or app categorization engine in DS-3.
- No Memory, Assistant, Permission Education screen migration beyond using the new controls where a proof
  needs them.
- No Activity, Agent, Execution Stream, grants, or task-flow UI without real A-layer contracts.
- No removal of legacy components until usage reaches zero.

## 9. Success Criteria

- New controls compose from DS-2 primitives and live in `core/ui` with presentation-only interfaces.
- Settings proves rows/buttons/chips/top bar without changing behaviour.
- Routed confirmation can be migrated through `SidrActionGate` with parity tests.
- Roborazzi captures the full control-state matrix.
- Future DS-4 Home can build Universal Input and route chips without inventing another control language.
