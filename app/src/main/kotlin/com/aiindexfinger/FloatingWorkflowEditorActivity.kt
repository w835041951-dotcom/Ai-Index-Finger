package com.aiindexfinger

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiindexfinger.automation.AutomationAccessibilityService
import com.aiindexfinger.automation.PreflightRecoveryAction
import com.aiindexfinger.automation.WorkflowPreflightReport
import com.aiindexfinger.automation.buildWorkflowPreflightReport
import com.aiindexfinger.automation.imageTemplateIsValid
import com.aiindexfinger.automation.openRunningNotificationSettings
import com.aiindexfinger.automation.runningNotificationReadiness
import com.aiindexfinger.data.AppPreferences
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.model.StepPath
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.model.effectiveState
import java.lang.ref.WeakReference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWorkflowEditorActivity : ComponentActivity() {
    private val workflowApplication by lazy { application as AiIndexFingerApplication }
    private val accessibilityDisclosurePreferences by lazy {
        AccessibilityDisclosurePreferences(this)
    }
    private var requestedWorkflowId by mutableStateOf<String?>(null)
    private var openRequestSequence by mutableIntStateOf(0)
    private var collapsed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOpenRequest(intent)
        currentActivity = WeakReference(this)
        configureFloatingWindow()
        setContent {
            AiIndexFingerTheme(AppPreferences(this).appearanceMode()) {
                FloatingWorkflowEditor(
                    requestedWorkflowId = requestedWorkflowId,
                    openRequestSequence = openRequestSequence,
                    application = workflowApplication,
                    onClose = ::finish,
                    onCollapse = ::collapseEditor,
                    accessibilityDisclosureAcknowledged =
                        accessibilityDisclosurePreferences.isAcknowledged(),
                    onAccessibilityDisclosureAcknowledged =
                        accessibilityDisclosurePreferences::acknowledge,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        currentActivity = WeakReference(this)
        collapsed = false
        AutomationAccessibilityService.instance?.hideFloatingEditorRestoreControl()
        configureFloatingWindow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_EDITOR, false)) {
            collapsed = false
            AutomationAccessibilityService.instance?.hideFloatingEditorRestoreControl()
        } else {
            applyOpenRequest(intent)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureFloatingWindow()
    }

    override fun onDestroy() {
        AutomationAccessibilityService.instance?.hideFloatingEditorRestoreControl()
        if (currentActivity?.get() === this) currentActivity = null
        super.onDestroy()
    }

    private fun collapseEditor() {
        val service = AutomationAccessibilityService.instance ?: return
        if (service.showFloatingEditorRestoreControl()) {
            collapsed = true
            if (!moveTaskToBack(true)) {
                collapsed = false
                service.hideFloatingEditorRestoreControl()
            }
        }
    }

    private fun configureFloatingWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            dimAmount = 0.28f
        }
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.90f).toInt(),
        )
    }

    private fun applyOpenRequest(intent: Intent) {
        requestedWorkflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        openRequestSequence += 1
    }

    companion object {
        private const val EXTRA_WORKFLOW_ID = "floating_workflow_id"
        private const val EXTRA_RETURN_TO_EDITOR = "floating_return_to_editor"
        private var currentActivity: WeakReference<FloatingWorkflowEditorActivity>? = null

        fun createIntent(context: Context, workflowId: String? = null): Intent =
            Intent(context, FloatingWorkflowEditorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                workflowId?.let { putExtra(EXTRA_WORKFLOW_ID, it) }
            }

        fun returnIntent(context: Context): Intent? = currentActivity?.get()?.let {
            Intent(context, FloatingWorkflowEditorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_RETURN_TO_EDITOR, true)
            }
        }

        internal fun hasCollapsedSession(): Boolean = currentActivity?.get()?.collapsed == true
    }
}

@Composable
private fun FloatingWorkflowEditor(
    requestedWorkflowId: String?,
    openRequestSequence: Int,
    application: AiIndexFingerApplication,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    accessibilityDisclosureAcknowledged: Boolean,
    onAccessibilityDisclosureAcknowledged: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val canonicalLibrary by application.library.collectAsStateWithLifecycle()
    var loadResult by remember { mutableStateOf<WorkflowLoadResult?>(null) }
    var selectedWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var persistedBaseline by remember { mutableStateOf<Workflow?>(null) }
    var initialEditingStepPath by remember { mutableStateOf<StepPath?>(null) }
    var editorRevision by remember { mutableIntStateOf(0) }
    var preflight by remember { mutableStateOf<Pair<Workflow, WorkflowPreflightReport>?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var handledOpenRequest by remember { mutableIntStateOf(0) }
    val accessibilityDisclosureGate = remember(accessibilityDisclosureAcknowledged) {
        AccessibilityDisclosureGate(accessibilityDisclosureAcknowledged)
    }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val requestAccessibilitySetup = {
        when (accessibilityDisclosureGate.requestSetup()) {
            AccessibilityDisclosureAction.ShowDisclosure -> showAccessibilityDisclosure = true
            AccessibilityDisclosureAction.OpenSettings -> onOpenAccessibilitySettings()
            AccessibilityDisclosureAction.StayInApp -> Unit
        }
    }

    DisposableEffect(Unit) {
        val observationLease = AutomationAccessibilityService.acquireObservationLease()
        onDispose { observationLease?.close() }
    }

    LaunchedEffect(Unit) {
        loadResult = withContext(Dispatchers.IO) { application.loadCanonicalLibrary() }
    }
    LaunchedEffect(canonicalLibrary, openRequestSequence) {
        val library = canonicalLibrary
        if (library != null && handledOpenRequest != openRequestSequence) {
            if (selectedWorkflow == null) {
                selectedWorkflow = requestedWorkflowId?.let { workflowId ->
                    library.workflows.firstOrNull { it.id == workflowId }
                }
                persistedBaseline = selectedWorkflow
                initialEditingStepPath = null
            }
            handledOpenRequest = openRequestSequence
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
    ) {
        val workflow = selectedWorkflow
        if (workflow == null) {
            FloatingWorkflowPicker(
                library = canonicalLibrary,
                loadResult = loadResult,
                onSelect = {
                    persistedBaseline = it
                    selectedWorkflow = it
                },
                onCreate = {
                    persistedBaseline = null
                    selectedWorkflow = Workflow(
                        id = UUID.randomUUID().toString(),
                        name = it,
                        steps = emptyList(),
                        state = WorkflowState.Draft,
                    )
                },
                onClose = onClose,
                onCollapse = onCollapse,
            )
        } else {
            key(workflow.id, editorRevision) {
                WorkflowEditor(
                    workflow = workflow,
                    persistedBaseline = persistedBaseline,
                    initialEditingStepPath = initialEditingStepPath,
                    floatingEditorMode = true,
                    onCollapse = onCollapse,
                    saveInProgress = saving,
                    onSetUpAutomation = requestAccessibilitySetup,
                    onTest = { candidate ->
                        val service = AutomationAccessibilityService.instance
                        val notificationStatus = runningNotificationReadiness(application)
                        val displaySize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            application.getSystemService(WindowManager::class.java)
                                .maximumWindowMetrics.bounds
                                .let { it.width() to it.height() }
                        } else {
                            application.resources.displayMetrics.let {
                                it.widthPixels to it.heightPixels
                            }
                        }
                        preflight = candidate to buildWorkflowPreflightReport(
                            workflow = candidate,
                            accessibilityConnected = service != null,
                            notificationStatus = notificationStatus,
                            isLaunchable = { packageName, intentAction ->
                                intentAction?.let { Intent(it).setPackage(packageName) }
                                    ?.resolveActivity(application.packageManager) != null ||
                                    intentAction == null &&
                                    application.packageManager.getLaunchIntentForPackage(packageName) != null
                            },
                            countMatches = { selector -> service?.countMatches(selector) ?: 0 },
                            imageCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                            displayWidth = displaySize.first,
                            displayHeight = displaySize.second,
                            isImageTemplateValid = ::imageTemplateIsValid,
                        )
                    },
                    onBack = {
                        selectedWorkflow = null
                        persistedBaseline = null
                        initialEditingStepPath = null
                    },
                    onSave = { expected, candidate ->
                        if (!saving) {
                            saving = true
                            saveError = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        application.commitWorkflow(expected, candidate)
                                    }
                                }.onSuccess { commit ->
                                    if (commit.cleanupError == null) {
                                        selectedWorkflow = null
                                        persistedBaseline = null
                                        initialEditingStepPath = null
                                    } else {
                                        persistedBaseline = candidate
                                        selectedWorkflow = candidate
                                        editorRevision += 1
                                        saveError = application.getString(
                                            R.string.workflow_saved_schedule_cleanup_failed,
                                        )
                                    }
                                }.onFailure { error ->
                                    saveError = application.getString(
                                        if (error is com.aiindexfinger.data.WorkflowEditConflictException) {
                                            R.string.workflow_edit_conflict
                                        } else {
                                            R.string.save_failed
                                        },
                                    )
                                }
                                saving = false
                            }
                        }
                    },
                )
            }
        }
    }

    if (saving) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.floating_editor_saving)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }
    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text(stringResource(R.string.floating_editor_save_issue)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { saveError = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
    preflight?.let { (workflow, report) ->
        PreflightReportDialog(
            workflow = workflow,
            report = report,
            onDismiss = { preflight = null },
            onEditStep = { path ->
                preflight = null
                selectedWorkflow = workflow
                initialEditingStepPath = path
                editorRevision += 1
            },
            onRecoveryAction = { action ->
                preflight = null
                when (action) {
                    PreflightRecoveryAction.SetUpAutomation -> requestAccessibilitySetup()
                    PreflightRecoveryAction.OpenNotificationSettings -> {
                        if (!openRunningNotificationSettings(application, report.notificationStatus)) {
                            saveError = application.getString(R.string.notification_settings_unavailable)
                        }
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
}

@Composable
private fun FloatingWorkflowPicker(
    library: WorkflowLibrary?,
    loadResult: WorkflowLoadResult?,
    onSelect: (Workflow) -> Unit,
    onCreate: (String) -> Unit,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
) {
    val untitledName = stringResource(R.string.untitled_workflow)
    BackHandler(onBack = onClose)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCollapse,
                    modifier = Modifier.weight(1f).testTag(FLOATING_EDITOR_COLLAPSE_TAG),
                ) {
                    Text(stringResource(R.string.floating_editor_collapse))
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.close))
                }
                Button(
                    onClick = { onCreate(untitledName) },
                    modifier = Modifier.weight(1f).testTag(FLOATING_EDITOR_NEW_TAG),
                ) {
                    Text(stringResource(R.string.new_workflow))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.floating_editor_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.floating_editor_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            HorizontalDivider()
            when (loadResult) {
                null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is WorkflowLoadResult.Corrupt -> Text(
                    stringResource(R.string.workflows_corrupt),
                    color = MaterialTheme.colorScheme.error,
                )
                is WorkflowLoadResult.UnsupportedVersion -> Text(
                    stringResource(R.string.workflows_unsupported_version),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val workflows = library?.workflows.orEmpty()
                    if (workflows.isEmpty()) Text(stringResource(R.string.no_workflows))
                    workflows.forEach { workflow ->
                        OutlinedButton(
                            onClick = { onSelect(workflow) },
                            modifier = Modifier.fillMaxWidth().testTag(floatingWorkflowTag(workflow.id)),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(workflow.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    pluralStringResource(
                                        R.plurals.floating_editor_workflow_summary,
                                        workflow.steps.size,
                                        stringResource(
                                            if (workflow.effectiveState() == WorkflowState.Ready) {
                                                R.string.workflow_state_ready
                                            } else {
                                                R.string.workflow_state_draft
                                            },
                                        ),
                                        workflow.steps.size,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

internal const val FLOATING_EDITOR_NEW_TAG = "floating-editor-new"
internal const val FLOATING_EDITOR_COLLAPSE_TAG = "floating-editor-collapse"
internal fun floatingWorkflowTag(workflowId: String) = "floating-workflow-$workflowId"
