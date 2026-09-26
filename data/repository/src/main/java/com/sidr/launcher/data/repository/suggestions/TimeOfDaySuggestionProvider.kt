package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionActionAnchor
import com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import javax.inject.Inject

/**
 * Zero-permission provider: a thin per-[TimeOfDay] fallback (Phase 9 Y3).
 *
 * It never hardcodes AOSP package names. Coarse anchors are resolved through
 * [SuggestionActionTargetResolver] to the current device's actual launchable package; if no handler can
 * be proven launchable, the provider contributes nothing.
 */
class TimeOfDaySuggestionProvider @Inject constructor(
    private val actionTargetResolver: SuggestionActionTargetResolver,
) : SuggestionProvider {

    override suspend fun provide(context: SuggestionContext): List<Suggestion> =
        candidatesFor(context.timeOfDay)
            .mapNotNull { candidate ->
                actionTargetResolver.resolve(candidate.anchor)?.let { resolved ->
                    Suggestion(
                        label = resolved.label,
                        actionId = resolved.actionId,
                        source = SuggestionSource.TIME_OF_DAY,
                        score = candidate.score,
                    )
                }
            }
            .distinctBy { it.actionId }

    private fun candidatesFor(timeOfDay: TimeOfDay): List<AnchorCandidate> = when (timeOfDay) {
        TimeOfDay.MORNING -> listOf(
            AnchorCandidate(SuggestionActionAnchor.ALARMS, 0.30),
        )
        TimeOfDay.WORK -> emptyList()
        TimeOfDay.EVENING -> listOf(
            AnchorCandidate(SuggestionActionAnchor.CAMERA, 0.25),
        )
        TimeOfDay.NIGHT -> listOf(
            AnchorCandidate(SuggestionActionAnchor.ALARMS, 0.30),
        )
    }

    private data class AnchorCandidate(
        val anchor: SuggestionActionAnchor,
        val score: Double,
    )
}
