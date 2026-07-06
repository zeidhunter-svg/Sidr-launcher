# Design Spec — Learned Resolutions (Stage 2, block S2-1)

> **Status:** DESIGN — approved section-by-section with the owner (2026-07-06). **Design-only:** no
> production code, no implementation plan, no Room schema change, no use-case wiring yet. The
> implementation plan (`writing-plans`) is explicitly deferred until the owner approves this document.
>
> **Stage:** 2 — AI Framework. This is its **first atomic vertical slice** (block **S2-1**), chosen
> feature-first: build one narrow, complete user capability and grow only the minimal memory/context
> abstractions it actually needs — do **not** build a universal User Memory / Context Engine up front.

## 1. Motivation & the one capability

The launcher already resolves commands rule-first → (optional) LLM router → confirmation (Stage 1,
AIL-0…6). When a launch command is **ambiguous** (e.g. `open bank` matches several apps) it shows a
candidate list and the user taps the one they meant. Today that choice is forgotten every time.

**Capability (the whole slice):**

```
ambiguous command → candidate targets → explicit user choice → local persistence →
learned preference → deterministic reuse on subsequent equivalent commands →
user-visible correction / reset path
```

Concretely: the launcher **learns which app you meant** for an ambiguous launch query and prefers it
next time — first by ranking it first, then (after enough consistent evidence) by resolving it
directly — always correctable, entirely on-device.

### 1.1 Conceptual model (the important architectural decision)

The memory is **not** `normalized command → target` (too primitive; a dead end). It is:

```
(Capability/Intent, CandidateSet awareness, ResolutionContext) → PreferredTarget + Evidence
```

Read: *"when the user wants capability X among available targets Y, they usually prefer target Z."*
Even though the first implementation is minimal (Context is degenerate, Target is app-only), the
contract is shaped so it can grow into the future User Memory → Context Engine → World Model without
breaking callers.

### 1.2 Owner decisions locked during design

- **Driver:** feature-first (a real capability pulls the framework forward), not an abstract refactor.
- **Feature:** context-aware routing → narrowed to **learned on-device resolutions** (no LLM, no cloud).
- **Reuse strength:** **threshold auto-resolve** — rank-first until `K` consistent choices, then
  auto-resolve; deterministic, self-correcting, not an eternal rule.
- **Threshold:** `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD = 3`, param `autoResolveStreakThreshold`.
- **Correction:** **hard-switch** in v1 (`chosen != preferred` → `preferred = chosen, streak = 1,
  totalChoices++, fingerprint = current`). Per-target evidence is a future evolution, not v1.
- **Integration:** Approach A — deterministic policy in `domain`, data-impl separate, applied **only on
  the ambiguous branch**, recorded **only after a successful explicit choice**.
- **Package:** `domain/memory/resolution/` (a narrow first inhabitant of the memory layer).

## 2. Architectural invariants (must hold)

1. **No preference ⇒ prior behavior, byte-for-byte** (parity).
2. **Non-ambiguous commands are never touched.**
3. **UI/VM hold no business logic** — the VM shuttles an opaque token; all decisions live in domain.
4. **Auto-resolve only for SAFE actions.** CONFIRM/RISKY never inherit auto-resolve, even at CONFIDENT.
5. **Correction / delete-to-relearn / invalidation are mandatory in v1; explicit reset is backlog.**
6. **Everything deterministic and on-device.** No LLM, no cloud, no network on this path.
7. **`HandleUserCommandUseCase` contract is not broken** — preference is an additive layer over its
   ambiguous outcome.
8. **Confidence lives in the policy, not storage.** Storage holds only raw deterministic evidence.
9. **Policy is pure (no side effects).** Prune/execute/persist are the caller's responsibility.
10. **The outbound privacy allow-list is unchanged.** Preferences never enter an `AiRequest`.

## 3. Domain model & types (`domain/memory/resolution/`)

```kotlin
// WHAT the user is trying to do (capability/intent), independent of which target.
// v1 = (action family + normalized slot query); shaped to carry a richer capability later
// (e.g. a semantic cluster once embeddings, OQ#3, exist) without changing the contract.
data class CapabilityKey(
    val actionId: ActionId,   // e.g. ActionIds.LAUNCH_APP
    val query: String,        // normalized slot, e.g. "bank" (reuses CommandNormalizer / slot-strip)
)

// WHERE/WHEN — situational context. v1 degenerate; grows (time/place) without signature churn.
sealed interface ResolutionContext { data object None : ResolutionContext }   // v1

// The chosen thing. v1 = app only; sealed so it generalizes (url/action/…) without breaking ports.
sealed interface ResolvedTarget { data class App(val packageName: String) : ResolvedTarget }

// The set of targets offered — for validation + change-awareness, NOT identity.
data class CandidateSet(val targets: List<ResolvedTarget>)
@JvmInline value class CandidateSetFingerprint(val value: String)  // deterministic hash of target ids

// Deterministic evidence the POLICY interprets — its own type so confidence can evolve beyond
// "streak" without changing the store/record contract.
data class PreferenceEvidence(
    val streak: Int,             // consecutive consistent explicit choices (v1 signal)
    val totalChoices: Int,
    val lastChosenAtEpochMs: Long,
)

// The stored record: (Capability, Context) → PreferredTarget + evidence + the set it was learned in.
data class ResolutionPreference(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val preferredTarget: ResolvedTarget,
    val evidence: PreferenceEvidence,
    val learnedInSetFingerprint: CandidateSetFingerprint,   // candidate-set change awareness
)

// Opaque, TRANSIENT (never persisted) token that the ambiguous outcome carries so the VM can hand it
// back on a candidate tap WITHOUT building a CapabilityKey itself (keeps business logic out of the UI).
data class ResolutionLearningToken(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val candidateSet: CandidateSet,
    val fingerprint: CandidateSetFingerprint,
    val isAppAmbiguityFlow: Boolean,   // true only for the in-scope path; the write guard checks this
)

interface ResolutionPreferenceStore {
    suspend fun find(key: CapabilityKey, context: ResolutionContext): OperationResult<ResolutionPreference?>
    suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit>
    suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit>
    fun observeAll(): Flow<List<ResolutionPreference>>
}
```

**Model decisions:** risk is **not** stored (it is a property of the action at decision time, from
`ActionCatalog`); confidence is **not** stored (only raw evidence); `preferredTarget` is always
validated against the live `CandidateSet`; the query is a normalized slot, so identity is
`(action family + slot)` and the record is richer than a bare string→target pair (it binds
candidate-set fingerprint + context + evidence, and the target is a validated `ResolvedTarget`).

## 4. Policy, threshold & confidence (`ResolutionPreferencePolicy`)

Pure, total, side-effect-free (precedent: `DefaultIntentConfidencePolicy`). It **classifies**; the
caller acts.

```kotlin
sealed interface ResolutionDecision {
    data object NoPreference : ResolutionDecision                                 // nothing stored → parity
    data class  Stale(val preference: ResolutionPreference) : ResolutionDecision  // stored but target gone → prune + parity
    data class  RankFirst(val target: ResolvedTarget) : ResolutionDecision        // reorder list, still confirm
    data class  AutoResolve(val target: ResolvedTarget) : ResolutionDecision      // resolve directly (skip list)
}

interface ResolutionPreferencePolicy {
    fun decide(preference: ResolutionPreference?, candidates: CandidateSet, risk: ActionRiskLevel): ResolutionDecision
}
```

**Deterministic decision table (fixed check order):**

| Condition | Decision |
|---|---|
| `preference == null` | `NoPreference` |
| `preference.preferredTarget ∉ candidates` | `Stale(preference)` — caller best-effort `delete`, then parity |
| `strength(evidence)=CONFIDENT` ∧ `risk == SAFE` ∧ `fingerprint(candidates) == learnedInSetFingerprint` | `AutoResolve(target)` |
| otherwise (preference valid but not eligible) | `RankFirst(target)` |

**Confidence seam (single evolution point; not a bare `K`-counter architecturally):**

```kotlin
class DefaultResolutionPreferencePolicy(
    private val autoResolveStreakThreshold: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,   // = 3
) : ResolutionPreferencePolicy {
    private fun strength(e: PreferenceEvidence): PreferenceStrength =
        if (e.streak >= autoResolveStreakThreshold) PreferenceStrength.CONFIDENT else PreferenceStrength.WEAK
    /* ... decision table ... */
}
enum class PreferenceStrength { WEAK, CONFIDENT }
```

Future confidence models (recency-decay, `streak/total` ratio, contradiction penalty, time-of-day)
change **only** `strength(...)` and may enrich `PreferenceEvidence`; the public `ResolutionDecision`,
the port, and the record stay stable. A changed candidate set (fingerprint mismatch) demotes a
CONFIDENT preference to `RankFirst` — it does **not** erase the learned preference; a later reconfirm
in the new set updates the fingerprint and re-enables auto-resolve.

## 5. Integration points & parity

### 5.1 Read path

Preference is consulted on the **rule-produced ambiguity, before any LLM consultation** (so a learned
choice resolves/reorders locally, never involving the cloud). A domain use-case (e.g.
`ResolveWithPreferenceUseCase`) runs **only** when the rule outcome is an ambiguous app-candidate list:

1. Build `CapabilityKey(LAUNCH_APP, normalizedQuery)` + `CandidateSet` from the outcome.
2. `risk` = the action's risk from `ActionCatalog` (LAUNCH_APP = SAFE).
3. `pref = store.find(key, None)` → `decision = policy.decide(pref, candidates, risk)`.
4. Map the decision:

| Decision | Use-case action | Outcome |
|---|---|---|
| `NoPreference` | none | **prior** candidate list (parity) |
| `Stale(p)` | best-effort `delete` (invalidation) | **prior** candidate list (parity) |
| `RankFirst(t)` | reorder candidates, `t` first | same list, `t` on top |
| `AutoResolve(t)` | execute via the **unchanged** `IntentActionResolver` + `ActionExecutor` | `Launched`; on executor failure → reordered candidate list (deterministic fallback, never a dead end) |

`AutoResolve` never touches evidence (it is not an explicit human choice).

The ambiguous outcome also carries a **transient `ResolutionLearningToken`** so the VM can record a
later choice without building the key itself.

### 5.2 Write path

Learning trigger = **only** an explicit candidate tap in the ambiguous flow (never a grid tap, never a
single-target/non-ambiguous launch, never search/web/assistant). After a **successful** launch of the
chosen candidate, the VM calls (fire-and-forget, precedent `recordMatch`/`recordUsage`;
`CancellationException` rethrown):

```kotlin
class RecordResolutionChoiceUseCase(private val store: ResolutionPreferenceStore) {
    suspend fun record(key: CapabilityKey, context: ResolutionContext,
                       chosen: ResolvedTarget, candidates: CandidateSet): OperationResult<Unit>
}
```

Deterministic evidence update (math in domain, not VM):

| Situation | Update |
|---|---|
| no preference | create: `preferred=chosen, streak=1, total=1, fingerprint=current` |
| exists, `chosen == preferred` (reinforce) | `streak++, total++, lastChosenAt=now`, `fingerprint=current` |
| exists, `chosen != preferred` (correction, **hard-switch**) | `preferred=chosen, streak=1, total++`, `fingerprint=current` |

If the tap's launch **fails**, evidence is **not** updated (learn only on a successful explicit choice).

### 5.3 Parity boundary (precise)

- Non-ambiguous commands, `NoPreference`, `Stale` → **byte-for-byte prior behavior**.
- Only "ambiguous + valid preference" changes: reorder (`RankFirst`) or direct launch (`AutoResolve`,
  SAFE + CONFIDENT + set-consistent only).
- `HandleUserCommandUseCase` contract untouched; the exact seam (decorator over the rule outcome vs a
  hook in its ambiguity branch) is finalized in the implementation plan and guarded by parity tests.

## 6. Persistence (Room, `:data:repository`)

New entity in the existing `SidrDatabase` (precedent: `AppUsageRecord`/`IntentMatchRecord`).

```
table resolution_preferences
  PK = (action_id, query, context_key)          // context_key v1 = "none"  (Capability + Context)
  preferred_target_type  TEXT                    // "app"  (sealed ResolvedTarget discriminator)
  preferred_target_value TEXT                    // packageName
  streak            INTEGER
  total_choices     INTEGER
  last_chosen_at    INTEGER                      // epoch ms
  learned_in_fingerprint TEXT                    // technical fingerprint (NOT anonymization)
```

- **DAO:** `findByKey`, `upsert` (insert-or-replace), `deleteByKey`, `deleteByTargetValue(pkg)` (kept
  even though eager PACKAGE_REMOVED prune is backlog — used by manual cleanup + display invalidation +
  future eager prune), `observeAll(): Flow`, `count`, `deleteOldestBeyond(cap)`.
- **Mapper:** entity ↔ `ResolutionPreference` (target type/value; evidence as-is).
- **`ResolutionPreferenceStore` impl:** returns `OperationResult`, **never throws** (rethrows
  `CancellationException`). `find` I/O error → the read path treats any non-`Success(pref)` as
  no-preference (deterministic parity degrade). `upsert`/`delete` are best-effort (fire-and-forget);
  a failure just means "not learned this time." `observeAll` → `Flow` for the management screen.
- **Invalidation (v1):** lazy on read (`policy → Stale` ⇒ best-effort `deleteByKey`) + display-time
  validation on the management screen. Eager `PACKAGE_REMOVED` prune is **backlog**.
- **Retention:** `MAX_RESOLUTION_PREFERENCES = 500`, LRU eviction by `last_chosen_at` (safety net; in
  practice rarely triggers — preferences are user-meaningful, not raw history).
- **Migration:** bump `SidrDatabase` version, golden `schemas/N.json`, `MigrationTestHelper` test.
  Table + columns added to the hand-synced `PrivacyInventoryGuardTest` inventory.

## 7. Privacy & on-device boundaries

**Classification:** `query` (`"bank"` can reveal intent) and `preferred_target_value`/packageName
(reveals a specific app) are **local-sensitive metadata** — together they form a user preference
record. Storing them is acceptable **only** because the capability is strictly on-device, never
transmitted, never logged, never in crash/external logs, user-visible and deletable, and scoped to
app-launch disambiguation. `learned_in_fingerprint` is a **technical** fingerprint for candidate-set
change detection — **not** an anonymization mechanism; nothing relies on it for privacy.

**Boundaries:**
- Nothing leaves the device; no network/cloud/LLM on this path.
- The outbound allow-list (`OutboundContextPolicy`) is widened by **zero**; preferences never enter an
  `AiRequest`; the memory package has no edge to the generative/outbound path.
- Only app-launch disambiguation uses this store; search/web/assistant/free-text do not (the existing
  SEARCH redaction of history is unchanged).
- No raw command is stored (`open my bank app` → only `bank`).

**`query` constraints (hard):** `trim` + `lowercase` + `CommandNormalizer`; `MAX_QUERY_LENGTH = 64`
(longer → not stored); empty query → no record; source is only the app-ambiguity flow (enforced by the
`ResolutionLearningToken.isAppAmbiguityFlow` flag on the write path).

**Logging:** `action_id`, target type, error category are allowed; `query` and packageName must never
reach external crash/log channels. Internal Room/mapper errors log category only.

**Executable guards:**
- `PrivacyInventoryGuardTest` extended to the table + columns.
- Outbound allow-list unchanged, asserted byte-for-byte; grep/dependency guard that
  `domain/memory/resolution` has no reference to `AiRequest`/the generative path.
- Scope guard: recording is only reachable from the app-ambiguity path; `query` constraints
  (non-empty, ≤ cap) enforced and tested.

## 8. Management UI — Learned Choices (`:feature:settings`)

Sub-screen `Routes.LearnedChoices` (in `core/common`; single `NavHost`; entered via `NavigationEvent`;
no `feature→feature` edge). Reached from Settings via a `[ learned choices ]` row.

**Shows** terminal-styled rows (monospace, bracket language):

```
[ LEARNED CHOICES ]
 > bank     →  MyBank     auto           [ x ]
 > music    →  Spotify    learning 2/3   [ x ]
```

Each row: the slot `query` → the target app **label** (resolved from packageName), a transparency
state, and `[ x ]` delete. **App icons are a UI concern** — the domain provides packageName / label /
availability only; the UI loads the icon from packageName.

**See / change / delete (v1):**
- **See** — the list (visibility into what was learned).
- **Delete** — `[ x ]` → `DeleteLearnedChoiceUseCase` (fire-and-forget); the row disappears via
  `observeAll`.
- **Change** — v1 = **delete-then-relearn**: deleting a choice makes the next ambiguous command show
  candidates again, and a fresh explicit choice restarts learning. `reset` and a `re-point picker` are
  **backlog**.

**Honest display state.** The state label must reflect the **same eligibility the policy checks**, not
a bare `streak ≥ K`. `EvaluateLearnedChoiceDisplayStateUseCase` reconstructs the current candidate set
for the preference's query (using the same deterministic app-matching used at runtime), reads the
action's risk, and classifies from the **same primitives** as `ResolutionPreferencePolicy`:

- `Unavailable` — target not installed (→ filtered + best-effort prune).
- `Learning(streak, threshold)` — `streak < K`.
- `NeedsReconfirm` — `streak ≥ K` but fingerprint mismatch (or risk ≠ SAFE): will `RankFirst`, not auto.
- `Auto` — **verified** `CONFIDENT ∧ SAFE ∧ target alive ∧ currentFingerprint == learnedFingerprint`.
- `AutoReady` (safe fallback) — `streak ≥ K ∧ SAFE ∧ alive`, but the current candidate set **could not
  be reliably reconstructed** on this screen to confirm the fingerprint. Shown instead of an absolute
  `auto` promise (labels like `auto-ready` / `learned`), so the UI never claims auto-resolve where the
  policy would only rank-first.

Honest-display invariant: the screen shows `Auto` **iff** the policy would return `AutoResolve` for
that preference against the reconstructed live candidate set; when it cannot be confirmed, it degrades
to `AutoReady`/`learned`.

**Display invalidation:** each `preferredTarget.packageName` is checked against
`InstalledAppsRepository`; uninstalled targets are filtered out and best-effort pruned
(`deleteByTargetValue`) — prune failure does not break the screen.

**Thin use-cases (VM does not inject the store directly):** `ObserveLearnedChoicesUseCase`,
`DeleteLearnedChoiceUseCase`, `PruneUnavailableLearnedChoicesUseCase`. The VM stays Android-free; the
join with installed-app labels/availability and the display-state derivation are pure domain (+ the
display use-case).

**Safe error state:** `observeAll` is wrapped in a guarded flow — a DAO/mapper error yields an empty
list + an inline `couldn't load learned choices · retry`, never a crash; a single bad-mapping row is
skipped (others still shown), logged by category only (no `query`/packageName).

## 9. Failure / fallback (deterministic reference)

| Situation | Behavior |
|---|---|
| `store.find` I/O error | treat as no-preference → prior list (**parity**) |
| policy → `Stale` | best-effort `delete`; prior list; delete error ignored |
| AutoResolve → executor OK | `Launched` |
| AutoResolve → executor fail (generic) | do **not** record evidence; list with preferred first only if still valid |
| AutoResolve → executor fail (target unavailable) | best-effort invalidate/`delete`; list without the gone target |
| `record` I/O error | best-effort; not learned this time; no user impact |
| candidate tap but launch fails | do **not** record evidence (learn only on a successful explicit choice) |
| `observeAll` DAO/mapper error | guarded flow → empty list + inline retry, no crash; skip bad row |
| count > cap (500) | LRU eviction by `last_chosen_at` |
| empty / > 64-char query | no record created |
| candidate set changed (fingerprint ≠) | no auto-resolve → `RankFirst`; display = `NeedsReconfirm` |

All deterministic, on-device, never throw (except rethrowing `CancellationException`).

## 10. Testing strategy

- **Policy (pure JVM):** full decision table + boundaries (`streak` K−1 vs K); CONFIRM/RISKY even at
  CONFIDENT → `RankFirst` (never AutoResolve); fingerprint mismatch at CONFIDENT+SAFE → `RankFirst`;
  `strength()` seam.
- **Evidence (write use-case):** create / reinforce (streak++, fingerprint update) / correction
  hard-switch (`preferred=new, streak=1, total++`); record only on success; AutoResolve does not record.
- **Token / scope guard:** the VM returns a `ResolutionLearningToken` and builds no key; recording is
  impossible from grid taps / non-ambiguity / search-web-assistant.
- **Store (Robolectric Room):** round-trip; find/upsert/delete/`deleteByTargetValue`/`observeAll`; LRU
  cap; read/write failure → `OperationResult` (never throws); mapper (type/value, evidence); migration
  (golden `schemas/N.json` + `MigrationTestHelper`).
- **Display use-case:** `Unavailable / Learning / NeedsReconfirm / Auto / AutoReady` derived from the
  same primitives as the policy (**UI-honesty:** display `Auto` ⟺ policy would `AutoResolve` for that
  preference against the reconstructed live set; unverifiable set → `AutoReady`, never `Auto`).
- **Management VM:** `observeAll` error → safe empty + error state (no crash); delete wiring;
  prune-unavailable.
- **Privacy guards:** inventory (table + columns); outbound allow-list byte-for-byte unchanged;
  grep/dependency guard `domain/memory/resolution ⊥ AiRequest`/generative; query constraints; no PII
  (`query`/packageName) in external logs.
- **Parity guards:** no-preference ⇒ existing `LauncherViewModel`/command-outcome suites pass
  unchanged; non-ambiguous untouched; LLM path not involved.
- **Build gates:** `:domain:test` + `testDebugUnitTest` + `assembleDebug` green; SM-A325F device
  acceptance of the whole slice.

## 11. Definition of Done

- Architectural invariants (§2) all hold.
- **Parity:** no-preference / non-ambiguous / router-off ⇒ byte-for-byte prior behavior (existing
  suites unchanged + new parity tests).
- **Privacy:** outbound allow-list unchanged (guard); preferences never in `AiRequest`; inventory guard
  green; `query` constraints enforced; no PII in logs.
- **Correction semantics (v1, explicit):**
  - hard-switch correction applies in the **learning / rank-first phase**, when the user explicitly
    picks a different candidate;
  - after auto-resolve there is **no in-flow correction** in v1;
  - the post-auto-resolve correction channel is **Settings → Learned Choices → delete → re-learn**.
- **Honest display state:** `Auto` is shown **only** when the display use-case verifies the same
  conditions as the policy (CONFIDENT + SAFE + target alive + current fingerprint == learned
  fingerprint); when the current candidate set cannot be reliably reconstructed on the management
  screen, a safe label (`auto-ready` / `learned` / `needs reconfirm`) is used instead of an absolute
  `auto`.
- Everything deterministic, on-device, never throws (except `CancellationException`).
- New tests green (policy, evidence, store + migration, display, management VM, guards); `:domain:test`
  + `testDebugUnitTest` + `assembleDebug` green.
- SM-A325F device acceptance: the whole slice observable — ambiguous → choice → `learning n/K` →
  learning-phase correction switches target → after K auto-resolve → Settings delete → re-learn →
  uninstall target invalidates.
- ADR appended; `CLAUDE.md` / `current-status.md` synced; recorded as Stage-2 block **S2-1**.

## 12. Scope

**In (v1 vertical slice):** app-launch disambiguation (LAUNCH_APP, SAFE); learning from explicit
choice; threshold auto-resolve (K=3) / rank-first below threshold / hard-switch correction; lazy +
display invalidation; Room persistence; management screen (**See + Delete**); privacy guards; minimal
memory abstractions shaped to grow.

**Out (explicit):** no LLM/cloud/context-to-cloud; no general User Memory / Context Engine v2; no
disambiguation for web/site/play-store/settings/assistant; `ResolutionContext` = `None` only (no
time/place); no per-target evidence; no eager `PACKAGE_REMOVED` prune; no `reset` / `re-point` picker;
no in-flow "not this?" after auto-resolve; CONFIRM/RISKY never auto-resolve; auto-resolve never
reinforces; semantic/embedding capability clustering (OQ#3) is future.

## 13. Backlog / future evolution (not v1)

- Per-target evidence (soften correction; accumulate winner / decay loser) via `strength()` +
  richer `PreferenceEvidence` — no contract change.
- Non-degenerate `ResolutionContext` (time-of-day, place) — extends the key, same signatures.
- Eager `PACKAGE_REMOVED` pruning (uses the already-present `deleteByTargetValue`).
- Management `reset` (demote auto → learning without delete) and `re-point` picker.
- In-flow "not this?" correction after auto-resolve.
- Generalizing `ResolvedTarget` beyond `App` (url/action) and `CapabilityKey` beyond `(family, slot)`
  (semantic clusters once embeddings land, OQ#3).
- These trace the path toward User Memory → Context Engine v2 → World Model.
