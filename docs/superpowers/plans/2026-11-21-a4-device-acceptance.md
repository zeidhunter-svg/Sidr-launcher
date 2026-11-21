# A4′ — device acceptance (the runtime, phases 0–4, one round)

> **NOT RUN. Written 2026-11-21, before the round; the owner runs it, not an agent.**
>
> **Why this file exists.** Block A4′ (Этап 6) ran five phases — 0, 1, 2, 3a, 3b, 4 — under the owner's
> decision of 2026-09-20: **one acceptance at the block's end, no device round per phase**. Every phase is
> `CODE-GREEN` (last boundary gate **1912 / 0 / 0**, `557 executed`, after phase 4's T8 and signatures);
> **none of it has been seen on a phone.** This file is that one round. `DEVICE-ACCEPTED` here is
> **ABSENT, not inapplicable** — A4′ changes what the user sees on nearly every command that misses FastPath.
>
> **Every line below that predicts a visible rendering is a hypothesis** (rule from A1″'s acceptance: four
> lines of that checklist were wrong, all predictions written without a run). Where a line is wrong, the
> finding is the line *or* the code — write down what you saw, and the ruling is yours.
>
> **Owner-run means owner-run.** Part C is the agent's half and clears **nothing** (Этап 0.5).
>
> **Sources** (the hypotheses are collected from, not copied over, these — if this file and a source
> disagree, the source's *execution* note wins over its plan line):
> - phase 0 — `§HANDOFF` archive «Фаза 0 A4′», «Гипотезы для чеклиста приёмки блока»;
> - phase 1 — plan [2026-09-26-a4-phase1-fastpath-overflow.md](2026-09-26-a4-phase1-fastpath-overflow.md) §0.6 + archive «Фаза 1 A4′»;
> - phase 2 — archive «Фаза 2 A4′», «Гипотезы…»;
> - phase 3a — plan [2026-10-03-a4-phase3a-bridge-first-slice.md](2026-10-03-a4-phase3a-bridge-first-slice.md) §0.7 + archive «Фаза 3a A4′» («Исполнение добавило»);
> - phase 3b — plan [2026-11-07-a4-phase3b-sorts-forms-multistep.md](2026-11-07-a4-phase3b-sorts-forms-multistep.md) §0.7 + archive «Фаза 3b A4′» (E6-R1, «Исполнение добавило», item 10 «пункты карточек»);
> - phase 4 — plan [2026-11-18-a4-phase4-learned-plan-cache.md](2026-11-18-a4-phase4-learned-plan-cache.md) §0.7 + archive «Фаза 4 A4′» («Исполнение добавило»), then T8 (`bb4656fa`) and the re-signed local-only line (`072889ca`), both later than that plan.
>
> Spec: [2026-09-21-a4-runtime-design.md](../specs/2026-09-21-a4-runtime-design.md). Block ADR A4′ is written
> **after** this round, from the phase records and from what this round found.

---

## SAFETY — read before touching the device

Rules 1–4 of the A1″ round stand unchanged ([2026-09-19-a1-second-device-acceptance.md](2026-09-19-a1-second-device-acceptance.md) § SAFETY). In short:

1. **NEVER confirm the Android uninstall dialog.** Every `uninstall_app` item is satisfied when the OS
   dialog **appears**; answer **Отмена**. This round has more uninstall items than A1″ (§A6, §A9) — the
   rule applies to each.
2. **No agent-driven Gradle task against this phone** (`guard-bash.sh` refuses it). The one exception is
   the owner's own `MigrationTest` run, typed by the owner, **after** §A10's live-upgrade items — see §A10.
3. **Install over, uninstall only as a fallback** (owner decision 2026-11-21: the phone is experimental,
   Sidr's data on it is not needed). Installing over is what makes §A10 a real v4→v5 migration on live
   data; a clean install creates schema 5 directly and that item then reads «not produced». If
   `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall and install clean — this
   loses only Sidr's own data (its database, settings, the BYOK key, the `HOME` role), nothing else on
   the phone.
4. **`open_wifi_settings` — do not tap OK** on the hotspot modal (laptop tethered through this phone).
5. **New for A4′ — the cloud sees the command text.** Every bridge item (Part A from §A3 on) sends the
   typed command and Sidr's tool descriptions to the configured provider. Use commands you are content to
   send; nothing else from the phone leaves (`OutboundContextPolicy`).

---

## Device state to establish first

- SM-A325F, Android 13, **primary locale `ru-RU`**; §A7 and Part D need `tr`, §A12 reads `en`.
- *Measured 2026-11-21:* the phone held debug build `0.1.0+e6e6d9b` (2026-10-06, a sha **not** in this
  repository, signed with another key), schema 4 — not `db1e75d` as predicted. `adb install -r` failed
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; Sidr was uninstalled, the new build installed clean, and the
  pulled schema-4 database copied back with `run-as` **before the first launch**, so §A10 still ran a
  real v4→v5 migration.
- The phone is expected to hold schema **4**; this build is schema **5**
  (`learned_plans`), so installing over runs the v4→v5 migration on the phone's data. A copy before
  installing is **optional** (owner decision 2026-11-21 — the data is not needed); if wanted:
  ```
  tools/device/pull-agent-db.sh /path/to/backup-before-a4
  ```
  (the output directory keeps `sidr_history.db` **and** its `-wal`; the pair is the database).
- **Before installing, note** what the memory screens show today: Settings → learned choices and aliases
  (§A10 compares against it).
- Install the new build **over** the existing one. Sidr holds the `HOME` role (shortcut items need it).
- **A BYOK provider configured and working**, network on. Note which host: the bridge's ceiling is **per
  host** since 3b — **40 s** for OpenRouter with the reasoning cap, **50 s** for every other host.
- A second state you will switch to and back: **local-only on** (Settings → «Только локально»), and once
  **flight mode** — §A11.
- Test apps: one installed app with a single-word label (below `<app>`), a second one `<app2>`; Telegram
  installed (§A7); if possible two apps sharing one label (§A5) — skip that item honestly if not.
- Read agent tables **only** with `tools/device/pull-agent-db.sh` (WAL; `DeviceDatabaseReadGuardTest`).

---

## Part A — the owner must look, because the block changed what the user sees

### A1. The bridge — a FastPath miss reaches the model, nothing runs before «Выполнить» (3a)

- **WRONG LINE, corrected 2026-11-21 (run):** «найди мне лекцию по rust в интернете» opened the browser
  search **at once** (~1 s), no card. That is FastPath: «найди» is its `ru` web-search trigger
  (`RuleBasedIntentMatcher.kt:269`), and phase 4's plan review (S2) had already said «найди …» never reaches
  the model. The line came from phase 3a's plan §0.7 and was carried over unchecked — a checklist defect,
  not a code one. (It is B15's finding seen live: FastPath answers the phrase confidently with the whole
  remainder as the query.) Re-run with a phrase no FastPath arm decides:
- [x] *Owner 2026-11-21: as predicted — «Разбираю команду…» with «Отмена» ~3 s, card «Понял так» with the
      search step and «Предложено моделью», nothing before «Выполнить», then «План выполнен» and the
      browser opened.* «хочу посмотреть лекцию по rust» — expected: «Разбираю команду…» with «Отмена» for the
      model's seconds, then a card «Понял так» with one step «Найти в интернете: …», «Предложено моделью:
      …» under a search text you did not type, «Выполнить» / «Отмена». **Nothing happens before «Выполнить».**
- [x] After «Выполнить» the **agent card** takes over: a `SAFE` step runs, «План выполнен» with its step
      line. *New for model actions:* before A4′ the router opened the page with no agent card.
- [x] *Owner 2026-11-21: as predicted.* «Отмена» on the waiting line — the wait stops, nothing is sent
      afterwards, the field is usable.
- [x] *~3 s observed.* The wait never exceeds the host's ceiling (40 s / 50 s, see Device state). If one line runs long,
      write down how long — that number re-pins `BridgeBudget`.
- [x] *Owner 2026-11-21: as predicted.* «почему небо синее» — expected: the **assistant** opens with the
      question already typed, **not sent**.
      **Owner wish recorded at this item (not a finding against A4′):** the assistant should answer at
      once (send, not prefill), keep the conversation without losing earlier messages, offer «new
      conversation», and save conversations — a full assistant. Its own block (brainstorm → spec → plan):
      sending earlier turns widens what leaves the device (`OutboundContextPolicy`, an ADR + sentinel guard
      by the hard rules), stored conversations are a new at-rest holder of user text (Auto Backup rules,
      «Забыть»), and «prefilled, not sent» was phase 3a's deliberate choice.
- [x] *Owner 2026-11-21: same text.* A question with `!'()*` in it opens in the assistant with **the same
      text** (the encoder was checked by hand on one fixture; decoding is executed by no test).
- *Host: OpenRouter* → the bridge ceiling for this round is **40 s** (reasoning cap on).

### A2. The fifth place — a `CONFIRM` proposal costs two cards (3a, named regression)

- [x] A model reading that lands on `open_url`, `play_store_search` or `uninstall_app` costs the
      interpretation card **and** the engine's consent card, and a wait up to the ceiling instead of 2 s.
      The interpretation card's chip reads **`CONFIRM`**; the engine's consent card for `uninstall_app`
      reads **`DESTRUCTIVE`** (phase 2's risk-tone surface).
- *Owner 2026-11-21: all three as predicted — «Понял так» with `CONFIRM`, the engine's consent card
  `DESTRUCTIVE` naming the package, then the OS dialog (cancelled).* **Owner finding: three confirmations
  for one uninstall are too many; if Android already asks, Sidr need not ask again.** This is spec
  §7.12.3's named regression («the fifth place»), seen and judged. Options for the owner (none built):
  **(a)** merge the interpretation card and the engine's consent card into one Sidr card for a `CONFIRM`
  proposal — the gate stays, one tap less (phase 3a listed «слияние двух карточек» as out of A4′);
  **(b)** skip Sidr's consent when the OS itself confirms the act — a per-tool attribute, a change to the
  one gate predicate (`DOC-ADL-1`) and to «the gate set is the same for every command» (ADR «Два
  принципа»); **(c)** keep as is. **Ruling 2026-11-21: (a)** — merge the two Sidr cards; build after
  the acceptance, its own small task.
- **Finding 2026-11-21 (run), «хочу открыть ftp://example.com»:** the screen read «Неизвестная команда.
  Попробуйте: …» — no card. Reading of the code (not a measurement): FastPath answers `Unknown` (`UrlDetector`
  refuses `ftp`), step 7 asks the model, the model answers `decline` («nothing listed fits» — no tool opens
  `ftp`), and `RouteCommandUseCase:278` `ModelPlanning.Declined -> ruleOutcome` hands back FastPath's
  `Unknown`, rendered `launcher_feedback_unknown_command`. Tested on purpose (`the model declines - keeps the
  FastPath outcome`), but the user is told the **command** is unknown after the **model** understood it and
  found no tool — the wording ADR 1/4 rule 3 retired for a FastPath miss. Owner ruling needed: an honest
  decline line (e.g. «Понял, но такого действия у Sidr нет»), or keep. The non-`http` failure path itself
  was not produced on the phone (the model declined before it) — stays test-only. *Owner confirmed «Разбираю
  команду…» flashed — the model was asked; the reading holds.* **Ruling 2026-11-21: (1)** — a model's
  decline gets its own honest line (exact `en`/`ru`/`tr` text to be signed by the owner); build after the
  acceptance, its own small task.
- [ ] A model-written address that is not `http`/`https` ends the step **failed** — nothing opens.

### A3. Honest states — the system's state is never blamed on the command (phase 1, 3a)

- *Owner 2026-11-21: broken-key line ✓; the card survives the Settings trip ✓.* **Finding (UI, not an A4′
  change):** on the provider screen the soft keyboard covers the key field while typing (input still works)
  — the field does not scroll above the IME. Address: the settings provider screen, a small fix after the
  acceptance.
- [x] **Broken key** (temporarily corrupt it): «Ключ ИИ-провайдера не найден или не принят.» with
      «Настроить провайдера».
- *Owner 2026-11-21, items below:* local-only line ✓; provider on — no store card ✓; «найди билеты в … и
  сделай бронь» — browser **not** opened ✓, instead the model's clarify shown verbatim: «Каким сервисом вы
  хотите искать и бронировать билеты в Казань?»; «найди лучший рецепт шашлыка» opens the browser ✓; WEB
  chip ✓. **Owner wish recorded at the clarify:** when the model asks, open a dialogue line with the agent
  where the answer can be typed or dictated. Today's clarify is one verbatim question by design — «NOT a
  clarification protocol» (`ModelPlanning.Clarify`, spec §1/§3), de-prioritised by B15's measured 0 %
  ambiguity. The owner now asks for it: a spec decision for the next block, sharing its ground with the
  full-assistant wish of §A1 (a conversation kept, answers sent back).
- [x] **Local-only on:** «открой gmail и проверь новые письма» — «Не распознано локально. Понимание облаком
      выключено.»; **no** store card (phase 1: before A4′ this offered a store search with a wrong name).
- [x] With the provider on, the same command shows **no** store card — most likely a model proposal to
      open Gmail.
- [x] «найди билеты в … и сделай бронь» does **not** open the browser; «найди лучший рецепт шашлыка» and
      «search how to download and install python» **do**, as before.
- [x] The **WEB chip** on a buffer like «… и закажи такси»: opens the browser with the buffer **as typed**,
      never goes to the model (phase 1 owner fix).
- [x] A card left standing survives a trip to Settings: switch local-only **on** there, come back, press
      «Выполнить» — the old plan runs; the model is not asked again; nothing new leaves (`DOC-ADL-3`).

### A4. Word forms and «did you mean» — offered, never bound (3b)

- **Finding (UI, not an A4′ change), owner 2026-11-21:** on the aliases screen the learned aliases sit at
  the very bottom of the long app list — reaching them means scrolling to the end. Wish: a separate screen or
  tab for learned aliases inside alias settings. Phone state: aliases «телега» → Plus, «tg» → Plus.
- **Run 2026-11-21 (owner):** «открой телегу» and «открой tg» both → the A0 plan «Подтвердите этот шаг — найти в
  магазине приложений»; «хочу открыть телегу» → «Вы имели в виду «Plus»?» ✓ (the bridge's binder finds the
  alias). **The two «открой …» lines are a checklist error, not a new defect:** an alias answers to the
  **bare** phrase only (`ResolveCommandWithAliasUseCase` fills only `Unknown`; «открой X» is FastPath's
  decided `NoAppFound` → A0's launch→store plan). The same wrong promise was found in A1″'s acceptance
  (ADR «Этап 5.5 (A1″)», four checklist defects) and is 3b's named debt (f). **Owner finding all the same:**
  offering a store search for a name the user taught as an alias is poor. Candidate ruling: «открой
  <alias>» consults aliases before the store plan (FastPath launch arm / step 2) — **Ruling 2026-11-21: yes.**
- **Run 2026-11-21 (owner): the bare alias «tg» / «телега» goes to the MODEL** and returns a card «Понял так —
  Plus»; it does not launch at once. Not a new defect but phase 3a's fork **F3a-C**, answered (a) on
  2026-10-04: the alias decorator fills only `Unknown`, and since 3a an undecided command reaches step 7
  first — with a working key the model's `launch_app` is bound through `AppTargetResolver` (which reads
  aliases), so the plan called it «better». **Seen on the phone it is worse:** a deterministic local memory
  costs a round trip, a card and a tap, and the alias words leave the device (principle 2 of ADR «Два
  принципа»: what can run on the device runs there). This agent's prediction «launches at once» was S2-2's
  behaviour, stale since 3a — a checklist error too. **Ruling 2026-11-21: alias before the model** — an
  alias, bare or after a launch verb, resolves on the device before step 7 and before the store plan;
  F3a-C (b) taken, widened to «открой <alias>». Build after the acceptance, its own task (touches
  `DOC-ADL-3`'s local path; local-only starts honouring aliases — named in the ADR). *Owner 2026-11-21: aliases
  working in local-only mode is accepted («не вижу ничего страшного»).*
- [x] *superseded by the run note above.* «открой телегу» (alias «телега» set) — expected «Вы имели в виду «…»?» with one app; the tap
      launches. *(Execution note: FastPath's launch arm does not consult aliases — if you see «нет такого»,
      that is the named debt (f), not a new finding.)*
- [x] *Owner 2026-11-21: `DESTRUCTIVE` consent card at once; «…пожалуйста» → «Вы имели в виду…?»; «несуществующее» → «не найдено», no model.* «удали приложение <app>» — the uninstall plans **locally** (no model wait); with an extra word
      («… пожалуйста») — «Вы имели в виду «<app>»?»; «удали приложение несуществующее» — «не нашёл…» with
      **no** model wait, online or not (R14-39 closed).
- [ ] An uninstall target that matches **only after folding** («lumora» for «Lumora+») — asks, does not
      bind without a tap (owner ruling 2026-11-17).
- [x] *Owner 2026-11-21 ✓.* «показать все приложения» (FastPath) opens the app grid; «Назад» returns to Home.
- [x] *Owner 2026-11-21 ✓ (30 / 90 min).* «поставь таймер на полчаса» / «полтора часа» — read as 30 / 90 minutes **on the device**.
      *Owner 2026-11-21: «полчасика» online → the model → card «таймер на 30 минут» ✓; the local-only half not run.*
      «поставь таймер на полчасика» — online goes **to the model** (E6-R1); local-only / no provider /
      offline reads that state's honest line, never a card the device could not fill.
- [ ] *(Optional)* an Arabic command naming an installed app with a prefix («افتح بالسنداف»-shaped) asks
      «did you mean» (bridge path).

### A5. Choosing between two same-named apps (3a)

- [ ] «мне нужен telegram» with two «Telegram» apps — «Какое приложение — «telegram»?» with both, label
      and package; the tap shows «Открыть … (package)»; nothing launches before «Выполнить». *(Skip with a
      note if the phone has no two same-labelled apps.)*
- [ ] After closing that choice, a tap on a grid tile is **not** recorded as a learned choice (the token
      was armed by the closed card — phase 3a T9 fix).

### A6. Multi-step model plans (3b)

- *§A5 skipped 2026-11-21: no two same-labelled apps on the phone.*
- *Owner 2026-11-21, «поставь будильник на 7:30 и открой tg»:* one card, two lines ✓. **Ruling (R10-3/C12):
  number the steps.** «Выполнить»: the alarm is set, but **the clock app stays open** — «Назад» is needed
  before Telegram shows. **Owner wish: return automatically.** Cause: `set_alarm`/`set_timer` send
  `EXTRA_SKIP_UI = false` (`Tier0IntentToolWorker`), and «immediately visible (the clock app opens showing
  it)» is one of the four grounds `SAFE` rests on for both tools (owner decision, `Tier0IntentToolSource`
  KDoc). Options: **(a)** `EXTRA_SKIP_UI = true` always — visibility moves to Sidr's own finished card
  («Поставить будильник 7:30 — выполнено»), the `SAFE` ground restated; **(b)** `true` only when another
  step follows; **(c)** keep. Either (a) or (b) needs one measurement first: the `true` case was never driven
  on the SM-A325F (the KDoc says so). **Ruling 2026-11-21: (a)**, with a Home indicator so the alarm stays
  visible: the owner proposes an alarm icon and the time beside it. Agent proposal: a chip under the clock
  read from `AlarmManager.getNextAlarmClock()` (no permission, offline, refreshed on
  `ACTION_NEXT_ALARM_CLOCK_CHANGED`), tap → `show_alarms`. **Caveat measured 2026-11-21 (corrected the same hour):** `dumpsys alarm` showed «Next alarm clock
  05:16:48.781» with no alarm or timer in the clock; its owner is **`com.samsung.android.fmm`** (Find My
  Mobile, tag `defaultScanStartTimer`), which registers a background scan through `setAlarmClock`. (A first
  reading here blamed the 90-minute timer — wrong, disproved by the same dump.) So `getNextAlarmClock()` is
  **not** «the user's next alarm» on this phone: the chip must keep only an entry whose
  `showIntent.creatorPackage` is the clock app (the package resolving `ACTION_SHOW_ALARMS`), or say nothing.
  **Sidr's Home has no clock** (the clock is Android's status bar) — the chip's place on Home is a design
  decision of that task. Design + `core/ui` goldens; its own task after the acceptance.
- **Owner observation 2026-11-21 (also seen at the previous acceptance):** a timer switched off in the clock
  still showed in Android's notifications. Reading: §A4 created **three** timers (30, 90 min, «полчасика»
  30) — Samsung's clock runs several at once; stopping one leaves the others. The owner then found and closed
  the one still running; the clock now shows no alarms and no timers (owner, 2026-11-21). Sidr only sends `ACTION_SET_TIMER`; it neither holds nor can see a
  running timer (no public API). To confirm: the clock's «Таймер» tab. Not a Sidr defect unless a timer
  shows there that was never asked for.
- **Measured 2026-11-21 — the stuck timer notification IS reproducible, and its trigger is the intent's
  shape, not Sidr's code:** a timer set through Sidr, stopped and the clock closed → an ongoing clock
  notification «Таймер (N мин.)» keeps ticking (a chronometer in the clock's RemoteViews), no clock service
  behind it, nothing rings. A timer set **by hand** in the clock, stopped the same way → clears (owner).
  The **same intent from `adb`, without Sidr** (`SET_TIMER`, `LENGTH 60`, `SKIP_UI false`, `NEW_TASK`) →
  stuck, twice (owner). With **`SKIP_UI true`** → the clock does not open, the timer runs, **rings**, and the
  notification clears (owner). `SET_ALARM` with `SKIP_UI true` → alarm created enabled at the asked time,
  nothing drawn (`dumpsys alarm`, agent). **Fixed during the round** (owner: «решай проблему»): both
  `set_timer` and `set_alarm` send `EXTRA_SKIP_UI = true` (`Tier0IntentToolWorker`; three tests flipped first,
  scoped `:data:repository` RED 3 → GREEN 477/0/0; KDocs restate «immediately visible» as Sidr's finished
  card). Installed over as `0.1.0+2e3794b-dirty`. This also builds ruling (a) above; the Home chip stays a
  follow-up. **Re-checked on the phone 2026-11-21 (owner):** «поставь таймер на 1 минуту» — rings, notification
  clears ✓; «поставь будильник на 7:30 и открой tg» — Telegram at once ✓, alarm set, **no alarm notification**.
  Not a defect: `dumpsys alarm` shows the clock's `EXPLICIT_ALARM_ALERT` at 07:30 and its own
  `UPCOMING_ALERT` at 07:00:01 — Samsung's clock shows «предстоящий будильник» 30 min before, for a hand-set
  alarm too. The gap between setting and that notice is what the Home chip (follow-up) closes.
- [x] «поставь будильник на 7:30 и открой <app>» (online) — one card, two lines; «Выполнить» sets the
      alarm, then opens the app. The N-step card is one text block, steps **not numbered**; judge whether
      «← из шага N» reads on a three-step plan (R10-3, C12 — numbering is a string decision, yours).
- *Run 2026-11-21:* «поставь таймер на 2 минуты и открой tg» cannot pause — both `SAFE` steps ran before a
  `force-stop` could land (no session row left); re-run with a gate: «поставь таймер на 2 минуты и удали
  приложение Plus» → force-stop at the consent card; DB read: `AwaitingConsent`, cursor 1, step 0 `set_timer`
  `Effected`, step 1 `uninstall_app` `org.telegram.plus` `CONFIRM`. Relaunch: the pause card ✓; «Продолжить»
  → a card for the uninstall **only** ✓ (no second timer); OS dialog → Отмена; afterwards all three
  `agent_*` tables empty, Telegram installed. The pause body's exact third line was not read back.
- [x] **A paused two-step model plan whose first step ran** (`adb shell am force-stop com.sidr.launcher`
      after step 1, on a `SAFE` pair) — on «Продолжить» comes back as a card **for the second step only**.
      Read the pause body's third line on such a plan (item 10 of 3b).
- [ ] Of «Отменить» and «Повторить остаток» only one secondary slot is shown — «Отменить» wins (C1).
- [ ] A failed resume: «Закрыть» cancels nothing — the pause returns at the next start (C2). Confirm that
      is what you see, then rule on it.
- [x] *Owner 2026-11-21: «План остановлен», no «Отменить», only «Закрыть» ✓.* Refusal at an uninstall's consent after step 1 was handed to the system: «План остановлен» with **no**
      «Отменить» (F1).

### A7. Turkish forms (3b) — locale `tr`

- [x] «telegramı aç» / «Telegram'ı kaldır» (Telegram installed) — ask «did you mean»; the uninstall then
      shows the engine's consent card **naming the package**. (Uninstall dialog → Отмена.)
      *Corrected in place 2026-11-21: no app labelled «Telegram» is on this phone — the one Telegram client is
      `org.telegram.plus`, label «Plus». Run as: «telegramı aç» → an honest no-such-app reading; «plusu aç» →
      «… mı demek istediniz?» with Plus, nothing launched before the pick; «plusu kaldır» → the consent card naming
      Plus / `org.telegram.plus` → «Onayla» → the Android dialog → **İptal**; «alarm kur» → a Play Store search, no
      alarm set; Ayarlar → «Öğrenilmiş komutlar» opens in Turkish, no crash. Owner 2026-11-21, locale `tr`, build
      with the Part D corrections: «все так как ты сказал» ✓.*

### A8. Pause, re-plan, undo (phases 0, 2)

- [x] *Owner 2026-11-21: «открой вельтрикс» → force-stop at the store consent (DB: `AwaitingConsent`, cursor 1, step 0 `launch_app` `Observed`, step 1 `play_store_search` `CONFIRM`); relaunch → pause card; «Продолжить» → a fresh store consent card ✓ (app not installed branch).* A0's plan paused at the gate («открой <не установленное>», process killed at the store consent) —
      the body reads «…а с тех пор всё могло измениться. Продолжите — и Sidr проверит план заново…».
      If the app was installed meanwhile, «Продолжить» opens it and offers no store; if not, the card
      returns to the store consent as a **new** session. **This supersedes §12.8 of the A0 re-check**
      ([2026-08-23-a0-device-recheck.md](2026-08-23-a0-device-recheck.md)), which predicts pre-phase-2 behaviour.
- [x] *Owner 2026-11-21: the state words read well — **accepted** («удачно»).* The paused cursor step reads «… — ждёт вас», a step with no effect «— без изменений» (phase 0 words;
      **the owner never accepted these** — read and rule).
- [ ] «Продолжить» on a plan whose target vanished meanwhile (uninstall stopped at consent; the app removed
      another way — *only if you have an expendable app*; otherwise skip) — «План не удалось завершить»,
      nothing runs (M1(b)).
- [ ] After an uninstall that ran to the OS dialog: «План пройден — исход за системой», body «…Как минимум
      один шаг передан системе…», step «… — передано системе».
- **WRONG LINE, corrected 2026-11-21 (run):** «назови вельтрикс tg» → the model → «Понял так — Найти в интернете:
  вельтрикс tg». The `ru` form of `set_app_alias` is **«называй <app> как <фраза>»** (`ToolVocabulary`, two
  slots, infix «как»); «назови …» (phase 2's hypothesis wording) matches nothing at 2b, and `launcher_memory`
  is never offered to the model (D1, §7.5), so the bridge could only guess — a web search. Re-run with the real
  form:
- *Run 2026-11-21:* «называй tg как вельтрикс» → **the model** → «Модель спрашивает: что вы хотите сделать с
  вельтрикс tg». The app slot takes an app **label** (the tests use «называй <label> как <фраза>»); «tg» is an
  alias, not a label. **Finding (minor, not diagnosed):** an app slot the device cannot resolve here reaches
  the model instead of being answered on the device as 3b Task 5 does for 2b's unresolved targets — and the
  alias words left the device. Belongs with the «alias before the model» task. Re-run with the label:
- *Owner 2026-11-21, «называй Plus как вельтрикс»:* «Отменить» visible and tappable ✓; the tap ends with «Забыть
  алиас «вельтрикс»» ✓; read as *undo* (pressed and got the undo). **Two findings:** **(1)** «Отменить» is
  slightly bigger than «Закрыть» — owner proposes **equal sizes** (design system, `core/ui` goldens);
  **(2)** «Разбираю команду…» flashed for a split second before the card although no model was asked —
  the indicator is a 300 ms timer on **any** command (`LauncherCommandSession.THINKING_DELAY_MS`, plan B-10:
  «only a slow answer — the model's — shows the wait»); on this phone the local 2b path (alias save + session
  start through Room) crossed 300 ms, so the user reads it as the model thinking. Options for the owner:
  show the wait only when step 7 is actually called, or a longer delay. **Ruling 2026-11-21: (a)** — the wait
  shows only while the model is asked; its own task after the acceptance.
- [x] «называй Plus как вельтрикс» — the card ends with a second button **«Отменить»**, visible and tappable
      on this screen (PF-2); the tap ends with «Забыть алиас «вельтрикс»» (or «Называть «<package>»
      «вельтрикс»» if it replaced an older alias — the **package**, not the label). Judge whether
      «Отменить» beside «Закрыть» reads as *undo*, not *refuse* (F2-3.3, your signature).
- [ ] A step cut by the budget ends «План остановлен» / «Sidr остановил этот план: продолжать его безопасно
      уже нельзя…». *(Only if reachable by a slow tool; otherwise note «not produced».)*

### A9. The learned-plan cache (phase 4)

Use a command that reaches the **model** — not «удали …» (2b) and not «найди …» (FastPath); they never
reach the cache.

- *Run 2026-11-21:* «сотри калькулятор» ×3 with the card (control) → `learned_plans` «сотри …» streak 3, template
  one `uninstall_app` with slot 0 (agent read). Fourth «сотри заметки» → «Разбираю команду…» then **«Неизвестная
  команда»**: «заметки» is not the Notes app's label (likely «Samsung Notes»), the replay's binder found nothing,
  the miss fell through (B-5) to the model, which declined → FastPath's `Unknown` (the decline finding of §A2,
  already ruled (1)). Fourth with a label, «сотри калькулятор» → **no wait, no interpretation card**, the consent
  card at once with «Выученная команда» and «Забыть» ✓ (owner). Also seen in the table: every fresh reading writes
  a streak-1 row, typos included («иинуту», «удпюали») — as designed (phase 4 ADR), visible and forgettable on the
  learned-commands screen.
- [x] «сотри <app>» (online): the interpretation card the first **three** times (control; uninstall dialog
      → Отмена each time). The fourth time «сотри <app2>»: **no** interpretation card, only the engine's
      consent card naming the package — **and the same online, offline and local-only.**
- *Run 2026-11-21 (owner):* «поищи рецепт плова», «поищи погоду в казани», «поищи курс доллара» with the card ✓; the
  fourth **different** «поищи новости дня» still showed the card; a command **repeated** three times went at once on
  the fourth ✓ with «Выученная команда» / «Забыть»; after «Забыть», «поищи что-нибудь» → the model's clarify
  «что именно нужно поискать» (the card path, not a replay ✓). **Agent read afterwards:** the only `поищи` row left is
  «поищи погоду в казани», streak 1, a **constant** template `"погода в казани"` — the model inflected the user's
  words («погоду» → «погода»), so no slot could be found and the reading became its own whole-command form, not
  «поищи …». **Reading (not proved):** «поищи …» did get learned — from the phrases the model copied verbatim — and
  that was the row «Забыть» deleted; «погоду в казани» never counted toward it, so the fourth different phrase was
  only the third confirmation. **Finding:** a slot is found only when the model's argument repeats the user's words
  byte for byte, so for Russian (and Turkish) a search form learns erratically — a word-form rule for slot
  detection (3b's `AppNameMatcher` B-2 is the precedent) is the candidate fix. **Ruling 2026-11-21: do it** —
  its own task after the acceptance.
- [x] «поищи <слова>» three times with the card, then «поищи <другие слова>» opens the search **at once**,
      the session card reading «Выученная команда» with «Забыть».
- [x] «Забыть» on that card → the next «поищи …» shows the interpretation card again.
- [ ] A learned command that still needs a card (a choice between two apps, or model text going out)
      carries the mark «Выученная команда» **after** «Выполнить», not before.
- [ ] A learned plan interrupted by process death: the restored card carries **no** mark.
- **DEFECT found and fixed in the round, 2026-11-21:** Settings → «Выученные команды» **crashed the launcher**
  (back to Samsung's home): `MissingFormatArgumentException: Format specifier '%2$s'` at
  `AgentSessionPresentation.line` ← `LearnedCommandsScreen.LearnedCommandItem` (crash buffer, twice). Cause:
  «сотри …» is an `uninstall_app` whose `app` is a slot (`⟨…⟩`) with no `app_label`; `PlanStep.line`'s fallback
  called `toolLabelFor`, whose `uninstall_app` / `set_app_alias` resources have **two** placeholders, with one
  — the KDoc's «degrades to says less» promise was false for exactly those two tools, and the screen's only
  test fixture (`delete_file`) never reached them. Fix: the fallback uses one-placeholder lines
  `launcher_agent_step_uninstall_one` («Удалить %1$s») / `launcher_agent_step_set_alias_one` («Задать название
  «%1$s»») in `en`/`ru`/`tr` (two new `tr` strings → Part D); `LearnedCommandsScreenStepLineTest` — RED 2 with
  the phone's exception, then scoped `:feature:launcher` GREEN 295/0/0, `:app` 90/0/0. Installed over.
- [x] *Owner 2026-11-21, after the fix: opens, rows visible.* Settings → «Выученные команды»: lists «сотри …» and «поищи …» as forms with a «…» slot, their step
      lines, «N / 3» or «Выучено», and «Забыть». No provenance / last-used / «stored locally» fields.
- [x] *Owner 2026-11-21 ✓.* **«Забыть» on that screen goes through a confirmation** (T8, later than the phase 4 plan — the plan's
      «one tap, no confirmation» line is superseded): title «Забыть выученную команду?», consequence
      ««…» больше не будет выполняться без вопроса. Чтобы выучить её снова, подтвердите её ещё три раза.»
      Cancel keeps the row; confirm removes it.
- [ ] A learned address ending in «)» opens **truncated**, at once — a named debt; confirm or refute.

### A10. The upgrade from schema 4 (phase 4)

- [x] The app updated over the v4 install **starts**; learned choices and aliases are the ones you noted
      before installing. *Owner, 2026-11-21: started, both aliases present.*
- [x] `tools/device/pull-agent-db.sh` after the first start — the database reads, `learned_plans` exists
      (pass a query as `$2`, e.g. `SELECT count(*) FROM learned_plans;`). *Agent read, 2026-11-21:
      `user_version` 4 → 5, `integrity_check` ok, `learned_plans` present and empty, `identity_hash`
      `6dc34733…` equals `schemas/…/5.json`; aliases 2 → 2, `intent_match` 69 → 69.*
- [x] *Run by the owner 2026-11-21 on the SM-A325F: JUnit XML 11 / 0 failures / 0 errors — `MigrationTest`
      8 (incl. `v4_to_v5…` and `v1_to_v5…`), `SecretStoreInstrumentedTest` 3; the launcher package stayed
      installed.* **Instrumented `MigrationTest`** (`data/repository/src/androidTest/.../db/MigrationTest.kt`:
      `v4_to_v5_migration_addsLearnedPlansTable_andValidatesAgainstGoldenSchema`,
      `v1_to_v5_migrationRunway_composes`) — **on this phone, run by the owner** (decision 2026-11-21),
      **after** the two items above, so whatever Gradle installs or removes cannot touch the live-upgrade
      check. From the Claude Code prompt: `! ./gradlew :data:repository:connectedDebugAndroidTest`. The
      test builds its own throwaway database; read the result from the JUnit XML under
      `data/repository/build/outputs/androidTest-results/`, not the console.

### A11. Local-only, no provider, offline — parity (ADR 1/4 rule 5)

- [x] *Owner 2026-11-21: «хочу посмотреть лекцию по kotlin» — local-only, flight mode, no provider: each its own line, never «Неизвестная команда» ✓.* For one model-shaped command, each of the three states reads **its own** line — local-only, no
      provider, offline — never «Unknown command».
- [x] *Owner 2026-11-21: «сотри калькулятор» replays in local-only and in flight mode, consent card each time ✓ (no-provider state not run for the replay).* A **learned** command (§A9) replays in all three; the consent gate still asks for the risky step.

### A12. The new local-only description — read it in three locales

- [x] *Owner 2026-11-21: reads as quoted and matches what the round showed ✓ (`ru`; `en`/`tr` with Part D).* Settings → «Только локально» reads (re-signed 2026-11-20, `072889ca`): «Выключено: команды, которые
      лаунчер не распознал локально, отправляются вашему ИИ-провайдеру… В обоих случаях предложенное
      действие запрашивает подтверждение перед выполнением, если вы не подтвердили эту же команду трижды,
      а рискованный шаг подтверждается всегда…». Check that the screen matches what §A9 just showed; read
      `en` and `tr` once.
- [x] *Owner 2026-11-21: «Сборка 0.1.0+2e3794b-dirty» ✓.* Settings → «Система»: «Сборка `0.1.0+<sha>`», `-dirty` only from an uncommitted tree (phase 0).

---

## Part B — the owner re-runs to confirm nothing regressed

*Owner 2026-11-21: «открой калькулятор» at once; «открой youtube music» → A0's plan with the store consent; «системные
настройки»; a voice «открой калькулятор»; the assistant streams; «Закрыть» clears the field and returns Home — all ✓.
Not re-run here: «alarm kur» (Part D, `tr`), the cascade-delete and force-stop items beyond what §A6/§A8 read.*

- [x] FastPath: «открой <установленное>» opens the app without the agent surface; «alarm kur» is still a
      Play Store search.
- [x] «открой <не установленное>», «открой youtube music», «открой google maps» — A0's two-step plan as
      before (phase 1).
- [x] «поставь таймер на 5 минут», «системные настройки», one navigating tool — open as in A1″.
- [ ] Launching apps, screens and shortcuts behaves as before (world calls moved off the main thread in
      phase 2 — a reading, not a measurement).
- [ ] Cancel mid-plan, then `tools/device/pull-agent-db.sh` — the three `agent_*` tables are empty.
- [ ] `am force-stop` during a `SAFE` step, relaunch — no stuck `AwaitingConsent`, launcher healthy.
- [ ] A voice command (Final without Partial) clears a standing card before the new answer arrives.
- [ ] After a re-route, the command's words are nowhere visible: card closed, field empty, WEB/ASK chips act
      on empty input.
- [x] Assistant / BYOK chat streams normally.

---

## Part C — agent-verifiable evidence, which does **not** clear anything

- **C1. Orphan `Running` row** (3a residual 1): a cancel/supersede landing while step 2/2b saves the
  session; the window is milliseconds. Not producible by hand; an agent-driven loop may try, and a hit is
  evidence for the ADR, not a finding of this round.
- **C2. Release build (R8) reads its own learned template.** Needs a release build on the phone; decoding
  under R8 was never executed. Only if the owner installs a release build for this round.
- **C3. ICU segmentation** of the phone equals Robolectric 72.1 (3b) — an agent can compare
  `TextFolding.words` on a fixed list via a debug hook if one is added; none exists today.
- **C4. Not producible in this round, named:** Auto Backup / device transfer bring back **default settings
  and an empty memory** (local-only off) — needs a second phone or a restore; old builds' copies may still
  sit in the Google account. Binder hang (phase 2) cannot be triggered on demand.

---

## Part D — the Turkish review (one session, the owner as native speaker)

Not a device item, but the block cannot close without it (owner answer 5 of phase 1). The strings, by
phase, with their addresses:

- phase 0 — six strings (proposals «Plan bitti — sonuç sistemde», «Derleme»): archive «Фаза 0 A4′»;
- phase 1 — «ve» and the twenty `SecondGoal` verbs (homonyms et / yaz / al / aç / ara); `en`/`ru` homonyms
  «order», «pay», «book», «play», «call», «check», «show» judged on real commands: archive «Фаза 1 A4′»;
- phase 2 — three (`launcher_agent_paused_body_replan`, `launcher_agent_undo`, `launcher_agent_undo_not_all`);
- phase 3a — eighteen (plus «Yapay zekâ» / «yapay zeka» on one line, straight quotes in
  `launcher_interpretation_choose_app`): archive «Фаза 3a A4′», T10 journal;
- phase 3b — five (plus `en` «Did you mean «%1$s»?» guillemets vs straight quotes, C8);
- phase 4 — eight, plus `launcher_forget_learned_command_title` (T8); this round — `launcher_agent_step_uninstall_one`,
  `launcher_agent_step_set_alias_one` (the crash fix). The two Class B lines (local-only
  description, the forget consequence) are already signed 2026-11-20.

**Run 2026-11-21 (owner, native speaker), all 43 unsigned strings + the `SecondGoal` list read in one table
(ru beside tr):**

- interpretation card and bridge (11), agent card (10), learned commands (9), `SecondGoal` «ve» + twenty verbs —
  **accepted as they stand** (straight quotes in `launcher_interpretation_choose_app` accepted with them);
  phase 0's proposals not taken: «Plan tamamlandı — sonuç sistemde» and «Sürüm %1$s» stay;
- provider errors (6) — accepted; the spelling is **«Yapay zekâ»** — applied to every `tr` string of the app
  (seven lines still said «yapay zeka»: three in signed files — `assistant_cloud_disclosure_body`,
  `assistant_no_provider_title`, `settings_local_only_description` — and four unsigned); the owner re-read the
  three signed sentences and **re-signed 2026-11-21** (`sha256:f972c9695ebaa133` assistant,
  `sha256:b3bdfacebde439da` settings), `checkOwnerReviewedLocaleStrings` green;
- step lines (5) — accepted except `launcher_agent_step_set_alias_one`: «“%1$s” adını ver» → **«“%1$s” adını
  belirle»**.

Part D is **done**.

---

## Closing this file

Write what was **seen**, item by item, in a section «Приёмка на устройстве» of the block ADR A4′ — not
here; here only mark the header RUN with the date and build, and correct in place any line that proved
wrong (A1″ precedent). The block is `CLOSED` only with Part A and Part D done, every finding ruled, and the
residuals named.
