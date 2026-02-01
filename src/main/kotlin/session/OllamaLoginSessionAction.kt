package session

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.sessions.ActionResult
import burp.api.montoya.http.sessions.SessionHandlingAction
import burp.api.montoya.http.sessions.SessionHandlingActionData
import ollama.OllamaConfig

/**
 * Session handling action that performs login using a configurable request template.
 * Configure in Settings > Burp Ollama > Login sequence.
 * Add to Project > Settings > Session handling as "Run session handling action" > Ollama Login.
 */
class OllamaLoginSessionAction(
    private val api: MontoyaApi,
    private val config: OllamaConfig
) : SessionHandlingAction {

    override fun name(): String = "Ollama Login"

    override fun performAction(actionData: SessionHandlingActionData): ActionResult {
        val baseRequest = actionData.request()

        if (!config.loginEnabled || config.loginRequestTemplate.isBlank()) {
            return ActionResult.actionResult(baseRequest)
        }

        // Optional scope: if loginBaseUrl is set, only run for requests matching that base
        val baseUrl = config.loginBaseUrl.trim()
        if (baseUrl.isNotEmpty()) {
            val requestUrl = try { baseRequest.url() } catch (_: Exception) { return ActionResult.actionResult(baseRequest) }
            val normalizedBase = baseUrl.trimEnd('/')
            val normalizedRequest = requestUrl.toString().trimEnd('/')
            if (!normalizedRequest.startsWith(normalizedBase, ignoreCase = true)) {
                return ActionResult.actionResult(baseRequest)
            }
        }

        val template = config.loginRequestTemplate
            .replace("{{username}}", config.loginUsername)
            .replace("{{password}}", config.loginPassword)

        val loginRequest = try {
            HttpRequest.httpRequest(template)
        } catch (e: Exception) {
            api.logging().logToError("Ollama Login: Invalid request template: ${e.message}")
            return ActionResult.actionResult(baseRequest)
        }

        val response = try {
            api.http().sendRequest(loginRequest)
        } catch (e: Exception) {
            api.logging().logToError("Ollama Login: Failed to send login request: ${e.message}")
            return ActionResult.actionResult(baseRequest)
        }

        val httpResponse = response.response() ?: return ActionResult.actionResult(baseRequest)
        val cookieValue = httpResponse.cookies()
            .joinToString("; ") { "${it.name()}=${it.value()}" }
        if (cookieValue.isBlank()) {
            api.logging().logToOutput("Ollama Login: No Set-Cookie in login response")
            return ActionResult.actionResult(baseRequest)
        }

        val updatedRequest = if (baseRequest.hasHeader("Cookie")) {
            baseRequest.withUpdatedHeader("Cookie", cookieValue)
        } else {
            baseRequest.withAddedHeader("Cookie", cookieValue)
        }

        return ActionResult.actionResult(updatedRequest)
    }
}
