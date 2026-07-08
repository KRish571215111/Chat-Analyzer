package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class TaskType {
    IMPORT, EXPORT, AI_ANALYSIS, THUMBNAIL_GENERATION, SEARCH_INDEXING, DB_OPTIMIZATION
}

enum class TaskStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

data class BackgroundTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TaskType,
    var status: TaskStatus = TaskStatus.QUEUED,
    var progress: Float = 0f,
    var message: String = "Waiting...",
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var error: String? = null
)

object TaskManager {
    private val _tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val tasks: StateFlow<List<BackgroundTask>> = _tasks.asStateFlow()

    fun submitTask(task: BackgroundTask) {
        _tasks.value = _tasks.value + task
    }

    fun updateTaskProgress(id: String, progress: Float, message: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(progress = progress, message = message, status = TaskStatus.RUNNING) else it
        }
    }

    fun completeTask(id: String, success: Boolean = true, error: String? = null) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) {
                it.copy(
                    status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                    progress = if (success) 1f else it.progress,
                    message = if (success) "Completed" else "Failed",
                    endTime = System.currentTimeMillis(),
                    error = error
                )
            } else it
        }
    }
    
    fun cancelTask(id: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id && it.status in listOf(TaskStatus.QUEUED, TaskStatus.RUNNING)) {
                it.copy(status = TaskStatus.CANCELLED, message = "Cancelled", endTime = System.currentTimeMillis())
            } else it
        }
    }
    
    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { 
            it.status in listOf(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.PAUSED)
        }
    }
}
