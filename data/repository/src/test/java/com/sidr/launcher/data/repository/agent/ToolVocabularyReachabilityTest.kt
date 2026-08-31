package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.LauncherIntent
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
     * The entry table — returned only **after** proving there is something to loop over.
     *
     * Both assertions in this class are `forEach`es over the table, so an empty or truncated table
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
                "Both assertions in this guard loop over that table, so it would otherwise report " +
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
    }
}
