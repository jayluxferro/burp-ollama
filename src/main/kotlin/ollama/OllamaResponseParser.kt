package ollama

/**
 * Parses Ollama API responses.
 * Extracted for testability.
 */
object OllamaResponseParser {

    fun parseTagsResponse(body: String): List<String> {
        return try {
            val models = mutableListOf<String>()
            var i = 0
            while (i < body.length) {
                val nameIdx = body.indexOf("\"name\"", i, ignoreCase = true)
                if (nameIdx == -1) break
                val colonIdx = body.indexOf(':', nameIdx)
                if (colonIdx == -1) break
                val startQuote = body.indexOf('"', colonIdx + 1)
                if (startQuote == -1) break
                val endQuote = body.indexOf('"', startQuote + 1)
                if (endQuote == -1) break
                val name = body.substring(startQuote + 1, endQuote)
                if (name.isNotBlank()) models.add(name)
                i = endQuote + 1
            }
            models
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseChatResponse(body: String): ChatResponse {
        return try {
            val error = extractJsonString(body, "error")
            if (error != null) return ChatResponse(error = error)

            val messageStart = body.indexOf("\"message\"")
            val content = if (messageStart >= 0) {
                val contentIdx = body.indexOf("\"content\"", messageStart)
                if (contentIdx >= 0) extractJsonString(body.substring(contentIdx), "content") else null
            } else null

            val response = ChatResponse(
                message = if (content != null) ChatResponseMessage(content = content) else null,
                done = body.contains("\"done\":true")
            )
            if (response.message == null && response.error == null && body.isNotBlank()) {
                ChatResponse(error = "Failed to parse response")
            } else {
                response
            }
        } catch (_: Exception) {
            ChatResponse(error = "Failed to parse response")
        }
    }

    fun extractJsonString(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val idx = json.indexOf(keyPattern, ignoreCase = true)
        if (idx == -1) return null
        val colonIdx = json.indexOf(':', idx)
        if (colonIdx == -1) return null
        val startQuote = json.indexOf('"', colonIdx + 1)
        if (startQuote == -1) return null
        var end = startQuote + 1
        while (end < json.length) {
            val c = json[end]
            if (c == '\\') {
                end += 2
                continue
            }
            if (c == '"') break
            end++
        }
        return json.substring(startQuote + 1, end).replace("\\n", "\n").replace("\\\"", "\"")
    }
}
