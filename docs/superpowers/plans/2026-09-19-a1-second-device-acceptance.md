# A1″ — device acceptance (tool mass, selection, and the first `CONFIRM` tool)

> **NOT RUN. Written 2026-09-19 at block close; the owner runs it, not an agent.**
>
> **Why this file exists.** Block A1″ (Этап 5.5) is `CODE-GREEN`: gate green at **1438 tests / 0
> failures / 0 errors** from a run printing `557 actionable tasks: 557 executed`, every new guard
> mutation-proved, ADR written («2026-09-19 — Этап 5.5 (A1″)» in `ai-context/decisions.md`). It is
> **not** `DEVICE-ACCEPTED`, and nothing this block built has run on a phone. The block *did* use a
> phone — the Task 13/13b measurement round on the SM-A325F — but that round measured **Android
> premises**, not the product. Not one of the five acting tools, not the consent card of the track's
> first `CONFIRM` tool, not one of the six Turkish triggers has ever been observed on a device.
>
> Unlike A0.5 (whose device acceptance was marked *not applicable*, because that block changed nothing
> on the phone), A1″ changes exactly what the user can reach. This is an ordinary deficit, not an
> inapplicability.
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

**Risk levels in this build, so nothing below reads as a missed gate.** Eight of the nine authored
tools are `SAFE` — `requiresConsent(SAFE) == false`, so their plans do **not** stop at
`AwaitingConsent`; the step runs immediately and the session lands on the completed surface with no
confirm tap. That is expected. **`uninstall_app` is `CONFIRM`** and is the first tool in the track's
history that stops the loop on a real product path.

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

Type «удали sidr» (or "uninstall sidr" / "sidr kaldır").

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
| 1 | `ru` | «называй telegram телега» | alias created |
| 1 | `en` | "call telegram tg" | alias created |
| 1 | `tr` | "telegram için tg kullan" | alias created |
| 2 | `ru` | «забудь название телега» | alias removed |
| 2 | `tr` | "telega adını unut" | alias removed |
| 3 | `ru` | «забудь выбор для телега» | learned choice removed |
| 3 | `tr` | "telega için seçimi unut" | learned choice removed |

- [ ] Each runs **without a consent card** (all three are `SAFE`) and lands on a completed one-step plan.
- [ ] The step line names the arguments, not the typed text: «Называть «telegram» «телега»» / «Забыть
      алиас «телега»» / «Забыть выученный выбор для «телега»».
- [ ] The provenance line beneath reads **`LAUNCHER MEMORY · LOCAL`** — locked English, and the `LOCAL`
      half is the point: this is the only shipped level that does **not** cross the app boundary.
- [ ] **Open Settings → Память and confirm the effect is really there** — the alias appears in
      «Псевдонимы» after command 1 and is gone after command 2. A memory tool that reports success
      while storing nothing is the specific silent failure this set was written against (an alias
      stored under a normalized phrase, deleted with a raw one, finds nothing and reports success).
- [ ] After creating the alias, **«открой телега» opens Telegram** — the alias is not just a row, it
      changes resolution.

### A6. THE SIX TURKISH TRIGGERS — none has ever been judged by a native speaker

**This is a numbered acceptance item, not a sentence in an ADR, and "assumed fine" is not an acceptable
answer to it** (precedent: §15 of the A1′ checklist, where exactly this row was answered honestly as
"not judged"). Before this block the unjudged set was **one** trigger inherited from A1′. It is now
**seven**.

| # | `tr` trigger | Tool | Shape | Judged? |
|---|---|---|---|---|
| 1 | `alarm ayarla` | `set_alarm` | suffix | [ ] |
| 2 | `uygulamasını kaldır` | `uninstall_app` | suffix | [ ] |
| 3 | `kaldır` | `uninstall_app` | suffix (bare) | [ ] |
| 4 | `için` … `kullan` | `set_app_alias` | infix + suffix | [ ] |
| 5 | `adını unut` | `forget_app_alias` | suffix | [ ] |
| 6 | `için seçimi unut` | `forget_learned_choice` | suffix | [ ] |
| 7 | `sayaç ayarla` | `set_timer` | suffix (inherited from A1′, still open) | [ ] |

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
- [ ] While in `tr`, also read the six `tr` step lines from §A1/§A4/§A5 above — they ship in the same
      commit and have never been read on a device either.
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
6. **Phase 3b is still ahead** — the eleven navigating tools. §16 criterion 1 (≥15 tools, ≥3 levels,
   accepted on the phone) is **not** closed by this round; this round accepts the acting set. Report the
   count split into *acting* and *navigating*, never as one figure.
