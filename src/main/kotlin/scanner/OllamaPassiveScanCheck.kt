package scanner

import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.AuditResult
import burp.api.montoya.scanner.scancheck.PassiveScanCheck

/**
 * Passive scan check registered as "Ollama". Currently a stub that always returns
 * empty results — no AI analysis is performed during passive scanning.
 *
 * This is a placeholder for future AI-powered passive analysis (e.g. lightweight
 * classification or false-positive detection with a short timeout).
 *
 * Registering this check adds overhead to every request/response; it is only
 * registered when the "passiveScanEnabled" config toggle (default: false) is
 * enabled in Settings > Experimental.
 */
class OllamaPassiveScanCheck : PassiveScanCheck {

    override fun checkName(): String = "Ollama"

    override fun doCheck(baseRequestResponse: HttpRequestResponse): AuditResult =
        AuditResult.auditResult()
}
