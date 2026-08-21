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
 * module — the same reason [HistoryBindsModule] and [MemoryBindsModule] exist. Nothing on the runtime
 * path injects the store yet: Task 11 makes the cut into the command pipeline and Task 12 the surface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentBindsModule {

    @Binds
    @Singleton
    abstract fun bindAgentSessionStore(impl: RoomAgentSessionStore): AgentSessionStore
}
