# Imported design docs — provenance archive (DS-0)

> **What this folder is.** The seven design documents the owner imported for the SIDR redesign, committed
> verbatim as-received so future work has the primary source material in-repo. This is an **archive of
> inputs**, not the governing spec. What actually governs the build is recorded in the DS-0 ADR and the two
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

## Where these fit in the DS sequence

`DS-0` (this archive + the reconciliation ADR) → `DS-1` tokens + Roborazzi harness (**DONE**) →
`DS-2/3` primitives & controls (provenance line, press-invert chips) → `DS-4` Home → `DS-6A` sacred header →
`DS-6B` prayer data (separate spec) → `DS-7` memory-migration (absorbs the S2-2 Aliases UI in the grey language).
