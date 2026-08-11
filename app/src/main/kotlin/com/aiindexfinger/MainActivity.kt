package com.aiindexfinger

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
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
import com.aiindexfinger.automation.applyLiveActionHandoff
import com.aiindexfinger.automation.cropBoundsOrNull
import com.aiindexfinger.automation.cropTemplate
import com.aiindexfinger.automation.encodeTemplatePng
import com.aiindexfinger.automation.decodeImageTemplate
import com.aiindexfinger.automation.filterLaunchableApps
import com.aiindexfinger.automation.ObservedNode
import com.aiindexfinger.automation.SelectorRecommendations
import com.aiindexfinger.automation.ScreenCaptureState
import com.aiindexfinger.automation.ScreenPoint
import com.aiindexfinger.automation.mapFitCenterTapToScreen
import com.aiindexfinger.automation.recommendedSelector
import com.aiindexfinger.automation.selectCaptureNode
import com.aiindexfinger.automation.PendingOverlayAction
import com.aiindexfinger.automation.PreflightRecoveryAction
import com.aiindexfinger.automation.WorkflowPreflightReport
import com.aiindexfinger.automation.buildWorkflowPreflightReport
import com.aiindexfinger.automation.recoveryActions
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.clearRunHistory
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
import com.aiindexfinger.data.readAndCommitImportedLibrary
import com.aiindexfinger.data.WorkflowFolder
import com.aiindexfinger.data.WorkflowFolderSelection
import com.aiindexfinger.data.filterWorkflows
import com.aiindexfinger.data.SettingsWorkflowPack
import com.aiindexfinger.data.AppPreferences
import com.aiindexfinger.data.AppearanceMode
import com.aiindexfinger.data.ClockWorkflowPack
import com.aiindexfinger.data.FilesWorkflowPack
import com.aiindexfinger.data.AiIndexFingerSelfTestPack
import com.aiindexfinger.data.sortedFolders
import com.aiindexfinger.data.WorkflowTransfer
import com.aiindexfinger.data.WorkflowVersion
import com.aiindexfinger.data.resolveRunHistoryDestination
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.RecordedClickFallbackCause
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
import com.aiindexfinger.scheduler.ScheduleNotificationAction
import com.aiindexfinger.scheduler.ScheduleNotificationReadiness
import com.aiindexfinger.scheduler.ScheduleRecurrence
import com.aiindexfinger.scheduler.ScheduleStorageException
import com.aiindexfinger.scheduler.ScheduledWorkflowEvent
import com.aiindexfinger.scheduler.ScheduledWorkflowEventController
import com.aiindexfinger.scheduler.WorkflowSchedule
import com.aiindexfinger.scheduler.WorkflowScheduler
import com.aiindexfinger.scheduler.localScheduleEpochMillis
import com.aiindexfinger.scheduler.missedSchedules
import com.aiindexfinger.scheduler.scheduleDelayMillis
import com.aiindexfinger.scheduler.scheduleNotificationAction
import com.aiindexfinger.scheduler.scheduleNotificationReadiness
import com.aiindexfinger.scheduler.openScheduleNotificationSettings
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
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val workflowPersistence by lazy { application as AiIndexFingerApplication }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val workflowScheduler by lazy { WorkflowScheduler(this) }
    private val appPreferences by lazy { AppPreferences(this) }
    private val accessibilityDisclosurePreferences by lazy {
        AccessibilityDisclosurePreferences(this)
    }
    private val scheduledWorkflowEvents = ScheduledWorkflowEventController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduledWorkflowEvents.publish(intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID))
        setContent {
            var appearanceMode by remember { mutableStateOf(appPreferences.appearanceMode()) }
            AiIndexFingerTheme(appearanceMode) {
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
                    val workflowPersistenceFailure by
                        workflowPersistence.persistenceFailure.collectAsStateWithLifecycle()
                    WorkflowApp(
                        initialLibrary = state.library,
                        initialRunRecords = state.runRecords,
                        initialRunHistoryCorrupt = state.runHistoryCorrupt,
                        initialSchedules = state.schedules,
                        initialRunMessage = state.loadMessageRes?.let { stringResource(it) },
                        workflowPersistenceFailure = workflowPersistenceFailure,
                        onWorkflowPersistenceFailureConsumed =
                            workflowPersistence::consumePersistenceFailure,
                        scheduledWorkflowEvent = scheduledWorkflowEvent,
                        onScheduledWorkflowEventConsumed = scheduledWorkflowEvents::consume,
                        onSave = workflowPersistence::saveLibrary,
                        onCommitWorkflow = workflowPersistence::commitWorkflow,
                        onCommitImport = workflowPersistence::commitLibrary,
                        onListVersions = workflowPersistence::listVersions,
                        onRollback = workflowPersistence::rollback,
                        onClearRunHistory = {
                            withContext(Dispatchers.IO) { runHistoryStore.clear() }
                        },
                        onSchedule = workflowScheduler::schedule,
                        onCancelSchedule = workflowScheduler::cancel,
                        onReloadSchedules = workflowScheduler::load,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        appearanceMode = appearanceMode,
                        onAppearanceModeChanged = { mode ->
                            appearanceMode = mode
                            appPreferences.setAppearanceMode(mode)
                        },
                        accessibilityDisclosureAcknowledged =
                            accessibilityDisclosurePreferences.isAcknowledged(),
                        onAccessibilityDisclosureAcknowledged =
                            accessibilityDisclosurePreferences::acknowledge,
                    )
                }
            }
        }
    }

    private fun loadInitialState(): InitialAppState {
        val workflowResult = workflowPersistence.loadLibrary()
        val runHistoryResult = runHistoryStore.loadDetailed()
        val library = workflowResult.library
        val scheduleResult = runCatching {
            workflowScheduler.load(
                library.workflows.filter { it.isReadyToRun() }.map { it.id }.toSet()
            )
        }
        scheduleResult.exceptionOrNull()?.let { error ->
            if (error !is ScheduleStorageException) throw error
        }
        val loadedSchedules = scheduleResult.getOrDefault(emptyList())
        val missed = missedSchedules(loadedSchedules)
        var schedules = loadedSchedules
        missed.forEach { schedule -> schedules = workflowScheduler.consumeMissedOccurrence(schedule.workflowId) }
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
    workflowPersistenceFailure: com.aiindexfinger.data.PersistenceFailureEvent?,
    onWorkflowPersistenceFailureConsumed: (Long) -> Unit,
    scheduledWorkflowEvent: ScheduledWorkflowEvent?,
    onScheduledWorkflowEventConsumed: (Long) -> Unit,
    onSave: (WorkflowLibrary) -> Unit,
    onCommitWorkflow: suspend (Workflow?, Workflow) -> WorkflowLibrary,
    onCommitImport: suspend (WorkflowLibrary) -> Unit,
        onListVersions: suspend (String) -> List<WorkflowVersion>,
        onRollback: suspend (String, String) -> Workflow,
    onClearRunHistory: suspend () -> Unit,
    onSchedule: (Workflow, Long, ScheduleRecurrence) -> List<WorkflowSchedule>,
    onCancelSchedule: (String) -> List<WorkflowSchedule>,
    onReloadSchedules: (Set<String>) -> List<WorkflowSchedule>,
    onOpenAccessibilitySettings: () -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    accessibilityDisclosureAcknowledged: Boolean,
    onAccessibilityDisclosureAcknowledged: () -> Unit,
) {
    var library by remember { mutableStateOf(initialLibrary) }
    val canonicalLibrary by (LocalContext.current.applicationContext as AiIndexFingerApplication)
        .library.collectAsStateWithLifecycle()
    LaunchedEffect(canonicalLibrary) {
        canonicalLibrary?.let { latest ->
            if (latest != library) library = latest
        }
    }
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
    var showSettings by remember { mutableStateOf(false) }
    var workflowComparison by remember { mutableStateOf<Pair<Workflow, Workflow>?>(null) }
        var versionHistory by remember { mutableStateOf<Pair<Workflow, List<WorkflowVersion>>?>(null) }
    var runMessage by remember { mutableStateOf(initialRunMessage) }
    var editorSaveError by remember { mutableStateOf<String?>(null) }
    var editorSaveInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(editingWorkflow?.id) {
        editorSaveError = null
        editorSaveInProgress = false
    }
    LaunchedEffect(workflowPersistenceFailure?.sequence) {
        workflowPersistenceFailure?.let { failure ->
            runMessage = failure.message
            onWorkflowPersistenceFailureConsumed(failure.sequence)
        }
    }
    var preflightReport by remember { mutableStateOf<Pair<Workflow, WorkflowPreflightReport>?>(null) }
    val runningWorkflowId by AutomationAccessibilityService.runningWorkflowId.collectAsStateWithLifecycle()
    val latestRun by AutomationAccessibilityService.latestRun.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Workflow?>(null) }
    var pendingBundleExport by remember { mutableStateOf<WorkflowLibrary?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val workflowTransfer = remember { WorkflowTransfer(context.contentResolver) }
    val requestClearRunHistory: () -> Unit = {
        coroutineScope.launch {
            try {
                clearRunHistory(onClearRunHistory)
                runRecords = emptyList()
                runHistoryCorrupt = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                runMessage = context.getString(R.string.clear_history_failed)
            }
        }
        Unit
    }
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
    var blockedNotificationReadiness by remember { mutableStateOf<ScheduleNotificationReadiness?>(null) }
    val persistSchedule: (Triple<Workflow, Long, ScheduleRecurrence>) -> Unit = { request ->
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
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingSchedule
        pendingSchedule = null
        if (granted && request != null && request.first.isReadyToRun()) {
            val readiness = scheduleNotificationReadiness(context)
            if (readiness == ScheduleNotificationReadiness.Ready) {
                persistSchedule(request)
            } else {
                blockedNotificationReadiness = readiness
                runMessage = context.getString(R.string.schedule_notifications_blocked)
            }
        } else if (granted && request != null) {
            runMessage = context.getString(
                R.string.cannot_schedule,
                request.first.readinessIssues().first().localizedMessage(context),
            )
        } else if (!granted) {
            blockedNotificationReadiness = ScheduleNotificationReadiness.RuntimePermissionRequired
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
        if (uri != null && !importInProgress) {
            importInProgress = true
            coroutineScope.launch {
                try {
                    var currentWorkflowCount = 0
                    val updated = readAndCommitImportedLibrary(
                        readImported = {
                            withContext(Dispatchers.IO) { workflowTransfer.readLibrary(uri) }
                        },
                        current = {
                            library.also { currentWorkflowCount = it.workflows.size }
                        },
                        newId = ::newId,
                        save = onCommitImport,
                    )
                    library = updated
                    runMessage = context.getString(
                        R.string.workflows_imported,
                        updated.workflows.size - currentWorkflowCount,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val details = (error as? InvalidWorkflowException)
                        ?.issue
                        ?.localizedMessage(context)
                        ?: error.message?.takeIf(String::isNotBlank)
                        ?: context.getString(R.string.import_error_unknown)
                    runMessage = context.getString(R.string.import_failed, details)
                } finally {
                    importInProgress = false
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
            persistedBaseline = workflows.firstOrNull { it.id == requireNotNull(editingWorkflow).id },
            initialEditingStepPath = initialEditingStepPath,
            saveInProgress = editorSaveInProgress,
            saveErrorMessage = editorSaveError,
            onTest = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = scheduleNotificationReadiness(context)
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
            onSave = { expected, workflow ->
                if (!editorSaveInProgress) {
                    editorSaveInProgress = true
                    editorSaveError = null
                    coroutineScope.launch {
                        runCatching {
                            onCommitWorkflow(expected, workflow)
                        }.onSuccess { updatedLibrary ->
                            library = updatedLibrary
                            if (!workflow.isReadyToRun()) {
                                runCatching { onCancelSchedule(workflow.id) }
                                    .onSuccess { schedules = it }
                                    .onFailure { error ->
                                        if (error !is ScheduleStorageException) throw error
                                        runMessage = context.getString(
                                            R.string.workflow_saved_schedule_cleanup_failed,
                                        )
                                    }
                            }
                            editingWorkflow = null
                            initialEditingStepPath = null
                        }.onFailure { error ->
                            editorSaveError = context.getString(
                                if (error is com.aiindexfinger.data.WorkflowEditConflictException) {
                                    R.string.workflow_edit_conflict
                                } else {
                                    R.string.save_failed
                                },
                            )
                        }
                        editorSaveInProgress = false
                    }
                }
            },
        )
    } else if (showSettings) {
        SettingsScreen(
            appearanceMode = appearanceMode,
            onAppearanceModeChanged = onAppearanceModeChanged,
            onOpenAccessibilitySettings = requestAccessibilitySetup,
            onReviewAccessibilityDisclosure = {
                if (accessibilityDisclosureGate.reviewDisclosure() ==
                    AccessibilityDisclosureAction.ShowDisclosure
                ) {
                    showAccessibilityDisclosure = true
                }
            },
            onBack = { showSettings = false },
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
            onClear = requestClearRunHistory,
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
                            R.string.clock_workflow_set_validation_alarm_time,
                            R.string.clock_workflow_set_validation_alarm_sound,
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
            onImport = {
                if (!importInProgress) importLauncher.launch(arrayOf("application/json", "text/plain"))
            },
            importInProgress = importInProgress,
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
                } else {
                    val request = Triple(workflow, targetEpochMillis, recurrence)
                    val readiness = scheduleNotificationReadiness(context)
                    when (scheduleNotificationAction(readiness)) {
                        ScheduleNotificationAction.Schedule -> persistSchedule(request)
                        ScheduleNotificationAction.RequestPermission -> {
                            pendingSchedule = request
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ScheduleNotificationAction.OpenSettings -> {
                            blockedNotificationReadiness = readiness
                            runMessage = context.getString(R.string.schedule_notifications_blocked)
                        }
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
            onClearRunHistory = requestClearRunHistory,
            onViewRunHistory = { showRunHistory = true },
            onOpenSettings = { showSettings = true },
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
            onDebug = { workflow ->
                val service = AutomationAccessibilityService.instance
                val issue = workflow.readinessIssues().firstOrNull()
                if (issue != null) {
                    runMessage = context.getString(R.string.cannot_run, issue.localizedMessage(context))
                } else if (service == null) {
                    runMessage = context.getString(R.string.enable_automation_before_run)
                    requestAccessibilitySetup()
                } else {
                    val started = service.startWorkflow(workflow, debug = true)
                    runMessage = if (started) {
                        context.getString(R.string.debugging_workflow, workflow.name)
                    } else {
                        context.getString(R.string.another_workflow_running)
                    }
                }
            },
            onPreflight = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = scheduleNotificationReadiness(context)
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
                    PreflightRecoveryAction.OpenNotificationSettings -> {
                        openScheduleNotificationSettings(context, report.notificationStatus)
                    }
                }
            },
        )
    }
    blockedNotificationReadiness?.let { readiness ->
        ScheduleNotificationRecoveryDialog(
            onOpenSettings = {
                openScheduleNotificationSettings(context, readiness)
                blockedNotificationReadiness = null
            },
            onDismiss = { blockedNotificationReadiness = null },
        )
    }

    if (importInProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_in_progress_title)) },
            text = {
                Row(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.import_in_progress_message))
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
internal fun ScheduleNotificationRecoveryDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SCHEDULE_NOTIFICATION_RECOVERY_TAG),
        title = { Text(stringResource(R.string.schedule_notifications_blocked_title)) },
        text = {
            Text(
                stringResource(R.string.schedule_notifications_blocked),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(SCHEDULE_NOTIFICATION_SETTINGS_TAG),
            ) { Text(stringResource(R.string.open_notification_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
    importInProgress: Boolean,
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
    onOpenSettings: () -> Unit,
    runningWorkflowId: String?,
    runMessage: String?,
    onRun: (Workflow) -> Unit,
    onDebug: (Workflow) -> Unit,
    onPreflight: (Workflow) -> Unit,
    onStop: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onReviewAccessibilityDisclosure: () -> Unit,
) {
    val serviceConnected by AutomationAccessibilityService.connected.collectAsStateWithLifecycle()
    val currentStepId by AutomationAccessibilityService.currentStepId.collectAsStateWithLifecycle()
    val debugPaused by AutomationAccessibilityService.debugPaused.collectAsStateWithLifecycle()
    val workflowStartedAtMillis by AutomationAccessibilityService.workflowStartedAtMillis
        .collectAsStateWithLifecycle()
    val observedNodes by AutomationAccessibilityService.observedNodes.collectAsStateWithLifecycle()
    val overlayStatus by AutomationAccessibilityService.overlayStatus.collectAsStateWithLifecycle()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AI Index Finger",
                    modifier = Modifier.weight(1f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings_title))
                }
            }
            Text(stringResource(R.string.workflows), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            PermissionStatus(
                connected = serviceConnected,
                onOpenSettings = onOpenAccessibilitySettings,
                onReviewDisclosure = onReviewAccessibilityDisclosure,
            )
            OutlinedButton(
                enabled = serviceConnected,
                onClick = { AutomationAccessibilityService.instance?.startFloatingWorkflowEditor() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.floating_editor_open)) }
            overlayStatus?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (runningWorkflowId != null) {
                val currentWorkflow = workflows.firstOrNull { it.id == runningWorkflowId }
                val currentStep = currentStepId?.let { currentWorkflow?.steps?.findById(it) }
                Spacer(Modifier.height(14.dp))
                RunningWorkflowStatus(
                    workflowName = currentWorkflow?.name ?: stringResource(R.string.workflow),
                    stepName = currentStep?.title() ?: currentStepId,
                    elapsedMillis = elapsedMillis,
                    debugPaused = debugPaused,
                    onNext = { AutomationAccessibilityService.instance?.advanceWorkflow() },
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
                TextButton(
                    onClick = onImport,
                    enabled = runningWorkflowId == null && !importInProgress,
                ) { Text(stringResource(R.string.import_action)) }
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
                    isAnotherWorkflowRunning = runningWorkflowId != null && workflow.id != runningWorkflowId,
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
                    onDebug = { onDebug(workflow) },
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
internal fun WorkflowEditor(
    workflow: Workflow,
    persistedBaseline: Workflow? = workflow,
    initialEditingStepPath: StepPath? = null,
    floatingEditorMode: Boolean = false,
    onCollapse: (() -> Unit)? = null,
    saveInProgress: Boolean = false,
    saveErrorMessage: String? = null,
    onTest: (Workflow) -> Unit,
    onBack: () -> Unit,
    onSave: (expected: Workflow?, candidate: Workflow) -> Unit,
) {
    val context = LocalContext.current
    val saveBaseline = remember(workflow.id) { persistedBaseline }
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
    var showOperationChooser by remember { mutableStateOf(false) }
    var inspectedClickSelector by remember { mutableStateOf<NodeSelector?>(null) }
    var policyStepPath by remember { mutableStateOf<StepPath?>(null) }
    var editingStepPath by remember(workflow.id) { mutableStateOf(initialEditingStepPath) }
    var stepToDeletePath by remember { mutableStateOf<StepPath?>(null) }
    var confirmDiscardChanges by remember { mutableStateOf(false) }
    var unrecognizedClickCount by remember { mutableStateOf(0) }
    var showAllValidationIssues by remember(workflow.id) { mutableStateOf(false) }
    val observedNodes by AutomationAccessibilityService.observedNodes.collectAsStateWithLifecycle()
    val pendingOverlayAction by AutomationAccessibilityService.pendingOverlayAction.collectAsStateWithLifecycle()
    val inspectedSelector by AutomationAccessibilityService.inspectedSelector.collectAsStateWithLifecycle()
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
    val hasUnsavedChanges = saveBaseline == null ||
        name != workflow.name ||
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

    LaunchedEffect(pendingOverlayAction, workflow.id) {
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
                                fallbackCause = recordedAction.target.fallbackCause,
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
            is PendingOverlayAction.LiveAction -> {
                val result = applyLiveActionHandoff(steps, workflow.id, action, ::newId)
                if (result.appended) steps = result.steps
                if (result.consume) {
                    AutomationAccessibilityService.consumePendingOverlayAction(action)
                }
            }
        }
    }

    LaunchedEffect(inspectedSelector) {
        if (inspectedSelector == null) return@LaunchedEffect
        inspectedClickSelector = AutomationAccessibilityService.consumeInspectedSelector()
        showClickDialog = inspectedClickSelector != null
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
    if (showOperationChooser) {
        WorkflowOperationChooserDialog(
            hasSteps = currentSteps.isNotEmpty(),
            serviceConnected = AutomationAccessibilityService.instance != null,
            onDismiss = { showOperationChooser = false },
            onSelect = { operation ->
                showOperationChooser = false
                when (operation) {
                    WorkflowEditorOperation.LaunchApp -> showLaunchDialog = true
                    WorkflowEditorOperation.Click -> {
                        inspectedClickSelector = null
                        showClickDialog = true
                    }
                    WorkflowEditorOperation.ImageClick -> showImageClickDialog = true
                    WorkflowEditorOperation.RecordedClick -> {
                        AutomationAccessibilityService.instance?.startElementMonitor(workflow.id, currentListPath)
                    }
                    WorkflowEditorOperation.LongClick -> showLongClickDialog = true
                    WorkflowEditorOperation.Tap -> showTapDialog = true
                    WorkflowEditorOperation.Scroll -> showScrollDialog = true
                    WorkflowEditorOperation.InputText -> showInputDialog = true
                    WorkflowEditorOperation.Swipe -> showSwipeDialog = true
                    WorkflowEditorOperation.Delay -> showWaitDialog = true
                    WorkflowEditorOperation.GlobalBack -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.Back),
                    )
                    WorkflowEditorOperation.GlobalHome -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.Home),
                    )
                    WorkflowEditorOperation.GlobalRecents -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.Recents),
                    )
                    WorkflowEditorOperation.WaitForNode -> showWaitNodeDialog = true
                    WorkflowEditorOperation.SetVariable -> showVariableDialog = true
                    WorkflowEditorOperation.ReadNodeText -> showReadNodeTextDialog = true
                    WorkflowEditorOperation.Repeat -> showRepeatDialog = true
                    WorkflowEditorOperation.VariableCondition -> showConditionDialog = true
                    WorkflowEditorOperation.NodeCondition -> showNodeConditionDialog = true
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
                        defaultTimeoutMillis != null && defaultTimeoutMillis > 0 && !saveInProgress
                    saveErrorMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (saveInProgress) {
                        Text(stringResource(R.string.floating_editor_saving), fontSize = 12.sp)
                    }
                    onCollapse?.let { collapse ->
                        OutlinedButton(
                            onClick = collapse,
                            modifier = Modifier.fillMaxWidth().testTag(FLOATING_EDITOR_COLLAPSE_TAG),
                        ) { Text(stringResource(R.string.floating_editor_collapse)) }
                    }
                    Button(
                        onClick = { showOperationChooser = true },
                        modifier = Modifier.fillMaxWidth().testTag(WORKFLOW_EDITOR_ADD_OPERATION_TAG),
                    ) { Text(stringResource(R.string.add_operation)) }
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
                                    saveBaseline,
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
                                    saveBaseline,
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
                TextButton(
                    onClick = requestBack,
                    modifier = Modifier.testTag(WORKFLOW_EDITOR_BACK_TAG),
                ) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.workflow_editor), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.workflow_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(WORKFLOW_NAME_INPUT_TAG),
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
            Text(
                stringResource(R.string.add_action),
                modifier = Modifier.testTag(WORKFLOW_EDITOR_ALL_ACTIONS_TAG),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showLaunchDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.launch_app))
                }
                Button(
                    onClick = {
                        inspectedClickSelector = null
                        showClickDialog = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.click))
                }
            }
            OutlinedButton(
                onClick = { showImageClickDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.image_click)) }
            if (!floatingEditorMode) {
                OutlinedButton(
                    enabled = AutomationAccessibilityService.instance != null && !hasUnsavedChanges,
                    onClick = {
                        AutomationAccessibilityService.instance?.startFloatingWorkflowEditor(workflow.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.floating_editor_open)) }
                if (hasUnsavedChanges) {
                    Text(
                        stringResource(R.string.floating_editor_save_first),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            OutlinedButton(
                enabled = AutomationAccessibilityService.instance != null,
                onClick = {
                    AutomationAccessibilityService.instance?.startLiveAction(workflow.id, currentListPath)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.live_action_quick_add)) }
            OutlinedButton(
                enabled = AutomationAccessibilityService.instance != null,
                onClick = {
                    AutomationAccessibilityService.instance?.startElementMonitor(workflow.id, currentListPath)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.monitor_elements_overlay)) }
            OutlinedButton(
                enabled = AutomationAccessibilityService.instance != null,
                onClick = { AutomationAccessibilityService.instance?.startElementInspector() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.inspect_element_overlay)) }
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
                enabled = currentSteps.isNotEmpty(),
                onClick = { showNodeConditionDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.element_exists_condition)) }
        }
    }

    if (showClickDialog) {
        ClickStepDialog(
            observedNodes = observedNodes,
            initialSelector = inspectedClickSelector,
            onDismiss = {
                inspectedClickSelector = null
                showClickDialog = false
            },
            onAdd = { selector ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.Click(UUID.randomUUID().toString(), selector),
                )
                inspectedClickSelector = null
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
            titleRes = R.string.long_click_element,
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
            onAdd = { packageName, intentAction ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.LaunchApp(UUID.randomUUID().toString(), packageName, intentAction),
                )
                showLaunchDialog = false
            },
        )
    }
    if (showInputDialog) {
        InputTextDialog(
            observedNodes = observedNodes,
            confirmLabel = stringResource(R.string.add),
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
            confirmLabel = stringResource(R.string.add),
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
            title = stringResource(R.string.wait_action),
            label = stringResource(R.string.duration_millis),
            initialValue = "1000",
            confirmLabel = stringResource(R.string.add),
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
                initialIntentAction = step.intentAction,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { packageName, intentAction ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(packageName = packageName, intentAction = intentAction),
                    )
                    editingStepPath = null
                },
            )
            is Step.LongClick -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                titleRes = R.string.long_click_element,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { selector ->
                    steps = steps.replaceStep(path, step.copy(selector = selector))
                    editingStepPath = null
                },
            )
            is Step.Click -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                confirmLabelRes = R.string.save,
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
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { replacement ->
                    steps = steps.replaceStep(path, replacement.copy(id = step.id))
                    editingStepPath = null
                },
            )
            is Step.InputText -> InputTextDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = stringResource(R.string.save),
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
                title = stringResource(R.string.edit_wait),
                label = stringResource(R.string.duration_millis),
                initialValue = step.durationMillis.toString(),
                confirmLabel = stringResource(R.string.save),
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
                    is Condition.Equals -> ConditionSettingsDialog(
                        initialLeft = condition.left,
                        initialRight = condition.right,
                        initialOperator = condition.operator,
                        onDismiss = { editingStepPath = null },
                        onSave = { left, right, operator ->
                            steps = steps.replaceStep(
                                path,
                                step.copy(condition = Condition.Equals(left, right, operator)),
                            )
                            editingStepPath = null
                        },
                    )
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
                confirmLabelRes = R.string.save,
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
                confirmLabel = stringResource(R.string.save),
                onDismiss = { editingStepPath = null },
                onAdd = { selector, direction ->
                    steps = steps.replaceStep(path, step.copy(selector = selector, direction = direction))
                    editingStepPath = null
                },
            )
            is Step.WaitForNode -> WaitNodeDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabelRes = R.string.save,
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
            title = stringResource(R.string.repeat_steps),
            valueLabel = stringResource(R.string.repeat_count),
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
            onAdd = { index, left, right, operator ->
                val path = StepPath(currentListPath, index)
                val nestedStep = steps.stepAt(path)
                steps = steps.replaceStep(
                    path,
                    Step.IfElse(
                        id = newId(),
                        condition = Condition.Equals(left, right, operator),
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
                title = { Text(stringResource(R.string.delete_step_title)) },
                text = { Text(stringResource(R.string.delete_step_message, path.index + 1, step.title())) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            steps = steps.removeStep(path)
                            stepToDeletePath = null
                        },
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { stepToDeletePath = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
    if (confirmDiscardChanges) {
        AlertDialog(
            onDismissRequest = { confirmDiscardChanges = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardChanges = false }) {
                    Text(stringResource(R.string.continue_editing))
                }
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
        title = { Text(stringResource(R.string.global_action)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.repeat_settings)) },
        text = {
            NodeField(
                countText,
                { countText = it },
                stringResource(R.string.repeat_count_range, Step.Repeat.MAX_REPEAT_COUNT),
                true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = count != null && count in 1..Step.Repeat.MAX_REPEAT_COUNT,
                onClick = { onSave(requireNotNull(count)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConditionSettingsDialog(
    initialLeft: Value,
    initialRight: Value,
    initialOperator: ComparisonOperator,
    onDismiss: () -> Unit,
    onSave: (Value, Value, ComparisonOperator) -> Unit,
) {
    var leftMode by remember(initialLeft) { mutableStateOf(variableValueMode(initialLeft)) }
    var leftText by remember(initialLeft) { mutableStateOf(variableValueText(initialLeft)) }
    var rightMode by remember(initialRight) { mutableStateOf(variableValueMode(initialRight)) }
    var rightText by remember(initialRight) { mutableStateOf(variableValueText(initialRight)) }
    var operator by remember(initialOperator) { mutableStateOf(initialOperator) }
    val left = variableValueOrNull(leftMode, leftText)
    val right = variableValueOrNull(rightMode, rightText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.condition_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VariableValueEditor(
                    title = stringResource(R.string.comparison_left_value),
                    mode = leftMode,
                    text = leftText,
                    onModeChange = { leftMode = it },
                    onTextChange = { leftText = it },
                )
                Text(stringResource(R.string.comparison_operator), fontWeight = FontWeight.SemiBold)
                ComparisonOperatorSelector(operator) { operator = it }
                VariableValueEditor(
                    title = stringResource(R.string.comparison_right_value),
                    mode = rightMode,
                    text = rightText,
                    onModeChange = { rightMode = it },
                    onTextChange = { rightText = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = left != null && right != null,
                onClick = { onSave(requireNotNull(left), requireNotNull(right), operator) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.step_settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(
                    timeoutText,
                    { timeoutText = it },
                    stringResource(R.string.step_timeout_override, defaultTimeoutMillis),
                )
                Text(stringResource(R.string.on_failure), fontWeight = FontWeight.SemiBold)
                Button(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Stop, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.stop_workflow))
                }
                OutlinedButton(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Continue, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.continue_next_step)) }
                Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold)
                NodeField(
                    retryAttempts,
                    { retryAttempts = it },
                    stringResource(R.string.retry_attempts),
                    true,
                )
                NodeField(
                    retryDelay,
                    { retryDelay = it },
                    stringResource(R.string.retry_delay_millis),
                    true,
                )
                OutlinedButton(
                    enabled = timeoutValid && attempts != null && attempts in 1..10 && delay != null && delay >= 0,
                    onClick = {
                        onSelect(
                            FailurePolicy.Retry(requireNotNull(attempts), requireNotNull(delay)),
                            timeoutMillis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.use_retry_policy)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InputTextDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.InputText? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, String, String?, TextInputMethod) -> Unit,
) {
    var selectorDraft by remember(initialStep) {
        mutableStateOf(initialStep?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    var inputText by remember(initialStep) { mutableStateOf(initialStep?.text.orEmpty()) }
    var useVariable by remember(initialStep) { mutableStateOf(initialStep?.variableName != null) }
    var variableName by remember(initialStep) { mutableStateOf(initialStep?.variableName.orEmpty()) }
    var inputMethod by remember(initialStep) {
        mutableStateOf(initialStep?.inputMethod ?: TextInputMethod.SetText)
    }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.input_text)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = observedNodes,
                    recentTitle = stringResource(R.string.select_recent_text_field),
                    emptyMessage = stringResource(R.string.input_target_hint),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (useVariable) R.string.input_use_variable else R.string.input_use_fixed_text,
                            ),
                        )
                        Text(
                            stringResource(
                                if (useVariable) {
                                    R.string.input_variable_description
                                } else {
                                    R.string.input_fixed_text_description
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = useVariable, onCheckedChange = { useVariable = it })
                }
                if (useVariable) {
                    NodeField(variableName, { variableName = it }, stringResource(R.string.variable_name), true)
                } else {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text(stringResource(R.string.text_to_input)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (inputMethod == TextInputMethod.Paste) {
                                    R.string.input_method_paste
                                } else {
                                    R.string.input_method_set_text
                                },
                            ),
                        )
                        Text(
                            if (inputMethod == TextInputMethod.Paste) {
                                stringResource(R.string.input_method_paste_description)
                            } else {
                                stringResource(R.string.input_method_set_text_description)
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
                        inputText,
                        preserveUnchangedOrTrim(
                            variableName,
                            initialStep?.variableName.orEmpty(),
                        ).takeIf { useVariable },
                        inputMethod,
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
private fun NodeSelectorEditor(
    draft: NodeSelectorDraft,
    onDraftChange: (NodeSelectorDraft) -> Unit,
    recentNodes: List<ObservedNode>,
    recentTitle: String,
    emptyMessage: String,
) {
    var selectionError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }
    VisualSelectorCapture(
        onSelectorSelected = { selector ->
            selectionError = null
            onDraftChange(selector.toDraft())
        },
        onSelectionError = { selectionError = it },
    )
    selectionError?.let { message ->
        Text(
            message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    }
    HorizontalDivider()
    Text(recentTitle, fontWeight = FontWeight.SemiBold)
    if (recentNodes.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    recentNodes.observedControlCandidates().forEach { node ->
        OutlinedButton(
            onClick = { onDraftChange(node.toSelector().toDraft()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
    }
    if (draft.hasTargetAttribute) {
        Text(
            stringResource(
                R.string.selected_element,
                draft.viewId.ifBlank {
                    draft.text.ifBlank {
                        draft.contentDescription.ifBlank { draft.className }
                    }
                },
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider()
    Text(stringResource(R.string.element_attributes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    NodeField(
        draft.packageName,
        { onDraftChange(draft.copy(packageName = it)) },
        stringResource(R.string.selector_package_optional),
    )
    NodeField(
        draft.viewId,
        { onDraftChange(draft.copy(viewId = it)) },
        stringResource(R.string.resource_id),
    )
    NodeField(
        draft.text,
        { onDraftChange(draft.copy(text = it)) },
        stringResource(R.string.selector_text),
    )
    SelectorToggleRow(
        label = stringResource(R.string.text_contains),
        checked = draft.textMatchMode == TextMatchMode.Contains,
        enabled = draft.text.isNotBlank(),
        onCheckedChange = { contains ->
            onDraftChange(
                draft.copy(
                    textMatchMode = if (contains) TextMatchMode.Contains else TextMatchMode.Exact,
                ),
            )
        },
    )
    NodeField(
        draft.contentDescription,
        { onDraftChange(draft.copy(contentDescription = it)) },
        stringResource(R.string.selector_content_description),
    )
    SelectorToggleRow(
        label = stringResource(R.string.description_contains),
        checked = draft.contentDescriptionMatchMode == TextMatchMode.Contains,
        enabled = draft.contentDescription.isNotBlank(),
        onCheckedChange = { contains ->
            onDraftChange(
                draft.copy(
                    contentDescriptionMatchMode = if (contains) {
                        TextMatchMode.Contains
                    } else {
                        TextMatchMode.Exact
                    },
                ),
            )
        },
    )
    NodeField(
        draft.className,
        { onDraftChange(draft.copy(className = it)) },
        stringResource(R.string.class_name),
    )
    MatchIndexControl(draft.matchIndex) { onDraftChange(draft.copy(matchIndex = it)) }
    SelectorToggleRow(
        label = stringResource(R.string.limit_to_ancestor),
        checked = draft.useAncestor,
        onCheckedChange = { onDraftChange(draft.copy(useAncestor = it)) },
    )
    if (draft.useAncestor) {
        Text(
            stringResource(R.string.ancestor_attributes),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        NodeField(
            draft.ancestorViewId,
            { onDraftChange(draft.copy(ancestorViewId = it)) },
            stringResource(R.string.ancestor_resource_id),
        )
        NodeField(
            draft.ancestorText,
            { onDraftChange(draft.copy(ancestorText = it)) },
            stringResource(R.string.ancestor_text),
        )
        SelectorToggleRow(
            label = stringResource(R.string.text_contains),
            checked = draft.ancestorTextMatchMode == TextMatchMode.Contains,
            enabled = draft.ancestorText.isNotBlank(),
            onCheckedChange = { contains ->
                onDraftChange(
                    draft.copy(
                        ancestorTextMatchMode = if (contains) {
                            TextMatchMode.Contains
                        } else {
                            TextMatchMode.Exact
                        },
                    ),
                )
            },
        )
        NodeField(
            draft.ancestorContentDescription,
            { onDraftChange(draft.copy(ancestorContentDescription = it)) },
            stringResource(R.string.ancestor_content_description),
        )
        SelectorToggleRow(
            label = stringResource(R.string.description_contains),
            checked = draft.ancestorContentDescriptionMatchMode == TextMatchMode.Contains,
            enabled = draft.ancestorContentDescription.isNotBlank(),
            onCheckedChange = { contains ->
                onDraftChange(
                    draft.copy(
                        ancestorContentDescriptionMatchMode = if (contains) {
                            TextMatchMode.Contains
                        } else {
                            TextMatchMode.Exact
                        },
                    ),
                )
            },
        )
        NodeField(
            draft.ancestorClassName,
            { onDraftChange(draft.copy(ancestorClassName = it)) },
            stringResource(R.string.ancestor_class),
        )
    }
    if (!draft.hasTargetAttribute) {
        Text(
            stringResource(R.string.selector_attribute_required),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    } else if (draft.useAncestor && !draft.hasAncestorAttribute) {
        Text(
            stringResource(R.string.ancestor_attribute_required),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SelectorToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
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
                        stringResource(initialStep.fallbackCause?.messageResourceId()
                            ?: R.string.recorded_click_control_unavailable),
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

private fun RecordedClickFallbackCause.messageResourceId(): Int = when (this) {
    RecordedClickFallbackCause.SourceUnavailable -> R.string.recorded_click_fallback_source_unavailable
    RecordedClickFallbackCause.SourceInvalid -> R.string.recorded_click_fallback_source_invalid
    RecordedClickFallbackCause.HierarchyUnavailable -> R.string.recorded_click_fallback_hierarchy_unavailable
    RecordedClickFallbackCause.HierarchyChanged -> R.string.recorded_click_fallback_hierarchy_changed
    RecordedClickFallbackCause.HierarchyIncomplete -> R.string.recorded_click_fallback_hierarchy_incomplete
    RecordedClickFallbackCause.SourceNotUnique -> R.string.recorded_click_fallback_source_not_unique
    RecordedClickFallbackCause.SelectorNotUnique -> R.string.recorded_click_fallback_selector_not_unique
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
    confirmLabel: String,
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, ScrollDirection) -> Unit,
) {
    var selectorDraft by remember(initialStep) {
        mutableStateOf(initialStep?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    var direction by remember(initialStep) {
        mutableStateOf(initialStep?.direction ?: ScrollDirection.Forward)
    }
    val scrollableNodes = observedNodes.filter { it.scrollable }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scroll_element)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = scrollableNodes,
                    recentTitle = stringResource(R.string.select_recent_element),
                    emptyMessage = stringResource(R.string.no_scrollable_elements),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (direction == ScrollDirection.Forward) {
                                    R.string.scroll_backward
                                } else {
                                    R.string.scroll_forward
                                },
                            ),
                        )
                        Text(
                            stringResource(
                                if (direction == ScrollDirection.Forward) {
                                    R.string.scroll_toward_later_content
                                } else {
                                    R.string.scroll_toward_earlier_content
                                },
                            ),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun WaitNodeDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.WaitForNode? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, Long?, Boolean) -> Unit,
) {
    var selectorDraft by remember(initialStep) {
        mutableStateOf(initialStep?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    var mustExist by remember(initialStep) { mutableStateOf(initialStep?.mustExist ?: true) }
    var timeout by remember(initialStep) {
        mutableStateOf(initialStep?.timeoutMillis?.toString().orEmpty())
    }
    val timeoutValue = timeout.toLongOrNull()
    val timeoutValid = timeout.isBlank() || timeoutValue != null && timeoutValue > 0
    val selectedSelector = selectorDraft.toSelectorOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wait_for_element)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = observedNodes,
                    recentTitle = stringResource(R.string.select_recent_element),
                    emptyMessage = stringResource(R.string.open_target_app_then_return),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (mustExist) R.string.wait_element_appear else R.string.wait_element_disappear,
                            ),
                        )
                        Text(
                            stringResource(
                                if (mustExist) {
                                    R.string.wait_element_appear_description
                                } else {
                                    R.string.wait_element_disappear_description
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = mustExist, onCheckedChange = { mustExist = it })
                }
                NodeField(
                    timeout,
                    { timeout = it },
                    stringResource(R.string.wait_timeout_override),
                )
                Text(
                    stringResource(R.string.wait_timeout_inherit_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && timeoutValid,
                onClick = {
                    onAdd(requireNotNull(selectedSelector), timeoutValue, mustExist)
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SetVariableDialog(
    initialName: String = "",
    initialValue: Value = Value.Literal(""),
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (String, Value) -> Unit,
) {
    var variableName by remember(initialName) { mutableStateOf(initialName) }
    var valueMode by remember(initialValue) { mutableStateOf(variableValueMode(initialValue)) }
    var value by remember(initialValue) { mutableStateOf(variableValueText(initialValue)) }
    val resolvedValue = variableValueOrNull(valueMode, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_variable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(variableName, { variableName = it }, stringResource(R.string.variable_name), true)
                VariableValueEditor(
                    title = stringResource(R.string.variable_value_source),
                    mode = valueMode,
                    text = value,
                    onModeChange = { valueMode = it },
                    onTextChange = { value = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = variableName.isNotBlank() && resolvedValue != null,
                onClick = {
                    onAdd(variableName.trim(), requireNotNull(resolvedValue))
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun VariableValueEditor(
    title: String,
    mode: VariableValueMode,
    text: String,
    onModeChange: (VariableValueMode) -> Unit,
    onTextChange: (String) -> Unit,
) {
    Text(title, fontWeight = FontWeight.SemiBold)
    VariableValueMode.entries.forEach { option ->
        if (mode == option) {
            Button(
                onClick = { onModeChange(option) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(option.labelRes)) }
        } else {
            OutlinedButton(
                onClick = { onModeChange(option) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(option.labelRes)) }
        }
    }
    NodeField(
        text,
        onTextChange,
        stringResource(mode.fieldLabelRes),
        mode == VariableValueMode.Variable,
    )
    Text(
        stringResource(mode.hintRes),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
}

internal enum class VariableValueMode(
    val labelRes: Int,
    val fieldLabelRes: Int,
    val hintRes: Int,
) {
    Literal(R.string.variable_value_literal, R.string.value_label, R.string.variable_value_literal_hint),
    Variable(R.string.variable_value_reference, R.string.source_variable_name, R.string.variable_value_reference_hint),
    Template(R.string.variable_value_template, R.string.template_label, R.string.variable_value_template_hint),
}

internal fun variableValueMode(value: Value): VariableValueMode = when (value) {
    is Value.Literal -> VariableValueMode.Literal
    is Value.Variable -> VariableValueMode.Variable
    is Value.Template -> VariableValueMode.Template
}

internal fun variableValueText(value: Value): String = when (value) {
    is Value.Literal -> value.value
    is Value.Variable -> value.name
    is Value.Template -> value.template
}

internal fun variableValueOrNull(mode: VariableValueMode, text: String): Value? = when (mode) {
    VariableValueMode.Literal -> Value.Literal(text)
    VariableValueMode.Variable -> text.trim().takeIf(String::isNotEmpty)?.let(Value::Variable)
    VariableValueMode.Template -> Value.Template(text)
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
                Text(stringResource(R.string.select_step_to_wrap), fontWeight = FontWeight.SemiBold)
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
            ) { Text(stringResource(R.string.wrap_step)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConditionDialog(
    steps: List<Step>,
    onDismiss: () -> Unit,
    onAdd: (Int, Value, Value, ComparisonOperator) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var leftMode by remember { mutableStateOf(VariableValueMode.Variable) }
    var leftText by remember { mutableStateOf("") }
    var rightMode by remember { mutableStateOf(VariableValueMode.Literal) }
    var rightText by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf(ComparisonOperator.Equals) }
    val left = variableValueOrNull(leftMode, leftText)
    val right = variableValueOrNull(rightMode, rightText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.if_variable_matches)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VariableValueEditor(
                    title = stringResource(R.string.comparison_left_value),
                    mode = leftMode,
                    text = leftText,
                    onModeChange = { leftMode = it },
                    onTextChange = { leftText = it },
                )
                Text(stringResource(R.string.comparison_operator), fontWeight = FontWeight.SemiBold)
                ComparisonOperatorSelector(operator) { operator = it }
                VariableValueEditor(
                    title = stringResource(R.string.comparison_right_value),
                    mode = rightMode,
                    text = rightText,
                    onModeChange = { rightMode = it },
                    onTextChange = { rightText = it },
                )
                Text(stringResource(R.string.run_step_when_true), fontWeight = FontWeight.SemiBold)
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
                enabled = selectedIndex != null && left != null && right != null,
                onClick = {
                    onAdd(requireNotNull(selectedIndex), requireNotNull(left), requireNotNull(right), operator)
                },
            ) { Text(stringResource(R.string.wrap_step)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

@Composable
private fun ComparisonOperator.displayName(): String = stringResource(when (this) {
    ComparisonOperator.Equals -> R.string.comparison_equals
    ComparisonOperator.NotEquals -> R.string.comparison_not_equals
    ComparisonOperator.Contains -> R.string.comparison_contains
    ComparisonOperator.NotContains -> R.string.comparison_not_contains
})

@Composable
private fun SystemAction.displayName(): String = stringResource(when (this) {
    SystemAction.Back -> R.string.system_action_back
    SystemAction.Home -> R.string.system_action_home
    SystemAction.Recents -> R.string.system_action_recents
})

@Composable
private fun WorkflowState.displayName(): String = stringResource(when (this) {
    WorkflowState.Draft -> R.string.workflow_state_draft
    WorkflowState.Ready -> R.string.workflow_state_ready
})

@Composable
private fun ScheduleNotificationReadiness.displayName(): String = stringResource(when (this) {
    ScheduleNotificationReadiness.Ready -> R.string.notification_status_ready
    ScheduleNotificationReadiness.RuntimePermissionRequired ->
        R.string.notification_status_permission_required
    ScheduleNotificationReadiness.AppNotificationsDisabled ->
        R.string.notification_status_app_disabled
    ScheduleNotificationReadiness.ChannelDisabled ->
        R.string.notification_status_channel_disabled
})

@Composable
private fun com.aiindexfinger.model.SelectorRole.displayName(): String = stringResource(when (this) {
    com.aiindexfinger.model.SelectorRole.Click -> R.string.selector_role_click
    com.aiindexfinger.model.SelectorRole.RecordedClick -> R.string.selector_role_recorded_click
    com.aiindexfinger.model.SelectorRole.LongClick -> R.string.selector_role_long_click
    com.aiindexfinger.model.SelectorRole.InputText -> R.string.selector_role_input_text
    com.aiindexfinger.model.SelectorRole.ReadNodeText -> R.string.selector_role_read_node_text
    com.aiindexfinger.model.SelectorRole.Scroll -> R.string.selector_role_scroll
    com.aiindexfinger.model.SelectorRole.WaitForNode -> R.string.selector_role_wait_for_node
    com.aiindexfinger.model.SelectorRole.NodeCondition -> R.string.selector_role_node_condition
})

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
    var selectorDraft by remember(initialSelector) {
        mutableStateOf(initialSelector?.toDraft() ?: NodeSelectorDraft())
    }
    var selectedStepIndex by remember { mutableStateOf<Int?>(null) }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.if_element_exists)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = observedNodes,
                    recentTitle = stringResource(R.string.select_element),
                    emptyMessage = stringResource(R.string.open_target_app_then_return),
                )
                steps?.let { availableSteps ->
                    Text(stringResource(R.string.run_step_when_element_exists), fontWeight = FontWeight.SemiBold)
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
            ) {
                Text(stringResource(if (steps == null) R.string.save else R.string.wrap_step))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
    var selectorDraft by remember(initialSelector) {
        mutableStateOf(initialSelector?.toDraft() ?: NodeSelectorDraft())
    }
    var variableName by remember(initialVariableName) { mutableStateOf(initialVariableName) }
    var attribute by remember(initialAttribute) { mutableStateOf(initialAttribute) }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_element_attribute)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(variableName, { variableName = it }, stringResource(R.string.save_to_variable), true)
                Text(stringResource(R.string.attribute), fontWeight = FontWeight.SemiBold)
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
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = observedNodes,
                    recentTitle = stringResource(R.string.select_element),
                    emptyMessage = stringResource(R.string.open_target_app_then_return),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && variableName.isNotBlank(),
                onClick = {
                    onSave(
                        requireNotNull(selectedSelector),
                        preserveUnchangedOrTrim(variableName, initialVariableName),
                        attribute,
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MatchIndexControl(matchIndex: Int, onChange: (Int) -> Unit) {
    val previousMatchDescription = stringResource(R.string.previous_match)
    val nextMatchDescription = stringResource(R.string.next_match)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.match_index, matchIndex + 1), modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onChange(matchIndex - 1) },
            enabled = matchIndex > 0,
            modifier = Modifier.semantics {
                contentDescription = previousMatchDescription
            },
        ) {
            Text("-")
        }
        TextButton(
            onClick = { onChange(matchIndex + 1) },
            enabled = matchIndex + 1 < NodeSelector.MAX_MATCH_COUNT,
            modifier = Modifier.semantics {
                contentDescription = nextMatchDescription
            },
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

@Composable
private fun NodeAttribute.displayName(): String = stringResource(when (this) {
    NodeAttribute.TextOrDescription -> R.string.node_attribute_text_or_description
    NodeAttribute.Text -> R.string.node_attribute_text
    NodeAttribute.ContentDescription -> R.string.node_attribute_content_description
    NodeAttribute.ViewId -> R.string.node_attribute_view_id
    NodeAttribute.ClassName -> R.string.node_attribute_class_name
})

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    val item = removeAt(fromIndex)
    add(toIndex, item)
}

private fun newId(): String = UUID.randomUUID().toString()

@Composable
private fun LaunchAppDialog(
    initialPackageName: String = "",
    initialIntentAction: String? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val launchableApps = remember { LaunchableAppCatalog(context).load() }
    var packageName by remember(initialPackageName) { mutableStateOf(initialPackageName) }
    var intentAction by remember(initialIntentAction) { mutableStateOf(initialIntentAction.orEmpty()) }
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
                HorizontalDivider()
                NodeField(
                    intentAction,
                    { intentAction = it },
                    stringResource(R.string.launch_intent_action),
                )
                Text(
                    stringResource(R.string.launch_intent_action_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = packageName.isNotBlank(),
                onClick = { onAdd(packageName.trim(), normalizedOptionalText(intentAction)) },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

internal fun normalizedOptionalText(value: String): String? = value.trim().ifBlank { null }

internal fun preserveUnchangedOrTrim(value: String, original: String): String =
    if (value == original) original else value.trim()

@Composable
private fun ClickStepDialog(
    observedNodes: List<ObservedNode>,
    initialSelector: NodeSelector? = null,
    titleRes: Int = R.string.step_click_element,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (NodeSelector) -> Unit,
) {
    val context = LocalContext.current
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
    var useAncestor by remember(initialSelector) { mutableStateOf(initialSelector?.ancestor != null) }
    var ancestorViewId by remember(initialSelector) { mutableStateOf(initialSelector?.ancestor?.viewId.orEmpty()) }
    var ancestorText by remember(initialSelector) { mutableStateOf(initialSelector?.ancestor?.text.orEmpty()) }
    var ancestorTextContains by remember(initialSelector) {
        mutableStateOf(initialSelector?.ancestor?.textMatchMode == TextMatchMode.Contains)
    }
    var ancestorDescription by remember(initialSelector) {
        mutableStateOf(initialSelector?.ancestor?.contentDescription.orEmpty())
    }
    var ancestorDescriptionContains by remember(initialSelector) {
        mutableStateOf(initialSelector?.ancestor?.contentDescriptionMatchMode == TextMatchMode.Contains)
    }
    var ancestorClassName by remember(initialSelector) {
        mutableStateOf(initialSelector?.ancestor?.className.orEmpty())
    }
    var matchResult by remember { mutableStateOf<String?>(null) }
    var matchSucceeded by remember { mutableStateOf(false) }
    val hasAttribute = listOf(viewId, text, description, className).any { it.isNotBlank() }
    val hasAncestorAttribute = listOf(
        ancestorViewId,
        ancestorText,
        ancestorDescription,
        ancestorClassName,
    ).any { it.isNotBlank() }
    val selectorIsValid = hasAttribute && (!useAncestor || hasAncestorAttribute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
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
                        matchResult = context.getString(R.string.selector_selected_from_screenshot)
                        matchSucceeded = true
                    },
                    onSelectionError = { matchResult = it },
                )
                HorizontalDivider()
                Text(stringResource(R.string.recent_observed_elements), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (observedNodes.isEmpty()) {
                    Text(
                        stringResource(R.string.selector_observed_elements_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    observedNodes.observedControlCandidates().forEach { node ->
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
                                    context.getString(R.string.selector_unique_auto_selected)
                                } else {
                                    context.getString(R.string.selector_candidate_selected)
                                }
                                matchSucceeded = count == 1
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
                Text(stringResource(R.string.element_attributes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                NodeField(
                    packageName,
                    { packageName = it },
                    stringResource(R.string.selector_package_optional),
                )
                NodeField(viewId, { viewId = it }, stringResource(R.string.resource_id))
                NodeField(text, { text = it }, stringResource(R.string.selector_text))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.text_contains), modifier = Modifier.weight(1f))
                    Switch(
                        checked = textContains,
                        enabled = text.isNotBlank(),
                        onCheckedChange = { textContains = it },
                    )
                }
                NodeField(description, { description = it }, stringResource(R.string.selector_content_description))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.description_contains), modifier = Modifier.weight(1f))
                    Switch(
                        checked = descriptionContains,
                        enabled = description.isNotBlank(),
                        onCheckedChange = { descriptionContains = it },
                    )
                }
                NodeField(className, { className = it }, stringResource(R.string.class_name))
                MatchIndexControl(matchIndex) { matchIndex = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.limit_to_ancestor), modifier = Modifier.weight(1f))
                    Switch(checked = useAncestor, onCheckedChange = { useAncestor = it })
                }
                if (useAncestor) {
                    Text(
                        stringResource(R.string.ancestor_attributes),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    NodeField(
                        ancestorViewId,
                        { ancestorViewId = it },
                        stringResource(R.string.ancestor_resource_id),
                    )
                    NodeField(
                        ancestorText,
                        { ancestorText = it },
                        stringResource(R.string.ancestor_text),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.text_contains), modifier = Modifier.weight(1f))
                        Switch(
                            checked = ancestorTextContains,
                            enabled = ancestorText.isNotBlank(),
                            onCheckedChange = { ancestorTextContains = it },
                        )
                    }
                    NodeField(
                        ancestorDescription,
                        { ancestorDescription = it },
                        stringResource(R.string.ancestor_content_description),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.description_contains), modifier = Modifier.weight(1f))
                        Switch(
                            checked = ancestorDescriptionContains,
                            enabled = ancestorDescription.isNotBlank(),
                            onCheckedChange = { ancestorDescriptionContains = it },
                        )
                    }
                    NodeField(
                        ancestorClassName,
                        { ancestorClassName = it },
                        stringResource(R.string.ancestor_class),
                    )
                }
                if (!hasAttribute) {
                    Text(
                        stringResource(R.string.selector_attribute_required),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                } else if (useAncestor && !hasAncestorAttribute) {
                    Text(
                        stringResource(R.string.ancestor_attribute_required),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                OutlinedButton(
                    enabled = selectorIsValid &&
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
                            useAncestor,
                            ancestorViewId,
                            ancestorText,
                            ancestorTextContains,
                            ancestorDescription,
                            ancestorDescriptionContains,
                            ancestorClassName,
                        )
                        val count = selector?.let {
                            AutomationAccessibilityService.instance?.countMatches(it)
                        } ?: 0
                        matchResult = when (count) {
                            0 -> context.getString(R.string.selector_no_matches)
                            in 1..matchIndex -> context.getString(
                                R.string.selector_index_unavailable,
                                count,
                                matchIndex + 1,
                            )
                            1 -> context.getString(R.string.selector_unique_ready)
                            else -> context.getString(R.string.selector_match_available, count, matchIndex + 1)
                        }
                        matchSucceeded = count > matchIndex
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.test_selector)) }
                matchResult?.let { result ->
                    Text(
                        result,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (matchSucceeded) Color(0xFF16815F) else Color(0xFFD04F3D),
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectorIsValid,
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
                                useAncestor,
                                ancestorViewId,
                                ancestorText,
                                ancestorTextContains,
                                ancestorDescription,
                                ancestorDescriptionContains,
                                ancestorClassName,
                            ),
                        ),
                    )
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ImageClickStepDialog(
    initialStep: Step.ImageClick? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (Step.ImageClick) -> Unit,
) {
    val savedTemplatePreview = remember(initialStep) { initialStep?.let(::decodeImageTemplate) }
    DisposableEffect(savedTemplatePreview) {
        onDispose {
            AutomationAccessibilityService.cancelPendingScreenCapture()
            savedTemplatePreview?.recycle()
        }
    }
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    var packageName by remember(initialStep) { mutableStateOf(initialStep?.packageName.orEmpty()) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var cropLeft by remember { mutableStateOf("") }
    var cropTop by remember { mutableStateOf("") }
    var cropRight by remember { mutableStateOf("") }
    var cropBottom by remember { mutableStateOf("") }
    var minimumScorePercent by remember(initialStep) {
        mutableStateOf(imageMatchPercentText(initialStep?.minimumScorePermille ?: 920))
    }
    var ambiguityMarginPercent by remember(initialStep) {
        mutableStateOf(imageMatchPercentText(initialStep?.ambiguityMarginPermille ?: 25))
    }
    var scaleTolerancePermille by remember(initialStep) {
        mutableStateOf(initialStep?.scaleTolerancePermille ?: 0)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val minimumScorePermille = imageMatchPercentToPermille(minimumScorePercent)
    val ambiguityMarginPermille = imageMatchPercentToPermille(ambiguityMarginPercent)

    LaunchedEffect(captureState) {
        val state = captureState as? ScreenCaptureState.Ready ?: return@LaunchedEffect
        if (packageName.isBlank()) packageName = state.nodes.firstOrNull()?.packageName.orEmpty()
        dragStart = null
        dragEnd = null
        cropLeft = ""
        cropTop = ""
        cropRight = ""
        cropBottom = ""
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
                if (initialStep != null) {
                    Text(
                        stringResource(R.string.image_click_saved_template_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (savedTemplatePreview != null) {
                        Image(
                            bitmap = savedTemplatePreview.asImageBitmap(),
                            contentDescription = stringResource(
                                R.string.image_click_saved_template_description,
                            ),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .testTag(IMAGE_CLICK_SAVED_TEMPLATE_PREVIEW_TAG),
                        )
                        Text(
                            stringResource(
                                R.string.image_click_saved_template,
                                initialStep.templateWidth,
                                initialStep.templateHeight,
                            ),
                            fontSize = 12.sp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.image_click_saved_template_invalid),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    HorizontalDivider()
                }
                NodeField(packageName, { packageName = it }, stringResource(R.string.image_click_package), true)
                HorizontalDivider()
                Text(stringResource(R.string.image_click_matching_settings), fontWeight = FontWeight.SemiBold)
                NodeField(
                    minimumScorePercent,
                    { minimumScorePercent = it },
                    stringResource(R.string.image_click_minimum_similarity),
                    true,
                )
                Text(
                    stringResource(R.string.image_click_minimum_similarity_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                NodeField(
                    ambiguityMarginPercent,
                    { ambiguityMarginPercent = it },
                    stringResource(R.string.image_click_uniqueness_margin),
                    true,
                )
                Text(
                    stringResource(R.string.image_click_uniqueness_margin_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(stringResource(R.string.image_click_scale_tolerance), fontWeight = FontWeight.SemiBold)
                listOf(
                    0 to R.string.image_click_scale_exact,
                    50 to R.string.image_click_scale_five_percent,
                    100 to R.string.image_click_scale_ten_percent,
                ).forEach { (value, labelRes) ->
                    val selected = scaleTolerancePermille == value
                    if (selected) {
                        Button(
                            onClick = { scaleTolerancePermille = value },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(labelRes)) }
                    } else {
                        OutlinedButton(
                            onClick = { scaleTolerancePermille = value },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(labelRes)) }
                    }
                }
                Text(
                    stringResource(R.string.image_click_scale_tolerance_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (minimumScorePermille == null || ambiguityMarginPermille == null) {
                    Text(
                        stringResource(R.string.image_click_percentage_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                HorizontalDivider()
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
                                                val first = dragStart?.let { start ->
                                                    mapFitCenterTapToScreen(
                                                        start.x,
                                                        start.y,
                                                        captureSize.width,
                                                        captureSize.height,
                                                        state.bitmap.width,
                                                        state.bitmap.height,
                                                    )
                                                }
                                                val second = mapFitCenterTapToScreen(
                                                    change.position.x,
                                                    change.position.y,
                                                    captureSize.width,
                                                    captureSize.height,
                                                    state.bitmap.width,
                                                    state.bitmap.height,
                                                )
                                                if (first != null && second != null) {
                                                    cropLeft = minOf(first.x, second.x).toString()
                                                    cropTop = minOf(first.y, second.y).toString()
                                                    cropRight = maxOf(first.x, second.x).toString()
                                                    cropBottom = maxOf(first.y, second.y).toString()
                                                }
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
                        Text(stringResource(R.string.image_click_crop_bounds), fontWeight = FontWeight.SemiBold)
                        NodeField(cropLeft, { cropLeft = it }, stringResource(R.string.crop_left), true)
                        NodeField(cropTop, { cropTop = it }, stringResource(R.string.crop_top), true)
                        NodeField(cropRight, { cropRight = it }, stringResource(R.string.crop_right), true)
                        NodeField(cropBottom, { cropBottom = it }, stringResource(R.string.crop_bottom), true)
                    }
                    ScreenCaptureState.Idle -> initialStep?.let {
                        Text(stringResource(R.string.image_click_saved_template, it.templateWidth, it.templateHeight))
                    }
                }
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = packageName.isNotBlank() && minimumScorePermille != null &&
                    ambiguityMarginPermille != null && (initialStep != null ||
                    captureState is ScreenCaptureState.Ready && cropBoundsOrNull(
                        cropLeft,
                        cropTop,
                        cropRight,
                        cropBottom,
                    ) != null),
                onClick = {
                    val state = captureState as? ScreenCaptureState.Ready
                    val bounds = cropBoundsOrNull(cropLeft, cropTop, cropRight, cropBottom)
                    if (state == null || bounds == null) {
                        initialStep?.let {
                            onAdd(
                                it.copy(
                                    packageName = packageName.trim(),
                                    minimumScorePermille = requireNotNull(minimumScorePermille),
                                    ambiguityMarginPermille = requireNotNull(ambiguityMarginPermille),
                                    scaleTolerancePermille = scaleTolerancePermille,
                                ),
                            )
                        }
                        return@TextButton
                    }
                    val crop = cropTemplate(state.bitmap, bounds)
                    if (crop == null) {
                        error = context.getString(
                            R.string.image_click_crop_too_small,
                            Step.ImageClick.MIN_TEMPLATE_SIZE,
                        )
                    } else {
                        val encoded = encodeTemplatePng(crop)
                        if (crop !== state.bitmap) crop.recycle()
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
                                    minimumScorePermille = requireNotNull(minimumScorePermille),
                                    ambiguityMarginPermille = requireNotNull(ambiguityMarginPermille),
                                    scaleTolerancePermille = scaleTolerancePermille,
                                    timeoutMillis = initialStep?.timeoutMillis,
                                    failurePolicy = initialStep?.failurePolicy ?: FailurePolicy.Stop,
                                ),
                            )
                        }
                    }
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal fun imageMatchPercentText(permille: Int): String = "${permille / 10}.${permille % 10}"

internal fun imageMatchPercentToPermille(value: String): Int? {
    val match = IMAGE_MATCH_PERCENT_PATTERN.matchEntire(value.trim()) ?: return null
    val whole = match.groupValues[1].toInt()
    val decimal = match.groupValues[2].ifEmpty { "0" }.toInt()
    return (whole * 10 + decimal).takeIf { it in 0..1_000 }
}

private val IMAGE_MATCH_PERCENT_PATTERN = Regex("(0|[1-9]\\d?|100)(?:\\.(\\d))?")

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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.primary,
        )
        is ScreenCaptureState.Error -> Text(
            state.message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
    useAncestor: Boolean,
    ancestorViewId: String,
    ancestorText: String,
    ancestorTextContains: Boolean,
    ancestorDescription: String,
    ancestorDescriptionContains: Boolean,
    ancestorClassName: String,
): NodeSelector? {
    if (!selectorHasAttribute(viewId, text, description, className)) return null
    val ancestor = if (useAncestor) {
        if (!selectorHasAttribute(
                ancestorViewId,
                ancestorText,
                ancestorDescription,
                ancestorClassName,
            )
        ) return null
        AncestorSelector(
            viewId = ancestorViewId.trim().ifBlank { null },
            text = ancestorText.trim().ifBlank { null },
            textMatchMode = if (ancestorTextContains) TextMatchMode.Contains else TextMatchMode.Exact,
            contentDescription = ancestorDescription.trim().ifBlank { null },
            contentDescriptionMatchMode = if (ancestorDescriptionContains) {
                TextMatchMode.Contains
            } else {
                TextMatchMode.Exact
            },
            className = ancestorClassName.trim().ifBlank { null },
        )
    } else {
        null
    }
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
        ancestor = ancestor,
    )
}

internal fun selectorHasAttribute(
    viewId: String,
    text: String,
    description: String,
    className: String,
): Boolean = listOf(viewId, text, description, className).any(String::isNotBlank)

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
                stringResource(R.string.step_failure_summary, step.failurePolicy.label()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                step.timeoutMillis?.let {
                    stringResource(R.string.step_timeout_override_summary, it)
                } ?: stringResource(R.string.step_timeout_inherit_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            (step as? Step.LaunchApp)?.intentAction?.let { action ->
                Text(
                    stringResource(R.string.launch_intent_action_summary, action),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        Column {
            if (onOpenRepeat != null) {
                TextButton(onClick = onOpenRepeat) { Text(stringResource(R.string.open_steps)) }
            }
            if (onOpenIfTrue != null || onOpenIfFalse != null) {
                Row {
                    onOpenIfTrue?.let { open ->
                        TextButton(onClick = open) { Text(stringResource(R.string.when_true)) }
                    }
                    onOpenIfFalse?.let { open ->
                        TextButton(onClick = open) { Text(stringResource(R.string.when_false)) }
                    }
                }
            }
            Row {
                TextButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "up")),
                ) { Text(stringResource(R.string.move_up)) }
                TextButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "down")),
                ) { Text(stringResource(R.string.move_down)) }
            }
            Row {
                TextButton(
                    onClick = onEdit,
                    enabled = canEdit,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "edit")),
                ) { Text(stringResource(R.string.edit)) }
                TextButton(
                    onClick = onEditPolicy,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "settings")),
                ) { Text(stringResource(R.string.settings_title)) }
            }
            Row {
                TextButton(
                    onClick = onDuplicate,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "duplicate")),
                ) { Text(stringResource(R.string.duplicate)) }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag(stepOperationTag(step.id, "delete")),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun WorkflowOperationChooserDialog(
    hasSteps: Boolean,
    serviceConnected: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WorkflowEditorOperation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_operation)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WorkflowEditorOperation.entries.forEach { operation ->
                    OutlinedButton(
                        enabled = operation.isAvailable(hasSteps, serviceConnected),
                        onClick = { onSelect(operation) },
                        modifier = Modifier.fillMaxWidth().testTag(workflowOperationTag(operation)),
                    ) { Text(operation.localizedLabel()) }
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
private fun WorkflowEditorOperation.localizedLabel(): String = stringResource(
    when (this) {
        WorkflowEditorOperation.LaunchApp -> R.string.launch_app
        WorkflowEditorOperation.Click -> R.string.click
        WorkflowEditorOperation.ImageClick -> R.string.image_click
        WorkflowEditorOperation.RecordedClick -> R.string.monitor_elements_overlay
        WorkflowEditorOperation.LongClick -> R.string.long_click
        WorkflowEditorOperation.Tap -> R.string.tap_coordinates
        WorkflowEditorOperation.Scroll -> R.string.scroll_element
        WorkflowEditorOperation.InputText -> R.string.input_text
        WorkflowEditorOperation.Swipe -> R.string.swipe
        WorkflowEditorOperation.Delay -> R.string.wait_action
        WorkflowEditorOperation.GlobalBack -> R.string.back
        WorkflowEditorOperation.GlobalHome -> R.string.home
        WorkflowEditorOperation.GlobalRecents -> R.string.recents
        WorkflowEditorOperation.WaitForNode -> R.string.wait_for_element
        WorkflowEditorOperation.SetVariable -> R.string.set_variable
        WorkflowEditorOperation.ReadNodeText -> R.string.read_element_attribute
        WorkflowEditorOperation.Repeat -> R.string.repeat_steps
        WorkflowEditorOperation.VariableCondition -> R.string.variable_condition
        WorkflowEditorOperation.NodeCondition -> R.string.element_exists_condition
    },
)

@Composable
private fun FailurePolicy.label(): String = when (this) {
    FailurePolicy.Stop -> stringResource(R.string.failure_policy_stop)
    FailurePolicy.Continue -> stringResource(R.string.failure_policy_continue)
    is FailurePolicy.Retry -> stringResource(R.string.failure_policy_retry, attempts)
}

private fun Step.isActionEditable(): Boolean = when (this) {
    is Step.Click, is Step.RecordedClick, is Step.Delay, is Step.GlobalAction, is Step.InputText, is Step.LaunchApp,
    is Step.ImageClick,
    is Step.LongClick, is Step.ReadNodeText, is Step.Repeat, is Step.SetVariable, is Step.Swipe,
    is Step.WaitForNode -> true
    is Step.Scroll, is Step.Tap -> true
    is Step.IfElse -> true
}

@Composable
private fun Step.title(): String = when (this) {
    is Step.Click -> stringResource(R.string.step_click_element)
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
    is Step.Delay -> stringResource(R.string.step_delay, durationMillis)
    is Step.GlobalAction -> action.displayName()
    is Step.IfElse -> when (val current = condition) {
        is Condition.Equals -> stringResource(R.string.step_if_variable, current.operator.displayName())
        is Condition.NodeExists -> stringResource(R.string.if_element_exists)
    }
    is Step.InputText -> {
        val source = variableName?.let { stringResource(R.string.variable_value, it) }
            ?: stringResource(R.string.literal_text)
        stringResource(if (inputMethod == TextInputMethod.Paste) R.string.step_paste else R.string.step_input, source)
    }
    is Step.Repeat -> stringResource(R.string.step_repeat, times)
    is Step.Scroll -> stringResource(if (direction == ScrollDirection.Forward) R.string.scroll_backward else R.string.scroll_forward)
    is Step.LaunchApp -> stringResource(R.string.step_launch_app, packageName)
    is Step.LongClick -> stringResource(R.string.long_click_element)
    is Step.ReadNodeText -> stringResource(R.string.step_read_attribute, attribute.displayName(), variableName)
    is Step.SetVariable -> stringResource(R.string.step_set_variable, name)
    is Step.Swipe -> stringResource(R.string.step_swipe, startX, startY, endX, endY)
    is Step.Tap -> stringResource(R.string.step_tap, x, y)
    is Step.WaitForNode -> stringResource(if (mustExist) R.string.wait_element_appear else R.string.wait_element_disappear)
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
    debugPaused: Boolean,
    onNext: () -> Unit,
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
                Text(stringResource(R.string.running_workflow, workflowName), fontWeight = FontWeight.SemiBold)
                Text(
                    stepName?.let { stringResource(R.string.running_current_step, it) }
                        ?: stringResource(R.string.running_preparing_first_step),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
                Text(
                    stringResource(R.string.running_elapsed, formatElapsed(elapsedMillis)),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
            }
            if (debugPaused) {
                Button(onClick = onNext) { Text(stringResource(R.string.debug_next_step)) }
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.stop)) }
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
internal fun AccessibilityDisclosureDialog(
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.accessibility_disclosure_observation),
                )
                Text(
                    stringResource(R.string.accessibility_disclosure_screenshot),
                )
                Text(
                    stringResource(R.string.accessibility_disclosure_actions),
                )
                Text(
                    stringResource(R.string.accessibility_disclosure_privacy),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.continue_to_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.not_now)) }
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
                stringResource(
                    if (connected) R.string.automation_service_ready else R.string.automation_service_disabled,
                ),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    if (connected) {
                        R.string.automation_service_ready_description
                    } else {
                        R.string.automation_service_disabled_description
                    },
                ),
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
                ) { Text(stringResource(R.string.settings_title)) }
            }
            TextButton(onClick = onReviewDisclosure) { Text(stringResource(R.string.details)) }
        }
    }
}


@Composable
private fun WorkflowRow(
    workflow: Workflow,
    isRunning: Boolean,
    isAnotherWorkflowRunning: Boolean,
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
    onDebug: () -> Unit,
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
                stringResource(
                    R.string.workflow_row_summary,
                    workflow.effectiveState().displayName(),
                    workflow.steps.size,
                ),
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
            if (isAnotherWorkflowRunning && isReady) {
                Text(
                    stringResource(R.string.another_workflow_running),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row {
                TextButton(onClick = onEdit, enabled = !isRunning) { Text(stringResource(R.string.edit)) }
                TextButton(
                    onClick = onDebug,
                    enabled = !isRunning && !isAnotherWorkflowRunning && isReady,
                    modifier = Modifier.testTag(workflowDebugTag(workflow.id)),
                ) {
                    Text(stringResource(R.string.debug_workflow))
                }
                Button(
                    onClick = if (isRunning) onStop else onRun,
                    enabled = isRunning || isReady && !isAnotherWorkflowRunning,
                    modifier = Modifier.testTag(workflowRunTag(workflow.id)),
                ) {
                    Text(stringResource(if (isRunning) R.string.stop else R.string.run_action))
                }
            }
            Row {
                TextButton(onClick = onExport, enabled = !isRunning) { Text(stringResource(R.string.export)) }
                TextButton(onClick = onDuplicate, enabled = !isRunning) {
                    Text(stringResource(R.string.duplicate))
                }
                TextButton(
                    onClick = onMove,
                    enabled = !isRunning,
                    modifier = Modifier.testTag(moveTag),
                ) {
                    Text(stringResource(R.string.move_to_folder))
                }
                TextButton(onClick = onDelete, enabled = !isRunning) { Text(stringResource(R.string.delete)) }
            }
            TextButton(onClick = onCompare, enabled = !isRunning && canCompare) {
                Text(stringResource(R.string.compare_workflow))
            }
            TextButton(onClick = onViewVersions, enabled = !isRunning) {
                Text(stringResource(R.string.workflow_version_history))
            }
            TextButton(onClick = onPreflight, enabled = !isRunning) { Text(stringResource(R.string.preflight)) }
            TextButton(
                onClick = if (schedule == null) onSchedule else onCancelSchedule,
                enabled = !isRunning && (schedule != null || isReady),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (schedule == null) R.string.schedule else R.string.cancel_schedule))
            }
        }
    }
}

internal const val FOLDER_MANAGE_TAG = "folder-manage"
internal const val WORKFLOW_EDITOR_ALL_ACTIONS_TAG = "workflow-editor-all-actions"
internal const val WORKFLOW_NAME_INPUT_TAG = "workflow-name-input"
internal const val WORKFLOW_EDITOR_BACK_TAG = "workflow-editor-back"
internal const val SCHEDULE_NOTIFICATION_RECOVERY_TAG = "schedule-notification-recovery"
internal const val SCHEDULE_NOTIFICATION_SETTINGS_TAG = "schedule-notification-settings"
internal const val RUN_HISTORY_DETAILS_SCROLL_TAG = "run-history-details-scroll"
internal const val IMAGE_CLICK_SAVED_TEMPLATE_PREVIEW_TAG = "image-click-saved-template-preview"
internal const val WORKFLOW_EDITOR_ADD_OPERATION_TAG = "workflow-editor-add-operation"
internal fun workflowOperationTag(operation: WorkflowEditorOperation) =
    "workflow-operation-${operation.name}"
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
internal fun workflowRunTag(workflowId: String) = "workflow-run-$workflowId"
internal fun workflowDebugTag(workflowId: String) = "workflow-debug-$workflowId"
internal fun folderDestinationTag(folderId: String) = "folder-destination-$folderId"
internal fun stepOperationTag(stepId: String, operation: String) = "step-$stepId-$operation"

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
internal fun PreflightReportDialog(
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
                                PreflightRecoveryAction.OpenNotificationSettings -> stringResource(
                                    R.string.open_notification_settings,
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
internal fun RunRecordDetailsDialog(
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag(RUN_HISTORY_DETAILS_SCROLL_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
private fun SettingsScreen(
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onReviewAccessibilityDisclosure: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.settings_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                stringResource(R.string.settings_appearance),
                fontWeight = FontWeight.SemiBold,
            )
            Column(Modifier.selectableGroup()) {
                AppearanceMode.entries.forEach { mode ->
                    val selectedMode = appearanceMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode,
                                role = Role.RadioButton,
                                onClick = { onAppearanceModeChanged(mode) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedMode,
                            onClick = null,
                        )
                        Text(mode.localizedName())
                    }
                }
            }
            Text(
                stringResource(R.string.settings_appearance_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.settings_automation_access),
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_open_accessibility)) }
            TextButton(
                onClick = onReviewAccessibilityDisclosure,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_review_disclosure)) }
        }
    }
}

@Composable
private fun AppearanceMode.localizedName(): String = stringResource(
    when (this) {
        AppearanceMode.System -> R.string.settings_appearance_system
        AppearanceMode.Light -> R.string.settings_appearance_light
        AppearanceMode.Dark -> R.string.settings_appearance_dark
    },
)

@Composable
internal fun AiIndexFingerTheme(
    appearanceMode: AppearanceMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = appearanceMode.usesDarkTheme(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF78D6B6),
                secondary = Color(0xFF9BCDC0),
                background = Color(0xFF111613),
                surface = Color(0xFF171D1A),
                onSurface = Color(0xFFE1E9E4),
                onSurfaceVariant = Color(0xFFBAC7C1),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF116B56),
                secondary = Color(0xFF3C6257),
                background = Color(0xFFF4F6F1),
                surface = Color.White,
                onSurface = Color(0xFF18201D),
                onSurfaceVariant = Color(0xFF5B6863),
            )
        },
        content = content,
    )
}

internal fun AppearanceMode.usesDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    AppearanceMode.System -> systemInDarkTheme
    AppearanceMode.Light -> false
    AppearanceMode.Dark -> true
}

internal fun List<ObservedNode>.observedControlCandidates(
    limit: Int = MAX_VISIBLE_OBSERVED_NODES,
): List<ObservedNode> = withIndex()
    .sortedWith(
        compareByDescending<IndexedValue<ObservedNode>> {
            it.value.clickable || it.value.longClickable || it.value.scrollable
        }.thenByDescending {
            it.value.viewId != null || it.value.text != null || it.value.contentDescription != null
        }.thenBy { it.index },
    )
    .take(limit)
    .map { it.value }

private const val MAX_VISIBLE_OBSERVED_NODES = 100