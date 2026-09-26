# B15 — measurement inputs

Three files live here and **none of them is committed** (see `.gitignore` for why). This README is the
instruction for regenerating them, so the measurement is reproducible without the data being in history.

## What the measurement is

Master Plan §3.6 row `B15`: measure the share of real phrasings that the tool-selection path **declines**,
broken down by *why*, before A4′ decides whether the multi-turn clarification protocol is ornament or
product. The row states the threshold plainly — "if 5% is declined it is ornament; if 40%, that is the
product" — and it states the methodology, which is the part that is easy to get wrong:

> The corpus is written by the **owner**, without looking at the trigger table. A corpus written by the
> author of the triggers measures the author.

## `shortcuts.jsonl` and `apps.jsonl` — regenerate from the device

Both come from one probe, read-only, which asserts nothing and launches nothing:
`app/src/androidTest/java/com/sidr/launcher/probe/ShortcutCatalogDumpProbe.kt`.

**Sidr must hold `android.app.role.HOME`** or `LauncherApps.getShortcuts` throws, `ShortcutCatalog.refresh`
swallows it into an empty list, and the dump silently reports zero. The probe prints a note saying which
of the two an empty result means, but it cannot tell them apart itself.

**Never `connectedAndroidTest`** — it uninstalls the app under test and destroys its data, including the
accepted schema-4 database. Drive `am instrument` by hand:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=<jdk17> :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk   # test package only
adb logcat -c
adb shell am instrument -w -e class com.sidr.launcher.probe.ShortcutCatalogDumpProbe \
  com.sidr.launcher.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s SIDR_B15:I -v raw > b15_raw.txt
```

Then split `b15_raw.txt` into the two JSONL files, one JSON object per line:
`{"id","qualifier","name"}` for shortcuts, `{"package","label"}` for apps.

**Check the end marker.** `dump.end` and `apps.end` carry the row count the probe believed it wrote. If
the number of parsed rows disagrees, the logcat read was truncated and the fixture is short — a short
fixture makes the decline rate look *better* than it is, because a tool that is missing cannot tie.

**The catalog drifts.** Measured 216 on 2026-09-16 (A1″ smoke round) and 223 on 2026-09-20, because apps
update and publish different shortcuts. That drift is the reason the fixture exists: a number re-measured
against a moving registry is not comparable to the one before it.

## `corpus.txt` — written by the owner

One phrasing per line. Blank lines and lines starting with `#` are ignored. Written **without** looking at
`ToolVocabulary.kt`, unedited afterwards — the unedited quality is the thing being measured.

## Running the measurement

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=<jdk17> \
  :data:repository:testDebugUnitTest --tests '*SelectionDeclineMeasurement*' --rerun-tasks
```

With any input missing the measurement reports that it was skipped and passes: a fresh clone has no
fixture, and a gate that goes red because a git-ignored file is absent would be a gate that lies.
