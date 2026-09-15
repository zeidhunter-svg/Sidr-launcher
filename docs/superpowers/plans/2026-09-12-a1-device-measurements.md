# A1″ — what Android actually does on the SM-A325F, measured

> **This file is the only admissible source for an Android premise in block A1″.**
> A row may be filled **only** from an observation made on the device. It may not be filled from
> platform documentation, from an API's name, from another project, or from recall — including when
> the documented answer seems obvious. An unmeasured row stays empty and says so.
>
> The rule is not procedural fussiness. A1′ shipped its headline tool **dead** because two inherited
> Android sentences were both false: "`set_timer` needs zero new permissions" was written in eight
> documents and `ActivityTaskManager` refused every invocation. No unit test could see it, because the
> seam was faked. Spec §3.1 exists because of that, and so does this file.

**Device:** SM-A325F (`RF8R705H38F`), Android 13, One UI.
**Probe:** `app/src/androidTest/java/com/sidr/launcher/probe/LauncherAppsProbe.kt` — asserts nothing,
logs verbatim under tag `SIDR_PROBE`. It lives in `:app` rather than a library module on purpose:
"am I the default home" is a property of **the package asking**, and a library module's `androidTest`
runs under its own test package, which would answer for the wrong one. The probe confirms its own
identity first (`context.packageName :: com.sidr.launcher`) so a reading cannot be silently about
something else.

**Probe (Task 13):** `app/src/androidTest/java/com/sidr/launcher/probe/Tier0IntentProbe.kt` — same
shape, same tag, same identity-first rule, one `@Test` per spec §7.2 candidate so a single intent can
be fired and then the device observed. It launches through **production's** shape —
`context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))` on the application context, what
`ContextIntentLauncher` does — and **not** `adb shell am start`: that runs as uid 2000 (`shell`), whose
permission set is not the app's, and row 26 below is the measured proof that the two answers differ.

**How it was run — and how it must be run.** `adb shell am instrument`, against APKs installed by hand
with `adb install -r`. **Never `./gradlew :app:connectedDebugAndroidTest`:** AGP installs both APKs,
runs the tests and then **uninstalls both**. On 2026-09-14 that silently removed the owner's install
mid-measurement, taking its data with it — no `databases/`, runtime permissions reset, and the
Keystore material behind the BYOK key destroyed, which is why rows 1–2 and 6–10 carry different uids
(10735 before, 10752 after). The finding itself is unaffected; the uid change is recorded so the
discrepancy is explained rather than mysterious.

## Measurements

| # | What was called | Device state | Observed result, verbatim | Date | Build |
|---|---|---|---|---|---|
| 1 | `LauncherApps.hasShortcutHostPermission()` | Sidr **not** default home (One UI Home holds `android.app.role.HOME`) | `false` — returned normally, did **not** throw | 2026-09-14 | `5d44167` + probe |
| 2 | `LauncherApps.getShortcuts(ShortcutQuery(FLAG_MATCH_MANIFEST\|FLAG_MATCH_DYNAMIC\|FLAG_MATCH_PINNED), Process.myUserHandle())` | same | **threw** `java.lang.SecurityException: Caller can't access shortcut information` | 2026-09-14 | `5d44167` + probe |
| 3 | `adb shell cmd package set-home-activity com.sidr.launcher/.LauncherActivity` | same | `Error: Failed to set default home.` | 2026-09-14 | — |
| 4 | `adb shell cmd role add-role-holder android.app.role.HOME com.sidr.launcher` | same | `Error: see logcat for details.` / `java.util.concurrent.ExecutionException: java.lang.RuntimeException: Failed` | 2026-09-14 | — |
| 5 | `adb shell dumpsys shortcut` | same | **216** `ShortcutInfo` entries across **73** packages; flags seen include `DynIc-rStr` (dynamic) and `ImManIc-rStr` (immutable manifest); ids masked `***` by the platform | 2026-09-14 | — |
| 6 | `hasShortcutHostPermission()` | Sidr **is** default home | `true` | 2026-09-14 | `5d44167` + probe |
| 7 | `getShortcuts(…)` — total returned | Sidr **is** default home | **205**, all with `isEnabled == true` | 2026-09-14 | `5d44167` + probe |
| 8 | `getShortcuts(…)` — distinct contributing packages | Sidr **is** default home | **65** | 2026-09-14 | `5d44167` + probe |
| 9 | `shortLabel` / `longLabel` populated | Sidr **is** default home | `shortLabel` **205 / 205**; `longLabel` **109 / 205** | 2026-09-14 | `5d44167` + probe |
| 10 | `LauncherApps.startShortcut(pkg, id, null, null, myUserHandle)` on the first enabled shortcut | Sidr **is** default home | `RETURNED_NORMALLY` — the target app opened; no additional permission, no throw | 2026-09-14 | `5d44167` + probe |
| 11 | Label language, observed in the returned set | Sidr **is** default home | mixed and **not** the app's own: `Anında Hesap Aç` (tr), `Подписки` (ru) side by side — each declaring app localises its own labels to the device locale | 2026-09-14 | `5d44167` + probe |
| 12 | `PackageManager.getApplicationInfo(pkg, 0)` for a shortcut-contributing package with no `LAUNCHER` activity | Sidr **is** default home | `<не измерено>` | — | — |
| 13 | `startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(EXTRA_HOUR, 4).putExtra(EXTRA_MINUTES, 37).putExtra(EXTRA_MESSAGE, "SIDR PROBE").putExtra(EXTRA_SKIP_UI, false).addFlags(FLAG_ACTIVITY_NEW_TASK))` from `com.sidr.launcher`, uid **10752** | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; **no** `Permission Denial` line, **no** exception. **What it does:** focus → `com.sec.android.app.clockpackage/.alarm.activity.AlarmWidgetListActivity`; the clock's own list showed a **new, already-enabled** alarm «SIDR PROBE / 04:37 / ср, 16 сент.», toggle **on**, header «Будильник через 6 ч. 3 мин.» and toast «Будильник сработает через 6 ч и 3 мин.» — no editor, no confirm button. **It performs the act before any further input.** Alarm created by this probe and **deleted through the clock UI** immediately after; the list returned to the two pre-existing alarms (05:20, 17:40, both off) | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 14 | `startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.sec.android.app.clockpackage/.alarm.activity.AlarmWidgetListActivity`, header «Все будильники отключены» and the device's two existing alarms listed, both off — **opens a screen**; state unchanged | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 15 | `startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", pkg, null)))`, run twice: `pkg = com.sidr.launcher`, then `pkg = com.sidr.launcher.data.ailocal.test` (a package with **no** launcher activity, i.e. not covered by the app's `<queries>` MAIN/LAUNCHER entry) | Sidr installed, not default home | **Permission:** both runs `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** both → `com.android.settings/.applications.InstalledAppDetails`; run 1 titled «Sidr Launcher», run 2 titled `com.sidr.launcher.data.ailocal.test` with the generic icon — **opens a screen**, and the no-launcher-activity target was **not** filtered out. Both landed screens carry an «Удалить» (uninstall) button, so the screen this tool opens is one tap from an irreversible act | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 16 | `startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", "com.sidr.launcher.data.ailocal.test", null)))`, same shape and caller | Sidr installed, not default home | **REFUSED, and the refusal names a permission.** `startActivity` `RETURNED_NORMALLY` — **no exception of any kind** — and `grep -i 'Permission Denial'` found **nothing**. The refusal is one line from the responder: `E UninstallerActivity: Uid 10752 does not have android.permission.REQUEST_DELETE_PACKAGES or android.permission.DELETE_PACKAGES`. `ActivityTaskManager` did `START u0 {act=android.intent.action.DELETE … cmp=com.google.android.packageinstaller/com.android.packageinstaller.UninstallerActivity} from uid 10752`; the activity was resumed at 22:36:26.052 and its surface `Destroyed` at 22:36:26.233 — **~180 ms, nothing drawn**. **What it does: nothing, silently.** No dialog, no toast, no result. `android.permission.REQUEST_DELETE_PACKAGES` is **not** in `app/src/main/AndroidManifest.xml` (grep: 0 hits) and not in the app's granted set; `pm list permissions -f` on this device reports it `protectionLevel:normal`. Verified after the run: `pm list packages` still lists `com.sidr.launcher.data.ailocal.test` and all four `sidr` packages | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 17 | `startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception — and the app holds **no** `CAMERA` permission, so none is needed to launch the camera app. **What it does:** focus → `com.sec.android.app.camera/.Camera`, live viewfinder in «ФОТОГРАФИЯ» mode with the shutter button awaiting the user — **opens a screen**; no capture. Side effect worth naming: the camera sensor is live with no user tap | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 18 | `startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.android.settings/com.samsung.android.settings.wifi.WifiWarning` — the Wi-Fi screen («Wi-Fi», «Выключено») **with a modal already raised**: «Отключить мобильную точку доступа? … Отмена / OK». **Opens a screen — but the screen it opens was a modal that changes device state on one tap**, because this phone had the mobile hotspot on. Dismissed with `KEYCODE_BACK` (= Отмена); hotspot left on, host connectivity re-checked `200` afterwards | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 19 | `startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception — and the app holds no `BLUETOOTH*` permission. **What it does:** focus → `com.android.settings/.Settings$BluetoothSettingsActivity`, «Bluetooth / Выключено» — **opens a screen**; state unchanged | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 20 | `startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.samsung.android.lool/com.samsung.android.sm.battery.ui.graph.PowerUsageSummary` — «Действия с аккумулятором», charge-level and consumption graphs. **Opens a screen.** Note the responder is **Samsung Device Care, not `com.android.settings`** | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 21 | `startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.android.settings/.Settings$DataUsageSummaryActivity`, «Использование данных», 9,46 ГБ for 1–30 сент. — **opens a screen**; the landed screen carries a «Мобильные данные» toggle (on) | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 22 | `startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.android.settings/.Settings$DisplaySettingsActivity`, «Дисплей» with the light/dark selector, brightness slider — **opens a screen**; state unchanged | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 23 | `startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.android.settings/.Settings$SoundSettingsActivity`, «Звуки и вибрация» (mode «Вибрация» selected) — **opens a screen**; state unchanged | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 24 | `startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))`, same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception — but the app **declares `android.permission.ACCESS_FINE_LOCATION`** (`AndroidManifest.xml:15`, for the prayer feature), so this run shows a location permission is **sufficient**, not that none is needed; whether one is *required* to open this screen is **unmeasured**, exactly as row 27 leaves `SET_ALARM`. Unlike rows 17 and 19, where the app holds no `CAMERA` and no `BLUETOOTH*` at all, the negative cannot be read off this row. **What it does:** focus → `com.android.settings/.Settings$LocationSettingsActivity`, «Локация / Включено» plus recent-access list — **opens a screen**; state unchanged | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 25 | `startActivity(Intent("android.settings.NOTIFICATION_SETTINGS"))` — **the action is a string literal because there is no such public constant**: `javap` over `platforms/android-37.0/android.jar` finds no `Settings.ACTION_NOTIFICATION_SETTINGS` field (`ACTION_APP_NOTIFICATION_SETTINGS` and `ACTION_NOTIFICATION_LISTENER_SETTINGS` are present, the bare one is not). Same shape and caller | Sidr installed, not default home | **Permission:** `RETURNED_NORMALLY`; no `Permission Denial`, no exception. **What it does:** focus → `com.android.settings/.Settings$ConfigureNotificationSettingsActivity`, «Уведомления» — **opens a screen**; state unchanged. So the screen is reachable, but only through a hardcoded action string | 2026-09-15 | installed 2026-09-14 debug + `Tier0IntentProbe` (`0c8faa0`) |
| 26 | **Same intent from uid 2000 `shell`, for comparison — never the app-facing answer** (controller ruling R13-1): `adb shell am start -a android.intent.action.DELETE -d package:com.sidr.launcher.data.ailocal.test` | same | **Opposite outcome to row 16.** `START … from uid 2000`, the activity **stayed** and drew the OS's own dialog: «com.sidr.launcher.data.ailocal.test / Удалить приложение? / Отмена / OK». Cancelled with `KEYCODE_BACK`; `pm list packages` verified all four `sidr` packages still installed. **This row is why row 16 had to be measured in-process:** taken from the shell, `uninstall_app` would have been recorded as «the OS confirms» and shipped dead, exactly as A1′'s `set_timer` did | 2026-09-15 | — |
| 27 | Whether `com.android.alarm.permission.SET_ALARM` is **required** for `ACTION_SET_ALARM` (row 13 succeeded **with** it already declared and granted) | — | `<не измерено>` | — | — |
| 28 | Whether the OS's uninstall dialog appears **for the app** once `android.permission.REQUEST_DELETE_PACKAGES` is declared (row 16's refusal removed) | — | `<не измерено>` | — | — |
| 29 | **Re-measurement of four of rows 13–26 on a freshly built-and-installed HEAD build** (controller ruling R13-4, because rows 13–26 were taken against the `5d44167` APK installed on 2026-09-14 and "the two builds are equivalent" was an *argument*). `:app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks` at `ab2d063` (`336 actionable tasks: 336 executed`), then `adb install -r` of both → `Success` / `Success`. Four methods re-run: `uninstallApp`, `setAlarm`, `showAlarms` (its action **is** in the `<queries>` block), `openLocationSettings` (its action is **not**) | Sidr installed **and now default home** (the owner flipped the role by hand between the two rounds — see below) | **No divergence in any behaviour cell.** `uninstallApp`: `RETURNED_NORMALLY`, no exception, `grep -i 'Permission Denial'` → nothing, refusal again only as `E UninstallerActivity: Uid 10752 does not have android.permission.REQUEST_DELETE_PACKAGES or android.permission.DELETE_PACKAGES`, nothing drawn. `setAlarm`: `RETURNED_NORMALLY`, no refusal, the clock's list again showed «SIDR PROBE / 04:37 / ср, 16 сент.» **toggle on** with header «Будильник через 2 ч. 55 мин.» and toast «Будильник сработает через 2 ч и 55 мин.» — it **acts**; alarm deleted through the clock UI immediately after, list back to the owner's 05:20/17:40 both off, `dumpsys alarm` greps 0 for `SIDR PROBE`. `showAlarms`: `RETURNED_NORMALLY`, no refusal, focus `com.sec.android.app.clockpackage/.alarm.activity.AlarmWidgetListActivity`, «Все будильники отключены». `openLocationSettings`: `RETURNED_NORMALLY`, no refusal, focus `com.android.settings/.Settings$LocationSettingsActivity`, «Локация / Включено». Identity on all four runs: `com.sidr.launcher` / uid `10752`. Post-install state: uid **still 10752**, `lastUpdateTime` 2026-09-16 01:40:08, `databases/` **intact** (`sidr_history.db`, `-shm`, `-wal`), `SET_ALARM` still declared and `granted=true`, `REQUEST_DELETE_PACKAGES` still absent, HOME role still `com.sidr.launcher/.LauncherActivity` | 2026-09-16 | `ab2d063`, built and installed this round |
| 30 | `dumpsys package com.sidr.launcher`, section `runtime permissions:` — read because row 24 deliberately claims only that `ACCESS_FINE_LOCATION` is *declared* | same | **`android.permission.ACCESS_FINE_LOCATION: granted=false`** and **`android.permission.ACCESS_COARSE_LOCATION: granted=false`** (both `USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED`); `READ_CALENDAR` and `RECORD_AUDIO` also `granted=false`. Read **at the moment** row 29's `openLocationSettings` was fired, so it is the grant state that run actually had. So `ACTION_LOCATION_SOURCE_SETTINGS` opened its screen while the app held **no granted location permission** — the *declaration* was present, the *grant* was not. Whether any permission is **required** for that screen is still unmeasured: this removes the grant from the set of possible explanations, not the declaration | 2026-09-16 | `ab2d063` |
| 31 | Observation about the **instrument**, recorded so a later round is not confused by it: what `am instrument` does to the launcher, and where focus lands afterwards | Sidr is default home | `am instrument` **force-stops the app first** — `I ActivityManager: Force stopping com.sidr.launcher appid=10752 user=0: start instr`, `Killing … (adj 0): stop com.sidr.launcher due to start instr` — then the system relaunches it as home (`START … cat=[HOME] cmp=com.sidr.launcher/.LauncherActivity from uid 0`, `MARsPolicyManager: Current Home Package com.sidr.launcher Resumed`). So **every probe run restarts the launcher process**, and no probe reading says anything about a long-lived Sidr process. Second half: when the started activity finishes, focus returned to **One UI Home's stale home task** (`t11242`) rather than to Sidr, while the platform's own line read `I ActivityUtils: HomePackage : com.sidr.launcher, resumePackageName : com.sec.android.app.launcher` — i.e. the **role** is Sidr's and the **resumed task** was One UI's. A `dumpsys window` focus reading taken after an activity finishes therefore does **not** measure who holds the HOME role; `cmd package resolve-activity -a MAIN -c HOME` and that `HomePackage :` line do | 2026-09-16 | `ab2d063` |

**Row 29 replaces an argument with an observation, and one input genuinely did change.** Rows 13–26
were measured against the APK installed on 2026-09-14; the case that it and HEAD were equivalent rested
on an empty `git diff` over the manifests and no SDK-level drift, which this file's preamble does not
accept as a source. Four rows were therefore re-run on a build made and installed this round. Two
things are worth stating precisely rather than glossing:

- **What agreed, agreed in the behaviour cells** — refusal/no-refusal, exception/no-exception, what the
  device did, which activity took focus. One incidental number differs: `uninstall_app`'s uninstaller
  activity lived `192 ms` from resumed (01:41:13.818) to surface `Destroyed` (01:41:14.010) where row 16
  read `181 ms`. That is a timing incidental on a different run of the same refusal, not a different
  answer, and it is written down rather than rounded into agreement.
- **The device state was not identical, and that is a confound named rather than hidden.** Rows 13–26
  were taken with One UI Home holding `android.app.role.HOME`; row 29 was taken after the owner had
  flipped the role to Sidr by hand. So the re-measure varies **two** inputs (build *and* home role), and
  its strict content is «with both changed, nothing in these four rows moved» — not «the build makes no
  difference, holding the role fixed». For these four intents that is enough, because the HOME role is
  not plausibly upstream of `startActivity` permission checks; it is *not* enough to generalise to any
  row whose outcome could depend on being home.

**A third observation makes the build half of that comparison unusually strong, and it is a measured
one:** `app-debug.apk` rebuilt from `ab2d063` with `--rerun-tasks` has sha256
`9b7b5a4e3e5ff47340cfe11ac78d1de8d4f6d9d4e5bca3a45eb459a60939d44a`, **byte-identical** to the APK that
was on disk from the Task 13 round. The app APK does not differ between the two trees at all — not
«differs only in ways that cannot matter», but the same bytes. (The `androidTest` APK is *not*
byte-identical, so the property is about the app APK, which is the one whose manifest and permissions
every row above depends on.)

**Row 12 is deliberately empty, per this file's own rule that an unmeasured row stays empty and says
so.** The reasoning around it splits into two pieces with very different standing, and blurring them
together is exactly the failure mode this file exists to prevent.

**Inference, from this file's own rows:** rows 7 and 8 record **205 shortcuts from 65 distinct
packages** returned through `getShortcuts`, while `AndroidManifest.xml`'s `<queries>` block declares
only a MAIN/LAUNCHER intent — nowhere near 65 packages' worth of visibility on that declaration alone.
That gap is what grounds "`LauncherApps` is not filtered for the HOME-role holder the way an ordinary
caller is" as an **inference from rows 7/8**, not as a fact read anywhere else.

**Unmeasured:** nothing in this file called `PackageManager` at all. Whether it carries the same
exemption, and whether the `<queries>` block would otherwise cover every package that publishes a
shortcut, is not observed here — asserting either would be recall or platform-documentation reasoning,
exactly the kind the preamble rules out, so it is left unmeasured rather than asserted. This asymmetry
— one half grounded in this file's own measured rows, the other resting on nothing measured here — is
*why* row 12 exists as an open row, not a footnote to it.

No device was attached this session (`adb devices -l` empty) to observe whether `getApplicationInfo`
throws `NameNotFoundException` for a shortcut-contributing package with no `LAUNCHER` activity, so the
row is left empty rather than filled from either platform documentation or the `<queries>` declaration's
apparent coverage. What depends on it: a shortcut tool's `appLabel` (`AndroidShortcutQuery`, which falls
back to the raw package name on that exception), and, through it, the user-visible qualifier Task 7
renders. Addressed to the next device round with `adb` access.

**Row 5 was a bound, and it behaved like one.** `dumpsys` reported 216 across 73 packages where
`getShortcuts` returns 205 across 65 — close, but not equal, because the service holds state the
three-flag query does not return. It was never used to fill rows 7 or 8, and rows 7 and 8 are what
Task 12 measures against.

**Rows 3 and 4 stood until the owner flipped the role by hand.** On this One UI build the HOME role
cannot be given to a third-party launcher from the shell by either path. The reverse **is** permitted:
`cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity` succeeded and
restored One UI afterwards. Asymmetric, and worth knowing before planning any future device round.

## What the measurements decide

**1. What the adapter must do when Sidr is not the default home.** Spec §5.3 requires degrading to an
empty tool set — never a crash, never a surface offering a tool it cannot run. Row 2 gives the shape of
that precisely: `getShortcuts` **throws `SecurityException`**; it does **not** return an empty list or
`null`. An adapter written as `getShortcuts(...).orEmpty()` would therefore crash the launcher at
exactly the moment most users meet it — Sidr installed and not yet chosen as home, which is every
user's first run.

Rows 1 and 6 give the better-shaped mechanism: `hasShortcutHostPermission()` answers `false`/`true`
across the same transition **without throwing**. Gate on it and return an empty list. The
`SecurityException` catch is **not** thereby removable: the role can change between the check and the
call, so the catch stays as the fail-closed backstop rather than the primary mechanism.

**2. Whether a manifest permission is needed, and whether Task 2's `toolPermissions` map needs a row.**
**No permission, and no row — but not because nothing is required.** Shortcut host access is not
governed by a manifest permission at all: it is the `android.app.role.HOME` role, held by exactly one
package at a time and assigned by the user. Rows 3, 4 and 6 are the evidence — no permission grant
moved the answer, and the role flip moved it completely. So `toolPermissions` gains no row for shortcut
tools, and **this is the first tool source in the project whose availability is a runtime role rather
than an install-time permission.** Task 2's guard answers "does every intent a scanned worker issues
have a declared permission"; it cannot answer "is this source available", and nothing should be added
to it that pretends otherwise. Row 10 confirms the role is sufficient: `startShortcut` needed nothing
`getShortcuts` did not.

**3. Roughly how many descriptors the registry will carry** — the figure Task 12 measures against.
**205 shortcut descriptors from 65 packages, on this device, today.** Two consequences:

- It **contradicts a number already written into the tree**: Task 4's `snapshot()` KDoc states its cost
  "over a registry of ~150 descriptors". 205 is shortcuts **alone**, before the `in_app` and
  `system_intent` adapters. The KDoc is corrected in the same commit as this file. Per-call derivation
  over 205+ descriptors is the thing Task 12 must actually measure, not assume.
- It is **one device's number, not a constant.** It depends on which apps are installed and how many
  shortcuts each declares. Task 6's `ShortcutCatalog` must not treat any count as fixed, and Task 2's
  non-vacuity floor was already re-spelled away from a count for this reason — that decision is now
  measured rather than anticipated.

**4. Which label field the adapter may rely on.** `shortLabel` is populated for **all 205**;
`longLabel` for **109**, barely half. `longLabel` is therefore optional and `shortLabel` is the only
field a descriptor may be built from without a fallback.

**5. Labels arrive already localised, by someone else.** Row 11: the returned set mixes Turkish and
Russian labels in one list, because each declaring app localises its own shortcuts to the device
locale. A shortcut tool's user-visible name is therefore **not** ours to translate and does not pass
through `sidrString`, and the block's `en`/`ru`/`tr` `ToolVocabulary` cannot cover shortcut names the
way it covers `set_timer`. Task 7 and Task 10 both need this; it was not anticipated by the spec, and
it is recorded here rather than discovered later.

---

## Task 13 — spec §7.2's candidate set, measured

Rows 13–26 above. Reproduced here in §7.2's own shape, because that is the table Phase 3's plan is
written from (controller ruling R13-2: the measurement table keeps this file's five-column form; §7.2's
form is reproduced here). Both right-hand columns are now filled from rows 13–25 and **nothing in them
is inherited** — no cell was taken from platform documentation, from an API's name, or from recall.

| Proposed id | Intent | Arg | Permission — MEASURED | What it actually does — MEASURED | Ships in Phase 3 |
|---|---|---|---|---|---|
| `set_alarm` | `AlarmClock.ACTION_SET_ALARM` | clock time | **None beyond the app's current set.** Returned normally, no refusal; `SET_ALARM` is already declared and granted, and whether it is *required* is row 27 — unmeasured | **Creates the alarm, already enabled.** No prefilled form, no confirm button; the clock opened on its list showing the armed alarm | **Yes** — but see the §7.2 contradiction below |
| `show_alarms` | `AlarmClock.ACTION_SHOW_ALARMS` | — | None beyond the app's current set; no refusal | Opens a screen (the clock's alarm list); nothing changes | **Yes** |
| `open_app_info` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` | package | None beyond the app's current set; no refusal, for a launchable target **and** for one with no launcher activity | Opens a screen (`InstalledAppDetails`); the screen carries an «Удалить» button | **Yes** |
| `uninstall_app` | `Intent.ACTION_DELETE` (package URI) | package | **REFUSED. `android.permission.REQUEST_DELETE_PACKAGES`** (or `DELETE_PACKAGES`), named by the responder, `protectionLevel:normal` on this device, **not declared** by the app | **Nothing, silently.** No dialog, no exception, no result — the uninstaller activity started and died in ~180 ms without drawing | **No** — blocked on an undeclared permission |
| `open_camera` | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | — | None beyond the app's current set; no refusal, and the app holds no `CAMERA` permission | Opens a screen (Samsung Camera, live viewfinder, shutter awaiting the user); no capture | **Yes** |
| `open_wifi_settings` | `Settings.ACTION_WIFI_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen — **which on this phone came up with a state-changing modal already raised** («Отключить мобильную точку доступа?») | **Yes**, with the observation named |
| `open_bluetooth_settings` | `Settings.ACTION_BLUETOOTH_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_battery_settings` | `Intent.ACTION_POWER_USAGE_SUMMARY` | — | None beyond the app's current set; no refusal | Opens a screen — served by **`com.samsung.android.lool`**, not `com.android.settings` | **Yes** |
| `open_data_usage_settings` | `Settings.ACTION_DATA_USAGE_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; carries a «Мобильные данные» toggle | **Yes** |
| `open_display_settings` | `Settings.ACTION_DISPLAY_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_sound_settings` | `Settings.ACTION_SOUND_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_location_settings` | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` | — | No refusal — but the app **declares `ACCESS_FINE_LOCATION`**, so this shows a location permission is *sufficient*, not that none is needed; whether one is **required** is unmeasured (row 24). Row 30 narrows it: at the re-measure both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` read `granted=false` and the screen still opened, so the *grant* is ruled out as the explanation and the *declaration* is not | Opens a screen; nothing changes | **Yes** |
| `open_notification_settings` | `Settings.ACTION_NOTIFICATION_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen — **but only via the literal action string**: there is no such public constant on `compileSdk 37` | **Yes**, with the constant caveat |

**Twelve of thirteen survive; one does not.**

### Where the measurement contradicts §7.2's own proposal

**1. `set_alarm` — «does it create the alarm, or open a prefilled form?» is answered *it creates it*,
and that is `B4`'s false half measured a third time.** Row 13: with `EXTRA_SKIP_UI = false` the Samsung
clock opened on its **list**, with the alarm already present and already **on**, and announced when it
would fire. This is the `set_timer` shape of 2026-09-05 exactly — the flag governs whether the
responding app shows its UI, not whether it acts — so Master Plan §3.6 `B4`'s «the "prefilled but not
sent" form yields consent from the OS» is now false for the second intent as well as the first. Nothing
in `ACTION_SET_ALARM` yields consent from the OS. A Phase 3 `set_alarm` descriptor therefore cannot
borrow its risk level from the *form* argument; the only justification available to it is the one the
owner already accepted for `set_timer` (reversible, immediately visible, provenance disclosed, nothing
leaves the device), and that is an owner-level reading of the same evidence rather than something this
measurement decides. It is named here so it cannot be «simplified» into a form argument later.

**2. `uninstall_app` — «does the OS confirm, or does it delete?» is answered *neither*.** Row 16: the
app is refused before any dialog exists. §7.2 offered two branches and the device took a third, and the
third is the dangerous one, for a reason that is about the engine rather than about this tool:
**`startActivity` returned normally and threw nothing.** A worker built on the shipped seam
(`Tier0IntentToolWorker.launch`, whose only failure signals are `ActivityNotFoundException` and
`SecurityException`) would answer `ToolResult.Effected()` for an invocation that did nothing, and
`AgentExecutor` would write `ToolObserved(Effected)` into a trace that `DOC-ILM-3` requires to be 1:1
with reality. That is strictly worse than A1′'s `set_timer`, which at least failed loudly enough for
`ActivityTaskManager` to log a refusal. **Phase 3 must not ship `uninstall_app` on the current seam**,
and the two things it needs — the declared permission and a way for the worker to learn that the
responder refused — are both design work, not a manifest line.

**`uninstall_app` keeps `CONFIRM` regardless**, in §7.3's own terms: the gate's job is that **Sidr**
does not initiate an irreversible act without the user, so a second confirmation from the OS would not
be a defect — and here there is no OS confirmation at all for the app, which makes the gate the *only*
consent in the path rather than a redundant one. The level is not weakened by this measurement; it is
the one thing about the row that the measurement strengthens.

**3. `open_notification_settings` — the intent named in §7.2 does not exist as a constant.**
`Settings.ACTION_NOTIFICATION_SETTINGS` is absent from `platforms/android-37.0/android.jar`
(`javap`); only the literal `"android.settings.NOTIFICATION_SETTINGS"` reaches the screen, and it does
(row 25). Shippable, but a Phase 3 tool for it must hardcode a string the SDK does not vouch for, and
that is a different kind of premise from the other eleven.

### Counting honestly: acting versus navigating

Spec §7.2's floor is **eight new authored tools, of which at least four act**. Measured:

- **Twelve** candidates survive the permission half — comfortably over the count floor of eight.
- **One** of them **acts**: `set_alarm`. The other eleven **navigate** — they open a screen and change
  nothing.
- `uninstall_app` would have been the second acting tool and is refused. **Even if its permission were
  declared, §7.2's candidate set contains at most two acting tools**, because ten of the thirteen are
  settings or information screens by construction.

**The floor is not reached, and this is a finding for the controller and the owner rather than
something to make up.** The count half is met (12 ≥ 8); the **«at least four act» half is not, and
cannot be met by this candidate set at all** — not by measurement error, but because the set was chosen
by §7.1's rule (an argument that is a bounded token or absent), and the intents that satisfy that rule
on Android are overwhelmingly navigational. §7.4 already excluded the acting, free-text ones
(`SENDTO`, calendar `INSERT`, `geo:`) with a measured reason. So the gap is structural: A1″ cannot
reach «four acting tools» out of §7.2 without either re-opening §7.4 (raw-span capture plus two owner
decisions) or counting acting tools from `B2`'s shortcut adapter, which is a different section of the
spec. Deciding which is the controller's, not this file's.

### The two empty rows, and what depends on them

**Row 27 — is `com.android.alarm.permission.SET_ALARM` actually required by `ACTION_SET_ALARM`?**
Not measured. Row 13 succeeded on a build that already declares and is granted it, which shows the
permission is *sufficient*, not that it is *necessary*. The negative test means running the same intent
from a build without the permission, i.e. reinstalling the app package — forbidden this session
(2026-09-14 destroyed the owner's data and Keystore material that way). **What depends on it:** whether
a Phase 3 `set_alarm` row belongs in the `toolPermissions` map that Task 2's guard checks. Until it is
measured, treating `SET_ALARM` as required is the fail-safe reading — the permission is already
declared, so nothing has to change for `set_alarm` to work — but «required» must not be *written down*
as measured, which is how A1′ acquired its false sentence in the first place.

**Row 28 — does the uninstall dialog appear for the app once `REQUEST_DELETE_PACKAGES` is declared?**
Not measured. It needs a manifest edit and a reinstall of the app package, both outside this task
(Task 13 touches two files and no production code). Row 26 shows the dialog exists and is reachable
**from uid 2000**; it does **not** show what `com.sidr.launcher` gets once permitted, and assuming the
two are the same is the inherited-premise move this file forbids. **What depends on it:** whether
`uninstall_app` is a Phase 3 row at all, and whether its worker can observe a refusal.

### Two findings about the instrument itself

**1. `grep -i 'Permission Denial'` is not sufficient, and on the one refused candidate it returned
nothing.** The brief's Step 1 proposes exactly that grep as the permission half's instrument. On row 16
the framework printed **no** `Permission Denial` line; the refusal existed only as the responder's own
`E UninstallerActivity: Uid 10752 does not have …`. A reading that trusted the grep alone would have
recorded `uninstall_app` as permission-clean. Whatever runs the next device round must read the
**whole** log around the invocation, not one string.

**2. The app's `<queries>` block did not gate any of these launches.** It declares MAIN/LAUNCHER, two
speech actions, `SHOW_ALARMS` and `STILL_IMAGE_CAMERA`. **Counting per declared action — the reading
used here, because it is what the block literally states — exactly two candidates are covered
(`show_alarms`, `open_camera`) and the other eleven are not.** All eleven nevertheless resolved:
`ActivityTaskManager` logged `START … from uid 10752` for every one, `uninstall_app` included, and ten
of the eleven went on to draw their screen — the eleventh finished itself for a **permission** reason
(row 16), not a visibility one. Row 15's second target, a package with no launcher activity at all, was
not filtered either. So on this device package-visibility filtering is **not** a barrier to
`startActivity` for these intents.

A **per-package** reading would give a different number — the `SHOW_ALARMS` query makes the clock
package visible, so `ACTION_SET_ALARM` to that same package would not be gated — but that reading rests
on which of the responding packages are launcher-visible, and this session measured none of that. It is
named, not used. Either way the conclusion is unchanged and, on the per-action count, understated: it
says nothing about `PackageManager` queries, so row 12 is still the open row for that and nothing here
fills it.
