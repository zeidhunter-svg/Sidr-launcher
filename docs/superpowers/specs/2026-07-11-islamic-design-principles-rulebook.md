# SIDR Islamic Design Principles — Governing Rulebook (Design Spec)

> **Status: PROPOSED (2026-07-11)** via brainstorming. This spec operationalizes the eight Islamic
> principles of the imported **Design Doctrine & Foundation v1.1** (archive) into **verifiable design
> rules** that gate the DS-block migration (DS-2…DS-10). It is the *why-checked-how* layer sitting between
> the archived doctrine (the *why*) and the DS-block plans (the *what/when*).
>
> **Sources it operationalizes (does not replace):**
> - Doctrine principles: [`../../design/SIDR Design Doctrine & Foundation v1.1.md`](../../design/SIDR%20Design%20Doctrine%20%26%20Foundation%20v1.1.md) §3.1 (Islamic Order), §4 (the eight principles), §5 (five-question promise), §7 (four visual layers).
> - Living visual identity: [`2026-07-10-visual-identity-soft-grey-design.md`](2026-07-10-visual-identity-soft-grey-design.md) (grey tokens, provenance line §6.2, press-invert, accent≠status).
> - Target architecture + golden rule: [`../../agentic-os-architecture.md`](../../agentic-os-architecture.md) (a surface's UI is built only when its engine is real).
> - Acceptance baseline: [`../../design/SIDR Visual Acceptance Spec v1.1.md`](../../design/SIDR%20Visual%20Acceptance%20Spec%20v1.1.md) (RTL/Arabic, font-scale 2.0, a11y) — referenced, not duplicated.
> - Block ladder: [`../../design/SIDR Design Migration Plan v1.1.md`](../../design/SIDR%20Design%20Migration%20Plan%20v1.1.md) (DS-0…DS-10).

## 0. Purpose, authority, scope

**Purpose.** The Doctrine states the eight principles as prose. That prose does not, on its own, catch a
violation. This rulebook turns each principle into concrete, falsifiable **rules** — each carrying a
pass example, a fail counter-example, and a **verification method bound to a real repo mechanism**
(guard test / screenshot / semantics test / unit test / device step / manual review). The output is a set
of **acceptance criteria** every DS block inherits.

**Authority / precedence of documents.** On a conflict:
1. The **hard architecture rules** (CLAUDE.md "Hard rules") and the **agentic golden rule** always win.
2. This **rulebook** governs *behavior-of-design* (principle rules, risk model, disclosure, calm).
3. The **grey visual spec** governs *exact visual tokens* (colour hexes, type roles, shape scale).
4. On any remaining conflict between (2) and (3), stop and escalate — do not guess.

**Scope.** Presentation-only governance. This document changes **no** routing / execution / privacy /
memory / navigation semantics. It adds no preference keys and widens no outbound allow-list. Islamic
identity here is **order, restraint, truthfulness, trust, calm** — explicitly **not** crescents, mosque
imagery, "Islamic green", or calligraphy-as-decoration (Doctrine §3.1; enforced by §6 `R-SACRED-1`).

---

## 1. Precedence — resolving principle conflicts

The eight principles are **not equal at decision time**. They will collide (explain vs. stay quiet;
disclose the cloud vs. keep Home calm). A rule is only useful if it resolves the collision. The ordering:

```text
Human-authority  +  Amanah        (safety & truth)     — non-negotiable, absolute
        ↓
Adl              (equal risk = equal UI)                — applies safety uniformly
        ↓
Ilm              (explain the non-obvious)              — earns its place progressively
        ↓
Mizan            (minimum needed to decide)             — default reveal budget
        ↓
Haya  +  Sukun   (restraint & calm)                     — the resting default
```

**The heuristic (one line):** *Safety and truth are non-negotiable; consistency protects them; any
information required for an informed decision is never suppressed for calm — but information that is
**not** required yields to calm.* So: calm gives way to **required** truth; **optional** detail gives way
to calm (progressive disclosure). When two rules of equal rank conflict, escalate — do not silently pick.

---

## 2. The five-question screen gate (the executable spine)

This is the checklist run on **every** screen block at acceptance (Doctrine §5). The eight principles in §3
are the *rationale library* behind these five questions; a reviewer who reads only this section can still
gate a screen.

| # | Question the screen MUST answer | Backed by | How it is checked |
|---|---|---|---|
| Q1 | **Where am I?** truthful surface identity + honest nav (no phantom routes) | Adl, Computational Precision | `semantics` (title/label) + `device` (back/nav) |
| Q2 | **What did the system understand?** interpretation echoed in the user's words | Niyyah | `screenshot` (echo present) + `unit` |
| Q3 | **What's happening now?** real process state only — never fabricated activity | Sukun, Computational Precision | `screenshot` (idle vs. active) + `unit` |
| Q4 | **Is a decision required from me?** proposals non-executing; gates explicit | Human-authority, Adl | `unit` (no auto-exec) + `device` |
| Q5 | **How can I change / cancel / verify?** cancel/undo present; provenance to verify | Human-authority, Amanah, Ilm | `unit` (cancel single-fire) + `screenshot` (provenance) |

**Gate rule:** if a screen cannot answer all five, its composition is wrong — revise the screen, not the
checklist. A screen may answer a question *trivially* (a plain launch answers Q3/Q4 with "nothing is
happening / no decision") — trivial is fine; **absent** is a failure.

---

## 3. The eight principles as rules

Each rule: an id, a testable statement, **pass** / **fail** examples, a **verify** method (primary type +
the real mechanism), and **governs** (visual layer(s) + DS block(s) it gates). Verification types are
defined in §5. Rules marked `manual` are honestly non-automatable and land as a review-checklist item.

### 3.1 Niyyah — clarity of intention
*The system shows how it understood the user; it never hides ambiguity behind false confidence.*

- **R-NIYYAH-1** — Ambiguity is resolved by an explicit choice, never a silent guess. An ambiguous parse
  renders a candidate list / `NeedsConfirmation`, not an execution.
  *Pass:* "open [two-app match]" → choice surface. *Fail:* auto-launches the likelier app with no choice.
  *Verify:* `unit` (rule-matcher `NeedsConfirmation` branch + `LauncherViewModelTest`) + `screenshot`
  (ambiguity state). *Governs:* Agency; DS-4, DS-5, DS-7.
- **R-NIYYAH-2** — A routed/interpreted action echoes the interpretation in the user's own words at/before
  confirmation. *Pass:* confirm card shows `> open https://github.com`. *Fail:* card shows only "Proceed?".
  *Verify:* `screenshot` (`ConfirmActionCard`). *Governs:* Agency; DS-5.
- **R-NIYYAH-3** — A low-confidence match is never dressed in high-confidence chrome (no "Executing…" for a
  guess). *Verify:* `unit` (confidence policy → Suggest, not auto-exec) + `manual`. *Governs:* Agency; DS-4.

### 3.2 Amanah — trust & responsibility
*User data, memory, permissions and context are held in trust; consequences are never hidden.*

- **R-AMANAH-1** — Any egress (cloud AI, external app via `ACTION_VIEW`, Play Store) renders a disclosure
  naming the destination **at or before** the handoff, never after. *Pass:* confirm card names
  "github.com" / "your AI provider" before the tap. *Fail:* app opens, disclosure shown next frame.
  *Verify:* `screenshot` + `arch-guard` (`OutboundContextPolicy` allow-list unchanged; `AiRequestGuardTest`).
  *Governs:* Agency/System; DS-5, DS-10.
- **R-AMANAH-2** — Every stored memory item (learned choice, alias) is user-deletable from a management
  surface; deletion is honored without a dark-pattern re-confirm loop. *Pass:* one clear delete → gone.
  *Fail:* "Are you sure? / Really sure?" nag. *Verify:* `device` + `unit` (VM delete). *Governs:*
  Human/Memory; DS-7.
- **R-AMANAH-3** — Data origin/destination is truthful and visible: **LOCAL** vs **CLOUD** provenance is
  shown wherever it differs, using the **fixed** status tokens (success=local, attention=cloud), never left
  for the user to infer. *Verify:* `screenshot` + `arch-guard` (status tokens fixed, not accent). *Governs:*
  System; DS-4, DS-5, DS-7, DS-10.
- **R-AMANAH-4** — No permission is requested without prior education of *why*; denial disables only that
  feature and never blocks core. *Verify:* `unit`/`device` (permission-education flow). *Governs:*
  Human/Agency; DS-5.

### 3.3 Mizan — balance / progressive disclosure
*Show the minimum needed to decide; reveal detail progressively.*

- **R-MIZAN-1** — The more ordinary the action, the less UI it generates: a plain app launch produces **no**
  result card, **no** toast, **no** disclosure. *Pass:* tap app → launches, Home unchanged. *Fail:* "✓
  Launched Telegram" toast. *Verify:* `screenshot` (plain launch = no card) + `manual`. *Governs:* all;
  DS-4, DS-5.
- **R-MIZAN-2** — Detail (a "why", full provenance, expanded plan) is opt-in, not always-on. *Verify:*
  `screenshot` (collapsed default) + `semantics` (expandable). *Governs:* Agency/Memory; DS-5, DS-7.
- **R-MIZAN-3** — Disclosure/result surfaces appear only for routed / agentic / risky actions or a
  meaningful learning event — never for the ordinary. *Verify:* `unit` (outcome→surface mapping) +
  `manual`. *Governs:* Agency; DS-5.

### 3.4 Ilm — knowledge & explainability
*Help the user understand the system with brief, useful "why" — not a technical log.*

- **R-ILM-1** — A non-obvious system choice carries a one-line, plain-language reason available on the
  surface. *Pass:* an auto-resolved launch shows "chosen because you confirmed this 3×" on the memory/result
  surface. *Fail:* silent auto-resolve with the reason nowhere. *Verify:* `screenshot` + `unit` (reason
  string present). *Governs:* Agency/Memory; DS-5, DS-7.
- **R-ILM-2** — Explanations are short and useful, never a raw technical dump; the human sentence is Sans,
  embedded values are Mono. *Verify:* `manual` (content) + `screenshot`. *Governs:* Human/System; DS-7,
  DS-10.
- **R-ILM-3** — "Processed locally · no data left this device" is stated where true; a cloud send states
  exactly what is sent (the command + the action schema — nothing else). *Verify:* `screenshot` +
  `arch-guard` (statement matches the real outbound allow-list). *Governs:* System; DS-5, DS-10.

### 3.5 Adl — consistency of rules
*Equal consequence gets equal representation, regardless of who proposed the action.*

- **R-ADL-1** — Identical consequence ⇒ identical risk UI, independent of source (rule / local model /
  cloud / agent / user). SAFE-vs-CONFIRM is a pure function of `ActionRiskLevel`, never of the proposer.
  *Pass:* a rule-routed and an LLM-routed `open_url` show the same risk chip. *Fail:* the LLM proposal gets
  a scarier chip for the same action. *Verify:* `unit` (gate derived from risk-level, not source) +
  `screenshot` parity. *Governs:* Agency; DS-5.
- **R-ADL-2** — Risk is never signalled by colour alone: always label + marker + consequence text + a
  separate confirm control. *Pass:* the surface reads correctly in greyscale. *Fail:* a red border is the
  only risk signal. *Verify:* `manual`/`semantics` + `screenshot` (greyscale legible). *Governs:* Agency;
  DS-5.
- **R-ADL-3** — A status token means the same thing everywhere (success=local/safe, attention=cloud/warn,
  caution=confirm/handoff, danger=destructive/failed, info=neutral); status is **never** re-tinted by the
  accent. *Verify:* `arch-guard` (`accent_and_status_are_distinct_tokens`, already green from DS-1) +
  `screenshot`. *Governs:* all (cross-cutting).

### 3.6 Haya — restraint
*No aggression, manipulation, urgency, noise, or ostentation.*

- **R-HAYA-1** — No artificial urgency, manipulative CTA, or emotional pressure; confirm/consequence text is
  neutral. *Pass:* "This opens github.com in your browser." *Fail:* "⚠️ Act now — don't miss out!".
  *Verify:* `manual` (content review). *Governs:* Agency/Human; all screen blocks.
- **R-HAYA-2** — No decorative or always-on animation; motion explains a state change only. *Verify:*
  `screenshot` (idle static) + `unit` (reduce-motion honored). *Governs:* all; DS-4, DS-6A.
- **R-HAYA-3** — The accent is barely present — subject to the per-screen accent budget in §6. *Verify:*
  `manual`/`screenshot` (accent-element count). *Governs:* all.

### 3.7 Sukun — calm
*Calm is the normal state; activity is shown only when a process really runs.*

- **R-SUKUN-1** — Idle Home has **zero** animated elements and no busy/progress affordance; progress/motion
  appears only while a real process runs. *Pass:* idle-Home golden has no spinner / no caret animation off
  focus. *Fail:* ambient pulse / scanlines. *Verify:* `screenshot` (idle) + `unit` (no progress when idle).
  *Governs:* all; DS-4, DS-6A.
- **R-SUKUN-2** — No fake activity: the UI never shows a process that is not really running (no fabricated
  multi-step plan for a single request). *Verify:* `arch-guard`/`manual` (execution surface stays
  preview-only until a real model exists — golden rule). *Governs:* Agency; DS-9 (deferred).
- **R-SUKUN-3** — The sacred zone is quiet: static, generous whitespace, no CTA, no animation, no
  truncation. *Verify:* `screenshot` + `semantics` (non-interactive). *Governs:* Sacred; DS-6A.

### 3.8 Human authority
*The user owns the decision; the AI may understand, propose, explain, prepare, and execute the permitted —
never more.*

- **R-HUMAN-1** — No risky action auto-executes; AI proposals are non-executing until an explicit user
  confirm (R4). *Pass:* a routed CONFIRM action waits. *Fail:* an LLM proposal opens a URL on its own.
  *Verify:* `unit` (`RouteCommandUseCaseTest` / `ExecuteActionUseCaseTest`) + `device`. *Governs:* Agency;
  DS-4, DS-5.
- **R-HUMAN-2** — Irreversible/destructive actions are never masked as ordinary; they get the strongest gate
  and explicit consequence text. *Verify:* `unit` (`DANGEROUS` → strongest gate) + `manual`. *Governs:*
  Agency; DS-5.
- **R-HUMAN-3** — The user can always change / cancel / verify; cancel is a first-class, single-fire
  control. *Verify:* `unit` (cancel callback fires exactly once) + `device`. *Governs:* Agency; DS-5.
- **R-HUMAN-4** — The AI never silently expands its authority or edits safety rules; the outbound allow-list
  is the single egress and widens only by an explicit, recorded decision. *Verify:* `arch-guard` (privacy
  allow-list guard). *Governs:* System; cross-cutting.

---

## 4. DS-block → principle coverage matrix

Two tiers, honoring the golden rule. **Real-engine blocks** get concrete inherited checks now.
**Deferred blocks** (engine not yet real) get a stub of the principles they *will* inherit — no detailed
criteria are written for a surface that cannot be built yet.

### 4.1 Real-engine tier (concrete now)

| Block | Must demonstrate (principles) | Inherited key checks |
|---|---|---|
| **DS-2** Primitives | Adl, Amanah, Ilm, Sukun | `R-ADL-3` (fixed status), provenance-line primitive (§7 keystone), `R-SUKUN-1` static defaults; screenshot goldens per primitive |
| **DS-3** Controls | Adl, Haya, Human-authority | `R-ADL-2` (risk not colour-only), `R-HAYA-1/3`, 48dp + single-fire; press-invert chip (colourless selection) |
| **DS-4** Universal Input | Niyyah, Mizan, Sukun, Human-authority | Q2 echo, `R-MIZAN-1` (plain launch = no card), `R-SUKUN-1` idle, `R-HUMAN-1` no auto-exec; full router parity |
| **DS-5** Action & Safety | **all** (this is the risk surface) | `R-ADL-1/2`, `R-AMANAH-1`, `R-HUMAN-1/2/3`, `R-NIYYAH-2`, `R-ILM-1/3`; the five-question gate in full |
| **DS-6A** Sacred header | Sukun, Haya, `R-SACRED-1` | `R-SUKUN-3` quiet sacred zone, aniconism, RTL/Arabic + font-scale (§7); non-interactive semantics |
| **DS-7** Memory / Learned Choices | Amanah, Ilm, Mizan | `R-AMANAH-2` (deletable, no nag), `R-ILM-1` (the "why"), `R-MIZAN-2` (disclosure opt-in); local-only label truthful |
| **DS-10** Assistant | Amanah, Ilm, Haya | `R-AMANAH-1/3` (cloud disclosure), `R-ILM-2/3`, `R-HAYA-1`; streaming/cancel/retry preserved |

### 4.2 Deferred tier (stub — build criteria when the engine is real)

| Block | Will inherit | Gate before any UI |
|---|---|---|
| **DS-6B** Prayer data | Amanah, Ilm, Adl (provenance mandatory, fail-visible) | needs its own approved **data** spec (source · method · offline · TZ · privacy) — no computed time without provenance |
| **DS-8** Activity | Amanah, Ilm, Mizan | needs an approved Activity domain/use-case; no new persistence to fill a screen |
| **DS-9** Execution | Human-authority, Adl, **Sukun (`R-SUKUN-2`)** | no production use until a real multi-step model exists; previews only |

---

## 5. Verification taxonomy

Every rule cites exactly one **primary** type from this closed set (a rule may add a secondary). This keeps
"testable" honest and reuses the repo's existing enforcement culture rather than inventing a parallel one.

| Type | What it means | Real mechanism in this repo |
|---|---|---|
| `screenshot` | Golden image assertion | Roborazzi (`:core:ui:verifyRoborazziDebug`, DS-1 harness) |
| `semantics` | Compose semantics / TalkBack / a11y | semantics-tree test; `contentDescription`, `Role`, focus order |
| `arch-guard` | Architecture / dependency / privacy guard | `OutboundContextPolicy`, `AiRequestGuardTest`, `PrivacyInventoryGuardTest`, `accent_and_status_are_distinct_tokens` |
| `unit` | Domain / ViewModel behavior test | `RouteCommandUseCaseTest`, `ExecuteActionUseCaseTest`, `LauncherViewModelTest` |
| `device` | SM-A325F device-acceptance step | scripted adb acceptance (per existing device-acceptance briefs) |
| `manual` | Human visual/content review item | rulebook review checklist (honestly non-automatable) |

**Automatable now:** `screenshot`, `arch-guard`, `unit`, most `semantics`. **Manual/deferred:** `manual`
(subjective content/aesthetics) and `device` (hardware). A rule that can only be `manual` is allowed but
must say so — no aspirational "should be tested".

---

## 6. Aniconism + measurable calm (teeth for the hardest principles)

The two things the Doctrine treats as the heart yet are easiest to fail silently.

- **R-SACRED-1 (aniconism)** — No figurative or ornamental religious imagery anywhere: no crescents, mosque
  silhouettes, lanterns, or calligraphy-as-decoration. The sacred identity is **typography + whitespace +
  restraint**, not imagery (Doctrine §3.1). *Pass:* the sacred header is set text, centered, quiet. *Fail:*
  a mosque-dome watermark behind the Shahada. *Verify:* `manual` visual-review gate + a `drawable/` inventory
  review (no religious-figurative assets). *Governs:* Sacred / all.

- **Calm budgets** — turning Sukun/Haya "vibes" into counts:
  - **Idle animation budget = 0.** No animated element on an idle surface (`R-SUKUN-1`).
  - **Accent budget ≤ 2–3 distinct accent-coloured elements per screen** (accent is "barely present" — grey
    spec §2/§6). Everything else is neutral grey/text roles. (Exact N tuned in the DS-2/DS-3 plans.)
  - **No status-by-colour-only** (`R-ADL-2`) — every status also carries text + marker.
  - **Sacred-density budget:** the sacred header holds the Shahada (+ optional single prayer line) and
    nothing that competes for attention (`R-SUKUN-3`).
  - **Motion within doctrine tokens** (100–360ms) and reduce-motion honored (`R-HAYA-2`).

---

## 7. Cross-cutting non-negotiable gates (every block inherits)

Referenced from existing specs — **not** re-specified here:

- **RTL / Arabic correctness** and **font-scale 2.0 usability** (buttons wrap, rows stack) — Visual
  Acceptance Spec v1.1. Universal, because they are correctness (Amanah/accessibility), not aesthetics.
- **48dp touch targets, visible focus, TalkBack order + labels** — a11y baseline.
- **Provenance-line keystone.** The `SOURCE · detail · freshness` primitive (grey spec §6.2) is where
  **Amanah + Ilm + the System layer converge** — it is the physical embodiment of "truthful state". It is
  therefore the **priority-1 primitive of DS-2**; DS-4/5/6B/7/10 all consume it.
- **Accent ≠ status** token separation (already guarded from DS-1).

---

## 8. Non-goals

- Not a visual redesign of any screen (that is DS-4+). Not a token change (that is DS-1, done).
- Not prayer-time *data* engineering — that is DS-6B's own data spec (§4.2). This rulebook only fixes that
  prayer times, when shown, obey Amanah/Ilm/Adl (provenance mandatory, fail-visible).
- Not new automated tests by itself — the DS-block **plans** wire the checks this rulebook names. This spec
  defines *which* checks each rule/block owes.
- No Islamic ornament, crescent/mosque iconography, "Islamic green", or per-screen calligraphy (Doctrine
  §3.1; `R-SACRED-1`).

## 9. Success criteria (for this spec)

- Each of the eight principles has ≥1 falsifiable rule with pass **and** fail examples. ✅ (§3)
- Every rule cites a primary verification type bound to a real mechanism, or is honestly marked `manual`. ✅ (§3, §5)
- A precedence order resolves inter-principle conflicts. ✅ (§1)
- An executable five-question gate exists as the per-screen spine. ✅ (§2)
- Every real-engine DS block has an inherited-coverage row; deferred blocks are stubbed, honoring the golden
  rule. ✅ (§4)
- Aniconism and calm are first-class **tested** rules, not disclaimers. ✅ (§6)
- Nothing here contradicts the hard architecture rules, the agentic golden rule, or the grey visual spec. ✅ (§0)
