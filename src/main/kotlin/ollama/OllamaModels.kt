package ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ollama API request/response data classes.
 * See https://docs.ollama.com/api/chat
 */

// --- Chat API ---

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val options: ChatOptions? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatOptions(
    @SerialName("num_ctx") val numCtx: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("num_predict") val numPredict: Int? = null
)

@Serializable
data class ChatResponse(
    val model: String? = null,
    val message: ChatResponseMessage? = null,
    val done: Boolean = false,
    val error: String? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("eval_count") val evalCount: Int? = null
)

/** Result of a chat call with optional token usage */
data class ChatResult(val content: String, val promptTokens: Int? = null, val evalTokens: Int? = null)

@Serializable
data class ChatResponseMessage(
    val role: String? = null,
    val content: String? = null
)

// --- Tags API (list models) ---

@Serializable
data class TagsResponse(
    val models: List<ModelInfo> = emptyList()
)

@Serializable
data class ModelInfo(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String? = null,
    val size: Long? = null
)
