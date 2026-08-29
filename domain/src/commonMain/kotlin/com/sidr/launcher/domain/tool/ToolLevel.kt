package com.sidr.launcher.domain.tool

/**
 * Which source supplies a tool. An **open** value class over [String], not a closed sum, and the choice
 * is measured rather than stylistic: A0.5 recorded three new `ToolId`s costing zero core edits against
 * one new `GoalShape` value costing four files in three modules. Levels are the axis that grows —
 * AppFunctions, MCP, Accessibility — so they take the cheap shape.
 *
 * Openness costs nothing here because nothing orders levels: `DOC-HMA-2` asks whether the level
 * *changed*, which is inequality. Precedence between colliding sources comes from the federation's
 * construction order (`ToolFederation`), never from the type.
 */
@JvmInline
value class ToolLevel(val value: String)

/** The levels with a live source. A level with no adapter behind it is not declared here. */
object ToolLevels {
    /** The launcher's own action families, projected. */
    val IN_APP = ToolLevel("in_app")

    /** Android system intents that are not among the frozen seven `ActionIds`. */
    val SYSTEM_INTENT = ToolLevel("system_intent")

    /** `:consumer:jvm`'s sandboxed file tools. */
    val SANDBOX = ToolLevel("sandbox")
}

/**
 * Whether a tool's effect crosses the device boundary. Closed at two on purpose, and the asymmetry with
 * [ToolLevel] is the point: "who supplies this" grows without bound, "does this leave the device" is a
 * yes/no. `DOC-ILM-2` hangs off [EXTERNAL].
 */
enum class ToolEffect { LOCAL, EXTERNAL }
