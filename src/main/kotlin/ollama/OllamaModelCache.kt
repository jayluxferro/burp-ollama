package ollama

import java.util.concurrent.atomic.AtomicReference

/**
 * Cache of available Ollama models for use in context menus and dialogs.
 * Updated when Settings panel refreshes models or Test connection runs.
 */
object OllamaModelCache {
    private val modelsRef = AtomicReference<List<String>>(emptyList())

    var models: List<String>
        get() = modelsRef.get()
        set(value) {
            modelsRef.set(value)
        }

    fun update(newModels: List<String>) {
        modelsRef.set(newModels)
    }
}
