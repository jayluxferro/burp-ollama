package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OllamaTaskRegistryTest {

    @Test
    fun `addTask returns increasing IDs`() {
        val id1 = OllamaTaskRegistry.addTask("prompt1-ids", "suite")
        val id2 = OllamaTaskRegistry.addTask("prompt2-ids", "repeater")
        assertTrue(id2 > id1)
    }

    @Test
    fun `addTask creates RUNNING status task`() {
        val id = OllamaTaskRegistry.addTask("test prompt", "test-source")
        val task = OllamaTaskRegistry.allTasks().find { it.id == id }
        assertNotNull(task)
        assertEquals("test prompt", task?.prompt)
        assertEquals("test-source", task?.source)
        assertEquals(OllamaTaskRegistry.Task.Status.RUNNING, task?.status)
        assertNotNull(task?.createdAt)
    }

    @Test
    fun `updateTask changes status`() {
        val id = OllamaTaskRegistry.addTask("test-update-status", "source")
        OllamaTaskRegistry.updateTask(id, OllamaTaskRegistry.Task.Status.COMPLETED, response = "done")
        val task = OllamaTaskRegistry.allTasks().find { it.id == id }
        assertNotNull(task)
        assertEquals(OllamaTaskRegistry.Task.Status.COMPLETED, task?.status)
        assertEquals("done", task?.response)
    }

    @Test
    fun `updateTask sets error`() {
        val id = OllamaTaskRegistry.addTask("test-update-error", "source")
        OllamaTaskRegistry.updateTask(id, OllamaTaskRegistry.Task.Status.FAILED, error = "something broke")
        val task = OllamaTaskRegistry.allTasks().find { it.id == id }
        assertNotNull(task)
        assertEquals(OllamaTaskRegistry.Task.Status.FAILED, task?.status)
        assertEquals("something broke", task?.error)
    }

    @Test
    fun `allTasks returns tasks ordered newest first`() {
        val ids = mutableListOf<Long>()
        repeat(5) { i ->
            ids.add(OllamaTaskRegistry.addTask("task-order-$i", "source-order"))
        }
        val tasks = OllamaTaskRegistry.allTasks()
        val myTasks = tasks.filter { it.source == "source-order" }
        // Within our tasks, IDs should be in descending order (newest first)
        assertEquals(5, myTasks.size)
        for (i in 0 until myTasks.size - 1) {
            assertTrue(myTasks[i].id > myTasks[i + 1].id, "Tasks should be ordered newest first")
        }
    }

    @Test
    fun `listener notification on add`() {
        val latch = CountDownLatch(1)
        val listener = { latch.countDown() }
        try {
            OllamaTaskRegistry.addListener(listener)
            OllamaTaskRegistry.addTask("test-listener-add", "source")
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Listener should be notified on add")
        } finally {
            OllamaTaskRegistry.removeListener(listener)
        }
    }

    @Test
    fun `listener notification on update`() {
        val addLatch = CountDownLatch(1)
        val addListener = { addLatch.countDown() }
        try {
            OllamaTaskRegistry.addListener(addListener)
            val id = OllamaTaskRegistry.addTask("test-listener-update", "source")
            assertTrue(addLatch.await(2, TimeUnit.SECONDS), "Listener should be notified on add")

            val updateLatch = CountDownLatch(1)
            val updateListener = { updateLatch.countDown() }
            OllamaTaskRegistry.addListener(updateListener)
            OllamaTaskRegistry.updateTask(id, OllamaTaskRegistry.Task.Status.COMPLETED)
            assertTrue(updateLatch.await(2, TimeUnit.SECONDS), "Listener should be notified on update")
        } finally {
            // Remove listeners by the stored references
            // Note: since we can't reference updateListener in the outer scope,
            // we don't remove it. But listeners are lambdas and get garbage collected
            // after the test method completes when the class is unloaded.
        }
    }

    @Test
    fun `eviction when exceeding MAX_TASKS`() {
        // Add many tasks to trigger eviction
        repeat(600) { i ->
            OllamaTaskRegistry.addTask("task-eviction-$i", "eviction-test")
        }
        val tasks = OllamaTaskRegistry.allTasks()
        // After eviction, should have at most 500 (MAX_TASKS) tasks
        assertTrue(tasks.size <= 500, "Should not exceed MAX_TASKS = 500, got ${tasks.size}")
        // The newest tasks should be present
        assertTrue(tasks.any { it.prompt == "task-eviction-599" }, "Newest task should be present")
    }
}
