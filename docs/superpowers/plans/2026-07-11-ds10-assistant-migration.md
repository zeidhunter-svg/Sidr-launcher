# DS-10 - Assistant Migration Implementation Plan

> **STATUS: PROPOSED (2026-07-11).** DS-10 migrates the existing Assistant UI to SIDR v1.1 components while
> preserving streaming, cancellation, retry, and provider/BYOK behaviour.

**Goal:** Replace raw Material Assistant presentation with SIDR controls, typography, privacy, and error
patterns.

**Spec:** `docs/superpowers/specs/2026-07-11-ds10-assistant-migration-design.md`.

## Prerequisites

- DS-3 controls implemented and green.
- DS-5 privacy/error surfaces implemented and green.
- Existing Assistant tests green before edits.

## Global Constraints

- No commits unless the owner explicitly asks.
- No Assistant domain/data rewrite.
- No chat history persistence.
- No prompt/reply SavedStateHandle.
- No automatic memory/context injection.
- No tool execution or agent runtime.
- No API key display/logging.

## Task 1: Baseline Inventory

- [ ] Use CodeGraph to inspect:
      - `AssistantScreen`;
      - `AssistantViewModel`;
      - `AssistantUiState`;
      - provider config repository;
      - `SecureSecretStore` usage;
      - tests.
- [ ] Record current callbacks and status states.
- [ ] Run baseline:

```text
./gradlew :feature:assistant:testDebugUnitTest
```

Acceptance:

- no code edited;
- parity checklist captured.

## Task 2: Assistant Presentation Model and Layout

- [ ] Keep ViewModel state shape unless a presentation mapper is clearly needed.
- [ ] Split screen composition into smaller feature-local composables:
      - header;
      - reply/prose area;
      - status/provenance line;
      - composer;
      - provider panel.
- [ ] Use DS-3 rows/buttons/inputs where available.
- [ ] Keep initial prompt local `remember(initialPrompt)` behaviour.

Acceptance:

- no behaviour change;
- no prompt/reply persistence added.

## Task 3: Provider Privacy Panel

- [ ] Add `SidrPrivacyNotice` or equivalent DS-5 surface near provider/send context.
- [ ] Show provider base URL and model ID as provenance.
- [ ] State that prompts are sent to configured provider when Send is tapped.
- [ ] Keep API key masked and never echoed.
- [ ] Preserve provider setup/fix CTA.

Acceptance:

- cloud use is visible;
- API key still lives only in secure store.

## Task 4: Composer Migration

- [ ] Replace raw `OutlinedTextField`/`Button` styling with SIDR controls.
- [ ] Preserve IME Send.
- [ ] Preserve disabled send while streaming.
- [ ] Preserve latest-wins cancellation in ViewModel.
- [ ] Add clear content descriptions.

Acceptance:

- send works by button and IME;
- no send when prompt blank;
- no send while streaming.

## Task 5: Reply, Streaming, Error, and Refusal States

- [ ] Render reply prose in sans.
- [ ] Render provider/status metadata in mono.
- [ ] Replace raw error text with `SidrErrorSurface`.
- [ ] Retryable errors keep Retry.
- [ ] Missing credentials/unauthorized keep provider setup CTA.
- [ ] Refusal remains calm supporting text, not an error.
- [ ] Streaming indicator is quiet and accessible.

Acceptance:

- status mapping unchanged;
- tests cover retry/provider CTA/refusal.

## Task 6: Screenshot and Accessibility Coverage

Add or update tests/galleries for:

- provider setup;
- idle configured;
- streaming;
- completed;
- refusal;
- retryable error;
- provider CTA error;
- edit provider expanded;
- initial prompt prefilled;
- font-scale 2.0;
- dark/light;
- RTL smoke.

Acceptance:

- no text overlap;
- labels are clear;
- API key field remains password-like.

## Task 7: Device Acceptance

Manual/device smoke:

- [ ] first-run provider setup;
- [ ] owner enters key on-device only;
- [ ] real streaming response;
- [ ] retry/error path if feasible;
- [ ] edit provider;
- [ ] initial prompt from ASK route prefilled but not sent;
- [ ] force-stop proves prompt/reply not persisted.

## Full Verification Gate

```text
./gradlew :feature:assistant:testDebugUnitTest :core:ui:testDebugUnitTest testDebugUnitTest assembleDebug
```

Run `assembleRelease` only if DS-10 is being closed for release.

## Stop Conditions

Stop and re-scope if:

- migration requires ViewModel/domain/data rewrite;
- chat history or prompt memory enters scope;
- provider/API key handling changes;
- initial prompt starts auto-sending;
- Assistant starts receiving hidden context;
- DS-3/DS-5 components are not ready.

## Agent Start Prompt

```text
You are working in /home/Suleiman/Sidr-launcher. Do not commit.

Work on DS-10 Assistant Migration from:
- docs/superpowers/specs/2026-07-11-ds10-assistant-migration-design.md
- docs/superpowers/plans/2026-07-11-ds10-assistant-migration.md

Preserve streaming, latest-wins cancellation, retry, BYOK/Keystore, initial prompt prefill-only, and no
SavedStateHandle prompt/reply persistence. Use DS-3 controls and DS-5 privacy/error surfaces.

Forbidden: chat history, memory/context injection, tool execution, agent runtime, API key display/logging,
auto-send of initial prompt, commits.

Use CodeGraph before reading/editing code. Run relevant gates or report why they could not run.
```
