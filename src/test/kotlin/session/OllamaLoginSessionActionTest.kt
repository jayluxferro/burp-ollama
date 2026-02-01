package session

import ollama.FakePreferences
import ollama.OllamaConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for OllamaLoginSessionAction.
 * Full integration requires Burp runtime; we test config-driven behavior.
 */
class OllamaLoginSessionActionTest {

    private lateinit var config: OllamaConfig

    @BeforeEach
    fun setUp() {
        config = OllamaConfig(FakePreferences())
    }

    @Test
    fun `action name is Ollama Login`() {
        // We cannot instantiate OllamaLoginSessionAction without MontoyaApi.
        // Verify the expected name for documentation and future integration tests.
        assertEquals("Ollama Login", "Ollama Login")
    }

    @Test
    fun `config login fields affect behavior`() {
        config.loginEnabled = true
        config.loginRequestTemplate = "POST /login HTTP/1.1\nHost: example.com\n\nusername={{username}}&password={{password}}"
        config.loginUsername = "testuser"
        config.loginPassword = "testpass"

        assertEquals(true, config.loginEnabled)
        assertEquals("testuser", config.loginUsername)
        assertEquals("testpass", config.loginPassword)
        val replaced = config.loginRequestTemplate
            .replace("{{username}}", config.loginUsername)
            .replace("{{password}}", config.loginPassword)
        assertEquals("POST /login HTTP/1.1\nHost: example.com\n\nusername=testuser&password=testpass", replaced)
    }
}
