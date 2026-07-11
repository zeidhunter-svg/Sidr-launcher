# Learned Resolutions (S2-1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.
>
> **STATUS: CLOSED (2026-07-11) — Tasks 1–16 done on `launcher-4`; build gate green; SM-A325F
> device acceptance passed with screenshots.** See ADR
> "2026-07-11 — S2-1 Learned Resolutions device accepted + closed" in
> [decisions.md](../../../ai-context/decisions.md). Originally: APPROVED (owner, 2026-07-06) after
> pre-flight edits + the v1-slot edit. Source of truth for behavior is the design spec:
> [2026-07-06-learned-resolutions-design.md](../specs/2026-07-06-learned-resolutions-design.md).

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

    @Test fun `target id is type-prefixed (stable across ResolvedTarget growth)`() {
        assertEquals("app:com.a", targetId(ResolvedTarget.App("com.a")))
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

/** Stable, type-prefixed id for a target — exhaustive `when`, no unsafe cast; grows with ResolvedTarget. */
fun targetId(target: ResolvedTarget): String = when (target) {
    is ResolvedTarget.App -> "app:${target.packageName}"
}

/** The app package for an [ResolvedTarget.App], or null for a non-app target. Exhaustive, cast-free. */
fun ResolvedTarget.appPackageOrNull(): String? = when (this) {
    is ResolvedTarget.App -> packageName
}

/** Deterministic, order-independent fingerprint of the candidate target ids. NOT anonymization. */
fun fingerprintOf(set: CandidateSet): CandidateSetFingerprint =
    CandidateSetFingerprint(set.targets.map(::targetId).sorted().joinToString("|"))

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
import org.junit.Assert.assertTrue
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

    @Test fun `store write failure returns Failure without throwing`() = runTest {
        store.failWrites = true
        val r = useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        assertTrue(r is com.sidr.launcher.domain.result.OperationResult.Failure)
    }
}
```

- [ ] **Step 3: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 4: Create `RecordResolutionChoiceUseCase.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class RecordResolutionChoiceUseCase(private val store: ResolutionPreferenceStore) {
    /**
     * Records an explicit candidate choice. Deterministic evidence update (create / reinforce /
     * hard-switch). Returns [OperationResult] per the global contract (the VM may ignore it in
     * fire-and-forget). Never throws (rethrows CancellationException); an empty/over-length query is a
     * no-op that returns [OperationResult.Success].
     */
    suspend fun record(
        key: CapabilityKey,
        context: ResolutionContext,
        chosen: ResolvedTarget,
        candidates: CandidateSet,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): OperationResult<Unit> {
        if (key.query.isBlank() || key.query.length > MAX_QUERY_LENGTH) return OperationResult.Success(Unit)
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
            return store.upsert(
                ResolutionPreference(
                    capabilityKey = key, context = context, preferredTarget = chosen,
                    evidence = evidence, learnedInSetFingerprint = fp,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return OperationResult.Failure(OperationError.Unknown(t.message ?: "record failed"))
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

**Confirmed signatures:** `InstalledAppsRepository.getInstalledApps(): OperationResult<List<InstalledApp>>`;
`InstalledApp(packageName, label, activityName? = null)`; `FakeInstalledAppsRepository { var appsToReturn }`.
v1 scope is LAUNCH_APP (SAFE), so `ObserveLearnedChoicesUseCase` passes `risk = ActionRiskLevel.SAFE` and
`currentCandidates = null` to the display use-case (→ confident rows read `AutoReady`; below-threshold read
`Learning`; uninstalled are filtered out). **Interfaces produced:** `LearnedChoiceView(capabilityKey,
targetPackageName, targetLabel, displayState)`, `ObserveLearnedChoicesUseCase.observe():
Flow<List<LearnedChoiceView>>`, `DeleteLearnedChoiceUseCase.delete(key, context): OperationResult<Unit>`,
`PruneUnavailableLearnedChoicesUseCase.prune(): OperationResult<Unit>`.

- [ ] **Step 1: Create `LearnedChoiceView.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

data class LearnedChoiceView(
    val capabilityKey: CapabilityKey,
    val targetPackageName: String,
    val targetLabel: String,
    val displayState: LearnedChoiceDisplayState,
)
```

- [ ] **Step 2: Write the failing test** — `LearnedChoiceUseCasesTest.kt`:

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnedChoiceUseCasesTest {
    private val store = FakeResolutionPreferenceStore()
    private val apps = FakeInstalledAppsRepository()
    private val display = EvaluateLearnedChoiceDisplayStateUseCase(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private fun key(q: String) = CapabilityKey(ActionId("launch_app"), q)
    private fun pref(q: String, target: String, streak: Int) = ResolutionPreference(
        key(q), ResolutionContext.None, app(target), PreferenceEvidence(streak, streak, 0L),
        fingerprintOf(CandidateSet(listOf(app(target)))))

    @Before fun setup() { apps.appsToReturn = listOf(InstalledApp("com.a", "MyBank")) }

    @Test fun `observe surfaces installed target with label and Learning state`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertEquals(1, list.size)
        assertEquals("MyBank", list[0].targetLabel)
        assertEquals("com.a", list[0].targetPackageName)
        assertEquals(LearnedChoiceDisplayState.Learning(1, 3), list[0].displayState)
    }

    @Test fun `observe reads AutoReady for a confident installed target`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 5))
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertEquals(LearnedChoiceDisplayState.AutoReady, list[0].displayState)
    }

    @Test fun `observe excludes an uninstalled target`() = runTest {
        store.upsert(pref("news", "com.gone", streak = 5)) // com.gone not in appsToReturn
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertTrue(list.isEmpty())
    }

    @Test fun `delete removes exactly one preference`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))
        DeleteLearnedChoiceUseCase(store).delete(key("bank"), ResolutionContext.None)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test fun `prune deletes rows whose target is not installed`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))     // installed → kept
        store.upsert(pref("news", "com.gone", streak = 5))  // absent → pruned
        PruneUnavailableLearnedChoicesUseCase(store, apps).prune()
        val remaining = store.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(app("com.a"), remaining[0].preferredTarget)
    }
}
```

- [ ] **Step 3: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 4: Create the three use-cases:**

```kotlin
// ObserveLearnedChoicesUseCase.kt
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveLearnedChoicesUseCase(
    private val store: ResolutionPreferenceStore,
    private val installedApps: InstalledAppsRepository,
    private val displayState: EvaluateLearnedChoiceDisplayStateUseCase,
) {
    fun observe(): Flow<List<LearnedChoiceView>> = store.observeAll().map { prefs ->
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        val byPkg = installed.associateBy { it.packageName }
        prefs.mapNotNull { pref ->
            val pkg = pref.preferredTarget.appPackageOrNull() ?: return@mapNotNull null
            val app = byPkg[pkg] ?: return@mapNotNull null // filter uninstalled
            LearnedChoiceView(
                capabilityKey = pref.capabilityKey,
                targetPackageName = pkg,
                targetLabel = app.label,
                // v1 scope = LAUNCH_APP (SAFE); currentCandidates unknown on this screen → AutoReady/Learning.
                displayState = displayState.evaluate(pref, currentCandidates = null, ActionRiskLevel.SAFE, targetInstalled = true),
            )
        }
    }
}
```

```kotlin
// DeleteLearnedChoiceUseCase.kt
package com.sidr.launcher.domain.memory.resolution
import com.sidr.launcher.domain.result.OperationResult
class DeleteLearnedChoiceUseCase(private val store: ResolutionPreferenceStore) {
    suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> = store.delete(key, context)
}
```

```kotlin
// PruneUnavailableLearnedChoicesUseCase.kt
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first

class PruneUnavailableLearnedChoicesUseCase(
    private val store: ResolutionPreferenceStore,
    private val installedApps: InstalledAppsRepository,
) {
    /** Best-effort: delete preferences whose target app is no longer installed. Never throws. */
    suspend fun prune(): OperationResult<Unit> {
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
            .map { it.packageName }.toSet()
        store.observeAll().first().forEach { pref ->
            val pkg = pref.preferredTarget.appPackageOrNull() ?: return@forEach
            if (pkg !in installed) store.delete(pref.capabilityKey, pref.context)
        }
        return OperationResult.Success(Unit)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — `:domain:test` → PASS. **Step 6: Commit**

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

### Task 6: Launch slot extractor + read-path decorator `ResolveCommandWithPreferenceUseCase`

> **Owner-decided fork (a):** `LauncherAction.LaunchApp` takes a *query* (re-resolves), so AutoResolve of a
> **specific** package cannot go through it. The decorator does **not** produce `Executed`; it returns a
> `ResolvedCommand.AutoLaunch(target, fallback)` **directive**, and the VM launches the specific package via
> its existing `launchApp(package, activity)` path. `CommandOutcome.Executed` appears only in the VM **after
> a successful launch** (its existing launch-success behavior). On launch failure the VM renders `fallback`
> (the reordered candidate list).

**Confirmed signatures:** `RouteCommandUseCase.route(rawInput): CommandOutcome` (concrete class — wrapped
behind a testable `CommandRouteStep` seam here); `CommandNormalizer.normalize(raw): String`;
`ActionCatalog.descriptor(id): ActionDescriptor?` with `.risk: ActionRiskLevel`; `ActionIds.LAUNCH_APP`;
`CommandOutcome.NeedsConfirmation(candidates: List<InstalledApp>)`.

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LaunchSlotExtractor.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCase.kt`
  (defines `ResolvedCommand` + `CommandRouteStep`)
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/LaunchSlotExtractorTest.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCaseTest.kt`

**Interfaces produced:** `LaunchSlotExtractor.slotOf(normalizedCommand): String`, `CommandRouteStep` (fun
interface), `ResolvedCommand` (`Outcome` | `AutoLaunch`),
`ResolveCommandWithPreferenceUseCase.resolve(rawInput): ResolvedCommand`.

> **v1 query = the normalized SLOT** (`LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(rawInput))`,
> e.g. `open bank` → `bank`, `open my bank app` → `bank`). This is a **narrow, deterministic** verb/filler
> strip for the LAUNCH_APP ambiguity flow only — **not** a general NLU/parser.

#### Part A — `LaunchSlotExtractor`

- [ ] **Step A1: Write the failing test** — `LaunchSlotExtractorTest.kt`:

```kotlin
package com.sidr.launcher.domain.memory.resolution

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchSlotExtractorTest {
    private fun slot(s: String) = LaunchSlotExtractor.slotOf(s)

    @Test fun `strips leading launch verb`() = assertEquals("bank", slot("open bank"))
    @Test fun `strips other launch verb`() = assertEquals("bank", slot("launch bank"))
    @Test fun `strips possessive and trailing app`() = assertEquals("bank", slot("open my bank app"))
    @Test fun `strips go to`() = assertEquals("bank", slot("go to bank"))
    @Test fun `keeps a bare slot`() = assertEquals("bank", slot("bank"))
    @Test fun `preserves a multi-word slot`() = assertEquals("bank of scotland", slot("open bank of scotland"))
    @Test fun `falls back when stripping empties it`() = assertEquals("open", slot("open"))
}
```

- [ ] **Step A2: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step A3: Create `LaunchSlotExtractor.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

/**
 * Minimal deterministic slot extractor for the LAUNCH_APP ambiguity flow ONLY — NOT a general NLU/parser.
 * From an already-normalized command it strips a leading launch verb + leading determiner/possessive and a
 * trailing "app"/"application", yielding the app slot. Falls back to the input if stripping empties it.
 *
 *   open bank → bank ; launch bank → bank ; open my bank app → bank ; go to bank → bank
 */
object LaunchSlotExtractor {
    private val LEADING = setOf(
        "open", "launch", "start", "run", "go", "to", "show", "get", "the", "a", "an", "my", "this",
    )
    private val TRAILING = setOf("app", "application")

    fun slotOf(normalizedCommand: String): String {
        val tokens = normalizedCommand.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.first() in LEADING) tokens.removeAt(0)
        while (tokens.isNotEmpty() && tokens.last() in TRAILING) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").ifBlank { normalizedCommand }
    }
}
```

- [ ] **Step A4: Run tests to verify they pass** — `:domain:test` → PASS. **Commit:**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/LaunchSlotExtractor.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/LaunchSlotExtractorTest.kt
git commit -m "feat(s2-1): narrow deterministic LaunchSlotExtractor (LAUNCH_APP ambiguity only)"
```

#### Part B — the decorator

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCommandWithPreferenceUseCaseTest {
    private val store = FakeResolutionPreferenceStore()
    private val policy = DefaultResolutionPreferencePolicy(autoResolveStreakThreshold = 3)
    private val catalog = FakeActionCatalog(listOf(
        ActionDescriptor(ActionIds.LAUNCH_APP, "Open app", "", ActionCategory.APP, ActionRiskLevel.SAFE)))
    private fun installed(p: String, l: String = p) = InstalledApp(p, l)
    private val ambiguous = CommandOutcome.NeedsConfirmation(listOf(installed("com.a", "A"), installed("com.b", "B")))
    private fun target(p: String) = ResolvedTarget.App(p)
    private val key = CapabilityKey(ActionIds.LAUNCH_APP, "bank")   // slotOf(normalize("open bank")) == "bank"
    private val candidates = CandidateSet(listOf(target("com.a"), target("com.b")))
    private fun useCase(routeReturns: CommandOutcome) =
        ResolveCommandWithPreferenceUseCase({ routeReturns }, store, policy, catalog)
    private suspend fun stored() = (store.find(key, ResolutionContext.None) as OperationResult.Success).value

    @Test fun `non-ambiguous outcome passes through unchanged (parity)`() = runTest {
        val r = useCase(CommandOutcome.Executed).resolve("show apps")
        assertEquals(ResolvedCommand.Outcome(CommandOutcome.Executed, null), r)
    }

    @Test fun `ambiguous + no preference returns original list + a learning token`() = runTest {
        val r = useCase(ambiguous).resolve("open bank") as ResolvedCommand.Outcome
        assertEquals(ambiguous, r.outcome)
        assertTrue(r.learningToken!!.isAppAmbiguityFlow)
        assertEquals(key, r.learningToken.capabilityKey)
    }

    @Test fun `RankFirst reorders candidates preferred-first (streak below K)`() = runTest {
        store.upsert(ResolutionPreference(key, ResolutionContext.None, target("com.b"),
            PreferenceEvidence(1, 1, 0L), fingerprintOf(candidates)))
        val r = useCase(ambiguous).resolve("open bank") as ResolvedCommand.Outcome
        val out = r.outcome as CommandOutcome.NeedsConfirmation
        assertEquals("com.b", out.candidates.first().packageName)
    }

    @Test fun `AutoResolve yields AutoLaunch directive with reordered fallback (never Executed)`() = runTest {
        store.upsert(ResolutionPreference(key, ResolutionContext.None, target("com.b"),
            PreferenceEvidence(3, 3, 0L), fingerprintOf(candidates)))
        val r = useCase(ambiguous).resolve("open bank")
        assertTrue(r is ResolvedCommand.AutoLaunch)
        r as ResolvedCommand.AutoLaunch
        assertEquals(target("com.b"), r.target)
        assertEquals("com.b", (r.fallback as CommandOutcome.NeedsConfirmation).candidates.first().packageName)
    }

    @Test fun `Stale prunes the record and returns the original list`() = runTest {
        store.upsert(ResolutionPreference(key, ResolutionContext.None, target("com.gone"),
            PreferenceEvidence(5, 5, 0L), fingerprintOf(CandidateSet(listOf(target("com.gone"))))))
        val r = useCase(ambiguous).resolve("open bank") as ResolvedCommand.Outcome
        assertEquals(ambiguous, r.outcome)
        assertNull(stored()) // pruned
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `:domain:test` → FAIL.

- [ ] **Step 3: Create `ResolveCommandWithPreferenceUseCase.kt`:**

```kotlin
package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.result.OperationResult

/** The underlying rule-first router, wrapped as a seam so the decorator is trivially testable. */
fun interface CommandRouteStep { suspend fun route(rawInput: String): CommandOutcome }

/** Result of preference-aware resolution. `AutoLaunch` is a directive — NOT an executed outcome. */
sealed interface ResolvedCommand {
    /** Render [outcome] as-is. [learningToken] is present only for an app-ambiguity list (for recording). */
    data class Outcome(val outcome: CommandOutcome, val learningToken: ResolutionLearningToken?) : ResolvedCommand
    /** Auto-resolve directive: the VM launches [target] via launchApp; on failure it renders [fallback]. */
    data class AutoLaunch(val target: ResolvedTarget.App, val fallback: CommandOutcome) : ResolvedCommand
}

class ResolveCommandWithPreferenceUseCase(
    private val route: CommandRouteStep,
    private val store: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
    private val catalog: ActionCatalog,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val outcome = route.route(rawInput)
        if (outcome !is CommandOutcome.NeedsConfirmation) return ResolvedCommand.Outcome(outcome, null)

        val candidates = CandidateSet(outcome.candidates.map { ResolvedTarget.App(it.packageName) })
        // v1 slot: narrow deterministic verb/filler strip for LAUNCH_APP ambiguity only (not a parser).
        val slot = LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(rawInput))
        val key = CapabilityKey(ActionIds.LAUNCH_APP, slot)
        val token = ResolutionLearningToken(
            capabilityKey = key, context = ResolutionContext.None, candidateSet = candidates,
            fingerprint = fingerprintOf(candidates), isAppAmbiguityFlow = true,
        )
        val risk = catalog.descriptor(ActionIds.LAUNCH_APP)?.risk ?: ActionRiskLevel.CONFIRM // fail-safe
        val pref = (store.find(key, ResolutionContext.None) as? OperationResult.Success)?.value

        return when (val decision = policy.decide(pref, candidates, risk)) {
            ResolutionDecision.NoPreference -> ResolvedCommand.Outcome(outcome, token)
            is ResolutionDecision.Stale -> {
                store.delete(key, ResolutionContext.None) // best-effort prune; result ignored
                ResolvedCommand.Outcome(outcome, token)
            }
            is ResolutionDecision.RankFirst -> ResolvedCommand.Outcome(reorder(outcome, decision.target), token)
            is ResolutionDecision.AutoResolve -> when (val t = decision.target) {
                is ResolvedTarget.App -> ResolvedCommand.AutoLaunch(t, reorder(outcome, t))
            }
        }
    }

    private fun reorder(outcome: CommandOutcome.NeedsConfirmation, first: ResolvedTarget): CommandOutcome.NeedsConfirmation {
        val pkg = first.appPackageOrNull()
        return CommandOutcome.NeedsConfirmation(outcome.candidates.sortedByDescending { it.packageName == pkg })
    }
}
```

- [ ] **Step 4: Run tests to verify they pass** — `:domain:test` → PASS.
- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/resolution/ResolveCommandWithPreferenceUseCaseTest.kt
git commit -m "feat(s2-1): ResolveCommandWithPreferenceUseCase (rank-first / auto-launch directive / stale, parity)"
```

**Invariants:** non-`NeedsConfirmation` passes through untouched (parity); no LLM involved; `AutoResolve`
becomes an `AutoLaunch` **directive** (never a premature `Executed`) and never records; a token rides only
the non-auto branches. **DoD:** `:domain:test` green; parity + AutoLaunch-not-Executed asserted.

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
  a `CommandRouteStep` provider that delegates to the existing `RouteCommandUseCase`
  (`CommandRouteStep { routeCommandUseCase.route(it) }`); the use-cases (`RecordResolutionChoiceUseCase`,
  `ResolveCommandWithPreferenceUseCase`, `ObserveLearnedChoicesUseCase`, `DeleteLearnedChoiceUseCase`,
  `PruneUnavailableLearnedChoicesUseCase`, `EvaluateLearnedChoiceDisplayStateUseCase`). Provide the DB
  `resolutionPreferenceDao()`. **Verify
  `:app:assembleDebug` (Hilt graph valid).** Commit. **DoD:** graph compiles; no behavior yet (nothing
  injects the new use-cases until Task 11).

### Task 11: Launcher VM wiring (read decorator + record on candidate tap)

**Files:** Modify `feature/launcher/.../LauncherViewModel.kt` (+ its test).

- [ ] Inject `ResolveCommandWithPreferenceUseCase` + `RecordResolutionChoiceUseCase`. In
  `onCommandSubmitted`: replace `routeCommand.route(text)` with `resolveCommand.resolve(text)` and branch on
  the sealed result:
  - `is ResolvedCommand.Outcome` → `applyOutcome(outcome)`; set `_pendingLearningToken = learningToken`
    (may be null for non-ambiguous).
  - `is ResolvedCommand.AutoLaunch` → `_pendingLearningToken = null`; call the existing
    `launchApp(target.packageName, activityName = <lookup or null>)`. **`Executed` semantics come only from
    `launchApp`'s existing success path** (it clears input on success). If `launchApp` reports failure,
    `applyOutcome(fallback)` (the reordered list). *(No premature `Executed`.)*
  In `onAppClicked(app)`: after a **successful** launch, if `_pendingLearningToken != null` and
  `app.packageName` ∈ token candidate set → `recordResolutionChoice.record(token.capabilityKey,
  token.context, ResolvedTarget.App(app.packageName), token.candidateSet)` on the `@ApplicationScope`
  (fire-and-forget — the returned `OperationResult` is ignored; rethrow Cancellation); clear the token.
  Clear the token on new submit / clear-input. **Parity:** the decorator delegates to `RouteCommandUseCase`
  via `CommandRouteStep`; a no-preference decorator returns `Outcome(originalOutcome, …)`, so existing
  `LauncherViewModelTest` passes unchanged.
  > *Implementer note:* confirm `launchApp`'s failure signal (it currently drives `CommandFeedback`); if it
  > has no direct success/failure return, thread one minimally or gate the fallback on the executor result
  > it already consumes. This is the only VM-internal detail to confirm against `launchApp`.

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

- [x] **Outbound-allow-list guard (Task 15, commit `59babbb`, 2026-07-10):** assert
  `OutboundContextPolicy`'s allow-list set is exactly its prior
  value (byte-for-byte; the memory work adds nothing). **Dependency guard:** a test that scans the
  `domain/memory/resolution` sources for any `AiRequest`/generative import and asserts none (grep-style,
  precedent: existing vendor-neutrality greps). **Scope guard:** `RecordResolutionChoiceUseCase` no-ops on
  empty/over-length query (already in Task 3 — re-assert here as the scope contract) and recording is only
  reachable via a token with `isAppAmbiguityFlow = true`. **Privacy inventory:** confirm Task 9's
  `RoomColumnNames` update is asserted. Verify `:domain:test` + `:data:repository:testDebugUnitTest`.
  Commit.

**DoD:** all guards green; outbound allow-list provably unchanged.

### Task 16: Full build, device acceptance, docs

- [x] **Build gate (2026-07-10):** `:domain:test` + `testDebugUnitTest` + `assembleDebug` → BUILD
  SUCCESSFUL (run under JBR 21 + a locally-downloaded JDK-17 toolchain
  `-Porg.gradle.java.installations.paths=…/jdk-17.0.19+10`, because the machine JDK had rolled to 25/26 —
  same env workaround as AIL-6, no repo change).
- [x] **SM-A325F device acceptance (2026-07-11):** installed the debug APK and drove the slice with two
  temporary same-label local fixture apps (`com.sidr.probe.a` / `com.sidr.probe.b`, label `SidrProbe`;
  both uninstalled after the run). Covered: ambiguous `open SidrProbe` → explicit choice →
  `learning 1/3` in Settings → repeated choices → subsequent command auto-launched the learned target
  without showing the list → Settings displayed the honest `[auto-ready]` state → delete produced
  "No learned choices yet" and the next command re-showed candidates → correction before K switched the
  ranked-first target from A to B → uninstalling preferred B invalidated the preference and the command
  launched remaining A without stale auto-resolve. Screenshots captured under `/tmp/sidr_acceptance_*.png`.
- [x] **Parity check on device (2026-07-11):** with no learned preferences, typed commands behaved as
  before; non-ambiguous `open Salatuk` launched the app with Smart command routing temporarily OFF, and
  the flag was restored to ON. With only one same-label fixture remaining after preferred-target uninstall,
  `open SidrProbe` launched the remaining app rather than a stale learned target.
- [x] **Docs (2026-07-11):** appended ADR "2026-07-11 — S2-1 Learned Resolutions device accepted +
  closed" to `decisions.md`; synced `CLAUDE.md` Current goal + `current-status.md`; marked this plan
  CLOSED.

**DoD:** build green; device slice observed end-to-end; parity confirmed; ADR + docs synced.

---

## Self-Review (author checklist — completed, incl. pre-flight edits)

- **Spec coverage:** every spec section maps to a task — types §3→T1, policy §4→T2, write §5.2→T3,
  display §8→T4, observe/delete/prune §8→T5, read path §5.1→T6, persistence §6→T7–T9, privacy §7→T9/T15,
  management UI §8→T12–T14, failure/fallback §9→spread across T3/T6/T8/T13, tests §10→each task, DoD §11→T16.
- **Placeholder scan:** none. T5 and T6 tests are now **fully concrete** (real `FakeInstalledAppsRepository`
  / `FakeActionCatalog` / `CommandRouteStep` seam; no `assertTrue(true)`, no empty bodies). Every production
  code block is concrete; no `TBD`/"add error handling"/"handle edge cases".
- **Pre-flight edits applied (owner-requested):**
  1. `fingerprintOf` uses a type-prefixed, cast-free `targetId` (`app:<pkg>`) via exhaustive `when`; a
     cast-free `appPackageOrNull()` helper replaces all `as ResolvedTarget.App` reads.
  2. `RecordResolutionChoiceUseCase.record(...)` returns `OperationResult<Unit>` (VM ignores it in
     fire-and-forget). Also `DeleteLearnedChoiceUseCase.delete`/`PruneUnavailableLearnedChoicesUseCase.prune`
     return `OperationResult<Unit>`.
  3. `ResolvedCommand` is a sealed `Outcome | AutoLaunch` — AutoResolve yields an **`AutoLaunch` directive**,
     never a premature `CommandOutcome.Executed`; `Executed` arises only in the VM after a successful
     `launchApp`.
  4. Fork (a) chosen by the owner: the VM launches the specific package via existing `launchApp`; encoded in
     T6 (`AutoLaunch(target, fallback)`) + T11.
- **Type consistency:** `fingerprintOf`/`targetId`/`appPackageOrNull`, `ResolutionDecision` (4 variants),
  `PreferenceEvidence(streak,totalChoices,lastChosenAtEpochMs)`,
  `RecordResolutionChoiceUseCase.record(key,context,chosen,candidates,now): OperationResult<Unit>`,
  `ResolvedCommand.Outcome(outcome,learningToken)` / `ResolvedCommand.AutoLaunch(target,fallback)`,
  `CommandRouteStep`, `LearnedChoiceDisplayState` (5 variants) — used consistently across tasks.
- **v1 slot (owner-required, included):** `query` = the normalized slot via the narrow deterministic
  `LaunchSlotExtractor` (Task 6 Part A) — LAUNCH_APP ambiguity only, **not** a general NLU/parser:
  `open bank` / `launch bank` / `open my bank app` → `bank`. This matches the design spec's §3 "normalized
  slot, e.g. 'bank'".

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-06-learned-resolutions.md`.

**Do not execute yet** — the owner has deferred implementation, Room schema changes, and use-case wiring;
this plan is for a final read first. The Task 6 integration fork is **resolved (owner: fork a — VM
`launchApp`)** and encoded as the `AutoLaunch` directive. After the owner clears execution, the two options
are:
1. **Subagent-Driven (recommended):** a fresh subagent per task with review between tasks.
2. **Inline Execution:** batch tasks in-session with checkpoints.
