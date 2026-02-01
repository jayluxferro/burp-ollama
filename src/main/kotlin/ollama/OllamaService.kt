package ollama

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * HTTP client for Ollama API.
 * Supports sync and streaming chat, list models, health check.
 */
class OllamaService(
    private var baseUrl: String = DEFAULT_BASE_URL,
    private var timeoutSeconds: Int = DEFAULT_TIMEOUT
) {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    fun updateConfig(baseUrl: String, timeoutSeconds: Int) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.timeoutSeconds = timeoutSeconds
    }

    /**
     * Check if Ollama is reachable.
     */
    fun healthCheck(): Boolean {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/tags"))
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            resp.statusCode() in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * List available models from Ollama.
     */
    fun listModels(): Result<List<String>> {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/tags"))
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: ${resp.body()}"))
            }
            val body = resp.body()
            val tags = OllamaResponseParser.parseTagsResponse(body)
            Result.success(tags)
        } catch (e: Exception) {
            Result.failure(OllamaException("Failed to list models: ${e.message}", e))
        }
    }

    /**
     * Send chat request (sync, non-streaming).
     */
    fun chat(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): Result<String> {
        return try {
            val messages = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messages.add(ChatMessage(role = "user", content = userMessage))

            val options = numCtx?.let { ChatOptions(num_ctx = it) }
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = false,
                options = options
            )

            val json = toJson(requestBody)
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/chat"))
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()

            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: ${resp.body()}"))
            }

            val chatResp = OllamaResponseParser.parseChatResponse(resp.body())
            val content = chatResp.message?.content
            if (chatResp.error != null) {
                Result.failure(OllamaException(chatResp.error))
            } else if (content != null) {
                Result.success(content)
            } else {
                Result.failure(OllamaException("Empty response from Ollama"))
            }
        } catch (e: Exception) {
            Result.failure(OllamaException("Chat failed: ${e.message}", e))
        }
    }

    /**
     * Send chat request asynchronously (for background execution).
     */
    fun chatAsync(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): CompletableFuture<Result<String>> {
        return CompletableFuture.supplyAsync {
            chat(model, systemPrompt, userMessage, numCtx)
        }
    }

    /**
     * Send streaming chat request. Invokes onChunk for each content piece.
     * onChunk may be called from a background thread; caller should use SwingUtilities.invokeLater for UI updates.
     */
    fun chatStream(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null,
        onChunk: (String) -> Unit
    ): Result<Unit> {
        return try {
            val messages = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messages.add(ChatMessage(role = "user", content = userMessage))

            val options = numCtx?.let { ChatOptions(num_ctx = it) }
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                options = options
            )

            val json = toJson(requestBody)
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/chat"))
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()

            val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
            if (resp.statusCode() !in 200..299) {
                val body = resp.body().reader().readText()
                return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: $body"))
            }

            BufferedReader(InputStreamReader(resp.body())).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty()) continue
                    val chatResp = OllamaResponseParser.parseChatResponse(trimmed)
                    if (chatResp.error != null) {
                        return Result.failure(OllamaException(chatResp.error))
                    }
                    val content = chatResp.message?.content
                    if (!content.isNullOrEmpty()) {
                        onChunk(content)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(OllamaException("Streaming chat failed: ${e.message}", e))
        }
    }

    /**
     * Send streaming chat request asynchronously.
     */
    fun chatStreamAsync(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null,
        onChunk: (String) -> Unit
    ): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            chatStream(model, systemPrompt, userMessage, numCtx, onChunk)
        }
    }

    private fun toJson(req: ChatRequest): String {
        val messagesJson = req.messages.joinToString(",") { msg ->
            """{"role":"${escapeJson(msg.role)}","content":"${escapeJson(msg.content)}"}"""
        }
        val optionsJson = req.options?.let { ""","options":{"num_ctx":${it.num_ctx}}""" } ?: ""
        return """{"model":"${escapeJson(req.model)}","messages":[$messagesJson],"stream":${req.stream}$optionsJson}"""
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_TIMEOUT = 120
    }
}

class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
