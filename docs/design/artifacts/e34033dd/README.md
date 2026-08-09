# Claude artifact e34033dd — SIDR soft classic grey

Source artifacts (two separate Claude artifacts referenced across this design track):
- **e34033dd** ("SIDR — Screen Set (soft classic grey)"):
  `https://claude.ai/code/artifact/e34033dd-f8ec-4bc6-a2c9-82f951349ade?org=3ad289cb-2ed0-4a18-aadc-05e5b90860f3`
- **177507de** ("SIDR — Visual Direction Study", referenced from
  `docs/superpowers/specs/2026-07-10-visual-identity-soft-grey-design.md`, the earlier study that fed the
  DS-1 token spec):
  `https://claude.ai/code/artifact/177507de-9dc2-4cdb-95f1-75e92f1ca26f`

Captured from owner-provided screenshots on 2026-07-11, plus (2026-07-12) the **raw HTML source of both
artifacts**, fetched directly and committed alongside the screenshots — a plain `curl`/headless fetch of
a `claude.ai/code/artifact/{uuid}` URL returns only a Cloudflare/SPA shell, but `WebFetch` retrieves the
real content using the owner's `claude.ai` login, so the source is now durable in-repo in its own right,
not just as pixels.

## Files

- `source.html` — raw HTML source of artifact **e34033dd** (the Screen Set below).
- `visual-direction-study-soft-classic-grey.source.html` — raw HTML source of artifact **177507de** (the
  Visual Direction Study below).
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

## Deviation note (2026-08-08) — shipped Home prayer strip is times-only

DS-6B Prayer Correctness landed code-complete (device-pending; ADR "2026-08-08 — DS-6B Prayer
Correctness (code-complete, device-pending)" in `ai-context/decisions.md`). During on-device iteration
on SM-A325F the owner requested a minimal look for the Home prayer strip: it now shows **times only**
(`HH:MM` per prayer cell, next prayer marked by an inverted chip). This is a deliberate deviation from
this artifact set's depiction of a labelled strip (see "prayer strip remains thin and secondary, with
next-prayer emphasis and provenance expected later" above) — prayer names moved to each cell's
`contentDescription` (still announced by TalkBack) and the provenance line (`LOCAL CALC · METHOD ·
MADHAB · LOCATION`) is no longer drawn on the strip itself. Provenance and method/madhab detail remain
fully visible on the prayer detail screen, and the strip's own `contentDescription` still carries
provenance. The status chip stays hidden only for calm states; it still surfaces as an explicit warning
label for stale/tz-conflict/failed states, so the "stale must be labelled stale" invariant is unaffected.

## Status (2026-07-11) — the running app now renders this artifact set

DS-1 through DS-4 and the **Vision MVP (Preview)** plan
(`docs/superpowers/plans/2026-07-11-vision-mvp-preview.md`) together bring every shipping surface in this
artifact set onto the approved soft-classic-grey look: Home, App Drawer (grid + Groups/A-Z toggle),
Settings, Memory, Assistant, and Permission Education are real, functional DS screens; the 5-tab bottom
bar now hosts Home alongside Tasks/Agents/Activity/Terminal. The **agentic future-contract surfaces**
(screen-set-04) are intentionally still previews, not implementations: Tasks/Agents/Activity/Terminal and
an Interaction-Moments screen render the same visual language, each carrying a persistent `PREVIEW` badge
or banner, with every action inert and no fabricated task/execution/activity/agent data — matching this
README's own "do not use them to prematurely implement" guidance above. They will graduate to real
surfaces only once their real backing (A1/A4/A5/A6) exists.
