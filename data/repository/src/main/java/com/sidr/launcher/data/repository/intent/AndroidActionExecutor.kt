package com.sidr.launcher.data.repository.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.SearchTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Android implementation of [ActionExecutor]. Performs only side-effecting actions —
 * [ExecutableAction.LaunchAppAction] and [ExecutableAction.OpenSearchAction] (WEB). Everything
 * else returns [ActionExecutionResult.Unsupported]; the use case never routes those here.
 *
 * Launches from the application context, so every Intent gets FLAG_ACTIVITY_NEW_TASK. No
 * sensitive permissions are required. System exceptions are caught and converted to
 * [ActionExecutionResult.Failure] with a safe, user-facing message — this never crashes.
 */
class AndroidActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ActionExecutor {

    override suspend fun execute(action: ExecutableAction): ActionExecutionResult =
        withContext(ioDispatcher) {
            when (action) {
                is ExecutableAction.LaunchAppAction -> launchApp(action)
                is ExecutableAction.OpenSearchAction -> openSearch(action)
                else -> ActionExecutionResult.Unsupported(action)
            }
        }

    private fun launchApp(action: ExecutableAction.LaunchAppAction): ActionExecutionResult {
        // activityName is intentionally unused here: the system launch intent resolves the
        // correct launcher activity. Explicit-component launch can be added in a later phase.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionExecutionResult.Failure(CANT_OPEN_APP)

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionExecutionResult.Failure(CANT_OPEN_APP)
        } catch (e: SecurityException) {
            ActionExecutionResult.Failure(CANT_OPEN_APP)
        }
    }

    private fun openSearch(action: ExecutableAction.OpenSearchAction): ActionExecutionResult {
        if (action.target != SearchTarget.WEB) {
            return ActionExecutionResult.Unsupported(action)
        }
        // TODO: configurable search provider (hardcoded Google for this slice).
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            ActionExecutionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionExecutionResult.Failure(NO_SEARCH_APP)
        } catch (e: SecurityException) {
            ActionExecutionResult.Failure(NO_SEARCH_APP)
        }
    }

    private companion object {
        const val CANT_OPEN_APP = "Couldn't open that app."
        const val NO_SEARCH_APP = "No app available to handle that search."
    }
}
