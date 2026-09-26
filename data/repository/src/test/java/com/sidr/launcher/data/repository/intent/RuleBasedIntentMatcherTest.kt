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

    // --- URL / site recognition (AIL-2, R6) ---

    @Test fun `bare known-TLD domain returns OpenUrlIntent with scheme`() = runTest {
        val best = matcher.match("github.com").best
        val intent = best.intent as LauncherIntent.OpenUrlIntent
        assertEquals("https://github.com", intent.url)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `explicit https url returns OpenUrlIntent preserving the url`() = runTest {
        val intent = matcher.match("https://example.com/docs").best.intent as LauncherIntent.OpenUrlIntent
        assertEquals("https://example.com/docs", intent.url)
    }

    @Test fun `open plus url opens the site not an app launch`() = runTest {
        val intent = matcher.match("open github.com").best.intent
        assertTrue("expected OpenUrlIntent, got $intent", intent is LauncherIntent.OpenUrlIntent)
        assertEquals("https://github.com", (intent as LauncherIntent.OpenUrlIntent).url)
    }

    @Test fun `open plus app name still launches the app`() = runTest {
        val intent = matcher.match("open telegram").best.intent
        assertTrue(intent is LauncherIntent.LaunchAppIntent)
        assertEquals("telegram", (intent as LauncherIntent.LaunchAppIntent).displayNameQuery)
    }

    @Test fun `ambiguous unknown-TLD domain falls back to web search`() = runTest {
        val best = matcher.match("example.foobar").best
        val intent = best.intent as LauncherIntent.SearchIntent
        assertEquals("example.foobar", intent.query)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `punycode host is not opened silently`() = runTest {
        val intent = matcher.match("xn--80ak6aa92e.com").best.intent
        assertTrue("expected SearchIntent (never a silent open), got $intent", intent is LauncherIntent.SearchIntent)
    }

    @Test fun `url with query string routes to web search`() = runTest {
        val intent = matcher.match("youtube.com/watch?v=abc").best.intent
        assertTrue(intent is LauncherIntent.SearchIntent)
    }

    // --- install verb → Play Store (AIL-2, Q3) ---

    @Test fun `install app returns PlayStoreSearchIntent`() = runTest {
        val best = matcher.match("install whatsapp").best
        val intent = best.intent as LauncherIntent.PlayStoreSearchIntent
        assertEquals("whatsapp", intent.query)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `bare install returns UnknownIntent`() = runTest {
        assertTrue(matcher.match("install").best.intent is LauncherIntent.UnknownIntent)
    }

    @Test fun `download verb does not trigger Play Store (install only)`() = runTest {
        // Q3 chose "install" only — "download X" is left to the normal rules (Unknown here).
        val intent = matcher.match("download manager").best.intent
        assertTrue("expected UnknownIntent for 'download manager', got $intent", intent is LauncherIntent.UnknownIntent)
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

    // --- ru/tr locale forms (agentic restart plan, Этап 0.2) ---
    // Table-driven parity: ru (SVO prefix, like en) and tr (SOV suffix, e.g. "telegramı aç")
    // must reach the same intent type at the same 0.90 confidence as their English analogues.

    private data class LocaleCase(
        val input: String,
        val expectLaunchApp: Boolean = false,
        val expectSearch: Boolean = false,
    )

    @Test fun `ru and tr forms match english confidence parity`() = runTest {
        val cases = listOf(
            LocaleCase("open telegram", expectLaunchApp = true),
            LocaleCase("открой телеграм", expectLaunchApp = true),
            LocaleCase("открыть телеграм", expectLaunchApp = true),
            LocaleCase("запусти телеграм", expectLaunchApp = true),
            LocaleCase("запустить телеграм", expectLaunchApp = true),
            LocaleCase("telegramı aç", expectLaunchApp = true),
            LocaleCase("search weather", expectSearch = true),
            LocaleCase("найди погоду", expectSearch = true),
            LocaleCase("найти погоду", expectSearch = true),
            LocaleCase("hava durumu ara", expectSearch = true),
        )
        cases.forEach { case ->
            val best = matcher.match(case.input).best
            when {
                case.expectLaunchApp -> assertTrue(
                    "Expected LaunchAppIntent for '${case.input}', got ${best.intent}",
                    best.intent is LauncherIntent.LaunchAppIntent,
                )
                case.expectSearch -> assertTrue(
                    "Expected SearchIntent for '${case.input}', got ${best.intent}",
                    best.intent is LauncherIntent.SearchIntent,
                )
            }
            assertTrue(
                "Expected confidence >= 0.85 for '${case.input}', got ${best.confidence}",
                best.confidence >= 0.85f,
            )
        }
    }

    @Test fun `ru install verb returns PlayStoreSearchIntent`() = runTest {
        val best = matcher.match("установи вотсап").best
        val intent = best.intent as LauncherIntent.PlayStoreSearchIntent
        assertEquals("вотсап", intent.query)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `tr install verb suffix form returns PlayStoreSearchIntent`() = runTest {
        val best = matcher.match("whatsapp kur").best
        val intent = best.intent as LauncherIntent.PlayStoreSearchIntent
        assertEquals("whatsapp", intent.query)
        assertTrue(best.confidence >= 0.85f)
    }

    @Test fun `ru bare settings keyword returns OpenSettingsIntent`() = runTest {
        assertTrue(matcher.match("настройки").best.intent is LauncherIntent.OpenSettingsIntent)
    }

    @Test fun `tr bare settings keyword returns OpenSettingsIntent`() = runTest {
        assertTrue(matcher.match("ayarlar").best.intent is LauncherIntent.OpenSettingsIntent)
    }

    @Test fun `ru simple commands return matching SimpleCommandIntent`() = runTest {
        val intent = matcher.match("показать приложения").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.SHOW_APPS, intent.command)
    }

    @Test fun `tr simple commands return matching SimpleCommandIntent`() = runTest {
        val intent = matcher.match("uygulamaları göster").best.intent as LauncherIntent.SimpleCommandIntent
        assertEquals(SimpleCommand.SHOW_APPS, intent.command)
    }

    @Test fun `bare tr launch verb returns UnknownIntent`() = runTest {
        val best = matcher.match("aç").best
        assertTrue(best.intent is LauncherIntent.UnknownIntent)
        assertTrue(best.confidence < 0.50f)
    }
}
