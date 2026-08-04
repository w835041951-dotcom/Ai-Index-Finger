package com.aiindexfinger.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopWorkflowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = AutomationAccessibilityService.instance ?: return
        if (intent.getBooleanExtra(EXTRA_STOP_RECORDING, false)) {
            service.stopRecording()
        } else if (intent.getBooleanExtra(EXTRA_ADVANCE_WORKFLOW, false)) {
            service.advanceWorkflow()
        } else if (intent.hasExtra(EXTRA_RECORDING_COMMAND)) {
            service.performRecordingCommand(intent.getStringExtra(EXTRA_RECORDING_COMMAND).orEmpty())
        } else {
            service.stopWorkflow()
        }
    }

    companion object {
        const val EXTRA_STOP_RECORDING = "stop_recording"
        const val EXTRA_ADVANCE_WORKFLOW = "advance_workflow"
        const val EXTRA_RECORDING_COMMAND = "recording_command"
        const val COMMAND_SHOW_CONTROLS = "show_controls"
        const val COMMAND_RECORD_BACK = "record_back"
        const val COMMAND_RECORD_HOME = "record_home"
    }
}