# DS-7 - Memory Surfaces Implementation Plan

> **STATUS: DONE — device-accepted on SM-A325F 2026-08-10.** Implemented on `launcher--7` on 2026-07-13
> (`8e3f317` components + `b2affdd` Settings/Aliases wiring); verification gate re-run green and the
> device pass driven on 2026-08-10. Task 6 (memory disclosure) shipped **preview-only** — no honest
> "stable preference formed" event exists to hang it on, which this plan explicitly permits. Task 7
> (Aliases surface) shipped together with the S2-2 last mile. Not covered on device: unavailable-target
> prune (would require disabling one of the owner's apps) and a live TalkBack session (the accessibility
> tree was read instead). ADR: `ai-context/decisions.md` "2026-08-10 — DS-7 Memory Surfaces + S2-2
> Explicit Aliases complete (device-accepted)".

**Goal:** Make Learned Choices the first honest SIDR memory surface, prepare the Aliases surface for S2-2,
and keep all memory presentation local, inspectable, and deletable.

**Spec:** `docs/superpowers/specs/2026-07-11-ds7-memory-surfaces-design.md`.

## Prerequisites

- DS-3 controls implemented and green.
- DS-5 action/safety implemented and green enough to provide `SidrForgetGate`/confirmation behaviour.
- S2-1 Learned Resolutions remains closed and tests are green.
- S2-2 Aliases product scope exists before implementing Alias management UI.
- Existing Settings/Learned Choices tests green before edits.

DS-7 docs were prepared after DS-6B was re-added as a separate spec/plan. Closing DS-7 as implemented should
follow the owner-approved design-track order or an explicit owner reprioritization.

## Global Constraints

- No commits unless the owner explicitly asks.
- Do not edit active DS-3 implementation files while another agent is working there.
- No new memory persistence, Room schema, policy, or learning semantics.
- No cloud memory.
- No fake Memory hub categories.
- No global export/delete-all until data ownership and retention scope is approved.
- No domain/data imports in `core/ui`.
- No hidden profiling language.
- Delete/forget remains reversible through relearning for learned choices.

## Task 1: Baseline Inventory

**Purpose:** Capture current memory behaviour before changing presentation.

- [ ] Use CodeGraph to inspect `LearnedChoicesScreen`, `LearnedChoicesViewModel`, `LearnedChoiceRow`,
      `ObserveLearnedChoicesUseCase`, `DeleteLearnedChoiceUseCase`, Alias use-cases, and navigation routes.
- [ ] Record current visible states:
      - loading;
      - empty;
      - error/retry;
      - active/auto-ready learned choices;
      - learning;
      - needs reconfirmation;
      - unavailable target/pruned target.
- [ ] Record current callbacks:
      - back;
      - retry;
      - delete learned choice.
- [ ] Run baseline tests:

```text
./gradlew :feature:settings:testDebugUnitTest :domain:test
```

Acceptance:

- no code edited yet;
- implementation notes list current behaviour and any missing tests.

## Task 2: Core UI Memory Gallery

**Purpose:** Add reviewable DS-7 components before production migration.

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrMemoryItem.kt`
- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrMemoryDisclosure.kt`
- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrForgetGate.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/MemoryGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrMemoryScreenshotTest.kt`
- optional semantics tests beside the screenshot tests.

States:

- learned preference active;
- learned preference learning;
- needs reconfirmation;
- unavailable;
- explicit alias preview;
- disclosure;
- forget gate;
- empty;
- delete failure message if a surface action is supplied;
- long phrase;
- long app name;
- dark/light;
- font-scale 2.0;
- RTL smoke.

Acceptance:

- `core/ui` components take display-safe values only;
- no domain/data/feature imports in `core/ui`;
- screenshots are useful for owner visual review.

## Task 3: Feature Presentation Mapper for Learned Choices

**Purpose:** Move domain display details out of the composable layer.

Files:

- `feature/settings/src/main/java/com/sidr/launcher/feature/settings/LearnedChoiceMemoryUiModel.kt`
- mapper file in the same package, or private mapper functions inside the ViewModel if simpler.

Steps:

- [ ] Map `LearnedChoiceView` to a feature-local memory UI model.
- [ ] Convert `LearnedChoiceDisplayState` into `SidrMemoryStatus` and display text inside the feature.
- [ ] Build human evidence copy:
      - learning: `Learning from confirmed choices` or `learning X/Y`;
      - active: `Based on confirmed choices`;
      - needs reconfirmation: `Needs reconfirmation before auto-open`;
      - unavailable: `Target unavailable`.
- [ ] Keep stable row IDs derived from safe, existing keys.
- [ ] Preserve package name only for icon lookup in the feature; never pass it into `core/ui` as memory
      identity.

Acceptance:

- `LearnedChoicesContent` no longer takes domain display models directly if practical within scope;
- core components receive only UI models/strings/status;
- ViewModel tests cover mapping for active, learning, needs reconfirmation, and unavailable.

## Task 4: Migrate Learned Choices Screen

**Purpose:** Make the existing production Settings surface match DS-7.

Steps:

- [ ] Replace `LearnedChoiceRow` usage with `SidrMemoryItem`.
- [ ] Use DS-3/DS-5 controls for actions.
- [ ] Keep current top-level route and navigation.
- [ ] Keep loading/empty/error semantics.
- [ ] Empty copy:

```text
No learned choices yet
Preferences appear only after confirmed choices.
Stored only on this device.
```

- [ ] Show local-only provenance on each item.
- [ ] Keep app icon rendering as a feature-local leading slot.
- [ ] Ensure long names wrap at 360dp and font-scale 2.0.

Acceptance:

- existing Settings navigation still reaches Learned Choices;
- no learned choice is forgotten from row render/focus;
- no broad Settings redesign happens in this task.

## Task 5: Forget Gate Integration

**Purpose:** Replace direct destructive row delete with an explicit consequence gate.

Steps:

- [ ] Add screen state for the pending item to forget.
- [ ] Show `SidrForgetGate` or DS-5 `SidrActionGate` variant.
- [ ] Copy:

```text
Forget learned choice?
"open bank" will no longer prefer Turkiye Finans.
The next ambiguous request will ask you to choose again.
```

- [ ] Cancel clears pending state.
- [ ] Forget calls existing `DeleteLearnedChoiceUseCase`.
- [ ] Disable Forget while deletion is running if state is modeled.
- [ ] Delete failure does not crash; show a retryable/safe error if reliable.

Acceptance:

- Cancel does not delete;
- Forget deletes exactly once;
- delete-to-relearn behaviour preserved;
- no biometric/system confirmation added.

## Task 6: Memory Disclosure Hook

**Purpose:** Prepare meaningful learning disclosures without noisy toasts.

Steps:

- [ ] Identify whether a current post-choice screen state can honestly show a new stable learned
      preference.
- [ ] If a real event is available, add `SidrMemoryDisclosure` after the meaningful event only.
- [ ] If no real event is available, keep disclosure preview-only and document why.

Acceptance:

- no disclosure after every ordinary launch;
- no disclosure after every evidence increment;
- no fake "SIDR learned" message.

## Task 7: Conditional Aliases Surface

**Purpose:** Make the S2-2 Alias UI ready without fabricating a product path.

Start only after S2-2/product scope exists.

- [ ] Confirm how aliases are created:
      - explicit command;
      - Settings add flow;
      - clarification flow;
      - other approved path.
- [ ] Add an Alias management route only if navigation is approved.
- [ ] Add Alias UI model and mapper from `AliasView`.
- [ ] Render aliases as `SidrMemoryItem` with `ExplicitAlias` or approved equivalent type.
- [ ] Support delete through `DeleteAliasUseCase`.
- [ ] Support edit only if validation and save flow are scoped.
- [ ] Add prune-unavailable behaviour tests if the screen invokes pruning.

Forbidden:

- no hidden alias creation from passive usage;
- no cloud sync;
- no alias suggestions without a signal;
- no invalid/over-length phrase creation.

Acceptance:

- aliases are legible as user-declared memory;
- local-only provenance visible;
- no Alias UI ships before backing flow is real.

## Task 8: Accessibility and Screenshot Acceptance

Run after component and screen migration:

- [ ] active learned choice;
- [ ] learning learned choice;
- [ ] needs reconfirmation;
- [ ] empty state;
- [ ] forget gate;
- [ ] long phrase;
- [ ] long target label;
- [ ] local-only provenance;
- [ ] dark/light;
- [ ] font-scale 2.0;
- [ ] RTL smoke;
- [ ] TalkBack pass for row summary and Forget action.

Acceptance:

- no text overlap;
- status is not colour-only;
- Forget action has a specific label;
- app icon is decorative unless needed.

## Task 9: Device Acceptance

Mandatory before DS-7 is closed:

- [ ] create/observe a learned choice through the real S2-1 flow;
- [ ] open Settings -> Learned Choices;
- [ ] verify active/local-only/evidence copy;
- [ ] forget a learned choice;
- [ ] repeat ambiguous command and confirm SIDR asks again;
- [ ] verify long app label layout if available;
- [ ] font-scale 2.0 smoke;
- [ ] dark/light smoke;
- [ ] TalkBack smoke.

Alias acceptance is separate and only applies after S2-2 UI scope.

## Task 10: Documentation and Status

- [ ] Add DS-7 completion ADR only after implementation and device acceptance.
- [ ] Update `ai-context/current-status.md`.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` if the implementation intentionally deviates from
      the artifact memory direction.
- [ ] Mark old `LearnedChoiceRow` deprecated only after production usage reaches zero.
- [ ] Keep S2-2 Alias scope separate if it is not implemented in this block.

## Full Verification Gate

Core:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
```

Feature/domain:

```text
./gradlew :feature:settings:testDebugUnitTest :domain:test testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-7 is closed.

## Stop Conditions

Stop and re-scope if:

- implementation needs a new Room schema or memory policy;
- `core/ui` needs to import domain/data classes;
- Aliases UI needs product decisions that S2-2 has not made;
- a Memory hub would contain fake categories;
- Forget semantics differ from delete-to-relearn;
- deletion can happen without explicit user confirmation;
- DS-3 active implementation files would need concurrent edits.

## Agent Start Prompt

Use this prompt for a new coding session:

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Implement DS-7 Memory Surfaces from:
- docs/superpowers/specs/2026-07-11-ds7-memory-surfaces-design.md
- docs/superpowers/plans/2026-07-11-ds7-memory-surfaces.md

First confirm DS-3 controls and DS-5 action/safety are implemented and green. If another agent is still
editing DS-3, do not touch those files. Use CodeGraph before reading/editing code.

Scope:
- migrate Settings -> Learned Choices to DS-7 memory components;
- preserve S2-1 learned-resolution semantics and delete-to-relearn;
- introduce feature-local presentation models/mappers so core/ui does not import domain memory classes;
- add screenshot/semantics coverage for memory item, disclosure, and forget gate;
- keep Aliases UI conditional until S2-2/product scope exists.

Forbidden:
- no commits;
- no new Room schema, memory policy, cloud memory, fake Memory hub, Activity, Agent, or Execution UI;
- no direct delete without an explicit forget gate;
- no domain/data imports in core/ui.

Before final: run the relevant Gradle gates or report exactly why they could not run.
```
