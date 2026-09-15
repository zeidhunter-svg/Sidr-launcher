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
| 27 | Whether `com.android.alarm.permission.SET_ALARM` is **required** for `ACTION_SET_ALARM` — **measured in the required (negative) direction**: the one `<uses-permission>` line was removed from `app/src/main/AndroidManifest.xml`, `:app:assembleDebug` rebuilt, `adb install -r`'d (the merged manifest's only remaining `SET_ALARM` string is inside an XML comment; `dumpsys package` listed **no** `SET_ALARM` in the app's declared set), and the same `setAlarm` probe method re-run | Sidr installed, **is** default home; build **without** `SET_ALARM` | **REQUIRED. The refusal is loud, and it is loud in both channels at once.** `startActivity` **threw**: `java.lang.SecurityException: Permission Denial: starting Intent { act=android.intent.action.SET_ALARM flg=0x10000000 cmp=com.sec.android.app.clockpackage/.alarm.activity.AlarmCTSHandleActivity (has extras) } from ProcessRecord{c3f62fa 31823:com.sidr.launcher/u0a752} (pid=31823, uid=10752) requires com.android.alarm.permission.SET_ALARM`, and the framework **also** printed `W ActivityTaskManager: Permission Denial: …requires com.android.alarm.permission.SET_ALARM` — so on this intent `grep -i 'Permission Denial'` *would* have worked (contrast row 16, where it found nothing). **Nothing happened on the device:** no alarm created (`dumpsys alarm` greps 0 for `SIDR PROBE`), the clock's list still «Все будильники отключены» with only the owner's 05:20/17:40, both off; focus never left the home screen. Reverted immediately: manifest restored (`git status` clean, byte-identical to HEAD's), rebuilt, `install -r`'d, and `dumpsys package` confirms `com.android.alarm.permission.SET_ALARM: granted=true` again; uid **10752** and `databases/` intact across all three installs | 2026-09-16 | `ab2d063` with the one line removed, then `ab2d063` restored |
| 28 | Whether the OS's uninstall dialog appears **for the app** once `android.permission.REQUEST_DELETE_PACKAGES` is declared — **measured**: the line was added to `app/src/main/AndroidManifest.xml`, `:app:assembleDebug` rebuilt, `install -r`'d (`dumpsys package`: `android.permission.REQUEST_DELETE_PACKAGES: granted=true`, install-time, no prompt), and the same `uninstallApp` probe method re-run against `com.sidr.launcher.data.ailocal.test` | Sidr installed, **is** default home; build **with** `REQUEST_DELETE_PACKAGES` | **YES — the dialog appears, and it is the same dialog uid 2000 got in row 26.** `startActivity` `RETURNED_NORMALLY`; **no** `E UninstallerActivity` refusal line, **no** `Permission Denial`, nothing in the wider refusal grep. The uninstaller this time **stayed and took focus** — `mCurrentFocus=Window{ca129eb u0 com.google.android.packageinstaller/com.android.packageinstaller.UninstallerActivity}` — and drew «com.sidr.launcher.data.ailocal.test / Удалить приложение? / Отмена / OK» (screenshot read, not merely captured). **Not confirmed, per R13-3:** dismissed with `KEYCODE_BACK`; `pm list packages` after still lists all four `sidr` packages, and `dumpsys package com.sidr.launcher.data.ailocal.test` still reports its original `firstInstallTime=2026-07-01 21:39:21`, so nothing was removed. Reverted immediately: manifest restored (`git status` clean), rebuilt, `install -r`'d, `dumpsys package` confirms `REQUEST_DELETE_PACKAGES` **absent** again and `SET_ALARM` still `granted=true`; uid **10752**, `databases/` intact | 2026-09-16 | `ab2d063` + one `<uses-permission>` line, then `ab2d063` restored |
| 29 | **Re-measurement of four of rows 13–26 on a freshly built-and-installed HEAD build** (controller ruling R13-4, because rows 13–26 were taken against the `5d44167` APK installed on 2026-09-14 and "the two builds are equivalent" was an *argument*). `:app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks` at `ab2d063` (`336 actionable tasks: 336 executed`), then `adb install -r` of both → `Success` / `Success`. Four methods re-run: `uninstallApp`, `setAlarm`, `showAlarms` (its action **is** in the `<queries>` block), `openLocationSettings` (its action is **not**) | Sidr installed **and now default home** (the owner flipped the role by hand between the two rounds — see below) | **No divergence in any behaviour cell.** `uninstallApp`: `RETURNED_NORMALLY`, no exception, `grep -i 'Permission Denial'` → nothing, refusal again only as `E UninstallerActivity: Uid 10752 does not have android.permission.REQUEST_DELETE_PACKAGES or android.permission.DELETE_PACKAGES`, nothing drawn. `setAlarm`: `RETURNED_NORMALLY`, no refusal, the clock's list again showed «SIDR PROBE / 04:37 / ср, 16 сент.» **toggle on** with header «Будильник через 2 ч. 55 мин.» and toast «Будильник сработает через 2 ч и 55 мин.» — it **acts**; alarm deleted through the clock UI immediately after, list back to the owner's 05:20/17:40 both off, `dumpsys alarm` greps 0 for `SIDR PROBE`. `showAlarms`: `RETURNED_NORMALLY`, no refusal, focus `com.sec.android.app.clockpackage/.alarm.activity.AlarmWidgetListActivity`, «Все будильники отключены». `openLocationSettings`: `RETURNED_NORMALLY`, no refusal, focus `com.android.settings/.Settings$LocationSettingsActivity`, «Локация / Включено». Identity on all four runs: `com.sidr.launcher` / uid `10752`. Post-install state: uid **still 10752**, `lastUpdateTime` 2026-09-16 01:40:08, `databases/` **intact** (`sidr_history.db`, `-shm`, `-wal`), `SET_ALARM` still declared and `granted=true`, `REQUEST_DELETE_PACKAGES` still absent, HOME role still `com.sidr.launcher/.LauncherActivity` | 2026-09-16 | `ab2d063`, built and installed this round |
| 30 | `dumpsys package com.sidr.launcher`, section `runtime permissions:` — read because row 24 deliberately claims only that `ACCESS_FINE_LOCATION` is *declared* | same | **`android.permission.ACCESS_FINE_LOCATION: granted=false`** and **`android.permission.ACCESS_COARSE_LOCATION: granted=false`** (both `USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED`); `READ_CALENDAR` and `RECORD_AUDIO` also `granted=false`. Read **at the moment** row 29's `openLocationSettings` was fired, so it is the grant state that run actually had. So `ACTION_LOCATION_SOURCE_SETTINGS` opened its screen while the app held **no granted location permission** — the *declaration* was present, the *grant* was not. Whether any permission is **required** for that screen is still unmeasured: this removes the grant from the set of possible explanations, not the declaration | 2026-09-16 | `ab2d063` |
| 31 | Observation about the **instrument**, recorded so a later round is not confused by it: what `am instrument` does to the launcher, and where focus lands afterwards | Sidr is default home | `am instrument` **force-stops the app first** — `I ActivityManager: Force stopping com.sidr.launcher appid=10752 user=0: start instr`, `Killing … (adj 0): stop com.sidr.launcher due to start instr` — then the system relaunches it as home (`START … cat=[HOME] cmp=com.sidr.launcher/.LauncherActivity from uid 0`, `MARsPolicyManager: Current Home Package com.sidr.launcher Resumed`). So **every probe run restarts the launcher process**, and no probe reading says anything about a long-lived Sidr process. Second half: when the started activity finishes, focus returned to **One UI Home's stale home task** (`t11242`) rather than to Sidr, while the platform's own line read `I ActivityUtils: HomePackage : com.sidr.launcher, resumePackageName : com.sec.android.app.launcher` — i.e. the **role** is Sidr's and the **resumed task** was One UI's. A `dumpsys window` focus reading taken after an activity finishes therefore does **not** measure who holds the HOME role; `cmd package resolve-activity -a MAIN -c HOME` and that `HomePackage :` line do | 2026-09-16 | `ab2d063` |
| 32 | **Row 28's consequential half — is a refusal *detectable by the caller*?** The same `uninstallApp` method was run on both builds (rows 16/29 without the permission, row 28 with it) and everything the caller can see was compared | Sidr installed, is default home | **`startActivity`'s answer is IDENTICAL in both directions: `RETURNED_NORMALLY`, and no exception of any kind is thrown either way.** Refused-and-nothing-happened and dialog-shown-and-the-user-is-deciding are **indistinguishable** to the caller after the fact. The only *post-hoc* difference lives outside the process: the responder's own `E UninstallerActivity: Uid 10752 does not have …` line in logcat, which an app cannot read for another app. So the shipped seam's two failure signals (`ActivityNotFoundException`, `SecurityException`) fire in **neither** direction, and `Tier0IntentToolWorker.launch` would report success for both. **Row 27 is the opposite case on the same device:** `ACTION_SET_ALARM` without its permission is refused by `ActivityTaskManager` *before* dispatch and **does** throw `SecurityException` at the caller. The two Tier-0 refusal modes are therefore structurally different — framework-side enforcement throws, responder-side enforcement does not — and no worker can treat one mechanism as covering both | 2026-09-16 | `ab2d063` ± the one line |
| 33 | `context.checkSelfPermission(…)` from **inside** the app process (new probe method `permissionSelfCheck`, fires no intent), run once on each of the two builds — the only channel left after row 32, i.e. a **precondition check before firing** | Sidr installed, is default home | **It discriminates the two builds exactly.** With the line declared: `REQUEST_DELETE_PACKAGES :: PERMISSION_GRANTED (raw=0)`. Without it: `REQUEST_DELETE_PACKAGES :: PERMISSION_DENIED (raw=-1)`. `SET_ALARM :: PERMISSION_GRANTED (raw=0)` on both runs (positive control, and consistent with row 27 succeeding only when declared). `ACCESS_FINE_LOCATION :: PERMISSION_DENIED (raw=-1)` on both runs, which **confirms row 30's `dumpsys` reading from inside the process** rather than from the shell. So the refusal is knowable **in advance** even though row 32 shows it is unknowable **afterwards** | 2026-09-16 | `ab2d063` ± the one line |

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

**A third observation makes the build half of that comparison unusually strong — with a limit that was
also measured, after the first form of this claim turned out to be too strong.** `app-debug.apk` built
from `ab2d063` by a **full** task path (`--rerun-tasks`, `299–336 actionable tasks: all executed`) has
sha256 `9b7b5a4e3e5ff47340cfe11ac78d1de8d4f6d9d4e5bca3a45eb459a60939d44a`, and that is **byte-identical**
to the APK left on disk by the Task 13 round — reproduced **three** times (the Task 13 build, the
`--rerun-tasks` build for row 29, and a third full build after row 27's manifest edit had been reverted).
So the app APK genuinely does not differ between the two trees: the same bytes, not «differs only in ways
that cannot matter».

**The limit:** an *incremental* rebuild of the identical reverted source (`2 executed, 4 from cache, 293
up-to-date`) produced a **different** sha256, `e3dc8ccd628e537600708e79d03bfd454bf42b5c42a47ded53bb2dbc033f2991`.
Byte-identity here is therefore a property of the **full** build path, not a certificate the build hands
out generally, and a future round must not use an sha mismatch alone as evidence that sources differ. The
`androidTest` APK is not byte-identical across rounds at all, so the property is about the app APK — the
one whose manifest and permissions every row above depends on. The build that is installed on the phone
at the end of this round is the reproducible `9b7b5a4e…` one.

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
| `set_alarm` | `AlarmClock.ACTION_SET_ALARM` | clock time | **`com.android.alarm.permission.SET_ALARM` — REQUIRED, measured in the negative direction** (row 27): on a build without it `startActivity` throws `SecurityException … requires com.android.alarm.permission.SET_ALARM` and no alarm is created. Already declared and granted, so nothing new is needed — but it does belong in `toolPermissions` | **Creates the alarm, already enabled.** No prefilled form, no confirm button; the clock opened on its list showing the armed alarm | **Yes** — but see the §7.2 contradiction below |
| `show_alarms` | `AlarmClock.ACTION_SHOW_ALARMS` | — | None beyond the app's current set; no refusal | Opens a screen (the clock's alarm list); nothing changes | **Yes** |
| `open_app_info` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` | package | None beyond the app's current set; no refusal, for a launchable target **and** for one with no launcher activity | Opens a screen (`InstalledAppDetails`); the screen carries an «Удалить» button | **Yes** |
| `uninstall_app` | `Intent.ACTION_DELETE` (package URI) | package | **`android.permission.REQUEST_DELETE_PACKAGES`** (or `DELETE_PACKAGES`), named by the responder, `protectionLevel:normal`, **not declared** by the app today → refused (rows 16, 29). **Declaring it is sufficient and costs no prompt** (row 28): granted at install, and the dialog then appears | **As shipped: nothing, silently** — the uninstaller starts and dies in ~180–190 ms without drawing. **With the permission declared: the OS confirms** — «Удалить приложение? / Отмена / OK» (row 28). Either way `startActivity` returns normally and throws nothing, so the caller cannot tell the two apart afterwards (row 32) | **Not on the current seam.** The permission is one manifest line, but row 32's blind refusal is a seam problem, not a manifest one |
| `open_camera` | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | — | None beyond the app's current set; no refusal, and the app holds no `CAMERA` permission | Opens a screen (Samsung Camera, live viewfinder, shutter awaiting the user); no capture | **Yes** |
| `open_wifi_settings` | `Settings.ACTION_WIFI_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen — **which on this phone came up with a state-changing modal already raised** («Отключить мобильную точку доступа?») | **Yes**, with the observation named |
| `open_bluetooth_settings` | `Settings.ACTION_BLUETOOTH_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_battery_settings` | `Intent.ACTION_POWER_USAGE_SUMMARY` | — | None beyond the app's current set; no refusal | Opens a screen — served by **`com.samsung.android.lool`**, not `com.android.settings` | **Yes** |
| `open_data_usage_settings` | `Settings.ACTION_DATA_USAGE_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; carries a «Мобильные данные» toggle | **Yes** |
| `open_display_settings` | `Settings.ACTION_DISPLAY_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_sound_settings` | `Settings.ACTION_SOUND_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen; nothing changes | **Yes** |
| `open_location_settings` | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` | — | No refusal — but the app **declares `ACCESS_FINE_LOCATION`**, so this shows a location permission is *sufficient*, not that none is needed; whether one is **required** is unmeasured (row 24). Row 30 narrows it: at the re-measure both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` read `granted=false` and the screen still opened, so the *grant* is ruled out as the explanation and the *declaration* is not | Opens a screen; nothing changes | **Yes** |
| `open_notification_settings` | `Settings.ACTION_NOTIFICATION_SETTINGS` | — | None beyond the app's current set; no refusal | Opens a screen — **but only via the literal action string**: there is no such public constant on `compileSdk 37` | **Yes**, with the constant caveat |

**Twelve of thirteen survive; one does not** — and the thirteenth's blocker turned out to be two
blockers with different costs: a manifest line (removable, measured in row 28) and a seam that cannot
see a refusal (not removable by a manifest line, measured in row 32).

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

**2. `uninstall_app` — «does the OS confirm, or does it delete?» is answered *neither, as the app
ships*; with the permission declared it is «the OS confirms».** Row 16: the app is refused before any
dialog exists. §7.2 offered two branches and the device took a third, and the third is the dangerous
one, for a reason that is about the engine rather than about this tool:
**`startActivity` returned normally and threw nothing.** A worker built on the shipped seam
(`Tier0IntentToolWorker.launch`, whose only failure signals are `ActivityNotFoundException` and
`SecurityException`) would answer `ToolResult.Effected()` for an invocation that did nothing, and
`AgentExecutor` would write `ToolObserved(Effected)` into a trace that `DOC-ILM-3` requires to be 1:1
with reality. That is strictly worse than A1′'s `set_timer`, which at least failed loudly enough for
`ActivityTaskManager` to log a refusal. **Phase 3 must not ship `uninstall_app` on the current seam**,
and the two things it needs — the declared permission and a way for the worker to learn that the
responder refused — are both design work, not a manifest line.

**Task 13b measured both halves of that, and they came out asymmetric.** The permission half is *easier*
than this paragraph assumed: row 28 shows one `<uses-permission>` line is sufficient, is granted at
install with no prompt, and makes the OS draw the same dialog uid 2000 got — so «the OS confirms» is
reachable. The seam half is *exactly* as bad as feared and now measured rather than reasoned: row 32
shows `startActivity` returns `RETURNED_NORMALLY` with no exception **in both directions**, so the
refusal is invisible to the caller after the fact. Row 33 shows it is visible **before** the fact via
`checkSelfPermission`. The conclusion is unchanged — not on the current seam — but the work it implies
is now specific: a precondition gate, not a better failure signal.

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

### The two formerly empty rows — both now measured (Task 13b)

**Row 27 — is `com.android.alarm.permission.SET_ALARM` actually required by `ACTION_SET_ALARM`?
Measured: YES.** This is the sentence whose falsity shipped A1′'s `set_timer` dead, so it was measured
in the only direction that can answer it — a build **without** the permission. `startActivity` threw
`SecurityException … requires com.android.alarm.permission.SET_ALARM`, the framework printed a
`Permission Denial` line, and no alarm was created. **What depended on it:** whether a Phase 3
`set_alarm` row belongs in the `toolPermissions` map Task 2's guard checks. It does, and that is now a
measurement rather than the fail-safe reading. Note what the measurement also settles: `SET_ALARM`
being *declared* is not enough to explain row 13 — it is *required*, so the row-13 launch depended on
it.

**Row 28 — does the uninstall dialog appear for the app once `REQUEST_DELETE_PACKAGES` is declared?
Measured: YES, and it is the same dialog uid 2000 drew in row 26.** The permission is `normal`, so it
is granted at install with no prompt, and the uninstaller then stays, takes focus and asks. It was
cancelled with `KEYCODE_BACK` and the target package verified still installed (R13-3 — never confirm an
uninstall). **What depended on it:** whether `uninstall_app` can be a Phase 3 row at all. On the
permission question, yes.

### The half of row 28 that actually governs Phase 3: a refusal is invisible *afterwards* and visible *beforehand*

Row 28's permission answer is the easy half. The half Phase 3 has to build on is row 32, and it is
worth stating without hedging:

**`startActivity` tells the caller nothing.** On this device, for `ACTION_DELETE`, the call returns
`RETURNED_NORMALLY` and throws nothing **both** when the responder refuses outright (no permission —
the uninstaller lives ~190 ms and draws nothing) and when it draws the dialog. The two outcomes are
**indistinguishable from inside the process after the fact**. The only post-hoc evidence is the
responder's own logcat line, which belongs to another app and is not readable as a signal. A worker
built on the shipped seam — `Tier0IntentToolWorker.launch`, whose only failure signals are
`ActivityNotFoundException` and `SecurityException` — therefore answers `ToolResult.Effected()` in
**both** cases, and `AgentExecutor` writes `ToolObserved(Effected)` into a trace `DOC-ILM-3` requires to
be 1:1 with reality. **"We could not tell from inside the process" is the measured answer, not a
guess.**

**But it is knowable in advance.** Row 33: `context.checkSelfPermission("android.permission.REQUEST_DELETE_PACKAGES")`
returns `PERMISSION_GRANTED (0)` on the declaring build and `PERMISSION_DENIED (-1)` on the build
without it, from inside the app's own process. So the refusal-detection path Phase 3 needs for this
class of tool is a **precondition check before dispatch**, not an outcome check after it.

**And the two refusal modes are not the same mechanism, which is the part most likely to be
over-generalised.** Row 27 and rows 16/28/32 are opposite shapes on the same phone:

| Refusal enforced by | Example measured here | `startActivity` | Exception at the caller | `Permission Denial` log | Detectable after the fact? |
|---|---|---|---|---|---|
| **`ActivityTaskManager`**, before dispatch | `ACTION_SET_ALARM` without `SET_ALARM` (row 27) | refused | **`SecurityException`**, naming the permission | **yes** | **yes** |
| **The responding app**, after dispatch | `ACTION_DELETE` without `REQUEST_DELETE_PACKAGES` (rows 16, 29) | `RETURNED_NORMALLY` | **none** | **no** | **no** |

A worker that treats `SecurityException` as "the way a permission refusal arrives" is correct for the
first row and silently wrong for the second. This is also why Task 13's finding F5 (that
`grep -i 'Permission Denial'` is not a sufficient instrument) is a property of the *second* mode only —
on row 27 that grep would have worked perfectly. Neither the count of declared permissions nor the
presence of a `Permission Denial` line is a general test for "can this tool actually run".

**Two limits on all of the above, named rather than left implicit.** (1) It is two intents on one ROM;
which mode a *third* intent takes is not predictable from these two and must be measured per intent —
which is exactly what §3.1 requires anyway. (2) `checkSelfPermission` was measured as a **read**, not as
a guard in the shipped path: no production code was changed this round, and whether wiring it into a
worker actually closes the trace-fidelity gap is Phase 3's to build and prove.

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

---

## Task 13b — a four-observation **smoke round** on the shipped surface (2026-09-16)

**This is not device acceptance, and it must not be read as one.** Spec §14 puts acceptance after Phase
3, and Phase 3 adds eight-plus tools to this same surface, so accepting it now would accept something
about to change. Device acceptance remains the **owner's** and remains **unperformed**. What follows is a
smoke round the owner authorised for one purpose: none of A1″'s shipped surface — `f8d0051` (the
`app_shortcut` adapter), `c6b8c46` (the per-call snapshot), `de3274a` (`ToolSelector`) — had **ever
executed on a phone**, and Phase 3 is planned on top of it. Four observations, each de-risking Phase 3.
Neither the count nor the coverage here is a §7.2 candidate measurement; these are separate rows, in
their own section, with their own numbering (S1–S6).

Device state for all of it: SM-A325F, Android 13, One UI, **`ru-RU`**, Sidr holding
`android.app.role.HOME` (the owner set it by hand — rows 3/4 stand, no shell path does it on this build),
app build `ab2d063` (sha256 `9b7b5a4e…`, the reproducible full-path build), test APK from the same tree
plus this round's three new probe methods.

| # | What was called | Device state | Observed result, verbatim | Date | Build |
|---|---|---|---|---|---|
| S1 | **The shipped `app_shortcut` adapter, wired as `AgentProvidesModule` wires it** — `ShortcutToolSource(ShortcutCatalog(AndroidShortcutQuery(context), Dispatchers.IO))`, `refresh()` then `all()` / `names()`. Probe method `shortcutAdapterSnapshot`. Rows 6–9 measured `LauncherApps` **raw**; these three production classes had never run on a device | Sidr **is** default home | `hasShortcutHostPermission :: true`. `catalog.current.size :: 216`, **`all.size :: 216`**, **`names.size :: 216`**, `distinctPackages :: 65`, `distinctIds :: 216` (**no id collisions**). Every descriptor: `levels :: app_shortcut`, `effects :: EXTERNAL`, `risks :: SAFE`, `durabilities :: TRANSIENT`. `blankLabels :: 0`. `appLabelEqualsPackageName :: 0` — the raw-package-name fallback in `AndroidShortcutQuery` fired for **none** of the 65 packages. Ids have the form `shortcut:<package>/<shortcutId>` | 2026-09-16 | `ab2d063` + probe |
| S2 | **Raw `LauncherApps` at the same moment**, to tell adapter filtering apart from device drift: `LauncherAppsProbe#measureLauncherApps`, run 39 s after S1 | same | `getShortcuts.total :: 216`, `distinctPackages :: 65`, `isEnabled.true :: 216`. **Identical to S1 in both numbers.** So the adapter drops **nothing** on this device, and the 205 → 216 difference from row 7 (2026-09-14) is **device drift over two days**, not adapter behaviour — measured rather than argued. Side effect to record: this probe method also fires `startShortcut` on the first shortcut (row 10's measurement), so a third-party app was launched; `RETURNED_NORMALLY`, and the phone was returned to home afterwards | 2026-09-16 | `ab2d063` + probe |
| S3 | **`ToolSelector` over the real 216-descriptor registry**, beside the authored `ToolVocabulary`: `ToolSelector(ToolVocabulary(), ShortcutToolSource(...))`. Probe method `toolSelectorAtScale`, eight texts via `-e texts` | same | `registrySize :: 216`. **It neither collapses nor offers many — it decides.** `«youtube подписки»` → `MATCHED id=shortcut:com.google.android.youtube/subscriptions-shortcut`; `«youtube shorts»` → `MATCHED …/shorts-shortcut`; `«obsidian новая заметка»` → `MATCHED id=shortcut:md.obsidian/app:new` (a **two-word** name matched as a contiguous run). Declines, each for the documented rule: `«подписки»` → `NO_MATCH` (rule 2 — must name its app); `«открой youtube подписки»` → `NO_MATCH` (rule 4 — `открой` unaccounted); `«youtube подписки пожалуйста»` → `NO_MATCH` (rule 4's named recall cost, now observed rather than predicted). **Authored beats dynamic at scale:** `«таймер на 5 минут»` → `MATCHED id=set_timer args={duration=5 минут}` and `«системные настройки»` → `MATCHED id=open_system_settings`, both in ~1 ms | 2026-09-16 | `ab2d063` + probe |
| S4 | **Cost of one `select` at this scale**, read from the log timestamps of S3's eight calls | same | The two **authored** matches resolved in **≈1 ms** (02:01:04.521 → .522 → .522) — they return before the dynamic branch. The six calls that reached the **dynamic** branch took **≈64–69 ms each** (.193→.262, .262→.326, .326→.392, .392→.455, .455→.521, .522→.586). That is `DynamicToolNames.names()` over 216 shortcuts plus the per-candidate match, **per call**, with no `LauncherApps` call involved (the catalog is a `@Volatile` snapshot). Figure from logcat timestamps around the call, not from an in-process timer — good to ±1 ms, no better | 2026-09-16 | `ab2d063` + probe |
| S5 | **A command that hits a shortcut, driven through the launcher's own UI** in `ru-RU`: tapped the command field on Sidr's home, typed `youtube shorts`, pressed ENTER. Nothing scripted past the keyboard | same | **It reached the agent surface, ran, and disclosed its provenance.** The shortcut genuinely executed through `LauncherApps.startShortcut`, not as an app launch: `ActivityTaskManager: START u0 {act=com.google.android.youtube.action.open.shorts flg=0x1000c000 cmp=com.google.android.youtube/.app.honeycomb.Shell$HomeActivity (has extras)} from uid 10234` — the **shortcut's own action**, started from **YouTube's** uid, where a plain launch would have been `act=android.intent.action.MAIN cat=[LAUNCHER]` from Sidr's. The surface (screenshot read) showed: «План выполнен» / `COMPLETED`; the step line «Открыть «YouTube — Shorts» — выполнено» — i.e. the **third-party label**, composed qualifier — name; the provenance line **`APP SHORTCUT · EXTERNAL`**; and `АГЕНТ · SHORTCUT:COM.GOOGLE.ANDROID.YOUTUBE/SHORTS-SHORTCUT`. So `DOC-ILM-2` disclosure works for a dynamic third-party tool on a real device with a real label. Tapping «Закрыть» cleared the card **and the command buffer** and brought the home body back (the A1′ acceptance fix (b), smoke-confirmed) | 2026-09-16 | `ab2d063` |
| S6 | Whether the S5 run stopped for consent | same | **It did not, and that is consistent rather than surprising.** Every shortcut descriptor is `risk = SAFE` (S1), so `requiresConsent(SAFE)` is false and the one-step plan ran to `COMPLETED` with no gate: from ENTER to YouTube in foreground was under 4 s with no user input. Observed, not judged — see the finding below | 2026-09-16 | `ab2d063` |
| S7 | **The HOME role taken away — the fail-closed path that had never executed.** `cmd package set-home-activity com.sec.android.app.launcher/.activities.LauncherActivity` → `Success` (the permitted direction; rows 3/4 still stand for the other one), then `shortcutAdapterSnapshot` re-run unchanged | Sidr installed, **not** default home | **Degrades to an empty tool set, exactly as Task 5's measurement prescribed, and without a `SecurityException` ever being raised.** `hasShortcutHostPermission :: false`; `catalog.current.size :: 0`, `all.size :: 0`, `names.size :: 0`, `distinctPackages :: 0`; every aggregate line empty. **No crash, no `FATAL EXCEPTION`, no ANR** in the whole capture. The primary gate fired first, so `getShortcuts` — row 2's thrower — was **never called**: the `SecurityException` catch in `AndroidShortcutQuery` remains the untested backstop it is documented to be, and this row does **not** exercise it | 2026-09-16 | `ab2d063` + probe |
| S8 | `toolSelectorAtScale` re-run with the role gone, four texts | same | `registrySize :: 0`. `«youtube shorts»` and `«youtube подписки»` → `NO_MATCH` — the two texts that selected in S3. The **authored** tools are untouched: `«таймер на 5 минут»` → `MATCHED id=set_timer args={duration=5 минут}`, `«системные настройки»` → `MATCHED id=open_system_settings`. All four in ≈1–2 ms, the dynamic branch now having nothing to walk | 2026-09-16 | `ab2d063` + probe |
| S9 | **The same UI command as S5, with the role gone**: `am start -n com.sidr.launcher/.LauncherActivity`, tapped the field, typed `youtube shorts`, ENTER | same | **The honest blocked-state statement, and no crash.** The surface showed «ИИ-провайдер ещё не настроен.» with a «Настроить провайдера» affordance — hard rule 5 / ADR 1/4's «never *Unknown command*, which blames the command for the system's state», reached here because step 2b found nothing and the command fell through to the model path with no provider configured. Focus stayed `com.sidr.launcher/.LauncherActivity`; a scan of the whole capture for `FATAL EXCEPTION`, `ANR in com.sidr`, `Process com.sidr.launcher … has died` and `Force finishing` found **nothing**. **Why no provider:** the 2026-09-14 `connectedAndroidTest` uninstall destroyed the Keystore material behind the BYOK key (recorded in this file's own preamble) — a pre-existing state, **not** something this round's `install -r`s caused: uid stayed `10752` and `databases/` survived all five of them | 2026-09-16 | `ab2d063` |

### What the smoke round found, in order of how much it changes Phase 3

**S-F1 — the registry carries 216 tools, all `EXTERNAL`, all `SAFE`, and therefore all ungated.** S6 is a
measurement, not a complaint: `ToolSelector` → one-step plan → `ToolFederation` → `startShortcut`, with
no consent checkpoint, because `ActionRiskLevel.SAFE` is what `ShortcutToolSource` assigns every
descriptor. The launch itself is benign and reversible for the one shortcut measured. What is
**owner-level** is that the same path applies unchanged to all 216, and the 216 are not a curated set —
they are whatever the installed apps happen to publish, changing as apps update. This is not a defect
against any written rule: `DOC-ADL-1`'s predicate is applied correctly and `DOC-ILM-2`'s disclosure is
present (S5). It is a scope question A1″ should put to the owner rather than settle.

**S-F2 — shortcut labels contain the owner's personal data, and that is a privacy input nobody had
measured.** Among the 216 names the adapter returned are WhatsApp conversation shortcuts whose
`shortcutLabel` is a **contact display name** — in Arabic, Russian and Latin script — and, in at least
one case, a **bare international phone number**. These are not a special case: they are ordinary
`FLAG_MATCH_DYNAMIC` shortcuts, and the adapter is correct to return them. The consequences are
concrete and belong to Phase 3, not here:
 - such a label is rendered on the agent surface as a tool name (S5 shows the mechanism);
 - `DynamicToolLabels` holds all of them in memory for the life of the process;
 - **whether any of them can reach the model** is an `OutboundContextPolicy` question that this round did
   not measure and must not be guessed at;
 - and `agent_session.goal_text` already puts raw command text on disk, so a command naming a contact
   puts that name in the database.

**The verbatim values are deliberately not reproduced in this file**, which is committed to git; they
are in the round's git-ignored evidence directory. That omission is itself the finding's point.

**S-F3 — the adapter's count is the raw count, so "205" was never an adapter property.** S1 and S2
together settle something rows 7–9 could not: the adapter's three filters (`isEnabled`, non-blank label,
id round-trip) removed **zero** entries on this device. Task 12's number to measure against is therefore
"whatever `getShortcuts` returns today", and the two-day drift from 205 to 216 is the concrete
demonstration that the file's existing warning — "one device's number, not a constant" — is about days,
not just about devices.

**S-F4 — one visual anomaly was observed, chased, and turned out not to be ours.** A panel bottom-right
of Sidr's home screen, overlapping the tab bar and obscuring «агенты» / «активность», appeared after the
S5 run and its content **changed between frames**. It is **YouTube Shorts in picture-in-picture**,
playing the video the shortcut opened: the third frame shows the video plainly. Recorded because the
first two frames read exactly like a launcher layout defect, and the honest trail matters more than the
tidy conclusion — a smoke round that had stopped at one screenshot would have filed a Sidr bug.

**S-F5 — the degradation path is the strongest result of the round, because the same command was run in
both states.** `youtube shorts` ran a third-party shortcut tool with full provenance disclosure at 02:02
(S5) and, four minutes later with the HOME role gone, produced «ИИ-провайдер ещё не настроен.» with no
crash and no offer of a tool that could not run (S9). Nothing had to be rebuilt or reinstalled between
the two; the only thing that changed was a runtime role. That is spec §5.3's requirement observed
end-to-end rather than argued from `ShortcutCatalog`'s KDoc — and note which mechanism did the work:
`hasShortcutHostPermission` gated it (S7), so the `SecurityException` backstop was never reached and is
still unexercised on a device.

**Limits of the smoke round, stated rather than implied.** (1) S1/S3 wire the production classes
**by hand**, as `AgentProvidesModule` does; `:app`'s `androidTest` has no Hilt testing dependency and
adding one would change the build under measurement, so a wiring mistake in the Hilt graph is invisible
here (it is `DoctrineGuardTest`/`ToolRegistryPermissionGuardTest`'s job on the host). **S5 does** go
through the real graph, which is why it is the load-bearing one. (2) One locale (`ru-RU`), one device,
one shortcut actually executed out of 216. (3) Nothing was measured about what happens when a shortcut
disappears between plan and invocation — `ShortcutStalenessEndToEndTest` covers it on the host; no device
row exists. (4) Row 12 remains Task 5's and remains empty: S1's `appLabelEqualsPackageName :: 0` shows
the *fallback* never fired for these 65 packages, which is not the same observation as whether
`getApplicationInfo` throws for a shortcut-contributing package with **no launcher activity** — no such
package was known to be in the set. (5) S7 leaves the `SecurityException` catch unexercised, as that row
says — closing it needs the role to change *between* the gate check and the call, which no shell command
can arrange.

**Device left as found**, verified: One UI Home holds `android.app.role.HOME`
(`resolve-activity` → `com.sec.android.app.launcher`) and is focused; the installed app is the
HEAD-matching `9b7b5a4e…` build with `SET_ALARM: granted=true` and **no** `REQUEST_DELETE_PACKAGES`; uid
`10752`; `databases/` intact; all four `sidr` packages installed; `dumpsys alarm` greps **0** for
`SIDR PROBE`. The manifest edits of rows 27 and 28 exist in no commit.
