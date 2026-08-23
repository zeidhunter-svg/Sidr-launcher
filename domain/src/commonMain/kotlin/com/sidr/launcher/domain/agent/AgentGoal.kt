package com.sidr.launcher.domain.agent

/** Opaque session identity. Produced by [AgentSessionIdFactory] so the domain needs no UUID API. */
@JvmInline
value class AgentSessionId(val value: String)

/**
 * What the planner recognised about the goal.
 *
 * [AppNotInstalled] is A0's one recognised shape, and the `when` over this type in `TemplatePlanner`
 * is exhaustive on purpose: adding a **recognised** shape forces a deliberate decision rather than
 * falling into a default.
 *
 * [Free] is A0.5's addition and is deliberately not a recognised shape at all — it is the *absence* of
 * one, carrying the raw text for a planner that does its own reading. Master Plan §3.6 `B1` holds this
 * type at one value until A4', and its stated failure mode is a taxonomy built for a consumer that has
 * not arrived; `Free` cannot start one, because there can never be a second `Free`. It is also the
 * shape A4''s model planner needs regardless — a model plans from text. `B1` otherwise stands.
 */
sealed interface GoalShape {
    /** FastPath resolved the command to an app launch and found no such app installed. */
    data class AppNotInstalled(val query: String) : GoalShape

    /** No shape was recognised; the planner receives the raw goal text and reads it itself. */
    data class Free(val text: String) : GoalShape
}

/**
 * The user's goal. [text] is the raw command, kept for the surface and for A4''s model planner;
 * [shape] is what the deterministic layer already knows, so the planner never re-parses text the cut
 * site had already understood.
 */
data class AgentGoal(val text: String, val shape: GoalShape)
