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
import com.sidr.launcher.domain.ai.bridge.BridgeBudget
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
 * median and p90 time, truncations, whether hidden reasoning was reported, how many `question`
 * lines went to the assistant against how many became a web search, and (phase 3b, Task 8) how many
 * `multi` lines came back as a plan of two steps or more. A plan's line reads `kind=plan:<n>`, its
 * steps' tools and arguments joined by `+`. Correctness is the owner's to judge from the per-line
 * report — nothing here decides it.
 *
 * Two switches, read from the environment and validated here, before any request (phase 3b, B-14 — the
 * script's argument parsing is untouched): `SIDR_MEASURE_MODES`, a comma list of [BridgeTransportMode]
 * names (default: all three), and `SIDR_MEASURE_REASONING` — `off`, `on` or `off,on` (default `off`;
 * fork 3b-H). `on` passes the reasoning cap `BridgeBudget.Default.maxOutputTokens / 2`, which the
 * transport sends only to OpenRouter (B-16) — so `on` against any other base URL is refused rather than
 * recorded as a cap that never left. An unknown or repeated value is refused. Every `line` and `summary`
 * says which setting it ran under (`reasoning=off|on`).
 */
class BridgeTransportMeasurement {

    private class Recording(private val inner: BridgeTransport) : BridgeTransport {
        val replies = mutableListOf<TransportReply>()
        override suspend fun ask(request: BridgeRequest): TransportReply = inner.ask(request).also { replies += it }
    }

    private data class Row(
        val model: String,
        val mode: BridgeTransportMode,
        val reasoning: Reasoning,
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
        val modes = switchOf("SIDR_MEASURE_MODES", BridgeTransportMode.entries) { name -> BridgeTransportMode.entries.find { it.name == name } }
        val reasonings = switchOf("SIDR_MEASURE_REASONING", listOf(Reasoning.OFF)) { word -> Reasoning.entries.find { it.word == word } }
        check(Reasoning.ON !in reasonings || OpenAiCompatibleBridgeTransport.acceptsReasoningCap(baseUrl)) {
            "BRIDGE-MEASURE :: refused - SIDR_MEASURE_REASONING=on is sent only to ${OpenAiCompatibleBridgeTransport.REASONING_CAP_HOST} (B-16); SIDR_MEASURE_BASE_URL is another host"
        }

        val registry = ProductionGraph.federation().registry
        val descriptions = AuthoredToolDescriptions()
        val offered = BridgeFilter.offeringOf(registry, descriptions).tools.size
        // Built BEFORE the dry-run return, so the dry run prints the budgets the live run would pass.
        val budgets = reasonings.associateWith { reasoning ->
            BridgeBudget(
                ceilingMs = MEASURE_CEILING_MS,
                maxOutputTokens = BridgeBudget.Default.maxOutputTokens,
                reasoningMaxTokens = if (reasoning == Reasoning.ON) REASONING_ON_MAX_TOKENS else null,
            )
        }
        if (System.getProperty("sidr.measureDryRun") == "true") {
            println(
                "BRIDGE-MEASURE :: dry-run key=present corpus=${corpus.size} models=$models modes=$modes " +
                    "reasoning=${reasonings.map { it.word }} reasoning_on_max_tokens=$REASONING_ON_MAX_TOKENS " +
                    "offering=$offered ceiling=$MEASURE_CEILING_MS",
            )
            return@runBlocking
        }

        // The cap of Task 12 rule 2, not the production ceiling: the measurement must be able to SEE a
        // latency the rule could still accept, or it can never raise the ceiling it exists to set.
        val client = bridgeHttpClient(socketTimeoutMs = MEASURE_SOCKET_MS)
        val provider = AiProviderId("measure")
        val store = FakeSecureSecretStore().apply { put(SecretKeys.apiKey(provider), key) }
        val report = StringBuilder()
        fun emit(line: String) {
            println(line)
            report.appendLine(line)
        }

        models.forEach { model ->
            val config = FakeAiProviderConfigRepository(AiProviderConfig(provider, baseUrl, AiModelId(model)))
            modes.forEach { mode ->
                budgets.forEach { (reasoning, budget) ->
                    val rows = corpus.map { (label, command) ->
                        val recording = Recording(OpenAiCompatibleBridgeTransport(client, store, config, Dispatchers.IO, mode))
                        val started = System.nanoTime()
                        val result = BridgeModelPlanner(registry, descriptions, recording, budget).plan(command)
                        Row(model, mode, reasoning, label, command, result, recording.replies.toList(), (System.nanoTime() - started) / 1_000_000)
                    }
                    rows.forEach { emit(lineOf(it)) }
                    emit(summaryOf(model, mode, reasoning, rows))
                }
            }
        }
        client.close()
        File("build/bridge-measure").mkdirs()
        File("build/bridge-measure/report.txt").writeText(report.toString())
    }

    private fun kindOf(result: ModelPlanning): String = when (result) {
        is ModelPlanning.Proposed -> "plan:${result.steps.size}"
        is ModelPlanning.ToAssistant -> "assistant"
        is ModelPlanning.Navigate -> "navigate:${result.destination.wire}"
        is ModelPlanning.Clarify -> "clarify"
        ModelPlanning.Declined -> "decline"
        is ModelPlanning.Unavailable -> "unavailable:${result.reason}"
    }

    private fun lineOf(row: Row): String {
        val steps = (row.result as? ModelPlanning.Proposed)?.steps
        val meta = row.replies.lastOrNull()?.let { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta }
        val tools = steps?.joinToString("+") { it.tool.value } ?: "-"
        val args = steps?.joinToString("+") { step -> if (step.refs.isEmpty()) "${step.args}" else "${step.args}${step.refs}" } ?: "-"
        return "BRIDGE-MEASURE :: line model=${row.model} mode=${row.mode} reasoning=${row.reasoning.word} label=${row.label} kind=${kindOf(row.result)} " +
            "tool=$tools args=$args attempts=${row.replies.size} ms=${row.ms} " +
            "finish=${meta?.finishReason ?: "-"} completion_tokens=${meta?.completionTokens ?: "-"} " +
            "reasoning_tokens=${meta?.reasoningTokens ?: "-"} command=${row.command}"
    }

    private fun summaryOf(model: String, mode: BridgeTransportMode, reasoning: Reasoning, rows: List<Row>): String {
        val n = rows.size
        val parsedFirst = rows.count { it.replies.firstOrNull() is TransportReply.Answer }
        val usable = rows.count { it.result !is ModelPlanning.Unavailable }
        val retried = rows.count { it.replies.size == 2 }
        val timedOut = rows.count { (it.result as? ModelPlanning.Unavailable)?.reason?.name == "TIMED_OUT" }
        val sorted = rows.map { it.ms }.sorted()
        val median = sorted.getOrNull(n / 2)
        // Nearest rank: the ceil(0.9 n)-th smallest, computed in integers ((9n + 9) / 10 == ceil(9n / 10))
        // so no floating rounding can move it. `(n * 9) / 10` was the maximum for n <= 10 and one rank high
        // above it — and Task 12 rule 2 multiplies this number by 1.5.
        val p90 = if (n == 0) null else sorted[(9 * n + 9) / 10 - 1]
        val metas = rows.flatMap { row -> row.replies.mapNotNull { (it as? TransportReply.Answer)?.meta ?: (it as? TransportReply.Unparseable)?.meta } }
        val truncated = metas.count { it.finishReason == "length" }
        val reasoningReported = metas.count { (it.reasoningTokens ?: 0) > 0 }
        val questions = rows.filter { it.label == "question" }
        val toAssistant = questions.count { it.result is ModelPlanning.ToAssistant }
        val toSearch = questions.count { row -> (row.result as? ModelPlanning.Proposed)?.steps.orEmpty().any { it.tool.value == "web_search" } }
        // Lines labelled `multi` ask for more than one act; how many came back as a plan of two steps or more.
        val multi = rows.filter { it.label == "multi" }
        val multiSteps = multi.count { ((it.result as? ModelPlanning.Proposed)?.steps?.size ?: 0) >= 2 }
        return "BRIDGE-MEASURE :: summary model=$model mode=$mode reasoning=${reasoning.word} n=$n parsed_first=$parsedFirst/$n usable=$usable/$n " +
            "retried=$retried timed_out=$timedOut median_ms=${median ?: "-"} p90_ms=${p90 ?: "-"} truncated=$truncated " +
            "reasoning_reported=$reasoningReported questions_to_assistant=$toAssistant/${questions.size} " +
            "questions_to_web_search=$toSearch/${questions.size} multi_steps_ge2=$multiSteps/${multi.size}"
    }

    /** `SIDR_MEASURE_REASONING`'s two values (fork 3b-H). */
    private enum class Reasoning(val word: String) { OFF("off"), ON("on") }

    /**
     * A switch: unset or blank → [default]; otherwise a comma list, each trimmed value read by [parse].
     * An unknown, empty or repeated value refuses the run — here, before any request is made.
     */
    private fun <T> switchOf(name: String, default: List<T>, parse: (String) -> T?): List<T> {
        val raw = System.getenv(name)?.takeIf { it.isNotBlank() } ?: return default
        val values = raw.split(',').map { it.trim() }
        val parsed = values.map { value -> checkNotNull(parse(value)) { "BRIDGE-MEASURE :: refused - $name has an unknown value '$value'" } }
        check(parsed.distinct().size == parsed.size) { "BRIDGE-MEASURE :: refused - $name repeats a value" }
        return parsed
    }

    private companion object {
        /** `SIDR_MEASURE_REASONING=on`: half the shipped output limit — derived, not a new number (plan §0.3). */
        val REASONING_ON_MAX_TOKENS = BridgeBudget.Default.maxOutputTokens / 2

        /** The socket cap of Task 12 rule 2: one attempt may be silent this long before the socket ends it. */
        const val MEASURE_SOCKET_MS = 90_000L

        /**
         * The planner's ceiling for the measurement. `BridgeModelPlanner` wraps the WHOLE planning, the one
         * retry included, in `withTimeoutOrNull(ceilingMs)`; with `BridgeBudget.Default` (45 s when this was
         * written, 50 s after the 2026-11-07 run, 45 s again since the 2026-11-07b re-measure) a 60 s answer
         * would be cut and recorded as `timed_out`
         * at the default ceiling, so p90 could never exceed it and rule 2 would be fed a truncated number.
         * Two attempts × the socket cap, so one silent wait up to the cap is observed rather than cut.
         * Not a strict bound: connect time and body transfer count against the
         * ceiling too, so a slow first attempt followed by a long retry can still meet it — that row reads
         * `timed_out`, and its `ms` says how long it ran.
         */
        const val MEASURE_CEILING_MS = 2 * MEASURE_SOCKET_MS
    }
}
