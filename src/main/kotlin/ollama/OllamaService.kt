package ollama

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.requests.HttpRequest
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JdkHttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HTTP client for Ollama API.
 * Supports sync and streaming chat, list models, health check.
 * When useBurpHttpApi is true, routes requests through Burp's HTTP API.
 * Uses a dedicated executor for async work; call shutdown() when extension unloads.
 */
class OllamaService(
    private var baseUrl: String = DEFAULT_BASE_URL,
    private var timeoutSeconds: Int = DEFAULT_TIMEOUT,
    private var montoyaApi: MontoyaApi? = null,
    private var useBurpHttpApi: Boolean = false,
    private var temperature: Double? = null,
    private var topP: Double? = null,
    private var numPredict: Int? = null
) {
    private var executor: ExecutorService = Executors.newFixedThreadPool(4)
    private var client: HttpClient = buildClient()
    private val json get() = OllamaResponseParser.json

    private fun buildClient(): HttpClient {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds.toLong()))
            .build()
    }

    fun updateConfig(
        baseUrl: String,
        timeoutSeconds: Int,
        useBurpHttpApi: Boolean = false,
        temperature: Double? = null,
        topP: Double? = null,
        numPredict: Int? = null
    ) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.timeoutSeconds = timeoutSeconds
        this.useBurpHttpApi = useBurpHttpApi
        this.temperature = temperature
        this.topP = topP
        this.numPredict = numPredict
        this.client = buildClient()
    }

    fun setMontoyaApi(api: MontoyaApi?) {
        this.montoyaApi = api
    }

    private fun hostHeader(): String {
        val uri = URI.create(baseUrl)
        return if (uri.port in listOf(80, 443, -1)) uri.host else "${uri.host}:${uri.port}"
    }

    private fun sendViaBurp(path: String, body: String?): Result<Pair<Int, String>> {
        val api = montoyaApi ?: return Result.failure(OllamaException("Burp API not available"))
        return try {
            val host = hostHeader()
            val requestStr = if (body != null) {
                val contentLength = body.toByteArray(Charsets.UTF_8).size
                "POST $path HTTP/1.1\r\nHost: $host\r\nContent-Type: application/json\r\nContent-Length: $contentLength\r\n\r\n$body"
            } else {
                "GET $path HTTP/1.1\r\nHost: $host\r\n\r\n"
            }
            val service = HttpService.httpService(baseUrl)
            val burpRequest = HttpRequest.httpRequest(service, requestStr)
            val response = api.http().sendRequest(burpRequest)
            val httpResponse = response.response() ?: return Result.failure(OllamaException("No response from Burp"))
            val statusCode = httpResponse.statusCode().toInt()
            val bodyStr = httpResponse.bodyToString()
            Result.success(statusCode to bodyStr)
        } catch (e: Exception) {
            Result.failure(OllamaException("Burp HTTP request failed: ${e.message}", e))
        }
    }

    /**
     * Check if Ollama is reachable.
     */
    fun healthCheck(): Boolean {
        return when {
            useBurpHttpApi -> sendViaBurp("/api/tags", null).fold(
                onSuccess = { (code, _) -> code in 200..299 },
                onFailure = { false }
            )
            else -> try {
                val req = JdkHttpRequest.newBuilder()
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
    }

    /**
     * List available models from Ollama.
     */
    fun listModels(): Result<List<String>> {
        return when {
            useBurpHttpApi -> sendViaBurp("/api/tags", null).fold(
                onSuccess = { (code, body) ->
                    if (code !in 200..299) Result.failure(OllamaException("Ollama returned $code: $body"))
                    else Result.success(OllamaResponseParser.parseTagsResponse(body))
                },
                onFailure = { Result.failure(it) }
            )
            else -> try {
                val req = JdkHttpRequest.newBuilder()
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
    }

    private fun buildOptions(numCtx: Int? = null): ChatOptions? {
        val hasCtx = numCtx != null
        val hasTemperature = temperature != null
        val hasTopP = topP != null
        val hasNumPredict = numPredict != null
        return if (hasCtx || hasTemperature || hasTopP || hasNumPredict) {
            ChatOptions(
                numCtx = numCtx,
                temperature = temperature,
                topP = topP,
                numPredict = numPredict
            )
        } else null
    }

    /**
     * Send chat request (sync, non-streaming).
     */
    fun chat(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): Result<ChatResult> {
        return try {
            val messages = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messages.add(ChatMessage(role = "user", content = userMessage))

            val options = buildOptions(numCtx)
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = false,
                options = options
            )

            val body = json.encodeToString(requestBody)
            val (statusCode, responseBody) = when {
                useBurpHttpApi -> sendViaBurp("/api/chat", body).getOrElse { return Result.failure(it) }
                else -> {
                    val req = JdkHttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/chat"))
                        .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                        .header("Content-Type", "application/json")
                        .POST(JdkHttpRequest.BodyPublishers.ofString(body))
                        .build()
                    val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() !in 200..299) {
                        return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: ${resp.body()}"))
                    }
                    resp.statusCode() to resp.body()
                }
            }
            if (statusCode !in 200..299) {
                return Result.failure(OllamaException("Ollama returned $statusCode: $responseBody"))
            }
            val chatResp = OllamaResponseParser.parseChatResponse(responseBody)
            val content = chatResp.message?.content
            when {
                chatResp.error != null -> Result.failure(OllamaException(chatResp.error))
                content != null -> Result.success(
                    ChatResult(
                        content = content,
                        promptTokens = chatResp.promptEvalCount,
                        evalTokens = chatResp.evalCount
                    )
                )
                else -> Result.failure(OllamaException("Empty response from Ollama"))
            }
        } catch (e: Exception) {
            Result.failure(OllamaException("Chat failed: ${e.message}", e))
        }
    }

    /**
     * Chain two models: A generates, B refines.
     * Returns refined output from model B, or failure if either step fails.
     */
    fun chatChained(
        modelA: String,
        modelB: String,
        refinePrompt: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): Result<ChatResult> {
        val step1 = chat(modelA, systemPrompt, userMessage, numCtx)
        val outputA = step1.getOrNull()?.content ?: return step1
        return chat(modelB, refinePrompt, outputA, numCtx)
    }

    /**
     * Async chain execution. Use for UI-triggered work.
     */
    fun chatChainedAsync(
        modelA: String,
        modelB: String,
        refinePrompt: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): CompletableFuture<Result<ChatResult>> {
        return CompletableFuture.supplyAsync(
            { chatChained(modelA, modelB, refinePrompt, systemPrompt, userMessage, numCtx) },
            executor
        )
    }

    /**
     * Run a task on the background executor. Use for UI-triggered async work.
     */
    fun execute(task: () -> Unit) {
        executor.execute(task)
    }

    /**
     * Run a task on the background executor with error handling.
     * Exceptions are logged and optionally passed to an onError callback.
     */
    fun executeSafe(task: () -> Unit, onError: ((Throwable) -> Unit)? = null) {
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                onError?.invoke(e)
                montoyaApi?.logging()?.logToError("Ollama background task failed: ${e.message}")
            }
        }
    }

    /**
     * Cancel all running requests and reset the executor.
     */
    fun cancelRunningRequests() {
        executor.shutdownNow()
        executor = Executors.newFixedThreadPool(4)
    }

    /**
     * Shutdown the executor. Call when extension unloads (BApp Store requirement).
     */
    fun shutdown() {
        executor.shutdownNow()
    }

    /**
     * Send chat request asynchronously (for background execution).
     */
    fun chatAsync(
        model: String,
        systemPrompt: String,
        userMessage: String,
        numCtx: Int? = null
    ): CompletableFuture<Result<ChatResult>> {
        return CompletableFuture.supplyAsync({ chat(model, systemPrompt, userMessage, numCtx) }, executor)
    }

    /**
     * Send streaming chat request. Invokes onChunk for each content piece.
     * onChunk may be called from a background thread; caller should use SwingUtilities.invokeLater for UI updates.
     *
     * When [useBurpHttpApi] is enabled, true streaming is not supported by the Burp HTTP API —
     * the entire response is buffered first, then parsed line-by-line. A warning is logged.
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

            val options = buildOptions(numCtx)
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                options = options
            )

            val body = json.encodeToString(requestBody)
            when {
                useBurpHttpApi -> {
                    montoyaApi?.logging()?.logToOutput(
                        "Ollama: streaming is not supported via Burp HTTP API; falling back to full response buffering"
                    )
                    val result = sendViaBurp("/api/chat", body)
                    val (code, responseBody) = result.getOrElse { return Result.failure(it) }
                    if (code !in 200..299) return Result.failure(OllamaException("Ollama returned $code: $responseBody"))
                    responseBody.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) return@forEach
                        val chatResp = json.decodeFromString<ChatResponse>(trimmed)
                        if (chatResp.error != null) return Result.failure(OllamaException(chatResp.error))
                        val content = chatResp.message?.content
                        if (!content.isNullOrEmpty()) onChunk(content)
                    }
                    Result.success(Unit)
                }
                else -> {
                    val req = JdkHttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/chat"))
                        .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                        .header("Content-Type", "application/json")
                        .POST(JdkHttpRequest.BodyPublishers.ofString(body))
                        .build()
                    val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
                    if (resp.statusCode() !in 200..299) {
                        val errorBody = resp.body().reader().readText()
                        return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: $errorBody"))
                    }
                    BufferedReader(resp.body().reader()).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.isEmpty()) continue
                            val chatResp = json.decodeFromString<ChatResponse>(trimmed)
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
                }
            }
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
        return CompletableFuture.supplyAsync(
            { chatStream(model, systemPrompt, userMessage, numCtx, onChunk) },
            executor
        )
    }

    /**
     * Send chat with full message list (for multi-turn conversation history).
     */
    fun chatWithMessages(
        model: String,
        messages: List<ChatMessage>,
        numCtx: Int? = null
    ): Result<String> {
        if (messages.isEmpty()) return Result.failure(OllamaException("No messages provided"))
        return try {
            val options = buildOptions(numCtx)
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = false,
                options = options
            )
            val body = json.encodeToString(requestBody)
            val (statusCode, responseBody) = when {
                useBurpHttpApi -> sendViaBurp("/api/chat", body).getOrElse { return Result.failure(it) }
                else -> {
                    val req = JdkHttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/chat"))
                        .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                        .header("Content-Type", "application/json")
                        .POST(JdkHttpRequest.BodyPublishers.ofString(body))
                        .build()
                    val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() !in 200..299) {
                        return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: ${resp.body()}"))
                    }
                    resp.statusCode() to resp.body()
                }
            }
            if (statusCode !in 200..299) {
                return Result.failure(OllamaException("Ollama returned $statusCode: $responseBody"))
            }
            val chatResp = OllamaResponseParser.parseChatResponse(responseBody)
            val content = chatResp.message?.content
            when {
                chatResp.error != null -> Result.failure(OllamaException(chatResp.error))
                content != null -> Result.success(content)
                else -> Result.failure(OllamaException("Empty response from Ollama"))
            }
        } catch (e: Exception) {
            Result.failure(OllamaException("Chat failed: ${e.message}", e))
        }
    }

    /**
     * Send streaming chat with full message list (for multi-turn conversation history).
     */
    fun chatStreamWithMessages(
        model: String,
        messages: List<ChatMessage>,
        numCtx: Int? = null,
        onChunk: (String) -> Unit
    ): Result<Unit> {
        if (messages.isEmpty()) return Result.failure(OllamaException("No messages provided"))
        return try {
            val options = buildOptions(numCtx)
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                options = options
            )
            val body = json.encodeToString(requestBody)
            when {
                useBurpHttpApi -> {
                    montoyaApi?.logging()?.logToOutput(
                        "Ollama: streaming is not supported via Burp HTTP API; falling back to full response buffering"
                    )
                    val result = sendViaBurp("/api/chat", body)
                    val (code, responseBody) = result.getOrElse { return Result.failure(it) }
                    if (code !in 200..299) return Result.failure(OllamaException("Ollama returned $code: $responseBody"))
                    responseBody.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) return@forEach
                        val chatResp = json.decodeFromString<ChatResponse>(trimmed)
                        if (chatResp.error != null) return Result.failure(OllamaException(chatResp.error))
                        val content = chatResp.message?.content
                        if (!content.isNullOrEmpty()) onChunk(content)
                    }
                    Result.success(Unit)
                }
                else -> {
                    val req = JdkHttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/chat"))
                        .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                        .header("Content-Type", "application/json")
                        .POST(JdkHttpRequest.BodyPublishers.ofString(body))
                        .build()
                    val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
                    if (resp.statusCode() !in 200..299) {
                        val errorBody = resp.body().reader().readText()
                        return Result.failure(OllamaException("Ollama returned ${resp.statusCode()}: $errorBody"))
                    }
                    BufferedReader(resp.body().reader()).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.isEmpty()) continue
                            val chatResp = json.decodeFromString<ChatResponse>(trimmed)
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
                }
            }
        } catch (e: Exception) {
            Result.failure(OllamaException("Streaming chat failed: ${e.message}", e))
        }
    }

    fun chatWithMessagesAsync(
        model: String,
        messages: List<ChatMessage>,
        numCtx: Int? = null
    ): CompletableFuture<Result<String>> =
        CompletableFuture.supplyAsync({ chatWithMessages(model, messages, numCtx) }, executor)

    fun chatStreamWithMessagesAsync(
        model: String,
        messages: List<ChatMessage>,
        numCtx: Int? = null,
        onChunk: (String) -> Unit
    ): CompletableFuture<Result<Unit>> =
        CompletableFuture.supplyAsync({ chatStreamWithMessages(model, messages, numCtx, onChunk) }, executor)

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_TIMEOUT = 120
    }
}

class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
