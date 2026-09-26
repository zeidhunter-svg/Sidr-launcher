package com.sidr.launcher.di

import com.sidr.launcher.data.repository.agent.RoomAgentSessionStore
import com.sidr.launcher.domain.agent.AgentSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the agentic-track A0 [AgentSessionStore] port to its Room-backed implementation (Task 10).
 *
 * Kept separate from [DatabaseModule] because Hilt forbids mixing `@Provides` and `@Binds` in one
 * module — the same reason [HistoryBindsModule] and [MemoryBindsModule] exist.
 *
 * Tasks 11 and 12 have since landed, so the store **is** on the runtime path: `LauncherViewModel`
 * injects it and `LauncherAgentSession.restoreOnStart` reads it off the main thread at startup.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentBindsModule {

    @Binds
    @Singleton
    abstract fun bindAgentSessionStore(impl: RoomAgentSessionStore): AgentSessionStore
}
