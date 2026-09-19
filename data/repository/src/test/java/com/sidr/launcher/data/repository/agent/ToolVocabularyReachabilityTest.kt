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
 * **When a dynamic candidate shadows an authored trigger's sample command — stated as a conjunction,
 * not a paraphrase, because a paraphrase here has drifted three times running (Task 10b review round
 * 2): first silence, then "structurally unshadowable," then "embeds the argument text itself," each
 * compression dropping a different term of the same conjunction.** `DynamicToolName.matches` (in
 * `ToolSelector`) selects a candidate exactly when all four of these hold, in the code's own terms:
 *  1. the candidate's normalized name is non-empty;
 *  2. that name appears as a contiguous, word-bounded run inside the normalized command;
 *  3. at least one of the candidate's qualifier tokens is present among the command's tokens; and
 *  4. every one of the command's tokens is present in the union of the candidate's name tokens and
 *     its qualifier tokens.
 *
 * That is an *iff* over the code, not a summary of it — drop any one condition and the sentence stops
 * describing `matches`.
 *
 * **One worked instance against an argument-carrying trigger, using the shape two rounds have missed:
 * the qualifier, not the name, covering the argument.** Against `set_timer`'s sample command
 * `"set timer for 10 minutes"`, a candidate with qualifier `"timer for 10 minutes"` and name `"set"`
 * satisfies all four: the name is non-empty and appears contiguously (`"set"` inside `"set timer for
 * 10 minutes"`); the qualifier token `"timer"` is present among the command's tokens; and the union
 * of name tokens `{set}` and qualifier tokens `{timer, for, 10, minutes}` covers every command token.
 * It shadows, and its name embeds none of the argument text.
 *
 * **The practical consequence, stated without an "only."** Covering an argument-carrying entry's
 * sample command requires the candidate's name and qualifier **together** to account for every
 * argument token, in some split between the two — which is why [SHADOW_CANDIDATE] here targets the
 * zero-argument `open_system_settings` instead: its floor collision does not depend on constructing
 * such a split against the literal `SAMPLE_ARGUMENT` constant. The shadowing assertion itself —
 * `an authored trigger cannot be shadowed by a third-party shortcut name` — still loops over all 14
 * commands generated from both entries, in both shapes, unchanged by Task 10b; only the floor's own
 * choice of collider changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolVocabularyReachabilityTest {

    private val fastPath = RuleBasedIntentMatcher()

    @Test
    fun `no vocabulary trigger is claimed by FastPath before the planner is asked`() = runTest {
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            sampleCommands(entry).forEach { (locale, _, command) ->
                failures += fastPathClaim(entry, locale, command)
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
            sampleCommands(entry).forEach { (locale, shape, command) ->
                if (vocabulary.match(command)?.id != entry.id) {
                    failures += "'$command' (${entry.id.value}/$locale, $shape) is not recognised by the vocabulary"
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
     * settings"`. [SHADOW_CANDIDATE] targets that **zero-argument** entry rather than `set_timer` — see
     * the class KDoc, "When a dynamic candidate shadows an authored trigger's sample command", for the
     * four-condition conjunction this choice is really about: covering `set_timer`'s sample command
     * needs a candidate whose name and qualifier together account for the argument tokens, which is
     * more to construct than reusing this entry's own zero-argument trigger text. A
     * reword of `open_system_settings`'s colliding form (a "legitimate rewording", per
     * `guardedEntries()`) would silently drop the collision count to zero and turn this into a
     * duplicate of `every trigger recognises its own sample command` that stays green even with
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
            sampleCommands(entry).forEach { (_, shape, command) ->
                commands += command
                if (selector.select(command)?.id != entry.id) failures += "$shape: $command"
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
     * A replacement need not target a zero-argument entry — see the class KDoc, "When a dynamic
     * candidate shadows an authored trigger's sample command", for the four-condition conjunction a
     * replacement must satisfy — it just must actually collide with *something* in [commands].
     */
    private fun collisionFloor(commands: List<String>) {
        val dynamicOnly = ToolSelector(vocabulary = ToolVocabulary(emptyList()), dynamicNames = namesOf(SHADOW_CANDIDATE))
        val collisions = commands.count { dynamicOnly.select(it)?.id == SHADOW_CANDIDATE.id }
        assertTrue(
            "the planted shadow candidate (qualifier '${SHADOW_CANDIDATE.qualifier}', name " +
                "'${SHADOW_CANDIDATE.name}') no longer collides with any of the ${commands.size} " +
                "generated commands — this test is no longer testing shadowing, and the planted " +
                "candidate must be re-chosen. The four conditions a replacement must satisfy are " +
                "documented on this class's KDoc and on collisionFloor's own KDoc — a replacement " +
                "must satisfy all four.",
            collisions > 0,
        )
    }

    /**
     * One sample command per declared form of [entry], shared by all three tests in this class
     * (controller ruling R14-21) so a two-slot arm is written once rather than three times.
     *
     * A zero-argument trigger is the bare form. A one-slot trigger appends (prefix) or prepends
     * (suffix) [SAMPLE_ARGUMENT]. A **two-slot** trigger (`entry.secondArgName != null`) needs the
     * infix inside the remainder for [ToolVocabulary.match] to recognise it at all — building
     * `"$form $SAMPLE_ARGUMENT"` for such an entry would leave the sample with no infix, and
     * `twoSlotMatch` would correctly refuse it for a reason that has nothing to do with reachability
     * (review finding I3, task-8-brief.md Step 4b). The suffix shape puts the infix inside the
     * remainder the same way, which is what makes a Turkish SOV two-slot form expressible at all.
     */
    private fun sampleCommands(entry: ToolVocabulary.Entry): List<Triple<String, String, String>> {
        val commands = mutableListOf<Triple<String, String, String>>()
        entry.prefixByLocale.forEach { (locale, forms) ->
            forms.forEach { form ->
                val command = when {
                    entry.secondArgName != null -> {
                        val infix = entry.infixByLocale.values.first().first()
                        "$form $SAMPLE_ARGUMENT $infix $SAMPLE_ARGUMENT_2"
                    }
                    entry.argName == null -> form
                    else -> "$form $SAMPLE_ARGUMENT"
                }
                commands += Triple(locale, "prefix", command)
            }
        }
        entry.suffixByLocale.forEach { (locale, forms) ->
            forms.forEach { form ->
                val command = when {
                    entry.secondArgName != null -> {
                        val infix = entry.infixByLocale.values.first().first()
                        "$SAMPLE_ARGUMENT $infix $SAMPLE_ARGUMENT_2 $form"
                    }
                    entry.argName == null -> form
                    else -> "$SAMPLE_ARGUMENT $form"
                }
                commands += Triple(locale, "suffix", command)
            }
        }
        return commands
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

        /** The second slot of a two-slot entry's sample command — see [sampleCommands]. */
        const val SAMPLE_ARGUMENT_2 = "20 minutes"

        /** The floor, never the ceiling — see [guardedEntries]. */
        val REQUIRED_TOOLS = listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS)

        /**
         * Chosen to collide with `open_system_settings`'s `en` prefix form `"system settings"`
         * byte-for-byte — see `collisionFloor` for why that collision is verified rather than assumed.
         * Targets that **zero-argument** entry rather than `set_timer` — this candidate's original
         * target, before Task 10b's leftover-word rule closed the collision that relied on ignoring
         * `SAMPLE_ARGUMENT` — so the floor's collision does not depend on constructing a candidate
         * whose name and qualifier together cover `set_timer`'s argument tokens. `set_timer` is not
         * unshadowable — see the class KDoc, "When a dynamic candidate shadows an authored trigger's
         * sample command", for the four-condition conjunction such a candidate would need to satisfy.
         */
        val SHADOW_CANDIDATE = DynamicToolName(ToolId("shortcut:com.x/s"), "Settings", "system settings")
    }
}
