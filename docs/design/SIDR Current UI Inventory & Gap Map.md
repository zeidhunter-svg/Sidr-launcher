# SIDR Current UI Inventory & Gap Map

**Версия:** 1.0
**Назначение:** зафиксировать существующий UI SIDR до начала Design System v1.1 migration
**Основание:** текущее состояние Stage 1, ADR AIL-0…6, AppNavHost и реализованные feature-поверхности

---

# 1. Главный вывод

Текущий SIDR UI уже содержит рабочую первую дизайн-систему и несколько специализированных компонентов.

Поэтому миграция должна выполняться по модели:

```text
reuse
→ normalize
→ evolve
→ replace only when necessary
```

а не:

```text
delete everything
→ rebuild from zero
```

Главные риски:

* создание новых компонентов рядом со старыми;
* дублирование `SidrCommandPrompt` и будущего `SidrUniversalInput`;
* сохранение двух confirmation systems;
* смешение старого full-monospace AIL-0 и новой dual-font системы;
* изменение поведения Universal Input во время presentation migration;
* перенос domain-моделей в `core/ui`;
* преждевременное добавление Agent/Execution UI без реальной модели выполнения.

---

# 2. Архитектурный inventory

## 2.1. `:app`

### Существует

* единый `AppNavHost`;
* composition root;
* Hilt graph;
* navigation event collection;
* safe fallback navigation;
* theme application;
* Launcher, App Drawer, Assistant, Settings, Learned Choices и Permission Education destinations.

### Сохраняется

* single `NavHost`;
* `NavigationEvent` flow;
* отсутствие `NavController` во ViewModel;
* safe fallback;
* app-level theme observation.

### Не переносить в Design System

* navigation routing;
* Hilt wiring;
* destination registration;
* feature ViewModel creation;
* permission contracts.

### Gap

Навигация пока использует string routes. Это допустимо для текущей миграции. Typed destinations не входят в Design System v1.1 scope.

---

## 2.2. `:core:ui`

### Текущая роль

* общая тема;
* визуальные токены AIL-0;
* переиспользуемые Compose-компоненты;
* launcher primitives;
* confirmation surface;
* empty/error states.

### Подтверждённые существующие элементы

```text
SidrTheme
SidrScaffold
SidrCommandPrompt
SidrSearchField
RouteChipRow
ConfirmActionCard
SectionHeader
TopBarIcon
AppTile
EmptyState
ErrorState
```

Могут существовать дополнительные небольшие helpers, которые агент обязан обнаружить в CL-0 repository inventory.

### Сильные стороны

* presentation-only подход;
* `ConfirmActionCard` не зависит от domain;
* `AppTile` использует icon slot;
* theme централизована;
* green/amber варианты уже реализованы;
* JetBrains Mono уже подключён;
* компоненты реально используются на production surfaces.

### Главный gap

`core/ui` пока представляет собой:

```text
theme
+ launcher components
```

а должен стать:

```text
tokens
+ primitives
+ components
+ semantic patterns
+ layouts
+ accessibility contracts
```

---

# 3. Component-by-component map

## 3.1. `SidrTheme`

### Current

AIL-0 theme:

* green default;
* amber alternative;
* dark/light schemes;
* dynamic color disabled by default;
* JetBrains Mono;
* brutalist shapes;
* terminal identity.

### Decision

**EVOLVE**

### Target

```text
SidrTheme v1.1
├── Material ColorScheme
├── SidrSemanticColors
├── SidrTypography
├── SidrSpacing
├── SidrShapes
├── SidrSizes
├── SidrStrokes
└── SidrMotion
```

### Preserve

* Green/Amber user preference;
* dark-first behavior;
* existing preference persistence;
* current app-level theme owner;
* startup behavior;
* no white boot flash.

### Change

* add dual-font model;
* soften neon values;
* add semantic role colors;
* add motion/reduce-motion tokens;
* add Arabic sacred typography;
* remove global cyberpunk assumptions.

### Forbidden

* changing preference storage keys;
* changing theme selection behavior;
* enabling dynamic color by default;
* moving theme observation into a feature.

---

## 3.2. `SidrScaffold`

### Current

Used by Launcher-related and settings surfaces as a general screen shell.

### Decision

**EVOLVE IN PLACE**

### Target

Add variants:

```text
Standard
Home
Focused
Detail
Immersive
```

### Preserve

* edge-to-edge compatibility;
* system insets;
* current call-site behavior;
* stable first frame.

### Gap

No explicit semantic distinction between Home, detail and focused agentic surfaces.

### Forbidden

* multiple unrelated scaffold implementations;
* embedding navigation behavior;
* owning screen business state.

---

## 3.3. `SidrCommandPrompt`

### Current

AIL-3 Universal Input presentation:

* terminal `>`;
* command field;
* route integration;
* live app filtering;
* WEB/ASK/SITE paths;
* block caret polish;
* voice path reuse.

### Decision

**EVOLVE → `SidrUniversalInput`**

### Preserve exactly

* ViewModel input contract;
* `onValueChange`;
* submit behavior;
* voice flow;
* app filtering;
* rule matcher path;
* LLM router path;
* assistant prefill behavior;
* IME behavior;
* offline parity.

### Add

* formal presentation state;
* supporting text;
* error/listening/interpreting states;
* new typography roles;
* stronger accessibility semantics;
* reduce-motion support.

### Migration method

First add `SidrUniversalInput` as a compatible presentation wrapper. Migrate the launcher call site. Remove `SidrCommandPrompt` only after all usages disappear.

### Forbidden

* changing `LauncherViewModel`;
* changing input normalization;
* changing command submission timing;
* auto-submitting voice partial text;
* sending any new cloud context.

---

## 3.4. `SidrSearchField`

### Current

Used by App Drawer for local live filtering.

### Decision

**KEEP, NORMALIZE**

### Preserve

* local-only filtering;
* clear action;
* no command routing;
* no voice on App Drawer;
* top-match IME submit behavior.

### Target

Make it a standard search variant built from shared input primitives, but do not merge its semantics with Universal Input.

```text
SidrUniversalInput ≠ SidrSearchField
```

---

## 3.5. `RouteChipRow`

### Current

Displays Universal Input routes such as WEB, ASK and SITE.

### Decision

**SPLIT RESPONSIBILITIES**

### Keep at feature level

* row/FlowRow composition;
* ordering;
* route-specific data mapping.

### Move to `core/ui`

```text
SidrRouteChip
SidrSuggestionChip
SidrActionChip
SidrStatusChip
SidrFilterChip
SidrRiskChip
```

### Forbidden

A single universal chip API with many nullable parameters.

---

## 3.6. `ConfirmActionCard`

### Current

DF-4 confirmation block:

* `EXECUTE?`;
* risk chip;
* command line;
* Cancel;
* Confirm;
* no domain dependency;
* used only for routed proposals;
* rule-only path remains untouched.

### Decision

**EVOLVE → `SidrActionGate`**

### Preserve exactly

* no auto-execution;
* explicit Cancel;
* explicit Confirm;
* proposal/execution distinction;
* current callbacks;
* risk-gated behavior;
* rule-path parity.

### Expand presentation family

```text
Confirmation
Permission
SensitiveData
ExternalHandoff
Destructive
```

### Important

Domain risk enum must be mapped inside feature code to a presentation enum.

### Forbidden

* `core/ui` importing `LauncherAction`;
* confirmation dialog dismiss counting as approval;
* automatic action after animation;
* hiding consequences behind expansion.

---

## 3.7. `SectionHeader`

### Current

Used in Home, App Drawer sticky headers and Settings.

### Decision

**SPLIT**

One component currently serves multiple semantic roles.

Target:

```text
SidrSectionHeader
SidrAlphabetHeader
SidrSystemLabel
```

### Reason

An alphabetical sticky header and a semantic Settings section are not the same component.

---

## 3.8. `TopBarIcon`

### Current

Used for Back, Settings, Assistant and screen actions.

### Decision

**KEEP, RENAME OR WRAP**

Target:

```text
SidrIconButton
```

`SidrTopBar` remains a composition.

### Preserve

* 48 dp touch target;
* content description;
* icon slot;
* current callbacks.

---

## 3.9. `AppTile`

### Current

Used for Favorites and app display.

### Decision

**KEEP AND NORMALIZE**

### Preserve

* icon slot;
* label;
* app launch callback;
* no package-manager access inside `core/ui`.

### Add

* selected state;
* optional badge slot;
* long-click callback;
* formal semantics;
* font-scale behavior.

### Do not add

* usage repository;
* learned-resolution logic;
* package resolution;
* app launch executor.

---

## 3.10. `EmptyState`

### Current

Used by launcher and drawer.

### Decision

**KEEP BASE, ADD SEMANTIC SIBLINGS**

Target:

```text
SidrEmptyState
SidrBlockedState
SidrOfflineState
```

Do not encode all situations into one giant enum immediately if separate APIs remain simpler.

---

## 3.11. `ErrorState`

### Current

Recoverable UI error with Retry.

### Decision

**EVOLVE → `SidrErrorSurface`**

Target error roles:

```text
Recoverable
Permission
Offline
Unsupported
NotFound
Provider
Unknown
```

### Preserve

* safe messages;
* no thrown errors to UI;
* retry only where meaningful.

---

# 4. Feature surface map

## 4.1. `LauncherScreen`

### Current responsibilities

* Home composition;
* Universal Input;
* app-filter results;
* routed action confirmation;
* suggestions;
* Favorites;
* All Apps;
* setup nudge;
* voice entry;
* feedback;
* permission education routing.

### Decision

**MIGRATE IN PHASES**

### Current risk

This is the highest-risk visual migration because it touches the central interaction loop.

### Target composition

```text
SidrScaffold(Home)
├── Sacred Header
├── Prayer Summary
├── Universal Input
├── Input Results
├── Pending Action / Confirmation
├── Relevant Context
├── Favorites
└── All Apps
```

### Forbidden during initial migration

* new business state;
* prayer repository in the same task as input migration;
* changes to routing;
* changes to learned-resolution behavior;
* execution stream pretending to represent single-step actions.

---

## 4.2. `AppDrawerScreen`

### Current

* own ViewModel;
* local search;
* sticky alphabetical headers;
* compact app rows;
* empty/error states;
* Ask Assistant affordance;
* no command routing.

### Decision

**KEEP STRUCTURE, RESKIN LATER**

### Target components

```text
SidrScaffold(Standard)
SidrTopBar
SidrSearchField
SidrAlphabetHeader
SidrAppRow
SidrEmptyState
SidrErrorSurface
```

### No redesign of behavior required.

---

## 4.3. `SettingsScreen`

### Current

* real feature module;
* theme;
* accent;
* AI suggestions;
* router toggle;
* voice;
* favorites count;
* assistant provider;
* learned choices;
* permissions;
* set as default.

### Decision

**RECOMPOSE, DO NOT REBUILD VIEWMODEL**

### Target

```text
SidrSection
SidrToggleRow
SidrChoiceRow
SidrNavigationRow
SidrStatusRow
SidrDestructiveRow
```

### Gap

Current screen uses a mixture of SIDR shell and raw M3 radio/switch/button components.

### Forbidden

* changing preference contracts;
* adding new settings keys during visual migration;
* moving system intents into ViewModel;
* duplicating assistant provider form.

---

## 4.4. `LearnedChoicesScreen`

### Current

* management surface for learned resolutions;
* reached from Settings;
* separate NavHost destination;
* on-device memory management.

### Decision

**FIRST PRODUCTION USER OF MEMORY COMPONENTS**

### Target

```text
SidrMemoryItem
SidrStatusChip
SidrEmptyState
SidrActionGate for Forget
```

### Preserve

* current read/delete behavior;
* local-only semantics;
* delete-to-relearn behavior;
* honest status.

### Gap

No clear memory taxonomy or local/evidence visual language.

---

## 4.5. `AssistantScreen`

### Current

* first-run provider form;
* streaming conversation;
* provider settings;
* retry/cancel;
* optional initial prompt;
* no SavedStateHandle;
* transient prompt/reply.

### Decision

**OUTSIDE FIRST MIGRATION WAVE**

### Reason

Assistant has separate interaction semantics and can be migrated after foundational controls are stable.

### Future target

* Interface Sans for conversation;
* Mono only for provider/status metadata;
* `SidrPrivacyNotice`;
* `SidrResultSurface`;
* `SidrErrorSurface`;
* `SidrUniversalInput` variant or separate conversation composer.

Do not merge Assistant and launcher ViewModels.

---

## 4.6. `PermissionEducationScreen`

### Current

* education before request;
* no fake system dialog;
* user-triggered request flow;
* feature-specific dismissal;
* optional permission only.

### Decision

**MIGRATE TO PERMISSION FAMILY**

Target:

```text
SidrPermissionNotice
SidrPrivacyNotice
SidrPrimaryButton
SidrTertiaryButton
```

### Preserve

* education/request separation;
* Not now;
* no permission request at startup;
* feature-local denial behavior.

---

## 4.7. `SuggestionsRow`

### Current

* stateless UI;
* host owns state;
* suggestions are privacy-filtered;
* known routes/launchable apps only.

### Decision

**KEEP FEATURE COMPOSITION**

The row can use `SidrSuggestionChip`, but should remain in `feature/suggestions`.

---

# 5. Missing component families

Not currently complete:

```text
semantic colors
dual typography
Arabic sacred typography
status marker family
privacy notice
memory item
memory disclosure
activity timeline
action proposal
action gate family
result surface
partial result
execution step
execution stream
responsive pane
bottom navigation
prayer summary
Shahada header
```

Not all should be implemented immediately.

---

# 6. Implementation classification

## Implement now

```text
Theme semantic tokens
Dual typography
Primitives
Buttons
Chips
Rows
Universal Input migration
Action Gate migration
Settings visual normalization
Memory components
Shahada Header
Prayer Summary UI-only contract
```

## Specify now, implement when used

```text
Activity Item
Execution Step
Execution Stream
Responsive Pane
Bottom Navigation
Agent cards
Automation surfaces
```

## Do not implement yet

```text
Fake multi-step execution
Agent dashboard without agents
Automation editor
Global five-tab OS navigation
Decorative prayer animations
```

---

# 7. Deletion candidates

Delete only after usage reaches zero:

```text
SidrCommandPrompt
old generic RouteChip implementation
ConfirmActionCard
old SectionHeader variants
global CRT overlay
full-monospace long-form styles
duplicate raw Material buttons/rows in migrated screens
```

No immediate mass deletion.

---

# 8. Documentation inconsistencies

`architecture.md` contains stale references that still describe `feature/settings` as planned, while the real module and destinations already exist.

Before implementation:

* update module layout;
* mark Settings built;
* mark Learned Choices built;
* update current design-system identity;
* link Doctrine and Component Library;
* identify CLAUDE.md as current session digest.

---

# 9. Inventory acceptance criteria

Inventory is complete only after the agent produces:

1. A list of every public `@Composable` in `core/ui`.
2. Exact file paths.
3. Every usage site.
4. Screenshot or preview coverage.
5. Classification:

```text
KEEP
EVOLVE
SPLIT
DEPRECATE
DELETE LATER
```

6. Confirmation that no feature-specific logic lives in `core/ui`.
7. Confirmation that the repository is unchanged except documentation in DS-0.
