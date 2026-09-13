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
