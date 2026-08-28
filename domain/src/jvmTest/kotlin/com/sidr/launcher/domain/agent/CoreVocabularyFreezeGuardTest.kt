package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ObservedFact
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The record-don't-fix decision of A0.5, held mechanically.**
 *
 * A0.5's whole scope decision (spec §2, Approach A) is that the core's launcher-shaped vocabularies
 * are *recorded with an address*, not repaired — because A1' has not arrived and Master Plan §3.4
 * forbids building a contract for a consumer that has not come. The predictable way that decision
 * gets reversed by accident is an implementer meeting one of these closed types, reading it as a gap,
 * and adding a value. This test is what makes that a conscious act.
 *
 * If you are here because one of these went red: adding a value to either type is **change-control**
 * (Master Plan §5 for `GoalShape` via §3.6 `B1`; the A1 fork for `ObservedFact`). Take it to the
 * owner. Do not update the expectation to match your edit.
 */
class CoreVocabularyFreezeGuardTest {

    /**
     * `ObservedFact` is the type A0.5 most wants to grow and must not: "the file is not there" has no
     * value here, which is exactly the finding spec §6.3 records against A1'.
     */
    @Test
    fun `ObservedFact still holds exactly the two values A0 shipped`() {
        assertEquals(
            "Adding an ObservedFact value decides the A1 fork by drift and reverses A0.5's " +
                "record-don't-fix decision (spec §2, §6.3). It is an owner decision, not a fix.",
            listOf("APP_NOT_INSTALLED", "APP_AMBIGUOUS"),
            ObservedFact.entries.map { it.name },
        )
    }

    /**
     * Master Plan §3.6 `B1` holds `GoalShape` at one value until A4'; A0.5 adds exactly one more, and
     * the argument for it (spec §7.1) is that `Free` is the *absence* of a shape and so cannot start a
     * taxonomy — there can never be a second `Free`. `B1` otherwise stands: no **recognised** shape may
     * be added before A4'.
     *
     * **What this test actually holds, stated plainly: an address and a message, not enforcement.** The
     * assertion below is a tautology — the list is built here from two literal constructions and then
     * mapped, so nothing an implementer does to `GoalShape` can make it fail at run time. The `when` is
     * exhaustive and has no `else`, so a third shape does make this file fail to **compile** — but it is
     * not the first thing to do so, and this file is therefore not the enforcement either. Enforcement
     * is the compiler's, spread across every else-free `when` over `GoalShape` in the tree, and the one
     * a third shape hits first is `TemplatePlanner.kt:45`, in `commonMain`, which compiles before this
     * source set exists. What this file adds is the place the message lands — the paragraph above, at
     * the address someone will actually be standing on. `B1` had no test before it and has none now.
     */
    @Test
    fun `GoalShape holds exactly the two shapes this project has decided on`() {
        val shapes: List<GoalShape> = listOf(
            GoalShape.AppNotInstalled(query = "x"),
            GoalShape.Free(text = "x"),
        )

        val names = shapes.map { shape ->
            when (shape) {
                is GoalShape.AppNotInstalled -> "AppNotInstalled"
                is GoalShape.Free -> "Free"
            }
        }

        assertEquals(listOf("AppNotInstalled", "Free"), names)
    }
}
