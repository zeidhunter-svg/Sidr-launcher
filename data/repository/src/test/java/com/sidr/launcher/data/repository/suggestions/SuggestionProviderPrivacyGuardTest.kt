package com.sidr.launcher.data.repository.suggestions

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakePermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.TimeOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Block U5 privacy guard for the opt-in providers — proves, against a planted sensitive value, that a
 * raw event title / raw coordinate can never become part of an emitted [com.sidr.launcher.domain.suggestions.Suggestion].
 *
 * This does not just read the source and assert intent: each test plants a sensitive value INSIDE the
 * real platform data path each provider reads from (a fake content provider behind
 * [CalendarContract.AUTHORITY]; a real [Location] fix via Robolectric's [LocationManager] shadow), runs
 * the actual production provider against it, and asserts the planted value appears nowhere in the
 * result — not in `label`, not in `actionId`, not in `toString()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SuggestionProviderPrivacyGuardTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val suggestionContext = SuggestionContext(timeOfDay = TimeOfDay.WORK, nowEpochMs = 1_700_000_000_000L)

    @Test
    fun `calendar provider never leaks the raw event title even when it is present in the cursor`() = runTest {
        val sensitiveTitle = "Therapy appointment with Dr. Aslanova"
        val providerController = Robolectric.buildContentProvider(FakeCursorContentProvider::class.java)
            .create(CalendarContract.AUTHORITY)
        val fakeProvider = providerController.get() as FakeCursorContentProvider
        // The real CalendarSuggestionProvider only ever requests the EVENT_ID column, but the fake
        // backing data deliberately ALSO carries the sensitive title — proving the leak can't happen
        // even if a future change widened the projection by accident, the row itself stays opaque here.
        fakeProvider.nextCursor = MatrixCursor(arrayOf(CalendarContract.Instances.EVENT_ID, "title")).apply {
            addRow(arrayOf<Any>(42L, sensitiveTitle))
        }

        val permissionChecker = FakePermissionChecker().apply {
            setStatus(PermissionFeature.CALENDAR_SUGGESTIONS, PermissionStatus.GRANTED)
        }
        val sut = CalendarSuggestionProvider(context, permissionChecker, dispatcher)

        val result = sut.provide(suggestionContext)

        assertEquals(1, result.size)
        val suggestion = result.single()
        assertEquals("Upcoming event", suggestion.label)
        assertEquals("com.google.android.calendar", suggestion.actionId)
        assertFalse(suggestion.toString().contains(sensitiveTitle))
        assertFalse(suggestion.label.contains("Therapy"))
    }

    @Test
    fun `calendar provider returns nothing when not granted, even with a planted sensitive cursor`() = runTest {
        val providerController = Robolectric.buildContentProvider(FakeCursorContentProvider::class.java)
            .create(CalendarContract.AUTHORITY)
        val fakeProvider = providerController.get() as FakeCursorContentProvider
        fakeProvider.nextCursor = MatrixCursor(arrayOf(CalendarContract.Instances.EVENT_ID, "title")).apply {
            addRow(arrayOf<Any>(7L, "Secret event"))
        }

        val permissionChecker = FakePermissionChecker() // defaultStatus = DENIED
        val sut = CalendarSuggestionProvider(context, permissionChecker, dispatcher)

        assertTrue(sut.provide(suggestionContext).isEmpty())
    }

    @Test
    fun `location provider never leaks the raw coordinates even when a real fix is present`() = runTest {
        val sensitiveLatitude = 37.774900
        val sensitiveLongitude = -122.419400
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = sensitiveLatitude
            longitude = sensitiveLongitude
        }
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix)

        val permissionChecker = FakePermissionChecker().apply {
            setStatus(PermissionFeature.LOCATION_SUGGESTIONS, PermissionStatus.GRANTED)
        }
        val sut = LocationSuggestionProvider(context, permissionChecker, dispatcher)

        val result = sut.provide(suggestionContext)

        assertEquals(1, result.size)
        val suggestion = result.single()
        assertEquals("Nearby places", suggestion.label)
        assertEquals("com.google.android.apps.maps", suggestion.actionId)
        val rendered = suggestion.toString()
        assertFalse(rendered.contains(sensitiveLatitude.toString()))
        assertFalse(rendered.contains(sensitiveLongitude.toString()))
        assertFalse(rendered.contains("37.77"))
        assertFalse(rendered.contains("-122.41"))
    }

    @Test
    fun `location provider returns nothing when not granted, even with a real fix present`() = runTest {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 51.5074
            longitude = -0.1278
        }
        shadowOf(locationManager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix)

        val permissionChecker = FakePermissionChecker() // defaultStatus = DENIED
        val sut = LocationSuggestionProvider(context, permissionChecker, dispatcher)

        assertTrue(sut.provide(suggestionContext).isEmpty())
    }

    /** Minimal test-only [ContentProvider] that serves whatever cursor [nextCursor] is set to. */
    class FakeCursorContentProvider : ContentProvider() {
        var nextCursor: Cursor? = null

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = nextCursor

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
