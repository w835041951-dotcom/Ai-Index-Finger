package com.aiindexfinger.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopWorkflowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutomationAccessibilityService.instance?.stopWorkflow()
    }
}