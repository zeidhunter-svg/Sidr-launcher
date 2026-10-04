package com.sidr.launcher.bridge

import com.sidr.launcher.agent.ProductionGraph
import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.data.aicloud.bridgeHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * The transport measurement (spec §7.12.2; plan Task 6, applied in Task 12). **Never in a gate:**
 * `app/build.gradle.kts` excludes this class from every Test task unless the run passes
 * `-Psidr.measureBridgeTransport` (probed, plan §0.3). Real production offering and descriptions, the
 * real `BridgeModelPlanner`, the real transport over the engine `:app` ships; only the config and the
 * key store are fakes, filled from the environment.
 *
 * Per (model × mode × corpus line) it records the reading, the attempts, the wall time and what the
 * provider reported (finish reason, completion and reasoning tokens); per (model × mode) it summarises
 * the share parsed on the first try, the share usable after at most one retry, retries, timeouts, the
 * median and p90 time, truncations, whether hidden reasoning was reported, and how many `question`
 * lines went to the assistant against how many became a web search. Correctness is the owner's to
 * judge from the per-line report — nothing here decides it.
 */
class BridgeTransportMeasurement {

    private class Recording(private val inner: BridgeTransport) : BridgeTransport {
        val replies = mutableListOf<TransportReply>()
        override suspend fun ask(request: BridgeRequest): TransportReply = inner.ask(request).also { replies += it }
    }

    private data class Row(
        val model: String,
        val mode: BridgeTransportMode,
        val label: String,
        val command: String,
        val result: ModelPlanning,
        val replies: List<TransportReply>,
        val ms: Long,
    )

    @Test
    fun measure() = runBlocking {
        val key = System.getenv("SIDR_MEASURE_API_KEY").orEmpty()
        val models = System.getProperty("sidr.measureModels").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val baseUrl = System.getenv("SIDR_MEASURE_BASE_URL")?.takeIf { it.isNotBlank() } ?: "https://openrouter.ai/api/v1"
        val corpus = File("src/test/resources/bridge-measure/corpus.tsv").takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") && '\t' in it }
            ?.map { it.substringBefore('\t').trim() to it.substringAfter('\t').trim() }
            .orEmpty()
        check(key.isNotEmpty()) { "BRIDGE-MEASURE :: refused - SIDR_MEASURE_API_KEY did not reach the test JVM" }
        check(corpus.isNotEmpty()) { "BRIDGE-MEASURE :: refused - no corpus lines in src/test/resources/bridge-measure/corpus.tsv" }
        check(models.isNotEmpty()) { "BRIDGE-MEASURE :: refused - pass -Psidr.measureModels=a,b" }

        val registry = ProductionGraph.federation().registry
        val descriptions = AuthoredToolDescriptions()
        val offered = BridgeFilter.offeringOf(registry, descriptions).tools.size
        if (System.getProperty("sidr.measureDryRun") == "true") {
            println("BRIDGE-MEASURE :: dry-run key=present corpus=${corpus.size} models=$models modes=${BridgeTransportMode.entries} offering=$offered")
            return@runBlocking
        }

        // The cap of Task 12 rule 2, not the provisional ceiling: the measurement must be able to SEE a
        // latency the rule could still accept, or it can never raise the ceiling it exists to set.
        val client = bridgeHttpClient(socketTimeoutMs = 90_000)
        val provider = AiProviderId("measure")
        val store = FakeSecureSecretStore().apply { put(SecretKeys.apiKey(provider), key) }
        val report = StringBuilder()
        fun emit(line: String) {
            println(line)
            report.appendLine(line)
        }

        models.forEach { model ->
            val config = FakeAiProviderConfigRepository(AiProviderConfig(provider, baseUrl, AiModelId(model)))
            BridgeTransportMode.entries.forEach { mode ->
                val rows = corpus.map { (label, command) ->
                    val recording = Recording(OpenAiCompatibleBridgeTransport(client, store, config, Dispatchers.IO, mode))
                    val started = System.nanoTime()
                    val result = BridgeModelPlanner(registry, descriptions, recording).plan(command)
                    Row(model, mode, label, command, result, recording.replies.toList(), (System.nanoTime() - started) / 1_000_000)
                }
                rows.forEach { emit(lineOf(it)) }
                emit(summaryOf(model, mode, rows))
            }
        }
        client.close()
        File("build/bridge-measure").mkdirs()
        File("build/bridge-measure/report.txt").writeText(report.toString())
    }

    private fun kindOf(result: ModelPlanning): String = when (result) {
        is ModelPlanning.Proposed -> "plan"
        is ModelPlanning.ToAssistant -> "assistant"
        is ModelPlanning.Navigate -> "navigate:${result.destination.wire}"
        is ModelPlanning.Clarify -> "clarify"
        ModelPlanning.Declined -> "decline"
        is ModelPlanning.Unavailable -> "unavailable:${result.reason}"
    }

    private fun lineOf(row: Row): String {
        val step = (row.result as? ModelPlanning.Proposed)?.step
        val meta = row.replies.lastOrNull()?.let { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta }
        return "BRIDGE-MEASURE :: line model=${row.model} mode=${row.mode} label=${row.label} kind=${kindOf(row.result)} " +
            "tool=${step?.tool?.value ?: "-"} args=${step?.args ?: "-"} attempts=${row.replies.size} ms=${row.ms} " +
            "finish=${meta?.finishReason ?: "-"} completion_tokens=${meta?.completionTokens ?: "-"} " +
            "reasoning_tokens=${meta?.reasoningTokens ?: "-"} command=${row.command}"
    }

    private fun summaryOf(model: String, mode: BridgeTransportMode, rows: List<Row>): String {
        val n = rows.size
        val parsedFirst = rows.count { it.replies.firstOrNull() is TransportReply.Answer }
        val usable = rows.count { it.result !is ModelPlanning.Unavailable }
        val retried = rows.count { it.replies.size == 2 }
        val timedOut = rows.count { (it.result as? ModelPlanning.Unavailable)?.reason?.name == "TIMED_OUT" }
        val sorted = rows.map { it.ms }.sorted()
        val median = sorted[n / 2]
        val p90 = sorted[((n * 9) / 10).coerceAtMost(n - 1)]
        val metas = rows.flatMap { row -> row.replies.mapNotNull { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta } }
        val truncated = metas.count { it.finishReason == "length" }
        val reasoning = metas.count { (it.reasoningTokens ?: 0) > 0 }
        val questions = rows.filter { it.label == "question" }
        val toAssistant = questions.count { it.result is ModelPlanning.ToAssistant }
        val toSearch = questions.count { (it.result as? ModelPlanning.Proposed)?.step?.tool?.value == "web_search" }
        return "BRIDGE-MEASURE :: summary model=$model mode=$mode n=$n parsed_first=$parsedFirst/$n usable=$usable/$n " +
            "retried=$retried timed_out=$timedOut median_ms=$median p90_ms=$p90 truncated=$truncated " +
            "reasoning_reported=$reasoning questions_to_assistant=$toAssistant/${questions.size} " +
            "questions_to_web_search=$toSearch/${questions.size}"
    }
}
