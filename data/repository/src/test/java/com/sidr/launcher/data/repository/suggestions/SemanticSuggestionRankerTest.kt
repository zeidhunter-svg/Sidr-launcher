package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.HeuristicSuggestionRanker
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticSuggestionRankerTest {

    private val modelId = ModelId("suggestion-embedding-v1")
    private val heuristic = HeuristicSuggestionRanker()
    private val context = SuggestionContext(TimeOfDay.WORK, nowEpochMs = 0L, typedPrefix = "camera")
    private val candidates = listOf(
        suggestion("Mail", "mail", 4.0),
        suggestion("Camera", "camera", 3.0),
        suggestion("Maps", "maps", 2.0),
    )

    private fun suggestion(label: String, id: String, score: Double) =
        Suggestion(label, id, SuggestionSource.RECENT_USAGE, score)

    private fun ranker(
        embedder: ScriptedTextEmbedder = ScriptedTextEmbedder(),
        pinned: Boolean = true,
        profileProvider: FakeDeviceProfileProvider = FakeDeviceProfileProvider(),
        availability: ModelAvailability = ModelAvailability.Available,
    ): Pair<SemanticSuggestionRanker, ScriptedTextEmbedder> {
        val availabilityRepository = FakeModelAvailabilityRepository().apply {
            setAvailability(modelId, availability)
        }
        return SemanticSuggestionRanker(
            heuristic = heuristic,
            textEmbedder = embedder,
            deviceProfileProvider = profileProvider,
            modelAvailabilityRepository = availabilityRepository,
            embeddingModelId = modelId,
            embeddingModelPinned = pinned,
        ) to embedder
    }

    @Test
    fun `pending model returns heuristic order and does not consult embedder`() {
        val (ranker, embedder) = ranker(pinned = false)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
        assertEquals(emptyList<String>(), embedder.receivedTexts)
    }

    @Test
    fun `gate off returns heuristic order and does not consult embedder`() {
        val lowEnd = FakeDeviceProfileProvider(initialProfile = DeviceProfile.LOW_END)
        val (ranker, embedder) = ranker(profileProvider = lowEnd)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
        assertEquals(emptyList<String>(), embedder.receivedTexts)
    }

    @Test
    fun `dynamic battery gate off returns heuristic order and does not consult embedder`() {
        val provider = FakeDeviceProfileProvider(
            initialCapability = DeviceCapability(
                ramBytes = 4_000_000_000L,
                cpuCores = 4,
                nnapiAvailable = false,
                thermalOk = true,
                batteryOk = false,
            ),
        )
        val (ranker, embedder) = ranker(profileProvider = provider)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
        assertEquals(emptyList<String>(), embedder.receivedTexts)
    }

    @Test
    fun `missing availability returns heuristic order and does not consult embedder`() {
        val (ranker, embedder) = ranker(availability = ModelAvailability.Missing)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
        assertEquals(emptyList<String>(), embedder.receivedTexts)
    }

    @Test
    fun `blank typed prefix returns heuristic order and does not consult embedder`() {
        val blankContext = context.copy(typedPrefix = " ")
        val (ranker, embedder) = ranker()

        assertEquals(heuristic.rank(candidates, blankContext), ranker.rank(candidates, blankContext))
        assertEquals(emptyList<String>(), embedder.receivedTexts)
    }

    @Test
    fun `embedder failure returns heuristic order exactly`() {
        val embedder = ScriptedTextEmbedder(
            fallback = OperationResult.Failure(OperationError.UnknownError("boom")),
        )
        val (ranker, _) = ranker(embedder = embedder)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
        assertEquals(listOf("camera"), embedder.receivedTexts)
    }

    @Test
    fun `empty query embedding returns heuristic order exactly`() {
        val embedder = ScriptedTextEmbedder(
            vectors = mapOf("camera" to floatArrayOf()),
        )
        val (ranker, _) = ranker(embedder = embedder)

        assertEquals(heuristic.rank(candidates, context), ranker.rank(candidates, context))
    }

    @Test
    fun `invalid candidate embedding returns heuristic order exactly`() {
        val heuristicOrder = heuristic.rank(candidates, context)
        val embedder = ScriptedTextEmbedder(
            vectors = mapOf(
                "camera" to floatArrayOf(1f, 0f),
                heuristicOrder[0].label to floatArrayOf(Float.NaN, 0f),
            ),
        )
        val (ranker, _) = ranker(embedder = embedder)

        assertEquals(heuristicOrder, ranker.rank(candidates, context))
    }

    @Test
    fun `dimension mismatch returns heuristic order exactly`() {
        val heuristicOrder = heuristic.rank(candidates, context)
        val embedder = ScriptedTextEmbedder(
            vectors = mapOf(
                "camera" to floatArrayOf(1f, 0f),
                heuristicOrder[0].label to floatArrayOf(1f),
            ),
        )
        val (ranker, _) = ranker(embedder = embedder)

        assertEquals(heuristicOrder, ranker.rank(candidates, context))
    }

    @Test
    fun `eligible semantic rank consults embedder and can rerank heuristic output`() {
        val embedder = ScriptedTextEmbedder(
            vectors = mapOf(
                "camera" to floatArrayOf(1f, 0f),
                "Mail" to floatArrayOf(0f, 1f),
                "Camera" to floatArrayOf(1f, 0f),
                "Maps" to floatArrayOf(0.5f, 0.5f),
            ),
        )
        val (ranker, _) = ranker(embedder = embedder)

        assertEquals(listOf("camera", "maps", "mail"), ranker.rank(candidates, context).map { it.actionId })
        assertEquals(listOf("camera", "Mail", "Camera", "Maps"), embedder.receivedTexts)
    }

    private class ScriptedTextEmbedder(
        private val vectors: Map<String, FloatArray> = emptyMap(),
        private val fallback: OperationResult<FloatArray> = OperationResult.Success(floatArrayOf(1f, 0f)),
    ) : TextEmbedder {
        val receivedTexts = mutableListOf<String>()

        override suspend fun embed(text: String): OperationResult<FloatArray> {
            receivedTexts += text
            val vector = vectors[text]
            return if (vector != null) OperationResult.Success(vector) else fallback
        }
    }
}
