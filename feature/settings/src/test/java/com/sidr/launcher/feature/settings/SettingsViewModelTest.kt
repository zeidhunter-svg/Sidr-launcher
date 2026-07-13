package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeSuggestionScheduling
import com.sidr.launcher.core.testing.FakeUserPreferencesRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabling ai suggestions updates flag and re-syncs scheduling`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(aiSuggestionsEnabled = false))
        val scheduling = FakeSuggestionScheduling()
        val vm = buildViewModel(flagRepo, scheduling = scheduling)

        vm.setAiSuggestionsEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertEquals(1, scheduling.ensureScheduledCount)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `disabling ai suggestions still re-syncs scheduling`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(aiSuggestionsEnabled = true))
        val scheduling = FakeSuggestionScheduling()
        val vm = buildViewModel(flagRepo, scheduling = scheduling)

        vm.setAiSuggestionsEnabled(false)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertEquals(1, scheduling.ensureScheduledCount)
    }

    @Test
    fun `unchanged ai suggestions setting skips flag write but still re-syncs scheduling`() =
        runTest(testDispatcher) {
            val flagRepo = FakeFeatureFlagRepository(FeatureFlags(aiSuggestionsEnabled = true))
            val scheduling = FakeSuggestionScheduling()
            val vm = buildViewModel(flagRepo, scheduling = scheduling)

            vm.setAiSuggestionsEnabled(true)
            advanceUntilIdle()

            assertTrue(flagRepo.getFlags().first().aiSuggestionsEnabled)
            assertEquals(0, flagRepo.updateCount)
            assertEquals(1, scheduling.ensureScheduledCount)
            assertEquals(null, vm.uiState.value.errorMessage)
        }

    @Test
    fun `flag write failure surfaces a safe error and does not re-sync`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(aiSuggestionsEnabled = false)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val scheduling = FakeSuggestionScheduling()
        val vm = buildViewModel(flagRepo, scheduling = scheduling)

        vm.setAiSuggestionsEnabled(true)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
        assertEquals(0, scheduling.ensureScheduledCount)
    }

    @Test
    fun `enabling usage history updates the flag and reflects in state`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false))
        val vm = buildViewModel(flagRepo)

        vm.setUsageHistoryEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().usageHistoryEnabled)
        assertTrue(vm.uiState.value.usageHistoryEnabled)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `usage history write failure surfaces a safe error`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(flagRepo)

        vm.setUsageHistoryEnabled(true)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().usageHistoryEnabled)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `unchanged usage history setting skips flag write`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))
        val vm = buildViewModel(flagRepo)

        vm.setUsageHistoryEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().usageHistoryEnabled)
        assertEquals(0, flagRepo.updateCount)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `enabling the llm router updates the flag and reflects in state`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(llmRouterEnabled = false))
        val vm = buildViewModel(flagRepo)

        vm.setLlmRouterEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().llmRouterEnabled)
        assertTrue(vm.uiState.value.llmRouterEnabled)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `llm router write failure surfaces a safe error`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(llmRouterEnabled = false)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(flagRepo)

        vm.setLlmRouterEnabled(true)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().llmRouterEnabled)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `unchanged llm router setting skips flag write`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(FeatureFlags(llmRouterEnabled = true))
        val vm = buildViewModel(flagRepo)

        vm.setLlmRouterEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().llmRouterEnabled)
        assertEquals(0, flagRepo.updateCount)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `selecting a theme persists the preference`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(themeName = "system"))
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setThemeName(ThemeOption.DARK)
        advanceUntilIdle()

        assertEquals("dark", prefsRepo.getPreferences().first().themeName)
        assertEquals("dark", vm.uiState.value.themeName)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `theme write failure surfaces a safe error`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(themeName = "system")).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setThemeName(ThemeOption.LIGHT)
        advanceUntilIdle()

        assertEquals("system", prefsRepo.getPreferences().first().themeName)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `selecting an accent persists the preference and reflects in state`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(accentColor = AccentOption.GREEN))
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setAccentColor(AccentOption.AMBER)
        advanceUntilIdle()

        assertEquals("amber", prefsRepo.getPreferences().first().accentColor)
        assertEquals("amber", vm.uiState.value.accentColor)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `accent write failure surfaces a safe error`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(accentColor = AccentOption.GREEN)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setAccentColor(AccentOption.AMBER)
        advanceUntilIdle()

        assertEquals("green", prefsRepo.getPreferences().first().accentColor)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `selecting a favorites count persists the preference`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(favoritesCount = 8))
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setFavoritesCount(4)
        advanceUntilIdle()

        assertEquals(4, prefsRepo.getPreferences().first().favoritesCount)
        assertEquals(4, vm.uiState.value.favoritesCount)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `favorites count write failure surfaces a safe error`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(favoritesCount = 8)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setFavoritesCount(10)
        advanceUntilIdle()

        assertEquals(8, prefsRepo.getPreferences().first().favoritesCount)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `toggling voice input off persists the preference`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(micInputEnabled = true))
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setMicInputEnabled(false)
        advanceUntilIdle()

        assertFalse(prefsRepo.getPreferences().first().micInputEnabled)
        assertFalse(vm.uiState.value.micInputEnabled)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `voice input write failure surfaces a safe error`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(micInputEnabled = true)).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setMicInputEnabled(false)
        advanceUntilIdle()

        assertTrue(prefsRepo.getPreferences().first().micInputEnabled)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `unchanged user preference settings skip preference writes`() = runTest(testDispatcher) {
        val prefsRepo = FakeUserPreferencesRepository(
            UserPreferences(
                themeName = ThemeOption.SYSTEM,
                favoritesCount = 8,
                micInputEnabled = true,
            ),
        )
        val vm = buildViewModel(prefsRepo = prefsRepo)

        vm.setThemeName(ThemeOption.SYSTEM)
        vm.setFavoritesCount(8)
        vm.setMicInputEnabled(true)
        advanceUntilIdle()

        assertEquals(0, prefsRepo.updateCount)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `assistant provider entry emits navigation to the dedicated provider route`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.openAssistantProvider()
        val event = vm.navigationEvents.first()

        assertEquals(NavigationEvent.NavigateTo(Routes.AssistantProvider.ROUTE), event)
    }

    @Test
    fun `learned choices entry emits navigation to learned choices route`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.openLearnedChoices()
        val event = vm.navigationEvents.first()

        assertEquals(NavigationEvent.NavigateTo(Routes.LearnedChoices.ROUTE), event)
    }

    @Test
    fun `aliases entry emits navigation to aliases route`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.openAliases()
        val event = vm.navigationEvents.first()

        assertEquals(NavigationEvent.NavigateTo(Routes.Aliases.ROUTE), event)
    }

    @Test
    fun `back action emits navigate back`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.navigateBack()
        val event = vm.navigationEvents.first()

        assertEquals(NavigationEvent.NavigateBack, event)
    }

    private fun buildViewModel(
        flagRepo: FakeFeatureFlagRepository = FakeFeatureFlagRepository(),
        prefsRepo: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        scheduling: FakeSuggestionScheduling = FakeSuggestionScheduling(),
    ): SettingsViewModel = SettingsViewModel(
        featureFlagRepository = flagRepo,
        userPreferencesRepository = prefsRepo,
        suggestionScheduling = scheduling,
        ioDispatcher = testDispatcher,
    )
}
