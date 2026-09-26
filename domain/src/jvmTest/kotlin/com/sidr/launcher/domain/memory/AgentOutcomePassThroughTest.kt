package com.sidr.launcher.domain.memory

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Neither memory decorator matches exhaustively over `CommandOutcome`: each passes what it does not
 * recognise straight through by an early return. That is the behaviour A0 wants, and it is **silent** —
 * adding `AgentSessionStarted` compiled clean and would have compiled clean if a future edit broke the
 * pass-through. Hence this test rather than trust in the shape of the code.
 *
 * "Passes through" is asserted as two things, because only the pair is the property: the outcome comes
 * back identical, **and** the decorator's own store was never consulted. An agent session is not an
 * ambiguity to learn from and not a phrase to alias, so a read against either store would be a memory
 * lookup keyed on something the memory layer has no business knowing about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentOutcomePassThroughTest {

    private val agentOutcome = CommandOutcome.AgentSessionStarted(AgentSessionId("s1"))

    @Test
    fun `the learned-resolution decorator passes an agent session through untouched`() = runTest {
        val store = CountingPreferenceStore()
        val useCase = ResolveCommandWithPreferenceUseCase(
            route = CommandRouteStep { agentOutcome },
            store = store,
            policy = DefaultResolutionPreferencePolicy(),
            catalog = FakeActionCatalog(),
        )

        val resolved = useCase.resolve("открой убер")

        assertTrue("expected a plain Outcome, was $resolved", resolved is ResolvedCommand.Outcome)
        resolved as ResolvedCommand.Outcome
        assertEquals(agentOutcome, resolved.outcome)
        assertNull("an agent session is not an ambiguity to learn from", resolved.learningToken)
        assertEquals("the preference store must not be consulted", 0, store.interactions)
    }

    @Test
    fun `the alias decorator passes an agent session through untouched`() = runTest {
        val store = CountingAliasStore()
        val apps = FakeInstalledAppsRepository()
        val inner = ResolvedCommand.Outcome(agentOutcome, null)
        val useCase = ResolveCommandWithAliasUseCase(
            inner = ResolvedCommandStep { inner },
            store = store,
            installedApps = apps,
        )

        val resolved = useCase.resolve("открой убер")

        assertSame("the decorator must return the inner result itself", inner, resolved)
        resolved as ResolvedCommand.Outcome
        assertEquals(agentOutcome, resolved.outcome)
        assertEquals("the alias store must not be consulted", 0, store.interactions)
        assertEquals("the installed-app list must not be read", 0, apps.callCount)
    }

    /**
     * Counting delegates rather than assertion-free fakes: "no store was consulted" is the half of the
     * property a returned value cannot show. The delegate is a real empty store, so a decorator that
     * did read one would still see the same empty answer — only the counter tells them apart.
     */
    private class CountingPreferenceStore(
        private val delegate: FakeResolutionPreferenceStore = FakeResolutionPreferenceStore(),
    ) : ResolutionPreferenceStore {
        var interactions = 0
            private set

        override suspend fun find(
            key: CapabilityKey,
            context: ResolutionContext,
        ): OperationResult<ResolutionPreference?> {
            interactions++
            return delegate.find(key, context)
        }

        override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> {
            interactions++
            return delegate.upsert(preference)
        }

        override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> {
            interactions++
            return delegate.delete(key, context)
        }

        override fun observeAll(): Flow<List<ResolutionPreference>> {
            interactions++
            return delegate.observeAll()
        }
    }

    private class CountingAliasStore(
        private val delegate: FakeAliasStore = FakeAliasStore(),
    ) : AliasStore {
        var interactions = 0
            private set

        override suspend fun find(phrase: String): OperationResult<Alias?> {
            interactions++
            return delegate.find(phrase)
        }

        override suspend fun upsert(alias: Alias): OperationResult<Unit> {
            interactions++
            return delegate.upsert(alias)
        }

        override suspend fun delete(phrase: String): OperationResult<Unit> {
            interactions++
            return delegate.delete(phrase)
        }

        override fun observeAll(): Flow<List<Alias>> {
            interactions++
            return delegate.observeAll()
        }
    }
}
