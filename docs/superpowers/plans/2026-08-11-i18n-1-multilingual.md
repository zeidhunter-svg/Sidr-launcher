# I18N-1 Multilingual UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax
> for tracking.

**STATUS: PROPOSED (2026-08-11).**

**Goal:** Extract every in-scope user-facing string into Android string resources behind an
overlay-capable wrapper, ship `en`/`ru`/`tr`, add per-app language selection, and install three
regression barriers - with zero golden rewrites.

**Architecture:** All string reads go through one `core/ui` wrapper (`sidrString`) that checks a
composition-local overlay before falling back to `stringResource`, so a future `translate_ui` needs no
call-site changes. Text that today originates in `domain`, `data`, and ViewModels becomes a typed value;
the feature layer picks the sentence (the DS-10 `AssistantPresentation` precedent). Three guard tests -
in the idiom of `PrivacyInventoryGuardTest` / `ControlsDependencyGuardTest` - fail the build on a new
hardcoded literal, a bypassed wrapper, or a key missing from a main locale.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Robolectric 4.14.1, Roborazzi 1.26.0,
JUnit4, `androidx.appcompat` (new, this block), minSdk 28 / compileSdk 35.

**Spec:** `docs/superpowers/specs/2026-08-11-i18n-1-multilingual-design.md` - read it before Task 1.

## Global Constraints

Every task's requirements implicitly include this section.

- **Build only under JDK 17.** The machine's JDK is 25, which Gradle 8.10.2 cannot parse. Every Gradle
  invocation in this plan uses:
  ```bash
  GRADLE='env -u JAVA_HOME JAVA_HOME=/home/Suleiman/Загрузки/android-studio/jbr ./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10'
  ```
- **Never pipe `gradlew` through `tail`/`head`.** It masked a red gate as exit 0 on 2026-07-13. If a run
  reports everything `UP-TO-DATE`, re-run the test tasks with `--rerun-tasks`.
- **Commit at the end of every task** - authorised by the owner on 2026-08-11 for this block, so a
  failed task can be reverted without taking its neighbours with it. Two rules: commit **only the paths
  that task touched** (never `git add -A`), and run `git status --porcelain` after committing to confirm
  nothing unrelated was swept in.
- **Start from a clean tree.** The golden check below compares `git status` against an empty baseline, so
  it is meaningless if unrelated work is already modified. DS-11 was committed before Task 1 for exactly
  this reason; if `git status --porcelain` is non-empty when a task starts, stop and ask rather than
  building on top of someone else's uncommitted work.
- **Zero golden rewrites.** `:core:ui:verifyRoborazziDebug` must pass without re-recording. The only
  permitted new files under `core/ui/src/test/screenshots/` are the pseudolocale captures of Task 4. A
  moved golden is a bug in the refactor - investigate, never `recordRoborazziDebug`.
- **`android.nonTransitiveRClass=true`.** Each module sees only its own `R`. A feature referencing a
  `core/ui` string must import `com.sidr.launcher.core.ui.R` explicitly - prefer defining the string in
  the module that renders it.
- **Key naming:** `<module>_<surface>_<meaning>`, with the module prefix from this fixed table -
  `core/ui` → `ui_`, `feature/launcher` → `launcher_`, `feature/settings` → `settings_`,
  `feature/prayer` → `prayer_`, `feature/permission_education` → `perm_`,
  `feature/assistant` → `assistant_`, `app` → `app_`. Key names are globally unique across modules.
- **No concatenation.** A sentence with a value becomes one string with positional placeholders
  (`%1$s`), never `"prefix " + value`. Any count-bearing string is a `<plurals>`.
- **XML escaping:** `'` → `\'`, `"` → `\"`, a literal `%` → `%%`. Getting this wrong moves pixels, which
  is what the goldens are for.
- **`domain` stays pure Kotlin** (stdlib + coroutines). **`core/ui` imports no `domain`/`data`/`feature`.**
  No new `feature -> feature` dependency. No new persisted preference key. The outbound allow-list is not
  widened.
- **Never touch the phone's mobile data, Wi-Fi, or airplane mode** - the owner's laptop is tethered
  through the test device. Any system-wide device change (language, font scale, default launcher, app
  data) needs the owner's permission first.

---

## Task 1: The overlay seam + barrier 1b

**Files:**
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/i18n/SidrStrings.kt`
- Create: `core/ui/src/main/res/values/strings.xml`
- Create: `core/ui/src/main/res/values-ru/strings.xml`
- Create: `core/ui/src/main/res/values-tr/strings.xml`
- Test: `core/ui/src/test/java/com/sidr/launcher/core/ui/i18n/SidrStringsTest.kt`
- Test: `app/src/test/java/com/sidr/launcher/i18n/StringSeamGuardTest.kt`

**Interfaces:**
- Produces: `sidrString(@StringRes id: Int): String`, `sidrString(@StringRes id: Int, vararg formatArgs: Any): String`,
  `sidrPluralString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String`,
  `SidrStringOverlay` (`fun lookup(key: String): String?`, `SidrStringOverlay.None`),
  `LocalSidrStringOverlay`. Every later task calls `sidrString` and nothing else.

- [ ] **Step 1: Write the failing seam test**

Create `core/ui/src/test/java/com/sidr/launcher/core/ui/i18n/SidrStringsTest.kt`:

```kotlin
package com.sidr.launcher.core.ui.i18n

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.sidr.launcher.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrStringsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resolves_from_resources_when_no_overlay() {
        var actual = ""
        compose.setContent { actual = sidrString(R.string.ui_action_cancel) }
        assertEquals("Cancel", actual)
    }

    @Test fun overlay_wins_over_resources() {
        var actual = ""
        val overlay = SidrStringOverlay { key -> if (key == "ui_action_cancel") "OVERLAID" else null }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_action_cancel)
            }
        }
        assertEquals("OVERLAID", actual)
    }

    @Test fun overlay_miss_falls_back_to_resources() {
        var actual = ""
        val overlay = SidrStringOverlay { null }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_action_cancel)
            }
        }
        assertEquals("Cancel", actual)
    }

    @Test fun overlay_template_receives_format_arguments() {
        var actual = ""
        val overlay = SidrStringOverlay { key ->
            if (key == "ui_memory_forget_content_description") "FORGET %1\$s %2\$s" else null
        }
        compose.setContent {
            CompositionLocalProvider(LocalSidrStringOverlay provides overlay) {
                actual = sidrString(R.string.ui_memory_forget_content_description, "alias", "open bank")
            }
        }
        assertEquals("FORGET alias open bank", actual)
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
GRADLE='env -u JAVA_HOME JAVA_HOME=/home/Suleiman/Загрузки/android-studio/jbr ./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10'
eval $GRADLE :core:ui:testDebugUnitTest --tests '*SidrStringsTest*'
```
Expected: compilation failure - `sidrString`, `SidrStringOverlay`, `R.string.ui_action_cancel` unresolved.

- [ ] **Step 3: Create the three resource files**

`core/ui/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="ui_action_cancel">Cancel</string>
    <string name="ui_memory_forget_content_description">Forget %1$s %2$s</string>
</resources>
```

`core/ui/src/main/res/values-ru/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="ui_action_cancel">Отмена</string>
    <string name="ui_memory_forget_content_description">Забыть %1$s %2$s</string>
</resources>
```

`core/ui/src/main/res/values-tr/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="ui_action_cancel">İptal</string>
    <string name="ui_memory_forget_content_description">%1$s %2$s unut</string>
</resources>
```

Both placeholder counts match on every path: the two-argument resource, the two-argument overlay
template, and the two arguments passed by the test. A mismatch here throws `IllegalFormatException`,
which is exactly what barrier 2 checks for across locales in Task 3.

- [ ] **Step 4: Implement the seam**

Create `core/ui/src/main/java/com/sidr/launcher/core/ui/i18n/SidrStrings.kt`:

```kotlin
package com.sidr.launcher.core.ui.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * I18N-1 string seam (spec §6). **Every** user-facing string read in this app goes through
 * [sidrString]; `stringResource` is called nowhere else, and `StringSeamGuardTest` enforces that.
 *
 * The seam exists so a future runtime overlay (the `translate_ui` A-stage feature) can serve strings
 * on top of the compiled resources without touching a single call site. I18N-1 ships the extension
 * point only - the shipped app always runs with [SidrStringOverlay.None].
 */
fun interface SidrStringOverlay {

    /**
     * An overlay value for [key] - the **resource entry name** (e.g. `ui_action_cancel`), never the
     * numeric id, which is not stable across builds. Returns `null` to fall through to resources.
     */
    fun lookup(key: String): String?

    companion object {
        /** No overlay: the shipped path. Identity-compared in [sidrString], so it costs nothing. */
        val None = SidrStringOverlay { null }
    }
}

val LocalSidrStringOverlay = staticCompositionLocalOf { SidrStringOverlay.None }

/**
 * Reads a string resource through the overlay seam.
 *
 * The [SidrStringOverlay.None] identity check short-circuits **before** the entry-name lookup, so the
 * shipped app performs exactly one `stringResource` call and no extra allocation.
 */
@Composable
@ReadOnlyComposable
fun sidrString(@StringRes id: Int): String {
    val overlay = LocalSidrStringOverlay.current
    if (overlay === SidrStringOverlay.None) return stringResource(id)
    val key = LocalContext.current.resources.getResourceEntryName(id)
    return overlay.lookup(key) ?: stringResource(id)
}

@Composable
@ReadOnlyComposable
fun sidrString(@StringRes id: Int, vararg formatArgs: Any): String {
    val overlay = LocalSidrStringOverlay.current
    if (overlay === SidrStringOverlay.None) return stringResource(id, *formatArgs)
    val key = LocalContext.current.resources.getResourceEntryName(id)
    val template = overlay.lookup(key) ?: return stringResource(id, *formatArgs)
    return String.format(Locale.getDefault(), template, *formatArgs)
}

/**
 * Plurals deliberately **bypass** the overlay in I18N-1: quantity selection is locale grammar, not
 * copy, and an overlay that supplied one form would silently break `ru` (one/few/many/other) and `tr`.
 * When `translate_ui` lands it must supply whole quantity sets or leave plurals to resources.
 */
@Composable
@ReadOnlyComposable
fun sidrPluralString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(id, count, *formatArgs)
```

- [ ] **Step 5: Run the seam test - expect PASS**

```bash
eval $GRADLE :core:ui:testDebugUnitTest --tests '*SidrStringsTest*'
```
Expected: 4 tests, 0 failures.

- [ ] **Step 6: Write barrier 1b (the seam cannot be bypassed)**

Create `app/src/test/java/com/sidr/launcher/i18n/StringSeamGuardTest.kt`:

```kotlin
package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I18N-1 barrier 1b (spec §10.2). `stringResource` / `pluralStringResource` may be called **only**
 * inside the seam (`core/ui/i18n/SidrStrings.kt`). Everything else goes through `sidrString`, which is
 * what keeps the future `translate_ui` overlay a drop-in.
 *
 * Exact, not heuristic - this is the barrier that makes the seam real rather than a convention.
 * The unit-test working directory is the module dir (`app`), so the repo root is `..`.
 */
class StringSeamGuardTest {

    private val seamFile = "core/ui/src/main/java/com/sidr/launcher/core/ui/i18n/SidrStrings.kt"

    @Test fun string_resource_is_called_only_inside_the_seam() {
        val repoRoot = File("..")
        val offenders = repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/src/main/") && !it.path.contains("/build/") }
            .filterNot { it.canonicalPath.endsWith(seamFile) }
            .flatMap { file ->
                file.readLines()
                    .filter { line ->
                        line.contains("androidx.compose.ui.res.stringResource") ||
                            line.contains("androidx.compose.ui.res.pluralStringResource")
                    }
                    .map { "${file.relativeTo(repoRoot)}: ${it.trim()}" }
            }
            .toList()

        assertTrue(
            "Call sidrString(...) instead of stringResource(...) - the overlay seam must not be " +
                "bypassed (spec §6, §10.2):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }
}
```

- [ ] **Step 7: Run barrier 1b - expect PASS (nothing bypasses the seam yet)**

```bash
eval $GRADLE :app:testDebugUnitTest --tests '*StringSeamGuardTest*'
```
Expected: 1 test, 0 failures.

- [ ] **Step 8: Verify the goldens have not moved**

```bash
eval $GRADLE :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug --rerun-tasks
git status --porcelain core/ui/src/test/screenshots/
```
Expected: build SUCCESSFUL; `git status` prints **nothing** for the screenshots directory.

- [ ] **Step 9: Commit**

```bash
git add core/ui/src/main/java/com/sidr/launcher/core/ui/i18n core/ui/src/main/res core/ui/src/test/java/com/sidr/launcher/core/ui/i18n app/src/test/java/com/sidr/launcher/i18n
git commit -m "feat(i18n-1): string overlay seam + seam guard"
```

---

## Task 2: Extract `core/ui` (62 literals) + locked vocabulary + display casing

**Files:**
- Modify: `core/ui/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Create: `core/ui/src/main/res/values/strings_locked.xml`
- Modify (call sites): `core/ui/src/main/java/com/sidr/launcher/core/ui/component/` -
  `SidrAssistant.kt`, `SidrActionGate.kt`, `SidrActionSafety.kt`, `SidrMemoryItem.kt`,
  `SidrMemoryDisclosure.kt`, `SidrForgetGate.kt`, `SidrRow.kt`, `SidrButton.kt`, `SidrSearchField.kt`,
  `SidrUniversalInput.kt`, `SidrCommandPrompt.kt`, `SidrPrayerSummary.kt`, `ConfirmActionCard.kt`,
  `LearnedChoiceRow.kt`, `ErrorState.kt`, `CommandBar.kt`, `AppTile.kt`, `TopBarIcon.kt`
- Modify (casing): `core/ui/src/main/java/com/sidr/launcher/core/ui/primitive/SidrSystemLabel.kt`,
  `primitive/SidrProvenanceLine.kt`, `component/SidrActionSafety.kt`
- Modify (tests referencing extracted constants): any `core/ui` test importing
  `SEND_READY_DESCRIPTION` / `SEND_EMPTY_DESCRIPTION` / `SEND_BUSY_DESCRIPTION`

**Interfaces:**
- Consumes: `sidrString` from Task 1.
- Produces: `R.string.ui_*` keys used by later tasks only through `core/ui` components; no feature module
  references `com.sidr.launcher.core.ui.R` (non-transitive R - Global Constraints).

- [ ] **Step 1: Record the baseline that must not move**

```bash
eval $GRADLE :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug --rerun-tasks
git status --porcelain core/ui/src/test/screenshots/
```
Expected: SUCCESSFUL, 119 tests + 4 from Task 1 = 123, and an empty `git status` for screenshots.
Write the exact test count into the task report - Task 16 reports the delta.

- [ ] **Step 2: Create the locked, never-translated vocabulary**

`core/ui/src/main/res/values/strings_locked.xml`. Every entry is `translatable="false"`, so AAPT itself
rejects a `values-ru`/`values-tr` copy (spec §7.1). These are the exact literals found in the source:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  I18N-1 locked vocabulary (spec §7.1). translatable="false" is the barrier: AAPT rejects any
  translation of these keys, so the machine-state vocabulary stays identical in every language.
  Provenance/status tokens and gate-type chips only. Buttons and sentences are NOT locked - they live
  in strings.xml and are translated (spec §7.3).
-->
<resources>
    <string name="ui_memory_type_learned_preference" translatable="false">LEARNED PREFERENCE</string>
    <string name="ui_memory_type_explicit_alias" translatable="false">EXPLICIT ALIAS</string>
    <string name="ui_memory_type_user_provided_fact" translatable="false">USER PROVIDED FACT</string>
    <string name="ui_memory_type_temporary_context" translatable="false">TEMPORARY CONTEXT</string>
    <string name="ui_memory_type_system_policy" translatable="false">SYSTEM POLICY</string>
    <string name="ui_memory_type_automation_state" translatable="false">AUTOMATION STATE</string>

    <string name="ui_memory_status_active" translatable="false">ACTIVE</string>
    <string name="ui_memory_status_learning" translatable="false">LEARNING</string>
    <string name="ui_memory_status_needs_confirmation" translatable="false">NEEDS CONFIRMATION</string>
    <string name="ui_memory_status_needs_reconfirmation" translatable="false">NEEDS RECONFIRMATION</string>
    <string name="ui_memory_status_inactive" translatable="false">INACTIVE</string>
    <string name="ui_memory_status_expired" translatable="false">EXPIRED</string>
    <string name="ui_memory_status_unavailable" translatable="false">UNAVAILABLE</string>
    <string name="ui_memory_status_deleted" translatable="false">DELETED</string>

    <string name="ui_gate_type_confirmation" translatable="false">CONFIRM</string>
    <string name="ui_gate_type_permission" translatable="false">PERMISSION</string>
    <string name="ui_gate_type_sensitive" translatable="false">SENSITIVE</string>
    <string name="ui_gate_type_external" translatable="false">EXTERNAL</string>
    <string name="ui_gate_type_destructive" translatable="false">DESTRUCTIVE</string>

    <string name="ui_scope_local" translatable="false">LOCAL</string>
    <string name="ui_status_failed" translatable="false">FAILED</string>
</resources>
```

- [ ] **Step 3: Extract, one file at a time - worked example A (a constant)**

`SidrAssistant.kt:43-45` today:

```kotlin
internal const val SEND_READY_DESCRIPTION = "Send message"
internal const val SEND_EMPTY_DESCRIPTION = "Send message, unavailable until you type a message"
internal const val SEND_BUSY_DESCRIPTION = "Send message, unavailable while the assistant is replying"
```

Delete the constants; add to `values/strings.xml`:

```xml
<string name="ui_assistant_send_ready">Send message</string>
<string name="ui_assistant_send_empty">Send message, unavailable until you type a message</string>
<string name="ui_assistant_send_busy">Send message, unavailable while the assistant is replying</string>
```

and at the call site read `sidrString(R.string.ui_assistant_send_ready)` inside the composable.
**Any test importing those constants** switches to
`ApplicationProvider.getApplicationContext<Context>().getString(R.string.ui_assistant_send_ready)`.

- [ ] **Step 4: Worked example B (a `@Composable` default parameter)**

`SidrAssistant.kt:72` and `:132` today:

```kotlin
placeholder: String = "Message",
label: String = "Replying…",
```

become

```kotlin
placeholder: String = sidrString(R.string.ui_assistant_composer_placeholder),
label: String = sidrString(R.string.ui_assistant_streaming_label),
```

This compiles because default arguments of a `@Composable` function are evaluated in composition.
Note the ellipsis: `Replying…` is U+2026, copy it verbatim into the XML - do not retype as `...`, which
would move a golden.

Apply the same to `SidrActionSafety.kt` (`executeLabel = "Run"`, `secondaryLabel = "Not now"`,
`SidrSurfaceAction("Cancel")`), `SidrActionGate.kt` (`SidrSurfaceAction("Cancel")`),
`SidrForgetGate.kt` (`confirmLabel = "Forget"`), `SidrMemoryItem.kt` (`"Open"`, `"Edit"`, `"Forget"`,
`"local only"`, `"memory"`).

- [ ] **Step 5: Leave `@Preview` bodies alone**

Every literal inside a `@Preview`-annotated function stays a Kotlin literal (spec §3.3) - e.g.
`SidrActionGate.kt:150-169` (`"Open website"`, `"This will open https://github.com in your browser."`),
`SidrMemoryItem.kt:200-206`, `SidrForgetGate.kt:36-38`. These are the gallery fixtures the goldens
rasterise. Touching them is the single most likely cause of a moved golden.

- [ ] **Step 6: Make display casing locale-explicit**

In `SidrSystemLabel.kt`, `SidrProvenanceLine.kt`, and `SidrActionSafety.kt`, every bare `.uppercase()` /
`.lowercase()` on **display** text becomes `.uppercase(Locale.getDefault())` /
`.lowercase(Locale.getDefault())` (add `import java.util.Locale`). Do not change the `"ENABLED"`,
`"GRANTED"`, `"BLOCKED"`, `"DENIED"`, `"OPTIONAL"` comparisons in `SidrActionSafety.kt:48-50` - those are
**matching**, not display, and must stay locale-independent; if they call `.uppercase()` on their input,
use `Locale.ROOT` there and write the reason in a comment.

- [ ] **Step 7: Write `ru` and `tr` for every key added**

Both `values-ru/strings.xml` and `values-tr/strings.xml` get **every** key from `values/strings.xml` -
not the locked file, which AAPT forbids translating. Turkish note: `İptal`, `Aç`, `Unut`, `Şimdi değil`,
`Çalıştır` carry Turkish diacritics; IBM Plex Sans covers them (DS-11 verified Cyrillic on device).

- [ ] **Step 8: Verify - the goldens must be byte-identical**

```bash
eval $GRADLE :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug --rerun-tasks
git status --porcelain core/ui/src/test/screenshots/
```
Expected: SUCCESSFUL, same test count as Step 1, **empty** `git status` for screenshots.
If a golden moved: the cause is almost always XML escaping (`\'`, `\"`, `%%`) or a retyped Unicode
character (`…`, `·`, `→`, `->`). Diff the rendered text, fix the resource, re-run. Do **not** re-record.

- [ ] **Step 9: Commit**

```bash
git add core/ui/src/main core/ui/src/test
git commit -m "feat(i18n-1): extract core/ui strings + locked vocabulary + locale-explicit casing"
```

---

## Task 3: Barrier 2 - locale completeness

**Files:**
- Create: `app/src/test/java/com/sidr/launcher/i18n/LocaleCompletenessGuardTest.kt`

**Interfaces:**
- Consumes: the resource files from Task 2.
- Produces: the barrier every later extraction task is verified against.

- [ ] **Step 1: Write the guard**

```kotlin
package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * I18N-1 barrier 2 (spec §10.3). Walks every module's `res/values*/strings*.xml` and fails the build on:
 *  - a key present in `values` and missing from a MAIN locale (ru, tr);
 *  - a `translatable="false"` key that appears in any translated folder;
 *  - a key whose name does not carry its module prefix, or that collides across modules
 *    (`android.nonTransitiveRClass=true` hides the collision until the app merge picks a winner);
 *  - a translation whose placeholder set differs from the base - `%1$s` dropped in `ru` is an
 *    `IllegalFormatException` at runtime, which no screenshot can catch;
 *  - a `<plurals>` missing a quantity form its locale requires (ru: one/few/many/other; tr: one/other).
 *
 * Long-tail locales (anything outside MAIN_LOCALES) only WARN: a barrier nobody can satisfy is a
 * barrier somebody deletes.
 *
 * Unit-test working directory is the module dir (`app`), so the repo root is `..`.
 */
class LocaleCompletenessGuardTest {

    private val mainLocales = listOf("ru", "tr")

    private val requiredQuantities = mapOf(
        "ru" to setOf("one", "few", "many", "other"),
        "tr" to setOf("one", "other"),
    )

    /** Module res dir (relative to repo root) -> mandatory key prefix. */
    private val modulePrefixes = mapOf(
        "core/ui" to "ui_",
        "feature/launcher" to "launcher_",
        "feature/settings" to "settings_",
        "feature/prayer" to "prayer_",
        "feature/permission_education" to "perm_",
        "feature/assistant" to "assistant_",
        "app" to "app_",
    )

    private data class Entry(val name: String, val translatable: Boolean, val placeholders: Set<String>)

    private val placeholderPattern = Regex("""%(\d+\$)?[a-zA-Z]""")

    private fun parse(file: File): Pair<Map<String, Entry>, Map<String, Set<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, Entry>()
        val plurals = mutableMapOf<String, Set<String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            val name = el.getAttribute("name")
            strings[name] = Entry(
                name = name,
                translatable = el.getAttribute("translatable") != "false",
                placeholders = placeholderPattern.findAll(el.textContent).map { it.value }.toSet(),
            )
        }

        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            val quantities = mutableSetOf<String>()
            for (j in 0 until items.length) {
                quantities += (items.item(j) as Element).getAttribute("quantity")
            }
            plurals[el.getAttribute("name")] = quantities
        }
        return strings to plurals
    }

    private fun resFiles(moduleDir: File, locale: String?): List<File> {
        val dirName = if (locale == null) "values" else "values-$locale"
        val dir = File(moduleDir, "src/main/res/$dirName")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.extension == "xml" }
            ?.sortedBy { it.name }.orEmpty()
    }

    @Test fun main_locales_are_complete_and_consistent() {
        val repoRoot = File("..")
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val seenKeys = mutableMapOf<String, String>()   // key -> module that declared it

        for ((modulePath, prefix) in modulePrefixes) {
            val moduleDir = File(repoRoot, modulePath)
            val baseFiles = resFiles(moduleDir, null)
            if (baseFiles.isEmpty()) continue

            val base = mutableMapOf<String, Entry>()
            val basePlurals = mutableMapOf<String, Set<String>>()
            baseFiles.forEach { f -> parse(f).let { base += it.first; basePlurals += it.second } }

            base.keys.forEach { key ->
                if (!key.startsWith(prefix)) {
                    failures += "$modulePath: key '$key' must start with '$prefix'"
                }
                val owner = seenKeys.put(key, modulePath)
                if (owner != null) {
                    failures += "key '$key' declared in both $owner and $modulePath - " +
                        "resource merge silently keeps one"
                }
            }

            for (locale in mainLocales) {
                val translated = mutableMapOf<String, Entry>()
                val translatedPlurals = mutableMapOf<String, Set<String>>()
                resFiles(moduleDir, locale).forEach { f ->
                    parse(f).let { translated += it.first; translatedPlurals += it.second }
                }

                base.values.filter { it.translatable }.forEach { entry ->
                    val t = translated[entry.name]
                    if (t == null) {
                        failures += "$modulePath: '${entry.name}' missing from values-$locale"
                    } else if (t.placeholders != entry.placeholders) {
                        failures += "$modulePath: '${entry.name}' placeholder mismatch in values-$locale " +
                            "- base ${entry.placeholders}, $locale ${t.placeholders}"
                    }
                }

                base.values.filterNot { it.translatable }.forEach { entry ->
                    if (translated.containsKey(entry.name)) {
                        failures += "$modulePath: '${entry.name}' is translatable=\"false\" but appears " +
                            "in values-$locale"
                    }
                }

                basePlurals.keys.forEach { name ->
                    val quantities = translatedPlurals[name]
                    if (quantities == null) {
                        failures += "$modulePath: plurals '$name' missing from values-$locale"
                    } else {
                        val missing = requiredQuantities.getValue(locale) - quantities
                        if (missing.isNotEmpty()) {
                            failures += "$modulePath: plurals '$name' in values-$locale is missing " +
                                "quantity forms $missing"
                        }
                    }
                }
            }

            // Long-tail locales: warn only.
            val resRoot = File(moduleDir, "src/main/res")
            resRoot.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
                .map { it.name.removePrefix("values-") }
                .filter { it !in mainLocales && it.length <= 3 }
                .forEach { locale ->
                    val translated = mutableMapOf<String, Entry>()
                    resFiles(moduleDir, locale).forEach { f -> translated += parse(f).first }
                    val missing = base.values.filter { it.translatable && !translated.containsKey(it.name) }
                    if (missing.isNotEmpty()) {
                        warnings += "$modulePath: values-$locale is missing ${missing.size} keys"
                    }
                }
        }

        warnings.forEach { println("WARNING (long-tail locale): $it") }
        assertTrue(
            "Locale completeness failures (spec §10.3):\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }
}
```

- [ ] **Step 2: Run it against Task 2's output - expect PASS**

```bash
eval $GRADLE :app:testDebugUnitTest --tests '*LocaleCompletenessGuardTest*' --rerun-tasks
```
Expected: 1 test, 0 failures.

- [ ] **Step 3: Prove the barrier bites**

Temporarily delete one `<string>` line from `core/ui/src/main/res/values-ru/strings.xml`, re-run the
command from Step 2, and confirm it FAILS naming that key. Restore the line and confirm it passes again.
A guard nobody has seen fail is a guard nobody should trust.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/sidr/launcher/i18n/LocaleCompletenessGuardTest.kt
git commit -m "test(i18n-1): locale completeness barrier"
```

---

## Task 4: Pseudolocale spike + golden

**Files:**
- Modify: `core/ui/build.gradle.kts`
- Modify: `core/ui/src/test/java/com/sidr/launcher/core/ui/component/ControlsScreenshotTest.kt`
- Modify: `core/ui/src/test/java/com/sidr/launcher/core/ui/component/SidrAssistantScreenshotTest.kt`
- Create: `core/ui/src/test/screenshots/controls_pseudolocale.png`, `assistant_pseudolocale.png`

**Interfaces:**
- Consumes: `sidrString` (Task 1), `core/ui` resources (Task 2).
- Produces: the pseudolocale variant used as the layout-expansion barrier.

- [ ] **Step 1: Spike - can a library module even generate `en-XA`?**

Add to `core/ui/build.gradle.kts` inside `android { }`:

```kotlin
buildTypes {
    debug {
        isPseudoLocalesEnabled = true
    }
}
```

```bash
eval $GRADLE :core:ui:assembleDebug
```
Record one of two outcomes:
- **Configuration fails** (property unavailable on a library build type) → the real `en-XA` path is out;
  go to Step 3 (overlay fallback).
- **Build succeeds** → continue to Step 2.

- [ ] **Step 2: Spike - can Robolectric resolve `b+en+XA`?**

Add this temporary test to `core/ui/src/test/java/com/sidr/launcher/core/ui/i18n/PseudolocaleSpikeTest.kt`:

```kotlin
package com.sidr.launcher.core.ui.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.ui.R
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "b+en+XA")
class PseudolocaleSpikeTest {
    @Test fun pseudolocale_expands_strings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotEquals("Cancel", context.getString(R.string.ui_action_cancel))
    }
}
```

```bash
eval $GRADLE :core:ui:testDebugUnitTest --tests '*PseudolocaleSpikeTest*' --rerun-tasks
```
PASS → the real pseudolocale works; delete this spike test and use `@Config(qualifiers = "b+en+XA")` on
the golden captures in Step 4. FAIL → go to Step 3.

- [ ] **Step 3: Fallback only if Step 1 or 2 failed - overlay pseudolocale**

Revert the `buildTypes` block from Step 1, delete the spike test, and create
`core/ui/src/test/java/com/sidr/launcher/core/ui/i18n/PseudoLocaleOverlay.kt`:

```kotlin
package com.sidr.launcher.core.ui.i18n

/**
 * Test-only pseudolocale used when the platform's `en-XA` cannot be resolved from Robolectric
 * (I18N-1 Task 4, spec §10.4). Expands each string to ~1.5x and accents ASCII vowels, mimicking the
 * platform transform closely enough to expose layout that only fits English.
 *
 * This also exercises the production overlay seam end to end, which the real `en-XA` path would not.
 */
private val ACCENTS = mapOf('a' to 'á', 'e' to 'é', 'i' to 'í', 'o' to 'ó', 'u' to 'ú')

/** Wraps any base value: accents vowels and pads to ~1.5x length, preserving `%n$s` placeholders. */
fun pseudoLocaleOverlay(base: (String) -> String?): SidrStringOverlay = SidrStringOverlay { key ->
    base(key)?.let { value ->
        val accented = value.map { ACCENTS[it] ?: it }.joinToString("")
        val padding = "~".repeat((value.length * 0.5).toInt().coerceAtLeast(1))
        "[$accented $padding]"
    }
}
```

The capture then supplies base values from `context.resources` and provides the overlay via
`CompositionLocalProvider(LocalSidrStringOverlay provides pseudoLocaleOverlay { name -> ... })`.
**Record this substitution as a documented deviation in the ADR** (spec §10.4) - it is a different
transform from the platform's, and saying so is the point.

- [ ] **Step 4: Add the two captures**

In `ControlsScreenshotTest.kt`, beside `controls_dark` / `controls_light` / `controls_fontscale2` /
`controls_rtl`:

```kotlin
@Test fun controls_pseudolocale() {
    compose.setContent {
        SidrTheme(darkTheme = true) { ControlGallery() }
    }
    compose.onRoot().captureRoboImage("src/test/screenshots/controls_pseudolocale.png")
}
```

annotated per the Step 2/3 outcome (either the class-level `qualifiers = "b+en+XA"` variant of the test,
or the overlay provider wrapped around `ControlGallery()`). Add the equivalent
`assistant_pseudolocale` capture to `SidrAssistantScreenshotTest.kt`.

**Do not add a pseudolocale capture to the `memory` or `prayer_summary` galleries** (spec §10.4): their
visible text is caller-supplied sample data that stays a Kotlin literal, so the capture would be a
byte-identical copy of `*_dark`. Write that reason as a comment in each of those two test classes so the
next reader does not "fix" the omission.

- [ ] **Step 5: Record the two new goldens and verify**

```bash
eval $GRADLE :core:ui:recordRoborazziDebug --rerun-tasks --tests '*controls_pseudolocale*' --tests '*assistant_pseudolocale*'
eval $GRADLE :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug --rerun-tasks
git status --porcelain core/ui/src/test/screenshots/
```
Expected: exactly two **new** (`??`) files; **zero modified** (` M`) files. A modified golden here means
the recording pass touched an existing capture - investigate before continuing.

- [ ] **Step 6: Read the two new goldens**

Open both PNGs and confirm the expanded strings are visibly clipped or wrapped nowhere. If something
clips, that is a real finding: record it in the task report. Fix it only if it is inside `core/ui`;
tab-bar clipping is explicitly a later block (spec §3.6).

- [ ] **Step 7: Commit**

```bash
git add core/ui
git commit -m "test(i18n-1): pseudolocale golden for controls + assistant"
```

---

## Task 5: Extract `:app` (5 literals) + app name

**Files:**
- Create: `app/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:label`)
- Modify: `app/src/main/java/com/sidr/launcher/navigation/SidrTabScaffold.kt`,
  `app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `sidrString` (Task 1).
- Produces: `R.string.app_name` (locked), `app_tab_*` keys.

- [ ] **Step 1: Extract the tab labels and the app name**

`values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_tab_home">home</string>
    <string name="app_tab_apps">apps</string>
    <string name="app_tab_tasks">tasks</string>
    <string name="app_tab_agents">agents</string>
    <string name="app_tab_activity">activity</string>
</resources>
```

Keep the lowercase exactly as DS-11 shipped it - the tab bar is deliberately lowercase and icon-free.
Russian and Turkish translations are lowercase too (`дом`, `программы`, `задачи`, `агенты`,
`активность`; `ana sayfa`, `uygulamalar`, `görevler`, `ajanlar`, `etkinlik`).

`values/strings_locked.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name" translatable="false">Sidr Launcher</string>
</resources>
```

and in `AndroidManifest.xml` replace `android:label="Sidr Launcher"` with `android:label="@string/app_name"`.

- [ ] **Step 2: Verify**

```bash
eval $GRADLE :app:testDebugUnitTest :app:assembleDebug --rerun-tasks
```
Expected: SUCCESSFUL, 8 `:app` tests + the two guards from Tasks 1 and 3 all green.

Note for the device pass: DS-11 records that **`SidrTabBar` and `SidrAppFooter` have no automated visual
coverage** (Roborazzi is wired only in `:core:ui`), so the Russian tab labels are verified on device in
Task 16, not by a golden. Longer labels are expected to reveal the known fontScale-2.0 clipping - report
it, do not fix it here.

- [ ] **Step 3: Commit**

```bash
git add app/src/main
git commit -m "feat(i18n-1): extract :app strings + app name resource"
```

---

## Task 6: Extract `feature/permission_education` (15 literals)

**Files:**
- Create: `feature/permission_education/src/main/res/values/strings.xml`, `values-ru/`, `values-tr/`
- Create: `feature/permission_education/src/main/res/values/strings_locked.xml`
- Modify: `feature/permission_education/src/main/java/.../PermissionRationale.kt`,
  `.../PermissionEducationScreen.kt`

**Interfaces:**
- Consumes: `sidrString` (Task 1).
- Produces: `perm_*` keys.

- [ ] **Step 1: Split rationale copy into locked and free**

Permission rationale sentences are **consent copy** → `strings_locked.xml`, translatable, listed in the
owner-review package assembled in Task 15 (spec §7.2). Screen chrome (titles, button labels) goes to the
ordinary `strings.xml`.

`PermissionRationale.kt` currently returns rationale text per `PermissionFeature`. It stays a `when`,
but each branch returns `@StringRes` ids instead of sentences:

```kotlin
@StringRes
internal fun rationaleFor(feature: PermissionFeature): Int = when (feature) {
    PermissionFeature.WALLPAPER -> R.string.perm_rationale_wallpaper
    PermissionFeature.VOICE_INPUT -> R.string.perm_rationale_voice_input
    PermissionFeature.CALENDAR_SUGGESTIONS -> R.string.perm_rationale_calendar
    PermissionFeature.LOCATION_SUGGESTIONS -> R.string.perm_rationale_location
    PermissionFeature.PRAYER_LOCATION -> R.string.perm_rationale_prayer_location
}
```

Keep the `when` exhaustive with no `else` - the existing file's contract. Read the current branch text
verbatim into the XML; do not reword. Any copy change is a separate decision, not part of extraction.

- [ ] **Step 2: Verify**

```bash
eval $GRADLE :feature:permission_education:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL; the locale barrier from Task 3 passes with the new module's keys.

- [ ] **Step 3: Commit**

```bash
git add feature/permission_education/src/main
git commit -m "feat(i18n-1): extract permission education strings"
```

---

## Task 7: Extract `feature/prayer` (22 literals) + Diyanet terminology

**Files:**
- Create: `feature/prayer/src/main/res/values/strings.xml`, `values-ru/`, `values-tr/`
- Create: `feature/prayer/src/main/res/values/strings_locked.xml`
- Modify: `feature/prayer/src/main/java/.../PrayerSettingsScreen.kt`, `.../PrayerDetailScreen.kt`
- Create: `docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md` (start the review package here)

**Interfaces:**
- Consumes: `sidrString` (Task 1).
- Produces: `prayer_*` keys; the religious-terminology section of the owner-review package.

- [ ] **Step 1: All religious terminology goes to `strings_locked.xml`**

Prayer names, calculation-method names, and Asr madhab labels are locked-translated (spec §7.2). Turkish
is not a free translation here - it is Diyanet terminology (`İmsak`, `Güneş`, `Öğle`, `İkindi`, `Akşam`,
`Yatsı`). Russian uses the transliteration already in use in Russian-language Islamic sources
(`Фаджр`, `Восход`, `Зухр`, `Аср`, `Магриб`, `Иша`).

- [ ] **Step 2: Keep provenance untranslated**

`LOCAL CALC · <METHOD> · <MADHAB>` is a provenance line (spec §7.1): the frame stays
`translatable="false"`; only the method/madhab **names** it interpolates are locked-translated. The
provenance invariant from DS-6B - a prayer surface always states where its times came from - must still
hold after extraction; do not drop the line while rewriting it into a placeholder form.

- [ ] **Step 3: Start the owner-review package**

Create `docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md` with one table per locked key:
key, English, Russian draft, Turkish draft, and a "why locked" column. Task 15 completes it with the
consent copy; the owner reviews it once, not five times.

- [ ] **Step 4: Verify**

```bash
eval $GRADLE :feature:prayer:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL. `:feature:prayer` has no ViewModel-text change in this task.

- [ ] **Step 5: Commit**

```bash
git add feature/prayer/src/main docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md
git commit -m "feat(i18n-1): extract prayer strings + Diyanet terminology for review"
```

---

## Task 8: Extract `feature/settings` screens (43 literals)

**Files:**
- Create: `feature/settings/src/main/res/values/strings.xml`, `values-ru/`, `values-tr/`
- Modify: `feature/settings/src/main/java/.../SettingsScreen.kt`, `.../AliasesScreen.kt`,
  `.../LearnedChoicesScreen.kt`

**Interfaces:**
- Consumes: `sidrString` (Task 1).
- Produces: `settings_*` keys.

- [ ] **Step 1: Merge source-level concatenations into single strings**

`SettingsScreen.kt:167` and `:230` build one sentence with a Kotlin `+` across two source lines. That is
one string, not two:

```kotlin
description = "Let the bottom bar slide away after a few seconds of inactivity. Tap the " +
    "handle to bring it back.",
```
becomes one `<string name="settings_auto_hide_nav_description">…full sentence…</string>` read with
`sidrString`. Never split a sentence across two resources.

- [ ] **Step 2: Section headers are translated; they are not locked vocabulary**

`SidrSectionHeader(text = "APPEARANCE" | "ACCENT" | "SUGGESTIONS" | "HOME" | "PRAYER" | "MEMORY" |
"ASSISTANT" | "SYSTEM")` are navigation labels, not the machine-state vocabulary of spec §7.1, so they
are translated (`ВНЕШНИЙ ВИД`, `GÖRÜNÜM`, …). The uppercase look comes from `SidrTextRole.SYSTEM`, not
from the literal - do **not** pre-uppercase the Russian in the XML.

- [ ] **Step 3: Verify**

```bash
eval $GRADLE :feature:settings:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL, 33 `:feature:settings` tests still green (the mapper typing is Task 9).

- [ ] **Step 4: Commit**

```bash
git add feature/settings/src/main
git commit -m "feat(i18n-1): extract settings screen strings"
```

---

## Task 9: Type `LearnedChoiceMemoryUiModel`

**Files:**
- Modify: `feature/settings/src/main/java/.../LearnedChoiceMemoryUiModel.kt`
- Modify: `feature/settings/src/main/java/.../LearnedChoicesScreen.kt`
- Modify: `feature/settings/src/test/java/.../LearnedChoiceMemoryUiModelTest.kt` (if it asserts prose)

**Interfaces:**
- Consumes: `sidrString`, `settings_*` keys (Task 8).
- Produces: `LearnedChoiceEvidence` sealed type consumed by `LearnedChoicesScreen`.

- [ ] **Step 1: Replace prose with a typed value**

Today (`LearnedChoiceMemoryUiModel.kt:45-49`):

```kotlin
LearnedChoiceDisplayState.Unavailable -> "Target unavailable"
is LearnedChoiceDisplayState.Learning -> "Learning from confirmed choices (${streak}/${threshold})"
LearnedChoiceDisplayState.NeedsReconfirm -> "Needs reconfirmation before auto-open"
LearnedChoiceDisplayState.Auto -> "Based on confirmed choices"
LearnedChoiceDisplayState.AutoReady -> "Based on confirmed choices"
```

becomes:

```kotlin
internal sealed interface LearnedChoiceEvidence {
    data object Unavailable : LearnedChoiceEvidence
    data class Learning(val streak: Int, val threshold: Int) : LearnedChoiceEvidence
    data object NeedsReconfirm : LearnedChoiceEvidence
    data object Confirmed : LearnedChoiceEvidence
}
```

`Auto` and `AutoReady` both map to `Confirmed` because they already produce the identical sentence today
- that is preservation, not a merge of two different behaviours.

The screen resolves it:

```kotlin
@Composable
private fun evidenceText(evidence: LearnedChoiceEvidence): String = when (evidence) {
    LearnedChoiceEvidence.Unavailable -> sidrString(R.string.settings_learned_evidence_unavailable)
    is LearnedChoiceEvidence.Learning ->
        sidrString(R.string.settings_learned_evidence_learning, evidence.streak, evidence.threshold)
    LearnedChoiceEvidence.NeedsReconfirm -> sidrString(R.string.settings_learned_evidence_reconfirm)
    LearnedChoiceEvidence.Confirmed -> sidrString(R.string.settings_learned_evidence_confirmed)
}
```

with `<string name="settings_learned_evidence_learning">Learning from confirmed choices (%1$d/%2$d)</string>`.

- [ ] **Step 2: Update the mapper tests to assert types, not sentences**

Any assertion of the form `assertEquals("Based on confirmed choices", …)` becomes
`assertEquals(LearnedChoiceEvidence.Confirmed, …)`. Record the before/after test counts.

- [ ] **Step 3: Verify**

```bash
eval $GRADLE :feature:settings:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL; report the count (33 before) and which assertions changed.

- [ ] **Step 4: Commit**

```bash
git add feature/settings
git commit -m "refactor(i18n-1): type learned-choice evidence instead of prose"
```

---

## Task 10: `feature/assistant` - strings + typed errors

**Files:**
- Create: `feature/assistant/src/main/res/values/strings.xml`, `values-ru/`, `values-tr/`
- Create: `feature/assistant/src/main/res/values/strings_locked.xml`
- Modify: `feature/assistant/src/main/java/.../AssistantScreen.kt`,
  `.../AssistantPresentation.kt`, `.../AssistantViewModel.kt`
- Modify: `feature/assistant/src/test/java/.../AssistantPresentationTest.kt`,
  `.../AssistantViewModelTest.kt`

**Interfaces:**
- Consumes: `sidrString` (Task 1).
- Produces: `AssistantError` / `ProviderSaveError` typed values; `assistant_*` keys.

- [ ] **Step 1: Type the ViewModel's error text**

`AssistantViewModel.kt:205-210` builds sentences from `AiError`. Replace with a typed value carried in
the existing `UiError.Message` slot's place:

```kotlin
internal sealed interface AssistantError {
    data object MissingCredentials : AssistantError
    data object Unauthorized : AssistantError
    data object RateLimited : AssistantError
    data object Timeout : AssistantError
    data class ServerError(val statusCode: Int?) : AssistantError
    data class InvalidRequest(val detail: String?) : AssistantError
    data object Network : AssistantError
    data object Unknown : AssistantError
}
```

and `saveError: String?` becomes `saveError: ProviderSaveError?`:

```kotlin
internal enum class ProviderSaveError { BASE_URL_NOT_HTTPS, KEY_SAVE_FAILED, CONFIG_SAVE_FAILED }
```

**Do not touch the write ordering** in `saveProvider` - the DS-10 fix writes the key first and the
config last, and an ordering test guards it. This task changes only the *type* of the error field.

- [ ] **Step 2: Move the wording into `AssistantPresentation`**

`AssistantPresentation.kt` already owns DS-10's what/why/next copy and the action-label constants
(`RETRY_LABEL`, `PROVIDER_CTA_LABEL`, `UNKNOWN_HOST`). Those constants become `@StringRes` ids resolved
in `AssistantScreen`; the mapper returns ids plus arguments rather than sentences:

```kotlin
internal data class AssistantErrorPresentation(
    @StringRes val title: Int,
    @StringRes val whatFailed: Int,
    @StringRes val why: Int,
    val whyArg: String?,
    @StringRes val next: Int,
    @StringRes val primaryLabel: Int?,
)
```

`UNKNOWN_HOST` (`"(unknown host)"`) is a **neutral sentinel that must never be the raw URL** - keep that
property; it becomes `assistant_provider_unknown_host`, `translatable="false"` (it is machine data, not
copy).

- [ ] **Step 3: The cloud disclosure is locked-translated consent copy**

The "this leaves your device / goes to your provider" disclosure and the provenance line
(`CLOUD · <HOST> · <MODEL> · KEY IN KEYSTORE` / `LOCAL ONLY · NO PROVIDER CONFIGURED`) split as in
spec §7: the provenance tokens are `translatable="false"`; the disclosure sentence is locked-translated
and goes into the owner-review package started in Task 7.

- [ ] **Step 4: Update tests to typed assertions**

`AssistantPresentationTest` asserts ids instead of sentences; `AssistantViewModelTest` (21 tests before
DS-10's ordering test, 22 after) asserts `AssistantError` values. Record exact before/after counts.

- [ ] **Step 5: Verify**

```bash
eval $GRADLE :feature:assistant:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL, 35 `:feature:assistant` tests still green.

- [ ] **Step 6: Commit**

```bash
git add feature/assistant
git commit -m "feat(i18n-1): assistant strings + typed assistant errors"
```

---

## Task 11: Typed message contract in `domain` and `data`

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/intent/CommandMessage.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/intent/CommandFailure.kt`
- Modify: `domain/src/main/java/com/sidr/launcher/domain/intent/CommandOutcome.kt` (lines 28, 45),
  `ExecutableAction.kt:31`, `ActionExecutor.kt:33`, `IntentActionResolver.kt:61,67,69,70`,
  `HandleUserCommandUseCase.kt:81,90,103,110,111,159,160`, `ExecuteActionUseCase.kt:45,59,66,67,71`,
  `ai/router/RouteCommandUseCase.kt:61`
- Modify: `data/repository/src/main/java/.../intent/AndroidActionExecutor.kt:54,61,63,98,108,110,115-119`
- Modify: `domain/src/test/**` and `data/repository/src/test/**` assertions that compare these sentences

**Interfaces:**
- Produces: `CommandMessage`, `CommandFailure`; `CommandOutcome.Message(CommandMessage)`,
  `CommandOutcome.Failed(CommandFailure)`, `ExecutableAction.ShowMessageAction(CommandMessage)`,
  `ActionExecutionResult.Failure(CommandFailure)`. Task 12 consumes all four.

- [ ] **Step 1: Write the failing domain test**

Add to `domain/src/test/java/com/sidr/launcher/domain/intent/IntentActionResolverTest.kt`:

```kotlin
@Test fun no_app_found_carries_the_query_not_a_sentence() {
    val action = resolver.resolve(LauncherIntent.LaunchAppIntent(displayNameQuery = "telegram"))
    assertEquals(
        ExecutableAction.ShowMessageAction(CommandMessage.NoAppFound("telegram")),
        action,
    )
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
eval $GRADLE :domain:test --tests '*IntentActionResolverTest*'
```
Expected: compilation failure - `CommandMessage` unresolved.

- [ ] **Step 3: Add the two pure types**

`domain/src/main/java/com/sidr/launcher/domain/intent/CommandMessage.kt`:

```kotlin
package com.sidr.launcher.domain.intent

/**
 * A user-facing message named, not worded (I18N-1 spec §3.5).
 *
 * `domain` is pure Kotlin with no access to Android resources, so it must not decide wording - it says
 * *what happened* and the feature layer picks the sentence in the user's language. This is the doctrine
 * rule "user-facing text never originates in domain" made structural.
 */
sealed interface CommandMessage {

    /** Full help: the long variant that also lists `show apps, clear`. */
    data object Help : CommandMessage

    /** Short help: the variant `IntentActionResolver` produces. Kept distinct - the wording differs. */
    data object HelpBrief : CommandMessage

    /** No installed app matched [query]. */
    data class NoAppFound(val query: String) : CommandMessage

    data object ShowingAllApps : CommandMessage

    data object AssistantComingSoon : CommandMessage

    /**
     * Text from an external source - today only the LLM router's clarify question. Passed through
     * verbatim: it is model output, and its language is the model's, not ours.
     */
    data class Verbatim(val text: String) : CommandMessage
}
```

`domain/src/main/java/com/sidr/launcher/domain/intent/CommandFailure.kt`:

```kotlin
package com.sidr.launcher.domain.intent

/**
 * A technical failure named, not worded (I18N-1 spec §3.5). Display-safe by construction: a failure
 * carries no message, so no stack trace or PII can ride along in one.
 */
sealed interface CommandFailure {
    /** Was `SAFE_FAILURE_MESSAGE` - "Something went wrong. Please try again." */
    data object Generic : CommandFailure
    /** Was `AndroidActionExecutor.CANT_OPEN_APP`. */
    data object CantOpenApp : CommandFailure
    /** Was `NO_SEARCH_APP`. */
    data object NoSearchApp : CommandFailure
    /** Was `CANT_OPEN_URL`. */
    data object CantOpenUrl : CommandFailure
    /** Was `NO_STORE_APP`. */
    data object NoStoreApp : CommandFailure
}
```

- [ ] **Step 4: Change the four carriers**

```kotlin
// CommandOutcome.kt
data class Message(val message: CommandMessage) : CommandOutcome
data class Failed(val failure: CommandFailure) : CommandOutcome

// ExecutableAction.kt
data class ShowMessageAction(val message: CommandMessage) : ExecutableAction

// ActionExecutor.kt
data class Failure(val failure: CommandFailure) : ActionExecutionResult
```

Then update every producer. Exact mapping - no wording decisions left to the implementer:

| Site | Before | After |
|---|---|---|
| `IntentActionResolver:61` | `ShowMessageAction("No app found for \"$q\"")` | `ShowMessageAction(CommandMessage.NoAppFound(q))` |
| `IntentActionResolver:67` | `ShowMessageAction("Showing all apps")` | `ShowMessageAction(CommandMessage.ShowingAllApps)` |
| `IntentActionResolver:69` | `ShowMessageAction("Try: open <app>, search <query>")` | `ShowMessageAction(CommandMessage.HelpBrief)` |
| `IntentActionResolver:70` | `ShowMessageAction("Assistant coming soon")` | `ShowMessageAction(CommandMessage.AssistantComingSoon)` |
| `HandleUserCommandUseCase:90` | `CommandOutcome.Message(HELP_MESSAGE)` | `CommandOutcome.Message(CommandMessage.Help)` |
| `HandleUserCommandUseCase:81,111` | `CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)` | `CommandOutcome.Failed(CommandFailure.Generic)` |
| `ExecuteActionUseCase:45,67,71` | same constant | `CommandFailure.Generic` |
| `RouteCommandUseCase:61` | `CommandOutcome.Message(plan.question)` | `CommandOutcome.Message(CommandMessage.Verbatim(plan.question))` |
| `AndroidActionExecutor:54,61,63` | `Failure(CANT_OPEN_APP)` | `Failure(CommandFailure.CantOpenApp)` |
| `AndroidActionExecutor:98` | `Failure(NO_STORE_APP)` | `Failure(CommandFailure.NoStoreApp)` |
| `AndroidActionExecutor:108,110` | `Failure(failureMessage)` | `Failure(failure)` where the caller passes `CommandFailure.NoSearchApp` / `CommandFailure.CantOpenUrl` exactly as the constant it passed before |

Delete the now-unused constants `HELP_MESSAGE`, both `SAFE_FAILURE_MESSAGE`, `CANT_OPEN_APP`,
`NO_SEARCH_APP`, `CANT_OPEN_URL`, `NO_STORE_APP`. Keep `QUERY_PLACEHOLDER` - it is not user text.

- [ ] **Step 5: Update domain and data tests**

Replace sentence assertions with typed ones. **Do not change any non-text assertion**, any branch, or
any control flow. Record `:domain` (332 before) and `:data:repository` (167 before) counts after.

- [ ] **Step 6: Verify**

```bash
eval $GRADLE :domain:test :data:repository:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL. `:domain` still has no Android dependency - confirm with:

```bash
grep -rn "import android\." domain/src/main | head
```
Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add domain data/repository
git commit -m "refactor(i18n-1): type the command message/failure contract"
```

---

## Task 12: `feature/launcher` ViewModels - typed feedback + presentation mapper

**Files:**
- Modify: `feature/launcher/src/main/java/.../CommandFeedback.kt`
- Create: `feature/launcher/src/main/java/.../LauncherPresentation.kt`
- Modify: `feature/launcher/src/main/java/.../LauncherViewModel.kt` (lines 517-524, 533-560, 785, 812-816),
  `.../AppDrawerViewModel.kt:173-174`
- Modify: `feature/launcher/src/test/java/.../LauncherViewModelTest.kt`, `.../AppDrawerViewModelTest.kt`
- Test: `feature/launcher/src/test/java/.../LauncherPresentationTest.kt` (new)

**Interfaces:**
- Consumes: `CommandMessage`, `CommandFailure` (Task 11).
- Produces: typed `CommandFeedback`; `LauncherPresentation` mapping feedback → `@StringRes` + args,
  consumed by `LauncherScreen` in Task 13.

- [ ] **Step 1: Write the failing mapper test**

`feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherPresentationTest.kt`:

```kotlin
package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPresentationTest {

    @Test fun unknown_command_maps_to_its_string_with_english_command_examples() {
        val text = requireNotNull(feedbackText(CommandFeedback.UnknownCommand))
        assertEquals(R.string.launcher_feedback_unknown_command, text.id)
        assertEquals(listOf("open <app>", "search <query>"), text.args)
    }

    @Test fun none_and_dev_console_are_not_resource_backed() {
        assertEquals(null, feedbackText(CommandFeedback.None))
        assertEquals(null, feedbackText(CommandFeedback.Message("dev console on")))
    }

    @Test fun every_voice_error_has_its_own_string() {
        val ids = SpeechRecognitionError.entries
            .map { requireNotNull(feedbackText(CommandFeedback.VoiceError(it))).id }
        assertEquals(SpeechRecognitionError.entries.size, ids.toSet().size)
    }

    @Test fun domain_message_is_mapped_not_passed_through() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.NoAppFound("telegram"))))
        assertEquals(R.string.launcher_feedback_no_app_found, text.id)
        assertEquals(listOf("telegram"), text.args)
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
eval $GRADLE :feature:launcher:testDebugUnitTest --tests '*LauncherPresentationTest*'
```
Expected: compilation failure - `feedbackText`, `CommandFeedback.UnknownCommand` unresolved.

- [ ] **Step 3: Type `CommandFeedback`**

```kotlin
sealed interface CommandFeedback {
    data object None : CommandFeedback

    /** Dev-console output only - exempt from I18N-1 (spec §3.2). Never used by product surfaces. */
    data class Message(val text: String) : CommandFeedback

    /** Empty submit: show the "type a command" hint. */
    data object EmptyInput : CommandFeedback

    /** Below the suggest threshold: ask the user to be more specific. */
    data object LowConfidence : CommandFeedback

    /** Unrecognised input: show the usage examples. */
    data object UnknownCommand : CommandFeedback

    /** A message named by the domain (I18N-1 spec §3.5). */
    data class Domain(val message: CommandMessage) : CommandFeedback

    /** A technical failure named by the domain. */
    data class Failure(val failure: CommandFailure) : CommandFeedback

    /** Voice recognition failed - the enum is the message. */
    data class VoiceError(val error: SpeechRecognitionError) : CommandFeedback

    /** A medium-confidence suggestion the user can confirm by re-submitting. */
    data class Suggestion(val intent: SuggestedIntent) : CommandFeedback

    data class Ambiguous(val candidates: List<InstalledApp>) : CommandFeedback
}
```

`SuggestedIntent` carries what `describe()` used to word (`LauncherViewModel:812-816`):

```kotlin
sealed interface SuggestedIntent {
    data class LaunchApp(val query: String) : SuggestedIntent
    data class Search(val query: String) : SuggestedIntent
    data class OpenUrl(val url: String) : SuggestedIntent
}
```

- [ ] **Step 4: Write the mapper**

`LauncherPresentation.kt`:

```kotlin
package com.sidr.launcher.feature.launcher

import androidx.annotation.StringRes
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.voice.SpeechRecognitionError

/**
 * I18N-1 feature-local presentation mapping (spec §8), following the DS-10 `AssistantPresentation`
 * precedent: pure Kotlin, no Compose, so the *wording decision* is unit-testable without a Robolectric
 * host, and `LauncherScreen` stays a render.
 */
internal data class FeedbackText(@StringRes val id: Int, val args: List<String> = emptyList())

/**
 * The command grammar is English by construction - `RuleBasedIntentMatcher` parses English verbs - so
 * command examples are arguments from this object, never part of the translatable sentence. A
 * translated `open` would not parse (spec §4).
 */
internal object CommandExamples {
    const val OPEN_APP = "open <app>"
    const val SEARCH_QUERY = "search <query>"
    const val OPEN_TELEGRAM = "open telegram"
}

/**
 * `null` means "this branch is not resource-backed": [CommandFeedback.None] renders nothing, and
 * [CommandFeedback.Message] is dev-console output rendered verbatim (spec §3.2). Returning `null`
 * rather than throwing keeps a mis-routed dev string harmless.
 */
internal fun feedbackText(feedback: CommandFeedback): FeedbackText? = when (feedback) {
    CommandFeedback.None -> null
    is CommandFeedback.Message -> null
    CommandFeedback.EmptyInput ->
        FeedbackText(R.string.launcher_feedback_empty_input, listOf(CommandExamples.OPEN_TELEGRAM))
    CommandFeedback.LowConfidence -> FeedbackText(R.string.launcher_feedback_low_confidence)
    CommandFeedback.UnknownCommand -> FeedbackText(
        R.string.launcher_feedback_unknown_command,
        listOf(CommandExamples.OPEN_APP, CommandExamples.SEARCH_QUERY),
    )
    is CommandFeedback.Domain -> when (val m = feedback.message) {
        CommandMessage.Help ->
            FeedbackText(R.string.launcher_message_help, listOf(CommandExamples.OPEN_APP, CommandExamples.SEARCH_QUERY))
        CommandMessage.HelpBrief ->
            FeedbackText(R.string.launcher_message_help_brief, listOf(CommandExamples.OPEN_APP, CommandExamples.SEARCH_QUERY))
        is CommandMessage.NoAppFound -> FeedbackText(R.string.launcher_feedback_no_app_found, listOf(m.query))
        CommandMessage.ShowingAllApps -> FeedbackText(R.string.launcher_message_showing_all_apps)
        CommandMessage.AssistantComingSoon -> FeedbackText(R.string.launcher_message_assistant_soon)
        is CommandMessage.Verbatim -> FeedbackText(R.string.launcher_message_verbatim, listOf(m.text))
    }
    is CommandFeedback.Failure -> when (feedback.failure) {
        CommandFailure.Generic -> FeedbackText(R.string.launcher_failure_generic)
        CommandFailure.CantOpenApp -> FeedbackText(R.string.launcher_failure_cant_open_app)
        CommandFailure.NoSearchApp -> FeedbackText(R.string.launcher_failure_no_search_app)
        CommandFailure.CantOpenUrl -> FeedbackText(R.string.launcher_failure_cant_open_url)
        CommandFailure.NoStoreApp -> FeedbackText(R.string.launcher_failure_no_store_app)
    }
    is CommandFeedback.VoiceError -> FeedbackText(
        when (feedback.error) {
            SpeechRecognitionError.PERMISSION_DENIED -> R.string.launcher_voice_permission_denied
            SpeechRecognitionError.UNAVAILABLE -> R.string.launcher_voice_unavailable
            SpeechRecognitionError.NO_MATCH -> R.string.launcher_voice_no_match
            SpeechRecognitionError.BUSY -> R.string.launcher_voice_busy
            SpeechRecognitionError.NETWORK -> R.string.launcher_voice_network
            SpeechRecognitionError.TIMEOUT -> R.string.launcher_voice_timeout
            SpeechRecognitionError.UNKNOWN -> R.string.launcher_voice_unknown
        },
    )
    is CommandFeedback.Suggestion -> when (val i = feedback.intent) {
        is SuggestedIntent.LaunchApp -> FeedbackText(R.string.launcher_suggest_launch_app, listOf(i.query))
        is SuggestedIntent.Search -> FeedbackText(R.string.launcher_suggest_search, listOf(i.query))
        is SuggestedIntent.OpenUrl -> FeedbackText(R.string.launcher_suggest_open_url, listOf(i.url))
    }
    is CommandFeedback.Ambiguous -> FeedbackText(R.string.launcher_feedback_ambiguous)
}
```

`R.string.launcher_message_verbatim` is `%1$s` - a passthrough that still goes through the seam so an
overlay can see it.

- [ ] **Step 5: Update the ViewModels**

In `LauncherViewModel`, delete `voiceErrorMessage(...)` and `describe(...)`; each site emits the typed
variant instead. `applyOutcome` maps `CommandOutcome.Message(m)` → `CommandFeedback.Domain(m)` and
`CommandOutcome.Failed(f)` → `CommandFeedback.Failure(f)`. Line 785's
`CommandFeedback.Message(result.safeMessage)` becomes `CommandFeedback.Failure(result.failure)`.

`AppDrawerViewModel:173-174` gets the same treatment with a feature-local typed error carrying
`permission` / `feature` as an argument.

**Nothing else in either ViewModel changes.** Same branches, same `when` exhaustiveness, same
`SavedStateHandle` usage, same coroutine scopes.

- [ ] **Step 6: Run the mapper test - expect PASS, then the suites**

```bash
eval $GRADLE :feature:launcher:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL. Report the count (130 before) and list exactly which `LauncherViewModelTest`
assertions changed from sentence to type - this is the parity loss the spec predicted (§8), and it must
be enumerated, not summarised.

- [ ] **Step 7: Commit**

```bash
git add feature/launcher
git commit -m "refactor(i18n-1): typed launcher feedback + presentation mapper"
```

---

## Task 13: Extract `feature/launcher` screens (non-preview)

**Files:**
- Create: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/`, `values-tr/`
- Create: `feature/launcher/src/main/res/values/strings_locked.xml`
- Modify: `feature/launcher/src/main/java/.../LauncherScreen.kt`, `.../AppDrawerScreen.kt`,
  `.../PrayerSummaryMapper.kt`
- **Do not modify:** `feature/launcher/src/main/java/.../preview/*.kt` (exempt, spec §3.2)

**Interfaces:**
- Consumes: `sidrString` (Task 1), `feedbackText` (Task 12).
- Produces: `launcher_*` keys.

- [ ] **Step 1: Render the mapper output**

Wherever `LauncherScreen` renders `CommandFeedback`, resolve through the mapper:

```kotlin
val text = feedbackText(feedback)
when {
    text != null ->
        SidrText(text = sidrString(text.id, *text.args.toTypedArray()), role = SidrTextRole.HUMAN_BODY)
    feedback is CommandFeedback.Message ->      // dev console, exempt (spec §3.2)
        SidrText(text = feedback.text, role = SidrTextRole.HUMAN_BODY)
}
```

`feedbackText` returns `null` for `None` and for the dev-console `Message`; those are the only two
branches the screen words itself.

- [ ] **Step 2: Fix the remaining concatenations**

`AppDrawerScreen.kt:128` `"Ask assistant: \"$query\""` →
`<string name="launcher_drawer_ask_assistant">Ask assistant: \"%1$s\"</string>`.
`LauncherScreen.kt:658` `"This will run: ${pending.commandLine}"` →
`<string name="launcher_confirm_will_run">This will run: %1$s</string>`.
The `commandLine` argument itself stays English (command echo, spec §4).

- [ ] **Step 3: Locked and untouched items in this module**

- `PrayerSummaryMapper.kt:76` `LOCAL CALC · … · …` → provenance frame, `translatable="false"`.
- `LauncherScreen.kt:315` (Hijri + Gregorian date) - **already localized by the platform**; leave the
  formatting call alone, only the separator stays literal.
- `LauncherScreen.kt:626,631` (`"> ${line.command}"`, `"  ${line.result}"`) - dev console, exempt.

- [ ] **Step 4: `AppDrawerScreen` casing**

Apply the same display-casing rule as Task 2 Step 6: `.uppercase()`/`.lowercase()` used for **display**
takes `Locale.getDefault()`; anything used for **matching or sorting** stays as it is - the `Collator`
fix is explicitly a later block (spec §3.6). Add a one-line comment at each site saying which it is.

- [ ] **Step 5: Verify**

```bash
eval $GRADLE :feature:launcher:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: SUCCESSFUL; the locale barrier accepts the new `launcher_*` keys.

- [ ] **Step 6: Commit**

```bash
git add feature/launcher/src/main
git commit -m "feat(i18n-1): extract launcher screen strings"
```

---

## Task 14: Per-app language - appcompat, `locales_config`, Settings switcher

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/com/sidr/launcher/LauncherActivity.kt`
- Modify: `app/src/main/res/values/styles.xml`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/locales_config.xml`
- Modify: `feature/settings/src/main/java/.../SettingsScreen.kt` (+ its strings files)

**Interfaces:**
- Consumes: `settings_*` keys (Task 8).
- Produces: the language switcher; no new persisted preference key.

- [ ] **Step 1: Add the dependency**

`gradle/libs.versions.toml`:

```toml
appcompat = "1.7.0"
```
```toml
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
```
and in `app/build.gradle.kts` dependencies: `implementation(libs.androidx.appcompat)`.

- [ ] **Step 2: Declare the supported locales**

`app/src/main/res/xml/locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ru" />
    <locale android:name="tr" />
</locale-config>
```

In `AndroidManifest.xml`, on `<application>`: `android:localeConfig="@xml/locales_config"`, and inside
`<application>` enable appcompat's backported persistence:

```xml
<service
    android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
    android:enabled="false"
    android:exported="false">
    <meta-data
        android:name="autoStoreLocales"
        android:value="true" />
</service>
```

This is what makes `AppCompatDelegate` the single store on API 28-32, so **no `user_language` key enters
DataStore** and `PrivacyInventoryGuardTest` stays untouched (spec §9).

- [ ] **Step 3: Re-parent the theme and convert the activity**

`app/src/main/res/values/styles.xml`:

```xml
<style name="Theme.SidrLauncher" parent="Theme.AppCompat.NoActionBar">
    <item name="android:windowBackground">@color/sidr_ground</item>
</style>
```

The `windowBackground` item is load-bearing - it is why there is no white flash before Compose paints
(AIL-0). Keep it exactly.

In `LauncherActivity.kt`, change `ComponentActivity` to
`androidx.appcompat.app.AppCompatActivity`. Change nothing else: `@AndroidEntryPoint`, the injected
repositories, `homeResetSignal`, the edge-to-edge call, and `onNewIntent` behaviour all stay as they are.

- [ ] **Step 4: Add the Settings switcher**

In `SettingsScreen.kt`, under a new `SidrSectionHeader(text = sidrString(R.string.settings_section_language))`:

```kotlin
val current = AppCompatDelegate.getApplicationLocales()
    .toLanguageTags().takeIf { it.isNotBlank() }?.substringBefore('-') ?: ""
listOf("" to R.string.settings_language_system,
       "en" to R.string.settings_language_en,
       "ru" to R.string.settings_language_ru,
       "tr" to R.string.settings_language_tr).forEach { (tag, labelRes) ->
    SidrSelectableRow(
        title = sidrString(labelRes),
        selected = current == tag,
        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
    )
}
```

Reuse the exact composable the APPEARANCE options already use (`SettingsScreen.kt:132-140`, the block
under `SidrSectionHeader(text = "APPEARANCE")` that renders `title = label` per option) rather than
introducing a new control - the LANGUAGE section must look like the sections around it. The empty tag means "follow
the system", which is what `LocaleListCompat.getEmptyLocaleList()` expresses.

**`AppCompatDelegate.setApplicationLocales` is the only writer.** Do not add a DataStore key, do not
cache the choice anywhere, and read the current value back from `getApplicationLocales()` so the system
picker and this switcher can never disagree.

- [ ] **Step 5: Verify**

```bash
eval $GRADLE :app:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDebug --rerun-tasks
```
Expected: SUCCESSFUL. Also re-run the privacy guard explicitly, since the claim "no new preference key"
must be proven, not asserted:

```bash
eval $GRADLE :data:repository:testDebugUnitTest --tests '*PrivacyInventoryGuardTest*' --rerun-tasks
```

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app feature/settings
git commit -m "feat(i18n-1): per-app language via AppCompatDelegate + locales_config"
```

---

## Task 15: Barrier 1 - the hardcoded-literal guard

**Files:**
- Create: `app/src/test/java/com/sidr/launcher/i18n/HardcodedUiTextGuardTest.kt`
- Modify: `docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md` (complete the review package)

**Interfaces:**
- Consumes: everything extracted in Tasks 2-14.
- Produces: the barrier that keeps the extraction from decaying.

- [ ] **Step 1: Write the guard**

```kotlin
package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I18N-1 barrier 1 (spec §10.1). Fails the build when a new user-facing literal is hardcoded in a
 * Composable instead of going through `sidrString(R.string.…)`.
 *
 * Heuristic by necessity (this project is 100% Compose and Lint's HardcodedText only reads XML
 * layouts, which do not exist here). It catches regression; it does not prove absence - that is why
 * `StringSeamGuardTest` exists alongside it as an exact check.
 *
 * Working directory is the module dir (`app`), so the repo root is `..`.
 */
class HardcodedUiTextGuardTest {

    /** Text sinks: a literal on one of these lines is user-facing until proven otherwise. */
    private val sinks = listOf(
        "text =", "label =", "title =", "contentDescription =", "placeholder =",
        "description =", "consequence =", "evidence =", "provenance =",
        "confirmLabel =", "secondaryLabel =", "executeLabel =",
        "SidrText(", "Text(", "SidrSurfaceAction(",
    )

    /**
     * Owner-approved exemptions (spec §3.2). Each entry states WHY, so the list stays a decision
     * record rather than a place to hide new offences.
     */
    private val exemptions = mapOf(
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/TasksPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/AgentsPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/ActivityPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/TerminalPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/MomentsPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
    )

    /** A literal is only interesting if it contains a letter: `[ %1$s ]` and `> ` are decoration. */
    private val literalPattern = Regex("\"([^\"\\\\]|\\\\.){2,}\"")

    private fun hasLetter(s: String) = s.any { it.isLetter() }

    @Test fun no_hardcoded_user_text_in_composables() {
        val repoRoot = File("..")
        val offenders = mutableListOf<String>()

        repoRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/src/main/") && !it.path.contains("/build/") }
            .forEach { file ->
                val relative = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
                if (exemptions.containsKey(relative)) return@forEach

                var previewDepth = 0
                var inPreview = false
                file.readLines().forEach { raw ->
                    val line = raw.trim()

                    // Skip @Preview function bodies: gallery fixtures are not product copy (spec §3.3).
                    if (line.startsWith("@Preview")) { inPreview = true; previewDepth = 0 }
                    if (inPreview) {
                        previewDepth += line.count { it == '{' } - line.count { it == '}' }
                        if (previewDepth <= 0 && line.contains("}")) inPreview = false
                        return@forEach
                    }
                    if (line.startsWith("//") || line.startsWith("*")) return@forEach
                    if (!sinks.any { line.contains(it) }) return@forEach

                    literalPattern.findAll(line)
                        .map { it.value.trim('"') }
                        .filter { hasLetter(it) }
                        .forEach { offenders += "$relative: $line" }
                }
            }

        assertTrue(
            "Hardcoded user-facing text must go through sidrString(R.string.…) (spec §10.1). " +
                "If a site is genuinely exempt, add it to `exemptions` WITH a reason:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun every_exemption_states_a_reason() {
        val blank = exemptions.filterValues { it.isBlank() }.keys
        assertTrue("Exemptions must carry a reason: $blank", blank.isEmpty())
    }
}
```

- [ ] **Step 2: Run it - it will fail, and the failures are the work list**

```bash
eval $GRADLE :app:testDebugUnitTest --tests '*HardcodedUiTextGuardTest*' --rerun-tasks
```
Expected on first run: FAIL, listing whatever Tasks 2-13 missed. Every entry is either a real miss (fix
it by extracting) or a false positive (a `text =` on a non-user string, e.g. a route or a test tag). For
false positives, narrow the *pattern* - do not add a file to `exemptions`, which is reserved for the two
owner-approved categories.

- [ ] **Step 3: Iterate until green**

Re-run after each fix. When it passes, the extraction is complete by the barrier's definition.

- [ ] **Step 4: Prove the barrier bites**

Add `SidrText(text = "Temporary regression probe")` to any production composable, run the guard, and
confirm it FAILS naming that file and line. Remove the probe and confirm it passes.

- [ ] **Step 5: Complete the owner-review package**

Finish `docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md`: the §7.2 locked-translated consent
copy (cloud disclosure, "Nothing was saved", destructive-gate consequences, permission rationale) plus
the Diyanet terminology started in Task 7. One table, three columns of text plus the reason each key is
locked. Nothing else goes to the owner - the free translations ship on the draft (spec §12).

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/sidr/launcher/i18n docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md
git commit -m "test(i18n-1): hardcoded user-text barrier"
```

---

## Task 16: Full gate, device smoke, documentation

**Files:**
- Modify: `CLAUDE.md` (Hard rules + digest + Contract → Owner table)
- Modify: `ai-context/decisions.md` (new ADR)
- Modify: `ai-context/current-status.md`
- Modify: `docs/superpowers/plans/2026-08-11-i18n-1-multilingual.md` (STATUS)

- [ ] **Step 1: Run the full gate, honestly**

```bash
eval $GRADLE :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :domain:test testDebugUnitTest assembleDebug --rerun-tasks
echo "EXIT: $?"
```
Expected: `BUILD SUCCESSFUL`, exit 0. Not piped through `tail`. If anything is `UP-TO-DATE`, re-run with
`--rerun-tasks` (already included). Record per-module test counts and compare against the baselines in
this plan: `:domain` 332, `:core:ui` 119, `:feature:launcher` 130, `:feature:settings` 33,
`:feature:assistant` 35, `:data:repository` 167, `:app` 8.

- [ ] **Step 2: Prove the goldens did not move**

```bash
git status --porcelain core/ui/src/test/screenshots/
```
Expected: exactly two new files (`controls_pseudolocale.png`, `assistant_pseudolocale.png`) and **zero**
modified. If any golden shows ` M`, stop and investigate - do not re-record (Global Constraints).

- [ ] **Step 3: Install and run the device smoke**

```bash
adb devices                 # expect RF8R705H38F
eval $GRADLE :app:installDebug
adb shell am start -W -n com.sidr.launcher/.LauncherActivity
```

Cover, screenshotting each (`uiautomator dump` misses parts of the tree - verify with a screenshot):

1. in-app switch `en → ru → tr` via Settings → LANGUAGE;
2. system per-app language (Settings → Apps → Sidr Launcher → Language) - **ask the owner before
   changing any system-wide setting**;
3. every migrated screen in `ru` and in `tr`: no English left, nothing clipped;
4. relaunch and force-stop → the chosen language survives;
5. cold start: no white flash; record `am start -W` TotalTime and compare with the ~766 ms baseline;
6. Turkish `İ` renders in `SidrTextRole.SYSTEM` labels;
7. **the Home date line follows the app language, not the device language.** `HomeDateLine`
   (`LauncherScreen.kt:315`) formats both dates with `Locale.getDefault()`
   (`LauncherScreen.kt:416,426`) - the one piece of UI that was already localized before this block.
   Test with app and system languages **deliberately different** (system `ru`, app `tr`): if the date
   stays on the system language, `Locale.getDefault()` is not picking up the per-app locale and the
   formatters must read the locale from the configuration instead. Note the line hides while the input
   is active, so check it on idle Home.

Remember: the bottom chrome auto-hides after 5 s - tap the handle (≈540,2264) and the target in one
command; press `keyevent 4` before any swipe if the keyboard is open. **Never touch mobile data, Wi-Fi,
or airplane mode.**

- [ ] **Step 4: Report what was not covered**

Anything unverified is stated with its reason - the five PREVIEW tabs staying English (spec §3.2), any
offline path (tethering), TalkBack, fontScale 2.0 clipping (a later block). An honest gap beats a
confident claim.

- [ ] **Step 5: Write the ADR**

Append to `ai-context/decisions.md`: **"2026-08-11 — I18N-1 Multilingual UI complete"** covering the
seam and why the overlay is keyed by entry name; the two exemptions and their cost; the typed
domain/data message contract and that it was a scope expansion discovered while planning; the
**honest parity statement** (which VM suites changed and why byte-for-byte is not claimable); the
pseudolocale outcome (real `en-XA` or the documented overlay deviation); the appcompat/theme change and
its startup evidence; per-module test counts before/after; and **how many `<plurals>` the extraction
actually needed** - if the answer is zero, say so plainly and note that barrier 2's quantity check ships
dormant rather than inventing a plural to justify it (spec §5.1).

- [ ] **Step 6: Add the two doctrine rules**

In `CLAUDE.md` **Hard rules**:

```markdown
- **User-facing text never originates in `domain` — and not in a ViewModel either.** Domain and
  ViewModels emit typed results (`CommandMessage`, `CommandFailure`, `CommandFeedback`); the feature
  layer chooses the string via `sidrString(R.string.…)`. Enforced by `HardcodedUiTextGuardTest` and
  `StringSeamGuardTest`.
- **Strings and all main-locale translations ship in the same commit as the feature.** A block is not
  gate-green until `en`/`ru`/`tr` are complete — enforced by `LocaleCompletenessGuardTest`.
```

Add to the Contract → Owner table: `sidrString` / `SidrStringOverlay` → `core/ui`;
`locales_config.xml` + language switcher + i18n guards → `app`.

- [ ] **Step 7: Sync the digest and status**

Update the `CLAUDE.md` digest with an I18N-1 entry in the style of the DS-11 entry (what shipped, what is
device-verified, what is not, known gaps), and `ai-context/current-status.md` accordingly. Set this
plan's `STATUS:` to `CLOSED` (or `CODE-CLOSED, device-pending` if the smoke could not run).

- [ ] **Step 8: Commit**

```bash
git add CLAUDE.md ai-context docs/superpowers/plans/2026-08-11-i18n-1-multilingual.md
git commit -m "docs(i18n-1): ADR, doctrine rules, status sync"
```

---

## Appendix: task order and why

Tasks 1 → 4 build the machinery and the two barriers that can be green from the start (the seam guard and
the locale guard). Barrier 1 is deliberately **last** (Task 15): added early it would fail on all ~166
un-extracted sites and force someone to weaken it. Task 11 (domain typing) precedes Task 12 (ViewModel
typing), which precedes Task 13 (screen rendering), because each consumes the previous one's types.
Task 14 (appcompat) is late because it touches the startup path and its risk should not be entangled with
extraction failures.
