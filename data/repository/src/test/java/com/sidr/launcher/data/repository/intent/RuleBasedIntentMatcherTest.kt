package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedIntentMatcherTest {

    private val matcher = RuleBasedIntentMatcher()

    // --- launch verb: open / launch / start ---

    @Test fun `open telegram returns LaunchAppIntent with query telegram`() = runTest {
        val result = matcher.match("open telegram")
        val intent = result.best.intent as LauncherIntent.LaunchAppIntent
        assertEquals("telegram", intent.displayNameQuery)
        assertTrue(result.best.confidence >= 0.85f)
    }

    @Test fun `launch whatsapp returns LaunchAppIntent`() = runTest {
        val intent = matcher.match("launch whatsapp").best.intent
        assertTrue(intent is LauncherIntent.LaunchAppIntent)
        assertEquals("whatsapp", (intent as LauncherIntent.LaunchAppIntent).displayNameQuery)
    }

    @Test fun `start maps returns LaunchAppIntent`() = runTest {
        val intent = matcher.match("start maps").best.intent
        assertTrue(intent is LauncherIntent.LaunchAppIntent)
    }

    /**
     * Documented product decision: launch verb always produces LaunchAppIntent regardless of
     * keyword after it. "open settings" is a launch attempt (query="settings"), not
     * OpenSettingsIntent. Resolver will return ShowMessageAction if no app is found.
     * Behaviour to refine in Block D.
     */
    @Test fun `open settings returns LaunchAppIntent not OpenSettingsIntent`() = runTest {
        val intent = matcher.match("open settings").best.intent
        assertTrue(intent is LauncherIntent.LaunchAppIntent)
        assertEquals("settings", (intent as LauncherIntent.LaunchAppIntent).displayNameQuery)
    }

    @Test fun `launch settings returns LaunchAppIntent`() = runTest {
        val intent = matcher.match("launch settings").best.intent
        assertTrue(intent is LauncherIntent.LaunchAppIntent)
        assertEquals("settings", (intent as LauncherIntent.LaunchAppIntent).displayNameQuery)
    }

    @Test fun `bare open returns UnknownIntent with low confidence`() = runTest {
        val best = matcher.match("open").best
        assertTrue(best.intent is LauncherIntent.UnknownIntent)
        assertTrue(best.confidence < 0.50f)
    }

    @Test fun `bare launch returns UnknownIntent`() = runTest {
        assertTrue(matcher.match("launch").best.intent is LauncherIntent.UnknownIntent)
    }

    @Test fun `bare start returns UnknownIntent`() = runTest {
        assertTrue(matcher.match("start").best.intent is LauncherIntent.UnknownIntent)
    }

    // --- search verb: search / find / google ---

    @Test fun `search weather tomorrow returns SearchIntent`() = runTest {
        val best = matcher.match("search weather tomorrow").best
        val intent = best.intent as LauncherIntent.SearchIntent
        assertEquals("weather tomorrow", intent.query)
        assertEquals(SearchTarget.WEB, intent.target)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `find coffee near me returns SearchIntent`() = runTest {
        val intent = matcher.match("find coffee near me").best.intent
        assertTrue(intent is LauncherIntent.SearchIntent)
        assertEquals("coffee near me", (intent as LauncherIntent.SearchIntent).query)
    }

    @Test fun `google news returns SearchIntent`() = runTest {
        assertTrue(matcher.match("google news").best.intent is LauncherIntent.SearchIntent)
    }

    @Test fun `bare search returns UnknownIntent with low confidence`() = runTest {
        val best = matcher.match("search").best
        assertTrue(best.intent is LauncherIntent.UnknownIntent)
        assertTrue(best.confidence < 0.50f)
    }

    // --- settings bare keywords (no verb) ---

    @Test fun `bare settings returns OpenSettingsIntent with high confidence`() = runTest {
        val best = matcher.match("settings").best
        assertTrue(best.intent is LauncherIntent.OpenSettingsIntent)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `launcher settings returns OpenSettingsIntent`() = runTest {
        assertTrue(matcher.match("launcher settings").best.intent is LauncherIntent.OpenSettingsIntent)
    }

    // --- simple commands ---

    @Test fun `show apps returns SimpleCommandIntent SHOW_APPS`() = runTest {
        val intent = matcher.match("show apps").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.SHOW_APPS, intent.command)
    }

    @Test fun `show all apps returns SimpleCommandIntent SHOW_APPS`() = runTest {
        val intent = matcher.match("show all apps").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.SHOW_APPS, intent.command)
    }

    @Test fun `clear returns SimpleCommandIntent CLEAR`() = runTest {
        val intent = matcher.match("clear").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.CLEAR, intent.command)
    }

    @Test fun `clear input returns SimpleCommandIntent CLEAR`() = runTest {
        val intent = matcher.match("clear input").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.CLEAR, intent.command)
    }

    @Test fun `help returns SimpleCommandIntent HELP`() = runTest {
        val intent = matcher.match("help").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.HELP, intent.command)
    }

    @Test fun `assistant commands return SimpleCommandIntent OPEN_ASSISTANT`() = runTest {
        listOf("assistant", "show assistant").forEach { input ->
            val intent = matcher.match(input).best.intent as LauncherIntent.SimpleCommandIntent
            assertEquals("Expected OPEN_ASSISTANT for '$input'", SimpleCommand.OPEN_ASSISTANT, intent.command)
        }
    }

    // --- unknowns ---

    @Test fun `empty input returns UnknownIntent with zero confidence`() = runTest {
        val best = matcher.match("").best
        assertTrue(best.intent is LauncherIntent.UnknownIntent)
        assertEquals(0.0f, best.confidence)
    }

    @Test fun `unrecognized input returns UnknownIntent with low confidence`() = runTest {
        val best = matcher.match("xyz blah").best
        assertTrue(best.intent is LauncherIntent.UnknownIntent)
        assertTrue(best.confidence < 0.50f)
    }

    @Test fun `single unrecognized word returns UnknownIntent`() = runTest {
        assertTrue(matcher.match("flibbertigibbet").best.intent is LauncherIntent.UnknownIntent)
    }

    // --- source is always RULE_BASED ---

    @Test fun `all match results have RULE_BASED source`() = runTest {
        listOf("open telegram", "search weather", "settings", "help", "", "xyz").forEach { input ->
            assertEquals(
                "Expected RULE_BASED for '$input'",
                MatcherSource.RULE_BASED,
                matcher.match(input).source,
            )
        }
    }

    // --- normalizedInput is echoed back ---

    @Test fun `normalizedInput in result matches the input passed in`() = runTest {
        val input = "open telegram"
        assertEquals(input, matcher.match(input).normalizedInput)
    }
}
