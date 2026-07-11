# DS-2 — SIDR Primitives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the eight additive DS-2 presentation primitives (keystone `SidrProvenanceLine`) + a stroke token + a preview-only gallery with dark/light/font-scale/RTL Roborazzi goldens, so DS-3+ compose from a stable, tested primitive layer.

**Architecture:** New `core/ui/primitive/` package holds eight stateless composables that take strings + `core/ui`-local enums and read DS-1 tokens via `SidrTheme.colors`/`SidrTheme.textStyles`/`SidrShapes`. Each primitive's *logic* (role→style, provenance format, status→colour, tone→background) is an `internal` pure function unit-tested on the JVM; each primitive's *look* is a committed Roborazzi golden. Presentation-only, additive — no production screen, nav, ViewModel, persistence, or `domain` change.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Robolectric + Roborazzi (DS-1 harness), JUnit4.

**Spec:** [docs/superpowers/specs/2026-07-11-ds2-primitives-design.md](../specs/2026-07-11-ds2-primitives-design.md). Governing map: `docs/design/SIDR Design System Master Plan.md` §11 DS-2, §5/§5.1/§5.2/§20.1, §14, §22.

## Global Constraints

- **Presentation-only, additive.** No change to Home / nav / Settings / any ViewModel / persistence / prayer / agent UI. **No `domain` or `feature` import in `core/ui/primitive/`** (guard-tested, Task 7).
- **Primitives take strings + `core/ui`-local enums only** — no domain models cross into `core/ui`.
- **Status ≠ accent** (DS-1 lock): status colours come from `SidrColors.{success,attention,caution,danger,info}`, never the accent.
- **`SidrProvenanceLine` API is semantic** (`source: String, details: List<String>`), never a pre-formatted string sink; TalkBack reads a composed description, not the raw glyphs.
- **Goldens are module-relative** (`src/test/screenshots/…`) — the unit-test JVM's working dir is the module dir (DS-1 lesson).
- **Build gate command:** plain `./gradlew <tasks>` — this machine's `~/.gradle/gradle.properties` pins `org.gradle.java.home` to a local JDK 17. **No `JAVA_HOME` prefix.**
- **Commit style:** `feat(ui): …` / `test(ui): …` / `docs(ui): …`, footer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

**New (main, `core/ui/src/main/java/com/sidr/launcher/core/ui/`):**
`theme/Strokes.kt`; `primitive/SidrText.kt` (+ `SidrTextRole`), `primitive/SidrSystemLabel.kt`,
`primitive/SidrProvenanceLine.kt`, `primitive/SidrStatusMarker.kt` (+ `SidrStatus`),
`primitive/SidrSurface.kt` (+ `SidrSurfaceTone`), `primitive/SidrDivider.kt`, `primitive/SidrFocusRing.kt`,
`primitive/SidrProgress.kt`.

**New (test, `core/ui/src/test/java/com/sidr/launcher/core/ui/`):**
`theme/StrokesTest.kt`, `primitive/SidrTextRoleTest.kt`, `primitive/ProvenanceFormatTest.kt`,
`primitive/SidrStatusTest.kt`, `primitive/SidrSurfaceToneTest.kt`, `primitive/PrimitiveSemanticsTest.kt`,
`primitive/PrimitiveDependencyGuardTest.kt`, `primitive/PrimitiveGallery.kt`,
`primitive/PrimitivesScreenshotTest.kt`; goldens under `src/test/screenshots/`.

**Modified:** none in production code.

---

## Task 1: `Strokes` token

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Strokes.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/StrokesTest.kt`

**Interfaces:**
- Produces: `object Strokes { val hairline: Dp = 1.dp; val focus: Dp = 2.dp }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokesTest {
    @Test fun stroke_tokens_match_spec() {
        assertEquals(1.dp, Strokes.hairline)
        assertEquals(2.dp, Strokes.focus)
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*StrokesTest'`
Expected: FAIL — `Strokes` unresolved.

- [ ] **Step 3: Write `Strokes.kt`**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stroke widths (DS-2). Elevation is expressed as 1px lines, not shadow (grey spec §4); 2px is reserved
 * for focus and risk emphasis only.
 */
object Strokes {
    /** 1dp — dividers and surface borders. */
    val hairline: Dp = 1.dp

    /** 2dp — focus ring / risk emphasis only. */
    val focus: Dp = 2.dp
}
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*StrokesTest'`

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Strokes.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/StrokesTest.kt
git commit -m "feat(ui): add Strokes token (hairline 1dp / focus 2dp)"
```

---

## Task 2: `SidrText` + `SidrTextRole` + `SidrSystemLabel`

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrText.kt`
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrSystemLabel.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrTextRoleTest.kt`

**Interfaces:**
- Consumes: `SidrTextStyles`, `SidrColors`, `SidrTheme` (DS-1).
- Produces: `enum class SidrTextRole { COMMAND, SYSTEM, PROVENANCE, SACRED, HUMAN_BODY, HUMAN_TITLE, LABEL }`;
  `internal fun SidrTextRole.textStyle(styles, typography): TextStyle`; `internal fun SidrTextRole.defaultColor(colors): Color`;
  `@Composable fun SidrText(text, role, modifier, color, maxLines, overflow)`;
  `@Composable fun SidrSystemLabel(text, modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import com.sidr.launcher.core.ui.theme.SidrTextStyles
import com.sidr.launcher.core.ui.theme.SidrTypography
import org.junit.Assert.assertEquals
import org.junit.Test

class SidrTextRoleTest {
    @Test fun role_maps_to_the_right_text_style() {
        assertEquals(SidrTextStyles.Default.command, SidrTextRole.COMMAND.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTextStyles.Default.provenance, SidrTextRole.PROVENANCE.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTextStyles.Default.sacred, SidrTextRole.SACRED.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTypography.bodyMedium, SidrTextRole.HUMAN_BODY.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTypography.labelSmall, SidrTextRole.LABEL.textStyle(SidrTextStyles.Default, SidrTypography))
    }

    @Test fun role_maps_to_the_right_default_color() {
        assertEquals(SidrDarkColors.text, SidrTextRole.COMMAND.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.faint, SidrTextRole.PROVENANCE.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.dim, SidrTextRole.SYSTEM.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.sacred, SidrTextRole.SACRED.defaultColor(SidrDarkColors))
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrTextRoleTest'`
Expected: FAIL — `SidrTextRole` unresolved.

- [ ] **Step 3: Write `SidrText.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrTextStyles
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * The tri-font entry point (DS-2). A role picks the SIDR text style + default colour so callers stop
 * hand-selecting `MaterialTheme.typography`. Interface = mono; prose = sans; sacred = serif (grey spec §3).
 */
enum class SidrTextRole { COMMAND, SYSTEM, PROVENANCE, SACRED, HUMAN_BODY, HUMAN_TITLE, LABEL }

internal fun SidrTextRole.textStyle(styles: SidrTextStyles, typography: Typography): TextStyle = when (this) {
    SidrTextRole.COMMAND -> styles.command
    SidrTextRole.SYSTEM -> styles.system
    SidrTextRole.PROVENANCE -> styles.provenance
    SidrTextRole.SACRED -> styles.sacred
    SidrTextRole.HUMAN_BODY -> typography.bodyMedium
    SidrTextRole.HUMAN_TITLE -> typography.titleMedium
    SidrTextRole.LABEL -> typography.labelSmall
}

internal fun SidrTextRole.defaultColor(colors: SidrColors): Color = when (this) {
    SidrTextRole.COMMAND -> colors.text
    SidrTextRole.SYSTEM -> colors.dim
    SidrTextRole.PROVENANCE -> colors.faint
    SidrTextRole.SACRED -> colors.sacred
    SidrTextRole.HUMAN_BODY -> colors.text
    SidrTextRole.HUMAN_TITLE -> colors.text
    SidrTextRole.LABEL -> colors.dim
}

@Composable
fun SidrText(
    text: String,
    role: SidrTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolved = if (color == Color.Unspecified) role.defaultColor(SidrTheme.colors) else color
    Text(
        text = text,
        modifier = modifier,
        style = role.textStyle(SidrTheme.textStyles, MaterialTheme.typography),
        color = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}
```

- [ ] **Step 4: Write `SidrSystemLabel.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Mono, uppercase, wide-tracking system/section label (DS-2). Backs — does not yet replace — the existing
 * `SectionHeader`; production migration is DS-3.
 */
@Composable
fun SidrSystemLabel(text: String, modifier: Modifier = Modifier) {
    SidrText(text = text.uppercase(), role = SidrTextRole.SYSTEM, modifier = modifier)
}
```

- [ ] **Step 5: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrTextRoleTest'`

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrText.kt core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrSystemLabel.kt core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrTextRoleTest.kt
git commit -m "feat(ui): SidrText tri-font primitive + SidrSystemLabel"
```

---

## Task 3: `SidrProvenanceLine` (keystone)

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrProvenanceLine.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/ProvenanceFormatTest.kt`

**Interfaces:**
- Consumes: `SidrText`, `SidrTextRole`.
- Produces: `internal fun provenanceDisplay(source, details): String`; `internal fun provenanceDescription(source, details): String`;
  `@Composable fun SidrProvenanceLine(source: String, details: List<String> = emptyList(), modifier: Modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.primitive

import org.junit.Assert.assertEquals
import org.junit.Test

class ProvenanceFormatTest {
    @Test fun display_uppercases_and_joins_with_middot() {
        assertEquals("LOCAL · 14 MS", provenanceDisplay("local", listOf("14 ms")))
        assertEquals("DIYANET · ISTANBUL · UPDATED 2H AGO",
            provenanceDisplay("Diyanet", listOf("Istanbul", "updated 2h ago")))
        assertEquals("LOCAL", provenanceDisplay("local", emptyList()))
    }

    @Test fun display_drops_blank_segments() {
        assertEquals("LOCAL · 14 MS", provenanceDisplay("local", listOf("", "14 ms", "   ")))
    }

    @Test fun description_is_a_composed_sentence_not_glyphs() {
        assertEquals("source LOCAL, 14 MS", provenanceDescription("local", listOf("14 ms")))
        assertEquals("source DIYANET, ISTANBUL, UPDATED 2H AGO",
            provenanceDescription("Diyanet", listOf("Istanbul", "updated 2h ago")))
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ProvenanceFormatTest'`

- [ ] **Step 3: Write `SidrProvenanceLine.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

private const val SEP = " · "

internal fun provenanceDisplay(source: String, details: List<String>): String =
    (listOf(source) + details).filter { it.isNotBlank() }.joinToString(SEP) { it.trim().uppercase() }

internal fun provenanceDescription(source: String, details: List<String>): String {
    val segments = (listOf(source) + details).filter { it.isNotBlank() }.map { it.trim().uppercase() }
    return "source " + segments.joinToString(", ")
}

/**
 * DS-2 keystone (Amanah + Ilm): the single visual primitive for origin and truth —
 * `SOURCE · detail · detail` in faint mono, uppercased, wrapping (truth is never truncated). TalkBack reads
 * a composed sentence, not the raw glyphs. Contains no raw sensitive data; the CALLER omits it for ordinary
 * actions (grey spec §6.2, Master Plan §11 DS-2).
 */
@Composable
fun SidrProvenanceLine(
    source: String,
    details: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val description = provenanceDescription(source, details)
    SidrText(
        text = provenanceDisplay(source, details),
        role = SidrTextRole.PROVENANCE,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ProvenanceFormatTest'`

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrProvenanceLine.kt core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/ProvenanceFormatTest.kt
git commit -m "feat(ui): SidrProvenanceLine keystone (source+details, semantic TalkBack)"
```

---

## Task 4: `SidrStatusMarker` + `SidrStatus`

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrStatusMarker.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrStatusTest.kt`

**Interfaces:**
- Consumes: `SidrColors`, `SidrText`, `Spacing`, `SidrTheme`.
- Produces: `enum class SidrStatus { SUCCESS, ATTENTION, CAUTION, DANGER, INFO }`; `internal fun SidrStatus.color(colors): Color`;
  `@Composable fun SidrStatusMarker(status: SidrStatus, label: String, modifier: Modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SidrStatusTest {
    @Test fun status_maps_to_the_fixed_status_token() {
        assertEquals(SidrDarkColors.success, SidrStatus.SUCCESS.color(SidrDarkColors))
        assertEquals(SidrDarkColors.attention, SidrStatus.ATTENTION.color(SidrDarkColors))
        assertEquals(SidrDarkColors.caution, SidrStatus.CAUTION.color(SidrDarkColors))
        assertEquals(SidrDarkColors.danger, SidrStatus.DANGER.color(SidrDarkColors))
        assertEquals(SidrDarkColors.info, SidrStatus.INFO.color(SidrDarkColors))
    }

    @Test fun status_is_never_the_accent() {
        SidrStatus.entries.forEach { assertNotEquals(SidrDarkColors.accent, it.color(SidrDarkColors)) }
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrStatusTest'`

- [ ] **Step 3: Write `SidrStatusMarker.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/** Fixed semantic status (DS-2). Never the accent; the dot's meaning is carried by the text label. */
enum class SidrStatus { SUCCESS, ATTENTION, CAUTION, DANGER, INFO }

internal fun SidrStatus.color(colors: SidrColors): Color = when (this) {
    SidrStatus.SUCCESS -> colors.success
    SidrStatus.ATTENTION -> colors.attention
    SidrStatus.CAUTION -> colors.caution
    SidrStatus.DANGER -> colors.danger
    SidrStatus.INFO -> colors.info
}

/**
 * Status shown as a coloured dot **plus** a text label (Adl `R-ADL-2`: risk/status never by colour alone) —
 * readable in greyscale and by TalkBack (the dot is decorative; the label carries meaning).
 */
@Composable
fun SidrStatusMarker(status: SidrStatus, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp).clip(CircleShape).background(status.color(SidrTheme.colors)),
        )
        SidrText(text = label, role = SidrTextRole.SYSTEM)
    }
}
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrStatusTest'`

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrStatusMarker.kt core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrStatusTest.kt
git commit -m "feat(ui): SidrStatusMarker (dot + label, status never colour-only)"
```

---

## Task 5: `SidrSurface` + `SidrSurfaceTone` + `SidrDivider`

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrSurface.kt`
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrDivider.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrSurfaceToneTest.kt`

**Interfaces:**
- Consumes: `SidrColors`, `SidrShapes`, `Strokes`, `SidrTheme`.
- Produces: `enum class SidrSurfaceTone { GROUND, SURFACE, RAISED, SACRED, RISK }`;
  `internal fun SidrSurfaceTone.background(colors): Color`; `internal fun SidrSurfaceTone.borderColor(colors): Color?`;
  `@Composable fun SidrSurface(tone, modifier, shape, content)`; `@Composable fun SidrDivider(modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidrSurfaceToneTest {
    @Test fun tone_maps_to_background() {
        assertEquals(SidrDarkColors.ground, SidrSurfaceTone.GROUND.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.SURFACE.background(SidrDarkColors))
        assertEquals(SidrDarkColors.raised, SidrSurfaceTone.RAISED.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.SACRED.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.RISK.background(SidrDarkColors))
    }

    @Test fun only_risk_has_a_border() {
        assertNull(SidrSurfaceTone.SURFACE.borderColor(SidrDarkColors))
        assertNull(SidrSurfaceTone.SACRED.borderColor(SidrDarkColors))
        assertEquals(SidrDarkColors.caution, SidrSurfaceTone.RISK.borderColor(SidrDarkColors))
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrSurfaceToneTest'`

- [ ] **Step 3: Write `SidrSurface.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** Tonal surface (DS-2). Elevation is a 1px line, never a shadow (grey spec §4). Press-invert = DS-3. */
enum class SidrSurfaceTone { GROUND, SURFACE, RAISED, SACRED, RISK }

internal fun SidrSurfaceTone.background(colors: SidrColors): Color = when (this) {
    SidrSurfaceTone.GROUND -> colors.ground
    SidrSurfaceTone.SURFACE -> colors.surface
    SidrSurfaceTone.RAISED -> colors.raised
    SidrSurfaceTone.SACRED -> colors.surface
    SidrSurfaceTone.RISK -> colors.surface
}

internal fun SidrSurfaceTone.borderColor(colors: SidrColors): Color? = when (this) {
    SidrSurfaceTone.RISK -> colors.caution
    else -> null
}

@Composable
fun SidrSurface(
    tone: SidrSurfaceTone,
    modifier: Modifier = Modifier,
    shape: Shape = SidrShapes.medium,
    content: @Composable () -> Unit,
) {
    val colors = SidrTheme.colors
    val border = tone.borderColor(colors)
    Surface(
        modifier = modifier,
        shape = shape,
        color = tone.background(colors),
        border = border?.let { BorderStroke(Strokes.hairline, it) },
        content = content,
    )
}
```

- [ ] **Step 4: Write `SidrDivider.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** 1px hairline divider in the `line` role (DS-2). */
@Composable
fun SidrDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = Strokes.hairline, color = SidrTheme.colors.line)
}
```

- [ ] **Step 5: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*SidrSurfaceToneTest'`

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrSurface.kt core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrDivider.kt core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/SidrSurfaceToneTest.kt
git commit -m "feat(ui): SidrSurface tonal surface + SidrDivider hairline"
```

---

## Task 6: `SidrFocusRing` + `SidrProgress`

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrFocusRing.kt`
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrProgress.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitiveSemanticsTest.kt`

**Interfaces:**
- Consumes: `SidrShapes`, `Strokes`, `SidrTheme`.
- Produces: `fun Modifier.sidrFocusRing(shape: Shape = SidrShapes.small): Modifier`;
  `@Composable fun SidrProgress(modifier: Modifier = Modifier, progress: Float? = null)`.
- Also adds the compose-rule semantics test proving `SidrProvenanceLine` exposes its composed description (Task 3 output) and `SidrStatusMarker`'s label is readable (Task 4 output).

- [ ] **Step 1: Write `SidrFocusRing.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Shape
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** 2px accent focus ring shown only while focused (grey spec §4: 2px is focus/risk only). */
fun Modifier.sidrFocusRing(shape: Shape = SidrShapes.small): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = if (focused) Modifier.border(Strokes.focus, SidrTheme.colors.accentBorder, shape) else Modifier
    this.onFocusEvent { focused = it.isFocused }.then(ring)
}
```

- [ ] **Step 2: Write `SidrProgress.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * Quiet progress (DS-2). `null` = indeterminate, a value = determinate. Shown only while a real process
 * runs (Sukun `R-SUKUN-1`); idle surfaces render none. Accent used sparingly on a `line` track.
 */
@Composable
fun SidrProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = SidrTheme.colors
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier, color = colors.accent, trackColor = colors.line)
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier, color = colors.accent, trackColor = colors.line)
    }
}
```

- [ ] **Step 3: Write the semantics test** (Robolectric compose rule, mirrors DS-1's `SidrThemeTest`)

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrimitiveSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun provenance_exposes_composed_description_not_glyphs() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrProvenanceLine(source = "local", details = listOf("14 ms"))
            }
        }
        compose.onNodeWithContentDescription("source LOCAL, 14 MS").assertIsDisplayed()
    }

    @Test fun status_marker_label_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrStatusMarker(status = SidrStatus.SUCCESS, label = "LOCAL")
            }
        }
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*PrimitiveSemanticsTest'`
Expected: PASS (both primitives + Task 3/4 outputs resolve).

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrFocusRing.kt core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrProgress.kt core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitiveSemanticsTest.kt
git commit -m "feat(ui): SidrFocusRing modifier + SidrProgress + primitive semantics test"
```

---

## Task 7: Dependency guard — no `domain`/`feature` import in `primitive/`

**Files:**
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitiveDependencyGuardTest.kt`

**Interfaces:**
- Consumes: the `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/` source directory.
- Produces: a guard proving no primitive imports `com.sidr.launcher.domain` or `com.sidr.launcher.feature`.

- [ ] **Step 1: Write the guard test**

```kotlin
package com.sidr.launcher.core.ui.primitive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DS-2 architecture guard: `core/ui` primitives are presentation-only. No `domain`/`feature` import may
 * leak in (Master Plan "DS-2 не должен: импортировать domain в core/ui"). Reads the real source dir; the
 * unit-test JVM's working directory is the module dir (`core/ui`).
 */
class PrimitiveDependencyGuardTest {
    @Test fun no_domain_or_feature_import_in_primitives() {
        val dir = File("src/main/java/com/sidr/launcher/core/ui/primitive")
        assertTrue("primitive source dir must exist: ${dir.absolutePath}", dir.isDirectory)
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter { it.contains("com.sidr.launcher.domain") || it.contains("com.sidr.launcher.feature") }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()
        assertTrue("presentation primitives must not import domain/feature:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
```

- [ ] **Step 2: Run → PASS**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*PrimitiveDependencyGuardTest'`
Expected: PASS (dir exists, no offending imports).

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitiveDependencyGuardTest.kt
git commit -m "test(ui): guard — no domain/feature import in core/ui primitives"
```

---

## Task 8: Preview gallery + goldens (dark/light, font-scale, RTL) + full gate + docs

**Files:**
- Create: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitiveGallery.kt`
- Create: `core/ui/src/test/java/com/sidr/launcher/core/ui/primitive/PrimitivesScreenshotTest.kt`
- Modify: `ai-context/decisions.md`, `CLAUDE.md`, `ai-context/current-status.md`, `docs/superpowers/plans/2026-07-11-ds2-primitives.md`

**Interfaces:**
- Consumes: every primitive from Tasks 1–6.
- Produces: `@Composable fun PrimitiveGallery()`; goldens `primitives_dark`, `primitives_light`, `primitives_fontscale2`, `primitives_rtl` under `core/ui/src/test/screenshots/`.

- [ ] **Step 1: Write `PrimitiveGallery.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme

/** Preview-only DS-2 primitive gallery (proof surface). Not a production screen; no feature dependency. */
@Composable
fun PrimitiveGallery() {
    Surface(color = SidrTheme.colors.ground) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SidrSystemLabel("PRIMITIVES")
            SidrText("Open Telegram", role = SidrTextRole.COMMAND)
            SidrText("There is no deity except Allah", role = SidrTextRole.SACRED)
            SidrText("Assistant answer in readable prose.", role = SidrTextRole.HUMAN_BODY)
            SidrProvenanceLine("local", listOf("14 ms"))
            SidrProvenanceLine("routed by ai", listOf("openrouter"))
            SidrProvenanceLine("diyanet", listOf("istanbul", "updated 2h ago"))
            SidrStatusMarker(SidrStatus.SUCCESS, "LOCAL")
            SidrStatusMarker(SidrStatus.ATTENTION, "CLOUD")
            SidrStatusMarker(SidrStatus.CAUTION, "CONFIRM")
            SidrStatusMarker(SidrStatus.DANGER, "FAILED")
            SidrDivider()
            SidrSurface(SidrSurfaceTone.RAISED) { SidrText("Raised surface", role = SidrTextRole.HUMAN_BODY, modifier = Modifier.padding(12.dp)) }
            SidrSurface(SidrSurfaceTone.RISK) { SidrText("Risk surface", role = SidrTextRole.HUMAN_BODY, modifier = Modifier.padding(12.dp)) }
            SidrProgress()
            SidrProgress(progress = 0.4f)
        }
    }
}

@SidrThemePreviews
@Composable
private fun PrimitiveGalleryPreview() {
    SidrTheme { PrimitiveGallery() }
}
```

Note: `@SidrThemePreviews` already exists in the test source set from DS-1 (`theme/SidrThemePreviews.kt`); it is visible here (same module, same source set).

- [ ] **Step 2: Write `PrimitivesScreenshotTest.kt`**

```kotlin
package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PrimitivesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primitives_dark() = capture(dark = true, name = "primitives_dark")
    @Test fun primitives_light() = capture(dark = false, name = "primitives_light")

    @Test fun primitives_fontscale2() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { PrimitiveGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_fontscale2.png")
    }

    @Test fun primitives_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { PrimitiveGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_rtl.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { PrimitiveGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
```

- [ ] **Step 3: Record + verify goldens**

Run: `./gradlew :core:ui:recordRoborazziDebug --tests '*PrimitivesScreenshotTest*'`
Expected: BUILD SUCCESSFUL; four PNGs appear under `core/ui/src/test/screenshots/`.
Then: `./gradlew :core:ui:verifyRoborazziDebug --tests '*PrimitivesScreenshotTest*'` → PASS.
Eyeball the four PNGs: grey ground, mono labels/provenance (faint), sans body, serif Shahada, status dots
distinct in colour **and** labelled, risk surface bordered, provenance wraps at font-scale 2.0, RTL mirrors.

- [ ] **Step 4: Full build gate**

Run: `./gradlew :core:ui:testDebugUnitTest testDebugUnitTest assembleDebug :core:ui:verifyRoborazziDebug`
Expected: BUILD SUCCESSFUL (all module unit tests + goldens + debug APK; feature tests unaffected — additive).

- [ ] **Step 5: Docs + ADR**

Append an ADR "2026-07-11 — DS-2 primitives complete" to `ai-context/decisions.md`; add the DS-2 line to
`CLAUDE.md` current-goal + `ai-context/current-status.md`; mark this plan DONE; sync the Master Plan §11
DS-2 status to DONE.

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/test/ ai-context/decisions.md CLAUDE.md ai-context/current-status.md \
        docs/superpowers/plans/2026-07-11-ds2-primitives.md "docs/design/SIDR Design System Master Plan.md"
git commit -m "test(ui): DS-2 primitive gallery goldens (dark/light/fontscale/RTL) + docs sync"
```

**DoD (Master Plan §22 / spec §7):** eight primitives + `Strokes` token live and additive; keystone
`SidrProvenanceLine` semantic + TalkBack-proven; role/format/status/tone logic unit-tested; dark/light +
font-scale-2.0 + RTL goldens green; no `domain`/`feature` import in `primitive/` (guard green); status≠accent;
full gate green; no production screen/nav/VM/persistence touched; ADR + status synced.

---

## Self-Review (author checklist — completed)

- **Spec coverage:** §3 `Strokes` → T1; §4.2 `SidrText`+`SidrTextRole` → T2; §4.3 `SidrSystemLabel` → T2;
  §4.1 `SidrProvenanceLine` → T3; §4.4 `SidrStatusMarker`+`SidrStatus` → T4; §4.5 `SidrSurface`+tone → T5;
  §4.6 `SidrDivider` → T5; §4.7 `SidrFocusRing` → T6; §4.8 `SidrProgress` → T6; §5 proof gallery → T8;
  §6 traceability (unit+semantics) → T2/T3/T4/T5 + T6; §7 acceptance (dark/light/font-scale/RTL/360dp/
  status≠accent/TalkBack/no-dep/full-gate) → T4 (status≠accent), T6 (semantics), T7 (no-dep), T8 (goldens+gate).
- **Placeholder scan:** none — every step has concrete code/commands. The one prose step (T8 Step 5 docs)
  names the exact files and status edits.
- **Type consistency:** `SidrTextRole` (7 cases) + `.textStyle(styles,typography)` + `.defaultColor(colors)`;
  `SidrText(text,role,modifier,color,maxLines,overflow)`; `SidrSystemLabel(text,modifier)`;
  `provenanceDisplay`/`provenanceDescription(source,details)` + `SidrProvenanceLine(source,details,modifier)`;
  `SidrStatus` (5) + `.color(colors)` + `SidrStatusMarker(status,label,modifier)`; `SidrSurfaceTone` (5) +
  `.background/.borderColor(colors)` + `SidrSurface(tone,modifier,shape,content)`; `SidrDivider(modifier)`;
  `Modifier.sidrFocusRing(shape)`; `SidrProgress(modifier,progress)`; `Strokes.{hairline,focus}` — used
  consistently across tasks and matching the spec signatures.
- **Ordering:** T1 token → T2 SidrText (used by T3/T4 labels) → T3/T4/T5/T6 primitives → T7 guard → T8
  gallery/goldens/gate. Each task compiles and tests independently.
