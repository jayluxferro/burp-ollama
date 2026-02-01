package ollama

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OllamaResponseParserTest {

    @Test
    fun `parseTagsResponse extracts model names`() {
        val body = """
            {"models":[{"name":"llama3.2:3b","modified_at":"2024-01-01"},{"name":"codellama","modified_at":"2024-01-02"}]}
        """.trimIndent()
        val result = OllamaResponseParser.parseTagsResponse(body)
        assertEquals(listOf("llama3.2:3b", "codellama"), result)
    }

    @Test
    fun `parseTagsResponse returns empty list for empty body`() {
        assertEquals(emptyList<String>(), OllamaResponseParser.parseTagsResponse(""))
    }

    @Test
    fun `parseTagsResponse returns empty list for invalid JSON`() {
        assertEquals(emptyList<String>(), OllamaResponseParser.parseTagsResponse("not json"))
    }

    @Test
    fun `parseTagsResponse handles single model`() {
        val body = """{"models":[{"name":"mistral"}]}"""
        assertEquals(listOf("mistral"), OllamaResponseParser.parseTagsResponse(body))
    }

    @Test
    fun `parseChatResponse extracts content from message`() {
        val body = """{"model":"llama3.2:3b","message":{"role":"assistant","content":"Hello world"},"done":true}"""
        val result = OllamaResponseParser.parseChatResponse(body)
        assertNull(result.error)
        assertEquals("Hello world", result.message?.content)
        assertTrue(result.done)
    }

    @Test
    fun `parseChatResponse extracts error`() {
        val body = """{"error":"model not found"}"""
        val result = OllamaResponseParser.parseChatResponse(body)
        assertEquals("model not found", result.error)
        assertNull(result.message)
    }

    @Test
    fun `parseChatResponse handles escaped content`() {
        val body = """{"message":{"content":"Line1\nLine2"},"done":true}"""
        val result = OllamaResponseParser.parseChatResponse(body)
        assertEquals("Line1\nLine2", result.message?.content)
    }

    @Test
    fun `parseChatResponse returns error for invalid JSON`() {
        val result = OllamaResponseParser.parseChatResponse("not json")
        assertEquals("Failed to parse response", result.error)
    }

    @Test
    fun `extractJsonString extracts simple value`() {
        val json = """{"key":"value"}"""
        assertEquals("value", OllamaResponseParser.extractJsonString(json, "key"))
    }

    @Test
    fun `extractJsonString returns null for missing key`() {
        val json = """{"other":"value"}"""
        assertNull(OllamaResponseParser.extractJsonString(json, "key"))
    }

    @Test
    fun `extractJsonString handles escaped quotes`() {
        val json = """{"key":"say \"hello\""}"""
        assertEquals("say \"hello\"", OllamaResponseParser.extractJsonString(json, "key"))
    }
}
