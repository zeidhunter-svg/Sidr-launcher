# A1″ — device acceptance (tool mass, selection, and the first `CONFIRM` tool)

> **RUN 2026-09-20 by the owner on the SM-A325F (build `db1e75d`). Parts A and B passed in full, in
> `ru-RU` and — for the first time in this project — in `tr` and `en`. The block is `CLOSED`; spec §16
> criterion 1, the last open one of nine, is closed. What was actually observed, the seven findings,
> the owner's rulings and what the round did NOT cover are recorded in the section «Приёмка на
> устройстве» at the end of ADR «2026-09-19 — Этап 5.5 (A1″)» in `ai-context/decisions.md` — not here.**
>
> **Four lines of this file were WRONG and are corrected below where they stand (§A2, §A5 ×3).** The
> document erred more often than the code it checked, and three of the four were predictions about what
> the user would *see*, written without a device run. **Rule taken from it: a checklist line predicting
> a specific visible rendering is a hypothesis until it has been run once, and must be written in that
> tone.**
>
> *Original header, kept as the record of what this file claimed before the round:* **NOT RUN. Written
> 2026-09-19 at block close; the owner runs it, not an agent.**
>
> **Why this file exists.** Block A1″ (Этап 5.5) is `CODE-GREEN` through **phase 3b**: gate green at
> **1439 tests / 0 failures / 0 errors** from a run printing `557 actionable tasks: 557 executed`,
> every new guard mutation-proved, ADR written («2026-09-19 — Этап 5.5 (A1″)» in
> `ai-context/decisions.md`, with phase 3b as its last section). It is **not** `DEVICE-ACCEPTED`.
>
> **Said exactly — corrected 2026-09-20, because the earlier wording here ("nothing this block built
> has run on a phone") was wider than the truth, and an under-claimed deficit is the same class of
> error as an over-claimed success.** Verified against git: `ab2d063` (2026-09-16), the Task 13b
> **smoke round** the owner authorised and which is explicitly **not** an acceptance, ran the shipped
> phase-1/2 code on the SM-A325F — 216 shortcut descriptors from 65 packages with no id collisions,
> `ToolSelector` over that real registry (authored beats dynamic in ≈1 ms, dynamic candidates ≈64–69
> ms, three documented declines), one command driven through the launcher's own UI in `ru-RU` where the
> shortcut executed via `LauncherApps.startShortcut`, the step line carried the third-party label,
> provenance rendered `APP SHORTCUT · EXTERNAL` and «Закрыть» cleared the command buffer, and
> fail-closed behaviour with the HOME role removed. So: **phases 1–2 received a device reading in a
> smoke round that is not an acceptance; phase 3a — the entire acting set, the `CONFIRM` card, the
> `launcher_memory` adapter and every `tr`/`en` trigger — has not run on a phone at all, and neither
> has phase 3b.** The Task 13/13b measurement round measured **Android premises**, not the product.
>
> Unlike A0.5 (whose device acceptance was marked *not applicable*, because that block changed nothing
> on the phone), A1″ changes exactly what the user can reach. `DEVICE-ACCEPTED` here is **ABSENT, not
> inapplicable** — an ordinary deficit.
>
> **Extended 2026-09-20 with phase 3b:** §A9 adds eleven numbered rows, one per navigating tool; the
> SAFETY section gains a fourth rule for `open_wifi_settings`; and §A6's Turkish row went from six
> forms to **23**. Spec §14 has this round cover **all** the block's tools in one pass.
>
> **Owner-run means owner-run.** An `adb`/`uiautomator` pass driven by an agent is evidence, not
> acceptance (Этап 0.5's status vocabulary). Part C below is exactly that kind of evidence and clears
> **nothing** — the same three-way split A0's re-check and A1′'s acceptance used, and half the value of
> the file: **A** — what the owner must look at with their own eyes; **B** — no-regression re-runs on
> paths the block did not change; **C** — what only an agent can show instrumentally.
>
> Full record of what shipped: ADR «2026-09-19 — Этап 5.5 (A1″)». Spec:
> [2026-09-12-a1-second-tool-mass-and-selection-design.md](../specs/2026-09-12-a1-second-tool-mass-and-selection-design.md).
> Plans: [phases 0–2](2026-09-12-a1-second-tool-mass-and-selection.md),
> [phase 3a](2026-09-18-a1-phase3a-acting-tools.md).

---

## SAFETY — read before touching the device

This is the first acceptance round in the project's history where a checklist item can **delete an
app**. Three rules, and they are not advisory:

1. **NEVER confirm the Android uninstall dialog** during this round. Sidr's `CONFIRM` card is the
   *first* gate; the OS's own dialog is the *second*. The item below is satisfied when the OS dialog
   **appears** — the correct action then is **Cancel**. Nothing on this phone needs to be removed to
   prove that uninstall works.
2. **NEVER run `connectedAndroidTest` or any Gradle task that targets the device.** AGP uninstalls both
   APKs and destroys the app's data, which is the accepted schema-4 database. Drive `am`/`adb shell`
   by hand if anything instrumental is needed.
3. **NEVER `adb uninstall com.sidr.launcher`.** Install the new debug build **over** the existing one.
4. **`open_wifi_settings` — DO NOT TAP OK ON THE MODAL.** Measured on this phone (row 18 of the
   measurements file): the Wi-Fi screen opened **with «Отключить мобильную точку доступа? … Отмена /
   OK» already raised**, because the hotspot was on — **and the owner's laptop is tethered through this
   phone.** Tapping OK cuts the laptop's internet. The item in §A9 is satisfied when the screen
   **appears**; dismiss with **Отмена**. The tool itself is `SAFE` and performed no act: the modal is
   the responder's and the tap would be the user's — which is exactly why this is a SAFETY rule and not
   an ordinary numbered item.

---

## Device state to establish first

Same device as every prior acceptance round, for the same reason — comparing against what is already
accepted:

- SM-A325F, Android 13. **Primary locale `ru-RU`**, as in every previous round. `en` and `tr` are not
  optional extras this time — see §A6, which is the one item this block cannot close without them.
- Install the new debug build **over** the existing one. **No schema change in this block**
  (`schemas/…/4.json` untouched, `identityHash` unchanged), so no migration runs and none should.
- **Sidr must hold the `android.app.role.HOME` role** for §A5 to be checkable at all. Shortcut host
  access is a *role*, not a permission: with the role held elsewhere the shortcut catalog is empty and
  the `app_shortcut` adapter correctly advertises **nothing**. Both states are worth one look, and §A5
  asks for both.
- **Two new install-time permissions** ship in this build, both `protectionLevel: normal`, both granted
  silently: `com.android.alarm.permission.SET_ALARM` (already present since A1′) and
  `android.permission.REQUEST_DELETE_PACKAGES` (new, owner decision 2026-09-18). Neither prompts.
  Worth confirming once that the install itself raised no permission screen.
- Reading the agent tables after a run — **one command**:
  ```
  tools/device/pull-agent-db.sh
  ```
  It prints `agent_session`, `agent_plan_step`, `agent_trace_event` and the at-rest row counts. Pass an
  output directory as `$1` to keep the pulled files, and a query as `$2` to ask something else.

  **Do not read the database by copying the main `.db` file on its own.** Room runs it in WAL mode, so
  a committed write lives in the `-wal` and reaches the main file only at a *checkpoint*. The main file
  alone is the database **as of the last checkpoint** — on 2026-09-10 that was three hours and four
  state transitions stale, and it reported a session the engine had correctly deleted as still present.
  It fails the other way too: it can show all three `agent_*` tables empty while a row holding
  `agent_session.goal_text` — the user's raw command — is on disk, which is a **false green on the
  privacy guarantee**. `DeviceDatabaseReadGuardTest` (`:app`) holds this mechanically so a checklist
  cannot quietly reintroduce the truncated read. This device also exposes **no** `sqlite3` through
  `run-as` (measured 2026-09-05), so the script's own copy of the SDK binary is the one to use.

**Risk levels in this build, so nothing below reads as a missed gate.** Updated 2026-09-20 for phase
3b and read off `DoctrineGuardTest.declaredRisk`, which pins risk over the production federation:
**eighteen of the twenty authored tools are `SAFE`** — `requiresConsent(SAFE) == false`, so their plans
do **not** stop at `AwaitingConsent`; the step runs immediately and the session lands on the completed
surface with no confirm tap. That is expected, and it covers all eleven navigating tools of §A9.
**Two are `CONFIRM`: `uninstall_app`** — the first tool the agent's own vocabulary mints at that level,
and the first to stop the loop on a real product path — **and `play_store_search`**, projected from
`ActionCatalog` and `CONFIRM` since A0, which is the consent stop in A0's launch→store plan. (This
paragraph previously read "eight of the nine authored tools are `SAFE`"; with `play_store_search`
pinned `CONFIRM`, that was seven of nine even before phase 3b.)

---

## Part A — the owner must look, because this block changes what the user can reach

### A1. `uninstall_app` — the consent card names what will be removed, not what was typed

**This is the most important item in the file.** It is the first `CONFIRM` tool of the agentic track,
and the whole of owner condition 2 (2026-09-18) is that a human sees **both** the app label and the
package before tapping.

Type into the command bar (the same entry surface Task 15 used for A0's «открой …»), naming an app
that is actually installed:

| Locale | Command |
|---|---|
| `ru` | «удали приложение telegram» (and the shorter «удали telegram») |
| `en` | "uninstall telegram" (and "remove app telegram") |
| `tr` | "telegram uygulamasını kaldır" (and "telegram kaldır") |

- [ ] The plan **stops** and a consent card is drawn. It does **not** run.
- [ ] **The card names BOTH the label and the package.** In `ru` it should read, with the real package
      of whatever app was named:
      «Следующее действие Sidr: **Удалить Telegram (org.telegram.messenger)**. Ничего не выполнится,
      пока вы не подтвердите.»
      `en`: "Sidr will do this next: **Remove Telegram (org.telegram.messenger)**. Nothing runs until
      you confirm."
      `tr`: "Sidr sırada şunu yapacak: **Telegram uygulamasını kaldır (org.telegram.messenger)**. Siz
      onaylamadan hiçbir şey çalışmaz."
      **It must NOT read back the words that were typed.** A card reading «Удалить удали приложение
      telegram» is the exact failure this condition exists to prevent, and it is what the build did
      before Task 11.
- [ ] Beneath the step line, the provenance line reads **`SYSTEM INTENT · EXTERNAL`** — locked English
      in every locale, on purpose (Class A).
- [ ] **Tapping «Закрыть» runs nothing** (that is the cancel affordance's actual wording —
      `launcher_agent_cancel`), and the launcher returns to the home body with the command bar cleared
      — not stuck in search mode with the text still in it. (That specific regression was found and
      fixed during A1′'s acceptance; it is re-checked here because every recognised tool command now
      reaches this surface.)
- [ ] **Tapping «Подтвердить» makes the Android uninstall dialog appear.** → **CANCEL IT.** The item is
      satisfied by the dialog *appearing*; Sidr removes nothing itself, the OS asks separately, and
      this round does not need any app actually deleted.
- [ ] After cancelling at the OS dialog, the launcher is healthy and the session has not wedged.

### A2. `uninstall_app` refuses to remove Sidr, and the refusal arrives AFTER consent — look at this deliberately

Type «удали приложение sidr launcher». **CORRECTED 2026-09-20 during the run:** this line used to
read «удали sidr», which resolves to **nothing** — the app's label is «Sidr Launcher»
(`strings_locked.xml`, `translatable="false"`) and `AppTargetResolver` matches the **exact**
normalized label and never guesses. The original line therefore tested the fall-through, not the
refusal. The product was right; the checklist named a target that does not exist on the phone.

- [ ] The name resolves to **our own package**, a `CONFIRM` card is drawn **naming it**, and only after
      confirming does the step fail. **This is spec-compliant, not a defect** — condition 4 sits exactly
      there — and it is pinned by a test as current behaviour.
- [ ] **Owner decision required, and it is the reason this is a numbered item rather than a footnote:**
      the user consents to something that cannot happen and then gets a generic failure. Moving the
      refusal *above* the gate (so no card is ever drawn for a target we will not act on — Task 6b's
      own argument) is **the owner's call**, not an agent's. Record the verdict here or in the ADR.
      Ruling reference: R14-38.

### A3. An unresolved app name declines — and sends the typed text to the cloud model

Type «удали приложение панголин» (a name matching no installed app), **with a cloud provider
configured and the network on**.

- [ ] **No consent card is drawn.** The planner declines rather than guessing, which is the property
      that keeps a fuzzy resolution from reaching a `CONFIRM` gate with the wrong target.
- [ ] **Then look at what happens next, because this is the doctrinal residual R14-39 and it is
      visible from the outside:** a declined resolution becomes `NoPlan` at routing step 2b, and
      `NoPlan` at 2b falls through to the **cloud model** — so the raw command text leaves the device
      on a goal a registered tool had already matched deterministically. This is **not** a widening of
      `OutboundContextPolicy` (it is the same text the model path would have received had nothing
      matched), but the decision to send it is made by an unresolved name rather than by the routing
      rules. **Addressed to A4′.** The item here is only to see it once with the owner's own eyes and
      decide whether it is acceptable until then.
- [ ] Re-run the same command with `localOnlyMode` on (or the network off) and confirm the honest
      local answer instead — never "Unknown command".

### A4. `set_alarm` — it creates the alarm, and it is already enabled

| Locale | Command |
|---|---|
| `ru` | «поставь будильник на 7:30» |
| `en` | "set an alarm for 7:30" |
| `tr` | "07:30 alarm ayarla" |

- [ ] The system clock app opens and the alarm **exists and is already enabled** — there is no
      prefilled form to submit. Measured on this device (rows 13/29): `ACTION_SET_ALARM` creates it
      enabled. A running/enabled alarm is the **expected** result, not a defect; `SAFE` rests on four
      other properties (reversible in one tap, immediately visible, provenance disclosed, nothing
      leaves the device).
- [ ] The time matches the command.
- [ ] The agent surface shows a **one-step, completed** plan reading «Поставить будильник на 7:30» /
      "Set an alarm for 7:30" / "07:30 için alarm kur", with `SYSTEM INTENT · EXTERNAL` beneath it.
- [ ] **Delete the alarm afterwards** so the phone is returned as found.

### A5. The three `launcher_memory` tools — the agent changes its own memory on request

This is the fourth adapter and the first tool level whose effect is **`LOCAL`**: nothing leaves the
launcher, and everything it does is undoable from Settings → Память.

| # | Locale | Command | Expected |
|---|---|---|---|
| 1 | `ru` | «называй telegram **как** телега» | alias created |
| 1 | `en` | "call telegram **as** tg" | alias created |
| 1 | `tr` | "telegram için tg kullan" | alias created |
| 2 | `ru` | «забудь название телега» | alias removed |
| 2 | `tr` | "telega adını unut" | alias removed |
| 3 | `ru` | «забудь выбор для телега» | learned choice removed |
| 3 | `tr` | "telega için seçimi unut" | learned choice removed |

- [ ] Each runs **without a consent card** (all three are `SAFE`) and lands on a completed one-step plan.
- [ ] The step line names the arguments, not the typed text: «Называть «telegram» «телега»» / «Забыть
      алиас «телега»» / «Забыть выученный выбор для «телега»».
- [x] **CORRECTED 2026-09-20: there is NO provenance line for these three, and that is correct.**
      `provenanceLabelFor` opens with `if (effect != ToolEffect.EXTERNAL) return null`, and every
      `launcher_memory` tool is `LOCAL`, so no chip is drawn — its KDoc argues that claiming a
      boundary crossing that did not happen would be `DOC-ILM-2` lying in the opposite direction.
      The string `launcher_tool_level_launcher_memory` («LAUNCHER MEMORY · LOCAL») ships **locked and
      unreachable**, mapped only for totality — which is where this line's mistaken demand came from.
- [ ] **Open Settings → Память and confirm the effect is really there** — the alias appears in
      «Псевдонимы» after command 1 and is gone after command 2. A memory tool that reports success
      while storing nothing is the specific silent failure this set was written against (an alias
      stored under a normalized phrase, deleted with a raw one, finds nothing and reports success).
- [x] **CORRECTED 2026-09-20: the bare phrase «телега» opens it; «открой телега» does NOT, and that
      is the contract, not a defect.** `ResolveCommandWithAliasUseCase` fires only on
      `CommandOutcome.Unknown` and looks up `store.find(normalize(rawInput))` over the **whole**
      input — «псевдонимы заполняют только пробелы», as the Settings copy already says. «открой
      телега» is *decided* by FastPath (verb understood, no such app) → `NoAppFound` → A0's
      launch→store plan. Verified on device: bare «телега» opened the app, and «забудь название
      телега» then stopped it opening.

### A6. THE TWENTY-THREE TURKISH TRIGGERS — none has ever been judged by a native speaker

**This is a numbered acceptance item, not a sentence in an ADR, and "assumed fine" is not an acceptable
answer to it** (precedent: §15 of the A1′ checklist, where exactly this row was answered honestly as
"not judged"). Before this block the unjudged set was **one** trigger inherited from A1′. After phase
3a it was **seven**; **phase 3b added sixteen more `tr` forms across eleven tools**, so it is now
**23** by this table's convention (`için` … `kullan` counted as one item) and **24** counted as raw
distinct trigger strings. The vocabulary table holds **27** `tr` forms in all — the other three
(`zamanlayıcı ayarla`, `sistem ayarları`, `android ayarları`) predate this accounting.

| # | `tr` trigger | Tool | Shape | Judged? |
|---|---|---|---|---|
| 1 | `alarm ayarla` | `set_alarm` | suffix | [ ] |
| 2 | `uygulamasını kaldır` | `uninstall_app` | suffix | [ ] |
| 3 | `kaldır` | `uninstall_app` | suffix (bare) | [ ] |
| 4 | `için` … `kullan` | `set_app_alias` | infix + suffix | [ ] |
| 5 | `adını unut` | `forget_app_alias` | suffix | [ ] |
| 6 | `için seçimi unut` | `forget_learned_choice` | suffix | [ ] |
| 7 | `sayaç ayarla` | `set_timer` | suffix (inherited from A1′, still open) | [ ] |
| 8 | `alarmlar` | `show_alarms` | prefix | [ ] |
| 9 | `alarmlarım` | `show_alarms` | prefix | [ ] |
| 10 | `kamera` | `open_camera` | prefix | [ ] |
| 11 | `kamera uygulaması` | `open_camera` | prefix | [ ] |
| 12 | `wi-fi ayarları` | `open_wifi_settings` | prefix | [ ] |
| 13 | `kablosuz ayarları` | `open_wifi_settings` | prefix | [ ] |
| 14 | `bluetooth ayarları` | `open_bluetooth_settings` | prefix | [ ] |
| 15 | **`pil ayarları`** | `open_battery_settings` | prefix — **least confident** | [ ] |
| 16 | `pil kullanımı` | `open_battery_settings` | prefix | [ ] |
| 17 | **`veri kullanımı`** | `open_data_usage_settings` | prefix — **least confident** | [ ] |
| 18 | `mobil veri ayarları` | `open_data_usage_settings` | prefix | [ ] |
| 19 | `ekran ayarları` | `open_display_settings` | prefix | [ ] |
| 20 | `ses ayarları` | `open_sound_settings` | prefix | [ ] |
| 21 | **`konum ayarları`** | `open_location_settings` | prefix — **least confident** | [ ] |
| 22 | **`bildirim ayarları`** | `open_notification_settings` | prefix — **least confident** | [ ] |
| 23 | **`uygulama bilgisi`** | `open_app_info` | suffix (the only argument-carrying one of the eleven) — **least confident** | [ ] |

- [ ] **The five forms marked least-confident were named in advance, before the build** (same
      discipline R14-42 used for `için`/`kullan`): `pil ayarları`, `veri kullanımı`,
      `bildirim ayarları`, `konum ayarları`, `uygulama bilgisi`. They are literal renderings; whether
      One UI's own Turkish labels those screens that way is exactly the question no guard and no agent
      can answer. `pil` is the everyday word for battery, but Samsung's own screen on this device is
      «Действия с аккумулятором» in `ru`.
- [ ] **Switch the device to `tr` and type each one.** No `tr` trigger in this project has *ever* run
      on a phone; every device round to date has been `ru-RU` only.
- [ ] **#4 (`için`/`kullan`) is the least confident form, and its limitation is recorded rather than
      hidden** (ruling R14-42). The finding behind it is real and is the useful part: the literal
      Turkish renderings of "as" — `olarak` / `diye` — are **postpositions attaching to the second
      argument**, immediately before the verb, so they do not occupy a slot *between* the two arguments
      and cannot fit the mechanism's fixed `<A> <infix> <B> <suffix>` order. `için` ("for") does follow
      the noun it governs, which is A's position, so it fits literally. The implementer's own read:
      "comprehensible, if more instructive-register than fully idiomatic."
- [ ] **#3 (`kaldır`, bare) deserves its own look.** It is the shortest trigger in the whole vocabulary
      and the only bare one attached to a `CONFIRM` tool. `ToolVocabularyReachabilityTest` proves it is
      reachable and un-shadowed by FastPath — that is a mechanical fact, **not** a judgement about
      whether a Turkish speaker typing `kaldır` means "uninstall".
- [ ] **Record the verdict per row.** "Not judged, no Turkish speaker available" is an honest answer and
      the one A1′ gave. "Assumed fine" is not. If any verdict is "drop it", that is a code change in
      `ToolVocabulary.kt`'s `tr` sets, not a documentation edit.
- [ ] While in `tr`, also read the step lines from §A1/§A4/§A5 and the eleven from §A9 — they ship in
      the same commits and have never been read on a device either.
- [ ] Repeat the same pass in `en`. `en` has never run on a phone either.

### A7. The `app_shortcut` adapter — a tool whose name is data

Measured on this device during Task 13: **205 shortcut tools from 65 packages** when the `HOME` role is
held.

- [ ] **With Sidr as the home app:** a command naming both an app and one of its shortcuts — e.g.
      «telegram новое сообщение» / "telegram new message" — opens that shortcut. The provenance line
      reads **`APP SHORTCUT · EXTERNAL`**.
- [ ] **The app name is required.** A bare shortcut name — "new message" with no app named — must
      **not** match, even if exactly one candidate exists. A shortcut launch performs an effect, so a
      false positive has to be structurally unlikely, not just statistically unlikely.
- [ ] **Ambiguity declines rather than guesses.** Two shortcuts of equal strength produce nothing, not
      a pick by insertion order.
- [ ] **An authored trigger cannot be shadowed by a third-party label.** «системные настройки» must
      still reach `open_system_settings` even if some installed app publishes a shortcut with a similar
      name.
- [ ] **Leftover words decline.** "system settings for my car" must **not** launch anything (Task 10b's
      rule, owner-approved).
- [ ] **Hand the `HOME` role to another launcher and repeat one shortcut command.** The catalog is empty,
      the adapter advertises **nothing**, and the launcher does **not** crash — the tool simply is not
      offered. This is the required degradation and the only on-device way to see it. **Hand the role
      back afterwards.**

### A8. Read the new strings once, in all three locales

The new user-visible strings this block ships — the uninstall step line, the three memory step lines,
the shortcut step line, and the `LAUNCHER MEMORY · LOCAL` / `APP SHORTCUT · EXTERNAL` provenance tokens.

- [ ] Nothing is untranslated, truncated, or reading as a developer string.
- [ ] The provenance tokens stay **English in every locale** — that is deliberate (Class A,
      `translatable="false"`), for the same reason A1′ locked `SYSTEM INTENT · EXTERNAL`: a translated
      provenance token would say a different thing in each language about the same mechanism.
- [ ] **No Class B key was added by this block and no signature needs re-signing.** Verified at the
      gate's own logic (`app/build.gradle.kts:186` — a key is Class B only if `translatable != "false"`),
      not at the file. If the owner is asked to re-sign anything during this round, that is a defect to
      report, not a step to perform.

### A9. The eleven navigating tools (phase 3b) — each opens a screen and performs no act

Added 2026-09-20 when phase 3b closed `CODE-GREEN`. Spec §14 has this round cover **all** the block's
tools in one pass, so these rows sit beside §A1–§A8 rather than in a round of their own. All eleven are
`system_intent` / `EXTERNAL` / `SAFE` / `TRANSIENT`: `requiresConsent(SAFE) == false`, so **no confirm
card appears** — the step runs immediately and the session lands on the completed surface. That is
expected, and it is the whole of their `SAFE`: these tools perform **no act at all**, so there is
nothing to reverse. What the *landed screen* then offers is the user's business, not the tool's — which
is why two of the rows below carry warnings that do **not** change the risk level.

Type each command in `ru-RU` first (the locale every previous round used). Each row is satisfied when
the named screen opens, the step line reads as a sentence, and the provenance line under the step reads
**`SYSTEM INTENT · EXTERNAL`**.

- [ ] **1. `show_alarms`** — «будильники» / «мои будильники». The clock's **alarm list** opens; no alarm
      is created, changed or deleted. Step line: «Показать будильники».
- [ ] **2. `open_camera`** — «камера» / «приложение камеры». The viewfinder opens. **Known and named:
      the sensor goes live with no further tap** (measured, row 17) — the app holds **no** `CAMERA`
      permission, so this is the camera app's own session, not ours. Step line: «Открыть камеру».
      If an installed app is labelled exactly «Камера», FastPath answering first is **correct**
      behaviour, not a defect.
- [ ] **3. `open_wifi_settings`** — «настройки wi-fi» / «настройки вайфая». **READ SAFETY RULE 4 FIRST.**
      On this phone the screen opened with «Отключить мобильную точку доступа? … Отмена / OK» **already
      raised**, because the hotspot was on, and the laptop is tethered through this phone. **The row is
      satisfied when the screen appears; dismiss with Отмена. Do not tap OK.**
- [ ] **4. `open_bluetooth_settings`** — «настройки bluetooth» / «настройки блютуз». The screen opens;
      nothing is paired, unpaired or toggled. The app holds no `BLUETOOTH*` permission at all.
- [ ] **5. `open_battery_settings`** — «настройки батареи» / «расход батареи». The screen opens — and on
      this device the responder is **Samsung Device Care** (`com.samsung.android.lool/…
      PowerUsageSummary`), not `com.android.settings`. **That is a ROM-dependent premise measured on one
      phone**: on a device where `ACTION_POWER_USAGE_SUMMARY` resolves to nothing, the tool returns
      `Failed` (`ActivityNotFoundException` is already handled) rather than crashing. If it fails here,
      say so — the premise, not the code, is what would be wrong.
- [ ] **6. `open_data_usage_settings`** — «расход трафика» / «настройки мобильных данных». The screen
      opens. The landed screen carries a **mobile-data toggle**; the tool did not touch it.
- [ ] **7. `open_display_settings`** — «настройки экрана» / «настройки дисплея». The screen opens; state
      unchanged.
- [ ] **8. `open_sound_settings`** — «настройки звука» / «настройки громкости». The screen opens; state
      unchanged.
- [ ] **9. `open_location_settings`** — «настройки геолокации» / «настройки локации». The screen opens.
      **This row is also the P2 item below — read it before ticking.**
- [ ] **10. `open_notification_settings`** — «настройки уведомлений». The screen opens. Note for the
      record: **the platform has no `Settings.ACTION_NOTIFICATION_SETTINGS` constant** (verified with
      `javap` over `android.jar`), so the action string is ours, declared as a private constant with the
      measurement row named beside it. If this one screen fails to open while the other ten work, that
      string is the first suspect.
- [ ] **11. `open_app_info`** — «сведения о приложении telegram» / «информация о приложении <app>». The
      **App info** screen of that app opens. This is the only argument-carrying tool of the eleven —
      see the two notes below.

**Numbered item — P2: `open_location_settings` ships claiming NO permission, on a necessity that is
UNMEASURED.** The measurements exclude only the *grant*: `ACCESS_FINE_LOCATION` was `granted=false`
when the screen opened (rows 29/30) and the process confirmed it from inside (row 33). What is **not**
excluded is the *declaration* — the prayer feature already declares that permission in the same
manifest, so this build cannot tell "needs nothing" from "needs a declaration that happens to be
there". The row ships `emptyList()` deliberately: declaring `ACCESS_FINE_LOCATION` would make
`Tier0IntentToolSource.available()` **withhold the tool on this very phone** (it filters on
`isGranted`, and row 33 measured DENIED), registering it nowhere while every equality test stayed
green.

- [ ] **If the location screen fails to open, the row is wrong** — report it rather than retrying. On a
      build where the prayer feature's declaration is absent, this is the tool that would break first.
      **This is a named limit, not a closed question.**

**Note — `open_app_info` lands one tap from «Удалить».** Measured, row 15. The tool is still `SAFE`: it
opened a screen and performed no act, exactly like the other ten; the uninstall button belongs to the
system screen and the tap would be the user's. It is written here so that a reviewer who sees it does
not read it as a missed gate. Also measured: a package with **no launcher activity** is not filtered
out, so the screen can open for something the user cannot launch.

- [ ] **Its step line renders the RESOLVED PACKAGE, not the word typed** — «Сведения о приложении
      org.telegram.messenger», not «…telegram». Resolution happens **above** the consent gate (fork
      F5), and this descriptor declares `app` without the optional `app_label`. For a `SAFE` tool this
      is a **legibility** limit, not a safety one, and it was deliberately not softened. Judge whether
      it reads acceptably; if it does not, that is a real finding.
- [ ] **A name that resolves to nothing plans nothing** — «сведения о приложении <бессмыслица>» must
      not open App info for something else. Note that this is the same path as §A3: an unresolved name
      produces `NoPlan` at routing step 2b, and `NoPlan` there **falls through to the cloud model**, so
      the typed text leaves the device if a provider is configured and the device is online.

**After the eleven, one look at the whole surface:**

- [ ] **No tool was shadowed by another.** Each of the eleven reached its **own** tool — nothing landed
      on `open_system_settings` instead. Every trigger deliberately carries a distinguishing token and
      none is a bare settings synonym, but a device is where a mistyped expectation actually shows.
- [ ] **The step lines and provenance read correctly in `en` and `tr` too** (§A6, §A8) — neither locale
      has ever run on a phone.

---

## Part B — the owner re-runs to confirm nothing regressed

Unchanged in behaviour by this block, but the block touched the engine and the surface they run
through, and "unchanged" is worth a minute of device time each.

- [ ] **A0's two-step plan still runs.** «открой <не установленное>» still produces launch → store,
      still stops at the risk transition before the store step, still completes the same way.
- [ ] **A1′'s two tools still work.** «поставь таймер на 5 минут» opens a running timer;
      «системные настройки» opens Android settings. Both `SAFE`, both with
      `SYSTEM INTENT · EXTERNAL` beneath.
- [ ] **FastPath is untouched.** An installed app still opens on «открой <установленное>» without
      reaching the agent surface at all. In particular, `"alarm kur"` must still be a Play Store
      search, not an alarm — that collision is why `alarm kur` was deliberately **excluded** from the
      `tr` trigger set.
- [ ] **Cascade delete still works.** Cancel mid-plan, then read with `tools/device/pull-agent-db.sh` —
      the three `agent_*` tables are empty afterwards. **Read with the script**, for the reason stated
      at the top of this file; this is the exact item the 2026-09-10 run failed, and it failed on the
      read rather than on the engine.
- [ ] **`adb shell am force-stop com.sidr.launcher`** during a step, then relaunch — no stuck
      `AwaitingConsent`, no orphaned row, the launcher is healthy. **Do this on a `SAFE` tool**, not on
      `uninstall_app`.
- [ ] **Assistant / BYOK chat** still streams normally — confirms none of the four adapters reaches
      outside the agent surface.
- [ ] **Leaving the agent surface** («Закрыть») clears the command buffer and returns the home body.

---

## Part C — agent-verifiable evidence, which does **not** clear anything

Recorded so a later session does not mistake "hard to check on device" for "unchecked". None of it is
acceptance.

### C1. The four guard surfaces that grew during this block

`ToolWorkerCallSiteGuardTest`'s holder floor (five → six), `DoctrineGuardTest`'s `declaredRisk` map and
adapter/level assertions, `ToolRegistryPermissionGuardTest`'s registry-keyed totality, and
`Tier0IntentToolSourceTest`'s per-id risk map. Each was extended and mutation-proved in the suite. **The
count of lists that must move together when a tool is added went five → six → seven → eight inside two
sessions, and no KDoc states the true number because it keeps moving** (R14-32/33/36/41). The real
closure is spec §8.2's registry-keyed guard applied to *workers* as well as *permissions* — explicitly
not this block's. There is no on-device signature for a scan gap.

### C2. The engine's exception containment

`AgentExecutor.perform`'s single `toolExecutor.invoke` call now has a `try` (spec §8.1), so a throwing
worker becomes `Failed` rather than killing the home-screen process. Reproducing the *broken* state
needs a build with the fix removed; the *fixed* state is what every item in Part A already exercises by
not crashing. This closes A1′'s residual (8) and is covered by a deliberately throwing fake.

### C3. `R14-37` — the optional-`app` mirror, not producible on device

`ToolMatchPlanner` resolves an argument named `app` regardless of `required`. A future descriptor
declaring `app` as *optional* would get `NoPlan` for every goal, forever, with the suite green. No tool
shipped today declares it optional, so there is nothing to observe on a phone. Addressed to A4′/A1″.

### C4. The `DURABLE_EFFECT` / `RISK_LEVEL` distinction

Mutation M1a proved the consent gate distinguishes **why** it fired, not merely **that** it fired:
flipping `uninstall_app` to `SAFE` while keeping `DURABLE` reddened the trace assertion with
`reason=DURABLE_EFFECT` where `RISK_LEVEL` was expected. On the device both produce the same card, so
the distinction has no on-device signature — it is a trace-fidelity property, held in the suite.

### C5. What no build on this device can show

- A `SAFE → CONFIRM` change on an already-registered tool: needs two builds. Pinned in
  `DoctrineGuardTest.declaredRisk` over the production federation (new in this block's Phase 0).
- The `core/testing/src/main/java` call-site blind spot: a scan gap either exists in the source tree or
  it does not.
- `RouteCommandUseCase`'s fail-open class beyond the fixed instances: needs a disk-full or corrupt-row
  store failure. Addressed to A4′.
- SQLite `secure_delete` at rest — a deleted session's raw `goal_text` was readable out of the on-disk
  file image during A1′'s acceptance. At the SQL level the guarantee holds; changing journal mode or
  `secure_delete` on an accepted schema-4 database is an **owner decision**, unchanged by this block.

---

## Closing this file

When the run is done:

1. **Record what was observed** — not what was expected — in the ADR «2026-09-19 — Этап 5.5 (A1″)» in
   `ai-context/decisions.md`, as a § «Приёмка на устройстве» section, in the shape A1′'s ADR takes.
   Findings the run produces are part of the record, including ones that turn out to be measurement
   faults rather than product defects (A1′'s most expensive error of its run was its own read command).
2. **Update `CLAUDE.md` and `ai-context/current-status.md`**: A1″ moves `CODE-GREEN` → `DEVICE-ACCEPTED`,
   and to `CLOSED` only if every residual is **named** rather than cleared. `CLOSED` is not a zero-debt
   claim (precedent DS-6B).
3. **§A6's verdicts are the row this file exists for.** Whatever they are, write them down per trigger.
4. **§A2 needs an owner ruling**, not just an observation.
5. **Part C cleared nothing** — say so explicitly in whatever is written.
6. **Updated 2026-09-20 — phase 3b is no longer ahead; it is in this file.** The eleven navigating
   tools shipped `CODE-GREEN` at gate 1439 and are §A9 above, so **this round is the whole block**:
   §16 criterion 1 (≥15 tools, ≥3 levels, reachable on the owner's phone, accepted by the owner) is
   now open for **exactly one** reason — this run has not happened. Report the count split, never as
   one figure: **A1″ built sixteen authored tools, of which five act**, and the federation holds
   **twenty** authored tools on three levels plus the dynamic `app_shortcut` family on a fourth.
