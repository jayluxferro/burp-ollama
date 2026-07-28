package ollama

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OllamaServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var service: OllamaService

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        service = OllamaService(
            baseUrl = mockServer.url("/").toString().trimEnd('/'),
            timeoutSeconds = 5
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        mockServer.shutdown()
    }

    @Test
    fun `healthCheck returns true when Ollama responds 200`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        assertTrue(service.healthCheck())
    }

    @Test
    fun `healthCheck returns false when Ollama responds 500`() {
        mockServer.enqueue(MockResponse().setResponseCode(500))
        assertFalse(service.healthCheck())
    }

    @Test
    fun `listModels returns model names from tags API`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"models":[{"name":"llama3.2:3b"},{"name":"codellama"}]}""")
        )
        val result = service.listModels()
        assertTrue(result.isSuccess)
        assertEquals(listOf("llama3.2:3b", "codellama"), result.getOrNull())
    }

    @Test
    fun `listModels returns failure on non-2xx response`() {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal error"))
        val result = service.listModels()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OllamaException)
    }

    @Test
    fun `chat returns content on success`() {
        mockServer.enqueue(
            MockResponse().setBody("""
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"The sky is blue."},"done":true}
            """.trimIndent())
        )
        val result = service.chat("llama3.2:3b", "You are helpful.", "Why is the sky blue?")
        assertTrue(result.isSuccess)
        assertEquals("The sky is blue.", result.getOrNull()?.content)
    }

    @Test
    fun `chat returns failure on error response`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"error":"model not found"}""")
        )
        val result = service.chat("nonexistent", "", "Hello")
        assertTrue(result.isFailure)
        assertEquals("model not found", (result.exceptionOrNull() as OllamaException).message)
    }

    @Test
    fun `chat sends correct JSON structure`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"content":"ok"},"done":true}""")
        )
        service.chat("llama3.2:3b", "system", "user msg", numCtx = 4096)

        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertTrue(request.path!!.endsWith("/api/chat"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"llama3.2:3b\""))
        // stream defaults to false and is omitted by kotlinx.serialization
        assertFalse(body.contains("\"stream\":true"), "stream should not be true (default is false)")
        assertTrue(body.contains("\"num_ctx\":4096"))
    }

    @Test
    fun `chatStream invokes onChunk for each NDJSON line`() {
        mockServer.enqueue(
            MockResponse().setBody("""
                {"model":"llama3.2:3b","message":{"role":"assistant","content":"Hello"},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":" world"},"done":false}
                {"model":"llama3.2:3b","message":{"role":"assistant","content":""},"done":true}
            """.trimIndent())
        )
        val chunks = mutableListOf<String>()
        val result = service.chatStream("llama3.2:3b", "You are helpful.", "Say hello", onChunk = { chunks.add(it) })
        assertTrue(result.isSuccess)
        assertEquals(listOf("Hello", " world"), chunks)
    }

    @Test
    fun `chatStream sends stream true in request`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"content":"ok"},"done":true}""")
        )
        service.chatStream("llama3.2:3b", "", "hi", onChunk = { })
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertTrue(request!!.body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun `updateConfig trims trailing slash from baseUrl`() {
        val urlWithSlash = mockServer.url("/").toString() // e.g. http://localhost:12345/
        service.updateConfig(urlWithSlash, 60)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        assertTrue(service.healthCheck())
        // Request was made successfully; updateConfig applied (trailing slash trimmed internally)
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertTrue(request!!.path!!.startsWith("/api"))
    }

    @Test
    fun `chat with empty system prompt sends no system message in body`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"role":"assistant","content":"Hi"},"done":true}""")
        )
        service.chat("m", "", "hello")
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertFalse(body.contains("\"role\":\"system\""), "Empty system prompt should not add system message: $body")
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content\":\"hello\""))
    }

    @Test
    fun `chat with non-empty system prompt sends system and user messages`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"content":"ok"},"done":true}""")
        )
        service.chat("m", "You are helpful.", "hi")
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"content\":\"You are helpful.\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content\":\"hi\""))
    }

    @Test
    fun `chat escapes double quotes in content`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"content":"ok"},"done":true}""")
        )
        service.chat("m", "", """say "hello"""")
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertTrue(body.contains("\\\"hello\\\""), "Content quotes should be JSON-escaped: $body")
    }

    @Test
    fun `chatWithMessages sends correct messages array in body`() {
        mockServer.enqueue(
            MockResponse().setBody("""{"message":{"content":"reply"},"done":true}""")
        )
        val messages = listOf(
            ChatMessage(role = "user", content = "first"),
            ChatMessage(role = "assistant", content = "ack"),
            ChatMessage(role = "user", content = "second")
        )
        val result = service.chatWithMessages("m", messages, numCtx = 8192)
        assertTrue(result.isSuccess)
        val request = mockServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content\":\"first\""))
        assertTrue(body.contains("\"content\":\"ack\""))
        assertTrue(body.contains("\"content\":\"second\""))
        assertTrue(body.contains("\"num_ctx\":8192"))
    }

    @Test
    fun `chatWithMessages returns failure when messages list is empty`() {
        val result = service.chatWithMessages("m", emptyList())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OllamaException)
        assertTrue((result.exceptionOrNull() as OllamaException).message!!.contains("No messages"))
    }
}
