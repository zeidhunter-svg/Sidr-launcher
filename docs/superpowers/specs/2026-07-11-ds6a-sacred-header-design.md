# DS-6A — Sacred Header (Design Spec)

> **Status: PROPOSED (2026-07-11).** Design spec for adding SIDR's sacred Home anchor without introducing
> prayer-time data, location, calculation, notifications, or any fake religious state.
>
> **Prerequisites:** DS-3 controls, DS-4 Home shell, and DS-5 action/safety migration should be implemented
> before production integration. The component may be built and screenshot-tested earlier in `core/ui`, but
> Home wiring should use the DS-4 sacred-anchor slot and should not bypass DS-5 in the sequence.
>
> **Governing sources:** `docs/design/SIDR Design System Master Plan.md` DS-6A, `docs/design/SIDR Design
> and Architecture Audit.md` "Shahada и Home", `docs/design/SIDR Component Library v1.1.md` Sacred
> Components, `docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md`, and the
> `docs/design/artifacts/e34033dd/` Home captures.

## 1. Goal

Add a respectful, stable, accessible Sacred Header to Home. This is the Islamic spiritual anchor of SIDR,
not a brand slogan, control, animation, widget, or decorative pattern.

DS-6A is **UI-only**:

- no prayer times;
- no calculation method;
- no location permission;
- no notifications;
- no adhan/alarm;
- no Qibla;
- no religious content feed;
- no network request;
- no persistence change;
- no ViewModel/domain/data change.

## 2. Approved Text

Arabic:

```text
لا إله إلا الله محمد رسول الله
```

English:

```text
THERE IS NO DEITY EXCEPT ALLAH
MUHAMMAD IS THE MESSENGER OF ALLAH
```

The English text stays in this absolute formulation. It must not be changed into a first-person pledge,
marketing slogan, or personalized copy.

## 3. Public Interface

The production module lives in `core/ui` as a presentation-only component.

```kotlin
@Composable
fun SidrShahadaHeader(
    modifier: Modifier = Modifier,
    arabicText: String = SIDR_SHAHADA_ARABIC,
    translationLineOne: String = SIDR_SHAHADA_TRANSLATION_LINE_ONE,
    translationLineTwo: String = SIDR_SHAHADA_TRANSLATION_LINE_TWO,
    showArabic: Boolean = true,
    showTranslation: Boolean = true,
)
```

Constants live beside the component:

```kotlin
const val SIDR_SHAHADA_ARABIC = "لا إله إلا الله محمد رسول الله"
const val SIDR_SHAHADA_TRANSLATION_LINE_ONE = "THERE IS NO DEITY EXCEPT ALLAH"
const val SIDR_SHAHADA_TRANSLATION_LINE_TWO = "MUHAMMAD IS THE MESSENGER OF ALLAH"
```

Interface rules:

- the caller does not pass callbacks;
- no `onClick`, no dismiss callback, no navigation;
- Arabic and English are separate text nodes;
- `showTranslation` may be used only for an explicit product/user setting in the future, not as a layout
  hack to fit small screens;
- `showArabic` exists to support staged rollout if Arabic shaping fails a device gate, but production Home
  should show Arabic only after the Arabic typography gate passes.

## 4. Visual Rules

The header:

- is not clickable;
- is not a card;
- is not dismissible;
- has no badges;
- has no animation;
- has no loading state;
- does not blink, pulse, shimmer, glow, or scroll-react;
- is centered and calm;
- uses `SidrTheme.colors.sacred`;
- uses the sacred typography role, never JetBrains Mono for Arabic;
- has at least 20dp horizontal padding and 24dp vertical breathing space;
- does not truncate either Arabic or English text;
- does not compete visually with Universal Input.

The artifact currently shows an English-only Home anchor in the visual direction study, while the Master
Plan approves Arabic + English. DS-6A follows the Master Plan and keeps the artifact's restraint: quiet,
centered, unframed, and non-interactive.

## 5. Typography and Arabic Gate

DS-1's `FontFamily.Serif` sacred role is a foundation, not final Arabic typography. Before Home integration,
DS-6A must prove Arabic rendering rather than assuming the platform serif is enough.

Required gates:

- Arabic shaping screenshot;
- ligature/diacritics check;
- RTL reading order check;
- mixed Arabic/LTR composition screenshot;
- font-scale 1.0 / 1.5 / 2.0 screenshots;
- 360dp narrow layout screenshot;
- landscape screenshot;
- Samsung device smoke;
- emulator/API smoke;
- TalkBack order review;
- owner visual approval;
- license review if adding a bundled Arabic font.

If a bundled Arabic font is introduced, it must live in `core/ui/src/main/res/font/`, have clear license
provenance, and be added through a separate explicit task in the implementation plan.

## 6. Home Integration

DS-6A fills the DS-4 `sacredAnchor` slot.

Target hierarchy after DS-6A:

```text
Home top row
SidrShahadaHeader
Universal Input
Active/pending state
Relevant continuation
Favorites
All Apps
Privacy line
```

Rules:

- Universal Input must remain visible without excessive scroll on a standard phone where possible.
- In low-height layouts, the header may become more compact through spacing/type scale, but it must not be
  clipped, animated away, or treated as disposable chrome.
- Typing/results may compact the header, but the logic must be respectful and deterministic.
- App Drawer, Settings, Assistant, and Permission Education must not duplicate the Shahada.

## 7. Accessibility

Requirements:

- Arabic and English are read in a sensible order;
- Arabic text uses `textDirection = Rtl`;
- English translation uses LTR;
- no click semantics;
- no role/button semantics;
- no duplicate content descriptions unless TalkBack ordering requires a single merged description;
- 2.0 font scale remains usable;
- no truncation of sacred text.

## 8. Verification

Required:

- `unit`: constants match approved text exactly;
- `semantics`: no click/action semantics; reading order is explicit;
- `screenshot`: dark/light, Arabic+English, English-only fallback if staged, font-scale 1.5/2.0, RTL,
  narrow 360dp, landscape;
- `arch-guard`: no domain/data/feature imports in sacred component;
- `manual/device`: Samsung smoke and TalkBack pass before production Home integration.

Suggested gate:

```text
./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug testDebugUnitTest assembleDebug
```

Home integration additionally requires launcher tests and device acceptance.

## 9. Non-goals

- No `SidrPrayerSummary` production component in DS-6A.
- No fake prayer strip or static prayer preview in production Home.
- No location, timezone, DST, authority/method, cache, provenance, or prayer failure state.
- No decorative mosque arch, crescent, ornament, or calligraphic background by default.
- No splash screen branding use.
- No agentic/task surface interaction with the sacred header.

## 10. Success Criteria

- `SidrShahadaHeader` exists as a tested, presentation-only `core/ui` component.
- Approved Arabic and English text are exact and protected by tests.
- Arabic rendering is validated before Home production integration.
- Home gains a respectful sacred anchor without behaviour changes or fake prayer data.
- DS-6B remains a separate capability track for prayer correctness.
