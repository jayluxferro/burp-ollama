package ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Registry for proactive AI suggestions.
 * Suggestions are added when heuristics detect interesting patterns (e.g. login, auth).
 */
object OllamaSuggestionRegistry {

    data class Suggestion(
        val id: Long,
        val description: String,
        val suggestedPrompt: String,
        val url: String,
        val method: String,
        val createdAt: Instant
    ) {
        fun formattedTime(): String =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(createdAt)
    }

    private val suggestions = CopyOnWriteArrayList<Suggestion>()
    private var nextId = 0L
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val recentKeys = mutableMapOf<String, Long>() // key -> timestamp
    private const val DEDUPE_WINDOW_MS = 300_000L // 5 minutes
    private const val MAX_SUGGESTIONS = 200

    fun addSuggestion(description: String, suggestedPrompt: String, url: String, method: String): Boolean {
        val key = "$method:$url:$description"
        val now = System.currentTimeMillis()
        synchronized(recentKeys) {
            val last = recentKeys[key] ?: 0L
            if (now - last < DEDUPE_WINDOW_MS) return false
            recentKeys[key] = now
        }
        if (suggestions.size >= MAX_SUGGESTIONS) {
            val evicted = suggestions.size - 160
            suggestions.subList(160, suggestions.size).clear()
            java.util.logging.Logger.getLogger(OllamaSuggestionRegistry::class.java.name)
                .info("Evicted $evicted old suggestions (max $MAX_SUGGESTIONS)")
        }
        // Clean up stale recentKeys entries older than 2x dedupe window
        synchronized(recentKeys) {
            val cutoff = now - DEDUPE_WINDOW_MS * 2
            recentKeys.entries.removeAll { it.value < cutoff }
        }
        val id = ++nextId
        suggestions.add(0, Suggestion(id, description, suggestedPrompt, url, method, Instant.now()))
        notifyListeners()
        return true
    }

    fun allSuggestions(): List<Suggestion> = suggestions.toList()

    fun clear() {
        suggestions.clear()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        SwingUtilities.invokeLater {
            listeners.forEach { it() }
        }
    }
}
