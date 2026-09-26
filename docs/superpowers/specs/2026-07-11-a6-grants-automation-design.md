# A6 - Grants and Automation (Architecture Spec)

> **Status: PROPOSED (2026-07-11).** A6 is Stage 3: controlled automation through explicit grants,
> revocable limits, audit, and optional accessibility-tier capabilities.
>
> **Prerequisites:** A1 tools, A4 runtime, and A5 activity/audit traces must exist before production A6.
> Agents/Automation UI remains forbidden until these backing contracts are real.
>
> **Governing sources:** `docs/agentic-os-architecture.md`, Stage 3 roadmap, permission-education module,
> DS-5 action/safety, DS-8/DS-9 future surface constraints.

## 1. Goal

Allow SIDR to automate only within explicit, user-owned boundaries:

- which agent/automation is allowed;
- which tools it may use;
- under what limits;
- during which quiet hours;
- with which consent policy;
- how to revoke it;
- how to audit what happened.

A6 is not "let AI control the phone". It is a grant system for constrained, inspectable automation.

## 2. Current Baseline

Existing seeds:

- `PermissionFeature` education/request flow for feature-specific Android permissions;
- feature flags for opt-in behaviours;
- AIL-5 confirmation gates for routed actions;
- planned A1 tool tiers;
- planned A4 runtime consent loop;
- planned A5 activity/audit trace.

Not present:

- grant model;
- agent identity model;
- automation policy;
- quiet hours;
- accessibility tier flow;
- audit log;
- Agents/Automation navigation.

## 3. Domain Concepts

```text
AgentId
AutomationId
Grant
GrantScope
GrantLimit
GrantStatus
AutomationPolicy
QuietHours
AuditLog
RevocationReason
ToolTierGrant
```

Tool tiers:

```text
IN_APP
SYSTEM_INTENT
ACCESSIBILITY
```

Accessibility tier is opt-in only and never required for normal launcher use.

## 4. Grant Model

A `Grant` must define:

- subject: agent/automation id;
- allowed tools;
- allowed tool tiers;
- max frequency;
- max runtime/cost;
- time windows/quiet hours;
- data boundaries;
- consent override rules, if any;
- expiration;
- revocation state;
- audit visibility.

No grant can allow:

- bypassing `DANGEROUS` confirmation without explicit separate policy;
- hidden accessibility control;
- hidden cloud egress;
- background automation with no audit;
- unrestricted tool access.

## 5. Automation Policy

Automation runs only when:

- grant is active;
- runtime limits allow it;
- quiet hours allow it or user explicitly overrides;
- required Android permission is granted;
- tool preconditions pass;
- consent policy permits the step;
- A4 runtime can trace it.

Failure degrades to blocked/needs-consent, not silent action.

## 6. Accessibility Tier

Accessibility tier requires its own education flow and must not be added to existing `PermissionFeature`
casually.

Required:

- separate user-initiated setup;
- clear statement of capabilities and risks;
- system settings handoff;
- revocation detection;
- disable path;
- per-tool limits;
- audit visibility;
- no requirement for normal launcher operation.

## 7. Audit

A6 depends on A5. Every consequential automated action must be audit-visible:

- what ran;
- why it was allowed;
- which grant allowed it;
- tool tier;
- local/cloud/external boundary;
- result;
- whether user consent was requested.

Audit can be ephemeral or persisted according to A5 policy, but automation grants require at least recent
inspectability.

## 8. UI Relationship

Agents/Automation surfaces are allowed only after:

- A4 runtime;
- A5 audit;
- A6 grant model;
- at least one real agent/automation capability.

Before then, artifact Agent/Automation cards remain future-contract references.

No 5-tab IA without owner approval.

## 9. Verification

Required:

- grant create/update/revoke;
- expired grant blocks automation;
- quiet hours block automation;
- tool outside grant blocked;
- tier escalation requires consent/new grant;
- accessibility denial degrades safely;
- automation disable-all works;
- audit records every consequential action;
- revoked grant prevents future steps;
- no hidden cloud egress;
- no hidden background loop.

## 10. Non-goals

- No Accessibility Service implementation in this spec.
- No automation editor UI before grants exist.
- No unrestricted agents.
- No background autonomous task runner.
- No bypass of DS-5/A4 consent checkpoints.

## 11. Success Criteria

- Automation is grant-bounded, revocable, auditable, and optional.
- Accessibility tier is explicit and separate.
- Agents/Automation UI can be backed by real grants.
- Normal launcher behaviour remains independent of automation.
