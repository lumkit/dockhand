package io.github.dockhand.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class TaskState {
    Waiting,
    Running,
    Completed,
    Failed,
    Cancelled,
}

data class TaskProgress(
    val completed: Long? = null,
    val total: Long? = null,
) {
    val isIndeterminate: Boolean
        get() = completed == null || total == null || total <= 0

    val fraction: Float?
        get() {
            val done = completed ?: return null
            val all = total ?: return null
            if (all <= 0) return null
            return (done.toDouble() / all.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

data class AppTask(
    val id: Long,
    val title: String,
    val detail: String? = null,
    val state: TaskState = TaskState.Running,
    val progress: TaskProgress = TaskProgress(),
    val isBackground: Boolean = false,
    val error: String? = null,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
    val waitingMessage: String? = null,
)

/** 系统通知；返回 false 时由 UI 用 snackbar 兜底。 */
interface TaskNotifier {
    suspend fun notify(title: String, message: String): Boolean
}

object NoopTaskNotifier : TaskNotifier {
    override suspend fun notify(title: String, message: String): Boolean = false
}

class TaskContext internal constructor(
    private val onProgress: (progress: TaskProgress, speed: Double?, etaSeconds: Long?) -> Unit,
    private val onWaiting: (message: String?) -> Unit,
    private val rollbacks: MutableList<suspend () -> Unit>,
    private val job: Job,
) {
    private var sample: ProgressSample? = null

    private data class ProgressSample(
        val at: TimeMark,
        val completed: Long,
        val speed: Double?,
    )

    val isCancelled: Boolean
        get() = !job.isActive

    fun report(completed: Long?, total: Long?) {
        val progress = TaskProgress(completed, total)
        var speed: Double? = null
        var eta: Long? = null
        if (
            completed != null &&
            total != null &&
            total > 1024 &&
            completed in 0..total
        ) {
            val now = TimeSource.Monotonic.markNow()
            val previous = sample
            if (previous != null && completed >= previous.completed) {
                val elapsed = previous.at.elapsedNow().inWholeMilliseconds / 1000.0
                if (elapsed > 0.05) {
                    val instant = (completed - previous.completed) / elapsed
                    speed = previous.speed
                        ?.let { it * 0.7 + instant * 0.3 }
                        ?: instant
                    speed = speed.takeIf { it > 0.0 }
                } else {
                    speed = previous.speed
                }
            }
            if (speed != null) {
                eta = ((total - completed) / speed).toLong().coerceAtLeast(0)
            }
            sample = ProgressSample(now, completed, speed)
        } else {
            sample = null
        }
        onProgress(progress, speed, eta)
    }

    fun waiting(message: String) {
        onWaiting(message)
    }

    fun running() {
        onWaiting(null)
    }

    fun addRollback(block: suspend () -> Unit) {
        rollbacks += block
    }

    suspend fun ensureActive() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }
}

class TaskManager(
    private val scope: CoroutineScope,
    private val notifier: TaskNotifier = NoopTaskNotifier,
    private val onFallbackMessage: (String) -> Unit = {},
) {
    private val _tasks = MutableStateFlow<List<AppTask>>(emptyList())
    val tasks: StateFlow<List<AppTask>> = _tasks.asStateFlow()

    private val jobs = mutableMapOf<Long, Job>()
    private var nextId = 0L

    fun start(
        title: String,
        detail: String? = null,
        background: Boolean,
        onFinished: (() -> Unit)? = null,
        operation: suspend TaskContext.() -> Unit,
    ): Long {
        val id = nextId++
        val rollbacks = mutableListOf<suspend () -> Unit>()
        _tasks.update { it + AppTask(id, title, detail, isBackground = background) }
        jobs[id] = scope.launch {
            val context = TaskContext(
                onProgress = { progress, speed, eta ->
                    updateTask(id) {
                        it.copy(
                            progress = progress,
                            speedBytesPerSecond = speed,
                            etaSeconds = eta,
                        )
                    }
                },
                onWaiting = { message ->
                    updateTask(id) {
                        it.copy(
                            state = if (message == null) TaskState.Running else TaskState.Waiting,
                            waitingMessage = message,
                            speedBytesPerSecond = null,
                            etaSeconds = null,
                        )
                    }
                },
                rollbacks = rollbacks,
                job = currentCoroutineContext()[Job] ?: error("task job missing"),
            )
            try {
                context.operation()
                updateTask(id) { it.copy(state = TaskState.Completed) }
                if (background) notify(id)
                removeTask(id)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { runRollbacks(rollbacks) }
                updateTask(id) { it.copy(state = TaskState.Cancelled) }
                if (background) withContext(NonCancellable) { notify(id) }
                removeTask(id)
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) { runRollbacks(rollbacks) }
                updateTask(id) {
                    it.copy(state = TaskState.Failed, error = e.message)
                }
                if (background) notify(id)
                removeTask(id)
            } finally {
                runCatching { onFinished?.invoke() }
                jobs.remove(id)
            }
        }
        return id
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    fun dismiss(id: Long) {
        if (jobs[id] == null) {
            _tasks.update { tasks -> tasks.filterNot { it.id == id } }
        }
    }

    private fun updateTask(id: Long, block: (AppTask) -> AppTask) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) block(it) else it }
        }
    }

    private fun removeTask(id: Long) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
    }

    private suspend fun runRollbacks(rollbacks: List<suspend () -> Unit>) {
        rollbacks.asReversed().forEach { rollback ->
            runCatching { rollback() }
        }
    }

    private suspend fun notify(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        val title = when (task.state) {
            TaskState.Completed -> "任务完成"
            TaskState.Failed -> "任务失败"
            TaskState.Cancelled -> "任务已取消"
            TaskState.Waiting -> "等待设备空闲"
            TaskState.Running -> "任务运行中"
        }
        val message = buildString {
            append(task.title)
            task.error?.let { append("：").append(it) }
        }
        val delivered = runCatching { notifier.notify(title, message) }.getOrDefault(false)
        if (!delivered) {
            onFallbackMessage("$title：$message")
        }
    }
}
