package ollama

/**
 * Ollama API request/response data classes.
 * See https://docs.ollama.com/api/chat
 */

// --- Chat API ---

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val options: ChatOptions? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatOptions(
    val num_ctx: Int? = null
)

data class ChatResponse(
    val model: String? = null,
    val message: ChatResponseMessage? = null,
    val done: Boolean = false,
    val error: String? = null,
    val promptEvalCount: Int? = null,
    val evalCount: Int? = null
)

/** Result of a chat call with optional token usage */
data class ChatResult(val content: String, val promptTokens: Int? = null, val evalTokens: Int? = null)

data class ChatResponseMessage(
    val role: String? = null,
    val content: String? = null
)

// --- Tags API (list models) ---

data class TagsResponse(
    val models: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val name: String,
    val modified_at: String? = null,
    val size: Long? = null
)
