package com.sidr.launcher.consumer.jvm.store

import kotlinx.serialization.Serializable

/**
 * The consumer's **own** serializable mirror of the session graph.
 *
 * No domain type carries `@Serializable`, and none may: `:domain` is stdlib + coroutines by hard rule,
 * so the serialization plugin cannot be applied there. That means every consumer that persists a
 * session hand-writes a mapper — `AgentSessionMappers` on Android, [SessionMapper] here. **That cost is
 * a finding of this block, not an accident** (spec §8): the price of a second consumer includes one
 * mapper per consumer, and A0.5 reports the measured size rather than estimating it.
 *
 * Everything is stored by **name**, never by ordinal: an enum reordered in `commonMain` must not
 * silently re-interpret a file written by an earlier build.
 */
@Serializable
internal data class SessionDto(
    val id: String,
    val goalText: String,
    val goalFreeText: String,
    val steps: List<StepDto>,
    val cursor: Int,
    val state: String,
    val observations: Map<Int, ResultDto> = emptyMap(),
    val consents: Map<Int, Boolean> = emptyMap(),
    val trace: List<TraceDto> = emptyList(),
)

@Serializable
internal data class StepDto(
    val index: Int,
    val toolId: String,
    val args: Map<String, ArgSourceDto> = emptyMap(),
    val risk: String,
    val precondition: PreconditionDto,
    val rationale: String,
)

/** `kind` is `"Literal"` or `"FromStep"`; the other fields are populated per kind. */
@Serializable
internal data class ArgSourceDto(
    val kind: String,
    val value: String? = null,
    val stepIndex: Int? = null,
    val key: String? = null,
)

/** `kind` is `"None"` or `"PreviousStepObserved"`. */
@Serializable
internal data class PreconditionDto(val kind: String, val fact: String? = null)

/** `kind` is `"Effected"`, `"Observed"` or `"Failed"`. */
@Serializable
internal data class ResultDto(
    val kind: String,
    val fact: String? = null,
    val failure: String? = null,
    val output: Map<String, String> = emptyMap(),
)

/** `kind` is the `TraceEvent` subtype's simple name; the other fields are populated per kind. */
@Serializable
internal data class TraceDto(
    val kind: String,
    val index: Int? = null,
    val stepCount: Int? = null,
    val toolId: String? = null,
    val reason: String? = null,
    val granted: Boolean? = null,
    val state: String? = null,
    val precondition: PreconditionDto? = null,
    val result: ResultDto? = null,
)
