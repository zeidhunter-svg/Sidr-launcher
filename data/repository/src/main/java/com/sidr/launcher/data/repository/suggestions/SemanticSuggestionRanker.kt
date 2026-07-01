package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.device.LocalInferenceGate
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.HeuristicSuggestionRanker
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionRanker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/**
 * Optional Phase 7 Block V decorator. The heuristic ranker remains the shipping baseline; semantic
 * scoring is only attempted when the embedding artifact is pinned, the typed prefix is non-blank, and
 * the same local inference gate allows ONNX. Any failure returns the heuristic order exactly.
 */
class SemanticSuggestionRanker(
    private val heuristic: SuggestionRanker = HeuristicSuggestionRanker(),
    private val textEmbedder: TextEmbedder,
    private val deviceProfileProvider: DeviceProfileProvider,
    private val modelAvailabilityRepository: ModelAvailabilityRepository,
    private val embeddingModelId: ModelId,
    private val embeddingModelPinned: Boolean,
) : SuggestionRanker {

    override fun rank(candidates: List<Suggestion>, context: SuggestionContext): List<Suggestion> {
        val heuristicOrder = heuristic.rank(candidates, context)
        if (heuristicOrder.size <= 1) return heuristicOrder

        val prefix = context.typedPrefix?.takeIf { it.isNotBlank() } ?: return heuristicOrder
        if (!embeddingModelPinned) return heuristicOrder

        return try {
            runBlocking {
                if (!gateAllows()) return@runBlocking heuristicOrder

                val query = textEmbedder.embed(prefix).vectorOrNull() ?: return@runBlocking heuristicOrder
                if (!query.isUsableEmbedding()) return@runBlocking heuristicOrder

                val scored = heuristicOrder.mapIndexed { index, suggestion ->
                    val candidate = textEmbedder.embed(suggestion.label).vectorOrNull()
                        ?: return@runBlocking heuristicOrder
                    val similarity = cosine(query, candidate)
                    if (!similarity.isFinite()) return@runBlocking heuristicOrder
                    SemanticScore(suggestion, similarity, index)
                }

                scored
                    .sortedWith(
                        compareByDescending<SemanticScore> { it.similarity }
                            .thenBy { it.heuristicIndex },
                    )
                    .map { it.suggestion }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            heuristicOrder
        }
    }

    private suspend fun gateAllows(): Boolean {
        val profile = deviceProfileProvider.profile()
        val capability = deviceProfileProvider.capability()
        val availability = modelAvailabilityRepository.availability(embeddingModelId).first()
        return LocalInferenceGate.allowsLocalNlu(profile, capability, availability)
    }

    private data class SemanticScore(
        val suggestion: Suggestion,
        val similarity: Double,
        val heuristicIndex: Int,
    )

    private fun OperationResult<FloatArray>.vectorOrNull(): FloatArray? = when (this) {
        is OperationResult.Success -> value
        is OperationResult.Failure -> null
    }

    private fun FloatArray.isUsableEmbedding(): Boolean = isNotEmpty() && all { it.isFinite() }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        if (!a.isUsableEmbedding() || !b.isUsableEmbedding() || a.size != b.size) return Double.NaN
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        if (normA <= 0.0 || normB <= 0.0) return Double.NaN
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
