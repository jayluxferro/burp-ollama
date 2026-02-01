package proactive

import burp.api.montoya.http.handler.HttpHandler
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.http.handler.HttpResponseReceived
import burp.api.montoya.http.handler.RequestToBeSentAction
import burp.api.montoya.http.handler.ResponseReceivedAction
import ui.OllamaSuggestionRegistry

/**
 * HTTP handler that detects interesting patterns and adds proactive suggestions.
 * Runs heuristics only (no AI calls) to avoid blocking traffic.
 */
class OllamaProactiveHandler(
    private val enabled: () -> Boolean
) : HttpHandler {

    private val loginPaths = setOf("login", "signin", "sign-in", "authenticate", "auth")
    private val registerPaths = setOf("register", "signup", "sign-up")
    private val passwordPaths = setOf("password", "reset", "forgot")
    private val apiPaths = setOf("/api/", "/graphql", "/rest/")

    override fun handleHttpRequestToBeSent(requestToBeSent: HttpRequestToBeSent): RequestToBeSentAction {
        if (!enabled()) return RequestToBeSentAction.continueWith(requestToBeSent)
        val req = requestToBeSent
        val path = req.path().lowercase()
        val url = "${req.httpService().host()}:${req.httpService().port()}${req.path()}"

        when {
            req.method() == "POST" && loginPaths.any { path.contains(it) } -> {
                OllamaSuggestionRegistry.addSuggestion(
                    "Login request detected",
                    "Generate a login sequence for this request. Describe the login flow (e.g. POST to /login with username/password form).",
                    url,
                    req.method()
                )
            }
            req.method() == "POST" && registerPaths.any { path.contains(it) } -> {
                OllamaSuggestionRegistry.addSuggestion(
                    "Registration request detected",
                    "Analyze this registration flow for security issues (e.g. weak validation, mass assignment).",
                    url,
                    req.method()
                )
            }
            req.method() == "POST" && passwordPaths.any { path.contains(it) } -> {
                OllamaSuggestionRegistry.addSuggestion(
                    "Password reset/change detected",
                    "Analyze this password flow for security issues (e.g. token leakage, weak reset logic).",
                    url,
                    req.method()
                )
            }
            apiPaths.any { path.contains(it) } && req.method() in setOf("POST", "PUT", "PATCH") -> {
                OllamaSuggestionRegistry.addSuggestion(
                    "API mutation detected",
                    "Analyze this API request for vulnerabilities (e.g. IDOR, injection, broken access control).",
                    url,
                    req.method()
                )
            }
        }

        return RequestToBeSentAction.continueWith(requestToBeSent)
    }

    override fun handleHttpResponseReceived(responseReceived: HttpResponseReceived): ResponseReceivedAction {
        if (!enabled()) return ResponseReceivedAction.continueWith(responseReceived)
        val resp = responseReceived
        val req = resp.initiatingRequest()
        val path = req.path().lowercase()
        val url = "${req.httpService().host()}:${req.httpService().port()}${req.path()}"

        when {
            resp.statusCode().toInt() == 200 && resp.cookies().isNotEmpty() && loginPaths.any { path.contains(it) } -> {
                OllamaSuggestionRegistry.addSuggestion(
                    "Session established (login response)",
                    "Analyze the session cookies and response for security implications.",
                    url,
                    req.method()
                )
            }
        }

        return ResponseReceivedAction.continueWith(responseReceived)
    }
}
