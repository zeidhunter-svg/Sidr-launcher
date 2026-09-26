# Design System v1.1 - Release Gate Checklist

> **STATUS: PROPOSED (2026-07-11).** This checklist defines what "SIDR Design System v1.1 adopted" means.
> It is not a commit plan; it is the close-out gate for the DS track.

## Scope

Covered blocks:

```text
DS-0 provenance
DS-1 token/theme layer
DS-2 primitives
DS-3 controls
DS-4 Home / Universal Input
DS-5 action and safety
DS-6A sacred header
DS-6B prayer correctness
DS-7 memory surfaces
DS-8 activity foundations
DS-9 execution foundations
DS-10 assistant migration
```

Blocks DS-8/DS-9 may close as component/contract-only if A4/A5 are not real yet. They must not be counted
as production Activity/Execution adoption.

## Architecture Gates

- [ ] No `core/ui -> domain` dependency.
- [ ] No `core/ui -> data` dependency.
- [ ] No `core/ui -> feature` dependency.
- [ ] No new `feature -> feature` dependency.
- [ ] Feature modules own presentation mapping from domain to UI.
- [ ] Domain remains Android-free.
- [ ] Data owns Room/DataStore/network/Android implementations.
- [ ] No production agent/activity/execution screen before engine exists.

## Visual Gates

- [ ] Soft-classic-grey tokens used by migrated surfaces.
- [ ] Dark and light themes pass screenshot review.
- [ ] Accent and semantic status remain separate.
- [ ] Risk is never colour-only.
- [ ] Press-invert selection used for route/segmented controls.
- [ ] No global CRT/scanline overlay.
- [ ] No fake decorative agent/prayer/activity state.
- [ ] No text overlap at 360dp.
- [ ] Font-scale 2.0 smoke passes for migrated surfaces.
- [ ] RTL smoke passes for controls and relevant surfaces.

## Behaviour Gates

- [ ] Router-off parity preserved.
- [ ] Offline launcher core works.
- [ ] SAFE proposals still require deliberate tap.
- [ ] CONFIRM actions require explicit confirmation.
- [ ] Cancel never confirms.
- [ ] Duplicate confirm does not execute twice.
- [ ] Permission education does not request on entry.
- [ ] Permission denial affects only the related feature.
- [ ] Assistant initial prompt prefill does not auto-send.
- [ ] Assistant prompt/reply remains transient.

## Privacy Gates

- [ ] Privacy guard tests green.
- [ ] BYOK key never logged or displayed.
- [ ] Assistant provider/cloud disclosure visible.
- [ ] Memory items are viewable/deletable/local by default.
- [ ] Prayer precise location never enters AI/cloud prompts/logs.
- [ ] Activity persistence remains opt-in if implemented.
- [ ] No hidden prompt/history journal.
- [ ] No installed-app list cloud egress without explicit opt-in.

## Religious Correctness Gates

- [ ] Sacred Header text approved.
- [ ] Arabic rendering approved before production Arabic Home integration.
- [ ] Prayer times never shown without provenance.
- [ ] Prayer authority/method visible.
- [ ] Prayer timezone/DST correctness tested.
- [ ] Prayer stale/offline/failure states visible.
- [ ] No fake prayer strip in production.

## Test Gates

Minimum close-out command set:

```text
./gradlew :domain:test :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:assistant:testDebugUnitTest :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug testDebugUnitTest assembleDebug
```

Release close additionally:

```text
./gradlew assembleRelease
```

If a module/task is not available, record why and what replaces it.

## Device Acceptance

Mandatory surfaces:

- [ ] Home idle.
- [ ] Home typing/results.
- [ ] App Drawer.
- [ ] Settings.
- [ ] Permission Education.
- [ ] Assistant.
- [ ] Routed SAFE proposal.
- [ ] Routed CONFIRM action.
- [ ] Learned Choices / Memory.
- [ ] Sacred Header.
- [ ] Prayer no-data/cached/verified states if DS-6B production lands.

Device matrix:

- [ ] primary Samsung device.
- [ ] dark/light.
- [ ] font-scale 1.0 and 2.0.
- [ ] offline/router-off smoke.
- [ ] TalkBack smoke for critical controls.
- [ ] no first-frame white flash/loading regression.

## Documentation Gates

- [ ] Completion ADR for each implemented DS block.
- [ ] `ai-context/current-status.md` updated.
- [ ] `docs/design/README.md` sequence updated.
- [ ] Artifact deviations documented.
- [ ] Known deferred items explicitly named.
- [ ] Agent start prompts updated for remaining work.

## Close Criteria

Design System v1.1 can be called adopted when:

- DS-3/DS-4/DS-5 production migrations are green;
- DS-6A is visually/accessibility accepted;
- DS-6B is either production-correct or explicitly deferred with no fake prayer data;
- DS-7 production memory migration is green;
- DS-10 Assistant migration is green;
- DS-8/DS-9 are either component-only accepted or backed by real A4/A5;
- full test and device gates are recorded.

## Stop Conditions

Do not declare DS v1.1 adopted if:

- any migrated surface requires fake data to match the artifact;
- `core/ui` imports domain/data;
- router parity breaks;
- privacy guard fails;
- prayer correctness is uncertain but times are shown;
- Activity/Execution/Agents UI ships without real engine backing.
