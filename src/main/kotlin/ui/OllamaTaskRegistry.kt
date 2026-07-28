package ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Central registry for Ollama AI tasks.
 * Tracks tasks from Suite tab, Repeater tab, and context menu.
 */
object OllamaTaskRegistry {

    data class Task(
        val id: Long,
        val prompt: String,
        val source: String,
        val status: Status,
        val response: String?,
        val error: String?,
        val createdAt: Instant
    ) {
        enum class Status { RUNNING, COMPLETED, FAILED, CANCELLED }
        fun promptPreview(maxLen: Int = 60): String =
            if (prompt.length <= maxLen) prompt else prompt.take(maxLen) + "…"
        fun formattedTime(): String =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(createdAt)
    }

    private val tasks = CopyOnWriteArrayList<Task>()
    private var nextId = 0L
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private const val MAX_TASKS = 500

    fun addTask(prompt: String, source: String): Long {
        val id = ++nextId
        if (tasks.size >= MAX_TASKS) {
            val evicted = tasks.size - 400
            tasks.subList(400, tasks.size).clear()
            java.util.logging.Logger.getLogger(OllamaTaskRegistry::class.java.name)
                .info("Evicted $evicted old tasks (max $MAX_TASKS)")
        }
        val task = Task(id, prompt, source, Task.Status.RUNNING, null, null, Instant.now())
        tasks.add(0, task)
        notifyListeners()
        return id
    }

    fun updateTask(id: Long, status: Task.Status, response: String? = null, error: String? = null) {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val t = tasks[idx]
            tasks[idx] = t.copy(status = status, response = response, error = error)
            notifyListeners()
        }
    }

    fun allTasks(): List<Task> = tasks.toList()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        SwingUtilities.invokeLater {
            listeners.forEach { it() }
        }
    }
}
