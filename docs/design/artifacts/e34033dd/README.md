# Claude artifact e34033dd — SIDR soft classic grey

Source artifact:
`https://claude.ai/code/artifact/e34033dd-f8ec-4bc6-a2c9-82f951349ade?org=3ad289cb-2ed0-4a18-aadc-05e5b90860f3`

Captured from owner-provided screenshots on 2026-07-11. The Claude artifact is protected by an
interactive browser challenge, so these local captures are the durable in-repo visual source.

## Files

- `visual-direction-study-soft-classic-grey.png` — Home visual direction study, dark + light, token notes, and two moment cards.
- `screen-set-01-shipping-screens.png` — approved shipping surfaces: Home, Home typing/results, App Drawer, Settings.
- `screen-set-02-memory-assistant-permission.png` — Memory, Assistant, Permission Education, plus the start of interaction moments.
- `screen-set-03-interaction-moments.png` — Clarify, Action Gate, SAFE proposal, Result, Partial Result, and Error moments.
- `screen-set-04-agentic-future-contracts.png` — pre-designed future task flow, execution, result, agent, Activity, and Memory surfaces.

## Standing

These screenshots are **visual north-star artifacts**, not executable implementation plans. They sit below
owner decisions, ADRs, and living specs in the governing hierarchy, and above the imported provenance
archive as composition reference.

Use them to guide:

- DS-3 controls: buttons, route chips, rows, action gate, risk styling, press-invert selection.
- DS-4 Home: calm Home hierarchy, dark/light parity, Universal Input composition, Favorites, Recent,
  App Drawer entry, and privacy line.
- App Drawer migration: grouped icon grid, local search, `Groups / A-Z` toggle, and on-device/offline provenance.
- Settings migration: section-by-spacing layout, no card-per-row, SIDR toggle/navigation rows.
- DS-7 memory surfaces: local-only memory list, learned choices, aliases, and forget/export controls once
  the relevant memory slice is scoped.

Do **not** use them to prematurely implement:

- live Agents surface without A1/A4/A6 backing contracts;
- Activity journal without real A5 execution traces;
- multi-step Execution Stream without a real A4 runtime;
- fake task automation, fake cloud steps, or fake grants.

## Extracted Design Notes

Home target from the visual direction study:

- top row: `SIDR` wordmark, Gregorian date, Hijri date, settings icon; no duplicated Android clock/status UI;
- quiet English Shahada as the spiritual anchor;
- prayer strip remains thin and secondary, with next-prayer emphasis and provenance expected later;
- Universal Input is the primary control, with `>` prompt, block caret, mic affordance, and focus border;
- route chips use colourless press-invert selection (`APP / WEB / SITE / ASK`);
- Favorites are a compact icon grid, followed by Recent rows, All Apps, and a local-first privacy line;
- dark and light themes share the same layout and proportions.

Shipping screen targets:

- Home idle and Home typing/results are DS-4 targets; typing overtakes content without changing routing semantics.
- App Drawer moves toward grouped icon grids by category with local search and an A-Z toggle; categorization
  must be backed by the real app/category layer, not hardcoded in UI.
- Settings is the DS-3 proof surface: rows separated by spacing/hairlines, not cards; toggles and navigation
  rows share one control language.
- Memory combines learned choices and explicit aliases; local-only state and evidence are visible.
- Assistant uses sans prose for answers and mono only for provenance/provider metadata.
- Permission Education explains before request; it is not a fake system dialog and always leaves `Not now`.

Interaction moment targets:

- clarification is not an error; no app opens until selected;
- `SidrActionGate` has one shape for external confirmation and other consent boundaries;
- SAFE proposals are one tap, but still deliberate and never auto-run;
- risk is represented by label + marker + consequence, never colour alone;
- results honestly distinguish completed and partially completed work;
- error surfaces explain what failed, why, and what the user can do next.

Future-contract targets:

- task execution, dedicated agent cards, Activity, and execution traces are future-contract surfaces;
- consent is woven into execution, not shown as decoration;
- every step is tagged local/cloud/web where meaningful;
- ordinary completed actions should stay quiet; result surfaces appear for consequential, routed, or agentic work.

## Next Use

1. Draft DS-3 Controls spec/plan against these images plus the existing DS-2 primitive layer.
2. Implement Settings as the DS-3 proof surface before touching Home.
3. Migrate the existing routed confirmation card to `SidrActionGate` only with full parity tests.
4. Use the Home study for DS-4 after controls are stable.
