package com.sidr.launcher.domain.intent

import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentActionResolverTest {

    private val fakeRepo = FakeInstalledAppsRepository()
    private val resolver = IntentActionResolver(fakeRepo)

    @Before fun setUp() = fakeRepo.reset()

    // --- LaunchAppIntent: exact match ---

    @Test fun `exact label match returns LaunchAppAction`() = runTest {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("telegram"))
        assertTrue(action is ExecutableAction.LaunchAppAction)
        assertEquals("org.telegram.messenger", (action as ExecutableAction.LaunchAppAction).packageName)
    }

    @Test fun `match is case-insensitive via Locale ROOT`() = runTest {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("TELEGRAM"))
        assertTrue(action is ExecutableAction.LaunchAppAction)
    }

    @Test fun `activityName is forwarded to LaunchAppAction`() = runTest {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("org.telegram.messenger", "Telegram", activityName = ".MainActivity")
        )
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("telegram")) as ExecutableAction.LaunchAppAction
        assertEquals(".MainActivity", action.activityName)
    }

    // --- LaunchAppIntent: ambiguity → AmbiguousAppAction, never auto-launch ---

    @Test fun `two apps with same label returns AmbiguousAppAction`() = runTest {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("org.telegram.messenger", "Telegram"),
            InstalledApp("org.telegram.messenger.beta", "Telegram"),
        )
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("telegram"))
        assertTrue(action is ExecutableAction.AmbiguousAppAction)
        assertEquals(2, (action as ExecutableAction.AmbiguousAppAction).candidates.size)
    }

    @Test fun `AmbiguousAppAction carries original query`() = runTest {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Maps"),
            InstalledApp("com.b", "Maps"),
        )
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("maps")) as ExecutableAction.AmbiguousAppAction
        assertEquals("maps", action.query)
    }

    // --- LaunchAppIntent: not found → ShowMessageAction ---

    @Test fun `no matching app returns ShowMessageAction`() = runTest {
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("whatsapp"))
        assertTrue(action is ExecutableAction.ShowMessageAction)
    }

    @Test fun `empty app list returns ShowMessageAction`() = runTest {
        fakeRepo.appsToReturn = emptyList()
        val action = resolveSuccess(LauncherIntent.LaunchAppIntent("telegram"))
        assertTrue(action is ExecutableAction.ShowMessageAction)
    }

    // --- LaunchAppIntent: repository failure propagates ---

    @Test fun `repository failure is returned as Failure not Success`() = runTest {
        fakeRepo.errorToReturn = OperationError.UnknownError("db crash")
        val result = resolver.resolve(LauncherIntent.LaunchAppIntent("telegram"))
        assertTrue(result is OperationResult.Failure)
    }

    // --- SearchIntent: SearchTarget flows through ---

    @Test fun `SearchIntent WEB target produces OpenSearchAction with WEB`() = runTest {
        val action = resolveSuccess(LauncherIntent.SearchIntent("weather", SearchTarget.WEB))
        val search = action as ExecutableAction.OpenSearchAction
        assertEquals("weather", search.query)
        assertEquals(SearchTarget.WEB, search.target)
    }

    @Test fun `SearchIntent APP target flows through to OpenSearchAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.SearchIntent("photos", SearchTarget.APP))
        assertEquals(SearchTarget.APP, (action as ExecutableAction.OpenSearchAction).target)
    }

    @Test fun `SearchIntent LOCAL target flows through to OpenSearchAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.SearchIntent("notes", SearchTarget.LOCAL))
        assertEquals(SearchTarget.LOCAL, (action as ExecutableAction.OpenSearchAction).target)
    }

    // --- OpenSettingsIntent ---

    @Test fun `OpenSettingsIntent returns OpenLauncherSettingsAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.OpenSettingsIntent())
        assertTrue(action is ExecutableAction.OpenLauncherSettingsAction)
    }

    // --- SimpleCommandIntent ---

    @Test fun `CLEAR returns NoOpAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.SimpleCommandIntent(SimpleCommand.CLEAR))
        assertTrue(action is ExecutableAction.NoOpAction)
    }

    @Test fun `SHOW_APPS returns ShowMessageAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS))
        assertTrue(action is ExecutableAction.ShowMessageAction)
    }

    @Test fun `HELP returns ShowMessageAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP))
        assertTrue(action is ExecutableAction.ShowMessageAction)
    }

    // --- OpenUrlIntent / PlayStoreSearchIntent (AIL-2) ---

    @Test fun `OpenUrlIntent returns OpenUrlAction carrying the url`() = runTest {
        val action = resolveSuccess(LauncherIntent.OpenUrlIntent("https://github.com"))
        assertTrue(action is ExecutableAction.OpenUrlAction)
        assertEquals("https://github.com", (action as ExecutableAction.OpenUrlAction).url)
    }

    @Test fun `PlayStoreSearchIntent returns PlayStoreSearchAction carrying the query`() = runTest {
        val action = resolveSuccess(LauncherIntent.PlayStoreSearchIntent("whatsapp"))
        assertTrue(action is ExecutableAction.PlayStoreSearchAction)
        assertEquals("whatsapp", (action as ExecutableAction.PlayStoreSearchAction).query)
    }

    // --- UnknownIntent ---

    @Test fun `UnknownIntent returns NoOpAction`() = runTest {
        val action = resolveSuccess(LauncherIntent.UnknownIntent("xyz"))
        assertTrue(action is ExecutableAction.NoOpAction)
    }

    // --- helpers ---

    private suspend fun resolveSuccess(intent: LauncherIntent): ExecutableAction {
        val result = resolver.resolve(intent)
        assertTrue("Expected Success but got $result", result is OperationResult.Success)
        return (result as OperationResult.Success).value
    }
}
