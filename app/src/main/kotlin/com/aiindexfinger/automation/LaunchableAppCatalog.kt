package com.aiindexfinger.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LaunchableApp(
    val label: String,
    val packageName: String,
)

internal data class LaunchTargetSpec(
    val packageName: String,
    val intentAction: String?,
)

internal sealed interface LaunchIntentStrategy {
    data object PackageManagerFrontDoor : LaunchIntentStrategy
    data class PackageScopedAction(val action: String) : LaunchIntentStrategy
}

internal fun launchIntentStrategy(target: LaunchTargetSpec): LaunchIntentStrategy =
    target.intentAction?.let(LaunchIntentStrategy::PackageScopedAction)
        ?: LaunchIntentStrategy.PackageManagerFrontDoor

internal fun normalizedLaunchTarget(
    packageName: String,
    intentAction: String?,
): LaunchTargetSpec? {
    val normalizedPackage = packageName.trim().takeIf(String::isNotEmpty) ?: return null
    return LaunchTargetSpec(
        packageName = normalizedPackage,
        intentAction = intentAction?.trim()?.takeIf(String::isNotEmpty),
    )
}

internal fun launchTargetIntent(target: LaunchTargetSpec, launcherIntent: Intent?): Intent? =
    target.intentAction?.let { action ->
    Intent(action).setPackage(target.packageName)
} ?: launcherIntent

internal fun sortLaunchableApps(apps: List<LaunchableApp>): List<LaunchableApp> = apps.sortedWith(
    compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        .thenBy(LaunchableApp::label)
        .thenBy(LaunchableApp::packageName),
)

internal fun filterLaunchableApps(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return apps
    return apps.filter { app ->
        app.label.contains(normalizedQuery, ignoreCase = true) ||
            app.packageName.contains(normalizedQuery, ignoreCase = true)
    }
}

class LaunchableAppCatalog(private val context: Context) {
    fun load(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return activities
            .map { activity ->
                LaunchableApp(
                    label = activity.loadLabel(context.packageManager).toString(),
                    packageName = activity.activityInfo.packageName,
                )
            }
            .filterNot { it.packageName == context.packageName }
            .distinctBy { it.packageName }
            .let(::sortLaunchableApps)
    }
}