package com.aiindexfinger.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.util.Base64
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aiindexfinger.MainActivity
import com.aiindexfinger.R

class ScheduleNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
        val scheduledAtMillis = inputData.getLong(KEY_SCHEDULED_AT_MILLIS, Long.MIN_VALUE)
        if (scheduledAtMillis == Long.MIN_VALUE) return Result.failure()
        val workflowName = inputData.getString(KEY_WORKFLOW_NAME)
            ?: applicationContext.getString(R.string.scheduled_workflow_fallback_name)

        val notificationManager = ensureScheduleNotificationChannel(applicationContext)
        if (scheduleNotificationReadiness(applicationContext) != ScheduleNotificationReadiness.Ready) {
            try {
                WorkflowScheduler(applicationContext).missOccurrence(workflowId, scheduledAtMillis)
            } catch (_: ScheduleStorageException) {
                return Result.failure()
            }
            return Result.success()
        }

        val completion = try {
            WorkflowScheduler(applicationContext).completeOccurrence(workflowId, scheduledAtMillis)
        } catch (_: ScheduleStorageException) {
            return Result.failure()
        }
        if (!completion.accepted) return Result.success()

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(scheduleIntentData(workflowId))
            putExtra(EXTRA_WORKFLOW_ID, workflowId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(applicationContext.getString(R.string.schedule_notification_title))
            .setContentText(applicationContext.getString(R.string.schedule_notification_text, workflowName))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(workflowId, SCHEDULE_NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_WORKFLOW_NAME = "workflow_name"
        const val KEY_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
        const val EXTRA_WORKFLOW_ID = "scheduled_workflow_id"
        internal const val CHANNEL_ID = "workflow_schedules"
        private const val SCHEDULE_NOTIFICATION_ID = 1
    }
}

internal fun ensureScheduleNotificationChannel(context: Context): NotificationManager =
    context.getSystemService(NotificationManager::class.java).also { notificationManager ->
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ScheduleNotificationWorker.CHANNEL_ID,
                context.getString(R.string.schedule_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

internal fun scheduleNotificationReadiness(context: Context): ScheduleNotificationReadiness {
    val notificationManager = ensureScheduleNotificationChannel(context)
    val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val channelImportance = notificationManager
        .getNotificationChannel(ScheduleNotificationWorker.CHANNEL_ID)
        ?.importance
        ?: NotificationManager.IMPORTANCE_NONE
    return scheduleNotificationReadiness(
        runtimePermissionGranted,
        notificationManager.areNotificationsEnabled(),
        channelImportance,
    )
}

internal fun scheduleNotificationSettingsIntent(
    context: Context,
    readiness: ScheduleNotificationReadiness,
): Intent = if (readiness == ScheduleNotificationReadiness.ChannelDisabled) {
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, ScheduleNotificationWorker.CHANNEL_ID)
    }
} else {
    appNotificationSettingsIntent(context)
}

internal fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

internal fun openScheduleNotificationSettings(
    context: Context,
    readiness: ScheduleNotificationReadiness,
): Boolean {
    val applicationDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    val intents = listOf(
        scheduleNotificationSettingsIntent(context, readiness),
        appNotificationSettingsIntent(context),
        applicationDetails,
    ).distinctBy { intent ->
        listOf(intent.action, intent.dataString, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }
    return intents.any { intent ->
        if (intent.resolveActivity(context.packageManager) == null) return@any false
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
}

internal fun scheduleIntentData(workflowId: String): String =
    "aiindexfinger://schedule/" + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(workflowId.toByteArray(StandardCharsets.UTF_8))

internal fun canPostScheduleNotification(
    runtimePermissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channelImportance: Int,
): Boolean = scheduleNotificationReadiness(
    runtimePermissionGranted,
    appNotificationsEnabled,
    channelImportance,
) == ScheduleNotificationReadiness.Ready

enum class ScheduleNotificationReadiness {
    Ready,
    RuntimePermissionRequired,
    AppNotificationsDisabled,
    ChannelDisabled,
}

internal enum class ScheduleNotificationAction {
    Schedule,
    RequestPermission,
    OpenSettings,
}

internal fun scheduleNotificationAction(
    readiness: ScheduleNotificationReadiness,
): ScheduleNotificationAction = when (readiness) {
    ScheduleNotificationReadiness.Ready -> ScheduleNotificationAction.Schedule
    ScheduleNotificationReadiness.RuntimePermissionRequired ->
        ScheduleNotificationAction.RequestPermission
    ScheduleNotificationReadiness.AppNotificationsDisabled,
    ScheduleNotificationReadiness.ChannelDisabled -> ScheduleNotificationAction.OpenSettings
}

internal fun scheduleNotificationReadiness(
    runtimePermissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channelImportance: Int,
): ScheduleNotificationReadiness = when {
    !runtimePermissionGranted -> ScheduleNotificationReadiness.RuntimePermissionRequired
    !appNotificationsEnabled -> ScheduleNotificationReadiness.AppNotificationsDisabled
    channelImportance == NotificationManager.IMPORTANCE_NONE ->
        ScheduleNotificationReadiness.ChannelDisabled
    else -> ScheduleNotificationReadiness.Ready
}

internal fun missedSchedules(
    schedules: List<WorkflowSchedule>,
): List<WorkflowSchedule> = schedules.filter { schedule ->
    schedule.status == ScheduleStatus.Missed || schedule.missedOccurrencePending
}