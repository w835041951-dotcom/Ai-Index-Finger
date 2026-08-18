package com.aiindexfinger

import android.app.Application
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.commitImportedLibrary
import com.aiindexfinger.data.WorkflowLoadResult
import com.aiindexfinger.data.WorkflowPersistenceCoordinator
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.data.WorkflowVersion
import com.aiindexfinger.data.WorkflowRollbackCommit
import com.aiindexfinger.data.commitLibraryUpdate
import com.aiindexfinger.data.commitLibraryUpdateWithCleanup
import com.aiindexfinger.data.commitWorkflowDeletion
import com.aiindexfinger.data.completeWorkflowLibraryCommit
import com.aiindexfinger.model.Workflow
import com.aiindexfinger.model.WorkflowState
import com.aiindexfinger.scheduler.WorkflowScheduler
import com.aiindexfinger.scheduler.WorkflowSchedule
import com.aiindexfinger.scheduler.ScheduleRecurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AiIndexFingerApplication : Application() {
    private val workflowStore by lazy { WorkflowStore(applicationContext) }
    private val workflowScheduler by lazy { WorkflowScheduler(applicationContext) }
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val coordinator = WorkflowPersistenceCoordinator(persistenceScope)
    private val mutableLibrary = MutableStateFlow<WorkflowLibrary?>(null)
    val library = mutableLibrary.asStateFlow()

    suspend fun updateLibrary(update: (WorkflowLibrary) -> WorkflowLibrary): WorkflowLibrary =
        coordinator.submit {
            commitLibraryUpdate(
                current = { workflowStore.loadDetailed().library },
                update = update,
                save = { library ->
                    workflowStore.saveLibrary(library)
                    mutableLibrary.value = library
                },
            )
        }.await()

    suspend fun importLibrary(imported: WorkflowLibrary): WorkflowLibrary = coordinator.submit {
        commitImportedLibrary(
            current = { workflowStore.loadDetailed().library },
            imported = imported,
            newId = { UUID.randomUUID().toString() },
            importedName = { name -> getString(R.string.imported_workflow_name, name) },
            save = { merged ->
                workflowStore.saveLibrary(merged)
                mutableLibrary.value = merged
            },
        )
    }.await()

    internal suspend fun deleteWorkflow(workflowId: String) = coordinator.submit {
        commitWorkflowDeletion(
            current = { workflowStore.loadDetailed().library },
            workflowId = workflowId,
            save = { library ->
                workflowStore.saveLibrary(library)
                mutableLibrary.value = library
            },
            cleanup = { workflowScheduler.cancel(workflowId) },
        )
    }.await()

    internal suspend fun commitWorkflow(expected: Workflow?, workflow: Workflow) = coordinator.submit {
        commitLibraryUpdateWithCleanup(
            current = { workflowStore.loadDetailed().library },
            update = { latest -> latest.withWorkflowIfUnchanged(expected, workflow) },
            save = { library ->
                workflowStore.saveLibrary(library)
                mutableLibrary.value = library
            },
            cleanup = if (workflow.state == WorkflowState.Draft) {
                { workflowScheduler.cancel(workflow.id) }
            } else {
                null
            },
        )
    }.await()

    suspend fun loadCanonicalLibrary(): WorkflowLoadResult = coordinator.submit {
        workflowStore.loadDetailed().also { mutableLibrary.value = it.library }
    }.await()

    suspend fun listVersions(workflowId: String): List<WorkflowVersion> =
        coordinator.submit { workflowStore.listVersions(workflowId) }.await()

    internal suspend fun scheduleWorkflow(
        workflow: Workflow,
        targetEpochMillis: Long,
        recurrence: ScheduleRecurrence,
    ): List<WorkflowSchedule> = coordinator.submit {
        workflowScheduler.schedule(workflow, targetEpochMillis, recurrence)
    }.await()

    internal suspend fun cancelWorkflowSchedule(workflowId: String): List<WorkflowSchedule> =
        coordinator.submit { workflowScheduler.cancel(workflowId) }.await()

    internal suspend fun reloadWorkflowSchedules(workflowIds: Set<String>): List<WorkflowSchedule> =
        coordinator.submit { workflowScheduler.load(workflowIds) }.await()

    internal suspend fun loadWorkflowSchedulesWithoutReconciliation(): List<WorkflowSchedule> =
        coordinator.submit { workflowScheduler.loadWithoutReconciliation() }.await()

    internal suspend fun consumeMissedWorkflowSchedule(workflowId: String): List<WorkflowSchedule> =
        coordinator.submit { workflowScheduler.consumeMissedOccurrence(workflowId) }.await()

    internal suspend fun rollback(workflowId: String, versionId: String) = coordinator.submit {
        val restored = workflowStore.rollback(workflowId, versionId)
        val library = workflowStore.loadDetailed().library
        mutableLibrary.value = library
        val cleanup: (suspend () -> List<WorkflowSchedule>)? = if (restored.state == WorkflowState.Draft) {
            { workflowScheduler.cancel(restored.id) }
        } else {
            null
        }
        WorkflowRollbackCommit(
            workflow = restored,
            libraryCommit = completeWorkflowLibraryCommit(library, cleanup),
        )
    }.await()

}