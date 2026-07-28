package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OllamaSuggestionRegistryTest {

    @BeforeEach
    fun setUp() {
        OllamaSuggestionRegistry.clear()
    }

    @Test
    fun `addSuggestion returns true for new suggestion`() {
        val result = OllamaSuggestionRegistry.addSuggestion(
            description = "Test suggestion",
            suggestedPrompt = "Analyze this",
            url = "http://test-registry-suggestion/1",
            method = "GET"
        )
        assertTrue(result)
    }

    @Test
    fun `deduplication returns false for duplicate within window`() {
        val result1 = OllamaSuggestionRegistry.addSuggestion(
            description = "dedup suggestion",
            suggestedPrompt = "Check auth",
            url = "http://test-dedup/",
            method = "POST"
        )
        assertTrue(result1)

        val result2 = OllamaSuggestionRegistry.addSuggestion(
            description = "dedup suggestion",
            suggestedPrompt = "Check auth",
            url = "http://test-dedup/",
            method = "POST"
        )
        assertFalse(result2, "Duplicate within dedup window should return false")
    }

    @Test
    fun `same url different method is not a duplicate`() {
        val result1 = OllamaSuggestionRegistry.addSuggestion(
            description = "test",
            suggestedPrompt = "prompt",
            url = "http://test-url-method/",
            method = "GET"
        )
        assertTrue(result1)

        val result2 = OllamaSuggestionRegistry.addSuggestion(
            description = "test",
            suggestedPrompt = "prompt",
            url = "http://test-url-method/",
            method = "POST"
        )
        assertTrue(result2, "Different method should not be deduplicated")
    }

    @Test
    fun `allSuggestions returns list`() {
        OllamaSuggestionRegistry.addSuggestion(
            description = "Suggestion A",
            suggestedPrompt = "Prompt A",
            url = "http://test-list-all/1",
            method = "GET"
        )
        OllamaSuggestionRegistry.addSuggestion(
            description = "Suggestion B",
            suggestedPrompt = "Prompt B",
            url = "http://test-list-all/2",
            method = "POST"
        )
        val suggestions = OllamaSuggestionRegistry.allSuggestions()
        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.description == "Suggestion A" })
        assertTrue(suggestions.any { it.description == "Suggestion B" })
    }

    @Test
    fun `clear removes all suggestions`() {
        OllamaSuggestionRegistry.addSuggestion(
            description = "To be cleared",
            suggestedPrompt = "prompt",
            url = "http://test-clear/",
            method = "GET"
        )
        assertFalse(OllamaSuggestionRegistry.allSuggestions().isEmpty())
        OllamaSuggestionRegistry.clear()
        assertTrue(OllamaSuggestionRegistry.allSuggestions().isEmpty())
    }

    @Test
    fun `listener notification`() {
        val latch = CountDownLatch(1)
        val listener = { latch.countDown() }
        try {
            OllamaSuggestionRegistry.addListener(listener)
            OllamaSuggestionRegistry.addSuggestion(
                description = "listener test",
                suggestedPrompt = "prompt",
                url = "http://test-listener/",
                method = "GET"
            )
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Listener should be notified")
        } finally {
            OllamaSuggestionRegistry.removeListener(listener)
        }
    }

    @Test
    fun `listener notified on clear`() {
        OllamaSuggestionRegistry.addSuggestion(
            description = "pre-clear",
            suggestedPrompt = "prompt",
            url = "http://test-clear-listener/",
            method = "GET"
        )
        val latch = CountDownLatch(1)
        val listener = { latch.countDown() }
        try {
            OllamaSuggestionRegistry.addListener(listener)
            OllamaSuggestionRegistry.clear()
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Listener should be notified on clear")
        } finally {
            OllamaSuggestionRegistry.removeListener(listener)
        }
    }
}
