# AIL-3 — Universal Input — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax
> for tracking.

**Goal:** One home input field that additively routes typed + spoken natural language to app-filter /
existing command pipeline / web / site / Play Store / assistant, without touching the proven command
pipeline (no LLM — that is AIL-4).

**Architecture:** A pure `domain` classifier (`UniversalInputRouter` object, mirrors `UrlDetector`)
turns the live buffer into an `InputIntent`. `LauncherViewModel` derives a `HomeInputResults` state
(app matches + route chips) from that + the loaded app list; **Enter still calls
`HandleUserCommandUseCase` byte-for-byte**, and route chips dispatch *through* the existing pipeline/nav.
A terminal `>`-prompt field + bracketed route-chip row land in `core/ui`. A hidden session-only
"Command console" dev mode unlocks via a 7-tap wordmark arm + `//dev-mode` sentinel.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, JUnit4 + coroutines-test. Multi-module
Clean Architecture.

## Global Constraints

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`. (Router is stdlib-only.)
- No `feature → feature` deps. Single `NavHost` in `app`. ViewModels emit `NavigationEvent`; never touch
  `NavHostController`. The ViewModel stays Android-free (the screen does `Uri.encode`).
- `IntentMatcher` / `CommandNormalizer` / `HandleUserCommandUseCase` / `ExecutableAction` / resolver /
  `AndroidActionExecutor` are **untouched** — byte-for-byte for every real typed command.
- Launcher core works fully offline; no LLM, no new persisted context, no new persisted preference key
  (dev mode is in-memory / session-only, so `PrivacyInventoryGuardTest` is untouched).
- Verification gate: `./gradlew --no-daemon testDebugUnitTest assembleDebug` green.
- Closure (plan §0): ADR appended to `decisions.md`; `CLAUDE.md` Current goal → AIL-4;
  `ai-context/current-status.md` synced; `ai-launcher-mvp-plan.md` §5 AIL-3 marked ✅.

Design source: [ail-3-universal-input-design.md](ail-3-universal-input-design.md).

---

## File Structure

- **Create** `domain/src/main/java/com/sidr/launcher/domain/input/InputIntent.kt` — sealed `InputIntent`.
- **Create** `domain/src/main/java/com/sidr/launcher/domain/input/UniversalInputRouter.kt` — pure `object`
  classifier (reuses `com.sidr.launcher.domain.intent.UrlDetector`).
- **Create** `domain/src/test/java/com/sidr/launcher/domain/input/UniversalInputRouterTest.kt`.
- **Create** `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrCommandPrompt.kt` — DF-2
  terminal `>`-prompt field.
- **Create** `core/ui/src/main/java/com/sidr/launcher/core/ui/component/RouteChipRow.kt` — DF-3 bracketed
  route chips (`RouteChip` UI model + row).
- **Create** `feature/launcher/.../HomeInputResults.kt` — feature-local results model + `RouteChipKind`
  enum + `ConsoleLine`.
- **Modify** `feature/launcher/.../LauncherViewModel.kt` — results flow, chip dispatch, dev mode.
- **Modify** `feature/launcher/.../LauncherScreen.kt` — swap field, "search overtakes" results panel,
  wordmark + tap-to-arm, console transcript layout.
- **Modify** `feature/launcher/src/test/java/.../LauncherViewModelTest.kt` — new behavior tests.

No `:app` DI change (router is a pure `object`, called directly — like `UrlDetector`).

---

### Task 1: `InputIntent` + `UniversalInputRouter` (pure domain)

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/input/InputIntent.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/input/UniversalInputRouter.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/input/UniversalInputRouterTest.kt`

**Interfaces:**
- Consumes: `com.sidr.launcher.domain.intent.UrlDetector.classify(String): UrlClassification`
  (`UrlClassification.Url(url)` / `.SearchFallback(term)` / `.None`).
- Produces: `sealed interface InputIntent { data object Empty; data object DevSentinel;
  data class Query(val raw: String, val siteUrl: String?) }` and
  `object UniversalInputRouter { fun classify(buffer: String): InputIntent }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalInputRouterTest {

    @Test
    fun `blank buffer is Empty`() {
        assertEquals(InputIntent.Empty, UniversalInputRouter.classify("   "))
    }

    @Test
    fun `dev sentinel is recognized case-insensitively and trimmed`() {
        assertEquals(InputIntent.DevSentinel, UniversalInputRouter.classify("  //DEV-mode "))
    }

    @Test
    fun `plain text is a Query with no site url`() {
        val intent = UniversalInputRouter.classify("telegram")
        assertEquals(InputIntent.Query(raw = "telegram", siteUrl = null), intent)
    }

    @Test
    fun `safe url yields a Query carrying the openable site url`() {
        val intent = UniversalInputRouter.classify("github.com") as InputIntent.Query
        assertEquals("github.com", intent.raw)
        assertEquals("https://github.com", intent.siteUrl)
    }

    @Test
    fun `unsafe or unknown-tld domain yields a Query with no site url`() {
        // UrlDetector routes punycode / unknown TLD to SearchFallback → no site chip.
        val intent = UniversalInputRouter.classify("xn--nxasmq6b.com") as InputIntent.Query
        assertNull(intent.siteUrl)
    }

    @Test
    fun `raw is the trimmed original, not the lowercased url token`() {
        val intent = UniversalInputRouter.classify("  Hello World  ") as InputIntent.Query
        assertEquals("Hello World", intent.raw)
        assertNull(intent.siteUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --no-daemon :domain:test --tests "com.sidr.launcher.domain.input.UniversalInputRouterTest"`
Expected: FAIL — `UniversalInputRouter` / `InputIntent` unresolved.

- [ ] **Step 3: Write `InputIntent.kt`**

```kotlin
package com.sidr.launcher.domain.input

/**
 * What the live universal-input buffer affords (AIL-3). Purely a *presentation-routing* classification:
 * it never executes anything and never replaces the command pipeline. The ViewModel uses it to decide
 * which route chips to offer; app-match filtering stays in the ViewModel (it needs the installed-app
 * list). Enter/submit still goes through the unchanged [com.sidr.launcher.domain.intent.HandleUserCommandUseCase].
 */
sealed interface InputIntent {

    /** Blank buffer — show the home body (favorites / suggestions / all apps). */
    data object Empty : InputIntent

    /** The literal `//dev-mode` toggle string (developer Command-console unlock). Inert unless armed. */
    data object DevSentinel : InputIntent

    /**
     * Normal typing. [raw] is the trimmed original text (used verbatim for web search / assistant
     * prefill / submit). [siteUrl] is non-null only when the buffer is a high-confidence, safe openable
     * `http(s)` URL — then the UI may offer an "open site" chip targeting it.
     */
    data class Query(val raw: String, val siteUrl: String?) : InputIntent
}
```

- [ ] **Step 4: Write `UniversalInputRouter.kt`**

```kotlin
package com.sidr.launcher.domain.input

import com.sidr.launcher.domain.intent.UrlClassification
import com.sidr.launcher.domain.intent.UrlDetector

/**
 * Classifies the universal-input buffer into an [InputIntent] (AIL-3). Pure, Android-free, stdlib-only —
 * mirrors [UrlDetector], which it reuses for URL safety (scheme allow-list, curated TLD, homograph
 * guard). Deterministic and side-effect-free, so it is exhaustively unit-testable and the ViewModel can
 * call it on every keystroke without a coroutine.
 */
object UniversalInputRouter {

    private const val DEV_SENTINEL = "//dev-mode"

    fun classify(buffer: String): InputIntent {
        val raw = buffer.trim()
        if (raw.isEmpty()) return InputIntent.Empty
        if (raw.equals(DEV_SENTINEL, ignoreCase = true)) return InputIntent.DevSentinel

        // A safe, openable URL earns a "site" chip; anything else (search fallback / not a URL) does not.
        val siteUrl = when (val url = UrlDetector.classify(raw)) {
            is UrlClassification.Url -> url.url
            is UrlClassification.SearchFallback -> null
            UrlClassification.None -> null
        }
        return InputIntent.Query(raw = raw, siteUrl = siteUrl)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew --no-daemon :domain:test --tests "com.sidr.launcher.domain.input.UniversalInputRouterTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/input/ domain/src/test/java/com/sidr/launcher/domain/input/
git commit -m "feat(ail-3): add pure UniversalInputRouter + InputIntent (domain)"
```

---

### Task 2: `SidrCommandPrompt` terminal field (`core/ui`, DF-2)

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrCommandPrompt.kt`

**Interfaces:**
- Produces: `@Composable fun SidrCommandPrompt(value: String, onValueChange: (String) -> Unit,
  onSubmit: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "type a command",
  showMic: Boolean = false, listening: Boolean = false, onMic: () -> Unit = {})`.

Presentation only — no unit test; validated by `assembleDebug` + the `@Preview`s. Mirrors
`SidrSearchField`'s callback contract so the ViewModel wiring is unchanged (`showMic`/`onMic` reuse the
existing gate; `listening` drives the mic visual).

- [ ] **Step 1: Write the component + previews**

```kotlin
package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * The terminal `>`-prompt universal-input field (AIL-3 / DF-2). A leading `>` glyph replaces the search
 * magnifier, JetBrains Mono renders the text, an accent block caret marks the cursor, and thin grid
 * borders carry glowing accent corner ticks. Same callback contract as [SidrSearchField] so it is a
 * drop-in for the home field; the App Drawer keeps [SidrSearchField]. Pure presentation — routing is the
 * caller's decision in [onSubmit] / [onValueChange].
 *
 * @param listening true while a voice recognizer is capturing — the mic affordance switches to the
 *   active accent state (a static fill; pulse/flicker is DF-5 motion, deferred).
 */
@Composable
fun SidrCommandPrompt(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "type a command",
    showMic: Boolean = false,
    listening: Boolean = false,
    onMic: () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .drawBehind {
                // Thin grid border + glowing accent corner ticks (static; brutalist AIL-0 identity).
                val tick = 10.dp.toPx()
                val w = size.width
                val h = size.height
                val stroke = 1.dp.toPx()
                // full thin border
                drawRect(color = onSurface.copy(alpha = 0.12f), size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                // four corner ticks in accent
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(accent, Offset(x, y), Offset(x + dx, y), strokeWidth = stroke)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy), strokeWidth = stroke)
                }
                corner(0f, 0f, tick, tick)
                corner(w, 0f, -tick, tick)
                corner(0f, h, tick, -tick)
                corner(w, h, -tick, -tick)
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = ">",
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface.copy(alpha = 0.4f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = onSurface),
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showMic) {
            IconButton(
                onClick = onMic,
                modifier = if (listening) {
                    Modifier.background(accent.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic_24),
                    contentDescription = if (listening) "Listening" else "Voice input",
                    tint = if (listening) accent else onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptEmptyPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "", onValueChange = {}, onSubmit = {}, showMic = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptTypedPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "open telegram", onValueChange = {}, onSubmit = {}, showMic = true, listening = true)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew --no-daemon :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL (font/mic resources already present since AIL-0 / Block T).

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrCommandPrompt.kt
git commit -m "feat(ail-3): add SidrCommandPrompt terminal input field (core/ui, DF-2)"
```

---

### Task 3: `RouteChipRow` bracketed route chips (`core/ui`, DF-3)

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/component/RouteChipRow.kt`

**Interfaces:**
- Produces: `data class RouteChip(val label: String, val onClick: () -> Unit)` and
  `@Composable fun RouteChipRow(chips: List<RouteChip>, modifier: Modifier = Modifier)`.

Presentation only; validated by `assembleDebug` + `@Preview`. The caller (launcher screen) builds the
label/onClick per chip so no feature model leaks into `core/ui`.

- [ ] **Step 1: Write the component + preview**

```kotlin
package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/** One bracketed terminal route chip (AIL-3 / DF-3): a tappable `[ label ]` accelerator. */
data class RouteChip(val label: String, val onClick: () -> Unit)

/**
 * The route-chip row shown under the universal-input field (DF-3): bracketed `[ ⌕ web ]` terminal chips
 * for the explicit web / assistant / open-site lanes. Horizontally scrollable so it never wraps the home
 * layout. Pure presentation; the caller supplies each chip's label + tap action.
 */
@Composable
fun RouteChipRow(
    chips: List<RouteChip>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(chips) { chip ->
            Text(
                text = "[ ${chip.label} ]",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = chip.onClick)
                    .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun RouteChipRowPreview() {
    SidrTheme(darkTheme = true) {
        RouteChipRow(
            chips = listOf(
                RouteChip("⌕ web") {},
                RouteChip("✦ ask") {},
                RouteChip("⌂ site") {},
            ),
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew --no-daemon :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/component/RouteChipRow.kt
git commit -m "feat(ail-3): add RouteChipRow bracketed route chips (core/ui, DF-3)"
```

---

### Task 4: `HomeInputResults` model + ViewModel results flow

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/HomeInputResults.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt`

**Interfaces:**
- Consumes: `UniversalInputRouter.classify(...)`, `InputIntent`, existing `filterApps(apps, query)`
  (`AppDrawerUiState.kt`, `internal` in the same module), the existing `commandInput` + `_rawAppsResult`
  flows.
- Produces: `data class HomeInputResults(val active: Boolean, val appMatches: List<InstalledApp>,
  val chips: List<RouteChipKind>)`, `enum class RouteChipKind { WEB, ASK, SITE }`, and
  `val LauncherViewModel.inputResults: StateFlow<HomeInputResults>`.

- [ ] **Step 1: Write the failing test** (append to `LauncherViewModelTest`)

```kotlin
    @Test
    // Note: FakeInstalledAppsRepository seeds via `appsToReturn`; FakeIntentMatcher records the
    // *normalized* (trim+collapse+lowercase-ROOT) inputs in `receivedInputs` — assert against those.
    fun `typing surfaces app matches and web+ask chips`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp(packageName = "org.telegram.messenger", label = "Telegram", activityName = null),
            InstalledApp(packageName = "com.maps", label = "Maps", activityName = null),
        )
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("tele")
        advanceUntilIdle()
        val results = vm.inputResults.value
        assertTrue(results.active)
        assertEquals(listOf("Telegram"), results.appMatches.map { it.label })
        assertEquals(listOf(RouteChipKind.WEB, RouteChipKind.ASK), results.chips)
    }

    @Test
    fun `typing a safe url adds the site chip`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("github.com")
        advanceUntilIdle()
        assertEquals(listOf(RouteChipKind.WEB, RouteChipKind.ASK, RouteChipKind.SITE), vm.inputResults.value.chips)
    }

    @Test
    fun `clearing the buffer returns empty inactive results`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("tele")
        advanceUntilIdle()
        vm.onCommandChanged("")
        advanceUntilIdle()
        val results = vm.inputResults.value
        assertFalse(results.active)
        assertTrue(results.appMatches.isEmpty())
        assertTrue(results.chips.isEmpty())
    }
```

> Note: check `FakeInstalledAppsRepository`'s seam name — if it exposes a setter method rather than a
> `var apps`, seed via that method instead (keep the assertions identical).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: FAIL — `inputResults` / `RouteChipKind` unresolved.

- [ ] **Step 3: Write `HomeInputResults.kt`**

```kotlin
package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp

/**
 * Live universal-input results (AIL-3 / DF-1 "search overtakes"). Derived from the typed/spoken buffer:
 * when [active], the home body yields to app matches + route chips. Empty/inactive → the home body
 * (favorites / suggestions / all apps) renders unchanged.
 */
data class HomeInputResults(
    val active: Boolean = false,
    val appMatches: List<InstalledApp> = emptyList(),
    val chips: List<RouteChipKind> = emptyList(),
)

/** The explicit route lanes offered as chips. WEB/ASK for any non-blank buffer; SITE only for a safe URL. */
enum class RouteChipKind { WEB, ASK, SITE }

/** One line of the hidden developer Command console (AIL-3 / DF-1): a submitted command + its outcome. */
data class ConsoleLine(val command: String, val result: String)
```

- [ ] **Step 4: Add the results flow to `LauncherViewModel`**

Add imports near the existing ones:

```kotlin
import com.sidr.launcher.domain.input.InputIntent
import com.sidr.launcher.domain.input.UniversalInputRouter
```

Add the derived flow after the `commandInput` declaration (around line 159):

```kotlin
    // ── Universal-input live results (AIL-3) ───────────────────────────────
    // Derived purely from the buffer + the loaded app list. Enter still routes through the unchanged
    // command pipeline; this only decides what the "search overtakes" panel shows.
    val inputResults: StateFlow<HomeInputResults> = combine(
        commandInput,
        _rawAppsResult,
    ) { buffer, appsResult ->
        when (val intent = UniversalInputRouter.classify(buffer)) {
            InputIntent.Empty, InputIntent.DevSentinel -> HomeInputResults()
            is InputIntent.Query -> {
                val loaded = (appsResult as? OperationResult.Success)?.value ?: emptyList()
                val chips = buildList {
                    add(RouteChipKind.WEB)
                    add(RouteChipKind.ASK)
                    if (intent.siteUrl != null) add(RouteChipKind.SITE)
                }
                HomeInputResults(
                    active = true,
                    appMatches = filterApps(loaded, intent.raw),
                    chips = chips,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeInputResults(),
    )
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: PASS (new + existing tests).

- [ ] **Step 6: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/HomeInputResults.kt \
        feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt \
        feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt
git commit -m "feat(ail-3): derive HomeInputResults (app matches + route chips) in LauncherViewModel"
```

---

### Task 5: Chip dispatch (web/site) through the unchanged pipeline

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt`

**Interfaces:**
- Produces: `fun LauncherViewModel.submitWebSearch(query: String)` and
  `fun LauncherViewModel.submitSite(query: String)` — both dispatch through the existing
  `onCommandSubmitted(...)` (so they route via `HandleUserCommandUseCase`, never new execution logic).
  The ASK lane + app-tap need no new VM method (ASK is screen-side nav via `Routes.Assistant.routeFor`;
  app tap reuses `onAppClicked`).

- [ ] **Step 1: Write the failing test** (append)

```kotlin
    @Test
    fun `web chip routes a search command through the unchanged pipeline`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("cats")
        vm.submitWebSearch("cats")
        advanceUntilIdle()
        // FakeIntentMatcher records the normalized command text it was asked to match.
        assertEquals("search cats", fakeMatcher.receivedInputs.last())
    }

    @Test
    fun `site chip submits the raw url through the unchanged pipeline`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.submitSite("github.com")
        advanceUntilIdle()
        assertEquals("github.com", fakeMatcher.receivedInputs.last())
    }
```

> `FakeIntentMatcher.receivedInputs` (a `List<String>`) holds the *normalized* inputs in call order —
> `receivedInputs.last()` is the assertion seam; no fake change needed.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: FAIL — `submitWebSearch` / `submitSite` unresolved.

- [ ] **Step 3: Add the dispatch methods to `LauncherViewModel`** (after `onCommandSubmitted`, ~line 220)

```kotlin
    /**
     * Web-search route chip (AIL-3): prefix the buffer with the `search` verb and route it through the
     * UNCHANGED command pipeline (AIL-2 web search, already history-redacted). No new executor path.
     */
    fun submitWebSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        onCommandSubmitted("search $q")
    }

    /**
     * Open-site route chip (AIL-3): the buffer is already a safe URL (the chip is offered only then), so
     * submitting it as-is routes to AIL-2's OpenUrl through the UNCHANGED pipeline. One-tap "submit".
     */
    fun submitSite(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        onCommandSubmitted(q)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt \
        feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt
git commit -m "feat(ail-3): web/site route-chip dispatch through the unchanged command pipeline"
```

---

### Task 6: Dev-mode Command console (session-only) + parity guard

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt`

**Interfaces:**
- Produces: `val LauncherViewModel.devConsoleOn: StateFlow<Boolean>`,
  `val LauncherViewModel.consoleLines: StateFlow<List<ConsoleLine>>`, `fun armDevMode()`. Submit gains an
  additive pre-check: armed `//dev-mode` toggles the console and is consumed (never reaches the use case);
  un-armed `//dev-mode` falls through unchanged.

- [ ] **Step 1: Write the failing tests** (append)

```kotlin
    @Test
    fun `dev sentinel is inert until armed then toggles the console`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        // Un-armed: passes through to the pipeline (matched as a normal, unknown command).
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertFalse(vm.devConsoleOn.value)
        assertEquals("//dev-mode", fakeMatcher.receivedInputs.last())

        // Arm, then toggle on; the sentinel must NOT reach the matcher this time.
        fakeMatcher.reset()
        vm.armDevMode()
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertTrue(vm.devConsoleOn.value)
        assertTrue(fakeMatcher.receivedInputs.isEmpty())

        // Toggle off.
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertFalse(vm.devConsoleOn.value)
    }

    @Test
    fun `console records submitted commands only while on`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandSubmitted("open telegram")
        advanceUntilIdle()
        assertTrue(vm.consoleLines.value.isEmpty()) // off → no transcript

        vm.armDevMode()
        vm.onCommandSubmitted("//dev-mode")   // on
        vm.onCommandSubmitted("open telegram")
        advanceUntilIdle()
        assertEquals(listOf("open telegram"), vm.consoleLines.value.map { it.command })
    }
```

> Uses only `assertEquals`/`assertTrue`/`assertFalse` (already imported in this file).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: FAIL — `devConsoleOn` / `consoleLines` / `armDevMode` unresolved.

- [ ] **Step 3: Add dev-mode state + submit pre-check to `LauncherViewModel`**

Add fields near the other private flows (~line 177):

```kotlin
    // ── Developer Command console (AIL-3 / DF-1) — session-only, in-memory. No persisted key, so the
    // privacy denylist guard is untouched; both flags reset on process death. Two-factor unlock:
    // arm via 7 wordmark taps (screen), then submit the "//dev-mode" sentinel to toggle.
    private val _devArmed = MutableStateFlow(false)
    private val _devConsoleOn = MutableStateFlow(false)
    val devConsoleOn: StateFlow<Boolean> = _devConsoleOn
    private val _consoleLines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val consoleLines: StateFlow<List<ConsoleLine>> = _consoleLines
```

Add the arm method near the other UI actions:

```kotlin
    /** Arm the hidden developer console (called by the screen after 7 rapid wordmark taps). */
    fun armDevMode() {
        _devArmed.value = true
        _commandFeedback.value = CommandFeedback.Message("dev mode armed — submit //dev-mode")
    }
```

Replace the body of `onCommandSubmitted` (currently lines 216-220) with the additive pre-check + console
capture:

```kotlin
    fun onCommandSubmitted(text: String) {
        // Additive AIL-3 pre-check: an ARMED "//dev-mode" toggles the console and is consumed here so it
        // never reaches HandleUserCommandUseCase. Un-armed, it falls through unchanged (Unknown), so the
        // command pipeline stays byte-for-byte for every real input.
        if (_devArmed.value && UniversalInputRouter.classify(text) is InputIntent.DevSentinel) {
            _devConsoleOn.value = !_devConsoleOn.value
            setCommandInput("")
            _commandFeedback.value = CommandFeedback.Message(
                if (_devConsoleOn.value) "dev console on" else "dev console off",
            )
            return
        }
        viewModelScope.launch {
            val outcome = handleUserCommand.handle(text)
            applyOutcome(outcome)
            if (_devConsoleOn.value) {
                _consoleLines.value = _consoleLines.value + ConsoleLine(text, outcomeSummary(outcome))
            }
        }
    }

    /** One-line console summary of a [CommandOutcome] (dev console only; display-safe). */
    private fun outcomeSummary(outcome: CommandOutcome): String = when (outcome) {
        CommandOutcome.Empty -> "empty"
        CommandOutcome.Executed -> "✓ executed"
        CommandOutcome.NoOp -> "no-op"
        is CommandOutcome.Message -> outcome.text
        is CommandOutcome.NeedsConfirmation -> "? ${outcome.candidates.size} candidates"
        is CommandOutcome.Suggest -> "? suggest"
        CommandOutcome.LowConfidence -> "low confidence"
        is CommandOutcome.Unknown -> "unknown"
        is CommandOutcome.Failed -> "✗ ${outcome.message}"
        CommandOutcome.OpenAssistant -> "→ assistant"
        CommandOutcome.OpenSettings -> "→ settings"
        CommandOutcome.ShowApps -> "→ apps"
        CommandOutcome.ClearInput -> "cleared"
    }
```

> `outcomeSummary`'s `when` must stay exhaustive — mirror the exact `CommandOutcome` variants used in the
> existing `applyOutcome` (lines 297-352). If a variant name differs, match `applyOutcome`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew --no-daemon :feature:launcher:testDebugUnitTest --tests "*LauncherViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt \
        feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt
git commit -m "feat(ail-3): hidden session-only developer Command console (armed //dev-mode toggle)"
```

---

### Task 7: Wire the screen — search-overtakes, terminal field, chips, wordmark arm, console

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt`

**Interfaces:**
- Consumes: `SidrCommandPrompt`, `RouteChip`/`RouteChipRow` (core/ui); `inputResults`, `devConsoleOn`,
  `consoleLines`, `submitWebSearch`, `submitSite`, `armDevMode`, `RouteChipKind`, `ConsoleLine`,
  `HomeInputResults`; `Routes.Assistant.routeFor`, `Uri.encode`.
- Produces: no new public API (screen-only wiring).

UI wiring — no unit test; validated by `assembleDebug`.

- [ ] **Step 1: Add imports** to `LauncherScreen.kt`

```kotlin
import android.net.Uri
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import com.sidr.launcher.core.ui.component.RouteChip
import com.sidr.launcher.core.ui.component.RouteChipRow
import com.sidr.launcher.core.ui.component.SidrCommandPrompt
```

- [ ] **Step 2: Collect the new state** (in `LauncherScreen`, after the existing `collectAsStateWithLifecycle` calls, ~line 86)

```kotlin
    val inputResults by viewModel.inputResults.collectAsStateWithLifecycle()
    val devConsoleOn by viewModel.devConsoleOn.collectAsStateWithLifecycle()
    val consoleLines by viewModel.consoleLines.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Add the `SIDR//` wordmark with tap-to-arm** — replace the `topBar` `Row` (lines 121-137) so the wordmark leads and Settings/Assistant stay at the end:

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Wordmark + hidden dev-mode arm: 7 rapid taps arm the Command console (DF-1).
                var tapCount by remember { androidx.compose.runtime.mutableStateOf(0) }
                var lastTap by remember { androidx.compose.runtime.mutableStateOf(0L) }
                Text(
                    text = "SIDR//",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val now = System.currentTimeMillis()
                            tapCount = if (now - lastTap < 3000L) tapCount + 1 else 1
                            lastTap = now
                            if (tapCount >= 7) {
                                tapCount = 0
                                viewModel.armDevMode()
                            }
                        },
                )
                TopBarIcon(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = { viewModel.navigateTo(Routes.Settings.ROUTE) },
                )
                TopBarIcon(
                    painter = painterResource(R.drawable.ic_assistant_24),
                    contentDescription = "Assistant",
                    onClick = { viewModel.navigateTo(Routes.Assistant.ROUTE) },
                )
            }
```

- [ ] **Step 4: Swap the field to `SidrCommandPrompt`** — replace the `SidrSearchField(...)` block (lines 148-154):

```kotlin
            SidrCommandPrompt(
                value = commandInput,
                onValueChange = viewModel::onCommandChanged,
                onSubmit = viewModel::onCommandSubmitted,
                showMic = showMic,
                onMic = onMicTap,
            )
```

- [ ] **Step 5: Render results / console / home in the body** — replace the `Box(modifier = Modifier.weight(1f))` `when (val state = uiState)` block (lines 163-189) so the input results (and dev console) take over the body:

```kotlin
            Box(modifier = Modifier.weight(1f)) {
                when {
                    // Hidden developer transcript (DF-1) — takes over the body while on.
                    devConsoleOn -> CommandConsole(lines = consoleLines)
                    // "Search overtakes": a non-blank buffer replaces the home body with results.
                    inputResults.active -> InputResultsPanel(
                        results = inputResults,
                        query = commandInput,
                        onAppClick = viewModel::onAppClicked,
                        onWeb = { viewModel.submitWebSearch(commandInput) },
                        onSite = { viewModel.submitSite(commandInput) },
                        onAsk = {
                            viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(commandInput.trim())))
                        },
                    )
                    else -> when (val state = uiState) {
                        is UiState.Loading -> LoadingContent()
                        is UiState.Empty -> EmptyState(message = "No apps found")
                        is UiState.Error -> ErrorState(
                            message = errorMessage(state.error),
                            onRetry = if (state.retryable) viewModel::retry else null,
                        )
                        is UiState.Success -> HomeContent(
                            state = state.data,
                            onAppClick = viewModel::onAppClicked,
                            onSuggestionTap = viewModel::onSuggestionClicked,
                            onAllApps = { viewModel.navigateTo(Routes.AppDrawer.ROUTE) },
                            showSetupHint = !isDefaultLauncher && !state.data.setupHintDismissed,
                            onSetDefault = {
                                viewModel.dismissSetupHint()
                                try {
                                    setDefaultLauncher.launch(defaultLauncherIntent(context))
                                } catch (_: ActivityNotFoundException) {
                                }
                            },
                            onDismissHint = viewModel::dismissSetupHint,
                            suggestionsContent = suggestionsContent,
                        )
                    }
                }
            }
```

- [ ] **Step 6: Add the two new private composables** at the bottom of the file (before the helper `fun`s)

```kotlin
/**
 * "Search overtakes" results (AIL-3 / DF-1 + DF-3 hybrid): app matches as an icon+label list over a
 * bracketed route-chip row (`⌕ web`, `✦ ask`, `⌂ site`). App icons use the feature-local [AppTileIcon].
 */
@Composable
private fun InputResultsPanel(
    results: HomeInputResults,
    query: String,
    onAppClick: (InstalledApp) -> Unit,
    onWeb: () -> Unit,
    onSite: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = results.chips.map { kind ->
        when (kind) {
            RouteChipKind.WEB -> RouteChip("⌕ web", onWeb)
            RouteChipKind.ASK -> RouteChip("✦ ask", onAsk)
            RouteChipKind.SITE -> RouteChip("⌂ site", onSite)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        RouteChipRow(chips = chips, modifier = Modifier.padding(vertical = Spacing.sm))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results.appMatches, key = { it.packageName }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    AppTileIcon(app)
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }
        }
    }
}

/** Hidden developer Command console transcript (AIL-3 / DF-1): `> command` + a one-line outcome. */
@Composable
private fun CommandConsole(
    lines: List<ConsoleLine>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        items(lines) { line ->
            Text(
                text = "> ${line.command}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "  ${line.result}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}
```

> `items` for `LazyColumn` needs `import androidx.compose.foundation.lazy.items` — already imported for the
> existing `LazyRow` usage (`androidx.compose.foundation.lazy.items` is shared). Confirm; if the file only
> imported the `LazyRow` `items`, they are the same symbol, so no extra import.

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew --no-daemon :feature:launcher:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt
git commit -m "feat(ail-3): wire universal input — terminal field, search-overtakes results, chips, dev console"
```

---

### Task 8: Full verification + closure docs

**Files:**
- Modify: `ai-context/decisions.md` (ADR), `CLAUDE.md`, `ai-context/current-status.md`,
  `ai-context/ai-launcher-mvp-plan.md` (§5 AIL-3 ✅).

- [ ] **Step 1: Full gate**

Run: `./gradlew --no-daemon testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 0 test failures.

- [ ] **Step 2: Byte-for-byte parity spot-check** — confirm the existing `LauncherViewModelTest` command
  cases (open/search/settings/assistant/unknown) still pass unchanged, and grep proves the pipeline is
  untouched:

Run: `git diff --name-only bafd0f3 -- domain/src/main/java/com/sidr/launcher/domain/intent/ data/repository/src/main/java/com/sidr/launcher/data/repository/intent/`
(`bafd0f3` = the AIL-3 BASE checkpoint, which already contains AIL-2's pipeline changes — so this proves
AIL-3 adds **nothing** on top of them.)
Expected: **empty** (no change to `CommandNormalizer` / `IntentMatcher` / `HandleUserCommandUseCase` /
`ExecutableAction` / resolver / `AndroidActionExecutor`).

- [ ] **Step 3: Append the ADR** to `ai-context/decisions.md` in the established format
  ("ADR 2026-07-05 — AIL-3 complete") covering: additive router (Enter untouched → parity), the pure
  `UniversalInputRouter`/`InputIntent` (UrlDetector reuse), `HomeInputResults` + chip dispatch through the
  pipeline/nav, DF-1/2/3 decisions, the session-only dev console (no persisted key → privacy guard
  untouched), R8 voice reuse, and the verification results.

- [ ] **Step 4: Sync `CLAUDE.md`** — advance `Current goal` to **AIL-4**; mark AIL-3 ✅ DONE with a
  one-paragraph result in the AIL block list.

- [ ] **Step 5: Sync `ai-context/current-status.md`** and mark
  `ai-context/ai-launcher-mvp-plan.md` §5 AIL-3 ✅ with a one-paragraph result.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md ai-context/decisions.md ai-context/current-status.md ai-context/ai-launcher-mvp-plan.md
git commit -m "docs(ail-3): ADR + status sync; Current goal → AIL-4"
```

---

## Self-Review (author checklist)

- **Spec coverage:** app-filter (T4) · command pipeline byte-for-byte (T5/T6/T8-step2) · web/site (T5) ·
  Play Store (via unchanged pipeline `install X`, no new work — covered by AIL-2, surfaced through the
  same submit) · assistant prefill (T7 ask chip) · voice R8 (buffer-derived results, no new code) ·
  DF-1 search-overtakes + hidden console (T4/T6/T7) · DF-2 terminal field (T2) · DF-3 hybrid (T3/T7). ✓
- **Placeholders:** none — every code step carries full code. The three `> Note:` callouts are
  fake-API confirmations (`FakeInstalledAppsRepository` seam, `FakeIntentMatcher.lastCommand`,
  `assertNull` import), each with a concrete fallback. ✓
- **Type consistency:** `InputIntent` (Empty/DevSentinel/Query), `UniversalInputRouter.classify`,
  `HomeInputResults(active,appMatches,chips)`, `RouteChipKind{WEB,ASK,SITE}`, `RouteChip(label,onClick)`,
  `ConsoleLine(command,result)`, `submitWebSearch`/`submitSite`/`armDevMode`/`devConsoleOn`/`consoleLines`
  used identically across tasks. ✓

## Verified fake/API seams (no in-task discovery needed)

- `FakeInstalledAppsRepository.appsToReturn: List<InstalledApp>` — seeding seam (Task 4).
- `FakeIntentMatcher.receivedInputs: List<String>` — normalized inputs in call order; `.last()` is the
  routing-assertion seam, `.isEmpty()` proves "not consulted" (Tasks 5–6).
- `CommandNormalizer.normalize` = trim + collapse whitespace + `lowercase(Locale.ROOT)`, so lowercase
  test inputs (`search cats`, `github.com`, `//dev-mode`) reach the matcher verbatim.
- `outcomeSummary`'s `when` mirrors the 13 `CommandOutcome` variants in the existing `applyOutcome`
  (Empty / Executed / NoOp / Message / NeedsConfirmation / Suggest / LowConfidence / Unknown / Failed /
  OpenAssistant / OpenSettings / ShowApps / ClearInput) — already aligned in Task 6 Step 3.
