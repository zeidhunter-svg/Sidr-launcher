package com.sidr.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InstalledAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InstalledAppsRepository {

    override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> =
        withContext(ioDispatcher) {
            try {
                val pm = context.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

                @Suppress("DEPRECATION")
                val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                    )
                } else {
                    pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                }

                val apps = resolveInfoList.map { info ->
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        activityName = info.activityInfo.name,
                    )
                }.sortedBy { it.label.lowercase() }

                OperationResult.Success(apps)
            } catch (e: SecurityException) {
                OperationResult.Failure(
                    OperationError.PermissionDenied(permission = "QUERY_ALL_PACKAGES"),
                )
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.UnknownError())
            }
        }
}
