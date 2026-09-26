# DS-2 — SIDR Primitives (Design Spec)

> **Status: PROPOSED (2026-07-11)** via brainstorming. Repository-grounded design for the DS-2 primitive
> layer prescribed by the **Design System Master Plan v1.2 §11 (DS-2)** and its §24 immediate-next-action.
> Presentation-only, additive; **no production screen, navigation, ViewModel, persistence, or domain change**.
>
> **Governing sources:** Master Plan v1.2 §11 DS-2 (primitives list, `SidrProvenanceLine` keystone, proof
> surface, acceptance), §5 traceability + §5.1 precedence + §5.2 verify-vocab + §20.1 calm budgets, §14
> screenshot matrix, §22 DoD; grey visual spec (§2 tokens, §3 tri-font, §4 shape/stroke, §6.2 provenance);
> DS-1 ADR (2026-07-11) — the live `SidrColors` / `SidrTextStyles` / `SidrShapes` / Roborazzi harness this
> builds on.

## 1. Goal & scope

Create the minimal building blocks that express SIDR identity and **system truth**, so DS-3 controls and
DS-4+ screens compose from a stable, tested primitive layer instead of raw Material calls. Everything is
additive to `core/ui`; existing components (`ConfirmActionCard`, `SectionHeader`, rows, `RouteChipRow`, …)
are **not** migrated here — that is DS-3/DS-5.

**In scope:** eight primitives + one new stroke token + a preview-only gallery + Roborazzi goldens.
**Out of scope (Master Plan "DS-2 не должен"):** Home / nav / Settings / any ViewModel / agent UI / prayer
data / new persistence / **any `domain` import into `core/ui`**.

**Golden-rule note:** primitives are *generic* presentation elements, explicitly allowed to exist ahead of
engines (Master Plan §10: "Design System может заранее создать generic primitive"); none depicts a
non-existent engine state.

## 2. What already exists (DS-1 baseline, grounded)

- `SidrColors` (both themes) with roles `ground/surface/raised/line/border/text/sacred/dim/faint/accent/
  accentBorder` + fixed status `success/attention/caution/danger/info`; read via `SidrTheme.colors`.
- `SidrTextStyles.Default` roles `command/system/provenance/sacred`; read via `SidrTheme.textStyles`.
  Material slots: titles/body = Sans, labels = Mono.
- `SidrShapes` 4/7/10/12dp; `Spacing.{xs=4,sm=8,md=12,lg=16,xl=24,xxl=32}`; `Sizes.{minTouchTarget=48,…}`.
- Roborazzi harness (`:core:ui:verifyRoborazziDebug`), module-relative golden paths under
  `core/ui/src/test/screenshots/`, `@SidrThemePreviews` (dark+light) multipreview.
- **Missing, added by DS-2:** a stroke token (DS-1 shipped no `Strokes`). Grey spec §4: 1px hairlines,
  2px only for focus/risk.

## 3. New token

```kotlin
// core/ui/theme/Strokes.kt
object Strokes {
    val hairline: Dp = 1.dp   // dividers, surface borders (elevation-by-line, not shadow)
    val focus: Dp = 2.dp      // focus ring / risk emphasis only (grey spec §4)
}
```

## 4. The eight primitives

All live in a new package `core/ui/primitive/` (distinct from the higher-level `component/`), take
**strings + core/ui-local enums** only, and set a full `contentDescription` where the visual is not
self-describing. Signatures are the contract; internals may evolve without breaking callers.

### 4.1 `SidrProvenanceLine` — keystone (Amanah + Ilm)

The single visual primitive for origin & truth (`SOURCE · detail · detail`, faint mono, uppercased). API is
**semantic** (Master Plan: not "arbitrary formatted tokens"):

```kotlin
@Composable
fun SidrProvenanceLine(
    source: String,
    details: List<String> = emptyList(),
    modifier: Modifier = Modifier,
)
```

- Renders `source` + each detail joined by ` · `, style `SidrTheme.textStyles.provenance`, colour
  `SidrColors.faint`, uppercased for display, **wraps** (no truncation of truth).
- **TalkBack:** one composed `contentDescription` (e.g. `"source LOCAL, 14 MS"`) — not the raw glyphs.
- Contains no raw sensitive data; never decorative; the **caller** omits it for ordinary actions
  (`R-MIZAN-1`/§7 interaction law). Examples it must render: `LOCAL · 14 MS`, `ROUTED BY AI · OPENROUTER`,
  `LEARNED · 3 CONFIRMED CHOICES`, `DIYANET · UPDATED 2H AGO`, `CACHED · ISTANBUL · UTC+3`,
  `SYSTEM INTENT · EXTERNAL`.

### 4.2 `SidrText` — tri-font entry point (visual identity)

```kotlin
enum class SidrTextRole { COMMAND, SYSTEM, PROVENANCE, SACRED, HUMAN_BODY, HUMAN_TITLE, LABEL }

@Composable
fun SidrText(
    text: String,
    role: SidrTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,   // Unspecified → role default
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
)
```

Role → style + default colour: `COMMAND`→`textStyles.command`/`text`; `SYSTEM`→`textStyles.system`/`dim`;
`PROVENANCE`→`textStyles.provenance`/`faint`; `SACRED`→`textStyles.sacred`/`sacred`;
`HUMAN_BODY`→`typography.bodyMedium`(Sans)/`text`; `HUMAN_TITLE`→`typography.titleMedium`(Sans)/`text`;
`LABEL`→`typography.labelSmall`(Mono)/`dim`. Callers stop hand-picking `MaterialTheme.typography`.

### 4.3 `SidrSystemLabel` — mono system/section label (System layer)

```kotlin
@Composable
fun SidrSystemLabel(text: String, modifier: Modifier = Modifier)
```

`textStyles.system`, uppercased, wide tracking, colour `dim`. Backs (does not yet replace) the existing
`SectionHeader`; production migration is DS-3.

### 4.4 `SidrStatusMarker` — status never by colour alone (Adl `R-ADL-2`/§20)

```kotlin
enum class SidrStatus { SUCCESS, ATTENTION, CAUTION, DANGER, INFO }

@Composable
fun SidrStatusMarker(status: SidrStatus, label: String, modifier: Modifier = Modifier)
```

A small filled dot in the **fixed** status token (`success/attention/caution/danger/info`) **plus** the
`label` text (`textStyles.system`). The dot is decorative (`contentDescription = null`); the label carries
meaning → readable in greyscale and by TalkBack. Status colour is never the accent (guard already green
from DS-1).

### 4.5 `SidrSurface` — bordered tonal surface (Haya / §6.4 restraint)

```kotlin
enum class SidrSurfaceTone { GROUND, SURFACE, RAISED, SACRED, RISK }

@Composable
fun SidrSurface(
    tone: SidrSurfaceTone,
    modifier: Modifier = Modifier,
    shape: Shape = SidrShapes.medium,
    content: @Composable () -> Unit,
)
```

Tone → background: `GROUND→ground`, `SURFACE→surface`, `RAISED→raised`, `SACRED→surface` (quiet, extra
padding, no border), `RISK→surface` + `Strokes.hairline` `caution` border. Elevation is a 1px `line`/`border`,
never a shadow. Press-invert / SELECTED is a **chip state → DS-3**, deliberately excluded here.

### 4.6 `SidrDivider`

```kotlin
@Composable
fun SidrDivider(modifier: Modifier = Modifier)
```

`Strokes.hairline` in `SidrColors.line`, full width.

### 4.7 `SidrFocusRing` — 2px accent focus (a11y focus)

```kotlin
fun Modifier.sidrFocusRing(shape: Shape = SidrShapes.small): Modifier
```

Observes focus (`onFocusEvent`) and draws a `Strokes.focus` (2px) `accentBorder` ring when focused; no-op
otherwise. Grey spec §4: 2px only for focus/risk.

### 4.8 `SidrProgress` — quiet, real-process-only (Sukun `R-SUKUN-1`)

```kotlin
@Composable
fun SidrProgress(modifier: Modifier = Modifier, progress: Float? = null)
```

`null` → thin indeterminate; a value → determinate. Accent used sparingly on `line` track. Presence is the
**caller's** responsibility — it is shown only while a real process runs (idle surfaces render none).

## 5. Proof surface (preview-only)

`core/ui/src/test/.../primitive/PrimitiveGallery.kt` — a composable arranging every primitive (each in its
representative states) inside `SidrTheme`. **Not** a production screen; no feature dependency. It is the
subject of the Roborazzi goldens and the `@SidrThemePreviews` dark/light IDE preview. (Kept in the test
source set so it never ships in the APK, mirroring DS-1's `ThemeScreenshotTest`/preview placement.)

## 6. Principle traceability (Master Plan §5)

| Primitive | Principle(s) | Verification (§5.2 vocab) |
|---|---|---|
| `SidrProvenanceLine` | Ilm, Amanah | `screenshot` (all 6 examples) + `semantics` (composed TalkBack) + `unit` (join/format) |
| `SidrStatusMarker` | Adl (`R-ADL-2`) | `screenshot` (greyscale legible) + `semantics` (label read, dot silent) + `arch-guard` (status≠accent) |
| `SidrText`/`SidrSystemLabel` | tri-font contract | `unit` (role→style/colour map) + `screenshot` (font-scale 1.0/1.5/2.0) |
| `SidrSurface`/`SidrDivider` | Haya/§6.4 | `screenshot` (dark/light, 1px line) |
| `SidrFocusRing` | a11y focus | `screenshot` (focused vs not) + `semantics` |
| `SidrProgress` | Sukun (`R-SUKUN-1`) | `screenshot` (indeterminate/determinate) + `manual` (absent when idle) |

## 7. Acceptance (Master Plan DS-2 + §14 + calm budgets §20.1)

- Dark **and** light goldens for the gallery + per-primitive states (`:core:ui:verifyRoborazziDebug`).
- Font-scale **1.0 / 1.5 / 2.0** goldens for text-bearing primitives (wrap, no clip of truth).
- Narrow **360dp** layout holds; provenance wraps rather than truncates.
- **RTL** goldens for text primitives (`SidrText`, `SidrProvenanceLine`, `SidrSystemLabel`,
  `SidrStatusMarker`).
- `arch-guard`: **no `domain` / feature import in `core/ui/primitive/`** (dependency guard test);
  status tokens ≠ accent (DS-1 guard).
- `semantics`: `SidrProvenanceLine` exposes a composed description; `SidrStatusMarker` label readable, dot
  silent.
- **Calm budget** on the gallery: accent appears only in `SidrFocusRing`/`SidrProgress`; everything else is
  neutral grey/text (≤ the §20.1 accent budget).
- Full gate green: `:core:ui:testDebugUnitTest testDebugUnitTest assembleDebug :core:ui:verifyRoborazziDebug`.
- DoD §22 items that apply (design/arch review, ADR, status docs, clean tree, next block not auto-started).

## 8. File plan

**New (main):** `theme/Strokes.kt`; `primitive/SidrProvenanceLine.kt`, `SidrText.kt` (+ `SidrTextRole`),
`SidrSystemLabel.kt`, `SidrStatusMarker.kt` (+ `SidrStatus`), `SidrSurface.kt` (+ `SidrSurfaceTone`),
`SidrDivider.kt`, `SidrFocusRing.kt`, `SidrProgress.kt`.
**New (test):** `primitive/PrimitiveGallery.kt`, `primitive/PrimitivesScreenshotTest.kt`,
`primitive/SidrPrimitivesTest.kt` (role/format unit), `primitive/PrimitiveDependencyGuardTest.kt`
(no-domain-import), goldens under `core/ui/src/test/screenshots/`.
**Modified:** none in production code (additive only). Optional: none.

## 9. Non-goals / deferred

- No press-invert / SELECTED surface (DS-3 chip state). No buttons/chips/rows (DS-3). No `SectionHeader`/
  `ConfirmActionCard` migration (DS-3/DS-5). No prayer, memory, or agent primitives.
- `SidrProgress` ships but no production screen wires it here (DS-4+).

## 10. Success criteria (this spec)

- Eight primitives + one token, each with an exact signature and a role→token mapping grounded in the live
  DS-1 API. ✅
- `SidrProvenanceLine` is semantic (`source`+`details`), not a formatted-string sink. ✅
- Every primitive cites a §5.2 verification type; no production/domain/nav/VM change. ✅
- Acceptance matches Master Plan DS-2 (goldens, font-scale, narrow, RTL, status≠accent, TalkBack, no feature
  dep, full gate). ✅
