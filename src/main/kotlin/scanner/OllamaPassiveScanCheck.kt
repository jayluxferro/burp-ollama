package scanner

import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.AuditResult
import burp.api.montoya.scanner.scancheck.PassiveScanCheck

/**
 * Passive scan check registered as "Ollama". Currently returns no issues;
 * can be extended to use local Ollama for FP/classification or lightweight
 * analysis (with short timeout to avoid blocking scans).
 */
class OllamaPassiveScanCheck : PassiveScanCheck {

    override fun checkName(): String = "Ollama"

    override fun doCheck(baseRequestResponse: HttpRequestResponse): AuditResult =
        AuditResult.auditResult()
}
