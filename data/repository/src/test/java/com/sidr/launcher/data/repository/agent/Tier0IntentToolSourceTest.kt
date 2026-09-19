package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A1' Task 6: the federation's second real source. Two Android system intents that are not among the
 * frozen seven `ActionIds`, so they mint their own [com.sidr.launcher.domain.tool.ToolId]s rather than
 * projecting `ActionCatalog` the way [SystemIntentToolSource] does.
 *
 * A1" Phase 3a Task 2 adds the other half: what the source withholds. The two families of test below
 * are deliberately built on **opposite** fixtures, and which one a test gets is not a convenience —
 * see [grantsEverything].
 */
class Tier0IntentToolSourceTest {

    private class FakePresence(private val granted: Set<String>) : PermissionPresence {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    /**
     * The fixture for every test whose subject is the registry's **contents**.
     *
     * It is load-bearing, not a convenience. A source that filters by permission can make a
     * contents test pass by **absence**: the test would quantify over a list the filter had already
     * emptied and assert nothing, exactly the vacuous-guard shape this repository has recorded
     * before (`ToolVocabularyLocaleGuardTest`'s KDoc, and `DoctrineGuardTest.productionAdapters`'
     * floor). Granting everything keeps those tests strict — they see the full shipped set and
     * assert against it. A fixture that grants **nothing** belongs only in the three tests below
     * whose subject *is* the filter.
     */
    private val grantsEverything = PermissionPresence { true }

    /**
     * **The blanket `none { requiresConsent(it.risk) }` is gone, and its replacement is stronger rather
     * than weaker** (Task 7, A1″ Phase 3a). Until `uninstall_app` this source declared one risk for
     * everything it held, so a single quantifier said it all; `DoctrineGuardTest`'s own KDoc cites that
     * assertion as the reason a `SAFE → CONFIRM` mutation on a Tier-0 tool reddens `:data:repository`.
     * With two risk levels in one source a quantifier can no longer carry that, and dropping it would
     * quietly hand the whole pin to `:app`'s hand-written map. The per-id map below keeps the pin here,
     * and additionally catches the direction a quantifier never could: a tool silently dropping *to*
     * `SAFE`, which would take the consent gate off an act that cannot be undone.
     */
    @Test
    fun `the tier-0 source offers fifteen tools at the system-intent level, all external, one behind consent`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(
            listOf(
                Tier0ToolIds.SET_TIMER,
                Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
                Tier0ToolIds.SET_ALARM,
                Tier0ToolIds.UNINSTALL_APP,
                // A1″ Phase 3b, Task 2 (Slice A) — four navigating tools, all SAFE, none behind
                // consent. The "one behind consent" clause below stays true: uninstall_app remains
                // the only CONFIRM tool this source declares.
                Tier0ToolIds.SHOW_ALARMS,
                Tier0ToolIds.OPEN_CAMERA,
                Tier0ToolIds.OPEN_WIFI_SETTINGS,
                Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS,
                // A1″ Phase 3b, Task 3 (Slice B) — four more navigating tools, all SAFE.
                Tier0ToolIds.OPEN_BATTERY_SETTINGS,
                Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS,
                Tier0ToolIds.OPEN_DISPLAY_SETTINGS,
                Tier0ToolIds.OPEN_SOUND_SETTINGS,
                // A1″ Phase 3b, Task 4 (Slice C) — the last three navigating tools. Two are
                // zero-argument; `open_app_info` is the only argument-carrying one of the eleven.
                // All three SAFE, so the "one behind consent" clause stays true.
                Tier0ToolIds.OPEN_LOCATION_SETTINGS,
                Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS,
                Tier0ToolIds.OPEN_APP_INFO,
            ),
            source.all().map { it.id },
        )
        assertEquals(true, source.all().all { it.level == ToolLevels.SYSTEM_INTENT })
        assertEquals(true, source.all().all { it.effect == ToolEffect.EXTERNAL })
        assertEquals(
            mapOf(
                Tier0ToolIds.SET_TIMER to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_SYSTEM_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.SET_ALARM to ActionRiskLevel.SAFE,
                Tier0ToolIds.UNINSTALL_APP to ActionRiskLevel.CONFIRM,
                Tier0ToolIds.SHOW_ALARMS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_CAMERA to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_WIFI_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_BATTERY_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_DISPLAY_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_SOUND_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_LOCATION_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to ActionRiskLevel.SAFE,
                Tier0ToolIds.OPEN_APP_INFO to ActionRiskLevel.SAFE,
            ),
            source.all().associate { it.id to it.risk },
        )
        assertEquals(
            listOf(Tier0ToolIds.UNINSTALL_APP),
            source.all().filter { requiresConsent(it.risk) }.map { it.id },
        )
    }

    @Test
    fun `set_timer takes a required duration and open_system_settings takes nothing`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(listOf("duration"), source.find(Tier0ToolIds.SET_TIMER)!!.argSchema.map { it.name })
        assertEquals(emptyList<String>(), source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS)!!.argSchema.map { it.name })
    }

    @Test
    fun `set_alarm takes a required time`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(listOf("time"), source.find(Tier0ToolIds.SET_ALARM)!!.argSchema.map { it.name })
    }

    /**
     * Task 7. The first `CONFIRM` tool of the track and the first `DURABLE` one — and the two halves
     * of that pair are asserted together on purpose: `DURABLE` is what says the effect cannot be
     * undone, `CONFIRM` is what stops the loop for consent, and a tool that lost either while keeping
     * the other would read as safer than it is. (`DURABLE_EFFECT` is not what fires the checkpoint at
     * `CONFIRM` or above — `checkpointFor` takes the `RISK_LEVEL` branch first, the A0.5 finding this
     * block does not fix — so the user is stopped by `risk`, and `durability` is what the trace says.)
     *
     * **`app_label` is `required = false`, and that is load-bearing rather than tidy.**
     * `ToolMatchPlanner` checks required arguments **before** it resolves, and the vocabulary never
     * supplies `app_label` — the planner binds it one step later, from the raw text, *after*
     * resolution. A descriptor declaring it required would therefore return `NoPlan` for every goal
     * forever, and this tool would ship registered, reachable, matching its trigger and dead on every
     * invocation with the whole suite green. `ActionArg.required` defaults to `true`, so the
     * declaration has to be written out; this assertion is what keeps it written.
     */
    @Test
    fun `uninstall_app is CONFIRM and DURABLE, takes a package, and its label argument is not required`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)
        val descriptor = source.find(Tier0ToolIds.UNINSTALL_APP)!!

        assertEquals(ActionRiskLevel.CONFIRM, descriptor.risk)
        assertEquals(ToolDurability.DURABLE, descriptor.durability)
        assertEquals(true, requiresConsent(descriptor.risk))
        assertEquals(listOf("app", "app_label"), descriptor.argSchema.map { it.name })
        assertEquals(
            "app_label is bound by ToolMatchPlanner AFTER its required-argument check, so declaring " +
                "it required makes every goal NoPlan and the tool dead on arrival",
            listOf(true, false),
            descriptor.argSchema.map { it.required },
        )
    }

    /**
     * A1″ Phase 3b, Task 4 — **the SECOND consecutive per-descriptor `required` pin, and there is
     * still no generalisation.**
     *
     * The pin above holds `uninstall_app`'s `app_label` OPTIONAL (R14-35); this one holds
     * `open_app_info`'s `app` REQUIRED. They are opposite directions of the same gap, and the gap is
     * that `ToolMatchPlanner`'s resolution branch keys on the argument being **named** `app`, never on
     * whether it is `required`. So a descriptor declaring `app` optional supplies no value, `raw`
     * becomes `""`, `AppTargetResolver.resolve("")` declines, and the planner answers `NoPlan` — for
     * every goal, forever, with the whole suite green and the tool registered, reachable by its
     * trigger, and dead. Nothing in the type system holds either direction; these two tests are the
     * whole of the enforcement, and the generalisation is the `ArgType` debt (spec §7.5), owned by A4′.
     * Two instances is the evidence that block needs — the accumulation is deliberate, not an
     * omission.
     *
     * **`app_label` is deliberately absent**, which is why the schema is asserted by equality rather
     * than by containment: `uninstall_app` needs both arguments because its `CONFIRM` card must name
     * what the user said and what will be removed. `open_app_info` is `SAFE` and draws no card, so a
     * second argument would only route it down `uninstall_app`'s by-name branch in
     * `AgentSessionPresentation.line()` and hit the argument-**count** step-line limit.
     */
    @Test
    fun `open_app_info takes exactly one app argument and it is required`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)
        val descriptor = source.find(Tier0ToolIds.OPEN_APP_INFO)!!

        assertEquals(
            "open_app_info declares exactly one argument. Adding app_label would take it down " +
                "uninstall_app's by-name rendering branch for no gain (it is SAFE and draws no " +
                "consent card) and into the argument-count step-line limit",
            listOf("app"),
            descriptor.argSchema.map { it.name },
        )
        assertEquals(
            "ToolMatchPlanner resolves an argument NAMED app regardless of `required` (R14-37). " +
                "MEASURED 2026-09-20 (phase 3b mutation M3b), stated no wider than it is true: with " +
                "required=false planted, this tool still PLANS correctly, because its vocabulary " +
                "entry declares argName=\"app\" and refuses a bare trigger -- match(\"app info\") " +
                "is null, so `raw` is never empty and the flag only changes WHICH line returns " +
                "NoPlan. The pin is therefore against the WIRING changing, not against today's " +
                "behaviour: R14-37's failure mode (raw=\"\" -> resolve(\"\") -> null -> NoPlan for " +
                "every goal forever, suite green) arises for any descriptor whose vocabulary does " +
                "NOT supply `app` -- one vocabulary edit away. Keep it required and keep this " +
                "sentence honest.",
            listOf(true),
            descriptor.argSchema.map { it.required },
        )
    }

    /**
     * **The floor Task 2's own filter made necessary** (fix round 1, finding I1).
     *
     * Before Task 2, a Tier-0 tool with no [ToolPermissionCatalog] row was still *registered*, so
     * `ToolRegistryPermissionGuardTest`'s `every registered tool has a permission row` could see it and
     * go red. After Task 2 it is withheld by `available()`, so it is registered nowhere — and a guard
     * that quantifies over the registry has nothing left to find. That inverts the 2026-09-05 defect
     * from "registered but refused" into "silently never registered", which is worse: nothing is red
     * anywhere, because a newly-added id is not in that guard's hand-written `REQUIRED_TOOL_IDS`
     * either.
     *
     * The expected list is spelled as **its own literal** and never derived from
     * [ToolPermissionCatalog] — deriving it would make the assertion agree with itself whatever the
     * catalog says, which is the identical reason `REQUIRED_TOOL_IDS` is hand-written rather than
     * read from production.
     *
     * **Adding a Tier-0 tool means extending two lists**: this one, and `REQUIRED_TOOL_IDS` in both
     * `ToolRegistryPermissionGuardTest` and `DoctrineGuardTest` (`:app`). Equality, not containment,
     * so a descriptor that silently stops being registered is red here too.
     *
     * **What it catches and what it does not, measured rather than assumed.** Removing a shipped
     * tool's catalog row turns this RED — verified by deleting `SET_TIMER`'s row and watching it fail
     * (`expected:<[set_timer, open_system_settings]> but was:<[open_system_settings]>`). Adding a
     * descriptor *and* extending this list while forgetting the row is also RED. What it cannot catch
     * is a descriptor added with **neither** a row nor a line here: `available()` drops it, `all()` is
     * unchanged, and the equality still holds. Closing that last case needs the source to expose its
     * *declared* descriptors, which is a production API this fix round did not open.
     */
    @Test
    fun `every tool this source declares is registered when every permission is held`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything)

        assertEquals(
            "A Tier-0 descriptor with no ToolPermissionCatalog row is withheld at runtime and " +
                "registered nowhere, so no :app guard can see it. This list is where that absence " +
                "turns red. Adding a tool? Extend this list AND REQUIRED_TOOL_IDS in both :app guards.",
            listOf(
                Tier0ToolIds.SET_TIMER,
                Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
                Tier0ToolIds.SET_ALARM,
                Tier0ToolIds.UNINSTALL_APP,
                Tier0ToolIds.SHOW_ALARMS,
                Tier0ToolIds.OPEN_CAMERA,
                Tier0ToolIds.OPEN_WIFI_SETTINGS,
                Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS,
                Tier0ToolIds.OPEN_BATTERY_SETTINGS,
                Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS,
                Tier0ToolIds.OPEN_DISPLAY_SETTINGS,
                Tier0ToolIds.OPEN_SOUND_SETTINGS,
                Tier0ToolIds.OPEN_LOCATION_SETTINGS,
                Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS,
                Tier0ToolIds.OPEN_APP_INFO,
            ),
            source.all().map { it.id },
        )
    }

    @Test
    fun `a tool whose permission is not held is not advertised`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertEquals(emptyList<ToolId>(), source.all().map { it.id }.filter { it == Tier0ToolIds.SET_TIMER })
        assertNull(source.find(Tier0ToolIds.SET_TIMER))
    }

    @Test
    fun `a tool needing nothing is advertised even when no permission is held`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertNotNull(source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS))
    }

    @Test
    fun `find never answers for a tool all() withholds`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))
        val advertised = source.all().map { it.id }.toSet()

        listOf(
            Tier0ToolIds.SET_TIMER,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            Tier0ToolIds.SET_ALARM,
            Tier0ToolIds.UNINSTALL_APP,
            Tier0ToolIds.SHOW_ALARMS,
            Tier0ToolIds.OPEN_CAMERA,
            Tier0ToolIds.OPEN_WIFI_SETTINGS,
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS,
            Tier0ToolIds.OPEN_BATTERY_SETTINGS,
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS,
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS,
            Tier0ToolIds.OPEN_SOUND_SETTINGS,
            Tier0ToolIds.OPEN_LOCATION_SETTINGS,
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS,
            Tier0ToolIds.OPEN_APP_INFO,
        ).forEach { id ->
            assertEquals("find/all disagree for ${id.value}", id in advertised, source.find(id) != null)
        }
    }
}
