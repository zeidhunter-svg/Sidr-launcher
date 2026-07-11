# A2 - Context Engine v2 Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** A2 should be built feature-first and compatibly with current
> suggestions. It is a reduced context engine, not a raw data collector.

**Goal:** Introduce a shared, privacy-safe `ContextSnapshot` seam.

**Spec:** `docs/superpowers/specs/2026-07-11-a2-context-engine-design.md`.

## Prerequisites

- Current suggestion/context tests green.
- Privacy allow-list rules understood.
- A1 Tool layer may be in progress, but A2 can be developed independently.

## Global Constraints

- No commits unless the owner explicitly asks.
- No raw calendar/location/notification/contact/clipboard data in snapshot.
- No cloud context widening without explicit tests and review.
- No hidden background collection.
- No breakage of existing suggestions.

## Task 1: Baseline Inventory

- [ ] Use CodeGraph to inspect:
      - `SuggestionContext`;
      - `SuggestionProvider`;
      - suggestion providers;
      - `DeviceProfileProvider`;
      - `ConnectivityChecker`;
      - `OutboundContextPolicy`;
      - privacy guard tests.
- [ ] List current raw signal sources and reduced outputs.

Acceptance:

- no code edited;
- source/reduction table captured.

## Task 2: Domain Context Contracts

- [ ] Add `ContextSnapshot`.
- [ ] Add `ContextRequest`.
- [ ] Add `ContextSignal`/provider result type.
- [ ] Add `ContextEngine`.
- [ ] Add `ContextProvider` seam if at least two providers are ready.

Acceptance:

- pure domain;
- small interface;
- JVM tests.

## Task 3: Provider Adapters

- [ ] Add adapters for time/device/network first.
- [ ] Add usage/calendar/location only as reduced signals.
- [ ] Ensure provider failures degrade independently.
- [ ] Preserve permission gates.

Acceptance:

- denied calendar/location produces absent signal, not failure;
- raw title/coords unavailable outside provider.

## Task 4: Suggestion Compatibility Projection

- [ ] Project `ContextSnapshot` to current `SuggestionContext`.
- [ ] Keep existing suggestion engine behaviour.
- [ ] Migrate host construction gradually.

Acceptance:

- existing suggestion tests green;
- no duplicate raw Android reads remain after migration.

## Task 5: Outbound Projection

- [ ] Add context field inventory.
- [ ] Add `ContextOutboundPolicy`.
- [ ] Add destination-specific projections:
      - assistant;
      - router/planner;
      - future A4.
- [ ] Add planted-sensitive-value tests.

Acceptance:

- outbound projection excludes forbidden fields by default;
- any allowed field is deliberate and tested.

## Full Verification Gate

```text
./gradlew :domain:test :data:repository:testDebugUnitTest testDebugUnitTest assembleDebug
```

## Stop Conditions

Stop and re-scope if:

- consumers need raw values;
- outbound context would include private fields;
- provider failures break the whole snapshot;
- suggestions regress;
- background collection enters scope.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on A2 Context Engine v2 from:
- docs/superpowers/specs/2026-07-11-a2-context-engine-design.md
- docs/superpowers/plans/2026-07-11-a2-context-engine.md

Build a pure-domain ContextSnapshot/ContextEngine seam over reduced signals. Preserve current suggestions.
No raw calendar titles, coordinates, contacts, clipboard, notification text, prompt history, or installed-app
list in the snapshot or outbound projection.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
