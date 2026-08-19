# Imported design docs — provenance archive (DS-0)

> **What this folder is.** The seven design documents the owner imported for the SIDR redesign, committed
> verbatim as-received so future work has the primary source material in-repo. This is an **archive of
> inputs**, not the governing spec.
>
> **Moved out 2026-08-19 (Этап 3):** `SIDR Design System Master Plan.md` used to sit here despite being a
> *living governing* document and not one of the seven imports — a living document inside an archive. It now
> lives at [`../governing/sidr-design-system-master-plan-v1.2.md`](../governing/sidr-design-system-master-plan-v1.2.md),
> alongside the doctrine matrix and the agentic Master Plan. Closed DS specs and plans still link to the old
> path; those are history and were left as written. See [`../governing/README.md`](../governing/README.md). What actually governs the build is recorded in the DS-0 ADR and the two
> living specs linked below — some of what these PDFs propose was consciously **overridden** (colour → grey,
> 5-tab IA → 4 surfaces, live agentic surfaces → deferred). Read the ADR before treating any PDF as current.

## The documents

| File | Version / role | Standing |
|---|---|---|
| `SIDR Design System v1.pdf` | **v1 — Vision / North-Star** | Earliest, most aspirational (5-tab nav, live *Agents* tab, amber-only). Direction, **superseded** on colour + IA. |
| `SIDR Design Doctrine & Foundation v1.1.pdf` | **v1.1 — Governing doctrine** | Principles/foundation. **Principles remain in force**; its green/amber accent is superseded by grey. |
| `SIDR Component Library v1.1.pdf` | v1.1 — components | Component reference. Feeds DS-2/DS-3; retinted to the grey tokens. |
| `SIDR Design Migration Plan v1.1.pdf` | v1.1 — migration plan | The reuse→evolve→replace / token→primitive→component→pattern layering the DS-blocks follow. |
| `SIDR Visual Acceptance Spec v1.1.pdf` | v1.1 — acceptance | Visual acceptance criteria (font-scale, RTL, dark/light, a11y). |
| `SIDR Current UI Inventory & Gap Map.pdf` | input — inventory | Snapshot of the pre-redesign UI + gaps. Point-in-time input. |
| `SIDR Design & Architecture Audit.pdf` | input — audit | The audit that triggered the grey identity + the A1–A6 architecture direction. |

## What actually governs now (living, in-repo)

- **Visual identity:** [`../superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md`](../superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md)
  — the "soft classic grey" spec (APPROVED). **Supersedes the colour/accent portion of every PDF above.**
- **Target architecture:** [`../agentic-os-architecture.md`](../agentic-os-architecture.md) — the six-layer
  agentic target A1–A6 (golden rule: a surface's UI is built only when its engine is real).
- **Reconciliation of record:** ADR **"2026-07-11 — DS-0 (design provenance backfill)"** in
  [`../../ai-context/decisions.md`](../../ai-context/decisions.md) — the three v1-vs-v1.1 conflict
  resolutions, the nine engineering deviations/additions from implementation, and the DS-block sequence.

## Visual north-star artifacts

- [`artifacts/e34033dd/`](artifacts/e34033dd/) — owner-provided Claude artifact screenshots for the
  soft-classic-grey Home direction and screen set. These are composition references for DS-3/DS-4 and
  future-contract references for agentic surfaces; they do not override ADRs or living specs.

## Where these fit in the DS sequence

`DS-0` (this archive + the reconciliation ADR) → `DS-1` tokens + Roborazzi harness (**DONE**) →
`DS-2/3` primitives & controls (provenance line, press-invert chips) → `DS-4` Home → `DS-6A` sacred header →
`DS-6B` prayer correctness capability / prayer data (separate spec) → `DS-7` memory surfaces /
memory-migration (Learned Choices first, S2-2 Aliases conditional, in the grey language) → `DS-8`
Activity foundations (component/contract first; production only after real Activity scope) → `DS-9`
Execution foundations (preview contracts only until A4 runtime) → `DS-10` Assistant migration → DS v1.1
release gate. Agentic production surfaces remain gated by the separate A1/A2/A3 → A4/A5/A6 architecture
specs; A1 Tool/Capability is the next architecture slice before any runtime-backed agentic surface.
