package ollama

import burp.api.montoya.scanner.audit.issues.AuditIssue

/**
 * Formats Scanner audit issues for AI analysis.
 * Extracted for testability.
 */
object OllamaAuditIssueFormatter {

    fun format(issue: AuditIssue): String {
        val sb = StringBuilder()
        sb.appendLine("## Scanner Finding: ${issue.name()}")
        sb.appendLine("**Severity:** ${issue.severity()}")
        sb.appendLine("**Confidence:** ${issue.confidence()}")
        sb.appendLine("**URL:** ${issue.baseUrl()}")
        issue.detail()?.let { sb.appendLine("**Detail:** $it") }
        issue.remediation()?.let { sb.appendLine("**Remediation:** $it") }
        val rrs = issue.requestResponses()
        if (rrs.isNotEmpty()) {
            sb.appendLine("\n**Request/Response:**")
            val rr = rrs.first()
            sb.appendLine("Request:\n${rr.request().toString().take(2000)}")
            rr.response()?.let { sb.appendLine("\nResponse:\n${it.toString().take(2000)}") }
        }
        return sb.toString()
    }
}
