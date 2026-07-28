package com.aiindexfinger.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aiindexfinger.MainActivity

class ScheduleNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
        val workflowName = inputData.getString(KEY_WORKFLOW_NAME) ?: "Scheduled workflow"
        ScheduleStore(applicationContext).remove(workflowId)

        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workflow schedules", NotificationManager.IMPORTANCE_HIGH),
        )
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORKFLOW_ID, workflowId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            workflowId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Workflow ready")
            .setContentText("Open AI Index Finger to run $workflowName")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(workflowId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_WORKFLOW_NAME = "workflow_name"
        const val EXTRA_WORKFLOW_ID = "scheduled_workflow_id"
        private const val CHANNEL_ID = "workflow_schedules"
    }
}