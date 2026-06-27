package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.aicloud.OpenAiCompatibleGenerativeAiEngine
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import com.sidr.launcher.domain.security.SecureSecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Provides the cloud-AI Ktor [HttpClient] and the [OpenAiCompatibleGenerativeAiEngine] (Block K).
 *
 * The engine module (`:data:ai-cloud`) is Hilt-free, so the engine is a plain class constructed here
 * via `@Provides`. Both bindings are lazy singletons: nothing on the launcher cold path injects them
 * (the `@CloudEngine` engine is only wired in by Block M's router / Block N's assistant), so no AI /
 * HTTP machinery is built at startup.
 *
 * Streaming-specific: the client sets **connect/socket timeouts only — no `requestTimeoutMillis`** —
 * a whole-request timeout would abort a long but legitimate stream; the engine enforces its own
 * first-token + idle-between-chunks deadlines instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiCloudProvidesModule {

    @Provides
    @Singleton
    fun provideAiHttpClient(): HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            // socketTimeoutMillis (Ktor: max inactivity between two data packets) must stay
            // >= the engine's DEFAULT_IDLE_TIMEOUT_MS (20 s) so the engine's manual idle deadline —
            // not Ktor — tears down a silently-dead stream. 30 s > 20 s, so it's a pure backstop.
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            // requestTimeoutMillis intentionally omitted — it bounds the WHOLE call (send → full
            // response) and would abort a long but legitimate stream. See class doc.
        }
    }

    @Provides
    @Singleton
    @CloudEngine
    fun provideCloudGenerativeAiEngine(
        httpClient: HttpClient,
        secretStore: SecureSecretStore,
        configRepository: AiProviderConfigRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): GenerativeAiEngine = OpenAiCompatibleGenerativeAiEngine(
        httpClient = httpClient,
        secretStore = secretStore,
        configRepository = configRepository,
        ioDispatcher = ioDispatcher,
    )

    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val SOCKET_TIMEOUT_MS = 30_000L
}
