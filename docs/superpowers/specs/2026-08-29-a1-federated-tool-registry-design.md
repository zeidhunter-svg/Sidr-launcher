# A1′ — Federated ToolRegistry (Design Spec)

> **Status: PROPOSED (2026-08-29).** A1′ turns the registry from *one source projecting the launcher's
> own actions* into a **federation of adapters over one vocabulary**, and closes the three doctrine
> debts that federation is the precondition for: `DOC-ILM-2` (provenance), `DOC-ADL-1` (one risk → gate
> point) and the A1′ half of `DOC-ADL-3` (the egress claim, mechanical beyond `:domain`).
>
> **The block's deliverable is the boundary, not the capability.** Two tools arrive on the phone
> because composition cannot be proved on the product without a second source — not because the block
> is about timers. The tool *mass* (§3.6 `B2`/`B4`) and the selection layer (`B3`) are **`A1″`**, the next
> block, recorded as an obligation in §11 rather than as an intention.
>
> **Governing sources:** Master Plan §3.2 (block definition), §3.6 (`B2`/`B3`/`B4`), §4 (DoD), §5
> (change-control); restart plan Этап 5 and `§HANDOFF`; doctrine matrix rows `DOC-ILM-2`, `DOC-ADL-1`,
> `DOC-ADL-3`, `DOC-HMA-2`; ADR «2026-08-26 — Этап 4.5 (A0.5)» and its four vocabulary findings.
>
> **Rewrites** `docs/superpowers/specs/2026-07-11-a1-tool-capability-design.md`, written 2026-07-11
> against a target architecture that ADR 2/4 has since re-baselined.
>
> **Amendment 2026-08-29 (while writing the implementation plan) — reachability.** The spec as first
> written chose two tools without asking *what plans them*. Android's only planner is `TemplatePlanner`
> over `GoalShape`, which `B1` freezes, and whose only production shape is `AppNotInstalled`. The two
> Tier-0 tools would therefore have been **registered and unplannable** — the "a capability exists but
> is never offered" failure this very spec names as `B3`'s risk, arriving two blocks early. §8.4 adds
> the one thing that fixes it without buying a taxonomy, and §16's criteria are re-stated against it.

---

## 1. Goal

One `ToolRegistry` contract, N adapters. Concretely, after this block:

- the engine reaches the world through **one** `ToolExecutor` implementation and **one** call site, as
  it does today, while **N sources** supply tools behind it;
- every tool declares **where it came from** (`level`) and **whether its effect crosses the device
  boundary** (`effect`), and an `EXTERNAL` tool's provenance reaches the user's eyes;
- the mapping `risk → gate` exists in exactly **one** place, so a risk level inserted below `CONFIRM`
  cannot be gated by three call sites and missed by the fourth;
- a second **real** source runs on the phone, so composition is a measured property of the product and
  not a property of the test fixtures.

Non-goal, stated first because it is the most likely misreading: **A1′ does not make the launcher able
to do many things.** It makes it able to be *given* many things safely. The doing is `A1″`.

---

## 2. Owner-resolved forks (2026-08-29, before any code)

| # | Fork | Decision |
|---|---|---|
| **F1** | The A1 fork reserved by Master Plan §3.2: parallel tool vocabulary vs. evolving `ActionCatalog` in place | **Parallel vocabulary + federation, with identity C.** `ToolId`/`ToolDescriptor` stay the agent's vocabulary; `ActionCatalog` becomes **one adapter among N** — which it already is, in thirty lines. `ActionIds` is not touched. **Identity C:** a projected tool's `ToolId` is *derived* from its `ActionId` rather than hand-copied |
| **F2** | Block scope: boundaries, or boundaries + tool mass | **Boundaries + exactly one real second source.** `B2`/`B4` mass and `B3` selection become **`A1″`**, a new block recorded in Master Plan §3.2/§3.6 with an address |
| **F3** | Which second source proves composition on the phone | **`set_timer` + `open_system_settings`** (one Tier-0 adapter, two tools, arities 1 and 0). Revised from `set_timer` + `dial` during the second design pass — see §8. **Correction 2026-09-05: this cell read "zero new permissions" and that premise was false** — `AlarmClock.ACTION_SET_TIMER` requires `com.android.alarm.permission.SET_ALARM`, undeclared until the device-acceptance fix, so the tool could never run. **The decision stands; the reasoning that reached it does not.** What "zero permissions" was standing in for — that this pair drags in no consent machinery, no runtime grant flow and no permission-education surface — remains true: `SET_ALARM` is `protectionLevel: normal`, granted at install, never prompted. And the comparison that drove the revision is unchanged in direction: the rejected `dial` needs `CALL_PHONE`, a **dangerous** permission with a runtime grant flow, so `set_timer` is still strictly the cheaper of the two. What does **not** survive is the use the claim was put to: "zero permissions" doubled as grounds for not checking the permission surface at all, and that is precisely how a dead capability shipped. See §8.1 and `ToolPermissionManifestGuardTest`. **Second correction 2026-09-10:** the same device run also falsified this pair's *other* stated justification — "neither skips the OS's own UI, the final act is the user's" — for `set_timer`, which starts its timer on invocation. Owner ruling 2026-09-10: **`SAFE` stands, the reason is replaced** (reversible, immediately visible, provenance disclosed, nothing leaves the device). The F3 decision itself is again unaffected; §8.1 carries the corrected reasoning |
| **F4** | The type of a tool level in `commonMain` | **Open value class over `String`** (`ToolLevel`), applying A0.5's measured contrast directly: an open value type costs zero core edits per addition, a closed sum costs four files in three modules |
| **F5** | The A0.5 finding "consent fires **before** argument binding", addressed `A1′/A4′` — a slash, i.e. no owner | **A4′.** The order `validate → checkpointFor → resolve` is a property of the **runtime**, deliberately fail-safe, and reordering it changes trace shape on process death — which is A4′'s subject. A1′ records the address as a line, not as a slash |
| **F6** | `ArgType` — one of the four vocabulary findings A0.5 addressed to A1′ — now that a genuinely non-string argument exists (timer duration) | **Not extended.** Answered with a measured reason rather than deferred — see §10.4 |
| **F7** | Which tools, judged by the end product rather than by the block | Owner delegated F3 and F6 to the agent on the criterion "what is better for the Agentic OS in perspective". Both answers, and the reasoning, are recorded in §8 and §10.4 so they are overturnable on their merits |

---

## 3. Verification of the brief's premises — what actually holds

`§HANDOFF` makes A1′'s **first action** a hostile read of the A0.5 ADR, asking not "is it accurate?"
but "does it say what A1′ needs?", and forbids carrying any number from it into this plan unmeasured.
Both rules were followed. Findings are about **documents**, and each has an address.

### 3.1. The two load-bearing numbers survive re-measurement

| Claim in the A0.5 ADR | Re-measured | Result |
|---|---|---|
| "one `GoalShape` value — four files in three modules" | commit `fbd932e` | **Holds exactly.** `AgentGoal.kt` + `TemplatePlanner.kt` (`:domain`), `AgentSessionMappers.kt` (`:data:repository`), `AgentSessionPresentation.kt` (`:feature:launcher`); the fifth file in the commit is a test |
| "three new `ToolId`s — zero core edits" | commit `ba1f88c` | **Holds.** The commit touches `consumer/jvm` only |

The central contrast is therefore usable. §3.3 records what it does **not** measure.

### 3.2. `DOC-ILM-2` has no footing at all, and A0.5 never looked for one

The word **provenance** appears **zero** times in the A0.5 ADR, spec and plan (every hit in
`decisions.md` belongs to the DS-era typography work). `domain/tool` contains no `provenance`, no
`tier`, no `level`. The block that built the project's **second tool source** never asked "where did
this tool come from, and how would the trace say so?" — which is the whole of `DOC-ILM-2`. Largest
silence in the document relative to this block. → closed here, §6.

### 3.3. A0.5 is evidence of **portability**, not of **composition**

Two `ToolRegistry` implementations exist, but they **never coexist in one registry**: Hilt binds the
port 1:1 (`AgentProvidesModule.kt:50`), and `SandboxToolSource` lives in another process. The block
therefore yields **no observation** about `ToolId` collision, source order, precedence, or what `all()`
means over N sources — precisely A1′'s subject. "Two adapters over one scale" reads as groundwork for
federation; it is groundwork for portability, and the ADR does not draw the distinction.

Consequence for §3.1's contrast: it measures **addition**, not **composition**. "Three ids, zero core
edits" is true exactly because `ToolIds` and `SandboxToolIds` are two hand-written objects that never
meet. In a federation they meet, and uniqueness becomes a core concern with a cost the table does not
contain. The number must not be carried into this plan as "cheap".

### 3.4. Question 3's answer is most of `DOC-ADL-1`'s material, under the wrong label

The A0.5 answer — risk *assignment* belongs to the adapter, the *scale* belongs to the core, and the
gate reads `maxOf(plan, registry)` — is the substance A1′ needs for `DOC-ADL-1`. The ADR files it as an
answer about portability and never names the debt it feeds. Recorded so the next reader does not have
to re-derive it.

### 3.5. Federating the registry implies federating the **executor**, and no governing document says so

Found while designing, and it resizes the block. `AgentExecutor` holds **one** `ToolExecutor` and calls
it once (`AgentExecutor.kt:199`); Hilt binds that to `SystemIntentToolExecutor`, whose `actionFor` is a
`when (id)` over `ToolIds` with `else -> null` → `Failed(Generic)`
(`SystemIntentToolExecutor.kt:46-52`).

So a second source registered **without** a second executor produces tools that the planner sees, the
gate admits, and the call drops silently into a generic failure. The compiler is silent. Master Plan
§3.2's diagram and restart-plan Этап 5 are drawn for the **registry only** and do not mention execution
at all. This is a finding about the **plan**, not about the ADR. → §4.

### 3.6. `§HANDOFF`'s own sentence about `args_json` is false as written

> «`args_json` на диске хранит `ArgSource`, а не значения»

`AgentSessionMappers.kt:218` encodes `ArgSource.Literal` **with its value**. The sentence is true in
intent — a `FromStep` re-binds against surviving observations instead of replaying a value frozen at
plan time — and false as read. It matters because this spec's first draft reasoned from the literal
reading and drew a wrong conclusion from it (§8.2). Corrected here; the handoff line should be fixed in
the closing pass.

### 3.7. `DOC-ADL-3` also carries an A1′ debt, and it is not named in §3.2

The matrix cell for `DOC-ADL-3` ends: «"наружу не уходит ничего" держится механически только для
`domain/agent` + `domain/tool`; за их пределами это **факт**… а не проверка ← долг A1′ **вместе с
федерацией источников**». Master Plan §3.2 lists only `DOC-ILM-2` and `DOC-ADL-1`. The block therefore
closes **three** doctrine debts, and the third is sourced from the matrix, not from the block
definition. → §9.3, §12.

---

## 4. The shape — federation lives **under** the boundary, not through it

### 4.1. Types

```kotlin
// domain/tool — commonMain, stdlib only
@JvmInline value class ToolLevel(val value: String)   // open: "in_app", "system_intent", "sandbox", …

/** One federated source: what it offers, and the worker that runs it. */
interface ToolWorker { suspend fun invoke(invocation: ResolvedInvocation): ToolResult }

data class ToolAdapter(
    val level: ToolLevel,
    val registry: ToolRegistry,   // read-only, side-effect free — unchanged port
    val worker: ToolWorker,
)
```

### 4.2. One federation object, two faces

The engine's view does not change: it is handed a `ToolRegistry` (read) and a `ToolExecutor` (write),
exactly as today.

```kotlin
class ToolFederation(private val adapters: List<ToolAdapter>) {
    val registry: ToolRegistry   // FederatedToolRegistry over the same list
    val executor: ToolExecutor   // FederatedToolExecutor over the same list
}
```

**Both faces are derived from one object, and that is load-bearing rather than tidy.** Two combinators
each taking `List<ToolAdapter>` would receive their list *by wiring convention*; if the Hilt graph ever
provided them separately they could diverge, and the registry would advertise a tool the dispatcher
cannot route. That is the exact shape of finding **F2** of the A0 review — the gate read `risk` from
the persisted plan and `permissionGate` from the live registry, two sources for one decision. One
object makes "the same list" a property of construction.

### 4.3. What this does to the call-site invariant — stated honestly

`FederatedToolExecutor` becomes the **only** implementation of `ToolExecutor`; per-source workers
implement `ToolWorker`, which the engine never sees. The chain is:

```text
AgentExecutor ──(one call site)──▶ ToolExecutor (one implementation) ──▶ ToolWorker (N)
```

**This is not a strengthening, and this spec must not be read as claiming one.** An earlier draft of
this design said the guard "gets stronger"; that was the block-A0.5 signature defect — prose written
from intent, assertion written from convenience — caught in self-review and recorded here rather than
silently corrected. The truth is narrower: the invariant is **preserved at equal strength, and only if
the second guard is written**. The guarded surface grows from one type to two, and the honest statement
is that one hop was added and both hops are guarded (§9.1).

### 4.4. A dispatch miss is unreachable, and is therefore **not** named

Because the registry and the dispatcher read the same adapter list, and because
`InvocationValidator.validate` already rejects an unregistered tool as `UNKNOWN_TOOL` against **that
same** registry, "the dispatcher found no worker" cannot occur in a well-formed graph. `CommandFailure`
therefore does **not** gain a "no such tool" variant. Naming an unreachable branch is the `F2`/`D10`
disease this repository has caught three times; the fail-closed `else` remains, labelled
defence-in-depth rather than mechanism.

---

## 5. Identity and collision

### 5.1. Identity (F1, identity C)

A projected tool's id is **derived** from the action's, in the adapter:

```kotlin
ToolId(actionId.value)      // in SystemIntentToolSource, not in domain/tool
```

The derivation lives in the adapter on purpose: `domain/tool` keeps **no edge** to `domain/action`, the
edge A0 deliberately left absent so this fork stayed open. `ToolIds`' two constants stop being
hand-copied strings, and `ToolIdsTest` changes from pinning a copy to pinning the derivation. Tools
that are not projections (`set_timer`, the sandbox trio) mint their own ids — `ToolId` stays an open
value class, so this costs nothing.

**Rule to record rather than leave implicit:** identity C binds **projections only**. `ActionIds` is
frozen at seven values byte-for-byte (ADR 3/4), so a Tier-0 tool has no `ActionId` and must mint.

### 5.2. Collision — first adapter wins

`ToolId` is globally unique across the federation. On a duplicate, **the first adapter in the list
wins** and the later declaration is dropped from `all()` and `find()`.

The alternative considered and rejected was symmetric exclusion (drop both). Under identity C and a
future MCP source it is a **capability-denial vector**: a third-party server naming a tool `launch_app`
would take the built-in down with it. First-wins, with built-ins first in the composition root,
preserves the built-in.

Precedence comes from the **construction order of the federation**, which is ours — so it needs no
ordering on `ToolLevel`, and the open-value-class decision (F4) stands unstressed.

A guard test asserts the production federation contains **no** collision, which is what makes the
runtime rule defence-in-depth rather than the mechanism.

---

## 6. Provenance — `level` and `effect` (`DOC-ILM-2`)

### 6.1. Two fields, and why they are two

```kotlin
enum class ToolEffect { LOCAL, EXTERNAL }

data class ToolDescriptor(
    val id: ToolId,
    val level: ToolLevel,      // no default
    val effect: ToolEffect,    // no default
    …
)
```

`level` answers *who supplies this* and grows without bound (AppFunctions, MCP, Accessibility) → open.
`effect` answers *does this cross the device boundary* — a yes/no → closed at two. This is the same
pair the design system already renders: `SYSTEM INTENT · EXTERNAL`
(`sidr-design-system-master-plan-v1.2.md:981`), which exists on the surface today only as a hardcoded
mock string (`ActivityPreviewScreen.kt:70`).

**Neither field takes a default.** A default `effect = LOCAL` would let an adapter that forgets the
field silently claim locality — fail-**open**, on the exact field the doctrine rule hangs from. The
absence of a default costs four edits at construction sites (`SystemIntentToolSource.project`,
`SandboxToolSource`, `FakeToolRegistry.withA0Tools`, `FilePlannerTest`) and buys a compiler error where
the alternative buys a lie.

### 6.2. Third-party origin is **not** built

For built-in sources, `level` + `effect` **is** the whole provenance — the DS line is literally
expressible from the two fields. A field naming a third-party origin (an MCP server identity) is not
added, because no third-party source exists: building it now is the "taxonomy for a consumer that has
not arrived" that Master Plan §3.4 and `B1` warn against, with the `:data:ai-local` precedent behind
them.

### 6.3. The test must be behavioural, and that changes the rule's type

The obvious guard — "an `EXTERNAL` tool carries provenance" — **cannot fail** once `level`/`effect` are
required non-null fields: the compiler already enforces it. Writing it would be `F2`/`D10` again, and
the first draft of this design contained exactly that.

The non-vacuous property is that provenance **reaches the user**: an `EXTERNAL` tool's consent /
execution surface renders its provenance line, and removing the rendering turns the test red. That test
is `unit`/`semantics` in the feature layer, and the matrix records `DOC-ILM-2` as `arch-guard`.

> **Change-control item (§5).** Closing `DOC-ILM-2` requires changing its verification type from
> `arch-guard` to `unit`. Precedent: `DOC-ILM-3`'s type was corrected the same way, with its own
> journal line, and the matrix records that «смена типа проверки в строке — тоже change-control». This
> is declared **here, before code**, not discovered mid-block.

### 6.5. Putting provenance on a surface is the exact bug a barrier already anticipates

`DomainIdentifierLeakGuardTest` was written after a domain enum reached a UI sink directly: English
looked fine because the identifier *is* English, and only `ru`/`tr` announced untranslated text. Its
KDoc names the future risk by name — «`A1`'s planned `ToolId`/`ToolTier`/`ToolEffect`/`ActionCategory`
vocabulary are exactly this shape of risk if any of them ever reach a UI sink directly».

§6.3 asks for exactly that surface, so the warning applies to this block and not to a later one:

- `ToolLevel.value` and `ToolEffect` **never** reach a UI sink. The surface maps them to
  `sidrString(R.string.…)`, as `HardcodedUiTextGuardTest` and `StringSeamGuardTest` already require and
  as the hard rule states — user-facing text originates in the feature layer, never in `domain`.
- `en`/`ru`/`tr` ship in the same commit for every level and effect rendered (`LocaleCompletenessGuardTest`).
- The barrier's own KDoc is updated in this block: it forecasts `ToolTier`, and the type this spec
  introduces is `ToolLevel` — the doctrine matrix is the governing text and it says «уровень
  инструмента» (`DOC-HMA-2`, and `DOC-ILM-2`'s debt note «уровни инструментов ещё не введены»). The
  divergence is a naming one and is resolved toward the doctrine, not left for a reader to notice.
- An **open** `ToolLevel` means a level with no string resource is possible. The surface therefore
  fails closed to a generic provenance label rather than to the raw value, and a test holds that.

### 6.4. Where the two `effect` values actually live — named, not implied

After this block, **every tool registered on Android is `EXTERNAL`** (`launch_app` and
`play_store_search` reach other apps; both Tier-0 tools reach system apps). The `LOCAL` value is
carried by `:consumer:jvm`'s sandbox tools, whose effects never leave the process. So the field is
non-vacuous **across the federation** and constant **on Android**. Said plainly here so no reader
mistakes the Android constancy for a design claim; a `LOCAL` Android tool arrives when `open_settings`
or `show_apps` is projected, which is `A1″`'s business, not this block's.

---

## 7. One risk → gate point (`DOC-ADL-1`)

### 7.1. The debt is a latent defect, not a missing abstraction

Four independent sites decide gating from risk, in two spellings:

| Path | Site | Condition |
|---|---|---|
| agent | `AgentExecutor.kt:285` | `risk >= CONFIRM` |
| model | `RouteCommandUseCase.kt:156` | `risk != SAFE` |
| learned memory | `ResolutionPreferencePolicy.kt:29` | `risk == SAFE` (permits auto-resolve) |
| UI | `EvaluateLearnedChoiceDisplayStateUseCase.kt:21` | `risk != SAFE` |

With three levels the two spellings coincide, so the four agree today. **Insert a level below
`CONFIRM` — exactly the `D11` scenario this block owns — and they diverge silently:** the new level is
gated by the model, memory and UI paths and **not** by the agent path. `DOC-ADL-1` therefore has a
wake-up date, not merely an absent abstraction.

### 7.2. What unifies is the **predicate**, not the outcome

The four sites return four different things (`ConsentCheckpoint?`, "needs confirmation", "may
auto-resolve", a display state). Merging the return types would be forcing. One pure function in
`domain/action`, beside `ActionRiskLevel`:

```kotlin
fun requiresConsent(risk: ActionRiskLevel): Boolean
```

Each site keeps its own question and stops spelling the predicate itself. Stated explicitly because
"the four collapse into one function" would send an implementer to merge four return types.

### 7.3. The test that must not be vacuous

Two tests, and the second is the point:

1. **Parity:** for each of the three current levels, each of the four paths gates exactly as it does
   today. This is the DoD's parity line (`DOC-ADL-3`) for the router path in particular.
2. **The wake-up test:** a level inserted below `CONFIRM` is gated identically by all four paths. It is
   written against the predicate, so it is meaningful the day `D11` goes live rather than the day
   someone remembers.

---

## 8. The second source — `set_timer` + `open_system_settings`

### 8.1. What is built

One `Tier0IntentAdapter` at level `system_intent`, one new install-time permission:

| Tool | Args | Risk | Effect | Intent | Permission |
|---|---|---|---|---|---|
| `set_timer` | `duration` (1, required) | `SAFE` | `EXTERNAL` | `AlarmClock.ACTION_SET_TIMER`, UI **not** skipped | `com.android.alarm.permission.SET_ALARM` (`normal`, install-time) |
| `open_system_settings` | none (0) | `SAFE` | `EXTERNAL` | `ACTION_SETTINGS` | none |

**Corrected 2026-09-05.** This section read "zero new permissions" and the table had no permission
column at all. `ACTION_SET_TIMER` is refused by `ActivityTaskManager` without `SET_ALARM`, so the
headline tool of this block's second source failed on every invocation until the manifest declared it
(owner device acceptance). The permission column is now part of the table because *which permission an
intent requires* turned out to be a property of a Tier-0 tool that a design can silently omit — and
A1″'s entire content is more Tier-0 intents. `ToolPermissionManifestGuardTest` makes the omission a
red test rather than a device-day discovery.

Arities 0 and 1 stress the schema at both ends — the shape that already earned its keep in A0.5's
sandbox trio.

**Corrected 2026-09-10 — owner decision on `set_timer`'s `SAFE`.** This paragraph used to end
"Neither skips the OS's own UI: the 'prefilled but not sent' form leaves the final act with the user,
which is what makes `SAFE` honest." The owner's device run falsified that for `set_timer`: with
`EXTRA_SKIP_UI = false` the Samsung clock opened **with the timer already counting down**
(`Пауза`/`Удалить`, not a start button, SM-A325F 2026-09-05). The flag governs whether the responding
app shows its UI, not whether it acts.

The owner's ruling, 2026-09-10: **the level stands, the reason does not.** `set_timer` stays `SAFE`,
and what that now rests on is stated rather than assumed — the effect is trivially reversible (one
tap), immediately visible (the clock opens in front of the user; nothing happens in the background),
disclosed (`EXTERNAL` renders a provenance line, the only such mechanism a `SAFE` step gets since the
consent gate never fires for it), and local (nothing about the invocation leaves the device). No risk
level changed, and no code changed with this correction — only the claims did.

`open_system_settings` is a **different** case and is not covered by the withdrawal: opening a
settings screen performs no act, so it has nothing to reverse and "the final act is the user's" is
literally true of it.

Master Plan §3.6 `B4` is cited in the old sentence as the "prefilled but not sent" shape. That
citation no longer supports it for `set_timer` — see the note now carried in `B4`'s own cell — and
A1″ must **measure** the shape for each further Tier-0 intent (`SENDTO`, calendar `INSERT`, `DIAL`)
instead of inheriting it.

`open_system_settings` is deliberately **not** `ActionIds.OPEN_SETTINGS`: that one opens the launcher's
own settings and belongs to the `in_app` level. The pair is itself an illustration of why `level` and
`effect` are separate fields.

### 8.2. Why not `dial`, and a correction to this spec's own first draft

The first draft chose `set_timer` + `dial` and justified dropping `dial` on the ground that its
argument would put a **phone number** into `args_json` on disk — new territory. That justification was
**overstated and is withdrawn**: `agent_session.goal_text` already holds the raw command text, so a
number spoken into the launcher is already persisted whether or not `dial` exists. `dial` would add a
second copy in a structured field, not a new category of data.

The reason `dial` is not chosen is different and survives:

- The block's job is the boundary, and for `DOC-ILM-2` the informative case is a tool that is
  `EXTERNAL` **and** ungated — where provenance is the only mechanism telling the user the truth. A
  `CONFIRM` tool is already stopped by consent, so it exercises the weaker case.
- The risk contrast the federation needs already exists among the projected tools (`launch_app` `SAFE`,
  `play_store_search` `CONFIRM`). What the new source must contribute is a second **level**, which both
  chosen tools do.
- The retention question that `dial` raises thinly is raised **sharply** by `SENDTO` and calendar
  `INSERT` in `A1″` — message bodies and event contents. It belongs there, answered once, not
  half-answered here. → recorded in §11 as an obligation on `B`.

### 8.4. Reachability — one generic arm, not a taxonomy

**The problem.** Registering a tool does not make it reachable. `TemplatePlanner.plan` is exhaustive
over `GoalShape`: `AppNotInstalled` yields the launch→store plan, `Free` yields `NoPlan`
(`TemplatePlanner.kt:44-51`), and `RouteCommandUseCase` builds only `AppNotInstalled`
(`RouteCommandUseCase.kt:96-100`). No goal on the phone can invoke a Tier-0 tool.

**The rejected fix.** Adding `GoalShape.SetTimer` and a planner arm per tool works and is cheap once.
It is refused because it is **linear per tool**: `A1″`'s twelve tools would need twelve shapes and
twelve arms, which contradicts in code the exact thesis this block exists to establish — that tool #21
is free. It would also spend `B1`, whose stated failure mode is precisely a hand-written taxonomy.

**The fix.** One generic arm, and `GoalShape` gains **no** value:

- `RouteCommandUseCase` gets step **2b**, immediately after the existing agent branch and above the
  `localOnlyMode` check: when FastPath is undecided, build `AgentGoal(text, GoalShape.Free(text))` and
  offer it to the planner. The use case learns **nothing** about tools — it already fails open on
  `NoPlan`, so with no matching tool the behaviour is byte-identical to today.
- A second `Planner`, `ToolMatchPlanner`, lives in `:data:repository` (where localized vocabulary
  already lives, beside `RuleBasedIntentMatcher`) and plans `GoalShape.Free`: if deterministic matching
  yields exactly **one** registered tool and its required arguments are fillable, emit a one-step plan;
  otherwise `NoPlan`. `TemplatePlanner` is untouched and keeps `AppNotInstalled`.
- The two are composed behind the existing `Planner` port. `commonMain` gains nothing: text matching
  never enters `:domain`, so the JVM consumer is unaffected and the KMP rule holds.
- `StepRationale` gains **no** value either. A one-step plan is `GOAL_DIRECT`, and the step's line stops
  assuming a launch: the feature layer maps `ToolId` → string resource, which is what
  `ToolDescriptor`'s own KDoc already prescribes ("the surface maps `id` to a string resource in the
  feature layer"). §10.3's answer for `StepRationale` is unaffected.

**Why this is doctrinally clean, not a loophole.** Step 2b is deterministic, consults no model and
transmits nothing, so all three "understanding unavailable" states keep their meaning and the chain
stays a chain of early returns with one reportable cause. It changes an outcome FastPath decided —
which `DOC-ADL-3` **explicitly permits** since its 2026-08-22 amendment, with A0's two-step plan as the
precedent. Under `localOnlyMode` a matching command now produces a plan instead of
`UnderstandingLocalOnly`: that is the rule working as amended, and the parity tests state it.

**What it costs, named rather than implied.** The block grows past what fork F2 implied, and it touches
the understanding path — the most acceptance-sensitive code in the app. Every local state
(`localOnlyMode`, no provider, offline) gets an explicit parity test, and the existing invariant test
`all three causes at once — only the outermost is reported` must stay green untouched.

### 8.3. The duration argument

`duration` is the first argument in this project that is not a string by nature. It travels as
`ArgType.STRING` and is parsed **in the adapter**, failing closed on anything unparseable. §10.4 is the
answer to the `ArgType` finding this produces.

---

## 9. Guards — what holds what

### 9.1. The boundary, re-anchored (not weakened, not strengthened)

`ToolExecutorCallSiteGuardTest` is re-anchored to the new shape, in two halves as today:

- exactly **one** call spelled `toolExecutor.invoke(`, in `AgentExecutor.kt`;
- exactly **one** implementation of `ToolExecutor` (`FederatedToolExecutor`), plus its holder and its
  binding.

A sibling guard covers the second hop: `ToolWorker` is called from `FederatedToolExecutor` and nowhere
else. `D2` predicted this exact shape («регекс держателя не видит `Map<…, ToolExecutor>` — вероятная
форма A1′») and is closed by construction: the dispatcher holds workers, not executors.

Both guards must be **mutation-proved**. A green run of a new guard proves nothing (`§HANDOFF`).

### 9.2. Collision

A guard asserts the production federation declares no duplicate `ToolId` (§5.2).

### 9.3. Egress beyond `:domain` — the A1′ half of `DOC-ADL-3`

`AgentVocabularyGuardTest` holds mechanically that `domain/agent` + `domain/tool` cannot **name**
`GenerativeAiEngine` / `CommandPlanner` / Ktor / `HttpClient`; beyond those two packages the claim is a
fact about the single source, not a check. `DoctrineGuardTest` extends it to the federation: the set of
adapters registered in the composition root is **declared**, and no registered adapter names a network
type. That is the honest closure available while every source is local — and it is the check that
starts failing the day an MCP adapter is added without an ADR.

### 9.4. `FakeToolRegistry.withA0Tools()` — a debt this block would otherwise worsen

CLAUDE.md lists it as owned by no block: it mirrors `SystemIntentToolSource` and is pinned to it by
nothing. Federation multiplies the mirror. A parity assertion is added — the cheapest available fix,
and this is the block that makes the debt worse, so it is the block that pays.

---

## 10. The four A0.5 vocabulary findings — answered

DoD §4 requires each to be answered *included / rejected with reason / moved with a new address*.

### 10.1. `ObservedFact` — **not touched** (recorded owner instruction)

The `Failed`/`Completed` divergence of A0.5 §6.3 stays. Owner instruction 2026-08-23 stands: unifying
the two outcomes is not a task, and the asymmetry is held by `CoreVocabularyFreezeGuardTest` and by
`AgentLoopTest`'s named divergence test. **A diff touching either to make them agree is a revert.**
A1′ owns the finding; owning it here means recording that the vocabulary is a closed two-value enum
written for a launcher, not enlarging it under a second source that does not need it.

### 10.2. `CommandFailure` — **rejected with reason** (§4.4)

The concrete reason this block might have touched it — "the dispatcher found no worker" — is made
unreachable by the one-federation-object design. Adding a variant for it would build a branch that
cannot run.

### 10.3. `StepRationale` — **moved, address A4′**

Why a step is in the plan is a planner/runtime concern. A1′ changes neither planner; `TemplatePlanner`
stays deterministic and `FilePlanner` stays the consumer's own. Nothing in federation makes the finding
more or less answerable.

### 10.4. `ArgType` — **rejected with a measured reason**, not deferred

The block produces the first genuinely non-string argument (`set_timer`'s duration) and still does not
extend the enum. Reasons, in the order of their weight:

- **A second flat value does not approach the end state.** `AppFunctions` and MCP are self-describing
  functions with **JSON Schema**: nested objects, arrays, enums. `ArgType{STRING, INT}` is still a flat
  enum and still categorically insufficient; when a real schema consumer arrives the answer is a
  recursive schema type, and the two-value enum would have to be dismantled **together with** the
  branch it forces into `ProposalValidator`.
- **It costs work on the model's inbound boundary.** `ProposalValidator` (`ProposalValidator.kt:47`,
  "String-only in the MVP, so the value type needs no further check") would have to begin validating a
  model-proposed number — a wider surface than the block needs.
- **It is the expensive direction the owner has already chosen against.** `ArgType` is a closed sum;
  F4 chose an open value type for `ToolLevel` on exactly this reasoning.

What is recorded instead is the **measurement**: the first non-string argument arrived; its absence
cost one adapter-local parse and one fail-closed branch; and the repository has already named the two
sites a second value would land in (`InvocationValidatorTest.kt:176-178`, whose type check is vacuous
today and says so; `ProposalValidator.kt:47`). That is an answer with evidence, which is what the A0.5
ADR asked A1′ for.

---

## 11. Master Plan §3.6 backlog rows addressed to this block

| Row | Answer |
|---|---|
| **`B2`** — `LauncherApps` + app shortcuts as a tool source | **Moved, address: `A1″` (next block).** Verified premise: `LauncherApps`, `getShortcuts`, `startShortcut` appear **nowhere** in the repository. Deferred because it is the only *dynamic* source — its tool set varies by device and over time — so it forces `B3` on the same day, and F2 placed mass in `A1″` |
| **`B3`** — tool selection before the planner | **Moved, address: `A1″`, and binding there.** `CatalogSchemaRenderer.render(catalog)` still renders the whole catalog. With five tools that is correct behaviour; the row's own trigger is "the same day as the first real source *at scale*". Recorded consequence: `B3` is a **new failure surface**, not extra volume — a tool that exists but is never selected is a silently missing capability, and it must arrive with its own test |
| **`B4`** — Tier-0 system intents (~12 tools) | **Partially included.** Two of them (`set_timer`, `open_system_settings`) land here as the composition proof; the remaining ~10 move to `A1″`. New obligation attached to `A1″`: `SENDTO` and calendar `INSERT` carry **message bodies and event contents** into `args_json`, which is a retention question this project has not answered — `A1″` answers it, or moves it to A5 with an address (§8.2) |
| **`B1`** — do not grow `GoalShape` | **In force, untouched.** A1′ adds no `GoalShape` value. The rule is A4′'s to lift |

---

## 12. Doctrine

| Rule | Before | After this block |
|---|---|---|
| `DOC-ILM-2` — an `EXTERNAL` tool must carry provenance | `<нет>` ← долг A1′ | **Closed**, by a behavioural test that the provenance reaches the surface. **Requires a change-control entry**: verification type `arch-guard` → `unit` (§6.3) |
| `DOC-ADL-1` — equal risk gets an equal gate regardless of the proposal's source | `<нет>` ← долг A1′ | **Closed** by `requiresConsent`, with the parity test and the wake-up test (§7.3) |
| `DOC-ADL-3` — the egress half beyond `:domain` | «факт, а не проверка» ← долг A1′ | **Closed** to the extent honestly available: the adapter set is declared and no adapter names a network type (§9.3) |
| `DOC-HMA-2` — a risk transition **and a change of tool level** stop the loop | half open ← долг A1′ (уровней нет) | Levels now exist. **This block introduces the vocabulary; whether a level *change* stops the loop is a runtime decision belonging to A4′** — recorded as a half, not claimed closed. Claiming it here would repeat the mistake the matrix already records for this row |

No rule is added and the closed vocabulary of verification types is not touched (both would be
change-control §5). The one change-control item is `DOC-ILM-2`'s verification type, declared in §6.3.

---

## 13. Verification

Gate, one command, JDK 17, never through `tail`:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test
```

Baseline to beat: **1219 tests, 0 failures** (2026-08-26). Counts read from the JUnit XML, not the
console; a suite that returns `UP-TO-DATE` is re-run with `--rerun` for the closing number.

Additional, because this block touches a product surface:

- `:core:ui:verifyRoborazziDebug` if `core/ui` is touched;
- `en`/`ru`/`tr` complete in the same commit for both new tools (`LocaleCompletenessGuardTest`);
- **device acceptance by the owner** — two new capabilities ship, so §4 DoD's device line applies. It
  is small by construction: two tools, one of which takes no argument.

Every new guard is **mutation-proved**, landing asserted before the suite runs, mutation planted and
reverted in **one** shell invocation with `trap … EXIT`, and the tree checked afterwards with a full
`git status --porcelain --untracked-files=all`.

---

## 14. Non-goals

- **No MCP and no AppFunctions adapter.** The federation is built so they are additions; they are not
  additions made here.
- **No tool mass.** `B2`/`B4` beyond the two proof tools, and `B3`, are `A1″`.
- **No `ActionIds` change.** Frozen byte-for-byte (ADR 3/4); identity C reads it, never writes it.
- **No unification of the `Failed`/`Completed` asymmetry** (§10.1).
- **No reordering of consent and argument binding** — A4′ (F5).
- **No rollback or compensation.** `DOC-HMA-3` stays A4′'s; `ToolDurability` remains a marking.
- **No third-party origin field** in the descriptor (§6.2).

---

## 15. Work order

1. `ToolLevel`, `ToolEffect`, and the two new `ToolDescriptor` fields **without defaults**; four
   construction sites updated.
2. `ToolWorker`, `ToolAdapter`, `ToolFederation` with its two faces, in `commonMain`.
3. Collision policy (first adapter wins) + its guard.
4. Android wiring: the existing source/executor pair becomes one adapter at level `in_app`; Hilt
   provides the federation instead of the two ports directly.
5. `:consumer:jvm` wiring: the sandbox becomes an adapter at level `sandbox`; `SandboxToolExecutor`
   becomes a `ToolWorker`. Composition is thereby exercised on **both** consumers.
6. `ToolExecutorCallSiteGuardTest` re-anchored + the `ToolWorker` sibling guard; both mutation-proved.
7. `requiresConsent(risk)` in `domain/action`; the four sites converge; parity test + wake-up test.
8. `Tier0IntentAdapter`: `set_timer` (duration parsed fail-closed) and `open_system_settings`.
9. Identity C: projected ids derived from `ActionId`; `ToolIds` and `ToolIdsTest` re-pointed.
10. `DOC-ILM-2`'s behavioural test + the provenance line on the real surface, routed through
    `sidrString` with `en`/`ru`/`tr` in the same commit, the unknown-level fallback tested, and
    `DomainIdentifierLeakGuardTest`'s KDoc re-pointed from `ToolTier` to `ToolLevel` (§6.5).
11. `DoctrineGuardTest`: declared adapter set, no network type named (§9.3).
12. `FakeToolRegistry` parity line (§9.4).
13. `ToolMatchPlanner` in `:data:repository` + its localized tool vocabulary (`en`/`ru`/`tr`), composed
    behind the `Planner` port; `RouteCommandUseCase` step 2b with parity tests for all three local
    states; the step line keyed on `ToolId` rather than assuming a launch (§8.4).
14. Documents: ADR; `CLAUDE.md` + `current-status.md`; matrix rows for `DOC-ILM-2` / `DOC-ADL-1` /
    `DOC-ADL-3` + the change-control entry for the type change; `A1″` added to Master Plan §3.2 and named as the address in §3.6;
    the `§HANDOFF` `args_json` sentence corrected (§3.6 of this spec).

---

## 16. Success criteria

1. Two sources are registered in **one** federation on Android, and a goal runs end to end through a
   tool from each — on the device, accepted by the owner. Concretely: the existing «no such app» goal
   still runs its two-step plan through the `in_app` adapter, and a timer command runs a one-step plan
   through the `system_intent` adapter. **No registered tool is unreachable.**
2. `find()` and `all()` agree with what the dispatcher can route, **by construction** (one object), and
   a test says so.
3. A duplicate `ToolId` planted in the production federation turns a guard red.
4. Every registered tool declares `level` and `effect`; removing the provenance rendering from the
   `EXTERNAL` surface turns a test red; and no raw `ToolLevel`/`ToolEffect` value reaches a UI sink
   (§6.5).
5. All four risk→gate sites call one predicate; the wake-up test fails if a level below `CONFIRM` is
   gated inconsistently.
6. `:consumer:jvm` runs its goal through the same federation types with **zero** Android artifacts on
   its resolved classpath, as today.
7. Gate green at ≥ 1219 tests, 0 failures; every new guard mutation-proved.
8. `RouteCommandUseCase`'s three local states behave exactly as today when no tool matches, proved by
   test, and the existing mutual-exclusion invariant test is green untouched.
9. Three doctrine rows carry a real test name; `DOC-HMA-2` is **not** claimed closed.
