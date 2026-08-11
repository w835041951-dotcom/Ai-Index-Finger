package com.aiindexfinger.data

import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState

data class SettingsWorkflowPackInstallResult(
    val library: WorkflowLibrary,
    val addedWorkflowCount: Int,
)

data class SystemWorkflowTemplate(
    val id: String,
    val steps: List<Step>,
)

data class SystemWorkflowPack(
    val id: String,
    val folderId: String,
    val packageName: String,
    val installationState: WorkflowState,
    val templates: List<SystemWorkflowTemplate>,
) {
    val workflowIds: List<String> = templates.map(SystemWorkflowTemplate::id)

    init {
        require(workflowIds.isNotEmpty())
        require(workflowIds.distinct().size == workflowIds.size)
        require(templates.all { it.steps.isNotEmpty() })
    }

    fun install(
        library: WorkflowLibrary,
        folderName: String,
        workflowNames: List<String>,
    ): SettingsWorkflowPackInstallResult {
        require(workflowNames.size == workflowIds.size) { "$id workflow pack has invalid localized names" }
        val existingFolder = library.folders.firstOrNull { it.id == folderId }
            ?: library.folders.firstOrNull { it.name.equals(folderName, ignoreCase = true) }
        val folder = existingFolder ?: WorkflowFolder(folderId, folderName)
        val workflows = templates.zip(workflowNames) { template, name ->
            Workflow(
                id = template.id,
                name = name,
                state = installationState,
                steps = template.steps,
            )
        }
        val existingIds = library.workflows.mapTo(mutableSetOf(), Workflow::id)
        val missing = workflows.filterNot { it.id in existingIds }
        return SettingsWorkflowPackInstallResult(
            library = library.copy(
                workflows = library.workflows + missing,
                folders = if (existingFolder == null) library.folders + folder else library.folders,
                workflowFolderIds = library.workflowFolderIds + missing.associate { it.id to folder.id },
            ).normalized(),
            addedWorkflowCount = missing.size,
        )
    }
}

private fun readOnlyTemplate(
    workflowId: String,
    packageName: String,
    viewId: String,
) = SystemWorkflowTemplate(
    id = workflowId,
    steps = listOf(
        Step.LaunchApp("$workflowId-launch", packageName),
        Step.WaitForNode(
            "$workflowId-wait",
            NodeSelector(packageName = packageName, viewId = viewId),
        ),
    ),
)

private fun settingsPageTemplate(
    workflowId: String,
    titleIndex: Int,
    scrollForward: Boolean = false,
    destinationPackageName: String = "com.android.settings",
    destinationViewId: String = "com.android.settings:id/collapsing_toolbar",
) = SystemWorkflowTemplate(
    id = workflowId,
    steps = listOf(
        Step.LaunchApp("$workflowId-launch", "com.android.settings"),
        Step.WaitForNode(
            "$workflowId-home",
            NodeSelector("com.android.settings", viewId = "com.android.settings:id/settings_homepage_container"),
        ),
        *if (scrollForward) {
            arrayOf(
                Step.Scroll(
                    "$workflowId-scroll",
                    NodeSelector("com.android.settings", viewId = "com.android.settings:id/recycler_view"),
                    ScrollDirection.Forward,
                    failurePolicy = FailurePolicy.Continue,
                ),
            )
        } else {
            emptyArray()
        },
        Step.Click(
            "$workflowId-click",
            NodeSelector("com.android.settings", viewId = "android:id/title", matchIndex = titleIndex),
        ),
        Step.WaitForNode(
            "$workflowId-destination",
            NodeSelector(destinationPackageName, viewId = destinationViewId),
        ),
    ),
)

private fun directSettingsTemplate(
    workflowId: String,
    intentAction: String,
    destinationViewId: String = "com.android.settings:id/collapsing_toolbar",
) = SystemWorkflowTemplate(
    id = workflowId,
    steps = listOf(
        Step.LaunchApp(
            "$workflowId-launch",
            "com.android.settings",
            intentAction = intentAction,
        ),
        Step.WaitForNode(
            "$workflowId-destination",
            NodeSelector("com.android.settings", viewId = destinationViewId),
        ),
    ),
)

object SettingsWorkflowPack {
    const val FOLDER_ID = "built-in-pack-settings"
    const val OPEN_HOME_WORKFLOW_ID = "built-in-settings-open-home"
    const val VERIFY_LIST_WORKFLOW_ID = "built-in-settings-verify-list"
    const val VERIFY_SEARCH_WORKFLOW_ID = "built-in-settings-verify-search"
    const val OPEN_NETWORK_WORKFLOW_ID = "built-in-settings-open-network"
    const val OPEN_CONNECTED_DEVICES_WORKFLOW_ID = "built-in-settings-open-connected-devices"
    const val OPEN_APPS_WORKFLOW_ID = "built-in-settings-open-apps"
    const val OPEN_NOTIFICATIONS_WORKFLOW_ID = "built-in-settings-open-notifications"
    const val OPEN_SOUND_WORKFLOW_ID = "built-in-settings-open-sound"
    const val OPEN_MODES_WORKFLOW_ID = "built-in-settings-open-modes"
    const val OPEN_DISPLAY_WORKFLOW_ID = "built-in-settings-open-display"
    const val OPEN_STORAGE_WORKFLOW_ID = "built-in-settings-open-storage"
    const val OPEN_LOCATION_WORKFLOW_ID = "built-in-settings-open-location"
    const val OPEN_ACCESSIBILITY_WORKFLOW_ID = "built-in-settings-open-accessibility"
    const val OPEN_ACCOUNTS_WORKFLOW_ID = "built-in-settings-open-accounts"
    const val OPEN_LANGUAGES_WORKFLOW_ID = "built-in-settings-open-languages"

    private const val SETTINGS_PACKAGE = "com.android.settings"
    val definition = SystemWorkflowPack(
        id = "settings",
        folderId = FOLDER_ID,
        packageName = SETTINGS_PACKAGE,
        installationState = WorkflowState.Ready,
        templates = listOf(
            readOnlyTemplate(OPEN_HOME_WORKFLOW_ID, SETTINGS_PACKAGE, "com.android.settings:id/settings_homepage_container"),
            readOnlyTemplate(VERIFY_LIST_WORKFLOW_ID, SETTINGS_PACKAGE, "com.android.settings:id/recycler_view"),
            readOnlyTemplate(VERIFY_SEARCH_WORKFLOW_ID, SETTINGS_PACKAGE, "com.android.settings:id/search_bar_title"),
            settingsPageTemplate(OPEN_NETWORK_WORKFLOW_ID, titleIndex = 1),
            settingsPageTemplate(OPEN_CONNECTED_DEVICES_WORKFLOW_ID, titleIndex = 2),
            settingsPageTemplate(OPEN_APPS_WORKFLOW_ID, titleIndex = 3),
            settingsPageTemplate(OPEN_NOTIFICATIONS_WORKFLOW_ID, titleIndex = 4),
            settingsPageTemplate(OPEN_SOUND_WORKFLOW_ID, titleIndex = 5),
            settingsPageTemplate(OPEN_MODES_WORKFLOW_ID, titleIndex = 6),
            settingsPageTemplate(OPEN_DISPLAY_WORKFLOW_ID, titleIndex = 7),
            settingsPageTemplate(OPEN_STORAGE_WORKFLOW_ID, titleIndex = 9, scrollForward = true),
            directSettingsTemplate(
                OPEN_LOCATION_WORKFLOW_ID,
                intentAction = "android.settings.LOCATION_SOURCE_SETTINGS",
            ),
            directSettingsTemplate(
                OPEN_ACCESSIBILITY_WORKFLOW_ID,
                intentAction = "android.settings.ACCESSIBILITY_SETTINGS",
            ),
            directSettingsTemplate(
                OPEN_ACCOUNTS_WORKFLOW_ID,
                intentAction = "android.settings.SYNC_SETTINGS",
            ),
            directSettingsTemplate(
                OPEN_LANGUAGES_WORKFLOW_ID,
                intentAction = "android.settings.LOCALE_SETTINGS",
            ),
        ),
    )

    fun install(
        library: WorkflowLibrary,
        folderName: String,
        workflowNames: List<String>,
    ): SettingsWorkflowPackInstallResult {
        return definition.install(library, folderName, workflowNames)
    }

}

object ClockWorkflowPack {
    const val FOLDER_ID = "built-in-pack-clock"
    const val OPEN_WORKFLOW_ID = "built-in-clock-open"
    const val VERIFY_TIME_WORKFLOW_ID = "built-in-clock-verify-time"
    const val VERIFY_DATE_WORKFLOW_ID = "built-in-clock-verify-date"
    const val SET_VALIDATION_ALARM_TIME_WORKFLOW_ID = "built-in-clock-set-validation-alarm-time-v1"
    const val SET_VALIDATION_ALARM_SOUND_WORKFLOW_ID = "built-in-clock-set-validation-alarm-sound-v1"
    const val VALIDATION_ALARM_LABEL = "AI_INDEX_FINGER_CLOCK_VALIDATION_V1"

    private const val CLOCK_PACKAGE = "com.google.android.deskclock"
    private const val CLOCK_ID_PREFIX = "$CLOCK_PACKAGE:id/"
    private const val SHOW_ALARMS_ACTION = "android.intent.action.SHOW_ALARMS"

    private fun node(
        viewId: String? = null,
        text: String? = null,
        contentDescription: String? = null,
        contentDescriptionMatchMode: TextMatchMode = TextMatchMode.Exact,
        className: String? = null,
        ancestor: AncestorSelector? = null,
        matchIndex: Int = 0,
    ) = NodeSelector(
        packageName = CLOCK_PACKAGE,
        viewId = viewId?.let { "$CLOCK_ID_PREFIX$it" },
        text = text,
        contentDescription = contentDescription,
        contentDescriptionMatchMode = contentDescriptionMatchMode,
        className = className,
        ancestor = ancestor,
        matchIndex = matchIndex,
    )

    private val validationListLabel = node(viewId = "label", text = VALIDATION_ALARM_LABEL)
    private val validationDetailLabel = node(viewId = "alarm_label", text = VALIDATION_ALARM_LABEL)
    private val validationCardAncestor = AncestorSelector(
        viewId = "${CLOCK_ID_PREFIX}alarm_card",
        contentDescription = VALIDATION_ALARM_LABEL,
        contentDescriptionMatchMode = TextMatchMode.Contains,
    )
    private val validationAlarmSwitch = node(
        viewId = "onoff",
        ancestor = validationCardAncestor,
    )
    private val validationAlarmCard = node(
        viewId = "alarm_card",
        contentDescription = VALIDATION_ALARM_LABEL,
        contentDescriptionMatchMode = TextMatchMode.Contains,
    )

    private fun failureStep(id: String) = Step.WaitForNode(
        id = id,
        selector = node(viewId = "ai_index_finger_clock_validation_failure"),
        timeoutMillis = 500,
    )

    private fun alarmOffSteps(prefix: String, suffix: String): List<Step> {
        val variableName = "${prefix}_${suffix}_alarm_status"
        return listOf(
            Step.ReadNodeText(
                "$prefix-read-status-$suffix",
                node(viewId = "schedule_alarm_detail_1"),
                variableName,
                NodeAttribute.Text,
            ),
            Step.IfElse(
                "$prefix-check-status-en-$suffix",
                Condition.Equals(Value.Variable(variableName), Value.Literal("Alarm is off")),
                whenTrue = emptyList(),
                whenFalse = listOf(
                    Step.IfElse(
                        "$prefix-check-status-zh-$suffix",
                        Condition.Equals(Value.Variable(variableName), Value.Literal("闹钟已关闭")),
                        whenTrue = emptyList(),
                        whenFalse = listOf(failureStep("$prefix-status-failure-$suffix")),
                    ),
                ),
            ),
        )
    }

    private fun restoreDisabledSteps(prefix: String): List<Step> {
        val variableName = "${prefix}_saved_card_state"
        val disableStep = Step.Click("$prefix-restore-disabled", validationAlarmSwitch)
        return listOf(
            Step.ReadNodeText(
                "$prefix-read-saved-card-state",
                validationAlarmCard,
                variableName,
                NodeAttribute.ContentDescription,
            ),
            Step.IfElse(
                "$prefix-disable-if-enabled-en",
                Condition.Equals(
                    Value.Variable(variableName),
                    Value.Literal("currently enabled"),
                    ComparisonOperator.Contains,
                ),
                whenTrue = listOf(disableStep),
                whenFalse = listOf(
                    Step.IfElse(
                        "$prefix-disable-if-enabled-zh",
                        Condition.Equals(
                            Value.Variable(variableName),
                            Value.Literal("目前已启用"),
                            ComparisonOperator.Contains,
                        ),
                        whenTrue = listOf(disableStep.copy(id = "$prefix-restore-disabled-zh")),
                    ),
                ),
            ),
            Step.Delay("$prefix-wait-disabled", 1_000),
        )
    }

    private fun preparedAlarmPrefix(prefix: String): List<Step> = listOf(
        Step.LaunchApp("$prefix-launch", CLOCK_PACKAGE, SHOW_ALARMS_ACTION),
        Step.WaitForNode("$prefix-wait-label", validationListLabel),
        Step.WaitForNode(
            "$prefix-require-unique-label",
            validationListLabel.copy(matchIndex = 1),
            mustExist = false,
            timeoutMillis = 15_000,
        ),
        Step.Click("$prefix-open", validationListLabel),
        Step.WaitForNode("$prefix-wait-detail", validationDetailLabel),
    ) + alarmOffSteps(prefix, "before")

    private fun setValidationAlarmTimeTemplate(): SystemWorkflowTemplate {
        val prefix = SET_VALIDATION_ALARM_TIME_WORKFLOW_ID
        val hourInput = node(
            className = "android.widget.EditText",
            ancestor = AncestorSelector(viewId = "${CLOCK_ID_PREFIX}material_hour_text_input"),
        )
        val minuteInput = node(
            className = "android.widget.EditText",
            ancestor = AncestorSelector(viewId = "${CLOCK_ID_PREFIX}material_minute_text_input"),
        )
        val minuteFocusTarget = node(
            className = "android.view.View",
            ancestor = AncestorSelector(viewId = "${CLOCK_ID_PREFIX}material_minute_text_input"),
        )
        val configuredTimeVariable = "${prefix}_configured_time"
        return SystemWorkflowTemplate(
            id = prefix,
            steps = preparedAlarmPrefix(prefix) + listOf(
                Step.Click("$prefix-edit-time", node(viewId = "clock_edit_button")),
                Step.WaitForNode("$prefix-wait-picker", node(viewId = "material_timepicker_mode_button")),
                Step.IfElse(
                    "$prefix-open-text-mode",
                    Condition.NodeExists(hourInput),
                    whenTrue = emptyList(),
                    whenFalse = listOf(
                        Step.Click("$prefix-text-mode", node(viewId = "material_timepicker_mode_button")),
                        Step.WaitForNode("$prefix-wait-hour", hourInput),
                    ),
                ),
                Step.InputText(
                    "$prefix-hour",
                    hourInput,
                    "10",
                    inputMethod = TextInputMethod.SetText,
                ),
                Step.IfElse(
                    "$prefix-focus-minute",
                    Condition.NodeExists(minuteInput),
                    whenTrue = emptyList(),
                    whenFalse = listOf(
                        Step.Click("$prefix-click-minute", minuteFocusTarget),
                        Step.WaitForNode("$prefix-wait-minute", minuteInput),
                    ),
                ),
                Step.InputText(
                    "$prefix-minute",
                    minuteInput,
                    "37",
                    inputMethod = TextInputMethod.SetText,
                ),
                Step.IfElse(
                    "$prefix-select-am-if-present",
                    Condition.NodeExists(node(viewId = "material_clock_period_am_button")),
                    whenTrue = listOf(
                        Step.Click("$prefix-am", node(viewId = "material_clock_period_am_button")),
                    ),
                ),
                Step.Click("$prefix-confirm-time", node(viewId = "material_timepicker_ok_button")),
                Step.WaitForNode("$prefix-wait-time", node(viewId = "clock")),
                Step.ReadNodeText(
                    "$prefix-read-time",
                    node(viewId = "clock"),
                    configuredTimeVariable,
                    NodeAttribute.Text,
                ),
                Step.IfElse(
                    "$prefix-check-time",
                    Condition.Equals(
                        Value.Variable(configuredTimeVariable),
                        Value.Literal("10:37"),
                        ComparisonOperator.Contains,
                    ),
                    whenTrue = emptyList(),
                    whenFalse = listOf(failureStep("$prefix-time-failure")),
                ),
                Step.Click("$prefix-save", node(viewId = "save_button")),
                Step.WaitForNode("$prefix-wait-saved-label", validationListLabel),
                Step.WaitForNode(
                    "$prefix-verify-saved-time",
                    node(
                        viewId = "digital_clock",
                        contentDescription = "10:37",
                        contentDescriptionMatchMode = TextMatchMode.Contains,
                        ancestor = validationCardAncestor,
                    ),
                ),
            ) + restoreDisabledSteps(prefix) + listOf(
                Step.Click("$prefix-reopen", validationListLabel),
                Step.WaitForNode("$prefix-wait-after", validationDetailLabel),
            ) + alarmOffSteps(prefix, "after") + listOf(
                Step.GlobalAction("$prefix-close-detail", SystemAction.Back),
                Step.WaitForNode("$prefix-finished", validationListLabel),
            ),
        )
    }

    private fun silentValueSteps(prefix: String, suffix: String): List<Step> {
        val variableName = "${prefix}_${suffix}_ringtone"
        return listOf(
            Step.ReadNodeText(
                "$prefix-read-ringtone-$suffix",
                node(viewId = "alarm_ringtone"),
                variableName,
                NodeAttribute.Text,
            ),
            Step.IfElse(
                "$prefix-check-silent-en-$suffix",
                Condition.Equals(
                    Value.Variable(variableName),
                    Value.Literal("Silent"),
                    ComparisonOperator.Contains,
                ),
                whenTrue = emptyList(),
                whenFalse = listOf(
                    Step.IfElse(
                        "$prefix-check-silent-zh-$suffix",
                        Condition.Equals(
                            Value.Variable(variableName),
                            Value.Literal("静音"),
                            ComparisonOperator.Contains,
                        ),
                        whenTrue = emptyList(),
                        whenFalse = listOf(failureStep("$prefix-ringtone-failure-$suffix")),
                    ),
                ),
            ),
        )
    }

    private fun setValidationAlarmSoundTemplate(): SystemWorkflowTemplate {
        val prefix = SET_VALIDATION_ALARM_SOUND_WORKFLOW_ID
        val silentEnglish = node(viewId = "ringtone_primary_text", text = "Silent")
        val silentChinese = node(viewId = "ringtone_primary_text", text = "静音")
        return SystemWorkflowTemplate(
            id = prefix,
            steps = preparedAlarmPrefix(prefix) + listOf(
                Step.Click("$prefix-open-sound", node(viewId = "ringtone_setting")),
                Step.WaitForNode("$prefix-wait-sounds", node(viewId = "ringtone_content")),
                Step.WaitForNode(
                    "$prefix-wait-ringtone-rows",
                    node(viewId = "ringtone_primary_text"),
                ),
                Step.IfElse(
                    "$prefix-select-silent-en",
                    Condition.NodeExists(silentEnglish),
                    whenTrue = listOf(Step.Click("$prefix-click-silent-en", silentEnglish)),
                    whenFalse = listOf(
                        Step.IfElse(
                            "$prefix-select-silent-zh",
                            Condition.NodeExists(silentChinese),
                            whenTrue = listOf(Step.Click("$prefix-click-silent-zh", silentChinese)),
                            whenFalse = listOf(failureStep("$prefix-select-silent-failure")),
                        ),
                    ),
                ),
                Step.GlobalAction("$prefix-back-to-alarm", SystemAction.Back),
                Step.WaitForNode("$prefix-wait-selected", validationDetailLabel),
            ) + silentValueSteps(prefix, "selected") + listOf(
                Step.Click("$prefix-save", node(viewId = "save_button")),
                Step.WaitForNode("$prefix-wait-saved-label", validationListLabel),
            ) + restoreDisabledSteps(prefix) + listOf(
                Step.Click("$prefix-reopen", validationListLabel),
                Step.WaitForNode("$prefix-wait-persisted", validationDetailLabel),
            ) + alarmOffSteps(prefix, "after") + silentValueSteps(prefix, "persisted") + listOf(
                Step.GlobalAction("$prefix-close-detail", SystemAction.Back),
                Step.WaitForNode("$prefix-finished", validationListLabel),
            ),
        )
    }

    val definition = SystemWorkflowPack(
        id = "clock",
        folderId = FOLDER_ID,
        packageName = CLOCK_PACKAGE,
        installationState = WorkflowState.Ready,
        templates = listOf(
            readOnlyTemplate(OPEN_WORKFLOW_ID, CLOCK_PACKAGE, "com.google.android.deskclock:id/action_bar_title"),
            readOnlyTemplate(VERIFY_TIME_WORKFLOW_ID, CLOCK_PACKAGE, "com.google.android.deskclock:id/digital_clock"),
            readOnlyTemplate(VERIFY_DATE_WORKFLOW_ID, CLOCK_PACKAGE, "com.google.android.deskclock:id/date_and_next_alarm"),
            setValidationAlarmTimeTemplate(),
            setValidationAlarmSoundTemplate(),
        ),
    )
}

object FilesWorkflowPack {
    const val FOLDER_ID = "built-in-pack-files"
    const val OPEN_WORKFLOW_ID = "built-in-files-open"
    const val VERIFY_HEADER_WORKFLOW_ID = "built-in-files-verify-header"
    const val VERIFY_LIST_WORKFLOW_ID = "built-in-files-verify-list"

    private const val FILES_PACKAGE = "com.google.android.documentsui"

    val definition = SystemWorkflowPack(
        id = "files",
        folderId = FOLDER_ID,
        packageName = FILES_PACKAGE,
        installationState = WorkflowState.Ready,
        templates = listOf(
            readOnlyTemplate(OPEN_WORKFLOW_ID, FILES_PACKAGE, "com.google.android.documentsui:id/drawer_layout"),
            readOnlyTemplate(VERIFY_HEADER_WORKFLOW_ID, FILES_PACKAGE, "com.google.android.documentsui:id/header_container"),
            readOnlyTemplate(VERIFY_LIST_WORKFLOW_ID, FILES_PACKAGE, "com.google.android.documentsui:id/dir_list"),
        ),
    )
}

object AiIndexFingerSelfTestPack {
    const val FOLDER_ID = "built-in-pack-ai-index-finger"
    const val VERIFY_HOME_WORKFLOW_ID = "built-in-ai-index-finger-verify-home"
    const val VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID =
        "built-in-ai-index-finger-verify-observation-runtime"
    const val HOME_MARKER_TEXT = "AI Index Finger"

    val definition = SystemWorkflowPack(
        id = "ai-index-finger",
        folderId = FOLDER_ID,
        packageName = "com.aiindexfinger",
        installationState = WorkflowState.Ready,
        templates = listOf(
            SystemWorkflowTemplate(
                id = VERIFY_HOME_WORKFLOW_ID,
                steps = listOf(
                    Step.WaitForNode(
                        "$VERIFY_HOME_WORKFLOW_ID-wait",
                        NodeSelector("com.aiindexfinger", text = HOME_MARKER_TEXT),
                    ),
                ),
            ),
            SystemWorkflowTemplate(
                id = VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID,
                steps = listOf(
                    Step.WaitForNode(
                        "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-wait",
                        NodeSelector("com.aiindexfinger", text = HOME_MARKER_TEXT),
                    ),
                    Step.ReadNodeText(
                        "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-read",
                        NodeSelector("com.aiindexfinger", text = HOME_MARKER_TEXT),
                        "observed_brand",
                        NodeAttribute.Text,
                    ),
                    Step.IfElse(
                        id = "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-if",
                        condition = Condition.Equals(
                            Value.Variable("observed_brand"),
                            Value.Literal(HOME_MARKER_TEXT),
                        ),
                        whenTrue = listOf(
                            Step.Repeat(
                                "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-repeat",
                                times = 2,
                                steps = listOf(
                                    Step.WaitForNode(
                                        "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-repeat-wait",
                                        NodeSelector("com.aiindexfinger", text = HOME_MARKER_TEXT),
                                    ),
                                ),
                            ),
                        ),
                        whenFalse = listOf(
                            Step.WaitForNode(
                                "$VERIFY_OBSERVATION_RUNTIME_WORKFLOW_ID-failure",
                                NodeSelector(
                                    "com.aiindexfinger",
                                    text = "__ai_index_finger_self_test_failure__",
                                ),
                                timeoutMillis = 500,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}