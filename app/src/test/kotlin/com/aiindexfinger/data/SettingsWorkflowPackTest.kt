package com.aiindexfinger.data

import com.aiindexfinger.model.Step
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWorkflowPackTest {
    @Test
    fun firstInstallCreatesFifteenDraftsInSettingsFolder() {
        val result = SettingsWorkflowPack.install(WorkflowLibrary(), "Settings", names)

        assertEquals(15, result.addedWorkflowCount)
        assertEquals(listOf("Settings"), result.library.folders.map(WorkflowFolder::name))
        assertEquals(15, result.library.workflows.size)
        assertTrue(result.library.workflows.all { it.state == WorkflowState.Draft })
        assertTrue(result.library.workflows.all { WorkflowValidator.validate(it).isEmpty() })
        assertTrue(result.library.workflows.all {
            result.library.folderIdFor(it.id) == SettingsWorkflowPack.FOLDER_ID
        })
    }

    @Test
    fun repeatedInstallIsIdempotentAndPreservesUserChanges() {
        val installed = SettingsWorkflowPack.install(WorkflowLibrary(), "Settings", names).library
        val customized = installed.copy(
            workflows = installed.workflows.map { workflow ->
                if (workflow.id == SettingsWorkflowPack.OPEN_HOME_WORKFLOW_ID) {
                    workflow.copy(name = "My customized Settings", steps = workflow.steps + Step.Delay("mine", 250))
                } else {
                    workflow
                }
            },
        )

        val result = SettingsWorkflowPack.install(customized, "Settings", names)

        assertEquals(0, result.addedWorkflowCount)
        assertEquals(customized, result.library)
    }

    @Test
    fun existingLocalizedSettingsFolderIsReused() {
        val existing = WorkflowLibrary(folders = listOf(WorkflowFolder("user-settings", "设置")))

        val result = SettingsWorkflowPack.install(existing, "设置", chineseNames)

        assertEquals(listOf("user-settings"), result.library.folders.map(WorkflowFolder::id))
        assertTrue(result.library.workflows.all { result.library.folderIdFor(it.id) == "user-settings" })
    }

    @Test
    fun packUsesRealSettingsPackageAndResourceSelectors() {
        val workflows = SettingsWorkflowPack.install(WorkflowLibrary(), "Settings", names).library.workflows

        assertTrue(workflows.flatMap(Workflow::steps).filterIsInstance<Step.LaunchApp>().all {
            it.packageName == "com.android.settings"
        })
        assertTrue(workflows.flatMap(Workflow::steps).filterIsInstance<Step.WaitForNode>().all {
            it.selector.viewId?.startsWith("${it.selector.packageName}:id/") == true
        })
        val searchWait = workflows
            .single { it.id == SettingsWorkflowPack.VERIFY_SEARCH_WORKFLOW_ID }
            .steps.filterIsInstance<Step.WaitForNode>().single()
        assertEquals(
            "com.android.settings:id/search_bar_title",
            searchWait.selector.viewId,
        )
        val network = workflows.single { it.id == SettingsWorkflowPack.OPEN_NETWORK_WORKFLOW_ID }
        val networkClick = network.steps.filterIsInstance<Step.Click>().single()
        assertEquals("android:id/title", networkClick.selector.viewId)
        assertEquals(1, networkClick.selector.matchIndex)
        assertEquals(
            "com.android.settings:id/collapsing_toolbar",
            network.steps.filterIsInstance<Step.WaitForNode>().last().selector.viewId,
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 9),
            workflows.filter {
                it.id.startsWith("built-in-settings-open-") &&
                    it.id != SettingsWorkflowPack.OPEN_HOME_WORKFLOW_ID &&
                    it.steps.filterIsInstance<Step.LaunchApp>().single().intentAction == null
            }
                .map { it.steps.filterIsInstance<Step.Click>().single().selector.matchIndex },
        )
        val storage = workflows.single { it.id == SettingsWorkflowPack.OPEN_STORAGE_WORKFLOW_ID }
        val storageScroll = storage.steps.filterIsInstance<Step.Scroll>().single()
        assertEquals(FailurePolicy.Continue, storageScroll.failurePolicy)
        val location = workflows.single { it.id == SettingsWorkflowPack.OPEN_LOCATION_WORKFLOW_ID }
        assertEquals(
            "android.settings.LOCATION_SOURCE_SETTINGS",
            location.steps.filterIsInstance<Step.LaunchApp>().single().intentAction,
        )
        assertTrue(location.steps.none { it is Step.Click || it is Step.Scroll })
        val accessibility = workflows.single { it.id == SettingsWorkflowPack.OPEN_ACCESSIBILITY_WORKFLOW_ID }
        assertEquals(
            "android.settings.ACCESSIBILITY_SETTINGS",
            accessibility.steps.filterIsInstance<Step.LaunchApp>().single().intentAction,
        )
        assertTrue(accessibility.steps.none { it is Step.Click || it is Step.Scroll })
        assertEquals(
            "android.settings.SYNC_SETTINGS",
            workflows.single { it.id == SettingsWorkflowPack.OPEN_ACCOUNTS_WORKFLOW_ID }
                .steps.filterIsInstance<Step.LaunchApp>().single().intentAction,
        )
        assertEquals(
            "android.settings.LOCALE_SETTINGS",
            workflows.single { it.id == SettingsWorkflowPack.OPEN_LANGUAGES_WORKFLOW_ID }
                .steps.filterIsInstance<Step.LaunchApp>().single().intentAction,
        )
    }

    private val names = listOf(
        "Open Settings home",
        "Verify Settings list",
        "Verify Settings search",
        "Open Network & internet",
        "Open Connected devices",
        "Open Apps settings",
        "Open Notifications settings",
        "Open Sound & vibration",
        "Open Modes",
        "Open Display & touch",
        "Open Storage",
        "Open Location settings",
        "Open Accessibility settings",
        "Open Accounts settings",
        "Open Languages settings",
    )
    private val chineseNames = listOf(
        "打开设置首页",
        "确认设置列表",
        "确认设置搜索入口",
        "打开网络和互联网",
        "打开已连接的设备",
        "打开应用设置",
        "打开通知设置",
        "打开声音和振动",
        "打开模式",
        "打开显示和触控",
        "打开存储空间",
        "打开位置信息设置",
        "打开无障碍设置",
        "打开账号设置",
        "打开语言设置",
    )
}