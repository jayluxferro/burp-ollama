package intruder

import burp.api.montoya.intruder.AttackConfiguration
import burp.api.montoya.intruder.GeneratedPayload
import burp.api.montoya.intruder.IntruderInsertionPoint
import burp.api.montoya.intruder.PayloadGenerator
import burp.api.montoya.intruder.PayloadGeneratorProvider

/**
 * Registers "Ollama" as an Intruder payload generator. The generator yields a list of payloads.
 * Default payloads are a small set of common test values; future enhancement could call Ollama to generate from the request context.
 */
class OllamaPayloadGeneratorProvider : PayloadGeneratorProvider {

    override fun displayName(): String = "Ollama"

    override fun providePayloadGenerator(attackConfiguration: AttackConfiguration): PayloadGenerator =
        OllamaPayloadGenerator()
}

/**
 * Yields payloads one by one. Uses a default list of common fuzz values; can be extended to use Ollama-generated payloads.
 */
private class OllamaPayloadGenerator : PayloadGenerator {

    private val payloads = listOf(
        "'", "\"", "<script>", "{{7*7}}", "1' OR '1'='1",
        "admin", "test", "payload", "\\", "\n",
        "<img src=x>", "${'$'}{1+1}", "\u0000"
    )
    private var index = 0

    override fun generatePayloadFor(insertionPoint: IntruderInsertionPoint): GeneratedPayload {
        return if (index < payloads.size) {
            GeneratedPayload.payload(payloads[index++])
        } else {
            GeneratedPayload.end()
        }
    }
}
