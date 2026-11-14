# SIDR — Improvements (idea register)

> **Status: IDEAS UNDER DISCUSSION — not a plan, not a spec, not a commitment.** Nothing here is
> scheduled, and nothing here may be cited as a decision. The same framing
> [agentic-os-architecture.md](agentic-os-architecture.md) opens with: a direction becomes real only by
> passing through `brainstorm → spec → plan → build`, with its own forks put to the owner before code.
>
> Opened 2026-09-13, during block A1″. Each entry says what it is, why it earns its place, what it costs,
> and which existing concept it plugs into — so that promoting one to a spec starts from something
> checkable rather than from a slogan.
>
> **Rule for this file:** an entry names existing types and documents where it leans on them, and those
> names are checkable claims like any other. An entry that invents a type must say so.

---

## Part 1 — Systemic additions toward "maximally capable, still simple"

Ordered by return over cost. All six are **mechanisms**, not features: each unlocks a class of tasks or
removes a class of friction, rather than adding one capability.

### 1. A capability-discovery surface

**What.** A browsable, searchable face for `ToolRegistry`: the system's own answer to "what can you do",
generated from the registry and grouped by source app and `ToolLevel`.

**Why it is first.** A1″ is about to harvest ~100 shortcuts from other apps. A blank text field with a
hundred *hidden* capabilities is less usable than a grid of icons, because icons are at least visible.
This is the entry that turns a command line into an interface, and A1″ creates the problem it solves.

**Cost.** Low. The data already exists — `registry.all()` plus `DynamicToolNames` for third-party labels
(A1″ task 7). No new domain concept. Presentation-only, so the golden rule ("a surface's UI is built
only once its engine is real") is satisfied: the engine is the registry, and it is real.

**Leans on.** `ToolRegistry`, `ToolDescriptor.level`, `DynamicToolNames`, `AgentSessionPresentation`'s
provenance labelling.

### 2. Rollback / compensation as a first-class mechanism

**What.** Every plan step may carry an inverse. A plan that can be undone is recorded as such, and the
user gets one consistent "undo that" affordance.

**Why, and this is the non-obvious part.** Compensation buys **quiet**, not only safety. People refuse
to delegate because they fear irreversibility, so today every risky step asks. With a real inverse, risk
can be re-graded as *reversible* and stop asking. Undo is therefore the mechanism that makes the system
simple rather than nagging — the opposite of how it is usually justified.

**Cost.** Medium, and it is already named in the track plan's Этап 6 (A4′) as "rollback/compensation".
This entry argues for promoting it from one runtime capability among several to a load-bearing element
of the product's simplicity.

**Leans on.** `ToolDurability` (`TRANSIENT`/`DURABLE`) as the existing seed, `ConsentCheckpoint`,
`requiresConsent(risk)` as the single risk→gate predicate.

### 3. Triggers — a goal whose precondition is context, not an utterance

**What.** One new domain concept: a standing goal fired by a predicate over `ContextSnapshot` rather
than by typed or spoken input. "When I get home", "every morning", "if the battery is low".

**Why.** "Maximum tasks" includes tasks that are not reactive at all. This turns the agent from
something you operate into something that stands ready — which is the actual definition of automation in
the roadmap's Agentic-1. One concept, a large multiplier on task coverage.

**Cost.** Medium, and it is gated: it needs A2's `ContextSnapshot` to be real and A6's `Grant` to bound
what a trigger may do unattended. Worth designing the trigger abstraction *with* A2 rather than after.

**Leans on.** A2 `ContextSnapshot` / `ContextProvider`, A6 `Grant` + `AutomationPolicy`, `StepPrecondition`.

### 4. Decline that offers, instead of dead-ending

**What.** When selection declines — two equal candidates, a dynamic name without its app token — the
system asks rather than returning nothing: "did you mean A or B", two taps.

**Why.** A1″'s selector correctly declines on ambiguity, and correctly prefers a miss to a wrong effect.
But scaling to ~100 tools multiplies misses, and a miss that dead-ends reads as "it doesn't work". The
contract to carry this already exists and is unused.

**Cost.** Low. `Planner` already returns `ExecutionPlan | Clarify | NoPlan`; the `Clarify` arm is
designed and has no producer. This entry is mostly wiring plus one surface shape.

**Leans on.** `Planner`'s `Clarify`, `ToolSelector` (A1″ task 10).

### 5. Voice as the primary input, designed in rather than retrofitted

**What.** The whole loop hands-free, **including consent** — a spoken confirmation is a different gate
design from a tapped button.

**Why.** Typing a sentence is slower than tapping an icon; saying it is faster. The product's value
proposition rests on an input method whose feasibility is still an open question (`OQ#4`: on-device STT
across the device matrix, untested). And delegation in particular is a voice act.

**Cost.** Unknown until `OQ#4` is answered, which is itself cheap to answer. Retrofitting voice consent
later is expensive, because the gate is currently one predicate and one card; adding a second modality
after it has grown is harder than now.

**Leans on.** `AndroidSpeechInputSource`, `ConsentCheckpoint`, `requiresConsent(risk)`.

### 6. A richer observation/failure vocabulary — on a stated trigger, not now

**What.** Widen what the engine can *say* about reality: `ObservedFact` beyond two values,
`CommandFailure` surviving persistence (today a persisted `Failed` restores as `Generic`).

**Why, and why not yet.** At maximum task coverage most interesting tasks will *partially* fail, and the
product must be able to say "2 of 3 steps done, the third failed because X". Today X is inexpressible.
This is deliberately frozen (owner, A1′ F5/F6; A0.5's `Failed`/`Completed` divergence is a recorded
decision, not a defect). So this entry proposes only a **trigger condition**: the day a multi-step plan
can partially fail in front of a user is the day the freeze is revisited. Not before, not later.

**Leans on.** `ObservedFact`, `CommandFailure`, `ToolResult`, A0.5 §6.3.

### And one subtraction — no surface per capability

Simplicity needs removal, not only good additions. Four `PREVIEW` tabs (Tasks / Agents / Activity /
Terminal) are already waiting for their engines. When the engines arrive the pull toward four screens
will be strong, and taking it would be a defeat: the point of the system is one input, and every tab
dilutes it. Collapse them into **one** view answering two questions — *what happened* and *what is
standing* — consistent with the design rule "the more ordinary the action, the less UI it generates" and
with the target IA of four surfaces rather than five.

---

## Part 2 — Owner ideas, raised 2026-09-13, discussed but not designed

### A. The portable identity file ("mental snapshot")

**The idea as raised.** The user's character/state captured in one file, movable to another device, which
picks it up and configures itself for that user automatically.

**The sharpening that makes it feasible.** Not "the whole state of the phone" — Sidr cannot move
WhatsApp's internal data and should not claim to. What it *can* move is the layer it owns entirely:
learned resolutions, aliases, environments, grants, cached plans, context rules, user-stated facts.
Call it the **portable identity layer**, not a device backup. Every row of it is data Sidr created or the
user dictated, which is exactly why this is achievable where a general phone backup is not.

**The strongest framing, and it is a genuine differentiator: identity portability without an account.**
Everyone else does this with a cloud account and a sync server. Sidr can do it with a file the user
holds, because the whole design already keeps this data local, structured, and inspectable. It also makes
the "two consumers" promise concrete: the same file loaded by `:consumer:jvm` means your phone and your
PC know who you are with no account between them.

**A new file format is not needed, and inventing one would be a trap.** What the artifact needs:

- **A version field from day one.** A0.5 already paid for its absence: `SessionDto` carries no version,
  so version skew is indistinguishable from corruption — a recorded debt addressed to A5. An export
  format repeats that mistake at ten times the cost.
- **Plain, documented, inspectable JSON** — not a binary format. "The user can see what is in it" is the
  same ethic as provenance and the editable memory surface; an opaque profile file would contradict the
  product it belongs to.
- **Encryption under a user passphrase**, not the Keystore. The Keystore key deliberately cannot leave
  the device, which is the whole point of it — so a portable file needs its own key derivation.
- **A strict import policy, and this is the critical security rule: grants must never be auto-applied.**
  An importable file that carries `Grant` rows is a privilege-escalation vector — someone hands you a
  "profile" and the agent silently acquires accessibility-tier tool access. Grants are either excluded
  from the format or re-confirmed individually on import, one by one, with their limits shown.
- **Honest scope on the receiving device:** it restores what Sidr owns and *offers* to reinstall the apps
  the profile references (a tool that already exists — Play Store deep links). It cannot restore other
  apps' internal state, and the surface must say so rather than implying a full device clone.

**Open questions.** Whether cached plans are portable at all (a plan references `ToolId`s that may not
exist on the new device — so import must validate every referenced tool against the new registry and
drop what does not resolve, which is `InvocationValidator`'s job extended to import). Whether the file
is one artifact or a manifest plus parts. Retention: does exporting create a copy the user then forgets
about, and does that deserve a warning.

### B. Environments — "set me up a chemistry lab", "set up a cinema and a rest room"

**The idea as raised.** The user says what they are about to do; Sidr composes a whole working
environment for it — the chemistry example (university, lab tools), the rest example (calls and
messengers silenced, films and books surfaced, a rest space).

**What it is architecturally, and this is why it fits so well.** An environment is a **named bundle**:
an `ExecutionPlan` (set DND, set volume, launch and arrange apps), a working set of tools promoted to the
surface, a `Grant` bounding what may happen unattended while it is active, and optionally a trigger
(entry 3) that offers it on arrival at a place or time. **Four things that already exist or are already
designed.** It needs almost no new domain vocabulary — which is rare for an idea this large.

**And it uses a structural advantage nothing else has: Sidr owns the home screen.** Google Assistant can
toggle DND; it cannot recompose your home screen into a chemistry workspace. That capability is available
to a launcher and to nothing else, and the project is currently not using it at all.

**The one genuinely new piece: the launcher itself must become a tool source.** Today the agent can
launch apps; it cannot arrange the surface. A `LauncherStateToolSource` — pin these shortcuts, hide those
apps, set this grid, set this wallpaper — would be the first source whose effects are **reversible by
construction** (the previous layout can always be restored), which makes it the ideal first consumer of
entry 2's compensation mechanism. The two entries reinforce each other: every environment needs an exit,
and an exit *is* a compensation.

**Why it gives the product a shape users grasp instantly.** Everyone already understands modes and focus
profiles. This is focus profiles the user **defines in natural language** and that compose *capabilities*
rather than just settings — which is a category nobody ships, and it is squarely what an agentic launcher
is for.

**Honest risks.**
- It is easy for this to degrade into a wallpaper-and-DND toy. The value is proportional to tool mass: a
  chemistry environment is only worth anything if there are chemistry-relevant capabilities to assemble.
  So it is correctly *downstream* of A1″, not a substitute for it.
- "Finds good films and books" is a content-recommendation problem, not an agent problem, and it is the
  one part of the example that needs the cloud and has no offline answer. Worth separating in the design
  so the environment mechanism does not inherit a dependency the rest of it does not have.
- An environment that silences calls is a **safety-relevant** state. It needs an exit that cannot be
  forgotten — a time bound, or a standing indicator, or both. `Grant`'s quiet-hours limit is the right
  place for that, not a bespoke timer.

**Open questions.** Who authors an environment — the user by dictation, the model by proposal from one
sentence, or a template the user then edits? (The middle one is the most attractive and the hardest to
make deterministic.) Whether an environment is a `MemoryItem.Policy` in A3 or its own persisted type.
Whether entering one is a single consent or a consent per constituent step the first time.

---

## Part 3 — The declarative approach, discussion of 2026-09-13

The conceptual side is factored out into [manifest-sidros-en.md](manifest-sidros-en.md). What follows is
only the engineering consequences, briefly.

### C. Elements from the network — configurations, not code

Downloading element *implementations* and running them is off the table: dynamic DEX loading is a Play
policy violation and a hole, and an element from the network running in the process inherits the
process's permissions. The version that works: the network supplies **configurations** of a closed set
of primitives. A table plus data, not a table plugin. The renderer is always local; the worst a hostile
source can do is show wrong content.

**Three classes of content, not one.** **Canonical** (the element table, constants, units) — exact,
hash-addressed, never from a model; the precedent is in this repository already, in the bundled GeoNames
index and `ModelStore`'s SHA-256 verification. **Curated** (links) — with the source shown.
**Generated** — only where "roughly right" is acceptable, and marked.

**Egress becomes a new class.** Fetching a URL a *model* chose punches through
`OutboundContextPolicy`. The answer is in the existing doctrine: the composition **names** its sources,
the list is shown before activation, fetching happens after consent. An environment's first activation
*is* the consent event.

**Risks.** An imported environment with URLs inside is the same vector as importing grants (same rule:
grants are never imported). And the moment environments are shareable there is a moderation problem a
single person cannot carry — so personal artifacts before shareable ones.

### D. S-expressions as the substrate

JSON cannot express behaviour: a converter needs a formula, a composition needs a conditional.
S-expressions give code-as-data, a **total evaluator of our own** over a closed set of forms (no `eval`
of a string, no host access), readability and hand-editability, and easy generation by a model — the
grammar is uniform.

**The key property: the evaluator produces proposals, not effects.** `(tool set_timer 15m)` evaluates to
a `ToolInvocation` that then travels the existing gates; `InvocationValidator.resolve` stays the only
constructor. The result type is `ExecutionPlan | Surface`, never an effect.

**One evaluator covers four needs:** surface composition, formulas inside elements, context predicates
for triggers, and plan generation. It is a substrate, not a feature. Reader plus evaluator is some
300–600 lines in `:domain`, no dependencies, and `:consumer:jvm` gets it free. Termination is a step
budget — the pattern already exists in `RuntimeBudget`.

**What it must not become:** a half-working Scheme. No user-defined recursion, no mutation, no I/O; a
fixed set of special forms, everything else a host function. Prior art: Nix, Guix, EDN; and in the
untrusted-logic niche the industry converged on Starlark and CEL.

### E. A symbolic core, and where the model remains

**Sidr is already a symbolic system.** `RuleBasedIntentMatcher` is a production system;
`TemplatePlanner` is a degenerate HTN; `ToolSelector` is rules with explicit priority; the learned-plan
cache is case-based reasoning; `ResolutionPreferenceStore` is a threshold, not learning. The LLM is the
only subsymbolic component, and it sits at the input boundary.

**The headline addition is HTN planning.** Tools declare **preconditions and effects**, the registry
becomes a planning domain, and `TemplatePlanner` generalizes from "one template per goal shape" to
search over declared methods. Multi-step decomposition then moves from the model to the symbolic side —
a strict improvement by the project's own doctrine: the model parses an utterance into a goal term, a
symbolic planner plans.

**Smaller, and all real:** context predicates as Datalog (~200 lines, the same substrate); a **sort
system** with a resolver per sort instead of the `ArgType` flag (which is the real argument for widening
it, and the one that was missing); memory as a knowledge graph with inference, readable and correctable
by the user.

**The limit is specific to this project:** a planning domain is hand-authored, and a hundred shortcuts
are *discovered*, not authored — of a third party's shortcut only the name is known. Hence the boundary:
**symbolic covers the authored tier, the model covers the discovered one.** It maps onto `ToolLevel`
unchanged.

**And the argument nobody else has:** the symbolic half can be mutated. Remove a precondition and the
plan must stop being valid; remove a method and the goal must stop being planned. A prompt cannot be
mutated. Every capability moved from the model to the symbolic side becomes verifiable.

### F. The model's authority as a graded grant

**Not "two versions of the agent".** Symbolic and generative are not alternatives but a pipeline; a
"generative version" either bypasses the gates (forbidden) or does not (then there is no second
version). And it asks the user a question they cannot answer.

**Instead: the model as a capability under a grant.** Half of it already ships — `localOnlyMode` *is*
the symbolic-only switch. The refinement replaces a boolean with a ladder: **0** off · **1** parse only
(a goal term over already-registered tools; it cannot invent a step) · **2** may propose plans · **3**
may re-plan mid-loop · **4** may act inside a named environment over pre-declared tools with a time
bound.

An observation against intuition: **rung 1 carries most of the value and is the safest** — it solves the
natural-language problem, the one thing the model is irreplaceable for, and it plans nothing.

**Two classes of "forbidden", and this is the central distinction.** Class 1 — forbidden until the user
allows it: accessibility, notification reading, uninstalling apps. Legitimately grantable; A6 exists for
them. Class 2 — forbidden because the system's integrity rests on it: executing around `ToolRegistry`,
skipping validation, skipping the allow-list, "stop asking" on `DANGEROUS`, a loop with no budget.

> **Rule:** a grant may widen what the agent **reaches**. A grant never removes a gate it **passes
> through**.

Checkable, and probably guardable: the list of gates on the path is closed, and no grant may subtract
from it. **The ladder's cost is a parity matrix** — three tests already pin the "understanding
unavailable" states. So three rungs rather than five, each pinned by a test.

### G. Program synthesis — "the system as a function of its elements"

The frame that unifies C–F: do not author compositions, **find** them. Tools and resolvers gain types
(`set_timer : Duration → Effect`, `text → Package`), and "set a timer for 15 minutes" becomes a search
for a composition of type `Text → Effect`. The composition is not written — it is found. "Open the clock
and set an alarm" is two required effects, so a two-step composition, with no new `GoalShape` and no
model.

**Synthesis is verifiable by construction:** the search runs against a spec, so the search *is* the
check. With a model it is the other way round — a plausible program that must be tested afterwards.

**The model moves up a level:** synthesis's real difficulty is not the search but obtaining a formal
spec from a human. "Set me up a chemistry lab" is a wish, not a spec. So **the model writes the spec,
the synthesizer writes the program, the evaluator produces proposals, the gates decide.** The model's
output becomes the smallest and most reviewable artifact in the chain; today it is the largest.

**Risks, honestly.** Types **underdetermine intent**: `launch_app` and `open_app_info` share a
signature and the search cannot tell them apart — which is why the authored vocabulary does not become
obsolete. It answers *designation*; synthesis answers *composition*. Search explosion is handled by a
depth bound and the cache. Drift over time is handled by the same cache — and that is **the second and
more important argument for it: the cache makes behaviour stable, not merely fast.**

**How to introduce it without hurting functionality:** as a third arm in
`CompositePlanner(listOf(TemplatePlanner(), toolMatchPlanner))`, never as a replacement. That list is
pinned by a guard and the parity tests hold offline behaviour, so a regression would be caught by
instruments that already exist. Start at **depth 2** (~150 lines), which covers "resolve an argument,
then invoke a tool" and "invoke two tools".

**The real risk is not technical:** a synthesizer ships no new visible capability by itself. So it
belongs not in a block of its own but inside the one that ships multi-step goals — A4′.

---

## Part 4 — Owner ideas, raised 2026-10-03, discussed but not designed

> Raised during the brainstorm for phase 3 of block A4′. The same day's architectural context — the
> "blind planner", environments as desired state, "build the system you like", the full-access window —
> is in the note [2026-10-03-agent-architecture-discussion-design.md](superpowers/specs/2026-10-03-agent-architecture-discussion-design.md)
> (status: under discussion, Russian). Here: five ideas with the agent's analysis; none is scheduled.

### H. Interface constructor — everyone assembles their own shell

**The idea as raised.** Every user assembles the system for themselves — not the core but the interface
and design: where the buttons, the search bar and the windows sit. Possibly right at the start, on first
launch.

**What it is architecturally.** The core is fixed, the shell is yours — as on Linux, one kernel and
many desktop environments. A layout is a **declaration** (new type: "layout declaration"), not pixels:
the screen is a few zones (top, main area, bottom/dock, side panels, overlay layer); each holds an
ordered list of palette elements (command bar, app grid, widgets, agent card, note, scene, prayer strip)
with a size S/M/L, an alignment and a density. Design-system rules compute the geometry — like a tiling
window manager (i3, sway): the human says "what goes where", the manager computes the pixels.

**Why zones and not a free canvas.** Pixels do not carry over to another screen, break accessibility
and are unreadable to the agent; zones carry over (geometry is recomputed), hold accessibility at any
font scale and can be edited by the agent as data ("move search to the bottom, make the clock bigger").
This does not contradict B.1 ("the agent chooses, the system places"): that argument is against the
**model** controlling geometry; here the **human** controls the meaning of the layout, and the system
still computes the geometry.

**What it looks like.** Edit mode on long-press: drag between zones, size S/M/L, hide, "put it back"
(generations — entry B, note §7). By text or voice, the agent edits the declaration: diff preview,
consent. **Each environment has its own layout.** First launch is three big choices with a live
preview, **without AI** (no provider key exists yet on first launch): where the command bar goes (top or
bottom — the most consequential choice: the bottom suits the thumb on large phones), density (large /
normal / dense), what the home screen holds (apps / widgets / minimum); caption: "everything can be
changed at any time". Later: "describe it in words" and "bring my system over" (entry A). First launch
is **the first consumer of the environment mechanism**, not a separate onboarding code path.

**Guards — no layout can break them:** (1) the entry to the agent (the command bar) is always reachable,
at least by a gesture; (2) the consent card and consequences are visible above everything
(`DOC-NYH-2`, `DOC-MZN-2`); (3) accessibility — touch targets ≥ 48 dp, contrast, font scale; (4) the exit
from restricted environments (entry L) is reachable by whoever is entitled to it; (5) the prayer strip,
when shown, obeys `DOC-SKN-3`; whether a user may remove it entirely is a doctrine question for the
owner.

**"Windows".** On the phone, other apps' windows are laid out by the system (Sidr can only ask for split
screen, and not everywhere); Sidr's windows are its own panels, pages and cards. On the PC (the second
consumer) the same zone language describes real windows.

**Cost.** A design-system block of its own: ADR, owner decision, accessibility review, a Roborazzi
matrix (layouts × screens × font scale) — the DS §8.4 procedure. The layout engine follows the same
discipline as `ToolRegistry`: closed vocabulary, validation, a single renderer.

**Precedents** (from memory): Nova / Lawnchair / Niagara — grid and dock, by hand; KWGT/KLWP — a free
per-pixel builder, powerful and brittle; Home Up in Good Lock — on Samsung's privileges. **Not seen:**
one declarative layout language edited by both the human and the agent, per environment, portable to
another phone and to the PC, with generations and guards.

### I. Translator — multi-stage, cloud, on our own server at the user's choice

**The idea as raised.** Our own built-in, highly accurate translator: multi-stage, cloud-based, through
a backend; both as a built-in app and as a separate product. **Our own server — yes, but only at the
user's choice** (owner, 2026-10-03).

**What it is architecturally.** A server pipeline is the right place for quality: draft translation →
refinement by a model with the task's context → back-translation to detect meaning drift → the user's
glossary ("always translate X as Y" — identity data, entry A) and **canonical** translations for sacred
and legal texts (manifesto §12, `DOC-ILM-5`: what must be exact is never generated; the source is shown)
→ confidence marks and alternatives for ambiguous words (`DOC-NYH-1`). Inside Sidr the translator is **a
capability in the registry plus a surface element**, not only an app: a "Paris" environment uses it from
day one.

**How "at the user's choice" works.** By default Sidr **does not contact its own server at all**:
translation goes through the user's BYOK model and/or on device. The Sidr server is switched on by an
explicit choice that states what is sent and what is kept. So "Sidr sends nothing about you" stays true
by default, and once switched on, the outbound receipt (note §6, mechanism 6) shows what went to the
server.

**Cost — Sidr's first server of its own, and it changes three things:** (1) **a new outbound
recipient** — today text goes only to the BYOK provider the user chose; the Sidr server is a recipient of
a different kind: an ADR on `OutboundContextPolicy` (new: per-recipient policy) and a guard per
recipient; (2) Sidr becomes **a data processor** — a retention policy ("we keep nothing"), security,
legal obligations; (3) **a separate product is a separate business**: accounts, billing, an API, model
costs. Offline and for private text, an on-device fallback translation. Translating **someone else's**
incoming text through the cloud sends third-party content out: on device, or explicitly marked.

**Place in the queue.** The capability can come early, right after the bridge (via BYOK); the server and
the standalone product — a track of their own, by ADR.

### J. Agentic browser — filters unwanted content, adapts to the user

**The idea as raised.** Our own agentic browser that blocks unwanted content and adapts to the user's
character.

**An advantage the others do not have.** The agentic browsers of 2025 (from memory — Perplexity Comet,
ChatGPT Atlas) became the prime target of **indirect prompt injection**: a page dictated actions to the
agent. Privilege separation (note §8) closes this by construction: the agent that reads pages **has no
phone tools**, and everything it reads is labelled "third-party". The formula: **an agentic browser a
page cannot hijack.**

**What it looks like.** The engine is the system's (WebView or GeckoView); we do not write one.
Filtering in three layers: ads and trackers (known lists, deterministic); content categories (strict in
a children's environment, entry L); **the user's values** — for Sidr's audience a filter by Islamic
norms is natural (browsers for Muslims exist — from memory, SalamWeb — but without an agent and without
privilege separation). **Transparency is mandatory:** what was filtered and why is visible and can be
overridden; hidden filtering, however well meant, turns "fitted to your character" into a bubble chosen
for the user.

**Cost.** Fetching pages from the phone is a new network channel that `OutboundContextPolicy` (a policy
for requests to the model) does not cover — it needs a policy of its own (note §14); it needs the
research agent and multi-agent support. The researcher's output is shown as **curated** — with its
source (`DOC-ILM-5`); this block is the addressee of that rule's debt. **Place in the queue:** after
environments and multi-agent support.

### K. The system as an imprint of the person

**The idea as raised.** The whole system adapts to the user's character and carries a kind of imprint
of their personality.

**What it rests on.** Manifesto §18, "identity is data, not weights", and entry A (the portable
snapshot): preferences, aliases, environments, layouts, glossary, cached programs — rows with provenance
and retention. Entries H–J and L are its expressions.

**The condition without which the idea is dangerous: a mirror, not a shadow.** The user can always see
"what Sidr thinks about me", correct it and delete it (`DOC-AML-5`). Hidden adaptation to character is
the road to manipulation and engagement-farming, which `DOC-HYA-1` forbids. Inferences about character
never leave the device (`DOC-AML-4`; note §6 — the model never sees the user's world).

### L. Care — the elderly and children

**The idea as raised.** Apply it for convenience, including monitoring the health of the elderly,
children, and so on.

**The central question is manifesto §19: on whose behalf.** There are **two principals with different
interests** here: the one who cares and the one cared for.

| | done right | never |
|---|---|---|
| elderly | a "Care" environment: large, voice-first ("call my daughter"), medication, a help button; the relative receives **signals, not data** ("no activity for 12 hours") — with the elderly person's own consent and right to revoke | covert monitoring — that is stalkerware: banned by Google Play, and degrading |
| children | a "Children" environment: allowed apps, a strict browser (J); **exit only by a parent's biometrics** | — |
| health | steps and heart rate from Health Connect (from a watch); wording of "wellbeing", not "diagnosis" | diagnostic claims: Sidr would become a regulated medical device (EU MDR, FDA) |

**A finding: one mechanism in two directions.** The full-access window (note §7.2) and the
"Children" / "Care" environments are all environments with **authenticated entry and exit**: the first
elevates (only the owner may enter), the others restrict (only a parent or carer may leave). One
construct, not three features.

**Cost.** Signals to a relative are a new outbound channel (to someone else's device — naturally through
the relative's Sidr, manifesto §20), with its own policy and ADR; health data is the most sensitive
storage category; regulation and the consent of two principals are questions of doctrine and law, to be
answered **before** design.

### How H–L relate, and in what order

All five are **environments and capabilities on one foundation**. Built as separate features before the
environment mechanism, they would become special-case code that is not declarative and will be
rewritten. The order the agent proposes: (1) the bridge — A4′ phase 3 (without it the model cannot write
declarations); (2) the translator as a capability via BYOK; (3) the environments block — declaration,
reconciliation, generations, palette; first consumers: first launch (H), then "Children", "Care" (L) and
the full-access window; (4) identity — A3 and A5 (K); (5) multi-agent support and the browser (J); the
translator server and the standalone product — a track of their own, by ADR (I).

---

## Part 5 — Owner ideas 2026-11-07: recorded conceptually, not analysed

> Framing only — analysis later; each idea goes through its own `brainstorm → spec → plan`. Remarks in
> brackets are the agent's first reaction, not a decision. Anchor: ADR "2026-11-07 — Two principles of the
> agentic OS" and its addendum: Sidr is **an open-world operating system — open in capability, closed at the
> boundaries**. "Open world" is native to an AI agent on the **understanding** side (language has no closed
> command list; missing knowledge is "unknown", not "false") and unnatural on the **execution** side (the
> model is probabilistic, the world is adversarial — injection through content); so the gates are built,
> they do not arise by themselves.

### M. Sidr as a platform — layers, not a list of apps

Owner: a full, broad platform — its own secure anonymous messenger, chat, social network, browser,
translator, a programming workspace; devices — smartphone, PC, "satellites" (to clarify: satellite
connectivity or wearable companion devices), IoT. The agent's proposed layering: (1) the agent core with a
finite set of gates; (2) the user's device mesh — one identity, sync without a third-party cloud in the
middle; (3) the world as tool sources — apps, the web, IoT via Matter/Thread, MCP; (4) communication
human↔human and agent↔agent — a client of an open protocol, never home-made cryptography; (5) surfaces —
browser (→ J), translator as a capability of the local model (→ I), a programmer's workspace on the PC,
chat. (Selection criterion — maximum agent capability per unit of own infrastructure; a public social
network is the riskiest direction: moderation, liability, anonymity.)

### N. A local model and the user's personal space

A model that knows the user's "workspace and character" (books, work, sites) and writes messages in their
style. Analysed in the track plan's `§HANDOFF` ("Candidate for a separate block"): knowledge — an on-device
personal index; character — an editable profile; weight fine-tuning — only for style, and later; sending on
the user's behalf — the user's last tap (→ K).

### O. Sidr network — a chat shared between Sidr users

A chat (and more broadly a "local social network") that Sidr users share with each other. (First reaction:
invite-only circles instead of a public feed — removes most of the moderation burden; end-to-end encryption
and peer-to-peer delivery with minimal "blind" relays; identity — keys, verified by QR; the distinguishing
capability — agent↔agent: "my agent arranges it with yours" under both owners' consent — e.g. two agents
agree on a meeting time, each within the limits its owner allowed; going fully serverless is not possible —
phones are switched off, networks block direct connections — but a relay carries ciphertext only.)

### P. News feed — region and topics of the user's choice

A window or a strip on the home screen where world news runs automatically; the user picks the region and
the events. (First reaction: a source on every headline and a mark on any generated summary — `DOC-ILM-5`;
moving text must be pausable — accessibility, WCAG 2.2.2; fetching news reveals interests to the server —
take generic feeds and filter on the device; off by default so the home screen stays calm; a right-to-left
ticker reads worse than a calm card feed or a widget that refreshes rarely; sources are visible to the user
and changeable by them; the agent tie-in — "what's new in Bishkek?", "more on the third one", "follow this
topic".)

### Q. A multi-layered open-world system — the Sidr ontology

The owner's amendment to the formula: *an open multi-layered system built on the laws of the real world
(mathematics, physics, computer science); closed at the boundary of each layer.* Spheres (ocean, atmosphere,
space) do not cross their bounds, yet form one whole governed from one core; layers are visible to each
other, some are invisible; the subject — the person — moves between layers. Refinements from the discussion:
- a boundary is a **membrane**, not a wall: a pair of layers has two relations — **visibility** and
  **permeability**;
- the core is a **law**, the same in every layer, not a ruler; so the number of layers is unbounded;
- **no layer is invisible to its owner** — invisibility only towards agents, models and other people;
- resonance with the doctrine: *barzakh* (55:19–20), *mizan* (55:7–9), orbits (36:40).

Draft of concepts and axioms (Russian): [`docs/governing/sidr-ontology-v0.1.md`](governing/sidr-ontology-v0.1.md).
