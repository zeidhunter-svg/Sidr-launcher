package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPresentationTest {

    @Test fun unknown_command_maps_to_its_string_with_english_command_examples() {
        val text = requireNotNull(feedbackText(CommandFeedback.UnknownCommand))
        assertEquals(R.string.launcher_feedback_unknown_command, text.id)
        assertEquals(listOf("open <app>", "search <query>"), text.args)
    }

    @Test fun none_and_dev_console_are_not_resource_backed() {
        assertEquals(null, feedbackText(CommandFeedback.None))
        assertEquals(null, feedbackText(CommandFeedback.Message("dev console on")))
    }

    @Test fun every_voice_error_has_its_own_string() {
        val ids = SpeechRecognitionError.entries
            .map { requireNotNull(feedbackText(CommandFeedback.VoiceError(it))).id }
        assertEquals(SpeechRecognitionError.entries.size, ids.toSet().size)
    }

    @Test fun domain_message_is_mapped_not_passed_through() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.NoAppFound("telegram"))))
        assertEquals(R.string.launcher_feedback_no_app_found, text.id)
        assertEquals(listOf("telegram"), text.args)
    }

    // ── Additional coverage (I18N-1 Task 12 rulings) ────────────────────────

    @Test fun empty_input_maps_with_the_open_telegram_example() {
        val text = requireNotNull(feedbackText(CommandFeedback.EmptyInput))
        assertEquals(R.string.launcher_feedback_empty_input, text.id)
        assertEquals(listOf("open telegram"), text.args)
    }

    @Test fun low_confidence_maps_to_its_string_with_no_args() {
        val text = requireNotNull(feedbackText(CommandFeedback.LowConfidence))
        assertEquals(R.string.launcher_feedback_low_confidence, text.id)
        assertEquals(emptyList<String>(), text.args)
    }

    @Test fun ambiguous_maps_to_the_did_you_mean_header() {
        val text = requireNotNull(feedbackText(CommandFeedback.Ambiguous(emptyList())))
        assertEquals(R.string.launcher_feedback_ambiguous, text.id)
    }

    @Test fun help_carries_the_whole_four_part_command_grammar() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.Help)))
        assertEquals(R.string.launcher_message_help, text.id)
        assertEquals(listOf("open <app>", "search <query>", "show apps", "clear"), text.args)
    }

    @Test fun help_brief_carries_only_open_and_search() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.HelpBrief)))
        assertEquals(R.string.launcher_message_help_brief, text.id)
        assertEquals(listOf("open <app>", "search <query>"), text.args)
    }

    @Test fun showing_all_apps_and_assistant_coming_soon_carry_no_args() {
        assertEquals(
            R.string.launcher_message_showing_all_apps,
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.ShowingAllApps))).id,
        )
        assertEquals(
            R.string.launcher_message_assistant_soon,
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.AssistantComingSoon))).id,
        )
    }

    /**
     * A4′ phase 3b, Task 6: a value a tool's sort could not read («полчасика» is no duration) is said in
     * the words given for it — the whole [FeedbackText] by equality, so a sentence without them goes red.
     */
    @Test
    fun `a value not understood names the words`() {
        assertEquals(
            FeedbackText(R.string.launcher_message_value_not_understood, listOf("полчасика")),
            feedbackText(CommandFeedback.Domain(CommandMessage.ValueNotUnderstood("полчасика"))),
        )
    }

    @Test fun verbatim_is_a_passthrough_seam() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.Verbatim("model said hi"))))
        assertEquals(R.string.launcher_message_verbatim, text.id)
        assertEquals(listOf("model said hi"), text.args)
    }

    @Test fun every_command_failure_has_its_own_string() {
        val failures = listOf(
            CommandFailure.Generic,
            CommandFailure.CantOpenApp,
            CommandFailure.NoSearchApp,
            CommandFailure.CantOpenUrl,
            CommandFailure.NoStoreApp,
        )
        val ids = failures.map { requireNotNull(feedbackText(CommandFeedback.Failure(it))).id }
        assertEquals(failures.size, ids.toSet().size)
    }

    @Test fun every_suggested_intent_variant_maps_with_its_argument() {
        assertEquals(
            FeedbackText(R.string.launcher_suggest_launch_app, listOf("telegram")),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.LaunchApp("telegram"))),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_search, listOf("weather")),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.Search("weather"))),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_open_settings),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.OpenSettings)),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_simple_command),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.SimpleCommand)),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_open_url, listOf("https://x.com")),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.OpenUrl("https://x.com"))),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_play_store, listOf("telegram")),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.PlayStoreSearch("telegram"))),
        )
        assertEquals(
            FeedbackText(R.string.launcher_suggest_unknown),
            feedbackText(CommandFeedback.Suggestion(SuggestedIntent.Unknown)),
        )
    }

    @Test fun suggestion_variants_map_to_distinct_strings() {
        val ids = listOf(
            SuggestedIntent.LaunchApp("x"),
            SuggestedIntent.Search("x"),
            SuggestedIntent.OpenSettings,
            SuggestedIntent.SimpleCommand,
            SuggestedIntent.OpenUrl("x"),
            SuggestedIntent.PlayStoreSearch("x"),
            SuggestedIntent.Unknown,
        ).map { requireNotNull(feedbackText(CommandFeedback.Suggestion(it))).id }
        assertEquals(7, ids.toSet().size)
    }

    // ── AppDrawerError (feature-local typed error for AppDrawerViewModel) ───

    @Test fun app_drawer_permission_denied_carries_the_permission_argument() {
        val text = appDrawerErrorText(AppDrawerError.PermissionDenied("QUERY_ALL_PACKAGES"))
        assertEquals(R.string.launcher_drawer_permission_denied, text.id)
        assertEquals(listOf("QUERY_ALL_PACKAGES"), text.args)
    }

    @Test fun app_drawer_device_not_capable_carries_the_feature_argument() {
        val text = appDrawerErrorText(AppDrawerError.DeviceNotCapable("multi_window"))
        assertEquals(R.string.launcher_drawer_not_supported, text.id)
        assertEquals(listOf("multi_window"), text.args)
    }

    @Test fun app_drawer_errors_map_to_distinct_strings() {
        val ids = setOf(
            appDrawerErrorText(AppDrawerError.PermissionDenied("p")).id,
            appDrawerErrorText(AppDrawerError.DeviceNotCapable("f")).id,
        )
        assertEquals(2, ids.size)
    }

    /**
     * Этап 4.0 — the three understanding-unavailable states must resolve to three DIFFERENT strings.
     * Asserting each id individually would pass even if two branches were copy-pasted onto the same
     * resource, which is the mistake worth catching: three states that read identically are one state
     * wearing three names, and the user learns nothing about which fix applies to them.
     */
    @Test
    fun `the three understanding-unavailable states resolve to three distinct strings`() {
        val ids = listOf(
            CommandMessage.UnderstandingLocalOnly,
            CommandMessage.UnderstandingNeedsProvider,
            CommandMessage.UnderstandingNeedsNetwork,
        ).map { requireNotNull(feedbackText(CommandFeedback.Domain(it))).id }

        assertEquals(
            "each unavailable-state needs its own sentence naming its own fix",
            3,
            ids.toSet().size,
        )
        assertEquals(
            R.string.launcher_understanding_local_only,
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.UnderstandingLocalOnly))).id,
        )
        assertEquals(
            R.string.launcher_understanding_needs_provider,
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.UnderstandingNeedsProvider))).id,
        )
        assertEquals(
            R.string.launcher_understanding_needs_network,
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.UnderstandingNeedsNetwork))).id,
        )
    }

    /**
     * A4′ acceptance tail, task 5: the model's refusal of a command it read is its own sentence — not «unknown
     * command» (`launcher_feedback_unknown_command`), not one of the four «understanding unavailable» states — and
     * offers no provider setup (nothing there to fix: understanding worked).
     */
    @Test
    fun `a command the model declined says it was understood and cannot be done yet`() {
        val text = requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.UnderstoodNoAction)))

        assertEquals(R.string.launcher_model_declined, text.id)
        assertEquals(emptyList<Any>(), text.args)
        assertFalse(CommandMessage.UnderstoodNoAction.offersProviderSetup())
    }

    /** Finding (c) of the A1″ acceptance: each reason the model gave nothing usable is its own sentence. */
    @Test
    fun `each model-unavailable reason has its own sentence`() {
        val expected = mapOf(
            ModelUnavailableReason.CREDENTIALS to R.string.launcher_model_unavailable_credentials,
            ModelUnavailableReason.REQUEST_REJECTED to R.string.launcher_model_unavailable_request_rejected,
            ModelUnavailableReason.PROVIDER_FAILING to R.string.launcher_model_unavailable_provider_failing,
            ModelUnavailableReason.TIMED_OUT to R.string.launcher_model_unavailable_timed_out,
            ModelUnavailableReason.NETWORK to R.string.launcher_model_unavailable_network,
            ModelUnavailableReason.UNREADABLE to R.string.launcher_model_unavailable_unreadable,
        )
        assertEquals("the table must name every reason", ModelUnavailableReason.entries.toSet(), expected.keys)
        val actual = ModelUnavailableReason.entries.associateWith { reason ->
            requireNotNull(feedbackText(CommandFeedback.Domain(CommandMessage.UnderstandingModelUnavailable(reason)))).id
        }
        assertEquals(expected, actual)
        assertEquals(ModelUnavailableReason.entries.size, actual.values.toSet().size)
    }

    /** The one affordance (Этап 4.0, F1) is offered exactly where the user can fix it in the provider screen. */
    @Test
    fun `the provider-setup affordance follows the reasons the user can fix there`() {
        val fixableThere = setOf(ModelUnavailableReason.CREDENTIALS, ModelUnavailableReason.REQUEST_REJECTED)
        ModelUnavailableReason.entries.forEach { reason ->
            assertEquals(
                "offer for $reason",
                reason in fixableThere,
                CommandMessage.UnderstandingModelUnavailable(reason).offersProviderSetup(),
            )
        }
        assertTrue(CommandMessage.UnderstandingNeedsProvider.offersProviderSetup())
        assertFalse(CommandMessage.UnderstandingLocalOnly.offersProviderSetup())
        assertFalse(CommandMessage.UnderstandingNeedsNetwork.offersProviderSetup())
    }
}
