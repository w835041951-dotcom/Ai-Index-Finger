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
        val workflowName = inputData.getString(KEY_WORKFLOW_NAME)
            ?: applicationContext.getString(R.string.scheduled_workflow_fallback_name)

        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ScheduleStore(applicationContext).markMissed(workflowId)
            return Result.success()
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.schedule_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
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
        ScheduleStore(applicationContext).remove(workflowId)
        return Result.success()
    }

    companion object {
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_WORKFLOW_ID = "scheduled_workflow_id"
        private const val CHANNEL_ID = "workflow_schedules"
        private const val SCHEDULE_NOTIFICATION_ID = 1
    }
}

internal fun scheduleIntentData(workflowId: String): String =
    "aiindexfinger://schedule/" + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(workflowId.toByteArray(StandardCharsets.UTF_8))

internal fun missedSchedules(
    schedules: List<WorkflowSchedule>,
): List<WorkflowSchedule> = schedules.filter { it.status == ScheduleStatus.Missed }