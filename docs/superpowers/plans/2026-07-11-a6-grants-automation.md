# A6 - Grants and Automation Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A6 is Stage 3. Do not start production implementation until A1, A4,
> and A5 exist.

**Goal:** Define and implement revocable grants and policy-bounded automation.

**Spec:** `docs/superpowers/specs/2026-07-11-a6-grants-automation-design.md`.

## Prerequisites

- A1 Tool tiers and registry.
- A4 Agent Runtime.
- A5 Activity/Audit trace.
- DS-5 action/safety.
- Owner approval for Stage 3 automation scope.

## Global Constraints

- No commits unless the owner explicitly asks.
- No accessibility tier implementation in this slice without separate approval.
- No hidden background automation.
- No unrestricted tool access.
- No Agents/Automation UI before grant model is real.
- No normal launcher dependency on automation.

## Task 1: Grant Requirements

- [ ] Define first supported automation scenarios.
- [ ] Define agent/automation identity.
- [ ] Define allowed tool tiers.
- [ ] Define grant limits and quiet hours.
- [ ] Define revocation and expiration.
- [ ] Define audit requirements.

Acceptance:

- owner-approved grant requirements.

## Task 2: Domain Grant Model

- [ ] Add pure domain grant models.
- [ ] Add `AutomationPolicy`.
- [ ] Add policy evaluator.
- [ ] Add tests for allow/block/revoke/expire/quiet hours.

Acceptance:

- no Android/UI/data imports;
- denied grant blocks execution.

## Task 3: Runtime Integration

- [ ] Integrate grants with A4 precondition/consent checks.
- [ ] Block tools outside grant.
- [ ] Require consent on tier escalation.
- [ ] Record grant decision into A5 audit.

Acceptance:

- no grant bypass;
- audit includes grant source.

## Task 4: Persistence and Disable Path

- [ ] Add grant store only after domain policy is tested.
- [ ] Add disable-all automation.
- [ ] Add revoke one grant.
- [ ] Add retention/audit alignment with A5.

Acceptance:

- user can disable automation completely;
- revoked grants prevent future execution.

## Task 5: Accessibility Tier Discovery

Separate approval required.

- [ ] Define education flow.
- [ ] Define system settings handoff.
- [ ] Define revocation detection.
- [ ] Define per-tool accessibility limits.
- [ ] Define audit copy.

Acceptance:

- no code unless owner approves accessibility tier.

## Task 6: Conditional UI

Start only after domain/data are real.

- [ ] Grants list in Settings or future Agents/Automation surface.
- [ ] Grant detail.
- [ ] Revoke/disable all.
- [ ] Audit link.
- [ ] Quiet hours controls.

Acceptance:

- no fake agents;
- UI reflects real grants.

## Full Verification Gate

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:settings:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance is required for any permission/accessibility path.

## Stop Conditions

Stop and re-scope if:

- A4 runtime is missing;
- A5 audit is missing;
- grants cannot be revoked;
- automation can run without trace;
- accessibility tier slips in without education/approval;
- normal launcher depends on automation.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A6 Grants and Automation from:
- docs/superpowers/specs/2026-07-11-a6-grants-automation-design.md
- docs/superpowers/plans/2026-07-11-a6-grants-automation.md

Do not start production implementation unless A1 tools, A4 runtime, and A5 audit traces exist. Automation
must be grant-bounded, revocable, auditable, optional, and fully disable-able. No accessibility tier without
separate owner approval.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
