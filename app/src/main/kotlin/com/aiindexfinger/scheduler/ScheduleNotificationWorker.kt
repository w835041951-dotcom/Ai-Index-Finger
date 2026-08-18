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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiindexfinger.MainActivity
import com.aiindexfinger.R
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.isReadyToRun

class ScheduleNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val scheduler = WorkflowScheduler(applicationContext)
        val inputWorkflowId = inputData.getString(KEY_WORKFLOW_ID)
        val inputScheduledAtMillis = inputData.getLong(KEY_SCHEDULED_AT_MILLIS, Long.MIN_VALUE)
        val target = if (inputWorkflowId != null && inputScheduledAtMillis != Long.MIN_VALUE) {
            ScheduleWorkTarget(
                inputWorkflowId,
                inputScheduledAtMillis,
                inputData.getString(KEY_OCCURRENCE_ID),
            )
        } else {
            try {
                scheduler.resolveWorkRequest(id)
            } catch (_: ScheduleStorageException) {
                return Result.failure()
            }
        } ?: return Result.success()
        val workflowId = target.workflowId
        val scheduledAtMillis = target.scheduledAtMillis
        val occurrenceId = target.occurrenceId
        val runnableWorkflows = loadRunnableWorkflows() ?: return Result.failure()
        if (runnableWorkflows.none { it.id == workflowId }) {
            return try {
                scheduler.discardOccurrence(
                    workflowId,
                    scheduledAtMillis,
                    occurrenceId,
                )
                Result.success()
            } catch (_: ScheduleStorageWriteException) {
                Result.retry()
            } catch (_: ScheduleStorageCapacityException) {
                Result.failure()
            } catch (_: ScheduleStorageException) {
                Result.failure()
            }
        }

        val notificationManager: NotificationManager
        val notificationReadiness: ScheduleNotificationReadiness
        try {
            notificationManager = ensureScheduleNotificationChannel(applicationContext)
            notificationReadiness = scheduleNotificationReadiness(applicationContext)
        } catch (_: Exception) {
            return Result.retry()
        }
        if (notificationReadiness != ScheduleNotificationReadiness.Ready) {
            try {
                scheduler.deliverOccurrence(
                    workflowId,
                    scheduledAtMillis,
                    ScheduleWorkOrigin.RunningWorker,
                    occurrenceId,
                ) { false }
            } catch (_: ScheduleWorkOperationException) {
                return Result.retry()
            } catch (_: ScheduleStorageWriteException) {
                return Result.retry()
            } catch (_: ScheduleStorageCapacityException) {
                return Result.failure()
            } catch (_: ScheduleStorageException) {
                return Result.failure()
            }
            return Result.success()
        }

        val runnableWorkflowsBeforeNotification = loadRunnableWorkflows()
            ?: return Result.retry()
        val workflowBeforeNotification = workflowForScheduledNotification(
            workflowId,
            runnableWorkflowsBeforeNotification,
        ) ?: run {
            try {
                scheduler.discardOccurrence(
                    workflowId,
                    scheduledAtMillis,
                    occurrenceId,
                )
            } catch (_: ScheduleStorageWriteException) {
                return Result.retry()
            } catch (_: ScheduleStorageCapacityException) {
                return Result.failure()
            } catch (_: ScheduleStorageException) {
                return Result.failure()
            }
            return Result.success()
        }

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
            .setContentText(
                applicationContext.getString(
                    R.string.schedule_notification_text,
                    workflowBeforeNotification.name,
                ),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .build()
        val delivery = try {
            scheduler.deliverOccurrence(
                workflowId,
                scheduledAtMillis,
                ScheduleWorkOrigin.RunningWorker,
                occurrenceId,
            ) {
                scheduledNotificationDelivered {
                    notificationManager.notify(workflowId, SCHEDULE_NOTIFICATION_ID, notification)
                }
            }
        } catch (_: ScheduleWorkOperationException) {
            return Result.retry()
        } catch (_: ScheduleStorageWriteException) {
            return Result.retry()
        } catch (_: ScheduleStorageCapacityException) {
            return Result.failure()
        } catch (_: ScheduleStorageException) {
            return Result.failure()
        }
        if (!delivery.accepted) return Result.success()
        return Result.success()
    }

    private fun loadRunnableWorkflows(): List<Workflow>? = try {
        runnableWorkflowsForScheduling(WorkflowStore(applicationContext).loadDetailed())
    } catch (_: Exception) {
        null
    }

    companion object {
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
        const val KEY_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_WORKFLOW_ID = "scheduled_workflow_id"
        internal const val CHANNEL_ID = "workflow_schedules"
        private const val SCHEDULE_NOTIFICATION_ID = 1
    }
}

internal fun runnableWorkflowsForScheduling(result: WorkflowLoadResult): List<Workflow>? = when (result) {
    WorkflowLoadResult.Missing -> emptyList()
    is WorkflowLoadResult.Loaded -> result.workflows.filter(Workflow::isReadyToRun)
    is WorkflowLoadResult.RecoveredFromBackup -> result.workflows.filter(Workflow::isReadyToRun)
    is WorkflowLoadResult.Corrupt,
    is WorkflowLoadResult.UnsupportedVersion,
    -> null
}

internal fun workflowForScheduledNotification(
    workflowId: String,
    runnableWorkflows: List<Workflow>?,
): Workflow? = runnableWorkflows?.firstOrNull { it.id == workflowId }

internal fun shouldRetryScheduledWorker(error: Throwable): Boolean =
    error is ScheduleWorkOperationException || error is ScheduleStorageWriteException

internal fun scheduledNotificationDelivered(deliver: () -> Unit): Boolean = try {
    deliver()
    true
} catch (_: Exception) {
    false
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