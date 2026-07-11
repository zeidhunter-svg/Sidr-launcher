package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAliasUseCaseTest {
    private val store = FakeAliasStore()
    private val useCase = SaveAliasUseCase(store)

    @Test
    fun `save normalizes phrase and upserts`() = runTest {
        val result = useCase.save("  Work   Chat ", AliasTarget.App("com.telegram"), nowEpochMs = 7L)

        assertTrue(result is OperationResult.Success<*>)
        val stored = store.observeAll().first().single()
        assertEquals("work chat", stored.phrase)
        assertEquals(AliasTarget.App("com.telegram"), stored.target)
        assertEquals(7L, stored.createdAtEpochMs)
    }

    @Test
    fun `blank phrase is a no-op success`() = runTest {
        val result = useCase.save("   ", AliasTarget.App("com.telegram"))

        assertTrue(result is OperationResult.Success<*>)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test
    fun `over-length phrase is a no-op success`() = runTest {
        val long = "a".repeat(MAX_ALIAS_PHRASE_LENGTH + 1)
        val result = useCase.save(long, AliasTarget.App("com.telegram"))

        assertTrue(result is OperationResult.Success<*>)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test
    fun `save is last-wins for the same phrase`() = runTest {
        useCase.save("bank", AliasTarget.App("com.a"))
        useCase.save("bank", AliasTarget.App("com.b"))

        val all = store.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(AliasTarget.App("com.b"), all.single().target)
    }
}
