# CLAUDE.md — Sidr Launcher

**I18N-2 residual localization + barrier 4 — CLOSED 2026-08-19, gate green.** Closes two of I18N-1's
own device-smoke residue items and installs a fourth regression barrier the first three structurally
could not have caught. **Fix 1:** `PrayerSummaryMapper.kt` built each Home prayer cell with
`name = name.name` — the raw `PrayerName` enum literal — which rode verbatim into
`SidrPrayerSummary`'s per-cell TalkBack `contentDescription`; invisible in English (the locked resource
happens to equal the enum name, which is exactly why every I18N-1 barrier missed it), audible as literal
English in `ru`/`tr`. Now a typed `HomePrayerTimeUi(name: PrayerName, …)` resolved at render time via a
new `prayerNameLabel()`, mirroring `feature/prayer`'s already-correct pattern; 5 new Class B locked keys
copied verbatim from `feature/prayer`. **Fix 2:** `AndroidPrayerLocationProvider`'s
`DEVICE_LOCATION_LABEL = "Current location"` rendered untranslated on Home, the prayer detail screen,
and (a third, previously unnoticed site) the prayer settings screen. `PrayerLocation.label` doubles as
display text AND `GetPrayerContextUseCase.matchesSetup`'s cache-validity key, so the fix could not just
swap the source string — `PrayerScheduleProvenance` gained a required `locationSource:
PrayerLocationSource` discriminator instead; the **stored** identity stays byte-identical (no cache
invalidation, `matchesSetup` untouched), only the *displayed* text now resolves through `sidrString`.
The DTO layer (`data/prayer`'s `ProvenanceDto`) defaults the new field to `CITY` so schedules cached
before this field existed still decode (proven by a round-trip test seeding hand-crafted pre-migration
JSON) rather than treating a whole cache generation as corrupt. **Barrier 4:**
`DomainIdentifierLeakGuardTest` (`app/src/test/…/i18n/`) scans the same spec §3.1 module roots for a
display-sink assignment whose right-hand side is a **bare** `.name`/`.toString()`/`.key`/`<Id>.value`
property chain, anchored immediately after the sink's `=` (calibrated against the real codebase to
avoid false-positiving on `count.toString()` number formatting or `it.name == name` comparisons); two
exemptions recorded (`SidrActionSafety.kt`'s spec §7.1 locked risk/status vocabulary, same precedent as
`RISK_CONFIRM_LABEL`; `SettingsScreen.kt`'s `count.toString()`, a plain `Int`). Both fixes and the new
barrier were proven regression-catching by reverting the code and confirming red, then restoring green.
**Gate (JDK-17):** `:data:prayer` 37→38, `:feature:launcher` 156→161, `:app` 15→18 (`:domain`/
`:core:android`/`:feature:prayer` untouched by count — existing call sites updated in place,
compiler-enforced via the new required constructor param); full module set + root `testDebugUnitTest` +
`assembleDebug` SUCCESSFUL; goldens untouched (no `core/ui` edits). **Deliberately out of scope:**
`CalendarSuggestionProvider`/`LocationSuggestionProvider` labels (near-zero real reach) and
`RISK_CONFIRM_LABEL` (already-recorded owner-approved exemption) stay routed to a future I18N pass.
**Known gap, not fixed here:** `checkOwnerReviewedLocaleStrings` checks the `OWNER-REVIEWED` marker's
*presence*, not *coverage* — a signed file can silently gain an unreviewed key later. ADR
"2026-08-19 — I18N-2 residual localization + barrier 4" in decisions.md.

**I18N-1 Multilingual UI — CLOSED 2026-08-16, gate green, device-verified.** Ships `en`/`ru`/`tr` across
every migrated production screen (Home, App Drawer, Settings + Memory/Learned-Choices/Aliases/AI-provider
sub-screens, Assistant + its provider screen, Prayer setup/detail, Permission education) behind one
`core/ui` seam — `sidrString(R.string.…)` is the **only** call site for `stringResource`
(`StringSeamGuardTest`), keyed by resource entry name so a future runtime overlay (`translate_ui`) needs no
call-site changes. Two owner-decided exemptions stay English on purpose: the five `PREVIEW` mock-up tabs
(§3.2, replaced wholesale by the A-stage) and the hidden dev console. 11 strings born below the UI
(domain/data/ViewModel) got a new typed contract — `CommandMessage`/`CommandFailure` in `domain`, resolved
to copy by a feature-layer `LauncherPresentation` mapper (the DS-10 precedent reused) — a scope expansion
found while planning, not assumed at brief time. Three regression barriers ship: `StringSeamGuardTest`
(no bypassing `sidrString`), `LocaleCompletenessGuardTest` (a block is not gate-green until `en`/`ru`/`tr`
are complete), `HardcodedUiTextGuardTest`; a fourth gate, `checkOwnerReviewedLocaleStrings`, makes
`:app:assembleRelease`/`:app:assemble`/`:app:bundleRelease`/root `./gradlew build` **RED BY DESIGN** until
the owner adds an `OWNER-REVIEWED` marker to all 10 locale `strings_locked.xml` files — this is a
deliberate build-behaviour change, not a regression, and every debug graph stays clean. Per-app language
switch is `AppCompatDelegate`-backed (`LauncherActivity` → `AppCompatActivity`, theme re-parented to
`Theme.AppCompat.NoActionBar`, `windowBackground` byte-identical, so no white-flash regression); no new
DataStore key. **Honest parity note:** unlike every prior DS block, I18N-1 cannot claim byte-for-byte
ViewModel-suite parity — typing domain/data messages touched `:feature:launcher`/`:feature:settings`
mapper tests in place (see the ADR for the itemized before/after). Zero `<plurals>` were needed by the
extraction; barrier 2's quantity check ships dormant by design rather than invented-plural theater. The
pseudolocale barrier resolved to real platform `en-XA` (no overlay fallback needed); golden budget stayed
at exactly 2 new captures (`assistant_pseudolocale`, `prayer_summary_pseudolocale` — a spec §10.4
correction from `controls`/`memory`, since `controls` renders byte-identical to English and would not have
loaded the barrier at all). **Gate (JDK-17, exit 0):** `:domain` 333/0 (was 332), `:core:ui` **126/0**
(was 119) + `verifyRoborazziDebug`, `:feature:launcher` **156/0** (was 130), `:feature:settings` 33/0,
`:feature:assistant` **38/0** (was 35), `:feature:prayer` 12/0, `:feature:permission_education` 15/0
(byte-parity), `:data:repository` 167/0, `:app` **13/0** (was 8), root `testDebugUnitTest` +
`assembleDebug` SUCCESSFUL; goldens **untouched** — `git status --porcelain` on the screenshots dir is
empty (36 PNGs, 0 new, 0 modified). **Device (SM-A325F, system locale `ru-RU` throughout, agent-driven
adb):** in-app `en↔ru↔tr` switch instant and correct on every migrated screen, selection dot updates
without leaving the screen, Turkish dotted-İ renders correctly in multiple uppercase SYSTEM headers,
force-stop→relaunch preserves the chosen language, and — the specific regression this block's own brief
flagged as the one to watch — the Home date line read in **Turkish** while the device stayed on `ru-RU`,
confirming it now follows the app locale rather than `Locale.getDefault()`'s system default. Status/nav
bar tint showed no anomaly on every screen captured (no pre-block reference screenshot exists on this
device state, so this is "no anomaly observed," not a diff-proven "unchanged"). **Not device-covered:**
the system per-app-language picker
(owner-gated), any offline path (tethering), live TalkBack, fontScale 2.0 clipping, a release-build
cold-start comparison (blocked by the new release gate itself pending owner sign-off). **Known gaps
routed to I18N-2:** `CalendarSuggestionProvider`/`LocationSuggestionProvider` labels and
`RISK_CONFIRM_LABEL` stay English (data-layer/locked-vocabulary, out of §3.1 scope); voice recognition
still follows the device language, not the app language (spec §3.6, deliberate). *(The other two items
originally listed here — `AndroidPrayerLocationProvider`'s hardcoded `"Current location"` label and
Home's per-cell prayer `contentDescription` reading the untranslated enum name — were closed by I18N-2,
2026-08-19; see the entry above.)* ADR "2026-08-16 — I18N-1 Multilingual UI complete" in
decisions.md.

**DS-11 pre-gate UI refinement — CODE-COMPLETE 2026-08-10, gate green, DEVICE-VERIFIED IN PART.**
Owner-directed polish immediately before the DS v1.1 release gate; five proposals triaged into
**Block A** (ship now), **Block B** (ship now), and two deferrals. **Block A (presentation-only + one
persisted flag):** nav-bar flag **inverted and renamed** `alwaysShowNavBar` → **`autoHideNavBar`
(default `false`)** with a **new** key `user_auto_hide_nav_bar` — pinned bottom chrome is now the
resting state and auto-hide is the opt-in (timer/`SidrChromeHandle`/Settings toggle all retained, the
toggle relabelled "Auto-hide navigation bar"; amends the 2026-07-12 auto-hide spec). **A plain default
flip was tried first and did nothing** — caught on device: `PreferencesMapper` writes the *whole*
object on every update, so any install where a setting was ever changed already had
`user_always_show_nav_bar` persisted, and a stored value beats a changed default; a new key is the
migration-free fix (old boolean orphaned, inert, dropped from `ALL_KEY_NAMES`);
`SidrTabBar` **replaced Material `NavigationBar`/`NavigationBarItem` with a
plain themed row** (that component requires a non-null `icon` and sizes its indicator around the glyph,
so icon-free is not expressible through it) — **lowercase icon-free labels** `home/apps/tasks/agents/
activity` with DS **press-invert** selection, `navigationBarsPadding` re-applied by hand; preview marker
`◦`→**`•`** (verified via TTF cmap: IBM Plex Sans / Nunito / platform sans lack U+25E6, so the marker
would have come from font fallback in a foreign face); `SidrAppFooter` lost the `Local-first ·
on-device` line (**7-tap dev-mode arm on `SIDR OS` retained**) and the wrench `Icons.Filled.Build`
became a bundled `ic_terminal_24.xml` (no `material-icons-extended` dependency — follows the existing
`ic_mic_24` precedent); route chips size to their text (`weight(1f)` dropped) and lost the resting
border (border became a `bordered` param defaulting **true**, because `SidrFilterChip` in Settings/App
Drawer genuinely needs a resting boundary); **APP chip un-highlighted** (was permanently `selected =
true` for a lane that was never a state) but **kept** per owner. **Block B (typography):** mono's
territory narrowed to **`command` + `provenance` only** — Master Plan §6.2 had assigned JetBrains Mono
the whole interface shell, which is what read "technological" against §6.1's stated `calm / mature /
restrained`; `SidrSans` went `FontFamily.SansSerif` → **bundled IBM Plex Sans** (OFL, 4 weights,
~800 KB, Latin+Cyrillic+Greek+Turkish from one file), `system`/`labelLarge`/`labelSmall` moved mono→sans,
tracking relaxed 1.4→0.6 and 1.6→0.8 sp; **new `SidrTextRole.CAPTION`** (sans 13/19, `dim`) because all
five `SidrRow` variants rendered their `description` as `PROVENANCE` — every settings/permission
explanation was still mono, so without this mono had not actually narrowed to its stated territory;
**`sacred` deliberately untouched** (`FontFamily.Serif`) — §6.2 defers the Arabic-capable face to DS-6A,
and **no** sans candidate carries Arabic (cmap-verified);
`TypographyRoleTest` grown 2 assertions → 4 tests pinning the whole boundary. **Gate (JDK-17, exit 0):**
`:domain` 332/0, `:core:ui` **119/0** (was 117) + `verifyRoborazziDebug`, `:feature:launcher` **130/0**,
`:feature:settings` **33/0**, `:feature:assistant` **35/0**, `:data:repository` 167/0, `:app` 8/0, root
`testDebugUnitTest` + `assembleDebug` SUCCESSFUL — **every VM suite byte-for-byte**; no VM/domain/data/
nav-graph/schema change. Goldens: Block A moved exactly 5 (border removal only, no layout shift), Block B
re-recorded all 34 (typeface-only; fontScale 2.0 got *better* — sans fits on one line where mono wrapped).
**Device (SM-A325F, agent-driven adb):** pinned chrome past 10 s, lowercase text tabs with press-invert,
`•` markers rendering, real terminal glyph, no `Local-first` line, `SIDR OS` intact, borderless
un-highlighted route chips, Plex Sans incl. Cyrillic, tab bar flush above system nav, relabelled Settings
toggle off. **Not device-confirmed** (phone disconnected mid-pass — it is the owner's tethering link):
`CAPTION` in Settings, 7-tap dev-arm, Terminal navigation, auto-hide toggled back on, TalkBack,
fontScale 2.0 / RTL / light on the live tab bar.
**Deferred, direction fixed:** multilingual UI = its own post-gate block (**zero i18n infrastructure
exists** — no `strings.xml`, `stringResource` = 0 occurrences, ~250–350 UI strings across 8 modules,
plus three real locale bugs found: `Locale`-less `.uppercase()`/`.lowercase()` (Turkish dotted-İ) in
`SidrSystemLabel`/`SidrProvenanceLine`/`SidrActionSafety`/`AppDrawerScreen`/`InstalledAppsRepositoryImpl`,
`.lowercase()` instead of `Collator` for sorting, and forced uppercase + letter-spacing on
`SidrTextRole.SYSTEM` which breaks Arabic letter-joining — forbidden by Doctrine §15.2); "show all active
windows" gesture = post-gate AccessibilityService slice (the real system Overview is unreachable for a
sideloaded launcher — `getRecentTasks()` returns only own tasks since API 21 — **and the proposed
bottom-right-corner swipe collides with both system gesture-nav edges**, the very reason the auto-hide
spec chose a tap; the gesture must be re-picked). **Known gaps:** device acceptance pending, and
`SidrTabBar`/`SidrAppFooter` have **no automated visual coverage** (Roborazzi is wired only in
`:core:ui`, these live in `:app`). ADR "2026-08-10 — DS-11 pre-gate UI refinement" in decisions.md.

**Design track (DS) — DS-1…DS-4 + Vision MVP (Preview) DONE (2026-07-11); DS-5 CODE-CLOSED
(2026-07-13, device-pending); DS-6B Prayer Correctness COMPLETE — device-accepted by owner (2026-08-08);
DS-7 Memory Surfaces + Stage-2 S2-2 Explicit Aliases CLOSED — device-accepted (2026-08-10);
DS-10 Assistant Migration CLOSED — device-accepted (2026-08-10). The DS v1.1 release gate is now open.**

**DS-10 Assistant Migration CLOSED — device-accepted on SM-A325F 2026-08-10; the design track's last
production surface.** **Baseline correction:** the spec's "raw Material Assistant" premise was stale —
the Vision MVP pass had already moved the screen onto `SidrScaffold`/`SidrTopBar`/`SidrText`/`SidrSurface`
and split the key-bearing form onto `AssistantProviderScreen`. DS-10's real delta was the **DS-5 layer**,
the a11y contract, the layout split, and the missing coverage — not a re-skin. New presentation-only
`core/ui/component/SidrAssistant.kt` (`SidrAssistantComposer` — IME Send and the button share **one**
`canSend = notBlank && !sending` guard, send's `contentDescription` names *why* it is unavailable;
`SidrStreamingIndicator` — one `liveRegion` so TalkBack says "Replying…" once per state change, not once
per token) + new pure `feature/assistant/AssistantPresentation.kt` (DS-7 mapper precedent: DS-5
what/why/next copy, **at most one** action — `Retry` / `Fix provider settings` / none for
`InvalidRequest` — the cloud-disclosure text, and host+model+key-presence provenance; `providerHost`
moved here unchanged). `AssistantScreen` recomposed into shell/content/message/status-line/composer/
provider panels; idle-with-no-reply now *is* the cloud disclosure; provenance pinned above the composer;
errors → `SidrErrorSurface`; refusal stays calm text; API-key field gained Compose `password()` semantics.
**Parity: `AssistantUiState`/domain/data untouched, `AssistantViewModel` touched only by the
write-order fix below** — streaming, latest-wins
cancellation, retry, BYOK/Keystore, prefill-only `initialPrompt`, no prompt/reply `SavedStateHandle`
(`AssistantViewModelTest` 21/0 byte-for-byte); no chat history, memory injection, tool execution, or key
display added. Gate (JDK-17, force-rerun, exit 0): `:domain` 332/0, `:core:ui` **117/0** (was 108) +
`verifyRoborazziDebug`, `:feature:assistant` **35/0** (was 24), `:feature:launcher` 130/0,
`:feature:settings` 33/0, all data modules green, root `testDebugUnitTest` + `assembleDebug` SUCCESSFUL;
4 additive `assistant_*` goldens, no pre-existing golden changed. **Device (owner typed the provider+key
on-device; the agent never typed or read a key):** no-provider disclosure → provider form (key masked,
"Key set" copy, Save gated) → `CLOUD · OPENROUTER.AI · OPENAI/GPT-4O-MINI · KEY IN KEYSTORE`; ASK-route
prompt **prefilled, never auto-sent** (3×); **real BYOK streaming** (quiet `Replying…`, composer cleared,
Send disabled mid-stream, sans-prose reply); a real HTTP-404 hit the **unactionable** `SidrErrorSurface`
(FAILED + WHAT/WHY/NEXT + no button) exactly per the mapper; force-stop → nothing persisted; 0 key leaks
in logcat. **Fixed mid-pass:** the empty provider form claimed `CLOUD · (UNKNOWN HOST)` → now
`LOCAL ONLY · NO PROVIDER CONFIGURED`. **Also fixed (pre-existing VM race, surfaced on device):** right after a key
save the *chat* VM instance showed `NO KEY SET` — `keySet` is recomputed only on `activeConfig()` emits
and `saveProvider` wrote the config *before* the Keystore put. `saveProvider` now writes **key first,
config last** (the config write is the observable event); guarded by a new ordering test proven to fail
on the old order. No other VM change — `AssistantViewModelTest` 21 → 22, the original 21 unchanged. **Not device-covered:**
retryable-network error + Retry (cutting the phone's data killed the owner's tethered laptop — stopped;
mapping covered by `AssistantPresentationTest`), credential-CTA 401, refusal, fontScale-2.0/RTL/light on
the live screen (goldens only), live TalkBack. **Deviation:** Task 6 ships as a `core/ui` component
gallery of the Assistant's states, not whole-screen goldens — Roborazzi is wired only in `:core:ui`.
ADR "2026-08-10 — DS-10 Assistant Migration complete (device-accepted)" in
decisions.md. **Next: the DS v1.1 release gate; next architectural slice: A1 Tool & Capability.**

**DS-7 Memory Surfaces + S2-2 Explicit Aliases CLOSED — device-accepted on SM-A325F 2026-08-10.** Both
had been implemented on `launcher--7` since 2026-07-13 (`8e3f317` = `core/ui` `SidrMemoryItem`/
`SidrMemoryDisclosure`/`SidrForgetGate` + 4 `memory_*` goldens; `b2affdd` = Learned Choices migrated
behind a feature-local `LearnedChoiceMemoryUiModel` mapper + new `AliasesScreen`/`AliasesViewModel` +
`Routes.Aliases` + Settings **MEMORY** section) and shipped in every build since — only the docs lagged.
S2-2's stranded `launcher-4` last mile was re-applied **by hand** (`7c20b63` decorator into
`LauncherViewModel`, `0198abc` `AliasPrivacyScopeGuardTest` + `RoomColumnNames` entry); `cef4111` (docs)
intentionally dropped. Invariants held: aliases fire **only** on `CommandOutcome.Unknown` (rule path and
`open <app>` parity untouched), outbound allow-list widened by **zero**, `core/ui` imports no
domain/data, Room stays at v3 + `Migration2To3` + golden `3.json`. Gate (JDK-17, force-rerun): `:domain`
332/0, `:core:ui` 108/0 + `verifyRoborazziDebug`, `:feature:settings` 33/0, `:feature:launcher` 130/0,
root `testDebugUnitTest` + `assembleDebug` green. Device: alias add → row (`EXPLICIT ALIAS · ACTIVE ·
LOCAL ONLY`) → phrase launches the declared app directly; `open opera` still wins; unknown phrase →
unchanged fallback; `SidrForgetGate` on both surfaces (Cancel never deletes, Forget deletes once);
`open python` ambiguity → `LEARNING (1/3)` → Forget → asks again; fontScale 2.0 wraps clean.
**Not device-covered:** unavailable-target prune (needs disabling one of the owner's apps) + live
TalkBack (accessibility tree read instead). **Deviation:** `SidrMemoryDisclosure` ships preview-only (no
honest "preference formed" event; plan permits). **Follow-ups:** Aliases list renders after the whole app
picker; `AliasesViewModel` discards save/delete `OperationResult`s; tab-bar labels clip at fontScale 2.0.
ADR "2026-08-10 — DS-7 Memory Surfaces + S2-2 Explicit Aliases complete (device-accepted)" in
decisions.md. **(Superseded by DS-10 above: DS-10 is now closed too, so the next design milestone is the
DS v1.1 release gate; next architectural slice: A1 Tool & Capability.)**

**DS-6B Prayer Correctness COMPLETE — device-accepted by the owner on SM-A325F 2026-08-08; the Home strip
was reduced to times-only during acceptance (calm status chip + provenance line hidden, names in TalkBack
contentDescription, degraded-state warnings kept — provenance invariant intact). ADR "2026-08-08 — DS-6B
Prayer Correctness (COMPLETE — device-accepted by owner)" in decisions.md.** New modules `:data:prayer` (offline
`AdhanPrayerCalculator` over `com.batoulapps.adhan:adhan2:0.0.5`, MIT-in-POM, Kotlin port used instead of
the originally-named Java "adhan-java" because that port lacks a `TURKEY`/Diyanet method; zero network)
and `:feature:prayer` (setup/detail screens, pushed route, no new tab); `domain/prayer/` stays pure.
Method + Asr madhab are explicit first-run choices, no default, no locale/SIM/location guessing (11
supported methods; `OTHER`/`TEHRAN` excluded — genuinely absent from adhan2:0.0.5). Location = bundled
offline GeoNames city index (19,481 cities, 286.8 KB gzipped, CC-BY 4.0 attributed) + optional one-shot
device location that rounds to 2dp **before** crossing the port boundary — no continuous tracking, no
coordinate logging, precise coords never persisted/leave the device. New `PermissionFeature.PRAYER_LOCATION`
(documented deviation: honest prayer-specific copy instead of reusing `LOCATION_SUGGESTIONS`'s
suggestion-specific copy; no new manifest permission). Privacy proven by a 10-guard
`PrayerLocationPrivacyGuardTest` (`OutboundContextPolicy.ALLOWED` unchanged at 4 values; no prayer field
in `AiRequest`; planted coordinate sentinel never reaches `PromptContextBuilder.build`). `LauncherViewModel`
gained exactly one new dependency (`GetPrayerContextUseCase`) behind a lazy `WhileSubscribed` `StateFlow`;
Home strip renders below the Shahada, opt-in, **only** on `PrayerContext.Available` — never fabricated
times; `LauncherViewModelTest` parity byte-for-byte. **Owner refinement (2026-08-08, on-device):** shipped
strip is **times-only** (status chip hidden for calm states, kept as a warning label for
stale/tz-conflict/failed states; prayer names moved to per-cell `contentDescription`; provenance dropped
from the strip's visible text but still required by its invariant and still on the detail screen). Full
Verification Gate green (JDK-17): all prayer modules + `core:ui:verifyRoborazziDebug` + root
`testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL. **Device status PARTIAL** — SM-A325F no-data
invariant passed (fresh install → calm Home, no strip/prompt/fabricated times) and a real Diyanet/Turkey
setup rendered a correct-looking strip, but full interactive + religious-correctness acceptance (times
cross-checked against published authority tables, airplane-mode cache, stale path, device-location grant,
tz-conflict, font-scale 2.0, TalkBack, router-off parity) is **PENDING the owner** (DS-5 precedent).
**Known limitation:** Istanbul (Diyanet) and Makkah (Umm al-Qura) golden times are authority-table-anchored;
London and Kazan (MWL) are cross-implementation-verified only, since MWL has no official authority portal.
DS-6A (the quiet Shahada component) stays UI-only and is a separate block from DS-6B's data path.

**DS-5 Action & Safety CODE-CLOSED + two owner features (2026-07-12/13; retro-ADR "2026-07-13 — DS-5 +
auto-hide nav + accent reactivation closed" in decisions.md).** DS-5: `core/ui/component/SidrActionSafety.kt`
(`SidrActionProposal`/`SidrPermissionNotice`/`SidrPrivacyNotice`/`SidrResultSurface`/`SidrErrorSurface`/
`SidrOfflineState`/`SidrBlockedState` + `SidrSurfaceActions` fontScale-stacking) + hardened one-shot
`SidrActionGate`; adopted in `LauncherScreen` (SAFE→proposal, CONFIRM→gate) + `PermissionEducationScreen`;
`ErrorState`→`SidrErrorSurface`; `ConfirmActionCard` usage zero (not yet `@Deprecated`); no VM change ⇒
parity structural; deviation: plan's `ActionSafetyGallery` goldens not delivered (behavioural tests only).
Owner features: **auto-hide bottom nav** (5 s idle → `SidrChromeHandle`; pin =
`UserPreferences.alwaysShowNavBar` + Settings toggle; spec `2026-07-12-auto-hide-nav-bar-design.md`) and
**accent reactivation** — `AccentColor {GREY,GREEN,AMBER}` as full themes (`sidrColorsFor`; sacred+status
pinned; default now `"grey"`). Stabilization pass 2026-07-13: `alwaysShowNavBar` round-trip test, 4
green/amber goldens (+accent/status proof lines), stale-comment fix, brittle
`AppNavHostReentryGuardTest` literal relaxed (re-entry behaviour verified intact), **full gate green**
(JDK-17). **Device acceptance PENDING on SM-A325F** (checklist in the ADR; the DS-7/S2-2 pass of
2026-08-10 exercised `SidrForgetGate` and the auto-hide nav incidentally but did not run DS-5's own
checklist). **DS-7 Memory Surfaces closed 2026-08-10 — see the top of this file.**
A parallel, presentation-only design-system
migration to the approved **soft-classic-grey** identity, governed by `docs/design/SIDR Design System
Master Plan v1.2` (the principles rulebook was folded into it: §5.1 precedence, §5.2 verify-vocab, §20.1
calm budgets). **DS-1** = grey token layer + tri-font + softened shapes + `SidrTheme.colors`/`textStyles`
(accent kept but inert) + global CRT removed + Roborazzi screenshot harness. **DS-2** = eight additive
`core/ui/primitive/` primitives + `Strokes` token + gallery goldens (dark/light/font-scale/RTL); keystone
`SidrProvenanceLine` (semantic `source`+`details`, TalkBack); status≠accent. **DS-3** = SIDR controls
(buttons/chips-press-invert/rows/top-bar/`SidrActionGate`) in `core/ui/component/`; Settings migrated as
proof surface; routed confirmation → `SidrActionGate`. **DS-4** = Home migrated to the intent-first layout;
`SidrCommandPrompt` → `core/ui` `SidrUniversalInput`; `LauncherScreen` recomposed feature-locally
(`HomeTopRow` SIDR wordmark + Gregorian+Hijri date + Settings icon; empty `HomeAnchorSlot` seam for DS-6A;
results overtake on DS-3 chips + DS-2 text; bottom CommandBar retired → All Apps/Assistant rows +
local-first privacy line); `SidrCommandPrompt` `@Deprecated`. **All four: no production
nav/VM/persistence/domain change — command pipeline / routing / voice / offline / router-off parity fully
intact** (`LauncherViewModelTest` byte-for-byte); DS-4 **device-accepted on SM-A325F**.

**Vision MVP (Preview)** (13 tasks, `docs/superpowers/plans/2026-07-11-vision-mvp-preview.md`) extends
DS-3/DS-4's artifact look to every remaining production screen — App Drawer (grid + Groups/A-Z toggle),
Assistant, AI provider settings, Memory/Learned Choices, Permission Education — adds the 5-tab bottom bar
(`SidrTab`/`SidrTabBar`) with Home reconciliation, and delivers DS-8/DS-9/DS-10's Tasks/Agents/Activity/
Terminal surfaces as `PREVIEW`-badged, non-functional mock-ups (every callback inert, zero fabricated
data, Terminal test-proven incapable of producing output) plus an Interaction-Moments preview. Same hard
rule as DS-1..4: **no production nav/VM/persistence/domain change**; every ViewModel test suite passes
byte-for-byte. **Device-accepted on SM-A325F.** ADRs:
decisions.md "2026-07-11 — DS-1/DS-2/DS-3/DS-4 complete", "2026-07-11 — Vision MVP preview + all
screens to artifact", and "2026-07-13 — DS-5 + auto-hide nav + accent reactivation closed"; specs+plans under
`docs/superpowers/`. Stage-2 S2-2 "Explicit Aliases" is **CLOSED** (device-accepted 2026-08-10, together
with DS-7 which shipped its grey UI). The AI-launcher stage digest below is unchanged.

Session digest. Read this first. **Last synced: 2026-07-11 — Stage-2 block S2-1 "Learned Resolutions" is
CLOSED. Tasks 1–16 are done on `launcher-4`; build gate `:domain:test` + `testDebugUnitTest` +
`assembleDebug` was green; SM-A325F device acceptance passed with screenshots under
`/tmp/sidr_acceptance_*.png`. Details: ADR "2026-07-11 — S2-1 Learned Resolutions device accepted +
closed" in `ai-context/decisions.md`; plan `docs/superpowers/plans/2026-07-06-learned-resolutions.md`
(STATUS CLOSED). The prior Stage-1 sync is retained below.**

**Prior sync (2026-07-06) — AIL-6 complete → Stage-1 AI-Launcher MVP
CLOSED (blocks AIL-0…6). The RC build gate (`:app:assembleRelease`) is green under a JDK-17 toolchain and a
full SM-A325F device-acceptance pass with a real BYOK provider passed: NL routing → DF-4 confirm card
(CONFIRM) / one-tap (SAFE) → execution, nothing auto-runs (R4); router-off / offline ⇒ byte-for-byte
rule-only parity; DF-5/6/7 polish (block caret, chip press-invert, `>_` mark, green↔amber accent that
persists across force-stop). No code change was required by the device pass. Active work is now Stage 2 —
AI Framework.**
Project reframed into three
stages (AI Launcher → AI Framework → Agentic OS); the Stage-1 AI-Launcher completion track is done.
Foundation (Phases 3 → 9 + Phase UX) is built and device-accepted; routing is **AI-first behind a
default-off flag** — AIL-4 added the BYOK cloud `CommandPlanner`, AIL-5 added the confirmation + execution
surface for its proposals, and AIL-6 device-proved the whole loop. Router-off ⇒ byte-for-byte rule-only
parity. The original framing of that work: a **BYOK cloud LLM
router** that understands natural language and routes it to safe, registered actions with confirmation.
Reframed roadmap: [docs/roadmap.md](docs/roadmap.md); Stage-1 plan (closed):
[ai-context/ai-launcher-mvp-plan.md](ai-context/ai-launcher-mvp-plan.md).

**Three-stage vision (owner reframe 2026-07-05).**
1. **Stage 1 — AI Launcher (MVP, NOW).** Foundation ✅ done; the **AI-Launcher completion track
   (AIL-1…6)** adds a universal input + BYOK LLM action router + Action Registry + web/URL/Play-Store
   routing + confirmation gating. This is what ships first.
2. **Stage 2 — AI Framework.** Generalize the router/registry/context/memory into a reusable on-device AI
   framework (Action Registry, Context Engine v2, User Memory).
3. **Stage 3 — Agentic OS.** Safe user-consented automation (absorbs the old Phase 8 / accessibility) +
   the AI OS shell.

**Owner decisions (2026-07-05) for the AI-Launcher MVP.**
- **AI core = BYOK cloud LLM routing** (rule matcher stays the fast offline fallback; not blocked on ONNX
  model selection).
- **Action rights = understand + route to safe actions**; risky actions require explicit confirmation; no
  autonomy / multi-step chains (that is Stage 3).

**Current ship status.**
- **Phase UX closed** (2026-07-03) and device-accepted on SM-A325F / Android 13 (2026-07-04): minimal home
  + app drawer, Settings/Assistant entry points, settings persistence, first-run nudge, a11y semantics,
  ask-assistant prefill, and set-as-default chooser fix all passed.
- **Phase 9 closed** (2026-07-04): Y1/Y2 startup + release hardening, Y3 suggestion correctness, Y4 test
  hardening, Y5 privacy/logging/error handling, Y6 Android 13 + trim/LOW_END-path validation, and Y7
  residual cosmetic cleanup are complete.
- **Release performance record:** final release on SM-A325F warm median ~102ms; cold median 766ms after
  drop-first protocol; first home frame has no Loading spinner. `<400ms` cold remains aspirational, not a
  ship gate.
- **Assistant real streaming and BYOK Keystore are device-proven:** OpenRouter / `openai/gpt-4o-mini`
  streamed end-to-end; config persisted in `sidr_preferences`, key stayed encrypted in `sidr_secrets`,
  offline static fallback / cancel / retry / rotation passed.
- **Suggestion correctness is device-proven:** home suggestions render only launchable package targets or
  known routes; old hardcoded AOSP time-of-day chips were replaced with resolved universal anchors.
- **Y7 cosmetic findings retired:** `keySet` flips true immediately after a successful non-blank key save;
  `RateLimited` text is pinned as `retry`; relaunching `LauncherActivity` while alive resets nested nav
  back to home via `singleTop` + `onNewIntent` + `AppNavHost` home reset.

**Verification already run for Phase 9 closure.**
- `./gradlew --no-daemon :domain:test :core:android:testDebugUnitTest :data:ai-local:testDebugUnitTest :app:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:permission_education:testDebugUnitTest :feature:assistant:testDebugUnitTest :app:assembleDebug` ✅
- SM-A325F / Android 13 debug smoke for Y7 re-entry ✅: drawer -> `am start -W -n com.sidr.launcher/.LauncherActivity`
  delivered the intent to the running top-most instance and returned to home; `AndroidRuntime:E` empty.
- Earlier Y1/Y2 release smoke on SM-A325F ✅: release builds/installs/runs with R8/resource shrink +
  Baseline Profile; home -> drawer -> settings -> assistant -> set-as-default -> voice education clean.

**Still not claimed / not Phase 9 blockers.**
- Android 9 / 11 / 14 device/emulator validation and real LOW_END hardware validation remain residual
  until those targets exist.
- OQ#1/OQ#2 real NLU model + vocab + host/hash, OQ#3 embedding model/host/hash, and OQ#4 on-device STT
  matrix remain separate model/STT tracks.
- Voice recognizer `Ready` / `Partial` intermediate states are not fully evidenced (OQ#4).
- Boot warmup after a physical reboot (`RECEIVE_BOOT_COMPLETED` re-enqueue) remains unexercised.

Authoritative details: `ai-context/current-status.md`, `ai-context/ai-launcher-mvp-plan.md`, and
`ai-context/decisions.md` (latest ADR: "2026-07-05 — AIL-1 complete (Action Registry contracts)").

## What this is

AI-first Android launcher (Android 9+ / API 28+). Text / voice / contextual commands.
Multi-module Kotlin + Jetpack Compose + Clean Architecture (MVVM, Hilt, Coroutines/Flow).
The offline launcher core (home, app grid, app launch) must work fully without AI.

## Current goal (active work)

**NOW (2026-08-19): the AGENTIC TRACK is the active track. Этап 1 (strategic ADR package) is DONE —
docs only, no code.** Governing document:
[docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md)
(owner-approved 2026-08-18). Four ADRs accepted 2026-08-19, all in `ai-context/decisions.md`:

1. **Deterministic-first redefined** — *understanding belongs to the model, execution to the
   deterministic layer* (see Hard rules below). `FeatureFlags.llmRouterEnabled` is **inverted onto a new
   key**: `localOnlyMode` / `flag_local_only`, default `false` (a plain default flip would be inert —
   the DS-11 `autoHideNavBar` precedent). Prerequisite for shipping that flag: Этап 0.2 (localize
   FastPath to `ru`/`tr`).
2. **Platform re-baseline 2026** — ONNX NLU **closed**, OQ#1/#2/#3 closed; designated local-inference
   runtime is **LiteRT / LiteRT-LM** (alternative on record: ExecuTorch, with a switch condition;
   separate path: AICore); `AppFunctions` / `MCP` become first-class tool sources; performance budgets
   move from "hard targets" to a three-tier scheme in `docs/architecture.md`.
3. **Portable core boundary** — "Framework" = a portable agent core with **two consumers** (Android +
   PC), *not* a schedule stage; Stage 2 as a stage is abolished; `:domain` → KMP in Этап 2.2;
   **`ActionIds`' seven values are frozen byte-for-byte** (two different contracts: `launch_app` is a
   Room PK in `resolution_preferences`, all seven are the outbound wire contract to the LLM).
4. **Assistant ⊕ Agent** — **one conversational loop, two surfaces**: one `AgentSession` / `Planner`
   contract; a 0-step plan *is* a spoken reply (Assistant screen), an N-step plan is a task
   (Tasks/Agents).

**Next: Этап 0 (cleanup — release unblock, FastPath `ru`/`tr`, remove the ONNX stack, honest statuses,
measured budgets), then Этап 2 (toolchain + `:domain` → KMP), Этап 3 (agentic Master Plan + doctrinal
matrix), then the A0 vertical spike.** The A1 fork (parallel vocabulary vs. evolve in place) is
**deliberately not decided** — it belongs to Этап 5, on a spec rewritten after ADR 2.

**Этап 0.1 CLOSED 2026-08-19 — the release gate is cleared and `:app:assembleRelease` is green for the
first time in the project's history.** The owner reviewed the locale package and directed the marker;
all 10 `values-{ru,tr}/strings_locked.xml` files now carry `OWNER-REVIEWED 2026-08-19`, each recording
in its own header that the *review* was the owner's and the *token* was typed by the agent on their
instruction. Found and fixed before signing: the review package did not enumerate the five
`launcher_prayer_name_*` keys I18N-2 had added to it (now Section 6e; text byte-identical to the
brief-verbatim Section 1b, verified by file comparison). **First release APK measured: 78 MB, of which
ONNX is 70.4 MB (~90%)** — the "before" baseline Этап 0.3 needs; recorded in ADR 2/4. **Still open:**
the gate checks the marker's *presence*, not its *coverage*, so a later block can add a Class B key to
an already-signed file and ship it gate-green — dormant until today, active now that files are signed.

**Prior sync (2026-08-16): I18N-1 Multilingual UI CLOSED (see the digest entry below).**

**Prior sync (2026-08-10): Stage 2 — AI Framework; blocks S2-1 "Learned Resolutions" and S2-2 "Explicit
Aliases" are both CLOSED and device-accepted. The design track is finished too — DS-10 Assistant
Migration closed and device-accepted the same day, so the whole shipped surface is on SIDR v1.1.** The Stage-1
AI-Launcher MVP (blocks AIL-0…6) is CLOSED and
device-accepted on SM-A325F. Stage 2 generalizes the router/registry/context/memory into a reusable
on-device AI framework (Action Registry v2, Context Engine v2, User Memory) per the three-stage reframe,
built **feature-first** (grow only the minimal memory abstractions each narrow capability needs).

**S2-1 "Learned Resolutions" — CLOSED (2026-07-11).** On-device learning of which app the user meant
for an ambiguous launch command (rank-first → threshold auto-resolve at `streak ≥ 3`, fully offline, no
LLM/cloud), always correctable via Settings → Learned Choices. Fully built to the approved design and
device-accepted on SM-A325F: pure `domain/memory/resolution/` policy + use-cases + decorator (applied **only**
on the rule `NeedsConfirmation` app-ambiguity branch), Room `resolution_preferences` (`SidrDatabase` v2 +
`Migration1To2` + golden `schemas/2.json`), DI, `LauncherViewModel` wiring (`AutoLaunch` directive → real
`launchApp`; record-on-explicit-choice), and the Settings management screen. **Parity intact:**
`HandleUserCommandUseCase`/`RouteCommandUseCase` untouched → no-preference / non-ambiguous / router-off ⇒
byte-for-byte pre-S2-1 path; **outbound allow-list widened by zero** (preferences never enter an
`AiRequest`). Build gate green. Device pass used two temporary same-label local `SidrProbe` fixture APKs
(installed only for the run, then removed) and covered ambiguous choice, `learning 1/3`, threshold
auto-launch, delete/relearn, correction before K, preferred-target uninstall invalidation, no stale
auto-resolve, and router-off `open Salatuk` parity. Spec:
[docs/superpowers/specs/2026-07-06-learned-resolutions-design.md](docs/superpowers/specs/2026-07-06-learned-resolutions-design.md);
plan: [docs/superpowers/plans/2026-07-06-learned-resolutions.md](docs/superpowers/plans/2026-07-06-learned-resolutions.md);
ADRs: decisions.md "2026-07-06 — Stage 2 kickoff" (design), "2026-07-10 — S2-1 complete
(code-closed; device-pending)", and "2026-07-11 — S2-1 device accepted + closed". **S2-2 "Explicit
Aliases" is CLOSED (device-accepted 2026-08-10, see the DS-7/S2-2 entry at the top); the next Stage-2
architectural slice is A1 Tool & Capability.**

The launcher ships AI-first today: AIL-3 unified the home input over the
unchanged pipeline, **AIL-4 made routing AI-first** (the BYOK cloud `CommandPlanner` — a sanctioned third
pipeline — proposes **registered** actions as non-executing `CommandOutcome.RoutedAction`, never
auto-executed (R4), behind a default-off flag so **router-off ⇒ byte-for-byte rule-only parity**), **AIL-5
turned those proposals into a risk-gated confirmation surface** (CONFIRM → DF-4 confirm card, SAFE →
one-tap accelerator, confirm executes the `LauncherAction` via `ExecuteActionUseCase` — router-proposals
only, rule path untouched), and **AIL-6 device-proved the whole loop on SM-A325F** with a real BYOK
provider (NL → confirm card / one-tap → execution; CANCEL = no-op; offline & router-off = rule-only parity;
0 key leaks; DF-5/6/7 polish) and cleared the `:app:assembleRelease` RC gate. Stage-1 track history
(blocks **AIL-0…6**, plan: [ai-context/ai-launcher-mvp-plan.md](ai-context/ai-launcher-mvp-plan.md)):

- **AIL-0 ✅ DONE (2026-07-05)** Design tokens & visual identity (`core/ui`, presentation-only) — replaced
  the neutral-indigo Material-You look with the "ultra-cyberpunk / early-computer terminal" identity: 4
  `ColorScheme`s (green default / amber alt × dark/light), dynamic colour off by default, `AccentColor`
  enum, **JetBrains Mono** bundled (OFL), brutalist 0/2/4/8dp shapes, previews refreshed, brand window
  background (no white boot flash). Screen redesign stays deferred to AIL-3/5/6 (forks DF-1…DF-8).
  ADR: decisions.md "ADR 2026-07-05 — AIL-0 complete".
- **AIL-1 ✅ DONE (2026-07-05)** Action Registry (`domain`, contracts-only, additive) — `ActionId`/
  `ActionIds` (7 family ids), `LauncherAction` (sealed, unresolved semantic args + `id`), `ActionDescriptor`
  (catalog+risk+schema), `ActionRiskLevel {SAFE,CONFIRM,DANGEROUS}`, `ActionCategory`, `ArgType {STRING}`/
  `ActionArg`, `ActionCatalog` port + `FakeActionCatalog`. Forks decided: argSchema = `List<ActionArg>`
  string-only; `LauncherAction` is a **parallel** hierarchy (not a wrapper) so `ExecutableAction` is
  untouched; the **concrete descriptor catalog + impl is deferred to AIL-2**. No behaviour change (nothing
  consumes it yet); rule→resolver→executor path byte-for-byte unchanged. ADR: decisions.md "ADR 2026-07-05
  — AIL-1 complete".
- **AIL-2 ✅ DONE (2026-07-05)** Web / URL / Play-Store routing (no AI) — concrete `DefaultActionCatalog`
  (7 descriptors; open_url/play_store = CONFIRM, rest SAFE; bound via `ActionBindsModule`) + offline routing
  through the untouched rule→resolver→executor path. Pure `domain/intent/UrlDetector` enforces AIL-Q3:
  **http/https scheme allow-list only** (no silent `intent://`; user `market://` never honored), **curated
  TLD** open (Q1), **punycode/IDN → search** (homograph guard), **query-string → web search** (case-safe,
  Q4). New `OpenUrlIntent`/`PlayStoreSearchIntent` + `OpenUrlAction`/`PlayStoreSearchAction`; matcher gained
  launch-verb URL divert (Q2: `open github.com`→site, `open telegram`→app), `install <app>`→Play Store
  (Q3 install-only), bare-URL/ambiguous recognition (R6). Executor: `ACTION_VIEW` + `market://` (web
  fallback); **R5** configurable provider from `UserPreferences.webProviderTemplate` (default Google,
  denylist-clean key `web_provider_template`). URL/store queries redacted via existing Fork-3 SEARCH
  mapping. `HandleUserCommandUseCase`/`IntentMatcher`/`GenerateReplyUseCase` contracts unchanged; launcher
  fully offline. testDebugUnitTest + assembleDebug green. ADR: decisions.md "ADR 2026-07-05 — AIL-2 complete".
- **AIL-3 ✅ DONE (2026-07-05)** Universal Input — one home field additively routes typed + spoken NL over
  the **unchanged** command pipeline. New pure `domain/input/UniversalInputRouter` + sealed `InputIntent`
  (`Empty`/`DevSentinel`/`Query(raw,siteUrl)`, reuses `UrlDetector`, lowercases its token so mixed-case
  domains open); `core/ui` `SidrCommandPrompt` (DF-2 terminal `>` prompt) + `RouteChipRow` (DF-3 bracketed
  chips, button-role/48dp); `LauncherViewModel` `inputResults` (app-filter via reused `filterApps` +
  WEB/ASK/SITE chips, SITE-gated on safe URL) + `submitWebSearch`/`submitSite` (delegate to unchanged
  `onCommandSubmitted`); `LauncherScreen` "search-overtakes" body, chip dispatch (ASK = assistant nav with
  `Uri.encode`d prefill built in the screen), `SIDR//` wordmark. **DF-1** ships "search overtakes" + a
  hidden session-only **dev Command console** (7-tap wordmark arm + `//dev-mode` toggle; no persisted key).
  **R8** voice reuses the path for free. Command pipeline **byte-for-byte** (`git diff` over intent files
  empty); no `feature→feature`; VM Android-free; launcher fully offline; no LLM. testDebugUnitTest +
  assembleDebug green (7 router + 62→69 VM tests). Deferred to DF-5/AIL-6: true block caret + CRT motion,
  `>`-glyph TalkBack polish. ADR: decisions.md "ADR 2026-07-05 — AIL-3 complete".
- **AIL-4 ✅ DONE (2026-07-06)** LLM Action Router (BYOK cloud) — the **new third port** `CommandPlanner`
  (distinct from `IntentMatcher`/`GenerateReplyUseCase`) in `domain/ai/router/` + `PlanResult` +
  `ActionProposal` + fail-closed `ProposalValidator` (strict: unknown/unregistered id, missing/blank
  required arg, or **any** extra arg key → `NoPlan`; builds the concrete `LauncherAction`) +
  `CatalogSchemaRenderer` (the only outbound content besides the user command) + `RouteCommandUseCase`
  (rule-first; planner consulted **only** on `Unknown`/`LowConfidence`, only when `llmRouterEnabled` +
  online; `RoutedAction`→non-executing `CommandOutcome.RoutedAction`, `Clarify`→`Message`, `NoPlan`→rule
  outcome). Impl `data/ai-cloud/LlmCommandPlanner` = a **separate non-streaming** OpenAI-compatible call
  reusing the shared `HttpClient`/`AiProviderConfigRepository`/Keystore key; hard-timeout→`NoPlan`
  (AIL-Q1), strict content-JSON parse (tolerates prose/fences), non-tool-capable/prose/hallucination→
  `NoPlan` (AIL-Q2 — MVP uses the ADR-sanctioned portable content-JSON path; native `tools` deferred to
  Stage 2), **never throws**. `FeatureFlags.llmRouterEnabled` (default off, denylist-clean
  `flag_llm_router_enabled`) + a "Smart command routing" Settings toggle; `:app` `RouterProvidesModule`;
  `LauncherViewModel` injects `RouteCommandUseCase` (`HandleUserCommandUseCase` unmodified). **§0 guards
  green:** privacy allow-list widened by exactly `ACTION_CATALOG_SCHEMA` (guard-tested at domain +
  real-catalog + outbound-body levels — a planted sensitive value never leaves); **router-off /
  confident-rule / offline ⇒ byte-for-byte rule-only parity** (`RouteCommandUseCaseTest` proves the planner
  is never consulted; whole `LauncherViewModelTest` suite passes unchanged). Forks R1–R4 as recommended (no
  deviation). `:domain:test`+`testDebugUnitTest`+`assembleDebug` green. Device + confirmation-card/execution
  deferred to AIL-6/AIL-5. ADR: decisions.md "ADR 2026-07-06 — AIL-4 complete".
- **AIL-5 ✅ DONE (2026-07-06)** Confirmation & safety gating — turned AIL-4's display-only
  `CommandOutcome.RoutedAction` into an executing surface (**router-proposals only** → the rule path and its
  parity are untouched). New pure `domain/intent/ExecuteActionUseCase` maps a confirmed `LauncherAction` →
  `LauncherIntent` → the **unchanged** `IntentActionResolver` + `ActionExecutor` (never throws). VM gained
  Android-free `PendingRoutedAction` + `pendingRoutedAction` state: `needsConfirmation` → **confirm card**
  (CONFIRM / unregistered) or **one-tap** (SAFE); `confirmRoutedAction()` executes + re-applies the outcome,
  `cancelRoutedAction()` dismisses; neither auto-executes (R4). Permission gate handled in the screen via
  the existing education route (inert in MVP — all catalog gates `null` — but wired + tested). New dumb
  `core/ui` `ConfirmActionCard` = **DF-4 terminal confirm block** (`EXECUTE?` + bracketed `[CONFIRM]` accent
  risk chip + `> commandLine` + bracketed CANCEL/CONFIRM; AIL-0 tokens; no `domain→ui` edge). `:app`
  `provideExecuteActionUseCase`; VM injects it + the bound `ActionCatalog`. Hard rules intact; rule-only
  parity structural. New `ExecuteActionUseCaseTest` (11) + 5 VM tests (VM 70→75); `:domain:test` +
  `testDebugUnitTest` + `assembleDebug` green. Device acceptance deferred to AIL-6. ADR: decisions.md "ADR
  2026-07-06 — AIL-5 complete".
- **AIL-6 ✅ DONE (2026-07-06)** Polish + SM-A325F device acceptance — **closes the Stage-1 MVP**. Landed the
  bundled AIL-4/5 code + DF-5/6/7 polish (owner forks pre-decided), cleared the RC gate, and executed the
  mandatory device pass with a real BYOK provider. **Env note (no repo change):** the machine's JDK had
  rolled to 25 (Gradle 8.10.2 can't parse it → `IllegalArgumentException: 25.0.3`); fixed by running Gradle
  under JBR 21 + a locally-downloaded Temurin **JDK 17** toolchain (`-Porg.gradle.java.installations.paths`)
  + a git-ignored `local.properties` — `:domain:test`+`testDebugUnitTest`+`assembleDebug`+`:app:assembleRelease`
  green. **Device (SM-A325F/A13, agent-drove adb, owner typed the key on-device only):** one-field routing
  (app/site/web/Play-Store/settings/assistant, all external `ACTION_VIEW`, mic visible); **NL router**
  ("take me to the github homepage" → `open https://github.com` CONFIRM → **DF-4 confirm card**; CANCEL =
  no execution, CONFIRM → github opens; "see everything installed" → `show_apps` SAFE → **one-tap `[ ▸ apps ]`**);
  **R4** nothing auto-runs; **offline/router-off ⇒ rule-only parity** (airplane + flag-on NL → "Unknown
  command" rule fallback; settings nav offline OK); **privacy** 0 `sk-or-` in logcat + guard tests green;
  **DF-7** green↔amber instant + survives force-stop, **DF-5** block caret + chip press-invert, **DF-6** `>_`
  brand mark / no white flash. Honest partials (non-gating): DF-5 scanlines code-verified but too faint to
  frame-capture; assistant chat streaming (a Phase-5 item, not an AIL-6 gate) not re-driven via adb but its
  cloud transport is proven by the router round-trip; boot-warmup reboot + LOW_END motion suppression
  unexercised on this MID/HIGH device. No acceptance-blocking bug found. ADR: decisions.md "ADR 2026-07-06 —
  AIL-6 complete". **Stage-1 AI-Launcher MVP CLOSED (AIL-0…6); next = Stage 2 — AI Framework.**

**Blocking ADR for AIL-4 (✅ honored):** `CommandPlanner` is a *third* pipeline (structured
routing-via-LLM); "local matching runs before any LLM call" is preserved (rule path always first);
LLM proposals never auto-execute risky actions — this consciously refines the "matching ≠ generation" hard
rule. Recorded in decisions.md "ADR 2026-07-05 — AIL-4" (design) + "ADR 2026-07-06 — AIL-4 complete".

**Not in this track (separate/deferred):** ONNX NLU model (OQ#1/#2), embeddings (OQ#3), STT matrix
(OQ#4), Android 9/11/14 + LOW_END device matrix, boot warmup. RC/hardening polish (release build,
`<400ms` cold) is not a gate for the AI-launcher feature work and can run in parallel.
*(The phase-by-phase history below is retained for context.)*

**Phase 3 is DONE (Blocks A → D, 2026-06-21).** The MVP loop works: type `open telegram` →
resolves + launches offline; tap a grid app → launches; unknown → fallback UI, no crash.
**Live launch verified on device (SM-A325F, Android 13).** `assembleDebug` +
`testDebugUnitTest` green. Details in
[ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md) and the
Block D ADR in [ai-context/decisions.md](ai-context/decisions.md).

**Phase 4 — persistence, state & navigation hardening** (Blocks E → H, see
[ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)). **Block E DONE (2026-06-22):** DataStore
Preferences foundation — `UserPreferences`/`FeatureFlags`/`DeviceProfileCacheEntry`/`CachedSuggestion`
+ 4 repos (`Flow` read / `OperationResult` write), impls over one `DataStore<Preferences>`,
`PersistenceProvidesModule`+`PersistenceBindsModule` in `:app`, 4 fakes, 14 JVM tests green
(round-trip survives simulated restart). Privacy inventory enforced by a key-name guard test.
**Block F DONE (2026-06-22):** Room persistence — 3 history domain models + repo interfaces
(`AppUsageRecord`/`SuggestionRankingRecord`/`IntentMatchRecord` + `IntentMatchType`) in `:domain`;
Room entities, DAOs, `SidrDatabase` (v1, `exportSchema=true`), `TypeConverters`, mappers with
**SEARCH/UNKNOWN redaction** (query content never stored), 3 `*RepositoryImpl` with retention-caps
(200/100/200), `DatabaseModule`+`HistoryBindsModule` in `:app`; intent-match write live in
`HandleUserCommandUseCase`; grid sorts by usage recency/frequency; 2 fakes in `:core:testing`;
17 new JVM tests (Robolectric DAO/repo + redaction + column-privacy guard) green. Golden schema
`schemas/1.json` committed; `MigrationTestHelper` runway in `androidTest`.
**Block G DONE (2026-06-23):** permission-education — new `:feature:permission_education` module
(Compose + Hilt) replaces the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus` +
`PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker`
in `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (key
`perm_dismissed_wallpaper`); education ≠ request (Fork 5); live `SET_WALLPAPER` trigger from a
launcher "Wallpaper" button; denial disables only that feature, core never blocked.
**`BIND_ACCESSIBILITY_SERVICE` not requested/educated (Ph8).** 2 fakes + 12 new JVM tests green.
**Block H DONE (2026-06-23):** hardening + docs-sync — `UiState.Error(retryable)` + `retry()`
(H2, no process restart); `commandInput` via `SavedStateHandle` (H3 process-death restore);
`refreshStatus()` made upgrade-only (H-b, never downgrades `PERMANENTLY_DENIED`); privacy guard
extended to Room **table names** (H-a); temporary home-screen "Wallpaper" button removed (H4);
nav safe-fallback + kill→reopen verified on device (H5); `docs/architecture.md` synced to the real
3-flow `LauncherViewModel` design + Forks 1/2/8/9 (H6). Content-restore half of Fork 6 deferred to
Ph7 (no Ph4 display surface). `assembleDebug` + `testDebugUnitTest --rerun-tasks` green.
**Phase 4 closed; next = Phase 5 (cloud AI).** Details in
[ai-context/decisions.md](ai-context/decisions.md) ("ADR Block H").

**Phase 5 CLOSED (code-closed, Blocks I → N, 2026-06-27). ⚠ partial device acceptance executed
2026-07-01** (NOT dissolved — carried into Phase 7 Tracking): Block-J `SecretStoreInstrumentedTest`
**passed on SM-A325F** (real Keystore round-trip / per-provider isolation / StrongBox-fallback-safe
behavior; `OK (3 tests)`); Block-N N5 streaming / offline / cancel / rotation remains
**pending-config/manual**. Details:
[ai-context/phase-5-plan.md](ai-context/phase-5-plan.md).
**Round 2 update (2026-07-02):** launcher command `assistant` now opens the Assistant setup screen on
device without depending on NLU/model presence (**PASS** for the entry path), but the provider form is
still empty, so real streaming / offline fallback / retry / cancel / terminal-state acceptance remains
**PENDING-CONFIG**.

**Phase 6 CLOSED (code-closed, Blocks O → R, 2026-06-29). ⚠ partial device acceptance executed
2026-07-01** (NOT dissolved — carried into Phase 7 Tracking): APK install presence on SM-A325F **PASS**;
launcher **launch-smoke PASS only** (`am start -W` cold starts `1381ms` then `1026ms`; `<400ms`
performance budget still **pending/perf-risk**). Trim-memory is **PARTIAL**: `RUNNING_CRITICAL` plus a
backgrounded `BACKGROUND`/`COMPLETE` run left the process alive (**no crash observed**), and
`OpenGLRenderer` logged `trimMemory(TRIM_MEMORY_COMPLETE)::destroyRenderingContext`; however the device was
still in **no-model state**, so native ONNX-session release is not yet proven. `OnnxIntentClassifierInstrumentedTest`
was run on device but all three tests **assumption-skipped** with
`device-pending: nlu/intent.onnx not bundled (see tools/nlu/README.md)`, so Block-P P5 `<150ms`
inference + on-device NLU and Block-Q real model provisioning remain **pending/model-blocked** on
OQ#1/#2 (`intent.onnx` / `vocab.txt`). Details:
[ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md).
**Round 2 update (2026-07-02):** no `intent.onnx` / `vocab.txt` were found either in the repo or in the
app sandbox on device, so the status remains **PENDING-MODEL** and no real NLU acceptance is claimed.

**Phase 7 — voice input + contextual suggestions** (Blocks S → W, see
[ai-context/phase-7-voice-suggestions-plan.md](ai-context/phase-7-voice-suggestions-plan.md); forks
decided 2026-06-29). **Block S DONE (2026-06-29):** pure `:domain` suggestion+voice contracts
(`Suggestion`/`SuggestionSource`/`SuggestionContext`/`TimeOfDay`, `SuggestionProvider`/
`SuggestionEngine`/`SuggestionRanker` + pure `HeuristicSuggestionRanker`; `SpeechInputSource` port
+ `SpeechRecognitionState`/`SpeechRecognitionError` — **enum, UPPER_SNAKE_CASE** like
`AiStopReason`); fakes in `:core:testing`; 12 new JVM tests / 131 domain total; `assembleDebug` +
`testDebugUnitTest` green; 0 new Gradle deps. **Block T DONE (2026-06-30):** voice input —
`AndroidSpeechInputSource : SpeechInputSource` in `:core:android` (on-device recognizer preferred behind
`SDK_INT>=S` + `EXTRA_PREFER_OFFLINE` fallback; `RecognitionListener` → `callbackFlow`; all recognizer
calls marshalled to `Handler(Looper.getMainLooper())`; `destroy()` on `awaitClose` + `AtomicBoolean`
cancel-before-create guard so no recognizer/mic leak; full error-code → `SpeechRecognitionError` map; **no
transcript/audio logged**) — the **only** `android.speech` site (grep-proven). `VoiceModule` DI in `:app`
(`@ApplicationContext`); `FakeSpeechInputSource` drives JVM tests. `PermissionFeature.VOICE_INPUT.requestable`
flipped **true**; education VM routed via `SavedStateHandle` (`permission_education?feature={feature}`, defaults
WALLPAPER), screen request/post-grant feature-driven + `ON_RESUME` `refreshStatus()`. **Block-H `refreshStatus()`
debt discharged:** genuine `GRANTED→DENIED` revocation reflected; `PERMANENTLY_DENIED→DENIED` suppression
reachable only from an established `PERMANENTLY_DENIED` (never from `GRANTED`) — the two never collide; KDoc
rewritten + JVM-tested. Launcher mic affordance (shown only when `isVoiceInputAvailable`): granted →
`startVoiceInput()` (partials → `commandInput`, Final → **unchanged** `onCommandSubmitted`); not-granted →
route to education (framework `checkSelfPermission`, **no `:feature:launcher` build change**).
`HandleUserCommandUseCase` untouched; voice text = `USER_COMMAND` byte-for-byte (F7-9). **9 new JVM tests; 383
JVM total / 0 failures; `assembleDebug` (Hilt graph valid) + `testDebugUnitTest` green; 0 new Gradle deps; no
manifest line (RECORD_AUDIO present since Block N).** Real recognizer **device-pending (OQ#4, independent of
U/V/W)**. Details: decisions.md "ADR 2026-06-30 — Block T complete". **Block U DONE (2026-06-30):**
contextual suggestion engine — `:data:repository` gained `TimeOfDaySuggestionProvider` (zero-permission,
fixed per-`TimeOfDay` table) + `UsageSuggestionProvider` (recency-decay + frequency-ratio over
`UsageHistoryRepository`) + `CalendarSuggestionProvider`/`LocationSuggestionProvider` (`READ_CALENDAR`/
`ACCESS_FINE_LOCATION`-gated, query/read only enough to decide *whether* a signal exists — never a raw
title/coordinate — and emit a single fixed generic `Suggestion`) + `SuggestionEngineImpl` (structured-
concurrency aggregate, fault-isolated per provider, `HeuristicSuggestionRanker`-ranked, persists to
`SuggestionRankingRepository`+`SuggestionsCacheRepository` as `CachedSuggestion(label,actionId)` only,
gated by `aiSuggestionsEnabled`, fire-and-forget persist failures logged via direct `Log.w` — the module's
first such path; `core/common`'s dormant `ResultLogger` was deliberately **not** wired). DI:
`SuggestionsProvidesModule` in `:app`. `CALENDAR_SUGGESTIONS`/`LOCATION_SUGGESTIONS.requestable` flipped
**true** reusing Block T's request-flow template **literally** (zero edits needed to
`PermissionEducationScreen.androidPermission()`'s already-exhaustive `when`, or to either VM's
constructor); manifest gained exactly `READ_CALENDAR`+`ACCESS_FINE_LOCATION`. Per-feature dismissed flag
stays in-memory only for all three non-`WALLPAPER` requestable features (`VOICE_INPUT` included) — a
Block-G-era structural fact (the feature names collide with `PrivacyInventoryGuardTest`'s own denylist),
not a per-block deviation. **Privacy guard delivered as 4 executable proofs**: persistence-shape test,
`SuggestionProviderPrivacyGuardTest` (plants a real sensitive event title/GPS fix and runs the **actual**
providers against them, asserts nothing leaks), an outbound-isolation regression in `:domain`, and a
reflection-based fix to Block L's `AiRequestGuardTest` (closed a real hand-maintained-inventory drift gap
found during review — `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES` now reflect against `AiRequest`/
`AiError`'s actual declared fields). **16 new JVM tests; 399 JVM total / 0 failures; `assembleDebug` +
`testDebugUnitTest` + `:domain:test` green; 0 new Gradle deps; `android.location`/`CalendarContract`
confined to `:data:repository`.** `HandleUserCommandUseCase` untouched. **Block W DONE (2026-07-01):**
`LauncherUiState` now carries `suggestions`; the host `LauncherViewModel` owns the single suggestions source
and performs cache-first paint from `SuggestionsCacheRepository` followed by a fresh `SuggestionEngine`
result that supersedes rather than merges; `:feature:suggestions` stayed a stateless `SuggestionsRow`;
launcher-home rendering + suggestion tap routing are wired with no `feature→feature` edge. Background
completion added `SuggestionPrecomputeWorker` + `UsageCleanupWorker`, unique-periodic scheduling, boot
warmup via `RECEIVE_BOOT_COMPLETED`, and startup re-scheduling from `SidrLauncherApp`; precompute is
fail-closed and never schedules/runs when `aiSuggestionsEnabled == false`, on `LOW_END`, or while battery
saver is active. **Phase 7's user-facing part is therefore closed. Block V remains open as a separate
model/runtime track** (ONNX `TextEmbedder` impl + semantic re-rank; gated on OQ#3).

Phase 3 result, Blocks A → D:

- **A ✅** `OperationResult` / `OperationError` in `domain`; `domain → core/common` edge removed.
- **B ✅** `:data:repository`; `InstalledAppsRepository` (PackageManager, offline); app grid +
  command input in `feature/launcher`; `:core:testing` bootstrapped.
- **C ✅** `LauncherIntent` / `ExecutableAction` / `IntentCandidate`; `IntentMatcher` +
  `IntentMatchResult`; `CommandNormalizer`; `DefaultIntentConfidencePolicy`; `RuleBasedIntentMatcher`
  (`:data:repository`, Android-free); `IntentActionResolver`; 65 JVM tests.
- **D ✅** `ActionExecutor` port + truncated `ActionExecutionResult` + `CommandOutcome` (12
  variants) + `HandleUserCommandUseCase` (domain); `AndroidActionExecutor` (`:data:repository`);
  VM wiring + `CommandFeedback` fallback UI (`feature/launcher`); DI via `IntentBindsModule` +
  `IntentProvidesModule` (`:app`). Routing: low/medium never auto-executes; navigation + CLEAR +
  SHOW_APPS + ambiguity never go through the executor.

**All Phase-4 slices delivered:** DataStore (E), Room (F), permission-education (G), hardening (H).
**Frozen to Phase 5+:** secrets (Ph5), cloud AI (Ph5), ONNX (Ph6), voice + context-suggestions
(Ph7), accessibility (Ph8), WorkManager (Ph6/9), full Hilt→KSP migration (Ph9).

## Status snapshot

- P0 ✅ docs/decisions · P1 ✅ compile-ready skeleton · P2 ⏭ reordered into Block B ✅ ·
  P3 foundation `3.0.1`–`3.1.5` ✅ (result types + navigation).
- Block A ✅ `OperationResult`/`OperationError` in `domain`; `domain → core/common` edge gone.
- Block B ✅ `:data:repository`, `InstalledAppsRepositoryImpl`, app grid, command input,
  `:core:testing` with `FakeInstalledAppsRepository`.
- Block C ✅ full intent domain — contracts, normalizer, rule-based matcher, resolver, 65 tests.
- Block D ✅ MVP loop — `ActionExecutor` + `CommandOutcome` + `HandleUserCommandUseCase`,
  `AndroidActionExecutor`, VM wiring + `CommandFeedback`, DI split modules. Live-verified on device.
- **Phase 3 CLOSED (2026-06-21).**
- **Phase 4 Block E ✅ (2026-06-22)** — DataStore Preferences: domain models + 4 repo interfaces,
  impls + mappers (`@Serializable` DTO stays in `:data:repository`), DI split modules, fakes, 14
  JVM tests. Privacy key-name guard green.
- **Phase 4 Block F ✅ (2026-06-22, fixes 2026-06-23)** — Room: 3 history domain models + interfaces
  in `:domain`; entities + DAOs + `SidrDatabase` (v1) + mappers with SEARCH/UNKNOWN redaction + 3
  `*RepositoryImpl` (retention 200/100/200) in `:data:repository`; `DatabaseModule`+`HistoryBindsModule`
  in `:app`; intent-match write live in use case; usage-sorted grid; 2 fakes + 17 new JVM tests
  (Robolectric + column-privacy guard) green; `MigrationTestHelper` v1 baseline verified on SM-A325F
  (Android 13); both history writes gated behind `FeatureFlags.usageHistoryEnabled` (4 flag-gate tests).
  Follow-up (2026-06-23): `recordMatch` moved off critical path via `recordingScope.launch {}` (injected
  `@ApplicationScope CoroutineScope`, required param, no lifecycle-less default); `CancellationException`
  re-thrown in `LauncherViewModel.recordUsage`; 2 more tests; 67 domain + 25 launcher JVM, 0 failures.
- **Phase 4 Block G ✅ (2026-06-23)** — permission-education: new `:feature:permission_education`
  module (Compose + Hilt) replacing the `AppNavHost` placeholder; `PermissionFeature`/`PermissionStatus`
  + `PermissionChecker`/`PermissionPrefsRepository` ports in `:domain`; `AndroidPermissionChecker` in
  `:core:android`; per-feature `PermissionPrefsRepositoryImpl` over DataStore (`perm_dismissed_wallpaper`);
  education ≠ request (Fork 5) with a live `SET_WALLPAPER` trigger from a launcher "Wallpaper" button;
  denial disables only that feature, core never blocked; accessibility deferred (Ph8). 2 fakes + 12 JVM
  tests green.
- **Phase 4 Block H ✅ (2026-06-23)** — hardening + docs-sync (Fork 6 + Fork 9): `UiState.Error` gained
  `retryable: Boolean = false` + `LauncherViewModel.retry()` (no process restart, retry action not in the
  data class); `commandInput` backed by `SavedStateHandle` (process-death restore); `refreshStatus()`
  upgrade-only (never downgrades `PERMANENTLY_DENIED`, partial fix — revisit Ph7/`RECORD_AUDIO`); privacy
  guard extended to Room **table names** (hand-synced `TABLE_NAMES`); temporary home-screen wallpaper
  button removed (entry returns with `feature/settings`); nav safe-fallback latent (no bad route in Ph4)
  + kill→reopen verified on device (PID change, input restored, no crash); content-restore half of Fork 6
  deferred to Ph7 (no display surface yet). `docs/architecture.md` synced to the real 3-flow
  `LauncherViewModel`. `assembleDebug` + 105 JVM tests green. **Phase 4 CLOSED (Blocks E → H).**
- **Phase 5 Block I ✅ (2026-06-24)** — multi-provider AI domain contracts (pure): `domain.ai`
  (`AiProviderId`/`AiModelId` opaque value classes, `AiRequest`/`AiMessage`/`AiRole` with **no sampling
  params**, `AiChunk`+`AiStopReason`(refusal = success terminal)+`AiUsage`, `AiError`,
  `GenerativeAiEngine`+`GenerativeRouter`, `AiChunks.assembleText`), `domain.security`
  (`SecureSecretStore`+`SecretKey`+`SecretKeys.apiKey(provider)`, per-provider, `OperationResult`/never
  throws), `domain.connectivity` (`ConnectivityChecker`); 3 fakes in `:core:testing`; 10 new JVM tests
  green. Vendor-neutral (grep `anthropic|openai|gemini|claude` over `domain/src/` empty); `:domain`
  stays stdlib+coroutines; no new deps; intent code + `feature/assistant` untouched. Details:
  decisions.md "ADR Block I". **Addendum (2026-06-24):** added pure `AiProviderConfig` +
  `AiProviderConfigRepository` (`…domain.ai`) for user-configurable providers (base URL + free-text
  model; key stays in `SecureSecretStore`) + fake + round-trip test; plan re-oriented to
  OpenAI-compatible-first.
- **Phase 5 Block J ✅ (2026-06-24)** — Keystore-backed `SecureSecretStore` (BYOK, per-provider).
  `:data.repository.security`: `SecretCipher`+`EncryptedBlob` crypto seam (Fork 7); `KeystoreSecretCipher`
  (AES-256-GCM in `AndroidKeyStore`, alias `sidr_secret_aead_v1`, StrongBox-with-fallback,
  `userAuthRequired=false`); `SecureSecretStoreImpl` over a **dedicated `sidr_secrets` DataStore**
  (`@SecretsDataStore` qualifier — separate file from Block E's `sidr_preferences`, so credential keys
  never enter the privacy-guarded `ALL_KEY_NAMES`); `get` decrypt-fail/invalidation/corrupt →
  `Success(null)` + clear-entry, `put`/`remove` → `Failure` on I/O, never throws, `CancellationException`
  re-thrown. `java.util.Base64` (no Robolectric). DI: `SecretsProvidesModule`+`SecretsBindsModule` in
  `:app`. `FakeSecretCipher` + **9 JVM tests** (pure JVM) green; `SecretStoreInstrumentedTest` (3 tests,
  real Keystore) **compiles — device run pending on SM-A325F**. ESP/security-crypto absent; no secret
  logged; `PrivacyInventoryGuardTest` green; `:domain`/`feature` untouched. Details: decisions.md
  "ADR Block J".
- **Phase 5 Block K ✅ (2026-06-24)** — OpenAI-compatible cloud engine (SSE → `Flow<AiChunk>`).
  `:data:ai-cloud` (Hilt-free, no `:core:android` edge): `OpenAiCompatibleGenerativeAiEngine` streams any
  OpenAI-compatible `chat/completions` endpoint — base URL + free-text model from
  `AiProviderConfigRepository`, key from `SecureSecretStore`, `Authorization: Bearer`. **Minimal body**
  (`model`/`messages`/`max_tokens`/`stream:true`, `system` leading), **no sampling params** (absent from
  the private `@Serializable` DTOs); robust URL join (`removeSuffix("/")+"/chat/completions"`, keeps
  `/v1`); inputs trimmed; **HTTPS-only** (non-`https://`→`InvalidRequest`, socket never opened).
  **Manual SSE** over `bodyAsChannel()`+`readUTF8Line()` (no `ktor-client-sse`): `Text` deltas, terminal
  `Completed(stopReason,usage?)` at `[DONE]`/EOF; `finish_reason` captured off the content-empty terminal
  delta; `content_filter`/`delta.refusal`→`REFUSAL` (**sticky**, success terminal). Full `AiError`
  taxonomy as terminal `Failed` (MissingCredentials/Unauthorized/RateLimited(Retry-After
  delta+date)/ServerError/InvalidRequest/Network/Offline/Timeout/Unknown); `detail` safe-only. Per-read
  first-token + idle `withTimeoutOrNull` (**no `requestTimeoutMillis`**); cold `flow{}` + `execute{}` +
  `flowOn`, `CancellationException` re-thrown (collection-cancel aborts the request).
  `AiProviderConfigRepositoryImpl` lives in **`:data:repository`** over the shared `sidr_preferences`
  store (+ 4 denylist-clean `ai_provider_*` keys in `ALL_KEY_NAMES`); `@CloudEngine` engine + `HttpClient`
  (Android) providers in `:app` (`AiCloudProvidesModule`), config-repo bound in `PersistenceBindsModule`;
  `network_security_config.xml` (no cleartext) wired in the manifest. 20 MockEngine tests + 4 config-repo
  tests + full regression green; domain vendor-neutral/pure; only `ktor-client-mock` added (test-only).
  Details: decisions.md "ADR Block K". **Next = Block M (router + static fallback).**
- **Phase 5 Block L ✅ (2026-06-27)** — prompt/context builder + outbound privacy guards (pure, `:domain`).
  `…domain.ai`: `PromptContextBuilder` (public `build(userCommand)` **only** — no context-bag overload)
  → minimal `AiRequest` = **one verbatim `USER` message** + static `DEFAULT_SYSTEM_PROMPT` (short,
  context-free, vendor-neutral, no model pinned) + `maxOutputTokens=512` (guidance) + `model=null`;
  nothing else assembled (no device/usage/calendar/location/history/contacts/clipboard). `OutboundContextPolicy`
  = **positive allow-list** `{USER_COMMAND, STATIC_SYSTEM_PROMPT, GENERATION_LIMITS}` (fail-closed, Fork
  P5-3) + `FORBIDDEN_CONTEXT_TERMS`/`CREDENTIAL_TERMS` + hand-synced `OUTBOUND_FIELD_NAMES`/`AIERROR_FIELD_NAMES`
  (Phase-4 `TABLE_NAMES` precedent). Denylist scanned over **static text + field inventories, NEVER user
  content** (regression test keeps "calendar" in a user command); `token` excluded (collides with
  `maxOutputTokens`); credential terms scanned over field-name inventories **not rendered `toString`**
  (avoids the `MissingCredentials`/"credential" vacuous collision — a refinement past the prompt's literal
  toString scan); leak guard scans `toString` only for a planted sentinel. 11 reflection-free JVM tests
  (`AiRequestGuardTest` 5 + `OutboundSecretLeakGuardTest` 6) green, **91 domain total**; `:domain` stays
  stdlib+coroutines/vendor-neutral; no new deps; `IntentMatcher`/`HandleUserCommandUseCase`/`feature/*`/
  K-M-N untouched. Details: decisions.md "ADR Block L". **Next = Block M (router + static fallback,
  consumes K + L).**
- **Phase 5 Block M ✅ (2026-06-27)** — routing seam + static fallback + `GenerateReplyUseCase`.
  `:data.repository.ai`: `StaticFallbackEngine` (canned reply, no network, always `Completed`);
  `DefaultGenerativeRouter : GenerativeRouter` — cold `flow { emitAll(selectEngine().generate(request)) }`,
  ordered **ONNX slot (reserved) → cloud (online + config + non-blank key) → static** (latest-wins,
  selection at collection time; `firstOrNull()` on config flow; `Failure` from secret store → static;
  never throws expected errors). `core/android/connectivity/AndroidConnectivityChecker` (Hilt-free,
  `callbackFlow` + `conflate` + `distinctUntilChanged`, `ACCESS_NETWORK_STATE` added to manifest);
  `GenerateReplyUseCase` in `:domain` (`generate(command)` = `engine.generate(builder.build(command))`);
  `@FallbackEngine` qualifier co-located with `@CloudEngine` in `:app`; `GenerationProvidesModule`
  (fallback + router + unqualified-engine→router + builder + use case); `ConnectivityModule`. No
  data→data edge (router refs port only); single unqualified `GenerativeAiEngine` binding (the router);
  `HandleUserCommandUseCase` untouched; `:domain` pure; `core/android` gains `coroutines.core`.
  7 router tests + 6 use-case tests green; **full JVM regression green** (96 domain total); `assembleDebug`
  green (Hilt graph valid). Details: decisions.md "ADR Block M".
- **Phase 5 Block N ✅ (2026-06-27)** — assistant streaming UI + provider-settings form + phase close.
  `:feature:assistant` gains Hilt (`kapt` + `hilt.android` + `hilt.navigation.compose` etc., mirroring
  `permission_education`). `AssistantViewModel` (`@HiltViewModel`, 3 domain-port deps):
  `Flow<AiChunk>` collected in `viewModelScope` (survives rotation, aborted on back-nav, latest-wins
  `retry()`); `AssistantUiState(reply, status, form)` in a single `StateFlow`; `status` =
  `AssistantStatus {Idle/Streaming/Done(refused)/Error(error,retryable,showProviderCta)}`; refusal =
  `Done(refused=true)` (success terminal, not an error); credential errors → `showProviderCta=true`
  (CTA, not Retry); `AiError→UiError` mapper feature-local (prevents `core/common→domain` edge).
  **No `SavedStateHandle`** (deliberate — key must never touch saved state; prompt/reply are transient;
  see decisions.md "ADR Block N"). `saveProvider`: `providerId` derived from host (lowercase, path
  stripped), `https://`-validated, config written to `AiProviderConfigRepository`, key to
  `SecureSecretStore` (blank key skips put); key never logged/in state/displayed back. `AssistantScreen`
  pure render: first-run form when no config; streaming chat + expandable provider form when configured.
  `AppNavHost`: real `hiltViewModel()` destination + `LaunchedEffect(navigationEvents)` + safe-fallback.
  14 JVM tests green; **262 total JVM tests**; `assembleDebug` green. On-device (N5) + Block-J
  `androidTest` **pending** SM-A325F device run. Details: decisions.md "ADR Block N + Phase 5 close".
  **Phase 5 CLOSED (Blocks I → N). Next = Phase 6 (ONNX NLU).**
- **Phase 6 Block O ✅ (2026-06-27)** — local-AI domain contracts + `DeviceProfile`/`DeviceCapability`
  model + gating policy. `domain.ai.local`: `ModelId`, `ModelAvailability`, `ModelAvailabilityRepository`,
  `TextEmbedder` (port-only, impl deferred Phase 7). `domain.device`: `DeviceProfile` (enum
  `LOW_END/MID_RANGE/HIGH_END`), `DeviceCapability` (ramBytes/cpuCores/nnapiAvailable/thermalOk/
  batteryOk; **no `online` field** — owned by `ConnectivityChecker`), `DeviceProfileProvider` port,
  `LocalInferenceGate` (pure policy: LOW_END→false always; MID/HIGH→true iff Available+thermalOk+
  batteryOk). Port-topology: **NLU rides the existing `IntentMatcher` port**; no parallel
  `IntentClassifier` created. 4 fakes in `:core:testing` (`NoOpIntentMatcher`, `FakeTextEmbedder`,
  `FakeModelAvailabilityRepository`, `FakeDeviceProfileProvider`). 22 new JVM tests (**284 total**, 0
  failures); purity guard green; all greps clean; intent pipeline untouched.
  Details: decisions.md "ADR 2026-06-27 — Block O complete". **Next = Block P** (gated on model +
  tokenizer + label-set selection — open question must be resolved first).
- **Phase 6 Block P ✅ (2026-06-28)** — ONNX runtime in `:data:ai-local` (platform-risk block). Open
  Question #1 resolved (**amended multilingual 2026-06-29** — multilingual WordPiece teacher
  `bert-base-multilingual-uncased` → prune+distill+int8, pruned `vocab.txt` ~20–30k, `maxLen 48`),
  WordPiece **uncased** vocab, **7 (language-independent) classes**
  (`NluLabel` argmax order LAUNCH_APP/SEARCH/OPEN_SETTINGS/SHOW_APPS/HELP/OPEN_ASSISTANT/UNKNOWN;
  `CLEAR` rule-only; slots heuristic). **P2a pure, ONNX-free, JVM-tested:** `WordPieceTokenizer`
  (faithful HF BasicTokenizer+Wordpiece; byte-exact vs an **independent** stdlib reference golden —
  the #1 silent-failure guard), `IntentLabelMapper` (softmax→argmax→label→`IntentMatchResult` +
  **confidence escape** at floor 0.60 / argmax==UNKNOWN), `SlotExtractor` (verb/filler strip),
  `NluLabel`, `OnnxModelSpec`. **P2b/P3/P4 thin shell** `OnnxIntentClassifier : IntentMatcher`
  (`source = NLU`): lazy + single + `Mutex`-serialized session on `Dispatchers.Default`;
  **per-inference** `LocalInferenceGate.allowsLocalNlu` re-check with fresh `capability()` (Fork P6-4
  moment 2); files resolved via `LocalModelFiles` seam (Q impl) **before** any `OrtEnvironment` call so
  missing model/vocab degrades JVM-testably; tensors + `OrtSession.Result` in `use{}`; any failure →
  lowest-confidence result, never thrown; **no user text logged**. **Lifecycle:** `AutoCloseable` +
  `SessionLifecycle` ONNX-free seam (`:app onTrimMemory` wiring deferred to Q/R — classifier not
  bindable until Q's impls exist); **transient gate-off keeps the session, sustained (debounced ~30s)
  + trim tears it down**, re-inits lazily; `runMutex.tryLock()` + `pendingTeardown` avoids closing
  mid-run. **P1** `OnnxSessionFactory`: **CPU deterministic default** + NNAPI appended only when
  `nnapiEnabled && sdkInt>=29` (flag in `OnnxRuntimeFlags`, off by default; both init-failure and
  degraded-success handled; `nnapiEnabled`/`sdkInt` test seams for P5 path comparison). Pinned I/O:
  `input_ids`+`attention_mask`[+`token_type_ids` iff declared] int64 `[1,48]` (OQ#1 amended; was 32),
  `logits` float `[1,7]`
  read by index 0. **P0** `tools/nlu/` (out of source sets): stdlib golden generator (ran), placeholder
  + train/export scripts (device-pending — no torch/onnx/net). ONNX Java surface re-verified vs the
  bundled 1.20.0 AAR (`javap`). `:core:android` edge added; **no new dep**. ONNX confined to two shell
  files (`OnnxIntentClassifier`/`OnnxSessionFactory`) — grep clean incl. pure layer; `:domain`
  untouched; no network on inference path; generative router slot + intent/Phase-3/5 untouched. **21
  new JVM tests, 0 failures**; `androidTest` compiles (device-pending, Assume-skips w/o asset);
  `assembleDebug` (clean baseline) + full JVM regression green. NLU softmax confidence **uncalibrated**
  vs rule scale → open Q to **Block R**. Details: decisions.md "ADR 2026-06-28 — Block P complete".
  **Next = Block Q** (DeviceProfile detector + ModelStore + SHA-256 + WorkManager; gated on Open
  Question #2 — model hosting/URL). **Phase 6 NOT closed (Block R closes it).**
- **Phase 6 Block Q ✅ (2026-06-28)** — DeviceProfile detector + model management + WorkManager download
  gating. **OQ#2 branch = NOT resolved (expected):** `ModelDownloadConfig.INTENT_NLU_PENDING` is the
  single inert device/release-pending seam (blank URL/hash, `TODO(OQ#2)`, `isPinned=false`); the whole
  mechanism is JVM-tested vs fakes now, live download device-pending. `:core:android`: pure
  `DeviceProfileClassifier` (`(ram,cores)→DeviceProfile`; LOW_END `<2.5 GB` or `<4` cores, HIGH_END
  `≥5.5 GB`+`≥8` cores) + `(rawSignals)→DeviceCapability` (`nnapiAvailable=sdk≥29` hint, `thermalOk=status<SEVERE`
  w/ `-1` no-signal sentinel, `batteryOk=!powerSave`) + `DeviceProfileCacheMapping` (lossy LOW_END-vs-rest)
  + `AndroidDeviceProfiler : DeviceProfileProvider` (in-mem profile cache + write-through to the
  **pre-existing** `DeviceProfileCacheRepository` via `@ApplicationScope`; capability re-read per call) +
  `testImplementation(junit4)`. `:data:ai-local`: `ModelStore` (quarantine→SHA-256-verify→atomic
  `Files.move(ATOMIC_MOVE)`→ready, **never exposes unverified**, implements P's `LocalModelFiles`, vocab
  via injected `vocabOpener`/bundled `assets/nlu/`, also implements `ModelFilePresence`) + `Sha256Verifier`
  + `ModelDownloadScheduler` port + `ModelProvisioner` (`provision()`→`ProvisionResult`; idempotent,
  re-throws cancellation, permanent-vs-transient) + `ModelManager.ensureModel()` (**enqueue-gate-static-only §6.A**:
  `profile≠LOW_END && availability≠Available && config.isPinned` — thermal/battery are WM constraints +
  the per-inference re-check inside `OnnxIntentClassifier`, NOT the enqueue decision). `:domain` (rework):
  `ModelDownloader` + `ModelFilePresence` ports. `:data:repository`: `ModelAvailabilityRepositoryImpl`
  (shared `sidr_preferences`, `model_available_ids` stringSet **cross-checked vs `ModelFilePresence` disk
  truth** → all 3 Block-O states reachable, marker-but-missing = not-Available; `ALL_KEY_NAMES`, privacy
  guard green). `:data:ai-cloud` (rework): `KtorModelDownloader` plain class (HTTPS-only, retry taxonomy
  4xx/non-HTTPS→permanent / 5xx/network→transient, `@Provides`-wired in `:app`). `:app`: `@HiltWorker
  ModelDownloadWorker` (thin shell → `ModelProvisioner`, maps to `Result.success/retry/failure`, no
  foreground service) + `WorkManagerModelDownloadScheduler` (`enqueueUniqueWork` KEEP + CONNECTED/
  battery-not-low/storage-not-low constraints + EXPONENTIAL 30s backoff) + `SidrLauncherApp :
  Configuration.Provider`/`HiltWorkerFactory` + manifest `WorkManagerInitializer` removal
  (`tools:node="remove"`) + DI (`ModelProvisionProvidesModule`/`ModelProvisionBindsModule`,
  availability bound in `PersistenceBindsModule`). **Deliberate deviations (documented):** the `@HiltWorker`
  shell lives in `:app` (composition root, already kapt+Hilt) and the port fakes in `:data:ai-local-test`,
  to keep `:data:ai-local` kapt/HTTP-free; the runtime `ensureModel()` trigger is deferred to Block R
  (pairs with classifier consumption; inert under OQ#2). New deps = `androidx.work` 2.10.0 + `androidx.hilt`
  1.2.0 (`hilt-work` + compiler via kapt in `:app`) **only** (context7-verified). `:data:ai-local` gains no
  edge; `:data:ai-cloud` has **no `:data:ai-local` edge** (ports in `:domain`); `ai.onnxruntime` still
  confined to P's two shell files; `:domain` pure. **39 new JVM tests, 0 failures**; full
  `testDebugUnitTest` + `assembleDebug` **BUILD SUCCESSFUL**. **Reworked before close (review P1-1…P2-8):**
  context7-verified WM init pasted in ADR; availability disk cross-check (P1-2); downloader moved to
  `:data:ai-cloud` (P2-4); half-pinned-config `require` (P2-5); `noBackupFilesDir` confirmed (P2-6); retry
  taxonomy (P2-7). **Device/release-pending:** live download + real artifact URL/SHA-256 (OQ#2); **the
  pruned multilingual `vocab.txt` (data-driven ~20–30k, `en/ar/tr/ru`, byte-matched to the exported
  tokenizer) — OQ#1**; `AndroidDeviceProfiler` Android-API
  reads + thermal/battery transitions (SM-A325F). P's three seams now have prod impls →
  `OnnxIntentClassifier` bindable. Details: decisions.md "ADR 2026-06-28 — Block Q complete" + its
  "Rework before close" subsection. P's three seams now have prod impls → `OnnxIntentClassifier`
  bindable (consumed by Block R).
- **Phase 6 Block R ✅ (2026-06-29)** — wire NLU `IntentMatcher` source + docs-sync + **Phase 6 close**.
  `LayeredIntentMatcher : IntentMatcher` + `NluConfidenceCalibrator` (`:data:repository`, port-only, no
  `data→data` edge): **rule-first** — rule returned verbatim when `!isLowConfidence` (NLU never consulted,
  `< 10ms` + Phase-3 parity), NLU consulted only on low confidence; escape (`source==NLU && (conf==0f ||
  UnknownIntent)`) → rule stands; non-escape NLU wins iff calibrated conf clears `suggestThreshold`. **R1
  calibration = conservative band → Suggest (§5.A):** raw softmax remapped into `[suggest 0.50, autoExec
  0.85)` (always Suggests, never auto-executes a model-driven intent); `confidenceFloor` stays in
  `OnnxModelSpec`, calibrator floor a decoupled plain `Float` (0.60). DI (`:app`): `@RuleMatcher`/
  `@NluMatcher` qualifiers + `NluMatcherProvidesModule`; unqualified `IntentMatcher` →
  `LayeredIntentMatcher`; `HandleUserCommandUseCase` untouched. **§5.F deviation (recorded): `@NluMatcher`
  binds the self-gating `OnnxIntentClassifier` unconditionally** (availability flips at runtime → live
  re-check beats a stale graph-time NoOp swap), same `@Singleton` exposed as `@NluMatcher` +
  `SessionLifecycle`. R2.5: `SidrLauncherApp.onTrimMemory(≥TRIM_MEMORY_BACKGROUND)`/`onLowMemory()` →
  `releaseResources()` (`:app` holds only the ONNX-free seam). R3: `ensureModel()` fire-and-forget on
  `@ApplicationScope` (IO) from `onCreate()` (inert under OQ#2). **No-model parity** (shipping state):
  secondary always escapes → identical to rule-only. New JVM tests green (fast-path NLU-never-invoked,
  escape→rule, calibrated answer, no-model parity, calibrator boundaries); `assembleDebug` (Hilt graph
  valid) + full regression green. Two-port invariant intact; `:domain` pure; `ai.onnxruntime` still
  confined to P's two files; no new dep. Details: decisions.md "ADR 2026-06-29 — Block R complete + Phase
  6 close". **Phase 6 CLOSED (Blocks O → R). Next = deferred device-acceptance pass (SM-A325F, gated on
  OQ#1/#2) and/or Phase 7.**

## Hard rules

- `domain` = pure Kotlin (stdlib + coroutines only). No Android, no `core/*`.
- Interfaces in `domain`; implementations in `data/*`. UI holds no business logic.
- No `feature -> feature` deps. Single `NavHost` in `app`. ViewModels emit
  `NavigationEvent`; they never touch `NavHostController`.
- Repository/use-case ops return `OperationResult<T>`; never throw to UI.
- **Understanding belongs to the model. Execution belongs to the deterministic layer.**
  *(ADR "2026-08-19 — ADR 1/4 (agentic restart)"; replaces the former "fast local intent matching runs
  before any LLM call".)*
  1. **FastPath** (deterministic, localized) answers frequent exact commands without a model. It is a
     **latency optimization, NOT a filter on understanding**.
  2. The **learned-plan cache** replays already-understood goal shapes deterministically and offline.
  3. Everything else goes to the **model planner**. A FastPath miss is **no longer** grounds to answer
     "Unknown command".
  4. Nothing the model proposes executes, gains rights, or leaves the device except through
     deterministic gates: `ToolRegistry` → argument validation → preconditions → risk gate / consent
     → loop bounds → egress allow-list → trace.
  5. Router-off / offline / no-key ⇒ FastPath + plan cache + an honest "this needs network".
     Byte-for-byte rule-only parity stays a test-checkable property.
- **Understanding vs. execution, not matching vs. generation.** One contour may both speak and act
  (ADR 4/4 — one `AgentSession`, a 0-step plan *is* a spoken reply); what may never merge is
  **proposing** and **executing**. `GenerativeAiEngine` (→ `Flow<AiChunk>`, transport) stays a
  different port from `IntentMatcher` (→ `IntentMatchResult`) and from the structured `Planner` /
  `CommandPlanner` — those are different *shapes of answer*, and that separation is unaffected.
  LLM-proposed actions **never auto-execute a risky action** (confirmation-gated, Fork R4).
- Launcher core works fully offline; optional permissions never block startup.
- **User-facing text never originates in `domain` — and not in a ViewModel either.** Domain and
  ViewModels emit typed results (`CommandMessage`, `CommandFailure`, `CommandFeedback`); the feature
  layer chooses the string via `sidrString(R.string.…)`. Enforced by `HardcodedUiTextGuardTest` and
  `StringSeamGuardTest`.
- **Strings and all main-locale translations ship in the same commit as the feature.** A block is not
  gate-green until `en`/`ru`/`tr` are complete — enforced by `LocaleCompletenessGuardTest`.

## Contract → Owner module

| Contract / artifact | Owner module |
|---|---|
| Domain models (`InstalledApp`, `LauncherIntent`, `ExecutableAction`, `IntentMatchResult`, `AiChunk`) | `domain` |
| Repository & use-case interfaces (`InstalledAppsRepository`, `HandleUserCommandUseCase`) | `domain` |
| `OperationResult` / `OperationError` | `domain` |
| Ports: `IntentMatcher`, `IntentConfidencePolicy`, `GenerativeAiEngine` | `domain` |
| `ActionExecutor` contract + `ActionExecutionResult` *(Block D)* | `domain` |
| `DeviceProfile`/`DeviceCapability` model + `DeviceProfileProvider` port + `LocalInferenceGate` *(Block O ✅)* | `domain` |
| `ModelId`/`ModelAvailability`/`ModelAvailabilityRepository`/`TextEmbedder` port *(Block O ✅)* | `domain` |
| Rule-based matcher impl, `InstalledAppsRepository` impl, Android `ActionExecutor` impl | `data/repository` |
| `LayeredIntentMatcher` (rule-first composite) + `NluConfidenceCalibrator` *(Block R ✅)* | `data/repository` |
| `OnnxIntentClassifier` (`@NluMatcher` + `SessionLifecycle`) + `OnnxTextEmbedder` *(Block V inert seam)* | `data/ai-local` |
| `@RuleMatcher`/`@NluMatcher` qualifiers + matcher DI swap + `onTrimMemory`/`ensureModel` wiring *(Block R ✅; lifecycle set updated in V)* | `app` |
| Pref domain models (`UserPreferences`, `FeatureFlags`, `DeviceProfileCacheEntry`, `CachedSuggestion`) + their repo interfaces *(Block E ✅)* | `domain` |
| DataStore Preferences impls + `PreferencesMapper` + `PreferencesKeys` *(Block E ✅)* | `data/repository` |
| History domain models (`AppUsageRecord`, `SuggestionRankingRecord`, `IntentMatchRecord`) + repo interfaces (`UsageHistoryRepository`, `SuggestionRankingRepository`, `IntentMatchHistoryRepository`) *(Block F)* | `domain` |
| Room entities, DAOs, `SidrDatabase`, `TypeConverters`, `migrations/`, mappers *(Block F)* | `data/repository` |
| Permission contracts (`PermissionFeature`, `PermissionStatus`, `PermissionChecker`, `PermissionPrefsRepository`) *(Block G)* | `domain` |
| `AndroidPermissionChecker` impl *(Block G)* | `core/android` |
| `PermissionPrefsRepositoryImpl` (DataStore) *(Block G)* | `data/repository` |
| Permission-education UI (`PermissionEducationScreen`/`ViewModel`, rationale, request flow) *(Block G)* | `feature/permission_education` |
| Cloud AI client (Ktor) | `data/ai-cloud` |
| `PromptContextBuilder` + `OutboundContextPolicy` (outbound allow-list/guards) *(Block L)* | `domain` |
| `CommandPlanner` port + `PlanResult` + `ActionProposal` + `ProposalValidator` + `CatalogSchemaRenderer` + `RouteCommandUseCase` *(AIL-4 ✅)* | `domain` |
| `ExecuteActionUseCase` (confirmed `LauncherAction` → resolve → execute → `CommandOutcome`) *(AIL-5 ✅)* | `domain` |
| `LlmCommandPlanner` (`CommandPlanner` impl, non-streaming OpenAI-compatible) *(AIL-4 ✅)* | `data/ai-cloud` |
| `RouterProvidesModule` (`CommandPlanner` + `RouteCommandUseCase` wiring) *(AIL-4 ✅)* | `app` |
| `PendingRoutedAction` state + confirm/cancel VM wiring + confirmation UI dispatch *(AIL-5 ✅)* | `feature/launcher` |
| `ConfirmActionCard` (DF-4 terminal confirm block, presentation-only) *(AIL-5 ✅)* | `core/ui` |
| `SidrAssistantComposer` + `SidrStreamingIndicator` (DS-10 assistant controls, presentation-only) *(DS-10 ✅)* | `core/ui` |
| `AssistantPresentation` (DS-5 error copy + action choice + cloud-disclosure text + provider provenance, pure) *(DS-10 ✅)* | `feature/assistant` |
| `provideExecuteActionUseCase` (`IntentProvidesModule`) *(AIL-5 ✅)* | `app` |
| `sidrString`/`sidrPluralString` + `SidrStringOverlay` (I18N-1 string seam, entry-name-keyed overlay) *(I18N-1 ✅)* | `core/ui` |
| `locales_config.xml` + in-app language switcher (`AppCompatDelegate` per-app language) + i18n guard tests (`StringSeamGuardTest`/`LocaleCompletenessGuardTest`/`HardcodedUiTextGuardTest`/`DomainIdentifierLeakGuardTest`) *(I18N-1 ✅, `DomainIdentifierLeakGuardTest` I18N-2 ✅)* | `app` |
| ONNX NLU / embeddings | `data/ai-local` |
| `ModelDownloader` port + `ModelFilePresence` port *(Block Q ✅, rework)* | `domain` |
| `ModelStore`/`Sha256Verifier`/`ModelProvisioner`/`ModelManager` + `ModelDownloadScheduler` port + `ModelDownloadConfig` *(Block Q ✅)* | `data/ai-local` |
| `AndroidDeviceProfiler` + pure `DeviceProfileClassifier`/`DeviceProfileCacheMapping` *(Block Q ✅)* | `core/android` |
| `ModelAvailabilityRepositoryImpl` (marker + disk cross-check) *(Block Q ✅)* | `data/repository` |
| `KtorModelDownloader` (HTTPS-only, retry taxonomy) *(Block Q ✅, rework)* | `data/ai-cloud` |
| `ModelDownloadWorker` (`@HiltWorker`) / `WorkManagerModelDownloadScheduler` + `Configuration.Provider`/`HiltWorkerFactory` *(Block Q ✅)* | `app` |
| `UiState`, dispatchers, logging contracts | `core/common` |
| `Routes`, `NavigationEvent` | `core/common` *(→ `core/navigation` on trigger)* |
| `DeviceProfile` detection, `PackageManager` access, `SpeechInputSource` Android impl | `core/android` |
| Design system / theme | `core/ui` |
| Test fakes / fixtures | `core/testing` |
| Single `NavHost`, composition root, Hilt graph | `app` |
| Gradle convention plugins | `build-logic` *(planned)* |

## Source of truth

- Architecture & target module structure: [docs/architecture.md](docs/architecture.md)
  **IN SYNC** as of Block H6 (2026-06-23) — real 3-flow `LauncherViewModel`, `UiState.Error(retryable)`,
  per-feature permission education + upgrade-only `refreshStatus()`, Forks 1/2/8/9 reflected.
  `EncryptedSharedPreferences` documented as deprecated/not used (Fork 1 defers secrets to Phase 5).
- Closed checklists *(**code-closed**; "closed" = the code phase, NOT the device debt — Phase 5/6
  device-acceptance items are still open and carried into Phase 7 Tracking, see below)*:
  [ai-context/phase-6-local-nlu-plan.md](ai-context/phase-6-local-nlu-plan.md)
  *(Phase 6 code-closed, Blocks O → R, 2026-06-29; device-pending: Block-P P5 + OQ#1/#2 real model)* ·
  [ai-context/phase-5-plan.md](ai-context/phase-5-plan.md)
  *(Phase 5 code-closed, Blocks I → N, 2026-06-27; device-pending: Block-J `SecretStoreInstrumentedTest`
  + Block-N N5)* · [ai-context/phase-4-plan.md](ai-context/phase-4-plan.md)
  *(Phase 4 closed, Blocks E → H, 2026-06-23)* ·
  [ai-context/phase-3-intent-system-plan.md](ai-context/phase-3-intent-system-plan.md) *(Phase 3 closed 2026-06-21)*
- Decisions log: [ai-context/decisions.md](ai-context/decisions.md)
- Active checklist: [ai-context/phase-7-voice-suggestions-plan.md](ai-context/phase-7-voice-suggestions-plan.md)
  *(Phase 7 — voice input + contextual suggestions, Blocks S → W; forks decided 2026-06-29; **Block S DONE
  2026-06-29** — pure `:domain` suggestion+voice contracts + `HeuristicSuggestionRanker` + fakes; **Block T DONE
  2026-06-30** — `AndroidSpeechInputSource` (`:core:android`) + `VoiceModule` DI + `RECORD_AUDIO` routed-education
  request flow + `refreshStatus()` debt discharged + launcher mic affordance, 9 new JVM tests / 383 JVM total,
  `assembleDebug` + `testDebugUnitTest` green, 0 new deps, `android.speech` confined to `:core:android`, real
  recognizer device-pending OQ#4; **Block U DONE 2026-06-30** — `TimeOfDaySuggestionProvider`/
  `UsageSuggestionProvider` (offline) + `CalendarSuggestionProvider`/`LocationSuggestionProvider` (opt-in,
  `:data:repository`) + `SuggestionEngineImpl` (aggregate→rank→persist, gated by `aiSuggestionsEnabled`) +
  Block-T request-flow template reused verbatim for calendar/location; privacy guard delivered as 4
  executable proofs incl. a reflection-based fix to Block L's `AiRequestGuardTest`; 16 new JVM tests /
  **399 JVM total**, `assembleDebug` + `testDebugUnitTest` + `:domain:test` green, 0 new deps,
  `android.location`/`CalendarContract` confined to `:data:repository`, real device reads device-pending;
  **Block W DONE 2026-07-01** — W-lite + W proper close the shipped launcher surface (single-owner
  suggestions state, cache→fresh supersede, stateless row, periodic precompute/cleanup, boot warmup);
  **Block V remains separate** — ONNX `TextEmbedder` impl + semantic re-rank; OQ#3 embedding model/host
  gates it)*
- Roadmap: [docs/roadmap.md](docs/roadmap.md)

## Do not

- Don't start Phase 5 (cloud AI) ahead of its own approved plan. Phase 4 (E → H) is closed;
  extend the existing persistence/hardening, don't re-scaffold it.
- Don't create `core/data` (dropped from the target structure).
- Don't fold generative AI into the `IntentMatcher` contract.
- Don't re-introduce `EncryptedSharedPreferences` — deprecated; secrets land in Phase 5 via a
  `SecureSecretStore` port (Fork 1).
