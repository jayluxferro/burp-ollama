package ollama

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/**
 * Cache of available Ollama models for use in context menus and dialogs.
 * Updated when Settings panel refreshes models or Test connection runs.
 * Listeners are notified on the EDT when the model list changes.
 */
object OllamaModelCache {
    private val modelsRef = AtomicReference<List<String>>(emptyList())
    private val listeners = CopyOnWriteArrayList<(List<String>) -> Unit>()

    var models: List<String>
        get() = modelsRef.get()
        set(value) { update(value) }

    fun update(newModels: List<String>) {
        modelsRef.set(newModels)
        SwingUtilities.invokeLater { listeners.forEach { it(newModels) } }
    }

    fun addListener(listener: (List<String>) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (List<String>) -> Unit) { listeners.remove(listener) }
}
