package com.aiindexfinger

import android.app.Application
import com.aiindexfinger.data.WorkflowLibrary
import com.aiindexfinger.data.PersistenceFailureEventController
import com.aiindexfinger.data.WorkflowPersistenceCoordinator
import com.aiindexfinger.data.WorkflowStore
import com.aiindexfinger.data.WorkflowVersion
import com.aiindexfinger.model.Workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiIndexFingerApplication : Application() {
    private val workflowStore by lazy { WorkflowStore(applicationContext) }
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val coordinator = WorkflowPersistenceCoordinator(persistenceScope)
    private val persistenceFailures = PersistenceFailureEventController()
    val persistenceFailure = persistenceFailures.event

    fun saveLibrary(library: WorkflowLibrary) {
        val operation = coordinator.submit { workflowStore.saveLibrary(library) }
        persistenceScope.launch {
            runCatching { operation.await() }
                .onFailure { persistenceFailures.publish(getString(R.string.save_failed)) }
        }
    }

    fun consumePersistenceFailure(sequence: Long) {
        persistenceFailures.consume(sequence)
    }

    suspend fun commitLibrary(library: WorkflowLibrary) {
        coordinator.submit { workflowStore.saveLibrary(library) }.await()
    }

    suspend fun listVersions(workflowId: String): List<WorkflowVersion> =
        coordinator.submit { workflowStore.listVersions(workflowId) }.await()

    suspend fun rollback(workflowId: String, versionId: String): Workflow =
        coordinator.submit { workflowStore.rollback(workflowId, versionId) }.await()

    fun loadLibrary() = workflowStore.loadDetailed()
}