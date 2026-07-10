# DS-1 — Soft-Classic-Grey Token Layer + Screenshot Harness (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the AIL-0 green/amber terminal theme with the approved **soft classic grey** identity at
the token level (colors both themes + fixed semantic status + tri-font roles + softened shapes), and stand
up a **Roborazzi screenshot-test harness** the repo currently lacks — all additive/compatible, **no screen
restructuring** (that is DS-3/DS-4).

**Architecture:** Everything stays in `core/ui/theme/`. A new `SidrSemanticColors` + `LocalSidrColors`
CompositionLocal carries SIDR-specific roles alongside a grey Material `ColorScheme`; `SidrTheme` provides
both. `AccentColor` and the `accentColor` preference are kept **compiling but inert** (theme resolves grey
for every accent) — the Settings accent row is retired later in DS-3. Screens inherit the new look through
`MaterialTheme` with no per-screen edits; screenshot tests capture the new baseline.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Robolectric (already in catalog), **Roborazzi** (new,
JVM/Robolectric screenshot testing — no device needed).

**Spec:** [docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md](../specs/2026-07-10-visual-identity-soft-grey-design.md)
(§2 tokens, §3 type, §4 shape/motion). ADR: decisions.md "2026-07-10 — Agentic OS architecture + visual identity".

## Global Constraints

- **Presentation-only.** No routing/execution/privacy/memory/navigation semantics change. No `domain`/`data`
  changes. Only `core/ui/theme/*`, `core/ui/build.gradle.kts`, `gradle/libs.versions.toml`, and one
  `LauncherActivity` line.
- **Do NOT change stored preference keys or the `AccentColor` enum's cases** (migration-plan forbidden).
  `SidrTheme(darkTheme, accent, dynamicColor, content)` keeps its exact signature so no call site breaks;
  `accent` is simply ignored (grey for all values).
- **Accent ≠ semantic status** (spec-locked): the pewter accent (`SidrColors.accent`) and the fixed status
  palette (`success/attention/caution/danger/info`) are separate; status never re-tinted by the accent.
- **Dynamic colour stays off by default** (existing AIL-0 rule; keep the `dynamicColor` branch).
- **Keep terminal *gestures*, drop terminal *colour*:** `>` prompt and block caret stay (they live in
  components, untouched here); the global CRT scanline overlay is removed from `LauncherActivity` but the
  `Modifier.sidrScanlines` function is retained for optional dev/boot use.
- **Exact grey token values (spec §2):**
  - Dark: `ground #131415 · surface #1B1C1E · raised #212325 · line #2A2C30 · border #3A3D42 · text #E8E9EB ·
    sacred #CBCDD1 · dim #A0A2A8 · faint #6E7076 · accent #9BA1AB · accentBorder #494D54`;
    status `success #8AA892 · attention #C6A15C · caution #B8836A · danger #C2695C · info #8593A0`.
  - Light: `ground #ECEDED · surface #F5F6F7 · raised #FFFFFF · line #E0E1E4 · border #C9CBCF · text #1C1E21 ·
    sacred #3D4046 · dim #5C5F65 · faint #8A8D93 · accent #5F6773 · accentBorder #BCC0C6`;
    status `success #5E7D66 · attention #94702E · caution #9A6142 · danger #A24A3E · info #5B6675`.
- **Type roles (spec §3):** mono = `JetBrainsMono` (bundled, interface shell); sans = `FontFamily.SansSerif`
  (system-ui, prose); serif = `FontFamily.Serif` (sacred). No new font files.
- **Shape (spec §4):** softened — chips 7dp, default 10dp, modal 12dp.
- **Build gate command** (machine JDK rolled to 25/26; Gradle 8.10.2 needs JBR 21 + a JDK-17 toolchain):
  `env -u JAVA_HOME JAVA_HOME=~/Загрузки/android-studio/jbr ./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 <tasks>` (abbreviated `./gradlew <tasks>`).
- **Commit style:** `feat(ui): …` / `test(ui): …` / `chore(ui): …`, footer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

**New (`core/ui/theme/`):** `SidrColors.kt` (semantic colors + `LocalSidrColors`), `SidrTextStyles.kt`
(role text styles). **New (`core/ui/`):** `src/test/.../theme/ThemeTokensTest.kt`,
`src/test/.../theme/ThemeScreenshotTest.kt`, `src/test/.../theme/SidrThemePreviews.kt` (multipreview),
`src/test/resources/robolectric.properties`.
**Modified:** `theme/Color.kt` (grey schemes replace green/amber), `theme/Theme.kt` (provide
`LocalSidrColors`, `SidrTheme.colors`/`textStyles` accessors, collapse accent→grey), `theme/Type.kt`
(tri-font families + remap), `theme/Shape.kt` (soften), `theme/Motion.kt` (unchanged; scanlines kept),
`core/ui/build.gradle.kts` (Roborazzi + test deps), `gradle/libs.versions.toml` (Roborazzi), `app/.../LauncherActivity.kt` (drop global scanlines).

---

## Phase 1 — Screenshot harness (prerequisite the repo lacks)

### Task 1: Stand up Roborazzi in `:core:ui`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/ui/build.gradle.kts`
- Create: `core/ui/src/test/resources/robolectric.properties`
- Create: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeScreenshotTest.kt`

**Interfaces:**
- Produces: a working `./gradlew :core:ui:recordRoborazziDebug` / `:verifyRoborazziDebug` cycle; golden
  images under `core/ui/src/test/screenshots/`.

- [ ] **Step 1: Add Roborazzi to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
roborazzi = "1.26.0"
composeUiTest = "1.7.5"
```

Under `[libraries]` add:

```toml
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version = "1.2.1" }
```

Under `[plugins]` add:

```toml
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 2: Wire the plugin + test deps in `core/ui/build.gradle.kts`**

Add `alias(libs.plugins.roborazzi)` to the `plugins { }` block. Add to `android { }`:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

Add to `dependencies { }`:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    debugImplementation(libs.compose.ui.test.manifest)
```

(`libs.junit` already exists in the catalog — the data modules use it. If a `compose.ui.test.junit4` alias
name collides, keep the exact keys added in Step 1.)

- [ ] **Step 3: Pin Robolectric SDK**

Create `core/ui/src/test/resources/robolectric.properties`:

```properties
sdk=34
qualifiers=w360dp-h800dp-xhdpi
```

- [ ] **Step 4: Write the harness proof test**

Create `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeScreenshotTest.kt`:

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test fun harness_smoke() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SIDR", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
```

- [ ] **Step 5: Record the golden + verify**

Run: `./gradlew :core:ui:recordRoborazziDebug --tests '*ThemeScreenshotTest*'`
Expected: BUILD SUCCESSFUL; a PNG appears under `core/ui/src/test/screenshots/`.
Then: `./gradlew :core:ui:verifyRoborazziDebug --tests '*ThemeScreenshotTest*'` → PASS (matches).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml core/ui/build.gradle.kts core/ui/src/test/
git commit -m "test(ui): stand up Roborazzi screenshot harness in core:ui"
```

---

## Phase 2 — Tokens

### Task 2: `SidrColors` semantic layer + `LocalSidrColors`

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/SidrColors.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeTokensTest.kt`

**Interfaces:**
- Produces: `data class SidrColors(...)` with every §2 role; `SidrDarkColors`/`SidrLightColors` vals;
  `LocalSidrColors: ProvidableCompositionLocal<SidrColors>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test fun dark_grey_tokens_match_spec() {
        assertEquals(Color(0xFF131415), SidrDarkColors.ground)
        assertEquals(Color(0xFFE8E9EB), SidrDarkColors.text)
        assertEquals(Color(0xFF9BA1AB), SidrDarkColors.accent)
        assertEquals(Color(0xFFB8836A), SidrDarkColors.caution)
        assertEquals(Color(0xFFC2695C), SidrDarkColors.danger)
    }

    @Test fun light_grey_tokens_match_spec() {
        assertEquals(Color(0xFFECEDED), SidrLightColors.ground)
        assertEquals(Color(0xFF1C1E21), SidrLightColors.text)
        assertEquals(Color(0xFF5F6773), SidrLightColors.accent)
    }

    @Test fun accent_and_status_are_distinct_tokens() {
        // spec-lock: brand accent is never a status colour
        assertEquals(false, SidrDarkColors.accent == SidrDarkColors.caution)
        assertEquals(false, SidrDarkColors.accent == SidrDarkColors.success)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ThemeTokensTest'`
Expected: FAIL — `SidrDarkColors` unresolved.

- [ ] **Step 3: Write `SidrColors.kt`**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SIDR semantic colour roles — the identity layer sitting alongside the Material [ColorScheme].
 *
 * "Soft classic grey" (2026-07-10 visual-identity spec). The brand [accent] (pewter) is used sparingly
 * for interactivity; the status roles ([success]/[attention]/[caution]/[danger]/[info]) are FIXED and
 * never re-tinted by the accent, so risk always reads the same. Consumers read these via
 * `SidrTheme.colors`; raw values stay private-ish to this file.
 */
@Immutable
data class SidrColors(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val border: Color,
    val text: Color,
    val sacred: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accentBorder: Color,
    // fixed semantic status — separate from accent
    val success: Color,
    val attention: Color,
    val caution: Color,
    val danger: Color,
    val info: Color,
)

val SidrDarkColors = SidrColors(
    ground = Color(0xFF131415), surface = Color(0xFF1B1C1E), raised = Color(0xFF212325),
    line = Color(0xFF2A2C30), border = Color(0xFF3A3D42),
    text = Color(0xFFE8E9EB), sacred = Color(0xFFCBCDD1), dim = Color(0xFFA0A2A8), faint = Color(0xFF6E7076),
    accent = Color(0xFF9BA1AB), accentBorder = Color(0xFF494D54),
    success = Color(0xFF8AA892), attention = Color(0xFFC6A15C), caution = Color(0xFFB8836A),
    danger = Color(0xFFC2695C), info = Color(0xFF8593A0),
)

val SidrLightColors = SidrColors(
    ground = Color(0xFFECEDED), surface = Color(0xFFF5F6F7), raised = Color(0xFFFFFFFF),
    line = Color(0xFFE0E1E4), border = Color(0xFFC9CBCF),
    text = Color(0xFF1C1E21), sacred = Color(0xFF3D4046), dim = Color(0xFF5C5F65), faint = Color(0xFF8A8D93),
    accent = Color(0xFF5F6773), accentBorder = Color(0xFFBCC0C6),
    success = Color(0xFF5E7D66), attention = Color(0xFF94702E), caution = Color(0xFF9A6142),
    danger = Color(0xFFA24A3E), info = Color(0xFF5B6675),
)

/** Provided by [SidrTheme]; defaults to dark so a bare preview still renders. */
val LocalSidrColors = staticCompositionLocalOf { SidrDarkColors }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ThemeTokensTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/SidrColors.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeTokensTest.kt
git commit -m "feat(ui): add SidrColors soft-classic-grey semantic tokens + LocalSidrColors"
```

---

### Task 3: Grey Material `ColorScheme` (replace green/amber)

**Files:**
- Modify: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Color.kt`
- Test: extend `ThemeTokensTest.kt`

**Interfaces:**
- Consumes: `SidrDarkColors`/`SidrLightColors`.
- Produces: `GreyDarkColorScheme`/`GreyLightColorScheme` (internal). Removes `GreenDark/GreenLight/AmberDark/AmberLightColorScheme`.

- [ ] **Step 1: Add the failing assertion** (to `ThemeTokensTest`)

```kotlin
    @Test fun grey_scheme_maps_ground_and_accent() {
        assertEquals(SidrDarkColors.ground, GreyDarkColorScheme.background)
        assertEquals(SidrDarkColors.accent, GreyDarkColorScheme.primary)
        assertEquals(SidrDarkColors.danger, GreyDarkColorScheme.error)
        assertEquals(SidrLightColors.ground, GreyLightColorScheme.background)
    }
```

- [ ] **Step 2: Run → FAIL** (`GreyDarkColorScheme` unresolved).
Run: `./gradlew :core:ui:testDebugUnitTest --tests '*ThemeTokensTest'`

- [ ] **Step 3: Rewrite `Color.kt`**

Replace the entire body (delete the four green/amber schemes and the AIL-0 KDoc) with grey schemes derived
from `SidrColors`:

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material [androidx.compose.material3.ColorScheme] built from the "soft classic grey" identity
 * ([SidrDarkColors]/[SidrLightColors]). SIDR-specific roles (accent vs fixed status, sacred, provenance)
 * live in [SidrColors]; screens read Material slots off `MaterialTheme.colorScheme` and SIDR roles off
 * `SidrTheme.colors`. The prior green/amber terminal schemes were retired by the 2026-07-10 visual-identity
 * spec.
 */
internal val GreyDarkColorScheme = darkColorScheme(
    primary = SidrDarkColors.accent, onPrimary = SidrDarkColors.ground,
    primaryContainer = SidrDarkColors.raised, onPrimaryContainer = SidrDarkColors.text,
    secondary = SidrDarkColors.dim, onSecondary = SidrDarkColors.ground,
    secondaryContainer = SidrDarkColors.surface, onSecondaryContainer = SidrDarkColors.text,
    tertiary = SidrDarkColors.accent, onTertiary = SidrDarkColors.ground,
    error = SidrDarkColors.danger, onError = SidrDarkColors.ground,
    errorContainer = SidrDarkColors.surface, onErrorContainer = SidrDarkColors.danger,
    background = SidrDarkColors.ground, onBackground = SidrDarkColors.text,
    surface = SidrDarkColors.surface, onSurface = SidrDarkColors.text,
    surfaceVariant = SidrDarkColors.raised, onSurfaceVariant = SidrDarkColors.dim,
    outline = SidrDarkColors.border, outlineVariant = SidrDarkColors.line,
)

internal val GreyLightColorScheme = lightColorScheme(
    primary = SidrLightColors.accent, onPrimary = SidrLightColors.raised,
    primaryContainer = SidrLightColors.surface, onPrimaryContainer = SidrLightColors.text,
    secondary = SidrLightColors.dim, onSecondary = SidrLightColors.raised,
    secondaryContainer = SidrLightColors.surface, onSecondaryContainer = SidrLightColors.text,
    tertiary = SidrLightColors.accent, onTertiary = SidrLightColors.raised,
    error = SidrLightColors.danger, onError = SidrLightColors.raised,
    errorContainer = SidrLightColors.surface, onErrorContainer = SidrLightColors.danger,
    background = SidrLightColors.ground, onBackground = SidrLightColors.text,
    surface = SidrLightColors.surface, onSurface = SidrLightColors.text,
    surfaceVariant = SidrLightColors.raised, onSurfaceVariant = SidrLightColors.dim,
    outline = SidrLightColors.border, outlineVariant = SidrLightColors.line,
)
```

- [ ] **Step 4: Run → PASS.** `./gradlew :core:ui:testDebugUnitTest --tests '*ThemeTokensTest'`
(Theme.kt still references the old scheme names → it won't fully compile until Task 6; run `:core:ui:compileDebugKotlin` is expected to fail on Theme.kt only. To keep this task independently green, do Task 6's Theme.kt edit before running the module test — OR sequence Tasks 3+6 as one commit. **Recommended: commit Task 3 code, then immediately do Task 6; run the module test at the end of Task 6.**)

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Color.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeTokensTest.kt
git commit -m "feat(ui): grey Material ColorScheme replaces green/amber terminal schemes"
```

---

### Task 4: Tri-font typography + role styles

**Files:**
- Modify: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Type.kt`
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/SidrTextStyles.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/TypographyRoleTest.kt`

**Interfaces:**
- Produces: `SidrSans`/`SidrSerif` families; `SidrTextStyles` (`command`,`system`,`provenance`,`sacred`);
  remapped `SidrTypography` (labels → mono, body/titles → sans).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyRoleTest {
    @Test fun system_and_sacred_use_the_right_families() {
        assertEquals(JetBrainsMono, SidrTextStyles.command.fontFamily)
        assertEquals(JetBrainsMono, SidrTextStyles.provenance.fontFamily)
        assertEquals(FontFamily.Serif, SidrTextStyles.sacred.fontFamily)
    }
    @Test fun material_body_is_sans_labels_are_mono() {
        assertEquals(FontFamily.SansSerif, SidrTypography.bodyMedium.fontFamily)
        assertEquals(JetBrainsMono, SidrTypography.labelSmall.fontFamily)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :core:ui:testDebugUnitTest --tests '*TypographyRoleTest'`

- [ ] **Step 3: Create `SidrTextStyles.kt`**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SIDR role text styles beyond the Material set (2026-07-10 visual-identity spec §3). Mono = the interface
 * shell; serif = sacred. Read via `SidrTheme.textStyles`.
 */
data class SidrTextStyles(
    val command: TextStyle,
    val system: TextStyle,
    val provenance: TextStyle,
    val sacred: TextStyle,
) {
    companion object {
        val Default = SidrTextStyles(
            command = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
            system = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
            provenance = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
            sacred = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 26.sp),
        )
    }
}
```

- [ ] **Step 4: Edit `Type.kt`** — add sans/serif aliases and remap slots (labels → mono, body/titles → sans)

Add after the `JetBrainsMono` family:

```kotlin
/** Human/prose face — the platform sans (system-ui). */
val SidrSans = FontFamily.SansSerif
/** Sacred face — the platform serif (Shahada / long-form prose). */
val SidrSerif = FontFamily.Serif
```

Change `SidrTypography` so `titleLarge`/`titleMedium`/`bodyLarge`/`bodyMedium` use `fontFamily = SidrSans`
(prose is now readable sans), while `labelLarge`/`labelSmall` keep `fontFamily = JetBrainsMono` (section
headers/labels stay mono — the identity). Keep the existing sizes/line-heights; only the four body/title
`fontFamily` lines change from `JetBrainsMono` to `SidrSans`.

- [ ] **Step 5: Run → PASS.** `./gradlew :core:ui:testDebugUnitTest --tests '*TypographyRoleTest'`

- [ ] **Step 6: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Type.kt core/ui/src/main/java/com/sidr/launcher/core/ui/theme/SidrTextStyles.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/TypographyRoleTest.kt
git commit -m "feat(ui): tri-font typography (mono shell / sans prose / serif sacred) + role styles"
```

---

### Task 5: Soften shapes

**Files:**
- Modify: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Shape.kt`
- Test: extend `ThemeTokensTest.kt`

- [ ] **Step 1: Add the failing assertion**

```kotlin
    @Test fun shapes_are_softened() {
        assertEquals(RoundedCornerShape(10.dp), SidrShapes.medium)   // default 10dp
        assertEquals(RoundedCornerShape(7.dp), SidrShapes.small)     // chips 7dp
        assertEquals(RoundedCornerShape(12.dp), SidrShapes.large)    // modal 12dp
    }
```
(add imports `import androidx.compose.foundation.shape.RoundedCornerShape` and `import androidx.compose.ui.unit.dp` to the test.)

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Edit `Shape.kt`**

```kotlin
val SidrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
```
Update the KDoc: brutalist 0–8dp → **softened classic** 4/7/10/12dp (spec §4); elevation still via 1px lines.

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Shape.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeTokensTest.kt
git commit -m "feat(ui): soften shape scale to 4/7/10/12dp"
```

---

### Task 6: `SidrTheme` provides grey + `SidrColors`; accent collapsed

**Files:**
- Modify: `core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Theme.kt`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/SidrThemeTest.kt`

**Interfaces:**
- Consumes: `GreyDarkColorScheme`/`GreyLightColorScheme`, `SidrDarkColors`/`SidrLightColors`, `LocalSidrColors`, `SidrTextStyles`.
- Produces: unchanged `SidrTheme(darkTheme, accent, dynamicColor, content)` signature; `SidrTheme.colors`/`SidrTheme.textStyles` accessors; `AccentColor` enum kept (inert).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theme_provides_grey_regardless_of_accent() {
        var bg = Color.Unspecified
        var sidrAccent = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.AMBER) {
                bg = MaterialTheme.colorScheme.background
                sidrAccent = SidrTheme.colors.accent
            }
        }
        assertEquals(SidrDarkColors.ground, bg)          // amber ignored → grey ground
        assertEquals(SidrDarkColors.accent, sidrAccent)  // SidrTheme.colors exposed
    }
}
```

- [ ] **Step 2: Run → FAIL** (`SidrTheme.colors` unresolved / amber still resolves).

- [ ] **Step 3: Rewrite `Theme.kt`**

Keep the `AccentColor` enum (update its KDoc to "retained for source compatibility; the grey identity
ignores it — a future grey-temperature choice may reuse it"). Rewrite `SidrTheme`:

```kotlin
@Composable
fun SidrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.GREEN,   // retained for compat; ignored (grey identity)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sidrColors = if (darkTheme) SidrDarkColors else SidrLightColors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GreyDarkColorScheme
        else -> GreyLightColorScheme
    }

    CompositionLocalProvider(LocalSidrColors provides sidrColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SidrTypography,
            shapes = SidrShapes,
            content = content,
        )
    }
}

/** Accessors for SIDR roles beyond Material — `SidrTheme.colors` / `SidrTheme.textStyles`. */
object SidrTheme {
    val colors: SidrColors
        @Composable @ReadOnlyComposable get() = LocalSidrColors.current
    val textStyles: SidrTextStyles
        @Composable @ReadOnlyComposable get() = SidrTextStyles.Default
}
```
Add imports: `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.ReadOnlyComposable`.

- [ ] **Step 4: Run the whole module test suite**

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: PASS (ThemeTokens + TypographyRole + SidrTheme + screenshot smoke all green; module now compiles).

- [ ] **Step 5: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/theme/Theme.kt core/ui/src/test/java/com/sidr/launcher/core/ui/theme/SidrThemeTest.kt
git commit -m "feat(ui): SidrTheme resolves grey + provides SidrColors; accent inert"
```

---

## Phase 3 — Integrate, de-CRT, baseline

### Task 7: Drop the global CRT scanline overlay

**Files:**
- Modify: `app/src/main/java/com/sidr/launcher/LauncherActivity.kt`

**Interfaces:**
- Consumes: nothing new. The `Modifier.sidrScanlines` function stays defined (optional dev/boot use).

- [ ] **Step 1: Remove the global scanline application**

At the `.sidrScanlines(enabled = motionEnabled, color = scanlineColor)` call in `LauncherActivity`
(≈ line 71), change the argument to `enabled = false` (or delete the `.sidrScanlines(...)` modifier line
and the now-unused `scanlineColor` local). Leave `LocalSidrMotionEnabled` wiring intact (the block caret in
components still honors it). Spec §4: CRT/scanlines removed globally, kept only for optional dev/boot.

- [ ] **Step 2: Build gate**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no unused-symbol error; if `scanlineColor` becomes unused, delete its `val`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sidr/launcher/LauncherActivity.kt
git commit -m "chore(ui): drop global CRT scanline overlay (identity is grey, not phosphor)"
```

---

### Task 8: Preview matrix + theme-sample golden (dark + light)

**Files:**
- Create: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/SidrThemePreviews.kt`
- Modify: `core/ui/src/test/java/com/sidr/launcher/core/ui/theme/ThemeScreenshotTest.kt`

**Interfaces:**
- Produces: `@SidrThemePreviews` multipreview (dark + light); golden images for a token sample in both
  themes, establishing the grey baseline.

- [ ] **Step 1: Create the multipreview annotation**

```kotlin
package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Dark", uiMode = 0x21)   // UI_MODE_NIGHT_YES | UI_MODE_TYPE_NORMAL
@Preview(name = "Light", uiMode = 0x11)  // UI_MODE_NIGHT_NO  | UI_MODE_TYPE_NORMAL
annotation class SidrThemePreviews
```

- [ ] **Step 2: Add a dark+light token-sample capture** to `ThemeScreenshotTest`

```kotlin
    @Test fun grey_sample_dark() = captureSample(dark = true, name = "grey_sample_dark")
    @Test fun grey_sample_light() = captureSample(dark = false, name = "grey_sample_light")

    private fun captureSample(dark: Boolean, name: String) {
        compose.setContent {
            SidrTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SIDR", style = MaterialTheme.typography.titleLarge)
                        Text("FAVORITES", style = MaterialTheme.typography.labelSmall)
                        Text("Open Telegram", style = MaterialTheme.typography.bodyMedium)
                        Text("LOCAL · 14 MS", style = SidrTheme.textStyles.provenance)
                        Text("There is no deity except Allah", style = SidrTheme.textStyles.sacred)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("core/ui/src/test/screenshots/$name.png")
    }
```

- [ ] **Step 3: Record + verify goldens**

Run: `./gradlew :core:ui:recordRoborazziDebug` → generates `grey_sample_dark.png` / `grey_sample_light.png`.
Then: `./gradlew :core:ui:verifyRoborazziDebug` → PASS. Eyeball the two PNGs: grey ground, mono labels, sans
body, serif Shahada line — the new baseline.

- [ ] **Step 4: Commit** (goldens included)

```bash
git add core/ui/src/test/
git commit -m "test(ui): grey theme goldens (dark+light) + SidrThemePreviews multipreview"
```

---

### Task 9: Full build gate + docs sync

- [ ] **Step 1: Full gate**

Run: `./gradlew :core:ui:testDebugUnitTest testDebugUnitTest assembleDebug :core:ui:verifyRoborazziDebug`
Expected: BUILD SUCCESSFUL. The app now renders soft classic grey everywhere; existing feature tests still
pass (they assert behaviour, not colour).

- [ ] **Step 2: Docs**

Append a short ADR note ("DS-1 grey token layer — done") to `ai-context/decisions.md`; add a line to
`CLAUDE.md` Current-goal / `ai-context/current-status.md` noting the theme is now soft-classic-grey +
Roborazzi harness exists; mark this plan done. Commit:

```bash
git add ai-context/decisions.md CLAUDE.md ai-context/current-status.md docs/superpowers/plans/2026-07-10-ds1-grey-token-layer.md
git commit -m "docs(ui): DS-1 grey token layer complete + Roborazzi harness"
```

**DoD:** grey tokens (both themes) + tri-font + soft shapes live at token level; `SidrTheme.colors`/
`textStyles` available; accent inert but compiling; global CRT gone; Roborazzi harness + grey goldens green;
full build gate green; no screen restructured (that is DS-3/DS-4).

---

## Self-Review (author checklist — completed)

- **Spec coverage:** §2 dark tokens → T2/T3; §2 light → T2/T3; §2.3 fixed status separate from accent →
  T2 (`accent_and_status_are_distinct_tokens`); §3 tri-font roles → T4; §4 shape → T5; §4 CRT removed → T7;
  screenshot harness (spec §8 prerequisite) → T1/T8; `SidrTheme.colors` accessor → T6; accent dropped but
  keys preserved → T6 (inert enum, no preference change). Dynamic-colour-off retained → T6.
- **Placeholder scan:** none — every step has concrete code/values/commands. The one prose step (T4 Step 4
  "change four fontFamily lines") names the exact slots and the exact change.
- **Type consistency:** `SidrColors` fields, `SidrDarkColors`/`SidrLightColors`, `GreyDarkColorScheme`/
  `GreyLightColorScheme`, `SidrTextStyles.{command,system,provenance,sacred}` + `.Default`, `SidrTheme.colors`/
  `.textStyles`, `LocalSidrColors`, unchanged `SidrTheme(darkTheme,accent,dynamicColor,content)` +
  `AccentColor{GREEN,AMBER}` — used consistently across tasks.
- **Ordering note:** Task 3 leaves `Theme.kt` referencing removed scheme names; Task 6 fixes it. Do 3 then 6
  back-to-back and run the module test at the end of Task 6 (called out in T3 Step 4).
