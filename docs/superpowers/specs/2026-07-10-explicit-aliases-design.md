# Explicit Aliases (S2-2) — Design Spec

> **Status: APPROVED (owner, 2026-07-10)** section-by-section via brainstorming. This is a **design spec**;
> the implementation plan (`writing-plans`) is the next step. Behavior source of truth is this document.
> Sibling to the S2-1 "Learned Resolutions" design
> ([2026-07-06-learned-resolutions-design.md](2026-07-06-learned-resolutions-design.md)).

## 1. Summary & motivation

**S2-2 is the second Stage-2 feature-first slice.** It gives the user an **explicit, self-authored
nickname → app** mapping: typing "рабочий чат" launches Telegram, "мой банк" launches the bank app —
names that otherwise match no installed app or command. Fully on-device, no LLM/cloud, offline.

This is the **explicit** complement to S2-1's **implicit** learning: S2-1 learns from ambiguity
(`NeedsConfirmation`), S2-2 is user-declared and fills the gap when the rule matcher finds nothing
(`Unknown`). Together they realize "the launcher knows what you mean." S2-2 also advances roadmap
**Framework-3 (User Memory)**, whose acceptance explicitly lists *confirmed aliases ("work chat" → a
specific app/action), editable/deletable, no private memory to cloud*.

**Owner-locked decisions (2026-07-10):**
- **Direction:** explicit aliases (over Context Engine v2 / generalizing S2-1 / suggestion personalization).
- **Entry point:** a **Settings form** (no command-pipeline parser).
- **Precedence:** **fill-the-gap** — consulted **only** when the rule matcher returns `Unknown`; real app
  names and commands always win (strict rule-first, parity-safe).
- **Target scope (v1):** **app only** (`AliasTarget.App`), model shaped to grow toward action targets.
- **Storage:** a **separate clean `Alias` concept** (own model, port, Room table, use-cases) — not an
  extension of S2-1's `ResolutionPreference`.
- **Integration:** a **decorator over** S2-1's `ResolveCommandWithPreferenceUseCase` (not woven into it).
- **Management UI:** a **dedicated Settings → Aliases screen** (sibling to Learned Choices, not merged).

## 2. Scope

**In scope (v1):**
- `Alias` domain model + `AliasStore` port + validating/observe/delete/prune use-cases (pure `domain`).
- A decorator `ResolveCommandWithAliasUseCase` that resolves an alias to an `AutoLaunch` directive when the
  underlying resolution yields `Unknown`.
- Room table `aliases` (`SidrDatabase` v2 → v3 + `Migration2To3` + golden `3.json`) + store impl + mapper.
- Settings → Aliases screen: list + add-form (phrase + app picker) + delete; empty/error states.
- Privacy / scope / parity guard tests; build gate; device-acceptance checklist.

**Explicitly NOT in v1 (YAGNI):**
- Action targets other than app launch (URL / web search / settings / assistant) — additive later via a
  new `AliasTarget` case.
- Alias **override** of real app names / commands (precedence is fill-the-gap only).
- Fuzzy / substring / prefix matching — exact normalized-phrase match only.
- Import/export, sync, sharing, per-context aliases, multi-target aliases.
- Any change to `HandleUserCommandUseCase` / `RouteCommandUseCase` / the rule matcher.

## 3. Domain model — `domain/memory/alias/` (pure, additive)

```
Alias(
    phrase: String,              // normalized full phrase (CommandNormalizer), unique key
    target: AliasTarget,
    createdAtEpochMs: Long,
)

sealed interface AliasTarget {
    data class App(val packageName: String) : AliasTarget   // v1's only case
}

const val MAX_ALIAS_PHRASE_LENGTH = 64
```

- `phrase` is the **normalized** form (reuse `CommandNormalizer.normalize`) so lookup at input time and the
  stored key agree; case/whitespace-insensitive by construction. Multi-word allowed (unlike S2-1's single
  slot).
- `AliasTarget` is **sealed** purely so a future `Action(...)` case is an additive change — v1 exhaustive
  `when` has one branch.
- A tiny cast-free helper `AliasTarget.appPackageOrNull(): String?` (mirrors S2-1's precedent) avoids
  `as`-casts at read sites.

### 3.1 `AliasStore` port (in `domain`)

```
interface AliasStore {
    suspend fun find(phrase: String): OperationResult<Alias?>
    suspend fun upsert(alias: Alias): OperationResult<Unit>
    suspend fun delete(phrase: String): OperationResult<Unit>
    fun observeAll(): Flow<List<Alias>>
}
```

Never-throws contract (rethrows `CancellationException`), identical discipline to
`ResolutionPreferenceStore`.

### 3.2 Use-cases (pure)

- **`SaveAliasUseCase.save(phrase, target)`** → normalizes `phrase`; **no-op `Success`** on blank or
  `length > MAX_ALIAS_PHRASE_LENGTH`; else `upsert` (last-wins: one target per phrase). Returns
  `OperationResult<Unit>`. Never throws.
- **`DeleteAliasUseCase.delete(phrase)`** → `store.delete`; `OperationResult<Unit>`.
- **`ObserveAliasesUseCase.observe(): Flow<List<AliasView>>`** → `store.observeAll()` cross-checked against
  `InstalledAppsRepository`: an alias whose target app is uninstalled is **filtered out** of the view (the
  row disappears; the record may be pruned lazily). `AliasView(phrase, targetPackageName, targetLabel)`.
- **`PruneUnavailableAliasesUseCase.prune()`** → best-effort delete of aliases whose target app is no
  longer installed. Never throws; returns `OperationResult<Unit>`.

## 4. Resolution / integration — decorator over S2-1

New pure `ResolveCommandWithAliasUseCase` **wraps** S2-1's `ResolveCommandWithPreferenceUseCase` and
returns the **same** `ResolvedCommand` surface (`Outcome | AutoLaunch`), so the VM's existing `AutoLaunch`
handling is reused unchanged:

```
class ResolveCommandWithAliasUseCase(
    private val inner: ResolveCommandWithPreferenceUseCase,   // S2-1
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val resolved = inner.resolve(rawInput)
        // Alias fires ONLY when the rule pipeline found nothing. Any other outcome (including S2-1's
        // NeedsConfirmation / RankFirst / AutoLaunch) passes through untouched → orthogonal, no conflict.
        if (resolved !is ResolvedCommand.Outcome) return resolved
        if (resolved.outcome !is CommandOutcome.Unknown) return resolved

        val phrase = CommandNormalizer.normalize(rawInput)
        val alias = (store.find(phrase) as? OperationResult.Success)?.value ?: return resolved
        val pkg = alias.target.appPackageOrNull() ?: return resolved
        // Only auto-launch an installed target; otherwise keep the original Unknown outcome.
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        if (installed.none { it.packageName == pkg }) return resolved

        return ResolvedCommand.AutoLaunch(
            target = ResolvedTarget.App(pkg),
            fallback = resolved.outcome,   // the original Unknown, rendered if launchApp fails
        )
    }
}
```

- **Fill-the-gap:** alias is consulted **only** on `CommandOutcome.Unknown`, i.e. after the rule matcher
  (and S2-1) declined. Real app names / commands are never shadowed.
- **Direct launch (SAFE):** an alias → `AutoLaunch` directive; the VM launches the specific package via the
  existing `launchApp`; input clears only on real launch success; on failure the VM renders `fallback`
  (the original `Unknown`) exactly as a typed command would. No confirmation card (app launch is SAFE,
  mirrors S2-1's auto-resolve). `AutoLaunch` is a **directive**, never a premature `Executed`.
- **VM change is minimal:** `LauncherViewModel` injects `ResolveCommandWithAliasUseCase` in place of the
  S2-1 use-case (the outer decorator internally holds the inner one). No new VM state; `AutoLaunch`
  handling already exists. **No alias recording on launch** (aliases are declared in Settings, not learned
  from taps — unlike S2-1).

## 5. Persistence — `:data:repository`

- **`AliasEntity`** (`@Entity tableName = "aliases"`): `phrase` (PK, `TEXT`), `target_type` (`TEXT`,
  `"app"` in v1), `target_package` (`TEXT`), `created_at` (`INTEGER`). `AliasDao` (upsert / delete by
  phrase / find by phrase / observe all). `AliasStoreImpl` + mapper (never-throws; `OperationResult`).
- **`SidrDatabase` v2 → v3 + `Migration2To3`**: additive `CREATE TABLE aliases (...)` (**not** destructive
  recreate — S2-1's precedent). `exportSchema = true`; commit golden `data/repository/schemas/3.json`.
- **`RoomColumnNames`** and the privacy inventory extended for `aliases`. `aliases.phrase` collides with
  the forbidden term `"query"`-style scoping the same way `resolution_preferences.query` does (documented +
  scoped in the guard).
- **No LRU cap in v1** (unlike S2-1's `MAX_RESOLUTION_PREFERENCES`): aliases are user-authored and bounded,
  so unbounded automated growth is not a risk. Revisit only if a real need appears.

## 6. Management UI — `:feature:settings`

- **`Routes.Aliases`** (`core/common`) — a dedicated screen, **sibling** to Learned Choices (concepts stay
  separate). Settings gains a `[ aliases ]` entry row → `navigateTo(Routes.Aliases.ROUTE)`. `AppNavHost`
  registers the `composable`.
- **`AliasesViewModel`** (Android-free): injects `SaveAliasUseCase`, `DeleteAliasUseCase`,
  `ObserveAliasesUseCase`, `PruneUnavailableAliasesUseCase` (**not** the store) + the installed-apps list
  for the picker. `StateFlow<AliasesUiState>` with a **guarded flow** (`catch` → error state, never crash;
  skip a bad row); `prune()` on load; `onDelete(phrase)` fire-and-forget; `onSave(phrase, packageName)`.
- **`AliasesScreen`** (`:feature:settings`): list of aliases (`> phrase → label`, `[ x ]` delete) + an
  **"Add alias"** affordance (phrase text field + installed-app picker). Empty + error states. On save,
  validate; **best-effort soft warning** if the normalized phrase equals an installed app's label (the
  cheap, common shadowing case — "the app will win; aliases only fill gaps"). The warning is advisory only,
  never blocks saving, and does not attempt full command-matcher evaluation. App icon/label resolved from
  packageName in the UI layer.
- **`core/ui` `AliasRow`** — dumb (strings + lambdas only, no `domain → ui` edge); the feature maps
  model → labels. Preview + a simple render.

## 7. Privacy & parity (guard tests — the hard gates)

- **Outbound allow-list widened by ZERO.** Aliases (phrase + package) are **local-sensitive metadata**;
  they never enter an `AiRequest`. A guard asserts `OutboundContextPolicy`'s allow-list set is byte-for-byte
  its prior value, plus a leak test that a planted sentinel alias phrase never appears in an outbound body.
- **Dependency guard:** a grep-style test asserts `domain/memory/alias/` sources carry no
  `AiRequest`/generative import (precedent: existing vendor-neutrality greps + S2-1's scope guard).
- **Room privacy inventory:** `RoomColumnNamesGuardTest` covers the `aliases` table/columns.
- **Scope guard:** `SaveAliasUseCase` no-ops on blank / over-length phrase; the phrase is never logged.
- **Parity:** with an empty alias store, and for **every non-`Unknown`** outcome, `ResolveCommandWith
  AliasUseCase` returns byte-for-byte what the S2-1 decorator returned. `HandleUserCommandUseCase` /
  `RouteCommandUseCase` / the rule matcher are untouched → no-alias / non-Unknown / router-off ⇒
  byte-for-byte the pre-S2-2 path.

## 8. Failure / edge behavior

- **Uninstalled target:** never auto-launches (installed-check in §4); row hidden in Settings (§6);
  pruned lazily. `launchApp` failure at runtime → render `fallback` (original `Unknown`).
- **Duplicate phrase:** upsert, last target wins (one target per phrase).
- **Phrase that matches a real app/command:** unreachable at input time (fill-the-gap, only on `Unknown`)
  → harmless dead entry; creation-time soft warning surfaces it.
- **Store I/O failure:** `find` failure → treated as miss (return inner result); `upsert`/`delete` failure
  → `OperationResult.Failure`, VM ignores in fire-and-forget. Never throws.
- **Blank/over-length input:** normalized empty phrase never matches; save no-ops.

## 9. Testing

- **Domain:** `AliasTarget`/model + `appPackageOrNull`; each use-case (save validation/normalization,
  delete, observe cross-check hides uninstalled, prune); `ResolveCommandWithAliasUseCase` — Unknown+hit
  installed → `AutoLaunch(fallback=Unknown)`; Unknown+miss → passthrough Unknown; Unknown+hit-but-
  uninstalled → passthrough; **any non-Unknown → passthrough** (parity); empty store → passthrough.
- **Data:** `AliasDao` (Robolectric), `AliasStoreImpl` never-throws + round-trip, `Migration2To3`
  (`MigrationTestHelper`), `RoomColumnNamesGuardTest`.
- **Feature:** `AliasesViewModel` (observe error → error state; delete wiring; prune on load; save
  validation); `LauncherViewModel` (decorator injected; alias Unknown→launch; parity suite still green).
- **Build gate:** `:domain:test` + `testDebugUnitTest` + `assembleDebug` green (JBR-21 + JDK-17 toolchain
  workaround, same as AIL-6 / S2-1).

## 10. Definition of Done

- All of §9 green; outbound allow-list provably unchanged; rule-first / parity structurally intact.
- **SM-A325F device acceptance** (separate pass, mirrors S2-1): Settings → Aliases → add "рабочий чат" →
  Telegram → type "рабочий чат" on home → Telegram launches directly (no list, no confirm) → typing a real
  app name still opens that app (parity) → delete the alias → the phrase again yields `Unknown` → uninstall
  the target → row gone / no stale launch. Screenshots. No code change expected.
- Docs: ADR "S2-2 Explicit Aliases complete" in `decisions.md`; sync `CLAUDE.md` + `current-status.md`;
  mark the plan done.

## 11. How this grows (non-binding, for continuity)

Two concrete memory types now exist — S2-1 (implicit, learned, evidence-based) and S2-2 (explicit,
declared). A later slice (S2-3) can honestly extract a shared **User Memory** abstraction from these two
real examples rather than guessing it up front. `AliasTarget` growing an `Action(...)` case (URL / web /
settings / assistant, with risk-gating via the existing confirmation card) is the natural v2 of this
feature and maps onto roadmap Framework-1 (Action Registry) + Framework-3.
