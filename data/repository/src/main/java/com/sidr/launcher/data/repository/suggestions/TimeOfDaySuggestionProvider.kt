package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import javax.inject.Inject

/**
 * Zero-permission provider: a fixed per-[TimeOfDay] prior (Phase 7, Block U1).
 *
 * Always contributes — no platform read, no permission, nothing to deny. Pairs with
 * [UsageSuggestionProvider] to guarantee a non-empty suggestion list on a fresh device with every
 * optional permission denied (the U6 degrade contract).
 */
class TimeOfDaySuggestionProvider @Inject constructor() : SuggestionProvider {

    override suspend fun provide(context: SuggestionContext): List<Suggestion> =
        candidatesFor(context.timeOfDay)

    private fun candidatesFor(timeOfDay: TimeOfDay): List<Suggestion> = when (timeOfDay) {
        TimeOfDay.MORNING -> listOf(
            Suggestion("Clock", "com.android.deskclock", SuggestionSource.TIME_OF_DAY, 0.60),
            Suggestion("Camera", "com.android.camera", SuggestionSource.TIME_OF_DAY, 0.50),
        )
        TimeOfDay.WORK -> listOf(
            Suggestion("Messages", "com.android.messaging", SuggestionSource.TIME_OF_DAY, 0.60),
            Suggestion("Settings", "com.android.settings", SuggestionSource.TIME_OF_DAY, 0.45),
        )
        TimeOfDay.EVENING -> listOf(
            Suggestion("Maps", "com.google.android.apps.maps", SuggestionSource.TIME_OF_DAY, 0.55),
            Suggestion("Camera", "com.android.camera", SuggestionSource.TIME_OF_DAY, 0.50),
        )
        TimeOfDay.NIGHT -> listOf(
            Suggestion("Clock", "com.android.deskclock", SuggestionSource.TIME_OF_DAY, 0.60),
            Suggestion("Music", "com.android.music", SuggestionSource.TIME_OF_DAY, 0.45),
        )
    }
}
