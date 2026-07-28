package ui

import ollama.OllamaConfig
import ollama.OllamaService
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.SwingUtilities

object ModelComboHelper {

    /**
     * Refresh a model combo box asynchronously by querying Ollama for available models.
     * Preserves the current selection (or falls back to [defaultModel]).
     * Must be called from any thread; UI updates are posted via SwingUtilities.invokeLater.
     */
    fun refreshCombo(
        combo: JComboBox<String>,
        ollamaService: OllamaService,
        config: OllamaConfig,
        defaultModel: String
    ) {
        config.applyTo(ollamaService)
        ollamaService.execute {
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater {
                    val current = (combo.editor?.item?.toString() ?: combo.selectedItem?.toString())?.trim() ?: defaultModel
                    val list = models.getOrNull() ?: emptyList()
                    val items = if (list.isEmpty()) listOf(current) else {
                        val mutable = list.toMutableList()
                        if (!mutable.contains(current)) mutable.add(0, current)
                        mutable
                    }
                    combo.model = DefaultComboBoxModel(items.toTypedArray())
                    combo.selectedItem = current
                }
            }
        }
    }
}
