# A1″ Phase 3b — the eleven navigating tools · Implementation Plan

> **Status: written 2026-09-19, not started.** Branch `launcher--7`, base `43f2c94`, gate baseline
> **1438 / 0 / 0**. Spec: [§7.2 / §7.6 / §8.2 / §14 / §16 of
> 2026-09-12-a1-second-tool-mass-and-selection-design.md](../specs/2026-09-12-a1-second-tool-mass-and-selection-design.md).
> Template: [phase 3a](2026-09-18-a1-phase3a-acting-tools.md). ADR to extend (not rewrite):
> «2026-09-19 — Этап 5.5 (A1″)».
>
> **No device round.** §7.6: "every Android premise Phase 3 rests on is already in rows 13–33".
> Verified row by row below — §0.1 — against
> [the measurements file](2026-09-12-a1-device-measurements.md), not against the spec's prose.
>
> **The count is reported split, always.** At this plan's boundary: **sixteen authored tools, of which
> five act.** Never one figure. (§7.6: "both numbers reported split, never as one figure.")

---

## 0. Pre-flight scan of this plan against the tree

Phase 3a's pre-flight found six defects before its first task. This one found **ten**, plus one
question that is the owner's and not an agent's. Each was found by reading the tree, not the prose.

### 0.1. The eleven rows, each checked against the measurement it claims

Every row below cites the measurement file by number. **No cell is empty and no cell is negative**, so
the premise §7.6 asserts holds — but four rows say more than "it opens a screen", and those four
carry consequences into the build.

| Tool | Intent, as it must be written | Row | Permission | What the device did |
|---|---|---|---|---|
| `show_alarms` | `AlarmClock.ACTION_SHOW_ALARMS` | 14, re-run 29 | none | opens the clock's alarm list; state unchanged |
| `open_camera` | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | 17 | none — app holds **no** `CAMERA` permission, so the negative is readable here | opens the viewfinder. **The sensor goes live with no user tap** (row 17 names this) |
| `open_wifi_settings` | `Settings.ACTION_WIFI_SETTINGS` | 18 | none | **see P3 — it opened with a state-changing modal already raised** |
| `open_bluetooth_settings` | `Settings.ACTION_BLUETOOTH_SETTINGS` | 19 | none — app holds no `BLUETOOTH*` at all | opens the screen; state unchanged |
| `open_battery_settings` | `Intent.ACTION_POWER_USAGE_SUMMARY` | 20 | none | opens the screen — **responder is Samsung Device Care, not `com.android.settings`** (P4) |
| `open_data_usage_settings` | `Settings.ACTION_DATA_USAGE_SETTINGS` | 21 | none | opens the screen; the landed screen carries a mobile-data toggle |
| `open_display_settings` | `Settings.ACTION_DISPLAY_SETTINGS` | 22 | none | opens the screen; state unchanged |
| `open_sound_settings` | `Settings.ACTION_SOUND_SETTINGS` | 23 | none | opens the screen; state unchanged |
| `open_location_settings` | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` | 24, 29, 30, 33 | **sufficient, never proved necessary — see P2** | opens the screen; state unchanged |
| `open_notification_settings` | **no public constant exists** — see P1 | 25 | none | opens the screen; state unchanged |
| `open_app_info` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + package `Uri` | 15 | none | opens the screen — **one tap from «Удалить»** (P5); a target with no launcher activity was **not** filtered out |

All eleven are `system_intent` / `EXTERNAL` / `SAFE` / `TRANSIENT`, as the brief expected. **`SAFE` is
justified once, positively, and not by "it is only a settings screen":** these tools perform no act at
all, so there is nothing to reverse, and "the final act is the user's" is literally true of them — the
reasoning `Tier0IntentToolSource`'s KDoc already isolates for `open_system_settings` and explicitly
refuses to fold into `set_timer`'s four-property argument. P3 and P5 do not weaken that: what the
*landed screen* offers the user is not an act this tool performed.

### 0.2. The ten findings, each with what it changes

**P1 — `Settings.ACTION_NOTIFICATION_SETTINGS` does not exist, and the naive fix turns a guard RED.**
Row 25 records that `javap` over `platforms/android-37.0/android.jar` finds no such field. Writing
`Intent("android.settings.NOTIFICATION_SETTINGS")` makes
`ToolPermissionManifestGuardTest`'s **parse-completeness** half fail: `anyIntentConstruction` counts
every `Intent(`, `actionsIn` reads only what `intentWithAction`'s capture group
`[A-Za-z_][A-Za-z0-9_.]*` matches, and a quoted literal matches nothing. The counts diverge and the
test reddens — the guard behaving exactly as designed ("teaching the scan one real shape at a time").
**Decision: do not widen the regex.** Declare, in `Tier0IntentToolWorker`:
```kotlin
/** Row 25: the platform has no `Settings.ACTION_NOTIFICATION_SETTINGS` constant; the action string is ours. */
private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
```
and write `Intent(ACTION_NOTIFICATION_SETTINGS)`. The scan reads the token, the map keys on
`"ACTION_NOTIFICATION_SETTINGS"`, and no new blind spot is opened. The comment naming row 25 is
required, because the key otherwise reads as a platform constant.

**P2 — `open_location_settings`'s permission is measured SUFFICIENT and never NECESSARY, and the two
possible rows have opposite failure modes.** Row 24 states the requirement is unmeasured. Row 30
removes the *grant* from the explanations (`ACCESS_FINE_LOCATION: granted=false` at the moment the
screen opened), row 33 confirms that from inside the process, and row 29 re-ran it on a fresh build.
What remains unexcluded is the *declaration*. So:
- `listOf("android.permission.ACCESS_FINE_LOCATION")` — `Tier0IntentToolSource.available()` filters on
  `presence::isGranted`, and row 33 measured that permission **DENIED** in-process. The tool would be
  withheld from the registry **on the owner's own phone**, registered nowhere, reachable never — and
  `Tier0IntentToolSourceTest`'s equality lists would stay green (see P6). Shipped invisible.
- `emptyList()` — the tool ships alive, and the claim "needs none" is the one row 24 declines to make.

**Decision: `emptyList()`, with the qualification written into the row's comment**, because the guard
can only verify permissions a row *claims* (the boundary `ToolRegistryPermissionGuardTest`'s KDoc
already states and Task 4's mutation 6 measured). It goes on the acceptance checklist as a numbered
item: if the screen fails to open on a build where the prayer feature's declaration is absent, the row
is wrong. **This is a named limit, not a closed question.**

**P3 — `open_wifi_settings` is the one navigating tool whose landed screen changed device state on one
tap, and the device it did that on is the owner's tether.** Row 18: the Wi-Fi screen opened **with a
modal already raised** — «Отключить мобильную точку доступа? … Отмена / OK» — because the hotspot was
on. The owner's laptop reaches the internet through this phone. The tool stays `SAFE` (it opened a
screen; the modal is the responder's and the tap is the user's), but this must be (a) a comment on the
descriptor, (b) a **SAFETY line** on the acceptance checklist, not an ordinary numbered item.

**P4 — `open_battery_settings` resolves to Samsung Device Care** (`com.samsung.android.lool/…
PowerUsageSummary`), not to `com.android.settings` (row 20). `ACTION_POWER_USAGE_SUMMARY` resolving at
all is a **ROM-dependent premise measured on one device**, and `launch()` already returns `Failed` on
`ActivityNotFoundException`. Named in the descriptor comment, not fixed.

**P5 — `open_app_info` opens a screen one tap from «Удалить»** (row 15), and run 2 measured that a
package with **no launcher activity** was not filtered out. Same reasoning as P3: `SAFE`, named.

**P6 — `Tier0IntentToolSourceTest`'s lists are `assertEquals` over whole lists, and that is precisely
what makes R14-31 bite rather than what protects against it.** Three assertions (the `all()` id list,
the risk map, the consent list, at lines 57/68/77, and again at 166 and 196) are equalities, so adding
a **registered** descriptor turns them red. But `Tier0IntentToolSource.available()` fails closed on a
missing catalog row (`catalog.permissionsFor(id) ?: return@filter false`), so a descriptor added
**without** its `ToolPermissionCatalog.ROWS` row is withheld, `all()` returns the previous list, and
every one of those equalities stays **GREEN**. The tool is registered nowhere and the suite is silent.

**Method rule, binding on every build task below: extend the expected-id lists FIRST**, before the
descriptor and before the catalog row. Then a forgotten row is a red test rather than an invisible
tool. This is the mechanical answer to R14-31's "procedural" status, and it costs nothing.

**P7 — mutual shadowing among authored entries is already mechanically caught, per trigger.**
`ToolVocabularyReachabilityTest.every trigger recognises its own sample command` asserts
`vocabulary.match(command)?.id == entry.id`, and `ToolVocabulary.match` returns `null` when two entries
claim one text. So a collision inside the eleven — or with the existing nine — reddens that test on the
colliding trigger's own sample command. **It is not thereby proved load-bearing for our set**, so Task
D plants a deliberate collision and observes RED. This is the one place where the brief's expectation
("проверь мутацией") and the tree agree exactly.

**P8 — the surface scan requires a RAW STRING LITERAL for every id not declared in
`domain/tool/ToolId.kt`.** `DoctrineGuardTest.every tool in the production federation has a non-generic
label on the surface` looks up the id in `ToolId.kt`; finding none, it requires
`presentationText.contains("\"$id\"")`. So each of the eleven needs a `private const val` in
`AgentSessionPresentation.kt` holding the literal id, a `toolLabelFor` arm, and a string resource —
the shape `TIER0_SET_ALARM` / `TIER0_UNINSTALL_APP` already use, for the stated reason that
`:feature:launcher` has no edge to `:data:repository`.

**P9 — writing a shared intent helper would take all eleven out from under
`ToolPermissionManifestGuardTest` entirely, and eleven near-identical intents is exactly when one gets
written.** The guard admits a file only when it both declares `: ToolWorker` **and** contains
`Intent(`; a helper is neither. Its KDoc names this as measured (a `call_number` probe went **green
4/4** with `CALL_PHONE` absent from the manifest) and names A1″'s Tier-0 mass as the concrete risk.
**Decision, said out loud as the brief requires: no helper.** All eleven arms go in the existing
`Tier0IntentToolWorker`, each constructing its `Intent(...)` **inline in its own `when` arm**, the
shape the four shipped arms already use. No new worker file either — which also leaves
`expectedIntentIssuingWorkers = listOf("Tier0IntentToolWorker.kt")` untouched. **If a later task
finds itself wanting a helper, that is an escalation, not a refactor.**

**P10 — Class B: verified at the gate's logic, not at a file.** `app/build.gradle.kts:225` filters
`it.name == "strings_locked.xml" && it.parentFile.name == "values"`; `grep -c launcher_agent_step
feature/launcher/src/main/res/values/strings_locked.xml` → **0**. The step strings live in
`values/strings.xml`, outside the signature's scope. **Expected answer: no re-signing, nothing to
recompute, and the agent computes no sha256.** If any task finds itself needing a key in
`strings_locked.xml`, that is a reversal: stop, re-check against `app/build.gradle.kts`, report to the
owner.

### 0.3. The question that is the owner's, and that this plan does not settle

**3b takes the count of Turkish triggers no native speaker has judged from SEVEN to TWENTY-THREE.**

> **Corrected 2026-09-20, after the build.** This section first said "to eighteen", derived as 7 + one
> form per new tool. That was an **undercount**: most of the eleven declare **two** `tr` forms, so 3b
> adds **sixteen** new Turkish trigger strings, not eleven. Counted the way A1″ residual (4) counts —
> `için`/`kullan` as one item — the total after 3b is **23**; counted as raw distinct strings it is
> **24**. The vocabulary table holds **27** `tr` forms in all, of which three (`zamanlayıcı ayarla`,
> `sistem ayarları`, `android ayarları`) predate the unjudged accounting. An under-claimed deficit is
> the same class of error as an over-claimed success, which is the whole subject of Task 6 — so it is
> corrected here rather than carried.

A1″ residual (4) records seven (`sayaç ayarla` inherited from A1′, plus six from 3a) and records the
owner-level ruling that «assumed fine» is not an acceptable answer to that row. Eleven more tools with
`tr` triggers is not a marginal addition to that number — it is more than doubling it, and it is being
done *before* the existing seven have been judged, on the same phone, in the same unjudged column.

**This plan does not decide it.** It proposes the `tr` column (Task 2–4), flags every form, and the
build proceeds — because the alternative, blocking eleven tools on a question no agent and no guard can
answer, is what R14-42 already decided against for `için`/`kullan`. But the *number* is the finding:
the acceptance checklist must say eighteen, not eleven, and the owner should know before the build
that one `ru-RU` acceptance round will now leave eighteen unjudged Turkish forms behind it.

**Least-confident forms, named in advance** (same discipline as R14-42): `pil ayarları` (battery —
`pil` is the everyday word, but Samsung's own screen is «Действия с аккумулятором» and One UI's Turkish
may use a different noun), `veri kullanımı` (data usage), and `bildirim ayarları` vs the ROM's own
label. These three are where a native speaker's read is most likely to differ.

---

## Global Constraints

**Do not touch, do not revert, do not commit:** `docs/governing/sidr-agentic-master-plan-v1.0.md`
(modified) and `docs/a4-runtime-inputs.md` (untracked). They belong to a live peer session
(`ListAgents`: `sidr-launcher-31`, `sidr-launcher-4c`) and have survived fourteen dispatches untouched.
**Every `git add` names explicit paths.** No `-am`, no `add -A`, no `add .`, no `checkout -- .`, no
`stash`, no `reset --hard`.

**Commits:** the subject contains a double quote (`A1"`), so `git commit -F <file>`, never `-m`. Last
line of every message: `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`. **Never push.**

**Device:** none needed, and none used. Never `connectedAndroidTest`, never any Gradle task that
touches the phone (AGP removes both APKs and destroyed the accepted schema-4 database on 2026-09-14,
costing the Keystore material behind the BYOK key). Never `adb uninstall`. The owner's laptop is
tethered through that phone.

**Gate, run with JDK 17:**
```
./gradlew --no-daemon :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test
```
`:domain:jvmTest` and `:consumer:jvm:test` **must** be listed — `testDebugUnitTest` reaches neither.
Never pipe through `tail`; check the exit code. Use `--rerun-tasks`, never `--rerun`. Read counts from
the JUnit XML, not the console. **Clear `build/test-results` before any boundary gate** (R14-44), and
the last action of any mutation-proving task is a gate re-run from a cleared tree.

**Decomposition before the run, never after.** Derive the expected per-module deltas, then run, then
compare. A total cannot distinguish "two added" from "two added and two lost".

**Frozen, out of the diff:** `ActionIds`, `OutboundContextPolicy.ALLOWED`, `ObservedFact`,
`CommandFailure`, `ArgType`, Room schema 4. **`GoalShape` stays at two values** — phases 0–3a added
nine tools and four adapters without one, and needing a new one here is a finding and a stop.

**Not in this plan:** device acceptance (Phase 4, the owner's, after 3b — §14); the A4′ residuals
(R14-37's generalisation, R14-38, R14-39, `DURABLE_EFFECT`, the wall clock) — **accumulate evidence,
do not repair**.

### The guard-list surface, which is incomplete by construction

R14-41: it grew 5 → 6 → 7 → 8 inside two sessions, and every dispatch's list was short. This is a
**floor**, known to be partial. **Run the full gate early, not at the end.**

1. `ToolPermissionCatalog.ROWS` — one row per tool, `emptyList()` **explicit** (a missing row withholds
   the tool and ships it invisible — P6/R14-28)
2. `DoctrineGuardTest.declaredRisk`
3. `DoctrineGuardTest.REQUIRED_TOOL_IDS`
4. `DoctrineGuardTest`'s textual scan of literals in `AgentSessionPresentation.kt` (P8)
5. `DoctrineGuardTest.productionAdapters()` + the adapter-count and level assertions
6. `ToolRegistryPermissionGuardTest.REQUIRED_TOOL_IDS` — **a second, separate list from #3**
7. that class's own permission column (now reading production, but its floor is its own)
8. `Tier0IntentToolSourceTest` — the registered-id equality lists **and the test's own name**
   («offers four tools» → fifteen)
9. that class's per-id risk map
10. its sweep `find never answers for a tool all() withholds` — an **enumeration**, so a missing tool is
    simply not checked
11. `ToolPermissionManifestGuardTest.requiredPermissions` — keyed on the **text of the intent constant**
12. `ToolWorkerCallSiteGuardTest` — only if a new worker file is created. **P9 says none is.**

Plus, not in the brief's list and found by this scan: **`ToolVocabularyLocaleGuardTest`** and
**`ToolVocabularyReachabilityTest.REQUIRED_TOOLS`** carry their own floors over the vocabulary table.
That makes **fourteen** known today. **The number keeps moving, and no KDoc states it** — which is
itself the finding (R14-41). Extend the floor **and** `REQUIRED_TOOL_IDS` in the same commit: an
instruction, not a hope.

---

## Task ordering

| # | Task | Deliverable |
|---|---|---|
| 1 | Carry the traps from prompts into code | three KDoc/comment edits — the brief's Task B |
| 2 | Slice A — `show_alarms`, `open_camera`, `open_wifi_settings`, `open_bluetooth_settings` | 4 tools, registered, reachable, rendered |
| 3 | Slice B — `open_battery_settings`, `open_data_usage_settings`, `open_display_settings`, `open_sound_settings` | 4 tools |
| 4 | Slice C — `open_location_settings`, `open_notification_settings`, `open_app_info` | 3 tools, incl. the only argument-carrying one |
| 5 | Mutation round | every new/changed guard proved falsifiable |
| 6 | The over-claim correction | four documents — the brief's Task A |
| 7 | Close | ADR section, `CLAUDE.md`, `current-status.md`, checklist +11, commit proposed |

Each of tasks 2–4 is a **vertical slice**: its tools are registered, permitted, dispatched, reachable
in three locales and rendered on the surface before the task ends. A slice that leaves a tool
registered-but-unreachable is the block's own recurring defect and is not "progress".

---

# Task 1: carry the traps from prompts into code

**Why first.** Three things have been carried in dispatch prose for two sessions. Prose does not hold;
mechanism holds. Doing this first also means tasks 2–4 read the warnings in the files they edit.

1. **`ToolMatchPlanner`, at the `app`-resolution block** — KDoc naming R14-37 in the code that causes
   it: the block keys on `argSchema.any { it.name == APP_ARG }` **regardless of `required`**, so a
   descriptor declaring `app` **optional** yields `raw = ""` → `resolve("")` → `null` → `NoPlan` for
   every goal forever, with the suite green. State that the two pins (`uninstall_app`, and
   `open_app_info` from Task 4) are **per-descriptor and not a generalisation**, and that the
   generalisation is A4′'s.
2. **`DoctrineGuardTest`** — a comment at the head of the guard-list constants: *the set of lists that
   must change together when a tool is added is **not enumerable from any document**; it is fourteen
   known today and it has been 5, 6, 7 and 8 within two sessions; see spec §8.2; the only reliable
   enumeration is a full gate run.*
3. **`ToolVocabularyReachabilityTest`** — KDoc for the escalation protocol, which is the one part of
   the trigger rule no guard mechanises: the guard catches a FastPath collision, but **what to do next
   is a protocol** — a trigger that takes a FastPath verb is **escalated to the owner naming the verb**,
   never worked around by reshaping the trigger until the guard goes quiet. Name the verbs that
   currently claim text: `open`/`launch`/`start`, `открой`/`открыть`/`запусти`/`запустить`, the `tr`
   suffixes `aç` and `kur`, and the bare `SETTINGS_KEYWORDS` `settings` / `настройки` / `ayarlar`.

**Verification:** gate green, no behaviour change, no test count change. Comments only.

---

# Tasks 2–4: the three slices

Every slice repeats the same nine edits. They are written once here; each task names only its tools,
its triggers and its strings.

**Order within a slice is load-bearing (P6):**

1. **Expected lists first** — `Tier0IntentToolSourceTest` (id equality lists, risk map, sweep
   enumeration, and the test *name* on the first slice), `DoctrineGuardTest.REQUIRED_TOOL_IDS` and
   `declaredRisk`, `ToolRegistryPermissionGuardTest.REQUIRED_TOOL_IDS`. **Red at this point is
   correct.**
2. `ToolPermissionCatalog.ROWS` — one **explicit** `emptyList()` per tool, with P2's qualification
   spelled out on `open_location_settings`.
3. `Tier0ToolIds` — one `ToolId` per tool.
4. `Tier0IntentToolSource.descriptors` — `system_intent` / `EXTERNAL` / `SAFE` / `TRANSIENT`, with the
   per-tool comments P3/P4/P5 require, each citing its measurement row.
5. `Tier0IntentToolWorker` — one `when` arm per tool, `Intent(...)` **inline** (P9), `launch(...)`.
6. `ToolPermissionManifestGuardTest.requiredPermissions` — one entry per intent constant **as written
   in the worker source**, `emptyList()` meaning "needs none".
7. `ToolVocabulary.DEFAULT_ENTRIES` — one entry per tool, `en`/`ru`/`tr`, each carrying its own
   distinguishing token, **never a bare settings synonym** (§7.6), and never a form FastPath claims.
8. `AgentSessionPresentation` — `private const val` with the literal id (P8) + a `toolLabelFor` arm.
9. `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` — one `launcher_agent_step_*` key per
   tool, all three locales, **in the same commit** (`LocaleCompletenessGuardTest`). Zero-argument
   strings carry no placeholder, exactly as `launcher_agent_step_settings` does today; the caller
   passes a subject that the formatter ignores.

**Per-slice verification:** full gate from a cleared `build/test-results`, decomposition derived
first; then, for each tool, assert by hand that it appears in `registry.all()`, that
`ToolVocabulary.match` returns it for one command per locale, and that `toolLabelFor` does not fall to
`launcher_agent_step_generic`.

### Task 2 — Slice A

`show_alarms` · `open_camera` · `open_wifi_settings` · `open_bluetooth_settings`

Proposed triggers (all noun phrases — the `en` forms **cannot** begin with `open `, which
`LAUNCH_VERBS` claims as a prefix; the `tr` forms **cannot** end with ` aç`):

| Tool | `en` | `ru` | `tr` |
|---|---|---|---|
| `show_alarms` | `alarms`, `my alarms` | `будильники`, `мои будильники` | `alarmlar`, `alarmlarım` |
| `open_camera` | `camera app`, `photo camera` | `камера`, `приложение камеры` | `kamera`, `kamera uygulaması` |
| `open_wifi_settings` | `wifi settings`, `wi-fi settings` | `настройки wi-fi`, `настройки вайфая` | `wi-fi ayarları`, `kablosuz ayarları` |
| `open_bluetooth_settings` | `bluetooth settings` | `настройки bluetooth`, `настройки блютуз` | `bluetooth ayarları` |

**`open_camera` needs a deliberate decision, not a default.** A bare `camera` / `камера` / `kamera` is
what a user types, and FastPath leaves it undecided only because no installed app is labelled exactly
that. On a phone whose camera app *is* so labelled, FastPath's app resolution answers first and this
tool never fires — which is **correct behaviour**, not a defect: launching the camera app is what the
user asked for. The tool exists for the intent-action path. `camera app` / `приложение камеры` /
`kamera uygulaması` are the forms that carry a distinguishing token; the bare nouns are included for
`ru`/`tr` where the label is unlikely to match. If `ToolVocabularyReachabilityTest` reddens on any of
these, **escalate with the verb named** — do not reshape until it goes quiet (Task 1, item 3).

**`open_wifi_settings` carries P3 into its descriptor comment**, citing row 18 verbatim, and into the
checklist as a SAFETY line.

### Task 3 — Slice B

`open_battery_settings` · `open_data_usage_settings` · `open_display_settings` · `open_sound_settings`

| Tool | `en` | `ru` | `tr` |
|---|---|---|---|
| `open_battery_settings` | `battery settings`, `battery usage` | `настройки батареи`, `расход батареи` | `pil ayarları`, `pil kullanımı` |
| `open_data_usage_settings` | `data usage`, `mobile data settings` | `расход трафика`, `настройки мобильных данных` | `veri kullanımı`, `mobil veri ayarları` |
| `open_display_settings` | `display settings`, `screen settings` | `настройки экрана`, `настройки дисплея` | `ekran ayarları` |
| `open_sound_settings` | `sound settings`, `volume settings` | `настройки звука`, `настройки громкости` | `ses ayarları` |

`open_battery_settings` carries P4 in its comment. Two of the three least-confident `tr` forms named in
§0.3 (`pil ayarları`, `veri kullanımı`) are in this slice — flag both in the commit message.

### Task 4 — Slice C

`open_location_settings` · `open_notification_settings` · `open_app_info`

| Tool | `en` | `ru` | `tr` |
|---|---|---|---|
| `open_location_settings` | `location settings`, `gps settings` | `настройки геолокации`, `настройки локации` | `konum ayarları` |
| `open_notification_settings` | `notification settings` | `настройки уведомлений` | `bildirim ayarları` |
| `open_app_info` | `app info`, `app details` (+ argument) | `сведения о приложении`, `информация о приложении` (+ argument) | `uygulama bilgisi` (suffix form, + argument) |

Three things are unique to this slice:

**`open_location_settings` carries P2** — `emptyList()` with the sufficiency/necessity qualification in
the row's comment, and a numbered checklist item.

**`open_notification_settings` carries P1** — the `private const val` in the worker, the comment citing
row 25, and the map key `"ACTION_NOTIFICATION_SETTINGS"`. **Do not widen
`ToolPermissionManifestGuardTest`'s regex.**

**`open_app_info` is the only argument-carrying tool of the eleven, and it breaks on R14-37 exactly as
`uninstall_app` did.**
- Declare `ActionArg("app", …)` with `required = true` — the default, but **write it out**, because
  the reason is not tidiness: `ToolMatchPlanner` resolves an argument named `app` **regardless of
  `required`**, so an optional `app` gives `raw = ""` → `resolve("")` → `null` → `NoPlan` for every
  goal forever with the suite green (P0.2/R14-37).
- **Do not declare `app_label`.** `uninstall_app` needs it because its `CONFIRM` card must name both
  what the user said and what will be removed. `open_app_info` is `SAFE`, draws no card, and the
  single-literal path in `line()` renders its one argument. Declaring a second argument would take it
  down `uninstall_app`'s by-name branch for no gain — and would hit A1′ residual (5)/A1″ residual (7),
  the argument-**count** step-line rule.
- **Add the per-descriptor pin**, mirroring R14-35's for `uninstall_app`: a test in
  `Tier0IntentToolSourceTest` asserting `open_app_info`'s `app` argument is `required = true`, with the
  failure message naming the `NoPlan`-forever consequence.
- **Report it as the SECOND consecutive per-descriptor pin.** There is still no generalisation, and
  two instances is the evidence A4′ needs. Say so in the commit message, the ADR section and the
  report — the accumulation *is* the deliverable here.
- The rendered line reads e.g. `App info for com.example.app`, because resolution happens above the
  consent gate and the resolved package is what the plan carries. **Named, not worked around:** for a
  `SAFE` tool this is a legibility limit, not a safety one. Do not add `app_label` to soften it.

---

# Task 5: mutation round

Every new guard, and every guard whose meaning this phase changed, must be **proved falsifiable**. The
block's own recurring finding is seven demonstrated cases of evidence that did not describe what it was
believed to describe — four where strengthening an invariant made its guard unfalsifiable, three where
the guard never was.

Mutations to plant, each observed RED and then reverted:

1. **Mutual shadowing inside the eleven** (P7, the brief's trap 4) — give two of the new entries a
   common trigger text; `every trigger recognises its own sample command` must redden *on that text*.
   This is the one the brief specifically asks to prove by mutation.
2. **The withheld-tool trap** (P6/R14-31) — add a descriptor and omit its `ToolPermissionCatalog` row.
   With the expected lists already extended (step 1 of each slice) this must be RED. **If it is green,
   the method rule failed and the ordering must change**, which is a finding in its own right.
3. **`open_app_info`'s `required` pin** — flip it to `false`; the new pin must redden. Then confirm the
   consequence directly: `ToolMatchPlanner` answers `NoPlan` for a goal that should have planned.
4. **P1's parse-completeness** — replace `Intent(ACTION_NOTIFICATION_SETTINGS)` with the raw string
   literal; `ToolPermissionManifestGuardTest` must redden on the count mismatch, not pass silently.
5. **A false `emptyList()`** — claim one settings tool needs a permission absent from the manifest;
   `ToolRegistryPermissionGuardTest` must redden.
6. **Non-vacuity of the surface scan** — delete one `toolLabelFor` arm; `DoctrineGuardTest` must name
   that id.

**Fixture discipline (rule (a) of the block's own two):** expected values must not share substrings
with each other or with the fallback. **Realistic fixtures are the dangerous ones** — a real package
name usually contains the real label. Where a rendering assertion is involved, choose a label and a
package with no common substring.

**Rule (b): the last action of this task is a gate re-run from a cleared `build/test-results`.** Stale
mutation XML is indistinguishable from a red gate and misleads in both directions.

---

# Task 6: correct the over-claim already committed in `43f2c94`

Four documents say that **nothing** the block built ran on a phone. That is wider than the truth, and
the correction is not cosmetic: an over-claimed deficit is the same class of error as an over-claimed
success. Verified against git, not prose — `ab2d063` (2026-09-16), the Task 13b **smoke round** the
owner authorised, explicitly not acceptance, measured on the SM-A325F:

- **S1** — the shipped `ShortcutToolSource` / `ShortcutCatalog` / `AndroidShortcutQuery`, wired as
  `AgentProvidesModule` wires them: **216 descriptors, 65 packages**, no id collisions, all
  `app_shortcut` / `EXTERNAL` / `SAFE` / `TRANSIENT`, zero blank labels.
- **S3/S4** — `ToolSelector` over that real 216-tool registry, eight texts: authored beats dynamic
  (**≈1 ms**), dynamic candidates **≈64–69 ms**, and three documented declines.
- **S5** — a command driven through the launcher's own UI in `ru-RU`: the shortcut executed via
  `startShortcut`, the step line carried the third-party label, provenance rendered **`APP SHORTCUT ·
  EXTERNAL`**, and «Закрыть» cleared the command buffer and restored the home body.
- **S7/S8** — the HOME role removed: fail-closed to an empty tool set, no crash, authored tools intact.

**The exact wording to adopt, in all four places:** *phases 1–2 received a device reading in a smoke
round that is not an acceptance; **phase 3a — the entire acting set, the `CONFIRM` card, the
`launcher_memory` adapter and every `tr`/`en` trigger — has not run on a phone at all.***

Edit: the ADR's first paragraph (`ai-context/decisions.md:8506`), `CLAUDE.md` (the stage table row
5.5, the Shipped surface bullet, the A1″ debt row), `ai-context/current-status.md` (the header at
lines 9–13 and the A1″ section at 53–62), and the acceptance checklist's preamble. **The sentence that
must survive intact is the one that still holds:** `DEVICE-ACCEPTED` is **absent**, not inapplicable.

---

# Task 7: close

- **ADR** — a **new section inside** «2026-09-19 — Этап 5.5 (A1″)», not a rewrite. Precedent: A1′'s
  device acceptance is a section inside its own ADR. Record: the eleven tools; the split count
  (**sixteen authored, five acting**); the ten pre-flight findings; the second consecutive
  per-descriptor `required` pin and why the generalisation is still A4′'s; the `tr` count going seven →
  **eighteen**; P2's sufficiency/necessity limit; and the guard-list count reaching **fourteen**.
- **`CLAUDE.md`** — stage table, Shipped surface, A1″ debt row, and the `Contract → Owner module` row
  for `Tier0IntentToolSource` (four tools → fifteen).
- **`current-status.md`** — header and the A1″ section.
- **Acceptance checklist** — **append eleven rows, do not rewrite** (§14: Phase 4 covers all sixteen in
  one round). Add: the P3 SAFETY line for `open_wifi_settings`; the P2 item for
  `open_location_settings`; and the Turkish row updated from six to **seventeen** forms (eighteen
  including A1′'s inherited `sayaç ayarla`).
- **Gate** from a cleared `build/test-results`, decomposition derived first, counts read from JUnit
  XML.
- **Ledger** — `.superpowers/sdd/…` is git-ignored; move anything durable into the ADR or this plan
  **before** the phase closes.
- **Propose the commit to the owner.** The agent commits; the agent never pushes.

## STOP condition

Eleven registered and reachable in three locales · gate green from a cleared tree · checklist extended
· ADR extended by a section · `CLAUDE.md` and `current-status.md` synced · commit proposed.

**Report split, never as one number: sixteen authored tools, of which five act.**
