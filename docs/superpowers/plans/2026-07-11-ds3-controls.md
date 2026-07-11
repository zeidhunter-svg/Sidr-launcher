# DS-3 — SIDR Controls Implementation Plan

> **STATUS: DONE (2026-07-11).** This plan implements the DS-3 control language specified in
> `docs/superpowers/specs/2026-07-11-ds3-controls-design.md`. It is presentation-first and parity-preserving:
> new reusable controls land in `core/ui`, Settings proves the row/control language, and routed
> confirmation migrates only after the shared `SidrActionGate` is tested.

**Goal:** Introduce SIDR buttons, chips, rows, top bars, section headers, and Action Gate controls that match
the soft-classic-grey artifact set while preserving existing feature behaviour.

**Architecture:** Public controls live in `core/ui/component` and compose the completed DS-2 primitives.
Feature modules map their state into display-safe strings, local `core/ui` enums, callbacks, and slots.
Shared implementation detail may live in private/internal frames, but public interfaces stay semantic and
small.

**Spec:** `docs/superpowers/specs/2026-07-11-ds3-controls-design.md`.

## Global Constraints

- Presentation-only unless explicitly called out for call-site migration.
- No `domain`, `data`, or feature imports in `core/ui/component` controls.
- No Settings preference key changes, no new settings, no ViewModel contract changes.
- No Home/Universal Input restructuring; DS-4 owns `SidrUniversalInput`.
- No App Drawer grouping engine or category persistence in this block.
- No agent/task/activity/execution-stream UI beyond preview/future-contract notes.
- Legacy components are deprecated only after usage reaches zero; no mass deletion.

## Proposed File Structure

New main files under `core/ui/src/main/java/com/sidr/launcher/core/ui/component/`:

- `SidrButton.kt`
- `SidrIconButton.kt`
- `SidrChip.kt`
- `SidrRow.kt`
- `SidrTopBar.kt`
- `SidrActionGate.kt`

New test files under `core/ui/src/test/java/com/sidr/launcher/core/ui/component/`:

- `ControlsDependencyGuardTest.kt`
- `SidrButtonTest.kt`
- `SidrChipTest.kt`
- `SidrRowSemanticsTest.kt`
- `SidrActionGateTest.kt`
- `ControlGallery.kt`
- `ControlsScreenshotTest.kt`

Modified production call sites:

- `feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsScreen.kt`
- `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt` only for the
  `ConfirmActionCard -> SidrActionGate` migration task.
- Existing `RouteChipRow`, `TopBarIcon`, `SectionHeader`, `ConfirmActionCard` may become compatibility
  wrappers or remain until usages are migrated.

## Task 1: Control Gallery and Dependency Guard

**Purpose:** Create the verification harness before adding many controls.

- [x] Add `ControlsDependencyGuardTest` proving new `core/ui/component/Sidr*.kt` files do not import
      `domain`, `data`, or feature packages.
- [x] Add `ControlGallery.kt` in test source with sections for buttons, chips, rows, top bar, and Action
      Gate states.
- [x] Add `ControlsScreenshotTest` dark/light goldens with the gallery initially empty or smoke-only.
- [x] Run `./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug`.

Acceptance:

- Roborazzi path is proven before component migration.
- Guard is narrow enough not to fail existing legacy files accidentally unless they are part of DS-3.

## Task 2: Button Family

**Purpose:** Replace raw Material buttons with SIDR buttons that carry loading, disabled, and destructive
semantics consistently.

- [x] Add `SidrPrimaryButton`, `SidrSecondaryButton`, `SidrTertiaryButton`, `SidrDestructiveButton`, and
      `SidrTerminalAction`.
- [x] Share a private/internal button frame for shape, min touch target, loading, and semantics.
- [x] Unit/semantics tests: disabled blocks click, loading blocks duplicate click, content description is
      readable, min touch target is present.
- [x] Add dark/light/disabled/loading/long-label/font-scale-2.0 states to `ControlGallery`.

Acceptance:

- Buttons use DS-1/DS-2 tokens, not raw `MaterialTheme.colorScheme.primary` for every action.
- Destructive button is muted; no loud red fill by default.
- Terminal action is visibly compact and not used for normal Settings rows.

## Task 3: Chip Family and Press-Invert

**Purpose:** Stabilize route/filter/status/risk chip behaviour for DS-4 Home and current Settings controls.

- [x] Add `SidrRouteChip`, `SidrFilterChip`, `SidrSuggestionChip`, `SidrActionChip`, `SidrStatusChip`,
      `SidrRiskChip`, and `SidrRiskTone`.
- [x] Implement press-invert selected/pressed state with no glow, no scale, no layout shift.
- [x] Tests: selected state does not rely on colour alone; status/risk tokens never use the accent token;
      disabled chips do not fire callbacks.
- [x] Update `ControlGallery` with APP/WEB/SITE/ASK selected states, long labels, status chips, and risk chips.
- [x] Keep `RouteChipRow` as a row composition over `SidrRouteChip` or leave it unchanged until DS-4 if
      touching it would pull Home migration forward.

Acceptance:

- Press-invert matches artifact direction.
- `SidrRiskChip` can express SAFE, CONFIRM, EXTERNAL, and destructive risk labels without domain imports.

## Task 4: Rows, Sections, and Top Bar

**Purpose:** Build the controls needed to migrate Settings without changing its backend.

- [x] Add `SidrNavigationRow`, `SidrToggleRow`, `SidrChoiceRow`, `SidrStatusRow`, and `SidrDestructiveRow`.
- [x] Add `SidrSectionHeader`, `SidrAlphabetHeader`, and `SidrTopBar`.
- [x] Add `SidrIconButton` with `ImageVector` and `Painter` overloads; keep `TopBarIcon` as a compatibility
      wrapper or migrate its usages later.
- [x] Semantics tests: toggle row has one interaction owner; choice row uses radio role; navigation row
      exposes title/value; destructive row has explicit label.
- [x] Gallery states: normal, disabled, long value, font-scale-2.0, RTL text.

Acceptance:

- No card-per-row styling.
- Rows stack gracefully at large font scale.
- `SidrToggleRow` cannot double-toggle when tapping the row/switch.

## Task 5: Settings Proof Surface

**Purpose:** Prove the DS-3 controls on a real screen with stable behaviour and low routing risk.

- [x] Migrate `SettingsScreen` top bar to `SidrTopBar` + `SidrIconButton`.
- [x] Replace `SectionHeader` call sites with `SidrSectionHeader`.
- [x] Replace theme radio rows with `SidrChoiceRow`.
- [x] Replace AI suggestions, usage personalization, voice input, and Smart command routing rows with
      `SidrToggleRow`.
- [x] Replace Assistant provider, Learned choices, and Set as default launcher buttons with
      `SidrNavigationRow`.
- [x] Replace favorites count raw `FilterChip`s with `SidrFilterChip`.
- [x] Retire or hide the old accent selector if the current DS-1/ADR stance requires the stored accent
      preference to remain inert; otherwise render it as a compatibility row clearly marked by the plan.
- [x] Run `./gradlew :feature:settings:testDebugUnitTest testDebugUnitTest assembleDebug`.

Forbidden:

- no `SettingsViewModel` behaviour change;
- no persisted key change;
- no new setting;
- no move of `defaultLauncherIntent` into the ViewModel;
- no Assistant/provider form duplication.

Acceptance:

- Existing Settings tests pass unchanged or are updated only for presentation-independent labels.
- Screen matches artifact direction: sections by spacing/hairlines, no card per row, compact values.
- Font-scale-2.0 remains usable.

## Task 6: SidrActionGate Component

**Purpose:** Create the shared consent gate family before replacing routed confirmation.

- [x] Add `SidrActionGateType` and `SidrActionGate`.
- [x] Compose from `SidrSurface`, `SidrText`, `SidrProvenanceLine` slot, `SidrRiskChip`, and SIDR buttons.
- [x] Tests: Cancel calls only cancel; Confirm calls exactly once; `confirming` disables both controls;
      consequence is always present; long URL/target wraps.
- [x] Gallery states: External confirmation, Permission, Destructive, Sensitive Data, confirming/loading,
      large font vertical buttons.

Acceptance:

- `SidrActionGate` has no domain/action imports.
- Risk is label + marker + consequence, not colour-only.
- Dismiss/back semantics are documented for hosts; the component itself only exposes callbacks.

## Task 7: Migrate Routed Confirmation

**Purpose:** Replace the old `ConfirmActionCard` production usage with `SidrActionGate` while keeping router
proposal parity.

- [x] Map `PendingRoutedAction` display strings in `feature/launcher` to `SidrActionGate` presentation
      params.
- [x] Preserve existing `onConfirm` and `onCancel` callbacks.
- [x] Keep SAFE one-tap proposal behaviour distinct from CONFIRM external handoff.
- [x] Add/adjust launcher UI tests where practical; keep ViewModel tests behaviour-first.
- [x] Run `./gradlew :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug`.

Device/manual acceptance for this task:

- URL confirmation: no execution before Continue.
- Cancel produces no action.
- Continue executes exactly once.
- SAFE routed proposal still requires deliberate tap.
- Router-off/offline rule parity is unchanged.

## Task 8: Documentation, ADR, and Status

- [x] Update `ai-context/decisions.md` with a DS-3 completion ADR after implementation gates pass.
- [x] Update `ai-context/current-status.md` from "Next: DS-3" to the actual DS-3 state.
- [x] Update `docs/design/artifacts/e34033dd/README.md` only if implementation intentionally deviates from
      the captured artifact direction.
- [x] Mark legacy components as deprecated in comments only after call sites are migrated.

## Full Verification Gate

Run after Task 7:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:settings:testDebugUnitTest :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

Expected result:

- core UI unit/semantics/screenshot gates green;
- Settings and Launcher tests green;
- no domain/data imports in new controls;
- no behaviour changes in ViewModel tests;
- screenshots cover dark/light, disabled, focused, pressed/selected, long content, font-scale-1.5/2.0,
  and RTL for text-bearing controls.

## Stop Conditions

Stop and re-scope before implementation if:

- Settings migration requires new persisted preferences;
- App Drawer grouping/categorization is requested inside DS-3;
- Action Gate migration requires changing routed action semantics;
- Home/Universal Input migration starts creeping into the controls work;
- any artifact future-contract surface would require fake agent/runtime/activity state.
