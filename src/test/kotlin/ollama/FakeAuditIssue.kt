package ollama

import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity

/**
 * Mock AuditIssue for testing outside Burp runtime.
 */
class FakeAuditIssue(
    private val name: String,
    private val detail: String?,
    private val remediation: String?,
    private val baseUrl: String,
    private val severity: AuditIssueSeverity,
    private val confidence: AuditIssueConfidence,
    private val requestResponses: List<HttpRequestResponse> = emptyList()
) : AuditIssue {

    override fun name(): String = name
    override fun detail(): String? = detail
    override fun remediation(): String? = remediation
    override fun baseUrl(): String = baseUrl
    override fun severity(): AuditIssueSeverity = severity
    override fun confidence(): AuditIssueConfidence = confidence
    override fun requestResponses(): List<HttpRequestResponse> = requestResponses
    override fun httpService() = throw UnsupportedOperationException("Not needed for formatter test")
    override fun collaboratorInteractions() = throw UnsupportedOperationException("Not needed for formatter test")
    override fun definition() = throw UnsupportedOperationException("Not needed for formatter test")
}
