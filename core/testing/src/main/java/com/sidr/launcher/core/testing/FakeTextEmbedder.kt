package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult

/**
 * Configurable fake [TextEmbedder] for unit tests. Returns a scripted result and records
 * all texts passed to [embed]. Not wired into any Hilt graph — use directly in tests.
 */
class FakeTextEmbedder : TextEmbedder {

    var resultToReturn: OperationResult<FloatArray> = OperationResult.Success(floatArrayOf())
    var errorToReturn: OperationError? = null

    val receivedTexts = mutableListOf<String>()

    override suspend fun embed(text: String): OperationResult<FloatArray> {
        receivedTexts += text
        val err = errorToReturn
        return if (err != null) OperationResult.Failure(err) else resultToReturn
    }

    fun reset() {
        resultToReturn = OperationResult.Success(floatArrayOf())
        errorToReturn = null
        receivedTexts.clear()
    }
}
