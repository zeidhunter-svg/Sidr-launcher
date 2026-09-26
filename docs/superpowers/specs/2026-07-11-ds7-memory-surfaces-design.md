# DS-7 - Memory Surfaces (Design Spec)

> **Status: PROPOSED (2026-07-11).** Repository-grounded design for migrating existing SIDR memory
> management into the approved soft-classic-grey language.
>
> **Production prerequisite:** DS-3 controls and DS-5 action/safety should be implemented and green before
> production migration. DS-7 may be specified now, but it must not bypass the live DS-3 implementation
> already in progress.
>
> **Capability prerequisite:** Learned Choices are backed by S2-1 and may be the first production consumer.
> Aliases are conditional on S2-2/product scope; DS-7 can design their surface, but must not fabricate an
> Aliases UI before the caller and management flow are approved.
>
> **Governing sources:** `docs/design/SIDR Design System Master Plan.md` DS-7, `docs/design/SIDR Design
> Migration Plan v1.1.md` DS-7, `docs/design/SIDR Component Library v1.1.md` Memory Components,
> `docs/design/SIDR Visual Acceptance Spec v1.1.md` Learned Choices and Forget Memory Gate,
> `docs/design/artifacts/e34033dd/`, S2-1 Learned Resolutions implementation, and S2-2 Alias domain
> contracts where applicable.

## 1. Goal

Make SIDR memory visible, controllable, and honest:

- show what SIDR has learned or saved;
- show why it is used;
- show where it came from;
- show whether it is local-only;
- let the user edit or forget it when the backing capability supports that;
- distinguish active, learning, stale, unavailable, and needs-reconfirmation states;
- preserve delete-to-relearn behaviour for learned choices;
- avoid exposing internal fingerprints, raw database keys, or implementation confidence.

DS-7 is a **presentation and management migration**. It must not create new memory semantics merely to fill
a screen.

## 2. Current Production Baseline

Real backing today:

- S2-1 Learned Resolutions are closed and device-accepted.
- `LearnedChoicesScreen` exists under Settings and observes `LearnedChoiceView`.
- `LearnedChoicesViewModel` can delete learned choices through `DeleteLearnedChoiceUseCase`.
- `LearnedChoiceRow` exists in `core/ui`, but still reflects the earlier terminal row style.
- Alias domain/use-cases exist (`SaveAliasUseCase`, `ObserveAliasesUseCase`, `DeleteAliasUseCase`,
  `PruneUnavailableAliasesUseCase`, `ResolveCommandWithAliasUseCase`), but no production Aliases management
  screen is currently wired.

Important current issue:

- `feature/settings` currently imports domain memory display types directly for Learned Choices. DS-7 should
  introduce feature-local presentation models so `core/ui` and composables consume display-safe strings,
  status enums, callbacks, and slots.

## 3. Surface Scope

First production target:

- Settings -> Learned Choices migration to SIDR memory components.

Conditional production target:

- Aliases management, only after S2-2/product scope confirms how aliases are created, edited, deleted, and
  navigated to.

Preview-only / future:

- Memory main hub;
- global export/delete-all memory controls;
- temporary context list;
- saved facts beyond current alias contracts;
- cloud memory;
- agent/task memory;
- automation state memory.

The artifact's Memory card is a direction reference, not permission to implement future memory categories
without backing data.

## 4. Public Component Family

Components live in `core/ui/component` or `core/ui/pattern` if that package exists by DS-7 implementation.
They accept presentation-safe values only.

### 4.1 Memory Type and Status

```kotlin
enum class SidrMemoryType {
    LearnedPreference,
    ExplicitAlias,
    UserProvidedFact,
    TemporaryContext,
    SystemPolicy,
    AutomationState,
}

enum class SidrMemoryStatus {
    Active,
    Learning,
    NeedsConfirmation,
    NeedsReconfirmation,
    Inactive,
    Expired,
    Unavailable,
    Deleted,
}
```

Rules:

- `ExplicitAlias` may be implemented as a separate type or mapped to `UserProvidedFact` if the component
  library remains narrower; the UI copy must still make aliases legible to users.
- Status must be represented by label + marker, never colour alone.
- Status names shown to users should be human text, not raw enum names.

### 4.2 Memory Item

```kotlin
@Composable
fun SidrMemoryItem(
    title: String,
    value: String,
    type: SidrMemoryType,
    status: SidrMemoryStatus,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    provenance: String? = null,
    lastUsed: String? = null,
    localOnly: Boolean = true,
    leadingContent: (@Composable (() -> Unit))? = null,
    onOpen: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
)
```

Required anatomy:

```text
LEARNED PREFERENCE

"open bank" -> Turkiye Finans

Based on 3 confirmed choices
Last used 8 Jul 2026
LOCAL · ACTIVE

[ EDIT ] [ FORGET ]
```

Rules:

- one memory entity per row/card;
- local-only state is visible when true;
- evidence is human-readable;
- long phrases and target names wrap;
- forget is available but not the primary list affordance;
- the component does not know app package names, candidate fingerprints, Room entities, or policy classes;
- `leadingContent` may render an app icon, initial, or monogram supplied by the feature.

### 4.3 Memory Evidence

```kotlin
@Composable
fun SidrMemoryEvidence(
    evidence: String,
    modifier: Modifier = Modifier,
    provenance: String? = null,
    localOnly: Boolean = true,
)
```

Rules:

- use for compact evidence lines inside memory items or disclosures;
- keep evidence truthful and category-level;
- do not display raw streak thresholds if the user-facing copy can be clearer;
- do not expose internal candidate fingerprints.

### 4.4 Memory Disclosure

```kotlin
@Composable
fun SidrMemoryDisclosure(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    provenance: String? = null,
    onView: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
)
```

Show only on meaningful events:

- new stable learned preference;
- changed learned preference;
- new explicit alias;
- needs-reconfirmation state;
- stale or unavailable target that affects a user action.

Do not show:

- after every normal launch;
- after every evidence increment;
- as a permanent toast;
- as a vague "SIDR learned something" message.

### 4.5 Forget Gate

DS-7 consumes the DS-5 action/safety family for destructive-but-relearnable memory deletion.

```kotlin
@Composable
fun SidrForgetGate(
    title: String,
    consequence: String,
    onCancel: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    forgetting: Boolean = false,
)
```

Rules:

- state exact consequence;
- no vague "Delete data?";
- Cancel always visible;
- Forget disabled while deleting;
- delete failures do not crash the screen;
- no biometric confirmation for learned-choice/alias deletion unless a future memory type changes risk.

## 5. Feature Presentation Models

`core/ui` must not import:

- `ResolutionPreference`;
- `LearnedChoiceView`;
- `LearnedChoiceDisplayState`;
- `Alias`;
- `AliasView`;
- `MemoryPolicy`;
- Room entities;
- repository/store interfaces.

Feature modules map domain/use-case outputs to presentation models before composition.

Suggested Learned Choices presentation model:

```kotlin
data class LearnedChoiceMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String?,
    val status: SidrMemoryStatus,
    val statusLabel: String,
    val evidence: String,
    val provenance: String,
    val lastUsed: String?,
    val localOnly: Boolean,
)
```

Suggested Alias presentation model:

```kotlin
data class AliasMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String?,
    val status: SidrMemoryStatus,
    val provenance: String,
    val createdAt: String?,
    val localOnly: Boolean,
)
```

These are examples, not required names. The invariant is the boundary: presentation models are local to the
feature and do not leak into `core/ui`.

## 6. Learned Choices Mapping

Map current states to DS-7 language:

| Current state | DS-7 status | User-facing copy |
| --- | --- | --- |
| `Learning(streak, threshold)` | `Learning` | `Learning from confirmed choices` or `learning X/Y` |
| `NeedsReconfirm` | `NeedsReconfirmation` | `needs reconfirmation` |
| `Auto` | `Active` | `active` |
| `AutoReady` | `Active` | `active` or `ready` |
| `Unavailable` | `Unavailable` | `target unavailable` |

Delete-to-relearn copy:

```text
FORGET LEARNED CHOICE?

"open bank" will no longer prefer Turkiye Finans.
The next ambiguous request will ask you to choose again.
```

Rules:

- keep S2-1 delete semantics unchanged;
- do not delete on row swipe unless explicitly scoped and tested;
- do not auto-prune silently from a visible screen without preserving the existing best-effort behaviour;
- empty state explains that preferences appear only after confirmed choices.

## 7. Alias Mapping

Aliases are user-declared memory, not hidden profiling.

If S2-2 scopes Aliases management:

- show phrase;
- show target;
- show local-only provenance;
- show created/last-used date only if real;
- support edit and forget only if the backing use-cases exist;
- normalize display consistently with command normalization while preserving understandable copy.

Do not implement:

- alias suggestions without an approved signal;
- cloud alias sync;
- alias categories not backed by domain;
- edit UI that can create invalid or over-length phrases;
- hidden alias creation from passive usage.

## 8. Memory Main Surface

DS-7 may add component/gallery coverage for a future Memory surface, but production navigation should stay
with existing Settings destinations unless a real memory hub is explicitly scoped.

Minimum viable production path:

```text
Settings
  Learned choices
  Aliases (conditional after S2-2)
```

Future hub target:

```text
Memory
  Learned choices
  Aliases
  Facts and saved results
  Dismissed suggestions
  Export
  Delete all
```

Do not add Export/Delete all until data ownership and retention requirements are scoped.

## 9. Accessibility

Required:

- row/card has one coherent TalkBack summary;
- Forget action has a specific label, e.g. `Forget learned choice open bank`;
- local-only/provenance is announced;
- status is announced as text;
- app icons are decorative unless they add information not present in text;
- 2.0 font scale remains readable;
- long phrases and target labels wrap without overlap;
- RTL layout does not reverse the meaning of phrase-to-target copy.

## 10. Verification

Required tests/gates:

- active learned choice;
- learning learned choice;
- needs reconfirmation;
- unavailable target;
- empty state;
- delete/forget confirmation;
- delete failure does not crash;
- long phrase;
- long app name;
- local-only provenance;
- dark/light;
- font-scale 2.0;
- RTL smoke;
- TalkBack labels where testable;
- no domain/data imports in `core/ui`.

Suggested gate:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:settings:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-7 is closed.

## 11. Non-goals

- No new Room schema.
- No new memory policy.
- No cloud memory.
- No agent memory.
- No Activity journal.
- No Execution Stream.
- No global export/delete-all until separately scoped.
- No fake facts/saved results.
- No redesign of Assistant.
- No production Aliases screen before S2-2/product scope approves it.

## 12. Success Criteria

- Learned Choices use DS-7 memory components and soft-classic-grey controls.
- The user can understand what was learned, why it is used, and how to forget it.
- Forget uses a clear DS-5 gate and preserves delete-to-relearn behaviour.
- `core/ui` remains presentation-only.
- Aliases have a ready design path without being fabricated before S2-2.
- No hidden profiling language or unexplained saved data appears.
