package ui

import burp.api.montoya.http.message.HttpRequestResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Registry of HttpRequestResponse items that have been analyzed by Ollama.
 * Used to show "✓ Analyzed by Ollama" in context menu when item was previously analyzed.
 * Montoya API does not support custom columns in Proxy/Site map, so we show the indicator in context menu.
 */
object OllamaAnalyzedItemsRegistry {

    private val fingerprints = ConcurrentHashMap.newKeySet<String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private const val MAX_ENTRIES = 500

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() {
        SwingUtilities.invokeLater { listeners.forEach { it() } }
    }

    /**
     * Creates a fingerprint for an HttpRequestResponse (method + URL).
     */
    fun fingerprint(rr: HttpRequestResponse): String {
        val req = rr.request()
        val method = req.method()
        val url = req.url().toString()
        return "$method $url"
    }

    fun markAnalyzed(rr: HttpRequestResponse) {
        evictIfNeeded()
        fingerprints.add(fingerprint(rr))
        notifyListeners()
    }

    fun markAnalyzed(items: List<HttpRequestResponse>) {
        evictIfNeeded()
        items.forEach { fingerprints.add(fingerprint(it)) }
        if (items.isNotEmpty()) notifyListeners()
    }

    fun wasAnalyzed(rr: HttpRequestResponse): Boolean =
        fingerprints.contains(fingerprint(rr))

    fun anyAnalyzed(items: List<HttpRequestResponse>): Boolean =
        items.any { wasAnalyzed(it) }

    fun allFingerprints(): List<String> = fingerprints.toList().sorted()

    private fun evictIfNeeded() {
        if (fingerprints.size >= MAX_ENTRIES) {
            val toRemove = fingerprints.toList().take(MAX_ENTRIES / 4)
            toRemove.forEach { fingerprints.remove(it) }
        }
    }
}
