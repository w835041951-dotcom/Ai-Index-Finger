package com.aiindexfinger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.automation.LaunchableAppCatalog
import com.aiindexfinger.automation.ObservedNode
import com.aiindexfinger.automation.SelectorRecommendations
import com.aiindexfinger.data.RunHistoryStore
import com.aiindexfinger.data.RunRecord
import com.aiindexfinger.data.RunStatus
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.data.WorkflowTransfer
import com.aiindexfinger.data.normalizeImportedWorkflows
import com.aiindexfinger.executor.RunResult
import com.aiindexfinger.model.Condition
import com.aiindexfinger.model.ComparisonOperator
import com.aiindexfinger.model.FailurePolicy
import com.aiindexfinger.model.NodeAttribute
import com.aiindexfinger.model.NodeSelector
import com.aiindexfinger.model.ScrollDirection
import com.aiindexfinger.model.Step
import com.aiindexfinger.model.SystemAction
import com.aiindexfinger.model.TextMatchMode
import com.aiindexfinger.model.TextInputMethod
import com.aiindexfinger.model.Value
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowValidator
import com.aiindexfinger.model.duplicateWithNewIds
import com.aiindexfinger.model.matchesSearch
import com.aiindexfinger.model.withExecutionSettings
import com.aiindexfinger.scheduler.ScheduleNotificationWorker
import com.aiindexfinger.scheduler.WorkflowSchedule
import com.aiindexfinger.scheduler.WorkflowScheduler
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val workflowStore by lazy { WorkflowStore(this) }
    private val runHistoryStore by lazy { RunHistoryStore(this) }
    private val workflowScheduler by lazy { WorkflowScheduler(this) }
    private val scheduledWorkflowId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialWorkflows = workflowStore.load()
        val initialRunRecords = runHistoryStore.load()
        val initialSchedules = workflowScheduler.load(initialWorkflows.map { it.id }.toSet())
        scheduledWorkflowId.value = intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID)
        setContent {
            AiIndexFingerTheme {
            val scheduledWorkflow by scheduledWorkflowId.collectAsStateWithLifecycle()
                WorkflowApp(
                    initialWorkflows = initialWorkflows,
                    initialRunRecords = initialRunRecords,
                    initialSchedules = initialSchedules,
                    scheduledWorkflowId = scheduledWorkflow,
                    onSave = workflowStore::save,
                    onClearRunHistory = runHistoryStore::clear,
                    onSchedule = workflowScheduler::schedule,
                    onCancelSchedule = workflowScheduler::cancel,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduledWorkflowId.value = intent.getStringExtra(ScheduleNotificationWorker.EXTRA_WORKFLOW_ID)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
private fun WorkflowApp(
    initialWorkflows: List<Workflow>,
    initialRunRecords: List<RunRecord>,
    initialSchedules: List<WorkflowSchedule>,
    scheduledWorkflowId: String?,
    onSave: (List<Workflow>) -> Unit,
    onClearRunHistory: () -> Unit,
    onSchedule: (String, String, Long) -> List<WorkflowSchedule>,
    onCancelSchedule: (String) -> List<WorkflowSchedule>,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var workflows by remember { mutableStateOf(initialWorkflows) }
    var runRecords by remember { mutableStateOf(initialRunRecords) }
    var schedules by remember { mutableStateOf(initialSchedules) }
    var editingWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var runMessage by remember { mutableStateOf<String?>(null) }
    val runningWorkflowId by AutomationAccessibilityService.runningWorkflowId.collectAsStateWithLifecycle()
    val latestRun by AutomationAccessibilityService.latestRun.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Workflow?>(null) }
    var pendingBundleExport by remember { mutableStateOf<List<Workflow>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val workflowTransfer = remember { WorkflowTransfer(context.contentResolver) }
    LaunchedEffect(scheduledWorkflowId) {
        scheduledWorkflowId?.let { id ->
            val workflowName = workflows.firstOrNull { it.id == id }?.name ?: "Scheduled workflow"
            runMessage = "$workflowName is ready. Tap Run to start it."
        }
    }
    LaunchedEffect(latestRun?.record?.id) {
        latestRun?.let { outcome ->
            runRecords = (listOf(outcome.record) + runRecords)
                .distinctBy { it.id }
                .take(100)
            runMessage = outcome.result.message()
        }
    }
    var pendingSchedule by remember { mutableStateOf<Pair<Workflow, Long>?>(null) }
    var pendingRun by remember { mutableStateOf<Workflow?>(null) }
    val runNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val workflow = pendingRun
        pendingRun = null
        val service = AutomationAccessibilityService.instance
        runMessage = when {
            !granted -> "Notification permission is required while a workflow is running"
            workflow == null -> runMessage
            service == null -> "Enable the automation service before running a workflow"
            service.startWorkflow(workflow) -> "Running ${workflow.name}"
            else -> "Another workflow is already running"
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingSchedule
        pendingSchedule = null
        if (granted && request != null) {
            schedules = onSchedule(request.first.id, request.first.name, request.second)
            runMessage = "Scheduled ${request.first.name}"
        } else if (!granted) {
            runMessage = "Notification permission is required for schedule reminders"
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
                    .onSuccess { runMessage = "Exported ${workflow.name}" }
                    .onFailure { runMessage = "Export failed: ${it.message}" }
            }
        }
    }
    val bundleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val workflowSnapshot = pendingBundleExport
        pendingBundleExport = null
        if (uri != null && workflowSnapshot != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.writeBundle(uri, workflowSnapshot) }
                }
                outcome
                    .onSuccess { runMessage = "Backed up ${workflowSnapshot.size} workflows" }
                    .onFailure { runMessage = "Backup failed: ${it.message}" }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { workflowTransfer.readMany(uri) }
                }
                outcome.onSuccess { importedWorkflows ->
                    val normalized = normalizeImportedWorkflows(workflows, importedWorkflows, ::newId)
                    val updated = workflows + normalized
                    onSave(updated)
                    workflows = updated
                    runMessage = "Imported ${normalized.size} ${if (normalized.size == 1) "workflow" else "workflows"}"
                }
                        .onFailure { runMessage = "Import failed: ${it.message}" }
                    }
        }
    }

    if (editingWorkflow == null) {
        WorkflowHome(
            workflows = workflows,
            runRecords = runRecords,
            schedules = schedules,
            onCreate = {
                editingWorkflow = Workflow(
                    id = UUID.randomUUID().toString(),
                    name = "Untitled workflow",
                    steps = emptyList(),
                )
            },
            onEdit = { editingWorkflow = it },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            onExportAll = {
                pendingBundleExport = workflows.toList()
                bundleExportLauncher.launch("ai-index-finger-backup.json")
            },
            onExport = { workflow ->
                pendingExport = workflow
                exportLauncher.launch(workflow.exportFileName())
            },
            onDuplicate = { workflow ->
                val duplicate = workflow.copy(
                    id = newId(),
                    name = "${workflow.name} copy",
                )
                val updated = workflows + duplicate
                onSave(updated)
                workflows = updated
            },
            onDelete = { workflow ->
                schedules = onCancelSchedule(workflow.id)
                val updated = workflows.filterNot { it.id == workflow.id }
                onSave(updated)
                workflows = updated
            },
            onSchedule = { workflow, delayMinutes ->
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSchedule = workflow to delayMinutes
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    schedules = onSchedule(workflow.id, workflow.name, delayMinutes)
                    runMessage = "Scheduled ${workflow.name}"
                }
            },
            onCancelSchedule = { workflow ->
                schedules = onCancelSchedule(workflow.id)
                runMessage = "Cancelled schedule for ${workflow.name}"
            },
            onClearRunHistory = {
                onClearRunHistory()
                runRecords = emptyList()
            },
            runningWorkflowId = runningWorkflowId,
            runMessage = runMessage,
            onRun = { workflow ->
                val service = AutomationAccessibilityService.instance
                val issue = WorkflowValidator.validate(workflow).firstOrNull()
                if (issue != null) {
                    runMessage = "Cannot run: ${issue.message}"
                } else if (service == null) {
                    runMessage = "Enable the automation service before running a workflow"
                    onOpenAccessibilitySettings()
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pendingRun = workflow
                    runNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val started = service.startWorkflow(workflow)
                    runMessage = if (started) {
                        "Running ${workflow.name}"
                    } else {
                        "Another workflow is already running"
                    }
                }
            },
            onStop = { AutomationAccessibilityService.instance?.stopWorkflow() },
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        )
    } else {
        WorkflowEditor(
            workflow = requireNotNull(editingWorkflow),
            onBack = { editingWorkflow = null },
            onSave = { workflow ->
                val updated = workflows.filterNot { it.id == workflow.id } + workflow
                onSave(updated)
                workflows = updated
                editingWorkflow = null
            },
        )
    }
}

@Composable
private fun WorkflowHome(
    workflows: List<Workflow>,
    runRecords: List<RunRecord>,
    schedules: List<WorkflowSchedule>,
    onCreate: () -> Unit,
    onEdit: (Workflow) -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
    onExport: (Workflow) -> Unit,
    onDuplicate: (Workflow) -> Unit,
    onDelete: (Workflow) -> Unit,
    onSchedule: (Workflow, Long) -> Unit,
    onCancelSchedule: (Workflow) -> Unit,
    onClearRunHistory: () -> Unit,
    runningWorkflowId: String?,
    runMessage: String?,
    onRun: (Workflow) -> Unit,
    onStop: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
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
    val visibleWorkflows = remember(workflows, workflowQuery) {
        workflows.filter { it.matchesSearch(workflowQuery) }
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
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("New workflow") }
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
            Text("Workflows", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            PermissionStatus(serviceConnected, onOpenAccessibilitySettings)
            if (runningWorkflowId != null) {
                val currentWorkflow = workflows.firstOrNull { it.id == runningWorkflowId }
                val currentStep = currentStepId?.let { currentWorkflow?.steps?.findById(it) }
                Spacer(Modifier.height(14.dp))
                RunningWorkflowStatus(
                    workflowName = currentWorkflow?.name ?: "Workflow",
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
            ) { Text("Inspect recent nodes (${observedNodes.size})") }
            runMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MY WORKFLOWS", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = onExportAll,
                    enabled = workflows.isNotEmpty() && runningWorkflowId == null,
                ) { Text("Backup") }
                TextButton(onClick = onImport, enabled = runningWorkflowId == null) { Text("Import") }
            }
            if (workflows.isNotEmpty()) {
                OutlinedTextField(
                    value = workflowQuery,
                    onValueChange = { workflowQuery = it },
                    label = { Text("Search workflows") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${visibleWorkflows.size} of ${workflows.size} workflows",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            if (workflows.isEmpty()) {
                Text(
                    "No workflows yet",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visibleWorkflows.isEmpty()) {
                Text(
                    "No workflows match this search",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visibleWorkflows.forEach { workflow ->
                val schedule = schedules.firstOrNull { it.workflowId == workflow.id }
                WorkflowRow(
                    workflow = workflow,
                    isRunning = workflow.id == runningWorkflowId,
                    schedule = schedule,
                    onEdit = { onEdit(workflow) },
                    onExport = { onExport(workflow) },
                    onDuplicate = { onDuplicate(workflow) },
                    onDelete = { workflowToDelete = workflow },
                    onSchedule = { workflowToSchedule = workflow },
                    onCancelSchedule = { onCancelSchedule(workflow) },
                    onRun = { onRun(workflow) },
                    onStop = onStop,
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RUN HISTORY", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (runRecords.isNotEmpty()) {
                    TextButton(onClick = { confirmClearHistory = true }) { Text("Clear") }
                }
            }
            HorizontalDivider()
            if (runRecords.isEmpty()) {
                Text(
                    "No runs recorded",
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
            title = { Text("Delete workflow?") },
            text = { Text("${workflow.name} and all of its steps will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(workflow)
                        workflowToDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { workflowToDelete = null }) { Text("Cancel") }
            },
        )
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear run history?") },
            text = { Text("All local execution records will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRunHistory()
                        confirmClearHistory = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
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
            onSchedule = { delayMinutes ->
                onSchedule(workflow, delayMinutes)
                workflowToSchedule = null
            },
        )
    }
}

@Composable
private fun ScheduleDialog(
    workflowName: String,
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit,
) {
    var delayMinutes by remember { mutableStateOf("15") }
    val delay = delayMinutes.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule $workflowName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A notification will remind you to open the app and run this workflow.")
                NodeField(delayMinutes, { delayMinutes = it }, "Delay in minutes", true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = delay != null && delay in 1..525_600,
                onClick = { onSchedule(requireNotNull(delay)) },
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NodeInspectorDialog(nodes: List<ObservedNode>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visibleNodes = nodes.filter { it.matchesQuery(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent accessibility nodes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filter package, ID, text or class") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${visibleNodes.size} of ${nodes.size} nodes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (nodes.isEmpty()) {
                    Text(
                        "Open a target app and interact with its screen, then return here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                visibleNodes.forEachIndexed { index, node ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${index + 1}. ${node.displayName()}", fontWeight = FontWeight.SemiBold)
                        NodeProperty("Package", node.packageName)
                        NodeProperty("Resource ID", node.viewId)
                        NodeProperty("Text", node.text)
                        NodeProperty("Description", node.contentDescription)
                        NodeProperty("Class", node.className)
                        NodeProperty("Bounds", node.bounds)
                        NodeProperty(
                            "State",
                            "clickable=${node.clickable}, longClickable=${node.longClickable}, " +
                                "scrollable=${node.scrollable}, enabled=${node.enabled}",
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
private fun WorkflowEditor(workflow: Workflow, onBack: () -> Unit, onSave: (Workflow) -> Unit) {
    var name by remember(workflow.id) { mutableStateOf(workflow.name) }
    var defaultTimeoutText by remember(workflow.id) {
        mutableStateOf(workflow.defaultStepTimeoutMillis.toString())
    }
    val steps = remember(workflow.id) { mutableStateListOf<Step>().apply { addAll(workflow.steps) } }
    var showClickDialog by remember { mutableStateOf(false) }
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
    var policyStepIndex by remember { mutableStateOf<Int?>(null) }
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }
    var stepToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDiscardChanges by remember { mutableStateOf(false) }
    val observedNodes by AutomationAccessibilityService.observedNodes.collectAsStateWithLifecycle()
    val defaultTimeoutMillis = defaultTimeoutText.toLongOrNull()
    val validationIssues = if (name.isNotBlank() && defaultTimeoutMillis != null && defaultTimeoutMillis > 0) {
        WorkflowValidator.validate(
            workflow.copy(
                name = name.trim(),
                steps = steps.toList(),
                defaultStepTimeoutMillis = defaultTimeoutMillis,
            ),
        )
    } else {
        emptyList()
    }
    val hasUnsavedChanges = name != workflow.name ||
        defaultTimeoutMillis != workflow.defaultStepTimeoutMillis ||
        steps.toList() != workflow.steps
    val requestBack = {
        if (hasUnsavedChanges) confirmDiscardChanges = true else onBack()
    }

    BackHandler(onBack = requestBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    enabled = name.isNotBlank() && defaultTimeoutMillis != null && defaultTimeoutMillis > 0,
                    onClick = {
                        onSave(
                            workflow.copy(
                                schemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
                                name = name.trim(),
                                steps = steps.toList(),
                                defaultStepTimeoutMillis = requireNotNull(defaultTimeoutMillis),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Save workflow") }
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
                TextButton(onClick = requestBack) { Text("Back") }
                Text("Workflow editor", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Workflow name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = defaultTimeoutText,
                onValueChange = { defaultTimeoutText = it },
                label = { Text("Default step timeout (ms)") },
                isError = defaultTimeoutMillis == null || defaultTimeoutMillis <= 0,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (validationIssues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${validationIssues.size} validation ${if (validationIssues.size == 1) "issue" else "issues"}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                validationIssues.take(3).forEach { issue ->
                    Text(
                        issue.message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
                if (validationIssues.size > 3) {
                    Text(
                        "+${validationIssues.size - 3} more",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("STEPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (steps.isEmpty()) {
                Text("Add the first action below", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            steps.forEachIndexed { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.lastIndex,
                    onMoveUp = { steps.move(index, index - 1) },
                    onMoveDown = { steps.move(index, index + 1) },
                    canEdit = step.isActionEditable(),
                    onEdit = { editingStepIndex = index },
                    onEditPolicy = { policyStepIndex = index },
                    onDuplicate = {
                        steps.add(index + 1, step.duplicateWithNewIds(::newId))
                    },
                    onDelete = { stepToDeleteIndex = index },
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(20.dp))
            Text("ADD ACTION", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showLaunchDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Launch app")
                }
                Button(onClick = { showClickDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Click")
                }
            }
            OutlinedButton(
                onClick = { showLongClickDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Long click") }
            OutlinedButton(
                onClick = { showTapDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tap coordinate") }
            OutlinedButton(
                onClick = { showScrollDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scroll element") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showInputDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Input text")
                }
                OutlinedButton(onClick = { showSwipeDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Swipe")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showWaitDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Wait")
                }
                OutlinedButton(
                    onClick = { steps += Step.GlobalAction(newId(), SystemAction.Back) },
                    modifier = Modifier.weight(1f),
                ) { Text("Back") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { steps += Step.GlobalAction(newId(), SystemAction.Home) },
                    modifier = Modifier.weight(1f),
                ) { Text("Home") }
                OutlinedButton(
                    onClick = { steps += Step.GlobalAction(newId(), SystemAction.Recents) },
                    modifier = Modifier.weight(1f),
                ) { Text("Recents") }
            }
            Spacer(Modifier.height(16.dp))
            Text("ADD LOGIC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showWaitNodeDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Wait element")
                }
                OutlinedButton(onClick = { showVariableDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Set variable")
                }
            }
            OutlinedButton(
                enabled = observedNodes.isNotEmpty(),
                onClick = { showReadNodeTextDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Read element attribute") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = steps.isNotEmpty(),
                    onClick = { showRepeatDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Repeat step") }
                OutlinedButton(
                    enabled = steps.isNotEmpty(),
                    onClick = { showConditionDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text("If variable") }
            }
            OutlinedButton(
                enabled = steps.isNotEmpty() && observedNodes.isNotEmpty(),
                onClick = { showNodeConditionDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("If element exists") }
        }
    }

    if (showClickDialog) {
        ClickStepDialog(
            observedNodes = observedNodes,
            onDismiss = { showClickDialog = false },
            onAdd = { selector ->
                steps += Step.Click(UUID.randomUUID().toString(), selector)
                showClickDialog = false
            },
        )
    }
    if (showLongClickDialog) {
        ClickStepDialog(
            observedNodes = observedNodes,
            title = "Long click an element",
            onDismiss = { showLongClickDialog = false },
            onAdd = { selector ->
                steps += Step.LongClick(newId(), selector)
                showLongClickDialog = false
            },
        )
    }
    if (showLaunchDialog) {
        LaunchAppDialog(
            onDismiss = { showLaunchDialog = false },
            onAdd = { packageName ->
                steps += Step.LaunchApp(UUID.randomUUID().toString(), packageName)
                showLaunchDialog = false
            },
        )
    }
    if (showInputDialog) {
        InputTextDialog(
            observedNodes = observedNodes,
            onDismiss = { showInputDialog = false },
            onAdd = { selector, text, variableName, inputMethod ->
                steps += Step.InputText(
                    newId(),
                    selector,
                    text,
                    variableName,
                    inputMethod,
                )
                showInputDialog = false
            },
        )
    }
    if (showSwipeDialog) {
        SwipeDialog(
            onDismiss = { showSwipeDialog = false },
            onAdd = { startX, startY, endX, endY, duration ->
                steps += Step.Swipe(newId(), startX, startY, endX, endY, duration)
                showSwipeDialog = false
            },
        )
    }
    if (showTapDialog) {
        TapDialog(
            onDismiss = { showTapDialog = false },
            onAdd = { x, y ->
                steps += Step.Tap(newId(), x, y)
                showTapDialog = false
            },
        )
    }
    if (showScrollDialog) {
        ScrollStepDialog(
            observedNodes = observedNodes,
            onDismiss = { showScrollDialog = false },
            onAdd = { selector, direction ->
                steps += Step.Scroll(newId(), selector, direction)
                showScrollDialog = false
            },
        )
    }
    if (showWaitDialog) {
        NumberDialog(
            title = "Wait",
            label = "Duration in milliseconds",
            initialValue = "1000",
            onDismiss = { showWaitDialog = false },
            onAdd = { duration ->
                steps += Step.Delay(newId(), duration)
                showWaitDialog = false
            },
        )
    }
    if (showWaitNodeDialog) {
        WaitNodeDialog(
            observedNodes = observedNodes,
            onDismiss = { showWaitNodeDialog = false },
            onAdd = { selector, timeout, mustExist ->
                steps += Step.WaitForNode(
                    newId(),
                    selector,
                    mustExist = mustExist,
                    timeoutMillis = timeout,
                )
                showWaitNodeDialog = false
            },
        )
    }
    if (showVariableDialog) {
        SetVariableDialog(
            onDismiss = { showVariableDialog = false },
            onAdd = { variableName, value ->
                steps += Step.SetVariable(newId(), variableName, value)
                showVariableDialog = false
            },
        )
    }
    if (showReadNodeTextDialog) {
        ReadNodeTextDialog(
            observedNodes = observedNodes,
            onDismiss = { showReadNodeTextDialog = false },
            onSave = { selector, variableName, attribute ->
                steps += Step.ReadNodeText(newId(), selector, variableName, attribute)
                showReadNodeTextDialog = false
            },
        )
    }
    editingStepIndex?.let { index ->
        when (val step = steps.getOrNull(index)) {
            is Step.LaunchApp -> LaunchAppDialog(
                initialPackageName = step.packageName,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { packageName ->
                    steps[index] = step.copy(packageName = packageName)
                    editingStepIndex = null
                },
            )
            is Step.LongClick -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                title = "Long click an element",
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { selector ->
                    steps[index] = step.copy(selector = selector)
                    editingStepIndex = null
                },
            )
            is Step.Click -> ClickStepDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { selector ->
                    steps[index] = step.copy(selector = selector)
                    editingStepIndex = null
                },
            )
            is Step.InputText -> InputTextDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { selector, text, variableName, inputMethod ->
                    steps[index] = step.copy(
                        selector = selector,
                        text = text,
                        variableName = variableName,
                        inputMethod = inputMethod,
                    )
                    editingStepIndex = null
                },
            )
            is Step.ReadNodeText -> ReadNodeTextDialog(
                observedNodes = observedNodes,
                initialSelector = step.selector,
                initialVariableName = step.variableName,
                initialAttribute = step.attribute,
                onDismiss = { editingStepIndex = null },
                onSave = { selector, variableName, attribute ->
                    steps[index] = step.copy(
                        selector = selector,
                        variableName = variableName,
                        attribute = attribute,
                    )
                    editingStepIndex = null
                },
            )
            is Step.Delay -> NumberDialog(
                title = "Edit wait",
                label = "Duration in milliseconds",
                initialValue = step.durationMillis.toString(),
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { duration ->
                    steps[index] = step.copy(durationMillis = duration)
                    editingStepIndex = null
                },
            )
            is Step.GlobalAction -> GlobalActionSettingsDialog(
                current = step.action,
                onDismiss = { editingStepIndex = null },
                onSelect = { action ->
                    steps[index] = step.copy(action = action)
                    editingStepIndex = null
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
                                onDismiss = { editingStepIndex = null },
                                onSave = { variableName, expectedValue, operator ->
                                    steps[index] = step.copy(
                                        condition = Condition.Equals(
                                            Value.Variable(variableName),
                                            Value.Literal(expectedValue),
                                            operator,
                                        ),
                                    )
                                    editingStepIndex = null
                                },
                            )
                        }
                    }
                    is Condition.NodeExists -> NodeConditionDialog(
                        observedNodes = observedNodes,
                        initialSelector = condition.selector,
                        onDismiss = { editingStepIndex = null },
                        onSave = { _, selector ->
                            steps[index] = step.copy(condition = Condition.NodeExists(selector))
                            editingStepIndex = null
                        },
                    )
                }
            }
            is Step.Swipe -> SwipeDialog(
                initialStep = step,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { startX, startY, endX, endY, duration ->
                    steps[index] = step.copy(
                        startX = startX,
                        startY = startY,
                        endX = endX,
                        endY = endY,
                        durationMillis = duration,
                    )
                    editingStepIndex = null
                },
            )
            is Step.Tap -> TapDialog(
                initialStep = step,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { x, y ->
                    steps[index] = step.copy(x = x, y = y)
                    editingStepIndex = null
                },
            )
            is Step.SetVariable -> SetVariableDialog(
                initialName = step.name,
                initialValue = step.value,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { variableName, value ->
                    steps[index] = step.copy(name = variableName, value = value)
                    editingStepIndex = null
                },
            )
            is Step.Repeat -> RepeatSettingsDialog(
                initialCount = step.times,
                onDismiss = { editingStepIndex = null },
                onSave = { count ->
                    steps[index] = step.copy(times = count)
                    editingStepIndex = null
                },
            )
            is Step.Scroll -> ScrollStepDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { selector, direction ->
                    steps[index] = step.copy(selector = selector, direction = direction)
                    editingStepIndex = null
                },
            )
            is Step.WaitForNode -> WaitNodeDialog(
                observedNodes = observedNodes,
                initialStep = step,
                confirmLabel = "Save",
                onDismiss = { editingStepIndex = null },
                onAdd = { selector, timeout, mustExist ->
                    steps[index] = step.copy(
                        selector = selector,
                        mustExist = mustExist,
                        timeoutMillis = timeout,
                    )
                    editingStepIndex = null
                },
            )
            else -> Unit
        }
    }
    if (showRepeatDialog) {
        WrapStepDialog(
            title = "Repeat a step",
            valueLabel = "Repeat count",
            initialValue = "2",
            steps = steps,
            onDismiss = { showRepeatDialog = false },
            onAdd = { index, count ->
                val nestedStep = steps.removeAt(index)
                steps += Step.Repeat(newId(), count.toInt(), listOf(nestedStep))
                showRepeatDialog = false
            },
        )
    }
    if (showConditionDialog) {
        ConditionDialog(
            steps = steps,
            onDismiss = { showConditionDialog = false },
            onAdd = { index, variableName, expectedValue, operator ->
                val nestedStep = steps.removeAt(index)
                steps += Step.IfElse(
                    id = newId(),
                    condition = Condition.Equals(
                        Value.Variable(variableName),
                        Value.Literal(expectedValue),
                        operator,
                    ),
                    whenTrue = listOf(nestedStep),
                )
                showConditionDialog = false
            },
        )
    }
    if (showNodeConditionDialog) {
        NodeConditionDialog(
            observedNodes = observedNodes,
            steps = steps,
            onDismiss = { showNodeConditionDialog = false },
            onSave = { selectedIndex, selector ->
                val nestedStep = steps.removeAt(requireNotNull(selectedIndex))
                steps += Step.IfElse(
                    id = newId(),
                    condition = Condition.NodeExists(selector),
                    whenTrue = listOf(nestedStep),
                )
                showNodeConditionDialog = false
            },
        )
    }
    policyStepIndex?.let { index ->
        FailurePolicyDialog(
            currentStep = steps[index],
            defaultTimeoutMillis = defaultTimeoutMillis ?: workflow.defaultStepTimeoutMillis,
            onDismiss = { policyStepIndex = null },
            onSelect = { policy, timeoutMillis ->
                steps[index] = steps[index].withExecutionSettings(timeoutMillis, policy)
                policyStepIndex = null
            },
        )
    }
    stepToDeleteIndex?.let { index ->
        val step = steps.getOrNull(index)
        if (step == null) {
            stepToDeleteIndex = null
        } else {
            AlertDialog(
                onDismissRequest = { stepToDeleteIndex = null },
                title = { Text("Delete step?") },
                text = { Text("${index + 1}. ${step.title()} will be removed from this workflow.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            steps.removeAt(index)
                            stepToDeleteIndex = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { stepToDeleteIndex = null }) { Text("Cancel") }
                },
            )
        }
    }
    if (confirmDiscardChanges) {
        AlertDialog(
            onDismissRequest = { confirmDiscardChanges = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved workflow edits will be lost.") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardChanges = false }) { Text("Keep editing") }
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
        title = { Text("Global action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemAction.entries.forEach { action ->
                    val selected = action == current
                    if (selected) {
                        Button(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.name)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(action.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Repeat settings") },
        text = { NodeField(countText, { countText = it }, "Repeat count (1-${Step.Repeat.MAX_REPEAT_COUNT})", true) },
        confirmButton = {
            TextButton(
                enabled = count != null && count in 1..Step.Repeat.MAX_REPEAT_COUNT,
                onClick = { onSave(requireNotNull(count)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Condition settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(variableName, { variableName = it }, "Variable name", true)
                ComparisonOperatorSelector(operator) { operator = it }
                NodeField(expectedValue, { expectedValue = it }, "Expected value")
            }
        },
        confirmButton = {
            TextButton(
                enabled = variableName.isNotBlank(),
                onClick = { onSave(variableName.trim(), expectedValue, operator) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Step settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(
                    timeoutText,
                    { timeoutText = it },
                    "Timeout ms (blank uses $defaultTimeoutMillis)",
                )
                Text("On failure", fontWeight = FontWeight.SemiBold)
                Button(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Stop, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop workflow")
                }
                OutlinedButton(
                    enabled = timeoutValid,
                    onClick = { onSelect(FailurePolicy.Continue, timeoutMillis) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue to next step") }
                Text("Retry", fontWeight = FontWeight.SemiBold)
                NodeField(retryAttempts, { retryAttempts = it }, "Retry attempts (1-10)", true)
                NodeField(retryDelay, { retryDelay = it }, "Delay between retries ms", true)
                OutlinedButton(
                    enabled = timeoutValid && attempts != null && attempts in 1..10 && delay != null && delay >= 0,
                    onClick = {
                        onSelect(
                            FailurePolicy.Retry(requireNotNull(attempts), requireNotNull(delay)),
                            timeoutMillis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use retry policy") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InputTextDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.InputText? = null,
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector, String, String?, TextInputMethod) -> Unit,
) {
    var selectedSelector by remember(initialStep) { mutableStateOf(initialStep?.selector) }
    var inputText by remember(initialStep) { mutableStateOf(initialStep?.text.orEmpty()) }
    var useVariable by remember(initialStep) { mutableStateOf(initialStep?.variableName != null) }
    var variableName by remember(initialStep) { mutableStateOf(initialStep?.variableName.orEmpty()) }
    var inputMethod by remember(initialStep) {
        mutableStateOf(initialStep?.inputMethod ?: TextInputMethod.SetText)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input text") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose a recently observed text field", fontWeight = FontWeight.SemiBold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "Open the target app and focus its input screen, then return here.",
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
                        "Selected: ${it.viewId ?: it.text ?: it.contentDescription ?: it.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useVariable) "Use variable value" else "Use fixed text")
                        Text(
                            if (useVariable) "Resolve the value when this step runs" else "Store this text in the workflow",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = useVariable, onCheckedChange = { useVariable = it })
                }
                if (useVariable) {
                    NodeField(variableName, { variableName = it }, "Variable name", true)
                } else {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Text to enter") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (inputMethod == TextInputMethod.Paste) "Paste through clipboard" else "Set text directly")
                        Text(
                            if (inputMethod == TextInputMethod.Paste) {
                                "Replaces the current system clipboard"
                            } else {
                                "Uses the accessibility set-text action"
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwipeDialog(
    initialStep: Step.Swipe? = null,
    confirmLabel: String = "Add",
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
        title = { Text("Swipe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(startX, { startX = it }, "Start X", true)
                NodeField(startY, { startY = it }, "Start Y", true)
                NodeField(endX, { endX = it }, "End X", true)
                NodeField(endY, { endY = it }, "End Y", true)
                NodeField(duration, { duration = it }, "Duration ms", true)
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
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TapDialog(
    initialStep: Step.Tap? = null,
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onAdd: (Int, Int) -> Unit,
) {
    var xText by remember(initialStep) { mutableStateOf(initialStep?.x?.toString() ?: "540") }
    var yText by remember(initialStep) { mutableStateOf(initialStep?.y?.toString() ?: "1200") }
    val x = xText.toIntOrNull()
    val y = yText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tap coordinate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(xText, { xText = it }, "X coordinate", true)
                NodeField(yText, { yText = it }, "Y coordinate", true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = x != null && x >= 0 && y != null && y >= 0,
                onClick = { onAdd(requireNotNull(x), requireNotNull(y)) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScrollStepDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.Scroll? = null,
    confirmLabel: String = "Add",
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
        title = { Text("Scroll element") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (scrollableNodes.isEmpty()) {
                    Text(
                        "No scrollable nodes observed on the target screen.",
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
                        "Selected: ${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (direction == ScrollDirection.Forward) "Scroll forward" else "Scroll backward")
                        Text(
                            if (direction == ScrollDirection.Forward) "Move toward later content" else "Move toward earlier content",
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String = "Add",
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WaitNodeDialog(
    observedNodes: List<ObservedNode>,
    initialStep: Step.WaitForNode? = null,
    confirmLabel: String = "Add",
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
        title = { Text("Wait for element") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose a recently observed element", fontWeight = FontWeight.SemiBold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "Open the target app once, then return here.",
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
                        "Selected: ${it.viewId ?: it.text ?: it.contentDescription ?: it.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (mustExist) "Wait until element appears" else "Wait until element disappears")
                        Text(
                            if (mustExist) "Continue when a match exists" else "Continue when no match exists",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = mustExist, onCheckedChange = { mustExist = it })
                }
                NodeField(timeout, { timeout = it }, "Timeout ms", true)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetVariableDialog(
    initialName: String = "",
    initialValue: Value = Value.Literal(""),
    confirmLabel: String = "Add",
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
        title = { Text("Set variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NodeField(variableName, { variableName = it }, "Variable name", true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useTemplate) "Variable template" else "Fixed value")
                        Text(
                            if (useTemplate) "Use placeholders such as ${'$'}{orderId}" else "Store this exact value",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = useTemplate, onCheckedChange = { useTemplate = it })
                }
                NodeField(value, { value = it }, if (useTemplate) "Template" else "Value")
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
                Text("Choose the step to wrap", fontWeight = FontWeight.SemiBold)
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
            ) { Text("Wrap") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("If variable matches") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(variableName, { variableName = it }, "Variable name", true)
                ComparisonOperatorSelector(operator) { operator = it }
                NodeField(expectedValue, { expectedValue = it }, "Expected value")
                Text("Run this step when true", fontWeight = FontWeight.SemiBold)
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
            ) { Text("Wrap") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
    ComparisonOperator.Equals -> "Equals"
    ComparisonOperator.NotEquals -> "Does not equal"
    ComparisonOperator.Contains -> "Contains"
    ComparisonOperator.NotContains -> "Does not contain"
}

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
        title = { Text("If element exists") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose an element", fontWeight = FontWeight.SemiBold)
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
                }
                selectedSelector?.let { selector ->
                    Text(
                        "Selected: ${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectorMatchModeControls(selectedSelector) { selectedSelector = it }
                steps?.let { availableSteps ->
                    Text("Run this step when present", fontWeight = FontWeight.SemiBold)
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
            ) { Text(if (steps == null) "Save" else "Wrap") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Read element attribute") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeField(variableName, { variableName = it }, "Save to variable", true)
                Text("Attribute", fontWeight = FontWeight.SemiBold)
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
                Text("Choose an element", fontWeight = FontWeight.SemiBold)
                observedNodes.take(MAX_VISIBLE_OBSERVED_NODES).forEach { node ->
                    OutlinedButton(
                        onClick = { selectedSelector = node.toSelector() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(node.displayName(), modifier = Modifier.fillMaxWidth()) }
                }
                selectedSelector?.let { selector ->
                    Text(
                        "Selected: ${selector.viewId ?: selector.text ?: selector.contentDescription ?: selector.className}",
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            Text("Text contains", modifier = Modifier.weight(1f))
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
            Text("Description contains", modifier = Modifier.weight(1f))
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
        Text("Match occurrence ${matchIndex + 1}", modifier = Modifier.weight(1f))
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
    NodeAttribute.TextOrDescription -> "Text, falling back to description"
    NodeAttribute.Text -> "Text"
    NodeAttribute.ContentDescription -> "Content description"
    NodeAttribute.ViewId -> "Resource ID"
    NodeAttribute.ClassName -> "Control type"
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    val item = removeAt(fromIndex)
    add(toIndex, item)
}

private fun newId(): String = UUID.randomUUID().toString()

@Composable
private fun LaunchAppDialog(
    initialPackageName: String = "",
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val context = LocalContext.current
    val launchableApps = remember { LaunchableAppCatalog(context).load() }
    var packageName by remember(initialPackageName) { mutableStateOf(initialPackageName) }
    var appQuery by remember { mutableStateOf("") }
    val matchingApps = launchableApps.filter { app ->
        appQuery.isBlank() || app.label.contains(appQuery, ignoreCase = true) ||
            app.packageName.contains(appQuery, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Launch an app") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${matchingApps.size} launchable apps",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                matchingApps.take(MAX_VISIBLE_LAUNCHABLE_APPS).forEach { app ->
                    OutlinedButton(
                        onClick = { packageName = app.packageName },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Text("PACKAGE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                NodeField(packageName, { packageName = it }, "Package name", true)
                if (packageName.isNotBlank()) {
                    val selectedApp = launchableApps.firstOrNull { it.packageName == packageName.trim() }
                    Text(
                        selectedApp?.let { "Ready to launch: ${it.label}" }
                            ?: "This package is not launchable on this device",
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
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ClickStepDialog(
    observedNodes: List<ObservedNode>,
    initialSelector: NodeSelector? = null,
    title: String = "Click an element",
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onAdd: (NodeSelector) -> Unit,
) {
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
                Text("RECENTLY OBSERVED", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (observedNodes.isEmpty()) {
                    Text(
                        "Open the target app once, then return here. Its accessible elements will appear here.",
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
                                    "Unique match selected automatically"
                                } else {
                                    "Stable candidate selected; test it on the target screen"
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
                Text("NODE ATTRIBUTES", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                NodeField(packageName, { packageName = it }, "Package name", true)
                NodeField(viewId, { viewId = it }, "Resource ID")
                NodeField(text, { text = it }, "Text")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Text contains", modifier = Modifier.weight(1f))
                    Switch(
                        checked = textContains,
                        enabled = text.isNotBlank(),
                        onCheckedChange = { textContains = it },
                    )
                }
                NodeField(description, { description = it }, "Content description")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Description contains", modifier = Modifier.weight(1f))
                    Switch(
                        checked = descriptionContains,
                        enabled = description.isNotBlank(),
                        onCheckedChange = { descriptionContains = it },
                    )
                }
                NodeField(className, { className = it }, "Class name")
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
                            0 -> "No matching node in the current window"
                            in 1..matchIndex -> "Only $count matches: occurrence ${matchIndex + 1} is unavailable"
                            1 -> "Unique match: selector is ready"
                            else -> "Occurrence ${matchIndex + 1} is available among $count matches"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Test selector") }
                matchResult?.let { result ->
                    Text(
                        result,
                        color = if (result.startsWith("Unique")) Color(0xFF16815F) else Color(0xFFD04F3D),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                "On failure: ${step.failurePolicy.label()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                step.timeoutMillis?.let { "Timeout: $it ms" } ?: "Timeout: workflow default",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Column {
            Row {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Up") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Down") }
            }
            Row {
                TextButton(onClick = onEdit, enabled = canEdit) { Text("Edit") }
                TextButton(onClick = onEditPolicy) { Text("Settings") }
            }
            Row {
                TextButton(onClick = onDuplicate) { Text("Copy") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun FailurePolicy.label(): String = when (this) {
    FailurePolicy.Stop -> "Stop"
    FailurePolicy.Continue -> "Continue"
    is FailurePolicy.Retry -> "Retry $attempts times"
}

private fun Step.isActionEditable(): Boolean = when (this) {
    is Step.Click, is Step.Delay, is Step.GlobalAction, is Step.InputText, is Step.LaunchApp,
    is Step.LongClick, is Step.ReadNodeText, is Step.Repeat, is Step.SetVariable, is Step.Swipe,
    is Step.WaitForNode -> true
    is Step.Scroll, is Step.Tap -> true
    is Step.IfElse -> when (val current = condition) {
        is Condition.NodeExists -> true
        is Condition.Equals -> current.left is Value.Variable && current.right is Value.Literal
    }
}

private fun Step.title(): String = when (this) {
    is Step.Click -> "Click element"
    is Step.Delay -> "Wait $durationMillis ms"
    is Step.GlobalAction -> action.name
    is Step.IfElse -> when (val current = condition) {
        is Condition.Equals -> "If variable ${current.operator.displayName().lowercase()}"
        is Condition.NodeExists -> "If element exists"
    }
    is Step.InputText -> {
        val source = variableName?.let { "variable $it" } ?: "text"
        if (inputMethod == TextInputMethod.Paste) "Paste $source" else "Input $source"
    }
    is Step.Repeat -> "Repeat $times times"
    is Step.Scroll -> "Scroll ${direction.name.lowercase()}"
    is Step.LaunchApp -> "Launch $packageName"
    is Step.LongClick -> "Long click element"
    is Step.ReadNodeText -> "Read ${attribute.displayName()} into $variableName"
    is Step.SetVariable -> "Set variable $name"
    is Step.Swipe -> "Swipe ($startX, $startY) to ($endX, $endY)"
    is Step.Tap -> "Tap ($x, $y)"
    is Step.WaitForNode -> if (mustExist) "Wait for element to appear" else "Wait for element to disappear"
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
                Text("Running $workflowName", fontWeight = FontWeight.SemiBold)
                Text(
                    stepName?.let { "Current step: $it" } ?: "Preparing first step",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
                Text(
                    "Elapsed ${formatElapsed(elapsedMillis)}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                )
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Stop") }
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
private fun PermissionStatus(connected: Boolean, onOpenSettings: () -> Unit) {
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
                if (connected) "Automation service ready" else "Automation service is off",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (connected) "Element lookup and actions are available"
                else "Enable it before testing or running workflows",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (!connected) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("Set up") }
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: Workflow,
    isRunning: Boolean,
    schedule: WorkflowSchedule?,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(workflow.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${workflow.steps.size} steps",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            schedule?.let {
                Text(
                    "Scheduled ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.scheduledAtMillis))}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row {
                TextButton(onClick = onEdit, enabled = !isRunning) { Text("Edit") }
                Button(onClick = if (isRunning) onStop else onRun) {
                    Text(if (isRunning) "Stop" else "Run")
                }
            }
            Row {
                TextButton(onClick = onExport, enabled = !isRunning) { Text("Export") }
                TextButton(onClick = onDuplicate, enabled = !isRunning) { Text("Copy") }
                TextButton(onClick = onDelete, enabled = !isRunning) { Text("Delete") }
            }
            TextButton(
                onClick = if (schedule == null) onSchedule else onCancelSchedule,
                enabled = !isRunning,
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (schedule == null) "Schedule" else "Cancel schedule") }
        }
    }
}

private fun Workflow.exportFileName(): String {
    val safeName = name
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(60)
        .ifBlank { "workflow" }
    return "$safeName.aiflow.json"
}

@Composable
private fun RunRecordRow(record: RunRecord) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(record.workflowName, fontWeight = FontWeight.SemiBold)
            Text(
                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAtMillis))} · ${record.durationMillis} ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            if (record.status == RunStatus.Failed) {
                Text(
                    "Step ${record.failedStepId}: ${record.failureMessage}",
                    color = Color(0xFFD04F3D),
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            record.status.name,
            color = if (record.status == RunStatus.Completed) Color(0xFF16815F) else Color(0xFFD04F3D),
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun RunResult.message(): String = when (this) {
    RunResult.Completed -> "Workflow completed"
    RunResult.AlreadyRunning -> "Another workflow is already running"
    RunResult.Cancelled -> "Workflow stopped"
    is RunResult.Failed -> "Step $stepId failed: $message"
}

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
private const val MAX_VISIBLE_LAUNCHABLE_APPS = 30