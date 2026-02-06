package scanner

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scanner.audit.AuditIssueHandler
import burp.api.montoya.scanner.audit.issues.AuditIssue
import ollama.OllamaAuditIssueFormatter

/**
 * Invoked when the Scanner reports a new audit issue. Logs a one-line summary and
 * can be extended to offer "Analyze with Ollama" or "Suggest false positive" actions.
 */
class OllamaAuditIssueHandler(
    private val api: MontoyaApi
) : AuditIssueHandler {

    override fun handleNewAuditIssue(auditIssue: AuditIssue) {
        val summary = OllamaAuditIssueFormatter.summary(auditIssue)
        api.logging().logToOutput("Ollama: New scan issue – $summary")
    }
}
