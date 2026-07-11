# DS-10 - Assistant Migration (Design Spec)

> **Status: PROPOSED (2026-07-11).** DS-10 migrates the existing Assistant surface to the soft-classic-grey
> component language while preserving the current streaming/BYOK behaviour.
>
> **Governing sources:** `docs/design/SIDR Design Migration Plan v1.1.md` DS-10, current
> `feature/assistant` implementation, DS-3 controls, and DS-5 action/safety/privacy surfaces.

## 1. Goal

Bring Assistant into the SIDR v1.1 visual language:

- prose answers use Interface Sans;
- provider/provenance metadata uses Mono;
- cloud/provider disclosure uses DS-5 privacy language;
- errors use DS-5 error surfaces;
- buttons/rows/inputs use DS-3 controls;
- provider form remains clear and privacy-safe.

DS-10 is a **presentation migration**. It must preserve assistant runtime behaviour.

## 2. Current Production Baseline

Current `AssistantScreen`:

- receives `AssistantViewModel`;
- supports one-shot `initialPrompt` prefill;
- never auto-sends the initial prompt;
- shows provider setup when base URL is blank;
- streams reply text through `AssistantStatus.Streaming`;
- supports retry through `AssistantViewModel.retry`;
- exposes provider settings inline;
- keeps API key out of `ProviderFormState`;
- has no SavedStateHandle for prompt/reply persistence.

Current `AssistantViewModel`:

- cancels previous stream when sending a new prompt;
- collects `GenerateReplyUseCase.generate(prompt)`;
- appends `AiChunk.Text`;
- marks `Done(refused = true)` on refusal;
- maps `AiChunk.Failed` to retry/provider CTA state;
- stores API keys in `SecureSecretStore`.

## 3. Behaviour Parity

Must preserve:

- streaming;
- latest-wins cancellation;
- retry;
- provider setup/edit;
- BYOK Keystore behaviour;
- transient prompt/reply;
- initial prompt prefill only;
- no auto-send;
- no SavedStateHandle for prompt/reply;
- missing credentials/provider CTA behaviour;
- offline/network error mapping;
- refusal indication.

No Assistant/domain/data rewrite is required for DS-10.

## 4. Public/Feature Surface

DS-10 may introduce feature-local presentation components, or reuse `core/ui` components directly:

```text
AssistantShell
AssistantMessage
AssistantComposer
AssistantProviderPanel
AssistantStatusLine
```

These may live in `feature/assistant` if they depend on Assistant state. Generic controls remain in
`core/ui`.

## 5. Visual Rules

- Reply text is sans prose.
- Provider metadata is mono provenance.
- Streaming state is quiet and does not dominate the page.
- Provider form is not a modal unless explicitly scoped.
- API key field remains masked and never echoed.
- Error copy is safe and non-technical.
- Cloud disclosure is visible near provider/send context.
- No card-inside-card layouts.
- No fake local/offline assistant claim when cloud provider is active.

## 6. Privacy Rules

Assistant must make clear:

- configured provider/base URL;
- model ID;
- cloud use when sending;
- API key is stored in Keystore and not displayed;
- prompt text is sent to the configured provider when user taps Send.

Do not add:

- chat history persistence;
- prompt memory;
- automatic context injection;
- location/calendar/memory context;
- provider analytics.

## 7. Error and Result Rules

Use DS-5 surfaces:

- retryable network/server/timeout -> retry action;
- missing credentials/unauthorized -> provider setup action;
- refusal -> calm note, not a scary error;
- invalid provider config -> form validation;
- streaming cancellation -> no error unless user-visible cancellation is explicitly scoped.

## 8. Accessibility

Required:

- back action labelled;
- send disabled state announced;
- streaming state announced without noisy repetition;
- provider form labels clear;
- API key field uses password semantics;
- retry/provider CTA labels specific;
- font-scale 2.0 usable;
- keyboard IME send works.

## 9. Verification

Required:

- first-run provider setup;
- configured idle;
- initial prompt prefilled but not auto-sent;
- send starts streaming;
- text chunks append;
- retry works;
- missing credentials/provider CTA;
- network retryable error;
- refusal note;
- edit provider open/close;
- masked key state;
- font-scale 2.0;
- dark/light;
- no prompt/reply persistence added.

Suggested gate:

```text
./gradlew :feature:assistant:testDebugUnitTest :core:ui:testDebugUnitTest testDebugUnitTest assembleDebug
```

Device acceptance should include real provider smoke when the owner supplies a key on-device.

## 10. Non-goals

- No assistant memory.
- No chat history.
- No context engine injection.
- No tool execution.
- No agent runtime.
- No model recommendation flow.
- No provider marketplace.

## 11. Success Criteria

- Assistant visually matches SIDR v1.1.
- Streaming and BYOK behaviour remain unchanged.
- Cloud/provider provenance is clearer.
- Errors are safer and more useful.
- No new persistence or privacy exposure is introduced.
