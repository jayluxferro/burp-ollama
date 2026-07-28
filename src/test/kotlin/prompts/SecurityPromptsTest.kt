package prompts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecurityPromptsTest {

    @Test
    fun `default prompts are non-empty`() {
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_SELECTION.isNotBlank())
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_HEADERS.isNotBlank())
        assertTrue(SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY.isNotBlank())
        assertTrue(SecurityPrompts.DEFAULT_VALIDATE_FALSE_POSITIVE.isNotBlank())
        assertTrue(SecurityPrompts.DEFAULT_DECIPHER_CODE.isNotBlank())
    }

    @Test
    fun `default system prompt and task prompts define security agent`() {
        val base = SecurityPrompts.DEFAULT_SYSTEM_PROMPT
        assertTrue(base.contains("security", ignoreCase = true))
        assertTrue(base.contains("researcher", ignoreCase = true))
        assertTrue(base.contains("engineer", ignoreCase = true))
        assertTrue(base.contains("hacker", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_SELECTION.contains("security", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY.contains("security", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_SELECTION.contains("explain", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY.contains("analyze", ignoreCase = true))
    }
}
