package ui

import burp.api.montoya.http.message.HttpRequestResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists conversation state per request so that:
 * - Request tab and Response tab share the same conversation (same item)
 * - Switching away and back to the same Proxy item restores the conversation
 */
object OllamaEditorConversationRegistry {

    data class State(
        val conversationHistory: MutableList<Pair<String, String>>,
        val responseText: String
    )

    private val store = ConcurrentHashMap<String, State>()
    private const val MAX_ENTRIES = 100

    private fun fingerprint(rr: HttpRequestResponse): String = try {
        val req = rr.request()
        val url = req.url().toString()
        val method = req.method()
        val bodyHash = java.util.Arrays.hashCode(req.toByteArray().getBytes())
        "$method|$url|$bodyHash"
    } catch (_: Exception) {
        System.identityHashCode(rr).toString()
    }

    fun getState(rr: HttpRequestResponse?): State? =
        rr?.let { store[fingerprint(it)] }

    fun putState(rr: HttpRequestResponse?, state: State) {
        rr ?: return
        evictIfNeeded()
        store[fingerprint(rr)] = state
    }

    private fun evictIfNeeded() {
        if (store.size >= MAX_ENTRIES) {
            val keys = store.keys.toList().take(MAX_ENTRIES / 4)
            keys.forEach { store.remove(it) }
        }
    }
}
