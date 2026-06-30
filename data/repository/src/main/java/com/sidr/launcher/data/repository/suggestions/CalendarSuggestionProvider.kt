package com.sidr.launcher.data.repository.suggestions

import android.content.Context
import android.provider.CalendarContract
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Opt-in provider: a single generic "upcoming event" nudge from [CalendarContract] (Phase 7, Block U2).
 *
 * Gated on [PermissionFeature.CALENDAR_SUGGESTIONS] — returns empty immediately when not GRANTED, and
 * never throws (any platform failure degrades to empty). Only [CalendarContract.Instances] start times
 * are read to decide *whether* an event exists in the lookahead window; the raw event title, calendar
 * id, and timestamp never leave this provider — the emitted [Suggestion] is a fixed, generic label
 * (Fork F7-9: no raw event content cached, ranked, or sent to cloud).
 */
class CalendarSuggestionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionProvider {

    override suspend fun provide(context: SuggestionContext): List<Suggestion> {
        if (permissionChecker.status(PermissionFeature.CALENDAR_SUGGESTIONS) != PermissionStatus.GRANTED) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            try {
                if (hasUpcomingEvent(context.nowEpochMs)) {
                    listOf(
                        Suggestion(
                            label = "Upcoming event",
                            actionId = CALENDAR_APP_PACKAGE,
                            source = SuggestionSource.CALENDAR,
                            score = 0.90,
                        ),
                    )
                } else {
                    emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Any platform failure (provider absent, SecurityException race, etc.) degrades to empty.
                emptyList()
            }
        }
    }

    private fun hasUpcomingEvent(nowEpochMs: Long): Boolean {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(nowEpochMs.toString())
            .appendPath((nowEpochMs + LOOKAHEAD_MS).toString())
            .build()
        this.context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            return cursor.count > 0
        }
        return false
    }

    private companion object {
        const val CALENDAR_APP_PACKAGE = "com.google.android.calendar"
        val LOOKAHEAD_MS = TimeUnit.HOURS.toMillis(2)
    }
}
