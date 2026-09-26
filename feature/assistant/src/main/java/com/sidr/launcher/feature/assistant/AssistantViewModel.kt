package com.sidr.launcher.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.ai.GenerateReplyUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

/**
 * ViewModel for the assistant streaming screen (Block N).
 *
 * Deps are all domain interfaces — no `:data` / `:core:android` edge from this feature.
 *
 * **No SavedStateHandle (deliberate).** Unlike [LauncherViewModel] (H3), the assistant does NOT
 * persist prompt / reply across process death — [lastPrompt] is transient by design, and the API
 * key must never touch SavedStateHandle. This is an intentional deviation from the H3 precedent,
 * not an oversight; see decisions.md "ADR Block N".
 *
 * **VM-collected streaming (Fork P5-5 resolution).** The stream is collected in [viewModelScope]
 * so: (a) rotation does NOT restart the request — the VM and its StateFlow outlive config-change;
 * (b) screen-leave aborts the request — `onCleared` cancels `viewModelScope` → the cold Ktor flow
 * tears down. [retry] cancels the in-flight job before relaunching (latest-wins, H2 pattern).
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val generateReply: GenerateReplyUseCase,
    private val providerConfig: AiProviderConfigRepository,
    private val secretStore: SecureSecretStore,
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var lastPrompt: String? = null

    init {
        // Observe active provider config → reflect base URL + model + keySet boolean in form state.
        // keySet is recomputed on every emit so switching providers never shows a stale "key set ✓"
        // from a previous provider's Keystore slot. Only the boolean reaches state — never the key.
        viewModelScope.launch {
            providerConfig.activeConfig().collect { config ->
                val keySet = if (config != null) {
                    val result = secretStore.get(SecretKeys.apiKey(config.providerId))
                    result is OperationResult.Success && !result.value.isNullOrBlank()
                } else {
                    false
                }
                _uiState.update { current ->
                    current.copy(
                        form = current.form.copy(
                            baseUrl = config?.baseUrl ?: "",
                            modelId = config?.modelId?.value ?: "",
                            keySet = keySet,
                            saveError = null,
                        ),
                    )
                }
            }
        }
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    /** Navigate to the dedicated AI-provider setup surface (the chat itself never hosts the form). */
    fun openProviderSettings() {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(Routes.AssistantProvider.ROUTE))
    }

    fun send(prompt: String) {
        streamJob?.cancel()
        lastPrompt = prompt
        _uiState.update { it.copy(reply = "", status = AssistantStatus.Streaming) }
        streamJob = viewModelScope.launch {
            try {
                generateReply.generate(prompt).collect { chunk ->
                    when (chunk) {
                        is AiChunk.Text -> _uiState.update { it.copy(reply = it.reply + chunk.delta) }
                        is AiChunk.Completed -> _uiState.update {
                            it.copy(status = AssistantStatus.Done(refused = chunk.stopReason == AiStopReason.REFUSAL))
                        }
                        is AiChunk.Failed -> _uiState.update {
                            it.copy(
                                status = AssistantStatus.Error(
                                    error = chunk.error.toAssistantError(),
                                    retryable = chunk.error.isButtonRetryable(),
                                    showProviderCta = chunk.error.needsProviderSetup(),
                                ),
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /** Latest-wins retry — cancels the in-flight stream job via [send] before relaunching. */
    fun retry() {
        lastPrompt?.let { send(it) }
    }

    /**
     * Saves provider config + optionally updates the API key in Keystore.
     *
     * `providerId` is derived from the base-URL host (lowercase, port and path stripped) so
     * switching endpoints gives isolated Keystore slots. Two providers sharing a host collapse to
     * one slot — acceptable for Phase 5 single-active-config (see decisions.md "ADR Block N").
     *
     * A blank [apiKey] skips the Keystore write (existing key is preserved). [apiKey] is never
     * logged, placed in state, or returned after this call.
     *
     * **Write order: key, then config.** The config write is the observable event, so it must come
     * last — see the inline note below.
     */
    fun saveProvider(baseUrl: String, model: String, apiKey: String) {
        val trimmedUrl = baseUrl.trim()
        if (!trimmedUrl.startsWith("https://")) {
            _uiState.update {
                it.copy(form = it.form.copy(saveError = ProviderSaveError.BASE_URL_NOT_HTTPS))
            }
            return
        }
        val host = try {
            URI(trimmedUrl).host?.lowercase() ?: trimmedUrl.lowercase()
        } catch (_: Exception) {
            trimmedUrl.removePrefix("https://").substringBefore("/").lowercase()
        }
        val providerId = AiProviderId(host)
        viewModelScope.launch {
            // Key BEFORE config (2026-08-10). The config write is what makes `activeConfig()` emit, and
            // every observer — including the *other* screen's ViewModel instance — recomputes `keySet`
            // from the secret store on that emit. Writing the config first left a window where an
            // observer read the store before the key landed and then cached "no key set" until the next
            // config change (found on-device during DS-10). It also means a provider is never announced
            // as configured while its key is still missing.
            if (apiKey.isNotBlank()) {
                val keyResult = secretStore.put(SecretKeys.apiKey(providerId), apiKey.trim())
                if (keyResult is OperationResult.Failure) {
                    _uiState.update {
                        it.copy(form = it.form.copy(saveError = ProviderSaveError.KEY_SAVE_FAILED))
                    }
                    return@launch
                }
            }
            val config = AiProviderConfig(
                providerId = providerId,
                baseUrl = trimmedUrl,
                modelId = AiModelId(model.trim()),
                displayName = host,
            )
            val configResult = providerConfig.setActiveConfig(config)
            if (configResult is OperationResult.Failure) {
                _uiState.update {
                    it.copy(form = it.form.copy(saveError = ProviderSaveError.CONFIG_SAVE_FAILED))
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    form = it.form.copy(
                        keySet = it.form.keySet || apiKey.isNotBlank(),
                        saveError = null,
                    ),
                )
            }
        }
    }
}

// ── AiError → AssistantError mapping (feature-local) ─────────────────────────────────────────────
// Lives in :feature:assistant, NOT in core:common. Per ADR Block B, the shared UI error type is in
// core:common with no domain dep by design; putting the mapper there would add a forbidden
// core/common → domain edge. AiError is a domain type, so the mapping happens here in the VM layer.
// I18N-1 Task 10 changed only the *type* this produces (was `UiError`, whose `Message` variant
// carried an English sentence): every case still lands on exactly the copy it did before, now chosen
// from resources by `AssistantPresentation`. `Offline` and `Network` share a case because both
// already produced `UiError.Network` — the same single rendered sentence.
// Per Block-I KDoc retryability table:
//   Retryable:     Offline / Network / Timeout / RateLimited / ServerError / Unknown
//   Not-retryable: MissingCredentials / Unauthorized / InvalidRequest

internal fun AiError.toAssistantError(): AssistantError = when (this) {
    is AiError.Offline -> AssistantError.Network
    is AiError.Network -> AssistantError.Network
    is AiError.MissingCredentials -> AssistantError.MissingCredentials
    is AiError.Unauthorized -> AssistantError.Unauthorized
    is AiError.RateLimited -> AssistantError.RateLimited
    is AiError.Timeout -> AssistantError.Timeout
    is AiError.ServerError -> AssistantError.ServerError(statusCode)
    is AiError.InvalidRequest -> AssistantError.InvalidRequest(detail)
    is AiError.Unknown -> AssistantError.Unknown
}

internal fun AiError.isButtonRetryable(): Boolean = when (this) {
    is AiError.Offline,
    is AiError.Network,
    is AiError.Timeout,
    is AiError.RateLimited,
    is AiError.ServerError,
    is AiError.Unknown -> true

    is AiError.MissingCredentials,
    is AiError.Unauthorized,
    is AiError.InvalidRequest -> false
}

/** True for errors where the fix is updating provider settings, not just retrying. */
internal fun AiError.needsProviderSetup(): Boolean =
    this is AiError.MissingCredentials || this is AiError.Unauthorized
