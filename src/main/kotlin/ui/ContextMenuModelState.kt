package ui

/**
 * Shared state for context menu and editor tab model/chain selection.
 * Persists across menu invocations and keeps the Ollama tab in sync with "Use model" from the context menu.
 */
object ContextMenuModelState {
    var modelOverride: String? = null
    var bypassChain: Boolean = false
}
