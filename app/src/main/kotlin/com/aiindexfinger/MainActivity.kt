package com.aiindexfinger

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.automation.LaunchableAppCatalog
import com.aiindexfinger.automation.LaunchTargetStatus
import com.aiindexfinger.automation.openRunningNotificationSettings
import com.aiindexfinger.automation.runningNotificationReadiness
import com.aiindexfinger.automation.WorkflowStartResult
import com.aiindexfinger.automation.EXTRA_RUN_RECORD_ID
import com.aiindexfinger.automation.normalizedLaunchTarget
import com.aiindexfinger.automation.applyLiveActionHandoff
import com.aiindexfinger.automation.cropBoundsOrNull
import com.aiindexfinger.automation.mapBitmapCropToTargetScreen
import com.aiindexfinger.automation.cropTemplate
import com.aiindexfinger.automation.encodeTemplatePng
import com.aiindexfinger.automation.decodeImageTemplate
import com.aiindexfinger.automation.imageTemplateIsValid
import com.aiindexfinger.automation.filterLaunchableApps
import com.aiindexfinger.automation.ObservedNode
import com.aiindexfinger.automation.SelectorRecommendations
import com.aiindexfinger.automation.ScreenCaptureState
import com.aiindexfinger.automation.ScreenBounds
import com.aiindexfinger.automation.ScreenPoint
import com.aiindexfinger.automation.mapBitmapPointToScreen
import com.aiindexfinger.automation.mapFitCenterTapToScreen
import com.aiindexfinger.automation.recommendedSelector
import com.aiindexfinger.automation.selectCaptureNode
import com.aiindexfinger.automation.templatePointRelativeToCrop
import com.aiindexfinger.automation.PendingOverlayAction
import com.aiindexfinger.automation.PreflightRecoveryAction
import com.aiindexfinger.automation.WorkflowPreflightReport
import com.aiindexfinger.automation.buildWorkflowPreflightReport
import com.aiindexfinger.automation.recoveryActions
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.clearRunHistory
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStepBranch
import com.aiindexfinger.data.RunStepDiagnostic
import com.aiindexfinger.data.RunStepLocation
import com.aiindexfinger.data.uniqueRunLocationTo
import com.aiindexfinger.data.runLocationsTo
import com.aiindexfinger.data.RunHistoryLoadResult
import com.aiindexfinger.data.InvalidWorkflowException
import com.aiindexfinger.data.RunStepOutcome
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE
import com.aiindexfinger.data.filterRunRecords
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowImportSaveException
import com.aiindexfinger.data.WorkflowLibraryCommit
import com.aiindexfinger.data.WorkflowRollbackCommit
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
import com.aiindexfinger.data.WorkflowTransferErrorCode
import com.aiindexfinger.data.WorkflowTransferException
import com.aiindexfinger.data.WorkflowVersion
import com.aiindexfinger.data.resolveRunHistoryDestination
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.ImageClickSelectionMode
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.AncestorSelector
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.RecordedClickTargetMode
import com.aiindexfinger.model.RecordedClickFallbackCause
import com.aiindexfinger.model.ReadNodeTextCaseTransform
import com.aiindexfinger.model.ReadNodeTextPostProcess
import com.aiindexfinger.model.StepComparisonBranch
import com.aiindexfinger.model.StepComparisonField
import com.aiindexfinger.model.StepComparisonPath
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.ScrollUntilStopCondition
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
import com.aiindexfinger.model.renameLabel
import com.aiindexfinger.model.stepAt
import com.aiindexfinger.model.stepsAt
import com.aiindexfinger.model.wrapRangeInRepeat
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
import com.aiindexfinger.scheduler.ScheduleTimeError
import com.aiindexfinger.scheduler.ScheduleTimeException
import com.aiindexfinger.scheduler.ScheduledWorkflowEvent
import com.aiindexfinger.scheduler.ScheduledWorkflowEventController
import com.aiindexfinger.scheduler.WorkflowSchedule
import com.aiindexfinger.scheduler.WorkflowScheduleValidationException
import com.aiindexfinger.scheduler.WorkflowScheduler
import com.aiindexfinger.scheduler.localScheduleEpochMillis
import com.aiindexfinger.scheduler.runnableWorkflowsForScheduling
import com.aiindexfinger.scheduler.missedSchedules
import com.aiindexfinger.scheduler.scheduleDelayMillis
import com.aiindexfinger.scheduler.scheduleNotificationAction
import com.aiindexfinger.scheduler.scheduleNotificationReadiness
import com.aiindexfinger.scheduler.openScheduleNotificationSettings
import com.aiindexfinger.scheduler.removeTriggeredSchedule
import com.aiindexfinger.executor.ExecutionError
import com.aiindexfinger.executor.ExecutionErrorCode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
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
    private val appPreferences by lazy { AppPreferences(this) }
    private val accessibilityDisclosurePreferences by lazy {
        AccessibilityDisclosurePreferences(this)
    }
    private val scheduledWorkflowEvents = ScheduledWorkflowEventController()
    private var requestedRunRecordId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        scheduledWorkflowEvents.publish(intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID))
        requestedRunRecordId = intent.getStringExtra(EXTRA_RUN_RECORD_ID)
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
                    val initialRunMessage = state.loadMessageResources
                        .map { resourceId -> stringResource(resourceId) }
                        .joinToString("\n")
                    WorkflowApp(
                        initialLibrary = state.library,
                        initialRunRecords = state.runRecords,
                        initialRunHistoryCorrupt = state.runHistoryCorrupt,
                        initialSchedules = state.schedules,
                        initialRunMessage = initialRunMessage.ifBlank { null },
                        scheduledWorkflowEvent = scheduledWorkflowEvent,
                        onScheduledWorkflowEventConsumed = scheduledWorkflowEvents::consume,
                        requestedRunRecordId = requestedRunRecordId,
                        onRunRecordRequestConsumed = { recordId ->
                            if (requestedRunRecordId == recordId) requestedRunRecordId = null
                            if (intent.getStringExtra(EXTRA_RUN_RECORD_ID) == recordId) {
                                intent.removeExtra(EXTRA_RUN_RECORD_ID)
                            }
                        },
                        onUpdateLibrary = workflowPersistence::updateLibrary,
                        onDeleteWorkflow = workflowPersistence::deleteWorkflow,
                        onCommitWorkflow = workflowPersistence::commitWorkflow,
                        onCommitImport = workflowPersistence::importLibrary,
                        onListVersions = workflowPersistence::listVersions,
                        onRollback = workflowPersistence::rollback,
                        onClearRunHistory = {
                            withContext(Dispatchers.IO) { runHistoryStore.clear() }
                        },
                        onSchedule = workflowPersistence::scheduleWorkflow,
                        onCancelSchedule = workflowPersistence::cancelWorkflowSchedule,
                        onReloadSchedules = workflowPersistence::reloadWorkflowSchedules,
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

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private suspend fun loadInitialState(): InitialAppState {
        val workflowResult = workflowPersistence.loadCanonicalLibrary()
        val runHistoryResult = runHistoryStore.loadDetailed()
        val library = workflowResult.library
        val runnableWorkflows = runnableWorkflowsForScheduling(workflowResult)
        val scheduleLoad = loadSchedulesForStartup(
            reconcile = {
                if (runnableWorkflows == null) {
                    workflowPersistence.loadWorkflowSchedulesWithoutReconciliation()
                } else {
                    workflowPersistence.reloadWorkflowSchedules(
                        runnableWorkflows.mapTo(mutableSetOf(), Workflow::id),
                    )
                }
            },
            loadWithoutReconciliation =
                workflowPersistence::loadWorkflowSchedulesWithoutReconciliation,
        )
        val loadedSchedules = scheduleLoad.schedules
        val missed = missedSchedules(loadedSchedules)
        var schedules = loadedSchedules
        var scheduleIssue = scheduleLoad.issue
        if (runnableWorkflows != null) {
            for (schedule in missed) {
                try {
                    schedules = workflowPersistence.consumeMissedWorkflowSchedule(schedule.workflowId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: ScheduleStorageException) {
                    scheduleIssue = ScheduleStartupIssue.StorageCorrupt
                    break
                } catch (_: Exception) {
                    scheduleIssue = ScheduleStartupIssue.ReconciliationFailed
                    break
                }
            }
        }
        val hasMissedSchedule = missed.isNotEmpty()
        return InitialAppState(
            library = library,
            runRecords = runHistoryResult.records,
            runHistoryCorrupt = runHistoryResult is RunHistoryLoadResult.Corrupt,
            schedules = schedules,
            loadMessageResources = buildList {
                when (workflowResult) {
                    is WorkflowLoadResult.RecoveredFromBackup -> add(R.string.workflows_recovered_from_backup)
                    is WorkflowLoadResult.Corrupt -> add(R.string.workflows_corrupt)
                    is WorkflowLoadResult.UnsupportedVersion -> add(R.string.workflows_unsupported_version)
                    else -> Unit
                }
                scheduleIssue?.let { issue ->
                    add(
                        when (issue) {
                            ScheduleStartupIssue.StorageCorrupt -> R.string.schedule_storage_corrupt
                            ScheduleStartupIssue.ReconciliationFailed ->
                                R.string.schedule_reconciliation_failed
                        },
                    )
                }
                when (runHistoryResult) {
                    is RunHistoryLoadResult.Corrupt -> add(R.string.run_history_storage_corrupt)
                    is RunHistoryLoadResult.Loaded -> if (runHistoryResult.readOnly) {
                        add(R.string.run_history_newer_read_only)
                    }
                    else -> Unit
                }
                if (hasMissedSchedule) add(R.string.schedule_notification_missed)
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduledWorkflowEvents.publish(intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID))
        requestedRunRecordId = intent.getStringExtra(EXTRA_RUN_RECORD_ID)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

}

internal enum class ScheduleStartupIssue {
    StorageCorrupt,
    ReconciliationFailed,
}

internal enum class ScheduledWorkflowAvailability {
    Missing,
    NotReady,
    Ready,
}

internal fun scheduledWorkflowAvailability(
    workflows: List<Workflow>,
    workflowId: String,
): ScheduledWorkflowAvailability {
    val workflow = workflows.firstOrNull { it.id == workflowId }
        ?: return ScheduledWorkflowAvailability.Missing
    return if (workflow.isReadyToRun()) {
        ScheduledWorkflowAvailability.Ready
    } else {
        ScheduledWorkflowAvailability.NotReady
    }
}

internal data class ScheduleStartupLoad(
    val schedules: List<WorkflowSchedule>,
    val issue: ScheduleStartupIssue? = null,
)

internal suspend fun loadSchedulesForStartup(
    reconcile: suspend () -> List<WorkflowSchedule>,
    loadWithoutReconciliation: suspend () -> List<WorkflowSchedule>,
): ScheduleStartupLoad = try {
    ScheduleStartupLoad(reconcile())
} catch (error: CancellationException) {
    throw error
} catch (_: ScheduleStorageException) {
    ScheduleStartupLoad(emptyList(), ScheduleStartupIssue.StorageCorrupt)
} catch (_: Exception) {
    try {
        ScheduleStartupLoad(
            loadWithoutReconciliation(),
            ScheduleStartupIssue.ReconciliationFailed,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: ScheduleStorageException) {
        ScheduleStartupLoad(emptyList(), ScheduleStartupIssue.StorageCorrupt)
    } catch (_: Exception) {
        ScheduleStartupLoad(emptyList(), ScheduleStartupIssue.ReconciliationFailed)
    }
}

private data class InitialAppState(
    val library: WorkflowLibrary,
    val runRecords: List<RunRecord>,
    val runHistoryCorrupt: Boolean,
    val schedules: List<WorkflowSchedule>,
    val loadMessageResources: List<Int>,
)

@Composable
private fun WorkflowApp(
    initialLibrary: WorkflowLibrary,
    initialRunRecords: List<RunRecord>,
    initialRunHistoryCorrupt: Boolean,
    initialSchedules: List<WorkflowSchedule>,
    initialRunMessage: String?,
    scheduledWorkflowEvent: ScheduledWorkflowEvent?,
    onScheduledWorkflowEventConsumed: (Long) -> Unit,
    requestedRunRecordId: String?,
    onRunRecordRequestConsumed: (String) -> Unit,
    onUpdateLibrary: suspend ((WorkflowLibrary) -> WorkflowLibrary) -> WorkflowLibrary,
    onDeleteWorkflow: suspend (String) -> WorkflowLibraryCommit<List<WorkflowSchedule>>,
    onCommitWorkflow: suspend (Workflow?, Workflow) -> WorkflowLibraryCommit<List<WorkflowSchedule>>,
    onCommitImport: suspend (WorkflowLibrary) -> WorkflowLibrary,
        onListVersions: suspend (String) -> List<WorkflowVersion>,
        onRollback: suspend (String, String) -> WorkflowRollbackCommit<List<WorkflowSchedule>>,
    onClearRunHistory: suspend () -> Unit,
    onSchedule: suspend (Workflow, Long, ScheduleRecurrence) -> List<WorkflowSchedule>,
    onCancelSchedule: suspend (String) -> List<WorkflowSchedule>,
    onReloadSchedules: suspend (Set<String>) -> List<WorkflowSchedule>,
    onOpenAccessibilitySettings: () -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    accessibilityDisclosureAcknowledged: Boolean,
    onAccessibilityDisclosureAcknowledged: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var library by remember { mutableStateOf(initialLibrary) }
    val workflowApplication = context.applicationContext as AiIndexFingerApplication
    val canonicalLibrary by workflowApplication.library.collectAsStateWithLifecycle()
    LaunchedEffect(canonicalLibrary) {
        canonicalLibrary?.let { latest ->
            if (latest != library) library = latest
        }
    }
    val workflows = library.workflows
    val latestWorkflows = { workflowApplication.library.value?.workflows ?: workflows }
    var runRecords by remember { mutableStateOf(initialRunRecords) }
    var runHistoryCorrupt by remember { mutableStateOf(initialRunHistoryCorrupt) }
    var schedules by remember { mutableStateOf(initialSchedules) }
    var editingWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var initialEditingStepPath by remember { mutableStateOf<StepPath?>(null) }
    var showRunHistory by rememberSaveable { mutableStateOf(false) }
    var requestedHistoryRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var workflowComparison by remember { mutableStateOf<Pair<Workflow, Workflow>?>(null) }
        var versionHistory by remember { mutableStateOf<Pair<Workflow, List<WorkflowVersion>>?>(null) }
    var runMessage by remember { mutableStateOf(initialRunMessage) }
    fun persist(
        update: (WorkflowLibrary) -> WorkflowLibrary,
        onSuccess: (WorkflowLibrary) -> Unit = {},
    ) {
        coroutineScope.launch {
            try {
                val persisted = onUpdateLibrary(update)
                library = persisted
                onSuccess(persisted)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                runMessage = context.getString(R.string.save_failed)
            }
        }
    }
    var editorSaveError by remember { mutableStateOf<String?>(null) }
    var editorSaveInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(requestedRunRecordId) {
        requestedRunRecordId?.let { recordId ->
            requestedHistoryRecordId = recordId
            showRunHistory = true
            onRunRecordRequestConsumed(recordId)
        }
    }
    LaunchedEffect(editingWorkflow?.id) {
        editorSaveError = null
        editorSaveInProgress = false
    }
    var preflightReport by remember { mutableStateOf<Pair<Workflow, WorkflowPreflightReport>?>(null) }
    val runningWorkflowId by AutomationAccessibilityService.runningWorkflowId.collectAsStateWithLifecycle()
    val latestRun by AutomationAccessibilityService.latestRun.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Workflow?>(null) }
    var pendingBundleExport by remember { mutableStateOf<WorkflowLibrary?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val currentLocale = LocalConfiguration.current.locales[0]
    val preflightDisplaySize = currentDisplayPixelSize()
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
    val openTutorial: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(selectedTutorialUrl(currentLocale)),
                ),
            )
        }.onFailure {
            runMessage = context.getString(R.string.open_tutorial_failed)
        }
        Unit
    }
    LaunchedEffect(scheduledWorkflowEvent?.sequence) {
        scheduledWorkflowEvent?.let { event ->
            val id = event.workflowId
            try {
                schedules = onReloadSchedules(
                    workflows.filter { it.isReadyToRun() }.map { it.id }.toSet(),
                )
                val workflow = workflows.firstOrNull { it.id == id }
                runMessage = when (scheduledWorkflowAvailability(workflows, id)) {
                    ScheduledWorkflowAvailability.Ready -> context.getString(
                        R.string.workflow_ready_to_run,
                        requireNotNull(workflow).name,
                    )
                    ScheduledWorkflowAvailability.NotReady -> context.getString(
                        R.string.cannot_run,
                        requireNotNull(workflow).readinessIssues().first().localizedMessage(context),
                    )
                    ScheduledWorkflowAvailability.Missing ->
                        context.getString(R.string.run_workflow_missing)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: ScheduleStorageException) {
                runMessage = context.getString(R.string.schedule_storage_corrupt)
            } catch (_: Exception) {
                runMessage = context.getString(R.string.schedule_reconciliation_failed)
            }
            onScheduledWorkflowEventConsumed(event.sequence)
        }
    }
    LaunchedEffect(latestRun?.record?.id) {
        latestRun?.let { outcome ->
            runRecords = (listOf(outcome.record) + runRecords)
                .distinctBy { it.id }
                .take(100)
            val outcomeMessage = when {
                outcome.record.status == RunStatus.CompletedWithWarnings ->
                    context.getString(R.string.run_completed_with_warnings)
                outcome.record.failureCode == RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE ->
                    context.getString(R.string.run_cancelled_controls_unavailable)
                else -> outcome.result.localizedMessage(context, outcome.record.failedStepLocation)
            }
            runMessage = if (outcome.historyWriteFailed) {
                context.getString(R.string.run_result_history_not_saved, outcomeMessage)
            } else {
                outcomeMessage
            }
        }
    }
    var pendingSchedule by remember { mutableStateOf<Triple<Workflow, Long, ScheduleRecurrence>?>(null) }
    var pendingRunRequest by remember { mutableStateOf<Pair<Workflow, Boolean>?>(null) }
    var blockedNotificationReadiness by remember { mutableStateOf<ScheduleNotificationReadiness?>(null) }
    var blockedRunNotificationReadiness by remember {
        mutableStateOf<ScheduleNotificationReadiness?>(null)
    }
    val persistSchedule: suspend (Triple<Workflow, Long, ScheduleRecurrence>) -> ScheduleTimeError? = { request ->
        try {
            schedules = onSchedule(request.first, request.second, request.third)
            runMessage = context.getString(R.string.workflow_scheduled, request.first.name)
            null
        } catch (error: ScheduleTimeException) {
            runMessage = context.getString(error.error.messageResourceId())
            error.error
        } catch (error: WorkflowScheduleValidationException) {
            runMessage = context.getString(
                R.string.cannot_schedule,
                error.issue.localizedMessage(context),
            )
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runMessage = context.getString(
                if (error is ScheduleStorageException) {
                    R.string.schedule_storage_corrupt
                } else {
                    R.string.schedule_failed
                },
            )
            null
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingSchedule
        pendingSchedule = null
        val currentWorkflow = request?.first?.id?.let { workflowId ->
            latestWorkflows().firstOrNull { it.id == workflowId }
        }
        if (granted && request != null && currentWorkflow?.isReadyToRun() == true) {
            val readiness = scheduleNotificationReadiness(context)
            if (readiness == ScheduleNotificationReadiness.Ready) {
                coroutineScope.launch { persistSchedule(request.copy(first = currentWorkflow)) }
            } else {
                blockedNotificationReadiness = readiness
                runMessage = context.getString(R.string.schedule_notifications_blocked)
            }
        } else if (granted && request != null && currentWorkflow == null) {
            runMessage = context.getString(R.string.schedule_workflow_missing)
        } else if (granted && request != null && currentWorkflow != null) {
            runMessage = context.getString(
                R.string.cannot_schedule,
                currentWorkflow.readinessIssues().first().localizedMessage(context),
            )
        } else if (!granted) {
            blockedNotificationReadiness = ScheduleNotificationReadiness.RuntimePermissionRequired
            runMessage = context.getString(R.string.schedule_requires_notifications)
        }
    }
    val startRunRequest: (Workflow, Boolean) -> Unit = { workflow, debug ->
        val service = AutomationAccessibilityService.instance
        if (service == null) {
            runMessage = context.getString(R.string.enable_automation_before_run)
            requestAccessibilitySetup()
        } else {
            runMessage = when (service.startWorkflowDetailed(workflow, debug)) {
                WorkflowStartResult.Started -> if (debug) {
                    context.getString(R.string.debugging_workflow, workflow.name)
                } else {
                    context.getString(R.string.running_workflow, workflow.name)
                }
                WorkflowStartResult.NotReady -> context.getString(
                    R.string.cannot_run,
                    workflow.readinessIssues().first().localizedMessage(context),
                )
                WorkflowStartResult.AlreadyRunning ->
                    context.getString(R.string.another_workflow_running)
                WorkflowStartResult.ControlsUnavailable ->
                    context.getString(R.string.run_controls_unavailable)
                WorkflowStartResult.DebuggerUnavailable ->
                    context.getString(R.string.debug_overlay_unavailable)
                WorkflowStartResult.ServiceUnavailable ->
                    context.getString(R.string.enable_automation_before_run)
            }
        }
    }
    val runNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingRunRequest
        pendingRunRequest = null
        val currentWorkflow = request?.first?.id?.let { workflowId ->
            latestWorkflows().firstOrNull { it.id == workflowId }
        }
        when {
            !granted -> {
                blockedRunNotificationReadiness =
                    ScheduleNotificationReadiness.RuntimePermissionRequired
                runMessage = context.getString(R.string.run_requires_notifications)
            }
            request == null -> Unit
            currentWorkflow == null ->
                runMessage = context.getString(R.string.run_workflow_missing)
            !currentWorkflow.isReadyToRun() -> runMessage = context.getString(
                R.string.cannot_run,
                currentWorkflow.readinessIssues().first().localizedMessage(context),
            )
            runningNotificationReadiness(context) != ScheduleNotificationReadiness.Ready ->
                runMessage = context.getString(R.string.run_notifications_blocked)
            else -> startRunRequest(currentWorkflow, request.second)
        }
    }
    val requestWorkflowRun: (Workflow, Boolean) -> Unit = { workflow, debug ->
        val currentWorkflow = latestWorkflows().firstOrNull { it.id == workflow.id }
        val issue = currentWorkflow?.readinessIssues()?.firstOrNull()
        when {
            currentWorkflow == null ->
                runMessage = context.getString(R.string.run_workflow_missing)
            issue != null -> runMessage = context.getString(
                R.string.cannot_run,
                issue.localizedMessage(context),
            )
            AutomationAccessibilityService.instance == null -> {
                runMessage = context.getString(R.string.enable_automation_before_run)
                requestAccessibilitySetup()
            }
            else -> when (val readiness = runningNotificationReadiness(context)) {
                ScheduleNotificationReadiness.Ready -> startRunRequest(currentWorkflow, debug)
                ScheduleNotificationReadiness.RuntimePermissionRequired -> {
                    pendingRunRequest = currentWorkflow to debug
                    runNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ScheduleNotificationReadiness.AppNotificationsDisabled,
                ScheduleNotificationReadiness.ChannelDisabled -> {
                    runMessage = context.getString(
                        if (openRunningNotificationSettings(context, readiness)) {
                            R.string.run_notifications_blocked
                        } else {
                            R.string.run_notification_settings_unavailable
                        },
                    )
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val requestedWorkflow = pendingExport
        pendingExport = null
        val workflow = requestedWorkflow?.let { requested ->
            canonicalWorkflowForExport(requested, workflowApplication.library.value)
        }
        if (uri != null && requestedWorkflow != null && workflow == null) {
            runMessage = context.getString(R.string.export_workflow_missing)
        } else if (uri != null && workflow != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.write(uri, workflow) }
                }
                outcome
                    .onSuccess { runMessage = context.getString(R.string.workflow_exported, workflow.name) }
                    .onFailure {
                        runMessage = context.getString(
                            R.string.export_failed,
                            workflowTransferFailureMessage(context, it),
                        )
                    }
            }
        }
    }
    val bundleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val requestedSnapshot = pendingBundleExport
        pendingBundleExport = null
        val librarySnapshot = canonicalLibraryForExport(requestedSnapshot, workflowApplication.library.value)
        if (uri != null && librarySnapshot != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.writeLibrary(uri, librarySnapshot) }
                }
                outcome
                    .onSuccess {
                        runMessage = context.resources.getQuantityString(
                            R.plurals.workflows_backed_up,
                            librarySnapshot.workflows.size,
                            librarySnapshot.workflows.size,
                        )
                    }
                    .onFailure {
                        runMessage = context.getString(
                            R.string.backup_failed,
                            workflowTransferFailureMessage(context, it),
                        )
                    }
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
                    val imported = withContext(Dispatchers.IO) { workflowTransfer.readLibrary(uri) }
                    val updated = onCommitImport(imported)
                    library = updated
                    val importedCount = imported.workflows.size
                    runMessage = context.resources.getQuantityString(
                        R.plurals.workflows_imported,
                        importedCount,
                        importedCount,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val details = if (error is InvalidWorkflowException ||
                        error is WorkflowTransferException
                    ) {
                        workflowTransferFailureMessage(context, error)
                    } else if (error is WorkflowImportSaveException) {
                        context.getString(R.string.transfer_error_save_failed)
                    } else {
                        context.getString(R.string.transfer_error_unknown)
                    }
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
            onSetUpAutomation = requestAccessibilitySetup,
            onTest = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = runningNotificationReadiness(context)
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
                    displayWidth = preflightDisplaySize.width,
                    displayHeight = preflightDisplaySize.height,
                    isImageTemplateValid = ::imageTemplateIsValid,
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
                        try {
                            val commit = onCommitWorkflow(expected, workflow)
                            library = commit.library
                            commit.cleanupResult?.let { schedules = it }
                            if (commit.cleanupError != null) {
                                runMessage = context.getString(
                                    R.string.workflow_saved_schedule_cleanup_failed,
                                )
                            }
                            editingWorkflow = null
                            initialEditingStepPath = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            editorSaveError = context.getString(
                                if (error is com.aiindexfinger.data.WorkflowEditConflictException) {
                                    R.string.workflow_edit_conflict
                                } else {
                                    R.string.save_failed
                                },
                            )
                        } finally {
                            editorSaveInProgress = false
                        }
                    }
                }
            },
        )
    } else if (showSettings) {
        SettingsScreen(
            appearanceMode = appearanceMode,
            onAppearanceModeChanged = onAppearanceModeChanged,
            onOpenAccessibilitySettings = requestAccessibilitySetup,
            onOpenTutorial = openTutorial,
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
            requestedRecordId = requestedHistoryRecordId,
            onRequestedRecordConsumed = { requestedHistoryRecordId = null },
            onBack = { showRunHistory = false },
            onOpenWorkflow = { workflow, stepPath ->
                initialEditingStepPath = stepPath
                editingWorkflow = workflow
            },
            onRetry = { workflow ->
                showRunHistory = false
                requestWorkflowRun(workflow, false)
            },
            onClear = requestClearRunHistory,
        )
    } else {
        WorkflowHome(
            workflows = workflows,
            folders = library.folders,
            workflowFolderIds = library.workflowFolderIds,
            onSaveFolder = { folder -> persist(update = { latest -> latest.withFolder(folder) }) },
            onDeleteFolder = { folderId ->
                persist(update = { latest -> latest.withoutFolder(folderId) })
            },
            onMoveWorkflow = { workflowId, folderId ->
                persist(update = { latest -> latest.moveWorkflow(workflowId, folderId) })
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
                val packInputs = availablePacks.map { (pack, folderNameRes, workflowNameResources) ->
                    Triple(
                        pack,
                        context.getString(folderNameRes),
                        workflowNameResources.map(context::getString),
                    )
                }
                var addedCount = 0
                persist(
                    update = { latest ->
                        packInputs.fold(latest) { current, (pack, folderName, workflowNames) ->
                            pack.install(current, folderName, workflowNames).also { result ->
                                addedCount += result.addedWorkflowCount
                            }.library
                        }
                    },
                    onSuccess = {
                        runMessage = if (addedCount == 0) {
                            context.getString(R.string.system_packs_already_installed)
                        } else {
                            context.getString(R.string.system_packs_installed, addedCount)
                        }
                    },
                )
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
                pendingBundleExport = workflowApplication.library.value ?: library
                bundleExportLauncher.launch("ai-index-finger-backup.json")
            },
            onExport = { workflow ->
                val currentWorkflow = latestWorkflows().firstOrNull { it.id == workflow.id }
                if (currentWorkflow == null) {
                    runMessage = context.getString(R.string.export_workflow_missing)
                } else {
                    pendingExport = currentWorkflow
                    exportLauncher.launch(currentWorkflow.exportFileName())
                }
            },
            onDuplicate = { workflow ->
                val duplicate = workflow.copy(
                    id = newId(),
                    name = context.getString(R.string.workflow_copy_name, workflow.name),
                )
                persist(
                    update = { latest ->
                        latest.copy(
                            workflows = latest.workflows + duplicate,
                            workflowFolderIds = latest.folderIdFor(workflow.id)?.let { folderId ->
                                latest.workflowFolderIds + (duplicate.id to folderId)
                            } ?: latest.workflowFolderIds,
                        )
                    },
                )
            },
            onCompare = { before, after -> workflowComparison = before to after },
                        onViewVersions = { workflow ->
                            coroutineScope.launch {
                                try {
                                    versionHistory = workflow to onListVersions(workflow.id)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    runMessage = context.getString(R.string.workflow_versions_load_failed)
                                }
                            }
                        },
            onDelete = { workflow ->
                            coroutineScope.launch {
                                try {
                                    val deletion = onDeleteWorkflow(workflow.id)
                                    library = deletion.library
                                    deletion.cleanupResult?.let { schedules = it }
                                    if (deletion.cleanupError != null) {
                                        runMessage = context.getString(
                                            R.string.workflow_saved_schedule_cleanup_failed,
                                        )
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    runMessage = context.getString(R.string.save_failed)
                                }
                            }
            },
            onSchedule = { workflow, targetEpochMillis, recurrence ->
                val currentWorkflow = latestWorkflows().firstOrNull { it.id == workflow.id }
                if (currentWorkflow == null) {
                    runMessage = context.getString(R.string.schedule_workflow_missing)
                } else if (!currentWorkflow.isReadyToRun()) {
                    runMessage = context.getString(
                        R.string.cannot_schedule,
                        currentWorkflow.readinessIssues().first().localizedMessage(context),
                    )
                    null
                } else {
                    val request = Triple(currentWorkflow, targetEpochMillis, recurrence)
                    val readiness = scheduleNotificationReadiness(context)
                    when (scheduleNotificationAction(readiness)) {
                        ScheduleNotificationAction.Schedule -> {
                            coroutineScope.launch { persistSchedule(request) }
                            null
                        }
                        ScheduleNotificationAction.RequestPermission -> {
                            pendingSchedule = request
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            null
                        }
                        ScheduleNotificationAction.OpenSettings -> {
                            blockedNotificationReadiness = readiness
                            runMessage = context.getString(R.string.schedule_notifications_blocked)
                            null
                        }
                    }
                }
            },
            onCancelSchedule = { workflow ->
                coroutineScope.launch {
                    try {
                        schedules = onCancelSchedule(workflow.id)
                        runMessage = context.getString(
                            R.string.workflow_schedule_cancelled,
                            workflow.name,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
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
            onClearRunHistory = requestClearRunHistory,
            onViewRunHistory = { showRunHistory = true },
            onOpenSettings = { showSettings = true },
            onOpenTutorial = openTutorial,
            runningWorkflowId = runningWorkflowId,
            runMessage = runMessage,
            onRun = { workflow ->
                requestWorkflowRun(workflow, false)
            },
            onDebug = { workflow ->
                requestWorkflowRun(workflow, true)
            },
            onPreflight = { workflow ->
                val service = AutomationAccessibilityService.instance
                val notificationStatus = runningNotificationReadiness(context)
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
                    displayWidth = preflightDisplaySize.width,
                    displayHeight = preflightDisplaySize.height,
                    isImageTemplateValid = ::imageTemplateIsValid,
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
                    try {
                        val rollback = onRollback(current.id, version.versionId)
                        val restored = rollback.workflow
                        library = rollback.libraryCommit.library
                        rollback.libraryCommit.cleanupResult?.let { schedules = it }
                        val restoredVersions = try {
                            onListVersions(restored.id)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList()
                        }
                        versionHistory = restored to restoredVersions
                        runMessage = if (rollback.libraryCommit.cleanupError == null) {
                            context.getString(R.string.workflow_rollback_complete, restored.name)
                        } else {
                            context.getString(R.string.workflow_saved_schedule_cleanup_failed)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
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
                        if (!openRunningNotificationSettings(context, report.notificationStatus)) {
                            runMessage = context.getString(R.string.notification_settings_unavailable)
                        }
                    }
                }
            },
        )
    }
    blockedNotificationReadiness?.let { readiness ->
        ScheduleNotificationRecoveryDialog(
            onOpenSettings = {
                if (!openScheduleNotificationSettings(context, readiness)) {
                    runMessage = context.getString(R.string.notification_settings_unavailable)
                }
                blockedNotificationReadiness = null
            },
            onDismiss = { blockedNotificationReadiness = null },
        )
    }
    blockedRunNotificationReadiness?.let { readiness ->
        RunNotificationRecoveryDialog(
            onOpenSettings = {
                if (!openRunningNotificationSettings(context, readiness)) {
                    runMessage = context.getString(R.string.run_notification_settings_unavailable)
                }
                blockedRunNotificationReadiness = null
            },
            onDismiss = { blockedRunNotificationReadiness = null },
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

internal fun workflowTransferFailureMessage(context: Context, error: Throwable): String = when (error) {
    is InvalidWorkflowException -> error.issue.localizedMessage(context)
    is WorkflowTransferException -> when (error.code) {
        WorkflowTransferErrorCode.InvalidContent -> context.getString(R.string.transfer_error_invalid_content)
        WorkflowTransferErrorCode.TooManyWorkflows -> context.getString(R.string.transfer_error_too_many_workflows)
        WorkflowTransferErrorCode.TooManyFolders -> context.getString(R.string.transfer_error_too_many_folders)
        WorkflowTransferErrorCode.DuplicateWorkflowIds ->
            context.getString(R.string.transfer_error_duplicate_workflows)
        WorkflowTransferErrorCode.DuplicateFolderIds ->
            context.getString(R.string.transfer_error_duplicate_folder_ids)
        WorkflowTransferErrorCode.RootNotObject -> context.getString(R.string.transfer_error_root_not_object)
        WorkflowTransferErrorCode.UnsupportedBundleVersion -> context.getString(
            R.string.transfer_error_unsupported_bundle_version,
            error.arguments["version"].orEmpty(),
        )
        WorkflowTransferErrorCode.BlankFolderName -> context.getString(R.string.transfer_error_blank_folder)
        WorkflowTransferErrorCode.DuplicateFolderNames ->
            context.getString(R.string.transfer_error_duplicate_folder_names)
        WorkflowTransferErrorCode.UnsupportedWorkflowVersion -> context.getString(
            R.string.transfer_error_unsupported_workflow_version,
            error.arguments["version"].orEmpty(),
        )
        WorkflowTransferErrorCode.FileUnavailable -> context.getString(R.string.transfer_error_file_unavailable)
        WorkflowTransferErrorCode.FileTooLarge -> context.getString(R.string.transfer_error_file_too_large)
    }
    else -> context.getString(R.string.transfer_error_unknown)
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
internal fun RunNotificationRecoveryDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_notifications_blocked_title)) },
        text = {
            Text(
                stringResource(R.string.run_notifications_blocked),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_notification_settings))
            }
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
    onOpenTutorial: () -> Unit,
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
    val currentStepLocation by AutomationAccessibilityService.currentStepLocation
        .collectAsStateWithLifecycle()
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
    val visibleWorkflows = remember(workflows, folders, workflowQuery, selectedFolderFilter, workflowFolderIds) {
        filterWorkflows(workflows, workflowFolderIds, folders, workflowQuery, selectedFolderFilter)
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
                    stringResource(R.string.app_name),
                    modifier = Modifier.weight(1f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onOpenTutorial) {
                    Text(stringResource(R.string.tutorial_action))
                }
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
                Spacer(Modifier.height(14.dp))
                RunningWorkflowStatus(
                    workflowName = currentWorkflow?.name ?: stringResource(R.string.workflow),
                    stepName = currentStepLocation?.localizedName(),
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
                    pluralStringResource(
                        R.plurals.workflow_count,
                        workflows.size,
                        workflows.size,
                        visibleWorkflows.size,
                    ),
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
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.no_workflows),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.no_workflows_tutorial_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    TextButton(onClick = onOpenTutorial) {
                        Text(stringResource(R.string.tutorial_action))
                    }
                }
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
                        pluralStringResource(
                            R.plurals.delete_folder_message,
                            affectedCount,
                            folder.name,
                            affectedCount,
                        )
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
                try {
                    onSchedule(workflow, targetEpochMillis, recurrence)
                    workflowToSchedule = null
                    null
                } catch (error: ScheduleTimeException) {
                    error.error
                }
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
                            pluralStringResource(
                                R.plurals.folder_item_count,
                                workflowFolderIds.count { it.value == folder.id },
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
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name)
    }
}

@Composable
private fun ScheduleDialog(
    workflowName: String,
    onDismiss: () -> Unit,
    onSchedule: (Long, ScheduleRecurrence) -> ScheduleTimeError?,
) {
    val context = LocalContext.current
    val initialDateTime = remember { LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0) }
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var recurrence by remember { mutableStateOf(ScheduleRecurrence.Once) }
    var submissionError by remember(selectedDate, selectedTime) {
        mutableStateOf<ScheduleTimeError?>(null)
    }
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
                            .selectable(
                                selected = recurrence == option,
                                role = Role.RadioButton,
                                onClick = { recurrence = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = recurrence == option,
                            onClick = null,
                        )
                        Text(option.localizedLabel())
                    }
                }
                val timeError = (targetResult.exceptionOrNull() as? ScheduleTimeException)?.error
                    ?: submissionError
                timeError?.let { error ->
                    Text(
                        stringResource(error.messageResourceId()),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
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
                onClick = {
                    submissionError = onSchedule(targetResult.getOrThrow(), recurrence)
                },
            ) { Text(stringResource(R.string.schedule)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun ScheduleTimeError.messageResourceId(): Int = when (this) {
    ScheduleTimeError.NonexistentLocalTime -> R.string.schedule_time_nonexistent
    ScheduleTimeError.NotInFuture -> R.string.schedule_time_not_future
    ScheduleTimeError.TooFarInFuture -> R.string.schedule_time_too_far
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
                    pluralStringResource(
                        R.plurals.element_count,
                        nodes.size,
                        nodes.size,
                        visibleNodes.size,
                    ),
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
                        Text(
                            stringResource(R.string.numbered_item, index + 1, node.displayName()),
                            fontWeight = FontWeight.SemiBold,
                        )
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
                                stringResource(if (node.clickable) R.string.value_yes else R.string.value_no),
                                stringResource(if (node.longClickable) R.string.value_yes else R.string.value_no),
                                stringResource(if (node.scrollable) R.string.value_yes else R.string.value_no),
                                stringResource(if (node.enabled) R.string.value_yes else R.string.value_no),
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
        Text(
            stringResource(R.string.node_property, label, value),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
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
    onSetUpAutomation: () -> Unit = {},
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
    var showScrollUntilDialog by remember { mutableStateOf(false) }
    var showWaitDialog by remember { mutableStateOf(false) }
    var showWaitNodeDialog by remember { mutableStateOf(false) }
    var showVariableDialog by remember { mutableStateOf(false) }
    var showReadNodeTextDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var showJumpIfDialog by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var showNodeConditionDialog by remember { mutableStateOf(false) }
    var showOperationChooser by remember { mutableStateOf(false) }
    var inspectedClickSelector by remember { mutableStateOf<NodeSelector?>(null) }
    var policyStepPath by remember { mutableStateOf<StepPath?>(null) }
    var editingStepPath by remember(workflow.id) { mutableStateOf(initialEditingStepPath) }
    var stepToDeletePath by remember { mutableStateOf<StepPath?>(null) }
    var confirmDiscardChanges by remember { mutableStateOf(false) }
    var unrecognizedClickCount by remember { mutableStateOf(0) }
    var imageTemplateSaveReceipt by remember(workflow.id) { mutableStateOf<String?>(null) }
    var showAllValidationIssues by remember(workflow.id) { mutableStateOf(false) }
    var draggedStepId by remember(workflow.id, currentListPath) { mutableStateOf<String?>(null) }
    var dragOffsetY by remember(workflow.id, currentListPath) { mutableStateOf(0f) }
    val dragStepThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
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
    val currentLabels = currentSteps.filterIsInstance<Step.Label>().map(Step.Label::name)
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
                    pluralStringResource(
                        R.plurals.click_recording_unrecognized_message,
                        unrecognizedClickCount,
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
            hasLabels = currentLabels.isNotEmpty(),
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
                    WorkflowEditorOperation.ScrollUntil -> showScrollUntilDialog = true
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
                    WorkflowEditorOperation.GlobalNotifications -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.Notifications),
                    )
                    WorkflowEditorOperation.GlobalQuickSettings -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.QuickSettings),
                    )
                    WorkflowEditorOperation.GlobalPowerDialog -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.PowerDialog),
                    )
                    WorkflowEditorOperation.GlobalLockScreen -> steps = steps.insertStep(
                        currentListPath,
                        currentSteps.size,
                        Step.GlobalAction(newId(), SystemAction.LockScreen),
                    )
                    WorkflowEditorOperation.WaitForNode -> showWaitNodeDialog = true
                    WorkflowEditorOperation.SetVariable -> showVariableDialog = true
                    WorkflowEditorOperation.ReadNodeText -> showReadNodeTextDialog = true
                    WorkflowEditorOperation.Repeat -> showRepeatDialog = true
                    WorkflowEditorOperation.Label -> showLabelDialog = true
                    WorkflowEditorOperation.JumpIf -> showJumpIfDialog = true
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
                    imageTemplateSaveReceipt?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp,
                        )
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
                ) { Text(stringResource(R.string.system_action_back)) }
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
                supportingText = if (defaultTimeoutMillis == null || defaultTimeoutMillis <= 0) {
                    {
                        Text(
                            stringResource(R.string.validation_non_positive_timeout),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (validationIssues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    pluralStringResource(
                        R.plurals.validation_issue_count,
                        validationIssues.size,
                        validationIssues.size,
                    ),
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
                                pluralStringResource(
                                    R.plurals.more_issues,
                                    validationIssues.size - 3,
                                    validationIssues.size - 3,
                                )
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
                key(step.id) {
                    val stepPath = StepPath(currentListPath, index)
                    StepRow(
                    index = index,
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < currentSteps.lastIndex,
                    canMoveToTop = index > 0,
                    canMoveToBottom = index < currentSteps.lastIndex,
                    onMoveUp = { steps = steps.moveStep(stepPath, index - 1) },
                    onMoveDown = { steps = steps.moveStep(stepPath, index + 1) },
                    onMoveToTop = { steps = steps.moveStep(stepPath, 0) },
                    onMoveToBottom = { steps = steps.moveStep(stepPath, currentSteps.lastIndex) },
                    onDragStart = {
                        draggedStepId = step.id
                        dragOffsetY = 0f
                    },
                    onDragBy = { deltaY ->
                        val draggedId = draggedStepId ?: return@StepRow
                        var latestSteps = steps.stepsAt(currentListPath)
                        var currentIndex = latestSteps.indexOfFirst { it.id == draggedId }
                        if (currentIndex < 0) return@StepRow
                        dragOffsetY += deltaY
                        while (dragOffsetY >= dragStepThresholdPx && currentIndex < latestSteps.lastIndex) {
                            steps = steps.moveStep(StepPath(currentListPath, currentIndex), currentIndex + 1)
                            dragOffsetY -= dragStepThresholdPx
                            currentIndex++
                            latestSteps = steps.stepsAt(currentListPath)
                        }
                        while (dragOffsetY <= -dragStepThresholdPx && currentIndex > 0) {
                            steps = steps.moveStep(StepPath(currentListPath, currentIndex), currentIndex - 1)
                            dragOffsetY += dragStepThresholdPx
                            currentIndex--
                            latestSteps = steps.stepsAt(currentListPath)
                        }
                    },
                    onDragEnd = {
                        draggedStepId = null
                        dragOffsetY = 0f
                    },
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
            if (AutomationAccessibilityService.instance == null) {
                Text(
                    stringResource(R.string.overlay_actions_require_automation_service),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
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
                ) { Text(stringResource(R.string.system_action_home)) }
                OutlinedButton(
                    onClick = {
                        steps = steps.insertStep(
                            currentListPath,
                            currentSteps.size,
                            Step.GlobalAction(newId(), SystemAction.Recents),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.system_action_recents)) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showLabelDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.add_label)) }
                OutlinedButton(
                    enabled = currentLabels.isNotEmpty(),
                    onClick = { showJumpIfDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.jump_to_label)) }
            }
            OutlinedButton(
                enabled = currentSteps.isNotEmpty(),
                onClick = { showNodeConditionDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.element_exists_condition)) }
            if (currentSteps.isEmpty()) {
                Text(
                    stringResource(R.string.operation_requires_existing_step),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
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
            onAdd = { imageStep, receipt ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    imageStep.copy(id = newId()),
                )
                imageTemplateSaveReceipt = receipt
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
            onAdd = { selector, value, inputMethod ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.InputText(newId(), selector, text = "", inputMethod = inputMethod, value = value),
                )
                showInputDialog = false
            },
        )
    }
    if (showSwipeDialog) {
        SwipeDialog(
            onSetUpAutomation = onSetUpAutomation,
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
            onSetUpAutomation = onSetUpAutomation,
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
    if (showScrollUntilDialog) {
        ScrollUntilStepDialog(
            observedNodes = observedNodes,
            confirmLabel = stringResource(R.string.add),
            onDismiss = { showScrollUntilDialog = false },
            onSave = { selector, direction, stopCondition, maxScrolls ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.ScrollUntil(newId(), selector, direction, stopCondition, maxScrolls),
                )
                showScrollUntilDialog = false
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
            onSave = { selector, variableName, attribute, postProcess, defaultValue ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.ReadNodeText(
                        newId(),
                        selector,
                        variableName,
                        attribute,
                        postProcess,
                        defaultValue,
                    ),
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
                observedNodes = observedNodes,
                initialStep = step,
                onDismiss = { editingStepPath = null },
                onSave = { targetMode, x, y, selector ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(targetMode = targetMode, x = x, y = y, selector = selector),
                    )
                    editingStepPath = null
                },
            )
            is Step.ImageClick -> ImageClickStepDialog(
                initialStep = step,
                confirmLabelRes = R.string.save,
                onDismiss = { editingStepPath = null },
                onAdd = { replacement, receipt ->
                    steps = steps.replaceStep(path, replacement.copy(id = step.id))
                    imageTemplateSaveReceipt = receipt
                    editingStepPath = null
                },
            )
            is Step.InputText -> InputTextDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = stringResource(R.string.save),
                onDismiss = { editingStepPath = null },
                onAdd = { selector, value, inputMethod ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            selector = selector,
                            text = "",
                            variableName = null,
                            inputMethod = inputMethod,
                            value = value,
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
                initialPostProcess = step.postProcess,
                initialDefaultValue = step.defaultValue,
                onDismiss = { editingStepPath = null },
                onSave = { selector, variableName, attribute, postProcess, defaultValue ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            selector = selector,
                            variableName = variableName,
                            attribute = attribute,
                            postProcess = postProcess,
                            defaultValue = defaultValue,
                        ),
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
            is Step.Label -> LabelDialog(
                title = stringResource(R.string.label_settings),
                initialName = step.name,
                existingNames = currentSteps.filterIsInstance<Step.Label>()
                    .map(Step.Label::name)
                    .filterNot { it == step.name }
                    .toSet(),
                onDismiss = { editingStepPath = null },
                onSave = { name ->
                    steps = steps.renameLabel(path, name)
                    editingStepPath = null
                },
            )
            is Step.JumpIf -> JumpIfDialog(
                observedNodes = observedNodes,
                labels = currentLabels,
                initialTargetLabel = step.targetLabel,
                initialCondition = step.condition,
                onDismiss = { editingStepPath = null },
                onSave = { targetLabel, condition ->
                    steps = steps.replaceStep(path, step.copy(targetLabel = targetLabel, condition = condition))
                    editingStepPath = null
                },
            )
            is Step.Swipe -> SwipeDialog(
                initialStep = step,
                confirmLabelRes = R.string.save,
                onSetUpAutomation = onSetUpAutomation,
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
                onSetUpAutomation = onSetUpAutomation,
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
            is Step.ScrollUntil -> ScrollUntilStepDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = stringResource(R.string.save),
                onDismiss = { editingStepPath = null },
                onSave = { selector, direction, stopCondition, maxScrolls ->
                    steps = steps.replaceStep(
                        path,
                        step.copy(
                            selector = selector,
                            direction = direction,
                            stopCondition = stopCondition,
                            maxScrolls = maxScrolls,
                        ),
                    )
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
            onAdd = { startIndex, endIndex, count ->
                steps = steps.wrapRangeInRepeat(
                    path = currentListPath,
                    startIndex = startIndex,
                    endIndex = endIndex,
                    repeatId = newId(),
                    times = count.toInt(),
                )
                showRepeatDialog = false
            },
        )
    }
    if (showLabelDialog) {
        LabelDialog(
            title = stringResource(R.string.add_label),
            existingNames = currentSteps.filterIsInstance<Step.Label>().map(Step.Label::name).toSet(),
            onDismiss = { showLabelDialog = false },
            onSave = { name ->
                steps = steps.insertStep(currentListPath, currentSteps.size, Step.Label(newId(), name))
                showLabelDialog = false
            },
        )
    }
    if (showJumpIfDialog) {
        JumpIfDialog(
            observedNodes = observedNodes,
            labels = currentLabels,
            onDismiss = { showJumpIfDialog = false },
            onSave = { targetLabel, condition ->
                steps = steps.insertStep(
                    currentListPath,
                    currentSteps.size,
                    Step.JumpIf(newId(), targetLabel, condition),
                )
                showJumpIfDialog = false
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
                    val modifier = Modifier
                        .fillMaxWidth()
                        .semantics { this.selected = selected }
                    if (selected) {
                        Button(onClick = { onSelect(action) }, modifier = modifier) {
                            Text(action.displayName())
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(action) }, modifier = modifier) {
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
    val countValid = count != null && count > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_settings)) },
        text = {
            NodeField(
                countText,
                { countText = it },
                stringResource(R.string.repeat_count_range),
                true,
                numeric = true,
                errorText = stringResource(R.string.repeat_count_error)
                    .takeUnless { countValid },
            )
        },
        confirmButton = {
            TextButton(
                enabled = countValid,
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
    val left = variableValueOrNull(leftMode, leftText, initialLeft)
    val right = variableValueOrNull(rightMode, rightText, initialRight)
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
    val currentChoice = failurePolicyChoice(current)
    var timeoutText by remember { mutableStateOf(currentStep.timeoutMillis?.toString().orEmpty()) }
    var retryAttempts by remember { mutableStateOf((current as? FailurePolicy.Retry)?.attempts?.toString() ?: "2") }
    var retryDelay by remember { mutableStateOf((current as? FailurePolicy.Retry)?.delayMillis?.toString() ?: "500") }
    val timeoutMillis = timeoutText.toLongOrNull()
    val timeoutValid = stepTimeoutCanSave(timeoutText)
    val attempts = retryAttempts.toIntOrNull()
    val delay = retryDelay.toLongOrNull()
    val retryValid = retryPolicyCanSave(attempts, delay)
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
                    numeric = true,
                    errorText = stringResource(R.string.validation_non_positive_timeout)
                        .takeUnless { timeoutValid },
                )
                Text(stringResource(R.string.on_failure), fontWeight = FontWeight.SemiBold)
                FailurePolicyChoiceButton(
                    selected = currentChoice == FailurePolicyChoice.Stop,
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Stop, timeoutMillis) },
                    label = stringResource(R.string.stop_workflow),
                )
                FailurePolicyChoiceButton(
                    selected = currentChoice == FailurePolicyChoice.Continue,
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Continue, timeoutMillis) },
                    label = stringResource(R.string.continue_next_step),
                )
                Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold)
                NodeField(
                    retryAttempts,
                    { retryAttempts = it },
                    stringResource(R.string.retry_attempts),
                    true,
                    numeric = true,
                    errorText = stringResource(R.string.retry_attempts_error)
                        .takeUnless { attempts != null && attempts in 1..10 },
                )
                NodeField(
                    retryDelay,
                    { retryDelay = it },
                    stringResource(R.string.retry_delay_millis),
                    true,
                    numeric = true,
                    errorText = stringResource(R.string.retry_delay_error)
                        .takeUnless { delay != null && delay >= 0 },
                )
                FailurePolicyChoiceButton(
                    selected = currentChoice == FailurePolicyChoice.Retry,
                    enabled = timeoutValid && retryValid,
                    onClick = {
                        onSelect(
                            FailurePolicy.Retry(requireNotNull(attempts), requireNotNull(delay)),
                            timeoutMillis,
                        )
                    },
                    label = stringResource(R.string.use_retry_policy),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal enum class FailurePolicyChoice { Stop, Continue, Retry }

internal fun failurePolicyChoice(policy: FailurePolicy): FailurePolicyChoice = when (policy) {
    FailurePolicy.Stop -> FailurePolicyChoice.Stop
    FailurePolicy.Continue -> FailurePolicyChoice.Continue
    is FailurePolicy.Retry -> FailurePolicyChoice.Retry
}

@Composable
private fun FailurePolicyChoiceButton(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .semantics { this.selected = selected }
    if (selected) {
        Button(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
    }
}

internal fun stepTimeoutCanSave(value: String): Boolean =
    value.isBlank() || value.toLongOrNull()?.let { it > 0 } == true

internal fun retryPolicyCanSave(attempts: Int?, delayMillis: Long?): Boolean =
    attempts != null && attempts in 1..10 && delayMillis != null && delayMillis >= 0

@Composable
private fun InputTextDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.InputText? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, Value, TextInputMethod) -> Unit,
) {
    var selectorDraft by remember(initialStep) {
        mutableStateOf(initialStep?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    val initialValue = initialStep?.value ?: initialStep?.variableName?.let(Value::Variable)
        ?: Value.Literal(initialStep?.text.orEmpty())
    var valueMode by remember(initialStep) { mutableStateOf(variableValueMode(initialValue)) }
    var valueText by remember(initialStep) { mutableStateOf(variableValueText(initialValue)) }
    var inputMethod by remember(initialStep) {
        mutableStateOf(initialStep?.inputMethod ?: TextInputMethod.SetText)
    }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    val value = variableValueOrNull(valueMode, valueText, initialValue)
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
                VariableValueEditor(
                    title = stringResource(R.string.text_to_input),
                    mode = valueMode,
                    text = valueText,
                    onModeChange = { valueMode = it },
                    onTextChange = { valueText = it },
                )
                SelectorToggleRow(
                    label = stringResource(
                        if (inputMethod == TextInputMethod.Paste) R.string.input_method_paste
                        else R.string.input_method_set_text,
                    ),
                    description = stringResource(
                        if (inputMethod == TextInputMethod.Paste) R.string.input_method_paste_description
                        else R.string.input_method_set_text_description,
                    ),
                    checked = inputMethod == TextInputMethod.Paste,
                    onCheckedChange = {
                        inputMethod = if (it) TextInputMethod.Paste else TextInputMethod.SetText
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && value != null,
                onClick = {
                    onAdd(
                        requireNotNull(selectedSelector),
                        requireNotNull(value),
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
    onSetUpAutomation: () -> Unit = {},
    onDismiss: () -> Unit,
    onAdd: (Int, Int, Int, Int, Long) -> Unit,
) {
    val displaySize = currentDisplayPixelSize()
    val defaults = defaultSwipeCoordinates(displaySize.width, displaySize.height)
    var startX by remember(initialStep) {
        mutableStateOf(initialStep?.startX?.toString() ?: defaults.start.x.toString())
    }
    var startY by remember(initialStep) {
        mutableStateOf(initialStep?.startY?.toString() ?: defaults.start.y.toString())
    }
    var endX by remember(initialStep) {
        mutableStateOf(initialStep?.endX?.toString() ?: defaults.end.x.toString())
    }
    var endY by remember(initialStep) {
        mutableStateOf(initialStep?.endY?.toString() ?: defaults.end.y.toString())
    }
    var previousDefaults by remember(initialStep) { mutableStateOf(defaults) }
    var duration by remember(initialStep) { mutableStateOf(initialStep?.durationMillis?.toString() ?: "400") }
    LaunchedEffect(defaults, initialStep) {
        if (initialStep == null) {
            val currentStart = startX.toIntOrNull()?.let { x -> startY.toIntOrNull()?.let { y -> ScreenPoint(x, y) } }
            val currentEnd = endX.toIntOrNull()?.let { x -> endY.toIntOrNull()?.let { y -> ScreenPoint(x, y) } }
            val updatedStart = currentStart?.let {
                updateAdaptiveCoordinateDefault(it, previousDefaults.start, defaults.start)
            }
            val updatedEnd = currentEnd?.let {
                updateAdaptiveCoordinateDefault(it, previousDefaults.end, defaults.end)
            }
            if (updatedStart != null) {
                startX = updatedStart.x.toString()
                startY = updatedStart.y.toString()
            }
            if (updatedEnd != null) {
                endX = updatedEnd.x.toString()
                endY = updatedEnd.y.toString()
            }
            previousDefaults = defaults
        }
    }
    val values = listOf(startX, startY, endX, endY).map { it.toIntOrNull() }
    val start = values[0]?.let { x -> values[1]?.let { y -> ScreenPoint(x, y) } }
    val end = values[2]?.let { x -> values[3]?.let { y -> ScreenPoint(x, y) } }
    val originalStart = initialStep?.let { ScreenPoint(it.startX, it.startY) }
    val originalEnd = initialStep?.let { ScreenPoint(it.endX, it.endY) }
    val startCanSave = coordinateCanSave(start, displaySize.width, displaySize.height, originalStart)
    val endCanSave = coordinateCanSave(end, displaySize.width, displaySize.height, originalEnd)
    val coordinatesInsideDisplay = start?.let {
        coordinateInsideDisplay(it, displaySize.width, displaySize.height)
    } == true && end?.let {
        coordinateInsideDisplay(it, displaySize.width, displaySize.height)
    } == true
    val durationValue = duration.toLongOrNull()
    val durationValid = durationValue != null && durationValue in 1..10_000
    val valid = startCanSave && endCanSave && durationValid

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
                    onSetUpAutomation = onSetUpAutomation,
                    onSwipe = { start, end ->
                        startX = start.x.toString()
                        startY = start.y.toString()
                        endX = end.x.toString()
                        endY = end.y.toString()
                    },
                )
                NodeField(startX, { startX = it }, stringResource(R.string.swipe_start_x), true, numeric = true)
                NodeField(startY, { startY = it }, stringResource(R.string.swipe_start_y), true, numeric = true)
                NodeField(endX, { endX = it }, stringResource(R.string.swipe_end_x), true, numeric = true)
                NodeField(endY, { endY = it }, stringResource(R.string.swipe_end_y), true, numeric = true)
                NodeField(duration, { duration = it }, stringResource(R.string.duration_millis), true, numeric = true)
                if (!startCanSave || !endCanSave) {
                    Text(
                        stringResource(
                            R.string.coordinate_input_error,
                            displaySize.width,
                            displaySize.height,
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                } else if (!coordinatesInsideDisplay) {
                    Text(
                        stringResource(R.string.coordinate_imported_outside_display),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                if (!durationValid) {
                    Text(
                        stringResource(R.string.swipe_duration_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
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

internal data class SwipeCoordinateDefaults(
    val start: ScreenPoint,
    val end: ScreenPoint,
)

internal fun defaultTapCoordinate(displayWidth: Int, displayHeight: Int): ScreenPoint {
    val width = displayWidth.coerceAtLeast(1)
    val height = displayHeight.coerceAtLeast(1)
    return ScreenPoint(
        x = (width / 2).coerceAtMost(width - 1),
        y = (height / 2).coerceAtMost(height - 1),
    )
}

internal fun defaultSwipeCoordinates(displayWidth: Int, displayHeight: Int): SwipeCoordinateDefaults {
    val center = defaultTapCoordinate(displayWidth, displayHeight)
    val height = displayHeight.coerceAtLeast(1)
    return SwipeCoordinateDefaults(
        start = center.copy(y = ((height.toLong() * 3) / 4).toInt().coerceAtMost(height - 1)),
        end = center.copy(y = (height / 4).coerceAtMost(height - 1)),
    )
}

internal fun coordinateInsideDisplay(point: ScreenPoint, displayWidth: Int, displayHeight: Int): Boolean =
    point.x in 0 until displayWidth && point.y in 0 until displayHeight

internal fun captureBoundsMatchDisplay(
    bounds: ScreenBounds,
    displayWidth: Int,
    displayHeight: Int,
): Boolean = bounds.right - bounds.left == displayWidth &&
    bounds.bottom - bounds.top == displayHeight

internal fun coordinateCanSave(
    point: ScreenPoint?,
    displayWidth: Int,
    displayHeight: Int,
    original: ScreenPoint?,
): Boolean = point != null && point.x >= 0 && point.y >= 0 &&
    (coordinateInsideDisplay(point, displayWidth, displayHeight) || point == original)

internal fun updateAdaptiveCoordinateDefault(
    current: ScreenPoint,
    previousDefault: ScreenPoint,
    newDefault: ScreenPoint,
): ScreenPoint = if (current == previousDefault) newDefault else current

@Composable
private fun currentDisplayPixelSize(): IntSize {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            IntSize(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1))
        } else {
            val metrics = context.resources.displayMetrics
            IntSize(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
        }
    }
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
    Text(
        stringResource(R.string.selector_matching_behavior),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
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
    description: String? = null,
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
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
private fun TapDialog(
    initialStep: Step.Tap? = null,
    confirmLabelRes: Int = R.string.add,
    onSetUpAutomation: () -> Unit = {},
    onDismiss: () -> Unit,
    onAdd: (Int, Int) -> Unit,
) {
    val displaySize = currentDisplayPixelSize()
    val default = defaultTapCoordinate(displaySize.width, displaySize.height)
    var xText by remember(initialStep) { mutableStateOf(initialStep?.x?.toString() ?: default.x.toString()) }
    var yText by remember(initialStep) { mutableStateOf(initialStep?.y?.toString() ?: default.y.toString()) }
    var previousDefault by remember(initialStep) { mutableStateOf(default) }
    LaunchedEffect(default, initialStep) {
        if (initialStep == null) {
            val current = xText.toIntOrNull()?.let { x -> yText.toIntOrNull()?.let { y -> ScreenPoint(x, y) } }
            current?.let {
                val updated = updateAdaptiveCoordinateDefault(it, previousDefault, default)
                xText = updated.x.toString()
                yText = updated.y.toString()
            }
            previousDefault = default
        }
    }
    val x = xText.toIntOrNull()
    val y = yText.toIntOrNull()
    val point = x?.let { parsedX -> y?.let { parsedY -> ScreenPoint(parsedX, parsedY) } }
    val original = initialStep?.let { ScreenPoint(it.x, it.y) }
    val coordinateValid = coordinateCanSave(point, displaySize.width, displaySize.height, original)
    val coordinateInsideDisplay = point?.let {
        coordinateInsideDisplay(it, displaySize.width, displaySize.height)
    } == true
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
                    onSetUpAutomation = onSetUpAutomation,
                    onTap = { point ->
                        xText = point.x.toString()
                        yText = point.y.toString()
                    },
                )
                NodeField(xText, { xText = it }, stringResource(R.string.coordinate_x), true, numeric = true)
                NodeField(yText, { yText = it }, stringResource(R.string.coordinate_y), true, numeric = true)
                if (!coordinateValid) {
                    Text(
                        stringResource(
                            R.string.coordinate_input_error,
                            displaySize.width,
                            displaySize.height,
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                } else if (!coordinateInsideDisplay) {
                    Text(
                        stringResource(R.string.coordinate_imported_outside_display),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = coordinateValid,
                onClick = { onAdd(requireNotNull(x), requireNotNull(y)) },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecordedClickDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.RecordedClick,
    onDismiss: () -> Unit,
    onSave: (RecordedClickTargetMode, Int, Int, NodeSelector?) -> Unit,
) {
    val displaySize = currentDisplayPixelSize()
    var targetMode by remember(initialStep) { mutableStateOf(initialStep.targetMode) }
    var xText by remember(initialStep) { mutableStateOf(initialStep.x.toString()) }
    var yText by remember(initialStep) { mutableStateOf(initialStep.y.toString()) }
    val x = xText.toIntOrNull()
    val y = yText.toIntOrNull()
    val point = x?.let { parsedX -> y?.let { parsedY -> ScreenPoint(parsedX, parsedY) } }
    val originalPoint = ScreenPoint(initialStep.x, initialStep.y)
    val coordinateValid = coordinateCanSave(
        point,
        displaySize.width,
        displaySize.height,
        originalPoint,
    )
    val coordinateInsideDisplay = point?.let {
        coordinateInsideDisplay(it, displaySize.width, displaySize.height)
    } == true
    var selectorDraft by remember(initialStep) {
        mutableStateOf(
            initialStep.selector?.toDraft()
                ?: NodeSelectorDraft(packageName = initialStep.control.packageName),
        )
    }
    val selectedSelector = selectorDraft.toSelectorOrNull()
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
                Text(stringResource(R.string.recorded_click_selector_repair), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.recorded_click_selector_repair_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                NodeSelectorEditor(
                    draft = selectorDraft,
                    onDraftChange = { selectorDraft = it },
                    recentNodes = observedNodes,
                    recentTitle = stringResource(R.string.select_recent_element),
                    emptyMessage = stringResource(R.string.open_target_app_then_return),
                )
                HorizontalDivider()
                Text(stringResource(R.string.recorded_click_target_mode), fontWeight = FontWeight.Bold)
                RecordedClickTargetMode.entries.forEach { option ->
                    val enabled = option != RecordedClickTargetMode.Control || selectedSelector != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = targetMode == option,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { targetMode = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = targetMode == option,
                            enabled = enabled,
                            onClick = null,
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
                if (selectedSelector == null) {
                    Text(
                        stringResource(initialStep.fallbackCause?.messageResourceId()
                            ?: R.string.recorded_click_control_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                NodeField(xText, { xText = it }, stringResource(R.string.coordinate_x), true, numeric = true)
                NodeField(yText, { yText = it }, stringResource(R.string.coordinate_y), true, numeric = true)
                if (!coordinateValid) {
                    Text(
                        stringResource(
                            R.string.coordinate_input_error,
                            displaySize.width,
                            displaySize.height,
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                } else if (!coordinateInsideDisplay) {
                    Text(
                        stringResource(R.string.coordinate_imported_outside_display),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
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
                        control.clickable.localizedBoolean(),
                        control.enabled.localizedBoolean(),
                        control.longClickable.localizedBoolean(),
                        control.scrollable.localizedBoolean(),
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
                enabled = recordedClickCanSave(targetMode, selectedSelector, coordinateValid),
                onClick = {
                    onSave(targetMode, requireNotNull(x), requireNotNull(y), selectedSelector)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal fun recordedClickCanSave(
    targetMode: RecordedClickTargetMode,
    selector: NodeSelector?,
    coordinateValid: Boolean,
): Boolean = coordinateValid && (targetMode != RecordedClickTargetMode.Control || selector != null)

@Composable
private fun Boolean.localizedBoolean(): String = stringResource(
    if (this) R.string.value_yes else R.string.value_no,
)

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
    onSetUpAutomation: () -> Unit,
    onTap: (ScreenPoint) -> Unit = {},
    onSwipe: (ScreenPoint, ScreenPoint) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    val automationConnected by AutomationAccessibilityService.connected.collectAsStateWithLifecycle()
    val displaySize = currentDisplayPixelSize()
    var captureGeometryChanged by remember { mutableStateOf(false) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureStart by remember(captureState) { mutableStateOf<Offset?>(null) }
    var gestureEnd by remember(captureState) { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose { AutomationAccessibilityService.cancelPendingScreenCapture() }
    }
    LaunchedEffect(captureState, displaySize) {
        val state = captureState as? ScreenCaptureState.Ready
        if (state != null && !captureBoundsMatchDisplay(
                state.screenBounds,
                displaySize.width,
                displaySize.height,
            )
        ) {
            captureGeometryChanged = true
            AutomationAccessibilityService.discardScreenCapture()
        } else if (captureState is ScreenCaptureState.Armed) {
            captureGeometryChanged = false
        }
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
    if (captureGeometryChanged) {
        Text(
            stringResource(R.string.screenshot_display_changed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                AutomationAccessibilityService.instance?.capturePreviousApp()
            },
            enabled = AutomationAccessibilityService.instance != null &&
                automationConnected &&
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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.primary,
        )
        is ScreenCaptureState.Error -> Text(
            state.message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
        )
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
        ScreenCaptureState.Idle -> when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Text(
                stringResource(R.string.capture_requires_android_11),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            !automationConnected || AutomationAccessibilityService.instance == null -> {
                Text(
                    stringResource(R.string.coordinate_capture_requires_automation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = onSetUpAutomation,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.set_up_automation)) }
            }
        }
    }
}

private fun mapCaptureOffset(
    state: ScreenCaptureState.Ready,
    captureSize: IntSize,
    offset: Offset,
): ScreenPoint? {
    val bitmapPoint = mapFitCenterTapToScreen(
        tapX = offset.x,
        tapY = offset.y,
        containerWidth = captureSize.width,
        containerHeight = captureSize.height,
        imageWidth = state.bitmap.width,
        imageHeight = state.bitmap.height,
    ) ?: return null
    return mapBitmapPointToScreen(
        point = bitmapPoint,
        bitmapWidth = state.bitmap.width,
        bitmapHeight = state.bitmap.height,
        screenBounds = state.screenBounds,
    )
}

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = direction == ScrollDirection.Forward,
                            role = Role.Switch,
                            onValueChange = { forward ->
                                direction = if (forward) ScrollDirection.Forward else ScrollDirection.Backward
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(scrollDirectionLabelRes(direction)),
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
                        onCheckedChange = null,
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
private fun ScrollUntilStepDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.ScrollUntil? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (NodeSelector, ScrollDirection, ScrollUntilStopCondition, Int?) -> Unit,
) {
    val initialStopCondition = initialStep?.stopCondition
    var selectorDraft by remember(initialStep) {
        mutableStateOf(initialStep?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    var direction by remember(initialStep) {
        mutableStateOf(initialStep?.direction ?: ScrollDirection.Forward)
    }
    var stopMode by remember(initialStopCondition) {
        mutableStateOf(
            when (initialStopCondition) {
                is ScrollUntilStopCondition.NodeAppears -> ScrollUntilStopMode.NodeAppears
                is ScrollUntilStopCondition.NodeDisappears -> ScrollUntilStopMode.NodeDisappears
                is ScrollUntilStopCondition.ConditionMet -> ScrollUntilStopMode.Condition
                ScrollUntilStopCondition.NoProgress -> ScrollUntilStopMode.NoProgress
                ScrollUntilStopCondition.MaxScrolls -> ScrollUntilStopMode.MaxScrolls
                null -> ScrollUntilStopMode.NodeAppears
            },
        )
    }
    val initialTargetSelector = when (initialStopCondition) {
        is ScrollUntilStopCondition.NodeAppears -> initialStopCondition.selector
        is ScrollUntilStopCondition.NodeDisappears -> initialStopCondition.selector
        else -> null
    }
    var targetDraft by remember(initialTargetSelector) {
        mutableStateOf(initialTargetSelector?.toDraft() ?: NodeSelectorDraft())
    }
    val initialCondition = (initialStopCondition as? ScrollUntilStopCondition.ConditionMet)?.condition
    val initialValueCondition = initialCondition as? Condition.Equals
    val initialNodeCondition = initialCondition as? Condition.NodeExists
    var conditionMode by remember(initialCondition) {
        mutableStateOf(
            if (initialNodeCondition != null) {
                ScrollUntilConditionMode.NodeExists
            } else {
                ScrollUntilConditionMode.Values
            },
        )
    }
    var leftMode by remember(initialCondition) {
        mutableStateOf(variableValueMode(initialValueCondition?.left ?: Value.Literal("")))
    }
    var leftText by remember(initialCondition) {
        mutableStateOf(variableValueText(initialValueCondition?.left ?: Value.Literal("")))
    }
    var rightMode by remember(initialCondition) {
        mutableStateOf(variableValueMode(initialValueCondition?.right ?: Value.Literal("")))
    }
    var rightText by remember(initialCondition) {
        mutableStateOf(variableValueText(initialValueCondition?.right ?: Value.Literal("")))
    }
    var operator by remember(initialCondition) {
        mutableStateOf(initialValueCondition?.operator ?: ComparisonOperator.Equals)
    }
    var conditionNodeDraft by remember(initialNodeCondition) {
        mutableStateOf(initialNodeCondition?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    var maxScrollsText by remember(initialStep) { mutableStateOf(initialStep?.maxScrolls?.toString().orEmpty()) }
    val maxScrolls = maxScrollsText.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val maxScrollsValid = maxScrollsText.isBlank() || maxScrolls != null && maxScrolls > 0
    val scrollSelector = selectorDraft.toSelectorOrNull()
    val targetSelector = targetDraft.toSelectorOrNull()
    val conditionSelector = conditionNodeDraft.toSelectorOrNull()
    val left = variableValueOrNull(leftMode, leftText, initialValueCondition?.left)
    val right = variableValueOrNull(rightMode, rightText, initialValueCondition?.right)
    val stopCondition = when (stopMode) {
        ScrollUntilStopMode.NodeAppears -> targetSelector?.let(ScrollUntilStopCondition::NodeAppears)
        ScrollUntilStopMode.NodeDisappears -> targetSelector?.let(ScrollUntilStopCondition::NodeDisappears)
        ScrollUntilStopMode.Condition -> when (conditionMode) {
            ScrollUntilConditionMode.Values -> if (left != null && right != null) {
                ScrollUntilStopCondition.ConditionMet(Condition.Equals(left, right, operator))
            } else {
                null
            }
            ScrollUntilConditionMode.NodeExists -> conditionSelector?.let { selector ->
                ScrollUntilStopCondition.ConditionMet(Condition.NodeExists(selector))
            }
        }
        ScrollUntilStopMode.NoProgress -> ScrollUntilStopCondition.NoProgress
        ScrollUntilStopMode.MaxScrolls -> maxScrolls?.let { ScrollUntilStopCondition.MaxScrolls }
    }
    val canSave = scrollSelector != null && stopCondition != null && maxScrollsValid
    val scrollableNodes = observedNodes.filter { it.scrollable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scroll_until_settings)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = direction == ScrollDirection.Forward,
                            role = Role.Switch,
                            onValueChange = { forward ->
                                direction = if (forward) ScrollDirection.Forward else ScrollDirection.Backward
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(scrollDirectionLabelRes(direction)), modifier = Modifier.weight(1f))
                    Switch(checked = direction == ScrollDirection.Forward, onCheckedChange = null)
                }
                Text(stringResource(R.string.scroll_until_stop_condition_title), fontWeight = FontWeight.SemiBold)
                ScrollUntilStopMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = stopMode == mode,
                                role = Role.RadioButton,
                                onClick = { stopMode = mode },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = stopMode == mode, onClick = null)
                        Text(mode.localizedName(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (stopMode == ScrollUntilStopMode.NodeAppears ||
                    stopMode == ScrollUntilStopMode.NodeDisappears
                ) {
                    Text(stringResource(R.string.scroll_until_target), fontWeight = FontWeight.SemiBold)
                    NodeSelectorEditor(
                        draft = targetDraft,
                        onDraftChange = { targetDraft = it },
                        recentNodes = observedNodes,
                        recentTitle = stringResource(R.string.select_recent_element),
                        emptyMessage = stringResource(R.string.open_target_app_then_return),
                    )
                }
                if (stopMode == ScrollUntilStopMode.Condition) {
                    Text(stringResource(R.string.scroll_until_condition_type), fontWeight = FontWeight.SemiBold)
                    ScrollUntilConditionMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = conditionMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { conditionMode = mode },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = conditionMode == mode, onClick = null)
                            Text(mode.localizedName(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (conditionMode == ScrollUntilConditionMode.Values) {
                        VariableValueEditor(
                            title = stringResource(R.string.comparison_left_value),
                            mode = leftMode,
                            text = leftText,
                            onModeChange = { leftMode = it },
                            onTextChange = { leftText = it },
                        )
                        ComparisonOperatorSelector(operator) { operator = it }
                        VariableValueEditor(
                            title = stringResource(R.string.comparison_right_value),
                            mode = rightMode,
                            text = rightText,
                            onModeChange = { rightMode = it },
                            onTextChange = { rightText = it },
                        )
                    } else {
                        NodeSelectorEditor(
                            draft = conditionNodeDraft,
                            onDraftChange = { conditionNodeDraft = it },
                            recentNodes = observedNodes,
                            recentTitle = stringResource(R.string.select_recent_element),
                            emptyMessage = stringResource(R.string.open_target_app_then_return),
                        )
                    }
                }
                NodeField(
                    value = maxScrollsText,
                    onValueChange = { maxScrollsText = it },
                    label = stringResource(R.string.scroll_until_max_scrolls),
                    numeric = true,
                    errorText = stringResource(R.string.scroll_until_max_scrolls_error)
                        .takeUnless { maxScrollsValid &&
                            (stopMode != ScrollUntilStopMode.MaxScrolls || maxScrolls != null) },
                )
                Text(
                    stringResource(
                        if (stopMode == ScrollUntilStopMode.MaxScrolls) {
                            R.string.scroll_until_max_scrolls_required_hint
                        } else {
                            R.string.scroll_until_max_scrolls_optional_hint
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(requireNotNull(scrollSelector), direction, requireNotNull(stopCondition), maxScrolls) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private enum class ScrollUntilStopMode {
    NodeAppears,
    NodeDisappears,
    Condition,
    NoProgress,
    MaxScrolls,
}

@Composable
private fun ScrollUntilStopMode.localizedName(): String = stringResource(
    when (this) {
        ScrollUntilStopMode.NodeAppears -> R.string.scroll_until_stop_node_appears
        ScrollUntilStopMode.NodeDisappears -> R.string.scroll_until_stop_node_disappears
        ScrollUntilStopMode.Condition -> R.string.scroll_until_stop_condition
        ScrollUntilStopMode.NoProgress -> R.string.scroll_until_stop_no_progress
        ScrollUntilStopMode.MaxScrolls -> R.string.scroll_until_stop_max_scrolls
    },
)

private enum class ScrollUntilConditionMode {
    Values,
    NodeExists,
}

@Composable
private fun ScrollUntilConditionMode.localizedName(): String = stringResource(
    when (this) {
        ScrollUntilConditionMode.Values -> R.string.scroll_until_condition_values
        ScrollUntilConditionMode.NodeExists -> R.string.scroll_until_condition_node_exists
    },
)

internal fun scrollDirectionLabelRes(direction: ScrollDirection): Int = when (direction) {
    ScrollDirection.Forward -> R.string.scroll_forward
    ScrollDirection.Backward -> R.string.scroll_backward
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
    val valid = delayDurationCanSave(number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(value, { value = it }, label, true, numeric = true)
                if (!valid) {
                    Text(
                        stringResource(R.string.delay_duration_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onAdd(requireNotNull(number)) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal fun delayDurationCanSave(durationMillis: Long?): Boolean =
    durationMillis != null && durationMillis >= 0

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
                SelectorToggleRow(
                    label = stringResource(
                        if (mustExist) R.string.wait_element_appear else R.string.wait_element_disappear,
                    ),
                    description = stringResource(
                        if (mustExist) R.string.wait_element_appear_description
                        else R.string.wait_element_disappear_description,
                    ),
                    checked = mustExist,
                    onCheckedChange = { mustExist = it },
                )
                NodeField(
                    timeout,
                    { timeout = it },
                    stringResource(R.string.wait_timeout_override),
                    numeric = true,
                    errorText = stringResource(R.string.validation_non_positive_timeout)
                        .takeUnless { timeoutValid },
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
    val resolvedValue = variableValueOrNull(valueMode, value, initialValue)
    val variableNameValid = variableName.isNotBlank()
    val valueReferenceValid = resolvedValue != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_variable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(
                    variableName,
                    { variableName = it },
                    stringResource(R.string.variable_name),
                    true,
                    errorText = stringResource(R.string.validation_blank_variable_name)
                        .takeUnless { variableNameValid },
                )
                VariableValueEditor(
                    title = stringResource(R.string.variable_value_source),
                    mode = valueMode,
                    text = value,
                    onModeChange = { valueMode = it },
                    onTextChange = { value = it },
                )
                if (!valueReferenceValid) {
                    Text(
                        stringResource(R.string.validation_blank_variable_name),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = variableNameValid && valueReferenceValid,
                onClick = {
                    onAdd(
                        preserveUnchangedOrTrim(variableName, initialName),
                        requireNotNull(resolvedValue),
                    )
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
        val modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = mode == option }
        if (mode == option) {
            Button(
                onClick = { onModeChange(option) },
                modifier = modifier,
            ) { Text(stringResource(option.labelRes)) }
        } else {
            OutlinedButton(
                onClick = { onModeChange(option) },
                modifier = modifier,
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

internal fun variableValueOrNull(
    mode: VariableValueMode,
    text: String,
    originalValue: Value? = null,
): Value? {
    if (originalValue != null && mode == variableValueMode(originalValue) &&
        text == variableValueText(originalValue)
    ) {
        return originalValue
    }
    return when (mode) {
        VariableValueMode.Literal -> Value.Literal(text)
        VariableValueMode.Variable -> text.trim().takeIf(String::isNotEmpty)?.let(Value::Variable)
        VariableValueMode.Template -> Value.Template(text)
    }
}

@Composable
private fun WrapStepDialog(
    title: String,
    valueLabel: String,
    initialValue: String,
    steps: List<Step>,
    onDismiss: () -> Unit,
    onAdd: (Int, Int, Long) -> Unit,
) {
    var rangeStartId by remember { mutableStateOf<String?>(null) }
    var rangeEndId by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf(initialValue) }
    val count = value.toLongOrNull()
    val countValid = count != null && count > 0
    val rangeStart = rangeStartId?.let { id -> steps.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val rangeEnd = rangeEndId?.let { id -> steps.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val range = rangeStart?.let { start -> rangeEnd?.let { end -> minOf(start, end)..maxOf(start, end) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.select_step_range_to_wrap), fontWeight = FontWeight.SemiBold)
                if (rangeStartId != null) {
                    TextButton(
                        onClick = {
                            rangeStartId = null
                            rangeEndId = null
                        },
                    ) { Text(stringResource(R.string.clear_selection)) }
                }
                steps.forEachIndexed { index, step ->
                    SelectableStepButton(
                        index = index,
                        step = step,
                        selected = range?.contains(index) == true ||
                            rangeEndId == null && step.id == rangeStartId,
                        onSelect = {
                            if (rangeStartId == null || rangeEndId != null) {
                                rangeStartId = step.id
                                rangeEndId = null
                            } else {
                                rangeEndId = step.id
                            }
                        },
                    )
                }
                NodeField(
                    value,
                    { value = it },
                    valueLabel,
                    true,
                    numeric = true,
                    errorText = stringResource(R.string.repeat_count_error)
                        .takeUnless { countValid },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = range != null && countValid,
                onClick = { onAdd(range!!.first, range.last, requireNotNull(count)) },
            ) { Text(stringResource(R.string.wrap_step)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LabelDialog(
    title: String,
    initialName: String = "",
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalizedName = name.trim()
    val valid = normalizedName.isNotEmpty() && normalizedName !in existingNames
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NodeField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.label_name),
                required = true,
                errorText = stringResource(R.string.label_name_error).takeUnless { valid },
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(normalizedName) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun JumpIfDialog(
    observedNodes: List<ObservedNode>,
    labels: List<String>,
    initialTargetLabel: String? = null,
    initialCondition: Condition? = null,
    onDismiss: () -> Unit,
    onSave: (String, Condition?) -> Unit,
) {
    var targetLabel by remember(initialTargetLabel, labels) {
        mutableStateOf(initialTargetLabel?.takeIf { it in labels } ?: labels.firstOrNull())
    }
    var useCondition by remember(initialCondition) { mutableStateOf(initialCondition != null) }
    var conditionMode by remember(initialCondition) {
        mutableStateOf(
            when (initialCondition) {
                null -> JumpConditionMode.Always
                is Condition.Equals -> JumpConditionMode.Values
                is Condition.NodeExists -> JumpConditionMode.NodeExists
            },
        )
    }
    val initialValueCondition = initialCondition as? Condition.Equals
    val initialNodeCondition = initialCondition as? Condition.NodeExists
    var leftMode by remember(initialCondition) {
        mutableStateOf(variableValueMode(initialValueCondition?.left ?: Value.Literal("")))
    }
    var leftText by remember(initialCondition) {
        mutableStateOf(variableValueText(initialValueCondition?.left ?: Value.Literal("")))
    }
    var rightMode by remember(initialCondition) {
        mutableStateOf(variableValueMode(initialValueCondition?.right ?: Value.Literal("")))
    }
    var rightText by remember(initialCondition) {
        mutableStateOf(variableValueText(initialValueCondition?.right ?: Value.Literal("")))
    }
    var operator by remember(initialCondition) {
        mutableStateOf(initialValueCondition?.operator ?: ComparisonOperator.Equals)
    }
    var nodeDraft by remember(initialCondition) {
        mutableStateOf(initialNodeCondition?.selector?.toDraft() ?: NodeSelectorDraft())
    }
    val left = variableValueOrNull(leftMode, leftText, initialValueCondition?.left)
    val right = variableValueOrNull(rightMode, rightText, initialValueCondition?.right)
    val nodeSelector = nodeDraft.toSelectorOrNull()
    val conditionValid = when (conditionMode) {
        JumpConditionMode.Always -> true
        JumpConditionMode.Values -> left != null && right != null
        JumpConditionMode.NodeExists -> nodeSelector != null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jump_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.jump_target_label), fontWeight = FontWeight.SemiBold)
                if (labels.isEmpty()) {
                    Text(stringResource(R.string.no_labels_available))
                } else {
                    labels.forEach { label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = targetLabel == label,
                                    role = Role.RadioButton,
                                    onClick = { targetLabel = label },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = targetLabel == label, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Text(stringResource(R.string.jump_condition), fontWeight = FontWeight.SemiBold)
                JumpConditionMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = conditionMode == mode,
                                role = Role.RadioButton,
                                onClick = { conditionMode = mode },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = conditionMode == mode, onClick = null)
                        Text(mode.localizedName(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (conditionMode == JumpConditionMode.Values) {
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
                if (conditionMode == JumpConditionMode.NodeExists) {
                    NodeSelectorEditor(
                        draft = nodeDraft,
                        onDraftChange = { nodeDraft = it },
                        recentNodes = observedNodes,
                        recentTitle = stringResource(R.string.select_recent_element),
                        emptyMessage = stringResource(R.string.open_target_app_then_return),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetLabel != null && conditionValid,
                onClick = {
                    onSave(
                        requireNotNull(targetLabel),
                        when (conditionMode) {
                            JumpConditionMode.Always -> null
                            JumpConditionMode.Values ->
                                Condition.Equals(requireNotNull(left), requireNotNull(right), operator)
                            JumpConditionMode.NodeExists -> Condition.NodeExists(requireNotNull(nodeSelector))
                        },
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private enum class JumpConditionMode {
    Always,
    Values,
    NodeExists,
}

@Composable
private fun JumpConditionMode.localizedName(): String = stringResource(
    when (this) {
        JumpConditionMode.Always -> R.string.jump_condition_always
        JumpConditionMode.Values -> R.string.jump_condition_values
        JumpConditionMode.NodeExists -> R.string.jump_condition_node_exists
    },
)

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
                    SelectableStepButton(
                        index = index,
                        step = step,
                        selected = selectedIndex == index,
                        onSelect = { selectedIndex = index },
                    )
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
private fun SelectableStepButton(
    index: Int,
    step: Step,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onSelect, modifier = modifier) {
            Text(
                stringResource(R.string.numbered_step_label, index + 1, step.title()),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        OutlinedButton(onClick = onSelect, modifier = modifier) {
            Text(
                stringResource(R.string.numbered_step_label, index + 1, step.title()),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComparisonOperatorSelector(
    selected: ComparisonOperator,
    onSelect: (ComparisonOperator) -> Unit,
) {
    ComparisonOperator.entries.forEach { operator ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected == operator,
                    role = Role.RadioButton,
                    onClick = { onSelect(operator) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == operator, onClick = null)
            Text(operator.displayName())
        }
    }
    Text(
        stringResource(R.string.comparison_matching_behavior),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
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
    SystemAction.Notifications -> R.string.system_action_notifications
    SystemAction.QuickSettings -> R.string.system_action_quick_settings
    SystemAction.PowerDialog -> R.string.system_action_power_dialog
    SystemAction.LockScreen -> R.string.system_action_lock_screen
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
    com.aiindexfinger.model.SelectorRole.ScrollUntil -> R.string.selector_role_scroll_until
    com.aiindexfinger.model.SelectorRole.WaitForNode -> R.string.selector_role_wait_for_node
    com.aiindexfinger.model.SelectorRole.NodeCondition -> R.string.selector_role_node_condition
})

@Composable
private fun RunStatus.localizedName(): String = stringResource(
    when (this) {
        RunStatus.Completed -> R.string.run_status_completed
        RunStatus.CompletedWithWarnings -> R.string.run_status_completed_with_warnings
        RunStatus.Cancelled -> R.string.run_status_cancelled
        RunStatus.Failed -> R.string.run_status_failed
        RunStatus.Rejected -> R.string.run_status_rejected
        RunStatus.Unknown -> R.string.run_status_unknown
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
                        SelectableStepButton(
                            index = index,
                            step = step,
                            selected = selectedStepIndex == index,
                            onSelect = { selectedStepIndex = index },
                        )
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
    initialPostProcess: ReadNodeTextPostProcess = ReadNodeTextPostProcess(),
    initialDefaultValue: String? = null,
    onDismiss: () -> Unit,
    onSave: (NodeSelector, String, NodeAttribute, ReadNodeTextPostProcess, String?) -> Unit,
) {
    var selectorDraft by remember(initialSelector) {
        mutableStateOf(initialSelector?.toDraft() ?: NodeSelectorDraft())
    }
    var variableName by remember(initialVariableName) { mutableStateOf(initialVariableName) }
    var attribute by remember(initialAttribute) { mutableStateOf(initialAttribute) }
    var trim by remember(initialPostProcess) { mutableStateOf(initialPostProcess.trim) }
    var caseTransform by remember(initialPostProcess) { mutableStateOf(initialPostProcess.caseTransform) }
    var regexText by remember(initialPostProcess) { mutableStateOf(initialPostProcess.regex.orEmpty()) }
    var regexGroupText by remember(initialPostProcess) { mutableStateOf(initialPostProcess.regexGroup.toString()) }
    var splitDelimiter by remember(initialPostProcess) { mutableStateOf(initialPostProcess.splitDelimiter.orEmpty()) }
    var splitIndexText by remember(initialPostProcess) { mutableStateOf(initialPostProcess.splitIndex.toString()) }
    var useDefaultValue by remember(initialDefaultValue) { mutableStateOf(initialDefaultValue != null) }
    var defaultValueText by remember(initialDefaultValue) { mutableStateOf(initialDefaultValue.orEmpty()) }
    val selectedSelector = selectorDraft.toSelectorOrNull()
    val regex = regexText.trim().takeIf(String::isNotEmpty)
    val regexGroup = regexGroupText.toIntOrNull()
    val split = splitDelimiter.takeIf(String::isNotEmpty)
    val splitIndex = splitIndexText.toIntOrNull()
    val regexValid = regex == null || runCatching { Regex(regex) }.isSuccess
    val postProcessValid = regexValid && regexGroup != null && regexGroup >= 0 &&
        splitIndex != null && splitIndex >= 0
    val postProcess = if (postProcessValid) {
        ReadNodeTextPostProcess(
            trim = trim,
            caseTransform = caseTransform,
            regex = regex,
            regexGroup = requireNotNull(regexGroup),
            splitDelimiter = split,
            splitIndex = requireNotNull(splitIndex),
        )
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_element_attribute)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(
                    variableName,
                    { variableName = it },
                    stringResource(R.string.save_to_variable),
                    true,
                    errorText = stringResource(R.string.validation_blank_variable_name)
                        .takeIf { variableName.isBlank() },
                )
                Text(stringResource(R.string.attribute), fontWeight = FontWeight.SemiBold)
                NodeAttribute.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = attribute == option,
                                role = Role.RadioButton,
                                onClick = { attribute = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = attribute == option, onClick = null)
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
                Text(stringResource(R.string.read_processing), fontWeight = FontWeight.SemiBold)
                SelectorToggleRow(
                    label = stringResource(R.string.read_trim),
                    checked = trim,
                    onCheckedChange = { trim = it },
                )
                Text(stringResource(R.string.read_case_transform), fontWeight = FontWeight.SemiBold)
                ReadNodeTextCaseTransform.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = caseTransform == option,
                                role = Role.RadioButton,
                                onClick = { caseTransform = option },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = caseTransform == option, onClick = null)
                        Text(option.localizedName(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                NodeField(
                    regexText,
                    { regexText = it },
                    stringResource(R.string.read_regex),
                    errorText = stringResource(R.string.read_regex_error).takeUnless { regexValid },
                )
                NodeField(
                    regexGroupText,
                    { regexGroupText = it },
                    stringResource(R.string.read_regex_group),
                    numeric = true,
                    errorText = stringResource(R.string.read_non_negative_error)
                        .takeUnless { regexGroup != null && regexGroup >= 0 },
                )
                NodeField(
                    splitDelimiter,
                    { splitDelimiter = it },
                    stringResource(R.string.read_split_delimiter),
                )
                NodeField(
                    splitIndexText,
                    { splitIndexText = it },
                    stringResource(R.string.read_split_index),
                    numeric = true,
                    errorText = stringResource(R.string.read_non_negative_error)
                        .takeUnless { splitIndex != null && splitIndex >= 0 },
                )
                SelectorToggleRow(
                    label = stringResource(R.string.read_use_default_value),
                    checked = useDefaultValue,
                    onCheckedChange = { useDefaultValue = it },
                )
                if (useDefaultValue) {
                    OutlinedTextField(
                        value = defaultValueText,
                        onValueChange = { defaultValueText = it },
                        label = { Text(stringResource(R.string.read_default_value)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSelector != null && variableName.isNotBlank() && postProcess != null,
                onClick = {
                    onSave(
                        requireNotNull(selectedSelector),
                        preserveUnchangedOrTrim(variableName, initialVariableName),
                        attribute,
                        requireNotNull(postProcess),
                        defaultValueText.takeIf { useDefaultValue },
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
            enabled = matchIndex < Int.MAX_VALUE,
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

@Composable
private fun ReadNodeTextCaseTransform.localizedName(): String = stringResource(
    when (this) {
        ReadNodeTextCaseTransform.None -> R.string.read_case_none
        ReadNodeTextCaseTransform.Lowercase -> R.string.read_case_lowercase
        ReadNodeTextCaseTransform.Uppercase -> R.string.read_case_uppercase
    },
)

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
    val normalizedTarget = normalizedLaunchTarget(packageName, intentAction)
    val normalizedPackageName = normalizedTarget?.packageName.orEmpty()
    val normalizedIntentAction = normalizedTarget?.intentAction
    val targetResolvable = normalizedTarget?.let { target ->
        target.intentAction?.let { action ->
            Intent(action).setPackage(target.packageName).resolveActivity(context.packageManager) != null
        } ?: (context.packageManager.getLaunchIntentForPackage(target.packageName) != null)
    } == true
    val targetStatus = launchTargetEditorStatus(
        packageName = packageName,
        intentAction = intentAction,
        initialPackageName = initialPackageName,
        initialIntentAction = initialIntentAction,
        isResolvable = targetResolvable,
    )
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
                if (targetStatus != LaunchTargetEditorStatus.MissingPackage) {
                    val selectedApp = launchableApps.firstOrNull {
                        it.packageName == normalizedPackageName
                    }
                    val statusText = when (targetStatus) {
                        LaunchTargetEditorStatus.Resolvable -> when {
                            normalizedIntentAction != null -> stringResource(
                                R.string.launch_target_action_available,
                                normalizedIntentAction,
                            )
                            selectedApp != null -> stringResource(
                                R.string.selected_launchable_app,
                                selectedApp.label,
                            )
                            else -> stringResource(R.string.launch_target_available)
                        }
                        LaunchTargetEditorStatus.Unverified ->
                            stringResource(R.string.launch_target_unverified)
                        LaunchTargetEditorStatus.PreservedUnavailable ->
                            stringResource(R.string.launch_target_imported_unavailable)
                        LaunchTargetEditorStatus.Unavailable ->
                            stringResource(R.string.launch_target_unavailable)
                        LaunchTargetEditorStatus.MissingPackage -> ""
                    }
                    Text(
                        statusText,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = when (targetStatus) {
                            LaunchTargetEditorStatus.Resolvable -> Color(0xFF16815F)
                            LaunchTargetEditorStatus.Unverified ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                            LaunchTargetEditorStatus.PreservedUnavailable ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetStatus == LaunchTargetEditorStatus.Resolvable ||
                    targetStatus == LaunchTargetEditorStatus.Unverified ||
                    targetStatus == LaunchTargetEditorStatus.PreservedUnavailable,
                onClick = {
                    onAdd(
                        preserveUnchangedOrTrim(packageName, initialPackageName),
                        preserveUnchangedOptionalText(intentAction, initialIntentAction),
                    )
                },
            ) { Text(stringResource(confirmLabelRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

internal enum class LaunchTargetEditorStatus {
    MissingPackage,
    Resolvable,
    Unverified,
    PreservedUnavailable,
    Unavailable,
}

internal fun launchTargetEditorStatus(
    packageName: String,
    intentAction: String,
    initialPackageName: String = "",
    initialIntentAction: String? = null,
    isResolvable: Boolean,
): LaunchTargetEditorStatus {
    val target = normalizedLaunchTarget(packageName, intentAction)
        ?: return LaunchTargetEditorStatus.MissingPackage
    if (isResolvable) return LaunchTargetEditorStatus.Resolvable
    if (target.intentAction != null) return LaunchTargetEditorStatus.Unverified
    val initialTarget = normalizedLaunchTarget(initialPackageName, initialIntentAction)
    return if (initialTarget == target) {
        LaunchTargetEditorStatus.PreservedUnavailable
    } else {
        LaunchTargetEditorStatus.Unavailable
    }
}

internal fun normalizedOptionalText(value: String): String? = value.trim().ifBlank { null }

internal fun preserveUnchangedOptionalText(value: String, original: String?): String? =
    if (value == original.orEmpty()) original else normalizedOptionalText(value)

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
    fun applyReplacementSelector(selector: NodeSelector) {
        val draft = initialSelector?.toDraft()?.withReplacementSelector(selector) ?: selector.toDraft()
        packageName = draft.packageName
        viewId = draft.viewId
        text = draft.text
        textContains = draft.textMatchMode == TextMatchMode.Contains
        description = draft.contentDescription
        descriptionContains = draft.contentDescriptionMatchMode == TextMatchMode.Contains
        className = draft.className
        matchIndex = draft.matchIndex
        useAncestor = draft.useAncestor
        ancestorViewId = draft.ancestorViewId
        ancestorText = draft.ancestorText
        ancestorTextContains = draft.ancestorTextMatchMode == TextMatchMode.Contains
        ancestorDescription = draft.ancestorContentDescription
        ancestorDescriptionContains =
            draft.ancestorContentDescriptionMatchMode == TextMatchMode.Contains
        ancestorClassName = draft.ancestorClassName
    }
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
                        applyReplacementSelector(selector)
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
                                applyReplacementSelector(selector)
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
                SelectorToggleRow(
                    label = stringResource(R.string.text_contains),
                    checked = textContains,
                    enabled = text.isNotBlank(),
                    onCheckedChange = { textContains = it },
                )
                NodeField(description, { description = it }, stringResource(R.string.selector_content_description))
                SelectorToggleRow(
                    label = stringResource(R.string.description_contains),
                    checked = descriptionContains,
                    enabled = description.isNotBlank(),
                    onCheckedChange = { descriptionContains = it },
                )
                NodeField(className, { className = it }, stringResource(R.string.class_name))
                MatchIndexControl(matchIndex) { matchIndex = it }
                SelectorToggleRow(
                    label = stringResource(R.string.limit_to_ancestor),
                    checked = useAncestor,
                    onCheckedChange = { useAncestor = it },
                )
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
                    SelectorToggleRow(
                        label = stringResource(R.string.text_contains),
                        checked = ancestorTextContains,
                        enabled = ancestorText.isNotBlank(),
                        onCheckedChange = { ancestorTextContains = it },
                    )
                    NodeField(
                        ancestorDescription,
                        { ancestorDescription = it },
                        stringResource(R.string.ancestor_content_description),
                    )
                    SelectorToggleRow(
                        label = stringResource(R.string.description_contains),
                        checked = ancestorDescriptionContains,
                        enabled = ancestorDescription.isNotBlank(),
                        onCheckedChange = { ancestorDescriptionContains = it },
                    )
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
                            initialSelector,
                        )
                        val count = selector?.let {
                            AutomationAccessibilityService.instance?.countMatches(it)
                        } ?: 0
                        matchResult = when (count) {
                            0 -> context.getString(R.string.selector_no_matches)
                            in 1..matchIndex -> context.resources.getQuantityString(
                                R.plurals.selector_index_unavailable,
                                count,
                                count,
                                matchIndex + 1,
                            )
                            1 -> context.getString(R.string.selector_unique_ready)
                            else -> context.resources.getQuantityString(
                                R.plurals.selector_match_available,
                                count,
                                count,
                                matchIndex + 1,
                            )
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
                                initialSelector,
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

private enum class ImageClickCaptureMode { Crop, ClickPoint }

internal fun imageClickTemplateSelectionCanSave(
    hasInitialTemplate: Boolean,
    captureReady: Boolean,
    replacementComplete: Boolean,
): Boolean = replacementComplete || (hasInitialTemplate && !captureReady)

@Composable
private fun ImageClickStepDialog(
    initialStep: Step.ImageClick? = null,
    confirmLabelRes: Int = R.string.add,
    onDismiss: () -> Unit,
    onAdd: (Step.ImageClick, String?) -> Unit,
) {
    val savedTemplatePreview = remember(initialStep) { initialStep?.let(::decodeImageTemplate) }
    val existingTemplateValid = initialStep == null || savedTemplatePreview != null
    DisposableEffect(savedTemplatePreview) {
        onDispose {
            AutomationAccessibilityService.cancelPendingScreenCapture()
            savedTemplatePreview?.recycle()
        }
    }
    val context = LocalContext.current
    val captureState by AutomationAccessibilityService.screenCaptureState.collectAsStateWithLifecycle()
    val displaySize = currentDisplayPixelSize()
    var captureGeometryChanged by remember { mutableStateOf(false) }
    var packageName by remember(initialStep) { mutableStateOf(initialStep?.packageName.orEmpty()) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var cropLeft by remember { mutableStateOf("") }
    var cropTop by remember { mutableStateOf("") }
    var cropRight by remember { mutableStateOf("") }
    var cropBottom by remember { mutableStateOf("") }
    var captureMode by remember { mutableStateOf(ImageClickCaptureMode.Crop) }
    var replacementCapturePending by remember(initialStep) { mutableStateOf(false) }
    var preCapturePackageName by remember(initialStep) { mutableStateOf<String?>(null) }
    var preCaptureClickXText by remember(initialStep) { mutableStateOf<String?>(null) }
    var preCaptureClickYText by remember(initialStep) { mutableStateOf<String?>(null) }
    var templateClickXText by remember(initialStep) {
        mutableStateOf(initialStep?.templateClickX?.toString().orEmpty())
    }
    var templateClickYText by remember(initialStep) {
        mutableStateOf(initialStep?.templateClickY?.toString().orEmpty())
    }
    var minimumScorePercent by remember(initialStep) {
        mutableStateOf(imageMatchPercentText(initialStep?.minimumScorePermille ?: 920))
    }
    var selectionMode by remember(initialStep) {
        mutableStateOf(initialStep?.selectionMode ?: ImageClickSelectionMode.BestMatch)
    }
    var maxClicksText by remember(initialStep) {
        mutableStateOf((initialStep?.maxClicks ?: 20).toString())
    }
    var clickIntervalMillisText by remember(initialStep) {
        mutableStateOf((initialStep?.clickIntervalMillis ?: 200).toString())
    }
    var scaleTolerancePermille by remember(initialStep) {
        mutableStateOf(initialStep?.scaleTolerancePermille ?: 0)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val minimumScorePermille = imageMatchPercentToPermille(minimumScorePercent)
    val maxClicks = maxClicksText.toIntOrNull()?.takeIf { it in 1..100 }
    val clickIntervalMillis = clickIntervalMillisText.toLongOrNull()?.takeIf { it in 0..10_000 }
    val batchSettingsValid = maxClicks != null && clickIntervalMillis != null
    val selectedMaxClicks = maxClicks ?: initialStep?.maxClicks ?: 20
    val selectedClickIntervalMillis = clickIntervalMillis ?: initialStep?.clickIntervalMillis ?: 200
    val editableCrop = cropBoundsOrNull(cropLeft, cropTop, cropRight, cropBottom)
    val editableTemplateWidth = editableCrop?.let { it.right - it.left } ?: initialStep?.templateWidth
    val editableTemplateHeight = editableCrop?.let { it.bottom - it.top } ?: initialStep?.templateHeight
    val templateClickPoint = templateClickXText.toIntOrNull()?.let { x ->
        templateClickYText.toIntOrNull()?.let { y -> ScreenPoint(x, y) }
    }
    val clickPointFieldsBlank = templateClickXText.isBlank() && templateClickYText.isBlank()
    val clickPointInputValid = templateClickPoint?.let { point ->
        editableTemplateWidth?.let { width ->
            editableTemplateHeight?.let { height ->
                point.x in 0 until width && point.y in 0 until height
            }
        }
    } == true
    val legacyCenterAllowed = initialStep?.let { step ->
        step.templateClickX == null && step.templateClickY == null
    } == true
    val clickPointInputAllowed = clickPointInputValid || (clickPointFieldsBlank && legacyCenterAllowed)

    LaunchedEffect(captureState, displaySize) {
        val state = captureState as? ScreenCaptureState.Ready
        if (state != null && !captureBoundsMatchDisplay(
                state.screenBounds,
                displaySize.width,
                displaySize.height,
            )
        ) {
            captureGeometryChanged = true
            AutomationAccessibilityService.discardScreenCapture()
        } else if (captureState is ScreenCaptureState.Armed) {
            captureGeometryChanged = false
        }
    }

    LaunchedEffect(captureState) {
        val state = captureState
        if (state is ScreenCaptureState.Ready) {
            if (initialStep != null) {
                replacementCapturePending = true
                preCapturePackageName = packageName
                preCaptureClickXText = templateClickXText
                preCaptureClickYText = templateClickYText
            }
            packageName = state.targetPackage
            dragStart = null
            dragEnd = null
            cropLeft = ""
            cropTop = ""
            cropRight = ""
            cropBottom = ""
            captureMode = ImageClickCaptureMode.Crop
            templateClickXText = ""
            templateClickYText = ""
            error = null
        } else if (replacementCapturePending && initialStep != null) {
            replacementCapturePending = false
            packageName = requireNotNull(preCapturePackageName)
            templateClickXText = requireNotNull(preCaptureClickXText)
            templateClickYText = requireNotNull(preCaptureClickYText)
            preCapturePackageName = null
            preCaptureClickXText = null
            preCaptureClickYText = null
            error = null
        }
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
                Text(
                    stringResource(R.string.image_click_template_privacy_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (initialStep != null) {
                    Text(
                        stringResource(R.string.image_click_saved_template_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (savedTemplatePreview != null) {
                        ImageClickPointPicker(
                            bitmap = savedTemplatePreview,
                            selectedPoint = templateClickPoint ?: ScreenPoint(
                                initialStep.templateWidth / 2,
                                initialStep.templateHeight / 2,
                            ),
                            onPointSelected = {
                                templateClickXText = it.x.toString()
                                templateClickYText = it.y.toString()
                            },
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
                        Text(
                            templateClickPoint?.let {
                                stringResource(R.string.image_click_point_selected, it.x, it.y)
                            } ?: stringResource(R.string.image_click_point_legacy_center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.image_click_point_coordinates), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.image_click_point_coordinates_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                NodeField(
                    templateClickXText,
                    { templateClickXText = it },
                    stringResource(R.string.image_click_point_x),
                    true,
                )
                NodeField(
                    templateClickYText,
                    { templateClickYText = it },
                    stringResource(R.string.image_click_point_y),
                    true,
                )
                if (!clickPointFieldsBlank && !clickPointInputValid) {
                    Text(
                        stringResource(R.string.image_click_point_outside_crop),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                if (clickPointFieldsBlank && !legacyCenterAllowed) {
                    Text(
                        stringResource(R.string.image_click_point_required),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selectionMode == ImageClickSelectionMode.AllMatches,
                            role = Role.Switch,
                            onValueChange = { clickAllMatches ->
                                selectionMode = if (clickAllMatches) {
                                    ImageClickSelectionMode.AllMatches
                                } else {
                                    ImageClickSelectionMode.BestMatch
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.image_click_all_matches))
                    Switch(
                        checked = selectionMode == ImageClickSelectionMode.AllMatches,
                        onCheckedChange = null,
                    )
                }
                if (selectionMode == ImageClickSelectionMode.AllMatches) {
                    NodeField(
                        maxClicksText,
                        { maxClicksText = it },
                        stringResource(R.string.image_click_max_clicks),
                        true,
                    )
                    NodeField(
                        clickIntervalMillisText,
                        { clickIntervalMillisText = it },
                        stringResource(R.string.image_click_click_interval_millis),
                        true,
                    )
                }
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
                if (minimumScorePermille == null) {
                    Text(
                        stringResource(R.string.image_click_percentage_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                if (selectionMode == ImageClickSelectionMode.AllMatches && !batchSettingsValid) {
                    Text(
                        stringResource(R.string.image_click_batch_settings_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                HorizontalDivider()
                if (captureGeometryChanged) {
                    Text(
                        stringResource(R.string.screenshot_display_changed),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
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
                    )
                    is ScreenCaptureState.Error -> Text(
                        state.message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                    is ScreenCaptureState.Ready -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val cropSelected = captureMode == ImageClickCaptureMode.Crop
                            if (cropSelected) {
                                Button(
                                    onClick = { captureMode = ImageClickCaptureMode.Crop },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.image_click_select_crop)) }
                            } else {
                                OutlinedButton(
                                    onClick = { captureMode = ImageClickCaptureMode.Crop },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.image_click_select_crop)) }
                            }
                            val pointEnabled = cropBoundsOrNull(cropLeft, cropTop, cropRight, cropBottom) != null
                            if (captureMode == ImageClickCaptureMode.ClickPoint) {
                                Button(
                                    onClick = { captureMode = ImageClickCaptureMode.ClickPoint },
                                    enabled = pointEnabled,
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.image_click_select_point)) }
                            } else {
                                OutlinedButton(
                                    onClick = { captureMode = ImageClickCaptureMode.ClickPoint },
                                    enabled = pointEnabled,
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.image_click_select_point)) }
                            }
                        }
                        Text(
                            stringResource(
                                if (captureMode == ImageClickCaptureMode.Crop) {
                                    R.string.image_click_crop_hint
                                } else {
                                    R.string.image_click_point_hint
                                },
                            ),
                            fontSize = 12.sp,
                        )
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
                                    .pointerInput(state, captureSize, captureMode) {
                                        when (captureMode) {
                                            ImageClickCaptureMode.Crop -> detectDragGestures(
                                                onDragStart = {
                                                    dragStart = it
                                                    dragEnd = it
                                                    templateClickXText = ""
                                                    templateClickYText = ""
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
                                                        cropRight = (maxOf(first.x, second.x) + 1)
                                                            .coerceAtMost(state.bitmap.width).toString()
                                                        cropBottom = (maxOf(first.y, second.y) + 1)
                                                            .coerceAtMost(state.bitmap.height).toString()
                                                    }
                                                },
                                            )
                                            ImageClickCaptureMode.ClickPoint -> detectTapGestures { offset ->
                                                val screenshotPoint = mapFitCenterTapToScreen(
                                                    offset.x,
                                                    offset.y,
                                                    captureSize.width,
                                                    captureSize.height,
                                                    state.bitmap.width,
                                                    state.bitmap.height,
                                                )
                                                val bounds = cropBoundsOrNull(
                                                    cropLeft,
                                                    cropTop,
                                                    cropRight,
                                                    cropBottom,
                                                )
                                                val relativePoint = if (bounds != null && screenshotPoint != null) {
                                                    templatePointRelativeToCrop(bounds, screenshotPoint)
                                                } else {
                                                    null
                                                }
                                                if (relativePoint == null) {
                                                    error = context.getString(R.string.image_click_point_outside_crop)
                                                } else {
                                                    templateClickXText = relativePoint.x.toString()
                                                    templateClickYText = relativePoint.y.toString()
                                                    error = null
                                                }
                                            }
                                        }
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
                                val point = templateClickPoint
                                val bounds = cropBoundsOrNull(cropLeft, cropTop, cropRight, cropBottom)
                                if (point != null && bounds != null) {
                                    val scale = minOf(
                                        size.width / state.bitmap.width,
                                        size.height / state.bitmap.height,
                                    )
                                    val imageWidth = state.bitmap.width * scale
                                    val imageHeight = state.bitmap.height * scale
                                    val center = Offset(
                                        x = (size.width - imageWidth) / 2f + (bounds.left + point.x + 0.5f) * scale,
                                        y = (size.height - imageHeight) / 2f + (bounds.top + point.y + 0.5f) * scale,
                                    )
                                    drawCircle(Color(0xFFD04F3D), radius = 7.dp.toPx(), center = center)
                                    drawLine(
                                        Color.White,
                                        center - Offset(9.dp.toPx(), 0f),
                                        center + Offset(9.dp.toPx(), 0f),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                    drawLine(
                                        Color.White,
                                        center - Offset(0f, 9.dp.toPx()),
                                        center + Offset(0f, 9.dp.toPx()),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                }
                            }
                        }
                        templateClickPoint?.let {
                            Text(stringResource(R.string.image_click_point_selected, it.x, it.y), fontSize = 12.sp)
                        }
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
            val captureReady = captureState is ScreenCaptureState.Ready
            val replacementComplete = captureReady && editableCrop != null && clickPointInputValid
            TextButton(
                enabled = packageName.isNotBlank() && minimumScorePermille != null &&
                    (selectionMode != ImageClickSelectionMode.AllMatches || batchSettingsValid) &&
                    clickPointInputAllowed &&
                    (captureReady || existingTemplateValid) &&
                    imageClickTemplateSelectionCanSave(
                        hasInitialTemplate = initialStep != null,
                        captureReady = captureReady,
                        replacementComplete = replacementComplete,
                    ),
                onClick = {
                    val state = captureState as? ScreenCaptureState.Ready
                    val bounds = cropBoundsOrNull(cropLeft, cropTop, cropRight, cropBottom)
                    if (state != null && bounds == null) {
                        error = context.getString(R.string.image_click_replacement_incomplete)
                        return@TextButton
                    }
                    if (state == null) {
                        initialStep?.let {
                            if (!existingTemplateValid) {
                                error = context.getString(R.string.image_click_saved_template_invalid)
                                return@TextButton
                            }
                            if (!clickPointInputAllowed) {
                                error = context.getString(
                                    if (clickPointFieldsBlank) {
                                        R.string.image_click_point_required
                                    } else {
                                        R.string.image_click_point_outside_crop
                                    },
                                )
                                return@TextButton
                            }
                            onAdd(
                                it.copy(
                                    packageName = packageName.trim(),
                                    minimumScorePermille = requireNotNull(minimumScorePermille),
                                    scaleTolerancePermille = scaleTolerancePermille,
                                    templateClickX = templateClickPoint?.x,
                                    templateClickY = templateClickPoint?.y,
                                    selectionMode = selectionMode,
                                    maxClicks = selectedMaxClicks,
                                    clickIntervalMillis = selectedClickIntervalMillis,
                                ),
                                null,
                            )
                        }
                        return@TextButton
                    }
                    val replacementBounds = requireNotNull(bounds)
                    if (packageName.trim() != state.targetPackage) {
                        error = context.getString(
                            R.string.image_click_capture_package_mismatch,
                            state.targetPackage,
                        )
                        return@TextButton
                    }
                    val clickPoint = templateClickPoint
                    if (clickPoint == null) {
                        error = context.getString(R.string.image_click_point_required)
                        return@TextButton
                    }
                    if (clickPoint.x !in 0 until (replacementBounds.right - replacementBounds.left) ||
                        clickPoint.y !in 0 until (replacementBounds.bottom - replacementBounds.top)
                    ) {
                        error = context.getString(R.string.image_click_point_outside_crop)
                        return@TextButton
                    }
                    if (mapBitmapCropToTargetScreen(
                            crop = replacementBounds,
                            bitmapWidth = state.bitmap.width,
                            bitmapHeight = state.bitmap.height,
                            screenBounds = state.screenBounds,
                            targetBounds = state.targetBounds,
                        ) == null
                    ) {
                        error = context.getString(R.string.live_action_image_outside_target)
                        return@TextButton
                    }
                    val crop = cropTemplate(state.bitmap, replacementBounds)
                    if (crop == null) {
                        error = context.getString(
                            R.string.image_click_crop_too_small,
                            Step.ImageClick.MIN_TEMPLATE_SIZE,
                        )
                    } else {
                        val encoded = encodeTemplatePng(crop, clickPoint)
                        if (crop !== state.bitmap) crop.recycle()
                        if (encoded == null) {
                            error = context.getString(R.string.image_click_template_too_large)
                        } else {
                            val savedStep = Step.ImageClick(
                                    id = initialStep?.id ?: "pending",
                                    packageName = packageName.trim(),
                                    templatePngBase64 = encoded.base64,
                                    templateWidth = encoded.width,
                                    templateHeight = encoded.height,
                                    minimumScorePermille = requireNotNull(minimumScorePermille),
                                    ambiguityMarginPermille = initialStep?.ambiguityMarginPermille ?: 25,
                                    scaleTolerancePermille = scaleTolerancePermille,
                                    timeoutMillis = initialStep?.timeoutMillis,
                                    failurePolicy = initialStep?.failurePolicy ?: FailurePolicy.Stop,
                                    templateClickX = requireNotNull(encoded.templateClickPoint).x,
                                    templateClickY = requireNotNull(encoded.templateClickPoint).y,
                                    selectionMode = selectionMode,
                                    maxClicks = selectedMaxClicks,
                                    clickIntervalMillis = selectedClickIntervalMillis,
                                )
                            val colorMode = context.getString(
                                if (encoded.convertedToGrayscale) {
                                    R.string.image_click_template_color_grayscale
                                } else {
                                    R.string.image_click_template_color_full
                                },
                            )
                            onAdd(
                                savedStep,
                                context.getString(
                                    R.string.image_click_template_optimized,
                                    encoded.sourceWidth,
                                    encoded.sourceHeight,
                                    encoded.width,
                                    encoded.height,
                                    colorMode,
                                    encoded.pngByteCount,
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

@Composable
private fun ImageClickPointPicker(
    bitmap: Bitmap,
    selectedPoint: ScreenPoint,
    onPointSelected: (ScreenPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier.onSizeChanged { previewSize = it }) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.image_click_saved_template_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, previewSize) {
                    detectTapGestures { offset ->
                        mapFitCenterTapToScreen(
                            tapX = offset.x,
                            tapY = offset.y,
                            containerWidth = previewSize.width,
                            containerHeight = previewSize.height,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                        )?.let(onPointSelected)
                    }
                },
        ) {
            if (selectedPoint.x !in 0 until bitmap.width || selectedPoint.y !in 0 until bitmap.height) return@Canvas
            val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
            val imageWidth = bitmap.width * scale
            val imageHeight = bitmap.height * scale
            val center = Offset(
                x = (size.width - imageWidth) / 2f + (selectedPoint.x + 0.5f) * scale,
                y = (size.height - imageHeight) / 2f + (selectedPoint.y + 0.5f) * scale,
            )
            drawCircle(Color(0xFFD04F3D), radius = 7.dp.toPx(), center = center)
            drawLine(
                Color.White,
                center - Offset(9.dp.toPx(), 0f),
                center + Offset(9.dp.toPx(), 0f),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                Color.White,
                center - Offset(0f, 9.dp.toPx()),
                center + Offset(0f, 9.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
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
    val automationConnected by AutomationAccessibilityService.connected.collectAsStateWithLifecycle()
    val displaySize = currentDisplayPixelSize()
    var captureGeometryChanged by remember { mutableStateOf(false) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(captureState, displaySize) {
        val state = captureState as? ScreenCaptureState.Ready
        if (state != null && !captureBoundsMatchDisplay(
                state.screenBounds,
                displaySize.width,
                displaySize.height,
            )
        ) {
            captureGeometryChanged = true
            AutomationAccessibilityService.discardScreenCapture()
        } else if (captureState is ScreenCaptureState.Armed) {
            captureGeometryChanged = false
        }
    }

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
    if (captureGeometryChanged) {
        Text(
            stringResource(R.string.screenshot_display_changed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
    }
    OutlinedButton(
        onClick = {
            AutomationAccessibilityService.instance?.capturePreviousApp()
        },
        enabled = AutomationAccessibilityService.instance != null &&
            automationConnected &&
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
                            val point = mapCaptureOffset(state, captureSize, offset)
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
        ScreenCaptureState.Idle -> when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Text(
                stringResource(R.string.visual_capture_requires_android_11),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            !automationConnected || AutomationAccessibilityService.instance == null -> Text(
                stringResource(R.string.selector_capture_requires_automation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

internal fun nodeSelectorOrNull(
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
    originalSelector: NodeSelector? = null,
): NodeSelector? = NodeSelectorDraft(
        packageName = packageName,
        viewId = viewId,
        text = text,
        textMatchMode = if (textContains) TextMatchMode.Contains else TextMatchMode.Exact,
        contentDescription = description,
        contentDescriptionMatchMode = if (descriptionContains) {
            TextMatchMode.Contains
        } else {
            TextMatchMode.Exact
        },
        className = className,
        matchIndex = matchIndex,
        useAncestor = useAncestor,
        ancestorViewId = ancestorViewId,
        ancestorText = ancestorText,
        ancestorTextMatchMode = if (ancestorTextContains) TextMatchMode.Contains else TextMatchMode.Exact,
        ancestorContentDescription = ancestorDescription,
        ancestorContentDescriptionMatchMode = if (ancestorDescriptionContains) {
            TextMatchMode.Contains
        } else {
            TextMatchMode.Exact
        },
        ancestorClassName = ancestorClassName,
        originalSelector = originalSelector,
    )
    .toSelectorOrNull()

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
    numeric: Boolean = false,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        isError = errorText != null,
        supportingText = errorText?.let { message ->
            {
                Text(
                    message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepRow(
    index: Int,
    step: Step,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canMoveToTop: Boolean,
    canMoveToBottom: Boolean,
    canEdit: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
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
        val dragHandleDescription = stringResource(R.string.drag_reorder_handle)
        Text(
            stringResource(R.string.drag_reorder_handle),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = dragHandleDescription }
                .pointerInput(step.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragBy(dragAmount.y)
                        },
                    )
                },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${index + 1}", modifier = Modifier.size(32.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(step.title(), fontWeight = FontWeight.SemiBold)
            if (step is Step.Click || step is Step.LongClick || step is Step.ReadNodeText ||
                step is Step.Scroll || step is Step.ScrollUntil
            ) {
                val selector = when (step) {
                    is Step.Click -> step.selector
                    is Step.LongClick -> step.selector
                    is Step.ReadNodeText -> step.selector
                    is Step.Scroll -> step.selector
                    is Step.ScrollUntil -> step.selector
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
            TextButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.fillMaxWidth().testTag(stepOperationTag(step.id, "up")),
            ) { Text(stringResource(R.string.move_up)) }
            TextButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.fillMaxWidth().testTag(stepOperationTag(step.id, "down")),
            ) { Text(stringResource(R.string.move_down)) }
            TextButton(onClick = onMoveToTop, enabled = canMoveToTop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.move_to_top))
            }
            TextButton(onClick = onMoveToBottom, enabled = canMoveToBottom, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.move_to_bottom))
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
    hasLabels: Boolean,
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
                    val unavailableReason = operation.unavailableReason(
                        hasSteps,
                        serviceConnected,
                        hasLabels,
                    )
                    OutlinedButton(
                        enabled = unavailableReason == null,
                        onClick = { onSelect(operation) },
                        modifier = Modifier.fillMaxWidth().testTag(workflowOperationTag(operation)),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(operation.localizedLabel())
                            unavailableReason?.let { reason ->
                                Text(reason.localizedMessage(), fontSize = 12.sp)
                            }
                        }
                    }
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
private fun WorkflowOperationUnavailableReason.localizedMessage(): String = stringResource(
    when (this) {
        WorkflowOperationUnavailableReason.AutomationServiceRequired ->
            R.string.operation_requires_automation_service
        WorkflowOperationUnavailableReason.ExistingStepRequired ->
            R.string.operation_requires_existing_step
        WorkflowOperationUnavailableReason.LabelRequired -> R.string.operation_requires_label
    },
)

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
        WorkflowEditorOperation.ScrollUntil -> R.string.scroll_until
        WorkflowEditorOperation.InputText -> R.string.input_text
        WorkflowEditorOperation.Swipe -> R.string.swipe
        WorkflowEditorOperation.Delay -> R.string.wait_action
        WorkflowEditorOperation.GlobalBack -> R.string.system_action_back
        WorkflowEditorOperation.GlobalHome -> R.string.system_action_home
        WorkflowEditorOperation.GlobalRecents -> R.string.system_action_recents
        WorkflowEditorOperation.GlobalNotifications -> R.string.system_action_notifications
        WorkflowEditorOperation.GlobalQuickSettings -> R.string.system_action_quick_settings
        WorkflowEditorOperation.GlobalPowerDialog -> R.string.system_action_power_dialog
        WorkflowEditorOperation.GlobalLockScreen -> R.string.system_action_lock_screen
        WorkflowEditorOperation.WaitForNode -> R.string.wait_for_element
        WorkflowEditorOperation.SetVariable -> R.string.set_variable
        WorkflowEditorOperation.ReadNodeText -> R.string.read_element_attribute
        WorkflowEditorOperation.Repeat -> R.string.repeat_steps
        WorkflowEditorOperation.Label -> R.string.add_label
        WorkflowEditorOperation.JumpIf -> R.string.jump_to_label
        WorkflowEditorOperation.VariableCondition -> R.string.variable_condition
        WorkflowEditorOperation.NodeCondition -> R.string.element_exists_condition
    },
)

@Composable
private fun FailurePolicy.label(): String = when (this) {
    FailurePolicy.Stop -> stringResource(R.string.failure_policy_stop)
    FailurePolicy.Continue -> stringResource(R.string.failure_policy_continue)
    is FailurePolicy.Retry -> pluralStringResource(R.plurals.failure_policy_retry, attempts, attempts)
}

private fun Step.isActionEditable(): Boolean = when (this) {
    is Step.Click, is Step.RecordedClick, is Step.Delay, is Step.GlobalAction, is Step.InputText, is Step.LaunchApp,
    is Step.ImageClick,
    is Step.LongClick, is Step.ReadNodeText, is Step.Repeat, is Step.SetVariable, is Step.Swipe,
    is Step.WaitForNode -> true
    is Step.Scroll, is Step.ScrollUntil, is Step.Tap -> true
    is Step.IfElse, is Step.Label, is Step.JumpIf -> true
}

@Composable
private fun Step.title(): String = when (this) {
    is Step.Click -> stringResource(R.string.step_click_element)
    is Step.RecordedClick -> if (targetMode == RecordedClickTargetMode.Control) {
        stringResource(
            R.string.recorded_click_step_control,
            control.text ?: control.contentDescription ?: control.viewId ?: control.className.orEmpty(),
        )
    } else {
        stringResource(R.string.recorded_click_step_coordinates, x, y)
    }
    is Step.ImageClick -> stringResource(R.string.image_click_step_title, templateWidth, templateHeight)
    is Step.Delay -> stringResource(R.string.step_delay, durationMillis)
    is Step.GlobalAction -> action.displayName()
    is Step.IfElse -> when (val current = condition) {
        is Condition.Equals -> stringResource(R.string.step_if_variable, current.operator.displayName())
        is Condition.NodeExists -> stringResource(R.string.if_element_exists)
    }
    is Step.Label -> stringResource(R.string.step_label, name)
    is Step.JumpIf -> stringResource(
        if (condition == null) R.string.step_jump else R.string.step_jump_if,
        targetLabel,
    )
    is Step.InputText -> {
        val sourceValue = value ?: variableName?.let(Value::Variable) ?: Value.Literal(text)
        val source = when (sourceValue) {
            is Value.Literal -> stringResource(R.string.literal_text)
            is Value.Variable -> stringResource(R.string.variable_value, sourceValue.name)
            is Value.Template -> stringResource(R.string.template_label)
        }
        stringResource(if (inputMethod == TextInputMethod.Paste) R.string.step_paste else R.string.step_input, source)
    }
    is Step.Repeat -> pluralStringResource(R.plurals.step_repeat, times, times)
    is Step.Scroll -> stringResource(scrollDirectionLabelRes(direction))
    is Step.ScrollUntil -> stringResource(
        R.string.step_scroll_until,
        stringResource(scrollDirectionLabelRes(direction)),
    )
    is Step.LaunchApp -> stringResource(R.string.step_launch_app, packageName)
    is Step.LongClick -> stringResource(R.string.long_click_element)
    is Step.ReadNodeText -> stringResource(R.string.step_read_attribute, attribute.displayName(), variableName)
    is Step.SetVariable -> stringResource(R.string.step_set_variable, name)
    is Step.Swipe -> stringResource(R.string.step_swipe, startX, startY, endX, endY)
    is Step.Tap -> stringResource(R.string.step_tap, x, y)
    is Step.WaitForNode -> stringResource(if (mustExist) R.string.wait_element_appear else R.string.wait_element_disappear)
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
                pluralStringResource(
                    R.plurals.workflow_row_summary,
                    workflow.steps.size,
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
private const val RUN_HISTORY_DEEP_LINK_SETTLE_MILLIS = 500L
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
internal fun folderFilterTag(folderId: String) = boundedIdentityTag("folder-filter", folderId)
internal fun folderRenameTag(folderId: String) = boundedIdentityTag("folder-rename", folderId)
internal fun folderDeleteTag(folderId: String) = boundedIdentityTag("folder-delete", folderId)
internal fun folderMoveWorkflowTag(workflowId: String) =
    boundedIdentityTag("folder-move-workflow", workflowId)
internal fun workflowRunTag(workflowId: String) = boundedIdentityTag("workflow-run", workflowId)
internal fun workflowDebugTag(workflowId: String) = boundedIdentityTag("workflow-debug", workflowId)
internal fun folderDestinationTag(folderId: String) =
    boundedIdentityTag("folder-destination", folderId)
internal fun stepOperationTag(stepId: String, operation: String) =
    boundedIdentityTag("step", stepId, operation)

internal fun boundedIdentityTag(
    prefix: String,
    identity: String,
    suffix: String? = null,
): String {
    val direct = listOfNotNull(prefix, identity, suffix).joinToString("-")
    if (direct.toByteArray(StandardCharsets.UTF_8).size <= MAX_DYNAMIC_TEST_TAG_BYTES) {
        return direct
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
    val encoded = buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
    return listOfNotNull(prefix, "sha256", encoded, suffix).joinToString("-")
}

private const val MAX_DYNAMIC_TEST_TAG_BYTES = 256
private const val HEX_DIGITS = "0123456789abcdef"

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
                    pluralStringResource(
                        R.plurals.workflow_difference_count,
                        comparison.differences.size,
                        comparison.differences.size,
                    ),
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

internal fun Workflow.exportFileName(): String {
    val safeName = name
        .trim()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}._-]+"), "_")
        .trim('.', '_')
        .take(60)
        .ifBlank { "workflow" }
    return "$safeName.aiflow.json"
}

internal fun canonicalWorkflowForExport(
    requested: Workflow,
    canonical: WorkflowLibrary?,
): Workflow? = canonical?.workflows?.firstOrNull { it.id == requested.id }
    ?: requested.takeIf { canonical == null }

internal fun canonicalLibraryForExport(
    requested: WorkflowLibrary?,
    canonical: WorkflowLibrary?,
): WorkflowLibrary? = canonical ?: requested

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
                    stringResource(R.string.workflow_check_read_only),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
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
                        pluralStringResource(
                            R.plurals.workflow_test_structure_issues,
                            report.validationIssues.size,
                            report.validationIssues.size,
                        ),
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
                if (report.coordinateIssues.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.workflow_test_coordinate_issues),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    report.coordinateIssues.forEach { issue ->
                        val location = workflow.steps.uniqueRunLocationTo(issue.stepId)
                            ?.localizedName()
                            ?: stringResource(R.string.workflow_step_type_unknown)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.workflow_test_coordinate_issue,
                                    location,
                                    issue.displayWidth,
                                    issue.displayHeight,
                                ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                            workflow.steps.uniquePathTo(issue.stepId)?.let { path ->
                                TextButton(onClick = { onEditStep(path) }) {
                                    Text(stringResource(R.string.edit_step))
                                }
                            }
                        }
                    }
                }
                if (report.imageTemplateIssues.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.workflow_test_image_template_issues),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    report.imageTemplateIssues.forEach { issue ->
                        val location = workflow.steps.uniqueRunLocationTo(issue.stepId)
                            ?.localizedName()
                            ?: stringResource(R.string.workflow_step_type_unknown)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.workflow_test_image_template_issue,
                                    location,
                                ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                            workflow.steps.uniquePathTo(issue.stepId)?.let { path ->
                                TextButton(onClick = { onEditStep(path) }) {
                                    Text(stringResource(R.string.edit_step))
                                }
                            }
                        }
                    }
                }
                if (report.imageClickTimeoutWarnings.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.workflow_test_image_click_timeout_warnings),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    report.imageClickTimeoutWarnings.forEach { warning ->
                        val location = workflow.steps.uniqueRunLocationTo(warning.stepId)
                            ?.localizedName()
                            ?: stringResource(R.string.workflow_step_type_unknown)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    R.string.workflow_test_image_click_timeout_warning,
                                    location,
                                    warning.minimumIntervalMillis,
                                    warning.effectiveTimeoutMillis,
                                ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp,
                            )
                            workflow.steps.uniquePathTo(warning.stepId)?.let { path ->
                                TextButton(onClick = { onEditStep(path) }) {
                                    Text(stringResource(R.string.edit_step))
                                }
                            }
                        }
                    }
                }
                if (report.launchTargets.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.workflow_test_launch_targets), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    report.launchTargets.forEach { target ->
                        Text(
                            stringResource(
                                when (target.status) {
                                    LaunchTargetStatus.Available -> R.string.workflow_test_target_available
                                    LaunchTargetStatus.Unverified -> R.string.workflow_test_target_unverified
                                    LaunchTargetStatus.Unavailable -> R.string.workflow_test_target_unavailable
                                },
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
                        val result = if (check.matchCount == null) {
                            stringResource(R.string.workflow_test_selector_not_checked)
                        } else when (check.expectation) {
                            com.aiindexfinger.automation.SelectorPreflightExpectation.RequiredPresent -> {
                                if (check.requirementSatisfied == true) {
                                    pluralStringResource(
                                        R.plurals.workflow_test_selector_available,
                                        check.matchCount,
                                        check.matchCount,
                                    )
                                } else {
                                    pluralStringResource(
                                        R.plurals.workflow_test_selector_index_missing,
                                        check.matchCount,
                                        check.matchCount,
                                        check.use.selector.matchIndex + 1,
                                    )
                                }
                            }
                            com.aiindexfinger.automation.SelectorPreflightExpectation.RequiredAbsent -> {
                                if (check.requirementSatisfied == true) {
                                    stringResource(R.string.workflow_test_selector_absent)
                                } else {
                                    pluralStringResource(
                                        R.plurals.workflow_test_selector_waiting_disappearance,
                                        check.matchCount,
                                        check.matchCount,
                                    )
                                }
                            }
                            com.aiindexfinger.automation.SelectorPreflightExpectation.ObserveOnly -> {
                                if (check.matchCount > check.use.selector.matchIndex) {
                                    pluralStringResource(
                                        R.plurals.workflow_test_condition_currently_true,
                                        check.matchCount,
                                        check.matchCount,
                                    )
                                } else {
                                    stringResource(R.string.workflow_test_condition_currently_false)
                                }
                            }
                        }
                        val location = workflow.steps.uniqueRunLocationTo(check.use.stepId)?.localizedName()
                            ?: stringResource(R.string.workflow_step_type_unknown)
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
    requestedRecordId: String?,
    onRequestedRecordConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenWorkflow: (Workflow, StepPath?) -> Unit,
    onRetry: (Workflow) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<RunStatus?>(null) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedRecordMissing by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val visibleRecords = filterRunRecords(records, query, status)
    val selectedRecord = selectedRecordId?.let { recordId ->
        records.firstOrNull { it.id == recordId }
    }

    LaunchedEffect(requestedRecordId, records) {
        requestedRecordId?.let { recordId ->
            requestedRecordMissing = false
            val record = records.firstOrNull { it.id == recordId }
            if (record == null) {
                selectedRecordId = null
                delay(RUN_HISTORY_DEEP_LINK_SETTLE_MILLIS)
                requestedRecordMissing = true
            } else {
                selectedRecordId = record.id
            }
            onRequestedRecordConsumed()
        }
    }

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
            if (requestedRecordMissing) {
                Text(
                    stringResource(R.string.run_history_record_missing),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            listOf<RunStatus?>(null, RunStatus.Completed, RunStatus.CompletedWithWarnings)
                .chunked(3)
                .plus(
                    listOf(
                        listOf(RunStatus.Failed, RunStatus.Cancelled, RunStatus.Rejected),
                        listOf(RunStatus.Unknown),
                    ),
                )
                .forEach { options ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = status == option,
                                        role = Role.RadioButton,
                                        onClick = { status = option },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = status == option, onClick = null)
                                Text(
                                    option?.localizedName()
                                        ?: stringResource(R.string.run_history_status_all),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            Text(
                pluralStringResource(
                    R.plurals.run_history_count,
                    records.size,
                    records.size,
                    visibleRecords.size,
                ),
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
                        Box(Modifier.clickable { selectedRecordId = record.id }) {
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
            onDismiss = { selectedRecordId = null },
            onOpenWorkflow = { workflow, stepPath ->
                selectedRecordId = null
                onOpenWorkflow(workflow, stepPath)
            },
            onRetry = { workflow ->
                selectedRecordId = null
                onRetry(workflow)
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
                        selectedRecordId = null
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
    onRetry: (Workflow) -> Unit = {},
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
                            record.failedStepLocation?.localizedName()
                                ?: stringResource(R.string.workflow_step_type_unknown),
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
                            pluralStringResource(
                                R.plurals.execution_diagnostic_row,
                                diagnostic.attemptCount,
                                diagnostic.location?.localizedName()
                                    ?: stringResource(R.string.workflow_step_type_unknown),
                                diagnostic.outcome.localizedName(),
                                diagnostic.durationMillis,
                                diagnostic.attemptCount,
                            ),
                            fontSize = 12.sp,
                        )
                        diagnostic.localizedFailureMessage(context)?.let { warning ->
                            diagnostic.failedStepId?.let { failedStepId ->
                                Text(
                                    stringResource(
                                        R.string.execution_diagnostic_failed_step,
                                        diagnostic.failedStepLocation?.localizedName()
                                            ?: stringResource(R.string.workflow_step_type_unknown),
                                    ),
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                stringResource(R.string.run_failure_details, warning),
                                color = if (diagnostic.outcome == RunStepOutcome.ContinuedAfterFailure) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    Color(0xFFD04F3D)
                                },
                                fontSize = 12.sp,
                            )
                        }
                        diagnostic.imageClick?.let { imageClick ->
                            ImageClickDiagnosticDetails(imageClick)
                        }
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
            destination?.let { target ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.status == RunStatus.Failed ||
                        record.status == RunStatus.CompletedWithWarnings
                    ) {
                        TextButton(onClick = { onRetry(target.workflow) }) {
                            Text(stringResource(R.string.run_history_retry_current_version))
                        }
                    }
                    TextButton(onClick = { onOpenWorkflow(target.workflow, target.stepPath) }) {
                        Text(
                            stringResource(
                                if (target.stepPath == null) R.string.run_history_open_workflow
                                else R.string.run_history_edit_failed_step,
                            ),
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ImageClickDiagnosticDetails(diagnostic: com.aiindexfinger.data.RunImageClickDiagnostic) {
    val candidateCount = if (diagnostic.candidatesTruncated) {
        stringResource(R.string.image_click_diagnostic_candidates_truncated, diagnostic.candidateCount)
    } else {
        stringResource(R.string.image_click_diagnostic_candidates, diagnostic.candidateCount)
    }
    val mode = stringResource(
        when (diagnostic.selectionMode) {
            com.aiindexfinger.data.RunImageClickSelectionMode.BestMatch -> R.string.image_click_selection_best_match
            com.aiindexfinger.data.RunImageClickSelectionMode.AllMatches -> R.string.image_click_selection_all_matches
            com.aiindexfinger.data.RunImageClickSelectionMode.Unknown -> R.string.image_click_selection_unknown
        },
    )
    val bestScore = diagnostic.bestScorePermille
    val bestScale = diagnostic.bestScalePermille
    Text(
        if (bestScore != null && bestScale != null) {
            stringResource(
                R.string.image_click_diagnostic_summary,
                mode,
                candidateCount,
                bestScore / 10f,
                bestScale / 10f,
                diagnostic.completedClickCount,
                diagnostic.plannedClickCount,
            )
        } else {
            stringResource(
                R.string.image_click_diagnostic_summary_no_best,
                mode,
                candidateCount,
                diagnostic.completedClickCount,
                diagnostic.plannedClickCount,
            )
        },
        fontSize = 12.sp,
    )
    diagnostic.failedClickIndex?.let { failedClickIndex ->
        Text(
            stringResource(R.string.image_click_diagnostic_failed_click, failedClickIndex),
            fontSize = 12.sp,
        )
    }
    if (diagnostic.retrySuppressed) {
        Text(
            stringResource(R.string.image_click_diagnostic_retry_suppressed),
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RunStepOutcome.localizedName(): String = stringResource(
    when (this) {
        RunStepOutcome.Completed -> R.string.execution_outcome_completed
        RunStepOutcome.ContinuedAfterFailure -> R.string.execution_outcome_continued
        RunStepOutcome.Failed -> R.string.execution_outcome_failed
        RunStepOutcome.Cancelled -> R.string.execution_outcome_cancelled
        RunStepOutcome.Unknown -> R.string.execution_outcome_unknown
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
                            record.failedStepLocation?.localizedName(context)
                                ?: context.getString(R.string.workflow_step_type_unknown),
                            failureMessage,
                        )
                    } ?: failureMessage,
                    color = if (record.status == RunStatus.CompletedWithWarnings) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        Color(0xFFD04F3D)
                    },
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            record.status.localizedName(),
            color = when (record.status) {
                RunStatus.Completed -> Color(0xFF16815F)
                RunStatus.CompletedWithWarnings -> MaterialTheme.colorScheme.tertiary
                else -> Color(0xFFD04F3D)
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun RunRecord.localizedFailureMessage(context: Context): String? {
    return localizedStoredFailure(context, failureCode, failureArguments, failureMessage)
}

private fun RunStepDiagnostic.localizedFailureMessage(context: Context): String? =
    localizedStoredFailure(context, failureCode, failureArguments)

private fun localizedStoredFailure(
    context: Context,
    failureCode: String?,
    failureArguments: Map<String, String>,
    legacyMessage: String? = null,
): String? {
    val storedCode = failureCode ?: return legacyMessage
    if (storedCode == RUN_FAILURE_CONTROL_NOTIFICATION_UNAVAILABLE) {
        return context.getString(R.string.run_cancelled_controls_unavailable)
    }
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
        failedStepLocation?.localizedName(context)
            ?: context.getString(R.string.workflow_step_type_unknown),
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

internal fun RunStepLocation.localizedName(context: Context): String = segments.flatMap { segment ->
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
    ExecutionErrorCode.ImageClickPartialExecution -> context.getString(R.string.execution_error_image_partial_execution)
    ExecutionErrorCode.ImageClickTargetWindowChanged -> context.getString(R.string.execution_error_image_target_window_changed)
    ExecutionErrorCode.ImageClickPartialTimedOut -> context.getString(R.string.execution_error_image_partial_timed_out)
    ExecutionErrorCode.ScreenCaptureFailed -> context.getString(R.string.execution_error_capture_failed)
    ExecutionErrorCode.ImageGestureFailed -> context.getString(R.string.execution_error_image_gesture_failed)
    ExecutionErrorCode.SystemActionFailed -> arguments["action"]?.let { action ->
        context.getString(
            R.string.execution_error_system_action_failed_for,
            systemActionName(context, action),
        )
    } ?: context.getString(R.string.execution_error_system_action_failed)
    ExecutionErrorCode.TargetNotScrollable -> arguments["direction"]?.let { direction ->
        context.getString(
            R.string.execution_error_scroll_failed_for,
            context.getString(scrollDirectionLabelRes(ScrollDirection.valueOf(direction))),
        )
    } ?: context.getString(R.string.execution_error_target_not_scrollable)
    ExecutionErrorCode.ScrollUntilNoProgress ->
        context.getString(R.string.execution_error_scroll_until_no_progress)
    ExecutionErrorCode.ScrollUntilMaxReached -> context.getString(
        R.string.execution_error_scroll_until_max_reached,
        arguments.getValue("maxScrolls").toInt(),
    )
    ExecutionErrorCode.AppLaunchFailed -> arguments["packageName"]?.let { packageName ->
        arguments["intentAction"]?.let { intentAction ->
            context.getString(
                R.string.execution_error_app_launch_action_failed,
                packageName,
                intentAction,
            )
        } ?: context.getString(R.string.execution_error_app_launch_package_failed, packageName)
    } ?: context.getString(R.string.execution_error_app_launch_failed)
    ExecutionErrorCode.TargetNotLongClickable -> context.getString(R.string.execution_error_target_not_long_clickable)
    ExecutionErrorCode.TargetNotFound -> context.getString(R.string.execution_error_target_not_found)
    ExecutionErrorCode.UndefinedVariable -> context.getString(
        R.string.execution_error_undefined_variable,
        arguments.getValue("variableName"),
    )
    ExecutionErrorCode.TextInputFailed -> arguments["inputMethod"]?.let { inputMethod ->
        context.getString(
            R.string.execution_error_text_input_method_failed,
            context.getString(
                if (TextInputMethod.valueOf(inputMethod) == TextInputMethod.Paste) {
                    R.string.input_method_paste
                } else {
                    R.string.input_method_set_text
                },
            ),
        )
    } ?: context.getString(R.string.execution_error_text_input_failed)
    ExecutionErrorCode.MissingNodeAttribute -> context.getString(
        R.string.execution_error_missing_node_attribute,
        nodeAttributeName(context, arguments.getValue("attribute")),
    )
    ExecutionErrorCode.ReadValueProcessingFailed ->
        context.getString(R.string.execution_error_read_value_processing_failed)
    ExecutionErrorCode.SwipeFailed -> context.getString(R.string.execution_error_swipe_failed)
    ExecutionErrorCode.TapFailed -> context.getString(R.string.execution_error_tap_failed)
    ExecutionErrorCode.CoordinatesOutOfBounds -> context.getString(
        R.string.execution_error_coordinates_out_of_bounds,
        arguments.getValue("displayWidth").toInt(),
        arguments.getValue("displayHeight").toInt(),
    )
    ExecutionErrorCode.ClipboardUnavailable ->
        context.getString(R.string.execution_error_clipboard_unavailable)
}

private fun systemActionName(context: Context, action: String): String = context.getString(
    when (SystemAction.valueOf(action)) {
        SystemAction.Back -> R.string.system_action_back
        SystemAction.Home -> R.string.system_action_home
        SystemAction.Recents -> R.string.system_action_recents
        SystemAction.Notifications -> R.string.system_action_notifications
        SystemAction.QuickSettings -> R.string.system_action_quick_settings
        SystemAction.PowerDialog -> R.string.system_action_power_dialog
        SystemAction.LockScreen -> R.string.system_action_lock_screen
    },
)

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
    ValidationIssueCode.DuplicateLabel -> context.getString(
        R.string.validation_duplicate_label,
        arguments.getValue("label"),
    )
    ValidationIssueCode.MissingJumpLabel -> context.getString(
        R.string.validation_missing_jump_label,
        arguments.getValue("label"),
    )
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
                    pluralStringResource(
                        R.plurals.workflow_example_result_count,
                        visibleExamples.size,
                        visibleExamples.size,
                    ),
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

private fun selectedTutorialUrl(locale: Locale): String =
    if (locale.isSimplifiedChinese()) {
        WORKFLOW_TUTORIAL_ZH_URL
    } else {
        WORKFLOW_TUTORIAL_EN_URL
    }

private fun Locale.isSimplifiedChinese(): Boolean =
    language.equals("zh", ignoreCase = true) &&
        !script.equals("Hant", ignoreCase = true) &&
        country.uppercase(Locale.ROOT) !in setOf("TW", "HK", "MO")

private const val WORKFLOW_TUTORIAL_ZH_URL =
    "https://github.com/w835041951-dotcom/Ai-Index-Finger/blob/main/docs/WORKFLOW_TUTORIAL_ZH.md"

private const val WORKFLOW_TUTORIAL_EN_URL =
    "https://github.com/w835041951-dotcom/Ai-Index-Finger/blob/main/docs/WORKFLOW_TUTORIAL_EN.md"

private const val VISIBLE_RUN_RECORDS = 10

@Composable
private fun SettingsScreen(
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenTutorial: () -> Unit,
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
            OutlinedButton(
                onClick = onOpenTutorial,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tutorial_action)) }
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
                tertiary = warningTextColor(darkTheme = true),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF116B56),
                secondary = Color(0xFF3C6257),
                background = Color(0xFFF4F6F1),
                surface = Color.White,
                onSurface = Color(0xFF18201D),
                onSurfaceVariant = Color(0xFF5B6863),
                tertiary = warningTextColor(darkTheme = false),
            )
        },
        content = content,
    )
}

internal fun warningTextColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFFFFCC66) else Color(0xFF7A4E00)

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