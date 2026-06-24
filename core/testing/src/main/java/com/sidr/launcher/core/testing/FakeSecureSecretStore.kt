package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKey
import com.sidr.launcher.domain.security.SecureSecretStore

/**
 * In-memory fake [SecureSecretStore]. Backed by a [MutableMap]; records [put] / [remove] calls.
 * When [errorToReturn] is non-null, every operation returns [OperationResult.Failure] without
 * mutating state. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeSecureSecretStore : SecureSecretStore {

    private val store = mutableMapOf<SecretKey, String>()

    /** When non-null, all operations return [OperationResult.Failure] and do not mutate state. */
    var errorToReturn: OperationError? = null

    val putCalls = mutableListOf<Pair<SecretKey, String>>()
    val removeCalls = mutableListOf<SecretKey>()

    override suspend fun get(key: SecretKey): OperationResult<String?> {
        errorToReturn?.let { return OperationResult.Failure(it) }
        return OperationResult.Success(store[key])
    }

    override suspend fun put(key: SecretKey, value: String): OperationResult<Unit> {
        putCalls += key to value
        errorToReturn?.let { return OperationResult.Failure(it) }
        store[key] = value
        return OperationResult.Success(Unit)
    }

    override suspend fun remove(key: SecretKey): OperationResult<Unit> {
        removeCalls += key
        errorToReturn?.let { return OperationResult.Failure(it) }
        store.remove(key)
        return OperationResult.Success(Unit)
    }
}
