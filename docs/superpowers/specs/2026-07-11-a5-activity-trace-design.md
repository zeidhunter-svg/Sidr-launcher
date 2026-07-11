# A5 - Activity and Trace (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A5 turns real A4 traces into privacy-bounded activity records.
>
> **Prerequisite:** A4 `ExecutionTrace` must exist before production A5. DS-8 Activity components remain
> component/contract-only until A5 is scoped and approved.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, DS-8 Activity Foundations, current
> `IntentMatchHistoryRepository`, and the Stage 2 privacy posture.

## 1. Goal

Provide honest history from real events:

- real traces only;
- ephemeral by default;
- opt-in persistence;
- retention caps;
- redaction;
- clear/delete controls;
- no raw sensitive command/prompt/location by default.

A5 is not analytics. It is user-facing accountability.

## 2. Current Baseline

Existing persisted records:

- intent-match history with retention cap and redaction responsibilities;
- usage history with opt-in personalization path;
- learned resolution memory evidence.

Missing:

- A4 execution traces;
- trace projection policy;
- Activity retention/privacy settings;
- clear-all/per-row delete semantics for unified Activity;
- Activity destination.

## 3. Domain Concepts

```text
ActivityRecord
ActivityType
ActivityStatus
ActivitySource
ActivityProjectionPolicy
ActivityRedactionPolicy
ActivityRetentionPolicy
ActivityStore
ActivityClearance
```

Allowed record types begin narrow:

```text
ExecutionTraceEvent
ConsentEvent
ToolInvocationEvent
ResultEvent
MemoryEvent
PermissionEvent
```

Other sources require explicit approval.

## 4. Ephemeral Default

Default rule:

```text
trace exists for current/recent session inspection
persisted Activity is opt-in
```

Persistent Activity requires:

- explicit user setting;
- retention cap;
- clear all;
- redaction tests;
- source-by-source approval.

This mirrors SIDR's existing stance: history should help the user, not quietly profile them.

## 5. Redaction Rules

Activity records must never store/display by default:

- API keys;
- raw assistant prompts/replies;
- precise location;
- raw calendar titles/details;
- unredacted search queries when policy says search is sensitive;
- raw URLs with sensitive query strings;
- stack traces;
- internal database IDs;
- model raw chain-of-thought.

Allowed by default:

- category-level action;
- safe app label;
- tool id/name;
- status;
- timestamp;
- local/cloud/external provenance;
- safe failure category.

## 6. Projection From A4

A5 projects from `ExecutionTrace`:

```text
Trace event -> redaction policy -> ActivityRecord -> optional persistence -> DS-8 UI model
```

Rules:

- no fake Activity rows;
- no projection before trace event is recorded;
- partial result remains partial;
- consent denial/cancel is visible;
- cloud/tool provenance is visible.

## 7. Store and Retention

`ActivityStore` may be implemented only after privacy approval.

Required operations:

- observe records;
- delete one record;
- clear all;
- prune by retention;
- export only if separately scoped.

Persistence must be bounded by:

- max rows;
- max age;
- opt-in setting;
- per-source inclusion policy.

## 8. UI Relationship

DS-8 owns generic Activity visuals. A5 owns data truth.

Production Activity surface may be:

- Settings sub-surface first;
- later Activity destination only after IA decision.

No 5-tab IA resurrection without owner approval.

## 9. Verification

Required:

- trace -> record projection;
- redaction for sensitive payloads;
- ephemeral default;
- opt-in persistence;
- retention pruning;
- delete one;
- clear all;
- cancelled/denied/partial/failed/completed mapping;
- local/cloud/external provenance;
- no fake rows.

## 10. Non-goals

- No analytics.
- No engagement dashboard.
- No always-on hidden journal.
- No prompt history.
- No Activity tab by default.
- No storage of raw trace payloads for convenience.

## 11. Success Criteria

- Activity is derived from real A4 traces.
- The user can inspect and clear what is persisted.
- Sensitive content is redacted by default.
- DS-8 can render records without owning privacy logic.
