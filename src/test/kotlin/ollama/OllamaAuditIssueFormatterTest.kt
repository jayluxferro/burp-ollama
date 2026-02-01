package ollama

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OllamaAuditIssueFormatterTest {

    @Test
    fun `format includes issue name severity and confidence`() {
        val issue = FakeAuditIssue(
            name = "SQL injection",
            detail = "Parameter id is vulnerable",
            remediation = "Use parameterized queries",
            baseUrl = "https://example.com/api",
            severity = AuditIssueSeverity.HIGH,
            confidence = AuditIssueConfidence.CERTAIN
        )
        val result = OllamaAuditIssueFormatter.format(issue)
        assertTrue(result.contains("SQL injection"))
        assertTrue(result.contains("HIGH"))
        assertTrue(result.contains("CERTAIN"))
        assertTrue(result.contains("https://example.com/api"))
        assertTrue(result.contains("Parameter id is vulnerable"))
        assertTrue(result.contains("Use parameterized queries"))
    }

    @Test
    fun `format handles null detail and remediation`() {
        val issue = FakeAuditIssue(
            name = "XSS",
            detail = null,
            remediation = null,
            baseUrl = "https://test.com",
            severity = AuditIssueSeverity.MEDIUM,
            confidence = AuditIssueConfidence.FIRM
        )
        val result = OllamaAuditIssueFormatter.format(issue)
        assertTrue(result.contains("XSS"))
        assertTrue(result.contains("MEDIUM"))
        assertTrue(result.contains("https://test.com"))
    }
}
