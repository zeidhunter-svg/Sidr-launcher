package com.sidr.launcher.data.repository.security

import javax.inject.Qualifier

/**
 * Marks the **dedicated** `sidr_secrets` `DataStore<Preferences>` — distinct from Block E's shared
 * `sidr_preferences` store. The secrets store holds only Keystore-ciphertext blobs; keeping it on its
 * own file means none of those entries land in the Phase-4-guarded `PreferencesKeys.ALL_KEY_NAMES`
 * key space (so `PrivacyInventoryGuardTest` stays green) and the encrypted blobs never share a file
 * with non-secret preferences.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecretsDataStore
