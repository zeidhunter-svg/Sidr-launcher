# SIDR — Review of the idea register and the manifest

> **Status: A CHECK OF CLAIMS — not a decision, not a spec, not an approval or rejection of ideas.**
> It checks exactly the condition [improvements.md](improvements.md) and
> [manifest-sidros-en.md](manifest-sidros-en.md) set for themselves: "an entry names existing types and
> documents where it leans on them, and those names are checkable claims like any other" (the idea
> file's own rule), "the gap is named, not hidden" (manifest §4). The condition was set and never
> checked.
>
> **A verdict of the form "promote" plans nothing.** It names the next step of
> `brainstorm → spec → plan → build` and a block address — and it carries none of Master Plan §5's
> authority: no line here may be cited as a decision taken.
>
> **Written:** 2026-09-14, tree at `0b2fe06`, branch `launcher--7`, during block A1″ (resume point —
> Task 6, Phase 1; this review does not touch the block).
> **Russian pair:** [improvements-review-ru.md](improvements-review-ru.md) — the two files are kept
> in correspondence.
>
> **Documents checked:** `improvements.md` / `improvements-ru.md` and `manifest-sidros.md` /
> `manifest-sidros-en.md`, all four opened 2026-09-13 in commit `d391350`.

---

## 1. Method, and its boundary

**What was checked.** Every type, function, test and document named in the idea files was read in the
tree: does it exist, what shape is it, and does the code say what is said about it. No claim in §2 or
§3 is taken from an idea document — all are from sources at `0b2fe06`.

**What was NOT checked, and could not be.** The worth of an idea, its product priority, whether the
owner wants to pay for it. That is owner-level, and a verdict of "reject as written" below means
**"the claims the entry rests on are false"**, not "the idea is bad". The distinction matters: entry 4
is rejected on facts, while the need it names is real and has an address.

**Why this was worth doing separately.** The project has paid three times for the class of error "a
name, signature or value a text asserts without checking the tree" — six plan defects caught by A1″'s
pre-flight scan, a seventh found by an implementer, and before those the `set_timer` "zero new
permissions" sentence, written into eight documents and false on the device. An idea register is
precisely the genre where that error is invisible: it compiles nothing.

---

## 2. Claims that did not hold

Nine. Three change an entry's **conclusion**, not its wording.

### 2.1. Entry 4 — `Clarify` belongs to a different port, and it has a producer

**Said:** "`Planner` already returns `ExecutionPlan | Clarify | NoPlan`; the `Clarify` arm is designed
and has no producer. Cost. Low. This entry is mostly wiring plus one surface shape."

**In the tree:**

- The agent port is `PlanningResult`, and it has **two** variants:
  [`ExecutionPlan.kt:69-72`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt#L69-L72)
  — `Planned` and `NoPlan`. There is **no** `Clarify` arm; the port itself is declared at
  [`ExecutionPlan.kt:75`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt#L75).
- `Clarify` exists on a **different** port — the cloud `CommandPlanner`:
  [`CommandPlanner.kt:43`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/CommandPlanner.kt#L43).
- **And it has a producer** — [`ProposalValidator.kt:33`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/ProposalValidator.kt#L33)
  (`action == "clarify"` with a non-blank question) — **and a consumer** —
  [`RouteCommandUseCase.kt:178`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt#L178),
  where `Clarify` becomes a message to the user.

**A consequence the entry does not carry, and it matters more than the error.** No consumer of
`PlanningResult` is **exhaustive**: [`StartAgentSessionUseCase.kt:33`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/StartAgentSessionUseCase.kt#L33)
tests `planned !is PlanningResult.Planned`, [`CompositePlanner.kt:20`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt#L20)
tests `result is PlanningResult.Planned`. A third variant **compiles everywhere** and is silently
swallowed into `Success(null)` → routing falls through with no session, no error and no trace. That is
the same silhouette as A1′'s `CRITICAL` (`GoalShape.Free` threw in `toSessionEntity`, and the
fail-open branch swallowed it with 1285 tests green).

**What the entry actually asks for** is not wiring but the **multi-turn clarification protocol** that
Master Plan §3.3 names as one of A4′'s three unwritten specs: "`PlanResult.Clarify(question)` is a
terminal leaf. The multi-turn 'the agent asked → the human answered → the plan was refined → we
continued' exists in no contract." Today's `Clarify` is exactly that terminal leaf, and it works.

### 2.2. Part 3 C — both cited precedents are hollow

**Said:** "the precedent is in this repository already, in the bundled GeoNames index and `ModelStore`'s
SHA-256 verification."

**In the tree:**

- `ModelStore` was **deleted** in Этап 0.3 with the whole ONNX stack (ADR 2/4). Only KDoc references to
  the deleted path `data/ai-local/.../ModelStore.kt` survive —
  [`BundledCityIndex.kt:22`](../data/prayer/src/main/java/com/sidr/launcher/data/prayer/BundledCityIndex.kt#L22).
  Citing it as a live precedent is separately hazardous: resurrecting that stack is on `CLAUDE.md`'s
  Do-not list.
- The GeoNames index itself has **no integrity check at all**: `data/prayer` contains neither `sha256`
  nor `MessageDigest`. It is **bundled**, so its integrity comes from APK signing rather than a hash —
  which means it is in principle not a precedent for verifying **fetched** canonical content, the thing
  it is named for.

**What this changes.** The entry's thesis ("canonical content is hash-addressed, and we already have
the mechanism") is right as a requirement and **backed by nothing** as a fact. The first element from
the network has to build that verification from zero.

### 2.3. Entry 3 — a duplicate of an already-addressed row, `B8`

**Said:** triggers as "one new domain concept", leaning on A2 `ContextSnapshot` and A6 `Grant`.

**In the tree and in the documents:**

- `ContextSnapshot`, `ContextProvider`, `ContextEngine`, `Grant`, `AutomationPolicy`, `AuditLog` —
  **zero files**. The entry admits this ("gated"), and there is no objection here.
- But the trigger mechanism **already runs** and feeds suggestions: `SuggestionEngine` +
  `SuggestionPrecomputeWorker`.
- Above all: this is **row `B8` of the addressed backlog**, Master Plan §3.6 — "events as a source of
  goals over the existing `SuggestionEngine`/`SuggestionPrecomputeWorker`, propose-only policy", owner
  **A6**, carrying an explicit change-control §5 note: a background loop needs an ADR **before** code.
  The entry does not cite §3.6 and proposes the address A2, which the row does not belong to.

### 2.4. Entry 3 — `StepPrecondition` is not a precondition language

`StepPrecondition` exists ([`ExecutionPlan.kt:8`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt#L8)),
but it is exactly two values: `None` and `PreviousStepObserved(fact)`, where `fact` is the two-value
`ObservedFact` ([`ToolInvocation.kt:50`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt#L50))
and "previous" means `observations[index - 1]` and nothing else. A predicate over context is not
expressible on that; the entry over-claims what it leans on.

### 2.5. Entry 1 — the data does not exist yet, and "Low" was assigned before the measurements

**Said:** "Cost. Low. The data already exists — `registry.all()` plus `DynamicToolNames` (A1″ task 7)."

**In the tree:** `ToolRegistry.all()` exists. `DynamicToolNames` is **zero files**: it is A1″ Task 7,
not shipped (resume point — Task 6). Today a tool's name on the surface comes from a hand-written
`when` over **four** ids in the feature layer — [`AgentSessionPresentation.kt:93`](../feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt#L93),
falling back to `launcher_agent_step_generic` for an unknown id. A discovery surface built today would
show four tools.

Separately, the cost the entry calls "presentation-only". For the **data** that is true. But a
browsable face is a **new production surface**, and the agentic track does not authorize one: DS Master
Plan §8.4 requires, for a navigation-IA change, a real new primary surface, usage rationale, an
architecture prerequisite, an accessibility review, an owner decision and **its own ADR**; §22 adds
design review, the Roborazzi matrix and device acceptance. See also §4 — three measured complications
the entry did not have.

### 2.6. Part 2 B — `MemoryItem` exists in the tree, but not that `MemoryItem`

**Said:** "Whether an environment is a `MemoryItem.Policy` in A3."

**In the tree:** `MemoryItem` appears in seven files, and every one is the **`core/ui` component**
(`SidrMemoryItem`, its gallery, screenshot and semantics tests, `feature/settings` screens). The A3
domain sealed type `MemoryItem = Preference | Fact | Alias | Dismissed | Policy` does not exist. The
name collision is real and will land on A3: a domain type of that name must either be renamed or live
beside an identically named design-system component.

### 2.7. Part 3 D — "the only constructor" is held by nothing

**Said:** "`InvocationValidator.resolve` stays the only constructor."

**In the tree:** `resolve` exists ([`InvocationValidator.kt:118`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/InvocationValidator.kt#L118))
and returns `ResolutionResult`. But `ResolvedInvocation` is a plain `data class`; nothing stops a
caller building one by hand. This is an **intention, not an invariant**. The project already knows how
such things are held: the two tool call sites are held by `ToolExecutorCallSiteGuardTest` and
`ToolWorkerCallSiteGuardTest`, both mutation-proved. The entry's thesis would need a third guard of the
same shape — a cost the entry does not carry.

### 2.8. Manifest §3 — falsified by the project's own code

**Said (§3):** "`behaviour = f(elements)`. Adding an element widens what the system can do **without a
single new line in the planner**… if adding a capability requires editing the planner, the element was
declared wrong."

**What `set_timer` actually cost** (block A1′, two tools): a new planner arm (`ToolMatchPlanner`), a new
routing step **2b** in `RouteCommandUseCase`, a localized `ToolVocabulary` table in `en`/`ru`/`tr`, an
arm in `toolLabelFor`, a row in the guard's permission map and a triple of string resources. By §3's
own rule, today's elements are **declared wrong**.

This is not a refutation of the manifest but its **unverified prediction** — and, unusually, the
experiment is already running: A1″ adds ~205 dynamic tools, and its spec §6.2 states outright that
`ToolMatchPlanner` "keeps its current job… and learns nothing new". **If A1″ closes with no planner
edit, §3 is confirmed across two orders of magnitude; if not, §3 must be rewritten.** That is how it
should be recorded: a falsifiable prediction with its experiment already assigned.

### 2.9. Manifest §15 — a direct conflict with the design track's governing document

**Said (§15):** "The surface is a result, not a design… there is no drawn screen per case: environments
are unbounded in number."

**The conflict:** DS Master Plan §9 declares the visual artifact a North-Star, §14 requires a screenshot
matrix (a Roborazzi golden per primitive/control across eight states), §18 forbids production
simulation, and the Golden policy requires goldens to fix an **approved visual contract**. A composition
assembled by the agent at runtime has no golden in that sense — layout *rules* can be fixed, the result
cannot.

This is **resolvable** (goldens over layout invariants instead of goldens over screens) but it needs a
decision: manifest §15 as written contradicts a standing governing document, and that must be named
before governing status rather than inherited silently.

---

## 3. Claims that held

Without this section the document would read as an indictment. As much was confirmed as was refuted.

| Claim | Confirmation |
|---|---|
| **Part 3 G:** `CompositePlanner(listOf(TemplatePlanner(), toolMatchPlanner))` exists and **is pinned by a guard** | [`AgentProvidesModule.kt:112`](../app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt#L112) + [`DoctrineGuardTest.kt:362`](../app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt#L362). "A third arm, never a replacement" is exactly the code's shape: `CompositePlanner` returns the first plan found |
| **Part 3 F:** `localOnlyMode` *is* the symbolic-only switch | `FeatureFlags.localOnlyMode`, default `false`; branch (3) of `RouteCommandUseCase` returns FastPath parity and the planner is never consulted |
| **Part 3 E / manifest §4:** `ArgType` at one value | [`ActionArg.kt:9`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/action/ActionArg.kt#L9) — `enum class ArgType { STRING }`; `InvocationValidator`'s KDoc calls its own type check "vacuous today" |
| **Entry 6:** `ObservedFact` is two-valued; a persisted `Failed` loses its variant | [`ToolInvocation.kt:50`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt#L50); write [`AgentSessionMappers.kt:166`](../data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt#L166), read [`:334`](../data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt#L334) — it comes back as `CommandFailure.Generic`, and the mapper names the debt in its own KDoc |
| **Entry 2:** `ToolDurability`/`ConsentCheckpoint`/`requiresConsent` exist | `ToolDurability { TRANSIENT, DURABLE }`; `ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, DURABLE_EFFECT }`; [`ConsentPolicy.kt:17`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/action/ConsentPolicy.kt#L17) |
| **Part 2 A:** `SessionDto` carries no version field | `consumer/jvm/.../store/SessionDto.kt` — no version; the debt is addressed to A5 |
| **Manifest §1:** the five OS primitives exist | registry (`ToolRegistry`/`ToolFederation`), risk gate (`requiresConsent` + `checkpointFor`), bounded executor (`RuntimeBudget`, steps + consecutive failures), a session surviving process death (`AgentSession`/`RoomAgentSessionStore`), trace (`TraceEvent`/`ExecutionTrace`). **The table is honest throughout** |
| **Manifest §4:** there is no effects model | Confirmed by absence: no postcondition type exists in `domain/` under any name. The nearest neighbours — `ToolEffect` (`LOCAL\|EXTERNAL`, provenance), `ToolDurability` (a gate flag), `outputSchema` (data for the next step) — none is an assertion about the world |

**Two additions to cost the entries do not carry, neither of which refutes them:**

- **Entry 6** costs **two** mappers, not one: `:consumer:jvm` has its own `store/SessionMapper.kt`
  beside the Room mapper.
- **Entry 2** leans on a mechanism that is **inert today**: `ToolDurability` has exactly one production
  reader — [`AgentExecutor.kt:321`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt#L321)
  — and `checkpointFor`'s branch order puts `requiresConsent(risk)` first, so `DURABLE_EFFECT` is
  **unreachable for any tool at `CONFIRM` or above**, which is exactly where reversibility matters
  most. The user is not thereby at risk (the first branch stops them), but entry 2's "existing seed" is
  half non-functional.

---

## 4. Three measured facts the ideas did not have

The idea files were opened 2026-09-13. A1″'s measurements were taken **a day later**, 2026-09-14, on
the SM-A325F, and recorded in
[2026-09-12-a1-device-measurements.md](superpowers/plans/2026-09-12-a1-device-measurements.md). All
three bear on entry 1.

1. **205 shortcuts across 65 packages.** `shortLabel` is populated for 205/205, `longLabel` for only
   109/205. A "grid of icons" is two hundred-odd items, not a dozen.
2. **Labels arrive already localized — by other apps.** One returned list holds `Anında Hesap Aç` and
   `Подписки` side by side: each declaring app localizes its own shortcuts. A dynamic tool's name is
   **not ours**, does not pass through `sidrString`, and is not covered by our `en`/`ru`/`tr` triple. A
   discovery surface would show the user a mixed-language list we do not control.
3. **Availability is the `android.app.role.HOME` role, not a permission.** `getShortcuts` **throws
   `SecurityException`** when Sidr is not the default home — it does not return an empty list. So "what
   can you do" is empty for a user who has not yet made Sidr their home. That is everyone's first run.

None of the three cancels entry 1. All three change its design and its cost.

---

## 5. Verdicts and addresses

Format: verdict · why · address.

### Part 1

**1. A capability-discovery surface — sound, with its cost stated wrongly.**
The need is real, and it passes the golden rule honestly: the engine (the registry) is real. But this
is a **new production surface**, not a presentation task: DS §8.4 (ADR + owner decision + a11y review),
§22 DoD, the §20 checklist, the Roborazzi matrix, acceptance. Plus the three measured complications of
§4. Plus an **internal contradiction** with Part 1's own subtraction ("no surface per capability") and
with `DOC-SKN-1` (idle animation budget = 0, and Home's calm).
→ **Its own DS block after A1″ closes.** Precondition: Tasks 6–8 (`DynamicToolNames`).

**2. Rollback and compensation — accept; the address is already right.**
The entry's non-obvious argument (compensation buys **quiet**, not only safety) is strong and deserves
to reach the A4′ spec verbatim. **But its second half needs an ADR before design:** "risk can be
re-graded as reversible and stop asking" falls under change-control §5 — "execute a risky action
without human confirmation". That is an owner decision, not an engineering one.
→ **A4′**, rule `DOC-HMA-3` (today `<нет> ← долг A4′`). Caveat: the mechanism is half inert, see §3.

**3. Triggers — not a new entry; a duplicate of `B8`.**
→ **A6** (not A2); merge into Master Plan §3.6's `B8` row. The one thing the entry adds and that is
worth keeping: **design the trigger abstraction *with* A2, not after it** — a correct observation whose
place is A2's spec as an input requirement.

**4. Decline that offers — reject as written.**
Three factual errors (§2.1). Beyond them, the entry **re-opens a decision A1″'s spec §6.3 already took**
— "Ranking that breaks ties would choose an effect for the user in silence; declining costs a miss, and
a miss is free". The entry objects on the merits ("at a hundred tools a miss reads as 'it doesn't
work'"), which is a legitimate objection — but it is an **argument with a decision**, not wiring, and
its price is the multi-turn clarification protocol.
→ **A4′** (the clarification protocol, Master Plan §3.3, spec #2 of three). The objection to §6.3 —
**to the owner**.

**5. Voice — split into two halves of very different cost.**
The cheap half: **`OQ#4` (on-device STT availability across the device matrix) is answered by
measurement**, and the protocol is already in place — A1″ Task 5 established a device probe with the
rule "a row may be filled only from an observation". One measurement closes a question open since
Phase 7.
The expensive half: spoken consent as a second gate modality — after A4′, bounded by `DOC-ADL-2`
(identical risk reads identically) and DS §11's Action Gate contract.
→ **`OQ#4` — can be done now**; the gate — **A4′/A6**.

**6. A richer observation vocabulary — accept as written.**
The only entry in either document that proposes a **trigger rather than a change**, which is
methodologically right under the standing freeze (owner, A1′ F5/F6; the `Failed`/`Completed` "Do not").
Sharpen it twice: (a) state the trigger in the same words as A0.5 §6.3's recorded decision, so the entry
cannot be read as licence to revert; (b) add the second mapper's cost.
→ **A4′** as a conditional trigger; the freeze holds.

**The subtraction (one surface, not four) — consistent.**
Master Plan §7 already gave the preview tabs expiry addresses (`tasks`/`agents` → A4′, `activity` → A5,
`terminal` → name a block or mark it an indefinite demo); DS §8.3 says the same from the design side.
→ **A5.** Name the contradiction with entry 1: one Part 1 entry adds a surface, another demands they be
collapsed.

### Part 2

**A. The portable identity file — a strong idea, at the wrong time.**
Every requirement the entry places on the artifact (a version field from day one, readable JSON,
passphrase encryption rather than the Keystore, grants never auto-applied, honest scope) is correct and
self-consistent. `InvocationValidator.validate(invocation, precedingTools, registry)` genuinely already
returns `UNKNOWN_TOOL`, so "validate an imported plan against the new registry" extends existing work
rather than inventing it.
**What the entry lacks:** an export is a **second egress path**, one `OutboundContextPolicy` does not
describe at all (its allow-list is about what reaches **the model**). Add the A1′ acceptance
observation: after a session row was deleted, its raw `goal_text` was still readable out of the on-disk
file image. The project has not settled its at-rest guarantees, and an export file multiplies them.
→ **A3 + A5**, with an ADR for egress. **Exactly one part applies immediately:** carry the version-field
lesson into A5's `SessionDto` fix, without waiting for an export format.

**B. Environments — architecturally the cheapest of the large ideas, and the strongest product argument.**
"An environment = an `ExecutionPlan` + a working tool set + a `Grant` + optionally a trigger" is
correct, and it needs almost no new vocabulary. The argument "Sidr owns the home screen, and Google
Assistant cannot" is the strongest in either document — and it is **measurably unused**: `UserPreferences`
today carries theme, accent, `favoritesCount` ([`UserPreferences.kt:16`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/preferences/UserPreferences.kt#L16),
a derived top-N, not a pinned set), `micInputEnabled`, `webProviderTemplate`, `autoHideNavBar`. No
pinning, no app hiding, no grid configuration, no wallpaper control (the `SET_WALLPAPER` permission
exists; no tool exposes it).
So `LauncherStateToolSource` is a **large new capability, not a small one** — and the entry is honest in
declaring it new. The connection worth keeping: its effects are reversible by construction, so it is the
ideal first consumer of entry 2's mechanism, which today is inert in exactly that risk band.
→ **After A1″ and A6.** `LauncherStateToolSource` is a candidate adapter #4 for the agentic track;
opening it inside A1″ is scope expansion, i.e. an owner decision (DoD §4).

### Part 3

**C. Elements from the network — the taxonomy is sound, the precedents are hollow.**
The split "canonical / curated / generated" is strong and deserves to become a **doctrine candidate**
(as does manifest §12) rather than remain an idea-register entry: it is a rule about what may be shown
to the user as truth. The egress consequence ("the composition **names** its sources, the list is shown
before activation, fetching happens after consent") is correct and lands on change-control §5.
→ **doctrine** (the rule) + **A6** (the mechanism). Replace both precedents of §2.2 with an honest "we
do not have this".

**D. S-expressions — technically admissible, with no consumer.**
`:domain` is stdlib + coroutines, so an evaluator fits; `RuntimeBudget` genuinely models the step
budget; `:consumer:jvm` genuinely would get it free. But the project has this exact failure on record:
`:data:ai-local` — 1400 lines against a consumer that never arrived in 14 months, deleted in Этап 0.3 —
and Master Plan §3.4 turned it into a **block rule**. Add to the entry: "the only constructor" is held
by nothing (§2.7).
→ **Hold until a consumer exists.** The consumer is environments (B) or synthesis (G); name the build
trigger in that spec, not here.

**E. A symbolic core / HTN — the boundary is right, HTN's cost is understated, and the best proposal hides here.**
Checked and true: `RuleBasedIntentMatcher` is a production system, `TemplatePlanner` a degenerate HTN,
`ResolutionPreferenceStore` a threshold rather than learning. **The boundary "symbolic covers the
authored tier, the model covers the discovered one" maps onto `ToolLevel` without a single edit** — and
§4's measurements confirm it: of a third party's shortcut only the name is known, and the name is not
even in our language.
**But HTN requires declared preconditions and effects, which do not exist** (§3; manifest §4 admits it)
— so it is a change to `ToolDescriptor`, read by three sources, two stores, guards and the surface.
"Smaller, and all real" is understated.
**Separately, and most valuable of all:** "a **sort system** with a resolver per sort instead of the
`ArgType` flag (which is the real argument for widening it, and the one that was missing)" is the one
place in either document that supplies a **new** argument against a standing owner decision (A1′ fork
F6 rejected widening `ArgType` with a measured reason). That rejection rested on a second flag value
being categorically insufficient for MCP's JSON Schema; a sort system is not a second flag value but a
different construction.
→ **A4′** (HTN). **Sorts are an owner-level re-opening of fork F6**, and a separate question from any
block.

**F. The model's authority as a graded grant — the cheapest valuable promotion lies here.**
The entry's rule — **"a grant may widen what the agent reaches; a grant never removes a gate it passes
through"** — is doctrine-grade: checkable (the list of gates on the path is closed and enumerated in the
hard rules), it distinguishes the two kinds of "forbidden", and it closes a hole that change-control §5
currently covers with a list rather than a principle.
The 0–4 ladder is separate and later: it changes not `localOnlyMode`'s default but its **semantics**, and
`DOC-ADL-3` (twice amended) plus the signed Class B string `settings_local_only_description` are
anchored on those. The entry's observation that "rung 1 carries most of the value and is the safest" is
right and worth testing, but that is A6 design.
→ **Propose the rule to the doctrine now**, through ordinary change-control (§5: "add a rule to the
doctrine matrix"). **The ladder — A6.**

**G. Program synthesis — correct placement, unnamed dependency.**
"As a third arm in `CompositePlanner`, never as a replacement" is exactly the code's shape, and the list
is guard-pinned (§3). "Inside A4′ rather than a block of its own" is right for the reason the entry
itself gives: a synthesizer ships no visible capability by itself.
**What the entry lacks:** there is nothing to search over by type while `ArgType` has one value and no
effects exist — so G sits strictly below E. And the model's input today is the seven-action
`ActionCatalog` via `CatalogSchemaRenderer.render(catalog)` ([`:32`](../domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/CatalogSchemaRenderer.kt#L32)),
**not** the registry: "two hundred descriptors do not fit in a prompt" is the problem of whichever block
first hands tools to a model, and A1″ Task 12 is already measuring a number for it instead of a slogan.
→ **A4′, after E.**

### The manifest

| Section | Verdict |
|---|---|
| §1 OS primitives | **Checked, honest.** All five exist |
| §2 the unit is intent | Positioning, not code-checkable; not contradicted |
| §3 the system is a function of its elements | **Falsified today** (§2.8). Rewrite as a falsifiable prediction with its experiment assigned — A1″ |
| §4 elements are declared | **The self-assessment is correct**, including the gap it names. The document's best property |
| §5–§7 spec → synthesis → data | Aspirational; depends on types that do not exist (E, G) |
| §8–§9 a total evaluator producing proposals | Consistent with the hard rules; the cost is §2.7 |
| §10 only the model may be wrong | Consistent with ADR 1/4 |
| §11 understood once, then offline and identical | This is Master Plan §3.3's learned-plan cache (A4′). The argument "the cache is a mechanism of predictability, not an optimization" strengthens what is already decided; it adds nothing new |
| §12 the exact is not generated | **Doctrine candidate**, as with C |
| §13 a grant never removes a gate | **Doctrine candidate, first in line** — see F |
| §14 the environment is the unit of delegation | Consistent with B and with A6 |
| §15 the surface is a result | **Conflicts with DS §9/§14/§18** (§2.9). Needs a decision before governing status |
| §16 symbolic by tier, model by language | **Checked; maps onto `ToolLevel`** |
| §17 verifiability by mutation | Consistent with the block's practice (every guard mutation-proved) |
| §18 identity is data, not weights | Consistent with A3 |
| **The name `SidrOS`** | A product decision that **appears in no ADR**. Needs an owner decision before governing status |

---

## 6. What to promote first

Three candidates, chosen by value over cost rather than by order in the documents.

1. **The rule "a grant widens reach and never removes a gate" (Part 3 F / manifest §13) → the
   doctrine.** Cost: one ADR and one matrix row with a verification type; the rule is checkable because
   the list of gates on the path is closed. Value: it turns change-control §5's enumeration into a
   principle the next items follow from rather than being appended to.
2. **`OQ#4` (Part 1, entry 5) → one measurement.** Open since Phase 7, it blocks the voice half of the
   product's value, and A1″ Task 5 already established the device-probe protocol. It will not get
   cheaper.
3. **The sort system as a counter-argument to fork F6 (Part 3 E) → an owner-level re-opening.** Not
   work but a question: F6's rejection rested on a second flag value being insufficient, and sorts are a
   different construction. Worth asking while `ArgType` has not yet grown consumers.

---

## 7. Contradictions inside the documents themselves

Named here so the owner resolves them rather than the next agent doing it silently.

- **Entry 1 against Part 1's own subtraction.** One adds a surface; the other demands four be collapsed
  into one. Both in the same part; neither references the other.
- **Manifest §3 against the code** (§2.8) — and A1″ will settle it.
- **Manifest §15 against DS Master Plan §9/§14/§18** (§2.9).
- **Name collisions that will land on future blocks:** `ToolEffect` (`LOCAL|EXTERNAL`, provenance)
  against the effects model manifest §4 and Part 3 E both require; `MemoryItem` (`core/ui`) against A3's
  domain type.
- **Entry 4 against A1″ spec §6.3** — a legitimate objection, but it argues with a taken decision while
  being written as wiring.

---

## 8. The question of folding entry 1 into block A1″

Asked by the owner, 2026-09-14. **Recommendation: do not fold it in.** Five reasons, strongest first:

1. **DoD §4, first line: "scope is not expanded".** A1′ was already cut by fork F2 for exactly this
   reason — federation boundaries separately from mass and selection. Folding a surface into A1″ undoes
   the owner's own decision.
2. **The producer has not shipped.** Entry 1's data is `DynamicToolNames`, Task 7; the resume point is
   Task 6. A consumer inside the same block as its producer requires reordering the plan.
3. **It is a new production surface, and it changes the block's kind.** A1″'s device work is declared
   **measurement, not acceptance** (plan, § Global Constraints). Folding turns a block closeable at
   `CODE-GREEN` into one obliged to pass design spec → design review → the §20 Islamic checklist → the
   Roborazzi matrix → owner acceptance, plus an ADR for the navigation-IA change (DS §8.4).
4. **A1″ already carries an unresolved owner fork** (`SENDTO`/`Events` retention, Master Plan §3.6 `B4`)
   and the unjudged `"sayaç ayarla"`. A third open question makes closing more expensive.
5. **§4's measurements make the surface dearer than the entry assumes:** mixed languages, an empty list
   until Sidr is the default home, 205 items.

**What is cheap and does stay inside A1″'s scope.** Entry 1's premise ("the data already exists") is
confirmed **for free** once Tasks 6–8 land: `registry.all()` grouped by `ToolLevel` and source — zero
new work, and exactly the fact a future DS spec will stand on. It is recorded here as that spec's
precondition, not as an A1″ task.

---

## 9. Re-check recipe

This document has **no** guard test — unlike the doctrine matrix, which `DoctrineMatrixGuardTest` turns
red on a test name absent from the repository. So the recipe stands in for a guard: the next reader
reproduces §2 and §3 without trusting the text.

```bash
# §2.1 — the agent port: two variants, no Clarify
grep -n 'sealed interface PlanningResult' -A4 \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt

# §2.1 — not one exhaustive consumer of PlanningResult
grep -rn 'PlanningResult' --include=*.kt --exclude-dir=test --exclude-dir=jvmTest .

# §2.1 — PlanResult.Clarify has both a producer and a consumer
grep -rn 'PlanResult.Clarify' --include=*.kt domain/src/commonMain

# §2.2 — ModelStore: KDoc references to a deleted path only
grep -rn 'ModelStore' --include=*.kt .
# §2.2 — the GeoNames index has no integrity check (expect empty)
grep -rn 'sha256\|MessageDigest' --include=*.kt data/prayer/

# §2.3 — the A2/A3/A6 layers are absent (expect 0 files each)
for s in ContextSnapshot ContextProvider ContextEngine UserMemoryStore MemoryPolicy AutomationPolicy; do
  echo "$s: $(grep -rl "$s" --include=*.kt . | wc -l)"; done

# §2.5 — DynamicToolNames does not exist yet (expect empty)
grep -rl 'DynamicToolNames' --include=*.kt .

# §2.6 — MemoryItem in the tree is the core/ui component
grep -rl 'MemoryItem' --include=*.kt .

# §3 — the planner composition (the composition root)
grep -rn 'CompositePlanner(listOf' app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt
# §3 — and the guard that holds it. A separate command: in the test this is the regex
# `CompositePlanner\(\s*listOf\(`, which the literal above does NOT match
grep -rn 'CompositePlanner' app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt

# §3 — DURABLE in production: expect EXACTLY TWO lines, of two different kinds —
#   AgentExecutor.kt:321      — the only READER, under checkpointFor's first branch
#   SandboxToolSource.kt:65   — a WRITER: declares delete_file DURABLE (and DANGEROUS, which is
#                               precisely why DURABLE_EFFECT is unreachable for it)
grep -rn 'ToolDurability.DURABLE' --include=*.kt --exclude-dir=test --exclude-dir=jvmTest .
```

**The recipe was run on 2026-09-14 at `0b2fe06`, and it found two defects in itself** — both commands
above are corrected from that run. This is recorded rather than tidied away: a recipe nobody ran is
worth exactly what a claim nobody checked against the tree is worth — which is the very defect this
whole document exists to catch.

**What the recipe does not check:** anything in §5–§8. Those are judgements, refuted by argument rather
than by a command. Where a judgement rests on a fact, the fact is in §2–§4 with its reference.
