# Phase 4 — Persistence, State & Navigation Hardening

> Format mirrors `phase-3-intent-system-plan.md`: blocks → checkbox steps → acceptance
> criteria. Block lettering continues Phase 3 (A→D), so Phase 4 = **Blocks E → H**.
> This document is the source of truth for Phase 4 scope and the resolved fork decisions.
> Status: **plan approved for documentation; no block started.** Each block requires a
> separate execution prompt. First execution round (user-approved): **Block E only**.

## In scope (unfreezing what Phase 3 deferred)

- **DataStore Preferences**: user preferences, feature flags, device-profile cache,
  last-known suggestions.
- **Room**: app usage history, suggestion-ranking history, intent-match-history (unfrozen).
- **Permission-education** module + request flow (was an inline placeholder).
- **Hardening**: `UiState` / navigation / recoverable errors; "feature modules touch
  persistence only through repositories".

## Hard invariants (the plan must obey)

- `domain` stays pure (stdlib + coroutines). Repository interfaces and domain models live in
  `domain`; Room entities and DataStore types **never** leak into the domain.
- Entity ↔ domain mapping lives inside `:data:repository`. Room annotations never cross the
  domain boundary.
- `feature/*` has no edge into `:data:*` — only domain repository interfaces, injected from
  `:app`.
- Business outcomes flow through `Success`, not `Failure`. `OperationResult<T>` is the
  repository result channel; repositories never throw to the UI.
- Frozen stays frozen: cloud AI (Ph5), ONNX (Ph6), voice (Ph7), accessibility (Ph8),
  WorkManager (Ph6/9). `IntentMatcher` ≠ generation.

---

## Block order and rationale

```
E  DataStore Preferences   — simpler, no migrations; provides feature-flags/profile-cache that F/G/H lean on
F  Room (schema + migrations) — harder, schema versioning/export; intent-match-history closes the Phase-3 pipeline
G  Permission-education     — new module + reusable request flow; reads "dismissed" flags from DataStore (E)
H  Hardening                — last: needs persistence (E/F, something to restore and a real I/O failure source) and the new destinations (G)
```

**Why E before F:** DataStore has no migrations, it establishes the
"repo-interface-in-domain ↔ impl+mapping-in-data" pattern, and feature flags are needed
earlier (a gate for future features and for blocks G/H).
**Why H last:** process-death restoration and recoverable errors only matter once persistence
exists (something to restore) and there are real I/O failure sources (DataStore/Room), plus
new navigation nodes from G.

E and F can run nearly in parallel once the gating fork decisions are fixed (KSP/kapt, Room
schema, secrets). G depends on E ("don't ask again" flags). H depends on E+F+G.

---

## Fork decisions (fixed BEFORE code)

### Fork 1 — Secure secrets storage — **DEFER to Phase 5 (approved)**
`architecture.md` names `EncryptedSharedPreferences`, but it is **deprecated** (Google: "no
longer recommended"). Options weighed: (A) defer — Phase 4 stores no secrets; (B) Tink AEAD +
Android Keystore master key; (C) backend proxy.

**Decision: (A) defer.** Phase 4 stores no secrets. A `SecureSecretStore` interface appears in
`domain` in Phase 5 when a real key exists (cloud/ONNX); only then do we pick Tink vs proxy by
the facts. **ESP is not the default and is not introduced here.** DataStore in Block E holds
**non-secret data only**.

### Fork 2 — Room schema lifecycle (Block F)
- `exportSchema = true` from the start; `room.schemaLocation` → `data/repository/schemas/`,
  **schemas committed to VCS**.
- `version = 1`, but migration infrastructure is in place from day one: a
  `…data.repository.db.migrations` package and a wired `MigrationTestHelper` (androidTest);
  rule "change an entity → bump version + Migration + golden schema".
- `AutoMigration` where possible; manual `Migration` objects where not.
- `fallbackToDestructiveMigration` — **only** in debug builds and **only** for learning/cache
  tables (usage/ranking/intent-match are recreatable). Forbidden in release. Nothing we promise
  to keep falls under destructive fallback.

### Fork 3 — Persistence as a data-loss/leak risk (inventory fixed in Block E, step E1)

| Stored (DataStore) | Stored (Room) | Never stored |
|---|---|---|
| user preferences | app usage history | AI conversation history |
| feature flags | suggestion-ranking history | location history |
| device-profile cache | intent-match-history | raw voice input |
| last-known suggestions | | API keys / secrets (Fork 1 → Phase 5) |

How it is enforced:
- For forbidden categories there is **no** entity, no DAO, no DataStore key — absence of a
  carrier is the guarantee.
- `intent-match-history` stores **only** normalized command text + match type + confidence +
  timestamp. No raw voice, no free conversational text.
- Retention: row-count cap + age-based pruning, executed **in the repository on write**
  (WorkManager is frozen to Ph6/9 — no background cleanup here).
- Test guard: a check that the Room entity list and DataStore keys contain no forbidden fields
  (table-driven test + anchor comment).

### Fork 4 — Schema owner and mapping location
- **`domain`** owns domain models (`UserPreferences`, `FeatureFlags`, `DeviceProfileCacheEntry`,
  `Suggestion`, `AppUsageRecord`, `IntentMatchRecord`, `SuggestionRankingRecord`) and repository
  interfaces.
- **`:data:repository`** owns Room entities, DAOs, `@Database`, `TypeConverters`, DataStore
  keys/serialization, and the **mappers** `entity ↔ domain` (`…data.repository.mapper`).
- Room/DataStore annotations do not leave `:data:repository`. Repo interfaces return domain
  models / `OperationResult` / `Flow<domain model>`.

### Fork 5 — Permission-education: education ≠ request, and scope
Separate two concepts: **education/rationale** (explanatory screen, always available, **no**
system dialog) from **request** (the system permission dialog, **triggered by a feature** via
`ActivityResultContracts.RequestPermission`).

**Phase 4 permission scope:** no optional-permission-gated feature exists yet — voice (Ph7),
calendar/location suggestions (Ph7), accessibility (Ph8) are frozen. So Phase 4 builds the
**reusable module + request-flow contract** and wires a live demo trigger to **`SET_WALLPAPER`**
(optional, non-dangerous, launcher-core-adjacent, demoable). Future dangerous permissions
(`RECORD_AUDIO`, `READ_CALENDAR`, `ACCESS_FINE_LOCATION`) have education content but the request
flow stays **dormant** until their phase. **`BIND_ACCESSIBILITY_SERVICE` is fully deferred
(Phase 8)** — no education either, to avoid implying consent before an explicit user-initiated
flow.
- Status check — `core/android` (`PermissionChecker` over `checkSelfPermission`).
- Launching the system dialog — UI layer (`:feature:permission_education` / `:app`,
  `ActivityResultContracts`), not `core/android`.
- "Don't ask again / dismissed" flag — via DataStore (Block E).
- Rule: denial disables exactly that feature; launcher core is never blocked.

### Fork 6 — What exactly "harden" means (Block H)
- **Recoverable errors without restart:** repositories can now actually fail on I/O
  (DataStore/Room). Full path `OperationError → UiError → UiState.Error(retryable)` + a retry
  action with no process restart. Categories per `architecture.md`
  (`NetworkError/PermissionDenied/UnknownError/...`).
- **Process-death restoration:** transient UI state (command-input text, current route args)
  via `SavedStateHandle`; content state (last-known suggestions, profile) restored fast from the
  DataStore cache.
- **Navigation:** replace inline placeholders with real destinations (permission_education from
  G), keep the single `NavHost` + safe-fallback (3.1.5) for new nodes, back-stack correctness.
- **UiState consistency:** single source of truth per VM, correct `Empty` handling, no business
  logic in composables — audit and finish across all VMs.

### Fork 7 — Testability of persistence
- **Room DAO/repo:** Robolectric in the JVM unit source set (fast, CI-friendly, no emulator) —
  **new dependency, to discuss**. Alternative: `androidTest` (instrumented, no new dependency,
  needs emulator/device). **Recommendation:** Robolectric for DAO/repo tests + a minimal
  `androidTest` only for `MigrationTestHelper` (migrations are best tested instrumented).
- **DataStore:** test on a temp file / `TestScope` (`PreferenceDataStoreFactory` with a tmp dir)
  — no new dependency, JVM.
- **Feature/VM tests:** repository fakes from `:core:testing` only — no Room/DataStore in feature
  tests. No `NO-SOURCE`: every new repository and mapper is covered.

### Fork 8 — KSP vs kapt for Room
The project currently uses `kotlin-kapt` (for the Hilt compiler).

| Option | Pros | Cons |
|---|---|---|
| **(A) Room via KSP, keep Hilt on kapt (hybrid)** | Room's KSP processor is markedly faster; Room is officially KSP; kapt stays only for Hilt. | Two processors in the build (KSP + kapt); new `com.google.devtools.ksp` plugin in the catalog. |
| (B) Room via the existing kapt | Zero new plugins, single pipeline. | kapt for Room is maintenance-mode, slower; against the current direction. |
| (C) KSP for Room **and** migrate Hilt to KSP | Removes kapt entirely, fastest pipeline. | Larger one-off change (touches the working Hilt wiring of all modules); DI-regression risk for a non-functional gain — against the "minimal slice" spirit. |

**Recommendation: (A) hybrid — Room on KSP, Hilt on kapt for now.** Gives Room a modern fast
processor without touching the working Hilt graph. A full Hilt→KSP migration (C) is split out as
an optional Phase 9 task (build optimization). The KSP plugin is flagged as a **new dependency**;
its version is chosen against Kotlin `2.0.21`.

### Fork 9 — `architecture.md` out of sync with reality
`docs/architecture.md` still partly describes a target/stale layout: the dropped `core/data` is
already handled in "resolved" notes, but `core/testing`/`data/repository` are still tagged
"(planned)", and the persistence/security section leans on the deprecated
`EncryptedSharedPreferences`. **Task in this plan (not edited in the planning pass):** during
Phase 4, sync `architecture.md` to the actual module layout and decisions (as was done with
`CLAUDE.md`): drop "(planned)" from existing modules, reflect Fork 1 (secrets), Fork 2 (Room
schema), Fork 8 (KSP). Executed as **Block H, step H6** (docs-sync at the end of the phase, to
lock in what was decided).

---

## Block E — DataStore Preferences (persistence foundation, no migrations)

**Goal:** user preferences, feature flags, device-profile cache, last-known suggestions —
behind repository interfaces, observable via `Flow`, written via `OperationResult`. Establish
the `domain-interface ↔ data-impl+mapping` pattern. **First execution round.**

**Depends on:** nothing (Phase 4 starting block). Forks 1, 3, 4 fixed before code.

**New / changed files by module:**
- `:domain` (`…domain.preferences`): `UserPreferences`, `FeatureFlags`, `DeviceProfileCacheEntry`,
  a minimal `Suggestion` model (data holder, no pipeline) + interfaces
  `UserPreferencesRepository`, `FeatureFlagRepository`, `DeviceProfileCacheRepository`,
  `SuggestionsCacheRepository` (reads → `Flow<T>`, writes → `OperationResult<Unit>`).
- `:data:repository` (`…data.repository.preferences`): DataStore instance + keys/serialization,
  `*RepositoryImpl` ×4, mappers; `build.gradle.kts` += `datastore-preferences`.
- `gradle/libs.versions.toml`: `androidx.datastore:datastore-preferences` entry (expected dep).
- `:app` (`di`): `PersistenceModule` (provide DataStore + `@Binds` repositories) — modeled on
  `RepositoryModule`.
- `:core:testing`: `FakeUserPreferencesRepository`, `FakeFeatureFlagRepository`,
  `FakeDeviceProfileCacheRepository`, `FakeSuggestionsCacheRepository`.

**Steps:** *(E1–E8 complete 2026-06-22 — see decisions.md "ADR Block E")*
- [x] `E1` Privacy inventory (Fork 3) — split **three categories explicitly**, do not collapse
      to "no secrets":
      - **Allowed in DataStore:** preferences, feature flags, device-profile cache,
        last-known suggestions.
      - **Forbidden anywhere** (per `architecture.md`): AI-conversation history, location
        history, raw voice input.
      - **Deferred:** secrets / keys → Phase 5 (Fork 1).
      Add as an anchor comment + a test-guard skeleton.
      **Sub-check — last-known suggestions sensitivity:** verify no sensitive context leaks into
      the cache. Cache **only** what is needed to repaint the last suggestion surface on cold
      start: a small bounded list of suggestion *display items* (label + the action/route id
      already resolvable offline). **Do NOT cache:** raw query/search text the user typed, search
      timestamps/history, location- or calendar-derived context, or per-app usage trails. If the
      `Suggestion` model would carry any of those, store the minimal display projection instead
      and document the field-by-field "cached / not cached" split next to the model. This keeps
      "last-known suggestions" a UI-repaint cache, not a behavioral-history store (that's Room's
      intent-match/usage tables, with their own constraints).
- [x] `E2` Add DataStore to the catalog + `:data:repository/build.gradle.kts`.
- [x] `E3` Domain models + repo interfaces (`Flow` on read, `OperationResult` on write).
- [x] `E4` `Impl` ×4 over Preferences DataStore + mappers; writes return `OperationResult`, I/O
      exceptions caught → `OperationError`, never thrown.
- [x] `E5` `DeviceProfileCacheRepository`: caches the computed `DeviceProfile` (the detector
      stays in `core/android`; only the cache lives here).
- [x] `E6` DI `PersistenceModule` in `:app`; confirm `feature/*` gains no edge into `:data:*`.
- [x] `E7` Fakes in `:core:testing`.
- [x] `E8` Tests: DataStore on a tmp file / `TestScope` (Fork 7) — round-trip, defaults, `Flow`
      observation. No new test dependency (Robolectric not needed for E).

**Acceptance criteria:**
- A written preference/flag/profile survives a process restart (round-trip test).
- `:domain` stays stdlib+coroutines (`:domain:dependencies` guard).
- `feature/launcher` has no edge into `:data:repository` (grep guard).
- `assembleDebug` + `testDebugUnitTest` green.
- The privacy inventory (E1) is recorded and the last-known-suggestions field split is
  documented; no forbidden field has a DataStore key.
- 🎬 **Demoable:** a feature-flag toggle and a user setting survive a full app restart.

---

## Block F — Room (schema, migrations, history)

**Goal:** app usage history, suggestion-ranking history, intent-match-history behind repository
interfaces; schema versioned/exported with a migration runway; intent-match-history closes the
Phase-3 pipeline.

**Depends on:** Block E (persistence pattern). Forks 2, 4, 8 fixed before code.

**New / changed files by module:**
- `:domain` (`…domain.history`): `AppUsageRecord`, `SuggestionRankingRecord`, `IntentMatchRecord`
  + interfaces `UsageHistoryRepository`, `SuggestionRankingRepository`,
  `IntentMatchHistoryRepository`.
- `:data:repository` (`…data.repository.db`): `entity/`, `dao/`, `SidrDatabase` (`@Database`,
  `exportSchema=true`), `TypeConverters`, `migrations/`, `mapper/`, `*RepositoryImpl` with
  retention-pruning on write; `schemas/` dir in VCS; `build.gradle.kts` += Room + schemaLocation
  arg + (per Fork 8) KSP plugin.
- `gradle/libs.versions.toml`: Room runtime/ktx/compiler (+ KSP plugin, flagged new dep).
- `:data:repository/src/androidTest`: `MigrationTest` via `MigrationTestHelper`.
- `:app` (`di`): provide `SidrDatabase` + DAOs + `@Binds` repositories (in `PersistenceModule`
  or a dedicated `DatabaseModule`).
- `:domain` (`…domain.intent`): `HandleUserCommandUseCase` optionally records an
  `IntentMatchRecord` via `IntentMatchHistoryRepository` (domain interface, clean).
- `:feature:launcher`: grid may sort by usage recency/frequency (consumes usage history).
- `:core:testing`: fakes for the three history repositories.

**Steps:**
- [ ] `F1` Fork 8 (KSP/kapt) decision approved → configure the processor; Room into catalog +
      build file; `exportSchema=true`, schemaLocation, `schemas/` dir.
- [ ] `F2` Entities + DAOs + `SidrDatabase` (version=1) + `TypeConverters`; `migrations/` package
      seeded (empty runway) + bump/Migration/golden-schema rule.
- [ ] `F3` Entity↔domain mappers (Fork 4); Room annotations stay inside `:data:repository`
      (grep guard).
- [ ] `F4` `*RepositoryImpl` with retention (cap + age-pruning on write, Fork 3); I/O →
      `OperationResult`, never throws.
- [ ] `F5` Integration: `HandleUserCommandUseCase` writes `IntentMatchRecord` (normalized text +
      type + confidence + ts only, no raw voice — Fork 3).
- [ ] `F6` Consume usage history in the `feature/launcher` grid (recency/frequency sort) —
      demoable slice.
- [ ] `F7` DI: database/DAO/repo in `:app`.
- [ ] `F8` Tests: Robolectric DAO/repo round-trip + retention; `androidTest` `MigrationTest`
      (v1 baseline + helper wired); privacy inventory guard test (no forbidden fields).

**Acceptance criteria:**
- Schema exported to `schemas/` and committed; `MigrationTestHelper` works on v1 (runway for
  future migrations).
- `intent-match-history` is written when a command executes; contains only allowed fields.
- Grep guard: Room annotations / `androidx.room` never appear outside `:data:repository`;
  `:domain` has no Room.
- `assembleDebug` + JVM tests + the minimal `androidTest` green.
- 🎬 **Demoable:** launch a few apps → after restart the grid orders them by
  frequency/recency (history survives restart); a repeated command shows in
  intent-match-history.

---

## Block G — Permission-education module + request flow

**Goal:** a real `:feature:permission_education` replacing the inline placeholder; a reusable
"education ≠ request" contract; a live demo trigger on `SET_WALLPAPER`; accessibility deferred.

**Depends on:** Block E ("dismissed / don't ask again" flag in DataStore).

**New / changed files by module:**
- `:feature:permission_education` (**new module**): `PermissionEducationScreen`,
  `PermissionEducationViewModel`, rationale content per `PermissionFeature`, request-flow glue
  (`ActivityResultContracts`).
- `:domain` (`…domain.permission`): `PermissionFeature` (enum: in-scope `WALLPAPER` only; dormant
  entries for the future), `PermissionStatus`, optional `PermissionPrefsRepository` (dismissed
  flags over DataStore).
- `:core:android`: `PermissionChecker` (over `checkSelfPermission`) → `PermissionStatus`.
- `:core:common` (`navigation`): `Routes.PermissionEducation` (+ `NavigationEvent` node if
  needed).
- `:app`: `settings.gradle.kts` += module; `AppNavHost` — real destination instead of the
  placeholder; ActivityResult launcher wiring; DI bindings.
- `:core:testing`: fake `PermissionChecker` / `PermissionPrefsRepository`.

**Steps:**
- [ ] `G1` Create `:feature:permission_education`, register in `settings.gradle.kts`,
      dependency direction `app → feature → domain/core` (no `feature→feature`, no `feature→data`).
- [ ] `G2` `PermissionFeature`/`PermissionStatus` in domain; `PermissionChecker` in `core/android`.
- [ ] `G3` Separate education (rationale screen, no system dialog) from request (dialog on
      feature trigger) — Fork 5.
- [ ] `G4` Live `SET_WALLPAPER` trigger: education → request → feature reaction; denial disables
      exactly that feature, core not blocked.
- [ ] `G5` "Don't ask again / dismissed" via DataStore (Block E).
- [ ] `G6` `AppNavHost`: replace the inline placeholder with a real destination + safe-fallback
      (3.1.5).
- [ ] `G7` Tests: VM on fake `PermissionChecker`/prefs; granted/denied/permanently-denied paths;
      core not blocked on denial.

**Acceptance criteria:**
- An optional permission is requested **only** on feature trigger, never at startup.
- `BIND_ACCESSIBILITY_SERVICE` is never requested or educated (deferred to Ph8).
- No `feature→feature` / `feature→data` edges; single `NavHost` preserved.
- 🎬 **Demoable:** wallpaper trigger → explanatory screen → system dialog → grant enables the
  feature, deny disables only it, launcher keeps working.

---

## Block H — Hardening (UiState / navigation / recoverable errors) + docs-sync

**Goal:** make Fork 6 concrete — recoverable errors without restart, process-death restoration,
navigation and `UiState` consistency; sync `architecture.md` (Fork 9).

**Depends on:** E + F (I/O failure sources and a cache to restore from) + G (new destinations).

**New / changed files by module:**
- `:core:common`: `UiError`/`UiState` — add retryable semantics / retry action if needed.
- `:feature:launcher` (and other VMs): full `OperationError → UiError → UiState.Error(retryable)`
  path + retry without restart; `SavedStateHandle` for command-input/route args; fast content
  restore from the DataStore cache.
- `:app` `AppNavHost`: safe-fallback for all new nodes, back-stack correctness.
- `docs/architecture.md`: docs-sync (drop "(planned)" from existing modules, reflect Forks
  1/2/8) — **step H6**.
- Tests: offline/error/retry paths, process-death restoration.

**Steps:**
- [ ] `H1` Audit all VMs: single source of truth, correct `Empty`, zero business logic in
      composables.
- [ ] `H2` Recoverable errors: full `OperationError → UiError → UiState.Error(retryable)` +
      retry action without restart (categories from `architecture.md`).
- [ ] `H3` Process-death restoration: `SavedStateHandle` for transient UI; content restore from
      the DataStore cache.
- [ ] `H4` Navigation: safe-fallback for new destinations (G), back-stack, single `NavHost`.
- [ ] `H5` Tests: induced repo error → retry without restart; kill→reopen → state restored.
- [ ] `H6` **Docs-sync** `architecture.md` ↔ actual module layout + Fork 1/2/8 decisions
      (Fork 9). Update `CLAUDE.md`/`decisions.md` with Phase 4 ADRs.

**Acceptance criteria:**
- A repository error (DataStore/Room) → a recoverable `UiState.Error` with retry, no process
  restart.
- After a process kill, input/route state is restored; content comes back fast from the cache.
- `architecture.md` matches the real layout; Phase 4 ADRs recorded.
- Launcher core remains fully offline-capable.

---

## Frozen / pushed forward (Phase 5+)

- Secure secret/API-key storage (`SecureSecretStore`) → **Phase 5** (Fork 1).
- Full Hilt migration from kapt to KSP → **Phase 9** (optional, Fork 8 option C).
- WorkManager (background history cleanup, suggestion pre-compute, ONNX download) → **Phase 6/9**.
- Cloud AI (Ph5), ONNX NLU/embeddings (Ph6), voice/`SpeechInputSource` (Ph7), context-suggestion
  pipeline (Ph7), accessibility + its education/consent (Ph8).
- `:feature:settings` as a standalone module — stays a placeholder until needed (out of Phase 4
  scope).
- Request flow for `RECORD_AUDIO`/`READ_CALENDAR`/`ACCESS_FINE_LOCATION` — dormant until their
  phase (education content may exist, the dialog does not).
- Extraction of `core/navigation` — on trigger.

## Demoable milestones (like `open telegram` for Phase 3)

- **E:** a setting/flag survives an app restart.
- **F:** the grid sorts by real usage history after restart; a command lands in
  intent-match-history.
- **G:** the wallpaper trigger → education → grant/deny, denial disables only that feature.
- **H:** kill→reopen restores state; a repository error retries without restart.

## Tracking

- Record each block's completion as an ADR in `ai-context/decisions.md` (as Blocks A–D did).
- Update root `CLAUDE.md` status snapshot per block.
- Per-block acceptance: Block E's DataStore round-trip tests should be green before Block F
  starts; Block F's schema/migration test before Block G; etc.
