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
    fun `default prompts contain security context`() {
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_SELECTION.contains("security", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_EXPLAIN_HEADERS.contains("headers", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY.contains("penetration", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_VALIDATE_FALSE_POSITIVE.contains("false positive", ignoreCase = true))
        assertTrue(SecurityPrompts.DEFAULT_DECIPHER_CODE.contains("security", ignoreCase = true))
    }
}
