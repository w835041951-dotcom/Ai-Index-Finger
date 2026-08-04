package com.aiindexfinger

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.automation.LaunchableAppCatalog
import com.aiindexfinger.automation.filterLaunchableApps
import com.aiindexfinger.automation.ObservedNode
import com.aiindexfinger.automation.SelectorRecommendations
import com.aiindexfinger.automation.ScreenCaptureState
import com.aiindexfinger.automation.ScreenPoint
import com.aiindexfinger.automation.mapFitCenterTapToScreen
import com.aiindexfinger.automation.recommendedSelector
import com.aiindexfinger.automation.selectCaptureNode
import com.aiindexfinger.automation.NotificationPreflightStatus
import com.aiindexfinger.automation.PendingOverlayAction
import com.aiindexfinger.automation.PreflightRecoveryAction
import com.aiindexfinger.automation.WorkflowPreflightReport
import com.aiindexfinger.automation.buildWorkflowPreflightReport
import com.aiindexfinger.automation.recoveryActions
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStepBranch
import com.aiindexfinger.data.RunStepLocation
import com.aiindexfinger.data.uniqueRunLocationTo
import com.aiindexfinger.data.runLocationsTo
import com.aiindexfinger.data.RunHistoryLoadResult
import com.aiindexfinger.data.InvalidWorkflowException
import com.aiindexfinger.data.RunStepOutcome
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.filterRunRecords
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowFolder
import com.aiindexfinger.data.WorkflowFolderSelection
import com.aiindexfinger.data.filterWorkflows
import com.aiindexfinger.data.SettingsWorkflowPack
import com.aiindexfinger.data.ClockWorkflowPack
import com.aiindexfinger.data.FilesWorkflowPack
import com.aiindexfinger.data.AiIndexFingerSelfTestPack
import com.aiindexfinger.data.sortedFolders
import com.aiindexfinger.data.WorkflowTransfer
import com.aiindexfinger.data.WorkflowVersion
import com.aiindexfinger.data.mergeImportedLibrary
import com.aiindexfinger.data.resolveRunHistoryDestination
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.StepComparisonBranch
import com.aiindexfinger.model.StepComparisonField
import com.aiindexfinger.model.StepComparisonPath
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.StepBranch
import com.aiindexfinger.model.StepListPath
import com.aiindexfinger.model.StepPath
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.ValidationIssue
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowDifference
import com.aiindexfinger.model.WorkflowExample
import com.aiindexfinger.model.WorkflowExampleCapability
import com.aiindexfinger.model.WorkflowExampleCategory
import com.aiindexfinger.model.SearchableWorkflowExample
import com.aiindexfinger.model.WorkflowLimits
import com.aiindexfinger.model.WorkflowMetadataField
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.WorkflowStarterTemplates
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.ValidationIssueCode
import com.aiindexfinger.model.duplicateStep
import com.aiindexfinger.model.compareWorkflows
import com.aiindexfinger.model.insertStep
import com.aiindexfinger.model.matchesSearch
import com.aiindexfinger.model.moveStep
import com.aiindexfinger.model.removeStep
import com.aiindexfinger.model.replaceStep
import com.aiindexfinger.model.stepAt
import com.aiindexfinger.model.stepsAt
import com.aiindexfinger.model.withExecutionSettings
import com.aiindexfinger.model.uniquePathTo
import com.aiindexfinger.model.effectiveState
import com.aiindexfinger.model.filterWorkflowExamples
import com.aiindexfinger.model.isReadyToRun
import com.aiindexfinger.model.readinessIssues
import com.aiindexfinger.scheduler.ScheduleNotificationWorker
import com.aiindexfinger.scheduler.ScheduleRecurrence
import com.aiindexfinger.scheduler.ScheduleStorageException
import com.aiindexfinger.scheduler.ScheduledWorkflowEvent
import com.aiindexfinger.scheduler.ScheduledWorkflowEventController
import com.aiindexfinger.scheduler.WorkflowSchedule
import com.aiindexfinger.scheduler.WorkflowScheduler
import com.aiindexfinger.scheduler.localScheduleEpochMillis
import com.aiindexfinger.scheduler.missedSchedules
import com.aiindexfinger.scheduler.scheduleDelayMillis
import com.aiindexfinger.scheduler.removeTriggeredSchedule
import com.aiindexfinger.executor.ExecutionError
import com.aiindexfinger.executor.ExecutionErrorCode
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val workflowStore by lazy { WorkflowStore(this) }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val workflowScheduler by lazy { WorkflowScheduler(this) }
    private val releasePreferences by lazy {
        getSharedPreferences(RELEASE_PREFERENCES_NAME, MODE_PRIVATE)
    }
    private val scheduledWorkflowEvents = ScheduledWorkflowEventController()
    private val persistenceDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)
    private val mutablePersistenceError = MutableStateFlow<String?>(null)
    private val persistenceError = mutablePersistenceError.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduledWorkflowEvents.publish(intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID))
        setContent {
            AiIndexFingerTheme {
                val initialState by produceState<InitialAppState?>(null) {
                    value = withContext(Dispatchers.IO) { loadInitialState() }
                }
                val state = initialState
                if (state == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.loading_workflows))
                    }
                } else {
                    val scheduledWorkflowEvent by scheduledWorkflowEvents.event.collectAsStateWithLifecycle()
                    val persistenceFailure by persistenceError.collectAsStateWithLifecycle()
                    WorkflowApp(
                        initialLibrary = state.library,
                        initialRunRecords = state.runRecords,
                        initialRunHistoryCorrupt = state.runHistoryCorrupt,
                        initialSchedules = state.schedules,
                        initialRunMessage = state.loadMessageRes?.let { stringResource(it) },
                        persistenceFailure = persistenceFailure,
                        scheduledWorkflowEvent = scheduledWorkflowEvent,
                        onScheduledWorkflowEventConsumed = scheduledWorkflowEvents::consume,
                        onSave = { library ->
                            persistenceScope.launch {
                                runCatching { workflowStore.saveLibrary(library) }
                                    .onFailure { mutablePersistenceError.value = getString(R.string.save_failed) }
                            }
                        },
                        onListVersions = { workflowId ->
                            withContext(persistenceDispatcher) { workflowStore.listVersions(workflowId) }
                        },
                        onRollback = { workflowId, versionId ->
                            withContext(persistenceDispatcher) { workflowStore.rollback(workflowId, versionId) }
                        },
                        onClearRunHistory = {
                            persistenceScope.launch {
                                runCatching { runHistoryStore.clear() }
                                    .onFailure { mutablePersistenceError.value = getString(R.string.clear_history_failed) }
                            }
                        },
                        onSchedule = workflowScheduler::schedule,
                        onCancelSchedule = workflowScheduler::cancel,
                        onReloadSchedules = workflowScheduler::load,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        accessibilityDisclosureAcknowledged = releasePreferences.getBoolean(
                            ACCESSIBILITY_DISCLOSURE_ACKNOWLEDGED,
                            false,
                        ),
                        onAccessibilityDisclosureAcknowledged = {
                            releasePreferences.edit()
                                .putBoolean(ACCESSIBILITY_DISCLOSURE_ACKNOWLEDGED, true)
                                .apply()
                        },
                    )
                }
            }
        }
    }

    private fun loadInitialState(): InitialAppState {
        val workflowResult = workflowStore.loadDetailed()
        val runHistoryResult = runHistoryStore.loadDetailed()
        val library = workflowResult.library
        val scheduleResult = runCatching {
            workflowScheduler.load(
                library.workflows.filter { it.isReadyToRun() }.map { it.id }.toSet(),
            )
        }
        scheduleResult.exceptionOrNull()?.let { error ->
            if (error !is ScheduleStorageException) throw error
        }
        val loadedSchedules = scheduleResult.getOrDefault(emptyList())
        val missed = missedSchedules(loadedSchedules)
        var schedules = loadedSchedules
        missed.forEach { schedule ->
            schedules = workflowScheduler.consumeMissedOccurrence(schedule.workflowId)
        }
        val hasMissedSchedule = missed.isNotEmpty()
        return InitialAppState(
            library = library,
            runRecords = runHistoryResult.records,
            runHistoryCorrupt = runHistoryResult is RunHistoryLoadResult.Corrupt,
            schedules = schedules,
            loadMessageRes = when (workflowResult) {
                is WorkflowLoadResult.RecoveredFromBackup -> R.string.workflows_recovered_from_backup
                is WorkflowLoadResult.Corrupt -> R.string.workflows_corrupt
                is WorkflowLoadResult.UnsupportedVersion -> R.string.workflows_unsupported_version
                else -> if (scheduleResult.exceptionOrNull() is ScheduleStorageException) {
                    R.string.schedule_storage_corrupt
                } else if (runHistoryResult is RunHistoryLoadResult.Corrupt) {
                    R.string.run_history_storage_corrupt
                } else if (hasMissedSchedule) {
                    R.string.schedule_notification_missed
                } else {
                    null
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduledWorkflowEvents.publish(intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private companion object {
        const val RELEASE_PREFERENCES_NAME = "release_readiness"
        const val ACCESSIBILITY_DISCLOSURE_ACKNOWLEDGED = "accessibility_disclosure_acknowledged"
    }
}

private data class InitialAppState(
    val library: WorkflowLibrary,
    val runRecords: List<RunRecord>,
    val runHistoryCorrupt: Boolean,
    val schedules: List<WorkflowSchedule>,
    val loadMessageRes: Int?,
)

@Composable
private fun WorkflowApp(
    initialLibrary: WorkflowLibrary,
    initialRunRecords: List<RunRecord>,
    initialRunHistoryCorrupt: Boolean,
    initialSchedules: List<WorkflowSchedule>,
    initialRunMessage: String?,
    persistenceFailure: String?,
    scheduledWorkflowEvent: ScheduledWorkflowEvent?,
    onScheduledWorkflowEventConsumed: (Long) -> Unit,
    onSave: (WorkflowLibrary) -> Unit,
        onListVersions: suspend (String) -> List<WorkflowVersion>,
        onRollback: suspend (String, String) -> Workflow,
    onClearRunHistory: () -> Unit,
    onSchedule: (Workflow, Long, ScheduleRecurrence) -> List<WorkflowSchedule>,
    onCancelSchedule: (String) -> List<WorkflowSchedule>,
    onReloadSchedules: (Set<String>) -> List<WorkflowSchedule>,
    onOpenAccessibilitySettings: () -> Unit,
    accessibilityDisclosureAcknowledged: Boolean,
    onAccessibilityDisclosureAcknowledged: () -> Unit,
) {
    var library by remember { mutableStateOf(initialLibrary) }
    val workflows = library.workflows
    val persist: (WorkflowLibrary) -> Unit = { updated ->
        val normalized = updated.normalized()
        onSave(normalized)
        library = normalized
    }
    var runRecords by remember { mutableStateOf(initialRunRecords) }
    var runHistoryCorrupt by remember { mutableStateOf(initialRunHistoryCorrupt) }
    var schedules by remember { mutableStateOf(initialSchedules) }
    var editingWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var initialEditingStepPath by remember { mutableStateOf<StepPath?>(null) }
    var showRunHistory by remember { mutableStateOf(false) }
    var workflowComparison by remember { mutableStateOf<Pair<Workflow, Workflow>?>(null) }
        var versionHistory by remember { mutableStateOf<Pair<Workflow, List<WorkflowVersion>>?>(null) }
    var runMessage by remember { mutableStateOf(initialRunMessage) }
    LaunchedEffect(persistenceFailure) {
        if (persistenceFailure != null) runMessage = persistenceFailure
    }
    var preflightReport by remember { mutableStateOf<Pair<Workflow, WorkflowPreflightReport>?>(null) }
    val runningWorkflowId by AutomationAccessibilityService.runningWorkflowId.collectAsStateWithLifecycle()
    val latestRun by AutomationAccessibilityService.latestRun.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Workflow?>(null) }
    var pendingBundleExport by remember { mutableStateOf<WorkflowLibrary?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val workflowTransfer = remember { WorkflowTransfer(context.contentResolver) }
    DisposableEffect(editingWorkflow != null) {
        val observationLease = if (editingWorkflow != null) {
            AutomationAccessibilityService.acquireObservationLease()
        } else {
            null
        }
        onDispose { observationLease?.close() }
    }
    val accessibilityDisclosureGate = remember(accessibilityDisclosureAcknowledged) {
        AccessibilityDisclosureGate(accessibilityDisclosureAcknowledged)
    }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val requestAccessibilitySetup = {
        when (accessibilityDisclosureGate.requestSetup()) {
            AccessibilityDisclosureAction.ShowDisclosure -> showAccessibilityDisclosure = true
            AccessibilityDisclosureAction.OpenSettings -> onOpenAccessibilitySettings()
            AccessibilityDisclosureAction.StayInApp -> Unit
        }
    }
    LaunchedEffect(scheduledWorkflowEvent?.sequence) {
        scheduledWorkflowEvent?.let { event ->
            val id = event.workflowId
            runCatching {
                onReloadSchedules(workflows.filter { it.isReadyToRun() }.map { it.id }.toSet())
            }.onSuccess {
                schedules = it
                val workflowName = workflows.firstOrNull { it.id == id }?.name
                    ?: context.getString(R.string.scheduled_workflow_fallback_name)
                runMessage = context.getString(R.string.workflow_ready_to_run, workflowName)
            }.onFailure { error ->
                if (error !is ScheduleStorageException) throw error
                runMessage = context.getString(R.string.schedule_storage_corrupt)
            }
            onScheduledWorkflowEventConsumed(event.sequence)
        }
    }
    LaunchedEffect(latestRun?.record?.id) {
        latestRun?.let { outcome ->
            runRecords = (listOf(outcome.record) + runRecords)
                .distinctBy { it.id }
                .take(100)
            runMessage = outcome.result.localizedMessage(context, outcome.record.failedStepLocation)
        }
    }
    var pendingSchedule by remember { mutableStateOf<Triple<Workflow, Long, ScheduleRecurrence>?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingSchedule
        pendingSchedule = null
        if (granted && request != null && request.first.isReadyToRun()) {
            runCatching { onSchedule(request.first, request.second, request.third) }
                .onSuccess {
                    schedules = it
                    runMessage = context.getString(R.string.workflow_scheduled, request.first.name)
                }
                .onFailure { error ->
                    runMessage = context.getString(
                        if (error is ScheduleStorageException) {
                            R.string.schedule_storage_corrupt
                        } else {
                            R.string.schedule_failed
                        },
                    )
                }
        } else if (granted && request != null) {
            runMessage = context.getString(
                R.string.cannot_schedule,
                request.first.readinessIssues().first().localizedMessage(context),
            )
        } else if (!granted) {
            runMessage = context.getString(R.string.schedule_requires_notifications)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val workflow = pendingExport
        pendingExport = null
        if (uri != null && workflow != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.write(uri, workflow) }
                }
                outcome
                    .onSuccess { runMessage = context.getString(R.string.workflow_exported, workflow.name) }
                    .onFailure { runMessage = context.getString(R.string.export_failed, it.message.orEmpty()) }
            }
        }
    }
    val bundleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val librarySnapshot = pendingBundleExport
        pendingBundleExport = null
        if (uri != null && librarySnapshot != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.writeLibrary(uri, librarySnapshot) }
                }
                outcome
                    .onSuccess {
                        runMessage = context.getString(
                            R.string.workflows_backed_up,
                            librarySnapshot.workflows.size,
                        )
                    }
                    .onFailure { runMessage = context.getString(R.string.backup_failed, it.message.orEmpty()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.readLibrary(uri) }
                }
                outcome.onSuccess { importedLibrary ->
                    val updated = mergeImportedLibrary(library, importedLibrary, ::newId)
                    persist(updated)
                    runMessage = context.getString(
                        R.string.workflows_imported,
                        updated.workflows.size - workflows.size,
                    )
                }
                        .onFailure { error ->
                            val details = (error as? InvalidWorkflowException)
                                ?.issue
                                ?.localizedMessage(context)
                                ?: error.message.orEmpty()
                            runMessage = context.getString(R.string.import_failed, details)
                        }
                    }
        }
    }

    if (workflowComparison != null) {
        val (before, after) = requireNotNull(workflowComparison)
        WorkflowComparisonScreen(
            before = before,
            after = after,
            onBack = { workflowComparison = null },
        )
    } else if (editingWorkflow != null) {
        WorkflowEditor(
            workflow = requireNotNull(editingWorkflow),
            initialEditingStepPath = initialEditingStepPath,
            onTest = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = if (Build.VERSION.SDK_INT < 33) {
                    NotificationPreflightStatus.NotRequired
                } else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationPreflightStatus.Granted
                } else {
                    NotificationPreflightStatus.Denied
                }
                preflightReport = workflow to buildWorkflowPreflightReport(
                    workflow = workflow,
                    accessibilityConnected = service != null,
                    notificationStatus = notificationStatus,
                    isLaunchable = { packageName, intentAction ->
                        intentAction?.let { Intent(it).setPackage(packageName) }
                            ?.resolveActivity(context.packageManager) != null ||
                            intentAction == null &&
                            context.packageManager.getLaunchIntentForPackage(packageName) != null
                    },
                    countMatches = { selector -> service?.countMatches(selector) ?: 0 },
                    imageCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                )
            },
            onBack = {
                editingWorkflow = null
                initialEditingStepPath = null
            },
            onSave = { workflow ->
                if (!workflow.isReadyToRun()) {
                    runCatching { onCancelSchedule(workflow.id) }
                        .onSuccess {
                            schedules = it
                            val updated = workflows.filterNot { current -> current.id == workflow.id } + workflow
                            persist(library.copy(workflows = updated))
                            editingWorkflow = null
                            initialEditingStepPath = null
                        }
                        .onFailure { error ->
                            if (error !is ScheduleStorageException) throw error
                            runMessage = context.getString(R.string.schedule_storage_corrupt)
                        }
                } else {
                    val updated = workflows.filterNot { it.id == workflow.id } + workflow
                    persist(library.copy(workflows = updated))
                    editingWorkflow = null
                    initialEditingStepPath = null
                }
            },
        )
    } else if (showRunHistory) {
        RunHistoryScreen(
            records = runRecords,
            historyCorrupt = runHistoryCorrupt,
            workflows = workflows,
            onBack = { showRunHistory = false },
            onOpenWorkflow = { workflow, stepPath ->
                initialEditingStepPath = stepPath
                editingWorkflow = workflow
            },
            onClear = {
                onClearRunHistory()
                runRecords = emptyList()
                runHistoryCorrupt = false
            },
        )
    } else {
        WorkflowHome(
            workflows = workflows,
            folders = library.folders,
            workflowFolderIds = library.workflowFolderIds,
            onSaveFolder = { folder -> persist(library.withFolder(folder)) },
            onDeleteFolder = { folderId -> persist(library.withoutFolder(folderId)) },
            onMoveWorkflow = { workflowId, folderId ->
                persist(library.moveWorkflow(workflowId, folderId))
            },
            onInstallSettingsPack = {
                val availablePacks = listOf(
                    Triple(
                        SettingsWorkflowPack.definition,
                        R.string.settings_folder_name,
                        listOf(
                            R.string.settings_workflow_open_home,
                            R.string.settings_workflow_verify_list,
                            R.string.settings_workflow_scroll_list,
                            R.string.settings_workflow_open_network,
                            R.string.settings_workflow_open_connected_devices,
                            R.string.settings_workflow_open_apps,
                            R.string.settings_workflow_open_notifications,
                            R.string.settings_workflow_open_sound,
                            R.string.settings_workflow_open_modes,
                            R.string.settings_workflow_open_display,
                            R.string.settings_workflow_open_storage,
                            R.string.settings_workflow_open_location,
                            R.string.settings_workflow_open_accessibility,
                            R.string.settings_workflow_open_accounts,
                            R.string.settings_workflow_open_languages,
                        ),
                    ),
                    Triple(
                        ClockWorkflowPack.definition,
                        R.string.clock_folder_name,
                        listOf(
                            R.string.clock_workflow_open,
                            R.string.clock_workflow_verify_time,
                            R.string.clock_workflow_verify_date,
                        ),
                    ),
                    Triple(
                        FilesWorkflowPack.definition,
                        R.string.files_folder_name,
                        listOf(
                            R.string.files_workflow_open,
                            R.string.files_workflow_verify_header,
                            R.string.files_workflow_verify_list,
                        ),
                    ),
                    Triple(
                        AiIndexFingerSelfTestPack.definition,
                        R.string.self_test_folder_name,
                        listOf(
                            R.string.self_test_workflow_verify_home,
                            R.string.self_test_workflow_verify_observation_runtime,
                        ),
                    ),
                ).filter { (pack) ->
                    context.packageManager.getLaunchIntentForPackage(pack.packageName) != null
                }
                var updatedLibrary = library
                var addedCount = 0
                availablePacks.forEach { (pack, folderNameRes, workflowNameResources) ->
                    val result = pack.install(
                        library = updatedLibrary,
                        folderName = context.getString(folderNameRes),
                        workflowNames = workflowNameResources.map(context::getString),
                    )
                    updatedLibrary = result.library
                    addedCount += result.addedWorkflowCount
                }
                persist(updatedLibrary)
                runMessage = if (addedCount == 0) {
                    context.getString(R.string.system_packs_already_installed)
                } else {
                    context.getString(R.string.system_packs_installed, addedCount)
                }
            },
            runRecords = runRecords,
            runHistoryCorrupt = runHistoryCorrupt,
            schedules = schedules,
            onCreate = { workflow ->
                initialEditingStepPath = null
                editingWorkflow = workflow
            },
            onEdit = {
                initialEditingStepPath = null
                editingWorkflow = it
            },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            onExportAll = {
                pendingBundleExport = library
                bundleExportLauncher.launch("ai-index-finger-backup.json")
            },
            onExport = { workflow ->
                pendingExport = workflow
                exportLauncher.launch(workflow.exportFileName())
            },
            onDuplicate = { workflow ->
                val duplicate = workflow.copy(
                    id = newId(),
                    name = context.getString(R.string.workflow_copy_name, workflow.name),
                )
                persist(
                    library.copy(
                        workflows = workflows + duplicate,
                        workflowFolderIds = library.folderIdFor(workflow.id)?.let { folderId ->
                            library.workflowFolderIds + (duplicate.id to folderId)
                        } ?: library.workflowFolderIds,
                    ),
                )
            },
            onCompare = { before, after -> workflowComparison = before to after },
                        onViewVersions = { workflow ->
                            coroutineScope.launch {
                                runCatching { onListVersions(workflow.id) }
                                    .onSuccess { versions -> versionHistory = workflow to versions }
                                    .onFailure {
                                        runMessage = context.getString(R.string.workflow_versions_load_failed)
                                    }
                            }
                        },
            onDelete = { workflow ->
                            runCatching { onCancelSchedule(workflow.id) }
                                .onSuccess {
                                    schedules = it
                                    persist(library.copy(workflows = workflows.filterNot { it.id == workflow.id }))
                                }
                                .onFailure { error ->
                                    runMessage = context.getString(
                                        if (error is ScheduleStorageException) {
                                            R.string.schedule_storage_corrupt
                                        } else {
                                            R.string.schedule_failed
                                        },
                                    )
                                }
            },
            onSchedule = { workflow, targetEpochMillis, recurrence ->
                if (!workflow.isReadyToRun()) {
                    runMessage = context.getString(
                        R.string.cannot_schedule,
                        workflow.readinessIssues().first().localizedMessage(context),
                    )
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSchedule = Triple(workflow, targetEpochMillis, recurrence)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching { onSchedule(workflow, targetEpochMillis, recurrence) }
                        .onSuccess {
                            schedules = it
                            runMessage = context.getString(R.string.workflow_scheduled, workflow.name)
                        }
                        .onFailure { error ->
                            runMessage = context.getString(
                                if (error is ScheduleStorageException) {
                                    R.string.schedule_storage_corrupt
                                } else {
                                    R.string.schedule_failed
                                },
                            )
                        }
                }
            },
            onCancelSchedule = { workflow ->
                runCatching { onCancelSchedule(workflow.id) }
                    .onSuccess {
                        schedules = it
                        runMessage = context.getString(R.string.workflow_schedule_cancelled, workflow.name)
                    }
                    .onFailure { error ->
                        runMessage = context.getString(
                            if (error is ScheduleStorageException) {
                                R.string.schedule_storage_corrupt
                            } else {
                                R.string.schedule_failed
                            },
                        )
                    }
            },
            onClearRunHistory = {
                onClearRunHistory()
                runRecords = emptyList()
                runHistoryCorrupt = false
            },
            onViewRunHistory = { showRunHistory = true },
            runningWorkflowId = runningWorkflowId,
            runMessage = runMessage,
            onRun = { workflow ->
                val service = AutomationAccessibilityService.instance
                val issue = workflow.readinessIssues().firstOrNull()
                if (issue != null) {
                    runMessage = context.getString(R.string.cannot_run, issue.localizedMessage(context))
                } else if (service == null) {
                    runMessage = context.getString(R.string.enable_automation_before_run)
                    requestAccessibilitySetup()
                } else {
                    val started = service.startWorkflow(workflow)
                    runMessage = if (started) {
                        context.getString(R.string.running_workflow, workflow.name)
                    } else {
                        context.getString(R.string.another_workflow_running)
                    }
                }
            },
            onPreflight = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = if (Build.VERSION.SDK_INT < 33) {
                    NotificationPreflightStatus.NotRequired
                } else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationPreflightStatus.Granted
                } else {
                    NotificationPreflightStatus.Denied
                }
                preflightReport = workflow to buildWorkflowPreflightReport(
                    workflow = workflow,
                    accessibilityConnected = service != null,
                    notificationStatus = notificationStatus,
                    isLaunchable = { packageName, intentAction ->
                        intentAction?.let { Intent(it).setPackage(packageName) }
                            ?.resolveActivity(context.packageManager) != null ||
                            intentAction == null &&
                            context.packageManager.getLaunchIntentForPackage(packageName) != null
                    },
                    countMatches = { selector -> service?.countMatches(selector) ?: 0 },
                    imageCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                )
            },
            onStop = { AutomationAccessibilityService.instance?.stopWorkflow() },
            onOpenAccessibilitySettings = requestAccessibilitySetup,
            onReviewAccessibilityDisclosure = {
                if (accessibilityDisclosureGate.reviewDisclosure() ==
                    AccessibilityDisclosureAction.ShowDisclosure
                ) {
                    showAccessibilityDisclosure = true
                }
            },
        )
    }
    versionHistory?.let { (current, versions) ->
        WorkflowVersionHistoryDialog(
            current = current,
            versions = versions,
            onDismiss = { versionHistory = null },
            onPreview = { version ->
                versionHistory = null
                workflowComparison = version.workflow to current
            },
            onRollback = { version ->
                coroutineScope.launch {
                    runCatching { onRollback(current.id, version.versionId) }
                        .onSuccess { restored ->
                            library = library.copy(
                                workflows = workflows.map { workflow ->
                                    if (workflow.id == restored.id) restored else workflow
                                },
                            )
                            versionHistory = restored to runCatching { onListVersions(restored.id) }
                                .getOrDefault(emptyList())
                            runMessage = context.getString(R.string.workflow_rollback_complete, restored.name)
                        }
                        .onFailure {
                            runMessage = context.getString(R.string.workflow_rollback_failed)
                        }
                }
            },
        )
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onDecline = {
                accessibilityDisclosureGate.declineDisclosure()
                showAccessibilityDisclosure = false
            },
            onAccept = {
                val action = accessibilityDisclosureGate.acceptDisclosure()
                onAccessibilityDisclosureAcknowledged()
                showAccessibilityDisclosure = false
                if (action == AccessibilityDisclosureAction.OpenSettings) {
                    onOpenAccessibilitySettings()
                }
            },
        )
    }
    preflightReport?.let { (workflow, report) ->
        PreflightReportDialog(
            workflow = workflow,
            report = report,
            onDismiss = { preflightReport = null },
            onEditStep = { path ->
                preflightReport = null
                initialEditingStepPath = path
                editingWorkflow = workflow
            },
            onRecoveryAction = { action ->
                preflightReport = null
                when (action) {
                    PreflightRecoveryAction.SetUpAutomation -> requestAccessibilitySetup()
                }
            },
        )
    }
}

@Composable
internal fun WorkflowHome(
    workflows: List<Workflow>,
    folders: List<WorkflowFolder>,
    workflowFolderIds: Map<String, String>,
    onSaveFolder: (WorkflowFolder) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveWorkflow: (String, String?) -> Unit,
    onInstallSettingsPack: () -> Unit,
    runRecords: List<RunRecord>,
    runHistoryCorrupt: Boolean,
    schedules: List<WorkflowSchedule>,
    onCreate: (Workflow) -> Unit,
    onEdit: (Workflow) -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
    onExport: (Workflow) -> Unit,
    onDuplicate: (Workflow) -> Unit,
    onCompare: (Workflow, Workflow) -> Unit,
        onViewVersions: (Workflow) -> Unit,
    onDelete: (Workflow) -> Unit,
    onSchedule: (Workflow, Long, ScheduleRecurrence) -> Unit,
    onCancelSchedule: (Workflow) -> Unit,
    onClearRunHistory: () -> Unit,
    onViewRunHistory: () -> Unit,
    runningWorkflowId: String?,
    runMessage: String?,
    onRun: (Workflow) -> Unit,
    onPreflight: (Workflow) -> Unit,
    onStop: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onReviewAccessibilityDisclosure: () -> Unit,
) {
    val serviceConnected by AutomationAccessibilityService.connected.collectAsStateWithLifecycle()
    val currentStepId by AutomationAccessibilityService.currentStepId.collectAsStateWithLifecycle()
    val workflowStartedAtMillis by AutomationAccessibilityService.workflowStartedAtMillis
        .collectAsStateWithLifecycle()
    val observedNodes by AutomationAccessibilityService.observedNodes.collectAsStateWithLifecycle()
    var workflowToDelete by remember { mutableStateOf<Workflow?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var showNodeInspector by remember { mutableStateOf(false) }
    var workflowToSchedule by remember { mutableStateOf<Workflow?>(null) }
    var workflowQuery by remember { mutableStateOf("") }
    var showCreateWorkflow by remember { mutableStateOf(false) }
    var selectedWorkflowExample by remember { mutableStateOf<WorkflowExample?>(null) }
    var workflowToCompare by remember { mutableStateOf<Workflow?>(null) }
    var selectedFolderFilter by remember { mutableStateOf<WorkflowFolderSelection>(WorkflowFolderSelection.All) }
    var showFolderManager by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<WorkflowFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<WorkflowFolder?>(null) }
    var workflowToMove by remember { mutableStateOf<Workflow?>(null) }
    val untitledWorkflowName = stringResource(R.string.untitled_workflow)
    DisposableEffect(showNodeInspector) {
        val observationLease = if (showNodeInspector) {
            AutomationAccessibilityService.acquireObservationLease()
        } else {
            null
        }
        onDispose { observationLease?.close() }
    }
    LaunchedEffect(folders, selectedFolderFilter) {
        val selected = selectedFolderFilter as? WorkflowFolderSelection.Folder
        if (selected != null && folders.none { it.id == selected.id }) {
            selectedFolderFilter = WorkflowFolderSelection.All
        }
    }
    val visibleWorkflows = remember(workflows, workflowQuery, selectedFolderFilter, workflowFolderIds) {
        filterWorkflows(workflows, workflowFolderIds, workflowQuery, selectedFolderFilter)
    }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(workflowStartedAtMillis) {
        val startedAtMillis = workflowStartedAtMillis
        if (startedAtMillis == null) {
            elapsedMillis = 0L
        } else {
            while (true) {
                elapsedMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
                delay(1_000L)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { showCreateWorkflow = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.new_workflow)) }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("AI Index Finger", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.workflows), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            PermissionStatus(
                connected = serviceConnected,
                onOpenSettings = onOpenAccessibilitySettings,
                onReviewDisclosure = onReviewAccessibilityDisclosure,
            )
            if (runningWorkflowId != null) {
                val currentWorkflow = workflows.firstOrNull { it.id == runningWorkflowId }
                val currentStep = currentStepId?.let { currentWorkflow?.steps?.findById(it) }
                Spacer(Modifier.height(14.dp))
                RunningWorkflowStatus(
                    workflowName = currentWorkflow?.name ?: stringResource(R.string.workflow),
                    stepName = currentStep?.title() ?: currentStepId,
                    elapsedMillis = elapsedMillis,
                    onStop = onStop,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showNodeInspector = true },
                enabled = serviceConnected,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.inspect_recent_elements, observedNodes.size)) }
            runMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.my_workflows), modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = onExportAll,
                    enabled = workflows.isNotEmpty() && runningWorkflowId == null,
                ) { Text(stringResource(R.string.backup)) }
                TextButton(onClick = onImport, enabled = runningWorkflowId == null) { Text(stringResource(R.string.import_action)) }
            }
            OutlinedButton(
                onClick = onInstallSettingsPack,
                enabled = runningWorkflowId == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SETTINGS_PACK_INSTALL_TAG),
            ) {
                Text(stringResource(R.string.install_system_examples))
            }
            if (workflows.isNotEmpty()) {
                OutlinedTextField(
                    value = workflowQuery,
                    onValueChange = { workflowQuery = it },
                    label = { Text(stringResource(R.string.search_workflows)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.workflow_count, workflows.size, visibleWorkflows.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolderFilterButton(
                    label = stringResource(R.string.folder_all),
                    count = workflows.size,
                    selected = selectedFolderFilter == WorkflowFolderSelection.All,
                    modifier = Modifier.testTag(FOLDER_FILTER_ALL_TAG),
                    onClick = { selectedFolderFilter = WorkflowFolderSelection.All },
                )
                FolderFilterButton(
                    label = stringResource(R.string.folder_unfiled),
                    count = workflows.count { workflowFolderIds[it.id] == null },
                    selected = selectedFolderFilter == WorkflowFolderSelection.Unfiled,
                    modifier = Modifier.testTag(FOLDER_FILTER_UNFILED_TAG),
                    onClick = { selectedFolderFilter = WorkflowFolderSelection.Unfiled },
                )
                sortedFolders(folders).forEach { folder ->
                    FolderFilterButton(
                        label = folder.name,
                        count = workflows.count { workflowFolderIds[it.id] == folder.id },
                        selected = selectedFolderFilter == WorkflowFolderSelection.Folder(folder.id),
                        modifier = Modifier.testTag(folderFilterTag(folder.id)),
                        onClick = { selectedFolderFilter = WorkflowFolderSelection.Folder(folder.id) },
                    )
                }
                TextButton(
                    onClick = { showFolderManager = true },
                    modifier = Modifier.testTag(FOLDER_MANAGE_TAG),
                ) {
                    Text(stringResource(R.string.manage_folders))
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            if (workflows.isEmpty()) {
                Text(
                    stringResource(R.string.no_workflows),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visibleWorkflows.isEmpty()) {
                Text(
                    stringResource(R.string.no_matching_workflows),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visibleWorkflows.forEach { workflow ->
                val schedule = schedules.firstOrNull { it.workflowId == workflow.id }
                WorkflowRow(
                    workflow = workflow,
                    isRunning = workflow.id == runningWorkflowId,
                    canCompare = workflows.size > 1,
                    schedule = schedule,
                    onEdit = { onEdit(workflow) },
                    onExport = { onExport(workflow) },
                    onDuplicate = { onDuplicate(workflow) },
                    onMove = { workflowToMove = workflow },
                    moveTag = folderMoveWorkflowTag(workflow.id),
                    onCompare = { workflowToCompare = workflow },
                                        onViewVersions = { onViewVersions(workflow) },
                    onDelete = { workflowToDelete = workflow },
                    onSchedule = { workflowToSchedule = workflow },
                    onCancelSchedule = { onCancelSchedule(workflow) },
                    onRun = { onRun(workflow) },
                    onPreflight = { onPreflight(workflow) },
                    onStop = onStop,
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.run_history), modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (runRecords.isNotEmpty()) {
                    TextButton(onClick = onViewRunHistory) { Text(stringResource(R.string.view_all_count, runRecords.size)) }
                }
                if (runRecords.isNotEmpty() || runHistoryCorrupt) {
                    TextButton(onClick = { confirmClearHistory = true }) { Text(stringResource(R.string.clear)) }
                }
            }
            HorizontalDivider()
            if (runRecords.isEmpty()) {
                Text(
                    stringResource(R.string.no_run_records),
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                runRecords.take(VISIBLE_RUN_RECORDS).forEach { record ->
                    RunRecordRow(record)
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    workflowToDelete?.let { workflow ->
        AlertDialog(
            onDismissRequest = { workflowToDelete = null },
            title = { Text(stringResource(R.string.delete_workflow_title)) },
            text = { Text(stringResource(R.string.delete_workflow_message, workflow.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(workflow)
                        workflowToDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { workflowToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showFolderManager) {
        WorkflowFolderManagerDialog(
            folders = folders,
            workflowFolderIds = workflowFolderIds,
            onDismiss = { showFolderManager = false },
            onCreate = {
                folderToEdit = WorkflowFolder(newId(), "")
                showFolderManager = false
            },
            onRename = {
                folderToEdit = it
                showFolderManager = false
            },
            onDelete = {
                folderToDelete = it
                showFolderManager = false
            },
        )
    }
    folderToEdit?.let { folder ->
        WorkflowFolderNameDialog(
            folder = folder,
            folders = folders,
            onDismiss = { folderToEdit = null },
            onSave = {
                onSaveFolder(it)
                folderToEdit = null
            },
        )
    }
    folderToDelete?.let { folder ->
        val affectedCount = workflowFolderIds.count { it.value == folder.id }
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = {
                Text(
                    if (affectedCount == 0) {
                        stringResource(R.string.delete_empty_folder_message, folder.name)
                    } else {
                        stringResource(R.string.delete_folder_message, folder.name, affectedCount)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder(folder.id)
                        folderToDelete = null
                    },
                    modifier = Modifier.testTag(FOLDER_DELETE_CONFIRM_TAG),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    workflowToMove?.let { workflow ->
        WorkflowMoveDialog(
            workflow = workflow,
            folders = folders,
            currentFolderId = workflowFolderIds[workflow.id],
            onDismiss = { workflowToMove = null },
            onMove = { folderId ->
                onMoveWorkflow(workflow.id, folderId)
                workflowToMove = null
            },
        )
    }

    workflowToCompare?.let { source ->
        AlertDialog(
            onDismissRequest = { workflowToCompare = null },
            title = { Text(stringResource(R.string.compare_with_workflow, source.name)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    workflows.filterNot { it.id == source.id }.forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                workflowToCompare = null
                                onCompare(source, candidate)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(candidate.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { workflowToCompare = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showCreateWorkflow) {
        WorkflowExampleCatalogDialog(
            onDismiss = { showCreateWorkflow = false },
            onCreateBlank = {
                showCreateWorkflow = false
                onCreate(
                    Workflow(
                        id = newId(),
                        name = untitledWorkflowName,
                        steps = emptyList(),
                        state = WorkflowState.Draft,
                    ),
                )
            },
            onSelectExample = { example ->
                showCreateWorkflow = false
                selectedWorkflowExample = example
            },
        )
    }
    selectedWorkflowExample?.let { example ->
        WorkflowExampleDetailsDialog(
            example = example,
            onDismiss = {
                selectedWorkflowExample = null
                showCreateWorkflow = true
            },
            onUse = { localizedTitle ->
                selectedWorkflowExample = null
                onCreate(example.create(localizedTitle, ::newId))
            },
        )
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.clear_run_history_title)) },
            text = { Text(stringResource(R.string.clear_run_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRunHistory()
                        confirmClearHistory = false
                    },
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showNodeInspector) {
        NodeInspectorDialog(
            nodes = observedNodes,
            onDismiss = { showNodeInspector = false },
        )
    }
    workflowToSchedule?.let { workflow ->
        ScheduleDialog(
            workflowName = workflow.name,
            onDismiss = { workflowToSchedule = null },
            onSchedule = { targetEpochMillis, recurrence ->
                onSchedule(workflow, targetEpochMillis, recurrence)
                workflowToSchedule = null
            },
        )
    }
}

@Composable
private fun FolderFilterButton(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selectionModifier = modifier.semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onClick, modifier = selectionModifier) {
            Text(stringResource(R.string.folder_filter_count, label, count))
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = selectionModifier) {
            Text(stringResource(R.string.folder_filter_count, label, count))
        }
    }
}

@Composable
private fun WorkflowFolderManagerDialog(
    folders: List<WorkflowFolder>,
    workflowFolderIds: Map<String, String>,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRename: (WorkflowFolder) -> Unit,
    onDelete: (WorkflowFolder) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_folders)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        stringResource(R.string.no_folders),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sortedFolders(folders).forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                R.string.folder_item_count,
                                folder.name,
                                workflowFolderIds.count { it.value == folder.id },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        val renameDescription = stringResource(R.string.rename_folder_accessibility, folder.name)
                        TextButton(
                            onClick = { onRename(folder) },
                            modifier = Modifier
                                .testTag(folderRenameTag(folder.id))
                                .semantics { contentDescription = renameDescription },
                        ) {
                            Text(stringResource(R.string.rename))
                        }
                        val deleteDescription = stringResource(R.string.delete_folder_accessibility, folder.name)
                        TextButton(
                            onClick = { onDelete(folder) },
                            modifier = Modifier
                                .testTag(folderDeleteTag(folder.id))
                                .semantics { contentDescription = deleteDescription },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCreate,
                modifier = Modifier.testTag(FOLDER_CREATE_TAG),
            ) { Text(stringResource(R.string.create_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun WorkflowFolderNameDialog(
    folder: WorkflowFolder,
    folders: List<WorkflowFolder>,
    onDismiss: () -> Unit,
    onSave: (WorkflowFolder) -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val normalizedName = name.trim()
    val duplicate = folders.any {
        it.id != folder.id && it.name.equals(normalizedName, ignoreCase = true)
    }
    val errorRes = when {
        normalizedName.isEmpty() -> R.string.folder_name_required
        duplicate -> R.string.folder_name_duplicate
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (folder.name.isEmpty()) R.string.create_folder else R.string.rename_folder))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.testTag(FOLDER_NAME_INPUT_TAG),
                label = { Text(stringResource(R.string.folder_name)) },
                supportingText = { errorRes?.let { Text(stringResource(it)) } },
                isError = errorRes != null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = errorRes == null,
                onClick = { onSave(folder.copy(name = normalizedName)) },
                modifier = Modifier.testTag(FOLDER_NAME_SAVE_TAG),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun WorkflowMoveDialog(
    workflow: Workflow,
    folders: List<WorkflowFolder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_workflow_title, workflow.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FolderDestinationRow(
                    name = stringResource(R.string.folder_unfiled),
                    selected = currentFolderId == null,
                    modifier = Modifier.testTag(FOLDER_DESTINATION_UNFILED_TAG),
                    onClick = { onMove(null) },
                )
                sortedFolders(folders).forEach { folder ->
                    FolderDestinationRow(
                        name = folder.name,
                        selected = currentFolderId == folder.id,
                        modifier = Modifier.testTag(folderDestinationTag(folder.id)),
                        onClick = { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FolderDestinationRow(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name)
    }
}

@Composable
private fun ScheduleDialog(
    workflowName: String,
    onDismiss: () -> Unit,
    onSchedule: (Long, ScheduleRecurrence) -> Unit,
) {
    val context = LocalContext.current
    val initialDateTime = remember { LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0) }
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var recurrence by remember { mutableStateOf(ScheduleRecurrence.Once) }
    val targetResult = runCatching {
        localScheduleEpochMillis(selectedDate, selectedTime, ZoneId.systemDefault()).also {
            scheduleDelayMillis(it, System.currentTimeMillis())
        }
    }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_workflow_title, workflowName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.schedule_workflow_description))
                Text(LocalDateTime.of(selectedDate, selectedTime).format(formatter))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth,
                            ).apply {
                                datePicker.minDate = System.currentTimeMillis()
                                datePicker.maxDate = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1_000
                            }.show()
                        },
                    ) { Text(stringResource(R.string.choose_date)) }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> selectedTime = LocalTime.of(hour, minute) },
                                selectedTime.hour,
                                selectedTime.minute,
                                android.text.format.DateFormat.is24HourFormat(context),
                            ).show()
                        },
                    ) { Text(stringResource(R.string.choose_time)) }
                }
                Text(stringResource(R.string.schedule_recurrence), fontWeight = FontWeight.SemiBold)
                ScheduleRecurrence.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recurrence = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                        )
                        Text(option.localizedLabel())
                    }
                }
                targetResult.exceptionOrNull()?.message?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.schedule_delay_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetResult.isSuccess,
                onClick = { onSchedule(targetResult.getOrThrow(), recurrence) },
            ) { Text(stringResource(R.string.schedule)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NodeInspectorDialog(nodes: List<ObservedNode>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visibleNodes = nodes.filter { it.matchesQuery(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recent_accessibility_elements)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.filter_elements)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.element_count, nodes.size, visibleNodes.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (nodes.isEmpty()) {
                    Text(
                        stringResource(R.string.observe_elements_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                visibleNodes.forEachIndexed { index, node ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${index + 1}. ${node.displayName()}", fontWeight = FontWeight.SemiBold)
                        NodeProperty(stringResource(R.string.package_name), node.packageName)
                        NodeProperty(stringResource(R.string.resource_id), node.viewId)
                        NodeProperty(stringResource(R.string.text_label), node.text)
                        NodeProperty(stringResource(R.string.description_label), node.contentDescription)
                        NodeProperty(stringResource(R.string.class_name), node.className)
                        NodeProperty(stringResource(R.string.bounds), node.bounds)
                        NodeProperty(
                            stringResource(R.string.status),
                            stringResource(
                                R.string.element_state,
                                node.clickable,
                                node.longClickable,
                                node.scrollable,
                                node.enabled,
                            ),
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun ObservedNode.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return listOf(packageName, viewId, text, contentDescription, className, bounds)
        .filterNotNull()
        .any { it.contains(query, ignoreCase = true) }
}

@Composable
private fun NodeProperty(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Text("$label: $value", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun WorkflowEditor(
    workflow: Workflow,
    initialEditingStepPath: StepPath? = null,
    onTest: (Workflow) -> Unit,
    onBack: () -> Unit,
    onSave: (Workflow) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(workflow.id) { mutableStateOf(workflow.name) }
    var defaultTimeoutText by remember(workflow.id) {
        mutableStateOf(workflow.defaultStepTimeoutMillis.toString())
    }
    var steps by remember(workflow.id) { mutableStateOf(workflow.steps) }
    var currentListPath by remember(workflow.id) {
        mutableStateOf(initialEditingStepPath?.parent ?: StepListPath())
    }
    var showClickDialog by remember { mutableStateOf(false) }
    var showImageClickDialog by remember { mutableStateOf(false) }
    var showLongClickDialog by remember { mutableStateOf(false) }
    var showLaunchDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showSwipeDialog by remember { mutableStateOf(false) }
    var showTapDialog by remember { mutableStateOf(false) }
    var showScrollDialog by remember { mutableStateOf(false) }
    var showWaitDialog by remember { mutableStateOf(false) }
    var showWaitNodeDialog by remember { mutableStateOf(false) }
    var showVariableDialog by remember { mutableStateOf(false) }
    var showReadNodeTextDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var showNodeConditionDialog by remember { mutableStateOf(false) }
    var policyStepPath by remember { mutableStateOf<StepPath?>(null) }
    var editingStepPath by remember(workflow.id) { mutableStateOf(initialEditingStepPath) }
    var stepToDeletePath by remember { mutableStateOf<StepPath?>(null) }
    var confirmDiscardChanges by remember { mutableStateOf(false) }
    var unrecognizedClickCount by remember { mutableStateOf(0) }
    var showAllValidationIssues by remember(workflow.id) { mutableStateOf(false) }
    val observedNodes by AutomationAccessibilityService.observedNodes.collectAsStateWithLifecycle()
    val pendingOverlayAction by AutomationAccessibilityService.pendingOverlayAction.collectAsStateWithLifecycle()
    val overlayStatus by AutomationAccessibilityService.overlayStatus.collectAsStateWithLifecycle()
    val defaultTimeoutMillis = defaultTimeoutText.toLongOrNull()
    val currentSteps = steps.stepsAt(currentListPath)
    val validationIssues = if (name.isNotBlank() && defaultTimeoutMillis != null && defaultTimeoutMillis > 0) {
        WorkflowValidator.validate(
            workflow.copy(
                name = name.trim(),
                steps = steps,
                defaultStepTimeoutMillis = defaultTimeoutMillis,
            ),
        )
    } else {
        emptyList()
    }
    val hasUnsavedChanges = name != workflow.name ||
        defaultTimeoutMillis != workflow.defaultStepTimeoutMillis ||
        steps != workflow.steps
    val requestBack = {
        if (currentListPath.segments.isNotEmpty()) {
            currentListPath = StepListPath(currentListPath.segments.dropLast(1))
        } else if (hasUnsavedChanges) {
            confirmDiscardChanges = true
        } else {
            onBack()
        }
    }

    LaunchedEffect(pendingOverlayAction) {
        val action = pendingOverlayAction ?: return@LaunchedEffect
        when (action) {
            is PendingOverlayAction.RecordedClicks -> {
                if (matchesRecordingDestination(action.workflowId, workflow.id) &&
                    runCatching { steps.stepsAt(action.listPath) }.isSuccess
                ) {
                    action.actions.forEach { recordedAction ->
                        val latestSteps = steps.stepsAt(action.listPath)
                        val step = when (recordedAction) {
                            is com.aiindexfinger.automation.RecordedAction.Click -> Step.RecordedClick(
                                id = newId(),
                                x = recordedAction.target.x,
                                y = recordedAction.target.y,
                                selector = recordedAction.target.selector,
                                control = recordedAction.target.control,
                            )
                            is com.aiindexfinger.automation.RecordedAction.ExistingStep -> recordedAction.step
                        }
                        steps = steps.insertStep(
                            action.listPath,
                            latestSteps.size,
                            step,
                        )
                    }
                    unrecognizedClickCount = action.issues.size
                    AutomationAccessibilityService.consumePendingOverlayAction(action)
                }
            }
        }
    }

    BackHandler(onBack = requestBack)
    if (unrecognizedClickCount > 0) {
        AlertDialog(
            onDismissRequest = { unrecognizedClickCount = 0 },
            title = { Text(stringResource(R.string.click_recording_unrecognized_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.click_recording_unrecognized_message,
                        unrecognizedClickCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { unrecognizedClickCount = 0 }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val canSave = name.isNotBlank() &&
                        defaultTimeoutMillis != null && defaultTimeoutMillis > 0
                    Button(
                        enabled = canSave,
                        onClick = {
                            onTest(
                                workflow.copy(
                                    schemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
                                    name = name.trim(),
                                    steps = steps,
                                    defaultStepTimeoutMillis = requireNotNull(defaultTimeoutMillis),
                                    state = WorkflowState.Draft,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.test_entire_workflow)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = canSave,
                            onClick = {
                                onSave(
                                    workflow.copy(
                                        schemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
                                        name = name.trim(),
                                        steps = steps,
                                        defaultStepTimeoutMillis = requireNotNull(defaultTimeoutMillis),
                                        state = WorkflowState.Draft,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.save_draft)) }
                        Button(
                            enabled = canSave && validationIssues.isEmpty(),
                            onClick = {
                                onSave(
                                    workflow.copy(
                                        schemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
                                        name = name.trim(),
                                        steps = steps,
                                        defaultStepTimeoutMillis = requireNotNull(defaultTimeoutMillis),
                                        state = WorkflowState.Ready,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.save_ready)) }
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = requestBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.workflow_editor), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.workflow_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = defaultTimeoutText,
                onValueChange = { defaultTimeoutText = it },
                label = { Text(stringResource(R.string.default_step_timeout)) },
                isError = defaultTimeoutMillis == null || defaultTimeoutMillis <= 0,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (validationIssues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.validation_issue_count, validationIssues.size),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                val visibleIssues = if (showAllValidationIssues) validationIssues else validationIssues.take(3)
                visibleIssues.forEach { issue ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            issue.localizedMessage(context),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                        issue.stepId?.let(steps::uniquePathTo)?.let { path ->
                            TextButton(
                                onClick = {
                                    currentListPath = path.parent
                                    editingStepPath = path
                                },
                            ) { Text(stringResource(R.string.edit_step)) }
                        }
                    }
                }
                if (validationIssues.size > 3) {
                    TextButton(onClick = { showAllValidationIssues = !showAllValidationIssues }) {
                        Text(
                            if (showAllValidationIssues) {
                                stringResource(R.string.collapse)
                            } else {
                                stringResource(R.string.more_issues, validationIssues.size - 3)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (currentListPath.segments.isEmpty()) R.string.steps else R.string.nested_steps,
                    ),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (currentListPath.segments.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            currentListPath = StepListPath(currentListPath.segments.dropLast(1))
                        },
                    ) { Text(stringResource(R.string.up_one_level)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (currentSteps.isEmpty()) {
                Text(stringResource(R.string.add_first_action), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            currentSteps.forEachIndexed { index, step ->
                val stepPath = StepPath(currentListPath, index)
                StepRow(
                    index = index,
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < currentSteps.lastIndex,
                    onMoveUp = { steps = steps.moveStep(stepPath, index - 1) },
                    onMoveDown = { steps = steps.moveStep(stepPath, index + 1) },
                    canEdit = step.isActionEditable(),
                    onEdit = { editingStepPath = stepPath },
                    onEditPolicy = { policyStepPath = stepPath },
                    onDuplicate = {
                        steps = steps.duplicateStep(stepPath, ::newId)
                    },
                    onDelete = { stepToDeletePath = stepPath },
                    onOpenRepeat = (step as? Step.Repeat)?.let {
                        {
                            currentListPath = currentListPath.child(step.id, StepBranch.RepeatBody)
                        }
                    },
                    onOpenIfTrue = (step as? Step.IfElse)?.let {
                        { currentListPath = currentListPath.child(step.id, StepBranch.IfTrue) }
                    },
                    onOpenIfFalse = (step as? Step.IfElse)?.let {
                        { currentListPath = currentListPath.child(step.id, StepBranch.IfFalse) }
                    },
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.add_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showLaunchDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.launch_app))
                }
                Button(onClick = { showClickDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.click))
                }
            }
            OutlinedButton(
                onClick = { showImageClickDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.image_click)) }
            OutlinedButton(
                enabled = AutomationAccessibilityService.instance != null,
                onClick = {
                    AutomationAccessibilityService.instance?.startElementMonitor(workflow.id, currentListPath)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.monitor_elements_overlay)) }
            overlayStatus?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showLongClickDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.long_click)) }
            OutlinedButton(
                onClick = { showTapDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tap_coordinates)) }
            OutlinedButton(
                onClick = { showScrollDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.scroll_element)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showInputDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.input_text))
                }
                OutlinedButton(onClick = { showSwipeDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.swipe))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showWaitDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wait_action))
                }
                OutlinedButton(
                    onClick = {
                        steps = steps.insertStep(
                            currentListPath,
                            currentSteps.size,
                            Step.GlobalAction(newId(), SystemAction.Back),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.back)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        steps = steps.insertStep(
                            currentListPath,
                            currentSteps.size,
                            Step.GlobalAction(newId(), SystemAction.Home),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.home)) }
                OutlinedButton(
                    onClick = {
                        steps = steps.insertStep(
                            currentListPath,
                            currentSteps.size,
                            Step.GlobalAction(newId(), SystemAction.Recents),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.recents)) }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.add_logic), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showWaitNodeDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wait_for_element))
                }
                OutlinedButton(onClick = { showVariableDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_variable))
                }
            }
            OutlinedButton(
                enabled = observedNodes.isNotEmpty(),
                onClick = { showReadNodeTextDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.read_element_attribute)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = currentSteps.isNotEmpty(),
                    onClick = { showRepeatDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.repeat_steps)) }
                OutlinedButton(
                    enabled = currentSteps.isNotEmpty(),
                    onClick = { showConditionDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.variable_condition)) }
            }
            OutlinedButton(
                enabled = currentSteps.isNotEmpty() && observedNodes.isNotEmpty(),
                onClick = { showNodeConditionDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.element_exists_condition)) }
        }
    }

    if (showClickDialog) {
        ClickStepDialog(
            observedNodes = observedNodes,
            onDismiss = { showClickDialog = false },
            onAdd = { selector ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Click(UUID.randomUUID().toString(), selector),
                )
                showClickDialog = false
            },
        )
    }
    if (showImageClickDialog) {
        ImageClickStepDialog(
            onDismiss = { showImageClickDialog = false },
            onAdd = { imageStep ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    imageStep.copy(id = newId()),
                )
                showImageClickDialog = false
            },
        )
    }
    if (showLongClickDialog) {
        ClickStepDialog(
            observedNodes = observedNodes,
            title = "长按元素",
            onDismiss = { showLongClickDialog = false },
            onAdd = { selector ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.LongClick(newId(), selector),
                )
                showLongClickDialog = false
            },
        )
    }
    if (showLaunchDialog) {
        LaunchAppDialog(
            onDismiss = { showLaunchDialog = false },
            onAdd = { packageName ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.LaunchApp(UUID.randomUUID().toString(), packageName),
                )
                showLaunchDialog = false
            },
        )
    }
    if (showInputDialog) {
        InputTextDialog(
            observedNodes = observedNodes,
            onDismiss = { showInputDialog = false },
            onAdd = { selector, text, variableName, inputMethod ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.InputText(newId(), selector, text, variableName, inputMethod),
                )
                showInputDialog = false
            },
        )
    }
    if (showSwipeDialog) {
        SwipeDialog(
            onDismiss = { showSwipeDialog = false },
            onAdd = { startX, startY, endX, endY, duration ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Swipe(newId(), startX, startY, endX, endY, duration),
                )
                showSwipeDialog = false
            },
        )
    }
    if (showTapDialog) {
        TapDialog(
            onDismiss = { showTapDialog = false },
            onAdd = { x, y ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Tap(newId(), x, y),
                )
                showTapDialog = false
            },
        )
    }
    if (showScrollDialog) {
        ScrollStepDialog(
            observedNodes = observedNodes,
            onDismiss = { showScrollDialog = false },
            onAdd = { selector, direction ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Scroll(newId(), selector, direction),
                )
                showScrollDialog = false
            },
        )
    }
    if (showWaitDialog) {
        NumberDialog(
            title = "等待",
            label = "持续时间（毫秒）",
            initialValue = "1000",
            onDismiss = { showWaitDialog = false },
            onAdd = { duration ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Delay(newId(), duration),
                )
                showWaitDialog = false
            },
        )
    }
    if (showWaitNodeDialog) {
        WaitNodeDialog(
            observedNodes = observedNodes,
            onDismiss = { showWaitNodeDialog = false },
            onAdd = { selector, timeout, mustExist ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.WaitForNode(
                        newId(),
                        selector,
                        mustExist = mustExist,
                        timeoutMillis = timeout,
                    ),
                )
                showWaitNodeDialog = false
            },
        )
    }
    if (showVariableDialog) {
        SetVariableDialog(
            onDismiss = { showVariableDialog = false },
            onAdd = { variableName, value ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.SetVariable(newId(), variableName, value),
                )
                showVariableDialog = false
            },
        )
    }
    if (showReadNodeTextDialog) {
        ReadNodeTextDialog(
            observedNodes = observedNodes,
            onDismiss = { showReadNodeTextDialog = false },
            onSave = { selector, variableName, attribute ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.ReadNodeText(newId(), selector, variableName, attribute),
                )
                showReadNodeTextDialog = false
            },
        )
    }
    editingStepPath?.let { path ->
        when (val step = runCatching { steps.stepAt(path) }.getOrNull()) {
            is Step.LaunchApp -> LaunchAppDialog(
                initialPackageName = step.packageName,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { packageName ->
                    steps = steps.replaceStep(path, step.copy(packageName = packageName))
                    editingStepPath = null
                },
            )
            is Step.LongClick -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                title = "长按元素",
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { selector ->
                    steps = steps.replaceStep(path, step.copy(selector = selector))
                    editingStepPath = null
                },
            )
            is Step.Click -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { selector ->
                    steps = steps.replaceStep(path, step.copy(selector = selector))
                    editingStepPath = null
                },
            )
            is Step.RecordedClick -> RecordedClickDialog(
                initialStep = step,
                onDismiss = { editingStepPath = null },
                onSave = { targetMode, x, y ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(targetMode = targetMode, x = x, y = y),
                    )
                    editingStepPath = null
                },
            )
            is Step.ImageClick -> ImageClickStepDialog(
                initialStep = step,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { replacement ->
                    steps = steps.replaceStep(path, replacement.copy(id = step.id))
                    editingStepPath = null
                },
            )
            is Step.InputText -> InputTextDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { selector, text, variableName, inputMethod ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            selector = selector,
                            text = text,
                            variableName = variableName,
                            inputMethod = inputMethod,
                        ),
                    )
                    editingStepPath = null
                },
            )
            is Step.ReadNodeText -> ReadNodeTextDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                initialVariableName = step.variableName,
                initialAttribute = step.attribute,
                onDismiss = { editingStepPath = null },
                onSave = { selector, variableName, attribute ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(selector = selector, variableName = variableName, attribute = attribute),
                    )
                    editingStepPath = null
                },
            )
            is Step.Delay -> NumberDialog(
                title = "编辑等待",
                label = "持续时间（毫秒）",
                initialValue = step.durationMillis.toString(),
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { duration ->
                    steps = steps.replaceStep(path, step.copy(durationMillis = duration))
                    editingStepPath = null
                },
            )
            is Step.GlobalAction -> GlobalActionSettingsDialog(
                current = step.action,
                onDismiss = { editingStepPath = null },
                onSelect = { action ->
                    steps = steps.replaceStep(path, step.copy(action = action))
                    editingStepPath = null
                },
            )
            is Step.IfElse -> {
                when (val condition = step.condition) {
                    is Condition.Equals -> {
                        val variable = condition.left as? Value.Variable
                        val expected = condition.right as? Value.Literal
                        if (variable != null && expected != null) {
                            ConditionSettingsDialog(
                                initialVariableName = variable.name,
                                initialExpectedValue = expected.value,
                                initialOperator = condition.operator,
                                onDismiss = { editingStepPath = null },
                                onSave = { variableName, expectedValue, operator ->
                                    steps = steps.replaceStep(
                                        path,
                                        step.copy(
                                            condition = Condition.Equals(
                                                Value.Variable(variableName),
                                                Value.Literal(expectedValue),
                                                operator,
                                            ),
                                        ),
                                    )
                                    editingStepPath = null
                                },
                            )
                        }
                    }
                    is Condition.NodeExists -> NodeConditionDialog(
                        observedNodes = observedNodes,
                        initialSelector = condition.selector,
                        onDismiss = { editingStepPath = null },
                        onSave = { _, selector ->
                            steps = steps.replaceStep(
                                path,
                                step.copy(condition = Condition.NodeExists(selector)),
                            )
                            editingStepPath = null
                        },
                    )
                }
            }
            is Step.Swipe -> SwipeDialog(
                initialStep = step,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { startX, startY, endX, endY, duration ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            startX = startX,
                            startY = startY,
                            endX = endX,
                            endY = endY,
                            durationMillis = duration,
                        ),
                    )
                    editingStepPath = null
                },
            )
            is Step.Tap -> TapDialog(
                initialStep = step,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { x, y ->
                    steps = steps.replaceStep(path, step.copy(x = x, y = y))
                    editingStepPath = null
                },
            )
            is Step.SetVariable -> SetVariableDialog(
                initialName = step.name,
                initialValue = step.value,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { variableName, value ->
                    steps = steps.replaceStep(path, step.copy(name = variableName, value = value))
                    editingStepPath = null
                },
            )
            is Step.Repeat -> RepeatSettingsDialog(
                initialCount = step.times,
                onDismiss = { editingStepPath = null },
                onSave = { count ->
                    steps = steps.replaceStep(path, step.copy(times = count))
                    editingStepPath = null
                },
            )
            is Step.Scroll -> ScrollStepDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { selector, direction ->
                    steps = steps.replaceStep(path, step.copy(selector = selector, direction = direction))
                    editingStepPath = null
                },
            )
            is Step.WaitForNode -> WaitNodeDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "保存",
                onDismiss = { editingStepPath = null },
                onAdd = { selector, timeout, mustExist ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            selector = selector,
                            mustExist = mustExist,
                            timeoutMillis = timeout,
                        ),
                    )
                    editingStepPath = null
                },
            )
            else -> Unit
        }
    }
    if (showRepeatDialog) {
        WrapStepDialog(
            title = "重复步骤",
            valueLabel = "重复次数",
            initialValue = "2",
            steps = currentSteps,
            onDismiss = { showRepeatDialog = false },
            onAdd = { index, count ->
                val path = StepPath(currentListPath, index)
                val nestedStep = steps.stepAt(path)
                steps = steps.replaceStep(
                    path,
                    Step.Repeat(newId(), count.toInt(), listOf(nestedStep)),
                )
                showRepeatDialog = false
            },
        )
    }
    if (showConditionDialog) {
        ConditionDialog(
            steps = currentSteps,
            onDismiss = { showConditionDialog = false },
            onAdd = { index, variableName, expectedValue, operator ->
                val path = StepPath(currentListPath, index)
                val nestedStep = steps.stepAt(path)
                steps = steps.replaceStep(
                    path,
                    Step.IfElse(
                        id = newId(),
                        condition = Condition.Equals(
                            Value.Variable(variableName),
                            Value.Literal(expectedValue),
                            operator,
                        ),
                        whenTrue = listOf(nestedStep),
                    ),
                )
                showConditionDialog = false
            },
        )
    }
    if (showNodeConditionDialog) {
        NodeConditionDialog(
            observedNodes = observedNodes,
            steps = currentSteps,
            onDismiss = { showNodeConditionDialog = false },
            onSave = { selectedIndex, selector ->
                val path = StepPath(currentListPath, requireNotNull(selectedIndex))
                val nestedStep = steps.stepAt(path)
                steps = steps.replaceStep(
                    path,
                    Step.IfElse(
                        id = newId(),
                        condition = Condition.NodeExists(selector),
                        whenTrue = listOf(nestedStep),
                    ),
                )
                showNodeConditionDialog = false
            },
        )
    }
    policyStepPath?.let { path ->
        val currentStep = runCatching { steps.stepAt(path) }.getOrNull()
        if (currentStep == null) {
            policyStepPath = null
        } else {
        FailurePolicyDialog(
            currentStep = currentStep,
            defaultTimeoutMillis = defaultTimeoutMillis ?: workflow.defaultStepTimeoutMillis,
            onDismiss = { policyStepPath = null },
            onSelect = { policy, timeoutMillis ->
                steps = steps.replaceStep(
                    path,
                    currentStep.withExecutionSettings(timeoutMillis, policy),
                )
                policyStepPath = null
            },
        )
        }
    }
    stepToDeletePath?.let { path ->
        val step = runCatching { steps.stepAt(path) }.getOrNull()
        if (step == null) {
            stepToDeletePath = null
        } else {
            AlertDialog(
                onDismissRequest = { stepToDeletePath = null },
                title = { Text("删除步骤？") },
                text = { Text("将从此工作流中删除第 ${path.index + 1} 步：${step.title()}。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            steps = steps.removeStep(path)
                            stepToDeletePath = null
                        },
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { stepToDeletePath = null }) { Text("取消") }
                },
            )
        }
    }
    if (confirmDiscardChanges) {
        AlertDialog(
            onDismissRequest = { confirmDiscardChanges = false },
            title = { Text("放弃更改？") },
            text = { Text("未保存的工作流更改将会丢失。") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardChanges = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun GlobalActionSettingsDialog(
    current: SystemAction,
    onDismiss: () -> Unit,
    onSelect: (SystemAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemAction.entries.forEach { action ->
                    val selected = action == current
                    if (selected) {
                        Button(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.displayName())
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.displayName())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RepeatSettingsDialog(
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var countText by remember(initialCount) { mutableStateOf(initialCount.toString()) }
    val count = countText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重复设置") },
        text = { NodeField(countText, { countText = it }, "重复次数（1-${Step.Repeat.MAX_REPEAT_COUNT}）", true) },
        confirmButton = {
            TextButton(
                enabled = count != null && count in 1..Step.Repeat.MAX_REPEAT_COUNT,
                onClick = { onSave(requireNotNull(count)) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConditionSettingsDialog(
    initialVariableName: String,
    initialExpectedValue: String,
    initialOperator: ComparisonOperator,
    onDismiss: () -> Unit,
    onSave: (String, String, ComparisonOperator) -> Unit,
) {
    var variableName by remember(initialVariableName) { mutableStateOf(initialVariableName) }
    var expectedValue by remember(initialExpectedValue) { mutableStateOf(initialExpectedValue) }
    var operator by remember(initialOperator) { mutableStateOf(initialOperator) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("条件设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(variableName, { variableName = it }, "变量名", true)
                ComparisonOperatorSelector(operator) { operator = it }
                NodeField(expectedValue, { expectedValue = it }, "预期值")
            }
        },
        confirmButton = {
            TextButton(
                enabled = variableName.isNotBlank(),
                onClick = { onSave(variableName.trim(), expectedValue, operator) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FailurePolicyDialog(
    currentStep: Step,
    defaultTimeoutMillis: Long,
    onDismiss: () -> Unit,
    onSelect: (FailurePolicy, Long?) -> Unit,
) {
    val current = currentStep.failurePolicy
    var timeoutText by remember { mutableStateOf(currentStep.timeoutMillis?.toString().orEmpty()) }
    var retryAttempts by remember { mutableStateOf((current as? FailurePolicy.Retry)?.attempts?.toString() ?: "2") }
    var retryDelay by remember { mutableStateOf((current as? FailurePolicy.Retry)?.delayMillis?.toString() ?: "500") }
    val timeoutMillis = timeoutText.toLongOrNull()
    val timeoutValid = timeoutText.isBlank() || (timeoutMillis != null && timeoutMillis > 0)
    val attempts = retryAttempts.toIntOrNull()
    val delay = retryDelay.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("步骤设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(
                    timeoutText,
                    { timeoutText = it },
                    "超时毫秒数（留空使用 $defaultTimeoutMillis）",
                )
                Text("失败时", fontWeight = FontWeight.SemiBold)
                Button(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Stop, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("停止工作流")
                }
                OutlinedButton(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Continue, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("继续下一步") }
                Text("重试", fontWeight = FontWeight.SemiBold)
                NodeField(retryAttempts, { retryAttempts = it }, "重试次数（1-10）", true)
                NodeField(retryDelay, { retryDelay = it }, "重试间隔（毫秒）", true)
                OutlinedButton(
                    enabled = timeoutValid && attempts != null && attempts in 1..10 && delay != null && delay >= 0,
                    onClick = {
                        onSelect(
                            FailurePolicy.Retry(requireNotNull(attempts), requireNotNull(delay)),
                            timeoutMillis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("使用重试策略") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun InputTextDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.InputText? = null,
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, String, String?, TextInputMethod) -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }
    var selectedSelector by remember(initialStep) { mutableStateOf(initialStep?.selector) }
    var inputText by remember(initialStep) { mutableStateOf(initialStep?.text.orEmpty()) }
    var useVariable by remember(initialStep) { mutableStateOf(initialStep?.variableName != null) }
    var variableName by remember(initialStep) { mutableStateOf(initialStep?.variableName.orEmpty()) }
    var inputMethod by remember(initialStep) {
        mutableStateOf(initialStep?.inputMethod ?: TextInputMethod.SetText)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入文本") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VisualSelectorCapture(
                    onSelectorSelected = { selector -> selectedSelector = selector },
                )
                HorizontalDivider()
                Text("选择最近观察到的文本框", fontWeight = FontWeight.SemiBold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "打开目标应用并进入输入界面，然后返回此处。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            node.text ?: node.contentDescription ?: node.viewId ?: node.className.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                selectedSelector?.let {
                    Text(
                        "已选择：${it.viewId ?: it.text ?: it.contentDescription ?: it.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useVariable) "使用变量值" else "使用固定文本")
                        Text(
                            if (useVariable) "在此步骤运行时解析变量值" else "将此文本保存在工作流中",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = useVariable, onCheckedChange = { useVariable = it })
                }
                if (useVariable) {
                    NodeField(variableName, { variableName = it }, "变量名", true)
                } else {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("要输入的文本") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (inputMethod == TextInputMethod.Paste) "通过剪贴板粘贴" else "直接设置文本")
                        Text(
                            if (inputMethod == TextInputMethod.Paste) {
                                "临时使用剪贴板，若内容未被再次更改则自动恢复"
                            } else {
                                "使用无障碍服务的设置文本操作"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = inputMethod == TextInputMethod.Paste,
                        onCheckedChange = {
                            inputMethod = if (it) TextInputMethod.Paste else TextInputMethod.SetText
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && (!useVariable || variableName.isNotBlank()),
                onClick = {
                    onAdd(
                        requireNotNull(selectedSelector),
                        if (useVariable) "" else inputText,
                        variableName.trim().takeIf { useVariable },
                        inputMethod,
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SwipeDialog(
    initialStep: Step.Swipe? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (Int, Int, Int, Int, Long) -> Unit,
) {
    var startX by remember(initialStep) { mutableStateOf(initialStep?.startX?.toString() ?: "540") }
    var startY by remember(initialStep) { mutableStateOf(initialStep?.startY?.toString() ?: "1800") }
    var endX by remember(initialStep) { mutableStateOf(initialStep?.endX?.toString() ?: "540") }
    var endY by remember(initialStep) { mutableStateOf(initialStep?.endY?.toString() ?: "600") }
    var duration by remember(initialStep) { mutableStateOf(initialStep?.durationMillis?.toString() ?: "400") }
    val values = listOf(startX, startY, endX, endY).map { it.toIntOrNull() }
    val durationValue = duration.toLongOrNull()
    val valid = values.all { it != null && it >= 0 } && durationValue != null && durationValue in 1..10_000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swipe)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScreenshotCoordinatePicker(
                    mode = ScreenshotCoordinateMode.Swipe,
                    onSwipe = { start, end ->
                        startX = start.x.toString()
                        startY = start.y.toString()
                        endX = end.x.toString()
                        endY = end.y.toString()
                    },
                )
                NodeField(startX, { startX = it }, stringResource(R.string.swipe_start_x), true)
                NodeField(startY, { startY = it }, stringResource(R.string.swipe_start_y), true)
                NodeField(endX, { endX = it }, stringResource(R.string.swipe_end_x), true)
                NodeField(endY, { endY = it }, stringResource(R.string.swipe_end_y), true)
                NodeField(duration, { duration = it }, stringResource(R.string.duration_millis), true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onAdd(
                        requireNotNull(values[0]),
                        requireNotNull(values[1]),
                        requireNotNull(values[2]),
                        requireNotNull(values[3]),
                        requireNotNull(durationValue),
                    )
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TapDialog(
    initialStep: Step.Tap? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (Int, Int) -> Unit,
) {
    var xText by remember(initialStep) { mutableStateOf(initialStep?.x?.toString() ?: "540") }
    var yText by remember(initialStep) { mutableStateOf(initialStep?.y?.toString() ?: "1200") }
    val x = xText.toIntOrNull()
    val y = yText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tap_coordinates)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScreenshotCoordinatePicker(
                    mode = ScreenshotCoordinateMode.Tap,
                    onTap = { point ->
                        xText = point.x.toString()
                        yText = point.y.toString()
                    },
                )
                NodeField(xText, { xText = it }, stringResource(R.string.coordinate_x), true)
                NodeField(yText, { yText = it }, stringResource(R.string.coordinate_y), true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = x != null && x >= 0 && y != null && y >= 0,
                onClick = { onAdd(requireNotNull(x), requireNotNull(y)) },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecordedClickDialog(
    initialStep: Step.RecordedClick,
    onDismiss: () -> Unit,
    onSave: (RecordedClickTargetMode, Int, Int) -> Unit,
) {
    var targetMode by remember(initialStep) { mutableStateOf(initialStep.targetMode) }
    var xText by remember(initialStep) { mutableStateOf(initialStep.x.toString()) }
    var yText by remember(initialStep) { mutableStateOf(initialStep.y.toString()) }
    val x = xText.toIntOrNull()
    val y = yText.toIntOrNull()
    val control = initialStep.control
    val unavailable = stringResource(R.string.element_monitor_not_available)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recorded_click_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.recorded_click_target_mode), fontWeight = FontWeight.Bold)
                RecordedClickTargetMode.entries.forEach { option ->
                    val enabled = option != RecordedClickTargetMode.Control || initialStep.selector != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { targetMode = option }
                            .semantics { selected = targetMode == option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = targetMode == option,
                            enabled = enabled,
                            onClick = { targetMode = option },
                        )
                        Text(
                            stringResource(
                                if (option == RecordedClickTargetMode.Control) {
                                    R.string.recorded_click_target_control
                                } else {
                                    R.string.recorded_click_target_coordinates
                                },
                            ),
                        )
                    }
                }
                if (initialStep.selector == null) {
                    Text(
                        stringResource(R.string.recorded_click_control_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                NodeField(xText, { xText = it }, stringResource(R.string.coordinate_x), true)
                NodeField(yText, { yText = it }, stringResource(R.string.coordinate_y), true)
                HorizontalDivider()
                Text(stringResource(R.string.recorded_click_snapshot_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.recorded_click_snapshot_package, control.packageName))
                Text(stringResource(R.string.recorded_click_snapshot_view_id, control.viewId ?: unavailable))
                Text(stringResource(R.string.recorded_click_snapshot_text, control.text ?: unavailable))
                Text(
                    stringResource(
                        R.string.recorded_click_snapshot_description,
                        control.contentDescription ?: unavailable,
                    ),
                )
                Text(stringResource(R.string.recorded_click_snapshot_class, control.className ?: unavailable))
                Text(
                    stringResource(
                        R.string.recorded_click_snapshot_bounds,
                        control.bounds.left,
                        control.bounds.top,
                        control.bounds.right,
                        control.bounds.bottom,
                    ),
                )
                Text(
                    stringResource(
                        R.string.recorded_click_snapshot_capabilities,
                        control.clickable,
                        control.enabled,
                        control.longClickable,
                        control.scrollable,
                    ),
                )
                Text(
                    stringResource(R.string.recorded_click_privacy_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = x != null && x >= 0 && y != null && y >= 0 &&
                    (targetMode != RecordedClickTargetMode.Control || initialStep.selector != null),
                onClick = { onSave(targetMode, requireNotNull(x), requireNotNull(y)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private enum class ScreenshotCoordinateMode { Tap, Swipe }

@Composable
private fun ScreenshotCoordinatePicker(
    mode: ScreenshotCoordinateMode,
    onTap: (ScreenPoint) -> Unit = {},
    onSwipe: (ScreenPoint, ScreenPoint) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureStart by remember(captureState) { mutableStateOf<Offset?>(null) }
    var gestureEnd by remember(captureState) { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }

    Text(
        stringResource(
            if (mode == ScreenshotCoordinateMode.Tap) {
                R.string.tap_screenshot_instructions
            } else {
                R.string.swipe_screenshot_instructions
            },
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                AutomationAccessibilityService.instance?.capturePreviousApp()
            },
            enabled = AutomationAccessibilityService.instance != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                captureState !is ScreenCaptureState.Armed,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(
                    if (captureState is ScreenCaptureState.Ready) R.string.recapture
                    else R.string.capture_previous_app,
                ),
            )
        }
        if (captureState is ScreenCaptureState.Ready) {
            OutlinedButton(
                onClick = AutomationAccessibilityService::discardScreenCapture,
            ) { Text(stringResource(R.string.clear_screenshot)) }
        }
    }
    when (val state = captureState) {
        ScreenCaptureState.Armed -> Text(
            stringResource(R.string.capture_waiting),
            color = MaterialTheme.colorScheme.primary,
        )
        is ScreenCaptureState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
        is ScreenCaptureState.Ready -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .onSizeChanged { captureSize = it },
        ) {
            Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(
                    if (mode == ScreenshotCoordinateMode.Tap) {
                        R.string.tap_screenshot_description
                    } else {
                        R.string.swipe_screenshot_description
                    },
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state, captureSize, mode) {
                        if (mode == ScreenshotCoordinateMode.Tap) {
                            detectTapGestures { offset ->
                                mapCaptureOffset(state, captureSize, offset)?.let { point ->
                                    gestureStart = offset
                                    gestureEnd = null
                                    onTap(point)
                                }
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    gestureStart = offset
                                    gestureEnd = offset
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    gestureEnd = change.position
                                    val start = gestureStart?.let {
                                        mapCaptureOffset(state, captureSize, it)
                                    }
                                    val end = mapCaptureOffset(state, captureSize, change.position)
                                    if (start != null && end != null) onSwipe(start, end)
                                },
                            )
                        }
                    },
            ) {
                val start = gestureStart
                val end = gestureEnd
                if (start != null) {
                    drawCircle(Color(0xFFFFC857), radius = 7.dp.toPx(), center = start)
                    if (end != null) {
                        drawLine(
                            color = Color(0xFFFFC857),
                            start = start,
                            end = end,
                            strokeWidth = 3.dp.toPx(),
                        )
                        drawCircle(Color(0xFFD04F3D), radius = 7.dp.toPx(), center = end)
                    }
                }
            }
        }
        ScreenCaptureState.Idle -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Text(
                stringResource(R.string.capture_requires_android_11),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

private fun mapCaptureOffset(
    state: ScreenCaptureState.Ready,
    captureSize: IntSize,
    offset: Offset,
): ScreenPoint? = mapFitCenterTapToScreen(
    tapX = offset.x,
    tapY = offset.y,
    containerWidth = captureSize.width,
    containerHeight = captureSize.height,
    imageWidth = state.bitmap.width,
    imageHeight = state.bitmap.height,
)

@Composable
private fun ScrollStepDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.Scroll? = null,
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, ScrollDirection) -> Unit,
) {
    var selectedSelector by remember(initialStep) { mutableStateOf(initialStep?.selector) }
    var direction by remember(initialStep) {
        mutableStateOf(initialStep?.direction ?: ScrollDirection.Forward)
    }
    val scrollableNodes = observedNodes.filter { it.scrollable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("滚动元素") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (scrollableNodes.isEmpty()) {
                    Text(
                        "目标界面中未观察到可滚动元素。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                scrollableNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
                }
                selectedSelector?.let { selector ->
                    Text(
                        "已选择：${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (direction == ScrollDirection.Forward) "向后滚动" else "向前滚动")
                        Text(
                            if (direction == ScrollDirection.Forward) "移向后续内容" else "移向之前的内容",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = direction == ScrollDirection.Forward,
                        onCheckedChange = {
                            direction = if (it) ScrollDirection.Forward else ScrollDirection.Backward
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null,
                onClick = { onAdd(requireNotNull(selectedSelector), direction) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val number = value.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NodeField(value, { value = it }, label, true) },
        confirmButton = {
            TextButton(
                enabled = number != null && number > 0,
                onClick = { onAdd(requireNotNull(number)) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun WaitNodeDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.WaitForNode? = null,
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, Long, Boolean) -> Unit,
) {
    var selectedSelector by remember(initialStep) { mutableStateOf(initialStep?.selector) }
    var mustExist by remember(initialStep) { mutableStateOf(initialStep?.mustExist ?: true) }
    var timeout by remember(initialStep) {
        mutableStateOf(initialStep?.timeoutMillis?.toString() ?: "10000")
    }
    val timeoutValue = timeout.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("等待元素") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择最近观察到的元素", fontWeight = FontWeight.SemiBold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "打开一次目标应用，然后返回此处。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(node.displayName(), modifier = Modifier.fillMaxWidth())
                    }
                }
                selectedSelector?.let {
                    Text(
                        "已选择：${it.viewId ?: it.text ?: it.contentDescription ?: it.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (mustExist) "等待元素出现" else "等待元素消失")
                        Text(
                            if (mustExist) "出现匹配项后继续" else "没有匹配项后继续",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = mustExist, onCheckedChange = { mustExist = it })
                }
                NodeField(timeout, { timeout = it }, "超时时间（毫秒）", true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && timeoutValue != null && timeoutValue > 0,
                onClick = {
                    onAdd(requireNotNull(selectedSelector), requireNotNull(timeoutValue), mustExist)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SetVariableDialog(
    initialName: String = "",
    initialValue: Value = Value.Literal(""),
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (String, Value) -> Unit,
) {
    var variableName by remember(initialName) { mutableStateOf(initialName) }
    var useTemplate by remember(initialValue) { mutableStateOf(initialValue !is Value.Literal) }
    var value by remember(initialValue) {
        mutableStateOf(
            when (initialValue) {
                is Value.Literal -> initialValue.value
                is Value.Template -> initialValue.template
                is Value.Variable -> "${'$'}{${initialValue.name}}"
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置变量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(variableName, { variableName = it }, "变量名", true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useTemplate) "变量模板" else "固定值")
                        Text(
                            if (useTemplate) "使用 ${'$'}{orderId} 之类的占位符" else "保存此固定值",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = useTemplate, onCheckedChange = { useTemplate = it })
                }
                NodeField(value, { value = it }, if (useTemplate) "模板" else "值")
            }
        },
        confirmButton = {
            TextButton(
                enabled = variableName.isNotBlank(),
                onClick = {
                    onAdd(
                        variableName.trim(),
                        if (useTemplate) Value.Template(value) else Value.Literal(value),
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun WrapStepDialog(
    title: String,
    valueLabel: String,
    initialValue: String,
    steps: List<Step>,
    onDismiss: () -> Unit,
    onAdd: (Int, Long) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var value by remember { mutableStateOf(initialValue) }
    val count = value.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择要包装的步骤", fontWeight = FontWeight.SemiBold)
                steps.forEachIndexed { index, step ->
                    OutlinedButton(
                        onClick = { selectedIndex = index },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${index + 1}. ${step.title()}", modifier = Modifier.fillMaxWidth()) }
                }
                NodeField(value, { value = it }, valueLabel, true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIndex != null && count != null && count in 1..Step.Repeat.MAX_REPEAT_COUNT.toLong(),
                onClick = { onAdd(requireNotNull(selectedIndex), requireNotNull(count)) },
            ) { Text("包装") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConditionDialog(
    steps: List<Step>,
    onDismiss: () -> Unit,
    onAdd: (Int, String, String, ComparisonOperator) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var variableName by remember { mutableStateOf("") }
    var expectedValue by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf(ComparisonOperator.Equals) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("如果变量匹配") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(variableName, { variableName = it }, "变量名", true)
                ComparisonOperatorSelector(operator) { operator = it }
                NodeField(expectedValue, { expectedValue = it }, "预期值")
                Text("条件为真时运行此步骤", fontWeight = FontWeight.SemiBold)
                steps.forEachIndexed { index, step ->
                    OutlinedButton(
                        onClick = { selectedIndex = index },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${index + 1}. ${step.title()}", modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIndex != null && variableName.isNotBlank(),
                onClick = {
                    onAdd(requireNotNull(selectedIndex), variableName.trim(), expectedValue, operator)
                },
            ) { Text("包装") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ComparisonOperatorSelector(
    selected: ComparisonOperator,
    onSelect: (ComparisonOperator) -> Unit,
) {
    ComparisonOperator.entries.forEach { operator ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(operator) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == operator, onClick = { onSelect(operator) })
            Text(operator.displayName())
        }
    }
}

private fun ComparisonOperator.displayName(): String = when (this) {
    ComparisonOperator.Equals -> "等于"
    ComparisonOperator.NotEquals -> "不等于"
    ComparisonOperator.Contains -> "包含"
    ComparisonOperator.NotContains -> "不包含"
}

private fun SystemAction.displayName(): String = when (this) {
    SystemAction.Back -> "返回"
    SystemAction.Home -> "主屏幕"
    SystemAction.Recents -> "最近任务"
}

private fun WorkflowState.displayName(): String = when (this) {
    WorkflowState.Draft -> "草稿"
    WorkflowState.Ready -> "就绪"
}

private fun NotificationPreflightStatus.displayName(): String = when (this) {
    NotificationPreflightStatus.Granted -> "已授予"
    NotificationPreflightStatus.Denied -> "未授予"
    NotificationPreflightStatus.NotRequired -> "无需授权"
}

private fun com.aiindexfinger.model.SelectorRole.displayName(): String = when (this) {
    com.aiindexfinger.model.SelectorRole.Click -> "点击"
    com.aiindexfinger.model.SelectorRole.RecordedClick -> "录制点击"
    com.aiindexfinger.model.SelectorRole.LongClick -> "长按"
    com.aiindexfinger.model.SelectorRole.InputText -> "输入文本"
    com.aiindexfinger.model.SelectorRole.ReadNodeText -> "读取元素属性"
    com.aiindexfinger.model.SelectorRole.Scroll -> "滚动"
    com.aiindexfinger.model.SelectorRole.WaitForNode -> "等待元素"
    com.aiindexfinger.model.SelectorRole.NodeCondition -> "元素条件"
}

@Composable
private fun RunStatus.localizedName(): String = stringResource(
    when (this) {
        RunStatus.Completed -> R.string.run_status_completed
        RunStatus.Cancelled -> R.string.run_status_cancelled
        RunStatus.Failed -> R.string.run_status_failed
        RunStatus.Rejected -> R.string.run_status_rejected
    },
)

@Composable
private fun NodeConditionDialog(
    observedNodes: List<ObservedNode>,
    steps: List<Step>? = null,
    initialSelector: NodeSelector? = null,
    onDismiss: () -> Unit,
    onSave: (Int?, NodeSelector) -> Unit,
) {
    var selectedSelector by remember(initialSelector) { mutableStateOf(initialSelector) }
    var selectedStepIndex by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("如果元素存在") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择元素", fontWeight = FontWeight.SemiBold)
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
                }
                selectedSelector?.let { selector ->
                    Text(
                        "已选择：${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                steps?.let { availableSteps ->
                    Text("元素存在时运行此步骤", fontWeight = FontWeight.SemiBold)
                    availableSteps.forEachIndexed { index, step ->
                        OutlinedButton(
                            onClick = { selectedStepIndex = index },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${index + 1}. ${step.title()}", modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && (steps == null || selectedStepIndex != null),
                onClick = { onSave(selectedStepIndex, requireNotNull(selectedSelector)) },
            ) { Text(if (steps == null) "保存" else "包装") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ReadNodeTextDialog(
    observedNodes: List<ObservedNode>,
    initialSelector: NodeSelector? = null,
    initialVariableName: String = "",
    initialAttribute: NodeAttribute = NodeAttribute.TextOrDescription,
    onDismiss: () -> Unit,
    onSave: (NodeSelector, String, NodeAttribute) -> Unit,
) {
    var selectedSelector by remember(initialSelector) { mutableStateOf(initialSelector) }
    var variableName by remember(initialVariableName) { mutableStateOf(initialVariableName) }
    var attribute by remember(initialAttribute) { mutableStateOf(initialAttribute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("读取元素属性") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(variableName, { variableName = it }, "保存到变量", true)
                Text("属性", fontWeight = FontWeight.SemiBold)
                NodeAttribute.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { attribute = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = attribute == option, onClick = { attribute = option })
                        Text(option.displayName())
                    }
                }
                Text("选择元素", fontWeight = FontWeight.SemiBold)
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
                }
                selectedSelector?.let { selector ->
                    Text(
                        "已选择：${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && variableName.isNotBlank(),
                onClick = { onSave(requireNotNull(selectedSelector), variableName.trim(), attribute) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SelectorMatchModeControls(
    selector: NodeSelector?,
    onChange: (NodeSelector) -> Unit,
) {
    selector?.text?.let {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("文本包含", modifier = Modifier.weight(1f))
            Switch(
                checked = selector.textMatchMode == TextMatchMode.Contains,
                onCheckedChange = { contains ->
                    onChange(
                        selector.copy(
                            textMatchMode = if (contains) TextMatchMode.Contains else TextMatchMode.Exact,
                        ),
                    )
                },
            )
        }
    }
    selector?.contentDescription?.let {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("描述包含", modifier = Modifier.weight(1f))
            Switch(
                checked = selector.contentDescriptionMatchMode == TextMatchMode.Contains,
                onCheckedChange = { contains ->
                    onChange(
                        selector.copy(
                            contentDescriptionMatchMode = if (contains) {
                                TextMatchMode.Contains
                            } else {
                                TextMatchMode.Exact
                            },
                        ),
                    )
                },
            )
        }
    }
    selector?.let { current ->
        MatchIndexControl(current.matchIndex) { matchIndex ->
            onChange(current.copy(matchIndex = matchIndex))
        }
    }
}

@Composable
private fun MatchIndexControl(matchIndex: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 ${matchIndex + 1} 个匹配项", modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(matchIndex - 1) }, enabled = matchIndex > 0) {
            Text("-")
        }
        TextButton(
            onClick = { onChange(matchIndex + 1) },
            enabled = matchIndex + 1 < NodeSelector.MAX_MATCH_COUNT,
        ) { Text("+") }
    }
}

private fun ObservedNode.toSelector() = NodeSelector(
    packageName = packageName,
    viewId = viewId,
    text = text,
    contentDescription = contentDescription,
    className = className,
)

private fun ObservedNode.displayName(): String =
    text ?: contentDescription ?: viewId ?: className.orEmpty()

private fun NodeAttribute.displayName(): String = when (this) {
    NodeAttribute.TextOrDescription -> "文本，无文本时读取描述"
    NodeAttribute.Text -> "文本"
    NodeAttribute.ContentDescription -> "内容描述"
    NodeAttribute.ViewId -> "资源 ID"
    NodeAttribute.ClassName -> "控件类型"
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    val item = removeAt(fromIndex)
    add(toIndex, item)
}

private fun newId(): String = UUID.randomUUID().toString()

@Composable
private fun LaunchAppDialog(
    initialPackageName: String = "",
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val context = LocalContext.current
    val launchableApps = remember { LaunchableAppCatalog(context).load() }
    var packageName by remember(initialPackageName) { mutableStateOf(initialPackageName) }
    var appQuery by remember { mutableStateOf("") }
    val matchingApps = filterLaunchableApps(launchableApps, appQuery)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.launch_app)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    label = { Text(stringResource(R.string.search_installed_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    pluralStringResource(
                        R.plurals.launchable_app_count,
                        matchingApps.size,
                        matchingApps.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (matchingApps.isEmpty()) {
                    Text(
                        stringResource(
                            if (launchableApps.isEmpty()) {
                                R.string.no_launchable_apps
                            } else {
                                R.string.no_matching_apps
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        items(matchingApps, key = { it.packageName }) { app ->
                            val selected = packageName.trim() == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        role = Role.RadioButton,
                                        onClick = { packageName = app.packageName },
                                    )
                                    .semantics { this.selected = selected }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        app.packageName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.package_name),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                NodeField(
                    packageName,
                    { packageName = it },
                    stringResource(R.string.package_name),
                    true,
                )
                if (packageName.isNotBlank()) {
                    val selectedApp = launchableApps.firstOrNull { it.packageName == packageName.trim() }
                    Text(
                        selectedApp?.let {
                            stringResource(R.string.selected_launchable_app, it.label)
                        } ?: stringResource(R.string.app_package_not_launchable),
                        color = if (selectedApp != null) {
                            Color(0xFF16815F)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = packageName.isNotBlank(),
                onClick = { onAdd(packageName.trim()) },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ClickStepDialog(
    observedNodes: List<ObservedNode>,
    initialSelector: NodeSelector? = null,
    title: String = "点击元素",
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector) -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }
    var packageName by remember(initialSelector) { mutableStateOf(initialSelector?.packageName.orEmpty()) }
    var viewId by remember(initialSelector) { mutableStateOf(initialSelector?.viewId.orEmpty()) }
    var text by remember(initialSelector) { mutableStateOf(initialSelector?.text.orEmpty()) }
    var textContains by remember(initialSelector) {
        mutableStateOf(initialSelector?.textMatchMode == TextMatchMode.Contains)
    }
    var description by remember(initialSelector) {
        mutableStateOf(initialSelector?.contentDescription.orEmpty())
    }
    var descriptionContains by remember(initialSelector) {
        mutableStateOf(initialSelector?.contentDescriptionMatchMode == TextMatchMode.Contains)
    }
    var className by remember(initialSelector) { mutableStateOf(initialSelector?.className.orEmpty()) }
    var matchIndex by remember(initialSelector) { mutableStateOf(initialSelector?.matchIndex ?: 0) }
    var matchResult by remember { mutableStateOf<String?>(null) }
    val hasAttribute = listOf(viewId, text, description, className).any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VisualSelectorCapture(
                    onSelectorSelected = { selector ->
                        packageName = selector.packageName
                        viewId = selector.viewId.orEmpty()
                        text = selector.text.orEmpty()
                        textContains = false
                        description = selector.contentDescription.orEmpty()
                        descriptionContains = false
                        className = selector.className.orEmpty()
                        matchIndex = selector.matchIndex
                        matchResult = "已从截图中选择"
                    },
                    onSelectionError = { matchResult = it },
                )
                HorizontalDivider()
                Text("最近观察到的元素", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "打开一次目标应用，然后返回此处。可访问的元素会显示在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                        OutlinedButton(
                            onClick = {
                                val service = AutomationAccessibilityService.instance
                                val candidates = SelectorRecommendations.candidates(node)
                                val selector = candidates.firstOrNull { service?.countMatches(it) == 1 }
                                    ?: candidates.first()
                                packageName = selector.packageName
                                viewId = selector.viewId.orEmpty()
                                text = selector.text.orEmpty()
                                textContains = false
                                description = selector.contentDescription.orEmpty()
                                descriptionContains = false
                                className = selector.className.orEmpty()
                                matchIndex = selector.matchIndex
                                val count = service?.countMatches(selector) ?: 0
                                matchResult = if (count == 1) {
                                    "已自动选择唯一匹配项"
                                } else {
                                    "已选择稳定候选项，请在目标界面测试"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    node.text ?: node.contentDescription ?: node.viewId.orEmpty(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    node.viewId ?: node.className.orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("元素属性", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                NodeField(packageName, { packageName = it }, "包名", true)
                NodeField(viewId, { viewId = it }, "资源 ID")
                NodeField(text, { text = it }, "文本")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("文本包含", modifier = Modifier.weight(1f))
                    Switch(
                        checked = textContains,
                        enabled = text.isNotBlank(),
                        onCheckedChange = { textContains = it },
                    )
                }
                NodeField(description, { description = it }, "内容描述")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("描述包含", modifier = Modifier.weight(1f))
                    Switch(
                        checked = descriptionContains,
                        enabled = description.isNotBlank(),
                        onCheckedChange = { descriptionContains = it },
                    )
                }
                NodeField(className, { className = it }, "类名")
                MatchIndexControl(matchIndex) { matchIndex = it }
                OutlinedButton(
                    enabled = packageName.isNotBlank() && hasAttribute &&
                        AutomationAccessibilityService.instance != null,
                    onClick = {
                        val selector = nodeSelectorOrNull(
                            packageName,
                            viewId,
                            text,
                            textContains,
                            description,
                            descriptionContains,
                            className,
                            matchIndex,
                        )
                        val count = selector?.let {
                            AutomationAccessibilityService.instance?.countMatches(it)
                        } ?: 0
                        matchResult = when (count) {
                            0 -> "当前窗口中没有匹配元素"
                            in 1..matchIndex -> "仅有 $count 个匹配项，无法选择第 ${matchIndex + 1} 个"
                            1 -> "唯一匹配，选择器已就绪"
                            else -> "$count 个匹配项中可选择第 ${matchIndex + 1} 个"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("测试选择器") }
                matchResult?.let { result ->
                    Text(
                        result,
                        color = if (result.startsWith("唯一匹配")) Color(0xFF16815F) else Color(0xFFD04F3D),
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = packageName.isNotBlank() && hasAttribute,
                onClick = {
                    onAdd(
                        requireNotNull(
                            nodeSelectorOrNull(
                                packageName,
                                viewId,
                                text,
                                textContains,
                                description,
                                descriptionContains,
                                className,
                                matchIndex,
                            ),
                        ),
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ImageClickStepDialog(
    initialStep: Step.ImageClick? = null,
    confirmLabel: String = "添加",
    onDismiss: () -> Unit,
    onAdd: (Step.ImageClick) -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    var packageName by remember(initialStep) { mutableStateOf(initialStep?.packageName.orEmpty()) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(captureState) {
        val state = captureState as? ScreenCaptureState.Ready ?: return@LaunchedEffect
        if (packageName.isBlank()) packageName = state.nodes.firstOrNull()?.packageName.orEmpty()
        dragStart = null
        dragEnd = null
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_click)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.image_click_instructions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                NodeField(packageName, { packageName = it }, stringResource(R.string.image_click_package), true)
                OutlinedButton(
                    onClick = {
                        AutomationAccessibilityService.instance?.capturePreviousApp()
                    },
                    enabled = AutomationAccessibilityService.instance != null &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        captureState !is ScreenCaptureState.Armed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (captureState is ScreenCaptureState.Ready) R.string.recapture
                            else R.string.capture_previous_app,
                        ),
                    )
                }
                if (captureState is ScreenCaptureState.Ready) {
                    OutlinedButton(
                        onClick = AutomationAccessibilityService::discardScreenCapture,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.clear_screenshot)) }
                }
                when (val state = captureState) {
                    ScreenCaptureState.Armed -> Text(stringResource(R.string.capture_waiting))
                    is ScreenCaptureState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is ScreenCaptureState.Ready -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .onSizeChanged { captureSize = it },
                        ) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.image_click_capture_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(state, captureSize) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragStart = it
                                                dragEnd = it
                                                error = null
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                dragEnd = change.position
                                            },
                                        )
                                    },
                            ) {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null) {
                                    val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                                    drawRect(
                                        color = Color(0xFFFFC857),
                                        topLeft = topLeft,
                                        size = androidx.compose.ui.geometry.Size(
                                            abs(start.x - end.x),
                                            abs(start.y - end.y),
                                        ),
                                        style = Stroke(width = 3.dp.toPx()),
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.image_click_crop_hint), fontSize = 12.sp)
                    }
                    ScreenCaptureState.Idle -> initialStep?.let {
                        Text(stringResource(R.string.image_click_saved_template, it.templateWidth, it.templateHeight))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = packageName.isNotBlank() && (initialStep != null ||
                    captureState is ScreenCaptureState.Ready && dragStart != null && dragEnd != null),
                onClick = {
                    val state = captureState as? ScreenCaptureState.Ready
                    val start = dragStart
                    val end = dragEnd
                    if (state == null || start == null || end == null) {
                        initialStep?.let { onAdd(it.copy(packageName = packageName.trim())) }
                        return@TextButton
                    }
                    val crop = cropTemplate(state.bitmap, captureSize, start, end)
                    if (crop == null) {
                        error = context.getString(
                            R.string.image_click_crop_too_small,
                            Step.ImageClick.MIN_TEMPLATE_SIZE,
                        )
                    } else {
                        val encoded = encodeTemplatePng(crop)
                        crop.recycle()
                        if (encoded == null) {
                            error = context.getString(R.string.image_click_template_too_large)
                        } else {
                            onAdd(
                                Step.ImageClick(
                                    id = initialStep?.id ?: "pending",
                                    packageName = packageName.trim(),
                                    templatePngBase64 = encoded.base64,
                                    templateWidth = encoded.width,
                                    templateHeight = encoded.height,
                                    minimumScorePermille = initialStep?.minimumScorePermille ?: 920,
                                    ambiguityMarginPermille = initialStep?.ambiguityMarginPermille ?: 25,
                                    timeoutMillis = initialStep?.timeoutMillis,
                                    failurePolicy = initialStep?.failurePolicy ?: FailurePolicy.Stop,
                                ),
                            )
                        }
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun cropTemplate(bitmap: Bitmap, container: IntSize, start: Offset, end: Offset): Bitmap? {
    val first = mapFitCenterTapToScreen(
        start.x, start.y, container.width, container.height, bitmap.width, bitmap.height,
    ) ?: return null
    val second = mapFitCenterTapToScreen(
        end.x, end.y, container.width, container.height, bitmap.width, bitmap.height,
    ) ?: return null
    val left = minOf(first.x, second.x)
    val top = minOf(first.y, second.y)
    val width = abs(first.x - second.x)
    val height = abs(first.y - second.y)
    if (width < Step.ImageClick.MIN_TEMPLATE_SIZE || height < Step.ImageClick.MIN_TEMPLATE_SIZE) return null
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

private fun encodeTemplatePng(source: Bitmap): EncodedTemplate? {
    val scale = minOf(
        1f,
        Step.ImageClick.MAX_TEMPLATE_SIZE.toFloat() / maxOf(source.width, source.height),
    )
    val width = (source.width * scale).toInt().coerceAtLeast(Step.ImageClick.MIN_TEMPLATE_SIZE)
    val height = (source.height * scale).toInt().coerceAtLeast(Step.ImageClick.MIN_TEMPLATE_SIZE)
    val scaled = if (width == source.width && height == source.height) {
        source
    } else {
        Bitmap.createScaledBitmap(source, width, height, true)
    }
    return try {
        val output = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
        val bytes = output.toByteArray()
        if (bytes.size > IMAGE_TEMPLATE_MAX_PNG_BYTES) null else EncodedTemplate(
            Base64.getEncoder().encodeToString(bytes),
            width,
            height,
        )
    } finally {
        if (scaled !== source) scaled.recycle()
    }
}

private data class EncodedTemplate(val base64: String, val width: Int, val height: Int)

private const val IMAGE_TEMPLATE_MAX_PNG_BYTES = 96 * 1024

@Composable
private fun VisualSelectorCapture(
    onSelectorSelected: (NodeSelector) -> Unit,
    onSelectionError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    var captureSize by remember { mutableStateOf(IntSize.Zero) }

    Text(
        stringResource(R.string.select_from_screenshot),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(R.string.visual_selector_instructions),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    OutlinedButton(
        onClick = {
            AutomationAccessibilityService.instance?.capturePreviousApp()
        },
        enabled = AutomationAccessibilityService.instance != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            captureState !is ScreenCaptureState.Armed,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (captureState is ScreenCaptureState.Ready) R.string.recapture
                else R.string.capture_previous_app,
            ),
        )
    }
    if (captureState is ScreenCaptureState.Ready) {
        OutlinedButton(
            onClick = AutomationAccessibilityService::discardScreenCapture,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.clear_screenshot)) }
    }
    when (val state = captureState) {
        ScreenCaptureState.Armed -> Text(
            stringResource(R.string.capture_waiting),
            color = MaterialTheme.colorScheme.primary,
        )
        is ScreenCaptureState.Error -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
        is ScreenCaptureState.Ready -> {
            Text(stringResource(R.string.tap_object_to_select), fontWeight = FontWeight.SemiBold)
            Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.visual_selector_capture_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .onSizeChanged { captureSize = it }
                    .pointerInput(state, captureSize) {
                        detectTapGestures { offset ->
                            val point = mapFitCenterTapToScreen(
                                tapX = offset.x,
                                tapY = offset.y,
                                containerWidth = captureSize.width,
                                containerHeight = captureSize.height,
                                imageWidth = state.bitmap.width,
                                imageHeight = state.bitmap.height,
                            )
                            val node = point?.let { selectCaptureNode(state.nodes, it) }
                            if (node == null) {
                                onSelectionError(context.getString(R.string.no_accessible_element_at_position))
                            } else {
                                onSelectorSelected(
                                    recommendedSelector(node) {
                                        AutomationAccessibilityService.instance?.countMatches(it) ?: 0
                                    },
                                )
                            }
                        }
                    },
            )
        }
        ScreenCaptureState.Idle -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Text(
                stringResource(R.string.visual_capture_requires_android_11),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

private fun nodeSelectorOrNull(
    packageName: String,
    viewId: String,
    text: String,
    textContains: Boolean,
    description: String,
    descriptionContains: Boolean,
    className: String,
    matchIndex: Int,
): NodeSelector? {
    if (packageName.isBlank() || listOf(viewId, text, description, className).all { it.isBlank() }) return null
    return NodeSelector(
        packageName = packageName.trim(),
        viewId = viewId.trim().ifBlank { null },
        text = text.trim().ifBlank { null },
        textMatchMode = if (textContains) TextMatchMode.Contains else TextMatchMode.Exact,
        contentDescription = description.trim().ifBlank { null },
        contentDescriptionMatchMode = if (descriptionContains) {
            TextMatchMode.Contains
        } else {
            TextMatchMode.Exact
        },
        className = className.trim().ifBlank { null },
        matchIndex = matchIndex,
    )
}

@Composable
private fun NodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepRow(
    index: Int,
    step: Step,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canEdit: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onEditPolicy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOpenRepeat: (() -> Unit)? = null,
    onOpenIfTrue: (() -> Unit)? = null,
    onOpenIfFalse: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", modifier = Modifier.size(32.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(step.title(), fontWeight = FontWeight.SemiBold)
            if (step is Step.Click || step is Step.LongClick || step is Step.ReadNodeText || step is Step.Scroll) {
                val selector = when (step) {
                    is Step.Click -> step.selector
                    is Step.LongClick -> step.selector
                    is Step.ReadNodeText -> step.selector
                    is Step.Scroll -> step.selector
                    else -> error("Selector step expected")
                }
                Text(
                    selector.viewId ?: selector.text ?: selector.contentDescription
                    ?: selector.className.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Text(
                "失败时：${step.failurePolicy.label()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                step.timeoutMillis?.let { "超时：$it 毫秒" } ?: "超时：使用工作流默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Column {
            if (onOpenRepeat != null) {
                TextButton(onClick = onOpenRepeat) { Text("打开步骤") }
            }
            if (onOpenIfTrue != null || onOpenIfFalse != null) {
                Row {
                    onOpenIfTrue?.let { open ->
                        TextButton(onClick = open) { Text("为真") }
                    }
                    onOpenIfFalse?.let { open ->
                        TextButton(onClick = open) { Text("为假") }
                    }
                }
            }
            Row {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("上移") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("下移") }
            }
            Row {
                TextButton(onClick = onEdit, enabled = canEdit) { Text("编辑") }
                TextButton(onClick = onEditPolicy) { Text("设置") }
            }
            Row {
                TextButton(onClick = onDuplicate) { Text("复制") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

private fun FailurePolicy.label(): String = when (this) {
    FailurePolicy.Stop -> "停止"
    FailurePolicy.Continue -> "继续"
    is FailurePolicy.Retry -> "重试 $attempts 次"
}

private fun Step.isActionEditable(): Boolean = when (this) {
    is Step.Click, is Step.RecordedClick, is Step.Delay, is Step.GlobalAction, is Step.InputText, is Step.LaunchApp,
    is Step.ImageClick,
    is Step.LongClick, is Step.ReadNodeText, is Step.Repeat, is Step.SetVariable, is Step.Swipe,
    is Step.WaitForNode -> true
    is Step.Scroll, is Step.Tap -> true
    is Step.IfElse -> when (val current = condition) {
        is Condition.NodeExists -> true
        is Condition.Equals -> current.left is Value.Variable && current.right is Value.Literal
    }
}

@Composable
private fun Step.title(): String = when (this) {
    is Step.Click -> "点击元素"
    is Step.RecordedClick -> stringResource(
        if (targetMode == RecordedClickTargetMode.Control) {
            R.string.recorded_click_step_control
        } else {
            R.string.recorded_click_step_coordinates
        },
        control.text ?: control.contentDescription ?: control.viewId ?: control.className.orEmpty(),
        x,
        y,
    )
    is Step.ImageClick -> stringResource(R.string.image_click_step_title, templateWidth, templateHeight)
    is Step.Delay -> "等待 $durationMillis 毫秒"
    is Step.GlobalAction -> action.displayName()
    is Step.IfElse -> when (val current = condition) {
        is Condition.Equals -> "如果变量${current.operator.displayName()}"
        is Condition.NodeExists -> "如果元素存在"
    }
    is Step.InputText -> {
        val source = variableName?.let { "变量 $it" } ?: "文本"
        if (inputMethod == TextInputMethod.Paste) "粘贴$source" else "输入$source"
    }
    is Step.Repeat -> "重复 $times 次"
    is Step.Scroll -> if (direction == ScrollDirection.Forward) "向后滚动" else "向前滚动"
    is Step.LaunchApp -> "启动 $packageName"
    is Step.LongClick -> "长按元素"
    is Step.ReadNodeText -> "读取${attribute.displayName()}到 $variableName"
    is Step.SetVariable -> "设置变量 $name"
    is Step.Swipe -> "从 ($startX, $startY) 滑动到 ($endX, $endY)"
    is Step.Tap -> "点击 ($x, $y)"
    is Step.WaitForNode -> if (mustExist) "等待元素出现" else "等待元素消失"
}

private fun List<Step>.findById(stepId: String): Step? {
    for (step in this) {
        if (step.id == stepId) return step
        val nested = when (step) {
            is Step.IfElse -> (step.whenTrue + step.whenFalse).findById(stepId)
            is Step.Repeat -> step.steps.findById(stepId)
            else -> null
        }
        if (nested != null) return nested
    }
    return null
}

internal fun matchesRecordingDestination(recordingWorkflowId: String, editorWorkflowId: String): Boolean =
    recordingWorkflowId == editorWorkflowId

@Composable
private fun RunningWorkflowStatus(
    workflowName: String,
    stepName: String?,
    elapsedMillis: Long,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("正在运行 $workflowName", fontWeight = FontWeight.SemiBold)
                Text(
                    stepName?.let { "当前步骤：$it" } ?: "正在准备第一个步骤",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
                Text(
                    "已用时 ${formatElapsed(elapsedMillis)}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("停止") }
        }
    }
}

private fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun AccessibilityDisclosureDialog(
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("允许自动化访问？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "启用后，AI Index Finger 会读取屏幕上可见的文本、描述、视图标识和应用名称，" +
                        "以便你检查元素并运行自己创建的工作流。",
                )
                Text(
                    stringResource(R.string.accessibility_disclosure_screenshot),
                )
                Text(
                    "运行工作流时，应用可以点击、滑动、输入文本、使用系统导航，" +
                        "还可临时替换剪贴板内容进行粘贴；若剪贴板未再次变化，之后会恢复原内容。",
                )
                Text(
                    "观察到的界面数据和运行历史仅保存在此设备上。应用没有网络权限，" +
                        "不会向外发送这些数据。你可以随时在 Android 设置中关闭此服务。",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("继续前往设置") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("暂不") }
        },
    )
}

@Composable
private fun PermissionStatus(
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onReviewDisclosure: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(10.dp).background(
                if (connected) Color(0xFF16815F) else Color(0xFFD04F3D),
                CircleShape,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (connected) "自动化服务已就绪" else "自动化服务未开启",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (connected) "可以查找元素并执行操作"
                else "请先启用服务，再测试或运行工作流",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!connected) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) { Text("设置") }
            }
            TextButton(onClick = onReviewDisclosure) { Text("详情") }
        }
    }
}


@Composable
private fun WorkflowRow(
    workflow: Workflow,
    isRunning: Boolean,
    canCompare: Boolean,
    schedule: WorkflowSchedule?,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    moveTag: String,
    onCompare: () -> Unit,
        onViewVersions: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onRun: () -> Unit,
    onPreflight: () -> Unit,
    onStop: () -> Unit,
) {
    val isReady = workflow.isReadyToRun()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(workflow.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${workflow.effectiveState().displayName()} · ${workflow.steps.size} 个步骤",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            schedule?.let {
                Text(
                    stringResource(
                        R.string.schedule_summary,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(it.scheduledAtMillis)),
                        it.recurrence.localizedLabel(),
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row {
                TextButton(onClick = onEdit, enabled = !isRunning) { Text("编辑") }
                Button(
                    onClick = if (isRunning) onStop else onRun,
                    enabled = isRunning || isReady,
                ) {
                    Text(if (isRunning) "停止" else "运行")
                }
            }
            Row {
                TextButton(onClick = onExport, enabled = !isRunning) { Text("导出") }
                TextButton(onClick = onDuplicate, enabled = !isRunning) { Text("复制") }
                TextButton(
                    onClick = onMove,
                    enabled = !isRunning,
                    modifier = Modifier.testTag(moveTag),
                ) {
                    Text(stringResource(R.string.move_to_folder))
                }
                TextButton(onClick = onDelete, enabled = !isRunning) { Text("删除") }
            }
            TextButton(onClick = onCompare, enabled = !isRunning && canCompare) {
                Text(stringResource(R.string.compare_workflow))
            }
            TextButton(onClick = onViewVersions, enabled = !isRunning) {
                Text(stringResource(R.string.workflow_version_history))
            }
            TextButton(onClick = onPreflight, enabled = !isRunning) { Text("检查") }
            TextButton(
                onClick = if (schedule == null) onSchedule else onCancelSchedule,
                enabled = !isRunning && (schedule != null || isReady),
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (schedule == null) "计划" else "取消计划") }
        }
    }
}

internal const val FOLDER_MANAGE_TAG = "folder-manage"
internal const val SETTINGS_PACK_INSTALL_TAG = "settings-pack-install"
internal const val FOLDER_CREATE_TAG = "folder-create"
internal const val FOLDER_NAME_INPUT_TAG = "folder-name-input"
internal const val FOLDER_NAME_SAVE_TAG = "folder-name-save"
internal const val FOLDER_DELETE_CONFIRM_TAG = "folder-delete-confirm"
internal const val FOLDER_FILTER_ALL_TAG = "folder-filter-all"
internal const val FOLDER_FILTER_UNFILED_TAG = "folder-filter-unfiled"
internal const val FOLDER_DESTINATION_UNFILED_TAG = "folder-destination-unfiled"
internal fun folderFilterTag(folderId: String) = "folder-filter-$folderId"
internal fun folderRenameTag(folderId: String) = "folder-rename-$folderId"
internal fun folderDeleteTag(folderId: String) = "folder-delete-$folderId"
internal fun folderMoveWorkflowTag(workflowId: String) = "folder-move-workflow-$workflowId"
internal fun folderDestinationTag(folderId: String) = "folder-destination-$folderId"

@Composable
private fun ScheduleRecurrence.localizedLabel(): String = stringResource(
    when (this) {
        ScheduleRecurrence.Once -> R.string.schedule_recurrence_once
        ScheduleRecurrence.Daily -> R.string.schedule_recurrence_daily
        ScheduleRecurrence.Weekly -> R.string.schedule_recurrence_weekly
    },
)

@Composable
private fun WorkflowVersionHistoryDialog(
    current: Workflow,
    versions: List<WorkflowVersion>,
    onDismiss: () -> Unit,
    onPreview: (WorkflowVersion) -> Unit,
    onRollback: (WorkflowVersion) -> Unit,
) {
    var pendingRollback by remember(current.id) { mutableStateOf<WorkflowVersion?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_version_history_title, current.name)) },
        text = {
            if (versions.isEmpty()) {
                Text(stringResource(R.string.workflow_version_history_empty))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    versions.forEach { version ->
                        Column {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                                    .format(Date(version.createdAtEpochMillis)),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.workflow_version_name, version.workflow.name),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Row {
                                TextButton(onClick = { onPreview(version) }) {
                                    Text(stringResource(R.string.preview_changes))
                                }
                                TextButton(onClick = { pendingRollback = version }) {
                                    Text(stringResource(R.string.rollback))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
    pendingRollback?.let { version ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text(stringResource(R.string.confirm_rollback_title)) },
            text = { Text(stringResource(R.string.confirm_rollback_message, version.workflow.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRollback = null
                        onRollback(version)
                    },
                ) { Text(stringResource(R.string.rollback)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WorkflowComparisonScreen(
    before: Workflow,
    after: Workflow,
    onBack: () -> Unit,
) {
    val comparison = remember(before, after) { compareWorkflows(before, after) }
    BackHandler(onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                stringResource(R.string.workflow_comparison),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.workflow_comparison_names, before.name, after.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (comparison.isIdentical) {
                Text(
                    stringResource(R.string.workflows_identical),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.workflow_difference_count, comparison.differences.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(comparison.differences) { difference ->
                        WorkflowDifferenceRow(difference)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowDifferenceRow(difference: WorkflowDifference) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (difference) {
            is WorkflowDifference.MetadataChanged -> Text(
                stringResource(R.string.workflow_metadata_changed, difference.field.localizedName()),
                fontWeight = FontWeight.SemiBold,
            )
            is WorkflowDifference.StepAdded -> {
                Text(difference.path.localizedName(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.workflow_step_added, difference.stepType.localizedStepType()),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            is WorkflowDifference.StepRemoved -> {
                Text(difference.path.localizedName(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.workflow_step_removed, difference.stepType.localizedStepType()),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            is WorkflowDifference.StepChanged -> {
                Text(difference.path.localizedName(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (difference.field) {
                        StepComparisonField.Type -> stringResource(
                            R.string.workflow_step_type_changed,
                            difference.beforeStepType.localizedStepType(),
                            difference.afterStepType.localizedStepType(),
                        )
                        StepComparisonField.Configuration -> stringResource(
                            R.string.workflow_step_configuration_changed,
                            difference.beforeStepType.localizedStepType(),
                        )
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StepComparisonPath.localizedName(): String {
    val segments = buildList {
        parent?.let { add(it.localizedName()) }
        branch?.let { add(it.localizedName()) }
        add(stringResource(R.string.workflow_step_position, index + 1))
    }
    return segments.joinToString(" › ")
}

@Composable
private fun StepComparisonBranch.localizedName(): String = stringResource(
    when (this) {
        StepComparisonBranch.Repeat -> R.string.workflow_branch_repeat
        StepComparisonBranch.WhenTrue -> R.string.workflow_branch_when_true
        StepComparisonBranch.WhenFalse -> R.string.workflow_branch_when_false
    },
)

@Composable
private fun WorkflowMetadataField.localizedName(): String = stringResource(
    when (this) {
        WorkflowMetadataField.Name -> R.string.workflow_metadata_name
        WorkflowMetadataField.State -> R.string.workflow_metadata_state
        WorkflowMetadataField.DefaultStepTimeout -> R.string.workflow_metadata_timeout
    },
)

@Composable
private fun String.localizedStepType(): String = stringResource(
    when (this) {
        "launch_app" -> R.string.workflow_step_type_launch_app
        "click" -> R.string.workflow_step_type_click
        "image_click" -> R.string.workflow_step_type_image_click
        "long_click" -> R.string.workflow_step_type_long_click
        "input_text" -> R.string.workflow_step_type_input_text
        "read_node_text" -> R.string.workflow_step_type_read_node_text
        "swipe" -> R.string.workflow_step_type_swipe
        "scroll" -> R.string.workflow_step_type_scroll
        "tap" -> R.string.workflow_step_type_tap
        "global_action" -> R.string.workflow_step_type_global_action
        "wait_for_node" -> R.string.workflow_step_type_wait_for_node
        "delay" -> R.string.workflow_step_type_delay
        "set_variable" -> R.string.workflow_step_type_set_variable
        "if_else" -> R.string.workflow_step_type_if_else
        "repeat" -> R.string.workflow_step_type_repeat
        else -> R.string.workflow_step_type_unknown
    },
)

private fun Workflow.exportFileName(): String {
    val safeName = name
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(60)
        .ifBlank { "workflow" }
    return "$safeName.aiflow.json"
}

@Composable
private fun PreflightReportDialog(
    workflow: Workflow,
    report: WorkflowPreflightReport,
    onDismiss: () -> Unit,
    onEditStep: (StepPath) -> Unit,
    onRecoveryAction: (PreflightRecoveryAction) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_test_title, workflow.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.workflow_test_state, report.state.displayName()),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(
                        R.string.workflow_test_automation,
                        stringResource(
                            if (report.accessibilityConnected) R.string.status_connected
                            else R.string.status_not_connected,
                        ),
                    ),
                )
                Text(
                    stringResource(
                        R.string.workflow_test_notifications,
                        report.notificationStatus.displayName(),
                    ),
                )
                if (report.requiresImageCapture) {
                    Text(
                        stringResource(
                            if (report.imageCaptureSupported) R.string.image_click_preflight_supported
                            else R.string.image_click_preflight_unsupported,
                        ),
                        color = if (report.imageCaptureSupported) Color.Unspecified else MaterialTheme.colorScheme.error,
                    )
                }
                report.recoveryActions().forEach { action ->
                    OutlinedButton(
                        onClick = { onRecoveryAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (action) {
                                PreflightRecoveryAction.SetUpAutomation -> stringResource(
                                    R.string.set_up_automation,
                                )
                            },
                        )
                    }
                }
                if (report.validationIssues.isEmpty()) {
                    Text(stringResource(R.string.workflow_test_structure_valid), color = Color(0xFF16815F))
                } else {
                    Text(
                        stringResource(R.string.workflow_test_structure_issues, report.validationIssues.size),
                        color = Color(0xFFD04F3D),
                    )
                    report.validationIssues.forEach { issue ->
                        val location = issue.stepId
                            ?.let(workflow.steps::runLocationsTo)
                            ?.takeIf(List<RunStepLocation>::isNotEmpty)
                            ?.map { it.localizedName() }
                            ?.joinToString()
                            ?: stringResource(R.string.workflow_test_whole_workflow)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.workflow_test_issue_row,
                                    location,
                                    issue.localizedMessage(context),
                                ),
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                            )
                            issue.stepId?.let(workflow.steps::uniquePathTo)?.let { path ->
                                TextButton(onClick = { onEditStep(path) }) {
                                    Text(stringResource(R.string.edit_step))
                                }
                            }
                        }
                    }
                }
                val validation = report.validation
                Text(
                    stringResource(
                        R.string.workflow_test_variables,
                        validation.definedVariables.size,
                        validation.referencedVariables.size,
                    ),
                    fontSize = 12.sp,
                )
                if (validation.definedVariables.isNotEmpty()) {
                    Text(validation.definedVariables.joinToString(), fontSize = 12.sp)
                }
                Text(
                    stringResource(
                        R.string.workflow_test_limits,
                        validation.definedStepCount,
                        WorkflowLimits.MAX_DEFINED_STEPS,
                        validation.maximumNestingDepth,
                        WorkflowLimits.MAX_NESTING_DEPTH,
                        validation.maximumStepExecutions,
                        WorkflowLimits.MAX_EXECUTED_STEPS,
                    ),
                    fontSize = 12.sp,
                )
                if (report.launchTargets.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.workflow_test_launch_targets), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    report.launchTargets.forEach { target ->
                        Text(
                            stringResource(
                                if (target.isLaunchable) R.string.workflow_test_target_available
                                else R.string.workflow_test_target_unavailable,
                                target.packageName,
                            ),
                            fontSize = 12.sp,
                        )
                    }
                }
                if (report.selectors.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.workflow_test_selector_snapshot), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    report.selectors.forEach { check ->
                        val result = when (check.requiredMatchAvailable) {
                            true -> stringResource(R.string.workflow_test_selector_available, check.matchCount ?: 0)
                            false -> stringResource(
                                R.string.workflow_test_selector_index_missing,
                                check.matchCount ?: 0,
                                check.use.selector.matchIndex,
                            )
                            null -> stringResource(R.string.workflow_test_selector_not_checked)
                        }
                        val location = workflow.steps.uniqueRunLocationTo(check.use.stepId)?.localizedName()
                            ?: check.use.stepId
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.workflow_test_selector_row,
                                    location,
                                    check.use.role.displayName(),
                                    result,
                                ),
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                            )
                            workflow.steps.uniquePathTo(check.use.stepId)?.let { path ->
                                TextButton(onClick = { onEditStep(path) }) {
                                    Text(stringResource(R.string.edit_step))
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.workflow_test_selector_scope_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RunHistoryScreen(
    records: List<RunRecord>,
    historyCorrupt: Boolean,
    workflows: List<Workflow>,
    onBack: () -> Unit,
    onOpenWorkflow: (Workflow, StepPath?) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<RunStatus?>(null) }
    var selectedRecord by remember { mutableStateOf<RunRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val visibleRecords = filterRunRecords(records, query, status)

    BackHandler(onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.run_history),
                    modifier = Modifier.weight(1f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (records.isNotEmpty() || historyCorrupt) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear)) }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.run_history_filter_workflow)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.run_history_status), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf<RunStatus?>(null, RunStatus.Completed, RunStatus.Failed)
                .chunked(3)
                .plus(listOf(listOf(RunStatus.Cancelled, RunStatus.Rejected)))
                .forEach { options ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        options.forEach { option ->
                            RadioButton(selected = status == option, onClick = { status = option })
                            Text(
                                option?.localizedName() ?: stringResource(R.string.run_history_status_all),
                                modifier = Modifier.clickable { status = option },
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            Text(
                stringResource(R.string.run_history_count, records.size, visibleRecords.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            HorizontalDivider()
            when {
                records.isEmpty() -> Text(
                    stringResource(R.string.no_run_records),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                visibleRecords.isEmpty() -> Text(
                    stringResource(R.string.run_history_no_matches),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleRecords, key = { it.id }) { record ->
                        Box(Modifier.clickable { selectedRecord = record }) {
                            RunRecordRow(record)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedRecord?.let { record ->
        val destination = resolveRunHistoryDestination(record, workflows)
        RunRecordDetailsDialog(
            record = record,
            destination = destination,
            onDismiss = { selectedRecord = null },
            onOpenWorkflow = { workflow, stepPath ->
                selectedRecord = null
                onOpenWorkflow(workflow, stepPath)
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.run_history_clear_title)) },
            text = { Text(stringResource(R.string.run_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedRecord = null
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RunRecordDetailsDialog(
    record: RunRecord,
    destination: com.aiindexfinger.data.RunHistoryDestination?,
    onDismiss: () -> Unit,
    onOpenWorkflow: (Workflow, StepPath?) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.workflowName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.run_history_detail_status, record.status.localizedName()))
                Text(stringResource(R.string.run_history_detail_workflow_id, record.workflowId))
                Text(
                    stringResource(
                        R.string.run_history_detail_started,
                        DateFormat.getDateTimeInstance().format(Date(record.startedAtMillis)),
                    ),
                )
                Text(stringResource(R.string.run_history_detail_duration, record.durationMillis))
                record.failedStepId?.let {
                    Text(
                        stringResource(
                            R.string.run_history_detail_failed_step,
                            record.failedStepLocation?.localizedName() ?: it,
                        ),
                    )
                }
                record.localizedFailureMessage(context)?.let {
                    Text(stringResource(R.string.run_failure_details, it))
                }
                if (record.diagnostics.isNotEmpty()) {
                    Text(
                        stringResource(R.string.execution_diagnostics),
                        fontWeight = FontWeight.SemiBold,
                    )
                    record.diagnostics.sortedBy { it.sequence }.forEach { diagnostic ->
                        Text(
                            stringResource(
                                R.string.execution_diagnostic_row,
                                diagnostic.location?.localizedName() ?: diagnostic.stepId,
                                diagnostic.outcome.localizedName(),
                                diagnostic.durationMillis,
                                diagnostic.attemptCount,
                            ),
                            fontSize = 12.sp,
                        )
                    }
                }
                if (destination == null) {
                    Text(
                        stringResource(R.string.run_history_workflow_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (record.failedStepId != null && destination.stepPath == null) {
                    Text(
                        stringResource(R.string.run_history_step_missing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            destination?.let {
                TextButton(onClick = { onOpenWorkflow(it.workflow, it.stepPath) }) {
                    Text(
                        stringResource(
                            if (it.stepPath == null) R.string.run_history_open_workflow
                            else R.string.run_history_edit_failed_step,
                        ),
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RunStepOutcome.localizedName(): String = stringResource(
    when (this) {
        RunStepOutcome.Completed -> R.string.execution_outcome_completed
        RunStepOutcome.ContinuedAfterFailure -> R.string.execution_outcome_continued
        RunStepOutcome.Failed -> R.string.execution_outcome_failed
        RunStepOutcome.Cancelled -> R.string.execution_outcome_cancelled
    },
)

@Composable
private fun RunRecordRow(record: RunRecord) {
    val context = LocalContext.current
    val failureMessage = record.localizedFailureMessage(context)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(record.workflowName, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.run_history_row_metadata,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(record.startedAtMillis)),
                    record.durationMillis,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            if (failureMessage != null) {
                Text(
                    record.failedStepId?.let {
                        context.getString(
                            R.string.run_failure_with_step,
                            record.failedStepLocation?.localizedName(context) ?: it,
                            failureMessage,
                        )
                    } ?: failureMessage,
                    color = Color(0xFFD04F3D),
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            record.status.localizedName(),
            color = if (record.status == RunStatus.Completed) Color(0xFF16815F) else Color(0xFFD04F3D),
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun RunRecord.localizedFailureMessage(context: Context): String? {
    val storedCode = failureCode ?: return failureMessage
    val separator = storedCode.indexOf('.')
    if (separator <= 0 || separator == storedCode.lastIndex) {
        return context.getString(R.string.run_failure_unknown)
    }
    val namespace = storedCode.substring(0, separator)
    val codeName = storedCode.substring(separator + 1)
    return runCatching {
        when (namespace) {
            "execution" -> ExecutionErrorCode.entries.firstOrNull { it.name == codeName }
                ?.let { ExecutionError(it, failureArguments).localizedMessage(context) }
            "validation" -> ValidationIssueCode.entries.firstOrNull { it.name == codeName }
                ?.let { ValidationIssue(null, it, failureArguments).localizedMessage(context) }
            else -> null
        }
    }.getOrNull() ?: context.getString(R.string.run_failure_unknown)
}

private fun RunResult.localizedMessage(
    context: Context,
    failedStepLocation: RunStepLocation? = null,
): String = when (this) {
    RunResult.Completed -> context.getString(R.string.run_completed)
    RunResult.AlreadyRunning -> context.getString(R.string.run_already_running)
    is RunResult.NotReady -> context.getString(R.string.cannot_run, issue.localizedMessage(context))
    RunResult.Cancelled -> context.getString(R.string.run_cancelled)
    is RunResult.Failed -> context.getString(
        R.string.run_step_failed,
        failedStepLocation?.localizedName(context) ?: stepId,
        error.localizedMessage(context),
    )
}

@Composable
private fun RunStepLocation.localizedName(): String = segments.flatMap { segment ->
    buildList {
        add(stringResource(R.string.workflow_step_position, segment.index + 1))
        segment.branch?.let { add(it.localizedName()) }
    }
}.joinToString(" › ")

private fun RunStepLocation.localizedName(context: Context): String = segments.flatMap { segment ->
    buildList {
        add(context.getString(R.string.workflow_step_position, segment.index + 1))
        segment.branch?.let { branch ->
            add(
                context.getString(
                    when (branch) {
                        RunStepBranch.RepeatBody -> R.string.workflow_branch_repeat
                        RunStepBranch.IfTrue -> R.string.workflow_branch_when_true
                        RunStepBranch.IfFalse -> R.string.workflow_branch_when_false
                    },
                ),
            )
        }
    }
}.joinToString(" › ")

@Composable
private fun RunStepBranch.localizedName(): String = stringResource(
    when (this) {
        RunStepBranch.RepeatBody -> R.string.workflow_branch_repeat
        RunStepBranch.IfTrue -> R.string.workflow_branch_when_true
        RunStepBranch.IfFalse -> R.string.workflow_branch_when_false
    },
)

private fun ExecutionError.localizedMessage(context: Context): String = when (code) {
    ExecutionErrorCode.StepFailed -> context.getString(R.string.execution_error_step_failed)
    ExecutionErrorCode.StepTimedOut -> context.getString(R.string.execution_error_step_timed_out)
    ExecutionErrorCode.ExecutionLimitExceeded -> context.getString(
        R.string.execution_error_limit_exceeded,
        arguments.getValue("limit").toLong(),
    )
    ExecutionErrorCode.TargetNotClickable -> context.getString(R.string.execution_error_target_not_clickable)
    ExecutionErrorCode.ImageClickUnsupported -> context.getString(R.string.execution_error_image_unsupported)
    ExecutionErrorCode.ImageClickWrongPackage -> context.getString(R.string.execution_error_image_wrong_package)
    ExecutionErrorCode.ImageTemplateInvalid -> context.getString(R.string.execution_error_image_template_invalid)
    ExecutionErrorCode.ImageTemplateNotFound -> context.getString(R.string.execution_error_image_not_found)
    ExecutionErrorCode.ImageTemplateAmbiguous -> context.getString(R.string.execution_error_image_ambiguous)
    ExecutionErrorCode.ScreenCaptureFailed -> context.getString(R.string.execution_error_capture_failed)
    ExecutionErrorCode.ImageGestureFailed -> context.getString(R.string.execution_error_image_gesture_failed)
    ExecutionErrorCode.SystemActionFailed -> context.getString(R.string.execution_error_system_action_failed)
    ExecutionErrorCode.TargetNotScrollable -> context.getString(R.string.execution_error_target_not_scrollable)
    ExecutionErrorCode.AppLaunchFailed -> context.getString(R.string.execution_error_app_launch_failed)
    ExecutionErrorCode.TargetNotLongClickable -> context.getString(R.string.execution_error_target_not_long_clickable)
    ExecutionErrorCode.UndefinedVariable -> context.getString(
        R.string.execution_error_undefined_variable,
        arguments.getValue("variableName"),
    )
    ExecutionErrorCode.TextInputFailed -> context.getString(R.string.execution_error_text_input_failed)
    ExecutionErrorCode.MissingNodeAttribute -> context.getString(
        R.string.execution_error_missing_node_attribute,
        nodeAttributeName(context, arguments.getValue("attribute")),
    )
    ExecutionErrorCode.SwipeFailed -> context.getString(R.string.execution_error_swipe_failed)
    ExecutionErrorCode.TapFailed -> context.getString(R.string.execution_error_tap_failed)
}

private fun nodeAttributeName(context: Context, attribute: String): String = context.getString(
    when (NodeAttribute.valueOf(attribute)) {
        NodeAttribute.TextOrDescription -> R.string.node_attribute_text_or_description
        NodeAttribute.Text -> R.string.node_attribute_text
        NodeAttribute.ContentDescription -> R.string.node_attribute_content_description
        NodeAttribute.ViewId -> R.string.node_attribute_view_id
        NodeAttribute.ClassName -> R.string.node_attribute_class_name
    },
)

private fun ValidationIssue.localizedMessage(context: Context): String = when (code) {
    ValidationIssueCode.EmptyWorkflow -> context.getString(R.string.validation_empty_workflow)
    ValidationIssueCode.ExecutionLimitExceeded -> context.getString(
        R.string.validation_execution_limit_exceeded,
        arguments.getValue("limit").toLong(),
    )
    ValidationIssueCode.NestingLimitExceeded -> context.getString(
        R.string.validation_nesting_limit_exceeded,
        arguments.getValue("limit").toInt(),
    )
    ValidationIssueCode.DefinedStepLimitExceeded -> context.getString(
        R.string.validation_defined_step_limit_exceeded,
        arguments.getValue("limit").toInt(),
    )
    ValidationIssueCode.BlankStepId -> context.getString(R.string.validation_blank_step_id)
    ValidationIssueCode.DuplicateStepId -> context.getString(R.string.validation_duplicate_step_id)
    ValidationIssueCode.NonPositiveTimeout -> context.getString(R.string.validation_non_positive_timeout)
    ValidationIssueCode.BlankVariableName -> context.getString(R.string.validation_blank_variable_name)
    ValidationIssueCode.UndefinedVariable -> context.getString(
        R.string.validation_undefined_variable,
        arguments.getValue("variableName"),
    )
    ValidationIssueCode.NegativeDelay -> context.getString(R.string.validation_negative_delay)
    ValidationIssueCode.DraftWorkflow -> context.getString(R.string.validation_draft_workflow)
}

@Composable
private fun WorkflowExampleCatalogDialog(
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onSelectExample: (WorkflowExample) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<WorkflowExampleCategory?>(null) }
    val localizedCategories = WorkflowExampleCategory.entries.associateWith { it.localizedName() }
    val localizedExamples = WorkflowStarterTemplates.catalog.map { example ->
        SearchableWorkflowExample(
            example = example,
            localizedTitle = localizedResource(example.titleResourceKey),
            localizedDescription = localizedResource(example.descriptionResourceKey),
            localizedCategory = localizedCategories.getValue(example.category),
        )
    }
    val visibleExamples = filterWorkflowExamples(localizedExamples, query, selectedCategory)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_example_catalog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onCreateBlank, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.blank_workflow), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.blank_workflow_description), fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.workflow_example_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryFilterButton(
                        label = stringResource(R.string.workflow_example_category_all),
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                    )
                    WorkflowExampleCategory.entries.forEach { category ->
                        CategoryFilterButton(
                            label = localizedCategories.getValue(category),
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                        )
                    }
                }
                Text(
                    stringResource(R.string.workflow_example_result_count, visibleExamples.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (visibleExamples.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.workflow_example_no_results))
                        TextButton(onClick = {
                            query = ""
                            selectedCategory = null
                        }) { Text(stringResource(R.string.workflow_example_clear_filters)) }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(visibleExamples, key = { it.example.id }) { localized ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onSelectExample(localized.example) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(localized.localizedTitle, fontWeight = FontWeight.SemiBold)
                                Text(
                                    localized.localizedCategory,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    localized.localizedDescription,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CategoryFilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun WorkflowExampleDetailsDialog(
    example: WorkflowExample,
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
) {
    val title = localizedResource(example.titleResourceKey)
    val description = localizedResource(example.descriptionResourceKey)
    val capabilities = example.compatibility.requiredCapabilities
        .sortedBy(WorkflowExampleCapability::ordinal)
        .map { capability -> capability.localizedName() }
        .joinToString(stringResource(R.string.workflow_example_capability_separator))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_example_details_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(example.category.localizedName(), color = MaterialTheme.colorScheme.primary)
                Text(description)
                Text(
                    stringResource(
                        if (example.compatibility.requiresConfiguration) {
                            R.string.workflow_example_requires_configuration
                        } else {
                            R.string.workflow_example_ready_to_edit
                        },
                    ),
                    color = if (example.compatibility.requiresConfiguration) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(stringResource(R.string.workflow_example_capabilities, capabilities))
                Text(
                    stringResource(
                        R.string.workflow_example_compatibility,
                        example.compatibility.minimumSchemaVersion,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onUse(title) }) { Text(stringResource(R.string.workflow_example_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun localizedResource(resourceKey: String): String {
    val context = LocalContext.current
    val resourceId = remember(resourceKey) {
        context.resources.getIdentifier(resourceKey, "string", context.packageName)
    }
    check(resourceId != 0) { "Missing string resource: $resourceKey" }
    return stringResource(resourceId)
}

@Composable
private fun WorkflowExampleCategory.localizedName(): String = stringResource(
    when (this) {
        WorkflowExampleCategory.Fundamentals -> R.string.workflow_example_category_fundamentals
        WorkflowExampleCategory.Navigation -> R.string.workflow_example_category_navigation
        WorkflowExampleCategory.Repetition -> R.string.workflow_example_category_repetition
        WorkflowExampleCategory.Variables -> R.string.workflow_example_category_variables
        WorkflowExampleCategory.Decisions -> R.string.workflow_example_category_decisions
        WorkflowExampleCategory.Timing -> R.string.workflow_example_category_timing
        WorkflowExampleCategory.Resilience -> R.string.workflow_example_category_resilience
        WorkflowExampleCategory.Gestures -> R.string.workflow_example_category_gestures
        WorkflowExampleCategory.AppObservation -> R.string.workflow_example_category_app_observation
        WorkflowExampleCategory.TextReading -> R.string.workflow_example_category_text_reading
    },
)

@Composable
private fun WorkflowExampleCapability.localizedName(): String = stringResource(
    when (this) {
        WorkflowExampleCapability.Delay -> R.string.workflow_example_capability_delay
        WorkflowExampleCapability.GlobalNavigation -> R.string.workflow_example_capability_global_navigation
        WorkflowExampleCapability.Variables -> R.string.workflow_example_capability_variables
        WorkflowExampleCapability.Conditions -> R.string.workflow_example_capability_conditions
        WorkflowExampleCapability.Loops -> R.string.workflow_example_capability_loops
        WorkflowExampleCapability.Gestures -> R.string.workflow_example_capability_gestures
        WorkflowExampleCapability.AppSelectors -> R.string.workflow_example_capability_app_selectors
        WorkflowExampleCapability.NodeReading -> R.string.workflow_example_capability_node_reading
    },
)

private const val VISIBLE_RUN_RECORDS = 10

@Composable
private fun AiIndexFingerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF116B56),
            secondary = Color(0xFF3C6257),
            background = Color(0xFFF4F6F1),
            surface = Color.White,
            onSurface = Color(0xFF18201D),
            onSurfaceVariant = Color(0xFF5B6863),
        ),
        content = content,
    )
}

private const val MAX_VISIBLE_OBSERVED_NODES = 8