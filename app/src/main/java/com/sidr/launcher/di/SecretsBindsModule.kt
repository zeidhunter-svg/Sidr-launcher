package com.sidr.launcher.di

import com.sidr.launcher.data.repository.security.KeystoreSecretCipher
import com.sidr.launcher.data.repository.security.SecretCipher
import com.sidr.launcher.data.repository.security.SecureSecretStoreImpl
import com.sidr.launcher.domain.security.SecureSecretStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Block J secret-storage implementations to their interfaces: the domain
 * [SecureSecretStore] port → [SecureSecretStoreImpl], and the data-internal [SecretCipher] seam →
 * the Keystore-backed [KeystoreSecretCipher]. Concrete `sidr_secrets` DataStore provision lives in
 * [SecretsProvidesModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecretsBindsModule {

    @Binds
    @Singleton
    abstract fun bindSecureSecretStore(impl: SecureSecretStoreImpl): SecureSecretStore

    @Binds
    @Singleton
    abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher
}
