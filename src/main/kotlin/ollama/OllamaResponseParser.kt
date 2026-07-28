package ollama

import kotlinx.serialization.json.Json

/**
 * Parses Ollama API responses using kotlinx.serialization.
 * Provides a shared Json instance used by both this parser and [OllamaService].
 */
object OllamaResponseParser {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseTagsResponse(body: String): List<String> {
        return try {
            val tagsResponse = json.decodeFromString<TagsResponse>(body)
            tagsResponse.models.map { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseChatResponse(body: String): ChatResponse {
        return try {
            json.decodeFromString<ChatResponse>(body)
        } catch (_: Exception) {
            ChatResponse(error = "Failed to parse response")
        }
    }
}
