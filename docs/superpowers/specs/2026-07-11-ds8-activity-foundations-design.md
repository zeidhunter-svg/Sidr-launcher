# DS-8 - Activity Foundations (Design Spec)

> **Status: PROPOSED (2026-07-11).** DS-8 prepares honest activity/history presentation patterns.
>
> **Important:** DS-8 must not create new persistence merely to populate a screen. Production Activity UI is
> allowed only for real, privacy-reviewed records. Component/gallery work may land earlier as preview-only.
>
> **Governing sources:** `docs/design/SIDR Design Migration Plan v1.1.md` DS-8,
> `docs/design/SIDR Design System Master Plan.md` Conditional Future Blocks,
> `docs/design/SIDR Design System v1.md` Activity screen direction, and current history/memory repositories.

## 1. Goal

Prepare a visual and semantic language for SIDR activity:

- what happened;
- when it happened;
- whether it stayed local;
- whether it required confirmation;
- whether it completed, failed, or partially completed;
- what the user can safely inspect or clear.

DS-8 is about **truthful audit presentation**, not engagement history or decorative logs.

## 2. Current Production Baseline

Real records that exist today:

- `IntentMatchHistoryRepository` stores intent-match records with retention cap.
- `UsageHistoryRepository` stores app launch usage records with retention cleanup.
- S2-1 learned resolutions store preference evidence.
- AIL-4/AIL-5 routed proposals and confirmations exist as runtime state, but there is no durable execution
  trace model.

Important implications:

- command categories may be derivable from existing redacted intent history;
- app launch usage may be shown only if its privacy meaning is clear;
- learned preference events may be shown only if mapped from real memory state;
- confirmation/proposal/result activity should not be fabricated from transient UI state;
- raw sensitive command text remains excluded by default.

## 3. Scope

Allowed first step:

- `core/ui` preview components for activity items/timeline.
- Feature-local presentation model sketches.
- Inventory of real existing records that could safely feed Activity.

Conditional production:

- Settings sub-surface or Activity destination only after an approved Activity domain/use-case plan exists.

Forbidden:

- new Room table just to fill Activity;
- fake "today" feed from mock events;
- raw sensitive command log;
- automatic persistent activity journal without owner-approved retention/privacy policy;
- Agents/Automation/Execution Stream activity before A4/A5 backing records.

## 4. Public Component Family

`core/ui` components accept display-safe presentation values only.

```kotlin
enum class SidrActivityType {
    Command,
    AppLaunch,
    WebAction,
    Confirmation,
    Permission,
    Memory,
    Assistant,
    Error,
}

enum class SidrActivityStatus {
    Completed,
    Partial,
    Failed,
    Cancelled,
    Blocked,
    Learned,
    LocalOnly,
}

@Composable
fun SidrActivityItem(
    title: String,
    type: SidrActivityType,
    status: SidrActivityStatus,
    timestamp: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    provenance: String? = null,
    localOnly: Boolean = true,
    onOpen: (() -> Unit)? = null,
)

@Composable
fun SidrActivityTimeline(
    items: List<SidrActivityItemUi>,
    modifier: Modifier = Modifier,
    emptyContent: (@Composable (() -> Unit))? = null,
)
```

Suggested UI model for preview/components:

```kotlin
data class SidrActivityItemUi(
    val id: String,
    val title: String,
    val type: SidrActivityType,
    val status: SidrActivityStatus,
    val timestamp: String,
    val details: String? = null,
    val provenance: String? = null,
    val localOnly: Boolean = true,
)
```

Rules:

- status is label + marker, not colour alone;
- timestamp is user-readable;
- provenance is shown when the event affects privacy, memory, cloud, or external handoff;
- local-only is visible when meaningful;
- rows wrap at font-scale 2.0;
- no raw database IDs or raw command payloads.

## 5. Activity Data Rules

Every production activity item must answer:

- what happened;
- when;
- source/provenance;
- local/cloud/external boundary if relevant;
- retention/delete affordance if persisted;
- whether it is safe to show the title/details.

Do not show:

- API keys;
- raw prompts by default;
- precise location;
- raw calendar titles;
- raw URL query strings if privacy policy says redacted;
- internal IDs;
- implementation confidence;
- stack traces.

## 6. Retention and Privacy

Before production Activity:

- define ephemeral vs persisted default;
- define retention cap;
- define clear-all behaviour;
- define per-row deletion, if supported;
- define redaction rules by activity type;
- define export policy, if any;
- define opt-in requirement for persistent history beyond current repositories.

Default posture:

- activity is ephemeral unless a current repository already persists the source;
- persistent Activity expansion requires explicit owner approval.

## 7. Existing Source Mapping

Potential mappings:

| Source | Allowed? | Notes |
| --- | --- | --- |
| Intent match history | Conditional | Must respect existing redaction and category-only display. |
| Usage history | Conditional | Show as launcher usage only if user opted into personalization/history. |
| Learned preferences | Yes for memory surface | DS-7 owns memory list; Activity may later show learned events. |
| Routed proposal runtime state | No durable Activity yet | Needs A4/A5 trace model before Activity persistence. |
| Confirmation runtime state | No durable Activity yet | Do not backfill from UI-only state. |
| Assistant replies/prompts | No by default | Assistant is transient; no SavedStateHandle. |

## 8. Accessibility

Required:

- timeline order announced clearly;
- row summary includes title, status, and time;
- local/cloud/external boundary is announced;
- clear/delete actions have specific labels;
- long titles wrap;
- no icon-only meaning;
- font-scale 2.0 and RTL smoke.

## 9. Verification

Component-only gate:

- command event;
- memory event;
- permission event;
- failed event;
- local-only event;
- cloud/external event preview;
- empty timeline;
- long title;
- dark/light;
- font-scale 2.0;
- RTL smoke;
- no `core/ui` domain/data imports.

Production gate, when scoped:

- no new persistence without privacy decision;
- records are real;
- redaction tests;
- retention tests;
- clear/delete tests if implemented;
- no raw sensitive text by default.

## 10. Non-goals

- No Activity destination before domain/use-case scope.
- No fake trace feed.
- No Agents/Automation feed.
- No Execution Stream.
- No persistent prompt journal.
- No hidden analytics.

## 11. Success Criteria

- SIDR has reusable activity presentation components.
- Production Activity remains blocked until real, privacy-bounded records exist.
- Existing history repositories are inventoried before use.
- The user never sees fake or over-specific activity data.
