# SIDR Design Migration Plan v1.1

**Статус:** Proposed implementation plan
**Цель:** поэтапно перевести существующий AIL-0 UI на SIDR Design System v1.1 без изменения поведения
**Главный инвариант:** visual migration must not alter routing, execution, privacy, memory or navigation semantics

---

# 1. Global constraints

Во всех блоках запрещено:

* менять domain behavior без отдельного плана;
* менять `HandleUserCommandUseCase`;
* менять `RouteCommandUseCase`;
* менять `ExecuteActionUseCase`;
* менять router-off/offline parity;
* добавлять cloud context;
* менять memory policy;
* менять Room/DataStore schema;
* добавлять preference keys без отдельного решения;
* добавлять `feature → feature`;
* добавлять `core/ui → domain`;
* передавать ViewModel/NavController в `core/ui`;
* автоматически выполнять AI proposal;
* объединять Assistant и Universal Input pipelines;
* добавлять фиктивный Execution Stream.

Каждый блок заканчивается:

```text
targeted unit tests
relevant screenshot tests
full testDebugUnitTest
:app:assembleDebug
architecture guard
device acceptance when screen behavior is touched
ADR / status update
clean commit
```

---

# 2. DS-0 — Documentation Sync & Repository Inventory

## Goal

Зафиксировать точную текущую UI-базу до кода.

## Allowed changes

* documentation only;
* component inventory;
* file path table;
* usage graph;
* screenshots/previews catalog;
* architecture document synchronization.

## Required outputs

```text
docs/design/sidr-design-audit.md
docs/design/sidr-design-doctrine-foundation-v1.1.md
docs/design/sidr-component-library-v1.1.md
docs/design/sidr-ui-inventory-gap-map.md
docs/design/sidr-visual-acceptance-spec-v1.1.md
docs/design/sidr-design-migration-plan-v1.1.md
```

## Required repository scan

* all `core/ui` Kotlin files;
* all public composables;
* all feature usages;
* raw Material components used directly by feature screens;
* theme files;
* font resources;
* preview files;
* screenshot/golden infrastructure;
* accessibility tests.

## Forbidden

* production code changes;
* renaming components;
* formatting unrelated files;
* adding dependencies.

## Verification

```text
git diff --stat
```

must show docs only.

## Commit

```text
docs: establish SIDR Design System v1.1 migration baseline
```

---

# 3. DS-1 — Foundation Tokens

## Goal

Add v1.1 tokens while preserving existing visual output as closely as practical.

## Scope

```text
SidrSemanticColors
SidrTypography
SidrSpacing
SidrSizes
SidrShapes
SidrStrokes
SidrElevation
SidrMotion
SidrCompositionLocals
```

## Key decisions

* retain Material 3 `ColorScheme`;
* add SIDR semantic colors alongside it;
* retain Green/Amber preference values;
* introduce Interface Sans;
* retain JetBrains Mono for System roles;
* add Arabic typography contract;
* no screen migration yet.

## Compatibility

Existing APIs should continue compiling.

Use additive migration first:

```text
old token
→ backed by new token where possible
```

## Tests

* Green/Amber × dark/light token tests;
* contrast checks for primary text and controls;
* typography role tests;
* reduced-motion token tests;
* no dynamic color default regression.

## Forbidden

* changing launcher layout;
* changing stored accent enum/value;
* deleting JetBrains Mono;
* bundling unapproved font licensing;
* styling Shahada before Arabic rendering test exists.

## Commit

```text
feat(ui): add SIDR v1.1 semantic foundation tokens
```

---

# 4. DS-2 — Primitive Layer

## Goal

Create stable low-level primitives.

## Components

```text
SidrSurface
SidrText
SidrDivider
SidrStatusMarker
SidrFocusRing
SidrProgress
```

## Rules

* no feature models;
* no user-facing hardcoded strings;
* role-based colors;
* font scale 2.0;
* RTL-safe;
* reduce motion.

## Tests

* dark/light previews;
* Green/Amber previews;
* RTL;
* font scale;
* disabled state;
* semantics.

## Forbidden

* migrating production screens in this block;
* introducing card wrappers around all content;
* custom canvas effects unless required.

## Commit

```text
feat(ui): add SIDR v1.1 presentation primitives
```

---

# 5. DS-3 — Core Controls

## Goal

Normalize reusable interactive controls.

## Components

```text
SidrPrimaryButton
SidrSecondaryButton
SidrTertiaryButton
SidrDestructiveButton
SidrTerminalAction
SidrIconButton

SidrRouteChip
SidrSuggestionChip
SidrActionChip
SidrStatusChip
SidrRiskChip
SidrFilterChip

SidrNavigationRow
SidrToggleRow
SidrChoiceRow
SidrStatusRow
SidrDestructiveRow

SidrSection
SidrSectionHeader
SidrAlphabetHeader
SidrTopBar
```

## Migration target

No major screen redesign. Migrate only the simplest call sites needed to prove APIs.

Recommended proof surface:

```text
SettingsScreen
```

because its behavior is stable and its raw M3 controls can be replaced without touching routing.

## Tests

* switch row single-toggle test;
* button loading disables duplicate click;
* 48 dp touch targets;
* chip selected/disabled states;
* long labels;
* 2.0 font scale.

## Forbidden

* preference changes;
* Settings ViewModel changes except presentation mapping if essential;
* moving system intents into ViewModel.

## Commit

```text
feat(ui): introduce SIDR v1.1 controls and settings rows
```

---

# 6. DS-4 — Universal Input Migration

## Goal

Replace `SidrCommandPrompt` presentation with `SidrUniversalInput`.

## Required behavior parity

The following must remain unchanged:

```text
blank input behavior
live app filtering
WEB route
SITE route
ASK route
rule-first matching
LLM planner trigger conditions
SAFE proposal behavior
CONFIRM proposal behavior
voice final transcript path
assistant prompt prefill
clear input behavior
IME submission
offline fallback
router-off parity
```

## Implementation

* create `SidrUniversalInput`;
* map existing state to presentation enum;
* preserve callbacks;
* retain old component temporarily if App Drawer or tests still use it;
* migrate LauncherScreen only;
* deprecate old component after zero usages.

## Visual states

```text
Idle
Focused
Typing
Listening
Interpreting
Ambiguous
Proposed
Executing
Error
Disabled
```

Only map states actually available. Do not invent false runtime states.

## Tests

* all existing LauncherViewModel tests unchanged;
* input semantics;
* prompt marker not read as “greater than”;
* microphone state;
* clear action;
* IME;
* route chip layout;
* font scale 2.0;
* RTL safety;
* screenshot states.

## Device acceptance

* type and launch app;
* unknown natural-language route;
* SAFE routed proposal;
* CONFIRM routed proposal;
* Cancel;
* voice permission path;
* offline;
* router disabled.

## Commit

```text
feat(launcher-ui): migrate home input to SidrUniversalInput
```

---

# 7. DS-5 — Action & Safety Family

## Goal

Replace `ConfirmActionCard` with semantic Action Gate components.

## Components

```text
SidrActionProposal
SidrActionGate
SidrPermissionNotice
SidrPrivacyNotice
SidrResultSurface
SidrErrorSurface
SidrBlockedState
SidrOfflineState
```

## Migration

* map current pending routed action to presentation model in `feature/launcher`;
* preserve confirm/cancel callbacks;
* migrate Permission Education presentation;
* keep all domain enums outside `core/ui`.

## Required variants

```text
SAFE proposal
CONFIRM
External handoff
Permission
Sensitive data
Destructive
Blocked
```

Only current CONFIRM/SAFE paths become production active. Other variants can remain preview-tested until used.

## Tests

* no confirm on dismiss;
* Cancel callback exactly once;
* Confirm callback exactly once;
* loading prevents duplicate action;
* consequence always visible;
* vertical buttons at large font;
* risk represented by text/icon, not color only.

## Device acceptance

Mandatory for:

* URL confirmation;
* Play Store confirmation;
* SAFE action proposal;
* cancel;
* external app handoff.

## Commit

```text
feat(ui): migrate routed actions to SIDR Action Gate
```

---

# 8. DS-6 — Sacred Home Foundation

## Goal

Add the Islamic spiritual foundation to Home without blocking startup.

## Components

```text
SidrShahadaHeader
SidrPrayerSummary
```

## Scope split

### DS-6A — UI-only components

* Arabic rendering;
* English translation;
* RTL;
* prayer summary states;
* static preview data;
* Home layout slots.

### DS-6B — Prayer data architecture

Must have a separate approved technical plan covering:

* location source;
* official/local authority;
* calculation method;
* offline cache;
* timezone;
* privacy;
* manual override;
* update schedule;
* errors.

Do not implement prayer calculation casually inside `LauncherScreen`.

## Home integration requirements

* Shahada is noninteractive;
* no animation;
* no truncation;
* no card treatment by default;
* no remote request before first frame;
* cached prayer data only on startup;
* Universal Input remains accessible;
* low-height fallback must remain respectful.

## Tests

* Arabic RTL rendering;
* English LTR translation;
* 2.0 font scale;
* narrow screen;
* landscape;
* TalkBack order;
* no click semantics;
* no startup network requirement.

## Device acceptance

* normal phone;
* large font;
* dark/light;
* Green/Amber;
* no location permission;
* cached/no-data prayer states.

## Commit

```text
feat(home): add SIDR sacred header foundation
```

Prayer architecture lands in a separate later commit.

---

# 9. DS-7 — Memory Components & Learned Choices Migration

## Goal

Make Learned Resolutions the first real SIDR memory surface.

## Components

```text
SidrMemoryItem
SidrMemoryDisclosure
SidrMemoryStatus
```

## Preserve

* local-only data;
* current Room schema;
* current policy;
* delete-to-relearn;
* status truth;
* no cloud context.

## UI mapping

Feature maps resolution state to:

```text
Active
NeedsReconfirmation
Inactive
Expired
```

No domain dependency enters `core/ui`.

## Tests

* active preference;
* needs reconfirmation;
* empty state;
* delete confirmation;
* long app names;
* 2.0 font scale;
* local-only label;
* TalkBack description.

## Commit

```text
feat(memory-ui): migrate learned choices to SIDR memory patterns
```

---

# 10. DS-8 — Activity Foundations

## Goal

Prepare honest action history UI.

## Components

```text
SidrActivityItem
SidrActivityTimeline
```

## Important boundary

Do not create new persistence merely to populate the screen.

First define which existing records can be safely shown:

* command categories;
* proposals;
* confirmation;
* execution result;
* learned preference events.

Raw sensitive user content remains excluded according to existing privacy rules.

## Status

This block may remain component-only until an approved Activity domain/use-case plan exists.

## Commit

```text
feat(ui): add SIDR activity presentation patterns
```

---

# 11. DS-9 — Execution Foundations

## Goal

Prepare future multi-step agentic UI.

## Components

```text
SidrExecutionPlan
SidrExecutionStep
SidrExecutionStream
```

## Hard restriction

No production use until Stage 2/3 has a real execution model.

Do not map a single network request to a fake three-step plan.

## Allowed work

* presentation models;
* previews;
* screenshot tests;
* accessibility;
* component API review.

## Commit

```text
feat(ui): add future SIDR execution presentation contracts
```

---

# 12. DS-10 — Assistant Migration

## Goal

Apply v1.1 typography and privacy language to Assistant.

## Preserve

* streaming;
* cancellation;
* retry;
* provider form;
* transient prompt/reply;
* no SavedStateHandle;
* secure secret behavior.

## Changes

* conversation uses Interface Sans;
* technical provider metadata uses Mono;
* provider/cloud disclosure uses `SidrPrivacyNotice`;
* errors use `SidrErrorSurface`;
* controls use SIDR buttons/rows.

## Commit

```text
feat(assistant-ui): migrate assistant to SIDR v1.1 components
```

---

# 13. Release gates

Before declaring Design System v1.1 adopted:

```text
[ ] no core/ui → domain dependency
[ ] no new feature → feature dependency
[ ] router parity tests green
[ ] privacy guards green
[ ] learned-resolution tests green
[ ] full unit suite green
[ ] assembleDebug green
[ ] assembleRelease green
[ ] Home device acceptance green
[ ] confirmation device acceptance green
[ ] large-font smoke green
[ ] Arabic/RTL smoke green
[ ] no cold-start regression caused by visual effects
```

---

# 14. Recommended first agent assignment

Do not assign the entire plan.

First assignment:

```text
DS-0 + DS-1 only
```

Deliverables:

* exact inventory;
* documentation sync;
* semantic token implementation;
* compatibility previews;
* tests;
* no screen redesign.

After review, continue with DS-2.
