package com.aiindexfinger

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiindexfinger.automation.PreflightRecoveryAction
import com.aiindexfinger.automation.buildWorkflowPreflightReport
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
import com.aiindexfinger.scheduler.ScheduleNotificationWorker
import com.aiindexfinger.scheduler.scheduleNotificationSettingsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleNotificationRecoveryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun blockedScheduleOffersNotificationSettingsWithoutScheduling() {
        var settingsRequests = 0
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                ScheduleNotificationRecoveryDialog(
                    onOpenSettings = { settingsRequests += 1 },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag(SCHEDULE_NOTIFICATION_RECOVERY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.schedule_notifications_blocked))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCHEDULE_NOTIFICATION_SETTINGS_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(1, settingsRequests)
            assertTrue(!dismissed)
        }
    }

    @Test
    fun deniedRunOffersWorkflowControlNotificationSettings() {
        var settingsRequests = 0
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                RunNotificationRecoveryDialog(
                    onOpenSettings = { settingsRequests += 1 },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.run_notifications_blocked_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.run_notifications_blocked))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.open_notification_settings))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, settingsRequests)
            assertTrue(!dismissed)
        }
    }

    @Test
    fun preflightShowsChannelBlockAndNotificationRecoveryAction() {
        val workflow = Workflow(
            id = "notification-preflight",
            name = "Notification preflight",
            steps = listOf(Step.Delay("delay", 100)),
            state = WorkflowState.Ready,
        )
        val report = buildWorkflowPreflightReport(
            workflow = workflow,
            accessibilityConnected = true,
            notificationStatus = ScheduleNotificationReadiness.ChannelDisabled,
            isLaunchable = { _, _ -> true },
            countMatches = { 0 },
        )
        var recoveryAction: PreflightRecoveryAction? = null
        composeRule.setContent {
            MaterialTheme {
                PreflightReportDialog(
                    workflow = workflow,
                    report = report,
                    onDismiss = {},
                    onEditStep = {},
                    onRecoveryAction = { recoveryAction = it },
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(
                R.string.workflow_test_notifications,
                context.getString(R.string.notification_status_channel_disabled),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.open_notification_settings))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(PreflightRecoveryAction.OpenNotificationSettings, recoveryAction)
        }
    }

    @Test
    fun settingsIntentTargetsBlockedChannelOrApp() {
        val channelIntent = scheduleNotificationSettingsIntent(
            context,
            ScheduleNotificationReadiness.ChannelDisabled,
        )
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channelIntent.action)
        assertEquals(context.packageName, channelIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(
            ScheduleNotificationWorker.CHANNEL_ID,
            channelIntent.getStringExtra(Settings.EXTRA_CHANNEL_ID),
        )

        val appIntent = scheduleNotificationSettingsIntent(
            context,
            ScheduleNotificationReadiness.AppNotificationsDisabled,
        )
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, appIntent.action)
        assertEquals(context.packageName, appIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}