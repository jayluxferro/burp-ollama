package ui

import ollama.OllamaConfig
import ollama.OllamaService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Custom settings panel for Burp Ollama.
 * Uses Preferences via OllamaConfig for persistence.
 */
class OllamaSettingsPanel(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val onTestConnection: (String) -> Unit
) : JPanel(GridBagLayout()), burp.api.montoya.ui.settings.SettingsPanel {

    private val baseUrlField = JTextField(config.baseUrl, 40)
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.model)
    }
    private val timeoutField = JTextField(config.timeoutSeconds.toString(), 8)
    private val numCtxField = JTextField(config.numCtx.toString(), 8)
    private val streamingCheck = JCheckBox("Use streaming responses", config.streaming)
    private val systemPromptField = JTextArea(config.systemPromptExplain, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private val testButton = JButton("Test connection")

    init {
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
            gridx = 0
            gridy = 0
        }

        add(JLabel("Ollama base URL:"), gbc)
        gbc.gridx = 1
        add(baseUrlField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(JLabel("Model:"), gbc)
        gbc.gridx = 1
        val modelPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        modelPanel.add(modelCombo)
        val refreshModelsButton = JButton("Refresh")
        refreshModelsButton.addActionListener {
            saveToConfig()
            config.applyTo(ollamaService)
            refreshModelComboAsync()
        }
        modelPanel.add(refreshModelsButton)
        add(modelPanel, gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(JLabel("Timeout (seconds):"), gbc)
        gbc.gridx = 1
        add(timeoutField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(JLabel("Context size (num_ctx):"), gbc)
        gbc.gridx = 1
        add(numCtxField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(streamingCheck, gbc)
        gbc.gridx = 1
        add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(JLabel("System prompt (explain):"), gbc)
        gbc.gridx = 1
        add(JScrollPane(systemPromptField).apply {
            preferredSize = java.awt.Dimension(400, 80)
        }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        add(testButton, gbc)
        gbc.gridx = 1
        add(JLabel(""), gbc)

        // Save on focus lost
        listOf(baseUrlField, timeoutField, numCtxField, systemPromptField).forEach { field ->
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) = saveToConfig()
            })
        }
        streamingCheck.addActionListener { saveToConfig() }
        modelCombo.addActionListener { saveToConfig() }

        testButton.addActionListener {
            saveToConfig()
            config.applyTo(ollamaService)
            val result = ollamaService.healthCheck()
            val models = ollamaService.listModels()
            val msg = when {
                result && models.isSuccess -> {
                    refreshModelCombo(models.getOrNull() ?: emptyList())
                    "Connected. Models: ${(models.getOrNull() ?: emptyList()).joinToString(", ")}"
                }
                result -> "Connected but failed to list models: ${models.exceptionOrNull()?.message}"
                else -> "Cannot connect to Ollama at ${config.baseUrl}. Is Ollama running?"
            }
            onTestConnection(msg)
        }

        // Load models on panel init (background)
        SwingUtilities.invokeLater { refreshModelComboAsync() }
    }

    private fun refreshModelCombo(models: List<String>) {
        val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim()?.ifBlank { null } ?: config.model
        val items = if (models.isEmpty()) {
            listOf(current)
        } else {
            val list = models.toMutableList()
            if (!list.contains(current)) list.add(0, current)
            list
        }
        modelCombo.model = DefaultComboBoxModel(items.toTypedArray())
        modelCombo.selectedItem = current
    }

    private fun refreshModelComboAsync() {
        Thread {
            config.applyTo(ollamaService)
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater { refreshModelCombo(models.getOrNull() ?: emptyList()) }
            }
        }.start()
    }

    private fun saveToConfig() {
        config.baseUrl = baseUrlField.text.trim().ifBlank { OllamaService.DEFAULT_BASE_URL }
        config.model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: "").trim().ifBlank { OllamaConfig.DEFAULT_MODEL }
        config.timeoutSeconds = timeoutField.text.toIntOrNull() ?: OllamaService.DEFAULT_TIMEOUT
        config.numCtx = numCtxField.text.toIntOrNull() ?: OllamaConfig.DEFAULT_NUM_CTX
        config.streaming = streamingCheck.isSelected
        config.systemPromptExplain = systemPromptField.text.ifBlank { prompts.SecurityPrompts.DEFAULT_EXPLAIN_SELECTION }
    }

    private fun loadFromConfig() {
        baseUrlField.text = config.baseUrl
        if (modelCombo.getItemCount() == 0) {
            modelCombo.addItem(config.model)
        }
        modelCombo.selectedItem = config.model
        timeoutField.text = config.timeoutSeconds.toString()
        numCtxField.text = config.numCtx.toString()
        streamingCheck.isSelected = config.streaming
        systemPromptField.text = config.systemPromptExplain
    }

    override fun uiComponent(): JComponent = this

    override fun keywords(): Set<String> = setOf("ollama", "ai", "llm", "burp ollama")

    fun refreshFromConfig() {
        SwingUtilities.invokeLater { loadFromConfig() }
    }
}
