package com.sidr.launcher.di

import com.sidr.launcher.data.repository.db.IntentMatchHistoryRepositoryImpl
import com.sidr.launcher.data.repository.db.SuggestionRankingRepositoryImpl
import com.sidr.launcher.data.repository.db.UsageHistoryRepositoryImpl
import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.history.SuggestionRankingRepository
import com.sidr.launcher.domain.history.UsageHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds Room-backed history repository implementations to their domain interfaces.
 * Mirrors [PersistenceBindsModule]. DAOs and the database are provided by [DatabaseModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryBindsModule {

    @Binds
    @Singleton
    abstract fun bindUsageHistoryRepository(
        impl: UsageHistoryRepositoryImpl,
    ): UsageHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSuggestionRankingRepository(
        impl: SuggestionRankingRepositoryImpl,
    ): SuggestionRankingRepository

    @Binds
    @Singleton
    abstract fun bindIntentMatchHistoryRepository(
        impl: IntentMatchHistoryRepositoryImpl,
    ): IntentMatchHistoryRepository
}
