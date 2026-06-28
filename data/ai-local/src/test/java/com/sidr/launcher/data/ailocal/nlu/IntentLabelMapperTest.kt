package com.sidr.launcher.data.ailocal.nlu

import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.intent.SimpleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2a: pure logits → softmax → argmax → 7-class label-map → IntentMatchResult, the confidence
 * escape, and the heuristic slot fill — all without an OrtEnvironment.
 *
 * Label order (must match NluLabel ordinals / the trained model):
 * 0 LAUNCH_APP, 1 SEARCH, 2 OPEN_SETTINGS, 3 SHOW_APPS, 4 HELP, 5 OPEN_ASSISTANT, 6 UNKNOWN.
 */
class IntentLabelMapperTest {

    private val mapper = IntentLabelMapper(OnnxModelSpec(confidenceFloor = 0.60f))

    /** Strongly favours index [idx] so softmax max-prob clears the 0.60 floor. */
    private fun logitsFor(idx: Int): FloatArray = FloatArray(7) { if (it == idx) 8f else 0f }

    @Test
    fun launch_app_maps_with_heuristic_slot_and_nlu_source() {
        val r = mapper.map("fire up telegram", logitsFor(0))
        val intent = r.best.intent as LauncherIntent.LaunchAppIntent
        assertEquals("telegram", intent.displayNameQuery)
        assertEquals(MatcherSource.NLU, r.source)
        assertTrue(r.best.confidence >= 0.60f)
    }

    @Test
    fun search_maps_with_stripped_query() {
        val r = mapper.map("google pizza near me", logitsFor(1))
        val intent = r.best.intent as LauncherIntent.SearchIntent
        assertEquals("pizza near me", intent.query)
        assertEquals(MatcherSource.NLU, r.source)
    }

    @Test
    fun open_settings_search_show_help_assistant_map_to_expected_intents() {
        assertTrue(mapper.map("device prefs", logitsFor(2)).best.intent is LauncherIntent.OpenSettingsIntent)
        assertEquals(
            SimpleCommand.SHOW_APPS,
            (mapper.map("all apps", logitsFor(3)).best.intent as LauncherIntent.SimpleCommandIntent).command,
        )
        assertEquals(
            SimpleCommand.HELP,
            (mapper.map("what can you do", logitsFor(4)).best.intent as LauncherIntent.SimpleCommandIntent).command,
        )
        assertEquals(
            SimpleCommand.OPEN_ASSISTANT,
            (mapper.map("talk to ai", logitsFor(5)).best.intent as LauncherIntent.SimpleCommandIntent).command,
        )
    }

    @Test
    fun argmax_unknown_escapes_to_lowest_confidence() {
        val r = mapper.map("zxqv", logitsFor(6)) // index 6 == UNKNOWN
        assertTrue(r.best.intent is LauncherIntent.UnknownIntent)
        assertEquals(0f, r.best.confidence, 0f)
        assertEquals(MatcherSource.NLU, r.source)
        assertEquals("argmax_unknown", r.debugReason)
    }

    @Test
    fun below_confidence_floor_escapes_even_when_argmax_is_a_real_class() {
        // Near-uniform logits → max softmax well below 0.60 though argmax is LAUNCH_APP.
        val logits = FloatArray(7) { 0f }.also { it[0] = 0.2f }
        val r = mapper.map("ambiguous thing", logits)
        assertTrue(r.best.intent is LauncherIntent.UnknownIntent)
        assertEquals(0f, r.best.confidence, 0f)
        assertEquals("below_confidence_floor", r.debugReason)
    }

    @Test
    fun logit_size_mismatch_escapes_rather_than_crashing() {
        val r = mapper.map("x", FloatArray(3))
        assertTrue(r.best.intent is LauncherIntent.UnknownIntent)
        assertEquals("logit_size_mismatch", r.debugReason)
    }

    @Test
    fun bare_verb_launch_keeps_whole_input_as_slot() {
        val r = mapper.map("open", logitsFor(0))
        val intent = r.best.intent as LauncherIntent.LaunchAppIntent
        assertEquals("open", intent.displayNameQuery)
    }
}
