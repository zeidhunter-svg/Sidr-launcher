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
    // `query` is the app name FastPath extracted from "open bank" (A4′ phase 3b, Task 7: the key is built from it).
    private val ambiguous = CommandOutcome.NeedsConfirmation(listOf(installed("com.a", "A"), installed("com.b", "B")), query = "bank")
    private fun target(p: String) = ResolvedTarget.App(p)
    private val key = CapabilityKey(ActionIds.LAUNCH_APP, "bank")   // LearnedChoiceKeys.launch("bank", …) == "bank"
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

    @Test fun `missing catalog descriptor fails safe to non-SAFE and never AutoLaunch`() = runTest {
        // Confident streak + matching fingerprint would AutoLaunch under SAFE — but with no descriptor
        // the risk lookup falls back to CONFIRM, so the policy demotes to RankFirst (Outcome, not AutoLaunch).
        val emptyCatalog = FakeActionCatalog(emptyList())
        val uc = ResolveCommandWithPreferenceUseCase({ ambiguous }, store, policy, emptyCatalog)
        store.upsert(ResolutionPreference(key, ResolutionContext.None, target("com.b"),
            PreferenceEvidence(9, 9, 0L), fingerprintOf(candidates)))
        val r = uc.resolve("open bank")
        assertTrue(r is ResolvedCommand.Outcome)   // fail-safe: NOT an AutoLaunch directive
        r as ResolvedCommand.Outcome
        val out = r.outcome as CommandOutcome.NeedsConfirmation
        assertEquals("com.b", out.candidates.first().packageName)   // still RankFirst-reordered
    }

    @Test fun `Stale prunes the record and returns the original list`() = runTest {
        store.upsert(ResolutionPreference(key, ResolutionContext.None, target("com.gone"),
            PreferenceEvidence(5, 5, 0L), fingerprintOf(CandidateSet(listOf(target("com.gone"))))))
        val r = useCase(ambiguous).resolve("open bank") as ResolvedCommand.Outcome
        assertEquals(ambiguous, r.outcome)
        assertNull(stored()) // pruned
    }

    // --- A4′ phase 3b, Task 7: one key for a learned launch choice (B-4); a confirmation is learned (B-3) ---

    private val tessarOne = installed("net.fixture.tessar.one", "Tessar")
    private val tessarTwo = installed("net.fixture.tessar.two", "Tessar")
    private val miravin = installed("net.fixture.miravin", "Миравин")

    /**
     * B-4: the key is the app NAME FastPath extracted ([CommandOutcome.NeedsConfirmation.query]), folded —
     * not the command. Before Task 7 it was the command's English-verb-stripped slot, so «открой tessar»
     * keyed «открой tessar», and a choice learned there was never the one the bridge reads for «tessar».
     * Both halves are checked: the token the VM will record, and the key the store is READ by (a weak
     * choice stored under «tessar» ranks its app first).
     */
    @Test fun `a choice is keyed on the app name, not on the verb`() = runTest {
        val tessar = CapabilityKey(ActionIds.LAUNCH_APP, "tessar")
        val both = CandidateSet(listOf(target(tessarOne.packageName), target(tessarTwo.packageName)))
        store.upsert(ResolutionPreference(tessar, ResolutionContext.None, target(tessarTwo.packageName),
            PreferenceEvidence(1, 1, 0L), fingerprintOf(both)))

        val r = useCase(CommandOutcome.NeedsConfirmation(listOf(tessarOne, tessarTwo), query = "tessar"))
            .resolve("открой tessar") as ResolvedCommand.Outcome

        assertEquals(tessar, r.learningToken!!.capabilityKey)
        assertEquals(CommandOutcome.NeedsConfirmation(listOf(tessarTwo, tessarOne), query = "tessar"), r.outcome)
    }

    /**
     * B-3: a word form FastPath only OFFERS («миравину» for «Миравин», `confirm`) is learned exactly like a
     * choice between several — the same store, the same policy, the token the VM records on the tap.
     * Confirmed [DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD] times, it launches without asking; until then, and
     * as the fallback a failed launch shows, it is still the same question — `query` and `confirm` kept.
     */
    @Test fun `a confirmed word form is learned like a choice`() = runTest {
        val offered = CommandOutcome.NeedsConfirmation(listOf(miravin), query = "миравину", confirm = true)
        val record = RecordResolutionChoiceUseCase(store)

        repeat(DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD) {
            val asked = useCase(offered).resolve("открой миравину") as ResolvedCommand.Outcome
            assertEquals(offered, asked.outcome)
            val token = asked.learningToken!!
            record.record(token.capabilityKey, token.context, target(miravin.packageName), token.candidateSet)
        }

        assertEquals(
            ResolvedCommand.AutoLaunch(target(miravin.packageName), fallback = offered),
            useCase(offered).resolve("открой миравину"),
        )
    }
}
