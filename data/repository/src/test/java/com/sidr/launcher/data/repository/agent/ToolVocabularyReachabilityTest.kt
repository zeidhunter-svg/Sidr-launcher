package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1' Task 8 — the guard that keeps a **green locale table over a dead capability** from happening.
 *
 * `ToolVocabularyLocaleGuardTest` proves every tool carries `en`/`ru`/`tr` triggers. That says nothing
 * about whether a user can reach them, because FastPath runs **first**: `RouteCommandUseCase` only
 * builds a `GoalShape.Free` goal when FastPath left the command undecided, and FastPath's verb table
 * lives in this same module. A trigger FastPath claims first is a capability that exists in the table
 * and nowhere else.
 *
 * That is not hypothetical. Every one of these was a trigger in this task's brief, and every one is
 * swallowed before the planner is ever asked:
 *  - `"open system settings"` → `LAUNCH_VERBS` `en` prefix `"open"` → `LaunchAppIntent("system settings")`;
 *  - `"открой настройки системы"` → `LAUNCH_VERBS` `ru` prefix `"открой"`;
 *  - `"sistem ayarlarını aç"` / `"android ayarlarını aç"` → `LAUNCH_VERBS` `tr` suffix `"aç"`;
 *  - `"zamanlayıcı kur"` / `"sayaç kur"` → `INSTALL_VERBS` `tr` suffix `"kur"` → a Play Store search.
 *
 * The last two mattered most: with them, Turkish had a full locale column and **zero** reachable
 * triggers for either tool.
 *
 * **What this guard checks, stated no wider than it is true.** It asserts that the sample command built
 * from each trigger leaves `RuleBasedIntentMatcher` at `LauncherIntent.UnknownIntent` — the intent that
 * `HandleUserCommandUseCase` maps to `CommandOutcome.Unknown`, which is one of the two states
 * `RouteCommandUseCase` calls undecided. It does not model the confidence policy, and it says nothing
 * about a trigger's *usefulness* — only that FastPath does not claim it first.
 *
 * **The shadowing guard, narrowed by Task 10b — said here, not just at the point of failure.**
 * `ToolSelector`'s leftover-word rule (Task 10b) requires every token of a command to be accounted for
 * by the matched shortcut's own name or its app's qualifier. An **argument-carrying** entry's sample
 * command always trails a token neither can ever cover — `SAMPLE_ARGUMENT`, standing in for whatever the
 * tool needs — so such an entry is now **structurally unshadowable**: no third-party name, however
 * chosen, can collide with it. That is a strengthening of the product, not a weakening of anything here.
 * The consequence for this file is that `an authored trigger cannot be shadowed by a third-party shortcut
 * name` can only be proved non-vacuous against a **zero-argument** entry — [SHADOW_CANDIDATE] is chosen
 * accordingly, and the guard now exercises one entry shape rather than two, not both as before. It does
 * not mean shadowing itself has weakened; the opposite entry shape is the one that no longer needs
 * guarding, because the product now rules it out by construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolVocabularyReachabilityTest {

    private val fastPath = RuleBasedIntentMatcher()

    @Test
    fun `no vocabulary trigger is claimed by FastPath before the planner is asked`() = runTest {
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            entry.prefixByLocale.forEach { (locale, forms) ->
                forms.forEach { form ->
                    val command = if (entry.argName == null) form else "$form $SAMPLE_ARGUMENT"
                    failures += fastPathClaim(entry, locale, command)
                }
            }
            entry.suffixByLocale.forEach { (locale, forms) ->
                forms.forEach { form ->
                    val command = if (entry.argName == null) form else "$SAMPLE_ARGUMENT $form"
                    failures += fastPathClaim(entry, locale, command)
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The vocabulary must also still recognise the sample command it was asked about — otherwise the
     * assertion above could be satisfied by a trigger that matches nothing at all.
     */
    @Test
    fun `every trigger recognises its own sample command`() {
        val vocabulary = ToolVocabulary()
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            entry.prefixByLocale.forEach { (locale, forms) ->
                forms.forEach { form ->
                    val command = if (entry.argName == null) form else "$form $SAMPLE_ARGUMENT"
                    if (vocabulary.match(command)?.id != entry.id) {
                        failures += "'$command' (${entry.id.value}/$locale, prefix) is not recognised by the vocabulary"
                    }
                }
            }
            entry.suffixByLocale.forEach { (locale, forms) ->
                forms.forEach { form ->
                    val command = if (entry.argName == null) form else "$SAMPLE_ARGUMENT $form"
                    if (vocabulary.match(command)?.id != entry.id) {
                        failures += "'$command' (${entry.id.value}/$locale, suffix) is not recognised by the vocabulary"
                    }
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * **Non-vacuity floor, not just the shadowing assertion.** `guardedEntries()` pins ids and its own
     * KDoc blesses rewording, so this test's only real evidence that it tests anything is that
     * [SHADOW_CANDIDATE] actually collides with at least one generated command — today, `"system
     * settings"`, because `en`'s `open_system_settings` prefix form is the literal string `"system
     * settings"`. It must be a **zero-argument** entry's collider (see the class KDoc, "The shadowing
     * guard, narrowed by Task 10b"): since Task 10b, `set_timer`'s sample command always trails
     * `SAMPLE_ARGUMENT`, a token no third-party name or qualifier can ever account for under
     * `ToolSelector`'s leftover-word rule, so an argument-carrying entry cannot collide with anything
     * planted here any more. A reword of `open_system_settings`'s colliding form (a "legitimate
     * rewording", per `guardedEntries()`) would silently drop the collision count to zero and turn this
     * into a duplicate of `every trigger recognises its own sample command` that stays green even with
     * `ToolSelector`'s rule 1 (authored beats dynamic) deleted — the exact vacuity `guardedEntries()`'s
     * KDoc claims no test in this class can have. [collisionFloor] measures the same commands the
     * shadowing loop below builds, against the dynamic branch alone (an *empty* [ToolVocabulary] so
     * authored priority cannot mask the answer), and fails loudly rather than passing quietly if the
     * count ever reaches zero.
     */
    @Test
    fun `an authored trigger cannot be shadowed by a third-party shortcut name`() {
        val selector = ToolSelector(vocabulary = ToolVocabulary(), dynamicNames = namesOf(SHADOW_CANDIDATE))
        val commands = mutableListOf<String>()
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            entry.prefixByLocale.values.flatten().forEach { form ->
                val command = if (entry.argName == null) form else "$form $SAMPLE_ARGUMENT"
                commands += command
                if (selector.select(command)?.id != entry.id) failures += "prefix: $command"
            }
            entry.suffixByLocale.values.flatten().forEach { form ->
                val command = if (entry.argName == null) form else "$SAMPLE_ARGUMENT $form"
                commands += command
                if (selector.select(command)?.id != entry.id) failures += "suffix: $command"
            }
        }

        collisionFloor(commands)

        assertTrue(
            "an authored trigger stopped recognising its own sample once a shortcut claimed it:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /**
     * Fails loudly, not quietly, the moment [SHADOW_CANDIDATE] stops colliding with anything in
     * [commands] — see the test's own KDoc for why a silent zero would be worse than no test at all.
     * [SHADOW_CANDIDATE] must collide with a **zero-argument** entry (see the class KDoc, "The
     * shadowing guard, narrowed by Task 10b"): an argument-carrying entry's sample command always
     * trails a token nothing planted here can account for, under `ToolSelector`'s leftover-word rule.
     */
    private fun collisionFloor(commands: List<String>) {
        val dynamicOnly = ToolSelector(vocabulary = ToolVocabulary(emptyList()), dynamicNames = namesOf(SHADOW_CANDIDATE))
        val collisions = commands.count { dynamicOnly.select(it)?.id == SHADOW_CANDIDATE.id }
        assertTrue(
            "the planted shadow candidate (qualifier '${SHADOW_CANDIDATE.qualifier}', name " +
                "'${SHADOW_CANDIDATE.name}') no longer collides with any of the ${commands.size} " +
                "generated commands — this test is no longer testing shadowing, and the planted name " +
                "must be re-chosen to collide with a real authored trigger again. It must collide with a " +
                "zero-argument entry: since Task 10b, an argument-carrying entry's sample command always " +
                "trails a sample-argument token that ToolSelector's leftover-word rule requires be " +
                "accounted for, and no shortcut name or qualifier can ever cover it.",
            collisions > 0,
        )
    }

    /**
     * The entry table — returned only **after** proving there is something to loop over.
     *
     * All three tests in this class run `forEach`es over the table, so an empty or truncated table
     * makes this guard pass while checking nothing — and what it would stop checking is the property
     * the whole task turns on: that FastPath does not claim a trigger before the planner is asked.
     * Emptying the vocabulary undoes Task 8 entirely and would leave this green. Asserting the floor
     * **here** rather than in one extra `@Test` means no individual test in this class can be vacuous,
     * including one added later by someone who never read this comment.
     *
     * On the choice of floor: it is *containment* of the two tools A1' shipped, not equality with the
     * table and not any trigger string. `A1"`'s twelfth entry and every legitimate rewording must stay
     * green. See [ToolVocabularyLocaleGuardTest.guardedEntries] for the full reasoning; the two are
     * deliberately separate rather than shared, so each guard states in its own words what it loses.
     */
    private fun guardedEntries(): List<ToolVocabulary.Entry> {
        val entries = ToolVocabulary().entries
        val missing = REQUIRED_TOOLS.filterNot { required -> entries.any { it.id == required } }
        assertTrue(
            "ToolVocabulary has no entry for ${missing.joinToString { it.value }} " +
                "(the table holds ${entries.size} entr${if (entries.size == 1) "y" else "ies"}). " +
                "All three tests in this guard loop over that table, so it would otherwise report " +
                "'no trigger is claimed by FastPath' after examining no triggers at all.",
            missing.isEmpty(),
        )
        return entries
    }

    private suspend fun fastPathClaim(
        entry: ToolVocabulary.Entry,
        locale: String,
        command: String,
    ): List<String> {
        val intent = fastPath.match(CommandNormalizer.normalize(command)).best.intent
        return if (intent is LauncherIntent.UnknownIntent) {
            emptyList()
        } else {
            listOf(
                "'$command' (${entry.id.value}/$locale) is claimed by FastPath as " +
                    "${intent::class.simpleName} — the planner is never asked, so this trigger cannot fire",
            )
        }
    }

    private companion object {
        /** Stands in for whatever an argument-carrying tool needs; FastPath's rules do not read it. */
        const val SAMPLE_ARGUMENT = "10 minutes"

        /** The floor, never the ceiling — see [guardedEntries]. */
        val REQUIRED_TOOLS = listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS)

        /**
         * Chosen to collide with `open_system_settings`'s `en` prefix form `"system settings"`
         * byte-for-byte — see `collisionFloor` for why that collision is verified rather than assumed.
         * Deliberately a **zero-argument** entry: Task 10b's leftover-word rule means an argument-carrying
         * entry's sample command always trails a token (`SAMPLE_ARGUMENT`) that no third-party name or
         * qualifier can account for, so `set_timer` — this candidate's original target — can no longer
         * collide with anything planted here. See the class KDoc, "The shadowing guard, narrowed by
         * Task 10b", for what that costs this guard.
         */
        val SHADOW_CANDIDATE = DynamicToolName(ToolId("shortcut:com.x/s"), "Settings", "system settings")
    }
}
