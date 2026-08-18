package com.aiindexfinger.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal enum class NotificationCommand {
    StopWorkflow,
    AdvanceWorkflow,
    StopRecording,
    ShowRecordingControls,
    RecordBack,
    RecordHome,
}

internal data class NotificationCommandIdentity(
    val action: String,
    val requestCode: Int,
)

internal fun notificationCommandIdentity(command: NotificationCommand): NotificationCommandIdentity =
    when (command) {
        NotificationCommand.StopWorkflow -> NotificationCommandIdentity(
            "com.aiindexfinger.action.STOP_WORKFLOW",
            2_101,
        )
        NotificationCommand.AdvanceWorkflow -> NotificationCommandIdentity(
            "com.aiindexfinger.action.ADVANCE_WORKFLOW",
            2_102,
        )
        NotificationCommand.StopRecording -> NotificationCommandIdentity(
            "com.aiindexfinger.action.STOP_RECORDING",
            2_103,
        )
        NotificationCommand.ShowRecordingControls -> NotificationCommandIdentity(
            "com.aiindexfinger.action.SHOW_RECORDING_CONTROLS",
            2_104,
        )
        NotificationCommand.RecordBack -> NotificationCommandIdentity(
            "com.aiindexfinger.action.RECORD_BACK",
            2_105,
        )
        NotificationCommand.RecordHome -> NotificationCommandIdentity(
            "com.aiindexfinger.action.RECORD_HOME",
            2_106,
        )
    }

class StopWorkflowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = AutomationAccessibilityService.instance ?: return
        when (intent.action) {
            notificationCommandIdentity(NotificationCommand.StopWorkflow).action -> service.stopWorkflow()
            notificationCommandIdentity(NotificationCommand.AdvanceWorkflow).action -> service.advanceWorkflow()
            notificationCommandIdentity(NotificationCommand.StopRecording).action -> service.stopRecording()
            notificationCommandIdentity(NotificationCommand.ShowRecordingControls).action ->
                service.performRecordingCommand(COMMAND_SHOW_CONTROLS)
            notificationCommandIdentity(NotificationCommand.RecordBack).action ->
                service.performRecordingCommand(COMMAND_RECORD_BACK)
            notificationCommandIdentity(NotificationCommand.RecordHome).action ->
                service.performRecordingCommand(COMMAND_RECORD_HOME)
            else -> when {
                intent.getBooleanExtra(EXTRA_STOP_RECORDING, false) -> service.stopRecording()
                intent.getBooleanExtra(EXTRA_ADVANCE_WORKFLOW, false) -> service.advanceWorkflow()
                intent.hasExtra(EXTRA_RECORDING_COMMAND) ->
                    service.performRecordingCommand(intent.getStringExtra(EXTRA_RECORDING_COMMAND).orEmpty())
                else -> service.stopWorkflow()
            }
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