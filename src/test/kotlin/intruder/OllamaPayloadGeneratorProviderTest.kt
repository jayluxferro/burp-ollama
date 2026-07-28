package intruder

import burp.api.montoya.core.ByteArray
import burp.api.montoya.http.HttpService
import burp.api.montoya.intruder.AttackConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Optional

class OllamaPayloadGeneratorProviderTest {

    @Test
    fun `displayName is Ollama static`() {
        val provider = OllamaPayloadGeneratorProvider()
        assertEquals("Ollama (static)", provider.displayName())
    }

    @Test
    fun `providePayloadGenerator returns non-null generator`() {
        val provider = OllamaPayloadGeneratorProvider()
        val config = createMinimalAttackConfig()
        val generator = provider.providePayloadGenerator(config)
        assertNotNull(generator)
    }

    /**
     * Note: The generator (OllamaPayloadGenerator) internally calls
     * GeneratedPayload.payload(...) which requires the Montoya ObjectFactory
     * provided by Burp at runtime. In test, this factory is not available,
     * so generatePayloadFor() will throw. We test the provider instead.
     *
     * The generation logic is straightforward: it iterates through a static
     * payload list and returns each in turn, then returns end when exhausted.
     * This is verified through the public API in integration tests.
     */

    private fun createMinimalAttackConfig(): AttackConfiguration = object : AttackConfiguration {
        override fun httpService(): Optional<HttpService> = Optional.empty()
        override fun requestTemplate() = throw UnsupportedOperationException("Not needed for test")
    }
}
