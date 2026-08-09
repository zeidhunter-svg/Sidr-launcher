package com.sidr.launcher.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Test infra mirroring `:data:repository`'s `PreferencesTestSupport` (Block E, Fork 7): a Preferences
 * DataStore on a temp file, no Android runtime, no Robolectric — pure JVM. Each call builds a
 * DataStore over [file] bound to [scope]; cancelling [scope] releases the single-instance file lock
 * so a *new* store can be opened on the same file to simulate a process restart.
 */
internal fun createTestDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
