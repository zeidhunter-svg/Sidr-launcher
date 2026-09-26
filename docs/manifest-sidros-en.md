# Manifest SidrOS

> **Status: draft of principles, 2026-09-13.** Not a plan and not a spec: this states *what the system
> is*, not what gets built and when. Engineering consequences live in [improvements.md](improvements.md),
> Part 3. Russian original: [manifest-sidros.md](manifest-sidros.md) — that is the authored version;
> this is its translation. Governing status arrives only by the owner's decision; until then this
> document describes intent, not obligation.

**Sidr is not a launcher with AI in it. It is a system that assembles a program out of the phone's
capabilities to satisfy what you asked for.**

---

## 1. SidrOS is an operating layer, not a product name

`Sidr` is what the user sees: the launcher. `SidrOS` is the layer beneath it. **OS here is literal — an
operating system** — but not a replacement for Android's internals: a layer **above** them with
primitives of its own, addressed to an intent rather than to a program:

| OS primitive | In SidrOS |
|---|---|
| syscall table | the tool registry — the only path to the world, one call site |
| permission model | the risk gate and grants: allowed tools, limits, quiet hours |
| scheduler with quotas | a bounded executor: steps, time, cost; fail-closed, cancellable |
| process table | a session that survives process death and resumes from its cursor |
| audit log | the trace: every step with provenance |

That is what the word "OS" carries here. Not that it replaces Android, but that it has its own calls, its
own rights, its own scheduler and its own process memory.

## 2. The unit is an intent, not an app

Today, to do anything you must know which app does it, find it, and understand its interface — the app is
the addressee, the executor and the interface at once. In SidrOS the unit is the intent. Apps stay
sandboxed and unchanged, and their cooperation is not required: their capabilities become elements.

## 3. The system is a function of its elements

`behaviour = f(elements)`. An element is a tool, a resolver, a dataset, or a surface primitive. Adding an
element widens what the system can do **without a single new line in the planner**. This is not a
metaphor but a requirement on the architecture: if adding a capability requires editing the planner, the
element was declared wrong.

## 4. Elements are declared; functions are not written

The requirement on an element: it carries its own type, its own preconditions and its own effects. The
developer does not write scenarios — he extends the vocabulary. This is the only way one person covers
the long tail of tasks: not by writing features, but by declaring elements.

*Today this holds only in part: a tool carries risk, permission and an argument schema, but **there is no
model of effects**, and an argument's sort is expressed by a single value. The gap is named rather than
hidden.*

## 5. You say what you want; the system writes the program

Four stages, each with a narrow job:

**the model writes the spec → the synthesizer writes the program → the evaluator produces proposals →
the gates decide.**

The model's output is the smallest and most reviewable artifact in the chain, and it is shown to the
human before anything executes. The difficulty here is not finding the program but obtaining the spec
from a human: "set me up a chemistry lab" is a wish, not a specification. So the model is raised to the
level of the spec and taken off the level of the program.

## 6. The program is found, not generated — and finding it is checking it

A composition is **searched for** by type among the declared elements. "Set a timer for 15 minutes" is a
search for a composition of type `Text → Effect`; it was neither authored by a developer nor invented by
a model. It was found.

**Hence the central property: synthesis is verifiable by construction.** The search runs against the
spec, so **the search is the check**. With generation it is the other way round: you get a plausible
program and must test it afterwards.

Two consequences, both useful. If several compositions are valid, that is a **derived clarification
question** rather than a model's guess that the phrase was ambiguous. And if none is, that is an honest
decline rather than an invented plan.

And the honest limit: **types underdetermine intent.** Two tools sharing a signature are
indistinguishable to the search, so the authored vocabulary does not become obsolete — it answers
*designation* (which capability the human named) while synthesis answers *composition* (how to assemble
it).

## 7. A program is data

What synthesis returns is an s-expression: readable, hand-editable, diffable, cacheable, portable. One
representation for a plan, a surface, a formula and a context predicate:

```lisp
(plan    (tool set_timer (duration "15m"))
         (tool launch_app (package (resolve "clock"))))

(surface (timer :presets '(5m 15m))
         (note  "Lab notes")
         (table :data (ref dataset "periodic-table" :sha256 "a3f…")))
```

An artifact a human can read is not a side effect — it is a requirement.

## 8. The evaluator is total, closed, and ours

No `eval` of an arbitrary string, no host access, no I/O. A submitted expression can do exactly what has
been implemented. The danger in code is not computation but what it can reach; an evaluator over a closed
set of forms reaches nothing.

## 9. The evaluator produces proposals, never effects

`(tool set_timer 15m)` does not execute — it evaluates to an invocation, which then travels through
validation, preconditions, the risk gate, loop bounds, the egress allow-list and the trace. The result
type is a plan or a surface, never an effect.

## 10. The model is the only component allowed to be wrong

It sits at the input boundary and outside the execution path. Everything else is built so that a wrong
model output yields a decline, a clarification, or an honest step failure — never an unauthorized effect.

## 11. Understood once, then offline — and **identical**

The model is consulted on a cache miss, not on every request. A program once found is pinned to its goal
and replayed deterministically. Three properties follow:

- **locality is bought by the cache**, not by a model in the process;
- the model's share of the work **shrinks** with use — the opposite of how cloud agents behave;
- and the one that matters more than speed: **behaviour becomes stable.** The same phrase tomorrow yields
  the same program as today, even if the set of elements around it has changed. The cache is a mechanism
  of predictability, not an optimization.

## 12. What must be exact is never generated

Three classes of content, and they do not mix. **Canonical** — exact, versioned, addressed by hash; never
from a model. **Curated** — with its source shown. **Generated** — only where "roughly right" is
acceptable, and always marked as such.

## 13. A grant widens reach and never removes a gate

There are two kinds of "forbidden". The first is forbidden until the user allows it: that is legitimately
granted, with scope, limits and revocation. The second is what the system's integrity rests on —
executing around the registry, skipping validation, skipping the allow-list, "stop asking" on the
irreversible, a loop with no budget. The second is never granted; otherwise the honest description of the
product becomes "an agent with gates, unless they were switched off".

## 14. An environment is the unit of delegation

Rights are not configured per tool — nobody will do that. They are given to an environment — "lab",
"trip", "cinema" — with a pre-declared set of tools and pre-declared limits. A human understands "while
I'm in cinema mode you may silence calls". Every environment has an exit, and the exit is a compensation,
not a separate feature.

## 15. The surface is a result, not a design

There is no drawn screen per case: environments are unbounded in number. The agent chooses **which**
elements and with what content; layout rules decide **how** they sit. Hence a neutral, systematic
aesthetic is a structural requirement rather than a stylistic choice: any composition must look
deliberate.

## 16. Symbolic where there are types; the model where there is language

The symbolic planner covers the declared, typed tier. The model covers the discovered tier — the one
where only a name is known. Natural language — morphology, paraphrase, code-switching — stays the
model's for good: that is the match the symbolic approach lost over forty years.

## 17. Verifiability is a property of the system, not a development practice

The symbolic half can be mutated: remove a precondition and a plan must stop being valid; remove a method
and a goal must stop being planned; remove a type annotation and a composition must stop being found. A
prompt cannot be mutated. So every capability moved from the model to the symbolic side **becomes
verifiable**, and that is a direction of development rather than a detail of testing.

## 18. Identity is data, not weights

What the system learned about a person is stored as rows with provenance and retention: preferences,
aliases, environments, cached programs, stated facts. Every row can be viewed, corrected, deleted and
carried elsewhere. A model cannot be asked to forget and then checked; a row can.

---

**In one sentence:** you declare elements, the human states an intent, the system **finds** a program —
and finding it is checking it — the gates decide, and the cache makes it stable. Everything else is a
consequence.
