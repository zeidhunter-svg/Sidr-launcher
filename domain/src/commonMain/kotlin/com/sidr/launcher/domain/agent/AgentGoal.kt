package com.sidr.launcher.domain.agent

/** Opaque session identity. Produced by [AgentSessionIdFactory] so the domain needs no UUID API. */
@JvmInline
value class AgentSessionId(val value: String)

/**
 * What the planner recognised about the goal. A0 has exactly one shape, and the `when` over it in
 * `TemplatePlanner` is exhaustive on purpose: adding a second shape later forces a deliberate decision
 * rather than falling into a default.
 */
sealed interface GoalShape {
    /** FastPath resolved the command to an app launch and found no such app installed. */
    data class AppNotInstalled(val query: String) : GoalShape
}

/**
 * The user's goal. [text] is the raw command, kept for the surface and for A4''s model planner;
 * [shape] is what the deterministic layer already knows, so the planner never re-parses text the cut
 * site had already understood.
 */
data class AgentGoal(val text: String, val shape: GoalShape)
