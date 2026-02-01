package ui

import ollama.OllamaConfig
import ollama.OllamaModelCache
import ollama.OllamaService
import prompts.SecurityPrompts
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.border.EmptyBorder
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JOptionPane
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSeparator
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
) : JPanel(BorderLayout()), burp.api.montoya.ui.settings.SettingsPanel {

    private val saveButton = JButton("Save")
    private val statusLabel = JLabel(" ").apply { toolTipText = "Edit settings and click Save to apply" }
    private val baseUrlField = JTextField(config.baseUrl, 40)
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.model)
    }
    private val modelRepeaterCombo = createModelDropdown(config.modelRepeater).apply {
        toolTipText = "Optional. Use default to use default model in Repeater Ollama tab."
    }
    private val modelSuiteCombo = createModelDropdown(config.modelSuite).apply {
        toolTipText = "Optional. Use default to use default model in Ollama Suite tab."
    }
    private val modelDecoderCombo = createModelDropdown(config.modelDecoder).apply {
        toolTipText = "Optional. Use default to use default model in Decoder (when Ollama tab is shown)."
    }
    private val chainModelACombo = createModelDropdown(config.chainModelA).apply {
        toolTipText = "First model in chain (generates initial response). Use default to disable chaining."
    }
    private val chainModelBCombo = createModelDropdown(config.chainModelB).apply {
        toolTipText = "Second model in chain (refines output of model A)."
    }
    private val chainRefinePromptField = JTextArea(config.chainRefinePrompt, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "System prompt for the refiner model (model B)."
    }
    private val chainPresetCombo = javax.swing.JComboBox(arrayOf("Chain 1", "Chain 2")).apply {
        selectedIndex = config.chainActivePreset
        toolTipText = "Active chain preset"
    }
    private val chain2ModelACombo = createModelDropdown(config.chain2ModelA).apply {
        toolTipText = "Chain 2: first model"
    }
    private val chain2ModelBCombo = createModelDropdown(config.chain2ModelB).apply {
        toolTipText = "Chain 2: second model"
    }
    private val chain2RefinePromptField = JTextArea(config.chain2RefinePrompt, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Chain 2: refine prompt"
    }
    private val timeoutField = JTextField(config.timeoutSeconds.toString(), 8)
    private val numCtxField = JTextField(config.numCtx.toString(), 8)
    private val streamingCheck = JCheckBox("Use streaming responses", config.streaming)
    private val useBurpHttpApiCheck = JCheckBox("Route Ollama via Burp HTTP API", config.useBurpHttpApi).apply {
        toolTipText = "Send Ollama requests through Burp (proxy, TLS). Default: direct to localhost."
    }
    private val proactiveSuggestionsCheck = JCheckBox("Enable proactive suggestions", config.proactiveSuggestionsEnabled).apply {
        toolTipText = "Detect login, auth, API patterns and suggest AI actions in Ollama tab"
    }
    private val systemPromptField = JTextArea(config.systemPromptExplain, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private val testButton = JButton("Test connection")

    // Login sequence (experimental)
    private val loginEnabledCheck = JCheckBox("Enable login sequence", config.loginEnabled)
    private val loginBaseUrlField = JTextField(config.loginBaseUrl, 40).apply {
        toolTipText = "Base URL scope (e.g. https://example.com). Leave blank to run for all requests."
    }
    private val loginDescriptionField = JTextArea(2, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Describe the login flow (e.g. POST to /login with username/password form)"
    }
    private val generateLoginButton = JButton("Generate from description")
    private val loginRequestTemplateField = JTextArea(config.loginRequestTemplate, 6, 50).apply {
        lineWrap = false
        toolTipText = "Raw HTTP request. Use {{username}} and {{password}} as placeholders."
    }
    private val loginUsernameField = JTextField(config.loginUsername, 20)
    private val loginPasswordField = JPasswordField(config.loginPassword, 20).apply {
        toolTipText = "Password for login (stored in Burp preferences)"
    }

    // Advanced prompts (Explain headers, Analyze, Validate false positive, Decipher, Generate login)
    private val promptExplainHeadersField = JTextArea(config.systemPromptExplainHeaders, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptAnalyzeField = JTextArea(config.systemPromptAnalyze, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptValidateFalsePositiveField = JTextArea(config.systemPromptValidateFalsePositive, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptDecipherField = JTextArea(config.systemPromptDecipher, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptGenerateLoginField = JTextArea(config.systemPromptGenerateLogin, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptIntruderPayloadsField = JTextArea(config.systemPromptIntruderPayloads, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptIntruderAttackTypeField = JTextArea(config.systemPromptIntruderAttackType, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptIntruderOobPayloadsField = JTextArea(config.systemPromptIntruderOobPayloads, 2, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptExploreIssueField = JTextArea(config.systemPromptExploreIssue, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val promptAutonomousExploreField = JTextArea(config.systemPromptAutonomousExplore, 3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val autonomousMaxIterField = JTextField(config.autonomousExploreMaxIterations.toString(), 6)
    private val autonomousDelayField = JTextField(config.autonomousExploreDelayMs.toString(), 6).apply {
        toolTipText = "Delay between requests (ms). 0 = no delay."
    }
    private val customPromptsListModel = DefaultListModel<String>()
    private val customPromptsList = JList(customPromptsListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        toolTipText = "Custom prompts appear in Ask Ollama context menu"
    }

    private val formPanel = JPanel(GridBagLayout())

    companion object {
        private const val USE_DEFAULT = "(use default)"
    }

    private fun createModelDropdown(currentValue: String): JComboBox<String> {
        val models = OllamaModelCache.models
        val extra = currentValue.trim().takeIf { it.isNotBlank() && !models.contains(it) }?.let { listOf(it) } ?: emptyList()
        val items = listOf(USE_DEFAULT) + models + extra
        val combo = JComboBox(DefaultComboBoxModel(items.toTypedArray()))
        combo.preferredSize = Dimension(220, 24)
        val toSelect = currentValue.trim().ifBlank { USE_DEFAULT }
        combo.selectedItem = if (items.contains(toSelect)) toSelect else USE_DEFAULT
        return combo
    }

    private fun refreshAllModelDropdowns() {
        val models = OllamaModelCache.models
        listOf(modelRepeaterCombo, modelSuiteCombo, modelDecoderCombo, chainModelACombo, chainModelBCombo, chain2ModelACombo, chain2ModelBCombo).forEach { combo ->
            val current = (combo.selectedItem?.toString()?.trim()?.takeIf { it != USE_DEFAULT } ?: USE_DEFAULT)
            val extra = if (current != USE_DEFAULT && current.isNotBlank() && !models.contains(current)) listOf(current) else emptyList()
            val items = listOf(USE_DEFAULT) + models + extra
            combo.model = DefaultComboBoxModel(items.toTypedArray())
            combo.selectedItem = if (current == USE_DEFAULT || items.contains(current)) current else USE_DEFAULT
        }
    }

    private fun createPromptEditPanel(valueField: JTextArea, label: String, tooltip: String, defaultText: String): JPanel {
        val previewLabel = JLabel().apply {
            font = font.deriveFont(java.awt.Font.ITALIC, font.size - 1f)
        }
        fun updatePreview() {
            val t = valueField.text.trim().ifBlank { defaultText }
            previewLabel.text = if (t.length > 60) t.take(60) + "…" else t.ifBlank { "(default)" }
            previewLabel.toolTipText = t.ifBlank { defaultText }
        }
        val editButton = JButton("✎ Edit").apply {
            toolTipText = tooltip
            addActionListener {
                val editArea = JTextArea(valueField.text, 6, 50).apply {
                    lineWrap = true
                    wrapStyleWord = true
                    margin = Insets(8, 8, 8, 8)
                }
                val dialogPanel = JPanel(BorderLayout()).apply {
                    add(JLabel("$label (click OK to save):"), BorderLayout.NORTH)
                    add(JScrollPane(editArea).apply { preferredSize = java.awt.Dimension(450, 120) }, BorderLayout.CENTER)
                }
                val r = JOptionPane.showConfirmDialog(
                    null,
                    dialogPanel,
                    "Edit: $label",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
                )
                if (r == JOptionPane.OK_OPTION) {
                    valueField.text = editArea.text.trim().ifBlank { defaultText }
                    updatePreview()
                }
            }
        }
        updatePreview()
        return JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(previewLabel)
                add(editButton)
            }, BorderLayout.CENTER)
        }
    }

    init {
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(UiConstants.FORM_ROW_GAP, 8, UiConstants.FORM_ROW_GAP, 8)
            gridx = 0
            gridy = 0
        }
        formPanel.border = EmptyBorder(UiConstants.PANEL_PADDING)

        formPanel.add(JLabel("Ollama base URL:"), gbc)
        gbc.gridx = 1
        formPanel.add(baseUrlField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model (default):"), gbc)
        gbc.gridx = 1
        val modelPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        modelPanel.add(modelCombo)
        val refreshModelsButton = JButton("Refresh")
        refreshModelsButton.addActionListener {
            saveToConfig()
            config.applyTo(ollamaService)
            refreshModelComboAsync()
        }
        modelPanel.add(refreshModelsButton)
        formPanel.add(modelPanel, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model override (Repeater):"), gbc)
        gbc.gridx = 1
        formPanel.add(modelRepeaterCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model override (Suite tab):"), gbc)
        gbc.gridx = 1
        formPanel.add(modelSuiteCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model override (Decoder):"), gbc)
        gbc.gridx = 1
        formPanel.add(modelDecoderCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain model A:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainModelACombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain model B:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainModelBCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain refine prompt:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(chainRefinePromptField, "Chain refine prompt", "System prompt for the refiner model (model B)", SecurityPrompts.DEFAULT_CHAIN_REFINE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Active preset:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainPresetCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 model A:"), gbc)
        gbc.gridx = 1
        formPanel.add(chain2ModelACombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 model B:"), gbc)
        gbc.gridx = 1
        formPanel.add(chain2ModelBCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 refine prompt:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(chain2RefinePromptField, "Chain 2 refine prompt", "Chain 2: refine prompt", SecurityPrompts.DEFAULT_CHAIN_REFINE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Timeout (seconds):"), gbc)
        gbc.gridx = 1
        formPanel.add(timeoutField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Context size (num_ctx):"), gbc)
        gbc.gridx = 1
        formPanel.add(numCtxField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(streamingCheck, gbc)
        gbc.gridx = 1
        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(useBurpHttpApiCheck, gbc)
        gbc.gridx = 1
        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(proactiveSuggestionsCheck, gbc)
        gbc.gridx = 1
        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("System prompt (explain):"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(systemPromptField, "System prompt (explain)", "Default prompt for Explain selection", SecurityPrompts.DEFAULT_EXPLAIN_SELECTION), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(testButton, gbc)
        gbc.gridx = 1
        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Prompts library:"), gbc)
        gbc.gridx = 1
        val promptsPanel = JPanel(BorderLayout())
        promptsPanel.add(JScrollPane(customPromptsList).apply { preferredSize = java.awt.Dimension(400, 80) }, BorderLayout.CENTER)
        val promptsButtons = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        promptsButtons.add(JButton("Add").apply {
            addActionListener {
                val nameField = JTextField(20)
                val promptField = JTextArea(3, 40).apply { lineWrap = true; wrapStyleWord = true }
                val r = JOptionPane.showConfirmDialog(
                    null,
                    JPanel(GridBagLayout()).apply {
                        val g = GridBagConstraints()
                        g.gridx = 0; g.gridy = 0; add(JLabel("Name:"), g)
                        g.gridx = 1; add(nameField, g)
                        g.gridy = 1; g.gridx = 0; add(JLabel("System prompt:"), g)
                        g.gridx = 1; add(JScrollPane(promptField).apply { preferredSize = java.awt.Dimension(300, 60) }, g)
                    },
                    "Add custom prompt",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
                )
                if (r == JOptionPane.OK_OPTION && nameField.text.trim().isNotBlank() && promptField.text.trim().isNotBlank()) {
                    val prompts = config.getCustomPrompts().toMutableList()
                    prompts.add(nameField.text.trim() to promptField.text.trim())
                    config.setCustomPrompts(prompts)
                    refreshCustomPromptsList()
                }
            }
        })
        promptsButtons.add(JButton("Remove").apply {
            addActionListener {
                val idx = customPromptsList.selectedIndex
                if (idx >= 0) {
                    val prompts = config.getCustomPrompts().toMutableList()
                    if (idx < prompts.size) {
                        prompts.removeAt(idx)
                        config.setCustomPrompts(prompts)
                        refreshCustomPromptsList()
                    }
                }
            }
        })
        promptsPanel.add(promptsButtons, BorderLayout.SOUTH)
        formPanel.add(promptsPanel, gbc)
        gbc.gridx = 0
        gbc.gridy++

        // Login sequence (experimental)
        gbc.gridwidth = 2
        formPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        gbc.gridy++

        formPanel.add(JLabel("<html><b>Login sequence (experimental)</b></html>"), gbc)
        gbc.gridx = 1
        formPanel.add(loginEnabledCheck, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Base URL scope:"), gbc)
        gbc.gridx = 1
        formPanel.add(loginBaseUrlField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Description:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(loginDescriptionField).apply { preferredSize = java.awt.Dimension(400, 50) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 1
        val genPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0))
        genPanel.add(generateLoginButton)
        formPanel.add(genPanel, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Request template:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(loginRequestTemplateField).apply { preferredSize = java.awt.Dimension(400, 120) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Username:"), gbc)
        gbc.gridx = 1
        formPanel.add(loginUsernameField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Password:"), gbc)
        gbc.gridx = 1
        formPanel.add(loginPasswordField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        // Advanced prompts
        gbc.gridwidth = 2
        formPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        gbc.gridy++

        formPanel.add(JLabel("<html><b>Advanced prompts</b></html>"), gbc)
        gbc.gridx = 1
        formPanel.add(JLabel(""), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Explain headers:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptExplainHeadersField, "Explain headers", "Prompt for Explain headers", SecurityPrompts.DEFAULT_EXPLAIN_HEADERS), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Analyze (vulnerability):"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptAnalyzeField, "Analyze (vulnerability)", "Prompt for vulnerability analysis", SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Validate false positive:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptValidateFalsePositiveField, "Validate false positive", "Prompt for false positive validation", SecurityPrompts.DEFAULT_VALIDATE_FALSE_POSITIVE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Decipher code:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptDecipherField, "Decipher code", "Prompt for code analysis", SecurityPrompts.DEFAULT_DECIPHER_CODE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Generate login:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptGenerateLoginField, "Generate login", "Prompt for login sequence generation", SecurityPrompts.DEFAULT_GENERATE_LOGIN_SEQUENCE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest payloads:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptIntruderPayloadsField, "Intruder suggest payloads", "Prompt for Intruder payload suggestions", SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_PAYLOADS), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest attack type:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptIntruderAttackTypeField, "Intruder suggest attack type", "Prompt for Intruder attack type", SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_ATTACK_TYPE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest OOB payloads:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptIntruderOobPayloadsField, "Intruder suggest OOB payloads", "Prompt for OOB payload suggestions", SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_OOB_PAYLOADS), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Explore issue:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptExploreIssueField, "Explore issue", "Prompt for issue exploration", SecurityPrompts.DEFAULT_EXPLORE_ISSUE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Autonomous Explore:"), gbc)
        gbc.gridx = 1
        formPanel.add(createPromptEditPanel(promptAutonomousExploreField, "Autonomous Explore", "Prompt for autonomous exploration", SecurityPrompts.DEFAULT_AUTONOMOUS_EXPLORE), gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Autonomous max iterations:"), gbc)
        gbc.gridx = 1
        formPanel.add(autonomousMaxIterField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Autonomous delay (ms):"), gbc)
        gbc.gridx = 1
        formPanel.add(autonomousDelayField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        // Explicit Save button - no auto-save on focus lost
        saveButton.addActionListener {
            saveToConfig()
            config.applyTo(ollamaService)
            statusLabel.text = "Saved"
            val timer = javax.swing.Timer(2000) { evt ->
                statusLabel.text = " "
                (evt.source as? javax.swing.Timer)?.stop()
            }
            timer.start()
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(UiConstants.PANEL_PADDING)
        }
        toolbar.add(saveButton)
        toolbar.add(statusLabel)
        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(formPanel), BorderLayout.CENTER)

        generateLoginButton.addActionListener {
            saveToConfig()
            config.applyTo(ollamaService)
            val desc = loginDescriptionField.text.trim()
            if (desc.isBlank()) {
                onTestConnection("Enter a login flow description first.")
                return@addActionListener
            }
            generateLoginButton.isEnabled = false
            ollamaService.execute {
                val result = ollamaService.chat(
                    model = config.model,
                    systemPrompt = config.systemPromptGenerateLogin,
                    userMessage = desc,
                    numCtx = config.numCtx
                )
                SwingUtilities.invokeLater {
                    generateLoginButton.isEnabled = true
                    when {
                        result.isSuccess -> {
                            val template = result.getOrNull()?.content?.trim() ?: ""
                            loginRequestTemplateField.text = template
                            config.loginRequestTemplate = template
                            onTestConnection("Generated login template. Review and edit if needed.")
                        }
                        else -> onTestConnection("Generation failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                    }
                }
            }
        }

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
        SwingUtilities.invokeLater {
            refreshModelComboAsync()
            refreshCustomPromptsList()
        }
    }

    private fun refreshCustomPromptsList() {
        customPromptsListModel.clear()
        config.getCustomPrompts().forEach { (name, _) -> customPromptsListModel.addElement(name) }
    }

    private fun refreshModelCombo(models: List<String>) {
        OllamaModelCache.update(models)
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
        refreshAllModelDropdowns()
    }

    private fun refreshModelComboAsync() {
        ollamaService.execute {
            config.applyTo(ollamaService)
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater { refreshModelCombo(models.getOrNull() ?: emptyList()) }
            }
        }
    }

    private fun modelFromCombo(combo: JComboBox<String>): String {
        val v = (combo.editor?.item ?: combo.selectedItem)?.toString()?.trim() ?: ""
        return if (v == USE_DEFAULT || v.isBlank()) "" else v
    }

    private fun saveToConfig() {
        config.baseUrl = baseUrlField.text.trim().ifBlank { OllamaService.DEFAULT_BASE_URL }
        config.model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: "").trim().ifBlank { OllamaConfig.DEFAULT_MODEL }
        config.modelRepeater = modelFromCombo(modelRepeaterCombo)
        config.modelSuite = modelFromCombo(modelSuiteCombo)
        config.modelDecoder = modelFromCombo(modelDecoderCombo)
        config.chainModelA = modelFromCombo(chainModelACombo)
        config.chainModelB = modelFromCombo(chainModelBCombo)
        config.chainRefinePrompt = chainRefinePromptField.text.ifBlank { SecurityPrompts.DEFAULT_CHAIN_REFINE }
        config.chainActivePreset = chainPresetCombo.selectedIndex
        config.chain2ModelA = modelFromCombo(chain2ModelACombo)
        config.chain2ModelB = modelFromCombo(chain2ModelBCombo)
        config.chain2RefinePrompt = chain2RefinePromptField.text.ifBlank { SecurityPrompts.DEFAULT_CHAIN_REFINE }
        config.timeoutSeconds = timeoutField.text.toIntOrNull() ?: OllamaService.DEFAULT_TIMEOUT
        config.numCtx = numCtxField.text.toIntOrNull() ?: OllamaConfig.DEFAULT_NUM_CTX
        config.streaming = streamingCheck.isSelected
        config.useBurpHttpApi = useBurpHttpApiCheck.isSelected
        config.proactiveSuggestionsEnabled = proactiveSuggestionsCheck.isSelected
        config.systemPromptExplain = systemPromptField.text.ifBlank { SecurityPrompts.DEFAULT_EXPLAIN_SELECTION }
        config.systemPromptExplainHeaders = promptExplainHeadersField.text.ifBlank { SecurityPrompts.DEFAULT_EXPLAIN_HEADERS }
        config.systemPromptAnalyze = promptAnalyzeField.text.ifBlank { SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY }
        config.systemPromptValidateFalsePositive = promptValidateFalsePositiveField.text.ifBlank { SecurityPrompts.DEFAULT_VALIDATE_FALSE_POSITIVE }
        config.systemPromptDecipher = promptDecipherField.text.ifBlank { SecurityPrompts.DEFAULT_DECIPHER_CODE }
        config.systemPromptGenerateLogin = promptGenerateLoginField.text.ifBlank { SecurityPrompts.DEFAULT_GENERATE_LOGIN_SEQUENCE }
        config.systemPromptIntruderPayloads = promptIntruderPayloadsField.text.ifBlank { SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_PAYLOADS }
        config.systemPromptIntruderAttackType = promptIntruderAttackTypeField.text.ifBlank { SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_ATTACK_TYPE }
        config.systemPromptIntruderOobPayloads = promptIntruderOobPayloadsField.text.ifBlank { SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_OOB_PAYLOADS }
        config.systemPromptExploreIssue = promptExploreIssueField.text.ifBlank { SecurityPrompts.DEFAULT_EXPLORE_ISSUE }
        config.systemPromptAutonomousExplore = promptAutonomousExploreField.text.ifBlank { SecurityPrompts.DEFAULT_AUTONOMOUS_EXPLORE }
        config.autonomousExploreMaxIterations = autonomousMaxIterField.text.toIntOrNull() ?: 5
        config.autonomousExploreDelayMs = autonomousDelayField.text.toIntOrNull() ?: 500
        config.loginEnabled = loginEnabledCheck.isSelected
        config.loginBaseUrl = loginBaseUrlField.text.trim()
        config.loginRequestTemplate = loginRequestTemplateField.text
        config.loginUsername = loginUsernameField.text
        config.loginPassword = String(loginPasswordField.password)
    }

    private fun loadFromConfig() {
        baseUrlField.text = config.baseUrl
        if (modelCombo.getItemCount() == 0) {
            modelCombo.addItem(config.model)
        }
        modelCombo.selectedItem = config.model
        refreshAllModelDropdowns()
        fun setComboSelection(combo: JComboBox<String>, value: String) {
            val v = value.trim().ifBlank { USE_DEFAULT }
            if (v != USE_DEFAULT) {
                val model = combo.model as DefaultComboBoxModel<String>
                if ((0 until model.size).map { model.getElementAt(it) }.none { it == v }) {
                    model.addElement(v)
                }
            }
            combo.selectedItem = v
        }
        setComboSelection(modelRepeaterCombo, config.modelRepeater)
        setComboSelection(modelSuiteCombo, config.modelSuite)
        setComboSelection(modelDecoderCombo, config.modelDecoder)
        setComboSelection(chainModelACombo, config.chainModelA)
        setComboSelection(chainModelBCombo, config.chainModelB)
        chainRefinePromptField.text = config.chainRefinePrompt
        chainPresetCombo.selectedIndex = config.chainActivePreset
        setComboSelection(chain2ModelACombo, config.chain2ModelA)
        setComboSelection(chain2ModelBCombo, config.chain2ModelB)
        chain2RefinePromptField.text = config.chain2RefinePrompt
        refreshCustomPromptsList()
        timeoutField.text = config.timeoutSeconds.toString()
        numCtxField.text = config.numCtx.toString()
        streamingCheck.isSelected = config.streaming
        useBurpHttpApiCheck.isSelected = config.useBurpHttpApi
        proactiveSuggestionsCheck.isSelected = config.proactiveSuggestionsEnabled
        systemPromptField.text = config.systemPromptExplain
        promptExplainHeadersField.text = config.systemPromptExplainHeaders
        promptAnalyzeField.text = config.systemPromptAnalyze
        promptValidateFalsePositiveField.text = config.systemPromptValidateFalsePositive
        promptDecipherField.text = config.systemPromptDecipher
        promptGenerateLoginField.text = config.systemPromptGenerateLogin
        promptIntruderPayloadsField.text = config.systemPromptIntruderPayloads
        promptIntruderAttackTypeField.text = config.systemPromptIntruderAttackType
        promptIntruderOobPayloadsField.text = config.systemPromptIntruderOobPayloads
        promptExploreIssueField.text = config.systemPromptExploreIssue
        promptAutonomousExploreField.text = config.systemPromptAutonomousExplore
        autonomousMaxIterField.text = config.autonomousExploreMaxIterations.toString()
        autonomousDelayField.text = config.autonomousExploreDelayMs.toString()
        loginEnabledCheck.isSelected = config.loginEnabled
        loginBaseUrlField.text = config.loginBaseUrl
        loginRequestTemplateField.text = config.loginRequestTemplate
        loginUsernameField.text = config.loginUsername
        loginPasswordField.text = config.loginPassword
        refreshCustomPromptsList()
    }

    override fun uiComponent(): JComponent = this

    override fun keywords(): Set<String> = setOf("ollama", "ai", "llm", "burp ollama", "login", "session")

    fun refreshFromConfig() {
        SwingUtilities.invokeLater { loadFromConfig() }
    }
}
