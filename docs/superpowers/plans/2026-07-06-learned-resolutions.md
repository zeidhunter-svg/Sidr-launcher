# Learned Resolutions (S2-1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.
>
> **STATUS: PLAN — awaiting owner approval. Do NOT start execution** (no production code, no Room schema
> change, no use-case wiring) until the owner approves this plan. Source of truth for behavior is the
> design spec: [2026-07-06-learned-resolutions-design.md](../specs/2026-07-06-learned-resolutions-design.md).

**Goal:** Teach the launcher, entirely on-device, which app the user meant for an ambiguous launch
command — rank the learned target first, then (after K consistent explicit choices) auto-resolve it —
always correctable via a Settings management screen.

**Architecture:** Approach A — a pure deterministic `ResolutionPreferencePolicy` + a
`ResolutionPreferenceStore` port live in `domain/memory/resolution/`; a read-path decorator applies the
policy **only** to the rule `CommandOutcome.NeedsConfirmation` (app ambiguity) and records **only** after
a successful explicit candidate choice; a Room table in `:data:repository` persists it; a Settings
sub-screen shows/deletes learned choices. `HandleUserCommandUseCase`/`RouteCommandUseCase` contracts are
untouched → no-preference / non-ambiguous ⇒ byte-for-byte parity.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, Hilt, Jetpack Compose, JUnit4 + Robolectric (Room tests).

## Global Constraints

- `domain` = pure Kotlin (stdlib + coroutines only); interfaces in `domain`, impls in `data/*`; UI holds
  no business logic; no `feature → feature` deps; repository/use-case ops return `OperationResult<T>` and
  never throw to callers (rethrow `CancellationException` only).
- **Parity:** no stored preference / non-ambiguous command / router-off ⇒ prior behavior byte-for-byte.
- **Auto-resolve only for SAFE actions**; CONFIRM/RISKY never auto-resolve.
- **On-device only:** no LLM, no cloud, no network on this path. The outbound allow-list
  (`OutboundContextPolicy`) is widened by **zero**; preferences never enter an `AiRequest`.
- Constants (verbatim): `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD = 3` (param `autoResolveStreakThreshold`);
  `MAX_QUERY_LENGTH = 64`; `MAX_RESOLUTION_PREFERENCES = 500`.
- `query` stored = normalized slot only (`CommandNormalizer` + trim + lowercase, non-empty, ≤ 64);
  `query`/packageName are **local-sensitive metadata** and must never be logged to external/crash channels.
- Build runs under the pinned JDK 17 (see `~/.gradle/gradle.properties`). **Verification commands** used
  throughout (run from repo root):
  - Domain tests: `env -u JAVA_HOME ./gradlew --no-daemon :domain:test`
  - Data tests: `env -u JAVA_HOME ./gradlew --no-daemon :data:repository:testDebugUnitTest`
  - Launcher tests: `env -u JAVA_HOME ./gradlew --no-daemon :feature:launcher:testDebugUnitTest`
  - Settings tests: `env -u JAVA_HOME ./gradlew --no-daemon :feature:settings:testDebugUnitTest`
  - Build: `env -u JAVA_HOME ./gradlew --no-daemon :app:assembleDebug`

## File Structure (decomposition)

**Phase A — domain (pure, additive, zero behavior change until Phase C):** `domain/memory/resolution/`
— `Model.kt` (value types + constants), `ResolutionPreferenceStore.kt` (port), `ResolutionDecision.kt`,
`ResolutionPreferencePolicy.kt` (+ `DefaultResolutionPreferencePolicy`), `RecordResolutionChoiceUseCase.kt`,
`LearnedChoiceDisplayState.kt` + `EvaluateLearnedChoiceDisplayStateUseCase.kt`,
`LearnedChoiceView.kt` + `ObserveLearnedChoicesUseCase.kt` / `DeleteLearnedChoiceUseCase.kt` /
`PruneUnavailableLearnedChoicesUseCase.kt`, `ResolveCommandWithPreferenceUseCase.kt` (+ `ResolvedCommand`).
Fake in `:core:testing`.

**Phase B — persistence (`:data:repository`):** `db/entity/ResolutionPreferenceEntity.kt`,
`db/dao/ResolutionPreferenceDao.kt`, `db/ResolutionPreferenceStoreImpl.kt` (+ mapper),
`db/migrations/Migration1To2.kt`, `SidrDatabase` v2, golden `schemas/…/2.json`, guard-test updates.

**Phase C — wiring & DI (deferred group):** `:app` DI providers; `:feature:launcher` VM wiring.

**Phase D — management UI:** `core/common` `Routes.LearnedChoices`; `core/ui`
`LearnedChoiceRow.kt`; `:feature:settings` `LearnedChoicesViewModel.kt` + `LearnedChoicesScreen.kt` +
Settings entry row + NavHost registration in `:app`.

**Phase E — guards, parity, acceptance + docs.**

---

## Phase A — Domain

### Task 1: Domain value types, constants, store port

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/Model.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionPreferenceStore.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/CandidateSetFingerprintTest.kt`

**Interfaces produced:** `CapabilityKey`, `ResolutionContext`, `ResolvedTarget`, `CandidateSet`,
`CandidateSetFingerprint` + `fingerprintOf(CandidateSet)`, `PreferenceEvidence`, `ResolutionPreference`,
`ResolutionLearningToken`, constants `DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD`, `MAX_QUERY_LENGTH`,
`MAX_RESOLUTION_PREFERENCES`, `ResolutionPreferenceStore`.

- [ ] **Step 1: Write the failing test** — `CandidateSetFingerprintTest.kt`:

```kotlin
package com.sidr.launcher.domain.memory.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CandidateSetFingerprintTest {
    private fun app(p: String) = ResolvedTarget.App(p)

    @Test fun `fingerprint is order-independent`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        val b = CandidateSet(listOf(app("com.b"), app("com.a")))
        assertEquals(fingerprintOf(a), fingerprintOf(b))
    }

    @Test fun `fingerprint changes when the set changes`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        val c = CandidateSet(listOf(app("com.a"), app("com.c")))
        assertNotEquals(fingerprintOf(a), fingerprintOf(c))
    }

    @Test fun `fingerprint is stable across calls`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        assertEquals(fingerprintOf(a), fingerprintOf(a))
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `:domain:test` → FAIL (`fingerprintOf` unresolved).

- [ ] **Step 3: Create `Model.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId

/** v1 default: number of consecutive consistent explicit choices before auto-resolve is eligible. */
const val DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD: Int = 3
/** Max stored normalized-slot length; longer queries are never persisted (privacy + sanity). */
const val MAX_QUERY_LENGTH: Int = 64
/** Safety-net cap on stored preferences; LRU-evicted by lastChosenAt when exceeded. */
const val MAX_RESOLUTION_PREFERENCES: Int = 500

/** WHAT the user is trying to do, independent of which target. v1 = (action family + normalized slot). */
data class CapabilityKey(val actionId: ActionId, val query: String)

/** WHERE/WHEN. v1 degenerate; grows (time/place) without signature churn. */
sealed interface ResolutionContext { data object None : ResolutionContext }

/** The chosen thing. v1 = app only; sealed so it generalizes without breaking ports. */
sealed interface ResolvedTarget { data class App(val packageName: String) : ResolvedTarget }

/** The offered candidates — for validation + change-awareness, not identity. */
data class CandidateSet(val targets: List<ResolvedTarget>)

@JvmInline value class CandidateSetFingerprint(val value: String)

/** Deterministic, order-independent fingerprint of the candidate target ids. NOT anonymization. */
fun fingerprintOf(set: CandidateSet): CandidateSetFingerprint =
    CandidateSetFingerprint(
        set.targets
            .map { (it as ResolvedTarget.App).packageName }
            .sorted()
            .joinToString("|"),
    )

/** Raw deterministic evidence the policy interprets. */
data class PreferenceEvidence(val streak: Int, val totalChoices: Int, val lastChosenAtEpochMs: Long)

data class ResolutionPreference(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val preferredTarget: ResolvedTarget,
    val evidence: PreferenceEvidence,
    val learnedInSetFingerprint: CandidateSetFingerprint,
)

/** Opaque TRANSIENT token an ambiguous outcome carries so the VM records a choice without building a key. */
data class ResolutionLearningToken(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val candidateSet: CandidateSet,
    val fingerprint: CandidateSetFingerprint,
    val isAppAmbiguityFlow: Boolean,
)
```

- [ ] **Step 4: Create `ResolutionPreferenceStore.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface ResolutionPreferenceStore {
    suspend fun find(key: CapabilityKey, context: ResolutionContext): OperationResult<ResolutionPreference?>
    suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit>
    suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit>
    fun observeAll(): Flow<List<ResolutionPreference>>
}
```

- [ ] **Step 5: Run tests to verify they pass** — `:domain:test` → PASS.
- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/Model.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionPreferenceStore.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/CandidateSetFingerprintTest.kt
git commit -m "feat(s2-1): resolution-memory domain value types + store port + fingerprint"
```

**Invariants:** pure Kotlin (only `domain.action.ActionId` + `domain.result.OperationResult` imports); no
consumer yet ⇒ zero behavior change. **DoD:** `:domain:test` green; `fingerprintOf` order-independent.

---

### Task 2: `ResolutionPreferencePolicy` (pure decision)

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionDecision.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionPreferencePolicy.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/DefaultResolutionPreferencePolicyTest.kt`

**Interfaces:** Consumes Task 1 types + `ActionRiskLevel`. Produces `ResolutionDecision` (`NoPreference`,
`Stale`, `RankFirst`, `AutoResolve`), `PreferenceStrength`, `ResolutionPreferencePolicy.decide(...)`,
`DefaultResolutionPreferencePolicy(autoResolveStreakThreshold)`.

- [ ] **Step 1: Write the failing test** (covers every decision-table row + boundaries):

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultResolutionPreferencePolicyTest {
    private val policy = DefaultResolutionPreferencePolicy(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun set(vararg p: String) = CandidateSet(p.map { app(it) })
    private fun pref(target: String, streak: Int, learnedSet: CandidateSet) = ResolutionPreference(
        capabilityKey = key, context = ResolutionContext.None, preferredTarget = app(target),
        evidence = PreferenceEvidence(streak, streak, 0L), learnedInSetFingerprint = fingerprintOf(learnedSet),
    )

    @Test fun `null preference is NoPreference`() {
        assertEquals(ResolutionDecision.NoPreference,
            policy.decide(null, set("com.a", "com.b"), ActionRiskLevel.SAFE))
    }

    @Test fun `target not in candidates is Stale`() {
        val p = pref("com.gone", streak = 5, learnedSet = set("com.gone", "com.b"))
        val d = policy.decide(p, set("com.a", "com.b"), ActionRiskLevel.SAFE)
        assertTrue(d is ResolutionDecision.Stale)
    }

    @Test fun `confident + SAFE + same fingerprint is AutoResolve`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", streak = 3, learnedSet = s), s, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.AutoResolve(app("com.a")), d)
    }

    @Test fun `below threshold is RankFirst`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", streak = 2, learnedSet = s), s, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }

    @Test fun `exactly K is confident (boundary)`() {
        val s = set("com.a", "com.b")
        assertTrue(policy.decide(pref("com.a", 3, s), s, ActionRiskLevel.SAFE) is ResolutionDecision.AutoResolve)
        assertTrue(policy.decide(pref("com.a", 2, s), s, ActionRiskLevel.SAFE) is ResolutionDecision.RankFirst)
    }

    @Test fun `confident but CONFIRM risk is RankFirst never AutoResolve`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", 9, s), s, ActionRiskLevel.CONFIRM)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }

    @Test fun `confident + SAFE but fingerprint changed is RankFirst`() {
        val learned = set("com.a", "com.b")
        val current = set("com.a", "com.c")   // set changed → demote, don't erase
        val d = policy.decide(pref("com.a", 9, learned), current, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 3: Create `ResolutionDecision.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

sealed interface ResolutionDecision {
    data object NoPreference : ResolutionDecision
    data class Stale(val preference: ResolutionPreference) : ResolutionDecision
    data class RankFirst(val target: ResolvedTarget) : ResolutionDecision
    data class AutoResolve(val target: ResolvedTarget) : ResolutionDecision
}

enum class PreferenceStrength { WEAK, CONFIDENT }
```

- [ ] **Step 4: Create `ResolutionPreferencePolicy.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel

interface ResolutionPreferencePolicy {
    fun decide(
        preference: ResolutionPreference?,
        candidates: CandidateSet,
        risk: ActionRiskLevel,
    ): ResolutionDecision
}

class DefaultResolutionPreferencePolicy(
    private val autoResolveStreakThreshold: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
) : ResolutionPreferencePolicy {

    /** Single confidence evolution point. v1: streak threshold. */
    private fun strength(e: PreferenceEvidence): PreferenceStrength =
        if (e.streak >= autoResolveStreakThreshold) PreferenceStrength.CONFIDENT else PreferenceStrength.WEAK

    override fun decide(
        preference: ResolutionPreference?,
        candidates: CandidateSet,
        risk: ActionRiskLevel,
    ): ResolutionDecision {
        if (preference == null) return ResolutionDecision.NoPreference
        if (preference.preferredTarget !in candidates.targets) return ResolutionDecision.Stale(preference)
        val eligible = strength(preference.evidence) == PreferenceStrength.CONFIDENT &&
            risk == ActionRiskLevel.SAFE &&
            fingerprintOf(candidates) == preference.learnedInSetFingerprint
        return if (eligible) ResolutionDecision.AutoResolve(preference.preferredTarget)
        else ResolutionDecision.RankFirst(preference.preferredTarget)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — `:domain:test` → PASS.
- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionDecision.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolutionPreferencePolicy.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/DefaultResolutionPreferencePolicyTest.kt
git commit -m "feat(s2-1): deterministic ResolutionPreferencePolicy (SAFE+CONFIDENT+fingerprint gate)"
```

**Invariants:** pure, total, side-effect-free; CONFIRM/RISKY never AutoResolve; fingerprint mismatch
demotes to RankFirst. **DoD:** `:domain:test` green; every decision-table row covered.

---

### Task 3: `FakeResolutionPreferenceStore` + `RecordResolutionChoiceUseCase`

**Files:**
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeResolutionPreferenceStore.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/RecordResolutionChoiceUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/RecordResolutionChoiceUseCaseTest.kt`

**Interfaces:** Consumes Task 1 store port + types. Produces `RecordResolutionChoiceUseCase.record(...)`
and `FakeResolutionPreferenceStore` (in-memory, `failWrites`/`failReads` toggles).

- [ ] **Step 1: Create `FakeResolutionPreferenceStore.kt`** (`:core:testing`):

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.memory.resolution.*
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

class FakeResolutionPreferenceStore(
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
) : ResolutionPreferenceStore {
    private val state = MutableStateFlow<List<ResolutionPreference>>(emptyList())
    private fun keyOf(p: ResolutionPreference) = p.capabilityKey to p.context

    override suspend fun find(key: CapabilityKey, context: ResolutionContext): OperationResult<ResolutionPreference?> =
        if (failReads) OperationResult.Failure(OperationError.Unknown("io"))
        else OperationResult.Success(state.value.firstOrNull { it.capabilityKey == key && it.context == context })

    override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.Unknown("io"))
        state.value = state.value.filterNot { keyOf(it) == keyOf(preference) } + preference
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.Unknown("io"))
        state.value = state.value.filterNot { it.capabilityKey == key && it.context == context }
        return OperationResult.Success(Unit)
    }

    override fun observeAll(): Flow<List<ResolutionPreference>> = state
}
```

> Confirm the exact `OperationError` variant/constructor by reading
> `domain/src/main/java/com/sidr/launcher/domain/result/OperationResult.kt` before writing (use whatever
> generic error the codebase exposes; the tests only check `is Failure`).

- [ ] **Step 2: Write the failing test** — `RecordResolutionChoiceUseCaseTest.kt`:

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordResolutionChoiceUseCaseTest {
    private val store = FakeResolutionPreferenceStore()
    private val useCase = RecordResolutionChoiceUseCase(store)
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun app(p: String) = ResolvedTarget.App(p)
    private val candidates = CandidateSet(listOf(app("com.a"), app("com.b")))

    private suspend fun current() = (store.find(key, ResolutionContext.None) as
        com.sidr.launcher.domain.result.OperationResult.Success).value

    @Test fun `first choice creates streak 1`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        val p = current()!!
        assertEquals(app("com.a"), p.preferredTarget); assertEquals(1, p.evidence.streak)
        assertEquals(1, p.evidence.totalChoices)
    }

    @Test fun `same choice reinforces (streak++ , fingerprint updated)`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        val newer = CandidateSet(listOf(app("com.a"), app("com.b"), app("com.c")))
        useCase.record(key, ResolutionContext.None, app("com.a"), newer)
        val p = current()!!
        assertEquals(2, p.evidence.streak); assertEquals(2, p.evidence.totalChoices)
        assertEquals(fingerprintOf(newer), p.learnedInSetFingerprint)
    }

    @Test fun `different choice hard-switches (preferred=new, streak=1, total++)`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates) // streak 2
        useCase.record(key, ResolutionContext.None, app("com.b"), candidates) // correction
        val p = current()!!
        assertEquals(app("com.b"), p.preferredTarget); assertEquals(1, p.evidence.streak)
        assertEquals(3, p.evidence.totalChoices)
    }

    @Test fun `empty query is never recorded`() = runTest {
        useCase.record(CapabilityKey(ActionId("launch_app"), "  "), ResolutionContext.None, app("com.a"), candidates)
        assertNull((store.find(CapabilityKey(ActionId("launch_app"), ""), ResolutionContext.None) as
            com.sidr.launcher.domain.result.OperationResult.Success).value)
    }

    @Test fun `store write failure does not throw`() = runTest {
        store.failWrites = true
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates) // must not throw
    }
}
```

- [ ] **Step 3: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 4: Create `RecordResolutionChoiceUseCase.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class RecordResolutionChoiceUseCase(private val store: ResolutionPreferenceStore) {
    /**
     * Records an explicit candidate choice. Deterministic evidence update (create / reinforce /
     * hard-switch). Never throws (rethrows CancellationException); an empty/over-length query is a no-op.
     */
    suspend fun record(
        key: CapabilityKey,
        context: ResolutionContext,
        chosen: ResolvedTarget,
        candidates: CandidateSet,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        if (key.query.isBlank() || key.query.length > MAX_QUERY_LENGTH) return
        try {
            val existing = (store.find(key, context) as? OperationResult.Success)?.value
            val fp = fingerprintOf(candidates)
            val evidence = when {
                existing == null -> PreferenceEvidence(streak = 1, totalChoices = 1, lastChosenAtEpochMs = nowEpochMs)
                existing.preferredTarget == chosen -> existing.evidence.copy(
                    streak = existing.evidence.streak + 1,
                    totalChoices = existing.evidence.totalChoices + 1,
                    lastChosenAtEpochMs = nowEpochMs,
                )
                else -> PreferenceEvidence(streak = 1, totalChoices = existing.evidence.totalChoices + 1, lastChosenAtEpochMs = nowEpochMs)
            }
            store.upsert(
                ResolutionPreference(
                    capabilityKey = key, context = context, preferredTarget = chosen,
                    evidence = evidence, learnedInSetFingerprint = fp,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // best-effort — "not learned this time"; never propagate.
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — `:domain:test` → PASS.
- [ ] **Step 6: Commit**

```bash
git add core/testing/src/main/java/com/sidr/launcher/core/testing/FakeResolutionPreferenceStore.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/RecordResolutionChoiceUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/RecordResolutionChoiceUseCaseTest.kt
git commit -m "feat(s2-1): RecordResolutionChoiceUseCase (create/reinforce/hard-switch) + fake store"
```

**Invariants:** learn math in domain; empty/over-length query → no-op; never throws. **DoD:** `:domain:test`
green.

---

### Task 4: Display-state use-case (honest label)

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LearnedChoiceDisplayState.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/EvaluateLearnedChoiceDisplayStateUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/EvaluateLearnedChoiceDisplayStateUseCaseTest.kt`

**Interfaces:** Consumes Task 1/2 types + `ActionRiskLevel`. Produces `LearnedChoiceDisplayState`
(`Unavailable`/`Learning(streak,threshold)`/`NeedsReconfirm`/`Auto`/`AutoReady`) and
`EvaluateLearnedChoiceDisplayStateUseCase.evaluate(preference, currentCandidates?, risk, targetInstalled)`.

Rationale: the label must match the policy. `Auto` is shown **iff** the same primitives the policy checks
are verifiable here; if `currentCandidates == null` (set could not be reconstructed on the management
screen) it degrades to `AutoReady` rather than promising `auto`.

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateLearnedChoiceDisplayStateUseCaseTest {
    private val useCase = EvaluateLearnedChoiceDisplayStateUseCase(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private fun set(vararg p: String) = CandidateSet(p.map { app(it) })
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun pref(streak: Int, learned: CandidateSet, target: String = "com.a") = ResolutionPreference(
        key, ResolutionContext.None, app(target), PreferenceEvidence(streak, streak, 0L), fingerprintOf(learned))

    @Test fun `not installed is Unavailable`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Unavailable,
            useCase.evaluate(pref(9, s), s, ActionRiskLevel.SAFE, targetInstalled = false))
    }

    @Test fun `below threshold is Learning`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Learning(2, 3),
            useCase.evaluate(pref(2, s), s, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident but fingerprint changed is NeedsReconfirm`() {
        val learned = set("com.a", "com.b"); val current = set("com.a", "com.c")
        assertEquals(LearnedChoiceDisplayState.NeedsReconfirm,
            useCase.evaluate(pref(9, learned), current, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident + safe + same set + installed is Auto`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Auto,
            useCase.evaluate(pref(9, s), s, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident + safe + installed but set unknown is AutoReady`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.AutoReady,
            useCase.evaluate(pref(9, s), currentCandidates = null, ActionRiskLevel.SAFE, targetInstalled = true))
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 3: Create `LearnedChoiceDisplayState.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

sealed interface LearnedChoiceDisplayState {
    data object Unavailable : LearnedChoiceDisplayState
    data class Learning(val streak: Int, val threshold: Int) : LearnedChoiceDisplayState
    data object NeedsReconfirm : LearnedChoiceDisplayState
    data object Auto : LearnedChoiceDisplayState
    data object AutoReady : LearnedChoiceDisplayState
}
```

- [ ] **Step 4: Create `EvaluateLearnedChoiceDisplayStateUseCase.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel

class EvaluateLearnedChoiceDisplayStateUseCase(
    private val autoResolveStreakThreshold: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
) {
    /**
     * @param currentCandidates the reconstructed live candidate set, or null if it could not be
     *   reliably reconstructed on the management screen (→ degrade Auto to AutoReady).
     */
    fun evaluate(
        preference: ResolutionPreference,
        currentCandidates: CandidateSet?,
        risk: ActionRiskLevel,
        targetInstalled: Boolean,
    ): LearnedChoiceDisplayState {
        if (!targetInstalled) return LearnedChoiceDisplayState.Unavailable
        val confident = preference.evidence.streak >= autoResolveStreakThreshold
        if (!confident) return LearnedChoiceDisplayState.Learning(preference.evidence.streak, autoResolveStreakThreshold)
        if (risk != ActionRiskLevel.SAFE) return LearnedChoiceDisplayState.NeedsReconfirm
        if (currentCandidates == null) return LearnedChoiceDisplayState.AutoReady
        val setConsistent = fingerprintOf(currentCandidates) == preference.learnedInSetFingerprint &&
            preference.preferredTarget in currentCandidates.targets
        return if (setConsistent) LearnedChoiceDisplayState.Auto else LearnedChoiceDisplayState.NeedsReconfirm
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — `:domain:test` → PASS.
- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LearnedChoiceDisplayState.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/EvaluateLearnedChoiceDisplayStateUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/EvaluateLearnedChoiceDisplayStateUseCaseTest.kt
git commit -m "feat(s2-1): honest display-state use-case (Auto only when policy-verifiable, else AutoReady)"
```

**Invariants:** display `Auto` ⟺ policy would `AutoResolve`; unverifiable set → `AutoReady`. **DoD:**
`:domain:test` green.

---

### Task 5: Observe / Delete / Prune use-cases + `LearnedChoiceView`

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LearnedChoiceView.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ObserveLearnedChoicesUseCase.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/DeleteLearnedChoiceUseCase.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/PruneUnavailableLearnedChoicesUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/LearnedChoiceUseCasesTest.kt`

**Interfaces:** Consumes the store + `InstalledAppsRepository` (read `installedPackages(): Set<String>` /
label lookup — confirm the exact method by reading
`domain/.../InstalledAppsRepository.kt`). Produces `LearnedChoiceView(capabilityKey, targetPackageName,
targetLabel, displayState)`, `ObserveLearnedChoicesUseCase.observe(): Flow<List<LearnedChoiceView>>`,
`DeleteLearnedChoiceUseCase.delete(key, context)`, `PruneUnavailableLearnedChoicesUseCase.prune()`.

- [ ] **Step 1: Write the failing test** (`observe` joins installed apps → view + display state; prune
  deletes rows whose target is not installed; delete removes one). Use `FakeResolutionPreferenceStore` +
  the existing `FakeInstalledAppsRepository` from `:core:testing`:

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedChoiceUseCasesTest {
    private val store = FakeResolutionPreferenceStore()
    private val apps = FakeInstalledAppsRepository()  // seed com.a (installed); com.gone absent
    private fun app(p: String) = ResolvedTarget.App(p)
    private val keyA = CapabilityKey(ActionId("launch_app"), "bank")

    @Test fun `observe emits a view only for known state`() = runTest {
        // seed store with one installed-target preference; assert the view carries its label + a state.
        // (fill from FakeInstalledAppsRepository's seeded label)
        // ...
        assertTrue(true) // replace with concrete assertions once InstalledAppsRepository shape is confirmed
    }
}
```

> **Implementer note:** Task 5's exact test bodies depend on `InstalledAppsRepository`'s method names
> (`installedPackages` / label accessor) and `FakeInstalledAppsRepository`'s seeding API. **Step 0 of this
> task:** read both files, then write concrete assertions (installed → view with label + `Learning`/`Auto`
> per evidence; uninstalled → excluded by `observe`; `prune()` deletes the uninstalled row; `delete()`
> removes exactly one). Do not leave the `assertTrue(true)` placeholder in the committed test.

- [ ] **Step 2: Create `LearnedChoiceView.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

data class LearnedChoiceView(
    val capabilityKey: CapabilityKey,
    val targetPackageName: String,
    val targetLabel: String,
    val displayState: LearnedChoiceDisplayState,
)
```

- [ ] **Step 3: Create the three use-cases.** `ObserveLearnedChoicesUseCase` maps
  `store.observeAll()` → joins installed-app label/availability → filters out uninstalled → derives
  `displayState` via `EvaluateLearnedChoiceDisplayStateUseCase` (with `currentCandidates = null` at this
  layer unless the caller supplies a reconstruction; v1 passes `null` → `AutoReady`). `DeleteLearnedChoiceUseCase`
  delegates to `store.delete`. `PruneUnavailableLearnedChoicesUseCase` reads current install set and calls
  `store.delete` for each preference whose target is absent. Show the concrete code once the
  `InstalledAppsRepository` accessor names are confirmed in Step 0.

- [ ] **Step 4: Run tests** — `:domain:test` → PASS. **Step 5: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LearnedChoice*.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ObserveLearnedChoicesUseCase.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/DeleteLearnedChoiceUseCase.kt \
        domain/src/main/java/com/sidr/launcher/domain/memory/resolution/PruneUnavailableLearnedChoicesUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/LearnedChoiceUseCasesTest.kt
git commit -m "feat(s2-1): observe/delete/prune learned-choice use-cases + view model"
```

**Invariants:** icons are NOT in domain (label/packageName/availability only); observe never surfaces
uninstalled targets. **DoD:** `:domain:test` green; no placeholder assertions committed.

---

### Task 6: Read-path decorator `ResolveCommandWithPreferenceUseCase`

> **⚠ OPEN INTEGRATION FORK — confirm with owner before implementing (see hand-off).** `LauncherAction.LaunchApp`
> takes a *query* (re-resolves), so AutoResolve of a **specific** package cannot go through it. v1 default:
> the decorator returns a `ResolvedCommand` whose `autoLaunch` field names the package; the **VM launches it
> via its existing `launchApp(package, activity)`** path (launch mechanics already live in the VM), and the
> decorator also carries a `fallbackOutcome` (reordered `NeedsConfirmation`) the VM renders if that launch
> fails. Alternative: add a domain `LaunchResolvedAppUseCase` wrapping the executor for a specific app
> (decision+exec+fallback fully in domain, more code). This plan encodes the v1 default.

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCase.kt`
  (defines `ResolvedCommand`)
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCaseTest.kt`

**Interfaces:** Consumes `RouteCommandUseCase` (call `route(rawInput): CommandOutcome`),
`ResolutionPreferenceStore`, `ResolutionPreferencePolicy`, `CommandNormalizer`, `ActionCatalog` (risk for
LAUNCH_APP). Produces `ResolvedCommand(outcome, learningToken, autoLaunch, fallbackOutcome)` and
`ResolveCommandWithPreferenceUseCase.route(rawInput): ResolvedCommand`.

Behavior: call `routeCommand.route(rawInput)`. If the outcome is **not** `NeedsConfirmation`, return
`ResolvedCommand(outcome, null, null, null)` (parity). If it is `NeedsConfirmation(candidates)`:
build `CapabilityKey(LAUNCH_APP, normalize(rawInput))`, `CandidateSet` from candidate packageNames,
`risk` from catalog; `store.find` → `policy.decide`. Map:
- `NoPreference` → `ResolvedCommand(originalOutcome, token, null, null)`.
- `Stale(p)` → best-effort `store.delete`; `ResolvedCommand(originalOutcome, token, null, null)`.
- `RankFirst(t)` → `ResolvedCommand(NeedsConfirmation(reordered t-first), token, null, null)`.
- `AutoResolve(App(pkg))` → `ResolvedCommand(CommandOutcome.Executed, token=null, autoLaunch=App(pkg),
  fallbackOutcome=NeedsConfirmation(reordered t-first))`.

`token` (for the non-auto branches) = `ResolutionLearningToken(key, None, candidateSet, fingerprint,
isAppAmbiguityFlow=true)`.

- [ ] **Step 1: Write the failing test** (fakes for store/policy/route; assert each branch):

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCommandWithPreferenceUseCaseTest {
    // Build the use-case with: a fake RouteCommandUseCase returning a fixed CommandOutcome; a
    // FakeResolutionPreferenceStore; DefaultResolutionPreferencePolicy; the real CommandNormalizer;
    // a fake ActionCatalog returning SAFE for LAUNCH_APP.
    // (wire per the confirmed constructors)

    @Test fun `non-ambiguous outcome passes through unchanged (parity)`() = runTest {
        // route returns CommandOutcome.Executed → ResolvedCommand.outcome == Executed, token null, autoLaunch null
    }
    @Test fun `ambiguous + no preference returns original list + a learning token`() = runTest { }
    @Test fun `RankFirst reorders candidates preferred-first`() = runTest { }
    @Test fun `AutoResolve yields Executed + autoLaunch + reordered fallback`() = runTest { }
    @Test fun `Stale prunes and returns original list`() = runTest { }
}
```

> **Implementer:** flesh each `@Test` with concrete `InstalledApp` candidates and assertions. Confirm
> `RouteCommandUseCase`/`CommandNormalizer`/`ActionCatalog` constructor + method names first
> (`RouteCommandUseCase.route`, `CommandNormalizer.normalize` or equivalent, `ActionCatalog` risk lookup).
> No empty-body tests may be committed.

- [ ] **Step 2–5:** verify red → implement `ResolvedCommand` + the use-case per the mapping above → verify
  green (`:domain:test`) → commit.

```bash
git commit -m "feat(s2-1): ResolveCommandWithPreferenceUseCase (rank-first / auto-resolve / stale, parity)"
```

**Invariants:** non-`NeedsConfirmation` outcomes pass through untouched (parity); no LLM involved;
`AutoResolve` never records; token only on non-auto branches. **DoD:** `:domain:test` green; parity branch
asserted.

---

## Phase B — Persistence (Room)

> **Gated:** this phase changes the Room schema; do not start until Phase A is merged and the owner has
> cleared schema changes.

### Task 7: Room entity + DAO

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/ResolutionPreferenceEntity.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/dao/ResolutionPreferenceDao.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/db/ResolutionPreferenceDaoTest.kt` (Robolectric)

**Interfaces:** Produces `ResolutionPreferenceEntity` (composite PK) + `ResolutionPreferenceDao`.

- [ ] **Step 1: Write the failing Robolectric DAO test** (round-trip; upsert-replace on same PK;
  `deleteByKey`; `deleteByTargetValue`; `count`; `deleteOldest`; `observeAll` order). Mirror the existing
  `IntentMatch` DAO test setup (`Room.inMemoryDatabaseBuilder`, `RobolectricTestRunner`).

- [ ] **Step 2: Verify red.** **Step 3: Create the entity:**

```kotlin
package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "resolution_preferences", primaryKeys = ["action_id", "query", "context_key"])
data class ResolutionPreferenceEntity(
    @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "context_key") val contextKey: String,   // v1 always "none"
    @ColumnInfo(name = "preferred_target_type") val preferredTargetType: String,   // "app"
    @ColumnInfo(name = "preferred_target_value") val preferredTargetValue: String, // packageName
    @ColumnInfo(name = "streak") val streak: Int,
    @ColumnInfo(name = "total_choices") val totalChoices: Int,
    @ColumnInfo(name = "last_chosen_at") val lastChosenAtEpochMs: Long,
    @ColumnInfo(name = "learned_in_fingerprint") val learnedInFingerprint: String,
)
```

- [ ] **Step 4: Create the DAO** (mirror `IntentMatchDao` idioms):

```kotlin
package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResolutionPreferenceDao {
    @Query("SELECT * FROM resolution_preferences WHERE action_id = :actionId AND query = :query AND context_key = :contextKey LIMIT 1")
    suspend fun findByKey(actionId: String, query: String, contextKey: String): ResolutionPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolutionPreferenceEntity)

    @Query("DELETE FROM resolution_preferences WHERE action_id = :actionId AND query = :query AND context_key = :contextKey")
    suspend fun deleteByKey(actionId: String, query: String, contextKey: String)

    @Query("DELETE FROM resolution_preferences WHERE preferred_target_value = :packageName")
    suspend fun deleteByTargetValue(packageName: String)

    @Query("SELECT * FROM resolution_preferences ORDER BY last_chosen_at DESC")
    fun observeAll(): Flow<List<ResolutionPreferenceEntity>>

    @Query("SELECT COUNT(*) FROM resolution_preferences")
    suspend fun count(): Int

    @Query("DELETE FROM resolution_preferences WHERE (action_id, query, context_key) IN " +
        "(SELECT action_id, query, context_key FROM resolution_preferences ORDER BY last_chosen_at ASC LIMIT :excess)")
    suspend fun deleteOldest(excess: Int)
}
```

- [ ] **Step 5: Verify green** (`:data:repository:testDebugUnitTest`). **Step 6: Commit.**

**Invariants:** composite PK (action_id, query, context_key); target as type/value. **DoD:** DAO test green.

### Task 8: Mapper + `ResolutionPreferenceStoreImpl`

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/ResolutionPreferenceStoreImpl.kt`
  (mapper inline or a small `ResolutionPreferenceMapper.kt`)
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/db/ResolutionPreferenceStoreImplTest.kt`

**Interfaces:** Produces `ResolutionPreferenceStoreImpl : ResolutionPreferenceStore`.

- [ ] **Steps (TDD):** test round-trip through the port; `find` returns `Success(null)` on absent and
  `Failure` on a thrown DAO error (never throws); `upsert` applies the cap
  (`if (count() > MAX_RESOLUTION_PREFERENCES) deleteOldest(count() - MAX_RESOLUTION_PREFERENCES)`);
  `delete`; `observeAll` maps entities → domain, skipping any row that fails to map (guarded). Implement
  `contextKey` serialization (`ResolutionContext.None → "none"`), target (`App → type="app"`,
  `value=packageName`), evidence. Mirror `IntentMatchHistoryRepositoryImpl`'s `OperationResult` +
  `CancellationException` + `@ApplicationScope` idioms (read that file first). Commit.

**Invariants:** never throws (rethrow Cancellation); read failure → `Failure`; cap enforced. **DoD:**
`:data:repository:testDebugUnitTest` green.

### Task 9: `SidrDatabase` v2 + migration + golden schema + guards

**Files:**
- Modify: `data/repository/.../db/SidrDatabase.kt` (add entity, `version = 2`, `abstract fun
  resolutionPreferenceDao()`)
- Create: `data/repository/.../db/migrations/Migration1To2.kt`
- Create golden: `data/repository/schemas/com.sidr.launcher.data.repository.db.SidrDatabase/2.json`
  (generated by the build with `exportSchema=true`; commit it)
- Modify: the `@ApplicationScope` DB builder in `:app` DI to add `.addMigrations(Migration1To2)`
- Modify: `RoomColumnNamesGuardTest` inventory (`RoomColumnNames.TABLE_NAMES` + column list) to include the
  new table + columns
- Test: `data/repository/src/androidTest/.../MigrationTest.kt` case v1→v2 (mirror the existing
  `MigrationTestHelper` case)

- [ ] **Steps:** write `Migration1To2` `CREATE TABLE resolution_preferences (...)` matching the entity
  (exact SQL from the generated `2.json`); register it; regenerate + commit `2.json`; extend the guard
  inventory; add the v1→v2 `MigrationTest`. Verify `:data:repository:testDebugUnitTest` +
  `:app:assembleDebug` green. Commit.

**Invariants:** release builds get no destructive fallback (a missing migration must fail loudly);
privacy guard covers the new table/columns. **DoD:** migration test + guard green; `2.json` committed.

---

## Phase C — Wiring & DI (deferred group)

> **Gated:** do not start until the owner clears use-case wiring. This is where behavior turns on.

### Task 10: DI providers (`:app`)

**Files:** Modify a `:app` Hilt module (new `MemoryProvidesModule.kt` / `MemoryBindsModule.kt`).

- [ ] Provide/bind: `ResolutionPreferenceStore` → `ResolutionPreferenceStoreImpl` (with the DAO from the
  DB + `@ApplicationScope`); `ResolutionPreferencePolicy` → `DefaultResolutionPreferencePolicy()`;
  the use-cases (`RecordResolutionChoiceUseCase`, `ResolveCommandWithPreferenceUseCase`,
  `ObserveLearnedChoicesUseCase`, `DeleteLearnedChoiceUseCase`, `PruneUnavailableLearnedChoicesUseCase`,
  `EvaluateLearnedChoiceDisplayStateUseCase`). Provide the DB `resolutionPreferenceDao()`. **Verify
  `:app:assembleDebug` (Hilt graph valid).** Commit. **DoD:** graph compiles; no behavior yet (nothing
  injects the new use-cases until Task 11).

### Task 11: Launcher VM wiring (read decorator + record on candidate tap)

**Files:** Modify `feature/launcher/.../LauncherViewModel.kt` (+ its test).

- [ ] Inject `ResolveCommandWithPreferenceUseCase` + `RecordResolutionChoiceUseCase`. In
  `onCommandSubmitted`: replace `routeCommand.route(text)` with `resolveCommand.route(text)`; on the result
  — if `autoLaunch != null` → `launchApp(pkg, activityName?)` and, on the executor's failure signal, render
  `fallbackOutcome`; else `applyOutcome(outcome)` and store `_pendingLearningToken = learningToken`. In
  `onAppClicked(app)`: after a **successful** launch, if `_pendingLearningToken != null` and
  `app.packageName` ∈ token candidate set → `recordResolutionChoice.record(token.capabilityKey,
  token.context, ResolvedTarget.App(app.packageName), token.candidateSet)` on the `@ApplicationScope`
  (fire-and-forget; rethrow Cancellation); clear the token. Clear the token on new submit / clear-input.
  **Parity:** `RouteCommandUseCase` stays the decorator's dependency; a null-store / no-preference decorator
  returns the original outcome, so existing `LauncherViewModelTest` passes unchanged.

- [ ] **Tests:** ambiguous → token set; candidate tap after success → record called (fake); grid tap (no
  token) → not recorded; auto-launch path launches the package; parity: existing suite unchanged. Add
  `resolveCommand`/`recordResolutionChoice` to the 4 VM test construction sites (fakes). Verify
  `:feature:launcher:testDebugUnitTest` + `:app:assembleDebug`. Commit.

**Invariants:** VM holds no decision logic (all in the decorator/use-cases); recording only from the
ambiguity flow after success; grid taps never record. **DoD:** launcher tests + build green; parity suite
unchanged.

---

## Phase D — Management UI

### Task 12: Route + `core/ui` row

**Files:** Modify `core/common/.../Routes.kt` (add `object LearnedChoices { const val ROUTE =
"learned_choices" }`); Create `core/ui/.../component/LearnedChoiceRow.kt` (terminal-styled row: `> query
→ label`, a state chip from `LearnedChoiceDisplayState`, `[ x ]` delete; strings + lambdas only, no
`domain → ui` edge — the feature maps state → label). Preview + a simple render. Commit
(`:app:assembleDebug`). **DoD:** builds; no `domain→ui` import.

### Task 13: `LearnedChoicesViewModel` (`:feature:settings`)

**Files:** Create `feature/settings/.../LearnedChoicesViewModel.kt` + test.

- [ ] Inject `ObserveLearnedChoicesUseCase`, `DeleteLearnedChoiceUseCase`,
  `PruneUnavailableLearnedChoicesUseCase` (NOT the store). Expose `StateFlow<LearnedChoicesUiState>` with a
  **guarded flow**: map `observe()` to a list state; `catch` → an error state (`couldn't load` + retry),
  never crash; skip a bad row. `onDelete(key)` → fire-and-forget delete. Call `prune()` on load. VM stays
  Android-free. **Tests:** observe error → error state (no crash); delete wiring; prune called. Verify
  `:feature:settings:testDebugUnitTest`. Commit.

### Task 14: `LearnedChoicesScreen` + Settings entry + NavHost

**Files:** Create `feature/settings/.../LearnedChoicesScreen.kt`; Modify `SettingsScreen.kt` (add a
`[ learned choices ]` row → `navigateTo(Routes.LearnedChoices.ROUTE)`); Modify the `:app` `AppNavHost`
(register `composable(Routes.LearnedChoices.ROUTE)`); resolve the app icon from packageName in the screen
(UI concern). Device-independent render + empty/error states. Verify `:app:assembleDebug`. Commit. **DoD:**
navigable Settings → Learned Choices; list + delete render; safe error state.

---

## Phase E — Guards, parity, acceptance

### Task 15: Privacy / scope / parity guard tests

**Files:** Create guard tests in `:domain` and/or `:data:repository`.

- [ ] **Outbound-allow-list guard:** assert `OutboundContextPolicy`'s allow-list set is exactly its prior
  value (byte-for-byte; the memory work adds nothing). **Dependency guard:** a test that scans the
  `domain/memory/resolution` sources for any `AiRequest`/generative import and asserts none (grep-style,
  precedent: existing vendor-neutrality greps). **Scope guard:** `RecordResolutionChoiceUseCase` no-ops on
  empty/over-length query (already in Task 3 — re-assert here as the scope contract) and recording is only
  reachable via a token with `isAppAmbiguityFlow = true`. **Privacy inventory:** confirm Task 9's
  `RoomColumnNames` update is asserted. Verify `:domain:test` + `:data:repository:testDebugUnitTest`.
  Commit.

**DoD:** all guards green; outbound allow-list provably unchanged.

### Task 16: Full build, device acceptance, docs

- [ ] **Build gate:** `env -u JAVA_HOME ./gradlew --no-daemon :domain:test testDebugUnitTest assembleDebug`
  → all green.
- [ ] **SM-A325F device acceptance** (install debug; drive the slice): ambiguous launch command (e.g. two
  apps matching a query) → choice → repeat → `learning n/K` visible in Settings → Learned Choices → after
  K consistent choices the command auto-resolves (no list) → learning-phase correction (pick the other
  candidate before K) switches the target → Settings → Learned Choices → delete → next command re-shows
  candidates (re-learn) → uninstall the target → the preference is invalidated (no stale auto-resolve; row
  gone from the screen). Capture screenshots.
- [ ] **Parity check on device:** with no learned preferences, typed commands behave exactly as before;
  non-ambiguous commands unaffected; router-off unaffected.
- [ ] **Docs:** append an ADR ("S2-1 Learned Resolutions complete") to `decisions.md`; sync `CLAUDE.md`
  Current goal + `current-status.md`; mark this plan done. Commit.

**DoD:** build green; device slice observed end-to-end; parity confirmed; ADR + docs synced.

---

## Self-Review (author checklist — completed)

- **Spec coverage:** every spec section maps to a task — types §3→T1, policy §4→T2, write §5.2→T3,
  display §8→T4, observe/delete/prune §8→T5, read path §5.1→T6, persistence §6→T7–T9, privacy §7→T9/T15,
  management UI §8→T12–T14, failure/fallback §9→spread across T3/T6/T8/T13, tests §10→each task, DoD §11→T16.
- **Placeholder scan:** two tasks (T5, T6) intentionally defer *exact test bodies* to a documented Step 0
  read of `InstalledAppsRepository` / `RouteCommandUseCase` shapes — flagged as "no empty-body / no
  `assertTrue(true)` may be committed." These are the only spots needing a read-first; every production
  code block is concrete. No `TBD`/"add error handling"/"handle edge cases".
- **Type consistency:** `fingerprintOf`, `ResolutionDecision`, `PreferenceEvidence(streak,totalChoices,
  lastChosenAtEpochMs)`, `RecordResolutionChoiceUseCase.record(key,context,chosen,candidates,now)`,
  `ResolvedCommand(outcome,learningToken,autoLaunch,fallbackOutcome)`, `LearnedChoiceDisplayState`
  (5 variants) are used consistently across tasks.
- **Open fork (T6):** AutoResolve of a specific package via the VM's `launchApp` vs a new domain
  `LaunchResolvedAppUseCase` — flagged for owner decision; v1 default encoded.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-06-learned-resolutions.md`.

**Do not execute yet** — the owner has deferred implementation, Room schema changes, and use-case wiring;
this plan is for approval first. One open integration fork (Task 6 AutoResolve execution path) needs an
owner decision before Phase C. After approval, the two execution options are:
1. **Subagent-Driven (recommended):** a fresh subagent per task with review between tasks.
2. **Inline Execution:** batch tasks in-session with checkpoints.
