# DS-5 — Action and Safety Family (Design Spec)

> **Status: PROPOSED (2026-07-11).** This spec restores DS-5 as its own design-system block after DS-3
> controls. DS-3 may provide the base controls and the first `SidrActionGate` primitive; DS-5 owns the
> production action/safety family, migration, result/error surfaces, and device acceptance.
>
> **Prerequisites:** DS-3 controls implemented and green. DS-4 Home may be implemented before or alongside
> DS-5, but DS-5 must not rely on fake Home, agent, activity, or execution states.
>
> **Governing sources:** `docs/design/SIDR Design Migration Plan v1.1.md` DS-5, `docs/design/SIDR Visual
> Acceptance Spec v1.1.md` action/result/error sections, `docs/design/SIDR Component Library v1.1.md`,
> `docs/design/artifacts/e34033dd/`, and the Action Registry/routed-action behaviour already implemented
> in Stage 1.

## 1. Goal

Make consequential actions honest, consistent, and reusable across SIDR:

- proposals are visibly not completed actions;
- confirmation/permission/privacy gates share one visual and behavioural language;
- results state what happened, including partial success;
- errors state what failed, why, and what the user can do next;
- local/cloud/external provenance is visible where it matters;
- Cancel never confirms;
- duplicate confirm taps cannot execute twice.

DS-5 is a **semantic presentation migration**. It must preserve all existing routing/execution semantics.

## 2. Relationship to DS-3

DS-3:

- builds base buttons, chips, rows, top bar, and initial `SidrActionGate` controls;
- proves controls on Settings;
- may add preview-only risk/gate variants.

DS-5:

- turns the action/safety family into production surfaces;
- migrates current routed proposal/confirmation behaviour;
- migrates Permission Education presentation;
- introduces result/error/offline/blocked surfaces;
- sets screenshot/device acceptance for consequential action moments.

This split prevents DS-3 from becoming too broad while keeping the sequence honest.

## 3. Current Production Baseline

Current surfaces:

- `ConfirmActionCard` in `core/ui` renders the old terminal DF-4 confirmation block.
- `PendingActionArea` in `LauncherScreen` maps routed `SAFE` proposals and `CONFIRM` proposals.
- `PermissionEducationScreen` uses raw Material `Button`, `TextButton`, and text.
- `ErrorState` and `EmptyState` are generic centered states with raw Material styling.
- `CommandFeedbackArea` renders messages, suggestions, and ambiguity with feature-local presentation.

Current invariants to preserve:

- routed actions never auto-execute;
- SAFE proposal still requires a deliberate tap;
- CONFIRM proposal requires explicit confirmation;
- Cancel dismisses with no side effects;
- permission education is shown before any request;
- denial affects only the related feature;
- router-off/offline parity remains intact.

## 4. Public Surface Family

All production components live in `core/ui/component` or `core/ui/pattern` if that package is created by
the implementation. Public interfaces take display-safe strings, callbacks, slots, and `core/ui` enums only.

### 4.1 Action Proposal

```kotlin
enum class SidrActionProposalTone { Safe, Confirm, External, Destructive }

@Composable
fun SidrActionProposal(
    title: String,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    tone: SidrActionProposalTone = SidrActionProposalTone.Safe,
    provenance: (@Composable (() -> Unit))? = null,
    onCancel: (() -> Unit)? = null,
    executing: Boolean = false,
)
```

Rules:

- proposal is not result;
- one-tap SAFE still needs user tap;
- executing disables controls;
- tone includes label/marker, not colour alone.

### 4.2 Action Gate

`SidrActionGate` from DS-3 becomes production-owned here.

Required variants:

- confirmation;
- external handoff;
- permission boundary;
- sensitive data/cloud handoff;
- destructive.

Rules:

- consequence always visible;
- Cancel always visible;
- Confirm disabled while confirming;
- dismiss/back equals Cancel in host surfaces;
- long URL/target wraps;
- risk is label + marker + consequence text.

### 4.3 Permission and Privacy Notices

```kotlin
@Composable
fun SidrPermissionNotice(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    withoutPermission: String? = null,
    secondaryLabel: String = "Not now",
    onSecondary: (() -> Unit)? = null,
    status: String? = null,
)

@Composable
fun SidrPrivacyNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    provenance: (@Composable (() -> Unit))? = null,
)
```

Rules:

- not a fake Android permission dialog;
- states what continues to work without permission;
- no pressure language;
- no startup permission request;
- `Not now` remains available when the flow is optional.

### 4.4 Result and Error Surfaces

```kotlin
enum class SidrResultTone { Completed, Partial, Failed }

@Composable
fun SidrResultSurface(
    tone: SidrResultTone,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    provenance: (@Composable (() -> Unit))? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
)

@Composable
fun SidrErrorSurface(
    title: String,
    modifier: Modifier = Modifier,
    whatFailed: String? = null,
    why: String? = null,
    next: String? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
)

data class SidrSurfaceAction(
    val label: String,
    val onClick: () -> Unit,
)
```

Rules:

- completed states what happened;
- partial success is never labelled completed;
- errors explain what failed, why, and next action;
- retry only appears when meaningful;
- ordinary app launch should not create a result surface.

### 4.5 Blocked and Offline States

```kotlin
@Composable
fun SidrBlockedState(...)

@Composable
fun SidrOfflineState(...)
```

Rules:

- use only for real blocked/offline conditions;
- no fake agent/task blockage;
- communicate what remains available offline.

## 5. Production Migration Targets

First production targets:

- `feature/launcher` routed SAFE proposal and CONFIRM proposal;
- URL and Play Store confirmation flows;
- `feature/permission_education` presentation;
- generic `ErrorState` replacement where a meaningful `SidrErrorSurface` can be used without changing
  behaviour.

Preview-only until a real caller exists:

- sensitive data/cloud handoff variants beyond existing routed cloud planner disclosure;
- destructive variants;
- blocked/offline states not yet backed by current state;
- result surfaces for future multi-step work.

## 6. Verification

Required:

- no confirm on render/focus/dismiss;
- Cancel callback exactly once;
- Confirm callback exactly once;
- loading/confirming prevents duplicate action;
- consequence is visible;
- buttons stack at font-scale 2.0;
- risk represented by text/marker/consequence;
- no domain/data imports in `core/ui`;
- existing launcher/router tests pass unchanged;
- device acceptance for URL, Play Store, SAFE, CONFIRM, Cancel, and external handoff.

Suggested gate:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:launcher:testDebugUnitTest :feature:permission_education:testDebugUnitTest testDebugUnitTest assembleDebug
```

## 7. Non-goals

- No new routing/action execution semantics.
- No new domain risk enum.
- No agent runtime, execution stream, Activity journal, or automation UI.
- No DS-6A sacred content.
- No DS-6B prayer correctness.
- No broad Assistant redesign.

## 8. Success Criteria

- Action/safety components are production-ready and screenshot-tested.
- `ConfirmActionCard` usage reaches zero or is explicitly deprecated pending zero usage.
- Permission Education matches the action/safety language without changing request behaviour.
- Result/error/offline/blocked surfaces exist for real states and preview-only future variants.
- Device acceptance proves consequential action parity.
