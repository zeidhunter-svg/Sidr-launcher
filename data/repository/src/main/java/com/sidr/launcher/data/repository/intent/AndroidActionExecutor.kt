package com.sidr.launcher.data.repository.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Android implementation of [ActionExecutor]. Performs only side-effecting actions —
 * [ExecutableAction.LaunchAppAction], [ExecutableAction.OpenSearchAction] (WEB),
 * [ExecutableAction.OpenUrlAction] and [ExecutableAction.PlayStoreSearchAction] (AIL-2). Everything
 * else returns [ActionExecutionResult.Unsupported]; the use case never routes those here.
 *
 * Launches from the application context, so every Intent gets FLAG_ACTIVITY_NEW_TASK. No
 * sensitive permissions are required. System exceptions are caught and converted to
 * [ActionExecutionResult.Failure] with a safe, user-facing message — this never crashes.
 *
 * The web-search provider is configurable (AIL-2 / R5): the search URL is built from
 * [com.sidr.launcher.domain.preferences.UserPreferences.webProviderTemplate] (default Google),
 * retiring the former hardcoded-Google TODO.
 */
class AndroidActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ActionExecutor {

    override suspend fun execute(action: ExecutableAction): ActionExecutionResult =
        withContext(ioDispatcher) {
            when (action) {
                is ExecutableAction.LaunchAppAction -> launchApp(action)
                is ExecutableAction.OpenSearchAction -> openSearch(action)
                is ExecutableAction.OpenUrlAction -> openUrl(action)
                is ExecutableAction.PlayStoreSearchAction -> openPlayStore(action)
                else -> ActionExecutionResult.Unsupported(action)
            }
        }

    private fun launchApp(action: ExecutableAction.LaunchAppAction): ActionExecutionResult {
        // activityName is intentionally unused here: the system launch intent resolves the
        // correct launcher activity. Explicit-component launch can be added in a later phase.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionExecutionResult.Failure(CommandFailure.CantOpenApp)

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
        } catch (e: SecurityException) {
            ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
        }
    }

    private suspend fun openSearch(action: ExecutableAction.OpenSearchAction): ActionExecutionResult {
        if (action.target != SearchTarget.WEB) {
            return ActionExecutionResult.Unsupported(action)
        }
        // AIL-2 / R5 — configurable provider (default Google). Substitute the URL-encoded query into
        // the `{q}` placeholder; if a user-set template omits it, append the encoded query.
        val template = userPreferencesRepository.getPreferences().first().webProviderTemplate
        val encoded = Uri.encode(action.query)
        val urlString =
            if (template.contains(QUERY_PLACEHOLDER)) template.replace(QUERY_PLACEHOLDER, encoded)
            else template + encoded
        return viewUrl(urlString, CommandFailure.NoSearchApp)
    }

    private fun openUrl(action: ExecutableAction.OpenUrlAction): ActionExecutionResult {
        // The URL is already scheme-checked + normalized upstream (UrlDetector); ACTION_VIEW on an
        // http(s) URI hands off to a browser. Never fired for non-http(s) schemes.
        return viewUrl(action.url, CommandFailure.CantOpenUrl)
    }

    private fun openPlayStore(action: ExecutableAction.PlayStoreSearchAction): ActionExecutionResult {
        val encoded = Uri.encode(action.query)
        // Prefer the Play Store app (market://); fall back to the web store when it isn't installed.
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(marketIntent)
            ActionExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            viewUrl("https://play.google.com/store/search?q=$encoded&c=apps", CommandFailure.NoStoreApp)
        } catch (e: SecurityException) {
            ActionExecutionResult.Failure(CommandFailure.NoStoreApp)
        }
    }

    private fun viewUrl(url: String, failure: CommandFailure): ActionExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ActionExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionExecutionResult.Failure(failure)
        } catch (e: SecurityException) {
            ActionExecutionResult.Failure(failure)
        }
    }

    private companion object {
        const val QUERY_PLACEHOLDER = "{q}"
    }
}
