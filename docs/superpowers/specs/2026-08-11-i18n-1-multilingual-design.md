# I18N-1 - Multilingual UI (Design Spec)

> **Status: PROPOSED (2026-08-11).** I18N-1 extracts every in-scope user-facing string into Android
> string resources behind an overlay-capable wrapper, ships `en` / `ru` / `tr`, adds per-app language
> selection, and installs three regression barriers. It runs **before** the DS v1.1 release gate so the
> fontScale-2.0 and RTL smoke passes happen once, on localized strings, not twice.
>
> **Governing sources:** owner brief 2026-08-10, `CLAUDE.md` (DS-11 entry: "multilingual UI = its own
> post-gate block"), the existing guard-test idiom (`PrivacyInventoryGuardTest`,
> `ControlsDependencyGuardTest`, `RoomColumnNamesGuardTest`), and the DS-10 `AssistantPresentation`
> precedent for feature-local presentation mappers.
>
> **Hard rule this block obeys:** no behaviour change. No ViewModel, domain, or data **logic** edit; no
> navigation or command-pipeline edit. `domain` stays pure Kotlin; `core/ui` imports no
> domain/data/feature; no new `feature -> feature` edge; router-off parity, the offline core, and the
> outbound privacy allow-list are untouched (this block sends nothing outbound).
>
> **Scope expansion, owner decision 2026-08-11:** the *message contract* of `domain` and
> `AndroidActionExecutor` is typed (§3.5). Same branches, same control flow, typed payload instead of a
> baked English sentence. This is the one deliberate exception to "no domain/data edit", and it makes
> `domain` purer rather than less pure.

## 1. Goal

Make the shipped interface speak the user's language. Today it is half-translated by accident: the Home
date is localized by the platform while every other string is an English Kotlin literal, so a Russian
device shows a Russian date above English chrome.

Three outcomes:

1. every in-scope user-facing string lives in `res/values/strings.xml`, read through one wrapper that a
   future runtime overlay can intercept;
2. `en` / `ru` / `tr` ship together with the code that uses them;
3. three barriers make the regression - a new hardcoded literal, a key missing from a main locale, a
   layout that breaks on longer strings - fail the build instead of reaching a device.

## 2. Verified baseline (2026-08-11)

Confirmed by inspection, not assumed:

- **Zero i18n infrastructure.** No `strings.xml` anywhere; no `values-*` directory; `stringResource` has
  zero occurrences. The only `res/values` files are `app/.../colors.xml` and `app/.../styles.xml`.
- **~236 hardcoded user-facing literals** across `core/ui` 62, `feature/launcher` 75,
  `feature/settings` 43, `feature/prayer` 22, `feature/permission_education` 15, `feature/assistant` 14,
  `app` 5.
- **User text outside the UI layer** in `LauncherViewModel` (command feedback, 7 voice-recognition
  errors, 3 "did you mean" suggestions), `AssistantViewModel` (3 `saveError` strings + 6 `toUiError`
  messages), `AppDrawerViewModel` (2 `UiError.Message`), and the `LearnedChoiceMemoryUiModel` mapper (6).
  That is **~27 sites, not the ~10 in the brief** - the voice-error table and the suggestion prompts were
  not counted there. The shape of the work is unchanged; the size of §8 is roughly doubled.
- **`UiError` already has typed variants** (`Network`, `Unknown`) beside `Message(String)`, and
  `CommandFeedback` already has typed variants (`None`, `Ambiguous(candidates)`) beside `Message(String)`.
  Typing the remaining text extends an existing pattern; it does not invent one.
- **`LauncherActivity` is a bare `ComponentActivity`** on `Theme.SidrLauncher`, whose parent is
  `android:style/Theme.Material.NoActionBar`. `androidx.appcompat` is **not** a dependency of this project.
- **`core/ui` unit tests already resolve Android resources** (`testOptions.unitTests.isIncludeAndroidResources
  = true`), which is what lets Robolectric render `stringResource` in the Roborazzi goldens.
- **Release shrinks resources** (`isShrinkResources = true`), which forbids any name-based resource lookup.
- **11 user-facing strings are born below the UI layer** - 7 in `domain`, 4 in
  `AndroidActionExecutor`. Full table in §3.5; this was not in the brief and expands scope.
- **Android Lint is not wired into any gate**, and its `HardcodedText` check only inspects XML layouts,
  of which this project has none. Lint cannot be the barrier here; a guard test must be.

## 3. Scope

### 3.1 In scope

All user-facing text in `core/ui`, `feature/settings`, `feature/prayer`, `feature/permission_education`,
`feature/assistant`, `app`, and the non-preview part of `feature/launcher`, plus the ~25 literals living
in ViewModels and mappers. Roughly 166 sites, expected to collapse to ~150 keys after de-duplication.

### 3.2 Explicit exemptions (owner decision 2026-08-10)

Both are recorded in a single named exemption list inside the barrier-1 guard, each entry carrying its
reason - not a silent hole in the regex.

1. **PREVIEW mock-up screens** - `feature/launcher/.../preview/TasksPreviewScreen.kt`,
   `AgentsPreviewScreen.kt`, `ActivityPreviewScreen.kt`, `TerminalPreviewScreen.kt`,
   `MomentsPreviewScreen.kt` (~58 literals). Reason: `PREVIEW`-badged, non-functional mock-ups that the
   A-stage replaces wholesale; translating them spends ~174 translations on text scheduled for deletion.
   **Consequence, to be stated in the device report:** on a `ru`/`tr` device these five tabs stay English.
2. **The hidden dev console** - the 7-tap dev-mode arm, `//dev-mode` toggle, `outcomeSummary` labels
   (~12 literals in `LauncherViewModel`). Reason: a debug surface, not product. `outcomeSummary` values
   are effectively pipeline state names and are more useful stable than translated.

### 3.3 Not touched

**Sample data inside `@Preview` and gallery functions stays a Kotlin literal.** That text is what the
Roborazzi goldens rasterise; it is developer fixture, not product copy. Moving it is the single change
most likely to shift a golden, and it buys nothing.

### 3.4 Locale-correct display casing (in scope, owner decision 2026-08-10)

Of the three locale defects recorded in `CLAUDE.md`, this block fixes **only the display-side one**: the
`Locale`-less `.uppercase()` / `.lowercase()` calls in `SidrSystemLabel`, `SidrProvenanceLine`,
`SidrActionSafety`, and `AppDrawerScreen` become explicit `Locale.getDefault()`. Display casing should
follow the user's locale - Turkish `i -> İ` is correct there - and making it explicit documents that
intent instead of inheriting whatever the JVM default happens to be.

This is **pixel-neutral on ASCII**: `"run".uppercase()` and `"run".uppercase(Locale.getDefault())` are
identical under the `en` default the goldens render in, so §11 still holds.

The other two defects stay out (§3.6): `Collator` sorting and locale-safe matching live in
`InstalledAppsRepositoryImpl` (data layer), and forced caps + tracking on `SidrTextRole.SYSTEM` is an
Arabic problem, and Arabic is out of scope.

### 3.5 Typed message contract in `domain` and `data` (scope expansion, owner decision 2026-08-11)

Discovered while planning, not assumed at brief time: **11 user-facing strings are born below the UI**,
where no resource lookup exists.

| Site | String |
|---|---|
| `IntentActionResolver:61` | `No app found for "<query>"` |
| `IntentActionResolver:67,69,70` | `Showing all apps`, `Try: open <app>, search <query>`, `Assistant coming soon` |
| `HandleUserCommandUseCase:159,160` | `Something went wrong. Please try again.`, `Try: open <app>, search <query>, show apps, clear` |
| `ExecuteActionUseCase:71` | `Something went wrong. Please try again.` |
| `AndroidActionExecutor:116-119` | `Couldn't open that app.`, `No app available to handle that search.`, `No app available to open that link.`, `Couldn't open the Play Store.` |

`No app found for "…"` is the most common message on Home. Leaving these English would put English on the
flagship migrated screen, and would make the §13 doctrine rule false on the day it is written.

Two new pure domain types name *what happened* without wording it:

```kotlin
sealed interface CommandMessage {
    data object Help : CommandMessage                      // HandleUserCommandUseCase.HELP_MESSAGE
    data object HelpBrief : CommandMessage                 // IntentActionResolver's shorter variant
    data class NoAppFound(val query: String) : CommandMessage
    data object ShowingAllApps : CommandMessage
    data object AssistantComingSoon : CommandMessage
    /** Verbatim text from an external source (the LLM router's clarify question). Not localizable. */
    data class Verbatim(val text: String) : CommandMessage
}

sealed interface CommandFailure {
    data object Generic : CommandFailure        // SAFE_FAILURE_MESSAGE
    data object CantOpenApp : CommandFailure    // AndroidActionExecutor.CANT_OPEN_APP
    data object NoSearchApp : CommandFailure    // NO_SEARCH_APP
    data object CantOpenUrl : CommandFailure    // CANT_OPEN_URL
    data object NoStoreApp : CommandFailure     // NO_STORE_APP
}
```

Carried by `CommandOutcome.Message(CommandMessage)`, `CommandOutcome.Failed(CommandFailure)`,
`ExecutableAction.ShowMessageAction(CommandMessage)`, `ActionExecutionResult.Failure(CommandFailure)`.
The feature-layer `LauncherPresentation` mapper turns them into resources.

Rules for this expansion:

- **Both HELP variants are preserved as distinct types.** Today two different English help strings exist
  (`HandleUserCommandUseCase` includes `show apps, clear`; `IntentActionResolver` does not). Collapsing
  them would change what a user sees, which is a behaviour change. They stay distinct even if one branch
  turns out to be unreachable - that possible dead branch is reported, not "fixed", by this block.
- **`PlanResult.Clarify.question` stays a passthrough** (`CommandMessage.Verbatim`). It is model output;
  its language is the model's, and pretending otherwise would be dishonest.
- Control flow, branch structure, and every non-text field are untouched. `:domain` gains no dependency
  and stays stdlib + coroutines.
- `:domain` and `:data:repository` test suites change **only** where they assert these exact sentences;
  the plan reports per-suite counts before and after.

### 3.6 Out of scope (do not widen)

- **`translate_ui` (AI translation at runtime)** - A-stage, after A1 Tool & Capability. This block ships
  only the extension point of §6.
- **Arabic and any RTL language** - blocked on the typeface decision the owner has not taken (JetBrains
  Mono has no Arabic; `SidrSans`/`sacred` coverage is unresolved). The `rtl` golden variant stays as it
  is today - a layout-direction test, not a language.
- **Typeface change and removing the CRT tracking/caps** of `SidrTextRole.SYSTEM` - a separate block,
  before or parallel to this one, at the owner's discretion.
- **Structural fix for tab-bar label clipping at fontScale 2.0** - deliberately a *later* block, so it is
  fixed against long Russian strings rather than short English ones.
- **Locale-correctness in the data layer** - `Collator`-based app sorting and locale-safe search matching
  in `InstalledAppsRepositoryImpl`. These change data-layer behaviour, which this block forbids. Deferred
  to I18N-2 and named as a known defect (Cyrillic currently sorts by code point).
- **The command grammar itself.** See §4.
- **Voice recognition language.** `LauncherViewModel.startVoiceInput(languageTag = null)` never passes a
  tag, so `AndroidSpeechInputSource` omits `RecognizerIntent.EXTRA_LANGUAGE` and the recognizer follows
  the **device** language, not the app language. I18N-1 does not change this: wiring the app locale into
  the recognizer only pays off once the matcher accepts non-English verbs (§4), and doing one without
  the other would transcribe Russian speech into a matcher that cannot parse it. Recorded here as a
  known gap rather than left to be discovered on device.

## 4. What multilingual does *not* mean here

The **UI becomes multilingual; the command language does not.** `RuleBasedIntentMatcher` parses English
verbs, so a Russian user still types `open telegram`, not `открой telegram`. This is a real product
limitation and it is stated here so it is a decision rather than a surprise:

- **Command echo strings stay English.** `open ${query}`, `search ${query}`, `install ${query}`,
  `settings`, `assistant` (`LauncherViewModel.commandLine`) mirror the grammar the user must type. A
  translated echo above a `> ` prompt would teach the wrong syntax.
- **Command examples inside translated sentences are passed as format arguments**, never inlined into the
  translatable text. `"Unknown command. Try: open <app>, search <query>"` becomes a translated sentence
  with `%1$s` / `%2$s` filled from an English-only constants object, so no translator can accidentally
  localize a keyword the matcher will then fail to parse.

Localizing the command grammar is a domain change (matcher vocabulary) and belongs to a future block.

## 5. Resource layout and key naming

Per-module `res/values/strings.xml`, the Android default: `core/ui` owns its strings, each feature owns
its own. The failure mode this invites is silent: **duplicate resource names across modules collide at
merge time, last module wins, no error**. Two mitigations, both machine-checked by barrier 2:

- every key carries its module prefix: `ui_`, `launcher_`, `settings_`, `prayer_`, `assistant_`,
  `perm_`, `app_`;
- key names are globally unique across all modules.

Naming shape: `<module>_<surface>_<meaning>`, e.g. `ui_action_run`, `launcher_feedback_unknown_command`,
`assistant_error_rate_limited`, `prayer_method_diyanet`.

### 5.1 Placeholders and plurals

- **No concatenation.** Any sentence that today interpolates a value becomes a single translatable string
  with positional placeholders (`%1$s`), never a runtime `"prefix " + value`. Confirmed sites include
  `"Ask assistant: \"$query\""`, `"This will run: ${commandLine}"`, `"Did you mean to open \"$q\"?"`,
  `"Search the web for \"$q\"?"`, `"Permission denied: $permission"`, `"Next $prayer $time"`,
  `"Forget $type $title"`.
- **Every count-bearing string is a `<plurals>`**, because `ru` needs `one/few/many/other` and `tr` needs
  `one/other` where English needs two forms. The task-1 inventory determines the actual set; the honest
  expectation is that it is small (the one obvious count site, `"? N candidates"`, is dev-console and
  exempt). If the inventory finds none, that is recorded as a fact and the barrier's quantity check ships
  dormant rather than a plural being invented to justify it.
- Punctuation frames (`[ %1$s ]`, `> %1$s`) stay in Kotlin. They are decoration in the design system, not
  translatable text, and barrier 1 ignores literals containing no letters.

## 6. The overlay seam

The point of the block. All reads go through one wrapper in `core/ui/i18n/SidrStrings.kt`:

```kotlin
fun interface SidrStringOverlay {
    /** Overlay value for [key] (the resource entry name), or null to fall through to resources. */
    fun lookup(key: String): String?

    companion object { val None = SidrStringOverlay { null } }
}

val LocalSidrStringOverlay = staticCompositionLocalOf { SidrStringOverlay.None }

@Composable
@ReadOnlyComposable
fun sidrString(@StringRes id: Int): String {
    val overlay = LocalSidrStringOverlay.current
    if (overlay === SidrStringOverlay.None) return stringResource(id)   // shipped path
    val key = LocalContext.current.resources.getResourceEntryName(id)
    return overlay.lookup(key) ?: stringResource(id)
}
```

Design constraints, each with a reason:

- **Overlay keys are resource entry names, not ids.** Resource ids are not stable across builds; an
  overlay keyed by id would silently mistranslate after any resource change.
- **The `None` identity check short-circuits before any name lookup**, so the shipped app pays nothing
  for the seam - no `getResourceEntryName` call, no allocation, no behaviour difference from calling
  `stringResource` directly.
- **Ids are always static `R.string.*` references. Never `Resources.getIdentifier`** - `isShrinkResources
  = true` strips resources that have no static reference, so a name-based lookup would work in debug and
  fail in release.
- Overloads: `sidrString(id, vararg formatArgs)`, `sidrPluralString(id, count, vararg formatArgs)`, and
  one non-composable `Context.sidrString(id)` for the few call sites outside composition.
- `staticCompositionLocalOf` is deliberate: overlay changes are rare and must recompose the whole subtree.

`translate_ui` later ships an overlay implementation and nothing else. No call site changes again.

## 7. Locked subset

`strings_locked.xml` sits beside `strings.xml` in each module that has locked content, in two classes.

### 7.1 Locked, never translated (`translatable="false"`)

AAPT itself rejects a translation of these, so the barrier is the toolchain, not a convention. This is
the terminal state-vocabulary and the command echo:

- provenance and status: `LOCAL ONLY`, `CLOUD`, `KEY IN KEYSTORE`, `LOCAL CALC`, `NO PROVIDER CONFIGURED`,
  `ACTIVE`, `LEARNING`, `NEEDS CONFIRMATION`, `INACTIVE`, `EXPIRED`, `UNAVAILABLE`, `DELETED`, `FAILED`;
- memory type labels: `EXPLICIT ALIAS`, `LEARNED PREFERENCE`, `USER PROVIDED FACT`, `TEMPORARY CONTEXT`,
  `SYSTEM POLICY`, `AUTOMATION STATE`;
- gate-type chips: `CONFIRM`, `PERMISSION`, `SENSITIVE`, `EXTERNAL`, `DESTRUCTIVE`;
- command echo (§4) and the `SIDR OS` / `SIDR` wordmarks, host names, and model ids - the last three are
  data, not copy;
- the launcher's own name: the manifest's `android:label="Sidr Launcher"` moves to `@string/app_name`
  with `translatable="false"`. It is a brand, and it is also what the system launcher-chooser shows.

### 7.2 Locked, translated only with owner sign-off

- **Religious terminology**: prayer names, calculation methods, Asr madhab labels. Turkish here means
  Diyanet terminology, which the owner reviews specifically.
- **Consent and safety sentences**: the cloud-disclosure copy, "Nothing was saved", destructive-gate
  consequence sentences, permission rationale copy.

These are listed in a hand-synced `LOCKED_KEYS` inventory (the `ALL_KEY_NAMES` / `TABLE_NAMES`
precedent), so adding or changing one forces an inventory edit and therefore lands in owner review.

### 7.3 The chip/button split

Chips stay English (§7.1); **buttons are translated** - `Confirm`, `Cancel`, `Forget`, `Not now`, `Run`.
A consent button the user cannot read is not consent. Owner-confirmed 2026-08-11.

## 8. Text leaving the ViewModels

Per owner decision: typed state + pure feature-local mapper, the DS-10 `AssistantPresentation` shape.

| Site | Today | After |
|---|---|---|
| `LauncherViewModel` feedback | `CommandFeedback.Message("Unknown command. Try: ...")` | `CommandFeedback.UnknownCommand` etc. |
| `LauncherViewModel` voice errors (7) | `Message("Didn't catch that - try again.")` | `CommandFeedback.VoiceError(SpeechRecognitionError)` |
| `LauncherViewModel` suggestions (3) | `Suggestion("Did you mean to open \"x\"?")` | `CommandFeedback.Suggestion` typed by intent + arg |
| `AssistantViewModel.toUiError` (6) | `UiError.Message("Rate limited. ...")` | typed assistant error, mapped in `AssistantPresentation` |
| `AssistantViewModel` saveError (3) | `saveError = "Failed to save API key"` | typed `ProviderSaveError` |
| `AppDrawerViewModel` (2) | `UiError.Message("Permission denied: $p")` | typed variant + arg |
| `LearnedChoiceMemoryUiModel` (6) | prose strings | typed evidence value, resolved in the composable |

ViewModels stay Android-free; mappers stay unit-testable without resources; `CommandFeedback.Message`
survives only for the exempt dev console.

**Honest parity statement.** Every DS block claimed byte-for-byte ViewModel-suite parity. **I18N-1 cannot
claim it** for `LauncherViewModelTest`, `AssistantViewModelTest`, `AppDrawerViewModelTest`, and the
`feature/settings` mapper tests: assertions that compare exact English sentences become typed
assertions. No ViewModel *logic* changes, no test is deleted, and every other suite - `:domain`,
`:data:*`, `:app`, and the untouched parts of the feature suites - stays byte-for-byte. The plan states
per-suite counts before and after.

## 9. Locale selection

- `androidx.appcompat` added; `LauncherActivity` becomes `AppCompatActivity`; `Theme.SidrLauncher`
  re-parents to `Theme.AppCompat.NoActionBar`, keeping `android:windowBackground=@color/sidr_ground` so
  the no-white-flash property is preserved by construction.
- `res/xml/locales_config.xml` listing `en`, `ru`, `tr` + `android:localeConfig` on `<application>`:
  this is what puts Sidr in the system per-app language picker on API 33+.
- `AppCompatDelegate.setApplicationLocales` is the **only** writer. On API 33+ it delegates to the
  framework; on API 28-32 appcompat's `autoStoreLocales` backports persistence.
- The Settings switcher calls that same API and reads back `AppCompatDelegate.getApplicationLocales()`.
  **No `user_language` key in DataStore** - appcompat is the single store, so there is no second source
  of truth and `PrivacyInventoryGuardTest` / `ALL_KEY_NAMES` are untouched.
- Sidr is a launcher: it is not the default launcher on the test device, and locale changes recreate the
  activity. The device smoke covers relaunch and persistence explicitly.

Two properties this puts on the device checklist that would not otherwise be there: **no white flash at
cold start**, and **cold start still in the ~766 ms neighbourhood** - an appcompat delegate on the
startup path is the classic regression.

## 10. Three barriers

### 10.1 Barrier 1 - a new hardcoded literal fails the build

One guard test in `:app` walking the repository (`File("..")`), not seven per-module copies, so a UI
module added later is covered automatically. It runs inside `:app:testDebugUnitTest`, already in the gate.

Heuristic, in the idiom of the existing guards (line-based walk of `src/main/**/*.kt`): a string literal
is an offence when it contains at least one letter **and** sits on a line with a text sink - `text =`,
`label =`, `title =`, `contentDescription =`, `placeholder =`, `description =`, `consequence =`,
`evidence =`, `confirmLabel =`, `secondaryLabel =`, `SidrText(`, `Text(`, `SidrSurfaceAction(`.

Skipped: bodies of `@Preview`-annotated functions (tracked by brace depth), the §3.2 exemption list with
its reasons, and letter-free literals (§5.1 punctuation frames).

### 10.2 Barrier 1b - the seam cannot be bypassed

Exact, not heuristic: no file outside `core/ui/i18n` may import
`androidx.compose.ui.res.stringResource` (or `pluralStringResource`). This is what actually keeps §6
true; without it the wrapper is a convention and the first hurried day routes around it.

### 10.3 Barrier 2 - locale completeness

Walks every module's `res/values*/strings.xml`:

- a key present in `values` and missing from `values-ru` or `values-tr` **fails**;
- a long-tail locale (none exist yet) missing a key **warns** only - a barrier people cannot satisfy is a
  barrier people delete;
- a `translatable="false"` key appearing in any translated folder **fails**;
- module prefix and global key uniqueness (§5) **fail** on violation;
- **placeholder-set equality** across locales **fails**: a translation that drops `%1$s` throws
  `IllegalFormatException` at runtime, and no screenshot can catch it;
- **quantity completeness**: `ru` needs `one/few/many/other`, `tr` needs `one/other`.

The last two are additions beyond the owner brief; both catch crashes rather than cosmetics.

### 10.4 Barrier 3 - the pseudolocale golden

Real `en-XA` first: `pseudoLocalesEnabled` on `:core:ui`'s debug build type +
`@Config(qualifiers = "b+en+XA")`. This path is unverified in Robolectric, so task 1 of the plan is a
spike. If it does not resolve, the fallback is a pseudolocale installed through the §6 overlay seam -
which also proves the seam works - and that substitution is recorded as a documented deviation, not a
silent swap.

**Where the variant is added, and why not everywhere.** Gallery sample text stays a Kotlin literal
(§3.3), so it cannot expand. A pseudolocale capture is only meaningful for galleries that render
resource-backed copy: `controls` (button/gate/action defaults) and `assistant` (composer placeholder,
`Replying…`). For `memory` and `prayer_summary`, whose visible text is caller-supplied sample data, the
capture would be a byte-identical copy of `*_dark`; those are skipped with that reason written down.
Long-string risk on feature screens is covered by the `ru`/`tr` device smoke, not by this golden.
Owner-confirmed 2026-08-11.

## 11. Pixel neutrality - the block's acceptance criterion

**`:core:ui:verifyRoborazziDebug` must pass with zero golden rewrites.** Robolectric resolves resources,
so identical English text must produce a byte-identical raster. A moved golden means the refactor changed
something; it is investigated, not re-recorded. The only permitted addition is the new `pseudolocale`
capture of §10.4.

The known source of accidental drift is XML escaping: `Didn't` -> `Didn\'t`, `"open bank"` -> `\"`,
a literal `%` -> `%%`, plus `…`, `·`, and non-breaking spaces. Every one of these changes the raster if
done wrong, which is exactly what the goldens are for.

## 12. Translation policy

- Drafts for `en` / `ru` / `tr` are produced in-block.
- **Owner review covers only**: the §7.2 locked subset, and Turkish Diyanet terminology (prayer names,
  calculation methods). Everything else ships on the draft.
- Source of truth for `en` is the current English literal, verbatim where it survives; any copy change is
  called out separately rather than smuggled in with the extraction.

## 13. Doctrine changes

1. `CLAUDE.md` **Hard rules** gains: *"User-facing text never originates in `domain` - and not in a
   ViewModel either. Domain and ViewModels emit typed results; the feature layer chooses the string."*
2. The **Full Verification Gate** block template gains: *"Strings and all main-locale translations ship
   in the same commit as the feature."*
3. `Contract -> Owner module` gains: `sidrString` / `SidrStringOverlay` -> `core/ui`;
   `locales_config.xml` + language switcher + locale guards -> `app`.

## 14. Invariants preserved (stop and re-scope if any breaks)

- No behaviour change: no ViewModel, domain, or data **logic** edit; no navigation or
  command-pipeline edit. The typed message contract of §3.5 changes payload types, not control flow.
- `domain` stays pure Kotlin (stdlib + coroutines).
- `core/ui` imports no `domain`/`data`/`feature` - `ControlsDependencyGuardTest` and
  `PrimitiveDependencyGuardTest` stay green unchanged.
- No new `feature -> feature` dependency.
- Router-off parity, offline core, and the outbound allow-list are untouched; this block sends nothing
  outbound and adds no persisted preference key.
- Room stays at v3 with golden `3.json`.

## 15. Gate

```
env -u JAVA_HOME JAVA_HOME=/home/Suleiman/Загрузки/android-studio/jbr ./gradlew --no-daemon \
  -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :domain:test \
  testDebugUnitTest assembleDebug
```

Never piped through `tail`/`head` - that masked a red gate as exit 0 on 2026-07-13. All `UP-TO-DATE`
means re-running the test tasks with `--rerun-tasks`.

## 16. Device smoke (SM-A325F, RF8R705H38F)

- switch `en -> ru -> tr` in-app **and** through system Settings (per-app language), both paths;
- no English string left on migrated screens (the five PREVIEW tabs excepted, §3.2, stated in the report);
- nothing clipped or truncated at default font scale;
- relaunch preserves the chosen language; force-stop preserves it;
- no white flash at cold start; cold-start time recorded and compared to ~766 ms;
- Turkish `İ` renders in `SidrTextRole.SYSTEM` labels; Cyrillic renders (already device-verified in DS-11);
- the Home date line (`HomeDateLine`, Hijri · Gregorian) follows the **app** language when the app and
  system languages differ - it formats through `Locale.getDefault()` and is the one surface that was
  already localized before this block.

Device constraints that bound the pass: the owner's laptop is tethered through this phone, so mobile
data, Wi-Fi, and airplane mode are never touched - offline paths are not agent-verified. Any change to
system-wide state (device language, font size, default launcher, app data) needs the owner's permission
first; the system-locale half of the smoke is therefore explicitly owner-gated. Sidr is not the default
launcher: start with `adb shell am start -W -n com.sidr.launcher/.LauncherActivity`.

## 17. Risks

1. **appcompat on the startup path** - theme re-parent + delegate. Mitigated by keeping the window
   background item and verifying flash/cold-start on device.
2. **The `en-XA` spike may fail** in Robolectric - fallback is the overlay pseudolocale, recorded as a
   deviation (§10.4).
3. **Russian runs ~15% longer than English** - tab-bar label clipping at fontScale 2.0 will surface. It
   is a known open follow-up and deliberately a later block (§3.6); this block records the finding
   instead of fixing it.
4. **Barrier 1's heuristic yields false negatives** - compensated by the exact barrier 1b.
5. **XML escaping drift** moving pixels (§11) - caught by the unmodified goldens.

## 18. Definition of done

Spec and plan approved; all in-scope strings extracted behind `sidrString`; `en`/`ru`/`tr` shipped;
locked subset split and reviewed; typed ViewModel text with mappers; language switcher on one API; three
barriers green; gate green with an honest exit code; **zero golden rewrites** plus the new pseudolocale
capture; device smoke executed or explicitly reported as not covered with the reason; ADR in
`ai-context/decisions.md`; plan `STATUS` updated; `CLAUDE.md` and `ai-context/current-status.md` synced.
