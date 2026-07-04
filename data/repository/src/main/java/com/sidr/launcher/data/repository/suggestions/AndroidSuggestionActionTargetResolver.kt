package com.sidr.launcher.data.repository.suggestions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.suggestions.ResolvedSuggestionAction
import com.sidr.launcher.domain.suggestions.SuggestionActionAnchor
import com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AndroidSuggestionActionTargetResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionActionTargetResolver {

    override suspend fun resolve(anchor: SuggestionActionAnchor): ResolvedSuggestionAction? =
        withContext(ioDispatcher) {
            when (anchor) {
                SuggestionActionAnchor.ALARMS -> resolveLaunchable(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                SuggestionActionAnchor.CAMERA -> resolveLaunchable(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                )
            }
        }

    override suspend fun isSupportedAction(actionId: String): Boolean =
        withContext(ioDispatcher) {
            actionId.isKnownRoute() || hasLaunchIntent(actionId)
        }

    private fun resolveLaunchable(intent: Intent): ResolvedSuggestionAction? {
        val pm = context.packageManager
        val info = queryIntentActivities(intent)
            .firstOrNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName
                packageName != null && hasLaunchIntent(packageName)
            }
            ?: return null
        val packageName = info.activityInfo.packageName
        return ResolvedSuggestionAction(
            label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: packageName,
            actionId = packageName,
        )
    }

    private fun hasLaunchIntent(packageName: String): Boolean =
        try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: RuntimeException) {
            false
        }

    @Suppress("DEPRECATION")
    private fun queryIntentActivities(intent: Intent): List<ResolveInfo> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun String.isKnownRoute(): Boolean =
        this == Routes.Launcher.ROUTE ||
            this == Routes.Assistant.ROUTE ||
            this == Routes.Settings.ROUTE ||
            this == Routes.PermissionEducation.ROUTE ||
            this.startsWith("${Routes.PermissionEducation.ROUTE}?")
}
