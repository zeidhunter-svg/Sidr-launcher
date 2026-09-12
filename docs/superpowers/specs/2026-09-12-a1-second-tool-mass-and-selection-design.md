# A1″ — Tool mass and selection (Design Spec)

> **Block:** A1″ (Этап 5.5) of the agentic track. **Opened:** 2026-09-12, on `launcher--7`, after A1′
> closed `CLOSED` 2026-09-10.
> **Governing documents:** [Master Plan](../../governing/sidr-agentic-master-plan-v1.0.md) §3.2 / §3.6
> rows `B2` `B3` `B4`, milestone `M-A2`; [track plan](../plans/2026-08-18-agentic-track-restart.md) §0
> and `§HANDOFF`; [doctrine matrix](../../governing/sidr-doctrine-matrix-v1.0.md).
> **Predecessor spec:** [A1′](2026-08-29-a1-federated-tool-registry-design.md) — its boundaries are
> the floor this block builds on and does not re-open.
>
> **Status of this document:** design, written before any code, as DoD §4 requires. Every premise it
> inherits from an earlier document was re-checked against the tree; every premise it cannot check
> from the tree — what an Android intent requires and what it actually does — is marked
> **MEASURE ON DEVICE** and may not be inherited. That rule is not general caution: A1′ shipped its
> headline tool dead because two inherited sentences about Android were both false (§3.1).

---

## 1. Goal

A1′ made the launcher able to be *given* many things safely. A1″ gives them.

After this block:

- the agent's registry holds **≥15 real tools from ≥3 levels**, and that is a property of the running
  product on the owner's phone rather than of a fixture — Master Plan §2 criterion 2, the last open
  item of milestone `M-A2`;
- a tool source may be **dynamic** — its tool set changes while the process lives — and both faces of
  `ToolFederation` see the change, which is not true today (§4.1);
- a command is matched against a registry of that size by a **selector** that fails closed on
  ambiguity and declines rather than guessing, because a false positive here performs an effect;
- a throw from any worker becomes an honest `Failed` instead of killing the home-screen process, and
  the permission guard is keyed on **what the registry declares** rather than on which files happen
  to contain both a `ToolWorker` and an `Intent(` — both are preconditions for shipping ten more
  workers, not improvements to be done afterwards (§8).

**Non-goal, stated first because it is the most likely misreading:** A1″ does **not** connect the tool
registry to the model planner. Tools stay in the deterministic branch. See §6.1 — the reason is that
the code path B3's justification describes does not exist, and building it is a separate block with an
ADR and an egress guard.

---

## 2. Forks — resolved 2026-09-12

The owner delegated the block's shape to the agent on the standing criterion "what is best for the
product / for the Agentic OS in perspective" (precedent: A1′ fork F7, where F3 and F6 were delegated
the same way). Every decision below is therefore recorded with the reasoning that reached it, so it is
overturnable on its merits rather than on its authorship. One fork is **not** delegated and stays open
— §15.

| # | Fork | Decision |
|---|---|---|
| **G1** | What `B3` "selection before the planner" means, given that no `ToolDescriptor` has ever reached a model prompt | **Selection is built for the deterministic branch.** Tools are not rendered to the model, `OutboundContextPolicy` is untouched, no change-control ADR is needed. The prompt-size argument is re-addressed with a **measured** number (§6.1) |
| **G2** | Block order: mass first, or the engine hole first | **Containment first.** The `try` at `AgentExecutor.perform`'s single call site lands before the first new worker. This is one file from A4′ pulled forward, not A4′ (§8.1) |
| **G3** | `B2` shortcuts: N descriptors, or one `launch_shortcut` tool with a dynamic argument | **N descriptors.** One tool with a dynamic argument space hides mass rather than handling it: the registry would declare one capability where a hundred exist, per-tool risk and provenance would collapse into a worker, and the "which shortcut" resolution would move to a place with no argument validation. It also does not satisfy criterion 2, which says tools are **discovered** (§5.1) |
| **G4** | Where a shortcut's display name lives | **Not in `ToolDescriptor`.** Its KDoc states the descriptor "carries no user-facing copy", and that property is worth keeping. Dynamic names are supplied by a `:data:repository` port read by both the selector and the presentation mapper, so `:domain` and `:consumer:jvm` are untouched by the whole shortcut adapter except for the one federation fix (§5.2) |
| **G5** | Which Tier-0 intents of `B4`'s ~10 this block ships | **Only tools whose argument is a bounded token or absent.** `SENDTO`, calendar `INSERT`, `geo:` and web search are excluded with a measured reason and a new address — §7.4. This is `B4` answered, not `B4` postponed |
| **G6** | The `args_json` retention question `§HANDOFF` hands this block | **Not answered here, because §7.4 removes the need for it.** No tool this block ships puts free-typed personal content into an argument. The question moves to whichever block first ships one, with the owner-level half (deleted rows remain readable in the file image without `secure_delete`) still owner's — §7.4 |
| **G7** | Whether the first non-`SAFE` tool ships in this block | **Yes, exactly one.** All four tools shipped so far are `SAFE`, so the consent gate has a product path only through A0's two-step plan. A registered `CONFIRM` tool makes the gate a measured property (§7.3) |

---

## 3. Hostile reading — the premises this block refuses to inherit

`§HANDOFF` makes this the block's first action, with the question stated as "does the earlier record
say what A1″ needs", not "is it accurate". Four findings. The first two are about Android and were
paid for on the owner's phone; the last two are about this repository's own code and were found by
reading the tree, not the documents.

### 3.1. Both halves of `B4`'s justification are measured false, and A1″ starts from zero

Master Plan §3.6 `B4` reads "~12 tools at zero new permissions; the 'prefilled but not sent' form
yields consent from the OS". Both halves failed on the SM-A325F:

- **"Zero new permissions"** — `AlarmClock.ACTION_SET_TIMER` requires
  `com.android.alarm.permission.SET_ALARM`. Undeclared, `ActivityTaskManager` refused every
  invocation. The tool was registered, reachable, matched, planned, gated and unit-tested, and could
  not run once. Eight documents carried the false claim.
- **"Prefilled but not sent"** — with `EXTRA_SKIP_UI = false` the Samsung clock opened **with the
  timer already counting**. The flag governs whether the responding app shows its UI, not whether it
  acts.

**Consequence for this spec, and it is a rule rather than a caution:** for every intent in §7, both
questions — *which permission does it require* and *what does it actually do when invoked* — are
**MEASURE ON DEVICE** items. No cell in §7.2 may be filled from documentation, from this repository's
history, or from the model's own knowledge of Android. A cell filled any other way is the exact defect
that cost A1′ its first acceptance run.

### 3.2. `B3`'s justification describes a code path that does not exist

`B3` reads: "`CatalogSchemaRenderer.render(catalog)` renders the whole catalog … two hundred
descriptors do not fit in a prompt. This is a requirement, not an optimization."

Checked against the tree:
[`CatalogSchemaRenderer.render`](../../../domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/CatalogSchemaRenderer.kt)
takes an **`ActionCatalog`**, and so does
[`LlmCommandPlanner.plan(command, catalog)`](../../../data/ai-cloud/src/main/java/com/sidr/launcher/data/aicloud/LlmCommandPlanner.kt).
`RouteCommandUseCase` step (7) passes the injected `catalog` — the **seven frozen `ActionIds`**.
`OutboundContextPolicy.ALLOWED` carries `ACTION_CATALOG_SCHEMA` and nothing about tools. **No
`ToolDescriptor` has ever left the device.** Fifteen new tools and a hundred shortcuts therefore grow
the model prompt by **zero bytes**.

This is not an error in A1′'s ADR — A1′ never claimed otherwise. It is a gap between what the Master
Plan says A1″ must do and what A1″'s code can do, and the honest form of "row addressed, answered" is
to say so: §6.1 re-states what selection is actually for in this block, and §6.1's last paragraph
hands the prompt question forward with a measurement instead of a slogan.

### 3.3. `ToolFederation` caches at construction, so a dynamic source is invisible after start

[`ToolFederation`](../../../domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt)
builds `byId` and `descriptors` in its constructor, and `provideToolFederation` is a Hilt `@Singleton`.
A source whose `all()` returns a different list later is read **once, at process start**, and never
again. Neither the Master Plan, nor A1′'s spec, nor its ADR names this — reasonably, since all three
shipped sources are static. It is the first thing `B2` breaks, and §4 is its answer.

### 3.4. The vocabulary already knows the argument problem, and the ADR does not

[`ToolVocabulary.Entry`](../../../data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt)'s
KDoc states it plainly: a matched argument is the **normalized** remainder — lower-cased,
whitespace-collapsed — and `ToolMatch` carries the value, not a span into the raw command, so **the
original is unrecoverable from the type**. A tool whose argument is a message body, a note or a search
phrase receives mangled text. A1′'s ADR does not carry this; the file does. It is load-bearing for
§7.4, and it is the kind of sentence that exists only because the author of A1′ task 8 wrote down a
cost rather than a feature.

---

## 4. Dynamic sources — the registry contract under a moving tool set

### 4.1. What must change, and the smallest change that is honest

`ToolRegistry.all()` is documented "read-only and side-effect free", and that must stay: it is called
by `InvocationValidator`, by the selector, by guards, and by the presentation mapper. Making it a
binder call would put I/O behind a synchronous, pure-looking contract — the same class of mistake as
`cat`-ing a WAL database.

So the change is split in two, and neither half is in the port:

1. **The source holds a snapshot.** `ShortcutToolSource.all()` returns a `@Volatile` list it was
   *given*; refreshing it is a separate, explicit, suspending call driven by Android (§5.3). `all()`
   stays pure, cheap and clock-free.
2. **`ToolFederation` stops caching.** `descriptors`, `byId` and `descriptorById` become derived per
   call from `adapters` instead of computed once in the constructor.

The federation's KDoc argues that deriving both faces from one list is load-bearing rather than tidy.
**Deriving per call preserves that argument and strengthens it**: the two faces still read one list,
and neither can now be stale. What it costs is rebuilding two maps per call — with ~150 descriptors,
arithmetic on the order of microseconds, and a task in §13 measures it rather than asserting it.

`ToolAdapter`, `ToolWorker`, `ToolExecutor`, the call-site guards and both consumers are untouched.
`:consumer:jvm` and `:domain`'s `commonMain` see exactly one edited file.

### 4.2. What a stale snapshot may and may not cause

A shortcut may vanish between `all()` and invocation — the app was uninstalled, the shortcut
disabled. The engine's answer already exists and is not extended: `InvocationValidator` validates
against the registry at plan time **and on every resume**, and `ToolFederation.executor` fails closed
on an id it cannot route. What is new is that the failure is now *reachable* rather than a
defence-in-depth branch: §13 requires a test that removes a shortcut between planning and invocation
and pins `Failed`, not a crash and not a silent success.

This is the same staleness family A4′ owns for resumed plans, and this block does not claim to close
it. It claims one thing only: a tool set that moves must not produce an observation the trace calls
`Effected` when nothing happened.

---

## 5. `B2` — the shortcut adapter

### 5.1. What a shortcut is here

One `ToolDescriptor` per enabled, launcher-visible shortcut of an installed app: "Telegram — New
message", "Chrome — New tab". Zero arguments (a shortcut is a fully-bound action by construction),
level `app_shortcut`, effect `EXTERNAL`, durability `TRANSIENT`, risk — see §5.4.

**Identity.** A shortcut's `ToolId` is *derived*, never hand-written, in the adapter — identity C,
exactly as A1′ binds a projection's id (`ToolId(actionId.value)` inside `SystemIntentToolSource`).
Shape: `shortcut:<packageName>/<shortcutId>`. Two properties are required of it and both are testable:
it is stable across a refresh so a persisted plan step still resolves after a restart, and it cannot
collide with an authored id, since no authored id contains `:`.

`ActionIds` is not touched. `ToolIds` gains no constant — these ids do not exist at compile time.

### 5.2. Display names are data, and they do not enter `:domain` (G4)

`ToolDescriptor`'s KDoc says it "carries no user-facing copy: the surface maps `id` to a string
resource in the feature layer". A shortcut's name is neither our copy nor a resource: it is a string
authored by a third-party app in whatever language that app chose. Two ways to carry it:

- add `label: String?` to `ToolDescriptor` — reverses a stated property of a `commonMain` type used by
  both consumers, for a field only one adapter will ever set;
- **chosen:** a `:data:repository` port, `DynamicToolNames`, with `fun nameFor(id: ToolId): String?`,
  implemented by the shortcut adapter and read by (a) the selector, which needs the text to match
  against, and (b) `LauncherViewModel`, which already passes a `toolProvenance` map into
  `AgentSessionPresentation` and gains a `toolNames` map beside it.

`AgentSessionPresentation` keeps its existing rule for authored tools — `ToolId` → `sidrString` — and
falls back to the dynamic name only when there is no resource. The hard rule "user-facing text never
originates in `domain`" is not merely respected but strengthened: no third-party string crosses into
`commonMain` at all. Precedent for carrying a third-party label in a UI-bound model is `InstalledApp.label`,
which the launcher has rendered since the first slice — but it lives in `domain`, and this design
deliberately does not follow it that far.

### 5.3. Refresh, and the condition Android puts on all of this

`LauncherApps.getShortcuts()` is available to the **default home app**. `app/src/main/AndroidManifest.xml`
declares `android.intent.category.HOME`, so Sidr *can* hold that role — but holding it is a runtime
condition the user controls, and the API refuses a caller who is not the current default.

**MEASURE ON DEVICE (§3.1 rule), before any of §5 is written:** what `getShortcuts` does when Sidr is
not the default home (which exception, or an empty list); whether any permission is required beyond
the role; whether `startShortcut` has a different answer from `getShortcuts`; and what a pinned versus
dynamic versus manifest shortcut yields. Nothing in this section may be implemented against an assumed
answer. The design obligation that follows whatever the measurement says: **the adapter degrades to an
empty tool set, never to a crash and never to a surface that offers a tool it cannot run** — the same
shape A1′'s dead `set_timer` violated from the other direction.

Refresh is driven by Android and stays out of `:domain`: a `LauncherApps.Callback` for shortcut and
package changes, plus one refresh when the launcher becomes the default home. The snapshot is built on
a background dispatcher; nothing in the refresh path runs on the main thread.

### 5.4. Risk, and the thing a shortcut cannot tell us

A shortcut's effect is unknown to us: "New message" opens a composer, but nothing in the
`ShortcutInfo` contract says an app may not ship a shortcut that acts immediately. The launcher is in
the same position as the OS, which shows shortcuts and lets the user tap them.

Decision: `SAFE`, with the reason stated rather than assumed — the four properties A1′'s owner ruling
established for `set_timer` (reversible, immediately visible, provenance-disclosed, local) hold for a
shortcut launch **as a class**, because a shortcut is the same act the user performs by long-pressing
the app icon, in front of them, and nothing about it leaves the device. The one property that does
*not* transfer is "we know what it does" — and that is written into the KDoc as the limit, not
implied absent. If a measurement in §5.3 contradicts this, the level changes and the reason with it;
the ruling `set_timer` received is the precedent for how that is recorded.

---

## 6. `B3` — selection

### 6.1. What selection is for in this block (G1)

Not the prompt: §3.2 measured that no tool reaches it. Selection is required because **the matcher
fails closed on growth**, and the failure is silent.

[`ToolVocabulary.match`](../../../data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt)
ends `hits.singleOrNull()` — two entries claiming one text yields **no match at all**. Its own KDoc
says the ambiguity branch is unreachable with two entries and that "`A1″`'s twelve tools make
collisions a question of when". With fifteen authored entries plus a hundred shortcut names, the
common case becomes: the tool exists, is registered, is reachable by every guard, and the user's text
produces nothing. `ToolVocabularyReachabilityTest`'s second assertion catches this **between table
entries** — a shadowed entry stops recognising its own sample — but a table entry shadowed by a
*dynamic* shortcut name is invisible to it, because the shortcut is not in the table.

So selection in A1″ is: **candidate retrieval over a registry that is partly dynamic, plus a decision
policy that declines rather than guesses.**

**The prompt question, handed forward with a number rather than a slogan.** One task in §13 renders
the current registry through a tool-shaped schema and reports the byte and token count for 2, 15 and
~120 descriptors. That number, not `B3`'s sentence, is what the block which first plans tools with a
model starts from. The row is answered here and re-addressed there.

### 6.2. Two vocabularies, one selector

- **Authored tools** keep today's static, localized table (`en`/`ru`/`tr`), with its two guards
  unchanged: triggers are our copy, they are translated, and `LocaleCompletenessGuardTest` plus
  `ToolVocabularyLocaleGuardTest` keep them complete.
- **Dynamic tools** cannot be in that table and must never be translated by us: the name belongs to
  another app. They are matched by normalized token containment over `"<app label> <shortcut label>"`,
  read through `DynamicToolNames` (§5.2).

`ToolSelector` (new, `:data:repository`) takes the normalized command and returns at most one
`ToolMatch`. `ToolMatchPlanner` keeps its current job — turn a match into a one-step plan — and learns
nothing new. `RouteCommandUseCase` step 2b is **not touched**: it already knows nothing about tools.

### 6.3. The decision policy — one strong candidate, or nothing

Ordered, and each rule exists because of a failure mode, not a preference:

1. **An authored trigger beats any dynamic name.** Our vocabulary is deliberate; an app's label is
   not, and an app could ship a shortcut named "system settings".
2. **A dynamic candidate must name its app.** "телеграм новое сообщение" matches; bare "новое
   сообщение" does not, even with exactly one candidate. A shortcut launch is an effect, and the app
   token is what makes a false positive structurally unlikely instead of statistically unlikely.
3. **Two candidates of the same strength → no match.** Today's `singleOrNull`, kept and now reachable.
   Ranking that breaks ties would choose an effect for the user in silence; declining costs a miss,
   and a miss is free — routing falls through exactly as before.
4. **Nothing here answers "unknown command".** Doctrine rule 1 is unchanged: a miss falls through to
   the existing chain.

### 6.4. What selection must not become

It must not learn, rank by usage, or consult a model. Those are A2/A3 and A4′'s plan cache. A selector
whose behaviour depends on history is untestable by the guards this block ships and would put usage
data into a decision path that today has none.

---

## 7. `B4` — authored mass

### 7.1. The rule that chooses the set (G5)

**An argument is a bounded token or absent.** A bounded token is one the launcher can validate or
resolve deterministically: a duration, a clock time, an installed package. Free text — a message body,
a note, a query, a place name — is excluded, for a reason measured in §3.4, not a preference: the
vocabulary hands the worker a lower-cased, whitespace-collapsed remainder, so a message body would be
mangled before it reached the intent, and fixing that means raw-span capture plus an answer to where
personal content may be persisted. Two owner-level decisions, for four tools, none of which `M-A2`
needs.

### 7.2. The candidate set

Every cell in the two right-hand columns is **MEASURE ON DEVICE** and must be filled by a task that
ran it on the SM-A325F. No row ships until both are filled. Ids are proposals; the count floor is
**eight new authored tools**, of which at least four *act* rather than *navigate*.

| Proposed id | Intent | Arg | Permission — MEASURE | What it actually does — MEASURE |
|---|---|---|---|---|
| `set_alarm` | `AlarmClock.ACTION_SET_ALARM` | clock time | ? (`SET_ALARM` already declared) | does it create the alarm, or open a prefilled form? |
| `show_alarms` | `AlarmClock.ACTION_SHOW_ALARMS` | — | ? | — |
| `open_app_info` | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` | package | ? | — |
| `uninstall_app` | `Intent.ACTION_DELETE` (package URI) | package | ? | does the OS confirm, or does it delete? |
| `open_camera` | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | — | ? | — |
| `open_wifi_settings` | `Settings.ACTION_WIFI_SETTINGS` | — | ? | — |
| `open_bluetooth_settings` | `Settings.ACTION_BLUETOOTH_SETTINGS` | — | ? | — |
| `open_battery_settings` | `Intent.ACTION_POWER_USAGE_SUMMARY` | — | ? | — |
| `open_data_usage_settings` | `Settings.ACTION_DATA_USAGE_SETTINGS` | — | ? | — |
| `open_display_settings` | `Settings.ACTION_DISPLAY_SETTINGS` | — | ? | — |
| `open_sound_settings` | `Settings.ACTION_SOUND_SETTINGS` | — | ? | — |
| `open_location_settings` | `Settings.ACTION_LOCATION_SOURCE_SETTINGS` | — | ? | — |
| `open_notification_settings` | `Settings.ACTION_NOTIFICATION_SETTINGS` | — | ? | — |

**Counting honestly.** Eight settings screens are not the same weight as eight capabilities that act,
and the block's own report says so: §14 counts *acting* and *navigating* tools separately, and the
`M-A2` claim is made with both numbers visible. A criterion satisfied by a count nobody breaks down is
the kind of claim Этап 0.5 exists to prevent.

**Package arguments** resolve through the launcher's existing app resolution — the same path that
turns "телеграм" into a package for `launch_app`, including learned resolutions and aliases. A tool
that cannot resolve its package **declines**; it never guesses a package.

### 7.3. The first non-`SAFE` tool (G7)

`uninstall_app` is `CONFIRM` — subject to §7.2's measurement of what the OS itself does. All four
tools shipped to date are `SAFE`, so `requiresConsent` has never gated a real tool on the phone, and
`DoctrineGuardTest`'s M9 gap (declared risk is pinned by no guard) has never been exercised against a
tool that matters. One `CONFIRM` tool turns the consent gate into a measured product property and
gives the owner's acceptance something to look at that is not a settings screen.

If §7.2's measurement shows the OS's own uninstall dialog always confirms, the level still stands: the
gate's job is that **Sidr** does not initiate an irreversible act without the user, and a second
confirmation is not a defect. That reasoning is written into the descriptor's KDoc, so it cannot later
be "simplified" away the way `set_timer`'s was.

### 7.4. Excluded, with the reason and the new address (G5, G6)

`SENDTO` (SMS / email), calendar `Events.INSERT`, `geo:`, web search — **not shipped by A1″**.

Their argument is free text. Shipping them honestly requires, in order: raw-span argument capture
(§3.4), an answer to whether message bodies and event contents may sit in `agent_session` /
`args_json` — where `ArgSource.Literal` is encoded **with its value** — and an answer to the owner-level
observation from A1′'s acceptance that a deleted row's text was still readable out of the on-disk file
image, because SQLite does not zero freed pages without `secure_delete`. Two owner decisions and one
new engine capability, for four tools that `M-A2` does not need.

**Address:** the raw-span capture is A1″-adjacent and cheap but pointless alone → the block that first
ships a free-text tool. The retention question → **A5** (trace/history surface), which is where
"what is kept and for how long" already belongs. The `secure_delete` / journal-mode question stays
**owner / A4′**, unchanged from where A1′'s acceptance left it. This is `B4` and the `§HANDOFF`
obligation *answered* — rejected with a measured reason, re-addressed — which DoD §4 permits and
silence does not.

---

## 8. Preconditions — what must land before the first new worker (G2)

### 8.1. A throw at the single call site

`AgentExecutor.perform`'s one `toolExecutor.invoke` call has no `try`; `RunAgentSessionUseCase.run`
has none; `LauncherAgentSession` runs on `viewModelScope` with no `CoroutineExceptionHandler`. A1′'s
final review found this the expensive way: a device with no activity for `ACTION_SET_TIMER` produced a
`SAFE` one-step plan the consent gate does not stop, and a typed timer command **killed the
home-screen process**. It was fixed inside `Tier0IntentToolWorker`, which is correct placement for
that adapter and is not a mechanism: the contract "an invocation always yields a `ToolResult`" is held
today by convention plus three implementations with three different nets.

A1″ ships ten to a hundred more workers. The obligation `§HANDOFF` puts on this block is "close the
contract mechanically, or write down that every new worker catches for itself, and on which set of
errors". **This block closes it mechanically**, in the engine, at the one call site: a caught throw
becomes `ToolResult.Failed` and an ordinary `ToolObserved`. `CancellationException` is rethrown; the
argument that forbade catching in `ContextIntentLauncher` — a `Unit`-returning launcher cannot tell a
swallowed failure from success — does not apply here, because the call site's result type is already
`ToolResult`, so a caught throw becomes an honest `Failed` rather than an invented `Effected`.

Adapter-level catches stay where they are. They produce *specific* failures; this is the floor under
all of them.

### 8.2. A permission guard keyed on the registry

`ToolPermissionManifestGuardTest` has two measured blind spots, both written in its own KDoc and both
addressed to "the author of adapter #3", which is this block: an `Intent(…)` built in a plain helper
the worker calls is invisible to the whole guard, and the guard keys on **workers** rather than on
**registered tools**, so a tool in a source with no worker branch is unseen. The 2026-09-05 defect was
an instance of the second.

A shared intent-building helper is exactly what an author adding ten intents writes. So the new guard is
keyed on what the **registry declares**: for every `ToolDescriptor` in the production federation there
must be a declared mapping to the intent action it issues and the permission that action requires, and
every permission in that mapping must be in the manifest. The hand-written column does not disappear —
it moves to where totality can be asserted over the registry instead of over a file set. The existing
guard is not deleted; the two answer different questions, and §13 records which.

### 8.3. Risk pinning for a future source

`DoctrineGuardTest` does not pin declared risk. Today's four tools are covered by assertions inside
their own sources' tests in `:data:repository`; a new adapter arrives with no pin unless its author
writes one — and this block adds two new adapters. The pin moves into the guard, over the production
federation, so that a `SAFE → CONFIRM` change on any registered tool is red in `:app` regardless of
which source declared it.

---

## 9. Master Plan §3.6 rows addressed to this block

| Row | Answer |
|---|---|
| **`B1`** — do not grow `GoalShape` | **Respected, and tested at scale.** A1′ proved the generic `Free` arm works for a second tool; this block runs it for ~15 authored plus ~100 dynamic tools and adds **no** `GoalShape` value. §13 pins the count |
| **`B2`** — `LauncherApps` + shortcuts | **Included** — §4, §5 |
| **`B3`** — selection before the planner | **Included, with its justification corrected** — §6.1. The prompt half is re-addressed with a measurement, not deferred silently |
| **`B4`** — Tier-0 intents | **Partly included, partly rejected with a reason and re-addressed** — §7.2, §7.4 |

---

## 10. Doctrine

- **`DOC-ILM-2`** (provenance; `M-A2` criterion 2) — this block is what actually closes the "≥15 tools
  from ≥2 levels" half. A third level appears (`app_shortcut`), and a dynamic tool's provenance must
  reach the user the same behavioural way A1′'s does — rendered and held by a test that reads the
  surface, never by a structural scan.
- **`DOC-ADL-1`** (one risk → gate) — unchanged and re-used. The first `CONFIRM` tool exercises it on
  a real path for the first time. The A1′ caveat stands: closure rests on the single call site, not on
  `ConsentPolicyTest`'s wake-up test.
- **`DOC-ADL-3`** (offline parity) — the three parity tests must stay green with the registry an order
  of magnitude larger, and nothing in §5's refresh path may touch the network.
- **`DOC-HMA-2`** (a level change stops the loop) — **not closed here.** This block adds a third level,
  which makes the question larger, not answered. Address stays A4′.
- **`DOC-ILM-3`** (the trace matches reality) — §8.1 is its direct service: a throw that killed the
  process recorded nothing; a throw that becomes `Failed` records the truth.
- **`DOC-HMA-3`** (irreversible marked before execution) — `uninstall_app` is the first tool where
  `DURABLE` is a real question. Note the A0.5 finding: `DURABLE_EFFECT` is unreachable as a consent
  trigger at `CONFIRM` or above, because `checkpointFor` takes the `RISK_LEVEL` branch first. The user
  is still stopped; the trace is less specific than it looks. Named, not fixed here — A4′.

---

## 11. Boundaries — what this block does not move

Registry as the only path to the world, one `ToolExecutor` implementation, one call site, argument
validation, consent gate, loop bounds, trace, egress allow-list. All present before this block and
untouched by it — which is the whole point of the growth rule: tool #21 gets them free. Both call-site
guards keep holding, re-anchored if a file moves, never weakened.

`ActionIds` — not touched (seven values, frozen). `OutboundContextPolicy.ALLOWED` — not touched (G1).
`GoalShape` — not grown (`B1`). `ObservedFact` / `CommandFailure` / `ArgType` — not grown (owner, A1′
F5/F6). The `Failed`/`Completed` divergence of A0.5 §6.3 — deliberately untouched; a diff that makes
the two consumers agree is a revert.

---

## 12. Non-goals

- Tools rendered to the model planner (G1).
- Free-text-argument tools (G5, §7.4).
- The fail-open class at `RouteCommandUseCase:147-150` — one instance was fixed in A1′, the class is
  A4′'s, and this block does not widen it.
- Wall-clock budget, re-planning, staleness re-checks on resume, level-change gating — A4′.
- `AppFunctions` / MCP adapters — the federation is ready for them; neither is this block.
- Learning or usage-ranked selection (§6.4).

---

## 13. Verification

**Gate**, exactly and with no `tail` anywhere in it:

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
```

`--rerun-tasks`, never `--rerun`. A number counts only from a run that printed
`N actionable tasks: N executed`, and is read from the JUnit XML under `build/test-results/`, never
from the console. Baseline to compare against: **1314 tests, 0 failures** (`1ef158d`) — not any number
quoted in an ADR.

Tests this block must carry, beyond per-task unit coverage:

1. **A seam-crossing test per new seam.** The block's most expensive lesson, three times now: four
   layers each fully tested in isolation summed to a dead capability. New seams here are
   *dynamic registry → federation → validator → worker* and *selector → planner → session*. At least
   one test per seam runs the real objects end to end.
2. **The federation sees a changed tool set** through both `all()` and `find()`, after construction.
3. **A shortcut that disappears between planning and invocation** yields `Failed`, not a crash and not
   a success.
4. **A throw from a worker** yields `Failed` and an ordinary trace event — asserted at the engine, with
   a deliberately throwing fake, not at an adapter.
5. **The registry-keyed permission guard** — mutation-proved, including the two blind spots it exists
   to close (an intent built in a helper; a registered tool with no worker branch).
6. **Risk pinning over the production federation** — `SAFE → CONFIRM` on any registered tool is red.
7. **Selection**: authored beats dynamic; a dynamic candidate without its app token declines; two
   equal candidates decline; an authored trigger shadowed by a shortcut name still wins.
8. **Reachability for every new authored trigger** — run through the real `RuleBasedIntentMatcher`, as
   `ToolVocabularyReachabilityTest` does. Six of A1′'s thirteen proposed triggers could never fire;
   a trigger enters this spec as *intent* and enters the code only after the matcher agrees.
9. **`GoalShape` value count is still two** — pinned, not assumed.
10. **Parity** — `localOnlyMode` / no provider / offline behave exactly as today, with the model
    planner's call count asserted at zero.
11. **Locale completeness** for every new authored string, in the same commit.
12. **The prompt measurement** of §6.1, reported as a number in the ADR.

**Mutation protocol.** Every new guard goes to a separate `mutation-prover` place; the author's own
green run proves nothing — in A1′ that rule caught two guards that were vacuous and one assertion that
was unfalsifiable. A legitimate-growth mutation is run too, so the guard is shown not to over-pin.
Restoration is `trap … EXIT` + `git checkout --`, never `cp`, and the legitimate edit is committed
**before** the file is mutated.

**Device measurement** (§3.1 rule) is agent-driven and closes nothing: it fills §7.2's cells and
answers §5.3. Acceptance is the owner's, and is a separate run at the end of the block, written as a
runnable checklist the way A0's and A1′'s were. The agent database on the device is read **only**
through `tools/device/pull-agent-db.sh`.

---

## 14. Work order

Ordered so that each phase leaves a coherent product, and so that the block can be closed early at a
phase boundary if it grows beyond one session's reach.

- **Phase 0 — preconditions (§8).** Containment at the call site; registry-keyed permission guard;
  risk pinning. No new tool ships before this phase is green and mutation-proved.
- **Phase 1 — the dynamic seam (§4).** Federation derives per call; the shortcut source's snapshot and
  refresh; `DynamicToolNames`; device measurement of §5.3 **before** the adapter is written.
- **Phase 2 — selection (§6).** `ToolSelector`, its policy, its guards. Phase 1's hundred descriptors
  are what make this testable against reality rather than against a fixture.
- **Phase 3 — authored mass (§7).** Device measurement fills §7.2 first; then descriptors, workers,
  triggers, strings in `en`/`ru`/`tr`, reachability.
- **Phase 4 — close.** Owner acceptance checklist, ADR, `CLAUDE.md` + `current-status.md`, track plan
  §HANDOFF rewritten, ledger emptied before it is deleted, commit proposed to the owner.

Each phase is subagent-driven per §0: fresh subagent per task, review between tasks, mutation-prover
for every new guard, and the controller re-checks the tree rather than trusting a brief — a dispatch
that says "create file X" is verified against the tree before it is sent.

---

## 15. The one fork that is not the agent's (owner)

**`"sayaç ayarla"`** — the Turkish trigger for `set_timer`. Proved reachable and unshadowed by
`ToolVocabularyReachabilityTest`, but `sayaç` reads as a counter/meter rather than a kitchen timer. The
2026-09-10 acceptance did **not** judge it: no Turkish speaker was present and no `tr` string was typed
on the phone. "Presumably fine" is not an acceptable answer to this row.

**Agent's recommendation:** remove it, keeping `"zamanlayıcı ayarla"` — the `tr` column stays reachable,
both guards stay green, and a false trigger on a tool that performs an effect costs more than an absent
one. **The verdict is the owner's**, and it is a code change (`ToolVocabulary`'s `tr` set), not a
documentation one.

**Standing, and larger than this row: no block of this track has ever been accepted on the phone in
`tr` or `en`.** Every device round to date ran `ru-RU`. This block adds ten to a hundred tools whose
triggers are localized in three languages; the acceptance checklist will offer an `en` pass as an
option, and whether it is run is the owner's call.

---

## 16. Success criteria

1. **≥15 real tools from ≥3 levels are registered and reachable on the owner's phone**, with the count
   reported broken into *acting* and *navigating*, and the acceptance run by the owner — Master Plan §2
   criterion 2, closing milestone `M-A2`.
2. A tool set that changes while the process lives is visible through **both** faces of the federation,
   held by a test, and a vanished tool yields `Failed` rather than a crash or a false success.
3. A throw from any worker is contained **in the engine**, at the single call site, proved with a
   deliberately throwing fake — and the per-worker nets are no longer the only thing standing.
4. The permission guard is keyed on what the registry declares; both of its predecessor's measured
   blind spots are red under mutation.
5. Declared risk is pinned over the production federation, so a future adapter cannot ship without one.
6. Selection declines on ambiguity, never guesses, and an authored trigger cannot be shadowed by a
   third-party label.
7. `GoalShape` still has two values; `ActionIds`, `OutboundContextPolicy.ALLOWED`, `ObservedFact`,
   `CommandFailure` and `ArgType` are untouched.
8. Every §7.2 cell was filled by a measurement on the SM-A325F, and no Android claim in the shipped
   documents rests on inheritance.
9. Gate green at ≥1314 tests, 0 failures, from a run that printed `N actionable tasks: N executed`;
   every new guard mutation-proved by a separate place.
10. `B2`, `B3`, `B4` each answered in this spec as included / rejected-with-reason / re-addressed, and
    the ADR carries the same three answers.
