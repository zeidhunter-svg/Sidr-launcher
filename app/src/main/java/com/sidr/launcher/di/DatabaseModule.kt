package com.sidr.launcher.di

import android.content.Context
import androidx.room.Room
import com.sidr.launcher.BuildConfig
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.migrations.Migration1To2
import com.sidr.launcher.data.repository.db.migrations.Migration2To3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room [SidrDatabase] and its DAOs (Fork 2 / F7).
 *
 * `fallbackToDestructiveMigration` is enabled only in debug builds, so a missing migration wipes
 * and rebuilds rather than crashing during development. The history/cache tables (`app_usage`,
 * `suggestion_ranking`, `intent_match`) are recreatable; `resolution_preferences` (Stage-2 S2-1)
 * is user-learned state, so a real migration is provided (see [Migration1To2]) rather than relying
 * on the debug-only destructive path. Release builds get no destructive fallback: a missing
     * migration will crash loudly (expected — signals that a Migration object and schema bump are
     * required).
 *
 * @Binds for the three history repository interfaces lives in [HistoryBindsModule] (kept separate
 * because Hilt forbids mixing @Provides and @Binds in one module — see Block D ADR).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SidrDatabase {
        val builder = Room.databaseBuilder(
            context,
            SidrDatabase::class.java,
            SidrDatabase.DATABASE_NAME,
        ).addMigrations(Migration1To2, Migration2To3)
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }
        return builder.build()
    }

    @Provides
    fun provideAppUsageDao(db: SidrDatabase): AppUsageDao = db.appUsageDao()

    @Provides
    fun provideSuggestionRankingDao(db: SidrDatabase): SuggestionRankingDao =
        db.suggestionRankingDao()

    @Provides
    fun provideIntentMatchDao(db: SidrDatabase): IntentMatchDao = db.intentMatchDao()

    @Provides
    fun provideResolutionPreferenceDao(db: SidrDatabase): ResolutionPreferenceDao =
        db.resolutionPreferenceDao()

    @Provides
    fun provideAliasDao(db: SidrDatabase): AliasDao = db.aliasDao()
}
