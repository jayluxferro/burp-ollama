package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OllamaAnalyzedItemsRegistryTest {

    @BeforeEach
    fun setUp() {
        // The registry stores fingerprints in a set; no public clear.
        // We work with the existing state by using unique test URLs per test method.
    }

    @Test
    fun `markAnalyzed then wasAnalyzed returns true`() {
        val rr = FakeHttpRequestResponse(method = "GET", url = "http://test-mark-analyzed/")
        assertFalse(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr))

        OllamaAnalyzedItemsRegistry.markAnalyzed(rr)
        assertTrue(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr))
    }

    @Test
    fun `unmarked item returns wasAnalyzed false`() {
        val rr = FakeHttpRequestResponse(method = "POST", url = "http://test-unmarked/")
        assertFalse(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr))
    }

    @Test
    fun `markAnalyzed with list`() {
        val rr1 = FakeHttpRequestResponse(method = "GET", url = "http://test-mark-list/1")
        val rr2 = FakeHttpRequestResponse(method = "POST", url = "http://test-mark-list/2")
        OllamaAnalyzedItemsRegistry.markAnalyzed(listOf(rr1, rr2))

        assertTrue(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr1))
        assertTrue(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr2))
    }

    @Test
    fun `anyAnalyzed returns true when at least one item is analyzed`() {
        val rr1 = FakeHttpRequestResponse(method = "GET", url = "http://test-any-analyzed/1")
        val rr2 = FakeHttpRequestResponse(method = "POST", url = "http://test-any-analyzed/2")
        OllamaAnalyzedItemsRegistry.markAnalyzed(rr1)

        assertTrue(OllamaAnalyzedItemsRegistry.anyAnalyzed(listOf(rr1, rr2)))
    }

    @Test
    fun `anyAnalyzed returns false when no items are analyzed`() {
        val rr1 = FakeHttpRequestResponse(method = "GET", url = "http://test-none-analyzed/1")
        val rr2 = FakeHttpRequestResponse(method = "POST", url = "http://test-none-analyzed/2")
        assertFalse(OllamaAnalyzedItemsRegistry.anyAnalyzed(listOf(rr1, rr2)))
    }

    @Test
    fun `fingerprint is based on method and URL not response`() {
        val rr1 = FakeHttpRequestResponse(method = "GET", url = "http://test-fingerprint/")
        val rr2 = FakeHttpRequestResponse(method = "GET", url = "http://test-fingerprint/")
        // Even though they're different instances, same method+url → same fingerprint
        OllamaAnalyzedItemsRegistry.markAnalyzed(rr1)
        assertTrue(OllamaAnalyzedItemsRegistry.wasAnalyzed(rr2))
    }

    @Test
    fun `eviction when exceeding MAX_ENTRIES does not throw`() {
        // Add many unique entries to trigger eviction (MAX_ENTRIES = 500)
        repeat(520) { i ->
            val rr = FakeHttpRequestResponse(
                method = "GET",
                url = "http://test-eviction/$i"
            )
            OllamaAnalyzedItemsRegistry.markAnalyzed(rr)
        }
        // Should not throw and should still contain recent entries
        val recent = FakeHttpRequestResponse(method = "GET", url = "http://test-eviction/519")
        assertTrue(OllamaAnalyzedItemsRegistry.wasAnalyzed(recent))
    }
}
