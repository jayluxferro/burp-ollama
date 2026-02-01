package ollama

/**
 * Formats Ollama errors into user-friendly, actionable messages.
 */
object OllamaErrorFormatter {

    fun format(error: Throwable, baseUrl: String, model: String): String {
        val msg = error.message ?: "Unknown error"

        // Model not found (from Ollama API: {"error":"model 'X' not found"} or HTTP 404)
        val modelNotFoundMatch = Regex("model\\s*['\"]?([^'\"]+)['\"]?\\s*not found", RegexOption.IGNORE_CASE).find(msg)
        if (modelNotFoundMatch != null) {
            val modelName = modelNotFoundMatch.groupValues.getOrNull(1)?.trim() ?: model
            return """
                Model '$modelName' is not installed.

                Pull it with:
                  ollama pull $modelName

                Or choose a different model in Settings > Burp Ollama.
            """.trimIndent()
        }

        // Connection refused
        if (msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("Connection reset", ignoreCase = true)
        ) {
            return """
                Cannot connect to Ollama at $baseUrl

                • Is Ollama running? Start it from the Ollama app or run: ollama serve
                • Check the URL in Settings > Burp Ollama
            """.trimIndent()
        }

        // Timeout
        if (msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)) {
            return """
                Request timed out.

                • Try a smaller selection
                • Increase timeout in Settings > Burp Ollama
                • Larger models may need more time
            """.trimIndent()
        }

        // HTTP 404 with raw JSON - try to extract error
        val jsonErrorMatch = Regex("""["']error["']\s*:\s*["']([^"']+)["']""").find(msg)
        if (jsonErrorMatch != null) {
            val extracted = jsonErrorMatch.groupValues.getOrNull(1) ?: msg
            if (extracted.contains("not found", ignoreCase = true)) {
                return format(OllamaException(extracted), baseUrl, model)
            }
        }

        // Generic fallback - show clean message without raw JSON
        val cleanMsg = msg
            .replace(Regex("""Ollama returned \d+:\s*\{[^}]*\}"""), "Ollama returned an error")
            .replace(Regex("""Chat failed:\s*"""), "")
        return """
            $cleanMsg

            Check Settings > Burp Ollama and ensure Ollama is running at $baseUrl
        """.trimIndent()
    }
}
