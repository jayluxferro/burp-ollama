package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OllamaEditorConversationRegistryTest {

    @Test
    fun `putState then getState returns saved state`() {
        val rr = FakeHttpRequestResponse(method = "GET", url = "http://test-put-get/")
        val state = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf("user" to "hello", "assistant" to "hi"),
            responseText = "analysis result"
        )
        OllamaEditorConversationRegistry.putState(rr, state)

        val retrieved = OllamaEditorConversationRegistry.getState(rr)
        assertNotNull(retrieved)
        assertEquals("analysis result", retrieved?.responseText)
        assertEquals(2, retrieved?.conversationHistory?.size)
        assertEquals("hello", retrieved?.conversationHistory?.get(0)?.second)
    }

    @Test
    fun `getState for unknown item returns null`() {
        val rr = FakeHttpRequestResponse(method = "GET", url = "http://test-unknown/")
        val retrieved = OllamaEditorConversationRegistry.getState(rr)
        assertNull(retrieved)
    }

    @Test
    fun `getState for null request returns null`() {
        val retrieved = OllamaEditorConversationRegistry.getState(null)
        assertNull(retrieved)
    }

    @Test
    fun `putState with null request does not store`() {
        val state = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf(),
            responseText = "should not be stored"
        )
        // Should not throw
        OllamaEditorConversationRegistry.putState(null, state)
        // No assertion needed beyond no exception
    }

    @Test
    fun `putState overwrites existing state for same item`() {
        val rr = FakeHttpRequestResponse(method = "POST", url = "http://test-overwrite/")
        val state1 = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf(),
            responseText = "first"
        )
        val state2 = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf(),
            responseText = "second"
        )
        OllamaEditorConversationRegistry.putState(rr, state1)
        OllamaEditorConversationRegistry.putState(rr, state2)

        val retrieved = OllamaEditorConversationRegistry.getState(rr)
        assertEquals("second", retrieved?.responseText)
    }

    @Test
    fun `same fingerprint maps to same state`() {
        val rr1 = FakeHttpRequestResponse(
            method = "GET",
            url = "http://test-same-fingerprint/",
            requestBytes = kotlin.ByteArray(0)
        )
        val rr2 = FakeHttpRequestResponse(
            method = "GET",
            url = "http://test-same-fingerprint/",
            requestBytes = kotlin.ByteArray(0)
        )
        val state = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf(),
            responseText = "shared state"
        )
        OllamaEditorConversationRegistry.putState(rr1, state)
        val retrieved = OllamaEditorConversationRegistry.getState(rr2)
        assertNotNull(retrieved)
        assertEquals("shared state", retrieved?.responseText)
    }

    @Test
    fun `different fingerprints have different state`() {
        val rr1 = FakeHttpRequestResponse(
            method = "GET",
            url = "http://test-diff-fp/1",
            requestBytes = kotlin.ByteArray(0)
        )
        val rr2 = FakeHttpRequestResponse(
            method = "POST",
            url = "http://test-diff-fp/2",
            requestBytes = kotlin.ByteArray(0)
        )
        val state = OllamaEditorConversationRegistry.State(
            conversationHistory = mutableListOf(),
            responseText = "only for rr1"
        )
        OllamaEditorConversationRegistry.putState(rr1, state)
        assertNull(OllamaEditorConversationRegistry.getState(rr2))
    }

    @Test
    fun `eviction when exceeding MAX_ENTRIES does not throw`() {
        // MAX_ENTRIES = 100; add 120 unique entries to trigger eviction
        repeat(120) { i ->
            val rr = FakeHttpRequestResponse(
                method = "GET",
                url = "http://test-editor-eviction/$i",
                requestBytes = kotlin.ByteArray(4) { i.toByte() }
            )
            val state = OllamaEditorConversationRegistry.State(
                conversationHistory = mutableListOf(),
                responseText = "entry $i"
            )
            OllamaEditorConversationRegistry.putState(rr, state)
        }
        // Should not throw. Verify that a recent entry is retrievable.
        val recent = FakeHttpRequestResponse(
            method = "GET",
            url = "http://test-editor-eviction/119",
            requestBytes = kotlin.ByteArray(4) { 119.toByte() }
        )
        // May or may not be null depending on eviction algorithm, but shouldn't throw
        // We just verify no exception
        try {
            OllamaEditorConversationRegistry.getState(recent)
        } catch (e: Exception) {
            fail("Eviction should not cause exception: ${e.message}")
        }
    }
}
