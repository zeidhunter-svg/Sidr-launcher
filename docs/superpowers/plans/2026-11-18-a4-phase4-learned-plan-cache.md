# A4′ Phase 4 — the learned-plan cache · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: forks answered (§0.1); the plan body follows in its own commit.** Branch `launcher--7`, base
> `abf3531`, gate baseline **1841 / 0 / 0**.

## 0. Pre-flight

### 0.1 · The owner's forks — answered 2026-11-17/18, before this plan was written

Asked one at a time, each with meaning, options and cost, in the session of 2026-11-17 20:46–22:11 UTC
(23:46–01:11 Moscow), which ended before it wrote anything; fork 6 was answered in the next session
(2026-11-18). The questions, options and answers verbatim: git-ignored
`.superpowers/sdd/2026-11-18-a4-phase4-plan/forks-session-a62d319e-raw.txt`.
**Answer line: `1: a · 2: a · 3: c · 4: a (3) · 5: a · 6: a`.**

| Fork | Answer | What it decides |
|---|---|---|
| **4-1** the cache's key (spec §8.1) | **(a) a form with slots** | the user's words the model took into an argument become a slot: «удали телеграм» → «удали …»; the streak is the form's; once pinned, «удали X» works for any X; each language is its own form around one template |
| **4-2** what is stored (§8.2) | **(a) the model's answer with slots** | the validated answer in its own shape: tool, arguments as words (the user's words → slots, the model's own words → constants), references; no package, no risk, no effect. Replay goes through **the same validator and binder** as step 7 |
| **4-3** what the 4th time shows (§8.3) | **(c) at once, no switch** | a pinned command runs like an authored one; a risky step's consent card is the floor, asked every time in any mode; the **interpretation** card only when the user must choose or the model's text goes out (`DOC-MZN-2`). The switch «Спрашивать перед выученными командами» comes with a modes block — **candidate block** (modes as in Claude Code + grant rules + the floor + an anonymity axis + a full-access window; brainstorm → spec). Needs an ADR amending D3 |
| **4-4** the threshold (§8.4) | **(a) 3, the shared constant** | `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD` for everything learned; one «раз» = «Выполнить» on a model card of the same form and template **after which the session started** |
| **4-5** provenance (`DOC-ILM-5`, §8.5) | **(a) a mark in process memory** | the session card of a learned command: «Выученная команда» + «Забыть»; lives while the process lives. Named limit: a card restored after process death mid-plan does not carry it. A Settings section «Выученные команды» (form, steps, streak N/3, forget) in every variant — `DOC-AML-5` |
| **4-6** storage | **(a) a new table, schema 4 → 5** | `learned_plans` in `sidr_history.db`; `Migration4To5` = `CREATE TABLE` only; `5.json`; the column-name guard; a cap with eviction. Structured rows are the future source of «goal → plan» pairs (ADR 2/4). The migration: an attempt to prove it in the gate (Robolectric) + a case in `MigrationTest` for the owner's acceptance |

**Recorded beside the forks (owner, 2026-11-17):** «agentic control of the system — one main agent between
the user and the system» — answered: yes, that is Sidr's thought (the session's answer, raw file 21:33 UTC);
«where does the agent's memory of the user live» — three levels (memory without training, today; personal
context for a local model, A2/A3; LoRA for style only), and this phase's store is the first structured
source of confirmed «goal → plan» pairs (raw file 22:11 UTC). Neither is a decision of this plan.
