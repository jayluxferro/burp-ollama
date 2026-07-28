package ollama

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OllamaModelCacheTest {

    @BeforeEach
    fun setUp() {
        // Reset to known state (setter routes through update() which notifies, but no listeners yet)
        OllamaModelCache.models = emptyList()
    }

    @Test
    fun `initial state is empty`() {
        assertTrue(OllamaModelCache.models.isEmpty())
    }

    @Test
    fun `update changes models list`() {
        OllamaModelCache.update(listOf("llama3.2:3b", "codellama"))
        assertEquals(listOf("llama3.2:3b", "codellama"), OllamaModelCache.models)
    }

    @Test
    fun `setter changes models list`() {
        OllamaModelCache.models = listOf("mistral")
        assertEquals(listOf("mistral"), OllamaModelCache.models)
    }

    @Test
    fun `listener is notified on update`() {
        val latch = CountDownLatch(1)
        val listener: (List<String>) -> Unit = { latch.countDown() }
        try {
            OllamaModelCache.addListener(listener)
            OllamaModelCache.update(listOf("llama3.2:3b"))
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Listener should be notified via EDT")
        } finally {
            OllamaModelCache.removeListener(listener)
        }
    }

    @Test
    fun `listener receives correct model list on update`() {
        val latch = CountDownLatch(1)
        var receivedModels: List<String>? = null
        val listener: (List<String>) -> Unit = { models ->
            receivedModels = models
            latch.countDown()
        }
        try {
            OllamaModelCache.addListener(listener)
            val expected = listOf("llama3.2:3b", "mistral")
            OllamaModelCache.update(expected)
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals(expected, receivedModels)
        } finally {
            OllamaModelCache.removeListener(listener)
        }
    }

    @Test
    fun `setter notifies listeners since it routes through update`() {
        val latch = CountDownLatch(1)
        val listener: (List<String>) -> Unit = { latch.countDown() }
        try {
            OllamaModelCache.addListener(listener)
            OllamaModelCache.models = listOf("codellama")
            // The setter calls update(), which notifies listeners via EDT
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Setter should notify listeners since it routes through update()")
        } finally {
            OllamaModelCache.removeListener(listener)
        }
    }

    @Test
    fun `multiple listeners all get notified`() {
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        val listener1: (List<String>) -> Unit = { latch1.countDown() }
        val listener2: (List<String>) -> Unit = { latch2.countDown() }
        try {
            OllamaModelCache.addListener(listener1)
            OllamaModelCache.addListener(listener2)
            OllamaModelCache.update(listOf("llama3.2:3b"))
            assertTrue(latch1.await(2, TimeUnit.SECONDS), "Listener 1 should be notified")
            assertTrue(latch2.await(2, TimeUnit.SECONDS), "Listener 2 should be notified")
        } finally {
            OllamaModelCache.removeListener(listener1)
            OllamaModelCache.removeListener(listener2)
        }
    }
}
