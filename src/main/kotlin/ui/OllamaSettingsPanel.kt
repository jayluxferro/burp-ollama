package ui

import ollama.OllamaConfig
import ollama.OllamaModelCache
import ollama.OllamaService
import prompts.SecurityPrompts
import java.awt.BorderLayout
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
    private val modelRepeaterField = JTextField(config.modelRepeater, 30).apply {
        toolTipText = "Optional. Leave blank to use default model in Repeater Ollama tab."
    }
    private val modelSuiteField = JTextField(config.modelSuite, 30).apply {
        toolTipText = "Optional. Leave blank to use default model in Ollama Suite tab."
    }
    private val modelDecoderField = JTextField(config.modelDecoder, 30).apply {
        toolTipText = "Optional. Leave blank to use default model in Decoder (when Ollama tab is shown)."
    }
    private val chainModelAField = JTextField(config.chainModelA, 25).apply {
        toolTipText = "First model in chain (generates initial response). Leave blank to disable chaining."
    }
    private val chainModelBField = JTextField(config.chainModelB, 25).apply {
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
    private val chain2ModelAField = JTextField(config.chain2ModelA, 25).apply {
        toolTipText = "Chain 2: first model"
    }
    private val chain2ModelBField = JTextField(config.chain2ModelB, 25).apply {
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
        formPanel.add(modelRepeaterField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model override (Suite tab):"), gbc)
        gbc.gridx = 1
        formPanel.add(modelSuiteField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Model override (Decoder):"), gbc)
        gbc.gridx = 1
        formPanel.add(modelDecoderField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain model A:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainModelAField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain model B:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainModelBField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain refine prompt:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(chainRefinePromptField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Active preset:"), gbc)
        gbc.gridx = 1
        formPanel.add(chainPresetCombo, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 model A:"), gbc)
        gbc.gridx = 1
        formPanel.add(chain2ModelAField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 model B:"), gbc)
        gbc.gridx = 1
        formPanel.add(chain2ModelBField, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Chain 2 refine prompt:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(chain2RefinePromptField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
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
        formPanel.add(JScrollPane(systemPromptField).apply {
            preferredSize = java.awt.Dimension(400, 80)
        }, gbc)
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
                    this@OllamaSettingsPanel,
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
        formPanel.add(JScrollPane(promptExplainHeadersField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Analyze (vulnerability):"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptAnalyzeField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Validate false positive:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptValidateFalsePositiveField).apply { preferredSize = java.awt.Dimension(400, 55) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Decipher code:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptDecipherField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Generate login:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptGenerateLoginField).apply { preferredSize = java.awt.Dimension(400, 55) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest payloads:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptIntruderPayloadsField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest attack type:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptIntruderAttackTypeField).apply { preferredSize = java.awt.Dimension(400, 45) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Intruder suggest OOB payloads:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptIntruderOobPayloadsField).apply { preferredSize = java.awt.Dimension(400, 55) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Explore issue:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptExploreIssueField).apply { preferredSize = java.awt.Dimension(400, 55) }, gbc)
        gbc.gridx = 0
        gbc.gridy++

        formPanel.add(JLabel("Autonomous Explore:"), gbc)
        gbc.gridx = 1
        formPanel.add(JScrollPane(promptAutonomousExploreField).apply { preferredSize = java.awt.Dimension(400, 55) }, gbc)
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

    private fun saveToConfig() {
        config.baseUrl = baseUrlField.text.trim().ifBlank { OllamaService.DEFAULT_BASE_URL }
        config.model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: "").trim().ifBlank { OllamaConfig.DEFAULT_MODEL }
        config.modelRepeater = modelRepeaterField.text.trim()
        config.modelSuite = modelSuiteField.text.trim()
        config.modelDecoder = modelDecoderField.text.trim()
        config.chainModelA = chainModelAField.text.trim()
        config.chainModelB = chainModelBField.text.trim()
        config.chainRefinePrompt = chainRefinePromptField.text.ifBlank { SecurityPrompts.DEFAULT_CHAIN_REFINE }
        config.chainActivePreset = chainPresetCombo.selectedIndex
        config.chain2ModelA = chain2ModelAField.text.trim()
        config.chain2ModelB = chain2ModelBField.text.trim()
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
        modelRepeaterField.text = config.modelRepeater
        modelSuiteField.text = config.modelSuite
        modelDecoderField.text = config.modelDecoder
        chainModelAField.text = config.chainModelA
        chainModelBField.text = config.chainModelB
        chainRefinePromptField.text = config.chainRefinePrompt
        chainPresetCombo.selectedIndex = config.chainActivePreset
        chain2ModelAField.text = config.chain2ModelA
        chain2ModelBField.text = config.chain2ModelB
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
