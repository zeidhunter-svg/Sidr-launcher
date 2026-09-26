# A2 - Context Engine v2 (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A2 turns scattered, permission-gated signals into one reduced,
> privacy-safe context snapshot for routing, suggestions, assistant handoff, and future planning.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, ADR "2026-07-10 - Agentic OS target
> architecture (A1-A6)", current `SuggestionProvider` / `SuggestionContext`, `DeviceProfileProvider`,
> `ConnectivityChecker`, and `OutboundContextPolicy`.

## 1. Goal

Create a deep Context Engine module:

- gather signals through fault-isolated providers;
- reduce raw data into safe categories;
- expose immutable `ContextSnapshot`;
- enforce permission degradation per provider;
- define exactly which context fields may leave the device.

A2 is the agent's senses, not a data lake.

## 2. Current Baseline

Existing seeds:

- `SuggestionContext` contains safe time bucket, now timestamp, and typed prefix.
- `SuggestionProvider`s are already fault-tolerant and permission-gated.
- Calendar/location suggestion providers emit generic labels only and do not expose raw titles/coords.
- `DeviceProfileProvider` exposes device profile/capability.
- `ConnectivityChecker` gates cloud router use.
- `OutboundContextPolicy` is a positive allow-list for outbound AI context.

Current limitations:

- context exists only inside suggestions/router pieces;
- no shared `ContextSnapshot`;
- providers produce suggestions, not reusable reduced signals;
- outbound policy does not yet classify context snapshot fields;
- consumers still assemble context ad hoc.

## 3. Deep Module Shape

Small external interface:

```kotlin
interface ContextEngine {
    suspend fun snapshot(request: ContextRequest): ContextSnapshot
}
```

Optional observable interface may come later, but do not start with a broad stream API unless a consumer
requires it.

Complexity hidden behind the interface:

- provider fan-out;
- permission checks;
- timeout/failure isolation;
- raw-to-reduced mapping;
- outbound projection;
- test clocks/device/connectivity adapters.

## 4. Core Concepts

```text
ContextSnapshot
ContextRequest
ContextProvider
ContextSignal
ContextFreshness
ContextPermissionState
ContextReductionPolicy
ContextOutboundPolicy
ContextSource
```

Potential snapshot fields:

```text
timeOfDay
now
typedPrefixClass
network
power
thermal
deviceClass
voiceAvailable
usageClass
recentActionClass
calendarWindow
placeClass
prayerWindow
```

Fields must be reduced. Raw values stay inside providers.

## 5. Provider Rules

Every provider must:

- return reduced context only;
- never throw expected failures;
- degrade independently;
- honour permission denial;
- avoid logging raw sensitive data;
- expose freshness/source when meaningful.

Forbidden in snapshot:

- raw calendar titles;
- precise coordinates;
- contacts;
- SMS/email;
- clipboard;
- raw notification text;
- raw prompt/reply history;
- full installed-app list;
- API keys or provider credentials.

## 6. Outbound Policy

A2 extends `OutboundContextPolicy` from category-level allow-list to field-level context projection.

Suggested interface:

```kotlin
interface ContextOutboundPolicy {
    fun project(snapshot: ContextSnapshot, destination: OutboundDestination): OutboundContextProjection
}
```

Rules:

- default projection is empty/minimal;
- every new outbound field is a privacy decision;
- cloud planner may receive only reduced allow-listed fields;
- assistant generation remains minimal unless explicitly widened;
- tests plant sensitive values and prove they do not leave.

## 7. Relationship to Suggestions

A2 should not break current suggestions.

Migration path:

```text
current SuggestionContext -> ContextSnapshot projection -> SuggestionContext compatibility adapter
```

Suggestion providers may remain as-is while context providers are introduced. Do not duplicate raw Android
reads across two systems long term.

## 8. Relationship to A4

A4 `Planner` receives a `ContextSnapshot` or an outbound-projected subset.

Rules:

- A4 never reads Android APIs directly;
- A4 never gets raw provider data;
- denied permission removes only that signal;
- snapshot includes enough provenance/freshness for planner and UI disclosure.

## 9. Verification

Required:

- provider failure does not fail whole snapshot;
- permission denied removes only related signal;
- calendar raw title never appears;
- location coordinates never appear;
- typed prefix handling is explicit and transient;
- device/network/power fields reduce correctly;
- outbound projection excludes forbidden fields;
- existing suggestion tests remain green;
- no consumer reads Android APIs directly once migrated.

## 10. Non-goals

- No user memory.
- No Activity journal.
- No world model.
- No cloud context upload by default.
- No raw installed-app classifier payload to cloud.
- No hidden background collection.

## 11. Success Criteria

- Consumers can request one safe `ContextSnapshot`.
- Existing suggestions can consume context through a compatibility projection.
- A4 has a privacy-bounded context input.
- Outbound privacy guards prove raw sensitive signals do not leave the device.
