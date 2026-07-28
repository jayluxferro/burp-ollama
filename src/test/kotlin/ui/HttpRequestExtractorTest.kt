package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpRequestExtractorTest {

    @Test
    fun `extracting HTTP requests from markdown code blocks`() {
        val text = """
            Here is a sample request:
            ```
            GET /api/users HTTP/1.1
            Host: example.com
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("GET /api/users"))
    }

    @Test
    fun `extracting from code blocks with http language tag`() {
        val text = """
            Try this payload:
            ```http
            POST /login HTTP/1.1
            Host: example.com
            Content-Type: application/json

            {"user":"admin"}
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("POST /login"))
        assertTrue(requests[0].contains("{\"user\":\"admin\"}"))
    }

    @Test
    fun `extracting raw HTTP method lines without code blocks`() {
        val text = """
            You can send:
            GET /api/data HTTP/1.1
            Host: test.com

            That should work.
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("GET /api/data"))
    }

    @Test
    fun `empty input returns empty list`() {
        val requests = HttpRequestExtractor.extractRequests("")
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `text with no HTTP methods returns empty`() {
        val text = "This is just a plain text response with no HTTP requests in it."
        val requests = HttpRequestExtractor.extractRequests(text)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `multiple requests in one response`() {
        val text = """
            Here are two payloads:

            ```http
            GET /api/admin HTTP/1.1
            Host: target.com
            ```

            And another one:

            ```http
            POST /api/data HTTP/1.1
            Host: target.com
            Content-Length: 0
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("GET /api/admin"))
        assertTrue(requests[1].contains("POST /api/data"))
    }

    @Test
    fun `code blocks that are not HTTP should not extract`() {
        val text = """
            Here is some JSON:
            ```json
            {"key": "value"}
            ```
            And some Python:
            ```python
            def hello():
                print("hi")
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `raw non-HTTP lines without code blocks should not extract`() {
        val text = """
            Some text about security testing.
            No HTTP methods here, just analysis.
            Maybe some SQL: SELECT * FROM users;
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `mixed content extracts only HTTP code blocks not raw text`() {
        val text = """
            Analysis:
            The request GET /foo would be bad.

            ```http
            DELETE /admin/users HTTP/1.1
            Host: example.com
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("DELETE /admin/users"))
    }

    @Test
    fun `HEAD and OPTIONS methods are recognized in code blocks`() {
        val text = """
            ```http
            HEAD /status HTTP/1.1
            Host: example.com
            ```
            ```http
            OPTIONS /api HTTP/1.1
            Host: example.com
            ```
        """.trimIndent()
        val requests = HttpRequestExtractor.extractRequests(text)
        assertEquals(2, requests.size)
        assertTrue(requests.any { it.contains("HEAD /status") })
        assertTrue(requests.any { it.contains("OPTIONS /api") })
    }
}
