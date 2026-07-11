# DS-6A — Sacred Header Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** This plan adds the UI-only Shahada/Sacred Header foundation. It is
> safe to keep drafted now because it explicitly excludes prayer data, but production Home integration is
> gated after DS-3 controls, DS-4 Home, DS-5 action/safety, and Arabic rendering approval.

**Goal:** Implement and verify `SidrShahadaHeader` as a respectful, accessible, presentation-only `core/ui`
component, then integrate it into the DS-4 Home sacred-anchor slot after Arabic rendering gates pass.

**Spec:** `docs/superpowers/specs/2026-07-11-ds6a-sacred-header-design.md`.

## Prerequisites

- DS-3 controls implemented and green.
- DS-4 Home shell implemented or ready to expose the sacred-anchor slot.
- DS-5 Action and Safety implemented or explicitly deferred by owner decision.
- DS-1/DS-2 theme/primitives available.
- Owner approval for Arabic rendering before production Home integration.

The component can be built and screenshot-tested before DS-4 integration, but Home wiring should not happen
until DS-4 exists and DS-5 is not being bypassed accidentally.

## Global Constraints

- No prayer times, prayer summary, calculation, location, notifications, adhan, Qibla, or religious content
  feed.
- No network or startup work.
- No ViewModel/domain/data/persistence changes.
- No click/dismiss/navigation semantics on the sacred header.
- No truncation of approved text.
- No decorative religious motifs by default.
- No commits unless the owner explicitly asks.

## Task 1: Text Constants and Tests

**Purpose:** Protect the approved text before rendering it.

Files:

- `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrShahadaHeader.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrShahadaHeaderTest.kt`

Steps:

- [ ] Add `SIDR_SHAHADA_ARABIC`.
- [ ] Add `SIDR_SHAHADA_TRANSLATION_LINE_ONE`.
- [ ] Add `SIDR_SHAHADA_TRANSLATION_LINE_TWO`.
- [ ] Unit-test exact strings.
- [ ] Add a test comment warning that copy changes require owner approval.

Acceptance:

- constants match the spec exactly;
- no composable implementation is required yet;
- `./gradlew :core:ui:testDebugUnitTest --tests '*SidrShahadaHeaderTest'` passes.

## Task 2: Component Implementation

**Purpose:** Build `SidrShahadaHeader` as a deep presentation module.

Steps:

- [ ] Implement `SidrShahadaHeader` with the spec interface.
- [ ] Render Arabic and English as separate text nodes.
- [ ] Use `SidrText` / `SidrTextRole.SACRED` where appropriate, but override text direction for Arabic.
- [ ] Ensure no `clickable`, `selectable`, button role, or dismiss semantics.
- [ ] Add controlled spacing: at least 20dp horizontal padding and 24dp vertical breathing space.
- [ ] Add compact internal layout behaviour for narrow/low-height states without truncation.

Acceptance:

- component has no callbacks;
- no domain/data/feature imports;
- no animation/loading code;
- no card/surface frame by default.

## Task 3: Arabic Typography Decision Gate

**Purpose:** Decide whether platform typography is acceptable or a bundled Arabic font is required.

Steps:

- [ ] Capture screenshots using current `SidrTextStyles.sacred` / platform rendering.
- [ ] Inspect Arabic shaping, joining, baseline, and line height.
- [ ] Run RTL and mixed Arabic/LTR screenshot tests.
- [ ] If rendering is not acceptable, pick a bundled Arabic font only after license review.
- [ ] If adding a font, add it to `core/ui/src/main/res/font/` and document license provenance.
- [ ] Update `SidrTextStyles` or component-local Arabic style only after approval.

Acceptance:

- owner approves current rendering or selected font;
- no production Home integration before this gate passes.

## Task 4: Screenshot and Semantics Harness

**Purpose:** Make sacred rendering reviewable and regression-safe.

Files:

- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SacredHeaderGallery.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrShahadaHeaderScreenshotTest.kt`
- `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrShahadaHeaderSemanticsTest.kt`

Steps:

- [ ] Add gallery states:
      - Arabic + English;
      - English-only fallback;
      - dark;
      - light;
      - font-scale 1.5;
      - font-scale 2.0;
      - RTL locale;
      - narrow 360dp;
      - landscape.
- [ ] Add semantics tests proving no click/action semantics.
- [ ] Add reading-order expectations where testable.
- [ ] Add dependency guard entry if DS-3 guard does not already cover this file.

Acceptance:

- `:core:ui:testDebugUnitTest` green;
- `:core:ui:verifyRoborazziDebug` green with intentional goldens.

## Task 5: Home Integration Through DS-4 Slot

**Purpose:** Add the Sacred Header to Home without rewriting the launcher loop.

Prerequisite:

- DS-4 Home shell exposes a sacred-anchor slot or equivalent stable composition point.

Steps:

- [ ] Render `SidrShahadaHeader` in the Home sacred-anchor slot.
- [ ] Ensure Universal Input remains visible and accessible on a standard phone.
- [ ] Make typing/results compact the header only through deterministic layout, not animation or scroll tricks.
- [ ] Ensure App Drawer, Settings, Assistant, and Permission Education do not duplicate the Shahada.
- [ ] Keep first frame local-only: no network, no permissions, no prayer data.

Acceptance:

- Home idle screenshot matches sacred-header direction;
- Home typing/results still works;
- no routing/input/ViewModel changes;
- no first-frame spinner introduced.

## Task 6: Device and Accessibility Acceptance

Run after code + screenshots are green:

- [ ] Samsung device smoke, dark and light.
- [ ] 1.0/1.5/2.0 font scale.
- [ ] Arabic shaping visual check.
- [ ] RTL system locale smoke.
- [ ] landscape smoke.
- [ ] TalkBack reading order review.
- [ ] no click/dismiss action announced.
- [ ] Universal Input still reachable without frustrating scroll.

Acceptance:

- owner approves visual result;
- no sacred text truncation;
- no fake prayer data appears.

## Task 7: Documentation and Status

- [ ] Add DS-6A completion ADR only after gates pass.
- [ ] Update `ai-context/current-status.md`.
- [ ] Update `docs/design/artifacts/e34033dd/README.md` only if implementation intentionally deviates from
      artifact/DS-6A direction.
- [ ] Leave DS-6B explicitly unimplemented and separately scoped.

## Full Verification Gate

Core:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug
```

Integration:

```text
./gradlew :feature:launcher:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required before DS-6A is closed.

## Stop Conditions

Stop and re-scope if:

- Arabic rendering is poor and no approved font/license decision exists;
- implementation needs a new domain/data/persistence concept;
- prayer times or prayer summary work starts entering DS-6A;
- Home integration makes Universal Input hard to reach;
- sacred text truncates at 360dp or font-scale 2.0;
- TalkBack announces the header as clickable/actionable.
